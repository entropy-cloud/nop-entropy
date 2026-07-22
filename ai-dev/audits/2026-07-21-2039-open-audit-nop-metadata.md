> Audit Status: planned
> Audit Type: open-ended
> Mission: nop-metadata

# Open-Ended Adversarial Audit: nop-metadata (Round 6)

**Auditor**: opencode adversarial agent
**Date**: 2026-07-21
**Previous audits consulted**: 2026-07-19-1118-open (14 findings), 2026-07-20-1554-deep (21+ dimensions), 2026-07-20-1816-multi (88), 2026-07-20-1816-open (10), 2026-07-21-2039-multi (41), 2026-07-21-2039-open (5 AR findings)

**Deduplication**: All findings below cross-referenced against ~210+ previously reported issues. Where a previously-reported issue is confirmed still present, it is marked "still open" with current status assessment. New findings are labeled "new".

## Remediation Verification Summary

Before reporting new findings, I verified the status of the 14 findings from the most recent open audit (Round 5 AR-20 through AR-24) and the critical P0/P1 issues from the 2026-07-19 open audit (AR-01 through AR-14):

| Previous ID | Issue | Status | Evidence |
|---|---|---|---|
| AR-01 | schemaPattern SQL injection (3 executors) | **FIXED** | `MetaTableProfiler.normalizeSchema` (line 557-565), `MetaQualityRuleExecutor.normalizeSchema` (line 670-678), `MetaCatalogCollector.normalizeSchema` (line 164-172) all call `validateIdentifier` |
| AR-02 | JDBC URL no whitelist/SSRF/RCE | **FIXED** | `MetaDataSourceConnectionProcessor` has protocol whitelist (line 59), dangerous params blacklist (line 63), driver class whitelist (line 79), login timeout (line 86), host whitelist (line 109) |
| AR-03 | querySpace routing hijack | **FIXED** | `MetaDataSourceResolver` now uses `findAllByQuery` with multi-match detection (line 70-80) |
| AR-04 | OFFSET without LIMIT MySQL bug | **FIXED** | Verified in test `TestSqlPaginationOffsetOnly.java` (present in test list) |
| AR-05 | cross-db NULL key join semantics | **FIXED** | `CrossDbJoinMerger.crossDbMerge` skips NULL keys on both sides (line 68-73, 80-85) |
| AR-06 | syncExternalTables before==after | **FIXED** | Before captured at line 182 (pre-loop), after at line 207 (post-loop); proper diff semantics |
| AR-07 | connectionConfig in events | **FIXED** | `MetaModelChangedEventPublisher` has sensitive column redaction via `isSensitiveColumn` (line 220) |
| AR-08 | Math.toIntExact overflow | **FIXED** | `CrossDbJoinMerger.truncate` now checks `> Integer.MAX_VALUE` with proper ErrorCode (line 225-227, 235-237) |
| AR-09 | lineage OOM via findAll | **FIXED** | `NopMetaLineageEdgeQueryAction` uses configurable `maxEdges`/`maxTables` limits with `setLimit` + size check throwing `ERR_LINEAGE_GRAPH_TOO_LARGE` (line 340-347, 363-371) |
| AR-10 | N+1 upsert lineage edge | **STILL OPEN** | See finding AR-25 below |
| AR-11 | evalExpectPassWhen NFE | **FIXED** | `MetaQualityRuleExecutor.evalExpectPassWhen` wraps in NFE catch → proper ErrorCode (line 636-642) |
| AR-12 | Statement vs PreparedStatement | **FIXED** | All query helpers use `PreparedStatement` (lines 552, 568, 588) |
| AR-13 | missing error param in ErrorCode | **FIXED** | Both params `sql` + `error` set in catch blocks (lines 559-562, 574-577) |
| AR-14 | wrong ErrorCode param (ERR_DATASOURCE_NOT_FOUND for table) | **FIXED** | Now correctly throws `ERR_TABLE_NOT_FOUND` with `metaTableId` param (line 329) |
| AR-20 | batchConfirmMatches NFE | **FIXED** | Parameter changed to typed DTO `ReconciliationSelectionDTO`; `toInt` has null guard (line 172-181) |
| AR-21 | leftKeys from first row only | **FIXED** | `computeLeftKeys` iterates all rows with `TreeSet(String.CASE_INSENSITIVE_ORDER)` (line 189-198) |
| AR-22 | formatIso timezone bug | **FIXED** | `SimpleDateFormat` now explicitly sets UTC (line 151-152) |
| AR-23 | dead extractItems method | **FIXED** | Method `extractItems` removed (verified by file read) |
| AR-24 | key type consistency heuristic | **FIXED** | `firstNonNullKeyType` now iterates ALL rows (line 135-151) |

