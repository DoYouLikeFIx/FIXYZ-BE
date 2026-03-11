package com.fix.channel.service;

import com.fix.channel.config.PasswordRecoveryProperties;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PasswordRecoveryChallengeService {

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

  public ChallengePayload issue(String normalizedEmail) {
    String emailHash = tokenService.fingerprint(normalizedEmail);
    String nonce = tokenService.generateRawResetToken();
    Instant expiresAt = Instant.now().plusSeconds(properties.getChallenge().getTtlSeconds());
    String payload = emailHash + ":" + nonce + ":" + expiresAt.toEpochMilli();
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

    return new ChallengePayload(encodedPayload + "." + signature, properties.getChallenge().getType(), expiresAt);
  }

  public void validateAndConsume(String normalizedEmail, String challengeToken, String challengeAnswer) {
    if (challengeToken == null || challengeToken.isBlank() || challengeAnswer == null || challengeAnswer.isBlank()) {
      throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID, "reset token invalid or expired");
    }

    String[] parts = challengeToken.split("\\.", 2);
    if (parts.length != 2 || !tokenService.signaturesMatch(parts[0], parts[1])) {
      throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID, "reset token invalid or expired");
    }

    String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
    String[] payloadParts = payload.split(":", 3);
    if (payloadParts.length != 3) {
      throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID, "reset token invalid or expired");
    }

    String emailHash = payloadParts[0];
    String nonce = payloadParts[1];
    long expiresAtMillis;
    try {
      expiresAtMillis = Long.parseLong(payloadParts[2]);
    } catch (NumberFormatException ex) {
      throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID, "reset token invalid or expired");
    }

    if (!emailHash.equals(tokenService.fingerprint(normalizedEmail))
        || Instant.ofEpochMilli(expiresAtMillis).compareTo(Instant.now()) <= 0) {
      throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID, "reset token invalid or expired");
    }

    StringRedisTemplate redisTemplate = requireRedis();
    String storedEmailHash = redisTemplate.opsForValue().getAndDelete(nonceKey(nonce));
    if (!emailHash.equals(storedEmailHash)) {
      throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID, "reset token invalid or expired");
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

  public record ChallengePayload(String token, String type, Instant expiresAt) {
  }
}
