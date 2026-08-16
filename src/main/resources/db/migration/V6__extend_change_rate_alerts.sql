ALTER TABLE alert_rule DROP CONSTRAINT alert_rule_condition_type_check;
ALTER TABLE alert_rule DROP CONSTRAINT alert_rule_threshold_check;

ALTER TABLE alert_rule ADD CONSTRAINT alert_rule_condition_type_check CHECK (
    condition_type IN ('PRICE_ABOVE', 'PRICE_BELOW', 'CHANGE_RATE_ABOVE', 'CHANGE_RATE_BELOW',
                       'RSI_ABOVE', 'RSI_BELOW', 'MACD_CROSS_UP', 'MACD_CROSS_DOWN')
);
ALTER TABLE alert_rule ADD CONSTRAINT alert_rule_threshold_check CHECK (
    (condition_type IN ('PRICE_ABOVE', 'PRICE_BELOW') AND threshold > 0)
    OR (condition_type IN ('CHANGE_RATE_ABOVE', 'CHANGE_RATE_BELOW') AND threshold > 0 AND threshold <= 100)
    OR (condition_type IN ('RSI_ABOVE', 'RSI_BELOW') AND threshold BETWEEN 0 AND 100)
    OR (condition_type IN ('MACD_CROSS_UP', 'MACD_CROSS_DOWN') AND threshold IS NULL)
);
