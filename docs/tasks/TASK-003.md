# TASK-003 — Historical Daily Data Backfill & Quality Baseline

## 1. 메타데이터

| 항목 | 계약 내용 |
|---|---|
| 작업 ID | TASK-003 |
| 제목 | Historical Daily Data Backfill & Quality Baseline |
| 상태 | READY |
| 우선순위 | HIGH |
| 선행 조건 | TASK-002 DONE, Historical Daily Price Analysis, Historical Backfill Pre-Analysis |
| 기본 과거 데이터 목표일 | 2015-01-01 |
| 기본 universe 정책 | CURRENT_UNIVERSE_BACKFILL |
| 핵심 산출물 | 재개 가능·멱등적인 과거 일봉 backfill 및 품질 기준선 |
| 데이터 source | Kiwoom REST 일봉 차트 API ka10081 |
| 보호 대상 변경 | frontend/e2e/app.spec.js는 수정·복원·stage·commit하지 않음 |

2015-01-01은 데이터 수집 목표일이며 Box Formation 규칙이 아니다. 2010년 full backfill은 이 task의 범위 밖이다.

## 2. 배경

현재 일봉 데이터는 최신 데이터 중심이다.

```
daily_candle rows     1,892,978
symbols               3,922
MIN(trade_date)       2024-07-24
MAX(trade_date)       2026-08-21
active stock_master   3,928
sync_state            FAILED 3,925 / SUCCESS 3
```

기존 구조에는 애플리케이션 요청량 `limit=1500`과 현재 프로젝트 chart client의 `1~500` validation 사이의 불일치가 있다. 이 `1~500` 범위를 Kiwoom `ka10081`의 공식 page-size 계약으로 간주하지 않는다. 과거 수집을 큰 count 요청으로 구현하지 않고, 애플리케이션의 날짜/수집 목표와 broker의 실제 page/continuation 계약을 분리한다. 실제 `ka10081` pagination semantics는 Gate 1의 인증된 Real API 검증으로 확정한다. 기존 데이터는 보존하고 중단·부분 실패·재실행을 지원한다. Discovery A1과 production strategy/trading은 이 task에서 변경하지 않는다.
## 3. 목적

- Kiwoom에 존재하는 일봉을 2015-01-01까지 과거 방향으로 확장한다.
- cont-yn / next-key continuation으로 페이지를 순회한다.
- 페이지·checkpoint 단위 atomicity와 멱등 rerun을 보장한다.
- historical backfill 상태를 latest sync 상태와 분리한다.
- 잘못된 응답, 무진행, 중복, 비정상 OHLCV를 감지한다.
- 전체 실행 전에 품질 기준선을 확보한다.

## 4. 범위

### 포함

- ka10081 historical daily OHLCV 수집
- base_dt cursor 계산 및 continuation 순회
- 날짜 범위 기반 backfill service
- candle 자연키 atomic upsert
- 종목별 backfill 상태와 run 감사 정보
- resume, rerun, rate limit, bounded retry
- 수정주가 옵션 검증
- CURRENT_UNIVERSE_BACKFILL 및 survivorship limitation 기록
- 자동 테스트, 1종목 smoke, 5종목 pilot, 품질 기준선

### 제외

- 2010 full backfill
- 상장폐지 포함 historical universe 복원 및 survivorship bias 제거
- Discovery A1 생성·평가
- Box Formation Rule v1, Detector v2, lifecycle rule
- production strategy, signal, order, trading 변경
- ETF/ETN 등 product universe 정책의 임의 변경
- 기존 데이터 삭제·전체 재생성
- 명시적 승인 없는 full-universe 자동 실행
- frontend/e2e/app.spec.js 수정·복원·stage·commit

## 5. 아키텍처 및 구현 계약

```
Backfill service
  ├─ universe/목표일 선택
  ├─ 종목별 state와 oldest date 조회
  ├─ 이미 충족 여부 판단
  ├─ target/exhaustion까지 page traversal
  └─ state·metric·checkpoint 기록

Kiwoom client
  ├─ broker-valid 단일 page 요청
  ├─ application target count/date와 broker pagination 분리
  ├─ 실제 broker page-size를 임의 상수로 가정하지 않음
  ├─ 응답 mapping
  └─ candle + continuation metadata 반환

Persistence
  ├─ page 검증
  ├─ candle atomic upsert
  └─ checkpoint/state atomic commit
```

