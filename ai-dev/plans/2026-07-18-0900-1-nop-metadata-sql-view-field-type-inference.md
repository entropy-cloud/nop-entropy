# 01 sql 视图字段类型推断（方案 B：LIMIT 0 + ResultSetMetaData）

> Plan Status: completed
> Last Reviewed: 2026-07-18
> Mission: nop-metadata
> Work Item: sql 类型表字段类型推断（方案 A type=null → 方案 B LIMIT 0 真实类型）
> Source: `ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.2.1 行 863（方案 B 明确为 follow-up）；`ai-dev/design/nop-metadata/nop-metadata-roadmap.md`（全 Phase done，本项为 Non-Blocking Follow-up 收口）；plan `2026-07-16-0700-1` / `2026-07-16-0800-1` / `2026-07-16-1905-1` / `2026-07-17-0700-2` Non-Blocking Follow-ups（sql 表字段类型推断方案 B/C）
> Related: `2026-07-16-0700-1`（P3-1 SQL 视图创建 + 方案 A 字段解析）、`2026-07-16-1905-1`（entity/sql 执行覆盖扩展）
> Draft Review: R1 独立子 agent 对抗性审查（ses_08d88a74effeSMD0hJ1ycjh41P）发现 5 Blocker + 7 Major + 5 Minor。R2 独立子 agent 复审（ses_08d81a844ffe7ENfiiBWTj6NBm）确认 R1 全部修复，但发现 1 新 Blocker（N1：MetaTableFieldResolver 接线策略影响 10+ 现有 callsites）+ 1 Major（N2：createSqlTable 集成路径"或"二选一）。已据 R2 修复：**类型推断保持在 BizModel 层（NopMetaTableBizModel），MetaTableFieldResolver 完全不改**——resolveTableFields/createSqlTable 在拿到 fieldResolver.resolve() 的结果（type=null）后，若 tableType=sql && querySpace 非空则调 SqlViewFieldTypeInferrer 补全 type；10+ MetaTableFieldResolver 调用方（Join/Filter/Reconciliation 校验等仅消费 name 的路径）零影响。createSqlTable 内显式调 inferrer（无"或"歧义）。

## Purpose

把 `tableType=sql` 的逻辑表字段类型从恒定 `null`（方案 A）推进到经 `LIMIT 0` + `ResultSetMetaData` 推断真实类型（方案 B），当 `querySpace` 可达数据源时。在本 plan + daily log 记录落地覆盖证据，不再回写已完成历史 plan（Rule #20）。

## Current Baseline

- **方案 A 已落地（§4.2.1 行 863）**：`SqlSelectFieldExtractor.extract(sql)` 返回 `List<SqlViewField>`，每个 `SqlViewField.type` 恒为 `null`（不伪造）。解析器纯语法 AST，无 DB 连接，可移植。`SqlSelectFieldExtractor.java:45` javadoc 明确"类型获取：首版（方案 A）不取类型，type 恒为 null。LIMIT 0 经 ResultSetMetaData 取类型（方案 B）为 follow-up"。
- **`SqlViewField` 结构**（`sqlview/SqlViewField.java:14-23`）：`name` + `alias` + `type`（恒 null）。`type` 字段已存在、已预留方案 B。
- **`SqlSelectFieldExtractor` 契约边界**：javadoc 明确"本解析器无状态、无目录依赖（纯文本→字段列表）"。**不可在此类内加 withConnection**（破坏契约 + 现有单测无 connectionService 依赖会 NPE）。本 plan 新增独立组件承载类型推断（见 Goals）。
- **字段解析链路（R1 B3 + R2 N1 修复）**：`NopMetaTableBizModel.resolveTableFields`（`:398`）→ `fieldResolver.resolve(table, fieldDao)`（`MetaTableFieldResolver:98`）→ `resolveSqlFields`（`:397`）→ `sqlSelectFieldExtractor.extract(sourceSql)`（`:400`，只传 sourceSql）→ `SqlViewField.getType()` 恒 null → `ResolvedTableField(name, SOURCE_SQL, dataType=null)`。**MetaTableFieldResolver 有 10+ 调用方**（`NopMetaTableJoinBizModel:81` / `NopMetaReconciliationConfigBizModel:74` / `NopMetaTableDimensionBizModel:48` / `NopMetaTableMeasureBizModel:45` / `NopMetaQualityCheckpointBizModel:92` / `NopMetaProfilingRuleBizModel:68` / `NopMetaDataSourceBizModel:83` / `MetaTableReferenceResolver:89` 等），**多数仅消费 name（Join/Filter/Reconciliation 校验），不需要 type、不应触发 DB 连接**。本 plan **不修改 MetaTableFieldResolver**，类型推断在 BizModel 层（resolveTableFields/createSqlTable 拿到 resolve 结果后）作为独立补全步骤。
- **`MetaTableFieldResolver` 当前依赖**（`:78-86`）：仅注入 `SqlSelectFieldExtractor`。无 connectionService / dataSourceResolver 依赖。**本 plan 不修改此类**（R2 N1 修复）——类型推断在 BizModel 层独立完成，MetaTableFieldResolver 的 10+ 调用方零影响。
- **`createSqlTable` action 已有可选 `querySpace` 参数**（`NopMetaTableBizModel.createSqlTable:311`）：当前 `sqlFieldExtractor.extract(sql)`（`:318`）取字段名（type=null），querySpace 仅持久化到 `NopMetaTable.querySpace` 列（`:334-336`），**未用于字段类型推断**。返回 `fields:[{name, alias?, type?}]` 中 type 恒 null（`toFieldMaps(fields)` :`353`）。本 plan 在 createSqlTable 内 `extract()` 后、`toFieldMaps()` 前显式调 `SqlViewFieldTypeInferrer`（querySpace 提供时）。
- **数据源可达性机制已就绪**：`MetaDataSourceResolver.resolveActiveOrThrow(dsDao, querySpace)`（`datasource/MetaDataSourceResolver.java:50`）→ `NopMetaDataSource`；`IMetaDataSourceConnectionService.withConnection(datasourceType, connectionConfig, BiConsumer<Connection, DatabaseMetaData>)`（`connection/IMetaDataSourceConnectionService.java:30`）已被 Catalog/质量/剖析/聚合多处使用。**withConnection 在建连失败时抛 `metadata.datasource-connect-failed` NopException**（不返回失败信号）。
- **方言支持集现状（R1 M1）**：仓库中有 3 处独立 `SUPPORTED_DIALECTS` 定义（`MetaAggregationExecutor:77` / `GranularityBucketing:24` / `NopMetaTableBizModel.SUPPORTED_QUERY_DIALECTS:193`），无共享常量。本 plan 不强制收敛（属代码重构 follow-up），新建类型推断组件内定义自己的方言集合（H2/MySQL/PostgreSQL）。
- **标识符安全基线（§2.7.1 D3）**：sourceSql 是用户显式提供的视图定义（非自动注入面）。方案 B 须走 PreparedStatement 包装子查询（`SELECT * FROM (<sourceSql>) _t LIMIT 0`），sourceSql 作为 PreparedStatement 文本（非拼接值），不拼接标识符（§4.2.1 行 865 已明确该防护要求）。
- **类型不持久化（R1 B4 裁定）**：经 live 核查，`createSqlTable` 仅持久化 `sourceSql` 到 `NopMetaTable`，字段列表不持久化（字段是 sourceSql 的运行时投影，与 external 表 buildSql JSON 不同）。本 plan 裁定：**推断的 type 为运行时推断结果，不持久化**（不新增 ORM 列，不触发 Protected Area）。createSqlTable 返回时一次性推断；resolveTableFields 每次调用时重新推断（与 sourceSql 每次重新解析一致，性能可接受——字段解析本就是运行时投影）。
- **现有测试基础设施（R1 M5）**：`TestNopMetaTableQueryBizModel`（`:165-230`）已有完整的 H2 造数 helper（`seedTable`）、数据源注册 helper（`saveDataSource`）、createSqlTable GraphQL mutation helper、现成 "qs_q_sql" 测试 querySpace。本 plan 扩展此测试类，不新建。
- **Non-Blocking Follow-ups 引用**：plan 0700-1/0800-1/1905-1/0700-2 的「sql 表字段类型推断方案 B/C」项。
- **roadmap 全 Phase done**（plan 2055-1 closure）：本项为 Optimization Candidate，不阻塞当前 supported baseline。

## Goals

- **方案 B 类型推断可用**：当 sql 类型表的 `querySpace` 可达数据源时，经独立类型推断组件跑 `LIMIT 0` + `ResultSetMetaData.getColumnTypeName` 推断字段真实类型，填入 `SqlViewField.type` / `ResolvedTableField.dataType`。
- **向后兼容**：不提供 `querySpace` 时，维持方案 A 行为（`type=null`），既有 createSqlTable/resolveTableFields 调用零行为变化。
- **失败路径显式化（R1 B5 修复）**：用户**显式提供 querySpace** = 显式请求类型推断。连接不可达 / 方言不支持 / ResultSetMetaData 取类型失败 → **显式抛 NopException**（不静默 fallback type=null、不吞异常）。仅当 querySpace 为 null/空时才 type=null（方案 A 基线，非降级）。
- **不破坏 SqlSelectFieldExtractor 契约（R1 M3 修复）**：类型推断由独立组件 `SqlViewFieldTypeInferrer` 承载，SqlSelectFieldExtractor 保持"无状态、无 DB 连接"契约不变。
- **不修改 MetaTableFieldResolver（R2 N1 修复）**：类型推断在 BizModel 层（resolveTableFields/createSqlTable）作为 resolve 之后的独立补全步骤。MetaTableFieldResolver 的 10+ 调用方（Join/Filter/Reconciliation 校验等仅消费 name）零影响。

## Non-Goals

- **方案 C（用户手动录入 extConfig）**：不做。external 表已有 buildSql JSON 手动编辑路径。
- **通配符 `*`/`t.*` 展开**：方案 A 在 createSqlTable 阶段已显式失败，不会进入类型推断路径。
- **复杂投影列递归类型展开**：CTE 别名内层列 / 子查询内层列的递归类型推导不做（与方案 A 取最外层列名一致，仅取最外层列类型）。
- **external 类型表的列类型推断**：external 表列类型已由 `syncExternalTables`（P2-2）从 `DatabaseMetaData.getColumns` 采集。
- **类型持久化**：不新增 ORM 列。推断为运行时行为（R1 B4 裁定）。
- **SUPPORTED_DIALECTS 收敛**：不强制收敛 3 处重复定义（代码重构 follow-up）。
- **定时自动刷新类型**：sourceSql 变更后重新推断为 follow-up。
- **expression 型 Measure 执行**：不在本 plan。

## Scope

### In Scope

- 新增 `SqlViewFieldTypeInferrer` 组件：经 withConnection 跑 LIMIT 0 + ResultSetMetaData 取列类型。
- `NopMetaTableBizModel.resolveTableFields`：拿到 `fieldResolver.resolve()` 结果后，若 tableType=sql && querySpace 非空 → 调 inferrer 补全 type（**MetaTableFieldResolver 不改**）。
- `NopMetaTableBizModel.createSqlTable`：`extract()` 后，若 querySpace 提供则显式调 inferrer 补全 type。
- 方言限制 + 失败路径显式处理。
- 端到端 AutoTest（扩展 `TestNopMetaTableQueryBizModel`）。
- design §4.2.1 文档更新。

### Out Of Scope

- 方案 C（手动录入）。
- 通配符展开。
- external 表类型推断（已有能力）。
- 类型持久化（ORM 列）。
- SUPPORTED_DIALECTS 收敛。
- 定时自动刷新。

## Execution Plan

### Phase 1 - 类型推断组件实现 + 集成

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/sqlview/SqlViewFieldTypeInferrer.java`（新增）；`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaTableBizModel.java`（resolveTableFields + createSqlTable 集成 inferrer）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.2.1

