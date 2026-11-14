# 2570 信号型完结≠生命周期终态语义错位修复（绝缘层登记摘除 + job 扫描器存活判定）

> Plan Status: completed
> Last Reviewed: 2026-11-14
> Source: `ai-dev/analysis/2026-11/2026-11-14-signal-vs-terminal-semantics-audit.md`（C-1/C-2/C-3 CONFIRMED）；设计契约 `ai-dev/design/concurrency/periodic-task-insulation.md`
> Related: 无活跃相关计划

## Purpose

把「future 异常完结被误当生命周期终态」的三处确认缺陷收口：绝缘链守卫登记摘除时机（C-1/C-2）与 job 扫描器存活判定（C-3）。修复后：失败信号完结的链保持被 destroy() 统一拒绝路径覆盖；fixedRate 链在 timer destroy 后必有终态；job 扫描器只在设施级拒绝时停摆。

## Current Baseline

- `InsulatedPeriodicChain` javadoc 契约：返回 future 是失败/取消/拒绝**信号**，不是链存活指标；链存活判定必须用 `isStopped()`（已落地并有测试钉）。
- `DefaultScheduledExecutor.trackPeriodicTask`（:173-178）当前用裸 `whenComplete` 摘除登记——首次任务体失败（completeExceptionally 信号）即摘除，live defect。
- `DefaultScheduledExecutor.destroy()`（:136-167）遍历 `periodicTasks` 对每个 guard 调 `rejectChain` 统一收口——该路径因 C-1 对失败过的链失效。
- fixedRate 六参路径（:246-275）的 native handle 是 JDK ScheduledFuture，非 CompletionStage，`PeriodicTaskHelper.watchNativeHandle` 对其 no-op——destroy 后无补终态通道，live defect。
- `AbstractBatchScanner.startScanning`（nop-job-core :106-117）当前用 whenComplete + 非 Cancellation 异常判定翻 `running=false`——把失败信号误读为链拒绝，live defect，影响 6 个 job 扫描器子类。
- `IPeriodicTaskListener` 提供 `onChainRejected` 回调，是设施级拒绝的权威信号源（`InsulatedPeriodicTask.rejectChain` 调用）。
- `nop-autotest` 的 `MockScheduledExecutor` 是 autotest 侧载体（nop-commons/nop-job-core 测试**不可依赖**，依赖方向相反）；nop-commons 绝缘层测试载体为真实 `DefaultScheduledExecutor`（`TestPeriodicTaskInsulation` 已存在）。

## Goals

- C-1：失败信号完结的绝缘链保持登记，destroy() 统一拒绝路径覆盖所有活链（含失败过的）。
- C-2：fixedRate 链在 timer destroy 后必有链终止终态（经 destroy 的 guard 遍历 rejectChain 达成：`isStopped` 置位 + native handle cancel + `onChainRejected` 回调必达）。注意：链 future 的终态原因可能已被首次失败信号占用（completeExceptionally 幂等），修复的可观察效果是 rejectChain 被补执行，而非 future 终态原因改变。
- C-3：`AbstractBatchScanner` 只在设施级拒绝（onChainRejected）时翻转 running/计数/日志；单轮任务体失败不停摆扫描业务。
- 三处修复均有先红后绿回归钉，防退化。

## Non-Goals

- 不改 `InsulatedPeriodicChain`/`InsulatedPeriodicTask`/`PeriodicTaskHelper` 的绝缘语义与公共契约（它们本身正确）。
- 不改 fixedRate/fixedDelay 速率与退避契约。
- 不审计或修改 nop-ai/nop-stream/nop-code/nop-tools 等本轮排除模块内的消费方。
- 不处理 S-1（AutoRegistration watch-only，见 analysis Open Questions）。

## Scope

### In Scope

- `nop-kernel/nop-commons/.../executor/DefaultScheduledExecutor.java`（trackPeriodicTask）
- `nop-job/nop-job-core/.../AbstractBatchScanner.java`（startScanning 存活判定）
- `ai-dev/design/concurrency/periodic-task-insulation.md`（登记摘除时机契约句更新）
- 回归测试：nop-commons（绝缘层钉）、nop-job-core（扫描器存活钉）

