ALTER TABLE executions
    ADD COLUMN execution_seq INT NULL;

UPDATE executions
SET execution_seq = (
    SELECT COUNT(*)
    FROM executions prior
    WHERE prior.order_id = executions.order_id
      AND (
          prior.executed_at < executions.executed_at
          OR (prior.executed_at = executions.executed_at AND prior.id <= executions.id)
      )
)
WHERE execution_seq IS NULL;

ALTER TABLE executions
    MODIFY COLUMN execution_seq INT NOT NULL DEFAULT 1;

CREATE INDEX idx_executions_order_execution_seq ON executions(order_id, execution_seq);
