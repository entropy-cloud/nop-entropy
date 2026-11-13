# C6 资源生命周期与泄漏 — nop-retry + nop-tcc 第 2 轮深挖（盲审）

- 审计单元：`nop-retry`（8 jar）+ `nop-tcc`（9 jar），合计 82 files / 7,972 行手写代码
- 仓库：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- 审计日期：2026-10-15
- 本轮输入：第 1 轮 `C6-resource-lifecycle.md`（C6-001~013 + CL-001）、grep 基线 `_tmp/cra-baseline-nop-retry-nop-tcc.txt`、owner doc `docs-for-ai/03-modules/nop-retry.md`、`nop-tcc.md`
- 盲审纪律：除上述指定输入外未读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 下任何文件；未读取其他维度输出；仅以 live code 为准。
- 本轮任务面：①第 1 轮「配对健全面」抽查复核（scanner R1 / tcc registry T1 / moveToDeadLetter R5 / T6 循环 4 面）；②释放路径中间异常（suppressed 语义）；③C6-002 失效面扩展（attempt 表之外的中间态残留×UK 碰撞）；④ContextualizedRegistry/TccTransactionRegistry 注册-恢复对称性（生命周期视角）；⑤临时资源（内存聚合、future 句柄表）清理时机。

---

## 1. 本轮发现

