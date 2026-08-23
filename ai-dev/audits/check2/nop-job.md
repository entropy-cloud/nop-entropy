# nop-job 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-job
- 文件数: 169（src/main/java，含 6 个生成文件：`entity/_gen/` 4 个 + `_NopJobDaoConstants.java` + `_NopJobCoreConstants.java`，生成文件按纪律跳过）
- 覆盖范围声明: 深读约 75 个承载逻辑的文件（关键类：`CronExpression`、`TriggerBuilder`、`OnceTrigger`、`HandleMisfireTrigger`、`PeriodicTrigger`、`PauseCalendarTrigger`、全部 `calendar/*`、`AbstractBatchScanner`、`JobPlannerScannerImpl`、`JobDispatcherScannerImpl`、`JobTimeoutCheckerImpl`、`JobCompletionProcessorImpl`、`JobScheduleCounterReconciler`、`RemoteJobInvoker`、`HttpRpcPollTaskClient`、全部 task builder、`JobScheduleStoreImpl`、`JobFireStoreImpl`、`JobTaskStoreImpl`、3 个状态机、`TriggerSpecHelper`、`JobWorkerScannerImpl`、`DefaultJobExecutionContextBuilder`、`MetadataWorkerCapacityProvider`、`LocalJobScheduler`、`BeanMethodJobInvoker`、`RpcJobInvoker`、`NopRetryJobRetryBridge`、4 个 BizModel、全部 beans.xml / sql-lib / orm.xml 交叉验证）；其余约 88 个为 api 模块 DataBean/接口/常量/Empty/NoOp 实现，做反模式模式扫描（private `@Inject`、Spring 依赖、`SimpleDateFormat`、bare `RuntimeException`、`printStackTrace`、重复错误码、可变内部集合暴露）覆盖 100%。未深读区域：api 模块 spec/config/crud/beans DataBean 的字段级逐行审查、生成实体类内部实现。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 2 |
| P2 | 2 |
| P3 | 13 |

## 发现列表

