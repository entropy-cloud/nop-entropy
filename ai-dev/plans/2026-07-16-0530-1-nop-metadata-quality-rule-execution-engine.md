# 1 nop-metadata 质量规则执行引擎

> Plan Status: completed
> Last Reviewed: 2026-07-16
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` P2（P2-6）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.7 数据质量；`ai-dev/design/nop-metadata/06-data-quality-extended.md` §二 质量规则扩展（mostly）
> Mission: nop-metadata
> Work Item: P2-6 — 质量规则执行引擎（MetaQualityRule 执行 not_null/unique/range/regex/freshness/volume/custom_sql → 写入 MetaQualityResult 时序结果）
> Related: `2026-07-16-0225-1-nop-metadata-datasource-registration-and-connection-verification.md`（提供 callback 式连接服务 `withConnection`）、`2026-07-16-0420-1-nop-metadata-catalog-runtime-collection.md`（提供 external 表 querySpace→数据源→withConnection 执行范式 + 降级/失败隔离模式，本 plan 复用）、`2026-07-16-0225-2-nop-metadata-external-table-metadata-sync.md`（external 表目录 + 列结构 JSON 存 buildSql）

## Purpose

让 nop-metadata 的质量规则从"只有 CRUD 表结构"推进到"可执行、可产出时序结果"。具体：提供 MetaQualityRule 的**执行引擎**——对 7 种内置规则类型（not_null/unique/range/regex/freshness/volume/custom_sql）生成并运行检测 SQL，按规则语义判定 pass/fail，写入 MetaQualityResult 时序结果行。本 plan 使质量成为可运行的治理能力，并收口 P2-4 Catalog Deferred（entity/sql 类型表收集需 querySpace→数据源解析）中"物理定位解析"在质量域的等价裁定。

## Current Baseline

- **NopMetaQualityRule 实体已建模**（`nop-metadata/model/nop-metadata.orm.xml:1422-1479`）：`qualityRuleId`(PK) / `ruleName`(mandatory) / `displayName` / `ruleType`(mandatory, dict `meta/quality-rule-type`：`not_null`/`unique`/`range`/`regex`/`custom_sql`/`freshness`/`volume`) / `entityType`(mandatory, dict `meta/quality-entity-type`：`field`/`table`/`database`) / `entityId`(mandatory) / `severity`(mandatory, dict `meta/quality-severity`) / `sqlExpression`(domain mediumtext) / `threshold`(double) / `params`(domain `json-4000` + stdDomain json) / `extConfig`(json) + 审计。索引 `IX_NOP_META_QRULE_ENTITY`(entityType, entityId)。
- **NopMetaQualityResult 实体已建模**（`orm.xml:1484-1541`）：`qualityResultId`(PK) / `qualityRuleId`(mandatory) / `executeTime`(mandatory) / `status`(mandatory, dict `meta/quality-result-status`：`PASS`/`FAIL`/`ERROR`/`SKIP`) / `actualValue`(double) / `expectedValue`(double) / `message` / `details`(domain `json-4000` + stdDomain json) + 审计。to-one qualityRule。索引 `IX_NOP_META_QRESULT_RULE`(qualityRuleId, executeTime)。**时序语义**：每次执行追加新行（executeTime=now），不覆盖旧行。
- **BizModel 现状**：`NopMetaQualityRuleBizModel`、`NopMetaQualityResultBizModel` 存在，仅 CrudBizModel 自动生成的 findPage/findList/get/save/delete。**无任何自定义 action**——无执行引擎、无结果写入逻辑。
- **dict 现状**：`meta/quality-rule-type`（7 值，`quality-rule-type.dict.yaml`）、`meta/quality-entity-type`（field/table/database）、`meta/quality-result-status`（PASS/FAIL/ERROR/SKIP）、`meta/quality-severity` 均已存在。
- **连接服务（前置已 done）**：P2-1 提供 `IMetaDataSourceConnectionService.withConnection(datasourceType, connectionConfig, BiConsumer<Connection, DatabaseMetaData>)`（`nop-metadata-service/.../connection/IMetaDataSourceConnectionService.java:30`），bean 已注册。
- **可复用执行范式（P2-4 Catalog 已验证）**：`NopMetaDataSourceBizModel.collectCatalog`（`NopMetaDataSourceBizModel.java:190-238`）演示了 external 表执行模式——按 `querySpace` 查 external NopMetaTable（`findExternalTables`，`tableType='external'`，首版全表扫描可接受）→ `withConnection` callback → callback 内逐表 `SELECT COUNT(*) FROM <限定表名>` + JDBC 元数据 → 写入时序行 → 单表失败 per-table try/catch 收集 errors + `flushSession()`/`clearSession()` 隔离不中断整批。`MetaCatalogCollector.collectForTable`（`MetaCatalogCollector.java:50`）演示了无状态收集器 + schema 限定（`qualifyTable(schema, tableName)`，null 依赖默认 schema）。
- **NopMetaTable 现状**：`metaTableId`(PK) / `tableName` / `querySpace` / `tableType`(dict `meta/table-type`：`entity`/`sql`/`external`) / `buildSql`（external 表存列结构 JSON，A2 方案，**external 表无字段实体**）。external 表的 `querySpace` 取自数据源 querySpace（`NopMetaDataSource.querySpace` 唯一），故 external 表 → querySpace → NopMetaDataSource 解析路径成立。
- **设计契约**：`01-architecture-baseline.md` §2.7 定义 7 种规则类型 + 适用对象（field: not_null/unique/range/regex；table: freshness/volume/custom_sql）+ 结果模型。`06-data-quality-extended.md` §二 补充 `mostly` 容错参数（draft，含 Python 伪码 + Open Questions）。
- **执行机制建议（Decision 待定）**：`09-gap-analysis-extended.md` §4.4 建议复用 nop-batch processor（`MetaQualityRule → nop-batch processor → SQL 执行 → MetaQualityResult`）。但本模块既有 external 执行能力（Catalog/同步）均用 **BizModel action + withConnection**，且 BizModel action 可被 Nop AutoTest 端到端验证、可被 GraphQL 直接暴露。**执行机制选型 + 执行范围 + 物理解析路径为硬前置门禁（item 1.1）**。
- **物理定位缺口（Decision 待定）**：规则的 `entityId` → 物理表 + 物理列解析。`entityType=table` 时 entityId=NopMetaTable.metaTableId；`entityType=field` 时 entityId 语义对 external 表不成立（external 表无 MetaEntityField 实体，列在 buildSql JSON）。entity/sql 类型表的物理库定位需 `querySpace → 数据源` 解析（P2-4 Catalog 已裁定为 follow-up，entity/sql 收集未做）。**首版可执行范围 + field 级列引用方式为硬前置门禁（item 1.1）**。
- **测试基建**：Nop AutoTest 可用，P2-2/P2-4 已证明可在真实 H2 库建外部表（如 `EXT_DEPT`）被检测。`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（P2-5 后 44 tests 全绿）。

