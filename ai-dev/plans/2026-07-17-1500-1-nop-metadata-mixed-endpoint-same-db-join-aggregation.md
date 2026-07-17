# 混合端点（entity↔external/sql）同库 JOIN 聚合 — 机制裁定与实现

> Plan Status: completed
> Last Reviewed: 2026-07-17
> Mission: nop-metadata
> Work Item: P4-deferred-closure / 混合端点 JOIN 聚合（同库部分）
> Draft Review: 三轮独立子 agent 对抗性审查（含想象性分析 + live repo 核验）。R1 发现 1 Major（P1-1：同库判定框架未指出 querySpace 对混合端点语义不可靠 + schema 限定缺失）→ 修订。R2 确认 P1-1 RESOLVED，APPROVE-WITH-MINORS（F1-1 fallback/exit-criteria 联动、F1-2 JoinExternalSideResolver table↔table 上下文适配、F1-3 requireRegistered 在候选 A 下适用性）→ 已全部纳入 Phase 1 Decision 项。R3 确认 Plan 1 APPROVE-WITH-MINORS（F1-1/2/3 已合理处理，无新问题），共识达成，Plan Status → active。
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md`（P4 联邦查询 deferred）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.4.1 D1.2（端点组合路由）+ §4.4.2 D9（侧别建模范围裁定）+ §4.4.3 D1（entity 平台 Connection 机制）；plan `2026-07-17-1200-1` Deferred But Adjudicated「混合端点（entity↔external/sql）JOIN 聚合」Successor Required: no（需先在 §4.4.1 裁定跨连接 JOIN 聚合机制，design-first）
> Related: `2026-07-17-1200-1`（external↔external 同库 JOIN 聚合 + side 建模，混合端点 deferred 留本 plan）、`2026-07-17-0700-2`（混合端点行级 JOIN queryJoinData 已走 D5 拼接）、`2026-07-17-0852-1`（entity↔entity JOIN 聚合）、`2026-07-17-1500-2`（跨库 JOIN 聚合，successor candidate）

## Purpose

把 plan 1200-1 显式 deferred 的「混合端点（entity ↔ external/sql）JOIN 聚合」中**可在单一连接上正确执行**的部分（同物理库、两表在同一连接可见）从「显式失败」推进到「可正确聚合」。本 plan 是 design-first：先在 §4.4.1 裁定混合端点同库 JOIN 聚合的连接载体机制，再据此实现。

## Current Baseline

- **聚合路由现状（live）**：`MetaAggregationExecutor.executeJoinAggregation`（`nop-metadata/nop-metadata-service/.../service/query/MetaAggregationExecutor.java:334`）按端点组合分派：
  - entity↔entity 同 querySpace → `executeEntityEntityJoinAggregation`（`orm().executeQuery` 原生 GROUP BY over JOIN，plan 0852-1）。
  - external↔external 同 querySpace → `executeExternalExternalJoinAggregation`（共享 `withConnection` 原生 GROUP BY over JOIN，plan 1200-1）。
  - external↔external 跨 querySpace → 显式失败 `ERR_AGGR_JOIN_EXTERNAL_CROSS_QUERY_SPACE`（`:359`）。
  - **混合端点（entity ↔ external/sql，任意 querySpace）→ 显式失败 `ERR_AGGR_JOIN_MIXED_ENDPOINT_DEFERRED`（`:368`）**。本 plan 收口此分支中「同库可正确」的部分。
  - entity↔entity 跨 querySpace → 显式失败 `ERR_AGGR_JOIN_CROSS_QUERY_SPACE`（`:403`，留 successor plan `1500-2`）。
- **行级 JOIN（queryJoinData）混合端点已落地**：`MetaJoinExecutor`（§4.4.1 D1.2）对混合端点**一律走应用层拼接 D5**（entity 侧 ORM DAO `fetchEntityRows` + table 侧 `fetchTableRows`，内存按 join key 合并），**不要求 entity querySpace 注册 NopMetaDataSource**。理由：两种本质不同的连接机制（平台 ORM session vs 外部 NopMetaDataSource）无法在单一连接跑原生 JOIN SQL。
- **关键既有机制（§4.4.3 D1，plan 1905-1 落地）**：entity 表可经平台 `IJdbcTransaction.getConnection()`（`ITransactionTemplate.runInTransaction(entityQuerySpace, SUPPORTS, txn -> ...)`）取**平台事务的 JDBC 连接**，配合物理表名 `NopMetaEntity.tableName` 直查。该机制服务 Catalog/Profiling 统计（绕过 EQL 函数白名单），**JOIN 路径当前未使用它**（§4.4.1 D1.2 明确「该机制服务 Catalog/Profiling，JOIN 路径不需要」）。
- **侧别建模（§4.4.2 D9，plan 1200-1）**：`NopMetaTableMeasure`/`NopMetaTableDimension` 已有 `side`（dict `meta/join-side`，left/right）。external/sql 端点 side query-time 必填；entity 端点 side 可选（`entityFieldId→metaEntityId` 无歧义）。混合端点 measure/dimension 的 side 语义在本 plan 须裁定如何复用（entity 侧经 entityFieldId 归属，external/sql 侧经 side 归属）。
- **1200-1 deferred 理由**：§4.4.1 D1.2 裁定混合端点任意 querySpace 一律走截断式应用层拼接 D5，聚合若走 D5 则 GROUP BY 在 `MAX_CROSS_DB_ROWS`（默认 10000）截断后的拼接集上执行 → SUM/COUNT/AVG 静默错误（语义近似不可正确）。支持其**正确**聚合需引入单一共享连接跑原生 GROUP BY over JOIN，独立复杂度 → deferred（design-first）。

## Goals

- **裁定混合端点同库 JOIN 聚合的连接载体机制**：在 §4.4.1 新增裁定（如 D1.5 或扩展 D1.2），明确「entity 物理表与 external/sql 物理表在同一物理库、可在单一连接上同时可见」时，如何获取该单一连接并跑原生 `GROUP BY over JOIN`，产出**正确**（非截断近似）聚合结果。
- **实现混合端点同库 JOIN 聚合**：把 `executeJoinAggregation` 中混合端点分支的「一律显式失败」细化为「同库可正确→执行原生聚合 / 不可同库→显式失败」，复用 side 建模解析两侧 measure/dimension 物理列。
- **失败路径显式化（Anti-Hollow）**：不可同库（两表无单一共享连接）、entity 物理表在该连接不可见、缺 datasource、joinType=right、self-join、side 缺失/列不存在等均显式失败，绝不静默降级为 D5 拼接近似聚合。

## Non-Goals

- **跨库（不同 querySpace）混合端点 JOIN 聚合**：不在本 plan（应用层拼接 + 内存 GROUP BY 近似语义，留 successor plan `1500-2`）。
- **跨库 entity↔entity / external↔external JOIN 聚合**：不在本 plan（留 `1500-2`）。
- **self-join 双侧别名机制**：不在本 plan（watch-only residual，沿用 1200-1 deferred）。
- **行级 JOIN（queryJoinData）混合端点改造**：不在本 plan（行级已走 D5 拼接且语义正确——行级无聚合截断问题，保持现状）。
- **EQL 函数白名单扩展 / 时间分桶在 entity 路径的完整支持**：不在本 plan（沿用 §4.4.2 D7 follow-up）。

## Scope

### In Scope

- §4.4.1 机制裁定（连接载体获取、同库判定、两表共可见性校验、失败语义）。
- `MetaAggregationExecutor` 混合端点同库分支实现（替换 `ERR_AGGR_JOIN_MIXED_ENDPOINT_DEFERRED` 一律失败为「同库执行 / 跨库失败」）。
- measure/dimension 物理列解析：entity 侧经 `entityFieldId→metaEntityId→columnCode`，external/sql 侧经 side→`MetaTableFieldResolver`。
- AutoTest 覆盖：同库成功路径（真实分组聚合值断言）+ 各失败分支 + 既有 entity↔entity/external↔external 回归。

### Out Of Scope

- 跨库 JOIN 聚合（→ `1500-2`）。
- 平台层 `IJdbcTransaction`/`ITransactionTemplate` 自身改造（仅复用既有能力，不改 nop-dao/nop-orm 内部）。
- 混合端点行级 JOIN（queryJoinData）路径。

## Execution Plan

### Phase 1 - 机制裁定（design-first Decision）

Status: completed
Targets: `ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.4.1（+ §4.4.2 D9 引用同步）

