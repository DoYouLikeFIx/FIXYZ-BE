package com.fix.channel.integration;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.dto.request.AdminAuditLogQueryRequest;
import com.fix.channel.entity.AuditAction;
import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.Member;
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.service.AuditLogService;
import com.fix.channel.support.ChannelContainersIntegrationTestBase;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
class AdminSessionAuditIntegrationTest extends ChannelContainersIntegrationTestBase {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MemberRepository memberRepository;

  @Autowired
  private AuditLogRepository auditLogRepository;

  @Autowired
  private AuditLogService auditLogService;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private SessionRepository<? extends Session> sessionRepository;

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  private void resetStores() {
    auditLogRepository.deleteAll();
    memberRepository.deleteAll();
    stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
      connection.serverCommands().flushDb();
      return null;
    });
  }

  @Test
  void shouldInvalidateTargetMemberSessionsAndRejectStaleSessionAfterAdminForceLogout() throws Exception {
    resetStores();
    Member admin = createMember("M-ADMIN-001", "admin1@fixyz.com", "ROLE_ADMIN");
    Member target = createMember("M-USER-001", "user1@fixyz.com", "ROLE_USER");

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");
    String targetSessionId = createAuthenticatedSession(target, "ROLE_USER");
    String csrfToken = fetchCsrfToken(adminSessionId);

    mockMvc.perform(delete("/api/v1/admin/members/{memberUuid}/sessions", target.getMemberNo())
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .header(HttpHeaders.USER_AGENT, "JUnit-Admin-Client/1.0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.memberUuid").value(target.getMemberNo()))
        .andExpect(jsonPath("$.data.invalidatedCount").value(1));

    MvcResult staleSessionResult = performWithSingleRetryOn5xx(() ->
      get("/api/v1/notifications/stream")
        .cookie(sessionCookie(targetSessionId))
    );
    int staleStatus = staleSessionResult.getResponse().getStatus();
    assertThat(staleStatus).isBetween(400, 499);

    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getAction()).isEqualTo(AuditAction.ADMIN_FORCE_LOGOUT.value());
          assertThat(log.getMemberId()).isEqualTo(admin.getId());
          assertThat(log.getTargetId()).isEqualTo(target.getMemberNo());
          assertThat(log.getIpAddress()).isNotBlank();
          assertThat(log.getUserAgent()).isEqualTo("JUnit-Admin-Client/1.0");
          assertThat(log.getDetail()).contains("adminEmail=" + admin.getEmail());
        });
  }

  @Test
  void shouldReturnIdempotentSuccessWhenTargetHasNoSessionsAndStillRecordAudit() throws Exception {
    resetStores();
    Member admin = createMember("M-ADMIN-002", "admin2@fixyz.com", "ROLE_ADMIN");
    Member target = createMember("M-USER-002", "user2@fixyz.com", "ROLE_USER");

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");
    String csrfToken = fetchCsrfToken(adminSessionId);

    mockMvc.perform(delete("/api/v1/admin/members/{memberUuid}/sessions", target.getMemberNo())
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .header(HttpHeaders.USER_AGENT, "JUnit-Admin-Client/1.0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.memberUuid").value(target.getMemberNo()))
        .andExpect(jsonPath("$.data.invalidatedCount").value(0));

    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getAction()).isEqualTo(AuditAction.ADMIN_FORCE_LOGOUT.value());
          assertThat(log.getTargetId()).isEqualTo(target.getMemberNo());
          assertThat(log.getDetail()).contains("adminEmail=" + admin.getEmail());
          assertThat(log.getDetail()).contains("invalidatedCount=0");
        });
  }

  @Test
  void shouldReturnPaginatedFilteredAuditLogsForAdminAuditEndpoint() throws Exception {
    resetStores();
    Member admin = createMember("M-ADMIN-003", "admin3@fixyz.com", "ROLE_ADMIN");
    Member target = createMember("M-USER-003", "user3@fixyz.com", "ROLE_USER");

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");

    auditLogService.record(AuditLog.of(
        target.getId(),
        AuditAction.ORDER_SESSION_OTP_FAILED,
        "ORDER_SESSION",
        "target-1",
        "clOrdId=CL-OTP-1",
        "127.0.0.1",
        "junit",
        "corr-1"
    ));
    auditLogService.record(AuditLog.of(
        target.getId(),
        AuditAction.ORDER_SESSION_OTP_REPLAYED,
        "ORDER_SESSION",
        "target-2",
        "clOrdId=CL-OTP-2",
        "127.0.0.1",
        "junit",
        "corr-2"
    ));
    auditLogService.record(AuditLog.of(
        target.getId(),
        AuditAction.AUTH_LOGIN_SUCCESS,
        "SESSION",
        "target-3",
        "email=" + target.getEmail(),
        "127.0.0.1",
        "junit",
        "corr-3"
    ));

    String from = Instant.now().minusSeconds(3600).toString();
    String to = Instant.now().plusSeconds(3600).toString();

    mockMvc.perform(get("/api/v1/admin/audit-logs")
            .cookie(sessionCookie(adminSessionId))
            .param("page", "0")
            .param("size", "1")
            .param("from", from)
            .param("to", to)
            .param("memberId", String.valueOf(target.getId()))
            .param("eventType", "ORDER_OTP_FAIL"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.number").value(0))
        .andExpect(jsonPath("$.data.size").value(1))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].eventType").value("ORDER_OTP_FAIL"))
        .andExpect(jsonPath("$.data.content[0].memberId").value(target.getId()))
        .andExpect(jsonPath("$.data.content[0].ipAddress").value("127.0.0.0"));
  }

  @Test
  void shouldExposeRecoveryAuditEntriesThroughCanonicalOrderRecoveryFilter() throws Exception {
    resetStores();
    Member admin = createMember("M-ADMIN-013", "admin13@fixyz.com", "ROLE_ADMIN");
    Member target = createMember("M-USER-013", "user13@fixyz.com", "ROLE_USER");

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");

    auditLogService.record(AuditLog.ofOrderSession(
        target.getId(),
        77L,
        "ORDER_SESSION_RECOVERY_ATTEMPT",
        "ORDER_SESSION",
        "recovery-target-1",
        "clOrdId=CL-REC-1, attemptCount=3, outcome=ESCALATED, note=IllegalStateException: corebank unavailable",
        "127.0.0.1",
        "junit",
        "corr-rec-1"
    ));
    auditLogService.record(AuditLog.ofOrderSession(
        target.getId(),
        78L,
        AuditAction.ORDER_SESSION_EXECUTED,
        "ORDER_SESSION",
        "recovery-target-2",
        "clOrdId=CL-REC-1, result=FILLED",
        "127.0.0.1",
        "junit",
        "corr-rec-2"
    ));

    mockMvc.perform(get("/api/v1/admin/audit-logs")
            .cookie(sessionCookie(adminSessionId))
            .param("page", "0")
            .param("size", "10")
            .param("memberId", String.valueOf(target.getId()))
            .param("eventType", "ORDER_RECOVERY"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].eventType").value("ORDER_RECOVERY"))
        .andExpect(jsonPath("$.data.content[0].clOrdId").value("CL-REC-1"))
        .andExpect(jsonPath("$.data.content[0].orderSessionId").value(77))
        .andExpect(jsonPath("$.data.content[0].description").value(
            "clOrdId=CL-REC-1, attemptCount=3, outcome=ESCALATED, note=IllegalStateException: corebank unavailable"
        ));
  }

  @Test
  void shouldExposeReconciliationAuditEntriesThroughCanonicalOrderReconciliationFilter() throws Exception {
    resetStores();
    Member admin = createMember("M-ADMIN-014", "admin14@fixyz.com", "ROLE_ADMIN");
    Member target = createMember("M-USER-014", "user14@fixyz.com", "ROLE_USER");

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");

    auditLogService.record(AuditLog.ofOrderSession(
        target.getId(),
        88L,
        "ORDER_SESSION_RECONCILIATION",
        "ORDER_SESSION",
        "reconcile-target-1",
        "clOrdId=CL-RECNC-1, outcome=MISMATCH, mismatchType=ACCOUNT_MISMATCH, sourceSystems=CHANNEL|COREBANK|FEP",
        "127.0.0.1",
        "junit",
        "corr-rec-3"
    ));

    mockMvc.perform(get("/api/v1/admin/audit-logs")
            .cookie(sessionCookie(adminSessionId))
            .param("page", "0")
            .param("size", "10")
            .param("eventType", "ORDER_RECONCILIATION"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].eventType").value("ORDER_RECONCILIATION"))
        .andExpect(jsonPath("$.data.content[0].clOrdId").value("CL-RECNC-1"))
        .andExpect(jsonPath("$.data.content[0].description").value(
            "clOrdId=CL-RECNC-1, outcome=MISMATCH, mismatchType=ACCOUNT_MISMATCH, sourceSystems=CHANNEL|COREBANK|FEP"
        ));
  }

  @Test
  void shouldRejectAuditQueryWhenFromIsAfterTo() throws Exception {
    String from = Instant.now().plusSeconds(3600).toString();
    String to = Instant.now().minusSeconds(3600).toString();
    assertThatThrownBy(() -> new AdminAuditLogQueryRequest(
        0,
        20,
        Instant.parse(from),
        Instant.parse(to),
        null,
        null
    ))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
          BusinessException businessException = (BusinessException) ex;
          assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
          assertThat(businessException.getMessage()).contains("from must be before or equal to to");
        });
  }

  @Test
  void shouldRateLimitAdminApisPerSessionAndExposeRetryAfterHeader() throws Exception {
    resetStores();
    Member admin = createMember("M-ADMIN-004", "admin4@fixyz.com", "ROLE_ADMIN");
    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");

    for (int attempt = 0; attempt < 20; attempt++) {
      mockMvc.perform(get("/api/v1/admin/audit-logs")
              .cookie(sessionCookie(adminSessionId))
              .param("page", "0")
              .param("size", "1"))
          .andExpect(status().isOk());
    }

    mockMvc.perform(get("/api/v1/admin/audit-logs")
            .cookie(sessionCookie(adminSessionId))
            .param("page", "0")
            .param("size", "1"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
        .andExpect(jsonPath("$.code").value("RATE_001"));
  }

  @Test
  void shouldUseIndependentRateLimitBucketsPerAdminEndpoint() throws Exception {
    resetStores();
    Member admin = createMember("M-ADMIN-005", "admin5@fixyz.com", "ROLE_ADMIN");
    Member target = createMember("M-USER-005", "user5@fixyz.com", "ROLE_USER");

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");
    String csrfToken = fetchCsrfToken(adminSessionId);

    for (int attempt = 0; attempt < 20; attempt++) {
      mockMvc.perform(get("/api/v1/admin/audit-logs")
              .cookie(sessionCookie(adminSessionId))
              .param("page", "0")
              .param("size", "1"))
          .andExpect(status().isOk());
    }

    mockMvc.perform(delete("/api/v1/admin/members/{memberUuid}/sessions", target.getMemberNo())
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .header(HttpHeaders.USER_AGENT, "JUnit-Admin-Client/1.0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void shouldUseIndependentRateLimitBucketsFromSessionInvalidationToAuditLogs() throws Exception {
    resetStores();
    Member admin = createMember("M-ADMIN-012", "admin12@fixyz.com", "ROLE_ADMIN");
    Member target = createMember("M-USER-012", "user12@fixyz.com", "ROLE_USER");

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");
    String csrfToken = fetchCsrfToken(adminSessionId);

    for (int attempt = 0; attempt < 20; attempt++) {
      mockMvc.perform(delete("/api/v1/admin/members/{memberUuid}/sessions", target.getMemberNo())
              .cookie(sessionCookie(adminSessionId))
              .header("X-CSRF-TOKEN", csrfToken)
              .header(HttpHeaders.USER_AGENT, "JUnit-Admin-Client/1.0"))
          .andExpect(status().isOk());
    }

    mockMvc.perform(get("/api/v1/admin/audit-logs")
            .cookie(sessionCookie(adminSessionId))
            .param("page", "0")
            .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void shouldRejectAdminSessionInvalidationWhenCsrfTokenMissing() throws Exception {
    resetStores();
    Member admin = createMember("M-ADMIN-010", "admin10@fixyz.com", "ROLE_ADMIN");
    Member target = createMember("M-USER-010", "user10@fixyz.com", "ROLE_USER");
    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");

    mockMvc.perform(delete("/api/v1/admin/members/{memberUuid}/sessions", target.getMemberNo())
            .cookie(sessionCookie(adminSessionId)))
        .andExpect(status().isForbidden());

    assertThat(auditLogRepository.findAll())
        .noneSatisfy(log -> assertThat(log.getAction()).isEqualTo(AuditAction.ADMIN_FORCE_LOGOUT.value()));
  }

  @Test
  void shouldRejectAdminSessionInvalidationWhenCsrfTokenInvalid() throws Exception {
    resetStores();
    Member admin = createMember("M-ADMIN-011", "admin11@fixyz.com", "ROLE_ADMIN");
    Member target = createMember("M-USER-011", "user11@fixyz.com", "ROLE_USER");
    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");

    mockMvc.perform(delete("/api/v1/admin/members/{memberUuid}/sessions", target.getMemberNo())
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", "invalid-token"))
        .andExpect(status().isForbidden());

    assertThat(auditLogRepository.findAll())
        .noneSatisfy(log -> assertThat(log.getAction()).isEqualTo(AuditAction.ADMIN_FORCE_LOGOUT.value()));
  }

  @Test
  void shouldReturnNotFoundWhenTargetMemberDoesNotExist() throws Exception {
    resetStores();
    Member admin = createMember("M-ADMIN-006", "admin6@fixyz.com", "ROLE_ADMIN");
    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");
    String csrfToken = fetchCsrfToken(adminSessionId);

    mockMvc.perform(delete("/api/v1/admin/members/{memberUuid}/sessions", "M-USER-NOT-FOUND")
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .header(HttpHeaders.USER_AGENT, "JUnit-Admin-Client/1.0"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("target member not found"));
  }

  @Test
  void shouldPersistMaskedForwardedClientIpInAdminAudit() throws Exception {
    resetStores();
    Member admin = createMember("M-ADMIN-007", "admin7@fixyz.com", "ROLE_ADMIN");
    Member target = createMember("M-USER-007", "user7@fixyz.com", "ROLE_USER");

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");
    String csrfToken = fetchCsrfToken(adminSessionId);

    mockMvc.perform(delete("/api/v1/admin/members/{memberUuid}/sessions", target.getMemberNo())
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .header("X-Forwarded-For", "203.0.113.10, 10.0.0.2")
            .header(HttpHeaders.USER_AGENT, "JUnit-Admin-Client/1.0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getAction()).isEqualTo(AuditAction.ADMIN_FORCE_LOGOUT.value());
          assertThat(log.getIpAddress()).isEqualTo("203.0.113.0");
        });
  }

  @Test
  void shouldPersistMaskedRealIpWhenForwardedForIsMissing() throws Exception {
    resetStores();
    Member admin = createMember("M-ADMIN-008", "admin8@fixyz.com", "ROLE_ADMIN");
    Member target = createMember("M-USER-008", "user8@fixyz.com", "ROLE_USER");

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");
    String csrfToken = fetchCsrfToken(adminSessionId);

    mockMvc.perform(delete("/api/v1/admin/members/{memberUuid}/sessions", target.getMemberNo())
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .header("X-Real-IP", "198.51.100.44")
            .header(HttpHeaders.USER_AGENT, "JUnit-Admin-Client/1.0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getAction()).isEqualTo(AuditAction.ADMIN_FORCE_LOGOUT.value());
          assertThat(log.getIpAddress()).isEqualTo("198.51.100.0");
        });
  }

  @Test
  void shouldRejectWhenAuthenticationContextMismatchesSessionPrincipal() throws Exception {
    resetStores();
    Member admin = createMember("M-ADMIN-009", "admin9@fixyz.com", "ROLE_ADMIN");
    Member target = createMember("M-USER-009", "user9@fixyz.com", "ROLE_USER");

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN", "wrong-admin@fixyz.com");
    String csrfToken = fetchCsrfToken(adminSessionId);

    mockMvc.perform(delete("/api/v1/admin/members/{memberUuid}/sessions", target.getMemberNo())
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .header(HttpHeaders.USER_AGENT, "JUnit-Admin-Client/1.0"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-003"))
        .andExpect(jsonPath("$.message").value("authentication context mismatch"));
  }

  private Member createMember(String memberNo, String email, String role) {
    Member member = memberRepository.saveAndFlush(
        Member.registerUser(memberNo, email, passwordEncoder.encode("Abcd1234!"), memberNo)
    );
    if (!role.equals(member.getRole())) {
      ReflectionTestUtils.setField(member, "role", role);
      member = memberRepository.saveAndFlush(member);
    }
    return member;
  }

  private String createAuthenticatedSession(Member member, String role) {
    return createAuthenticatedSession(member, role, member.getEmail());
  }

  private String createAuthenticatedSession(Member member, String role, String principalNameInSession) {
    Session session = sessionRepository.createSession();
    session.setAttribute("AUTH_MEMBER_ID", member.getId());
    session.setAttribute("AUTH_MEMBER_NAME", member.getName());
    session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principalNameInSession);

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
        member.getEmail(),
        null,
        List.of(new SimpleGrantedAuthority(role))
    ));
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    saveSession(session);
    return session.getId();
  }

  private String fetchCsrfToken(String sessionId) throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf")
            .cookie(sessionCookie(sessionId)))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.path("data").path("token").asText();
  }

  private Cookie sessionCookie(String sessionId) {
    return new Cookie("SESSION", sessionId);
  }

  private MvcResult performWithSingleRetryOn5xx(Supplier<MockHttpServletRequestBuilder> requestSupplier) throws Exception {
    MvcResult latest = null;
    for (int attempt = 0; attempt < 5; attempt++) {
      latest = mockMvc.perform(requestSupplier.get()).andReturn();
      if (latest.getResponse().getStatus() < 500) {
        return latest;
      }
    }
    return latest;
  }

  @SuppressWarnings("unchecked")
  private void saveSession(Session session) {
    ((SessionRepository<Session>) sessionRepository).save(session);
  }
}
