> Audit Status: closed
> Audit Type: multi-dimensional
> Mission: nop-metadata

# Multi-Dimensional Audit: nop-metadata

**Audit Date**: 2026-07-20  
**Target**: `nop-metadata` module (api, core, codegen, dao, meta, service, web, app)  
**Executed Dimensions**: 01 (Dependency Graph), 03 (API Surface), 04 (ORM Model), 07 (BizModel Conformance), 09 (Error Handling), 16 (Test Coverage)  
**Scope**: Code, config, tests, and public contracts (exports, API surface)

---

## 1. Execution Statistics

| Dimension | Round 1 Findings | Severity Distribution |
|-----------|-----------------|----------------------|
| 01 — Dependency Graph | 3 | P3×2, P2×1 |
| 03 — API Surface | 10 | P0×1, P1×3, P2×4, P3×2 |
| 04 — ORM Model & Entity | 4 | P2×2, P3×2 |
| 07 — BizModel Conformance | 4 | P2×2, P3×2 |
| 09 — Error Handling | 11 | P1×1, P2×4, P3×6 |
| 16 — Test Coverage | 7 | P2×1, P3×6 |
| **Total** | **39** | **P0: 1, P1: 4, P2: 14, P3: 20** |

---

## 2. Findings by Severity

### P0 — Critical (1 finding)

| ID | Dimension | File | Summary |
|----|-----------|------|---------|
| 03-F01 | API Surface | `nop-metadata-dao/.../INopMetaDataProductBiz.java` | Interface contract totally missing — 3 `@BizQuery`/`@BizMutation` methods (`linkAsset`, `unlinkAsset`, `getLinkedAssets`) not declared in `INopMetaDataProductBiz` |

### P1 — High (4 findings)

| ID | Dimension | File | Summary |
|----|-----------|------|---------|
| 03-F02 | API Surface | `nop-metadata-dao/.../INopMetaQualityResultBiz.java` | `approve`/`reject` methods missing from interface |
| 03-F03 | API Surface | `nop-metadata-dao/.../INopMetaDataContractBiz.java` | `approve`/`reject` methods missing from interface |
| 03-F09 | API Surface | `nop-metadata-meta/.../NopMetaTable/NopMetaTable.xmeta` | Core entity retention layer completely empty — no field permission overrides |
| 09-11 | Error Handling | `nop-metadata-service/.../NopMetadataConstants.java` | Empty interface skeleton — no constants defined despite being listed as key file |

### P2 — Medium (14 findings)

| ID | Dimension | File | Summary |
|----|-----------|------|---------|
| 01-02 | Dependency | `nop-metadata-service/pom.xml` | Unused compile dependency on `nop-sys-dao` — zero references in code or resources |
| 03-F04 | API Surface | Multiple BizModels | 15 action methods return `Map<String, Object>` instead of `@DataBean` DTO |
| 03-F05 | API Surface | `INopMetaDataContractBiz.java` | 3 `@Deprecated` methods in BizModel not marked deprecated in interface |
| 03-F06 | API Surface | `NopMetaDataSource.xmeta` | `connectionConfig` has `queryable=true` in generated file, retention layer only overrides `published`/`insertable`/`updatable` |
| 03-F10 | API Surface | `NopMetaLineageEdge.xmeta` | Retention layer empty — CRUD bypasses business API validation |
| 04-P2-01 | ORM Model | `nop-metadata.orm.xml:NopMetaLineageEdge` | Missing unique constraint — duplicate lineage edges possible |
| 04-P2-02 | ORM Model | `nop-metadata.orm.xml:NopMetaTable` | Two overlapping unique keys with conflicting semantics |
| 07-F1 | BizModel | Multiple BizModels | 21 methods use `Map<String, Object>` return type — "plan 307" noted in comments |
| 07-F2 | BizModel | `NopMetaQualityRuleBizModel.java` | `judgeByRuleId` public method missing from `INopMetaQualityRuleBiz`, missing `@BizQuery`/`@BizMutation`/`@BizAction` annotation |
| 09-01 | Error Handling | `AggregationContext.java` (18 occurrences) | `.cause(e)` chained instead of two-arg constructor pattern |
| 09-02 | Error Handling | `NopMetaModuleBizModel.java` | `LOG.warn("...{}", e.toString())` loses stack trace |
| 09-03 | Error Handling | `AggregationContext.java` | `tryLoadEntityField` swallows all exceptions silently — returns null with no log |
| 09-05 | Error Handling | `MetaContractChecker.java` | Hardcoded Chinese business messages in Java source |
| 16-01 | Test Coverage | `NopMetaQualityResultBizModel.java` | `approve`/`reject` methods have zero test coverage |

