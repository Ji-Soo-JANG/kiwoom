# TASK-004 — Box Formation Discovery A1 Sampling & Dataset Preparation

## 1. Metadata

- Task ID: TASK-004
- Title: Box Formation Discovery A1 Sampling & Dataset Preparation
- Status: READY
- Priority: HIGH
- Depends On:
  - TASK-002 — DONE
  - TASK-003 — DONE
  - Repository-wide Test & Coverage Contract — DONE
- Research Phase: Box Formation / Discovery A1
- Dataset Type: DISCOVERY
- Dataset Stage: A1
- Target Sample Size: 20
- Universe: CURRENT KOSPI/KOSDAQ UNIVERSE
- Market Quota:
  - KOSPI = 10
  - KOSDAQ = 10
- Historical Coverage Baseline: TASK-003
- Evaluation Workflow Baseline: TASK-002
- Coverage Contract Baseline:
  - Changed LINE >= 80%
  - Changed BRANCH >= 70%
  - Global LINE no-regression
  - Global BRANCH no-regression

---

## 2. Purpose

TASK-004의 목적은 Box Formation Rule을 만드는 것이 아니다.

목적은 이후 Box Formation 연구에 사용할 첫 번째 Discovery dataset인
A1을 편향 없이, 재현 가능하고, PIT(Point-In-Time) 및 blind 조건을
보존한 상태로 생성하는 것이다.

전체 연구 흐름:

Historical Daily Data
→ Eligibility
→ Sampling Contract
→ Discovery A1
→ Human Blind Evaluation
→ A1 Analysis
→ Discovery A2
→ 필요 시 A3
→ Boundary Set
→ Formation Rule v1
→ Holdout
→ C0 Regression

TASK-004의 책임 범위:

Historical Daily Data
→ Eligibility
→ Sampling Contract
→ Discovery A1 생성
→ 기존 Workbench에서 평가 가능한 상태 확인
→ STOP

A1 human evaluation 및 결과 분석은 TASK-004의 완료 조건이 아니다.

---

## 3. Research Principle

A1은 Box를 많이 발견하기 위한 dataset이 아니다.

A1은 Box Formation의 실제 특성을 탐색하기 위한 discovery sample이다.

따라서 다음 결과는 모두 정상적인 연구 결과가 될 수 있다.

- BOX가 많음
- BOX가 적음
- BOX가 없음
- NOT_BOX가 대부분
- UNCERTAIN이 많음

A1 생성 이후 BOX 비율을 보고 sample을 교체하거나 추가해서는 안 된다.

특히 다음 행위는 금지한다.

- known Box 사례를 의도적으로 추가
- 좋은 모양의 차트를 보고 sample 선택
- BOX 수가 적다는 이유로 재추출
- 사용자 판정 결과를 이용한 A1 재구성
- 미래 수익률이 좋은 사례를 선택
- strategy signal을 이용한 sample 선택

Discovery 결과를 본 뒤 sampling 전략을 변경해야 한다면
A2 또는 이후 별도 연구 단계에서 명시적으로 수행한다.

---

## 4. Universe

A1 authoritative universe boundary는 market이다.

포함 market:

- KOSPI
- KOSDAQ

제외 market:

- KONEX
- 기타 KRX market
- repository에서 KOSPI/KOSDAQ으로 확인되지 않는 대상

Universe는 현재 repository가 보유한 current universe를 사용한다.

따라서:

SURVIVORSHIP_BIAS = PRESENT
SURVIVORSHIP_BIAS_REMOVED = NO

TASK-004는 historical delisted universe를 복원하지 않는다.

---

## 5. Product Type Policy

TASK-004에서는 product type을 추가 eligibility filter로 사용하지 않는다.

따라서 KOSPI/KOSDAQ market에 속하는 대상에 대해:

- ETF
- ETN
- SPAC
- REIT
- preferred stock
- 기타 product subtype

이라는 이유만으로 A1 eligibility에서 추가 제외하지 않는다.

Product type은 repository에 신뢰 가능한 structured metadata가 이미 존재하는 경우
A1 composition을 이해하기 위한 reporting metadata로만 사용할 수 있다.

Product type metadata가 없거나 의미가 불명확하더라도
TASK-004 A1 생성을 BLOCKED 처리할 이유는 아니다.

다음은 금지한다.

- 종목명 문자열 기반 product type 추정
- ticker pattern 기반 임의 product type 추정
- undocumented heuristic 기반 product type 추정
- product type에 따른 sample 우선순위 변경
- product type에 따른 sampling probability 변경

A1 분석 결과 product type 차이가 Formation research에 영향을 준다고 판단되면
A2 또는 이후 research design에서 별도로 결정한다.

