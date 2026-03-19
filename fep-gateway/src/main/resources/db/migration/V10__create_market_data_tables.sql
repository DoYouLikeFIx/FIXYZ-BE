CREATE TABLE fep_market_data_subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subscription_id VARCHAR(36) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    symbol VARCHAR(16) NOT NULL,
    source_mode VARCHAR(16) NOT NULL,
    tr_id VARCHAR(32) NULL,
    tr_key VARCHAR(32) NULL,
    last_event_offset BIGINT NULL,
    last_quote_as_of DATETIME(6) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL,
    CONSTRAINT uk_fep_market_data_subscriptions_subscription_id UNIQUE (subscription_id),
    CONSTRAINT uk_fep_market_data_subscriptions_provider_symbol_source_mode UNIQUE (provider, symbol, source_mode)
);

CREATE INDEX idx_fep_market_data_subscriptions_active_updated_at
    ON fep_market_data_subscriptions (is_active, updated_at);

CREATE TABLE fep_quote_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    quote_snapshot_id VARCHAR(128) NOT NULL,
    symbol VARCHAR(16) NOT NULL,
    source_mode VARCHAR(16) NOT NULL,
    quote_as_of DATETIME(6) NOT NULL,
    best_bid BIGINT NULL,
    best_ask BIGINT NULL,
    last_trade BIGINT NULL,
    stream_offset BIGINT NOT NULL,
    is_stale BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL,
    CONSTRAINT uk_fep_quote_snapshots_quote_snapshot_id UNIQUE (quote_snapshot_id)
);

CREATE INDEX idx_fep_quote_snapshots_symbol_quote_as_of
    ON fep_quote_snapshots (symbol, quote_as_of);

CREATE INDEX idx_fep_quote_snapshots_source_mode_quote_as_of
    ON fep_quote_snapshots (source_mode, quote_as_of);

CREATE TABLE fep_replay_cursors (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    replay_id VARCHAR(36) NOT NULL,
    seed VARCHAR(128) NOT NULL,
    symbol VARCHAR(16) NOT NULL,
    cursor_offset BIGINT NOT NULL DEFAULT 0,
    speed_factor DECIMAL(10, 4) NOT NULL DEFAULT 1.0000,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL,
    CONSTRAINT uk_fep_replay_cursors_replay_id UNIQUE (replay_id)
);

CREATE INDEX idx_fep_replay_cursors_symbol_status_updated_at
    ON fep_replay_cursors (symbol, status, updated_at);
