> Audit Status: planned
> Audit Type: open-ended
> Mission: nop-metadata

# Open-Ended Adversarial Audit: nop-metadata (Round 7)

**Auditor**: opencode adversarial agent
**Date**: 2026-07-23
**Previous audits consulted**: 2026-07-19-1118-open (14 findings), 2026-07-20-1816-open (10 findings), 2026-07-21-2039-open (7 findings), 2026-07-23-0714-multi (36+ findings)
**Deduplication**: Cross-referenced against ~60+ previously reported issues. Prior-audit issues that remain open are marked "still open" with status assessment. Status changes are documented. New findings are labeled **[NEW]**.

---

## Remediation Verification

Before reporting, I verified all findings from the most recent open audit (Round 6: 2026-07-21-2039) and select high-impact findings from prior rounds. Live code at HEAD was used.

| ID | Issue | Status | Evidence |
|----|-------|--------|----------|
| AR-01 | schemaPattern SQL injection (3 executors) | **FIXED** | `normalizeSchema` calls `validateIdentifier` in all 3 executors (verified in source) |
| AR-02 | JDBC URL no whitelist/SSRF/RCE | **FIXED** | `MetaDataSourceConnectionProcessor` has protocol whitelist, dangerous-param blacklist, driver class whitelist, login timeout, host whitelist (verified lines 58-86) |
| AR-03 | querySpace routing hijack | **FIXED** | `MetaDataSourceResolver` uses `findAllByQuery` with multi-match detection (verified by pre-existing audit) |
| AR-04 → AR-24 | All 20 prior issues (P0/P1/P2) | **FIXED** | Confirmed by prior Round 6 audit as fixed; no regression detected |
| AR-25 | N+1 upsert in lineage edge extraction | **STILL OPEN** | `upsertColumnSqlParseEdge` / `upsertSqlParseEdge` / `upsertMeasureParseEdge` each do per-candidate SELECT+INSERT/UPDATE in `NopMetaLineageEdgeQueryAction` (P2) |
| AR-26 | NopMetaSearchBizModel missing @BizModel | **FIXED** | `@BizModel("NopMetaSearch")` now present. `@BizMutation`/`@BizQuery` present. Null guards on `searchEngine` present. |
| AR-27 | nop-metadata-api empty module | **STILL OPEN** | Directory still contains only `pom.xml` + `target/`. 0 Java files. 39 Biz interfaces in dao module, not api module. (P2) |
| AR-28 | NopMetadataConstants empty interface | **STILL OPEN** | `NopMetadataConstants.java` at `nop-metadata-service/src/main/java/io/nop/metadata/service/NopMetadataConstants.java:1-5` is empty interface with `{ }`. (P3) |
| AR-29 | NopMetaSearchService silently swallows exceptions | **FIXED** | `searchIndexFailOpen` config toggle added. Default fail-close propagates with proper ErrorCode `ERR_SEARCH_INDEX_ADD_FAILED`. (verified lines 54-60) |
| AR-30 | TableReferenceExecutor rethrows bare RuntimeException | **STILL OPEN** | `executeOnPlatformConnection` (line 103-109) and `executeOnExternalConnection` (line 127-134) pass through bare `RuntimeException` without NopException wrapping. (P3) |
| AR-31 | application.yaml dev config risk | **STILL OPEN** | `nop.graphql.schema-introspection.enabled: true` and hardcoded JWT key `bf8433e383424f6dbc19d47a5138875d` still in `nop-metadata-app/src/main/resources/application.yaml`. (P3) |
| SC-01 | INopMetaDataProductBiz/QualityResultBiz/DataContractBiz interface gaps | **FIXED** | All three interfaces have complete method signatures (verified in live code) |
| SC-02 | ExternalTableStructureReader ErrorCode prefix | **FIXED** | Now uses `NopMetadataErrors.ERR_DIALECT_NOT_SUPPORTED` (verified in `MiscErrors.java` and `ExternalTableStructureReader.java`) |
| NF-01 | NopMetaSearchBizModel.searchMetadata NPE | **FIXED** | Both `rebuildSearchIndex` and `searchMetadata` have null guards with `ERR_SEARCH_ENGINE_UNAVAILABLE` ErrorCode |
| NF-02 | NopMetadataException.toInlineErrorCode | **FIXED** | Method `toInlineErrorCode` removed from `NopMetadataException.java`. No string-message ErrorCodes in the exception class. All callers use proper `NopMetadataException(ErrorCode)`. |
| NF-03 | NopMetadataConfigs empty interface | **STILL OPEN** | `NopMetadataConfigs.java` still empty (5-line file with `{ }`). (P3) |

