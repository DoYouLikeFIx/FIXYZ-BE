ALTER TABLE audit_logs
  ADD COLUMN ip_address VARCHAR(64);

ALTER TABLE audit_logs
  ADD COLUMN user_agent VARCHAR(255);

ALTER TABLE audit_logs
  ADD COLUMN correlation_id VARCHAR(128);
