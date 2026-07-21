> Audit Status: planned
> Audit Type: open-ended
> Mission: nop-metadata

# Open-Ended Adversarial Audit: nop-metadata

**Auditor**: opencode adversarial agent
**Date**: 2026-07-21
**Previous audits consulted**: 2026-07-19-1118-open, 2026-07-19-1118-multi, 2026-07-20-1554-deep, 2026-07-20-1816-open, 2026-07-20-1816-multi, 2026-07-21-2039-multi

**Deduplication check**: All findings below have been cross-referenced against ~100+ previously reported findings. None duplicate existing reports. Prior audits covered: SQL injection in schemaPattern (P0), JDBC URL SSRF/RCE (P0), ErrorCode centralization (P1-P2), API contract gaps (P1), ORM model issues (P2), search BizModel missing xmeta (P0), thread safety of ensureXxx (P2), lineage OOM (P1), connectionConfig plaintext (P1), and many more. The findings below address gaps not previously highlighted.

## Findings

### [AR-15] jdbcUrl credential exposure through error message templates

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/NopMetadataErrors.java:411-414`
- **Evidence fragment**:
  ```java
  ErrorCode ERR_DATASOURCE_JDBC_URL_BLOCKED =
          ErrorCode.define("nop.err.metadata.datasource-jdbc-url-blocked",
                  "JDBC URL is blocked by security policy ...: "
                          + "{jdbcUrl} reason={reason}", ARG_JDBC_URL, ARG_REASON);
  ```
  And in `MetaDataSourceConnectionProcessor.java:218-234`:
  ```java
  throw new NopException(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED)
          .param("jdbcUrl", jdbcUrl)
          .param("reason", "protocol not in whitelist ...");
  ```
- **Severity**: P1
- **Status**: The ErrorCode message template explicitly interpolates the full `{jdbcUrl}` into the error text. Although standard JDBC URL format separates credentials into `password` field, users commonly embed credentials inline (`jdbc:mysql://user:password@host:port/db`). When the URL is blocked by security policy (protocol/host/dangerous-param), the full URL — including any embedded credentials — is serialized into the NopException and can propagate to GraphQL error responses, API logs, and monitoring systems.
- **Risk**: Credential leakage through error channels. An attacker who triggers URL block errors (e.g., by configuring a data source with a blocked protocol or host) can harvest embedded passwords from error messages. Defeats the purpose of the AR-02 connectionConfig password redaction in the event system — the same credentials leak through a different vector.
- **Suggested fix**: Redact credentials from jdbcUrl before attaching it as an error param (e.g., strip `user:password@` segment, or replace with hashed/truncated version). Alternatively, omit `{jdbcUrl}` from the default error message template and include only `reason` — the blocked host/protocol is sufficient for diagnosis.
- **Confidence**: Likely
- **Discovery perspective**: 异常路径侦探 — error propagation of sensitive data