Broker client는 한 페이지를 반환하고, 언제까지 과거로 내려갈지는 service가 결정한다.

```java
DailyChartPage requestDailyChart(
        String code,
        LocalDate baseDate,
        ContinuationToken continuation
)
```

Service 요청은 `backfill(code, targetStartDate)`와 같은 날짜 기반이어야 한다. `1500`과 같은 application-level 수집 목표를 broker의 단일 page-size로 전달하지 않는다. 기존 프로젝트의 `1~500` validation은 현재 구현 제약으로 취급하며, `ka10081`의 공식 page-size 계약으로 간주하지 않는다. 실제 broker pagination은 응답의 continuation metadata와 Gate 1에서 검증된 API semantics를 따른다.
각 page 저장 전 다음을 검증한다.

- 요청 code와 응답 code 일치
- 날짜 해석 가능 및 base_dt보다 미래가 아님
- page 내 날짜 중복 없음
- OHLCV null/invalid 없음
- high >= low, high >= open/close, low <= open/close
- volume 음수 아님
- oldest date 결정 가능
- continuation마다 실제로 더 과거로 진행
- 같은 page/date/token 반복 시 NO_PROGRESS 종료

## 6. Backfill 상태 모델

최소 상태:

```
PENDING
IN_PROGRESS
TARGET_REACHED
HISTORY_EXHAUSTED
ALREADY_SATISFIED
FAILED
```

- PENDING: 대상이나 미시작
- IN_PROGRESS: 실행 중이며 checkpoint가 있을 수 있음
- TARGET_REACHED: 목표 범위의 boundary crossing이 확인됨
- HISTORY_EXHAUSTED: broker traversal이 targetStartDate에 도달하기 전에 정상적으로 종료되었으며, 더 오래된 history를 현재 수집 경로에서 취득할 수 없음
- ALREADY_SATISFIED: 이미 목표 범위를 충족하여 API 호출 불필요
- FAILED: permanent error, invalid response, no-progress, retry 한도 초과

`TARGET_REACHED`, `HISTORY_EXHAUSTED`, `ALREADY_SATISFIED`는 traversal이 종료된 terminal outcome이다. 이 중 `TARGET_REACHED`와 `ALREADY_SATISFIED`는 목표 범위 충족을 의미하지만, `HISTORY_EXHAUSTED`는 목표 범위 충족을 의미하지 않는다. `FAILED`는 정상 terminal outcome이 아니며 성공으로 취급하지 않는다.
`HISTORY_EXHAUSTED`는 `TARGET_REACHED`와 동일한 데이터 충족 상태가 아니다. 가능한 경우 exhaustion reason을 다음과 같이 구분하여 기록한다.

- `LISTING_HISTORY_EXHAUSTED`: 확인 가능한 상장 이력 경계 때문에 더 오래된 candle이 존재하지 않음
- `BROKER_HISTORY_EXHAUSTED`: broker가 정상적으로 history 종료를 반환함
- `UNKNOWN_HISTORY_EXHAUSTED`: continuation 종료 원인을 신뢰성 있게 분류할 수 없음

reason을 확정할 근거가 없으면 임의로 `LISTING_HISTORY_EXHAUSTED` 또는 `BROKER_HISTORY_EXHAUSTED`로 추정하지 않고 `UNKNOWN_HISTORY_EXHAUSTED`로 기록한다.

설명되지 않은 `UNKNOWN_HISTORY_EXHAUSTED`가 Pilot에서 발생하면 Gate 4의 명시적 검토 대상이다. 반복적이거나 구조적으로 발생하여 historical coverage의 신뢰성을 판단할 수 없는 경우 Full Universe 실행을 진행하지 않고 BLOCKED로 보고한다.

권장 필드:

```
code, target_start_date, oldest_synced_date, status
exhaustion_reason
continuation_key, continuation_active
page_count, candle_count, attempt_count
last_error_code, last_error_message
started_at, last_checkpoint_at, completed_at, updated_at
```

선택적 run record에는 run_id, target range, universe policy/snapshot, 요청·완료·실패 수, status, timestamp, effective config를 기록한다.

## 7. Continuation 및 base_dt 의미

base_dt는 시작 cursor이고 cont-yn / next-key는 page traversal 제어값이다.

