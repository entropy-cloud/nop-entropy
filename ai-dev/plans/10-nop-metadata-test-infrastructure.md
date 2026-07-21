# 10 nop-metadata Test Infrastructure Remediation

> Plan Status: active
> Execution Order: 3
> Last Reviewed: 2026-07-22
> Source: `ai-dev/audits/2026-07-21-2039-multi-audit-nop-metadata.md` (TST-01 remnant, TST-02, TST-03, TST-04, TST-05)
> Related: `07-nop-metadata-test-coverage-remediation.md` (resolved TST-01 partial — 3 of 27 tests fixed), `08-nop-metadata-remaining-api-contract-code-quality.md`, `09-nop-metadata-orm-data-integrity.md`

## Purpose

Close all remaining test infrastructure findings in nop-metadata: tests calling concrete BizModel classes (bypassing BizProxy), zero AutoTest snapshot coverage, zero concurrency tests, MockHttpClient static state, and test boilerplate duplication. Execute after Plans 08/09 stabilize the production code surface.

**Execution dependency**: Execute only after Plans 08 and 09 reach `completed` status. Plan 08 changes API signatures (Map→DTO) that existing tests consume. Plan 09 changes ORM model which affects test DB schema. Tests written before those are complete would need rework.

## Current Baseline

- Plan 07 refactored 3 aggregation test files to use `IGraphQLEngine.executeRpc`. **~24 tests remain** across the module that call concrete BizModel classes directly (e.g., `nopMetaTableBizModel.queryAggregation(...)`) instead of going through `I*Biz` interfaces + BizProxy.
- **Zero AutoTest snapshot tests** among 82 test files — all use database-integration mode. No regression protection for new fields: adding an ORM column won't break any existing test even if the field is uninitialized.
- **Zero concurrency tests** — no stress or multi-thread scenarios for checkpoint/scheduler/aggregation code.
- `MockHttpClient` test utility uses static mutable state (`DEFAULT_RESPONSE`, `responseQueue`) shared across tests — risk of cross-test interference in parallel execution.
- Test boilerplate duplication: multiple test files have ~300 lines each for setup (`@BeforeEach` blocks, entity factory methods, GraphQL request builders).
- Plan 08 may change some API return types that existing tests consume — will need test updates.

## Goals

- No tests call BizModel concrete classes directly — all use `I*Biz` interfaces or `IGraphQLEngine.executeRpc`
- At least one AutoTest snapshot test covering a core BizModel CRUD flow
- At least one concurrency test covering the highest-risk concurrent code path
- MockHttpClient uses instance-level (not static) state, safe for parallel test execution
- Test setup boilerplate reduced through shared test utility classes

## Non-Goals

- **Not** achieving 100% BizModel test coverage (already addressed in Plan 07)
- **Not** replacing all Mockito mocks (already addressed in Plan 07)
- **Not** adding performance benchmarks or stress tests
- **Not** adding end-to-end tests requiring external DB setup

## Scope

### In Scope

- Migrate ~24 remaining tests from direct BizModel calls to `I*Biz` interface or `IGraphQLEngine.executeRpc`
- Create at least 1 AutoTest snapshot test for a core BizModel CRUD flow (e.g., `NopMetaTableBizModel` or `NopMetaClassificationBizModel`)
- Create at least 1 concurrency test for the highest-risk concurrent path (recommend: `CheckpointActionDispatcher` score update — multiple actions racing on same checkpoint)
- Refactor `MockHttpClient` to use instance-level state (remove static `DEFAULT_RESPONSE`/`responseQueue`)
- Extract shared test setup (entity factories, GraphQL request builders) into utility classes
- Update any tests broken by Plan 08 API signature changes

### Out Of Scope

- Full 40/40 BizModel test coverage (adjudicated non-blocking in Plan 07)
- Adding new integration tests for untested BizModels (already addressed in Plan 07)
- Performance benchmarks
- E2E tests with external MySQL/Oracle

