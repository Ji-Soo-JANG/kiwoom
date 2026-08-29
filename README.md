# 📈 Kiwoom 주가 조회 시스템

설치, 실행 및 자동매매 사용 방법은 [사용자 매뉴얼](docs/user-manual.md)을 참고하세요.
제품 목표와 이후 구현 기준은 [설계 문서 색인](docs/specification-index.md)에서 확인할 수 있습니다.

Spring Boot + React로 구성된 실시간 주식 종목 정보 조회 플랫폼입니다.

## 🏗️ 프로젝트 구조

```
kiwoom/
├── src/
│   ├── main/
│   │   ├── java/com/example/kiwoom/
│   │   │   ├── KiwoomApplication.java          # Spring Boot 메인 애플리케이션
│   │   │   ├── client/                          # 키움 HTTP 호출 및 재시도
│   │   │   ├── config/                          # WebClient 및 CORS 설정
│   │   │   ├── controller/                      # REST API 및 웹 페이지 진입점
│   │   │   ├── dto/                             # 현재가·일봉 응답 모델
│   │   │   ├── error/                           # 공통 API 오류 응답 및 예외 처리
│   │   │   ├── mapper/                          # 키움 JSON 응답 변환
│   │   │   ├── repository/                      # R2DBC 영속 저장소
│   │   │   └── service/                         # 토큰·캐시 및 조회 흐름
│   │   └── resources/
│   │       └── static/                         # Git에서 관리하는 React 빌드 결과물
│   └── test/
│       └── java/com/example/kiwoom/             # 서비스 단위·기동 테스트
├── frontend/                                   # React 프론트엔드
│   ├── src/
│   │   ├── App.jsx                            # 메인 React 컴포넌트
│   │   ├── App.css                            # 스타일
│   │   ├── main.jsx                           # React 진입점
│   │   └── index.css                          # 글로벌 스타일
│   ├── index.html                             # HTML 템플릿
│   ├── vite.config.js                         # Vite 설정
│   └── package.json                           # npm 의존성
├── pom.xml                                     # Maven 설정
├── .idea/                                      # IDE 설정 (Java 21 설정됨)
└── README.md
```

## 🚀 시작하기

### 필수 요구사항
- Java 21 (LTS)
- Node.js 22 (LTS) 및 npm 10
- Maven은 별도 설치 없이 Maven Wrapper(`mvnw`, `mvnw.cmd`) 사용

2026-08-16에 Microsoft OpenJDK 21.0.12, Node.js 22.23.2, npm 10.9.8로
클린 테스트·빌드·애플리케이션 기동과 `/actuator/health` 응답을 확인했습니다.

Windows PowerShell에서 기본 Java가 21이 아니라면 현재 터미널에만 Java 21을 지정합니다.

```powershell
$env:JAVA_HOME='C:\path\to\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version
.\mvnw.cmd --version
```

### 1️⃣ 백엔드 준비

필수 환경변수:

```text
KIWOOM_APP_KEY=키움 앱 키
KIWOOM_SECRET_KEY=키움 시크릿 키
```

선택 환경변수:

```text
KIWOOM_CONNECT_TIMEOUT=3s
KIWOOM_RESPONSE_TIMEOUT=10s
KIWOOM_MAX_CONNECTIONS=20
KIWOOM_MAX_RETRIES=2
KIWOOM_RETRY_BACKOFF=200ms
KIWOOM_CURRENT_PRICE_CACHE_TTL=3s
KIWOOM_DAILY_PRICE_CACHE_TTL=10m
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:8080
POSTGRES_PASSWORD=안전한 데이터베이스 비밀번호
APP_USERNAME=관리자 로그인 아이디
APP_PASSWORD=안전한 로그인 비밀번호
DATABASE_USERNAME=kiwoom
DATABASE_R2DBC_URL=r2dbc:postgresql://localhost:5432/kiwoom
DATABASE_JDBC_URL=jdbc:postgresql://localhost:5432/kiwoom
```

