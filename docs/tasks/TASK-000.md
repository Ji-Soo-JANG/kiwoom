# TASK-000 — Project Structure Baseline Refactoring

**Status:** DONE 
**Priority:** P0  
**Type:** Refactoring / Architecture  
**Dependencies:** None  
**Next Task:** TASK-001 — Strategy Execution Contract

---

## 1. 목적

Kiwoom 프로젝트가 향후 TASK 단위로 지속적으로 개발될 수 있도록 코드 구조와 패키지 책임의 기준선을 확립한다.

TASK-000의 목적은 전체 프로젝트를 재작성하거나 모든 기존 패키지를 한 번에 이동하는 것이 아니다.

다음 TASK인 전략 실행 계약 통일 작업을 안전하게 수행할 수 있도록 우선 다음 경계를 명확하게 한다.

```text id="k3mbsm"
strategy
research
broker
common
```

TASK-000 완료 시점의 구조를 이후 개발의 **Architecture Baseline v1**으로 사용한다.

---

## 2. 현재 구조

현재 주요 Java 구조는 다음과 같다.

```text id="i7xlg5"
com.example.kiwoom
├─ client
├─ config
├─ controller
├─ dto
├─ error
├─ mapper
├─ repository
├─ research
│  └─ boxevaluation
└─ service
   ├─ broker
   ├─ strategy
   └─ ...
```

현재 `research/boxevaluation`은 이미 feature 중심으로 다음과 같이 분리되어 있다.

```text id="gmmg1s"
research/boxevaluation
├─ candidate
├─ controller
├─ dto
├─ model
├─ repository
└─ service
```

따라서 이 구조를 새로운 feature package의 참고 패턴으로 사용한다.

---

## 3. 구조 원칙

### 3.1 Package by Feature 우선

새로운 주요 기능은 최상위 기술 계층보다 기능/도메인 중심으로 구성한다.

지양:

```text id="gvig75"
controller
service
repository
dto
```

권장:

```text id="fpuk9d"
strategy
research
trading
marketdata
broker
operations
user
```

Controller / Service / Repository / DTO 등은 필요한 경우 각 feature 내부에서 사용한다.

---

### 3.2 점진적 Migration

기존 최상위:

```text id="zrvujr"
controller
service
repository
dto
```

를 TASK-000에서 제거하지 않는다.

향후 해당 영역을 수정하는 TASK가 수행될 때 feature package로 점진적으로 이전한다.

---

### 3.3 과도한 DDD 구조 금지

모든 package에 기계적으로 다음 구조를 만들지 않는다.

```text id="jpliz8"
domain
application
infrastructure
presentation
```

현재 프로젝트 규모에서는 feature 중심 구조를 우선하며 실제 복잡성이 필요한 경우에만 추가 계층을 도입한다.

---

## 4. Architecture Baseline v1

장기적인 최상위 package 방향은 다음과 같다.

```text id="nzhj9g"
com.example.kiwoom
│
├─ common
├─ broker
│  └─ kiwoom
├─ marketdata
├─ strategy
├─ research
│  ├─ backtest
│  ├─ walkforward
│  └─ boxevaluation
├─ trading
├─ operations
└─ user
```

TASK-000에서 이 구조 전체를 구현하지 않는다.

이번 TASK에서는 다음 TASK에 필요한 영역만 실제로 migration한다.

```text id="uh4wsr"
strategy
research/backtest
research/walkforward
broker
common
```

---

# 5. Mandatory Migration Map

아래 항목은 TASK-000에서 실제 이동한다.

## 5.1 Strategy

### 현재

```text id="0zsvf8"
service/strategy/StockStrategy.java
service/strategy/StrategyRegistry.java
service/strategy/CurrentRecoveryPullbackStrategy.java
service/strategy/DropBaseBreakoutPullbackStrategy.java
service/strategy/MultiPeriodRecoveryPullbackStrategy.java

service/StrategyPatternDetector.java
service/StrategyCatalogService.java

dto/StrategyDefinition.java
dto/StrategyCandidate.java
dto/StrategyScanResponse.java

repository/StrategyDefinitionRepository.java
repository/StrategySnapshotRepository.java
```

### 목표

```text id="3xkdlo"
strategy/
├─ StockStrategy.java
├─ StrategyRegistry.java
│
├─ implementation/
│  ├─ CurrentRecoveryPullbackStrategy.java
│  ├─ DropBaseBreakoutPullbackStrategy.java
│  └─ MultiPeriodRecoveryPullbackStrategy.java
│
├─ model/
│  ├─ StrategyDefinition.java
│  ├─ StrategyCandidate.java
│  └─ StrategyScanResponse.java
│
├─ repository/
│  ├─ StrategyDefinitionRepository.java
│  └─ StrategySnapshotRepository.java
│
└─ service/
   ├─ StrategyCatalogService.java
   └─ StrategyPatternDetector.java
```

