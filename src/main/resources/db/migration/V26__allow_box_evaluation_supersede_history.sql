ALTER TABLE box_evaluation
    DROP CONSTRAINT IF EXISTS uq_box_evaluation_item;

CREATE INDEX IF NOT EXISTS idx_box_evaluation_item
    ON box_evaluation(item_id, committed_at DESC);
