# DATA-01 — Data-View / Metadata Dynamic SQL Injection Audit (vs CORE-04 baseline)

> Mission: security-audit (roadmap item 8, deliverable DATA-01)
> Date: 2026-09-19. Auditor: ZCode session (plan 2026-09-18-2344-10, Phase 2).
> Severity labels: CRITICAL/HIGH/MEDIUM/LOW and P0/P1/P2/P3 (P0=CRITICAL … P3=LOW).
> Scope: datav view DSL → SQL chain (PanelSqlBuilder / PanelParamEvaluator /
> PanelDataBinder / ChatBI dataset executor / share-visit SQL) and metadata
> own-dynamic-SQL construction (FilterToSqlTranslator, ExpressionMeasureValidator,
> MetaAggregationExecutor/AggregationHelper, MetaJoinExecutor, MetaTableQueryExecutor,
> SqlPagination, custom_sql quality sandbox).
> Baseline: CORE-04 (`ai-dev/audits/security-audit/2026-09-18-core-audit-CORE-04.md`)
> established the repo-wide SQL-builder defense (validated identifiers +
> parameterized values). This report classifies item-8 surfaces as
> **validated / parameterized / DSL-defined (authoring trust) / documented
> escape hatch / vulnerable**. CORE-04 internals are NOT re-audited.

## Site classification (complete — no unclassified site)

Source sweep: variable-concatenation grep over `nop-datav-service` and
`nop-metadata-service` main sources (every `.append(var)`/`+var` in SQL-building
code was traced to its validation point; raw hits listed in
`_tmp/security-audit/data-inventory.md` §C).

| Cluster | Sites | Class | Rationale |
|---|---|---|---|
| datav panel SQL template | `PanelSqlBuilder.build` (`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/query/PanelSqlBuilder.java:42-62`) | **parameterized** | `${param}` placeholders replaced by `?`, values bound via `SQL.SqlBuilder.sqlWithParams`; undeclared placeholder → explicit error; params come from `PanelParamEvaluator` pure mapping (L41-89) — request values never enter SQL text |
| datav panel SQL body (dsText) | `PanelDataBinder.queryPanelData` (`.../query/PanelDataBinder.java:149-170`) reading `NopReportDataset.dsText` | **DSL-defined (authoring trust)** | SQL body authored in nop-report datasets (out-of-group module); executor-side defense = parameterization + dashboard RLS on entry (`NopDatavPanelBizModel.requirePanelDashboardAccess` L109-122) + max-panels gate (`NopDatavDashboardBizModel` L436-442) |
| datav ChatBI query | `DatavQueryDatasetExecutor.doQuery` (`.../chatbi/DatavQueryDatasetExecutor.java:187-210`) | **parameterized** (body = same DSL-defined dsText) | LLM-supplied params bound as `?`; dataset visibility check admin-or-owner (L102-109); maxRows server-clamped (L175-185) |
| datav internal SQL | share visit stats (`NopDatavDashboardShareBizModel.recordShareVisit` L253-262), scheduler `existsTable` constants | **internal-constant** | fixed statement text + bound params / fixed table names |
| metadata filter tree → WHERE/HAVING | `FilterToSqlTranslator` (`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/FilterToSqlTranslator.java` IDENTIFIER_PATTERN L38, requireField L269-287, comparisons L189-258) | **validated + parameterized** | every leaf name whitelisted `^[A-Za-z_][A-Za-z0-9_]*$` before interpolation (or resolved through the HAVING nameResolver whitelist); all values/in-lists/bounds PreparedStatement-bound; unsupported op fail-closed |
| metadata HAVING anti-forgery | `AggregationHelper.nameResolverFor` (`.../query/AggregationHelper.java:298-325`) + `MetaAggregationExecutor.preprocessHavingArithmetic` | **validated** | client-forgeable `havingExprResolved` attr stripped at recursion entry (MA7.1-01); raw unmarked names rejected |
| metadata measure expressions | `ExpressionMeasureValidator` (`.../field/ExpressionMeasureValidator.java` tokenize L300-487, blacklists L78-125, identifier whitelist L50) | **validated** | tokenizer rejects `;`/`--`/`/*`; literals → `?`; identifiers whitelisted; keyword+function blacklists (incl. H2/PG file families, check2 P1); enforced save-time AND query-time |
| metadata aggregation/JOIN SQL build | `AggregationHelper.buildExternalAggregationSql/buildExternalExternalJoinSql/buildMixedSameDbJoinSql` (L607-776), `aggSqlOf` closed set (L60-82), `MetaJoinExecutor` (validateIdentifier at L258-261/270-272/327-336/585-620), `SqlPagination.appendLimitOffset` | **validated + parameterized** | dims/measures/join columns/aliases all pass `validateIdentifier` or the expression validator; WHERE/HAVING via FilterToSqlTranslator; limit/offset bound as params (executeJdbcQuery L84-119 sets `?`) |
| metadata external/sql table browse | `MetaTableQueryExecutor.buildExternalSelectSql` (L48-70 per-column validateIdentifier), `buildSqlSelectSql` (L75-84 embeds `sourceSql` verbatim) | external path **validated + parameterized**; `sourceSql` embed **DSL-defined (authoring trust)** — view definition stored on NopMetaTable, authored via `createSqlTable`/CRUD under NopMetaTable:mutation |
| metadata quality custom_sql | `MetaQualityRuleExecutor.CUSTOM_SQL_FORBIDDEN_WORDS` (L100-125) + judgeCustomSql (L309+) | **documented escape hatch** | user-authored SQL executed against external datasources behind a fail-closed keyword/sequence blocklist (code comment L42 declares the known explicit risk); deny-list not whitelist — residual risk acknowledged; details persist sqlHash only |
| metadata reconciliation / lineage / profiling | ReconciliationExecutor (candidate matching, no SQL), SqlSourceTableExtractor/SqlColumnLineageExtractor (parse-only AST) | **non-SQL / parse-only** | no executable SQL constructed from request input |

