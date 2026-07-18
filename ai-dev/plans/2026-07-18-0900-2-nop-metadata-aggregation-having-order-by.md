# 02 聚合查询 having/排序增强

> Plan Status: active
> Last Reviewed: 2026-07-18
> Mission: nop-metadata
> Work Item: queryAggregation 增加 having（聚合后过滤）+ orderBy（聚合结果排序）
> Source: `ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.4.2（D6 单表聚合 + D8/D9/D10 JOIN 聚合，均仅 filter/limit/offset，无 having/orderBy）；`ai-dev/design/nop-metadata/nop-metadata-roadmap.md`（全 Phase done，本项为 Non-Blocking Follow-up 收口）；plan `2026-07-17-0852-1` / `2026-07-17-1200-1` / `2026-07-17-1500-1` / `2026-07-17-1500-2` Non-Blocking Follow-ups（聚合 having/排序增强）
> Related: `2026-07-16-0800-2`（P4-3 聚合查询基础）、`2026-07-17-0852-1`（entity↔entity JOIN 聚合）、`2026-07-17-1500-2`（跨库 JOIN 聚合内存 GROUP BY）
> Draft Review: R1 独立子 agent 对抗性审查（ses_08d888304ffeDVyloeHexr3iZk）发现 2 Blocker + 6 Major + 5 Minor。关键 Blocker：① `OrderByBean` 类型在全仓不存在（平台实际是 `OrderFieldBean`，字段 `name`+`desc:boolean`+`nullsFirst`，非 `{name, dir:string}`）；② `FilterToSqlTranslator` 的 `validateIdentifier` 会拒绝含括号的 `SUM(col)` 表达式，having 的 name→aggSql 解析无法直接复用现有翻译器。已据 R1 全部修复：orderBy 改用平台 `OrderFieldBean`；having 翻译机制裁定为**扩展 FilterToSqlTranslator 增加字段解析回调**（回调把 measure/dimension name 解析为已校验的 aggSql/groupExpr，绕过 validateIdentifier）；内存路径显式承认需新增 TreeBean 求值器 + 多键比较器；JOIN 3 条同库 SQL builder 方法逐一列出。

## Purpose

把 `queryAggregation` 从仅支持 `filter/limit/offset` 扩展到支持 `having`（对聚合结果过滤，如 `SUM(amount) > 1000`）和 `orderBy`（按 measure/dimension 排序），使三条执行路径（entity 原生 SQL / external-sql 原生 SQL / 跨库内存 GROUP BY）一致支持。在本 plan + daily log 记录落地覆盖证据，不再回写已完成历史 plan（Rule #20）。

## Current Baseline

- **queryAggregation 签名（§4.4.2 行 1066）**：`queryAggregation(metaTableId, measures, dimensions, filter?, joinId?, limit?, offset?, context)`，落点 `NopMetaTableBizModel.java:523`。8 个参数。无 `having` 参数、无 `orderBy` 参数。`TestNopMetaAggregationBizModel` 有 10+ 处 8-arg 直调（行 73/95/115/138/185/225/292/346/515/581/688/740/829/969/1145/1338 等），签名扩展须同步更新（R1 M6）。
- **entity 聚合 SQL 生成（`MetaAggregationExecutor.executeEntityAggregation:290`）**：`SELECT <维度>, <agg>(<列>) FROM <物理表> WHERE ? GROUP BY <维度> LIMIT ? OFFSET ?`。无 HAVING、无 ORDER BY。
- **external/sql 聚合 SQL 生成（`MetaAggregationExecutor.buildExternalAggregationSql:2058`）**：同结构（`SELECT ... FROM ... WHERE ... GROUP BY ... LIMIT/OFFSET`）。无 HAVING、无 ORDER BY。
- **JOIN 聚合 3 条同库 SQL builder（R1 M1 修复，逐一列出）**：
  - entity↔entity：`executeEntityEntityJoinAggregation`（约 `:420-520`）内联拼 SQL StringBuilder，Spec 类型 `JoinMeasureSpec{alias, aggSql, qualifiedAggCol}` / `JoinDimensionSpec{alias, qualifiedCol, column, dimensionType, granularity}`。aggSql 已是 `SUM(l.col)`/`SUM(r.col)` 形式（含端点限定前缀）。
  - external↔external：`buildExternalExternalJoinSql`（约 `:862-903`）抽出的 builder，返回 StringBuilder。Spec 类型同上。
  - 混合同库（entity↔external/sql）：`buildMixedSameDbJoinSql`（约 `:814-858`）抽出的 builder。Spec 类型同上。
  - 三者参数绑定构造各自独立。
- **跨库内存聚合（`executeCrossDbJoinAggregation`，§4.4.2 D10 行 1132）**：`MetaJoinExecutor.executeJoin` 取合并行 → `memoryGroupBy`（`:1039-1080`）产出 `List<Map<String,Object>>`（key 为 `safeAlias(name)` = **大写化** alias，如 `TOTAL`，值 Long/Double/Integer/BigDecimal/null）→ 直接返回。无 having 过滤、无排序。跨库路径当前无任何 TreeBean 内存求值器（R1 M2）。
- **既有 filter 翻译器（`FilterToSqlTranslator.java`）**：递归翻译 TreeBean 为 SQL WHERE。**核心限制（R1 B2）**：`requireField`（`:236-244`）对 name 做 `validateIdentifier`（白名单 `^[A-Za-z_][A-Za-z0-9_]*$`），**任何含括号/点号/表达式的串（如 `SUM(AMOUNT)`）会被拒绝抛 `ERR_FILTER_INVALID_IDENTIFIER`**。having 的 name 需解析为 `SUM(col)` 形式的 aggSql，无法直接复用现有 `translate(TreeBean)`。
- **平台 orderBy 类型（R1 B1 修复）**：平台标准类型为 `io.nop.api.core.beans.query.OrderFieldBean`（`nop-api-core/.../beans/query/OrderFieldBean.java:25`），字段 `{owner, name, desc(boolean), nullsFirst(Boolean)}`——**不是** `{name, dir:string}`。`OrderByBean` 在全仓**不存在**（grep 0 命中）。平台所有 findPage/QueryBean.orderBy/GraphQL 均用 `List<OrderFieldBean>`。本 plan 的 orderBy 参数须用 `List<OrderFieldBean>`。
- **MeasureSpec / DimensionSpec（R1 m1 修复）**：`private static final class`（非 package-private），位于 `MetaAggregationExecutor` 内。`MeasureSpec{alias, aggSql}` **不保留原始 measureName**（safeAlias 大写化不可逆）。`loadMeasures(table, names, ctx)` 只加载 `names` 列表里的 measure（R1 M3）。
- **Non-Blocking Follow-ups 引用**：plan 0852-1/1200-1/1500-1/1500-2 的「聚合 having/排序增强」项。
- **roadmap 全 Phase done**（plan 2055-1 closure）：本项为 Optimization Candidate，不阻塞当前 supported baseline。

## Goals

- **having 可用**：`queryAggregation` 新增 `having?` 参数（TreeBean），对聚合结果过滤。having 的 `name` 引用**用户选定的** measure/dimension 名 → 解析为 `aggSql`/`groupExpr` → 翻译为 SQL `HAVING`（原生路径）或内存过滤（跨库路径）。
- **orderBy 可用**：`queryAggregation` 新增 `orderBy?` 参数（`List<OrderFieldBean>`），按 measure/dimension 排序。翻译为 SQL `ORDER BY`（原生路径）或内存排序（跨库路径）。
- **三条路径一致**：entity 原生 SQL / external-sql 原生 SQL / 跨库内存 GROUP BY 三条路径一致支持 having + orderBy。
- **不破坏 FilterToSqlTranslator 既有契约**：扩展而非重写，既有 `translate(TreeBean)` 行为不变。

## Non-Goals

- **expression 型 Measure 执行**：不做。`expression` 型 Measure 仍显式不支持（§4.4.2 行 1081）。
- **多列 having 算术表达式**（如 `HAVING SUM(a) - SUM(b) > 100`）：首版 having 叶子条件引用单个 measure/dimension。
- **having/orderBy 引用未选定的 measure/dimension**：首版 having/orderBy 的 name 必须在用户的 `measures=[...]` + `dimensions=[...]` 选定集合内（R1 M3 裁定）。
- **group 总数 total**：不计算 group 总数。
- **分页游标 / keyset pagination**：不做。

## Scope

### In Scope

- `queryAggregation` / `executeAggregation` 新增 `having?`（TreeBean）+ `orderBy?`（`List<OrderFieldBean>`）参数。
- **扩展 `FilterToSqlTranslator`**：新增 `translate(TreeBean filter, Function<String,String> fieldResolver)` 重载（R1 B2 修复）。fieldResolver 非空时，叶子条件的 name 经 fieldResolver 解析为 SQL 表达式（已上游校验），**跳过 validateIdentifier**（因 aggSql 含括号会触发白名单失败）；fieldResolver 为空时维持既有 `requireField + validateIdentifier` 行为。
- entity 原生 SQL 路径：SQL 增加 `HAVING` + `ORDER BY` 子句。
- external/sql 原生 SQL 路径：SQL 增加 `HAVING` + `ORDER BY` 子句。
- JOIN 聚合 3 条同库路径（entity↔entity / external↔external / 混合）：SQL 增加 `HAVING` + `ORDER BY` 子句（逐一修改）。
- 跨库内存聚合路径：**新增内存 TreeBean 求值器**（having）+ **内存多键比较器**（orderBy）（R1 M2 修复，显式承认新组件）。
- having/orderBy 中 name → aggSql/groupExpr 解析（复用 MeasureSpec/DimensionSpec，但须从 `loadMeasures` 返回的 `List<NopMetaTableMeasure>` 阶段就建 name→aggSql 反查表，因 MeasureSpec 已丢 name）。
- design §4.4.2 文档更新。

### Out Of Scope

- expression 型 Measure 执行（单独 plan）。
- 多列 having 算术表达式。
- having/orderBy 引用未选定 measure/dimension。
- group 总数（total）。

## Execution Plan

### Phase 1 - having/orderBy 参数建模 + 翻译机制裁定

Status: planned
Targets: `ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.4.2