---

## 6. Historical Data Eligibility

TASK-003 결과를 A1 eligibility의 historical quality baseline으로 사용한다.

A1 initial eligibility는 다음을 요구한다.

historical_backfill_state.status = TARGET_REACHED

HISTORY_EXHAUSTED는 A1 initial sample에서 제외한다.

이 결정은 HISTORY_EXHAUSTED가 잘못된 데이터라는 의미가 아니다.

TASK-003에서 HISTORY_EXHAUSTED에는:

- listing boundary 가능 사례
- broker history exhaustion
- completeness uncertain

이 혼재되어 있으며 전체에 대해 historical completeness가
확정되지 않았기 때문이다.

A2/A3 또는 별도 TASK에서 검증된 listing-boundary 종목을
확장 eligibility로 사용할 수 있다.

---

## 7. 2015 Target Meaning

TASK-003의:

targetStartDate = 2015-01-01

은 historical data coverage target이다.

다음 의미가 아니다.

- Box가 2015년 이후에만 존재한다.
- Box Formation 최소 기간이 2015년부터다.
- Box duration rule이다.
- Formation threshold이다.

2015-01-01은 Box Formation 연구 규칙으로 사용하지 않는다.

---

## 8. Sample Unit

A1의 최종 sample unit은:

symbol + historical cutoffDate

이다.

단, sampling probability를 정의할 때 모든 가능한
symbol + cutoffDate 조합을 하나의 flat candidate population으로 취급하지 않는다.

Sampling은 반드시:

symbol-first
→ cutoff-second

순서로 수행한다.

목적은 historical candle 수가 많거나 상장 기간이 긴 symbol이
더 많은 cutoff candidate를 가진다는 이유만으로
더 높은 symbol selection probability를 갖는 것을 방지하는 것이다.

각 selected symbol은 A1에서 정확히 하나의 cutoffDate만 가진다.

단순히 symbol만 추출한 뒤 최신 차트를 평가하지 않는다.

각 sample은 특정 과거 시점의 정보만을 이용하여
그 시점에서의 Box Formation 여부를 평가할 수 있어야 한다.

예:

Sample
- symbol: hidden during blind evaluation
- cutoffDate: T
- visible candles: date <= T
- future candles: hidden

---

## 9. Sample Size

A1 target sample size:

20

Market quota:

KOSPI = 10
KOSDAQ = 10

원칙:

one symbol = one A1 sample

A1 안에서 동일 symbol을 서로 다른 cutoff로 중복 사용하지 않는다.

각 market에서 최종 eligibility를 만족하는 symbol이 10개 미만이면
다른 market에서 부족분을 보충하지 않는다.

이 경우:

A1_GENERATION_BLOCKED

로 처리하고 실제 eligible count를 보고한다.

sample size 또는 market quota를 임의 변경하지 않는다.

---

## 10. Pre-Cutoff Context

각 A1 sample은 cutoff 이전에 사람이 Formation context를
판단할 수 있는 충분한 daily history를 가져야 한다.

초기 최소 기준:

252 trading-day candles before or including cutoff

정확한 계산에서는 실제 저장된 유효 daily candle count를 사용한다.

이 조건은 Box duration rule이 아니다.

목적은 연구자가 cutoff 시점 이전의 가격 구조를 충분히 관찰할 수 있도록
research context를 확보하는 것이다.

따라서:

MIN_CONTEXT_CANDLES = 252

는 research dataset eligibility 조건이며
Formation Rule v1 threshold로 사용하지 않는다.

---

## 11. Cutoff Eligibility

cutoffDate는 반드시 해당 symbol에 실제 daily candle이 존재하는
trading date여야 한다.

각 sample에 대해:

- cutoffDate candle 존재
- cutoffDate 이전/포함 context >= 252 candles
- visible candle date <= cutoffDate
- future candle date > cutoffDate는 evaluation payload에서 제외

cutoff를 단순 calendar date로 생성한 뒤
가장 가까운 미래 trading date로 이동시키지 않는다.

cutoff selection은 실제 eligible candle date 집합에서 수행한다.

---

## 12. Historical Time Diversity

A1이 특정 historical period에 몰리지 않도록
selected symbols의 cutoffDate를 time-stratified sampling한다.

Time strata는:

- EARLY
- MIDDLE
- RECENT

3개를 사용한다.

Strata는 Box-related market regime을 사람이 정의하지 않는다.

금지:

- market return
- bull/bear label
- future return
- volatility outcome
- known event
- Box prevalence
- NARROW result
- strategy result

를 이용한 time-stratum boundary 결정.

