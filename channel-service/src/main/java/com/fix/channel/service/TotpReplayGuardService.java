package com.fix.channel.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TotpReplayGuardService {

  private static final String KEY_PREFIX = "ch:totp-used:";
  private static final long PERIOD_SECONDS = 30L;

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
  private final Clock clock;
  private final ConcurrentMap<String, Instant> localClaims = new ConcurrentHashMap<>();

  public TotpReplayGuardService(ObjectProvider<StringRedisTemplate> redisTemplateProvider, Clock clock) {
    this.redisTemplateProvider = redisTemplateProvider;
    this.clock = clock;
  }

  public void claim(Long memberId, long windowIndex, String otpCode) {
    String key = key(memberId, windowIndex, otpCode);
    Duration ttl = ttlFor(windowIndex);

    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
      if (Boolean.FALSE.equals(acquired)) {
        throw replayed();
      }
      return;
    }

    Instant now = Instant.now(clock);
    localClaims.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    Instant expiresAt = now.plus(ttl);
    Instant prior = localClaims.putIfAbsent(key, expiresAt);
    if (prior != null && prior.isAfter(now)) {
      throw replayed();
    }
  }

  public void releaseClaim(Long memberId, long windowIndex, String otpCode) {
    String key = key(memberId, windowIndex, otpCode);

    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      redisTemplate.delete(key);
      return;
    }

    localClaims.remove(key);
  }

  private Duration ttlFor(long windowIndex) {
    long windowEndEpochSecond = (windowIndex + 1L) * PERIOD_SECONDS;
    long nowEpochSecond = Instant.now(clock).getEpochSecond();
    long remaining = Math.max(1L, windowEndEpochSecond - nowEpochSecond + PERIOD_SECONDS);
    return Duration.ofSeconds(remaining);
  }

  private String key(Long memberId, long windowIndex, String otpCode) {
    String normalizedOtp = otpCode == null ? "" : otpCode.trim();
    return KEY_PREFIX + memberId + ":" + windowIndex + ":" + normalizedOtp;
  }

  private BusinessException replayed() {
    return new BusinessException(ErrorCode.AUTH_OTP_REPLAYED, "otp code already used in current window");
  }
}
