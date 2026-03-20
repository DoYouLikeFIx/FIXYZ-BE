package com.fix.channel.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.entity.Member;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.service.TotpService;
import com.fix.channel.support.ChannelContainersIntegrationTestBase;
import com.fix.channel.testsupport.OrderSessionTestFixture;
import com.fix.common.web.CommonHeaders;
import com.github.tomakehurst.wiremock.WireMockServer;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "internal.secret=test-secret")
@AutoConfigureMockMvc
@Import(OrderSessionTestFixture.class)
class ChannelCorrelationPropagationIntegrationTest extends ChannelContainersIntegrationTestBase {

  private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
  private static final WireMockServer WIRE_MOCK_SERVER = new WireMockServer(wireMockConfig().dynamicPort());

  static {
    WIRE_MOCK_SERVER.start();
  }

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private OrderSessionTestFixture orderSessionTestFixture;

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Autowired
  private MemberRepository memberRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private TotpService totpService;

  @Autowired
  private ObjectMapper objectMapper;

  private Long memberId;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("corebank.base-url", WIRE_MOCK_SERVER::baseUrl);
  }

  @AfterAll
  static void stopWireMock() {
    WIRE_MOCK_SERVER.stop();
  }

  @BeforeEach
  void setUp() {
    WIRE_MOCK_SERVER.resetAll();
    orderSessionTestFixture.reset();
    memberRepository.deleteAll();
    stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
      connection.serverCommands().flushDb();
      return null;
    });
    memberId = saveLinkedMember(1L);
  }

  @Test
  void shouldForwardCorrelationAndTraceparentHeadersAtAuthenticatedExecutionBoundary() throws Exception {
    AuthSession authSession = login("trace-channel-propagation@fix.local", "Abcd1234!");
    String orderSessionId = orderSessionTestFixture.createInitiatedSessionId(
        memberId,
        1L,
        "123e4567-e89b-42d3-a456-426614174261",
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.valueOf(2),
        BigDecimal.valueOf(70100),
        false,
        "TRUSTED_AUTH_SESSION",
        Instant.now().plusSeconds(3600)
    );
    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/internal/v1/orders"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "orderId": 90002,
                    "clOrdId": "123e4567-e89b-42d3-a456-426614174261",
                    "status": "FILLED",
                    "idempotent": false,
                    "orderQuantity": 2.0000,
                    "executionResult": "FILLED",
                    "executedQty": 2.0000,
                    "leavesQty": 0.0000,
                    "executedPrice": 70100.0000,
                    "externalOrderId": "FEP-KRX-90002",
                    "externalSyncStatus": "CONFIRMED",
                    "executedAt": "2026-03-12T00:06:00Z"
                  }
                }
                """)));

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/execute", orderSessionId)
            .cookie(sessionCookie(authSession))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .header(CommonHeaders.X_CORRELATION_ID, "trace-channel-propagation")
            .header(CommonHeaders.TRACEPARENT, TRACEPARENT))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-channel-propagation"))
        .andExpect(header().string(CommonHeaders.TRACEPARENT, TRACEPARENT));

    WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo("/internal/v1/orders"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-channel-propagation"))
        .withHeader(CommonHeaders.TRACEPARENT, equalTo(TRACEPARENT)));
  }

  private Long saveLinkedMember(Long accountId) {
    Member member = Member.registerUser("M-TRACE-CH-001", "trace-channel-propagation@fix.local", passwordEncoder.encode("Abcd1234!"), "Trace User");
    member.enableTotpEnrollment();
    member.updateLinkedAccount(accountId, "%014d".formatted(accountId));
    Member saved = memberRepository.saveAndFlush(member);
    totpService.provisionActiveSecret(saved);
    return saved.getId();
  }

  private AuthSession login(String email, String password) throws Exception {
    Member member = memberRepository.findByEmail(email).orElseThrow();
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
            .content(objectMapper.writeValueAsString(java.util.Map.of(
                "loginToken", loginToken,
                "otpCode", totpService.currentCode(member)
            ))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.verified").value(true))
        .andReturn();

    Cookie sessionCookie = result.getResponse().getCookie("SESSION");
    assertThat(sessionCookie).isNotNull();
    String csrfToken = fetchCsrfToken(sessionCookie.getValue());
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
}
