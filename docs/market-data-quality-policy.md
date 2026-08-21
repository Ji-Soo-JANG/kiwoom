# 시장 데이터 품질·수정주가 정책

## 원칙

- 키움 일봉 `ka10081` 요청에는 수정주가 구분 `upd_stkpc_tp=1`을 사용한다.
- 수집한 OHLCV 원본은 사후에 임의 변경하지 않는다.
- 수정주가 요청 후에도 기업행사로 의심되는 가격 단절이 있으면 자동 보정하지 않고 전략 대상에서 제외한다.
- 품질 검사 정책은 `adjusted-price-request-quality-v1`로 버전 관리한다.

## 검사 규칙

| 규칙                              | 등급     | 처리                         |
| --------------------------------- | -------- | ---------------------------- |
| 0 이하 가격 또는 잘못된 OHLC 범위 | BLOCKING | 전략 제외                    |
| 음수 거래량                       | BLOCKING | 전략 제외                    |
| 전일 종가 대비 절대 35% 초과 단절 | BLOCKING | 기업행사 확인 전 전략 제외   |
| 거래량 0                          | WARNING  | 기록 후 분석 허용            |
| 휴장일 일봉                       | WARNING  | 캘린더 또는 원천 데이터 확인 |
| 예상 거래일 3일 이상 누락         | WARNING  | 거래정지·수집 누락 여부 확인 |

품질 검사는 원본을 삭제하거나 수정하지 않는다. 가장 최근 검사에서 `BLOCKING` 문제가 있는 종목만 후보 스캔에서 제외한다. 기업행사 확정 정보가 별도 API로 확보되면 다음 정책 버전에서 조정계수와 원천 근거를 별도 테이블에 저장한다.

## API

- `POST /api/kiwoom/admin/market-data/quality/inspect`: 전체 저장 일봉 검사
- `GET /api/kiwoom/admin/market-data/quality`: 최근 검사 결과와 최대 100개 문제 조회

참고: [키움 REST API 가이드](https://openapi.kiwoom.com/m/guide/apiguide)
