# 1 nop-metadata 质量检查点编排（MetaQualityCheckpoint）

> Plan Status: completed
> Last Reviewed: 2026-07-17
> Draft Review: 两轮独立子 agent 对抗性审查（含想象性分析 + live repo 核验）。R1 发现 3 处 Major/Minor（实体计数基线、appendQualityResult private 可见性、Phase 1 缺测试豁免注记），已修复。R2 共识 YES（无 Blocker/Major 残留），仅余 3 个 Minor dict 大小写/边界澄清，已修复。
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md`（未建模实体 `MetaQualityCheckpoint`，目标 Phase P2）；`ai-dev/design/nop-metadata/06-data-quality-extended.md` §四（Checkpoint 模型 + 执行流程 + 执行动作）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.7 / §2.7.1（基线质量规则执行引擎）
> Related: `2026-07-16-0530-1-nop-metadata-quality-rule-execution-engine.md`（基线单规则/按数据源批量执行）；`2026-07-16-1905-1-nop-metadata-entity-sql-table-execution-extension.md`（提供 `MetaTableReferenceResolver` + `TableReferenceExecutor`，本 plan 复用）；`2026-07-17-0027-2`（质量评分，消费本 plan 与既有 QualityResult）

## Purpose

把 P2 未建模实体 `MetaQualityCheckpoint` 落地，收口「命名检查点 = 一组规则×表的验证配置，可手动批量执行并产出执行摘要」这一结果面。使质量规则执行从「单规则 / 按数据源」扩展到「用户自定义命名验证集」，为质量评分（0027-2）和后续定时调度（follow-up）提供稳定的批量执行入口。

## Current Baseline

> 经 live repo 核查（`nop-metadata/nop-metadata-service` + `nop-metadata/model/nop-metadata.orm.xml`）。

- **29 实体已建模**（`nop-metadata/model/nop-metadata.orm.xml`，`rg -c '<entity '`=29）。`MetaQualityCheckpoint` **不存在**（grep `checkpoint` 在 `*.java` 无命中）。
- **基线质量执行引擎已落地**（plan 0530-1 + 1905-1 扩展）：
  - `MetaQualityRuleExecutor.judge(conn, ref, schemaPattern, ruleType, entityType, paramsJson, sqlExpression, threshold, productName)`（`.../service/quality/MetaQualityRuleExecutor.java:60`）——无状态，消费 `TableReference`，按 §2.7.1 D3 判定语义返回 `QualityRuleJudgment`。
  - `NopMetaQualityRuleBizModel.executeQualityRule(qualityRuleId, schemaPattern?, context)`（`.../service/entity/NopMetaQualityRuleBizModel.java:130`）——单规则：解析目标表 → `tableRefResolver.resolve(...)` → `tableRefExecutor.execute(ref, callback)` → `executor.judge(...)` → `appendQualityResult(...)`。覆盖 external/entity/sql 任意 tableType。
  - `NopMetaQualityRuleBizModel.executeQualityRulesForDataSource(dataSourceId, schemaPattern?, context)`（`:182`）——按数据源 querySpace 下 external 表批量，per-rule try/catch + `flushSession/clearSession` 失败隔离，返回 `{executedCount, results, errors}`。
  - `appendQualityResult(qualityRuleId, judgment)` 写一行 `NopMetaQualityResult`（status PASS/FAIL/ERROR/SKIP + actualValue/expectedValue/message/details）。
- **table-reference 三路分派已落地**（plan 1905-1）：`MetaTableReferenceResolver`（`.../service/tableref/`）+ `TableReferenceExecutor` + `TableReference`（external/entity/sql 三态）。entity 路径取平台 `IJdbcTransaction.getConnection()`，external/sql 路径经 `withConnection`。**本 plan 复用此能力**，无需新增连接管理。
- **dict 现状**：`_vfs/dict/meta/` 已有 `quality-result-status` / `quality-entity-type` / `quality-severity` / `quality-rule-type`。无 checkpoint 相关 dict。
- **质量规则挂载约定**（§2.7.1 D1）：`NopMetaQualityRule.entityType` ∈ {field/table/database}，`entityId` 指向 `NopMetaTable.metaTableId`（external/entity/sql 任意 tableType）。checkpoint 的 tableIds 直接引用 `metaTableId`。
- **ErrorCode 惯例**：内联 `static final ErrorCode ERR_... = ErrorCode.define(...)`，失败 `.param(...)`。
- **设计文档现状**：§2.7 / §2.7.1 记录基线执行；§2.7.2 记录剖析；**§2.7.3 Checkpoint 尚未存在**（06 §四 为 follow-up 设计意图，含 Python 伪码）。

## Goals

- `NopMetaQualityCheckpoint` 实体建模（含 validations/actions 配置列 + 状态 + 模块归属）。
- `executeCheckpoint(checkpointId, schemaPattern?, context)` BizModel action：按 checkpoint 配置解析规则集（显式 ruleIds + tableIds 下挂载的规则），逐条复用既有单规则执行路径，结果写入 `NopMetaQualityResult`，per-rule 失败隔离。
- 产出结构化执行摘要（executedCount/passCount/failCount/errorCount/results/errors）。
- Checkpoint 编排裁定（模型结构 + 规则选择语义 + 动作边界 + 调度边界）写入 `01-architecture-baseline.md` §2.7.3。

## Non-Goals

- **定时调度**（cron 触发）——design 06 §1.3 已裁定定时执行为 follow-up（nop-job/nop-batch 适配），本 plan 仅手动触发。
- **质量评分**——独立 plan `2026-07-17-0027-2`，本 plan 只产出 QualityResult，不算分。
- **动作集成 store 之外**：notify（邮件）/ webhook（HTTP）/ update_docs（文档渲染）——`actions` 列存配置，但首版仅 `actionType=store`（自动随结果写入生效）；其他动作类型配置后执行时显式失败（不静默跳过）。notify/webhook/update_docs 为独立 follow-up。
- **includeInherited 跨模块继承规则解析**——validations 配置仅含显式 ruleIds + tableIds；跨模块规则继承解析为 follow-up（避免 hollow：不保留无法解析的 flag）。
- **流式/增量验证**——design 06 Open Question，未决，不在本 plan。

## Scope

### In Scope

- **D1 裁定（Checkpoint 模型结构）**：单实体 `NopMetaQualityCheckpoint`，配置存 JSON 列（非独立子实体）：
  - `checkpointName` / `displayName` / `moduleId`(→NopMetaModule, optional) / `description`
  - `validations`(mediumtext+json)：`[{ruleIds:[...], tableIds:[...]}]`（一个 checkpoint 可含多组验证配置）
  - `actions`(json-4000)：`[{actionType:"store", enabled:true}]`（首版仅 store；其他类型显式失败）
  - `status`(dict `meta/checkpoint-status`：ACTIVE/PAUSED/DISABLED，大写对齐 status 类 dict 惯例) / `extConfig`(json) + 审计列
  - 索引 `IX_NOP_META_QCHECKPOINT_MODULE`(moduleId)
  - 裁定写入 §2.7.3。
- **D2 裁定（规则选择语义）**：规则集 = ∪（每组 validations 的（显式 ruleIds）∪（tableIds 下挂载的 `NopMetaQualityRule where entityId ∈ tableIds`））；去重；`entityType=database` 规则在执行时按既有 D1 SKIP 写结果行（不剔除，保持与单规则一致语义）。无规则 → 显式失败（非空集静默返回）。裁定写入 §2.7.3。
- **D3 裁定（执行机制 + 复用）**：新增无状态 `MetaQualityCheckpointExecutor`（落 `.../service/quality/`），内部**复用既有单规则执行路径**（resolve 目标表 → `tableRefResolver.resolve` → `tableRefExecutor.execute` → `executor.judge` → 写 NopMetaQualityResult 行），per-rule try/catch + `flushSession/clearSession` 失败隔离（对齐 `executeQualityRulesForDataSource` 模式）。checkpoint executor 不自建连接、不重写判定逻辑。**注**：既有结果写入逻辑 `appendQualityResult` 当前为 `NopMetaQualityRuleBizModel` 的 `private` 方法（`:327`），checkpoint executor 跨类无法直接调用——实现时提取为共享 helper（如 `service/quality/QualityResultWriter`）或提升可见性，而非复制逻辑（避免重复造轮子）。裁定写入 §2.7.3。
- **D4 裁定（动作边界）**：`actionType=store` 随每条规则结果自动生效（写 QualityResult 行即 store）；`actions` 为空/null 视为合法（等价仅 store，store 为隐式默认）。配置了 `store` 之外的动作类型且 `enabled=true` → 该动作在 executeCheckpoint 时**显式失败**抛 inline ErrorCode（不静默跳过、不伪造执行）。裁定写入 §2.7.3。
- **D5 裁定（手动触发 + 状态门禁）**：`executeCheckpoint` 仅手动触发（GraphQL action）；`status=PAUSED/DISABLED` 的 checkpoint 执行时显式失败（不静默跳过）。裁定写入 §2.7.3。
- BizModel action `executeCheckpoint` 落 `NopMetaQualityCheckpointBizModel`（新建），返回执行摘要 Map。
- dict `meta/checkpoint-status`（ACTIVE/PAUSED/DISABLED，大写对齐 status 类 dict 惯例）+ `meta/checkpoint-action-type`（store，小写对齐 type/classification 类 dict 惯例如 quality-rule-type）。
- 端到端 AutoTest：定义 checkpoint（含 ruleIds + tableIds 两组配置）→ executeCheckpoint → 断言每条规则 QualityResult 写入 + 摘要计数正确 + paused 状态失败 + 未知动作类型失败。

### Out Of Scope

- cron 定时调度（follow-up）。
- notify/webhook/update_docs 动作实现（follow-up）。
- 跨模块 includeInherited 规则继承解析（follow-up）。
- 质量评分（plan 0027-2）。
- ORM 模型导入引擎改动（checkpoint 由用户/配置创建，不经 ORM 导入）。

## Execution Plan

### Phase 1 - 建模 + dict + 设计裁定

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml`（新增 `NopMetaQualityCheckpoint` 实体）；`nop-metadata-meta/src/main/resources/_vfs/dict/meta/checkpoint-status.dict.yaml` + `checkpoint-action-type.dict.yaml`；`nop-metadata-meta` 自动生成 xmeta；`ai-dev/design/nop-metadata/01-architecture-baseline.md`（新增 §2.7.3）