## Goals

- `executeQualityRule` GraphQL mutation action 可用：按 `qualityRuleId` 加载规则 → 解析目标物理表 + 数据源 → 复用 P2-1 `withConnection` callback 建连 → 按 ruleType 生成检测 SQL → 执行 → 判定 pass/fail → 追加一行 NopMetaQualityResult（status/actualValue/expectedValue/message/details，executeTime=now）
- 批量执行 action 可用：按 `dataSourceId`（或 metaTableId）执行该范围内所有可解析规则（复用 Catalog 的 querySpace→external 表 + per-rule 失败隔离 + errors 批次模式）
- 7 种内置 ruleType 的判定语义均有定义（D3）；其中**首版可执行子集**（按 item 1.1 裁定——至少 table 级 volume/freshness/custom_sql；field 级 not_null/unique/range/regex 取决于 item 1.1 对 external `params.column` 约定的实测裁定，若不可行则降级为 deferred）actualValue/expectedValue/details 按类型真实填充（不静默返回空、不伪造值）
- 失败/不可执行路径显式：规则不存在、目标表非 external（首版）、目标表无注册数据源、不支持的 ruleType/entityType 组合 → 显式失败或 SKIP（不静默通过、不吞异常）

## Non-Goals

- 数据剖析（profiling 统计/分布）—— P2-7，独立结果面（新建 MetaProfilingRule/Result 实体）
- 质量评分（MetaQualityScore）/ Checkpoint 编排（MetaQualityCheckpoint）—— `06-data-quality-extended.md` §四/§五，未建模实体，后续 plan
- **entity/sql 类型表的质量执行**（需 querySpace→数据源解析，与 P2-4 Catalog entity/sql 收集同源 deferred；首版仅 external）
- 定时自动执行调度（首版手动 action；定时为 follow-up）
- `mostly` 容错比例的完整 UI/配置（首版可在 details 记录，但规则模型无 mostly 列；mostly 全链路为 follow-up）
- 自定义 Expectation（`custom_expectation`）/ schema / fingerprint 规则类型（`06-data-quality-extended.md` 扩展类型，首版仅基线 §2.7 的 7 种）
- 规则执行的告警通知动作（store/notify/webhook，`06-data-quality-extended.md` §4.3 actions，后续）

