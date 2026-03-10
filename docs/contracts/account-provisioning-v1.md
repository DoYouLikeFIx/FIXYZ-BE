# Account Provisioning Contract v1 (Story 2.1)

Status: LOCKED
Owner: BE corebank-service
Scope: Story 2.1 write path only

## 1. Source of truth
- Canonical: `2-1-schema-and-auto-account-provisioning.md`
- Canonical: `epics.md` (Epic 2 / Story 2.1)
- Supplemental only: `epic-2-order-session-and-otp.md`

## 2. Scope boundary
- In scope (2.1): default account provisioning write flow.
- Out of scope (2.1): portfolio/balance read contract and GET endpoint redesign.
- Read-side contract moves to Story 2.2.

## 3. Endpoint
- Method/Path: `POST /internal/v1/portfolio`
- Content-Type: `application/json` (fixed)
- Auth boundary: internal-only; header guard required.

## 4. Required headers
- `X-Internal-Secret`: required, exact match with runtime secret.
- `X-Correlation-Id`: required; UUID recommended.

## 5. Request contract
```json
{
  "memberId": 123,
  "memberNo": "M-ABC123DEF456",
  "email": "member@example.com"
}
```
- `memberId`: required, owner identity key for idempotency.
- `memberNo`: optional, persisted if present.
- `email`: optional, persisted if present.

## 6. Response contract
### 6.1 Created (first provisioning)
- HTTP `201`
```json
{
  "accountId": 1001,
  "accountNumber": "110123456789",
  "status": "ACTIVE",
  "idempotent": false,
  "memberId": 123,
  "createdAt": "2026-03-10T10:00:00Z"
}
```

### 6.2 Duplicate/idempotent provisioning
- HTTP `200`
```json
{
  "accountId": 1001,
  "accountNumber": "110123456789",
  "status": "ACTIVE",
  "idempotent": true,
  "memberId": 123,
  "createdAt": "2026-03-10T10:00:00Z"
}
```

## 7. Duplicate semantics (fixed)
- Same `memberId` called again must never create a second account row.
- Duplicate request returns existing account deterministically.
- API-level semantics:
  - `201` + `idempotent=false`: new row created.
  - `200` + `idempotent=true`: existing row returned.

## 8. AC mapping
- AC1: migration + constraints exist at boot.
- AC2: successful registration event path creates default account.
- AC3: duplicate request stays single-account outcome.
- AC4: failure rolls back and returns normalized code.

## 9. Non-goals in this story
- No read-side expansion.
- No `GET /internal/v1/portfolio` rename/split in this commit line.
