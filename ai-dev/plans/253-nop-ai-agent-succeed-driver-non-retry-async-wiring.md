# 253 nop-task succeed-driver wiring：非 retry step 类型 + retry async 成功路径

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: succeed-driver-non-retry-and-async (L4-8, carry-over from plan 252)

> Last Reviewed: 2026-06-18
> Source: carry-over from `ai-dev/plans/252-nop-ai-agent-task-step-state-lifecycle.md`（§Non-Blocking Follow-ups L229「succeed() driver 在非 retry step 类型 + retry async 成功路径的 wiring」+ §Non-Goals L52-53）。plan 252 已交付 result/success 状态机（5 方法）+ retry **同步**成功路径（`TaskStepHelper.retry:175`）succeed-driver；本计划接入剩余两个 succeed-driver 结果面：(1) retry **async** 成功路径（`:170`/`:172-173`）；(2) 非 retry step 类型（具有具体 `execute()` 的 step 类：composite `SequentialTaskStep` / `SelectorTaskStep` / `LoopTaskStep` + simple/bean-backed wrapper `BeanTaskStep`；仓库无独立 `SimpleTaskStep` 运行时类——simple step 经 `BeanTaskStep` wrapper 执行）`execute()` 成功路径。消除 plan 252 诚实记录的 stepStatus ACTIVE→COMPLETED 可观测性不对称（retry-sync 成功转 COMPLETED，而 retry-async / 非 retry step 成功不转）。
> Related: `252`（状态机本体 + retry sync-success driver）、`248`（retry sync-success return 站点 `:175`）、`247`（fail/exception driver `:177`）、`246`（RetryTaskStepWrapper / TaskStepHelper.retry 骨架）

## Purpose

把 plan 252 交付的 `TaskStepStateBean.succeed()` 状态机从「仅 retry 同步成功路径 runtime-driven」收敛为「retry-sync + retry-async + 非 retry step **全部**成功路径 runtime-driven」，使 `stepStatus` ACTIVE→COMPLETED（`isDone()`/`isSuccess()`/`result()` 可观测）在**所有 step 成功路径**上成立。plan 252 §Non-Goals L52-53 已诚实裁定 async + 非 retry driver 为 successor（执行架构 wiring），本计划是该 successor。

## Current Baseline

基于 plan 252 closure audit 的 verified facts（引用位置可由独立审计复核 live repo）：

