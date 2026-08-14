> Audit Status: planned
> Audit Type: open-ended
> Mission: nop-metadata-invariant-loop
> Planned Into: `ai-dev/plans/2026-08-14-0707-1-...`（AR-04）、`2026-08-14-0707-2-...`（AR-01、AR-02、AR-03）。P2（AR-05–AR-14）入 mission roadmap Follow-up Backlog。

# Open-Ended Adversarial Audit — nop-metadata (Invariant-Loop Cycle re-audit)

- **Audit date**: 2026-08-14
- **Method**: `ai-dev/skills/open-ended-adversarial-review-prompt.md` (open, discovery-oriented). No fixed dimension checklist. Lead agent read source directly; high-impact candidates independently verified against live code (not trusted from subagent output).
- **Scope**: `nop-metadata/` full module group — service/processor/query/profiling/contract/lineage/manifest/reconciliation/event code, ORM model, tests, and `docs-for-ai/03-modules/nop-metadata.md`.
- **Dedup baseline**: today's companion multi-audit `2026-08-14-0707-multi-audit-...md` (F1–F19) and the arm backlog (`arm-index`/`arm-unclosed-findings`) were scanned. Findings below are **new** relative to both, except where a finding is a **live residual of a prior adjudication**, which is explicitly cross-referenced.
- **Perspective used**: "异常路径侦探" + "模型攻击者" (control-char / dirty-data injection into in-memory aggregation and SLA parsing) + "代码生成受害者/契约考古" (silent contract drift). These surfaced the SLA-truncation, group-key-collision, and redaction-residual findings — none of which the dimension-based audit (today) caught.

## Severity distribution

| Severity | Count | Categories |
|----------|-------|-----------|
| P0 | 0 | — |
| P1 | 4 | SLA correctness (×2 same site), in-memory aggregation collision, security redaction residual |
| P2 | 11 | profiler misclassification, lineage/manifest correctness, reconciliation bounding, dialect/diagnostic gaps, dead code, locale |

Downstream remediation is driven by P1 (P2 is recorded for backlog triage).

---

## P1 — Material (must fix)

### [AR-01] SLA `toDurationMillis` truncates fractional intervals → silent wrong staleness verdict

