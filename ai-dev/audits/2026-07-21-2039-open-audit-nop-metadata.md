> Audit Status: closed (all findings routed to Plans 08/09/10, now completed)
> Audit Type: open-ended
> Mission: nop-metadata

# Open-Ended Adversarial Audit: nop-metadata (Round 5)

**Auditor**: opencode adversarial agent
**Date**: 2026-07-21
**Previous audits consulted** (cross-referenced for deduplication): 2026-07-19-1118-open (14 findings), 2026-07-19-1118-multi (52), 2026-07-20-1554-deep, 2026-07-20-1816-open (10), 2026-07-20-1816-multi (88), 2026-07-21-2039-multi (41), 2026-07-21-2039-open (5 AR findings)

**Deduplication**: All findings below cross-referenced against ~210+ previously reported issues. None duplicate existing reports.

**Discovery perspectives used**: 异常路径侦探 (batchConfirmMatches NFE), 死代码清道夫 (dead extractItems), 跨边界侦探 (mergeRow leftKeys), GraphQL契约考古学家 (formatIso timezone), 模型攻击者 (incomplete key type check)

---

## Findings

### [AR-20] batchConfirmMatches nullable rowIndex throws NumberFormatException (bypasses ErrorCode)

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaReconciliationResultBizModel.java:113-117, 168-173`
- **Evidence fragment**:
  ```java
  // line 113-117: batchConfirmMatches
  for (Map<String, Object> sel : selections) {
      int rowIndex = toInt(sel.get(FIELD_ROW_INDEX));
      ...
      checkRowIndex(resultId, rowIndex, size);
  }

  // line 168-173: toInt helper
  private static int toInt(Object v) {
      if (v instanceof Number) {
          return ((Number) v).intValue();
      }
      return Integer.parseInt(String.valueOf(v));  // null → "null" → NFE
  }
  ```
- **Severity**: P2
- **Status**: When `batchConfirmMatches` receives a selection entry where `rowIndex` is null (missing key) or explicitly null, `toInt(null)` calls `Integer.parseInt("null")` which throws `NumberFormatException`. This is a bare `RuntimeException` subclass that bypasses the `NopException` + ErrorCode error handling convention used throughout the rest of the module. The GraphQL layer wraps it as a generic 500 error without the `resultId` / `rowIndex` business context.
- **Risk**: API consumers sending a malformed selection entry get a raw NFE instead of a structured ErrorCode. Harder to debug. Inconsistent with the module's own `checkRowIndex` which uses proper ErrorCode.
- **Suggested fix**: Add null guard at the start of `toInt()`:
  ```java
  private static int toInt(Object v) {
      if (v == null) {
          throw new NopException(NopMetadataErrors.ERR_RECON_INVALID_SELECTION)
                  .param("resultId", resultId)  // pass through context
                  .param("reason", "rowIndex is null");
      }
      ...
  }
  ```
  Or add null check in `batchConfirmMatches` loop before calling `toInt`.
- **Confidence**: Likely
- **Discovery perspective**: 异常路径侦探 — null parameter propagates to raw NFE

### [AR-21] CrossDbJoinMerger.mergeRow leftKeys derived from first row only — column collision resolution fragile

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/CrossDbJoinMerger.java:73, 167-183`
- **Evidence fragment**:
  ```java
  // line 73: leftKeys from first row only
  Set<String> leftKeys = leftRows.isEmpty() ? Collections.emptySet()
          : new HashSet<>(leftRows.get(0).keySet());

  // line 167-183: mergeRow uses leftKeys (case-sensitive) to alias right-columns
  static Map<String, Object> mergeRow(Map<String, Object> left, Map<String, Object> right,
                                        String alias, Set<String> leftKeys) {
      Map<String, Object> row = new java.util.LinkedHashMap<>();
      if (left != null) { row.putAll(left); }
      if (right != null) {
          for (Map.Entry<String, Object> e : right.entrySet()) {
              String k = e.getKey();
              if (leftKeys != null && leftKeys.contains(k)) {
                  k = alias + "_" + k;  // alias-prefix to avoid collision
              }
              row.put(k, e.getValue());
          }
      }
      return row;
  }
  ```
- **Severity**: P2
- **Status**: `leftKeys` is computed from the first left row's `keySet()` only. Two issues:
  1. **Heterogeneous SQL results**: If the left query produces rows with different column sets (valid in SQL — different sub-query branches, joined tables with varying columns), `leftKeys` misses columns present only in subsequent left rows. Right-side columns with the same name as these missed columns will not get prefixed → silent collision → one column overwrites the other in the merged row.
  2. **Case sensitivity**: SQL column names are typically case-insensitive, but `Set.contains()` is case-sensitive. If the first row has column `"ID"` and a later row has `"id"`, the later row's column won't be in `leftKeys` → right-side `"id"` won't be prefixed.
