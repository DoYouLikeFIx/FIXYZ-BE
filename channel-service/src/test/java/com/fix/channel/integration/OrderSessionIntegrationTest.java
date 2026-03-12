package com.fix.channel.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.entity.Member;
import com.fix.channel.entity.OrderSessionStatus;
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.service.TotpService;
import com.fix.channel.support.ChannelContainersIntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

@SpringBootTest
@AutoConfigureMockMvc
class OrderSessionIntegrationTest extends ChannelContainersIntegrationTestBase {

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
  private PasswordEncoder passwordEncoder;

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Autowired
  private TotpService totpService;

  @BeforeEach
  void setUp() {
    orderSessionRepository.deleteAll();
    auditLogRepository.deleteAll();
    memberRepository.deleteAll();
    stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
      connection.serverCommands().flushDb();
      return null;
    });
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
    assertThat(response.path("data").path("accountId").asLong()).isEqualTo(101L);
    assertThat(response.path("data").path("symbol").asText()).isEqualTo("005930");
    assertThat(response.path("data").path("side").asText()).isEqualTo("BUY");
    assertThat(response.path("data").path("orderType").asText()).isEqualTo("LIMIT");
    assertThat(response.path("data").path("qty").asLong()).isEqualTo(10L);
    assertThat(response.path("data").path("price").asLong()).isEqualTo(72000L);
    assertThat(response.path("data").path("createdAt").asText()).isNotBlank();
    assertThat(response.path("data").path("updatedAt").asText()).isNotBlank();
    assertThat(response.path("data").path("expiresAt").asText()).isNotBlank();
    assertThat(remainingSeconds).isBetween(1L, 600L);
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
        .andExpect(jsonPath("$.data.remainingSeconds").isNumber())
        .andExpect(jsonPath("$.data.expiresAt").value(createdExpiresAt));
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
}