- **Priority**: `[P1]` — SLA contract decisions flip on legal fractional inputs (e.g. "every 0.5h") with no error signal; same site also leaks an uncaught `NumberFormatException`.
- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/contract/MetaContractChecker.java:373,382` (+`:336-342`)
- **Evidence**:
  ```java
  // week branch:
  return TimeUnit.DAYS.toMillis((long) amount * 7);   // :373
  ...
  return tu.toMillis((long) amount);                   // :382  (general)
  ```
  ```java
  double amount;
  try { amount = ((Number) amountObj).doubleValue(); }
  catch (ClassCastException e) {                        // :339 — only ClassCastException
      LOG.debug(...);
      amount = Double.parseDouble(String.valueOf(amountObj)); // :341 — throws NumberFormatException, uncaught
  }
  ```
- **现状**: `amount` is parsed as `double` but every conversion to millis casts via `(long) amount`, truncating toward zero. `{"refreshFrequency":{"interval":0.5,"unit":"hour"}}` → `TimeUnit.HOURS.toMillis((long)0.5)` = `0` ms → `refreshMs=0` → `(nowMs - collectedMs) > 0` is always true → catalog reported **permanently stale**. `1.5 day` becomes `1 day` (too strict). AR-22 (R8.4a) explicitly added fail-fast for *unknown* units but left fractional amounts silently truncating.
- **风险**: Operators configure sub-hour/sub-day SLAs; the contract checker silently degrades them to either "always stale" (≤0.99h/≤0.99d → 0) or an under-sized window. False staleness alarms or silently-missed freshness violations — both are wrong contract verdicts with no error code.
- **建议**: Use `Math.round(amount)` (or preserve fractional precision via `tu.toMillis` on a `double` scaled to the finest unit) instead of `(long) amount`. Add a regression test: `{"interval":0.5,"unit":"hour"}` must yield 1_800_000 ms, and an SLA check against a 10-min-old catalog must report **not stale**.
- **信心水平**: 确定（行级读证；`double`→`(long)` 截断是 Java 语言确定行为）。
- **Dedup**: New. AR-22 only covered the `default` (unknown unit) branch; fractional-amount truncation is an unaddressed sibling in the same method.

### [AR-02] SLA amount string-parse throws uncaught `NumberFormatException` (escapes the fail-fast contract)

- **Priority**: `[P1]` — a malformed SLA amount produces an unwrapped `NumberFormatException` instead of the documented `ERR_CONTRACT_SLA_INVALID`; the module's own error-handling contract is violated.
- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/contract/MetaContractChecker.java:336-342`
- **Evidence**: (see AR-01 evidence block; the `catch` only handles `ClassCastException`; `Double.parseDouble("oops")` at `:341` throws unchecked `NumberFormatException`).
- **现状**: When `amountObj` is a non-numeric `String` (e.g. `{"interval":"oops","unit":"min"}`), `((Number) amountObj)` throws `ClassCastException` → caught → `Double.parseDouble("oops")` throws `NumberFormatException` → **not caught** → escapes `toDurationMillis` and the top-level `check` (whose try only wraps `parseQualityRuleIds`/`parseJsonObject`). The user gets a raw `NumberFormatException`, not `ERR_CONTRACT_SLA_INVALID`.
- **风险**: Violates the two-tier error-handling policy (AGENTS.md: bare unchecked exceptions must not escape module public APIs; SLA parse failures are explicitly meant to map to `ERR_CONTRACT_SLA_INVALID` per AR-22's design). A single bad SLA JSON crashes the whole contract check with a non-diagnostic exception.
- **建议**: Broaden the catch to `Exception` (or add `NumberFormatException`), and re-throw as `ERR_CONTRACT_SLA_INVALID` with `contractId` + the offending value. Add a test: `{"interval":"oops","unit":"min"}` → `getErrorCode() == ERR_CONTRACT_SLA_INVALID`, no `NumberFormatException`.
- **信心水平**: 确定。
- **Dedup**: New. Same method as AR-01 but a distinct defect path (string-amount parse vs fractional truncation).

### [AR-03] `memoryGroupBy` builds group key with an injectable separator → silent wrong aggregation

- **Priority**: `[P1]` — silent wrong SUM/COUNT/AVG with no error signal when a dimension value contains a control char (`\u0001`/`\u0000`); worst failure class (corrupted numbers, undetectable) and reachable via external/SQL-view data the module explicitly ingests.
- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/AggregationHelper.java:797-808`
- **Evidence**:
  ```java
  for (int i = 0; i < dims.size(); i++) {
      Object v = getCaseInsensitiveObj(row, dims.get(i).lookupKey);
      if (i > 0) keyBuilder.append('\u0001');              // separator
      keyBuilder.append(v == null ? "\u0000" : String.valueOf(v)); // null marker + raw value
  }
  String groupKey = keyBuilder.toString();
  ```
- **现状**: The cross-DB in-memory group-by joins dimension values with `\u0001` and uses `\u0000` as the null sentinel. Two distinct tuples collapse to one key when a value contains the separator/marker:
  - `("a", "\u0001b")` → `"a" + "\u0001" + "\u0001b"` = `"a\u0001\u0001b"`
  - `("a\u0001", "b")` → `"a\u0001" + "\u0001" + "b"` = `"a\u0001\u0001b"` — **identical** → rows wrongly merged into one group. Also `null` vs the literal string `"\u0000"` collide.
- **风险**: This is the cross-DB memory aggregation path (used when facts/dims span two datasources). A dimension value carrying SOH/NUL (dirty external data, imported xlsx, or an adversarial "model attacker" input per the prompt's own heuristic) silently fuses groups and corrupts aggregate totals — no exception, no log. The SQL-side path is unaffected (DB group-by is value-structural), so the in-memory and SQL paths **disagree** for the same data, undermining the `TestAggregationExternalJoinAndPagination.testCrossDbMemoryHavingMatchesSameDbSqlPath` invariant established in R8.3.
- **建议**: Replace the delimiter-joined `String` key with a structural key: `List<Object>` wrapped in a class with value-wise `equals`/`hashCode`, or length-prefix each dimension value (`dim_i_len + ":" + value`). Add an adversarial test: two rows with dimension values `("a","\u0001b")` and `("a\u0001","b")` must produce **two** groups, matching the SQL path.
- **信心水平**: 确定（碰撞由字符串拼接语义必然成立；触发依赖维度值含控制字符——本模块显式摄取外部表/SQL视图数据，非纯理论）。
- **发现来源视角**: 模型攻击者 + 异常路径侦探。
- **Dedup**: New. Not touched by R8.3's `MemoryFilterEvaluator` three-value-logic rewrite (that was the *filter* evaluator, not the cross-DB *group-by* key builder).

### [AR-04] Event snapshot POJO branch has NO sensitive-column redaction (AR-23⑩ sibling residual)

- **Priority**: `[P1]` — a security redaction contract (AR-07) is enforced on the ORM and Map branches but absent on the POJO branch of a method whose parameter is typed `Object entity`; AR-23⑩ was fixed for the *Map* branch on exactly this rationale ("API 级契约缺口") — the *POJO* branch is the unfixed sibling.
- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/event/MetaModelChangedEventPublisher.java:219-221`
- **Evidence**:
  ```java
  // ORM branch (:186-198): for each column, isSensitiveColumn(...) -> REDACTED_VALUE
  // Map branch (:208-216, AR-23⑩): for each key, isSensitiveColumn(null,key) -> REDACTED_VALUE
  // POJO branch:
  Object parsed = JsonTool.parse(JsonTool.stringify(entity));   // reflection serializes EVERY field
  return parsed instanceof Map ? (Map<String,Object>) parsed : new LinkedHashMap<>();
  ```
- **现状**: `buildEntitySnapshot(Object entity)` has three branches. ORM (`:181-201`) and Map (`:202-218`) both apply AR-07 credential redaction. The POJO fallback (`:219-221`) runs `JsonTool.stringify(entity)` which reflectively serializes **every** field (`password`, `connectionConfig`, `apiKey`, …) with **no** `isSensitiveColumn` check. The in-code comment "本 helper 实际只接收 ORM 实体" is a soft contract **not enforced by the code**. Live caller grep confirms all current callers pass `IOrmEntity` instances (`NopMetaTable`/`NopMetaModule`/`NopMetaDataSource`), so the branch is currently unreachable — exactly the same "0 callers but API-level contract gap" posture that AR-23⑩ used to justify fixing the Map branch.
- **风险**: The public method signature accepts `Object`; any future caller (or a Delta/subclass override) passing a POJO leaks full credentials into the `nop_meta_model_changed_event` snapshot table. The defense depends on caller discipline, not on the helper — which is the precise defect AR-23⑩ closed for the Map path.
- **建议**: Apply the same fallback redaction to the POJO branch: after `JsonTool.parse`, if the result is a `Map`, run the same `isSensitiveColumn(null, key)` pass as the Map branch (or, better, route the parsed Map back through the Map-branch logic). Alternatively, enforce "ORM-only" by throwing `ERR_EVENT_SNAPSHOT_SERIALIZE_FAILED` for non-`IOrmEntity`/non-`Map` inputs. At minimum, delete the misleading "本 helper 实际只接收 ORM 实体" comment or make it a runtime assertion.
- **信心水平**: 确定（分支逻辑行级读证；当前不可达经调用方 grep 核实）。
- **发现来源视角**: 代码生成受害者/契约考古（AR-23⑩ 的沿先例补兄弟面——正是 mission 描述中"R6.2 脱敏→R8.2 沿先例补兄弟"的同族 residual 模式）。
- **Dedup**: New residual of AR-23⑩ (R8.4b). AR-23⑩ fixed the Map branch; this is the POJO branch with the identical contract-gap characteristic.

---

## P2 — Backlog triage (recorded, no standalone remediation plan)

### [AR-05] Profiler `isNumericType` uses substring matching → misclassifies geometric/boolean columns

- **Priority**: `[P2]` — real misclassification producing wrong output shape (misclassified columns get no stats instead of string stats); maintenance correctness, not data corruption.
- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/profiling/MetaTableProfiler.java:490-501`
- **Evidence**:
  ```java
  private static final List<String> NUMERIC_KEYWORDS = Arrays.asList(
      "INT","DOUBLE","FLOAT","REAL","DECIMAL","NUMERIC","NUMBER","BIT","BOOLEAN");
  ...
  for (String kw : NUMERIC_KEYWORDS) { if (upper.contains(kw)) return true; }
  ```
- **现状**: `"POINT"` contains `"INT"` → classified numeric; `"BOOLEAN"` is explicitly listed → all boolean columns classified numeric. `collectNumericStats` then issues `SUM(POINT_col)` / `SUM(BOOL_col)` which is invalid SQL → the column lands in the per-column error path and gets **no stats at all** instead of falling back to string stats. The misclassification silently degrades profiling output for geometric/boolean/`*INT`-suffix types.
- **风险**: Profiling output silently omits stats for whole column classes; operators believe the profiler ran clean when it produced error rows for legitimate types.
- **建议**: Use word-boundary regex or, preferably, the JDBC `java.sql.Types` int (`ResultSetMetaData.getColumnType`) which is driver-portable and unambiguous. At minimum, remove `BOOLEAN` from numeric keywords and guard `INT` with a leading-non-digit check.
- **信心水平**: 确定（`POINT` 含 `INT` 子串、`BOOLEAN` 在列表内均经字符串核对）。

### [AR-06] Profiler `probeNumeric` collapses real connection/permission failures into "string stats"

- **Priority**: `[P2]` — same swallow family already flagged in the arm backlog (MA6.2-002), repeated at DEBUG here; transient infra failure becomes wrong (string) output shape.
- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/profiling/MetaTableProfiler.java:213-222`
- **Evidence**:
  ```java
  private boolean probeNumeric(...) {
      try { queryNullableDouble(conn, "SELECT SUM(" + col + ") FROM " + fromClause); return true; }
      catch (SQLException e) { LOG.debug(... "probeNumeric failed", e); return false; }
  }
  ```
- **现状**: A `SQLException` here may be benign ("non-numeric type") OR serious ("connection lost / column revoked / table dropped"); both return `false` → column silently profiled as string. Logged only at DEBUG.
- **风险**: A transient connection/permission failure mid-profile turns numeric columns into string stats with no per-column error; the profile result looks complete but is wrong.
- **建议**: Distinguish "type mismatch" (expected → return false) from "infra failure" (propagate as per-column error). At minimum raise the log level to WARN and record `col`/`tableName` context.
- **信心水平**: 确定。
- **Dedup**: New instance of the documented MA6.2-002 family (the backlog item covers `emptyCount`; this is the distinct `probeNumeric` site).

### [AR-07] `SqlSourceTableExtractor` dedups by simple table name → collapses cross-schema同名表

- **Priority**: `[P2]` — wrong lineage edges for multi-schema SQL; documented simplification, but the false-drop consequence is unrecorded.
- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/lineage/SqlSourceTableExtractor.java:78-81`
- **Evidence**:
  ```java
  // 去重：按 simpleName 去重（同一表多次引用只算一条边）。schema.table 与 table 视作同一目标。
  if (seen.add(simple)) { refs.add(new SqlTableReference(full, simple)); }
  ```
- **现状**: Dedup key is `simple` (the unqualified name). `dbo.users` and `sales.users` collapse to a single edge; the second physical table is silently dropped from the lineage.
- **风险**: Multi-schema source SQL under-reports source tables; lineage graph is wrong (missing edges) for any cross-schema view.
- **建议**: Dedup by `full` (schema-qualified) name, keeping the first-seen `full`. Document the (now-smaller) ambiguity: same full name referenced via different aliases is still one edge.
- **信心水平**: 确定。

### [AR-08] `SqlSourceTableExtractor` reports CTE names as physical source tables (false positives)

- **Priority**: `[P2]` — false-positive source edges for any SQL using `WITH`; the design note acknowledges "不展开 CTE 别名" but not the false-positive consequence the BizModel then matches against the catalog.
- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/lineage/SqlSourceTableExtractor.java:64-83`
- **现状**: The AST walk emits every `SqlSingleTableSource` table name. A CTE reference (`WITH t AS (...) SELECT * FROM t`) parses `t` as a `SqlSingleTableSource`, so the CTE name is reported as a physical source table. The class doc (`:30`) says "不展开 CTE 别名" but does not acknowledge that this yields **false positives** (CTE name as a real table) in the returned list.
- **风险**: The BizModel matches returned names against `NopMetaTable.tableName`; a CTE name that coincidentally matches a real catalog table produces a spurious lineage edge; a non-matching CTE name produces spurious "unresolved source table" entries.
- **建议**: Collect CTE names declared in the `WITH` clause and exclude them from the emitted source-table list (emit them as a separate `ctes` list, or skip). Add a test: `WITH t AS (SELECT 1) SELECT * FROM t JOIN real_table` → only `real_table` is a source.
- **信心水平**: 很可能（CTE-as-SqlSingleTableSource 由解析器语义决定；建议在闭包前用真实 `WITH` 语句跑一次 `extract` 实证）。

### [AR-09] `MetaManifestBuilder.addEdge` performs no dedup → duplicate relations produce duplicate graph edges

- **Priority**: `[P2]` — inflated graph degrees for modules with duplicate/bridged relations; manifest metadata correctness.
- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/manifest/MetaManifestBuilder.java:107-109,225-229`
- **Evidence**:
  ```java
  addEdge(parentMap, ownerUniqueId, targetUniqueId);            // :107
  childMap.computeIfAbsent(targetUniqueId, k -> new ArrayList<>()).add(ownerUniqueId); // :109
  ...
  private static void addEdge(Map<String,List<String>> map, String key, String value) {
      if (key == null || value == null) return;
      map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);   // :228 — unconditional add
  }
  ```
- **现状**: Both `parentMap` and `childMap` append unconditionally. If two `NopMetaEntityRelation` rows express the same owner→target dependency (duplicate relation rows, or self-referential M-N bridges), the same neighbor appears N times. Self-loops (owner==target) are also not filtered.
- **风险**: Manifest consumers see inflated degree counts and may render multi-edges; downstream graph metrics (centrality, impact analysis) are wrong.
- **建议**: Dedup each adjacency list (e.g. `LinkedHashSet` per key, or check `!list.contains(value)` before add). Filter self-loops (`key.equals(value)`).
- **信心水平**: 确定。

### [AR-10] `memoryGroupBy` adjacent: `toBigDecimal` loses precision for large Longs and silently drops numeric Strings

- **Priority**: `[P2]` — imprecise/wrong SUM/AVG for high-precision numeric columns in cross-DB aggregation; silent skip of string-encoded numerics.
- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/AggregationHelper.java:534-545`
- **Evidence**:
  ```java
  if (v instanceof Number) return java.math.BigDecimal.valueOf(((Number) v).doubleValue()); // Long>2^53 loses bits
  ...
  return null;   // numeric String "123" -> null -> SumAcc silently skips
  ```
