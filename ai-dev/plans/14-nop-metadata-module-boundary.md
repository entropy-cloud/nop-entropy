# 14 nop-metadata Module Boundary & Convention Alignment

> Plan Status: active
> Execution Order: 2
> Last Reviewed: 2026-07-23
> Source: `ai-dev/audits/2026-07-23-0714-multi-audit-nop-metadata/01-dependency-graph.md`, `04-orm-model.md`, `07-bizmodel-conformance.md`; `ai-dev/audits/2026-07-23-0714-open-audit-nop-metadata.md`
> Related: `13-nop-metadata-critical-fixes.md`, `15-nop-metadata-test-and-code-quality.md`

## Purpose

Align nop-metadata's module boundaries, dependency rules, and naming conventions with the declared Nop platform patterns. This plan bundles all findings about what goes where — from module-level POM dependencies down to ORM column naming.

## Current Baseline

- **01-02 (P2)**: `nop-metadata-service` does not depend on `nop-metadata-api` (Rule #4 violation).
- **01-03 (P3)**: `nop-metadata-core` does not depend on `nop-metadata-api` (Rule #3 violation).
- **01-04 (P3)**: `nop-metadata-service` depends on `nop-metadata-meta`, which is not listed in Rule #4.
- **01-05 (P3)**: `nop-metadata-api` module is orphaned — no module depends on it, zero source files.
- **AR-27 (P2)**: `nop-metadata-api` directory contains only `pom.xml` + `target/`. 0 Java files.
- **AR-34 (P2)**: 39 `INopMeta*Biz` interfaces reside in `nop-metadata-dao/biz/` instead of `nop-metadata-api`.
- **AR-35 / 04-02 (P2)**: Cross-module dict `wf/approve-status` referenced from `nop-metadata.orm.xml:2500,3276` — creates hard runtime coupling to `nop-wf-meta`.
- **04-03 (P2)**: FK column `entityTableId` on `NopMetaDataContract` — naming differs from all other entities (`metaTableId`).
- **04-01 (P2)**: `NopMetaTableJoin` dual FK system (entity-level + table-level) lacks declarative mutual-exclusion constraint.
- **07-03 (P3)**: `NopMetaTagLabelBizModel` uses `BeanContainer.getBeanByType()` service locator instead of `@Inject`.
- **07-04 (P3)**: `AutoClassificationService` and `LineageTagPropagationService` use prohibited `*Service` suffix.
- **07-05 (P3)**: `NopMetaModuleBizModel` is 586 lines with 17 methods spanning ORM parsing, manifest building, event publishing.
- **07-06 (P3)**: `NopMetaDataContractBizModel` uses reflective `bizObjectManager().invoke("submitForApproval", ...)` — fails if `approval-support.xbiz` not deployed.
- **07-07 (P3)**: 7 BizModels (NopMetaTableBizModel, NopMetaEntityBizModel, NopMetaEntityFieldBizModel, NopMetaModuleBizModel, NopMetaTagBizModel, NopMetaClassificationBizModel, NopMetaGlossaryTermBizModel) load "before" snapshots via `dao().getEntityById()` instead of `requireEntity()`.
- **07-08 (P3)**: `NopMetaReconciliationConfigBizModel` is the only BizModel using constructor injection — all 39 others use no-arg + `@Inject` field pattern.
- **04-04 (P3)**: Mixed UK naming convention on `NopMetaDataSource` — one `UK_...` uppercase, one `uk_...` lowercase.
- **04-05 (P3)**: `NopMetaModelChangedEvent` has dual audit field sets (entity-level + event-specific) with no semantic documentation.
- **04-06 (P3)**: `NopMetaReconciliationEntity` is the only entity with zero `<relations>` — no FK to any parent.
- **04-07 (P4)**: Duplicate `ext:icon="database"` on root `<orm>` and `NopMetaReconciliationEntity`.

## Goals

- Make `nop-metadata-api` a real module: either populate with Biz interfaces or remove it.
- Eliminate all module-boundary Rule violations (Rule #2, #3, #4).
- Eliminate hard cross-module runtime coupling to `nop-wf` dicts.
- Unify naming conventions: FK column names, UK naming, class suffixes.
- Enforce BizModel patterns consistently (injection, data loading, service locator).
- Document unresolved design decisions in entity/ORM model.

## Non-Goals

- Structural P1 fix (01-01: dao→core dependency) — covered by Plan 13.
- Security fixes (07-02, 11-01, 11-04, 11-06, AR-31) — covered by Plan 13.
- Error handling conventions (09-*) and test quality (16-*) — covered by Plan 15.

## Scope

### In Scope

- 01-02, 01-03, 01-04, 01-05/dead-api: dependency rule alignment
- AR-27 + AR-34: populate or remove `nop-metadata-api` module; move 39 Biz interfaces
- AR-35 / 04-02: localize or document `wf/approve-status` dict dependency
- 04-03: rename `entityTableId` → `metaTableId` in ORM model
- 04-01: add BizModel-level validation for NopMetaTableJoin dual FK mutual exclusion
- 07-03: replace `BeanContainer.getBeanByType()` with `@Inject`
- 07-04: rename `*Service` classes to `*Processor`
- 07-05: extract processors from `NopMetaModuleBizModel`
- 07-06: document xbiz dependency or add fallback
- 07-07: convert `dao().getEntityById()` snapshot loads to `requireEntity()` in 7 BizModels (NopMetaTableBizModel, NopMetaEntityBizModel, NopMetaEntityFieldBizModel, NopMetaModuleBizModel, NopMetaTagBizModel, NopMetaClassificationBizModel, NopMetaGlossaryTermBizModel)
- 07-08: refactor constructor injection to standard no-arg + `@Inject` setter
- 04-04: rename `uk_meta_datasource_name` to `UK_NOP_META_DATA_SOURCE_NAME`
- 04-05: add inline comments clarifying dual audit field semantics
- 04-06: add `metaTableId` FK to `NopMetaReconciliationEntity` or document design decision
- 04-07: change `NopMetaReconciliationEntity` icon to distinct value

### Out Of Scope

- Critical structural/security fixes — covered by Plan 13.
- Error code naming, exception constructors, bare NopException usage — covered by Plan 15.
- Test enhancements — covered by Plan 15.
- CRUD API codegen (05-08) — covered by Plan 15.

## Execution Plan

### Phase 1 - Fix Module Dependency Rules & API Module (01-02, 01-03, 01-04, 01-05, AR-27, AR-34)

Status: planned
Targets: `nop-metadata/pom.xml`, `nop-metadata-api/`, `nop-metadata-core/pom.xml`, `nop-metadata-service/pom.xml`, `nop-metadata-dao/src/main/java/io/nop/metadata/biz/`

- Item Types: `Decision | Fix`

- [ ] Decide: populate `nop-metadata-api` with the 39 `INopMeta*Biz` interfaces, or remove the empty api module entirely.
- [ ] If populate: move all 39 `INopMeta*Biz.java` files from `nop-metadata-dao/biz/` to `nop-metadata-api/src/main/java/io/nop/metadata/api/biz/`.
- [ ] If populate: update imports and package declarations in moved files.
- [ ] If populate: update `nop-metadata-core/pom.xml` to add `nop-metadata-api` dependency.
- [ ] If populate: update `nop-metadata-service/pom.xml` to add `nop-metadata-api` dependency.
- [ ] If populate: update `nop-metadata-dao/pom.xml` to add `nop-metadata-api` dependency (replacing core if removed by Plan 13).
- [ ] If remove: delete `nop-metadata-api/` directory; remove from parent POM module list.
- [ ] Update Rule docs in `docs-for-ai/` if the dependency rule list changes (add `meta` to Rule #4).

Exit Criteria:

- [ ] `nop-metadata-api` is either populated with 39 Biz interfaces or removed entirely — no dead module.
- [ ] Core depends on api; service depends on api + core + dao + meta; dao depends on api.
- [ ] `./mvnw compile -pl nop-metadata-service -am` passes.
- [ ] `./mvnw test -pl nop-metadata-service -am` passes.
- [ ] **接线验证**: If populated, verify a downstream module can import an `INopMeta*Biz` interface from `nop-metadata-api` without pulling dao dependencies.
- [ ] 若该 Phase 改变 live baseline：相关 `docs-for-ai/` 依赖规则文档已更新。目标文件：`docs-for-ai/02-core-guides/service-layer.md` 或相应模块文档。
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Eliminate Cross-Module Dict Coupling & FK Name Inconsistency (AR-35/04-02, 04-03)

Status: planned
Targets: `nop-metadata/model/nop-metadata.orm.xml`

- Item Types: `Fix`

- [ ] Define `approve-status` dict locally in `nop-metadata.orm.xml` (3-option dict: DRAFT, PENDING, APPROVED, REJECTED) or document mandatory `nop-wf` dependency.
- [ ] Rename FK column `entityTableId` to `metaTableId` in `NopMetaDataContract` entity definition in source ORM model. Note: `entityTableId` appears in column definition (line 2455), relation mappings (lines 1402, 2511, 2517) — update all references.
- [ ] Run `./mvnw compile -pl nop-metadata-service -am` to regenerate. Do NOT hand-edit generated `_*.java`/`_*.xmeta` — verify they are correct after regeneration.

Exit Criteria:

- [ ] Dict `approve-status` resolves locally without requiring `nop-wf-meta` — confirmed by ORM model review.
- [ ] `NopMetaDataContract` FK column is named `metaTableId` — confirmed by ORM model review.
- [ ] `./mvnw compile -pl nop-metadata-service -am` passes.
- [ ] **接线验证**: Regenerated ORM model does not break entity loading — confirmed by test pass.
- [ ] `No owner-doc update required`.
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - BizModel Convention Alignment (07-03, 07-04, 07-05, 07-06, 07-07, 07-08)

Status: planned
Targets: `nop-metadata/nop-metadata-service/`

- Item Types: `Fix | Decision | Refactor`

- [ ] Replace `BeanContainer.getBeanByType(LineageTagPropagationService.class)` with `@Inject protected LineageTagPropagationProcessor lineageTagPropagationProcessor;` in `NopMetaTagLabelBizModel`.
- [ ] Rename `AutoClassificationService` → `AutoClassificationProcessor`. Update `TestMetadataPropagationUnit.java` imports and constructor calls.
- [ ] Rename `LineageTagPropagationService` → `LineageTagPropagationProcessor`. Update `TestMetadataPropagationUnit.java` imports and constructor calls.
- [ ] **Decision & Refactor**: Decide on processor extraction from `NopMetaModuleBizModel` (586 lines). Option A: extract `OrmModelImportProcessor` (methods: `importOrmModel`, `resolveEntityXmlDsk`) and `ManifestGenerationProcessor` (methods: `generateManifest`, `buildGlobalClassNameToModuleId`). Option B: do not extract, add decompression comments and leave for later. If extracted, add focused unit tests for each new Processor.
- [ ] Add class-level javadoc to `NopMetaDataContractBizModel` documenting reflective xbiz dependency or add fallback.
- [ ] Convert `dao().getEntityById()` snapshot loads to `requireEntity(id, "save", context)` in the following 7 BizModels: NopMetaTableBizModel, NopMetaEntityBizModel, NopMetaEntityFieldBizModel, NopMetaModuleBizModel, NopMetaTagBizModel, NopMetaClassificationBizModel, NopMetaGlossaryTermBizModel.
- [ ] Refactor `NopMetaReconciliationConfigBizModel` constructor injection to no-arg + `@Inject` setter.
- [ ] Update `beans.xml` if bean ID references changed.
- [ ] Add focused tests for renamed `*Processor` classes verifying basic instantiation and dispatch.

Exit Criteria:

- [ ] No `BeanContainer.getBeanByType()` calls remain in BizModel code — confirmed by grep.
- [ ] No `*Service` class suffix remains — confirmed by grep on `service/entity/`.
- [ ] `TestMetadataPropagationUnit.java` compiles and passes with renamed class references.
- [ ] `NopMetaModuleBizModel` is under 300 lines or extraction decision documented as deferred with `UnsupportedOperationException` for any stub methods.
- [ ] xbiz dependency is documented in javadoc or has a fallback.
- [ ] 7 snapshot-loading sites in the listed BizModels use `requireEntity()` — confirmed by code review.
- [ ] Only one injection pattern across all 40 BizModels.
- [ ] **接线验证**: `NopMetaTagLabelBizModel` injection is actually wired via beans.xml — confirmed.
- [ ] `./mvnw compile -pl nop-metadata-service -am` passes.
- [ ] `./mvnw test -pl nop-metadata-service -am` passes (including updated/new tests for renamed classes).
- [ ] `No owner-doc update required`.
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - ORM Model Naming & Documentation (04-01, 04-04, 04-05, 04-06, 04-07)

Status: planned
Targets: `nop-metadata/model/nop-metadata.orm.xml`

- Item Types: `Fix | Documentation`

- [ ] Add BizModel `save()` override enforcing exactly-one-pair semantics on `NopMetaTableJoin` (entity-level XOR table-level FKs populated). Use `CrudBizModel.save()` override pattern consistent with existing `validateJoinSide()` in `NopMetaTableJoinBizModel`.
- [ ] Rename `uk_meta_datasource_name` → `UK_NOP_META_DATA_SOURCE_NAME`.
- [ ] Add inline comments to `NopMetaModelChangedEvent` explaining dual audit field semantics.
- [ ] Add `metaTableId` FK to `NopMetaReconciliationEntity` relations or document why zero relations is intentional.
- [ ] Change `NopMetaReconciliationEntity` icon from `database` to distinct value (e.g. `list-checks`).

Exit Criteria:

- [ ] `NopMetaTableJoin` has BizModel-level mutual exclusion enforcement — confirmed by code review.
- [ ] All UKs in ORM model follow `UK_{TABLE}_{COLS}` uppercase pattern.
- [ ] `NopMetaModelChangedEvent` has inline doc explaining audit field semantics.
- [ ] `NopMetaReconciliationEntity` either has a FK or has documented rationale.
- [ ] No duplicate `ext:icon="database"` — confirmed by ORM review.
- [ ] **无静默跳过**: All changes are actual implementations or documented decisions — no empty stubs.
- [ ] `./mvnw compile -pl nop-metadata-dao -am` passes.
- [ ] `No owner-doc update required`.
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 关闭条件：本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选后，才能将 Plan Status 改为 `completed`。

- [ ] Module dependency rules (api/core/dao/service) are self-consistent and match updated docs.
- [ ] `nop-metadata-api` is either populated with 39 Biz interfaces or removed.
- [ ] Cross-module dict `wf/approve-status` no longer creates hard runtime coupling.
 - [ ] BizModel conventions (injection, naming, constructor, data loading) are consistent across all 40 models.
- [ ] ORM naming (FK columns, UKs, icons) follows consistent conventions.
- [ ] `./mvnw compile -pl nop-metadata-service -am` passes.
- [ ] `./mvnw test -pl nop-metadata-service -am` passes.
- [ ] Updated `docs-for-ai/` if dependency rules changed.
- [ ] 独立子 agent closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**: 接线验证 — moved Biz interfaces are actually importable from api module; refactored BizModel injection actually resolves at runtime.
- [ ] **No Silent No-Op**: No empty method bodies left, renamed classes have proper implementation.

## Deferred But Adjudicated

None.

## Non-Blocking Follow-ups

- 04-06 (NopMetaReconciliationEntity zero relations): If intentional design choice, document rationale in ORM model as minimum acceptable fix.

## Closure

Status Note: *(to be filled on completion)*
Completed: *(to be filled)*

### Review History

- **2026-07-23 adversarial review (ses_073cd78b7ffeSfhp2hE76EHbwq)**: 1 Blocker (test update omission), 6 Major items (extraction scope, generated files, conditional follow-ups, 7-BizModel list, mutual exclusion mechanism, new test requirements). All resolved in subsequent edit pass.
- **2026-07-23 re-review (ses_073c76186ffedahapsxfNtvsC5)**: All issues resolved. Consensus reached. Plan Status → active.

Closure Audit Evidence:

- Reviewer / Agent: *(to be filled on completion)*
- Evidence: *(to be filled on completion)*

Follow-up:

- No remaining plan-owned work.
