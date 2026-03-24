# Epic 10 Concurrency/Performance Matrix

Story 10.2 canonical concurrency and performance coverage is owned by the following automated tests and gate evidence.

| Scenario ID | Description | Owner Test / Evidence |
| --- | --- | --- |
| `E10-CONC-001` | Same-position concurrent sell requests preserve final position integrity under 100-thread load | `com.fix.corebank.integration.PositionConcurrencyIntegrationTest#e10Conc001ShouldPreservePositionIntegrityAcrossHundredConcurrentSellAttempts` |
| `E10-CONC-002` | Same-symbol lock contention returns deterministic conflict contract and emits observability metrics | `com.fix.corebank.integration.PositionLockContentionIntegrationTest#e10Conc002ShouldExposeConflictMetricsWhenSameSymbolPositionLockContentionExceedsTimeout` |
| `E10-PERF-001` | Execute path p95 stays within the configured SLA budget | `com.fix.channel.perf.OrderExecuteLatencySmokeTest#e10Perf001ShouldKeepExecuteP95WithinConfiguredBudget` |
| `E10-PERF-002` | Threshold breach blocks the release gate with metric evidence | Story 10.2 gate summary generated from `perf-p95.json` and `channel_order_execution_latency_seconds` evidence |
| `E10-PERF-003` | Repeated benchmark runs surface unacceptable variance | Story 10.2 repeated-run summary comparing multiple `perf-p95.json` artifacts |

## Notes

- `E10-CONC-*` scenarios are owned by `corebank-service` and prove race/integrity correctness from Story 5.5 assets.
- `E10-PERF-*` scenarios are owned by `channel-service` and reuse the execute benchmark path introduced for Story 11.9, with Story 10.2-specific scenario IDs and metric evidence.
- Runtime observability linkage depends on Story 0.16 metric contracts exposed through Prometheus and surfaced by Grafana-backed monitoring.