- Item Types: `Decision | Fix`

- [x] **D1**：在 `nop-metadata.orm.xml` 新增 `NopMetaQualityCheckpoint` 实体（className `io.nop.metadata.dao.entity.NopMetaQualityCheckpoint`），列按 Scope D1；`validations` 用 `domain="mediumtext"` + `stdDomain="json"`（对齐 Manifest/Catalog/Profiling 的 JSON 列决策，可能多组配置超长），`actions` 用 `domain="json-4000"` + `stdDomain="json"`。
- [x] to-one 关系：Checkpoint→Module（moduleId optional）；不建 validations/actions 子实体（JSON 列）。
- [x] 索引 `IX_NOP_META_QCHECKPOINT_MODULE`(moduleId)。
- [x] 新增 dict `meta/checkpoint-status`（ACTIVE/PAUSED/DISABLED，大写对齐 status 类 dict 惯例）+ `meta/checkpoint-action-type`（store，小写对齐 type 类 dict 惯例）。
- [x] **D2/D3/D4/D5 裁定写入 §2.7.3**（模型结构 + 规则选择语义 + 执行机制复用 + 动作边界 + 手动触发状态门禁），与 §设计结论 #9（不引入额外抽象层）一致。
- [x] `./mvnw clean install -pl nop-metadata/nop-metadata-meta -am` 触发 xmeta/codegen 生成，确认无报错。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `rg '<entity ' nop-metadata/model/nop-metadata.orm.xml` 计数 = 30（新增 1）；新实体含 validations(mediumtext+json)/actions(json-4000)/status/moduleId 列 + 模块关系 + 索引。
- [x] dict 文件 `checkpoint-status.dict.yaml` / `checkpoint-action-type.dict.yaml` 存在且 label 正确；status 大写对齐。
- [x] §2.7.3 存在且记录 D1-D5 裁定；与 §七（拒绝额外抽象层）不冲突。
- [x] `./mvnw compile -pl nop-metadata -am` 通过（codegen + 实体编译）。
- [x] **No new test required**: Phase 1 仅建模 + dict + 设计裁定，无运行时行为（实体的执行行为在 Phase 2 验证）。
- [x] **若该 Phase 改变 live baseline**：`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.7.3 已更新；`ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 执行引擎 + BizModel action + 端到端测试

