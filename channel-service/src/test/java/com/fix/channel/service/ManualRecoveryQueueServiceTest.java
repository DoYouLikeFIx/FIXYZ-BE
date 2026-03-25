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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class ManualRecoveryQueueServiceTest {

  private static final Instant ENQUEUED_AT = Instant.parse("2026-03-18T00:00:00Z");
  private static final Instant NOW = Instant.parse("2026-03-18T00:01:00Z");

  @Test
  void shouldClaimPublishAndMarkPendingEntryAsPublished() {
    @SuppressWarnings("unchecked")
    ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
    ManualRecoveryQueueEntryRepository repository = mock(ManualRecoveryQueueEntryRepository.class);
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    ManualRecoveryQueueEntry entry = pendingEntry(11L, "session-1", "clord-1", 3, "ESCALATED_MANUAL_REVIEW\nneeds-review");

    when(repository.findByPublishedAtIsNullAndResolvedAtIsNullOrderByEnqueuedAtAscIdAsc(any(Pageable.class)))
        .thenReturn(List.of(entry));
    when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
    when(repository.claimPendingIfAvailable(eq(11L), eq(ENQUEUED_AT), any(), eq(NOW), eq(NOW.minus(Duration.ofMinutes(5)))))
        .thenReturn(1);
    doReturn(1L).when(redisTemplate).execute(any(), anyList(), any(), any(), any());
    when(repository.markPublishedIfClaimed(eq(11L), eq(ENQUEUED_AT), any(), eq(NOW))).thenReturn(1);

    ManualRecoveryQueueService service = new ManualRecoveryQueueService(
        redisProvider,
        repository,
        new ObjectMapper(),
        Clock.fixed(NOW, ZoneOffset.UTC),
        10,
        Duration.ofMinutes(5)
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
            "{\"entryId\":11,\"enqueuedAt\":\"2026-03-18T00:00:00Z\",\"orderSessionId\":\"session-1\","
                + "\"clOrdId\":\"clord-1\",\"attemptCount\":3,"
                + "\"reason\":\"ESCALATED_MANUAL_REVIEW\\nneeds-review\"}"
        )
    );
    verify(repository).claimPendingIfAvailable(eq(11L), eq(ENQUEUED_AT), any(), eq(NOW), eq(NOW.minus(Duration.ofMinutes(5))));
    verify(repository).markPublishedIfClaimed(eq(11L), eq(ENQUEUED_AT), any(), eq(NOW));
    verify(repository, never()).releaseClaimIfMatches(any(), any(), any());
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
        Clock.fixed(NOW, ZoneOffset.UTC),
        10,
        Duration.ofMinutes(5)
    );

    service.publishPendingEntries();

    verify(repository, never()).findByPublishedAtIsNullAndResolvedAtIsNullOrderByEnqueuedAtAscIdAsc(any(Pageable.class));
    verify(repository, never()).claimPendingIfAvailable(any(), any(), any(), any(), any());
    verify(repository, never()).markPublishedIfClaimed(any(), any(), any(), any());
  }

  @Test
  void shouldReleaseClaimWhenRedisPublishFails() {
    @SuppressWarnings("unchecked")
    ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
    ManualRecoveryQueueEntryRepository repository = mock(ManualRecoveryQueueEntryRepository.class);
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    ManualRecoveryQueueEntry entry = pendingEntry(12L, "session-1", "clord-1", 1, "reason-1");

    when(repository.findByPublishedAtIsNullAndResolvedAtIsNullOrderByEnqueuedAtAscIdAsc(any(Pageable.class)))
        .thenReturn(List.of(entry));
    when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
    when(repository.claimPendingIfAvailable(eq(12L), eq(ENQUEUED_AT), any(), eq(NOW), eq(NOW.minus(Duration.ofMinutes(5)))))
        .thenReturn(1);
    doThrow(new IllegalStateException("redis down")).when(redisTemplate).execute(any(), anyList(), any(), any(), any());

    ManualRecoveryQueueService service = new ManualRecoveryQueueService(
        redisProvider,
        repository,
        new ObjectMapper(),
        Clock.fixed(NOW, ZoneOffset.UTC),
        10,
        Duration.ofMinutes(5)
    );

    service.publishPendingEntries();

    verify(repository, never()).markPublishedIfClaimed(any(), any(), any(), any());
    verify(repository).releaseClaimIfMatches(eq(12L), eq(ENQUEUED_AT), any());
    assertThat(entry.getPublishedAt()).isNull();
  }

  @Test
  void shouldAcknowledgeClaimedRowWithoutRepublishingWhenRedisAlreadyHasReceipt() {
    @SuppressWarnings("unchecked")
    ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
    ManualRecoveryQueueEntryRepository repository = mock(ManualRecoveryQueueEntryRepository.class);
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    ManualRecoveryQueueEntry entry = pendingEntry(13L, "session-1", "clord-1", 2, "reason-1");

    when(repository.findByPublishedAtIsNullAndResolvedAtIsNullOrderByEnqueuedAtAscIdAsc(any(Pageable.class)))
        .thenReturn(List.of(entry));
    when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
    when(repository.claimPendingIfAvailable(eq(13L), eq(ENQUEUED_AT), any(), eq(NOW), eq(NOW.minus(Duration.ofMinutes(5)))))
        .thenReturn(1);
    doReturn(0L).when(redisTemplate).execute(any(), anyList(), any(), any(), any());
    when(repository.markPublishedIfClaimed(eq(13L), eq(ENQUEUED_AT), any(), eq(NOW))).thenReturn(1);

    ManualRecoveryQueueService service = new ManualRecoveryQueueService(
        redisProvider,
        repository,
        new ObjectMapper(),
        Clock.fixed(NOW, ZoneOffset.UTC),
        10,
        Duration.ofMinutes(5)
    );

    service.publishPendingEntries();

    verify(repository).markPublishedIfClaimed(eq(13L), eq(ENQUEUED_AT), any(), eq(NOW));
    verify(repository, never()).releaseClaimIfMatches(any(), any(), any());
  }

  @Test
  void shouldSkipRowWhenAnotherPublisherAlreadyClaimedIt() {
    @SuppressWarnings("unchecked")
    ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
    ManualRecoveryQueueEntryRepository repository = mock(ManualRecoveryQueueEntryRepository.class);
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    ManualRecoveryQueueEntry entry = pendingEntry(14L, "session-2", "clord-2", 1, "reason-2");

    when(repository.findByPublishedAtIsNullAndResolvedAtIsNullOrderByEnqueuedAtAscIdAsc(any(Pageable.class)))
        .thenReturn(List.of(entry));
    when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
    when(repository.claimPendingIfAvailable(eq(14L), eq(ENQUEUED_AT), any(), eq(NOW), eq(NOW.minus(Duration.ofMinutes(5)))))
        .thenReturn(0);

    ManualRecoveryQueueService service = new ManualRecoveryQueueService(
        redisProvider,
        repository,
        new ObjectMapper(),
        Clock.fixed(NOW, ZoneOffset.UTC),
        10,
        Duration.ofMinutes(5)
    );

    service.publishPendingEntries();

    verify(redisTemplate, never()).execute(any(), anyList(), any(), any(), any());
    verify(repository, never()).markPublishedIfClaimed(any(), any(), any(), any());
  }

  @Test
  void shouldMarkPublishedEntryAsResolvedWhenReplayConverges() {
    @SuppressWarnings("unchecked")
    ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
    ManualRecoveryQueueEntryRepository repository = mock(ManualRecoveryQueueEntryRepository.class);
    ManualRecoveryQueueEntry entry = pendingEntry(15L, "session-3", "clord-3", 2, "ESCALATED_MANUAL_REVIEW");
    entry.markPublished(NOW.minusSeconds(10));

    when(repository.findByOrderSessionIdAndResolvedAtIsNull("session-3"))
        .thenReturn(java.util.Optional.of(entry));
    when(repository.markResolvedIfUnresolved(15L, ENQUEUED_AT, "operator-1", "COMPLETED", NOW))
        .thenReturn(1);

    ManualRecoveryQueueService service = new ManualRecoveryQueueService(
        redisProvider,
        repository,
        new ObjectMapper(),
        Clock.fixed(NOW, ZoneOffset.UTC),
        10,
        Duration.ofMinutes(5)
    );

    service.resolveIfPresent("session-3", "operator-1", "COMPLETED", NOW);

    verify(repository).markResolvedIfUnresolved(15L, ENQUEUED_AT, "operator-1", "COMPLETED", NOW);
  }

  @Test
  void shouldSkipResolveWhenQueueEntryStateChangesConcurrently() {
    @SuppressWarnings("unchecked")
    ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
    ManualRecoveryQueueEntryRepository repository = mock(ManualRecoveryQueueEntryRepository.class);
    ManualRecoveryQueueEntry entry = pendingEntry(16L, "session-4", "clord-4", 1, "ESCALATED_MANUAL_REVIEW");

    when(repository.findByOrderSessionIdAndResolvedAtIsNull("session-4"))
        .thenReturn(java.util.Optional.of(entry));
    when(repository.markResolvedIfUnresolved(16L, ENQUEUED_AT, "operator-2", "COMPLETED", NOW))
        .thenReturn(0);

    ManualRecoveryQueueService service = new ManualRecoveryQueueService(
        redisProvider,
        repository,
        new ObjectMapper(),
        Clock.fixed(NOW, ZoneOffset.UTC),
        10,
        Duration.ofMinutes(5)
    );

    service.resolveIfPresent("session-4", "operator-2", "COMPLETED", NOW);

    verify(repository).markResolvedIfUnresolved(16L, ENQUEUED_AT, "operator-2", "COMPLETED", NOW);
  }

  private ManualRecoveryQueueEntry pendingEntry(
      long id,
      String orderSessionId,
      String clOrdId,
      int attemptCount,
      String reason
  ) {
    ManualRecoveryQueueEntry entry = ManualRecoveryQueueEntry.pending(
        orderSessionId,
        clOrdId,
        attemptCount,
        reason,
        ENQUEUED_AT
    );
    ReflectionTestUtils.setField(entry, "id", id);
    return entry;
  }
}
