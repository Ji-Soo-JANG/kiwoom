# 로컬 개발 실행 매뉴얼

이 프로젝트의 개발 환경은 PostgreSQL(Docker, 5432), Spring Boot(8080),
Vite(5173) 세 프로세스로 구성한다. 주소는 모두 `localhost`로 통일한다.

## 사전 준비

- Docker Desktop 실행
- Java 21
- Node.js 22와 npm
- 프로젝트 루트의 `.env`

처음 한 번만 `.env.example`을 복사한다.

```powershell
Copy-Item .env.example .env
```

`.env`에는 실제 `KIWOOM_APP_KEY`, `KIWOOM_SECRET_KEY`와 로컬 전용 DB·로그인
비밀번호를 입력한다. `.env`는 Git에서 제외되며 값을 문서나 로그에 붙여 넣지 않는다.

## 한 명령으로 시작

프로젝트 루트 PowerShell에서 실행한다.

```powershell
.\scripts\start-dev.ps1
```

PowerShell 실행 정책으로 차단될 때는 현재 프로세스에서만 허용한다.

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\start-dev.ps1
```

스크립트는 다음 작업을 수행한다.

1. Docker, Java, npm과 Java 21을 확인한다.
2. `.env`를 현재 프로세스에 불러오되 값을 출력하지 않는다.
3. `dev` 프로필을 활성화한다.
4. PostgreSQL health를 기다린다.
5. Spring Boot와 Vite를 각각 새 PowerShell에서 실행한다.

로그인은 `http://localhost:8080/login`, React 개발 화면은
`http://localhost:5173`에서 확인한다. 로그인 성공 후 React 화면으로 자동 이동한다.

Spring Boot와 Vite는 각 터미널에서 `Ctrl+C`로 종료한다. DB도 중지하려면 다음을 실행한다.

```powershell
.\scripts\stop-dev.ps1
```

## IntelliJ에서 백엔드 실행

키와 시크릿을 IntelliJ에만 보관하려면 백엔드는 IntelliJ에서 실행하고 PostgreSQL과
Vite는 별도로 실행한다.

1. `Run` → `Edit Configurations...`
2. `KiwoomApplication` 선택
3. `Environment variables` 편집
4. 다음 이름을 등록하고 실제 값은 로컬에서만 입력

```text
KIWOOM_APP_KEY
KIWOOM_SECRET_KEY
APP_USERNAME
APP_PASSWORD
POSTGRES_PASSWORD
DATABASE_USERNAME
DATABASE_R2DBC_URL
DATABASE_JDBC_URL
CORS_ALLOWED_ORIGINS
```

`Active profiles`에 `dev`를 입력한다. 별도 입력란이 없다면 환경변수에
`SPRING_PROFILES_ACTIVE=dev`를 추가한다. 서버 시작 로그에 다음 문구가 있어야 한다.

```text
The following 1 profile is active: "dev"
```

Docker Desktop에서 기존 `postgres` 컨테이너를 실행하거나 다음 명령을 사용한다.

```powershell
docker compose -f compose.yml up -d --wait postgres
```

Vite는 별도 PowerShell에서 실행한다.

```powershell
cd frontend
npm run dev
```

## 로그인과 세션

`http://localhost:8080/login`에서 `APP_USERNAME`, `APP_PASSWORD`로 로그인한다.
개발 프로필에서는 성공 후 `http://localhost:5173`으로 이동한다. 화면 헤더에서 현재
사용자를 확인하고 로그아웃할 수 있다.

로그인 전에는 React가 인증 상태만 확인하며 관심 종목·포트폴리오 같은 보호 API를 먼저
호출하지 않는다. 세션이 만료되어 401이 발생하면 현재 React 경로를 저장하고 로그인
화면으로 이동하며, 재로그인 후 원래 경로로 돌아온다.

## 문제 해결

### 계속 401이 발생하는 경우

- `localhost`와 `127.0.0.1`을 섞어 쓰지 않는다.
- IntelliJ 환경변수를 바꾼 뒤 서버를 완전히 재시작한다.
- 브라우저의 localhost 쿠키를 삭제하고 다시 로그인한다.

### DB 인증에 실패하는 경우

Docker 컨테이너 최초 생성 시 비밀번호와 Spring의 `POSTGRES_PASSWORD`가 같아야 한다.
`.env`만 나중에 변경해도 기존 컨테이너의 DB 비밀번호는 자동 변경되지 않는다.

```powershell
docker compose -f compose.yml ps
docker compose -f compose.yml logs postgres
```

### 포트가 이미 사용 중인 경우

기존 Spring Boot, Vite 또는 PostgreSQL 프로세스를 먼저 종료한다. 기본 포트는 각각
8080, 5173, 5432다.
