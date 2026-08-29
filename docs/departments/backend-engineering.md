# 백엔드팀 프로젝트 현황 조사서

- 문서 상태: 부서 조사 초안
- 조사 기준일: 2026-08-29
- 담당 부서: 백엔드팀
- 검토 대상 요구사항: `BUS`, `DAT`, `STR`, `ENT`, `EXT`, `MKT`, `RES`, `PRM`, `TRD`, `RSK`, `OPS`, `NFR`
- 작성 범위: 현재 소스와 설정을 조사한 결과이며 목표 기능이 이미 구현됐다는 뜻이 아니다.

## 1. 부서 결론

현재 백엔드는 시세 조회 애플리케이션을 넘어 데이터 저장, 전략 탐색, 검증, PAPER 주문 상태, 제한 매매 관찰까지 확장되어 있다. 특히 PostgreSQL 기반 데이터 이력, 전략 버전 레지스트리, 이벤트 백테스트, 주문 상태 머신, 위험 한도는 실전 시스템의 기반으로 유지할 가치가 있다.

그러나 실전 자동매매의 핵심인 키움 주문 제출·조회 어댑터, 브로커 원장 대사, 진입 계획, 시장 상태 게이트와 재시작 복구는 아직 목표 설계에 머물러 있다. `LIVE`라는 명칭을 가진 어댑터도 실제 전송을 차단하는 스텁이므로 현재 시스템을 실전 주문 가능 상태로 해석하면 안 된다.

## 2. 현재 구현 구성

### 2.1 기술 기반

| 영역 | 현재 구현 |
|---|---|
| 런타임 | Java 21, Spring Boot 3.2.5 |
| 웹/API | Spring WebFlux, Reactor `Mono`·`Flux` |
| 저장소 | Spring Data R2DBC, PostgreSQL, Flyway |
| 외부 연동 | `WebClient` 기반 키움 HTTP 클라이언트 |
| 인증 | 단일 로컬 사용자, 폼 로그인, 역할 기반 API 보호 |
| 운영 | Actuator health·Prometheus, 구조화 로그, 요청 추적 ID |
| API 계약 | springdoc OpenAPI와 저장된 `docs/openapi.json` |

JDBC와 R2DBC가 함께 존재하는 이유는 Flyway 마이그레이션에는 JDBC, 업무 저장소에는 R2DBC를 사용하기 때문이다. 현재로서는 단순 중복 의존성으로 제거하면 안 된다.

### 2.2 계층과 역할

```text
controller
  → service / strategy / broker
    → repository 또는 KiwoomHttpClient
      → PostgreSQL 또는 키움 API
```

- `controller`: 인증, 시세·검색·계좌, 데이터 수집, 전략·검증, PAPER·제한 매매, 장중 관찰 API
- `service`: 키움 응답 가공, 수집 스케줄, 전략 스캔, 백테스트, 주문·위험·관찰 흐름
- `service.strategy`: 세 전략 구현과 레지스트리
- `service.broker`: 브로커 추상 계약과 차단된 키움 모의 어댑터
- `repository`: R2DBC 기반 시장·전략·주문·성과·사용자 기능 저장
- `config/error`: 인증, CORS, 연결 설정, 예외 표준화와 추적

### 2.3 현재 데이터 흐름

1. 키움 API에서 종목·시세·일봉·계좌 정보를 가져온다.
2. 종목 마스터와 일봉을 PostgreSQL에 증분 저장하고 품질 상태를 관리한다.
3. 전략 레지스트리에서 버전을 선택해 DB 일봉으로 후보를 스캔한다.
4. 스캔 결과, 특징값과 관찰 표본을 저장한다.
5. PAPER 흐름은 후보, 주문, 체결, 포지션, 청산 주기와 성과를 로컬 원장에 기록한다.
6. 장 전·장중·장 후 스케줄은 설정으로 활성화되며 기본값은 비활성이다.

## 3. API 현황

| API 묶음 | 현재 역할 | 목표와의 관계 |
|---|---|---|
| `/api/kiwoom/**` | 현재가·차트·검색·순위·계좌·전략·백테스트·데이터 관리 | `DAT`, `STR`, `RES`, `TRD-005` 기반 |
| `/api/trading/**` | 모드, 자동매매 설정, PAPER 주문·위험·대사·킬 스위치 | `TRD`, `RSK`, `UI-003`의 부분 구현 |
| `/api/trading/limited/**` | 후보 승인, PAPER 거래 주기와 성과 | 검증 단계 자산이며 최종 무승인 자동 운전과 분리 필요 |
| `/api/intraday/**` | 장중 이벤트 저장·재생·봉 생성 | `EXT`, `NFR-006` 기반 |
| `/api/observations/**` | 신호 관찰과 예상·실제 차이 저장 | `PRM`, `RES` 기반 |
| `/api/watchlist`, `/api/alerts`, `/api/portfolio` | 보조 사용자 기능 | 핵심 자동매매 우선순위 밖, 레거시 격리 후보 |

