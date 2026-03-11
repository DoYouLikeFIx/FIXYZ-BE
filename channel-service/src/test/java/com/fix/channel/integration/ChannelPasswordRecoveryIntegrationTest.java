package com.fix.channel.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.entity.Member;
import com.fix.channel.entity.PasswordResetToken;
import com.fix.channel.entity.PasswordResetTokenTerminalReason;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.repository.PasswordResetTokenRepository;
import com.fix.channel.service.PasswordRecoveryMailDispatcher;
import com.fix.channel.support.ChannelContainersIntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.main.allow-bean-definition-overriding=true",
    "auth.password-recovery.token.current-pepper=it-current-pepper",
    "auth.password-recovery.token.previous-pepper=it-previous-pepper"
})
class ChannelPasswordRecoveryIntegrationTest extends ChannelContainersIntegrationTestBase {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private MemberRepository memberRepository;

  @Autowired
  private PasswordResetTokenRepository passwordResetTokenRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Autowired
  private RecordingPasswordRecoveryMailDispatcher recordingPasswordRecoveryMailDispatcher;

  @BeforeEach
  void setUp() {
    passwordResetTokenRepository.deleteAll();
    memberRepository.deleteAll();
    recordingPasswordRecoveryMailDispatcher.clear();
    stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
      connection.serverCommands().flushDb();
      return null;
    });
  }

  @Test
  void shouldReturnSameAcceptedEnvelopeForExistingAndUnknownEmail() throws Exception {
    memberRepository.save(
        Member.registerUser("M-REC-001", "recover.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Recover User")
    );

    JsonNode existing = forgot("recover.user@fixyz.com");
    JsonNode unknown = forgot("unknown.user@fixyz.com");

    assertThat(existing.path("data")).isEqualTo(unknown.path("data"));
    assertThat(existing.path("data").path("accepted").asBoolean()).isTrue();
    assertThat(existing.path("data").path("recovery").path("challengeEndpoint").asText())
        .isEqualTo("/api/v1/auth/password/forgot/challenge");
    assertThat(recordingPasswordRecoveryMailDispatcher.tokensFor("recover.user@fixyz.com")).hasSize(1);
    assertThat(recordingPasswordRecoveryMailDispatcher.tokensFor("unknown.user@fixyz.com")).isEmpty();
  }

  @Test
  void shouldReturnSameAcceptedEnvelopeForExistingUnknownAndChallengeGatedEmail() throws Exception {
    memberRepository.save(
        Member.registerUser("M-REC-001A", "parity.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Parity User")
    );
    memberRepository.save(
        Member.registerUser("M-REC-001B", "challenge.parity@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Challenge Parity")
    );

    JsonNode existing = forgot("parity.user@fixyz.com");
    JsonNode unknown = forgot("parity.unknown@fixyz.com");

    forgot("challenge.parity@fixyz.com");
    String challengeToken = bootstrapChallenge("challenge.parity@fixyz.com");
    JsonNode challengeGated = forgotWithChallenge("challenge.parity@fixyz.com", challengeToken);

    assertThat(existing.path("data")).isEqualTo(unknown.path("data"));
    assertThat(existing.path("data")).isEqualTo(challengeGated.path("data"));
    assertThat(recordingPasswordRecoveryMailDispatcher.tokensFor("challenge.parity@fixyz.com")).hasSize(1);
  }

  @Test
  void shouldBootstrapChallengeAndRejectReplayedChallengeToken() throws Exception {
    memberRepository.save(
        Member.registerUser("M-REC-002", "challenge.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Challenge User")
    );

    String challengeToken = bootstrapChallenge("challenge.user@fixyz.com");

    mockMvc.perform(post("/api/v1/auth/password/forgot")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "email": "challenge.user@fixyz.com",
                  "challengeToken": "%s",
                  "challengeAnswer": "verified"
                }
                """.formatted(challengeToken)))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accepted").value(true));

    mockMvc.perform(post("/api/v1/auth/password/forgot")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "email": "challenge.user@fixyz.com",
                  "challengeToken": "%s",
                  "challengeAnswer": "verified"
                }
                """.formatted(challengeToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-012"));
  }

  @Test
  void shouldReturnSameChallengeContractForUnknownEmail() throws Exception {
    memberRepository.save(
        Member.registerUser("M-REC-002A", "known.challenge@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Known Challenge")
    );

    JsonNode known = bootstrapChallengeResponse("known.challenge@fixyz.com");
    JsonNode unknown = bootstrapChallengeResponse("unknown.challenge@fixyz.com");

    assertThat(known.path("challengeType").asText()).isEqualTo(unknown.path("challengeType").asText());
    assertThat(known.path("challengeTtlSeconds").asInt()).isEqualTo(unknown.path("challengeTtlSeconds").asInt());
    assertThat(known.path("challengeToken").asText()).isNotBlank();
    assertThat(unknown.path("challengeToken").asText()).isNotBlank();
  }

  @Test
  void shouldInvalidatePreviousTokenWhenRecoveryIsReissued() throws Exception {
    memberRepository.save(
        Member.registerUser("M-REC-002B", "reissue.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Reissue User")
    );

    forgot("reissue.user@fixyz.com");
    String firstToken = recordingPasswordRecoveryMailDispatcher.tokensFor("reissue.user@fixyz.com").getFirst();

    stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
      connection.serverCommands().flushDb();
      return null;
    });

    forgot("reissue.user@fixyz.com");
    String secondToken = recordingPasswordRecoveryMailDispatcher.tokensFor("reissue.user@fixyz.com").get(1);

    List<PasswordResetToken> tokens = passwordResetTokenRepository.findAll();
    assertThat(tokens).hasSize(2);
    assertThat(tokens.stream().filter(token -> Byte.valueOf((byte) 1).equals(token.getActiveSlot()))).hasSize(1);
    PasswordResetToken supersededToken = tokens.stream()
        .filter(token -> token.getTokenHash() != null && token.getConsumedAt() == null && token.getActiveSlot() == null)
        .findFirst()
        .orElseThrow();
    assertThat(supersededToken.getTerminalReason()).isEqualTo(PasswordResetTokenTerminalReason.SUPERSEDED);
    assertThat(supersededToken.getTerminalizedAt()).isNotNull();

    mockMvc.perform(post("/api/v1/auth/password/reset")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "token": "%s",
                  "newPassword": "Qwer1234!"
                }
                """.formatted(firstToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-012"));

    mockMvc.perform(post("/api/v1/auth/password/reset")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "token": "%s",
                  "newPassword": "Qwer1234!"
                }
                """.formatted(secondToken)))
        .andExpect(status().isNoContent());
  }

  @Test
  void shouldResetPasswordConsumeTokenAndRejectFirstStaleSessionWithAuth016() throws Exception {
    Member member = memberRepository.save(
        Member.registerUser("M-REC-003", "reset.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Reset User")
    );
    String sessionId = loginAndGetSessionId("reset.user@fixyz.com", "Abcd1234!");

    forgot("reset.user@fixyz.com");
    String rawToken = recordingPasswordRecoveryMailDispatcher.singleToken("reset.user@fixyz.com");

    mockMvc.perform(post("/api/v1/auth/password/reset")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "token": "%s",
                  "newPassword": "Qwer1234!"
                }
                """.formatted(rawToken)))
        .andExpect(status().isNoContent());

    Member updated = memberRepository.findById(member.getId()).orElseThrow();
    assertThat(passwordEncoder.matches("Qwer1234!", updated.getPasswordHash())).isTrue();
    assertThat(updated.getPasswordChangedAt()).isNotNull();

    List<PasswordResetToken> tokens = passwordResetTokenRepository.findAll();
    assertThat(tokens).hasSize(1);
    assertThat(tokens.getFirst().getTokenHash()).isNotEqualTo(rawToken);
    assertThat(tokens.getFirst().getConsumedAt()).isNotNull();
    assertThat(tokens.getFirst().getTerminalReason()).isEqualTo(PasswordResetTokenTerminalReason.CONSUMED);
    assertThat(tokens.getFirst().getTerminalizedAt()).isEqualTo(tokens.getFirst().getConsumedAt());

    mockMvc.perform(get("/api/v1/auth/session")
            .cookie(new Cookie("SESSION", sessionId)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-016"))
        .andExpect(jsonPath("$.message").value("stale session after password change"));
  }

  @Test
  void shouldRejectInvalidExpiredConsumedAndSamePasswordResetAttempts() throws Exception {
    memberRepository.save(
        Member.registerUser("M-REC-004", "reject.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Reject User")
    );

    mockMvc.perform(post("/api/v1/auth/password/reset")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "token": "invalid-token",
                  "newPassword": "Qwer1234!"
                }
                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-012"));

    forgot("reject.user@fixyz.com");
    String rawToken = recordingPasswordRecoveryMailDispatcher.singleToken("reject.user@fixyz.com");
    PasswordResetToken issuedToken = passwordResetTokenRepository.findAll().getFirst();
    issuedToken.expireAt(Instant.now().minusSeconds(60));
    passwordResetTokenRepository.saveAndFlush(issuedToken);

    mockMvc.perform(post("/api/v1/auth/password/reset")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "token": "%s",
                  "newPassword": "Qwer1234!"
                }
                """.formatted(rawToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-012"));

    passwordResetTokenRepository.deleteAll();
    recordingPasswordRecoveryMailDispatcher.clear();
    stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
      connection.serverCommands().flushDb();
      return null;
    });
    forgot("reject.user@fixyz.com");
    rawToken = recordingPasswordRecoveryMailDispatcher.singleToken("reject.user@fixyz.com");

    mockMvc.perform(post("/api/v1/auth/password/reset")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "token": "%s",
                  "newPassword": "Abcd1234!"
                }
                """.formatted(rawToken)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("AUTH-015"));

    mockMvc.perform(post("/api/v1/auth/password/reset")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "token": "%s",
                  "newPassword": "Qwer1234!"
                }
                """.formatted(rawToken)))
        .andExpect(status().isNoContent());

    mockMvc.perform(post("/api/v1/auth/password/reset")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "token": "%s",
                  "newPassword": "Zxcv1234!"
                }
                """.formatted(rawToken)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH-013"));
  }

  @Test
  void shouldReturnRetryAfterForForgotChallengeAndResetRateLimits() throws Exception {
    memberRepository.save(
        Member.registerUser("M-REC-005", "ratelimit.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Rate Limit User")
    );

    for (int attempt = 0; attempt < 3; attempt++) {
      forgot("ratelimit.user@fixyz.com");
    }

    mockMvc.perform(post("/api/v1/auth/password/forgot")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "email": "ratelimit.user@fixyz.com"
                }
                """))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("AUTH-014"));

    for (int attempt = 0; attempt < 3; attempt++) {
      bootstrapChallenge("ratelimit.user@fixyz.com");
    }

    mockMvc.perform(post("/api/v1/auth/password/forgot/challenge")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "email": "ratelimit.user@fixyz.com"
                }
                """))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("AUTH-014"));

    for (int attempt = 0; attempt < 5; attempt++) {
      mockMvc.perform(post("/api/v1/auth/password/reset")
              .with(csrf())
              .contentType("application/json")
              .content("""
                  {
                    "token": "rate-limited-token",
                    "newPassword": "Qwer1234!"
                  }
                  """))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("AUTH-012"));
    }

    mockMvc.perform(post("/api/v1/auth/password/reset")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "token": "rate-limited-token",
                  "newPassword": "Qwer1234!"
                }
                """))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("AUTH-014"));
  }

  @Test
  void shouldRequireRawSpringSecurity403ForAllRecoveryEndpoints() throws Exception {
    mockMvc.perform(post("/api/v1/auth/password/forgot")
            .contentType("application/json")
            .content("""
                {
                  "email": "csrf.user@fixyz.com"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(result -> {
          String body = result.getResponse().getContentAsString();
          assertThat(body).doesNotContain("\"code\"");
          assertThat(body).doesNotContain("\"success\"");
        });

    mockMvc.perform(post("/api/v1/auth/password/forgot/challenge")
            .contentType("application/json")
            .content("""
                {
                  "email": "csrf.user@fixyz.com"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(result -> {
          String body = result.getResponse().getContentAsString();
          assertThat(body).doesNotContain("\"code\"");
          assertThat(body).doesNotContain("\"success\"");
        });

    mockMvc.perform(post("/api/v1/auth/password/reset")
            .contentType("application/json")
            .content("""
                {
                  "token": "csrf-token",
                  "newPassword": "Qwer1234!"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(result -> {
          String body = result.getResponse().getContentAsString();
          assertThat(body).doesNotContain("\"code\"");
          assertThat(body).doesNotContain("\"success\"");
        });
  }

  private JsonNode forgot(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/auth/password/forgot")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "email": "%s"
                }
                """.formatted(email)))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.success").value(true))
        .andReturn();

    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private String bootstrapChallenge(String email) throws Exception {
    return bootstrapChallengeResponse(email).path("challengeToken").asText();
  }

  private JsonNode forgotWithChallenge(String email, String challengeToken) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/auth/password/forgot")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "email": "%s",
                  "challengeToken": "%s",
                  "challengeAnswer": "verified"
                }
                """.formatted(email, challengeToken)))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.success").value(true))
        .andReturn();

    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode bootstrapChallengeResponse(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/auth/password/forgot/challenge")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "email": "%s"
                }
                """.formatted(email)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.challengeType").value("proof-of-work"))
        .andExpect(jsonPath("$.data.challengeTtlSeconds").value(300))
        .andReturn();

    return objectMapper.readTree(result.getResponse().getContentAsString())
        .path("data");
  }

  private String loginAndGetSessionId(String email, String password) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
            .with(csrf())
            .param("email", email)
            .param("password", password))
        .andExpect(status().isOk())
        .andReturn();

    Cookie sessionCookie = result.getResponse().getCookie("SESSION");
    assertThat(sessionCookie).isNotNull();
    assertThat(sessionCookie.getValue()).isNotBlank();
    return sessionCookie.getValue();
  }

  @TestConfiguration
  static class PasswordRecoveryTestConfig {

    @Bean
    @Primary
    TaskExecutor passwordRecoveryTaskExecutor() {
      return new SyncTaskExecutor();
    }

    @Bean
    @Primary
    RecordingPasswordRecoveryMailDispatcher recordingPasswordRecoveryMailDispatcher() {
      return new RecordingPasswordRecoveryMailDispatcher();
    }
  }

  static class RecordingPasswordRecoveryMailDispatcher implements PasswordRecoveryMailDispatcher {

    private final Map<String, List<String>> tokensByEmail = new ConcurrentHashMap<>();

    @Override
    public void dispatch(String email, String rawToken, Instant expiresAt) {
      tokensByEmail.computeIfAbsent(email, ignored -> new ArrayList<>()).add(rawToken);
    }

    List<String> tokensFor(String email) {
      return tokensByEmail.getOrDefault(email, List.of());
    }

    String singleToken(String email) {
      List<String> tokens = tokensFor(email);
      assertThat(tokens).hasSize(1);
      return tokens.getFirst();
    }

    void clear() {
      tokensByEmail.clear();
    }
  }
}
