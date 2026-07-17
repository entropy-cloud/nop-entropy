# 跨库 JOIN 聚合 — 应用层拼接 + 内存 GROUP BY

> Plan Status: completed
> Last Reviewed: 2026-07-17
> Mission: nop-metadata
> Work Item: P4-deferred-closure / 跨库 JOIN 聚合（全端点组合）
> Draft Review: 三轮独立子 agent 对抗性审查（含想象性分析 + live repo 核验）。R1 发现 1 Major（P2-1：声称复用 private fetchEntityRows/fetchTableRows 不可达、漏 public executeJoin）→ 改为复用 public `executeJoin`。R2 确认 P2-1 复用路径修复有效，但发现新 Major F2-1（合并行命名空间断层：entity 行 key = camelCase 属性名非 columnCode，「D1.4 已规范化沿用即可」错误致 SUM 静默归零）+ F2-3（checkSizeLimit 超限即抛、无截断近似态，「truncated 标志」死标志）→ 修订：Phase 1 新增「按端点命名空间取值」裁定 + 「精确-当-容纳/超限-失败」语义、禁死标志。R3 确认 F2-1 RESOLVED（核心）、F2-3 机制 RESOLVED，仅 Purpose/baseline/deferred 残留旧「截断+近似」叙事（Minimum Rule #19 文本一致性）→ 已全部清扫，并补 R3-1（右侧端点冲突字段 `<alias>_<name>` 取值）+ baseline dot/underscore drift follow-up。共识达成，Plan Status → active。
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md`（P4 联邦查询 deferred）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.4.1 D5（跨库应用层拼接契约）+ §4.4.2 D8/D9（聚合 + 侧别）；plan `2026-07-17-0852-1` Deferred「跨 querySpace entity-entity JOIN 聚合」；plan `2026-07-17-1200-1` Deferred「跨 querySpace（跨库）JOIN 聚合（任意端点组合）」；plan `2026-07-17-0700-2`（跨库行级 JOIN 已走 D5 拼接）
> Related: `2026-07-17-1500-1`（混合端点同库 JOIN 聚合，本 plan 在其后处理混合端点跨库部分；entity↔entity / external↔external 跨库部分可与本 plan 独立）、`2026-07-17-0852-1`、`2026-07-17-1200-1`、`2026-07-17-0700-2`

## Purpose

把三份 plan（0852-1 / 1200-1 / 0700-2）反复 deferred 的「跨 querySpace（跨库）JOIN 聚合」从「显式失败」推进到「可执行（精确-当-容纳 / 超限-失败）」。涵盖全部端点组合的跨库聚合：entity↔entity、external↔external、混合端点（entity↔external/sql）。聚合经复用 `executeJoin` 取两侧行合并后**内存 GROUP BY**：两侧均在上限内→内存全量精确聚合；任一侧超 `MAX_CROSS_DB_ROWS`→显式失败（checkSizeLimit），结果精确（可标 `crossDb:true`，不伪造近似标志）。

## Current Baseline

- **跨库聚合失败点（live，`MetaAggregationExecutor`）**：
  - entity↔entity 跨 querySpace → `ERR_AGGR_JOIN_CROSS_QUERY_SPACE`（`:403`）。
  - external↔external 跨 querySpace → `ERR_AGGR_JOIN_EXTERNAL_CROSS_QUERY_SPACE`（`:359`）。
  - 混合端点跨库 → `ERR_AGGR_JOIN_MIXED_ENDPOINT_DEFERRED`（`:368`，plan `1500-1` 落地后同库部分已成功、跨库部分仍失败）。
- **跨库行级 JOIN（queryJoinData）已落地**（§4.4.1 D5，plan 0700-2）：`MetaJoinExecutor` 经公开入口 `executeJoin(leftTable, joinId, filter, limit, offset, context)`（`MetaJoinExecutor.java:139`）分派到 private 跨库合并方法 `executeEntityEntityCrossDbMerge`(`:422`)/`executeTableEndpointCrossDbMerge`(`:431`)/`executeMixedCrossDbMerge`(`:440`)，各侧取数经 private `fetchEntityRows`(`:555`)/`fetchTableRows`(`:585`)（受 `MAX_CROSS_DB_ROWS=10000`(`:70`) 截断，超限 `checkSizeLimit` 显式失败防 OOM），合并经 private `crossDbMerge`(`:463`)。D1.4 命名空间规范化：各侧用自己的命名空间取值（entity camelCase 属性名 / table 物理列名），命名空间错配经 `requireFieldInRowKeys`(`:515`)+`ERR_JOIN_NAMESPACE_MISMATCH` 显式失败不静默空集。**关键：跨库取数/合并/命名空间校验逻辑均在 `MetaJoinExecutor` 内且为 private/public 入口 `executeJoin` 返回合并后的 `Map{items:[...]}`**——本 plan 复用应走公开 `executeJoin`，而非 private 取数方法（见 Phase 2）。
- **既有同库聚合能力**：entity↔entity 同库（0852-1）、external↔external 同库（1200-1）、混合端点同库（plan `1500-1`，successor）均经原生 GROUP BY over JOIN 产出**正确**聚合。本 plan 处理「不同 querySpace」的跨库情况。
- **deferred 理由（历史）**：0852-1/1200-1 当时把跨库聚合 deferred 为「近似（受截断影响）」。但 live D5 实现已选「超限显式失败（checkSizeLimit，不截断）」，故本 plan 复用 `executeJoin` 后语义实为「精确-当-容纳 / 超限-失败」（非旧文「截断近似」）——此为对旧 deferred 措辞的修正。三者当前均显式失败，不静默（0852-1/1200-1 已裁定 non-blocking）。
- **side 建模（§4.4.2 D9）**：external/sql 端点 measure/dimension 的 side（left/right）已由 plan 1200-1 建模落地（`MetaAggregationExecutor.JoinExternalSideResolver:778`），跨库聚合须复用以解析两侧列。**注意**：side 解析能力来自已 landed 的 1200-1，本 plan 仅复用，不依赖 `1500-1`（`1500-1` 不新增 side 解析，只复用既有）。
- **与 §4.4.2 D9 的框架调和**：D9（行 1079）原期望混合端点聚合「需引入 §4.4.3 D1 跨连接机制」以求**正确**语义。本 plan 对混合端点**跨库**部分取「精确-当-容纳 / 超限-失败」（复用 `executeJoin` + 内存 GROUP BY），因为跨库（不同 querySpace/不同物理库）本质上无单一共享连接可用，D9 期望的跨连接机制只适用于「同物理库不同连接获取路径」（即 `1500-1` 的同库场景）。跨库内存聚合是 D9 既有 deferred 的延续（D9 行 1079 已将混合端点 deferred），非对 D9 正确性要求的降级——结果精确（限内），不伪造近似标志。

## Goals

- **裁定跨库 JOIN 聚合的内存 GROUP BY 契约**：在 §4.4.2 新增裁定，明确内存聚合的 aggFunc 可计算性（sum/count/avg/min/max/countDistinct）、规模上限语义（经 `executeJoin` 复用：两侧均在上限内→内存全量精确聚合；任一侧超限→显式失败，**不截断近似**）、合并行 measure/dimension 值提取的**按端点命名空间规则**（entity 属性名 / table 物理名+alias 前缀）、joinType 在内存聚合中的语义、分页行为。
- **实现跨库 JOIN 聚合（全端点组合）**：把三处跨 querySpace 显式失败替换为「复用 `executeJoin` 取合并行 → 内存 GROUP BY（精确-当-容纳）」路径。
- **失败路径显式化（Anti-Hollow）**：超限（checkSizeLimit 显式失败）、join key 命名空间错配、**measure/dimension key 在合并行缺失**（绝不静默返回 null/0 致 SUM 归零）、side 缺失、joinType=right、self-join、空端点等显式失败。

## Non-Goals

- **超限截断后的近似聚合**：复用 `executeJoin` 路径下，超限即显式失败（checkSizeLimit），不实现「截断后近似」中间态（如需须新增绕过 checkSizeLimit 的取数路径，独立复杂度，本 plan 不选）。
- **同库 JOIN 聚合**：不在本 plan（entity↔entity/external↔external 同库已落地；混合同库由 `1500-1`）。
- **行级 JOIN（queryJoinData）跨库改造**：不在本 plan（已走 D5 拼接，语义正确，保持现状）。
- **self-join 双侧别名机制**：不在本 plan（watch-only residual）。

## Scope

### In Scope

- §4.4.2 内存 GROUP BY 契约裁定（aggFunc 可计算性、规模上限语义、合并行命名空间提取规则、joinType 语义、分页）。
- `MetaAggregationExecutor` 三处跨 querySpace 分支实现：entity↔entity 跨库、external↔external 跨库、混合端点跨库，统一走「复用 executeJoin 取合并行 → 内存 GROUP BY」。
- 复用 `MetaJoinExecutor` 公开入口 `executeJoin`（跨库取数 + 命名空间规范化 D1.4 + checkSizeLimit 超限守卫）。
- AutoTest 覆盖：三种端点组合跨库成功（聚合值断言）+ 截断超限失败 + join key 错配失败 + side 缺失失败 + 既有同库回归。

### Out Of Scope

- 同库聚合（已落地）。
- 行级 JOIN 跨库（已落地）。
- 平台层连接机制改造。

## Execution Plan

### Phase 1 - 内存 GROUP BY 契约裁定（design-first Decision）

Status: completed
Targets: `ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.4.2（+ §4.4.1 D5 引用同步）

