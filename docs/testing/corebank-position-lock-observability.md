# CoreBank Position Lock Observability v1 (Story 5.5)

Status: LOCKED
Owner: BE corebank-service
Scope: Story 5.5 pessimistic position locking, conflict alerting, NFR-P5 evidence

## 1. Metrics

| Metric | Prometheus series | Meaning | Source |
|---|---|---|---|
| Position lock wait | `corebank_order_position_lock_wait_seconds` | Time spent waiting to acquire `positions` pessimistic lock | `CorebankOrderPersistenceService.prepareOrderSubmission()` |
| Position lock hold | `corebank_order_position_lock_hold_seconds` | Time from position lock acquisition until transaction completion | `CorebankOrderPersistenceService.prepareOrderSubmission()` + transaction completion hook |
| Position lock conflicts | `corebank_order_position_lock_conflicts_total` | Deterministic `CORE-003` conflict count for position lock contention | `CorebankOrderService.createFreshOrder()` |

## 2. SLO / Alert Thresholds

- Primary SLO: `position lock hold p95 <= 100ms` under normal operating conditions.
- Warning alert: `position lock hold p95 > 100ms` for 10 minutes.
- Critical alert: `position lock hold p95 > 250ms` for 5 minutes.
- Warning alert: `position lock wait p95 > 50ms` for 10 minutes.
- Critical alert: `increase(position lock conflicts_total[5m]) >= 5`.
- Immediate investigation rule: any sustained `CORE-003` spike combined with rising lock wait means symbol-level hot spot or transaction elongation.

## 3. PromQL Examples

Lock hold p95:

```promql
histogram_quantile(
  0.95,
  sum(rate(corebank_order_position_lock_hold_seconds_bucket[5m])) by (le)
)
```

Lock wait p95:

```promql
histogram_quantile(
  0.95,
  sum(rate(corebank_order_position_lock_wait_seconds_bucket[5m])) by (le)
)
```

Conflict count over 5 minutes:

```promql
increase(corebank_order_position_lock_conflicts_total[5m])
```

## 4. Evidence Path

- Runtime scrape endpoint: `GET /actuator/prometheus`
- Required actuator exposure: `health,info,metrics,prometheus,circuitbreakers`
- Story 5.5 evidence tests:
  - `PositionConcurrencyIntegrationTest`
  - `PositionLockContentionIntegrationTest`
  - `CorebankSameBankLedgerPostingIntegrationTest`
- `PositionLockContentionIntegrationTest` verifies:
  - same-symbol contention returns `409 CORE-003`
  - no partial ledger/order writes on conflict path
  - `/actuator/prometheus` exports the 3 required lock metrics

## 5. Operator Runbook

1. Check `/actuator/prometheus` and confirm all 3 lock metrics are present.
2. If `hold p95` breaches `100ms`, inspect recent deploys and long-running transaction paths around `prepareOrderSubmission()`.
3. If `conflicts_total` spikes, identify hot `(account_id, symbol)` pairs from application logs and recent order bursts.
4. If `wait p95` rises without many conflicts, treat it as lock queue growth before user-visible failures.
5. Re-run `PositionLockContentionIntegrationTest` locally before changing lock timeout or transaction scope.

## 6. Acceptance Criteria Linkage

- AC2: deterministic contention failure is measured by `corebank_order_position_lock_conflicts_total`.
- AC4: cross-symbol isolation remains observable because unrelated symbols should not inflate same-symbol lock wait/hold.
- AC5: Prometheus export and alert thresholds provide the operational evidence path for NFR-P5.
