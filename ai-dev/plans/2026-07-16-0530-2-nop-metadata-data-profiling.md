# 2 nop-metadata 数据剖析（Profiling）

> Plan Status: active
> Last Reviewed: 2026-07-16
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` P2（P2-7）；`ai-dev/design/nop-metadata/06-data-quality-extended.md` §三 数据剖析 + §3.1/§3.2 剖析规则/结果模型；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.7 数据质量
> Mission: nop-metadata
> Work Item: P2-7 — 数据剖析（profiling 规则类型，值分布/统计指标/异常值检测，参考 Apache Griffin）
> Related: `2026-07-16-0225-1-nop-metadata-datasource-registration-and-connection-verification.md`（P2-1，提供 callback 式连接服务 `withConnection`，已 done——本 plan 硬依赖）、`2026-07-16-0420-1-nop-metadata-catalog-runtime-collection.md`（P2-4，提供 external 表 querySpace→数据源→withConnection 执行范式 + 无状态收集器 + 失败隔离/降级模式，已 done——本 plan 直接复用）、`2026-07-16-0530-1-nop-metadata-quality-rule-execution-engine.md`（P2-6 质量执行引擎，**同构参考，非硬前驱**——剖析"对列跑聚合 SQL 产统计"与质量"对列跑检测 SQL 产 pass/fail"同构，若 P2-6 先 done 可直接借鉴其执行器骨架；但 P2-7 不依赖 P2-6 的任何代码产物，仅依赖 P2-1/P2-4）

## Purpose

让 nop-metadata 具备**数据剖析（profiling）**能力——对表的列做统计分析（count/distinct_count/null_count/mean/median/stddev/min/max/percentiles/distribution），产出剖析结果（参考 Apache Griffin / dbt profiling），区别于 P2-6 的 pass/fail 质量检查。本 plan 新建 `MetaProfilingRule` / `MetaProfilingResult` 实体（`未建模实体` 表所列 P2 待建模），提供剖析执行 action，使剖析成为可运行的治理能力，并收口 Phase 2 的最后一个工作项。

## Current Baseline

- **MetaProfilingRule / MetaProfilingResult 实体不存在**：`nop-metadata/model/nop-metadata.orm.xml` 当前实体中**无**这两个实体。`nop-metadata-roadmap.md` §未建模实体表将其列为 P2 待建模（设计 `06-data-quality-extended.md` §3.1/§3.2）。
- **设计契约（draft）**：`06-data-quality-extended.md` §三定义剖析规则（tableId / columns[] / stats[] / sampleSize / schedule）+ 剖析结果模型（tableStats{rowCount,sizeBytes,lastModified} + columnStats[]{columnName,dataType,totalCount,distinctCount,nullCount,emptyCount,numericStats{min,max,mean,median,stddev,percentiles},stringStats{minLength,maxLength,avgLength,topValues},distribution{buckets,counts}}）。该 doc 当前 `Status: draft`，含 Python 伪码 + Open Questions（item 1.1 须顺带重写为最终设计状态，满足 Rule 14）。
- **`profiling` 不在 `meta/quality-rule-type` dict 中**（确认：`quality-rule-type.dict.yaml` 仅 7 值 not_null/unique/range/regex/custom_sql/freshness/volume）。⇒ 剖析若复用 MetaQualityRule 需扩 dict；若新建独立实体则不受 dict 约束。**建模选型为硬前置门禁（item 1.1）**。
- **执行范式（直接复用 P2-1/P2-4，已 done）**：P2-1 提供 `IMetaDataSourceConnectionService.withConnection(datasourceType, connectionConfig, BiConsumer<Connection, DatabaseMetaData>)`；P2-4（Catalog）落地了 external 表执行范式——`NopMetaDataSourceBizModel.collectCatalog`（`NopMetaDataSourceBizModel.java:190-238`）的 querySpace→external 表解析（`findExternalTables`）+ withConnection callback + 无状态收集器（`MetaCatalogCollector`）+ per-item 失败隔离（try/catch + flushSession/clearSession）+ 降级（unavailable 标记）。本 plan 直接复用该范式（剖析是"对列跑聚合 SQL 产统计"，与 Catalog 的"对表跑 COUNT 产统计"同构）。P2-6（质量执行引擎）为同构参考（剖析与质量检测同构），若 P2-6 先 done 可借鉴其执行器骨架，但 P2-7 不硬依赖 P2-6 的代码产物。
- **连接服务（前置已 done）**：P2-1 `IMetaDataSourceConnectionService.withConnection(datasourceType, connectionConfig, BiConsumer<Connection, DatabaseMetaData>)`。
- **NopMetaTable 现状**：external 表列结构存 `buildSql` JSON（A2，**无字段实体**），剖析列名需从此 JSON 解析或由规则 `columns[]` 显式指定；`tableName`/`querySpace`/`tableType='external'` 可用。
- **统计可移植性（Decision 待定）**：count/distinct_count/null_count/empty_count/min/max/avg/stddev 可跨方言用 SQL 聚合（`COUNT/MIN/MAX/AVG`，stddev H2 用 `STDDEV_SAMP`/`STDDEV_POP` 须验证）；median/percentiles/distribution 方言差异大（H2 无原生 PERCENTILE，需 in-app 排序计算或方言特定函数）；topValues 需 `GROUP BY ... ORDER BY COUNT(*) DESC LIMIT N`。**统计范围 + 降级策略为硬前置门禁（item 1.1）**。
- **测试基建**：Nop AutoTest 可用，P2-2/P2-4 已证明可在真实 H2 库建外部表（如 `EXT_DEPT` 含数值/字符串列）被检测。`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS。

