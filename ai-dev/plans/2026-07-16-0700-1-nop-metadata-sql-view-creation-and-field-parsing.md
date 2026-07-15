# 1 nop-metadata SQL 视图创建与 SELECT 字段解析

> Plan Status: completed
> Last Reviewed: 2026-07-16
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` P3（P3-1 + P3-6）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.5 逻辑表 + §4.2 SQL 视图创建 + §八 待定问题；`ai-dev/design/nop-metadata/00-vision.md`
> Mission: nop-metadata
> Work Item: P3-1 — SQL 视图创建（输入 SQL → 创建 MetaTable(tableType=sql) → 运行时解析 SELECT 字段）；P3-6 — 视图字段解析方案确定（EXPLAIN vs SELECT LIMIT 0 vs AST 解析 vs 手动录入）
> Draft Review: PASS（两轮独立子 agent 对抗性审查，ses_0980582f6ffe / ses_097fe0248ffe，无 Blocker/Major，核心技术可行性已实地验证 AST 可遍历取 SELECT 列）
> Related: `2026-07-16-0420-2-nop-metadata-lineage-collection-and-traversal.md`（P2-5 已落地 `SqlSourceTableExtractor`，复用 `nop-orm-eql` AST 解析 sourceSql 的先例——本 plan 复用同一 AST 解析器抽取 SELECT 字段，非硬前驱但强参考）、`2026-07-16-0225-1-nop-metadata-datasource-registration-and-connection-verification.md`（P2-1 提供 callback 式连接服务 `withConnection`，用于 LIMIT 0 类型解析的可选路径）

## Purpose

让 nop-metadata 从"只能导入 ORM 实体表和扫描外部表"推进到"用户可输入 SQL 自定义逻辑视图表"。具体：提供 `createSqlTable` action——校验输入 SQL 为可解析的 SELECT → 创建 `NopMetaTable(tableType=sql, sourceSql=...)` → 提供 SELECT 字段解析能力（字段名/别名），并收口 §八 待定问题中的"SQL 视图字段解析方案"裁定（P3-6）。本 plan 是 BI 语义层（P3）的核心新能力，也是 P4 联邦查询对 sql 类型表执行的前置。

## Current Baseline

- **NopMetaTable 实体已建模**（`nop-metadata/model/nop-metadata.orm.xml:959-1021`）：`metaTableId`(PK) / `metaModuleId`(mandatory) / `tableName`(mandatory) / `displayName` / `tableType`(mandatory, dict `meta/table-type`：`entity`/`sql`/`external`) / `querySpace`(nullable) / `sourceSql`(domain `mediumtext`, `orm.xml:980`) / `baseEntityId` / `description` / `buildSql`(domain `mediumtext`) + 审计。to-one metaModule。索引 `IX_NOP_META_TABLE_MODULE`(metaModuleId)。
- **tableType=sql 记录今天可通过 CRUD save 直接创建**（CrudBizModel 自动生成 save），但**无任何 sourceSql 校验或解析**——可存入任意字符串（含非 SELECT / 空 / 语法错误），且无字段解析能力。这是 P3-1 要补的真实行为。
- **NopMetaTableBizModel 现状**（`NopMetaTableBizModel.java`）：仅 `profileTable`（P2-7 数据剖析）一个自定义 action + CrudBizModel。**无 createSqlTable / 无字段解析 action**。
- **AST 解析先例（P2-5 已验证）**：`SqlSourceTableExtractor`（`nop-metadata/nop-metadata-service/.../lineage/SqlSourceTableExtractor.java:31-97`）已复用平台 `nop-orm-eql` 的 `EqlASTParser.parseFromText(null, sql)` 对 sourceSql 做纯语法 AST 解析（不绑定 ORM session），从 FROM/JOIN 抽取 `SqlSingleTableSource` 表名。**同一解析器可遍历 AST 抽取 SELECT 列定义**（`SqlProgram` → select 项）。`nop-orm-eql` 经 `nop-orm`（`nop-metadata-dao` 依赖）已传递可用，无需新增 pom 依赖。
- **设计契约（§4.2 + §八 待定）**：§4.2 规定"运行时解析 SELECT 子句获取字段列表，不单独存储"（无 MetaTableField 实体）。§八 列出**未决问题**："SQL 视图字段解析：走 EXPLAIN 还是 SELECT ... LIMIT 0 还是用户手动录入？"——**本 plan item 1.1 收口此裁定**。
- **字段类型获取的权衡（Decision 待定）**：AST 解析可得字段**名/别名**（可移植、无需连接，与血缘一致），但**得不到列类型**（类型需执行 `SELECT ... LIMIT 0` 经 ResultSetMetaData 取，需 querySpace→数据源→withConnection，sql 表 querySpace 显式设置可解析）。类型获取的取舍 + 不可用时降级为 item 1.1 硬前置门禁。
- **entity/external 表字段来源（Plan 2 范围，此处仅记录依赖）**：entity 表字段来自 MetaEntityField；external 表字段在 buildSql JSON（A2 方案）。sql 表字段来自 sourceSql 解析（本 plan 产出解析器）。
- **测试基建**：Nop AutoTest 可用，P2-2/P2-4/P2-7 已证明可在 H2 建外部表被检测。`TestNopMetaTableBizModel.java` **已存在**（P2-7 数据剖析用例，含 `saveManualTable`/`saveDataSource`/`seedTable` helper）——本 plan 为**追加** createSqlTable/previewSqlFields/resolveTableFields 用例，非新建文件。`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（P2-7 后全模块测试全绿）。