```
첫 요청 — 기존 candle이 있는 경우:
  base_dt = 현재 저장 oldest date - 1일
  continuation header 없음

첫 요청 — 기존 candle이 없는 경우:
  base_dt = effectiveRunEndDate
  continuation header 없음

응답:
  cont-yn = Y
  next-key = broker 반환 token

다음 요청:
  동일 code/논리 요청
  반환 token을 continuation header에 전달

종료:
  target 도달 또는 continuation 없음 또는 terminal error
```

저장 history가 없으면 최초 cursor는 `targetStartDate`가 아니라 `effectiveRunEndDate`를 기준으로 정한다. 이는 현재/최근 history부터 과거 방향으로 내려가며 목표 범위를 확보하기 위함이다. 실제 최초 `base_dt`와 effective run range는 run record에 기록한다.

`targetStartDate`는 거래일임을 가정하지 않는다. 예를 들어 기본값 `2015-01-01`은 휴장일일 수 있으므로 해당 날짜 자체의 candle 존재를 `TARGET_REACHED` 조건으로 요구하지 않는다.

`TARGET_REACHED`는 targetStartDate 이후의 필요한 history가 확보되었고 broker traversal이 targetStartDate 경계를 넘어 더 과거 데이터까지 진행했음이 확인된 경우 판정할 수 있다. 예를 들어 `targetStartDate=2015-01-01`이고 가장 오래된 필요한 거래 candle이 `2015-01-02`이며 broker 응답이 `2014-12-30`까지 내려갔다면 target boundary를 통과한 것으로 판단할 수 있다.

Backfill target 범위보다 오래된 candle이 broker page에 함께 반환될 수 있다. 이러한 boundary-crossing candle의 저장 여부는 명시적이고 결정적인 정책으로 구현하며 테스트한다. 단순히 `MIN(trade_date) <= targetStartDate`만을 `TARGET_REACHED` 조건으로 사용하지 않는다.

`next-key`가 영구 resume token이라고 가정하지 않는다. 내구성 있는 resume anchor는 commit된 `oldest_synced_date`이며, 재시작은 해당 경계를 기준으로 새로운 traversal을 시작할 수 있어야 한다.
## 8. Atomic upsert 및 보존

Candle 자연키는 (code, trade_date)이며 다음과 동등한 방식으로 저장한다.

```sql
INSERT INTO daily_candle (...)
VALUES (...)
    ON CONFLICT (code, trade_date)
DO UPDATE SET ...;
```

Conflict 시 갱신 column과 정책을 문서화한다. 기존 row를 삭제·bulk 재생성하지 않는다. 기존 candle과 새 OHLCV가 다를 때의 처리는 결정적이어야 하고 테스트로 확인되어야 한다. page upsert와 checkpoint/state update는 같은 transaction으로 commit한다.

## 9. Latest sync 분리

market_data_sync_state는 최신 동기화 전용으로 유지한다. Historical state는 별도 table/model 또는 분리된 namespace를 사용한다. Historical terminal outcome을 latest-sync SUCCESS로 기록하지 않으며 기존 FAILED도 실제 성공 수집 없이 성공으로 바꾸지 않는다. Backfill 후 latest sync regression을 실행하고 production strategy/trading은 변경하지 않는다.

## 10. Resume 및 rerun

재시작 시 state와 DB MIN(trade_date)를 읽고, 이미 충족된 종목은 API 호출 없이 건너뛴다. commit된 oldest-date 경계부터 계속하며 이전 page를 보존하고 이미 수집한 전체 기간을 다시 요청하지 않는다. 동일 run을 반복해도 중복 row가 없어야 한다. N번째 page 이후 중단 시 1~N page와 일치하는 checkpoint를 남기며 commit 전 중단은 이전 checkpoint를 기준으로 한다.
`ALREADY_SATISFIED` 판정도 단순히 `MIN(trade_date) <= targetStartDate` 또는 특정 calendar date의 candle 존재 여부만으로 결정하지 않는다. 이전에 `TARGET_REACHED`로 검증·checkpoint된 state가 있거나, 저장된 history가 본 TASK의 target boundary 충족 조건을 만족한다는 재현 가능한 근거가 있을 때 API 호출을 생략한다. 충족 여부가 불명확하면 `ALREADY_SATISFIED`로 추정하여 건너뛰지 않는다.
## 11. Rate Limit 및 Retry

실제 broker 응답을 기준으로 configurable rate limit을 적용한다. 가능한 경우 HTTP status, Kiwoom error code, Retry-After, rate-limit indicator를 사용한다.

