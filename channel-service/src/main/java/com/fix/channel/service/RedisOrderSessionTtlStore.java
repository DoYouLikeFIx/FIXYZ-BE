package com.fix.channel.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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

  @Override
  public void activate(String orderSessionId) {
    StringRedisTemplate redisTemplate = requireRedis();
    Long activated = executeActivation(redisTemplate, orderSessionId);
    if (activated == null || activated != 1L) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "order session cache activation failed");
    }
  }

  @Override
  public Optional<Long> remainingSeconds(String orderSessionId) {
    Long ttlSeconds = requireRedis().getExpire(orderSessionKey(orderSessionId), TimeUnit.SECONDS);
    if (ttlSeconds == null || ttlSeconds <= 0) {
      return Optional.empty();
    }
    return Optional.of(ttlSeconds);
  }

  @Override
  public void clear(String orderSessionId) {
    StringRedisTemplate redisTemplate = requireRedis();
    redisTemplate.delete(orderSessionKey(orderSessionId));
    redisTemplate.delete(otpAttemptsKey(orderSessionId));
  }

  @Override
  public long ttlSeconds() {
    return ORDER_SESSION_TTL.toSeconds();
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

  protected Long executeActivation(StringRedisTemplate redisTemplate, String orderSessionId) {
    return redisTemplate.execute(
        ACTIVATE_SESSION_SCRIPT,
        List.of(orderSessionKey(orderSessionId), otpAttemptsKey(orderSessionId)),
        String.valueOf(ORDER_SESSION_TTL.toMillis()),
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
