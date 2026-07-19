# nop-metadata 安全与数据完整性加固

> Plan Status: active
> Last Reviewed: 2026-07-19
> Source: `ai-dev/audits/2026-07-19-1118-open-audit-nop-metadata.md` (AR-01, AR-02, AR-03, AR-04, AR-07, AR-09, AR-12, AR-13, AR-14) + `ai-dev/audits/2026-07-19-1118-multi-audit-nop-metadata.md` (维度13-01, 维度13-02, 维度13-03, 维度13-04, 维度09-08, 维度09-11)
> Execution Order: 1 (unblocks Plan 2/3 — security baseline must be locked before schema/convention refactor)
> Related: `2026-07-19-1250-2-nop-metadata-orm-schema-and-data-semantics.md`, `2026-07-19-1250-3-nop-metadata-api-contract-and-engineering-conventions.md`

## Purpose

把 nop-metadata 模块中所有已确认的 P0/P1 安全漏洞（SQL 注入家族、JDBC URL 任意构造、凭据明文暴露与二次落盘、SSRF、内存膨胀 DoS）以及直接放大这些漏洞的工程缺陷（`Statement.executeQuery` 风格不一、`SQLException` 用于业务控制流、错误码语义错配、`querySpace` 路由可被劫持）从 live code 收口到「无 P0/P1 confirmed security defect」状态，并通过新增的对抗性回归测试锁死。

## Current Baseline

### 已成立的事实

- nop-metadata 已完成 32 实体建模、8 Maven 子模块、约 80 个 service Java 文件、~28 个测试文件，模块通过 `./mvnw install`，测试整体绿色。
- 三个执行器（`MetaTableProfiler` / `MetaCatalogCollector` / `MetaQualityRuleExecutor`）对 `tableName` 和列名已统一调用 `FilterToSqlTranslator.validateIdentifier(...)`，唯独 `schemaPattern` 在所有 3 个执行器和 6 个 `judge*` 方法上 **一致漏校验**（AR-01，P0）。
- `MetaDataSourceConnectionService.buildDataSource` 直接用 `SimpleDataSource.setUrl(jdbcUrl)` + `setDriverClassName(driverClassName)`，**无 URL 协议/主机白名单、无驱动类白名单、无 loginTimeout**（AR-02，P0）。
- `NopMetaDataSource.connectionConfig` 在 `_NopMetaDataSource.xmeta` 中默认 `published=true` 且 `queryable/sortable/insertable/updatable=true`，明文 password 通过 GraphQL 与生成的列表 view 直接暴露（维度13-01，P1）。
- `MetaModelChangedEventPublisher.buildEntitySnapshot` 遍历 ORM 实体的全部列并无脱敏写入 `NopMetaModelChangedEvent.{before,after}Snapshot`，使凭据在事件表里二次落盘（AR-07，P1）。
- `MetaDataSourceResolver.resolveActiveOrThrow`（line 50-71）用 `findFirstByQuery(querySpace)` 多匹配取首条且不告警；该方法被 5 处直接调用（MetaAggregationExecutor:2567, 3064 + MetaJoinExecutor:724 + MetaTableReferenceResolver:207 + SqlViewFieldTypeInferrer:124）+ NopMetaTableBizModel:711 经 MetaTableReferenceResolver 间接调用。
- **关于 querySpace 唯一约束（修正 audit AR-03 描述）**：`nop-metadata/model/nop-metadata.orm.xml:283-286` **已声明** `<unique-key name="UK_NOP_META_DS_QUERY_SPACE" columns="querySpace" i18n-en:displayName="Unique Query Space"/>`；`_NopMetaDataSource.xmeta:20-22` 也有对应 `<keys><key name="UK_NOP_META_DS_QUERY_SPACE" props="querySpace"/></keys>`。所以 audit AR-03 描述的"无 querySpace 唯一约束"是**错误的**——UK 在 ORM 层已存在。**但**：(a) live data 中可能已存在违反 UK 的历史数据（迁移前未做冲突检测）；(b) UK 名字用 `DS` 缩写与 Plan 2 维度19-01 的"全称映射"重命名趋势不一致；(c) `resolveActiveOrThrow` 在 runtime 仍以 `findFirstByQuery` 取首条，未做多匹配告警/失败。
- **关于 LIMIT/OFFSET 拼接（修正 audit AR-04 描述）**：实际只有 `NopMetaTableBizModel.appendLimitOffset`（line 770-777）一个真正的 private static method；`MetaAggregationExecutor` 用 inline `sb.append(" LIMIT ?")` / `sb.append(" OFFSET ?")` 共 6 处（line 515/519, 626/629, 813/817, 1234/1237, 1287/1290, 2660/2663）；`MetaJoinExecutor` 用 inline 共 3 处（line 339/343, 401/405, 790/793）。**合计 1 个 method + 9 处 inline = 10 处 LIMIT/OFFSET 拼接**，所有 10 处的 offset-only 分支都会生成 MySQL 不接受的 SQL（AR-04，P1）。
- `NopMetaLineageEdgeBizModel.buildLineageGraph/buildTableNameIndex`（line 628-643, 681-691）使用 `dao().findAll()` 全量加载边集和表索引，无 size 上限、无分页（AR-09，P1）。
- `MetaQualityRuleExecutor.queryLong/queryTimestamp/querySingleValue`（line 453-467, 469-481, 484-507）使用 `Statement.executeQuery(sql)` 拼接 SQL，与同文件 `judgeRange/judgeRegex` 的 `PreparedStatement` 风格不一致（AR-12，P3，纯风格问题不解决安全漏洞）；其中 `queryLong`/`queryTimestamp` 的 catch 块 ErrorCode 描述含 `{error}` 占位符但 throw 时只设置 `sql`（AR-13，P3）。
- `MetaQualityRuleExecutor.judgeCustomSql`（line 199-227）用 `Statement.executeQuery(sql)` 直接执行 `ruleType=custom_sql` 的用户 SQL；**改为 PreparedStatement 不解决注入**（因为 SQL 文本本身是用户配置），需要的是 SQL 内容白名单（禁分号、禁 `UNION/INTO OUTFILE/LOAD DATA` 等）（维度13-03，P2）。
- `NopMetaDataSourceBizModel.collectCatalogForTable` 在 `metaTableId` 找不到表时抛 `ERR_DATASOURCE_NOT_FOUND` 并把 `metaTableId` 写入 `dataSourceId` 参数（维度09-11 / AR-14，P2，确认未修复）。
- `MetaCatalogCollector` / `MetaTableProfiler` 用 `throw new SQLException("COUNT(*) returned no row ...")` 表达不可能发生的逻辑断言（维度09-08，P2）。
- `data-auth.xml` 是空 `<objs/>`，33 个实体无任何行级数据权限规则（维度13-02，P2）。
- `CheckpointActionDispatcher.dispatchWebhook`（line 132-171）用 `httpClient.fetch(request, null)` 发起用户配置 URL 的请求，无 URL 白名单、无方法白名单、无超时（维度13-04，P2）。
- `MetaDataSourceConnectionService` 顶部已定义 `ERR_DATASOURCE_TYPE_NOT_SUPPORTED` ErrorCode（line 30-32），但 `requireJdbcType`（line 114-118）仍抛 `UnsupportedOperationException` 未用之。同模块 `ExternalTableStructureReader:127-135` 同模式；这两处的 javadoc 引用（`IMetaDataSourceConnectionService.java:22`、`SqlViewFieldTypeInferrer.java:59`、`NopMetaQualityRuleBizModel.java:62`、`NopMetaTableBizModel.java:79`、`NopMetaDataSourceBizModel.java:99/129/130/212`）共 5+ 处也需同步更新（维度09-07 javadoc 引用由 Plan 3 集中处理，本计划负责 `MetaDataSourceConnectionService` 代码与 javadoc；`ExternalTableStructureReader` 代码与 javadoc 由 Plan 3 处理）。