## Design Decisions

> D1/D2/D3 为硬前置门禁，须在 item 1.1 裁定并写入 `01-architecture-baseline.md` §2.7 后实现。

### D1. 执行范围 + 物理解析路径 + field 级列引用（待 item 1.1 裁定）

- **执行范围**：首版仅 `entityType=table` 且目标 NopMetaTable 为 **external** 类型（已知注册数据源，与 P2-4 Catalog 一致）。`entityType=field` 与 entity/sql 类型表执行为 follow-up（依赖 querySpace→数据源解析与 field 实体引用语义裁定）。`entityType=database` 首版不支持（SKIP + details 标记原因），与 entity/sql 表执行同为 follow-up。
- **物理解析路径**：`qualityRule.entityId`（entityType=table → metaTableId）→ NopMetaTable.querySpace → NopMetaDataSource（`dataSource.querySpace == table.querySpace`，唯一）→ `withConnection(type, connectionConfig, ...)`。querySpace 找不到数据源 → 显式失败（不静默 SKIP 整批）。
- **field 级列引用裁定**（收口"external 表无字段实体"）：首版 `entityType=field` 规则在 external 表上的列引用，以**约定**方式——`entityId` 指向 NopMetaTable.metaTableId，物理列名取自 `params.column`（字符串）；item 1.1 确认此约定并写入设计文档，或裁定首版不支持 field 级（仅 table 级 volume/freshness/custom_sql）。**优先尝试支持 field 级（external，params.column 约定）**，以覆盖 not_null/unique/range/regex 这 4 类高价值检查；若 H2 测试库或解析复杂度过高则降级为仅 table 级，由 item 1.1 基于实测裁定。
- **schema 限定**：复用 Catalog 的 `qualifyTable(schema, tableName)` 策略——执行 action 可选 `schemaPattern` 参数限定物理 SQL；null 依赖连接默认 schema（多 schema 同名表为已知限制，与 Catalog 同源 follow-up）。

### D2. 执行机制选型（待 item 1.1 裁定）