`StockStrategy`와 `StrategyRegistry`는 전략 실행 계약의 핵심이므로 최상위 `strategy` package에 둔다.

---

## 5.2 Research — Backtest

### 현재

```text id="ezk7ex"
service/BacktestConfig.java
service/BacktestService.java
service/EventBacktestEngine.java

dto/BacktestRequest.java
dto/BacktestResponse.java
dto/BacktestTrade.java

repository/BacktestRepository.java
```

### 목표

```text id="xjgj4q"
research/backtest/
├─ BacktestConfig.java
├─ BacktestService.java
├─ EventBacktestEngine.java
│
├─ dto/
│  ├─ BacktestRequest.java
│  ├─ BacktestResponse.java
│  └─ BacktestTrade.java
│
└─ repository/
   └─ BacktestRepository.java
```

TASK-000에서는 백테스트 알고리즘을 변경하지 않는다.

`EventBacktestEngine` 내부 전략 실행 방식 변경 역시 TASK-001에서 수행한다.

---

## 5.3 Research — Walk Forward

현재 다음 클래스가 존재한다.

```text id="ugdujl"
dto/WalkForwardFold.java
dto/WalkForwardReport.java
dto/WalkForwardRequest.java

repository/WalkForwardRepository.java
```

이들을 다음으로 이동한다.

```text id="1dfr0p"
research/walkforward/
├─ dto/
│  ├─ WalkForwardFold.java
│  ├─ WalkForwardReport.java
│  └─ WalkForwardRequest.java
│
└─ repository/
   └─ WalkForwardRepository.java
```

Walk Forward 관련 service가 현재 다른 package에 존재한다면 참조 관계를 조사한다.

명확하게 Walk Forward만 담당하는 클래스라면:

```text id="9zbyf4"
research/walkforward/
```

로 이동한다.

복수 기능을 담당하여 안전하게 분리할 수 없다면 TASK-000에서 임의 분해하지 않고 Implementation Report에 기록한다.

---

## 5.4 Research — Box Evaluation

현재:

```text id="07ou7c"
research/boxevaluation/
```

은 이미 feature package 구조를 사용하고 있다.

**TASK-000에서 이동하거나 재구성하지 않는다.**

현재 구조를 유지한다.

---

## 5.5 Broker

현재:

```text id="ns16c7"
service/broker/BrokerAdapter.java
service/broker/KiwoomSimulationBrokerAdapter.java
```

목표:

```text id="b2lajm"
broker/
├─ BrokerAdapter.java
│
└─ kiwoom/
   └─ KiwoomSimulationBrokerAdapter.java
```

`BrokerAdapter`는 특정 증권사 구현과 분리된 broker-neutral 계약으로 유지한다.

Kiwoom 전용 구현은 `broker/kiwoom` 아래에 둔다.

---

## 5.6 Kiwoom External API Infrastructure

현재:

```text id="y15nmf"
client/KiwoomHttpClient.java
mapper/KiwoomResponseMapper.java
```

목표:

```text id="sdszme"
broker/kiwoom/
├─ client/
│  └─ KiwoomHttpClient.java
│
└─ mapper/
   └─ KiwoomResponseMapper.java
```

Kiwoom API와 직접 연결된 외부 통신 책임을 `broker.kiwoom` 경계로 모은다.

단, 시장데이터 API와 주문 API를 TASK-000에서 다시 세분화하지 않는다.

---

## 5.7 Common

현재 `config`와 `error` package는 TASK-000에서 **물리적으로 이동하지 않는다.**

이유:

- Spring configuration 전체에 영향
- Security 및 WebClient 설정 포함
- trading-specific exception과 Kiwoom-specific exception이 혼재
- TASK-000 범위를 불필요하게 확대할 가능성이 있음

따라서 Architecture Baseline에서 다음을 장기 목표로만 정의한다.

```text id="7yfrzt"
common/config
common/error
```

실제 분리는 후속 TASK에서 수행한다.

---

# 6. Explicitly Unmoved Areas

TASK-000에서는 다음 영역을 이동하지 않는다.

```text id="28ylxu"
Market Data
PAPER Trading
Order
Position
Risk
Auto Trading
Alert
Portfolio
Watchlist
Frontend
```