PostgreSQL은 Docker Compose로 실행할 수 있습니다. 최초 실행 시 Flyway가 관심 종목,
포트폴리오, 거래 내역, 목표가 알림 테이블을 자동 생성하며 데이터는 Docker volume에 유지됩니다.

```powershell
Copy-Item .env.example .env
# .env의 키와 비밀번호를 실제 값으로 변경
docker compose up -d --wait postgres
```

DB 컨테이너만 중지할 때는 `docker compose stop postgres`를 사용합니다. 데이터를 포함한
volume까지 삭제하는 `docker compose down -v`는 초기화가 필요한 경우에만 사용합니다.

의존성 업데이트 주기와 보안 반영 기준은 [`docs/dependency-policy.md`](docs/dependency-policy.md)를 따릅니다.
목표가·기술지표 앱 알림의 데이터 모델과 API 계획은 [`docs/alert-design.md`](docs/alert-design.md)를 따릅니다.
로컬 빌드 산출물과 템플릿 엔진 결정은 [`docs/build-decisions.md`](docs/build-decisions.md)를 따릅니다.
IntelliJ, Docker와 Vite 개발 서버 실행은 [`docs/local-development.md`](docs/local-development.md)를 따릅니다.
자동매매 실행 모드, PAPER 주문, 위험 한도와 킬 스위치는 [`docs/trading-safety.md`](docs/trading-safety.md)를 따릅니다.

자동매매 관련 기본 환경 설정은 다음과 같습니다.

```dotenv
TRADING_MODE=SIGNAL_ONLY
PAPER_INITIAL_CASH=10000000
TRADING_MAX_POSITION_RATE=0.10
TRADING_MAX_GROSS_EXPOSURE_RATE=0.50
TRADING_MAX_DAILY_LOSS_RATE=0.02
TRADING_MAX_DRAWDOWN_RATE=0.10
TRADING_MAX_OPEN_POSITIONS=5
```

현재 버전은 키움 실주문 어댑터가 없어 `LIVE`를 요청해도 실주문을 전송하지 않습니다.

로컬 개발 환경을 한 번에 시작하려면 다음 명령을 사용합니다.

```powershell
.\scripts\start-dev.ps1
```

로컬 DB 관리:

```powershell
.\scripts\backup-db.ps1
.\scripts\restore-db.ps1 -BackupFile .\backups\kiwoom-날짜.sql -ConfirmRestore
.\scripts\reset-db.ps1 -ConfirmReset
```

```powershell
# Windows
.\mvnw.cmd clean test
.\mvnw.cmd package -DskipTests
```

```bash
# macOS/Linux
./mvnw clean test
./mvnw package -DskipTests
```

### 2️⃣ 프론트엔드 개발 (선택)

```powershell
cd frontend
npm ci
npm run dev
```

브라우저: `http://localhost:5173`

### 3️⃣ 프로덕션 빌드

```powershell
# 이미 자동으로 수행됨
# frontend/src → ../src/main/resources/static/로 빌드됨

cd frontend
npm run lint
npm run format:check
npm run types:check
npm test
npm run build
cd ..
.\mvnw.cmd package -DskipTests
```

### 4️⃣ 애플리케이션 실행

```powershell
# IDE에서 KiwoomApplication 실행
# 또는 명령어로:
java -jar target/kiwoom-0.0.1-SNAPSHOT.jar
```

브라우저: `http://localhost:8080`

화면별 주소는 종목 검색 `/`, 차트 `/chart`, 종목 발견 `/discover`, 관심 종목 `/watchlist`, 포트폴리오 `/portfolio`, 알림
`/alerts`입니다. 각 주소로 직접 접근하거나 새로고침해도 React 화면이 열립니다.

보호된 API는 세션 로그인이 필요합니다. `APP_USERNAME`, `APP_PASSWORD`로 로그인하면
발급된 `SESSION` 쿠키가 이후 요청에 사용됩니다. 운영 환경에서는 기본값을 사용하지 마세요.

```powershell
curl.exe -c session.txt -X POST http://localhost:8080/login `
  -H "Content-Type: application/x-www-form-urlencoded" `
  --data-urlencode "username=$env:APP_USERNAME" `
  --data-urlencode "password=$env:APP_PASSWORD"