### [nop-retry-nop-tcc-C6-014] 扫描线单点静默死亡：doScan 抛 Error/worker 拒绝后 BindScheduledExecutor 停止重臂，scanFuture 异常完成无人观测，且 running 标志未复位使 startScanning 重启自锁
- **发现ID**：nop-retry-nop-tcc-C6-014
- **严重度**：P3
- **缺陷族**：F8（schedule 句柄异常路径无观测/无配对收敛）+ F9（死亡仅一条通用日志，扫描停摆不可见）
- **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/scanner/RetryScannerImpl.java:90-101,105-111,114-155`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/BindScheduledExecutor.java:88-116`（本轮抽查平台侧机制，缺陷登记在模块侧使用方式）
- **证据片段**：
```java
// RetryScannerImpl.java:90-96 —— running 置 true 后仅在 stopScanning 复位
public synchronized void startScanning(Consumer<List<NopRetryRecord>> processor) {
    if (running) {
        return;                                  // ← 死亡后 running 仍为 true，重启调用静默 no-op
    }
    running = true;
    scanFuture = getExecutor().scheduleWithFixedDelay(...)
```
```java
// BindScheduledExecutor.java:99-107 —— command.run() 抛 Throwable：记一条通用日志后
// completeExceptionally 并 return，:107 的重臂 schedule 不再执行 → 扫描循环永久停止
    try {
        command.run();
    } catch (Throwable e) {
        LOG.error("nop.err.thread-pool.execute-fail",e);
        future.completeExceptionally(e);
        return;                                  // ← 跳过 schedule(command, delay, ...) 重臂
    }
    schedule(command, delay, delay, unit, future, timerHandle);
```
```java
// RetryScannerImpl.java:40,107-109 —— scanFuture 仅用于 cancel，无任何 exceptionally/whenComplete 观测
    private Future<?> scanFuture;
    ...
    scanFuture.cancel(false);
```
- **资源与泄漏链**：路径 = doScan 内部 catch 的是 `Exception`（:152-154），`Error`（OOM/StackOverflowError/NoClassDefFoundError）或 processor 内 Error 穿透 `command.run()` → BindScheduledExecutor 捕获 Throwable 后 `completeExceptionally` 且不再重臂 → 整个 retry 扫描线永久停止；`scanFuture` 异常完成但模块侧零观测（字段仅用于 cancel），仅有 `nop.err.thread-pool.execute-fail` 一条通用日志，`running` 保持 true → ①无告警可挂接（对比 C6-008 至少阻塞可见），②运维按直觉重新调用 `startScanning` 被 `if (running) return` 静默吞掉——恢复必须先 `stopScanning` 再 `startScanning` 或重启进程。次要触发：`executor.execute`（globalWorker 有界池，标准 JUC 缺省 Abort 语义）在池/队列饱和时抛 RejectedExecutionException，同样走 `completeExceptionally`→永久死亡——与 C6-008（join 占用 worker）复合时饱和概率上升。资源 = 扫描线 liveness（全部待重试记录停止推进）；频率 = Error/拒绝事件，低频但后果是整模块核心功能静默下线。
- **影响分析**：所有 PENDING/RETRYING 记录的到期重试、死信化、租约接管全部停止；调用方侧记录仍会被提交并进入账本，形成积压；无 metrics/告警挂钩点（scanFuture 的异常完成即天然告警源，被丢弃）。
- **与已声明边界的区分**：第 1 轮配对表 R1 判「通过（start/stop 配对）」——start/stop 的显式配对确凿，但「任务自身抛 Error 后循环终止且不可再启动」不在任何声明内；`nop.err.thread-pool.execute-fail` 是平台通用日志，非模块声明的停机语义。
- **误报校准核对**：触发前提（Error/池饱和拒绝）低频 → 按 campaign 口径降 P3 标注「理论风险偏工程现实」（OOM 场景在长驻进程中并不罕见，且死亡后完全不可自愈不可观测）；非 try-with-resources 类；非平台设施自身缺陷（模块未观测自己持有的 schedule 句柄是使用方式问题）。
- **修复方向**：startScanning 处挂 `scanFuture.whenComplete` 观测异常完成（LOG.error + 告警钩子 + 自动 stopScanning/startScanning 重臂有限次）；或 doScan 顶层 catch 改 `Throwable`（Error 记录后继续循环由下一轮兜底需谨慎，至少保证死亡可见 + running 复位使重启 API 有效）。
- **置信度**：高（BindScheduledExecutor 重臂逻辑逐行核实；running 守卫自锁确定）
- **交叉引用**：C6-008（join 占 worker → 饱和 → 拒绝触发本条）；C6-013（自旋延长单轮 doScan 占用）；第 1 轮配对表 R1 行需按本条修订。
- **复验建议**：单测：processor 注入抛 Error 的批次后，断言后续扫描停止、日志含异常、再次 startScanning 的行为明确（报错或恢复，而非静默 no-op）。
- **复核状态**：保留（P3 维持）；新发现；独立复核证实 BindScheduledExecutor:94-108 catch Throwable → LOG.error + completeExceptionally + return（跳过 ：107 重臂）、:109-111 executor.execute 拒绝同样链死、RetryScannerImpl running 仅 stopScanning 复位（:90-93 `if (running) return` 重启自锁属实）、scanFuture（:40）仅用于 cancel 零观测——机制逐行成立；**同族关联**：与 nop-job 审计 C4-003（AbstractBatchScanner 同一 BindScheduledExecutor 链死 + running 自锁形态，见 nop-job C10-005 引证）跨模块同族同平台机制，机制真实性获独立佐证；按规则与 WI 审计零代码交集仍标 新发现。

