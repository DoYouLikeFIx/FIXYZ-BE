# Epic 10 Acceptance Matrix

Story 10.1 canonical 7+1 acceptance coverage is owned by the following automated tests.

| Scenario ID | Description | Owner Test |
| --- | --- | --- |
| `E10-001` | Order request to execution happy path | `com.fix.channel.integration.OrderSessionIntegrationTest#e10_001ShouldCompleteLowRiskTrustedOrderExecutionHappyPath` |
| `E10-002` | Concurrent sell on same position | `com.fix.corebank.integration.PositionConcurrencyIntegrationTest#e10_002ShouldAllowExactlyFiveFilledSellOrdersWithoutOversellUnderTenThreadLoad` |
| `E10-003` | Required step-up failure blocks execution | `com.fix.channel.integration.OrderSessionIntegrationTest#e10_003ShouldBlockExecuteWhenElevatedRiskSessionIsNotStepUpAuthorized` |
| `E10-004` | Duplicate client order key replay | `com.fix.corebank.integration.CorebankOrderIdempotencyIntegrationTest#e10_004ShouldCommitOnlyOnePostingPathForConcurrentDuplicateOrderRequests` |
| `E10-005` | Repeated external timeout opens protection circuit | `com.fix.corebank.integration.CorebankSimulatorDrivenResilienceIntegrationTest#e10_005ShouldDriveSubmitBreakerTransitionsFromCanonicalChaosRulesApi` |
| `E10-006` | Session invalidated after logout | `com.fix.channel.integration.ChannelAuthSessionIntegrationTest#e10_006ShouldLogoutAndExpireSessionCookieImmediately` |
| `E10-007` | Ledger integrity after repeated executions | `com.fix.corebank.integration.LedgerIntegrityIntegrationTest#e10_007ShouldPassWhenCompletedOrdersHaveBalancedLedgerEvidence` |
| `E10-008` | Internal endpoint call without internal secret is denied with 401 | `com.fix.corebank.security.InternalSecretFilterTest#e10_008ShouldBlockInternalRouteWithoutSecret` |
