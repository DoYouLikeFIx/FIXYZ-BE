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
import com.fix.channel.support.ChannelContainersIntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    memberRepository.save(
        Member.registerUser("M-ORD-001", "order.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Order User")
    );

    AuthSession authSession = login("order.user@fixyz.com", "Abcd1234!");
    JsonNode response = createOrderSession(authSession, "123e4567-e89b-42d3-a456-426614174260", "ORD-REF-001");

    String orderSessionId = response.path("data").path("orderSessionId").asText();
    long remainingSeconds = response.path("data").path("remainingSeconds").asLong();

    assertThat(orderSessionId).isNotBlank();
    assertThat(response.path("data").path("status").asText()).isEqualTo("PENDING_NEW");
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
    memberRepository.save(
        Member.registerUser("M-ORD-002", "status.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Status User")
    );

    AuthSession authSession = login("status.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(authSession, "123e4567-e89b-42d3-a456-426614174260", "ORD-REF-002");
    String orderSessionId = created.path("data").path("orderSessionId").asText();
    String createdExpiresAt = created.path("data").path("expiresAt").asText();

    mockMvc.perform(get("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .param("orderSessionId", orderSessionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.orderSessionId").value(orderSessionId))
        .andExpect(jsonPath("$.data.status").value("PENDING_NEW"))
        .andExpect(jsonPath("$.data.remainingSeconds").isNumber())
        .andExpect(jsonPath("$.data.expiresAt").value(createdExpiresAt));
  }

  @Test
  void shouldReturnExistingOrderSessionWhenOwnerRecreatesActiveSession() throws Exception {
    memberRepository.save(
        Member.registerUser("M-ORD-002B", "repeat.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Repeat User")
    );

    AuthSession authSession = login("repeat.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(authSession, "123e4567-e89b-42d3-a456-426614174261", "ORD-REF-002B");
    String orderSessionId = created.path("data").path("orderSessionId").asText();
    String expiresAt = created.path("data").path("expiresAt").asText();
    long firstRemainingSeconds = created.path("data").path("remainingSeconds").asLong();

    JsonNode recreated = createOrderSession(authSession, "123e4567-e89b-42d3-a456-426614174261", "ORD-REF-002B", status().isOk());

    assertThat(recreated.path("data").path("orderSessionId").asText()).isEqualTo(orderSessionId);
    assertThat(recreated.path("data").path("status").asText()).isEqualTo("PENDING_NEW");
    assertThat(recreated.path("data").path("expiresAt").asText()).isEqualTo(expiresAt);
    assertThat(recreated.path("data").path("remainingSeconds").asLong()).isBetween(1L, firstRemainingSeconds);
    assertThat(auditLogRepository.count()).isEqualTo(1L);
  }

  @Test
  void shouldRejectReplayWhenOrderRefChanges() throws Exception {
    memberRepository.save(
        Member.registerUser("M-ORD-002C", "mismatch.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Mismatch User")
    );

    AuthSession authSession = login("mismatch.user@fixyz.com", "Abcd1234!");
    createOrderSession(authSession, "123e4567-e89b-42d3-a456-426614174264", "ORD-REF-002C");

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload("123e4567-e89b-42d3-a456-426614174264", "ORD-REF-CHANGED"))
            )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ORD_001"))
        .andExpect(jsonPath("$.message").value("clOrdId replay payload mismatch"))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions"));
  }

  @Test
  void shouldRejectNonOwnerOrderSessionStatusLookup() throws Exception {
    memberRepository.save(
        Member.registerUser("M-ORD-003A", "owner.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Owner User")
    );
    memberRepository.save(
        Member.registerUser("M-ORD-003B", "intruder.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Intruder User")
    );

    AuthSession owner = login("owner.user@fixyz.com", "Abcd1234!");
    AuthSession intruder = login("intruder.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(owner, "123e4567-e89b-42d3-a456-426614174260", "ORD-REF-003");
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    mockMvc.perform(get("/api/v1/orders/sessions")
            .cookie(sessionCookie(intruder))
            .param("orderSessionId", orderSessionId))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CHANNEL-006"))
        .andExpect(jsonPath("$.message").value("Access denied."))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions"));
  }

  @Test
  void shouldRejectNonOwnerDuplicateOrderSessionCreate() throws Exception {
    memberRepository.save(
        Member.registerUser("M-ORD-003C", "owner.create@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Owner Create")
    );
    memberRepository.save(
        Member.registerUser("M-ORD-003D", "intruder.create@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Intruder Create")
    );

    AuthSession owner = login("owner.create@fixyz.com", "Abcd1234!");
    AuthSession intruder = login("intruder.create@fixyz.com", "Abcd1234!");
    createOrderSession(owner, "123e4567-e89b-42d3-a456-426614174262", "ORD-REF-003B");

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(intruder))
            .header("X-CSRF-TOKEN", intruder.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload("123e4567-e89b-42d3-a456-426614174262", "ORD-REF-003B"))
            )
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CHANNEL-006"))
        .andExpect(jsonPath("$.message").value("Access denied."))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions"));
  }

  @Test
  void shouldReturnExpiredContractWhenOrderSessionTtlIsGone() throws Exception {
    memberRepository.save(
        Member.registerUser("M-ORD-004", "expired.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Expired User")
    );

    AuthSession authSession = login("expired.user@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(authSession, "123e4567-e89b-42d3-a456-426614174260", "ORD-REF-004");
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    stringRedisTemplate.delete("ch:order-session:" + orderSessionId);

    mockMvc.perform(get("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .param("orderSessionId", orderSessionId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORD-008"))
        .andExpect(jsonPath("$.message").value("Order session not found."))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions"));

    assertThat(orderSessionRepository.findByOrderSessionId(orderSessionId))
        .hasValueSatisfying(session -> assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.EXPIRED));
  }

  @Test
  void shouldReturnExpiredContractWhenDuplicateCreateTargetsExpiredSession() throws Exception {
    memberRepository.save(
        Member.registerUser("M-ORD-004B", "expired.action@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Expired Action")
    );

    AuthSession authSession = login("expired.action@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(authSession, "123e4567-e89b-42d3-a456-426614174263", "ORD-REF-004B");
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    stringRedisTemplate.delete("ch:order-session:" + orderSessionId);

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload("123e4567-e89b-42d3-a456-426614174263", "ORD-REF-004B"))
            )
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORD-008"))
        .andExpect(jsonPath("$.message").value("Order session not found."))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions"));
  }

  @Test
  void shouldReturnExpiredContractBeforeReplayPayloadValidation() throws Exception {
    memberRepository.save(
        Member.registerUser("M-ORD-004C", "expired.mismatch@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Expired Mismatch")
    );

    AuthSession authSession = login("expired.mismatch@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(authSession, "123e4567-e89b-42d3-a456-426614174269", "ORD-REF-004C");
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    stringRedisTemplate.delete("ch:order-session:" + orderSessionId);

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload("123e4567-e89b-42d3-a456-426614174269", "ORD-REF-CHANGED"))
            )
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORD-008"))
        .andExpect(jsonPath("$.message").value("Order session not found."))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions"));
  }

  @Test
  void shouldRejectNonOwnerLookupEvenWhenSessionExpired() throws Exception {
    memberRepository.save(
        Member.registerUser("M-ORD-004D", "expired.owner@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Expired Owner")
    );
    memberRepository.save(
        Member.registerUser("M-ORD-004E", "expired.intruder@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Expired Intruder")
    );

    AuthSession owner = login("expired.owner@fixyz.com", "Abcd1234!");
    AuthSession intruder = login("expired.intruder@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(owner, "123e4567-e89b-42d3-a456-426614174270", "ORD-REF-004D");
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    stringRedisTemplate.delete("ch:order-session:" + orderSessionId);

    mockMvc.perform(get("/api/v1/orders/sessions")
            .cookie(sessionCookie(intruder))
            .param("orderSessionId", orderSessionId))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CHANNEL-006"))
        .andExpect(jsonPath("$.message").value("Access denied."))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions"));
  }

  @Test
  void shouldRejectNonOwnerDuplicateCreateEvenWhenSessionExpired() throws Exception {
    memberRepository.save(
        Member.registerUser("M-ORD-004F", "expired.create.owner@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Expired Create Owner")
    );
    memberRepository.save(
        Member.registerUser("M-ORD-004G", "expired.create.intruder@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Expired Create Intruder")
    );

    AuthSession owner = login("expired.create.owner@fixyz.com", "Abcd1234!");
    AuthSession intruder = login("expired.create.intruder@fixyz.com", "Abcd1234!");
    JsonNode created = createOrderSession(owner, "123e4567-e89b-42d3-a456-426614174271", "ORD-REF-004E");
    String orderSessionId = created.path("data").path("orderSessionId").asText();

    stringRedisTemplate.delete("ch:order-session:" + orderSessionId);

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(intruder))
            .header("X-CSRF-TOKEN", intruder.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload("123e4567-e89b-42d3-a456-426614174271", "ORD-REF-004E"))
            )
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CHANNEL-006"))
        .andExpect(jsonPath("$.message").value("Access denied."))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions"));
  }

  @Test
  void shouldRequireAuthenticationForStatusLookup() throws Exception {
    mockMvc.perform(get("/api/v1/orders/sessions")
            .param("orderSessionId", "123e4567-e89b-42d3-a456-426614174265"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-003"))
        .andExpect(jsonPath("$.message").value("authentication required"));
  }

  @Test
  void shouldRejectStatusLookupWhenLookupTargetIsMissing() throws Exception {
    memberRepository.save(
        Member.registerUser("M-ORD-005", "validation.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Validation User")
    );

    AuthSession authSession = login("validation.user@fixyz.com", "Abcd1234!");

    mockMvc.perform(get("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_001"))
        .andExpect(jsonPath("$.message").value("exactly one of orderSessionId or clOrdId is required"));
  }

  @Test
  void shouldRejectStatusLookupWhenBothIdentifiersAreProvided() throws Exception {
    memberRepository.save(
        Member.registerUser("M-ORD-006", "dual.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Dual User")
    );

    AuthSession authSession = login("dual.user@fixyz.com", "Abcd1234!");

    mockMvc.perform(get("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .param("orderSessionId", "123e4567-e89b-42d3-a456-426614174266")
            .param("clOrdId", "123e4567-e89b-42d3-a456-426614174267"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_001"))
        .andExpect(jsonPath("$.message").value("exactly one of orderSessionId or clOrdId is required"));
  }

  @Test
  void shouldRejectOversizedOrderRef() throws Exception {
    memberRepository.save(
        Member.registerUser("M-ORD-007", "length.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Length User")
    );

    AuthSession authSession = login("length.user@fixyz.com", "Abcd1234!");
    String oversizedOrderRef = "X".repeat(65);

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload("123e4567-e89b-42d3-a456-426614174268", oversizedOrderRef))
            )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_001"))
        .andExpect(jsonPath("$.message").value("size must be between 1 and 64"));
  }

  @Test
  void shouldEnforceCreateRateLimitPerMember() throws Exception {
    memberRepository.save(
        Member.registerUser("M-ORD-008", "ratelimit.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Rate User")
    );

    AuthSession authSession = login("ratelimit.user@fixyz.com", "Abcd1234!");
    for (int index = 0; index < 10; index++) {
      String clOrdId = String.format("123e4567-e89b-42d3-a456-4266141742%02d", 70 + index);
      createOrderSession(authSession, clOrdId, "ORD-REF-RL-" + index);
    }

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload("123e4567-e89b-42d3-a456-426614174299", "ORD-REF-RL-10"))
            )
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_001"))
        .andExpect(jsonPath("$.message").value("rate limit exceeded"))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions"));
  }

  @Test
  void shouldAllowReplayEvenAfterCreateRateLimitIsReached() throws Exception {
    memberRepository.save(
        Member.registerUser("M-ORD-009", "replay-ratelimit.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Replay Rate User")
    );

    AuthSession authSession = login("replay-ratelimit.user@fixyz.com", "Abcd1234!");
    JsonNode firstCreated = createOrderSession(authSession, "123e4567-e89b-42d3-a456-426614174300", "ORD-REF-RL-BASE");

    for (int index = 0; index < 9; index++) {
      String clOrdId = String.format("123e4567-e89b-42d3-a456-4266141743%02d", index + 1);
      createOrderSession(authSession, clOrdId, "ORD-REF-RL-BULK-" + index);
    }

    JsonNode replayed = createOrderSession(
        authSession,
        "123e4567-e89b-42d3-a456-426614174300",
        "ORD-REF-RL-BASE",
        status().isOk()
    );

    assertThat(replayed.path("data").path("orderSessionId").asText())
        .isEqualTo(firstCreated.path("data").path("orderSessionId").asText());
    assertThat(replayed.path("data").path("expiresAt").asText())
        .isEqualTo(firstCreated.path("data").path("expiresAt").asText());

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload("123e4567-e89b-42d3-a456-426614174399", "ORD-REF-RL-OVER"))
            )
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_001"));
  }

  private JsonNode createOrderSession(AuthSession authSession, String clOrdId, String orderRef) throws Exception {
    return createOrderSession(authSession, clOrdId, orderRef, status().isCreated());
  }

  private JsonNode createOrderSession(
      AuthSession authSession,
      String clOrdId,
      String orderRef,
      ResultMatcher expectedStatus
  ) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(orderSessionPayload(clOrdId, orderRef))
            )
        .andExpect(expectedStatus)
        .andExpect(jsonPath("$.success").value(true))
        .andReturn();

    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private String orderSessionPayload(String clOrdId, String orderRef) throws Exception {
    return objectMapper.writeValueAsString(new OrderSessionPayload(clOrdId, orderRef));
  }

  private AuthSession login(String email, String password) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
            .with(csrf())
            .param("email", email)
            .param("password", password))
        .andExpect(status().isOk())
        .andReturn();

    Cookie sessionCookie = result.getResponse().getCookie("SESSION");
    assertThat(sessionCookie).isNotNull();
    assertThat(sessionCookie.getValue()).isNotBlank();

    String csrfToken = fetchCsrfToken(sessionCookie.getValue());

    // Keep assertions focused on order-session side effects, not auth bootstrap noise.
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

  private Cookie sessionCookie(AuthSession authSession) {
    return new Cookie("SESSION", authSession.sessionId());
  }

  private record AuthSession(String sessionId, String csrfToken) {
  }

  private record OrderSessionPayload(String clOrdId, String orderRef) {
  }
}