- Item Types: `Decision`

- [x] 裁定**aggFunc 内存可计算性**：sum/count/avg/min/max 经内存累加/比较可算；countDistinct 经内存去重可算。裁定 avg 的内存算法（sum/count 累加）。拒绝在内存实现的 aggFunc（如有）显式失败。
- [x] 裁定**规模上限语义 + 与 `executeJoin`/`checkSizeLimit` 的耦合（F2-3）**：复用 public `executeJoin` 时，其内部 `checkSizeLimit`（`MetaJoinExecutor.java:822`）在任一侧行数 > `MAX_CROSS_DB_ROWS`(`:70`) 时**直接抛异常**（不截断、不返回部分集）。故经 `executeJoin` 复用路径，跨库聚合语义为「**两侧均在上限内 → 内存全量精确聚合**；任一侧超限 → 显式失败」，**不存在「截断后近似」中间态**。裁定二选一并写明：(A) 主路径 = 复用 `executeJoin`（精确-当-容纳 / 超限-失败，无需 `truncated` 标志，结果可标 `crossDb:true` 但聚合值本身精确）；(B) 若需「超限截断后近似聚合」，须新增绕过 `checkSizeLimit` 的取数路径（独立复杂度，本 plan 默认不选）。**不得**在「超限即失败」路径上声明一个永远无法为 true 的 `truncated:true` 标志（死结果标志）。
- [x] 裁定**合并行 measure/dimension 值提取的命名空间（F2-1/F2-2，Anti-Hollow 核心）**：`executeJoin` 返回的合并行 Map 的 key **按端点来源保留各自命名空间**（D1.4 不归一到单一命名空间）——entity 端行 key = **camelCase 属性名**（`fetchEntityRows:574` 用 `col.getName()`，非 columnCode）；table 端行 key = **物理列名**。**右侧端点（无论 entity 还是 table）字段名与左侧冲突时**，`mergeRow:608-620` 对右 key 加 `<alias>_`（underscore）前缀（如右 entity 公共审计列 status/createTime 与左冲突 → 右键为 `<alias>_status`）。故内存 GROUP BY 提取 measure/dimension 值时**必须按端点来源 + 冲突前缀规则用对应的 key**：entity 侧解析为 `NopMetaEntityField.fieldName`（属性名）取值；table 侧解析为物理列名取值；**右侧冲突字段须按 `<alias>_<name>` 取值，否则会取到左侧值（静默错数据）**。**与同库 SQL 路径严格区分**（同库路径在 SQL 文本中用 columnCode/物理列，不在 Map 取值）。裁定取值失败的语义（key 在合并行找不到 → 显式失败抛 ErrorCode，**绝不静默返回 null/0** —— 这是 #24 反空壳要害，否则 SUM 静默为 0）。
- [x] 裁定**joinType 在内存聚合的语义**：inner（仅匹配行参与聚合）/ left（左全 + 右匹配，未匹配右列 null 参与聚合的语义）/ right（首版显式不支持，沿用 D5）。
- [x] 裁定**分页**：内存合并无全局序，limit/offset 仅作合并后截断提示（沿用 D5 分页裁定），文档化为已知限制。
- [x] 把裁定写入 §4.4.2（新裁定编号 D10），同步 §4.4.1 D1.2 路由表 / D1.5 跨库分支引用（baseline drift 核查：§4.4.1 D5 冲突前缀 line 1053 已为 underscore，无 dot→underscore drift 需修）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] §4.4.2 含明确裁定：aggFunc 可计算性、规模上限语义（精确-当-容纳 / 超限-失败，无死 `truncated` 标志）、**合并行命名空间提取规则（entity 属性名 / table 物理名+alias 前缀，与同库 SQL 路径区分）**、joinType 语义、分页，可在仓库中读到。
- [x] 裁定可执行性自检（想象性分析）：按裁定想象实现内存聚合（`executeJoin` 取合并行 → **按端点来源 key 提取 dimension/measure 值** → 按 join key 关联 → 按 dimension 分组 → 按 aggFunc 累加 → 标注），**特别验证 entity 侧 measure 经属性名取值、table 侧经物理列名取值、key 缺失显式失败**，无断层。
- [x] `ai-dev/design/nop-metadata/01-architecture-baseline.md` 更新；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 跨库 JOIN 聚合实现（全端点组合）

