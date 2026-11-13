# C7 长时运行稳定性维度 — nop-job 第 1 轮初审（盲审）

- 审计单元：`nop-job` 聚合器（12 jar 子模块），src/main/java 手写代码 164 files / 17,664 lines
- 仓库：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- 审计日期：2026-10-14
- 盲审纪律：未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件；只审当前 live code；`_` 前缀生成文件不作为发现对象
- Owner doc 对照：docs-for-ai/03-modules/nop-job.md（规模包络/边界声明对照在每条发现中标注）

---

## 检查范围说明

C7 维度六个子项 × 缺陷族定向对照的覆盖方式：

| C7 子项 | 覆盖文件（主要） | 结论位置 |
|---|---|---|
| 1. 单调增长盘点（F4 账本/内存历史/metrics 基数/日志放大） | JobFireStoreImpl、JobScheduleStoreImpl、JobTaskStoreImpl、NopJobTaskLogBizModel、4 个 MetricsImpl、RpcPollTaskManager、JobWorkerScannerImpl | 清单 + C7-001/004 |
| 2. 挂死可检测性（F5） | JobTimeoutCheckerImpl（liveness 链）、AbstractBatchScanner、JobWorkerScannerImpl（invoker promise）、RpcPollTaskManager（poll 死循环）、LocalJobScheduler | 清单 + C7-001/002/003/007 |
| 3. 重试风暴 | JobDispatcherScannerImpl（no-worker backoff）、JobPlannerScannerImpl（misfire/recovery）、NopRetryJobRetryBridge（委托 nop-retry 策略）、HandleMisfireTrigger、PeriodicTrigger | 覆盖度（正面）+ C7-001（无退避重试） |
| 4. 自愈语义（旧租约/fencing） | JobScheduleCounterReconciler、JobTimeoutCheckerImpl、JobWorkerScannerImpl（AR-85 CAS）、JobScheduleStoreImpl（planner 锁/overlay/recovery）、DefaultJobCancelHandler | 清单 + C7-003/005/006 |
| 5. 时钟与期限 | JobScheduleStoreImpl/JobFireStoreImpl（DB estimated clock）、RpcPollTaskManager（JVM 墙钟）、LocalJobScheduler（JVM 墙钟）、CronTrigger/PeriodicTrigger/AnchorRepeatTrigger/PauseCalendarTrigger | C7-008 + 覆盖度 |
| 6. 每次调用增长的小对象 | AbstractBatchScanner（Cursor per-cycle 复位）、drainBatch、drain 循环内集合 | 覆盖度（正面） |

阅读方法：全文精读核心引擎 22 个文件（coordinator engine 17、worker engine 5、local 3、dao store 3、retry-adapter 1、service BizModel 4、trigger 10、api 若干），其余 DTO/spec/calendar 文件为无状态纯计算，按 grep 基线 + 抽查覆盖。

---

## 单调增长源清单

| # | 增长源 | 位置 | 类型 | 有界性判定 |
|---|---|---|---|---|
| G1 | `nop_job_fire` 行（每次触发 1 行，永久保留） | JobPlannerScannerImpl.buildFire → nop_job_fire | DB 账本（F4，受 DB 约束） | 无任何清理/归档路径；BizModel delete 显式阻断 → C7-004 |
| G2 | `nop_job_task` 行（每 fire × fan-out，广播=健康实例数） | JobFireStoreImpl.insertTasksAndMarkFireDispatching | DB 账本（F4，受 DB 约束） | 同上 → C7-004；per-fire task 数有注释声明边界（广播=实例数） |
| G3 | `nop_job_task_log` 行（reportTaskLog 批量端点逐条落库） | NopJobTaskLogBizModel.reportTaskLog | DB 账本（F4，受 DB 约束） | 无清理路径 → C7-004 |
| G4 | `RpcPollTaskManager.entries`（ConcurrentHashMap，每在途 rpcPoll 任务 1 条目 + 1 个 fixed-delay 句柄） | RpcPollTaskManager.java:65,105-128 | 内存账本（F4） | 终态路径不闭合（见 C7-001）→ 可单调增长 |
| G5 | `nop_job_schedule` 计数列（totalFireCount/successFireCount/fireCount 等 long 递增） | JobCompletionProcessorImpl.completeSingleFire、JobScheduleStoreImpl | DB 计数器 | long 上限，2^63 次触发不可达，非风险 |
| G6 | metrics 计数器/Timer（nop.job.* 固定名，无 label） | JobPlannerMetricsImpl/JobDispatcherMetricsImpl/JobCompletionMetricsImpl/JobWorkerMetricsImpl | Micrometer | 全部固定 meter 名、零标签 → 无标签基数增长，**合格** |
| G7 | 扫描周期内临时对象（Cursor/HashMap/batch list） | AbstractBatchScanner（cursor 每周期 reset；registeredCursors 固定 1-3 个/扫描器） | JVM 短生命周期 | 每周期重建、GC 可回收，无累积 → **合格** |
| G8 | DefaultWorkerLoadProvider.scanCache（ThreadLocal HashMap） | DefaultWorkerLoadProvider.java:42,55-68 | per-scan 缓存 | beginScan 清空 + endScan remove()，线程驻留已修复 → **合格** |

## 挂死点清单（F5：可能永久阻塞/停摆的点及其可见性）

| # | 挂死点 | 触发条件 | 现有检测 | 判定 |
|---|---|---|---|---|
| H1 | rpcPoll 条目轮询死循环（pollOnce 每 5s 永久执行） | schedule.timeoutSeconds 未配 + 远端永不返回终态 | 无（deadline=0 时无墙钟；DB 回收不联动注册表） | **盲区** → C7-001 |
| H2 | invoker promise 永不完成 → task 永久 RUNNING | worker 存活 + 业务 invoker future 泄漏/无超时；executionTimeoutMs=-1（默认）且 timeoutSeconds=0 | liveness 链只覆盖 worker 消失；无 wall-clock；无运行时长 metric | **盲区**（部分声明边界）→ C7-002 |
| H3 | scanner 线程阻塞在 DB 查询（无 socket/query timeout 配置时） | 平台层 JDBC 超时未配置 + DB 网络分区 | 无 stall 检测/心跳；scheduleWithFixedDelay 停摆且无日志 | **盲区** → C7-007 |
| H4 | LocalJobScheduler `job.running` 永不清（invoker future 永不完成） | 本地模式 invoker 返回不终结的 CompletionStage | 无（fireNow 返回 false、awaitIdle 永等） | owner doc 声明本地模式不支持超时控制 → **已声明边界**（覆盖度注记） |
| H5 | CLAIMED 态任务滞留（认领后 worker 崩溃） | claim 后置 RUNNING 前崩溃 | startTime 于认领时写入（check2 P2-1 修复）+ isInFlight 含 CLAIMED + SUSPICIOUS→TIMEOUT | **已闭合**（正面） |
| H6 | fire 卡 DISPATCHING（builder 抛错/实例全不健康） | 配置错误或 0 健康实例 | dispatchTimeoutMs（默认 5min）→ TIMEOUT 回收；no-fitting-worker revert+backoff | **已闭合**（正面） |
| H7 | worker 失联 | 进程崩溃/网络分区 | namingService liveness → SUSPICIOUS → TIMEOUT；namingService 未接线时 WARN 一次 + 回退超时配置 | **已闭合**（check2 P1-1 修复），但见 C7-003 的误杀面 |

---

## 发现条目