보안 설정상 거래·장중·관찰·관리 API는 `ADMIN`, 일반 조회와 관심종목은 인증 사용자가 접근한다. CSRF는 현재 비활성화되어 있어 로컬 단일 사용자라는 운영 전제를 벗어나면 재검토가 필요하다.

## 4. 요구사항별 구현 판정

| 요구사항 | 현재 판정 | 근거와 차이 |
|---|---|---|
| `DAT-001~004` | 부분 구현 | DB 일봉·품질·수집 상태는 있으나 수정주가 확정성과 장 전 전체 준비 게이트는 추가 검증 필요 |
| `STR-001`, `STR-003~005` | 부분 구현 | 구조화된 전략 결과와 현재 패턴 v3가 있으나 판정값 연구·설명 데이터 보강 필요 |
| `STR-002` | 미구현 | API와 UI가 60~1,200일 고정 창을 사용한다. 동적 상태 변화 탐지가 필요 |
| `RES-001~002` | 부분 구현 | 이벤트 백테스트·워크포워드·관찰 저장은 있으나 승격 게이트와 충분한 외부 표본 기준 미완성 |
| `ENT-001~002` | 미구현 | 패턴 후보와 주문 가능한 진입 계획, 무효화 가격·예상 손실 계약이 분리되지 않음 |
| `EXT-001~002` | 부분 구현 | PAPER 스윙 청산과 감시는 있으나 실계좌 부분체결·보호 주문 정책 없음 |
| `TRD-001` | 초기 기반 | `BrokerAdapter`가 있지만 `place`만 제공하며 실전 어댑터가 없음 |
| `TRD-002~004` | PAPER 부분 구현 | 로컬 상태 머신과 대사는 있으나 브로커 주문 ID·실원장 수렴·재시작 복구 미구현 |
| `RSK-001~004` | PAPER 부분 구현 | 비중·노출·일손실·낙폭·킬 스위치는 있으나 손실 예산, 시장·데이터 준비 게이트와 보호 매도 분리 필요 |
| `OPS-001~003` | 부분 구현 | 스케줄러는 존재하나 단일 실행 원장, 단계별 준비 검사, 실브로커 우선 복구가 없음 |
| `NFR-001~008` | 부분 구현 | 환경변수, 추적 ID, 테스트·메트릭은 있으나 감사 원장·개인정보 보관 정책·실전 SLA가 미완성 |

## 5. 강점과 유지 대상

- Flyway V1~V24로 데이터 구조 변화가 추적된다.
- 저장 일봉, 시점 기준 종목 유니버스, 품질 이슈, 전략 스냅샷은 재현성의 좋은 기반이다.
- 전략 인터페이스와 레지스트리는 전략 버전 병렬 비교에 적합하다.
- 주문 상태 머신, 의사결정 ID, 부분 체결 픽스처는 `TRD-002~003` 검증 기반이다.
- 외부 API가 없어도 전략·주문 흐름을 검사할 수 있어 `NFR-006`에 유리하다.
- 전역 예외 응답과 trace ID로 401·502·키움 오류의 추적 기반이 있다.
- 운영 스케줄이 기본 비활성이라 개발 중 의도하지 않은 자동 실행 위험을 낮춘다.

## 6. 문제와 기술 부채

### P0: 실전 전 반드시 수정

1. `KiwoomSimulationBrokerAdapter`는 `mode()`가 `LIVE`이지만 외부 제출은 항상 거부한다. 이름·모드·실제 능력의 의미를 분리해야 한다.
2. `BrokerAdapter` 계약이 주문 생성만 지원한다. 정정, 취소, 주문 조회, 미체결, 체결, 잔고, 포지션, 호출 제한과 모호한 응답 확인 계약이 필요하다.
3. 패턴 후보가 곧 주문 의사결정으로 연결될 여지가 있다. `ENT-001~002`의 진입 계획과 위험 승인을 도메인 객체로 분리해야 한다.
4. 데이터·계좌·미체결·시장 상태를 종합한 LIVE readiness 결과가 없다.
5. 애플리케이션 재시작 시 로컬 DB보다 브로커를 먼저 조회하여 수렴하는 실행 절차가 없다.

### P1: 구조 개선

1. `Paper*` 명칭이 서비스·DTO·API 전반에 확산되어 브로커 중립 도메인 전환 비용이 커지고 있다.
2. `KiwoomController`가 시세, 검색, 전략, 검증, 데이터 관리자, 계좌까지 맡는다. 기능별 컨트롤러 분리가 필요하다.
3. 전략 후보 API의 `boxRangeDays`가 `STR-002`와 충돌한다.
4. 다수 스케줄러가 각자 `subscribe()`하고 메모리 `AtomicBoolean`으로 중복 실행을 막는다. 재시작·다중 실행·단계 감사에는 영속 실행 잠금과 실행 원장이 필요하다.
5. 호출 제한을 고려한 중앙 주문 큐와 요청 우선순위가 명시적으로 보이지 않는다.
6. Maven 설정의 오래된 설명성 주석과 빈 프로젝트 메타데이터는 정리 대상이다.

