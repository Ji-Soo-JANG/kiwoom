# STR-P06 1단계 실행 승인 요청서

- 문서 ID: `STR-P06-PH1-PLAN-01`
- 기준일: 2026-08-29
- 상태: **사용자 실행 승인 대기**
- 상위 승인: `STR-P06-APPROVAL-01`
- 목표: 평가 데이터 기반, 복수 후보 생성기, 서버 블라인드 API와 핵심 검증 완성

## 1. PM 승인 요청

STR-P06의 1단계를 아래 작업 묶음으로 구현할 것을 요청한다. 이 승인은 실제 주문이나 프론트 GUI 구현 승인이 아니라, 연구 워크벤치가 사용할 백엔드 기반 구현 승인이다.

## 2. 작업 묶음

| 작업 ID | 작업 | 주요 산출물 |
|---|---|---|
| `PH1-01` | 연구 전용 패키지와 계약 분리 | `research.boxevaluation` 하위 controller/service/repository/dto/model |
| `PH1-02` | 평가 저장 구조 추가 | Flyway `V25` 마이그레이션, 제약조건·인덱스·불변 상태 필드 |
| `PH1-03` | 시점 기준 입력 스냅샷 | cutoff 이하 일봉 조회, 입력 메타데이터와 SHA-256 해시 |
| `PH1-04` | 복수 박스권 후보 생성 | `NARROW`, `EXPANDED`, `CONNECTED` 결정론적 후보와 특징 |
| `PH1-05` | 블라인드 조회 API | 평가 과제·다음 항목·항목·일봉 조회, 미래 필드 미반환 |
| `PH1-06` | 평가 상태 API | draft 저장, commit 잠금, supersede 정정 기반 |
| `PH1-07` | 미래 결과 공개 경계 | reveal 권한·상태 계약과 결과 스냅샷 저장 기반 |
| `PH1-08` | 안전·재현성 시험 | 미래 누수, 해시, 반복 결정성, 동시 수정, 주문 0회 테스트 |
| `PH1-09` | 부서 산출물과 API 매뉴얼 | 데이터 사전, API 계약, QA 증적, 단계 완료 보고서 |

## 3. 구현 구조

기존 주문·전략 실행 코드와의 결합을 막기 위해 다음 경계를 사용한다.

```text
com.example.kiwoom.research.boxevaluation
├─ controller   연구 전용 /api/research/box-evaluations/**
├─ service      평가 생성·블라인드 조회·확정·공개
├─ repository   평가 테이블과 cutoff 강제 일봉 조회
├─ candidate    역방향 확장·변화점·후보 조합
├─ dto          공개 전/공개 후 응답의 물리적 분리
└─ model        평가 상태·라벨·품질·후보 유형
```

이 패키지는 `BrokerAdapter`, `PaperOrderService`, `LimitedTradingService`, `AutoTradingControlService`를 의존하지 않는다. 빌드 테스트에서 금지 의존성을 검사한다.

## 4. 1단계 기본 결정안

사용자 승인 후 다음 값을 초기 구현 기준으로 사용한다. 이는 수익성을 최적화한 값이 아니라 후보 다양성과 기능 검증을 위한 `research-v1` 기본값이다.

- 후보 유형: 유형별 대표 1개, 최대 3개
- 사용자 직접 구간: 허용하며 `USER_ADDED`로 분리
- 결과 공개 정책: 과제별 `AFTER_ITEM`, `AFTER_BATCH`, `NEVER`; 기본값 `AFTER_BATCH`
- 평가 라벨: `VALID_BOX`, `PARTIAL_BOX`, `NOT_BOX`, `INSUFFICIENT_DATA`, `DATA_QUALITY_ISSUE`
- 확정 정정: 원본을 보존하고 새 평가가 원본을 `supersede`
- 종목 표시: v1에서는 코드·종목명 표시, 이후 완전 블라인드 옵션 확장 가능
- 저장 방식: 일봉 원장은 참조하고 정렬된 입력 값의 해시·범위·행 수를 저장
- 미래 결과 산식: 1단계에서는 버전 가능한 저장·접근 경계만 구축하고 공식 성과값은 3단계 승인안에서 확정