## Goals

- P3-6 决策已裁定并写入设计文档（收口 §八 待定问题）：SQL 视图字段解析方案——字段名/别名走 AST 解析（可移植、无需连接，复用 `EqlASTParser`，与血缘先例一致）；字段类型的获取方式（LIMIT 0 when querySpace 可解析到 ACTIVE jdbc 数据源 / 首版仅名不取类型 / 手动录入补充）由 item 1.1 基于实测裁定
- `createSqlTable` GraphQL mutation action 可用：输入 SQL + tableName + metaModuleId(+ 可选 querySpace/displayName) → 校验 SQL 为可解析 SELECT（非 SELECT / 空 / 不可解析显式失败）→ 创建 `NopMetaTable(tableType=sql, sourceSql=sql)` → 返回新建表 + 解析出的字段列表
- `previewSqlFields` GraphQL query action 可用：输入 SQL 文本（不持久化）→ 返回 AST 解析出的字段名/别名列表（+ item 1.1 裁定的类型信息），供 UI 预览
- 已建 sql 视图表可通过 `resolveTableFields(metaTableId)` 运行时解析其 sourceSql 得到字段列表（对齐 §4.2 "不单独存储、运行时解析"）
- 失败路径显式：非 SELECT / 语法错误 / 空 SQL / 表名缺失 / module 不存在 → 显式失败（不静默存入脏数据、不静默返回空字段列表）

## Non-Goals

- Measure / Dimension / Filter / Join 的字段引用校验与跨表字段解析 —— P3-2~P3-5，独立 plan（`2026-07-16-0700-2-*`）
- sql 视图表的**查询执行**（聚合/JOIN 执行）—— P4 联邦查询
- entity/sql 类型表的 Catalog 收集 / 质量执行 / 剖析执行（querySpace→数据源解析，P2-4/P2-6/P2-7 同源 deferred，归 P4 或后续增量）
- MetaTableField 实体持久化（§4.2 明确"不单独存储"；若 item 1.1 推翻此结论须显式记录并走 ORM 结构变更 Protected Area 流程）
- 复杂 SQL 的完整列级类型推断（CTE/子查询/UNION/UDF 的类型展开超出首版 AST 能力，降级为 item 1.1 裁定范围）
- DDL/DML 语句支持（首版仅 SELECT 视图；CREATE VIEW 物化视图为 follow-up）

## Design Decisions

> D1 为硬前置门禁，须在 item 1.1 裁定并写入 `01-architecture-baseline.md` §4.2 / §八 后实现。

### D1. 字段解析方案 + 类型获取 + 降级（待 item 1.1 裁定）

