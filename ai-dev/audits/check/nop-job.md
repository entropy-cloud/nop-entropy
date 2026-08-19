# nop-job 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-job
- 文件数: 实测 163 个非 `_` 前缀 `src/main/java` 文件（api 42 / core 27 / dao 24 / coordinator 40 / worker 13 / service 11 / local 4 / retry-adapter 1 / app 1；任务描述的"约 222"含 `_` 前缀生成文件）
- 覆盖范围声明: 全量深读 core 触发器/cron/calendar、AbstractBatchScanner、LocalJobScheduler 及 local 装配链、coordinator 全部 engine 类（planner/dispatcher/timeout/completion/reconciler/coordinator/cancel/taskBuilder 系列/RPC 系列）、dao 层三个 Store 与三个状态机及 TriggerSpecHelper、worker 的 scanner/contextBuilder/capacity、service 层 BizModel/Invoker/辅助类、retry-adapter 与 alarm handler。api 层 beans/crud 为数据类抽查。未覆盖: `_` 前缀生成代码、dao entity `_gen`、meta 模板、测试代码、nop-job-web（0 个 Java 文件）。为验证 nop-job 内发现，另行查证了框架侧 `SingleSessionMethodInterceptor`、`AopCodeGenerator` 生成物、`IOrmEntityDao.tryUpdateManyWithVersionCheck` 实现。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 3 |
| P2 | 4 |
| P3 | 9 |

## 发现列表

### [P1] RemoteJobInvoker 轮询任务永不取消，每次 rpcPoll 执行泄漏一个永久周期任务

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RemoteJobInvoker.java:116-180`
- **维度**: D2（资源泄漏）/ D6
- **证据**:
```java
private static final ScheduledExecutorService POLL_EXECUTOR = Executors.newSingleThreadScheduledExecutor(...);

