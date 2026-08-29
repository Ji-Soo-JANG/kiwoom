# 상세 설계서

최종 수정일: 2026-08-29
문서 상태: 초안 기준선 v0.1

## 1. 전략 판정 상세

### 1.1 목표 패턴

최신 거래일을 기준으로 다음 시간 순서를 만족하는 종목을 찾는다.

```text
급락 구간 → 안정 구간(간헐적 거래량 증가) → 최근 회복 → 현재 눌림
```

과거 어느 시점에 이 패턴이 완성됐다는 사실만으로는 후보가 아니다. 회복과 눌림이 최신 데이터에 연결되어 현재 진입 검토가 가능해야 한다.

### 1.2 동적 구간 탐지

고정된 60/120/240일 등의 창을 자격 조건으로 쓰지 않는다. 다음 절차로 구간 경계를 데이터에서 찾는다.

1. 최신 일봉에서 현재 눌림 구간의 저점·고점 구조와 거래량 변화를 찾는다.
2. 직전 회복 파동의 시작점과 고점을 찾고 회복 폭을 계산한다.
3. 회복 시작점에서 과거 방향으로 이동하며 가격 분포, 기울기, 실현 변동성과 범위가 안정적인 동일 상태(regime)의 시작점을 찾는다.
4. 안정 상태 직전의 고점과 하락 저점을 찾아 급락의 크기와 속도를 계산한다.
5. 네 구간의 순서, 연속성, 현재성과 데이터 품질을 필수 조건으로 검사한다.

안정 구간의 기간은 결과 설명과 분석 특징으로 저장하지만 기간 자체의 길고 짧음만으로 탈락시키지 않는다. 계산량과 데이터 가용성 때문에 탐색 상한을 둘 수 있으나 이는 사업 규칙이 아니라 기술 한계로 기록한다.

### 1.3 특징값

| 그룹 | 예시 특징값 |
|---|---|
| 급락 | 하락률, 하락 소요일, 일 최대 하락, 거래량 변화, 갭 비율 |
| 안정 구간 | 시작·종료일, 기간, 중앙값 대비 강건 범위, 기울기, 변동성 축소율, 범위 내 체류율 |
| 거래량 | 중앙 거래량 대비 급증 횟수·배수, 급증 간격, 급증일 가격 반응, 이후 가격 유지율 |
| 회복 | 하락 절대폭 대비 회복 비율, 상승 속도, 고점 돌파 여부, 회복 거래량 |
| 눌림 | 회복 고점 대비 하락률, 회복분 보존율, 눌림 기간, 거래량 감소, 저점 상승 여부 |
| 거래 가능성 | 최근 거래대금, 호가·가격 제한, 거래정지·관리 상태, 데이터 신선도 |

“급락폭의 약 20% 회복”은 초기 가설이다. 기본 탐색 범위는 15~30%로 둘 수 있지만 백테스트 결과 없이 최종값으로 고정하지 않는다.

### 1.4 필수 게이트와 점수

필수 게이트가 하나라도 실패하면 점수와 관계없이 탈락한다.

- 최신 데이터와 품질 검사 통과
- 급락 → 안정 → 회복 → 눌림 순서 성립
- 회복·눌림이 현재성 허용 범위 내 존재
- 거래정지·주문 불가·유동성 하한 위반 없음
- 미래 데이터 사용 없음

통과 종목은 급락 명확성, 안정성, 유효한 거래량 급증, 회복 품질, 눌림 품질과 유동성으로 점수화한다. 각 점수와 거절 사유는 JSON 특징 스냅샷과 검색 가능한 주요 컬럼으로 저장한다.

## 2. 전략 버전과 평가

- 전략 정의는 `strategyCode + version`으로 불변 저장한다.
- 파라미터, 코드 해시, 데이터 기준일과 실행 ID를 기록한다.
- 파라미터 변경은 기존 정의 수정이 아니라 새 버전을 만든다.
- 후보 신호와 주문은 반드시 한 전략 버전에 연결한다.
- 승격 상태 예: `DRAFT → BACKTESTED → PAPER_APPROVED → SIMULATION_APPROVED → LIMITED_LIVE → LIVE_APPROVED → RETIRED`.
- 승격은 최소 표본, 비용 후 기대값, 최대 낙폭, 안정성 및 운영 오류율을 모두 본다.
- 학습용 데이터는 지금부터 원시 특징·결정·결과를 보존하되, AI가 실전 주문 규칙을 직접 변경하는 기능은 별도 검증 전 금지한다.

## 3. 주문과 포지션

