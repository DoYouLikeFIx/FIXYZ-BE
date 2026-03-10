package com.fix.channel.service;

import com.fix.channel.config.PasswordRecoveryProperties;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.RetryAfterBusinessException;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PasswordRecoveryRateLimitService {

  private static final String PREFIX = "ch:password-recovery:";

  private final PasswordRecoveryProperties properties;
  private final PasswordRecoveryTokenService tokenService;
  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

  public PasswordRecoveryRateLimitService(
      PasswordRecoveryProperties properties,
      PasswordRecoveryTokenService tokenService,
      ObjectProvider<StringRedisTemplate> redisTemplateProvider
  ) {
    this.properties = properties;
    this.tokenService = tokenService;
    this.redisTemplateProvider = redisTemplateProvider;
  }

  public ForgotDecision registerForgotAttempt(String clientIp, String normalizedEmail) {
    String emailHash = tokenService.fingerprint(normalizedEmail);
    enforceRateLimit(
        key("forgot:ip", sanitize(clientIp)),
        properties.getForgot().getIp(),
        "password recovery rate limit exceeded"
    );
    long emailAttempts = enforceRateLimit(
        key("forgot:email", emailHash),
        properties.getForgot().getEmail(),
        "password recovery rate limit exceeded"
    );
    return new ForgotDecision(emailHash, emailAttempts > properties.getForgot().getChallengeRequiredAfterAttempts());
  }

  public boolean tryAcquireForgotCooldown(String emailHash) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      return true;
    }
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
        key("forgot:cooldown", emailHash),
        "1",
        properties.getForgot().getMailCooldown()
    );
    return Boolean.TRUE.equals(acquired);
  }

  public void registerChallengeAttempt(String clientIp, String normalizedEmail) {
    String emailHash = tokenService.fingerprint(normalizedEmail);
    enforceRateLimit(
        key("challenge:ip", sanitize(clientIp)),
        properties.getChallenge().getIp(),
        "password recovery rate limit exceeded"
    );
    enforceRateLimit(
        key("challenge:email", emailHash),
        properties.getChallenge().getEmail(),
        "password recovery rate limit exceeded"
    );
    enforceRateLimit(
        key("challenge:global", "all"),
        properties.getChallenge().getGlobal(),
        "password recovery rate limit exceeded"
    );
  }

  public void registerResetAttempt(String clientIp, String rawToken) {
    String tokenFingerprint = tokenService.fingerprint(rawToken);
    enforceRateLimit(
        key("reset:ip", sanitize(clientIp)),
        properties.getReset().getIp(),
        "password recovery rate limit exceeded"
    );
    enforceRateLimit(
        key("reset:token", tokenFingerprint),
        properties.getReset().getToken(),
        "password recovery rate limit exceeded"
    );
    enforceRateLimit(
        key("reset:global", "all"),
        properties.getReset().getGlobal(),
        "password recovery rate limit exceeded"
    );
  }

  private long enforceRateLimit(
      String key,
      PasswordRecoveryProperties.RateLimit rateLimit,
      String message
  ) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      return 0L;
    }

    Long current = redisTemplate.opsForValue().increment(key);
    if (current == null) {
      return 0L;
    }

    if (current == 1L) {
      redisTemplate.expire(key, rateLimit.getWindow());
    }

    if (current > rateLimit.getMaxAttempts()) {
      throw new RetryAfterBusinessException(
          ErrorCode.AUTH_PASSWORD_RECOVERY_RATE_LIMIT,
          message,
          retryAfterSeconds(redisTemplate, key, rateLimit.getWindow())
      );
    }
    return current;
  }

  private long retryAfterSeconds(StringRedisTemplate redisTemplate, String key, Duration fallback) {
    Long rawTtl = redisTemplate.getExpire(key);
    if (rawTtl == null || rawTtl < 1L) {
      return Math.max(1L, fallback.toSeconds());
    }
    return rawTtl;
  }

  private String key(String suffix, String subject) {
    return PREFIX + suffix + ":" + subject;
  }

  private String sanitize(String input) {
    if (input == null || input.isBlank()) {
      return "unknown";
    }
    return input.trim();
  }

  public record ForgotDecision(String emailHash, boolean challengeRequired) {
  }
}
