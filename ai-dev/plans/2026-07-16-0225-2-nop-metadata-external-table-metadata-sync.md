# 2 nop-metadata 外部表元数据同步

> Plan Status: active
> Last Reviewed: 2026-07-16
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` P2（P2-2）；`ai-dev/design/nop-metadata/05-metadata-import.md` §4.4 数据库适配；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.2 / §2.5
> Mission: nop-metadata
> Work Item: P2-2 — 外部表元数据同步
> Related: `2026-07-16-0225-1-nop-metadata-datasource-registration-and-connection-verification.md`（前置 plan，提供连接服务）；`292-nop-metadata-implementation-roadmap.md`

## Purpose

让 nop-metadata 能从已注册的 jdbc 数据源（MySQL/PostgreSQL/ClickHouse）扫描物理表结构，写入元数据目录，使外部表成为可被 Catalog 收集（P2-4）、可被血缘（P2-5）/质量（P2-6）引用的目录化资产。本 plan 是 P2 的"外部数据接入"层，依赖 P2-1 的连接服务。

## Current Baseline

- **前置依赖（Prerequisite）**：P2-1（plan `2026-07-16-0225-1-...`）提供 **callback 式**连接服务 `withConnection(datasourceType, connectionConfig, BiConsumer<Connection, DatabaseMetaData>)`（P2-1 item 1.1/1.2 裁定并实现）。本 plan 在 callback 内执行 N 条 `information_schema` 查询，复用 P2-1 的建连/释放逻辑，**不自建连接**。**本 plan 假设 P2-1 已 done**；若 P2-1 未 done，本 plan Phase 1 无法执行。方言不依赖 P2-1 持久化（P2-1 不写 extConfig），由本 plan 在 callback 内调用 `DatabaseMetaData.getDatabaseProductName()` 运行时获取。
- **NopMetaTable 实体已建模**（`nop-metadata.orm.xml:958-1020`）：字段 `metaTableId`(PK) / `metaModuleId`(**mandatory**) / `tableName` / `displayName` / `tableType`(dict `meta/table-type`：`entity`/`sql`) / `querySpace` / `sourceSql` / `baseEntityId`(→MetaEntity) / `buildSql` + 审计。有 to-one `metaModule`。索引 `IX_NOP_META_TABLE_MODULE`（**非唯一**；`tableName` 上无唯一约束）。
- **NopMetaEntityField 实体已建模**（`nop-metadata.orm.xml:457`）：字段级实体，外键列实际名为 `metaEntityId`(→MetaEntity, mandatory)。**存储字段需要先有 MetaEntity**。
- **NopMetaEntity 预检**：`NopMetaEntity.ormModelId` 已确认 mandatory（`nop-metadata.orm.xml:367`）——这直接决定方案 B（合成 MetaEntity）成本高（需再造合成 OrmModel），**item 1.1a 基于此倾向于方案 A**。
- **关键建模缺口（Decision 必须裁定）**：
  1. `NopMetaTable.metaModuleId` 是 mandatory，但**外部表不属于任何平台模块**（外部库无 MetaModule）。
  2. `NopMetaTable.tableType` 当前只有 `entity`/`sql`，外部物理表两者都不是（entity=包装 ORM 实体；sql=用户 SQL 视图）。
  3. 外部表字段存储：roadmap 写"写入 NopMetaEntityField"，但 NopMetaEntityField 需 `metaEntityId`→MetaEntity，外部表无 MetaEntity。
  - ⇒ 三者共同指向一个建模决策：是否引入 `tableType="external"` + 一个承载外部表的"系统模块" + 外部表字段存储方案。**item 1.1a 为硬前置门禁**（只裁定），1.1b 落地。
- **无外部表扫描/同步能力**：`NopMetaModuleBizModel.importOrmModel` 只解析平台 `orm.xml`（P1），不连接外部库。`information_schema`/`system.columns` 读取逻辑不存在。
- **方言差异**（设计 `05-metadata-import.md` §4.4）：MySQL/PG 用 `information_schema.COLUMNS/TABLES`，ClickHouse 用 `system.columns/parts`。首版方言范围在 item 1.2 确定。
- **测试基建**：Nop AutoTest 可用，测试库可作"外部库"被扫描。`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS。

