ALTER TABLE watchlist ADD COLUMN username VARCHAR(100) NOT NULL DEFAULT 'admin';
ALTER TABLE watchlist DROP CONSTRAINT watchlist_pkey;
ALTER TABLE watchlist ADD PRIMARY KEY (username, code);
ALTER TABLE watchlist ALTER COLUMN username DROP DEFAULT;

ALTER TABLE portfolio_position ADD COLUMN username VARCHAR(100) NOT NULL DEFAULT 'admin';
ALTER TABLE portfolio_position DROP CONSTRAINT portfolio_position_pkey;
ALTER TABLE portfolio_position ADD PRIMARY KEY (username, code);
ALTER TABLE portfolio_position ALTER COLUMN username DROP DEFAULT;

ALTER TABLE portfolio_trade ADD COLUMN username VARCHAR(100) NOT NULL DEFAULT 'admin';
ALTER TABLE portfolio_trade ALTER COLUMN username DROP DEFAULT;
CREATE INDEX idx_portfolio_trade_user_traded_at
    ON portfolio_trade(username, traded_at DESC);
