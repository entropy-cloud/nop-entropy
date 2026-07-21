# 06 nop-metadata BizModel API Completeness & Contract Alignment

> Plan Status: active
> Execution Order: 2
> Last Reviewed: 2026-07-21
> Source: `ai-dev/audits/2026-07-21-2039-multi-audit-nop-metadata.md` (维度11-01, 11-04, 07-04, 07-02, 07-03, 04-01, 11-05, 04-02, 04-04, 01-01, 01-05), `ai-dev/audits/2026-07-21-2039-open-audit-nop-metadata.md` (AR-18)
> Related: `05-nop-metadata-security-and-error-hardening.md`, `07-nop-metadata-test-coverage-remediation.md`

## Purpose

Close all BizModel API completeness, contract alignment, and ORM schema correctness findings in nop-metadata. Establishes the correct API surface so that Plan 07 (test coverage) can write meaningful integration tests against stable interfaces.

## Current Baseline

- `NopMetaSearchBizModel` (line 28) declares `@BizModel("NopMetaSearch")` but has **no corresponding xmeta file** — the only `@BizModel` in 40 without one. Frontend GraphQL calls fail; only `/r/` REST path works.
- 7 `CrudBizModel<E>` subclasses extend `CrudBizModel<E>` without implementing the corresponding `I*Biz<E>` interface: `NopMetaQualityCheckpointBizModel`, `NopMetaTableDimensionBizModel`, `NopMetaReconciliationConfigBizModel`, `NopMetaModelChangedEventBizModel`, `NopMetaReconciliationResultBizModel`, `NopMetaQualityScoreBizModel`, `NopMetaTableFilterBizModel`. The `I*Biz` interfaces already exist in `nop-metadata-dao`; the gap is in the `implements` clause.
- `NopMetaSearchBizModel` does not have a corresponding I*Biz interface (it is not a CrudBizModel subclass). Excluded from the 40/40 count.
- `NopMetaDataSourceBizModel` (lines 120, 175, 253) uses `dao().getEntityById()` directly in 3 mutation methods instead of `requireEntity()` — bypasses authorization/validation layer.
- `NopMetaLineageEdgeBizModel.recordLineage()` (line 123-125) accepts `List<Map<String, Object>>` instead of a type-safe DTO.
- `NopMetaModuleBizModel.importOrmModels()` (line 361-386) returns `List<Map<String, Object>>` instead of DTO.
- `NopMetaModelChangedEvent` entity's `changeSource` field references `ext:dict="meta/change-source"` but no corresponding `<dicts>` definition exists in `nop-metadata.orm.xml:2814`.
- `NopMetaDataSource.connectionConfig` retention xmeta (`NopMetaDataSource.xmeta`) has `sortable="true"` (inherited from generated) while `queryable="false"` — contradictory combination (dimension11-05). Fix in the retention xmeta (non-generated file), not in `_NopMetaDataSource.xmeta`.
- `NopMetaTableBizModel` has 3 `@Deprecated` private wrapper methods (`buildExternalSelectSql` at 715, `buildSqlSelectSql` at 726, `executeQuery` at 736) that only delegate to `MetaTableQueryExecutor` — should be removed; update call sites (queryExternalData at 658, querySqlData at 680) to call `MetaTableQueryExecutor` directly.
- `nop-metadata-api/pom.xml` has **no `<parent>` element** — it uses bare `<groupId>`/`<version>`/`<properties>`. Other api submodules have `<parent>` pointing to the owning module (e.g., nop-stream-api → nop-stream). Missing parent means it skips inheriting managed dependency versions and plugin config.
- nop-metadata submodules not registered in `nop-bom/pom.xml` (dimension01-05).
- `NopMetaGlossary`→`NopMetaGlossaryTerm` relationship lacks `cascadeDelete="true"` on the `to-many` side, inconsistent with `Classification→Tag` pattern (dimension04-02).
- `NopMetaTagLabel` entity lacks a unique constraint, risking duplicate label entries (dimension04-04).

## Goals

- NopMetaSearchBizModel has a valid xmeta or is refactored to non-BizModel processor
- All 39 CrudBizModel subclasses implement their I*Biz interfaces (7 fixed, 32 already correct)
- No `dao().getEntityById()` bypasses remain where `requireEntity()` should be used
- No `Map<String, Object>` API signatures remain where DTOs should be used
- ORM model dictionary, cascadeDelete, and unique constraint gaps fixed
- API module parent and BOM registration aligned
- Deprecated private wrappers removed

## Non-Goals

