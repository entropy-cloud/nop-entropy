# nop-ai-agent PlanReplanner ROLLBACK_PHASE/SPLIT_TASK 运行时（W1-4 successor）

> Plan Status: active
> Mission: nop-ai-agent-harness-evolution
> Work Item: W1-4 余（ROLLBACK_PHASE 阶段回退 + SPLIT_TASK 任务拆分运行时 enactment；前置 plan 1505-2 只交付 design + ESCALATE/CONTINUE，本计划收口 W1-4）
> Last Reviewed: 2026-08-01
> Draft Review: round-1 独立子 agent 审查发现 1 Blocker（executor 单向控制流使 ROLLBACK 端到端不可能）+ 4 Major（SPLIT scheduler/executor 过滤层、GateOnFail 事实错误、SPLIT DSL 无落点、载荷爆炸半径）+ 1 Minor（ABORT 含糊），全部已修。round-2 独立子 agent 审查 verdict READY FOR ACTIVE（5 项全部 RESOLVED，新发现 4 Minor 不阻断）。共识达成。
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md` §14.4（`:483-547`，含 §14.4.2 决策契约 + §14.4.3 状态突变语义 + freeze 裁定）；`ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` W1-4
> Related: 前置 plan `2026-08-01-1505-2`（W1-4 首切，已 completed，本计划是其显式 Deferred But Adjudicated successor）；reliability §13.3 / W2-3 三级失败升级（边界已在 §14.4.5 reconcile，本计划不实现单 attempt 重试）

## Purpose

收口 W1-4：把前置 plan 定义但延后的 `ROLLBACK_PHASE`（阶段回退）与 `SPLIT_TASK`（任务拆分）运行时 enactment 落地，使 plan 级重规划决策空间（§14.4.2 的 5 值）全部可达、全部有真实 enactment（不再 `UnsupportedOperationException`）。完成后 roadmap `[ ] W1-4` 可勾选。

## Current Baseline

> 已逐条核对 live repo（独立 explore 子 agent 报告 ses_043019418ffeFXtTOYb7KTemph）。路径相对仓库根。

**已落地（W1-4 首切，plan 1505-2，核对属实）**：

- `PlanReplanner`（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/plan/runtime/PlanReplanner.java`，116 行）：
  - `decide(StagnationEvent)`（`:36`）与 `decide(List<StagnationEvent>)`（`:55`）—— **只产 `CONTINUE` 或 `ESCALATE`**（GATE_EXHAUSTED/TASK_STALLED/REPEATED_ERRORS → ESCALATE；空 → CONTINUE）。
  - `apply(ReplanDecision, PlanExecutionState)`（`:76`）：`CONTINUE` no-op（`:84-85`）；`ESCALATE` 置 `planStatus`+`currentPhase` 的 phase status 为 `escalated`（`:86-92`）。
  - **`ROLLBACK_PHASE`（`:93-95`）/`SPLIT_TASK`（`:96-98`）/`ABORT`（`:99-101`）抛 `UnsupportedOperationException`**（消息含 design §14.4.3 引用，非静默跳过）。
