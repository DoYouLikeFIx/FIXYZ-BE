package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.entity.ManualRecoveryQueueEntry;
import com.fix.channel.repository.ManualRecoveryQueueEntryRepository;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class ManualRecoveryQueueServiceTest {

  @Test
  void shouldPublishPendingEntriesToRedisAndMarkPublished() {
    @SuppressWarnings("unchecked")
    ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
    ManualRecoveryQueueEntryRepository repository = mock(ManualRecoveryQueueEntryRepository.class);
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    ManualRecoveryQueueEntry entry = ManualRecoveryQueueEntry.pending(
        "session-1",
        "clord-1",
        3,
        "ESCALATED_MANUAL_REVIEW\nneeds-review",
        Instant.parse("2026-03-18T00:00:00Z")
    );
    ReflectionTestUtils.setField(entry, "id", 11L);

    when(repository.findByPublishedAtIsNullOrderByEnqueuedAtAscIdAsc(any(Pageable.class))).thenReturn(List.of(entry));
    when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
    doReturn(1L).when(redisTemplate).execute(any(), anyList(), any(), any(), any());
    when(repository.markPublishedIfPending(
        11L,
        Instant.parse("2026-03-18T00:00:00Z"),
        Instant.parse("2026-03-18T00:01:00Z")
    )).thenReturn(1);

    ManualRecoveryQueueService service = new ManualRecoveryQueueService(
        redisProvider,
        repository,
        new ObjectMapper(),
        Clock.fixed(Instant.parse("2026-03-18T00:01:00Z"), ZoneOffset.UTC),
        10
    );

    service.publishPendingEntries();

    verify(redisTemplate).execute(
        any(),
        eq(List.of(
            "ch:manual-recovery:published:11:2026-03-18T00:00:00Z",
            "ch:manual-recovery:queue"
        )),
        eq("session-1"),
        eq(String.valueOf(Duration.ofDays(30).toMillis())),
        eq(
            "{\"enqueuedAt\":\"2026-03-18T00:00:00Z\",\"orderSessionId\":\"session-1\","
                + "\"clOrdId\":\"clord-1\",\"attemptCount\":3,"
                + "\"reason\":\"ESCALATED_MANUAL_REVIEW\\nneeds-review\"}"
        )
    );
    verify(repository).markPublishedIfPending(
        11L,
        Instant.parse("2026-03-18T00:00:00Z"),
        Instant.parse("2026-03-18T00:01:00Z")
    );
  }

  @Test
  void shouldLeaveEntriesPendingWhenRedisIsUnavailable() {
    @SuppressWarnings("unchecked")
    ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
    ManualRecoveryQueueEntryRepository repository = mock(ManualRecoveryQueueEntryRepository.class);
    when(redisProvider.getIfAvailable()).thenReturn(null);

    ManualRecoveryQueueService service = new ManualRecoveryQueueService(
        redisProvider,
        repository,
        new ObjectMapper(),
        Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC),
        10
    );

    service.publishPendingEntries();

    verify(repository, never()).findByPublishedAtIsNullOrderByEnqueuedAtAscIdAsc(any(Pageable.class));
    verify(repository, never()).markPublishedIfPending(any(), any(), any());
  }

  @Test
  void shouldLeaveEntriesPendingWhenRedisPushFails() {
    @SuppressWarnings("unchecked")
    ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
    ManualRecoveryQueueEntryRepository repository = mock(ManualRecoveryQueueEntryRepository.class);
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    ManualRecoveryQueueEntry entry = ManualRecoveryQueueEntry.pending(
        "session-1",
        "clord-1",
        1,
        "reason-1",
        Instant.parse("2026-03-18T00:00:00Z")
    );
    ReflectionTestUtils.setField(entry, "id", 12L);

    when(repository.findByPublishedAtIsNullOrderByEnqueuedAtAscIdAsc(any(Pageable.class))).thenReturn(List.of(entry));
    when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
    doThrow(new IllegalStateException("redis down")).when(redisTemplate).execute(any(), anyList(), any(), any(), any());

    ManualRecoveryQueueService service = new ManualRecoveryQueueService(
        redisProvider,
        repository,
        new ObjectMapper(),
        Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC),
        10
    );

    service.publishPendingEntries();

    verify(repository, never()).markPublishedIfPending(any(), any(), any());
    assertThat(entry.getPublishedAt()).isNull();
  }

  @Test
  void shouldAcknowledgePendingRowWithoutRepublishingWhenRedisAlreadyHasDedupeMarker() {
    @SuppressWarnings("unchecked")
    ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
    ManualRecoveryQueueEntryRepository repository = mock(ManualRecoveryQueueEntryRepository.class);
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    ManualRecoveryQueueEntry entry = ManualRecoveryQueueEntry.pending(
        "session-1",
        "clord-1",
        2,
        "reason-1",
        Instant.parse("2026-03-18T00:00:00Z")
    );
    ReflectionTestUtils.setField(entry, "id", 13L);

    when(repository.findByPublishedAtIsNullOrderByEnqueuedAtAscIdAsc(any(Pageable.class))).thenReturn(List.of(entry));
    when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
    doReturn(0L).when(redisTemplate).execute(any(), anyList(), any(), any(), any());
    when(repository.markPublishedIfPending(
        13L,
        Instant.parse("2026-03-18T00:00:00Z"),
        Instant.parse("2026-03-18T00:01:00Z")
    )).thenReturn(1);

    ManualRecoveryQueueService service = new ManualRecoveryQueueService(
        redisProvider,
        repository,
        new ObjectMapper(),
        Clock.fixed(Instant.parse("2026-03-18T00:01:00Z"), ZoneOffset.UTC),
        10
    );

    service.publishPendingEntries();

    verify(repository).markPublishedIfPending(
        13L,
        Instant.parse("2026-03-18T00:00:00Z"),
        Instant.parse("2026-03-18T00:01:00Z")
    );
  }
}
