# TASK-002 — Box Formation Research Infrastructure & Discovery

## 0. Metadata

- Task ID: TASK-002
- Title: Box Formation Research Infrastructure & Discovery
- Status: DONE
- Type: Research / Validation / UI
- Priority: High
- Depends On:
    - TASK-001 — Box Boundary Validation C0
    - STR-P06-R2 Boundary Validation C0
- Related Requirements:
    - STR-P06
    - DAT-PIT / reproducible research requirements
- Architecture Baseline: Architecture Baseline v1
- Primary Area: `research/boxevaluation`
- Strategy Integration: Not included
- Detector Implementation: Not included

---

## 1. Purpose

본 TASK의 목적은 Box Detector v2를 구현하는 것이 아니다.

향후 Box Detector v2가 사용할 **Box Formation Rule v1**을 데이터에 근거하여 결정할 수 있도록 연구 인프라를 구축한다.

범위에는 다음이 포함된다.

- 새로운 Box 후보 표본 생성
- 미래정보가 차단된 Blind Formation Evaluation
- `BOX / NOT_BOX / UNCERTAIN` 라벨 수집
- BOX 판정 후 Boundary Quality 평가
- 차트에서 기간 및 가격 Zone 직접 수정
- 반복 평가를 위한 고속 UX
- 즉시 저장 및 이어하기
- 후보 feature와 사용자 라벨 비교
- Discovery → Boundary → Holdout 연구 기반

특정 Box 판정 임계값과 운영용 Box Detector는 본 TASK에서 확정하거나 구현하지 않는다.

---

## 2. Background

TASK-001 C0 Boundary Validation에서 양성 라벨 4건 모두 최종 날짜 경계가 `NARROW` 후보와 일치했다.

형식상 Candidate Acceptance Rate는 50.0%였으나 `MANUAL` 2건도 실제 입력 날짜가 `NARROW` 후보와 동일했다.

따라서 `NARROW`는 초기 boundary baseline으로 사용할 수 있다.

단, C0는 12개 표본의 sanity check이며 통계적 탐지 성능 검증이 아니다.

또한 다음 사항은 검증되지 않았다.

- `NARROW` 후보의 존재가 Box 자체를 의미하지 않는다.
- 후보 생성기가 모든 Box를 탐지한다고 검증되지 않았다.
- `EXPANDED` 후보는 검증되지 않았다.
- `CONNECTED` 후보는 검증되지 않았다.
- C0만으로 production Box Detector를 확정할 수 없다.

따라서 Detector 구현보다 먼저 다음 질문을 연구해야 한다.

> 사람이 실제 가격 차트를 보았을 때 어떤 가격 구조를 Box라고 판단하는가?

TASK-002는 이 질문에 답하기 위한 연구 인프라와 Discovery 과정에 집중한다.

---

## 3. Related Requirements and Design Decisions

### 3.1 Related Requirements

본 TASK는 다음 설계 및 요구사항과 연관된다.

- STR-P06 — stable price / Box 구조
- Point-in-Time 데이터 원칙
- 재현 가능한 research dataset
- future information 차단
- strategy parameter/version 추적
- explainable research process

### 3.2 TASK-001 Decision

TASK-001의 최종 결정은 **DECISION-A — Conditional Detector Implementation**이다.

여기서 의미하는 것은 다음으로 제한한다.

- `NARROW` candidate generation을 초기 Boundary Candidate Baseline으로 채택한다.
- C0 결과를 전체 candidate generator의 통계적 검증으로 해석하지 않는다.
- `EXPANDED`, `CONNECTED`를 baseline으로 채택하지 않는다.
- Box 자체의 판정 기준은 별도로 연구한다.
- Detector 구현 전에 Box Formation Rule을 검증한다.

### 3.3 Research Layer Separation

Box 연구와 전체 전략 연구를 다음과 같이 분리한다.

```text
Box Formation
    ↓
Box Lifecycle
    ↓
Volume Event
    ↓
Full Strategy
(plunge → box → volume event → recovery → current pullback)
```

TASK-002는 첫 번째 단계인 **Box Formation**에 집중한다.

---

## 4. Scope

본 TASK의 구현 범위는 다음과 같다.

### 4.1 Research Dataset

- TASK-001 C0와 분리된 새로운 Discovery Dataset 생성
- dataset / batch 식별
- stratified sampling 지원
- cutoffDate 기반 Point-in-Time snapshot
- candidate feature snapshot 보존
- Discovery / Boundary / Holdout dataset 구분 지원

### 4.2 Blind Formation Evaluation

사용자가 미래정보 및 시스템 계산 결과를 보지 않고 가격 차트만으로 다음 중 하나를 판단할 수 있어야 한다.

```text
BOX
NOT_BOX
UNCERTAIN
```

### 4.3 Boundary Evaluation

`BOX` 판정 후에만 시스템의 `NARROW` Boundary Candidate를 공개한다.

사용자는 다음을 평가할 수 있어야 한다.

- 제안 기간 승인
- 제안 기간 수정
- 제안 Core Range / Zone 승인
- 제안 Core Range / Zone 수정

### 4.4 Chart-first Editing

기간 및 가격 Zone 수정은 차트 직접 조작을 기본으로 한다.

- start/end boundary drag
- candle click 기반 기간 선택
- trading-day snap
- support/resistance zone drag/resize
- wheel zoom
- drag/pan
- reset view

### 4.5 Evaluation Workflow

- 키보드 평가
- 이전/다음 후보 이동
- 즉시 저장
- resume
- 기존 평가 수정
- batch progress 표시

### 4.6 Research Analysis Support

다음 정보를 함께 분석할 수 있도록 저장한다.

- 사용자 Formation Label
- system Boundary Candidate
- 사용자 수정 Boundary
- Core Range / Zone
- candidate features
- dataset / batch metadata
- cutoffDate
- evaluation timestamp
- 수정 이력

---

## 5. Out of Scope

다음은 TASK-002의 범위가 아니다.