- **Not** changing the search index implementation or query semantics
- **Not** adding new test coverage (covered by Plan 07)
- **Not** redesigning the connection security layer (covered by Plan 05)
- **Not** addressing silent exception catches (covered by Plan 05)

## Scope

### In Scope

- Create xmeta for `NopMetaSearchBizModel` or refactor to Processor if xmeta is semantically inappropriate
- Add `implements I*Biz<E>` to 7 CrudBizModel subclasses (interfaces already exist in -dao module)
- Replace `dao().getEntityById()` with `requireEntity()` in `NopMetaDataSourceBizModel` (3 call sites at lines 120, 175, 253)
- Replace `List<Map<String, Object>>` with type-safe `@DataBean` DTO in `recordLineage()` and `importOrmModels()`; place DTOs in `nop-metadata-core/src/main/java/io/nop/metadata/core/dto/` following existing patterns
- Define `meta/change-source` dictionary in `nop-metadata.orm.xml` `<dicts>` section
- Fix `sortable="false"` on `connectionConfig` in retention `NopMetaDataSource.xmeta`
- Remove 3 `@Deprecated` private wrapper methods in `NopMetaTableBizModel` and update call sites
- Add `<parent>nop-metadata</parent>` to `nop-metadata-api/pom.xml`
- Register nop-metadata submodules in `nop-bom/pom.xml`
- Add `cascadeDelete="true"` to `NopMetaGlossary→NopMetaGlossaryTerm` relation in orm.xml
- Add unique constraint to `NopMetaTagLabel` entity in orm.xml
- Run code generation after ORM model changes to update generated files

### Out Of Scope

- General test coverage improvements (Plan 07)
- Error handling improvements (Plan 05)
- Aggregation processor restructuring (already completed in Plan 04)

## Execution Plan

### Phase 1 — xmeta and I*Biz interface completeness

Status: planned
Targets: `NopMetaSearchBizModel.java`, 7 BizModel files in `service/entity/`, corresponding I*Biz interfaces in `-dao/biz/`

- Item Types: `Fix | Fix | Decision`

- [ ] Create xmeta for `NopMetaSearchBizModel` (place in `nop-metadata-meta` following existing xmeta convention). Decision rule: if `NopMetaSearchBizModel` has no entity-backed fields that need GraphQL schema generation, refactor to a plain Processor class (not @BizModel) instead of creating a near-empty xmeta; otherwise create xmeta with the search input/output fields
- [ ] Add `implements INopMetaQualityCheckpointBiz` to `NopMetaQualityCheckpointBizModel`
- [ ] Add `implements INopMetaTableDimensionBiz` to `NopMetaTableDimensionBizModel`
- [ ] Add `implements INopMetaReconciliationConfigBiz` to `NopMetaReconciliationConfigBizModel`
- [ ] Add `implements INopMetaModelChangedEventBiz` to `NopMetaModelChangedEventBizModel`
- [ ] Add `implements INopMetaReconciliationResultBiz` to `NopMetaReconciliationResultBizModel`
- [ ] Add `implements INopMetaQualityScoreBiz` to `NopMetaQualityScoreBizModel`
- [ ] Add `implements INopMetaTableFilterBiz` to `NopMetaTableFilterBizModel`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `NopMetaSearchBizModel` has a valid xmeta file (or is refactored away from @BizModel)
- [ ] All 7 CrudBizModel subclasses now implement their corresponding I*Biz interface (verify: grep `implements.*I.*Biz` on each)
- [ ] `./mvnw compile -pl nop-metadata -am` passes
- [ ] **No owner-doc update required** (service-layer.md already documents the rule; this brings code into compliance)
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — API contract alignment

Status: planned
Targets: `NopMetaDataSourceBizModel.java`, `NopMetaLineageEdgeBizModel.java`, `NopMetaModuleBizModel.java`, `NopMetaTableBizModel.java`, `nop-metadata-api/pom.xml`, `nop-bom/pom.xml`

- Item Types: `Fix | Fix | Fix`