### [nop-job-C7-001] rpcPoll 轮询注册表条目无终态联动：永久泄漏 + 无退避重试风暴 + WARN 日志放大

- **严重度**：P1
- **缺陷族**：F4（内存账本只增不删）+ F5（挂死轮询不可见）+ 重试风暴（无退避无上限）
- **位置**：
  - nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcPollTaskManager.java:105-128（register）、175-207（pollOnce）、259-265（computeDeadline）
  - nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RemoteJobInvoker.java:82-119（invokeAsync 创建 future 并注册）
  - nop-job/nop-job-api/src/main/java/io/nop/job/api/execution/IJobExecutionContext.java:22-24（getCancelToken 默认 null）
- **证据片段**（RpcPollTaskManager.java:105-127）：
```java
    public void register(NopJobSchedule schedule, NopJobFire fire, NopJobTask task,
                         ICancelToken cancelToken, CompletableFuture<JobFireResult> future) {
        PollEntry entry = new PollEntry(task.getJobTaskId(), schedule, fire, task, cancelToken, future,
                computeDeadline(schedule));
        entries.put(entry.getTaskId(), entry);
        ...
        Future<?> handle = scheduledExecutor().scheduleWithFixedDelay(
                () -> pollOnce(entry), pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
        entry.setPollHandle(handle);
        // future 终结（终态/取消/超时）后取消调度句柄并移出注册表
        future.whenComplete((r, e) -> cancelEntry(entry));
```
```java
    // RpcPollTaskManager.java:181-203（deadline=0 时不超时；getJobStatus 失败仅 WARN 后继续轮询）
            long now = CoreMetrics.currentTimeMillis();
            if (entry.getDeadline() > 0 && now >= entry.getDeadline()) { ... }
            ...
            rpcPollTaskClient.getJobStatus(...)
                    .whenComplete((status, err) -> {
                        if (err != null) {
                            // 瞬态失败（网络等）：本轮忽略，下轮重查；连续失败由 TimeoutChecker 墙钟兜底
                            LOG.warn("nop.job.remote.poll-failed:taskId={}", entry.getTaskId(), err);
                            return;
                        }
```
```java
    // RpcPollTaskManager.java:259-265：deadline 仅来自 schedule.timeoutSeconds，未配置 = 0 = 永不超时
    private static long computeDeadline(NopJobSchedule schedule) {
        Integer timeoutSeconds = schedule != null ? schedule.getTimeoutSeconds() : null;
        if (timeoutSeconds != null && timeoutSeconds > 0) {
            return CoreMetrics.currentTimeMillis() + timeoutSeconds * 1000L;
        }
        return 0L;
    }
```
- **增长/劣化链**（时间尺度 × 增长源 × 后果）：
  1. 触发前提 1：`schedule.timeoutSeconds` 未配置（owner doc 默认执行超时即为 -1/未配）→ entry 的 `deadline=0`，manager 层无墙钟超时。
  2. 触发前提 2：远端 REST worker 在任务执行中永久失联（发布/回滚/缩容/宕机——周级运行中必然发生）。此时每 5s 的 `getJobStatus` RPC 失败（连接拒绝/实例不存在），走 err 分支仅 WARN 并返回，**下轮继续**：固定 5s 间隔、无退避、无重试次数上限（代码注释所称"TimeoutChecker 墙钟兜底"只回收 DB 行，不 resolve 该 future——全模块检索无任何把 DB 终态回灌到 `entry.future` 的路径；`future.whenComplete→cancelEntry` 是唯一出口）。
  3. 取消链同样失效：`IJobExecutionContext.getCancelToken()` 为 default null，`WorkerJobExecutionContext`/`CancelJobExecutionContext` 均未覆写 → `register` 的 cancelToken 恒 null → 即时取消回调、isCancelled 兜底全部不生效（该分支为死代码）；TimeoutChecker 的 `cancelRunningTask → RemoteJobInvoker.cancelAsync` 只发远端 cancelJob，不触碰 manager future。
  4. 后果：TimeoutChecker 将 DB task 置 TIMEOUT、fire 终结、schedule 推进 nextFireTime → cron 下一轮产生**新** fire/新 task/新 entry。每个失联任务泄漏 1 个 `PollEntry`（capture 的 schedule/fire/task ORM 对象）+ 1 个 `scheduleWithFixedDelay` 句柄（挂在默认 2 线程的 `nopRpcPollScheduledExecutor` 上）。时间尺度：**天-周级单调增长**（如每分钟一次的 rpcPoll 调度 + 远端失联一天 = 1440 条目/天），同时每 entry 每 5s 一次失败 RPC + 一条带堆栈的 WARN（10 个泄漏条目 = 17.2 万条 WARN/天，日志放大）。最终 poll 线程池调度负载与堆占用持续增长，且全程无 metric 暴露 `entries.size()`（`activeCount()` 为 package-private，仅测试用）。
- **判级理由**：P1——使用 rpcPoll 模式的部署在长时运行中"远端失联"近乎必然（发布即触发），一旦发生增长不可逆且无自愈；同时构成无退避无上限的重试风暴与日志放大。非 P0：单条目内存占用小、RPC 失败快速返回，OOM/线程耗尽需要月级+高频调度叠加。
- **边界声明对照**：owner doc 声明了"轮询管理器……条目终结（终态/取消/墙钟超时）即取消自己的调度句柄并移出注册表，自动排除已取消/已超时/已终结条目"——与实际行为不符：**DB 侧取消/超时不会终结条目**，声明覆盖的能力并未闭环。未声明本缺陷场景。
- **误报校准对照**：不适用（非低频粗锁/非有界声明结构——注释中的"墙钟兜底"经核实不覆盖此路径；非平台设施自身实现——RpcPollTaskManager 是 nop-job 模块内代码）。
- **修复方向**：(a) register 时同时接受 schedule.timeoutSeconds 与全局 execution-timeout-ms 计算兜底 deadline（deadline=0 时取全局值）；(b) pollOnce 增加连续失败上限（超过 N 次/D 超时后 resolve REMOTE_TASK_LOST 终结条目）或指数退避；(c) 暴露 entries.size() 为 gauge metric。
- **置信度**：高（全链路代码核实：future 无 DB 终态回灌路径、cancelToken 恒 null、deadline 仅 timeoutSeconds）。
- **影响面**：executorKind=rpcPoll 的部署（coordinator 内嵌 worker 执行链）；DB 模式（RpcJobInvoker）不受影响。
- **残留问题**：需下一轮确认 `IRpcServiceInvoker` 底层是否存在连接级快速失败以外的无限挂起形态（若 RPC 可无限挂起，`pollOnce` 的 fixed-delay 不重叠保证仍成立，但 WARN 频率降低、结论不变）。
- **复核状态**: 已保留（P1 维持。全链重验成立：pollOnce L196-201 err 分支仅 WARN 返回、无失败预算/退避；computeDeadline L259-265 仅读 schedule.timeoutSeconds，全局 execution-timeout-ms 默认 -1（JobTimeoutCheckerImpl.java:53）不参与；cancelToken 恒 null——IJobExecutionContext L22-24 default null 且 WorkerJobExecutionContext/CancelJobExecutionContext/SimpleExecutionContext 均未覆写（grep 全模块零命中）；DB 终态→future 无回灌——DefaultJobCancelHandler L36-44 仅调 invoker.cancelAsync、RemoteJobInvoker.cancelAsync L122-141 仅发 cancelJob RPC，全模块无第二组件触达 pollTaskManager；entries.size() 无 metric 暴露（activeCount package-private 仅测试用）；nopRpcPollScheduledExecutor 默认 2 线程（app-engine.beans.xml L93-94）。反驳面关闭：worker 实例失联时 targetHost 精确路由直接传输失败走 err 分支，不命中 STATUS_NOT_FOUND 解析路径（DefaultRpcPollTaskClient 经 IRpcServiceInvoker + nop-svc-target-host header）。与 C4-001/C5-001 同机制三视角归并方向一致（C4/C5 复核均声明归并、三方判级均 P1 自洽）——本条同向保留 P1。现状：新发现——rpcPoll 特性 2026-08-28（commit 5310efaf97）引入，晚于全部 nop-job 历史审计（最晚 2026-06-19）；历史 P1 修复 72b770b094 系"每次执行泄漏空转调度句柄"反方向且已修，非本条机制）

