package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class ManualRecoveryQueueServiceTest {

  @Test
  void shouldPushJsonPayloadToRedisWhenRedisIsAvailable() {
    @SuppressWarnings("unchecked")
    ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ListOperations<String, String> listOperations = mock(ListOperations.class);
    when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
    when(redisTemplate.opsForList()).thenReturn(listOperations);

    ManualRecoveryQueueService service = new ManualRecoveryQueueService(
        redisProvider,
        new ObjectMapper(),
        Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC),
        10
    );

    service.enqueue("session-1", "clord-1", 3, "ESCALATED_MANUAL_REVIEW");

    verify(listOperations).rightPush(eq("ch:manual-recovery:queue"), eq(
        "{\"enqueuedAt\":\"2026-03-18T00:00:00Z\",\"orderSessionId\":\"session-1\","
            + "\"clOrdId\":\"clord-1\",\"attemptCount\":3,\"reason\":\"ESCALATED_MANUAL_REVIEW\"}"
    ));
  }

  @Test
  void shouldKeepLocalQueueBoundedWhenRedisIsUnavailable() throws Exception {
    @SuppressWarnings("unchecked")
    ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
    when(redisProvider.getIfAvailable()).thenReturn(null);
    ManualRecoveryQueueService service = new ManualRecoveryQueueService(
        redisProvider,
        new ObjectMapper(),
        Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC),
        2
    );

    service.enqueue("session-1", "clord-1", 1, "reason-1");
    service.enqueue("session-2", "clord-2", 2, "reason-2");
    service.enqueue("session-3", "clord-3", 3, "reason-3");

    Field localQueueField = ManualRecoveryQueueService.class.getDeclaredField("localQueue");
    localQueueField.setAccessible(true);
    @SuppressWarnings("unchecked")
    ConcurrentLinkedQueue<String> localQueue = (ConcurrentLinkedQueue<String>) localQueueField.get(service);
    assertThat(localQueue).hasSize(2);
    assertThat(localQueue.peek()).contains("\"orderSessionId\":\"session-2\"");
  }
}