### 3.1 브로커 계약

목표 `BrokerAdapter`는 다음 기능을 제공한다.

```text
getAccountSummary()
getPositions()
getOpenOrders()
getOrder(brokerOrderId)
getFills(from, to)
placeOrder(clientDecisionId, symbol, side, quantity, pricePolicy)
amendOrder(brokerOrderId, quantity, pricePolicy)
cancelOrder(brokerOrderId)
getMarketStatus()
```

반환값은 브로커 원문 코드, 정규화 상태, 브로커 주문 ID, 요청·응답 시각과 추적 ID를 포함한다. LIVE 구현은 공식 키움 계약과 모의투자 현장 검증 후 확정한다.

### 3.2 주문 상태

```text
DECIDED → VALIDATED → SUBMITTING → ACKNOWLEDGED → PARTIALLY_FILLED → FILLED
                          │              ├─> AMEND_PENDING
                          │              ├─> CANCEL_PENDING → CANCELLED
                          │              └─> REJECTED
                          └─> UNKNOWN → 조회·대사로만 확정
```

- `clientDecisionId`는 전략 실행, 종목, 방향, 의사결정 종류를 조합한 유일 키다.
- 전송 타임아웃은 `REJECTED`가 아니라 `UNKNOWN`으로 둔다.
- `UNKNOWN`에서는 같은 주문을 다시 보내지 않고 브로커 주문·체결을 조회한다.
- 부분 체결 수량만 포지션에 반영하고 잔여 주문을 별도로 관리한다.

### 3.3 진입 처리

1. 현재 신호의 유효기간과 가격 상태를 재검사한다.
2. 계좌·주문·포지션 대사 상태가 정상인지 확인한다.
3. 위험 엔진이 주문 가능 금액의 최솟값을 계산한다.
4. 수량이 1주 미만이면 거부 사유를 기록한다.
5. 의사결정과 주문 예정 레코드를 한 트랜잭션에 저장한다.
6. 커밋 후 브로커에 전송하고 응답 이벤트를 저장한다.
7. 폴링 또는 이벤트로 체결 상태를 수렴시킨다.

주문 가격 정책(지정가, 시장가, 최우선 추적)과 재주문 횟수는 미결정이며 모의투자 결과로 확정한다.

### 3.4 청산 처리

청산 신호는 손절, 전략 구조 훼손, 익절·추세 보호, 시간 제한(채택 시), 킬 스위치의 위험 축소로 구분한다. 동일 시각에 여러 조건이 충족되면 위험 감소 우선순위를 적용한다. 보유 수량을 초과해 매도하지 않으며 기존 청산 주문의 잔여 수량을 차감한다.

## 4. 위험 엔진

주문 가능 금액은 다음 한도의 최솟값으로 결정한다.

```text
min(
  주문당 한도,
  종목별 잔여 한도,
  총 노출 잔여 한도,
  현금·주문가능금액,
  일 손실/낙폭 한도가 허용하는 금액
)
```

필수 차단 조건:

- 킬 스위치 또는 HALTED
- 계좌·주문·잔고 대사 불일치
- 오래되거나 품질 차단된 시장 데이터
- 시장 상태·종목 상태 불명확
- 전략 미승격 또는 버전 불일치
- 일 손실, 누적 낙폭, 주문 횟수·보유 수 한도 초과
- 같은 의사결정의 기존 주문 존재

차단 결과는 코드, 사용자 설명, 입력 스냅샷과 해제 조건을 저장한다.

## 5. 데이터 모델 계획

기존 테이블은 유지하며 다음 개념을 보강한다. 실제 마이그레이션 전에 현재 스키마와 중복 여부를 확인한다.

| 개념 | 핵심 필드 |
|---|---|
| strategy_signal | scan_id, strategy_version_id, symbol, as_of_date, status, score, feature_json, drop/stable/recovery/pullback dates, rejection_codes |
| automation_run | run_id, trade_date, mode, phase, started_at, finished_at, status, failure_code |
| readiness_check | run_id, check_code, status, observed_value, message, checked_at |
| live_order_link | local_order_id, client_decision_id, broker_order_id, account_hash, submitted_at, last_broker_status |
| broker_event | broker_order_id, event_type, broker_timestamp, received_at, raw_code, normalized_payload, dedup_key |
| position_strategy_link | account_hash, symbol, strategy_version_id, entry_signal_id, quantity, average_price, exit_plan_json |
| reconciliation_issue | run_id, type, local_value, broker_value, severity, resolution_status, resolved_at |
| daily_account_snapshot | trade_date, cash, equity, exposure, realized_pnl, unrealized_pnl, drawdown, source_timestamp |

