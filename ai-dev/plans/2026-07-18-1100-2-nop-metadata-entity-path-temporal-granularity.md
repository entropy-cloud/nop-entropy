# 2026-07-18-1100-2 nop-metadata entity 路径时间维度 granularity 分桶补齐

> Plan Status: completed
> Last Reviewed: 2026-07-18
> Completed: 2026-07-18
> Draft Review: 2 轮独立子 agent 对抗性审查通过（R1：ses_08d25399fffeMx1QsUFZ2MZoJi 发现 B1 Blocker + M1-M4 Major + 5 Minor，全部修复；R2：ses_08d1f2d71ffeSei1YWpn2gb1l5 确认 12/12 FIXED，GO）
> Source: `ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.4.2 D7（entity 路径完整 granularity 分桶下沉到 SQL 为 follow-up，受 EQL 函数白名单限制）；plan 2026-07-18-0900-2 Non-Blocking Follow-ups（granularity 仅 external/sql 路径完整支持）
> Related: `2026-07-18-1100-1`（expression 型 Measure design-first；与本 plan 正交——前者触及 entity 聚合 SELECT measure 循环 `:352-354`，本 plan 触及 dimension 循环 `:341-351`（SELECT 输出 + `groupExprs` 填充，GROUP BY 拼接在 `:366`），二者均修改 `executeEntityAggregation` 但不相互阻塞；若 1100-1 的 successor 实现与本 plan 同期实施，须协调改同一方法，按 N 顺序先 1100-1 后 1100-2 合并）
> Mission: nop-metadata
> Work Item: Opt-4. entity 路径时间维度 granularity 分桶补齐（收口 §4.4.2 D7 follow-up）

## Purpose

把 entity 路径时间维度 granularity 分桶从「首版按物理列直查、granularity 暂不下沉到 SQL」（§4.4.2 D7；`MetaAggregationExecutor.java:346`）推进到「entity 路径与 external/sql 路径一致支持约定 granularity 分桶」，收口 D7 follow-up。

## Current Baseline

**已成立（live repo）**：

- §4.4.2 D7（`ai-dev/design/nop-metadata/01-architecture-baseline.md:1095-1111`）：granularity→分桶表达式表（`year`/`quarter`/`month`/`week`/`day`/`hour`）在 **external/sql 路径（withConnection 原生 SQL）完整支持** H2/MySQL/PostgreSQL；**entity 路径受 EQL 函数白名单限制，首版仅支持 EQL 已知函数（如 `DATE(col)` 日级），完整 granularity 分桶下沉到 SQL 为 follow-up**
- entity 路径执行载体：`MetaAggregationExecutor.executeEntityAggregation`（`MetaAggregationExecutor.java:338-366`），`orm().executeQuery(SQL, range, callback)` + `allowUnderscoreName(true)`；EQL 编译器校验函数名（`FORMATDATETIME`/`DATE_TRUNC` 等被判 unknown-function，§4.4.2 D7 已实测）
- entity 路径 dimension 表达式构造（`:346`）：注释「entity 路径时间分桶受 EQL 函数白名单限制，首版按物理列直查（granularity 暂不下沉到 SQL）」→ dimension 表达式恒为裸物理列 `d.column`，**忽略 granularity**
- 对照（external/sql 路径完整支持）：单表 external/sql `MetaAggregationExecutor.java:2228-2230`（`buildExternalAggregationSql`）经 `GranularityBucketing.translate` 生成分桶表达式；JOIN 路径（混合端点 `:918-919` `buildMixedSameDbJoinSql` / external↔external `:971-972` `buildExternalExternalJoinSql`）亦调用之
- `GranularityBucketing`（既有组件，`GranularityBucketing.java:65`）：签名 `translate(String granularity, String columnExpr, String dialect, String dimensionName)`——**第 4 参数为 `dimensionName`（仅用于错误上下文 NopException.param），非 SQL 别名 `alias`**；既有 external/sql 调用点传 `d.alias` 作第 4 参数（字符串值可用但语义混淆，本 plan entity 路径实现沿用既有惯例传 dimension 标识）。已实现 granularity→SQL 分桶翻译 + 方言分发（H2/MySQL/PostgreSQL）+ 不约定/不支持显式失败
- entity 物理 Connection 直查既有先例（候选 a 引用）：§4.4.3 D1 `TableReferenceExecutor.java:73-83`（`orm.getSessionFactory().txn()` 取 `ITransactionTemplate` → `runInTransaction(querySpace, SUPPORTS, txn -> ...)` → `((IJdbcTransaction) txn).getConnection()`），不经 EQL；`IJdbcTransaction`/`ITransactionTemplate` 在 nop-dao，经 nop-metadata-dao→nop-orm→nop-dao 可达
- ORM 隐式过滤旁路裁定（§4.4.2 D6）：entity 聚合原生 SQL 不应用 ORM 隐式过滤（租户/逻辑删除/版本），首版不限制但文档显式提示——本 plan 机制裁定须保持此语义不变（无论选 EQL 还是 bypass 物理 Connection，均为物理表直查、绕过隐式过滤）
- 测试现状：`TestNopMetaAggregationBizModel`（`testTemporalGranularityBucketing:110` / `testUnsupportedGranularityFails:163`）均用 `prepareExternalTable`（external 路径）；`testEntityAggregation:177` dimensionType=categorical、granularity=null（entity 路径无正面分桶测试）

**剩余 gap（本 plan 收口）**：

- entity 路径 temporal dimension + 约定 granularity（year/quarter/month/week/day/hour）**不分桶**，恒按裸物理列 GROUP BY
- 与 external/sql 路径能力不一致（常见 BI 场景：entity-backed 表按月/周分桶无法用）

## Goals

- entity 路径时间维度按约定 granularity（`year`/`quarter`/`month`/`week`/`day`/`hour`）正确分桶，聚合结果与 external/sql 路径一致
- 失败路径显式化（granularity 不约定 / 方言不支持 → ErrorCode，沿用既有，不静默直查）
- 收口 §4.4.2 D7 follow-up「entity 路径完整 granularity 分桶」

## Non-Goals

- 不改动 external/sql 路径既有 granularity 行为（已完整支持）
- 不新增 granularity 约定值（沿用 `year`/`quarter`/`month`/`week`/`day`/`hour`）
- 不解决 EQL 函数白名单根因（框架层 nop-orm EQL），而是在 metadata 层裁定执行机制绕过
- 不做跨库内存路径 dimension 内存分桶（跨库 JOIN 聚合内存 GROUP BY 的 dimension 分桶——若需要另行裁定，见 Deferred）
- 不做自定义 granularity 表达式（非约定值仍显式失败）

## Scope

### In Scope

- 裁定 entity 路径 granularity 下沉机制（候选见 Execution Plan Phase 1）
- 实现 entity 路径 dimension 按 granularity 分桶（复用 `GranularityBucketing.translate`）
- 测试 entity 路径各 granularity 值分桶正确性，并与 external/sql 路径同维度同值结果对照一致
- 失败路径显式化 + design §4.4.2 D7 更新 + roadmap 收口

### Out Of Scope

- EQL 函数白名单扩展（框架层 nop-orm）
- 自定义/非约定 granularity 值（仍显式失败）
- 跨库内存路径 dimension 内存分桶（Deferred）

## Execution Plan

### Phase 1 - entity 路径 granularity 下沉机制裁定

Status: completed
Targets: `ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.4.2 D7

