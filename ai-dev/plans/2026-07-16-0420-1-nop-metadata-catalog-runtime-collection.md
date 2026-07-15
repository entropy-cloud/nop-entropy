# 1 nop-metadata Catalog 运行时元数据收集

> Plan Status: completed
> Last Reviewed: 2026-07-16
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` P2（P2-4）；`ai-dev/design/nop-metadata/05-metadata-import.md` §四 Catalog 运行时元数据；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.2 数据源 / §2.5.1 外部表建模
> Mission: nop-metadata
> Work Item: P2-4 — MetaCatalog 运行时收集
> Related: `2026-07-16-0225-1-nop-metadata-datasource-registration-and-connection-verification.md`（提供连接服务）、`2026-07-16-0225-2-nop-metadata-external-table-metadata-sync.md`（提供外部表目录 + 结构读取器模式）、`2026-07-16-0225-3-nop-metadata-manifest-snapshot.md`（Manifest 是逻辑快照，Catalog 是物理运行时快照，两者独立）

## Purpose

让 nop-metadata 能从已注册 jdbc 数据源收集物理表的**运行时统计**（行数 / 大小 / 索引 / 分区），写入 `NopMetaCatalog`，使外部表（P2-2）和后续治理（质量 P2-6 体积检查、剖析 P2-7、治理看板）能基于一份可查的物理运行时快照工作。参考 dbt Catalog。本 plan 是 P2-2 的天然 successor：P2-2 写入"表有哪些列"（结构），P2-4 写入"表有多大 / 多少行 / 索引状态"（运行时）。

## Current Baseline

- **前置依赖（Prerequisite）**：P2-1（plan `0225-1`）提供 callback 式连接服务 `IMetaDataSourceConnectionService.withConnection(datasourceType, connectionConfig, BiConsumer<Connection, DatabaseMetaData>)`（`nop-metadata-service/.../connection/IMetaDataSourceConnectionService.java:30-31`），bean 已注册（`app-service.beans.xml`）。P2-2（plan `0225-2`）已落地 `tableType=external` 外部表目录（列结构序列化为 JSON 存 `NopMetaTable.buildSql`），并提供外部表结构读取器模式（`ExternalTableStructureReader`，在 `withConnection` callback 内调标准 JDBC `DatabaseMetaData.getTables()/getColumns()`）。本 plan 复用同一 callback 模式，**不自建连接**。本 plan 假设 P2-1/P2-2 已 done。
- **NopMetaCatalog 实体不存在**：`nop-metadata/model/nop-metadata.orm.xml` 当前 21+1（NopMetaManifest）实体中**无** NopMetaCatalog。`未建模实体` 表将其列为 P2 待建模（设计 `05-metadata-import.md` §四）。
- **设计契约**：`05-metadata-import.md` §1.3 + §四 明确：**Catalog 不是 Manifest 的来源**——Manifest 是逻辑元数据快照（基于已导入 ORM 元数据），Catalog 是物理运行时元数据快照。两者独立实现。"Catalog 的具体建模与收集实现属于 P2-4，不在本设计文档展开"——即建模形态由本 plan 裁定（硬前置门禁）。
- **NopMetaTable 现状**（`orm.xml:960-1021`）：`tableType` dict 含 `entity`/`sql`/`external`（P2-2 新增 external）。外部表 `metaModuleId` 指向系统模块 `nop/meta-external`，`querySpace` 取自数据源的 `querySpace`，列结构 JSON 存 `buildSql`。外部表通过 `(metaModuleId, tableName)` 复合键去重（`tableName` 上无唯一约束）。
- **关键建模缺口（影响 Catalog 收集）**：NopMetaTable **无 schema/dbSchema 列**——实体列只有 `tableName`（`orm.xml:971`），P2-2 的 `ExternalTableStructureReader` 虽在扫描时经 `DatabaseMetaData.getTables(schemaPattern,...)` 拿到 `TABLE_SCHEM`，但**未持久化**到 NopMetaTable。⇒ collectCatalog 在 `SELECT COUNT(*) FROM <表>` 时**无法从目录恢复每张外部表的物理 schema**。此约束直接决定首版 schema 限定策略（见 D1/D2），item 1.1 必须据此裁定降级方案。
- **查询性能现状**：NopMetaTable 仅有 `IX_NOP_META_TABLE_MODULE`(metaModuleId) 索引（`orm.xml:1017`）；按 `querySpace + tableType='external'` 查找外部表为**全表扫描**（首版外部表数量少，可接受；见 Non-Blocking Follow-ups）。
- **NopMetaDataSource 现状**：`dataSourceId`(PK) / `querySpace`(唯一) / `datasourceType`(jdbc/http/rest/file) / `connectionConfig`(json) / `status`(DISABLED/ACTIVE)。jdbc 连接验证可用（P2-1 `testConnection`）。
- **运行时统计可移植性（Decision 待定）**：行数可跨方言用 `SELECT COUNT(*) FROM <table>` 便携获取；大小/索引/分区依赖 DB 特定视图（MySQL `information_schema.TABLES`、PG `pg_class/pg_indexes`），H2（AutoTest 测试库）部分统计不可用（如分区、表物理大小）。**首版统计范围与降级策略为硬前置门禁（item 1.1）**。
- **收集范围（Decision 待定）**：外部表（tableType=external）有明确的注册数据源（P2-1/P2-2），可直接连接收集。entity/sql 类型表的物理库定位需 `querySpace → 数据源` 解析（entity 的 querySpace 由其引用实体决定），复杂度高。**首版收集范围为硬前置门禁（item 1.1）**。
- **测试基建**：Nop AutoTest 可用，P2-2 已证明可在真实 H2 库建外部表（如 `EXT_DEPT`）被 `syncExternalTables` 扫描。`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（P2-3 后 25 tests 全绿）。