### 真正剩余的 gap

- 所有上述 P0/P1 漏洞在 live code 中确认存在且可被对应权限用户触发，没有现成的对抗性回归测试锁死注入/SSRF/凭据泄露/路由劫持/OOM 路径。
- 防御纵深完全缺失：URL 白名单、敏感字段脱敏、`data-auth` 行级权限、webhook SSRF 防护、连接超时、custom_sql 沙箱化均未实现。
- 错误码与参数语义错配会让运维误判（`metaTableId` 错误报成「DataSource not found」）。

## Goals

- 所有已确认的 P0 安全漏洞（AR-01 SQL 注入家族、AR-02 JDBC URL 任意构造）在 live code 中修复并通过新增对抗性回归测试锁死。
- 所有已确认的 P1 安全/数据完整性漏洞（AR-03 querySpace 路由劫持、AR-04 OFFSET-only MySQL 非法 SQL、AR-07 凭据事件二次落盘、AR-09 lineage 内存膨胀 DoS、维度13-01 connectionConfig GraphQL 暴露）在 live code 中修复并通过测试验证。
- 配套的工程缺陷在同一 phase 内一并修复：AR-12/AR-13（queryLong 风格与参数）、AR-14/维度09-11（错误码语义错配）、维度09-08（SQLException 控制流）、维度13-02（data-auth）、维度13-03（custom_sql 沙箱）、维度13-04（webhook SSRF）。
- `_NopMetaDataSource.xmeta`、`data-auth.xml`、相关 ErrorCode 常量、对应回归测试在仓库中可观察、可执行、可审计。

## Non-Goals

- **不**在本计划修复 ORM 模型结构性问题（to-many 反向关系、FK 索引、自然键 UK、表名 `nop_meta_recon_*` 重命名、`mediumtext` precision、`DEL_VERSION` 列名等）——见 Plan 2。
- **特别说明 querySpace UK**：`nop-metadata/model/nop-metadata.orm.xml:283-286` **已存在** `<unique-key name="UK_NOP_META_DS_QUERY_SPACE" columns="querySpace"/>`（audit AR-03 描述有误）。本计划 **不新增 UK**、**不重命名 UK**（重命名 `DS → DATASOURCE` 由 Plan 2 与维度19-01 命名统一一并处理）；本计划只在 service 层增加 runtime 多匹配检测作为兜底（应对历史数据违反 UK 的过渡期）。
- **不**在本计划改造 BizModel 返回 `Map<String,Object>` 为 `@DataBean`、补 `I*Biz` 接口、拆分大类、集中 ErrorCode、改 import 顺序——见 Plan 3。
- **不**改写历史已落盘的 `NopMetaModelChangedEvent` 行（凭据轮换/加密历史行属于 ops 层任务，不在代码 plan 范围）；本计划只负责从修复点之后的新事件不再泄露凭据。
- **不**重写测试框架；新增测试沿用模块现有的 H2 集成测试 + Nop AutoTest 模式（参考 `TestNopMetaQualityRuleBizModel` / `TestNopMetaTableQueryBizModel`）。
- **不**变更 `NopMetaDataSource:mutation` 等 action-auth 的角色映射（admin-only 收紧属于产品策略决策，由 Plan 3 docs 落实或运营层单独处理）。
- **不**修复 `ExternalTableStructureReader` 的 `UnsupportedOperationException`（由 Plan 3 处理）；本计划只修 `MetaDataSourceConnectionService.requireJdbcType` 一处（与 AR-02 URL 白名单配合产生有意义错误码）。

