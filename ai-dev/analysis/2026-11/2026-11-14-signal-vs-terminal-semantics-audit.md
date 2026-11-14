# 「信号型完结 ≠ 生命周期终态」语义错位族审计（周期任务绝缘层）

> Status: resolved
> Date: 2026-11-14
> Scope: 周期任务绝缘层（PeriodicTaskHelper / InsulatedPeriodicChain / InsulatedPeriodicTask / DefaultScheduledExecutor / BindScheduledExecutor）及其全部消费方（排除 nop-ai/nop-stream/nop-code/nop-tools/nop-dev-tools/nop-frontend-support/nop-autotest 与测试代码）
> Conclusion: 确认缺陷 3 处（C-1/C-2 同根于 DefaultScheduledExecutor 登记摘除时机，C-3 为 nop-job AbstractBatchScanner 消费方放大——6 个 job 扫描器单轮失败即业务静默停摆）+ 1 处 SUSPECT（AutoRegistration 观察型）。修复方向统一：清理/存活判定只认 `InsulatedPeriodicChain.isStopped()`（或 listener.onChainRejected），禁止把 future 异常完结当生命周期终态。

## Context

- 起点缺陷（用户发现）：`DefaultScheduledExecutor.trackPeriodicTask`（:173-178）用 `chain.whenComplete((r,e) -> periodicTasks.remove(guard))` 摘除登记，但 `InsulatedPeriodicChain.completeExceptionally` 被 `InsulatedPeriodicTask.onFailure` 用作**失败信号**（javadoc 明示：结算不携带取消语义，链继续重臂）。首次失败 → future 异常完结 → guard 被提前摘除 → `destroy()` 的统一拒绝路径不再覆盖这条活链。
- 审计问题：仓库内还有哪些「把 future 完结当成生命周期结束来触发清理/注销/存活判定」的同族实例。

## 缺陷原型与判定模式

`InsulatedPeriodicChain` 的 javadoc 契约：**返回 future 是失败/取消/拒绝信号，不是链存活指标**。因此任何把「future 完结」映射为「资源回收 / 状态翻转 / 判活」的接线都是潜在错位。四种模式：

1. whenComplete 挂在信号型 future 上做注销/摘除
2. 用 isDone/isCompletedExceptionally 判活信号型链
3. 「任意完结 → 自动取消」接线（C3-016/FutureHelper.bindCancel 家族）
4. 登记表移除时机挂在错误终态上

## 审计结论

### CONFIRMED

| # | 位置 | 模式 | 问题 | 后果 |
|---|---|---|---|---|
| C-1 | `DefaultScheduledExecutor.trackPeriodicTask`（nop-commons :173-178） | 1+4 | 原型缺陷本体：失败信号触发登记摘除 | fixedDelay 链 destroy 后仅在销毁瞬间恰有 pending 轮次时被 pendingFutures 部分兜底；轮次间隙/在飞中的链无覆盖，future 可能悬挂 |
| C-2 | `DefaultScheduledExecutor.scheduleAtFixedRate` 六参（nop-commons :255-274） | 1+4 | 与 C-1 同根；放大项：fixedRate native handle 是 JDK ScheduledFuture（非 CompletionStage），`watchNativeHandle` 的 instanceof 检查直接 return，destroy 后无任何通道补链终止 | 失败过的 fixedRate 链在 timer destroy 后无补终态通道：链 `isStopped` 不置位、native handle 不被 cancel、`onChainRejected` 不达（future 终态原因已被首次失败信号占用）；统一拒绝路径覆盖洞 |
| C-3 | `AbstractBatchScanner.startScanning`（nop-job-core :106-117） | 1+2 | whenComplete 判定 `error != null && !(error instanceof CancellationException)` 即翻 `running=false`——但该条件无法区分「任务体失败信号」（completeExceptionally）与「设施级拒绝」（rejectChain），两者都是非 Cancellation 异常终态 | **触发面修正（实施期实证）**：`scanOnce` 内部有 2496 绝缘边界（`catch Throwable → onScanCycleFailed`），`scanBatches` 还有内层 catch（`→ onScanFailed`）——常规 scanBatch 失败**不会**到达绝缘层守卫。真实触发面：`onCycleStart`/cursor reset 等外层 catch 链路径的异常 + `onScanCycleFailed` 钩子自身抛出（catch 块内调用，会逃逸 scanOnce）。此时（修复前）链失败信号完结 → `running=false` → 6 个 job 扫描器业务静默停摆 + 僵尸链持续重臂；`scanChainRejectedCount` 把任务体失败误计为链拒绝。缺陷为 CONFIRMED（结构性误判 + 存在真实触发面），但严重度低于审计初判——常规扫描失败路径已被 2496 边界隔离 |

