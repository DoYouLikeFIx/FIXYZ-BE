ALTER TABLE executions
    ADD COLUMN execution_seq INT NULL;

CREATE TEMPORARY TABLE tmp_execution_sequence_backfill_v13 AS
SELECT ranked.id,
       ROW_NUMBER() OVER (
           PARTITION BY ranked.order_id
           ORDER BY ranked.executed_at, ranked.id
       ) AS execution_seq
FROM executions ranked;

UPDATE executions
SET execution_seq = (
    SELECT seq.execution_seq
    FROM tmp_execution_sequence_backfill_v13 seq
    WHERE seq.id = executions.id
)
WHERE execution_seq IS NULL;

DROP TABLE IF EXISTS tmp_execution_sequence_backfill_v13;

ALTER TABLE executions
    MODIFY COLUMN execution_seq INT NOT NULL DEFAULT 1;

CREATE INDEX idx_executions_order_execution_seq ON executions(order_id, execution_seq);
