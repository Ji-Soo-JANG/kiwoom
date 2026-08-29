# 매매시스템팀 현행 조사 보고서

- 문서 상태: 부서 조사 완료 / 부서 간 검토 대기
- 조사 기준일: 2026-08-29
- 담당 부서: 매매시스템팀
- 연결 요구사항: TRD-001~005, ENT-001~002, EXT-001~002, OPS-001~003, NFR-002~003·006·008
- 조사 범위: 주문 API, 주문 상태와 체결, PAPER 계좌·포지션, 제한 매매, 스윙 청산, 자동매매 제어, 스케줄러, DB migration과 관련 테스트

## 1. 결론

현재 프로젝트는 **로컬 PAPER 주문 흐름을 검증하는 기반**을 갖췄지만 **실제 주문을 전송하는 시스템은 아니다**. `LIVE`를 설정해도 `TradingModeService`가 `SIGNAL_ONLY`로 강등하고, `AutoTradingControlService`도 실주문 가능 상태를 항상 `false`로 반환한다. 구현된 `BrokerAdapter` 계약은 주문 생성만 포함하며 키움 LIVE 주문·조회·정정·취소·체결·잔고 어댑터가 없다.

따라서 현재 코드는 TRD-002의 로컬 멱등성 및 TRD-003의 상태 모델을 연구하는 데 유효하지만, TRD-001·004·005와 OPS-003의 실전 완료 근거로 사용할 수 없다. 실전 전에는 브로커 원장을 기준으로 한 OMS와 재시작 복구를 별도 설계·구현해야 한다.

## 2. 현재 구성

| 영역 | 현재 구성 | 판정 |
|---|---|---|
| 운전 모드 | `SIGNAL_ONLY`, `PAPER`, `LIVE`; LIVE는 강제 강등 | 안전한 연구 기반, 실전 미지원 |
| 자동매매 제어 | DB 단일 행으로 PAPER/LIVE ON/OFF와 전략 저장 | 설정 기록은 유효, LIVE 실행과 연결되지 않음 |
| 브로커 추상화 | `BrokerAdapter.mode`, `externalSubmissionAvailable`, `place` | 계약 범위 부족 |
| PAPER 주문 | `decision_id` UNIQUE, 주문·이벤트·체결 저장, 즉시 전량 체결 | 멱등성 연구에 유효 |
| 주문 상태 | 생성·제출·접수·부분체결·체결·취소·거부 전이 검사 | 상태 모델 기반 존재 |
| 계좌·포지션 | 로컬 `paper_account`, `paper_position` | 실계좌 원장 아님 |
| 대사 | 로컬 주문·체결·현금·포지션 내부 일관성 비교 | 브로커 대사 아님 |
| 제한 매매 | 후보 만료, 승인/자동 PAPER 실행, 주문 금액·횟수 제한 | PAPER 단계 안전 실험 |
| 스윙 청산 | 손절·익절·최대보유일 평가 후 PAPER 매도와 결과 저장 | 전략 연구용, 실전 체결 위험 미반영 |
| 장중 감시 | 거래시간 중 1분 간격 현재가 조회, 15:35 PAPER 대사 | 기본 스케줄, 운영 보장 없음 |

## 3. 현재 처리 흐름

### 3.1 신호에서 PAPER 진입

1. 전략 스캔이 제한 매매 후보를 생성한다.
2. `limited_trade_candidate.signal_id`가 중복 생성을 막는다.
3. 자동매매가 켜졌거나 사용자가 승인하면 `LimitedTradingService`가 제한 조건을 검사한다.
4. `PaperOrderService`가 `decision_id` 기준 주문을 만들고 PAPER 계좌에서 즉시 체결한다.
5. 체결가가 있으면 진입 슬리피지 표본과 `paper_trade_cycle`을 기록한다.

### 3.2 보유와 청산

1. `SwingPositionMonitoringScheduler`가 장중 1분마다 HOLDING 사이클의 현재가를 조회한다.
2. `PaperTradeCycleService`가 손절가, 익절가, 최대 보유일을 순서대로 검사한다.
3. 조건이 충족되면 PAPER 매도를 만들고 사이클과 결과를 닫는다.
4. 15:35에 로컬 대사와 누적 PAPER 결과를 로그에 남긴다.

### 3.3 장애 및 재시작

주문·이벤트·체결·사이클이 DB에 남으므로 로컬 상태를 다시 읽을 기반은 있다. 그러나 재시작 시 키움의 미체결·체결·잔고를 먼저 조회하여 로컬 상태를 수렴시키는 부팅 절차는 없다. 이는 OPS-003과 TRD-004의 실전 차단사항이다.

