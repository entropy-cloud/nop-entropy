# 248 nop-task TaskStepHelper.retry 同步成功路径 return 修复（retry re-execute defect）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: retry-sync-return-quirk (carry-over from plan 247)

> Last Reviewed: 2026-06-18
> Source: carry-over from `ai-dev/plans/247-nop-ai-agent-task-state-exception-persistence.md`（§Non-Goals line 41「retry loop 同步成功路径不 return 的 quirk」+ §Deferred But Adjudicated line 140-145 + §Non-Blocking Follow-ups line 156 + §Closure Follow-up line 182，均 Classification: successor plan required）。该 carry-over 是 plan 246 交付 `RetryTaskStepDecorator`（用户经 `<decorator name="retry"/>` 接入）后暴露的最高杠杆正确性缺陷：plan 246 之前该路径仅被 first-class `retry=` attr 触发（内部消费），plan 246 把 retry decorator 提升为用户可配置原语后，每一个声明了 retry decorator 的同步成功 step 都会命中此缺陷（被重复执行至 retryCount 耗尽而非返回成功结果）。
> Related: `247`（交付 `TaskStepStateBean` exception 持久化——本计划修同一 `TaskStepHelper.retry()` 方法的另一独立结果面：同步成功路径 return）、`246`（交付 `RetryTaskStepDecorator`——本计划修复其依赖的 `TaskStepHelper.retry()` 同步成功路径缺陷）

## Purpose

修复 `TaskStepHelper.retry()` 同步成功路径缺少 `return result;` 的缺陷：当被 retry 包装的 step action 返回**同步（非 async）成功结果**时，`result.isAsync()` 为 false，控制流跳过 async 分支后**没有 return**，直接落入 `setRetryAttempt(retryAttempt + 1)` + `while(true)` 循环回顶，导致成功的 step 被反复重新执行直到 retryCount 耗尽（或 retry policy 终止）。

**为何需要独立 successor**：plan 247 闭合时诚实记录此限制为独立结果面（§Non-Goals：成功路径 re-execute vs 失败路径异常分类是不同结果面），且修复需理解全量 step 执行模型对同步返回的假设（risk profile 与 plan 247 的 no-op 方法体替换不同）。plan 246 刚把 retry decorator 提升为用户可配置原语（`<decorator name="retry"/>`），此前该缺陷仅经 first-class `retry=` attr 间接触发，此后每一个 retry-wrapped 同步成功 step 都直接命中——从「内部消费的潜在 quirk」升级为「用户可观测的正确性缺陷」。

## Current Baseline

基于 live repo 核对（引用位置为审计可复核的 live code path）：

- **缺陷位置（核心）**：`TaskStepHelper.retry()`（`nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:130-182`）的同步循环路径 `:166-181`：
  - `:166-167` `try { TaskStepReturn result = action.call();`
  - `:168-174` `if (result.isAsync()) { ... return ...; }` —— async 分支有 return
  - **`:174` 之后、`:175` catch 之前，缺少 `return result;`** —— 同步成功结果（`isAsync()==false`）不被返回，控制流落入 `:179 setRetryAttempt(retryAttempt + 1)` + `:180 saveState()` + `:181 while(true)` 循环回顶
