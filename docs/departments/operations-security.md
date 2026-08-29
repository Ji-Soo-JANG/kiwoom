# 운영·보안팀 현행 조사 보고서

- 문서 상태: 부서 조사 완료 / 부서 간 검토 대기
- 조사 기준일: 2026-08-29
- 담당 부서: 운영·보안팀
- 연결 요구사항: OPS-001~003, NFR-001~007, TRD-002~004, DAT-003~004, UI-001·003~004
- 조사 범위: 프로파일·환경 설정, 인증·인가, 외부 API 통신, 로그·추적, 모니터링, DB·migration, 로컬 기동/종료/백업 스크립트, CI와 운영 문서

## 1. 결론

현재 운영 구조는 **한 대의 Windows 개발 PC에서 PAPER 스윙 검증을 자동 시작·종료하는 로컬 환경**이다. 환경변수 기반 비밀 주입, 관리자 경로 보호, 구조화 로그, trace ID, health/Prometheus, Flyway, DB 백업·복원, CI 검사가 존재한다.

그러나 실전 자동매매 운영에 필요한 고가용성보다 먼저 해결해야 할 기본 통제가 남아 있다. CSRF가 비활성화되어 있고, 단일 메모리 사용자가 시작 때 생성되며, `.env` 평문 파일을 직접 읽는다. Windows 작업 스케줄러는 평일만 판단하며 실제 휴장일 기동을 막지 못하고, 종료 스크립트는 15:40에 프로세스를 강제 종료한 뒤 DB를 정지한다. 장 후 대사 실패·미체결·열린 포지션·백업 성공을 확인하는 종료 게이트가 없다.

따라서 현 상태는 로컬 PAPER 연구 운영에는 적합하지만, NFR-001~003과 OPS-001~003을 만족하는 실전 운영 환경은 아니다.

## 2. 현재 구성

| 영역 | 현재 구현 | 판정 |
|---|---|---|
| 실행 환경 | Java 21, Spring Boot WebFlux, PostgreSQL Docker, React 정적 자산 | 로컬 개발 기준 |
| 설정 | 환경변수와 dev/prod properties 분리 | 기본 구조 존재 |
| 비밀정보 | 키·시크릿·DB·로그인 비밀번호를 환경변수로 참조 | `.env` 평문 취급 보강 필요 |
| 시작 | PostgreSQL 대기 → JAR 빌드 → 숨김 Java 프로세스 시작 | PAPER 편의 자동화 |
| 종료 | PID 확인 → Java 강제 종료 → PostgreSQL stop | 안전 종료 보장 없음 |
| 일정 | 평일 08:40 시작, 15:40 종료; 내부 09:05 흐름, 1분 감시, 15:35 대사 | 휴장·지연·실패 조정 부족 |
| 인증 | form login, BCrypt, 단일 사용자 USER+ADMIN | 개인 로컬 환경용 |
| 인가 | 거래·장중·관찰·관리 API는 ADMIN | 경로 기반 기본 통제 |
| 세션 | HttpOnly, SameSite Strict | TLS/Secure 정책 미명시 |
| CSRF | 전체 비활성 | 상태 변경 API 위험 |
| 관측 | JSON console, trace ID, health/info/prometheus | 보존·경보·감사 미완성 |
| DB 변경 | Flyway V1~V24 | 재현성 기반 존재 |
| 백업 | 수동 pg_dump, 확인 옵션이 필요한 복원 | 자동화·암호화·복구시험 미완성 |
| CI | 백엔드 검증/커버리지, 프론트 품질·감사·E2E·빌드 | 공급망·비밀 스캔 보강 필요 |

## 3. 현재 운영 흐름

### 3.1 장 전 시작

1. Windows 작업 스케줄러가 평일 08:40에 `start-paper-swing.ps1`을 실행한다.
2. 프로젝트 루트 `.env`를 Process 환경변수로 적재한다.
3. `dev`, `PAPER`, 거래 스케줄러와 스윙 모니터를 활성화한다.
4. Docker PostgreSQL을 시작하고 대기한다.
5. 테스트를 건너뛰고 JAR를 빌드하여 숨김 프로세스로 실행한다.
6. PID와 표준/오류 로그를 `.runtime`에 기록한다.

dev 진단기는 키·시크릿 누락과 기본 로그인 비밀번호를 거부하지만, DB 연결·Flyway·키움 인증·데이터 신선도·브로커 대사 전체를 하나의 준비검사 결과로 저장하지는 않는다.

### 3.2 장중·장 후

- 내부 스케줄러가 거래일인지 확인한 후 데이터 동기화, 전략 스캔, 관찰 저장을 실행한다.
- 장중에는 거래시간일 때 PAPER 스윙 포지션을 1분마다 감시한다.
- 15:35에 PAPER 내부 대사와 결과 요약을 로그로 남긴다.
- 15:40에는 대사 성공 여부와 무관하게 프로세스를 강제 종료하고 DB 컨테이너를 정지한다.

### 3.3 장애 진단