## 4. 강점

- `trading_order.decision_id`와 `trading_fill.execution_id`의 UNIQUE 제약으로 로컬 중복 생성·체결 방지 의도가 DB까지 반영됐다.
- 주문 이벤트를 별도 보존하여 상태 전이 감사 기반이 있다.
- `OrderStateMachineTest`가 정상·부분체결 전이와 종결 상태 이후 전이 거부를 검증한다.
- `TradingModeServiceTest`가 LIVE 설정 시에도 실제 주문이 불가능함을 명시적으로 고정한다.
- 제한 후보 승인 실패 시 PENDING으로 되돌리는 흐름과 성과 악화 시 킬 스위치 연결이 있다.
- 스윙 포지션은 서버 재시작과 거래일 경계를 넘어 DB에 유지된다.
- 로컬 주문 수명주기 검증기가 외부 주문 없이 부분체결·미체결·정정·취소·복구 시나리오를 점검한다.

## 5. 문제와 기술 부채

### 5.1 실전 차단사항

| 차단사항 | 영향 | 연결 요구사항 |
|---|---|---|
| 키움 LIVE 브로커 어댑터 없음 | 실제 주문·정정·취소·조회 불가 | TRD-001, TRD-003 |
| 브로커 계약이 `place`만 제공 | 미체결·체결·잔고·포지션 수렴 불가 | TRD-003~005 |
| 브로커 주문번호와 로컬 주문의 영속 매핑 없음 | 타임아웃 후 주문 존재 여부 확인 불가 | TRD-002~004 |
| 부팅 전 브로커 대사 절차 없음 | 재시작 시 중복 주문 또는 누락 위험 | OPS-003 |
| 로컬 대사만 존재 | 실계좌를 기준 원장으로 삼지 못함 | TRD-004~005 |
| 현재가 폴링 기반 청산 | API 장애·지연·호출 제한 때 청산 누락 가능 | EXT-001~002, NFR-003 |
| 진입 계획에 무효화 가격·예상 손실이 필수 아님 | 주문 전 위험 대비 수익 검증 불가 | ENT-001~002 |
| 부분체결 청산·기존 청산 주문 합산 로직 미완성 | 초과 매도 또는 잔여 포지션 위험 | EXT-002 |

### 5.2 모델 불일치

- `AutoTradingControlService`의 LIVE 확인 문구와 `TradingModeService`의 환경 확인 문구가 서로 다르며, 하나는 DB 제어이고 하나는 환경 모드다. 목표 상태에서는 단일 운전 상태 머신으로 통합해야 한다.
- 현재 PAPER 주문 요청 DTO와 `BrokerAdapter`가 결합되어 있다. 목표 계약은 도메인 주문 명령과 브로커별 요청 DTO를 분리해야 한다.
- PAPER는 즉시 전량 체결하므로 실제 호가, 부분체결 시간, 미체결 만료, 가격 정정과 체결 순서 위험을 재현하지 못한다.
- `max_holding_days`가 현재 청산 조건에 포함되어 있으나 사용자 목표에서는 박스권 기간과 마찬가지로 고정 일수가 핵심 규칙이 아니다. 보유기간 청산을 유지할지 자금 회수 보조 규칙으로 둘지 전략·위험관리팀 합의가 필요하다.
- 제한 매매의 고정값(주문 10만원, 1종목, 하루 2회)은 P2 연구 보호장치이지 최종 실전 자금 정책이 아니다.

## 6. 유지·수정·격리 제안

### 유지할 부분

- 주문·이벤트·체결을 분리한 영속 모델
- 의사결정 ID와 체결 ID의 DB 유일성
- 명시적 주문 상태 머신과 종결 상태 보호
- 모드 기본값 `SIGNAL_ONLY` 및 LIVE fail-closed
- PAPER 계좌와 체결 시뮬레이터: 회귀·장애 테스트용
- 전략 버전, 신호, 주문, 매매 결과 간 추적 개념

### 수정할 부분

- `BrokerAdapter`를 주문 제출, 조회, 정정, 취소, 미체결, 체결, 잔고, 포지션, 연결 상태 계약으로 확장한다.
- 브로커 주문번호·원 요청 해시·마지막 확인 시각·불확실 상태를 주문 원장에 저장한다.
- `automation_run`, `reconciliation_issue`, 브로커 포지션 스냅샷을 도입해 장 전/재시작/장 후 수렴 과정을 기록한다.
- 신규 매수는 준비검사와 대사 완료 후에만 허용하고, 불확실 주문은 재주문 대신 조회한다.
- 진입 주문에 전략 버전, 신호 ID, 진입 상한가, 무효화 가격, 예상 손실, 청산 계획을 필수 연결한다.
- 청산 주문은 현재 브로커 보유 가능 수량과 기존 미체결 매도량을 원자적으로 확인한다.
- PAPER 체결기를 지연·부분체결·미체결·갭·비용을 재현 가능한 결정론적 시뮬레이터로 확장한다.

