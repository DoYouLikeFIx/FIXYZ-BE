package com.fix.channel.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

@Service
public class ChannelSessionInvalidationService {

  private static final String STALE_SESSION_PREFIX = "ch:session-stale:";
  private static final String TRUSTED_SESSION_PREFIX = "ch:trusted-session:";

  @SuppressWarnings("rawtypes")
  private final ObjectProvider<FindByIndexNameSessionRepository> sessionRepositoryProvider;
  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
  private final Duration staleMarkerTtl;

  public ChannelSessionInvalidationService(
      @SuppressWarnings("rawtypes") ObjectProvider<FindByIndexNameSessionRepository> sessionRepositoryProvider,
      ObjectProvider<StringRedisTemplate> redisTemplateProvider,
      @Value("${server.servlet.session.timeout:30m}") Duration staleMarkerTtl
  ) {
    this.sessionRepositoryProvider = sessionRepositoryProvider;
    this.redisTemplateProvider = redisTemplateProvider;
    this.staleMarkerTtl = staleMarkerTtl;
  }

  public void invalidateAllPasswordSessions(String email) {
    invalidateAllSessions(email, "password-changed");
  }

  public void invalidateAllSessions(String email, String reason) {
    @SuppressWarnings("rawtypes")
    FindByIndexNameSessionRepository sessionRepository = sessionRepositoryProvider.getIfAvailable();
    if (sessionRepository != null) {
      @SuppressWarnings("unchecked")
      Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(email);
      sessions.keySet().forEach(sessionId -> {
        markStaleSession(sessionId, normalizeReason(reason));
        sessionRepository.deleteById(sessionId);
      });
    }
    clearTrustedSessionMarkers(email);
  }

  public boolean consumePasswordChangedMarker(String sessionId) {
    return consumeStaleSessionReason(sessionId) != null;
  }

  public String consumeStaleSessionReason(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return null;
    }
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      return null;
    }
    return redisTemplate.opsForValue().getAndDelete(markerKey(sessionId));
  }

  private void markStaleSession(String sessionId, String reason) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      return;
    }
    redisTemplate.opsForValue().set(markerKey(sessionId), reason, staleMarkerTtl);
  }

  private void clearTrustedSessionMarkers(String email) {
    if (email == null || email.isBlank()) {
      return;
    }
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      return;
    }
    String normalizedEmail = email.trim();
    byte[] exactKey = (TRUSTED_SESSION_PREFIX + normalizedEmail).getBytes(StandardCharsets.UTF_8);
    String namespacedPattern = TRUSTED_SESSION_PREFIX + normalizedEmail + ":*";

    redisTemplate.execute((RedisCallback<Void>) connection -> {
      LinkedHashSet<byte[]> keysToDelete = new LinkedHashSet<>();
      keysToDelete.add(exactKey);

      ScanOptions scanOptions = ScanOptions.scanOptions()
          .match(namespacedPattern)
          .count(128)
          .build();
      try (Cursor<byte[]> cursor = connection.keyCommands().scan(scanOptions)) {
        while (cursor.hasNext()) {
          keysToDelete.add(cursor.next());
        }
      }

      if (!keysToDelete.isEmpty()) {
        connection.keyCommands().del(keysToDelete.toArray(new byte[0][]));
      }
      return null;
    });
  }

  private String markerKey(String sessionId) {
    return STALE_SESSION_PREFIX + sessionId;
  }

  private String normalizeReason(String reason) {
    if (reason == null || reason.isBlank()) {
      return "security-change";
    }
    return reason.trim();
  }
}