- Item Types: `Decision`

- [x] 裁定**连接载体获取方式**：混合端点同库聚合用哪一条连接跑原生 `GROUP BY over JOIN`——候选：(A) 复用 external/sql 端点的 NopMetaDataSource `withConnection`，要求 entity 物理表在该连接可见；(B) 经 §4.4.3 D1 平台 `IJdbcTransaction.getConnection()` 取 entity querySpace 连接（`ITransactionTemplate.runInTransaction(entityQuerySpace, SUPPORTS, ...)`），要求 external 表在该平台连接可见；(C) 其他裁定。须选定唯一方案并写明理由（含对「entity querySpace 通常无 NopMetaDataSource」事实的处理）。§4.4.3 D1 机制经 `TableReferenceExecutor` 生产验证可达，本裁定仅复用不改平台层。
- [x] 裁定**同库判定 + 两表共可见性校验（含 querySpace 语义不可靠性）**：何谓「同库可正确」。**须显式承认 querySpace 字符串相等对混合端点语义不可靠**——entity querySpace（ORM `IOrmSessionFactory` 注册体系）与 external querySpace（`NopMetaDataSource` 注册体系）是两套独立注册表，字符串相等不保证同一物理库。故裁定须选定**连接可达性实测**方案（选定一条连接后实测另一表名是否在该连接的 `DatabaseMetaData` 可见），并解决其先有鸡先有蛋问题（先按候选 A/B 选定基准连接，再实测对端表可见性）。裁定 query-time 校验时机与失败语义（不可同库 → 显式失败 ErrorCode 指向跨库 successor `1500-2`，不静默降级 D5）。
- [x] 裁定**schema 限定**：即便两表在同一连接可见，entity schema（`NopMetaEntity.dbSchema`）与 external schema（`NopMetaTable.schema`）可能不同。裁定 JOIN SQL 中两侧表名的 schema 限定方式（显式 `<schema>.<table>` 限定，沿用 P2-multi-schema 持久化的 schema 列），以及 schema 缺失/不可达时的失败语义。
- [x] 裁定**measure/dimension 物理列解析与 side 复用**：entity 侧经 `entityFieldId→metaEntityId→columnCode`（无歧义），external/sql 侧经 side 绑定端点 + `MetaTableFieldResolver` 解析列名；SQL 中以端点别名（l./r.）限定。side 解析复用既有 `MetaAggregationExecutor.JoinExternalSideResolver`（plan 1200-1 已 landed，该类位于 `MetaAggregationExecutor:778`；**注意 F1-2**：该 resolver 构造面向 table↔table 上下文，混合上下文一端为 `NopMetaEntity`，须裁定仅 table 侧套用 resolver、entity 侧走 entityFieldId→columnCode，或对 resolver 构造做适配）。裁定列名白名单（沿用 §2.7.1 D3）+ 值参数绑定。
- [x] 裁定**`requireRegistered` 在候选 A 下的适用性（F1-3）**：候选 A（用 external `withConnection` 跑原生 SQL 读 entity 物理表）绕过 ORM session，则 `MetaJoinExecutor.requireRegistered`（ORM 实体注册检查）对 entity 侧可能无关或误拒（物理表在外部连接可见但未在平台 `IOrmSessionFactory` 注册）。裁定候选 A 下 entity 侧是否仍需 `requireRegistered`、还是仅校验物理表名可达性。
- [x] **fallback 裁定**：若裁定结论为「两候选均不可靠、混合端点同库无法安全正确聚合」，则显式记录该结论（保留显式失败 + 指向 1500-2 跨库近似路径），本 plan Phase 2 转为「仅落地失败语义细化」而无可执行成功路径，并在 plan 中标注结果面收窄。不得静默降级为 D5 拼接近似聚合。
- [x] 把裁定写入 §4.4.1（新裁定编号，如 D1.5），并**显式修订 §4.4.1 D1.2「混合端点一律走截断式 D5」裁定**为「行级 JOIN 仍走 D5；聚合同库部分按 D1.5 走原生 GROUP BY over JOIN」；同步 §4.4.2 D9「混合端点仍 deferred」措辞为「同库部分已落地、跨库仍 deferred」。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] §4.4.1 含明确裁定：连接载体选定方案 + 理由、同库判定规则（含 querySpace 语义不可靠性的承认与连接可达性实测方案）、schema 限定方式、失败语义、fallback 结论，可在仓库中读到；D1.2 旧裁定已显式修订（行级 vs 聚合分列）。
- [x] 裁定可执行性自检（想象性分析）：按裁定想象实现 `executeMixedSameDbJoinAggregation`，连接获取、两表 schema 限定引用、GROUP BY 构造、列限定均有确定路径，无断层。
- [x] `ai-dev/design/nop-metadata/01-architecture-baseline.md` 更新；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 混合端点同库 JOIN 聚合实现

