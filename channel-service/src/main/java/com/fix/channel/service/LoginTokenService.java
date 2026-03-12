package com.fix.channel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.entity.Member;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.RetryAfterBusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginTokenService {

  private static final String LOGIN_TOKEN_KEY_PREFIX = "ch:login-token:";
  private static final String LOGIN_TOKEN_LOCK_KEY_PREFIX = "ch:login-token-lock:";
  private static final Duration LOGIN_TOKEN_LOCK_TTL = Duration.ofSeconds(30);
  private static final long LOGIN_TOKEN_LOCK_WAIT_NANOS = Duration.ofSeconds(2).toNanos();
  private static final long LOGIN_TOKEN_LOCK_RETRY_NANOS = Duration.ofMillis(25).toNanos();
  private static final RedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
      "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
      Long.class
  );

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final ConcurrentMap<String, StoredLoginToken> inMemoryStore = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ReentrantLock> tokenLocks = new ConcurrentHashMap<>();

  @Value("${auth.login-token.ttl:5m}")
  private Duration loginTokenTtl;

  public LoginTokenState issue(Member member, HttpServletRequest request, String clientIp, String userAgent) {
    Instant expiresAt = Instant.now(clock).plus(loginTokenTtl);
    String sessionId = request.getSession(true).getId();
    StoredLoginToken storedToken = new StoredLoginToken(
        "login-" + UUID.randomUUID(),
        member.getId(),
        member.getEmail(),
        member.isTotpEnabled(),
        expiresAt,
        sessionId,
        normalize(clientIp),
        userAgentHash(userAgent)
    );
    save(storedToken);
    return storedToken.toState();
  }

  public LoginTokenState requireActive(String loginToken) {
    if (loginToken == null || loginToken.isBlank()) {
      throw expiredLoginToken();
    }

    StoredLoginToken storedToken = read(loginToken.trim());
    if (storedToken == null) {
      throw expiredLoginToken();
    }

    if (!storedToken.isActiveAt(Instant.now(clock))) {
      consume(loginToken);
      throw expiredLoginToken();
    }

    return storedToken.toState();
  }

  public LoginTokenState requireBoundActive(
      String loginToken,
      HttpServletRequest request,
      String clientIp,
      String userAgent
  ) {
    LoginTokenState loginTokenState = requireActive(loginToken);
    StoredLoginToken storedToken = read(loginToken.trim());
    if (storedToken == null) {
      throw expiredLoginToken();
    }

    String sessionId = request.getSession(false) == null ? null : request.getSession(false).getId();
    if (!equalsNormalized(storedToken.preAuthSessionId(), sessionId)
        || !equalsNormalized(storedToken.clientIp(), normalize(clientIp))
        || !equalsNormalized(storedToken.userAgentHash(), userAgentHash(userAgent))) {
      throw expiredLoginToken();
    }

    return loginTokenState;
  }

  public void consume(String loginToken) {
    if (loginToken == null || loginToken.isBlank()) {
      return;
    }

    String normalizedToken = loginToken.trim();
    inMemoryStore.remove(normalizedToken);

    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      redisTemplate.delete(redisKey(normalizedToken));
    }
  }

  public <T> T withTokenLock(String loginToken, Supplier<T> action) {
    String normalizedToken = loginToken == null ? "unknown" : loginToken.trim();
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      return withRedisLock(normalizedToken, redisTemplate, action);
    }

    return withLocalLock(normalizedToken, action);
  }

  private <T> T withRedisLock(String loginToken, StringRedisTemplate redisTemplate, Supplier<T> action) {
    String lockKey = redisLockKey(loginToken);
    String lockValue = UUID.randomUUID().toString();
    long deadline = System.nanoTime() + LOGIN_TOKEN_LOCK_WAIT_NANOS;

    while (System.nanoTime() < deadline) {
      Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOGIN_TOKEN_LOCK_TTL);
      if (Boolean.TRUE.equals(acquired)) {
        try {
          return action.get();
        } finally {
          redisTemplate.execute(RELEASE_LOCK_SCRIPT, java.util.List.of(lockKey), lockValue);
        }
      }
      LockSupport.parkNanos(LOGIN_TOKEN_LOCK_RETRY_NANOS);
    }

    throw new RetryAfterBusinessException(
        ErrorCode.RATE_LIMIT_EXCEEDED,
        "login token is already being processed",
        Math.max(1L, LOGIN_TOKEN_LOCK_TTL.getSeconds())
    );
  }

  private <T> T withLocalLock(String loginToken, Supplier<T> action) {
    ReentrantLock lock = tokenLocks.computeIfAbsent(loginToken, ignored -> new ReentrantLock());
    lock.lock();
    try {
      return action.get();
    } finally {
      lock.unlock();
      if (!lock.hasQueuedThreads()) {
        tokenLocks.remove(loginToken, lock);
      }
    }
  }

  private void save(StoredLoginToken storedToken) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      try {
        redisTemplate.opsForValue().set(
            redisKey(storedToken.loginToken()),
            objectMapper.writeValueAsString(storedToken),
            loginTokenTtl
        );
        return;
      } catch (JsonProcessingException ex) {
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "login token serialization failed", ex);
      }
    }

    inMemoryStore.put(storedToken.loginToken(), storedToken);
  }

  private StoredLoginToken read(String loginToken) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      String rawValue = redisTemplate.opsForValue().get(redisKey(loginToken));
      if (rawValue == null || rawValue.isBlank()) {
        return null;
      }
      try {
        return objectMapper.readValue(rawValue, StoredLoginToken.class);
      } catch (JsonProcessingException ex) {
        redisTemplate.delete(redisKey(loginToken));
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "login token deserialization failed", ex);
      }
    }

    StoredLoginToken storedToken = inMemoryStore.get(loginToken);
    if (storedToken == null) {
      return null;
    }
    if (!storedToken.isActiveAt(Instant.now(clock))) {
      inMemoryStore.remove(loginToken);
      return null;
    }
    return storedToken;
  }

  private BusinessException expiredLoginToken() {
    return new BusinessException(ErrorCode.AUTH_LOGIN_TOKEN_EXPIRED, "login token expired or invalid");
  }

  private String redisKey(String loginToken) {
    return LOGIN_TOKEN_KEY_PREFIX + loginToken;
  }

  private String redisLockKey(String loginToken) {
    return LOGIN_TOKEN_LOCK_KEY_PREFIX + loginToken;
  }

  private String userAgentHash(String userAgent) {
    String normalizedUserAgent = normalize(userAgent);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(normalizedUserAgent.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "login token user agent hashing failed", ex);
    }
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.trim();
  }

  private boolean equalsNormalized(String left, String right) {
    return normalize(left).equals(normalize(right));
  }

  public record LoginTokenState(
      String loginToken,
      Long memberId,
      String email,
      boolean totpEnrolled,
      Instant expiresAt
  ) {
  }

  private record StoredLoginToken(
      String loginToken,
      Long memberId,
      String email,
      boolean totpEnrolled,
      Instant expiresAt,
      String preAuthSessionId,
      String clientIp,
      String userAgentHash
  ) {

    private LoginTokenState toState() {
      return new LoginTokenState(loginToken, memberId, email, totpEnrolled, expiresAt);
    }

    private boolean isActiveAt(Instant instant) {
      return expiresAt != null && expiresAt.isAfter(instant);
    }
  }
}