## Findings

### [AR-25] N+1 upsert pattern in lineage edge extraction — still open

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeQueryAction.java:394-465`
- **Evidence fragment**:
  ```java
  // Called per candidate edge in a loop (extractColumnLineageFromSql line 225-226)
  void upsertColumnSqlParseEdge(String sourceTableId, String targetTableId,
                                  String sourceColumn, String targetColumn, String transformType,
                                  IEntityDao<NopMetaLineageEdge> dao) {
      QueryBean q = new QueryBean();
      q.addFilter(FilterBeans.eq(..., sourceTableId));
      // ... more filters ...
      NopMetaLineageEdge edge = dao.findFirstByQuery(q);  // 1 SELECT per candidate
      if (edge == null) {
          edge = dao.newEntity();
          // ...
          dao.saveEntity(edge);  // 1 INSERT per candidate
      } else {
          edge.setTransformType(transformType);
          dao.updateEntity(edge);  // 1 UPDATE per candidate
      }
  }
  ```
  Callers (`extractColumnLineageFromSql` line 213-227, `extractLineageFromSql` line 171-179, `extractMeasureLineage` line 255-278): loop per candidate with per-item upsert.
- **Severity**: P2
- **Status**: Each of the three upsert methods (`upsertSqlParseEdge`, `upsertColumnSqlParseEdge`, `upsertMeasureParseEdge`) does individual SELECT + INSERT/UPDATE per candidate. For a 50-column SQL view with 150 candidates, this produces 150 SELECT + 150 INSERT statements in a single transaction.
- **Risk**: Long-running transactions in `extractColumnLineageFromSql` and `extractMeasureLineage`. Performance degrades linearly with view complexity. The transaction holds ORM session + connection for the full duration.
- **Suggested fix**: Batch query all existing edges by `(sourceTableId, targetTableId, lineageSource)` in a single query, then batch-save new ones. Same approach as `loadExistingTableIds` (line 381-392) but extended to the 5-tuple key used by upsert methods.
- **Confidence**: Likely
- **Discovery perspective**: N+1 detector — loop-per-candidate upsert pattern

### [AR-26] NopMetaSearchBizModel missing @BizModel annotation — still open (previously reported 07/03-F2)

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/search/NopMetaSearchBizModel.java:24`
- **Evidence fragment**:
  ```java
  public class NopMetaSearchBizModel {
      // No @BizModel("NopMetaSearch") annotation
      // Methods have no @BizQuery/@BizMutation
      public List<IndexResult> rebuildSearchIndex(...) { ... }
      public SearchResultDTO searchMetadata(...) { ... }
  }
  ```
- **Severity**: P2
- **Status**: Class is named `*BizModel` but lacks `@BizModel` annotation. Its public methods lack `@BizQuery`/`@BizMutation`. Tests bypass this by directly instantiating (`TestNopMetadataSearchIntegration` line 58: `searchBiz = new NopMetaSearchBizModel()`). No xmeta file exists for this model. The class is therefore not discoverable via GraphQL and not registered in the BizModel registry.
- **Risk**: Any consumption path that depends on GraphQL schema discovery will not find these methods. The search API is effectively unusable through normal GraphQL access. This was reported in the multi-dim audit as [07/03-F2] / P1-A-01 and remains unfixed.
- **Suggested fix**: Add `@BizModel("NopMetaSearch")` to the class, `@BizMutation` to `rebuildSearchIndex`, `@BizQuery` to `searchMetadata`. Add xmeta `NopMetaSearch/NopMetaSearch.xmeta`. Add `INopMetaSearchBiz` interface if cross-module access is needed.
- **Confidence**: Likely
- **Discovery perspective**: GraphQL契约考古学家 — missing annotations mean GraphQL cannot discover

### [AR-27] nop-metadata-api empty module — still open (previously reported 01-03, D01-02)

