> Audit Status: closed
> Audit Type: multi-dimensional
> Mission: nop-metadata
> Remediation Plans: `ai-dev/plans/2026-07-21-1200-1-nop-metadata-p1-runtime-defects.md`, `ai-dev/plans/2026-07-21-1200-2-nop-metadata-code-quality-and-docs.md`

# Multi-Dimensional Audit Report: nop-metadata

## Basic Information

- **Module**: `nop-metadata` (8 sub-modules: api, core, codegen, dao, meta, service, web, app)
- **Audit Date**: 2026-07-20
- **Executed Dimensions**: 01 (dependency graph), 02 (module responsibility), 04 (ORM model), 05 (codegen pipeline), 07 (BizModel), 09 (error handling), 11 (XMeta alignment), 15 (type safety), 16 (test coverage), 18 (doc consistency)
- **Total Findings**: 88 (including passes/info items)

---

## Executive Summary

`nop-metadata` is a **high-quality, well-architected** reusable business module with 39 ORM entities, 40 BizModels, and full-generation pipeline coverage (100% entity→dao→meta→service→web). The module demonstrates strong engineering discipline: centralized ErrorCode management, comprehensive negative test coverage, Anti-Hollow testing practices, and full i18n localization.

**Top 5 actionable issues:**

| Rank | Issue | Severity | File |
|------|-------|----------|------|
| 1 | `ExternalTableStructureReader` inline ErrorCode with wrong prefix | **P1** | `service/.../datasource/ExternalTableStructureReader.java:46` |
| 2 | `NopMetaSearchBizModel` pseudo BizModel with no entity/xmeta | **P1** | `service/search/NopMetaSearchBizModel.java:26` |
| 3 | BizModel methods returning `Map<String,Object>` instead of @DataBean | **P1** | 7 BizModels, 13 methods |
| 4 | `INopMeta*Biz` interfaces misplaced in dao module | **P1** | `dao/.../biz/` (40 interfaces) |
| 5 | Javadoc subdomain declaration vs actual ErrorCode usage mismatch | **P1** | `service/NopMetadataErrors.java:22-24` |

---

## Dimension 01: Dependency Graph & Module Boundaries

**Rounds**: 1 | **Findings**: 7 issues + 2 passes

| ID | Severity | Finding | File |
|----|----------|---------|------|
| 01-01 | **P1** | dao→core cross-layer dependency (violates rule 2) | `nop-metadata-dao/pom.xml:20-24` |
| 01-02 | **P2** | service→search-lucene concrete implementation coupling | `nop-metadata-service/pom.xml:74-77` |
| 01-03 | P3 | api module is empty (no Java sources) | `nop-metadata-api/` |
| 01-04 | **P2** | dao module contains 40 Biz interfaces (responsibility mixing) | `dao/.../biz/INopMetaTableBiz.java` |
| 01-05 | P3 | web→meta dependency penetration (VFS, low risk) | `nop-metadata-web/pom.xml:16-20` |
| 01-06 | P3 | service→meta VFS dependency | `nop-metadata-service/pom.xml:45-49` |
| 01-07 | P3 | codegen lacks explicit nop-codegen dependency | `nop-metadata-codegen/pom.xml` |
| — | ✅ Pass | No circular dependencies | — |
| — | ✅ Pass | Framework isolation (Quarkus only in app) | — |

---

## Dimension 02: Module Responsibility & File Boundaries

**Rounds**: 1 | **Findings**: 10 issues

