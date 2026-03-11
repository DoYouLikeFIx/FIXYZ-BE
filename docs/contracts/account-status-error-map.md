# Account Status Error Mapping v1 (Story 2.6)

Status: LOCKED  
Owner: BE corebank-service + BE channel-service  
Scope: Story 2.6 status query/transition/order-guard

## 1. Mapping table

| Scenario | Boundary | HTTP | Code | Notes |
|---|---|---:|---|---|
| Missing/invalid `X-Internal-Secret` | corebank internal | 401 | CORE-9401 | filter-level reject |
| Status contract validation failure | corebank internal | 400 | VALIDATION-001 | invalid request field |
| Account ownership mismatch | corebank internal | 403 | AUTH-005 | `memberId` not account owner |
| Account not found | corebank internal | 404 | SYS_404 | missing account id |
| Account status unsupported in storage | corebank internal | 422 | CONTRACT-001 | invalid persisted status |
| Order blocked by status (`FROZEN/CLOSED`) | corebank internal order create | 422 | ORD-012 | deterministic reject |
| Core dependency timeout | channel boundary | 504 | CORE-901 | upstream timeout normalize |
| Core dependency unavailable | channel boundary | 503 | CORE-902 | upstream unavailable normalize |
| Ownership mismatch from corebank transition | channel boundary | 403 | AUTH-005 | machine code preserved |

## 2. Normalization policy
- Corebank emits machine code + message in standard error envelope.
- Channel preserves known machine codes from corebank payload.
- Unknown upstream non-503/504 failures normalize to `SYS_500`.

## 3. Correlation policy
- `X-Correlation-Id` required on corebank transition endpoint.
- Channel forwards inbound correlation id to corebank.
- Error responses at both boundaries must include the same correlation id.

## 4. Idempotency/no-op semantics
- Status transition to same status is success, not error.
- Response is `200` with `changed=false` and no event emission.