- Item Types: `Decision`

**设计决策（已落地，非建议）**：

- [ ] **D1 — having 参数建模 + 翻译机制（Decision，已裁定）**：
  - **结构**：having 为 TreeBean（与 filter 同结构 `{type, name?, value?, children?}`），`name` 引用用户选定的 measure/dimension 名。
  - **翻译机制（R1 B2 修复）**：扩展 `FilterToSqlTranslator` 新增 `translate(TreeBean filter, Function<String,String> fieldResolver)` 重载。having 调用时传入 fieldResolver = `name -> {查 measure name → aggSql; 查 dimension name → groupExpr; 未命中 → 抛 ErrorCode}`。fieldResolver 返回的表达式（如 `SUM(AMOUNT)`）**不经 validateIdentifier**（该表达式中的列名已在 measure/dimension 加载时经白名单校验）；递归 and/or/not 逻辑完全复用既有 `joinChildren`/`translateNot`。
  - **name 集合（R1 M3 修复）**：having 的 name 必须在用户的 `measures=[...]` + `dimensions=[...]` 选定集合内。引用未选定 name → 显式失败（`ERR_AGGR_HAVING_UNKNOWN_NAME`）。name→aggSql 反查表从 `loadMeasures`/`loadDimensions` 返回的原始实体列表（仍持 name）阶段构建，不从 MeasureSpec（已丢 name）构建。
  - 裁定写入 design §4.4.2。
