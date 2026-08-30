CREATE TABLE box_research_dataset (
    id BIGSERIAL PRIMARY KEY,
    dataset_key VARCHAR(80) NOT NULL UNIQUE,
    dataset_type VARCHAR(20) NOT NULL,
    source_batch_id BIGINT REFERENCES box_evaluation_batch(id),
    sampling_policy_json TEXT NOT NULL,
    blind_policy_version VARCHAR(50) NOT NULL,
    feature_snapshot_version VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_box_research_dataset_type CHECK (dataset_type IN ('DISCOVERY','BOUNDARY','HOLDOUT','REGRESSION'))
);

CREATE TABLE box_formation_evaluation (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL REFERENCES box_evaluation_item(id) ON DELETE CASCADE,
    reviewer_id VARCHAR(100) NOT NULL,
    formation_label VARCHAR(20) NOT NULL,
    proposed_start_date DATE,
    proposed_end_date DATE,
    final_start_date DATE,
    final_end_date DATE,
    proposed_lower_support_min NUMERIC,
    proposed_lower_support_max NUMERIC,
    proposed_upper_resistance_min NUMERIC,
    proposed_upper_resistance_max NUMERIC,
    final_lower_support_min NUMERIC,
    final_lower_support_max NUMERIC,
    final_upper_resistance_min NUMERIC,
    final_upper_resistance_max NUMERIC,
    note VARCHAR(4000),
    revision BIGINT NOT NULL DEFAULT 1,
    committed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_box_formation_label CHECK (formation_label IN ('BOX','NOT_BOX','UNCERTAIN')),
    CONSTRAINT ck_box_formation_dates CHECK (
        (final_start_date IS NULL OR final_end_date IS NULL OR final_start_date <= final_end_date)
        AND (proposed_start_date IS NULL OR proposed_end_date IS NULL OR proposed_start_date <= proposed_end_date)
    ),
    CONSTRAINT uq_box_formation_item_reviewer UNIQUE (item_id, reviewer_id)
);

CREATE INDEX idx_box_formation_item ON box_formation_evaluation(item_id, committed_at DESC);