- production Box Detector v2 구현
- Box Detector를 운영 전략에 연결
- 자동매매 전략 통합
- PAPER 주문 로직 변경
- LIVE 주문 로직 변경
- 거래량 패턴 판정
- 급락 패턴 판정
- 회복 패턴 판정
- 현재 눌림목 판정
- 수익률 기반 Box 최적화
- 미래 가격을 이용한 Box 판정
- production duration threshold 확정
- production Box width threshold 확정
- production zone width threshold 확정
- production swing threshold 확정
- breakout confirmation threshold 확정
- breakdown confirmation threshold 확정
- Box Lifecycle Rule 확정
- Volume Event Rule 확정
- `EXPANDED` 또는 `CONNECTED`를 production baseline으로 채택

TASK-002는 **Box Formation Research Infrastructure + Discovery**에만 집중한다.

---

## 6. Detailed Research / Implementation Design

### 6.1 Conceptual Box Definition

Box의 개념적 정의는 다음과 같다.

> 충분한 기간 동안 좁은 Core Range 안에서 가격이 움직이며, 반복적인 상승·하락 및 지지·저항 반응을 통해 일정한 가격 영역이 형성된 상태.

이는 개념적 정의이며 수치 임계값이 아니다.

다음 값은 TASK 시작 시 확정하지 않는다.

- minimum duration
- maximum Box width
- support zone width
- resistance zone width
- swing count
- swing density
- excursion tolerance
- breakout confirmation
- breakdown confirmation
- breakout depth
- reentry period

Codex는 구현 편의를 위해 이러한 값을 임의의 production rule로 확정해서는 안 된다.

---

### 6.2 Box Formation vs Box Lifecycle

#### Box Formation

Formation은 다음 질문을 다룬다.

> 언제 처음으로 해당 가격 구간을 Box라고 인정할 수 있는가?

연구 가능한 관측 feature에는 다음이 포함될 수 있다.

- duration
- Core Range width
- support/resistance structure
- price distribution
- directional changes
- swing structure
- boundary interaction
- excursion

#### Box Lifecycle

이미 형성된 Box는 Formation 조건을 매 시점 다시 만족할 필요가 없다.

예를 들어 Box 형성 이후 최근 가격이 한 방향으로 움직이더라도 유효한 경계를 벗어나지 않았다면 기존 Box가 유지될 수 있다.

따라서 다음을 분리한다.

```text
Formation Condition ≠ Maintenance Condition
```

최근 swing 감소만으로 기존 Box를 무효화해서는 안 된다.

TASK-002의 주 대상은 Formation이며 Lifecycle 수치 규칙은 후속 TASK에서 연구한다.

---

### 6.3 Box Geometry Principles

#### 6.3.1 Horizontal-only Box 금지

Box는 완전히 수평일 필요가 없다.

완만한 우상향 또는 우하향 구조라도 좁은 Core Range가 유지된다면 Box일 가능성이 있다.

따라서 다음과 같은 단순 production rule을 도입하지 않는다.

```text
trendSlope > X
→ NOT_BOX
```

`trendSlope`는 연구 feature가 될 수 있지만 단독 탈락 조건으로 사용하지 않는다.

#### 6.3.2 Full Range Traversal 불필요

가격이 다음과 같이 정확히 움직일 필요는 없다.

```text
lower
  ↓
upper
  ↓
lower
  ↓
upper
```

상단과 하단의 완전한 왕복 횟수를 절대 Box 조건으로 사용하지 않는다.

#### 6.3.3 Formation 단계의 반복 구조

새 Box 형성에서는 단 하나의 큰 상승/하락 wave와 반복적인 가격 움직임을 구분할 필요가 있다.

그러나 다음과 같은 고정 규칙을 사전에 확정하지 않는다.

```text
swingCount >= N
```

기간이 길어질수록 swing 수 자체도 증가할 수 있으므로 duration과 swing structure/density의 관계를 연구한다.

---

### 6.4 Core Range

Box 경계는 단순한 다음 값으로 정의하지 않는다.

```text
lowest low ~ highest high
```

Core Range는 가격이 반복적으로 지지 또는 저항을 받은 핵심 가격 영역을 의미한다.

```text
Box
├─ Core Range
│  ├─ Lower Support Zone
│  └─ Upper Resistance Zone
├─ Observed Range
│  ├─ Observed Low
│  └─ Observed High
├─ Boundary Evidence
└─ Outside Events
```

일시적인 wick 또는 excursion만으로 Core Range를 자동 확대하지 않는다.

Core Range와 Observed Range를 구분하여 연구할 수 있어야 한다.

---

### 6.5 Support / Resistance Zone

Box 경계는 단일 price line이 아니라 zone으로 취급한다.

동일 가격을 정확하게 반복 터치할 필요는 없다.

인접 가격대에서 반복적으로 지지 또는 저항 반응이 나타난 경우 하나의 zone으로 해석할 수 있다.

다음 두 zone을 구분한다.

```text
Lower Support Zone
Upper Resistance Zone
```

두 zone의 폭은 동일할 필요가 없다.

적절한 zone width와 interaction 기준은 연구 결과를 통해 결정하며 TASK-002에서 production threshold로 확정하지 않는다.

---

### 6.6 Box Width Principle

목표 Box는 상대적으로 가격 변동이 작은 안정적인 가격 영역이다.

종목 자체의 변동성이 크다는 이유만으로 다음과 같은 넓은 가격 범위를 정상 Box로 허용해서는 안 된다.

```text
-20% ~ +20%
```

ATR 또는 종목 변동성으로 지나치게 넓은 범위를 정상화하지 않는다.

Box Width는 기본적으로 **절대 가격 변동률 개념**을 보존하여 연구한다.

단, 다음 값은 TASK-002에서 결정하지 않는다.

```text
maximumBoxWidthPct = ?
```

---

### 6.7 Lower Breakdown Principle

기존 Box의 하단 경계가 실제로 붕괴된 경우 기존 Box는 종료된다.

다만 다음 중 무엇을 실제 `LOWER_BREAKDOWN` 확인 기준으로 사용할지는 Lifecycle 연구 대상으로 남긴다.

- intraday low 이탈
- close 이탈
- 침범 깊이
- 연속 이탈 일수
- 이탈 후 회복 여부

