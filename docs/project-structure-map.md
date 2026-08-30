# 프로젝트 구성 지도

기준일: 2026-08-30
상태: Architecture Baseline v1

## 1. 규모와 기술 구성

- 백엔드: Java 21, Spring Boot WebFlux, Spring Security, R2DBC, Flyway
- 프론트엔드: React, React Router, TanStack Query, Recharts, Vite
- 데이터베이스: PostgreSQL, 테스트용 H2/R2DBC
- 외부 연동: 키움 REST API
- 소스 규모: Java 운영 코드 182개, Java 테스트 클래스 42개, 프론트 소스 39개
- DB 변경 이력: Flyway V1~V24

## 2. 실행 구조

```text
사용자
  └─ React UI
       └─ Spring REST Controller
            ├─ 조회·검색·계좌
            ├─ 데이터 수집·품질
            ├─ 전략·백테스트·관찰
            ├─ PAPER 주문·위험·성과
            └─ 알림·관심종목·레거시 포트폴리오
                 ├─ broker/kiwoom/KiwoomHttpClient → 키움 REST API
                 └─ Repository → PostgreSQL
```

## 3. 백엔드 패키지

Architecture Baseline v1은 feature package를 우선하고, 기존 기술 계층 package는 관련 TASK에서 점진적으로 이동하는 기준선이다.

| 패키지 | 파일 수 | 현재 책임 | 분류 |
|---|---:|---|---|
| `broker` | 4 | broker-neutral 계약과 Kiwoom 전용 어댑터·HTTP·매핑 | 신규 feature package |
| `strategy` | 12 | 전략 계약·구현·모델·저장소·카탈로그 | 신규 feature package |
| `research/backtest` | 7 | 백테스트 설정·실행·DTO·저장소 | 신규 feature package |
| `research/walkforward` | 6 | 워크포워드 분석·실행·DTO·저장소 | 신규 feature package |
| `research/boxevaluation` | 27 | 박스 평가 후보·모델·API·저장소·서비스 | 선행 feature package 사례 |
| `config` | 9 | 보안, CORS, API·거래 설정, 진단 | legacy technical package |
| `controller` | 14 | REST와 웹 진입점 | legacy technical package |
| `dto` | 56 | 아직 이동하지 않은 외부·API·도메인 전달 모델 | legacy technical package |
| `error` | 8 | 공통 오류와 키움 오류 | legacy technical package |
| `repository` | 12 | 아직 이동하지 않은 R2DBC SQL과 영속화 | legacy technical package |
| `service` | 26 | 아직 이동하지 않은 수집·거래·운영 서비스 | legacy technical package |

`client`와 `mapper`의 Kiwoom 외부 API 클래스는 `broker/kiwoom` 경계로 이동했다. `common/config`와 `common/error`는 장기 목표이며 이번 기준선에서는 물리적으로 만들지 않았다.

### 점진적 migration 정책

- 새 주요 기능은 최상위 기술 계층보다 feature package를 우선한다.
- 기존 `controller`, `service`, `dto`, `repository`를 일괄 해체하지 않는다.
- 기존 클래스는 관련 기능 TASK에서 수정할 때 feature package로 점진적으로 이동한다.
- `research/boxevaluation`은 이번 기준선 이전부터 적용된 package-by-feature 선행 사례다.
- Market Data, PAPER Trading, Order, Position, Risk, Auto Trading, Alert, Portfolio, Watchlist와 Frontend는 현재 위치와 동작을 유지한다.

## 4. 주요 기능 흐름

### 시장 데이터

키움 종목 목록·일봉 조회 → `FullMarketDataCollectionService` → `MarketDataRepository` → 품질 검사·전략 스캔.

### 전략 연구

DB 일봉 → `StrategyRegistry`/`StockStrategy` → `StrategyScanService` → 후보 스냅샷 → 백테스트·워크포워드·관찰.

### PAPER 매매

전략 후보 → 제한 후보 → 자동 또는 승인 처리 → `PaperOrderService`/`BrokerAdapter` → 주문·체결·포지션 → 위험·청산·성과.

### 실전 계좌 조회

키움 계좌 API → `KiwoomApiService` → `AccountPortfolioResponse` → `AccountPortfolio` 화면. 현재 실제 주문과 실전 주문 대사는 연결되지 않았다.

## 5. 데이터 영역

- 사용자 편의: 관심종목, 레거시 수동 포트폴리오, 알림
- 시장 원장: 종목 마스터, 일봉, 수집 상태, 데이터 품질
- 연구 원장: 전략 정의, 전략 스캔·후보, 백테스트, 워크포워드, 관찰
- PAPER 거래 원장: 주문 이벤트·체결·계좌·포지션, 제한 후보, 거래 사이클·성과
- 운영 제어: 자동매매 설정

현재 실전 브로커 주문·체결 원장, 준비 검사, 시장 상태, 전략 승격, 개인 자금정책 테이블은 목표 설계이며 구현되지 않았다.

## 6. 프론트 화면

- 종목 검색과 차트
- 종목 비교
- 종목 발견과 시장 순위
- 관심 종목
- 실제 키움 계좌 포트폴리오 조회
- 알림
- PAPER 중심 자동매매·제한매매
- 사용자 설정

실전 운전용 공통 상태 막대, 장 전 준비 검사, 브로커 주문·대사 화면과 전략 승격 화면은 추가 설계 대상이다.

## 7. 자동 실행

- 일봉·전체 시장 데이터 수집 스케줄
- 알림 평가 스케줄
- 전략 스캔·관찰과 PAPER 거래 스케줄
- 스윙 포지션 감시와 장 후 대사
- Windows 시작·종료·백업·복원 스크립트

스케줄은 평일과 로컬 휴장일 판정을 사용하지만, 실전 운전에는 공식 거래일·장 상태, 단계별 실행 원장과 재시작 복구 게이트가 더 필요하다.

## 8. 현재 경계

- 실제 키움 주문 어댑터 없음
- 현재 전략은 고정된 복수 기간 후보를 비교하며 동적 안정 구간이 아님
- PAPER 검증 기능은 많지만 실제 20거래일·모의투자 현장 자료는 외부 시간이 필요함
- 수동 포트폴리오 백엔드는 데이터 보호 목적으로 레거시 유지
- 자동매매 관련 UI와 모델에 PAPER 전용 용어가 남아 있음

이 지도는 부서 문서의 공통 출발점이며, 구체 판단은 각 부서 문서와 PM 종합 검토를 따른다.