계좌번호 원문은 저장하지 않고 필요한 경우 안정적인 해시 또는 별칭을 사용한다. 브로커 원문 응답에는 민감정보 제거 규칙을 적용한다.

## 6. API 계획

| 영역 | 목표 API |
|---|---|
| 준비 상태 | `GET /api/trading/readiness`, `POST /api/trading/readiness/check` |
| 자동 운전 | `GET /api/trading/automation`, `POST /api/trading/automation/arm`, `/start`, `/stop`, `/resume` |
| 전략 | `GET /api/trading/strategies`, `GET /api/trading/strategies/{id}/signals` |
| 후보 | `GET /api/kiwoom/discovery?strategyVersion=&asOf=` |
| 계좌 | `GET /api/trading/account`, `/positions`, `/orders`, `/fills` |
| 대사 | `GET /api/trading/reconciliation`, `POST /api/trading/reconciliation/run` |
| 성과 | `GET /api/trading/performance/daily`, `/strategies` |

현재 API 경로와 호환 계층을 유지하고, 구현 시 OpenAPI 문서와 UI 호출을 동시에 갱신한다. 상태 변경 API는 인증, CSRF 정책, 감사 이벤트와 멱등 키를 요구한다.

## 7. 오류·재시도·호출 제한

- 조회 API: 제한된 지수 백오프와 랜덤 지연을 적용한다.
- 주문 API: 자동 재전송하지 않고 의사결정 ID와 브로커 조회로 결과를 확인한다.
- 401: 토큰을 한 번 갱신하되 주문 결과가 모호하면 조회로 전환한다.
- 429: 브로커 제한 정보를 존중하고 신규 주문을 일시 차단한다.
- 5xx/timeout: 조회와 주문을 구분하며 주문은 `UNKNOWN` 처리한다.
- 오류 전문, 키, 토큰, 계좌번호는 로그에 남기지 않는다.

## 8. 스케줄과 거래일

정확한 시각은 설정값이며 다음 순서를 보장한다.

```text
PREPARE → DATA_SYNC → QUALITY_CHECK → RECONCILE → SCAN → READY
READY → MARKET_MONITOR/ORDER_MONITOR/POSITION_MONITOR
MARKET_CLOSED → FINAL_RECONCILE → PERFORMANCE → SAFE_TO_STOP
```

평일이라는 이유만으로 거래일로 간주하지 않는다. 휴장일과 임시 개장 시간을 제공하는 신뢰 가능한 거래일 원천을 사용한다. 각 단계는 멱등 실행 ID를 가지며 선행 단계 실패 시 후속 신규 매수 단계를 실행하지 않는다.

## 9. 테스트 설계

- 전략 단위 테스트: 인공 일봉으로 각 단계, 경계, 현재성, 고정 기간 비의존성을 검증한다.
- 속성 테스트: 미래 일봉 추가 전의 과거 신호가 바뀌지 않는지 확인한다.
- 데이터 테스트: 누락, 중복, 분할·배당 의심, 오래된 데이터 차단을 검증한다.
- OMS 계약 테스트: PAPER와 LIVE 픽스처가 동일 상태 계약을 만족하는지 확인한다.
- 장애 주입: timeout, 401, 429, 5xx, 프로세스 종료, 중복 이벤트, 순서 역전 이벤트를 재현한다.
- 대사 테스트: 부분 체결, 외부 수동 주문, 수량·평단 불일치와 재시작 복구를 검증한다.
- UI E2E: PAPER/LIVE 구분, 위험 확인, ON/OFF, 오류·빈 상태를 검증한다.
- 실전 전 시험: 모의투자와 제한 실전 체크리스트를 별도로 승인한다.

## 10. 요구사항 추적

| 요구사항 | 설계 구현 위치 | 주요 검증 |
|---|---|---|
| STR-001~005 | Dynamic Pattern Engine, strategy_signal | 전략 단위·미래 누수·회귀 테스트 |
| DAT-001~004 | Market Data, Quality, Calendar | 데이터 품질·신선도 테스트 |
| TRD-001~005 | BrokerAdapter, OMS, Reconciliation | 계약·장애 주입·재시작 테스트 |
| RSK-001~004 | Risk Engine, readiness_check | 한도·킬 스위치·실패 폐쇄 테스트 |
| OPS-001~003 | automation_run, scheduler | 단계 순서·멱등·복구 테스트 |
| UI-001~004 | 운영·후보·계좌 화면 | 접근성·E2E 테스트 |
