ALTER TABLE order_sessions
    ADD COLUMN challenge_required BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE order_sessions
    ADD COLUMN authorization_reason VARCHAR(64) NOT NULL DEFAULT 'STEP_UP_REQUIRED';

-- Legacy sessions that already passed authorization must not retain step-up defaults.
-- EXPIRED rows are only backfilled when their version shows they advanced beyond initial creation.
UPDATE order_sessions
SET challenge_required = FALSE,
    authorization_reason = 'OTP_VERIFIED'
WHERE status IN ('AUTHED', 'EXECUTING', 'REQUERYING', 'ESCALATED', 'COMPLETED', 'FAILED', 'CANCELED')
   OR (status = 'EXPIRED' AND COALESCE(version, 0) > 1);