- [ ] Replace `dao().getEntityById()` with `requireEntity()` in `NopMetaDataSourceBizModel` (3 occurrences at lines 120, 175, 253) — this is the scope; other `getEntityById` calls in other BizModels (quality, module, table, etc.) are out of scope
- [ ] Create `RecordLineageDTO` in `nop-metadata-core/dto/`; update `recordLineage()` to accept DTO instead of `List<Map<String, Object>>`. For `importOrmModels()`, use existing `ImportOrmModelResultDTO` (already in `nop-metadata-core/dto/`) as return type
- [ ] Remove 3 `@Deprecated` private wrapper methods in `NopMetaTableBizModel` (buildExternalSelectSql at 715, buildSqlSelectSql at 726, executeQuery at 736); update call sites (659 queryExternalData → buildExternalSelectSql, 680 querySqlData → buildSqlSelectSql, 681 querySqlData → executeQuery) to call `MetaTableQueryExecutor` directly
- [ ] Add `<parent>` block pointing to `nop-metadata` in `nop-metadata-api/pom.xml`
- [ ] Register nop-metadata submodules in `nop-bom/pom.xml`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `NopMetaDataSourceBizModel` no longer uses `dao().getEntityById()` (3 occurrences replaced with `requireEntity()`)
- [ ] `recordLineage()` and `importOrmModels()` use DTOs instead of `Map<String, Object>`
- [ ] No `@Deprecated` private wrapper methods in `NopMetaTableBizModel`; all call sites use `MetaTableQueryExecutor` directly
- [ ] `nop-metadata-api/pom.xml` has `<parent>` pointing to `nop-metadata` (inherits managed deps and plugin config)
- [ ] nop-metadata submodules registered in `nop-bom/pom.xml`
- [ ] `./mvnw compile -pl nop-metadata -am` passes
- [ ] **No owner-doc update required** (contract alignment; Map→DTO return type change is intentional GraphQL schema change, documented in code)
- [ ] DTO/API changes (RecordLineageDTO, importOrmModels return type) flagged for integration test coverage in Plan 07; handoff confirmed via plan cross-reference
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — ORM model gap closure

Status: planned
Targets: `nop-metadata/model/nop-metadata.orm.xml`, `nop-metadata-meta/src/main/resources/_vfs/nop/metadata/model/NopMetaDataSource/NopMetaDataSource.xmeta` (retention)

- Item Types: `Fix | Fix | Fix`

- [ ] Define `meta/change-source` dictionary in `nop-metadata.orm.xml` `<dicts>` section (dict YAML already exists at `meta/change-source.dict.yaml`; orm.xml entry is missing)
- [ ] Set `sortable="false"` on `connectionConfig` field in retention `NopMetaDataSource.xmeta` (not the generated `_NopMetaDataSource.xmeta`)
- [ ] Add `cascadeDelete="true"` to `NopMetaGlossary→NopMetaGlossaryTerm` relation in orm.xml
- [ ] Add unique constraint to `NopMetaTagLabel` entity in orm.xml on columns `(entityType, entityId, tagId, source)` to prevent duplicate label entries
- [ ] Run code generation (`mvn compile` or equivalent pipeline) to regenerate generated files from updated orm.xml

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `meta/change-source` dict defined and usable (verify via orm.xml `<dicts>` section + generation output)
- [ ] `connectionConfig` in retention xmeta has `sortable="false"` (verified in retention file)
- [ ] Glossary→GlossaryTerm has `cascadeDelete="true"` (verified in orm.xml and regenerated artifacts)
- [ ] TagLabel has a unique constraint (verified in orm.xml and regenerated artifacts)
- [ ] Generated files (e.g., `_NopMetaModelChangedEvent.xmeta`, `_app.orm.xml`) are up-to-date with model changes
- [ ] `./mvnw compile -pl nop-metadata -am` passes
- [ ] **No owner-doc update required** (ORM model corrections, no public API change)
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] P0 finding (NopMetaSearchBizModel missing xmeta) resolved
- [ ] All 39 CrudBizModel subclasses implement corresponding I*Biz interfaces (0 gap)
- [ ] `NopMetaDataSourceBizModel` no longer uses `dao().getEntityById()` (scoped — other BizModel `getEntityById` calls out of scope)
- [ ] `recordLineage()` and `importOrmModels()` use DTOs instead of `Map<String, Object>`
- [ ] ORM model gaps (dictionary, cascadeDelete, unique, sortable) resolved; generated files regenerated
- [ ] API module parent and BOM registration fixed
- [ ] Deprecated wrappers removed
- [ ] No in-scope live defect or contract drift deferred to follow-up
- [ ] **No owner-doc update required**
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**: closure audit verifies each I*Biz interface is actually implemented (not just declared); generated files reflect orm.xml changes
- [ ] `./mvnw compile -pl nop-metadata -am`
- [ ] `./mvnw test -pl nop-metadata -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

*None.*

## Non-Blocking Follow-ups

- *None.*

## Closure

Status Note: *to be completed after execution*
Completed: *to be completed after execution*

Closure Audit Evidence:

- Reviewer / Agent: *to be completed by independent subagent*
- Evidence: *to be completed*

Follow-up:

- no remaining plan-owned work after closure
