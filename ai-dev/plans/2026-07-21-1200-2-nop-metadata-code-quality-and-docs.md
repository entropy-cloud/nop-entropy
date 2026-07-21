# 2 nop-metadata Code Quality, Type Safety, and Documentation

> Plan Status: active
> Last Reviewed: 2026-07-21
> Source: `ai-dev/audits/2026-07-20-1816-multi-audit-nop-metadata.md` (dimensions 01, 02, 04, 07, 09, 11, 15, 16, 18), `ai-dev/audits/2026-07-20-1816-open-audit-nop-metadata.md`
> Related: `ai-dev/plans/300-nop-metadata-audit-fix.md`, `ai-dev/plans/309-nop-metadata-error-handling-fixes.md`, `ai-dev/plans/310-nop-metadata-stubs-model-deps-cleanup.md`, `ai-dev/plans/311-nop-metadata-dto-module-restructure.md`, `ai-dev/plans/2026-07-21-1200-1-nop-metadata-p1-runtime-defects.md`

## Purpose

Address all remaining nop-metadata audit findings not covered by prior plans (300/306/308/309/310/311) and Plan 1. Three workstreams: (A) type safety antipatterns — raw casts, Map returns, @SuppressWarnings density; (B) BizModel and file restructuring — oversized files, god tests, misplaced interfaces; (C) documentation and small-fix cleanup — doc-code inconsistencies, ORM model minor issues, dead module pieces.

## Current Baseline

### Workstream A — Type Safety

- **15-01** (P1): `INopMetaLineageEdgeBiz` returns `Map<String,Object>` despite existing `@DataBean` DTOs (`LineagePathDTO`, `ImpactAnalysisDTO`) in same module
- **15-02** (P1): Double raw cast `(IOrmEntityDao)(IOrmEntityDao)` duplicated in `NopMetaTableBizModel.java:614` and `MetaJoinExecutor.java:421`
- **15-03** (P1): `(List<Map<String,Object>>[]) new List<?>[1]` pattern triplicated in `AggregationContext.java`, `MetaTableQueryExecutor.java`, `MetaJoinExecutor.java`
- **15-04** (P2): `Map<String,Object>` propagation from executors forces unchecked casts at 10+ call sites
- **15-06** (P2): `testConnect()` returns `Map<String,Object>` forcing 4 instanceof checks at caller
- **15-07** (P2): 37 `@SuppressWarnings("unchecked")` annotations in production code
- **15-08** (P2): `NopMetaQualityCheckpointBizModel.summary` uses `Map<String,Object>` as unsafe accumulator
- **15-09** (P3): `NopMetaQualityCheckpointBizModel.save()` missing `@Name("data")` annotation

### Workstream B — BizModel Restructuring & Oversized Files

- **07-01** / **11-01** (P1/P3): `NopMetaSearchBizModel` is a pseudo-BizModel with no backing entity and no xmeta — 3 search methods orphaned outside the xmeta/BizModel contract system
- **07-02** / **11-02** (P1): 13 `Map<String,Object>` return methods across 7 BizModels — GraphQL schema derivation cannot produce typed return types. Partial migration deferred from Plan 311 scope (which focused on 11 high-frequency methods; remaining 2 + 6 BizModels still pending)
- **07-03** / **02-04** (P1/P2): 4 BizModels exceed 500 lines — `TableBizModel` (931), `LineageEdgeBizModel` (878), `ModuleBizModel` (631), `DataSourceBizModel` (542). Plan 300 Phase 3 split utility/executor classes but not the BizModels themselves
- **02-02** (P1): `AggregationContext.java` at 1854 lines — aggregation logic, field resolution, schema inference, and error handling all in one file
- **02-09** (P3): `NopMetadataErrors.java` at 1001 lines after Plan 300 Phase 2 added 190 ErrorCode constants
- **07-04** (P2): 11 call sites use `dao().getEntityById(id)` instead of `requireEntity(id)` — no entity-not-found fast-fail
- **07-05** (P2): `daoProvider()` intermediate API + double cast in `queryEntityData()`
- **01-02** (P2): `nop-metadata-service/pom.xml:74-77` declares compile dependency on `nop-search-lucene` — concrete coupling to Lucene implementation
- **04-16** (P2): `NopMetaDataSource` missing `name` unique key in ORM model

### Workstream C — Documentation & Small-Fix Cleanup