## Goals

- 新建 `NopMetaCatalog` 实体（按 item 1.1 裁定的存储形态），承载物理运行时统计快照
- `collectCatalog` GraphQL mutation action 可用：按 `dataSourceId` 连接外部库 → 收集已目录化外部表的运行时统计（行数/索引等）→ 写入 NopMetaCatalog
- 幂等/时序：对同一表重复收集追加为新的快照行（时序，`collectedAt` 区分），支持"最近一次"与"趋势"查询
- 统计获取可移植：行数走 `SELECT COUNT(*)`（全方言）；索引走标准 `DatabaseMetaData.getIndexInfo()`；大小/分区按方言能力收集，**不可用时显式记 null 并在 details 标记 unavailable**（不静默跳过整行）

## Non-Goals

- 外部表结构同步本身（P2-2，前置 plan，提供"有哪些表/列"；本 plan 提供"有多大/多少行"）
- Manifest 逻辑快照（P2-3，已 done；Catalog 与 Manifest 独立，见设计 §1.3）
- 血缘采集（P2-5）/ 质量规则执行（P2-6）/ 数据剖析（P2-7）
- entity/sql 类型表的 Catalog 收集（需 `querySpace → 数据源` 解析，首版仅 external；entity/sql 收集为 follow-up）
- 定时自动收集调度（首版手动 action；定时为 follow-up）
- 多方言全覆盖的物理统计视图（MySQL `information_schema.TABLES.TABLES_ROWS`、PG `pg_class.relpages` 等近似统计；首版用便携的 COUNT + JDBC 元数据接口，方言特定统计为 follow-up）

## Design Decisions

> D1 为硬前置门禁，须在 item 1.1 裁定并写入 `05-metadata-import.md` §四 / `01-architecture-baseline.md` 后实现。

### D1. NopMetaCatalog 建模 + 收集范围（待 item 1.1 裁定）

