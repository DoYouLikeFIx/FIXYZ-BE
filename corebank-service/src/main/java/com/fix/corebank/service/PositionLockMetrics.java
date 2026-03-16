package com.fix.corebank.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PositionLockMetrics {

  private static final Duration[] LOCK_SLOS = {
      Duration.ofMillis(10),
      Duration.ofMillis(50),
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofSeconds(1)
  };

  private final Timer waitTimer;
  private final Timer holdTimer;
  private final Counter conflictCounter;

  public PositionLockMetrics(MeterRegistry meterRegistry) {
    this.waitTimer = Timer.builder("corebank.order.position.lock.wait")
        .description("Time spent waiting to acquire a pessimistic position lock")
        .publishPercentileHistogram()
        .serviceLevelObjectives(LOCK_SLOS)
        .register(meterRegistry);
    this.holdTimer = Timer.builder("corebank.order.position.lock.hold")
        .description("Time a pessimistic position lock is held until transaction completion")
        .publishPercentileHistogram()
        .serviceLevelObjectives(LOCK_SLOS)
        .register(meterRegistry);
    this.conflictCounter = Counter.builder("corebank.order.position.lock.conflicts")
        .description("Count of deterministic position lock contention failures")
        .register(meterRegistry);
  }

  public void recordWait(long startedAtNanos) {
    waitTimer.record(System.nanoTime() - startedAtNanos, TimeUnit.NANOSECONDS);
  }

  public void recordHoldOnTransactionCompletion(long lockAcquiredAtNanos) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      holdTimer.record(System.nanoTime() - lockAcquiredAtNanos, TimeUnit.NANOSECONDS);
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        holdTimer.record(System.nanoTime() - lockAcquiredAtNanos, TimeUnit.NANOSECONDS);
      }
    });
  }

  public void incrementConflicts() {
    conflictCounter.increment();
  }
}