## Goals

- 新建 `NopMetaProfilingRule`（剖析规则定义）+ `NopMetaProfilingResult`（剖析结果，承载 tableStats + columnStats 嵌套统计）实体（按 item 1.1 裁定形态），重新生成代码
- `profileTable` GraphQL mutation action 可用：按 `metaTableId`（external）+ 可选 columns[]/stats[] → 复用 withConnection callback → 对每列跑聚合统计 → 写入 NopMetaProfilingResult 时序行（snapshotTime=now）
- 统计可移植 + 降级：便携统计（count/distinct/null/min/max/avg）全方言精确；方言特定统计（median/percentiles/distribution）不可用时显式记 null + 标记 unavailable（不静默跳过整列、不伪造值）
- AutoTest：建 external 表 → 剖析 → 断言 NopMetaProfilingResult 写入真实统计（distinctCount/min/max/avg 等与实测数据一致）

## Non-Goals

- 质量规则执行（pass/fail 检查）—— P2-6（同构参考，非前驱：剖析与质量检测同构但独立结果面），本 plan 是统计剖析，不判 pass/fail
- 质量评分（MetaQualityScore）/ Checkpoint 编排 —— 后续 plan
- **entity/sql 类型表的剖析**（需 querySpace→数据源解析，与 P2-4/P2-6 同源 deferred；首版仅 external）
- 定时自动剖析调度（首版手动 action；定时为 follow-up）
- 异常值检测的完整算法（首版提供 min/max/stddev/percentiles 作为异常值判据原料；完整异常值检测算法为 follow-up）
- 列级剖析结果写入 Catalog 列级统计（与 P2-4 Catalog 列级统计 follow-up 重叠，本 plan 独立存 ProfilingResult）
- 增量剖析 / 流式剖析（`06-data-quality-extended.md` Open Questions，后续）

## Design Decisions

> D1/D2/D3 为硬前置门禁，须在 item 1.1 裁定并写入 `06-data-quality-extended.md` §三 / `01-architecture-baseline.md` 后实现。

### D1. 建模选型 + 存储形态（待 item 1.1 裁定）