- **选定 BizModel action + withConnection**（不选 nop-batch），理由：与本模块既有 external 执行能力（collectCatalog/syncExternalTables）一致；可被 Nop AutoTest 端到端验证；可被 GraphQL 直接暴露；nop-batch 适合离线批量调度但首版为手动 action。`09-gap-analysis-extended.md` §4.4 的 nop-batch 建议作为"定时调度"后续选项记录，首版不用。
- **action 落点 + 契约**：
  - `executeQualityRule(@Name("qualityRuleId") String id, @Optional @Name("schemaPattern") String schemaPattern, IServiceContext)` → 返回 `Map{qualityResultId, status, actualValue, expectedValue, message}`。单规则执行；规则不存在/不可解析目标表/无注册数据源 → 显式失败抛 inline ErrorCode。
  - `executeQualityRulesForDataSource(@Name("dataSourceId") String id, @Optional @Name("schemaPattern") String schemaPattern, IServiceContext)` → 返回 `Map{executedCount: int, results: [...], errors: [...]}`。批量执行该 querySpace 下 external 表挂载的规则；与 collectCatalog 同入口（dataSourceId）、同 callback 模式、同 per-rule 失败隔离。
  - mutation 暴露名按 GraphQL 约定（`NopMetaQualityRule__executeQualityRule` 等，落点 BizModel 由 item 1.1 确认放 QualityRuleBizModel 还是 DataSourceBizModel；推荐放 **NopMetaQualityRuleBizModel**——规则执行是规则对象的行为，单规则入口天然归属规则；批量入口亦放 QualityRuleBizModel 以保持 action 聚合）。

### D3. 规则判定语义（待 item 1.1 裁定并写入 §2.7）

> 以下为期望行为语义（算法规格），非类签名。item 1.1 据此确认 SQL 形态与字段填充，写入设计文档。

- `volume`（table）：`SELECT COUNT(*) FROM <限定表名>` → actualValue=行数；与 `params.minRows`/`params.maxRows`（或 threshold）比较 → pass/fail；expectedValue=阈值。
- `freshness`（table）：需时间戳列（`params.timestampColumn`，必填否则显式失败）→ `SELECT MAX(<tsCol>) FROM <限定表名>` → 计算 age(now - maxTs) 分钟 → 与 `params.maxAgeMinutes`/threshold 比较 → pass/fail；actualValue=ageMinutes，expectedValue=maxAgeMinutes。
- `custom_sql`（table）：执行 `rule.sqlExpression`（优先）或 `params.sql` → 期望返回单数值或单布尔 → actualValue=返回值；按 `params.expectPassWhen`（如 `eq 0`/`gt 0`/`true`）或默认"返回 0 = pass"判定 → pass/fail。SQL 不返回单值 → ERROR。
- `not_null`（field，external `params.column`）：`SELECT COUNT(*) FROM <限定表名> WHERE <col> IS NULL` → actualValue=nullCount；expectedValue=0（或 threshold 作为允许上限）→ nullCount<=阈值 pass。
- `unique`（field）：`SELECT COUNT(*) FROM (SELECT <col> FROM <限定表名> WHERE <col> IS NOT NULL GROUP BY <col> HAVING COUNT(*)>1) d` → actualValue=重复值数；expectedValue=0 → 0 pass。
- `range`（field）：`SELECT COUNT(*) FROM <限定表名> WHERE <col> IS NOT NULL AND (<col> < :min OR <col> > :max)`（min/max 来自 params）→ actualValue=越界数；expectedValue=0。
- `regex`（field）：`SELECT COUNT(*) FROM <限定表名> WHERE <col> IS NOT NULL AND <col> NOT <regex谓词> <pattern>`（regex 谓词方言相关；H2 支持 `REGEXP`；item 1.1 确认方言兼容，不支持方言记 SKIP+details 标记 unavailable）→ actualValue=不匹配数；expectedValue=0。
- **details 填充**：记录 `{ruleType, tableName, column?, threshold, params, ...}` 便于审计；失败原因（如"nullCount=3 超过阈值 0"）写入 message。
- **参数化与注入安全**：列名/schema/表名为标识符（不能参数化占位，须白名单校验或转义，防止标识符注入；item 1.1 确认校验方式）；比较值可用 PreparedStatement 参数。custom_sql 为用户显式提供（记录为已知风险，非自动注入面）。

## Scope

### In Scope

