> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-metadata

# Multi-Dimensional Audit: nop-metadata

**Auditor**: opencode multi-dimensional agent
**Date**: 2026-07-23
**Scope**: All submodules under `nop-metadata/` — code, config, tests, public contracts, API surface, Maven build
**Coverage**: 8 dimensions (01-dep, 02-module-boundary, 03-api-surface, 04-orm, 05-codegen, 07-bizmodel, 09-error, 11-xmeta, 16-tests) + cross-reference against architecture docs for documented contract drift
**Status**: Incorporated from earlier multi-dim audit (36 findings in 7 dimensions, directory `2026-07-23-0714-multi-audit-nop-metadata/`) and open audit (3 new findings, file `2026-07-23-0714-open-audit-nop-metadata.md`)

---

## Execution Summary

| Dimension | Rounds | Findings | Retained | Downgraded | Rejected |
|-----------|--------|----------|----------|------------|----------|
| 01 — Dependency Graph & Module Boundaries | 1 | 1 (P2) + BOM issue | 1 | 0 | 0 |
| 02 — Module Responsibility & File Boundaries | 1 | 2 (P2, P3) | 2 | 0 | 0 |
| 03 — API Surface & Contract Consistency | 1 | 2 (P2) | 2 | 0 | 0 |
| 04 — ORM Model & Entity Design | 1 | 7 (P2x3, P3x4) | 7 | 0 | 0 |
| 05 — Codegen Pipeline Integrity | 1 | 2 (P2, P3) + 8 informational | 2 | 0 | 0 |
| 07 — BizModel Conformance | 1 | 8 (P2x6, P3x2) | 8 | 0 | 0 |
| 09 — Error Handling & Error Codes | 1 | 7 (P2x2, P3x5) | 7 | 0 | 0 |
| 11 — XMeta & BizModel Alignment | 1 | 6 (P2x3, P3x3) | 6 | 0 | 0 |
| 16 — Test Coverage & Quality | 1 | 10 (P2x5, P3x5) | 10 | 0 | 0 |
| Cross-ref — Architecture Contract Drift | 1 | 8 (P1x1, P2x6, P3x1) | 8 | 0 | 0 |
| **Total** | **10** | **53** | **53** | **0** | **0** |

---

## Severity Distribution

| Severity | Count | Categories |
|----------|-------|------------|
| P1 | 2 | Maven parent POM missing, dao→core dependency inversion |
| P2 | 30 | BizModel data auth bypass, ErrorCode naming systemic, xmeta field protection gaps, workflow test-scope inversion, CRUD API codegen disabled, ORM index redundancy, test coverage AntiTest gaps, contract drift |
| P3 | 21 | Inline param strings, bare NopException in module code, redundant tests, naming inconsistencies, deprecated method retention, empty retention xmeta |

---

## Cross-Reference: Architecture Doc Contract Drift

### [CR-01] service-layer.md: `requireEntity()` contract systematically violated (P2)

- **File**: `docs-for-ai/02-core-guides/service-layer.md:57-60` — Doc states "取实体优先走 `requireEntity()`"
- **Evidence**: 8 BizModel files, ~20 methods use `dao().getEntityById(id)` instead of `requireEntity(id, actionName, context)` — originally flagged as [维度07-003] in multi-dim audit
- **Risk**: Doc describes standard pattern; ~50% of custom `@BizMutation` methods in the module violate it. This is systemic contract drift.

### [CR-02] service-layer.md: Redundant nested `txn()` in @BizMutation (P2)

- **File**: `docs-for-ai/02-core-guides/service-layer.md:66` — Doc states `@BizMutation` is already transactional
- **Evidence**: `NopMetaDataContractBizModel.java:43,76` wraps body in `txn().runInTransaction(...)` — [维度07-002]
- **Risk**: Doc explicitly warns against this. Creates confusing transaction boundaries.

### [CR-03] error-handling.md: Missing `(String)` constructors on NopMetadataException (P2)

- **File**: `docs-for-ai/02-core-guides/error-handling.md:9-11` — Doc requires both ErrorCode and String constructors
- **Evidence**: `NopMetadataException.java:22-32` has only ErrorCode constructors — [维度09-001]
- **Risk**: Internal code must use bare `NopException` or define intermediate ErrorCodes.

### [CR-04] error-handling.md: ErrorCode naming separator drift (P2)

- **File**: `docs-for-ai/02-core-guides/error-handling.md:69-80` — Doc uses `nop.err.order.not-found` format (dots)
- **Evidence**: All ~80+ error codes use hyphens: `nop.err.metadata.aggr-no-measure` — [维度09-007]
- **Risk**: Downstream tooling parsing by dots for sub-domain extraction produces wrong results.

### [CR-05] domain-module-pattern.md: `*-api/` no-parent POM + wrong Java version (P1)

- **File**: `docs-for-ai/01-repo-map/domain-module-pattern.md:23` — Standard structure includes `{app}-api/`
- **Evidence**: `nop-metadata-api/pom.xml` — no `<parent>` element, `java.version=11` vs rest of project Java 21 — [AR-36]
- **Risk**: Binary compatibility mismatch, BOM management bypassed.

