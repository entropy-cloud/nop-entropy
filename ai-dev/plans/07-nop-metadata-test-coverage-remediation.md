# 07 nop-metadata Test Coverage Remediation

> Plan Status: active
> Execution Order: 3
> Last Reviewed: 2026-07-21
> Source: `ai-dev/audits/2026-07-21-2039-multi-audit-nop-metadata.md` (维度16-01, 16-02, 16-03, 16-04, 16-05), `ai-dev/audits/2026-07-21-2039-open-audit-nop-metadata.md` (AR-16)
> Related: `05-nop-metadata-security-and-error-hardening.md`, `06-nop-metadata-bizmodel-api-contract.md`

## Purpose

Close the test coverage gaps in nop-metadata identified by multi-dimensional audit: 40% BizModels with zero tests, aggregation tests using wrong test pattern, search tests relying on Mockito mocks, core business methods untested, and undersized processor tests.

**Execution dependency**: Execute only after Plan 06's `Plan Status: completed`. Plan 06 stabilizes the BizModel API surface (I*Biz interfaces, DTOs replacing Map signatures, xmeta for search). Tests written before Plan 06 would need rework after API changes.

## Current Baseline

- **19/40 BizModels have zero test coverage**. Highest-risk 5 (selected based on data criticality and custom action complexity):
  1. `NopMetaClassificationBizModel` — classification CRUD used by catalog tagging; has custom `save()`/`delete()` overrides
  2. `NopMetaTableFilterBizModel` — custom filter CRUD with user-filter merging logic
  3. `NopMetaGlossaryBizModel` — glossary management with cross-entity relation semantics
  4. `NopMetaQualityCheckpointBizModel` — checkpoint lifecycle for data quality (5 actions: approve/reject/reset/retry/comment)
  5. `NopMetaTableJoinBizModel` — join configuration between tables (core for foreign entity lookups)
- **3 aggregation test files** (`TestAggregationCategoricalAndTemporal`, `TestAggregationExternalJoinAndPagination`, `TestAggregationEntityJoinAndComplex`) **call BizModel methods directly** instead of via `IGraphQLEngine.executeRpc`, violating testing.md recommendation (dimension16-03). They already `@Inject IGraphQLEngine` but invoke `nopMetaTableBizModel.queryAggregation(...)` directly in test methods.
- **3 search test files** use Mockito mocks for `ISearchEngine` and `IEntityDao`: `TestNopMetadataSearchIntegration`, `TestNopMetaSearchService`, `TestNopMetaIndexBuilder`. Mockito is used in at least 5 other test files in the same module, so it cannot be removed from the classpath. Scope is limited to ensuring the search test files exercise the error path (AR-16), not replacing all mocks with real engines.
- **Core untested methods**: `NopMetaModuleBizModel.releaseModule()` (444), `generateManifest()` (line ~470), and `NopMetaDataContractBizModel.activateContract()` (line ~94) — key contract state machine methods with no test.
- **5 JOIN processor tests** are each < 50 lines (e.g., `TestEntityAggregationProcessor.java` at 28 lines, 3 trivial tests). Provide near-zero protection against refactoring regression.

## Goals

- 3 aggregation test files refactored to use `IGraphQLEngine.executeRpc`
- Search test files cover the silent-failure error path in `addToIndex`/`removeFromIndex` (AR-16 test coverage)
- 5 highest-risk zero-coverage BizModels have integration tests
- `releaseModule`, `generateManifest`, `activateContract` have focused tests
- JOIN processor tests expanded to meaningful coverage

## Non-Goals

- **Not** achieving 100% BizModel test coverage within this plan (remaining 14 deferred — see Deferred But Adjudicated)
- **Not** replacing all Mockito mocks with real implementations across the module
- **Not** changing the search index implementation (only test improvements)
- **Not** changing error handling robustness (covered by Plan 05)

## Scope

### In Scope

- Refactor 3 aggregation test files to use `IGraphQLEngine.executeRpc` instead of direct BizModel calls
- Add test(s) to search test files that verify `NopMetaSearchService.addToIndex()`/`removeFromIndex()` propagate exceptions (or log ERROR) when `ISearchEngine` throws; keep Mockito for other test scenarios but cover the error path
- Create integration tests (via `IGraphQLEngine.executeRpc`) for 5 highest-risk zero-coverage BizModels: `NopMetaClassificationBizModel`, `NopMetaTableFilterBizModel`, `NopMetaGlossaryBizModel`, `NopMetaQualityCheckpointBizModel`, `NopMetaTableJoinBizModel`
- Write tests for `NopMetaModuleBizModel.releaseModule()`, `generateManifest()`, `activateContract()`
- Expand at least 3 of the 5 JOIN processor tests (`TestEntityAggregationProcessor` + 2 others) to ≥100 lines each covering multiple scenarios

### Out Of Scope

- Full 40/40 BizModel test coverage (deferred)
- Removing Mockito from the module's test classpath (other 5+ test files still need it)
- Performance or stress tests
- Lineage BFS graph traversal tests (complex integration, separate plan)

## Execution Plan

### Phase 1 — Test pattern migration + search error-path coverage

Status: planned
Targets: `TestAggregationCategoricalAndTemporal.java`, `TestAggregationExternalJoinAndPagination.java`, `TestAggregationEntityJoinAndComplex.java`, `TestNopMetadataSearchIntegration.java`, `TestNopMetaSearchService.java`, `TestNopMetaIndexBuilder.java`

- Item Types: `Fix | Fix | Fix`

