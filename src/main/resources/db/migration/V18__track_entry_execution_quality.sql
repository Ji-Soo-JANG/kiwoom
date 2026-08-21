ALTER TABLE trading_performance_sample ADD COLUMN source_key VARCHAR(120);
ALTER TABLE trading_performance_sample ADD COLUMN sample_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE trading_performance_sample ALTER COLUMN net_return_rate DROP NOT NULL;
UPDATE trading_performance_sample SET source_key = 'legacy-' || id WHERE source_key IS NULL;
ALTER TABLE trading_performance_sample ALTER COLUMN source_key SET NOT NULL;
CREATE UNIQUE INDEX uq_trading_performance_source ON trading_performance_sample(source_key);
