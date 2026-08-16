CREATE TABLE watchlist (
    code VARCHAR(6) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT watchlist_code_format CHECK (code ~ '^[0-9]{6}$')
);

CREATE TABLE portfolio_position (
    code VARCHAR(6) PRIMARY KEY,
    quantity NUMERIC(20, 4) NOT NULL CHECK (quantity > 0),
    average_price NUMERIC(20, 4) NOT NULL CHECK (average_price > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT portfolio_code_format CHECK (code ~ '^[0-9]{6}$')
);
