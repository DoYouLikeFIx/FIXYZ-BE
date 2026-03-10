ALTER TABLE gateway_orders ADD COLUMN status_message VARCHAR(255) NULL;
ALTER TABLE gateway_orders ADD COLUMN reject_reason VARCHAR(64) NULL;
ALTER TABLE gateway_orders ADD COLUMN parse_error VARCHAR(255) NULL;
