# 앱 내부 알림 설계

## 범위와 결정

이 프로젝트는 로컬 실행용이므로 1차 채널은 이메일이나 Slack이 아닌 앱 내부 알림으로
한다. 사용자가 화면을 열거나 새로 고침할 때 조건을 평가하며, 백그라운드 스케줄러와
외부 메시지 인프라는 도입하지 않는다.

목표가 이상/이하와 RSI(14) 이상/이하, MACD·Signal 상향/하향 교차 조건을 지원한다.
지표는 백엔드가 일봉 데이터의 오래된 순서부터 계산해 API 응답과 알림 평가에 함께 사용한다.

## 데이터 모델

### `alert_rule`

| 필드 | 설명 |
| --- | --- |
| `id` | 알림 규칙 식별자 |
| `username` | 데이터 격리에 사용하는 인증 사용자 |
| `code` | 6자리 종목 코드 |
| `condition_type` | `PRICE_ABOVE`, `PRICE_BELOW`, `RSI_ABOVE`, `RSI_BELOW`, `MACD_CROSS_UP`, `MACD_CROSS_DOWN` |
| `threshold` | 가격 또는 RSI 기준값. MACD 교차는 null |
| `enabled` | 규칙 활성화 여부 |
| `last_state` | 직전 평가의 충족 여부. 경계 교차 판정에 사용 |
| `created_at`, `updated_at` | 생성·변경 시각 |

사용자·종목·조건·기준값 조합은 중복 저장하지 않는다.

### `alert_event`

| 필드 | 설명 |
| --- | --- |
| `id`, `rule_id`, `username` | 이벤트와 소유자 |
| `code`, `condition_type` | 발생 당시 규칙 스냅샷 |
| `observed_value`, `threshold` | 관측값과 기준값 |
| `triggered_at` | 발생 시각 |
| `read_at` | null이면 읽지 않은 알림 |

규칙을 삭제해도 과거 알림 내역은 유지한다.

## 평가와 중복 방지

1. 활성 규칙을 사용자별로 조회한다.
2. 같은 종목의 현재가는 한 번만 조회하고 기존 TTL 캐시를 재사용한다.
3. 조건이 `false → true`로 바뀐 순간에만 이벤트를 만든다.
4. 조건이 계속 참이면 새 이벤트를 만들지 않는다.
5. 다시 거짓이 된 뒤 재진입하면 새 이벤트를 만든다.
6. 키움 조회 실패 종목은 이벤트를 만들지 않고 기존 오류 코드와 trace ID로 반환한다.
7. 규칙 상태 갱신과 이벤트 저장은 하나의 R2DBC 트랜잭션으로 처리한다.

## API

모든 API는 세션 인증이 필요하며 `username`은 요청 본문이 아니라 인증 주체에서 얻는다.

- `GET /api/alerts/rules`: 내 규칙 조회
- `POST /api/alerts/rules`: 목표가 규칙 생성
- `PATCH /api/alerts/rules/{id}`: 기준값·활성 여부 변경
- `DELETE /api/alerts/rules/{id}`: 내 규칙 삭제
- `POST /api/alerts/evaluate`: 활성 규칙 즉시 평가
- `GET /api/alerts/events?unreadOnly=true`: 앱 알림 조회
- `POST /api/alerts/events/{id}/read`: 읽음 처리

다른 사용자의 ID는 존재 여부를 노출하지 않도록 HTTP 404로 처리한다.

## 프론트엔드

- 헤더에 읽지 않은 알림 개수 배지를 표시한다.
- 알림 설정 화면에서 종목, 이상/이하, 목표가, 활성 여부를 관리한다.
- 평가 버튼 또는 화면 진입 시 `POST /api/alerts/evaluate`를 호출한다.
- 종목 없음·호출 제한·장 운영시간 오류는 기존 `ApiError` 안내를 재사용한다.

## 구현 순서와 완료 기준

1. Flyway 테이블과 사용자 격리 repository를 추가한다.
2. 목표가 규칙 CRUD와 교차 평가 서비스를 구현한다.
3. 규칙/이벤트 API 통합 테스트와 트랜잭션 테스트를 추가한다.
4. React 알림 설정·배지·이벤트 목록을 구현한다.
5. 백엔드에 RSI/MACD 계산을 추가한 뒤 지표 조건을 확장한다.

동일 조건이 계속 참인 동안 이벤트가 한 번만 생성되고, 사용자 간 규칙과 이벤트가
서로 보이지 않으며, 키움 장애 시 잘못된 이벤트가 저장되지 않으면 1차 구현이 완료된다.