### P2: 격리 또는 우선순위 하향

- 수동 포트폴리오 API·DB는 사용자 데이터 보존 때문에 즉시 삭제하지 않는다. 읽기 전용 레거시 경계를 명시한 뒤 별도 승인으로 폐기한다.
- 관심종목, 일반 알림, 종목 비교 지원 API는 자동매매 핵심 경로와 패키지·화면에서 분리한다.
- 빌드 산출물인 `src/main/resources/static`은 직접 수정하지 않고 React 빌드에서만 생성한다.

## 7. 백엔드팀 제안

| 순서 | 제안 | 연결 요구사항 | 산출물 |
|---:|---|---|---|
| 1 | 동적 안정 구간 탐지 계약과 전략 특징 스키마 정의 | `STR-001~005` | 알고리즘 제안서, 특징 사전, 회귀 데이터셋 |
| 2 | `PatternCandidate → EntryPlan → RiskDecision → OrderIntent` 단계 분리 | `ENT`, `RSK`, `NFR-008` | 도메인·API 계약서 |
| 3 | 브로커 중립 OMS 계약 확장 | `TRD-001~004` | 상태 전이표, 멱등·재시도 정책 |
| 4 | 준비 검사와 영속 자동운전 실행 원장 추가 | `OPS`, `RSK-003` | readiness 항목, 실행 상태도 |
| 5 | 키움 모의 어댑터 현장 검증 후 최소 실전 어댑터 개발 | `BUS-001`, `CAP-002`, `PRM` | 공식 API 매핑, 호출 제한 시험, 대사 보고서 |
| 6 | 컨트롤러와 PAPER 명칭을 기능 경계에 맞춰 점진 분리 | 유지보수성 | 호환성·마이그레이션 계획 |

## 8. 타 부서 의존성과 조율 요청

| 대상 부서 | 필요한 결정·자료 |
|---|---|
| 전략 연구팀 | 동적 안정 구간 정의, 필수 특징, 전략 버전과 무효화 조건 |
| 데이터팀 | 수정주가 출처, 기업행사 처리, 거래일·VI·거래정지·업종 데이터 계약 |
| 위험관리팀 | 주문당 손실 예산, 일·월 손실, 보호 매도 우선순위와 실패 폐쇄 기준 |
| 매매 시스템팀 | 키움 주문·정정·취소·조회 필드, 호출 제한, 브로커 상태 수렴 규칙 |
| 프론트엔드·UX팀 | readiness·차단 사유·주문 상태를 표시할 응답 형식 |
| QA팀 | 외부 API 픽스처, 장애·재시작·동시성·대사 승인 시나리오 |
| 보안·운영팀 | 비밀정보 저장, 감사 로그 보존, PC 기동·종료·백업 정책 |

## 9. 미결정 쟁점

- 실제 키움 REST 주문 API의 최종 계약과 모의·실전 환경 차이
- 시장가·지정가·조건부 주문 사용 정책과 허용 슬리피지
- 수정주가 소급 변경 시 과거 신호·성과 재현 정책
- 스케줄 실행 중 PC 절전·네트워크 단절·프로세스 재기동 처리
- 매수 차단 중 보호 매도를 허용할 최소 데이터와 브로커 상태
- 단일 사용자 로컬 환경에서도 CSRF와 추가 조작 인증을 적용할 범위

## 10. 근거 파일

- `pom.xml`
- `src/main/resources/application.properties`
- `src/main/resources/db/migration/V1__create_watchlist_and_portfolio.sql` ~ `V24__select_current_swing_strategy.sql`
- `src/main/java/com/example/kiwoom/config/SecurityConfig.java`
- `src/main/java/com/example/kiwoom/client/KiwoomHttpClient.java`
- `src/main/java/com/example/kiwoom/controller/KiwoomController.java`
- `src/main/java/com/example/kiwoom/controller/TradingController.java`
- `src/main/java/com/example/kiwoom/service/StrategyScanService.java`
- `src/main/java/com/example/kiwoom/service/TradingWorkflowScheduler.java`
- `src/main/java/com/example/kiwoom/service/SwingPositionMonitoringScheduler.java`
- `src/main/java/com/example/kiwoom/service/OrderStateMachine.java`
- `src/main/java/com/example/kiwoom/service/PaperRiskService.java`
- `src/main/java/com/example/kiwoom/service/broker/BrokerAdapter.java`
- `src/main/java/com/example/kiwoom/service/broker/KiwoomSimulationBrokerAdapter.java`
- `docs/requirements-definition.md`, `docs/basic-design.md`, `docs/detailed-design.md`
