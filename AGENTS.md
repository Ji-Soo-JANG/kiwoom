# Development quality contract

For every production behavior change:

1. Identify the behavior and inspect the existing test location before editing.
2. Design the applicable normal, meaningful branch, boundary, invalid-input, failure, retry, rollback, persistence, state-transition, or regression scenarios.
3. Implement the production change and its tests in the same work item. Bug fixes require a regression test whenever practical.
4. Review integration coverage for persistence/state changes and contract tests for API/client changes.
5. Run targeted tests first, then the repository quality entry point before declaring the work complete.
6. Changed production Java must meet Changed LINE >= 80% and Changed BRANCH >= 70%, with branch meaning JaCoCo branch coverage for changed source lines.
7. Global LINE and BRANCH must not regress against the declared Git base. Instruction and Method are report-only unless a task says otherwise.

Do not lower thresholds, add an exclusion to avoid a failure, delete or weaken tests, add assertion-free or execution-only coverage tests, test only getters/setters, weaken validation, swallow broad exceptions, or couple tests unnecessarily to private implementation details. A `COVERAGE_EXCEPTION` requires explicit user/design approval, affected files, risk, missing verification, and a follow-up task.

Preserve unrelated working-tree changes. Do not modify, revert, stage, or commit them.
