ALTER TABLE password_reset_tokens
  ADD COLUMN terminal_reason VARCHAR(16) NULL;

ALTER TABLE password_reset_tokens
  ADD COLUMN terminalized_at TIMESTAMP(6) NULL;

UPDATE password_reset_tokens
SET terminal_reason = 'CONSUMED',
    terminalized_at = consumed_at
WHERE active_slot IS NULL
  AND consumed_at IS NOT NULL
  AND terminal_reason IS NULL
  AND terminalized_at IS NULL;

UPDATE password_reset_tokens
SET terminal_reason = 'SUPERSEDED',
    terminalized_at = COALESCE(updated_at, expires_at, created_at)
WHERE active_slot IS NULL
  AND consumed_at IS NULL
  AND terminal_reason IS NULL
  AND terminalized_at IS NULL;

ALTER TABLE password_reset_tokens
  ADD CONSTRAINT chk_password_reset_tokens_terminal_reason
  CHECK (terminal_reason IS NULL OR terminal_reason IN ('CONSUMED', 'SUPERSEDED', 'EXPIRED'));

CREATE INDEX idx_password_reset_tokens_active_expires_at
  ON password_reset_tokens(active_slot, expires_at);

CREATE INDEX idx_password_reset_tokens_terminalized_expires_at
  ON password_reset_tokens(terminalized_at, expires_at);
