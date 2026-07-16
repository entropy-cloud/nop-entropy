# 2 nop-metadata 质量评分（MetaQualityScore）

> Plan Status: active
> Last Reviewed: 2026-07-17
> Draft Review: 两轮独立子 agent 对抗性审查（含想象性分析 + 评分公式 worked example 验证）。R1 发现 2 Major（scoreEntityType=entity 无规则解析路径 → 降级为 table-only v1；SKIP-only 维度 0/0 NaN → null+unavailable）+ 实体计数基线 + 多个 Minor，已修复。R2 worked example 证明 D2/D3 计算确定性，共识 YES（无 Blocker/Major 残留），余 Minor（unavailable payload / 零结果规则边界）已修复。
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md`（未建模实体 `MetaQualityScore`，目标 Phase P2）；`ai-dev/design/nop-metadata/06-data-quality-extended.md` §五（质量评分模型 + 评分计算，含设计意图伪码）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.7 / §2.7.1
> Related: `2026-07-17-0027-1`（质量检查点，先 landing，产出更多 QualityResult 供评分；本 plan 假定 0027-1 已落地，实体计数基线 = 30）；`2026-07-16-0530-1`（基线质量规则执行引擎，产出 QualityResult）；`2026-07-16-0530-2`（数据剖析，QualityScore 不消费 ProfilingResult 的 pass/fail）

## Purpose

把 P2 未建模实体 `MetaQualityScore` 落地，收口「基于已有质量规则执行结果，为逻辑表计算可解释的质量评分（维度分 + 总分 + 趋势）」这一结果面。使数据质量从「单条规则的 pass/fail」上升到「可量化的维度健康度 + 趋势」，参考 Apache Griffin 的评分维度模型（design 06 §5）。

## Current Baseline

> 经 live repo 核查（`nop-metadata`）。**本 plan 假定 `2026-07-17-0027-1`（Checkpoint）已先 landing**：落地后 ORM 实体计数 30（29 + Checkpoint）；本 plan 新增 Score 后为 31。

- **`MetaQualityScore` 不存在**（grep `QualityScore` 在 `*.java` 无命中）。
- **`NopMetaQualityResult` 已落地**（§2.7）：`qualityRuleId`(→Rule) / `executeTime`(时序) / `status`(PASS/FAIL/ERROR/SKIP，dict `meta/quality-result-status`) / `actualValue`(double) / `expectedValue` / `message` / `details`(json)。评分直接消费此实体的 status。
- **`NopMetaQualityRule` 已落地**（§2.7）：`ruleType` ∈ {not_null/unique/range/regex/custom_sql/freshness/volume}（dict `meta/quality-rule-type`）/ `entityType` ∈ {field/table/database}（dict `meta/quality-entity-type`）/ `severity`(INFO/WARNING/ERROR) / `entityId`→`metaTableId`（**规则仅挂载于 NopMetaTable.metaTableId，不挂载于 NopMetaEntity**，§2.7.1 D1）。
- **质量结果时序存储**：每条规则每次执行追加一行（executeTime=now，不覆盖），支持按 (qualityRuleId, executeTime) 时序查询趋势（§2.7）。
- **既有执行入口**：`executeQualityRule` / `executeQualityRulesForDataSource`（plan 0530-1）/ `executeCheckpoint`（plan 0027-1，本 plan 不强依赖但消费其产出）。
- **数据剖析结果**（plan 0530-2）：`NopMetaProfilingResult` 产出统计值（非 pass/fail），**本 plan 不消费 ProfilingResult**（评分基于 pass/fail 质量结果，不基于统计剖析；剖析→评分映射为 follow-up，见 Non-Goals）。
- **JSON 列惯例**：Manifest/Catalog/Profiling 的 JSON 列用 `domain="mediumtext"` + `stdDomain="json"`（可能超长）；小结构用 `json-4000`。
- **dict 大小写惯例**：状态类 dict 大写（quality-result-status: PASS/FAIL）；类型/分类类 dict 小写（quality-entity-type: field/table）。
- **ErrorCode 惯例**：内联 `static final ErrorCode`，`.param(...)`。
- **设计文档现状**：§2.7 / §2.7.1 记录基线执行；§2.7.2 记录剖析；§2.7.3 由 plan 0027-1 新增 Checkpoint；**§2.7.4 质量评分尚未存在**（06 §5 为 follow-up 设计意图，含 Python 伪码，未收敛为最终设计）。

## Goals

- `NopMetaQualityScore` 实体建模（**v1 table 级**，per-table 时序评分行：维度分 + 总分 + 规则结果汇总 + 趋势）。
- `computeQualityScore(metaTableId, context)` BizModel action：读取目标表挂载规则的**最新** QualityResult → 按 ruleType 映射到维度 → 计算维度 pass rate → 加权总分 → 对比上一评分行算趋势 → 写入新 QualityScore 行。
- 评分模型裁定（维度映射 + 计算公式 + 权重 + 时间窗口 + 趋势 + 不可评路径 + SKIP-only 维度处理）写入 `01-architecture-baseline.md` §2.7.4。

## Non-Goals

- **entity 级评分**（NopMetaEntity 维度）——质量规则仅挂载于 `NopMetaTable.metaTableId`（§2.7.1 D1），entity 级评分需额外的 entity→table 规则解析路径，为 follow-up。v1 仅 table 级（`metaTableId` 直接 FK）。
- **基于 ProfilingResult 的评分**——剖析产出统计值（非 pass/fail），与评分维度映射不同；profiling→评分（如 null 率→completeness）为独立 follow-up。本 plan 仅基于 QualityResult 的 PASS/FAIL/ERROR。
- **维度权重运行时可配置**（per-table/per-module 覆盖）——首版用全局默认权重（design 06 §5.2），运行时覆盖为 follow-up（Open Question）。
- **checkpoint 自动触发评分**——本 plan 提供手动 `computeQualityScore` 入口；checkpoint 执行后自动算分为 follow-up 集成。
- **评分告警/阈值动作**（分数低于阈值触发通知）——属动作集成（plan 0027-1 的动作 follow-up 同源），不在本 plan。
- **流式评分、增量评分**——design 06 Open Question，未决，不在本 plan。
- **重写/迁移历史 QualityResult**——评分是只读消费 + 新写 QualityScore，不改既有结果。

## Scope

### In Scope

- **D1 裁定（评分实体结构，v1 table 级）**：单实体 `NopMetaQualityScore`，评分对象为 `NopMetaTable`（quality 规则挂载点）：
  - `metaTableId`(→NopMetaTable.metaTableId, mandatory) —— 评分对象（v1 唯一支持；entity 级见 Non-Goals/Deferred）
  - `scoreTime`(mandatory，时序键) / `overallScore`(double 0~100)
  - `dimensionScores`(mediumtext+json)：`{completeness, accuracy, consistency, timeliness, uniqueness}` 各 0~100 或 null（无对应规则 / 全 SKIP 的维度 → null + 显式标记，不伪造）
  - `ruleSummary`(json-4000)：`{totalRules, passedRules, failedRules, errorRules, skipRules}`（SKIP 单列，不计入 failed）
  - `trend`(json-4000)：`{previousScore, changeRate, trendDirection(improving/stable/degrading)}`（无历史时 null + 标记）
  - `extConfig`(json) + 审计列
  - 索引 `IX_NOP_META_QSCORE_TABLE`(metaTableId, scoreTime) 时序查询
  - 裁定写入 §2.7.4。
- **D2 裁定（维度映射 ruleType → dimension）**：
  - not_null → completeness；volume → completeness
  - unique → uniqueness
  - range → accuracy；regex → accuracy
  - freshness → timeliness
  - custom_sql → consistency（默认；可通过 rule.extConfig.dimension 覆盖，覆盖值不在五维内则计 consistency）
  - **结果状态计入**：PASS 计通过；FAIL/ERROR 计未通过（ERROR 保守计未通过）；**SKIP 不计入任何维度的分子分母**（单列 ruleSummary.skipRules）
  - 无规则的维度 → dimensionScores 该维度 null + `unavailable=["no-rules"]` 标记（不伪造 0/100，对齐 Profiling 降级铁律）
  - **SKIP-only 维度**：某维度有规则但其最新结果**全为 SKIP**（无任何可计数 PASS/FAIL/ERROR）→ 该维度视为不可评，dimensionScores 该维度 null + `unavailable=["skipped"]` 标记（**不计 0、不产生 NaN**，与"无规则维度"同等降级处理）
  - 裁定写入 §2.7.4。
- **D3 裁定（计算公式）**：维度分 = 该维度内 PASS/(PASS+FAIL+ERROR) × 100（SKIP 排除在分母外；SKIP-only 维度 → null，见 D2）；总分 = Σ(非 null 维度的维度分 × 权重) / Σ(非 null 维度的权重)（**仅对非 null 维度归一化权重**）；默认权重（design 06 §5.2）：completeness 0.3 / accuracy 0.3 / consistency 0.2 / timeliness 0.1 / uniqueness 0.1。全部维度 null（对象无任何可评规则或全 SKIP）→ 显式失败抛 ErrorCode（不静默返回 0/不伪造）。裁定写入 §2.7.4。
- **D4 裁定（时间窗口）**：默认取每条规则**最新一条** QualityResult（按 executeTime DESC 取首）参与评分；不支持历史窗口聚合（follow-up）。**规则无任何 QualityResult（从未执行）→ 视为不可评分，按 SKIP 等价处理**（不计入维度分子分母，计入 `ruleSummary.skipRules`），不静默忽略。裁定写入 §2.7.4。
- **D5 裁定（趋势）**：**先查后写**——读取同 (metaTableId) 上一条 QualityScore（按 scoreTime DESC 取首，此时新行尚未写入），changeRate = overall − previous；trendDirection：|changeRate| < 阈值(默认 1.0) → stable，>0 → improving，<0 → degrading；无历史 → trend null + 标记。裁定写入 §2.7.4。
- **D6 裁定（不可评路径显式失败）**：metaTableId 不存在（NopMetaTable 查不到）/ 表无任何挂载规则 / 所有规则最新结果全 SKIP（全维度 null）/ 算出全维度 null → 显式失败抛 inline ErrorCode（不静默 0 分、不伪造）。裁定写入 §2.7.4。
- BizModel action `computeQualityScore(metaTableId, context)` 落 `NopMetaQualityScoreBizModel`（新建），返回 `{scoreId, overallScore, dimensionScores, ruleSummary, trend}`。
- 新增无状态 `MetaQualityScorer`（`.../service/quality/`）：读 QualityResult → 维度聚合 → 加权 → 趋势 → 返回结构化 score。
- dict `meta/quality-trend-direction`（improving/stable/degrading，小写对齐类型/分类类 dict 惯例）。
- 端到端 AutoTest。

### Out Of Scope

- entity 级评分（follow-up，需 entity→table 解析路径）。
- ProfilingResult→评分映射（follow-up）。
- 运行时维度权重覆盖（follow-up，Open Question）。
- checkpoint 自动触发评分（follow-up 集成）。
- 评分告警/通知动作（follow-up）。

## Execution Plan

### Phase 1 - 建模 + dict + 设计裁定

Status: planned
Targets: `nop-metadata/model/nop-metadata.orm.xml`（新增 `NopMetaQualityScore` 实体）；`nop-metadata-meta/src/main/resources/_vfs/dict/meta/quality-trend-direction.dict.yaml`；`ai-dev/design/nop-metadata/01-architecture-baseline.md`（新增 §2.7.4）

- Item Types: `Decision | Fix`

- [ ] **D1**：在 `nop-metadata.orm.xml` 新增 `NopMetaQualityScore` 实体（className `io.nop.metadata.dao.entity.NopMetaQualityScore`），列按 Scope D1；`dimensionScores` 用 `domain="mediumtext"` + `stdDomain="json"`（对齐 Manifest/Catalog/Profiling 的 JSON 列决策），`ruleSummary`/`trend` 用 `domain="json-4000"` + `stdDomain="json"`；to-one 关系 Score→Table（metaTableId mandatory）。
- [ ] 索引 `IX_NOP_META_QSCORE_TABLE`(metaTableId, scoreTime)。
- [ ] 新增 dict `meta/quality-trend-direction`（improving/stable/degrading，小写）。
- [ ] **D2/D3/D4/D5/D6 裁定写入 §2.7.4**（实体结构 + 维度映射 + SKIP-only 维度降级 + 计算公式 + 时间窗口 + 趋势先查后写 + 不可评路径显式失败），与 §设计结论 #9（不引入额外抽象层）一致。
- [ ] `./mvnw clean install -pl nop-metadata/nop-metadata-meta -am` 触发 xmeta/codegen 生成，确认无报错。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `rg '<entity ' nop-metadata/model/nop-metadata.orm.xml` 计数 = 31（假定 0027-1 已落地为 30，本 plan 新增 1）；新实体含 metaTableId(→Table)/scoreTime/overallScore/dimensionScores(mediumtext+json)/ruleSummary/trend + 时序索引。
- [ ] dict 文件 `quality-trend-direction.dict.yaml` 存在且 label 正确（improving/stable/degrading 小写）。
- [ ] §2.7.4 存在且记录 D1-D6 裁定；与 §七 不冲突。
- [ ] `./mvnw compile -pl nop-metadata -am` 通过（codegen + 实体编译）。
- [ ] **No new test required**: Phase 1 仅建模 + dict + 设计裁定，无运行时行为（评分行为在 Phase 2 验证）。
- [ ] **若该 Phase 改变 live baseline**：`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.7.4 已更新；`ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 评分器 + BizModel action + 端到端测试

Status: planned
Targets: `nop-metadata-service`（新增 `MetaQualityScorer` + `NopMetaQualityScoreBizModel`）

- Item Types: `Fix | Proof`

- [ ] **D3/D4**：新增无状态 `MetaQualityScorer`（`.../service/quality/`），方法 `score(metaTableId, context)` → 查目标表挂载规则 → 每规则取最新 QualityResult（executeTime DESC 取首，D4）→ 按 D2 映射维度 → 算维度 pass rate（D3）→ 加权总分（归一化非 null 维度，D3）。
- [ ] **D2**：实现维度映射表（not_null/volume→completeness；unique→uniqueness；range/regex→accuracy；freshness→timeliness；custom_sql→consistency；ERROR 计未通过；SKIP 不计入维度但列 skipRules；无规则维度 null + unavailable；SKIP-only 维度 null + unavailable=["skipped"]）。
- [ ] **D5**：趋势计算——**先查后写**：读同表上一条 QualityScore（scoreTime DESC 取首，此时新行未写），changeRate + trendDirection（improving/stable/degrading）；无历史 trend null + 标记。
- [ ] **D6**：不可评路径（metaTableId 不存在 / 无挂载规则 / 最新结果全 SKIP / 全维度 null）→ 显式失败抛 inline ErrorCode（不静默 0 分、不伪造）。
- [ ] `NopMetaQualityScoreBizModel.computeQualityScore(metaTableId, context)`（`@BizMutation`），写新 QualityScore 行，返回 `{scoreId, overallScore, dimensionScores, ruleSummary, trend}`。
- [ ] 新增 AutoTest（`TestNopMetaQualityScoreBizModel`）：fixture 建 external/entity 表 + 多 ruleType 规则 + 执行产出 PASS/FAIL/ERROR/SKIP 混合 QualityResult；computeQualityScore 后断言（a）overallScore 在 0~100 且与手工计算一致（b）dimensionScores 各维度 pass rate 正确、无规则维度 null、SKIP-only 维度 null（c）连续两次评分 trend 正确（d）无规则表失败（e）全 SKIP 失败。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] computeQualityScore 对多 ruleType 混合结果产出真实 overallScore + 维度分，端到端验证通过（与手工计算一致，非伪造）。
- [ ] **接线验证**：scorer 运行时确实读取 QualityResult（按 qualityRuleId + executeTime DESC）并写入 QualityScore（测试断言 score 行 + 维度分与 fixture 结果集一致）。
- [ ] **端到端验证**：从 `computeQualityScore` GraphQL action 入口 → 读规则 → 读最新结果 → 维度聚合 → 加权 → 趋势 → 写 QualityScore → 返回完整跑通（非仅组件单测）。
- [ ] **无静默跳过**：无规则维度 / SKIP-only 维度 null + unavailable 标记（非伪造 0/100、非 NaN）；无规则表 / 全 SKIP / 全维度 null 显式失败（非静默 0 分）。
- [ ] **新增功能测试覆盖**：`TestNopMetaQualityScoreBizModel` 显式覆盖（a）混合 ruleType 维度映射（b）维度分 pass rate + 总分归一化（c）SKIP-only 维度 null（d）趋势（e）无规则失败（f）全 SKIP 失败 六条路径。
- [ ] `ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.7.4 已与落地一致（Phase 1 已写，Phase 2 核对无 drift）；`ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] `NopMetaQualityScore` 实体落地（ORM 实体计数 31）+ dict + 时序索引。
- [ ] computeQualityScore 产出真实 overallScore + 维度分 + 趋势，与手工计算一致。
- [ ] 无规则维度 / SKIP-only 维度 null + unavailable 标记（不伪造、不 NaN）；不可评路径显式失败（无静默降级到 deferred 的 in-scope 路径）。
- [ ] 评分模型裁定（D1-D6）已写入 §2.7.4 并与 §设计结论 #9 一致。
- [ ] 受影响 owner doc（§2.7.4）已同步到 live baseline。
- [ ] 独立子 agent closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：scorer 运行时确实读 QualityResult 并按维度聚合（非空壳返回固定分）；无空方法体/静默 SKIP/伪造值/NaN。
- [ ] `./mvnw test -pl nop-metadata -am` 通过。
- [ ] checkstyle / 代码规范检查通过。

## Deferred But Adjudicated

### entity 级评分（NopMetaEntity 维度）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 质量规则仅挂载于 `NopMetaTable.metaTableId`（§2.7.1 D1），entity 级评分需额外的 entity→table 规则解析路径（entity → NopMetaTable(tableType=entity, baseEntityId) → rules），独立于评分计算本身。table 级评分已使质量健康度可量化、可用。
- Successor Required: `no`

### ProfilingResult → 评分映射

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: ProfilingResult 产出统计值（mean/stddev/distribution，非 pass/fail），与评分维度（completeness/accuracy... 基于 pass rate）映射语义不同；基于 QualityResult 的 pass/fail 评分已使质量健康度可量化、可用。
- Successor Required: `no`

### 运行时维度权重覆盖

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 全局默认权重（design 06 §5.2）已使评分可用且可解释；per-table/per-module 权重覆盖为 Open Question，不影响核心评分闭环。
- Successor Required: `no`

### checkpoint 执行后自动触发评分

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 手动 `computeQualityScore` 入口已使评分可用；自动触发为接线增强（依赖 plan 0027-1），独立于评分计算本身。
- Successor Required: `yes`
- Successor Path: 后续 follow-up plan（checkpoint→score 接线）

## Non-Blocking Follow-ups

- 历史时间窗口评分（非仅最新结果，聚合最近 N 天）。
- 评分告警/阈值动作（分数低于阈值触发通知，与 plan 0027-1 动作 follow-up 同源）。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<待 closure audit 填写>>

Follow-up:

- <<待 closure audit 填写；或明确写 no remaining plan-owned work>>