- [ ] **D2 — orderBy 参数建模（Decision，已裁定，R1 B1 修复）**：orderBy 为 `List<OrderFieldBean>`（平台标准类型 `io.nop.api.core.beans.query.OrderFieldBean`，字段 `{owner, name, desc(boolean), nullsFirst(Boolean)}`）。`name` 引用用户选定的 measure/dimension 名 → 解析为 aggSql/groupExpr（与 D1 同反查表）。`desc=true` → DESC，`desc=false` → ASC。`nullsFirst` → SQL `NULLS FIRST/LAST`（原生路径）/ Comparator null 策略（内存路径）。裁定写入 design §4.4.2。
- [ ] **D3 — 内存路径 having/orderBy 语义（Decision，已裁定）**：
  - **having**：内存 GROUP BY 产出 `List<Map<String,Object>>`（key 为大写化 alias）→ **新增内存 TreeBean 求值器**（`MemoryFilterEvaluator`），递归 and/or/not，叶子条件按 name→大写化 alias 取聚合值做比较。op 集合与 SQL 路径对齐（eq/ne/gt/ge/lt/le/like/in/between/is-null/not-null）。**类型强转（R1 M2）**：聚合值可能 Long/Double/BigDecimal，用户字面量可能 Integer/String → 比较前统一转 `Comparable`（Number→BigDecimal 统一）。大小写匹配（R1 m2）：name 比对 case-insensitive（与 `safeAlias` 大写化对齐）。
  - **orderBy**：内存 GROUP BY 产出 group → **新增内存多键比较器**（按 `OrderFieldBean` 列表逐字段排序，desc/nullsFirst 生效）。类型强转同 having。
  - **顺序**：先 orderBy → 再 limit/offset（与 SQL `ORDER BY ... LIMIT` 一致）。orderBy 缺席时内存路径无序（沿用 D5 既有裁定），SQL 路径无 ORDER BY 子句。
  - **executeJoin 不感知 having/orderBy**（R1 m4）：having/orderBy 必须在 memoryGroupBy 之后应用，`MetaJoinExecutor.executeJoin` 签名不变。
  - 裁定写入 design §4.4.2 D10 扩展。