Status: completed
Targets: `nop-metadata-service`（新增 `MetaQualityCheckpointExecutor` + `NopMetaQualityCheckpointBizModel`）

- Item Types: `Fix | Proof`

- [x] **D3**：新增无状态 `MetaQualityCheckpointExecutor`（`.../service/quality/`），注入既有 `MetaQualityRuleExecutor` / `MetaTableReferenceResolver` / `TableReferenceExecutor` / 各 dao；方法 `execute(NopMetaQualityCheckpoint cp, schemaPattern, context)` → 解析规则集（D2）→ per-rule 复用单规则路径 → `appendQualityResult` → per-rule try/catch + flushSession/clearSession 失败隔离 → 返回摘要。
- [x] **D2**：规则集解析 = ∪（显式 ruleIds）∪（tableIds 下 `NopMetaQualityRule where entityId ∈ tableIds`），去重；ruleId/tableId 不存在 → 记入 errors 不中断（per-item 隔离）；解析后规则集为空 → 显式失败抛 inline ErrorCode `metadata.checkpoint-no-rules`（不静默空集）。
- [x] **D5**：checkpoint `status` 非 ACTIVE（PAUSED/DISABLED）→ 显式失败抛 inline ErrorCode（不静默跳过）。
- [x] **D4**：执行前校验 `actions`，存在 `actionType != store` 且 enabled → 显式失败抛 inline ErrorCode `metadata.checkpoint-action-not-supported`（不静默跳过）。
- [x] `NopMetaQualityCheckpointBizModel.executeCheckpoint(checkpointId, schemaPattern?, context)`（`@BizMutation`），返回 `{checkpointId, executedCount, passCount, failCount, errorCount, results:[...], errors:[...]}`。
- [x] 新增 AutoTest（`TestNopMetaQualityCheckpointBizModel`）：fixture 用 importOrmModel 建 entity 表 + 注册 external 数据源表 + 挂载多条规则；建 checkpoint（ruleIds 组 + tableIds 组混合）；executeCheckpoint 后断言（a）每条规则对应一行 QualityResult（b）摘要 executedCount/passCount/failCount 与实际一致（c）paused checkpoint 失败（d）配置 notify 动作失败（e）空规则集失败。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] executeCheckpoint 对 ruleIds + tableIds 混合配置各产出真实 QualityResult 行，端到端验证通过。
- [x] **接线验证**：checkpoint executor 运行时确实调用既有 `MetaQualityRuleExecutor.judge` + `TableReferenceExecutor.execute` + `appendQualityResult`（测试断言 QualityResult 行写入 + 追踪调用链；非仅 executor 存在）。
- [x] **端到端验证**：从 `executeCheckpoint` GraphQL action 入口 → 规则解析 → 单规则执行 → QualityResult 写入 → 摘要返回完整跑通（非仅组件单测）。
- [x] **无静默跳过**：空规则集 / PAUSED·DISABLED 状态 / 未知动作类型 / 不存在 ruleId·tableId 均显式失败或显式记入 errors（非静默空返回、非伪造）。
- [x] **新增功能测试覆盖**：`TestNopMetaQualityCheckpointBizModel` 显式覆盖（a）混合规则集执行（b）摘要计数（c）paused 失败（d）未知动作失败（e）空规则集失败 五条路径。
- [x] `ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.7.3 已与落地一致（Phase 1 已写，Phase 2 核对无 drift）；`ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `NopMetaQualityCheckpoint` 实体落地（ORM 实体计数 30）+ dict + 模块关系 + 索引。
- [x] `executeCheckpoint` 对 ruleIds/tableIds 混合集产出真实 QualityResult，摘要计数正确。
- [x] 空规则集 / 非 active 状态 / 未知动作类型 均显式失败（无静默降级到 deferred 的 in-scope 路径）。
- [x] Checkpoint 编排裁定（D1-D5）已写入 §2.7.3 并与 §设计结论 #9 一致。
- [x] 受影响 owner doc（§2.7.3）已同步到 live baseline。
- [x] 独立子 agent closure-audit 已完成并记录证据（verdict: APPROVED，详见 Closure section）。
- [x] **Anti-Hollow Check**：checkpoint executor 运行时确实调用既有单规则执行链（judge + TableReferenceExecutor + appendQualityResult），非空壳循环；无空方法体/静默 SKIP。
- [x] `./mvnw test -pl nop-metadata -am` 通过（service 217 tests 0 failure；web 模块 `NopMetadataWebPagesTest` 1 error 为 **pre-existing**——DataContract 页面引用 nop-auth-web 资源，clean HEAD 同样失败，非本 plan 引入）。
- [x] checkstyle / 代码规范检查通过（compile clean；imports 分组 java.*→jakarta.*→third-party→io.nop.*；inline ErrorCode + .param()；英文错误消息）。

