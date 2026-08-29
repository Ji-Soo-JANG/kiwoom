# 데이터팀 현행 분석 보고서

- 문서 ID: `DEPT-DAT-001`
- 기준일: 2026-08-29
- 상태: 현행 조사 완료 / 제안 검토 필요
- 담당 요구사항: `DAT-001`~`DAT-004`, `MKT-001`, `MKT-002`, `OPS-001`, `NFR-002`, `NFR-003`

## 1. 조사 목적

종목 마스터, 일봉, 장중 데이터와 품질·시점 재현 구조가 전략 연구 및 실전 운전에 적합한지 코드와 DB를 기준으로 평가한다. 목표 설계와 현재 구현을 구분하며 이 문서는 마이그레이션 실행 승인이 아니다.

## 2. 현재 구현(사실)

### 2.1 저장 구조

| 테이블 | 역할 | 도입 |
|---|---|---|
| `stock_master` | 현재 종목 코드·이름·시장·상품 유형 | `V8` |
| `daily_candle` | 종목·거래일별 OHLCV, `(code, trade_date)` 기본키 | `V8` |
| `market_data_sync_state` | 종목별 동기화 상태·최종 거래일·오류 | `V8` |
| `market_data_quality_run/issue` | 품질 정책 버전, 실행 요약, 종목별 문제 | `V10` |
| `stock_master_snapshot` | 날짜별 시점 기준 종목 유니버스 | `V11` |
| `intraday_price_event` | 코드·이벤트 시각별 장중 시세 이벤트 | `V15` |

`MarketDataRepository`는 종목 upsert, 일봉 저장·조회, 현재 및 시점 기준 유니버스 조회를 제공한다. 시점 기준 조회는 기준일 이하 최근 종목 스냅샷과 기준일 이하 일봉만 사용한다. 최신 품질 실행에서 `BLOCKING` 문제가 있는 종목은 후보 조회에서 제외한다.

`FullMarketDataCollectionService`와 수집 스케줄러는 전체 시장 종목 및 일봉 적재를 담당한다. 키움 일봉 요청은 정책상 수정주가 구분을 사용한다. `MarketDataQualityService`는 저장된 전 종목을 검사하고 정책 버전 `adjusted-price-request-quality-v1`으로 실행을 저장한다.

### 2.2 품질 규칙

- 잘못된 OHLC, 0 이하 가격, 음수 거래량: 차단
- 전일 종가 대비 절대 35% 초과 단절: 미확인 기업행사로 차단
- 거래량 0, 휴장일 일봉, 예상 거래일 3일 이상 누락: 경고
- 원본을 자동 수정하거나 삭제하지 않고 문제를 별도 기록

`MarketCalendarService`는 주말과 설정된 휴장일을 제외해 거래일을 계산한다. 휴장일은 설정 문자열에 의존한다.

### 2.3 데이터 흐름

```text
키움 종목·일봉 조회
→ stock_master / daily_candle upsert
→ 성공한 실제 종목 목록만 날짜 스냅샷 저장
→ sync_state 갱신
→ 품질 검사 실행·문제 저장
→ 최신 BLOCKING 종목 제외
→ 전략 스캔·백테스트에 DB 데이터 제공
```

장중 자료는 별도의 이벤트 저장소에 추가되고 재생할 수 있으나, 일봉과 장중 이벤트 사이의 공식 정합성·완결성 판정은 아직 없다.

## 3. 잘된 점

- 외부 API 즉시 조회와 전략 계산을 분리하고 DB 일봉을 기준 데이터로 사용한다(`DAT-001`).
- 종목·거래일 복합 기본키와 upsert로 동일 일봉의 중복을 방지한다.
- 동기화 상태가 종목 단위로 존재해 실패 종목을 추적할 기반이 있다.
- 품질 문제를 원본과 분리해 보존하며 차단과 경고를 구분한다(`DAT-003`).
- 과거 기준일 스캔에서 당시 스냅샷을 사용해 생존편향을 줄이는 구조가 있다(`DAT-002`).
- 캐시된 종목 목록을 실제 당일 스냅샷으로 저장하지 않는 정책이 문서화돼 있다.
- 장중 이벤트가 이벤트 시각과 수집 시각을 함께 보관하고 코드·시각 인덱스를 갖는다.
- 품질 분석기, 캘린더, 전체 수집 서비스와 저장소 통합 테스트가 존재한다.