후보 생성 수치 파라미터는 코드 상수가 아니라 버전이 붙은 설정 스냅샷으로 저장한다. 결과를 본 뒤 동일 버전의 값을 바꾸지 않는다.

## 5. 데이터베이스 원칙

`V25__create_box_evaluation_workbench.sql`에 다음 평가 개념을 추가한다.

- 평가 과제
- 평가 항목과 cutoff·입력 해시
- 자동 후보와 특징
- 사용자 draft와 revision
- 확정 평가와 immutable snapshot
- 정정 관계
- reveal 감사와 결과 스냅샷

모든 후보·사용자 경계 날짜는 cutoff 이하이어야 한다. DB 제약으로 표현 가능한 규칙은 DB에서 강제하고, 나머지는 서비스 검증과 통합 테스트를 병행한다.

기존 `daily_candle`, 전략 스캔, 주문, 포지션과 자동매매 테이블은 변경하지 않는다.

## 6. API 1단계 범위

- `GET /api/research/box-evaluations/batches`
- `POST /api/research/box-evaluations/batches`
- `GET /api/research/box-evaluations/batches/{batchId}/next`
- `GET /api/research/box-evaluations/items/{itemId}`
- `GET /api/research/box-evaluations/items/{itemId}/candles`
- `PUT /api/research/box-evaluations/items/{itemId}/draft`
- `POST /api/research/box-evaluations/items/{itemId}/commit`
- `POST /api/research/box-evaluations/items/{itemId}/supersede`
- `POST /api/research/box-evaluations/items/{itemId}/reveal`
- `GET /api/research/box-evaluations/items/{itemId}/outcome`

공개 전 응답에는 미래 일봉, 미래 파생값, 성과, 결과 존재 여부나 결과 ID를 포함하지 않는다. 결과 계산이 아직 승인되지 않은 경우 reveal/outcome은 명확한 `NOT_CONFIGURED` 상태를 반환하고 임의 산식을 사용하지 않는다.

## 7. 테스트와 승인 차단

### 필수 테스트

- cutoff 이후 극단값을 추가해도 후보·특징·입력 해시가 동일한지 검사
- 요청 날짜·limit 조작에도 cutoff 이후 일봉이 반환되지 않는지 검사
- 동일 입력·설정에서 후보 ID·경계·특징·순서가 동일한지 검사
- draft revision 충돌과 중복 commit 멱등성 검사
- 확정 평가 update/delete와 원본 덮어쓰기 차단
- 공개 전 DTO 직렬화 결과에 금지 필드가 없는지 검사
- 연구 패키지에서 주문 서비스 의존과 주문 테이블 변경이 없는지 검사
- 기존 전체 백엔드 테스트와 Flyway 마이그레이션 검사

### 즉시 중단하고 재승인할 조건

- 기존 일봉 원장 자체를 수정해야 하는 경우
- 주문 또는 자동매매 서비스 의존이 필요해지는 경우
- 브라우저로 미래 데이터를 전송해야만 구현 가능한 경우
- 확정 평가 원본을 덮어써야 하는 경우
- 승인 범위를 넘어 공식 성과 산식이나 전략 승격 규칙을 확정해야 하는 경우

## 8. 완료 보고

1단계 완료 시 다음 자료를 사용자에게 함께 제출한다.

- 구현 파일과 DB 변경 목록
- API 사용 예시와 개발 서버 확인 방법
- 후보별 실제 DB 사례 결과
- 미래 누수·재현성·주문 분리 테스트 결과
- 전략·데이터·백엔드·QA·위험 부서의 서면 검토
- 남은 문제와 2단계 GUI 구현 승인 요청서

1단계 커밋은 기능 단위로 나누어 남기고, 전체 테스트가 끝난 뒤 푸시는 사용자 지시에 따른다.

## 9. 승인 요청 문구

> **STR-P06 1단계를 `PH1-01~09` 범위와 본 문서의 기본 결정안으로 구현한다. 승인 차단 조건이 발생하면 작업을 확대하지 않고 사용자에게 다시 승인받으며, 1단계 완료 후 부서 검토 결과와 STR-P06 2단계 GUI 실행안을 별도로 승인 요청한다.**