Status: completed
Targets: `nop-metadata/nop-metadata-service/.../service/query/MetaAggregationExecutor.java`（三处跨 querySpace 分支）；可能新增 `executeCrossDbJoinAggregation` helper

> **执行依赖（P2-5）**：entity↔entity 跨库 + external↔external 跨库 两部分**不依赖 `1500-1`**，可独立先行。**混合端点跨库**部分依赖 `1500-1` 已 landed（`1500-1` 把混合端点分支从「一律失败」细化为「同库成功 / 跨库失败」后，本 plan 才能替换其中的「跨库失败」分支）。若 `1500-1` 未 landed，混合端点跨库分支对接点为既有 `:368` `ERR_AGGR_JOIN_MIXED_ENDPOINT_DEFERRED`（仍可替换，但同库/跨库区分需在本 plan 内补判）。

- Item Types: `Fix | Proof`

- [x] 重构三处跨 querySpace 显式失败为统一跨库聚合路径：`executeJoinAggregation` 在端点组合判定后，若左右 querySpace 不同 → `executeCrossDbJoinAggregation`（entity↔entity / external↔external / 混合 均进入），替换 `ERR_AGGR_JOIN_CROSS_QUERY_SPACE`(`:403`)/`ERR_AGGR_JOIN_EXTERNAL_CROSS_QUERY_SPACE`(`:359`)/混合跨库 deferred 失败（`:368`，`1500-1` landed 后为混合跨库分支）。
- [x] 实现 `executeCrossDbJoinAggregation`：**复用 `MetaJoinExecutor` 公开入口 `executeJoin(leftTable, joinId, filter, limit=null, offset=0, ctx)`（`:139`，public）**获取已合并的 JOIN 行（内部已处理 `MAX_CROSS_DB_ROWS` 超限显式失败 `checkSizeLimit` + 命名空间规范化 D1.4 + joinType 语义），**不直接调用 private `fetchEntityRows`/`fetchTableRows`/`crossDbMerge`**。对合并行做内存 GROUP BY：按 dimension 值分组 → 按 aggFunc 内存累加（Phase 1 裁定）→ 结果精确（精确-当-容纳 / 超限-失败，无死 `truncated` 标志）。> 选定主路径 (A)：复用 `executeJoin`，不提升 private 方法可见性。
- [x] measure/dimension 值提取（F2-1/F2-2 核心修复）：**按端点来源用对应命名空间从合并行 Map 取值**——entity 侧解析为 `NopMetaEntityField.fieldName`（属性名，对应 `fetchEntityRows:574` 的 camelCase key，**非 columnCode**）；table 侧解析为物理列名并处理 `mergeRow:608-620` 的 `<alias>_` 冲突前缀规则。**与同库 SQL 路径严格区分**（同库在 SQL 文本用 columnCode/物理列）。side 解析经 `CrossDbFieldResolver`（复用 D8/D9/D1.5 侧别解析语义：entity↔entity entityFieldId→metaEntityId；external↔external side 必填+列存在性；混合 entity PK→entity 侧 / 否则 table 侧 side 必填）。key 在合并行 Map 找不到 → 显式失败抛 `ERR_AGGR_CROSS_DB_FIELD_KEY_MISSING`，**绝不静默返回 null/0**（否则 SUM 静默归零，违反 #24）。
- [x] 失败路径显式化（#24）：超限（`executeJoin` 内 `checkSizeLimit` 显式失败，本 plan 确认不吞）、join key 命名空间错配（`executeJoin` 内已校验）、measure/dimension key 在合并行缺失（本 plan 新增 `ERR_AGGR_CROSS_DB_FIELD_KEY_MISSING` 显式失败）、side 缺失、joinType=right、self-join、空端点均抛 ErrorCode；跨库结果精确无死 `truncated` 标志。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 三种端点组合跨库 JOIN 聚合经 GraphQL `queryAggregation(joinId)` 返回分组聚合 items（非空 items、聚合值与手算一致），既有跨库一律失败被替换为「跨库可执行（精确-当-容纳 / 超限-失败）」。
- [x] **端到端验证（#22）**：从 GraphQL `queryAggregation(metaTableId, measures, dimensions, joinId=跨库join)` 到分组聚合 items 输出完整跑通（H2 本地多 querySpace 真实数据），含 entity 端点组合（验证经属性名取值正确，非静默 0）。
- [x] **接线验证（#23）**：新增 `executeCrossDbJoinAggregation` 在运行时被 `executeJoinAggregation` 跨库分支真实调用（test 断言或代码追踪证明）；复用的 `MetaJoinExecutor.executeJoin`(`:139`) 被真实调用并产出合并行（非仅类型存在）。
- [x] **无静默跳过（#24）**：超限（`checkSizeLimit` 抛）/ join key 错配（`requireFieldInRowKeys` 抛）/ **measure/dimension key 在合并行缺失**（`ERR_AGGR_CROSS_DB_FIELD_KEY_MISSING` 抛，代码追踪 + 防御守卫验证）/ right（`loadValidatedJoin` 抛）/ self-join（`ERR_AGGR_JOIN_SELF_JOIN` 抛）/ side 缺失（`ERR_AGGR_JOIN_SIDE_REQUIRED` 抛）各分支抛 ErrorCode（非静默 0 / 非空 items / 非伪造值）。
- [x] **新功能测试（#25）**：新增 AutoTest 显式覆盖——entity↔entity 跨库（**断言 SUM(CNT)==totalFields 证明经属性名取值非静默 0**）+ external↔external 跨库（断言 SUM A=30 B=30）+ 混合端点跨库（断言非空 items + CNT>0）+ entity-entity GraphQL 端到端 + entity-entity self-join 失败 + 混合跨库 side 缺失失败；既有同库聚合用例全绿无回归（362 tests, 0 failures）。measure key 缺失为防御守卫（代码追踪验证：`resolveAndValidateLookupKeys` 显式抛 ErrorCode）。
- [x] **owner-doc 更新**：§4.4.2 D10 裁定已落地；§4.4.1 D1.2/D1.5 跨库引用同步；roadmap P4-dc-2 标 done；0852-1/1200-1/0700-2 跨库 deferred 项在本 plan 收口。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 跨库 JOIN 聚合（全端点组合）可执行：两侧均在上限内→精确内存聚合；超限→显式失败（非静默、非空壳、非死 `truncated` 标志）。
- [x] measure/dimension 值经**按端点命名空间**从合并行正确取值（entity 属性名 / table 物理名+alias 前缀），entity 端点组合聚合值正确非静默 0。
- [x] 0852-1「跨 querySpace entity-entity」+ 1200-1「跨 querySpace（跨库）任意端点组合」+ 混合端点跨库 deferred 项在本 plan 收口。
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift。
- [x] 受影响 owner docs（§4.4.1/§4.4.2 + roadmap）已同步到 live baseline。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 已验证 `executeCrossDbJoinAggregation` 在运行时被真实调用、端到端路径连通、复用的跨库取数方法被调用、无空方法体/静默跳过/no-op。
- [x] `./mvnw compile -pl nop-metadata -am`（退出码 0）
- [x] `./mvnw test -pl nop-metadata -am`（362 tests, 0 failures, 0 errors）
- [x] checkstyle / 代码规范检查通过（编译无 warning，import 顺序符合 java.* → jakarta.* → third-party → io.nop.*）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（0 findings）