Exit Criteria:

- [ ] D1/D2/D3 裁定已写入 design §4.4.2（having TreeBean + fieldResolver 翻译机制 + OrderFieldBean + 内存求值器/比较器语义）。
- [ ] orderBy 用 `List<OrderFieldBean>`（非虚构的 OrderByBean）。
- [ ] having 翻译机制明确（扩展 FilterToSqlTranslator + fieldResolver 回调，非含糊的"复用"）。
- [ ] name 集合限定裁定明确（仅用户选定集合内）。
- [ ] 内存路径新组件（MemoryFilterEvaluator + 多键比较器）显式承认。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 原生 SQL 路径实现（单表 + 3 条 JOIN 同库）

Status: planned
Targets: `MetaAggregationExecutor.executeEntityAggregation` / `buildExternalAggregationSql` / `executeEntityEntityJoinAggregation`（内联）/ `buildExternalExternalJoinSql` / `buildMixedSameDbJoinSql`；`FilterToSqlTranslator.java`（扩展）；`NopMetaTableBizModel.queryAggregation`（签名扩展）

- Item Types: `Fix`

- [ ] **扩展 FilterToSqlTranslator（Fix，R1 B2 修复）**：新增 `translate(TreeBean filter, Function<String,String> fieldResolver)` 重载。fieldResolver 非空时叶子条件 name 经回调解析为 SQL 表达式（跳过 validateIdentifier，因表达式已上游校验）；fieldResolver 为空时维持既有行为。递归 and/or/not 完全复用。既有 `translate(TreeBean)` 行为不变（委托 `translate(filter, null)`）。
- [ ] **扩展 queryAggregation 签名（Fix，R1 M6 修复）**：新增 `@Optional @Name("having") TreeBean having` + `@Optional @Name("orderBy") List<OrderFieldBean> orderBy` 参数，**固定在 offset 之后**（参数顺序：`..., limit, offset, having, orderBy, context`）。同步更新 `TestNopMetaAggregationBizModel` 全部 10+ 处 8-arg 直调站点（行 73/95/115/138/185/225/292/346/515/581/688/740/829/969/1145/1338 等）为新签名（having=null, orderBy=null 保持既有行为）。
- [ ] **实现 name→aggSql/groupExpr 反查表（Fix，R1 M3 修复）**：在 `executeEntityAggregation` / `executeExternalAggregation` / JOIN 聚合路径中，从 `loadMeasures`/`loadDimensions` 返回的原始实体列表（仍持 measureName/dimensionName）构建 `Map<String, String> nameToAggSql`（measure name → aggSql，dimension name → groupExpr/qualifiedCol）。反查表传入 having 的 fieldResolver + orderBy 的 name 解析。未知 name → `ERR_AGGR_HAVING_UNKNOWN_NAME` / `ERR_AGGR_ORDER_BY_UNKNOWN_NAME` 显式失败。
- [ ] **entity 聚合 SQL 增加 HAVING + ORDER BY（Fix）**：`executeEntityAggregation` 的 SQL 生成中，GROUP BY 后追加 `HAVING <translated>`（经 `translate(having, fieldResolver)` 翻译）+ `ORDER BY <orderByExprs>`（name→aggSql/groupExpr + ASC/DESC）。参数绑定顺序：filter 值 → having 值 → limit/offset。
- [ ] **external/sql 聚合 SQL 增加 HAVING + ORDER BY（Fix）**：`buildExternalAggregationSql` 同上追加。
- [ ] **JOIN entity↔entity 聚合 SQL 增加 HAVING + ORDER BY（Fix，R1 M1）**：`executeEntityEntityJoinAggregation` 内联 SQL 追加 HAVING + ORDER BY。having/orderBy 的 name → `JoinMeasureSpec.qualifiedAggCol`（已含 `l.`/`r.` 前缀）/ `JoinDimensionSpec.qualifiedCol`。
- [ ] **JOIN external↔external 聚合 SQL 增加 HAVING + ORDER BY（Fix，R1 M1）**：`buildExternalExternalJoinSql` 同上追加。
- [ ] **JOIN 混合同库聚合 SQL 增加 HAVING + ORDER BY（Fix，R1 M1）**：`buildMixedSameDbJoinSql` 同上追加。