## Deferred But Adjudicated

### cron 定时调度

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: design 06 §1.3 已裁定定时执行为独立 follow-up（nop-job/nop-batch 适配）；手动触发入口已使 checkpoint 功能完整可用，调度为增强项。
- Successor Required: `yes`
- Successor Path: 后续 follow-up plan（nop-job 调度集成）

### notify/webhook/update_docs 动作

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: store 动作（结果持久化）已使 checkpoint 执行结果可查询、可追溯；notify/webhook/update_docs 为外部集成增强，依赖邮件/HTTP/渲染层，独立结果面。首版配置后显式失败（非静默）。
- Successor Required: `yes`
- Successor Path: 后续 follow-up plan（动作集成）

### includeInherited 跨模块规则继承

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 显式 ruleIds + tableIds 已覆盖核心用例；跨模块继承解析依赖 Delta 链遍历（§3），复杂度高且独立于编排本身。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 多 schema 数据源下 checkpoint 执行（继承 §2.3.2 / §2.7.1 multi-schema follow-up）。
- checkpoint 执行历史持久化（执行摘要行，独立于 QualityResult）——可选增强。

## Closure

Status Note: 全部 Phase 已完成；两个 Phase 的 Exit Criteria 与本节 Closure Gates 均已勾选；独立子 agent closure-audit verdict = APPROVED。落地范围（D1-D5 裁定 + 实体 + 执行引擎 + BizModel action + 端到端测试）全部交付，无 in-scope 项 deferred。
Completed: 2026-07-17

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit subagent（explore，task ses_0940fea4fffeKOj8lym8TOR9nV，不复用执行 session）
- Verdict: **CLOSURE: APPROVED**（10/10 验证项 PASS）
- 关键证据：
  - 实体计数 30（`nop-metadata.orm.xml` `rg -c '<entity '`=30）；validations/actions 列 `stdDomain="json"` + mediumtext/json-4000（audit 提出 minor deviation 后已修复对齐 plan spec）；status dict meta/checkpoint-status 大写；to-one→Module + 索引 IX_NOP_META_QCHECKPOINT_MODULE。
  - **Anti-Hollow**：`MetaQualityCheckpointExecutor.executeSingleRule` 运行时确实调用既有链——`tableRefResolver.resolve`（:270）/ `tableRefExecutor.execute`（:277）/ `ruleExecutor.judge`（:278）/ `resultWriter.append`（:132），无空方法体/静默 SKIP；database 规则写真 SKIP judgment（:251-259）。
  - 共享 `QualityResultWriter` 已提取，`NopMetaQualityRuleBizModel.appendQualityResult` 委托（:333-334），无复制逻辑。
  - 三处显式失败均抛 inline ErrorCode：`metadata.checkpoint-not-active` / `metadata.checkpoint-no-rules` / `metadata.checkpoint-action-not-supported`。
  - BizModel `@BizMutation executeCheckpoint` + `_service.beans.xml:81` 注册 `biz_NopMetaQualityCheckpoint`。
  - §2.7.3 覆盖 D1-D5，显式声明不引入额外 Driver/QuerySpace/动作框架抽象层（与 §七 一致，:577）。
  - 测试 8 个 @Test 覆盖五条路径（混合执行/摘要计数/paused 失败/未知动作失败/空规则集失败）+ bonus（disabled/空 actions 合法/部分缺失 ref 隔离），真实 H2 + 真实 QualityResult 行断言。
- 测试：`TestNopMetaQualityCheckpointBizModel` 8/8 绿；`TestNopMetaQualityRuleBizModel` 13/13 绿（QualityResultWriter 重构后无回归）；service 全量 217 tests 0 failure。`NopMetadataWebPagesTest` 1 error 为 pre-existing（clean HEAD 同样失败，DataContract→nop-auth-web 资源缺失，非本 plan 引入）。

Follow-up:

- cron 定时调度（nop-job 适配）—— Deferred But Adjudicated，successor required。
- notify/webhook/update_docs 动作实现 —— Deferred But Adjudicated，successor required。
- includeInherited 跨模块规则继承 —— Deferred But Adjudicated，successor NOT required。
- 修复 `NopMetaDataContract` 页面对 nop-auth-web picker 的引用使 `NopMetadataWebPagesTest` 在 nop-metadata 独立构建下可通过 —— 非 plan-owned（属 0900-1 DataContract 遗留，建议另开 follow-up）。