| ID | Severity | Finding | File |
|----|----------|---------|------|
| 02-01 | **P1** | 40 `INopMeta*Biz` interfaces misplaced in dao module | `dao/.../biz/` |
| 02-02 | **P1** | `AggregationContext.java` oversized (1854 lines) | `service/query/AggregationContext.java` |
| 02-03 | P2 | `TestNopMetaAggregationBizModel` god test (2592 lines) | `service/src/test/.../TestNopMetaAggregationBizModel.java` |
| 02-04 | P2 | Multiple BizModels >500 lines (max 931) | `service/entity/NopMetaTableBizModel.java` (931), `LineageEdge` (878), `Module` (631), `DataSource` (542) |
| 02-05 | P2 | `nop-metadata-api` empty module | `nop-metadata-api/` |
| 02-06 | P3 | `core` module misnamed (only DTOs + constants) | `nop-metadata-core/` |
| 02-07 | P3 | Hand-written Entity classes all empty (11 lines each) | `dao/entity/NopMeta*.java` (40 files) |
| 02-08 | P3 | Too many BizModels under `service/entity/` (39 files) | `service/entity/` |
| 02-09 | P3 | `NopMetadataErrors.java` oversized (1001 lines) | `service/NopMetadataErrors.java` |
| 02-10 | P3 | Codegen template path needs verification | `codegen/postcompile/gen-orm.xgen` |

---

## Dimension 04: ORM Model & Entity Design

**Rounds**: 1 | **Findings**: 12 evaluations + 4 info items

| ID | Severity | Finding | File |
|----|----------|---------|------|
| 04-01 | ✅ Pass | PK design consistent (VARCHAR(32)+seq across all) | `model/nop-metadata.orm.xml` |
| 04-02 | ✅ Pass | Full i18n displayName (Chinese + English) | `model/nop-metadata.orm.xml` |
| 04-03 | ✅ Pass | Dictionary-field association complete (24 dicts) | `model/nop-metadata.orm.xml` |
| 04-04 | P3 | `remark` domain precision=1000 vs column precision=200 mismatch | `model/nop-metadata.orm.xml:201` |
| 04-05 | **P1** | `schema` column name is SQL reserved word | `model/nop-metadata.orm.xml:1281` |
| 04-06 | **P1** | Strong parent-child relations missing `cascade-delete` tag | Multiple entities |
| 04-07 | P2 | Business columns appear after audit columns | `NopMetaTable`, `DataContract`, etc. |
| 04-08 | P3 | Index `IX_NOP_META_TABLE_LOOKUP` naming non-standard | `model/nop-metadata.orm.xml:1412` |
| 04-09 | ℹ️ Info | Redundant `stdDataType` on domain-using columns | Throughout |
| 04-10 | P3 | `NopMetaModelChangedEvent.changeSource` missing ext:dict | `model/nop-metadata.orm.xml:2812` |
| 04-11 | ℹ️ Info | QualityRule unique constraint design needs comment | — |
| 04-12 | P3 | `json-1000`/`json-4000` columns redundant precision | Throughout |
| 04-13 | ℹ️ Info | `NopMetaFilter` entity absent from model | — |
| 04-14 | ✅ Pass | delFlag removal decision well-documented | `model/nop-metadata.orm.xml:207-210` |
| 04-15 | ✅ Pass | Bidirectional relation declarations complete | Throughout |
| 04-16 | P2 | `NopMetaDataSource` missing `name` unique key | `model/nop-metadata.orm.xml:368` |

---

## Dimension 05: Codegen Pipeline Integrity

**Rounds**: 1 | **Findings**: 10 evaluations (mostly passes)

| ID | Finding | Result |
|----|---------|--------|
| 05-01 | Source model exists & valid (39 entities) | ✅ |
| 05-02 | codegen xgen scripts complete | ✅ |
| 05-03 | `app.orm.xml` + `_app.orm.xml` Delta pattern correct | ✅ |
| 05-04 | dao generation 100% coverage (39/39 entities) | ✅ |
| 05-05 | meta generation 100% coverage (39 xmeta pairs) | ✅ |
| 05-06 | service xbiz generation 100% coverage | ✅ |
| 05-07 | web page generation 100% coverage | ✅ |
| 05-08 | Pipeline fully closed (model→dao→meta→service→web) | ✅ |
| 05-09 | `gen-crud-api.xgen` disabled (design decision) | ℹ️ |
| 05-10 | Entity count discrepancy (docs say 36, actual 39) | ⚠️ |

---

## Dimension 07: BizModel Conformance

**Rounds**: 1 | **Findings**: 8 issues

