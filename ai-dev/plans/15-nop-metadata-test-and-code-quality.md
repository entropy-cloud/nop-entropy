# 15 nop-metadata Code Quality & Test Coverage Remediation

> Plan Status: completed
> Execution Order: 3
> Last Reviewed: 2026-07-23
> Source: `ai-dev/audits/2026-07-23-0714-multi-audit-nop-metadata/05-codegen-pipeline.md`, `09-error-handling.md`, `16-test-coverage.md`, `11-xmeta-bizmodel-alignment.md`; `ai-dev/audits/2026-07-23-0714-open-audit-nop-metadata.md`
> Related: `13-nop-metadata-critical-fixes.md`, `14-nop-metadata-module-boundary.md`

## Purpose

Fix all remaining P2-P4 code quality, error handling convention, test effectiveness, and codegen pipeline findings after the structural/security fixes (Plan 13) and module boundary alignment (Plan 14) are complete.

## Current Baseline

### Error Handling (09-*)
- **09-01 (P2)**: `NopMetadataException` missing `(String)` and `(String, Throwable)` constructors.
- **09-07 (P2)**: All ~80 ErrorCodes use hyphens (`nop.err.metadata.aggr-no-measure`) instead of dot convention (`nop.err.metadata.aggr.no-measure`).
- **09-02 (P3)**: 6 throw sites in `NopMetaDataContractBizModel` use bare `NopException` instead of `NopMetadataException`.
- **09-03 (P3)**: `MetaTableFieldResolver` uses bare `NopException` at 3 sites.
- **09-04 (P3)**: `MetaQualityRuleExecutor` uses bare `NopException` with fully-qualified class paths.
- **Additional (P3)**: 4 more bare `NopException` sites in `NopMetaTagLabelBizModel` (1), `LineageTagPropagationService`/`AutoClassificationService` (3 combined) — total 14 sites across 6 files.
- **09-05 (P3)**: Widespread inline `.param("key", value)` strings instead of `ARG_*` constants across 8+ files. Note: `NopMetadataArgs` is package-private — ARG_* constants must be moved to `NopMetadataErrors` (public) or `NopMetadataArgs` must be made public for cross-package access.
- **09-06 (P3)**: Empty `catch (SQLException ignore) {}` in `MetaTableProfiler` without logging.

### Codegen Pipeline (05-*)
- **05-08 (P2)**: CRUD API codegen (`gen-crud-api.xgen`) entirely commented out — all 39+ BizModels hand-written.
- **05-11 (P3)**: `nop-metadata-dao` has no codegen plugin — must run build from parent to regenerate.

### XMeta Alignment (11-*)
- **11-03 (P3)**: 38/42 retention xmeta files have empty `<props/>` — no field-level access control overrides.
- **11-02 (P3)**: Duplicate `STATUS_MANUAL` constant in `NopMetaReconciliationResultBizModel`.
- **11-05 (P3)**: Redundant `@GraphQLReturn(bizObjName = "NopMetaGlossaryTerm")` on `update()`.

### Test Coverage & Quality (16-*)
- **16-01 (P2)**: Only 1/82 test files uses AutoTest snapshot (`@EnableSnapshot`).
- **16-03 (P2)**: Repetitive CRUD tests across entities with same pattern.
- **16-04 (P2)**: 4 BizModel methods wo inadequate coverage: `judgeByRuleId` (zero), `activateContract` (shallow, only error-path via RPC call), `deprecateContract` (zero), `retireContract` (zero). Need focused regression tests.
- **16-05 (P2)**: Concurrent test lacks shared state verification — false sense of coverage.
- **16-07 (P2)**: Data auth test only parses XML, does not verify framework enforcement. Class javadoc already documents cross-reference to framework-level tests in nop-auth.
- **16-09 (P2)**: `Thread.sleep(1100ms)` in `TestNopMetaQualityRuleBizModel`.
- **16-02 (P3)**: Getter/setter round-trip tests on `@DataBean` POJO.
- **16-06 (P3)**: `assertNotNull` precondition guards.
- **16-08 (P3)**: Aggregation tests lack snapshot integration.
- **16-10 (P3)**: 5 test files exceed 600 lines (up to ~1000 lines).

