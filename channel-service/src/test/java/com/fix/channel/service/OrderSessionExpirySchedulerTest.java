package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
    scheduler = new OrderSessionExpiryScheduler(persistenceService, ttlStore, 2);
  }

  @Test
  void shouldExpireOverdueSessionsInChunksAndClearRedisKeys() {
    persistenceService.expiredSessionIdBatches = List.of(
        List.of("sess-1", "sess-2"),
        List.of("sess-3")
    );

    scheduler.expireOverdueSessions();

    assertThat(persistenceService.cutoff).isNotNull();
    assertThat(persistenceService.requestedBatchSizes).containsExactly(2, 2);
    assertThat(ttlStore.clearedSessionIds).containsExactly("sess-1", "sess-2", "sess-3");
  }

  private static class RecordingPersistenceService extends OrderSessionPersistenceService {

    private Instant cutoff;
    private List<List<String>> expiredSessionIdBatches = List.of();
    private final List<Integer> requestedBatchSizes = new ArrayList<>();

    RecordingPersistenceService() {
      super(null, null);
    }

    @Override
    public List<String> expireOverdueSessionBatch(Instant cutoff, int batchSize) {
      this.cutoff = cutoff;
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

    @Override
    public void activate(String orderSessionId) {
    }

    @Override
    public java.util.Optional<Long> remainingSeconds(String orderSessionId) {
      return java.util.Optional.empty();
    }

    @Override
    public void clear(String orderSessionId) {
      clearedSessionIds.add(orderSessionId);
    }

    @Override
    public long ttlSeconds() {
      return 600L;
    }
  }
}