curl.exe -b session.txt http://localhost:8080/api/portfolio
```

로그아웃은 `POST /logout`입니다. 헬스 체크와 정적 화면을 제외한 주가·관심 종목·
포트폴리오 API는 인증이 필요하고, Swagger와 관리 endpoint는 관리자 권한이 필요합니다.

Spring 프로필은 `dev`, `test`, `prod`를 사용합니다. 운영 프로필에서는 Swagger UI와 OpenAPI JSON이 비활성화됩니다.

## 📚 API 명세

### REST API 엔드포인트

#### 1. 단일 종목 조회
```
GET /api/kiwoom/stock-price/005930
```

**응답:**
```json
{
  "code": "005930",
  "currentPrice": "70500",
  "changeAmount": "500",
  "changeRate": "0.71"
}
```

#### 2. 다중 종목 조회
```
GET /api/kiwoom/stock-prices?codes=005930,000660,035420
```

**응답:**
```json
[
  {
    "code": "005930",
    "currentPrice": "70500",
    "changeAmount": "500",
    "changeRate": "0.71"
  },
  ...
]
```

다중 조회는 원자적 실패 정책을 사용합니다. 요청한 종목 중 하나라도 키움 API 조회에
실패하면 일부 성공 목록을 반환하지 않고 전체 요청을 공통 오류 응답(HTTP 502)으로 처리합니다.
중복 종목 코드는 한 번만 조회하며 한 번에 최대 20개까지 요청할 수 있습니다.

#### 3. 기간별 차트 조회
```
GET /api/kiwoom/stock-price/005930/daily?baseDate=20260816&period=day&limit=500
```

`baseDate`를 생략하면 서울 시간 기준 오늘 날짜를 사용합니다. `period`는
`day`(일봉, 기본), `week`(주봉), `month`(월봉), `year`(년봉)를 지원하며 각각 키움
`ka10081`, `ka10082`, `ka10083`, `ka10094`로 조회합니다. `limit`는 최대 500건까지
요청할 수 있고 차트 화면에서 종류(일봉·주봉·월봉·년봉)와 조회 기간(1개월~전체)을
바꿀 수 있습니다.

#### 3-1. 종목 검색과 자동완성

```http
GET /api/kiwoom/stocks/search?q=삼성&market=KOSPI&productType=STOCK
GET /api/kiwoom/stocks/search?q=ㅅㅅㅈㅈ&market=ALL&productType=ALL
GET /api/kiwoom/stocks/search?q=ETF&market=ALL&productType=ETF
```

`market`은 `ALL`, `KOSPI`, `KOSDAQ`을 지원하고 `productType`은 `ALL`, `STOCK`,
`PREFERRED`, `ETF`, `ETN`, `REIT`, `SPAC`을 지원합니다. 키움 `ka10099`의
코스피·코스닥 종목 목록을 서버에서 12시간 캐시하며 최대 20개의 후보를 반환합니다.
종목 코드, 종목명, 띄어쓰기를 생략한 종목명, 한글 초성, 상품유형 이름으로 검색할 수
있으며 각 후보에 시장과 상품유형을 함께 표시합니다.

#### 3-2. 종목 발견 순위

```http
GET /api/kiwoom/market-rankings
```

종목 발견 화면의 `카드 설정`에서 급등·급락·거래량 카드의 표시 여부와 순서, 카드별
5개/10개 표시, 전체/KOSPI/KOSDAQ 시장을 선택할 수 있습니다. 설정은 브라우저에 저장되어
다음 실행에도 유지됩니다. 시장별 API 조회도 지원합니다.

```http
GET /api/kiwoom/market-rankings?market=KOSDAQ
```

### 시장 데이터 로컬 저장

종목 탐색 화면에서 `다음 20개 종목 수집`을 누르면 종목 마스터와 최근 일봉을 PostgreSQL에
저장합니다. 수집은 미수집 종목, 이전 실패 종목, 가장 오래전에 갱신한 종목 순서로 진행되며
같은 종목·거래일 데이터는 갱신되므로 중복되지 않습니다.

```http
GET /api/kiwoom/admin/market-data
POST /api/kiwoom/admin/market-data/sync?limit=20
```

최초 적재 시 버튼을 여러 번 실행해 전체 종목을 순차 수집할 수 있습니다. 한 요청의 최대
처리량은 500종목이며 키움 API 호출 제한을 고려해 작은 단위부터 사용하는 것을 권장합니다.
개발 서버를 장 마감 후 자동 갱신하려면 다음 환경변수를 설정합니다.

```text
MARKET_DATA_SCHEDULER_ENABLED=true
MARKET_DATA_SYNC_BATCH_SIZE=100
MARKET_DATA_SYNC_CRON=0 10 16 * * MON-FRI
```

키움 `ka10027`과 `ka10030`을 이용해 급등주·급락주·거래량 상위 종목을 최대 10개씩
반환합니다. 서버 응답은 30초 캐시되며 `/discover` 화면에서 세 목록을 동시에 확인하고
종목을 클릭해 현재가와 일봉 차트로 이동할 수 있습니다.

#### 3-3. 키움 계좌 포트폴리오

```http
GET /api/kiwoom/account/portfolio
```

키움 `ka00001`로 토큰에 연결된 계좌번호를 확인하고 `kt00018` 평가잔고에서 추정자산,
총 매입·평가금액, 손익, 수익률과 실제 보유종목을 조회합니다. 화면에서 포지션을 직접
등록하지 않으며 결과는 10초간 캐시됩니다.

#### 4. 헬스 체크

Spring Boot Actuator의 `GET /actuator/health`를 사용합니다.

#### 5. 레거시 로컬 포트폴리오 거래 API

현재 포트폴리오 화면에서는 사용하지 않지만 기존 데이터 호환을 위해 API는 유지합니다.
매수·매도 거래를 등록하면 포지션 수량과 이동평균 매입가가 함께 갱신됩니다. 매수
수수료는 평균 매입가에 포함하고, 매도 수수료와 세금은 실현 손익에서 차감합니다.

```http
POST /api/portfolio/transactions
Content-Type: application/json

