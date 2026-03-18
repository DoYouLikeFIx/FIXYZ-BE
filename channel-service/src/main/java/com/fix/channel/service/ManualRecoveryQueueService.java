package com.fix.channel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ManualRecoveryQueueService {

  private static final String MANUAL_RECOVERY_QUEUE_KEY = "ch:manual-recovery:queue";

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final int localQueueMaxSize;
  private final ConcurrentLinkedQueue<String> localQueue = new ConcurrentLinkedQueue<>();

  public ManualRecoveryQueueService(
      ObjectProvider<StringRedisTemplate> redisTemplateProvider,
      ObjectMapper objectMapper,
      Clock clock,
      @Value("${order.session.recovery.manual-queue.local-max-size:10000}") int localQueueMaxSize
  ) {
    this.redisTemplateProvider = redisTemplateProvider;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.localQueueMaxSize = Math.max(1, localQueueMaxSize);
  }

  public void enqueue(String orderSessionId, String clOrdId, int attemptCount, String reason) {
    String payload = serialize(new ManualRecoveryTaskPayload(
        Instant.now(clock).toString(),
        orderSessionId,
        clOrdId,
        attemptCount,
        reason
    ));
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      redisTemplate.opsForList().rightPush(MANUAL_RECOVERY_QUEUE_KEY, payload);
      return;
    }
    while (localQueue.size() >= localQueueMaxSize) {
      localQueue.poll();
    }
    localQueue.add(payload);
  }

  private String serialize(ManualRecoveryTaskPayload payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize manual recovery queue payload", ex);
    }
  }

  private record ManualRecoveryTaskPayload(
      String enqueuedAt,
      String orderSessionId,
      String clOrdId,
      int attemptCount,
      String reason
  ) {
  }
}