- **建模选型**：**新建独立实体 NopMetaProfilingRule / NopMetaProfilingResult**（不复用 MetaQualityRule + profiling ruleType）。理由：剖析结果是**统计值集合**（嵌套 numericStats/stringStats/distribution），与质量结果的 pass/fail + actualValue(double) 形态不同；剖析规则语义（columns[]/stats[]）与质量规则（ruleType/threshold）不同；独立实体避免在 QualityResult 的单 actualValue(double) 列里硬塞统计 JSON。结论写入 `06-data-quality-extended.md` §三。
- **NopMetaProfilingRule 存储**：per-rule 行，列至少 `profilingRuleId`(PK) / `ruleName` / `displayName` / `tableId`(→NopMetaTable, mandatory) / `columns`(JSON，空=所有列) / `stats`(JSON，要收集的指标列表) / `sampleSize`(nullable) / `extConfig`(json) + 审计。
- **NopMetaProfilingResult 存储**：per-execution 时序行，列至少 `profilingResultId`(PK) / `profilingRuleId`(→NopMetaProfilingRule, mandatory) / `metaTableId`(mandatory) / `snapshotTime`(mandatory) / `tableStats`(JSON，rowCount/sizeBytes/lastModified) / `columnStats`(JSON，列级统计数组) + 审计。**`tableStats`/`columnStats` 用 `domain="mediumtext"` + `stdDomain="json"`**（列级统计含 percentiles/topValues/distribution 可能超长，不得用 json-4000，对齐 Manifest/Catalog 的 JSON 列决策）。
- to-one 关系：ProfilingResult→ProfilingRule、ProfilingRule→Table（可选）；索引 `IX_...(profilingRuleId, snapshotTime)` 时序查询 + `IX_...(metaTableId)`。

### D2. 统计范围 + 可移植性 + 降级（待 item 1.1 裁定）

- **便携统计（全方言含 H2，精确）**：totalCount=`COUNT(*)`、distinctCount=`COUNT(DISTINCT col)`、nullCount=`COUNT(*)-COUNT(col)`、emptyCount=`COUNT(*)` WHERE col=''、min/max=`MIN/MAX(col)`、avg=`AVG(col)`、stddev=H2 `STDDEV_SAMP(col)`（item 1.1 验证 H2 支持）。minLength/maxLength=`MIN/MAX(LENGTH(col))`。
- **方言特定/重型统计（降级策略）**：median/percentiles（H2 无原生 PERCENTILE_CONT）→ item 1.1 裁定：in-app 排序计算（拉取列值排序取中位/百分位）或方言特定函数或首版记 unavailable；distribution（分桶）→ in-app 分桶或 unavailable；topValues=`GROUP BY col ORDER BY COUNT(*) DESC LIMIT N`（便携，首版支持）。**不可用统计显式记 null + columnStats[].unavailable=[...] 标记，不静默跳过整列、不伪造值**（对齐 Catalog 降级模式）。
- **列类型适配**：数值列收集 numericStats；字符串列收集 stringStats；类型不适用时（如数值列的 topValues 仍可收集但 stringStats 的 avgLength 不适用）按列类型选择性收集，不适用的记 unavailable 而非伪造。
- **采样（sampleSize）**：首版 sampleSize 仅作记录（首版全表聚合，不实现 TABLESAMPLE；item 1.1 确认是否首版支持采样或记为 follow-up）。

### D3. 执行机制 + action 契约（待 item 1.1 裁定）