**Vulnerable class: zero sites.**

## Findings

| ID | Severity | Anchor | Description | Remediation suggestion |
|---|---|---|---|---|
| F-D1-1 | MEDIUM (P2) | `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaTableQueryAction.java:64-105` (`queryEntityData`: `propNames = entityModel.getColumns()`, `row.orm_propValueByName(prop)` for every column); entry `NopMetaTableBizModel.queryTableData` L273-297 | The entity-table path of `queryTableData` returns **every ORM column** of the target entity as raw row maps — bypassing the GraphQL/xmeta field-level controls of that entity (`published=false`, sensitive tagSets). Example impact: a logical table mapped onto `NopMetaDataSource` (importable via `importOrmModel`) would return `connectionConfig` (plaintext JDBC credentials) even though that field is `published="false"`/`queryable=false` in its own xmeta (owner doc P1-1 contract). Requires `NopMetaTable:query` (metadata browse) permission — an authorization boundary crossing, not injection. | Apply the underlying entity's field visibility (xmeta published / tagSet sensitive) when building `propNames`, or document that `NopMetaTable:query` implies full-column read on all catalogued entities and restrict that permission by default; minimum: exclude `tagSet=sensitive` columns. |
| F-D1-2 | LOW (P3) | `MetaQualityRuleExecutor.java:41-42, 100-125` (custom_sql blocklist); `NopMetaTableBizModel.createSqlTable` L180-233 (sourceSql authoring) | Two authoring-trust SQL surfaces are guarded by permissions + (for custom_sql) a deny-list rather than positive validation: (a) quality-rule `custom_sql` executes user-provided SQL on external datasources (blocklist fail-closed; deny-list bypass potential is inherent — code comment L42 acknowledges); (b) `sourceSql` view definitions are executed verbatim as subqueries. Both are equivalent to CORE-04's "documented escape hatch" class, but the effective security boundary is the *rule/table authoring permission*, which this module leaves to coarse CRUD auth (no explicit @Auth on createSqlTable/save beyond defaults). | Item 9 triage: record the trust assumption (metadata authors = SQL authors) in deployment guidance; optional hardening — explicit fine-grained `@Auth` on `createSqlTable`/quality-rule save, or an opt-in allowlist mode for custom_sql. |
| F-D1-3 | LOW (P3, observation) | `NopDatavDatasetRefBizModel.java` (plain CrudBizModel); `PanelDataBinder` L149-157 | datav `paramMapping` (dashboard-author surface) and dataset selection are stored without schema-level validation beyond JSON shape; a dashboard author can point a panel at ANY NopReportDataset id and bind any request param — reader-side exposure is bounded by dashboard RLS (published dashboards widen the audience to all readers, who then trigger the dataset SQL with their own filter values). No injection (values bound), but the data-exposure decision rests on the dashboard author's dataset choice, not the reader's permissions on the dataset (contrast ChatBI, which DOES check dataset visibility — DatavQueryDatasetExecutor L102-109). | Optional parity: apply ChatBI's `ChatBiDatasetVisibility` (admin-or-owner) check — or a dataset-level visibility flag — in `PanelDataBinder` before executing the dataset SQL. |

## Explicit no-finding statements

- No SQL injection path on the request-facing datav panel query chain:
  placeholders → `?` with bound values (PanelSqlBuilder), verified at source.
- No SQL injection path on the metadata filter/aggregation/JOIN chain: every
  interpolated identifier passes `IDENTIFIER_PATTERN`, expressions pass the
  ExpressionMeasureValidator tokenizer, and all comparison values, in-lists,
  limits, and offsets are PreparedStatement parameters.
- No raw concatenation of request input into executable SQL text found in
  either group (sweep hit list fully classified above; remaining hits are
  error-param strings or validated columns).
- HAVING anti-forgery control re-verified live (MA7.1-01 marker stripping +
  nameResolver whitelist), and the expression blacklists include the H2/PG
  file-family functions (check2 P1, 2026-08-28) — previously audited fixes hold.
- Cross-source JOIN: same-DB native JOIN uses validated identifiers; cross-DB
  merge is in-memory with `maxCrossDbRows` bound (MetaJoinExecutor.java:75/427/459)
  — no SQL-level risk beyond the classified per-side queries.

## Adjudication (Phase 3 input)

- F-D1-1: `remediation-target` (MEDIUM — field-visibility bypass on entity
  browse; candidate fix in nop-metadata).
- F-D1-2: `adjudicated trust-boundary documentation` + optional `@Auth`
  hardening (LOW).
- F-D1-3: `remediation-target` (LOW, parity/visibility check) or adjudicated
  design constraint if dataset-permission parity is deemed out of scope.

## Owner mapping

- F-D1-1, F-D1-2 successor owner: item 9 consolidation (nop-metadata fixes).
- F-D1-3 successor owner: item 9 (nop-datav; note nop-datav has no owner doc —
  recorded limitation, design docs in `ai-dev/design/nop-datav/`).
- SQL-builder internals remain CORE-04 (item 1); GraphQL coarse auth mechanics
  remain item 4. No double ownership.