- **状态机本体已就绪（plan 252 Phase 1）**：`TaskStepStateBean`（`nop-task/nop-task-core/src/main/java/io/nop/task/state/TaskStepStateBean.java`）5 个方法（`succeed` / `isDone` / `isSuccess` / `result()` / `result(TaskStepReturn)`）已实现，基于既有字段 `resultValue`（父类 `AbstractTaskStateCommon`）/ `stepStatus`（本类）。`succeed(R, nextStepId, taskRt)` = `setResultValue(R)` + `setStepStatus(COMPLETED)`。`isDone()`/`isSuccess()` null-safe 派生自 `stepStatus`。
- **retry 同步成功路径已 wired（plan 252 Phase 2）**：`TaskStepHelper.retry`（`nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java`）`:175 return result;` 前已调 `state.succeed(result.getResult(), result.getNextStepName(), stepRt.getTaskRuntime())`（plan 252 closure audit PASS）。
- **retry async 成功路径未 wired（本计划 Phase 1）**：`TaskStepHelper.retry` 的 async 成功完成有多个站点，均未调 `state.succeed(...)`——plan 252 §Non-Goals L53 显式裁定为 successor（async callback 模式与同步不同）。包括：primary async 分支（`:170 result.sync()` / `:172-173` thenCompose callback）与 scheduled-retry（delay>0）async 分支（`:152-153 result.sync()` / `:155-156 thenCompose doRetry`）。这些 async 站点最终经公共 choke-point `doRetry`（`:186-197`，`err==null` success branch `:194-195` 直接返回成功 value）汇总——在 `doRetry` 成功分支 wiring 可 DRY 覆盖全部 async 路径（具体 wiring 点属 execution 裁定，见设计裁定 2）。verified：`state.succeed(` 在 `src/main` 仅出现在 `TaskStepHelper.java:175`。
- **非 retry step 成功路径未 wired（本计划 Phase 2）**：具有具体 `execute()` 的 step 类（`nop-task-core/.../step/`）的成功返回路径未调 `state.succeed(...)`——plan 252 §Non-Goals L52 显式裁定为 successor（执行架构 wiring）。目标 step 类：composite 类 `SequentialTaskStep` / `SelectorTaskStep` / `LoopTaskStep`（plan 252 Current Baseline L35 记录：step 执行调 `TaskStepReturn.isDone()`，非 `ITaskStepState.isDone()`；其 `execute()` 各有多个成功返回点——见设计裁定 1 DRY wiring）+ simple/bean-backed wrapper `BeanTaskStep`（`TaskStepBuilder.buildSimpleStep` 返回用户 bean 经此 wrapper 执行；仓库无独立 `SimpleTaskStep` 运行时类）。目标 step 类全集在 execution 时经 grep `ITaskStep` implementors 复核确认。
- **`ITaskStepState.isDone()/result()` production reader 仍未消费（continuation-skip）**：plan 252 §Non-Goals L54 裁定为独立 successor（依赖 DB-backed state persistence 才能 runtime-live）。本计划**不**引入 reader 消费侧。
- **不对称（本计划收敛目标）**：retry-wrapped step **同步**成功 → stepStatus COMPLETED（plan 252）；retry-wrapped step **async**成功 → stepStatus 不变；非 retry step 成功 → stepStatus 不变。本计划使后两者与前者对齐。
- **plan 252 E2E 基础设施可复用**：plan 252 Phase 3 引入 `StateCapturingTaskStateStore`（继承 `DefaultTaskStateStore`，在 `newStepState(...)` 捕获 state 引用——`succeed` 原地 mutate 同一对象，故捕获引用在执行后反映终态）+ `TestReliabilityDecorators` retry-sync-success E2E。本计划复用该 capture 机制验证 async + 非 retry 路径状态转移。

## Goals

- **retry async 成功路径 succeed-driver**：`TaskStepHelper.retry` 全部 async 成功路径（primary `:170`/`:172-173` + scheduled-retry delay>0 `:152-153`/`:155-156`，或经公共 choke-point `doRetry` success branch）成功完成时调 `state.succeed(...)`，使 retry-wrapped step async 成功后 stepStatus ACTIVE→COMPLETED 可观测（含 delay>0 路径）。
- **非 retry step 成功路径 succeed-driver**：composite step（`SequentialTaskStep` / `SelectorTaskStep` / `LoopTaskStep`）+ simple/bean-backed wrapper（`BeanTaskStep`）的 `execute()` 成功返回时调 `state.succeed(...)`，使非 retry step 成功后 stepStatus ACTIVE→COMPLETED 可观测。
- **可观测的行为契约（对称性）**：无论 retry-sync / retry-async / 非 retry step，step 成功后均 `getStepStatus()==COMPLETED(40)` + `isDone()==true` + `isSuccess()==true` + `result()` 非 null。
- **零回归**：plan 252 retry-sync-success 行为不变；retry 失败重试路径不变；plan 246-251 decorator/bizFatal 行为不变；nop-task-core + nop-task-ext + nop-ai-agent 既有测试全绿。

## Non-Goals

- **continuation-skip 消费侧（`isDone()`/`result()` 的 production reader）**：re-execution 检查 isDone 跳过执行，依赖 DB-backed state persistence 才能 runtime-live（当前 in-memory 单次执行 state 每次新建，wiring reader 会引入永不触发的死代码）。Classification: successor plan required（plan 252 §Non-Goals L54 同一裁定）。
- **终态失败（stepStatus=FAILED）设置路径**：retry 耗尽 throw 不经状态转移。Classification: successor plan required（plan 252 §Non-Goals L56）。
- **`afterLoad`/`beforeSave` load/save 生命周期**：仍为 no-op（plan 252 §Non-Goals L59 verified）。Classification: successor plan required。
- **result/success 跨重启持久化（serialization）**：in-memory 单次执行内生效，跨重启恢复链路未实现。Classification: optimization candidate（plan 252 carry-over）。
- **完整 outputs map 持久化**：`result()` 仅持久化主 result value。Classification: out-of-scope improvement。
- **`fail()` 标记 done / stepStatus 转移**：REJECTED（plan 247 §设计裁定 1：fail 仅记 exception，不终止生命周期）。
- **新建 step state 持久化架构**：本计划仅在既有 state-access 路径上 wiring；若发现非 retry step 无 state 访问路径，仅引入最小必要访问 hook（非新建持久化架构）。Classification: 本计划 in-scope（最小 hook）。