**存储形态**（推荐）：
- **方案 A（选定方向）**：per-table 结构化快照行 `NopMetaCatalog`，真实列承载核心统计 + `details` JSON 承载扩展/方言特定字段。理由：Catalog 核心用途是"按表查行数/大小/索引状态"、"排序找最大表"、"趋势监控"，结构化列才可查可排序；单 CLOB（如 Manifest）不利于这类查询。
- 列设计（item 1.2 落地，至少）：`metaCatalogId`(PK) / `metaTableId`(→NopMetaTable, mandatory) / `rowCount`(long) / `sizeBytes`(long, nullable) / `indexCount`(int, nullable) / `partitionCount`(int, nullable) / `lastModified`(timestamp, nullable) / `details`(domain mediumtext + stdDomain json，承载 unavailable 标记/方言特定字段/列级统计) / `collectedAt`(timestamp, mandatory) + 审计列。
- `details` 用 `mediumtext + stdDomain json`（不得用 `json-4000`，列级统计可能超长，对齐 Manifest D2 决策）。

**收集范围**（推荐）：
- **首版仅 external 类型表**（`tableType=external`）：它们有明确注册数据源（P2-1/P2-2），`collectCatalog(dataSourceId)` 连接该数据源 → 找到该 querySpace 下的 external NopMetaTable → 按其 `tableName` 收集统计。
- entity/sql 类型表收集（需 querySpace→数据源解析）为显式 follow-up。

**统计获取与降级策略**（推荐）：
- 行数：`SELECT COUNT(*) FROM <限定表名>`（便携，全方言含 H2）。**schema 限定**：因 NopMetaTable 不持久化 schema（见 Current Baseline 建模缺口），首版策略为——`collectCatalog` 的 `schemaPattern` 参数用于限定 COUNT/索引查询的物理 schema：传入时用 `<schemaPattern>.<tableName>`；**不传时依赖连接默认 schema**（H2=PUBLIC、单 schema 数据源正常；多 schema 数据源下不同 schema 同名表无法区分——为已知限制，见 Non-Blocking Follow-ups，彻底解决需为 NopMetaTable 增加 schema 列属 P2-2 实体 Protected Area 变更，不在本 plan）。
- 索引：标准 `DatabaseMetaData.getIndexInfo(catalog, schemaPattern, table, ...)`（便携，schema 参数同上策略）→ `indexCount`。
- 大小/分区：方言特定。首版对不支持/查不到的统计记 `null` + `details.unavailable = ["sizeBytes","partitionCount"]` 显式标记，**不静默跳过整行**，也不伪造 0。
- `details` 首版实际承载：unavailable 标记 + 方言特定字段（如 DB 产品名）；列级统计**不在首版**（与 P2-7 重叠，见 Non-Goals）。
- 单表失败（SQL 异常）收集到 errors 不中断整批（`orm().clearSession()` 隔离，对齐 P2-2 模式）。

### D2. collectCatalog action 契约

- **落点（裁定）**：action 放在 **`NopMetaDataSourceBizModel`**（与现有 `testConnection`/`syncExternalTables` 一致——三者均以 `dataSourceId` 为入口键，操作对象是数据源而非单条 Catalog 行）。GraphQL mutation 名为 **`NopMetaDataSource__collectCatalog`**。
- **签名**：`@BizMutation collectCatalog(@Name("dataSourceId") String id, @Name("schemaPattern") String schemaPattern, IServiceContext context)` → 返回 `Map{collectedCount: int, errors: [...]}`。**`schemaPattern` 的作用**：限定 COUNT/索引查询的物理 schema（`<schemaPattern>.<tableName>` / `getIndexInfo(catalog, schemaPattern, table)`），**不过滤 NopMetaTable 行**（schema 不存于该表）；null 时依赖连接默认 schema（见 D1）。
- **行为**：加载 NopMetaDataSource → 不存在抛 inline ErrorCode `metadata.datasource-not-found`（不 NPE）→ `status==DISABLED` 抛 `metadata.datasource-disabled`（不静默通过）→ **复用 P2-1 `withConnection` callback 建连**（callback 内取方言 + 遍历该 querySpace 的 external NopMetaTable + 收集统计）→ 写入 NopMetaCatalog（每次追加新行，`collectedAt=now`）→ 单表失败收集 errors 不中断 → callback 结束自动释放连接。
- **非 jdbc 类型**：连接服务显式抛 `UnsupportedOperationException`（继承 P2-1/P2-2 行为，不静默成功）。