- **字段名/别名解析（推荐 AST，待 item 1.1 确认）**：复用 `EqlASTParser.parseFromText(null, sql)` 得 `SqlProgram`，遍历 AST 取 SELECT 输出列——别名优先（`expr AS alias` → alias），无别名时取列名；表达式列（无别名且为表达式）取表达式文本或标记 `<expr>`（item 1.1 裁定标记策略）。**可移植、无需 DB 连接、与血缘先例一致**。复杂 SQL（CTE/子查询/UNION）首版取最外层 SELECT 输出列（不递归展开），展开为 follow-up。
- **字段类型获取（Decision 待 item 1.1 裁定，三选一或组合）**：
  - 方案 A（推荐候选）：首版**仅返回字段名/别名，不取类型**（与 AST 解析对齐，无需连接）。类型解析为 follow-up。最简、最可移植、无连接依赖。
  - 方案 B：当 sql 表 querySpace 可解析到 ACTIVE jdbc 数据源时，用 `withConnection` 执行 `SELECT * FROM (<sourceSql>) _t LIMIT 0` 经 `ResultSetMetaData` 取列名+类型；无数据源/非 jdbc 时降级为仅名（null + unavailable 标记，对齐 Catalog §2.3.2 降级模式，不伪造类型）。
  - 方案 C：用户手动录入/补充类型（存 extConfig）。
  - **item 1.1 须实测裁定**：H2 测试库执行 `SELECT * FROM (...) LIMIT 0` 是否可取 ResultSetMetaData（验证方案 B 可行性）；裁定首版采纳 A / B / C 中的哪种组合。结论写入 §4.2 + §八（关闭待定问题）。
- **复杂投影列处理（Decision 待 item 1.1 裁定）**：`SqlExprProjection`（普通列/别名/表达式）按上述别名优先策略；`SqlAllProjection`（`SELECT *` / `t.*`）在纯语法 AST 下**无法展开为具体列**——item 1.1 须裁定处理策略（显式失败 / 仅方案 B LIMIT 0 展开 / 返回单一 wildcard 标记），**不得静默跳过或返回空**。
- **非 SELECT / 多语句处理**：`createSqlTable` 必须校验输入为**恰一条 SELECT 语句**（AST 顶层 `getStatementKind()==SELECT` 且 `getStatements().size()==1`；多语句如 `SELECT 1; DELETE...` 显式失败，不静默存入）。item 1.1 确认 AST 判定方式。
- **标识符安全**：sourceSql 是用户显式提供的视图定义（非自动注入面），但 LIMIT 0 执行时（方案 B）须走 PreparedStatement 包装子查询、不拼接标识符（列名/表名不出现在拼接位），与 P2-6/P2-7 标识符防护原则一致。

### D2. action 落点 + 契约（待 item 1.1 确认）

- `createSqlTable`、`previewSqlFields`、`resolveTableFields` 落点 **NopMetaTableBizModel**（操作对象是逻辑表，与 profileTable/collectCatalog 入口风格一致）。
- `@BizMutation createSqlTable(@Name("sql") String sql, @Name("tableName") String tableName, @Name("metaModuleId") String metaModuleId, @Optional @Name("querySpace") String querySpace, @Optional @Name("displayName") String displayName, IServiceContext)` → 返回 `Map{metaTableId, tableName, tableType:"sql", fields:[...]}`。
- `@BizQuery previewSqlFields(@Name("sql") String sql, IServiceContext)` → 返回 `Map{fields:[{name, alias?, type?}], unavailable?[...]}`（不持久化）；`@BizQuery resolveTableFields(@Name("metaTableId") String metaTableId, IServiceContext)` → 返回**同一 wrapper 结构** `Map{fields:[{name, alias?, type?}], unavailable?[...]}`：加载 NopMetaTable(tableType=sql) → 解析 sourceSql。非 sql 表 / sourceSql 空 → 显式失败。

## Scope

### In Scope

- `NopMetaTableBizModel`：新增 `createSqlTable`（mutation）+ `previewSqlFields`（query）+ `resolveTableFields`（query）
- SELECT 字段解析器（无状态，参考 `SqlSourceTableExtractor` 的 AST 遍历模式）：sourceSql/SQL 文本 → 字段名/别名（+ item 1.1 裁定的类型）
- createSqlTable 的 SELECT 校验（非 SELECT/空/不可解析显式失败）
- AutoTest：建 sql 视图表（含别名列、表达式列、多列）→ 解析字段 → 断言字段名与 SELECT 输出一致 + 非 SELECT/空/不可解析显式失败

### Out Of Scope