- **执行机制**：复用 P2-1/P2-4 范式（已 done）——BizModel action + withConnection callback + 无状态剖析器（参考 P2-4 `MetaCatalogCollector` + P2-6 同构执行器骨架，若可用）。剖析器输入 Connection + schemaPattern + NopMetaTable + columns/stats → 对每列跑聚合 SQL → 返回结构化统计。不自建连接。
- **action 落点 + 契约**：`@BizMutation profileTable(@Name("metaTableId") String id, @Optional @Name("schemaPattern") String schemaPattern, @Optional @Name("columns") String columns, IServiceContext)` → 返回 `Map{profilingResultId, columnCount, unavailable: [...], errors: [...]}`，落点 **NopMetaTableBizModel**（入口键是 metaTableId，操作对象是表；与 collectCatalog 入口风格一致）。也支持按 `NopMetaProfilingRule` 执行：`executeProfilingRule(profilingRuleId)`（按规则定义的 columns/stats 执行）。item 1.1 确认两个入口的取舍（推荐 profileTable 为主入口 + 规则定义可选）。
- **时序语义**：每次剖析追加新行（snapshotTime=now），不覆盖（趋势分析）。单列失败 per-column try/catch 收集 errors 不中断整表（对齐 P2-6 per-rule 隔离）。
- **物理解析 + schema 限定**：复用 P2-6/D1——metaTableId→NopMetaTable(external)→querySpace→NopMetaDataSource→withConnection；schemaPattern 限定物理 SQL，null 依赖默认 schema。

## Scope

### In Scope

- `nop-metadata/model/nop-metadata.orm.xml`：新增 `NopMetaProfilingRule` + `NopMetaProfilingResult` 实体（按 D1 形态）+ to-one 关系 + 索引
- 新增剖析器（无状态）：withConnection callback 内对每列跑聚合统计 + 降级标记
- `NopMetaTableBizModel`：新增 `profileTable` action（落点见 D3）
- AutoTest：建 external 表（含数值列 + 字符串列）→ profileTable → 断言 NopMetaProfilingResult 写入真实 distinctCount/min/max/avg + 不可用统计 unavailable 标记 + 单列失败收集 errors

### Out Of Scope

- P2-6 质量执行（同构参考，非前驱——P2-7 仅硬依赖 P2-1/P2-4）
- 质量评分 / Checkpoint（未建模实体，后续）
- entity/sql 类型表剖析（follow-up）
- 定时调度、增量/流式剖析、完整异常值检测算法（follow-up）

## Execution Plan

### Phase 1 - 剖析实体建模 + 剖析器 + profileTable action

Status: planned
Targets: `nop-metadata/model/nop-metadata.orm.xml`（新增 NopMetaProfilingRule/NopMetaProfilingResult）、新增剖析器（参考 P2-6 执行器 + `MetaCatalogCollector`）、`nop-metadata/nop-metadata-service/.../entity/NopMetaTableBizModel.java`（新增 `profileTable`）、`TestNopMetaTableBizModel.java`

- Item Types: `Decision`（D1 建模选型/存储形态 + D2 统计范围/降级 + D3 执行机制/action 契约，硬前置）+ `Proof`（新功能：数据剖析）

> **硬前置门禁（item 1.1）**：D1/D2/D3 必须先裁定（只裁定不写代码），写入设计文档后再落地 ORM 实体与剖析逻辑。