### [AR-16] Search index silent divergence: addToIndex/removeFromIndex swallow errors with no recovery path

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/search/NopMetaSearchService.java:26-47`
- **Evidence fragment**:
  ```java
  public void addToIndex(String entityType, String entityId, SearchableDoc searchableDoc) {
      if (searchEngine == null) {
          LOG.warn("searchEngine not available, skip addToIndex ...");
          return;
      }
      try {
          searchEngine.addDoc(TOPIC, searchableDoc);
      } catch (Exception e) {
          LOG.warn("addToIndex failed ...", e);
      }
  }
  ```
  Called from `NopMetaTableBizModel.save()` (and similar BizModel overrides):
  ```java
  NopMetaTable saved = super.save(data, context);
  // ... event publishing (in-transaction) ...
  searchService.addToIndex("MetaTable", saved.getMetaTableId(), toSearchableDoc(saved));
  // If addToIndex fails: logged at WARN, DB committed, search index MISSING
  ```
- **Severity**: P1
- **Status**: `addToIndex()` and `removeFromIndex()` in `NopMetaSearchService` silently catch all exceptions and log only a WARN. The calling BizModel save/delete overrides have zero feedback — the DB record is created/updated/deleted (transaction committed), but the search index update is silently dropped. There is no retry mechanism, no dead-letter queue, no periodic reconciliation between DB state and search index. The only recovery path is a manual rebuild via `rebuildSearchIndex()` mutation, which requires explicit human invocation.
- **Risk**: Permanent divergence between DB and search index. Over time, newly created or deleted entities accumulate missing/stale search entries. Users searching the catalog get incomplete results. In the worst case, a transient search engine outage (e.g., Elasticsearch cluster restart) silently desynchronizes the entire search index — and no one knows until someone runs a manual rebuild.
- **Suggested fix**: At minimum, propagate failures to the caller (throw the exception instead of swallowing). Better: implement an async retry queue or periodic reconciliation job. The current `rebuildSearchIndex()` is a good safety net but relies on operator awareness — a silent divergence won't trigger it.
- **Confidence**: Likely
- **Discovery perspective**: 异常路径侦探 + 代码生成受害者 — silent failure patterns

### [AR-17] testConnect exposes raw exception details in API response

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java:128-130`
- **Evidence fragment**:
  ```java
  } catch (SQLException e) {
      LOG.warn("testConnect failed for datasourceType={}", datasourceType, e);
      result.put("connected", false);
      result.put("error", e.toString());  // <-- full SQLException.toString() exposed
      return result;
  }
  ```
- **Severity**: P2
- **Status**: The `testConnect` mutation returns `e.toString()` verbatim in the response. `SQLException.toString()` includes the exception class name, message, SQL state, and vendor code. While the connection string itself has already passed security validation (protocol/host/driver whitelist), the error message may disclose database type details, driver version, network topology hints, or internal hostnames from the failure context. This information is returned directly in the GraphQL mutation response (accessible to any user with data-source mutation permissions).
- **Risk**: Infrastructure information disclosure through API responses. Can aid reconnaissance for targeted attacks after the initial connection validation is bypassed. Also inconsistent with the rest of the module's error handling convention (NopException with ErrorCode.param).
- **Suggested fix**: Return a sanitized error message `e.getMessage()` (or `"Connection failed"` fallback) with a structured error code rather than raw `e.toString()`. Log the full details server-side but keep the API response clean.
- **Confidence**: Likely
- **Discovery perspective**: 异常路径侦探 — error information leakage in API boundaries

### [AR-18] Private @Deprecated wrappers not cleaned up after migration

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaTableBizModel.java:715-740`
- **Evidence fragment**:
  ```java
  /**
   * @deprecated plan 2026-07-19-1250-3 Phase 3：委托到 {@link MetaTableQueryExecutor#buildExternalSelectSql}。
   * 保留本方法仅为 BizModel 内部调用兼容；后续 slice 整体迁移后移除。
   */
  @Deprecated
  private static String buildExternalSelectSql(...) {
      return MetaTableQueryExecutor.buildExternalSelectSql(tableName, columns, filterSql, limit, offset, dialect);
  }
  ```
  Same pattern for `buildSqlSelectSql()` (line 726) and `executeQuery()` (line 736).
- **Severity**: P3
- **Status**: Three private methods are marked `@Deprecated` but do nothing except delegate to the same-named static methods on `MetaTableQueryExecutor`. Since they are `private` and only called from within `NopMetaTableBizModel`, the `@Deprecated` annotation is semantically meaningless to external consumers. If the migration to `MetaTableQueryExecutor` is complete, these wrappers should be removed. Their continued existence suggests the cleanup was deferred and never revisited.
- **Risk**: Low individual impact, but signals incomplete refactoring discipline. New team members might replicate the pattern rather than calling `MetaTableQueryExecutor` directly. Accumulated technical debt.
- **Suggested fix**: Remove the three private wrapper methods and update the two call sites (`queryExternalData` line 658, `querySqlData` line 680) to call `MetaTableQueryExecutor.buildExternalSelectSql(...)`/`buildSqlSelectSql(...)`/`executeQuery(...)` directly.
- **Confidence**: Likely
- **Discovery perspective**: 死代码清道夫 — stale migration artifacts

### [AR-19] DriverManager.setLoginTimeout called per-connection attempt overrides JVM-global state

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java:189-191`
- **Evidence fragment**:
  ```java
  try {
      DriverManager.setLoginTimeout(DEFAULT_LOGIN_TIMEOUT_SECONDS);
  } catch (SecurityException se) {
      LOG.warn("DriverManager.setLoginTimeout denied by security manager", se);
  }
  ```
  Called inside `buildDataSource()` which is invoked on every `withConnection()` and every `testConnect()` call.