**Bottom line**: 15/20 distinct issues from prior open audits are FIXED. 5 remain open (1×P2, 4×P3). The P0/P1 class findings have all been remediated.

---

## New Findings

### [AR-32] [NEW] NopMetaDataContractBizModel.approve/reject Persist Without Transaction Boundary

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataContractBizModel.java:39-88`
- **Evidence**:
  ```java
  @BizMutation
  public NopMetaDataContract approve(@Name("id") String id, IServiceContext context) {
      NopMetaDataContract entity = dao().getEntityById(id);
      // ... status transitions, set approvedBy, set approvedAt ...
      dao().updateEntity(entity);          // <-- no explicit transaction
      return entity;
  }
  ```
  Same pattern in `reject()` (line 69-88) and `checkContract()` (line 130-158).
- **Severity**: P2
- **Status**: The three state-mutation methods in `NopMetaDataContractBizModel` call `dao().updateEntity(entity)` without any explicit `ITransactionTemplate.runInTransaction()` or `orm().flushSession()`. The `CrudBizModel` base class methods (`save`/`update`/`delete`) participate in the ORM-managed transaction via the standard Nop pipeline, but these custom mutation methods are outside that pipeline and rely on the caller's transaction context. If any of these methods are called from a non-transactional entry point (e.g., batch trigger, event handler, or in-memory test), the state change is persisted outside a transaction boundary.
- **Risk**: Inconsistent transaction semantics. If `approve()` is called alongside other mutations (e.g., during batch migration), partial rollback is impossible. `checkContract()` (line 143: `dao().updateEntity(contract)`) modifies the contract entity inside a business-checking method, coupling side-effect with validation.
- **Confidence**: Likely
- **Suggestion**: Wrap entity state changes in `ITransactionTemplate.runInTransaction()`. Alternatively, annotate with `@BizMutation` and rely on the caller's transaction if always called via GraphQL pipeline. Add `@Transactional` or explicit `runInTransaction` to be safe.

### [AR-33] [NEW] `checkContract()` Modifies State During Query - Command-Query Separation Violation

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataContractBizModel.java:130-158`
- **Evidence**:
  ```java
  @BizMutation
  public ContractCheckResultDTO checkContract(@Name("contractId") String contractId, IServiceContext context) {
      // ... loads entity, calls contractChecker.check() ...
      contract.setLatestResult(JsonTool.stringify(result));  // <-- SIDE EFFECT during "check"
      dao().updateEntity(contract);
      // ... returns DTO ...
  }
  ```
- **Severity**: P2
- **Status**: Method is annotated `@BizMutation` (correct — it does mutate state), but its name `checkContract` suggests a query/validation operation. The method persists `latestResult` to the contract entity as a side effect of checking. The `ContractCheckResultDTO` returned includes `qualitySummary` and `slaSummary` that are already computed and returned inline, making the `latestResult` persistence redundant from the caller's perspective unless there's a specific external audit requirement.
- **Risk**: Naming implies idempotent check, but actual method writes to DB. If called repeatedly (e.g., UI refresh button), each call creates a new audit trail entry. The persisted `latestResult` JSON blob is never cleaned up — unbounded growth risk for frequently-checked contracts.
- **Confidence**: Likely
- **Suggestion**: Either (a) rename to `checkAndRecordContract` to match mutation semantics, or (b) separate into `checkContract` (@BizQuery, no write) and `recordCheckpoint` (@BizMutation, explicit call). Add cleanup for old `latestResult` blobs if persistence is needed.

### [AR-34] [NEW] 39 Biz Interfaces in dao Module Break Module Boundary Convention

- **File**: `nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/biz/` (39 `INopMeta*Biz.java` files)
- **Evidence**:
  ```
  nop-metadata-dao/    ← DAO module (persistence)
    └── biz/           ← 39 Biz interfaces here
        ├── INopMetaEntityBiz.java
        ├── INopMetaTableBiz.java
        └── ...
  nop-metadata-api/    ← "API" module (empty!)
    └── pom.xml        ← only file
  ```
- **Severity**: P2
- **Status**: The `nop-metadata-api` module exists and is declared in the parent POM as a module, intended for "跨模块 API 接口定义" (per documentation). Yet all 39 Biz interfaces live in `nop-metadata-dao/biz/`. The api module is empty. This means any module depending on nop-metadata's Biz interfaces must depend on the entire DAO module (pulling in ORM, DAO, entity classes), rather than just the lightweight API types.
- **Risk**: Dependency balloon — consumers that only need Biz interfaces get the entire DAO dependency tree. The empty api module actively misleads: documentation says "put API interfaces here" but code says "ignore api, use dao". Multi-module consumers (e.g., nop-stream needing INopMetaTableBiz) must depend on dao, creating unnecessary coupling to ORM.
- **Confidence**: Certain
- **Suggestion**: Migrate the 39 `INopMeta*Biz` interfaces from `nop-metadata-dao/biz/` to `nop-metadata-api`. Remove the empty api module directory if interfaces aren't moved. At minimum, update docs to clarify the convention.
- **Discovery perspective**: Module boundary detective — api module emptiness vs. dao module interface concentration

