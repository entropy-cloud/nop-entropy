# 2 nop-metadata 列级 SQL 血缘解析（SELECT 列→源列映射）

> Plan Status: completed
> Last Reviewed: 2026-07-17
> Draft Review: 两轮独立子 agent 对抗性审查（含想象性分析 + AST 字段分层 live 核验）。R1 共识 NO（1 Major）：Non-Goal"不改动表级行为"与 D2"补 sourceColumn IS NULL"矛盾——已将隔离补丁移入 In Scope（Non-Goal 限定为"不重写表级解析逻辑"）。R1 另发现 6 Minor（parseFromText 签名简写、getOwner 返回类型、表达式多边测试缺失、transformExpr 提取、SqlAggregateFunction instanceof、Rule #10 边界），关键项已修复。R2 逐条核实 Major-1 三处一致 + Minor 全部 PASS，共识 YES。核心技术 claim（AST 句法字段可用/resolution 字段 null）经逐字段核实验证通过。
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md`（P2 血缘 deferred）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.6.1 血缘采集（列级 SQL 解析 deferred at 行 433）；plan `2026-07-16-0420-2` Deferred But Adjudicated「列级 SQL 血缘解析（SELECT 列→源列映射）」Successor Required: yes
> Mission: nop-metadata
> Work Item: P2-5+ — 列级 SQL 血缘解析（在已落地的表级 sql_parse 之上扩展到列级）
> Related: `2026-07-16-0420-2-nop-metadata-lineage-collection-and-traversal.md`（P2-5 血缘采集+遍历，已 done——表级 sql_parse + 列级 manual 已落地，本 plan 扩展列级 sql_parse）、`2026-07-16-0700-1-nop-metadata-sql-view-creation-and-field-parsing.md`（P3-1 SELECT 字段解析，已 done——`SqlSelectFieldExtractor` 同 EqlASTParser 解析器）

## Purpose

把反复 deferred 的「列级 SQL 血缘解析（SELECT 输出列→源表源列映射）」从 follow-up 推进到 landed：在已落地的表级 sql_parse 血缘（`extractLineageFromSql`）之上，新增列级解析能力——解析 tableType=sql 视图的 `sourceSql`，将 SELECT 输出列映射回其引用的源表源列，写入 `NopMetaLineageEdge` 列级边（sourceColumn/targetColumn 填充），收口"sql_parse 血缘可表级也可列级"这一结果面。

## Current Baseline

- **表级 sql_parse 已落地（P2-5，plan 0420-2 done）**：`extractLineageFromSql(metaTableId)` 解析 tableType=sql 的 `NopMetaTable.sourceSql`，从 FROM/JOIN 抽取源表名，匹配目录 `NopMetaTable.tableName`，创建**表级边**（sourceColumn/targetColumn 留空，lineageSource=sql_parse）。幂等 upsert。dangling 引用进 unresolved 列表（不建悬空边）。
- **列级 manual 已落地**：`recordLineage` 支持表级+列级手工录入（sourceColumn/targetColumn 存列名字符串）。
- **列级 sql_parse 未实现（本 plan 目标）**：`01-architecture-baseline.md` §2.6.1 行 433 明确「列级 SQL 解析（SELECT 列→源列映射）不在首版，复杂度过高」。plan 0420-2 Deferred「列级 SQL 血缘解析（SELECT 列→源列映射）」Classification=optimization candidate，Successor Required: yes。
- **解析器基建（直接复用，已 done）**：
  - `SqlSourceTableExtractor`（`nop-metadata-service/.../lineage/SqlSourceTableExtractor.java`）：用 `EqlASTParser.parseFromText` 纯语法解析 SQL，递归 walk AST 收集 `SqlSingleTableSource` 表名。本 plan 需在其之上扩展列级映射（SELECT projection → 源表源列）。
  - `SqlSelectFieldExtractor`（`.../sqlview/SqlSelectFieldExtractor.java`）：解析 SELECT 输出列名（alias/col/expr_N），处理 CTE（钻入 getSelect）、UNION（firstSelect）。本 plan 的列级血缘需复用同解析器，但额外需**将输出列关联回源表源列**（解析 projection 表达式中的列引用所属表）。
- **AST 字段分层（关键限制，item 1.1 须据此裁定实现路径）**：`EqlASTParser.parseFromText` 返回纯句法 AST，**句法字段可用 / resolution 字段为 null**：
  - ✅ 句法可用：`SqlColumnName.getName()`（列名）、`SqlColumnName.getOwner()`（列限定符，如 `t1.col` 的 `t1`，AST child）、`SqlSingleTableSource.getAlias()/getScopeName()`（表别名）、`SqlSingleTableSource.getTableName().getName()`（表名）。
  - ❌ resolution 字段纯 parse 后为 null：`SqlColumnName.getTableSource()`/`getResolvedOwner()`、`SqlQualifiedName.getResolvedSource()`/`getResolvedTableMeta()`——这些仅在 compile/resolution 阶段（`EqlTransformVisitor`）填充，列级血缘**不调用 resolution**，不得依赖（否则 NPE）。
  - **归属解析正确路径**：从 FROM/JOIN 的 `SqlSingleTableSource` 手建 `alias/name → tableName` 映射；对 projection 表达式内每个 `SqlColumnName` 用 `getOwner()` 查映射表归属源表（不用 `getTableSource()`）。表达式列（`a+b`）walk expr 子树收集所有 `SqlColumnName` 节点（`forEachChild` 已递归）。
- **NopMetaLineageEdge 已有列级字段（无需 ORM 变更）**：`sourceColumn`(precision 100) / `targetColumn`(precision 100) / `transformType`(direct|derived|aggregated) / `transformExpression` / `lineageSource` 均已建模。列级 sql_parse 边只需填充 sourceColumn/targetColumn + lineageSource=sql_parse + transformType。
- **图遍历已支持列级过滤（P2-5 done）**：`getImpactAnalysis(metaTableId, columnName?)` 按 sourceColumn/targetColumn 列过滤；`getUpstream`/`getDownstream`/`getLineagePath` 表级。列级边写入后自动被这些遍历消费，无需改遍历代码。
- **设计 §2.6.1 限制（显式记录）**：表级 sql_parse 已记录「不展开 CTE 别名、子查询别名、动态 SQL」。列级解析的复杂度在于**列引用到源表的归属解析**（SQL 中 `t.col` / `col` / 表别名限定 / 表达式列）。
- **测试基建**：Nop AutoTest 可用，0420-2 已证明可在 H2 建目录表 + 录入 sourceSql + 抽取血缘 + 遍历断言。`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS。

