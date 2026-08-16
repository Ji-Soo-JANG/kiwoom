ALTER TABLE alert_rule DROP CONSTRAINT alert_rule_condition_type_check;
ALTER TABLE alert_rule DROP CONSTRAINT alert_rule_threshold_check;
ALTER TABLE alert_rule ALTER COLUMN threshold DROP NOT NULL;
ALTER TABLE alert_rule ADD CONSTRAINT alert_rule_condition_type_check CHECK (
    condition_type IN ('PRICE_ABOVE', 'PRICE_BELOW', 'RSI_ABOVE', 'RSI_BELOW', 'MACD_CROSS_UP', 'MACD_CROSS_DOWN')
);
ALTER TABLE alert_rule ADD CONSTRAINT alert_rule_threshold_check CHECK (
    (condition_type IN ('PRICE_ABOVE', 'PRICE_BELOW') AND threshold > 0)
    OR (condition_type IN ('RSI_ABOVE', 'RSI_BELOW') AND threshold BETWEEN 0 AND 100)
    OR (condition_type IN ('MACD_CROSS_UP', 'MACD_CROSS_DOWN') AND threshold IS NULL)
);
ALTER TABLE alert_event ALTER COLUMN threshold DROP NOT NULL;
