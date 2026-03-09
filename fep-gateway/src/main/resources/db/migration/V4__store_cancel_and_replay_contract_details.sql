ALTER TABLE gateway_order_cancels ADD COLUMN cancel_cl_ord_id VARCHAR(64) NULL;
ALTER TABLE gateway_order_cancels ADD COLUMN canceled_qty BIGINT NULL;
ALTER TABLE gateway_order_cancels ADD COLUMN executed_qty BIGINT NULL;
ALTER TABLE gateway_order_cancels ADD COLUMN executed_price BIGINT NULL;
ALTER TABLE gateway_order_cancels ADD COLUMN executed_at DATETIME(6) NULL;
ALTER TABLE gateway_order_cancels ADD COLUMN canceled_at DATETIME(6) NULL;

UPDATE gateway_order_cancels
SET cancel_cl_ord_id = CONCAT('legacy-cancel-', id)
WHERE cancel_cl_ord_id IS NULL;

ALTER TABLE gateway_order_cancels MODIFY COLUMN cancel_cl_ord_id VARCHAR(64) NOT NULL;
ALTER TABLE gateway_order_cancels ADD CONSTRAINT uk_gateway_order_cancels_cancel_cl_ord_id UNIQUE (cancel_cl_ord_id);

ALTER TABLE gateway_order_replays ADD COLUMN manual_decision VARCHAR(16) NULL;
ALTER TABLE gateway_order_replays ADD COLUMN operator_id VARCHAR(64) NULL;
ALTER TABLE gateway_order_replays ADD COLUMN approved_by VARCHAR(64) NULL;
ALTER TABLE gateway_order_replays ADD COLUMN evidence_ref VARCHAR(255) NULL;
ALTER TABLE gateway_order_replays ADD COLUMN execution_price BIGINT NULL;
ALTER TABLE gateway_order_replays ADD COLUMN execution_source VARCHAR(32) NULL;
ALTER TABLE gateway_order_replays ADD COLUMN execution_result VARCHAR(32) NULL;
ALTER TABLE gateway_order_replays ADD COLUMN processed_at DATETIME(6) NULL;

UPDATE gateway_order_replays
SET manual_decision = 'APPROVE',
    operator_id = CONCAT('legacy-operator-', id),
    approved_by = CONCAT('legacy-approver-', id),
    evidence_ref = 'LEGACY-MIGRATION'
WHERE manual_decision IS NULL;

ALTER TABLE gateway_order_replays MODIFY COLUMN manual_decision VARCHAR(16) NOT NULL;
ALTER TABLE gateway_order_replays MODIFY COLUMN operator_id VARCHAR(64) NOT NULL;
ALTER TABLE gateway_order_replays MODIFY COLUMN approved_by VARCHAR(64) NOT NULL;
ALTER TABLE gateway_order_replays MODIFY COLUMN evidence_ref VARCHAR(255) NOT NULL;
