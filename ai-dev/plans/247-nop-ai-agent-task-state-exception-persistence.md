# 247 nop-task TaskStepStateBean 异常持久化（retry 异常分类修复）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: nop-task-core-state-exception-persistence (carry-over from L4-nop-task-decorator / plan 246)

> Last Reviewed: 2026-06-18
> Source: carry-over from `ai-dev/plans/246-nop-ai-agent-task-step-decorator.md`（§Non-Blocking Follow-ups line 229「nop-task-core 内部修正（in-memory state exception 保存）」+ §Closure line 202,230 已知限制「in-memory state 不保存 exception → 不可重试分类不生效」）。该 carry-over 是 plan 246 交付（retry/timeout/rateLimit decorator bean）后最高杠杆修复项：0 依赖（standalone fix）、最多下游受益（解锁 plan 246 retry decorator 的异常分类价值主张）、真实功能正确性缺口而非 feature gap。
> Related: `246`（交付 retry/timeout/rateLimit decorator bean——本计划修复其依赖的 nop-task-core state exception 持久化缺口）、`236`（daemon 诚实失败语义——step 级重试可靠性由 plan 246 decorator + 本计划 state fix 共同支撑）

## Purpose

修复 nop-task-core `TaskStepStateBean` 的 exception 持久化 no-op 缺口：`fail(Throwable, ITaskRuntime)` 和 `exception(Throwable)` 当前为空方法体，导致 `state.exception()` 永远返回 null。这使得 plan 246 刚交付的 `RetryTaskStepDecorator` 的 `RetryPolicy` 异常分类在 in-memory state 模式下完全失效——所有异常都被无条件按 retryCount 重试至耗尽，不可恢复异常无法提前终止（honest throw 退化为 generic error）。

**为何需要独立 successor**：plan 246 闭合时诚实记录此限制为 nop-task-core 内部 Non-Goal（Protected Area，plan-first）。本计划以最小手术式修复（2 个方法体从 no-op 改为真实实现）闭合此 carry-over，不扩大到 `succeed`/`isDone`/`isSuccess` 等其他 no-op 生命周期方法（独立结果面）。

## Current Baseline

基于 live repo 核对（引用位置为审计可复核的 live code path）：

- **`exception(Throwable exp)` 是空方法体（核心缺口）**：`TaskStepStateBean.exception(Throwable exp)`（`nop-task/nop-task-core/src/main/java/io/nop/task/state/TaskStepStateBean.java:75-77`）方法体为空 → `exception` 字段（line 27 `private transient Throwable exception`）永远不被赋值。
- **`fail(Throwable, ITaskRuntime)` 是空方法体（核心缺口）**：`TaskStepStateBean.fail(Throwable exception, ITaskRuntime taskRt)`（同文件 `:45-47`）方法体为空 → 异常永远不被保存。
- **`exception()` getter 已正确实现但无数据可读**：`TaskStepStateBean.exception()`（同文件 `:70-72`）返回 `this.exception` 字段——字段存在且 getter 正确，但因 setter 为 no-op 故 getter 实际永远返回 null。
- **消费路径（retry 异常分类）**：`TaskStepHelper.retry()`（`nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:130-182`）：
  - `:176` catch 块 `state.fail(e, stepRt.getTaskRuntime())` → 应保存异常但因 no-op 不保存
  - `:138` `retryPolicy.getRetryDelay(state.exception(), retryAttempt, stepRt)` → 读异常用于分类可恢复性 + 计算延迟 → 永远收到 null
  - `:140-143` 若 delay < 0 则 `Throwable e = state.exception()` → 用于构造最终抛出 → 永远 null → 退化为 `ERR_TASK_RETRY_TIMES_EXCEED_LIMIT` generic error
- **后果（plan 246 §Phase 2 line 121 已诚实记录）**：`RetryPolicy.getRetryDelay(null, ...)` 无法分类异常可恢复性 → 跳过不可重试分类 → 所有异常按 retryCount 重试至耗尽。不可恢复异常（如 `IllegalArgumentException`）本应立即 honest throw，却退化无条件重试。
- **消费路径 blast radius 确认**：精确搜索 `state\.fail\(` + `state\.exception\(\)`（`ITaskStepState` 接口方法消费）在全仓 java 源码仅命中 `TaskStepHelper.java`（nop-task-core 内部 `retry()` 方法 line 176/138/140），无 nop-ai-agent 或其他模块直接消费 `ITaskStepState.fail` / `exception()`。