### [nop-job-C7-002] worker 存活但 invoker promise 永不完成时，task/fire 永久滞留 RUNNING 且零可观测性

- **严重度**：P2
- **缺陷族**：F5（挂死对健康检测不可见）
- **位置**：
  - nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:295-307（invokeAsync 后仅挂 whenComplete）、139-148（maxConcurrency 以 in-flight 计数为闸）
  - nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:489-502（无超时配置时不判超时）
- **证据片段**（JobWorkerScannerImpl.java:296-303）：
```java
        try {
            var promise = invoker.invokeAsync(ctx);
            if (promise == null) {
                handleExecutionResult(runningTask.getJobTaskId(), null,
                        new NopException(ERR_JOB_INVOKER_RETURNED_NULL));
            } else {
                promise.whenComplete((result, err) -> handleExecutionResult(runningTask.getJobTaskId(), result, err));
            }
```
```java
    // JobTimeoutCheckerImpl.java:489-502：默认配置下 RUNNING task 永不判超时
        int timeoutSeconds = defaultInt(schedule.getTimeoutSeconds());
        long effectiveTimeoutMs;
        if (timeoutSeconds > 0) {
            effectiveTimeoutMs = timeoutSeconds * 1000L;
        } else if (executionTimeoutMs > 0) {
            effectiveTimeoutMs = executionTimeoutMs;
        } else {
            // ... Long-running tasks without a per-schedule timeout
            // are instead recovered by the worker-liveness chain (SUSPICIOUS -> TIMEOUT) when
            // their worker disappears.
            return;
        }
```
- **增长/劣化链**：业务 `IJobInvoker` 返回的 CompletionStage 永不终结（invoker 实现缺陷、远程调用无超时且对端黑洞）→ `handleExecutionResult` 永不执行 → task 行永久 RUNNING（无 endTime、无 error）→ fire 永久 RUNNING、`activeFireCount` 恒 >0 → DISCARD 块策略下 planner 的 `shouldDiscard` 恒真，**该 schedule 停止产出一切新 fire（作业静默停摆）**；worker 侧 `maxConcurrency>0` 时 `countInFlightTasks` 恒含该任务，并发额度被永久占用，足够多挂死后 worker 停止认领任何新任务（唯一信号是 rejected counter）。liveness 链不触发（worker 进程存活、注册健康）；默认 `executionTimeoutMs=-1` 下无任何墙钟；无"RUNNING 时长分布"metric、无停滞告警。时间尺度：**永久**（直到人工干预或进程重启）。
- **判级理由**：P2——owner doc 明确声明"未配任何执行超时的长任务不会被墙钟超时误杀……否则不超时"（部分已声明边界），且触发需业务 invoker 自身挂死这一外部缺陷；但"声明不超时"不等于"声明了滞留状态零可见性"——一个不产出也不报错、连 DB 行状态都可识别却无任何监控出口的永久滞留，属监控盲区/边界配置缺陷，判 P2 不立案升级。
- **边界声明对照**：超时语义已声明；"无任何可观测性出口（无 gauge/无 stall 告警）"未声明。另注意 worker-liveness"兜底"注释暗示了覆盖面，实际不覆盖 worker-存活-任务-挂死形态（与 C7-001 同源的声明-实现落差）。
- **误报校准对照**：不适用。
- **修复方向**：(a) worker 侧在 invokeAsync 注册一个 `orTimeout` 式的宽松看门狗（例如 `taskDispatchWaitTimeoutMs` 量级），超时后至少 ERROR 日志 + metric；(b) 暴露 per-worker in-flight 时长直方图或最老 in-flight 时间 gauge；(c) 文档把"无超时配置 + invoker 挂死 = 永久滞留且 DISCARD 调度停摆"写进 owner doc 超时小节。
- **置信度**：高。
- **影响面**：所有分布式模式（DB 模式与 rpcPoll 模式）；本地模式同形态见覆盖度 H4（已声明边界）。
- **残留问题**：`JobFireResult` 是否可能被 invoker 在完成后又丢失（whenComplete 线程被杀）——需 C4 线程池维度交叉确认。
- **复核状态**: 已保留（P2 维持。promise.whenComplete 无超时包装（JobWorkerScannerImpl L296-306）、maxConcurrency 以 countInFlightTasks 为闸（L140-148）、tryMarkTimeout 无超时配置即 return（JobTimeoutCheckerImpl L489-502，executionTimeoutMs 默认 -1 见 L53）、JobWorkerMetricsImpl 仅有 claimed/success/failure/timeout/rejected/execute-failed 计数器 + 1 timer，无 in-flight 年龄 gauge——滞留零可观测性成立；DISCARD 停摆链经 shouldDiscard（JobPlannerScannerImpl L273-277）核实。判级校准：触发前提为业务 invoker 自身挂死（外部缺陷）+ 不超时语义已声明，P2 恰当。交叉注记：与同 campaign C4-009（worker 容量侵蚀视角）为同机制异维度对，7.1 合并裁定时可归并。现状：新发现）

### [nop-job-C7-003] SUSPICIOUS 单向棘轮 + 注册表"全不健康空集"放大：瞬态健康抖动误杀健康在途任务（自愈机制的误伤面）

- **严重度**：P2
- **缺陷族**：F5 逆转形态（健康检测误报导致半个成功无恢复）+ 竞态
- **位置**：
  - nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:249-266（scanTaskTimeouts 本轮新标 SUSPICIOUS 跳过、历史 SUSPICIOUS 直转 TIMEOUT）、268-295（resolveAliveWorkerIds 空集语义）、297-314（tryMarkSuspiciousIfWorkerGone）
  - nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:313-315（isConcurrentlyFinalized 丢弃迟到真实结果）