Time-stratum boundary는 eligible cutoff-date population의
시간 분포만으로 deterministic하게 결정한다.

Eligible cutoff dates를 chronological order로 정렬하고,
population을 approximately equal-sized temporal thirds로 나누어:

- first third = EARLY
- second third = MIDDLE
- final third = RECENT

로 정의한다.

동일 eligible data snapshot에서는 동일 stratum boundary가 생성되어야 한다.

Boundary가 동일 date를 가르는 경우 deterministic tie-breaking을 사용한다.

Time strata는 research sampling diversity를 위한 것이며
Formation Rule의 일부가 아니다.

---

## 13. Symbol-First / Cutoff-Second Sampling Algorithm

A1 sampling은 다음 순서를 따른다.

### Step 1 — Eligible Symbol Population

각 market별로 다음 조건을 만족하는 symbol population을 만든다.

- market = KOSPI 또는 KOSDAQ
- current universe member
- historical_backfill_state = TARGET_REACHED
- 최소 하나 이상의 valid cutoffDate 후보 존재
- 해당 cutoffDate에서 context >= 252 candles 확보 가능

Product type은 eligibility filter로 사용하지 않는다.

### Step 2 — Deterministic Symbol Ordering

database physical row order에 의존하지 않는
stable deterministic ordering을 적용한다.

### Step 3 — Seeded Symbol Selection

고정 seed를 이용하여:

- KOSPI 10 symbols
- KOSDAQ 10 symbols

을 선택한다.

동일 eligible symbol population + 동일 seed에서는
동일 symbol set과 order가 생성되어야 한다.

### Step 4 — Time-Stratum Assignment

선택된 20 symbols에 대해 cutoff time diversity를 확보하도록
EARLY / MIDDLE / RECENT strata를 deterministic하게 배정한다.

20 samples는 가능한 한 균등하게 배정한다.

목표 분포:

- EARLY = 7
- MIDDLE = 6
- RECENT = 7

Market와 time stratum의 조합도 가능한 범위에서 편중되지 않도록 한다.

단, market quota:

KOSPI = 10
KOSDAQ = 10

은 유지한다.

### Step 5 — Cutoff Selection

각 selected symbol에 대해 배정된 time stratum 안에서
eligible actual candle dates만 cutoff candidate로 사용한다.

고정 seed와 stable deterministic ordering을 이용하여
정확히 하나의 cutoffDate를 선택한다.

### Step 6 — Deterministic Fallback

특정 selected symbol이 배정된 time stratum 안에
eligible cutoffDate를 갖지 않는 경우 결과를 보고 symbol을 임의 교체하지 않는다.

먼저 동일 symbol에 대해 deterministic fallback order:

MIDDLE assignment:
MIDDLE → EARLY → RECENT

EARLY assignment:
EARLY → MIDDLE → RECENT

RECENT assignment:
RECENT → MIDDLE → EARLY

를 사용한다.

Fallback은 Box-related information을 사용하지 않는다.

Fallback 발생 여부와 실제 final time stratum을 dataset metadata/report에 기록한다.

어떤 time stratum에서도 valid cutoff가 없으면 해당 symbol은
final eligible symbol population에 포함될 수 없었던 것으로 간주한다.

이 상황이 sampling 후 발견되었다면
eligibility implementation defect로 취급하고 Gate 0를 FAIL/BLOCKED 처리한다.

---

## 14. Market Diversity

A1 market distribution은 exact quota를 사용한다.

KOSPI = 10
KOSDAQ = 10

다른 market은 허용하지 않는다.

KOSPI 부족분을 KOSDAQ으로 채우거나
KOSDAQ 부족분을 KOSPI로 채우지 않는다.

각 market에 eligible symbols가 10개 미만이면:

A1_GENERATION_BLOCKED

처리한다.

---

## 15. Sampling Seed

A1 sampling은 deterministic해야 한다.

고정 seed:

20260901

예상 dataset identity:

datasetKey = BOX-FORMATION-DISCOVERY-A1
datasetType = DISCOVERY
stage = A1
sampleSize = 20
seed = 20260901

repository의 기존 dataset schema/API naming convention이 다르면
동등한 의미로 맞춘다.

seed 값은 random retry 또는 desired outcome을 얻기 위해 변경하지 않는다.

---

## 16. Determinism

다음 조건이 동일하면 동일한 A1 sample set과 order가 생성되어야 한다.

- same eligible data snapshot
- same universe
- same eligibility rules
- same sampling algorithm/version
- same seed

동일성에는 다음이 포함된다.

- same 20 symbols
- same sample order
- same cutoffDate
- same time-stratum assignment
- same fallback result

database row physical order에 의존하면 안 된다.