- **缺陷后果（执行轨迹）**：retryAttempt=0 首轮 action.call() 返回同步成功 → 不 return → setRetryAttempt(1) → 循环回顶 → retryAttempt=1 进入 `:137 if (retryAttempt > 0)` 块 → `:138 retryPolicy.getRetryDelay(state.exception(), 1, ...)`（成功路径未调 fail()，故 `state.exception()` 为 null）→ 按 policy delay 值分支（delay<0 抛 `ERR_TASK_RETRY_TIMES_EXCEED_LIMIT` / delay==0 同步重执行 / delay>0 进 `:147-162` 延迟调度路径）。无论哪个分支，**同步成功结果都不在首轮返回**，成功 step 被重复执行至 retry policy 终止。
- **消费入口（plan 246 新暴露）**：`RetryTaskStepWrapper.execute()`（`nop-task/nop-task-core/src/main/java/io/nop/task/step/RetryTaskStepWrapper.java:27-30`）委托 `TaskStepHelper.retry(...)`。plan 246 交付的 `RetryTaskStepDecorator`（`nop-task/nop-task-ext`）返回此 wrapper，用户经 `.task.xml` 的 `<decorator name="retry"/>` 即触发。此前仅 first-class `<step retry="..."/>` 经 `TaskStepEnhancer.buildRetryPolicy` 内部消费。
- **`TaskStepHelper.retry()` 的 async / 失败路径正确**：`:168-174` async 分支（`result.isAsync()`）正确 return（已 done 则 `result.sync()`，未 done 则 `thenCompose(doRetry)`）；`:175-177` catch 块 `state.fail(e, ...)` + 落入 `:179` setRetryAttempt 重试（plan 247 已使 fail() 保存异常供下轮分类）—— 失败重试路径无此缺陷。
- **`state.exception()` 在成功路径为 null（非缺陷，预期）**：成功路径不调 fail()，故 `:138` 收到 null exception。这不是 plan 247 的结果面（plan 247 修复的是失败路径 exception 持久化），本计划修复成功路径 return，两者正交。
- **`TaskStepStateBean` 已就绪（plan 247）**：`fail()` / `exception(Throwable)` 已为真实实现（plan 247 闭合），本计划不改 state bean。

## Goals

- **修复同步成功路径 return**：`TaskStepHelper.retry()` 同步循环路径（`:166-181`）在 `action.call()` 返回非 async 结果时立即 `return result;`，使成功 step 不被重复执行。
- **focused 测试验证**：retry-wrapped 同步成功 step 执行**恰好一次**（修复前会执行多次直到 retry policy 终止——该测试在修复前失败、修复后通过）。
- **回归验证**：retry 失败重试路径（plan 246/247 已测）行为不变；nop-task-core + nop-task-ext + nop-ai-agent 既有测试全绿。
- **端到端验证**：从 `.task.xml` 声明 `<decorator name="retry"/>` → 同步成功 step → `RetryTaskStepWrapper` → `TaskStepHelper.retry` → 首轮即返回成功结果（执行一次）完整路径连通。

## Non-Goals

- **`TaskStepHelper.retry()` 延迟调度路径（`:147-162`）**：该路径仅在 retryAttempt>0 且 delay>0 时（即**前序迭代已失败**）进入。其内部在 `thenApply` 中再次 `action.call()` 的语义与同步循环路径不同，是失败重试的延迟执行机制。本计划只修同步循环路径（首轮同步成功的 return），延迟调度路径的 sync 处理为独立分析对象。Classification: out-of-scope improvement（未确认其为缺陷，且属失败重试执行面）。
- **`succeed()` / `isDone()` / `isSuccess()` / `result()` / `result(TaskStepReturn)` 等其他 no-op 生命周期方法**（plan 247 carry-over）：result/success 生命周期管理与 retry 控制流 return 是不同结果面。Classification: successor plan required。
- **`TaskStepStateBean` 变更**：plan 247 已交付 exception 持久化，本计划不改 state bean。Classification: rejected（已完成 / Protected Area scope control）。
- **`RetryTaskStepDecorator` / `RetryPolicy` / first-class `retry=` attr 变更**：本计划只修 `TaskStepHelper.retry()` 同步成功 return，decorator/policy/attr 均不改。Classification: rejected（Protected Area scope control）。
- **DB-backed state / exception 跨重启持久化（transient 移除）**：Classification: optimization candidate（in-memory retry 不依赖跨重启）。

## Scope

### In Scope

- `TaskStepHelper.retry()`（`nop-task/nop-task-core/.../utils/TaskStepHelper.java:166-181`）同步循环路径：`action.call()` 返回非 async 结果时 `return result;`
- focused 测试：retry-wrapped 同步成功 step 执行一次（核心回归测试，修复前失败）+ retry 失败重试路径不变（回归）
- 端到端验证（nop-task-ext）：`.task.xml` retry decorator → 同步成功 step → 执行一次返回
- 零回归：nop-task-core + nop-task-ext（plan 246 的 16 个 decorator 测试）+ nop-ai-agent 全量