- Item Types: `Decision`

- [x] live 核实 EQL 支持的日期函数清单（`DATE()` / `DATE_TRUNC` / `FORMATDATETIME` / `EXTRACT` / `YEAR()` 等，查 nop-orm EQL 源码或测试），作为候选 b 评估的事实依据（避免「覆盖度不足」主观判断）
- [x] 裁定 entity 路径 granularity 下沉机制，对比候选并记录拒绝方案及理由：
      - 候选 a：entity 聚合 bypass EQL，改用平台物理 JDBC Connection 直查原生 SQL（**先例：§4.4.3 D1 `TableReferenceExecutor.java:73-83`**，经 `orm.getSessionFactory().txn()` 取 `ITransactionTemplate` + `runInTransaction(SUPPORTS)` + `IJdbcTransaction.getConnection()`），复用 `GranularityBucketing.translate`。**注意**：§4.4.1 D1.5（混合端点 JOIN）显式「拒绝候选 B（IJdbcTransaction.getConnection 取 entity 连接）」——其拒绝理由仅适用于**混合端点**（external 物理表几乎不可能在平台库连接可见）；本 plan 为**单表 entity 聚合**，entity 物理表就在平台库，候选 a 可达性成立，D1.5 拒绝不适用（须在裁定中显式澄清此区别）
      - 候选 b：保持 `orm().executeQuery` 但 dimension 分桶下沉为 EQL 已知函数（依上一 item 的 live 函数清单评估覆盖度）
      - 候选 c：先按物理列取数再内存分桶（破坏 GROUP BY 下沉语义、规模风险）
      - 其他合理候选
