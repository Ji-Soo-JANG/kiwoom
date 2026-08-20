# 변경 내역 (Changelog)

## 2026-08-20

### 1. 박스권 기준 기간 슬라이더 추가

기존에 60일로 하드코딩된 박스권 횡보 분석 기간을 프론트엔드에서 조절할 수 있도록 변경했다.

**백엔드**
- `StrategyPatternDetector` — `BASE_DAYS` 상수를 `DEFAULT_BASE_DAYS = 60`으로 변경하고, `analyze(stock, prices, baseDays)` 오버로드를 추가해 기간을 파라미터로 받도록 수정 (20~250 사이로 안전 클램프). 조건 문구도 `{n}거래일 박스권 횡보`로 동적 표시.
- `StrategyScanService` — `scan(int boxRangeDays)` 오버로드 추가 (기존 `scan()`은 60일 기본값 유지).
- `KiwoomController` — `GET /api/kiwoom/strategy-candidates?boxRangeDays=90` 쿼리 파라미터 추가 (기본 60).

**프론트엔드**
- `getStrategyCandidates(boxRangeDays)` API에 파라미터 전달.
- 전략 결과 카드 위에 "박스권 기준 기간" 날짜 바(슬라이더) 추가 — 30~120거래일 범위, 10일 단위, 기본 60일.
- `useDeferredValue`를 사용해 슬라이더 조작 시 요청이 누적되지 않고 멈추면 자동으로 재분석.

**기타**
- `StrategyPatternDetectorTest`에 사용자 지정 기간 테스트 추가.
- `MarketDiscovery.test.jsx`에 슬라이더 조절 시 재검색 테스트 추가.
- `docs/openapi.json`, `openapi.d.ts` 갱신.

---

### 2. 차트 타임프레임 선택 (일봉/주봉/월봉/년봉) 추가

차트에 기간별 캔들 조회를 추가하고, 한 번에 보이는 캔들 수를 500건까지 확장했다.

**백엔드**
- `KiwoomHttpClient` — 기간별 차트 요청 `requestPeriodPrices()` 추가: 일봉 `ka10081`, 주봉 `ka10082`, 월봉 `ka10083`, 년봉 `ka10094` (모두 `/api/dostk/chart` + 동일 요청 구조).
- `KiwoomApiService` — `getPeriodPrices(code, baseDate, limit, period)` 추가. 기존 `getDailyPrices`는 그대로 일봉으로 위임하고, 캐시 키에 api-id를 포함해 주기별로 캐시가 분리됨.
- `KiwoomController` — `GET /api/kiwoom/stock-price/{code}/daily?period=week&limit=500` 지원.

**프론트엔드**
- 차트 종류 선택(일봉/주봉/월봉/년봉) — 선택 시 해당 주기로 자동 재조회, `localStorage`에 기억.
- 조회 기간에 1년(250) 옵션 추가, "전체"는 최대 500건(약 2년치)까지 표시.
- 차트가 데이터를 직접 조회하도록 변경(App의 중복 호출 제거), 로딩/오류 상태 표시.

**기타**
- `KiwoomApiServiceTest`에 주기별 api-id 매핑 테스트 추가.
- `StockDailyChart.test.jsx`를 차트 자가 조회 구조에 맞게 재작성.
- `docs/openapi.json`, `openapi.d.ts`, `README.md` 갱신.

---

### 3. 포트폴리오 화면 보유종목 정보 확장

포트폴리오 테이블에 종목코드, 가능수량, 매입금액 컬럼을 추가하고 모바일 카드 뷰를 도입했다.

**프론트엔드**
- 테이블 컬럼 7개 → 10개로 확장: 종목명, **종목코드**, 보유수량, **가능수량**, **매입금액**, 평균단가, 현재가, 평가금액, 손익, 수익률.
- 종목명과 종목코드를 별도 컬럼으로 분리해 종목코드를 더 쉽게 확인할 수 있게 함.
- 850px 이하 화면에서 테이블 대신 카드형 레이아웃으로 전환 — 종목명+코드 상단, 나머지 8필드를 2열 그리드로 배치.
- 테이블은 가로 스크롤로 전체 확인 가능.

**CSS**
- `.position-card-list` — 모바일 카드 컨테이너 (850px 이하에서만 `display: grid`).
- `.position-card`, `.position-card-header`, `.position-card-grid` — 카드 내부 스타일.
- `.position-code` — 종목코드 탭ular-nums 폰트.
- `.position-name-btn` — 종목명 클릭 버튼 스타일.

**테스트**
- `AccountPortfolio.test.jsx` — 종목코드, 가능수량, 매입금액 표시 검증 추가.

---

### 검증 결과

| 구분 | 테스트 수 | 결과 |
|------|-----------|------|
| 백엔드 (JUnit) | 77 | ✅ 전부 통과 |
| 프론트엔드 (Vitest) | 43 | ✅ 전부 통과 |
| ESLint | — | ✅ 경고 없음 |
| Spotless 포맷 | — | ✅ 적용 완료 |
| Vite 빌드 | — | ✅ 정적 산출물 갱신 완료 |