- 각 HTTP 응답에 `X-Trace-Id`를 넣고 완료 로그에 method/path/status/duration을 구조화한다.
- 키움 HTTP 클라이언트는 연결/응답 제한 시간과 일시 오류 재시도를 갖는다.
- health와 Prometheus가 노출되지만 자동 경보, 로그 회전·보존, 운영 실행 이력과 장애 티켓 연결은 없다.

## 4. 강점

- 소스 설정은 비밀값을 직접 포함하지 않고 환경변수를 참조한다.
- dev 프로파일이 누락된 키·시크릿과 기본 비밀번호를 시작 시 거부한다.
- `/api/trading/**` 등 민감 경로는 ADMIN 역할을 요구한다.
- 세션 쿠키에 HttpOnly와 SameSite Strict가 설정됐다.
- 구조화 JSON 로그와 요청 trace ID가 장애 상관분석의 기반을 제공한다.
- 외부 API timeout, 제한된 연결 풀, 선택적 재시도와 메트릭이 있다.
- Flyway migration, 백업·확인형 복원, CI 품질 게이트가 재현성과 변경 안전성을 높인다.
- 시작 스크립트가 중복 PID를 확인하고 PostgreSQL 준비 완료를 기다린다.

## 5. 문제와 기술 부채

### 5.1 실전 전 필수 차단사항

| 차단사항 | 영향 | 연결 요구사항 |
|---|---|---|
| CSRF 전체 비활성 | 로그인 세션을 이용한 상태 변경 요청 위조 위험 | NFR-001 |
| `.env` 평문 비밀 적재 | 로컬 계정·백업·오류 공유를 통한 키 노출 위험 | NFR-001·007 |
| 쿠키 Secure/TLS 정책 없음 | 비암호화 구간 세션 탈취 가능 | NFR-001 |
| 단일 인메모리 관리자 | 권한 분리·비밀번호 교체·행위자 추적 부족 | NFR-001·002 |
| 운영자 조작 감사 원장 없음 | 자동매매/한도/킬스위치 변경의 부인 방지 불가 | NFR-002 |
| 준비검사 상태 영속화 없음 | 데이터·인증·대사 실패에도 실행 단계 판단 불가 | OPS-001 |
| 강제 종료 및 종료 게이트 없음 | 주문·대사 처리 중 중단, 장 후 결과 유실 가능 | OPS-002 |
| 재시작 브로커 복구 없음 | 중복 주문·미체결 누락 가능 | OPS-003, TRD-002~004 |
| 수동 휴일 목록 | 임시공휴일·시장 변경 시 오작동 가능 | OPS-001 |
| 로그 로컬 콘솔 의존 | 장기 보존·검색·변조 방지·개인정보 마스킹 보장 없음 | NFR-002·007 |
| 백업 자동화/암호화/복구시험 없음 | 장애 시 복구 가능 시점과 손실 범위 불명확 | NFR-003 |
| 주문 API 재시도 정책 미분리 | 조회 재시도와 주문 재시도를 동일 취급할 위험 | NFR-003 |

### 5.2 운영상 추가 문제

- 시작 때 `package -DskipTests`를 수행하므로 장 전 빌드 시간과 실패가 운전 준비시간을 잠식한다. 승인된 불변 산출물을 미리 만들고 해시를 검증하는 방식이 적절하다.
- 내부 09:05 스캔과 외부 08:40 시작의 성공 여부를 중앙 실행 레코드로 연결하지 않는다.
- `AtomicBoolean`은 단일 프로세스 중복 실행만 막는다. 다중 인스턴스나 재시작 경계의 분산 잠금·멱등 실행은 보장하지 않는다.
- 종료 스크립트가 `Stop-Process -Force`를 사용하여 graceful shutdown과 진행 트랜잭션 완료를 기다리지 않는다.
- actuator health는 무인 운영에 필요한 키움 인증, 데이터 최신일, 스케줄러 상태, 대사 상태를 상세 컴포넌트로 제공하지 않는다.
- CORS 기본값에 개발 origin이 포함되어 있으며 실전 프로파일의 허용 origin 고정 검증이 없다.
- 요청 로그는 path를 기록하므로 향후 계좌/주문 식별자가 URL에 들어가면 마스킹 정책이 필요하다.

## 6. 유지·수정·제외 제안

### 유지할 부분

- 환경별 설정과 기본 `SIGNAL_ONLY`/스케줄 비활성값
- 시작 시 필수 설정 진단과 비밀값 비로그 원칙
- 관리자 경로 분리, BCrypt, HttpOnly/SameSite 세션
- trace ID, 구조화 로그, health/Prometheus
- Flyway와 명시적 DB 백업·복원 절차
- CI의 테스트·정적 검사·의존성 감사

### 수정할 부분

