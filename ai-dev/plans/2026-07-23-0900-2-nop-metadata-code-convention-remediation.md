# 02 nop-metadata Code Convention & Quality Remediation

> Plan Status: active
> Last Reviewed: 2026-07-23
> Source: Multi-dim audit `2026-07-23-0714-multi-audit-nop-metadata.md` + Open audit `2026-07-23-0714-open-audit-nop-metadata.md`
> Related: Plan {1} (infrastructure fixes, must complete first); Plan {3} (testing/codegen)

## Purpose

Align nop-metadata BizModel, error handling, ORM model, and XMeta code with documented nop-entropy conventions. Eliminate systemic contract drift between `docs-for-ai/` documented patterns and live code.

## Current Baseline

- **requireEntity() bypass**: 8 BizModels, ~20 `@BizMutation` methods use `dao().getEntityById(id)` instead of `requireEntity(id, actionName, context)` — P2, affects data auth enforcement [07-003]
- **Redundant txn nesting**: `NopMetaDataContractBizModel.java` wraps `@BizMutation` body in `txn().runInTransaction(...)` — P2, violates documented convention [07-002]
- **Map return types**: `NopMetaTableBizModel` returns `List<Map<String, Object>>` instead of typed `@DataBean` — P2, no type safety or GraphQL selection [07-004]
- **Missing I*Biz interface methods**: `INopMetaDataContractBiz` missing `checkContractReadOnly`; `INopMetaTagLabelBiz` missing `propagateTags`/`suggestTags` — P2, methods invisible to cross-module callers [07-005, 07-006]
- **N+1 upsert**: 3 lineage edge extraction methods do individual SELECT+INSERT per candidate — P2 [AR-25]
- **ErrorCode naming**: Systemic use of hyphens instead of dots as sub-domain separator — P2, documented as intentional in NopMetadataErrors [09-007]
- **Bare NopException**: 3 files use bare `NopException` instead of module-specific `NopMetadataException` — P3 [09-002, 09-003, 09-004]
- **Inline param strings**: Widespread use of inline string keys in `.param()` instead of `ARG_*` constants — P3 [09-005]
- **Empty catch block**: `MetaTableProfiler` has empty `catch (SQLException)` — P3 [09-006]
- **Swallowed exceptions**: `NopMetaSearchService.addToIndex` catches all `Exception` at WARN level — P3 [AR-29]
- **RuntimeException pass-through**: `TableReferenceExecutor` has unhandled RuntimeException patterns — P3 [AR-30]
- **AggregationContext null fields**: `measureNames`, `dimensionNames`, `orderBy` default to null instead of empty list — P3 [AR-40]
- **ORM model issues**: redundant index [04-001], missing `stdDomain="json"` [04-004], unused dicts [04-005], inconsistent dict case [04-006], undocumented cascade [04-007] — P2/P3
- **XMeta field protection gaps**: `connectionConfigComponent` not restricted [11-001], 38/42 retention xmeta have empty `<props/>` [11-003], `computeQualityScore` bypasses xmeta insertable validation [11-004], `sourceSql`/`buildSql` not flagged sensitive [11-006] — P2/P3
- **Duplicate constant**: `STATUS_MANUAL` in `NopMetaReconciliationResultBizModel` — P3 [11-002]
- **Redundant @GraphQLReturn**: on `NopMetaGlossaryTermBizModel.update()` — P3 [11-005]
- **@Deprecated method retention**: `NopMetaDataContractBizModel` retains deprecated methods — P3 [07-008]
- **saveEntity bypass**: `NopMetaLineageEdgeBizModel` uses `dao().saveEntity()` bypassing CrudBizModel pipeline — P3 [07-007]
- **Pseudo-BizModel**: `NopMetaSearchBizModel` has no corresponding entity or xmeta — P2 [07-001 / 03-002]

## Goals

