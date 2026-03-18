package com.fix.channel.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.entity.AuditAction;
import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.Member;
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.service.AuditLogService;
import com.fix.channel.support.ChannelContainersIntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

    mockMvc.perform(get("/api/v1/notifications/stream")
            .cookie(sessionCookie(targetSessionId)))
        .andExpect(status().is4xxClientError());

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
        .andExpect(jsonPath("$.data.content[0].memberId").value(target.getId()));
  }

  @Test
  void shouldRejectAuditQueryWhenFromIsAfterTo() throws Exception {
    resetStores();
    Member admin = createMember("M-ADMIN-005", "admin5@fixyz.com", "ROLE_ADMIN");
    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");

    String from = Instant.now().plusSeconds(3600).toString();
    String to = Instant.now().minusSeconds(3600).toString();

    MvcResult invalidRangeResult = mockMvc.perform(get("/api/v1/admin/audit-logs")
            .cookie(sessionCookie(adminSessionId))
            .param("page", "0")
            .param("size", "20")
            .param("from", from)
            .param("to", to))
        .andExpect(status().is4xxClientError())
        .andReturn();

    int status = invalidRangeResult.getResponse().getStatus();
    String responseBody = invalidRangeResult.getResponse().getContentAsString();

    if (status == HttpStatus.BAD_REQUEST.value()) {
      JsonNode invalidRangeBody = objectMapper.readTree(responseBody);
      assertThat(invalidRangeBody.path("code").asText()).isEqualTo("VALIDATION_001");
      assertThat(invalidRangeBody.path("message").asText("").toLowerCase()).contains("from");
      return;
    }

    if (!responseBody.isBlank()) {
      JsonNode errorBody = objectMapper.readTree(responseBody);
      String errorCode = errorBody.path("code").asText("");
      assertThat(errorCode).isIn("AUTH-003", "RATE_001", "AUTH-006");
    }
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
  void shouldPersistForwardedClientIpInAdminAudit() throws Exception {
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
          assertThat(log.getIpAddress()).isEqualTo("203.0.113.10");
        });
  }

  @Test
  void shouldPersistRealIpWhenForwardedForIsMissing() throws Exception {
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
          assertThat(log.getIpAddress()).isEqualTo("198.51.100.44");
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

  @SuppressWarnings("unchecked")
  private void saveSession(Session session) {
    ((SessionRepository<Session>) sessionRepository).save(session);
  }
}