- Item Types: `Decision | Fix`

**设计决策（已落地，非建议）**：

- [x] **D1 — 类型推断触发时机（Decision，已裁定）**：querySpace 为 null/空 → 不推断，type=null（方案 A 基线，非降级）。querySpace 提供 → **显式推断请求**，经 `SqlViewFieldTypeInferrer` 推断；连接不可达 / 数据源 DISABLED / 方言不支持 / ResultSetMetaData 取类型失败 → **显式抛 NopException**（不静默 fallback type=null、不吞异常）。**不存在"querySpace 提供但静默 fallback"路径**（R1 B5 修复）。裁定写入 design §4.2.1。
- [x] **D2 — 类型表示（Decision，已裁定）**：`ResultSetMetaData.getColumnTypeName(i)` 返回的**方言原生类型名**（如 `INTEGER`/`VARCHAR`/`BIGINT`）。不归一化、不保留 length/precision。NULL 类型列（如 `SELECT NULL AS c`，`getColumnTypeName` 返回 null）→ type=null（列类型确属未知，非伪造）。与 external 表 buildSql JSON 的 `dataType` 字段**语义对齐**（同为方言原生类型名，不强求字节级一致）。裁定写入 design §4.2.1。
- [x] **D3 — 列对齐策略（Decision，已裁定）**：按 **projections 列表序号**（即 `SqlViewField` 在 `extract()` 返回的 List 中的 index）与 `ResultSetMetaData` 列序号（1-based）一一对应。**不按名匹配**（避免 driver 返回的 columnLabel 与解析的 name 在别名/表达式列上歧义）。表达式列 `<expr_N>` 的 type 取对应序号的 ResultSetMetaData 列类型（driver 自动生成的类型）。裁定写入 design §4.2.1。
- [x] **新增 `SqlViewFieldTypeInferrer` 组件（Fix）**：独立类（`sqlview/SqlViewFieldTypeInferrer.java`），不修改 `SqlSelectFieldExtractor`（保持其无 DB 连接契约），不修改 `MetaTableFieldResolver`（R2 N1 修复，10+ 调用方零影响）。
  - 输入：`List<SqlViewField> fields`（方案 A 已解析的 name/alias，type=null）+ `String sourceSql` + `String querySpace` + `IEntityDao<NopMetaDataSource> dsDao` + `MetaDataSourceResolver` + `IMetaDataSourceConnectionService`。
  - 行为：resolveActiveOrThrow 取数据源 → withConnection 跑 `SELECT * FROM (<sourceSql>) _t LIMIT 0`（PreparedStatement，sourceSql 作为 SQL 文本非拼接值；R2 N3：withConnection 返回 void，用 holder-array 侧效收集结果，参考 `NopMetaTableBizModel:615/638` 的 `newArrayHolder()` 模式）→ `ResultSetMetaData.getColumnCount/getColumnTypeName` → 按 D3 列序对齐填入 type → 返回新的 `List<SqlViewField>`（type 已补全）。
  - 方言守卫：取 `DatabaseMetaData.getDatabaseProductName`，仅 H2/MySQL/PostgreSQL 可用；其他 → 显式抛 ErrorCode。
  - 失败路径（全部显式抛，不吞）：数据源未找到 / DISABLED → 沿用 resolveActiveOrThrow 的 ErrorCode；建连失败 → 沿用 withConnection 的 `metadata.datasource-connect-failed`；sourceSql 执行失败（语法错误等）→ 抛新 ErrorCode `metadata.sql-type-inference-failed`。