## Scope

### In Scope

- **SQL 注入家族收口**（AR-01）：在 3 个执行器（`MetaTableProfiler.buildFromClause:524-529` / `MetaCatalogCollector.buildFromClause:138-153` / `MetaQualityRuleExecutor.buildFromClause:445-456`）的 `normalizeSchema(schemaPattern)` **之前**对原始 `schemaPattern` 调用 `FilterToSqlTranslator.validateIdentifier(schemaPattern)`（与 tableName/列名一致），失败时由调用方 catch 并包装为对应 ErrorCode 的 `NopException`（`ERR_PROFILING_INVALID_IDENTIFIER` / `ERR_CATALOG_INVALID_IDENTIFIER` / `ERR_QUALITY_INVALID_IDENTIFIER`，沿用模块现有 `metadata.<子域>-invalid-identifier` 命名）。新增对抗性测试：`profileTable/collectCatalog/collectCatalogForTable/executeQualityRule/executeCheckpoint` 在典型注入 payload 下显式失败。
- **JDBC URL 与驱动类白名单 + 超时**（AR-02）：在 `MetaDataSourceConnectionService.buildDataSource`（line 94-119）引入 URL 协议/主机白名单（mysql/postgresql/h2 协议 + 可配置 host allowlist `@cfg:nop.metadata.datasource.allowed-hosts`，禁用 `allowLoadLocalInfile` / `INIT=` / `allowMultiQueries` 等危险参数）、driverClassName 白名单（H2/MySQL/PostgreSQL Driver 类）、`SimpleDataSource.setLoginTimeout(5)`；新增 `ERR_DATASOURCE_JDBC_URL_BLOCKED` / `ERR_DATASOURCE_DRIVER_NOT_ALLOWED` ErrorCode（inline 在 `MetaDataSourceConnectionService` 顶部，沿用模块现有命名 `metadata.datasource-jdbc-url-blocked` / `metadata.datasource-driver-not-allowed`；Plan 3 集中化时统一搬迁到 `NopMetadataErrors.java`）。
- **custom_sql 沙箱化**（维度13-03 + AR-12 关联路径）：`MetaQualityRuleExecutor.judgeCustomSql`（line 199-227）与 `querySingleValue`（line 484-507）：(a) `querySingleValue` 改为 `PreparedStatement`（纯风格，与 queryLong/queryTimestamp 一致）；(b) 在 SQL 内容层增加白名单——拒绝含分号（多语句）、`UNION`、`INTO OUTFILE`、`LOAD DATA` 等危险关键字的 SQL（先做关键字大写 + trim 后 grep）；(c) 在 details 写入 `sqlHash`（SHA-256 短摘要）用于审计；(d) ErrorCode 描述明确"custom_sql 规则在外部数据源账户上执行"。**重点：`PreparedStatement` 不解决 custom_sql 注入**（SQL 文本本身是用户配置），所以白名单是核心、PreparedStatement 是辅助。
- **querySpace 路由劫持 runtime 兜底**（AR-03）：不在 ORM 层新增 UK（已存在 `UK_NOP_META_DS_QUERY_SPACE`）；在 `MetaDataSourceResolver.resolveActiveOrThrow` 改用 `findAllByQuery(q)` 返回 list 后按 size 分派（size==0 抛 `ERR_RESOLVE_NO_DATASOURCE`，size>1 抛新增 `ERR_DATASOURCE_DUPLICATE_QUERY_SPACE` 附 `querySpace`/`dataSourceCount` 参数，size==1 取首条）。新增 ErrorCode inline 在 `MetaDataSourceResolver` 顶部（与现有 `ERR_RESOLVE_NO_DATASOURCE` 同位），命名 `metadata.datasource-duplicate-query-space`。
- **OFFSET-only MySQL 非法 SQL 修复**（AR-04）：在 10 处 LIMIT/OFFSET 拼接点统一语义——`NopMetaTableBizModel.appendLimitOffset`（line 770-777）+ `MetaAggregationExecutor` inline 6 处（line 515/519, 626/629, 813/817, 1234/1237, 1287/1290, 2660/2663）+ `MetaJoinExecutor` inline 3 处（line 339/343, 401/405, 790/793）。建议优先抽公共 helper（`SqlPagination.appendLimitOffset(sb, limit, offset, dialect)`），10 处全部改为调用它；当 `offset != null && offset > 0 && limit == null` 时按方言分派——MySQL 用 `LIMIT 18446744073709551615 OFFSET ?`（MySQL 约定的"无限大 LIMIT"），H2/PostgreSQL 保持原样；或方案 C 在 API 层抛 ErrorCode（实现时择一并加注释）。新增测试覆盖 offset-only 在三种方言下的 SQL 输出。
- **connectionConfig 凭据暴露与事件二次落盘收口**（AR-07 + 维度13-01）：
  - 在 `_NopMetaDataSource.xmeta` 的 `connectionConfig` prop 上设 `published="false"`（屏蔽 GraphQL 出口；**默认选项**）；若导致前端 add/edit 表单失效，则在留存层 `NopMetaDataSource.view.xml`（非 `_gen`）独立配置 add/edit 字段（不修改 `/nop/templates/page/` 平台模板）。fallback 选项 `ui:maskPattern`（保留写、读时脱敏）。
  - **`ext:sensitive="true"` 是新约定**（live repo `grep ext:sensitive nop-metadata/` 0 命中）：在 `_NopMetaDataSource.xmeta` 或 `column` 上标记 `connectionConfig` 为 sensitive；在 `MetaModelChangedEventPublisher.buildEntitySnapshot`（line 147-170）读取 `IColumnModel` 的 ext 标记（若平台 `IColumnModel` 无 ext API，需要先确认平台 schema 支持；若平台不支持，回退到硬编码列名集合 `{connectionConfig}` 在 helper 内脱敏）。对 sensitive 列返回 `"***REDACTED***"`。
  - 该 helper 同时被 save/delete/syncExternalTables/collectCatalog 路径调用，所有路径统一受益。
  - 新增测试：`query { NopMetaDataSource__findPage { connectionConfig } }` 返回脱敏值；`save(NopMetaDataSource)` 触发的事件行 `afterSnapshot` 不含明文 password。