### [nop-retry-nop-tcc-C6-015] endAsync 补偿链在 CANCELLING 标记落库失败时短路：业务失败证据丢失（无 suppressed），TRYING 残留记录经超时恢复被无证据 CONFIRM——失败事务以确认方向释放参与者预留资源
- **发现ID**：nop-retry-nop-tcc-C6-015
- **严重度**：P2
- **缺陷族**：F8（释放路径中间异常：补偿前置写失败后整链放弃，记录以错误方向收敛）+ F9（原始业务异常被补偿链异常覆盖，无 addSuppressed、无日志保留）
- **位置**：`nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccEngine.java:236-256`（runTaskWithNewTxnAsync 的 catch 补偿与 thenCompose 丢异常点 :248-249）；`nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccTransaction.java:58-95`（endAsync 路由）、:97-117（doCancelAsync，CANCELLING 标记 :110-111）、:119-138（doConfirmAsync）；`nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:160-197`（updateTccStatusAsync CAS，异常即整链失败）；`nop-tcc/nop-tcc-api/src/main/java/io/nop/tcc/api/TccStatus.java:85-95`（isRollbackOnly 判定）
- **证据片段**：
```java
// TccEngine.java:245-249 —— task 失败后的补偿：endAsync 一旦失败（DB 异常），
// thenCompose(v2 -> failedFuture(e)) 不执行，调用方收到的是补偿链的 DB 异常，业务异常 e 丢失
    } catch (Throwable e) {
        // task同步抛异常/Error时必须显式执行endAsync补偿，否则record残留TRYING
        return txn.endAsync(false, null, e)
                .thenCompose(v2 -> CompletableFuture.<T>failedFuture(e));   // ← endAsync 失败则不可达
    }
```
```java
// TccTransaction.java:110-111 —— doCancelAsync 第一步是 CANCELLING 标记落库（唯一持久化
// 「业务失败」证据的动作）；该 updateTccStatusAsync 的 futureCall 体抛 DB 异常时，
// thenCompose(cancelAllAsync) 不执行、分支不补偿，全局记录保持 TRYING
    CompletionStage<Void> future = getRepository().updateTccStatusAsync(tccRecord, TccStatus.CANCELLING, null)
            .thenCompose(r -> TccRunner.cancelAllAsync(branchTxns, timeout, tccEngine.getServiceInvoker()));
```
```java
// TccStatus.java:93-95 —— 恢复路由依据：TRY_SUCCESS 分支不是 rollbackOnly
    public boolean isRollbackOnly() {
        return isInTransaction() && !isAllowConfirm();
```
恢复入口 `checkExpiredAsync`（TccEngine:345-348）以 `endAsync(true, null, null)` 续跑：TRYING 记录 + 全部分支 TRY_SUCCESS 时 `isRollbackOnly=false` 且 `isFailed(null,null)=false` → `doConfirmAsync` → CONFIRM_SUCCESS。
- **资源与泄漏链**：两条具体链——(A) 崩溃窗口：`task.apply` 抛业务异常后、CANCELLING 标记提交前进程崩溃（毫秒级窗口，每次失败事务均存在）→ 记录 TRYING + 分支 TRY_SUCCESS，失败证据随进程消亡 → expireTime（begin+60s）+expireGap 到期被扫描恢复 → 无回滚证据 → **失败事务被 CONFIRM**，参与者预留资源被转正。(B) 补偿前置写失败：业务失败 + CANCELLING 标记落库时 DB 异常（futureCall 体抛出）→ 分支补偿不执行、记录滞留 TRYING、调用方 future 以 DB 异常完成（业务异常 e 无 suppressed/无日志地丢失）→ 同上恢复路径 CONFIRM；调用方按失败语义重试业务 → 与被确认的首事务形成双重副作用。资源 = 参与者 TCC 预留资源以错误方向（confirm 而非 cancel）释放；频率 = (A) 崩溃命中窗口 / (B) 一次定向 DB 抖动。
- **影响分析**：终态方向翻转：业务失败的分布式事务被持久化为 CONFIRM_SUCCESS，事后从账本看不出任何失败痕迹（无 error 列写入走的是 confirm 路径）；与调用方可见的失败结果分叉，破坏 TCC 原子性。（B）链中框架**已持有**失败证据却未持久化，属缺陷而非「崩溃后证据不可得」的固有边界。
- **与已声明边界的区分**：owner doc 崩溃恢复语义仅声明「补偿中间态（CONFIRMING/CONFIRM_FAILED/CANCELLING/CANCEL_FAILED）对称续跑」；TRYING 状态恢复的「无证据缺省 confirm」方向未被声明（纯崩溃形态可辩护为设计选择，但 (B) 链的证据丢弃不在任何辩护范围内）。第 1 轮配对表 T3 行「恢复续跑（声明契约）通过」按本条修订。
- **误报校准核对**：链 (B) 两条件叠加（业务失败 × 定向 DB 异常）低频 → P2 而非 P1；泄漏链逐行可写（非理论风险）；非平台设施自身缺陷（futureCall 捕获 Throwable 的契约已被核实为正常，抛点在 DB 层）。
- **修复方向**：doCancelAsync/doConfirmAsync 将「失败/方向证据持久化」与「分支补偿」解耦：CANCELLING 标记写失败时有限重试直至成功（或改用不被 fromStatus 卡死的直写标记）再继续分支补偿；endAsync 整链失败时调用方 future 以**业务异常优先**完成（addSuppressed 补偿异常）并 LOG.error 保留证据；可考虑为 TRYING+全 TRY_SUCCESS 的恢复 confirm 增加 WARN 级审计日志与可配置回退方向。
- **置信度**：中高（代码路径确定；链 (A) 窗口极窄、链 (B) 依赖 DB 异常时机）
- **交叉引用**：T3 行修订；恢复语义与 C1（状态机）/C5（一致性）交叉——CONFIRM 方向翻转的副作用面建议合稿时交 C5 认领；与 C6-005 相关（恢复驱动缺省缺位，现网触发被 C6-005 放大为「永不恢复」而非「错误方向恢复」）。
- **复验建议**：单测：Mock repository 使 updateTccStatusAsync(CANCELLING) 抛 DB 异常 + task 抛业务异常，断言 (1) 调用方收到业务异常（或至少含 suppressed）、(2) 恢复扫描后的事务终态与业务结果一致（不出现 CONFIRM_SUCCESS）。
- **复核状态**：保留（P2 维持，附证据修正）；新发现；核心链独立复核成立：doCancelAsync TccTransaction:110-111 CANCELLING 标记写失败 → thenCompose 短路 → cancelAllAsync 分支补偿不执行 → 全局记录 DB 滞留 TRYING（CAS 写失败 ：188-193）；恢复入口 TccEngine:345-348 endAsync(true,null,null) → TRYING + 全 TRY_SUCCESS → isRollbackOnly=false（TccStatus:93-95）且 isFailed(null,null)=false → doConfirmAsync → CONFIRM_SUCCESS，失败事务以确认方向翻转属实，链 (A) 崩溃窗口与链 (B) 均逐行可写；**证据修正（F9 子项驳回）**：原文「调用方收到补偿链 DB 异常、业务异常 e 丢失、无 suppressed/无日志」不成立——endAsync 的 whenComplete（TccTransaction:86-94）在补偿链失败时先 LOG.error("nop.tcc.end-compensate-fail") 保留补偿异常，再 `throw NopException.adapt(ex)` 重抛业务异常，调用方实际收到业务异常（本条复验建议的断言 (1) 按现实现即通过）；方向翻转核心以替代证据成立故保留 P2，修复方向中「业务异常优先完成/addSuppressed」一项失效，「证据持久化解耦 + 恢复方向 WARN/可配置」两项有效。