## Goals

- `syncExternalTables` GraphQL mutation action 可用：按 `dataSourceId` 连接外部库 → 扫描表结构（列名/类型/可空/注释）→ 写入元数据目录
- 外部表在目录中有明确的建模归属（通过 item 1.1 的 Decision 落地，不破坏 `metaModuleId mandatory` 等现有不变量）
- 支持至少一种方言（MySQL 或 PostgreSQL，按测试库能力选定）的列结构读取；其他方言显式 `UnsupportedOperationException`
- 幂等/增量：对已同步的表重复同步更新字段而非无限追加（按 **`(metaModuleId, tableName)` 复合键** 去重 upsert，因 `tableName` 上无唯一约束、跨 schema 可能同名）

## Non-Goals

- 连接服务本身（P2-1，前置 plan）
- 行数/大小/索引/分区等运行时统计（P2-4 Catalog 收集，不在本 plan）
- 血缘采集（P2-5）/ 质量执行（P2-6）/ 数据剖析（P2-7）
- SQL 视图创建（P3）
- 多方言全覆盖（首版至少一种；其余显式失败，见 Goals）
- 全库自动定时同步调度（本 plan 仅手动触发 action；定时为 follow-up）

## Design Decisions

> **核心建模决策 D1 为硬前置门禁**，必须在 item 1.1 裁定并写入 `01-architecture-baseline.md` 后才能实现同步逻辑。下列为推荐裁定方向（最终以 item 1.1 live 验证为准）。

### D1. 外部表建模归属（待 item 1.1 裁定）

候选方案（item 1.1a 评估后选定其一，1.1b 落地）：
- **方案 A（推荐）**：引入 `tableType="external"`；新建一个系统模块（`moduleId="nop/meta-external"`，status=RELEASED）作为外部表的归属，所有外部表 `metaModuleId` 指向它；外部表字段**子方案二选一**（1.1a 必须裁定）：(A1) 新建轻量字段存储实体（涉及 ORM 新实体，**Protected Area**），或 (A2) 字段集序列化为 JSON 存入 `buildSql`/`extConfig`。
- **方案 B**：复用 `tableType="entity"`，为每个外部表创建合成 MetaEntity（无 OrmModel 来源，但 `NopMetaEntity.ormModelId` 已 mandatory，需再造合成 OrmModel，成本高），字段进 NopMetaEntityField（metaEntityId 指向合成 MetaEntity）。
- **方案 C**：放宽 `metaModuleId` 为 nullable，外部表 metaModuleId=null（破坏现有 mandatory 不变量，需迁移评估）。

> 三方案对现有不变量影响不同。**item 1.1a 必须基于 live repo 核查**（`metaModuleId` mandatory、`ormModelId` 已确认 mandatory → 方案 B 成本高、`meta/table-type` dict 可否加值、字段存储子方案），**只裁定不写代码**；1.1b 才落地。若选定方案涉及 ORM 新实体/列，显式标注 **Protected Area: ORM 模型结构变更，plan-first**，并同步更新 `meta/table-type` dict（如选 A）与架构基线 §2.5。系统模块（方案 A）的初始化（seed/初始化代码）属于功能可用的前置条件，须在 1.1b 一并落地，不是 follow-up。

### D2. syncExternalTables action 契约