- `ReplanDecision`（`.../plan/runtime/ReplanDecision.java`，39 行）—— **纯 `enum`，无字段、无构造器、无 per-value 载荷**。5 值：CONTINUE/ESCALATE/ROLLBACK_PHASE/SPLIT_TASK/ABORT。
- `PlanExecutionState`（`.../plan/runtime/PlanExecutionState.java`，216 行）—— **可变运行时覆盖层**（镜像冻结模板之上的 task/phase status + 错误记录）：`taskStatus`/`phaseStatus`/`currentPhase`（`:40-50`）、`errors`（`:164` recordError）、`taskConsecutiveFailures`/`taskAttempts`、`gateExhaustedPhases`。冻结 xdef 模板永不被突变（§14.4.3 裁定）。
- `PlanExecutor.respondToStagnation`（`.../plan/runtime/PlanExecutor.java:204-218`）：`detector.detect(state)`（`:207`）→ `replanner.decide(events)`（`:212`）→ `replanner.apply(decision, state)`（`:214`）。
- `StagnationDetector`（`.../plan/runtime/StagnationDetector.java`，123 行）：产 `GATE_EXHAUSTED`/`TASK_STALLED`/`REPEATED_ERRORS`（`:74-100`）。
- `StagnationEvent`（99 行）已带 `targetPhase`/`targetTaskNo`/`count`/`reason` + `idempotencyKey()`（`:71`，wall-clock 排除）。
- 测试固定 not-yet-implemented：`TestPlanReplanner.apply_rollbackPhase_throwsUnsupported_noSilentSkip`（`:107`）、`apply_splitTask_throwsUnsupported_noSilentSkip`（`:116`）—— successor 须更新。

**真正剩余的 gap（逐项核实）**：

1. `ReplanDecision` 是裸枚举——**无决策载荷**（目标 phase/task、子任务规格、理由）。ROLLBACK 需目标 phase、SPLIT 需目标 task + 子任务描述，apply() 今日只收 enum 无法获知目标。design §14.4.2 要求"决策载荷 = 决策类型 + 目标 phase/task + 触发信号类型 + 理由"。
2. `decide()` 今日**永不产 ROLLBACK/SPLIT**——三信号恒映射 ESCALATE。ROLLBACK/SPLIT 的触发条件 design §14.4.2 标"由 successor 定义"，未定义。
3. **`PlanExecutor.execute()` 控制流是单向的，不支持回退后重入**（关键阻断项）：phase 循环 `for (int phaseIdx = startIdx; phaseIdx < phases.size(); phaseIdx++)`（`PlanExecutor.java:99`）只前进不回退；`respondToStagnation`（`:204-218`）对**任何**非空停滞事件都返回 `StagnationResponse`（`:215-217`），调用方 `drivePhaseTasks`（`:109-111`）/`checkPhaseGate`（`:114-116`）立即 `return stop.result`，execute() 随即返回。**即使 `apply()` 把 `currentPhase` 移回 phase-1，execute() 也在该停滞点终止返回，单向 for 循环不会回到更早 phaseIdx**。故 ROLLBACK 端到端（"回退后重新推进可达后续 phase"）在今日控制流下**不可能**——须改造 executor：ROLLBACK 不终止（区别于 ESCALATE 终止）+ phase 循环可按 `currentPhase` 重入/回跳 + cycle-safety bound 计数回退防死循环。
4. `ROLLBACK_PHASE` enactment 不存在：需重置目标 phase 的 task status（completed→pending）+ phase status 回退 + 标记错误 resolved。
5. **SPLIT 集成面被低估（阻断项）**：`PlanScheduler.getReadyTasks(plan, statusProvider)` 的任务**结构**来自 `new PlanDagBuilder().collectAllTasks(plan)`（读冻结模板 `plan.getPhases()...getTasks()`）；`statusProvider` 只是状态函数非结构来源。`PlanExecutor.readyTasksForPhase`（`:220-230`）调 `scheduler.getReadyTasks(state.getPlan(), ...)` 后用 `collectTaskNos(phase)`（`:232-236`，读冻结 phase）过滤：`phaseTaskNos.contains(task.getTaskNo())`。**故即使给 PlanExecutionState 加运行时子任务节点，scheduler 看不到其结构（dependsOn/triggerRule/taskNo），且 executor 的 phase 过滤会排除它**。SPLIT 须同时改：结构来源（从冻结模板改为可读运行时 overlay）+ executor phase 过滤层（识别运行时新增节点）。
6. **触发机制无落点**：`GateOnFail = {retry, block, escalate}`（`GateOnFail.java:13-19`）——**无 `rollback`、无 `split` 值**。gate `on-fail` 不是现成可复用的触发通道；新增枚举值会触发 `_AgentPlanGate.java` 重新生成 + xdef + `GateCheckResult` 改造（Protected Area 模型变更）。须裁定 ROLLBACK/SPLIT 触发的声明落点（候选：plan 级 `<replanPolicy>` 配置，而非 gate on-fail 枚举扩展——避免模型变更）。
7. **SPLIT 子任务规格无 DSL 落点**：全仓库无 `splittable` 字段、无拆分模板 schema。"预定义拆分模板"是一个**尚不存在**的 DSL 特性，须新建 xdef 节点/字段（Protected Area）或等价声明机制。
8. **决策载荷改造爆炸半径**：`PlanExecutionResult.getDecisionsEnacted()` 是 `List<ReplanDecision>`（`PlanExecutor.java:92,122,129,213`）；`TestPlanExecutorEndToEnd` 的 `CountingReplanner` 与 `result.getDecisionsEnacted().get(0)` 断言（`TestPlanReplanner` 多处 `assertEquals(ReplanDecision.ESCALATE, decide(...))`）——若 `decide()` 返回类型变为结果对象，**全部编译失败**。须预先裁定载荷形态（结果对象 vs apply 传事件）并枚举级联改造。
9. `AgentPlanError.resolvedAt`（`_gen/_AgentPlanError.java`，字段声明区）**全模块零业务 writer**（仅生成 setter + clone）——ROLLBACK 标记错误 resolved 将是首个 writer。

