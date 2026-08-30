ALTER TABLE box_formation_evaluation
    ADD COLUMN IF NOT EXISTS period_decision VARCHAR(20),
    ADD COLUMN IF NOT EXISTS zone_decision VARCHAR(20),
    ADD COLUMN IF NOT EXISTS proposed_lower_support_min NUMERIC,
    ADD COLUMN IF NOT EXISTS proposed_lower_support_max NUMERIC,
    ADD COLUMN IF NOT EXISTS proposed_upper_resistance_min NUMERIC,
    ADD COLUMN IF NOT EXISTS proposed_upper_resistance_max NUMERIC;

ALTER TABLE box_formation_evaluation
    ADD CONSTRAINT ck_box_formation_period_decision
        CHECK (period_decision IS NULL OR period_decision IN ('ACCEPTED', 'MODIFIED')),
    ADD CONSTRAINT ck_box_formation_zone_decision
        CHECK (zone_decision IS NULL OR zone_decision IN ('ACCEPTED', 'MODIFIED'));
