package com.fix.channel.service;

import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.OrderSessionStatus;
import com.fix.channel.repository.OrderSessionRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class OrderSessionMonitoringMetrics {

  private static final List<OrderSessionStatus> PENDING_STATUSES = List.of(
      OrderSessionStatus.PENDING_NEW,
      OrderSessionStatus.AUTHED,
      OrderSessionStatus.EXECUTING,
      OrderSessionStatus.REQUERYING
  );

  private final OrderSessionRepository orderSessionRepository;
  private final MeterRegistry meterRegistry;

  public OrderSessionMonitoringMetrics(
      OrderSessionRepository orderSessionRepository,
      MeterRegistry meterRegistry
  ) {
    this.orderSessionRepository = orderSessionRepository;
    this.meterRegistry = meterRegistry;
    Gauge.builder("channel.order.sessions.pending", orderSessionRepository, this::countPendingSessions)
        .description("Current non-terminal order sessions awaiting execution or recovery")
        .register(meterRegistry);
  }

  public void recordExecutionCompleted(OrderSession session) {
    meterRegistry.counter(
        "channel.order.execution.completed",
        "result",
        normalizeExecutionResult(session.getExecutionResult())
    ).increment();
  }

  double countPendingSessions(OrderSessionRepository repository) {
    return repository.countByStatusIn(PENDING_STATUSES);
  }

  private String normalizeExecutionResult(String executionResult) {
    if (executionResult == null || executionResult.isBlank()) {
      return "unknown";
    }
    return executionResult.toLowerCase(Locale.ROOT);
  }
}