- **Severity**: P3
- **Status**: `DriverManager.setLoginTimeout()` is a JVM-global static method that affects all subsequent `DriverManager.getConnection()` calls across the entire JVM, not just this processor's connections. It is called redundantly on every connection attempt. The Javadoc on `SimpleDataSource.setLoginTimeout` already documents it as a no-op, confirming this workaround is intentional. However, the side effect on global state is unnecessary — setting it once at bean initialization (`@PostConstruct`) or in the bean constructor would suffice.
- **Risk**: Low in practice (always set to the same 5s value). But if any other part of the system expects a different DriverManager login timeout, this per-call override silently changes it. Also a minor concurrency concern: two concurrent `buildDataSource` calls each call `setLoginTimeout`, though with the same value it's idempotent.
- **Suggested fix**: Move `DriverManager.setLoginTimeout(DEFAULT_LOGIN_TIMEOUT_SECONDS)` to `@PostConstruct` or to the static initializer. No need to set it on every connection attempt.
- **Confidence**: Likely
- **Discovery perspective**: IoC 侦探 — unnecessary global state mutation

## Unconfirmed observations

- **Extract host from jdbcUrl DNS rebinding**: The `extractHost()` method in `MetaDataSourceConnectionProcessor.java:247-261` parses the host from jdbcUrl. However, the actual TCP connection targets the resolved IP address. DNS rebinding between validation and connection could bypass host allowlists. This is a known SSRF attack pattern. However, this is an extremely narrow attack surface (requires DNS control over the target hostname) and common to any URL-based allowlist. Not reported as a finding because it's an inherent limitation of hostname-based allowlists, not specific to this codebase.

## Total Assessment

### Most notable 1-2 concerns

1. **Search index vs DB divergence is a ticking data consistency bomb** (AR-16). The module has a large, well-designed CRUD layer that captures events, maintains lineage, profiles data — but the search index path is the weakest link. A transient search engine outage permanently desynchronizes the index. Given the index is the primary discovery mechanism for the catalog, this is a meaningful operational risk that needs either retry logic or periodic reconciliation.

2. **Defense-in-depth gaps in credential protection** (AR-15). The event system (AR-07) carefully redacts sensitive columns with tagSet-based+fallback detection. But the connection processor's own error paths leak the same credentials through error message templates. The two subsystems are not aligned on what constitutes a "safe" scope for jdbcUrl. An attacker who can't read the connectionConfig directly (access controlled) can still infer credentials by triggering URL validation errors.

### Blind spots

This audit may have missed:
- Actual test coverage quality (did not run tests, only read source code)
- Generated file correctness (the `_gen/` directory and codegen templates were not inspected in depth)
- XMeta files for search BizModel (previous audits already flagged it as missing)
- Cross-version compatibility with nop-sys-dao or nop-core changes
- Performance profiling of the lineage BFS graph traversal for large graphs

### Severity distribution

| Severity | Count | Main categories |
|----------|-------|-----------------|
| P0 | 0 | — |
| P1 | 2 | Credential leakage in error templates (AR-15), Search index silent divergence (AR-16) |
| P2 | 1 | testConnect raw exception leak (AR-17) |
| P3 | 2 | Private @Deprecated wrappers not removed (AR-18), DriverManager global state per-call (AR-19) |
