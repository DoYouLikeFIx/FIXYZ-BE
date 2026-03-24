UPDATE executions
SET execution_seq = (
    SELECT COUNT(*)
    FROM executions prior
    WHERE prior.order_id = executions.order_id
      AND (
          prior.executed_at < executions.executed_at
          OR (prior.executed_at = executions.executed_at AND prior.id <= executions.id)
      )
);

DROP INDEX idx_executions_order_execution_seq ON executions;

ALTER TABLE executions
    ADD CONSTRAINT uk_executions_order_execution_seq UNIQUE (order_id, execution_seq);