### Out Of Scope

- 见 Non-Goals（延迟调度路径 / 其他生命周期 no-op 方法 / TaskStepStateBean / decorator / RetryPolicy / first-class attr / DB state 均为显式 rejected / successor / out-of-scope）

### Granularity Justification

本计划为单一 work item（单方法单分支 1 行 return 修复 + 测试），不与其它 carry-over bundle，理由：(1) **无可行 bundle 伙伴**——同模块（nop-task-core）的其它 carry-over（`task-state-lifecycle-noops`）是 P2、不同结果面（result/success 生命周期 vs retry 控制流）、且 scope 更大（5+ 方法 + continuation 语义），bundle 会违反 Minimum Rules #2（一个计划只负责一个明确结果面）并延迟 P0 修复；(2) **P0 live defect + Protected Area**——plan 246 已把该缺陷从内部消费升级为用户可观测， urgency 要求聚焦快速闭合，Protected Area（nop-task-core）要求 plan-first 仪式（AGENTS.md）；(3) **测试是价值主体**——证明「执行恰好一次」需理解 retry 循环并构造区分首轮返回 vs 重复执行的断言，非平凡。Litmus test 经 (2)(3) 满足：ceremony 由 Protected Area + P0 正确性缺陷正当化，非空壳。

### 设计裁定（Pre-Adjudicated）

1. **修复 = 同步循环路径 `action.call()` 返回非 async 结果时 `return result;`**。`TaskStepReturn` 的 sync（非 async）结果代表 step 已完成（成功）；retry 语义是针对**失败**（由 catch 块捕获 + fail() + setRetryAttempt 触发）。当前缺陷把 sync 成功误判为「需重试」。`return result;` 置于 `:168-174 if (result.isAsync())` 块之后、`:175 catch` 之前。理由：(1) sync 结果 = 完成态，应立即返回；(2) 与 async 分支（`:168-174` 已 return）对称——sync 与 async 两条完成路径都应 return，仅失败（catch）才进 retry 循环；(3) 最小手术式（1 行），不改变失败重试 / async 路径语义。

2. **scope 限于同步循环路径（`:166-181`），不触及延迟调度路径（`:147-162`）**。延迟调度路径仅在 retryAttempt>0 且 delay>0（前序迭代已失败）时进入，是失败重试的延迟执行机制，与「首轮同步成功不 return」是不同执行面。理由：(1) 最小手术式修复降低回归风险；(2) 延迟调度路径未确认缺陷，扩展 scope 会引入未经验证的变更；(3) Protected Area scope control。

3. **不改失败重试路径语义（plan 246/247 已验证）**。catch 块 `:175-177`（fail + setRetryAttempt + 循环）、`:137-164` retryAttempt>0 重试块、`:184-195 doRetry` 均不改。理由：(1) 失败重试路径经 plan 246（16 decorator 测试）+ plan 247（exception 分类测试）验证正确；(2) 本计划只补成功路径 return，失败路径零变更 = 回归风险最小。

4. **诚实失败（#24）保留**：修复后 sync 成功路径 return 真实结果（非吞掉、非 placeholder）；失败路径仍经 catch → fail → 重试/throw（plan 247 已使 fail() 保存异常）。无新增静默跳过。

## Execution Plan

### Phase 1 - 同步成功路径 return 修复 + compile

Status: completed
Targets: `nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java`

- Item Types: `Fix`（confirmed live defect：同步成功路径缺 `return result;` 导致成功 step 被 re-execute 至 retry policy 终止，违反 retry 语义——retry 应只针对失败）