- All `@BizMutation` methods use `requireEntity()` instead of `dao().getEntityById()`
- No redundant `txn()` nesting inside `@BizMutation` methods
- No `Map<String, Object>` return types in BizModel public methods (replace with `@DataBean`)
- All missing I*Biz interface methods declared
- N+1 upsert converted to batch merge/upsert pattern
- ErrorCode naming convention resolved with documented decision
- All bare `NopException` sites replaced with `NopMetadataException`
- Inline param strings replaced with `ARG_*` constants
- Empty exception handlers replaced with logging or rethrow
- Swallowed exceptions either logged properly or rethrown as `NopMetadataException`
- RuntimeException pass-through paths hardened
- `AggregationContext` collection fields initialized to empty list
- ORM model index/domain/dict issues cleaned up
- XMeta field protection gaps closed (restricted fields, sensitive tags, non-empty `<props/>`)
- Deprecated methods removed, redundant annotations cleaned up
- `NopMetaSearchBizModel` either given an entity/xmeta or explicitly documented as pseudo-BizModel
- `dao().saveEntity()` bypass replaced with proper CrudBizModel pipeline

## Non-Goals

- Not modifying build infrastructure or Docker files (covered in Plan {1})
- Not adding new test coverage or AutoTest snapshots (deferred to Plan {3})
- Not changing codegen pipeline configuration
- Not refactoring module boundaries or re-packaging

## Scope

### In Scope

- BizModel convention compliance (requireEntity, txn, Map return, I*Biz interfaces)
- Error handling (exception types, param constants, empty catches, swallowed exceptions, RuntimeException)
- N+1 upsert refactor
- ORM model polish (indexes, domains, dicts, cascade docs)
- XMeta field protection
- Deprecated method removal, redundant annotation cleanup
- Pseudo-BizModel documentation
- AggregationContext null-safety
- ErrorCode naming decision (hyphen vs dot) — document as intentional convention or migrate to dots

### Out Of Scope

- Adding new entities or xmeta files
- Enabling CRUD API codegen (deferred to Plan {3})
- Adding new test methods (deferred to Plan {3})
- Docker or POM changes (Plan {1})

## Execution Plan

### Phase 1 - BizModel convention alignment

Status: planned
Targets: All BizModel classes in nop-metadata-service

- Item Types: `Fix`

- [ ] Replace `dao().getEntityById(id)` with `requireEntity(id, actionName, context)` in all `@BizMutation` methods (~20 methods across 8 BizModels) (07-003)
- [ ] Remove redundant `txn().runInTransaction(...)` wrappers from `@BizMutation` in NopMetaDataContractBizModel (07-002)
- [ ] Replace `List<Map<String, Object>>` return type in NopMetaTableBizModel with a typed `@DataBean` class (07-004)
- [ ] Add `checkContractReadOnly` to `INopMetaDataContractBiz` interface (07-005)
- [ ] Add `propagateTags` and `suggestTags` to `INopMetaTagLabelBiz` interface (07-006)
- [ ] Replace `dao().saveEntity()` with proper CrudBizModel pipeline call in NopMetaLineageEdgeBizModel (07-007)
- [ ] Remove `@Deprecated` methods from NopMetaDataContractBizModel (07-008)
- [ ] Decide and implement fate of NopMetaSearchBizModel: either add corresponding entity+xmeta or add explicit `@BizModel` doc comment explaining pseudo-BizModel status (07-001/03-002)
- [ ] Remove redundant `@GraphQLReturn` on NopMetaGlossaryTermBizModel.update() (11-005)

Exit Criteria:

- [ ] All BizModel mutation methods verified: grep for `dao().getEntityById` in nop-metadata-service returns 0 matches
- [ ] No `txn().runInTransaction` inside any `@BizMutation` in nop-metadata-service
- [ ] No `Map<String, Object>` or `List<Map<String, Object>>` return types on public BizModel methods
- [ ] All missing interface methods declared and implemented
- [ ] No `@Deprecated` methods in BizModel classes
- [ ] NopMetaSearchBizModel status resolved (entity+xmeta or documented pseudo-BizModel)
- [ ] `./mvnw compile -pl nop-metadata-service -am` passes
- [ ] `./mvnw test -pl nop-metadata-service -am` passes
- [ ] Owner-doc update required: `docs-for-ai/02-core-guides/service-layer.md` contract drift items (CR-01, CR-02, CR-07, CR-08) verified as resolved
- [ ] `ai-dev/logs/` updated

### Phase 2 - Error handling hardening

Status: planned
Targets: ErrorCode files, exception classes, catch blocks across nop-metadata-service

- Item Types: `Fix | Decision`

- [ ] Resolve ErrorCode naming convention: decide whether to migrate hyphens to dots or document hyphen convention as intentional alignment with nop-entropy convention. If migrate, update all ~80 ErrorCode constants (09-007)
- [ ] Replace bare `NopException` with `NopMetadataException` in:
  - NopMetaDataContractBizModel (6 sites) (09-002)
  - MetaTableFieldResolver (09-003)
  - MetaQualityRuleExecutor (09-004)
- [ ] Replace inline `.param("key", value)` strings with `ARG_*` constants across all error sites (09-005)
- [ ] Fix empty `catch (SQLException)` in MetaTableProfiler: add error logging or rethrow (09-006)
- [ ] Fix swallowed exceptions in NopMetaSearchService.addToIndex: change from `catch (Exception)` at WARN to proper error propagation (AR-29)
- [ ] Fix RuntimeException pass-through in TableReferenceExecutor (both `executeOnPlatformConnection` and `executeOnExternalConnection`): wrap in `NopMetadataException` with ErrorCode (AR-30)
- [ ] Initialize `AggregationContext` collection fields to `Collections.emptyList()` to prevent NPE on unset fields (AR-40)
- [ ] Remove duplicate constant `STATUS_MANUAL` in NopMetaReconciliationResultBizModel (11-002)

Exit Criteria:

- [ ] No bare `NopException` used in nop-metadata-service module (verify with grep)
- [ ] All `.param()` calls use `ARG_*` constants
- [ ] No empty `catch` blocks in nop-metadata-service
- [ ] No `catch (Exception)` blocks at WARN level without error propagation
- [ ] `TableReferenceExecutor` wraps all exceptions in `NopMetadataException`
- [ ] `AggregationContext` fields initialized to `Collections.emptyList()`
- [ ] **无静默跳过**: No empty `catch` blocks, no swallowed `catch (Exception)` at WARN level, no unhandled RuntimeException pass-through remaining
- [ ] `./mvnw compile -pl nop-metadata-service -am` passes
- [ ] `./mvnw test -pl nop-metadata-service -am` passes
- [ ] Owner-doc update required: `docs-for-ai/02-core-guides/error-handling.md` updated if ErrorCode naming convention changes; else No owner-doc update required
- [ ] `ai-dev/logs/` updated

### Phase 3 - ORM model and XMeta polish

Status: planned
Targets: ORM model files, XMeta files

- Item Types: `Fix`

- [ ] Remove redundant index `IX_NOP_META_SEM_TYPE_NAME` from NopMetaSemanticType (duplicates UK) (04-001)
- [ ] Add non-unique index on `status` column for NopMetaDataSource (04-002)
- [ ] Fix comment in NopMetaDataProduct: replace "报表定义（预留）" with correct data product description (04-003)
- [ ] Add `stdDomain="json"` to NopMetaQualityCheckpoint.extConfig (04-004)
- [ ] Remove or add `ext:dict` references for 3 unused dicts (04-005)
- [ ] Normalize dict value case style (04-006)
- [ ] Add cascade behavior documentation comment to NopMetaModule self-referencing FK (04-007)
- [ ] Add `restricted` prop to `connectionConfigComponent` in NopMetaDataSource xmeta (matching sibling `connectionConfig`) (11-001)
- [ ] Populate empty `<props/>` in retention xmeta files with field-level access control overrides (11-003)
- [ ] Fix `computeQualityScore` to respect xmeta insertable validation instead of using `dao().saveEntity()` directly (11-004)
- [ ] Add `tagSet="sensitive"` to `sourceSql`/`buildSql` fields for event redaction (11-006)