## Goals

- **决策载荷落地**：引入决策结果对象（`ReplanDecisionResult`：枚举 + 目标 phase/task + 触发信号 + 理由），使 `apply()` 能获知 enactment 目标（design §14.4.2）。**预先裁定**采用结果对象（而非 apply 传事件），因 `PlanExecutionResult.getDecisionsEnacted()` 须记录决策——结果对象使记录含载荷（可观测）。保持 `decide()` 幂等性不变（相同停滞状态 → 相同决策+载荷）。
- **executor 控制流改造**（ROLLBACK 前置）：ROLLBACK 是**可恢复重规划**（不终止），区别于 ESCALATE（终止）。改造 `PlanExecutor.execute()`：ROLLBACK 决策不返回终止响应 + phase 循环可按 `currentPhase` 重入/回跳 + cycle-safety bound 计数回退防死循环。这是 ROLLBACK 端到端"回退后重新推进"的前提。
- **触发机制落点**：裁定 ROLLBACK/SPLIT 触发声明形态——**优先 plan 级 `<replanPolicy>` 配置**（避免 `GateOnFail` 枚举扩展的模型变更），在停滞信号之上定义何时产 ROLLBACK vs SPLIT vs ESCALATE（可配置，留生产调参）。须与 ESCALATE（不可恢复升级）区分。
- **ROLLBACK_PHASE enactment**：重置目标 phase 的 task status（completed→pending）+ phase status 回退 + 标记该 phase 累积错误 resolved（`AgentPlanError.resolvedAt` 首个 writer）+ 移回 currentPhase。
- **SPLIT_TASK enactment + 集成面**：向 `PlanExecutionState` 运行时副本插入子任务节点（保留原 task 占位）+ 子任务初始 pending；**同步改造 scheduler 结构来源（从冻结模板改为可读运行时 overlay）+ executor phase 过滤层**（识别运行时新增节点，否则子任务死节点）。子任务规格须有 DSL 落点（裁定声明机制）。
- **端到端可观测**：加载 plan → 推进 → 注入可回退停滞 → replanner 产 ROLLBACK → executor 不终止、回退 phase → 重新推进可达后续 phase（非死锁）。

## Non-Goals