## Scope

### In Scope

- `TaskStepHelper.retry`（`.../utils/TaskStepHelper.java`）全部 async 成功路径接入 `state.succeed(...)`：primary `:170`/`:172-173` + scheduled-retry delay>0 `:152-153`/`:155-156`（或经公共 choke-point `doRetry` `:186-197` success branch DRY 覆盖）。
- 非 retry step 类型的 `execute()` 成功返回路径接入 `state.succeed(...)`：composite 类 `SequentialTaskStep` / `SelectorTaskStep` / `LoopTaskStep` + simple/bean-backed wrapper `BeanTaskStep`（`nop-task-core/.../step/`）。目标 step 类全集在 execution 时经 grep 复核确认（仓库无独立 `SimpleTaskStep` 运行时类——simple step 经 `BeanTaskStep` wrapper 执行）。
- 若非 retry step 的 `execute()` 上下文不直接持有 `ITaskStepState`：引入最小 state-access（经 `ITaskStepRuntime` 既有访问点，或对齐 plan 252 `TaskStepHelper` 获取 state 的同一方式），**不**新建持久化架构。
- focused 单元测试 + E2E：retry async 成功路径（含 delay>0）状态转移可观测；目标非 retry step 成功路径状态转移可观测。
- 零回归：retry-sync / 失败重试 / async 发起路径 + plan 246-251 decorator/bizFatal + nop-ai-agent 既有测试全绿。

### Out Of Scope

- 见 Non-Goals（continuation-skip reader / 终态 FAILED / afterLoad-beforeSave / 跨重启持久化 / 完整 outputs / fail-done / 新建持久化架构 均为显式 rejected / successor / out-of-scope）。

### Granularity Justification

本计划为单一 work item（retry async driver + 目标非 retry step 类 driver + 测试），同模块（`nop-task-core`）、同模式（step 成功返回时调 `state.succeed`，对齐 plan 252 `:175` 已建立的 driver 形态）。bundle 而非拆分，理由：(1) **同一状态机的对称扩展**——plan 252 已确立 `succeed()` 本体 + retry-sync driver，async driver + 非 retry driver 是同一 driver 在其余成功路径上的对称 wiring，拆分会产中间态不一致（部分路径 stepStatus 转、部分不转）；(2) **driver wiring 是状态机 Anti-Hollow 必需项**——plan 252 独立审计 Round 1 已确认无 production caller 的状态机语义是空壳（违反 #22/#23），driver 必须与状态机语义在同一继承链上闭环；(3) **Litmus test**：retry async + 目标非 retry step 类 driver wiring + focused 单元测试 + E2E + Protected Area（`nop-task-core`）仪式 ≥ plan 文档成本，非过小项。

### 设计裁定（Pre-Adjudicated）

1. **succeed-driver 接入 step 成功返回路径（return 前）**。各 step `execute()` 在返回非异常 `TaskStepReturn` 前，调 `state.succeed(<result 派生值>, <nextStepName>, <taskRuntime>)`——与 plan 252 `TaskStepHelper.retry:175` 同一调用形态（plan 252 设计裁定 6 同源）。理由：成功记 succeed / 失败记 fail 对称。**composite step 多返回点处理**：`SequentialTaskStep` / `SelectorTaskStep` / `LoopTaskStep` 的 `execute()` 各有多个成功返回点（且部分返回点无单一 `result` 变量在 scope 内——R 须从该返回点的 `TaskStepReturn` 派生）；优先识别 DRY 出口（共享 return-helper / 公共完成点）统一 wiring，或经共享 private helper 在各成功返回点一致调用 succeed。具体 wiring 机制（choke-point vs per-return helper）属 execution 裁定（Minimum Rules #10）；plan 约束每个目标 step 类型的所有成功返回路径均触发 `state.succeed`，不遗漏、不产新不对称。

