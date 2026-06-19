# 268 nop-job 扫描循环错误隔离与派发语义修复

> Plan Status: completed
> Last Reviewed: 2026-06-19
> Source: `ai-dev/audits/2026-06-19-0931-adversarial-review-nop-job/01-open-findings.md`（AR-85, AR-86, AR-87, AR-93）
> Related: `ai-dev/archived/2026-06/111-nop-job-r9-and-deep-audit-remediation.md`（completed，含 R9 AR-73 completion 批隔离修复）

## Purpose

把 dispatcher/worker 的 `scanOnce` 从"单点失败令整批 fire/task 卡死或重复执行、未知派发模式静默兜底"的脆弱状态，收口为"单 fire/单任务失败被隔离、派发模式误配显式失败、资源不足时排队而非判失败"的健壮状态。

## Current Baseline

- R9 AR-73（completion processor 批隔离）已修复（`JobCompletionProcessorImpl` 每 fire 独立 try/catch），`./mvnw test -pl nop-job -am` BUILD SUCCESS。
- dispatcher `JobDispatcherScannerImpl.scanOnce:131-166` 与 worker `JobWorkerScannerImpl.scanOnce:162-220` 仍用单个 try 包整个 per-fire/per-task 循环；dispatcher 还先 `tryLockFiresForDispatch` 把全部 fire 翻 DISPATCHING 再循环。
- `JobDispatcherScannerImpl.resolveTaskBuilder:168-186` 对未知/未注册 dispatchMode 静默 fallback（executorKind → defaultTaskBuilder）；`ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED`（`JobCoreErrors:99`）已定义并被 import，但全仓库无抛出点（死代码）。
- worker `executeTask:240-246` 忽略 `taskStore.updateTask()` 返回值，CAS 失败仍 `invokeAsync`（同类 `handleExecutionResult:316` 则检查返回值）。
- worker overfetch `tasks.isEmpty():205` 静默 return，无日志/指标。

## Goals

- 单个 fire/task 的异常（含 no-fitting-worker、requireEntityById 找不到 schedule）不波及同批次其余 fire/task。
- CLAIMED→RUNNING 的 CAS 失败时，worker 不再调用已失去所有权的任务（消除重复执行）。
- 未知/未注册的 `dispatchMode` 显式失败（抛出既有错误码或硬 ERROR 日志），不再静默退化为单例派发。
- 资源/容量不足导致无可认领任务时，有可观测信号（日志/指标）而非静默停滞。

## Non-Goals

- 不改资源限制的 double-count（归 Plan 267）。
- 不改 best-fit 算法/命名、capacity 0 语义、优先级索引（归 Plan 269）。
- 不改"no-fitting-worker 应排队而非判失败"之外的超时回收窗口数值（仅改 fail/defer 语义归属）。

## Scope

### In Scope

- dispatcher scanOnce per-fire 错误隔离 + 预锁 fire 失败回退（AR-86）
- worker scanOnce / executeTask per-task 错误隔离（AR-86 worker 侧）
- worker CLAIMED→RUNNING 检查 updateTask 返回值（AR-85）
- 恢复 ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED 显式失败（AR-87）
- worker overfetch 无可认领任务时的可观测信号（AR-93）

### Out Of Scope

- overfetch 窗口/饥饿升级算法本身（仅加可观测信号；深度饥饿升级可作 follow-up）。
- dispatcher 跨事务并发模型（归 Plan 267/后续）。

## Cross-Plan Execution Order

本计划 Phase 1/2 修改 `JobWorkerScannerImpl.scanOnce` 与 `JobDispatcherScannerImpl.scanOnce`，与 Plan 267 改动同一方法体。**本计划必须晚于 Plan 267 落地**（267 先修正 reserved/cost 语义，本计划再在其上叠加 per-element 错误隔离），执行时协调两计划对同一循环体的合并。

## Execution Plan

### Phase 1 - 扫描循环 per-element 错误隔离 + no-fitting-worker defer

> **方向裁定（回应对抗审查 Blocker-1/B2）**：per-fire 隔离用独立 try/catch 解决"一个 fire 失败阻塞整批"；对 `ERR_JOB_NO_FITTING_WORKER`（worker 满载的正常瞬态）采用 revert+backoff，而非留 fire 卡 DISPATCHING 等 5min 判失败，也非裸回退（避免 DISPATCHING↔WAITING 紧循环）。

