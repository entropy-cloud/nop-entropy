# 96 陈旧计划处置与后续计划裁定

> Plan Status: completed
> Last Reviewed: 2026-06-02
> Source: 全量 94 份计划审查（Plans 01-95），识别出 2 份 in-progress + 5 份 draft/pending/reviewed 计划需要处置
> Related: Plan 32, Plan 42, Plan 06, Plan 11, Plan 09, Plan 07, Plan 15, Plan 57
> Adversarial Review: Round 1 (ses_17f0f4ecfffe4WpxiJAVPIBNpw) — 1 Blocker + 3 Major 已修复

## Purpose

对 `ai-dev/plans/` 中不再代表活跃工作的计划进行正式处置——关闭、标记取代、或取消——使计划队列准确反映当前工作状态。同时对 Plans 85-95 中反复标记为 "Successor Required: yes" 的延迟项进行裁定，确认哪些真正需要后续计划、哪些已被正确延迟。

## Current Baseline

### 计划队列现状（截至 2026-06-02）

| 状态 | 数量 | 说明 |
|------|------|------|
| completed | 79 | Plans 5,10,14,16-26,27-31,33-39,41,43-56,58-66,67-95 |
| in progress | 2 | **Plan 32**（checkpoint 完整实现）、**Plan 42**（设计实现） |
| near completion | 1 | **Plan 06**（nop-code 功能补齐） |
| draft | 3 | Plans 11, 15, 57 |
| pending | 2 | Plans 08, 09 |
| reviewed | 1 | Plan 07 |
| 无状态标记 | 6 | Plans 01-04, 12, 13 |

### 需处置的计划分析

#### Plan 32 — nop-stream Checkpoint 完整实现

- **当前状态**：`in progress`
- **实际完成情况**：Phases 1-7 全部 `[x]`（除 Phase 2 中 1 项标注"留在 core 模块"的非阻塞项）
- **未完成项**：
  1. Closure Gates 中 `[ ] 独立子 agent closure-audit` — 未执行
  2. Phase 2 中 `[ ] GraphExecutionPlan.build() 不再有 .get(0) 单 chain 假设` — 标注为"涉及 StreamTaskInvokable 架构变更，当前场景无实际影响"
- **后续验证**：Plans 43-87（超过 25 份审计修复计划）对 checkpoint 子系统进行了逐行审查和修复，充分验证了 Plan 32 产出的正确性
- **处置方案**：执行 closure audit 并正式关闭

#### Plan 42 — nop-stream 设计实现计划

- **当前状态**：`in progress`
- **实际完成情况**：
  - Phase 0（StreamComponents/StreamRequirement）：工作项 9/9 `[x]`，Exit Criteria 6/8（2 项文档更新未做）
  - Phase 1（StateShard/StatePath）：工作项 7/7 `[x]`，Exit Criteria 9/11（2 项文档更新未做）
  - Phase 2（CheckpointParticipant/ProcessingGuarantee）：工作项 8/15 `[x]`，Exit Criteria 4/13 — **模型类已创建但核心协议集成未实现**
  - Phase 3（SourceWorkUnit/连接器协议）：工作项 4/10 `[x]` — **协议接口已创建但连接器适配未做**
  - Phase 4（EdgeConfig/MemoryBudget）：工作项 4/9 `[x]` — **配置模型已创建但运行时集成未做**
  - Phase 5（WindowingStrategy/AccumulationMode）：工作项 4/9 `[x]` — **窗口模型已创建但算子集成未做**
  - Phase 6（PartitionedPlan/DeploymentPlan）：工作项 5/9 `[x]` — **计划模型已创建但生成器未实现**
- **后续覆盖分析**（精确区分 covered vs uncovered）：

  **已被后续计划覆盖的工作**（Plan 32 定义的已有 checkpoint 架构的正确性修复）：
  - Checkpoint barrier 线程安全 → Plan 32 Phase 3 + Plans 43-87 验证
  - CheckpointCoordinator 生命周期 → Plan 32 Phase 2 + Plans 62-87 验证
  - JdbcCheckpointStorage 多数据库 → Plan 32 Phase 4
  - CheckpointMetrics 接入 → Plan 32 Phase 5
  - ReplayableSourceFunction → Plan 32 Phase 6
  - 错误处理、并发安全、测试覆盖 → Plans 62-87 全面修复

  **未被后续计划覆盖的工作**（Plan 42 定义的**新协议层**，模型类已创建但集成未实现）：
  - CheckpointCoordinator 使用 CheckpointParticipant 调度逻辑（Phase 2）
  - ProcessingGuarantee 对 barrier 行为的影响（STRICT_EXACTLY_ONCE 阻塞等）（Phase 2）
  - EpochManifest 替代 CompletedCheckpoint 作为持久化对象（Phase 2）
  - exactly-once 静态校验（Phase 2）
  - 连接器一致性能力声明与 ProcessingGuarantee 校验交互（Phase 3）
  - RecordWriter/InputGate 根据 EdgeConfig 选择流控行为（Phase 4）
  - WindowedStreamImpl windowingStrategyId 构造路径（Phase 5）
  - PartitionedPlanGenerator / DeploymentPlanGenerator 编译层（Phase 6）

