ALTER TABLE members
  ADD COLUMN password_changed_at TIMESTAMP(3) NULL;

UPDATE members
SET password_changed_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP(3))
WHERE password_changed_at IS NULL;

ALTER TABLE members
  MODIFY COLUMN password_changed_at TIMESTAMP(3) NOT NULL;

CREATE INDEX idx_members_password_changed_at ON members(password_changed_at);

CREATE TABLE password_reset_tokens (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL,
  pepper_version SMALLINT NOT NULL,
  active_slot TINYINT NULL,
  issued_at TIMESTAMP(6) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  consumed_at TIMESTAMP(6) NULL,
  request_ip VARCHAR(45) NULL,
  request_user_agent_hash CHAR(64) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NULL,
  CONSTRAINT fk_password_reset_tokens_member FOREIGN KEY (member_id) REFERENCES members(id),
  CONSTRAINT uk_password_reset_tokens_token_hash UNIQUE (token_hash),
  CONSTRAINT uk_password_reset_tokens_member_active_slot UNIQUE (member_id, active_slot),
  CONSTRAINT chk_password_reset_tokens_active_slot CHECK (active_slot IS NULL OR active_slot = 1)
);

CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens(expires_at);
CREATE INDEX idx_password_reset_tokens_consumed_at ON password_reset_tokens(consumed_at);
CREATE INDEX idx_password_reset_tokens_member_active_slot ON password_reset_tokens(member_id, active_slot);
