ALTER TABLE members
  ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;

ALTER TABLE members
  ADD COLUMN locked_at TIMESTAMP NULL;
