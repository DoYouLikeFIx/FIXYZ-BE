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
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.repository.PasswordResetTokenRepository;
import com.fix.channel.repository.SecurityEventRepository;
import com.fix.channel.service.ChannelSessionInvalidationService;
import com.fix.channel.service.PasswordRecoveryMailDispatcher;
import com.fix.channel.service.TotpService;
import com.fix.channel.support.ChannelContainersIntegrationTestBase;
import com.fix.common.web.CommonHeaders;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
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
  private AuditLogRepository auditLogRepository;

  @Autowired
  private SecurityEventRepository securityEventRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private RecordingPasswordRecoveryMailDispatcher recordingPasswordRecoveryMailDispatcher;

  @Autowired
  private TotpService totpService;

  @Autowired
  private ToggleableChannelSessionInvalidationService channelSessionInvalidationService;

  @BeforeEach
  void setUp() {
    passwordResetTokenRepository.deleteAll();
    auditLogRepository.deleteAll();
    securityEventRepository.deleteAll();
    memberRepository.deleteAll();
    recordingPasswordRecoveryMailDispatcher.clear();
    channelSessionInvalidationService.setFailInvalidation(false);
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
  void shouldBootstrapRecoveryProofRebindRequireRecoveryUntilConfirmAndRejectProofReplay() throws Exception {
    Member member = memberRepository.save(
        Member.registerUser("M-REC-006", "rebind.proof@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Proof Rebind")
    );
    member.enableTotpEnrollment();
    memberRepository.saveAndFlush(member);
    totpService.provisionActiveSecret(member);
    String previousOtp = totpService.currentCode(member);

    forgot("rebind.proof@fixyz.com");
    String rawToken = recordingPasswordRecoveryMailDispatcher.singleToken("rebind.proof@fixyz.com");

    MvcResult resetResult = mockMvc.perform(post("/api/v1/auth/password/reset")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "token": "%s",
                  "newPassword": "Qwer1234!"
                }
                """.formatted(rawToken)))
        .andExpect(status().isNoContent())
        .andExpect(header().exists(CommonHeaders.X_MFA_RECOVERY_PROOF))
        .andExpect(header().string(CommonHeaders.X_MFA_RECOVERY_PROOF_EXPIRES_IN, "600"))
        .andReturn();

    String recoveryProof = resetResult.getResponse().getHeader(CommonHeaders.X_MFA_RECOVERY_PROOF);
    assertThat(recoveryProof).isNotBlank();

    MvcResult bootstrapResult = mockMvc.perform(post("/api/v1/auth/mfa-recovery/rebind")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "recoveryProof": "%s"
                }
                """.formatted(recoveryProof)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rebindToken").isString())
        .andExpect(jsonPath("$.data.manualEntryKey").isString())
        .andExpect(jsonPath("$.data.enrollmentToken").isString())
        .andExpect(jsonPath("$.data.expiresAt").isString())
        .andReturn();

    JsonNode bootstrapData = objectMapper.readTree(bootstrapResult.getResponse().getContentAsString()).path("data");
    String rebindToken = bootstrapData.path("rebindToken").asText();
    String manualEntryKey = bootstrapData.path("manualEntryKey").asText();
    String enrollmentToken = bootstrapData.path("enrollmentToken").asText();

    LoginAttempt blockedAttempt = startLogin("rebind.proof@fixyz.com", "Qwer1234!");
    mockMvc.perform(post("/api/v1/auth/otp/verify")
            .cookie(blockedAttempt.preAuthSession().sessionCookie())
            .header("X-CSRF-TOKEN", blockedAttempt.preAuthSession().csrfToken())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(Map.of(
                "loginToken", blockedAttempt.loginToken(),
                "otpCode", previousOtp
            ))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH-021"))
        .andExpect(jsonPath("$.recoveryUrl").value("/mfa-recovery"));

    mockMvc.perform(post("/api/v1/auth/mfa-recovery/rebind")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "recoveryProof": "%s"
                }
                """.formatted(recoveryProof)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH-020"));

    mockMvc.perform(post("/api/v1/auth/mfa-recovery/rebind/confirm")
            .with(csrf())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(Map.of(
                "rebindToken", rebindToken,
                "enrollmentToken", enrollmentToken,
                "otpCode", totpService.currentCodeForManualEntryKey(manualEntryKey)
            ))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rebindCompleted").value(true))
        .andExpect(jsonPath("$.data.reauthRequired").value(true));

    LoginAttempt completedAttempt = startLogin("rebind.proof@fixyz.com", "Qwer1234!");
    mockMvc.perform(post("/api/v1/auth/otp/verify")
            .cookie(completedAttempt.preAuthSession().sessionCookie())
            .header("X-CSRF-TOKEN", completedAttempt.preAuthSession().csrfToken())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(Map.of(
                "loginToken", completedAttempt.loginToken(),
                "otpCode", totpService.currentCodeForManualEntryKey(manualEntryKey)
            ))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.verified").value(true))
        .andExpect(jsonPath("$.data.email").value("rebind.proof@fixyz.com"));

    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> assertThat(log.getAction()).isEqualTo("AUTH_MFA_RECOVERY_PROOF_ISSUED"))
        .anySatisfy(log -> assertThat(log.getAction()).isEqualTo("AUTH_TOTP_REBIND_INITIATED"))
        .anySatisfy(log -> assertThat(log.getAction()).isEqualTo("AUTH_TOTP_SECRET_TERMINALIZED"))
        .anySatisfy(log -> assertThat(log.getAction()).isEqualTo("AUTH_TOTP_REBIND_CONFIRMED"));

    assertThat(securityEventRepository.findAll())
        .extracting(event -> event.getEventType())
        .contains("MFA_RECOVERY_PROOF_ISSUED", "MFA_REBIND_INITIATED", "MFA_REBIND_COMPLETED");
  }

  @Test
  void shouldAllowAuthenticatedRebindAndInvalidateExistingSessionsAfterConfirm() throws Exception {
    Member member = memberRepository.save(
        Member.registerUser("M-REC-007", "rebind.session@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Session Rebind")
    );
    member.enableTotpEnrollment();
    memberRepository.saveAndFlush(member);
    totpService.provisionActiveSecret(member);

    String sessionId = loginAndGetSessionId("rebind.session@fixyz.com", "Abcd1234!");
    String csrfToken = fetchCsrfToken(sessionId);

    MvcResult bootstrapResult = mockMvc.perform(post("/api/v1/members/me/totp/rebind")
            .cookie(new Cookie("SESSION", sessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .contentType("application/json")
            .content("""
                {
                  "currentPassword": "Abcd1234!"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rebindToken").isString())
        .andExpect(jsonPath("$.data.manualEntryKey").isString())
        .andExpect(jsonPath("$.data.enrollmentToken").isString())
        .andReturn();

    JsonNode bootstrapData = objectMapper.readTree(bootstrapResult.getResponse().getContentAsString()).path("data");
    String rebindToken = bootstrapData.path("rebindToken").asText();
    String manualEntryKey = bootstrapData.path("manualEntryKey").asText();
    String enrollmentToken = bootstrapData.path("enrollmentToken").asText();
    stringRedisTemplate.opsForValue().set("ch:trusted-session:rebind.session@fixyz.com", "web");
    stringRedisTemplate.opsForValue().set("ch:trusted-session:rebind.session@fixyz.com:ios", "ios");
    stringRedisTemplate.opsForValue().set("ch:trusted-session:rebind.session@fixyz.com:android", "android");

    mockMvc.perform(post("/api/v1/auth/mfa-recovery/rebind/confirm")
            .cookie(new Cookie("SESSION", sessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(Map.of(
                "rebindToken", rebindToken,
                "enrollmentToken", enrollmentToken,
                "otpCode", totpService.currentCodeForManualEntryKey(manualEntryKey)
            ))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rebindCompleted").value(true))
        .andExpect(jsonPath("$.data.reauthRequired").value(true));

    assertThat(stringRedisTemplate.opsForValue().get("ch:trusted-session:rebind.session@fixyz.com")).isNull();
    assertThat(stringRedisTemplate.opsForValue().get("ch:trusted-session:rebind.session@fixyz.com:ios")).isNull();
    assertThat(stringRedisTemplate.opsForValue().get("ch:trusted-session:rebind.session@fixyz.com:android")).isNull();

    mockMvc.perform(get("/api/v1/auth/session")
            .cookie(new Cookie("SESSION", sessionId)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-016"));

    mockMvc.perform(post("/api/v1/auth/mfa-recovery/rebind/confirm")
            .with(csrf())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(Map.of(
                "rebindToken", rebindToken,
                "enrollmentToken", enrollmentToken,
                "otpCode", totpService.currentCodeForManualEntryKey(manualEntryKey)
            ))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH-020"));
  }

  @Test
  void shouldRejectAuthenticatedRebindWhenCurrentPasswordMismatchWithCanonicalAuthCode() throws Exception {
    Member member = memberRepository.save(
        Member.registerUser("M-REC-008", "rebind.mismatch@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Mismatch Rebind")
    );
    member.enableTotpEnrollment();
    memberRepository.saveAndFlush(member);
    totpService.provisionActiveSecret(member);

    String sessionId = loginAndGetSessionId("rebind.mismatch@fixyz.com", "Abcd1234!");
    String csrfToken = fetchCsrfToken(sessionId);

    mockMvc.perform(post("/api/v1/members/me/totp/rebind")
            .cookie(new Cookie("SESSION", sessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .contentType("application/json")
            .content("""
                {
                  "currentPassword": "Wrong1234!"
                }
                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-026"))
        .andExpect(jsonPath("$.message").value("current password mismatch"));
  }

  @Test
  void shouldRequireEnrollmentMetadataWhenAuthenticatedRebindNoLongerHasTotpEnrollment() throws Exception {
    Member member = memberRepository.save(
        Member.registerUser("M-REC-009", "rebind.enroll@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Enroll Rebind")
    );
    member.enableTotpEnrollment();
    memberRepository.saveAndFlush(member);
    totpService.provisionActiveSecret(member);

    String sessionId = loginAndGetSessionId("rebind.enroll@fixyz.com", "Abcd1234!");
    String csrfToken = fetchCsrfToken(sessionId);

    disableTotpEnrollment("rebind.enroll@fixyz.com");

    mockMvc.perform(post("/api/v1/members/me/totp/rebind")
            .cookie(new Cookie("SESSION", sessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .contentType("application/json")
            .content("""
                {
                  "currentPassword": "Abcd1234!"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH-009"))
        .andExpect(jsonPath("$.enrollUrl").value("/settings/totp/enroll"));
  }

  @Test
  void shouldRejectRecoveryProofBootstrapWithDisclosureSafeInvalidCodeWhenTotpStateChanges() throws Exception {
    Member member = memberRepository.save(
        Member.registerUser("M-REC-010", "rebind.disclosure@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Disclosure Rebind")
    );
    member.enableTotpEnrollment();
    memberRepository.saveAndFlush(member);
    totpService.provisionActiveSecret(member);

    forgot("rebind.disclosure@fixyz.com");
    String rawToken = recordingPasswordRecoveryMailDispatcher.singleToken("rebind.disclosure@fixyz.com");

    MvcResult resetResult = mockMvc.perform(post("/api/v1/auth/password/reset")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "token": "%s",
                  "newPassword": "Qwer1234!"
                }
                """.formatted(rawToken)))
        .andExpect(status().isNoContent())
        .andExpect(header().exists(CommonHeaders.X_MFA_RECOVERY_PROOF))
        .andReturn();

    String recoveryProof = resetResult.getResponse().getHeader(CommonHeaders.X_MFA_RECOVERY_PROOF);
    assertThat(recoveryProof).isNotBlank();

    disableTotpEnrollment("rebind.disclosure@fixyz.com");

    mockMvc.perform(post("/api/v1/auth/mfa-recovery/rebind")
            .with(csrf())
            .contentType("application/json")
            .content("""
                {
                  "recoveryProof": "%s"
                }
                """.formatted(recoveryProof)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-019"))
        .andExpect(jsonPath("$.message").value("mfa recovery proof or rebind token invalid or expired"));
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
        .andExpect(jsonPath("$.code").value("AUTH-022"));
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

  @Test
  void shouldAbortRebindConfirmationWhenSessionInvalidationFails() throws Exception {
    Member member = memberRepository.save(
        Member.registerUser("M-REC-011", "rebind.failure@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Failure Rebind")
    );
    member.enableTotpEnrollment();
    memberRepository.saveAndFlush(member);
    totpService.provisionActiveSecret(member);

    String sessionId = loginAndGetSessionId("rebind.failure@fixyz.com", "Abcd1234!");
    String csrfToken = fetchCsrfToken(sessionId);

    MvcResult bootstrapResult = mockMvc.perform(post("/api/v1/members/me/totp/rebind")
            .cookie(new Cookie("SESSION", sessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .contentType("application/json")
            .content("""
                {
                  "currentPassword": "Abcd1234!"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode bootstrapData = objectMapper.readTree(bootstrapResult.getResponse().getContentAsString()).path("data");
    String rebindToken = bootstrapData.path("rebindToken").asText();
    String manualEntryKey = bootstrapData.path("manualEntryKey").asText();
    String enrollmentToken = bootstrapData.path("enrollmentToken").asText();

    channelSessionInvalidationService.setFailInvalidation(true);

    mockMvc.perform(post("/api/v1/auth/mfa-recovery/rebind/confirm")
            .cookie(new Cookie("SESSION", sessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(Map.of(
                "rebindToken", rebindToken,
                "enrollmentToken", enrollmentToken,
                "otpCode", totpService.currentCodeForManualEntryKey(manualEntryKey)
            ))))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.message").value("Internal server error"));

    channelSessionInvalidationService.setFailInvalidation(false);

    mockMvc.perform(post("/api/v1/auth/mfa-recovery/rebind/confirm")
            .cookie(new Cookie("SESSION", sessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(Map.of(
                "rebindToken", rebindToken,
                "enrollmentToken", enrollmentToken,
                "otpCode", totpService.currentCodeForManualEntryKey(manualEntryKey)
            ))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rebindCompleted").value(true))
        .andExpect(jsonPath("$.data.reauthRequired").value(true));
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

  private LoginAttempt startLogin(String email, String password) throws Exception {
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
    assertThat(loginToken).isNotBlank();
    return new LoginAttempt(preAuthSession, loginToken);
  }

  private String fetchCsrfToken(String sessionId) throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf")
            .cookie(new Cookie("SESSION", sessionId)))
        .andExpect(status().isOk())
        .andReturn();

    return objectMapper.readTree(result.getResponse().getContentAsString())
        .path("data")
        .path("token")
        .asText();
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

  private void disableTotpEnrollment(String email) {
    jdbcTemplate.update(
        """
            update members
               set totp_enabled = false,
                   totp_enrolled_at = null,
                   updated_at = ?,
                   version = version + 1
             where email = ?
            """,
        Timestamp.from(Instant.now()),
        email
    );
  }

  private record PreAuthSession(String sessionId, String csrfToken) {
    private Cookie sessionCookie() {
      return new Cookie("SESSION", sessionId);
    }
  }

  private record LoginAttempt(PreAuthSession preAuthSession, String loginToken) {
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

    @Bean
    @Primary
    ToggleableChannelSessionInvalidationService channelSessionInvalidationService(
        @SuppressWarnings("rawtypes") ObjectProvider<FindByIndexNameSessionRepository> sessionRepositoryProvider,
        ObjectProvider<StringRedisTemplate> redisTemplateProvider,
        @Value("${server.servlet.session.timeout:30m}") Duration staleMarkerTtl
    ) {
      return new ToggleableChannelSessionInvalidationService(
          sessionRepositoryProvider,
          redisTemplateProvider,
          staleMarkerTtl
      );
    }
  }

  static class ToggleableChannelSessionInvalidationService extends ChannelSessionInvalidationService {

    private volatile boolean failInvalidation;

    ToggleableChannelSessionInvalidationService(
        @SuppressWarnings("rawtypes") ObjectProvider<FindByIndexNameSessionRepository> sessionRepositoryProvider,
        ObjectProvider<StringRedisTemplate> redisTemplateProvider,
        Duration staleMarkerTtl
    ) {
      super(sessionRepositoryProvider, redisTemplateProvider, staleMarkerTtl);
    }

    void setFailInvalidation(boolean failInvalidation) {
      this.failInvalidation = failInvalidation;
    }

    @Override
    public void invalidateAllSessions(String email, String reason) {
      if (failInvalidation) {
        throw new IllegalStateException("session invalidation unavailable");
      }
      super.invalidateAllSessions(email, reason);
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