- transient transport/throttling만 bounded retry
- broker 지연시간 또는 bounded backoff 사용
- validation/authentication/malformed request는 무한 retry 금지
- 시도 횟수와 최종 오류 기록
- 검증되지 않은 고정 sleep/retry 수를 production 규칙으로 박지 않음

## 12. 수정주가 정책

Historical request는 기존 collection과 같은 price-adjustment option을 사용한다. 현재 기준값은 upd_stkpc_tp = "1"이다. Full run 전에 공식 의미와 저장 데이터 일관성을 검증한다. 최신 sync와 backfill이 서로 다른 옵션을 쓰지 않도록 중앙 설정으로 관리한다.

## 13. Universe 및 Survivorship 한계

기본 정책은 CURRENT_UNIVERSE_BACKFILL이다. 현재 eligible한 stock_master 종목만 과거 방향으로 채운다. 상장폐지 또는 현재 master에서 제외된 종목은 복원하지 않는다.

```
Historical survivorship bias: PRESENT / NOT REMOVED
```

따라서 이 데이터는 formation research에는 쓸 수 있으나 최종 무편향 수익률·strategy backtest universe로 표현하지 않는다. Historical delisted-universe reconstruction은 별도 task다. ETF/ETN/Reg.S 등 상품 포함 여부는 기존 정책을 따르며 Discovery sampling과 분리한다.

## 14. 실행 Gate

### Gate 0 — 자동 테스트

실제 Kiwoom 호출 없이 page 검증, continuation, no-progress, upsert, transaction, retry, state transition, resume, rerun, latest-sync regression을 검증한다.

### Gate 1 — 1종목 Smoke

인증된 real API에서 과거 이력이 충분한 1종목으로 첫 page, `base_dt`, `cont-yn`, `next-key`, 더 과거로의 이동, 중복·무한 loop, price-adjustment option을 확인한다. 실패하면 Pilot을 실행하지 않는다.

Gate 1은 본 TASK가 가정한 `ka10081` traversal contract를 실제 인증 환경에서 확정하는 Gate이기도 하다.

실제 `ka10081`의 `base_dt`, continuation, page behavior 또는 price-adjustment semantics가 본 TASK의 가정과 다르게 확인되면 이를 구현 세부사항으로 임의 보정하지 않는다. Pilot으로 진행하지 않고 TASK-003을 `BLOCKED`로 보고하며 다음을 Completion/Interim Report에 기록한다.

- 실제 request 조건
- 민감정보를 제거한 response/response-header evidence
- 예상한 contract와 실제 동작의 차이
- 현재 구현에 미치는 영향
- 필요한 TASK 계약 변경안

계약 변경 승인 없이 실제 API 동작에 맞춰 research/product 의미 또는 traversal contract를 임의 변경하지 않는다.

### Gate 2 — 5종목 Pilot

targetStartDate=2015-01-01로 서로 다른 이력 특성의 5종목을 실행한다. 각 종목은 TARGET_REACHED, HISTORY_EXHAUSTED, ALREADY_SATISFIED 중 하나여야 한다. 설명되지 않은 FAILED는 차단 사유다.

### Gate 3 — Pilot 품질 기준선

5종목별 MIN/MAX date, row count, duplicate, gap, OHLC validity, 기존 데이터 보존, page count, 오류·retry, rerun 결과를 기록한다. 재실행 시 대규모 불필요 API 순회·중복·기존 데이터 손상이 없는지 확인한다.

### Gate 4 — 명시적 Full Universe 승인

Pilot 보고서 후 자동으로 멈춘다. Full universe는 사람이 명시적으로 승인해야 하며 rate-limit 위험, 수정주가, 품질 기준선, survivorship 한계, 장애 복구를 확인해야 한다.

### Gate 5 — Full Universe 품질 기준선

승인 후 실행했다면 종합·종목별 결과와 TARGET_REACHED / HISTORY_EXHAUSTED / ALREADY_SATISFIED / FAILED를 구분해 보고한다. HISTORY_EXHAUSTED는 exhaustion reason별로 추가 집계한다. 실패를 성공으로 숨기지 않는다.
## 15. Acceptance Criteria

모든 항목의 초기 상태는 NOT RUN이다. 재현 가능한 실행 증거가 있을 때만 PASS로 변경한다. 설계·코드 존재만으로 PASS할 수 없다.

