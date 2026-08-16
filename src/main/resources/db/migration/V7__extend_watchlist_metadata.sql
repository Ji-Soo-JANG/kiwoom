ALTER TABLE watchlist ADD COLUMN group_name VARCHAR(50) NOT NULL DEFAULT '기본';
ALTER TABLE watchlist ADD COLUMN note VARCHAR(500) NOT NULL DEFAULT '';
CREATE INDEX idx_watchlist_user_group ON watchlist(username, group_name, code);
