# Epic 10 Concurrency/Performance Observability v1 (Story 10.2)

Status: LOCKED
Owner: BE channel-service + corebank-service
Scope: Story 10.2 concurrency gate, execute latency p95 evidence, Story 0.16 observability linkage

## 1. Canonical Metrics

| Metric | Prometheus series | Meaning | Source |
|---|---|---|---|
| Execute latency | `channel_order_execution_latency_seconds` | Execute path latency histogram tagged by terminal `outcome` | `OrderExecutionService.execute()` + `OrderSessionMonitoringMetrics.recordExecutionLatency()` |
| Execute completion count | `channel_order_execution_completed_total` | Completed execution count segmented by execution `result` | `OrderSessionMonitoringMetrics.recordExecutionCompleted()` |
| Execute freshness | `channel_order_execution_last_completed_epoch_seconds` | Latest completed execution timestamp used by Story 0.16 monitoring freshness | `OrderSessionMonitoringMetrics` |
| Recovery backlog freshness | `channel_order_sessions_recovery_backlog_last_updated_epoch_seconds` | Latest backlog update timestamp used by Story 0.16 monitoring freshness | `OrderSessionMonitoringMetrics` |
| Market-data freshness | `fep_marketdata_snapshots_last_persisted_epoch_seconds` | Latest persisted market-data snapshot timestamp used by Story 0.16 monitoring freshness | `MarketDataMetrics` |
| Position lock wait | `corebank_order_position_lock_wait_seconds` | Time spent waiting for the pessimistic position lock | `PositionLockMetrics` |
| Position lock hold | `corebank_order_position_lock_hold_seconds` | Time a pessimistic position lock is held until transaction completion | `PositionLockMetrics` |
| Position lock conflicts | `corebank_order_position_lock_conflicts_total` | Deterministic conflict count for same-symbol lock contention | `PositionLockMetrics` |

## 2. Story 0.16 Linkage

- Story 0.16 admin monitoring freshness wiring uses:
  - `channel_order_execution_last_completed_epoch_seconds`
  - `channel_order_sessions_recovery_backlog_last_updated_epoch_seconds`
  - `fep_marketdata_snapshots_last_persisted_epoch_seconds`
- `AdminMonitoringFreshnessService` resolves those metrics against Prometheus jobs `channel-service` and `fep-gateway`.
- Story 10.2 extends that observability surface with `channel_order_execution_latency_seconds` so Grafana release panels can evaluate p95 latency from real Prometheus histogram buckets instead of ad-hoc stopwatch output alone.

## 3. SLO / Gate Thresholds

- Primary release SLO: `completed execute path p95 <= 1000ms`.
- Gate failure rule: any Story 10.2 benchmark evidence showing `p95 > 1000ms` blocks the release gate.
- Variance rule: repeated benchmark runs must report when p95 drift exceeds the configured acceptable spread in the Story 10.2 gate summary.
- Concurrency rule: same-position oversell and lock-contention scenarios must remain green before performance evidence is considered valid.

## 4. PromQL Examples

Completed execute path p95:

```promql
histogram_quantile(
  0.95,
  sum(rate(channel_order_execution_latency_seconds_bucket{outcome="completed"}[5m])) by (le)
)
```

Completed execute request volume:

```promql
sum(rate(channel_order_execution_completed_total{result="filled"}[5m]))
```

Channel execution freshness:

```promql
time() - max(channel_order_execution_last_completed_epoch_seconds)
```

Same-symbol lock conflict spike:

```promql
increase(corebank_order_position_lock_conflicts_total[5m])
```

## 5. Evidence Path

- Runtime scrape endpoint: `GET /actuator/prometheus`
- Required actuator exposure: `health,info,metrics,prometheus,circuitbreakers`
- Story 10.2 evidence producers:
  - `PositionConcurrencyIntegrationTest`
  - `PositionLockContentionIntegrationTest`
  - `OrderExecuteLatencySmokeTest`
- `OrderExecuteLatencySmokeTest` emits `perf-p95.json` with:
  - `scenarioId=E10-PERF-001`
  - local benchmark samples and derived p95
  - `channel_order_execution_latency_seconds` metric family linkage
  - timer count and max-time snapshot for the `outcome=completed` series

## 6. Operator Notes

1. Confirm `/actuator/prometheus` exports `channel_order_execution_latency_seconds_bucket` before trusting Grafana p95 panels.
2. When p95 breaches, compare the Story 10.2 benchmark artifact with the Prometheus histogram to determine whether the slowdown is reproducible or environment-specific.
3. If latency rises together with `corebank_order_position_lock_wait_seconds`, treat it as downstream contention before looking for channel-only regressions.
4. If freshness metrics go stale while latency looks normal, investigate the Story 0.16 observability path separately from the Story 10.2 execute-path SLA.
