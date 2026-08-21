CREATE TABLE backtest_run (
    id BIGSERIAL PRIMARY KEY,
    strategy_version VARCHAR(100) NOT NULL,
    code VARCHAR(6) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    initial_capital NUMERIC(20, 4) NOT NULL,
    final_capital NUMERIC(20, 4) NOT NULL,
    fee_rate DOUBLE PRECISION NOT NULL,
    tax_rate DOUBLE PRECISION NOT NULL,
    slippage_rate DOUBLE PRECISION NOT NULL,
    trade_count INTEGER NOT NULL,
    win_rate DOUBLE PRECISION NOT NULL,
    total_return_rate DOUBLE PRECISION NOT NULL,
    max_drawdown_rate DOUBLE PRECISION NOT NULL,
    expectancy NUMERIC(20, 4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE backtest_trade (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES backtest_run(id) ON DELETE CASCADE,
    entry_date DATE NOT NULL,
    exit_date DATE NOT NULL,
    entry_price NUMERIC(20, 4) NOT NULL,
    exit_price NUMERIC(20, 4) NOT NULL,
    quantity BIGINT NOT NULL,
    gross_profit_loss NUMERIC(20, 4) NOT NULL,
    fee NUMERIC(20, 4) NOT NULL,
    tax NUMERIC(20, 4) NOT NULL,
    slippage_cost NUMERIC(20, 4) NOT NULL,
    net_profit_loss NUMERIC(20, 4) NOT NULL,
    return_rate DOUBLE PRECISION NOT NULL,
    exit_reason VARCHAR(30) NOT NULL
);

CREATE INDEX idx_backtest_trade_run ON backtest_trade(run_id, entry_date);
