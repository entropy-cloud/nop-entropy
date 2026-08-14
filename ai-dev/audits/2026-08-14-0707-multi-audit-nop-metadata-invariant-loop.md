> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-metadata-invariant-loop
> Planned Into: `ai-dev/plans/2026-08-14-0707-1-...`（F1、F2、AR-04 来自 open-audit 同批）、`2026-08-14-0707-2-...`（F4）、`2026-08-14-0707-3-...`（F3）。P2（F5–F19）入 mission roadmap Follow-up Backlog。

# Multi-Dimensional Audit — nop-metadata (Invariant-Loop Cycle re-audit)

- **Audit date**: 2026-08-14
- **Scope**: `nop-metadata/` full module group (8 submodules, 39 entities, ~283 main Java + 121 tests), focused on code, config, tests, and public contracts (exports / API surface), cross-referenced against `docs-for-ai/03-modules/nop-metadata.md`.
- **Method**: `ai-dev/skills/deep-audit-prompts.md` (shared prefix + per-dimension bodies). Parallel deep-dive subagents across dims 03/04/05/06/07/09/11/13/16/20/21, followed by **independent live-code verification of every P0/P1 finding by the lead agent** (code traces re-read directly; not trusted from subagent output).
- **Invariant baseline (re-verified this audit)**: the 4 established invariant gates — `check-silent-swallow.mjs`, `check-orm-unique-key-constraint.mjs`, `check-sensitive-literal-leak.mjs`, and the INV-LIMIT JUnit — report **0 hits** when run explicitly. This audit therefore focused on (a) **new** issues outside the 4 invariants and (b) **gaps in the invariant enforcement itself**. See F3.

## Severity distribution

| Severity | Count | Must-fix |
|----------|-------|----------|
| P0 | 2 | yes |
| P1 | 2 | yes |
| P2 | 15 | triaged to backlog |

Downstream remediation is driven by P0+P1 only (P2 recorded for backlog triage).

---

## P0 — Blocking (must fix)

### [F1] HAVING SQL injection via forgeable `havingExprResolved` TreeBean attr (MA7.1-01 regression)
*[dims 13+16; confirmed end-to-end by lead agent]*

- **Files**:
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/AggregationHelper.java:301-311` (trust gate)
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/MetaAggregationExecutor.java:156-180` (marker setter — never clears forged attrs)
  - `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/AggregationHelper.java:598-604` (raw concat into HAVING SQL)
  - `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/TreeBean.java:400-415` (client-injectable attrs)
- **Evidence**:
  ```java
  // AggregationHelper.nameResolverFor — MA7.1-01 "fix":
  String expr = nameToExpr.get(name);
  if (expr != null) { ... return expr; }
  if (Boolean.TRUE.equals(node.getAttr(MetaAggregationExecutor.HAVING_EXPR_RESOLVED_ATTR))) {
      return name;                 // raw client-supplied name -> SQL text
  }
  throw ...ERR_AGGR_HAVING_UNKNOWN_NAME...;

  // MetaAggregationExecutor.preprocessHavingArithmetic — only sets marker on `expr` leaves,
  // never clears existing attrs on other leaves (line 173-179 recurse; leaf w/o expr -> return, no sanitize):
  Object exprAttr = having.getAttr(HAVING_EXPR_ATTR);
  if (exprAttr != null && !exprAttr.toString().isEmpty()) { ... having.setAttr(HAVING_EXPR_RESOLVED_ATTR, TRUE); return; }
  // ... recurse; a plain leaf {type:"gt",name:"<injection>",havingExprResolved:true} is untouched

  // buildExternalAggregationSql: resolver output concatenated raw:
  FilterToSqlTranslator.TranslatedFilter hf = ctx.filterTranslator().translate(having,
          nameResolverFor(nameToExpr, table, measureNames, dimensionNames, "HAVING"));
  sql.append(" HAVING ").append(hf.getSql());

  // TreeBean.createFromJson — every JSON key becomes an attr:
  for (Map.Entry<String,Object> entry : map.entrySet()) { ... bean.setAttr(name, entry.getValue()); }
  ```
