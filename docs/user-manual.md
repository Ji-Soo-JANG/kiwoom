# Kiwoom 자동매매 사용자 매뉴얼

마지막 갱신: 2026-08-22

## 1. 현재 지원 범위

이 프로젝트는 한국 주식 현물에서 `급락 → 장기 횡보 → 간헐적 거래량 증가 → 초기 상승 → 짧은 눌림` 패턴을 탐색하는 로컬 프로그램이다.

| 실행 방식 | 상태 | 외부 주문 |
|---|---|---|
| 신호 전용 | 사용 가능 | 없음 |
| 로컬 PAPER 자동매매 | 사용 가능 | 로컬 DB에서만 가상 체결 |
| 키움 모의투자 | 어댑터·픽스처 준비 | 현재 차단 |
| 키움 실투자 | 설정 저장 가능 | 현재 차단 |

선물, 옵션, 공매도, 신용, 미수, 해외 주식과 가상자산은 지원하지 않는다.

## 2. 준비 사항

- Docker Desktop
- Java 21
- Node.js 22
- IntelliJ IDEA
- 키움 REST API 앱 키와 시크릿 키

PostgreSQL은 로컬에 직접 설치하지 않고 Docker 컨테이너를 사용한다.

## 3. IntelliJ 환경변수

**Run → Edit Configurations → KiwoomApplication → Environment variables**에 등록한다.

```text
SPRING_PROFILES_ACTIVE=dev
KIWOOM_APP_KEY=발급받은 앱 키
KIWOOM_SECRET_KEY=발급받은 시크릿 키
APP_USERNAME=admin
APP_PASSWORD=사용할 로컬 비밀번호
POSTGRES_PASSWORD=local-password
DATABASE_USERNAME=kiwoom
DATABASE_R2DBC_URL=r2dbc:postgresql://localhost:5432/kiwoom
DATABASE_JDBC_URL=jdbc:postgresql://localhost:5432/kiwoom
```

키와 시크릿은 Git, 문서, 화면 캡처와 로그에 남기지 않는다.

## 4. 실행 순서

### 데이터베이스

Docker Desktop을 실행한 후 프로젝트 루트에서 실행한다.

```powershell
docker compose -f compose.yml up -d --wait postgres
docker compose -f compose.yml ps
```

PostgreSQL이 `healthy`인지 확인한다.

### 백엔드

IntelliJ에서 `KiwoomApplication`을 실행한다. 다음 주소에서 상태를 확인한다.

```text
http://localhost:8080/actuator/health
```

정상이면 `{"status":"UP"}`가 표시된다.

### 프론트엔드 개발 서버

```powershell
cd frontend
npm install
npm run dev
```

접속 주소는 `http://localhost:5173`이다. 최근 정적 빌드만 확인할 때는 프론트 개발 서버 없이 `http://localhost:8080`을 사용할 수 있다.

## 5. 로그인

`http://localhost:8080/login`에서 `APP_USERNAME`, `APP_PASSWORD`로 로그인한다. 개발 프로필에서는 로그인 후 프론트 개발 서버로 이동한다. 브라우저 콘솔에서 별도 `fetch()`를 실행할 필요가 없다.

## 6. 화면별 기능

### 종목 검색·차트

- 코드, 종목명, 초성, 부분 이름 검색
- 시장·상품 유형 필터
- 캔들, 거래량, 이동평균, RSI, MACD 확인
- 조회 기간과 확대 범위 변경

### 종목 발견

- 급등·급락·거래량 상위 목록
- 전략 조건에 맞는 급등 후보 조회
- 종목 선택 후 차트 이동

### 관심 종목·포트폴리오·알림

- 관심 종목 그룹과 메모 관리
- 키움 계좌 평가잔고, 손익과 보유 비중 확인
- 목표가, 등락률, RSI와 MACD 알림 설정

### 자동매매

- PAPER 및 실투자 자동매매 ON/OFF
- 실행 전략 선택
- PAPER 보유·청산·완료 거래 확인
- 승률, 손익비, Profit Factor와 최대 낙폭 확인
- 20거래일 관찰 진행률, 신호 일치율과 가격 편차 확인

## 7. PAPER 자동매매

1. 상단 **자동매매** 메뉴로 이동한다.
2. **모의투자 자동매매**를 ON으로 설정한다.
3. `drop-base-breakout-pullback-v1` 전략을 선택한다.
4. **자동매매 설정 저장**을 누른다.
5. 종목 발견 화면에서 전략 스캔을 실행하거나 자동 스케줄을 활성화한다.

PAPER가 ON이면 다음 흐름이 승인 버튼 없이 자동 실행된다.

`후보 발견 → 위험 한도 검사 → PAPER 매수 → 장중 가격 감시 → 조건부 PAPER 매도 → 성과 집계`