즉 다음과 같은 기존 클래스들은 현재 위치를 유지할 수 있다.

```text id="mv28fz"
PaperOrderService
PaperRiskService
PaperTradeCycleService
OrderStateMachine
AutoTradingControlService
PortfolioService
AlertService
...
```

이들은 관련 기능 TASK가 시작될 때 점진적으로 migration한다.

---

# 7. Controller 정책

TASK-000에서는 기존 최상위 `controller` package를 대규모로 이동하지 않는다.

단, package 이동으로 import 변경이 필요한 Controller는 정상적으로 수정한다.

새 Controller를 추가하지 않는다.

Controller feature migration은 별도 TASK에서 수행한다.

---

# 8. DTO 정책

기존 `dto` package 전체를 해체하지 않는다.

이번 TASK에서 명시된 다음 DTO만 이동한다.

```text id="kh6e7a"
StrategyDefinition
StrategyCandidate
StrategyScanResponse

BacktestRequest
BacktestResponse
BacktestTrade

WalkForwardFold
WalkForwardReport
WalkForwardRequest
```

나머지 DTO는 현재 위치를 유지한다.

---

# 9. Repository 정책

다음 Repository만 이동한다.

```text id="cm26fo"
StrategyDefinitionRepository
StrategySnapshotRepository

BacktestRepository
WalkForwardRepository
```

그 외 Repository는 이동하지 않는다.

---

# 10. Test Migration

이동한 production class와 직접 대응하는 테스트는 동일한 package 구조로 이동한다.

예:

```text id="zsfkv3"
service/strategy/StrategyRegistryTest
→ strategy/StrategyRegistryTest

service/EventBacktestEngineTest
→ research/backtest/EventBacktestEngineTest
```

실제 테스트 클래스 이름과 위치는 현재 repository를 조사하여 대응시킨다.

테스트의 의미 또는 assertion을 변경해서는 안 된다.

package/import 변경만으로 해결되지 않는 테스트 실패가 발생하면 원인을 보고한다.

---

# 11. Dependency Rules

TASK-000 완료 후 최소 다음 방향을 지향한다.

```text id="z99emh"
research
   ↓
strategy

trading
   ↓
strategy

broker implementation
   ↓
BrokerAdapter
```

금지되는 신규 의존 방향:

```text id="myczre"
strategy → research

strategy → controller

strategy → PAPER-specific trading implementation

BrokerAdapter → Kiwoom-specific implementation
```

TASK-000에서 기존의 모든 구조적 위반을 제거할 필요는 없지만 새로운 역방향 의존을 만들어서는 안 된다.

---

# 12. Behavior Preservation

TASK-000은 구조 리팩터링이다.

다음 동작을 변경하지 않는다.

```text id="58h1bs"
전략 계산식
전략 파라미터
전략 선택 규칙

백테스트 계산
수익률 계산
체결 simulation

Walk Forward 계산

PAPER 주문
위험 한도
자동매매

REST API path
request/response JSON contract

DB schema
DB data

Frontend behavior
```

---

# 13. 금지 사항

TASK-000에서는 다음을 하지 않는다.

```text id="lf2brx"
새로운 전략 구현

EventBacktestEngine 전략 실행 방식 변경
→ TASK-001 범위

전략 version 계약 변경
→ TASK-001 범위

동적 안정구간 구현

DB migration 추가

REST API redesign

Frontend 구조 변경

Spring Boot upgrade

Java version 변경

dependency upgrade

전체 프로젝트 DDD 변환

전체 controller/service/repository/dto 제거

죽은 코드 추정 삭제

대규모 naming 변경
```

---

# 14. Documentation Update

구조 변경 후:

```text id="ffm8w8"
docs/project-structure-map.md
```

를 실제 구조와 일치하도록 갱신한다.

최소 다음을 기록한다.

```text id="a5vdum"
Architecture Baseline v1

신규 feature package
- strategy
- research/backtest
- research/walkforward
- research/boxevaluation
- broker

기존 legacy technical package
- controller
- service
- dto
- repository

점진적 migration 정책
```

기존 `research/boxevaluation`이 feature package 전환의 선행 사례라는 점도 기록한다.

---

# 15. Acceptance Criteria

### AC-000-01

`docs/tasks/README.md`와 `docs/tasks/TASK-000.md`가 존재한다.

### AC-000-02

`strategy` package가 생성되고 Mandatory Migration Map에 지정된 전략 클래스가 해당 경계로 이동한다.

### AC-000-03

