ALTER TABLE box_formation_evaluation
    ADD COLUMN IF NOT EXISTS boundary_decision VARCHAR(40),
    ADD COLUMN IF NOT EXISTS label_code VARCHAR(40),
    ADD COLUMN IF NOT EXISTS reason_codes TEXT,
    ADD COLUMN IF NOT EXISTS comment_text VARCHAR(4000);