- **签名**：`@BizMutation syncExternalTables(@Name("dataSourceId") String id, @Name("schemaPattern") String schemaPattern, IServiceContext context)` → 返回 `Map{syncedTableCount: int, errors: [...]}`（schemaPattern 可选，限定扫描的 schema）
- **行为**：加载 NopMetaDataSource → 校验 status != DISABLED → **复用 P2-1 callback 式连接服务 `withConnection(...)`**（在 callback 内读 `DatabaseMetaData.getDatabaseProductName()` 取方言 + 执行读取查询）→ 按 D1 方案写入目录。单表失败收集到 errors 不中断整批（失败信息显式记录，非静默跳过）

## Scope

### In Scope

- `NopMetaDataSourceBizModel`（或新建 BizModel）：新增 `syncExternalTables` action
- 外部表结构读取器（按方言：至少 MySQL 或 PG 的 `information_schema` 读取）
- item 1.1 的建模方案落地（可能涉及 `meta/table-type` dict 新增、系统模块初始化、或字段存储实体）
- 幂等 upsert（按 `(metaModuleId, tableName)` 复合键去重）
- AutoTest：扫描测试库 → 断言 MetaTable/MetaEntityField 写入 + 重复同步不重复追加

### Out Of Scope

- P2-1 连接服务（前置）
- P2-4 Catalog 运行时统计
- P2-5/P2-6/P2-7
- 定时调度、全方言覆盖、SQL 视图（P3）

## Execution Plan

### Phase 1 - 外部表建模决策 + 同步 action

Status: planned
Targets: `nop-metadata/model/nop-metadata.orm.xml`（如选方案 A/B 需改 dict 或加实体）、`nop-metadata/nop-metadata-service/.../entity/NopMetaDataSourceBizModel.java`（或新 BizModel）、新增结构读取器、`TestNopMetaDataSourceBizModel.java`

- Item Types: `Decision`（D1 建模归属，硬前置）+ `Proof`（新功能：外部表同步）

> **硬前置门禁（item 1.1a）**：D1 建模决策必须先裁定（只裁定不写代码）。未裁定前不实现同步写入，避免返工。

- [ ] 1.1a **建模决策（硬前置门禁，Decision only）**：基于 live repo 核查并裁定 D1 方案——`NopMetaTable.metaModuleId` mandatory、`NopMetaEntity.ormModelId` 已 mandatory（→ 方案 B 成本高）、`meta/table-type` dict 可否加值；选定方案 A/B/C 及方案 A 的字段存储子方案 A1/A2（倾向于 A）。**只裁定不写代码**。若涉及 ORM 新实体/列变更，标注 Protected Area。结论写入 `01-architecture-baseline.md` §2.5
- [ ] 1.1b **建模方案落地（Proof，依赖 1.1a）**：按 1.1a 裁定落地——方案 A：新增 `meta/table-type` 的 `external` 枚举值 + 系统模块 `nop/meta-external` 初始化（seed/初始化代码）+ 字段子方案 A1/A2 落地；方案 B：合成实体规则；方案 C：mandatory 放宽迁移。运行 `./mvnw clean install -pl nop-metadata -T 1C` 重新生成代码确认 BUILD SUCCESS
- [ ] 1.2 **方言范围裁定**：确定首版支持的方言（MySQL 或 PostgreSQL，按测试库能力选定）。读取列结构的 SQL（`information_schema.COLUMNS`：column_name/data_type/is_nullable/column_default/ordinal_position/...）。方言**由 callback 内 `DatabaseMetaData.getDatabaseProductName()` 运行时获取**，不依赖 P2-1 持久化。其他方言在读取器入口显式 `UnsupportedOperationException`
- [ ] 1.3 新增外部表结构读取器：输入 Connection + schemaPattern → 执行 `information_schema` 查询 → 返回表+列结构列表（在 P2-1 `withConnection` callback 内调用）
- [ ] 1.4 在 BizModel 新增 `@BizMutation syncExternalTables(...)`，实现 D2：通过 P2-1 `withConnection` callback 建连 → callback 内取方言 + 读取器扫描 → 按 D1 方案写入 MetaTable + 字段 → 幂等 upsert（按 **`(metaModuleId, tableName)` 复合键**去重，重复同步更新而非追加）→ 单表失败收集到 errors 不中断（`orm().clearSession()` 隔离）→ callback 结束自动释放连接
- [ ] 1.5 DISABLED 数据源 / 非 jdbc 类型 / 不支持的方言：显式抛异常或收集到 errors，不静默跳过

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。