- **Severity**: **P0** — SQL injection restoring the exact vector MA7.1-01 (a prior P0) was meant to close; reachable with query-level (read) permission on `queryAggregation` via 6 aggregation processors.
- **Current state**: MA7.1-01 replaced "pass-through on whitelist miss" with "pass-through only if marked `havingExprResolved`". But the marker lives in `TreeBean.attrs`, which `TreeBean.createFromJson` (TreeBean.java:411) + `@JsonAnySetter` populate from **arbitrary client JSON keys**. A client can send `having={"type":"gt","name":"(SELECT password FROM users LIMIT 1)","value":1,"havingExprResolved":true}`; `preprocessHavingArithmetic` recurses to that leaf, finds no `expr` attr and no children, returns without clearing the forged marker; `nameResolverFor` then returns the raw injection `name` into the HAVING clause. Lead agent traced every hop in live code (resolver at AggregationHelper:303-304; raw concat at AggregationHelper:603; createFromJson at TreeBean:411).
- **Risk**: Arbitrary scalar subquery injection into HAVING across entity/external/sql/join/mixed aggregation paths (read sensitive data, version probing, blind injection). The defense's trust marker is unforgeable-in-name only.
- **Recommendation**: Stop using a client-controllable `TreeBean.attr` as a trust credential. Either (a) clear `HAVING_EXPR_RESOLVED_ATTR` on **every** leaf before the `expr`-path sets it (defense-in-depth), or better (b) pass resolved-leaf identity out-of-band (`Set<TreeBean>` / wrapper type) instead of via attrs. Add adversarial test: construct a `TreeBean` with `setAttr("havingExprResolved", true)` + SQL payload `name`, assert `nameResolverFor`/`buildExternalAggregationSql` throws `ERR_AGGR_HAVING_UNKNOWN_NAME`. Verify live GraphQL payload before closing.
- **Confidence**: Certain at the code level (all 4 hops read directly). The only residual unknown is whether any GraphQL-layer schema validation strips unknown attrs before TreeBean binding — Nop's standard TreeBean argument binding does not, so this is expected reachable. **Mandatory live payload verification before closure.**
- **False-positive exclusion**: Not INV-SILENT-SWALLOW scope (behavior, not catch blocks). Not a platform standard pattern (TreeBean attrs are a generic Nop capability, but using one as a security trust marker without sanitization is a nop-metadata design defect). Not the same finding as MA7.1-01 (that fixed the "miss→pass-through" fallback; this is a newly-introduced bypass via forgeable credential).

### [F2] Multi-host JDBC URL SSRF bypass (only first host is checked)
*[dims 13+16; confirmed by lead agent]*

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java:269-310` (`extractHost`) + `:216-247` (`validateJdbcUrl`)
- **Evidence**:
  ```java
  int slash = rest.indexOf('/');
  int comma = rest.indexOf(',');        // comma treated as hostPort terminator
  int q = rest.indexOf('?');
  int end = minPositive(minPositive(slash, comma), q);
  String hostPort = end > 0 ? rest.substring(0, end) : rest;   // only "h1:3306" kept
  // ... only this first segment reaches HostSecurityUtil.isInternalHost;
  // the full URL (incl. h2,h3,...) is passed unchanged to the JDBC driver
  ```
- **Severity**: **P0** — bypasses the documented fail-closed "internal hosts denied by default" SSRF contract; MySQL Connector/J and PostgreSQL JDBC officially support comma-separated multi-host failover/loadbalance URLs (`jdbc:mysql://h1,h2:port/db`) and `address=(host=...)` equivalence, so the driver connects to the unchecked second host.
- **Current state**: `extractHost` truncates `hostPort` at the first comma, so `validateJdbcUrl` only checks the first host. A URL like `jdbc:mysql://example.com,169.254.169.254:3306/db` passes validation (first host external) yet the driver will also reach the AWS metadata IP / RFC1918 internal host. `TestMetaDataSourceConnectionSecurity` (25 cases) covers IPv6/userinfo/IP-notation variants but has **zero** multi-host cases.
- **Risk**: A user able to configure a `NopMetaDataSource` (write permission on data-source entity) can bypass internal-host protection to reach cloud-metadata / internal DB services via `testConnection` / `syncExternalTables` / `collectCatalog` / any `withConnection` entry — potential cloud-credential theft.
- **Recommendation**: Make `extractHost` return a host list (and handle `address=(host=...)`/`[host=...]` forms); `validateJdbcUrl` must run `HostSecurityUtil.isInternalHost` against **every** host and reject if any is internal-and-unallowlisted. Add adversarial tests: `jdbc:mysql://good.com,169.254.169.254:3306/db` and `(host=external),(host=127.0.0.1)` must be rejected.
- **Confidence**: Certain (trace read directly; multi-host URL syntax is official driver documentation).
- **False-positive exclusion**: Not MA7.2-01 (userinfo single-host parsing, already fixed). This is a distinct blind spot in the comma-truncation logic.

