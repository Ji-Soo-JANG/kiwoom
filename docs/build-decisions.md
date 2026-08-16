# 로컬 빌드 결정

## Thymeleaf 제거

화면은 React SPA 하나로 제공하며 `WebController`가 `classpath:static/index.html`을 직접
반환한다. 서버 템플릿 렌더링 경로가 없으므로 Thymeleaf 의존성과 기존 템플릿을 제거한다.

## Vite 산출물 관리

이 저장소는 서버 배포가 아닌 로컬 실행·학습용이다. Node.js 없이 백엔드 JAR만 빌드하거나
실행해도 최신 UI를 포함하도록 `src/main/resources/static`의 Vite 산출물을 Git에서 관리한다.

소스와 산출물의 불일치를 막기 위해 CI는 `npm run build:check`로 프론트엔드를 다시 빌드한 뒤
정적 디렉터리에 Git 차이가 생기지 않는지 검사한다. 프론트 소스를 변경한 커밋에는
`npm run build` 결과를 반드시 함께 포함한다.
