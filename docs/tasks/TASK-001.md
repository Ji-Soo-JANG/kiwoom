# TASK-001 — Box Boundary Validation C0

**Status:** DONE
**Priority:** P0
**Type:** Research / Validation
**Dependencies:** TASK-000
**Related:** STR-P06-R2
**Baseline:** Architecture Baseline v1

---

## 1. 목적

기존 STR-P06-R2에서 준비된 Box Definition Validation을 이어서 수행한다.

이번 TASK의 목적은 새로운 박스권 탐지기를 구현하는 것이 아니다.

기존에 준비된:

`STR-P06-R2 boundary validation C0 v1`

배치를 사용자가 직접 평가하고, 그 결과를 집계하여 현재 Box Candidate 생성 방식이 사용자가 인식하는 박스권 경계를 어느 정도 재현하는지 검증한다.

검증 결과를 기반으로 다음 중 하나를 결정한다.

1. 현재 후보 생성 규칙을 기반으로 Box Detector v2 구현 진행
2. 후보 생성 규칙 보정 후 추가 검증
3. 박스권 정의 가설 자체 수정 후 재검증

---

# 2. 현재 상태

기존 STR-P06-R2에서 박스권 정의 분류 평가는 완료됐다.

그러나 기존 Definition Validation Batch B에서는 사용자 라벨 20건이 확정됐음에도 후보 선택과 최종 경계가 저장되지 않아 실제 경계 정확도를 검증할 수 없었다.

이를 보완하기 위해 다음 계약이 이미 구현됐다.

- `CANDIDATE`
- `MANUAL`
- `NO_SUITABLE_CANDIDATE`

긍정 라벨:

- `VALID_BOX`
- `PARTIAL_BOX`

은 후보를 선택하거나 직접 시작/종료 경계를 지정해야 한다.

부정/판단불가 라벨:

- `NOT_BOX`
- `INSUFFICIENT_DATA`
- `DATA_QUALITY_ISSUE`

은 `NO_SUITABLE_CANDIDATE`를 명시해야 한다.

---

# 3. 기존 C0 Validation Batch

배치:

`STR-P06-R2 boundary validation C0 v1`

구성:

- 총 12종목
- 후보 36개
- 기준일 3개
- 기준일별 4종목
- 기준일:
    - 2025-11-28
    - 2026-04-30
    - 2026-07-31

표본은 각 기준일 이전 120거래일의 강건 가격 범위를 기준으로 사분위별로 추출됐다.

기존 배치 1·2의 50종목과 중복되지 않는다.

ETF/ETN 등 비주식 상품은 제외됐다.

미래 가격, 미래 수익률, 급등 여부 및 사용자 라벨은 표본 선정에 사용하지 않는다.

---

# 4. 사용자 평가

이번 TASK의 핵심 작업이다.

사용자는 Box Evaluation Workbench에서 C0 배치의 12개 종목을 순차 평가한다.

각 종목에 대해 다음을 판단한다.

### A. 박스권으로 인정

`VALID_BOX`

명확하게 박스권이라고 판단한다.

경계는 반드시 다음 중 하나로 결정한다.

`CANDIDATE`

제시된 후보의 시작일/종료일을 그대로 인정한다.

또는:

`MANUAL`

박스권은 존재하지만 후보 경계가 정확하지 않아 사용자가 시작일/종료일을 직접 수정한다.

---

### B. 부분적으로 박스권

`PARTIAL_BOX`

박스권 성격은 존재하지만 완전한 박스권으로 보기 어렵거나 경계가 모호하다.

반드시 설명(comment)을 남긴다.

경계는 `CANDIDATE` 또는 `MANUAL`로 지정한다.

---

### C. 박스권 아님

`NOT_BOX`

사용자가 판단하기에 해당 구간에 박스권이 없다.

`NO_SUITABLE_CANDIDATE`를 선택한다.

---

### D. 판단 자료 부족

`INSUFFICIENT_DATA`

현재 데이터만으로 박스권 여부를 판단하기 어렵다.

`NO_SUITABLE_CANDIDATE`를 선택하고 이유를 기록한다.

---

### E. 데이터 품질 문제

`DATA_QUALITY_ISSUE`

가격 데이터 이상 등으로 평가 자체가 신뢰할 수 없다.

`NO_SUITABLE_CANDIDATE`를 선택한다.

---

# 5. 평가 원칙

사용자는 후보 생성 알고리즘을 맞히려고 평가하지 않는다.

판단 기준은:

> “내가 이 차트를 실제로 봤을 때 이 구간을 박스권이라고 인정하는가?”

이다.

후보와 의견이 다르면 후보에 맞추지 않고 `MANUAL`을 사용한다.

적합한 후보가 없으면 억지로 가장 가까운 후보를 선택하지 않는다.

`NO_SUITABLE_CANDIDATE`를 사용한다.

---

# 6. 평가 완료 조건

12건 모두 확정되어야 한다.

마지막 평가 확정 후 해당 batch 상태는:

`CLOSED`

가 되어야 한다.

긍정 라벨인:

- VALID_BOX
- PARTIAL_BOX

에서 시작일 또는 종료일이 NULL인 평가가 존재해서는 안 된다.

---

# 7. 결과 집계

12건 평가 완료 후 다음 지표를 계산한다.

### 7.1 Boundary Decision Distribution

- CANDIDATE 건수/비율
- MANUAL 건수/비율
- NO_SUITABLE_CANDIDATE 건수/비율

---

### 7.2 Candidate Acceptance Rate

VALID_BOX + PARTIAL_BOX 중:

`CANDIDATE / (CANDIDATE + MANUAL)`

을 계산한다.

현재 후보 경계를 그대로 인정한 비율이다.

---

### 7.3 Manual Boundary Difference

MANUAL 평가에 대해 후보 대비:

- 시작 거래일 차이
- 종료 거래일 차이

를 계산한다.

가능하면 calendar day가 아니라 trading-day 기준 차이를 우선 사용한다.

---

### 7.4 Boundary IoU

후보 구간과 사용자 확정 구간의 Intersection over Union을 계산한다.

개념:

`겹치는 거래일 수 / 두 구간의 합집합 거래일 수`

MANUAL 평가에서 후보가 사용자 경계를 어느 정도 재현하는지 측정한다.

---

### 7.5 Candidate Type Performance

후보 유형별:

- 수용
- 수정
- 거부

건수와 비율을 계산한다.

특정 candidate type이 반복적으로 실패하는지 확인한다.

---

### 7.6 Negative Case Analysis

다음 라벨에 대해:

- NOT_BOX
- INSUFFICIENT_DATA
- DATA_QUALITY_ISSUE

후보가 왜 적합하지 않았는지 확인한다.

---

# 8. 결과 해석 원칙

12건은 통계적으로 최종 전략 성능을 증명하기 위한 표본이 아니다.

이번 검증 목적은:

1. 후보 생성 방식이 사용자 경계를 합리적으로 제안하는지
2. 후보 경계가 어떤 방향으로 반복적으로 어긋나는지
3. 박스가 없는 경우에도 후보를 과도하게 생성하는지
4. 현재 Box Definition을 코드로 구현할 만큼 경계가 명확한지

를 판단하는 것이다.

따라서 단순 Candidate Acceptance Rate 하나만으로 합격/실패를 결정하지 않는다.

---

# 9. Decision Gate

평가 결과를 기반으로 ChatGPT와 사용자가 다음 중 하나를 결정한다.

### DECISION-A — Detector 구현 진행

후보가 사용자 정의와 충분히 일치하고 반복적인 구조적 오류가 크지 않다.

다음 TASK에서 Box Detector v2 구현으로 진행한다.

### DECISION-B — Candidate Generator 보정

박스 정의 자체는 유효하지만 후보 시작/종료 경계가 반복적으로 특정 방향으로 어긋난다.

후보 생성 규칙을 보정하고 별도 validation batch를 수행한다.

### DECISION-C — Definition 재검토

후보 문제가 아니라 사용자가 박스권이라고 인식하는 기준 자체가 현재 정의와 일치하지 않는다.

`user-box-definition-v1`을 수정하고 다시 검증한다.

### 최종 결정 — DECISION-A

C0 Boundary Validation 결과, 양성 라벨(`VALID_BOX` / `PARTIAL_BOX`) 4건 모두 최종 시작일 및 종료일이 `NARROW` 후보와 일치했다.

이 중 2건은 `CANDIDATE`로 직접 선택되었으며, 나머지 2건은 `MANUAL`로 저장되었으나 실제 입력된 시작일 및 종료일은 `NARROW` 후보와 완전히 동일했다.

따라서 Candidate Acceptance Rate는 형식상 50.0%이지만, 경계 날짜 기준으로는 양성 사례 4건 모두 `NARROW` 후보와 일치했으며 반복적인 경계 오차는 관찰되지 않았다.