- **证据片段**（JobTimeoutCheckerImpl.java:251-261, 281-290）：
```java
                int statusBefore = task.getTaskStatus() != null ? task.getTaskStatus() : 0;
                if (aliveWorkerIds != null) {
                    tryMarkSuspiciousIfWorkerGone(task, aliveWorkerIds);
                }
                if (JobTaskStateMachine.isSuspicious(task.getTaskStatus())
                        && !JobTaskStateMachine.isSuspicious(statusBefore)) {
                    continue;   // 仅跳过"本轮刚标 SUSPICIOUS"的 task
                }
                tryMarkTimeout(task, fireMap, scheduleMap);   // 上一轮已 SUSPICIOUS 的无条件转 TIMEOUT
```
```java
            Set<String> alive = new HashSet<>();
            for (ServiceInstance inst : instances) {
                if (inst.isHealthy() && inst.isEnabled()) {
                    alive.add(inst.getInstanceId());
                }
            }
            return alive;   // instances 非空但全部 unhealthy → 返回空集（非 null）→ 全部在途任务判"worker 全灭"
```
- **增长/劣化链**：注册中心健康状态瞬态抖动（worker 一次 GC 停顿超过心跳周期、心跳网络闪断、健康检查超时一次）→ 扫描轮 N：该 worker 全部 CLAIMED/RUNNING task 被置 SUSPICIOUS → 扫描轮 N+1（默认仅 5s 后）：**不复查 worker 是否已恢复存活**，`markSuspiciousAsTimeout` 无条件转 TIMEOUT 并 `cancelRunningTask` → worker 真实完成后的结果写入被 `isConcurrentlyFinalized`（SUSPICIOUS 视为并发终结）丢弃 → fire 聚合为失败/超时 → failFireCount++，若配了 retryPolicy 则经 NopRetryJobRetryBridge 触发重跑 = **实际已成功的工作被重复执行 + 计数失真**。特例放大：`resolveAliveWorkerIds` 在"实例列表非空但全部 unhealthy"时返回**空集**（仅 null/empty-list 才禁用 liveness 检查）→ 注册中心健康服务整体抖动一轮即可把集群全部在途任务判死。两轮确认窗口仅 = 1 个 scan-interval（5s），远小于常见心跳容忍窗口，无法过滤瞬态。
- **判级理由**：P2——罕见但可具体化（注册中心抖动为外部前提，长时运行周级内概率可观），后果限单批在途任务/单 fire 级（调度本身恢复，无内存/账本增长），故不判 P1；但它是自愈链路自身的误伤缺陷（"失败可自愈"维度的反向问题），且空集放大形态无任何文档声明，须立案。
- **边界声明对照**：owner doc 声明"SUSPICIOUS 是有意保留的中间态：它既阻止旧 worker 的迟到结果覆盖"——fencing 单向性本身是声明过的设计取舍；**未声明**：(a) SUSPICIOUS→TIMEOUT 不做存活复查（无恢复路径），(b) "全不健康空集"等价于"worker 全灭"的判定语义。
- **误报校准对照**：不属低频路径粗锁（liveness 判定每 5s 高频执行）；非有界声明结构。
- **修复方向**：(a) markSuspiciousAsTimeout 前复查 `resolveAliveWorkerIds`，worker 已恢复存活则顺延本轮（保留 SUSPICIOUS 等待真实结果或加 stale 上限）；(b) resolveAliveWorkerIds 对"全部 unhealthy"返回 null（视同发现服务不可信，禁用本轮判定）而非空集；(c) SUSPICIOUS 持续时长 metric。
- **置信度**：中高（代码路径确凿；实际误杀频率取决于所配注册中心的健康判定滞后特性）。
- **影响面**：所有分布式部署（含单实例形态——coordinator 与自身 worker 走同一 liveness 链，自身健康抖动会误杀自己正在执行的任务）。
- **残留问题**：与 C6/C3 维度交叠（cancelRunningTask 与 worker 真实完成的写冲突已由乐观锁+fencing 防住状态漂移，本条仅指业务层重复执行/结果丢弃），无需维度集外立案。
- **复核状态**: 已保留（P2 维持。逐行重验：scanTaskTimeouts L251-261 仅跳过"本轮新标 SUSPICIOUS"、上一轮 SUSPICIOUS 无条件进 markSuspiciousAsTimeout（且 worker 已恢复存活时 tryMarkSuspiciousIfWorkerGone L304 直接 return，无顺延/复查路径）；resolveAliveWorkerIds L281-290 非空但全 unhealthy 返回空集（非 null）→ 集群在途任务全判 worker 消失；迟到真实结果被 isConcurrentlyFinalized 丢弃（JobWorkerScannerImpl L313-315 + JobTaskStateMachine L149-155）。两轮确认窗 = 1 scan-interval（默认 5s，L106）。判级先例自洽：罕见竞态 + 后果有界（单批任务/单 fire 级、调度自身恢复、无账本增长）→ P2，与 campaign"罕见但可具体化竞态中后果无界者才入 P1"的既定校准线一致。现状：新发现）

### [nop-job-C7-004] F4（DB 侧，受 DB 约束单独标注）：nop_job_fire / nop_job_task / nop_job_task_log 三表无任何保留/归档/清理机制，且 delete 被显式阻断

- **严重度**：P2
- **缺陷族**：F4（账本只增不删）
- **位置**：
  - nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java:80-84（delete 抛错）
  - nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobTaskBizModel.java:25（delete 阻断）
  - nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobTaskLogBizModel.java:72-86（reportTaskLog 逐条落库，无上限）
  - 全模块 grep（delete/purge/cleanup/retention/ttl）无任何清理路径
- **证据片段**（NopJobFireBizModel.java:80-84）：
```java
    @Override
    public boolean delete(String id, IServiceContext context) {
        throw new NopException(ERR_JOB_FIRE_DELETE_NOT_ALLOWED)
                .param("jobFireId", id);
    }
```
（JobApiErrors.java:80-81：`"Job schedules must be archived, not directly deleted"`——但 `archiveSchedule` 只改 `scheduleStatus=ARCHIVED`，不清理任何历史 fire/task/log 行。）
- **增长/劣化链**：每个 cron/fixed-rate schedule 每次触发单调插入 1 行 `nop_job_fire` + ≥1 行 `nop_job_task`（broadcast 模式 = 健康实例数行）+ 可选 N 行 `nop_job_task_log`。时间尺度：**周-月级**（例：1 个每分钟 schedule = 52.6 万 fire 行/年，广播 10 实例 = 526 万 task 行/年）。后果：扫描器高频查询（`fetchWaitingFires`/`fetchRunningFires`/`fetchDispatchingFires`/`fetchRunningTasks`/`fetchDueSchedules` 全部按状态过滤 + order by）在表体量增大后索引深度/统计信息劣化 → 扫描延迟上升 → 5s 扫描节拍被拉长 → 调度吞吐下降；且应用层 delete 被主动阻断，运维只能绕过应用直接操作 DB。
- **判级理由**：P2——增长主体是 DB 行，受 DB 约束（可由 DBA 分区/归档），不会直接 OOM；但模块内既无 TTL 工具、又显式阻断 delete、还无归档清理 job，属于"模块自带的 F4 债务 + 强制保留策略"的组合，长时运行必然触发查询劣化（P1 边缘，按 DB 可干预性降 P2）。
- **边界声明对照**：owner doc 未声明任何保留策略或表增长包络；"禁止直接删除"是显式产品语义。
- **误报校准对照**：不适用。
- **修复方向**：提供可配置的历史清理 job（复用 `.job.yaml` 机制：按 endTime/fireStatus 保留 N 天归档删除），或在 owner doc 明确声明运维保留责任与建议的分区方案。
- **置信度**：高。
- **影响面**：所有分布式部署；本地模式无 DB 不受影响。
- **残留问题**：`reportTaskLog` 单请求 entries 列表无条数上限（逐条 insert + 每条至多 2 次快照 load）——该形态更偏资源/滥用面（C6/C8 形态），最高 P2，按 overlay 裁定不登记维度集外线索，仅在此注记。
- **复核状态**: 已保留（P2 维持。NopJobFireBizModel delete L80-84 抛错、NopJobTaskBizModel L25-26 阻断、archiveSchedule 仅改状态不清理历史行（NopJobScheduleBizModel L131-141）、reportTaskLog L72-86 无条数上限逐条落库（saveEntry + fillRedundantColumns 每条至多 2 次 load，L100-133）全部核实；模块级 grep purge/cleanup/retention/ttl 无任何清理路径。"DB 可干预性降 P2"的校准成立。历史对照：2026-05/06 各轮 nop-job 审计 summary/结论节无保留策略/表增长登记。现状：新发现）

