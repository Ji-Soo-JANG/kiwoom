CREATE TABLE strategy_definition (
    id BIGSERIAL PRIMARY KEY,
    strategy_id VARCHAR(80) NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    parameters_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(strategy_id, version)
);

INSERT INTO strategy_definition(strategy_id, version, name, description, status, parameters_json)
VALUES ('drop-base-breakout-pullback', 1, '급락-횡보-돌파-눌림',
        '급락 후 장기 횡보와 거래량 증가, 초기 박스 돌파 및 저거래량 눌림을 탐지한다.',
        'PAPER_ENABLED',
        '{"baseDays":60,"minimumScore":70,"minimumDrawdownRate":-0.20,"maximumBoxRangeRate":0.30,"minimumVolumeSpikes":2,"volumeSpikeMultiple":2.5,"minimumBreakoutRate":0.05,"maximumBreakoutRate":0.18,"minimumBreakoutVolumeMultiple":2.0,"minimumPullbackRate":-0.12,"maximumPullbackRate":-0.02}');