즉 다음 개념은 유지한다.

```text
Confirmed Lower Breakdown
→ Existing Box Ends
```

그러나 confirmation 숫자는 TASK-002에서 확정하지 않는다.

작은 wick 하나만으로 임의의 production breakdown rule을 만들지 않는다.

---

### 6.8 Box Reformation After Lower Breakdown

하단 붕괴 이후 새로운 가격대에서 다시 안정화되더라도 과거 Box를 소급하여 확장하지 않는다.

예:

```text
BOX A: 100 ~ 110
       ↓
Lower Breakdown
       ↓
New Stabilization
       ↓
BOX B: 95 ~ 105
```

이를 다음과 같이 수정해서는 안 된다.

```text
BOX A: 95 ~ 110
```

Box A를 종료하고 이후 Point-in-Time 정보에 따라 Box B가 새롭게 형성되었다고 판단해야 한다.

새 Box는 기존 Box와 가격 범위가 일부 겹칠 수 있다.

새 Box의 시작점을 미래정보를 사용하여 과거로 소급 확정하지 않는다.

---

### 6.9 Upper Excursion and Breakout

상단 경계와 하단 경계는 비대칭적으로 처리할 수 있다.

상단을 일시적으로 이탈했더라도 가격이 다시 Box로 진입하고 안정된다면 동일 Box가 유지될 수 있다.

개념적 Lifecycle 상태는 다음과 같다.

```text
IN_BOX
UPPER_EXCURSION
REENTRY
UPPER_BREAKOUT
LOWER_BREAKDOWN
```

상단 돌파 후 상승 움직임이 지속되는 경우 `UPPER_BREAKOUT`으로 기존 Box가 종료될 수 있다.

그러나 다음 수치는 TASK-002에서 결정하지 않는다.

- upper break depth
- breakout persistence
- reentry days
- post-reentry stability
- breakout confirmation days

TASK-002에서는 향후 연구에 필요한 원천 관측값을 보존할 수 있지만 Lifecycle Rule 자체를 확정하지 않는다.

---

### 6.10 Volume Event Separation

거래량은 Box 자체의 정의에서 제외한다.

전체 전략에서는 다음 구조가 중요할 수 있다.

```text
Box Formation
    ↓
Volume Expansion
    ↓
Upper Excursion
    ↓
Reentry
```

그러나 거래량 이벤트가 아직 발생하지 않았다는 이유로 정상적인 Box를 `NOT_BOX`로 판단해서는 안 된다.

TASK-002 Formation Evaluation은 가격 구조를 대상으로 한다.

거래량 연구는 별도 TASK에서 수행한다.

---

### 6.11 Point-in-Time Evaluation

평가 시점 이후 정보를 사용해서는 안 된다.

후보 이전 가격 컨텍스트는 사용자에게 제공할 수 있지만 `cutoffDate` 이후 가격은 렌더링하지 않는다.

```text
Past Context
    ↓
Candidate / Formation Context
    ↓
cutoffDate
    ↓
[ FUTURE — NOT RENDERED ]
```

과거 컨텍스트 길이를 하나의 production rule로 확정하지 않는다.

사용자가 Formation 맥락을 판단할 수 있을 정도의 과거 데이터는 제공하되 미래정보는 차단한다.

---

### 6.12 Blind Formation Evaluation

1차 Formation 판단에서는 다음 정보를 숨긴다.

- 종목명 / symbol (가능한 범위)
- cutoffDate 이후 가격
- 거래량
- duration 계산값
- width 계산값
- swing 계산값
- candidate feature
- NARROW boundary
- system prediction
- system Box 여부

사용자는 가격 구조만 보고 판단한다.

---

### 6.13 Evaluation Procedure

#### STEP 1 — Blind Formation Classification

사용자는 다음 중 하나를 선택한다.

```text
Q. 이 가격 구간을 박스권이라고 판단합니까?

[ BOX ]
[ NOT_BOX ]
[ UNCERTAIN ]
```

`NOT_BOX` 또는 `UNCERTAIN`은 일반적인 경우 추가 Boundary 입력 없이 즉시 완료할 수 있어야 한다.

초기 Discovery에서는 NOT_BOX 이유 카테고리를 필수 입력으로 요구하지 않는다.

선택적인 free note는 허용할 수 있다.

#### STEP 2 — Boundary Evaluation

STEP 1에서 `BOX`를 선택한 경우에만 `NARROW` Boundary Candidate를 공개한다.

사용자는 다음 두 가지를 독립적으로 평가한다.

1. Proposed Date Interval
2. Proposed Core Range / Support-Resistance Zones

제안이 적절하면 그대로 승인할 수 있다.

부적절하면 차트에서 수정할 수 있다.

이를 통해 다음 두 품질을 분리하여 분석한다.

```text
Box Classification Quality
Boundary Quality
```

`NOT_BOX` 또는 `UNCERTAIN`에는 Boundary 수정을 요구하지 않는다.

---

### 6.14 Chart-first Boundary Editing

평가 원칙은 다음과 같다.

> 판단은 차트를 보면서 하고, 입력도 최대한 차트에서 끝낸다.

date picker와 text input을 주 조작 방식으로 사용하지 않는다.

#### Period Editing

다음 조작을 지원한다.

- start handle drag
- end handle drag
- 시작 candle 클릭 → 종료 candle 클릭
- 실제 trading-day candle로 자동 snap
- 현재 선택 범위 표시
- 변경 취소

제안 기간이 맞다면 `Enter` 한 번으로 승인할 수 있어야 한다.

date picker 또는 text field는 필요할 경우 보조 수단으로만 제공한다.

#### Zone Editing

다음 zone을 차트에서 직접 수정할 수 있어야 한다.

```text
Lower Support Zone
Upper Resistance Zone
```

기본 조작:

- drag
- resize
- 현재 zone 시각화
- 변경 취소

숫자 입력 form은 보조 기능으로만 사용한다.

---

### 6.15 High-throughput Evaluation UX

반복 평가를 전제로 키보드 중심 workflow를 제공한다.

