# Account Provisioning Error Mapping v1 (Story 2.1)

Status: LOCKED
Owner: BE corebank-service + BE channel-service
Scope: Story 2.1 provisioning path only

## 1. Mapping table

| Scenario | Service boundary | HTTP | Code | Notes |
|---|---|---:|---|---|
| Missing/invalid `X-Internal-Secret` | corebank internal API | 401 | CORE-9401 | Filter-level rejection |
| Missing required payload field | corebank internal API | 422 | VALIDATION-001 | Contract validation failure |
| First provisioning success | corebank internal API | 201 | SUCCESS | `idempotent=false` |
| Duplicate provisioning request (same `memberId`) | corebank internal API | 200 | SUCCESS | `idempotent=true`, no new row |
| Concurrent duplicate race resolved by unique constraint | corebank internal API | 200 | SUCCESS | Deterministic existing account return |
| Provisioning transaction rollback | corebank internal API | 500 | CORE-004 | No partial write allowed |
| Channel signup -> corebank provisioning failure | channel external API | 503 | CORE-001 | Signup success must not be returned |

## 2. Normalization policy
- Corebank returns normalized machine code and message in standard envelope.
- Channel maps provisioning upstream failures to `CORE-001` for client-facing signup failure.
- Duplicate provisioning is not an error; it is a successful idempotent response.

## 3. Legacy compatibility note
- Existing underscore code variants may still exist in legacy paths.
- Story 2.1 contract uses hyphenated external code style for new/updated paths.

## 4. Retry policy
- Client retry for provisioning is allowed.
- Server-side must remain safe under at-least-once invocation.
- Retry outcome for same `memberId` must be deterministic (single-account outcome).

## 5. AC linkage
- AC3 and AC4 are enforced by this mapping:
  - AC3 via success+idempotent duplicate handling.
  - AC4 via rollback + `CORE-004` normalized failure contract.
