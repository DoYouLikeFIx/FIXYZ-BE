CREATE TEMPORARY TABLE tmp_execution_sequence_backfill_v14 AS
SELECT ranked.id,
       ROW_NUMBER() OVER (
           PARTITION BY ranked.order_id
           ORDER BY ranked.executed_at, ranked.id
       ) AS execution_seq
FROM executions ranked;

UPDATE executions
SET execution_seq = (
    SELECT seq.execution_seq
    FROM tmp_execution_sequence_backfill_v14 seq
    WHERE seq.id = executions.id
);

DROP TABLE IF EXISTS tmp_execution_sequence_backfill_v14;

DROP INDEX idx_executions_order_execution_seq ON executions;

ALTER TABLE executions
    ADD CONSTRAINT uk_executions_order_execution_seq UNIQUE (order_id, execution_seq);