- **nop-task 执行层迁移（§14.5）**——独立大型 successor（前置 plan Deferred），本计划在自建 `PlanExecutor` host 上实现 enactment。
- **W2-3 三级失败升级**——单 attempt 内失败（§14.4.5 边界已 reconcile），属 W2-3 work item。
- **ABORT 运行时**——明确 **out-of-scope**（design §14.4.2 保留决策槽位；ABORT 语义 = 置 planStatus=aborted 并终止，与 ESCALATE 终止模式同构，可在 ESCALATE 路径顺带覆盖，但不扩展 ABORT 独立语义）。本计划保持 ABORT 抛 `UnsupportedOperationException`（不静默跳过），留 successor。
- **生产调参**——阈值留可配置项，调参非阻塞（Non-Blocking Follow-up）。
- W2（checkpoint）、W3+ 全部。

## Scope

### In Scope

- 决策载荷对象引入 + `decide()`/`apply()` 签名适配 + 幂等性保持（含载荷的 hash）。
- ROLLBACK_PHASE / SPLIT_TASK 触发条件定义（可配置映射）。
- ROLLBACK_PHASE enactment（task status 重置 + phase 回退 + 错误 resolved + currentPhase 移回）。
- SPLIT_TASK enactment（运行时副本子任务插入）。
- `AgentPlanError.resolvedAt` 首个 writer（ROLLBACK 时标记）。
- 端到端验证 + 零回归（CONTINUE/ESCALATE 行为不变）。

### Out Of Scope

- nop-task 迁移（§14.5 successor）。
- W2-3 单 attempt 失败升级。
- W2 checkpoint / W3+ 全部。
- 生产阈值调参。

## Execution Plan