- **处置方案**：标记为 superseded。Supersession Note 中明确区分 "模型类创建已完成" 和 "协议集成未实现且未被覆盖，属 future architecture work"

#### Plan 06 — nop-code 功能补齐实现计划

- **当前状态**：`near completion`（Phase 0-4 完成，P2/P3 剩余）
- **后续覆盖**：Plans 52（CRG 功能对齐）、55（深度审计 78 findings）、58（P0 bug 修复）、59（语义边模型）、69-95（8 轮审计修复）全面覆盖了 Plan 06 剩余的 P2/P3 工作
- **处置方案**：标记为 superseded

#### Plan 11 — nop-code 审查问题修正计划

- **当前状态**：`draft`
- **后续覆盖**：Plans 55, 58, 59, 69-95 全面覆盖
- **处置方案**：标记为 cancelled（superseded by Plans 55+）

#### Plan 09 — nop-code Stateless Design and GraphQL Convention Fix

- **当前状态**：`pending`
- **核心工作**：(1) CodeIndexService 从共享状态设计改为完全无状态设计（移除 ConcurrentHashMap，所有查询走 DB）；(2) GraphQL API 命名修正（findSymbols → findPage_symbols，getById → getBySymbolId）
- **后续覆盖分析**：Plans 88-95 对 CodeIndexService 做了大量审计修复（拆分、安全、OOM、数据完整性），但这些都是**在现有共享状态架构上的修复**，而非 Plan 09 所要求的**从共享状态到无状态的架构重构**。ConcurrentHashMap 字段（analysisResultsMap、callGraphMap）可能仍存在。GraphQL 命名修正（findSymbols → findPage_symbols）也未在后续计划中覆盖。
- **处置方案**：标记为 `deferred`（核心工作未被覆盖，仍有效但不在当前活跃队列。需要评估：当前共享状态架构是否仍构成并发安全问题，还是已被 Plans 88-95 的 per-indexId Lock + TTL 驱逐充分缓解）

#### Plan 07 — nop-code GraphQL Service Implementation Plan

- **当前状态**：`reviewed`
- **后续覆盖**：Plan 52 实现了 GraphQL service 层
- **处置方案**：标记为 superseded by Plan 52

#### Plan 15 — nop-job Invoker Implementation

- **当前状态**：`draft`
- **分析**：nop-job 模块已被 Plans 14-21 全面重写。Invoker 实现作为 Plan 16（核心调度器重构）的一部分或独立于当前活跃工作
- **处置方案**：保留为 `deferred`（未明确取代，但不阻塞当前工作）

#### Plan 57 — nop-stream Code Cleanup & Style Fixes

- **当前状态**：`draft`
- **分析**：import 排序、FQN、未使用依赖清理。仍有效但极低优先级
- **处置方案**：保留为 `deferred`（仍有效但延后）

#### 早期计划（01-04, 12, 13）— 无状态标记

- **分析**：这些是模板标准化前的早期计划，功能已实现或已被后续计划取代
- **处置方案**：标记为 `completed` 或 `superseded`（逐份判定）

### 后续计划裁定（Successor Adjudication）

以下延迟项在多份计划中反复标记为 "Successor Required: yes"，经裁定后确认**不需要创建后续计划**：