```text
1     = BOX
2     = NOT_BOX
3     = UNCERTAIN
←     = Previous
→     = Next
Enter = Confirm / Accept
Esc   = Cancel Current Edit
```

마우스 버튼도 함께 제공한다.

#### UX-001 — Fast Negative Evaluation

`NOT_BOX`는 일반적인 경우 키 입력 한 번으로 저장하고 다음 후보로 이동할 수 있어야 한다.

#### UX-002 — Fast Uncertain Evaluation

`UNCERTAIN`도 일반적인 경우 키 입력 한 번으로 완료할 수 있어야 한다.

#### UX-003 — Minimal BOX Workflow

BOX이며 제안 Boundary가 적절한 경우 최소한의 추가 입력으로 완료할 수 있어야 한다.

#### UX-004 — Navigation

이전 후보로 돌아가 기존 판단을 수정할 수 있어야 한다.

#### UX-005 — Chart Interaction

차트는 최소한 다음을 지원한다.

- mouse wheel zoom
- drag/pan
- reset view
- stable layout
- candidate/context 구분
- hover candle date / OHLC
- trading-day snapping

평가마다 modal → confirm → save → next가 반복되는 구조는 피한다.

---

### 6.16 Persistence and Resume

평가 결과는 입력 또는 확정 시 즉시 저장한다.

다음을 지원한다.

- browser close 후 데이터 보존
- reload 후 데이터 보존
- current batch resume
- 마지막 미완료 후보부터 이어하기
- 기존 평가 reopen
- 기존 평가 수정
- 수정 timestamp 또는 변경 이력 보존

진행률을 표시한다.

예:

```text
17 / 20
```

---

### 6.17 Research Dataset Strategy

TASK-002에서는 기존 C0를 Discovery Dataset으로 사용하지 않는다.

연구 구조는 다음과 같다.

```text
Dataset A — Discovery Set
        ↓
Feature / Label Analysis
        ↓
Dataset B — Boundary Set
        ↓
Box Formation Rule v1 Candidate
        ↓
Dataset C — Holdout Set
        ↓
Existing C0 — Regression Check
```

#### Dataset A — Discovery

새로운 표본으로 Box 판단 구조를 탐색한다.

#### Dataset B — Boundary

BOX와 NOT_BOX 판단이 갈리는 decision-boundary 영역을 집중적으로 조사한다.

#### Dataset C — Holdout

Rule v1 후보 결정에 사용하지 않은 새로운 표본으로 검증한다.

#### Existing C0

TASK-001에서 사용한 12개 표본은 Discovery에 재사용하지 않고 Regression / Sanity Check 용도로 보존한다.

---

### 6.18 Discovery Sampling

처음부터 100~200개를 무작정 평가하지 않는다.

초기 계획은 다음과 같은 adaptive batch 방식으로 한다.

```text
A1 ≈ 20
    ↓
Analysis
    ↓
A2 ≈ 15
    ↓
Analysis
    ↓
A3 only if needed
```

정확한 batch size는 research configuration으로 조정할 수 있다.

Discovery 전체는 초기에는 약 50개 이내를 목표 범위로 삼되 필요한 정보가 충분히 확보되면 불필요한 추가 라벨링을 하지 않는다.

---

### 6.19 Stratified Sampling

완전 랜덤 sampling만 사용하지 않는다.

예를 들어 다음 차원을 조합하여 다양성을 확보한다.

```text
Duration
├─ Short
├─ Medium
└─ Long

Width
├─ Narrow
├─ Medium
└─ Wide
```

또한 가능한 범위에서 다음을 분산한다.

- symbol
- cutoffDate
- market regime / period
- price level
- candidate characteristics

중요:

> Sampling Bucket은 Box 판정 Threshold가 아니다.

Sampling을 위해 사용한 bucket boundary를 production Box Rule로 사용해서는 안 된다.

---

### 6.20 Adaptive Research

Discovery 결과를 분석하여 BOX와 NOT_BOX가 명확하게 분리되는 영역에 추가 라벨링을 집중하지 않는다.

다음과 같은 영역을 우선 조사한다.

```text
BOX
 ↕
UNCERTAIN / Decision Boundary
 ↕
NOT_BOX
```

목적은 최대한 많은 표본을 라벨링하는 것이 아니라 **최소한의 라벨링으로 Formation Rule을 설명할 수 있는 근거를 찾는 것**이다.

---

### 6.21 Candidate Features

Candidate feature는 정답 규칙이 아니라 사용자 라벨과 가격 구조의 관계를 분석하기 위한 관측값이다.

#### Formation Features

```text
duration
coreRangeWidthPct
```

#### Structure Features

```text
swingCount
swingDensity
directionalPersistence
priceDistribution
trendSlope
```

`trendSlope`는 feature일 뿐 단독 탈락 규칙이 아니다.

#### Zone Features

```text
supportZoneWidth
resistanceZoneWidth
supportInteraction
resistanceInteraction
```

#### Excursion Features

```text
upperExcursionCount
lowerExcursionCount
upperExcursionDepth
lowerExcursionDepth
closeOutlierCount
wickOutlierCount
maxConsecutiveCloseOutliers
maxUpsideDeviationPct
maxDownsideDeviationPct
```

#### Lifecycle-oriented Observations

향후 연구를 위해 필요한 경우 다음 원천 관측값을 보존할 수 있다.

```text
reentryDays
postReentryStability
upperBreakDepth
lowerBreakDepth
```

이 값들로 Lifecycle Rule을 결정하는 것은 TASK-002의 범위가 아니다.

Feature를 많이 생성하는 것 자체가 목적이 아니다.

---

### 6.22 NARROW Candidate Role

TASK-001 결과에 따라 `NARROW`는 유지한다.

단, 의미는 다음으로 제한한다.

> **NARROW = Box Detector가 아니라 초기 Boundary Candidate Baseline**

따라서 다음 관계는 성립하지 않는다.

```text
NARROW Candidate Exists
≠
BOX
```

`NARROW`는 Blind Formation Classification이 완료된 뒤 BOX 후보에 대해서만 공개한다.

`EXPANDED`와 `CONNECTED`는 검증된 baseline으로 취급하지 않는다.

