package com.fix.channel.service;

import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

@Service
public class ChannelSessionInvalidationService {

  private static final String STALE_SESSION_PREFIX = "ch:password-recovery:stale-session:";

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
    @SuppressWarnings("rawtypes")
    FindByIndexNameSessionRepository sessionRepository = sessionRepositoryProvider.getIfAvailable();
    if (sessionRepository == null) {
      return;
    }

    @SuppressWarnings("unchecked")
    Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(email);
    sessions.keySet().forEach(sessionId -> {
      markStaleSession(sessionId);
      sessionRepository.deleteById(sessionId);
    });
  }

  public boolean consumePasswordChangedMarker(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return false;
    }
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      return false;
    }
    String marker = redisTemplate.opsForValue().getAndDelete(markerKey(sessionId));
    return marker != null;
  }

  private void markStaleSession(String sessionId) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      return;
    }
    redisTemplate.opsForValue().set(markerKey(sessionId), "password-changed", staleMarkerTtl);
  }

  private String markerKey(String sessionId) {
    return STALE_SESSION_PREFIX + sessionId;
  }
}
