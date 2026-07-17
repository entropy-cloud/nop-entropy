# Column-Level Lineage CTE / Subquery Column Passthrough

> Plan Status: active
> Last Reviewed: 2026-07-17
> Source: roadmap `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` P2-5+（列级血缘）；plan 0228-2 Deferred But Adjudicated「CTE / 子查询列穿透」「聚合内部表达式语义展开」；架构基线 `01-architecture-baseline.md` §2.6.1（列级 D3）+ §4.2.1（CTE/UNION 行 795-801）
> Mission: nop-metadata
> Work Item: P2-5++ CTE/子查询列穿透（列级血缘解析增强）
> Related: `2026-07-17-0228-2`（列级 SQL 血缘首版，本 plan 直接 successor）、`2026-07-16-0700-1`（sql 视图字段解析）

## Purpose

把列级血缘解析从「仅直查源表 SELECT ... FROM 源表 [JOIN]」收口到「支持 CTE（WITH）与派生表（FROM (...) alias）的别名列穿透」，使经 CTE/子查询别名引用的列能解析到底层源表源列并产出血缘边。plan 0228-2 已把 CTE/子查询穿透裁定为独立 optimization successor，本 plan 兑现。

## Current Baseline

（live repo 核实，2026-07-17）

- 解析器：`SqlColumnLineageExtractor.extract(String sql)`（`nop-metadata-service/.../lineage/SqlColumnLineageExtractor.java:78`），纯语法 AST（`EqlASTParser.parseFromText`，无 session 绑定）。
- `resolveQuerySelect`（extractor :160-185）：`SqlSelectWithCte` 钻入 `getSelect()` 取 body；`SqlUnionSelect` 取 firstSelect。**CTE 定义体本身不被解析为列来源。**
- AST 能力已核实可用：`SqlSelectWithCte.getWithCtes()` → `List<SqlCteStatement>`（`getName()`/`getStatement():SqlSelect`）；`SqlSubqueryTableSource.getQuery():SqlSelect` + `getAlias()`；`SqlColumnName.getOwner()` → `SqlQualifiedName.getName()`。
- **两类别名当前失败机制不同（关键事实）**：
  - **派生表**（`FROM (SELECT ...) d`）解析为 `SqlSubqueryTableSource`，`buildAliasTableMap`（extractor :192-218）**仅收集 `SqlSingleTableSource`**，故派生表别名被跳过 → `attribute`（:268-288）owner 未命中 → `unresolvable("owner-not-matched:<owner>")`（:277-278）。
  - **CTE 引用**（`FROM cte c`）按 EQL 文法解析为 **`SqlSingleTableSource`**（CTE 名在句法上即表名），故 `buildAliasTableMap` **会收集**（`c → "cte"`），`attribute` **命中**返回 `resolved(targetCol, "cte", sourceCol, direct)`（:275）—— 失败发生在 **BizModel 目录匹配层**：`nameToId.get("cte")` 为 null → unresolved `"source-table-not-in-catalog"`。
- transformType 判定（extractor `resolveTransformType` :238-249）：`SqlAggregateFunction` → aggregated；裸 `SqlColumnName` → direct；其他 → derived。`transformExpr` 字段未被 sql_parse 路径填充。
- 持久化侧：`NopMetaLineageEdgeBizModel.extractColumnLineageFromSql`（:222-223）—— `isUnresolvable()` 候选进入返回的 `unresolved` 列表（:260-264），**不写边**；仅 resolved 候选经 `upsertColumnSqlParseEdge(sourceId, targetId, sourceColumn, targetColumn, transformType)`（:273-274）写边。结果 Map = `{extractedEdgeCount, unresolved, errors}`（:278-282）。**本 plan 无需改持久化结构**。
- 结论：CTE 引用产出 resolved 候选但目录层 miss；派生表产出 owner-not-matched unresolved。两者均不写边到**底层**源表（确认 gap，非接口壳）。

## Goals

- 解析 CTE（`WITH name AS (...)`）：先解析 `SqlSelectWithCte.getWithCtes()` 每个 CTE（`SqlCteStatement.getName()` + `getStatement()`，经既有 `resolveQuerySelect` 处理 UNION/嵌套 select），把 CTE 输出列解析到底层源表源列，建立 `CTE 名 → {输出列 → [(源表, 源列, transformType)]}`。
- **CTE 引用穿透**（关键修正）：CTE 引用解析为 `SqlSingleTableSource`（CTE 名=tableName，已进 aliasMap）。在归属解析阶段，对 owner 命中的源表名若匹配某个**已注册 CTE 名**，则按 CTE 输出列映射穿透到底层源列产出 resolved 候选（而非当作未知物理表）。即：CTE 穿透须在**已收集的 SqlSingleTableSource 集合**中按名拦截，**不是**新增 SqlSubqueryTableSource 分支。
- 解析派生表（`FROM (...) alias` → `SqlSubqueryTableSource`）：在 `buildAliasTableMap` 新增对 `SqlSubqueryTableSource` 的处理——递归解析其输出列 → 底层源列，注册 `alias → {...}`；经该别名引用的列穿透到底层源列产出 resolved 候选。
- 嵌套 CTE/派生表递归解析（带环路守卫，防自引用无限递归）。
- transformType 透传正确：纯透传列继承底层 direct/derived/aggregated；CTE 内聚合列 → aggregated。
- 解析出的 resolved 候选经既有 BizModel 目录匹配后正常写边（**无需改持久化结构**）；CTE 引用不再因目录 miss 而丢失（穿透后源表为底层物理表，目录可匹配）。

## Non-Goals

- **`WITH RECURSIVE` 自引用递归 CTE**：不在本 plan（自引用会无限展开，需语义裁定深度/基线条件），配置时显式 unsupported。
- **通配符展开**（CTE/派生表内 `SELECT *`）：沿用既有显式 unresolved（纯 AST 无列清单），不在本 plan 改变。
- **聚合内部表达式精确语义展开**（`SUM(a+b)` 内部拆解）：沿用 0228-2 deferred，记 aggregated 即可。
- 多 schema 列级血缘（NopMetaTable 无 schema 列）：归入多 schema plan，不在本 plan。
- 持久化结构变更：本 plan **不改 ORM**，仅产出更多 resolved 候选经既有 upsert 写边。

## Scope

### In Scope

- CTE 输出列 → 底层源列穿透解析 + CTE 名/别名注册。
- 派生表输出列 → 底层源列穿透解析 + 别名注册。
- 嵌套递归解析 + 环路守卫（已见 CTE/别名集合防环）。
- transformType 透传。
- 单元测试（解析器级）+ AutoTest（BizModel 端到端：含 CTE/派生表的 sql 表 sourceSql 抽取列血缘，断言边落到底层源表）。

### Out Of Scope

- `WITH RECURSIVE` 自引用 CTE（显式 unsupported）、通配符展开、聚合内部展开、多 schema、持久化结构变更（→ Deferred / Non-Goals）。

## Execution Plan

### Phase 1 - CTE 列穿透解析

Status: planned
Targets: `nop-metadata/nop-metadata-service/.../lineage/SqlColumnLineageExtractor.java`（CTE 定义解析 + 注册 + body 穿透）

- Item Types: `Fix`（CTE 穿透缺失）、`Proof`（单测 + AutoTest）

- [ ] 解析 `SqlSelectWithCte.getWithCtes()` 的 CTE 定义列表：对每个 CTE（`SqlCteStatement.getName()` + `getStatement()`，后者经既有 `resolveQuerySelect` 处理 UNION/嵌套）递归解析其输出列 → 底层源列，构造 `CTE 名(lower) → {输出列名(lower) → List<(源表, 源列, transformType)>}`。
- [ ] **CTE 穿透拦截**：CTE 引用解析为 `SqlSingleTableSource`（已进 aliasMap，映射值 `c → "cte"`，即 owner 别名 → 解析后**源表名=CTE 名**）。在 `attribute` 命中 owner 并取得**源表名**后，若该**源表名**匹配已注册 CTE 名，按引用列名查 CTE 输出列映射，对每个底层源列产出 resolved 候选（源表=底层源表 simpleName，transformType=透传底层或 aggregated）；**非**新增 SqlSubqueryTableSource 分支。
- [ ] 环路守卫：解析 CTE 体时传入「正在解析的 CTE 名集合」，遇自引用（CTE 引用自身）显式标记 unsupported/跳过该引用（不无限递归）；`WITH RECURSIVE` 自引用整体显式 unsupported。
- [ ] 失败路径显式：CTE 输出列引用未注册底层表 / 通配符输出 → 该列 unresolved（沿用既有 unresolved 语义，不伪造、不静默）。

Exit Criteria:

- [ ] 含单层 CTE 的 sourceSql（如 `WITH cte AS (SELECT t.x, t.y FROM src t) SELECT c.x FROM cte c`）解析出 `output.x → src.x(direct)` resolved 候选，目录匹配后写边指向 `src`（单测断言）。
- [ ] CTE 内聚合列（`SUM(t.x) AS s`）经引用穿透产出 `aggregated` 候选（单测断言）。
- [ ] **端到端验证**：BizModel `extractColumnLineageFromSql` 对含 CTE 的 sql 表产出 `extractedEdgeCount > 0` 且边 `sourceTableId` 指向底层源表（AutoTest，见 #22）。
- [ ] **接线验证**：CTE 穿透路径经既有 `extract`→BizModel upsert 写边链路真实生效（解析器产 resolved 候选、BizModel 写边，非仅解析到内存丢弃，见 #23）。
- [ ] **无静默跳过**：CTE 自引用 / 通配符 / 未匹配列进入 `unresolved` 而非被伪造为 resolved（#24）。
- [ ] 既有直查源表 + 多表歧义 + 裸 `*` 用例零回归（既有单测通过）。
- [ ] design `01-architecture-baseline.md` §2.6.1 / §4.2.1 标注 CTE 穿透已落地；roadmap P2-5++ 同步。
- [ ] `ai-dev/logs/2026/07-17.md` 追加条目。

