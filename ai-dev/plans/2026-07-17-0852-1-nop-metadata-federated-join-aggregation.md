# Federated JOIN Aggregation — entity↔entity (queryAggregation over JOIN)

> Plan Status: completed
> Last Reviewed: 2026-07-17
> Source: roadmap `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` P4-3（聚合查询）；plan 0700-2 Deferred But Adjudicated「JOIN 聚合（queryAggregation）的 sql/external JOIN 扩展」；架构基线 `01-architecture-baseline.md` §4.4.1（JOIN D3/D4/D5）+ §4.4.2（聚合 D6/D7）
> Mission: nop-metadata
> Work Item: P4-3+ JOIN 聚合查询执行（entity↔entity 跨表 Measure/Dimension 聚合）
> Related: `2026-07-17-0700-1`（join 端点建模+校验）、`2026-07-17-0700-2`（queryJoinData 行级 JOIN 执行）、`2026-07-16-0800-2`（单表聚合 D6）、`2026-07-17-0228-3`（跨表 Measure/Dimension 校验，entity-entity 直连）

## Purpose

把 `queryAggregation` 从「仅单表」收口到「支持 entity↔entity 跨表 Measure/Dimension 经 JOIN 聚合」，完成 P4 联邦查询聚合在 ORM-backed 表上的最后一块。plan 0700-2 把「JOIN 聚合」裁定为独立 successor（optimization candidate）；本 plan 兑现其中 **entity↔entity** 部分（端点归属可由 `metaEntityId` 无歧义判定）。external/sql 端点的 JOIN 聚合因数据模型表达力缺口（见 Deferred）移出本 plan。

## Current Baseline

（live repo 核实，2026-07-17）

- `NopMetaTableBizModel.queryAggregation`（`nop-metadata-service/.../entity/NopMetaTableBizModel.java:510-517`）仅接收 `metaTableId`，委托 `MetaAggregationExecutor.executeAggregation`（:523-524）。**无 join 参数。**
- `MetaAggregationExecutor`（`nop-metadata-service/.../query/MetaAggregationExecutor.java`）：
  - 路由 `executeAggregation`（:115-141）：entity → `executeEntityAggregation`（:145，`orm().executeQuery`）；external/sql → `executeExternalAggregation`（:211，`withConnection`）。未知类型显式失败。
  - FROM 子句**恒为单表**：entity 用 `physicalTable`（:182）。无任何 JOIN 遍历。
  - entity 字段解析 `resolveEntityFieldColumn`（:466-482）：`entityFieldId` 是 `NopMetaEntityField` 主键 → 查 `columnCode`；`NopMetaEntityField` 带 `metaEntityId`（:484-496），**故 entity 字段归属哪个实体可无歧义判定**。
- `queryJoinData`（`NopMetaTableBizModel.java:480-486`）→ `MetaJoinExecutor.executeJoin`（`MetaJoinExecutor.java:139`）已支持 entity↔entity **行级 JOIN**：同 querySpace → 原生 JOIN SQL（`orm().executeQuery`，:174-179）；不同 querySpace → 应用层拼接（:180-181）。`joinType=right` 全局显式不支持（:152-153）。
- 跨表 Measure/Dimension **校验**：plan 0228-3 仅覆盖 **entity-entity 直连可达**（Measure/Dimension 的 `entityFieldId` 指向经 JOIN 可达的对端 entity 字段）；plan 0700-1 D4 把 external/sql 扩展为**按字段名并集校验**（`MetaTableFieldResolver` 收集 endpoint 字段名为 `Set<String>`，**丢弃 side 信息**）。故 external/sql 端点侧别在数据模型中不可表达。
- 单表聚合对 entity/external/sql 三类型**均可用**；JOIN + 聚合组合**完全缺失**（确认 gap，非接口壳）。

## Goals

