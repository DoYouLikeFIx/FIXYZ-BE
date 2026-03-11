package com.fix.channel.service;

import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    value = "order.session.expiry-reconciliation.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class OrderSessionExpiryScheduler {

  private final OrderSessionPersistenceService orderSessionPersistenceService;
  private final OrderSessionTtlStore orderSessionTtlStore;
  private final int reconciliationBatchSize;

  public OrderSessionExpiryScheduler(
      OrderSessionPersistenceService orderSessionPersistenceService,
      OrderSessionTtlStore orderSessionTtlStore,
      @Value("${order.session.expiry-reconciliation.batch-size:100}") int reconciliationBatchSize
  ) {
    this.orderSessionPersistenceService = orderSessionPersistenceService;
    this.orderSessionTtlStore = orderSessionTtlStore;
    this.reconciliationBatchSize = reconciliationBatchSize;
  }

  @Scheduled(fixedDelayString = "${order.session.expiry-reconciliation.fixed-delay-ms:60000}")
  public void expireOverdueSessions() {
    Instant cutoff = Instant.now().minusSeconds(orderSessionTtlStore.ttlSeconds());
    int batchSize = Math.max(1, reconciliationBatchSize);
    List<String> expiredSessionIds;
    do {
      expiredSessionIds = orderSessionPersistenceService.expireOverdueSessionBatch(cutoff, batchSize);
      expiredSessionIds.forEach(orderSessionTtlStore::clear);
    } while (expiredSessionIds.size() == batchSize);
  }
}
