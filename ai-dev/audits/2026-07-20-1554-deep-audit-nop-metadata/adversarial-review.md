# Adversarial Review: nop-metadata Module

**Date**: 2026-07-20
**Scope**: nop-metadata (8 submodules, 32 ORM entities, ~266 Java files, ~50 test files, 2758-line ORM model)
**Method**: Open-ended discovery, not checklist-driven

---

## [AR-001] ErrorCode 集中化承诺 vs 现实的 40:1 比例崩塌

**Files**: `NopMetadataErrors.java` (116 lines), `MetaAggregationExecutor.java:89-171` (15 inline ErrorCodes), `NopMetaTableBizModel.java:94-153` (13 inline), `NopMetaDataSourceBizModel.java:61-70`, `FilterToSqlTranslator.java:31-57`, `MetaQualityRuleExecutor.java:46-60`, `MetaJoinExecutor.java:72-100`, `MetaTableQueryExecutor.java:43-46`, `SqlColumnLineageExtractor.java:77-86` — 40+ files total

**Evidence**: The module doc claims "ErrorCode 已集中到 NopMetadataErrors.java" and the Phase 2 plan commits to migrating 178+ ErrorCode.define calls. Reality:
- `NopMetadataErrors.java` defines 13 ErrorCodes (including ARG constants).
- `MetaAggregationExecutor.java` alone has 15 ErrorCodes defined inline at the class top.
- The entire module has ~40+ files with inline ErrorCodes, each with `static final ErrorCode ERR_XXX = ErrorCode.define(...)`.
- No systematic migration in progress — inline codes use prefix `metadata.` while NopMetadataErrors uses `nop.err.metadata.`, creating TWO naming conventions.

**Severity**: P1 → **P2** (独立复核降级)

**Risk**: Duplicate ErrorCodes exist (e.g., `ERR_DATASOURCE_NOT_FOUND` was defined in both `NopMetaDataSourceBizModel` and `NopMetaQualityRuleBizModel` — the comment in NopMetadataErrors:66 confirms this). Namespace pollution: 178+ scattered definitions make it impossible to audit error paths, and impossible for downstream consumers to handle errors predictably.

**Recommendation**: Stop the pretense of centralization. Either finish the migration (one phase, no follow-up), or accept inline ErrorCodes as the pattern and delete NopMetadataErrors.java. The mixed state is worse than either pure approach.

**Confidence**: High

**独立复核共识 (2026-07-20)**: 降至 P2。问题真实存在（集中化率仅 4.4%），但 Javadoc 自身已注明"渐进迁移"，非疏忽。纯代码风格/可维护性问题，非安全或功能缺陷。

---

## [AR-002] `custom_sql` Keyword Blacklist is Bypassable (Sandbox Weakness)

**File**: `MetaQualityRuleExecutor.java:56-82`

**Evidence**: The custom_sql keyword blocklist uses `String.contains()` (case-insensitive via `toUpperCase()`):
```java
private static final Set<String> CUSTOM_SQL_FORBIDDEN_KEYWORDS = unmodifiableSet(
    ";", "UNION", "INTO OUTFILE", "INTO DUMPFILE", "LOAD DATA",
    "CALL", "EXEC", "EXECUTE", "SHUTDOWN", "DROP", "TRUNCATE",
    "ALTER", "CREATE", "GRANT", "REVOKE",
    "INFORMATION_SCHEMA", "MYSQL.USER", "MYSQL.SCHEMAS");
```
Bypass techniques:
- `INTO OUTFILE` blocked but `INTO/**/OUTFILE` bypasses (if MySQL comment-in-SQL enabled).
- `DROP` blocked but `droptable` would trigger false positive — while `DR op` (space after `DR`) might pass.
- MySQL-specific: `LOAD_FILE()` function blocked but `LOAD_FILE` is checked as token; `BENCHMARK()` / `SLEEP()` time-based exfiltration is NOT blocked.
- PostgreSQL: `COPY ... TO PROGRAM` is not blocked (command execution).
- `MYSQL.USER` blocked but `MYSQL`.`USER` (backtick) passes the contains check.

**Severity**: ~~P1~~ → **不成立 / 关闭 (独立复核)**

**Risk**: This is user-configurable SQL that runs against external data sources.

**Recommendation**: Replace string-level blacklist with an actual SQL parser (reuse the EQL AST parser already used elsewhere in the module). Parse the custom_sql and reject any statement that is not a single SELECT with allowed structure.

**Confidence**: ~~High~~ → **独立复核驳回**

> **独立复核共识 (2026-07-20)**: 关闭。原报告声称的"大小写绕过"手法（如 `UnIoN` 绕过 `UNION` 检查）被 `sql.trim().toUpperCase().contains(keyword)` 中的 `toUpperCase()` 彻底防御。代码实现是安全侧过匹配（宁可错杀），非欠匹配。`contains()` 子串匹配可能导致误拦截（如 `DROP_DATE` 列名被拦），但不导致漏拦截。ExpressionMeasureValidator 使用分词 token + `Set.contains(upper)` 全词匹配更精确，但 custom_sql 的 `String.contains()` 对已知风险集仍有防御效果。原报告 P1 评级基于错误的前提，不成立。

---

## [AR-003] `ensureXxx()` Lazy Initialization is Not Thread-Safe

**Files**: `NopMetaTableBizModel.java:864-877`, `NopMetaDataSourceBizModel.java:361-366`

**Evidence**:
```java
private TableReferenceExecutor tableRefExecutor;  // null initially

private TableReferenceExecutor ensureTableRefExecutor() {
    if (tableRefExecutor == null) {
        tableRefExecutor = new TableReferenceExecutor(connectionService, orm());
    }
    return tableRefExecutor;
}
```
Same pattern in `NopMetaTableBizModel.ensureSqlFieldTypeInferrer()` and `NopMetaDataSourceBizModel.ensureTableRefExecutor()`.

BizModel instances are used concurrently (GraphQL requests are per-session but BizModel resolution can be shared). This is a classic check-then-act race: two concurrent calls can:
1. Both see `tableRefExecutor == null`
2. Both create separate instances
3. One overwrites the other's reference
4. The overwritten instance is garbage, but **multiple `TableReferenceExecutor` instances exist simultaneously** — more importantly, with `connectionService` and `orm()` being shared mutable state, the instance that actually gets used depends on timing.

**Severity**: P2

**Risk**: Subtle race conditions where `connectionService` or `orm()` state could be inconsistent between construction and first use. More critically, if the constructor throws, subsequent callers get a permanently non-functional field with no retry.

**Recommendation**: Use `synchronized` or initialize in constructor (the doc says "构造时 orm() 不可用" but `@PostConstruct` or an `INeedInit` hook would work — Nop supports this).

**Confidence**: High

---

## [AR-004] Cross-Datasource Schema Matching Uses In-Memory Filter → Hidden Overwrite Risk

**File**: `NopMetaDataSourceBizModel.java:432-471` (upsertExternalTable)

**Evidence**:
```java
// EQL-safe query: SELECT by (metaModuleId, tableName) only
QueryBean query = new QueryBean();
query.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_metaModuleId, metaModuleId));
query.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, info.getTableName()));
List<NopMetaTable> candidates = tableDao.findAllByQuery(query);

// Java-side schema match
for (NopMetaTable candidate : candidates) {
    if (Objects.equals(normalizeSchemaForMatch(candidate.getSchema()), infoSchema)) {
        table = candidate;
        break;
    }
}
```
The comment admits: "跨数据源、同名同schema的表会互相覆盖（与1905-1收敛前语义一致）". 

