package com.fix.channel.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(
    value = "order.session.expiry-reconciliation.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class OrderSessionExpiryScheduler {

  private final OrderSessionPersistenceService orderSessionPersistenceService;
  private final OrderSessionTtlStore orderSessionTtlStore;
  private final Clock clock;
  private final int reconciliationBatchSize;

  public OrderSessionExpiryScheduler(
      OrderSessionPersistenceService orderSessionPersistenceService,
      OrderSessionTtlStore orderSessionTtlStore,
      Clock clock,
      @Value("${order.session.expiry-reconciliation.batch-size:100}") int reconciliationBatchSize
  ) {
    this.orderSessionPersistenceService = orderSessionPersistenceService;
    this.orderSessionTtlStore = orderSessionTtlStore;
    this.clock = clock;
    this.reconciliationBatchSize = reconciliationBatchSize;
  }

  @Scheduled(fixedDelayString = "${order.session.expiry-reconciliation.fixed-delay-ms:60000}")
  public void expireOverdueSessions() {
    Instant cutoff = Instant.now(clock);
    int batchSize = Math.max(1, reconciliationBatchSize);
    List<String> expiredSessionIds;
    do {
      expiredSessionIds = orderSessionPersistenceService.expireOverdueSessionBatch(cutoff, batchSize);
      expiredSessionIds.forEach(this::clearRedisStateSafely);
    } while (expiredSessionIds.size() == batchSize);
  }

  private void clearRedisStateSafely(String orderSessionId) {
    try {
      orderSessionTtlStore.clear(orderSessionId);
    } catch (RuntimeException ex) {
      log.warn("Failed to clear Redis state for expired order session during reconciliation: orderSessionId={}",
          orderSessionId, ex);
    }
  }
}
