CREATE TABLE intraday_price_event (
    id BIGSERIAL PRIMARY KEY,
    source_event_id VARCHAR(120) NOT NULL UNIQUE,
    code VARCHAR(6) NOT NULL,
    event_time TIMESTAMP WITH TIME ZONE NOT NULL,
    price BIGINT NOT NULL CHECK (price > 0),
    volume BIGINT NOT NULL CHECK (volume >= 0),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_intraday_event_replay ON intraday_price_event(code, event_time, id);
