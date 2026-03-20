package com.fix.channel.service;

import com.fix.channel.config.PasswordRecoveryProperties;
import com.fix.channel.vo.PasswordForgotChallengeResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PasswordRecoveryChallengeService implements PasswordRecoveryChallengeProvider {

  private static final String CHALLENGE_NONCE_PREFIX = "ch:password-recovery:challenge:";

  private final PasswordRecoveryProperties properties;
  private final PasswordRecoveryTokenService tokenService;
  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

  public PasswordRecoveryChallengeService(
      PasswordRecoveryProperties properties,
      PasswordRecoveryTokenService tokenService,
      ObjectProvider<StringRedisTemplate> redisTemplateProvider
  ) {
    this.properties = properties;
    this.tokenService = tokenService;
    this.redisTemplateProvider = redisTemplateProvider;
  }

  @Override
  public boolean isProofOfWorkProvider() {
    return false;
  }

  @Override
  public boolean supportsToken(String challengeToken) {
    return challengeToken == null || !challengeToken.startsWith("v2.");
  }

  @Override
  public PasswordForgotChallengeResult issue(String rawEmail, String normalizedEmail, HttpServletRequest request) {
    String emailHash = tokenService.fingerprint(normalizedEmail);
    String nonce = tokenService.generateRawResetToken();
    Instant expiresAt = Instant.now().plusSeconds(properties.getChallenge().getTtlSeconds());
    boolean rolloutEnabled = issueContextFlag(
        request,
        PasswordRecoveryChallengeProvider.ISSUE_CONTEXT_ROLLOUT_ENABLED_ATTRIBUTE
    );
    boolean challengeCapableCohort = issueContextFlag(
        request,
        PasswordRecoveryChallengeProvider.ISSUE_CONTEXT_CHALLENGE_CAPABLE_COHORT_ATTRIBUTE
    );
    String payload = String.join(
        ":",
        emailHash,
        nonce,
        String.valueOf(expiresAt.toEpochMilli()),
        String.valueOf(rolloutEnabled),
        String.valueOf(challengeCapableCohort)
    );
    String encodedPayload = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    String signature = tokenService.sign(encodedPayload);

    StringRedisTemplate redisTemplate = requireRedis();
    redisTemplate.opsForValue().set(
        nonceKey(nonce),
        emailHash,
        Duration.ofSeconds(properties.getChallenge().getTtlSeconds())
    );

    return PasswordForgotChallengeResult.legacy(
        encodedPayload + "." + signature,
        properties.getChallenge().getType(),
        (int) properties.getChallenge().getTtlSeconds()
    );
  }

  @Override
  public void validate(
      String rawEmail,
      String normalizedEmail,
      String challengeToken,
      String challengeAnswer,
      HttpServletRequest request
  ) {
    validateAndConsume(normalizedEmail, challengeToken, challengeAnswer);
  }

  @Override
  public ChallengeEventContext describeVerifyContext(
      String challengeToken,
      ChallengeEventContext fallbackContext
  ) {
    if (challengeToken == null || challengeToken.isBlank()) {
      return fallbackContext;
    }

    try {
      String[] parts = challengeToken.split("\\.", 2);
      if (parts.length != 2 || !tokenService.signaturesMatch(parts[0], parts[1])) {
        return fallbackContext;
      }

      String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
      String[] payloadParts = payload.split(":", 5);
      if (payloadParts.length != 5) {
        return fallbackContext;
      }

      return new ChallengeEventContext(
          challengeContractVersionLabel(),
          Boolean.parseBoolean(payloadParts[3]),
          Boolean.parseBoolean(payloadParts[4])
      );
    } catch (RuntimeException ex) {
      return fallbackContext;
    }
  }

  public void validateAndConsume(String normalizedEmail, String challengeToken, String challengeAnswer) {
    if (challengeToken == null || challengeToken.isBlank() || challengeAnswer == null || challengeAnswer.isBlank()) {
      throw new BusinessException(
          ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_INVALID,
          "password recovery challenge invalid"
      );
    }

    String[] parts = challengeToken.split("\\.", 2);
    if (parts.length != 2 || !tokenService.signaturesMatch(parts[0], parts[1])) {
      throw new BusinessException(
          ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_INVALID,
          "password recovery challenge invalid"
      );
    }

    String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
    String[] payloadParts = payload.split(":", 5);
    if (payloadParts.length != 3 && payloadParts.length != 5) {
      throw new BusinessException(
          ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_INVALID,
          "password recovery challenge invalid"
      );
    }

    String emailHash = payloadParts[0];
    String nonce = payloadParts[1];
    long expiresAtMillis;
    try {
      expiresAtMillis = Long.parseLong(payloadParts[2]);
    } catch (NumberFormatException ex) {
      throw new BusinessException(
          ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_INVALID,
          "password recovery challenge invalid"
      );
    }

    if (!emailHash.equals(tokenService.fingerprint(normalizedEmail))
        || Instant.ofEpochMilli(expiresAtMillis).compareTo(Instant.now()) <= 0) {
      throw new BusinessException(
          ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_INVALID,
          "password recovery challenge invalid"
      );
    }

    StringRedisTemplate redisTemplate = requireRedis();
    String storedEmailHash = redisTemplate.opsForValue().getAndDelete(nonceKey(nonce));
    if (!emailHash.equals(storedEmailHash)) {
      throw new BusinessException(
          ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_INVALID,
          "password recovery challenge invalid"
      );
    }
  }

  private StringRedisTemplate requireRedis() {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      throw new IllegalStateException("redis is required for password recovery challenges");
    }
    return redisTemplate;
  }

  private String nonceKey(String nonce) {
    return CHALLENGE_NONCE_PREFIX + nonce;
  }

  private boolean issueContextFlag(HttpServletRequest request, String attributeName) {
    if (request == null) {
      return false;
    }

    Object attribute = request.getAttribute(attributeName);
    if (attribute instanceof Boolean flag) {
      return flag;
    }
    return attribute != null && Boolean.parseBoolean(attribute.toString());
  }
}
