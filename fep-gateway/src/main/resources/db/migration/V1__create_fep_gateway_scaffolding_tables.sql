CREATE TABLE gateway_orders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    cl_ord_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(8) NOT NULL,
    qty DECIMAL(19, 4) NOT NULL,
    status VARCHAR(32) NOT NULL,
    transport VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL,
    UNIQUE KEY uk_gateway_orders_cl_ord_id (cl_ord_id)
);

CREATE TABLE gateway_order_cancels (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    cl_ord_id VARCHAR(64) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL
);

CREATE TABLE gateway_order_replays (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    cl_ord_id VARCHAR(64) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL
);

CREATE TABLE gateway_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL,
    counterparty VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL,
    UNIQUE KEY uk_gateway_sessions_session_id (session_id)
);

CREATE INDEX idx_gateway_order_cancels_cl_ord_id ON gateway_order_cancels (cl_ord_id);
CREATE INDEX idx_gateway_order_replays_cl_ord_id ON gateway_order_replays (cl_ord_id);
