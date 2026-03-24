package com.fix.channel.service;

import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.OrderSessionStatus;
import com.fix.channel.repository.OrderSessionRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderSessionMonitoringMetrics {

  private static final List<OrderSessionStatus> RECOVERY_BACKLOG_STATUSES = List.of(
      OrderSessionStatus.REQUERYING,
      OrderSessionStatus.ESCALATED
  );

  private final OrderSessionRepository orderSessionRepository;
  private final MeterRegistry meterRegistry;
  private final AtomicLong lastPendingSessionUpdatedEpochSeconds;
  private final AtomicLong lastCompletedExecutionEpochSeconds;

  @Autowired
  public OrderSessionMonitoringMetrics(
      OrderSessionRepository orderSessionRepository,
      MeterRegistry meterRegistry
  ) {
    this.orderSessionRepository = orderSessionRepository;
    this.meterRegistry = meterRegistry;
    this.lastPendingSessionUpdatedEpochSeconds = new AtomicLong(seedLatestRecoveryBacklogUpdateEpochSeconds());
    this.lastCompletedExecutionEpochSeconds = new AtomicLong(seedLatestCompletedExecutionEpochSeconds());
    Gauge.builder("channel.order.sessions.recovery.backlog", orderSessionRepository, this::countPendingSessions)
        .description("Current recovery backlog sessions awaiting requery or manual intervention")
        .register(meterRegistry);
    Gauge.builder(
        "channel.order.sessions.recovery.backlog.last.updated.epoch.seconds",
        lastPendingSessionUpdatedEpochSeconds,
        AtomicLong::get
    )
        .description("Epoch seconds of the latest updatedAt among sessions currently in the recovery backlog")
        .register(meterRegistry);
    Gauge.builder(
        "channel.order.execution.last.completed.epoch.seconds",
        lastCompletedExecutionEpochSeconds,
        AtomicLong::get
    )
        .description("Epoch seconds of the most recent completed order execution")
        .register(meterRegistry);
  }

  public void recordExecutionCompleted(OrderSession session) {
    meterRegistry.counter(
        "channel.order.execution.completed",
        "result",
        normalizeExecutionResult(session.getExecutionResult())
    ).increment();
    lastCompletedExecutionEpochSeconds.accumulateAndGet(resolveExecutionEpochSeconds(session), Math::max);
  }

  public void refreshRecoveryBacklogLastUpdated() {
    lastPendingSessionUpdatedEpochSeconds.set(seedLatestRecoveryBacklogUpdateEpochSeconds());
  }

  double countPendingSessions(OrderSessionRepository repository) {
    return repository.countByStatusIn(RECOVERY_BACKLOG_STATUSES);
  }

  boolean isRecoveryBacklogStatus(OrderSessionStatus status) {
    return status != null && RECOVERY_BACKLOG_STATUSES.contains(status);
  }

  private long seedLatestRecoveryBacklogUpdateEpochSeconds() {
    Optional<OrderSession> latestSession = Optional.ofNullable(
        orderSessionRepository.findTopByStatusInOrderByUpdatedAtDescIdDesc(RECOVERY_BACKLOG_STATUSES)
    ).orElse(Optional.empty());
    return latestSession.map(OrderSession::getUpdatedAt).map(OrderSessionMonitoringMetrics::toEpochSeconds).orElse(0L);
  }

  private long seedLatestCompletedExecutionEpochSeconds() {
    return orderSessionRepository.findByStatusOrderByEffectiveExecutionTimestampDesc(
            OrderSessionStatus.COMPLETED,
            PageRequest.of(0, 1)
        )
        .stream()
        .findFirst()
        .map(this::resolveExecutionEpochSeconds)
        .orElse(0L);
  }

  private long resolveExecutionEpochSeconds(OrderSession session) {
    if (session.getExecutedAt() != null) {
      return toEpochSeconds(session.getExecutedAt());
    }
    return toEpochSeconds(session.getUpdatedAt());
  }

  private static long toEpochSeconds(Instant timestamp) {
    return timestamp == null ? 0L : timestamp.getEpochSecond();
  }

  private String normalizeExecutionResult(String executionResult) {
    if (executionResult == null || executionResult.isBlank()) {
      return "unknown";
    }
    return executionResult.toLowerCase(Locale.ROOT);
  }
}
