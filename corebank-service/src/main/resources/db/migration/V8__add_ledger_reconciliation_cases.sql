CREATE TABLE IF NOT EXISTS ledger_reconciliation_cases (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  anomaly_id BIGINT NOT NULL,
  run_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  anomaly_type VARCHAR(64) NOT NULL,
  summary_message VARCHAR(500) NOT NULL,
  account_id BIGINT,
  symbol VARCHAR(32),
  position_id BIGINT,
  execution_id BIGINT,
  order_id BIGINT,
  cl_ord_id VARCHAR(64),
  journal_entry_id BIGINT,
  ledger_entry_id BIGINT,
  last_transition_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT
);

CREATE TABLE IF NOT EXISTS ledger_reconciliation_case_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  case_id BIGINT NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  previous_status VARCHAR(32),
  new_status VARCHAR(32) NOT NULL,
  reason VARCHAR(255) NOT NULL,
  actor VARCHAR(64) NOT NULL,
  context VARCHAR(255),
  correlation_id VARCHAR(64),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT
);

CREATE INDEX idx_ledger_reconciliation_cases_anomaly_id
  ON ledger_reconciliation_cases(anomaly_id);

CREATE INDEX idx_ledger_reconciliation_cases_status
  ON ledger_reconciliation_cases(status);

CREATE INDEX idx_ledger_reconciliation_cases_run_id
  ON ledger_reconciliation_cases(run_id);

CREATE INDEX idx_ledger_reconciliation_case_events_case_id
  ON ledger_reconciliation_case_events(case_id);

CREATE INDEX idx_ledger_reconciliation_case_events_correlation_id
  ON ledger_reconciliation_case_events(correlation_id);