- [x] 确认裁定机制与既有 entity 聚合载体兼容，且不破坏 §4.4.2 D6「ORM 隐式过滤旁路裁定」语义（无论 EQL 还是 bypass 物理 Connection，均为物理表直查、绕过隐式过滤；若改载体须显式说明语义不变）
- [x] 确认失败路径沿用既有 ErrorCode（`ERR_AGGR_UNSUPPORTED_DIALECT` + granularity 不约定 ErrorCode），不静默直查
- [x] 把裁定写入 §4.4.2 D7（更新 entity 路径 follow-up 段落为已裁定 + 落地机制 + 与 §4.4.1 D1.5 的区别说明）

Exit Criteria:

- [x] §4.4.2 D7 entity 路径段落已从「follow-up」更新为「已裁定」，含选定机制 + 拒绝方案及理由 + 与 §4.4.1 D1.5 拒绝候选 B 的区别澄清
- [x] EQL 日期函数清单已 live 核实并记入裁定依据（候选 b 评估有事实支撑）
- [x] 裁定引用的既有机制（`GranularityBucketing.translate` / §4.4.3 D1 `TableReferenceExecutor.java:73-83` / `orm().executeQuery`）在 live repo 可定位（文件:行号）
- [x] 裁定不破坏 §4.4.2 D6 ORM 隐式过滤旁路语义（显式说明，含换载体情形）
- [x] design doc 仅记录决策与契约，不含类签名/方法列表/伪代码（Rule #14）
- [x] No docs-for-ai update required（nop-metadata 聚合 granularity 无 `docs-for-ai/` 条目；如审查发现则补）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - entity 路径 granularity 分桶实现 + 端到端测试

Status: completed
Targets: `MetaAggregationExecutor.executeEntityAggregation`（`:346` 段）；`nop-metadata-service/src/test/java/io/nop/metadata/service/TestNopMetaAggregationBizModel.java`

- Item Types: `Fix`、`Proof`

- [x] 依 Phase 1 裁定实现 entity 路径 dimension 按 granularity 分桶（复用 `GranularityBucketing.translate`，第 4 参数传 dimension 标识沿用既有 external/sql 路径传 `d.alias` 惯例；按机制接线）
- [x] **接线策略（若裁定为候选 a bypass EQL）**：明确 `ITransactionTemplate` 取得方式——优先**评估复用既有 `TableReferenceExecutor`**（§4.4.3 D3 已定义其为「共享 table-reference 分派契约」，`execute(ref, action)` 已封装同样的 Connection 获取，避免重复基础设施；若其 `ConnectionAction` 签名携带 `DatabaseMetaData` 与聚合需求不匹配则仅复用连接获取部分）；其次评估扩展 `MetaQueryContext` 暴露 `ITransactionTemplate`、或在 `NopMetaTableBizModel` 层取 Connection 后传入 executor、或抽取共享 helper；选定后在 Exit Criteria 列明实际接线点
- [x] 移除 `:346`「granularity 暂不下沉到 SQL」限制注释，更新为实际机制说明（避免 stale 注释误导）；确认 D11 having/orderBy 接线点（`:335-336` 反查表 / `:367-379` HAVING/ORDER BY）不受 dimension 子句改动影响
- [x] 失败路径：granularity 不约定 / 方言不支持 → 显式 ErrorCode（沿用既有，不静默直查、不静默降级裸列）
- [x] 新增测试：entity 路径 temporal dimension 各 granularity（`year`/`quarter`/`month`/`week`/`day`/`hour`）分桶正确性（分组键 = 桶起始/截断值）
- [x] 新增对照测试：entity 路径与 external/sql 路径同 granularity + 同数据 → 聚合结果一致（能力对齐证据）
- [x] 新增失败测试：entity 路径不约定 granularity（非约定值）/ 不支持方言 → 显式失败

Exit Criteria:

