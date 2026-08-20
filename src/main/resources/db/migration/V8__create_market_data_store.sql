CREATE TABLE stock_master (
    code VARCHAR(6) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    market VARCHAR(20) NOT NULL,
    product_type VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT stock_master_code_format CHECK (code ~ '^[0-9]{6}$')
);

CREATE TABLE daily_candle (
    code VARCHAR(6) NOT NULL REFERENCES stock_master(code),
    trade_date DATE NOT NULL,
    open_price BIGINT NOT NULL,
    high_price BIGINT NOT NULL,
    low_price BIGINT NOT NULL,
    close_price BIGINT NOT NULL,
    volume BIGINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (code, trade_date)
);

CREATE INDEX idx_daily_candle_trade_date ON daily_candle(trade_date);

CREATE TABLE market_data_sync_state (
    code VARCHAR(6) PRIMARY KEY REFERENCES stock_master(code),
    last_synced_date DATE,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(500),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_market_data_sync_status ON market_data_sync_state(status, updated_at);