### [nop-job-C7-005] JobScheduleCounterReconciler 固定排序窗口饥饿：活跃 schedule 数 > batch-size 时，窗口外 schedule 的计数漂移永不收敛（自愈机制自身不可达）

- **严重度**：P2
- **缺陷族**：自愈语义（reconciler 对子集失效）→ 可转化为调度停摆
- **位置**：nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobScheduleCounterReconciler.java:74-82（fetchSchedulesToReconcile）
- **证据片段**（JobScheduleCounterReconciler.java:74-82）：
```java
    private List<NopJobSchedule> fetchSchedulesToReconcile() {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopJobSchedule.PROP_NAME_scheduleStatus,
                _NopJobCoreConstants.SCHEDULE_STATUS_ENABLED));
        query.addFilter(FilterBeans.gt(NopJobSchedule.PROP_NAME_activeFireCount, 0));
        query.setLimit(batchSize);
        query.addOrderField(NopJobSchedule.PROP_NAME_jobScheduleId, false);   // 固定 desc 排序，无分页偏移
        return scheduleDao().findAllByQuery(query);
    }
```
（scanBatch 恒返回 false：每周期仅处理这一批，无 drain 循环——本类为刻意的 non-@SingleSession 例外，但同样没有翻页。）
- **增长/劣化链**：启用 `activeFireCount>0` 的 schedule 数超过 `nop.job.coordinator.reconciler.batch-size`（默认 100）时，查询按 `jobScheduleId desc` 固定取前 100 条——每 5s 周期取到的是**同一批**最高 id 的 schedule；id 排序在窗口之外的 schedule（例如漂移为 `activeFireCount` 虚高 >0 而实际无 live fire）永远进不了对账窗口。该漂移的下游后果是实质性的：`shouldDiscard/shouldOverlay/shouldRecovery` 全部以 `activeFireCount>0` 为判据（JobPlannerScannerImpl.java:273-289）——虚高卡死的 DISCARD schedule **永久停止产出 fire**（作业静默停摆），且 reconciler 注释自己承认"向下漂移到 0/负值且无 stale 正读的 schedule 无法在无全表扫描的前提下被发现"。时间尺度：漂移一旦发生在窗口外 schedule 上，**无限期不收敛**；规模化部署（>100 活跃 schedule）为前提条件。
- **判级理由**：P2——需要"漂移发生 + >batch-size 活跃 schedule"两个前提，但后果是自愈机制宣称要收敛的状态对一部分实体永久不可达，且失败形态（作业停摆）不可见（reconciler 无"窗口截断"告警——`findActiveFires` 有 limit-hit warn，本查询没有）。
- **边界声明对照**：类 javadoc 声明"漂移低频、扫描间隔节拍最终一致"——该最终一致性承诺在 >batch-size 场景下不成立，声明与实现不符。
- **误报校准对照**：非低频粗锁（每 5s 执行）；非平台设施实现。
- **修复方向**：(a) 加 `jobScheduleId < cursor` 翻页轮转（每周期推进窗口）；(b) 结果达到 limit 时 warn（对齐 findActiveFires 的 limit-hit 先例）；(c) 或按 `updateTime` 淘汰近期已对账项。
- **置信度**：高。
- **影响面**：多 schedule（>100 活跃）的 coordinator 部署。
- **残留问题**：无。
- **复核状态**: 已保留（P2 维持。fetchSchedulesToReconcile L74-82 固定 jobScheduleId desc + limit=batchSize（默认 100，L49）+ 无翻页、scanBatch 恒 false（L71）、本查询无 limit-hit warn（对照 findActiveFires 有，JobScheduleStoreImpl L381-384）——防护不对称核实。反驳面关闭：已修正 schedule 跌出 gt(0) 集会使窗口下移，但"真忙（actual>0）schedule 持久占满窗口"时窗口外漂移行永不入窗——恰为本条前提（活跃 schedule 数 > batch-size），饥饿链成立；类 javadoc L32"最终一致"承诺在 >batch-size 场景不成立核实。reconciler 为 2026-08-13 plan 340（commit 3365534007）新增，晚于历史审计。现状：新发现）

### [nop-job-C7-006] LocalJobScheduler 单次执行异常即永久 FAILED：本地模式失败无重试/无下次调度/无自愈

- **严重度**：P2
- **缺陷族**：自愈语义（半个失败无恢复）
- **位置**：
  - nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:348-356（handleResult 错误分支）、281-290（scheduleNext 触发器计算异常同判 FAILED）
  - nop-job/nop-job-local/src/main/java/io/nop/job/local/executor/BeanMethodJobInvoker.java:67-72（方法异常包装为 JobFireResult.ERROR）
- **证据片段**（LocalJobScheduler.java:348-356）：
```java
    private void handleResult(ScheduledJob job, JobFireResult result, Throwable err) {
        job.state.fireCount++;
        job.state.lastEndTime = currentTime();

        if (err != null) {
            LOG.error("nop.job.execute-failed:jobName={}", job.spec.getJobName(), err);
            job.state.internal = InternalState.FAILED;
            return;          // 不 scheduleNext：周期型 job 也就此终止
        }
```
（BeanMethodJobInvoker.java:67-72：反射调用异常 → `JobFireResult.ERROR(...)` → handleResult 的 `result.isErrorResult()` 分支同样置 FAILED 终局。）
- **增长/劣化链**：YAML/编程式本地模式的周期型 job（如每 5s 的事件批量消费 job `sys-event-batch-consumer`）某次执行抛出瞬时异常（DB 抖动、下游超时、锁冲突——周级运行概率≈1）→ job 置 FAILED 且不再排程 → 该作业从此静默停摆；唯一痕迹是一条 ERROR 日志和 `getJobState()=FAILED`。无重试、无 misfire 补偿、无自动恢复；`resumeJob` 也只对 SUSPENDED 生效（FAILED 态返回 false），恢复只能 `fireNow`（FAILED 态同样返回 false）或重启进程重建。时间尺度：**单次瞬时故障 → 永久停摆**（直到重启）。
- **判级理由**：P2——本地模式定位为轻量嵌入式（owner doc："作业定义只存在于内存，重启即丢失"），单次失败停摆非显式声明边界但状态可查；对启用了平台内置 `.job.yaml` 的应用（nop-batch-sys、nop-wf-scheduler 均有内置本地 job）这是长时运行必然踩到的语义，值得立案警示，但不构成分布式侧的系统性劣化。
- **边界声明对照**：owner doc 声明本地模式"不支持 Misfire 补偿、阻塞策略、超时控制"，未声明"单次执行失败 = 作业永久终止"这一（对用户反直觉的）语义。
- **误报校准对照**：非低频粗锁；非平台设施实现；非测试专用（YAML 模式是 owner doc 推荐用法）。
- **修复方向**：(a) FAILED 后按 trigger 仍计算下次时间但带退避（failedCount++），或 (b) 至少在 owner doc 显式声明该语义并提示用户在 invoker 内自行兜底异常。
- **置信度**：高。
- **影响面**：nop-job-core/nop-job-local（本地模式），含平台内置 job（nop-batch-sys 事件消费、nop-wf-scheduler 到期任务扫描——若以本地模式启用）。
- **残留问题**：无。
- **复核状态**: 已保留（P2 维持。handleResult err→FAILED+return 不再排程（L352-356）、isErrorResult→FAILED（L358-362）、scheduleNext 触发器异常→FAILED（L284-290）、BeanMethodJobInvoker 异常包装 JobFireResult.ERROR（L55-71）、resumeJob 仅 SUSPENDED 生效（L161-176）、fireNow 对 FAILED 返回 false（L215-218）全部核实。历史注记：72b770b094（2026-08-23）曾修相邻触发器异常路径（原逃逸被吞卡 RUNNING → 置 FAILED）——该修复恰使异常路径落入本条所指的 FAILED 终局语义，"单次失败=永久停摆"语义本身无历史登记。本地模式轻量定位 + 状态可查，P2 恰当。现状：新发现）

