package com.fix.channel.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.entity.Member;
import com.fix.channel.entity.OrderSessionStatus;
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.repository.SecurityEventRepository;
import com.fix.channel.service.AccountPositionService;
import com.fix.channel.service.TotpService;
import com.fix.channel.support.ChannelContainersIntegrationTestBase;
import com.fix.channel.vo.AccountPositionResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.valuation.ValuationStatus;
import com.fix.common.valuation.ValuationUnavailableReason;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@AutoConfigureMockMvc
class OrderSessionIntegrationTest extends ChannelContainersIntegrationTestBase {

  private static final WireMockServer WIRE_MOCK_SERVER = new WireMockServer(wireMockConfig().dynamicPort());

  static {
    WIRE_MOCK_SERVER.start();
  }

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("corebank.base-url", WIRE_MOCK_SERVER::baseUrl);
  }

  @AfterAll
  static void stopWireMock() {
    WIRE_MOCK_SERVER.stop();
  }

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private MemberRepository memberRepository;

  @Autowired
  private OrderSessionRepository orderSessionRepository;

  @Autowired
  private AuditLogRepository auditLogRepository;

  @Autowired
  private SecurityEventRepository securityEventRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Autowired
  private TotpService totpService;

  @Autowired
  private StubAccountPositionService accountPositionService;

  @Autowired
  private MutableClock clock;

  @BeforeEach
  void setUp() {
    WIRE_MOCK_SERVER.resetAll();
    orderSessionRepository.deleteAll();
    auditLogRepository.deleteAll();
    securityEventRepository.deleteAll();
    memberRepository.deleteAll();
    stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
      connection.serverCommands().flushDb();
      return null;
    });
    clock.setInstant(Instant.parse("2026-03-20T00:00:04Z"));
    accountPositionService.reset();
  }

  @Test
  void shouldCreatePendingNewOrderSessionWithRedisTtl() throws Exception {
    saveLinkedMember("M-ORD-001", "order.user@fixyz.com", "Order User", 101L, "12345678901234");

    AuthSession authSession = login("order.user@fixyz.com", "Abcd1234!");
    JsonNode response = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174260",
        101L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );

    String orderSessionId = response.path("data").path("orderSessionId").asText();
    long remainingSeconds = response.path("data").path("remainingSeconds").asLong();

    assertThat(orderSessionId).isNotBlank();
    assertThat(response.path("data").path("clOrdId").asText()).isEqualTo("123e4567-e89b-42d3-a456-426614174260");
    assertThat(response.path("data").path("status").asText()).isEqualTo("PENDING_NEW");
    assertThat(response.path("data").path("challengeRequired").asBoolean()).isTrue();
    assertThat(response.path("data").path("authorizationReason").asText()).isEqualTo("ELEVATED_ORDER_RISK");
    assertThat(response.path("data").path("accountId").asLong()).isEqualTo(101L);
    assertThat(response.path("data").path("symbol").asText()).isEqualTo("005930");
    assertThat(response.path("data").path("side").asText()).isEqualTo("BUY");
    assertThat(response.path("data").path("orderType").asText()).isEqualTo("LIMIT");
    assertThat(response.path("data").path("qty").asLong()).isEqualTo(10L);
    assertThat(response.path("data").path("price").asLong()).isEqualTo(72000L);
    assertThat(response.path("data").path("createdAt").asText()).isNotBlank();
    assertThat(response.path("data").path("updatedAt").asText()).isNotBlank();
    assertThat(response.path("data").path("expiresAt").asText()).isNotBlank();
    assertThat(remainingSeconds).isBetween(1L, 3600L);
    assertThat(stringRedisTemplate.hasKey("ch:order-session:" + orderSessionId)).isTrue();
    assertThat(stringRedisTemplate.getExpire("ch:order-session:" + orderSessionId)).isPositive();
    assertThat(stringRedisTemplate.opsForValue().get("ch:otp-attempts:" + orderSessionId)).isEqualTo("3");
    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getAction()).isEqualTo("ORDER_SESSION_CREATE");
          assertThat(log.getTargetId()).isEqualTo(orderSessionId);
        });
  }

  @Test
  void shouldPopulateQuoteMetadataForMarketPrepareResponse() throws Exception {
    saveLinkedMember("M-ORD-001A", "market.prepare@fixyz.com", "Market Prepare", 141L, "12345678901274");

    AuthSession authSession = login("market.prepare@fixyz.com", "Abcd1234!");
    JsonNode response = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174277",
        141L,
        "005930",
        "BUY",
        "MARKET",
        10,
        null
    );

    String orderSessionId = response.path("data").path("orderSessionId").asText();
    assertThat(response.path("data").path("price").isNull()).isTrue();
    assertThat(response.path("data").path("quoteSnapshotId").asText()).isEqualTo("qsnap_005930_live_001");
    assertThat(response.path("data").path("quoteAsOf").asText()).isEqualTo("2026-03-20T00:00:00Z");
    assertThat(response.path("data").path("quoteSourceMode").asText()).isEqualTo("LIVE");
    assertThat(response.path("data").path("preTradePrice").decimalValue()).isEqualByComparingTo("72050.0000");

    assertThat(orderSessionRepository.findByOrderSessionId(orderSessionId))
        .hasValueSatisfying(session -> {
          assertThat(session.getQuoteSnapshotId()).isEqualTo("qsnap_005930_live_001");
          assertThat(session.getQuoteAsOf()).isEqualTo(Instant.parse("2026-03-20T00:00:00Z"));
          assertThat(session.getQuoteSourceMode()).isEqualTo(FepQuoteSourceMode.LIVE);
          assertThat(session.getPreTradePrice()).isEqualByComparingTo("72050.0000");
        });
  }

  @Test
  void shouldCreateMarketPrepareWhenQuoteAgeMatchesThresholdExactly() throws Exception {
    saveLinkedMember("M-ORD-001AA", "market.threshold@fixyz.com", "Market Threshold", 143L, "12345678901276");

    AuthSession authSession = login("market.threshold@fixyz.com", "Abcd1234!");
    clock.setInstant(Instant.parse("2026-03-20T00:00:10Z"));
    accountPositionService.setQuoteAsOf(clock.instant().minusMillis(5_000L));

    JsonNode response = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174279",
        143L,
        "005930",
        "BUY",
        "MARKET",
        10,
        null
    );

    assertThat(response.path("data").path("quoteSnapshotId").asText()).isEqualTo("qsnap_005930_live_001");
    assertThat(response.path("data").path("quoteAsOf").asText()).isEqualTo("2026-03-20T00:00:05Z");
    assertThat(response.path("data").path("quoteSourceMode").asText()).isEqualTo("LIVE");
    assertThat(response.path("data").path("preTradePrice").decimalValue()).isEqualByComparingTo("72050.0000");
  }

  @Test
  void shouldRejectMarketPrepareWhenQuoteAgeExceedsThresholdByOneMillisecond() throws Exception {
    saveLinkedMember("M-ORD-001AB", "market.threshold.stale@fixyz.com", "Market Threshold Stale", 144L, "12345678901277");

    AuthSession authSession = login("market.threshold.stale@fixyz.com", "Abcd1234!");
    clock.setInstant(Instant.parse("2026-03-20T00:00:10Z"));
    accountPositionService.setQuoteAsOf(clock.instant().minusMillis(5_001L));

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .header("X-ClOrdID", "123e4567-e89b-42d3-a456-426614174280")
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload(144L, "005930", "BUY", "MARKET", 10, null)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-003"))
        .andExpect(jsonPath("$.message").value("Stale quote"))
        .andExpect(jsonPath("$.details.symbol").value("005930"))
        .andExpect(jsonPath("$.details.snapshotAgeMs").value(5001))
        .andExpect(jsonPath("$.details.quoteSourceMode").value("LIVE"))
        .andExpect(jsonPath("$.details.quoteSnapshotId").value("qsnap_005930_live_001"));

    assertThat(orderSessionRepository.findByClOrdId("123e4567-e89b-42d3-a456-426614174280")).isEmpty();
    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getAction()).isEqualTo("ORDER_SESSION_FAILED");
          assertThat(log.getTargetId()).isEqualTo("123e4567-e89b-42d3-a456-426614174280");
          assertThat(log.getDetail()).contains("snapshotAgeMs=5001");
          assertThat(log.getDetail()).contains("quoteSourceMode=LIVE");
        });
  }

  @Test
  void shouldRejectMarketPrepareWhenQuoteIsStaleAndAuditIt() throws Exception {
    saveLinkedMember("M-ORD-001B", "market.stale@fixyz.com", "Market Stale", 142L, "12345678901275");

    AuthSession authSession = login("market.stale@fixyz.com", "Abcd1234!");
    accountPositionService.setQuoteSnapshotId("qsnap_005930_live_999");
    accountPositionService.setQuoteAsOf(clock.instant().minusMillis(6_000L));

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .header("X-ClOrdID", "123e4567-e89b-42d3-a456-426614174278")
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload(142L, "005930", "BUY", "MARKET", 10, null)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-003"))
        .andExpect(jsonPath("$.message").value("Stale quote"));

    assertThat(orderSessionRepository.findByClOrdId("123e4567-e89b-42d3-a456-426614174278")).isEmpty();
    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getAction()).isEqualTo("ORDER_SESSION_FAILED");
          assertThat(log.getTargetId()).isEqualTo("123e4567-e89b-42d3-a456-426614174278");
          assertThat(log.getDetail()).contains("reason=STALE_QUOTE");
          assertThat(log.getDetail()).contains("symbol=005930");
          assertThat(log.getDetail()).contains("snapshotAgeMs=6000");
          assertThat(log.getDetail()).contains("quoteSourceMode=LIVE");
        });
  }

  @Test
  void shouldRejectMarketPrepareWhenQuoteIsUnavailableAndAuditIt() throws Exception {
    saveLinkedMember("M-ORD-001C", "market.unavailable@fixyz.com", "Market Unavailable", 143L, "12345678901276");

    AuthSession authSession = login("market.unavailable@fixyz.com", "Abcd1234!");
    accountPositionService.setUnavailableQuote(ValuationUnavailableReason.PROVIDER_UNAVAILABLE);

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .header("X-ClOrdID", "123e4567-e89b-42d3-a456-426614174279")
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload(143L, "005930", "BUY", "MARKET", 10, null)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-003"))
        .andExpect(jsonPath("$.message").value("Stale quote"))
        .andExpect(jsonPath("$.details.symbol").value("005930"))
        .andExpect(jsonPath("$.details.reason").value("PROVIDER_UNAVAILABLE"))
        .andExpect(jsonPath("$.details.snapshotAgeMs").doesNotExist())
        .andExpect(jsonPath("$.details.quoteSourceMode").doesNotExist())
        .andExpect(jsonPath("$.details.quoteSnapshotId").doesNotExist());

    assertThat(orderSessionRepository.findByClOrdId("123e4567-e89b-42d3-a456-426614174279")).isEmpty();
    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getAction()).isEqualTo("ORDER_SESSION_FAILED");
          assertThat(log.getTargetId()).isEqualTo("123e4567-e89b-42d3-a456-426614174279");
          assertThat(log.getDetail()).contains("reason=STALE_QUOTE");
          assertThat(log.getDetail()).contains("symbol=005930");
          assertThat(log.getDetail()).contains("snapshotAgeMs=unknown");
          assertThat(log.getDetail()).contains("quoteSourceMode=unknown");
          assertThat(log.getDetail()).contains("quoteSnapshotId=unknown");
        });
  }

  @Test
  void shouldReturnOwnedOrderSessionStatus() throws Exception {
    saveLinkedMember("M-ORD-002", "status.user@fixyz.com", "Status User", 102L, "12345678901235");

    AuthSession authSession = login("status.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174261",
        102L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();
    String createdExpiresAt = created.path("data").path("expiresAt").asText();

    mockMvc.perform(get("/api/v1/orders/sessions/{orderSessionId}", orderSessionId)
            .cookie(sessionCookie(authSession)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.orderSessionId").value(orderSessionId))
        .andExpect(jsonPath("$.data.clOrdId").value("123e4567-e89b-42d3-a456-426614174261"))
        .andExpect(jsonPath("$.data.accountId").value(102L))
        .andExpect(jsonPath("$.data.symbol").value("005930"))
        .andExpect(jsonPath("$.data.side").value("BUY"))
        .andExpect(jsonPath("$.data.orderType").value("LIMIT"))
        .andExpect(jsonPath("$.data.qty").value(10))
        .andExpect(jsonPath("$.data.price").value(72000))
        .andExpect(jsonPath("$.data.status").value("PENDING_NEW"))
        .andExpect(jsonPath("$.data.challengeRequired").value(true))
        .andExpect(jsonPath("$.data.authorizationReason").value("ELEVATED_ORDER_RISK"))
        .andExpect(jsonPath("$.data.remainingSeconds").isNumber())
        .andExpect(jsonPath("$.data.expiresAt").value(createdExpiresAt));
  }

  @Test
  void shouldAutoAuthorizeLowRiskOrderWhenTrustedAuthSessionWindowIsFresh() throws Exception {
    saveLinkedMember("M-ORD-002AA", "authed.user@fixyz.com", "Authed User", 121L, "12345678901254");

    AuthSession authSession = login("authed.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174290",
        121L,
        "005930",
        "BUY",
        "LIMIT",
        1,
        10000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    assertThat(created.path("data").path("status").asText()).isEqualTo("AUTHED");
    assertThat(created.path("data").path("challengeRequired").asBoolean()).isFalse();
    assertThat(created.path("data").path("authorizationReason").asText()).isEqualTo("TRUSTED_AUTH_SESSION");

    mockMvc.perform(get("/api/v1/orders/sessions/{orderSessionId}", orderSessionId)
            .cookie(sessionCookie(authSession)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("AUTHED"))
        .andExpect(jsonPath("$.data.challengeRequired").value(false))
        .andExpect(jsonPath("$.data.authorizationReason").value("TRUSTED_AUTH_SESSION"));
  }

  @Test
  void shouldCompleteLowRiskTrustedOrderExecutionHappyPath() throws Exception {
    saveLinkedMember("M-ORD-002AAX", "trusted.execute.user@fixyz.com", "Trusted Execute User", 126L, "12345678901259");

    AuthSession authSession = login("trusted.execute.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174291",
        126L,
        "005930",
        "BUY",
        "LIMIT",
        1,
        10000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    assertThat(created.path("data").path("status").asText()).isEqualTo("AUTHED");
    stubCorebankExecuteSuccess("123e4567-e89b-42d3-a456-426614174291", 91001L, 1, 10000L, "FEP-KRX-91001");

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/execute", orderSessionId)
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.clOrdId").value("123e4567-e89b-42d3-a456-426614174291"))
        .andExpect(jsonPath("$.data.status").value("COMPLETED"))
        .andExpect(jsonPath("$.data.executionResult").value("FILLED"))
        .andExpect(jsonPath("$.data.executedQty").value(1))
        .andExpect(jsonPath("$.data.leavesQty").value(0))
        .andExpect(jsonPath("$.data.executedPrice").value(10000))
        .andExpect(jsonPath("$.data.externalOrderId").value("FEP-KRX-91001"))
        .andExpect(jsonPath("$.data.externalSyncStatus").value("CONFIRMED"))
        .andExpect(jsonPath("$.data.idempotent").value(false))
        .andExpect(jsonPath("$.data.failureReason").doesNotExist())
        .andExpect(jsonPath("$.data.executedAt").value("2026-03-12T00:06:00Z"));

    assertThat(orderSessionRepository.findByOrderSessionId(orderSessionId))
        .hasValueSatisfying(session -> {
          assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.COMPLETED);
          assertThat(session.getExecutionResult()).isEqualTo("FILLED");
          assertThat(session.getExecutedQty()).isEqualByComparingTo("1.0000");
          assertThat(session.getLeavesQty()).isEqualByComparingTo("0.0000");
          assertThat(session.getExecutedPrice()).isEqualByComparingTo("10000.0000");
          assertThat(session.getExternalOrderId()).isEqualTo("FEP-KRX-91001");
          assertThat(session.getExternalSyncStatus()).isEqualTo("CONFIRMED");
        });
    WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo("/internal/v1/orders")));
  }

  @Test
  void shouldExtendOwnedOrderSessionToFullWindow() throws Exception {
    saveLinkedMember("M-ORD-002AAA", "extend.user@fixyz.com", "Extend User", 124L, "12345678901257");

    AuthSession authSession = login("extend.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174296",
        124L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();
    String createdExpiresAt = created.path("data").path("expiresAt").asText();

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/extend", orderSessionId)
            .cookie(sessionCookie(authSession))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.orderSessionId").value(orderSessionId))
        .andExpect(jsonPath("$.data.remainingSeconds").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3590)))
        .andExpect(result -> {
          JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
          assertThat(Instant.parse(body.path("data").path("expiresAt").asText()))
              .isAfterOrEqualTo(Instant.parse(createdExpiresAt));
        });

    assertThat(orderSessionRepository.findByOrderSessionId(orderSessionId))
        .hasValueSatisfying(session -> assertThat(session.getExpiresAt()).isAfterOrEqualTo(Instant.parse(createdExpiresAt)));
    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> assertThat(log.getAction()).isEqualTo("ORDER_SESSION_EXTENDED"));
  }

  @Test
  void shouldRejectExecuteWhenAuthedSessionRedisTtlHasAlreadyDisappeared() throws Exception {
    saveLinkedMember("M-ORD-002AAB", "execute-expired.user@fixyz.com", "Execute Expired User", 131L, "12345678901264");

    AuthSession authSession = login("execute-expired.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174398",
        131L,
        "005930",
        "BUY",
        "LIMIT",
        1,
        10000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    assertThat(created.path("data").path("status").asText()).isEqualTo("AUTHED");
    stringRedisTemplate.delete("ch:order-session:" + orderSessionId);

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/execute", orderSessionId)
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORD-008"))
        .andExpect(jsonPath("$.message").value("Order session not found."));

    assertThat(orderSessionRepository.findByOrderSessionId(orderSessionId))
        .hasValueSatisfying(session -> assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.EXPIRED));
    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getAction()).isEqualTo("ORDER_SESSION_EXPIRED");
          assertThat(log.getTargetId()).isEqualTo(orderSessionId);
        });
  }

  @Test
  void shouldBlockExecuteWhenElevatedRiskSessionIsNotStepUpAuthorized() throws Exception {
    saveLinkedMember("M-ORD-002AAY", "stepup.block.user@fixyz.com", "Step Up Block User", 127L, "12345678901260");

    AuthSession authSession = login("stepup.block.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174397",
        127L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    assertThat(created.path("data").path("status").asText()).isEqualTo("PENDING_NEW");
    assertThat(created.path("data").path("challengeRequired").asBoolean()).isTrue();
    assertThat(created.path("data").path("authorizationReason").asText()).isEqualTo("ELEVATED_ORDER_RISK");

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/execute", orderSessionId)
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORD-009"))
        .andExpect(jsonPath("$.message").value("order session is not authorized for execution"));

    assertThat(orderSessionRepository.findByOrderSessionId(orderSessionId))
        .hasValueSatisfying(session -> assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.PENDING_NEW));
    WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlEqualTo("/internal/v1/orders")));
  }

  @Test
  void shouldVerifyOtpAndAuthorizePendingOrderSession() throws Exception {
    Member member = saveLinkedMember("M-ORD-002AB", "otp.user@fixyz.com", "Otp User", 122L, "12345678901255");

    AuthSession authSession = login("otp.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174295",
        122L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();
    String freshStepUpCode = nextTotpWindowCode(member);

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/otp/verify", orderSessionId)
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "otpCode", freshStepUpCode
            ))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.orderSessionId").value(orderSessionId))
        .andExpect(jsonPath("$.data.status").value("AUTHED"))
        .andExpect(jsonPath("$.data.challengeRequired").value(true))
        .andExpect(jsonPath("$.data.authorizationReason").value("ELEVATED_ORDER_RISK"));

    assertThat(orderSessionRepository.findByOrderSessionId(orderSessionId))
        .hasValueSatisfying(session -> assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.AUTHED));
    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getAction()).isEqualTo("ORDER_SESSION_OTP_VERIFIED");
          assertThat(log.getTargetId()).isEqualTo(orderSessionId);
        });
  }

  @Test
  void shouldAllowPendingSessionSuccessMarkerReplayWithinTotpWindow() throws Exception {
    Member member = saveLinkedMember(
        "M-ORD-002ABR",
        "otp-idempotent.user@fixyz.com",
        "Otp Idempotent User",
        129L,
        "12345678901262"
    );

    AuthSession authSession = login("otp-idempotent.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174396",
        129L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();
    String replayCode = nextTotpWindowCode(member);
    long windowIndex = clock.instant().getEpochSecond() / 30L;

    stringRedisTemplate.opsForValue().set(
        "ch:otp-success:" + orderSessionId + ":" + windowIndex,
        replayCode,
        Duration.ofSeconds(60)
    );
    stringRedisTemplate.opsForValue().set(
        "ch:totp-used:" + member.getId() + ":" + windowIndex + ":" + replayCode,
        "1",
        Duration.ofSeconds(60)
    );

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/otp/verify", orderSessionId)
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "otpCode", replayCode
            ))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.orderSessionId").value(orderSessionId))
        .andExpect(jsonPath("$.data.status").value("AUTHED"))
        .andExpect(jsonPath("$.data.challengeRequired").value(true))
        .andExpect(jsonPath("$.data.authorizationReason").value("ELEVATED_ORDER_RISK"));

    assertThat(orderSessionRepository.findByOrderSessionId(orderSessionId))
        .hasValueSatisfying(session -> assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.AUTHED));
    assertThat(stringRedisTemplate.opsForValue().get("ch:otp-attempts:" + orderSessionId)).isEqualTo("3");
  }

  @Test
  void shouldReturnOrd009ForAlreadyAuthorizedSessionEvenWhenSuccessMarkerExists() throws Exception {
    Member member = saveLinkedMember(
        "M-ORD-002ABS",
        "otp-authed.user@fixyz.com",
        "Otp Authed User",
        130L,
        "12345678901263"
    );

    AuthSession authSession = login("otp-authed.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174397",
        130L,
        "005930",
        "BUY",
        "LIMIT",
        1,
        10000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();
    String replayCode = totpService.currentCode(member);
    long windowIndex = clock.instant().getEpochSecond() / 30L;

    assertThat(created.path("data").path("status").asText()).isEqualTo("AUTHED");

    stringRedisTemplate.opsForValue().set(
        "ch:otp-success:" + orderSessionId + ":" + windowIndex,
        replayCode,
        Duration.ofSeconds(60)
    );

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/otp/verify", orderSessionId)
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "otpCode", replayCode
            ))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORD-009"));
  }

  @Test
  void shouldRejectVerifyWhenPendingSessionDoesNotRequireChallenge() throws Exception {
    Member member = saveLinkedMember("M-ORD-002ABX", "otp-guard.user@fixyz.com", "Otp Guard User", 125L, "12345678901258");

    AuthSession authSession = login("otp-guard.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174391",
        125L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();
    orderSessionRepository.findByOrderSessionId(orderSessionId).ifPresent(session -> {
      ReflectionTestUtils.setField(session, "challengeRequired", false);
      orderSessionRepository.saveAndFlush(session);
    });

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/otp/verify", orderSessionId)
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "otpCode", totpService.currentCode(member)
            ))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORD-009"));
  }

  @Test
  void shouldRejectInvalidOtpAndExposeRemainingAttempts() throws Exception {
    saveLinkedMember("M-ORD-002AC", "otp-fail.user@fixyz.com", "Otp Fail User", 123L, "12345678901256");

    AuthSession authSession = login("otp-fail.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174296",
        123L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/otp/verify", orderSessionId)
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "otpCode", "000000"
            ))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("CHANNEL-002"))
        .andExpect(jsonPath("$.message").value("otp code mismatch"))
        .andExpect(jsonPath("$.remainingAttempts").value(2));
  }

  @Test
  void shouldThrottleRapidDuplicateVerifyWithoutConsumingAttempts() throws Exception {
    Member member = saveLinkedMember(
        "M-ORD-002ACY",
        "otp-throttle.user@fixyz.com",
        "Otp Throttle User",
        126L,
        "12345678901259"
    );

    AuthSession authSession = login("otp-throttle.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174392",
        126L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/otp/verify", orderSessionId)
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "otpCode", "000000"
            ))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("CHANNEL-002"))
        .andExpect(jsonPath("$.remainingAttempts").value(2));

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/otp/verify", orderSessionId)
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "otpCode", "000000"
            ))))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_001"));

    assertThat(stringRedisTemplate.opsForValue().get("ch:otp-attempts:" + orderSessionId)).isEqualTo("2");
    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getAction()).isEqualTo("ORDER_SESSION_OTP_RATE_LIMITED");
          assertThat(log.getTargetId()).isEqualTo(orderSessionId);
        });
    assertThat(securityEventRepository.findAll())
        .anySatisfy(event -> {
          assertThat(event.getMemberId()).isEqualTo(member.getId());
          assertThat(event.getEventType()).isEqualTo("ORDER_SESSION_OTP_RATE_LIMITED");
          assertThat(event.getSeverity()).isEqualTo("MEDIUM");
        });
  }

  @Test
  void shouldRejectSameWindowTotpReplayAcrossPendingOrderSessions() throws Exception {
    Member member = saveLinkedMember("M-ORD-002ACZ", "otp-replay.user@fixyz.com", "Otp Replay User", 127L, "12345678901260");

    AuthSession authSession = login("otp-replay.user@fixyz.com", "Abcd1234!");
    JsonNode firstSession = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174393",
        127L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );
    JsonNode secondSession = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174394",
        127L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );
    String replayCode = nextTotpWindowCode(member);

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/otp/verify",
                firstSession.path("data").path("orderSessionId").asText())
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "otpCode", replayCode
            ))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("AUTHED"));

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/otp/verify",
                secondSession.path("data").path("orderSessionId").asText())
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "otpCode", replayCode
            ))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-011"));

    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getAction()).isEqualTo("ORDER_SESSION_OTP_REPLAYED");
          assertThat(log.getTargetId()).isEqualTo(secondSession.path("data").path("orderSessionId").asText());
        });
    assertThat(securityEventRepository.findAll())
        .anySatisfy(event -> {
          assertThat(event.getMemberId()).isEqualTo(member.getId());
          assertThat(event.getEventType()).isEqualTo("ORDER_SESSION_OTP_REPLAYED");
          assertThat(event.getSeverity()).isEqualTo("HIGH");
        });
  }

  @Test
  void shouldFailSessionAfterThirdOtpMismatchAndRejectFurtherVerifyOrExecute() throws Exception {
    saveLinkedMember("M-ORD-002ADA", "otp-exhaust.user@fixyz.com", "Otp Exhaust User", 128L, "12345678901261");

    AuthSession authSession = login("otp-exhaust.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174395",
        128L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/otp/verify", orderSessionId)
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "otpCode", "000000"
            ))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("CHANNEL-002"))
        .andExpect(jsonPath("$.remainingAttempts").value(2));
    stringRedisTemplate.delete("ch:otp-attempt-ts:" + orderSessionId);

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/otp/verify", orderSessionId)
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "otpCode", "000000"
            ))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("CHANNEL-002"))
        .andExpect(jsonPath("$.remainingAttempts").value(1));
    stringRedisTemplate.delete("ch:otp-attempt-ts:" + orderSessionId);

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/otp/verify", orderSessionId)
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "otpCode", "000000"
            ))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CHANNEL-003"));

    assertThat(orderSessionRepository.findByOrderSessionId(orderSessionId))
        .hasValueSatisfying(session -> {
          assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.FAILED);
          assertThat(session.getFailureReason()).isEqualTo("OTP_EXCEEDED");
        });

    stringRedisTemplate.delete("ch:otp-attempt-ts:" + orderSessionId);

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/otp/verify", orderSessionId)
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "otpCode", "000000"
            ))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORD-009"));

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/execute", orderSessionId)
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORD-009"));
  }

  @Test
  void shouldSerializeFailedOtpExceededLookupWithoutActiveWindowMetadata() throws Exception {
    saveLinkedMember("M-ORD-002ABX", "otp.failed.contract@fixyz.com", "Otp Failed Contract", 133L, "12345678901266");

    AuthSession authSession = login("otp.failed.contract@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174400",
        133L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    for (int attempt = 0; attempt < 3; attempt += 1) {
      mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/otp/verify", orderSessionId)
              .cookie(sessionCookie(authSession))
              .header("X-CSRF-TOKEN", authSession.csrfToken())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(Map.of(
                  "otpCode", "000000"
              ))))
          .andExpect(attempt < 2 ? status().isUnprocessableEntity() : status().isForbidden());
      stringRedisTemplate.delete("ch:otp-attempt-ts:" + orderSessionId);
    }

    MvcResult result = mockMvc.perform(get("/api/v1/orders/sessions/{orderSessionId}", orderSessionId)
            .cookie(sessionCookie(authSession)))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    assertThat(data.path("status").asText()).isEqualTo("FAILED");
    assertThat(data.has("expiresAt")).isFalse();
    assertThat(data.has("remainingSeconds")).isFalse();
    assertThat(data.path("failureReason").asText()).isEqualTo("OTP_EXCEEDED");
    assertThat(data.path("executionResult").isNull()).isTrue();
    assertThat(data.path("externalOrderId").isNull()).isTrue();
    assertThat(data.path("canceledAt").isNull()).isTrue();
  }

  @Test
  void shouldRejectCreateWhenAvailableCashIsInsufficient() throws Exception {
    accountPositionService.setAvailableBalance(BigDecimal.valueOf(10_000));
    saveLinkedMember("M-ORD-002AB", "cash.user@fixyz.com", "Cash User", 122L, "12345678901255");

    AuthSession authSession = login("cash.user@fixyz.com", "Abcd1234!");

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .header("X-ClOrdID", "123e4567-e89b-42d3-a456-426614174291")
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload(122L, "005930", "BUY", "LIMIT", 10, 72000L)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("ORD-001"))
        .andExpect(jsonPath("$.message").value("available cash is insufficient"))
        .andExpect(jsonPath("$.userMessageKey").value("error.order.insufficient_cash"))
        .andExpect(jsonPath("$.operatorCode").value("INSUFFICIENT_CASH"));
  }

  @Test
  void shouldRejectCreateWhenAvailableQuantityIsInsufficient() throws Exception {
    accountPositionService.setAvailableQuantity(BigDecimal.valueOf(5));
    saveLinkedMember("M-ORD-002AC", "position.user@fixyz.com", "Position User", 123L, "12345678901256");

    AuthSession authSession = login("position.user@fixyz.com", "Abcd1234!");

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .header("X-ClOrdID", "123e4567-e89b-42d3-a456-426614174292")
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload(123L, "005930", "SELL", "LIMIT", 10, 72000L)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("ORD-003"))
        .andExpect(jsonPath("$.message").value("insufficient position quantity"))
        .andExpect(jsonPath("$.userMessageKey").value("error.order.insufficient_position"))
        .andExpect(jsonPath("$.operatorCode").value("INSUFFICIENT_POSITION"));
  }

  @Test
  void shouldRequireStepUpWhenRequestDeviceContextDiffersFromLogin() throws Exception {
    saveLinkedMember("M-ORD-002AD", "context.user@fixyz.com", "Context User", 124L, "12345678901257");

    AuthSession authSession = login("context.user@fixyz.com", "Abcd1234!");

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("User-Agent", "Different-Device/1.0")
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .header("X-ClOrdID", "123e4567-e89b-42d3-a456-426614174293")
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload(124L, "005930", "BUY", "LIMIT", 1, 10000L)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("PENDING_NEW"))
        .andExpect(jsonPath("$.data.challengeRequired").value(true))
        .andExpect(jsonPath("$.data.authorizationReason").value("ELEVATED_ORDER_RISK"));
  }

  @Test
  void shouldReturnExistingOrderSessionWhenOwnerRecreatesActiveSession() throws Exception {
    saveLinkedMember("M-ORD-002B", "repeat.user@fixyz.com", "Repeat User", 103L, "12345678901236");

    AuthSession authSession = login("repeat.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174262",
        103L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();
    String expiresAt = created.path("data").path("expiresAt").asText();
    long firstRemainingSeconds = created.path("data").path("remainingSeconds").asLong();

    JsonNode recreated = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174262",
        103L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L,
        status().isOk()
    );

    assertThat(recreated.path("data").path("orderSessionId").asText()).isEqualTo(orderSessionId);
    assertThat(recreated.path("data").path("status").asText()).isEqualTo("PENDING_NEW");
    assertThat(recreated.path("data").path("challengeRequired").asBoolean()).isTrue();
    assertThat(recreated.path("data").path("authorizationReason").asText()).isEqualTo("ELEVATED_ORDER_RISK");
    assertThat(recreated.path("data").path("expiresAt").asText()).isEqualTo(expiresAt);
    assertThat(recreated.path("data").path("remainingSeconds").asLong()).isBetween(1L, firstRemainingSeconds);
    assertThat(auditLogRepository.count()).isEqualTo(1L);
  }

  @Test
  void shouldRejectReplayWhenOrderPayloadChanges() throws Exception {
    saveLinkedMember("M-ORD-002C", "mismatch.user@fixyz.com", "Mismatch User", 104L, "12345678901237");

    AuthSession authSession = login("mismatch.user@fixyz.com", "Abcd1234!");
    createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174263",
        104L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .header("X-ClOrdID", "123e4567-e89b-42d3-a456-426614174263")
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload(104L, "005930", "BUY", "LIMIT", 10, 72100L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ORD_001"))
        .andExpect(jsonPath("$.message").value("clOrdId replay payload mismatch"))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions"));
  }

  @Test
  void shouldRejectCreateWhenLinkedAccountDoesNotBelongToSessionMember() throws Exception {
    saveLinkedMember("M-ORD-002D", "ownership.user@fixyz.com", "Ownership User", 105L, "12345678901238");

    AuthSession authSession = login("ownership.user@fixyz.com", "Abcd1234!");

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .header("X-ClOrdID", "123e4567-e89b-42d3-a456-426614174264")
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload(999L, "005930", "BUY", "LIMIT", 10, 72000L)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CHANNEL-006"))
        .andExpect(jsonPath("$.message").value("Access denied."))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions"));
  }

  @Test
  void shouldRejectNonOwnerOrderSessionStatusLookup() throws Exception {
    saveLinkedMember("M-ORD-003A", "owner.user@fixyz.com", "Owner User", 106L, "12345678901239");
    saveLinkedMember("M-ORD-003B", "intruder.user@fixyz.com", "Intruder User", 107L, "12345678901240");

    AuthSession owner = login("owner.user@fixyz.com", "Abcd1234!");
    AuthSession intruder = login("intruder.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        owner,
        "123e4567-e89b-42d3-a456-426614174265",
        106L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    mockMvc.perform(get("/api/v1/orders/sessions/{orderSessionId}", orderSessionId)
            .cookie(sessionCookie(intruder)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CHANNEL-006"))
        .andExpect(jsonPath("$.message").value("Access denied."))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions/" + orderSessionId));
  }

  @Test
  void shouldRejectNonOwnerDuplicateOrderSessionCreate() throws Exception {
    saveLinkedMember("M-ORD-003C", "owner.create@fixyz.com", "Owner Create", 108L, "12345678901241");
    saveLinkedMember("M-ORD-003D", "intruder.create@fixyz.com", "Intruder Create", 109L, "12345678901242");

    AuthSession owner = login("owner.create@fixyz.com", "Abcd1234!");
    AuthSession intruder = login("intruder.create@fixyz.com", "Abcd1234!");
    createOrderSession(
        owner,
        "123e4567-e89b-42d3-a456-426614174266",
        108L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(intruder))
            .header("X-CSRF-TOKEN", intruder.csrfToken())
            .header("X-ClOrdID", "123e4567-e89b-42d3-a456-426614174266")
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload(109L, "005930", "BUY", "LIMIT", 10, 72000L)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CHANNEL-006"))
        .andExpect(jsonPath("$.message").value("Access denied."))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions"));
  }

  @Test
  void shouldReturnExpiredContractWhenOrderSessionTtlIsGone() throws Exception {
    saveLinkedMember("M-ORD-004", "expired.user@fixyz.com", "Expired User", 110L, "12345678901243");

    AuthSession authSession = login("expired.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174267",
        110L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    stringRedisTemplate.delete("ch:order-session:" + orderSessionId);

    mockMvc.perform(get("/api/v1/orders/sessions/{orderSessionId}", orderSessionId)
            .cookie(sessionCookie(authSession)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORD-008"))
        .andExpect(jsonPath("$.message").value("Order session not found."))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions/" + orderSessionId));

    assertThat(orderSessionRepository.findByOrderSessionId(orderSessionId))
        .hasValueSatisfying(session -> assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.EXPIRED));
  }

  @Test
  void shouldReturnExpiredContractWhenDuplicateCreateTargetsExpiredSession() throws Exception {
    saveLinkedMember("M-ORD-004B", "expired.action@fixyz.com", "Expired Action", 111L, "12345678901244");

    AuthSession authSession = login("expired.action@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174268",
        111L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    stringRedisTemplate.delete("ch:order-session:" + orderSessionId);

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .header("X-ClOrdID", "123e4567-e89b-42d3-a456-426614174268")
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload(111L, "005930", "BUY", "LIMIT", 10, 72000L)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORD-008"))
        .andExpect(jsonPath("$.message").value("Order session not found."))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions"));
  }

  @Test
  void shouldReturnExpiredContractBeforeReplayPayloadValidation() throws Exception {
    saveLinkedMember("M-ORD-004C", "expired.mismatch@fixyz.com", "Expired Mismatch", 112L, "12345678901245");

    AuthSession authSession = login("expired.mismatch@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174269",
        112L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    stringRedisTemplate.delete("ch:order-session:" + orderSessionId);

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .header("X-ClOrdID", "123e4567-e89b-42d3-a456-426614174269")
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload(112L, "005930", "BUY", "LIMIT", 10, 72100L)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORD-008"))
        .andExpect(jsonPath("$.message").value("Order session not found."))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions"));
  }

  @Test
  void shouldRejectNonOwnerLookupEvenWhenSessionExpired() throws Exception {
    saveLinkedMember("M-ORD-004D", "expired.owner@fixyz.com", "Expired Owner", 113L, "12345678901246");
    saveLinkedMember("M-ORD-004E", "expired.intruder@fixyz.com", "Expired Intruder", 114L, "12345678901247");

    AuthSession owner = login("expired.owner@fixyz.com", "Abcd1234!");
    AuthSession intruder = login("expired.intruder@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        owner,
        "123e4567-e89b-42d3-a456-426614174270",
        113L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    stringRedisTemplate.delete("ch:order-session:" + orderSessionId);

    mockMvc.perform(get("/api/v1/orders/sessions/{orderSessionId}", orderSessionId)
            .cookie(sessionCookie(intruder)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CHANNEL-006"))
        .andExpect(jsonPath("$.message").value("Access denied."))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions/" + orderSessionId));
  }

  @Test
  void shouldRejectNonOwnerDuplicateCreateEvenWhenSessionExpired() throws Exception {
    saveLinkedMember("M-ORD-004F", "expired.create.owner@fixyz.com", "Expired Create Owner", 115L, "12345678901248");
    saveLinkedMember("M-ORD-004G", "expired.create.intruder@fixyz.com", "Expired Create Intruder", 116L, "12345678901249");

    AuthSession owner = login("expired.create.owner@fixyz.com", "Abcd1234!");
    AuthSession intruder = login("expired.create.intruder@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(
        owner,
        "123e4567-e89b-42d3-a456-426614174271",
        115L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    stringRedisTemplate.delete("ch:order-session:" + orderSessionId);

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(intruder))
            .header("X-CSRF-TOKEN", intruder.csrfToken())
            .header("X-ClOrdID", "123e4567-e89b-42d3-a456-426614174271")
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload(116L, "005930", "BUY", "LIMIT", 10, 72000L)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CHANNEL-006"))
        .andExpect(jsonPath("$.message").value("Access denied."))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions"));
  }

  @Test
  void shouldRequireAuthenticationForStatusLookup() throws Exception {
    mockMvc.perform(get("/api/v1/orders/sessions/{orderSessionId}",
            "123e4567-e89b-42d3-a456-426614174272"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-003"))
        .andExpect(jsonPath("$.message").value("authentication required"));
  }

  @Test
  void shouldRejectCreateWhenClOrdIdHeaderIsMissing() throws Exception {
    saveLinkedMember("M-ORD-005", "validation.user@fixyz.com", "Validation User", 117L, "12345678901250");

    AuthSession authSession = login("validation.user@fixyz.com", "Abcd1234!");

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload(117L, "005930", "BUY", "LIMIT", 10, 72000L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_001"))
        .andExpect(jsonPath("$.message").value("X-ClOrdID header is required"));
  }

  @Test
  void shouldRejectCreateWhenMarketOrderContainsPrice() throws Exception {
    saveLinkedMember("M-ORD-006", "market.user@fixyz.com", "Market User", 118L, "12345678901251");

    AuthSession authSession = login("market.user@fixyz.com", "Abcd1234!");

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .header("X-ClOrdID", "123e4567-e89b-42d3-a456-426614174273")
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload(118L, "005930", "BUY", "MARKET", 10, 72000L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_001"))
        .andExpect(jsonPath("$.message").value("LIMIT orders require price and MARKET orders must omit price"));
  }

  @Test
  void shouldEnforceCreateRateLimitPerMember() throws Exception {
    saveLinkedMember("M-ORD-008", "ratelimit.user@fixyz.com", "Rate User", 119L, "12345678901252");

    AuthSession authSession = login("ratelimit.user@fixyz.com", "Abcd1234!");
    for (int index = 0; index < 10; index++) {
      String clOrdId = String.format("123e4567-e89b-42d3-a456-4266141742%02d", 74 + index);
      createOrderSession(authSession, clOrdId, 119L, "005930", "BUY", "LIMIT", 10, 72000L);
    }

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .header("X-ClOrdID", "123e4567-e89b-42d3-a456-426614174299")
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload(119L, "005930", "BUY", "LIMIT", 10, 72000L)))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_001"))
        .andExpect(jsonPath("$.message").value("rate limit exceeded"))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions"));
  }

  @Test
  void shouldAllowReplayEvenAfterCreateRateLimitIsReached() throws Exception {
    saveLinkedMember("M-ORD-009", "replay-ratelimit.user@fixyz.com", "Replay Rate User", 120L, "12345678901253");

    AuthSession authSession = login("replay-ratelimit.user@fixyz.com", "Abcd1234!");
    JsonNode firstCreated = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174300",
        120L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L
    );

    for (int index = 0; index < 9; index++) {
      String clOrdId = String.format("123e4567-e89b-42d3-a456-4266141743%02d", index + 1);
      createOrderSession(authSession, clOrdId, 120L, "005930", "BUY", "LIMIT", 10, 72000L);
    }

    JsonNode replayed = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174300",
        120L,
        "005930",
        "BUY",
        "LIMIT",
        10,
        72000L,
        status().isOk()
    );

    assertThat(replayed.path("data").path("orderSessionId").asText())
        .isEqualTo(firstCreated.path("data").path("orderSessionId").asText());
    assertThat(replayed.path("data").path("expiresAt").asText())
        .isEqualTo(firstCreated.path("data").path("expiresAt").asText());

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .header("X-ClOrdID", "123e4567-e89b-42d3-a456-426614174399")
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload(120L, "005930", "BUY", "LIMIT", 10, 72000L)))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_001"));
  }

  private Member saveLinkedMember(
      String memberNo,
      String email,
      String name,
      Long accountId,
      String accountNumber
  ) {
    Member member = Member.registerUser(memberNo, email, passwordEncoder.encode("Abcd1234!"), name);
    member.enableTotpEnrollment();
    member.updateLinkedAccount(accountId, accountNumber);
    Member saved = memberRepository.saveAndFlush(member);
    totpService.provisionActiveSecret(saved);
    return saved;
  }

  private JsonNode createOrderSession(
      AuthSession authSession,
      String clOrdId,
      Long accountId,
      String symbol,
      String side,
      String orderType,
      int qty,
      Long price
  ) throws Exception {
    return createOrderSession(authSession, clOrdId, accountId, symbol, side, orderType, qty, price, status().isCreated());
  }

  private JsonNode createOrderSession(
      AuthSession authSession,
      String clOrdId,
      Long accountId,
      String symbol,
      String side,
      String orderType,
      int qty,
      Long price,
      ResultMatcher expectedStatus
  ) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .header("X-ClOrdID", clOrdId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload(accountId, symbol, side, orderType, qty, price)))
        .andExpect(expectedStatus)
        .andExpect(jsonPath("$.success").value(true))
        .andReturn();

    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private String orderSessionPayload(
      Long accountId,
      String symbol,
      String side,
      String orderType,
      int qty,
      Long price
  ) throws Exception {
    return objectMapper.writeValueAsString(new OrderSessionPayload(accountId, symbol, side, orderType, qty, price));
  }

  private void stubCorebankExecuteSuccess(String clOrdId, long orderId, int executedQty, long executedPrice, String externalOrderId) {
    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/internal/v1/orders"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "orderId": %d,
                    "clOrdId": "%s",
                    "status": "FILLED",
                    "idempotent": false,
                    "orderQuantity": %d.0000,
                    "executionResult": "FILLED",
                    "executedQty": %d.0000,
                    "leavesQty": 0.0000,
                    "executedPrice": %d.0000,
                    "externalOrderId": "%s",
                    "externalSyncStatus": "CONFIRMED",
                    "executedAt": "2026-03-12T00:06:00Z"
                  }
                }
                """.formatted(orderId, clOrdId, executedQty, executedQty, executedPrice, externalOrderId))));
  }

  private AuthSession login(String email, String password) throws Exception {
    Member member = memberRepository.findByEmail(email).orElseThrow();
    if (!member.isTotpEnabled()) {
      member.enableTotpEnrollment();
      memberRepository.saveAndFlush(member);
      totpService.provisionActiveSecret(member);
    } else if (!totpService.hasActiveSecret(member)) {
      totpService.provisionActiveSecret(member);
    }
    PreAuthSession preAuthSession = bootstrapPreAuthSession();

    MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
            .cookie(preAuthSession.sessionCookie())
            .header("X-CSRF-TOKEN", preAuthSession.csrfToken())
            .param("email", email)
            .param("password", password))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nextAction").value("VERIFY_TOTP"))
        .andReturn();

    String loginToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
        .path("data")
        .path("loginToken")
        .asText();

    MvcResult result = mockMvc.perform(post("/api/v1/auth/otp/verify")
            .cookie(preAuthSession.sessionCookie())
            .header("X-CSRF-TOKEN", preAuthSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "loginToken", loginToken,
                "otpCode", totpService.currentCode(member)
            ))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.verified").value(true))
        .andReturn();

    Cookie sessionCookie = result.getResponse().getCookie("SESSION");
    assertThat(sessionCookie).isNotNull();
    assertThat(sessionCookie.getValue()).isNotBlank();

    String csrfToken = fetchCsrfToken(sessionCookie.getValue());

    auditLogRepository.deleteAll();

    return new AuthSession(sessionCookie.getValue(), csrfToken);
  }

  private String fetchCsrfToken(String sessionId) throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf")
            .cookie(new Cookie("SESSION", sessionId)))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    String csrfToken = root.path("data").path("token").asText();
    assertThat(csrfToken).isNotBlank();
    return csrfToken;
  }

  private PreAuthSession bootstrapPreAuthSession() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
        .andExpect(status().isOk())
        .andReturn();

    Cookie sessionCookie = result.getResponse().getCookie("SESSION");
    assertThat(sessionCookie).isNotNull();
    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    String csrfToken = root.path("data").path("token").asText();
    assertThat(csrfToken).isNotBlank();
    return new PreAuthSession(sessionCookie.getValue(), csrfToken);
  }

  private Cookie sessionCookie(AuthSession authSession) {
    return new Cookie("SESSION", authSession.sessionId());
  }

  private String nextTotpWindowCode(Member member) {
    String currentCode = totpService.currentCode(member);
    long secondsToNextWindow = 30L - (clock.instant().getEpochSecond() % 30L);
    clock.advance(Duration.ofSeconds(secondsToNextWindow));
    String nextCode = totpService.currentCode(member);
    assertThat(nextCode).isNotEqualTo(currentCode);
    return nextCode;
  }

  private record AuthSession(String sessionId, String csrfToken) {
  }

  private record PreAuthSession(String sessionId, String csrfToken) {
    private Cookie sessionCookie() {
      return new Cookie("SESSION", sessionId);
    }
  }

  private record OrderSessionPayload(
      Long accountId,
      String symbol,
      String side,
      String orderType,
      Integer qty,
      Long price
  ) {
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    @Primary
    StubAccountPositionService stubAccountPositionService(MutableClock clock) {
      return new StubAccountPositionService(clock);
    }

    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock(Instant.parse("2026-03-20T00:00:04Z"));
    }
  }

  static class MutableClock extends Clock {

    private Instant currentInstant;

    MutableClock(Instant currentInstant) {
      this.currentInstant = currentInstant;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return currentInstant;
    }

    void setInstant(Instant currentInstant) {
      this.currentInstant = currentInstant;
    }

    void advance(Duration duration) {
      currentInstant = currentInstant.plus(duration);
    }
  }

  static class StubAccountPositionService extends AccountPositionService {

    private static final long MAX_QUOTE_AGE_MS = 5_000L;

    private BigDecimal availableBalance = BigDecimal.valueOf(5_000_000);
    private BigDecimal availableQuantity = BigDecimal.valueOf(500);
    private BigDecimal avgPrice = BigDecimal.valueOf(70000).setScale(4);
    private BigDecimal marketPrice = BigDecimal.valueOf(72050).setScale(4);
    private String quoteSnapshotId = "qsnap_005930_live_001";
    private Instant quoteAsOf = Instant.parse("2026-03-20T00:00:00Z");
    private FepQuoteSourceMode quoteSourceMode = FepQuoteSourceMode.LIVE;
    private ValuationStatus valuationStatus = ValuationStatus.FRESH;
    private ValuationUnavailableReason valuationUnavailableReason;
    private RuntimeException failure;
    private final Clock clock;

    StubAccountPositionService(Clock clock) {
      super(null);
      this.clock = clock;
    }

    @Override
    public AccountPositionResult getAccountSummary(com.fix.channel.vo.AccountSummaryQueryCommand command) {
      return AccountPositionResult.of(
          command.getAccountId(),
          command.getMemberId(),
          "",
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          availableBalance,
          "KRW",
          Instant.parse("2026-03-13T00:00:00Z")
      );
    }

    @Override
    public AccountPositionResult getAccountPosition(com.fix.channel.vo.AccountPositionQueryCommand command) {
      if (failure != null) {
        RuntimeException nextFailure = failure;
        failure = null;
        throw nextFailure;
      }
      long snapshotAgeMs = quoteAsOf == null ? 0L : Math.max(0L, Duration.between(quoteAsOf, clock.instant()).toMillis());
      ValuationStatus responseValuationStatus = valuationStatus;
      ValuationUnavailableReason responseValuationUnavailableReason = valuationUnavailableReason;
      BigDecimal responseMarketPrice = marketPrice;
      if (quoteAsOf != null && snapshotAgeMs > MAX_QUOTE_AGE_MS) {
        responseValuationStatus = ValuationStatus.STALE;
        responseValuationUnavailableReason = ValuationUnavailableReason.STALE_QUOTE;
        responseMarketPrice = null;
      }
      return AccountPositionResult.of(
          command.getAccountId(),
          command.getMemberId(),
          command.getSymbol(),
          availableQuantity,
          availableQuantity,
          availableBalance,
          "KRW",
          Instant.parse("2026-03-13T00:00:00Z"),
          avgPrice,
          responseMarketPrice,
          quoteSnapshotId,
          quoteAsOf,
          quoteSourceMode,
          null,
          null,
          responseValuationStatus,
          responseValuationUnavailableReason
      );
    }

    void reset() {
      availableBalance = BigDecimal.valueOf(5_000_000);
      availableQuantity = BigDecimal.valueOf(500);
      avgPrice = BigDecimal.valueOf(70000).setScale(4);
      marketPrice = BigDecimal.valueOf(72050).setScale(4);
      quoteSnapshotId = "qsnap_005930_live_001";
      quoteAsOf = Instant.parse("2026-03-20T00:00:00Z");
      quoteSourceMode = FepQuoteSourceMode.LIVE;
      valuationStatus = ValuationStatus.FRESH;
      valuationUnavailableReason = null;
      failure = null;
    }

    void setAvailableBalance(BigDecimal availableBalance) {
      this.availableBalance = availableBalance;
    }

    void setAvailableQuantity(BigDecimal availableQuantity) {
      this.availableQuantity = availableQuantity;
    }

    void setQuoteAsOf(Instant quoteAsOf) {
      this.quoteAsOf = quoteAsOf;
    }

    void setQuoteSnapshotId(String quoteSnapshotId) {
      this.quoteSnapshotId = quoteSnapshotId;
    }

    void setUnavailableQuote(ValuationUnavailableReason valuationUnavailableReason) {
      marketPrice = null;
      quoteSnapshotId = null;
      quoteAsOf = null;
      quoteSourceMode = null;
      valuationStatus = ValuationStatus.UNAVAILABLE;
      this.valuationUnavailableReason = valuationUnavailableReason;
    }

    void failNextWith(RuntimeException failure) {
      this.failure = failure;
    }
  }
}