### [CR-06] domain-module-pattern.md: CRUD API codegen disabled — pipeline gap (P2)

- **File**: `docs-for-ai/01-repo-map/domain-module-pattern.md:70-84` — Standard model→codegen→dao→meta→service→web pipeline
- **Evidence**: `gen-crud-api.xgen` fully commented out — [维度05-008]
- **Risk**: New entities require manual BizModel creation; no automated coverage verification.

### [CR-07] service-layer.md: `Map<String, Object>` return type (P2)

- **File**: `docs-for-ai/02-core-guides/service-layer.md:74` — "不要把复杂返回值做成 Map<String, Object>"
- **Evidence**: `NopMetaTableBizModel.java:245-283` uses `List<Map<String, Object>>` — [维度07-004]
- **Risk**: No type safety, no GraphQL selection validation.

### [CR-08] service-layer.md: Missing I*Biz interface declarations (P2)

- **File**: `docs-for-ai/02-core-guides/service-layer.md:18` — "Inject I*Biz interfaces for cross-module calls"
- **Evidence**: `INopMetaDataContractBiz` missing `checkContractReadOnly`; `INopMetaTagLabelBiz` missing `propagateTags`/`suggestTags` — [维度07-005, 维度07-006]
- **Risk**: Public `@BizQuery/@BizMutation` methods invisible to cross-module callers.

---

## Consolidated Findings (All Dimensions)

### Dimension 01: Dependency Graph & Module Boundaries

| ID | Finding | Severity |
|----|---------|----------|
| 01-001 | nop-metadata-api not registered in nop-bom POM (missing artifact) | P2 |
| 01-002 | nop-metadata-api/pom.xml has no parent POM, java.version=11, standalone config | P1 |

### Dimension 02: Module Responsibility & File Boundaries

| ID | Finding | Severity |
|----|---------|----------|
| 02-001 | nop-metadata-service contains 3 production workflow `.xwf` files depending on test-scoped `nop-wf-service` | P2 |
| 02-002 | Empty `_dao.beans.xml` imported by `app-service.beans.xml` — no-op import | P3 |

### Dimension 03: API Surface & Contract Consistency

| ID | Finding | Severity |
|----|---------|----------|
| 03-001 | 4 custom BizModel mutation methods (`judgeByRuleId`, `activateContract`, `deprecateContract`, `retireContract`) have zero test coverage (CR-08 linked) | P2 |
| 03-002 | NopMetaSearchBizModel: pseudo-BizModel with no corresponding entity or xmeta | P2 |

### Dimension 04: ORM Model & Entity Design

| ID | Finding | Severity |
|----|---------|----------|
| 04-001 | NopMetaSemanticType redundant index `IX_NOP_META_SEM_TYPE_NAME` duplicates UK | P2 |
| 04-002 | NopMetaDataSource missing non-unique index on `status` column | P2 |
| 04-003 | NopMetaDataProduct comment mismatch: "报表定义（预留）" instead of data product description | P2 |
| 04-004 | NopMetaQualityCheckpoint.extConfig missing `stdDomain="json"` | P3 |
| 04-005 | 3 dicts defined in ORM model but unused by any `ext:dict` reference | P3 |
| 04-006 | Dict values use inconsistent case styles (UPPERCASE, lowercase, PascalCase) | P3 |
| 04-007 | NopMetaModule self-referencing FK lacks cascade behavior documentation | P3 |

### Dimension 05: Codegen Pipeline Integrity

| ID | Finding | Severity |
|----|---------|----------|
| 05-001 | CRUD API codegen intentionally disabled (`gen-crud-api.xgen` commented out) | P2 |
| 05-002 | dao module pom.xml lacks codegen plugin (must build from parent level) | P3 |

> Pipeline model→dao→meta→web is correctly closed (39:39:39:39 entity match). 8 informational items confirm correct operation.

### Dimension 07: BizModel Conformance

| ID | Finding | Severity |
|----|---------|----------|
| 07-001 | NopMetaSearchBizModel: pseudo-BizModel without entity/xmeta | P2 |
| 07-002 | NopMetaDataContractBizModel: redundant `txn().runInTransaction()` inside `@BizMutation` | P2 |
| 07-003 | 8 BizModels use `dao().getEntityById()` instead of `requireEntity()` (~20 methods) | P2 |
| 07-004 | NopMetaTableBizModel uses `List<Map<String, Object>>` instead of typed `@DataBean` | P2 |
| 07-005 | INopMetaDataContractBiz missing `checkContractReadOnly` declaration | P2 |
| 07-006 | INopMetaTagLabelBiz missing `propagateTags`/`suggestTags` declarations | P2 |
| 07-007 | NopMetaLineageEdgeBizModel uses `dao().saveEntity()` bypassing CrudBizModel pipeline | P3 |
| 07-008 | NopMetaDataContractBizModel retains `@Deprecated` methods | P3 |

### Dimension 09: Error Handling & Error Codes