### 제품 경로에서 격리할 부분

- 수동 포트폴리오 데이터는 실계좌 화면과 주문 판단의 기준 원장으로 사용하지 않는다.
- `PaperOrderService`를 LIVE 구현의 상속 기반으로 사용하지 말고 테스트용 어댑터로 유지한다.
- P2 고정 제한값과 수동 승인 흐름은 승격 단계 실험으로 보존하되 최종 자동 실전 정책으로 간주하지 않는다.

## 7. 부서 간 의존성

| 관련 부서 | 필요한 입력/합의 |
|---|---|
| 전략 연구팀 | 진입 무효화, 청산 우선순위, 최대 진입가, 신호 만료 규칙 |
| 위험관리팀 | 손실 예산 수량, 동시 노출, 신규 매수 차단 및 위험 축소 매도 정책 |
| 데이터팀 | 실시간/장중 가격 신선도, 거래 가능 상태, 기업행사와 수정주가 |
| 운영·보안팀 | 인증 토큰 수명, 호출 제한, 장애 감지, 거래일·기동·종료 절차 |
| QA팀 | 타임아웃 후 실제 접수, 부분체결, 중복 콜백, 재시작과 네트워크 단절 장애 주입 |
| UI팀 | 실제 주문 가능 상태, 미확정 주문, 대사 불일치와 운영자 조치 표시 |

## 8. 매매시스템팀 제안

1. LIVE 구현 전에 `BrokerAdapter v2` 계약과 주문 상태/불확실성 상태를 설계 검토한다.
2. 키움 모의투자 어댑터를 먼저 연결해 PAPER와 외부 브로커의 차이를 계측한다.
3. 모든 주문 경로를 `신호 → 진입 계획 → 위험 승인 → OMS → 브로커` 한 경로로 통합한다.
4. 재시작 시 자동매매를 OFF로 시작하고 브로커 대사가 성공한 뒤에만 신규 매수를 연다.
5. 실전 승격은 실제 주문 API 계약 테스트, 장중 장애 훈련, 장 후 대사 연속 성공 기록을 조건으로 한다.

## 9. 미결정 쟁점

- 키움 REST 실제·모의투자 주문 API의 정확한 주문/정정/취소/조회 계약과 호출 제한
- 기본 주문 유형(지정가/시장가), 호가 허용 범위, 미체결 만료와 재가격 정책
- 주문 결과 타임아웃 시 조회 대기 시간과 수동 개입 기준
- 부분 진입·부분 청산 허용 여부와 잔여 수량 처리
- 갭 하락 시 청산 주문 방식과 가격 보호 우선순위
- 종가 이후 미체결 주문과 포지션을 유지할지 취소할지
- 실전 초기 최대 주문액, 동시 보유 수, 일일 주문 수

## 10. 근거 파일

- `src/main/java/com/example/kiwoom/service/TradingModeService.java`
- `src/main/java/com/example/kiwoom/service/AutoTradingControlService.java`
- `src/main/java/com/example/kiwoom/service/PaperOrderService.java`
- `src/main/java/com/example/kiwoom/service/OrderStateMachine.java`
- `src/main/java/com/example/kiwoom/service/LimitedTradingService.java`
- `src/main/java/com/example/kiwoom/service/PaperTradeCycleService.java`
- `src/main/java/com/example/kiwoom/service/SwingPositionMonitoringScheduler.java`
- `src/main/java/com/example/kiwoom/service/broker/BrokerAdapter.java`
- `src/main/java/com/example/kiwoom/service/broker/KiwoomSimulationBrokerAdapter.java`
- `src/main/resources/db/migration/V14__create_paper_order_state_machine.sql`
- `src/main/resources/db/migration/V17__create_limited_trading_workflow.sql`
- `src/main/resources/db/migration/V18__track_entry_execution_quality.sql`
- `src/main/resources/db/migration/V19__create_paper_trade_cycles.sql`
- `src/main/resources/db/migration/V20__create_auto_trading_control.sql`
- `src/test/java/com/example/kiwoom/service/OrderStateMachineTest.java`
- `src/test/java/com/example/kiwoom/service/TradingModeServiceTest.java`
- `src/test/java/com/example/kiwoom/service/PaperBrokerVerificationServiceTest.java`
- `docs/trading-safety.md`, `docs/p2-limited-trading.md`
