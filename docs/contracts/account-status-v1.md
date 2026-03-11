# Account Status Contract v1 (Story 2.6)

Status: LOCKED  
Owner: BE corebank-service + BE channel-service  
Scope: Story 2.6 account status read/transition + order eligibility guard

## 1. Source of truth
- Canonical: Epic 2 / Story 2.6 acceptance criteria.
- Contract snapshot: `contracts/openapi/corebank-service.json`, `contracts/openapi/channel-service.json`.
- Error codes: `core-common/src/main/java/com/fix/common/error/ErrorCode.java`.

## 2. Scope boundary
- In scope:
  - account status query (`ACTIVE|FROZEN|CLOSED`)
  - account status transition with event log
  - order eligibility guard (`ORD-012`)
- Out of scope:
  - status event read API
  - bulk transition

## 3. Corebank internal APIs

### 3.1 Query
- Method/Path: `GET /internal/v1/accounts/{accountId}/status`
- Required query:
  - `memberId`
- Response fields:
  - `accountId`, `memberId`, `accountNumber`, `status`, `orderEligible`, `denialCode`, `asOf`
- Deterministic rule:
  - `ACTIVE` => `orderEligible=true`, `denialCode` omitted
  - `FROZEN|CLOSED` => `orderEligible=false`, `denialCode="ORD-012"`

### 3.2 Transition
- Method/Path: `PATCH /internal/v1/accounts/{accountId}/status`
- Required headers:
  - `X-Internal-Secret`
  - `X-Correlation-Id`
- Request body:
```json
{
  "memberId": 301,
  "status": "FROZEN",
  "reason": "risk-control",
  "actor": "ops-admin",
  "context": "ticket=FIX-43"
}
```
- Response fields:
  - `accountId`, `memberId`, `previousStatus`, `newStatus`, `changed`, `eventId`, `reason`, `actor`, `context`, `asOf`
- Transition semantics:
  - status changed: `changed=true`, `eventId` populated
  - same-status no-op: `changed=false`, `eventId` omitted

## 4. Channel admin API
- Method/Path: `PATCH /api/v1/admin/accounts/{accountId}/status`
- Auth: authenticated session + CSRF
- Payload/response contract mirrors corebank transition schema.
- Channel forwards `X-Correlation-Id` and preserves known machine codes from corebank.

## 5. Order eligibility invariant
- Order create path must check current account status before FEP submission.
- `FROZEN|CLOSED` always reject with:
  - HTTP `422`
  - code `ORD-012`
  - no external exchange call

## 6. Persistence contract
- Migration `V5__add_account_status_events.sql` creates `account_status_events`.
- Event row contains:
  - `account_id`, `member_id`, `previous_status`, `new_status`, `reason`, `actor`, `context`, `correlation_id`
- Indexes:
  - `(account_id, created_at)`
  - `(member_id, created_at)`
