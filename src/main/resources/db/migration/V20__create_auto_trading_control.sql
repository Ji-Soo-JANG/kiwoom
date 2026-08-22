CREATE TABLE auto_trading_control (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    paper_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    paper_strategy VARCHAR(80) NOT NULL DEFAULT 'drop-base-breakout-pullback-v1',
    live_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    live_strategy VARCHAR(80) NOT NULL DEFAULT 'drop-base-breakout-pullback-v1',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO auto_trading_control(id) VALUES (1);