동일 datasetKey의 A1이 이미 존재하면
새 random dataset으로 교체하지 않는다.

정책:

ALREADY_EXISTS

또는 기존 dataset 반환.

기존 A1을 자동:

- delete
- replace
- recreate
- resample

하지 않는다.

---

## 17. Sampling Must NOT Use

A1 eligibility, symbol sampling, time-stratum assignment,
cutoff selection 또는 fallback에서 다음 정보를 사용해서는 안 된다.

- future candle
- future return
- future price outcome
- user Formation label
- prior BOX / NOT_BOX / UNCERTAIN label
- known Box example
- NARROW candidate quality
- NARROW score
- computed Box width
- computed Box duration
- swing count intended as Box rule
- volume
- volume increase
- plunge signal
- recovery signal
- pullback signal
- strategy signal
- detector prediction
- backtest result
- trade result
- profitability
- future lifecycle outcome

Sampling은 Box outcome을 모르는 상태에서 수행되어야 한다.

---

## 18. Volume Separation

Volume은 daily candle storage에 존재하더라도
TASK-004 Formation sampling 판단에 사용하지 않는다.

A1 blind evaluation에서도 volume을 표시하지 않는다.

Volume research는:

Box Formation
→ Lifecycle
→ Volume Event

순서에서 별도 단계로 수행한다.

TASK-004에서 volume threshold 또는 volume pattern을 연구하지 않는다.

---

## 19. PIT Contract

각 A1 item의 evaluation context는 cutoffDate 시점의
Point-In-Time 정보를 보존해야 한다.

필수 조건:

visible candle date <= cutoffDate

금지:

future candle date > cutoffDate

future data가 persistence layer에 존재하는 것은 허용되지만
evaluation API/payload/chart에 노출되어서는 안 된다.

PIT verification은 automated test 대상이다.

---

## 20. Blind Contract

사용자의 최초 Formation 판정 전에 가능한 범위에서 다음을 숨긴다.

- symbol
- stock name
- volume
- future candles
- future return
- computed Box duration
- computed Box width
- computed Formation features
- NARROW candidate
- system prediction
- detector result
- strategy signal

기존 TASK-002 architecture가 symbol/name blind identifier를
지원한다면 반드시 재사용한다.

지원하지 않는 경우 production-wide identity architecture를
TASK-004에서 임의 재설계하지 않는다.

blind requirement를 충족하기 위한 최소 변경만 허용한다.

---

## 21. Human Evaluation Workflow

TASK-002의 기존 Box Formation Workbench를 재사용한다.

초기 판단:

1 = BOX
2 = NOT_BOX
3 = UNCERTAIN

NOT_BOX / UNCERTAIN:

가능한 기존 one-key workflow를 유지한다.

BOX:

최초 BOX 판단 이후에만 NARROW baseline candidate를 reveal한다.

이후 기존 TASK-002 workflow를 이용하여:

- candidate period accept/edit
- Core Range edit
- Lower Support Zone edit
- Upper Resistance Zone edit

을 수행한다.

TASK-004에서 새로운 별도 evaluation application을 만들지 않는다.

---

## 22. NARROW Meaning

TASK-001 C0 결과에 따라 NARROW는 initial boundary baseline proposal이다.

NARROW 자체가 Box 정의는 아니다.

A1 최초 Formation 판정 전에 NARROW를 보여주지 않는다.

BOX 판정 이후 boundary editing aid로만 사용한다.

EXPANDED / CONNECTED는 validated baseline이 아니다.

TASK-004에서 EXPANDED / CONNECTED를 A1 candidate baseline으로
도입하지 않는다.

---

## 23. Formation Scope

TASK-004는 Box Formation만 다룬다.

Formation 개념:

A Box is a price structure formed over a sufficiently long period
within a narrow Core Range, with repeated price movement and
support/resistance reactions.

단, 다음 숫자는 아직 research 대상이다.

- exact minimum duration
- exact Core Range width
- exact support/resistance zone width
- exact swing density
- exact slope tolerance
- exact boundary reaction requirement

TASK-004에서 이를 production threshold로 결정하지 않는다.

---

## 24. Formation vs Lifecycle

TASK-004에서 Formation과 Lifecycle을 혼합하지 않는다.

향후 lifecycle states:

- IN_BOX
- UPPER_EXCURSION
- REENTRY
- UPPER_BREAKOUT
- LOWER_BREAKDOWN

은 TASK-004 scope가 아니다.

cutoff 이후 Box가 유지됐는지, breakout했는지, breakdown했는지는
A1 sampling 또는 Formation label에 사용하지 않는다.

---

## 25. Persistence