| 延迟项 | 出现计划 | 裁定结果 | 裁定理由 |
|--------|---------|---------|---------|
| CodeIndexService 拆分 | 88-95 | **No successor** | 已从 3033 行拆至 ~1510 行。进一步拆分属 diminishing returns（优化性质）。功能正确，无 correctness 影响 |
| nop-code-api 模块重组 | 89-95 | **No successor** | 空壳模块，不影响正确性。属架构清理优化，无紧迫性 |
| DRY 违规治理 | 89-95 | **No successor** | Plan 92 已通过 CodeSymbolConverter 修复三重复制。Plan 95 已验证修复 |
| Checkpoint 管线类型安全 | 85 | **No successor** | Plan 85 已添加运行时类型检查。完全类型安全需重设计整条管线，属架构演进 |
| RuntimeContext 完整 API | 85 | **No successor** | 需先做 API 设计文档。3 个核心方法已实现，剩余 ~27 个方法属增量扩展 |
| StreamRecord.replace 重构 | 85 | **No successor** | Apache Flink 继承的模式，影响整个算子链。运行时检查已足够防御 |
| MemoryKeyedStateBackend 类型安全 | 85 | **No successor** | 注册时类型记录已添加。完全重构需 StateRegistry<K,N,S> 设计，属架构演进 |
| Window 算子统一 | 86 | **Needs design doc first** | 需先做架构决策文档（WindowAggregationOperator vs WindowOperator 统一方案）。设计文档完成后需 successor plan。当前裁定：Successor Required: yes, Successor Path: design doc → successor plan |
| GraphExecutionPlan 隔离 | 82 | **No successor** | 优化性质，不影响正确性 |
| 分布式 runtime | 42 | **No successor** | 长期架构工作，超出当前开发周期 |
| 增量快照 | 32 | **No successor** | 优化性质，全量 JSON 序列化在 MemoryStateBackend 设计预期内 |

## Goals

1. 对 Plan 32 执行 closure audit 并正式关闭
2. 对 Plan 42 添加 supersession note，注明后续覆盖映射
3. 对 Plan 06 添加 supersession note
4. 对 Plans 07/11 标记为 superseded/cancelled，对 Plan 09 标记为 deferred（核心工作未被覆盖）
5. 对早期计划（01-04, 12, 13）添加状态标记
6. 对 Plans 15/57 确认 deferred 状态
7. 记录全部后续计划裁定结论，使 Plans 88-95 的 "Successor Required" 引用指向本计划的裁定

## Non-Goals

- 不执行任何代码变更（纯计划治理）
- 不创建技术性的后续计划（经裁定仅 Window 算子统一需要 design doc → successor plan，不在本计划 scope 内）
- 不修改已完成计划的实现内容（只修改状态和添加处置说明）
- 不对 `docs-for-ai/` 做内容更新（非功能性计划）

## Scope

### In Scope

- Plan 32：closure audit + 状态改为 completed
- Plan 42：supersession note（精确区分 covered/uncovered）+ 状态改为 superseded
- Plan 06：supersession note + 状态改为 superseded
- Plan 07：supersession note + 状态改为 superseded
- Plan 09：deferred note（核心工作未被覆盖）+ 状态改为 deferred
- Plan 11：cancellation note + 状态改为 cancelled
- Plan 15：状态改为 deferred
- Plan 57：状态改为 deferred
- Plans 01-04, 12, 13：状态标记
- 本计划：记录全部后续计划裁定

### Out Of Scope

- 代码变更
- 新技术计划创建
- docs-for-ai 内容更新

## Execution Plan

### Phase 1 — Plan 32 Closure Audit

Status: completed
Targets: `ai-dev/plans/32-nop-stream-checkpoint-complete-implementation.md`

- Item Types: `Follow-up`

- [x] 验证 Plan 32 所有 Phase exit criteria 在 live repo 中成立
- [x] 验证 Phase 2 遗留的 `.get(0)` 项确实无实际影响（确认当前代码中单 chain 假设的覆盖范围）
- [x] 验证后续 Plans 43-87 对 checkpoint 子系统的覆盖完整性
- [x] 写入 closure audit evidence
- [x] 将 Plan 32 状态改为 completed
- [x] 将 Phase 2 遗留 `.get(0)` 项移入 Deferred But Adjudicated

Exit Criteria:

- [x] Plan 32 状态为 completed，含完整 Closure Audit Evidence
- [x] Phase 2 `.get(0)` 项有明确的 non-blocking 理由
- [x] Closure Gates 中所有项已勾选或移入 Deferred
- [x] No owner-doc update required（纯计划治理，不涉及 docs-for-ai 内容变更）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — Plan 42 Supersession

Status: completed
Targets: `ai-dev/plans/42-nop-stream-design-implementation-plan.md`

- Item Types: `Follow-up`

- [x] 分析 Plan 42 每个 Phase 的 checked/unchecked 项，确定哪些被后续计划覆盖、哪些仍为 aspirational
- [x] 写入 supersession note，包含覆盖映射表
- [x] 将 Plan 42 状态改为 superseded

Exit Criteria:

- [x] Plan 42 状态为 superseded
- [x] Supersession Note 明确列出覆盖映射（Plan 42 Phase → 后续覆盖计划）
- [x] Supersession Note 明确列出**未被覆盖**的协议集成项，分类为 "future architecture work"
- [x] 未被覆盖的项有明确分类（out-of-scope / future architecture / needs design doc）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — Other Stale Plans Disposition

