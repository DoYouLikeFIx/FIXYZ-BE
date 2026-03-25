package com.fix.channel.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.entity.AuditAction;
import com.fix.channel.entity.ManualRecoveryQueueEntry;
import com.fix.channel.entity.Member;
import com.fix.channel.entity.OrderSession;
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.ManualRecoveryQueueEntryRepository;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.repository.SecurityEventRepository;
import com.fix.channel.support.ChannelContainersIntegrationTestBase;
import com.fix.channel.support.ManualReplayIdentitySupport;
import com.fix.channel.service.OrderSessionRecoveryService;
import com.fix.common.web.CommonHeaders;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminOrderReplayIntegrationTest extends ChannelContainersIntegrationTestBase {

  private static final String CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174620";
  private static final String APPROVED_BY = "123e4567-e89b-42d3-a456-426614174699";

  private static final WireMockServer WIRE_MOCK_SERVER =
      new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MemberRepository memberRepository;

  @Autowired
  private OrderSessionRepository orderSessionRepository;

  @Autowired
  private AuditLogRepository auditLogRepository;

  @Autowired
  private ManualRecoveryQueueEntryRepository manualRecoveryQueueEntryRepository;

  @Autowired
  private SecurityEventRepository securityEventRepository;

  @Autowired
  private OrderSessionRecoveryService orderSessionRecoveryService;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private SessionRepository<? extends Session> sessionRepository;

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @BeforeAll
  static void startWireMock() {
    WIRE_MOCK_SERVER.start();
  }

  @AfterAll
  static void stopWireMock() {
    WIRE_MOCK_SERVER.stop();
  }

  @DynamicPropertySource
  static void registerCorebankBaseUrl(DynamicPropertyRegistry registry) {
    registry.add("corebank.base-url", WIRE_MOCK_SERVER::baseUrl);
  }

  @BeforeEach
  void resetStores() {
    WIRE_MOCK_SERVER.resetAll();
    securityEventRepository.deleteAll();
    auditLogRepository.deleteAll();
    manualRecoveryQueueEntryRepository.deleteAll();
    orderSessionRepository.deleteAll();
    memberRepository.deleteAll();
    stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
      connection.serverCommands().flushDb();
      return null;
    });
  }

  @Test
  @Tag("epic10-resilience")
  @DisplayName("[E10-RES-003] unresolved requerying order should escalate and converge through admin replay")
  void e10Res003ShouldEscalateUnresolvedRequeryingOrderAndConvergeThroughAdminReplay() throws Exception {
    Member admin = createMember("M-ADMIN-REPLAY-11", "admin-replay-11@fixyz.com", "ROLE_ADMIN");
    Member member = createMember("M-USER-REPLAY-11", "user-replay-11@fixyz.com", "ROLE_USER");
    OrderSession requerying = saveRequeryingSession(member.getId(), 1L, "MARKET", null);
    String operatorId = ManualReplayIdentitySupport.operatorIdFor(admin.getMemberNo());

    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
            urlPathEqualTo("/internal/v1/orders/" + CL_ORD_ID + "/requery"))
        .withQueryParam("attemptCount", equalTo("1"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "orderId": 711,
                    "clOrdId": "%s",
                    "status": "REJECTED",
                    "externalSyncStatus": "REJECTED",
                    "executionResult": "DECLINED",
                    "executedQty": null,
                    "leavesQty": null,
                    "executedPrice": null,
                    "externalOrderId": null,
                    "executedAt": null,
                    "canceledAt": null,
                    "message": "requires manual intervention",
                    "retriable": false,
                    "escalationRequired": false,
                    "attemptCount": 1,
                    "maxRetryCount": 5
                  }
                }
                """.formatted(CL_ORD_ID))));

    orderSessionRecoveryService.runRecoveryCycle();

    OrderSession escalated = orderSessionRepository.findByOrderSessionId(requerying.getOrderSessionId()).orElseThrow();
    assertThat(escalated.getStatus().name()).isEqualTo("ESCALATED");
    assertThat(escalated.getFailureReason()).isEqualTo(OrderSession.ESCALATED_MANUAL_REVIEW);

    ManualRecoveryQueueEntry queueEntry =
        manualRecoveryQueueEntryRepository.findByOrderSessionId(requerying.getOrderSessionId()).orElseThrow();
    assertThat(queueEntry.getAttemptCount()).isEqualTo(1);
    assertThat(queueEntry.getReason()).isEqualTo(OrderSession.ESCALATED_MANUAL_REVIEW);
    assertThat(queueEntry.getPublishedAt()).isNotNull();

    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getAction()).isEqualTo(AuditAction.ORDER_SESSION_RECOVERY_ATTEMPT.value());
          assertThat(log.getOrderSessionId()).isEqualTo(escalated.getId());
          assertThat(log.getDetail()).contains("attemptCount=1");
          assertThat(log.getDetail()).contains("outcome=ESCALATED");
          assertThat(log.getDetail()).contains("recoveryStatus=REJECTED");
        });

    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
            urlPathEqualTo("/internal/v1/orders/" + CL_ORD_ID + "/replay"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "clOrdId": "%s",
                    "finalStatus": "COMPLETED",
                    "executionResult": "FILLED",
                    "executionSource": "VIRTUAL_FILL",
                    "executedQty": 10.0000,
                    "leavesQty": 0.0000,
                    "executedPrice": 72000.0000,
                    "externalOrderId": "FEP-RES-003",
                    "externalSyncStatus": "CONFIRMED",
                    "executedAt": "2026-03-24T10:15:00Z",
                    "canceledAt": null,
                    "processedBy": "%s",
                    "processedAt": "2026-03-24T10:16:00Z"
                  }
                }
                """.formatted(CL_ORD_ID, operatorId))));

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");
    String csrfToken = fetchCsrfToken(adminSessionId);

    mockMvc.perform(post("/api/v1/admin/orders/{clOrdId}/replay", CL_ORD_ID)
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .header(CommonHeaders.X_CORRELATION_ID, "c8d6f0a7-6c91-48f6-b59e-7d5ab53e4e11")
            .header(HttpHeaders.USER_AGENT, "JUnit-Admin-Replay/1.0")
            .contentType("application/json")
            .content(requestJson("APPROVE", 72000L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(CL_ORD_ID))
        .andExpect(jsonPath("$.data.finalStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.executionSource").value("VIRTUAL_FILL"))
        .andExpect(jsonPath("$.data.processedBy").value(operatorId));

    OrderSession converged = orderSessionRepository.findByOrderSessionId(requerying.getOrderSessionId()).orElseThrow();
    assertThat(converged.getStatus().name()).isEqualTo("COMPLETED");
    assertThat(converged.getManualReplayProcessedBy()).isEqualTo(operatorId);
    assertThat(converged.getManualReplayExecutionSource()).isEqualTo("VIRTUAL_FILL");
    assertThat(converged.getExternalOrderId()).isEqualTo("FEP-RES-003");

    ManualRecoveryQueueEntry resolvedQueueEntry =
        manualRecoveryQueueEntryRepository.findByOrderSessionId(requerying.getOrderSessionId()).orElseThrow();
    assertThat(resolvedQueueEntry.getPublishedAt()).isNotNull();
    assertThat(resolvedQueueEntry.getResolvedBy()).isEqualTo(operatorId);
    assertThat(resolvedQueueEntry.getResolution()).isEqualTo("COMPLETED");
    assertThat(resolvedQueueEntry.getResolvedAt()).isNotNull();
    assertThat(manualRecoveryQueueEntryRepository.findByOrderSessionIdAndResolvedAtIsNull(requerying.getOrderSessionId()))
        .isEmpty();

    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getAction()).isEqualTo(AuditAction.MANUAL_REPLAY.value());
          assertThat(log.getOrderSessionId()).isEqualTo(converged.getId());
          assertThat(log.getDetail()).contains("evidenceRef=OPS-INC-20260319-42");
        });

    WIRE_MOCK_SERVER.verify(1, com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
        urlPathEqualTo("/internal/v1/orders/" + CL_ORD_ID + "/requery"))
        .withQueryParam("attemptCount", equalTo("1")));
    WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlPathEqualTo("/internal/v1/orders/" + CL_ORD_ID + "/replay")));
  }

  @Test
  void shouldResolveUnpublishedManualRecoveryEntryWithoutReturningToPendingBacklog() throws Exception {
    Member admin = createMember("M-ADMIN-REPLAY-12", "admin-replay-12@fixyz.com", "ROLE_ADMIN");
    Member member = createMember("M-USER-REPLAY-12", "user-replay-12@fixyz.com", "ROLE_USER");
    OrderSession escalated = saveEscalatedSession(member.getId(), 1L, "MARKET", null);
    String operatorId = ManualReplayIdentitySupport.operatorIdFor(admin.getMemberNo());

    manualRecoveryQueueEntryRepository.save(ManualRecoveryQueueEntry.pending(
        escalated.getOrderSessionId(),
        escalated.getClOrdId(),
        1,
        OrderSession.ESCALATED_MANUAL_REVIEW,
        Instant.parse("2026-03-24T10:14:00Z")
    ));

    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
            urlPathEqualTo("/internal/v1/orders/" + CL_ORD_ID + "/replay"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "clOrdId": "%s",
                    "finalStatus": "COMPLETED",
                    "executionResult": "FILLED",
                    "executionSource": "VIRTUAL_FILL",
                    "executedQty": 10.0000,
                    "leavesQty": 0.0000,
                    "executedPrice": 72000.0000,
                    "externalOrderId": "FEP-RES-003-PENDING",
                    "externalSyncStatus": "CONFIRMED",
                    "executedAt": "2026-03-24T10:15:00Z",
                    "canceledAt": null,
                    "processedBy": "%s",
                    "processedAt": "2026-03-24T10:16:00Z"
                  }
                }
                """.formatted(CL_ORD_ID, operatorId))));

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");
    String csrfToken = fetchCsrfToken(adminSessionId);

    mockMvc.perform(post("/api/v1/admin/orders/{clOrdId}/replay", CL_ORD_ID)
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .header(CommonHeaders.X_CORRELATION_ID, "0b80a3da-3c89-4ebd-b53d-a550b0661e99")
            .header(HttpHeaders.USER_AGENT, "JUnit-Admin-Replay/1.0")
            .contentType("application/json")
            .content(requestJson("APPROVE", 72000L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.finalStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.processedBy").value(operatorId));

    ManualRecoveryQueueEntry resolvedQueueEntry =
        manualRecoveryQueueEntryRepository.findByOrderSessionId(escalated.getOrderSessionId()).orElseThrow();
    assertThat(resolvedQueueEntry.getPublishedAt()).isNull();
    assertThat(resolvedQueueEntry.getResolvedBy()).isEqualTo(operatorId);
    assertThat(resolvedQueueEntry.getResolution()).isEqualTo("COMPLETED");
    assertThat(resolvedQueueEntry.getResolvedAt()).isNotNull();
    assertThat(manualRecoveryQueueEntryRepository.findByOrderSessionIdAndResolvedAtIsNull(escalated.getOrderSessionId()))
        .isEmpty();
    assertThat(manualRecoveryQueueEntryRepository.findByPublishedAtIsNullAndResolvedAtIsNullOrderByEnqueuedAtAscIdAsc(
        PageRequest.of(0, 10)
    )).extracting(ManualRecoveryQueueEntry::getOrderSessionId)
        .doesNotContain(escalated.getOrderSessionId());
  }

  @Test
  void shouldReplayEscalatedOrderAndPersistAuditEvidence() throws Exception {
    Member admin = createMember("M-ADMIN-REPLAY-01", "admin-replay-01@fixyz.com", "ROLE_ADMIN");
    Member member = createMember("M-USER-REPLAY-01", "user-replay-01@fixyz.com", "ROLE_USER");
    OrderSession session = saveEscalatedSession(member.getId(), 1L, "MARKET", null);
    String operatorId = ManualReplayIdentitySupport.operatorIdFor(admin.getMemberNo());

    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
            urlPathEqualTo("/internal/v1/orders/" + CL_ORD_ID + "/replay"))
        .withHeader("X-Internal-Secret", equalTo("local-internal-secret"))
        .withHeader("X-Correlation-Id", equalTo("55b8d2a6-bcca-43fd-b217-d426a2f5d123"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "clOrdId": "%s",
                    "finalStatus": "COMPLETED",
                    "executionResult": "FILLED",
                    "executionSource": "VIRTUAL_FILL",
                    "executedQty": 10.0000,
                    "leavesQty": 0.0000,
                    "executedPrice": 72000.0000,
                    "externalOrderId": null,
                    "externalSyncStatus": "CONFIRMED",
                    "executedAt": "2026-03-19T12:00:00Z",
                    "canceledAt": null,
                    "processedBy": "%s",
                    "processedAt": "2026-03-19T12:01:00Z"
                  }
                }
                """.formatted(CL_ORD_ID, operatorId))));

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");
    String csrfToken = fetchCsrfToken(adminSessionId);

    mockMvc.perform(post("/api/v1/admin/orders/{clOrdId}/replay", CL_ORD_ID)
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .header(CommonHeaders.X_CORRELATION_ID, "55b8d2a6-bcca-43fd-b217-d426a2f5d123")
            .header(HttpHeaders.USER_AGENT, "JUnit-Admin-Replay/1.0")
            .contentType("application/json")
            .content(requestJson("APPROVE", 72000L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(CL_ORD_ID))
        .andExpect(jsonPath("$.data.finalStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.executionSource").value("VIRTUAL_FILL"))
        .andExpect(jsonPath("$.data.processedBy").value(operatorId));

    OrderSession persisted = orderSessionRepository.findByOrderSessionId(session.getOrderSessionId()).orElseThrow();
    assertThat(persisted.getStatus().name()).isEqualTo("COMPLETED");
    assertThat(persisted.getManualReplayProcessedBy()).isEqualTo(operatorId);
    assertThat(persisted.getManualReplayExecutionSource()).isEqualTo("VIRTUAL_FILL");
    assertThat(persisted.getExecutedPrice()).isEqualByComparingTo("72000.0000");

    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getAction()).isEqualTo(AuditAction.MANUAL_REPLAY.value());
          assertThat(log.getOrderSessionId()).isEqualTo(persisted.getId());
          assertThat(log.getDetail()).contains("approvedBy=" + APPROVED_BY);
          assertThat(log.getDetail()).contains("manualDecision=APPROVE");
          assertThat(log.getDetail()).contains("evidenceRef=OPS-INC-20260319-42");
          assertThat(log.getDetail()).contains("executionPriceSource=MANUAL_INPUT");
          assertThat(log.getCorrelationId()).isEqualTo("55b8d2a6-bcca-43fd-b217-d426a2f5d123");
        });

    WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlPathEqualTo("/internal/v1/orders/" + CL_ORD_ID + "/replay")));
  }

  @Test
  void shouldReturnIdempotentReplayResultWithoutSecondCorebankMutation() throws Exception {
    Member admin = createMember("M-ADMIN-REPLAY-02", "admin-replay-02@fixyz.com", "ROLE_ADMIN");
    Member member = createMember("M-USER-REPLAY-02", "user-replay-02@fixyz.com", "ROLE_USER");
    saveEscalatedSession(member.getId(), 1L);
    String operatorId = ManualReplayIdentitySupport.operatorIdFor(admin.getMemberNo());

    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
            urlPathEqualTo("/internal/v1/orders/" + CL_ORD_ID + "/replay"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "clOrdId": "%s",
                    "finalStatus": "FAILED",
                    "executionResult": null,
                    "executionSource": null,
                    "executedQty": null,
                    "leavesQty": null,
                    "executedPrice": null,
                    "externalOrderId": null,
                    "externalSyncStatus": "CONFIRMED",
                    "executedAt": null,
                    "canceledAt": null,
                    "processedBy": "%s",
                    "processedAt": "2026-03-19T12:10:00Z"
                  }
                }
                """.formatted(CL_ORD_ID, operatorId))));

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");
    String csrfToken = fetchCsrfToken(adminSessionId);

    mockMvc.perform(post("/api/v1/admin/orders/{clOrdId}/replay", CL_ORD_ID)
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .contentType("application/json")
            .content(requestJson()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.finalStatus").value("FAILED"));

    mockMvc.perform(post("/api/v1/admin/orders/{clOrdId}/replay", CL_ORD_ID)
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .contentType("application/json")
            .content(requestJson()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.finalStatus").value("FAILED"))
        .andExpect(jsonPath("$.data.processedBy").value(operatorId));

    WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlPathEqualTo("/internal/v1/orders/" + CL_ORD_ID + "/replay")));
  }

  @Test
  void shouldRecordSecurityEventWhenAuthenticatedNonAdminCallsReplay() throws Exception {
    Member nonAdmin = createMember("M-USER-REPLAY-03", "user-replay-03@fixyz.com", "ROLE_USER");
    saveEscalatedSession(nonAdmin.getId(), 1L);

    String sessionId = createAuthenticatedSession(nonAdmin, "ROLE_USER");
    String csrfToken = fetchCsrfToken(sessionId);

    mockMvc.perform(post("/api/v1/admin/orders/{clOrdId}/replay", CL_ORD_ID)
            .cookie(sessionCookie(sessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .header(CommonHeaders.X_CORRELATION_ID, "be244bd8-ad5b-4f8d-bc3a-a8c0be315ded")
            .header(HttpHeaders.USER_AGENT, "JUnit-User-Replay/1.0")
            .contentType("application/json")
            .content(requestJson()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH-006"));

    assertThat(securityEventRepository.findAll())
        .anySatisfy(event -> {
          assertThat(event.getEventType()).isEqualTo("MANUAL_REPLAY_FORBIDDEN");
          assertThat(event.getMemberId()).isEqualTo(nonAdmin.getId());
          assertThat(event.getDetail()).contains("clOrdId=" + CL_ORD_ID);
          assertThat(event.getCorrelationId()).isEqualTo("be244bd8-ad5b-4f8d-bc3a-a8c0be315ded");
          assertThat(event.getIpAddress()).isNotBlank();
        });
  }

  @Test
  void shouldReturnValidation001WhenExecutionPriceIsNonPositive() throws Exception {
    Member admin = createMember("M-ADMIN-REPLAY-04", "admin-replay-04@fixyz.com", "ROLE_ADMIN");
    Member member = createMember("M-USER-REPLAY-04", "user-replay-04@fixyz.com", "ROLE_USER");
    saveEscalatedSession(member.getId(), 1L);

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");
    String csrfToken = fetchCsrfToken(adminSessionId);

    mockMvc.perform(post("/api/v1/admin/orders/{clOrdId}/replay", CL_ORD_ID)
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .contentType("application/json")
            .content("""
                {
                  "manualDecision": "APPROVE",
                  "approvedBy": "%s",
                  "evidenceRef": "OPS-INC-20260319-42",
                  "reason": "KRX outage resolved after manual exchange confirmation",
                  "executionPrice": 0
                }
                """.formatted(APPROVED_BY)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-001"));
  }

  @Test
  void shouldReturnValidation001WhenManualDecisionIsUnsupported() throws Exception {
    Member admin = createMember("M-ADMIN-REPLAY-06", "admin-replay-06@fixyz.com", "ROLE_ADMIN");
    Member member = createMember("M-USER-REPLAY-06", "user-replay-06@fixyz.com", "ROLE_USER");
    saveEscalatedSession(member.getId(), 1L);

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");
    String csrfToken = fetchCsrfToken(adminSessionId);

    mockMvc.perform(post("/api/v1/admin/orders/{clOrdId}/replay", CL_ORD_ID)
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .contentType("application/json")
            .content("""
                {
                  "manualDecision": "approve",
                  "approvedBy": "%s",
                  "evidenceRef": "OPS-INC-20260319-42",
                  "reason": "KRX outage resolved after manual exchange confirmation",
                  "executionPrice": 72000
                }
                """.formatted(APPROVED_BY)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-001"));
  }

  @Test
  void shouldNormalizeCorebankReplayConflictToOrd009() throws Exception {
    Member admin = createMember("M-ADMIN-REPLAY-05", "admin-replay-05@fixyz.com", "ROLE_ADMIN");
    Member member = createMember("M-USER-REPLAY-05", "user-replay-05@fixyz.com", "ROLE_USER");
    saveEscalatedSession(member.getId(), 1L);

    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
            urlPathEqualTo("/internal/v1/orders/" + CL_ORD_ID + "/replay"))
        .willReturn(aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "code": "9009",
                  "message": "replay target must be ESCALATED"
                }
                """)));

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");
    String csrfToken = fetchCsrfToken(adminSessionId);

    mockMvc.perform(post("/api/v1/admin/orders/{clOrdId}/replay", CL_ORD_ID)
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .contentType("application/json")
            .content(requestJson()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORD-009"));
  }

  @Test
  void shouldTreatMalformedReplayJsonAsContractValidationFailure() throws Exception {
    Member admin = createMember("M-ADMIN-REPLAY-07", "admin-replay-07@fixyz.com", "ROLE_ADMIN");
    Member member = createMember("M-USER-REPLAY-07", "user-replay-07@fixyz.com", "ROLE_USER");
    saveEscalatedSession(member.getId(), 1L);

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");
    String csrfToken = fetchCsrfToken(adminSessionId);

    mockMvc.perform(post("/api/v1/admin/orders/{clOrdId}/replay", CL_ORD_ID)
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .contentType("application/json")
            .content("""
                {
                  "manualDecision": "APPROVE",
                  "approvedBy": "%s",
                  "evidenceRef": "OPS-INC-20260319-42",
                  "reason": "KRX outage resolved after manual exchange confirmation",
                  "executionPrice": "oops"
                }
                """.formatted(APPROVED_BY)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-001"));
  }

  @Test
  void shouldNotRecordManualInputWhenReplayUsesFilledExecutionDataWithoutOperatorPrice() throws Exception {
    Member admin = createMember("M-ADMIN-REPLAY-08", "admin-replay-08@fixyz.com", "ROLE_ADMIN");
    Member member = createMember("M-USER-REPLAY-08", "user-replay-08@fixyz.com", "ROLE_USER");
    OrderSession session = saveEscalatedSession(member.getId(), 1L);
    String operatorId = ManualReplayIdentitySupport.operatorIdFor(admin.getMemberNo());

    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
            urlPathEqualTo("/internal/v1/orders/" + CL_ORD_ID + "/replay"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "clOrdId": "%s",
                    "finalStatus": "COMPLETED",
                    "executionResult": "FILLED",
                    "executionSource": "FILLED",
                    "executedQty": 10.0000,
                    "leavesQty": 0.0000,
                    "executedPrice": 72000.0000,
                    "externalOrderId": "FEP-801",
                    "externalSyncStatus": "CONFIRMED",
                    "executedAt": "2026-03-19T12:20:00Z",
                    "canceledAt": null,
                    "processedBy": "%s",
                    "processedAt": "2026-03-19T12:21:00Z"
                  }
                }
                """.formatted(CL_ORD_ID, operatorId))));

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");
    String csrfToken = fetchCsrfToken(adminSessionId);

    mockMvc.perform(post("/api/v1/admin/orders/{clOrdId}/replay", CL_ORD_ID)
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .contentType("application/json")
            .content(requestJsonWithoutExecutionPrice("APPROVE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.executionSource").value("FILLED"));

    OrderSession persisted = orderSessionRepository.findByOrderSessionId(session.getOrderSessionId()).orElseThrow();
    assertThat(persisted.getStatus().name()).isEqualTo("COMPLETED");
    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getAction()).isEqualTo(AuditAction.MANUAL_REPLAY.value());
          assertThat(log.getDetail()).doesNotContain("executionPriceSource=MANUAL_INPUT");
        });
  }

  @Test
  void shouldReplayRejectWithoutManualPriceProvenance() throws Exception {
    Member admin = createMember("M-ADMIN-REPLAY-09", "admin-replay-09@fixyz.com", "ROLE_ADMIN");
    Member member = createMember("M-USER-REPLAY-09", "user-replay-09@fixyz.com", "ROLE_USER");
    saveEscalatedSession(member.getId(), 1L, "MARKET", null);
    String operatorId = ManualReplayIdentitySupport.operatorIdFor(admin.getMemberNo());

    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
            urlPathEqualTo("/internal/v1/orders/" + CL_ORD_ID + "/replay"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "clOrdId": "%s",
                    "finalStatus": "FAILED",
                    "executionResult": null,
                    "executionSource": null,
                    "executedQty": null,
                    "leavesQty": null,
                    "executedPrice": null,
                    "externalOrderId": null,
                    "externalSyncStatus": "CONFIRMED",
                    "executedAt": null,
                    "canceledAt": null,
                    "processedBy": "%s",
                    "processedAt": "2026-03-19T12:31:00Z"
                  }
                }
                """.formatted(CL_ORD_ID, operatorId))));

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");
    String csrfToken = fetchCsrfToken(adminSessionId);

    mockMvc.perform(post("/api/v1/admin/orders/{clOrdId}/replay", CL_ORD_ID)
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .contentType("application/json")
            .content(requestJsonWithoutExecutionPrice("REJECT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.finalStatus").value("FAILED"));

    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getAction()).isEqualTo(AuditAction.MANUAL_REPLAY.value());
          assertThat(log.getDetail()).contains("manualDecision=REJECT");
          assertThat(log.getDetail()).doesNotContain("executionPriceSource=MANUAL_INPUT");
        });
  }

  @Test
  void shouldExposeReplayRateLimitWithContractCode() throws Exception {
    Member admin = createMember("M-ADMIN-REPLAY-10", "admin-replay-10@fixyz.com", "ROLE_ADMIN");
    Member member = createMember("M-USER-REPLAY-10", "user-replay-10@fixyz.com", "ROLE_USER");
    saveEscalatedSession(member.getId(), 1L);

    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
            urlPathEqualTo("/internal/v1/orders/" + CL_ORD_ID + "/replay"))
        .willReturn(aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "message": "replay target must remain escalated"
                }
                """)));

    String adminSessionId = createAuthenticatedSession(admin, "ROLE_ADMIN");
    String csrfToken = fetchCsrfToken(adminSessionId);

    for (int attempt = 0; attempt < 20; attempt++) {
      mockMvc.perform(post("/api/v1/admin/orders/{clOrdId}/replay", CL_ORD_ID)
              .cookie(sessionCookie(adminSessionId))
              .header("X-CSRF-TOKEN", csrfToken)
              .contentType("application/json")
              .content(requestJson()))
          .andExpect(status().isConflict());
    }

    mockMvc.perform(post("/api/v1/admin/orders/{clOrdId}/replay", CL_ORD_ID)
            .cookie(sessionCookie(adminSessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .contentType("application/json")
            .content(requestJson()))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE-001"));
  }

  private OrderSession saveEscalatedSession(Long memberId, Long accountId) {
    return saveEscalatedSession(memberId, accountId, "LIMIT", BigDecimal.valueOf(72000));
  }

  private OrderSession saveRequeryingSession(Long memberId, Long accountId, String orderType, BigDecimal price) {
    OrderSession session = OrderSession.initiated(
        memberId,
        accountId,
        CL_ORD_ID,
        "prepare-fingerprint",
        "005930",
        "BUY",
        orderType,
        BigDecimal.TEN,
        price,
        false,
        "TRUSTED_AUTH_SESSION",
        Instant.parse("2026-03-19T13:00:00Z")
    );
    session.startExecuting();
    session.beginRequerying("EXECUTING_TIMEOUT");
    return orderSessionRepository.saveAndFlush(session);
  }

  private OrderSession saveEscalatedSession(Long memberId, Long accountId, String orderType, BigDecimal price) {
    OrderSession session = OrderSession.initiated(
        memberId,
        accountId,
        CL_ORD_ID,
        "prepare-fingerprint",
        "005930",
        "BUY",
        orderType,
        BigDecimal.TEN,
        price,
        false,
        "TRUSTED_AUTH_SESSION",
        Instant.parse("2026-03-19T13:00:00Z")
    );
    session.startExecuting();
    session.escalate(OrderSession.ESCALATED_MANUAL_REVIEW);
    return orderSessionRepository.saveAndFlush(session);
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
    Session session = sessionRepository.createSession();
    session.setAttribute("AUTH_MEMBER_ID", member.getId());
    session.setAttribute("AUTH_MEMBER_NAME", member.getName());
    session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, member.getEmail());

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
    JsonNode body = objectMapper.readTree(mockMvc.perform(get("/api/v1/auth/csrf")
            .cookie(sessionCookie(sessionId)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString());
    return body.path("data").path("token").asText();
  }

  private Cookie sessionCookie(String sessionId) {
    return new Cookie("SESSION", sessionId);
  }

  @SuppressWarnings("unchecked")
  private void saveSession(Session session) {
    ((SessionRepository<Session>) sessionRepository).save(session);
  }

  private String requestJson() {
    return requestJson("APPROVE", 72000L);
  }

  private String requestJson(String manualDecision, Long executionPrice) {
    String executionPriceField = executionPrice == null ? "" : ",\n  \"executionPrice\": " + executionPrice;
    return """
        {
          "manualDecision": "%s",
          "approvedBy": "%s",
          "evidenceRef": "OPS-INC-20260319-42",
          "reason": "KRX outage resolved after manual exchange confirmation"%s
        }
        """.formatted(manualDecision, APPROVED_BY, executionPriceField);
  }

  private String requestJsonWithoutExecutionPrice(String manualDecision) {
    return requestJson(manualDecision, null);
  }
}