### Phase 1 - 决策载荷 + executor 控制流改造 + ROLLBACK_PHASE enactment（Fix | Decision | Proof）

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/plan/runtime/ReplanDecision.java`（载荷引入）、`PlanReplanner.java`（decide 产 ROLLBACK + apply enactment）、`PlanExecutionState.java`（rollback 辅助 + resolvedAt writer）、`PlanExecutor.java`（控制流改造 + decide→apply 适配载荷）、`PlanExecutionResult.java`（`getDecisionsEnacted()` 类型迁移）、`ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`（§14.4.2 载荷 + 触发条件回写）

- Item Types: `Fix | Decision | Proof`

- [ ] **Decision（预先裁定）：决策载荷形态 = `ReplanDecisionResult`**。引入结果对象 `ReplanDecisionResult(ReplanDecision type, String targetPhase, String targetTaskNo, StagnationSignalType triggerSignal, String reason)`。**拒绝"apply 传事件"备选**——理由：`PlanExecutionResult.getDecisionsEnacted()` 须记录决策，结果对象使记录含载荷（可观测），传事件则记录丢失目标信息。`decide()` 返回类型从 `ReplanDecision` 改为 `ReplanDecisionResult`。**枚举级联改造**：`PlanExecutionResult.getDecisionsEnacted()` 类型 `List<ReplanDecision>` → `List<ReplanDecisionResult>`（`:92,122,129,213`）；`TestPlanExecutorEndToEnd.CountingReplanner`（覆盖 decide/apply）+ `result.getDecisionsEnacted().get(0)` 断言 + `TestPlanReplanner` 多处 `assertEquals(ReplanDecision.ESCALATE, decide(...))` 须同步改写。回写 design §14.4.2 载荷形态。
- [ ] **Decision：ROLLBACK_PHASE 触发条件**。**不扩展 `GateOnFail` 枚举**（`{retry,block,escalate}`，`GateOnFail.java:13-19`，无 rollback/split 值——扩展是 Protected Area 模型变更）。裁定采用 **plan 级 `<replanPolicy>` 配置**（新 xdef 元素或在 task/phase 上加 `on-stagnation` 属性）：声明某 phase/task 停滞时可回退（含目标前置 phase）。与 ESCALATE（不可恢复升级）区分。须可配置。回写 design §14.4.2 触发条件 + §14.4.1 信号集（若新增信号）。**若裁定需 xdef 模型变更，显式标注为 Protected Area plan-first 子项并列影响清单**（codegen + GateCheckResult 级联）。
- [ ] 落地 `ReplanDecisionResult` + `decide()` 在裁定条件下产 `ROLLBACK_PHASE` 结果（含目标 phase）；`CONTINUE`/`ESCALATE` 路径行为不变（零回归）+ 级联改造 `PlanExecutionResult`/测试。
- [ ] **executor 控制流改造**（ROLLBACK 前置阻断项）：改造 `PlanExecutor.execute()`（`:99` 单向 for 循环）+ `respondToStagnation`（`:204-218` 恒终止）：
  - ROLLBACK（可恢复）**不返回终止响应**——区别于 ESCALATE/ABORT（终止）。`respondToStagnation` 须区分可恢复决策（ROLLBACK/SPLIT→继续循环）与终止决策（ESCALATE/ABORT→返回）。
  - phase 循环可按 `state.getCurrentPhase()` **重入/回跳**（ROLLBACK 移回 currentPhase 后，循环从新 currentPhase 重新执行，非单向递增）。
  - **cycle-safety bound 扩展**：计数回退次数，超阈值抛 `IllegalStateException`（防 ROLLBACK↔推进 死循环），复用既有 `computeSafetyBound` 模式（`:282`）。
- [ ] **ROLLBACK_PHASE enactment** in `apply()`：重置目标 phase 内 task status（completed→pending，仅该 phase）+ phase status 回退（escalated/failed→pending）+ 标记该 phase task 的累积 `AgentPlanError.resolvedAt`（首个 writer）+ **清理该 phase 的 `gateExhaustedPhases` 标记**（否则 ROLLBACK 后 `StagnationDetector` 立即重产 GATE_EXHAUSTED → 再 ROLLBACK 死循环）+ 移回 `currentPhase` 至目标 phase。作用于 `PlanExecutionState` 可变副本（冻结模板不突变，§14.4.3）。
- [ ] 更新 `TestPlanReplanner.apply_rollbackPhase_*`：从断言"抛 Unsupported"改为断言 enactment 真实生效（task 回 pending、错误 resolved、currentPhase 移回）。
- [ ] 更新 `TestPlanExecutorEndToEnd`：新增 ROLLBACK 端到端用例（多 phase plan → 推进到 phase-2 → 注入 ROLLBACK 触发 → replanner 产 ROLLBACK → executor 不终止、回退 phase-1 → 重新推进可达 phase-2，非死锁）。

Exit Criteria:

- [ ] `ReplanDecisionResult` 存在，`decide()`/`apply()` 签名一致，幂等性测试固化（含载荷字段）
- [ ] **级联改造完成**：`PlanExecutionResult.getDecisionsEnacted()` 类型迁移 + `CountingReplanner` + 所有 `decide()`/`getDecisionsEnacted()` 断言编译通过且语义正确
- [ ] ROLLBACK_PHASE 在裁定条件下由 `decide()` 产出（非手工调 apply），有测试构造触发并断言决策类型 + 目标 phase
- [ ] **executor 控制流改造验证**：ROLLBACK 后 execute() **不终止**、phase 循环按 currentPhase 重入、cycle-safety 计数回退防死循环（有测试断言：回退后重新推进可达后续 phase；超阈值回退抛异常）
- [ ] **ROLLBACK enactment 真实生效**：task completed→pending、phase 回退、`AgentPlanError.resolvedAt` 被写入（首个 writer，单测断言非 null）、currentPhase 移回——均可在 `PlanExecutionState` 观测
- [ ] **幂等验证**：相同停滞状态重复 decide → 相同决策结果（含载荷）
- [ ] **端到端验证**：plan 加载→推进→注入回退触发→ROLLBACK 不终止→enactment 回退→重新推进可达后续 phase（Minimum Rules #22）
- [ ] **接线验证**：decide 产 ROLLBACK 后 apply 确实被 PlanExecutor 调用并改变状态；executor 确实重入而非终止（Minimum Rules #23）
- [ ] **无静默跳过**：ROLLBACK 真实改变状态（非 no-op）；触发条件不满足时不产 ROLLBACK（不误触发）
- [ ] **零回归**：CONTINUE/ESCALATE 路径行为不变（ESCALATE 仍终止）；无 ROLLBACK 触发配置的 plan 行为不变
- [ ] design §14.4.2（载荷）+ §14.4.2/§14.4.1（触发条件）已回写
- [ ] `ai-dev/logs/2026/08-01.md` 已追加本 phase

### Phase 2 - SPLIT_TASK enactment + 集成面（Fix | Proof）

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/plan/runtime/PlanReplanner.java`（SPLIT enactment）、`PlanExecutionState.java`（运行时子任务节点插入 API + 结构 overlay）、`PlanScheduler.java`（结构来源改造）、`PlanExecutor.java`（`readyTasksForPhase`/`collectTaskNos` phase 过滤层改造）、`agent-plan.xdef`（SPLIT 模板 DSL 落点，若裁定需模型变更）、`ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`（§14.4.3 SPLIT 语义回写）