Status: completed
Targets: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java`, `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java`, `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/IJobFireStore.java`, `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java`

- Item Types: `Fix`, `Decision`

- [x] dispatcher `scanOnce` 的 per-fire 循环包独立 try/catch：单个 fire 抛异常时以 WARN/ERROR 记 `fireId` + 派发指标并继续后续 fire（不令其余 fire 卡 DISPATCHING）
- [x] worker `scanOnce`/`executeTask` 的 per-task 处理包独立 try/catch：单个任务 `loadFire/loadSchedule` 抛异常时以 WARN 记 `taskId` + 指标并继续其余已认领任务
- [x] no-fitting-worker defer：捕获 `ERR_JOB_NO_FITTING_WORKER` 后，新增 `IJobFireStore.revertDispatchingFireToWaiting(fire, backoffUntilMs)`（DISPATCHING→WAITING + 版本检查）把 fire 回退为可重派发；并加 backoff（Decision：新配置 `nop.job.coordinator.no-worker-backoff-ms` 默认 30s，回退时在 fire.startTime 记录 backoff 截止，`fetchWaitingFires` 跳过未到截止的 fire），避免 DISPATCHING→WAITING→DISPATCHING 紧循环

Exit Criteria:

- [x] 回归测试：dispatcher 批次中第 k 个 fire 抛异常（schedule 已删 / no-fitting-worker），第 k+1..n 个 fire 仍被正常派发（`TestJobCoordinatorScanner#testPerFireIsolationBadFireDoesNotBlockRest` + `#testNoFittingWorkerRevertsToWaitingWithBackoff`）
- [x] 回归测试：worker 批次中某任务 loadFire/loadSchedule 抛异常，其余已认领任务仍被执行（`TestJobWorkerScanner#testPerTaskIsolationBadTaskDoesNotBlockRest`）
- [x] 回归测试：no-fitting-worker 的 fire 回退 WAITING 后，在 backoff 窗口内不被重复预锁（无紧循环）（`testNoFittingWorkerRevertsToWaitingWithBackoff` 第二轮 scanOnce 断言仍 WAITING）
- [x] **端到端验证**：混入一个"坏 fire"的调度批次，其余 fire 从派发→执行→完成完整跑通（testPerFireIsolationBadFireDoesNotBlockRest 验证 good fire RUNNING+task 插入）
- [x] **无静默跳过（#24）**：每个新增 catch 块以 WARN/ERROR 记 fireId/taskId + 发指标（onFireDispatchFailed/onTaskExecuteFailed），测试断言指标确实产生
- [x] **接线验证**：`revertDispatchingFireToWaiting` 在运行时被调用（fire 状态→WAITING + startTime 未来时间戳断言）
- [x] `./mvnw test -pl nop-job -am` 全过（coordinator 19 + worker 12，0 failures）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - CLAIMED→RUNNING 所有权校验

Status: completed
Targets: `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java`

- Item Types: `Fix`

- [x] worker `executeTask` 检查 `taskStore.updateTask(runningTask)` 返回值；CAS 失败（任务已被超时检查器移交给他人）时以 WARN 记 `taskId` 并跳过 `invokeAsync`，与 `handleExecutionResult:316` 行为对齐
- [x] 消除"失去所有权后仍执行任务"导致的重复执行路径

Exit Criteria:

- [x] 回归测试：模拟 CLAIMED→RUNNING 期间任务被超时检查器扫成 SUSPICIOUS（CAS 失败），worker 不再调用该任务的 invoker（`TestJobWorkerScanner#testClaimCasFailureSkipsInvoker`，用 FailingCasTaskStore 使 updateTask 返回 false）
- [x] **接线验证**：CAS 失败分支确实阻止 invokeAsync（`invokeCount[0]==0` 断言 invoker 未被调用）
- [x] **无静默跳过（#24）**：CAS 失败分支 WARN 记 taskId 并发 onTaskExecuteFailed 指标（非静默 return）
- [x] `./mvnw test -pl nop-job -am` 全过（worker 14 tests，0 failures）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - dispatchMode 误配显式失败 + overfetch 可观测信号

> **方向裁定（回应对抗审查 Major-1）**：精确边界——仅当 `dispatchMode` 非空/非 blank/非 single 且其 bean 不存在时抛 `ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED`；`dispatchMode∈{null,blank,single}` 时**保留** executorKind→default fallback（守护 rpcBroadcast-via-executorKind 合法路由，见既有 `TestJobDispatcherScannerRouting`）。