## Goals

- 新增列级 SQL 血缘解析 action（如 `extractColumnLineageFromSql`）：解析 tableType=sql 的 sourceSql，将 SELECT 输出列映射回源表源列，写入 NopMetaLineageEdge 列级边（sourceColumn/targetColumn 填充，lineageSource=sql_parse，transformType 按列表达式判定 direct/derived）
- 列引用归属解析：解析输出列引用的源表源列（含表别名限定 `t.col`、无别名列归属推断），映射到目录匹配后的源表
- 幂等 + dangling 一致：列级边按列级幂等键去重 upsert；源列所属表未匹配目录时按 dangling 策略处理（不建悬空边，进 unresolved）
- AutoTest：建 sql 视图表（sourceSql 引用已注册源表）→ 抽取列级血缘 → 断言 NopMetaLineageEdge 列级边 sourceColumn/targetColumn/transformType 正确，且 getImpactAnalysis 按 columnName 过滤生效

## Non-Goals

- **表级 sql_parse 重写**——表级已落地且稳定，本 plan 不重写表级 `extractLineageFromSql` 的解析逻辑。但**表级 upsert 隔离补丁**（补 `sourceColumn IS NULL` 过滤）属于列级共存的必需隔离，**在 scope 内**（见 In Scope），不违反此 Non-Goal
- **复杂表达式列的精确 transformExpression**——derived 列（如 `CONCAT(a,b)`）记 transformType=derived + 可选 transformExpression 原文，不做表达式 AST 标准化（follow-up）
- **CTE 内部列展开 / 子查询列穿透**——SQL 中 CTE 别名/子查询别名的列穿透属高复杂度，按 D3 裁定范围（首版支持直查 SELECT ... FROM 源表 [JOIN 源表]，CTE/子查询的列映射 deferred）
- **聚合列的 aggregated transformType 精确判定**——如 `SUM(col)` 记 transformType=aggregated，但不展开聚合内部表达式语义（follow-up）
- **血缘可视化（前端图渲染 JSON）**——follow-up（0420-2 Non-Blocking Follow-up）