- `NopMetaQualityRuleBizModel`：新增 `executeQualityRule`（单规则）+ `executeQualityRulesForDataSource`（批量）action
- 质量规则执行器：解析规则→物理表+数据源→withConnection callback→按 ruleType 生成检测 SQL→执行→判定→写 NopMetaQualityResult
- 7 种内置 ruleType 判定语义（table 级 3 类 + field 级 4 类，field 级依赖 D1 external params.column 约定）
- AutoTest：建 external 表 + 录入规则 → 执行 → 断言 NopMetaQualityResult 写入真实 pass/fail + actualValue + 时序追加 + 失败隔离 + 不可执行路径显式失败

### Out Of Scope

- P2-7 数据剖析（独立结果面，新建实体）
- entity/sql 类型表执行（querySpace→数据源解析，follow-up）
- 质量评分 / Checkpoint / 告警动作（未建模实体，后续）
- 定时调度（follow-up）
- mostly 全链路（规则模型无 mostly 列，follow-up）

## Execution Plan

### Phase 1 - 执行引擎（executeQualityRule 单规则 + executeQualityRulesForDataSource 批量）

Status: completed
Targets: `nop-metadata/nop-metadata-service/.../entity/NopMetaQualityRuleBizModel.java`、新增质量规则执行器（参考 `MetaCatalogCollector` 无状态收集器模式）、`TestNopMetaQualityRuleBizModel.java`

- Item Types: `Decision`（D1 执行范围/物理解析/field 列引用 + D2 执行机制/action 落点 + D3 判定语义，硬前置）+ `Proof`（新功能：质量执行）

> **硬前置门禁（item 1.1）**：D1/D2/D3 必须先裁定（只裁定不写代码）——尤其执行范围（external-only）+ field 级 external 列引用约定 + 判定语义 + 标识符注入校验，写入设计文档后再实现执行逻辑。