기본 청산 기준:

- 손절: 진입가 대비 -5%
- 익절: 진입가 대비 +10%
- 최대 보유기간: 10일
- 여러 조건이 동시에 충족되면 손절 우선

PAPER가 OFF이면 후보만 기록하고 주문은 생성하지 않는다.

## 8. 안전 한도

- 주문당 최대 100,000원
- 제한 자동매매 최대 보유 종목 1개
- 하루 최대 주문 2회
- 종목당 계좌 비중 최대 10%
- 총 투자 비중 최대 50%
- 일일 손실 최대 2%
- 누적 낙폭 최대 10%
- 중복 주문과 보유량 초과 매도 차단

손실 또는 성능 저하 조건이 발생하면 킬 스위치가 신규 매수를 중단한다. 킬 스위치는 자동 해제되지 않으며 명시적으로 재개해야 한다.

## 9. 거래일 자동 스케줄

IntelliJ 환경변수에 등록한다.

```text
TRADING_SCHEDULER_ENABLED=true
TRADING_SCHEDULER_BATCH_SIZE=100
TRADING_SCHEDULER_CRON=0 40 15 * * MON-FRI
```

기본적으로 거래일 15:40에 다음 순서로 실행한다.

1. 시장 데이터 갱신
2. 전략 스캔
3. PAPER 자동 진입
4. 관찰 표본 저장

주말과 등록된 휴장일에는 실행하지 않으며, 이전 실행이 끝나지 않았으면 중복 실행하지 않는다.

```text
MARKET_HOLIDAYS=2026-01-01,2026-02-16,2026-02-17,2026-02-18
```

## 10. 실투자와 키움 모의투자

실투자 자동매매를 ON으로 저장해도 현재는 `liveExecutionAvailable=false`이며 실제 주문이 전송되지 않는다. 화면에 차단 사유가 표시된다.

키움 모의투자 현장 검증에는 운영 키가 아닌 별도 모의투자 자격증명이 필요하다.

```text
KIWOOM_PAPER_BASE_URL=https://mockapi.kiwoom.com
KIWOOM_PAPER_APP_KEY=모의투자 전용 앱 키
KIWOOM_PAPER_SECRET_KEY=모의투자 전용 시크릿 키
```

현재 모의투자 어댑터도 외부 주문을 차단한다. 주문·정정·취소·부분 체결·잔고·거부·timeout 픽스처가 준비되어 있으며, 실제 모의 서버 계약 확인 후 별도 승인을 거쳐 차단을 해제해야 한다.

## 11. 관찰과 성과 해석

자동매매 화면에서 다음 정보를 확인한다.

- 관찰 거래일 `현재/20일`
- 누락 및 예상 밖 신호
- 백테스트·장중 신호 일치율
- 평균 가격 편차와 슬리피지
- 완료 거래 수, 승률과 순수익률
- 손익비, Profit Factor, 연속 손실과 최대 낙폭

실제 데이터 20거래일을 채우기 전에는 관찰 완료로 판단하지 않는다. PAPER 및 백테스트 수익은 실거래 수익을 보장하지 않는다.

## 12. 종료와 데이터 보호

프론트 터미널은 `Ctrl+C`, 백엔드는 IntelliJ 중지 버튼으로 종료한다.

```powershell
docker compose -f compose.yml stop postgres
```

DB 데이터를 유지하려면 Docker 볼륨을 삭제하지 않는다. `docker compose -f compose.yml down -v`는 데이터를 삭제하므로 백업 없이 실행하지 않는다.

## 13. 문제 해결

### 401

세션이 만료된 상태다. `/login`에서 다시 로그인한다.

### 프론트 API 502

- 백엔드가 8080 포트에서 실행 중인지 확인한다.
- PostgreSQL이 `healthy`인지 확인한다.
- Vite 터미널의 프록시 오류를 확인한다.
- 키움 환경변수가 IntelliJ 실행 설정에 있는지 확인한다.

### 자동매매가 실행되지 않음

- PAPER가 ON인지 확인한다.
- 킬 스위치 상태를 확인한다.
- 보유 종목, 일일 주문 횟수와 10만원 제한을 확인한다.
- 자동 실행은 `TRADING_SCHEDULER_ENABLED=true`인지 확인한다.
- 전략 스캔에 qualified 후보가 있는지 확인한다.

### 키움 조회 실패

인증 만료, 호출 제한, 키움 원격 장애와 잘못된 API URL을 확인한다. 화면에 나온 `traceId`를 서버 로그에서 검색한다.

## 14. 테스트

```powershell
.\mvnw.cmd clean test

cd frontend
npm run format:check
npm run lint
npm test -- --run
npm run build
```

현재 기준 정상 결과는 백엔드 142개, 프론트엔드 55개 테스트 통과다.