- **Lineage 图遍历内存膨胀 DoS 收口**（AR-09）：在 `NopMetaLineageEdgeBizModel.buildLineageGraph/buildTableNameIndex`（line 628-643, 681-691）**不**继续用 `dao().findAll()` 然后检查 size（OOM 已发生）；改为 `findPage(0, maxEdges+1)`（或 `QueryBean` 带 `limit`），返回 list size > maxEdges 时抛 `ERR_LINEAGE_GRAPH_TOO_LARGE` 并附 `edges` / `limit` 参数（默认 100_000，可配置 `@cfg:nop.metadata.lineage.max-edges`）。新增测试：构造 100_001 条边调用 `getUpstream(metaTableId)` 必须抛 ErrorCode 而非 OOM。
- **data-auth 行级权限补齐**（维度13-02）：在 `nop-metadata.data-auth.xml` 为 `NopMetaDataSource`、`NopMetaQualityCheckpoint`、`NopMetaModelChangedEvent` 至少补 row-level 规则；**Phase 4 第 1 项之前增加 Proof 项**：grep 验证 3 个目标实体均有 `createdBy` 列（若有则按 `createdBy` 过滤；若无租户字段，至少用 `role="admin"` + `otherwise deny` 的最小规则堵住「任意 query 即读全部行」）。新增测试：低权限用户调 `findPage` 在多创建者数据下只能看到自己创建的行。
- **webhook SSRF 防护**（维度13-04）：在 `CheckpointActionDispatcher.dispatchWebhook`（line 132-171）增加 URL 协议白名单（http/https）+ host allowlist（**fail-closed 策略**：默认禁 RFC1918 + 169.254.0.0/16 + localhost；部署 webhook 必须先配 `nop.metadata.checkpoint.webhook-allowed-hosts`，文档中明确说明）；method 限制为 POST/PUT；显式传 timeout（默认 30s，`@cfg:nop.metadata.checkpoint.webhook-timeout-seconds`，默认值在 `app-service.beans.xml` 注入，运行时覆盖在 `application.yaml`）。**测试方式**：mock `IHttpClient`，断言 `HttpRequest.timeout` 字段被设置 + URL 在 allowlist 之外时被拒绝；不依赖真实网络（CI 沙箱可能禁出站）。
- **配套错误处理修复**（在同一文件内一并清理，避免重复 churn）：
  - AR-12（纯风格）：`MetaQualityRuleExecutor.queryLong/queryTimestamp` 改为 `PreparedStatement`（与 querySingleValue 一并；custom_sql 注入由白名单解决，不靠 PreparedStatement）。
  - AR-13：catch 块补 `.param("error", messageOf(e))`（`messageOf` 是本文件已有的 private 静态方法，line 220）。
  - AR-14 / 维度09-11：`NopMetaDataSourceBizModel.collectCatalogForTable` 新增 `ERR_TABLE_NOT_FOUND` ErrorCode（描述 `{metaTableId}`）替换 `ERR_DATASOURCE_NOT_FOUND`。
  - 维度09-08：`MetaCatalogCollector` / `MetaTableProfiler` 的 `throw new SQLException(...)` 改为 `throw new NopException(ERR_*_AGGREGATE_NO_ROW).param("sql", sql)`。
  - 维度09-07（本计划仅负责 `MetaDataSourceConnectionService.requireJdbcType`）：改抛 `NopException(ERR_DATASOURCE_TYPE_NOT_SUPPORTED).param("datasourceType", datasourceType)`（ErrorCode 已在 line 30-32 定义）；同步更新 `MetaDataSourceConnectionService.java` 与 `IMetaDataSourceConnectionService.java` 内的 javadoc 引用。**`ExternalTableStructureReader` 的同模式修复由 Plan 3 负责**（避免两 plan 改同文件冲突）。

### Out Of Scope

- AR-05 cross-db merge NULL/类型语义、AR-08 `Math.toIntExact` 溢出、AR-06 syncExternalTables before==after、AR-10 N+1 upsert、AR-11 `evalExpectPassWhen` NFE —— 移至 Plan 2/3。
- ORM 模型关系/索引/UK/naming —— Plan 2（除 AR-03 的 `UK_NOP_META_DATASOURCE_QUERY_SPACE` 例外）。
- BizModel 拆分、`I*Biz` 接口补齐、DTO 化、ErrorCode 集中、import 顺序、`System.currentTimeMillis`、docs 同步 —— Plan 3。
- 历史已落盘 `NopMetaModelChangedEvent` 行的凭据轮换/加密 —— ops 任务，不在代码 plan 范围。