| ID | 인수 기준 | 증거 | 상태 |
|---|---|---|---|
| AC-001 | 기본 목표일은 2015-01-01 | 설정·테스트 | NOT RUN |
| AC-002 | 2010 full backfill은 제공하지 않음 | 범위·설정 테스트 | NOT RUN |
| AC-003 | 날짜 범위 기반 backfill | service/client 계약 테스트 | NOT RUN |
| AC-004 | application-level target count/date와 broker page/continuation contract가 분리되며 broker page-size를 검증 없이 임의 상수로 확정하지 않음 | client/service 계약 테스트 + Gate 1 | NOT RUN |
| AC-005 | oldest date 기반 base_dt | unit test | NOT RUN |
| AC-006 | cont-yn/next-key 전달 | client 계약 테스트 | NOT RUN |
| AC-007 | targetStartDate가 비거래일이어도 boundary crossing 기준으로 TARGET_REACHED를 정확히 판정하고 종료 | service boundary test | NOT RUN |
| AC-008 | target 미도달 history 종료를 HISTORY_EXHAUSTED로 처리하고 exhaustion reason을 근거에 따라 기록 | service/state test | NOT RUN |
| AC-009 | 충족 종목 API 호출 없음 | service test | NOT RUN |
| AC-010 | no-progress 감지·종료 | loop 방지 테스트 | NOT RUN |
| AC-011 | invalid code/date/price/OHLC/volume 거부 | validation test | NOT RUN |
| AC-012 | (code, trade_date) 중복 방지 | schema/integration | NOT RUN |
| AC-013 | atomic upsert 및 결정적 conflict | DB integration | NOT RUN |
| AC-014 | 기존 데이터 삭제·재생성 없음 | diff/integration | NOT RUN |
| AC-015 | 변경 OHLCV 처리 테스트 | conflict test | NOT RUN |
| AC-016 | page와 checkpoint atomic commit | rollback test | NOT RUN |
| AC-017 | historical/latest state 분리 | schema/service | NOT RUN |
| AC-018 | 모든 상태 전이 구현 | state-machine test | NOT RUN |
| AC-019 | 중단 후 oldest-date부터 resume | interruption test | NOT RUN |
| AC-020 | rerun 멱등성·불필요 순회 방지 | repeated-run test | NOT RUN |
| AC-021 | transient error bounded retry | retry test | NOT RUN |
| AC-022 | permanent error 무한 retry 없음 | retry 분류 테스트 | NOT RUN |
| AC-023 | rate-limit metadata/최종 오류 기록 | integration/log | NOT RUN |
| AC-024 | upd_stkpc_tp 일관성 및 full 전 검증 | 설정 + 공식/API 증거 | NOT RUN |
| AC-025 | current universe와 survivorship 한계 명시 | run 보고서 | NOT RUN |
| AC-026 | Discovery A1 미생성·미수정 | repository/data 점검 | NOT RUN |
| AC-027 | production strategy/trading 불변 | regression/diff | NOT RUN |
| AC-028 | frontend/e2e/app.spec.js 미수정·미stage·미commit | git status/diff | NOT RUN |
| AC-029 | Gate 순서 강제 | Gate 보고서 | NOT RUN |
| AC-030 | full universe 명시 승인 및 품질 기준선 | 승인 + 최종 보고서 | NOT RUN |

## 16. 자동 테스트

다음을 최소 검증한다.

- request validation, application/broker pagination separation, no-existing-candle initial cursor, base_dt, continuation mapping
- targetStartDate 비거래일 및 target boundary crossing 판정
- HISTORY_EXHAUSTED reason 분류와 UNKNOWN 처리
- page ordering, malformed response, no-progress
- state-machine, atomic upsert, conflict, rollback
- interruption/resume, already-satisfied short-circuit, rerun
- transient/permanent retry 분류
- latest sync regression
- Discovery A1 및 production strategy/trading 경로 비변경

명령·시각·환경·결과를 기록한다. 미실행 테스트는 NOT RUN이며 PASS가 아니다.

## 17. 수동 및 Real API 검증

보고서에 종목·universe 선정 근거, target date, 실제 base_dt, continuation header/page 수, oldest/newest date, row 수, retry/throttle, price option, 최종 state, rerun, 기존 데이터 보존, duplicate 검사를 포함한다. Credential·token·민감 header는 저장하거나 commit하지 않는다.

## 18. 작업 규칙

