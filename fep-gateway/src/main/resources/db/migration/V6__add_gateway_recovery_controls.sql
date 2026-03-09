ALTER TABLE gateway_orders ADD COLUMN recovery_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE gateway_orders ADD COLUMN cancel_failure_mode VARCHAR(32) NOT NULL DEFAULT 'NONE';
ALTER TABLE gateway_orders ADD COLUMN requery_ord_status VARCHAR(32) NULL;
ALTER TABLE gateway_orders ADD COLUMN requery_executed_qty BIGINT NULL;
ALTER TABLE gateway_orders ADD COLUMN requery_executed_price BIGINT NULL;

UPDATE gateway_orders
SET requested_price = executed_price
WHERE order_type = 'MARKET'
  AND requested_price IS NULL
  AND executed_price IS NOT NULL;
