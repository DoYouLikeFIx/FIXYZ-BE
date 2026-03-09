ALTER TABLE gateway_order_cancels MODIFY COLUMN cancel_cl_ord_id VARCHAR(64) NULL;
ALTER TABLE gateway_order_replays MODIFY COLUMN manual_decision VARCHAR(16) NULL;
ALTER TABLE gateway_order_replays MODIFY COLUMN operator_id VARCHAR(64) NULL;
ALTER TABLE gateway_order_replays MODIFY COLUMN approved_by VARCHAR(64) NULL;
ALTER TABLE gateway_order_replays MODIFY COLUMN evidence_ref VARCHAR(255) NULL;

UPDATE gateway_order_cancels
SET cancel_cl_ord_id = NULL
WHERE cancel_cl_ord_id LIKE 'legacy-cancel-%';

UPDATE gateway_order_replays
SET manual_decision = NULL,
    operator_id = NULL,
    approved_by = NULL,
    evidence_ref = NULL
WHERE evidence_ref = 'LEGACY-MIGRATION'
  AND operator_id LIKE 'legacy-operator-%'
  AND approved_by LIKE 'legacy-approver-%';