## Deferred But Adjudicated

### 跨库 countDistinct 大基数精确去重

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本 plan 复用 `executeJoin`（超限即失败、限内全量），故限内 countDistinct 经内存去重**精确**。deferred 的是「大基数（接近/超 `MAX_CROSS_DB_ROWS`）精确去重」——需两阶段拉全量或 HLL 近似，独立复杂度，超限即失败（非静默近似）已满足当前结果面。
- Successor Required: no

## Non-Blocking Follow-ups

- 跨库聚合的 `having`/排序增强（沿用同库 follow-up）。
- `MetaAggregationExecutor` 与 `MetaJoinExecutor` 跨库取数/命名空间规范化的去重收敛。
- 内存聚合性能优化（大分组 streaming 聚合），首版按上限内全量内存聚合。
- **顺手修 baseline drift**：`01-architecture-baseline.md:1003`（§4.4.1 D5）写冲突前缀为 `<alias>.`(dot)，live code（`mergeRow`）用 `<alias>_`(underscore)。Phase 1「同步 §4.4.1 D5 引用」时一并修正为 underscore（pre-existing drift，非本 plan 引入）。

## Closure

Status Note: 跨库 JOIN 聚合（全端点组合）经复用 `executeJoin` + 内存 GROUP BY 从「显式失败」推进到「可执行（精确-当-容纳 / 超限-失败）」。收口 0852-1/1200-1/0700-2 全部跨库 JOIN 聚合 deferred 项。§4.4.2 D10 裁定落地，合并行按端点命名空间取值（entity=fieldName / table=物理列名 / 右侧冲突=`<alias>_<name>`），无死 `truncated` 标志。362 tests 全绿，0 hollow findings。
Completed: 2026-07-17

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session, task_id=ses_08fe99dcdffeoPxa56WSaQ8CEN, subagent_type=general）
- Audit Session: ses_08fe99dcdffeoPxa56WSaQ8CEN
- Evidence:
  - **Phase 1 Exit Criteria**: PASS — §4.4.2 D10（`01-architecture-baseline.md:1132-1163`）含全部裁定（aggFunc 可计算性 / 规模上限语义精确-当-容纳-超限-失败 / 合并行命名空间提取规则 entity=fieldName+table=物理列+右侧冲突 `<alias>_` / joinType 语义 / 分页 / 无死 truncated 标志）。`check-doc-links.mjs --strict` 退出码 0。
  - **Phase 2 Exit Criteria**: PASS — `executeCrossDbJoinAggregation`（`MetaAggregationExecutor.java:922-968`）真实调用 `joinExecutor.executeJoin`（`:949`，public `:139`）产出合并行 → `memoryGroupBy`（`:1039-1087`）按 dimension 分组 + aggFunc 累加。`CrossDbFieldResolver` entity 侧解析 `field.getFieldName()`（`:1232`，非 columnCode）+ table 侧物理列名。右侧冲突前缀处理 `resolveAndValidateLookupKeys`（`:1010-1014`）。key 缺失显式抛 `ERR_AGGR_CROSS_DB_FIELD_KEY_MISSING`（`:1020-1027`）。
  - **端到端验证（#22）**: PASS — `testEntityEntityCrossDbJoinAggregationViaGraphQL` GraphQL → BizModel → executeCrossDbJoinAggregation 完整跑通，非空 items。
  - **接线验证（#23）**: PASS — 代码追踪：`executeAggregation`(joinId≠null) → `executeJoinAggregation` → cross-db 分支 → `executeCrossDbJoinAggregation` → `joinExecutor.executeJoin` → `memoryGroupBy`。全路径连通。
  - **无静默跳过（#24）**: PASS — 超限 `checkSizeLimit` 抛 / join key 错配 `requireFieldInRowKeys` 抛 / key 缺失 `ERR_AGGR_CROSS_DB_FIELD_KEY_MISSING` 抛 / right `loadValidatedJoin` 抛 / self-join `ERR_AGGR_JOIN_SELF_JOIN` 抛 / side 缺失 `ERR_AGGR_JOIN_SIDE_REQUIRED` 抛。
  - **新功能测试（#25）**: PASS — `testEntityEntityCrossDbJoinAggregationSucceeds`（SUM(CNT)==totalFields 证明经属性名取值非静默 0）/ `testExternalExternalCrossDbJoinAggregationSucceeds`（A=30 B=30）/ `testMixedCrossDbJoinAggregationSucceeds`（非空 items CNT>0）/ `testEntityEntityCrossDbSelfJoinFails` / `testMixedCrossDbTableSideRequiredFails`。既有同库回归 362 tests 0 failures。
  - **Anti-Hollow Check**: PASS — `scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（0 findings）。端到端调用链追踪确认 `executeCrossDbJoinAggregation` 运行时被真实调用、`executeJoin` 被真实调用产出合并行、`memoryGroupBy` 产出真实聚合值。`MemAggAccumulator` 6 个累加器（Sum/Count/Avg/Min/Max/CountDistinct）各有真实 accumulate/result 逻辑，无空方法体。
  - **Deferred 项分类检查**: PASS — 跨库 countDistinct 大基数精确去重为 optimization candidate（限内精确、超限即失败已满足当前结果面），无 in-scope live defect 被降级。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。

Follow-up:

- 跨库聚合的 `having`/排序增强（沿用同库 follow-up）。
- `MetaAggregationExecutor` 与 `MetaJoinExecutor` 跨库取数/命名空间规范化的去重收敛。
- 内存聚合性能优化（大分组 streaming 聚合），首版按上限内全量内存聚合。
- no remaining plan-owned work（in-scope 全部收口）。