Status: completed
Targets: `nop-metadata/nop-metadata-service/.../service/query/MetaAggregationExecutor.java`（混合端点分支）；可能新增 helper（连接获取/两表可见性校验）

> **fallback 联动（F1-1）**：若 Phase 1 fallback 裁定触发（同库不可安全正确聚合），本 Phase 的成功路径 Exit Criteria（返回真实聚合值 / 端到端 / 接线验证 executeMixedSameDbJoinAggregation 被调用）视为 N/A，改为仅验收「失败语义细化」Exit Criteria（不可同库→显式失败）。结果面收窄须在 plan 中标注。

- Item Types: `Fix | Proof`

- [x] 重构 `executeJoinAggregation`（`:368`）混合端点分支：把「一律 `ERR_AGGR_JOIN_MIXED_ENDPOINT_DEFERRED`」改为按 Phase 1 裁定分派——同库可正确→新增 `executeMixedSameDbJoinAggregation` 跑原生 GROUP BY over JOIN；不可同库/不可见→显式失败（新 ErrorCode 指向跨库 successor，或复用并细化既有 deferred ErrorCode）。
- [x] 实现 `executeMixedSameDbJoinAggregation`：按裁定获取单一连接（withConnection 或平台 Connection），构造 `SELECT <group cols>, <agg>(col) FROM <entityPhysical> l INNER|LEFT JOIN <externalFromClause> r ON l.<lf>=r.<rf> [WHERE] GROUP BY ...`，标识符白名单 + 参数绑定，measure/dimension 经 side/entityFieldId 解析物理列并以 l./r. 限定。
- [x] 失败路径显式化（#24）：不可同库、entity 物理表在选定连接不可见、缺 datasource、joinType=right、self-join、external/sql 端点 side 缺失、side 列不存在、EQL/SQL 编译失败（保留字列）均抛 ErrorCode，不静默降级 D5、不静默返回空 items、不伪造聚合值。
- [x] 复用既有 join 加载/端点解析/side 校验（`MetaJoinExecutor.loadValidatedJoin`/`resolveEndpoint`（package-private）+ `MetaAggregationExecutor.JoinExternalSideResolver`（`MetaAggregationExecutor.java:778`，plan 1200-1 已 landed）），避免去重 debt（沿用 1200-1 follow-up）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 混合端点同库 JOIN 聚合经 GraphQL `queryAggregation(joinId)` 返回**真实分组聚合值**（非空 items、聚合值与手算一致），混合端点 deferred 一律失败被替换为「同库成功」。
- [x] **端到端验证（#22）**：从 GraphQL `queryAggregation(metaTableId, measures, dimensions, joinId=混合端点join)` 到分组聚合 items 输出完整跑通（H2 本地真实数据）。
- [x] **接线验证（#23）**：新增 `executeMixedSameDbJoinAggregation` 在运行时被 `executeJoinAggregation` 混合端点同库分支真实调用（test 断言或代码追踪证明，非仅类型存在）。
- [x] **无静默跳过（#24）**：不可同库 / 表不可见 / right / self-join / side 缺失 / 列不存在 / 编译失败 各分支抛 ErrorCode（非空 items / 非 D5 降级 / 非伪造值）。
- [x] **新功能测试（#25）**：新增 AutoTest 显式覆盖——同库成功（断言聚合值）+ 不可同库失败 + 表不可见失败 + side 缺失失败 + right 失败 + 列不存在失败；既有 entity↔entity / external↔external / 单表聚合用例全绿无回归。
- [x] **owner-doc 更新**：§4.4.1 裁定已落地标注；roadmap 对应工作项标进度；1200-1 Deferred「混合端点（同库部分）」在本 plan 收口（跨库部分仍 deferred）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 混合端点同库 JOIN 聚合可产出**正确**（非截断近似）聚合结果。
- [x] 1200-1 Deferred「混合端点 JOIN 聚合」**同库部分**在本 plan 收口（跨库部分显式移入 successor `1500-2`，非静默降级）。
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift（不可同库须有显式 Why Not Blocking + 显式抛错）。
- [x] 受影响 owner docs（§4.4.1/§4.4.2 + roadmap）已同步到 live baseline。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 已验证 `executeMixedSameDbJoinAggregation` 在运行时被真实调用、端到端路径连通、无空方法体/静默跳过/no-op。
- [x] `./mvnw compile -pl nop-metadata -am`
- [x] `./mvnw test -pl nop-metadata -am`
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0