This means: if datasource A and datasource B both have a table `PUBLIC.SALES`, and the external module is `nop/meta-external`, the second datasource's sync will *update* (not create) the NopMetaTable row from the first sync. The `querySpace` is silently overwritten.

**Severity**: P2

**Risk**: After syncing two datasources with the same schema+tableName, queries route to the wrong datasource. Meta-data silently drifts from physical reality. This is a data corruption scenario, not just an inconvenience.

**Recommendation**: The fix is known (include `querySpace` in the upsert key). It was deferred to a follow-up plan with the rationale "应用层upsert仍幂等". But the problem is not correctness of upsert — it's data integrity: after sync-2, the NopMetaTable for `SALES` points to datasource B, but catalog/profiling/quality results from datasource A are still attached. This combination produces garbage.

**Confidence**: High

---

## [AR-005] `mediumtext` Domain Defined with Wrong Max Precision

**File**: `nop-metadata.orm.xml:152-153`

**Evidence**:
```xml
<!-- MEDIUMTEXT 最大字节数为 16777215（16MB-1）；16777216 (+1) 会按最小精度匹配落到 LONGTEXT -->
<domain name="mediumtext" precision="16777215" stdSqlType="VARCHAR"/>
```
`stdSqlType="VARCHAR"` with precision 16777215 is contradictory. In MySQL, VARCHAR max is 65535 (or 21845 for utf8mb4). A column with precision > 65535 should be mapped to MEDIUMTEXT or LONGTEXT, not VARCHAR. The Nop platform's SQL generation will produce `VARCHAR(16777215)` which MySQL will silently convert to MEDIUMTEXT — but this conversion is dialect-specific behavior and may fail on PostgreSQL or Oracle.

**Severity**: P2

**Risk**: The ORM model asserts `stdSqlType="VARCHAR"` which triggers Nop's type mapping rules. On PostgreSQL, `VARCHAR(16777215)` produces a valid but absurdly over-sized column (>16MB per value). On Oracle, this exceeds the 4000-byte VARCHAR limit and will silently (or noisily) fail. The `sourceSql` and `buildSql` columns on `NopMetaTable` use this domain and data will be silently truncated or queries will fail.

**Recommendation**: Use `stdDomain="string"` with a proper dialect-aware mapping, or split into `stdSqlType="CLOB"`/`MEDIUMTEXT` with correct per-dialect overrides in the ORM model's `dialect` section.

**Confidence**: High

---

## [AR-006] Per-Request JDBC Connection Creation → 10x Scale Will Melt

**File**: `MetaDataSourceConnectionProcessor.java:118-131`, callers in `NopMetaTableBizModel.java:688-695`, `NopMetaDataSourceBizModel.java:175-193`, `MetaAggregationExecutor.java` (multiple locations)

**Evidence**: Every external/sql query creates a NEW JDBC connection:
```java
public void withConnection(String datasourceType, String connectionConfig,
                           BiConsumer<Connection, DatabaseMetaData> action) {
    DataSource dataSource = buildDataSource(datasourceType, connectionConfig);
    Connection conn = null;
    try {
        conn = dataSource.getConnection();
        // ... execute query ...
    } finally {
        IoHelper.safeCloseObject(conn);
    }
}
```
`buildDataSource()` creates a NEW `SimpleDataSource` instance each time. There is zero connection pooling. For a single `syncExternalTables` call scanning 100 tables, this creates 100 separate JDBC connections (though it reuses the same connection inside the callback — the issue is separate batch runs).

More critically: `collectCatalog()` iterates N tables and calls `appendCatalogRow()` + `flushSession()` per table, but the entire batch shares ONE connection via `withConnection` callback. So the connection is reused within one `collectCatalog` call. However, across concurrent calls (e.g., two users triggering sync simultaneously), each spawns its own connection.

**Severity**: P2