## Scope

### In Scope

- `nop-metadata/model/nop-metadata.orm.xml`：新增 `NopMetaCatalog` 实体（按 D1 形态）+ to-one metaTable 关系
- 新增 Catalog 收集器：在 `withConnection` callback 内按表收集 行数/索引/大小/分区 + 降级标记
- `NopMetaDataSourceBizModel`：新增 `collectCatalog` action（落点见 D2，与 testConnection/syncExternalTables 同 BizModel）
- AutoTest：syncExternalTables 建外部表 → collectCatalog → 断言 NopMetaCatalog 写入 rowCount + 单表失败收集 errors + 不支持统计显式 unavailable

### Out Of Scope

- P2-1 连接服务（前置）/ P2-2 结构同步（前置）
- Manifest（P2-3，独立）
- entity/sql 类型表收集（follow-up，需 querySpace 解析）
- 血缘/质量/剖析（P2-5/P2-6/P2-7）
- 定时调度、方言特定物理统计视图（follow-up）

## Execution Plan

### Phase 1 - NopMetaCatalog 建模 + collectCatalog 收集 action

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml`（新增 NopMetaCatalog）、新增 Catalog 收集器、`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataSourceBizModel.java`（新增 `collectCatalog`）、`TestNopMetaDataSourceBizModel.java`

- Item Types: `Decision`（D1 建模 + 收集范围 + 降级策略，硬前置）+ `Proof`（新功能：Catalog 收集）

> **硬前置门禁（item 1.1）**：D1 建模/范围/降级策略必须先裁定（只裁定不写代码），写入设计文档后再落地 ORM 实体与收集逻辑。

- [x] 1.1 **建模 + 收集范围 + 降级 + schema 限定决策（硬前置门禁，Decision only）**：基于 live repo 核查并裁定 D1——确认 H2/AutoTest 测试库可提供哪些统计（COUNT 必有；`getIndexInfo` 可用；大小/分区 H2 是否可用）；确认 external 表经 `querySpace` 关联数据源的查询路径（**已知全表扫描，首版可接受**）；裁定存储形态（方案 A per-table 结构化行）、收集范围（首版仅 external）、降级策略（不可用统计 null + details.unavailable 标记）、**schema 限定策略（D1：schemaPattern 限定 COUNT/索引 SQL，null 依赖默认 schema；多 schema 为已知限制）**。**只裁定不写代码**。结论写入 `05-metadata-import.md` §四（Catalog 建模/范围/降级/schema 限定）+ `01-architecture-baseline.md`（NopMetaCatalog 实体说明）。**顺带把 §四从"不在本文档展开"收敛为最终设计状态**（满足 Rule 14）
- [x] 1.2 **NopMetaCatalog 实体落地（Proof，依赖 1.1）**：按 D1 方案 A 在 `nop-metadata.orm.xml` 新增 `NopMetaCatalog`（列见 D1；`details` 用 `domain="mediumtext"` + `stdDomain="json"`，不得 json-4000）+ to-one metaTable 关系 + 索引（`IX_NOP_META_CATALOG_TABLE` on metaTableId；时序查询用 `(metaTableId, collectedAt)`）。运行 `./mvnw clean install -pl nop-metadata -T 1C` 重新生成代码确认 BUILD SUCCESS，生成实体类与 CRUD
- [x] 1.3 新增 Catalog 收集器（依赖 1.1）：输入 Connection + DatabaseMetaData + dialect + schemaPattern + 该 querySpace 的 external NopMetaTable 列表 → 逐表收集：`SELECT COUNT(*) FROM <schemaPattern?.><tableName>`（schemaPattern null 时用 `<tableName>` 依赖默认 schema）→ rowCount；`DatabaseMetaData.getIndexInfo(catalog, schemaPattern, table, ...)` → indexCount；大小/分区按方言能力查（查不到 null）；`lastModified` 按方言（MySQL/PG 系统视图，H2 可能 null）；未获取的统计写入 `details.unavailable`。在 P2-1 `withConnection` callback 内调用（不自建连接）
- [x] 1.4 在 `NopMetaDataSourceBizModel` 新增 `@BizMutation collectCatalog(@Name("dataSourceId") String id, @Name("schemaPattern") String schemaPattern, IServiceContext context)`（依赖 1.1，落点见 D2，mutation 名 `NopMetaDataSource__collectCatalog`），实现 D2：加载 NopMetaDataSource → 不存在抛 `metadata.datasource-not-found`（不 NPE）→ DISABLED 抛 `metadata.datasource-disabled` → 复用 `withConnection` callback 建连 → callback 内查该 querySpace 的 external NopMetaTable（`tableType='external'`，**全表扫描首版可接受**）→ 收集器逐表收集 → 每表追加一行 NopMetaCatalog（`collectedAt=now`）→ 单表失败收集 errors + `orm().clearSession()` 隔离不中断整批 → callback 结束自动释放 → 返回 `Map{collectedCount, errors}`
- [x] 1.5 错误码按现有模式在 BizModel 内 inline 定义（参考 `NopMetaDataSourceBizModel` 内 inline ErrorCode 用法）

> **门禁说明**：本 Phase 为单 Phase 含硬前置 Decision（1.1）+ 实现（1.2-1.5，均依赖 1.1）。**Phase 实现项（1.2-1.5）在 1.1 勾选为 `[x]` 前不得开工**——1.1 可能推翻存储形态/范围/schema 策略的推荐方向，先裁定可避免返工（与 sibling plans 0225-1/2/3 的硬前置门禁模式一致）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] D1 决策已裁定并落地，且**可观测不变量成立**：`NopMetaCatalog` 实体已生成代码（`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS）；`meta/table-type` dict 仍含 entity/sql/external（未破坏 P2-2）；既有 `TestNopMetaModuleBizModel`/`TestNopMetaDataSourceBizModel` 回归测试全过
- [x] `NopMetaDataSource__collectCatalog` 可通过 GraphQL mutation 调用，对已 syncExternalTables 的数据源收集后 `NopMetaCatalog__findPage` 返回新增快照行（rowCount 非 null、为真实 COUNT 结果）
- [x] schema 限定策略成立：传入 `schemaPattern` 时 COUNT/索引按该 schema 限定；不传时依赖连接默认 schema（H2 测试库可验证；多 schema 限制已在 D1 记录为已知 follow-up）
- [x] 重复收集同一数据源追加为新的时序快照行（`collectedAt` 不同），不覆盖旧行（时序语义成立）
- [x] 不支持的统计（如 H2 的 sizeBytes/partitionCount）为 null 且 `details.unavailable` 显式列出该字段名（不静默跳过整行、不伪造 0）
- [x] 不存在的 dataSourceId / DISABLED / 非 jdbc：显式失败，不静默跳过
- [x] 单表失败（SQL 异常）收集到 errors 不中断整批，其余表仍收集
- [x] **端到端验证**：从 `syncExternalTables`（建外部表）→ `collectCatalog` → `NopMetaCatalog__findPage` 可查到真实 rowCount 的完整路径已验证
- [x] **接线验证**：collectCatalog 运行时确实通过 P2-1 `withConnection` callback 建连，并在 callback 内执行了 COUNT/索引查询（NopMetaCatalog 写入真实 rowCount 证明），非空壳
- [x] **无静默跳过**：统计不可用显式 unavailable 标记；单表失败收集 errors；DISABLED/非 jdbc/不存在显式失败；无空方法体或吞异常
- [x] **新功能测试**：新增测试覆盖 收集写入（真实 rowCount）+ 重复收集时序追加 + 不支持统计 unavailable 标记 + 单表失败收集 errors + DISABLED/不存在/非 jdbc 显式失败，全绿
- [x] `ai-dev/design/nop-metadata/05-metadata-import.md` §四 Catalog 建模/范围/降级（按 D1）已更新且收敛为最终设计状态；`01-architecture-baseline.md` NopMetaCatalog 实体说明已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：所有条目 + Phase Exit Criteria 全部 `[x]` 后才能 `completed`。