- [x] 1.1 **执行范围 + 物理解析 + field 列引用 + 执行机制 + 判定语义决策（硬前置门禁，Decision only）**：基于 live repo 核查并裁定 D1/D2/D3——确认 H2 测试库可执行上述 7 类检测 SQL（尤其 field 级 external `params.column` 约定下的 not_null/unique/range/regex；regex REGEXP 兼容；freshness 的 MAX(timestamp)）；裁定执行范围（首版 external-only；entity/sql deferred；entityType=database 首版 SKIP+details）；裁定 field 级列引用（`entityId`=metaTableId + `params.column`=列名约定，或首版不支持 field 级并移入 Deferred）；裁定 action 落点（推荐 NopMetaQualityRuleBizModel）+ 契约；裁定 7 类判定语义 + actualValue/expectedValue/details 填充；裁定标识符（列名/表名/schema）白名单或转义校验防注入。**只裁定不写代码**。结论写入 `01-architecture-baseline.md` §2.7（执行范围 + 物理解析路径 + field 列引用约定 + 执行机制=BizModel action + 7 类判定语义 + 标识符注入防护）。**顺带修正 §2.7 中 severity 值大小写**（设计文档现写小写 error/warning/info，实际 dict `meta/quality-severity` 为大写 INFO/WARNING/ERROR，统一为大写）；**顺带把 `06-data-quality-extended.md` §二相关的执行部分收敛为最终设计状态**（去掉 Python 伪码、收敛 Open Questions 中与本 plan 相关项，满足 Rule 14）
- [x] 1.2 新增质量规则执行器（依赖 1.1，无状态，参考 `MetaCatalogCollector`）：输入 Connection + DatabaseMetaData + schemaPattern + NopMetaQualityRule + 目标 NopMetaTable（+ field 列名）→ 按 D3 生成检测 SQL（标识符白名单/转义，比较值用 PreparedStatement 参数）→ 执行 → 返回结构化判定结果（status/actualValue/expectedValue/message/details）。执行器无目录依赖（纯 SQL→判定），物理解析在 BizModel 层做
- [x] 1.3 在 `NopMetaQualityRuleBizModel` 新增 `@BizMutation executeQualityRule(@Name("qualityRuleId") String id, @Optional @Name("schemaPattern") String schemaPattern, IServiceContext context)`（依赖 1.1，落点见 D2）：加载规则 → 规则不存在抛 inline ErrorCode → 解析目标表（entityId→NopMetaTable）→ 非 external 抛/标 SKIP（按 D1）→ table.querySpace→NopMetaDataSource，无数据源抛 inline ErrorCode → `withConnection` callback → 执行器判定 → 追加一行 NopMetaQualityResult（executeTime=now）→ 返回结果 Map。DISABLED/非 jdbc 显式失败（继承 collectCatalog 模式，不静默）
- [x] 1.4 在 BizModel 新增 `@BizMutation executeQualityRulesForDataSource(@Name("dataSourceId") String id, @Optional @Name("schemaPattern") String schemaPattern, IServiceContext context)` → 返回 `Map{executedCount, results, errors}`：加载 NopMetaDataSource（not-found/DISABLED 显式失败）→ 找该 querySpace 的 external NopMetaTable → 找挂载在这些表上的规则（`entityType=table` 且 entityId∈表 id；field 级按 D1 约定）→ `withConnection` callback 内逐规则执行 → per-rule try/catch + flushSession/clearSession 隔离 → 写 NopMetaQualityResult → 批次返回
- [x] 1.5 错误码按现有模式在 BizModel 内 inline 定义（参考 `NopMetaDataSourceBizModel`/`NopMetaLineageEdgeBizModel` inline ErrorCode 用法）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] D1/D2/D3 决策已裁定并写入 §2.7；判定语义与限制在设计文档显式记录；`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS，既有回归测试全过
- [x] `executeQualityRule` 可通过 GraphQL mutation 调用：对挂载在 external 表上的规则执行后，`NopMetaQualityResult__findPage` 返回新增时序行（status 为 PASS/FAIL，actualValue 为真实检测结果）
- [x] 按 item 1.1 裁定的**首版可执行 ruleType 子集**均有判定：至少 table 级 volume/freshness/custom_sql；field 级 not_null/unique/range/regex（external params.column 约定）是否纳入取决于 item 1.1 实测裁定（若降级则移入 Deferred）。**首版支持的每种类型至少一条测试断言 pass 与 fail 两种结果**；未纳入首版的类型须在 Deferred But Adjudicated 显式记录
- [x] `executeQualityRulesForDataSource` 对 external 表批量执行，返回 executedCount/results/errors；单规则失败（SQL 异常/不支持方言）收集到 errors 不中断整批，其余规则仍执行并写入结果
- [x] 时序语义成立：重复执行同一规则追加新结果行（executeTime 不同），不覆盖旧行
- [x] 失败/不可执行路径显式：规则不存在 / 目标表非 external（首版）/ 无注册数据源 / DISABLED / 非 jdbc / 缺 timestampColumn(freshness) / custom_sql 不返回单值 → 显式失败或 SKIP（带 details 标记），**不静默通过、不吞异常、不伪造值**
- [x] **端到端验证**：从 `syncExternalTables`（建 external 表）→ 录入规则 → `executeQualityRule`/`executeQualityRulesForDataSource` → `NopMetaQualityResult__findPage` 可查到真实 pass/fail 的完整路径已验证（见 Minimum Rules #22）
- [x] **接线验证**：执行 action 运行时确实通过 P2-1 `withConnection` callback 建连并执行了检测 SQL（NopMetaQualityResult 写入真实 actualValue 证明），非空壳（见 Minimum Rules #23）
- [x] **无静默跳过**：不可执行路径显式失败/SKIP + details 标记；无空方法体 / 吞异常 / return null 占位（见 Minimum Rules #24）
- [x] **新功能测试**：新增测试覆盖 单规则执行（table 级 pass+fail；若 item 1.1 裁定支持 field 级则含 field 级 pass+fail）+ 批量执行（含单规则失败隔离）+ 时序追加 + 不可执行路径显式失败（not-found/非 external/无数据源/DISABLED/非 jdbc/缺 timestampColumn/`entityType=database` SKIP），全绿（见 Minimum Rules #25）
- [x] `ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.7（执行范围 + 物理解析 + field 列引用约定 + 执行机制 + 7 类判定语义 + 标识符注入防护）已更新；`06-data-quality-extended.md` 相关执行部分收敛为最终设计状态
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：所有条目 + Phase Exit Criteria 全部 `[x]` 后才能 `completed`。

