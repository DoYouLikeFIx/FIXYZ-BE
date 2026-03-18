CREATE TABLE IF NOT EXISTS ledger_integrity_runs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  checked_at TIMESTAMP NOT NULL,
  passed BOOLEAN NOT NULL,
  anomaly_count INT NOT NULL,
  summary_message VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT
);

CREATE TABLE IF NOT EXISTS ledger_integrity_anomalies (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  run_id BIGINT NOT NULL,
  type VARCHAR(64) NOT NULL,
  message VARCHAR(500) NOT NULL,
  account_id BIGINT,
  symbol VARCHAR(32),
  position_id BIGINT,
  execution_id BIGINT,
  order_id BIGINT,
  cl_ord_id VARCHAR(64),
  journal_entry_id BIGINT,
  ledger_entry_id BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT
);

CREATE INDEX idx_ledger_integrity_runs_checked_at
  ON ledger_integrity_runs(checked_at);

CREATE INDEX idx_ledger_integrity_anomalies_run_id
  ON ledger_integrity_anomalies(run_id);

CREATE INDEX idx_ledger_integrity_anomalies_type
  ON ledger_integrity_anomalies(type);
