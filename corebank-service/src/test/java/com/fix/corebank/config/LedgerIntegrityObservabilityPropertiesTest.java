package com.fix.corebank.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LedgerIntegrityObservabilityPropertiesTest {

  @Test
  void shouldExposeOperationallySaferAlertDefaults() {
    LedgerIntegrityObservabilityProperties properties = new LedgerIntegrityObservabilityProperties();

    assertThat(properties.getStaleAfter()).isEqualTo(Duration.ofMinutes(5));
    assertThat(properties.getAlert().getUnresolvedBacklogThreshold()).isEqualTo(10L);
    assertThat(properties.getAlert().getRepairPendingThreshold()).isEqualTo(5L);
    assertThat(properties.getAlert().getCriticalAnomalyThreshold()).isEqualTo(1L);
  }

  @Test
  void shouldRejectNonPositiveStaleAfter() {
    LedgerIntegrityObservabilityProperties properties = new LedgerIntegrityObservabilityProperties();

    assertThatThrownBy(() -> properties.setStaleAfter(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("stale-after");
    assertThatThrownBy(() -> properties.setStaleAfter(Duration.ofMinutes(-1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("stale-after");
    assertThatThrownBy(() -> properties.setStaleAfter(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("stale-after");
  }

  @Test
  void shouldRejectNegativeAlertThresholds() {
    LedgerIntegrityObservabilityProperties properties = new LedgerIntegrityObservabilityProperties();

    assertThatThrownBy(() -> properties.getAlert().setUnresolvedBacklogThreshold(-1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unresolved-backlog-threshold");
    assertThatThrownBy(() -> properties.getAlert().setRepairPendingThreshold(-1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("repair-pending-threshold");
    assertThatThrownBy(() -> properties.getAlert().setCriticalAnomalyThreshold(-1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("critical-anomaly-threshold");
  }
}