- [x] 在 `TaskStepHelper.retry()` 同步循环路径 `:168-174 if (result.isAsync())` 块之后、`:175 catch` 之前，新增 `return result;`（使 `action.call()` 返回非 async 成功结果时立即返回，不再落入 setRetryAttempt 循环）
- [x] 确认失败重试路径（catch / retryAttempt>0 块 / doRetry）零变更

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TaskStepHelper.retry()` 同步循环路径在 `action.call()` 返回非 async 结果时 `return result;`（读码可复核：`if (result.isAsync())` 块后有 `return result;`，sync 成功不再落入 setRetryAttempt 循环）
- [x] async 分支（`:168-174`）return 语义不变；catch 失败重试路径（`:175-181`）零变更
- [x] **无静默跳过**（#24）：sync 成功路径 return 真实结果（非空方法体吞掉 / 非 continue / 非 TODO）
- [x] `./mvnw compile -pl nop-task/nop-task-core -am` 通过
- [x] 若该 Phase 改变 live baseline：`No owner-doc update required: 内部控制流修复，`TaskStepHelper.retry` / `ITaskStep` 无 public contract 变化，retry decorator 行为从「缺陷」收敛为「正确」`（无需 design/doc 更新）
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 1 实现 landing）

### Phase 2 - focused 测试 + 端到端验证 + 零回归 + 文档

Status: completed
Targets: `nop-task/nop-task-core/src/test/`（focused 单元测试）、`nop-task/nop-task-ext/src/test/`（端到端测试，复用 plan 246 `TestReliabilityDecorators` 同位置）、`ai-dev/logs/`

- Item Types: `Proof`

- [x] 编写同步成功执行一次单元测试：直接调 `TaskStepHelper.retry(...)`，action 返回**同步（非 async）`TaskStepReturn`** + 计数器 → 断言 action 执行**恰好一次**（executeCount == 1），且返回结果为该同步结果。**此测试修复前失败**（executeCount > 1，因成功不 return 触发循环重执行）、修复后通过
- [x] 编写 retry 失败重试路径回归测试：action 抛可恢复异常 → 断言按 retry 配置重试（executeCount == maxRetryCount+1）+ 最终 honest throw —— 证明失败重试路径不受本修复影响（plan 246/247 行为不变）
- [x] 编写端到端测试（nop-task-ext）：`.task.xml` 声明 `<decorator name="retry"/>` 包装一个同步成功 step → 断言 step 执行一次返回成功结果（从 decorator 声明到 `TaskStepHelper.retry` 首轮返回完整连通）
- [x] 零回归：nop-task-core + nop-task-ext（含 plan 246 的 16 个 decorator 测试）+ nop-ai-agent 既有测试全绿

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **同步成功执行一次测试全绿**（核心验证：executeCount == 1）——此测试修复前失败（sync 成功不 return → 循环重执行）、修复后通过，是本计划核心 Proof
- [x] retry 失败重试路径回归测试全绿（executeCount == maxRetryCount+1，行为不变）
- [x] **端到端验证**（#22）：从 `.task.xml` `<decorator name="retry"/>` → 同步成功 step → `RetryTaskStepWrapper.execute` → `TaskStepHelper.retry` → 首轮返回成功结果（执行一次）完整路径跑通
- [x] **接线验证**（#23）：`RetryTaskStepWrapper.execute:28` → `TaskStepHelper.retry` → sync 成功 `return result;` 连通（经端到端测试断言 step 执行一次 + 返回成功结果可观测）
- [x] **无静默跳过**（#24）：sync 成功路径 return 真实结果；无空方法体 / 吞异常 / placeholder
- [x] 新增功能各有 focused 测试覆盖（#25）：sync 成功执行一次（单元 + E2E）+ 失败重试回归（单元）
- [x] `./mvnw test -pl nop-task/nop-task-core -am` 通过 + `./mvnw test -pl nop-task/nop-task-ext -am` 覆盖 retry decorator E2E + `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 零回归
- [x] 若该 Phase 改变 live baseline：plan 247 §Closure Follow-up line 182「retry loop 同步成功路径不 return」可标注由 plan 248 修复；其余 `No owner-doc update required`（内部控制流修复，无 public contract 变化）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `TaskStepHelper.retry()` 同步成功路径 `return result;` 已落地（sync 成功不再 re-execute）
- [x] retry-wrapped 同步成功 step 执行恰好一次（核心缺陷修复证明：修复前失败的测试修复后通过）
- [x] retry 失败重试路径行为不变（plan 246/247 既有行为零回归）
- [x] 端到端：`.task.xml` retry decorator → 同步成功 step → 首轮返回完整路径连通
- [x] 必要 focused verification 已完成（sync 成功执行一次 + 失败重试回归 + E2E）
- [x] 零回归：nop-task-core + nop-task-ext（16 decorator 测试）+ nop-ai-agent 全绿
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（延迟调度路径 / 其他生命周期 no-op 方法 / DB state 均为显式 Non-Goals 独立 successor）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`TaskStepHelper.retry` sync 成功 `return result;` 在运行时确实执行（经 executeCount==1 测试可观测），（b）`RetryTaskStepWrapper` → `TaskStepHelper.retry` 调用链连通（E2E），（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-task/nop-task-core -am`
- [x] `./mvnw test -pl nop-task/nop-task-core -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### TaskStepHelper.retry() 延迟调度路径（:147-162）sync 处理

