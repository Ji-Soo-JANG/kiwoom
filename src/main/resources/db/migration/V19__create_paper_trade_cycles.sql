CREATE TABLE paper_trade_cycle (
    id BIGSERIAL PRIMARY KEY,
    entry_candidate_id BIGINT NOT NULL UNIQUE REFERENCES limited_trade_candidate(id),
    code VARCHAR(6) NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    entry_order_id BIGINT NOT NULL UNIQUE REFERENCES trading_order(id),
    entry_price NUMERIC(20, 4) NOT NULL CHECK (entry_price > 0),
    stop_loss_price NUMERIC(20, 4) NOT NULL CHECK (stop_loss_price > 0),
    take_profit_price NUMERIC(20, 4) NOT NULL CHECK (take_profit_price > 0),
    max_holding_days INTEGER NOT NULL CHECK (max_holding_days > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'HOLDING',
    exit_reason VARCHAR(30),
    exit_trigger_price NUMERIC(20, 4),
    exit_order_id BIGINT UNIQUE REFERENCES trading_order(id),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    exit_requested_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE paper_trade_result (
    id BIGSERIAL PRIMARY KEY,
    cycle_id BIGINT NOT NULL UNIQUE REFERENCES paper_trade_cycle(id),
    gross_pnl NUMERIC(20, 4) NOT NULL,
    total_cost NUMERIC(20, 4) NOT NULL,
    net_pnl NUMERIC(20, 4) NOT NULL,
    net_return_rate NUMERIC(12, 8) NOT NULL,
    holding_days INTEGER NOT NULL,
    exit_reason VARCHAR(30) NOT NULL,
    closed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_paper_trade_cycle_status_code ON paper_trade_cycle(status, code);
CREATE INDEX idx_paper_trade_result_closed ON paper_trade_result(closed_at);