{
  "code": "005930",
  "type": "BUY",
  "quantity": 10,
  "price": 70000,
  "fee": 500,
  "tax": 0
}
```

```http
GET /api/portfolio/transactions
```

보유 수량을 초과한 매도와 0 이하의 수량·가격은 HTTP 400으로 거부됩니다.

#### 6. 목표가 앱 알림

사용자별 목표가 규칙을 만들고 현재가를 평가합니다. 같은 조건이 계속 충족되는 동안에는
이벤트를 한 번만 만들며, 조건을 벗어났다가 다시 진입하면 새 이벤트를 생성합니다.

```http
POST /api/alerts/rules
Content-Type: application/json

{
  "code": "005930",
  "conditionType": "PRICE_ABOVE",
  "threshold": 80000
}
```

- `GET /api/alerts/rules`: 규칙 목록
- `PATCH /api/alerts/rules/{id}`: 목표가 또는 활성 상태 변경
- `DELETE /api/alerts/rules/{id}`: 규칙 삭제
- `POST /api/alerts/evaluate`: 활성 규칙을 현재가로 평가
- `GET /api/alerts/events?unreadOnly=true`: 이벤트 목록
- `POST /api/alerts/events/{id}/read`: 이벤트 읽음 처리

`conditionType`은 가격 조건 `PRICE_ABOVE`, `PRICE_BELOW`, RSI 조건 `RSI_ABOVE`,
`RSI_BELOW`, MACD 조건 `MACD_CROSS_UP`, `MACD_CROSS_DOWN`을 지원합니다. MACD 교차는
`threshold`를 `null`로 전송합니다. 모든 알림 데이터는 로그인 사용자별로 분리되며
규칙을 삭제해도 이미 발생한 이벤트 이력은 유지됩니다.

### 오류 응답

잘못된 입력은 HTTP 400, 키움 연동 오류는 HTTP 502로 반환됩니다.

키움 연동 오류는 원인에 따라 다음 코드로 구분됩니다.

- `KIWOOM_AUTHENTICATION_FAILED`: 키움 인증 또는 토큰 갱신 실패
- `KIWOOM_RATE_LIMITED`: 키움 호출 한도 초과
- `KIWOOM_STOCK_NOT_FOUND`: 존재하지 않는 종목
- `KIWOOM_MARKET_CLOSED`: 장 운영시간 외 요청
- `KIWOOM_UPSTREAM_UNAVAILABLE`: 키움 서비스 일시 장애
- `KIWOOM_INVALID_RESPONSE`: 키움 응답 형식 오류

```json
{
  "code": "INVALID_REQUEST",
  "message": "종목 코드는 6자리 숫자여야 합니다",
  "timestamp": "2026-08-16T00:00:00Z",
  "path": "/api/kiwoom/stock-price/ABC"
}
```

### API 문서

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- 저장소 API 계약: `docs/openapi.json`

프론트 API 타입은 저장소 계약으로부터 생성합니다. API를 변경했다면 계약을 함께 수정한 뒤
`npm run types:generate`를 실행합니다. CI는 런타임 Springdoc 문서에 모든 계약 경로가 있는지와
생성 타입이 최신인지 확인합니다.

### 실제 키움 API 통합 테스트

실제 외부 API 테스트는 기본 `test`와 CI에서 실행되지 않습니다. `.env.example`을 참고해
모의투자는 `KIWOOM_PAPER_*`, 운영은 `KIWOOM_PROD_*` 환경변수에 URL과 자격증명을 각각
설정하고 `KIWOOM_LIVE_ENV`로 하나를 선택합니다. 운영 자격증명 사용 시 실제 조회 권한과
호출 한도에 유의하세요.

```powershell
$env:KIWOOM_LIVE_ENV='PAPER'
$env:KIWOOM_PAPER_BASE_URL='모의투자 API URL'
$env:KIWOOM_PAPER_APP_KEY='모의투자 앱 키'
$env:KIWOOM_PAPER_SECRET_KEY='모의투자 시크릿 키'
$env:KIWOOM_LIVE_STOCK_CODE='005930'
.\mvnw.cmd verify -Pkiwoom-live
```

프로필을 명시했는데 선택 환경의 설정이 없으면 테스트는 실패하면서 누락된 환경변수를
표시합니다. 실제 키와 secret은 `.env`, 소스 코드, 빌드 로그에 저장하지 않습니다.

### 운영 상태

- Health: `GET /actuator/health`
- Info: `GET /actuator/info`
- Prometheus 로컬 지표: `GET /actuator/prometheus` (관리자 로그인 필요)
- Actuator 노출은 `health`, `info`, `prometheus`로 제한하며 `info`와 `prometheus`는 관리자만 접근합니다.

Prometheus 형식으로 키움 API 응답 시간·성공/실패 요청 수·재시도 사유·토큰 갱신·
현재가/일봉 캐시 적중 및 엔트리 수를 확인할 수 있습니다.

모든 요청에는 `X-Trace-Id` 응답 헤더가 포함되고 오류 응답의 `traceId`와 JSON 로그의
`traceId`가 동일하게 기록됩니다. 클라이언트가 8~64자의 영문·숫자·`_`·`-` 형식으로
헤더를 전달하면 해당 값을 유지하며, 그렇지 않으면 서버가 UUID를 생성합니다. API 키,
secret, 접근 토큰 값은 로그에 기록하지 않습니다.

## 🛠️ 기술 스택

### 백엔드
- **Spring Boot 3.2.5** - 웹 프레임워크
- **Spring WebFlux** - 반응형 웹 프레임워크
- **Spring Security** - 세션 로그인 및 API 접근 제어
- **Reactor** - 비동기 처리 (Mono, Flux)
- **Jackson** - JSON 처리
- **PostgreSQL 15 / R2DBC** - 관심 종목과 포트폴리오 영속 저장
- **Flyway** - 데이터베이스 스키마 마이그레이션

### 프론트엔드
- **React 19** - UI 라이브러리
- **React Router** - 검색·관심 종목·포트폴리오·알림 화면 라우팅
- **TanStack Query** - 서버 데이터 캐시·mutation·제한적 재시도 관리
- **Vite 8** - 번들러 및 개발 서버
- **Vanilla CSS** - 스타일링

## ✨ 주요 기능

✅ **단일 종목 조회** - 특정 코드의 주가 정보 조회  
✅ **다중 종목 조회** - 쉼표 구분으로 여러 종목 일괄 조회  
✅ **실시간 API** - WebFlux 기반 비동기 처리  
✅ **모던 UI** - React 기반 반응형 인터페이스  
✅ **CORS 지원** - 크로스 오리진 요청 처리  
✅ **에러 핸들링** - 일관된 JSON 오류 응답 제공  
✅ **거래 원장** - 매수·매도 기록, 이동평균 매입가 및 실현 손익 계산
✅ **사용자 보안** - 세션 인증과 사용자별 관심 종목·포트폴리오 데이터 격리
✅ **주가·지표 알림** - 사용자별 목표가·일간 급등락·RSI·MACD 규칙, 경계 교차 평가, 읽음 이벤트 관리

## 🧪 테스트

```bash
# 백엔드 테스트, JaCoCo 리포트 및 커버리지 기준 검증
./mvnw clean verify

