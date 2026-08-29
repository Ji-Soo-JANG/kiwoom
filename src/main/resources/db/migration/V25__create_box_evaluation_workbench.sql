CREATE TABLE box_evaluation_batch (
    id BIGSERIAL PRIMARY KEY,
    strategy_version_id BIGINT NOT NULL REFERENCES strategy_definition(id),
    name VARCHAR(150) NOT NULL,
    dataset_version VARCHAR(100) NOT NULL,
    candidate_generator_version VARCHAR(100) NOT NULL,
    sampling_policy_json TEXT NOT NULL,
    blind_policy_version VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_box_eval_batch_status
        CHECK (status IN ('DRAFT', 'READY', 'IN_PROGRESS', 'CLOSED', 'RETIRED')),
    CONSTRAINT uq_box_eval_batch_version
        UNIQUE (strategy_version_id, dataset_version, candidate_generator_version,
                blind_policy_version, name)
);

CREATE INDEX idx_box_eval_batch_status
    ON box_evaluation_batch(status, created_at DESC);

CREATE TABLE box_evaluation_item (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES box_evaluation_batch(id) ON DELETE CASCADE,
    code VARCHAR(6) NOT NULL REFERENCES stock_master(code),
    cutoff_date DATE NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order > 0),
    source_scan_id BIGINT REFERENCES strategy_scan(id),
    data_hash VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    lock_version BIGINT NOT NULL DEFAULT 0 CHECK (lock_version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_box_eval_item_status
        CHECK (status IN ('PENDING', 'DRAFTED', 'COMMITTED', 'REVEALED', 'VOID')),
    CONSTRAINT uq_box_eval_item_symbol_cutoff UNIQUE (batch_id, code, cutoff_date),
    CONSTRAINT uq_box_eval_item_display_order UNIQUE (batch_id, display_order)
);

CREATE INDEX idx_box_eval_item_next
    ON box_evaluation_item(batch_id, status, display_order);
CREATE INDEX idx_box_eval_item_symbol_cutoff
    ON box_evaluation_item(code, cutoff_date DESC);

CREATE TABLE box_evaluation_candidate (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL REFERENCES box_evaluation_item(id) ON DELETE CASCADE,
    candidate_key VARCHAR(30) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    rank_no INTEGER NOT NULL CHECK (rank_no > 0),
    feature_json TEXT NOT NULL,
    generator_version VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_box_eval_candidate_dates CHECK (start_date <= end_date),
    CONSTRAINT uq_box_eval_candidate_key UNIQUE (item_id, candidate_key),
    CONSTRAINT uq_box_eval_candidate_rank UNIQUE (item_id, rank_no)
);

CREATE INDEX idx_box_eval_candidate_item
    ON box_evaluation_candidate(item_id, rank_no);

CREATE TABLE box_evaluation_draft (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL REFERENCES box_evaluation_item(id) ON DELETE CASCADE,
    reviewer_id VARCHAR(100) NOT NULL,
    selected_candidate_key VARCHAR(30),
    edited_start_date DATE,
    edited_end_date DATE,
    label_code VARCHAR(40),
    confidence INTEGER CHECK (confidence BETWEEN 1 AND 5),
    reason_codes TEXT NOT NULL DEFAULT '',
    comment_text VARCHAR(4000),
    draft_revision BIGINT NOT NULL DEFAULT 0 CHECK (draft_revision >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_box_eval_draft_dates
        CHECK (edited_start_date IS NULL OR edited_end_date IS NULL
               OR edited_start_date <= edited_end_date),
    CONSTRAINT uq_box_eval_draft_reviewer UNIQUE (item_id, reviewer_id)
);

CREATE INDEX idx_box_eval_draft_reviewer
    ON box_evaluation_draft(reviewer_id, updated_at DESC);

CREATE TABLE box_evaluation (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL REFERENCES box_evaluation_item(id),
    reviewer_id VARCHAR(100) NOT NULL,
    commit_key VARCHAR(100) NOT NULL,
    selected_candidate_key VARCHAR(30),
    final_start_date DATE,
    final_end_date DATE,
    label_code VARCHAR(40) NOT NULL,
    confidence INTEGER NOT NULL CHECK (confidence BETWEEN 1 AND 5),
    reason_codes TEXT NOT NULL,
    comment_text VARCHAR(4000),
    input_snapshot_json TEXT NOT NULL,
    evaluation_schema_version VARCHAR(50) NOT NULL,
    committed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_box_evaluation_dates
        CHECK (final_start_date IS NULL OR final_end_date IS NULL
               OR final_start_date <= final_end_date),
    CONSTRAINT uq_box_evaluation_commit_key UNIQUE (commit_key)
);

CREATE INDEX idx_box_evaluation_item
    ON box_evaluation(item_id, committed_at DESC);
CREATE INDEX idx_box_evaluation_reviewer
    ON box_evaluation(reviewer_id, committed_at DESC);
CREATE INDEX idx_box_evaluation_label
    ON box_evaluation(evaluation_schema_version, label_code, committed_at DESC);

CREATE TABLE box_evaluation_supersede (
    id BIGSERIAL PRIMARY KEY,
    evaluation_id BIGINT NOT NULL REFERENCES box_evaluation(id),
    superseded_by_evaluation_id BIGINT NOT NULL REFERENCES box_evaluation(id),
    reason VARCHAR(1000) NOT NULL,
    superseded_by VARCHAR(100) NOT NULL,
    superseded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_box_evaluation_superseded_once UNIQUE (evaluation_id),
    CONSTRAINT ck_box_evaluation_not_self_supersede
        CHECK (evaluation_id <> superseded_by_evaluation_id)
);

CREATE INDEX idx_box_evaluation_supersede_replacement
    ON box_evaluation_supersede(superseded_by_evaluation_id);

CREATE TABLE box_evaluation_reveal (
    id BIGSERIAL PRIMARY KEY,
    evaluation_id BIGINT NOT NULL REFERENCES box_evaluation(id),
    outcome_policy_version VARCHAR(50) NOT NULL,
    requested_by VARCHAR(100) NOT NULL,
    outcome_snapshot_json TEXT NOT NULL,
    revealed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_box_evaluation_reveal UNIQUE (evaluation_id)
);

CREATE INDEX idx_box_evaluation_reveal_time
    ON box_evaluation_reveal(revealed_at DESC);