- **Risk**: Cross-DB JOIN results may silently lose data via column overwrite when left result set has heterogeneous column casing or composition. User gets wrong data without any error indication.
- **Suggested fix**: Build `leftKeys` from all left rows, or use case-insensitive set (e.g., `TreeSet(String.CASE_INSENSITIVE_ORDER)`):
  ```java
  Set<String> leftKeys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
  for (Map<String, Object> row : leftRows) { leftKeys.addAll(row.keySet()); }
  ```
- **Confidence**: Likely
- **Discovery perspective**: 跨边界侦探 — SQL semantics lost across DB boundary during merge

### [AR-22] MetaManifestBuilder.formatIso uses literal 'Z' but formats in local timezone

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/manifest/MetaManifestBuilder.java:147-151`
- **Evidence fragment**:
  ```java
  private static String formatIso(Date date) {
      if (date == null) return null;
      // 'Z' is a literal, not a pattern letter — always appended regardless of timezone
      return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(date);
  }
  ```
  Called from `build()` line 124: `metadata.put("generatedAt", formatIso(now));`
- **Severity**: P3
- **Status**: `formatIso` uses `SimpleDateFormat` with `'Z'` as a literal character (enclosed in single quotes), meaning `Z` is always appended as a fixed suffix regardless of the actual timezone. However, `SimpleDateFormat.format(Date)` uses the JVM's default timezone for the time component. On a JVM running in UTC+8 (e.g., China/Hong Kong), `new Date()` at 02:30 UTC would format as `"2026-07-21T10:30:00Z"` — asserting it's UTC when it's actually UTC+8. This is a timezone misrepresentation bug. The `generatedAt` timestamp is embedded in the manifest JSON that downstream consumers may interpret as UTC (as `Z` denotes).
- **Risk**: Manifest timestamps are off by the local timezone offset. If manifests are compared across regions or synchronized, the timestamps are inconsistent. Cache invalidation or version comparison logic using this timestamp would be wrong.
- **Suggested fix**: Set the formatter to UTC:
  ```java
  SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'X'");
  sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
  return sdf.format(date);
  ```
  Or use `Instant.now().toString()` which produces proper ISO-8601 UTC.
- **Confidence**: Likely
- **Discovery perspective**: GraphQL契约考古学家 — ISO-8601 timestamp with Z but not UTC

### [AR-23] NopMetaReconciliationConfigBizModel.extractItems dead code

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaReconciliationConfigBizModel.java:144-154`
- **Evidence fragment**:
  ```java
  // lines 144-154: defined but NEVER called
  private static List<Map<String, Object>> extractItems(Map<String, Object> queryResult) {
      if (queryResult == null) {
          return new ArrayList<>();
      }
      Object itemsObj = queryResult.get("items");
      if (itemsObj instanceof List) {
          return (List<Map<String, Object>>) itemsObj;
      }
      return new ArrayList<>();
  }
  ```
  The actual code on line 124 uses the typed return of `INopMetaTableBiz.queryTableData` directly:
  ```java
  items = tableBizModel.queryTableData(metaTableId, null, null, null, null, context).getItems();
  ```
  Grep confirms zero callers of `extractItems`.
- **Severity**: P3
- **Status**: Private method `extractItems` is defined but never invoked. It was likely intended for an earlier version that passed `queryResult` as `Map<String, Object>` before switching to the typed `QueryTableDataResponseDTO` return. It's a 3-line dead method with no test coverage. Companion to earlier dead code findings (NopMetadataConfigs, NopMetadataConstants, nop-metadata-api module).
- **Risk**: Low individually, but cumulatively with the other dead code/stubs (NopMetadataConfigs, NopMetadataConstants, nop-metadata-api empty module) it signals incomplete cleanup discipline.
- **Suggested fix**: Remove the `extractItems` method entirely (and the now-unused `@SuppressWarnings("unchecked")` on `batchConfirmMatches` line 144).
- **Confidence**: Likely
- **Discovery perspective**: 死代码清道夫 — left behind after typed return migration