- 상태 변경 API에 CSRF 보호를 복원하거나 세션 대신 명시적 API 인증과 Origin 검증을 적용한다.
- 키·시크릿을 OS 자격 증명 저장소 또는 제한 권한 secret store에서 읽고 파일·로그·백업 제외를 검증한다.
- LIVE는 TLS, Secure 쿠키, 강한 관리자 인증, 역할 분리, 세션 만료와 재인증을 요구한다.
- `automation_run`에 장 전 검사, 각 단계 시작/종료, 실패, 재시도와 종료 결과를 저장한다.
- 준비검사에 DB/Flyway, 키움 인증, 호출 가능, 거래일, 시간 동기, 데이터 신선도, 브로커 대사, 킬 스위치와 위험 한도를 포함한다.
- 종료 요청 → 신규 작업 차단 → 주문/체결 조회 → 대사 → 결과/백업 → graceful shutdown 순서로 바꾼다.
- 공식 KRX 거래일 소스와 서버 시간 동기 상태를 사용한다.
- 로그 회전·보존·민감정보 마스킹·무결성 및 경보 정책을 정한다.
- 암호화 백업 자동화, 보존 기간, 정기 복원 시험과 RPO/RTO를 확정한다.
- 조회와 주문의 timeout/retry/circuit-breaker 정책을 분리한다. 모호한 주문 결과는 절대 자동 재전송하지 않는다.

### 제거 또는 운영 경로에서 제외할 부분

- 기본 로그인 비밀번호 `change-me`는 dev 진단으로만 막지 말고 모든 실제 운전 프로파일에서 허용하지 않는다.
- LIVE 운전에서 `.env` 평문 파일과 장 전 소스 빌드를 사용하지 않는다.
- 대사 결과와 무관한 고정 시각 강제 종료를 실전 경로에서 사용하지 않는다.
- 로컬 console 로그만을 공식 감사 기록으로 인정하지 않는다.

## 7. 부서 간 의존성

| 관련 부서 | 필요한 입력/합의 |
|---|---|
| 매매시스템팀 | 브로커 헬스·대사·미확정 주문 상태, graceful shutdown 가능 지점 |
| 위험관리팀 | 준비검사 차단 항목, 킬 스위치와 재개 승인 권한, 알림 심각도 |
| 데이터팀 | 최신 거래일, 수집 완료·품질 상태, 공식 거래일 캘린더 |
| 백엔드팀 | CSRF/인증 구조, 감사 이벤트, actuator 구성, 분산 실행 잠금 |
| QA팀 | 비밀 누출, 권한, 장애 복구, 강제 종료, 백업 복원과 시간 경계 시험 |
| UI팀 | 준비검사, 실제 주문 가능 여부, 대사 오류, 다음 조치의 비기술적 표현 |
| PM/사용자 | LIVE 운영 시간, 알림 수단, 복구 목표와 비상 연락·승인 체계 |

## 8. 운영·보안팀 제안

1. 구현 전 `LIVE 운영 준비검사 목록`과 차단/경고 등급을 부서 공동 안건으로 확정한다.
2. PAPER 외부 모의투자 단계부터 장 전·장중·장 후 실행을 DB에 기록하고 무인 운영 실패율을 측정한다.
3. 실전 주문 어댑터보다 먼저 비밀 저장, CSRF/관리자 인증, 감사 로그, 안전 종료를 P0으로 처리한다.
4. 키움 API 장애, DB 장애, 네트워크 단절, PC 절전/재부팅, 시간 오차 훈련을 수행한다.
5. LIVE 전 최소 연속 운영 기간 동안 준비검사·대사·백업·복원 시험이 모두 통과해야 한다.

## 9. 미결정 쟁점

- 로컬 PC 단독 운전 유지 여부와 정전·인터넷 장애 대비 방식
- OS 자격 증명 저장소 및 키 회전 주기
- 운영자/승인자 계정 분리 수준과 추가 인증 방식
- 알림 채널, 심각도, 미응답 시 자동 조치
- 로그·주문·계좌·전략 데이터 보존 기간과 암호화 범위
- RPO/RTO, 백업 주기, 외부 매체 보관 여부
- 장 후 대사 실패 시 서버를 계속 유지할 최대 시간
- 임시 휴장과 조기 폐장 정보를 제공할 공식 캘린더 소스
- 자동 업데이트 허용 여부와 승인된 버전 배포/롤백 절차

## 10. 근거 파일

- `src/main/resources/application.properties`
- `src/main/resources/application-dev.properties`
- `src/main/resources/application-prod.properties`
- `src/main/resources/logback-spring.xml`
- `src/main/java/com/example/kiwoom/config/SecurityConfig.java`
- `src/main/java/com/example/kiwoom/config/LocalConfigurationDiagnostics.java`
- `src/main/java/com/example/kiwoom/config/KiwoomApiProperties.java`
- `src/main/java/com/example/kiwoom/config/RequestTraceFilter.java`
- `src/main/java/com/example/kiwoom/client/KiwoomHttpClient.java`
- `src/main/java/com/example/kiwoom/service/MarketCalendarService.java`
- `src/main/java/com/example/kiwoom/service/TradingWorkflowScheduler.java`
- `src/main/java/com/example/kiwoom/service/SwingPositionMonitoringScheduler.java`
- `scripts/start-paper-swing.ps1`, `scripts/stop-paper-swing.ps1`
- `scripts/register-paper-swing-tasks.ps1`
- `scripts/backup-db.ps1`, `scripts/restore-db.ps1`
- `.github/workflows/ci.yml`
- `docs/local-development.md`, `docs/user-manual.md`, `docs/trading-safety.md`