Status: completed
Targets: `ai-dev/plans/06-*.md`, `07-*.md`, `09-*.md`, `11-*.md`, `15-*.md`, `57-*.md`, `01-*.md` ~ `04-*.md`, `12-*.md`, `13-*.md`

- Item Types: `Follow-up`

- [x] Plan 06：添加 supersession note，状态改为 superseded
- [x] Plan 07：添加 supersession note（by Plan 52），状态改为 superseded
- [x] Plan 09：添加 deferred note（说明核心工作未被覆盖：无状态重构 + GraphQL 命名修正），状态改为 deferred
- [x] Plan 11：添加 cancellation note，状态改为 cancelled
- [x] Plan 15：状态改为 deferred
- [x] Plan 57：状态改为 deferred
- [x] Plans 01-04：逐份判定 → 均为 completed（功能在 live repo 中存在），已添加状态标记
- [x] Plans 12-13：逐份判定 → 均为 completed（功能在 live repo 中存在），已添加状态标记

Exit Criteria:

- [x] 所有处置的计划有正确的 `> Plan Status:` 标记
- [x] 所有 superseded/cancelled 计划有说明取代原因的 note
- [x] 所有 deferred 计划有说明延迟原因和未覆盖工作的 note
- [x] 无 `in progress` 状态的计划属于已完成的工作
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — Successor Adjudication Documentation

Status: completed
Targets: `ai-dev/plans/95-*.md`（或本计划自身的 Closure section）

- Item Types: `Decision`

- [x] 在本计划的 Closure section 中记录全部 successor adjudication 结论
- [x] 确认结论覆盖 Plans 88-95 中所有 "Successor Required: yes" 的延迟项
- [x] 确认无遗漏的高价值后续计划需求

Exit Criteria:

- [x] 每个 "Successor Required: yes" 项都有明确的裁定（No successor / Needs design doc first / Future work）
- [x] 裁定理由充分且可追溯
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Plan 32 已正式关闭（completed + closure audit evidence）
- [x] Plan 42 已标记取代（superseded + supersession note）
- [x] 所有陈旧计划（06/07/09/11/15/57/01-04/12/13）已处置
- [x] 全部 successor adjudication 结论已记录
- [x] 计划队列中无虚假的 `in progress` 状态
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `ai-dev/logs/` 对应日期条目已更新

## Deferred But Adjudicated

### Window 算子统一（WindowAggregationOperator vs WindowOperator）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 架构级决策，需先做设计文档评估统一方案。当前两种窗口算子各自功能正确，不影响 correctness
- Successor Required: yes
- Successor Path: design doc（`ai-dev/design/nop-stream/window-operator-unification.md`）→ successor plan

## Non-Blocking Follow-ups

- docs-for-ai nop-code 使用指南（Plan 90 提及的文档 gap，属文档改进任务，非正式计划）
- nop-stream P3 发现修复（20+ 项，属优化性质）
- CEP ClosureCleaner 等效机制
- fraud-example 端到端验证
- Plan 42 未覆盖的协议集成项（CheckpointParticipant 调度、ProcessingGuarantee barrier 行为等）作为 future architecture work，当 nop-stream 进入下一阶段开发时需重新评估

## Closure

Status Note: 全部 4 个 Phase 执行完毕。Plan 32 已正式关闭（completed + closure audit evidence）；Plan 42 已标记取代（superseded + supersession note 含精确覆盖映射）；12 份陈旧计划（06/07/09/11/15/57/01-04/12/13）已全部处置；11 项 successor adjudication 结论已记录。计划队列中无虚假 in-progress 状态。唯一需要 successor plan 的项是 Window 算子统一（需先做 design doc）。

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor (houyi agent, task_id: ses_17e9ab188ffeJo1USUpM3ZWNOm)
- Audit Session: 2026-06-01
- Evidence:
  - 全部 14 份被处置计划状态验证 PASS（与 Plan 96 声明一致）
  - 全部 11 项 successor adjudication 裁定理由充分且可追溯
  - Deferred 项分类诚实，无 in-scope live defect 被降级
  - Plan 32 有完整独立 closure audit evidence（houyi agent, task_id: ses_17ea98d65ffeKvA2efcgsGmIDn）
  - Plan 42 supersession note 精确区分 covered/uncovered
  - 计划队列无虚假 in-progress 状态

Follow-up:

- Window 算子统一 → Deferred But Adjudicated: design doc → successor plan（Plan 97 已创建）
- Plan 09 的无状态重构 + GraphQL 命名修正 → deferred，需评估当前是否仍需执行
- Plan 42 未覆盖的协议集成项 → future architecture work
- Plan 15 nop-job Invoker → deferred
- Plan 57 代码清理 → deferred