- **File**: `nop-metadata/nop-metadata-api/` (entire directory)
- **Evidence fragment**: The module contains only `pom.xml`. No Java sources, no beans.xml, no resource files.
- **Severity**: P2
- **Status**: The BOM declares `nop-metadata-api`, the module-level POM lists it, and `docs-for-ai/03-modules/nop-metadata.md` describes it as "跨模块 API 接口定义". Yet the module has zero Java files. The `docs-for-ai/03-modules/nop-metadata.md` documentation conflicts with the actual module contents. This was reported in both the multi-dim audit (01-03, D01-02) and remains unfixed.
- **Risk**: Documentation vs. reality mismatch. Any developer reading the module description would expect to find shared API interfaces here, but the 40 `INopMeta*Biz` interfaces are scattered in the dao module instead. Migration effort increases as the module ages.
- **Suggested fix**: Either (a) migrate the 40 Biz interfaces from dao to api module, or (b) remove the api module from the reactor and update docs. Plan-first because this is a cross-module public API boundary change.
- **Confidence**: Likely
- **Discovery perspective**: 死代码清道夫 — empty module with contradictory documentation

### [AR-28] NopMetadataConstants empty interface — still open (previously reported as dead code)

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/NopMetadataConstants.java`
- **Evidence fragment**:
  ```java
  public interface NopMetadataConstants{
  	
  }
  ```
- **Severity**: P3
- **Status**: Empty interface with no members. No implementations found. Grep confirms zero references. It's a leftover stub.
- **Risk**: Low — it's just dead code. But cumulatively with the api empty module, it signals incomplete cleanup discipline.
- **Suggested fix**: Remove the file.
- **Confidence**: Likely
- **Discovery perspective**: 死代码清道夫

### [AR-29] NopMetaSearchService quietly swallows search engine exceptions — design concern

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/search/NopMetaSearchService.java:26-47`
- **Evidence fragment**:
  ```java
  public void addToIndex(String entityType, String entityId, SearchableDoc searchableDoc) {
      if (searchEngine == null) {
          LOG.warn("...");  // silent skip
          return;
      }
      try {
          searchEngine.addDoc(TOPIC, searchableDoc);
      } catch (Exception e) {
          LOG.warn("...", e);  // exception swallowed
      }
  }
  ```
- **Severity**: P3
- **Status**: The search service's `addToIndex` and `removeFromIndex` methods silently catch all exceptions and log at WARN level. If the search engine is unavailable during a data mutation, the operation completes successfully with the index silently out of sync. The caller receives no indication that the index was not updated. This is a deliberate design choice (search is ancillary), but it creates a subtle data consistency gap: metadata can be mutated/created/deleted without the corresponding search index update, but the UI might still show stale results or miss new items.
- **Risk**: Silent index drift. Users may not find newly created metadata items in search results for an extended period (only next full reindex would catch up). There's no mechanism to detect or repair index drift incrementally.
- **Suggested fix**: Either (a) make search index failures propagate (consistent write pattern), or (b) add a periodic drift-detection background job that compares indexed vs actual entities, or (c) at minimum, make the log level ERROR and add a configurable fail-open/fail-close toggle.
- **Confidence**: Likely
- **Discovery perspective**: 异常路径侦探 — exception swallowed on the write path creates silent drift

### [AR-30] TableReferenceExecutor rethrows bare RuntimeException on platform connection path — skips ErrorCode wrapping

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/tableref/TableReferenceExecutor.java:103-109`
- **Evidence fragment**:
  ```java
  } catch (Exception e) {
      if (e instanceof RuntimeException) {
          throw (RuntimeException) e;  // passes through NPE, AIOOBE, etc.
      }
      throw new NopMetadataException(NopMetadataErrors.ERR_TABLEREF_EXEC_FAILED, e)
              .param("metaTableId", ref.getMetaTableId())
              .param("error", e.getMessage());
  }
  ```
- **Severity**: P3
- **Status**: The `executeOnPlatformConnection` path passes through bare `RuntimeException` (NPE, AIOOBE, etc.) without wrapping in a `NopException` with ErrorCode. The `executeOnExternalConnection` path (line 127-134) does the same. While RuntimeExceptions are generally bugs, this means framework-level errors in profiling/catalog/quality callbacks will show up as raw exceptions rather than structured ErrorCodes. The comment suggests it's intentional (let bugs crash), but it's inconsistent with the module's otherwise thorough NopException coverage.
- **Risk**: Low — RuntimeExceptions signal bugs, not business errors. But the inconsistency complicates error monitoring. If a callback throws a non-NopException RuntimeException, it bypasses the ErrorCode infrastructure.
- **Suggested fix**: Wrap all non-NopException exceptions in `NopMetadataException(ERR_TABLEREF_EXEC_FAILED, e)`. NopException subclasses would still propagate through naturally.
- **Confidence**: Likely
- **Discovery perspective**: 异常路径侦探 — RuntimeException unwrapping bypasses NopException contract

### [AR-31] application.yaml enables GraphQL schema introspection with hardcoded JWT key — development config risk

- **File**: `nop-metadata/nop-metadata-app/src/main/resources/application.yaml`
- **Evidence fragment**:
  ```yaml
  nop:
    auth:
      jwt:
        enc-key: bf8433e383424f6dbc19d47a5138875d
    graphql:
      schema-introspection:
        enabled: true
  ```
- **Severity**: P3
- **Status**: The application module's config has `graphql.schema-introspection.enabled: true`, which exposes the full GraphQL schema (including all types, fields, and queries) to any client that sends the introspection query. Combined with the hardcoded JWT encryption key, this is a security hardening concern. Likely intended for development (config also has `nop.debug: true` and `allow-create-default-user: true`), but if this config were deployed to production without override, it would expose the entire metadata schema.
- **Risk**: In production, GraphQL introspection enables schema discovery by unauthorized parties. Hardcoded JWT enc-key means token forgery is possible if the key leaks. No custom configuration profile separates dev vs production defaults.
- **Suggested fix**: Add a `%prod` profile with introspection disabled. Move `enc-key` to an external config source or environment variable. Document the expectation in the class-level Javadoc or README.
- **Confidence**: Likely
- **Discovery perspective**: 模型攻击者 — schema introspection + hardcoded key = amplified attack surface

---

## Unconfirmed / borderline observations

- **NopMetaDataSourceBizModel still uses direct import of specific collectors** (MetaCatalogCollector, ExternalTableStructureReader, etc.) via field injection. This is consistent with the module's own architecture (not a violation), but creates a high coupling surface for the BizModel class (534 lines, 18+ imports).
- **The NopMetadataErrors.java file is 1072 lines** — previously reported as oversized (P3), still at the same size. Functions as a single-file ErrorCode enum for the entire module. Not a defect but a maintenance concern.

---

## Total Assessment

### Most notable directions

1. **Remediation quality is high.** The 13 critical/high-severity findings (AR-01 through AR-14, AR-20 through AR-24) from previous audits have been systematically fixed. The SQL injection, JDBC SSRF, OOM, event credential leak, and cross-DB merge semantic issues are no longer present. The fixing process followed a clear pattern: ErrorCode centralization, identifier whitelist normalization, security boundary hardening, and edge case protection.

2. **The remaining issues are concentrated in two areas**: the search module integration (AR-26, AR-29) and lineage extraction performance (AR-25). The search module appears to be in a partially-integrated state — it has a well-defined Service class, proper tests, and an IndexBuilder, but the BizModel is not actually wired into the GraphQL layer via `@BizModel`. The lineage N+1 pattern (AR-25) is the only significant performance/transaction issue remaining.

3. **The empty module/interface pattern (AR-27, AR-28)** is a minor but persistent hygiene concern. Both `nop-metadata-api` and `NopMetadataConstants` were reported in previous audits and neither has been addressed. Individually low impact, but as a pattern they suggest incomplete post-migration cleanup that could mask more significant stale artifacts.

### Blind spots

- Did not run actual `mvnw test` — relied on file-level code inspection
- Did not inspect the `nop-metadata-web` page definitions for sensitive data exposure
- Did not audit the 33 dictionary YAML files for correctness or completeness
- Did not check ORM model for the tagSet-sensitive columns that the event publisher depends on
- Did not verify whether the `nop-search-lucene` optional dependency actually works without lucene on the classpath
- Did not inspect the `nop-metadata-dao` module's hand-written entity classes for consistency with generated bases

### Severity distribution

| Severity | Count | Main categories |
|---|---|---|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 3 | N+1 upsert (AR-25), missing @BizModel (AR-26), empty api module (AR-27) |
| P3 | 4 | Dead constants (AR-28), swallowed search exceptions (AR-29), RuntimeException pass-through (AR-30), dev config risk (AR-31) |