### Phase 2 - 派生表（subquery）列穿透解析

Status: planned
Targets: `.../lineage/SqlColumnLineageExtractor.java`（`SqlSubqueryTableSource` 派生表解析 + 注册）

- Item Types: `Fix`、`Proof`

- [ ] `buildAliasTableMap` 遇 `SqlSubqueryTableSource`（派生表 `FROM (...) alias`）时：递归解析其输出列 → 底层源列，注册 `alias(lower) → {输出列 → [(源表,源列,transformType)]}`（与 CTE 注册同机制）。
- [ ] `attribute` 命中派生表 alias 时穿透到底层源列产出 resolved 候选。
- [ ] 嵌套（派生表内再含 CTE / 派生表）经统一递归 + 环路守卫解析。
- [ ] `tableCount` 边界：新增派生表源时正确维护 `tableCount`（用于无别名单表归属，extractor :281）。混合查询（真表 + 派生表）下无限定符列的归属不得回归——须有单测覆盖「FROM 真表 + 派生表」无限定符列场景。
- [ ] 失败路径显式：派生表通配符输出 / 未匹配列 → unresolved。

Exit Criteria:

- [ ] 含派生表的 sourceSql（如 `SELECT d.x FROM (SELECT t.x FROM src t) d`）解析出 `output.x → src.x(direct)` resolved 候选（单测断言）。
- [ ] 嵌套 CTE+派生表组合解析正确（单测断言：穿透到最底层源表）。
- [ ] 混合（真表 + 派生表）无限定符列归属不回归（单测断言）。
- [ ] **端到端验证**：BizModel 对含派生表的 sql 表产出边指向底层源表（AutoTest）。
- [ ] **接线验证**：派生表穿透经既有解析→BizModel upsert 链路真实写边（#23）。
- [ ] **无静默跳过**：派生表通配符/未匹配列进 unresolved（#24）。
- [ ] 既有用例零回归。
- [ ] design §2.6.1 / §4.2.1 标注派生表穿透已落地；roadmap P2-5++ 同步。
- [ ] `ai-dev/logs/2026/07-17.md` 追加条目。

## Closure Gates

- [ ] CTE 与派生表列穿透均有解析器级单测 + BizModel 端到端 AutoTest。
- [ ] 自引用 CTE / 通配符 / 未匹配列显式 unresolved（无伪造 resolved、无静默丢弃）。
- [ ] 既有直查/多表/裸 `*` 用例零回归。
- [ ] 无 ORM / 持久化结构变更（确认仅产出更多 resolved 候选经既有 upsert 写边）。
- [ ] design §2.6.1 + §4.2.1 + roadmap P2-5++ 已同步到 live baseline。
- [ ] `./mvnw compile -pl nop-metadata -am` 通过。
- [ ] `./mvnw test -pl nop-metadata -am` 通过。
- [ ] checkstyle / 代码规范检查通过。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（Minimum Rules #26）。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0。
- [ ] 独立子 agent closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：CTE/派生表穿透路径在 `extractColumnLineageFromSql` 端到端被真实调用并产出指向底层源表的边（非空壳/非仍全部 unresolved）。

## Deferred But Adjudicated

### `WITH RECURSIVE` 自引用递归 CTE

- Classification: `optimization candidate`
- Why Not Blocking Closure: 自引用 CTE 需基线条件 + 展开深度语义裁定，纯句法无法确定终止；非自引用 CTE/派生表穿透已覆盖绝大多数视图血缘场景。
- Successor Required: `no`

### 聚合内部表达式精确语义展开

- Classification: `optimization candidate`
- Why Not Blocking Closure: 沿用 0228-2 裁定——记 aggregated transformType 已满足列级归属判定；内部表达式拆解为独立复杂度。
- Successor Required: `no`

## Non-Blocking Follow-ups

- transformExpr AST 标准化（首版记原文或留空，沿用 0228-2）。
- 血缘可视化前端图渲染 JSON（沿用 0420-2 follow-up）。
- 多 schema 列级血缘（归入多 schema plan）。

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Audit Session:
- Evidence:

Follow-up:

- no remaining plan-owned work（closure 时确认或改写）
