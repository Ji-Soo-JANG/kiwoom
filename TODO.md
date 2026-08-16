# Kiwoom 프로젝트 작업 목록

마지막 정리: 2026-08-16

## P0 — 실행 및 테스트 안정화

- [x] `CorsConfig`와 `WebClientConfig`에 중복 선언된 `corsWebFilter` 빈을 하나로 통합했다.
  - 완료 기준: Spring 애플리케이션 컨텍스트가 빈 이름 충돌 없이 시작된다.
- [x] `KiwoomApiServiceTest`를 외부 API와 환경변수에 의존하지 않는 단위 테스트로 전환했다.
  - MockWebServer 또는 WebClient mock을 사용한다.
  - null/빈 종목 코드, 정상 응답, HTTP 오류, 잘못된 JSON, API 오류 코드를 검증한다.
  - 실제 키움 API 호출 테스트는 별도의 통합 테스트로 분리하고 명시적으로 활성화할 때만 실행한다.
- [x] Windows의 `mvnw.cmd` 실행 오류를 수정했다.
  - 완료 기준: Windows에서 `.\mvnw.cmd clean test`가 실행된다.
- [x] 애플리케이션 기동 테스트를 추가했다.
  - 완료 기준: `@SpringBootTest` 또는 적절한 slice test로 주요 빈 구성이 검증된다.

## P1 — 키움 API 연동 안정성

- [x] 접근 토큰의 만료 시각을 관리하고 만료 전에 재발급하도록 개선했다.
  - 현재 `Mono.cache()`는 최초 토큰을 계속 재사용하므로 만료 후 요청 실패 가능성이 있다.
- [x] HTTP 401 인증 실패 시 토큰을 한 번 갱신한 뒤 요청을 재시도하도록 구현했다.
- [x] 429 및 일시적인 5xx 오류에 제한적인 재시도와 지수 백오프를 적용했다.
- [x] 연결/응답 timeout과 최대 연결 수를 `application.properties`에서 조정할 수 있도록 설정화했다.
- [x] 키움 HTTP·재시도는 `client`, JSON 변환은 `mapper`, 조회 흐름은 `service`로 분리했다.
- [x] API 전체 응답 본문 로그를 제거해 민감정보 및 과도한 데이터가 기록되지 않게 했다.

## P1 — API와 오류 처리

- [x] 전역 예외 처리기(`@RestControllerAdvice`)를 추가하고 오류 응답 형식을 통일했다.
  - 예: `code`, `message`, `timestamp`, `path`
- [x] 종목 코드를 6자리 숫자로 검증하고 다중 조회의 빈 값, 중복 값, 최대 개수를 제한했다.
- [x] `baseDate`가 실제 유효한 `yyyyMMdd` 날짜인지 검증했다.
- [x] 다중 종목 조회는 한 종목이라도 실패하면 전체 요청을 실패 처리하도록 정책과 테스트를 추가했다.
- [x] OpenAPI/Swagger 문서를 도입하고 README의 API 명세를 실제 구현과 동기화했다.
  - 단일 조회 실제 경로: `GET /api/kiwoom/stock-price/{code}`
  - 일봉 조회 경로: `GET /api/kiwoom/stock-price/{code}/daily`

## P1 — 설정 및 보안

- [x] 키움 설정을 `@ConfigurationProperties`로 묶고 시작 시 필수값을 검증한다.
- [x] 개발/테스트/운영 프로필을 분리했다.
- [x] CORS 허용 origin을 `CORS_ALLOWED_ORIGINS` 환경 설정으로 이동했다.
- [x] API 키와 secret이 소스, 로그, 정적 파일, 빌드 산출물에 포함되지 않는지 점검했다.
- [x] 취약점·업데이트 후보를 점검하고 Dependabot 및 프레임워크 업그레이드 정책을 추가했다.

## P2 — 프론트엔드 품질

- [x] API 요청의 로딩·빈 결과·네트워크 오류·잘못된 입력 상태를 접근 가능한 상태 메시지로 표시한다.
- [x] 종목 검색 폼, 결과 목록, 일봉 차트에 컴포넌트 테스트를 추가했다.
- [x] 프론트엔드 ESLint와 Vitest 명령을 추가하고 CI 검사에 연결했다.
- [x] Vite 빌드 결과가 Spring의 `static` 디렉터리에 배포되도록 설정했다.
- [x] 프론트엔드 차트 번들을 지연 로딩 및 별도 chunk로 분리했다.
- [x] 폼 키보드 제출, label·ARIA 상태, 포커스·색상 대비, 모션 감소 및 모바일 레이아웃을 점검했다.

## P2 — 기능 확장

- [x] 관심 종목(Watchlist)을 메모리에 저장하고 조회·삭제하는 기능을 구현했다.
- [x] 포트폴리오 매입가·수량을 관리하고 키움 현재가 기준 수익률을 계산한다.
- [x] RSI(14), MACD(12·26·9) 기술적 지표를 계산해 차트에 표시했다.
- [x] 현재가 조회에 설정 가능한 짧은 TTL 로컬 캐시를 적용해 중복 키움 API 호출을 줄였다.
  - 먼저 로컬 캐시 필요성을 측정하고, 다중 인스턴스가 필요할 때 Redis를 검토한다.

## P2 — 문서 및 자동화

- [x] README의 프로젝트 트리를 새 패키지 구조(`config`, `controller`, `dto`, `service`, `error`)로 갱신한다.
- [ ] Java 21, Node.js, 환경변수 설정 및 로컬 실행 절차를 실제 환경에서 재검증한다.
- [x] 백엔드 테스트와 프론트엔드 빌드를 실행하는 GitHub Actions CI를 추가했다.
- [x] 운영 상태 확인용 Actuator health/info endpoint와 노출 범위를 문서화했다.

## 완료된 작업

- [x] Java 클래스를 역할별 패키지로 분리했다.
  - `config`: Spring/WebClient/CORS 설정
  - `controller`: REST 및 웹 진입점
  - `dto`: 현재가 및 일봉 응답 모델
  - `service`: 키움 API 연동 및 조회 로직
- [x] 이동된 클래스의 package/import와 서비스 테스트 패키지를 정리했다.
- [x] DTO getter를 명시적으로 선언해 현재 빌드 환경에서 메인 및 테스트 소스 컴파일을 확인했다.
- [x] MockWebServer 기반 테스트와 Spring 컨텍스트 기동 테스트를 추가했다.
- [x] 종목 코드·기준일자·다중 조회 개수 검증과 공통 API 오류 응답을 추가했다.