- **18-08** (P1): `docs-for-ai/03-modules/nop-metadata.md:133` — DTO location documented as `nop-metadata-dao`, actual location is `nop-metadata-core/dto/`
- **18-09** (P2): Module structure table at `nop-metadata.md:130-137` missing `codegen` and `api` modules
- **18-10** (P2): `core` module description incomplete — missing 29 DTO classes
- **04-07** (P2): Business columns appear after audit columns in multiple entities (`NopMetaTable`, `DataContract` etc.) — ORM readability issue
- **04-08** (P3): Index `IX_NOP_META_TABLE_LOOKUP` naming non-standard
- **04-10** (P3): `NopMetaModelChangedEvent.changeSource` missing `ext:dict` attribute
- **04-12** (P3): `json-1000`/`json-4000` domain columns have redundant `precision` attributes
- **02-03** / **16-04** (P2): `TestNopMetaAggregationBizModel` god test at 2592 lines — 65 tests in one class, brittle ordering dependencies
- **16-05** (P2): High helper method duplication across 12+ test files
- **02-06** (P3): `nop-metadata-core` module misnamed — contains DTOs and constants, not "core" framework logic
- **02-07** (P3): 40 hand-written `NopMeta*.java` entity classes in `dao/entity/` are empty stubs (generation target, no custom logic)
- **02-08** (P3): 39 BizModel files in single `service/entity/` directory — flat structure
- **09-02** (P3): `NopMetadataException` underused in tier-2 internals — many internal methods throw `NopException(ErrorCode)` directly instead of `NopMetadataException`
- **09-06** (P3): Variable name `SQL_VIEW` vs ErrorCode string `sql-view` inconsistency in `NopMetadataErrors.java`

## Goals

- **WS-A**: Eliminate raw type cast patterns, reduce `@SuppressWarnings("unchecked")` count by >=50%, migrate BizModel return types from `Map<String,Object>` to `@DataBean` DTOs where targets exist
- **WS-B**: Reduce BizModel files >500 lines to <500 lines; split `AggregationContext`; fix BizModel coding patterns (requireEntity, daoProvider); make NopMetaSearchBizModel conform to BizModel contract
- **WS-C**: Fix doc-code inconsistencies in `nop-metadata.md`; clean ORM model micro-issues; split god test; document test helpers; address P3 cluster items

## Non-Goals

- Complete Map→DTO migration for ALL BizModel methods (only targetable methods; dynamic-schema methods remain Map)
- Full module restructuring (core rename, entity class move) — deferred per impact assessment
- GraphQL schema changes from DTO migration (backward compatible)
- Runtime behavior change beyond fail-fast patterns (requireEntity)
- Frontend page audit

## Scope

### In Scope

- **WS-A**: Extract `newArrayHolder()` utility; fix `INopMetaLineageEdgeBiz` return types; reduce unchecked casts in executors; migrate targetable Map returns to DTO; fix testConnect and qualityCheckpoint patterns; add @Name("data"); reduce @SuppressWarnings count
- **WS-B**: Split AggregationContext by concern (aggregation logic, field resolution, error handling); split oversized BizModels (TableBizModel, LineageEdgeBizModel); fix requireEntity at 11 call sites; fix daoProvider double cast; add xmeta for NopMetaSearchBizModel or merge methods into entity-backed BizModel; remove search-lucene compile dependency; add name UK on NopMetaDataSource
- **WS-C**: Fix nop-metadata.md DTO location/module structure/core description; fix ORM model issues (column ordering, index naming, ext:dict, redundant precision); split TestNopMetaAggregationBizModel; extract shared test helpers; mark empty entity classes and core module status in docs; fix ErrorCode variable naming inconsistency

### Out Of Scope

- I*Biz interface relocation from dao module (plan 311)
- NopMetadataErrors.java splitting (P3 — deferred)
- Empty entity class deletion (generation targets, P3)
- Full test helper deduplication across all 12+ files (P2 — partial)
- cascade-delete (plan 1)
- ErrorCode contract violations (plan 1)

## Execution Plan

### Workstream A — Type Safety Antipattern Remediation

Status: planned
Targets: `nop-metadata/nop-metadata-service/`, `nop-metadata/nop-metadata-dao/`

- Item Types: `Fix`, `Proof`