## Execution Plan

### Phase 1 — Test injection pattern migration

Status: planned
Targets: ~24 test files in `nop-metadata-service/src/test/`

- Item Types: `Decision | Fix | Fix | Proof`

- [ ] Identify all tests calling concrete BizModel classes directly (grep for `.BizModel` in test method bodies, excluding `IGraphQLEngine.executeRpc` patterns)
- [ ] Batch 1: Migrate tests for BizModels whose APIs are NOT changed by Plan 08 (utility-only BizModels, read-only queries)
- [ ] Batch 2: Migrate tests for BizModels whose APIs ARE changed by Plan 08 (Map→DTO) — update to new return types while migrating injection
- [ ] Verify each migrated test uses either `I*Biz` interface via `@Inject` or `IGraphQLEngine.executeRpc`

Exit Criteria:

- [ ] No test calls `XXXBizModel.` directly (verify via grep: `\.\w+BizModel\.\w+\(` in test files — plan-authorized exceptions only)
- [ ] All migrated tests pass with `./mvnw test -pl nop-metadata/nop-metadata-service -am`
- [ ] **No owner-doc update required** (test pattern change, no public API change)
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — AutoTest snapshot and concurrency testing

Status: planned
Targets: New test files in `nop-metadata-service/src/test/`

- Item Types: `Fix | Proof`

- [ ] Add 1 AutoTest snapshot test for a core BizModel CRUD flow (use `@NopTestConfig` with snapshot mode — follow pattern from other nop modules that use AutoTest)
- [ ] Add 1 concurrency test for `CheckpointActionDispatcher` (multiple threads updating same checkpoint score concurrently) — verify no data corruption or deadlock

Exit Criteria:

- [ ] At least 1 AutoTest snapshot test exists and passes — snapshot file created in `_snapshots/` directory
- [ ] At least 1 concurrency test exists and passes under `mvn test` (verify thread-safe with 4+ concurrent threads)
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -am` passes
- [ ] **Owner-doc update**: `docs-for-ai/02-core-guides/testing.md` updated to reflect AutoTest snapshot testing capability for nop-metadata
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — Test infrastructure quality

Status: planned
Targets: `MockHttpClient.java`, shared test utility classes

- Item Types: `Fix | Follow-up`

- [ ] Refactor `MockHttpClient`: move `DEFAULT_RESPONSE` and `responseQueue` from static to instance fields; update all test usages to create instance per test
- [ ] Extract shared test setup patterns (entity factory methods, GraphQL request builders) into a `TestUtil` base class or helper class
- [ ] Verify no cross-test interference in parallel execution by running affected test class with `mvn test -DforkCount=2`

Exit Criteria:

- [ ] `MockHttpClient` has no mutable static state (verify static fields are `final` or absent)
- [ ] Shared test setup extracted to utility class, used by at least 2 test files (reducing boilerplate by 50+ lines per file)
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -am` passes with `-DforkCount=2`
- [ ] **No owner-doc update required** (test infrastructure changes only)
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] All in-scope tests use `I*Biz` or `IGraphQLEngine.executeRpc` (no direct BizModel calls)
- [ ] AutoTest snapshot exists and captures a core CRUD flow
- [ ] Concurrency test exists for CheckpointActionDispatcher
- [ ] MockHttpClient uses instance-level state
- [ ] Test boilerplate reduced through shared utilities
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -am` passes
- [ ] Independent subagent closure audit completed and evidence recorded

## Deferred But Adjudicated

### Remaining 14 zero-coverage BizModels (from Plan 07)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Already adjudicated in Plan 07 as non-blocking — these are simple CRUD-only BizModels with lower risk.
- Successor Required: `no`

## Non-Blocking Follow-ups

- Expand AutoTest snapshot coverage to additional BizModels in future plans
- Add concurrency tests for aggregation and scheduler paths

## Closure

Status Note: (to be filled at close)
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:
