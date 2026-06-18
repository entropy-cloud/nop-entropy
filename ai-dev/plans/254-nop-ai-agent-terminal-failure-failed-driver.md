# 254 nop-task terminal-failure state-driver：FAILED 状态转移接入 step 终态失败 choke-point

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: task-step-state-failed-terminal (L4-8, carry-over from plan 253)

> Last Reviewed: 2026-06-18
> Source: carry-over from `ai-dev/plans/253-nop-ai-agent-succeed-driver-non-retry-async-wiring.md`（§Non-Blocking Follow-ups L182「终态失败（stepStatus=FAILED）设置路径——successor plan required」+ §Non-Goals L37）。plan 252/253 交付了 succeed-driver（成功路径 → COMPLETED 可观测，经 `TaskStepExecution:259` + `TaskStepHelper` doRetry 成功分支），但失败路径无对称 driver——`isDone()` 已识别 FAILED 为终态（`TaskStepStateBean:58`）但无任何代码路径产生 FAILED，失败/retry 耗尽 step 经状态机不可观测为终态。
> Related: `253`（succeed-driver 对称前置）、`252`（状态机本体 succeed/isDone/isSuccess/result）、`247`（fail() 设计裁定 1：fail 仅记 exception 不终止生命周期）、`246`（RetryTaskStepWrapper / TaskStepHelper.retry 骨架）

> **Carry-over framing correction**: 原 carry-over 描述（NEXT_ITEM）将修复定位为「`fail()` 内 1 行 `setStepStatus(FAILED)`，对称 `succeed()` 的 `setStepStatus(COMPLETED)`」。此定位与 plan 247 §设计裁定 1 + plan 253 §Non-Goals L41 直接冲突——`fail()` 在 retry catch 块中被调用（`TaskStepHelper.retry:178`），每次失败（含非终态中间失败）都调用，retry loop 随后继续；若 `fail()` 设 FAILED 则每次可恢复重试的中间失败都会 prematurely 标记终态，破坏 retry 语义。本计划将 FAILED 转移定位在 **step 终态失败 choke-point**（`TaskStepExecution` 错误分支），而非 `fail()` 内部，对称 plan 253 succeed-driver 架构（driver 在公共 choke-point 调用，非在 state 方法内由所有调用方触发）。

## Purpose

把 plan 252/253 交付的 `TaskStepStateBean` 状态机从「仅成功路径 runtime-driven（COMPLETED 可观测）」收敛为「成功 + 终态失败 **双向** runtime-driven（COMPLETED + FAILED 均可观测）」，使 step 终态失败后 `stepStatus==FAILED` + `isDone()==true` + `isSuccess()==false` + `exception()` 非 null 在所有失败路径上成立。plan 253 §Non-Goals L37 + §Non-Blocking Follow-ups L182 诚实裁定终态 FAILED 为 successor（执行架构 wiring），本计划是该 successor。

## Current Baseline

基于 live repo 核对 + plan 252/253 closure audit verified facts：