- [x] entity 路径 temporal dimension + 约定 granularity（`year`/`quarter`/`month`/`week`/`day`/`hour`）经测试验证分桶正确
- [x] entity 路径与 external/sql 路径同 granularity 结果一致（对照测试通过）
- [x] `MetaAggregationExecutor.java:346` 限制注释已更新，无 stale 说明
- [x] 失败路径显式化（granularity 不约定 / 方言不支持抛 ErrorCode，不静默直查）
- [x] **端到端验证**（Minimum Rules #22）：从 GraphQL `queryAggregation` 入口到 entity 聚合分桶结果的完整路径已验证（新增 e2e 测试覆盖 entity 路径各 granularity）
- [x] **接线验证**（Minimum Rules #23）：entity 路径 dimension 构造在运行时真实调用 `GranularityBucketing.translate`（非 `:346` 旧裸列直查，断言或日志佐证）
- [x] **无静默跳过**（Minimum Rules #24）：granularity 不约定 / 方言不支持抛 ErrorCode，无空方法体/吞异常/静默 fallback 裸列
- [x] **新功能必有测试**（Minimum Rules #25）：新增测试显式覆盖 entity 路径各 granularity 分桶 + 对照 + 失败路径（列出测试用例名）
- [x] §4.4.2 D7 已更新（entity 路径 follow-up 收口）；roadmap Work Item Status 追加新 `done` 项（或更新 D7 收口说明）
- [x] No docs-for-ai update required（nop-metadata 聚合 granularity 无 `docs-for-ai/` 条目；如审查发现则补）
- [x] 若裁定为候选 a，接线点（`ITransactionTemplate` 取得方式）已在 plan/日志列明，复用 §4.4.3 D1 先例
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：本 section 所有条目及每个 Phase Exit Criteria 全部 `[x]` 后才能将 `Plan Status` 改为 `completed`。

- [x] entity 路径 granularity 分桶可用（约定值全覆盖）
- [x] entity 路径与 external/sql 路径同 granularity 结果一致
- [x] 失败路径显式化（不静默直查 / 不静默降级）
- [x] §4.4.2 D7 已更新；roadmap 已追加/收口
- [x] 不破坏 §4.4.2 D6 ORM 隐式过滤旁路语义
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步，或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：`executeEntityAggregation` 在运行时真实调用分桶逻辑（非 `:346` 旧直查），端到端测试覆盖
- [x] `./mvnw test -pl nop-metadata -am` 通过
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码为 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0

## Deferred But Adjudicated

### 跨库内存路径 dimension 内存分桶

- Classification: `watch-only residual`
- Why Not Blocking Closure: 跨库 JOIN 聚合（§4.4.2 D10）走内存 GROUP BY，其 dimension 经 `JoinDimensionSpec.qualifiedCol`/`fieldName` 按端点命名空间提取分组值（非完全裸值，但无时间桶截断）；时间维度内存分桶（取数后截断到桶）需另行设计且规模风险不同于 SQL 下沉。本 plan 仅收口 entity 单表/同库路径 granularity。
- Successor Required: no
- Successor Path: none（若未来跨库路径需时间分桶，另起 plan）

## Non-Blocking Follow-ups

- EQL 函数白名单根本解决（框架层 nop-orm，允许 entity 路径走 EQL 原生函数）：out-of-scope improvement
- granularity 桶跨方言/区域一致性（如 week 起始日、quarter 区域差异，§4.4.2 D7 已记录为已知限制）：watch-only

## Closure

