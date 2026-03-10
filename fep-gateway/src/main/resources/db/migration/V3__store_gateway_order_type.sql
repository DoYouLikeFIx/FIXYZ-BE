ALTER TABLE gateway_orders ADD COLUMN order_type VARCHAR(16) NOT NULL DEFAULT 'LIMIT';
ALTER TABLE gateway_orders ADD COLUMN requested_price BIGINT NULL;
