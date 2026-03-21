ALTER TABLE orders MODIFY COLUMN order_price DECIMAL(19, 4) NULL;

ALTER TABLE orders ADD COLUMN order_type VARCHAR(16) NOT NULL DEFAULT 'LIMIT';
ALTER TABLE orders ADD COLUMN pre_trade_price DECIMAL(19, 4) NULL;
ALTER TABLE orders ADD COLUMN quote_snapshot_id VARCHAR(64) NULL;
ALTER TABLE orders ADD COLUMN quote_as_of TIMESTAMP NULL;
ALTER TABLE orders ADD COLUMN quote_source_mode VARCHAR(16) NULL;

CREATE INDEX idx_orders_quote_snapshot_id ON orders(quote_snapshot_id);

ALTER TABLE executions ADD COLUMN quote_snapshot_id VARCHAR(64) NULL;
ALTER TABLE executions ADD COLUMN quote_as_of TIMESTAMP NULL;
ALTER TABLE executions ADD COLUMN quote_source_mode VARCHAR(16) NULL;

CREATE INDEX idx_executions_quote_snapshot_id ON executions(quote_snapshot_id);
