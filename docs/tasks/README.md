# Kiwoom TASK 개발 프로세스

이 디렉터리는 Kiwoom 프로젝트의 구현 작업을 TASK 단위로 정의하고 추적하기 위한 문서를 관리한다.

프로젝트의 기능 추가, 구조 변경, 리팩터링, 버그 수정 등 주요 코드 변경은 가능한 한 TASK 문서를 기준으로 수행한다.

---

## 1. 기본 개발 흐름

Kiwoom 프로젝트는 다음 흐름을 기본 개발 프로세스로 사용한다.

```text
요구사항 / 문제 발견
        ↓
ChatGPT
설계 및 영향 범위 검토
        ↓
TASK 문서 작성
        ↓
READY
        ↓
Codex
코드 분석 및 구현
        ↓
테스트
        ↓
Implementation Report
        ↓
ChatGPT
TASK 기준 검수
        ↓
PASS ──────────→ DONE
 │
 └─ 수정 필요 → Codex 재작업
```

### ChatGPT의 역할

ChatGPT는 주로 다음을 담당한다.

- 요구사항 분석
- 현재 설계와의 정합성 검토
- 구현 방향 설계
- TASK 범위 결정
- Acceptance Criteria 정의
- 구현 결과 검수
- 후속 TASK 결정

### Codex의 역할

Codex는 TASK 문서를 기준으로 다음을 담당한다.

- 관련 코드 확인
- TASK 범위 내 구현
- 테스트 작성 및 실행
- 회귀 영향 확인
- 구현 결과 보고

Codex가 TASK에 정의되지 않은 요구사항이나 아키텍처를 임의로 추가하지 않는 것을 기본 원칙으로 한다.

---

## 2. TASK 파일 명명 규칙

TASK 파일은 다음 형식을 사용한다.

```text
TASK-NNN.md
```

예:

```text
TASK-000.md
TASK-001.md
TASK-002.md
```

번호는 생성 순서대로 증가시키며 기존 번호를 재사용하지 않는다.

TASK가 취소되더라도 해당 번호는 유지한다.

---

## 3. TASK 상태

각 TASK는 다음 상태 중 하나를 가진다.

### DRAFT

설계 중인 TASK.

범위 또는 Acceptance Criteria가 아직 확정되지 않았다.

Codex 구현을 시작하지 않는다.

### READY

설계와 구현 범위가 확정되어 구현 가능한 상태.

Codex에 전달할 수 있다.

### IN_PROGRESS

Codex가 구현 중인 상태.

### REVIEW

구현과 테스트가 완료되어 검수를 기다리는 상태.

### BLOCKED

외부 문제 또는 선행 작업 때문에 진행할 수 없는 상태.

BLOCKED 사유를 TASK에 기록한다.

### DONE

Acceptance Criteria와 필수 테스트를 만족하고 검수가 완료된 상태.

---

## 4. TASK 기본 구조

각 TASK 문서는 원칙적으로 다음 항목을 포함한다.

```text
# TASK-NNN — 제목

Status:
Priority:
Dependencies:

## 1. 목적

## 2. 배경 / 현재 문제

## 3. 관련 요구사항 및 설계

## 4. 구현 범위

## 5. 비범위

## 6. 상세 구현 요구사항

## 7. Acceptance Criteria

## 8. 필수 테스트

## 9. 구현 시 준수사항

## 10. 완료 보고 형식
```

TASK 특성상 필요하지 않은 항목은 생략할 수 있지만, 최소한 다음은 명확해야 한다.

- 무엇을 해결하는가
- 무엇을 변경하는가
- 무엇을 변경하지 않는가
- 언제 완료로 판단하는가
- 어떻게 검증하는가

---

## 5. Acceptance Criteria

Acceptance Criteria는 TASK 완료 여부를 판단할 수 있도록 검증 가능한 형태로 작성한다.

각 조건에는 번호를 부여한다.

예:

```text
AC-001
백테스트 실행 시 선택된 strategyCode와 strategyVersion의 전략이 실제 실행된다.

AC-002
존재하지 않는 전략 버전을 요청하면 명확하게 실패한다.

AC-003
기존 PAPER 거래 기능에 회귀가 발생하지 않는다.
```

가능한 한 다음과 같은 모호한 표현은 피한다.

```text
적절하게 처리한다.
문제없이 동작한다.
구조를 개선한다.
성능을 최적화한다.
```

완료 여부를 테스트 또는 코드 검토로 판단할 수 있어야 한다.

---

## 6. TASK 범위 관리

하나의 TASK는 하나의 명확한 목적을 가진다.

TASK 구현 과정에서 새로운 문제가 발견되더라도 현재 TASK 완료에 반드시 필요하지 않다면 임의로 함께 구현하지 않는다.

별도의 작업이 필요한 경우 후속 TASK 후보로 기록한다.

예:

```text
TASK-012 구현 중 기존 데이터 모델 문제 발견

현재 TASK 수행에 필수
→ TASK-012 범위에서 최소 수정

현재 TASK와 독립적
→ 후속 TASK 후보로 기록
```

대규모 리팩터링을 기능 TASK에 섞지 않는 것을 원칙으로 한다.

---

## 7. 설계 문서와의 관계

TASK는 상위 설계 문서를 임의로 변경하는 수단이 아니다.

기본적인 문서 우선순위는 프로젝트의 `specification-index.md`를 따른다.

주요 기준 문서는 다음과 같다.

```text
requirements-definition.md
        ↓
basic-design.md
        ↓
detailed-design.md
        ↓
ui-design.md
        ↓
TASK
        ↓
Implementation
```

TASK 작성 과정에서 기존 설계와 충돌하는 요구사항이 발견되면 구현보다 설계 결정을 먼저 수행한다.

필요한 경우 상위 설계 문서를 먼저 수정한 뒤 TASK를 READY 상태로 변경한다.

---

## 8. Codex 구현 원칙

Codex는 TASK를 구현할 때 다음 원칙을 따른다.

1. TASK와 관련된 코드부터 확인한다.
2. TASK의 Acceptance Criteria를 기준으로 구현한다.
3. 비범위에 명시된 기능은 구현하지 않는다.
4. 기존 공개 인터페이스와 데이터의 호환성을 가능한 한 유지한다.
5. 불필요한 대규모 리팩터링을 하지 않는다.
6. 기존 테스트를 임의로 삭제하거나 무력화하지 않는다.
7. 테스트 실패를 해결하기 위해 검증 기준을 낮추지 않는다.
8. TASK 전제와 실제 코드가 다르면 임의로 우회하기보다 차이를 보고한다.
9. TASK 범위를 넘어서는 개선점은 후속 작업으로 보고한다.
10. 구현 후 지정된 테스트를 실행한다.

---

## 9. 테스트 원칙

TASK는 구현 코드뿐 아니라 검증 방법까지 포함한다.

필요에 따라 다음을 사용한다.

- Unit Test
- Integration Test
- Repository Test
- API Test
- Regression Test
- Frontend Test
- E2E Test

기존 테스트가 존재한다면 회귀 여부도 확인한다.

전체 테스트 실패 시 다음을 구분한다.

```text
이번 TASK 변경으로 발생한 실패
기존부터 존재하던 실패
환경 의존 실패
```

테스트를 통과시키기 위해 기존 테스트를 이유 없이 삭제하거나 assertion을 약화하지 않는다.

---

## 10. Implementation Report

Codex는 구현 완료 후 최소 다음 내용을 보고한다.

```text
TASK-NNN Implementation Report

1. Summary
- 구현 내용

2. Changed Files
- 변경 파일
- 변경 이유

3. Implementation Details
- 주요 구현 내용

4. Acceptance Criteria
AC-001: PASS / FAIL
AC-002: PASS / FAIL
...

5. Tests
- 실행한 테스트
- 결과

6. Remaining Issues
- 미해결 사항
- 후속 TASK 후보

7. Design Impact
- 설계 문서 변경 필요 여부

8. Commit
- commit hash
```

FAIL이 존재하는 TASK는 원칙적으로 DONE 처리하지 않는다.

---

## 11. TASK 검수

Codex 구현 후 ChatGPT에서 다음을 검토한다.

- TASK 요구사항 충족 여부
- Acceptance Criteria 충족 여부
- 비범위 침범 여부
- 기존 설계와의 정합성
- 테스트 적절성
- 회귀 위험
- 불필요한 변경 여부

검수 결과는 다음 중 하나로 판단한다.

```text
PASS
수정 필요
BLOCKED
```

PASS된 TASK만 DONE으로 처리한다.

---

## 12. Git 원칙

가능한 한 하나의 TASK는 독립적으로 추적 가능한 변경 단위로 유지한다.

권장 커밋 메시지 형식:

```text
feat: complete TASK-012 strategy execution contract
fix: complete TASK-018 order reconciliation
refactor: complete TASK-000 project structure baseline
docs: define TASK-021 live readiness rules
```

TASK 구현 중 관계없는 변경을 같은 커밋에 포함하지 않는다.

---

## 13. 기본 원칙

Kiwoom 프로젝트의 TASK 개발 프로세스는 다음 원칙을 우선한다.

```text
설계 후 구현한다.

작은 단위로 변경한다.

완료 조건을 먼저 정의한다.

테스트 가능한 요구사항을 작성한다.

TASK 범위를 넘어가지 않는다.

기존 동작을 이유 없이 변경하지 않는다.

설계와 코드의 차이를 방치하지 않는다.

Codex는 구현하고,
ChatGPT는 설계와 검수를 담당한다.
```

TASK는 단순한 작업 메모가 아니라 **설계와 구현 사이의 계약**으로 취급한다.