Exit Criteria:

- [ ] FilterToSqlTranslator 新增 `translate(filter, fieldResolver)` 重载；既有 `translate(filter)` 行为不变。
- [ ] queryAggregation 签名扩展（having + orderBy 在 offset 之后）；`TestNopMetaAggregationBizModel` 全部既有调用站点更新到新签名并编译通过。
- [ ] name→aggSql/groupExpr 反查表从原始实体列表构建（非从 MeasureSpec）；未知 name 显式失败。
- [ ] entity / external-sql / JOIN 3 条同库路径（entity↔entity / external↔external / 混合）均生成 `HAVING` + `ORDER BY` 子句。
- [ ] 参数绑定顺序正确（filter → having → limit/offset）。
- [ ] **无静默跳过（#24）**：未知 measure/dimension name in having/orderBy → 显式失败（不静默忽略该条件）。
- [ ] 既有无 having/orderBy 调用零行为变化（having=null + orderBy=null → SQL 无 HAVING/ORDER BY，与现状一致）。
- [ ] design §4.4.2 已更新（having/orderBy 子句生成 + fieldResolver 翻译机制）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 跨库内存路径实现 + 端到端验证

Status: planned
Targets: `MetaAggregationExecutor.executeCrossDbJoinAggregation`；新增 `MemoryFilterEvaluator`（内存 TreeBean 求值器）；新增内存多键比较器逻辑；`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestNopMetaAggregationBizModel.java`

- Item Types: `Fix | Proof`

- [ ] **新增 MemoryFilterEvaluator（Fix，R1 M2 修复）**：内存 TreeBean 求值器，输入 `TreeBean having` + `Map<String,Object> row`（聚合结果行，key 为大写化 alias）→ 递归 and/or/not，叶子条件按 name（case-insensitive 匹配大写化 alias）取聚合值，op 集合 eq/ne/gt/ge/lt/le/like/in/between/is-null/not-null，比较前 Number→BigDecimal 统一类型强转。未知 op → 显式失败。
- [ ] **新增内存多键比较器（Fix，R1 M2 修复）**：按 `List<OrderFieldBean>` 逐字段排序（name→大写化 alias 取值，desc 生效，nullsFirst 生效），类型强转同 MemoryFilterEvaluator。
- [ ] **跨库内存 having 过滤（Fix）**：`executeCrossDbJoinAggregation` 内存 GROUP BY 产出 group → 经 MemoryFilterEvaluator 过滤（having=null 时不过滤）。未知 name → 显式失败。
- [ ] **跨库内存 orderBy 排序（Fix）**：内存 GROUP BY 产出 group → 经多键比较器排序 → limit/offset 截断（D3 顺序：orderBy → limit/offset）。orderBy=null 时无序（沿用 D5）。
- [ ] **新增 MemoryFilterEvaluator 单元测试（Fix，#25）**：覆盖 op 集合 + 类型强转（Long vs Integer vs BigDecimal）+ 嵌套 and/or/not + case-insensitive name 匹配。
- [ ] **端到端测试：单表 entity 聚合 + having + orderBy（Proof）**：`queryAggregation(..., having, orderBy)`，断言 having 过滤生效 + orderBy 排序正确。
- [ ] **端到端测试：external/sql 聚合 + having + orderBy（Proof）**：同上，external/sql 路径。
- [ ] **端到端测试：JOIN entity↔entity 同库 + having + orderBy（Proof，R1 M5）**。
- [ ] **端到端测试：JOIN external↔external 同库 + having + orderBy（Proof，R1 M5）**。
- [ ] **端到端测试：JOIN 混合同库 + having + orderBy（Proof，R1 M5）**。
- [ ] **端到端测试：跨库 JOIN 聚合 + having + orderBy（Proof）**：内存路径，验证 MemoryFilterEvaluator + 多键比较器接线。
- [ ] **向后兼容测试（Proof）**：无 having/orderBy 的既有 queryAggregation 调用行为零变化（既有测试全绿）。
- [ ] **失败路径测试（Proof）**：having/orderBy 引用未选定 measure/dimension name → 显式失败。