---

## P1 — Material (must fix)

### [F3] INV-LIMIT enforcement test excluded from default surefire — invariant ratchet defeated in default CI
*[dim 16; confirmed by lead agent]*

- **Files**:
  - `nop-metadata/nop-metadata-service/pom.xml:142-148`
  - `nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/invariant/TestLimitNegativeValueInvariant.java`
- **Evidence**:
  ```xml
  <!-- plan 2026-08-13-1930-2 Workstream B: limit 守护测试类默认从 surefire 排除，
       由单独命令调用产出 limit red list（非零退出码 = 存在未 reject 负值的方法）。
       该排除为持久配置，确保默认构建恒绿；TestLimitTargetSetCompleteness 仍在默认集合中运行。 -->
  <excludes>
      <exclude>**/invariant/TestLimitNegativeValueInvariant.java</exclude>
  </excludes>
  ```
- **Severity**: **P1** — contract drift: the mission's "4 invariant gates live 0 hits" closure claim is only true for **explicit** runs; the INV-LIMIT *enforcement* test (the one that actually asserts negative `limit` throws the ErrorCode) is excluded from default `./mvnw test`, so default CI does not guard against a silent-clamp regression.
- **Current state**: Only `TestLimitTargetSetCompleteness` runs by default — it checks the method *table* is complete (4 limit methods registered) but never invokes the limit handler. `TestLimitNegativeValueInvariant` (which would fail if any method silently clamps a negative limit) is persistently excluded "so the default build stays green". Any PR reintroducing `limit < 0 ? 0 : limit` (or `Math.abs(limit)`) into `normalizeQueryLimit`/`normalizeJoinQueryLimit`/`searchMetadata` merges green.
- **Risk**: The INV-LIMIT anti-regression guarantee — the core output of Cycle 1 — is "documented but not enforced" in the default build path. The ratchet only bites on manual/explicit runs, undermining the invariant-loop's prevention promise.
- **Recommendation**: Make `TestLimitNegativeValueInvariant` part of default surefire (fix whatever red it surfaces rather than hiding it), OR add an explicit CI workflow step that runs it fail-fast on every PR. At minimum, qualify the mission's "0 hits" closure record with "INV-LIMIT enforcement is opt-in".
- **Confidence**: Certain (pom.xml + test class comment double-confirmed).
- **False-positive exclusion**: Not "weak test protection" in the usual sense — the test exists and is strong; the defect is that it is disabled in the default build, weakening the ratchet contract.