## Deferred But Adjudicated

### 混合端点（entity↔external/sql）跨库 JOIN 聚合

- Classification: `optimization candidate`
- Why Not Blocking Closure: 跨库（不同 querySpace）混合端点聚合须走应用层拼接 D5 + 内存 GROUP BY，受 `MAX_CROSS_DB_ROWS` 截断、语义近似。同库正确聚合（本 plan）已使「混合端点可跨表聚合」核心结果面成立。留 successor plan `1500-2`。
- Successor Required: yes
- Successor Path: `ai-dev/plans/2026-07-17-1500-2-nop-metadata-cross-db-join-aggregation.md`

## Non-Blocking Follow-ups

- `MetaAggregationExecutor` 与 `MetaJoinExecutor` 间 join 加载/端点解析/side 解析的去重收敛（沿用 1200-1/0852-1 follow-up）。
- 聚合 `having`/排序增强、`expression` 型 Measure 展开（沿用单表/0852-1 follow-up）。

## Closure

Status Note: 混合端点（entity ↔ external/sql）同库 JOIN 聚合机制已落地（§4.4.1 D1.5 裁定 + `MetaAggregationExecutor.executeMixedSameDbJoinAggregation` 实现）。连接载体选定候选 A（复用 external `withConnection`），同库判定采用连接可达性实测（`DatabaseMetaData.getTables`，跨方言鲁棒：原值/大写/小写三次尝试）。entity 侧经 `entityFieldId→columnCode`（绕过 ORM session/EQL，不调用 `requireRegistered`），external 侧经 side 必填 + 列名存在性校验（F1-2：仅 table 侧套用 side resolver，新增 `JoinMixedSideResolver`）。schema 限定两侧表名显式 `<schema>.<table>`。失败路径全显式：不可同库→`ERR_AGGR_JOIN_MIXED_CROSS_DB_DEFERRED`（指向 successor `1500-2`，不静默降级 D5）；缺 datasource / right / side 缺失 / 列不存在 / SQL 执行失败 均抛 ErrorCode。Anti-Hollow 验证：真实 H2 造数（NOP_META_MODULE entity 物理表 + MIXED_DIM external 表同库），断言 group by CAT_NAME → A=2, B=1（真实聚合值，非伪造/非空 items）。
Completed: 2026-07-17

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（task_id: ses_090ee8eefffesNpx1o77BWuOqU，general subagent，与执行 agent 不同 session）
- Evidence:
  - **Phase 1 Exit Criteria 全 PASS**：
    - §4.4.1 含 D1.5 裁定（`01-architecture-baseline.md:987`，含连接载体候选 A 选定 + querySpace 不可靠性显式承认 + 连接可达性实测方案 + schema 限定 + side 复用 + requireRegistered 适用性 + fallback 范围边界）。
    - D1.2 混合端点行已修订为「行级 JOIN 走 D5；聚合同库→D1.5，跨库→显式失败 deferred」分列（`:976`）。
    - §4.4.2 D9 范围裁定已同步「同库部分 plan 1500-1 D1.5 落地，跨库仍 deferred」（`:1129`）。
    - `check-doc-links.mjs --strict` 退出码 0（0 errors）。
  - **Phase 2 Exit Criteria 全 PASS**：
    - `executeMixedSameDbJoinAggregation` 方法存在于 `MetaAggregationExecutor.java:625`（非 stub，110+ 行真实实现）。
    - `executeJoinAggregation` 混合端点分支（`:391-394`）路由到 `executeMixedSameDbJoinAggregation`（不再抛 `ERR_AGGR_JOIN_MIXED_ENDPOINT_DEFERRED`）。
    - `ERR_AGGR_JOIN_MIXED_CROSS_DB_DEFERRED`（`:144`）在 entity 表不可见时抛出（`:711`）。
    - 7 个新增 mixed 相关 AutoTest 全绿（`:607`/`:648`/`:686`/`:711`/`:739`/`:764`/`:788`）。
    - `./mvnw test -pl nop-metadata/nop-metadata-service`：359 tests, 0 failures（含 Aggregation 测试 30 个全绿）。
    - `./mvnw compile -pl nop-metadata -am`：BUILD SUCCESS。
  - **Anti-Hollow Check PASS**：
    - `executeMixedSameDbJoinAggregation` 真实调用 `ctx.connectionService().withConnection(...)`（`:701`）、`isEntityTableVisible`（`:710`）、`buildMixedSameDbJoinSql`（`:717`）、`executeJdbcQuery`（`:732`）——非空方法体/非静默跳过/非 no-op。
    - `testMixedSameDbJoinAggregationCorrectness` 断言手算真实聚合值（A=2, B=1，`:638-639`），证明真实 GROUP BY over JOIN 而非伪造。
  - **Closure Gates 全 PASS**：
    - `scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（0 findings）。
    - `check-plan-checklist.mjs <plan> --strict` 退出码 0（warnings only when not completed；completed 后转 0 errors）。
    - 1200-1 Deferred「混合端点 JOIN 聚合」同库部分在本 plan 收口，跨库部分显式移入 successor `1500-2`（非静默降级）。
    - 受影响 owner docs（§4.4.1/§4.4.2 + roadmap P4-dc-1）已同步到 live baseline。
