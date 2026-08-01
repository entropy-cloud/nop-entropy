# nop-ai-agent Plan 门控与 DAG 调度运行时（W1-1/2/3）

> Plan Status: completed
> Mission: nop-ai-agent-harness-evolution
> Work Item: W1-1/W1-2/W1-3（Plan 门控 Gate + Trigger Rule + DAG 依赖调度；W1 前三项，最高优先）
> Last Reviewed: 2026-08-01
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md` §14.1-14.3、§7/§8/§12；`ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` W1-1/W1-2/W1-3
> Related: 后续 plan：W1-4 PlanReplanner（需 design-first）；调研 `2026-08-01-codewhale-workflow-ir-gate-analysis.md`、`2026-08-01-archon-yaml-dag-workflow-analysis.md`、`2026-08-01-jcode-dag-first-agent-analysis.md`；nop-task `GraphStepAnalyzer`（W1-3 复用）；`TeamTaskGraphBuilder`（同模式参考）

## Purpose

为 Agent Plan DSL 建立运行时门控与调度层（从零）：阶段验收 Gate（on-fail retry/block/escalate）、任务依赖 Trigger Rule（4 种）、DAG 依赖环检测（复用 nop-task `GraphStepAnalyzer`）。当前 plan 模块**只有数据 schema（model 层），无任何运行时层**——本计划建立 plan 校验/门控/就绪计算运行时，让 plan 从"可声明的结构化协议"变成"加载时可校验、阶段切换时可门控、可按 DAG+trigger 计算就绪"的执行控制对象。**W1-4 PlanReplanner（停滞重规划）需独立 design 后另行立 plan，不在本计划。**

## Current Baseline

> 已逐条核对 live repo（含包结构、字段、行号、grep 0 命中核实），非沿用旧文档。

**已存在**：

- `agent-plan.xdef`（`nop-kernel/nop-xdefs/.../_vfs/nop/schema/ai/agent-plan.xdef`，136 行）：`<phase>` 有 `name/kind/status/startedAt/completedAt` + `description/targets/exitCriteria/tasks`；`<task taskNo title dependsOn="csv-set" ...>` —— **`dependsOn` 属性已在 xdef（L86）声明**。`AgentPlanCriterion` 结构（`id`/`completed`/`required`/`blocking` + 文本内容）已存在，phase 的 `exitCriteria` 与 task 的 `checks` 都用它。
- 生成模型 `_AgentPlanTaskModel` 已有 `_dependsOn : Set<String>`（`:38,159-169`，序列化 `:352`）。`_AgentPlanPhase` 有 `exitCriteria`/`tasks` KeyedList，**无任何 gate 字段**。
- `AgentExecStatus` 枚举（`:16-54`）已含 `escalated`（L29）——Gate `on-fail="escalate"` 目标状态就绪。
- nop-task `GraphStepAnalyzer`（`nop-task/nop-task-core/.../builder/GraphStepAnalyzer.java`，193 行）可用：`analyze(IGraphTaskStepModel)` → `Dag.analyze()` + `Dag.containsLoop()`（`nop-core/.../graph/dag/Dag.java:67-69`），环存在抛 `ERR_TASK_GRAPH_STEP_CONTAINS_LOOP` 含 loopEdges。已被 team-task 复用（`TeamTaskGraphBuilder.java:126`），**未被 plan 路径复用**。

**真正剩余的 gap（运行时层完全不存在）**：

- **W1-1（Gate）**：`agent-plan.xdef` `<phase>` **无** `<gate>` 子元素；`_AgentPlanPhase` **无** gate/onFail/maxRetries 字段。无 `PlanRunner`（全仓 grep 0 命中）。
- **W1-2（Trigger Rule）**：`<task>` **无** `triggerRule`；无 `TriggerRule` 枚举；无 `PlanScheduler`（plan 路径就绪计算不存在；team-task 的 `TeamTaskTopology.getReadyTasks()` 是另一套）。
- **W1-3（DAG）**：`dependsOn` **模型属性已存在但无运行时校验**——无代码把 `dependsOn` 桥接到 `Dag`/`GraphStepAnalyzer`。
- **结构事实**：`io.nop.ai.agent.plan` 包**只有** `model/` + `model/_gen/`，无 runtime 子包。
- **接线缺口**：无生产代码消费 AgentPlan 执行（`AgentExecutionContext` 持有 plan 但引擎不调 gate/scheduling）。仅 `TestAgentPlanMarkdownLoader`（测试用）加载 plan。本计划须建立至少一个真实调用点（load 时校验）。

## Goals

- Plan DSL 能在 `<phase>` 声明 `<gate>`（on-fail retry/block/escalate + max-retries + require-explicit-verdict），运行时 `PlanRunner.checkGate()` 按明确语义判定放行。
- Plan DSL 能在 `<task>` 声明 `triggerRule`（4 种），运行时 `PlanScheduler` 按 trigger + 全局 DAG 拓扑计算就绪任务集。
- `dependsOn` 声明的 DAG 在加载时经 nop-task `GraphStepAnalyzer` 做环检测（有环 fail-fast）；校验接入真实 plan 加载路径（`AgentPlanValidator`），不是孤立组件。
- 零空壳：`AgentPlanValidator` 有真实调用点；`PlanRunner`/`PlanScheduler` 经生命周期模拟测试验证（驱动 plan 实例经历门控 + 就绪计算）。

## Non-Goals

- PlanReplanner（W1-4 停滞检测重规划）——**需独立 design（§14.4 仅有方向无算法：停滞输入信号、回退/拆分的状态突变语义、幂等契约均未定义），另行立 plan**。
- 把 plan 任务实际交给 nop-task `GraphTaskStep`/`ChooseTaskStep` 执行（design §14.5 执行层复用——更大范围；本计划只做声明层 + 校验/门控/就绪运行时）。
- 引擎全量集成 checkGate/readiness 到现有 agent engine 执行主循环（若引擎无现成 phase-transition hook，本计划交付运行时库 + load 时校验接入；引擎主循环集成列为 follow-up）。
- Markdown↔XML plan 互转。
- mission-driver plan-execution subflow 改造。

## Scope

### In Scope

- W1-1：`agent-plan.xdef` `<phase>` 增 `<gate>`（on-fail/max-retries/require-explicit-verdict + criterion）；`AgentPlanGate` model；`PlanRunner.checkGate()` 运行时（明确判定语义见 Phase 1）。
- W1-2：`agent-plan.xdef` `<task>` 增 `triggerRule`；`TriggerRule` 枚举（4 值）；`PlanScheduler` 就绪计算（全局 DAG + trigger）。
- W1-3：`dependsOn` → nop-task `Dag`/`GraphStepAnalyzer` 环检测桥；`AgentPlanValidator`（加载时校验：环检测 + 结构校验 + 悬空依赖），接入 plan 加载路径。
- W1-4：**不在本计划**（successor）。

### Out Of Scope

- W1-4 PlanReplanner（successor，需 design-first）。
- plan 任务交给 nop-task 执行引擎（design §14.5）。
- 引擎执行主循环的 phase-transition 集成（follow-up）。
- W2e 及后续全部 work items。

## Execution Plan

> 本计划修改 `agent-plan.xdef`（在 `nop-kernel/nop-xdefs`）。改 xdef 后正确流程是**两步**：①`./mvnw install -pl nop-kernel/nop-xdefs -am -DskipTests` 把新 xdef jar 装入本地仓库；②`./mvnw clean compile -pl nop-ai/nop-ai-agent -am` 触发 nop-ai-agent 从新 jar 重新生成 `_gen` 模型并编译。仅做①不会重新生成下游 `_gen`。每个改 xdef 的 phase 验证步骤含这两步。

### Phase 1 - Phase Gate 门控（W1-1）

Status: completed
Targets: `nop-kernel/nop-xdefs/.../_vfs/nop/schema/ai/agent-plan.xdef`、`nop-ai/nop-ai-agent/.../plan/model/`（AgentPlanGate）、`nop-ai/nop-ai-agent/.../plan/runtime/`（PlanRunner，新建子包）

- Item Types: `Decision | Fix | Proof`

- [x] `agent-plan.xdef` `<phase>` 增 `<gate>` 子元素：属性 `on-fail`（枚举 retry|block|escalate）、`max-retries`（int）、`require-explicit-verdict`（boolean）；子节点 `criterion`（复用 AgentPlanCriterion）。`on-fail` 枚举类名/包名在 xdef 中明确声明（参照现有 `status="enum:io.nop.ai.agent.model.AgentExecStatus"` 模式）
- [x] 两步重打包：①`./mvnw install -pl nop-kernel/nop-xdefs -am -DskipTests` ②`./mvnw clean compile -pl nop-ai/nop-ai-agent -am`；确认 `_AgentPlanPhase` 生成 gate 字段 + `_AgentPlanGate` 生成
- [x] 新建 `PlanRunner`（plan runtime 子包），`checkGate(phase)` 实现明确判定语义（见下）
- [x] 单测覆盖判定语义全部分支（见 Exit Criteria）

**Gate 判定语义（行为规格，本 phase 须实现并测试）**：
- 一个 criterion"满足"= 其 `completed == true`（`completed` 是 plan 状态的一部分，由执行实体在完成对应工作时设置；criteria 对应 phase 的 exitCriteria/checks 项）。
- gate 通过 = 所有 `required=true` 的 criterion 均满足。
- `blocking=true` 的 criterion 未满足 → gate 硬失败（无论 required）。
- `on-fail=retry`：gate 失败 → 回到本阶段重试（受 max-retries 限，达上限后按 escalate 处理）；`on-fail=block`：阻塞后续阶段；`on-fail=escalate`：置 `AgentExecStatus.escalated`。
- `require-explicit-verdict=true`：gate 不允许仅凭 criterion.completed 自动通过——须有显式 verdict 记录（gate 级字段，由非执行者来源设置；本 phase 定义该字段语义并 fail-fast 防自动通过）。

Exit Criteria:

- [x] `agent-plan.xdef` `<phase>` 可声明 `<gate on-fail=... max-retries=... require-explicit-verdict=...>`；两步重打包后 `_AgentPlanPhase` 含 gate 字段、`_AgentPlanGate` 存在
- [x] `PlanRunner.checkGate()` 存在并能加载带 gate 的 plan 实例运行判定
- [x] **判定语义全覆盖（新功能测试，Rules #25）**：gate 全 required 满足→放行；某 required 未满足→失败；blocking 未满足→硬失败；on-fail=retry→回本阶段且 attempt+1（达 max-retries→escalate）；on-fail=block→阻塞；on-fail=escalate→status=escalated；require-explicit-verdict=true 无显式 verdict→不自动通过
- [x] **无静默跳过**：require-explicit-verdict=true 无 verdict 时显式阻止（抛异常或返回 block），不静默放行
- [x] design `nop-ai-agent-plan-dsl.md` §14.1（Gate）落地标注 + §7/§8 运行时强校验建议同步（gate 阻断结束语义）
- [x] `ai-dev/logs/2026/08-01.md` 已追加本 phase 进展

### Phase 2 - Trigger Rule + DAG 就绪计算与加载时校验（W1-2 + W1-3）

Status: completed
Targets: `nop-kernel/nop-xdefs/.../_vfs/nop/schema/ai/agent-plan.xdef`、`nop-ai/nop-ai-agent/.../plan/model/`（TriggerRule）、`nop-ai/nop-ai-agent/.../plan/runtime/`（PlanScheduler + AgentPlanValidator + 环检测桥）、`nop-task/nop-task-core/.../builder/GraphStepAnalyzer.java`（复用不改）

- Item Types: `Decision | Fix | Proof`

- [x] `agent-plan.xdef`：`<task>` 增 `triggerRule` 属性（默认 all_success）；新增 `TriggerRule` 枚举（包路径钉为 `io.nop.ai.agent.plan.model.TriggerRule`，与 gate model 同包，xdef 用 `enum:io.nop.ai.agent.plan.model.TriggerRule`）。两步重打包确认 `_gen` 含 triggerRule
- [x] **DAG 作用域裁定（全局扁平化 + subTasks 计入）**：design §14.3 标题"跨 phase 图结构"——DAG 是**全局**的：递归扁平化所有 phase 的 task **及 subTasks**（subTasks 是递归的 `xdef:ref`，计入 DAG；跨 subTask 的 dependsOn 有效）。即 `plan.getPhases()` 递归收集全部 task（含 subTask）为一个图。**taskNo 须全局唯一**（加入结构校验）。跨 phase / 跨 subTask 依赖均有效
- [x] 环检测桥：把全局扁平化后的 `dependsOn` 映射为 nop-task `Dag`/`IGraphTaskStepModel` 结构，调 `GraphStepAnalyzer.analyze()`（复用，同 `TeamTaskGraphBuilder` 模式）。**关键**：`GraphStepAnalyzer.analyze()` 要求 `enterSteps`/`exitSteps` 非空否则抛 NO_ENTER_STEPS——须参照 `TeamTaskGraphBuilder`（L108-123）从 `dependsOn` 集合差集**派生** enterSteps（无入度节点）/exitSteps（无出度节点）。有环 fail-fast（抛含 loopEdges 异常）；dependsOn 引用不存在的 taskNo → fail-fast；空 plan（无 task）与全依赖 plan（无源节点）定义明确 fail-fast 行为
- [x] `PlanScheduler`：按 trigger rule + 全局 DAG 拓扑计算就绪任务集（all_success=所有依赖 completed；one_success=任一 completed；none_failed_min_one_success=至少一 completed 且无 failed；all_done=所有依赖结束无论成败）
- [x] **加载时校验接入（真实调用点，钉死机制）**：Override `AgentPlan.validate()`（`AgentPlan.java` 是 9 行空壳继承 `_AgentPlan`→`AbstractComponentModel`，其 `validate()` 被 `ResourceComponentManager` 在加载 `.agent-plan.xml` 后自动调用）→ 委托 `AgentPlanValidator.validate(this)`。validate 含：环检测 + 结构校验（currentPhase 存在、taskNo 全局唯一、dependsOn 悬空检测）。**不另建并行 loader**——直接用现有 xdsl-loader + `AgentPlan.validate()` 接入点
- [x] 单测：4 种 trigger 就绪计算；有环 plan→抛环异常（含 loopEdges）；悬空 dependsOn→抛异常；跨 phase 依赖在全局 DAG 中有效

Exit Criteria:

- [x] `agent-plan.xdef` `<task>` 可声明 `triggerRule`；两步重打包后 `_AgentPlanTaskModel` 含该字段；`TriggerRule` 枚举存在
- [x] 环检测桥存在：加载含 dependsOn 环的 plan → 抛异常（fail-fast，含 loopEdges）；复用 nop-task `GraphStepAnalyzer`（不重写环算法）；悬空依赖→抛异常；跨 phase 依赖在全局 DAG 有效
- [x] `PlanScheduler` 按 4 种 trigger 正确计算就绪任务集（4 种各有测试）
- [x] **接线验证（真实调用点）**：`AgentPlanValidator.validate(plan)` 在 plan 加载路径被调用（测试断言：加载含环 plan 时被 validate 拒绝，而非静默接受；即 validate 确实接入加载路径）
- [x] **无静默跳过**：环/悬空依赖/trigger 不满足均显式 fail-fast
- [x] **新功能测试**：4 trigger + 环检测 + 悬空依赖 + 跨 phase 依赖各有测试
- [x] design `nop-ai-agent-plan-dsl.md` §14.2/§14.3 落地标注；§12（"无依赖拓扑执行器"）更新为已建立；DAG 全局扁平化裁定文档化
- [x] `ai-dev/logs/2026/08-01.md` 已追加本 phase 进展

## Closure Gates

> 本计划涉及代码变更，构建验证条目保留。

- [x] plan 运行时层从零建立：`io.nop.ai.agent.plan` 下新增 runtime 子包（runner/scheduler/validator），不再是纯 model 包
- [x] Gate 门控（retry/block/escalate + max-retries + require-explicit-verdict）行为正确：判定语义全分支有测试
- [x] Trigger Rule + 全局 DAG 就绪计算可用：4 trigger 正确 + 环检测 fail-fast + 跨 phase 依赖有效（测试覆盖）
- [x] `AgentPlanValidator` 接入真实加载路径（Anti-Hollow：经 `AgentPlan.init()` → `INeedInit` hook 接入，加载时被调用，测试断言环 plan 被加载拒绝）
- [x] `PlanRunner`/`PlanScheduler` 经生命周期模拟测试验证（驱动 plan 实例经历门控 + 就绪计算；**注：生产执行主循环集成属 follow-up，非本 plan closure 门槛**）
- [x] **交付范围限定（诚实预期）**：本 plan 交付门控/调度**运行时库** + 加载时校验（`AgentPlanValidator` 经 `AgentPlan.init()` 真实接入）。gate criterion 的 `completed`/verdict 的生产侧写入者、checkGate/readiness 接入 agent engine 执行主循环，均属 engine-integration follow-up——closure 时 gate 的生产门控力尚未兑现（仅库 + 模拟测试层），closure audit 据此判定而非按 Purpose 全语义
- [x] 无空壳/静默跳过：环/悬空/replan 不可行均 fail-fast；require-explicit-verdict 禁自动通过
- [x] xdef 两步重打包完成（`_gen` 含新增字段）
- [x] 受影响 owner docs（`nop-ai-agent-plan-dsl.md` §14.1-14.3、§7/§8/§12）已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证 `AgentPlanValidator` 经 `AgentPlan.init()` → `INeedInit` 真实接入加载路径（测试断言环 plan 被加载拒绝，非孤立组件）；`PlanRunner`/`PlanScheduler` 经生命周期模拟测试验证（驱动 plan 实例经历门控+就绪，不只是类型存在）
- [x] `./mvnw install -pl nop-kernel/nop-xdefs -am -DskipTests` 通过（xdef 重打包①）
- [x] `./mvnw clean compile -pl nop-ai/nop-ai-agent -am` 通过（重生成 _gen + 编译②）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] `./mvnw compile`（全量）通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### W1-4 PlanReplanner（停滞检测重规划）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: design §14.4 仅有方向（对标 spec-kit/browser-use/codewhale）无算法——停滞检测输入信号（何为"失败"、阈值、时间窗）、回退的状态突变语义（重置 currentPhase？task 状态？）、任务拆分机制（subTasks？新同级 task？）、幂等契约（输入状态 hash？决策输出格式？）均未定义。直接立 plan 会产出执行者各自臆想的实现、且 closure audit 无法客观验证。W1-1/2/3（声明 + 校验 + 门控 + 调度）独立成立且是 W1-4 的前置（重规划依赖 gate 与 DAG 结构）。本计划关闭不依赖 W1-4。
- Successor Required: yes
- Successor Path: 后续 plan（W1-4）。**前置条件**：先产出 PlanReplanner 的 design（§14.4 补齐算法：停滞输入、决策契约、状态突变、幂等），再立 plan。

### plan 任务交给 nop-task GraphTaskStep 执行（design §14.5）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: design §14.5 分"声明层（本 DSL）"与"执行层（nop-task）"。本计划只做声明层 + 校验/门控/就绪运行时；执行层迁移是更大范围工作。
- Successor Required: yes
- Successor Path: 后续 harness evolution 计划（design §14.5 执行层复用）

## Non-Blocking Follow-ups

- 引擎执行主循环的 phase-transition 集成：本计划交付 PlanRunner/PlanScheduler 运行时库 + load 时 AgentPlanValidator 接入。把 checkGate/readiness 接入 agent engine 执行主循环（若引擎无现成 phase-transition hook）是后续接线工作——不阻塞本计划 closure（运行时库 + validator 接入已 Anti-Hollow 合规）。
- `PlanScheduler` 与 team-task `TeamTaskTopology.getReadyTasks()` 就绪计算是否可抽取共用——观察项。
- gate criterion 是否支持 XPL 表达式判定（当前 AgentPlanCriterion 是 completed 标志 + 文本）——增强项。

## Closure

Status Note: Plan 运行时门控与 DAG 调度层已从零建立。W1-1（Gate 门控）、W1-2（Trigger Rule）、W1-3（DAG 环检测 + 加载时校验）全部落地。`AgentPlanValidator` 经 `AgentPlan.init()` → `INeedInit` hook 接入真实 xdef 加载路径（非孤立组件）。`PlanRunner`/`PlanScheduler` 经生命周期模拟测试验证（驱动 plan 实例经历门控 + 就绪计算）。W1-4 PlanReplanner 延期到 successor（需独立 design）。
Completed: 2026-08-01

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (session ses_043740369ffejvps57N1837sHF)
- Audit Session: ses_043740369ffejvps57N1837sHF
- Evidence:
  - Phase 1 Exit Criteria: all PASS — `agent-plan.xdef:82-89` has `<gate>` with on-fail/max-retries/require-explicit-verdict/verdict + criteria；`_AgentPlanGate.java` 全字段生成；`_AgentPlanPhase` 含 `_gate` 字段；`PlanRunner.checkGate()` 6 种 Outcome 全覆盖（TestPlanRunnerGateSemantics 15 测试 0 failures）；require-explicit-verdict=true 无 verdict → EXPLICIT_VERDICT_REQUIRED（不静默放行，PlanRunner.java:90-93 在 criteriaPass 检查之前返回）
  - Phase 2 Exit Criteria: all PASS — `TriggerRule` 枚举 4 值；`PlanDagBuilder` 调真实 `GraphStepAnalyzer.analyze()`（非本地重写，PlanDagBuilder.java:150）；递归扁平化 subTasks（collectTasksRecursive）；dangling/cyclic/duplicate 均 fail-fast；`PlanScheduler` 4 trigger 各有 ready/not-ready 测试（TestPlanScheduler 12 测试 0 failures）；TestPlanDagBuilder 11 测试 0 failures（环/悬空/跨 phase/subTask/重复 taskNo）
  - 接线验证（Anti-Hollow）: PASS — `AgentPlan.java:7` implements `INeedInit`，`init()` 调 `AgentPlanValidator.validate(this)`；`TestAgentPlanValidatorLoading` 经 `ResourceComponentManager.instance().loadComponentModel()` 加载（真实生产路径）；cyclic plan → assertThrows（rejected）；dangling-deps plan → assertThrows（rejected）；valid cross-phase plan → loads successfully（3 测试 0 failures）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码为 0（所有 checklist 已勾选 + Closure Evidence 已写入）
  - Anti-Hollow 检查结果：`scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0，0 findings
  - Deferred 项分类检查：W1-4 PlanReplanner + plan→nop-task 执行层迁移均为已裁定的 successor，无 in-scope live defect 被降级

Follow-up:

- 引擎执行主循环的 phase-transition 集成（checkGate/readiness 接入 agent engine）
- `PlanScheduler` 与 `TeamTaskTopology.getReadyTasks()` 是否可抽取共用——观察项
- gate criterion 是否支持 XPL 表达式判定——增强项
- W1-4 PlanReplanner（需独立 design：停滞输入信号、决策契约、状态突变、幂等）