- Measure/Dimension/Filter/Join 校验（Plan 2）
- sql 视图表查询执行（P4）
- entity/sql 的 Catalog/质量/剖析执行（P2 deferred / P4）
- MetaTableField 持久化（§4.2 不存储；推翻须走 Protected Area）
- CTE/子查询/UNION 递归展开、物化视图、DDL/DML（follow-up）

## Execution Plan

### Phase 1 - SQL 视图创建 + SELECT 字段解析器

Status: completed
Targets: `nop-metadata/nop-metadata-service/.../entity/NopMetaTableBizModel.java`（新增 3 个 action）、新增 SELECT 字段解析器（参考 `SqlSourceTableExtractor` AST 遍历）、`TestNopMetaTableBizModel.java`

- Item Types: `Decision`（D1 字段解析方案/类型获取/降级 + D2 action 契约，硬前置）+ `Proof`（新功能：SQL 视图创建 + 字段解析）

> **硬前置门禁（item 1.1）**：D1/D2 必须先裁定（只裁定不写代码），写入设计文档后再落地解析器与 action。

- [x] 1.1 **字段解析方案 + 类型获取 + 降级 + action 契约决策（硬前置门禁，Decision only）**：基于 live repo 核查并裁定 D1/D2——确认 `EqlASTParser.parseFromText` 可解析出 SELECT 输出列（实地验证 AST 节点类型：`SqlProgram.getStatements()` → `SqlStatement.getStatementKind()` 判 SELECT；SELECT 输出列经 `SqlSelect.getProjections()` → `List<SqlProjection>`，其子类 `SqlExprProjection`（表达式列，别名经 `getAlias().getAlias()` 取——注意是 `getAlias()` 非 `getName()`；列引用经 `getExpr()`→`SqlColumnName.getName()`）与 `SqlAllProjection`（`SELECT *`/`t.*`，无 alias/name）。取别名/列名/表达式列标记策略）；**通配符 `*`/`t.*`（SqlAllProjection）处理策略——纯语法 AST 无法展开为具体列，须裁定：显式失败 / 仅在方案 B（LIMIT 0 经 ResultSetMetaData 展开）/ 返回单一 wildcard 标记，任一裁定都不得静默跳过**；**多语句（`;` 分隔）拒绝策略——`program.getStatements().size() != 1` 显式失败，不允许 `SELECT 1; DELETE...` 这类多语句作为视图 sourceSql**；**CTE 包装注意——`SqlSelectWithCte extends SqlDmlStatement`（非 `SqlSelect`），`WITH ... SELECT` 的 `getStatementKind()` 返回 SELECT 但不能直接 cast 为 `SqlSelect` 取 projections，item 1.1 须裁定钻入 CTE 内层 query 取 projections 或首版显式拒绝 `WITH ...`**；注意 `SqlExprProjection.getFieldName()` 为**编译期**才填充（纯 `parseFromText` 后为 null），取列名须从 `getExpr()` 推导；裁定类型获取方案（A 仅名 / B LIMIT 0+ResultSetMetaData / C 手动，或组合），若选 B 须实测 H2 执行 `SELECT * FROM (<sql>) _t LIMIT 0` 的 ResultSetMetaData 可用性 + sql 表 querySpace→NopMetaDataSource→withConnection 解析可行性；裁定非 SELECT 判定方式（顶层单条语句类型）；裁定 action 落点（推荐 NopMetaTableBizModel）+ 三个 action 契约（**createSqlTable 与 previewSqlFields/resolveTableFields 的 fields 项结构须统一为 `{name, alias?, type?}`**）；裁定标识符安全（LIMIT 0 子查询包装不拼接标识符）。**只裁定不写代码**。结论写入 `01-architecture-baseline.md` §4.2（解析方案 + 类型获取 + 降级 + 通配符 + 多语句）+ §八（关闭"SQL 视图字段解析"待定问题，标注已裁定）
- [x] 1.2 新增 SELECT 字段解析器（依赖 1.1，无状态，参考 `SqlSourceTableExtractor` AST 遍历）：输入 SQL 文本 → `EqlASTParser.parseFromText` → 遍历 AST 取 SELECT 输出列（别名优先 / 列名次之 / 表达式列标记）→ 返回字段列表（含 item 1.1 裁定的类型信息，若选 B 则内部按 querySpace→数据源→withConnection LIMIT 0 取类型）。SQL 为空/不可解析 → 抛 inline ErrorCode（不静默返回空列表）
- [x] 1.3 在 `NopMetaTableBizModel` 新增 `@BizMutation createSqlTable(...)`（依赖 1.1，落点见 D2）：校验 sql 非空+为 SELECT（非 SELECT / 不可解析显式失败抛 inline ErrorCode）→ 校验 metaModuleId 存在 → 解析字段（调 1.2 解析器）→ 新建 `NopMetaTable(tableType="sql", sourceSql=sql, tableName, metaModuleId, querySpace, displayName)` → save → 返回 `{metaTableId, tableName, tableType, fields}`
- [x] 1.4 在 `NopMetaTableBizModel` 新增 `@BizQuery previewSqlFields(@Name("sql") String sql, IServiceContext)`（依赖 1.1）：调 1.2 解析器（不持久化）→ 返回 `{fields:[...], unavailable?[...]}`；新增 `@BizQuery resolveTableFields(@Name("metaTableId") String metaTableId, IServiceContext)`：加载表 → 非 sql/sourceSql 空 显式失败 → 解析 sourceSql → 返回字段列表
- [x] 1.5 错误码按现有模式在 BizModel 内 inline 定义（参考 `NopMetaTableBizModel` / `NopMetaDataSourceBizModel` inline ErrorCode 用法：sql-empty / sql-not-select / sql-parse-failed / sql-multi-statement / table-not-sql / module-not-found）。**注意既有先例**：`metadata.module-not-found` 已在 `NopMetaModuleBizModel` 定义、`metadata.lineage-not-sql-table` 已在 `NopMetaLineageEdgeBizModel` 定义——item 1.1 裁定复用既有还是改用 sql 视图专属名（避免重名）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] D1/D2 决策已裁定并写入 §4.2 / §八（关闭待定问题）；`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS，既有回归测试全过
- [x] `createSqlTable` 可通过 GraphQL mutation 调用：输入有效 SELECT SQL 后 `NopMetaTable__findPage`/`get` 返回新建的 tableType=sql 记录（sourceSql 已存），返回的 fields 列表与 SELECT 输出列名一致
- [x] 字段解析器对含别名列（`expr AS alias`）、普通列、表达式列均正确解析（按 item 1.1 裁定的标记策略），字段数与 SELECT 输出列数一致
- [x] 通配符 `*`/`t.*`（SqlAllProjection）按 item 1.1 裁定处理（显式失败 / 方案 B 展开 / wildcard 标记），**不静默跳过、不返回空、不伪造**
- [x] 若 item 1.1 裁定取类型（方案 B）：类型来自真实 ResultSetMetaData（与 H2 实测数据一致）；不可用时 null + unavailable 显式标记（不伪造类型）；若裁定仅名（方案 A）：类型缺省为 null 且不伪造
- [x] `previewSqlFields` 对纯 SQL 文本返回字段列表（不产生任何持久化副作用，表行数不变）；`resolveTableFields` 对已建 sql 表返回与 preview 一致的字段（两者 fields 项结构统一为 `{name, alias?, type?}`）
- [x] 失败路径显式：非 SELECT SQL / 空 SQL / 语法错误 / **多语句（`;` 分隔，statements.size()!=1）** / metaModuleId 不存在 / resolveTableFields 命中非 sql 表或 sourceSql 空 → 显式失败抛 inline ErrorCode，**不静默存入脏数据、不静默返回空字段列表、不吞异常**
- [x] **端到端验证**：从 `createSqlTable`（输入 SQL）→ MetaTable 持久化（findPage 可查 tableType=sql）→ `resolveTableFields` 返回解析字段的完整路径已验证（见 Minimum Rules #22）
- [x] **接线验证**：createSqlTable / resolveTableFields 运行时确实调用了 AST 解析器（fields 非空且与 SELECT 列匹配证明），非空壳（见 Minimum Rules #23）
- [x] **无静默跳过**：不可解析/非 SELECT 路径显式失败；无空方法体 / 吞异常 / return null 占位（见 Minimum Rules #24）
- [x] **新功能测试**：新增测试覆盖 createSqlTable（多列+别名+表达式列成功 / 非 SELECT 拒绝 / 空 SQL 拒绝 / 语法错误拒绝 / 多语句拒绝 / 通配符按裁定处理 / module 不存在拒绝）+ previewSqlFields（字段名一致 + 无持久化副作用）+ resolveTableFields（与 preview 一致 / 非 sql 表失败），全绿（见 Minimum Rules #25）
- [x] `ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.2 + §八（字段解析方案裁定 + 类型获取 + 降级）已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：所有条目 + Phase Exit Criteria 全部 `[x]` 后才能 `completed`。