private void schedulePolling(CompletableFuture<JobFireResult> future, ...) {
    ...
    POLL_EXECUTOR.scheduleWithFixedDelay(() -> {
        if (future.isDone()) {
            return;   // 仅跳过本轮，周期任务本身从未被 cancel
        }
        ...
    }, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
}
```
- **现状**: `scheduleWithFixedDelay` 的返回值（`ScheduledFuture`）被直接丢弃，没有任何代码路径 cancel 它。future 完成后（正常终态/超时/取消），周期任务退化为每 5s 一次的空转 no-op，但永远留在单线程 `POLL_EXECUTOR` 的队列里。
- **风险**: 每一次 `executorKind=rpcPoll` 的任务执行都永久累积一个调度项。长期运行的服务（每天数千次远程任务）会积累数万个空转周期任务：内存缓慢增长、单线程 executor 调度压力线性上升，活跃轮询被延迟，最终不可逆劣化直至重启。触发路径现实：rpcPoll 每次执行必泄漏。
- **建议**: 保存 `ScheduledFuture`，在 `future.whenComplete` 中 `cancel(false)`；或改为每次 `schedule` 单次延迟任务、在回调里自行续期。
- **误报排除**: 通读全文确认无任何地方引用/取消该周期任务；`future.isDone()` 只影响单轮行为，不影响任务生命周期。

### [P1] fetchRunningTasks cursor 分页未处理 null startTime：批末行为 CLAIMED（未启动）任务时 timeout 扫描周期性崩溃并停滞

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobTaskStoreImpl.java:103-122, 206-210`; `nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java:227-238`
- **维度**: D1 / D3
- **证据**:
```java
// JobTaskStoreImpl.fetchRunningTasks: CLAIMED ∈ RUNNING_LIKE_STATUSES，此时 startTime 尚为 null
query.addFilter(FilterBeans.in(PROP_NAME_taskStatus, JobTaskStateMachine.RUNNING_LIKE_STATUSES));
...
if (cursorTime != null) { ... } else if (cursorId != null) → validateCursor 抛异常

// JobTimeoutCheckerImpl.scanBatch → drainBatch(taskCursor, ..., NopJobTask::getStartTime, ...)
T last = batch.get(batch.size() - 1);
cursor.advance(timeFn.apply(last), idFn.apply(last));   // last.getStartTime()==null 时 cursor.time=null
```
```java
private static void validateCursor(Timestamp cursorTime, String cursorId) {
    if (cursorTime == null && cursorId != null) {
        throw new IllegalArgumentException("cursorId requires cursorTime");
    }
}
```
- **现状**: 任务被 worker 认领（WAITING→CLAIMED）后、进入 RUNNING 前，`startTime` 为 null，而 CLAIMED 属于 `RUNNING_LIKE_STATUSES` 会被 `fetchRunningTasks` 扫出。当某满批（size==batchSize=100）的最后一行 startTime 为 null 时，cursor 推进为 `(null, id)`，下一轮 `drainBatch` 再调 fetcher 即抛 `IllegalArgumentException`，`scanOnce` 捕获后整轮中止。排序依赖 DB 对 NULL 的处理：PostgreSQL/Oracle ASC 默认 NULLS LAST（尾部出现 null 即命中）；MySQL NULLS FIRST（整批皆为 null 起始的 CLAIMED 任务时命中）。
- **风险**: 命中后每个扫描周期都在同一位置失败：批 1 之后的 RUNNING 任务永远得不到超时检查，且异常发生在 `JobTimeoutCheckerImpl.scanBatch` 的子扫描 1，会连带子扫描 2（dispatch 超时）/3（stale WAITING 重置）整轮不执行。与 P2-4（CLAIMED 僵尸任务）叠加后可形成持续命中：僵尸 CLAIMED 任务（startTime 永久为 null）累积到批尾部即触发。
- **建议**: cursor 推进前对 null 排序键跳过或降级（null 视为最小值并单独过滤）；或查询排除 `startTime IS NULL` 的 CLAIMED 行由专门逻辑回收；至少在 `drainBatch.advance` 对 null 做防御。
- **误报排除**: 已核对 `JobTaskStateMachine.RUNNING_LIKE_STATUSES` 包含 CLAIMED（JobTaskStateMachine.java:49-52）、`tryLockTasksForExecute` 不设置 startTime（JobTaskStoreImpl.java:90-100）、`JobTimeoutCheckerImpl.tryMarkTimeout` 亦以 `startTime == null → return` 承认该状态存在。

### [P1] HandleMisfireTrigger 的 once 任务 misfire 跳过分支为死代码，迟到的一次性任务被立即补跑而非丢弃

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/trigger/HandleMisfireTrigger.java:29-34`; `nop-job/nop-job-core/src/main/java/io/nop/job/core/trigger/TriggerBuilder.java:32-47`
- **维度**: D1（miss fire 处理）/ D8
- **证据**:
```java
// HandleMisfireTrigger.nextScheduleTime
if (trigger instanceof OnceTrigger) {          // 永不成立
    long scheduleTime = ((OnceTrigger) trigger).getScheduleTime();
    if (scheduleTime > 0 && scheduleTime < afterTime - misfireThreshold) {
        return -1;                              // 期望：超阈值 misfire → 跳过
    }
}
```
```java
// TriggerBuilder.buildTrigger：OnceTrigger 总是被包装
trigger = new LimitCountTrigger(trigger);
trigger = new LimitTimeTrigger(trigger);
trigger = new CheckActiveTrigger(trigger);
...
trigger = new HandleMisfireTrigger(spec.getMisfireThreshold(), trigger);  // 传入的是包装链
```
- **现状**: `HandleMisfireTrigger` 仅在 `TriggerBuilder` 一处构造，传入的 `trigger` 此时至少是 `CheckActiveTrigger` 包装链，`instanceof OnceTrigger` 恒为 false。misfire 阈值对 once 触发器完全失效：新建的 `OnceTrigger`（first=true）直接返回过去时刻的 `minScheduleTime`，LocalJobScheduler 计算 delay=0 立即执行；分布式 planner 同样把过期 once schedule 判定为 due 并立即产生 fire。
- **风险**: 系统停机/重启后，minScheduleTime 已过且超过 misfireThreshold 的一次性任务（如"凌晨 3 点发报表"）会在恢复后被立即补跑，违反配置的 misfire 语义，产生业务上不该发生的迟到副作用。
- **建议**: 在包装链上解包识别 once 语义（如给 ITrigger 增加 `isOnce()`/`getOnceScheduleTime()` 委托），或将 misfire 判断移入 `TriggerBuilder` 构造时以未包装引用传入。
- **误报排除**: grep 全模块确认 `new HandleMisfireTrigger` 仅 TriggerBuilder.java:47 一处；`new OnceTrigger` 仅 TriggerBuilder 与 LocalJobScheduler（后者不接 misfire 包装）。

### [P2] LocalJobScheduler 忽略 JobFireResult.nextScheduleTime，违背 IJobInvoker 返回值契约

- **文件**: `nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:313-345`; 契约见 `nop-job/nop-job-api/src/main/java/io/nop/job/api/execution/JobFireResult.java:16-19`
- **维度**: D8
- **证据**:
```java
// JobFireResult 契约：返回 CONTINUE(nextScheduleTime) 且 nextScheduleTime>0 时，
// 以指定时间为准，忽略 trigger 计算结果。

// LocalJobScheduler.handleResult 成功路径：
scheduleNext(job);   // → job.trigger.nextScheduleTime(now, job.state)，从不读取 result.getNextScheduleTime()
```
- **现状**: 本地调度器成功后完全按 trigger 计算下次触发，invoker 显式指定的 `nextScheduleTime` 被静默丢弃。worker 链路（`DefaultJobExecutionContextBuilder.buildResultUpdate` → resultPayload → `JobCompletionProcessorImpl.resolveCompletionDecision`）则正确消费该值。
- **风险**: 同一 `IJobInvoker` 实现在 local 与分布式两条链路行为不一致：依赖"返回下次执行时间"动态调整节奏的任务（如退避、动态间隔）在 local 部署下退化为固定 trigger 节奏。
- **建议**: `handleResult` 成功续跑分支中，`result.getNextScheduleTime() > 0` 时以该时间作为下次触发时间。
- **误报排除**: grep 确认 LocalJobScheduler 中无任何 `getNextScheduleTime` 引用。

### [P2] LocalJobScheduler 触发器计算异常在 whenComplete 回调中被静默吞掉，job 永久卡在 RUNNING

- **文件**: `nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:250-276, 300-311`
- **维度**: D1 / D4
- **证据**:
```java
future.whenComplete((result, err) -> {
    synchronized (job) {
        job.running = null;
        handleResult(job, result, err);      // → scheduleNext → trigger.nextScheduleTime 可抛
        if (err == null) done.complete(null); // 抛出时永不执行
        else done.completeExceptionally(err);
    }
});
```
- **现状**: `scheduleNext` 内 `job.trigger.nextScheduleTime(...)` 可能抛出：`CronExpression` 的 runaway/overflow（CronExpression.java:202-229）、`PauseCalendarTrigger` 的 MAX_TRY_COUNT（PauseCalendarTrigger.java:42-43）、`CronCalendar/DailyCalendar` 的 MAX_ITERATION。异常从 whenComplete 回调逃逸后仅进入被丢弃的返回 future（无日志、无状态回写），`job.state.internal` 停留在 RUNNING、`scheduledFire` 为 null，job 从此不再调度也无法自愈。首轮 addJob 路径异常会抛给调用者，但 job 已注册进 `jobs` map 且无调度。
- **风险**: 配置了病态 cron（如 `0 0 0 30 2 ?`）或导致日历迭代超限的 pauseCalendar 的任务，一次执行成功后整个 job 静默死亡；`getJobState()` 永远返回 RUNNING，无任何错误痕迹。
- **建议**: `scheduleNext` 调用点包 try/catch，异常时置 FAILED 并记 error 日志（含 jobName 与表达式），避免异常逃出回调。
- **误报排除**: 已核对所有 `scheduleNext` 调用点均无捕获；whenComplete 抛出的异常由 CompletableFuture 机制吞入返回的 future，而该返回值被丢弃。

### [P2] dispatcher 对 ERR_JOB_NO_AVAILABLE_INSTANCE 不回退重试，短暂全量 worker 不可用时 fire 等待 300s 后被标 TIMEOUT 丢弃

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java:160-173`; `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/AbstractServiceTaskBuilder.java:79-98`
- **维度**: D1（任务漏执行）/ D8
- **证据**:
```java
} catch (NopException e) {
    if (isNoFittingWorker(e)) {                      // 仅 ERR_JOB_NO_FITTING_WORKER
        long backoffUntil = ...; 
        fireStore.revertDispatchingFireToWaiting(fire, backoffUntil);   // 回退重试
    } else {
        LOG.error("nop.job.dispatcher.fire-dispatch-failed:fireId={}", ...);  // 仅记日志
    }
}
```
- **现状**: broadcast/partition taskBuilder 在无健康实例时抛 `ERR_JOB_NO_AVAILABLE_INSTANCE`（滚动发布、目标服务整体重启的典型瞬态），落入 else 分支只记 error；fire 停留 DISPATCHING，`fetchWaitingFires` 不会再取到它，只能等 `dispatch-timeout-ms`（默认 300s）后 `tryMarkDispatchTimeout` 将 fire 置 TIMEOUT 并计失败。同为"暂时无 worker"的 `ERR_JOB_NO_FITTING_WORKER`（bestFit 路径）却有 30s backoff 回退 WAITING 的重试机制（AR-86）。
- **风险**: 目标服务重新部署超过 5 分钟窗口内到期的任务被直接判 TIMEOUT 丢弃（fire 不重试），造成漏执行；且与 NO_FITTING_WORKER 的恢复语义不一致。
- **建议**: 将 `ERR_JOB_NO_AVAILABLE_INSTANCE` 纳入 `isNoFittingWorker` 同等的 revert-to-waiting + backoff 处理（二者都是瞬态无 worker）。
- **误报排除**: 已核对 `resolveHealthyInstances` 抛错路径与 dispatcher 的 catch 分支；确认 DISPATCHING 状态 fire 仅有 timeout 回收一条出路。

### [P2] worker 认领后 loadFire/loadSchedule 失败仅 warn，任务滞留 CLAIMED 无恢复路径

- **文件**: `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:216-243`
- **维度**: D3 / D4
- **证据**:
```java
private void executeTask(NopJobTask task) {
    NopJobFire fire = fireStore.loadFire(task.getJobFireId());          // requireEntityById，可抛
    NopJobSchedule schedule = scheduleStore.loadSchedule(fire.getJobScheduleId()); // 可抛
    try {
        invoker = invokerResolver.resolveInvoker(schedule, fire);       // 仅此段有失败兜底
    } catch (NopException e) { ... completeTaskWithFailure(...); return; }
    ...
}
// scanBatch 外层：catch (Exception e) { LOG.warn("nop.job.worker.task-execute-failed:taskId={}", ...); }
```
- **现状**: 任务已 CAS 认领为 CLAIMED（乐观锁已占用）后，`loadFire`/`loadSchedule` 抛出（schedule/fire 被外部分级删除、DB 瞬断）时只在 scanBatch 外层 warn，任务停留 CLAIMED。恢复链均不覆盖该态：worker 存活时 liveness 链不标记 SUSPICIOUS；`tryMarkTimeout` 对非 RUNNING 直接 return；`resetStaleWaitingTasks` 只处理 WAITING。仅当执行超时（timeoutSeconds/executionTimeoutMs）配置了才可能被回收，而 executionTimeoutMs 默认 -1。
- **风险**: 任务永久滞留 CLAIMED → 所属 fire 永远 RUNNING 无法 finalize、activeFireCount 永不回落，且与 P1-2 叠加（null startTime 行进入超时扫描 cursor 头部）放大扫描停滞风险。
- **建议**: executeTask 头两行加载失败时走 `completeTaskWithFailure`（区分实体缺失 vs 瞬态错误，瞬态可 revert CLAIMED→WAITING 释放给其他 worker）。
- **误报排除**: 已核对 JobTaskStateMachine 各谓词与 timeout checker 全部分支，确认 CLAIMED+worker 存活+无执行超时配置时无任何回收路径。

### [P3] failFireWithoutSchedule 缺状态前置校验；schedule-deleted 回收路径 fire 更新失败仍取消全部任务，与 "Bug C fix" 防护不对称

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java:274-289`; `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:282-305`
- **维度**: D3 / D4
- **证据**:
```java
// failFireWithoutSchedule：requireEntityById 后直接置 FAILED，无 isDispatching 前置校验
fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_FAILED);
...
if (!fireDao().tryUpdateWithVersionCheck(fire)) {
    LOG.warn("nop.job.fire-finalize-conflict:fireId={},errorCode={}", ...);   // 冲突仅 warn
}
// tryMarkDispatchTimeout(schedule-deleted 分支)：无论 fire 是否更新成功，随后一律 cancel tasks
```
- **现状**: 正常超时路径有 "Bug C fix"（fire 更新失败则跳过任务取消，避免 RUNNING fire + CANCELED tasks），schedule-deleted 分支没有等价防护：`failFireWithoutSchedule` 版本冲突失败仅 warn，代码仍继续把全部未完结任务置 CANCELED。若 fire 恰好被 dispatcher 并发推进到 RUNNING，结果就是 RUNNING fire + 全 CANCELED tasks 的不一致（fire 后续由 completion processor 兜底聚合，但状态漂移可观测）。
- **风险**: 低概率状态漂移；`failFireWithoutSchedule` 若被新增调用方复用，缺前置校验的问题会被放大。
- **建议**: `failFireWithoutSchedule` 增加 `isDispatching/isActive` 前置校验；deleted 分支根据 fire 更新结果决定是否取消任务。
- **误报排除**: 已对照同文件 `insertTasksAndMarkFireDispatching`/`revertDispatchingFireToWaiting` 均有 isDispatching 前置校验，确认不对称为客观差异。

### [P3] tryLockTasksForExecute 的 lockTimeoutMs 参数被忽略，"租约"语义未实现

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobTaskStoreImpl.java:88-100`
- **维度**: D8
- **证据**:
```java
public List<NopJobTask> tryLockTasksForExecute(List<NopJobTask> tasks, String workerInstanceId, long lockTimeoutMs) {
    for (NopJobTask task : tasks) {
        task.setTaskStatus(TASK_STATUS_CLAIMED);
        task.setWorkerInstanceId(workerInstanceId);
    }
    return taskDao().tryUpdateManyWithVersionCheck(tasks);   // lockTimeoutMs 全程未使用
}
```
- **现状**: 接口签名承诺租约超时，实现不做任何基于时间的过期处理。CLAIMED 态的回收完全依赖 worker 存活链（SUSPICIOUS→TIMEOUT）或执行超时（默认关闭），在未配置 namingService 的部署里 `resolveAliveWorkerIds` 恒为 null，worker 崩溃后 CLAIMED 任务只能靠执行超时回收。
- **风险**: 契约漂移 + 依赖部署形态的回收盲区（无服务发现 + 未配执行超时 → 崩溃 worker 的 CLAIMED 任务永久滞留）。
- **建议**: 要么实现租约（CLAIMED 时间戳 + 过期重置），要么从接口移除参数并在 javadoc 说明真实回收机制。
- **误报排除**: 通读 JobTaskStoreImpl 确认参数无任何使用；已核对 beans.xml 装配（namingService 为可选注入，JobTimeoutCheckerImpl.java:73-75）。