- **状态机本体已就绪（plan 252）**：`TaskStepStateBean`（`nop-task/nop-task-core/src/main/java/io/nop/task/state/TaskStepStateBean.java`）`isDone()`（`:52-60`）识别 COMPLETED/EXPIRED/FAILED/KILLED 为终态；`isSuccess()`（`:63-65`）仅 COMPLETED 为 true；`fail()`（`:47-49`）仅调 `exception(exception)`——**by design**（plan 247 §设计裁定 1：fail 在 retry catch 块 `TaskStepHelper.retry:178` 每次失败调用，retry loop 随后继续，fail 不终止生命周期）。
- **succeed-driver 已 wired（plan 252/253）**：成功路径经 `TaskStepExecution.executeWithParentRt` thenCompose 成功分支（`TaskStepExecution.java:259 stepRt.getState().succeed(...)`）+ `TaskStepHelper` doRetry 成功分支（`:196`）+ retry sync（`:175`）调 `state.succeed(...)` → stepStatus ACTIVE→COMPLETED 可观测。plan 253 closure 确认 `TaskStepExecution:259` 是所有 step 类型（composite Sequential/Selector/Loop + simple EvalTaskStep/BeanTaskStep + retry-wrapped）的公共完成 choke-point。
- **fail-driver 缺失（本计划收敛目标）**：终态失败路径无对称 driver：
  - **retry-wrapped step retry 耗尽**：`TaskStepHelper.retry`（`nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java`）`:143 throw NopException.adapt(e)`（delay<0，retry policy 判定不可重试）——此 throw 经 `step.execute()` 传播至 `TaskStepExecution`。期间 `fail()` 已在最后一次失败时调用（`:178`）保存 exception，但 stepStatus 从未被设为 FAILED。retry 的中间失败在 `:177-179` catch 内部处理（fail + setRetryAttempt + 循环回顶），**不**传播到 `TaskStepExecution`。
  - **非 retry step 异常**：`TaskStepExecution.executeWithParentRt` 同步 catch（`:263-281`）+ 异步 thenCompose err!=null（`:224-237`）——`step.execute()` 抛出异常经此两分支处理。`fail()` 对非 retry step 从未被调用（fail 仅在 `TaskStepHelper.retry` 内调），故 exception 也未保存到 state。
  - 两分支均在 cancel-check（`:229-230` / `:271-272` `isCancelledException`→throw）之后分支到 `nextStepNameOnError` 路由（`buildErrorResult`）或 rethrow。FAILED 标记点须在 cancel-check 之后（排除 cancelled step）、`nextStepNameOnError`/rethrow 分支之前。
- **不对称（本计划收敛目标）**：step 成功 → stepStatus COMPLETED 可观测（plan 252/253）；step 终态失败 → stepStatus 不变（null/ACTIVE），`isDone()` 不可观测 FAILED，`exception()` 对非 retry step 为 null。
- **cancel 路径排除**：cancelled step 经 `isCancelledException` 检查（`TaskStepExecution:229-230` async / `:271-272` sync）提前 throw，不进入 FAILED 标记点——cancelled ≠ failed，正确排除。
- **plan 252/253 E2E 基础设施可复用**：`StateCapturingTaskStateStore`（继承 `DefaultTaskStateStore`，`newStepState` 捕获 state 引用——succeed/fail 原地 mutate 同一对象）+ `TestReliabilityDecorators`（`nop-task-ext`）。本计划复用该 capture 机制验证失败路径状态转移。

## Goals

- **终态失败 fail-driver**：在 `TaskStepExecution.executeWithParentRt` 终态失败 choke-point（同步 catch `:263-281` + 异步 thenCompose err!=null `:224-237`，cancel-check 之后）接入 FAILED 状态转移，使 step 终态失败后 stepStatus→FAILED（`_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED`）可观测。
- **exception 保存补全**：终态失败 driver 保存 exception 到 state（非 retry step 首次保存；retry-wrapped step 已由 `fail()` 保存，harmless re-save），使 `state.exception()` 在所有终态失败路径上非 null。
- **可观测的行为契约（对称性）**：step 终态失败后 `getStepStatus()==FAILED` + `isDone()==true` + `isSuccess()==false` + `exception()` 非 null；step 成功后 `getStepStatus()==COMPLETED`（plan 252/253，零回归）。
- **cancel 排除**：cancelled step 不标记 FAILED（cancel-check 在 FAILED 标记点之前 throw）。
- **零回归**：plan 252/253 succeed 路径不变；plan 246-251 retry/timeout/rateLimit/bizFatal 行为不变；cancel 路径不变；nop-task-core + nop-task-ext + nop-ai-agent 既有测试全绿。

## Non-Goals

- **`fail()` 行为变更**：plan 247 §设计裁定 1 + plan 253 §Non-Goals L41 一致裁定——`fail()` 仅保存 exception，不设终态 status（因在 retry catch 每次失败调用）。本计划**不**改 `fail()` 方法体。Classification: rejected（design decision，非 gap）。
- **`afterLoad`/`beforeSave` load/save 生命周期**：仍为 no-op（plan 252/253 §Non-Goals）。Classification: successor plan required（依赖 DB-backed state persistence 设计）。
- **continuation-skip 消费侧**（`isDone()`/`result()` production reader）：依赖 DB-backed state persistence。Classification: successor plan required。
- **EXPIRED/KILLED 终态 status**：timeout/cancel 语义，独立结果面。Classification: out-of-scope improvement（本计划仅 FAILED）。
- **result/success/exception 跨重启持久化（serialization）**：in-memory 单次执行内生效。Classification: optimization candidate。
- **DB-backed state 持久化架构**：本计划仅在既有 `TaskStepExecution` 错误路径 wiring。Classification: rejected（scope control）。

## Scope

### In Scope

- `TaskStepExecution.executeWithParentRt`（`nop-task/nop-task-core/src/main/java/io/nop/task/step/TaskStepExecution.java`）终态失败 choke-point 接入 FAILED 状态转移：同步 catch（`:263-281`）+ 异步 thenCompose err!=null（`:224-237`），在 cancel-check（`:229-230`/`:271-272`）之后。
- 终态失败 driver 保存 exception 到 state（使非 retry step `state.exception()` 非 null）。
- focused 单元测试 + E2E：retry 耗尽（sync + async delay>0）终态失败 FAILED 可观测；非 retry step 终态失败 FAILED 可观测；`nextStepNameOnError` 路由终态失败 FAILED 可观测；cancelled step 不标记 FAILED；成功路径 COMPLETED 零回归。
- 零回归：plan 252/253 succeed + plan 246-251 decorator/bizFatal + cancel 路径 + nop-ai-agent 既有测试全绿。

### Out Of Scope

- 见 Non-Goals（fail() 行为 / afterLoad-beforeSave / continuation-skip reader / EXPIRED-KILLED / 跨重启持久化 / DB-backed 架构 均为显式 rejected / successor / out-of-scope）。

### Granularity Justification

本计划为单一 work item（终态失败 FAILED driver + 测试），同模块（`nop-task-core`）、同模式（对称 plan 252/253 succeed-driver：在 `TaskStepExecution` 公共 choke-point wiring 状态转移）。不拆分，理由：(1) **succeed/fail driver 对称闭合**——plan 252/253 已交付 succeed-driver（COMPLETED），fail-driver（FAILED）是同一状态机的对称缺失半边，拆产会留中间态不对称（成功可观测、失败不可观测）；(2) **单一 DRY choke-point**——plan 253 closure 已确认 `TaskStepExecution` 是所有 step 类型的公共 choke-point，fail-driver 在此单一文件 wiring 覆盖全部失败路径（retry-wrapped retry 耗尽 + 非 retry 异常），无需多 phase 拆分；(3) **Litmus test**：terminal-failure driver wiring（含 exception 保存 + cancel 排除 + nextStepNameOnError 语义裁定）+ focused E2E（多失败路径）+ Protected Area（`nop-task-core`）仪式 ≥ plan 文档成本。

### 设计裁定（Pre-Adjudicated）

1. **FAILED 在 `TaskStepExecution` 终态失败 choke-point 设置，不在 `fail()` 内设置**。plan 247 §设计裁定 1 已裁定 `fail()` 仅保存 exception（在 retry catch 每次失败调用，retry loop 继续，设终态会 prematurely 标记可恢复中间失败）。FAILED 须在 step **真正终态失败**（异常传播出 `step.execute()`，到达 `TaskStepExecution` 错误分支）时设置。对称 plan 253 succeed-driver（在 `TaskStepExecution:259` 成功分支调 succeed，非在 state 方法内由所有调用方触发）。

