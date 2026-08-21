CREATE TABLE stock_master_snapshot (
    snapshot_date DATE NOT NULL,
    code VARCHAR(6) NOT NULL,
    name VARCHAR(200) NOT NULL,
    market VARCHAR(20) NOT NULL,
    product_type VARCHAR(30) NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (snapshot_date, code)
);

CREATE INDEX idx_stock_master_snapshot_code_date ON stock_master_snapshot(code, snapshot_date);

-- 최초 마이그레이션 시점의 현재 종목만 시작 스냅샷으로 보존한다.
-- 이 날짜 이전의 상장폐지 종목은 외부 과거 종목 마스터 없이는 복원할 수 없다.
INSERT INTO stock_master_snapshot(snapshot_date, code, name, market, product_type)
SELECT CURRENT_DATE, code, name, market, product_type
FROM stock_master
WHERE active = TRUE;