- [ ] A1: Extract `ArrayHolderUtils.newArrayHolder()` utility from the triplicated `(List<Map<String,Object>>[]) new List<?>[1]` pattern — apply to all 3 files
- [ ] A2: Change `INopMetaLineageEdgeBiz` return types from `Map<String,Object>` to `LineagePathDTO` / `ImpactAnalysisDTO` / other existing `@DataBean` targets — update BizModel and tests
- [ ] A3: Eliminate double raw cast `(IOrmEntityDao)(IOrmEntityDao)` pattern — introduce typed helper or fix hierarchy
- [ ] A4: Migrate `testConnect()` in `NopMetaDataSourceBizModel` to return `@DataBean ConnectionTestResultDTO` — eliminate 4 instanceof checks
- [ ] A5: Fix `NopMetaQualityCheckpointBizModel` — replace `Map<String,Object>` unsafe accumulator with typed DTO; add `@Name("data")` annotation on `save()` parameter
- [ ] A6: Audit and consolidate `@SuppressWarnings("unchecked")` — target >=50% reduction (from 37 to <=18) by fixing the root cast patterns

Exit Criteria (Workstream A):

- [ ] All 3 generic array creation sites use shared `ArrayHolderUtils` helper (0 triplication)
- [ ] `INopMetaLineageEdgeBiz` return types use `@DataBean` DTOs where targets exist
- [ ] 0 double raw cast `(IOrmEntityDao)(IOrmEntityDao)` patterns remain
- [ ] `testConnect()` returns typed DTO, not Map
- [ ] `@SuppressWarnings("unchecked")` count <= 18 (from 37)
- [ ] `./mvnw compile -pl nop-metadata -am` passes
- [ ] `./mvnw test -pl nop-metadata -am` passes
- [ ] No owner-doc update required: internal type safety refactoring, no public API change
- [ ] No new test required: internal refactoring (DTO migration, cast reduction) — existing test suite covers behavior; no new functionality added
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Workstream B — BizModel Restructuring & Oversized File Splitting

Status: planned
Targets: `nop-metadata/nop-metadata-service/`

- Item Types: `Fix`, `Decision`, `Follow-up`

- [ ] B1: Split `AggregationContext.java` (1854 lines) by concern — separate aggregation logic, field resolution, schema helpers into 2-3 files
- [ ] B2: Split `NopMetaTableBizModel.java` (931 lines) — extract non-CRUD action methods (profileTable, createSqlTable, previewSqlFields, etc.) into helper classes
- [ ] B3: Split `NopMetaLineageEdgeBizModel.java` (878 lines) — extract lineage path/impact analysis logic into dedicated service
- [ ] B4: Add xmeta for `NopMetaSearchBizModel` or merge its 3 search methods into an entity-backed BizModel (affects GraphQL schema derivation)
- [ ] B5: Fix all 11 `dao().getEntityById(id)` → `requireEntity(id)` call sites for fail-fast
- [ ] B6: Fix `daoProvider()` intermediate API + double cast pattern in `queryEntityData()`
- [ ] B7: Remove `nop-search-lucene` from `nop-metadata-service/pom.xml` (concrete dependency coupling)
- [ ] B8: Add unique key `uk_meta_datasource_name` on `NopMetaDataSource.name` column in ORM model

Exit Criteria (Workstream B):

- [ ] `AggregationContext.java` <= 800 lines (from 1854)
- [ ] `NopMetaTableBizModel.java` <= 500 lines (from 931)
- [ ] `NopMetaLineageEdgeBizModel.java` <= 500 lines (from 878)
- [ ] `NopMetaSearchBizModel` has xmeta or merged into entity-backed BizModel
- [ ] 0 call sites use `dao().getEntityById()` where `requireEntity()` applies
- [ ] 0 `daoProvider()` double cast patterns remain
- [ ] `nop-metadata-service/pom.xml` has 0 dependency on `nop-search-lucene`
- [ ] `NopMetaDataSource` has `uk_meta_datasource_name` unique key in ORM model
- [ ] `./mvnw compile -pl nop-metadata -am` passes
- [ ] `./mvnw test -pl nop-metadata -am` passes
- [ ] `No owner-doc update required` for B1-B3, B5-B8 (internal refactoring). B4 may require xmeta update.
- [ ] No new test required for B1-B6 (pure refactoring, fail-fast pattern fix, no behavioral change). B7-B8 (POM/ORM config) also no new test required.
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Workstream C — Documentation, ORM Model Micro-Fixes, Test Quality

Status: planned
Targets: `docs-for-ai/03-modules/nop-metadata.md`, `nop-metadata/model/nop-metadata.orm.xml`, `nop-metadata-service/src/test/`

- Item Types: `Fix`, `Proof`