---

### 6.23 Data Integrity and Reproducibility

동일한 원천 데이터와 동일한 cutoff 기준으로 연구 결과를 재생성할 수 있어야 한다.

가능한 범위에서 다음을 보존한다.

- candidate ID
- dataset ID
- batch ID
- symbol 또는 blind evaluation용 내부 식별자
- timeframe
- cutoffDate
- source data snapshot/version
- candidate generation method/version
- user Formation Label
- evaluation timestamp
- proposed date boundary
- modified date boundary
- proposed support/resistance zones
- modified support/resistance zones
- modification history
- candidate feature snapshot

미래 가격 또는 평가 시점 이후에만 알 수 있는 값을 Blind Evaluation 입력이나 해당 시점 candidate feature로 사용해서는 안 된다.

---

### 6.24 Formation / Lifecycle Boundary

TASK-002에서 다음과 같은 Lifecycle 관련 원천 가격 관측값을 저장하는 것은 허용한다.

- upper temporary excursion
- upper breakout candidate
- reentry observation
- lower breakdown observation
- later stabilization observation

그러나 다음 값을 production rule로 결정해서는 안 된다.

```text
upperBreakDepth
breakoutConfirmationDays
reentryDays
postReentryStability
lowerBreakDepth
lowerBreakdownConfirmationDays
```

Formation과 Lifecycle을 서로 다른 연구 질문으로 유지한다.

---

## 7. Acceptance Criteria

### AC-001 — New Research Dataset

TASK-001 C0와 분리된 새로운 Box Formation Research Dataset을 생성할 수 있어야 한다.

- 새로운 candidate ID를 가진다.
- dataset / batch를 식별할 수 있다.
- 기존 C0 표본을 Discovery 데이터로 재사용하지 않는다.
- 동일 조건에서 재현 가능한 candidate generation 정보를 보존한다.

### AC-002 — Point-in-Time Safety

각 후보는 명시적인 `cutoffDate`를 가져야 한다.

- `cutoffDate` 이후 가격 데이터는 Blind Evaluation 화면에 표시하지 않는다.
- `cutoffDate` 이후 데이터를 candidate feature 계산에 사용하지 않는다.
- 미래정보가 연구 결과에 유입되지 않아야 한다.

### AC-003 — Blind Classification

Blind Formation Classification 전에 다음 정보를 사용자에게 노출하지 않는다.

- 종목명 / symbol (가능한 범위)
- 미래 가격
- 거래량
- duration 계산값
- width 계산값
- swing 계산값
- 기타 candidate feature
- NARROW boundary
- system prediction

사용자는 가격 구조만으로 1차 Formation 판단을 할 수 있어야 한다.

### AC-004 — Formation Labels

사용자는 각 후보에 대해 다음 중 하나를 선택할 수 있어야 한다.

```text
BOX
NOT_BOX
UNCERTAIN
```

선택 결과는 candidate와 연결되어 저장되어야 한다.

### AC-005 — Fast Negative / Uncertain Workflow

`NOT_BOX`와 `UNCERTAIN`은 일반적인 경우 Boundary 입력이나 추가 필수 질문 없이 한 번의 평가 동작으로 완료할 수 있어야 한다.

초기 Discovery에서는 이유 카테고리를 필수 입력으로 요구하지 않는다.

### AC-006 — Boundary Reveal After BOX

`NARROW` Boundary Candidate는 사용자가 `BOX`를 선택한 뒤에만 공개한다.

`NOT_BOX` 또는 `UNCERTAIN` 판정 전에 NARROW 경계를 표시해서는 안 된다.

### AC-007 — Period Acceptance / Modification

BOX 판정 후 사용자는 NARROW의 제안 날짜 구간을 다음 중 하나로 처리할 수 있어야 한다.

- 그대로 승인
- 시작일 수정
- 종료일 수정
- 시작일과 종료일 모두 수정

시스템 제안값과 사용자 최종값을 구분하여 저장한다.

### AC-008 — Chart-based Period Editing

기간 수정은 차트 직접 조작을 기본으로 지원해야 한다.

최소한 다음 중 적절한 chart-first interaction을 제공한다.

- start/end handle drag
- 시작 candle 선택 후 종료 candle 선택

선택된 날짜는 실제 거래일 candle에 snap되어야 한다.

Date picker 또는 text input만으로 기간을 수정하도록 구현해서는 안 된다.

### AC-009 — Zone Acceptance / Modification

BOX 판정 후 사용자는 다음 제안 가격 Zone을 승인하거나 수정할 수 있어야 한다.

```text
Lower Support Zone
Upper Resistance Zone
```

시스템 제안값과 사용자 최종값을 구분하여 저장한다.

### AC-010 — Chart-based Zone Editing

Support / Resistance Zone은 차트에서 직접 조작할 수 있어야 한다.

최소한 다음을 지원한다.

- zone drag
- zone resize
- 현재 zone 시각화

숫자 form만으로 Zone을 수정하도록 구현해서는 안 된다.

### AC-011 — Keyboard Workflow

반복 평가를 위해 최소한 다음 keyboard workflow를 지원한다.

```text
1     = BOX
2     = NOT_BOX
3     = UNCERTAIN
←     = Previous
→     = Next
Enter = Confirm / Accept
Esc   = Cancel Current Edit
```

키보드 입력은 화면의 주요 평가 기능과 일관되게 동작해야 한다.

### AC-012 — Minimal Interaction

반복 평가 시 불필요한 modal / confirm / save 절차를 최소화한다.

특히:

- NOT_BOX는 일반적인 경우 한 번의 키 입력으로 완료 가능
- UNCERTAIN은 일반적인 경우 한 번의 키 입력으로 완료 가능
- BOX이며 제안 Boundary가 적절하면 최소한의 추가 입력으로 완료 가능

평가마다 `modal → confirm → save → next`가 반복되는 UX를 만들지 않는다.

### AC-013 — Previous Evaluation Correction

사용자는 이전 후보로 이동하여 기존 평가 결과를 확인하고 수정할 수 있어야 한다.