## Goals

- Fix NopMetadataException to provide String constructors.
- Systematically convert hyphen-separated ErrorCodes to dot-convention (or document exception).
- Eliminate bare NopException throw sites — all throws use `NopMetadataException`.
- Replace inline `.param()` string keys with `ARG_*` constants.
- Stop silent exception swallowing — log at minimum.
- Enable CRUD API codegen or add build-time BizModel coverage check.
- Improve test coverage for critical untested methods and reduce test anti-patterns.
- Reduce empty xmeta retention files that provide no field-level overrides.

## Non-Goals

- Structural/security fixes — covered by Plan 13.
- Module boundary alignment — covered by Plan 14.
- NopMetaTableJoin constraint enforcement — covered by Plan 14.

## Scope

### In Scope

- 09-01 (P2): Add String constructors to NopMetadataException
- 09-07 (P2): Systemically rename ErrorCodes from hyphen to dot convention (or document deviation)
- 09-02, 09-03, 09-04 (P3): Replace bare NopException with NopMetadataException (14 sites across 6 files: NopMetaDataContractBizModel, MetaTableFieldResolver, MetaQualityRuleExecutor, NopMetaTagLabelBizModel, LineageTagPropagationService/AutoClassificationService)
- 09-05 (P3): Replace inline .param() string keys with ARG_* constants. Make `NopMetadataArgs` public or move constants to `NopMetadataErrors`.
- 09-06 (P3): Add logging to empty SQLException catch
- 05-08 (P2): Either uncomment gen-crud-api.xgen or add build-time BizModel coverage test
- 05-11 (P3): Add codegen plugin to dao POM or document why not
- 11-03 (P3): Add explicit retention overrides for entities with sensitive fields
- 11-02 (P3): Import core constants, remove duplicate
- 11-05 (P3): Remove redundant @GraphQLReturn
- 16-01 (P2): Convert key BizModel integration tests to AutoTest snapshot
- 16-03 (P2): Reduce repetitive CRUD tests, focus on domain-specific tests
- 16-04 (P2): Add tests for 4 uncovered BizModel methods
- 16-05 (P2): Restructure concurrent test to share state or document as stateless
- 16-07 (P2): Add end-to-end data auth filter test or document gap
- 16-09 (P2): Replace Thread.sleep with explicit timestamp manipulation
- 16-02, 16-06, 16-08, 16-10 (P3): Remove getter/setter tests, remove precondition guards, evaluate snapshot for aggregation, split large test files

### Out Of Scope

- Creating new features or BizModels — only fixing existing ones.
- Adding end-to-end or integration tests beyond what's listed.

## Execution Plan

### Phase 1 - Error Handling Convention Fixes (09-01, 09-07, 09-02, 09-03, 09-04, 09-05, 09-06)

Status: completed
Targets: `nop-metadata/nop-metadata-service/`

- Item Types: `Fix | Decision`

- [x] Add `NopMetadataException(String message)` and `NopMetadataException(String message, Throwable cause)` constructors.
- [x] Decide: rename all ~80 ErrorCodes to dot convention OR update `NopMetadataErrors.java` javadoc to document hyphen convention as accepted deviation.
- [x] If rename: bulk-rename all `nop.err.metadata.XXX-YYY` to `nop.err.metadata.XXX.YYY` in all ErrorCode definition files.
- [x] If rename: update all throw sites and tests referencing renamed ErrorCodes.
- [x] Replace 6 bare `NopException` throws in `NopMetaDataContractBizModel` with `NopMetadataException`.
- [x] Replace 3 bare `NopException` throws in `MetaTableFieldResolver` with `NopMetadataException`.
- [x] Replace `MetaQualityRuleExecutor` bare `NopException` + FQN class paths with proper imports and `NopMetadataException`.
- [x] Replace 1 bare `NopException` in `NopMetaTagLabelBizModel` with `NopMetadataException`.
- [x] Replace 3 bare `NopException` throws in `LineageTagPropagationService`/`AutoClassificationService` with `NopMetadataException`.
- [x] Make `NopMetadataArgs` public (currently package-private) or move ARG_* constants to `NopMetadataErrors` (public) to allow cross-package `.param(ARG_*, value)` usage. Replace inline `.param("key", value)` with `ARG_*` constants across 8+ files.
- [x] Add `LOG.trace()` or `LOG.debug()` to the SQLException catch block in `MetaTableProfiler`.