1. 미실행·미검증 항목은 PASS로 표시하지 않는다.
2. 선행 Gate 전에는 pilot/full run을 실행하지 않는다.
3. Full universe를 자동 승인하지 않는다.
4. 기존 일봉을 삭제·재생성하지 않는다.
5. 목표일 미도달이 history 종료 때문이면 HISTORY_EXHAUSTED로 기록한다.
6. 실제 성공 없이 FAILED를 성공으로 바꾸지 않는다.
7. next-key의 재시작 유효성을 검증 없이 가정하지 않는다.
8. price-adjustment/product-universe 정책을 조용히 변경하지 않는다.
9. Discovery A1과 production strategy/trading을 변경하지 않는다.
10. frontend/e2e/app.spec.js를 수정·복원·stage·commit하지 않는다.
11. 증거를 재현 가능한 형태로 보존한다.
12. 제품·연구 의미 또는 본 TASK의 명시적 계약을 바꾸는 진짜 모호성만 BLOCKED 사유로 취급한다. DB migration 이름, DTO 구성, repository 구현 방식, transaction 구현 세부사항, test fixture 구성 등 본 계약 범위 안의 일반적인 기술 선택은 구현자가 기존 architecture와 최소 변경 원칙에 따라 결정한다.
13. 일반적인 기술 선택을 이유로 product/research architecture 결정을 사용자에게 다시 떠넘기지 않는다.
14. 단, 실제 Kiwoom API 동작이 본 TASK의 broker/traversal 가정과 충돌하는 경우는 일반 기술 선택으로 간주하지 않는다. Gate 1 규칙에 따라 BLOCKED로 보고하고 계약 변경 승인을 기다린다.
15. 기존 architecture와 공개 interface를 불필요하게 광범위하게 refactor하지 않는다. TASK-003 목적 달성에 필요한 최소 변경을 우선한다.

## 19. Completion Report

```
Task ID 및 최종 상태
구현 commit(s), 있는 경우
자동 테스트 명령·결과
1종목 smoke 결과
5종목 pilot 결과
pilot 품질 기준선
full-universe 승인 기록 및 실행 결과, 해당하는 경우
TARGET_REACHED / HISTORY_EXHAUSTED / ALREADY_SATISFIED / FAILED 수
HISTORY_EXHAUSTED reason별 수
실행 전후 row 수 및 최소·최대 거래일
duplicate/gap/OHLC 검증
retry/rate-limit 요약
price-adjustment 검증
universe snapshot 및 survivorship 한계
Discovery A1 미생성 확인
production strategy/trading 불변 확인
frontend/e2e/app.spec.js 보존 및 미stage·미commit 확인
알려진 한계 및 후속 task
```

미실행 Gate는 NOT RUN, 외부 검증 불가 항목은 이유와 함께 BLOCKED 또는 PENDING으로 기록한다.

## 20. Decision Gate

다음 조건을 모두 만족할 때만 TASK-003을 DONE으로 변경한다.

- AC-001~AC-030의 근거 기반 상태가 기록됨
- Gate 0, 1종목 smoke, 5종목 pilot 및 품질 기준선 검증 완료
- current-universe survivorship 한계 기록
- price-adjustment 정책 검증
- Gate 3 완료 후 `FULL-RUN-READY` 상태에서 자동으로 중지하고 명시적 사용자 승인을 기다림
- Full Universe 실행에 대한 명시적 사용자 승인 기록
- 승인된 Full Universe backfill 실행 완료
- Gate 5 Full Universe 품질 기준선 생성 및 검증 완료
- 모든 실패가 숨김없이 보고됨
- Discovery A1과 production strategy/trading 불변
- frontend/e2e/app.spec.js가 사용자의 기존 unstaged 변경으로 보존되고 수정·복원·stage·commit되지 않음

Gate 0~3을 통과했더라도 Full Universe 실행 승인을 기다리는 동안 TASK-003은 `DONE`이 아니다. 이 상태는 `REVIEW` 또는 프로젝트의 동등한 승인 대기 상태로 기록하고 `Full Universe: AWAITING APPROVAL`을 명시한다.
Full Universe 승인 후 실제 backfill과 Gate 5 품질 기준선까지 완료되어야 TASK-003을 `DONE`으로 변경할 수 있다.
이 문서 자체는 구현 완료나 PASS 결과가 아니다. 조건 충족 전 상태는 DRAFT, READY, IN_PROGRESS, REVIEW 또는 BLOCKED로 유지한다.