| ID | Severity | Finding | File |
|----|----------|---------|------|
| 07-01 | **P1** | `NopMetaSearchBizModel` pseudo BizModel (no entity, no xmeta) | `service/search/NopMetaSearchBizModel.java:26` |
| 07-02 | **P1** | `Map<String, Object>` return type antipattern (13 methods) | 7 BizModels (LineageEdge, QualityRule, QualityCheckpoint, etc.) |
| 07-03 | **P1** | BizModel over-responsibility: 4 files >500 lines (max 931) | `TableBizModel`, `LineageEdgeBizModel`, `ModuleBizModel`, `DataSourceBizModel` |
| 07-04 | P2 | `dao().getEntityById()` instead of `requireEntity()` (11 call sites) | Multiple BizModels |
| 07-05 | P2 | `daoProvider()` intermediate API + double cast in `queryEntityData()` | `NopMetaTableBizModel.java:614` |
| 07-06 | ℹ️ Info | No Processor classes (executor/helper pattern instead) | — |
| 07-07 | P3 | `IMetaDataSourceConnectionProcessor` naming | `service/connection/` |
| 07-08 | ✅ Pass | All 39 entity BizModels correctly extend `CrudBizModel<T>` | ✅ |

---

## Dimension 09: Error Handling & ErrorCode

**Rounds**: 1 | **Findings**: 10 evaluations

| ID | Severity | Finding | File |
|----|----------|---------|------|
| 09-01 | ✅ Pass | Tier-1 (public API): ErrorCode pattern compliant | — |
| 09-02 | P3 | Tier-2 (module exception): `NopMetadataException` underused | `service/NopMetadataException.java` |
| 09-03 | **P1** | `ExternalTableStructureReader` inline ErrorCode with wrong prefix (`metadata.` vs `nop.err.metadata.`) | `service/.../ExternalTableStructureReader.java:46` |
| 09-04 | ✅ Pass | ErrorCode naming format `nop.err.metadata.*` compliant | — |
| 09-05 | **P1** | Javadoc subdomain declaration vs actual usage mismatch (15+ undeclared subdomains) | `service/NopMetadataErrors.java:22-24` |
| 09-06 | P3 | Variable name vs ErrorCode string inconsistency (SQL_VIEW variants) | `service/NopMetadataErrors.java` |
| 09-07 | ✅ Pass | All messages in English | — |
| 09-08 | ✅ Pass | Sufficient ARG_* parameter constants | — |
| 09-09 | ℹ️ Info | `toInlineErrorCode()` uses message as code (internal, acceptable) | — |
| 09-10 | ✅ Pass | Test exception patterns compliant | — |

---

## Dimension 11: XMeta ↔ BizModel Alignment

**Rounds**: 1 | **Findings**: 10 evaluations

| ID | Severity | Finding | File |
|----|----------|---------|------|
| 11-01 | P3 | `NopMetaSearchBizModel` has no xmeta (expected, non-entity model) | `service/search/NopMetaSearchBizModel.java` |
| 11-02 | **P1** | 13 methods returning `Map<String,Object>` lack xmeta type definitions | 7 BizModels |
| 11-03 | ✅ Pass | Entity-BizModel ↔ xmeta 100% coverage (39/39) | — |
| 11-04 | ✅ Pass | Retention xmeta inheritance pattern clean (empty props) | — |
| 11-05 | ✅ Pass | Dict references 100% aligned between ORM ↔ xmeta | — |
| 11-06 | ✅ Pass | ORM domain usage consistent in xmeta | — |
| 11-07 | ✅ Pass | xmeta field permissions reasonable and consistent | — |
| 11-08 | ✅ Pass | No `biz-domain` alignment issues | — |
| 11-09 | ℹ️ Info | `ReconciliationResultBizModel` operates on JSON details column | — |
| 11-10 | ✅ Pass | No @BizLoader usage (no alignment work needed) | — |

---

## Dimension 15: Type Safety & Generics

**Rounds**: 1 | **Findings**: 10 issues

