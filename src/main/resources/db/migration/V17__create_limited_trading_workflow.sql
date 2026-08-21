CREATE TABLE limited_trade_candidate (
    id BIGSERIAL PRIMARY KEY,
    signal_id VARCHAR(100) NOT NULL UNIQUE,
    code VARCHAR(6) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    reference_price NUMERIC(20, 4) NOT NULL CHECK (reference_price > 0),
    suggested_quantity BIGINT NOT NULL CHECK (suggested_quantity > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMPTZ NOT NULL,
    approved_by VARCHAR(100),
    approved_at TIMESTAMPTZ,
    order_id BIGINT REFERENCES trading_order(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE trading_performance_sample (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT REFERENCES trading_order(id),
    code VARCHAR(6) NOT NULL,
    expected_price NUMERIC(20, 4) NOT NULL CHECK (expected_price > 0),
    actual_price NUMERIC(20, 4) NOT NULL CHECK (actual_price > 0),
    net_return_rate NUMERIC(12, 8) NOT NULL,
    slippage_rate NUMERIC(12, 8) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_limited_candidate_status ON limited_trade_candidate(status, expires_at);
CREATE INDEX idx_trading_performance_recorded ON trading_performance_sample(recorded_at);