### [AR-24] CrossDbJoinMerger key type consistency check uses single-row heuristic — misses heterogeneous types

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/CrossDbJoinMerger.java:106-120, 122-129`
- **Evidence fragment**:
  ```java
  private void verifyCrossDbKeyTypeConsistency(NopMetaTableJoin join,
                                                List<Map<String, Object>> leftRows, String leftField,
                                                List<Map<String, Object>> rightRows, String rightField) {
      Object leftSample = firstNonNullKey(leftRows, leftField);
      Object rightSample = firstNonNullKey(rightRows, rightField);
      if (leftSample == null || rightSample == null) {
          return;  // no non-null key to compare → skip
      }
      if (!leftSample.getClass().equals(rightSample.getClass())) {
          throw new NopException(NopMetadataErrors.ERR_JOIN_CROSS_DB_KEY_TYPE_MISMATCH)...
      }
  }

  private static Object firstNonNullKey(List<Map<String, Object>> rows, String field) {
      for (Map<String, Object> r : rows) {
          Object v = getCaseInsensitive(r, field);
          if (v != null) return v;
      }
      return null;
  }
  ```
- **Severity**: P3
- **Status**: The type consistency check only examines the first non-null key value. If the left side has mixed types (first row has Integer, later rows have Long; or first row has BigDecimal, later rows have Double), the type check passes. Then `String.valueOf()` converts all keys to strings — `Integer.valueOf(1)` → `"1"`, `Long.valueOf(1)` → `"1"` (fine for numeric widening) — but `BigDecimal.valueOf(1.0)` → `"1.0"` vs `Double.valueOf(1.0)` → `"1.0"` (fine). The real risk is when first rows happen to match types but later rows don't — the check is only as good as the first non-null row order.
- **Risk**: Low in practice (most SQL results from a single column have uniform types), but the heuristic could miss genuine type inconsistencies that cause silent mismatches: e.g., `Integer` left vs `String` right that happen to have matching first rows of different types (e.g., `Integer.valueOf(123)` and `"00123"` — `String.valueOf(123)` → `"123"` ≠ `"00123"` → key mismatch → missing JOIN rows). The missed rows would appear as data loss with no error or warning.
- **Suggested fix**: Iterate all rows and verify uniform type within each side. At minimum, add a comment documenting the heuristic limitation. For complete safety, compare a larger sample or convert all keys through a common representation (e.g., normalized string) instead of just checking type equality of first match.
- **Confidence**: Likely
- **Discovery perspective**: 模型攻击者 — heuristic-based validation can be bypassed by row order

---

## Unconfirmed / borderline observations

- **`LEFT` as alias prefix in CrossDbJoinMerger**: Hardcoded `alias = "right"` fallback (line 53-54). If the right side table is also named `right` (unlikely but possible), the alias prefix `right_` duplicates and could cause confusion. Not reported as a finding because it's a very unlikely collision.

---

## Total Assessment

### Most notable 1-2 concerns

1. **batchConfirmMatches NFE (AR-20) is the only P2-level runtime defect discovered anew.** The reconciliation API is the latest addition to nop-metadata and shows slightly less maturity in its error handling compared to the rest of the module. The `toInt(null)` → NFE path is a straightforward fix but represents the kind of edge case that test-driven development would catch.

2. **CrossDbJoinMerger column collision resolution (AR-21) is a subtle data correctness issue in the cross-DB path.** The `leftKeys`-from-first-row-only pattern means cross-DB JOIN results can silently lose data when the left result set has heterogeneous column composition. This is the kind of bug that's hard to reproduce in testing (depends on query plan order) but costly in production.

3. **The dead code pattern is accumulating.** AR-23 (extractItems), plus previously reported but still present NopMetadataConfigs, NopMetadataConstants, and nop-metadata-api empty module, form a pattern of incomplete cleanup. Together they signal that the post-migration cleanup checklist is not being fully followed, which could mask more serious stale-migration-artifact issues in the future.

### Blind spots

- Did not run or inspect AutoTest snapshot tests (reported absent in previous audits).
- Did not verify whether the `ERR_RECON_INVALID_SELECTION` ErrorCode constant exists (it would need to be added as a new constant — checking was deferred).
- Did not perform actual end-to-end testing with MySQL to verify the OFFSET-without-LIMIT behavior on real dialect.
- Did not verify the `MetaQualityCheckpointScheduler.executeScheduledCheckpoint` error code mismatch (ERR_CHECKPOINT_SCHEDULER_INVALID_CRON used for null checkpointId — previously noted but not independently verified).
- Did not inspect the `sort` package or the `target/` build output for generated file verification.

### Severity distribution

| Severity | Count | Main categories |
|----------|-------|-----------------|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 2 | NFE bypass in batchConfirmMatches (AR-20), mergeRow column collision (AR-21) |
| P3 | 3 | formatIso timezone bug (AR-22), dead extractItems (AR-23), incomplete key type check (AR-24) |