- Item Types: `Fix | Proof`

> **依赖 Phase 1 决策载荷 + executor 控制流**：SPLIT 复用 Phase 1 的 `ReplanDecisionResult`（目标 task + 子任务规格）+ executor 可恢复重入（SPLIT 不终止）。

- [ ] **Decision：SPLIT_TASK 触发条件 + 子任务规格 DSL 落点**。定义何时产 SPLIT——候选：`TASK_STALLED` 且 task 声明可拆分。**子任务规格来源须有 DSL 落点**（当前全仓库无 `splittable` 字段、无拆分模板 schema）：裁定在 `agent-plan.xdef` 的 task 上新增 `<splitTemplate>` 子元素（声明子任务规格：taskNo 前缀 + 继承的 dependsOn/trigger）或等价声明机制。**若需 xdef 模型变更，显式标注 Protected Area plan-first**（codegen 级联）。回写 design §14.4.3。
- [ ] **PlanExecutionState 结构 overlay**：今日 task 结构来自冻结模板（`collectAllTasks(plan)`）。SPLIT 须让运行时副本可持有**新增 task 节点**（保留原 task 占位标记 `split`，子任务初始 pending）。扩展 `PlanExecutionState` 支持运行时 task 节点注册（taskNo + status + dependsOn + trigger）。
- [ ] **scheduler 结构来源改造**：`PlanScheduler.getReadyTasks(plan, statusProvider)` 今日结构来自 `collectAllTasks(plan)`（冻结模板）。须使 scheduler 能读 **运行时 overlay 的结构**（运行时新增节点），非只冻结模板——裁定方案（如 `getReadyTasks` 接收结构 overlay，或 PlanExecutionState 暴露合并视图）。
- [ ] **executor phase 过滤层改造**：`readyTasksForPhase`（`:220-230`）调 `scheduler.getReadyTasks(state.getPlan(), ...)` 后用 `collectTaskNos(phase)`（`:232-236`，读冻结 phase）过滤。SPLIT 新增子任务须被此过滤层识别——`collectTaskNos` 须读运行时 overlay（含 split 出的子节点），非只冻结 phase。否则子任务被过滤掉成死节点。
- [ ] **SPLIT_TASK enactment** in `apply()`：向 `PlanExecutionState` 运行时副本插入子任务节点（原 task 标记 `split`/占位，子任务初始 pending，保留 DAG 依赖关系）；不增删冻结模板（§14.4.3）。
- [ ] 更新 `TestPlanReplanner.apply_splitTask_*`：从断言"抛 Unsupported"改为断言子任务节点真实插入（可调度）。
- [ ] 端到端测试：task 停滞 + 可拆分声明 → replanner 产 SPLIT → enactment 插入子任务 → **scheduler 返回子任务** → **executor phase 过滤不排除子任务** → PlanExecutor 调度子任务推进（子任务可由 stub TaskRunner 完成）→ 原 task 达成。