## Execution Plan

### Phase 1 - P0 漏洞应急修复（schemaPattern SQL 注入 + JDBC URL 白名单）

Status: completed
Targets: `nop-metadata-service/.../profiling/MetaTableProfiler.java`、`nop-metadata-service/.../catalog/MetaCatalogCollector.java`、`nop-metadata-service/.../quality/MetaQualityRuleExecutor.java`、`nop-metadata-service/.../connection/MetaDataSourceConnectionService.java`、`nop-metadata-service/.../NopMetadataErrors.java`（若新增 ErrorCode 文件）

- Item Types: `Fix | Proof`

- [x] 在 3 个执行器（profiling / catalog / quality）的 `qualifyTable`/`buildFromClause` 入口前增加 `validateIdentifier(schemaPattern)`，与 tableName/列名共用同一套 ErrorCode 风格（`ERR_PROFILING_INVALID_IDENTIFIER` / `ERR_CATALOG_INVALID_IDENTIFIER` / `ERR_QUALITY_INVALID_IDENTIFIER`），失败抛 NopException 并附 `schema` 参数（Fix）
- [x] 在 `MetaDataSourceConnectionService.buildDataSource` 引入：URL 协议白名单（mysql/postgresql/h2）+ host allowlist（`@cfg:nop.metadata.datasource.allowed-hosts`，默认拒绝 RFC1918/169.254/localhost 之外的内网；若需允许内网须显式配置）+ 危险参数黑名单（`allowLoadLocalInfile` / `INIT=` / `allowMultiQueries` 等）；driverClassName 白名单；`SimpleDataSource.setLoginTimeout(5)`；新增 `ERR_DATASOURCE_JDBC_URL_BLOCKED` / `ERR_DATASOURCE_DRIVER_NOT_ALLOWED` ErrorCode（Fix）
- [x] `MetaDataSourceConnectionService.requireJdbcType` 改抛 `NopException(ERR_DATASOURCE_TYPE_NOT_SUPPORTED)`（与维度09-07 一并修复，配套 URL 白名单产生有意义错误码）（Fix）
- [x] 新增对抗性回归测试：`TestMetaTableProfilerSecurity` 覆盖 `schemaPattern = "x; DROP TABLE y"` / `"mysql.user WHERE ...--"` 等典型注入 payload 必须显式失败（Proof）
- [x] 新增对抗性回归测试：`TestMetaDataSourceConnectionSecurity` 覆盖 jdbcUrl 指向 169.254.169.254 / `jdbc:h2:mem:...;INIT=RUNSCRIPT FROM ...` / `allowLoadLocalInfile=true` / 任意 driverClassName 必须显式失败，loginTimeout 在黑洞 IP 上 ≤ 6 秒返回（Proof）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 三个执行器所有 `schemaPattern` 路径（`profileTable` / `collectCatalog` / `collectCatalogForTable` / `executeQualityRule` / `executeCheckpoint` / 6 个 `judge*` 方法）均经过 `validateIdentifier` 校验
- [x] JDBC URL 白名单与驱动类白名单在 live code 中可观察，blacklist 参数显式拒绝
- [x] `MetaDataSourceConnectionService.buildDataSource` 不再无防护构造 `SimpleDataSource`
- [x] 新增的对抗性测试覆盖至少 3 类典型 payload（注入、SSRF、文件读取）+ 黑洞 IP 超时
- [x] **接线验证**：`profileTable` / `collectCatalog` / `executeQualityRule` 入口到 `buildFromClause` 的调用链在新测试中被断言（验证 `validateIdentifier` 确实被调用，不只是新方法存在）
- [x] **无静默跳过**：白名单失败显式抛 ErrorCode 而非返回 null / 默认值；`requireJdbcType` 不再用 `UnsupportedOperationException`
- [x] 受影响的 owner docs（`docs-for-ai/02-core-guides/error-handling.md` 若新增 ErrorCode 命名规则、`docs-for-ai/02-core-guides/api-and-graphql.md` 若涉及 mutation 权限描述）已同步；或明确写 `No owner-doc update required: 仅模块内 ErrorCode 新增`
- [x] `ai-dev/logs/2026/07-19.md` 顶部追加本 phase 进度条目