`research/backtest` package가 생성되고 지정된 Backtest 클래스가 이동한다.

### AC-000-04

`research/walkforward` package가 생성되고 지정된 Walk Forward 모델/Repository가 이동한다.

### AC-000-05

기존 `research/boxevaluation` 구조와 동작은 유지된다.

### AC-000-06

broker-neutral `BrokerAdapter`와 Kiwoom 전용 구현의 package 경계가 분리된다.

### AC-000-07

`KiwoomHttpClient`와 `KiwoomResponseMapper`가 `broker/kiwoom` 경계 아래에 위치한다.

### AC-000-08

이동 대상 production class에 대응하는 test package/import가 함께 정리된다.

### AC-000-09

`strategy → research` 신규 역방향 의존이 존재하지 않는다.

### AC-000-10

REST API contract가 변경되지 않는다.

### AC-000-11

DB schema/data migration이 발생하지 않는다.

### AC-000-12

전략 및 백테스트 계산 동작이 변경되지 않는다.

### AC-000-13

기존 backend 테스트가 회귀 없이 통과한다.

### AC-000-14

`project-structure-map.md`가 Architecture Baseline v1을 반영한다.

---

# 16. 검증

먼저 compile 수준 문제를 확인한다.

```powershell id="plujs3"
mvn -DskipTests compile
```

그다음:

```powershell id="eacww8"
mvn test
```

마지막으로 가능하면:

```powershell id="5y6n81"
mvn verify
```

Frontend는 TASK-000에서 수정하지 않는 것이 원칙이므로 frontend test는 필수 검증 대상이 아니다.

Frontend 파일을 변경했다면 변경 이유를 반드시 Implementation Report에 기록한다.

---

# 17. Codex 실행 절차

Codex는 다음 순서로 작업한다.

```text id="m6w5cz"
1. TASK-000 전체 읽기

2. git status 확인

3. 현재 package/class inventory 확인

4. Mandatory Migration Map과 실제 코드 대조

5. 실제 코드와 TASK 전제가 다른 부분 보고

6. 명시된 class만 이동

7. package declaration 수정

8. imports 수정

9. test package/import 수정

10. compile

11. test

12. verify

13. project-structure-map.md 갱신

14. git diff 검토

15. Implementation Report 작성
```

---

# 18. Existing Working Tree 보호

TASK 시작 전에 기존 미커밋 변경사항이 존재하면 해당 파일을 임의 수정, 삭제 또는 revert하지 않는다.

TASK-000과 충돌하는 기존 변경사항이 있으면 작업을 계속하기 전에 보고한다.

특히 TASK와 무관한 로컬 설정, session 파일, Codex 설정 파일 등을 commit 대상으로 포함하지 않는다.

---

# 19. Codex 자율 판단 범위

Codex가 자유롭게 결정할 수 있는 범위:

```text id="70ip5a"
import 정렬
package declaration 변경
컴파일을 위한 reference 수정
테스트 package 동기화
동일 동작을 유지하기 위한 최소 접근제어 수정
```

Codex가 임의로 결정해서는 안 되는 범위:

```text id="k1fl7o"
새 architecture 도입
새 abstraction/interface 생성
business logic 변경
DTO 의미 변경
DB 변경
API 변경
추가 feature 구현
Migration Map에 없는 대규모 이동
```

TASK 전제와 실제 코드가 달라 후자가 필요해 보이면 구현하지 말고 보고한다.

---

# 20. Implementation Report

Codex는 완료 후 다음 형식으로 보고한다.

```text id="4s2o8i"
TASK-000 Implementation Report

1. Summary

2. Migration Map

old package
→ new package

3. Changed Files

4. Unmoved / Legacy Areas

5. Dependency Check

6. Behavior Preservation

7. Acceptance Criteria
AC-000-01: PASS / FAIL
...
AC-000-14: PASS / FAIL

8. Tests
- mvn -DskipTests compile
- mvn test
- mvn verify

9. Existing / Environment Failures

10. Follow-up Candidates

11. Documentation Updates

12. Git Status

13. Commit
```

---

# 21. 완료 조건

다음을 모두 만족해야 TASK-000을 DONE으로 변경할 수 있다.

```text id="56jtxu"
Mandatory Migration 완료

Acceptance Criteria 전체 PASS

기존 기능 의미 유지

필수 테스트 통과

project-structure-map.md 갱신

ChatGPT 검수 PASS
```

TASK-000 완료 commit을 **Architecture Baseline v1**으로 사용한다.

그 이후 TASK-001부터 새로운 package 구조를 기준으로 개발한다.