### [nop-job-C7-007] 扫描器线程阻塞无 stall 检测：DB 查询无限期挂起时全部扫描器静默停摆且零日志

- **严重度**：P2
- **缺陷族**：F5（不产出也不报错路径无看门狗）
- **位置**：
  - nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java:58-80（startScanning/doScan）
  - nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCoordinator.java:52-72（doStart 仅 startScanning，无健康/心跳暴露）
- **证据片段**（AbstractBatchScanner.java:58-80）：
```java
    public synchronized void startScanning() {
        if (running) {
            return;
        }
        running = true;
        scanFuture = getExecutor().scheduleWithFixedDelay(
                this::doScan, 0, scanIntervalMs, TimeUnit.MILLISECONDS);
    }
    ...
    protected void doScan() {
        if (!running) {
            return;
        }
        scanOnce();      // scanBatch 内的 JDBC 查询若无限期阻塞，下一轮永不开始、无任何信号
    }
```
- **增长/劣化链**：`scanBatch()` 内的 ORM/JDBC 调用在 DB 网络半开（连接未设 socket/query timeout——平台连接池配置职责）时无限期阻塞 → 该扫描器的 fixed-delay 链**永久停在同一轮**：不再扫描、不再创建 fire、不抛错、无日志（`onScanFailed` 只覆盖"抛异常"，不覆盖"挂起"）。planner 挂死 = 全平台调度停摆；timeout checker 挂死 = liveness/超时回收链停摆（与 planner 挂死叠加时连"回收 DB 滞留行"的自愈也不再发生）。模块内无扫描心跳/last-scan-timestamp gauge/stall 告警，健康检测完全不可见。时间尺度：挂起持续多久停摆多久（小时-天级，直到 DB 侧 TCP 死亡或进程重启）。
- **判级理由**：P2——触发前提是平台层 JDBC 超时配置缺失（设施使用方式问题，模块边界内判定为配置依赖），但"不产出也不报错"的盲区本身在模块内无任何缓解，属 F5 监控盲区。不到 P1：JDBC 驱动默认通常有 TCP 层超时兜底（依驱动而异），非包络内必然。
- **边界声明对照**：owner doc / concurrency-and-transactions.md 声明了乐观锁与事务隔离模式，未声明扫描器 stall 可观测性缺口。
- **误报校准对照**：非平台设施自身实现（AbstractBatchScanner 是 nop-job 模块代码，仅依赖平台 executor）。
- **修复方向**：每扫描器暴露 `lastSuccessfulScanTime` gauge（scanOnce 出口更新），或将 scanBatch 包一层可配置的看门狗超时；文档建议强制配置 JDBC socket/query timeout。
- **置信度**：中高（阻塞形态依赖平台/驱动配置，模块内代码路径确凿）。
- **影响面**：全部 5 个扫描器（planner/dispatcher/completion/timeout/worker/reconciler 共用基类）。
- **残留问题**：GlobalExecutors.globalTimer 的线程模型（是否单线程、一个 scanner 挂死是否拖垮其他 scanner/平台其他定时任务）属 C4 线程池维度，不在本条展开。
- **复核状态**: 已保留（P2 维持。startScanning/doScan L58-80 fixed-delay 链、scanOnce 仅 catch Exception→onScanFailed（L93-102）——"挂起"形态无任何信号；模块内无 last-scan-timestamp gauge/stall 告警。触发前提（JDBC 无 socket/query 超时 + DB 半开）为平台配置面、模块内确无缓解，F5 监控盲区定性准确；"JDBC 驱动通常有 TCP 层兜底、非包络内必然"支撑不到 P1 的校准成立。现状：新发现）

### [nop-job-C7-008] RpcPollTaskManager 轮询期限使用 JVM 墙钟，与协调器其余期限判定使用的 DB 估计时钟不一致

- **严重度**：P3
- **缺陷族**：时钟与期限（C7 子项 5）
- **位置**：
  - nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcPollTaskManager.java:182、259-265（`CoreMetrics.currentTimeMillis()`）
  - 对照：JobScheduleStoreImpl.java:334-336 / JobFireStoreImpl.java:61,91 等（`getDbEstimatedClock().getMaxCurrentTimeMillis()`）
- **证据片段**（RpcPollTaskManager.java:181-186 与 259-263）：
```java
            long now = CoreMetrics.currentTimeMillis();
            if (entry.getDeadline() > 0 && now >= entry.getDeadline()) {
                // 超时：段 3 取消 + resolve ERROR（timeout）
                cancelRemote(entry);
                entry.getFuture().complete(JobFireResult.ERROR(error(ERR_JOB_TIMEOUT)));
```
```java
        if (timeoutSeconds != null && timeoutSeconds > 0) {
            return CoreMetrics.currentTimeMillis() + timeoutSeconds * 1000L;   // 注册时取 JVM 墙钟
        }
```
- **增长/劣化链**：deadline 于 register 时以 JVM 墙钟冻结、pollOnce 以 JVM 墙钟比较，而 task 的 DB 侧超时判定（JobTimeoutCheckerImpl.tryMarkTimeout）用 DB 估计时钟。两源漂移：JVM 时钟回拨 D 毫秒 → rpcPoll 条目墙钟超时顺延 D（DB 侧可能已先 TIMEOUT——条目多存活一段时间并继续无效轮询）；JVM 时钟前跳 → 提前超时误杀。量级与时钟同步方案相关（NTP 步进通常 <1s，故障场景可达分钟-小时）。无累积增长，单次事件级。
- **判级理由**：P3——理论风险、未具体化为单调增长；与 C7-001 的"deadline=0 永不超时"相比是次要不一致，时钟回拨下的行为只是期限偏移而非死锁。
- **边界声明对照**：未声明；owner doc 未提及时钟源约定。
- **误报校准对照**：不适用。
- **修复方向**：与 DB 侧一致改用 `scheduleStore.getCurrentTime()`（RemoteJobInvoker 已注入 scheduleStore，可透传）。
- **置信度**：高（不一致本身确凿；影响幅度依部署时钟管理而定）。
- **影响面**：rpcPoll 模式。
- **残留问题**：LocalJobScheduler 同用 JVM 墙钟（LocalJobScheduler.java:395-397）——单机场景无跨源比对，回拨仅导致延迟触发/前跳触发一次 catch-up，不立案。
- **复核状态**: 已保留（P3 维持。RpcPollTaskManager L182/L262 用 CoreMetrics.currentTimeMillis（JVM 墙钟），DB 侧 tryMarkTimeout 用 scheduleStore.getCurrentTime()（JobTimeoutCheckerImpl L504；JobScheduleStoreImpl L334-336 / JobFireStoreImpl L61,91 均 getDbEstimatedClock）——双源不一致确凿；LocalJobScheduler L395-397 同 JVM 墙钟且单机无跨源比对、不立案的残留口径维持。理论风险/事件级/无增长，P3 恰当。现状：新发现（随 rpcPoll 特性 2026-08-28 引入））