- [x] NopMetaCatalog 实体已建模并重新生成代码
- [x] collectCatalog 端到端可用（建外部表 → 收集 → NopMetaCatalog 可查真实统计）
- [x] 时序语义成立（重复收集追加新行，不覆盖）
- [x] 降级策略成立（不支持统计 null + unavailable 标记，不静默跳过）
- [x] 不存在空壳实现（无空方法体 / 静默跳过 / 吞异常）
- [x] 必要 focused verification 已完成（收集 + 时序 + 降级 + 失败路径测试全绿）
- [x] `./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（含测试）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [x] 受影响的 owner docs 已同步（`05-metadata-import.md` §四 + `01-architecture-baseline.md` NopMetaCatalog）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 collectCatalog 运行时确实建连 + 执行真实 COUNT/索引查询 + 写入统计（端到端连通）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-16-0420-1-nop-metadata-catalog-runtime-collection.md --strict` 退出码 0

## Deferred But Adjudicated

### entity/sql 类型表的 Catalog 收集

- Classification: `optimization candidate`
- Why Not Blocking Closure: entity/sql 类型表的物理库定位需 `querySpace → 数据源` 解析（entity 的 querySpace 由引用实体决定），复杂度独立。首版 external 类型表（有明确注册数据源）已满足"物理运行时快照可目录化"的核心结果面。entity/sql 收集不阻塞 external Catalog 结果面成立
- Successor Required: yes（entity/sql 收集作为后续增量 plan）