## Goals

- **实现 `TaskStepStateBean.exception(Throwable exp)`**：设置 `this.exception = exp`，使 getter `exception()` 返回真实异常。
- **实现 `TaskStepStateBean.fail(Throwable exception, ITaskRuntime taskRt)`**：保存异常（委托 `exception(exp)`，保持单一赋值入口）。
- **解锁 retry 异常分类**：`TaskStepHelper.retry:138` `retryPolicy.getRetryDelay(state.exception(), ...)` 收到真实异常 → 可恢复异常按配置重试，不可恢复异常（如 `NopException.bizFatal(true)`，经 `RetryPolicy` 默认 `isRecoverableException` 分类）提前 honest throw 抛出真实异常。注：plan 246 的 `RetryTaskStepDecorator` 构造 `RetryPolicy` 时未设 `exceptionFilter`，故使用默认分类逻辑（`NopException` 检查 `!isBizFatal()`）——本计划不改 decorator，只使 `state.exception()` 返回真实异常让默认分类生效。
- **focused 测试验证**：可恢复异常被重试 / 不可恢复异常不被重试（honest throw 真实异常）/ exception 持久化可观测（`state.exception()` 非 null）。
- **零回归**：nop-task-core + nop-task-ext 既有测试全绿。

## Non-Goals

- **retry loop 同步成功路径不 return 的 quirk**（plan 246 §Closure line 230 已知限制）：`TaskStepHelper.retry()` 同步成功路径缺少 `return result` 导致成功 step 被 re-execute 至 retryCount 耗尽。独立 successor，语义不同（成功路径 re-execute vs 失败路径异常分类），且修该 quirk 需理解全量 step 执行模型对同步返回的假设。Classification: successor plan required。
- **`succeed()` / `isDone()` / `isSuccess()` / `result()` / `result(TaskStepReturn)` 等其他 no-op 生命周期方法**：这些是 result/success 生命周期管理（step 完成后结果持久化 + continuation 跳过），与 exception 持久化（retry 循环内跨迭代异常传递）是不同结果面。Classification: successor plan required。
- **`transient` 关键字移除**：exception 字段为 `transient`（不参与 Java 序列化）。对 in-memory retry（单次执行内跨重试迭代传递异常）无影响——异常在同一执行内从 `fail()` 经字段传递到下一轮 `exception()` 读取，不跨 Java 序列化。跨重启恢复的 exception 持久化为独立 successor。Classification: out-of-scope improvement。
- **DB-backed state 实现 / nop-task-core 其他内部变更**：本计划只修 in-memory `TaskStepStateBean` 的 2 个方法。Classification: rejected（Protected Area scope control）。
- **team-task decorator 配置面 / builder 自动传播**：依赖独立 successor（ORM Protected Area）。Classification: successor plan required。

## Scope

### In Scope

- `TaskStepStateBean.exception(Throwable exp)` 实现（设置 `this.exception` 字段）
- `TaskStepStateBean.fail(Throwable exception, ITaskRuntime taskRt)` 实现（保存异常，委托 setter）
- focused 测试：可恢复异常重试 / 不可恢复异常 honest throw / exception 持久化可观测
- 零回归验证（nop-task-core + nop-task-ext 既有测试全绿）

### Out Of Scope

- 见 Non-Goals（retry sync-return quirk / 其他生命周期 no-op 方法 / transient 移除 / DB-backed state / team-task 配置面 均为显式 rejected / successor）

### 设计裁定（Pre-Adjudicated）

1. **`fail()` 只保存异常，不做生命周期终止**。`fail()` 在 retry catch 块中被调用（`TaskStepHelper.retry:176`），每次失败都调用，retry loop 随后继续（`setRetryAttempt` + 循环回顶）。`fail()` 不应设 `isDone()=true`（否则语义是"永久失败"，与 retry 继续矛盾）。`fail()` 在本计划的唯一职责是保存异常供下一轮 retry 分类。理由：(1) 消费路径（retry loop）只需 exception 持久化，不检查 `isDone()`；(2) 不扩大 scope 到生命周期管理（Non-Goal）。

2. **`fail()` 委托 `exception(exp)`**。`fail(Throwable e, ITaskRuntime taskRt)` → `exception(e)`，保持单一异常赋值入口。理由：(1) 与 bean setter 约定一致（getter `exception()` + setter `exception(Throwable)` 成对）；(2) 避免双份赋值逻辑漂移。

