package com.fix.channel.service;

import com.fix.common.error.ErrorCode;
import com.fix.common.error.RetryAfterBusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class OrderSessionOtpChallengeService {

  private static final Duration DEBOUNCE_TTL = Duration.ofSeconds(1);
  private static final Duration SUCCESS_TTL = Duration.ofSeconds(60);
  private static final String OTP_ATTEMPTS_KEY_PREFIX = "ch:otp-attempts:";
  private static final String OTP_DEBOUNCE_KEY_PREFIX = "ch:otp-attempt-ts:";
  private static final String OTP_SUCCESS_KEY_PREFIX = "ch:otp-success:";
  private static final DefaultRedisScript<Long> DECREMENT_ATTEMPTS_SCRIPT = createDecrementAttemptsScript();

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
  private final Clock clock;
  private final ConcurrentMap<String, Instant> localDebounceClaims = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Integer> localRemainingAttempts = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, LocalSuccessClaim> localSuccessClaims = new ConcurrentHashMap<>();

  public OrderSessionOtpChallengeService(
      ObjectProvider<StringRedisTemplate> redisTemplateProvider,
      Clock clock
  ) {
    this.redisTemplateProvider = redisTemplateProvider;
    this.clock = clock;
  }

  public void enforceDebounce(String orderSessionId) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      Boolean acquired = redisTemplate.opsForValue().setIfAbsent(debounceKey(orderSessionId), "1", DEBOUNCE_TTL);
      if (Boolean.FALSE.equals(acquired)) {
        throw rateLimited();
      }
      return;
    }

    Instant now = Instant.now(clock);
    localDebounceClaims.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    Instant previous = localDebounceClaims.putIfAbsent(debounceKey(orderSessionId), now.plus(DEBOUNCE_TTL));
    if (previous != null && previous.isAfter(now)) {
      throw rateLimited();
    }
  }

  public int remainingAttempts(String orderSessionId) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      String raw = redisTemplate.opsForValue().get(attemptsKey(orderSessionId));
      return parseAttempts(raw);
    }
    return localRemainingAttempts.computeIfAbsent(orderSessionId, ignored -> 3);
  }

  public int consumeFailure(String orderSessionId) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      Long remaining = redisTemplate.execute(DECREMENT_ATTEMPTS_SCRIPT, java.util.List.of(attemptsKey(orderSessionId)));
      return remaining == null ? 0 : Math.max(0, remaining.intValue());
    }

    return localRemainingAttempts.compute(orderSessionId, (ignored, value) -> {
      int current = value == null ? 3 : value;
      return Math.max(0, current - 1);
    });
  }

  public boolean isSuccessfulReplay(String orderSessionId, long windowIndex, String otpCode) {
    String normalizedOtp = normalizeOtp(otpCode);
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      String recorded = redisTemplate.opsForValue().get(successKey(orderSessionId, windowIndex));
      return normalizedOtp.equals(recorded);
    }

    LocalSuccessClaim claim = localSuccessClaims.get(successKey(orderSessionId, windowIndex));
    if (claim == null || !claim.expiresAt().isAfter(Instant.now(clock))) {
      localSuccessClaims.remove(successKey(orderSessionId, windowIndex));
      return false;
    }
    return normalizedOtp.equals(claim.otpCode());
  }

  public void recordSuccess(String orderSessionId, long windowIndex, String otpCode) {
    String normalizedOtp = normalizeOtp(otpCode);
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      redisTemplate.opsForValue().set(successKey(orderSessionId, windowIndex), normalizedOtp, SUCCESS_TTL);
      return;
    }

    localSuccessClaims.put(
        successKey(orderSessionId, windowIndex),
        new LocalSuccessClaim(normalizedOtp, Instant.now(clock).plus(SUCCESS_TTL))
    );
  }

  private int parseAttempts(String raw) {
    if (raw == null || raw.isBlank()) {
      return 0;
    }
    try {
      return Math.max(0, Integer.parseInt(raw));
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private String attemptsKey(String orderSessionId) {
    return OTP_ATTEMPTS_KEY_PREFIX + orderSessionId;
  }

  private String debounceKey(String orderSessionId) {
    return OTP_DEBOUNCE_KEY_PREFIX + orderSessionId;
  }

  private String successKey(String orderSessionId, long windowIndex) {
    return OTP_SUCCESS_KEY_PREFIX + orderSessionId + ":" + windowIndex;
  }

  private String normalizeOtp(String otpCode) {
    return otpCode == null ? "" : otpCode.trim();
  }

  private RetryAfterBusinessException rateLimited() {
    return new RetryAfterBusinessException(
        ErrorCode.RATE_LIMIT_EXCEEDED,
        "rate limit exceeded",
        DEBOUNCE_TTL.toSeconds()
    );
  }

  private static DefaultRedisScript<Long> createDecrementAttemptsScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText("""
        local current = redis.call('GET', KEYS[1])
        if not current then
          return 0
        end
        current = tonumber(current)
        if current <= 0 then
          return 0
        end
        return redis.call('DECR', KEYS[1])
        """);
    script.setResultType(Long.class);
    return script;
  }

  private record LocalSuccessClaim(String otpCode, Instant expiresAt) {
  }
}