- `queryAggregation` 增加可选 `joinId` 参数；提供 `joinId` 且两端点均为 **entity** 时，对 `NopMetaTableJoin` 定义的关联执行**跨表聚合**：所选 Measure + Dimension（可来自左 entity 或经 JOIN 可达的右 entity）经 GROUP BY 聚合返回。
- **同 querySpace entity↔entity** 走 DB 侧原生 `GROUP BY ... OVER JOIN`（复用 `MetaJoinExecutor` entity 同库 JOIN 的端点解析 + FROM/ON 构造语义）。
- 每个 Measure/Dimension 经 `entityFieldId → NopMetaEntityField.metaEntityId` 判定所属端点（left/right entity）+ 解析物理列，在 SQL 中以 `leftAlias.col` / `rightAlias.col` 限定。**entity 字段归属无歧义**（每字段绑定唯一 `metaEntityId`）。
- 聚合语义与单表路径一致：aggFunc（sum/count/avg/min/max/countDistinct）、默认过滤器自动应用、`expression` 型 Measure 显式不支持。
- 失败路径显式（非静默跳过）：join 不存在/不归属、端点非 entity、字段 `metaEntityId` 既不等于左也不等于右 entity、`leftEntityId == rightEntityId`（self-join：字段归属两侧均命中、无法表达右别名，与 external/sql 侧别缺口同源）、joinType=right、跨 querySpace（entity-entity 跨库 JOIN 聚合）均抛 `NopException` + ErrorCode。

## Non-Goals

- **external/sql 端点的 JOIN 聚合**：不在本 plan。`NopMetaTableMeasure/Dimension` 对 external/sql 表的 `entityFieldId` 是**裸列名字符串**（无 `metaEntityId`、无 side/endpointTableId 列），同名列（JOIN 常见：id/name/amount）无法判定左右侧，且用户无法表达「聚合右表 amount」。需数据模型变更（Measure/Dimension 增 side/endpointTableId 字段，Protected Area）→ 见 Deferred。
- **跨 querySpace（跨库）entity-entity JOIN 聚合**：不在本 plan（需应用层先拼接再内存聚合，受 `MAX_CROSS_DB_ROWS` 截断，语义近似）。
- **混合端点（entity ↔ external/sql）JOIN 聚合**：不在本 plan。
- `expression` 型 Measure、`joinType=right`：沿用单表/行级 JOIN 既定显式不支持。
- 聚合内部表达式精确语义展开、JOIN 可达性递归（A→B→C 多跳）：watch-only（沿用 0228-3 / 0700-2）。

## Scope

### In Scope

- `queryAggregation` 可选 `joinId` 参数 + GraphQL 暴露。
- entity↔entity Measure/Dimension 经 `metaEntityId` 判定端点归属 + 物理列限定；歧义/不可归属显式失败。
- 同库 entity↔entity JOIN 聚合（`orm().executeQuery` 原生 GROUP BY over JOIN）。
- 端到端 AutoTest：定义 entity-entity JOIN → 跨表 Measure/Dimension → queryAggregation(joinId) → 断言聚合结果。

### Out Of Scope

- external/sql JOIN 聚合（→ Deferred，数据模型决策）。
- 跨库 / 混合端点 JOIN 聚合（→ Deferred / Non-Goals）。
- `queryJoinData` 行级 JOIN 行为变更（本 plan 只新增聚合路径）。

## Execution Plan

### Phase 1 - entity↔entity JOIN 聚合

Status: completed
Targets: `nop-metadata/nop-metadata-service/.../entity/NopMetaTableBizModel.java`（queryAggregation 签名）、`.../query/MetaAggregationExecutor.java`（JOIN 聚合路径）、抽取/复用 join 加载与端点解析（当前 join 归属/joinType 校验在 `MetaJoinExecutor` 私有方法 `:142-159`，需决定抽取共享 vs 复用——本项须显式选定，不两可）

- Item Types: `Fix`（缺失的 entity↔entity JOIN 聚合能力）、`Decision`（join 校验复用方式）、`Proof`（AutoTest）

- [x] `queryAggregation` 增加可选 `joinId`（`@Optional`）；为空时维持单表既有行为完全不变。
- [x] 提供 `joinId` 时：加载并归属校验 `NopMetaTableJoin` + joinType 校验（显式选定复用方式：抽取 `MetaJoinExecutor` 的 join 加载/归属/joinType 校验为共享方法，或在聚合执行器内复刻并注明去重收敛 follow-up）；解析左右 entity 端点。
- [x] 端点类型守卫：两端点必须均为 entity；任一为 external/sql table 端点 → 显式失败（ErrorCode，指向 external/sql JOIN 聚合 deferred）。
- [x] 每个 Measure/Dimension：`entityFieldId → NopMetaEntityField.metaEntityId` 判定 left/right；既不等于左也不等于右 entity 的 `metaEntityId` → 显式失败（ErrorCode，带 measureName/dimensionName + joinId）；解析物理列 `columnCode`。
- [x] 同库 entity↔entity：构造 `SELECT <group cols>, <agg> FROM <leftPhysical> l INNER|LEFT JOIN <rightPhysical> r ON l.<lf>=r.<rf> [WHERE] GROUP BY ...`，经 `orm().executeQuery` 执行；列名/表名经既有白名单校验。
- [x] EQL 保留字风险：`MetaJoinExecutor.executeSameDbJoin` 为规避 EQL 保留字（`PRECISION`/`SCALE`/`NUMBER` 等）仅投影 join-key 列；本路径须投影两侧**任意** measure/dimension 物理列。须验证这些列名经 EQL 可接受（`allowUnderscoreName`），遇保留字物理列名显式失败并给出迁移指引（不静默退化）。
- [x] 跨 querySpace entity-entity（同库判定不成立）→ 显式失败（ErrorCode，指向跨库 deferred）。