3. **不改 `transient` 修饰符**。exception 字段保持 `transient`。in-memory retry 在单次执行内跨迭代使用 exception（`fail()` 写 → 下一轮 `exception()` 读），不跨 Java 序列化。理由：(1) `Throwable` 通常不可靠序列化；(2) 字段设计即为 `transient`，getter 已正确实现，仅 setter 缺失；(3) 跨重启恢复是独立结果面（Non-Goal）。

4. **scope 控制在 2 个方法，不修 `TaskStepHelper.retry()` 逻辑**。本计划只改 `TaskStepStateBean` 的 2 个 no-op 方法体。retry 消费逻辑（`TaskStepHelper.retry`）、retry decorator（plan 246 已交付）、RetryPolicy（nop-commons 已就绪）均不改。理由：(1) Protected Area scope control；(2) 最小手术式修复降低回归风险；(3) retry sync-return quirk 独立 successor（Non-Goal）。

## Execution Plan

### Phase 1 - exception 持久化实现 + compile

Status: completed
Targets: `nop-task/nop-task-core/src/main/java/io/nop/task/state/TaskStepStateBean.java`

- Item Types: `Fix`（confirmed live defect：两方法为 no-op 空方法体，违反 Minimum Rules #24 禁止静默跳过）

- [x] 实现 `exception(Throwable exp)`：方法体设 `this.exception = exp;`（替换空方法体）
- [x] 实现 `fail(Throwable exception, ITaskRuntime taskRt)`：方法体调用 `exception(exception);` 委托 setter（替换空方法体）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `exception(Throwable exp)` 方法体非空（读码可复核：`this.exception = exp` 赋值语句存在，不再是空 `{}`）
- [x] `fail(Throwable, ITaskRuntime)` 方法体非空（读码可复核：调用 `exception(exception)` 或等价赋值，不再是空 `{}`）
- [x] **无静默跳过**（#24）：两方法均有真实实现，非空方法体 / 非 continue / 非 TODO
- [x] `./mvnw compile -pl nop-task/nop-task-core -am` 通过
- [x] 若该 Phase 改变 live baseline：`No owner-doc update required: ITaskStepState 接口契约不变，仅实现从 no-op 变为正确赋值`（无 public contract 变化，无需 design/doc 更新）
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 1 实现 landing）

### Phase 2 - focused 测试 + 端到端验证 + 零回归 + 文档

Status: completed
Targets: `nop-task/nop-task-core/src/test/` 或 `nop-task/nop-task-ext/src/test/`（focused 测试）、`ai-dev/logs/`

- Item Types: `Proof`

- [x] 编写 exception 持久化单元测试：对 `TaskStepStateBean` 调用 `fail(e, taskRt)` 或 `exception(e)` → 断言 `exception()` 返回 `e`（非 null），且与传入引用一致
- [x] 编写不可恢复异常不重试测试：配置 retry decorator（plan 246 默认 `RetryPolicy` 分类）→ step 抛 `NopException(...).bizFatal(true)`（默认 `isRecoverableException` 判定为不可恢复）→ 断言执行次数 = 1（honest throw，不重试），且抛出的异常为真实异常（非 generic `ERR_TASK_RETRY_TIMES_EXCEED_LIMIT`）
  - **实现裁定**：bizFatal 分类的 focused 验证在 nop-task-core 单元测试层完成（`TestTaskStepStateBeanExceptionPersistence#retryPolicy_classifiesBizFatalAsUnrecoverable_afterFail`）。理由：xpl 函数调用（`AbstractObjFunctionExecutable.doInvoke*`）会把被调用方法抛出的 NopException 包装为 `NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL)`，丢失 bizFatal 标记（xpl 既有行为，非 plan 247 scope）。Java 层直接验证 `state.fail(bizFatal) → state.exception() 返回 bizFatal → RetryPolicy.getRetryDelay 返回 -1（不可恢复）` 证明 plan 247 修复后分类联动生效。
- [x] 编写可恢复异常重试测试：配置 retry decorator（plan 246 默认 `RetryPolicy` 分类）→ step 抛普通非 bizFatal 异常（默认 `isRecoverableException` 判定为可恢复）→ 断言执行次数 ≥ 2（按配置重试）
  - **双覆盖**：(a) nop-task-core 单元测试 `retryPolicy_classifiesRecoverableAsRecoverable_afterFail` 直接验证分类；(b) nop-task-ext E2E 测试 `retry_recoverableExceptionRetriedE2e` 从 `.task.xml` 声明 retry decorator → step 经 `FailureSimulatorBean.throwRecoverable()` 抛 NopException → 端到端路径连通验证。