- **现状**: For `Long`/`BigInteger` beyond 2^53, `doubleValue()` discards low-order bits before BigDecimal construction → imprecise SUM/AVG. For numeric values delivered as `String` (common from some JDBC drivers), `toBigDecimal` returns `null` and `SumAcc.accumulate` silently skips (`if (n != null)`) → a String-typed numeric column sums to `null`/wrong with no warning.
- **风险**: Cross-DB in-memory SUM/AVG silently wrong for high-precision or string-encoded numeric columns; no error signal.
- **建议**: Use `BigDecimal.valueOf(((Number)v).longValue())` for integral types; parse `String` numeric values via `new BigDecimal((String)v)` (catch → null). Add precision tests.
- **信心水平**: 确定。

### [AR-11] `SqlViewFieldTypeInferrer` does not strip trailing `;` from `sourceSql` → misleading type-inference failure

- **Priority**: `[P2]` — user-provided SQL views with a trailing semicolon fail type inference with a syntax-error-shaped message whose real fix is stripping `;`.
- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/sqlview/SqlViewFieldTypeInferrer.java:151-152`
- **Evidence**:
  ```java
  String wrappedSql = "SELECT * FROM (" + sourceSql + ") _t LIMIT 0";
  ```
- **现状**: For `sourceSql = "SELECT a FROM t;"`, the wrapped SQL becomes `SELECT * FROM (SELECT a FROM t;) _t LIMIT 0`; the inner trailing `;` is rejected by most JDBC drivers. The `SQLException` is wrapped (`:173-178`) as `ERR_SQL_TYPE_INFERENCE_FAILED`, so the user sees a "type inference failed / syntax error" for what is really a trivial trailing-semicolon issue.
- **风险**: Legitimate SQL views fail type inference with a misleading error; operator wastes time chasing a non-existent syntax bug in their view body.
- **建议**: `sourceSql.trim()` and strip a single trailing `;` before wrapping. Add a test: `sourceSql` with/without trailing `;` both infer successfully.
- **信心水平**: 很可能（分号包裹语法错误是大多数驱动确定行为；建议闭包前用 H2 实跑确认）。

### [AR-12] `LocalReconciliationProcessor.score` uses default-locale `toLowerCase` (Turkish-I risk)

- **Priority**: `[P2]` — non-deterministic fuzzy matching across JVM locales; low real-world trigger but deterministic-by-locale wrongness.
- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/reconciliation/LocalReconciliationProcessor.java:121-122`
- **Evidence**:
  ```java
  String s1 = a.toLowerCase(); String s2 = b.toLowerCase();  // default locale
  ```