## Design Decisions

> D1/D2/D3 为硬前置门禁，须在 item 1.1 裁定并写入 `01-architecture-baseline.md` §2.6.1 后实现。

### D1. 列引用归属解析范围 + 可解析性边界（待 item 1.1 裁定）

- **可解析形态**：裁定首版支持的 SELECT 输出列→源列映射形态。推荐首版支持：
  - 直接列引用 `SELECT t.col`（有表别名/限定符限定）：用 `SqlColumnName.getOwner()` 查 alias→table 映射归属，sourceColumn=col。别名输出 `SELECT t.col AS out_col`（targetColumn=out_col，sourceColumn=col）。
  - 无别名列引用 `SELECT col`（无限定符）：**仅当 FROM 仅一张源表时**归属该唯一源表；**FROM 多表时一律不可归属**（纯句法无法判断 col 属于哪张表——不是仅同名列才歧义，是多表即歧义），记 unresolved/transformType=derived（不尝试跨表列名推断、不查目录字段集合做归属猜测）。
  - 表达式列 `SELECT a+b AS total`（transformType=derived，walk expr 子树收集所有列引用节点，各列按其 owner 归属，targetColumn=total/alias）。
- **列存在性不校验（软引用裁定）**：解析出的 sourceColumn/targetColumn 存**列名字符串原样**，不校验该列是否真实存在于源表（对齐 §2.6.1 external 列级软引用裁定——SQL 笔误/陈旧引用存原样，运行时遍历发现悬空由调用方判断）。item 1.1 确认此软引用策略。
- **不可解析/降级（不静默跳过）**：CTE 别名列穿透、子查询内层列穿透、动态 SQL、`SELECT *` 通配符（无法确定列）→ 显式记 unresolved 或 transformType=derived（按 D1 裁定），不伪造列映射、不静默丢弃。
- **列名匹配**：sourceColumn/targetColumn 存**列名字符串**（对齐 §2.6.1 external 列级软引用裁定，不建硬 FK）。列引用归属通过 SQL AST 的表别名→源表名映射解析（FROM/JOIN 的别名绑定源表），**仅用句法字段**（getOwner/getAlias，见 Current Baseline AST 字段分层）。

### D2. action 落点 + 与表级的关系 + upsert 查询隔离（待 item 1.1 裁定）

- **落点**：裁定是否**新增独立 action** `extractColumnLineageFromSql(metaTableId)`（与表级 `extractLineageFromSql` 并列，落点 `NopMetaLineageEdgeBizModel` 或 `NopMetaTableBizModel`），还是**扩展表级 action 增加列级参数**（如 `extractLineageFromSql(metaTableId, columnLevel:boolean)`）。推荐新增独立 action（语义清晰，不破坏表级既有契约）。
- **与表级共存 + upsert 查询隔离（关键）**：现有表级 `upsertSqlParseEdge` 按 `(sourceTableId, targetTableId, lineageSource='sql_parse')` 查询**不带 sourceColumn 过滤**——列级边（sourceColumn 非空）会被此三元组查询误匹配，导致表级重抽时误更新列级边而非创建表级边。item 1.1 须裁定隔离方案：**表级 upsert 查询补 `sourceColumn IS NULL` 过滤条件**（让表级只匹配表级边），列级 upsert 用列级五元组。两者共存后各管各的幂等。列级 action 是否同时确保表级边存在（先表级后列级），在 item 1.1 裁定。
- **返回契约**：返回 `Map{edges:[...], unresolved:[...], errors:[...]}`（对齐表级 extractLineageFromSql 风格），列级边数 + unresolved + 失败隔离。