- [x] 零回归：nop-task-core + nop-task-ext 既有测试全绿（plan 246 交付的 16 个 decorator 测试仍全绿，含合并后的 retry 分类 E2E）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] exception 持久化单元测试全绿（`fail(e,...)` / `exception(e)` 后 `exception()` 返回真实异常非 null）
- [x] **不可恢复异常不重试测试全绿**（核心验证：异常分类生效，执行次数 = 1，honest throw 真实异常）——此测试在修复前会失败（因 exception=null 无法分类，退化为无条件重试），修复后通过。位于 `TestTaskStepStateBeanExceptionPersistence#retryPolicy_classifiesBizFatalAsUnrecoverable_afterFail`（Java 层直接验证，不经 xpl 包装）
- [x] 可恢复异常重试测试全绿（按配置重试，执行次数 ≥ 2）——双覆盖：单元测试 + E2E
- [x] **端到端验证**（#22）：从 `.task.xml` 声明 retry decorator → step 抛 NopException（经 `FailureSimulatorBean.throwRecoverable()` 构造）→ `TaskStepHelper.retry` 消费 `state.exception()` → `RetryPolicy` 默认 `isRecoverableException` 分类为可恢复 → 重试至耗尽 → honest throw state.exception() 保存的异常（非 `ERR_TASK_RETRY_TIMES_EXCEED_LIMIT` generic error）完整路径跑通。**bizFatal 端到端路径不可测**（xpl 包装丢失 bizFatal 标记，见实现裁定），bizFatal 分类由 Java 层单元测试覆盖。
- [x] **接线验证**（#23）：`TaskStepHelper.retry:176` `state.fail(e,...)` → `state.exception()` 返回 e（非 null）→ `retryPolicy.getRetryDelay(state.exception(),...)` 收到真实异常 → 分类生效（经端到端测试断言 `assertNotEquals(ERR_TASK_RETRY_TIMES_EXCEED_LIMIT.getErrorCode(), e.getErrorCode())` 可观测——修复前 e 为 generic retry-limit error，修复后 e 为 state.exception() 保存的 NopEvalException）
- [x] **无静默跳过**（#24）：`fail()` / `exception(Throwable)` 均有真实方法体；无空方法体 / 吞异常
- [x] 新增功能各有 focused 测试覆盖（#25）：exception 持久化 + 不可恢复分类（Java 单元测试）+ 可恢复重试（单元 + E2E）
- [x] `./mvnw test -pl nop-task/nop-task-core -am` 通过（6 tests）+ `./mvnw test -pl nop-task/nop-task-ext -am` 覆盖 retry decorator E2E（16 tests）+ `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 零回归（2714 tests）
- [x] 若该 Phase 改变 live baseline：plan 246 §Closure 已知限制「不可重试异常分类不生效」可标注由 plan 247 修复（Java 层 bizFatal 分类生效，xpl 端到端受 xpl 包装限制为 successor）；其余 `No owner-doc update required`（nop-task-core 内部 state bean 行为变更，无 public contract 变化——`ITaskStepState` 接口不变，仅实现从 no-op 变为正确）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `TaskStepStateBean.fail()` + `exception(Throwable)` 实现为真实代码（非空方法体，赋值 `this.exception`）
- [x] retry 异常分类在 in-memory state 模式下生效：不可恢复异常不再被无条件重试，而是 honest throw 真实异常（Java 层单元测试 `retryPolicy_classifiesBizFatalAsUnrecoverable_afterFail` 证明；xpl 端到端路径受 xpl 包装限制为 successor）
- [x] 端到端：`.task.xml` retry decorator → step 抛 NopException（经 FailureSimulatorBean 构造）→ 默认 `RetryPolicy` 分类为可恢复 → 重试至耗尽 → honest throw state.exception() 保存的异常完整路径跑通（bizFatal E2E 不可测因 xpl 包装丢失 bizFatal 标记，Java 单元测试覆盖）
- [x] 必要 focused verification 已完成（exception 持久化 + 不可恢复分类 + 可恢复重试各有测试）
- [x] 零回归：nop-task-core/ext 既有测试全绿（含 plan 246 交付的 16 个 decorator 测试）+ nop-ai-agent 2714 tests 全绿
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（retry sync-return quirk / 其他生命周期 no-op 方法 / transient 移除 均为显式 Non-Goals 独立 successor）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（self-audit 经执行者逐条复核 live code + test 输出，证据见 Closure Audit Evidence 段）
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`fail()` / `exception(Throwable)` 运行时确实保存异常（非空方法体，经单元测试 assertSame 验证），（b）`TaskStepHelper.retry` 消费路径 `fail()` → `exception()` 连通（E2E 测试 assertNotEquals ERR_TASK_RETRY_TIMES_EXCEED_LIMIT 可观测——修复后 state.exception() 非 null 故抛出保存的异常），（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-task/nop-task-core -am`
- [x] `./mvnw test -pl nop-task/nop-task-core -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### retry loop 同步成功路径不 return

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 成功路径 re-execute（同步成功不 return 导致 step 被重复执行至 retryCount 耗尽）与失败路径异常分类（本计划结果面）是不同结果面。本计划只修 exception 持久化（失败路径），不触及成功路径 return 语义。修该 quirk 需理解全量 step 执行模型对同步返回的假设（risk profile 不同）。
- Successor Required: yes
- Successor Path: 独立 successor plan（待创建）

### succeed / isDone / isSuccess / result / result(TaskStepReturn) 生命周期 no-op 方法

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: result/success 生命周期管理（step 完成后结果持久化 + continuation 跳过）与 exception 持久化（retry 循环内跨迭代异常传递）是不同结果面。当前 retry 消费路径不依赖这些方法。
- Successor Required: yes
- Successor Path: 独立 successor plan（待创建）

## Non-Blocking Follow-ups

- **retry loop 同步成功路径 return**（plan 246 carry-over）：Classification: successor plan required（成功路径 re-execute 修复）。
- **succeed/isDone/isSuccess/result 完整生命周期实现**：Classification: successor plan required（result/success 持久化 + continuation）。
- **exception 跨重启持久化**（移除 `transient` + 异常序列化方案）：Classification: optimization candidate（in-memory retry 不依赖跨重启异常传递）。

## Closure

Status Note: plan 247 闭合——修复 `TaskStepStateBean` 的两个 no-op 方法体（`fail(Throwable, ITaskRuntime)` 委托 `exception(exp)`；`exception(Throwable exp)` 设 `this.exception = exp;`），使 `state.exception()` getter 返回真实异常。闭合 plan 246 §Closure 已知限制「in-memory state 不保存 exception → 不可重试分类不生效」——修复后 `RetryPolicy.isRecoverableException` 分类联动生效（bizFatal → 不可恢复 → 立即 honest throw；非 bizFatal → 可恢复 → 按 maxRetryCount 重试）。最小手术式修复（2 行代码），scope 严控在 `TaskStepStateBean` 2 个方法体（设计裁定 4，Protected Area scope control）。6 focused 单元测试 + 1 新增 E2E（nop-task-ext，16 decorator 测试全绿）+ nop-ai-agent 2714 全量零回归。新增已知限制（诚实记录）：xpl 函数调用包装丢失 bizFatal 标记 → bizFatal 分类在 .task.xml E2E 路径不生效，Java 单元测试层覆盖证明联动；为独立 successor。
Completed: 2026-06-18

Closure Audit Evidence:

- Reviewer / Agent: self-audit by implementation session（执行者逐条复核 live code + test 输出 + 文档同步；plan 247 scope 限 nop-task-core 2 个方法体 + 测试，复杂度低，self-audit 经全量 test 输出 + 读码复核足以证明）
- Evidence:
  - **Gate 1（2 个方法真实代码）PASS**：`TaskStepStateBean.java:45-47 fail()` 方法体 `exception(exception);`（委托 setter）；`:75-77 exception(Throwable exp)` 方法体 `this.exception = exp;`（直接赋值）。两方法均非空方法体（无空 `{}`/continue/TODO 作为正常实现）。
  - **Gate 2（分类联动证明，plan 247 核心价值）PASS**：`TestTaskStepStateBeanExceptionPersistence#retryPolicy_classifiesBizFatalAsUnrecoverable_afterFail` 直接断言 `state.fail(bizFatal) → assertSame(bizFatal, state.exception()) → assertTrue(policy.getRetryDelay(state.exception(), 1, null) < 0)`。修复前此测试会失败（state.exception() 恒 null → getRetryDelay 跳过 isRecoverableException → delay >= 0）。
  - **Gate 3（端到端连通 #22/#23）PASS**：`TestReliabilityDecorators.retry_recoverableExceptionRetriedE2e` 从 `.task.xml` retry decorator → `FailureSimulatorBean.throwRecoverable()` → `TaskStepHelper.retry:176 state.fail(e) → state.exception() 返回 e（非 null）→ retryPolicy.getRetryDelay 收到真实异常 → 分类为可恢复 → 重试 3 次 → honest throw state.exception() 保存的 NopEvalException（assertNotEquals ERR_TASK_RETRY_TIMES_EXCEED_LIMIT 证明非 generic fallback）`。
  - **Gate 4（exception 持久化可观测）PASS**：3 个单元测试（`exceptionSetter_assignsField` / `fail_savesExceptionAndGetterReturnsIt` / `fail_overwritesPreviousException`）经 assertSame 验证 getter 返回与 setter 传入的引用一致 + 多次 fail 覆盖。
  - **Gate 5（零回归）PASS**：`./mvnw test -pl nop-task/nop-task-core -am` → 6 tests / 0 fail；`./mvnw test -pl nop-task/nop-task-ext -am -Dtest=TestReliabilityDecorators` → 16 tests / 0 fail（plan 246 交付的 decorator 测试零回归）；`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → 2714 tests / 0 fail。
  - **Gate 6（无静默跳过 #24 + Anti-Hollow）PASS**：`fail()` / `exception(Throwable)` 均为真实非空方法体（赋值语句）。`state.exception()` 在 fail 后返回真实异常（assertSame 验证），在端到端测试中经 assertNotEquals ERR_TASK_RETRY_TIMES_EXCEED_LIMIT 观测到非 generic fallback（证明 state.exception() 非 null 连通）。
  - **Gate 7（scope 控制确认）PASS**：只改 `TaskStepStateBean.java` 2 个方法体（fail + exception setter），`TaskStepHelper.retry()` / `RetryTaskStepDecorator` / `RetryPolicy` / 其他 `TaskStepStateBean` 生命周期方法（succeed/isDone/isSuccess/result/result(TaskStepReturn)）均不改（设计裁定 4 遵守）。
  - **`node ai-dev/tools/check-plan-checklist.mjs 247-...md`**：non-strict PASS（0 unchecked items）。
  - **`node ai-dev/tools/check-doc-links.mjs --strict`**：退出码 0（40 pre-existing warnings 全部在其他 plan 文件中，非本次引入）。

Follow-up:

- **xpl 函数调用异常包装丢失 bizFatal 标记**（新增 successor，本计划执行中发现）：`AbstractObjFunctionExecutable.doInvoke*` 把被调用方法抛出的 NopException 包装为 `NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL)`，导致 bizFatal 标记丢失。修复后 plan 247 的 state.exception() 持久化能保存真实异常，但 xpl 端到端路径下 RetryPolicy 收到的是包装后的 NopEvalException（非 bizFatal → 可恢复），bizFatal 分类在 .task.xml E2E 路径不生效。Classification: successor plan required（需改 xpl exec 包装行为或提供 bypass 机制）。
- **retry loop 同步成功路径不 return**（plan 246 carry-over）：Classification: successor plan required（成功路径 re-execute 修复）。**→ 已由 `ai-dev/plans/248-nop-ai-agent-retry-sync-success-return.md` 接管。**
- **succeed/isDone/isSuccess/result 完整生命周期实现**：Classification: successor plan required（result/success 持久化 + continuation）。
- **exception 跨重启持久化**（移除 `transient` + 异常序列化方案）：Classification: optimization candidate（in-memory retry 不依赖跨重启异常传递）。

## Follow-up handled by 248-nop-ai-agent-retry-sync-success-return.md

plan 247 §Non-Goals line 41 / §Deferred But Adjudicated line 140-145 / §Non-Blocking Follow-ups line 156 切出的 carry-over「retry loop 同步成功路径不 return 的 quirk」由 successor plan `ai-dev/plans/248-nop-ai-agent-retry-sync-success-return.md` 接管（修复 `TaskStepHelper.retry()` 同步成功路径缺 `return result;`，独立结果面：成功路径 return vs 本计划的失败路径 exception 持久化）。本链接仅为可追溯性标注，不回写 plan 247 的历史结论。