### [P3] CronExpression 私有构造器产出 timeZone=null 且 threadLocalCal 未初始化的实例，潜伏 NPE

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/utils/CronExpression.java:107-111, 147, 452-472`
- **维度**: D1
- **证据**:
```java
private CronExpression(String expression, String[] fields) {
    this.expression = expression;
    this.timeZone = null;          // 公开构造器保证非空，此处破坏不变式
    doParse(fields);               // threadLocalCal 未初始化
}
// getTimeAfter: Calendar calendar = threadLocalCal.get();  → 该实例上调用即 NPE
// equals/hashCode: this.timeZone.equals(...) / this.timeZone.hashCode() → NPE
```
- **现状**: 该构造器仅被 `isValidExpression` 内部使用，实例随即丢弃，当前不可触达；但它是同包可见的私有不变式破坏——`isValidExpression` 返回的校验通过实例若未来被复用（返回实例、缓存等）即触发 NPE。
- **风险**: 潜伏缺陷，当前无现实触发路径。
- **建议**: 私有构造器同样初始化 threadLocalCal 与默认 timeZone（或直接复用公开构造器路径）。
- **误报排除**: grep 确认该构造器仅 `isValidExpression` 一处调用，如实降级为 P3。

### [P3] LocalJobScheduler.addJob 不校验 invoker 解析结果为 null，错误延迟到执行期且无上下文

- **文件**: `nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:72`; `nop-job/nop-job-local/src/main/java/io/nop/job/local/config/BeanContainerInvokerResolver.java:12-15`
- **维度**: D1 / D4
- **证据**:
```java
IJobInvoker invoker = invokerResolver.apply(spec.getJobInvoker());   // 无 null 检查
// BeanContainerInvokerResolver.apply: return (IJobInvoker) BeanContainer.tryGetBean(beanName);  // 可返回 null
```
- **现状**: `jobInvoker` 配置错误（bean 未注册，如 `nopJobInvoker_xxx` 拼写错）时 addJob 正常返回；到触发时刻 `job.invoker.invokeAsync(ctx)` 抛 NPE，被 executeJob 的 catch(Exception) 吞为 "execute-failed" 日志，job 置 FAILED。错误信息不含 jobInvoker 名称，且暴露时间从配置期推迟到首个触发期。
- **风险**: 配置错误发现滞后、定位困难（NPE 无参数）。
- **建议**: addJob 时对 null invoker 抛 `ERR_JOB_BEAN_NOT_FOUND` 风格的 NopException 并带 invoker 名。
- **误报排除**: 已核对 addJob 全流程无 null 检查；executeJob catch(Exception) 会把 NPE 归入 FAILED 分支。

### [P3] 用户可配 headers 可覆盖框架路由头（nop-svc-target-host）

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/HttpRpcPollTaskClient.java:69-72, 186-193`; `nop-job/nop-job-service/src/main/java/io/nop/job/service/executor/RpcJobInvoker.java:48-53`
- **维度**: D5
- **证据**:
```java
injectTargetHost(request, task);      // 先注入框架路由头
injectUserHeaders(request, jobParams); // 后注入用户 headers（同名键覆盖）
// injectUserHeaders: request.setHeader(String.valueOf(entry.getKey()), entry.getValue()); // 无保留头过滤
```
- **现状**: jobParams.headers 来自 schedule/fire 配置（含 triggerNow 的 overrideParams），以任意键值覆盖请求头，可改写 `nop-svc-target-host` 等框架头的精确路由，也可能覆盖认证/租户类头。RpcJobInvoker 的 `addHeaders` 同样在框架头之后执行。
- **风险**: 有 triggerNow/任务配置权限的调用者可重定向任务 RPC 到任意注册实例；属于权限模型内的能力，但缺少对保留头前缀（nop-）的保护，属于纵深防御缺口。
- **建议**: injectUserHeaders 过滤 `nop-` 前缀保留头，框架头最后注入或显式覆盖回填。
- **误报排除**: 已核对两个 invoker 的头注入顺序，确认用户头后写。