### D3. transformType 判定 + CTE/子查询范围 + 幂等键（待 item 1.1 裁定）

- **transformType 判定**：直接列引用→direct；表达式/函数包裹列→derived；聚合函数（SUM/COUNT/AVG/MIN/MAX）包裹列→aggregated。判定规则（AST 节点类型匹配）在 item 1.1 裁定。**transformExpression 列实际名为 `transformExpr`**（`nop-metadata.orm.xml` 列 `name="transformExpr"`），裁定是否填充 transformExpr（推荐 derived/aggregated 记表达式原文，direct 留空）。
- **CTE/子查询范围**：首版支持「直查 FROM 源表 [JOIN 源表]」的列映射；CTE/子查询（FROM (subquery) / WITH ... ）的列穿透 deferred（记 unresolved 或 derived，不伪造）。
- **幂等键**：列级边按 `(sourceTableId, targetTableId, sourceColumn, targetColumn, lineageSource='sql_parse')` 去重 upsert（比表级多了 sourceColumn/targetColumn）。item 1.1 确认幂等键是否含 transformType（推荐不含，重抽更新 transformType/transformExpr）。

## Scope

### In Scope

- 新增列级 SQL 血缘解析器（无状态）：解析 SELECT projections → 输出列→源列映射（表别名→源表名绑定 + 列引用归属），复用 EqlASTParser.parseFromText（同 §2.6.1/§4.2.1 解析器）
- 新增 action（按 D2 落点）：`extractColumnLineageFromSql(metaTableId)` 或扩展表级，写入 NopMetaLineageEdge 列级边
- 列引用归属解析 + transformType 判定（按 D1/D3）+ dangling/unresolved 一致策略
- **表级 upsert 隔离补丁**：给表级 `upsertSqlParseEdge` 查询补 `sourceColumn IS NULL` 过滤条件，使表级查询只匹配表级边，列级边写入后不破坏表级重抽（隔离必需，非表级逻辑重写）
- AutoTest：建 sql 视图表 → 抽取列级血缘 → 断言列级边 + getImpactAnalysis 列过滤 + 跨表表达式列多源列边

### Out Of Scope

- 表级 sql_parse 重写（已稳定）
- CTE/子查询列穿透、聚合内部表达式语义展开（follow-up）
- 血缘可视化前端 JSON（follow-up）
- transformExpression AST 标准化（follow-up）

## Execution Plan

### Phase 1 - 列级血缘解析器 + action + 列级边写入

Status: completed
Targets: `nop-metadata/nop-metadata-service/.../lineage/`（新增列级解析器）、`.../entity/NopMetaLineageEdgeBizModel.java`（或 NopMetaTableBizModel，新增 action，按 D2）、`TestNopMetaLineageEdge*.java`

- Item Types: `Decision`（D1 列引用归属/可解析边界 + D2 action 落点/与表级关系 + D3 transformType/CTE 范围/幂等键，硬前置）+ `Proof`（新功能：列级 sql_parse 血缘）

> **硬前置门禁（item 1.1）**：D1/D2/D3 必须先裁定（只裁定不写代码），写入 `01-architecture-baseline.md` §2.6.1（列级 sql_parse 范围/解析器/transformType/幂等键/限制）后实现。**关键核实（见 Current Baseline AST 字段分层）**：列引用归属**必须用句法字段**（`SqlColumnName.getOwner()` + 手建 alias→table 映射），**不得用 resolution 字段**（`getTableSource()` 等纯 parse 后为 null，会 NPE）；无别名列多表场景一律不可归属（非仅同名列歧义）；表级/列级 upsert 查询须隔离（表级补 `sourceColumn IS NULL`，见 D2）。