- [ ] 1.1 **建模选型 + 存储形态 + 统计范围/降级 + 执行机制决策（硬前置门禁，Decision only）**：基于 live repo 核查并裁定 D1/D2/D3——确认 H2 测试库可执行哪些聚合（COUNT/DISTINCT/MIN/MAX/AVG 必有；`STDDEV_SAMP` H2 是否可用；median/percentile H2 无原生 → 裁定 in-app 计算 or unavailable）；裁定建模选型（新建独立实体 vs 复用 QualityRule；推荐独立）；裁定存储形态（per-execution 时序行 + tableStats/columnStats JSON，mediumtext+json）；裁定统计范围（便携精确 + 方言特定降级 unavailable 标记）；裁定执行机制（BizModel action + withConnection + 无状态剖析器）+ action 入口（推荐 NopMetaTableBizModel.profileTable 为主入口；`executeProfilingRule(profilingRuleId)` 是否纳入首版须在本 item 裁定，并将结论同步到 In Scope 或 Non-Goals）。**只裁定不写代码**。结论写入 `06-data-quality-extended.md` §三（建模/统计范围/降级/执行机制 最终设计）+ `01-architecture-baseline.md`（剖析实体说明）。**顺带把 `06-data-quality-extended.md` 从 draft 重写为最终设计状态**（去掉 Python 伪码、收敛 Open Questions，满足 Rule 14）
- [ ] 1.2 **NopMetaProfilingRule + NopMetaProfilingResult 实体落地（Proof，依赖 1.1）**：按 D1 在 `nop-metadata.orm.xml` 新增两实体（列见 D1；tableStats/columnStats 用 `domain="mediumtext"` + `stdDomain="json"`，不得 json-4000）+ to-one 关系 + 索引（`(profilingRuleId, snapshotTime)` 时序 + `metaTableId`）。运行 `./mvnw clean install -pl nop-metadata -T 1C` 重新生成代码确认 BUILD SUCCESS，生成实体类与 CRUD
- [ ] 1.3 新增剖析器（依赖 1.1，无状态，参考 P2-4 `MetaCatalogCollector` + P2-6 同构执行器骨架，若可用）：输入 Connection + DatabaseMetaData + schemaPattern + NopMetaTable + columns/stats → 解析列名（external 表从 buildSql JSON 或规则 columns[]）→ 对每列按 D2 跑聚合 SQL（标识符白名单/转义）→ 收集 tableStats + columnStats → 不可用统计 null + unavailable 标记。在 P2-1 withConnection callback 内调用（不自建连接）
- [ ] 1.4 在 `NopMetaTableBizModel` 新增 `@BizMutation profileTable(@Name("metaTableId") String id, @Optional @Name("schemaPattern") String schemaPattern, @Optional @Name("columns") String columns, IServiceContext context)`（依赖 1.1，落点见 D3）：加载 NopMetaTable → 不存在抛 inline ErrorCode → 非 external 抛/标 SKIP（按 D1）→ table.querySpace→NopMetaDataSource，无数据源抛 → withConnection callback → 剖析器逐列统计 → 每列失败 per-column try/catch 收集 errors 不中断整表 → 追加一行 NopMetaProfilingResult（snapshotTime=now）→ 返回结果 Map。DISABLED/非 jdbc 显式失败（继承 collectCatalog 模式）
- [ ] 1.5 错误码按现有模式在 BizModel 内 inline 定义（参考 `NopMetaDataSourceBizModel` inline ErrorCode 用法）

