CREATE TABLE IF NOT EXISTS ledger_reconciliation_repairs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  case_id BIGINT NOT NULL,
  repair_key VARCHAR(64) NOT NULL,
  repair_type VARCHAR(64) NOT NULL,
  outcome VARCHAR(16) NOT NULL,
  mutated BOOLEAN NOT NULL,
  reason VARCHAR(255) NOT NULL,
  actor VARCHAR(64) NOT NULL,
  context VARCHAR(255),
  correlation_id VARCHAR(64),
  summary_message VARCHAR(500) NOT NULL,
  rerun_run_id BIGINT,
  rerun_case_status VARCHAR(32),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT
);

CREATE UNIQUE INDEX uk_ledger_reconciliation_repairs_case_key
  ON ledger_reconciliation_repairs(case_id, repair_key);

CREATE INDEX idx_ledger_reconciliation_repairs_case_id
  ON ledger_reconciliation_repairs(case_id);

CREATE INDEX idx_ledger_reconciliation_repairs_type
  ON ledger_reconciliation_repairs(repair_type);