Status: completed
Targets: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java`, `nop-job/nop-job-core/src/main/java/io/nop/job/core/JobCoreErrors.java`, `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java`

- Item Types: `Fix`

- [x] 恢复 `ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED` 使用：`dispatchMode` 非空/非 blank/非 single 且 `nopJobTaskBuilder_<dispatchMode>` bean 不存在时抛该错误码；`dispatchMode∈{null,blank,single}` 仍走 executorKind→defaultTaskBuilder fallback（不抛）
- [x] worker overfetch 后 `tasks.isEmpty()`（有候选但无一 fit）分支增加 WARN 日志与指标，使饥饿/停滞可诊断（与 `candidates.isEmpty()` 无任务场景区分）
- [x] 核对既有 `TestJobDispatcherScannerRouting` 的 fallback 用例（dispatchMode=single/null + executorKind 路由）仍通过（6 tests 全过）

Exit Criteria:

- [x] 回归测试：配置一个未注册的 dispatchMode（如 "typo"），派发抛 `ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED`（可观测），不再静默退化为单例（`TestJobDispatcherScannerRouting#testUnknownDispatchModeThrowsExplicitly`）
- [x] 回归测试：`dispatchMode=null/single` + `executorKind=rpcBroadcast` 仍正确路由到广播 builder（fallback 路径不被误伤，既有 `testDispatchModeSingleFallsBackToExecutorKind`/`testDispatchModeNullFallsBackToExecutorKind` 通过）
- [x] 回归测试：worker overfetch 有候选但无一 fit 时产生可观测信号（`TestJobWorkerScanner#testOverfetchNoFittingCandidateEmitsSignal`，断言 onRejected + 候选保持 WAITING）
- [x] **无静默跳过**：未知 dispatchMode 分支是显式抛异常而非静默兜底（见 Minimum Rules #24）
- [x] **端到端验证**：dispatchMode 误配由 per-fire 隔离捕获（Phase 1），fire 层面可观测（onFireDispatchFailed 指标 + 留 DISPATCHING 由 timeout 回收），而非静默单 task 执行
- [x] `./mvnw test -pl nop-job -am` 全过（routing 6 + coordinator 19 + worker 14，0 failures）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] AR-85/86/87/93 的 confirmed live defect 已修复且有回归测试
- [x] 单点失败不再波及同批次；未知 dispatchMode 显式失败；CAS 失败不再重复执行
- [x] 不存在被静默降级到 deferred 的 in-scope live defect（overfetch 深度饥饿升级为 optimization candidate，前提 AR-83/AR-88 已由 Plan 267 修复）
- [x] 受影响 owner docs 已同步（`docs-for-ai/03-modules/nop-job.md` dispatchMode 显式失败行为 + no-worker-backoff 配置）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：错误隔离与显式失败分支在运行时确实被触发（per-fire/per-task catch 发指标；CAS-fail 经 invokeCount==0 断言；revert 经 fire→WAITING+future startTime 断言）
- [x] `./mvnw compile -pl nop-job -am`
- [x] `./mvnw test -pl nop-job -am`（全模块 BUILD SUCCESS，0 failures）
- [x] checkstyle 通过（commit 时 ast-grep Java lint 通过）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/268-nop-job-scan-loop-isolation-and-dispatch-semantics.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-job --severity high` 退出码 0（0 findings）

## Deferred But Adjudicated

### overfetch 深度饥饿升级算法

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本计划只补可观测信号；深度饥饿升级（按 cost 二次扫描等）是性能优化，不影响正确性契约成立。前提是 Plan 267 的 AR-83（double-count）与 AR-88（WAITING 回收）已修复——否则饥饿叠加 double-count + 无回收构成永久 live defect，此时本 deferred 不成立
- Successor Required: yes
- Successor Path: `ai-dev/plans/`（后续可建 P2/P3 收尾 successor）

## Closure

Status Note: Plan 268 收口——3 个 Phase 全部 completed，4 个 confirmed defect（AR-85/86/87/93）均已修复并有回归测试。单点失败经 per-fire/per-task 隔离不再波及同批次；no-fitting-worker 经 revert+backoff defer（不卡 DISPATCHING）；dispatchMode 误配显式失败；CLAIMED→RUNNING CAS 失败不再重复执行。独立 closure audit PASS，无 blocker。
Completed: 2026-06-19

Closure Audit Evidence:

- Reviewer / Agent: closure-auditor-independent（独立子 agent，fresh session `ses_121a8da56ffeAUZJqWIZRAVJSJ`，未参与实现）
- Audit Session: ses_121a8da56ffeAUZJqWIZRAVJSJ
- Verdict: PASS（无 blocker）
- Evidence:
  - 每条 Phase Exit Criterion（3 Phase 共 19 项）：均 PASS，含 live code 位置与 test 方法名。例：AR-86 → `JobDispatcherScannerImpl.java:165-204`（per-fire try/catch）+ `JobWorkerScannerImpl.java:246-253`（per-task）+ `JobFireStoreImpl.java:289-305`（revert）+ `:55-62`（backoff 过滤）；AR-85 → `JobWorkerScannerImpl.java:287-293`（CAS 返回值检查 + skip invokeAsync）；AR-87 → `JobDispatcherScannerImpl.java:227-232`（显式抛错）；AR-93 → `JobWorkerScannerImpl.java:229-237`（WARN+onRejected）。
  - 每条 Closure Gate：均 PASS。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/268-...md --strict` 退出码 0。
  - Anti-Hollow 检查：per-fire/per-task catch 块均发指标（非 catch{}）；revert 接线经 fire→WAITING+future startTime 断言；CAS-fail 经 invokeCount==0 断言证明未执行；dispatchMode 误配经 assertThrows 错误码断言。`scan-hollow-implementations.mjs --module nop-job --severity high` 退出码 0（0 findings）。
  - Deferred 项分类检查：overfetch 深度饥饿升级为 optimization candidate，前提 AR-83/AR-88 已由 Plan 267 修复，non-blocking 成立。

Follow-up:

- overfetch 深度饥饿升级算法（successor：可选 P2/P3 优化，不影响正确性契约）
- no remaining plan-owned correctness work（AR-85/86/87/93 in-scope 全部收口）