- [ ] C1: Fix `nop-metadata.md:133` DTO location — change `nop-metadata-dao` to `nop-metadata-core/dto/`
- [ ] C2: Update module structure table at `nop-metadata.md:130-137` — add `codegen` and `api` rows; update `core` description to reflect 29 DTO classes
- [ ] C3: Reorder ORM model columns — move business columns before audit columns in affected entities (`NopMetaTable`, `DataContract`, etc.)
- [ ] C4: Standardize index naming — fix `IX_NOP_META_TABLE_LOOKUP` to follow convention (use entity prefix pattern)
- [ ] C5: Add `ext:dict` on `NopMetaModelChangedEvent.changeSource` attribute
- [ ] C6: Remove redundant `precision` on `json-1000`/`json-4000` domain-using columns
- [ ] C7: Split `TestNopMetaAggregationBizModel` (2592 lines, 65 tests) — extract test groups by business domain
- [ ] C8: Extract shared test helpers from 12+ test files into dedicated `TestHelper` class — deduplicate setup/assertion utilities
- [ ] C9: Fix `NopMetadataErrors.java` variable naming inconsistency (`SQL_VIEW` → `SQL_VIEWS` or align string to match `sql-view`)

Exit Criteria (Workstream C):

- [ ] `nop-metadata.md` DTO location, module structure table, and core module description all accurate per live code
- [ ] Business columns appear before audit columns in all ORM model entities
- [ ] All index names follow naming convention
- [ ] `changeSource` has `ext:dict` attribute
- [ ] 0 `json-1000`/`json-4000` columns with redundant `precision` attribute
- [ ] `TestNopMetaAggregationBizModel` <= 1400 lines (from 2592)
- [ ] Shared test helpers extracted into at least 1 shared class
- [ ] ErrorCode variable names consistent with string values
- [ ] `./mvnw compile -pl nop-metadata -am` passes
- [ ] `./mvnw test -pl nop-metadata -am` passes
- [ ] `docs-for-ai/03-modules/nop-metadata.md` updated (C1, C2)
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Workstream 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] Workstream A — Type Safety: all Exit Criteria satisfied
- [ ] Workstream B — BizModel Restructuring: all Exit Criteria satisfied
- [ ] Workstream C — Documentation & Cleanup: all Exit Criteria satisfied
- [ ] No in-scope live defect downgraded to deferred/follow-up
- [ ] `./mvnw compile -pl nop-metadata -am` passes
- [ ] `./mvnw test -pl nop-metadata -am` passes
- [ ] Affected owner docs (`docs-for-ai/03-modules/nop-metadata.md`) updated
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**: split files have callers updated; extracted helpers are referenced; xmeta additions are wired to BizModel

## Deferred But Adjudicated

### Module Renaming (`nop-metadata-core`)

- Classification: `watch-only residual`
- Why Not Blocking Closure: Renaming a published Maven module breaks external consumers; the misnaming (contains DTOs+constants, not framework-core) is cosmetic. A rename requires cross-module POM updates and is best batched with a future major version.
- Successor Required: no
- Successor Path: N/A

### Empty Entity Classes (`dao/entity/NopMeta*.java`)

- Classification: `watch-only residual`
- Why Not Blocking Closure: These are codegen targets intentionally left empty (retention pattern for custom overrides). They block nothing; deleting them would break the expected Nop platform pattern where hand-written entity subclasses mirror generated `_NopMeta*` superclasses.
- Successor Required: no
- Successor Path: N/A

### Flat BizModel Directory (`service/entity/`)

- Classification: `optimization candidate`
- Why Not Blocking Closure: 39 files in one directory is organizationally messy but functionally transparent. Maven/IDE search handles flat structures fine. Would need a module-level sub-package decision.
- Successor Required: no
- Successor Path: N/A

### NopMetadataError Lines (1001 lines, P3)

- Classification: `optimization candidate`
- Why Not Blocking Closure: All ErrorCodes are centrally managed. File length is a readability concern but not a defect. Splitting them by subdomain would need convention agreement with other modules.
- Successor Required: no
- Successor Path: N/A

## Non-Blocking Follow-ups

- Test helper deduplication beyond initial extraction (C8 partial) — C8 reduces duplication but full dedup across all 12+ files is P2 optimization
- `NopMetadataException` tier-2 usage promotion (09-02, P3) — underused but not defective behavior

## Closure

Status Note: <<完成或关闭时填写>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者>>
- Evidence: <<待填写>>

Follow-up:

- no remaining plan-owned work
