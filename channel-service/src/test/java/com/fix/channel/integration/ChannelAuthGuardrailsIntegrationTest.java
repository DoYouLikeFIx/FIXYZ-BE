package com.fix.channel.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.channel.entity.Member;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.repository.SecurityEventRepository;
import com.fix.channel.support.ChannelContainersIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "auth.guardrails.ip-rate-limit.max-failed-attempts=3",
    "auth.guardrails.ip-rate-limit.window-seconds=2",
    "auth.guardrails.account-lockout.max-failed-attempts=3"
})
class ChannelAuthGuardrailsIntegrationTest extends ChannelContainersIntegrationTestBase {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MemberRepository memberRepository;

  @Autowired
  private SecurityEventRepository securityEventRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @BeforeEach
  void setUp() {
    memberRepository.deleteAll();
    securityEventRepository.deleteAll();
    stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
      connection.serverCommands().flushDb();
      return null;
    });
  }

  @Test
  void shouldApplyIpRateLimitAtNMinus1NAndNPlus1() throws Exception {
    String email = "unknown.ip@fixyz.com";
    String wrongPassword = "Wrong1234!";

    assertLoginUnauthorized(email, wrongPassword); // N-2
    assertLoginUnauthorized(email, wrongPassword); // N-1
    assertLoginUnauthorized(email, wrongPassword); // N
    assertLoginRateLimited(email, wrongPassword);  // N+1
  }

  @Test
  void shouldReleaseIpRateLimitAfterCooldownWindow() throws Exception {
    String email = "unknown.cooldown@fixyz.com";
    String wrongPassword = "Wrong1234!";

    assertLoginUnauthorized(email, wrongPassword);
    assertLoginUnauthorized(email, wrongPassword);
    assertLoginUnauthorized(email, wrongPassword);
    assertLoginRateLimited(email, wrongPassword);

    Thread.sleep(2500L);

    assertLoginUnauthorized(email, wrongPassword);
  }

  @Test
  void shouldApplyAccountLockAtNMinus1NAndNPlus1AndPersistSecurityEvent() throws Exception {
    Member saved = memberRepository.save(
        Member.registerUser("M-IT-GUARD-001", "guard.lock@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Guard Lock")
    );

    assertLoginUnauthorized("guard.lock@fixyz.com", "Wrong1234!"); // N-2
    assertLoginUnauthorized("guard.lock@fixyz.com", "Wrong1234!"); // N-1

    mockMvc.perform(post("/api/v1/auth/login")
            .with(csrf())
            .param("email", "guard.lock@fixyz.com")
            .param("password", "Wrong1234!"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_002"))
        .andExpect(jsonPath("$.message").value("account locked"));

    mockMvc.perform(post("/api/v1/auth/login")
            .with(csrf())
            .param("email", "guard.lock@fixyz.com")
            .param("password", "Abcd1234!"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_002"))
        .andExpect(jsonPath("$.message").value("account locked"));

    Member locked = memberRepository.findById(saved.getId()).orElseThrow();
    assertThat(locked.getStatus()).isEqualTo("LOCKED");
    assertThat(locked.getFailedLoginAttempts()).isEqualTo(3);
    assertThat(locked.getLockedAt()).isNotNull();

    assertThat(securityEventRepository.findAll())
        .anySatisfy(event -> {
          assertThat(event.getMemberId()).isEqualTo(saved.getId());
          assertThat(event.getEventType()).isEqualTo("ACCOUNT_LOCKED");
          assertThat(event.getSeverity()).isEqualTo("HIGH");
          assertThat(event.getIpAddress()).isNotBlank();
        });
  }

  @Test
  void shouldResetFailedAttemptsAfterSuccessfulLoginBelowThreshold() throws Exception {
    Member saved = memberRepository.save(
        Member.registerUser("M-IT-GUARD-002", "guard.reset@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Guard Reset")
    );

    assertLoginUnauthorized("guard.reset@fixyz.com", "Wrong1234!");
    assertLoginUnauthorized("guard.reset@fixyz.com", "Wrong1234!");

    mockMvc.perform(post("/api/v1/auth/login")
            .with(csrf())
            .param("email", "guard.reset@fixyz.com")
            .param("password", "Abcd1234!"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.loginToken").isString())
        .andExpect(jsonPath("$.data.nextAction").value("ENROLL_TOTP"))
        .andExpect(jsonPath("$.data.totpEnrolled").value(false));

    Member updated = memberRepository.findById(saved.getId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo("ACTIVE");
    assertThat(updated.getFailedLoginAttempts()).isZero();
    assertThat(updated.getLockedAt()).isNull();
  }

  private void assertLoginUnauthorized(String email, String password) throws Exception {
    mockMvc.perform(post("/api/v1/auth/login")
            .with(csrf())
            .param("email", email)
            .param("password", password))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_001"))
        .andExpect(jsonPath("$.message").value("invalid credentials"));
  }

  private void assertLoginRateLimited(String email, String password) throws Exception {
    mockMvc.perform(post("/api/v1/auth/login")
            .with(csrf())
            .param("email", email)
            .param("password", password))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_001"))
        .andExpect(jsonPath("$.message").value("rate limit exceeded"));
  }
}