### [维度集外线索] 无

本维度审计过程中识别的 C3/C6/C8/C9 形态线索均不高于 P2，按 plan 2464 裁定不登记：
- `NopJobTaskLogBizModel.reportTaskLog`（NopJobTaskLogBizModel.java:73-86）批量端点对 entries 列表无条数上限 + 逐条 insert（C6/C8 形态，P2）
- `JobFireStoreImpl.findTasksByFireId` / `JobScheduleStoreImpl.resetFailedTasks`/`cancelTasks` 无 LIMIT 全量加载（C5 形态，但 fire 的 task 数有 dispatch 期有界来源注释声明——"有界声明结构"，不立案）
- `NopJobApplication.globalArgs` static 非终态字段（C1 形态，demo app 启动代码，测试/示例专用，不立案）

---

## 覆盖度与残留边界

**已覆盖**（精读）：AbstractBatchScanner、LocalJobScheduler、LocalJobConfigLoader、BeanMethodJobInvoker、JobCoordinator、JobTimeoutCheckerImpl、JobPlannerScannerImpl、JobDispatcherScannerImpl、JobCompletionProcessorImpl、JobScheduleCounterReconciler、JobFireStoreImpl、JobScheduleStoreImpl、JobTaskStoreImpl、DefaultJobCancelHandler、DefaultWorkerLoadProvider、RemoteJobInvoker、RpcPollTaskManager、DefaultRpcPollTaskClient、JobWorker、JobWorkerScannerImpl、DefaultJobExecutionContextBuilder、DefaultJobInvokerResolver、RpcJobInvoker、JobLogReporter、NopJobFireBizModel、NopJobTaskLogBizModel、NopRetryJobRetryBridge、全部 4 个 MetricsImpl、核心 trigger 链（Cron/Periodic/Once/HandleMisfire/AnchorRepeat/LimitTime/PauseCalendar/CheckActive/JobTriggerCalculator）。抽查：AbstractServiceTaskBuilder、state machine helper、calendar 类、_vfs beans 装配（nopRpcPollScheduledExecutor 池大小）。grep 全量：时钟源、catch 吞异常、delete/清理路径、scheduled/timer/metrics 基线 C7 supplement 节逐条核对。

**正面结论（无发现）**：
1. metrics 全部固定名零标签，无标签基数增长（G6）。
2. 每周期临时对象均短生命周期（Cursor 每周期 reset、registeredCursors 固定），无每次调用累积（G7/G8）。
3. misfire/catch-up 无补偿风暴：PeriodicTrigger 固定速率只补一个网格槽（n=(afterTime-start)/period+1），HandleMisfireTrigger 丢弃超阈值迟到 once 任务；trigger 循环全部有上限（MAX_SCAN=36600、MAX_TRY_COUNT=10000）。
4. no-fitting-worker 有 backoff 窗口（默认 30s）；dispatch 失败 per-fire 隔离且失败后 fire 留 DISPATCHING 不重入（无紧循环）；重试委托 nop-retry 策略引擎（平台设施，退避/上限归其策略配置管，本单元用法正确：幂等 id=fireId、仅 SCHEDULE 来源可重试）。
5. fencing/旧租约：双 coordinator 由 planner 乐观锁（nextFireTime 推进为锁 deadline）+ 版本检查防护；双 worker 由 CAS 认领 + AR-85 CLAIMED→RUNNING CAS 校验防重复执行；zombie worker 迟到结果被 SUSPICIOUS/concurrently-finalized fencing 阻断；进程重启后无内存租约需要清理（状态权威在 DB），CLAIMED 滞留已由认领时写 startTime 修复纳入 liveness 链。
6. activeFireCount 漂移有 reconciler 兜底（但其窗口饥饿见 C7-005）。

**残留边界/未覆盖**：
1. `CronExpression`（约千行 Quartz 衍生计算）按 vendored/纯函数对待，只验证了入口时钟语义与 loop 上限，未逐分支审计时区/DST 行为（对长时运行的影响限于触发时刻偏移，非增长/挂死）。
2. `IRpcServiceInvoker`/`IScheduledExecutor`/`GlobalExecutors`/`getDbEstimatedClock`/JDBC 超时为平台设施，按使用方式审计，其自身实现归平台各单元（C7-007 的触发前提之一在其配置面）。
3. nop-job-app（示例应用）与全部 src/test 未审（按任务边界排除；NopE2eTestJobInvoker 为 e2e 专用 invoker，行为无害已抽查）。
4. C7-001 修复后的行为需 rpcPoll 集成测试覆盖（当前 src/main 无 entries.size 暴露，无法从外部观测泄漏增长）。
5. ORM 模型（model/nop-job.orm.xml）的索引设计决定 C7-004 的实际劣化速率，属 ORM 模型维度交叉项，本报告未展开。

---

## 维度复核结论（独立复核子代理）

- 复核子代理：C7 维度独立复核（与初审者不同角色，仅做逐条验证/保留/降级/驳回与现状标注，不补充新发现）
- 复核日期：2026-10-15
- 复核仓库：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation，复核基线 commit 335e77caec）
- 复核方法：12 条发现逐条重读 live code 验证（文件/行号、证据片段、增长/劣化链、判级口径），每条构造反驳面并关闭；历史对照仅读 ai-dev/audits/ 下 nop-job 历史审计目录（2026-05-18/06-03/06-04/06-19 各轮）summary/结论节、cra-index.md 与 git log。C7-001 按指令与 C4-001/C5-001 复核声明的三视角归并方向对齐（三方均 P1 自洽）；C7-010 按指令从严验证 CAS 时序与 UK 不拦截链路；C7-011 按指令验证 isRunning 谓词排除与认领 javadoc 矛盾。

### 逐条判定表

