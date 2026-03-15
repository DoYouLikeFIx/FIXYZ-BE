package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderSessionExpirySchedulerTest {

  private RecordingPersistenceService persistenceService;
  private RecordingTtlStore ttlStore;
  private OrderSessionExpiryScheduler scheduler;

  @BeforeEach
  void setUp() {
    persistenceService = new RecordingPersistenceService();
    ttlStore = new RecordingTtlStore();
    scheduler = new OrderSessionExpiryScheduler(
        persistenceService,
        ttlStore,
        Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC),
        2
    );
  }

  @Test
  void shouldExpireOverdueSessionsInChunksAndClearRedisKeys() {
    persistenceService.expiredSessionIdBatches = List.of(
        List.of("sess-1", "sess-2"),
        List.of("sess-3")
    );

    scheduler.expireOverdueSessions();

    assertThat(persistenceService.referenceTime).isEqualTo(Instant.parse("2026-03-12T00:00:00Z"));
    assertThat(persistenceService.requestedBatchSizes).containsExactly(2, 2);
    assertThat(ttlStore.clearedSessionIds).containsExactly("sess-1", "sess-2", "sess-3");
  }

  @Test
  void shouldContinueWhenRedisCleanupFailsForSingleSession() {
    persistenceService.expiredSessionIdBatches = List.of(
        List.of("sess-1", "sess-2"),
        List.of("sess-3")
    );
    ttlStore.failOnClear("sess-2");

    scheduler.expireOverdueSessions();

    assertThat(persistenceService.requestedBatchSizes).containsExactly(2, 2);
    assertThat(ttlStore.clearedSessionIds).containsExactly("sess-1", "sess-2", "sess-3");
  }

  private static class RecordingPersistenceService extends OrderSessionPersistenceService {

    private Instant referenceTime;
    private List<List<String>> expiredSessionIdBatches = List.of();
    private final List<Integer> requestedBatchSizes = new ArrayList<>();

    RecordingPersistenceService() {
      super(null, null);
    }

    @Override
    public List<String> expireOverdueSessionBatch(Instant referenceTime, int batchSize) {
      this.referenceTime = referenceTime;
      requestedBatchSizes.add(batchSize);
      if (expiredSessionIdBatches.isEmpty()) {
        return List.of();
      }
      List<String> batch = expiredSessionIdBatches.getFirst();
      expiredSessionIdBatches = expiredSessionIdBatches.subList(1, expiredSessionIdBatches.size());
      return batch;
    }
  }

  private static class RecordingTtlStore implements OrderSessionTtlStore {

    private final List<String> clearedSessionIds = new ArrayList<>();
    private String failingSessionId;

    @Override
    public void activate(String orderSessionId, Instant expiresAt) {
    }

    @Override
    public void refresh(String orderSessionId, Instant expiresAt) {
    }

    @Override
    public boolean isActive(String orderSessionId) {
      return false;
    }

    @Override
    public void clear(String orderSessionId) {
      clearedSessionIds.add(orderSessionId);
      if (orderSessionId.equals(failingSessionId)) {
        throw new IllegalStateException("simulated redis clear failure");
      }
    }

    @Override
    public Duration ttl() {
      return Duration.ofMinutes(60);
    }

    void failOnClear(String orderSessionId) {
      failingSessionId = orderSessionId;
    }
  }
}