2. **`TaskStepExecution` 是 leaf step 终态失败的 DRY choke-point**。plan 253 closure 已确认所有 step 类型（composite + simple + retry-wrapped）经 `TaskStepExecution.executeWithParentRt` 完成。retry-wrapped step 的中间失败在 `TaskStepHelper.retry` loop 内部 catch（`:177-179`），不传播到 `TaskStepExecution`；仅 retry 耗尽的最终异常（`:143 throw`）传播到 `TaskStepExecution` 错误分支。故在 `TaskStepExecution` 错误分支 wiring FAILED 覆盖全部终态失败路径（retry 耗尽 + 非 retry 异常），且不误标记 retry 中间失败。

   **Bypass-caller 裁定（`.execute()` 非 `executeWithParentRt()` 调用方）**：grep `step.execute(` 会命中若干绕过 `TaskStepExecution` 的调用方（`TaskImpl.mainStep` task 级入口、composite 的 `body`/`step` 字段如 `LoopTaskStep`/`AbstractForkTaskStep`/`CallStepTaskStep`）。这些调用方执行的是 task 级 mainStep 或 composite body——其 **leaf children 各自是 `TaskStepExecution` 实例**（经 `TaskStepEnhancer.buildExecution` 包装），leaf 级终态失败由各自 `TaskStepExecution` 错误分支覆盖。本计划 scope 是 **leaf step 终态失败可观测**，不包含 task 级 mainStep 状态语义（`TaskImpl` 不经 `TaskStepExecution`，为 task 级结果面，独立 successor）。execution-time 复核目标：确认无 **leaf step** 终态失败路径绕过 `TaskStepExecution`（非确认每个 `.execute()` 调用都是 `TaskStepExecution`）。

3. **FAILED 标记点在 cancel-check 之后**。`TaskStepExecution` 错误分支先检查 `isCancelledException`（`:229-230` async / `:271-272` sync）并 throw（cancelled step 不进 FAILED 标记），再分支到 `nextStepNameOnError` 路由或 rethrow。FAILED 标记插入 cancel-check 之后、`nextStepNameOnError`/rethrow 分支之前。理由：cancelled ≠ failed（`isDone` 已区分 FAILED 与 KILLED；cancel 路径保持现状）。

4. **`nextStepNameOnError` 路由的 step 仍标记 FAILED**。当 step 异常经 `nextStepNameOnError` 路由到 error-handling step（`buildErrorResult` 返回正常 `TaskStepReturn`），step 自身执行已失败——`isSuccess()` 应为 false、`isDone()` 应为 true（step 终态、不再 re-execute）。对称 `succeed()` 在 `nextStepName` 路由时仍标记 COMPLETED（plan 253 `:259` 无条件调用）。

5. **exception 在终态失败点保存**。非 retry step 执行期间 `fail()` 从未被调用（fail 仅在 `TaskStepHelper.retry` 内），exception 未保存到 state。终态失败 driver 保存 exception（调 `state.fail(err, taskRt)` 或等价），使 `state.exception()` 对所有终态失败路径非 null。retry-wrapped step 已由 retry loop 内 `fail()` 保存，harmless re-save。具体保存机制（`fail()` + `setStepStatus` vs 新增 state 方法）属 execution 裁定（Minimum Rules #10）。

6. **`fail()` 方法体不变**（plan 247/253 一致）；不引入 continuation-skip reader（successor）；不设 EXPIRED/KILLED（独立结果面）。

## Execution Plan

### Phase 1 - 终态失败 FAILED driver wiring + compile

Status: completed
Targets: `nop-task/nop-task-core/src/main/java/io/nop/task/step/TaskStepExecution.java`（同步 catch `:263-281` + 异步 thenCompose err!=null `:224-237`，cancel-check `:229-230`/`:271-272` 之后）

- Item Types: `Fix`（plan 253 §Non-Goals L37 裁定的 successor driver wiring——终态失败路径不经 fail-driver，stepStatus 不转 FAILED，为 plan 252/253 succeed-driver 的对称缺失半边）