| ID | Severity | Finding | File |
|----|----------|---------|------|
| 15-01 | **P1** | `INopMetaLineageEdgeBiz` returns `Map` despite existing `@DataBean` DTOs | `dao/biz/INopMetaLineageEdgeBiz.java:26-35` |
| 15-02 | **P1** | Double raw type cast pattern `(IOrmEntityDao)(IOrmEntityDao)` duplicated | `NopMetaTableBizModel.java:614`, `MetaJoinExecutor.java:421` |
| 15-03 | **P1** | `(List<Map<String,Object>>[]) new List<?>[1]` pattern triplicated | `AggregationContext.java`, `MetaTableQueryExecutor.java`, `MetaJoinExecutor.java` |
| 15-04 | P2 | `Map<String,Object>` propagation from executors forces casts at callers | Multiple locations |
| 15-05 | P2 | Query DTOs use `List<Map<String,Object>>` for rows (acceptable for dynamic schema) | `QueryTableDataResultDTO.java`, `AggregationResultDTO.java` |
| 15-06 | P2 | `testConnect()` returns `Map<String,Object>` → 4 instanceof checks | `NopMetaDataSourceBizModel.java:130-146` |
| 15-07 | P2 | 37 `@SuppressWarnings("unchecked")` in production code | Throughout |
| 15-08 | P2 | `Map<String,Object>` summary as unsafe accumulator | `NopMetaQualityCheckpointBizModel.java:235-280` |
| 15-09 | P3 | `NopMetaQualityCheckpointBizModel.save()` missing `@Name("data")` | `service/entity/NopMetaQualityCheckpointBizModel.java:182` |
| 15-10 | ℹ️ Info | `CrudBizModel.save()` uses `Map<String,Object>` (framework-level) | — |

---

## Dimension 16: Test Coverage & Quality

**Rounds**: 1 | **Findings**: 10 evaluations

| ID | Severity | Finding | File |
|----|----------|---------|------|
| 16-01 | ✅ Pass | Anti-Hollow pattern documented per test file | Throughout |
| 16-02 | ✅ Pass | Two-layer test architecture (integration + pure unit) | — |
| 16-03 | ✅ Pass | Excellent negative path coverage (700+ assertThrows) | 27 security-focused test files |
| 16-04 | P2 | `TestNopMetaAggregationBizModel` god class (2592 lines, 65 tests) | `TestNopMetaAggregationBizModel.java` |
| 16-05 | P2 | High helper method duplication across test files | 12+ files |
| 16-06 | P3 | `query/` subdirectory tests low assertion density | `TestEntityAggregationProcessor.java`, etc. |
| 16-07 | ✅ Pass | No mock framework dependency (reduces false-positives) | — |
| 16-08 | ✅ Pass | Excellent audit traceability (Javadoc with baseline references) | Throughout |
| 16-09 | ✅ Pass | `TestNopMetaBizInterfaceCompleteness` — contract verification test | `test/.../TestNopMetaBizInterfaceCompleteness.java` |
| 16-10 | ✅ Pass | Strong protection for business logic, security, constraint violation | — |

---

## Dimension 18: Doc-Code Consistency

**Rounds**: 1 | **Findings**: 11 evaluations

| ID | Severity | Finding | File |
|----|----------|---------|------|
| 18-01 | ✅ Pass | 5 responsibility areas: Catalog, Semantic, Lineage, Quality, Reconciliation | `module-groups.md:63-69` |
| 18-02 | ✅ Pass | Referenced class names exist in code | `module-groups.md:67-68` |
| 18-03 | ✅ Pass | `docs-for-ai/03-modules/nop-metadata.md` exists | — |
| 18-04 | ✅ Pass | `ai-dev/design/nop-metadata/` exists (18 design docs) | — |
| 18-05 | ✅ Pass | 22 core entities documented match code | `nop-metadata.md:19-41` |
| 18-06 | ✅ Pass | Source anchors META-001..005 all accurate | — |
| 18-07 | ✅ Pass | 9 I*Biz interfaces documented match code | `nop-metadata.md:111-116` |
| 18-08 | **P1** | DTO location incorrectly documented as `nop-metadata-dao` (actually in `core/dto/`) | `nop-metadata.md:133` |
| 18-09 | P2 | Module structure table missing `codegen` and `api` modules | `nop-metadata.md:130-137` |
| 18-10 | P2 | `core` module description incomplete (missing 29 DTO classes) | `nop-metadata.md:132` |
| 18-11 | ✅ Pass | NopMetadataErrors and NopMetadataException documented correctly | `nop-metadata.md:145,152-153` |