### SUSPECT

| # | 位置 | 说明 |
|---|---|---|
| S-1 | `AutoRegistration.attachAutoUpdateWatch`（nop-cluster :229-240） | 非 Cancellation 异常终态即 ERROR「refresh-registration-chain-broken terminal」。当前 `refreshRegistration` 任务体自行 catch 全部 Throwable，故现在只有真拒绝会触发——行为正确，但正确性依赖「任务体永不抛」的隐式前提，判读错误已埋在注释/日志语义中。登记 watch-only；若守卫被移除则升级为缺陷 |

### 排查后排除（OK，27 站点）

`CliRunCommand.startWatchLoop`（仅日志）、`watchNativeHandle` 本体（语义正确）、链内部轮次守卫（全用 isStopped）、`FutureHelper.bindCancel` 全部接线点（一次性 future↔promise，无绝缘链误接）、`ExecutorHelper.trackPending`（一次性 future 完结==终态）、`RpcPollTaskManager`、`PollingRpcClient`、LedgerCleaner 家族 8 处（listener 仅计数不改存活）、其余周期消费者 14 处（cancel-only 或一次性 future 摘除）——明细见审计子代理报告（本日 log 引用）。

## 修复方案（详见 plan 2570）

- **Fix A（C-1）**：`trackPeriodicTask` 摘除守卫加 `chain.isStopped()` 前置——只摘真终止的链；失败信号完结的链保持登记，destroy() 持续覆盖。副作用评估：登记表条目数 ≤ 每 executor 周期任务数，失败链驻留至 destroy，无泄漏面。
- **Fix B（C-2）**：Fix A 落地后 destroy() 的 guard 遍历天然覆盖失败过的 fixedRate 链（rejectChain 执行 isStopped 置位 + native handle cancel + onChainRejected 回调；future 终态原因已被失败信号占用，reject 的异常终态被幂等忽略），无需额外代码。
- **Fix C（C-3）**：AbstractBatchScanner 摘除错误的 whenComplete 判定，改为通过 `IPeriodicTaskListener.onChainRejected` 回调通道翻转 running/计数/日志——设施级拒绝的唯一权威信号源。

## Conclusion

- 修复批次（44719346a9）的绝缘层核心不变式（isStopped 判活、拒绝不复活、终态可见）设计正确；缺陷集中在**边界的登记/观察接线**上——正是「future 完结语义」最容易被误读的位置。
- 共性教训：**凡挂 whenComplete/isDone 的 future，必须先确认其完结语义是「终态」还是「信号」**。信号型 future 的清理只能挂权威生命周期通道（isStopped / listener 回调），或以「终结时全量清理」（destroy clear）兜底。
- 后续工作：`ai-dev/plans/2570-*.md`（Fix A/B/C + 回归钉 + 契约文档同步）。

## Open Questions

- [ ] S-1 AutoRegistration 的「任务体永不抛」隐式前提是否值得改为显式断言（watch-only，单独评估）
- [ ] nop-ai/nop-stream 等被排除模块内同族消费方未审计（本轮 scope 外）

## References

- `ai-dev/design/concurrency/periodic-task-insulation.md`（绝缘层设计契约）
- `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/PeriodicTaskHelper.java`
- `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/InsulatedPeriodicChain.java`
- `nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java`
