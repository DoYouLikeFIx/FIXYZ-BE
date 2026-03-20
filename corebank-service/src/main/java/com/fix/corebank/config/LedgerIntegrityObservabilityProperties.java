package com.fix.corebank.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "corebank.ledger-integrity.observability")
public class LedgerIntegrityObservabilityProperties {

  private Duration staleAfter = Duration.ofMinutes(5);
  private final Alert alert = new Alert();

  public Duration getStaleAfter() {
    return staleAfter;
  }

  public void setStaleAfter(Duration staleAfter) {
    if (staleAfter == null || staleAfter.isZero() || staleAfter.isNegative()) {
      throw new IllegalArgumentException("corebank.ledger-integrity.observability.stale-after must be positive");
    }
    this.staleAfter = staleAfter;
  }

  public Alert getAlert() {
    return alert;
  }

  public static class Alert {

    private long unresolvedBacklogThreshold = 10L;
    private long repairPendingThreshold = 5L;
    private long criticalAnomalyThreshold = 1L;

    public long getUnresolvedBacklogThreshold() {
      return unresolvedBacklogThreshold;
    }

    public void setUnresolvedBacklogThreshold(long unresolvedBacklogThreshold) {
      if (unresolvedBacklogThreshold < 0) {
        throw new IllegalArgumentException(
            "corebank.ledger-integrity.observability.alert.unresolved-backlog-threshold cannot be negative"
        );
      }
      this.unresolvedBacklogThreshold = unresolvedBacklogThreshold;
    }

    public long getRepairPendingThreshold() {
      return repairPendingThreshold;
    }

    public void setRepairPendingThreshold(long repairPendingThreshold) {
      if (repairPendingThreshold < 0) {
        throw new IllegalArgumentException(
            "corebank.ledger-integrity.observability.alert.repair-pending-threshold cannot be negative"
        );
      }
      this.repairPendingThreshold = repairPendingThreshold;
    }

    public long getCriticalAnomalyThreshold() {
      return criticalAnomalyThreshold;
    }

    public void setCriticalAnomalyThreshold(long criticalAnomalyThreshold) {
      if (criticalAnomalyThreshold < 0) {
        throw new IllegalArgumentException(
            "corebank.ledger-integrity.observability.alert.critical-anomaly-threshold cannot be negative"
        );
      }
      this.criticalAnomalyThreshold = criticalAnomalyThreshold;
    }
  }
}