### [P3] JobScheduleCounterReconciler 只对账 activeFireCount>0 的 schedule，recorded=0 而 actual>0 的漂移永不收敛

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobScheduleCounterReconciler.java:74-82`
- **维度**: D1 / D6
- **证据**:
```java
query.addFilter(FilterBeans.gt(NopJobSchedule.PROP_NAME_activeFireCount, 0));  // 只取 >0
```
- **现状**: 对账器是计数漂移的最终收敛机制，但查询过滤掉 recorded=0 的行。若出现多减（recorded 被减到 0 而实际仍有活跃 fire，如未来新增的双减路径或手工数据修正），该 schedule 永不进入对账候选。
- **风险**: 低概率下 activeFireCount 持续偏低，使 DISCARD/OVERLAY 阻塞策略误判"无并发 fire"，放过本应跳过/取消的重叠执行。
- **建议**: 候选条件改为 `activeFireCount>0 OR EXISTS active fire`（或定期全量对账一批 recorded=0 的 schedule）。
- **误报排除**: 已核对当前所有 decrement 路径均以版本检查成对出现，recorded<actual 当前难以触达，属防御性缺口，如实定级 P3。

### [P3] 触发器每次计算都重建并重解析 cron；dispatcher 每 fire 单独 loadSchedule

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/trigger/JobTriggerCalculator.java:20-27`; `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java:198-210`; `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java:146`
- **维度**: D6
- **证据**:
```java
// JobTriggerCalculator.calculateNextFireTime —— 每次调用:
ITrigger trigger = TriggerBuilder.buildTrigger(spec, defaultCalendar);  // new CronExpression + parse
return trigger.nextScheduleTime(now, evalContext);

// JobDispatcherScannerImpl.scanBatch —— 每个 locked fire:
NopJobSchedule schedule = scheduleStore.loadSchedule(fire.getJobScheduleId());  // 逐条 DB 加载
```
- **现状**: planner 每 5s 周期对每个 due schedule 完整重建触发器链并重新解析 cron（completion/fixed-delay 计算同路径）；dispatcher 对批内 fire 逐条 loadSchedule 而非按 scheduleId 去重批量加载（同 schedule 的多 fire 重复加载）。
- **风险**: schedule 数量大时（数千）每周期产生可观的解析与 DB 开销；每 cron 表达式还各持有 ThreadLocal GregorianCalendar。当前为固定浪费而非错误。
- **建议**: 按 scheduleId+trigger 配置哈希缓存已解析 CronExpression/Trigger 链；dispatcher 用 batchLoadSchedules 去重。
- **误报排除**: 已核对 calculateNextFireTime 无任何缓存层，scanBatch 循环内无批加载。