TASK-002에서 구축한 research dataset/batch/item persistence를
우선 재사용한다.

불필요한 새 schema를 만들지 않는다.

새 persistence가 필요한 경우 먼저 기존 schema로 표현할 수 없는
구체적인 이유를 보고한다.

A1에 최소한 추적 가능한 metadata:

- dataset identity
- dataset type
- stage
- seed
- sampling algorithm/version
- sample order
- item ID
- hidden symbol identity
- market
- cutoffDate
- assigned time stratum
- actual time stratum
- fallback applied 여부
- historical eligibility evidence
- createdAt

를 보존한다.

Product type은 reliable structured metadata가 이미 존재할 경우
reporting metadata로 저장할 수 있지만 sampling에 사용하지 않는다.

---

## 26. Dataset Immutability

A1 생성 이후 sample membership/order를 자동 변경하지 않는다.

특히 human evaluation이 시작된 이후에는 반드시 immutable하게 취급한다.

금지:

- resample
- replace NOT_BOX
- replace UNCERTAIN
- append known BOX
- seed 변경 후 재생성
- sample order random mutation
- cutoffDate 자동 변경

A1 자체에 문제가 발견되면 조용히 수정하지 않는다.

문제를 기록하고 연구 영향에 따라:

BLOCKED

또는 별도 correction decision을 요청한다.

---

## 27. Gate 0 — Sampling Infrastructure Verification

실제 A1을 생성하기 전에 automated verification을 수행한다.

최소 검증:

1. KOSPI/KOSDAQ universe filter
2. exact market quota capability
3. historical TARGET_REACHED eligibility
4. minimum 252 context candles
5. cutoff is actual candle date
6. symbol-first sampling
7. cutoff-second sampling
8. deterministic seed
9. deterministic ordering
10. one symbol per sample
11. KOSPI 10 / KOSDAQ 10
12. EARLY / MIDDLE / RECENT deterministic boundary
13. 7 / 6 / 7 target time assignment
14. deterministic cutoff selection
15. deterministic fallback
16. no future candle use/exposure
17. no volume use
18. no label use
19. no NARROW outcome use
20. no strategy/detector/backtest/trade outcome use
21. product type not used for sampling
22. existing dataset idempotence
23. ALREADY_EXISTS behavior
24. persistence roundtrip
25. Workbench compatibility
26. blind payload behavior

Gate 0가 PASS하기 전에 실제 A1 dataset을 생성하지 않는다.

---

## 28. Gate 1 — Discovery A1 Generation

Gate 0 PASS 후 실제 repository/database에서
A1 dataset을 한 번 생성한다.

목표:

20 items

필수 distribution:

KOSPI = 10
KOSDAQ = 10

Target time distribution:

EARLY = 7
MIDDLE = 6
RECENT = 7

time-stratum fallback이 발생하면 actual distribution은 달라질 수 있으나
fallback은 contract의 deterministic rule만 따라야 한다.

생성 후 검증:

- total items = 20
- unique symbols = 20
- duplicate symbol = 0
- KOSPI = 10
- KOSDAQ = 10
- other market = 0
- every item historical status = TARGET_REACHED
- every item context >= 252 candles
- every cutoff is actual candle date
- future leakage = 0
- volume leakage = 0
- label leakage = 0
- NARROW pre-judgment leakage = 0
- product-type sampling dependency = 0
- deterministic order confirmed
- dataset persisted
- Workbench accessible
- initial progress = 0/20

실제 historical time distribution을 기록한다.

fallback count와 fallback details를 symbol identity를 노출하지 않는 방식으로 기록한다.

Gate 1에서는 human BOX judgment를 시작하지 않는다.

---

## 29. Gate 1 Stop Condition

A1 dataset이 성공적으로 생성되고 검증되면 STOP한다.

TASK-004 완료를 위해 다음을 수행하지 않는다.

- 20개 차트 평가
- BOX label 입력
- NOT_BOX label 입력
- UNCERTAIN label 입력
- NARROW 수정
- Formation statistics 계산
- threshold fitting
- Rule v1 도출

A1 생성 결과를 먼저 ChatGPT/user가 검수한다.

---

## 30. Out of Scope

다음은 TASK-004 범위 밖이다.

- A1 human evaluation
- A1 result analysis
- A2 sampling
- A3 sampling
- Boundary Set
- Formation Rule v1
- Production Box Detector v2
- lifecycle implementation
- volume research
- plunge research
- recovery research
- pullback research
- strategy integration
- trading integration
- backtest return optimization
- profit optimization
- future outcome labeling
- historical delisted universe reconstruction
- survivorship bias removal
- Kiwoom historical backfill redesign
- product-type-specific Formation rule