### P3 — Low (20 findings)

See detailed summaries below for full list of P3 items across all dimensions.

---

## 3. Dimension 01 — Dependency Graph

**Summary**: `nop-metadata` follows standard Nop module layering. No circular dependencies. Two scope issues found.

| ID | Severity | Description |
|----|----------|-------------|
| 01-01 | P3 | `nop-metadata-web` declares redundant compile dependency on `nop-metadata-meta` — already available via `web → service → meta` transitive chain |
| 01-02 | P2 | `nop-metadata-service` has compile dependency on `nop-sys-dao` with zero code/resource references |
| 01-03 | P3 | `nop-metadata-service` depends on `nop-wf-meta` at compile scope — no Java types consumed from it; should be runtime or test |

---

## 4. Dimension 03 — API Surface

**Summary**: 39 BizModels, 44 custom `@BizQuery`/`@BizMutation` methods. 3 interfaces have contract gaps totaling 7 undeclared methods. Map-based return types pervasive.

| ID | Severity | Description |
|----|----------|-------------|
| F-01 | **P0** | `INopMetaDataProductBiz` — 3 methods (`linkAsset`, `unlinkAsset`, `getLinkedAssets`) not declared |
| F-02 | P1 | `INopMetaQualityResultBiz` — `approve`/`reject` not declared |
| F-03 | P1 | `INopMetaDataContractBiz` — `approve`/`reject` not declared |
| F-04 | P2 | 15 action methods return `Map<String, Object>` across 7 BizModels |
| F-05 | P2 | 3 `@Deprecated` methods in BizModel not marked in interface |
| F-06 | P2 | `connectionConfig` `queryable=true` not overridden in retention xmeta |
| F-07 | P3 | Standard `save(Map)` pattern — framework convention, informational |
| F-08 | P3 | `NopMetaTableFilterBizModel.save` — informational |
| F-09 | P1 | `NopMetaTable.xmeta` retention layer completely empty |
| F-10 | P2 | `NopMetaLineageEdge.xmeta` retention layer empty — CRUD bypasses API validation |

---

## 5. Dimension 04 — ORM Model & Entity Design

**Summary**: 45 entities, 3453-line hand-written ORM model. High overall quality — UUID PKs, consistent i18n, complete bidirectional relations.

| ID | Severity | Description |
|----|----------|-------------|
| P2-01 | P2 | `NopMetaLineageEdge` missing business unique key — duplicate lineage edges possible |
| P2-02 | P2 | `NopMetaTable` has two overlapping unique keys (`uk_meta_table_module_schema` vs `UK_NOP_META_TABLE_MODULE_NAME`) with conflicting nullable-column semantics |
| P3-01 | P3 | `uk_meta_table_module_schema` naming inconsistent (snake_case vs UPPER_SNAKE_CASE), missing `i18n-en:displayName` |
| P3-02 | P3 | `NopMetaCatalog` missing `(metaTableId, collectedAt)` unique constraint as hinted by entity comment |

---

## 6. Dimension 07 — BizModel Conformance

**Summary**: All 39 BizModels correctly inherit `CrudBizModel<T>`, call `setEntityName()`, implement I*Biz interfaces. Core structural conformance is 100%.

| ID | Severity | Description |
|----|----------|-------------|
| F1 | P2 | 21 custom methods across 7 BizModels return `Map<String, Object>` — comments reference "plan 307" |
| F2 | P2 | `judgeByRuleId` public method missing from interface, missing `@BizQuery`/`@BizMutation`, missing `IServiceContext` |
| F3 | P3 | I*Biz interfaces in DAO module (structural — standard Nop pattern, informational) |
| F4 | P3 | `dao().getEntityById()` in save/delete overrides bypasses CrudBizModel preprocessing pipeline |

---

## 7. Dimension 09 — Error Handling

**Summary**: Strong foundation — two-tier strategy implemented, no bare `RuntimeException`, ErrorCodes centralized with `nop.err.metadata.*` prefix, 82 ARG_* constants. Issues concentrated in logging practices and edge consistency.