### Out Of Scope

- 其余消费方改动（审计确认 OK 的 27 站点不动）
- MockScheduledExecutor / BindScheduledExecutor 行为变更

## Execution Plan

### Phase 1 - C-1/C-2 修复：trackPeriodicTask 摘除时机（nop-commons）

Status: completed
Targets: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/DefaultScheduledExecutor.java`

- Item Types: `Fix | Proof`

- [x] `trackPeriodicTask` 的 whenComplete 回调仅在 `chain.isStopped()` 为 true 时摘除 guard（失败信号完结不摘除）；注释同步说明「摘除只认链终止，失败信号完结的链保持登记以维持 destroy 覆盖」
- [x] 回归钉（先红后绿，断言对象=链终止三可观察量而非 future 终态原因——链 future 在首次失败时已被失败信号终态化，completeExceptionally 幂等）：fixedDelay 链首次任务体失败后调用 `destroy()`，断言 (a) 匿名 listener 捕获到 `onChainRejected` 且 cause 为 service-not-active；(b) 链 `isStopped()` 为 true。修复前：guard 已被裸 whenComplete 摘除，destroy 不触达该链，(a)(b) 均 fail
- [x] 回归钉（先红后绿）：fixedRate 链首次任务体失败后 destroy() → 同样断言 `onChainRejected` 必达 + 链 `isStopped()` 置位 + native handle `isDone()`（被 cancel）
- [x] 既有 `TestPeriodicTaskInsulation` 与 nop-commons executor 族测试全绿（登记摘除时机变更不得破坏既有路径——钉住：**失败前** cancel → guard 被摘除（whenComplete 在取消完结时触发且 isStopped 为 true）；已失败链后续 cancel 不再触发摘除属预期（guard 驻留至 destroy，rejectChain 对 stopped 链安全），repeat destroy 不 NPE）

Exit Criteria:

- [x] 上述 3 个回归钉先红后绿并保持绿（变异探针：DSE 摘除无条件化 → FixedDelay/FixedRate 双红；恢复 → 12/12 绿；nop-commons 全模块 481/481 绿）
- [x] `ai-dev/design/concurrency/periodic-task-insulation.md` 已更新登记摘除时机契约句（只认 isStopped）
- [x] `./mvnw test -pl nop-kernel/nop-commons -am` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - C-3 修复：AbstractBatchScanner 存活判定（nop-job）

Status: completed
Targets: `nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java`

（实施期触发面修正见 analysis C-3 行）

- Item Types: `Fix | Proof`

- [x] 摘除 `startScanning` 中 whenComplete 存活判定；改为向 `scheduleWithFixedDelay` 六参传入 `IPeriodicTaskListener`（四回调均 default，仅需覆写 onChainRejected），仅在其 `onChainRejected` 回调中：`scanChainRejectedCount.incrementAndGet()`、`running=false`、ERROR 日志（沿用 `nop.job.scan-chain-rejected` 日志键；与绝缘层自带 `nop.err.periodic-task.chain-rejected` 形成双条日志，属预期，在此声明）
- [x] 单轮任务体失败不停摆钉（先红后绿）：测试载体覆写 protected `getExecutor()` 返回测试专用的独立 `DefaultScheduledExecutor`（**禁止** globalTimer 单例——destroy 会污染共享池；**不可用** MockScheduledExecutor——位于 nop-autotest-core，nop-job-core 测试依赖方向不可达）。scanOnce 抛异常一轮后，在超时窗内轮询 `scanCycleCount` 再次增长（失败重臂 ≥1s 下限 + interval 钳制，至少需 ~2s 真实等待），断言 running 保持 true
- [x] 设施级拒绝停摆钉：链拒绝回调触发时 running=false、scanChainRejectedCount 增长（拒绝语义不回退）
- [x] 既有 nop-job-core 扫描器族测试全绿（6 个子类共享此基类，重点跑 coordinator/worker 扫描器测试）

Exit Criteria:

- [x] 上述 2 个回归钉先红后绿并保持绿（变异探针：onRunFailure 翻 running 模拟旧语义 → 钉 1 红；恢复 → 2/2 绿；nop-job-core 全模块 150/150 绿）。实施期修正：触发面为「外层 catch 链异常 + onScanCycleFailed rethrow」，常规 scanBatch 失败被 2496 边界隔离（已同步修正 analysis C-3）
- [x] 行为变更在 daily log 登记（原「异常终态→running=false」判定废除的语义变更声明）
- [x] `./mvnw test -pl nop-job/nop-job-core -am` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] C-1/C-2/C-3 全部修复并有先红后绿回归钉
- [x] 绝缘层契约文档（periodic-task-insulation.md）与 live baseline 一致
- [x] 不存在被静默降级的 in-scope live defect（S-1 保持 watch-only 登记于 analysis，属 scope 外裁定）
- [x] 独立子 agent closure audit 完成并写入证据
- [x] Anti-Hollow Check：修复路径从「任务体失败 → 链存活 → destroy 收口」与「scanOnce 抛 → 下轮仍执行」两条链路均有运行时测试证据，非仅类型存在
- [x] `./mvnw test -pl nop-kernel/nop-commons,nop-job/nop-job-core -am` 全绿

## Deferred But Adjudicated

### S-1 AutoRegistration 判读语义

- Classification: `watch-only residual`
- Why Not Blocking Closure: 当前 refreshRegistration 任务体 catch 全部 Throwable，缺陷触发前提（守卫移除）不成立，行为正确
- Successor Required: `no`
- Successor Path: analysis 报告 Open Questions 登记

### 排除模块同族消费方审计

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: nop-ai/nop-stream/nop-code/nop-tools 为本轮用户明示排除范围
- Successor Required: `no`
- Successor Path: 后续需要时另开 analysis

## Non-Blocking Follow-ups

- nop-job owner doc（docs-for-ai/03-modules/nop-job.md）若含扫描器拒绝语义描述，执行时顺带核对（属 Phase 2 文档核对义务）

## Closure

Status Note: 三处 CONFIRMED 缺陷（C-1 登记摘除时机 / C-2 fixedRate destroy 补终态通道 / C-3 扫描器存活判定）全部修复并有先红后绿变异探针回归钉；契约句落 design/concurrency/periodic-task-insulation.md；C-3 触发面经实施期实证修正（scanOnce 2496 边界隔离常规 scanBatch 失败），已回写 analysis。独立子 agent closure audit APPROVED-with-corrections，文本收口（本段 + 6 Closure Gates 勾选）即其 corrections 的落实。
Completed: 2026-11-14

Closure Audit Evidence:

- Reviewer / Agent: 独立 Explore 子代理（closure-audit session agent_c2ba1dac-e474-4b05-8c3f-d9a7199fe756）
- Audit Session: agent_c2ba1dac-e474-4b05-8c3f-d9a7199fe756
- Evidence:
  - Phase 1 Exit Criteria：3 钉存在（TestPeriodicTaskInsulation L419-449/L456-493/L500-514，断言对象=onChainRejected/isStopped/native cancel）PASS；design 契约句 L61 与 DSE L181-182 一致 PASS；nop-commons 481/481 绿（实现者记录，audit 按指示未复跑）
  - Phase 2 Exit Criteria：2 钉存在（TestScannerChainRejectionR2570 L78-90/L102-116，逃逸链 onCycleStart→onScanCycleFailed rethrow 与主类 L198-204 连通）PASS；nop-job-core 150/150 绿
  - Anti-Hollow：链 a（失败→guard 驻留→destroy L156-159→rejectChain L139-142→onChainRejected）与链 b（六参 scheduleWithFixedDelay→L270 guard→L100-107 running=false）代码级连通 PASS
  - `check-plan-checklist.mjs --strict` 退出码 0（收口前与收口后各一次）
  - Deferred 分类检查：S-1 watch-only 与排除模块两项 non-blocking 理由成立，无 in-scope live defect 被降级
  - 审计结论 APPROVED-with-corrections；corrections（4 处 checkbox + Closure 段填写 + Status→completed）已在本文件落实

Follow-up:

- S-1 AutoRegistration 判读语义：watch-only（analysis Open Questions 登记），触发前提（任务体守卫移除）当前不成立
- nop-ai/nop-stream 等排除模块的同族消费方审计：用户明示 scope 外，需要时另开 analysis