---

## Execution Statistics

| Dimension | Rounds | Findings | Issues | Pass/Info |
|-----------|--------|----------|--------|-----------|
| 01 — Dependency Graph | 1 | 9 | 7 | 2 |
| 02 — Module Responsibility | 1 | 10 | 10 | 0 |
| 04 — ORM Model | 1 | 16 | 8 | 8 |
| 05 — Codegen Pipeline | 1 | 10 | 1 | 9 |
| 07 — BizModel Conformance | 1 | 8 | 6 | 2 |
| 09 — Error Handling | 1 | 10 | 4 | 6 |
| 11 — XMeta Alignment | 1 | 10 | 2 | 8 |
| 15 — Type Safety | 1 | 10 | 9 | 1 |
| 16 — Test Coverage | 1 | 10 | 2 | 8 |
| 18 — Doc Consistency | 1 | 11 | 3 | 8 |
| **Total** | **10** | **104** | **52** | **52** |

---

## Severity Distribution (Across All Dimensions)

| Severity | Count | Main Categories |
|----------|-------|-----------------|
| **P1** | 11 | Cross-layer dependencies, pseudo BizModel, Map antipattern, inline ErrorCode, doc drift, type safety |
| **P2** | 18 | Oversized files, missing cascade-delete, module documentation gaps, @SuppressWarnings density, SQL reserved word |
| **P3** | 23 | Index naming, empty api module, missing dependencies, column ordering, dict gaps |
| ℹ️ Info | 10 | Design notes, redundant declarations, framework-level decisions |
| ✅ Pass | 42 | — |

---

## Priority Fix Recommendations

**P0 (immediate):** None identified — no active security breaches or data corruption risks.

**P1 (high — next sprint):**
1. `ExternalTableStructureReader.java:46` — Fix inline ErrorCode prefix from `metadata.` to `nop.err.metadata.`
2. `NopMetadataErrors.java:22-24` — Sync Javadoc subdomain declaration with actual usage
3. `NopMetaSearchBizModel.java:26` — Add xmeta or merge methods into entity-backed BizModel
4. Convert 13 `Map<String,Object>` return methods to `@DataBean` DTOs across 7 BizModels
5. Generate `cascade-delete` tag on strong parent-child ORM relations
6. Rename `schema` column to avoid SQL reserved word
7. Fix DTO location documentation in `nop-metadata.md:133`

**P2 (medium — next quarter):**
1. Extract Processors from oversized BizModels (TableBizModel 931 lines, LineageEdgeBizModel 878 lines)
2. Split `AggregationContext.java` (1854 lines) by responsibility
3. Extract shared `newArrayHolder()` pattern into utility class
4. Add `name` UK on `NopMetaDataSource`
5. Update module structure table in `nop-metadata.md`

**P3 (low — backlog):**
1. Move `INopMeta*Biz` interfaces from dao to api module (major refactor, check Nop platform convention)
2. Remove `nop-search-lucene` from service pom.xml
3. Standardize index naming (`IX_NOP_META_TABLE_LOOKUP`)
4. Clean up redundant `precision` declarations on json-domain columns

---

## Audit Blind Spots

- **Runtime behavior**: Static code audit only; dynamic SQL generation, GraphQL schema derivation, and Delta merge behavior were not executed
- **Security**: No penetration testing or runtime auth bypass attempts
- **Performance**: No profiling or load testing
- **Cross-module contract drift**: `nop-metadata`'s consumption by other modules (e.g., `nop-dyn`, `nop-report`) was not verified
- **XLang/XDSL**: XDSL schema validation was not executed via `mvn validate`
- **Dimension 21 (test effectiveness)**: Full antipattern analysis per `unit-test-antipatterns.md` was deferred

---

> Audit Status: closed
> Audit Type: multi-dimensional
> Mission: nop-metadata
> Remediation Plans: `ai-dev/plans/2026-07-21-1200-1-nop-metadata-p1-runtime-defects.md`, `ai-dev/plans/2026-07-21-1200-2-nop-metadata-code-quality-and-docs.md`

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