Exit Criteria:

- [ ] ORM model indexes are non-redundant (verify UK uniqueness)
- [ ] `NopMetaQualityCheckpoint.extConfig` has `stdDomain="json"`
- [ ] Dicts are either referenced or removed; case style is consistent
- [ ] Cascade behavior documented on NopMetaModule self-referencing FK
- [ ] XMeta field-level protection: `connectionConfigComponent` is restricted; retention xmeta files have non-empty `<props/>` where needed
- [ ] `computeQualityScore` no longer bypasses xmeta insertable validation
- [ ] `sourceSql`/`buildSql` have `tagSet="sensitive"`
- [ ] `./mvnw compile -pl nop-metadata-service,nop-metadata-meta -am` passes
- [ ] `./mvnw test -pl nop-metadata-service,nop-metadata-meta -am` passes
- [ ] Owner-doc update required: none (ORM and XMeta are self-documenting in model files)
- [ ] `ai-dev/logs/` updated

## Closure Gates

- [ ] All P2 BizModel findings (07-002 through 07-006, 07-001, 03-001, 03-002) resolved and verified
- [ ] All P3 BizModel findings (07-007, 07-008) resolved
- [ ] All P2 error handling findings (09-007, AR-25) resolved or definitively adjudicated
- [ ] All P3 error handling findings (09-002 through 09-006, AR-29, AR-30, AR-40) resolved
- [ ] All ORM model findings (04-001 through 04-007) resolved
- [ ] All XMeta findings (11-001, 11-003, 11-004, 11-006) resolved
- [ ] All redundant/duplicate annotations cleaned up (11-002, 11-005)
- [ ] `./mvnw compile -pl nop-metadata-service,nop-metadata-meta -am` passes
- [ ] `./mvnw test -pl nop-metadata-service,nop-metadata-meta -am` passes
- [ ] All contract drift with `docs-for-ai/` resolved (CR-01 through CR-08 except CR-05/CR-06 which belong to Plan {1}/{3})
- [ ] Independent sub-agent closure-audit completed and evidence recorded
- [ ] Anti-Hollow Check: verified that `requireEntity()` is actually called in runtime paths (not just import replaced) by reviewing key BizModel mutation flows

## Deferred But Adjudicated

### N+1 upsert optimization (AR-25)

- Classification: optimization candidate
- Why Not Blocking Closure: The N+1 pattern in lineage edge extraction does not cause incorrect results — each SELECT+INSERT transactionally produces the correct output. Performance degradation only matters at scale (>1000 edges per extraction). Current baseline: lineage extraction is called on-demand, not in hot paths. This can be optimized as a follow-up when volume warrants.
- Successor Required: no

### ErrorCode naming: hyphens vs dots (09-007)

- Classification: watch-only residual
- Why Not Blocking Closure: The module author has explicitly documented the hyphen convention as intentional in `NopMetadataErrors.java:22`. This is a deliberate design choice, not an oversight. If future cross-module tooling requires dot-separated ErrorCodes for sub-domain extraction, this becomes a migration project. For now, the documented intention stands.
- Successor Required: no

## Non-Blocking Follow-ups

- N+1 upsert optimization: consider batch merge pattern when lineage extraction volume grows (deferred, see above)
- ErrorCode naming: if future tooling requires dot separators, plan a migration (deferred, see above)

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- N+1 upsert optimization (watch-only, successor not required)
- ErrorCode naming alignment if tooling demands it (watch-only, successor not required)
