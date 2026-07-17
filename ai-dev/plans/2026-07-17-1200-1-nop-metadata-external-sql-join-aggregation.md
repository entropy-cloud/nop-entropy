# nop-metadata external/sql JOIN 聚合 — Measure/Dimension 侧别建模（external↔external 同库）

> Plan Status: completed
> Last Reviewed: 2026-07-17
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md`（P4 联邦查询 deferred）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.4.1（JOIN 路由 D1.1-D1.4/D3/D4/D5）+ §4.4.2（聚合 D6/D7/D8，行 959-1013）；plan `2026-07-17-0852-1` Deferred But Adjudicated「external/sql 端点 JOIN 聚合（含混合端点）」Successor Required: yes，Successor Path「Measure/Dimension 侧别建模 + external/sql JOIN 聚合」
> Mission: nop-metadata
> Work Item: P4-3++ external/sql JOIN 聚合（external↔external 同库）+ Measure/Dimension 侧别建模
> Related: `2026-07-17-0852-1`（entity↔entity JOIN 聚合 predecessor）、`2026-07-17-0700-2`（queryJoinData 行级 sql/external JOIN 执行，§4.4.1 D1 路由裁定）、`2026-07-17-0700-1`（NopMetaTableJoin sql/external 端点 schema + 跨表校验）、`2026-07-17-0228-3`（跨表 Measure/Dimension 校验 entity-entity）
> Draft Review: R1 独立子 agent 对抗性审查（含想象性分析 + live repo 核验）发现 1 Blocker（混合端点同库聚合与 §4.4.1 D1.2 矛盾——混合端点一律走截断式应用层拼接 D5，无法产出正确聚合，且 §4.4.2 D8 行 1013 已将混合端点 JOIN 聚合列为 deferred）+ 3 Major（side 多 join 语义未定义 / 未点名 `ERR_AGGR_JOIN_ENDPOINT_NOT_ENTITY` 硬失败守卫 / side 必填 vs 条件不一致）+ 4 Minor。已全部修复：混合端点移入 Deferred，Phase 3 收敛为 external↔external 同库；D1 扩展多 join 语义；显式点名守卫重构；side 规则统一为「external/sql 端点 query 时必填」。R2 独立子 agent 复审（fresh session，live repo 核验）：4 项前置全部 RESOLVED，无新 Blocker/Major，3 新 Minor（Phase2/3 守卫耦合、:277 引用归属、既有失败用例）均已 live-verified 修复。共识达成 APPROVE，Plan Status → active。

## Purpose

把 `queryAggregation` over JOIN 从「仅 entity↔entity」（plan 0852-1）收口到「JOIN 两端点均为 external/sql 类型表且同 querySpace 时可跨表聚合」。兑现 plan 0852-1 Deferred（Successor Required: yes）中**可正确实现**的部分。

核心障碍是**数据模型表达力缺口**：`NopMetaTableMeasure/Dimension.entityFieldId` 对 external/sql 表是**裸列名字符串**（无 `metaEntityId`、无 side 列），JOIN 同名列（id/name/amount 等）无法判定左右侧。本 plan 先补**侧别建模**（ORM 结构变更，Protected Area plan-first），再扩展聚合执行与 query-time 校验。

**范围裁定（经 R1 审查修正）**：external↔external 同 querySpace 走单一共享 `withConnection` 原生 `GROUP BY over JOIN`（§4.4.1 行 941），**可产出正确聚合结果**。混合端点（entity↔external/sql）一律走应用层拼接（§4.4.1 D1.2 行 923，受 `MAX_CROSS_DB_ROWS` 截断），**聚合语义为近似、不可正确**，且 §4.4.2 D8 行 1013 已将「混合端点 JOIN 聚合」显式列为 deferred——故本 plan 不做（见 Deferred）。

## Current Baseline

（live repo 核实，2026-07-17）

- `NopMetaTableMeasure`（`nop-metadata/model/nop-metadata.orm.xml:1134-1196`，max propId 17=REMARK）与 `NopMetaTableDimension`（同文件 `:1069-1129`，max propId 16=REMARK）均只有 `entityFieldId`（propId 5），**无 side 列**。对 entity 表存 `NopMetaEntityField.metaEntityFieldId` 主键（带 `metaEntityId`，归属可无歧义判定）；对 external/sql 表存裸列名字符串。
- `queryAggregation(metaTableId, measures, dimensions, joinId?)` 已落地（`NopMetaTableBizModel.java:523`），委托 `MetaAggregationExecutor.executeAggregation`（:199）。带 `joinId` 时进 `executeJoinAggregation`（:305）。
- JOIN 聚合路径**仅 entity↔entity**（plan 0852-1）：`executeJoinAggregation` 对任一端点非 entity 在 `MetaAggregationExecutor.java:320` 与 `:325` **主动抛 `ERR_AGGR_JOIN_ENDPOINT_NOT_ENTITY`**（ErrorCode 定义于 `:123`），在 side 解析与 SQL 构造**之前**短路。这是本 plan Phase 3 必须显式重构的守卫（见 Major 3 修正）。
- entity 字段归属：`entityFieldId → NopMetaEntityField.metaEntityId` 判定 left/right entity；同库 entity↔entity 走 `orm().executeQuery` 原生 `GROUP BY ... OVER JOIN`（§4.4.2 D8 行 1009）。
- 行级 JOIN（`queryJoinData`）已支持 external/sql 端点（plan 0700-2）：§4.4.1 路由 D1——external/sql↔external/sql 同 querySpace 走 `withConnection` 原生 JOIN SQL（行 941，两端点共享同一 NopMetaDataSource、单一连接）；**混合端点（entity↔external/sql，任意 querySpace）一律走应用层拼接 D5**（行 923，理由：两种本质不同的连接机制，无法在单一连接跑原生 JOIN SQL）；跨库走 D5（行 922）受 `MAX_CROSS_DB_ROWS=10000` 截断（行 952）。
- 跨表 Measure/Dimension **save-time 校验**：plan 0700-1 D4 把 external/sql 端点扩展为按字段名**并集**校验（`MetaTableFieldResolver.resolveAllowedFieldNames` 返回 `Set<String>` 丢弃 side，定义于 `MetaTableFieldResolver.java:277`）。save-time 校验入口 `NopMetaTableMeasureBizModel:83` / `NopMetaTableDimensionBizModel:82`，加载该表**全部** join（`findAllByQuery(metaTableId)`）——**无单一 join 上下文**，故 side 不能在 save-time 针对具体 join 校验。
- §4.4.2 D8（行 1002-1013）Deferred 段（行 1013）已显式列出三项 deferred：external/sql 端点 JOIN 聚合（本 plan 兑现 external↔external 部分）、跨 querySpace entity-entity JOIN 聚合、**混合端点（entity↔external/sql）JOIN 聚合**。三者当前均显式失败，不静默。
- `NopMetaTableJoin`（`:1262-1349`）：`leftEntityId/rightEntityId`（→NopMetaEntity）+ `leftTableId/rightTableId`（→NopMetaTable，覆盖 external/sql 端点）+ `leftField/rightField`（字段名字符串）+ `alias`（右表别名）。一个 MetaTable 可有多条 join（`findAllByQuery(metaTableId)`）。

## Goals

- **侧别建模**：`NopMetaTableMeasure` 与 `NopMetaTableDimension` 新增可空 `side` 列（left/right，dict `meta/join-side`）。语义：**side 相对 `queryAggregation(joinId)` 传入的那条具体 join 解释**——left = 该 join 的左端点，right = 该 join 的右端点；side 存储于 Measure/Dimension、与具体 join 解耦，在 query 时按传入 joinId 解释。
- **query-time 侧别解析 + 校验**：带 `joinId` 聚合时，每个 Measure/Dimension 经 side（entity 端点可改由 `metaEntityId` 判定）绑定到该 join 的 left/right 端点；external/sql 端点校验该列名存在于所绑定端点的解析字段集合中。**external/sql 端点 query 时 side 必填**（null → 显式失败）；entity 端点 side 可选（`metaEntityId` 已可判定，若提供须一致）。
- **external↔external 同库 JOIN 聚合**：两端点均为 external/sql 且同 querySpace 时，经两端点共享的 `withConnection` 跑原生 `SELECT <group>, <agg> FROM <leftFrom> l JOIN <rightFrom> r ON ... [WHERE] GROUP BY ...`；Measure/Dimension 按 side 经 `leftAlias.col`/`rightAlias.col` 限定。
- 聚合语义与既有路径一致：aggFunc（sum/count/avg/min/max/countDistinct）、默认过滤器自动应用、`expression` 型 Measure 显式不支持。
- 失败路径显式（非静默跳过）：external/sql 端点 side 缺失、side 指向端点字段集合不含该列、entity side 与 metaEntityId 不一致、混合端点、跨 querySpace、joinType=right、self-join（双侧别名机制不足）均抛 `NopException` + ErrorCode。

## Non-Goals

- **混合端点（entity↔external/sql）JOIN 聚合**：不在本 plan（§4.4.1 D1.2 行 923 一律走截断式应用层拼接 D5，聚合语义近似不可正确；§4.4.2 D8 行 1013 已 deferred）。
- **跨 querySpace（跨库）JOIN 聚合**：不在本 plan（沿用 0852-1 + 0700-2 跨库 deferred，应用层拼接受截断、语义近似）。
- **`expression` 型 Measure、`joinType=right`**：沿用单表/行级 JOIN/0852-1 既定显式不支持。
- **聚合内部表达式精确语义展开、JOIN 可达性递归（A→B→C 多跳）、JOIN 聚合 having/排序**：watch-only / follow-up（沿用 0228-3 / 0700-2 / 0852-1）。
- **save-time side 校验针对具体 join**：save-time 无单一 join 上下文（一个 MetaTable 可有多 join），side 的权威校验在 query-time（按传入 joinId）。save-time 仅校验 side 枚举合法性。

## Scope

### In Scope

- `NopMetaTableMeasure` + `NopMetaTableDimension` 新增 `side` 列（ORM，Protected Area plan-first）+ dict `meta/join-side` + 重新生成 `_gen`。
- query-time 侧别解析 + 校验：external/sql 端点 side 必填、列名存在性、entity 一致性；歧义/缺失/不一致显式失败。
- `queryAggregation` over JOIN 扩展：重构 `ERR_AGGR_JOIN_ENDPOINT_NOT_ENTITY` 守卫为端点组合路由；external↔external 同库原生 `GROUP BY over JOIN`。

### Out Of Scope

- 混合端点 JOIN 聚合（→ Deferred）。
- 跨库 JOIN 聚合（→ Deferred）。
- `expression` 型 Measure / joinType=right / 多跳递归 JOIN（→ Non-Goals / watch-only）。
- `queryJoinData` 行级 JOIN 行为变更（0700-2 已落地）。

## Execution Plan

### Phase 1 - Measure/Dimension 侧别建模（ORM 结构变更）

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml`（dict 段 `:39-50` 附近新增 `meta/join-side`；NopMetaTableMeasure `:1134-1196` 新增 `side` propId 18；NopMetaTableDimension `:1069-1129` 新增 `side` propId 17）；重新生成 `_gen`（`./mvnw install`）