## 4. 문제와 기술 부채

### 4.1 데이터 의미와 계보

- `daily_candle`에는 원천, 수집 실행 ID, 수정주가 요청 여부, 조정 정책 버전, 수집 시각이 행 단위로 없다. “수정주가 기준”을 사후 증명하기 어렵다.
- 키움 수정주가가 어떤 기업행사를 어떤 방식으로 반영했는지와 조정계수가 별도 저장되지 않는다. 가격 단절 35% 규칙은 정상 기업행사를 오탐할 수 있다.
- 전략 스냅샷과 품질 실행·데이터 수집 실행의 직접 외래키 연결이 없다. 어떤 데이터 버전으로 신호를 만들었는지 완전 재현하기 어렵다(`NFR-002`).
- 종목 마스터 스냅샷은 시스템 도입 이후만 완전하다. 그 이전 상장폐지 종목을 복원하지 못한다.

### 4.2 완결성과 신선도

- 후보 조회는 일봉 90개 이상을 요구하지만 현재 전략은 1,500일을 요구한다. 저장소 하한과 전략 요구 이력이 불일치한다.
- 최신 품질 실행 하나의 차단 문제를 모든 시점 조회에 사용한다. 과거 `asOf` 연구에 당시 품질 상태가 아니라 현재 검사 결과가 영향을 줄 수 있다.
- 품질 실행이 전 종목에 완전했는지, 특정 수집 실행과 결합됐는지, 검사 중 실패한 종목이 무엇인지 명확한 게이트가 부족하다.
- `DAT-004`의 최신 거래일 대비 신선도 차단이 전략 스캔과 주문 준비 검사 전체에 강제되는지 보장되지 않는다.
- 휴장일 설정이 수동 속성에 의존한다. 임시공휴일·거래소 특별 휴장과 반일장을 신뢰할 공식 캘린더 원천이 없다.

### 4.3 실전·연구 확장 부족

- 거래정지, 관리·투자경고, VI, 상장·폐지일, 업종, 시가총액, 거래대금 등 진입 필터 데이터가 기준 모델에 부족하다(`MKT-001`, `MKT-002`).
- 장중 이벤트의 중복·역순·누락·장 상태·시퀀스 품질 규칙과 보존 정책이 미완성이다.
- 일봉 데이터가 실제 거래일 연속인지 검사할 때 코드별 거래정지와 신규상장을 구별하지 못한다.
- 전체 시장 장기 이력 적재 비용, API 호출 제한, 실패 재개와 완료 SLA가 수치화되지 않았다.
- 데이터 삭제·정정·재수집 정책과 백업·복원 시험이 없다.

## 5. 분류 의견

### 유지·강화

- `stock_master`, `daily_candle`, `market_data_sync_state`
- 시점 기준 종목 스냅샷과 기준일 이하 조회
- 원본 보존형 품질 검사 및 차단/경고 분리
- 장중 이벤트 저장과 재생 기반

### 불필요 또는 격리 검토

- 자동매매 핵심과 무관한 수동 포트폴리오 데이터는 데이터팀이 삭제하지 않는다. 보존·내보내기·폐기 기한을 별도 데이터 거버넌스 결정으로 넘긴다.
- 빌드 산출물인 `src/main/resources/static`은 데이터 원천이 아니므로 데이터 계보·백업 대상에서 제외한다.

### 수정 필요

- 수집 실행 단위와 원천·정책·수집 시각을 연결하는 데이터 계보
- `asOf` 기준 품질 판정과 전략별 필요 이력 검사
- 공식 거래일·시장 상태 및 종목 위험 상태 데이터
- 일봉/장중 완결성, 신선도와 준비 검사 계약
- 기업행사 및 조정계수 원천 확보