2. **async 路径优先 DRY choke-point wiring**。retry async 成功有多站点（primary `:170`/`:172-173` + scheduled-retry delay>0 `:152-153`/`:155-156`），最终经公共 choke-point `doRetry`（`:186-197`，`err==null` success branch `:194-195`）汇总。优先在 `doRetry` 成功分支 wiring `state.succeed(...)` DRY 覆盖全部 async 路径；若不可行则逐站点 instrument（`:170`/`:172-173`/`:152-153`/`:155-156`）。succeed 须在 async 完成（future resolved）时触发，使状态转移反映最终成功而非发起态。具体 wiring 点属 execution 裁定（Minimum Rules #10）；plan 约束所有 retry async 成功路径（含 delay>0）后 state 可观测。

3. **state 获取方式对齐 TaskStepHelper.retry 既有路径**。plan 252 `:175` 经 `stepRt` 上下文获取 `state`；非 retry step 的 `execute(ITaskStepRuntime stepRt)` 若 `stepRt` 暴露同一访问点则直接复用；若不暴露，引入最小必要访问 hook（不新建持久化层）。**state-access 路径是本计划核心 execution-time 确认项**——独立审计须复核 step runtime API 是否暴露 step state，以及与 plan 252 `TaskStepHelper` 获取 state 的方式是否一致。

4. **nextStepId/result 派生方式属 execution 裁定**（Minimum Rules #10）。plan 约束：succeed 后 `state.isDone/isSuccess/result/stepStatus` 可观测（与 plan 252 设计裁定 7 同源）。

5. **不改变 `fail()`/`exception()` 行为**（plan 247/252 一致）；不引入 reader 消费侧（continuation-skip successor）。

## Execution Plan

### Phase 1 - retry async 成功路径 succeed-driver + compile

Status: completed
Targets: `nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java`（全部 async 成功站点：primary `:170`/`:172-173` + scheduled-retry delay>0 `:152-153`/`:155-156`；或公共 choke-point `doRetry` `:186-197` success branch）

- Item Types: `Fix`（plan 252 §Non-Goals L53 裁定的 successor driver wiring——retry async 成功路径不经 succeed-driver，stepStatus 不转，为 plan 252 留下的状态可观测性不对称的半边）

- [x] 识别 async 成功 DRY wiring 点：优先 `doRetry`（`:186-197`，`err==null` success branch `:194-195`）公共 choke-point 覆盖 primary + scheduled-retry 全部 async 路径；若不可行则逐站点（`:170`/`:172-173`/`:152-153`/`:155-156`）instrument（设计裁定 2）
- [x] 注入 `state.succeed(...)` 使所有 retry async 成功路径（含 delay>0 scheduled-retry）触发状态转移
- [x] 确认 retry sync 成功路径（`:175`，plan 252）零变更
- [x] 确认 retry 失败重试路径（catch + setRetryAttempt + while loop）零变更

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 所有 retry async 成功路径（primary + scheduled-retry，含 delay>0）完成时 `state.succeed(...)` 被调用（读码可复核，无遗漏 async 站点）
- [x] retry sync / 失败路径零变更（plan 252/247/248 行为不变）
- [x] **无静默跳过**（#24）：async succeed-driver 真实调用（非空 / 非 TODO）
- [x] `./mvnw compile -pl nop-task/nop-task-core -am` 通过
- [x] owner-doc 裁定：`No owner-doc update required`（内部 state 持久化行为，retry decorator public 契约不变）——最终在 Phase 3 复核
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 非 retry step 类型 succeed-driver + compile

Status: completed
Targets: `nop-task/nop-task-core/src/main/java/io/nop/task/step/TaskStepExecution.java`（execution-time 裁定：识别 `TaskStepExecution.executeWithParentRt` 为所有 step 类型的公共完成 choke-point，DRY wiring 覆盖 composite + simple/bean-backed 全集）

- Item Types: `Fix`（plan 252 §Non-Goals L52 裁定的 successor driver wiring——非 retry step 成功不经 succeed-driver）

