ALTER TABLE gateway_orders ADD COLUMN fep_order_id VARCHAR(96) NULL;
ALTER TABLE gateway_orders ADD COLUMN exec_type VARCHAR(32) NULL;
ALTER TABLE gateway_orders ADD COLUMN executed_qty BIGINT NULL;
ALTER TABLE gateway_orders ADD COLUMN executed_price BIGINT NULL;
ALTER TABLE gateway_orders ADD COLUMN leaves_qty BIGINT NULL;
ALTER TABLE gateway_orders ADD COLUMN transact_time DATETIME(6) NULL;