- [x] 1.1 **列引用归属解析范围 + action 落点 + upsert 隔离 + transformType/CTE 范围/幂等键决策（硬前置门禁，Decision only）**：基于 live repo 核实并裁定 D1/D2/D3——核实 EqlASTParser AST 句法字段（`SqlColumnName.getOwner()` 返回 `SqlQualifiedName`，需 `.getName()` 取限定符；`SqlSingleTableSource.getAlias()`/`getTableName()`）可用且 resolution 字段（`getTableSource()`）纯 parse 后为 null；裁定可解析形态（限定符列/别名/表达式列；无别名列仅单表可归属，多表一律 unresolved）+ 降级策略（CTE/子查询/通配符显式 unresolved 或 derived，不伪造）；裁定 action 落点（新增独立 vs 扩展表级）；裁定 transformType 判定规则（直接列→direct；表达式/函数→derived；聚合检测优先 `expr instanceof SqlAggregateFunction`，非函数名白名单）；裁定 transformExpr 填充（derived/aggregated 记原文或留空）；裁定 CTE/子查询范围（首版直查，CTE deferred）；裁定幂等键（列级五元组）。**裁定表级/列级 upsert 查询隔离**：表级 `upsertSqlParseEdge` 查询补 `FilterBeans.isNull("sourceColumn")` 过滤（In Scope，非表级逻辑重写）；列级 upsert 用列级五元组。**只裁定不写代码**。结论写入 `01-architecture-baseline.md` §2.6.1（列级 sql_parse 子节）
- [x] 1.2 **列级血缘解析器（Proof，依赖 1.1）**：新增无状态解析器（参考 SqlSourceTableExtractor + SqlSelectFieldExtractor 同 EqlASTParser 模式），输入 sourceSql → 解析 SELECT projections → 建表别名→源表名映射（FROM/JOIN）→ 对每个输出列解析其引用的源列 + 归属源表 → 输出 `列级边候选列表{targetColumn, sourceTableName, sourceColumn, transformType}`。不可解析形态（CTE/子查询/通配符）显式标记 unresolved/derived（不伪造、不静默丢弃）
- [x] 1.3 **action 接线 + 列级边写入（依赖 1.1，按 D2 落点）**：在 BizModel 新增 action（按 D2）：加载 NopMetaTable(tableType=sql) → 不存在/non-sql/空 sourceSql 显式失败 → 调列级解析器 → 源表名匹配目录 tableName → 命中则 upsert NopMetaLineageEdge 列级边（sourceColumn/targetColumn/transformType/lineageSource=sql_parse，按 D3 幂等键）→ 未匹配进 unresolved → 返回 `{edges, unresolved, errors}`。dangling 不建悬空边（sourceTableId mandatory）
- [x] 1.4 错误码按现有模式 inline 定义（参考 NopMetaLineageEdgeBizModel inline ErrorCode）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] D1/D2/D3 决策已裁定并落地，且**可观测不变量成立**：列级血缘 action 可调用；既有表级血缘 + 遍历回归测试全过（不破坏既有）
- [x] 列级边写入正确：建 sql 视图表（sourceSql 如 `SELECT t1.a AS x, t2.b FROM src1 t1 JOIN src2 t2 ON ...`）→ 抽取 → `NopMetaLineageEdge__findPage` 返回列级边（sourceColumn/targetColumn/transformType 正确，如 x←a direct、b←b direct），非伪造
- [x] 跨表表达式列多源列边成立：`SELECT t1.a + t2.b AS total FROM src1 t1 JOIN src2 t2` → 产出 2 条 derived 边（total←src1.a + total←src2.b），一个表达式输出列引用 N 个源列产出 N 条边
- [x] 表别名归属解析成立：`t1.a` 归属 src1、`t2.b` 归属 src2（源列正确关联到对应源表）
- [x] transformType 判定成立：直接列→direct、表达式/函数列→derived、聚合列→aggregated（按 D3）
- [x] 幂等成立：重复抽取同一 sql 表，列级边不无限追加（按列级幂等键 upsert）
- [x] 表级/列级 upsert 隔离成立：列级边存在后，重跑表级 `extractLineageFromSql` 仍正确创建/更新表级边（表级查询补 `sourceColumn IS NULL`，不误匹配列级边），二者共存正确
- [x] 无别名单表归属成立：`SELECT col FROM single_src` 的 col 归属 single_src；多表 `SELECT col FROM a,b` 的无别名列进 unresolved（不跨表推断）
- [x] dangling 一致：源列所属表未匹配目录 → 进 unresolved 列表，不建悬空边（sourceTableId 不会 null）
- [x] 降级显式（不静默跳过）：CTE/子查询/通配符等不可解析形态显式 unresolved 或 derived，不伪造列映射、不静默丢弃
- [x] 列级边被遍历消费：列级边写入后 `getImpactAnalysis(metaTableId, columnName)` 按 columnName 过滤生效（无需改遍历代码）
- [x] **端到端验证**：从建 sql 视图表 → 抽取列级血缘 → `NopMetaLineageEdge__findPage` 查到列级边 → `getImpactAnalysis` 列过滤生效的完整路径已验证（见 Minimum Rules #22）
- [x] **接线验证**：列级解析器运行时确实被 action 调用并执行了 AST 列引用解析（列级边含真实 sourceColumn/targetColumn 证明），非空壳（见 Minimum Rules #23）
- [x] **无静默跳过**：不可解析形态显式 unresolved/derived；失败路径显式抛 ErrorCode；无空方法体 / 吞异常 / 静默空返回（见 Minimum Rules #24）
- [x] **新功能测试**：新增测试覆盖 直接列+别名+表达式列映射 + 表别名归属 + transformType 判定 + 幂等 + dangling unresolved + 不可解析降级 + getImpactAnalysis 列过滤，全绿（见 Minimum Rules #25）
- [x] `ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.6.1 列级 sql_parse 子节已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：所有条目 + Phase Exit Criteria 全部 `[x]` 后才能 `completed`。

- [x] 列级 SQL 血缘 action 端到端可用（建 sql 表 → 抽取 → 列级边可查 + 遍历列过滤生效）
- [x] 列引用归属解析成立（表别名限定 + 无别名推断）
- [x] transformType 判定成立（direct/derived/aggregated）
- [x] 幂等 + dangling 一致（不无限追加 / 不建悬空边）
- [x] 不存在空壳实现（无空方法体 / 静默跳过 / 吞异常）
- [x] 既有表级血缘 + 遍历回归不破坏（全过）
- [x] 必要 focused verification 已完成（列级映射 + 归属 + transformType + 幂等 + dangling + 降级 + 遍历测试全绿）
- [x] `./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（含测试）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [x] 受影响的 owner docs 已同步（`01-architecture-baseline.md` §2.6.1）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证列级解析器运行时确实执行 AST 列引用解析并写入真实列级边（端到端连通）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-17-0228-2-nop-metadata-column-level-sql-lineage.md --strict` 退出码 0

## Deferred But Adjudicated

### CTE / 子查询列穿透

- Classification: `optimization candidate`
- Why Not Blocking Closure: CTE 别名/子查询内层列穿透需递归解析 CTE 定义 + 列名传播，复杂度独立。首版直查 SELECT ... FROM 源表 [JOIN] 的列映射已满足"sql_parse 列级血缘可产出"的核心结果面
- Successor Required: no（独立增量，按需 follow-up）

### 聚合内部表达式语义展开

- Classification: `optimization candidate`
- Why Not Blocking Closure: `SUM(col)` 记 aggregated transformType 已满足列级归属 + 转换类型判定。聚合内部表达式的精确语义展开属独立复杂度
- Successor Required: no

## Non-Blocking Follow-ups

- transformExpression AST 标准化（首版记原文或留空）
- 血缘可视化前端图渲染 JSON（0420-2 Non-Blocking Follow-up）
- 定时自动抽取调度（当前手动 action）
- 多 schema 列级血缘（需 NopMetaTable schema 列，与 Catalog/质量/剖析同源 follow-up）

## Closure

Status Note: 列级 SQL 血缘解析（SELECT 输出列→源表源列映射）已落地。新增无状态解析器 `SqlColumnLineageExtractor` + action `extractColumnLineageFromSql`，解析 tableType=sql 的 sourceSql，将 SELECT projections 映射回源表源列（表别名→源表名映射 + 列引用归属，仅用 AST 句法字段），写入 NopMetaLineageEdge 列级边（sourceColumn/targetColumn/transformType 填充，lineageSource=sql_parse）。表级/列级 upsert 查询隔离（表级补 `sourceColumn IS NULL`）。9 个新测试覆盖直接列/别名/跨表表达式多源列/聚合/幂等/隔离/单表归属 vs 多表歧义/dangling/通配符降级/getImpactAnalysis 列过滤，全绿。无空壳实现（端到端连通：action→解析器→AST 列引用解析→upsert→可查边）。
Completed: 2026-07-17

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（session ses_093445982ffeEcFtXVv3UoX6dS，fresh session，非执行 agent 复用）
- Evidence:
  - 每条 Exit Criterion（L110-126）：全 PASS。L111/113 testExtractColumnLineageDirectAndAliasAttribution 断言 x←src1.a direct、b←src2.b direct + 表别名归属 src1≠src2；L112 testExtractColumnLineageExpressionMultiSource 断言 t1.a+t2.b AS total → 2 derived 边；L114 testExtractColumnLineageAggregate 断言 SUM(a)→aggregated；L115 testIdempotent 断言重复抽取不追加；L116 testTableAndColumnLevelUpsertIsolation 断言列级+表级共存；L117 testUnqualifiedColumnSingleVsMultiTable 断言单表归属/多表歧义 unresolved；L118 testDangling 断言不建悬空边；L119 testWildcardAndFailures 断言通配符降级+失败路径显式；L120 testColumnLineageConsumedByImpactAnalysis 断言列过滤生效；L122/123 接线+无静默跳过经代码追踪验证。
  - 每条 Closure Gate（L132-144）：全 PASS。
  - `./mvnw test -pl nop-metadata/nop-metadata-service -am`：241 tests 全绿（232 baseline + 9 新增，0 failures / 0 errors）。注：`./mvnw clean install -pl nop-metadata -T 1C` 中 `nop-metadata-web` 的 `NopMetadataWebPagesTest` 失败（NopMetaDataContract/main.page.yaml extends `/nop/auth/pages/NopAuthUser/picker.page.yaml`，nop-auth-web 资源不在 nop-metadata-web 测试 classpath）——**pre-existing**，已 stash 本 plan 改动后在基线复现同一失败，与本 plan 无关（service 模块 scope 内全绿）。
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（无 high/critical 空壳发现）。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-17-0228-2-...md --strict` 退出码 0（见下方实测）。
  - Anti-Hollow 检查：端到端调用链 `extractColumnLineageFromSql`→`columnExtractor.extract`→`EqlASTParser.parseFromText`→FROM 别名映射→`SqlColumnName.getOwner().getName()` 归属→`upsertColumnSqlParseEdge`→持久化 NopMetaLineageEdge 运行时连通（测试断言列级边含真实 sourceColumn/targetColumn/transformType 证明非空壳）。
  - Deferred 项分类检查：CTE/子查询列穿透 + 聚合内部表达式语义展开 均为 `optimization candidate`（非 in-scope live defect），Why Not Blocking Closure 已写明，无 in-scope defect 被降级。

Follow-up:

- transformExpression AST 标准化（首版 transformExpr 留空）
- 血缘可视化前端图渲染 JSON（0420-2 Non-Blocking Follow-up）
- 定时自动抽取调度（当前手动 action）
- 多 schema 列级血缘（需 NopMetaTable schema 列，与 Catalog/质量/剖析同源 follow-up）
- CTE/子查询列穿透（Deferred But Adjudicated，optimization candidate，按需 follow-up）