- [x] 质量规则执行引擎可用（executeQualityRule 单规则 + executeQualityRulesForDataSource 批量）
- [x] 按 item 1.1 裁定的**首版可执行 ruleType 子集**判定语义正确，actualValue/expectedValue/details 真实填充；未纳入首版的类型已显式移入 Deferred
- [x] 时序语义成立（重复执行追加新行）
- [x] 失败/不可执行路径显式（不静默通过/不吞异常/不伪造）
- [x] 标识符注入防护成立（列名/表名/schema 白名单或转义，custom_sql 为已知显式风险）
- [x] 不存在空壳实现（无空方法体 / 静默跳过 / 吞异常）
- [x] 必要 focused verification 已完成（执行 + 判定 + 时序 + 失败隔离 + 不可执行路径测试全绿）
- [x] `./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（含测试）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [x] 受影响的 owner docs 已同步（`01-architecture-baseline.md` §2.7 + `06-data-quality-extended.md` 收敛）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证执行 action 运行时确实建连 + 执行检测 SQL + 写入真实判定结果（端到端连通）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-16-0530-1-nop-metadata-quality-rule-execution-engine.md --strict` 退出码 0

## Deferred But Adjudicated

### entity/sql 类型表的质量执行

- Classification: `optimization candidate`
- Why Not Blocking Closure: entity/sql 类型表的物理库定位需 `querySpace → 数据源` 解析（entity 的 querySpace 由引用实体决定），与 P2-4 Catalog 的 entity/sql 收集同源 deferred，复杂度独立。首版 external 类型表（已知注册数据源）已满足"质量规则可执行可产出时序结果"的核心结果面
- Successor Required: yes（entity/sql 执行作为后续增量 plan，与 Catalog entity/sql 收集一并解决 querySpace→数据源解析）

### 质量评分 / Checkpoint 编排 / 告警动作

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: MetaQualityScore / MetaQualityCheckpoint 为未建模实体（`06-data-quality-extended.md` §四/§五），属独立结果面（评分聚合 + 编排 + 通知），不在"规则执行产出时序结果"范围内
- Successor Required: yes（评分/Checkpoint 作为后续 plan）

## Non-Blocking Follow-ups

- 定时自动执行调度（当前手动 action；nop-batch 适配为定时后续，见 `09-gap-analysis-extended.md` §4.4）
- `mostly` 容错比例全链路（规则模型无 mostly 列；首版 details 记录，全链路需模型扩展属 Protected Area）
- 多 schema 数据源的质量执行（需为 NopMetaTable 增加 schema 列以持久化每表物理 schema，与 Catalog 同源 follow-up）
- NopMetaTable.querySpace 索引（首版按 querySpace+tableType 查找为全表扫描，外部表数量增长后需加索引，与 Catalog 同源）
- 自定义 Expectation/schema/fingerprint 扩展规则类型（`06-data-quality-extended.md` 扩展类型）

## Closure