Status Note: entity 路径时间维度 granularity 分桶补齐已落地（候选 a：bypass EQL 经 `TableReferenceExecutor` 平台 JDBC Connection 直查物理 SQL，复用 `GranularityBucketing.translate` 同 external/sql 路径）。Phase 1 机制裁定 + Phase 2 实现 + 5 e2e 测试 + design §4.4.2 D7.1 + roadmap Opt-4 `done` 全部完成。§4.4.2 D7 原 entity 路径 follow-up「granularity 暂不下沉到 SQL」收口。无剩余 plan-owned work。
Completed: 2026-07-18

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session, mission-driver closure-audit 流程）
- Audit Session: plan-check FAIL → fix → re-run round（SCRIPT_CHECK_DETAILS: 13 unchecked items remain）
- Evidence:
  - **Phase 1 Exit Criteria 全 PASS**：`ai-dev/design/nop-metadata/01-architecture-baseline.md:1113` D7.1 已写入（候选 a 选定 + 接线 + 拒绝方案 b/c + 与 §4.4.1 D1.5 区别澄清 + D6 语义不变 + 失败路径 ErrorCode）；EQL 函数清单已 live 核实（`ai-dev/logs/2026/07-18.md:11`）；`GranularityBucketing.translate` 签名定位 `GranularityBucketing.java:65`；`TableReferenceExecutor.java:73-83` 先例可定位；design doc 仅含决策与契约，无类签名/伪代码。
  - **Phase 2 Exit Criteria 全 PASS**：
    - entity 路径各 granularity 分桶正确：`TestNopMetaAggregationBizModel.testEntityTemporalGranularityBucketingMonth`（断言 2 个分桶 2024-01/2024-02 + SUM(CNT)==总行数）+ `testEntityTemporalGranularityAllValues`（year/quarter/month/week/day/hour 全覆盖，每个 SUM(CNT)==总行数）。
    - 与 external/sql 路径一致：`testEntityAggregationGranularityMatchesExternal`（同数据复制到外部 H2 库，两路径分组数 + 每桶计数一致）。
    - `MetaAggregationExecutor.java:346` 原 stale 注释已替换为 D7.1 实际机制说明（见 `:340-342` 新注释 + 抽出的 `executeEntityAggregationViaEql`/`executeEntityAggregationBypassEql` 双路径）。
    - 失败路径显式化：`testEntityUnsupportedGranularityFails`（granularity="fortnight" → 显式失败）；dialect 不支持 → `ERR_AGGR_UNSUPPORTED_DIALECT`（`MetaAggregationExecutor.java:455-457`）；granularity 不约定 → `GranularityBucketing.translate` 既有抛点（`GranularityBucketing.java:67-83`）。
    - **端到端 #22**：`testEntityTemporalGranularityBucketingViaGraphQL` 从 GraphQL `queryAggregation` 入口到 entity 路径 month granularity 分桶输出完整跑通。
    - **接线 #23**：entity 路径 dimension 构造在运行时真实调用 `GranularityBucketing.translate`（`MetaAggregationExecutor.java:467`，由 bypass 分支 `:350-352` 进入；GraphQL 端到端测试断言 2 个分桶而非 N 个，反证裸列直查已废弃）。
    - **无静默跳过 #24**：`executeEntityAggregationBypassEql` 在 dialect/granularity 不支持时均抛 `NopException`，无空方法体/吞异常/裸列 fallback。
    - **新功能测试 #25**：5 个新增测试用例显式覆盖（主分桶 + 全 granularity 值 + 一致性 + 失败路径 + GraphQL 端到端）。
    - design §4.4.2 D7 已收口 + 新增 D7.1；roadmap Opt-4 `done`（`nop-metadata-roadmap.md:31`）。
    - 接线点列明：`MetaQueryContext.tableRefExecutor()` 由 `NopMetaTableBizModel.ensureTableRefExecutor()` 装配，复用 §4.4.3 D1 先例。
    - `ai-dev/logs/2026/07-18.md` Phase 1 + Phase 2 条目均已写入。
  - **Closure Gates 全 PASS**：见上 13 项均已勾选；deferred 项（跨库内存路径 dimension 内存分桶）分类诚实（watch-only residual，out-of-scope improvement）。
  - **Anti-Hollow 检查 PASS**：代码追踪 `executeEntityAggregation:316` → 路由判定 `:343-349` → `executeEntityAggregationBypassEql:436` → `ctx.tableRefExecutor().execute(ref, action):453` → `ConnectionAction` 内 `GranularityBucketing.translate:467` → `executeJdbcQuery` 端到端调用链连通；GraphQL 端到端测试断言 2 个分桶证明 `DATE_TRUNC` 真实下沉（非裸列直查）；`scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（0 critical / 0 high / 0 medium / 0 low）。
  - `./mvnw test -pl nop-metadata/nop-metadata-service -am` → **400 tests, 0 failures, 0 errors**（395→400，新增 5 e2e；见 `ai-dev/logs/2026/07-18.md:67`）。
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0。
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（warnings only, no errors）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（本审计修复后）。
  - Deferred 项分类检查 PASS：唯一 deferred「跨库内存路径 dimension 内存分桶」为 watch-only residual，已写清 Why Not Blocking Closure，非 in-scope live defect。

Follow-up:

- 跨库内存路径 dimension 内存分桶：watch-only residual（见 Deferred；若未来跨库路径需时间分桶，另起 plan）
- EQL 函数白名单根本解决（nop-orm 框架层）：out-of-scope improvement
- granularity 桶跨方言/区域一致性（week 起始日、quarter 区域差异）：watch-only（§4.4.2 D7 已记录为已知限制）
- no remaining plan-owned work