### 方言特定物理统计视图（近似行数/relpages 等）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 首版用便携的 `SELECT COUNT(*)` + 标准 JDBC `getIndexInfo()` 已满足核心统计（行数/索引精确）。近似统计（MySQL `information_schema.TABLES.TABLE_ROWS`、PG `pg_class.relpages`）为性能优化，不阻塞精确统计结果面成立
- Successor Required: no

## Non-Blocking Follow-ups

- 定时自动收集调度（当前手动 action）
- Catalog 趋势查询 action（最近 N 次快照对比；当前按 metaTableId 时序查询即可，趋势聚合 action 为后续）
- entity/sql 类型表收集（需 querySpace → 数据源解析）
- 列级统计（与 P2-7 数据剖析部分重叠）
- 多 schema 数据源的 Catalog 收集（需为 NopMetaTable 增加 schema 列以持久化每表物理 schema，属 P2-2 实体 Protected Area 变更；当前 schemaPattern 仅能限定单一 schema）
- NopMetaTable.querySpace 索引（首版按 querySpace+tableType 查找为全表扫描，外部表数量增长后需加索引）

## Closure

Status Note: P2-4 MetaCatalog 运行时收集完成。NopMetaCatalog 实体已建模并生成代码；collectCatalog action 通过复用 P2-1 withConnection callback 端到端可用（建外部表 → 收集 → NopMetaCatalog 可查真实 rowCount/索引）；时序追加语义、降级策略（不支持统计 null + details.unavailable 显式标记，不静默跳过/不伪造）、失败路径（not-found/DISABLED/非 jdbc 显式失败、单表失败 batched 到 errors）均成立且由 7 个新增测试覆盖。独立子 agent closure-audit 13 项全部 PASS（含 Anti-Hollow 端到端调用链追踪）。
Completed: 2026-07-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（explore 类型，fresh session，非实现 session）
- Audit Session: ses_09873f5a9ffenNx5OByD7dndUa
- Evidence:
  - 每条 Exit Criterion：PASS（live code path + test name）
    - D1 裁定落地 + 不变量：PASS — `nop-metadata.orm.xml:1610-1669` NopMetaCatalog 实体；`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS；`meta/table-type` dict 仍含 entity/sql/external（orm.xml:25-29）；回归测试 32 全过
    - collectCatalog GraphQL 可调用 + 真实 rowCount：PASS — `testCollectCatalogWritesRealRowCount` 断言 rowCount==5（5 行真实 COUNT）+ indexCount>=1
    - schema 限定：PASS — `testCollectCatalogDefaultSchemaNoPattern` 不传 schemaPattern 依赖 H2 默认 PUBLIC schema，rowCount==2
    - 时序追加：PASS — `testCollectCatalogAppendsWithSchemaPattern` 第二次 collect 后 countCatalogRows==2（不覆盖）
    - 降级 unavailable 标记：PASS — test 断言 sizeBytes/partitionCount/lastModified null + details 含 "unavailable"/各字段名（不伪造 0）
    - not-found/DISABLED/非 jdbc 显式失败：PASS — `NopMetaDataSourceBizModel.java:195/200` 抛 NopException；非 jdbc 由 `MetaDataSourceConnectionService.java:116` 抛 UnsupportedOperationException；3 个 hasError 测试
    - 单表失败 batched：PASS — `testCollectCatalogSingleTableErrorBatched` EXT_GONE 进 errors + collectedCount==1 + EXT_GONE 无 catalog 行
    - 端到端：PASS — sync→collect→NopMetaCatalog__findPage 真实 rowCount 验证
    - 接线验证：PASS — `NopMetaDataSourceBizModel.java:212`(withConnection) → `:217`(collectForTable) → `:265`(saveEntity)；`MetaCatalogCollector.java:77-84`(真实 COUNT) + `:92`(真实 getIndexInfo)
    - 无静默跳过：PASS — degradation 显式 null+unavailable；失败 batched 到 errors；DISABLED/非 jdbc/不存在显式失败；无空方法体/吞异常
    - 新功能测试：PASS — 7 个 collectCatalog 测试全绿
    - owner docs 更新：PASS — `05-metadata-import.md` §四 4.1-4.6 最终设计（"不在本文档展开"已移除）；`01-architecture-baseline.md` §2.3.2 NopMetaCatalog
    - daily log：PASS — `ai-dev/logs/2026/07-16.md` 已追加 Plan 0420-1 条目
  - 每条 Closure Gate：PASS（见上 12 条勾选 + evidence）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-16-0420-1-nop-metadata-catalog-runtime-collection.md --strict` 退出码 0（见下方复跑）
  - Anti-Hollow 检查结果：端到端调用链连通——`collectCatalog`→`withConnection`callback→`collectForForTable`(真实 SELECT COUNT + getIndexInfo)→`appendCatalogRow`(saveEntity)；`testCollectCatalogWritesRealRowCount` 仅当真实 COUNT 跑通才能 rowCount==5；`scan-hollow-implementations.mjs` High/Critical=0 退出码 0
  - Deferred 项分类检查：entity/sql 收集=optimization candidate、方言特定统计视图=optimization candidate，均带 Why Not Blocking Closure，无 in-scope live defect 被降级

Follow-up:

- 定时自动收集调度（当前手动 action）
- Catalog 趋势查询 action（当前按 metaTableId 时序查询即可，趋势聚合 action 为后续）
- entity/sql 类型表收集（需 querySpace→数据源解析）
- 列级统计（与 P2-7 数据剖析重叠）
- 多 schema 数据源 Catalog 收集（需为 NopMetaTable 增加 schema 列，属 P2-2 Protected Area 变更）
- NopMetaTable.querySpace 索引（首版按 querySpace+tableType 查找为全表扫描，外部表数量增长后需加索引）
- 方言特定物理统计视图（近似行数/relpages/真实大小/分区）