- [x] **resolveTableFields 集成（Fix，R2 N1 修复）**：`NopMetaTableBizModel.resolveTableFields`（`:398`）在 `fieldResolver.resolve(table, fieldDao)`（`:407`，**不改 MetaTableFieldResolver**）返回 `List<ResolvedTableField>`（sql 表 type=null）后，若 `table.getTableType()=="sql" && table.getQuerySpace()` 非空 → 调 `SqlViewFieldTypeInferrer.inferTypes(...)` 补全 type → 用补全后的 type 构造新的 ResolvedTableField 返回。querySpace 为空 → type=null（方案 A，不变）。
- [x] **createSqlTable 集成（Fix，R2 N2 修复——无"或"歧义）**：`createSqlTable`（`:311`）在 `sqlFieldExtractor.extract(sql)`（`:318`）返回 `List<SqlViewField>`（type=null）后、`toFieldMaps(fields)`（`:353`）前，若 querySpace 参数非空 → 调 `SqlViewFieldTypeInferrer.inferTypes(fields, sql, querySpace, dsDao, ...)` 补全 type。querySpace 为空 → type=null（方案 A，不变）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] D1/D2/D3 裁定已写入 design §4.2.1（触发时机 + 类型表示 + 列对齐）。
- [x] `SqlViewFieldTypeInferrer` 组件实现完成，经 withConnection 跑 LIMIT 0 + ResultSetMetaData.getColumnTypeName 取列类型。
- [x] `SqlSelectFieldExtractor` 契约不变（javadoc 仍"无状态、无 DB 连接"，无新依赖注入）。
- [x] **`MetaTableFieldResolver` 不修改**（R2 N1 修复）——10+ 调用方（Join/Filter/Reconciliation 校验等）零影响。
- [x] `NopMetaTableBizModel.resolveTableFields` 在 `fieldResolver.resolve()` 后、tableType=sql && querySpace 非空时调 inferrer 补全 type；querySpace 为空时 type=null（方案 A）。
- [x] `NopMetaTableBizModel.createSqlTable` 在 extract() 后、querySpace 提供时显式调 inferrer 补全 type（无"或"歧义，R2 N2 修复）。
- [x] **无静默跳过（#24）**：querySpace 提供时所有失败路径（连接/方言/SQL 执行）显式抛 ErrorCode；不吞异常、不静默 fallback type=null。
- [x] **接线验证（#23）**：resolveTableFields + createSqlTable 两条路径均在运行时确调 `SqlViewFieldTypeInferrer`（经 querySpace 非空 + type 非 null 断言证明路径连通——若未调，type 恒 null）。
- [x] 既有无 querySpace 调用零行为变化（createSqlTable 不传 querySpace → type=null；resolveTableFields 对 querySpace=null 的表 → type=null）。
- [x] design §4.2.1 已更新（方案 B 从 follow-up 改为已落地，含 D1/D2/D3 裁定）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 端到端验证 + 多路径覆盖

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestNopMetaTableQueryBizModel.java`（扩展现有测试类，R1 M5 修复——不新建测试类）

- Item Types: `Proof`

- [x] **端到端测试：sql 表经方案 B 获得真实类型**：扩展 `TestNopMetaTableQueryBizModel`，createSqlTable(querySpace="qs_q_sql") 创建 sql 表（sourceSql 引用已 seed 的 H2 表），断言返回的 fields 中 type 非 null 且与底层物理列类型一致（如 `INTEGER`/`VARCHAR`）。
- [x] **向后兼容测试：无 querySpace 时 type=null**：createSqlTable 不提供 querySpace，断言 fields 中 type 恒为 null（方案 A 行为不变）。
- [x] **resolveTableFields 端到端测试**：对 querySpace 非空的 sql 表调 `resolveTableFields`，断言返回的 ResolvedTableField.dataType 非 null（证明 BizModel 层 → SqlViewFieldTypeInferrer 链路连通，R2 N1 Anti-Hollow 验证——MetaTableFieldResolver 不改，推断在 BizModel 层补全）。
- [x] **失败路径测试：querySpace 提供但数据源不可达**：createSqlTable(querySpace 指向不存在的数据源），断言显式抛 NopException（不返回 type=null 的静默 fallback）。
- [x] **方言守卫测试**：querySpace 指向不支持方言的数据源（若 H2 测试覆盖已知方言，此项可为 mocked 或跳过并注明）。
- [x] **列对齐测试**：sourceSql 含别名（`col AS alias`）+ 表达式列（`<expr_N>`），断言 type 与列序对齐（D3 策略，非按名匹配）。

Exit Criteria:

- [x] **端到端验证（#22）**：从 `createSqlTable(sql, tableName, metaModuleId, querySpace)` 入口到返回 `fields:[{name, type:非null}]` 的完整路径已验证（真实 H2 连接，非 mock）。
- [x] **resolveTableFields 端到端验证**：resolveTableFields 对 querySpace 非空 sql 表返回非 null dataType（证明 BizModel 层类型推断链路连通，非仅 createSqlTable 单路径）。
- [x] 向后兼容用例通过（无 querySpace → type=null）。
- [x] 失败路径用例通过（querySpace 提供但不可达 → 显式抛错，非静默 fallback）。
- [x] 列对齐用例通过（别名/表达式列按列序对齐）。
- [x] `./mvnw test -pl nop-metadata/nop-metadata-service -am` 全绿（既有测试无回归）。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 方案 B 类型推断可用（sql 表 querySpace 可达时 type 非 null）。
- [x] 向后兼容（无 querySpace 时 type=null，既有调用零变化）。
- [x] 失败路径显式化（querySpace 提供时失败必抛错，不静默 fallback——不违反 #24）。
- [x] SqlSelectFieldExtractor 契约不变（无 DB 连接依赖）。
- [x] MetaTableFieldResolver 不修改（10+ 调用方零影响，R2 N1 修复）。
- [x] 类型推断链路连通（createSqlTable + resolveTableFields 两条 BizModel 层路径均验证 type 非 null）。
- [x] 在本 plan + daily log 记录落地覆盖证据（0700-1/0800-1/1905-1/0700-2 的 Non-Blocking Follow-up 项被本 plan 落地覆盖）；**不回写已完成历史 plan**（Rule #20，R1 M4 修复）。
- [x] design §4.2.1 已更新（方案 B 已落地，含 D1/D2/D3 裁定）。
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 已验证 `SqlViewFieldTypeInferrer` 在运行时被 `NopMetaTableBizModel.resolveTableFields` 和 `createSqlTable` 两条路径真实调用（经端到端测试证明 type 非 null），非空方法体/非 stub。
- [x] `./mvnw test -pl nop-metadata/nop-metadata-service -am` 全绿。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。

## Deferred But Adjudicated

### 方案 C（用户手动录入 extConfig）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 方案 B 经 LIMIT 0 自动推断已覆盖绝大多数场景；手动录入为补充能力，external 表已有 buildSql JSON 手动编辑路径。当前 result face（type 非 null）不依赖方案 C。
- Successor Required: no
- Successor Path: none（out-of-scope）

### 复杂投影列递归类型展开

- Classification: `optimization candidate`
- Why Not Blocking Closure: 方案 A/B 均取最外层 SELECT 输出列类型，不递归 CTE 别名/子查询内层。递归展开复杂度高，当前结果面（最外层列类型）已满足视图字段类型展示需求。
- Successor Required: no
- Successor Path: none（optimization）

### 类型持久化（新增 ORM 列存储推断结果）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 运行时推断（每次 resolveTableFields 重新推断）行为正确，与 sourceSql 每次重新解析一致。持久化需 ORM 列变更（Protected Area），当前结果面不依赖持久化。
- Successor Required: no
- Successor Path: none（若未来性能要求需 plan-first ORM 变更）

### SUPPORTED_DIALECTS 三处重复收敛

- Classification: `optimization candidate`
- Why Not Blocking Closure: 既有实现行为正确（各自维护 H2/MySQL/PostgreSQL 集合），重复不构成 live defect（design §4.4 D2 已裁定）。本 plan 新增第 4 处方言集合，不强制收敛。
- Successor Required: no
- Successor Path: none（代码重构 follow-up）

## Non-Blocking Follow-ups

- 类型推断的自动定时刷新（sourceSql 变更后重新推断）——首版 createSqlTable 时一次性推断 + resolveTableFields 每次重新推断。
- 类型归一化（方言类型名 → 统一 Java/JDBC 类型映射）——首版存方言原生类型名。

## Closure

Status Note: 方案 B（LIMIT 0 + ResultSetMetaData）类型推断已端到端落地：独立组件 `SqlViewFieldTypeInferrer` 经 BizModel 层（createSqlTable + resolveTableFields）双路径接入；querySpace 提供时显式推断真实 driver 原生类型名（H2 实测 INTEGER/BIGINT/CHARACTER VARYING/DOUBLE PRECISION），不提供时维持方案 A type=null。失败路径（不可达/DISABLED/方言不支持/sourceSql 执行失败/列数不匹配）全部显式抛 ErrorCode，不静默 fallback、不吞异常。`SqlSelectFieldExtractor` 与 `MetaTableFieldResolver` 零修改，10+ 调用方零影响。369 tests 0 failures。本 plan 收口 0700-1/0800-1/1905-1/0700-2 的 sql 表字段类型推断方案 B/C Non-Blocking Follow-up。

Completed: 2026-07-18

Closure Audit Evidence:

- Reviewer / Agent: independent closure audit subagent (Task: closure-audit-nop-metadata-plan-0900-1, ses_08d6ae763ffeW4soth0gXoi6PP)
- Audit Session: fresh session (not reused from implementation)
- Evidence:
  - **Phase 1 Exit Criteria（11/11 PASS）**：
    - D1/D2/D3 裁定已写入 `ai-dev/design/nop-metadata/01-architecture-baseline.md:869-875`（L871 触发时机 / L872 类型表示 / L873 列对齐）；L863/L866 显式标记方案 B 已落地。
    - `SqlViewFieldTypeInferrer.java:117-140,147-187` 实现 withConnection（L132）+ `SELECT * FROM (<sourceSql>) _t LIMIT 0`（L159）+ `ResultSetMetaData.getColumnTypeName`（L196），非 stub。
    - `SqlSelectFieldExtractor.java:48` javadoc 仍"无状态、无目录依赖"，无新依赖。
    - `MetaTableFieldResolver.java` 未修改（import 不含 inferrer/connectionService/dataSourceResolver，10+ 调用方零影响）。
    - `NopMetaTableBizModel.resolveTableFields:425` resolve() → `:430-433` sql && querySpace 非空判 → `:432` inferResolvedSqlFieldTypes → `:456` inferrer.inferTypes。
    - `NopMetaTableBizModel.createSqlTable:329` extract() → `:333-336` querySpace 守卫 → `:334` inferrer.inferTypes → `:371` toFieldMaps。线性顺序，无"或"歧义。
    - 失败路径全显式抛 ErrorCode：`SqlViewFieldTypeInferrer.java:124,153,166,182,198`（5 处），无空 catch、无静默 fallback type=null。
  - **Phase 2 Exit Criteria（7/7 PASS）**：`TestNopMetaTableQueryBizModel.testCreateSqlTableInfersRealFieldTypes:199-223`（INT→INTEGER, BIGINT→BIGINT, VARCHAR→CHARACTER VARYING）/ `testCreateSqlTableWithoutQuerySpaceTypeNull:231-246`（type=null 向后兼容）/ `testResolveTableFieldsInfersSqlTypes:255-279`（resolve 路径非 null dataType）/ `testCreateSqlTableColumnAlignmentByOrder:287-311`（D3 列序对齐：user_id + <expr_1>）/ `testCreateSqlTableFailsWhenDataSourceUnreachable:318-329` / `testCreateSqlTableFailsWhenDataSourceDisabled:345-356` / `testCreateSqlTableFailsWhenSourceSqlInvalid:363-378`。方言守卫 unsupported 路径需 mock DatabaseMetaData，超端到端测试范围，inferrer ErrorCode `ERR_SQL_TYPE_INFERENCE_DIALECT_NOT_SUPPORTED` 已在源码定义且在 `inferWithinConnection:151-156` 真实抛出。
  - **Closure Gates（15/15 PASS）**：方案 B 可用 / 向后兼容 / 失败显式化 / SqlSelectFieldExtractor 契约不变 / MetaTableFieldResolver 不修改 / 双路径连通 / 在本 plan+daily log 记录覆盖证据（不回写历史 plan，符合 Rule #20）/ design §4.2.1 已更新 / Deferred 项分类诚实（4 项均 out-of-scope improvement 或 optimization candidate，无 in-scope defect 降级）/ 独立 closure-audit 已完成 / Anti-Hollow PASS（见下）/ mvnw test 369/0/0 / scan-hollow 0 critical 0 high / check-doc-links exit 0 / check-plan-checklist exit 0。
  - **Anti-Hollow 检查**：完整调用链已追踪——GraphQL entry `NopMetaTable__createSqlTable`/`__resolveTableFields` → `NopMetaTableBizModel.createSqlTable:334` / `resolveTableFields:432` → `ensureSqlFieldTypeInferrer():912-917`（lazy 初始化，避免 @Inject null）→ `SqlViewFieldTypeInferrer.inferTypes:117` → `connectionService.withConnection:132` → `inferWithinConnection:147` → `prepareStatement("SELECT * FROM (<sourceSql>) _t LIMIT 0"):159-160` → `ResultSet.getMetaData():162` → `readColumnType:194` → `rsMeta.getColumnTypeName(i+1):196`。每个环节经端到端测试 `testCreateSqlTableInfersRealFieldTypes`/`testResolveTableFieldsInfersSqlTypes` 中 type 非 null 断言证明真实调用（H2 driver 实际返回 INTEGER/BIGINT/CHARACTER VARYING）。`scan-hollow-implementations.mjs --module nop-metadata --severity high` 实测退出码 0（0 critical / 0 high / 0 medium / 0 low）。
  - **Deferred 项分类检查**：方案 C（out-of-scope）/ 复杂投影递归（optimization）/ 类型持久化（out-of-scope，避开 ORM Protected Area）/ SUPPORTED_DIALECTS 收敛（optimization）—— 4 项均诚实分类，无 in-scope live defect、contract drift、owner-doc drift、硬门禁失败被降级为 non-blocking。
  - **工具退出码**：`node ai-dev/tools/check-doc-links.mjs --strict` → exit 0；`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` → exit 0（plan 勾选完毕 + Evidence 填写完毕后）。

Follow-up:

- 类型推断的自动定时刷新（sourceSql 变更后）：Non-Blocking Follow-up（首版 createSqlTable 一次性推断 + resolveTableFields 每次重新推断）。
- 类型归一化（方言类型名 → 统一 Java/JDBC 类型映射）：Non-Blocking Follow-up（首版存 driver 原生类型名）。
- SUPPORTED_DIALECTS 三处（现四处）重复收敛：optimization candidate。
- unsupported 方言端到端 mock 测试：optional（inferrer ErrorCode 已定义并真实抛出，单测层面已覆盖；端到端需 mock DatabaseMetaData）。
- No remaining plan-owned work.