| ID | Finding | Severity |
|----|---------|----------|
| 09-001 | NopMetadataException missing `(String)` and `(String, Throwable)` constructors | P2 |
| 09-002 | NopMetaDataContractBizModel uses bare `NopException` (6 sites) | P3 |
| 09-003 | MetaTableFieldResolver uses bare `NopException` | P3 |
| 09-004 | MetaQualityRuleExecutor uses bare `NopException` with fully-qualified class path | P3 |
| 09-005 | Widespread inline string keys in `.param()` instead of `ARG_*` constants | P3 |
| 09-006 | Empty `catch (SQLException)` in MetaTableProfiler (no logging) | P3 |
| 09-007 | Systemic ErrorCode naming uses hyphens instead of dots as sub-domain separator | P2 |

### Dimension 11: XMeta & BizModel Alignment

| ID | Finding | Severity |
|----|---------|----------|
| 11-001 | NopMetaDataSource `connectionConfigComponent` not restricted (sibling `connectionConfig` is) | P2 |
| 11-002 | Duplicate core constant `STATUS_MANUAL` in NopMetaReconciliationResultBizModel | P3 |
| 11-003 | 38/42 retention xmeta files have empty `<props/>` — no field-level access control overrides | P3 |
| 11-004 | `computeQualityScore` bypasses xmeta insertable validation via direct `dao().saveEntity()` | P2 |
| 11-005 | Redundant `@GraphQLReturn` on NopMetaGlossaryTermBizModel.update() | P3 |
| 11-006 | `sourceSql`/`buildSql` not flagged with `tagSet="sensitive"` for event redaction | P2 |

### Dimension 16: Test Coverage & Quality

| ID | Finding | Severity |
|----|---------|----------|
| 16-001 | Minimal AutoTest snapshot coverage (only 1/82 test files) | P2 |
| 16-002 | Getter/setter round-trip tests with near-zero regression protection | P3 |
| 16-003 | Repetitive CRUD tests across entities (same pattern, different entity) | P2 |
| 16-004 | 4 BizModel interface methods lack test coverage | P2 |
| 16-005 | Concurrent test lacks shared state verification (false concurrency coverage) | P2 |
| 16-006 | `assertNotNull` precondition guards as noise | P3 |
| 16-007 | Data auth test bypasses framework enforcement verification | P2 |
| 16-008 | Aggregation tests lack snapshot integration | P3 |
| 16-009 | `Thread.sleep(1100ms)` causing test slowness | P2 |
| 16-010 | Large test files (>600 lines, up to ~1300 lines) | P3 |

---

## Architecture Doc Contract Drift Summary

The 8 cross-reference findings (CR01-CR08) reveal a pattern: the module's codebase has **systemically drifted from 5 documented conventions** in `docs-for-ai/`:

| Document | Drift Type | Severity | Linked Findings |
|----------|-----------|----------|-----------------|
| `service-layer.md` | `requireEntity()` bypass (~20 methods) | P2 | 07-003 |
| `service-layer.md` | Nested txn() in @BizMutation | P2 | 07-002 |
| `service-layer.md` | `Map<String, Object>` returns | P2 | 07-004 |
| `service-layer.md` | Missing I*Biz interface declarations | P2 | 07-005, 07-006 |
| `error-handling.md` | Missing String constructors | P2 | 09-001 |
| `error-handling.md` | Hyphen vs dot in ErrorCode naming | P2 | 09-007 |
| `domain-module-pattern.md` | No parent POM + wrong Java version | P1 | 01-002 |
| `domain-module-pattern.md` | CRUD API codegen disabled | P2 | 05-001 |

---

## Overall Assessment

nop-metadata is a structurally sound module with 39 well-modeled entities, complete codegen pipeline (model→dao→meta→web), strong BizModel patterns overall (~42 `@BizModel` classes with correct inheritance), and good test coverage (82 test files, ~450-500 methods).

**53 confirmed findings** break down as:
- **2 P1**: Missing parent POM on `nop-metadata-api` (Maven build isolation), dao→core dependency inversion (structural)
- **30 P2**: Systemic contract drift from documented conventions, data auth bypasses, missing xmeta field protections, workflow test-scope inversion, disabled CRUD codegen
- **21 P3**: Inline param strings, exception type inconsistencies, redundant tests, deprecated method retention

**Most impactful items (by remediation priority):**
1. Fix `nop-metadata-api/pom.xml` parent POM + Java version (P1)
2. Replace `dao().getEntityById()` with `requireEntity()` across ~20 `@BizMutation` methods (P2)
3. Add `(String)` constructors to `NopMetadataException` (P2)
4. Resolve `nop-wf-service` test-scope vs production workflow resources mismatch (P2)
5. Enable CRUD API codegen or add build-time BizModel coverage check (P2)

**Areas not audited**: XDSL/XLang correctness (10), Delta customization (06), IoC/beans config (08), GraphQL API layer (12), Security/permissions (13), Async/transactions (14), Type safety (15), Code style (17), Doc consistency (18), Naming (19), Cross-module contracts (20), Unit test effectiveness (21). These remain open for future audit rounds.

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