---

## 31. Coverage Contract

TASK-004는 repository-wide Coverage Contract를 따른다.

Production code를 변경하면:

Changed LINE >= 80%

Changed BRANCH >= 70%

Global LINE no-regression

Global BRANCH no-regression

Instruction/Method:

REPORT_ONLY

필수 테스트:

- normal behavior
- applicable branches
- boundary conditions
- failure behavior
- regression behavior where applicable

coverage를 맞추기 위해:

- meaningless test 추가
- assertion 없는 test
- existing test 약화
- exclusion 추가
- threshold 하향
- production validation 약화

를 해서는 안 된다.

---

## 32. Git / Working Tree Safety

TASK 시작 시 반드시 실제 git status를 확인한다.

Coverage Contract baseline commit:

b7442a61fb6728b5dd87e2ad1f4242bcdd64a7a4

현재 알려진 unrelated working-tree 변경:

- frontend/e2e/app.spec.js
- frontend/src/api/generated/openapi.d.ts
- BoxEvaluation controller/dto/model/service 변경
- tmp-run-controlled.ps1

이 파일들은 TASK-004의 소유가 아니다.

절대 임의로:

- modify
- revert
- overwrite
- cleanup
- stage
- commit

하지 않는다.

TASK-004 변경과 실제 충돌하면 작업을 계속 덮어쓰지 말고 BLOCKED로 보고한다.

---

## 33. Security / Operational Safety

TASK-004에서 실제 broker API 호출은 필요하지 않다.

기존 PostgreSQL historical data를 사용한다.

금지:

- Kiwoom API key 출력
- secret 출력
- access token 출력
- continuation token 출력
- .env 내용 출력
- production trading order
- PAPER order
- account mutation

A1 dataset persistence에 필요한 research DB mutation만 허용한다.

---

## 34. Design Decision Boundary

Codex가 자율적으로 결정 가능한 것:

- 기존 architecture에 맞는 class/package 이름
- repository/service 내부 구현 방식
- deterministic PRNG/shuffle 구현 세부사항
- stable tie-breaking 구현 세부사항
- SQL 최적화
- DTO 세부 구조
- test fixture 구성
- 기존 schema를 활용하는 기술적 방법

단, 이러한 구현 선택은 이 문서가 정의한
sampling probability/eligibility/distribution을 변경해서는 안 된다.

Codex가 임의 결정하면 안 되는 것:

- universe를 KOSPI/KOSDAQ 밖으로 확대
- product type을 eligibility filter로 사용
- HISTORY_EXHAUSTED 포함
- sample size 변경
- KOSPI 10 / KOSDAQ 10 quota 변경
- seed 변경
- symbol-first sampling 변경
- cutoff-second sampling 변경
- time-stratum 정의를 Box 결과에 맞게 변경
- BOX 결과를 이용한 resampling
- volume 사용
- future outcome 사용
- NARROW를 최초 판정 전에 공개
- Formation threshold 결정
- A1 평가 시작
- A2 생성
- product type을 이름/휴리스틱으로 추측
- survivorship bias가 제거됐다고 주장

이러한 변경이 필요하면 BLOCKED 후 설계 판단을 요청한다.

---

## 35. Acceptance Criteria

### Sampling Contract

AC-001
A1 dataset type은 DISCOVERY이다.

AC-002
A1 target sample size는 정확히 20이다.

AC-003
Universe는 current KOSPI/KOSDAQ으로 제한된다.

AC-004
KONEX 및 기타 market은 제외된다.

AC-005
Survivorship bias는 PRESENT / NOT REMOVED로 기록된다.

AC-006
Product type은 A1 eligibility/sampling filter로 사용하지 않으며
종목명/ticker/휴리스틱으로 product type을 추측하지 않는다.

### Historical Eligibility

AC-007
A1 sample은 historical_backfill_state = TARGET_REACHED만 사용한다.

AC-008
HISTORY_EXHAUSTED는 A1 initial eligibility에서 제외된다.

AC-009
각 sample은 cutoff 이전/포함 최소 252 daily candles를 가진다.

AC-010
2015-01-01을 Formation rule로 사용하지 않는다.

### Sampling

AC-011
최종 sample unit은 symbol + cutoffDate이다.

AC-012
sampling은 symbol-first → cutoff-second 순서를 따른다.

AC-013
동일 symbol은 A1에서 정확히 최대 1회 사용된다.

AC-014
cutoffDate는 해당 symbol의 실제 candle date이다.

AC-015
sampling은 deterministic seed를 사용한다.

AC-016
seed는 20260901로 고정된다.

AC-017
동일 eligible snapshot + 동일 contract + 동일 seed는
동일 sample set/order/cutoffDate를 생성한다.

