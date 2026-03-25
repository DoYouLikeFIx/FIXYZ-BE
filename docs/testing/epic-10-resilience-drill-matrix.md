# Epic 10 Resilience Drill Matrix

Story 10.3 canonical resilience drill coverage is owned by the following automated tests and gate evidence.

| Scenario ID | Description | Owner Test / Evidence |
| --- | --- | --- |
| `E10-RES-001` | Repeated simulator timeout/failure injection opens the downstream submit circuit and blocks unsafe calls | `com.fix.corebank.integration.CorebankSimulatorDrivenResilienceIntegrationTest#e10Res001ShouldOpenSubmitBreakerAfterRepeatedSimulatorTimeouts` |
| `E10-RES-002` | Clearing simulator chaos rules allows a recovery probe and closes the submit circuit back to normal flow | `com.fix.corebank.integration.CorebankSimulatorDrivenResilienceIntegrationTest#e10Res002ShouldCloseSubmitBreakerAfterRecoveryProbeSucceeds` |
| `E10-RES-003` | Unresolved requerying order escalates into manual recovery and converges through governed admin replay | `com.fix.channel.integration.AdminOrderReplayIntegrationTest#e10Res003ShouldEscalateUnresolvedRequeryingOrderAndConvergeThroughAdminReplay` |

## Notes

- `E10-RES-001` and `E10-RES-002` reuse the Story 10.1 simulator chaos fixture without changing the Story 10.1 owner test.
- `E10-RES-003` intentionally spans both scheduler-driven recovery escalation and admin replay convergence so Story 10.3 is anchored to one end-to-end operator workflow.
- Gate evidence for Story 10.3 is expected under `_bmad-output/test-artifacts/epic-10/<build-id>/story-10-3/`.