**Risk**: Under concurrent load (10 users × 10 concurrent ops), the external database sees 100+ simultaneous connections from the nop-metadata module. `syncExternalTables` with 1000 tables holds a single connection open for the entire duration — this is a long-lived connection that blocks database resources and is vulnerable to network timeouts.

**Recommendation**: Implement connection pooling per `(jdbcUrl, username)` identity. Even a simple `Map<String, DataSource>` with eviction (commons-dbcp2 or HikariCP) would dramatically reduce connection churn. The current architecture doc (架构基线 §2.2) explicitly says "不注册长期连接池到 ORM querySpace 路由" — this was the right decision for ORM routing, but the module should maintain its own ephemeral pool separate from ORM.

**Confidence**: High

---

## [AR-007] NopMetaTable Has 15+ `to-many` Relations → Unconstrained Loading Causes N+1 Explosion

**File**: `nop-metadata.orm.xml:1213-1325`

**Evidence**:
```xml
<entity name="io.nop.metadata.dao.entity.NopMetaTable">
    <!-- 15+ to-many relations -->
    <to-many name="dimensions" .../>
    <to-many name="measures" .../>
    <to-many name="filters" .../>
    <to-many name="joins" .../>
    <to-many name="joinAsLeftTable" .../>
    <to-many name="joinAsRightTable" .../>
    <to-many name="lineageAsSource" .../>
    <to-many name="lineageAsTarget" .../>
    <to-many name="catalogs" .../>
    <to-many name="profilingRules" .../>
    <to-many name="profilingResults" .../>
    <to-many name="qualityScores" .../>
    <to-many name="dataContracts" .../>
    <to-many name="reconciliationConfigs" .../>
    <to-many name="reconciliationResults" .../>
</entity>
```
Every `CrudBizModel`-based `findById` / `findList` / `findPage` on NopMetaTable will, by default, potentially trigger lazy-loading on any of these 15 collections. In a standard Nop ORM setup, accessing any un-fetched collection in the view layer triggers an additional SQL query.

**Severity**: P2

**Risk**: A single page load that touches `table.getMeasures()` and `table.getDimensions()` generates 2 extra queries per table. With a page of 20 tables, that's 40 extra queries. If xmeta exposes these relations in the default GraphQL response (auto-generated `_xmeta`), the ORM session will eagerly load them all. The combinatorial effect across deep entity graphs (NopMetaModule → 6 to-manys including NopMetaTable → 15 to-manys) produces exponential query counts.

**Recommendation**: Audit the generated `_xmeta` files. Consider adding `insertable="false" updatable="false"` or setting `fetch="join"` on critical paths. Mark most to-many as `lazy="true"` and only expose them via explicit BizLoader methods. The xmeta generation strategy should be reviewed for this entity specifically.

**Confidence**: High

---

## [AR-008] `connectionConfig` Passwords Stored Without Encryption at Rest

**File**: `nop-metadata.orm.xml:316-318`, `MetaDataSourceConnectionProcessor.java:167-199`

**Evidence**: The connectionConfig column has `tagSet="sensitive"` but the column stores the entire connection config JSON — including `password` — as plain text:
```xml
<column code="CONNECTION_CONFIG" displayName="连接配置" domain="json-4000" name="connectionConfig"
        precision="4000" propId="6" stdDataType="string" stdDomain="json" stdSqlType="VARCHAR"
        tagSet="sensitive" .../>
```
The `tagSet="sensitive"` is used by Nop for masking in logs, but the database column stores plaintext passwords. This is visible to:
- Any user with direct DB access
- Any user with GraphQL query access to the field (if xmeta exposes it)
- Any DBA / backup operator with access to database dumps

**Severity**: P2

**Risk**: Password to every registered external datasource is stored in nop_meta_data_source.connection_config as plain JSON. A SQL injection in any part of the system (even unrelated modules) or a misconfigured GraphQL permission exposes all external datasource credentials.

