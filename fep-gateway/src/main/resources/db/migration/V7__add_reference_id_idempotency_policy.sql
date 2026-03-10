ALTER TABLE gateway_orders ADD COLUMN account_id VARCHAR(64) NULL;
ALTER TABLE gateway_orders ADD COLUMN reference_id VARCHAR(128) NULL;
ALTER TABLE gateway_orders ADD COLUMN reference_id_expires_at DATETIME(6) NULL;

UPDATE gateway_orders
SET account_id = 'LEGACY',
    reference_id = CONCAT('LEGACY-', cl_ord_id),
    reference_id_expires_at = created_at
WHERE account_id IS NULL
   OR reference_id IS NULL
   OR reference_id_expires_at IS NULL;

ALTER TABLE gateway_orders MODIFY account_id VARCHAR(64) NOT NULL;
ALTER TABLE gateway_orders MODIFY reference_id VARCHAR(128) NOT NULL;
ALTER TABLE gateway_orders MODIFY reference_id_expires_at DATETIME(6) NOT NULL;

ALTER TABLE gateway_orders
    ADD CONSTRAINT uk_gateway_orders_reference_id UNIQUE (reference_id);

CREATE TABLE gateway_security_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    reference_id VARCHAR(128) NOT NULL,
    owner_account_id VARCHAR(64) NULL,
    attempted_account_id VARCHAR(64) NULL,
    owner_cl_ord_id VARCHAR(64) NULL,
    attempted_cl_ord_id VARCHAR(64) NULL,
    correlation_id VARCHAR(64) NOT NULL,
    detail VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL
);

CREATE INDEX idx_gateway_security_events_reference_id
    ON gateway_security_events (reference_id, created_at);
