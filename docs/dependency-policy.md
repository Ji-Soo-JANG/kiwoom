# 의존성 관리 정책

마지막 점검: 2026-08-16

## 자동 점검

- Dependabot이 Maven과 npm 의존성을 매주 월요일, GitHub Actions를 매월 확인한다.
- 프론트엔드 CI는 `npm audit --audit-level=high`로 high 이상 취약점이 있으면 실패한다.
- 로컬 정기 점검은 `npm outdated`, `npm audit`,
  `.\mvnw.cmd versions:display-dependency-updates -DprocessDependencyManagement=false`를 사용한다.

## 반영 기준

- critical/high 보안 수정은 최우선으로 검토하고 전체 테스트 후 즉시 반영한다.
- patch/minor 업데이트는 Dependabot PR 단위로 CI가 통과하면 반영한다.
- major 업데이트는 자동 병합하지 않는다. 별도 브랜치에서 마이그레이션 문서, API 호환성,
  백엔드 테스트, 프론트 린트·테스트·빌드를 모두 확인한다.
- Spring Boot와 Springdoc처럼 연동되는 프레임워크는 호환 버전 묶음으로 업그레이드한다.
- 잠금 파일(`package-lock.json`)을 커밋해 CI와 로컬 설치 결과를 재현한다.

## 2026-08-16 점검 결과

- `npm audit`: 알려진 취약점 0건.
- npm: ESLint 계열 patch/minor 후보는 현재 호환 범위에서 갱신하고, ESLint 10·jsdom 30 등
  major 후보는 별도 검증 대상으로 유지한다.
- Maven: Spring Boot 4.1, Springdoc 3, MockWebServer 5 등 major 후보가 확인되었다.
  현재 Java 21 기반 기능을 안정화한 뒤 프레임워크 major 업그레이드를 별도 작업으로 진행한다.
