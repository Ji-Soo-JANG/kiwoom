CREATE TABLE trading_order (
    id BIGSERIAL PRIMARY KEY,
    decision_id VARCHAR(100) NOT NULL UNIQUE,
    mode VARCHAR(20) NOT NULL,
    code VARCHAR(6) NOT NULL,
    side VARCHAR(10) NOT NULL,
    requested_quantity BIGINT NOT NULL CHECK (requested_quantity > 0),
    requested_price NUMERIC(20, 4) NOT NULL CHECK (requested_price > 0),
    status VARCHAR(30) NOT NULL,
    filled_quantity BIGINT NOT NULL DEFAULT 0,
    average_fill_price NUMERIC(20, 4),
    rejection_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE trading_order_event (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES trading_order(id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    detail VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE trading_fill (
    id BIGSERIAL PRIMARY KEY,
    execution_id VARCHAR(100) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL REFERENCES trading_order(id) ON DELETE CASCADE,
    code VARCHAR(6) NOT NULL,
    side VARCHAR(10) NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    price NUMERIC(20, 4) NOT NULL CHECK (price > 0),
    fee NUMERIC(20, 4) NOT NULL DEFAULT 0,
    tax NUMERIC(20, 4) NOT NULL DEFAULT 0,
    filled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE paper_account (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    initial_cash NUMERIC(20, 4) NOT NULL,
    cash NUMERIC(20, 4) NOT NULL,
    peak_equity NUMERIC(20, 4) NOT NULL,
    trading_day DATE NOT NULL,
    day_start_equity NUMERIC(20, 4) NOT NULL,
    kill_switch_active BOOLEAN NOT NULL DEFAULT FALSE,
    kill_switch_reason VARCHAR(500),
    kill_switch_activated_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE paper_position (
    code VARCHAR(6) PRIMARY KEY,
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    average_price NUMERIC(20, 4) NOT NULL CHECK (average_price > 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trading_order_status ON trading_order(status, updated_at);
CREATE INDEX idx_trading_fill_order ON trading_fill(order_id);