### [nop-retry-nop-tcc-C6-016] attempt 终态写失败毒化业务结果：RPC 已成功被误判为执行失败，触发副作用重复、重试预算虚耗乃至成功操作被死信化（CL-001 同后果的独立触发机制）
- **发现ID**：nop-retry-nop-tcc-C6-016
- **严重度**：P2
- **缺陷族**：F8（账本写与业务结果判定耦合：记账失败改变业务语义）+ C5 形态（at-least-once 重复）
- **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:449-489`（doExecute，attempt 终态写 :487）、:429-447（executeSingleAttempt 的 ex 分支 :431-432）、:341-358（executeImmediateRetry 的 ex 分支）、:509-527（handleExecutionFailure，死信化 :516-517）；`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java:142-149`（saveAttempt，无 duplicate-key/DB 异常容错）
- **证据片段**：
```java
// RetryEngineImpl.java:463-488 —— attempt 终态写在 RPC 结果回调内，saveAttempt(:487) 抛出
// DB 异常时，整个 doExecute 返回 stage 以「执行失败」完成——尽管 RPC 本身已成功返回
    return rpcServiceInvoker.invokeAsync(...)
            .whenComplete((response, ex) -> {
                ...
                } else if (response != null && response.isOk()) {
                    attempt.setStatus(NopRetryConstants.RETRY_ATTEMPT_STATUS_SUCCESS);
                }
                ...
                recordStore.saveAttempt(attempt);      // ← 此处抛异常 = 成功被改写为失败
            });
