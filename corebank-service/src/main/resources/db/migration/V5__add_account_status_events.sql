CREATE TABLE IF NOT EXISTS account_status_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  account_id BIGINT NOT NULL,
  member_id BIGINT NOT NULL,
  previous_status VARCHAR(16) NOT NULL,
  new_status VARCHAR(16) NOT NULL,
  reason VARCHAR(255) NOT NULL,
  actor VARCHAR(64) NOT NULL,
  context VARCHAR(255) NULL,
  correlation_id VARCHAR(64) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT
);

CREATE INDEX idx_account_status_events_account_id_created_at ON account_status_events(account_id, created_at);
CREATE INDEX idx_account_status_events_member_id_created_at ON account_status_events(member_id, created_at);
