CREATE TABLE signal_observation (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    strategy_version VARCHAR(50) NOT NULL,
    minimum_trading_days INTEGER NOT NULL DEFAULT 20 CHECK (minimum_trading_days >= 20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE signal_observation_sample (
    id BIGSERIAL PRIMARY KEY,
    observation_id BIGINT NOT NULL REFERENCES signal_observation(id) ON DELETE CASCADE,
    trading_day DATE NOT NULL,
    code VARCHAR(6) NOT NULL,
    backtest_signal BOOLEAN NOT NULL,
    realtime_signal BOOLEAN NOT NULL,
    expected_price BIGINT,
    observed_price BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_observation_sample UNIQUE (observation_id, trading_day, code)
);