AC-018
database physical row order에 의존하지 않는다.

AC-019
KOSPI sample count는 정확히 10이다.

AC-020
KOSDAQ sample count는 정확히 10이다.

AC-021
다른 market sample count는 0이다.

AC-022
동일 symbol은 cutoff candidate 수가 많다는 이유로
더 높은 symbol selection probability를 갖지 않는다.

### Time Diversity

AC-023
historical time strata는 EARLY / MIDDLE / RECENT이다.

AC-024
time-stratum boundary는 eligible cutoff-date temporal distribution만으로
deterministic하게 계산한다.

AC-025
market return/volatility/event/Box outcome을 time boundary 결정에 사용하지 않는다.

AC-026
target time assignment은 EARLY 7 / MIDDLE 6 / RECENT 7이다.

AC-027
cutoff는 배정된 stratum의 actual eligible candle date에서 선택한다.

AC-028
stratum fallback은 contract에 정의된 deterministic order만 사용한다.

AC-029
fallback은 Box-related information을 사용하지 않는다.

### Anti-Bias / PIT

AC-030
sampling은 future candle을 사용하지 않는다.

AC-031
sampling은 future return/outcome을 사용하지 않는다.

AC-032
sampling은 user label을 사용하지 않는다.

AC-033
sampling은 NARROW quality/result를 사용하지 않는다.

AC-034
sampling은 volume을 사용하지 않는다.

AC-035
sampling은 plunge/recovery/pullback signal을 사용하지 않는다.

AC-036
sampling은 strategy/detector/backtest/trade outcome을 사용하지 않는다.

AC-037
evaluation payload에는 cutoffDate 이후 candle이 존재하지 않는다.

### Blind Workflow

AC-038
volume은 initial Formation evaluation에서 숨겨진다.

AC-039
NARROW는 최초 BOX/NOT_BOX/UNCERTAIN 판단 전에 숨겨진다.

AC-040
system prediction/computed Formation feature는 최초 판단 전에 숨겨진다.

AC-041
symbol/name은 기존 architecture가 지원하는 범위에서 blind 처리된다.

AC-042
기존 TASK-002 Workbench를 재사용한다.

AC-043
initial label은 BOX / NOT_BOX / UNCERTAIN이다.

AC-044
NARROW reveal/edit는 BOX 판단 이후에만 가능하다.

### Persistence / Idempotence

AC-045
A1 dataset identity와 seed가 persistence된다.

AC-046
sample order와 cutoffDate가 persistence된다.

AC-047
assigned/actual time stratum 및 fallback 여부가 재현 가능하게 보존된다.

AC-048
동일 A1 dataset이 존재하면 자동 resample하지 않는다.

AC-049
A1 생성 후 membership/order/cutoffDate를 자동 변경하지 않는다.

AC-050
sample generation/persistence roundtrip automated test가 존재한다.

### Gate 0

AC-051
Gate 0 automated tests가 모두 PASS한다.

AC-052
PIT leakage automated test가 PASS한다.

AC-053
deterministic symbol-first sampling automated test가 PASS한다.

AC-054
deterministic cutoff-second sampling automated test가 PASS한다.

AC-055
duplicate-symbol prevention automated test가 PASS한다.

AC-056
exact market quota automated test가 PASS한다.

AC-057
time-stratum/fallback automated test가 PASS한다.

AC-058
existing dataset idempotence automated test가 PASS한다.

AC-059
product type non-dependency automated verification이 PASS한다.

### Gate 1

AC-060
Gate 0 PASS 이후에만 실제 A1이 생성된다.

AC-061
A1 persisted item count는 정확히 20이다.

AC-062
A1 unique symbol count는 정확히 20이다.

AC-063
A1 KOSPI count는 정확히 10이다.

AC-064
A1 KOSDAQ count는 정확히 10이다.

AC-065
A1 other market count는 0이다.

AC-066
A1 모든 item은 TARGET_REACHED eligibility를 만족한다.

AC-067
A1 모든 item은 context >= 252 candles를 만족한다.

AC-068
A1 모든 cutoffDate는 해당 symbol의 실제 candle date이다.

AC-069
future leakage count는 0이다.

AC-070
volume leakage count는 0이다.

AC-071
NARROW pre-judgment leakage count는 0이다.

AC-072
initial evaluation progress는 0/20이다.

AC-073
20개 item이 기존 Workbench에서 접근 가능하다.

AC-074
실제 historical cutoff/time-stratum distribution이 보고된다.

AC-075
fallback count/result가 blind identity를 훼손하지 않는 형태로 보고된다.

### Research Boundary