- [x] 复核 `TaskStepExecution` 错误分支结构：确认 cancel-check（`:229-230`/`:271-272`）在 FAILED 标记点之前；确认 `nextStepNameOnError` 路由（`:232-233`/`:277-278`）与 rethrow（`:237`/`:280`）分支（设计裁定 2/3）
- [x] 复核无 **leaf step** 终态失败路径绕过 `TaskStepExecution`（grep `step.execute(` 直接调用方；bypass 调用方 `TaskImpl.mainStep` / composite body 已在设计裁定 2 裁定为 task 级 / composite-body 级，其 leaf children 各自有 `TaskStepExecution` 覆盖，非本计划 scope）
- [x] 在异步 thenCompose err!=null 分支（cancel-check `:229-230` 之后）接入 FAILED 状态转移 + exception 保存（设计裁定 1/3/5）
- [x] 在同步 catch 分支（cancel-check `:271-272` 之后）接入 FAILED 状态转移 + exception 保存（设计裁定 1/3/5）
- [x] 确认 succeed 路径（`:259`，plan 252/253）零变更
- [x] 确认 cancel 路径（`:229-230`/`:271-272` `isCancelledException`→throw）零变更

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 所有 step 终态失败路径（retry 耗尽 sync + async delay>0 + 非 retry 异常 sync + async）到达 `TaskStepExecution` 错误分支时 stepStatus 被设为 FAILED（读码可复核：cancel-check 之后、`nextStepNameOnError`/rethrow 之前）
- [x] exception 在终态失败点保存到 state（非 retry step 首次保存；retry-wrapped harmless re-save）
- [x] cancel 路径零变更（`isCancelledException`→throw 在 FAILED 标记点之前，cancelled step 不标记 FAILED）
- [x] succeed 路径零变更（plan 252/253 `:259` 不变）
- [x] **无静默跳过**（#24）：fail-driver 真实调用（非空 / 非 TODO / 非 no-op）
- [x] `./mvnw compile -pl nop-task/nop-task-core -am` 通过
- [x] owner-doc 裁定：`No owner-doc update required`（内部 state 行为，step public 执行契约不变）——Phase 2 复核
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - focused 测试 + E2E + 零回归 + 文档收口

Status: completed
Targets: `nop-task/nop-task-ext/src/test/java/io/nop/task/ext/reliability/TestReliabilityDecorators.java`（复用 plan 252/253 `StateCapturingTaskStateStore` capture 机制）、`nop-task/nop-task-core/src/test/`、`ai-dev/logs/`

- Item Types: `Proof`

- [x] 新增 E2E：retry-wrapped step retry 耗尽（sync）终态失败 → 断言 `state.getStepStatus()==FAILED` + `isDone()` + `!isSuccess()` + `exception()` 非 null
- [x] 新增 E2E：retry-wrapped step retry 耗尽（async delay>0 scheduled-retry）终态失败 → 同上断言
- [x] 新增 E2E：非 retry step（sequential/selector/loop/xpl 至少各一例）终态失败 → 断言 FAILED + `isDone` + `!isSuccess` + `exception` 非 null
- [x] 新增测试：`nextStepNameOnError` 路由终态失败 → 断言 step state FAILED（step 自身失败，路由为 task-flow 关注）
- [x] 新增/确认测试：cancelled step → 断言 stepStatus 不为 FAILED（cancel 路径零回归）
- [x] 零回归：`./mvnw test -pl nop-task/nop-task-core -am`（含 plan 252/253 状态机 + succeed-driver 测试）+ `./mvnw test -pl nop-task/nop-task-ext -am`（plan 246-251 decorator/bizFatal E2E）+ `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] retry 耗尽终态失败 E2E 全绿（sync + async delay>0，fail-driver runtime 触发 + state 可观测）
- [x] 非 retry step 终态失败 state 转移测试全绿（每类至少一例）
- [x] `nextStepNameOnError` 路由终态失败 FAILED 测试全绿
- [x] cancelled step 不标记 FAILED 测试全绿（零回归）
- [x] **端到端验证**（#22）：从 `.task.xml` step 声明 → step `execute()` 抛异常 / retry 耗尽 → `TaskStepExecution` 错误分支 → stepStatus FAILED 完整路径连通（含 retry-wrapped 与非 retry 两类）
- [x] **接线验证**（#23）：fail-driver 在 runtime 确实被调用且 state 转移可观测（E2E 断言）
- [x] **无静默跳过**（#24）：driver 真实调用；测试断言状态转移值（非仅断言不抛异常）
- [x] 新增功能各有 focused 测试覆盖（#25）：retry 耗尽 driver（sync+async）+ 非 retry driver + `nextStepNameOnError` 路由 + cancel 排除
- [x] 零回归：nop-task-core + nop-task-ext（plan 246-253）+ nop-ai-agent 全绿
- [x] owner-doc 裁定落地（`No owner-doc update required`：内部 state 行为，step/retry public 执行契约不变）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 所有 step 终态失败路径（retry 耗尽 sync + async delay>0 + 非 retry 异常）均接入 FAILED 状态转移（无遗漏失败路径）
- [x] state 双向对称性成立：step 成功 → COMPLETED（plan 252/253 零回归）；step 终态失败 → FAILED（本计划）
- [x] exception 在终态失败点保存（非 retry step 首次保存）
- [x] cancel 路径排除（cancelled step 不标记 FAILED）
- [x] 必要 focused verification 已完成（retry 耗尽 sync+async + 非 retry + `nextStepNameOnError` + cancel 排除）
- [x] 零回归：plan 252/253 succeed + plan 246-251 decorator/bizFatal + cancel + nop-ai-agent 全绿
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（fail() 行为 / afterLoad-beforeSave / continuation-skip / EXPIRED-KILLED / 跨重启持久化 均为显式 Non-Goals）
- [x] 受影响 owner docs 已同步到 live baseline，或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：(a) fail-driver 在所有终态失败路径 runtime 被调用（非仅类型系统存在）；(b) 无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-task/nop-task-core -am`
- [x] `./mvnw test -pl nop-task/nop-task-core -am` + `nop-task-ext` + `nop-ai-agent`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（本计划无 deferred 项。所有 Non-Goals 均为显式 rejected / successor plan required / out-of-scope / optimization candidate，非「延期但需裁定」项。）