수정 후 최신 결과가 저장되어야 하며 가능한 범위에서 수정 시각 또는 변경 이력을 보존한다.

### AC-014 — Persistence

사용자의 평가 결과는 입력 또는 확정 시 즉시 영속화되어야 한다.

브라우저 종료 또는 reload로 이미 완료한 평가가 손실되어서는 안 된다.

### AC-015 — Resume

사용자가 평가 화면을 다시 열었을 때 기존 batch의 진행 상태를 복원할 수 있어야 한다.

완료되지 않은 후보가 존재한다면 적절한 미완료 위치부터 평가를 계속할 수 있어야 한다.

### AC-016 — Batch Progress

현재 batch의 진행 상황을 사용자에게 표시한다.

예:

```text
17 / 20
```

완료 / 미완료 상태를 식별할 수 있어야 한다.

### AC-017 — Stratified Discovery

Discovery Dataset 생성은 duration과 width 등 연구 차원을 고려한 stratified sampling을 지원해야 한다.

Sampling bucket은 research configuration이며 production Box threshold와 분리되어야 한다.

### AC-018 — Candidate Diversity

Discovery candidate는 가능한 범위에서 특정 종목이나 특정 시점에 과도하게 집중되지 않아야 한다.

다음 차원의 다양성을 고려한다.

- symbol
- cutoffDate
- duration bucket
- width bucket
- market period / regime

한 종목이 전체 연구 결과를 지배하지 않도록 한다.

### AC-019 — Research Feature Preservation

candidate와 함께 연구에 필요한 feature snapshot을 보존할 수 있어야 한다.

Feature는 사용자 Blind Classification 전에 노출하지 않는다.

저장된 feature와 사용자 label을 이후 분석에서 연결할 수 있어야 한다.

### AC-020 — Classification / Boundary Separation

연구 데이터에서 다음 두 결과를 명확하게 분리할 수 있어야 한다.

```text
Formation Classification
Boundary Evaluation
```

예를 들어 다음을 구분할 수 있어야 한다.

```text
BOX + NARROW_ACCEPTED
BOX + NARROW_MODIFIED
NOT_BOX
UNCERTAIN
```

NARROW Boundary 수정 여부를 Box Classification 자체와 혼동해서는 안 된다.

### AC-021 — No Production Threshold Decision

TASK-002 구현 과정에서 다음과 같은 production threshold를 임의로 확정해서는 안 된다.

- minimum duration
- maximum Box width
- support/resistance zone width
- minimum swing count
- trend slope rejection threshold
- breakout depth
- breakdown depth
- breakout confirmation days
- breakdown confirmation days
- reentry days

필요한 값은 research configuration / sampling bucket / observation feature로만 취급한다.

### AC-022 — Volume Separation

Formation Blind Evaluation에서는 거래량을 표시하거나 Formation 판정의 필수 입력으로 사용해서는 안 된다.

거래량 데이터가 존재한다는 이유만으로 삭제할 필요는 없지만 Box Formation Label 결정에는 사용하지 않는다.

### AC-023 — No Strategy Integration

TASK-002 구현으로 다음 운영 동작이 변경되어서는 안 된다.

- production strategy decision
- PAPER trading
- LIVE trading
- order generation
- order execution
- position management
- risk control

Box Formation Research는 운영 전략과 분리되어야 한다.

### AC-024 — Existing Behavior Preservation

TASK-002 범위 밖의 기존 기능을 불필요하게 변경하지 않는다.

특히:

- 기존 strategy behavior를 변경하지 않는다.
- 기존 backtest calculation을 변경하지 않는다.
- 기존 order behavior를 변경하지 않는다.
- 기존 unrelated API contract를 변경하지 않는다.
- 기존 테스트를 삭제하거나 약화하지 않는다.
- unrelated working-tree 변경을 수정하거나 되돌리지 않는다.

---

## 8. Tests / Validation

TASK-002는 Research Infrastructure이지만 구현 결과가 재현 가능하고 Blind Evaluation 원칙을 지키는지 자동 테스트 또는 적절한 검증으로 확인해야 한다.

### 8.1 Backend Tests

가능한 범위에서 다음을 검증한다.

#### Candidate Generation

- 새로운 Discovery candidate 생성
- dataset / batch 식별
- candidate ID 안정성
- sampling configuration 적용
- 동일한 입력 조건에서 재현 가능한 결과

#### Point-in-Time Safety

- cutoffDate 이후 데이터 제외
- future candle이 candidate input에 포함되지 않음
- future-derived feature가 저장되지 않음

#### Evaluation Persistence

- BOX 저장
- NOT_BOX 저장
- UNCERTAIN 저장
- 기존 evaluation update
- proposed boundary와 modified boundary 분리 저장
- proposed zone과 modified zone 분리 저장

#### Resume / Progress

- 완료 candidate 식별
- 미완료 candidate 식별
- batch progress 계산
- resume 대상 결정

#### Feature Mapping

- candidate와 feature snapshot 연결
- evaluation label과 candidate 연결
- feature와 evaluation 결과를 이후 분석 가능

#### Stratified Sampling

- duration / width sampling bucket 적용
- 특정 bucket만 비정상적으로 선택되지 않는지 확인
- candidate diversity 관련 configuration 동작 확인

Sampling test는 bucket을 production Box rule로 검증하는 테스트가 되어서는 안 된다.

---

### 8.2 Frontend Tests

가능한 범위에서 다음 사용자 workflow를 검증한다.

#### Blind Rendering

Blind Classification 전:

- future price 미표시
- volume 미표시
- NARROW boundary 미표시
- candidate feature 미표시
- system prediction 미표시

#### Formation Classification

다음 선택이 동작한다.

```text
BOX
NOT_BOX
UNCERTAIN
```

#### Conditional Boundary Reveal

- BOX 선택 후 NARROW 표시
- NOT_BOX에서는 Boundary Evaluation 요구하지 않음
- UNCERTAIN에서는 Boundary Evaluation 요구하지 않음

#### Keyboard Interaction

최소한 다음 동작을 검증한다.

```text
1
2
3
←
→
Enter
Esc
```

