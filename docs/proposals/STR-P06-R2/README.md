# STR-P06-R2 박스권 정의 이해·탐지 검증 안건

이 디렉터리는 `급락 → 박스권 → 회복 → 눌림` 전체 패턴에서 **박스권 하위 구간의 사용자 정의를 이해하고 재현하는 단계**의 승인 전 자료를 보관한다.

## 문서

| 부서 | 계획서 |
|---|---|
| 전략연구팀 | [박스권 정의 가설·탐지기 연구 계획](strategy-research-plan.md) |
| 데이터팀 | [표본·동결·분할 계획](data-plan.md) |
| 백엔드팀 | [연구 계약·구현 계획](backend-plan.md) |
| 프론트엔드팀 | [사용자 검토 GUI 계획](frontend-plan.md) |
| QA·성과검증팀 | [독립 검증 계획](qa-validation-plan.md) |
| 위험관리·매매시스템팀 | [범위·차단 검토](risk-trading-scope-plan.md) |
| 운영·보안팀 | [로컬 연구 운영·접근 통제 계획](operations-security-plan.md) |
| PM | [통합 승인 요청서](pm-approval-proposal.md) |
| 전략·데이터·PM | [사용자 박스권 정의 가설 v1](user-box-definition-v1.md) |
| 데이터·QA·PM | [정의 검증 배치 B 실행 기록](definition-validation-batch-b.md) |
| 전 부서·PM | [정의 검증 배치 B 결과 및 완료 판정](definition-validation-batch-b-result.md) |
| 전 부서·PM | [경계 검증 C0 보완 구현·배치 준비 보고](boundary-validation-c0-preparation.md) |
| PM | [기존 성과 해석 정정 기록](pilot-result-correction.md) |

현재 상태는 **정의 분류 평가 완료 / 경계 검증 C0 사용자 평가 대기**다. 후보·경계 누락을 차단하는 계약을 구현했고, 기존 50종목과 겹치지 않는 신규 12건 배치가 준비됐다. 이번 단계는 박스권 하위 구간만 다루고 급락·회복·눌림, 수익률, 손실 장벽, 주문은 다루지 않는다.