Exit Criteria:

- [ ] SPLIT_TASK 在裁定条件下由 `decide()` 产出，有测试构造触发并断言决策类型 + 目标 task + 子任务规格
- [ ] **SPLIT DSL 落点存在**（`agent-plan.xdef` 新增声明机制或等价），codegen 模型已生成（若裁定需模型变更）
- [ ] **SPLIT enactment 真实生效**：`PlanExecutionState` 运行时副本出现新子任务节点（可观测），冻结模板不变（断言模板 task 列表不变）
- [ ] **scheduler 结构来源改造验证**：`PlanScheduler.getReadyTasks` 返回运行时新增子任务节点（非只冻结模板任务）——anti-hollow 关键
- [ ] **executor phase 过滤验证**：`readyTasksForPhase`/`collectTaskNos` 识别运行时新增子节点（不被过滤掉）——anti-hollow 关键
- [ ] **接线验证**：子任务节点被 `PlanScheduler.getReadyTasks` 识别为可调度 **且** 被 executor 调度执行（计数器/标志位 verify，Minimum Rules #23）——新增节点不能是死节点
- [ ] **端到端验证**：task 停滞→SPLIT→子任务插入→scheduler 返回→executor 调度推进→原 task 达成（Minimum Rules #22）
- [ ] **无静默跳过**：SPLIT 真实插入节点（非 no-op）；触发条件不满足时不误触发
- [ ] **零回归**：CONTINUE/ESCALATE/ROLLBACK 路径不变；无 SPLIT 触发配置的 plan 行为不变（scheduler 对无 overlay 的 plan 行为不变）
- [ ] design §14.4.3 SPLIT 语义（运行时副本节点增删 + scheduler/executor 集成）已回写
- [ ] `ai-dev/logs/2026/08-01.md` 已追加本 phase

## Closure Gates

> 本计划涉及代码 + design 变更，构建验证条目保留。

- [ ] W1-4 决策空间全部可达 + 有真实 enactment（CONTINUE/ESCALATE/ROLLBACK_PHASE/SPLIT_TASK；ABORT 若附带落地则一并，否则留 successor 并明示）
- [ ] roadmap `[ ] W1-4` 满足勾选条件（ROLLBACK/SPLIT 运行时已交付）—— closure 时更新 roadmap + daily log
- [ ] 无静默跳过：ROLLBACK/SPLIT 真实改变状态，不再抛 UnsupportedOperationException（除非 ABORT 明确保留）
- [ ] 零回归：CONTINUE/ESCALATE 行为不变；无 ROLLBACK/SPLIT 触发配置的 plan 行为不变
- [ ] 冻结模板不突变（§14.4.3 裁定成立）
- [ ] design §14.4.2（载荷 + 触发条件）/§14.4.3（SPLIT 语义）已回写
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证（a）ROLLBACK 后状态确实回退且可重新推进，（b）SPLIT 后子任务节点被 PlanScheduler 真实调度，（c）端到端从停滞注入到 enactment 效果完整连通
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [ ] `./mvnw compile` 通过
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### nop-task 执行层迁移（§14.5）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 独立大型 successor（前置 plan Deferred）；本计划在自建 PlanExecutor host 上实现 enactment，不动 DSL/replanner 决策契约，迁移是纯执行层 successor。
- Successor Required: yes
- Successor Path: §14.5 nop-task 迁移 successor

### W2-3 三级失败升级完整实现

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 单 attempt 内失败（§14.4.5 边界已 reconcile），属 W2-3 work item。
- Successor Required: yes
- Successor Path: W2 roadmap work item W2-3

## Non-Blocking Follow-ups

- ROLLBACK/SPLIT 触发阈值的生产调参（design 留可配置项）。
- replan 决策可观测性/指标（决策分布、回退/拆分分布）。

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Evidence: <<完成时填写>>

Follow-up:

- <<完成时填写>>
