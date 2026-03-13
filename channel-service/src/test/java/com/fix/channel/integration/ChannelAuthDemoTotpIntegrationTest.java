package com.fix.channel.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.client.CorebankLinkedAccountProfile;
import com.fix.channel.client.CorebankProvisioningClient;
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.repository.SecurityEventRepository;
import com.fix.channel.service.AccountPositionService;
import com.fix.channel.service.TotpService;
import com.fix.channel.support.ChannelContainersIntegrationTestBase;
import com.fix.channel.vo.AccountPositionResult;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "auth.demo.auto-totp-enrolled=true")
@AutoConfigureMockMvc
class ChannelAuthDemoTotpIntegrationTest extends ChannelContainersIntegrationTestBase {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MemberRepository memberRepository;

  @Autowired
  private OrderSessionRepository orderSessionRepository;

  @Autowired
  private AuditLogRepository auditLogRepository;

  @Autowired
  private SecurityEventRepository securityEventRepository;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Autowired
  private TotpService totpService;

  @MockitoBean
  private CorebankProvisioningClient corebankProvisioningClient;

  @Autowired
  private StubAccountPositionService accountPositionService;

  @BeforeEach
  void setUp() {
    orderSessionRepository.deleteAll();
    auditLogRepository.deleteAll();
    securityEventRepository.deleteAll();
    memberRepository.deleteAll();
    stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
      connection.serverCommands().flushDb();
      return null;
    });
    org.mockito.Mockito.doAnswer(invocation -> new CorebankLinkedAccountProfile(
        1001L,
        invocation.getArgument(0, Long.class),
        "110123456789"
    )).when(corebankProvisioningClient)
        .provisionDefaultAccount(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    accountPositionService.reset();
  }

  @Test
  void shouldAutoEnrollDemoRegistrationsAndAllowOrderSessionPreparation() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
            .with(csrf())
            .param("email", "demo.totp@fixyz.com")
            .param("password", "Abcd1234!")
            .param("name", "Demo TOTP"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true));

    assertThat(memberRepository.findByEmail("demo.totp@fixyz.com"))
        .hasValueSatisfying(member -> {
          assertThat(member.isTotpEnabled()).isTrue();
          assertThat(member.getTotpEnrolledAt()).isNotNull();
          assertThat(member.getAccountId()).isEqualTo(1001L);
          assertThat(member.getAccountNumber()).isEqualTo("110123456789");
        });

    AuthSession authSession = login("demo.totp@fixyz.com", "Abcd1234!");

    mockMvc.perform(get("/api/v1/auth/session")
            .cookie(new Cookie("SESSION", authSession.sessionId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totpEnrolled").value(true))
        .andExpect(jsonPath("$.data.accountId").value("1001"))
        .andExpect(jsonPath("$.data.accountNumber").value("110123456789"));

    mockMvc.perform(post("/api/v1/orders/sessions")
            .cookie(new Cookie("SESSION", authSession.sessionId()))
            .header("X-CSRF-TOKEN", authSession.csrfToken())
            .header("X-ClOrdID", "123e4567-e89b-42d3-a456-426614174290")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new OrderSessionPayload(
                1001L,
                "005930",
                "BUY",
                "LIMIT",
                2,
                71000L
            ))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("AUTHED"))
        .andExpect(jsonPath("$.data.challengeRequired").value(false))
        .andExpect(jsonPath("$.data.authorizationReason").value("RECENT_LOGIN_MFA"))
        .andExpect(jsonPath("$.data.symbol").value("005930"))
        .andExpect(jsonPath("$.data.qty").value(2))
        .andExpect(jsonPath("$.data.price").value(71000));
  }

  private AuthSession login(String email, String password) throws Exception {
    var member = memberRepository.findByEmail(email).orElseThrow();
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
        .andReturn();

    Cookie sessionCookie = result.getResponse().getCookie("SESSION");
    assertThat(sessionCookie).isNotNull();
    assertThat(sessionCookie.getValue()).isNotBlank();

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
    StubAccountPositionService stubAccountPositionService() {
      return new StubAccountPositionService();
    }
  }

  static class StubAccountPositionService extends AccountPositionService {

    private BigDecimal availableBalance = BigDecimal.valueOf(5_000_000);
    private BigDecimal availableQuantity = BigDecimal.valueOf(500);

    StubAccountPositionService() {
      super(null);
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
      return AccountPositionResult.of(
          command.getAccountId(),
          command.getMemberId(),
          command.getSymbol(),
          availableQuantity,
          availableQuantity,
          BigDecimal.ZERO,
          "KRW",
          Instant.parse("2026-03-13T00:00:00Z")
      );
    }

    void reset() {
      availableBalance = BigDecimal.valueOf(5_000_000);
      availableQuantity = BigDecimal.valueOf(500);
    }
  }
}