```
```java
// RetryEngineImpl.java:429-432 —— 上游把 attempt 记账异常与业务失败同路由
    return doExecute(record, request, cancelToken)
            .whenComplete((response, ex) -> {
                if (ex != null) {
                    handleExecutionFailure(record, policy, ex, task);   // retryCount++ / 重试 / 死信
```
- **资源与泄漏链**：路径 = 任一次尝试的 RPC 成功返回后，:487 的 attempt 终态更新遇 DB 异常（抖动/锁超时）→ doExecute stage 失败 → executeSingleAttempt/executeImmediateRetry 按 `ex != null` 走 `handleExecutionFailure`：retryCount+1、记录置 PENDING + 退避间隔 → 扫描器/立即重试链**再次执行同一业务请求**（副作用重复）；若 retryCount 已达 maxRetryCount，则一个实际成功过的操作被 `moveToDeadLetter`（:516-517）死信化并可能触发 ON_FAILURE 回调。资源 = 1 次重复业务副作用 + 重试预算虚耗；频率 = attempt 终态写单点 DB 失败，与 C6-001 的触发概率同类（单次写失败即中）。
- **影响分析**：记账面（attempt 表）的故障被提升为业务语义输入，违反「账本记录结果、不改变结果」的分层；对比同文件 `handleExecutionSuccess`（:499-507）中 record 更新失败会挂起 future（C6-001 已立案），本条的 attempt 写失败不挂起、而是**静默改写**——两种记账失败产生两种相反的错误形态，均源于记账与结果判定的耦合。
- **与已声明边界的区分**：声明契约（超时双参、回调开关、COMPLETED 幂等重放）均不覆盖「记账写失败重解释成功结果」；CL-001 登记的是 COMPLETED 写失败（:360 记录更新）导致的重拾重复，本条的抛点在 attempt 快照表（:487）、即使记录 COMPLETED 更新本可成功也照样中毒，机制独立。
- **误报校准核对**：单次 DB 写失败即触发，非低频理论路径；后果与 CL-001（P1 线索）同域但机制独立，本条按机制登记 P2，后果严重度由 CL-001 在 C5 合稿时统一裁定，避免重复计分。
- **修复方向**：将 attempt 终态写包入 try/catch（失败仅 LOG.error，不改变 stage 结果——attempt 是观测数据，不应有能力否决业务结果）；或把 attempt 终态回写移到结果处置（handleExecutionSuccess/Failure）之后以独立错误路径执行。
- **置信度**：高（代码路径确定，抛点为真实同步 DB 调用）
- **交叉引用**：C6-001（同文件记账耦合的另一形态）、CL-001（同后果，建议合稿归并为「完成期记账失败」家族）；C6-002（attempt 表对账缺位的另一半）。
- **复验建议**：单测：Mock recordStore.saveAttempt 在终态回写时抛异常 + RPC 成功返回，断言记录不被置回 PENDING、业务不重复执行、错误仅入日志。
- **复核状态**：保留（P2 维持）；新发现；核实 doExecute :487 saveAttempt 在 invokeAsync 回调内，抛出使 doExecute stage 以异常完成 → executeSingleAttempt:431-432 / executeImmediateRetry:342 按 ex!=null 路由 handleExecutionFailure → retryCount+1、记录置 PENDING + 退避 → 重拾重复执行 / 预算耗尽死信化（:516-517）——记账失败改写业务结果属实；与 CL-001（record COMPLETED 写，:360/:501）、C6-002（崩溃残留）机制独立，与 C6-001 同根（回调内同步 DB 记账耦合）不同失败形态（挂起 vs 改写）；与 C6-002 无碰撞冲突（本路径 retryCount 已递增，attemptNo 推进）。

---

## 2. 排除项/定向核查

按第 2 轮深挖指令逐面核查，以下判定为干净或既有条目覆盖，不立案：

| # | 核查面 | 结论 |
|---|--------|------|
| E1 | **runTaskWithNewTxnAsync 缺 catch（对比 ExitingTxn 版的 catch Throwable）**：`registry.put` 与 `thenOnContext(txn.beginAsync())` 之间的同步异常窗口 | 排除。`beginAsync→saveTccRecordAsync` 经 `FutureHelper.futureCall`（FutureHelper:122-129）捕获 Throwable 返回 failed future，不会同步抛；`thenOnContext0`（ContextProvider:192-196）将 context.execute 异常转为 `promise.completeExceptionally`。窗口在缺省装配下不可达，不构成新发现 |
| E2 | **TccTransactionRegistry 注册-恢复对称性（生命周期视角，C1-004 交叉）** | 4 个 put 点的 restore 全部确证（catch Throwable / try-finally / whenComplete）；条目生命周期 = 请求 IContext 生命周期，future 永不完成时滞留有界（第 1 轮已记）。同组嵌套调用的 put/restore LIFO 交错翻转属并发竞态，归 C1 维度，本维度不重复计分 |
| E3 | **TccContext setCurrent/removeCurrent 配对** | `removeCurrent` 为 (key,value) 双参按实例移除（TccContext:75-81），不会误删后设上下文；网关三分支恢复 :102-111 与早退分支 :86-88 均无需清理或已清理。C6-012 的同步窗口结论维持 |
| E4 | **C6-002 失效面扩展：attempt 表之外的「中间态残留 × UK 碰撞」** | 无新面。`nop-retry.orm.xml` 仅两处 unique：UK_RETRY_IDEMPOTENT_ID（record，:264）与 UK_RETRY_ATTEMPT_RECORD_NO（attempt，:365，即 C6-002 本体）；record 插入为单行 REQUIRES_NEW 原子操作，冲突统一经 `resolveInsertRaceWinner`→`handleExistingRecord` 分流（RetryEngineImpl:178-187,222-241），无砖化路径；dead letter 的 idempotentId 非唯一（:461 仅普通索引）；`nop-tcc.orm.xml` 零 unique 约束（grep 核实），tcc 侧无 UK 碰撞形态 |
| E5 | **OVERWRITE deleteRecord 遗留孤儿 attempt** | 归 C6-004 的 F4 保留面（新 record 新 sid，attempt UK 为 (recordId,attemptNo)，无碰撞），不重复立案 |
| E6 | **moveToDeadLetter 原子性抽查** | `@Transactional`（REQUIRED）包住 saveEntityDirectly(deadLetter)+deleteEntityDirectly(record)，delete 失败整体回滚、记录保留，无半成功。维持第 1 轮「通过」 |
| E7 | **T6 checkExpiredTransactions 异常分支抽查** | TimeoutException→跳过（C6-006 已立案）、InterruptedException→恢复中断位+return、Exception→日志+continue，循环韧性与第 1 轮一致。维持「通过」 |
| E8 | **临时资源盘点**：RetryTaskImpl（无状态值对象）、PartitionResolver（配置持有）、TccGatewayInterceptor.newCtxRef（调用内数组）、ReflectionTccServiceMetaBuilder.methods（每次调用新建）、DefaultTccServiceMetaLoader.serviceMetas（@PostConstruct 装载、以服务数为上界） | 两模块主代码零内存聚合结构/零 future 句柄表（retry 立即重试句柄丢弃已由 C6-010 立案；tcc 补偿句柄已由 C6-006/007 立案）。无新增 |
| E9 | **getExecutor() 惰性初始化竞态**（RetryEngineImpl:621-626 / RetryScannerImpl:165-167） | 双线程可各建一个 BindScheduledExecutor 视图（无状态包装），last-write-wins，无泄漏无行为差异，良性 |
| E10 | **FutureHelper.whenCompleteAsync catch(Exception) 对 Error 的漏接**（TccTransaction.doCancelAsync 尾部状态回写经此） | 平台设施自身实现，按任务书校准排除；触发需 handler 抛 Error，不可达性同 E1 |
| E11 | **stopScanning 与在途 doScan 的收敛** | cancel(false) → BindScheduledExecutor future 异常完成（CancellationException）→ exceptionally 钩子级联取消 timer 句柄（:75-83）→ 在途批次由 running 标志在循环边界收敛；timerHandle 竞态窗口（set 前取消）有 `future.isDone()` 守卫兜底。维持第 1 轮「通过」 |
| E12 | **doExecute 记账失败的另一半形态**（:487 抛异常后 stage 失败是否挂 future） | 不挂起——异常作为执行失败向下游传播（区别于 C6-001 的 result 不完成）；其后果即本轮 C6-016，不另行立案 |

## 3. 终止声明

**C6 维度深挖终止。**

理由：本轮按指令完成全部 5 个深挖面——①4 个「配对健全面」抽查复核完成（scanner/registry/moveToDeadLetter/T6），产出 2 项修订（C6-014、C6-015）与 3 项维持通过（E6/E7/E11）；②释放路径中间异常系统性核查完成（E1/E10/E12 + C6-015/C6-016）；③C6-002 失效面扩展经 ORM 唯一约束全量比对确证 attempt 表外无新形态（E4/E5）；④registry 注册-恢复对称性独立复核完成（E2/E3）；⑤临时资源盘点确证两模块零未登记的内存态（E8/E9）。本轮新增 P2×2 + P3×1 后，C6 资源生命周期面的发现集已收敛：主代码资源获取点配对表全部行均已有结论（通过/立案/修订），未发现新的高价值问题。后续若 F4（账本保留）族需要专项（C6-004/C6-009 的 retention 设计），建议以独立 plan 而非继续深挖轮次承载。

## 4. 覆盖声明

**本轮深读文件**（相对第 1 轮新增/重读）：
- retry：`RetryEngineImpl`（全量重读，重点 :329-420/:449-489/:621-660）、`RetryScannerImpl`（全量重读）、`RetryRecordStoreImpl`（全量重读）、`RetryTaskImpl`（全量）、`nop-retry.orm.xml` 唯一约束段（:258-270,:357-365,:457-465）
- tcc：`TccEngine`（全量重读，重点 :184-289,:314-348）、`TccTransaction`（全量重读）、`TccBranchTransaction`（全量重读）、`TccRunner`（全量重读）、`TccRecordStore`（全量重读）、`TccTransactionRegistry`（全量）、`TccRpcServiceInterceptor`（全量重读）、`TccGatewayInterceptor`（全量重读）、`TccStatus`（全量）、`ITccTransaction`（全量）、`DefaultTccServiceMetaLoader`（全量）、`nop-tcc.orm.xml`（unique 约束 grep）
- 平台契约（仅按调用面契约评估，平台内部实现不作为发现对象）：`ContextualizedRegistry`（全量）、`BindScheduledExecutor`（全量——C6-014 机制证据）、`DefaultScheduledExecutor`（:107-141）、`GlobalExecutors`（:80-110）、`DefaultThreadPoolExecutor`（结构面）、`FutureHelper`（futureCall/syncGet/whenCompleteAsync/waitAll :113-176,:354-413）、`ContextProvider`（thenOnContext0/thenOnContext/completeAsyncOnContext :169-240）、`TccContext`（全量）
- beans 装配复核：`nop-retry` app-engine.beans.xml/_engine.beans.xml、`nop-tcc` tcc-core-defaults.beans.xml（维持第 1 轮结论）

**声明契约复核结果**：retry 侧 DISCARD/OVERWRITE+UK 兜底（含 resolveInsertRaceWinner 分流确证）、COMPLETED 幂等重放、回调开关按声明成立；tcc 侧执行表 auth 收敛、多跳传播优先级（thread-local 赢/请求头赢 + removeCurrent 按实例移除）确证成立；**崩溃恢复对称续跑的声明面收窄**：补偿中间态（CONFIRMING/CONFIRM_FAILED/CANCELLING/CANCEL_FAILED）续跑成立，TRYING 状态的恢复方向未声明且存在 C6-015 所述证据丢失链。

**残留边界（移交其他维度/后续）**：
1. `IRpcServiceInvoker` 内建超时语义仍在单元外（C6-001/007/008 前提不变）。
2. C6-015 的「TRYING+全 TRY_SUCCESS 恢复 confirm」纯崩溃形态是否可辩护为设计选择，建议 C5/C1 合稿时裁定；其副作用重复面归 C5。
3. ContextualizedRegistry 同组嵌套 put/restore 交错翻转归 C1（本维度仅确认生命周期有界）。
4. globalWorker 拒绝策略的具体配置值（队列容量/溢出策略）为平台配置面，C6-014 的拒绝触发分支按平台缺省 Abort 语义推断。
