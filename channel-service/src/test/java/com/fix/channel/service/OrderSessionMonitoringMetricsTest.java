package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.OrderSessionStatus;
import com.fix.channel.repository.OrderSessionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrderSessionMonitoringMetricsTest {

  @Test
  void shouldExposePendingOrderSessionGauge() {
    OrderSessionRepository repository = mock(OrderSessionRepository.class);
    when(repository.countByStatusIn(anyCollection())).thenReturn(4L);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    OrderSessionMonitoringMetrics metrics = new OrderSessionMonitoringMetrics(repository, meterRegistry);

    double gaugeValue = meterRegistry.get("channel.order.sessions.pending").gauge().value();

    assertThat(gaugeValue).isEqualTo(4.0d);

    ArgumentCaptor<Collection<OrderSessionStatus>> statusesCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(repository).countByStatusIn(statusesCaptor.capture());
    assertThat(statusesCaptor.getValue()).containsExactlyInAnyOrder(
        OrderSessionStatus.PENDING_NEW,
        OrderSessionStatus.AUTHED,
        OrderSessionStatus.EXECUTING,
        OrderSessionStatus.REQUERYING
    );
    assertThat(metrics).isNotNull();
  }

  @Test
  void shouldRecordCompletedExecutionCounterWithNormalizedResultTag() {
    OrderSessionRepository repository = mock(OrderSessionRepository.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    OrderSessionMonitoringMetrics metrics = new OrderSessionMonitoringMetrics(repository, meterRegistry);
    OrderSession session = mock(OrderSession.class);
    when(session.getExecutionResult()).thenReturn("FILLED");

    metrics.recordExecutionCompleted(session);

    assertThat(
        meterRegistry.get("channel.order.execution.completed").tag("result", "filled").counter().count()
    ).isEqualTo(1.0d);
  }
}