- [x] createSqlTable + previewSqlFields + resolveTableFields 可用
- [x] SELECT 字段解析器正确解析别名/列名/表达式列；类型获取按 item 1.1 裁定（不可用 null+unavailable 不伪造）
- [x] 失败路径显式（非 SELECT/空/不可解析/module 不存在/非 sql 表），不静默通过
- [x] 不存在空壳实现（无空方法体 / 静默跳过 / 吞异常）
- [x] 必要 focused verification 已完成（建表 + 解析 + 各失败路径测试全绿）
- [x] `./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（含测试）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [x] 受影响的 owner docs 已同步（`01-architecture-baseline.md` §4.2 + §八）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 createSqlTable/resolveTableFields 运行时确实调用 AST 解析器并返回真实字段（端到端连通）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-16-0700-1-nop-metadata-sql-view-creation-and-field-parsing.md --strict` 退出码 0

## Deferred But Adjudicated

### CTE/子查询/UNION 递归字段展开

- Classification: `optimization candidate`
- Why Not Blocking Closure: 首版取最外层 SELECT 输出列即满足"SQL 视图可创建可解析字段"的核心结果面。递归展开复杂 SQL 类型推断属独立复杂度，且 P4 查询执行前可按需补充
- Successor Required: yes（作为 P4 前的增量或 P3 收尾 follow-up）