- **现状**: The fuzzy path lowercases with the default locale. Under `tr-TR`, `"I".toLowerCase()` → `"ı"`, so two matching identifiers compared on a tr-locale JVM diverge from an en JVM. The exact path (`:110`) uses locale-insensitive `equalsIgnoreCase`, so the two paths disagree by locale.
- **风险**: Reconciliation matching becomes locale-dependent; same data + different deployment locale → different match results.
- **建议**: Use `toLowerCase(Locale.ROOT)` (or `Locale.English`).
- **信心水平**: 确定。

### [AR-13] `ReconciliationExecutor.execute` ignores the candidate `limit` knob → unbounded candidate serialization

- **Priority**: `[P2]` — a bounding parameter exists and is hardcoded `null`; under fuzzy strategy all candidates (score > 0.0001) are serialized into `details`. Kept at P2 because reconciliation is user-triggered/low-frequency (consistent with AR-23⑥ adjudication), but the *bounding-knob-ignored* aspect is distinct from the AR-23⑥ levenshtein-complexity note.
- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/reconciliation/ReconciliationExecutor.java:81-83,148-158`
- **Evidence**:
  ```java
  List<...> candidates = reconciliationService.reconcile(value, ..., null);  // :83 limit hardcoded null
  ...
  for (... c : candidates) { candidateMaps.add(c.toMap()); }                  // :151-152 serializes ALL
  ```
- **现状**: `LocalReconciliationProcessor.reconcile` only truncates when `limit != null && limit > 0` (`LocalReconciliationProcessor:85`); the executor passes `null`, so **every** fuzzy candidate (the pool minus only zero-score) is returned and serialized into the result `details` JSON. For a large candidate pool this is `rows × poolSize` maps materialized then stringified.
- **风险**: Unbounded memory/JSON blowup (potential OOM or multi-second serialization) on a large candidate pool under fuzzy strategy — the exact scenario the `limit` parameter was designed to bound.
- **建议**: Plumb a configurable limit (or a sane default, e.g. 50) through `execute`; document the cap. At minimum, cap candidate serialization in `toRowDetail`.
- **信心水平**: 确定。
- **Dedup**: Distinct from AR-23⑥ (which covered levenshtein O(n×m) *algorithm* complexity); this is the *bounding knob being ignored*.

### [AR-14] Dead/unverified code & diagnostics (batch)

- **Priority**: `[P2]` — maintenance confusion / diagnostic loss; individually trivial, collectively worth a hygiene sweep.
- **Files & items**:
  - `MetaAggregationExecutor.java:47` — `private static final Logger LOG` declared with **zero** `LOG.` references in the file (dead).
  - `AggregationHelper.java:212-227` — `resolveEntityFieldColumn(..., Map<String,String> propToCol)` never reads `propToCol`; callers build and pass it but the body re-loads the field by PK. Dead parameter + wasted work.
  - `AggregationHelper.java:469-476` — `safeProductName` catches `SQLException`, logs, returns `null`; every caller treats `null` as "unsupported dialect" and throws `ERR_AGGR_UNSUPPORTED_DIALECT`. A metadata-layer failure is mis-attributed as a dialect problem (the "logged-then-returns-wrong-result" anti-pattern). *(Note: today's multi-audit F17 covered the safeProductName **duplication**; this is the distinct **null→mis-attribution** behavior.)*
  - `SqlSelectFieldExtractor.java:114-115` — error param `"sql"` is set to the Java class name (`"unhandled SELECT statement class: ..."`), not the SQL text, unlike every sibling error site in the file.
  - `MetaManifestBuilder.java:144` — `SimpleDateFormat("'Z'")` is a literal; currently correct only because `setTimeZone(UTC)` is on the next line; fragile if refactored.
- **建议**: Delete the dead `LOG` and dead `propToCol` plumbing; have `safeProductName` distinguish "metadata failure" (propagate cause) from "unsupported dialect"; pass real SQL in `SqlSelectFieldExtractor:114`; use an immutable ISO formatter for the manifest timestamp.
- **信心水平**: 确定（逐项 grep/行级读证）。

---

## Dimensions verified clean (with evidence)

- **Error-handling tier policy (AGENTS.md)**: Zero bare `RuntimeException`/`IllegalArgumentException`/`UnsupportedOperationException` in hand-written service/core code (all in `src/test`). Public APIs use `NopMetadataException` + `ErrorCode` + `.param(...)`; the two P1 exceptions above (AR-02 NFE escape, AR-04 unenforced contract) are the *gaps* in an otherwise-followed policy, not a systematic violation.
- **JDBC resource handling**: `AggregationHelper.executeJdbcQuery`/`checkTableExists`, `MetaJoinExecutor.executeJdbcQuery`, `MetaTableQueryExecutor.executeQuery`, `SqlViewFieldTypeInferrer.inferWithinConnection` all use try-with-resources for `PreparedStatement`/`ResultSet`; the `Connection` is caller-owned via `withConnection` (closing it would be wrong). No leak found in audited JDBC paths.
- **Cross-DB pagination**: `CrossDbJoinMerger.truncate` / `AggregationHelper.truncateCrossDb` use `long` arithmetic for `from+limit`, explicitly reject negative/oversize, no off-by-one (offset inclusive, limit exclusive via `subList`). Correct.
- **Reconciliation match math**: `LocalReconciliationProcessor` score sort is stable (`Comparator.reversed()` preserves equal-order), limit/subList math is correct, unknown strategy throws (no silent skip). The defects are the locale (`toLowerCase`) and the executor's `limit=null`, not the core algorithm.

---

## 总评（最值得关注的 1–3 个方向）

1. **"silent wrong number" 家族是当前最大隐藏风险**：AR-01（SLA 截断）、AR-02（NFE 逃逸）、AR-03（group-key 碰撞）、AR-10（精度丢失）四个 P1/P2 共享同一特征——**没有错误信号，结果静默错误**。这正是本 mission 的 `check-silent-swallow` 不变式想消灭的模式，但这些落点在不变式的扫描范围之外（不是 catch 块吞异常，而是数值/字符串语义静默错算）。建议把不变式从"catch 块"扩展到"silent-wrong-result"检测（如 group-key 结构性、`(long)double` 截断、`contains` 类型分类）。
2. **沿先例补兄弟面的闭环有遗漏**：AR-04（POJO 分支）是 AR-23⑩（Map 分支）的精确 sibling，mission 描述明确把"R6.2 脱敏→R8.2 沿先例补兄弟"作为先例链铁证，但 AR-23⑩ 只补了 Map 没补 POJO。这提示"沿先例补兄弟"动作需要一个机械化的"列出同一方法所有分支/同一族所有 site"检查，否则依赖人工记忆会漏。
3. **lineage/manifest 是 metadata-correctness 的薄弱带**：AR-07/08/09 都是"文档记录为简化/限制，但 false-drop/false-positive 后果未记录"。对元数据目录而言，错误的血缘边/图度数是产品级缺陷，建议把这些建议从"文档化限制"升级为"限制 + 后果声明 + 测试钉死已知场景"。

## 本次审查的盲区自评

- **未跑动态验证**：AR-03/AR-08/AR-11 标注"建议闭包前实跑"。lineage 解析器对 CTE/派生表的真实 AST 行为、group-key 碰撞在端到端聚合里的可触发性，我没有用真实 GraphQL/JDBC 载荷执行——只做了静态代码追踪。闭包前必须做 live 载荷验证。
- **前端/web 契约未深审**（本 mission 目标集为 service/processor/bizmodel + ORM）。
- **风格/checkstyle 机械问题未穷举扫描**（按 skill，P2-only，非 invariant-loop 再审计重点）。
- **并行子 agent 还报告了若干 P2（如 `SimpleDateFormat 'Z'`、`firstNonNullKeyType` 误导参数名、CTE `NamedSourceMap.name=""`）**，其中证据较弱或属纯诊断退化的已并入 AR-14 批次或舍弃；未逐一展开以避免凑数。

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 4    | SLA 正确性（×2）、内存聚合 group-key 碰撞、脱敏契约 residual |
| P2      | 11   | profiler 误分类/吞错、lineage/manifest 正确性、reconciliation 边界、方言/诊断缺口、死码/卫生 |

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