- [ ] D1 建模决策已裁定并落地，且**可观测不变量成立**：`meta/table-type` dict 仍含 `entity`/`sql`（如选 A 则新增 `external`）；除非显式选方案 C 并记录迁移，否则 `NopMetaTable.metaModuleId` 在 orm.xml 中仍为 `mandatory="true"`；既有 `TestNopMetaModuleBizModel` 等回归测试全过（证明 entity/sql 路径未破坏）
- [ ] `syncExternalTables` 可通过 GraphQL mutation 调用，对支持的方言库扫描后 `NopMetaTable__findPage` 返回新增外部表（tableType 按方案 A/B/C 落地）
- [ ] 外部表字段（列名/类型/可空）被正确写入目录（按方案 A 的 ext/字段记录，或方案 B 的 NopMetaEntityField）
- [ ] 重复同步同一数据源不重复追加（按 `(metaModuleId, tableName)` 复合键 upsert，记录数稳定）
- [ ] 不支持的方言 / DISABLED / 非 jdbc：显式失败，不静默跳过
- [ ] **端到端验证**：从 `syncExternalTables(dataSourceId)` 入口到 `NopMetaTable__findPage` 可查到外部表 + 字段的完整路径已验证
- [ ] **接线验证**：syncExternalTables 运行时确实通过 P2-1 `withConnection` callback 建连，并在 callback 内执行了 information_schema 查询（写入记录 + 返回 syncedTableCount>0 证明），非空壳
- [ ] **无静默跳过**：单表失败收集到 errors；不支持方言/状态显式失败；无空方法体
- [ ] **新功能测试**：新增测试覆盖 扫描写入 + 重复同步幂等 + 不支持方言显式失败，全绿
- [ ] `ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.5 外部表建模方案（按 D1 裁定）+ syncExternalTables 契约（按 D2）已更新
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：所有条目 + Phase Exit Criteria 全部 `[x]` 后才能 `completed`。

- [ ] 外部表同步端到端可用（GraphQL 入口 → 外部库扫描 → 目录写入）
- [ ] 建模决策 D1 已落地且可观测不变量成立（dict 值 + mandatory 属性 + 回归测试，见 Phase Exit Criteria）
- [ ] 幂等性成立（按 `(metaModuleId, tableName)` 复合键，重复同步不追加）
- [ ] 不存在空壳实现（无空方法体 / 静默跳过 / 吞异常）
- [ ] 必要 focused verification 已完成（同步 + 幂等 + 失败路径测试全绿）
- [ ] `./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（含测试）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [ ] 受影响的 owner docs 已同步（`01-architecture-baseline.md` §2.5 外部表建模 + action 契约）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证 syncExternalTables 运行时确实建连 + 扫描 + 写入（端到端连通）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-16-0225-2-nop-metadata-external-table-metadata-sync.md --strict` 退出码 0

## Deferred But Adjudicated

### 多方言全覆盖（ClickHouse system.columns 等）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 首版支持至少一种方言即满足"外部表可目录化"的核心结果面。其余方言在读取器入口显式抛 UnsupportedOperationException（快速失败，非静默），可作为后续增量
- Successor Required: no

## Non-Blocking Follow-ups

- 定时全库同步调度（当前仅手动 action）
- 外部表 schema 变更检测（diff 旧字段集与新字段集）
- 系统模块 `nop/meta-external` 的初始化自动化（若方案 A 选定）

## Closure

Status Note: <<完成或关闭时填写>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