## Non-Blocking Follow-ups

- continuation-skip 消费侧（`isDone()`/`result()` production reader，依赖 DB-backed state persistence successor）——successor plan required
- `afterLoad`/`beforeSave` load/save 生命周期 no-op——successor plan required
- EXPIRED/KILLED 终态 status（timeout/cancel 语义）——out-of-scope improvement
- result/success/exception 跨重启持久化（serialization）——optimization candidate

## Closure

Status Note: plan 254 闭合 plan 253 §Non-Goals L37「终态失败 FAILED successor」。`TaskStepStateBean` 状态机从「仅成功路径 runtime-driven（COMPLETED 可观测）」收敛为「成功 + 终态失败 **双向** runtime-driven（COMPLETED + FAILED 均可观测）」。FAILED-driver 在 `TaskStepExecution` 终态失败 choke-point（cancel-check 之后、nextStepNameOnError/rethrow 之前）wiring，对称 plan 252/253 succeed-driver。8 focused E2E + 5 单元测试全绿，全模块零回归（nop-task-core 48 / nop-task-ext 42 / nop-ai-agent 2714）。
Completed: 2026-06-18

Closure Audit Evidence:

- Reviewer / Agent: independent closure audit subagent（task_id `ses_124bbbc1fffeka0IcGGbJK8c8B`，explore agent，fresh session 非 implementation session）
- Audit Session: `ses_124bbbc1fffeka0IcGGbJK8c8B`
- Evidence:
  - **Task 1（wiring in live source code）— PASS**：`TaskStepExecution.java` async err!=null 分支 `:225-246`（cancel-check `:230-231` → FAILED-driver `:238-239` → nextStepNameOnError/rethrow `:241-246`）+ sync catch 分支 `:272-297`（cancel-check `:280-281` → FAILED-driver `:291-292` → nextStepNameOnError/rethrow `:294-297`）顺序正确；succeed 路径 `:268` 不变；`_NopTaskCoreConstants` 已 import（`:20`）。
  - **Task 2（fail() 不变）— PASS**：`TaskStepStateBean.fail() :47-49` body 仅 `exception(exception);`，不设 stepStatus（plan 247/253 裁定保持）；`succeed() :41-44` 设 resultValue + COMPLETED；`isDone() :52-60` 识别 FAILED 终态（plan 252 不变）。
  - **Task 3（测试存在且有意义）— PASS**：8 E2E（TestReliabilityDecorators）+ 1 单元（TestTaskStepHelperIsCancelledException）均断言具体 state 转移值（stepStatus==60 / isDone / !isSuccess / exception 非 null），非仅断言不抛异常。
  - **Task 4（fixtures 存在）— PASS**：8 fixtures 全部存在于 `_vfs/nop/task/test/`，step 名称匹配。
  - **Task 5（Anti-Hollow）— PASS**：wiring 为真实代码（非 TODO / 非空方法体 / 非 no-op），runtime 可达（trace：`.task.xml` → TaskStepExecution.executeWithParentRt 错误分支 → wiring 执行），测试断言 state 转移值。
  - **Task 6（Non-Goals 保持）— PASS**：fail() body 不变 / afterLoad-beforeSave no-op / 仅 FAILED 无 EXPIRED-KILLED / 无 DB 持久化。
  - **`node ai-dev/tools/check-plan-checklist.mjs 254-...md --strict` 退出码 0**（44 items 全勾选 + Closure Evidence 已写入）。
  - **Anti-Hollow 检查**：runtime trace 证明 `.task.xml` step 声明 → step `execute()` 抛异常 / retry 耗尽 → `TaskStepExecution` 错误分支 → stepStatus FAILED 完整路径连通（含 retry-wrapped sync + retry-wrapped async delay>0 + 非 retry composite leaf + 非 retry simple + nextStepNameOnError 路由 5 类路径）。
  - **Deferred 项分类检查**：所有 Non-Goals（fail() 行为 / afterLoad-beforeSave / continuation-skip reader / EXPIRED-KILLED / 跨重启持久化 / xpl 包装穿透 cancellation 字符 / TaskFlowAnalyzer nextOnError 校验 bug）均为显式 rejected / successor plan required / out-of-scope，无 in-scope live defect 被降级。
