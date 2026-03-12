package com.fix.channel.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.entity.Member;
import com.fix.channel.repository.MemberRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.session.timeout=2s",
    "server.servlet.session.timeout=2s"
})
class ChannelSessionTimeoutIntegrationTest extends ChannelContainersIntegrationTestBase {

  private static final long SESSION_EXPIRE_AWAIT_MS = 8_000L;
  private static final long SESSION_EXPIRE_POLL_MS = 200L;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MemberRepository memberRepository;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private SessionRepository<? extends Session> sessionRepository;

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Autowired
  private TotpService totpService;

  @BeforeEach
  void setUp() {
    memberRepository.deleteAll();
    stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
      connection.serverCommands().flushDb();
      return null;
    });
  }

  @Test
  void shouldTreatSessionAsExpiredAfterInactivityThreshold() throws Exception {
    memberRepository.save(
        Member.registerUser("M-IT-TIMEOUT-001", "timeout.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Timeout User")
    );

    String sessionId = loginAndGetSessionId("timeout.user@fixyz.com", "Abcd1234!");
    assertThat(sessionRepository.findById(sessionId)).isNotNull();

    awaitSessionExpiration(sessionId);

    mockMvc.perform(get("/api/v1/auth/session")
            .cookie(new Cookie("SESSION", sessionId)))
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.code").value("CHANNEL-001"))
        .andExpect(jsonPath("$.message").value("channel session expired"))
        .andExpect(jsonPath("$.path").value("/api/v1/auth/session"));
  }

  private String loginAndGetSessionId(String email, String password) throws Exception {
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
        .andReturn();

    String loginToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
        .path("data")
        .path("loginToken")
        .asText();

    MvcResult result = mockMvc.perform(post("/api/v1/auth/otp/verify")
            .cookie(preAuthSession.sessionCookie())
            .header("X-CSRF-TOKEN", preAuthSession.csrfToken())
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "loginToken", loginToken,
                "otpCode", totpService.currentCode(member)
            ))))
        .andExpect(status().isOk())
        .andReturn();

    Cookie sessionCookie = result.getResponse().getCookie("SESSION");
    assertThat(sessionCookie).isNotNull();
    assertThat(sessionCookie.getValue()).isNotBlank();
    return sessionCookie.getValue();
  }

  private PreAuthSession bootstrapPreAuthSession() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
        .andExpect(status().isOk())
        .andReturn();

    Cookie sessionCookie = result.getResponse().getCookie("SESSION");
    assertThat(sessionCookie).isNotNull();
    String csrfToken = objectMapper.readTree(result.getResponse().getContentAsString())
        .path("data")
        .path("token")
        .asText();
    assertThat(csrfToken).isNotBlank();
    return new PreAuthSession(sessionCookie.getValue(), csrfToken);
  }

  private void awaitSessionExpiration(String sessionId) throws InterruptedException {
    long deadline = System.currentTimeMillis() + SESSION_EXPIRE_AWAIT_MS;
    while (System.currentTimeMillis() < deadline) {
      if (sessionRepository.findById(sessionId) == null) {
        return;
      }
      Thread.sleep(SESSION_EXPIRE_POLL_MS);
    }
    assertThat(sessionRepository.findById(sessionId)).isNull();
  }

  private record PreAuthSession(String sessionId, String csrfToken) {
    private Cookie sessionCookie() {
      return new Cookie("SESSION", sessionId);
    }
  }
}