| 发现编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---|---|---|---|---|
| C7-001 | P1 | **保留** | 四终结条件中三个在默认装配下失效、DB 终态→future 无回灌通道、cancelToken 恒 null 全部逐行重验成立；与 C4-001/C5-001 同机制三视角归并方向一致，同向保留 P1 | 新发现（rpcPoll 特性 2026-08-28 commit 5310efaf97 引入，晚于全部历史审计；历史 P1 修复 72b770b094 系反方向且已修） |
| C7-002 | P2 | **保留** | promise 无超时包装 + 无 in-flight 年龄 metric + 无墙钟兜底全部核实，滞留零可观测性成立；触发前提为外部 invoker 缺陷且不超时语义已声明，P2 恰当 | 新发现（交叉注记：与同 campaign C4-009 同根因异维度，7.1 可归并） |
| C7-003 | P2 | **保留** | SUSPICIOUS 单向棘轮（恢复后仍无条件转 TIMEOUT）+ 全不健康空集放大 + 迟到结果丢弃逐行核实；罕见竞态 + 后果有界 → P2 与 campaign 校准线一致 | 新发现 |
| C7-004 | P2 | **保留** | 三表 delete 显式阻断 + archive 仅改状态 + 全模块无任何清理路径核实；DB 可干预性降 P2 校准成立 | 新发现（历史 2026-05/06 各轮无保留策略登记） |
| C7-005 | P2 | **保留** | 固定 desc 排序 + limit=batchSize + 无翻页 + 无 limit-hit warn 核实；反驳面（修正后跌出窗口使窗口下移）被本条前提（>batch-size 活跃 schedule）覆盖，饥饿链成立 | 新发现（reconciler 2026-08-13 commit 3365534007 新增） |
| C7-006 | P2 | **保留** | 单次执行异常/ERROR 结果/触发器异常三路均置 FAILED 且 resumeJob/fireNow 均不生效核实；历史修复 72b770b094 恰使触发器异常路径落入此终局语义，停摆语义本身无历史登记 | 新发现 |
| C7-007 | P2 | **保留** | fixed-delay 链对"挂起"（区别于抛异常）零信号、模块内无 stall gauge 核实；触发前提为平台 JDBC 配置面，F5 监控盲区定性准确 | 新发现 |
| C7-008 | P3 | **保留** | JVM 墙钟 vs DB 估计时钟双源不一致确凿（RpcPollTaskManager L182/L262 vs JobTimeoutCheckerImpl L504）；事件级无增长，P3 恰当 | 新发现（随 rpcPoll 特性引入） |
| C7-009 | P2 | **保留** | completion 5s 无退避环（REQUIRES_NEW 回滚→fire 永久 RUNNING→重捞）+ planner 60s 环 + FIXED_DELAY 仅完成时构建触发器 + 触发器校验仅 enable/resume 存在，全链逐点核实；stuck fire 有界不判 P1 论证成立 | 新发现 |
| C7-010 | P2 | **保留**（从严验证通过） | fence=版本 CAS TTL 过期后半程无保护、hasWaitingFire 精确匹配不同时间戳不命中、UK 三列含 scheduledFireTime 对双 fire 不构成约束、saveEntityDirectly 前 zero post-fence 复查、A 计数经 updateWithRetry 重载重试存活——重复执行链逐步重构成立；后果限单槽双执行，P2 与 C7-003 先例一致 | 新发现 |
| C7-011 | P2 | **保留** | isRunning 门排除 CLAIMED（L470-472）与认领 javadoc"或执行超时配置"（JobTaskStoreImpl L88-91）逐字矛盾核实；四条回收链逐一排除 + 额度占用（IN_FLIGHT/RESERVED 双集含 CLAIMED）+ 仓库内注释自证（JobWorkerScannerImpl L231-233）成立 | 新发现（附注：in-code 注释自认"属另一课题"属代码内已知未修，但无历史发现编号，按口径标新发现；AR-88 系 WAITING 反方向已修） |
| C7-012 | P3 | **保留** | 两 WARN 站点（resolve-workers-failed 每批 / report-failed 按业务速率）无采样核实；速率上界明确无结构性后果，P3 恰当 | 新发现 |

### 不确定项（显式列出）

1. **C7-001 残留**：`IRpcServiceInvoker` 底层（ClusterRpcServiceInvoker/HttpRpcServiceInvoker）是否存在"连接级快速失败以外的无限挂起形态"未验证（平台设施边界，初审已声明；若存在仅降低 WARN 频率，不改变泄漏结论）。
2. **C7-002 残留**：whenComplete 回调线程被杀导致真实结果丢失的形态未验证，初审已声明归 C4 交叉确认；本复核不展开。
3. **C7-006 影响面**：nop-batch-sys/nop-wf-scheduler 内置 job 当前是否以本地模式启用未逐一核实（不影响判级与停摆语义本身）。
4. **C7-011 前提 (1)**：AppConfig.hostId 重启 identity 稳定性依赖平台 config provider 持久化形态（平台设施边界，初审已作前提注记；场景 (2) namingService 未接线不受影响，结论主体不变）。
5. **合并裁定注记**（非不确定，供 7.1 汇总裁定）：C7-001 与 C4-001/C5-001 为同一机制三视角（三方复核均 P1，合并时应单立案）；C7-002 与 C4-009 为同根因异维度对（C4 复核未声明归并，本复核补记方向供 7.1 参考）。

---

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|---|---|---|---|---|
| C7-001 | P1 | nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcPollTaskManager.java | rpcPoll 条目无 DB 终态联动：远端失联后 entry+调度句柄永久泄漏、5s 无退避重试风暴、WARN 日志放大（与 C4-001/C5-001 同机制） | 新发现 |
| C7-002 | P2 | nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java | invoker promise 永不完成时 task/fire 永久滞留 RUNNING、DISCARD 调度停摆，且无任何 RUNNING 时长可观测出口 | 新发现 |
| C7-003 | P2 | nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java | SUSPICIOUS 单向棘轮不复查存活 + 注册表全不健康空集放大：瞬态健康抖动误杀在途任务并丢弃迟到真实结果 | 新发现 |
| C7-004 | P2 | nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java | nop_job_fire/task/task_log 三表无保留/归档/清理机制且 delete 被显式阻断：DB 账本单调增长 + 扫描查询劣化 | 新发现 |
| C7-005 | P2 | nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobScheduleCounterReconciler.java | reconciler 固定排序窗口饥饿：活跃 schedule > batch-size 时窗口外计数漂移永不收敛（DISCARD 停摆不可见） | 新发现 |
| C7-006 | P2 | nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java | 本地模式单次执行异常即永久 FAILED：无重试/无下次调度，resumeJob/fireNow 均不生效，作业静默停摆 | 新发现 |
| C7-007 | P2 | nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java | 扫描器线程阻塞在 DB 查询时 fixed-delay 链静默停摆：无 stall 检测/心跳/日志，调度与自愈链同时失效 | 新发现 |
| C7-008 | P3 | nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcPollTaskManager.java | rpcPoll 轮询期限用 JVM 墙钟，与协调器其余期限判定的 DB 估计时钟不一致（时钟回拨/前跳期限偏移） | 新发现 |
| C7-009 | P2 | nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java | 触发器计算持久失败无隔离：completion 侧 5s 无退避重试环 + fire 永久滞留 RUNNING；planner 侧 60s 环 + 调度静默停摆 | 新发现 |
| C7-010 | P2 | nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java | planner 乐观锁 fence 过期窗口：滞后 planner 插入重复 fire，hasWaitingFire 精确匹配与三列 UK 均不拦——同一触发槽双执行 | 新发现 |
| C7-011 | P2 | nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java | CLAIMED 态零回收：liveness 要求 worker 消失、执行超时被 isRunning 门排除（与认领 javadoc 直接矛盾）、stale 重置仅 WAITING——fire 永久 RUNNING + 并发/资源额度永久占用 | 新发现（in-code 注释自认缺口但无历史编号） |
| C7-012 | P3 | nop-job/nop-job-api/src/main/java/io/nop/job/api/log/JobLogReporter.java | 循环路径无采样/无抑制 WARN 站点残留（resolve-workers-failed 每批、report-failed 按业务速率）：日志洪泛淹没真实告警 | 新发现 |

**复核计数**：保留 12（P1×1 + P2×9 + P3×2）；降级 0；驳回 0。全部 12 条与初审判级一致，无判级调整。

**需子项复核的高风险项清单**：无（无驳回、无判级争议；C7-001 三视角归并与 C7-002↔C4-009 同根因归并属 7.1 汇总裁定事项，非子项复核事项）。
