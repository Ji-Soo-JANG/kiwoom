# Repository Test and Coverage Contract

The repository quality contract protects both new behavior and existing quality.

## Metrics

- Changed production Java LINE coverage is at least 80%.
- Changed BRANCH coverage is at least 70%, calculated from JaCoCo `cb / (mb + cb)` on changed executable source lines. This is JaCoCo bytecode branch coverage, not a claim of business-branch equivalence.
- Global LINE and BRANCH must not regress against the declared base revision.
- Instruction and Method are initially report-only.
- A zero denominator is `NOT_APPLICABLE`, never 100%.

The changed checker reads Git new-side lines and the JaCoCo XML report. Deleted old-side lines, tests, and documentation are not changed production coverage targets. Obvious blank/comment/import/package lines are non-executable. Ambiguous or unmapped production code fails closed as `UNMAPPED_PRODUCTION_CODE` or `AMBIGUOUS_MAPPING`; an untested new class must not silently pass.

## Quality entry points

On Windows:

```powershell
.\scripts\quality.ps1 -Backend -Base origin/main
.\scripts\quality.ps1 -Frontend
.\scripts\quality.ps1 -All -Base origin/main
```

On Ubuntu/GitHub Actions:

```bash
./scripts/quality.sh backend --base "$BASE_SHA"
./scripts/quality.sh frontend
```

The backend entry point runs formatting, the full Maven verification, the changed checker, and a base-worktree report when no trusted base report is supplied. The frontend entry point preserves the existing format, lint, generated-type, coverage, E2E, build, and bundle checks. Live/authenticated integration is separate from this quality gate.

## Base revisions

PRs use the merge-base of the checked-out commit and `origin/<base>`. Main pushes use `github.event.before`; an all-zero, missing, or unavailable base fails rather than silently passing. A future CI optimization may use an exact-SHA coverage artifact, but a base worktree verification is the correctness fallback.

## Coverage anti-patterns and exceptions

Coverage-only tests without meaningful assertions, threshold reductions, unjustified exclusions, broad exception swallowing, and production weakening are prohibited. DTOs, configuration, bootstrap, and generated-like code are not automatically excluded; an exclusion requires a documented absence of meaningful executable behavior. `COVERAGE_EXCEPTION` requires explicit approval and a follow-up task.

The former absolute BUNDLE gate is replaced because the legacy repository baseline is below it. This does not claim that legacy debt is resolved: new code is protected by changed coverage and the existing code by no-regression checks.
