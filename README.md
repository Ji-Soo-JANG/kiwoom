# 📈 Kiwoom 주가 조회 시스템

Spring Boot + React로 구성된 실시간 주식 종목 정보 조회 플랫폼입니다.

## 🏗️ 프로젝트 구조

```
kiwoom/
├── src/
│   ├── main/
│   │   ├── java/com/example/kiwoom/
│   │   │   ├── KiwoomApplication.java          # Spring Boot 메인 애플리케이션
│   │   │   ├── config/                          # WebClient 및 CORS 설정
│   │   │   ├── controller/                      # REST API 및 웹 페이지 진입점
│   │   │   ├── dto/                             # 현재가·일봉 응답 모델
│   │   │   ├── error/                           # 공통 API 오류 응답 및 예외 처리
│   │   │   └── service/                         # 키움 API 연동 및 조회 로직
│   │   └── resources/
│   │       ├── static/                         # React 빌드 결과물
│   │       └── templates/index.html            # 기존 Thymeleaf HTML
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
- Node.js 18+ & npm
- Maven

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
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:8080
```

```bash
# IDE에서 프로젝트 재로드
File → Invalidate Caches → Invalidate and Restart

# 또는 Maven 빌드
cd kiwoom
./mvnw clean package -DskipTests
```

### 2️⃣ 프론트엔드 개발 (선택)

```bash
cd frontend
npm install
npm run dev
```

브라우저: `http://localhost:5173`

### 3️⃣ 프로덕션 빌드

```bash
# 이미 자동으로 수행됨
# frontend/src → ../src/main/resources/static/로 빌드됨

./mvnw clean package -DskipTests
```

### 4️⃣ 애플리케이션 실행

```bash
# IDE에서 KiwoomApplication 실행
# 또는 명령어로:
java -jar target/kiwoom-0.0.1-SNAPSHOT.jar
```

브라우저: `http://localhost:8080`

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

#### 3. 일봉 조회
```
GET /api/kiwoom/stock-price/005930/daily?baseDate=20260816
```

`baseDate`를 생략하면 서울 시간 기준 오늘 날짜를 사용합니다.

#### 4. 헬스 체크

Spring Boot Actuator의 `GET /actuator/health`를 사용합니다.

### 오류 응답

잘못된 입력은 HTTP 400, 키움 연동 오류는 HTTP 502로 반환됩니다.

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

### 운영 상태

- Health: `GET /actuator/health`
- Info: `GET /actuator/info`
- 외부 노출은 `health`, `info`로 제한하며 상세 health 정보는 기본적으로 숨깁니다.

## 🛠️ 기술 스택

### 백엔드
- **Spring Boot 3.2.5** - 웹 프레임워크
- **Spring WebFlux** - 반응형 웹 프레임워크
- **Reactor** - 비동기 처리 (Mono, Flux)
- **Jackson** - JSON 처리
- **Thymeleaf** - HTML 템플릿 엔진

### 프론트엔드
- **React 19** - UI 라이브러리
- **Vite 8** - 번들러 및 개발 서버
- **Vanilla CSS** - 스타일링

## ✨ 주요 기능

✅ **단일 종목 조회** - 특정 코드의 주가 정보 조회  
✅ **다중 종목 조회** - 쉼표 구분으로 여러 종목 일괄 조회  
✅ **실시간 API** - WebFlux 기반 비동기 처리  
✅ **모던 UI** - React 기반 반응형 인터페이스  
✅ **CORS 지원** - 크로스 오리진 요청 처리  
✅ **에러 핸들링** - 일관된 JSON 오류 응답 제공  

## 🧪 테스트

```bash
# 모든 테스트 실행
./mvnw test

# Windows
.\mvnw.cmd test
```

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