| ID | Severity | Description |
|----|----------|-------------|
| 09-01 | P2 | 18 `.cause(e)` chain calls instead of `new NopException(code, cause)` |
| 09-02 | P2 | `LOG.warn("...{}", e.toString())` loses stack trace in `NopMetaModuleBizModel` |
| 09-03 | P2 | `tryLoadEntityField` swallows all exceptions silently (return null, no log) |
| 09-04 | P3 | `safeProductName` swallows SQLException silently |
| 09-05 | P2 | Hardcoded Chinese business messages in `MetaContractChecker.java` |
| 09-06 | P3 | Redundant `.param("error", messageOf(e))` alongside `.cause(e)` |
| 09-07 | P3 | `NopMetadataException.toInlineErrorCode` uses message string as ErrorCode key |
| 09-08 | P3 | ErrorCode descriptions in English vs documented Chinese requirement (doc ambiguity) |
| 09-09 | P3 | ~20% of `.param()` calls use magic strings instead of ARG_* constants |
| 09-10 | P3 | ~10 param names not defined as ARG_* constants |
| 09-11 | P1 | `NopMetadataConstants.java` is an empty interface skeleton |

---

## 8. Dimension 16 — Test Coverage

**Summary**: 60+ test classes, strong integration coverage on critical paths (lineage, quality rules, scoring, data source connections, profiling, aggregation). Weaknesses in approval workflow testing and response validation patterns.

| ID | Severity | Description |
|----|----------|-------------|
| 16-01 | P2 | `NopMetaQualityResultBizModel.approve`/`reject` — zero test coverage |
| 16-02 | P3 | `testProfileColumnFailureIsolation` uses weak OR-assertion |
| 16-03 | P3 | 3+ test files duplicate identical helper methods (`saveDataSource`, `saveRule`, etc.) |
| 16-04 | P3 | Widespread `String.valueOf(resp.getData()).contains(...)` pattern — coupled to toString format |
| 16-05 | P3 | `testExtractMeasureLineageBfsNotPolluted` bypasses GraphQL entry, tests internal API |
| 16-06 | P3 | `generateManifest` missing `ERR_MODULE_FULL_MODEL_NOT_FOUND` failure path test |
| 16-07 | P3 | `NopMetaEntity` save/delete event publication not independently tested |

---

## 9. Overall Assessment

**Strengths**:
- Clean Nop module layering with no circular dependencies
- Excellent ORM model quality — consistent UUID PKs, full bilingual i18n, complete bidirectional relations
- 100% structural BizModel conformance (CrudBizModel, setEntityName, I*Biz implementation)
- Robust error handling foundation — no bare RuntimeException, centralized ErrorCode with 82 ARG_* constants
- Rich test suite with strong integration coverage on core business paths (lineage, quality, aggregation, profiling)

**Critical Issues** (must fix):
1. **03-F01** (P0): `INopMetaDataProductBiz` missing 3 method declarations — cross-module callers cannot compile
2. **03-F02/F03** (P1): Two more interfaces missing approval method declarations
3. **03-F09** (P1): `NopMetaTable.xmeta` retention layer empty — core entity lacks field permission control

**Medium Priority** (plan within 1-2 sprints):
1. Migrate 15+ Map-returning action methods to `@DataBean` DTOs (tech debt acknowledged as "plan 307")
2. Add unique constraints on `NopMetaLineageEdge` (P2-01) and resolve overlapping keys on `NopMetaTable` (P2-02)
3. Add `queryable="false"` on `connectionConfig` xmeta to prevent filter-based information leakage
4. Fix `tryLoadEntityField` silent swallowing and other logging deficits
5. Add tests for `NopMetaQualityResultBizModel.approve`/`reject`

**Clean Architecture**:
- No circular dependencies found
- All but one I*Biz interface gap are documented in code comments
- `NopMetadataException` properly provides both `(String)` and `(ErrorCode)` constructors

---

## 10. Audit Blind Spots

- No checkstyle baseline was available (dimension 17 skipped)
- No full `mvnw test` was executed — test effectiveness evaluated by reading test code only
- Web-level and UI-level contract verification not included
- Delta customization (dimension 06) not audited
- Cross-module runtime dependency verification not performed