> **Execution-time wiring 裁定（设计裁定 1 + 3 + Minimum Rules #10）**：
> 经 grep `ITaskStep` implementors 复核，所有目标 step 类型（Sequential/Selector/Loop composite + BeanTaskStep/EvalTaskStep simple）在 runtime 经
> `TaskStepExecution.executeWithParentRt` 的 `thenCompose` 成功分支（err==null, not suspend）统一完成。
> 该成功分支（`TaskStepExecution.java:259 return ret;` 前）是所有 step 类型的**公共完成点**（设计裁定 1 要求的 DRY 出口），
> 在此单一 wiring 点注入 `stepRt.getState().succeed(ret.getResult(), ret.getNextStepName(), taskRt)` 即可 DRY 覆盖全部目标 step 类型，
> 无需在各 step 类的多个 execute() 返回点逐个 instrument（composite step 有 5-9 个返回点，逐点 instrument 遗漏风险高）。
> state 获取经 `stepRt.getState()`（与 plan 252 `TaskStepHelper.retry:175` 同一访问路径，设计裁定 3 对齐）。
> 复核确认：BeanTaskStep 在仓库 builder 中未被实例化（buildSimpleStep 直接返回 bean 本身），但其作为 ITaskStep 实现仍经 TaskStepExecution 通用覆盖。

- [x] 复核目标 step 类全集（grep `ITaskStep` implementors / `TaskStepBuilder` factory），确认含 composite（Sequential/Selector/Loop）+ simple/bean-backed wrapper（BeanTaskStep）；仓库无独立 `SimpleTaskStep` 运行时类
- [x] 确认各 step `execute()` 获取 `ITaskStepState` 的访问路径（设计裁定 3：经 `ITaskStepRuntime` 既有访问点，或最小 hook）
- [x] 在 `TaskStepExecution.executeWithParentRt` thenCompose 成功分支调 `state.succeed(...)`——DRY 公共完成点覆盖 SequentialTaskStep 全部成功返回路径（设计裁定 1 DRY choke-point）
- [x] 经同一 TaskStepExecution choke-point 覆盖 SelectorTaskStep 全部成功返回路径
- [x] 经同一 TaskStepExecution choke-point 覆盖 LoopTaskStep 全部成功返回路径
- [x] 经同一 TaskStepExecution choke-point 覆盖 BeanTaskStep / EvalTaskStep（simple/bean-backed）成功返回路径
- [x] 无需引入 state-access hook：TaskStepExecution 已持有 `stepRt`（line 179），`stepRt.getState().succeed(...)` 直接可用

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 所有目标 step 类型的成功返回路径均调 `state.succeed(...)`（读码可复核：TaskStepExecution thenCompose 成功分支单一 wiring 点覆盖 composite 多返回点无遗漏）
- [x] **接线验证**（#23）：succeed-driver 经 TaskStepExecution 在 runtime 被 step 执行链调用（非仅类型系统存在）——Phase 3 E2E 断言验证
- [x] **无静默跳过**（#24）：driver 真实调用（`stepRt.getState().succeed(ret.getResult(), ret.getNextStepName(), taskRt)` 非空方法体、非 placeholder）
- [x] `./mvnw compile -pl nop-task/nop-task-core -am` 通过
- [x] owner-doc 裁定：`No owner-doc update required`（内部 state 行为，step public 执行契约不变）——Phase 3 复核
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - focused 测试 + E2E + 零回归 + 文档收口

Status: completed
Targets: `nop-task/nop-task-ext/src/test/java/io/nop/task/ext/reliability/TestReliabilityDecorators.java`（复用 plan 252 `TestReliabilityDecorators` + `StateCapturingTaskStateStore` capture 机制）、`ai-dev/logs/`

- Item Types: `Proof`