### sql 视图表的字段类型完整推断（若 item 1.1 裁定方案 A 仅名）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 取决于 item 1.1 实测裁定。若首版仅名，类型推断为可后续增量（LIMIT 0 路径已在 D1 标定为可行候选）
- Successor Required: no（若裁定方案 B/C 则此项不成立）

## Non-Blocking Follow-ups

- 物化视图（CREATE VIEW）/ DDL 支持
- sql 视图表的查询执行（聚合/JOIN，P4）
- entity/sql 类型表的 Catalog/质量/剖析执行（querySpace→数据源解析，与 P2 deferred 同源，归 P4）
- 复杂 SQL（动态 SQL / UDF）的列类型展开

## Closure

Status Note: Plan 完成。createSqlTable / previewSqlFields / resolveTableFields 三个 action 已落地 NopMetaTableBizModel，SELECT 字段解析器 SqlSelectFieldExtractor 复用 EqlASTParser AST 解析（与 §2.6.1 血缘同解析器/同无 session 模式）。D1/D2 决策已裁定并写入 §4.2（§4.2.1 字段解析方案 + §4.2.2 action 契约）+ §八（关闭"SQL 视图字段解析"待定问题）。所有 Exit Criteria + Closure Gates 经独立子 agent closure-audit 验证 PASS（14 项 claim 全 PASS，Anti-Hollow 确认运行时调用链连通）。Deferred 项均为 optimization candidate（CTE 递归展开 / 类型推断 follow-up），无 in-scope live defect 降级。
Completed: 2026-07-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，audit-only，非实现者 session）
- Audit Session: ses_097e74625ffe7RqFUENV2cdpzl
- Evidence:
  - **A. createSqlTable 接线**：PASS — `NopMetaTableBizModel.java:175-212`，`@BizMutation`，调 `sqlFieldExtractor.extract(sql)` → 校验 module → 建 NopMetaTable(tableType=sql, sourceSql=sql) → saveEntity → 返回 `{metaTableId, tableName, tableType, fields}`
  - **B. previewSqlFields**：PASS — `NopMetaTableBizModel.java:223-229`，`@BizQuery`，无 saveEntity，返回 `{fields:[...]}`
  - **C. resolveTableFields**：PASS — `NopMetaTableBizModel.java:247-269`，`@BizQuery`，not-found/non-sql/empty-sourceSql 显式失败，返回同一 `{fields:[...]}` 结构
  - **D. 字段名解析（alias→col→expr_N）**：PASS — `SqlSelectFieldExtractor.java:166-184`，alias 经 `getAlias().getAlias()`，列名经 `SqlColumnName.getName()`，表达式标记 `<expr_N>`（仅表达式列递增）
  - **E. 通配符显式失败（两形式）**：PASS — 空 projections（裸 `*` 被解析器丢弃）+ SqlAllProjection（`t.*`）均抛 `ERR_SQL_VIEW_WILDCARD_NOT_SUPPORTED`，无空列表返回路径
  - **F. 多语句拒绝**：PASS — `SqlSelectFieldExtractor.java:93-96`，`statements.size()!=1` 抛 `ERR_SQL_VIEW_MULTI_STATEMENT`
  - **G. 非 SELECT 拒绝**：PASS — `SqlSelectFieldExtractor.java:99-102`，`getStatementKind()!=SELECT` 抛 `ERR_SQL_VIEW_NOT_SELECT`
  - **H. type=null（方案 A 不伪造）**：PASS — 所有 SqlViewField 构造 type=null
  - **I. CTE/UNION 支持**：PASS — `SqlSelectFieldExtractor.java:115-125`，SqlSelectWithCte 钻入 getSelect()；SqlSelect（含 SqlUnionSelect 经 getProjections 委托）
  - **J. 无空壳/静默跳过/吞异常**：PASS — 未知 projection 子类显式 throw；未处理 SELECT class 显式 throw；解析器 catch rethrow
  - **K. 测试覆盖（11 用例）**：PASS — testCreateSqlTableSuccess / testCreateSqlTableRejectNonSelect / RejectEmptySql / RejectSyntaxError / RejectMultiStatement / RejectWildcard（`*`+`t.*`）/ RejectModuleNotFound / testPreviewSqlFieldsNoPersistence / testResolveTableFieldsMatchesPreview / testResolveTableFieldsFailsOnNonSqlTable / testResolveTableFieldsFailsOnNotFound
  - **L. 端到端 + 接线**：PASS — testCreateSqlTableSuccess 断言 getEntityById 返回 tableType=sql + sourceSql 持久化；testResolveTableFieldsMatchesPreview 断言字段名 order_id/cid/<expr_1>（来自真实 AST 解析，非空壳）
  - **M. ErrorCode inline + 无重名**：PASS — sql- 前缀（sql-module-not-found/sql-table-not-found/sql-table-not-sql/sql-source-sql-empty + 解析器 5 个），与 lineage 的 lineage-not-sql-table 区分
  - **N. 架构文档更新**：PASS — `01-architecture-baseline.md` §4.2.1（字段解析方案）+ §4.2.2（action 契约）+ §八（待定问题划线标注已裁定）
  - **Anti-Hollow**：PASS — createSqlTable/resolveTableFields 运行时确实调用 AST 解析器：testCreateSqlTableSuccess 断言 fields[0].name=="id"、fields[1].name=="user_name"+alias、fields[2].name=="<expr_1>"、type==null；stub 会立即失败这些相等性断言
  - **`scan-hollow-implementations.mjs --module nop-metadata --severity high`**：退出码 0，0 findings
  - **`check-plan-checklist.mjs ... --strict`**：退出码 0（Closure Evidence 已写入本节）
  - **`./mvnw clean install -pl nop-metadata -am -T 1C`**：BUILD SUCCESS，TestNopMetaTableBizModel 21 tests（含新增 11）全 0 failure
  - **Deferred 项分类检查**：CTE 递归展开 / 类型推断均为 optimization candidate（已裁定方案 A，Successor Required 标注清晰），无 in-scope live defect 降级

Follow-up:

- 物化视图（CREATE VIEW）/ DDL 支持（Non-Blocking）
- sql 视图表查询执行（聚合/JOIN，P4）
- entity/sql 类型表 Catalog/质量/剖析执行（querySpace→数据源解析，归 P4）
- 复杂 SQL（动态 SQL / UDF）列类型展开
- 字段类型推断 follow-up（方案 B LIMIT 0+ResultSetMetaData / 方案 C 手动录入，见 §4.2.1）
- CTE/子查询/UNION 递归字段展开（见 Deferred But Adjudicated）

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