또한 `NOT_BOX` 7건과 `INSUFFICIENT_DATA` 1건은 모두 `NO_SUITABLE_CANDIDATE`로 평가되었다.

이에 따라 **DECISION-A — 조건부 Detector 구현 진행**으로 최종 결정한다.

- `NARROW` 후보 생성 방식을 Box Detector v2의 초기 boundary baseline으로 채택한다.
- C0는 12개 표본을 대상으로 한 sanity check이며 통계적인 탐지 성능 검증으로 간주하지 않는다.
- `EXPANDED` 및 `CONNECTED` 후보의 유효성은 이번 검증에서 확인되지 않았다.
- 후속 TASK에서는 `NARROW` boundary 규칙을 기반으로 Box Detector v2를 독립적으로 구현하고 검증한다.
- 급락 → 박스권 → 회복 → 현재 눌림목으로 이어지는 전체 전략과의 통합은 후속 검증 이후 별도 TASK에서 수행한다.
---

# 10. Out of Scope

이번 TASK에서는 다음을 구현하지 않는다.

- Box Detector v2
- 실제 Strategy 변경
- CurrentRecoveryPullbackStrategy 변경
- 급락 탐지
- 회복 탐지
- 눌림목 탐지
- 전체 패턴 조합
- 진입 조건
- 손절 조건
- 수익률 평가
- Backtest 전략 변경
- Walk Forward 전략 변경
- PAPER 주문
- LIVE 주문
- Position sizing
- Market regime
- Strategy promotion

---

# 11. 코드 변경 원칙

기존 C0 평가 기능이 정상 동작한다면 새로운 production 코드를 작성하지 않는다.

이번 TASK의 핵심 산출물은 사용자 평가 데이터와 분석 결과다.

평가 과정에서 C0 Workbench 또는 저장 계약의 결함이 발견된 경우:

1. 즉시 우회 구현하지 않는다.
2. 문제를 기록한다.
3. 평가 신뢰성에 영향을 주는지 판단한다.
4. 필요한 경우 TASK-001을 BLOCKED로 전환한다.
5. 수정 범위를 별도 하위 작업 또는 후속 TASK로 분리한다.

---

# 12. Acceptance Criteria

### AC-001-01
`STR-P06-R2 boundary validation C0 v1` 배치의 12건이 모두 사용자 평가 완료된다.

### AC-001-02
최종 batch 상태가 `CLOSED`다.

### AC-001-03
VALID_BOX / PARTIAL_BOX 평가에는 확정된 시작일과 종료일이 존재한다.

### AC-001-04
모든 평가에 `CANDIDATE`, `MANUAL`, `NO_SUITABLE_CANDIDATE` 중 적절한 boundary decision이 존재한다.

### AC-001-05
Boundary Decision Distribution이 집계된다.

### AC-001-06
Candidate Acceptance Rate가 집계된다.

### AC-001-07
MANUAL 평가의 시작/종료 경계 차이가 분석된다.

### AC-001-08
가능한 평가에 대해 Boundary IoU가 계산된다.

### AC-001-09
Candidate Type별 수용/수정/거부 결과가 집계된다.

### AC-001-10
NOT_BOX / INSUFFICIENT_DATA / DATA_QUALITY_ISSUE 사례가 별도로 검토된다.

### AC-001-11
빈 후보 키 또는 양쪽 경계가 NULL인 긍정 라벨이 0건이다.

### AC-001-12
결과를 근거로 DECISION-A/B/C 중 하나가 명시적으로 결정된다.

---

# 13. 산출물

TASK 완료 시 다음을 남긴다.

`docs/tasks/TASK-001.md`

그리고 결과 문서:

`docs/proposals/STR-P06-R2/boundary-validation-c0-result.md`

결과 문서에는 최소 다음을 포함한다.

- Batch 정보
- 평가 완료 건수
- 라벨 분포
- Boundary Decision 분포
- Candidate Acceptance Rate
- Manual boundary difference
- Boundary IoU
- Candidate type 분석
- 실패 사례
- 데이터 품질 문제
- 결론
- DECISION-A/B/C
- 다음 TASK 권고

---

# 14. 완료 판정

TASK-001은 단순히 12건을 클릭했다고 DONE이 되지 않는다.

다음 흐름이 모두 완료되어야 한다.

`사용자 평가 → 결과 집계 → 결과 해석 → Decision Gate → ChatGPT 검수`

ChatGPT가 결과를 검수하고 DECISION-A/B/C를 확정한 뒤 TASK-001을 DONE으로 변경한다.