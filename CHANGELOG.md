# 변경 내역 (Changelog)

## Unreleased

### Improved
- **검색 분류 정확도 개선**: `StockProductType.classify()`에 종목코드 패턴 기반 보조 분류 추가 (ETN 50-59 코드 대역)
- **검색 정렬 개선**: 코드 정확일치 > 종목명 정확일치 > 코드 접두사 > 종목명/초성 접두사 > 상품유형 매칭 순으로 정렬 점수 부여
- **검색 알고리즘 문서화**: `searchStocks()` 메서드에 매칭·정렬 기준 Javadoc 추가
- **레거시 수동 포트폴리오 숨김 처리**: UI는 계좌 기반 포트폴리오로 전환 완료, 백엔드 API/테이블은 기존 데이터 보호를 위해 유지
- **데이터 신선도 표시**: 현재가 카드에 장중/장마감 뱃지 + 조회 시각, 차트에 데이터 기준일 표시

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
| 프론트엔드 (Vitest) | 46 | ✅ 전부 통과 |
| ESLint | — | ✅ 경고 없음 |
| Spotless 포맷 | — | ✅ 적용 완료 |
| Vite 빌드 | — | ✅ 정적 산출물 갱신 완료 |

---

## 2026-08-21

### P0 — 실 API 연동 안정화

#### 1. 계좌 API cont-yn/next-key 페이징 및 안정성

키움 REST API의 계좌 평가잔고(`kt00018`) 응답에 포함되는 `cont-yn`/`next-key` 페이징을 순회해 보유종목 전체를 조회하도록 변경했다.

**백엔드**
- `KiwoomHttpClient` — `postPaged()` 메서드를 추가해 응답 헤더의 `cont-yn`과 `next-key`를 파싱한다.
- `KiwoomHttpClient.requestAccountPortfolioPaged(accessToken, nextKey)` — 요청 헤더에 `cont-yn: Y`와 `next-key`를 포함해 다음 페이지를 요청한다.
- `KiwoomApiService.fetchAccountPortfolioPages()` — 재귀적으로 페이징을 순회해 모든 보유종목을 하나로 합친다.
- `KiwoomResponseMapper.parseAccountPortfolio()` — 보유종목 배열이 없는 응답(빈 계좌)도 안전하게 처리한다.

**프론트엔드**
- `AccountPortfolio` — `refetchOnWindowFocus: false`를 적용해 창 전환 시 불필요한 재호출을 방지한다.

#### 2. 시장 순위 API 부분 실패 격리

시장 순위 조회 시 코스피 또는 코스닥, 급등·급락·거래량 중 하나만 실패해도 나머지 목록은 계속 표시하도록 격리했다.

**백엔드**
- `KiwoomApiService.requestMarketRankings(token)` — 코스피/코스닥 각각에 `onErrorResume`을 적용해 한쪽 실패 시 빈 목록으로 대체한다.
- `KiwoomApiService.requestMarketRankings(marketType, token)` — 급등/급락/거래량 각 순위에도 `onErrorResume`을 적용해 개별 순위 실패 시 빈 목록으로 대체한다.
- 부분 실패 시 `market_rankings_partial_failure`와 `ranking_partial_failure` 로그를 남긴다.

**프론트엔드**
- `MarketDiscovery` — 빈 순위 목록일 때 "장 마감이거나 해당 순위 데이터가 없습니다" 안내 문구를 표시한다.
- 마지막 갱신 시각을 표시한다.

#### 3. API 호출량 관리

수동 새로고침의 연속 클릭을 방지하고 다음 갱신 가능 시점을 표시한다.

**프론트엔드**
- `useRefreshCooldown` 훅 — 30초 쿨다운 동안 연속 클릭을 차단하고 남은 초 수를 표시한다.
- `rankings` 쿼리에 `refetchOnWindowFocus: false`를 적용해 창 전환 시 자동 재조회를 방지한다.
- 새로고침 버튼이 쿨다운 중일 때 "N초 후 갱신 가능" 상태를 표시한다.

#### 4. 민감 정보 보호

**백엔드**
- `KiwoomResponseMapper.maskAccountNumber()` — 계좌번호를 앞 3자리와 뒤 2자리만 노출하고 나머지를 `***`로 마스킹한다.
  - 예: `123-456-78901` → `123-***-**01`
- `parseAccountPortfolio()`가 마스킹된 계좌번호를 `AccountPortfolioResponse`에 포함시켜 화면과 로그에 전체 계좌번호가 노출되지 않는다.
- 기존 테스트(`doesNotLogCredentialsOrAccessToken`)에서 API secret과 접근 토큰이 로그에 포함되지 않는지를 검증한다.

### 검증 결과

| 구분 | 테스트 수 | 결과 |
|------|-----------|------|
| 백엔드 (JUnit) | 77 | ✅ 전부 통과 |
| 프론트엔드 (Vitest) | 46 | ✅ 전부 통과 |
| Vite 빌드 | — | ✅ 정적 산출물 갱신 완료 |

---

### P1 — URL 동기화

차트 화면의 선택 종목 상태를 URL 쿼리 파라미터에 동기화했다.

**프론트엔드**
- `App.jsx` — `useSearchParams`를 도입해 `handleSingleSearch`와 `handleMultipleSearch`가 `navigate('/chart?code=XXXXXX')`를 호출하도록 변경.
- `useEffect`에서 URL의 `code` 파라미터를 읽어 직접 접근·새로고침 시 선택 종목을 자동 복원.
- 관심 종목, 시장 순위, 계좌 보유종목에서 종목 선택 시에도 동일하게 URL이 갱신됨.

**테스트**
- `App.test.jsx` — 종목 검색 후 URL 동기화 검증, 직접 URL 접근 시 자동 조회 검증 테스트 추가.

### 검증 결과

| 구분 | 테스트 수 | 결과 |
|------|-----------|------|
| 백엔드 (JUnit) | 77 | ✅ 전부 통과 |
| 프론트엔드 (Vitest) | 48 | ✅ 전부 통과 |
| Vite 빌드 | — | ✅ 정적 산출물 갱신 완료 |