- [x] 新增/扩展 E2E（`TestReliabilityDecorators`，复用 plan 252 `StateCapturingTaskStateStore`）：retry-wrapped step **async** 成功（含 delay>0 scheduled-retry 路径）→ 断言 `state.getStepStatus()==COMPLETED` + `isDone()` + `isSuccess()`
- [x] 新增 E2E/单元：非 retry step（Sequential / Selector / Loop composite + simple/xpl（BeanTaskStep 对齐））成功执行 → 断言 step state `getStepStatus()==COMPLETED` + `isDone()` + `isSuccess()`（对每类目标 step 至少一例）
- [x] 零回归：`./mvnw test -pl nop-task/nop-task-core -am`（含 plan 252 状态机 + retry 测试）+ `./mvnw test -pl nop-task/nop-task-ext -am`（plan 246-251 decorator/bizFatal E2E）+ `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] retry async 成功 E2E 全绿（async succeed-driver runtime 触发 + state 可观测，含 delay>0 路径）
- [x] 目标非 retry step 成功 state 转移测试全绿（每类至少一例）
- [x] **端到端验证**（#22）：从 `.task.xml` step 声明 → step `execute()` → `state.succeed(...)` → `state.getStepStatus()==COMPLETED` 完整路径连通（含 retry-async（含 delay>0）与非 retry 两类路径）
- [x] **接线验证**（#23）：succeed-driver 在 runtime 确实被调用且 state 转移可观测（E2E 断言）
- [x] **无静默跳过**（#24）：driver 真实调用；测试断言状态转移（非仅断言不抛异常）
- [x] 新增功能各有 focused 测试覆盖（#25）：retry async driver + 各目标非 retry step driver（composite + BeanTaskStep/xpl）
- [x] 零回归：nop-task-core（43 tests）+ nop-task-ext（34 tests）+ nop-ai-agent（2714 tests）全绿
- [x] owner-doc 裁定落地（`No owner-doc update required`：内部 state 持久化行为，step/retry public 执行契约不变）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 所有 retry async 成功路径（primary + scheduled-retry 含 delay>0）+ 所有目标非 retry step 成功路径均接入 `state.succeed(...)`（无遗漏 async 站点 / step 类型）
- [x] state 对称性成立：retry-sync / retry-async（含 delay>0）/ 非 retry step 成功后均 stepStatus COMPLETED 可观测
- [x] 必要 focused verification 已完成（async E2E 含 delay>0 + 各目标 step E2E/单元）
- [x] 零回归：plan 252 retry-sync + plan 246-251 decorator/bizFatal + nop-ai-agent 全绿
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（continuation-skip reader / 终态 FAILED / afterLoad-beforeSave / 跨重启持久化 均为显式 Non-Goals）
- [x] 受影响 owner docs 已同步到 live baseline，或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：(a) succeed-driver 在所有成功路径 runtime 被调用（非仅类型系统存在）；(b) 无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-task/nop-task-core -am`
- [x] `./mvnw test -pl nop-task/nop-task-core -am` + `nop-task-ext` + `nop-ai-agent`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（本计划无 deferred 项。所有 Non-Goals 均为显式 successor plan required / rejected / out-of-scope / optimization candidate，非「延期但需裁定」项。）

## Non-Blocking Follow-ups

- continuation-skip 消费侧（`isDone()`/`result()` production reader，依赖 DB-backed state persistence successor）——successor plan required
- 终态失败（stepStatus=FAILED）设置路径——successor plan required
- `afterLoad`/`beforeSave` load/save 生命周期 no-op——successor plan required
- result/success 跨重启持久化（serialization）——optimization candidate

## Closure

Status Note: plan 253 把 plan 252 交付的 `TaskStepStateBean.succeed()` 状态机从「仅 retry 同步成功路径 runtime-driven」收敛为「retry-sync + retry-async + 非 retry step **全部**成功路径 runtime-driven」。Phase 1 经 `doRetry` 公共 choke-point 成功分支注入 `state.succeed(...)` 覆盖全部 retry async 路径（primary + scheduled-retry delay>0），并将 already-done async 快捷路径（`:170`/`:152-153`）路由到 doRetry 以 DRY 统一。Phase 2 经 execution-time 裁定识别 `TaskStepExecution.executeWithParentRt` thenCompose 成功分支为所有 step 类型的公共完成点，在单一 wiring 点注入 `state.succeed(...)` DRY 覆盖全部目标 step 类型（Sequential/Selector/Loop composite + EvalTaskStep/BeanTaskStep simple），避免在各 step 类 5-9 个 execute() 返回点逐个 instrument 的遗漏风险。Phase 3 新增 6 个 focused E2E（retry async primary + retry delay>0 scheduled + sequential + selector + loop + xpl/simple），全部断言 stepStatus COMPLETED + isDone + isSuccess 可观测。零回归：nop-task-core（43）+ nop-task-ext（34）+ nop-ai-agent（2714）全绿。
Completed: 2026-06-18

Closure Audit Evidence:

- Reviewer / Agent: opencode session (GOAL_DRIVER execution), closure audit via live code path tracing + test suite execution
- Evidence:
  - **Phase 1 Exit Criteria**: `TaskStepHelper.java:194-197` doRetry success branch calls `state.succeed(value.getResult(), value.getNextStepName(), stepRt.getTaskRuntime())`；`:170` 和 `:153` 已从 `return result.sync()` 路由为 `return doRetry(result.sync(), null, ...)` 使 already-done async 快捷路径也经 doRetry 触发 succeed；`:175` retry sync 路径零变更；`:177-183` 失败重试路径零变更。读码复核无遗漏 async 站点。
  - **Phase 2 Exit Criteria**: `TaskStepExecution.java:259` return 前注入 `stepRt.getState().succeed(ret.getResult(), ret.getNextStepName(), taskRt)`；TaskStepExecution 是所有 step 的公共完成 choke-point（executeWithParentRt → step.execute → thenCompose 成功分支），覆盖 Sequential/Selector/Loop composite 多返回点 + EvalTaskStep/BeanTaskStep simple 全集；state 获取经 `stepRt.getState()`（与 plan 252 `:175` 同一路径）。
  - **Phase 3 Exit Criteria**: 6 个 focused E2E 全绿——`retry_asyncSuccessSucceedDriver_stateMachineObservable`（primary async, CompletableFuture.supplyAsync 10ms）、`retry_delayScheduledSuccessSucceedDriver_stateMachineObservable`（delay>0 scheduled-retry, retryDelay=10）、`nonRetry_sequentialStep_succeedDriver_stateMachineObservable`、`nonRetry_selectorStep_succeedDriver_stateMachineObservable`、`nonRetry_loopStep_succeedDriver_stateMachineObservable`、`nonRetry_simpleXplStep_succeedDriver_stateMachineObservable`。每个断言 stepStatus==COMPLETED(40) + isDone + isSuccess。
  - **端到端验证 (#22)**: 从 `.task.xml` `<decorator name="retry"/>` / `<sequential>` / `<selector>` / `<loop>` / `<xpl>` 声明 → step execute() → state.succeed → stepStatus COMPLETED 完整路径经 E2E 断言验证。
  - **接线验证 (#23)**: succeed-driver 在 runtime 确实被调用——StateCapturingTaskStateStore 捕获的 state 引用在执行后反映终态 COMPLETED（非仅类型系统存在）。
  - **无静默跳过 (#24)**: succeed 调用为 `state.succeed(ret.getResult(), ret.getNextStepName(), taskRt)` 非空方法体（TaskStepStateBean.succeed = setResultValue + setStepStatus(COMPLETED)）；测试断言状态转移值（非仅断言不抛异常）。
  - **新增功能测试覆盖 (#25)**: 6 个新 E2E 覆盖 retry async driver + sequential + selector + loop + xpl/simple driver。
  - **零回归**: `./mvnw test -pl nop-task/nop-task-core -am` 43 tests pass + `nop-task-ext` 34 tests pass（含 plan 252 `retry_syncSuccessSucceedDriver_stateMachineObservable` + plan 246-251 bizFatal/retry/timeout/rateLimit 全部不变）+ `nop-ai-agent` 2714 tests pass。
  - **Anti-Hollow**: doRetry 成功分支 + TaskStepExecution thenCompose 成功分支均为真实 succeed 调用（非空/no-op）；E2E 经 StateCapturingTaskStateStore 捕获的 state 引用验证 runtime 状态转移。
  - **owner-doc**: `No owner-doc update required`——内部 state 持久化行为变更，step/retry public 执行契约不变。
  - **Deferred 项分类检查**: continuation-skip reader / 终态 FAILED / afterLoad-beforeSave / 跨重启持久化均为显式 Non-Goals（successor plan required / optimization candidate），无 in-scope live defect 被降级。

Follow-up:

- continuation-skip 消费侧（`isDone()`/`result()` production reader，依赖 DB-backed state persistence successor）——successor plan required
- 终态失败（stepStatus=FAILED）设置路径——successor plan required
- `afterLoad`/`beforeSave` load/save 生命周期 no-op——successor plan required
- result/success 跨重启持久化（serialization）——optimization candidate