Status Note: 质量规则执行引擎已落地——`executeQualityRule`（单规则）+ `executeQualityRulesForDataSource`（批量）两个 `@BizMutation` 通过 P2-1 `withConnection` callback 建连，对 7 类 ruleType（volume/freshness/custom_sql/not_null/unique/range/regex）生成并执行检测 SQL，按 D3 语义判定 pass/fail，追加写入 NopMetaQualityResult 时序行。首版 external 表 + field 级 external `params.column` 约定全量支持（item 1.1 实测裁定 H2 全 7 类可执行）；entity/sql 表执行与评分/Checkpoint 为诚实 Deferred（out-of-scope，非 live defect）。所有不可执行路径显式失败/SKIP（不静默通过）。
Completed: 2026-07-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（task_id=ses_0983578faffeSuC700RVn5PXRP，fresh session，未参与实现）
- Audit Session: ses_0983578faffeSuC700RVn5PXRP
- Evidence:
  - **Exit Criteria（11/11 PASS）**：① D1/D2/D3 决策已写入 `01-architecture-baseline.md` §2.7.1（line 448-479），`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS，57 tests 全绿；② executeQualityRule 经 GraphQL mutation 调用后 NopMetaQualityResult 写入真实 actualValue（TestExecuteVolumePassAndFail 断言 actualValue=3.0）；③ 7 类 ruleType pass+fail 均有断言（clean 表全 PASS / dirty 表全 FAIL，actualValue=1.0）；④ 批量 executedCount + 失败隔离（好规则 1 结果、坏规则 0 结果 + errors 记录）；⑤ 时序追加 2 行；⑥ 失败路径 hasError/SKIP 全显式；⑦-⑩ 端到端/接线/无静默跳过/新功能测试 13 用例全绿。
  - **Closure Gates（13/13 PASS）**：执行引擎可用、7 类判定真实填充、时序成立、失败显式、标识符注入防护（白名单 `^[A-Za-z_][A-Za-z0-9_]*$` + PreparedStatement for range/regex，custom_sql 已知显式风险）、无空壳、focused verification 全绿、BUILD SUCCESS、scan-hollow 0 findings、owner docs 同步、独立 audit 完成、Anti-Hollow 通过（执行器真实建连+执行检测 SQL）、check-plan-checklist 退出码 0。
  - **Anti-Hollow 追踪**：executeQualityRule → withConnection callback → executor.judge → queryLong/querySingleValue 真实执行 `SELECT COUNT(*)`/`MAX(ts)`/`NOT REGEXP ?` → 写入真实 actualValue（3.0/nullCount=1/dupGroups=1/outOfRange=1/notMatching=1）。端到端连通，无空方法体/吞异常/return-null 占位。
  - **identifier injection**：`MetaQualityRuleExecutor.IDENTIFIER_PATTERN`（line 37）白名单 + `validateIdentifier`（line 65/158/405）；range（line 307-324）+ regex（line 364-367）PreparedStatement 参数绑定；custom_sql 用户显式提供（Executor:204 + 设计 §2.7.1 D3 记录为已知风险）。
  - **Deferred 诚实性**：entity/sql 表执行（`optimization candidate`，需 querySpace→数据源解析，与 P2-4 Catalog entity/sql 同源 deferred，非 in-scope live defect）+ 评分/Checkpoint（`out-of-scope improvement`，`MetaQualityScore`/`MetaQualityCheckpoint` 未建模实体，roadmap line 153-154）。两项均诚实，无偷偷降级。
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（Critical/High/Medium/Low 全 0）。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-16-0530-1-nop-metadata-quality-rule-execution-engine.md --strict` 退出码 0。
  - `./mvnw clean install -pl nop-metadata -T 1C`：BUILD SUCCESS（含测试，TestNopMetaQualityRuleBizModel 13/0/0 + 既有 44 用例无回归）。

Follow-up:

- 定时自动执行调度（nop-batch/nop-job 适配，见 `09-gap-analysis-extended.md` §4.4）
- entity/sql 类型表执行（querySpace→数据源解析，与 Catalog entity/sql 收集一并的后续 plan）
- mostly 容错比例全链路（规则模型无 mostly 列，需 ORM 扩展属 Protected Area）
- 多 schema 数据源质量执行 + NopMetaTable.querySpace 索引（与 Catalog 同源 follow-up）
- 自定义 Expectation/schema/fingerprint 扩展规则类型（`06-data-quality-extended.md` 扩展类型）