Exit Criteria:

- [ ] MemoryFilterEvaluator + 多键比较器实现完成，有独立单元测试覆盖 op 集合 + 类型强转 + 嵌套 + case-insensitive。
- [ ] 跨库内存路径 having/orderBy 实现完成（过滤 + 排序 + limit/offset 顺序正确）。
- [ ] **端到端验证（#22）**：从 GraphQL `queryAggregation(metaTableId, ..., having, orderBy)` 入口到返回过滤+排序后的 items 完整路径已验证。**JOIN 同库 3 条路径（entity↔entity / external↔external / 混合）各至少 1 条端到端测试**（R1 M5 修复），+ 跨库 1 条 + 单表 entity 1 条 + external/sql 1 条。
- [ ] **接线验证（#23）**：having/orderBy 参数在运行时确被 SQL 生成方法（原生路径）和 MemoryFilterEvaluator/比较器（内存路径）消费（断言 having 过滤生效 + orderBy 排序正确证明路径连通）。
- [ ] 向后兼容用例通过（无 having/orderBy → 零变化）。
- [ ] 失败路径用例通过（未知 name → 显式失败）。
- [ ] **新增组件有测试（#25）**：MemoryFilterEvaluator 有独立单元测试（非仅端到端间接覆盖）。
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -am` 全绿（既有测试无回归）。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] having + orderBy 在 queryAggregation 可用（三条路径一致）。
- [ ] orderBy 用平台 `OrderFieldBean`（非虚构类型）。
- [ ] FilterToSqlTranslator 扩展不破坏既有 `translate(TreeBean)` 行为。
- [ ] MemoryFilterEvaluator + 多键比较器有独立单元测试。
- [ ] 向后兼容（无 having/orderBy 调用零行为变化）。
- [ ] 失败路径显式化（未知 name 不静默跳过）。
- [ ] JOIN 同库 3 条路径（entity↔entity / external↔external / 混合）均有端到端测试覆盖。
- [ ] 在本 plan + daily log 记录落地覆盖证据（0852-1/1200-1/1500-1/1500-2 的 Non-Blocking Follow-up 项被本 plan 落地覆盖）；**不回写已完成历史 plan**（Rule #20）。
- [ ] design §4.4.2 已更新（having/orderBy 子句生成 + fieldResolver + 内存求值器/比较器语义）。
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift。
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：closure audit 已验证 having/orderBy 在三条路径的运行时调用链连通（SQL 路径生成 HAVING/ORDER BY 子句；内存路径 MemoryFilterEvaluator/比较器被真实调用），非空方法体/非 stub。
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -am` 全绿。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。

## Deferred But Adjudicated

### expression 型 Measure 执行

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: expression 型 Measure 的表达式语言（EQL? SQL 片段? 安全沙箱?）未定义，需 design-first plan。当前 having/orderBy 引用 field-based Measure（entityFieldId 非空）已覆盖绝大多数聚合场景。expression 型 Measure 仍显式不支持（§4.4.2 行 1081）。
- Successor Required: yes
- Successor Path: 后续 design-first → plan（expression 型 Measure 表达式语言设计 + 执行）

### 多列 having 算术表达式

- Classification: `optimization candidate`
- Why Not Blocking Closure: 首版 having 叶子条件引用单个 measure/dimension，不支持 `HAVING SUM(a) - SUM(b) > 100` 跨 measure 算术。跨 measure 表达式随 expression 型 Measure 一并 deferred。
- Successor Required: no
- Successor Path: none（随 expression 型 Measure successor 一并）

## Non-Blocking Follow-ups

- group 总数（total count of groups）——首版仅返回 items + limit/offset。
- 分页游标 / keyset pagination——首版 limit/offset 分页。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