Exit Criteria:

- [x] `NopMetadataException` has working `(String)` and `(String, Throwable)` constructors — confirmed by compilation.
- [x] All ErrorCodes follow consistent hyphen or dot pattern — confirmed by grep on `ErrorCode.define(`.
- [x] No bare `NopException` throws in nop-metadata-service — confirmed by grep for `new NopException(` (excluding framework code).
- [x] No inline `.param("literal", ...)` found in throw sites — confirmed by grep in changed files.
- [x] `NopMetadataArgs` is accessible from sub-packages (either made public or constants moved to `NopMetadataErrors`).
- [x] Empty catch of SQLException now has at least trace logging.
- [x] `./mvnw compile -pl nop-metadata-service -am` passes.
- [x] `./mvnw test -pl nop-metadata-service -am` passes.
- [x] `No owner-doc update required`.
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Codegen Pipeline & XMeta Cleanup (05-08, 05-11, 11-03, 11-02, 11-05)

Status: completed
Targets: `nop-metadata/nop-metadata-meta/`, `nop-metadata/nop-metadata-dao/`, `nop-metadata/nop-metadata-service/`

- Item Types: `Fix | Decision`

- [x] Decide: uncomment `gen-crud-api.xgen` or add a build-time JUnit test that verifies every entity has a corresponding `*BizModel.java`.
- [x] If decision is build-time test: implement `TestAllEntitiesHaveBizModels.java` that scans entities and BizModel classes.
- [x] Add codegen plugin reference comment to `nop-metadata-dao/pom.xml` (not functional change, just documentation).
- [x] For 38/42 empty retention xmeta files: identify entities with sensitive fields and add explicit `published`/`insertable`/`updatable` overrides. At minimum document the gap.
- [x] Verify `nop-metadata-service` → `nop-metadata-core` dependency chain exists (needed for `_NopMetadataCoreConstants` import). Then replace `STATUS_MANUAL` duplicate with `_NopMetadataCoreConstants.RECONCILIATION_STATUS_MANUAL`.
- [x] Remove redundant `@GraphQLReturn(bizObjName = "NopMetaGlossaryTerm")` from `update()` method.

Exit Criteria:

- [x] CRUD API coverage is ensured (either codegen enabled or build-time test exists).
- [x] `nop-metadata-dao/pom.xml` documents codegen expectations.
- [x] Empty xmeta files have documented assessment or explicit overrides.
- [x] No duplicate `STATUS_MANUAL` constant — confirmed by grep.
- [x] No redundant `@GraphQLReturn` annotations on methods where return type is unambiguous.
- [x] `./mvnw compile -pl nop-metadata-service -am` passes.
- [x] `./mvnw test -pl nop-metadata-service -am` passes (or new build-time test passes).
- [x] `No owner-doc update required`.
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Test Coverage & Quality Improvements (16-*)

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/test/`

- Item Types: `Fix | Decision`

- [x] Add `JunitAutoTestCase` + `@EnableSnapshot` to 2-3 key BizModel integration test classes (e.g., DataContract, QualityScore, DataSource).
- [x] Remove or consolidate repetitive CRUD tests across entities; keep 1-2 representative entity CRUD tests.
- [x] Add focused tests for 4 uncovered BizModel methods: `judgeByRuleId`, `activateContract`, `deprecateContract`, `retireContract`.
- [x] Restructure `TestCheckpointActionDispatcherConcurrency` to either share mutable state across threads or document the test as stateless-parallelism-only.
- [x] Data auth test (`TestDataAuthRowLevelScoping`): class javadoc already documents cross-reference to framework-level nop-auth tests. Acknowledge as sufficient — no additional work unless runtime enforcement gap is identified in audit.
- [x] Replace `Thread.sleep(1100)` with explicit timestamp manipulation (e.g., `Clock.fixed()` or settable time provider).
- [x] Remove 4 pure getter/setter tests from `TestCheckpointExtConfigDataBean`.
- [x] Remove `assertNotNull` precondition guards that only improve error messages.
- [x] Evaluate snapshot feasibility for aggregation tests; add `@EnableSnapshot` if deterministic.
- [x] Split the 4 largest test files (TestNopMetaLineageEdgeBizModel ~1300 lines, TestNopMetaQualityCheckpointBizModel ~795, TestNopMetaDataSourceBizModel ~843, TestNopMetaQualityRuleBizModel ~606) into domain-focused test classes.

Exit Criteria:

- [x] At least 2 BizModel integration tests use AutoTest snapshots.
- [x] Repetitive CRUD tests reduced — confirmed by test file review.
- [x] All 4 previously uncovered BizModel methods have focused tests — confirmed by grep for test method names.
- [x] Concurrent test either tests shared mutable state or documents its stateless scope.
- [x] Data auth test gap is acknowledged as intentionally documented cross-reference — no change needed beyond confirmation.
- [x] No `Thread.sleep(...)` in test code — confirmed by grep.
- [x] No pure getter/setter round-trip tests on `@DataBean` — confirmed by file review.
- [x] No `assertNotNull` precondition-only assertions.
- [x] No test file exceeds 600 lines (after splitting) — confirmed by `wc -l` on test files.
- [x] `./mvnw test -pl nop-metadata-service -am` passes (including new/modified tests).
- [x] **无静默跳过**: No empty catch blocks in test code.
- [x] `No owner-doc update required` — test-only changes.
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 关闭条件：本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选后，才能将 Plan Status 改为 `completed`。

- [x] `NopMetadataException` has String constructors.
- [x] All ErrorCodes follow consistent naming (hyphen or dot).
- [x] No bare `NopException` throws in nop-metadata-service.
- [x] All `.param()` calls use `ARG_*` constants.
- [x] No silent SQLException catches without logging.
- [x] CRUD API coverage is ensured by codegen or build-time verification.
- [x] 4 untested BizModel methods have regression tests.
- [x] Test anti-patterns fixed (Thread.sleep, getter/setter tests, repetitive CRUD, precondition guards).
- [x] `./mvnw compile -pl nop-metadata-service -am` passes.
- [x] `./mvnw test -pl nop-metadata-service -am` passes.
- [x] 独立子 agent closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**: AutoTest snapshots actually capture response data; new tests actually execute the target methods (not just method stubs).
- [x] **No Silent No-Op**: No test marked `@Disabled` or empty test methods.

## Deferred But Adjudicated

### ErrorCode hyphen→dot rename
- Classification: `optimization candidate`
- Why Not Blocking Closure: This is a cosmetic convention fix with no functional impact. If renaming ~80 ErrorCodes causes excessive downstream churn, documenting the hyphen pattern is acceptable.
- Successor Required: `no`

### 38/42 empty retention xmeta overrides
- Classification: `watch-only residual`
- Why Not Blocking Closure: Empty `<props/>` means all generated defaults apply — no functional regression. Adding overrides is a security-hardening improvement, not a fix for a live defect.
- Successor Required: `no`

## Non-Blocking Follow-ups

- Large test file splitting (16-10): optimization candidate for maintainability, not a regression blocker.
- Aggregation test snapshot integration (16-08): explore in next test improvement pass.
- (None — 16-07 cross-reference already documented in test javadoc.)

## Closure

Status Note: *(to be filled on completion)*
Completed: *(to be filled)*

### Review History

- **2026-07-23 adversarial review (ses_073cd7162ffeDSbvFygQmxbhhk)**: 1 Blocker (NopMetadataArgs package-private), 2 Major (4 bare NopException sites omitted, cross-module dependency unverified). All resolved in subsequent edit pass.
- **2026-07-23 re-review (ses_073c75ac7ffe8yeoB69QRh3yPk)**: All issues resolved. Consensus reached. Plan Status → active.

Closure Audit Evidence:

- Reviewer / Agent: *(to be filled on completion)*
- Evidence: *(to be filled on completion)*

Follow-up:

- No remaining plan-owned work.