## 6. 제안 안건

| 제안 ID | 내용 | 우선순위 | 사용자 승인 필요 |
|---|---|---:|---|
| `DAT-P01` | 수집 실행 ID, 원천, 조정 정책, 수집 범위를 기록하고 신호까지 계보 연결 | P0 | DB 변경 승인 필요 |
| `DAT-P02` | 전략 실행 기준일별 품질 스냅샷과 필요 이력 충족 게이트 도입 | P0 | 저장·조회 정책 승인 필요 |
| `DAT-P03` | 거래일 캘린더와 거래정지·관리·경고·VI/주문 가능 상태 원천 확정 | P0 | 외부 원천/비용 승인 필요 |
| `DAT-P04` | 과거 상장·폐지 포함 유니버스 및 기업행사 데이터 확보 | P1 | 공급자·범위 승인 필요 |
| `DAT-P05` | 장중 이벤트 중복·누락·역순 규칙, 보존 기간과 일봉 대사 정의 | P1 | 보존 비용 승인 필요 |
| `DAT-P06` | 전체 시장 수집 완료 SLA, 호출 제한, 실패 재개·백업 복구 절차 수립 | P0 | 운영 정책 승인 필요 |

## 7. 타 부서 의존성과 조율 요청

- **전략연구팀:** 동적 탐지에 필요한 최대 탐색 이력과 필수 특징을 확정해야 저장·조회 비용을 설계할 수 있다.
- **성과검증팀:** 과거 기준 품질과 유니버스 사용 규칙, 데이터 동결 ID를 검증 계약에 포함해야 한다.
- **매매시스템팀:** 주문 가능 상태, 실시간 가격 신선도, 브로커 대사 데이터의 기준 원장을 정의해야 한다.
- **위험관리팀:** 데이터 지연·누락 등급별 매수 차단과 보호 매도 허용 정책을 결정해야 한다.
- **운영팀:** DB 백업·복구, 디스크 용량, 수집 실패 알림과 Windows 기동 순서를 책임져야 한다.

## 8. 미결정 쟁점

- 키움 데이터만으로 기업행사·과거 상장폐지·시장 상태를 충분히 확보할 수 있는지
- 가격 원본과 조정 가격을 함께 저장할지, 조정계수만 별도 저장할지
- 과거 품질 문제를 재평가했을 때 기존 연구 결과를 무효화할 범위
- 전체 시장 장중 이벤트의 수집 범위와 보존 기간
- 데이터 신선도 허용치와 장 전 수집 마감 시간
- 경고 수준 품질 문제를 전략별로 허용할지 전역 정책으로 막을지

## 9. 근거 파일

- `src/main/java/com/example/kiwoom/repository/MarketDataRepository.java`
- `src/main/java/com/example/kiwoom/service/FullMarketDataCollectionService.java`
- `src/main/java/com/example/kiwoom/service/MarketDataCollectionService.java`
- `src/main/java/com/example/kiwoom/service/MarketDataCollectionScheduler.java`
- `src/main/java/com/example/kiwoom/service/MarketDataQualityAnalyzer.java`
- `src/main/java/com/example/kiwoom/service/MarketDataQualityService.java`
- `src/main/java/com/example/kiwoom/service/MarketCalendarService.java`
- `src/main/java/com/example/kiwoom/service/IntradayEventService.java`
- `src/main/resources/db/migration/V8__create_market_data_store.sql`, `V10__create_market_data_quality.sql`, `V11__create_point_in_time_stock_universe.sql`, `V15__create_intraday_event_store.sql`
- `src/test/java/com/example/kiwoom/service/FullMarketDataCollectionServiceTest.java`
- `src/test/java/com/example/kiwoom/service/MarketDataQualityAnalyzerTest.java`
- `src/test/java/com/example/kiwoom/service/MarketCalendarServiceTest.java`
- `src/test/java/com/example/kiwoom/repository/RepositoryIntegrationTest.java`
- `docs/market-data-quality-policy.md`, `docs/point-in-time-universe.md`, `docs/requirements-definition.md`