# Windows
.\mvnw.cmd clean verify

# 프론트엔드 단위 테스트와 커버리지 기준 검증
cd frontend
npm run test:coverage

# Chromium 브라우저 E2E
npx playwright install chromium
npm run test:e2e
```

백엔드는 라인·명령 80%, 브랜치 60%, 메서드 75%를 요구하며 리포트는
`target/site/jacoco/index.html`에 생성됩니다. 프론트엔드는 라인 74%, 구문 70%,
브랜치 65%, 함수 55%를 요구하며 리포트는 `frontend/coverage/index.html`에 생성됩니다.

## 📝 개발 노트

### Java 버전
- 원래 Java 22 사용으로 컴파일 에러 발생 (`ExceptionInInitializerError`, `TypeTag :: UNKNOWN`)
- **해결:** Java 21 (LTS)로 다운그레이드
- 설정 파일:
  - `pom.xml` - Maven 컴파일러 플러그인 (source/target = 21)
  - `.idea/misc.xml` - IDE 설정 (languageLevel = JDK_21)

### 반응형 프로그래밍
- `Mono<T>` - 0개 또는 1개 원소를 갖는 반응형 스트림
- `Flux<T>` - 0개 이상의 원소를 갖는 반응형 스트림
- `.flatMap()` - 비동기 체이닝
- `.collectList()` - Flux → List 변환

### 프론트엔드 프록시
개발 중 프론트엔드에서 백엔드 API 호출:
```javascript
// vite.config.js의 프록시 설정으로 자동 처리
/api/kiwoom/* → http://localhost:8080/api/kiwoom/*
```

## 🔧 트러블슈팅

### IDE에서 컴파일 에러
```
java: java.lang.ExceptionInInitializerError
com.sun.tools.javac.code.TypeTag :: UNKNOWN
```

**해결 방법:**
1. File → Invalidate Caches → Invalidate and Restart
2. 또는 .idea/misc.xml에서 languageLevel을 JDK_21로 설정

### CORS 에러 (프론트에서 백엔드 API 호출 불가)
```
Access to XMLHttpRequest ... has been blocked by CORS policy
```

**해결 방법:**
- CorsConfig.java에서 허용 오리진 설정 확인
- 기본값: localhost:5173, localhost:8080 허용
- 다른 origin은 `CORS_ALLOWED_ORIGINS` 환경변수에 쉼표로 구분하여 설정

## 📖 참고 자료

- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [Spring WebFlux 가이드](https://spring.io/guides/gs/reactive-rest-service/)
- [React 공식 문서](https://react.dev)
- [Vite 문서](https://vitejs.dev)

## 📄 라이선스

ISC

---

**작성일:** 2026-08-15  
**개발자:** Copilot