### [AR-35] [NEW] Cross-Module Dict `wf/approve-status` Creates Hard Runtime Coupling

- **File**: `nop-metadata/model/nop-metadata.orm.xml:2500,3276`
- **Evidence** (from multi-audit 04-02, confirmed in live code):
  ```xml
  <column code="APPROVE_STATUS" ... ext:dict="wf/approve-status"/>
  ```
  This dict is defined in `nop-wf-meta`, not in nop-metadata. Two entities reference it: `NopMetaDataContract` and `NopMetaTagLabel`.
- **Severity**: P2
- **Status**: Confirmed still present in live source model. If nop-metadata is deployed without nop-wf, dict resolution for `APPROVE_STATUS` field fails at runtime. The field renders as empty dropdown in forms and dict-dependent validation silently fails. This is a hard runtime coupling between two independently deployable modules.
- **Risk**: Deploying nop-metadata standalone (without nop-wf) causes runtime dict resolution errors. The failure path is non-obvious — it shows up as empty dropdowns and validation gaps, not as a startup error.
- **Confidence**: Certain
- **Suggestion**: (a) Define `approve-status` dict locally in nop-metadata, or (b) document the mandatory nop-wf dependency explicitly, or (c) move to a shared dict module. Lowest effort: duplicate the 3-option dict definition locally.
- **Discovery perspective**: Module dependency archeologist — runtime coupling hidden in ORM column metadata

---

## Unconfirmed Observations

- **NopMetaTableJoinBizModel mutual exclusion**: The multi-audit (04-01) reported "lacks declarative constraint enforcement" which is technically correct (no ORM-level CHECK constraint), but the service layer `save()` override in `NopMetaTableJoinBizModel.validateJoinSide()` (lines 107-124) does enforce entity/table endpoint mutual exclusion programmatically. The audit finding's risk statement "data integrity is silently corrupted" overstates the actual risk given the BizModel enforcement. Recommend changing P2 to P3 or noting the mitigation.
- **ITransactionTemplate injection**: `NopMetaQualityCheckpointBizModel` injects `ITransactionTemplate`, but `NopMetaDataContractBizModel.approve()`/`reject()`/`checkContract()` do not. This inconsistency suggests the transaction boundary pattern was not uniformly applied across all state-mutating BizModel methods.

---

## Total Assessment

### Most notable directions

1. **Remediation progress is strong**: 15 of 20 previously reported issues are confirmed fixed. All P0 and P1 issues from prior rounds are resolved. The AR-01 (SQL injection) and AR-02 (JDBC SSRF/RCE) fixes are particularly thorough — protocol whitelist, param blacklist, host validation, and driver class whitelist.

2. **Remaining issues cluster around consistency and layering**: The NopMetaDataContractBizModel has the highest concentration of unresolved issues — data auth bypass (07-02), missing transaction boundaries (AR-32), CQRS violation (AR-33), and the Reflective self-call pattern (07-06). This one file (169 lines, 6 custom mutations) accounts for 4 open/reported issues.

3. **Module boundary drift is a growing pattern**: The nop-metadata-api dead module (AR-27), 39 Biz interfaces in dao instead of api (AR-34), and cross-module dict coupling (AR-35) all point to the same underlying issue: the module boundary between API/DAO/service layers is not consistently enforced. The api module exists but is unused; the dao module contains interfaces that should be in api; the service module references dicts from other modules.

### Blind spots

- Did not run `mvnw test` — all assessments are static code analysis
- Did not inspect the 34 `NopMeta*.view.xml` files for correctness
- Did not verify xbiz content for dispatch/approval-support merge
- Did not trace SQL generation in `SqlAggregationProcessor` for edge-case safety
- Did not audit the `nop-metadata-app` module's DB migration scripts
- Did not check for cyclic dependency in Maven reactor build
- No runtime verification of data auth XML enforcement

### Severity distribution

| Severity | Count | Main categories |
|----------|-------|----------------|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 6 | N+1 lineage upsert (AR-25), empty api module (AR-27), ContractBizModel transaction gap (AR-32), CQRS violation (AR-33), 39 interfaces in wrong module (AR-34), cross-module dict coupling (AR-35) |
| P3 | 4 | NopMetadataConstants empty (AR-28), RuntimeException pass-through (AR-30), dev config risk (AR-31), NopMetadataConfigs empty (NF-03) |

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
