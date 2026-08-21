# 자동매매 P0 안전장치

현재 프로젝트의 주문 기능은 로컬 연구·모의 체결용이다. 키움 실주문 API로 주문을 보내는 코드는 연결되어 있지 않다.

## 실행 모드

- `SIGNAL_ONLY`: 기본값. 신호와 연구 결과만 만들고 주문 생성은 거부한다.
- `PAPER`: 로컬 DB의 모의 현금과 잔고에만 즉시 체결한다.
- `LIVE`: 예약된 모드다. `LIVE_TRADING_ENABLED=true`와 `LIVE_TRADING_CONFIRMATION=I_UNDERSTAND_LIVE_TRADING`이 모두 있어야 환경 잠금이 풀리지만, 브로커 주문 어댑터가 없으므로 현재는 항상 `SIGNAL_ONLY`로 강등된다.

따라서 현재 버전에서는 환경변수를 잘못 설정해도 실제 주문이 전송되지 않는다.

## PAPER 주문

`POST /api/trading/orders`

```json
{
  "decisionId": "strategy-v1-005930-20260822-buy",
  "code": "005930",
  "side": "BUY",
  "quantity": 1,
  "price": 70000
}
```

`decisionId`는 매매 결정마다 고유해야 한다. 같은 ID와 같은 내용으로 다시 요청하면 기존 주문을 반환하며 새 주문이나 체결을 만들지 않는다. 같은 ID에 다른 내용을 보내면 거부한다.

상태는 `CREATED → SUBMITTED → ACKNOWLEDGED → PARTIALLY_FILLED/FILLED` 순서를 따르고, 중간에는 `CANCELED` 또는 `REJECTED`로 종료될 수 있다. PAPER 주문은 현재 전량 즉시 체결하지만 부분 체결 상태를 허용하는 전이 규칙을 먼저 마련했다.

`POST /api/trading/reconcile`은 주문의 체결 수량, 체결로 계산한 종목 잔고, 초기 현금에서 체결 금액을 반영한 예상 현금을 저장 상태와 비교한다. 실제 계좌 대사는 실주문 어댑터가 추가될 때 별도 구현해야 한다.

## 위험 한도와 킬 스위치

기본값:

- 종목당 최대 비중 10%
- 총 투자 비중 50%
- 일일 손실 2%
- 누적 최대 낙폭 10%
- 최대 동시 보유 5종목

한도는 `TRADING_MAX_POSITION_RATE`, `TRADING_MAX_GROSS_EXPOSURE_RATE`, `TRADING_MAX_DAILY_LOSS_RATE`, `TRADING_MAX_DRAWDOWN_RATE`, `TRADING_MAX_OPEN_POSITIONS`로 조정한다.

손실 한도를 넘으면 킬 스위치를 DB에 저장하고 진행 중 주문을 취소한다. 킬 스위치 중 신규 매수는 차단하지만 보유 위험을 줄이는 매도는 허용한다. 수동 재개는 `POST /api/trading/kill-switch/resume`에 확인 문구 `RESUME_PAPER_TRADING`과 재개 사유를 함께 보내야 한다. 재개 시 해당 시점의 PAPER 자산을 새 일일·낙폭 기준으로 설정한다.

현재 PAPER 자산 평가는 현금과 저장된 평균 체결가 기준 잔고를 사용하므로, 체결되지 않은 보유 종목의 실시간 평가손익은 반영하지 않는다. 분봉·실시간 시세 저장 단계에서 시가평가를 연결하기 전까지는 실제 계좌 위험 한도로 사용할 수 없다.

모든 `/api/trading/**` API는 관리자 로그인이 필요하다.
