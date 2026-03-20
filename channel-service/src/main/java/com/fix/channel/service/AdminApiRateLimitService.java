package com.fix.channel.service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.RetryAfterBusinessException;

@Service
public class AdminApiRateLimitService {

  private static final String ENDPOINT_DEFAULT = "default";
  private static final String ENDPOINT_AUDIT_LOGS = "audit-logs";
  private static final String ENDPOINT_ORDER_REPLAY = "order-replay";
  private static final String ENDPOINT_SESSION_INVALIDATION = "session-invalidation";
  private static final Set<String> ALLOWED_ENDPOINTS = Set.of(
      ENDPOINT_DEFAULT,
      ENDPOINT_AUDIT_LOGS,
      ENDPOINT_ORDER_REPLAY,
      ENDPOINT_SESSION_INVALIDATION
  );

  private static final String KEY_PREFIX = "ch:ratelimit:admin:endpoint:";
  private static final DefaultRedisScript<Long> INCREMENT_WITH_WINDOW_SCRIPT = createIncrementWithWindowScript();

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

  @Value("${channel.admin.rate-limit.default.max-attempts:20}")
  private int defaultMaxAttempts;

  @Value("${channel.admin.rate-limit.audit-logs.max-attempts:20}")
  private int auditLogsMaxAttempts;

  @Value("${channel.admin.rate-limit.order-replay.max-attempts:20}")
  private int orderReplayMaxAttempts;

  @Value("${channel.admin.rate-limit.session-invalidation.max-attempts:20}")
  private int sessionInvalidationMaxAttempts;

  @Value("${channel.admin.rate-limit.window-seconds:60}")
  private long windowSeconds;

  public AdminApiRateLimitService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
    this.redisTemplateProvider = redisTemplateProvider;
  }

  public void enforceAuditLogs(String sessionId) {
    enforceForEndpoint(sessionId, ENDPOINT_AUDIT_LOGS);
  }

  public void enforceOrderReplay(String sessionId) {
    enforceForEndpoint(sessionId, ENDPOINT_ORDER_REPLAY);
  }

  public void enforceSessionInvalidation(String sessionId) {
    enforceForEndpoint(sessionId, ENDPOINT_SESSION_INVALIDATION);
  }

  private void enforceForEndpoint(String sessionId, String endpointKey) {
    String normalizedEndpoint = sanitizeEndpointKey(endpointKey);
    String key = KEY_PREFIX + normalizedEndpoint + ":session:" + sanitizeSessionId(sessionId);
    StringRedisTemplate redisTemplate = requireRateLimitRedis();
    Long current = redisTemplate.execute(
        INCREMENT_WITH_WINDOW_SCRIPT,
        List.of(key),
        String.valueOf(Duration.ofSeconds(Math.max(1L, windowSeconds)).toMillis())
    );
    if (current == null) {
      throw rateLimitUnavailable();
    }
    if (current <= maxAttemptsFor(normalizedEndpoint)) {
      return;
    }
    throw new RetryAfterBusinessException(
        rateLimitErrorCode(normalizedEndpoint),
        "rate limit exceeded",
        retryAfterSeconds(redisTemplate, key)
    );
  }

  private ErrorCode rateLimitErrorCode(String endpointKey) {
    if (ENDPOINT_ORDER_REPLAY.equals(endpointKey)) {
      return ErrorCode.CONTRACT_RATE_LIMIT_EXCEEDED;
    }
    return ErrorCode.RATE_LIMIT_EXCEEDED;
  }

  private String sanitizeSessionId(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return "unknown";
    }
    return sessionId.trim();
  }

  private String sanitizeEndpointKey(String endpointKey) {
    if (endpointKey == null || endpointKey.isBlank()) {
      return ENDPOINT_DEFAULT;
    }
    String normalized = endpointKey.trim().toLowerCase();
    if (ALLOWED_ENDPOINTS.contains(normalized)) {
      return normalized;
    }
    return ENDPOINT_DEFAULT;
  }

  private int maxAttemptsFor(String endpointKey) {
    if (ENDPOINT_AUDIT_LOGS.equals(endpointKey)) {
      return Math.max(1, auditLogsMaxAttempts);
    }
    if (ENDPOINT_ORDER_REPLAY.equals(endpointKey)) {
      return Math.max(1, orderReplayMaxAttempts);
    }
    if (ENDPOINT_SESSION_INVALIDATION.equals(endpointKey)) {
      return Math.max(1, sessionInvalidationMaxAttempts);
    }
    return Math.max(1, defaultMaxAttempts);
  }

  private long retryAfterSeconds(StringRedisTemplate redisTemplate, String key) {
    Long rawTtl = redisTemplate.getExpire(key);
    if (rawTtl == null) {
      return Math.max(1L, windowSeconds);
    }
    if (rawTtl > 0L) {
      return rawTtl;
    }
    if (rawTtl == -1L) {
      return Math.max(1L, windowSeconds);
    }
    return 1L;
  }

  private StringRedisTemplate requireRateLimitRedis() {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      throw rateLimitUnavailable();
    }
    return redisTemplate;
  }

  private BusinessException rateLimitUnavailable() {
    return new BusinessException(ErrorCode.INTERNAL_ERROR, "admin api rate limit unavailable");
  }

  private static DefaultRedisScript<Long> createIncrementWithWindowScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText("""
        local current = redis.call('INCR', KEYS[1])
        if current == 1 then
          redis.call('PEXPIRE', KEYS[1], ARGV[1])
        end
        return current
        """);
    script.setResultType(Long.class);
    return script;
  }
}
