CREATE TABLE simulator_connections (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    connection_key VARCHAR(64) NOT NULL,
    endpoint VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL,
    UNIQUE KEY uk_simulator_connections_connection_key (connection_key)
);

CREATE TABLE simulator_messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    connection_id BIGINT NULL,
    message_type VARCHAR(32) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    payload TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL
);

CREATE TABLE simulator_rules (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rule_code VARCHAR(64) NOT NULL,
    rule_action VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL,
    UNIQUE KEY uk_simulator_rules_rule_code (rule_code)
);

CREATE INDEX idx_simulator_messages_connection_id ON simulator_messages (connection_id);
