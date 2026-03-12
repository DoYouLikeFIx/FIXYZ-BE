package com.fix.channel.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisOrderSessionTtlStore implements OrderSessionTtlStore {

  private static final Duration ORDER_SESSION_TTL = Duration.ofMinutes(10);
  private static final String ORDER_SESSION_KEY_PREFIX = "ch:order-session:";
  private static final String OTP_ATTEMPTS_KEY_PREFIX = "ch:otp-attempts:";
  private static final String INITIAL_STATUS = "PENDING_NEW";
  private static final String INITIAL_OTP_ATTEMPTS = "3";
  private static final DefaultRedisScript<Long> ACTIVATE_SESSION_SCRIPT = createActivateSessionScript();

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
  private final Clock clock;

  @Override
  public void activate(String orderSessionId, Instant expiresAt) {
    long ttlMillis = Duration.between(Instant.now(clock), expiresAt).toMillis();
    if (ttlMillis <= 0) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "order session expiration must be in the future");
    }
    StringRedisTemplate redisTemplate = requireRedis();
    Long activated = executeActivation(redisTemplate, orderSessionId, ttlMillis);
    if (activated == null || activated != 1L) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "order session cache activation failed");
    }
  }

  @Override
  public boolean isActive(String orderSessionId) {
    Long ttlSeconds = requireRedis().getExpire(orderSessionKey(orderSessionId), TimeUnit.SECONDS);
    return ttlSeconds != null && ttlSeconds > 0;
  }

  @Override
  public void clear(String orderSessionId) {
    StringRedisTemplate redisTemplate = requireRedis();
    redisTemplate.delete(orderSessionKey(orderSessionId));
    redisTemplate.delete(otpAttemptsKey(orderSessionId));
  }

  @Override
  public Duration ttl() {
    return ORDER_SESSION_TTL;
  }

  private StringRedisTemplate requireRedis() {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "order session cache unavailable");
    }
    return redisTemplate;
  }

  private String orderSessionKey(String orderSessionId) {
    return ORDER_SESSION_KEY_PREFIX + orderSessionId;
  }

  private String otpAttemptsKey(String orderSessionId) {
    return OTP_ATTEMPTS_KEY_PREFIX + orderSessionId;
  }

  protected Long executeActivation(StringRedisTemplate redisTemplate, String orderSessionId, long ttlMillis) {
    return redisTemplate.execute(
        ACTIVATE_SESSION_SCRIPT,
        List.of(orderSessionKey(orderSessionId), otpAttemptsKey(orderSessionId)),
        String.valueOf(ttlMillis),
        INITIAL_STATUS,
        INITIAL_OTP_ATTEMPTS
    );
  }

  private static DefaultRedisScript<Long> createActivateSessionScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText("""
        redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[1])
        redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[1])
        return 1
        """);
    script.setResultType(Long.class);
    return script;
  }
}