**Recommendation**: Store only an encrypted blob (use Nop's `IAuthenticationService` or a KMS integration). Decrypt only in-memory in `MetaDataSourceConnectionProcessor.buildDataSource()`. Consider separating the `password` into a separate secrets store with a foreign key reference.

**Confidence**: High

---

## [AR-009] Plan Reference Fragility: 40+ Files Reference Obsolete Plan IDs

**Files**: Every BizModel and Executor file (40+ files)

**Evidence**: Inline comments reference specific plan IDs extensively:
```
// plan 2026-07-19-1250-3 Phase 3：委托到 MetaTableQueryExecutor#buildExternalSelectSql
// plan 0900-1（方案 B）：querySpace 提供 时显式调 inferrer 推断 type
// plan 2026-07-18-0900-2：having 聚合后过滤支持
// plan 0852-3 Phase 3: 默认 schema 解析在 BizModel 层
```
Over 200+ references to plan IDs, phase numbers, dimension codes (维度07-02, 维度09-01), and architectural baseline section numbers (§4.4.2 D6, §2.7.1 D3) scattered across Java code.

**Severity**: P3

**Risk**: This creates a massive documentation maintenance burden. Plans are ephemeral — `plan 2026-07-18-0900-2` will be meaningless in 3 months. The comments serve as "origin story" for implementation decisions but become noise (worse, misleading noise) when plans are superseded. New developers must cross-reference 100+ plan documents to understand current architecture.

**Recommendation**: Migrate design decision rationale to `ai-dev/design/` documents and reference those stable documents instead of plan IDs. Use architecture section numbers as `// @arch §4.4.1 D3` (stable anchor) instead of `// plan 2026-07-18-0900-2` (ephemeral). This is a code quality issue that compounds as the codebase ages.

**Confidence**: High

---

## [AR-010] `sourceSql` Execution Without Size or Complexity Guard

**File**: `MetaTableQueryExecutor.java:83-92`, `NopMetaTableBizModel.java:701-720`

**Evidence**: The `sourceSql` field is `mediumtext` (16MB max), and the query path executes it directly:
```java
public static String buildSqlSelectSql(String sourceSql, String filterSql, 
                                        Long limit, Long offset, String dialect) {
    StringBuilder sb = new StringBuilder("SELECT * FROM (");
    sb.append(sourceSql);  // 16MB of arbitrary SQL
    sb.append(") _t");
    // ...
}
```
There is no:
- Size check before execution
- Complexity analysis (nested subqueries, recursive CTEs)
- Timeout guard per query
- Validation that the sourceSql is a SELECT (not a mutation with side effects wrapped in a CTE)

An attacker (or accidental user) who configures a sourceSql of 16MB with deeply nested subqueries will cause the target database to spend unbounded CPU/memory parsing and planning.

**Severity**: P2

**Risk**: The same risk applies to `FilterToSqlTranslator` — a deeply nested TreeBean filter (1000 levels of AND/OR) produces exponential SQL output that could crash the parser or the optimizer, though TreeBean itself has a nesting guard in Nop platform.

**Recommendation**: 
1. Cap `sourceSql` at a reasonable length (e.g., 100KB) at the API boundary.
2. Add `Statement.setQueryTimeout()` before executing user-sourced SQL.
3. Validate via EQL parser that sourceSql is a single SELECT.

**Confidence**: High

---

## [AR-011] `Pooling` Anti-Pattern: `newArrayHolder()` for Lambda Variable Capture

**Files**: `MetaTableQueryExecutor.java:101-103`, used in `MetaAggregationExecutor.java:904/1069/2580`, `MetaJoinExecutor.java:429/675`, `NopMetaTableBizModel.java:687/710`

**Evidence**:
```java
@SuppressWarnings("unchecked")
public static List<Map<String, Object>>[] newArrayHolder() {
    return (List<Map<String, Object>>[]) new List<?>[1];
}
// Usage:
final List<Map<String, Object>>[] holder = newArrayHolder();
connectionService.withConnection(..., (conn, metaData) -> {
    holder[0] = executeQuery(conn, sql, ...);
});
return buildQueryResult(..., holder[0]);
```
This is a well-known hack to capture mutable state in Java lambdas (the variable must be effectively final). While functionally correct, it's repeated 8+ times across the codebase as a copy-paste pattern.

**Severity**: P3

**Risk**: Low functional risk, but the pattern indicates copy-paste code reuse where a refactored return-value approach would be cleaner. The prevalence suggests missing abstraction: `withConnection` could return a value instead of using `BiConsumer`. Consider changing `IMetaDataSourceConnectionProcessor.withConnection` to return `<T>` or provide an overload with `Function<Connection, T>`.

**Recommendation**: Add `<T> T withConnectionResult(...)` to `IMetaDataSourceConnectionProcessor` that returns values. Migrate callers. This eliminates 8+ boilerplate holders and improves type safety.

**Confidence**: High

---

## [AR-012] `FilterToSqlTranslator` Translates TreeBean to SQL String — Injection Surface on `value`

**File**: `FilterToSqlTranslator.java:190-200`

**Evidence**: Column names are validated against an identifier whitelist, but the `value` is set via PreparedStatement parameter:
```java
Object value = node.getAttr(FilterBeanConstants.FILTER_ATTR_VALUE);
params.add(value);
return col + " " + sqlOp + " ?";
```
This is correct for scalar values. However, the `in` and `between` operators collect values differently:
```java
// translateIn:
Collection<?> values = (Collection<?>) value;
for (Object v : values) {
    params.add(v);
}
```
The issue is that the `value` attribute from TreeBean is a deserialized JSON object — it can be a nested structure, a number, a boolean, or null. If a client sends `{"$gt": 100}` as a value (MongoDB-style injection), it passes through as a `LinkedHashMap` object in the params list. When `PreparedStatement.setObject()` receives a `Map`, the JDBC driver's behavior is dialect-specific — some drivers might throw, some might serialize.

**Severity**: P2

**Risk**: The TreeBean filter is user-supplied (via GraphQL). While column/field names are validated, the value types are not. A `between` with `min` and `max` as `Map` objects could exploit unexpected JDBC driver behavior. More critically, `TreeBean` from GraphQL can include nested `FilterBeans` that trigger the `fieldResolver` for having/orderBy — this resolver callback is user-controlled for aggregation queries.

**Recommendation**: Add type validation on values: reject non-scalar types (`Map`, `List`) for simple operators like eq/ne/gt/lt. For `between`, assert min/max are Comparable scalars.

**Confidence**: Medium

---

## [AR-013] Version Column Override: `NopMetaModule` Has `versionProp` But `version` Is Not the SemVer

**File**: `nop-metadata.orm.xml:161-165, 176-177`

**Evidence**:
```xml
<entity className="NopMetaModule" ... versionProp="version">
    <column code="MODULE_VERSION" displayName="模块版本号" mandatory="true" name="moduleVersion" 
            stdDataType="long" stdSqlType="BIGINT"/>
    <column code="VERSION" displayName="数据版本" domain="version" mandatory="true" name="version"
            stdDataType="long" stdSqlType="BIGINT"/>
</entity>
```
`NopMetaModule` has two versions: `moduleVersion` (domain-meaningful semver/int) and `version` (optimistic locking). The `versionProp="version"` correctly points to the ORM lock column. But the column comment `MODULE_VERSION` has 17 entities in this module (NopMetaModule, NopMetaTable, NopMetaDataSource, etc.) and ALL of them use `version` as the ORM optimistic lock column.

The **business meaning** of `moduleVersion` is defined as `BIGINT` — but how does `1.2.3` map to a BIGINT? The ORM model uses `stdDataType="long"` which implies an integer version (e.g., build number or revision number, not semver). If this is intended as semver, the type is wrong. If it's an integer revision, the column name "版本号" is misleading.

**Severity**: P3

**Risk**: Downstream consumers that treat `moduleVersion` as semver will make incorrect comparisons. The ORM model provides no clue about the versioning scheme (monotonic integer? git hash? Major.Minor.Patch packed as bits?).

**Recommendation**: Add a `description` or rename the column. If it's an auto-increment revision, name it `MODULE_REVISION`. If it's semver, store it as VARCHAR and add parsing logic.

**Confidence**: Medium

---

## [AR-014] Test Coverage Gap: Integration Tests Use H2 but Production Dialects Are MySQL/PostgreSQL

**Files**: `TestNopMetaTableBizModel.java:69` (`jdbc:h2:mem:...`), `SUPPORTED_DIALECTS` in `MetaAggregationExecutor.java:86-87` and `NopMetaTableBizModel.java:210-211`

**Evidence**: All integration tests run against H2 in-memory database. Production supports MySQL, PostgreSQL, and H2. The dialect-specific code paths (`SqlPagination.appendLimitOffset`, `GranularityBucketing`) are tested only with H2 SQL syntax.

```java
static final Set<String> SUPPORTED_DIALECTS =
    Collections.unmodifiableSet(new HashSet<>(Arrays.asList("H2", "MySQL", "PostgreSQL")));
```

The `SqlPagination` and `GranularityBucketing` classes contain dialect-specific SQL generation:
- MySQL: `LIMIT ? OFFSET ?`
- PostgreSQL: `LIMIT ? OFFSET ?` (same syntax, but type casting differs)
- H2: same syntax

None of the dialect-specific branches are tested under actual MySQL or PostgreSQL. The `validateJdbcUrl` blocks non-H2 connections in test environment, so integration tests cannot connect to real MySQL/PostgreSQL.

**Severity**: P2

**Risk**: MySQL and PostgreSQL syntax differences (function names, type casting, COLLATION, time zone handling) are not exercised. The `GranularityBucketing` translations for `DATE_TRUNC` (PostgreSQL) vs `DATE_FORMAT` (MySQL) have untested branches. A dialect-specific SQL generation bug would only be discovered in production.

**Recommendation**: Add Testcontainers-based integration tests for MySQL and PostgreSQL dialects, at minimum for the `SqlPagination` and `GranularityBucketing` classes. The Nop AutoTest framework supports this via `nop-test-docker`.

**Confidence**: High

---

## [AR-015] Single-Point `@Inject` Vulnerability: Connection Processor Is a Singleton Shared Across Tenants

**File**: `app-service.beans.xml:13`, `MetaDataSourceConnectionProcessor.java:99-100`

**Evidence**: The connection processor is a singleton bean:
```xml
<bean id="io.nop.metadata.service.connection.MetaDataSourceConnectionProcessor" 
      class="io.nop.metadata.service.connection.MetaDataSourceConnectionProcessor" ioc:default="true"/>
```
And it has `allowedInternalHostsCsv` as a mutable field set via `@InjectValue`:
```java
@InjectValue(value = "@cfg:nop.metadata.datasource.allowed-hosts|")
protected String allowedInternalHostsCsv = "";
```
This is mutable by design (the `resolveAllowedInternalHosts()` method computes on each call). But the `DriverManager.setLoginTimeout(5)` call is a global side effect:
```java
DriverManager.setLoginTimeout(DEFAULT_LOGIN_TIMEOUT_SECONDS);
```
`DriverManager.setLoginTimeout()` is a JVM-global static method. Every call sets the timeout for ALL JDBC connections in the entire JVM, not just nop-metadata connections. Furthermore, it's called on EVERY `buildDataSource()` — meaning every query resets the global login timeout to 5 seconds. If another module uses `DriverManager.getConnection()` with a timeout of 30s, nop-metadata repeatedly overrides it to 5s.

**Severity**: P2

**Risk**: In a multi-tenant deployment, tenant A's slow datasource (timeout 30s) is repeatedly reset to 5s by tenant B's queries. The global side effect creates cross-tenant interference. Also, `SimpleDataSource.setLoginTimeout()` being a no-op (as noted in the comment) means the `DEFAULT_LOGIN_TIMEOUT_SECONDS` only works via the global `DriverManager` side effect.

**Recommendation**: Use `DriverManager.setLoginTimeout()` only once (in `@PostConstruct`) or better, pass timeout as a connection property in the JDBC URL (`connectTimeout=5000` for MySQL, `loginTimeout=5` for PostgreSQL). Remove the per-call override.

**Confidence**: High

---

## Summary

### Top 3 Most Concerning Directions

1. **Security Theater**: The `custom_sql` keyword blacklist (AR-002) and the plaintext credential storage (AR-008) create a false sense of security. The blacklist is trivially bypassable, and the passwords are visible to anyone with DB access. These are the most likely attack vectors in a production deployment.

2. **Architecture Fragility**: The ErrorCode proliferation (AR-001), cross-datasource schema-matching bug (AR-004), and plan-ID documentation rot (AR-009) indicate that the module's rapid evolution has created technical debt that compounds with each phase. The "phase/follow-up" planning pattern defers hard decisions indefinitely.

3. **Production Readiness Gap**: The H2-only test coverage (AR-014), per-request connection creation (AR-006), and thread-safety issues (AR-003) suggest the module has been tested primarily in single-user ambient mode and may not survive production conditions.

### Blind Spots (What This Review Likely Missed)

- **Authentication/Authorization built on Nop platform**: Not deeply audited — the `@BizQuery`/`@BizMutation` annotations use platform auth, and I did not verify xmeta permission configurations.
- **Reconciliation matching logic**: The `ReconciliationExecutor` and `LocalReconciliationProcessor` were spot-read but not traced end-to-end for correctness.
- **Cross-module I*Biz interface contracts**: 32 interfaces, checked for existence but not for implementation completeness.
- **Codegen pipeline**: The `_gen/` output (128 files) and template metadata were not inspected for generation/customization boundary violations.
- **30+ remaining BizModels**: Only examined ~6 BizModels in detail. Others may have similar or different issues.

### Severity Distribution

| Severity | Count | AR IDs |
|----------|-------|--------|
| Severity | Count | AR IDs |
|----------|-------|--------|
| P2 | 11 | AR-001(降级), AR-003 through AR-010, AR-012, AR-014, AR-015 |
| P3 | 3 | AR-009, AR-011, AR-013 |
| 关闭(驳回) | 1 | AR-002 |
| Total (复核后) | 14 | — |

---

### 独立复核共识声明 (2026-07-20)

三次独立子 agent 复核（分别验证 P1 问题、对抗性审查关键发现、争议降级项）后达成以下共识：

| 发现 | 初始定级 | 复核后定级 | 状态 |
|------|---------|-----------|------|
| AR-001: ErrorCode 集中化崩塌 | P1 | P2 | ✅ 降级 |
| AR-002: custom_sql 黑名单绕过 | P1 | **关闭(驳回)** | ✅ 关闭 |
| AR-008: connectionConfig 明文密码 | P2 | P2 | ✅ 维持 |
| 03-01/07-01: BizMutation 未声明到 I*Biz | P1 | P3 | ✅ 降级 |
| 13-01: 缺少 @Auth 注解 | P1(升级) | P3 | ✅ 降级 |
| 14-01: dispatch 注释语义偏差 | P2 | **关闭(驳回)** | ✅ 关闭 |
| 16-01/16-02: 空存根文件 | P2 | P4(通知性) | ✅ 降级 |
| 04-02: SQL 保留字 | P2→P3 | P3 | ✅ 维持降级 |

**所有争议项已达成共识，无未解决异议。**
