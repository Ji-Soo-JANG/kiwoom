CREATE TABLE walk_forward_report (
    id BIGSERIAL PRIMARY KEY,
    strategy_version VARCHAR(100) NOT NULL,
    code VARCHAR(6) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    training_days INTEGER NOT NULL,
    validation_days INTEGER NOT NULL,
    step_days INTEGER NOT NULL,
    fold_count INTEGER NOT NULL,
    validation_trade_count INTEGER NOT NULL,
    cost_adjusted_expectancy NUMERIC(20, 4) NOT NULL,
    max_drawdown_rate DOUBLE PRECISION NOT NULL,
    average_return_rate DOUBLE PRECISION NOT NULL,
    cost_drag NUMERIC(20, 4) NOT NULL,
    passed BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE walk_forward_fold (
    report_id BIGINT NOT NULL REFERENCES walk_forward_report(id) ON DELETE CASCADE,
    fold_no INTEGER NOT NULL,
    training_start DATE NOT NULL,
    training_end DATE NOT NULL,
    validation_start DATE NOT NULL,
    validation_end DATE NOT NULL,
    training_trade_count INTEGER NOT NULL,
    training_return_rate DOUBLE PRECISION NOT NULL,
    validation_trade_count INTEGER NOT NULL,
    validation_win_rate DOUBLE PRECISION NOT NULL,
    validation_expectancy NUMERIC(20, 4) NOT NULL,
    validation_return_rate DOUBLE PRECISION NOT NULL,
    validation_max_drawdown_rate DOUBLE PRECISION NOT NULL,
    cost_drag NUMERIC(20, 4) NOT NULL,
    PRIMARY KEY (report_id, fold_no)
);