> **门禁说明**：本 Phase 为单 Phase 含硬前置 Decision（1.1）+ 实现（1.2-1.5，均依赖 1.1）。**Phase 实现项（1.2-1.5）在 1.1 勾选为 `[x]` 前不得开工**——1.1 可能推翻建模选型/统计范围/降级策略，先裁定可避免返工（与 sibling plans 的硬前置门禁模式一致）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] D1/D2/D3 决策已裁定并落地，且**可观测不变量成立**：`NopMetaProfilingRule`/`NopMetaProfilingResult` 实体已生成代码（`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS）；既有回归测试全过
- [ ] `profileTable` 可通过 GraphQL mutation 调用：对 external 表剖析后 `NopMetaProfilingResult__findPage` 返回新增时序行，columnStats 含真实统计（distinctCount/min/max/avg 与实测数据一致，非伪造）
- [ ] 便携统计精确（count/distinct/null/min/max/avg）；方言特定统计（median/percentile/distribution，按 D2）不可用时为 null 且 columnStats[].unavailable 显式列出（不静默跳过整列、不伪造值）
- [ ] 列类型适配成立：数值列有 numericStats、字符串列有 stringStats；类型不适用统计记 unavailable 而非伪造
- [ ] 时序语义成立：重复剖析同一表追加新行（snapshotTime 不同），不覆盖旧行
- [ ] 失败/不可执行路径显式：表不存在 / 非 external（首版）/ 无注册数据源 / DISABLED / 非 jdbc → 显式失败或 SKIP；单列失败（SQL 异常）收集 errors 不中断整表
- [ ] **端到端验证**：从 `syncExternalTables`（建 external 表）→ `profileTable` → `NopMetaProfilingResult__findPage` 可查到真实统计的完整路径已验证（见 Minimum Rules #22）
- [ ] **接线验证**：profileTable 运行时确实通过 P2-1 `withConnection` callback 建连并对每列执行了聚合 SQL（NopMetaProfilingResult 写入真实 distinctCount/min/max 证明），非空壳（见 Minimum Rules #23）
- [ ] **无静默跳过**：不可用统计显式 unavailable；单列失败收集 errors；不可执行路径显式失败/SKIP；无空方法体 / 吞异常 / return null 占位（见 Minimum Rules #24）
- [ ] **新功能测试**：新增测试覆盖 剖析写入（数值列 + 字符串列真实统计）+ 不可用统计 unavailable 标记 + 列类型适配 + 单列失败收集 errors + 时序追加 + 不可执行路径显式失败（not-found/非 external/无数据源/DISABLED/非 jdbc），全绿（见 Minimum Rules #25）
- [ ] `ai-dev/design/nop-metadata/06-data-quality-extended.md` §三 建模/统计范围/降级/执行机制（按 D1/D2/D3）已更新且收敛为最终设计状态；`01-architecture-baseline.md` 剖析实体说明已更新
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：所有条目 + Phase Exit Criteria 全部 `[x]` 后才能 `completed`。

- [ ] NopMetaProfilingRule / NopMetaProfilingResult 实体已建模并重新生成代码
- [ ] profileTable 端到端可用（建 external 表 → 剖析 → NopMetaProfilingResult 可查真实统计）
- [ ] 统计可移植 + 降级成立（便携精确 + 方言特定 null+unavailable，不静默跳过/不伪造）
- [ ] 时序语义成立（重复剖析追加新行，不覆盖）
- [ ] 列类型适配成立（不适用的统计 unavailable 而非伪造）
- [ ] 不存在空壳实现（无空方法体 / 静默跳过 / 吞异常）
- [ ] 必要 focused verification 已完成（剖析 + 降级 + 列类型 + 失败隔离 + 不可执行路径测试全绿）
- [ ] `./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（含测试）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [ ] 受影响的 owner docs 已同步（`06-data-quality-extended.md` §三 + `01-architecture-baseline.md` 剖析实体）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证 profileTable 运行时确实建连 + 对每列执行聚合 SQL + 写入真实统计（端到端连通）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-16-0530-2-nop-metadata-data-profiling.md --strict` 退出码 0

## Deferred But Adjudicated

### entity/sql 类型表的剖析

- Classification: `optimization candidate`
- Why Not Blocking Closure: entity/sql 类型表的物理库定位需 `querySpace → 数据源` 解析，与 P2-4 Catalog / P2-6 质量执行的同源 deferred，复杂度独立。首版 external 类型表（已知注册数据源）已满足"数据剖析可执行可产出统计"的核心结果面
- Successor Required: yes（entity/sql 剖析作为后续增量 plan，与 Catalog/质量的 entity/sql 一并解决 querySpace→数据源解析）

### 完整异常值检测算法

- Classification: `optimization candidate`
- Why Not Blocking Closure: 首版提供 min/max/stddev/percentiles 作为异常值判据原料（IQR/z-score 可由这些原料派生）。完整检测算法（含规则化、告警阈值）属独立结果面，不阻塞剖析统计结果面成立
- Successor Required: no

## Non-Blocking Follow-ups

- 定时自动剖析调度（当前手动 action）
- 增量剖析 / 流式剖析（`06-data-quality-extended.md` Open Questions）
- 采样（sampleSize）的实际执行（首版仅记录，TABLESAMPLE 后续）
- median/percentile/distribution 的 in-app 精确计算（首版方言特定时记 unavailable）
- 多 schema 数据源的剖析（需为 NopMetaTable 增加 schema 列，与 Catalog/质量同源 follow-up）
- 列级剖析结果写入 Catalog 列级统计（与 P2-4 Catalog 列级统计重叠）

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Audit Session:
- Evidence:

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