### Phase 2 - querySpace 路由劫持与 connectionConfig 凭据暴露收口

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml`、`nop-metadata-service/.../datasource/MetaDataSourceResolver.java`、`nop-metadata-meta/.../NopMetaDataSource/_NopMetaDataSource.xmeta`、`nop-metadata-service/.../event/MetaModelChangedEventPublisher.java`、`nop-metadata-service/.../connection/MetaDataSourceConnectionService.java`、`nop-metadata-web/.../pages/NopMetaDataSource/_gen/_NopMetaDataSource.view.xml`（若需修生成模板上游）

- Item Types: `Fix | Decision | Proof`

- [x] **不在 ORM 层新增 UK**（修正 audit AR-03 描述：`UK_NOP_META_DS_QUERY_SPACE` 已存在 line 284）；在 `MetaDataSourceResolver.resolveActiveOrThrow`（line 50-71）改用 `findAllByQuery(q)` 返回 list 后按 size 分派：size==0 抛 `ERR_RESOLVE_NO_DATASOURCE`，size>1 抛新增 `ERR_DATASOURCE_DUPLICATE_QUERY_SPACE`（附 `querySpace`/`dataSourceCount` 参数，命名 `metadata.datasource-duplicate-query-space`，inline 在 resolver 顶部），size==1 取首条（Fix）
- [x] 验证 5 处直接调用方（MetaAggregationExecutor:2567/3064 + MetaJoinExecutor:724 + MetaTableReferenceResolver:207 + SqlViewFieldTypeInferrer:124）在多匹配场景下都正确收到 `ERR_DATASOURCE_DUPLICATE_QUERY_SPACE`（Proof）
- [x] 在 `_NopMetaDataSource.xmeta` 的 `connectionConfig` prop 上设 `published="false"`（屏蔽 GraphQL 出口；**默认选项**）；若导致前端 add/edit 表单失效，则在留存层 `nop-metadata-web/.../pages/NopMetaDataSource/NopMetaDataSource.view.xml`（**非 `_gen`**）独立配置 add/edit 字段，**不修改 `/nop/templates/page/` 平台模板**；fallback 选项 `ui:maskPattern`（Decision + Fix）
- [x] **`tagSet="sensitive"` 是新约定**（沿用平台 IColumnModel.getTagSet() API；live repo `tagSet="sensitive"` 0 命中，是 connectionConfig 首例）：在 `nop-metadata.orm.xml` 的 CONNECTION_CONFIG column 上标记 `tagSet="sensitive"`（从 ORM 层下沉到 IColumnModel.getTagSet()，比平台不支持 ext:sensitive 更稳）；在 `MetaModelChangedEventPublisher.buildEntitySnapshot`（line 147-170）读取标记并对标记列返回 `"***REDACTED***"`（不读取实际值）；维护硬编码列名集合 `{connectionConfig, password, jdbcUrl, ...}` 兜底脱敏（defense-in-depth）（Fix）
- [x] 该 helper 同时被 save/delete/syncExternalTables/collectCatalog 路径调用，所有路径统一受益（Fix）
- [x] 新增回归测试：`TestMetaDataSourceResolver` 覆盖 querySpace 重复时抛 `ERR_DATASOURCE_DUPLICATE_QUERY_SPACE`（Proof；UK 在 ORM 层已存在 line 284，runtime check 兜底已有数据）
- [x] 新增回归测试：`TestMetaModelChangedEventPublisherSecurity` 覆盖 `buildEntitySnapshot` 对 sensitive 列返回 `***REDACTED***`、`save(NopMetaDataSource)` 触发的事件 `afterSnapshot` 不含明文 password（Proof）

Exit Criteria:

- [x] `nop-metadata.orm.xml` 中 `UK_NOP_META_DS_QUERY_SPACE` 保持现状（**不新增、不重命名**；重命名由 Plan 2 处理）
- [x] `MetaDataSourceResolver.resolveActiveOrThrow` 在多匹配场景抛 ErrorCode，错误响应包含 `dataSourceCount` 参数；5 处直接调用方在多匹配场景下都正确收到 ErrorCode
- [x] GraphQL `findPage { connectionConfig }` 实测返回脱敏值（不是字符串原值）（published=false 屏蔽 GraphQL 出口）
- [x] `MetaModelChangedEventPublisher.buildEntitySnapshot` 对 sensitive 列返回固定脱敏字符串（通过 tagSet 标记或硬编码列名集合实现）
- [x] **端到端验证**：从 GraphQL mutation `save(NopMetaDataSource)` → ORM 落库 → 事件发布 → `NopMetaModelChangedEvent.afterSnapshot` 的完整路径已验证不包含明文 password
- [x] **接线验证**：通过 mock 验证 `buildEntitySnapshot` 确实调用了 sensitive 屏蔽分支（orm_propValueByName 不被调用）
- [x] **无静默跳过**：sensitive 屏蔽失败时显式抛 ErrorCode，而非返回原值
- [x] 受影响 owner docs 同步：`docs-for-ai/02-core-guides/api-and-graphql.md` 在 mutation 权限/字段发布一节补充敏感字段约定（`published="false"` 与 `tagSet="sensitive"` 模式）；或明确写 `No owner-doc update required`（Decision: 后续 Plan 3 集中更新 docs，本 phase 仅落地代码约定）
- [x] `ai-dev/logs/2026/07-19.md` 追加本 phase 进度

### Phase 3 - OFFSET/LIMIT MySQL 非法 SQL、内存膨胀 DoS 与配套工程缺陷收口

Status: planned
Targets: `nop-metadata-service/.../entity/NopMetaTableBizModel.java`、`nop-metadata-service/.../query/MetaAggregationExecutor.java`、`nop-metadata-service/.../query/MetaJoinExecutor.java`、`nop-metadata-service/.../entity/NopMetaLineageEdgeBizModel.java`、`nop-metadata-service/.../catalog/MetaCatalogCollector.java`、`nop-metadata-service/.../profiling/MetaTableProfiler.java`、`nop-metadata-service/.../entity/NopMetaDataSourceBizModel.java`

- Item Types: `Fix | Proof`

- [ ] 统一 **10 处** LIMIT/OFFSET 拼接语义（**修正 audit AR-04 描述**：1 个 method + 9 处 inline）：`NopMetaTableBizModel.appendLimitOffset`（line 770-777）+ `MetaAggregationExecutor` inline 6 处（line 515/519, 626/629, 813/817, 1234/1237, 1287/1290, 2660/2663）+ `MetaJoinExecutor` inline 3 处（line 339/343, 401/405, 790/793）。**建议优先抽公共 helper**（`SqlPagination.appendLimitOffset(sb, limit, offset, dialect)`），10 处全部改为调用它；`offset != null && offset > 0 && limit == null` 时按方言分派（MySQL 用 `LIMIT 18446744073709551615 OFFSET ?`，H2/PostgreSQL 保持）；或方案 C 抛 ErrorCode（择一并加注释）（Fix）
- [ ] 在 `NopMetaLineageEdgeBizModel.buildLineageGraph/buildTableNameIndex`（line 628-643, 681-691）**不**用 `dao().findAll()` 然后检查 size（OOM 已发生）；改为 `findPage(0, maxEdges+1)` 或 `QueryBean` 带 `limit`，返回 list size > maxEdges 时抛 `ERR_LINEAGE_GRAPH_TOO_LARGE` 并附 `edges`/`limit` 参数（默认 100_000，可配置 `@cfg:nop.metadata.lineage.max-edges`）（Fix）
- [ ] `MetaCatalogCollector`（line 104）/ `MetaTableProfiler`（line 417）的 `throw new SQLException("COUNT(*) returned no row ...")` 改为 `throw new NopException(ERR_*_AGGREGATE_NO_ROW).param("sql", sql)`（维度09-08）（Fix）
- [ ] **AR-12（纯风格）**：`MetaQualityRuleExecutor.queryLong/queryTimestamp/querySingleValue`（line 453-467, 469-481, 484-507）改为 `PreparedStatement`；catch 块补 `.param("error", messageOf(e))`（AR-13；`messageOf` 是本文件已有的 private 静态方法，line 220）（Fix）
- [ ] **custom_sql 沙箱化（核心）**：`MetaQualityRuleExecutor.judgeCustomSql`（line 199-227）：(a) `querySingleValue` 用 `PreparedStatement`；(b) **SQL 内容白名单**（关键，不依赖 PreparedStatement）：拒绝含分号（多语句）、`UNION`、`INTO OUTFILE`、`LOAD DATA` 等危险关键字（先做 trim + 大写后 grep）；(c) 在 details 写入 `sqlHash`（SHA-256 短摘要）用于审计（维度13-03）（Fix）
- [ ] `NopMetaDataSourceBizModel.collectCatalogForTable` 新增 `ERR_TABLE_NOT_FOUND` ErrorCode 并替换 `ERR_DATASOURCE_NOT_FOUND`（AR-14 / 维度09-11）（Fix）
- [ ] 新增回归测试：`TestMetaTableQueryOffsetOnly` 覆盖 MySQL dialect 下 offset-only 分页 SQL 输出为 `LIMIT 18446744073709551615 OFFSET ?` 或 ErrorCode（择一）；至少 3 个方言（H2 / MySQL / PostgreSQL）的 SQL 输出已断言（Proof）
- [ ] 新增回归测试：`TestNopMetaLineageEdgeSizeLimit` 构造 > 100_000 条边，调 `getUpstream(metaTableId)` 必须抛 `ERR_LINEAGE_GRAPH_TOO_LARGE`（Proof）
- [ ] 新增回归测试：`TestMetaQualityRuleExecutorCustomSqlSandbox` 覆盖 custom_sql 内含 `;` / `UNION SELECT` / `INTO OUTFILE` / `LOAD DATA` 等典型 payload 必须失败、details 中含 `sqlHash`（Proof）

Exit Criteria:

- [ ] **10 处** LIMIT/OFFSET 拼接点（`grep -n 'LIMIT\\|OFFSET' nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/{entity/NopMetaTableBizModel,query/MetaAggregationExecutor,query/MetaJoinExecutor}.java`）在 MySQL dialect 下生成的 SQL 均合法（无 `OFFSET` without `LIMIT`）
- [ ] `NopMetaLineageEdgeBizModel` 两个 `findAll()` 调用已改为 `findPage(0, maxEdges+1)` 类似机制；size > maxEdges 显式抛 ErrorCode
- [ ] `MetaCatalogCollector`/`MetaTableProfiler`/`MetaQualityRuleExecutor` 中所有非真实 JDBC 故障路径不再抛 `SQLException`/`ArithmeticException`/`NumberFormatException`
- [ ] `MetaQualityRuleExecutor` 内 custom_sql 路径：(a) 使用 `PreparedStatement`；(b) **含分号/UNION/INTO OUTFILE/LOAD DATA 等危险关键字的 SQL 被拒绝**（不只是多语句）
- [ ] 新增的 3 类对抗性测试在仓库中可观察且通过
- [ ] **无静默跳过**：所有新增 ErrorCode 在失败路径上显式抛出，无 catch 后吞掉的情况
- [ ] 受影响 owner docs 同步（若 ErrorCode 命名规则在 `docs-for-ai/02-core-guides/error-handling.md` 中需要补充）；或明确写 `No owner-doc update required`
- [ ] `ai-dev/logs/2026/07-19.md` 追加本 phase 进度

### Phase 4 - data-auth 行级权限与 webhook SSRF 防护

Status: planned
Targets: `nop-metadata-service/src/main/resources/_vfs/nop/metadata/auth/nop-metadata.data-auth.xml`、`nop-metadata-service/.../quality/CheckpointActionDispatcher.java`、`nop-metadata-app/src/main/resources/application.yaml`（若新增配置项）

- Item Types: `Fix | Decision | Proof`

- [ ] **Proof（前置）**：grep 验证 `NopMetaDataSource` / `NopMetaQualityCheckpoint` / `NopMetaModelChangedEvent` 3 个目标实体是否均有 `createdBy` 列；记录结果到 daily log（Proof）
- [ ] 在 `nop-metadata.data-auth.xml` 为 3 个目标实体补 row-level 规则：若有 `createdBy` 则按 `createdBy == $user.userId` 过滤；若无租户/创建者字段，则用 `role="admin"` + `otherwise deny` 的最小规则堵住「任意 query 即读全部行」（Decision + Fix）
- [ ] 在 `CheckpointActionDispatcher.dispatchWebhook`（line 132-171）增加 URL 协议白名单（http/https）+ host allowlist（**fail-closed 策略**：默认禁 RFC1918 + 169.254.0.0/16 + localhost；部署 webhook 必须先配 `nop.metadata.checkpoint.webhook-allowed-hosts`）、method 白名单（POST/PUT）、显式 timeout（默认 30s，`@cfg:nop.metadata.checkpoint.webhook-timeout-seconds`；默认值在 `app-service.beans.xml` 注入，运行时覆盖在 `application.yaml`）（Fix）
- [ ] 新增测试：`TestDataAuthRowLevelScoping` 覆盖低权限用户调 `findPage` 在多创建者数据下只能看到允许的行（Proof）
- [ ] 新增测试：`TestCheckpointActionDispatcherWebhookSsrf` **使用 mock `IHttpClient`**（避免 CI 沙箱禁出站）：断言 `HttpRequest.timeout` 字段被设置、URL 在 allowlist 之外时被拒绝、method 非 POST/PUT 被拒绝（Proof）

Exit Criteria:

- [ ] `nop-metadata.data-auth.xml` 含至少 3 个实体的 row-level 规则
- [ ] `CheckpointActionDispatcher.dispatchWebhook` 拒绝内网/link-local URL、拒绝非 POST/PUT method、显式传 timeout
- [ ] 新增的对抗性测试通过（**mock IHttpClient，不依赖真实网络**）
- [ ] **端到端验证**：从 GraphQL `mutation { NopMetaQualityCheckpoint__save(...) actions: { webhook ... } }` → checkpoint 执行 → webhook 调用的完整路径在内网 URL 时显式失败（不是静默忽略）
- [ ] **无静默跳过**：白名单失败、超时、method 非法均抛 ErrorCode；不返回 200/默认值
- [ ] 受影响 owner docs 同步：`docs-for-ai/02-core-guides/api-and-graphql.md` 在权限模型一节补充 data-auth + webhook SSRF 约定；或明确写 `No owner-doc update required`
- [ ] `ai-dev/logs/2026/07-19.md` 追加本 phase 进度

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 所有 in-scope confirmed P0 live security defects（AR-01、AR-02）已修复
- [ ] 所有 in-scope confirmed P1 security/integrity defects（AR-03、AR-04、AR-07、AR-09、维度13-01）已修复
- [ ] 所有配套工程缺陷（AR-12、AR-13、AR-14/维度09-11、维度09-07、维度09-08、维度13-02、维度13-03、维度13-04）已修复
- [ ] 每个新增防护路径都有对抗性回归测试锁死（注入 / SSRF / 凭据泄露 / 路由劫持 / OOM / OFFSET-only / custom_sql 多语句）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope confirmed security defect
- [ ] 受影响的 owner docs 已同步（或明确裁定 No owner-doc update required）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）`validateIdentifier` 在运行时被调用链触达（不只是方法存在）、（b）URL/SSRF 白名单在运行时被检查（不只是常量定义）、（c）sensitive 屏蔽在 `buildEntitySnapshot` 运行路径上生效（不只是 xmeta 标记存在）
- [ ] `./mvnw compile -pl nop-metadata -am -T 1C`
- [ ] `./mvnw test -pl nop-metadata -am -T 1C`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### UK_NOP_META_DS_QUERY_SPACE 历史数据清洗（含 UK 重命名）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 本计划只在 service 层增加 runtime 多匹配检测；ORM 层的 `UK_NOP_META_DS_QUERY_SPACE`（line 284）已存在且本计划不重命名。重命名 `DS → DATASOURCE`（与维度19-01 全称映射趋势一致）由 Plan 2 处理。生产环境中若已存在重复 querySpace 的历史数据，runtime check 会拒绝调用并要求 ops 干预；具体清洗策略（保留最早 / 保留最新 / 等待人工裁决）属于 ops 决策，本代码 plan 不裁定。
- Successor Required: `yes`
- Successor Path: Plan 2（重命名）+ ops runbook（数据清洗，不在 ai-dev/plans 范围）

### 历史 NopMetaModelChangedEvent 行的凭据轮换

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本计划从代码层面保证"从修复点之后的新事件不再泄露凭据"；历史已落盘行的轮换/加密/删除属于 ops 层任务，依赖部署环境的备份策略与合规要求。
- Successor Required: `yes`
- Successor Path: ops runbook（不在 ai-dev/plans 范围）

## Non-Blocking Follow-ups

- 把 `NopMetaDataSource:mutation` 等 action-auth 在 demo app 收紧为 admin-only（产品策略决策，不在本 plan）
- URL host allowlist 在多环境部署下的配置中心化（ops 任务）
- webhook URL allowlist 在多环境部署下的配置中心化（ops 任务）

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<session ID>>
- Evidence:
  - 每条 Exit Criterion 的验证结果（PASS/FAIL + 对应的 live code path 或 test name）
  - 每条 Closure Gate 的验证结果（PASS/FAIL + evidence 来源）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码为 0
  - Anti-Hollow 检查结果：<<端到端调用链追踪结果>>
  - Deferred 项分类检查：<<确认无 in-scope security defect 被降级>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