#### Navigation

- previous candidate
- next candidate
- 기존 평가 reopen
- 기존 평가 수정

#### Chart Period Editing

- start boundary 수정
- end boundary 수정
- candle 기반 선택
- 실제 trading day로 snap

#### Chart Zone Editing

- Lower Support Zone 수정
- Upper Resistance Zone 수정
- drag / resize 후 결과 반영

#### Persistence / Resume

- 평가 결과 저장
- 화면 reload 후 상태 복원
- batch progress 복원

---

### 8.3 Regression Tests

Backend 전체 regression test를 실행한다.

```text
mvn test
```

Frontend에 TASK-002와 관련된 기존 test command가 존재하면 해당 테스트도 실행한다.

기존 프로젝트의 `mvn verify`가 TASK-000 시점부터 존재한 JaCoCo coverage threshold 때문에 실패할 수 있다는 사실과 TASK-002에서 새롭게 발생한 실패를 구분한다.

기존 알려진 coverage 기준을 통과시키기 위해 다음 행동을 해서는 안 된다.

- JaCoCo threshold 하향
- coverage rule 삭제
- 기존 test 삭제
- assertion 약화
- 테스트 제외를 통한 우회

TASK-002 변경으로 새로운 test failure 또는 coverage regression이 발생한 경우 Implementation Report에 명확히 기록한다.

---

### 8.4 Manual UX Validation

자동 테스트만으로 충분히 검증하기 어려운 chart interaction은 수동 검증 결과를 Implementation Report에 기록한다.

최소 확인 대상:

- 반복 평가 시 레이아웃 안정성
- wheel zoom
- drag / pan
- reset view
- candidate/context 시각적 구분
- date boundary drag
- candle selection
- trading-day snap
- support/resistance zone drag
- support/resistance zone resize
- keyboard workflow
- NOT_BOX / UNCERTAIN의 빠른 처리
- BOX + accepted boundary의 최소 입력 처리
- previous correction
- reload / resume

---

## 9. Research Outputs

TASK-002 완료 시 최소한 다음 연구 산출물을 확보할 수 있어야 한다.

- 재현 가능한 candidate generator 또는 candidate manifest
- Discovery Dataset / Batch
- Boundary Dataset을 생성할 수 있는 기반
- Holdout Dataset을 분리할 수 있는 기반
- Blind Formation Evaluation UI
- BOX / NOT_BOX / UNCERTAIN label dataset
- NARROW Boundary Evaluation 결과
- 사용자 수정 date boundary
- 사용자 수정 Support / Resistance Zone
- candidate feature dataset
- label-feature 비교가 가능한 데이터 구조
- batch progress / evaluation history
- Rule v1 결정을 위한 미결정 사항 목록

TASK-002 완료 자체가 Box Formation Rule v1의 최종 확정을 의미하지 않는다.

---

## 10. Analysis Requirements

Discovery Evaluation 이후 최소한 다음 관점에서 결과를 분석할 수 있어야 한다.

### 10.1 Label Distribution

```text
BOX
NOT_BOX
UNCERTAIN
```

각 label의 분포를 확인한다.

### 10.2 Duration / Width Distribution

다음 관계를 분석할 수 있어야 한다.

```text
duration bucket × label
width bucket × label
duration × width × label
```

Sampling bucket 자체를 Box Rule로 해석하지 않는다.

### 10.3 Candidate Diversity

다음 편향 여부를 확인한다.

- 특정 symbol 집중
- 특정 cutoffDate 집중
- 특정 시장 구간 집중
- 특정 duration / width 구간 집중

### 10.4 Feature / Label Relationship

연구 feature와 사용자 label 사이의 관계를 분석한다.

예:

```text
duration
coreRangeWidthPct
swingDensity
directionalPersistence
supportInteraction
resistanceInteraction
outlier-related features
```

상관관계가 있다는 이유만으로 즉시 production threshold를 확정하지 않는다.

### 10.5 Boundary Quality

BOX 후보에 대해 다음을 분석한다.

- NARROW accepted count
- NARROW modified count
- proposed vs final start date 차이
- proposed vs final end date 차이
- 가능한 경우 date interval IoU
- proposed vs final Core Range 차이
- Support Zone 수정 패턴
- Resistance Zone 수정 패턴

### 10.6 Uncertainty Region

`UNCERTAIN` 표본을 삭제하거나 단순 오류로 처리하지 않는다.

UNCERTAIN이 집중되는 가격 구조를 찾아 이후 Boundary Dataset sampling에 활용한다.

### 10.7 Batch Consistency

A1, A2 및 필요한 경우 A3 사이에서 label / feature 관계가 지나치게 달라지는지 확인할 수 있어야 한다.

---

## 11. Implementation Rules

Codex는 TASK-002 구현 시 다음 규칙을 준수한다.

1. TASK-002는 **Research Infrastructure + Discovery**이며 production Box Detector 구현 TASK가 아니다.

2. 구현 편의를 위해 duration, width, zone, swing, breakout, breakdown 등의 수치 임계값을 임의의 production rule로 확정하지 않는다.

3. Sampling bucket과 production Box Rule을 명확하게 분리한다.

4. 모든 Blind Evaluation은 Point-in-Time 원칙을 지켜야 한다.

5. Blind Classification 전에 NARROW Boundary, candidate feature, system prediction을 노출하지 않는다.

6. Formation Evaluation에서 거래량을 판단 입력으로 사용하지 않는다.

7. 기간과 Support / Resistance Zone 수정은 chart-first interaction을 기본으로 한다.

8. 기존 evaluation / history contract가 존재한다면 가능한 범위에서 호환성을 유지한다. 변경이 필요한 경우 Implementation Report에 이유와 영향을 명시한다.

9. DB schema 또는 API contract 변경이 필요한 경우 변경 내용을 Implementation Report에 명확하게 기록한다.

10. TASK-002 수행을 이유로 불필요한 architecture refactoring을 하지 않는다.

11. 기존 테스트를 삭제하거나 acceptance criteria를 약화하여 테스트를 통과시키지 않는다.

12. TASK-002와 관계없는 working-tree 변경을 수정, revert 또는 commit하지 않는다.