- [ ] Refactor `TestAggregationCategoricalAndTemporal` to use `IGraphQLEngine.executeRpc` for BizModel invocations (construct GraphQL requests matching `@BizMutation`/`@BizQuery` method names)
- [ ] Refactor `TestAggregationExternalJoinAndPagination` similarly
- [ ] Refactor `TestAggregationEntityJoinAndComplex` similarly
- [ ] Add test(s) in `TestNopMetadataSearchIntegration` (or a dedicated test) that verify `addToIndex()` and `removeFromIndex()` log at least at WARN level (not silently swallow) when `ISearchEngine` throws; keep existing Mockito-based tests but augment with error-path coverage
- [ ] Ensure existing search tests continue to pass with Mockito unchanged

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 3 aggregation tests call BizModel methods via `IGraphQLEngine.executeRpc` (verify via code review; no direct `nopMetaTableBizModel.xxx()` calls in test methods)
- [ ] Search test(s) exercise the failure path: mock `searchEngine.addDoc()` to throw, verify `LOG.warn()` logging or exception propagation (AR-16 test coverage)
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -am` passes (all existing + new tests)
- [ ] **No owner-doc update required** (test pattern changes, no public API change)
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — Core untested BizModel integration tests

Status: planned
Targets: 5 BizModel classes in `service/entity/` + corresponding new test classes in `src/test/java/`

- Item Types: `Fix | Fix`

Selection rationale: These 5 BizModels have the highest data criticality and custom action complexity (override save/delete, stateful workflows, cross-entity relations). The remaining 14 are simple CRUD-only and lower risk.

- [ ] Create integration test for `NopMetaClassificationBizModel` (CRUD + classification/tag semantics)
- [ ] Create integration test for `NopMetaTableFilterBizModel` (filter CRUD with user-filter merging)
- [ ] Create integration test for `NopMetaGlossaryBizModel` (glossary CRUD)
- [ ] Create integration test for `NopMetaQualityCheckpointBizModel` (checkpoint lifecycle: approve, reject, reset, retry, comment)
- [ ] Create integration test for `NopMetaTableJoinBizModel` (join configuration CRUD)
- [ ] Each integration test must use `IGraphQLEngine.executeRpc` and cover at minimum: save, get, update, delete (or equivalent)

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 5 new integration test classes created and passing
- [ ] Each covers at minimum: create/read/update/delete via GraphQL RPC
- [ ] `NopMetaQualityCheckpointBizModel` test covers the approve/reject/reset/retry/comment lifecycle actions
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -am` passes
- [ ] **No owner-doc update required**
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — Core business method tests + JOIN processor test expansion

Status: planned
Targets: `NopMetaModuleBizModel.java`, `NopMetaDataContractBizModel.java`, `TestEntityAggregationProcessor.java`, `TestSqlAggregationProcessor.java`, `TestExternalAggregationProcessor.java` (pick 2 smallest beyond EntityAggregation)

- Item Types: `Fix | Fix`

- [ ] Write integration tests for `NopMetaModuleBizModel.releaseModule()` — test a complete release lifecycle (create snapshots, verify state transitions)
- [ ] Write integration tests for `NopMetaModuleBizModel.generateManifest()` — verify manifest output structure
- [ ] Write integration tests for `NopMetaDataContractBizModel.activateContract()` — test contract activation flow
- [ ] Expand `TestEntityAggregationProcessor` to ≥100 lines covering: empty input, single join entry, multiple join entries, error paths
- [ ] Expand 2 additional JOIN processor tests (e.g., `TestSqlAggregationProcessor`, `TestExternalAggregationProcessor` if they exist and are undersized) to ≥100 lines each

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `releaseModule`, `generateManifest`, `activateContract` have focused integration tests (at least 6 test methods: success + failure path for each of the 3 methods)
- [ ] At least 3 processor tests expanded to ≥100 lines each, covering multiple scenarios
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -am` passes
- [ ] **No owner-doc update required**
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 3 aggregation test files use `IGraphQLEngine.executeRpc` (not direct BizModel calls)
- [ ] 5 new BizModel integration test classes created and passing
- [ ] `releaseModule`, `generateManifest`, `activateContract` have focused integration tests
- [ ] At least 3 processor tests expanded to ≥100 lines
- [ ] Search error path (AR-16) tested: addToIndex/removeFromIndex propagate or log exceptions
- [ ] No in-scope live defect or contract drift deferred to follow-up
- [ ] **No owner-doc update required**
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Test-Mandated Feature Rule**: all new tests follow the `IGraphQLEngine.executeRpc` pattern per testing.md
- [ ] `./mvnw compile -pl nop-metadata/nop-metadata-service -am`
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -am`

## Deferred But Adjudicated

### Remaining 14 untested BizModels

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: The 5 highest-risk BizModels (selected by data criticality + custom action complexity) are covered in Phase 2. The remaining 14 are simple CRUD-only BizModels whose risk is adequately mitigated by existing DAO layer tests and inherited CrudBizModel test coverage from the base framework.
- Successor Required: `yes`
- Successor Path: Future plan or tracked as backlog optimization

## Non-Blocking Follow-ups

- Evaluate adding a test coverage threshold in CI to prevent regressions (optimization candidate)
- 14 remaining zero-coverage BizModels tracked as backlog item

## Closure

Status Note: *to be completed after execution*
Completed: *to be completed after execution*

Closure Audit Evidence:

- Reviewer / Agent: *to be completed by independent subagent*
- Evidence: *to be completed*

Follow-up:

- 14 remaining zero-coverage BizModels tracked as optimization candidate