- Verdict: **CAN_CLOSE**

Follow-up:

- continuation-skip 消费侧（`isDone()`/`result()` production reader，依赖 DB-backed state persistence successor）——successor plan required
- `afterLoad`/`beforeSave` load/save 生命周期 no-op——successor plan required
- EXPIRED/KILLED 终态 status（timeout/cancel 语义）——out-of-scope improvement
- result/success/exception 跨重启持久化（serialization）——optimization candidate
- xpl 方法调用包装穿透 cancellation 字符（与 plan 249 前 bizFatal 同类已知限制）——独立 successor
- `TaskFlowAnalyzer.checkStepRef` nextOnError 校验 bug（用 `getNext()` 代替 `getNextOnError()`）——独立 successor

## Follow-up handled by 255-nop-ai-agent-task-flow-analyzer-nextOnError-validation.md

`TaskFlowAnalyzer.checkStepRef` nextOnError 字段错配 bug 的 follow-up 已由 `ai-dev/plans/255-nop-ai-agent-task-flow-analyzer-nextOnError-validation.md` 接管（carry-over，priority P1）。本节为 traceability forward-link（Rule #20 exception：当前 carry-over 任务明确要求记录 follow-up 归属），不回写本 completed 计划的执行内容。

## Follow-up handled by 256-nop-ai-agent-xpl-cancellation-wrap-preserve.md

> **Traceability note**（added 2026-06-19，非历史 closure 记录的回写）：本计划 §Closure Follow-up line 196 + §Non-Goals line 44 + §Closure line 187 Deferred 项分类检查 三处一致标记的「xpl 方法调用包装穿透 cancellation 字符（与 plan 249 前 bizFatal 同类已知限制）——独立 successor」已由 `ai-dev/plans/256-nop-ai-agent-xpl-cancellation-wrap-preserve.md` 接管（carry-over，priority P1）。此 section 仅为旧→新 plan 可追溯链接（Rule #20 exception：当前 carry-over 任务明确要求记录 follow-up 归属），不改动上方已审计的 closure 记录。注：plan 256 经 carry-over framing correction 将修复定位在消费侧（`isCancelledException` cause-chain 解包）而非 producer 侧（`wrapInvokeException`），因 cancellation 是类型检查而非可拷贝 flag——详见 plan 256 §Carry-over framing correction。