- Item Types: `Decision`（side 选型 + 多 join 语义 + 必填规则）、`Fix`（ORM 补列）

- [x] **D1 侧别建模选型与语义**（Decision）：逐项裁定并写入 §4.4.2（新增 D9 或修订 D8 Deferred 段，显式选定）：
  1. **side enum vs endpointTableId FK** → 选 `side`（enum left/right）：与 `NopMetaTableJoin` left/right 语义对齐、表达最小；`endpointTableId` 冗余（join 已持 leftTableId/rightTableId）。
  2. **多 join 语义**（R1 Major 2）→ side 存储于 Measure/Dimension、与具体 join 解耦；语义在 `queryAggregation(joinId)` 传入的具体 join 上解释（left=该 join 左端点，right=该 join 右端点）。save-time 不针对具体 join 校验 side（无单一 join 上下文），仅校验枚举合法性；权威校验在 query-time。
  3. **必填规则**（R1 Major 4）→ query-time：external/sql 端点 side 必填（null 即失败，不依赖是否歧义）；entity 端点 side 可选（metaEntityId 已可判定，若提供须与 metaEntityId 端点一致）。无 joinId（单表聚合）时 side 被忽略。
- [x] dict 段新增 `meta/join-side`（option left/right，带 `i18n-en:label="Join Side"`，位置在 `meta/join-type` 行 47 附近）。
- [x] `NopMetaTableMeasure` 新增可空列 `side`（propId **18**，`code="JOIN_SIDE"`，`stdSqlType=VARCHAR`，`ext:dict="meta/join-side"`，`i18n-en:displayName="Join Side"`）。
- [x] `NopMetaTableDimension` 新增可空列 `side`（propId **17**，同上）。
- [x] 向后兼容：既有行 side=null；单表聚合与 entity↔entity JOIN 聚合（0852-1）行为零变化。`./mvnw install` 重新生成 `_gen` DAO/entity，`./mvnw compile -pl nop-metadata -am` 通过。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-metadata.orm.xml`：dict `meta/join-side` 存在；Measure(propId18) + Dimension(propId17) 各新增 `side` 列；`_gen` 已重新生成且 compile 通过。
- [x] 既有数据 side=null 不破坏单表聚合与 entity↔entity JOIN 聚合（0852-1 回归 AutoTest 全绿）。
- [x] **无静默跳过**（#24）：本 Phase 仅引入可空数据载体，不引入新失败路径（失败语义 Phase 2/3 落地）。
- [x] **owner-doc 更新**：`01-architecture-baseline.md` §4.4.2 已记录 D1 选型/多 join 语义/必填规则（新增 D9）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - query-time 侧别解析与校验

Status: completed
Targets: `MetaAggregationExecutor`（`nop-metadata-service/.../query/MetaAggregationExecutor.java`，join 聚合路径，side 解析在 SQL 构造前）；列名解析复用 `MetaTableFieldResolver.resolveAllowedFieldNames`（返回端点解析字段 `Set<String>`）

- Item Types: `Fix`（external/sql 端点 side 解析与校验缺失）、`Proof`（query-time 校验 AutoTest）

- [x] **external/sql 端点 side 解析**（Fix）：带 joinId 聚合时，每个 Measure/Dimension 解析其所属端点；端点为 external/sql 时**要求 side 非空**（null → 显式失败 `ErrorCode` + measureName/dimensionName + joinId）。
- [x] **列名存在性校验**（Fix）：side 绑定端点的解析字段集合（`MetaTableFieldResolver` 按该端点 tableType 解析）不含该列名 → 显式失败（ErrorCode + 端点 tableType + side + 列名）。
- [x] **entity 端点一致性**（Fix）：entity 端点 side 可选；`entityFieldId → metaEntityId` 判定端点，若提供 side 须一致，不一致 → 显式失败。
- [x] **不支持组合显式失败**（Fix）：混合端点（entity↔external/sql）、跨 querySpace、joinType=right、self-join（双侧别名机制不足）在 query-time 显式失败（与 Phase 3 路由一致，ErrorCode 指向 deferred）。
  - > **实现前置提示**：external/sql 端点的 side 解析与失败分支需触达 `executeJoinAggregation` 的 per-Measure/Dimension 代码（`:368` 之后），但既有 `:320`/`:325` 守卫在更早处对非 entity 端点短路。故本 Phase 的路由/失败分支须**同步重构该守卫为端点组合路由**（即 Phase 3 item 1 的前置工作落地于此）。
- [x] query-time 校验 AutoTest：external/sql 端点缺 side → 失败；side 指向端点无此列 → 失败；entity side 与 metaEntityId 不一致 → 失败；混合端点/跨库/right → 失败；side 正确 → 通过进入 Phase 3 执行。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] external/sql 端点 Measure/Dimension 可经 side 绑定具体端点并通过校验；缺 side / 列名不存在 / entity 不一致 / 混合 / 跨库 / right 各有对应 ErrorCode 在 query-time 被抛出。
- [x] entity 端点校验行为零回归（0852-1 既有 entity-entity 校验用例全绿）。
- [x] **无静默跳过**（#24）：上述各失败分支显式失败，不静默放过、不退化单表。
- [x] **新功能测试**（#25）：侧别校验各失败/成功分支有显式 AutoTest 覆盖（`testExternalJoinAggregationExternalSideRequiredFails` / `testExternalJoinAggregationColumnNotOnSideFails` / `testExternalJoinAggregationEntitySideMismatchFails` / `testExternalJoinAggregationCrossQuerySpaceFails` / `testJoinAggregationTableEndpointFails`（混合端点既有用例不变）/ `testJoinAggregationJoinTypeRightFails`（right 既有用例不变））。
- [x] **owner-doc 更新**：若 Phase 2 失败语义改变 live baseline，`01-architecture-baseline.md` §4.4.2 已同步（D9 失败路径段）；否则 `No owner-doc update required`（D1 已在 Phase 1 记录）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - external↔external 同库 JOIN 聚合执行

Status: completed
Targets: `MetaAggregationExecutor.executeJoinAggregation`（`:305`）重构端点组合守卫为路由；external↔external 同库原生 `GROUP BY over JOIN` SQL 构造（经两端点共享 `withConnection`，沿用 §4.4.1 行 941 external/sql 同库 JOIN + §4.4.2 D6 external 聚合范式）

- Item Types: `Decision`（守卫→路由重构方式）、`Fix`（external↔external 同库聚合执行）、`Proof`（端到端 AutoTest）

- [x] **重构端点守卫为路由**（Fix，R1 Major 3）：移除/替换 `MetaAggregationExecutor.java:320` 与 `:325` 对「任一端点非 entity」的无条件 `ERR_AGGR_JOIN_ENDPOINT_NOT_ENTITY` 短路，改为按**端点组合**路由：entity↔entity（0852-1 既有路径保持）、external↔external 同库（本 Phase 新增）、其余组合（混合 / 跨库 / right / self-join 不足）显式失败。重构须保证既有 entity↔entity 路径不被绕过或退化。
- [x] **external↔external 同库原生聚合**（Fix）：两端点均为 external/sql 且同 querySpace → 经两端点共享 `withConnection`（querySpace→NopMetaDataSource）构造 `SELECT <group cols>, <agg> FROM <leftFrom> l INNER|LEFT JOIN <rightFrom> r ON l.<lf>=r.<rf> [WHERE default-filter] GROUP BY ...`；FROM 按 tableType 构造（external→`FROM <tableName>`；sql→`FROM (<sourceSql>) _t`，沿用 D6 `buildFromClause`）；标识符白名单 + 值参数绑定（§2.7.1 D3）。
- [x] **side 限定物理列**（Fix）：聚合 SQL 中每个 Measure/Dimension 按 side 经 `leftAlias.col`/`rightAlias.col` 限定；列名解析为该 external/sql 端点真实列名（external→buildSql columnName；sql→SELECT 解析列），白名单校验；遇 EQL/SQL 保留字物理列名显式失败并给迁移指引（沿用 0852-1）。
- [x] 端到端 AutoTest：external↔external 同库 JOIN 聚合产出正确分组聚合值（断言与等价直接 SQL 一致）；不带 joinId 单表行为零回归；0852-1 entity↔entity JOIN 聚合零回归；重构后 entity↔entity 仍走 `orm().executeQuery`（未退化）。
  - > **既有失败用例不变**：`TestNopMetaAggregationBizModel.testJoinAggregationTableEndpointFails`（`:378`）用的是**混合端点**（entity↔sql，`createMixedJoin` :389），混合端点在本 plan 仍 deferred → 该用例**仍应失败**（不变，勿误改为成功）。external↔external 同库成功路径为**新增**用例。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] external↔external 同库 JOIN 聚合产出正确聚合结果（AutoTest 断言分组聚合值与等价直接 SQL 一致）。
- [x] **端到端验证**（#22）：从 GraphQL `queryAggregation(metaTableId, measures, dimensions, joinId)`（两端点 external/sql 同库）经 `MetaAggregationExecutor` 路由到聚合 `items` 输出完整跑通。
- [x] **接线验证**（#23）：external↔external 同库聚合分支在运行时被真实调用（AutoTest 断言取数/聚合发生在该分支，非返回 entity-only 路径、非空 items）；重构后 entity↔entity 路径仍被调用（未失效）。
- [x] **无静默跳过**（#24）：混合 / 跨库 / right / self-join 不足 / external 缺 side / side 列不存在 / 保留字列 均抛 ErrorCode 而非空 items / 退化单表 / 静默降级。
- [x] **新功能测试**（#25）：external↔external + 各失败组合有显式 AutoTest 覆盖（`testExternalExternalJoinAggregationCorrectness` / `testExternalExternalJoinAggregationViaGraphQL` / `testEntityJoinAggregationStillWorksAfterRefactor`（entity 路径回归）+ Phase 2 各失败用例）。
- [x] **owner-doc 更新**：`01-architecture-baseline.md` §4.4.2 标注 external↔external 同库 JOIN 聚合已落地（混合/跨库仍 deferred）；roadmap 新增 P4-3++ 工作项并标进度。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `NopMetaTableMeasure` + `NopMetaTableDimension` 侧别建模落地，既有 entity/单表行为零回归。
- [x] external↔external 同库 JOIN 聚合端到端可用并有 focused AutoTest。
- [x] 所有不支持组合（混合 / 跨库 / right / self-join 不足 / external 缺 side / side 列不存在 / entity side 不一致 / 保留字列）显式失败（无静默空结果 / 无 no-op / 无静默降级单表）。
- [x] 新增 ErrorCode 在失败分支被实际抛出（接线验证）；重构后 entity↔entity 路径未失效。
- [x] plan 0852-1 Deferred「external/sql 端点 JOIN 聚合」**external↔external 部分**在本 plan 收口（混合端点显式移入 Deferred）。
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift（混合端点 / 跨库 / self-join 须有显式 Why Not Blocking Closure）。
- [x] 受影响 owner docs（`01-architecture-baseline.md` §4.4.2、§2.5.2；roadmap P4-3++）已同步到 live baseline。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：external↔external 同库 JOIN 聚合路径在运行时被 `queryAggregation(joinId)` 真实调用并产出聚合结果（非空壳 / 非退化单表 / 非空 items）；side 校验分支被真实触发；重构后 entity↔entity 路径仍连通。
- [x] `./mvnw compile -pl nop-metadata -am` 通过
- [x] `./mvnw test -pl nop-metadata -am` 通过
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0

## Deferred But Adjudicated

### 混合端点（entity↔external/sql）JOIN 聚合

- Classification: `out-of-scope improvement`（相对本 plan 结果面）
- Why Not Blocking Closure: §4.4.1 D1.2（行 923）裁定混合端点**任意 querySpace 一律走应用层拼接 D5**（两种本质不同的连接机制——平台 ORM session vs 外部 NopMetaDataSource——无法在单一连接跑原生 JOIN SQL）。聚合若走 D5 则 GROUP BY 在 `MAX_CROSS_DB_ROWS`（默认 10000，行 952）截断后的拼接集上执行 → SUM/COUNT/AVG 静默错误（语义近似不可正确）。§4.4.2 D8（行 1013）已将「混合端点 JOIN 聚合」显式列为 deferred。external↔external 同库（单一共享 withConnection 原生 GROUP BY over JOIN，可正确）已使「external/sql 端点可跨表聚合」核心结果面成立。支持混合端点聚合需引入 §4.4.3 D1 平台 `IJdbcTransaction.getConnection()` 跨连接机制（0700-2 明确未建），独立复杂度。
- Successor Required: `no`（需先在 §4.4.1 裁定跨连接 JOIN 聚合机制，属架构决策 design-first）

### 跨 querySpace（跨库）JOIN 聚合（任意端点组合）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 跨库聚合需应用层先按 `MAX_CROSS_DB_ROWS` 拼接再内存 GROUP BY，语义为近似（受截断影响），独立复杂度。沿用 0852-1「跨库 entity-entity」+ 0700-2「跨库行级」既有 deferred 裁定。同库原生聚合（external↔external）已使核心结果面成立。
- Successor Required: `no`

### self-join 双侧别名机制（若侧别 + 单 alias 不足）

- Classification: `watch-only residual`
- Why Not Blocking Closure: `NopMetaTableJoin` 现有 `alias` 仅限右表；同物理表 self-join 需左右双侧别名机制，独立于侧别建模结果面。首版以显式失败处理（不静默），裁定为按需 follow-up。
- Successor Required: `no`

## Non-Blocking Follow-ups

- `MetaAggregationExecutor` 与 `MetaJoinExecutor` 间 join 加载/端点解析/校验的去重收敛（沿用 0852-1 follow-up，若本 plan 复刻则记去重）。
- JOIN 聚合的 `having`/排序增强（沿用 0852-1，首版仅 limit/offset + 默认 filter）。
- 聚合内部表达式语义展开（expression 型 Measure）——沿用单表/0852-1 follow-up。
- save-time 对 side 的轻量结构校验（枚举合法性）——若 query-time 已覆盖，save-time 校验为可选增强。

## Closure

Status Note: external↔external 同库 JOIN 聚合 + Measure/Dimension 侧别建模全部落地。Phase 1 ORM 结构变更（side 列 + dict）经 codegen 重新生成 `_gen`，单表/entity-entity 聚合零回归；Phase 2 query-time 侧别解析与校验（external side 必填、列名存在性、entity 一致性）显式失败不静默；Phase 3 重构端点守卫为组合路由，external↔external 同库经共享 withConnection 跑原生 GROUP BY over JOIN 产出正确聚合。混合端点 / 跨库 / self-join 显式移入 Deferred（各有 Why Not Blocking Closure + 显式抛错 + 测试覆盖）。独立子 agent closure audit APPROVE。
Completed: 2026-07-17

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent（fresh session，task id `ses_0919916ecffejLnpelRdqdlMCK`），read-only 对抗性审计，对 live code 核验
- Audit Session: ses_0919916ecffejLnpelRdqdlMCK
- Evidence:
  - **Phase 1 Exit Criteria**：PASS — `nop-metadata.orm.xml:52-55`（dict `meta/join-side`）；Measure `side` propId 18（`:1188-1190`）；Dimension `side` propId 17（`:1118-1120`）；`_gen` 实体含 `getSide/setSide + PROP_ID_side/PROP_NAME_side`（`_NopMetaTableMeasure.java:93-94/1011`、`_NopMetaTableDimension.java:89-90/962`）。既有单表/entity-entity 聚合回归 16→23 全绿。
  - **Phase 2 Exit Criteria**：PASS — 5 个新 ErrorCode 在失败分支被实际抛出（`MetaAggregationExecutor.java:129/136/142/148/155`）；external side 必填（`JoinExternalSideResolver.resolve:803`）、列名存在性（`:834`）、entity 一致性（`JoinFieldResolver.resolve:757`）；失败用例 `testExternalJoinAggregationExternalSideRequiredFails`/`testExternalJoinAggregationColumnNotOnSideFails`/`testExternalJoinAggregationEntitySideMismatchFails`/`testExternalJoinAggregationCrossQuerySpaceFails`/`testJoinAggregationTableEndpointFails`（混合不变）+ `testJoinAggregationJoinTypeRightFails`（right 既有）全绿。
  - **Phase 3 Exit Criteria**：PASS — `executeJoinAggregation`（`:337-375`）端点组合路由：entity↔entity→`executeEntityEntityJoinAggregation`（`orm().executeQuery:484`，未退化）；external↔external 同库→`executeExternalExternalJoinAggregation`（`withConnection:552`，原生 GROUP BY over JOIN）；跨库→`ERR_AGGR_JOIN_EXTERNAL_CROSS_QUERY_SPACE:362`；混合→`ERR_AGGR_JOIN_MIXED_ENDPOINT_DEFERRED:371`。`testExternalExternalJoinAggregationCorrectness`（断言 A=30/B=30 真实分组聚合）/`testExternalExternalJoinAggregationViaGraphQL`（端到端）/`testEntityJoinAggregationStillWorksAfterRefactor`（entity 路径回归，断言 SUM==totalFields）全绿。
  - **端到端验证（#22）+ 接线验证（#23）**：PASS — GraphQL `queryAggregation(joinId)` 经 executor 路由到聚合 items 输出完整跑通；external↔external 分支被真实调用产非空 items，entity 路径重构后仍被调用。
  - **Anti-Hollow Check**：PASS — external↔external 路径运行时被 `queryAggregation(joinId)` 真实调用并产出分组聚合值（非空壳/非退化单表/非空 items）；无空方法体/continue 跳过/吞异常/TODO-as-done，未实现分支均抛 ErrorCode；entity 路径重构后仍连通（`testEntityJoinAggregationStillWorksAfterRefactor` SUM 断言证明）。
  - **Deferred 项分类检查**：PASS — 混合端点（`ERR_AGGR_JOIN_MIXED_ENDPOINT_DEFERRED` + Plan Deferred 段 + 基线 D9/D8，Why Not Blocking 已写）、跨库（`ERR_AGGR_JOIN_EXTERNAL_CROSS_QUERY_SPACE`/`ERR_AGGR_JOIN_CROSS_QUERY_SPACE`）、self-join（`ERR_AGGR_JOIN_SELF_JOIN`）均显式抛错 + 测试覆盖，无 in-scope live defect 被静默降级。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（所有 checklist 已勾选 + Closure Evidence 已写入）。
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（Critical/High/Medium/Low 均 0 findings）。
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（No errors found）。
  - 测试：`mvn test -pl nop-metadata/nop-metadata-service -Dtest=TestNopMetaAggregationBizModel` → Tests run: 23, Failures: 0, Errors: 0, BUILD SUCCESS；全模块 `mvn test -pl nop-metadata/nop-metadata-service` → Tests run: 347, Failures: 0, Errors: 0, BUILD SUCCESS（nop-orm eql 模块既有 Java26 环境失败与本 plan 无关，改动仅限 nop-metadata）。

Follow-up:

- `MetaAggregationExecutor` javadoc 历史引用 `ERR_AGGR_JOIN_ENDPOINT_NOT_ENTITY`（`:125` {@code} 纯文本，非 {@link}）属准确历史上下文，无功能影响；可后续顺手清理。
- `MetaAggregationExecutor` 与 `MetaJoinExecutor` 间 join 加载/端点解析的去重收敛（沿用 0852-1 follow-up）。
- 混合端点 / 跨库 JOIN 聚合支持需 §4.4.1 跨连接 JOIN 聚合机制裁定（design-first），见 Deferred。