AC-076
A1의 BOX 개수/비율은 TASK 성공 조건이 아니다.

AC-077
A1 human evaluation은 TASK-004에서 수행하지 않는다.

AC-078
Formation Rule v1을 만들지 않는다.

AC-079
Production Detector를 변경하지 않는다.

AC-080
Lifecycle을 구현하지 않는다.

AC-081
Volume Event를 구현하지 않는다.

AC-082
Strategy/Trading을 변경하지 않는다.

### Quality / Safety

AC-083
새 production code는 Changed LINE >= 80%를 만족한다.

AC-084
새 production code는 Changed BRANCH >= 70%를 만족한다.

AC-085
Global LINE coverage가 regression하지 않는다.

AC-086
Global BRANCH coverage가 regression하지 않는다.

AC-087
관련 automated tests가 PASS한다.

AC-088
repository quality contract를 우회하지 않는다.

AC-089
기존 unrelated working-tree 변경을 보존한다.

AC-090
실제 broker API 호출이 없다.

AC-091
secret/token/.env 내용이 노출되지 않는다.

AC-092
production trading/account mutation이 없다.

AC-093
git diff --check가 PASS한다.

---

## 36. Gate Decisions

Gate 0 결과:

GATE_0_PASS
또는
GATE_0_BLOCKED

Gate 0가 BLOCKED이면 A1을 생성하지 않는다.

Gate 1 결과:

A1_GENERATED
또는
A1_GENERATION_BLOCKED

TASK-004 최종 성공:

TASK_004_PASS

조건:

- Gate 0 PASS
- Gate 1 A1_GENERATED
- A1 20 items
- KOSPI 10
- KOSDAQ 10
- unique symbols 20
- eligibility/PIT/blind 검증 PASS
- deterministic sampling verification PASS
- Workbench access PASS
- progress 0/20
- human evaluation NOT STARTED

---

## 37. Completion Report

완료 시 다음 형식으로 보고한다.

# TASK-004 Completion Report

## Decision

TASK_004_PASS /
BLOCKED

## Summary

구현 및 A1 생성 요약.

## Git Baseline

- branch
- starting HEAD
- origin/main
- unrelated working-tree changes

## Universe

- KOSPI eligible count
- KOSDAQ eligible count
- excluded market count
- product type used for eligibility: NO
- product type reporting metadata availability
- survivorship bias: PRESENT / NOT REMOVED

## Historical Eligibility

- TARGET_REACHED eligible count
- HISTORY_EXHAUSTED excluded count
- context >= 252 eligible count

## Sampling Contract

- dataset key
- dataset type
- stage
- sample size
- seed
- symbol-first behavior
- cutoff-second behavior
- KOSPI quota
- KOSDAQ quota
- time-stratum boundary method
- time assignment
- fallback behavior
- determinism

## Gate 0

각 automated verification 결과.

## Gate 1

- dataset ID
- batch ID if applicable
- item count
- unique symbol count
- KOSPI count
- KOSDAQ count
- other market count
- EARLY count
- MIDDLE count
- RECENT count
- fallback count
- duplicate count
- future leakage count
- volume leakage count
- NARROW pre-judgment leakage count
- initial progress

실제 symbol/name은 blind research를 오염시킬 수 있으므로
일반 보고서에 출력하지 않는다.

필요한 내부 verification은 item ID/hash 등으로 수행한다.

## Workbench

- dataset accessible
- item navigation
- blind state
- progress
- human evaluation started: NO

## Coverage

- Changed LINE
- Changed BRANCH
- Global LINE
- Global BRANCH
- Instruction
- Method

## Tests

실행한 명령과 결과.

실행하지 않은 검증은 PASS로 표시하지 않는다.

## Acceptance Criteria

AC-001 ~ AC-093

PASS /
FAIL

근거 포함.

## Research Integrity

다음을 명시한다.

- future outcome used: NO
- volume used for sampling: NO
- label used for sampling: NO
- known Box outcome used: NO
- NARROW used for sampling: NO
- strategy/backtest outcome used: NO
- product type used for sampling: NO
- survivorship bias removed: NO

## Changed Files

TASK-004에서 변경한 파일만.

## Unrelated Working Tree

기존 사용자 변경 보존 상태.

## Remaining Issues

없으면 NONE.

## Commit

NOT CREATED

TASK-004 구현/검증 단계에서는 자동 commit/push하지 않는다.

## Next Single Action

TASK_004_PASS:

ChatGPT/user가 A1 generation 결과를 검수한 뒤
Discovery A1 blind human evaluation 시작 여부 결정.

BLOCKED:

설계 판단 또는 결함 수정.

보고 후 중지한다.