### [P3] 模块内校验异常策略混用：IllegalArgumentException/IllegalStateException 与 NopException 并存

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java:139-167`; `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/AdaptiveJobTaskBuilder.java:84-88`; `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RemoteJobInvoker.java:211-215`
- **维度**: D7（平台规范）
- **证据**:
```java
throw new IllegalArgumentException("scanIntervalMs must be >= 1000, got " + scanIntervalMs);
throw new IllegalStateException("AdaptiveJobTaskBuilder received null assignment at index " + i);
throw new IllegalStateException("jobTaskId attribute missing in execution context");
```
- **现状**: 同模块同类校验 elsewhere 一律 `NopException + ErrorCode + .param`（如 `JobTaskStoreImpl.fetchWaitingTasks` 的 ERR_JOB_WORKER_INSTANCE_ID_REQUIRED、各 applyXxx 校验在别处抛 NopException），上述位置残留 JDK 异常。均为英文消息、无 bare RuntimeException，属轻微偏离两档策略的统一性。
- **风险**: 配置错误时 IoC 启动失败信息无 error code，可观测性弱于模块惯例。
- **建议**: 统一替换为 JobCoreErrors 下的 NopException。
- **误报排除**: grep 确认模块内无 `new RuntimeException`/`printStackTrace`/空 catch（纪律整体良好），此处仅列已验证的残留点。

### [P3] 死代码：JobPlannerScannerImpl.toTime/defaultLong 与 JobFireStoreImpl.toTime 未被使用

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java:254-260`; `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java:347-349`
- **维度**: 维护性
- **证据**:
```java
private long toTime(Timestamp value) { return value == null ? 0L : value.getTime(); }
private long defaultLong(Long value) { return value == null ? 0L : value; }  // JobPlannerScannerImpl 内无调用
```
- **现状**: 两个文件中的 `toTime` 及 JobPlannerScannerImpl 的 `defaultLong` 为重构残留（同名 helper 在 JobTimeoutCheckerImpl/JobFireStoreImpl 中仍在使用），仅产生编译告警噪音。
- **风险**: 无行为影响；误导读者以为存在时间换算路径。
- **建议**: 删除未用私有方法。
- **误报排除**: grep 逐文件核对调用点后确认。

## 已排查未列入的疑点（负结果）

- `LocalJobScheduler` 失败即停（FAILED 不再调度）：与 `JobFireResult` 契约（ERROR/异常 → 终止调度）一致，非缺陷。
- `JobCompletionProcessorImpl.completeSingleFire` 自调用 + @Transactional/@SingleSession：Nop AOP 为生成子类（`Xxx__aop` 覆写方法调 super），自调用经虚分派仍被拦截，注解有效（已对照 `SingleSessionMethodInterceptor` 与 `MyModel__aop` 生成物验证）。
- planner 用 nextFireTime 覆写为 now+lockTimeout 作乐观锁、崩溃后 60s 自愈、`hasWaitingFire` 幂等去重：机制自洽，未见重复/漏触发路径。
- worker 认领 CAS（WAITING→CLAIMED→RUNNING 双版本检查，AR-85）、overlay 取消链、`resolveAliveWorkerIds` 空列表保守返回 null、revert backoff 复用 startTime：均已核对，无问题。
- 空 catch / bare RuntimeException / printStackTrace：全模块 grep 零命中。
- `@Inject` 全部为 setter 注入（无 private 字段注入）、`@InjectValue` 使用规范、bean 均在 `_vfs` beans.xml 显式注册：D7 无违例。
