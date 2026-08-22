UPDATE auto_trading_control
SET paper_strategy = 'drop-multi-base-current-pullback-v3',
    updated_by = 'V24_swing_default',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1
  AND paper_strategy = 'drop-base-breakout-pullback-v1';