13. 특히 기존 unrelated 변경인 다음 파일을 수정하거나 되돌리지 않는다.

```text
frontend/e2e/app.spec.js
```

14. TASK 문서의 전제와 실제 코드 구조가 충돌하여 제품/연구 설계 결정이 필요한 경우 Codex가 임의로 결정하지 않는다. 구현 가능한 범위까지 진행한 뒤 충돌 내용과 선택지를 Implementation Report에 기록한다.

15. `NARROW`를 Box 정답 또는 production Detector로 취급하지 않는다.

16. `EXPANDED` 또는 `CONNECTED`를 검증된 baseline으로 간주하지 않는다.

17. 기존 strategy, backtest, PAPER/LIVE trading, order, risk behavior를 TASK-002 때문에 변경하지 않는다.

18. 연구 결과와 사용자 수정 결과는 가능한 범위에서 재현 가능한 형태로 저장한다.

---

## 12. Completion / Implementation Report

TASK-002 구현 완료 후 Codex는 다음 형식으로 Implementation Report를 제출한다.

### 12.1 Summary

구현한 내용을 간략하게 설명한다.

### 12.2 Changed Files

TASK-002 수행으로 생성 또는 변경한 파일을 나열한다.

각 파일의 변경 목적을 간략하게 설명한다.

### 12.3 Implementation Details

다음 영역별 구현 내용을 설명한다.

- candidate generation / dataset
- Point-in-Time handling
- Blind Evaluation
- Formation Classification
- Boundary Evaluation
- chart interaction
- persistence
- resume / progress
- feature snapshot
- research analysis support

### 12.4 Acceptance Criteria

`AC-001`부터 `AC-024`까지 각각 다음 형식으로 보고한다.

```text
AC-001: PASS / FAIL
Evidence:
...

AC-002: PASS / FAIL
Evidence:
...
```

구현하지 않았거나 검증하지 못한 항목을 임의로 PASS 처리하지 않는다.

### 12.5 Tests

실행한 명령과 결과를 기록한다.

예:

```text
mvn test
Result: PASS / FAIL
Tests: ...
```

Frontend test도 실행한 경우 명령과 결과를 기록한다.

`mvn verify`를 실행한 경우 기존 JaCoCo technical debt와 TASK-002에서 새롭게 발생한 문제를 구분한다.

Manual UX Validation 결과도 별도로 기록한다.

### 12.6 Research Batch / Results

실제 Discovery Batch까지 생성했다면 다음을 기록한다.

- dataset ID
- batch ID
- candidate count
- sampling configuration
- duration / width bucket distribution
- symbol diversity
- cutoffDate distribution
- blind policy
- feature snapshot version
- evaluation status

사용자 평가가 아직 수행되지 않았다면 임의의 연구 결론을 생성하지 않고 다음과 같이 명시한다.

```text
Evaluation pending
```

### 12.7 Remaining Issues / Follow-up

다음을 구분하여 기록한다.

- 구현상 남은 문제
- 사용자 Evaluation 필요 사항
- 연구 결과 분석 필요 사항
- Lifecycle Research로 넘길 사항
- Volume Event Research로 넘길 사항
- production Detector 전에 결정해야 할 사항

### 12.8 Design Impact

TASK-002 구현 과정에서 기존 설계 또는 API / DB contract에 영향이 발생했는지 설명한다.

영향이 없다면 명시적으로 다음과 같이 기록한다.

```text
No design impact outside TASK-002 scope.
```

### 12.9 Commit

TASK-002 구현 commit hash를 기록한다.

아직 commit하지 않았다면 다음과 같이 기록한다.

```text
Not committed
```

---

## 13. Decision Gate / Expected Next Step

TASK-002 완료 후 바로 production Box Detector를 구현하지 않는다.

연구 흐름은 다음과 같다.

```text
TASK-002
Formation Research Infrastructure + Discovery
        ↓
Discovery Evaluation
        ↓
Discovery Result Analysis
        ↓
Boundary-focused Sampling (if needed)
        ↓
Boundary Evaluation
        ↓
Box Formation Rule v1 Candidate
        ↓
Holdout Validation
        ↓
Existing C0 Regression Check
        ↓
Box Formation Rule v1 Design Decision
        ↓
Box Detector v2
        ↓
Box Lifecycle Research
        ↓
Volume Expansion / Upper Excursion / Reentry Research
        ↓
Full Strategy Integration
```

구체적인 진행 절차는 다음과 같다.

1. 새로운 Discovery 후보를 생성한다.
2. Blind Evaluation UI에서 사용자 Formation Label을 수집한다.
3. BOX 후보에 대해서만 NARROW Boundary와 Core Range / Zone을 평가한다.
4. 첫 Discovery Batch 결과를 분석한다.
5. BOX / NOT_BOX 판단이 명확한 영역과 애매한 영역을 구분한다.
6. 필요한 경우 판단이 갈리는 영역을 Boundary Set으로 추가 조사한다.
7. 사용자 label과 feature 관계를 근거로 Box Formation Rule v1 후보를 설계한다.
8. Rule 결정에 사용하지 않은 Holdout Set에서 검증한다.
9. 기존 TASK-001 C0를 Regression / Sanity Check Set으로 확인한다.
10. ChatGPT 설계 검토를 통해 Box Formation Rule v1을 별도 설계 결정으로 확정한다.
11. 그 이후 별도 TASK에서 production Box Detector v2 구현 여부와 구현 계약을 정의한다.

TASK-002의 성공 기준은 **Box Detector가 완성되는 것**이 아니다.

TASK-002의 성공 기준은 다음 질문에 데이터로 답할 수 있는 연구 기반이 만들어지는 것이다.

> 어떤 가격 구조를 사용자가 Box라고 판단하며, 그 판단을 설명할 수 있는 Formation Rule을 어떻게 도출할 것인가?

Box Formation Rule v1은 Codex가 TASK-002 구현 과정에서 임의로 결정하지 않는다.

사용자 Evaluation과 연구 결과를 근거로 별도의 설계 검토를 거쳐 확정한다.