### [F4] `selection` parameter silently dropped in queryTableData / queryJoinData / queryAggregation
*[dim 07; confirmed by lead agent]*

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaTableBizModel.java:229-304` (selection declared at :233, :260, :289)
- **Evidence**:
  ```java
  public QueryTableDataResultDTO queryTableData(...,
          @Optional @Name("selection") FieldSelectionBean selection,
          IServiceContext context) {
      ...
      result.setItems(queryAction.queryEntityData(table, filter, limit, offset, daoProvider(), orm()));
      // 'selection' declared but NEVER passed to queryEntityData / queryExternalData / querySqlData
  }
  // identical silent-drop in queryJoinData (:260 -> executeJoin) and queryAggregation (:289 -> executeAggregation)
  ```
- **Severity**: **P1** — public API contract drift: a documented parameter is accepted and discarded; clients relying on field filtering get full rows.
- **Current state**: All three BizQuery methods declare `FieldSelectionBean selection` (and it appears in the `INopMetaTableBiz` interface) but the parameter is never read in the method body nor forwarded to any executor. Contrast with `CrudBizModel` where the same parameter genuinely drives `fetchResultWithSelection` (13+ usages). This is distinct from the documented P2-24 carve-out (which covers the `items: List<Map<String,Object>>` *return type*, not a silently-ignored *parameter*).
- **Risk**: (a) Clients believe field selection is in effect but receive all columns (wide tables / tables with sensitive columns return everything); (b) wasted serialization bandwidth; (c) silent parameter swallowing is the same anti-pattern family as the silent-swallow invariant, just on inputs.
- **Recommendation**: Either consume `selection` (forward to the executors / apply post-fetch key filtering on the result Maps) or explicitly document that `selection` is currently a no-op and remove/deprecate it from the public signature. Do not silently accept it.
- **Confidence**: Certain (grep confirms `selection` appears only as 3 parameter declarations in this file; no body reference).
- **False-positive exclusion**: Not the GraphQL-reflection "xbiz vs BizModel signature drift" pattern — the parameter is real in the reflective signature; the defect is it is never read after binding.

---

## P2 — Backlog triage (recorded, no standalone remediation plan)

### Security hardening (MetaDataSourceConnectionProcessor / HostSecurityUtil / MetaQualityRuleExecutor)

### [F5] jdbcUrl dangerous-param blocklist missing class-loading params (socketFactory / statementInterceptors / sslFactory)
- **File**: `MetaDataSourceConnectionProcessor.java:59-72` (`DANGEROUS_URL_TOKENS`)
- **Severity**: P2 — `socketFactory`/`statementInterceptors`/PG `sslFactory`/`options=` trigger reflective class loading; RCE-chain potential depends on deployment classpath. driverClassName allowlist mitigates but the "explicit safe-by-default" promise is incomplete.
- **Evidence**: blocklist enumerates 13 tokens; omits `socketfactory`, `statementinterceptors`, `detectcustomcollatz`, `sslfactory`, PG `options=` GUC.
- **Recommendation**: add the missing tokens, or switch to a connection-param allowlist. *Justification for P2 not P1: exploitation requires an abused class already on the deployment classpath; no confirmed gadget chain in this module.*

### [F6] redactJdbcUrl incomplete for passwords containing `@`
- **File**: `MetaDataSourceConnectionProcessor.java:85-86,257-260` (`CREDENTIAL_PATTERN = (://)([^:@/]+)(?::[^@/]*)?@`)
- **Severity**: P2 — when a password contains `@`, the regex stops at the first `@` and the password tail leaks into redacted error messages (e.g. `user:p@ss@host` → `ss@host`). *Justification: limited partial-credential leak in error responses only; not a full bypass.*
- **Recommendation**: redact using `lastIndexOf('@')` to find the userinfo boundary (matching extractHost's own logic at :283), then substring-replace.

### [F7] Empty/malformed authority fail-open in host validation
- **File**: `MetaDataSourceConnectionProcessor.java:269-310,240-246`
- **Severity**: P2 — `jdbc:mysql:///db` and `jdbc:mysql://:3306/db` yield hostPort `"/db"` / `":3306"`, which `isInternalHost` reports as external → silently allowed. Currently harmless because drivers reject empty hosts, but it is "lucky fail-closed" not "enforced fail-closed". *Justification: no current driver reaches localhost on empty host; defense-in-depth gap, not active bypass.*
- **Recommendation**: when `extractHost` returns a non-host-shaped string, `validateJdbcUrl` should explicitly reject (`ERR_DATASOURCE_JDBC_URL_BLOCKED`, reason="host unparseable").

### [F8] isInternalIpv6Literal triggers DNS (violates "no DNS" class-doc promise)
- **File**: `HostSecurityUtil.java:285-309` + `:101-103`
- **Severity**: P2 — any `:`-containing input routes to `isInternalIpv6Literal` which calls `InetAddress.getByName(h)` without the charset prefilter that `isIpLiteral` (:130-151) has; a crafted URL can trigger a DNS lookup, contradicting the class javadoc "纯确定性解析，不触发 DNS". *Justification: DNS signal/timing channel, not SSRF (no TCP); narrow reachability.*
- **Recommendation**: add the same `[0-9a-fA-F:.]` + ≥2-colons charset guard as `isIpLiteral` before calling `getByName`.

### [F9] custom_sql blocklist missing PostgreSQL `DO` / `WITH` (CTE)
- **File**: `MetaQualityRuleExecutor.java:76-98` (`CUSTOM_SQL_FORBIDDEN_WORDS`)
- **Severity**: P2 — PG `DO $$ ... $$` executes PL/pgSQL (file/network access if PL installed); `WITH` recursive CTE is a data-exfil surface; `PG_CATALOG`/`PG_SLEEP` metadata/probe surface. *Justification: requires PL/pgSQL installed + granted; same family as MA7.1-02 coverage follow-up.*
- **Recommendation**: add `DO`, `WITH`, `PG_CATALOG`, `PG_STAT_USER_TABLES`, `PG_SLEEP`; consider DRY-syncing with `ExpressionMeasureValidator.FUNCTION_BLACKLIST`.

### ORM model (nop-metadata/model/nop-metadata.orm.xml)

### [F10] NopMetaModelChangedEvent missing `entityId` in its audit-log index
- **File**: `nop-metadata/model/nop-metadata.orm.xml:2863-2923`
- **Severity**: P2 — index is `(entityType,changeTime)`; the natural "list changes for entity X" query (`WHERE entityType=? AND entityId=?`) degrades as the audit log grows. Sibling entities (NopMetaQualityRule, NopMetaTagLabel) carry `(entityType,entityId)` indexes. *Justification: perf, not correctness.*
- **Recommendation**: extend the index to `(entityType, entityId, changeTime)`.

### [F11] Four soft-FK columns unindexed (sourceModuleId / baseEntityId / entityFieldId ×2)
- **File**: `nop-metadata/model/nop-metadata.orm.xml:1065,1287,1467,1545`
- **Severity**: P2 — reverse-lookup queries (entity→referencing logical tables, field→referencing dimensions/measures, module→derived domains) have no index. *Justification: perf on reverse cascades.*
- **Recommendation**: add non-unique indexes on these `varchar(32)` FK-like columns.

### [F12] NopMetaQualityResult.runId not in any leading index column
- **File**: `nop-metadata/model/nop-metadata.orm.xml:2086-2108`
- **Severity**: P2 — `runId` is the 2nd column of the UK `(checkpointId,runId,qualityRuleId)`; "fetch all results for batch X" (by runId alone) cannot use the UK prefix and full-scans. *Justification: perf for batch-replay pages.*
- **Recommendation**: add a standalone `(runId)` index.

### [F13] `meta/quality-trend-direction` dict missing the "retained for Java constants" comment its siblings have
- **File**: `nop-metadata/model/nop-metadata.orm.xml:111-115`
- **Severity**: P2 — two sibling dicts (checkpoint-action-type, reconciliation-status) carry a comment explaining they are retained because Java code references their values; this one (used by `MetaQualityScorer` via `_NopMetadataCoreConstants`) lacks it and risks accidental deletion in a "remove unused dicts" sweep. *Justification: maintenance hygiene.*
- **Recommendation**: add the matching retention comment.

### API surface / docs / code hygiene

### [F14] KeyValueDTO is a dead DTO (zero production references)
- **File**: `nop-metadata/nop-metadata-api/src/main/java/io/nop/metadata/api/dto/KeyValueDTO.java:1-40`
- **Severity**: P2 — only reference is a serialization smoke test (`TestNopMetaDtoResults.java:89`); it inflates the public API surface and the "31 @DataBean DTOs" doc count (the other 30 all have ≥1 production reference). *Justification: doc-contract accuracy + surface bloat.*
- **Recommendation**: delete it (re-grep repo-wide first) or mark deprecated and correct the doc count to 30.

### [F15] Doc I*Biz method list incomplete for 5 quality/contract/profiling interfaces
- **File**: `docs-for-ai/03-modules/nop-metadata.md:168-169`
- **Severity**: P2 — the doc lists explicit method names for the first 4 interfaces (:164-167) but downgrades the last 5 to group labels ("质量规则/检查点/评分"). Code/interface alignment is fine; the doc is just less precise than its own earlier lines. *Justification: doc granularity inconsistency.*
- **Recommendation**: expand :168-169 to the same explicit method-name style.

### [F16] Dead code: NopMetaQualityRuleBizModel.resolveDataSourceOrThrow
- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaQualityRuleBizModel.java:312-328`
- **Severity**: P2 — private method with zero callers in its file; misleads maintainers into thinking DISABLED-datasource guarding happens here (it doesn't on the single-rule path). *Justification: maintenance confusion.*
- **Recommendation**: delete it.

### [F17] safeProductName duplicated 7× (incl. 1 dead instance)
- **Files**: `NopMetaProfilingRuleBizModel.java:190-198` (dead), `NopMetaQualityRuleBizModel.java:395`, `NopMetaDataSourceBizModel.java:456`, `NopMetaTableQueryAction.java:237`, `TableReferenceExecutor.java:138`, `SqlViewFieldTypeInferrer.java:196`, `AggregationHelper.java:469` (already public reusable)
- **Severity**: P2 — 7 identical private copies with diverging error codes; one copy is dead code. *Justification: DRY/maintenance, error-code drift risk.*
- **Recommendation**: delete the dead instance; route the other 5 through `AggregationHelper.safeProductName`.

### Test effectiveness (dims 16+21)

### [F18] Hollow test: TestNopMetaDtoResults.testDtoJsonRoundTripAllTypes
- **File**: `nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestNopMetaDtoResults.java:87-96`
- **Severity**: P2 — 6 empty DTOs passed through `JsonTool.stringify` then only `assertNotNull`; `stringify` essentially never returns null (even `{}` qualifies), so this catches no regression. The sibling `testProfileResultDtoFields` (:44-66) is a real round-trip test. *Justification: weak protection, not absence of test.*
- **Recommendation**: convert to a real round-trip (stringify → parseBeanFromText → field assertEquals) or delete.

### [F19] TestAllEntitiesHaveBizModels uses a hardcoded entity list
- **File**: `nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestAllEntitiesHaveBizModels.java:75-117`
- **Severity**: P2 — the "every entity has a BizModel" guard uses a 39-line hand-written list; adding a 40th entity without editing this list keeps the test green and defeats the guard. *Justification: guard-maintainability defect.*
- **Recommendation**: reflectively scan `io.nop.metadata.dao.entity` for `NopMeta*` classes (cf. `TestLimitTargetSetCompleteness.countInDir`).

---

## Dimensions verified clean (with evidence)

- **Dim 03/20 (API surface & cross-module contract)**: All 39 entity BizModels `extends CrudBizModel<T> implements INopMetaXBiz`; every custom `@BizQuery`/`@BizMutation` method declared in the interface matches the BizModel implementation (param names/types/return/`@Optional`). `NopMetaSearchBizModel` Pseudo-BizModel (no interface) matches the documented carve-out. `_service.beans.xml` registers all 39 BizModels with correct interface types; `app-service.beans.xml` service beans map to real classes. 30/31 DTOs have production references (F14 is the exception). limit negative-rejection verified live at all 4 entry points.
- **Dim 04/05/06 (ORM/codegen/delta)**: Source model ↔ generated `_app.orm.xml` match exactly (39 entities / 745 columns / 37 unique-keys / 63 indexes / 88 relations). No hand-edit traces in `_`-prefixed artifacts. No `_delta` directory exists (no Delta customization in this module). All retention files use `x:extends`. Findings are index-coverage/doc P2s only.
- **Dim 09 (error handling)**: Zero bare `RuntimeException`/`IllegalArgumentException`/`UnsupportedOperationException` in service/core hand-written code (all in `src/test` only). `NopMetadataErrors` uses `ErrorCode.define("nop.err.metadata.*")` centrally; public-API exceptions carry ErrorCode + `.param(...)`; module internals use `NopMetadataException` with English messages. Two-tier policy followed.
- **Dim 11 (XMeta/BizModel alignment)**: `NopMetaDataSource.connectionConfig`/`connectionConfigComponent` carry `tagSet="sensitive"` + `published=false` + `insertable/updatable/queryable/sortable=false` (dual GraphQL+CRUD shielding). No `@BizLoader` methods exist in the module. Sampled xmeta field permissions are reasonable.
- **Dim 16/21 (coverage where strong)**: `AggregationHelper`, `MetaQualityRuleExecutor` (regex-dialect + custom_sql sandbox), `HostSecurityUtil` (25 vectors), `SqlColumnLineageExtractor`, `MetaJoinExecutor` truncation/overflow, `TestNopMetaTableQueryBizModel` (real H2 data + row assertions) are well-covered, anti-hollow tests. Gaps are the adversarial cases noted in F1/F2/F18/F19.

## Audit blind spots (self-assessment)

- F1 reachability depends on the GraphQL endpoint's exact TreeBean binding path; a live payload run (constructing the GraphQL request) was not executed — only static code tracing through `TreeBean.createFromJson` + the resolver + raw SQL concat. **Closure must gate on a live payload test.**
- F2 host-list behavior was traced statically; driver multi-host connection ordering was not executed against a real MySQL/PG driver.
- Style/checkstyle mechanical issues were not exhaustively scanned (no checkstyle baseline run; per skill, these are P2-only and not the focus of an invariant-loop re-audit).
- Frontend (web pages / view.xml) contracts were not deeply audited (this mission's target set is service/processor/bizmodel + ORM).

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
