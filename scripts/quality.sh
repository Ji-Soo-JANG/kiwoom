#!/usr/bin/env bash
set -euo pipefail

MODE="all"
BASE=""
while (($#)); do
  case "$1" in
    backend|--backend) MODE="backend"; shift ;;
    frontend|--frontend) MODE="frontend"; shift ;;
    all|--all) MODE="all"; shift ;;
    --base) BASE="$2"; shift 2 ;;
    *) echo "QUALITY_RESULT ENVIRONMENT_FAILURE unknown argument: $1" >&2; exit 2 ;;
  esac
done

run_backend() {
  local root="$1"
  (cd "$root" && ./mvnw --batch-mode clean verify)
}

if [[ "$MODE" == backend || "$MODE" == all ]]; then
  [[ -n "$BASE" ]] || BASE="$(git rev-parse HEAD^)"
  run_backend .
  checker_out="$(mktemp -d)"
  base_worktree=""
  trap 'if [[ -n "$base_worktree" ]]; then git worktree remove --force "$base_worktree" || true; fi; rm -rf "$checker_out"' EXIT
  javac -d "$checker_out" tools/coverage/*.java
  java -cp "$checker_out" ChangedCoverageCheckerTest
  if [[ -z "${BASE_REPORT:-}" ]]; then
    base_worktree="$(mktemp -d)"
    git worktree add --detach "$base_worktree" "$BASE"
    run_backend "$base_worktree"
    BASE_REPORT="$base_worktree/target/site/jacoco/jacoco.xml"
  fi
  java -cp "$checker_out" ChangedCoverageChecker --repo . --base "$BASE" \
    --report target/site/jacoco/jacoco.xml --base-report "$BASE_REPORT"
fi

if [[ "$MODE" == frontend || "$MODE" == all ]]; then
  (cd frontend && npm ci && npm audit --audit-level=high && npm run format:check && npm run lint && \
    npm run types:check && npm run types:usage && npm run test:coverage && npx playwright install --with-deps chromium && npm run test:e2e && \
    npm run build:check && npm run build:size)
fi

echo "QUALITY_RESULT PASS"
