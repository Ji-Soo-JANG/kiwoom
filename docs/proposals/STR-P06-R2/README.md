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
| PM | [기존 성과 해석 정정 기록](pilot-result-correction.md) |

현재 상태는 **정의 분류 평가 완료 / 경계 검증 미성립**이다. 신규 20건의 사용자 라벨은 모두 확정됐지만 후보 선택과 최종 경계가 전부 비어 있어 정의의 정성 보정에는 사용할 수 있고 탐지기 경계 성능에는 사용할 수 없다. 다음 단계는 입력 계약을 고친 뒤 경계 검증을 다시 수행하는 것이다. 이번 단계는 박스권 하위 구간만 다루고 급락·회복·눌림, 수익률, 손실 장벽, 주문은 다루지 않는다.