### [P0] Once 语义在 planner 路径失效：once 型 schedule 首次触发后被无限重复执行

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/trigger/OnceTrigger.java:{19-38}`；`nop-job/nop-job-core/src/main/java/io/nop/job/core/trigger/TriggerBuilder.java:{27-29}`；`nop-job/nop-job-core/src/main/java/io/nop/job/core/trigger/JobTriggerCalculator.java:{25-26}`；`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java:{152-196}`
- **维度**: D1
- **证据**:
```java
// OnceTrigger.java —— "只执行一次"依赖实例内的可变 first 标志
public class OnceTrigger implements ITrigger {
    private boolean first = true;
    private final long scheduleTime;
    @Override
    public long nextScheduleTime(long afterTime, ITriggerEvalContext evalContext) {
        if (!first) { return -1; }
        first = false;
        if (scheduleTime > 0) return scheduleTime;   // 无视 afterTime，永远返回同一个时刻
        return afterTime + 1;
    }
}
// JobTriggerCalculator.java —— planner 每次计算都重建整条 trigger 链
ITrigger trigger = TriggerBuilder.buildTrigger(spec, defaultCalendar);
return trigger.nextScheduleTime(now, evalContext);
// TriggerBuilder.java:27-29 —— 每次调用 new 一个全新的 OnceTrigger（first 恒为 true）
} else if (spec.getRepeatInterval() <= 0) {
    trigger = new OnceTrigger(spec.getMinScheduleTime());
```
- **现状**: `OnceTrigger` 的 once 语义由实例字段 `first` 承载，只在 trigger 实例被长期复用时有效（`LocalJobScheduler` 保留实例，工作正常）。而分布式 planner 路径上 `JobTriggerCalculator.calculateNextFireTime` 每次调用都经 `TriggerBuilder.buildTrigger` 重建全新 `OnceTrigger`，`first` 每次都是 `true`，于是每次计算都返回同一个（首次触发后已是过去的）`minScheduleTime`。`planSchedule` 把这个过去时刻写回 `schedule.nextFireTime`（`insertFireAndAdvanceSchedule`），`fetchDueSchedules` 的 `nextFireTime <= now` 条件下个周期（5s）再次命中；唯一的去重 `hasWaitingFire` 只匹配 **WAITING** 状态的 fire，前一个 fire 一旦被 dispatcher 推进到 DISPATCHING/RUNNING，去重即失效，插入第二个 scheduledFireTime 相同的新 fire。
- **风险**: triggerType=ONCE（或无 cron 且 repeatInterval<=0）的 schedule 首次执行后，以约每 10s 一条（planner 5s + dispatcher 5s）的节奏无限重复创建并执行 fire，业务侧重复副作用（重复发消息/重复扣减等数据错误）。ORM 中 `MISFIRE_THRESHOLD_MS`、`MAX_EXECUTION_COUNT` 均无默认值（`_app.orm.xml:126-133`）：默认配置下 `HandleMisfireTrigger` 不装配（`TriggerBuilder` 仅在 `misfireThreshold > 0` 时包装）、`LimitCountTrigger` 不生效，无任何机制终止循环；即便配置了 misfireThreshold=T，在 T 窗口内仍会重复执行约 T/10s 次（`TestOnceMisfireAndLocalContract.java:83` 证实"迟到但未超阈值返回过去时刻、立即执行"是有意契约，但 planner 循环无界未被覆盖）。`scheduleStatus` 也不会自动置 COMPLETED——`JobCompletionProcessorImpl.completeSingleFire` 仅在 `allowResultCompletion` + task 结果显式 `completed=true` 时才置 COMPLETED（默认 false）。
- **建议**: planner 侧对 once 语义做持久化判定而非依赖 trigger 实例状态：如 `OnceTrigger` 改为依据 `evalContext.getLastScheduledTime() >= onceTime` 返回 -1；或在 `planSchedule` 中对 `nextFireTime <= 已有 lastFireTime` 的 once 型 schedule 直接置 COMPLETED/nextFireTime=null；同时给 `TRIGGER_TYPE_ONCE` 的 schedule 强制默认 `maxExecutionCount=1` 或 `misfireThreshold` 默认值。
- **误报排除**: 通读了 `TriggerBuilder`/`OnceTrigger`/`HandleMisfireTrigger`/`LimitCountTrigger`/`CheckActiveTrigger` 全链、`JobPlannerScannerImpl.planSchedule` 全部分支（无 once 特判）、`JobScheduleStoreImpl.insertFireAndAdvanceSchedule` 的 WAITING-only 去重、`JobCompletionProcessorImpl` 的 COMPLETED 条件、`_app.orm.xml` 列默认值、`TestOnceMisfireAndLocalContract`（仅覆盖 trigger 级单次计算，未覆盖 planner 跨周期循环）；并对照验证了 fixed-rate（PeriodicTrigger 以 lastFireTime 推进网格）与 cron（CronExpression.getTimeAfter 前进）路径均无此问题，仅 OnceTrigger 无状态推进。

### [P1] worker 存活链默认装配缺失：worker 崩溃后 RUNNING 任务/fire 永久滞留，阻塞策略调度永久卡死

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:{73-75, 239-260, 451-464}`；`nop-job/nop-job-coordinator/src/main/resources/_vfs/nop/job/beans/app-engine.beans.xml`（`IJobTimeoutChecker` bean 定义）
- **维度**: D7 / D1
- **证据**:
```java
// JobTimeoutCheckerImpl.java:73-75 —— 普通 setter，无 @Inject，IoC 不会自动装配
public void setNamingService(INamingService namingService) {
    this.namingService = namingService;
}
// JobTimeoutCheckerImpl.java:239-243 —— 未装配时存活检测整体关闭
private Set<String> aliveWorkerIds() /* resolveAliveWorkerIds */ {
    if (namingService == null) {
        return null;                       // → tryMarkSuspiciousIfWorkerGone 直接跳过
    ...
// JobTimeoutCheckerImpl.java:458-464 —— 无超时配置时明示依赖该链路兜底
    // plan 340 §2.1 (P1-1): no execution timeout configured — do not fall back to
    // dispatchTimeoutMs ... Long-running tasks ... are instead recovered by the
    // worker-liveness chain (SUSPICIOUS -> TIMEOUT) when their worker disappears.
    return;
```
```xml
<!-- app-engine.beans.xml —— IJobTimeoutChecker bean 只注入 partitionResolver/alarmHandler，无 namingService -->
<bean id="io.nop.job.coordinator.engine.IJobTimeoutChecker"
      class="io.nop.job.coordinator.engine.JobTimeoutCheckerImpl">
    <property name="partitionResolver" ref="nopJobPartitionResolver"/>
    <property name="alarmHandler" ref="io.nop.job.api.alarm.IJobAlarmHandler"/>
</bean>
```
- **现状**: 代码注释宣称"无执行超时的长任务由 worker 存活链（SUSPICIOUS→TIMEOUT）在 worker 消失时回收"，但 `setNamingService` 是无 `@Inject` 的普通 setter，且全模块 beans.xml 均未配置该 property（grep 全 nop-job XML 无 NamingService 装配），默认部署下 `namingService == null` 恒成立，`resolveAliveWorkerIds` 恒返回 null，`tryMarkSuspiciousIfWorkerGone` 永不执行。同时 `TIMEOUT_SECONDS` 列无默认值、`nop.job.coordinator.execution-timeout-ms` 默认 -1，`tryMarkTimeout` 直接 return。另外 `resolveAliveWorkerIds` 用 `AppConfig.appName()` 作为发现服务名，独立部署的 worker（appName 与 coordinator 不同）即使接线也无法命中实例。
- **风险**: worker 进程被 kill -9 / 宕机后，其 RUNNING 任务永远停留 RUNNING，所属 fire 永远停留 RUNNING（`resolveFinalStatus` 对 pending 任务返回 null，completion 处理器永不终结），`schedule.activeFireCount` 永久 ≥1：blockStrategy 为 DISCARD/RECOVERY/OVERLAY 的调度从此每个周期命中 `shouldDiscard/shouldRecovery/shouldOverlay` 分支而永久不再产生新 fire（DISCARD 是永久跳过、RECOVERY 反复复用同一 failed fire 也依赖其先行终结）；fixedDelay 型调度的 `nextFireTime` 为 null（等上次 fire 完成时计算），fire 不终结则永久停摆。任务永久泄漏且无告警。
- **建议**: 在 `app-engine.beans.xml` 为 `IJobTimeoutChecker` 增加 `namingService` 装配（`ioc:default="true"` 容器有则注入），或给 `setNamingService` 加 `@Inject @Nullable`；服务名改为可配置（如 `nop.job.worker.service-name`）而非复用 `AppConfig.appName()`；至少在 `namingService == null` 且无任何超时配置时打一次 WARN 提示恢复链路不可用。
- **误报排除**: 通读了 `JobTimeoutCheckerImpl` 全文（含 `resolveAliveWorkerIds` 异常回退 null、`tryMarkTimeout` 三级超时来源）、`app-engine.beans.xml` 全部 bean 定义、全模块 XML grep 确认无 `namingService` 属性装配；确认 `fetchRunningFires` 只取 RUNNING、`JobFireStateMachine.resolveFinalStatus` 对 RUNNING 任务返回 null、`JobScheduleStoreImpl.fetchDueSchedules` 过滤 ENABLED+nextFireTime<=now（null 被排除）、planner 的 `shouldDiscard/shouldRecovery` 均以 `activeFireCount>0` 为前提。

### [P1] RemoteJobInvoker 全局单线程轮询执行器 + poll RPC 无超时注入：单个慢 RPC 阻塞全部 rpcPoll 任务

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RemoteJobInvoker.java:{50-56, 122-175}`；`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/HttpRpcPollTaskClient.java:{64-115, 195-201}`
- **维度**: D3 / D6
- **证据**:
```java
// RemoteJobInvoker.java:50-56 —— 所有 rpcPoll 任务共享一个静态单线程执行器
private static final ScheduledExecutorService POLL_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "nop-job-rpcPoll");
    t.setDaemon(true);
    return t;
});
// RemoteJobInvoker.java:122-153 —— 轮询回调内串行执行 DB 加载 + 同步 RPC
java.util.concurrent.ScheduledFuture<?> pollHandle = POLL_EXECUTOR.scheduleWithFixedDelay(() -> {
    ...
    NopJobTask fresh = taskStore.loadTask(task.getJobTaskId());      // DB IO
    ...
    TaskStatusBean status = rpcPollTaskClient.getJobStatus(schedule, fire, fresh); // 同步 RPC
```
```java
// HttpRpcPollTaskClient.java —— 仅 startJob 注入超时头；getJobStatus/cancelJob 不注入
private static void injectTimeoutHeader(ApiRequest<Object> request, NopJobSchedule schedule) { ... }  // 只在 startJob 调用
public TaskStatusBean getJobStatus(...) {                            // 无 injectTimeoutHeader
    ApiRequest<Object> request = new ApiRequest<>();
    injectFrameworkHeaders(request, fire, task, schedule);
    injectTargetHost(request, task);
    ...
    ApiResponse<?> response = FutureHelper.syncGet(rpcServiceInvoker.invokeAsync(...));  // 无界等待
```
- **现状**: 每个 rpcPoll 任务的轮询回调都在同一个静态单线程 `POLL_EXECUTOR` 上串行执行 `taskStore.loadTask`（DB 查询）与 `FutureHelper.syncGet(getJobStatus)`（同步 RPC）。`getJobStatus`/`cancelJob` 不注入 `HEADER_TIMEOUT`（只有 `startJob` 调 `injectTimeoutHeader`），`syncGet` 无超时参数，超时完全依赖 `IRpcServiceInvoker` 的全局默认配置。
- **风险**: 任一 getJobStatus RPC 挂起（网络分区 + rpc 客户端默认超时未配置/过长）会独占唯一的 poll 线程，**所有** rpcPoll 任务的轮询全部停摆（DB 查询同样串行受累）；即使 RPC 正常，N 个并发 rpcPoll 任务 × 轮询间隔 5s 时，单线程吞吐 N×RTT 超 5s 即造成轮询周期系统性劣化。最终依赖 TimeoutChecker 墙钟超时回收（又依赖超时配置，见 P1-2），否则任务/fire 永久滞留。
- **建议**: `POLL_EXECUTOR` 改为小型线程池（按并发 rpcPoll 任务规模配置）；`getJobStatus`/`cancelJob` 同样注入 `HEADER_TIMEOUT`（可用独立较短配置如 `nop.job.remote.poll-timeout-ms`），或 `syncGet` 换成带超时的 `orTimeout/get(timeout)`。
- **误报排除**: 通读了 `RemoteJobInvoker` 全文（含 `future.whenComplete → pollHandle.cancel` 的空转累积修复注释，确认调度项会取消、问题只在串行阻塞本身）与 `HttpRpcPollTaskClient` 三个方法（确认超时头只在 startJob 注入）；`IRpcServiceInvoker` 默认超时在模块外无法证实，风险表述已按"依赖外部默认超时"条件化。

### [P2] CLAIMED 滞留任务无任何回收路径：worker 在 CAS 认领后、置 RUNNING 前崩溃则 fire 永久 RUNNING

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobTaskStoreImpl.java:{108-118}`；`nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:{260-279}`
- **维度**: D1
- **证据**:
```java
// JobTaskStoreImpl.java:113-118 —— fetchRunningTasks 显式排除 startTime 为 null 的行（CLAIMED 态 startTime 尚为 null）
query.addFilter(FilterBeans.in(PROP_NAME_taskStatus, JobTaskStateMachine.RUNNING_LIKE_STATUSES));
// CLAIMED∈RUNNING_LIKE_STATUSES但其startTime为null（进入RUNNING前才写入）：超时检查对
// null startTime本就跳过（tryMarkTimeout提前return），纳入扫描只会在满批尾部把cursor推进为
// (null,id)，下一轮fetchRunningTasks抛IllegalArgumentException使整轮扫描停滞。
query.addFilter(FilterBeans.not(FilterBeans.isNull(PROP_NAME_startTime)));
```
```java
// JobWorkerScannerImpl.java —— 认领(tryLockTasksForExecute→CLAIMED)与置 RUNNING 是两次独立事务，中间崩溃窗口无回收
List<NopJobTask> lockedTasks = taskStore.tryLockTasksForExecute(tasks, AppConfig.hostId(), lockTimeoutMs);
...
boolean acquired = taskStore.updateTask(runningTask);   // CLAIMED→RUNNING + startTime
```
- **现状**: `tryLockTasksForExecute` 提交 CLAIMED 后，若 worker 在 `updateTask(RUNNING)` 前崩溃，任务停留在 CLAIMED 且 `startTime=null`。该行被 `fetchRunningTasks` 的 `not(isNull(startTime))` 过滤排除（超时扫描与存活链都看不到它）；`resetStaleWaitingTasks` 只处理 WAITING；`cancelTasks`/完成聚合等路径也不会推进它。代码注释自认"CLAIMED滞留回收属另一课题"。
- **风险**: 该 CLAIMED 任务永不终结 → `resolveFinalStatus` 恒返回 null → 所属 fire 永久 RUNNING、`activeFireCount` 永久不减，与 P1-2 相同的连锁后果（阻塞策略卡死、fixedDelay 停摆）。触发窗口为两次事务之间的毫秒级间隔，概率低但为永久性损伤。
- **建议**: 为 CLAIMED 态增加租约回收：`tryLockTasksForExecute` 写入 claimTime，超时扫描增加"CLAIMED 且 claimTime 超 lockTimeoutMs（或 taskDispatchWaitTimeoutMs）→ 重置 WAITING/置 SUSPICIOUS"分支；或把 CLAIMED 判定从 `startTime != null` 改为独立列以纳入扫描。
- **误报排除**: 通读了 `fetchRunningTasks` 全部过滤条件、`resetStaleWaitingTasks`（仅 WAITING）、`JobTimeoutCheckerImpl.scanTaskTimeouts`/`tryMarkTimeout`（依赖 startTime）、`tryLockTasksForExecute` 注释（明确"lockTimeoutMs 参数当前不参与认领判定…租约机制需设计引入"）、`JobTaskStateMachine`（CLAIMED 仅在 IN_FLIGHT/RUNNING_LIKE 集合，无回收转移）。

### [P2] HolidayCalendarSpec 年度位串无长度/闰年校验：配置错误导致 schedule 永不触发且每周期异常刷屏

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/CalendarBuilder.java:{100-114}`
- **维度**: D1 / D4
- **证据**:
```java
for (Map.Entry<String, String> entry : spec.getYearDays().entrySet()) {
    int year = ConvertHelper.toPrimitiveInt(entry.getKey(), NopException::new);
    String str = entry.getValue();
    if (!StringHelper.isEmpty(str)) {
        for (int i = 0, n = str.length(); i < n; i++) {
            if (str.charAt(i) == '1') {
                LocalDate date = LocalDate.ofYearDay(year, i + 1);   // i+1 > 年长度即抛 DateTimeException
                days.add(date);
            }
        }
    }
}
```
- **现状**: `yearDays` 的位串第 i 位表示第 i+1 天，但未校验 `str.length() <= 365/366`，也未校验该年是否闰年。平年配置了 366 个字符且第 366 位为 '1'（如把闰年配置复制到平年），`LocalDate.ofYearDay(2023, 366)` 抛 `DateTimeException`。该异常在 planner 路径经 `TriggerBuilder.buildTrigger → calculateNextFireTime` 抛出，被 `JobPlannerScannerImpl` 的 per-schedule catch 捕获仅记 error；`nextFireTime` 未推进（仍是过去时刻），下个周期再次失败。
- **风险**: 一条静默的配置数据错误使该 schedule 永不触发，且 planner 每 5s 抛一次异常刷 error 日志（还会每周期重复占用批量槽位）；LocalJobScheduler 路径则在 addJob 时直接失败。业务侧表现为"任务神秘消失 + 日志风暴"，无指向配置的错误提示。
- **建议**: 解析前校验 `str.length()` 与当年 `Year.isLeap` 上界，非法位直接截断或抛带 `yearDays` 上下文的 `NopException`（配置期 fail-fast），而非裸 `DateTimeException`。
- **误报排除**: 通读了 `CalendarBuilder.buildCalendar` 全部分支（其余分支均有 isEmpty 防护，唯 HolidayCalendarSpec 无长度校验）、`LocalDate.ofYearDay` 语义（dayOfYear 超界抛异常）、planner 的 catch 路径（`plan-schedule-failed` per-schedule 隔离，异常不中断整批但也不推进 nextFireTime）。

### [P3] overlay/manual 取消路径 activeFireCount 减法无下限保护，reconciler 对 recorded<=0 不校正（与其余路径不一致）

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java:{145, 288-289}`；`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobScheduleCounterReconciler.java:{78, 95}`
- **维度**: D1
- **证据**:
```java
// overlayFireAndAdvanceSchedule / insertManualFire —— 无 Math.max 保护
schedule.setActiveFireCount(defaultInt(schedule.getActiveFireCount()) - cancelledCount + 1);
// 对照：cancelFire/completeSingleFire/tryMarkDispatchTimeout 均有保护
schedule.setActiveFireCount(Math.max(0, defaultInt(schedule.getActiveFireCount()) - 1));
// reconciler 只拉取/校正 recorded > 0 的 schedule
query.addFilter(FilterBeans.gt(NopJobSchedule.PROP_NAME_activeFireCount, 0));
...
if (recorded <= 0) { return; }
```
- **现状**: 计数漂移向下（recorded 偏小或为 0）时 reconciler 因 `gt(activeFireCount, 0)` 过滤与 `recorded <= 0` 早退永不介入；overlay/manual 的减法在计数已偏小时可产生负值或错误归零，负值又使 `shouldDiscard/shouldOverlay/shouldRecovery`（以 `activeFireCount > 0` 判定）永久失效退化为并发执行。
- **风险**: 已知计数漂移场景下行为不一致；当前未找到能直接构造负值的稳定触发路径（fire 与计数更新同事务、乐观锁互斥），属防御缺口而非已证 bug。
- **建议**: 两处减法补 `Math.max(0, ...)`；reconciler 放宽为 `recorded != actual` 即校正（含 recorded<=0 但存在 active fire 的方向）。
- **误报排除**: 通读了 `overlayFireAndAdvanceSchedule`/`insertManualFire` 的计数数学、`JobFireStateMachine.ACTIVE_STATUSES`、reconciler 的 fetch/早退逻辑，并核对四个减一处中仅这两处无保护。

### [P3] 未知 blockStrategy 处理不一致：activeFireCount==0 时按默认插入执行，>0 时跳过

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java:{185-195}`
- **维度**: D1
- **证据**:
```java
if (defaultInt(schedule.getActiveFireCount()) > 0
        && schedule.getBlockStrategy() != null
        && !isKnownBlockStrategy(schedule.getBlockStrategy())) {
    LOG.warn("nop.job.planner.unknown-block-strategy:...defaulting to DISCARD", ...);
    scheduleStore.advanceScheduleAfterSkip(schedule, nextFireTime);
    return;
}
scheduleStore.insertFireAndAdvanceSchedule(schedule, fire, nextFireTime, ...);
```
- **现状**: 未知 blockStrategy 值在忙时被当作 DISCARD（跳过），闲时落到末尾按普通插入执行——同一非法配置产生两种不同行为，且 warn 日志只在忙时出现。
- **风险**: 非法配置的排障体验差、行为不可预测；无直接数据损伤（执行或跳过二选一）。
- **建议**: 未知 blockStrategy 统一 fail-fast（fire 置 FAILED 并带错误码）或统一按默认策略执行并在 schedule 加载时校验。
- **误报排除**: 通读了 `planSchedule` 全部 blockStrategy 分支与 `isKnownBlockStrategy` 集合，确认默认值（null）不进该分支、仅非法整数值受影响。

### [P3] FireFactory.fillBaseFireFields 为空实现，调用点形同虚设

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/fire/FireFactory.java:{7-9}`
- **维度**: D8
- **证据**:
```java
public class FireFactory {
    public static void fillBaseFireFields(NopJobFire fire, Timestamp fireTime) {
    }
}
```
- **现状**: 公开静态工具方法体为空，`NopJobScheduleBizModel.buildManualFire:209` 与 `NopJobFireBizModel.buildRecoveryFire:213` 仍调用它并传入 fireTime，预期"填充基础字段"的调用点实际无效果。
- **风险**: 死代码/契约漂移；后续维护者可能误以为基础字段已统一填充（如 duration/startTime 类公共字段），掩盖真实的字段缺失。
- **建议**: 删除该方法及两处调用，或补齐应有逻辑。
- **误报排除**: grep 全模块确认仅上述两个调用点；两处调用点均已显式设置全部所需字段（triggerSource/scheduledFireTime/fireStatus/plannerInstanceId/triggeredBy/partitionIndex/jobParamsSnapshot/executorKind/dispatchMode），空方法当前无功能影响。

### [P3] HttpRpcPollTaskClient.startJob 不检查 response.isOk()，远程错误细节丢失

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/HttpRpcPollTaskClient.java:{80-90}`
- **维度**: D4
- **证据**:
```java
ApiResponse<?> response = FutureHelper.syncGet(rpcServiceInvoker.invokeAsync(
        serviceName, startMethod, request, null));
Object result = response.getData();
if (result == null) {
    throw new NopException(ERR_JOB_REMOTE_INVOKE_FAILED)
            .param("taskId", task.getJobTaskId());       // 丢失 response.getCode()/getMsg()
}
```
- **现状**: startJob 拿到错误响应（`!isOk()`，data 通常为 null）时抛出的异常只带 taskId，不带远程返回的 code/msg；对照 `getJobStatus`（判 `isOk()`）与 `cancelJob`（返回 `isOk()`）。
- **风险**: 远程启动失败时排障信息缺失，只能看到笼统的 REMOTE_INVOKE_FAILED；该异常会被 `RemoteJobInvoker` 再压一层（见下条），信息进一步丢失。
- **建议**: 非 ok 时抛出携带 `response.getCode()/getMsg()` 的 NopException。
- **误报排除**: 通读三个方法对照确认仅 startJob 缺 isOk 检查；`RemoteJobInvoker.invokeAsync` 的 catch 会捕获该异常（见下条），确认信息确实被丢弃。

### [P3] RemoteJobInvoker.invokeAsync 的 catch-all 把具体错误码统一抹平为 ERR_JOB_REMOTE_INVOKE_FAILED

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RemoteJobInvoker.java:{100-106}`
- **维度**: D4
- **证据**:
```java
} catch (Exception e) {
    LOG.warn("nop.job.remote.start-failed:taskId={}", jobCtx.getAttributes().get("jobTaskId"), e);
    future.complete(JobFireResult.ERROR(toError(ERR_JOB_REMOTE_INVOKE_FAILED)));
}
```
- **现状**: `startJob` 抛出的 `ERR_JOB_SERVICE_NAME_REQUIRED`、`ERR_JOB_REMOTE_TASK_LOST`（loadTask 失败）等带语义的错误码，在这里一律替换为 `ERR_JOB_REMOTE_INVOKE_FAILED` 写回 task.errorCode。
- **风险**: 任务失败原因失真（如"serviceName 未配置"被记成"远程调用失败"），误导排障与基于错误码的告警分类。
- **建议**: catch 中若 `e instanceof NopException` 且已有 errorCode，则透传原始 code。
- **误报排除**: 通读了 `invokeAsync` 全部 throw 点（`loadTask` 的 TASK_LOST、`requireServiceName` 的 SERVICE_NAME_REQUIRED、startJob 的 REMOTE_INVOKE_FAILED）确认均被抹平；`DefaultJobExecutionContextBuilder.buildResultUpdate` 会将该 ErrorBean 原样写入 task.errorCode。

### [P3] DefaultWorkerLoadProvider 的 ThreadLocal 扫描缓存不 remove，多线程共享时 beginScan 互相清空

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/DefaultWorkerLoadProvider.java:{42, 56-61}`
- **维度**: D3
- **证据**:
```java
private final ThreadLocal<Map<String, List<WorkerLoad>>> scanCache = ThreadLocal.withInitial(HashMap::new);
...
public void beginScan() { scanCache.get().clear(); }
public void endScan()   { scanCache.get().clear(); }
```
- **现状**: singleton bean 的 scan 缓存放在永不 `remove()` 的 ThreadLocal 里；若多个 dispatcher 扫描线程共享该 bean（注释明示该设计意图），任一线程的 `beginScan()` 会清掉其他线程正在进行中的缓存（仅损失命中率，无正确性影响）；线程池长生命周期线程各驻留一个空 HashMap。
- **风险**: 轻微内存驻留与缓存抖动；无数据错误。
- **建议**: `endScan()` 改用 `scanCache.remove()`；或缓存键加 scan 代次（AtomicLong generation）。
- **误报排除**: 通读了 dispatcher 的 `beginScan/finally endScan` 配对调用（异常路径也会 endScan），确认无泄漏放大；正确性影响仅在多线程同 bean 场景，单 dispatcher 线程下无影响。

### [P3] planner/completion/timeout 热路径每次计算都重建 trigger：cron 表达式与 pauseCalendarSpec 重复解析

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/trigger/JobTriggerCalculator.java:{25-26}`；`nop-job/nop-job-core/src/main/java/io/nop/job/core/trigger/TriggerBuilder.java:{24-26}`；`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/helper/TriggerSpecHelper.java:{81-86}`
- **维度**: D6
- **证据**:
```java
// JobTriggerCalculator —— 每次计算重新 build
ITrigger trigger = TriggerBuilder.buildTrigger(spec, defaultCalendar);
return trigger.nextScheduleTime(now, evalContext);
// TriggerBuilder:25 —— 每次 new CronExpression（tokenize + replaceOrdinals + BitSet 构建）
CronExpression cronExpr = new CronExpression(spec.getCronExpr());
// TriggerSpecHelper.parsePauseCalendars —— 每次 JsonTool.parseBeanFromText 解析同一 JSON 串
return JsonTool.parseBeanFromText(json, JavaGenericTypeBuilder.buildListType(CalendarSpec.class));
```
- **现状**: planner 每 5s 对每个 due schedule 调 `calculateNextFireTime`（completion 的 fixedDelay 路径、enable/resume 亦然），每次都重新解析 cron 与 pause 日历 JSON 并重建对象图，`CronExpression` 内含 ThreadLocal GregorianCalendar 初始化。
- **风险**: 每 schedule 每周期约一次表达式解析 + 一次 JSON 反序列化（batch 100 / 5s 量级约 20 次/s），CPU 浪费有限但随 schedule 数线性放大；无功能危害。
- **建议**: 以 `cronExpr + pauseCalendarSpec + triggerType + interval` 为 key 做有界缓存（如 Caffeine）复用不可变 trigger 链（`OnceTrigger` 的可变状态除外——需先修 P0）。
- **误报排除**: 通读了 `calculateNextFireTime` 的全部调用点（planner/completion/fireStore/bizModel）确认均为一次性构建后即丢弃；确认 `CronExpression`/日历对象本身不可变、可安全复用（`OnceTrigger` 除外）。

### [P3] dispatch 超时子扫描 cursor 对 null startTime 防御不对称：advance(null, id) 使下轮整轮扫描中止

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:{163-180}`；`nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java`（drainBatch）；`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java:{239-241}`
- **维度**: D1
- **证据**:
```java
// processDispatchTimeouts —— 单条防御 null
Timestamp startTime = fire.getStartTime();
if (startTime == null) { continue; }
// drainBatch —— cursor 无条件用最后一条推进（可能为 null）
T last = batch.get(batch.size() - 1);
cursor.advance(timeFn.apply(last), idFn.apply(last));
// fetchDispatchingFires —— (null, id) 组合直接抛
if (cursorTime == null && cursorId != null) {
    throw new IllegalArgumentException("cursorId requires cursorTime");
}
```
- **现状**: 若满批最后一条 DISPATCHING fire 的 startTime 为 null（processDispatchTimeouts 已为它写了 null 防御，说明作者认为该状态可能存在），cursor 推进为 `(null, id)`，下一轮 `fetchDispatchingFires` 抛 `IllegalArgumentException` → 整轮 scanBatch 异常中止（onScanFailed）。正常代码路径 `tryLockFiresForDispatch` 总会写 startTime，故触发需外部写入/数据修复残留。
- **风险**: 罕见数据状态下整轮超时扫描周期性中止（cursor 每周期 reset，形成"满批+null 末位"时的周期性失败），延后超时回收。
- **建议**: `drainBatch` 推进前校验 timeFn 结果非 null，null 时 markDrained 并 warn；或 fetchDispatchingFires 对 null cursorId 做忽略处理。
- **误报排除**: 通读了 drainBatch 模板、三个子扫描的 cursor 用法、`tryLockFiresForDispatch`/`revertDispatchingFireToWaiting` 对 startTime 的写入路径，确认正常流程 startTime 非空、仅异常数据可触发。

### [P3] WeeklyCalendar 暴露内部 excludeDays 数组且 setDaysExcluded 无长度校验（Quartz 遗留）

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/WeeklyCalendar.java:{70-95}`
- **维度**: D1 / D7
- **证据**:
```java
public boolean[] getDaysExcluded() {
    return excludeDays;              // 直接返回内部可变数组
}
public void setDaysExcluded(boolean[] weekDays) {
    if (weekDays == null) { return; }
    excludeDays = weekDays;          // 未校验长度 >= 8；isDayExcluded(wday) 随后可 AIOOBE
    excludeAll = areAllDaysExcluded();
}
```
- **现状**: getter 泄露内部数组（外部修改可破坏 excludeAll 缓存一致性）；setter 接受任意长度数组，长度 <8 时后续 `isDayExcluded(7)` 抛 ArrayIndexOutOfBoundsException。本模块内 `CalendarBuilder` 仅用 `setDayExcluded`（安全路径），风险仅在直接使用 calendar API 的业务代码。
- **风险**: 误用时的运行时异常与状态不一致；模块内部当前无触发点。
- **建议**: getter 返回克隆；setter 校验长度并抛 `NopException`（对齐 MonthlyCalendar 的做法）。
- **误报排除**: 通读了 `CalendarBuilder` 对 WeeklyCalendar 的唯一使用路径（`setDayExcluded` 循环）与 `isTimeIncluded/getNextIncludedTime` 的数组访问范围。

### [P3] BeanContainerInvokerResolver 对 bean 类型无 instanceof 检查，直接强转

- **文件**: `nop-job/nop-entropy-wt/.../nop-job/nop-job-local/src/main/java/io/nop/job/local/config/BeanContainerInvokerResolver.java:{12-17}`
- **维度**: D1
- **证据**:
```java
public class BeanContainerInvokerResolver implements Function<String, IJobInvoker> {
    static final String INVOKER_PREFIX = "nopJobInvoker_";
    @Override
    public IJobInvoker apply(String invokerName) {
        String beanName = INVOKER_PREFIX + invokerName;
        return (IJobInvoker) BeanContainer.tryGetBean(beanName);   // 无类型检查
    }
}
```
- **现状**: `nopJobInvoker_<name>` 存在但不是 `IJobInvoker` 时抛裸 ClassCastException（null 时 cast 为 null 安全返回，`LocalJobScheduler.addJob` 有 null 检查）；对照 `DefaultJobInvokerResolver`/`DefaultJobCancelHandler` 均用 `bean instanceof IJobInvoker` 判定。
- **风险**: 配错 bean 类型时报错信息为无上下文的 CCE，而不是语义化的 ERR_JOB_INVOKER_NOT_FOUND。
- **建议**: 对齐 `DefaultJobInvokerResolver` 的 instanceof + ERR_JOB_INVOKER_NOT_FOUND 模式。
- **误报排除**: 通读了 `LocalJobScheduler.addJob` 对 null 返回值的处理（抛 ERR_JOB_BEAN_NOT_FOUND，仅覆盖 null 不覆盖类型不符）与两个对照实现的判定方式。

### [P3] insertTasksAndMarkFireDispatching 静默返回时 dispatcher 的 dispatchedCount/metrics 虚增

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java:{159-160}`；`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java:{102-106}`
- **维度**: D4（可观测性）
- **证据**:
```java
// JobFireStoreImpl —— fire 已非 DISPATCHING 时静默 return（不插入 task、不抛错、无返回值）
NopJobFire currentFire = fireDao().requireEntityById(fire.getJobFireId());
if (!JobFireStateMachine.isDispatching(currentFire.getFireStatus())) {
    return;
}
// JobDispatcherScannerImpl —— 无视上述静默分支，无条件计数
fireStore.insertTasksAndMarkFireDispatching(fire, tasks);
dispatchedCount++;                    // 即使 tasks 实际未插入也计数
```
- **现状**: fire 被并发推进（如 revert/timeout）时任务未插入，但 dispatcher 仍 `dispatchedCount++` 并计入 `onFiresDispatched` 指标，无任何日志表明发生静默跳过。
- **风险**: 仅指标/日志层面的虚增（fire 本身会由超时链路兜底），无数据损伤；静默跳过缺观测信号。
- **建议**: `insertTasksAndMarkFireDispatching` 返回 boolean（或抛出），dispatcher 据此计数并在静默跳过时打 debug 日志。
- **误报排除**: 通读了该 store 方法的调用点（仅 dispatcher 一处）与 fire 并发推进路径（revertDispatchingFireToWaiting/tryMarkDispatchTimeout），确认静默分支可达且无其他观测。

### [P3] dispatch 超时的 schedule-deleted 分支给任务写 ERR_JOB_TIMEOUT 错误码，语义错位

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:{296-309}`
- **维度**: D4
- **证据**:
```java
List<NopJobTask> tasks = taskStore.findTasksByFireId(fire.getJobFireId());
Timestamp endTime = new Timestamp(now);
for (NopJobTask task : tasks) {
    ...
    task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_CANCELED);
    task.setErrorCode(ERR_JOB_TIMEOUT.getErrorCode());        // 实际原因是 schedule 已删除
    task.setErrorMessage(ERR_JOB_TIMEOUT.getDescription());
```
- **现状**: fire 的失败原因正确记为 `ERR_JOB_SCHEDULE_DELETED`，但其任务行的 errorCode 统一写 `ERR_JOB_TIMEOUT`，与 fire 层错误码不一致。
- **风险**: 按任务错误码聚合统计/告警时把"调度定义被删"误报为"执行超时"，误导排障。
- **建议**: 任务行复用 `ERR_JOB_SCHEDULE_DELETED`。
- **误报排除**: 通读了 `tryMarkDispatchTimeout` 的两个分支（schedule 存在路径用 ERR_JOB_TIMEOUT 语义正确；deleted 分支错位），确认 fire 与 task 的错误码来源不同。

### [P3] LocalJobScheduler.addJob 更新 SUSPENDED 任务时被 scheduleNext 强制置回 WAITING（静默恢复执行）

- **文件**: `nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:{100-103, 288-289}`
- **维度**: D1
- **证据**:
```java
// addJob 更新已存在 job —— SUSPENDED 也纳入重调度
if (existing.state.internal == InternalState.WAITING || existing.state.internal == InternalState.SUSPENDED) {
    cancelScheduledFire(existing);
    scheduleNext(existing);
}
// scheduleNext —— 无条件改写状态
job.state.internal = InternalState.WAITING;
```
- **现状**: 对处于 SUSPENDED 的 job 执行 `addJob(spec, allowUpdate=true)`（如配置热更新）会经 `scheduleNext` 把状态改回 WAITING 并重新排程，静默撤销了 `suspendJob` 的效果。
- **风险**: 配置更新隐式恢复被暂停的任务，违背运维预期（暂停中的任务突然开始执行）；无数据损坏。
- **建议**: update 路径保持 SUSPENDED 状态不变（仅替换 spec/trigger），或在恢复时打 INFO 日志。
- **误报排除**: 通读了 `addJob` 的 existing/raced 两条更新路径（行为一致）、`suspendJob`/`resumeJob` 的状态转移与 `scheduleNext` 的无条件赋值，确认 SUSPENDED 分支必然被改写。

## 补充说明（已核查、未列为发现的事项）

- `@Transactional(REQUIRES_NEW)`/`@SingleSession` 标注在 protected 方法上的自调用（如 `JobCompletionProcessorImpl.completeSingleFire` 由 `scanBatch` 调用）：经查 Nop 的 AOP 为生成子类覆写（见 `docs/ref/AuditServiceImpl__aop.java` 样例，覆写含 protected 方法），虚分派下注解生效，且 `dao-defaults.beans.xml`/`orm-defaults.beans.xml` 均有对应 pointcut 注册，不构成 Spring 式自调用失效问题。
- `fetchRunningTasks`/`fetchDispatchingFires`/`resetStaleWaitingTasks` 的 `addOrderField(name, true)`（DESC）配合 `lt` cursor 过滤：经核对 `OrderFieldBean` 语义（desc=true）与游标推进方向，DESC+lt 的分页窗口方向一致，无错位。
- D5 安全：`NopJobTask.sql-lib.xml` 使用 EQL 命名参数绑定（非拼接）；`JobLogReporter` 上报地址仅来自受信配置；未发现路径遍历/敏感信息日志/不安全反序列化/随机数误用。
- D7 平台规范：全模块无 `@Inject private` 字段、无 Spring 依赖、无 `@Value`、beans.xml 装配完整（BizModel/store/scanner 均有注册或经 CrudBizModel 机制）。
- `CronExpression` 为 Spring CronSequenceGenerator 移植，递归有 4 年年限保护、findNextDay 有 366 上限、ThreadLocal GregorianCalendar 用法正确；私有构造器对 timeZone/threadLocalCal 的不变式维护有注释说明。
