# Epic 10 Resilience Evidence v1 (Story 10.3)

Status: LOCKED
Owner: BE corebank-service + channel-service
Scope: Story 10.3 simulator-driven circuit drills, recovery escalation, admin replay convergence evidence

## 1. Canonical Evidence Signals

| Signal | Meaning | Source |
| --- | --- | --- |
| `TIMEOUT` chaos action active | Simulator drill has injected the intended downstream failure mode | `CorebankSimulatorDrivenResilienceIntegrationTest` fixture rule API |
| `fep-submit` breaker state `OPEN` | Submit path is protected after repeated downstream failures | `CircuitBreakerRegistry` assertions in `E10-RES-001` |
| `fep-submit` breaker state `CLOSED` | Service has recovered after simulator rule clear and successful probe | `CircuitBreakerRegistry` assertions in `E10-RES-002` |
| Recovery audit with `outcome=ESCALATED` | Requery workflow exhausted or rejected and escalated into manual recovery | `AuditAction.ORDER_SESSION_RECOVERY_ATTEMPT` in `E10-RES-003` |
| Manual recovery queue published entry | Escalated order is handed off for operator action | `ManualRecoveryQueueEntryRepository` in `E10-RES-003` |
| Manual replay audit | Operator replay is governed and traceable | `AuditAction.MANUAL_REPLAY` in `E10-RES-003` |
| Final status `COMPLETED` after replay | Unresolved order converged through replay as designed | `AdminOrderReplayIntegrationTest` persisted session assertions |

## 2. Story 0.16 Linkage

- Story 0.16 observability stack is the runtime surface for resilience drill evidence, but Story 10.3 gate remains code-first and artifact-first.
- The following runtime metrics are the expected linkage points once Prometheus/Grafana dashboards consume the same workflows:
  - `channel_order_sessions_recovery_backlog_last_updated_epoch_seconds`
  - `channel_order_execution_last_completed_epoch_seconds`
  - `channel_order_recovery_requery_attempts_total`
  - `channel_order_recovery_convergence_total`
- Circuit-breaker state should also be visible through actuator/prometheus exposure for the `fep-submit` breaker during operator drill rehearsal.

## 3. Gate Expectations

- `E10-RES-001` must prove:
  - timeout rule applied
  - three failed submits trip the breaker
  - the next protected submit is rejected without another downstream mutation
- `E10-RES-002` must prove:
  - simulator rules are cleared
  - a recovery probe succeeds
  - breaker state returns to `CLOSED`
- `E10-RES-003` must prove:
  - requery-driven recovery can escalate into manual recovery
  - queue/audit evidence is persisted
  - admin replay converges the same order into a terminal state

## 4. Evidence Path

- Runtime scrape endpoint: `GET /actuator/prometheus`
- Story 10.3 evidence producers:
  - `CorebankSimulatorDrivenResilienceIntegrationTest`
  - `AdminOrderReplayIntegrationTest`
- Gate artifact root:
  - `_bmad-output/test-artifacts/epic-10/<build-id>/story-10-3/`
- Required summary outputs:
  - `matrix-summary.md`
  - `matrix-summary.json`
  - JUnit XML for canonical resilience drills

## 5. Operator Notes

1. When `E10-RES-001` fails, inspect whether the simulator rule application failed or whether the breaker configuration drifted from the documented `COUNT_BASED` threshold.
2. When `E10-RES-002` fails, separate downstream recovery issues from breaker half-open timing issues before blaming the gateway.
3. When `E10-RES-003` fails, identify whether convergence failed in the scheduler path, queue publication path, or admin replay path before re-running the drill.