Exit Criteria:

- [x] `queryAggregation` 带 `joinId` 对同库 entity↔entity JOIN 产出正确聚合结果（AutoTest 断言分组聚合值与等价直接 SQL 一致）。
- [x] 不带 `joinId` 时单表行为与改动前逐字一致（回归 AutoTest 通过）。
- [x] **端到端验证**：从 GraphQL `queryAggregation(metaTableId, measures, dimensions, joinId)` 入口到聚合 `items` 输出完整跑通（见 Minimum Rules #22）。
- [x] **接线验证**：JOIN 聚合路径确实调用了 join 解析 + 端点归属判定（非复用单表分支）；新增 ErrorCode 在对应失败分支被抛出（见 #23）。
- [x] **无静默跳过**：external/sql 端点 / 跨库 / right / 字段不可归属 / EQL 保留字 列均抛 ErrorCode 而非返回空 items 或静默退化单表（见 #24）。
- [x] 相关 `ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.4.2 标注 entity↔entity JOIN 聚合已落地（external/sql 仍 deferred）；roadmap P4-3+ 标注进度。
- [x] `ai-dev/logs/2026/07-17.md` 追加条目。

## Closure Gates

- [x] 同库 entity↔entity JOIN 聚合端到端可用并有 focused AutoTest。
- [x] 单表 `queryAggregation`（无 joinId）行为零回归。
- [x] 所有不支持组合（external/sql 端点 / 跨库 / right / 字段不可归属 / 保留字）显式失败（无静默空结果 / 无 no-op / 无静默退化）。
- [x] 新增 ErrorCode 在失败分支被实际抛出（接线验证）。
- [x] design §4.4.2 + roadmap P4-3+ 已同步到 live baseline。
- [x] `./mvnw compile -pl nop-metadata -am`（或含 codegen 的正确模块集）通过。
- [x] `./mvnw test -pl nop-metadata -am` 通过。
- [x] checkstyle / 代码规范检查通过。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（Minimum Rules #26）。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0。
- [x] 独立子 agent closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：entity↔entity JOIN 聚合路径在运行时被 `queryAggregation(joinId)` 真实调用并产出聚合结果（非空壳/非退化单表/非空 items）。

## Deferred But Adjudicated

### external/sql 端点 JOIN 聚合（含混合端点）

- Classification: `out-of-scope improvement`（相对本 plan 结果面）
- Why Not Blocking Closure: external/sql 表的 `NopMetaTableMeasure/Dimension.entityFieldId` 为裸列名字符串（无 `metaEntityId`、无 side/endpointTableId 列），JOIN 同名列（id/name/amount 等）无法判定左右侧，且用户无法表达「聚合右表某列」。支持需 ORM 结构变更（Measure/Dimension 增 side 或 endpointTableId 字段，Protected Area plan-first）+ 侧别解析与歧义策略。entity↔entity 聚合已使「ORM-backed 表跨表指标/维度可聚合」核心结果面成立。
- Successor Required: `yes`
- Successor Path: 后续 plan-first plan（Measure/Dimension 侧别建模 + external/sql JOIN 聚合）

### 跨 querySpace（跨库）entity-entity JOIN 聚合

- Classification: `optimization candidate`
- Why Not Blocking Closure: 跨库聚合需应用层先按 `MAX_CROSS_DB_ROWS` 拼接再内存 GROUP BY，语义为近似（受截断影响），独立复杂度。同库原生聚合已使核心结果面成立。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 抽取 `MetaJoinExecutor` 私有 join 加载/校验为共享方法后的去重收敛（若本 plan 选择复刻，记去重 follow-up）。
- 聚合内部表达式语义展开（expression 型 Measure）——沿用单表 follow-up。
- JOIN 可达性递归（A→B→C 多跳聚合）——watch-only，沿用 0228-3/0700-2。
- JOIN 聚合的 `having`/排序增强——首版仅 limit/offset + 默认 filter。

## Closure

Status Note: entity↔entity JOIN 聚合已落地：`queryAggregation` 增加可选 `joinId`（null→单表 D6 行为零回归）；非空时经抽取共享的 `MetaJoinExecutor.loadValidatedJoin`/`resolveEndpoint`/`requireRegistered`/`resolveFieldToColumn` 解析 join + 端点 + 实体注册 + join 字段，按 `entityFieldId→metaEntityId` 无歧义判定左/右归属并 `l.col`/`r.col` 限定，构造同库 `GROUP BY over JOIN` 经 `orm().executeQuery` 执行。所有不支持组合（external/sql 端点 / 跨库 / self-join / right / 字段不可归属 / EQL 保留字）显式失败（5 个新 ErrorCode + 复用 join 校验的 3 个），无静默跳过/无静默降级。external/sql 与跨库 JOIN 聚合 deferred（数据模型表达力缺口 / 应用层拼接复杂度），已显式失败并裁定 non-blocking。
Completed: 2026-07-17

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (task ses_092391d23ffez0nKqWFEkaPpZ3, explore agent, fresh session)
- Audit Session: ses_092391d23ffez0nKqWFEkaPpZ3
- Evidence:
  - Exit Criterion「joinId 带/不带」: PASS — `MetaAggregationExecutor.executeAggregation:198-201` 仅 joinId 非空时进入 `executeJoinAggregation`；之后 `:203-216` 为原单表路由（未改动）。
  - Exit Criterion「端到端」: PASS — `testEntityJoinAggregationViaGraphQL` 经 graphQLEngine 入口到 items 输出完整跑通。
  - Exit Criterion「正确性」: PASS — `testEntityJoinAggregationCorrectness` 断言 `SUM(count(fields)) grouped by entity.displayName == nop_meta_entity_field 总行数`（JOIN+GROUP BY 正确性证明，非笛卡尔积）。
  - Exit Criterion「单表回归」: PASS — `testNoJoinIdSingleTableBehaviorUnchanged` + 7 个原单表测试全过。
  - Exit Criterion「接线验证」: PASS — `executeJoinAggregation` 真实调用 `loadValidatedJoin`(:310)/`resolveEndpoint`(:313,315)/`requireRegistered`(:333,334)/`resolveFieldToColumn`(:361-364)；JOIN SQL `FROM <l> l JOIN <r> r ON l.<lf>=r.<rf>` 经 `orm().executeQuery`(:424)。
  - Exit Criterion「无静默跳过」: PASS — 5 个新 ErrorCode 在对应分支显式抛出（ENDPOINT_NOT_ENTITY:319-328 / SELF_JOIN:337-340 / CROSS_QUERY_SPACE:345-350 / FIELD_SIDE_UNRESOLVED:516-524 / COMPILE_FAILED:423-430 try-catch），无 return null/continue/空方法体/吞异常。
  - Closure Gate「compile/test」: PASS — `./mvnw test -pl nop-metadata/nop-metadata-service -am`：293 tests pass, 0 failures。
  - Closure Gate「checklist 工具」: PASS — `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0。
  - Closure Gate「hollow 扫描」: PASS — `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（0 findings）。
  - Anti-Hollow 检查: PASS — 运行时调用链连通（`aggregationExecutor` 构造注入共享 `joinExecutor` 单例，4 个 package-private 方法真实被调用）；无空方法体/静默跳过。
  - Deferred 项分类检查: PASS — external/sql 端点 + 跨库 + 混合端点 JOIN 聚合均显式失败（非静默），已裁定 non-blocking（数据模型表达力缺口 / 应用层拼接复杂度）。
  - 独立 closure audit 结论: APPROVED_FOR_CLOSURE（无 blockers），见 audit session ses_092391d23ffez0nKqWFEkaPpZ3。

Follow-up:

- no remaining plan-owned work（external/sql 端点 JOIN 聚合 + 跨库 entity-entity JOIN 聚合已显式 deferred 并裁定 non-blocking，successor plan-first 待数据模型 side/endpointTableId 决策）
