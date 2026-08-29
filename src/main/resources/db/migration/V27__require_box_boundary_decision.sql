ALTER TABLE box_evaluation_draft
    ADD COLUMN boundary_decision VARCHAR(30);

ALTER TABLE box_evaluation
    ADD COLUMN boundary_decision VARCHAR(30);

ALTER TABLE box_evaluation_draft
    ADD CONSTRAINT ck_box_evaluation_draft_boundary_decision
    CHECK (boundary_decision IS NULL OR boundary_decision IN
        ('CANDIDATE', 'MANUAL', 'NO_SUITABLE_CANDIDATE'));

ALTER TABLE box_evaluation
    ADD CONSTRAINT ck_box_evaluation_boundary_decision
    CHECK (boundary_decision IS NULL OR boundary_decision IN
        ('CANDIDATE', 'MANUAL', 'NO_SUITABLE_CANDIDATE'));

COMMENT ON COLUMN box_evaluation.boundary_decision IS
    'Explicit boundary action; nullable only for evaluations committed before schema v2.';