- Classification: `watch-only residual`
- Why Not Blocking Closure: 延迟调度路径仅在 retryAttempt>0 且 delay>0（前序迭代已失败）时进入，是失败重试的延迟执行机制，与「首轮同步成功不 return」（本计划结果面）是不同执行面。本计划只修首轮同步成功 return；延迟调度路径的 sync 处理未确认为缺陷，扩展 scope 会引入未经验证变更。
- Successor Required: no（未确认缺陷；若执行中确认延迟调度路径亦有 sync-return 问题，则升级为独立 successor）

## Non-Blocking Follow-ups

- **`succeed/isDone/isSuccess/result` 完整生命周期实现**（plan 247 carry-over）：Classification: successor plan required（result/success 持久化 + continuation，不同结果面）。
- **exception 跨重启持久化（transient 移除）**（plan 247 carry-over）：Classification: optimization candidate（in-memory retry 不依赖跨重启）。

## Closure

Status Note: `TaskStepHelper.retry()` 同步成功路径 `return result;` 已落地（`TaskStepHelper.java:175`），retry-wrapped 同步成功 step 执行恰好一次（单元 + E2E 双重证明），失败重试路径行为不变（plan 246/247 零回归），端到端 `.task.xml` retry decorator → 首轮返回完整路径连通。无剩余 plan-owned work：延迟调度路径 / 其他生命周期 no-op / DB state 均为显式 Non-Goals（独立 successor 或 optimization candidate）。
Completed: 2026-06-18

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure-audit session（task_id: ses_126e2da91ffeBn3CXJKnYUc99X，fresh session，非实现者）+ 实现者复核确认审计发现为 stale-build 误报
- Evidence:
  - **Phase 1 Exit Criteria（全 PASS）**：
    - `TaskStepHelper.retry()` 同步循环路径 `return result;` 位于 `if (result.isAsync())` 块后、`catch` 前（`TaskStepHelper.java:175`）—— 读码复核 PASS
    - async 分支（`:168-174`）return 语义不变；catch 失败重试路径（`:176-181`）零变更 —— 读码复核 PASS
    - 无静默跳过：return 真实 `result`（非空方法体 / 非 continue / 非 TODO）—— PASS
    - `./mvnw compile -pl nop-task/nop-task-core -am` → BUILD SUCCESS —— PASS
  - **Phase 2 Exit Criteria（全 PASS）**：
    - `TestTaskStepHelperRetrySyncReturn.syncSuccess_executesExactlyOnce`：executeCount == 1 + 返回 "OK"（核心 Proof，修复前 executeCount > 1 且抛 retry-times-exceed-limit）—— PASS
    - `TestTaskStepHelperRetrySyncReturn.recoverableFailure_retriesUntilExhausted_unchanged`：executeCount == 3（1+maxRetryCount）+ honest throw（失败重试路径不变）—— PASS
    - `TestReliabilityDecorators.retry_syncSuccessExecutesExactlyOnce`：E2E `.task.xml` retry decorator → sync 成功 step → executeCount == 1 + 返回 "OK" —— PASS
    - 接线验证（#23）：`RetryTaskStepWrapper.execute:28` → `TaskStepHelper.retry` → sync 成功 `return result;` 连通（经 E2E executeCount==1 + 返回 "OK" 可观测）—— PASS
    - 新增功能各有 focused 测试覆盖（#25）：sync 成功执行一次（单元 + E2E）+ 失败重试回归（单元）—— PASS
    - `./mvnw test -pl nop-task/nop-task-core -am` → 34 tests / 0 fail；`./mvnw test -pl nop-task/nop-task-ext -am` → 24 tests / 0 fail（含 17 decorator 测试）；`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → 2714 tests / 0 fail —— PASS
  - **Closure Gates（全 PASS）**：见上方逐条勾选
  - **审计发现处置**：独立子 agent 初报 BLOCK CLOSURE（称 `retry_syncSuccessExecutesExactlyOnce` 在全类运行时因 test-isolation pollution 失败，17/0/1）。实现者复核发现根因为 **stale .m2 build**：子 agent 运行测试时未带 `-am`，Maven 使用 .m2 中**未含修复的旧版** nop-task-core（实现者仅 `compile`/`test` 未 `install`），导致 sync 成功路径未 return → 触发 retry-times-exceed-limit。子 agent 将此误判为 test-isolation pollution。**处置**：`./mvnw install -pl nop-task/nop-task-core -am -DskipTests` 将修复装入 .m2 后，`./mvnw test -pl nop-task/nop-task-ext -Dtest=TestReliabilityDecorators`（不带 -am）→ **17 tests / 0 fail**（clean run + 3 consecutive runs 全绿），证实为 stale-build 误报而非真实 test-isolation 缺陷。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/248-nop-ai-agent-retry-sync-success-return.md --strict` 退出码为 0（所有 checklist 已勾选 + Closure Evidence 已写入）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-task/nop-task-core --severity high` 退出码为 0（无 high/critical 空壳实现发现）
  - **Anti-Hollow 检查**：（a）`TaskStepHelper.retry:175 return result;` 在运行时确实执行（经 `syncSuccess_executesExactlyOnce` executeCount==1 + E2E executeCount==1 可观测）；（b）`RetryTaskStepWrapper.execute:28` → `TaskStepHelper.retry` 调用链连通（E2E 从 `.task.xml` `<decorator name="retry"/>` 到首轮返回成功结果）；（c）无空方法体/静默跳过/no-op（FakeTaskStepRuntime 未用方法抛 UnsupportedOperationException，遵循 #24）
  - Deferred 项分类检查：延迟调度路径（watch-only residual）/ 生命周期 no-op（successor）/ DB state（optimization candidate）均为显式 Non-Goals，无 in-scope live defect 被降级

Follow-up:

- `succeed/isDone/isSuccess/result` 完整生命周期实现（plan 247 carry-over）：successor plan required（不同结果面）
- exception 跨重启持久化（transient 移除）（plan 247 carry-over）：optimization candidate（in-memory retry 不依赖跨重启）
- TaskStepHelper.retry() 延迟调度路径（:147-162）sync 处理：watch-only residual（未确认缺陷，若执行中确认则升级为独立 successor）

## Follow-up handled by 252-nop-ai-agent-task-step-state-lifecycle.md

plan 248 §Non-Goals line 41 / §Non-Blocking Follow-ups line 149 切出的 carry-over「`succeed/isDone/isSuccess/result/result(TaskStepReturn)` 完整生命周期实现」由 successor plan `ai-dev/plans/252-nop-ai-agent-task-step-state-lifecycle.md` 接管（实现 `TaskStepStateBean` 5 个 no-op 生命周期方法的 result/success 状态机，独立结果面：result/success 持久化数据层 vs 本计划的 retry 控制流 return）。本链接仅为可追溯性标注，不回写 plan 248 的历史结论。
