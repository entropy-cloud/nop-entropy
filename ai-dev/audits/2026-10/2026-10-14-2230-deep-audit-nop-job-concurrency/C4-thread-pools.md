# nop-job 并发审计 — C4 线程池与执行器治理（第 1 轮盲审）

- 审计单元：`nop-job` 聚合器（12 个 jar 子模块，src/main/java 手写代码 164 files / 17,664 lines）
- 仓库根：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation）
- 审计维度：C4 线程池与执行器治理（配置有界 / 关闭正确 / 任务隔离 / 定时任务堆积策略 / F8 生命周期对称 / F9 吞异常停摆感知）
- 盲审纪律：未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件；全部结论基于当前 live code
- 基线：`_tmp/cra-baseline-nop-job.txt` C4 节（2 个使用点）为起点，人工全量 grep 补充核对（`new Thread|ExecutorService|Executors\.|ThreadPoolExecutor|IThreadPoolExecutor|ThreadPoolConfig|GlobalExecutors|commonPool|ForkJoin|ScheduledExecutorService|IScheduledExecutor|executeOn|scheduleAtFixedRate|scheduleWithFixedDelay|shutdownNow|awaitTermination|@PreDestroy`、`then*Async|supplyAsync|runAsync`）

## 检查范围说明

- 覆盖 12 个子模块全部 `src/main/java`（`_` 前缀生成文件不作为发现对象）；测试代码不在审计面。
- 平台并发设施（`io.nop.commons.concurrent.executor.*`：DefaultScheduledExecutor / DefaultThreadPoolExecutor / BindScheduledExecutor / GlobalExecutors / ThreadPoolConfig / ExecutorHelper）**只审 nop-job 的使用方式**；为实现引用其关键语义（如 `newScheduledExecutor` 强制 `maxPoolSize=0`、`shutdownNow` 无 `awaitTermination`）以支撑使用层判定，不对平台实现本身立案。
- 已声明边界不作为缺陷：本地模式定位为"单机嵌入式、作业定义重启即丢失"（owner doc `docs-for-ai/03-modules/nop-job.md` §1.4）；rpcPoll"轮询直至终态"的 declared 语义；`@SingleSession` 事务回滚语义。
- 排除误报面：低频路径粗粒度锁、有界声明池、平台设施自身实现、测试专用代码（`NopE2eTestJobInvoker` 为生产 beans 中显式注册的 e2e 占位 invoker，无线程行为，不立案）。

## 线程池清单表（池 × 参数 × 队列 × 拒绝策略 × 关闭路径 × 判定）

全量盘点结论：nop-job src/main **无直接 `new Thread`、无 ForkJoin/commonPool 直接或间接使用**（无 `supplyAsync`/`then*Async` 无参重载）。线程创建点收敛为 4 个池 + 2 个生命周期入口。

| # | 池 / 线程来源 | 声明位置 | 类型 | 核心/最大 | 队列 | 拒绝策略 | 线程命名 | 关闭路径 | 判定 |
|---|---|---|---|---|---|---|---|---|---|
| P1 | `nopJobScheduledExecutor`（本地模式调度+执行池） | `nop-job/nop-job-local/src/main/resources/_vfs/nop/job/beans/app-local-scheduler.beans.xml:7-17` | `DefaultScheduledExecutor` → JDK `ScheduledThreadPoolExecutor`（`ExecutorHelper.newScheduledExecutor`，maxPoolSize 被强制置 0） | 4 / 4（`nop.job.local-scheduler.pool-size`，core=max 由 beans 显式对齐） | DelayedWorkQueue **无界**（调度池固有不拒绝） | n/a（submit 永不入队拒绝） | `nop-job-local-scheduler-N`（NamedThreadFactory） | bean `destroy-method="destroy"` → `shutdownNow()`（无 awaitTermination，见 C4-006） | 调度与 job 执行同池无隔离（C4-002） |
| P2 | `nopRpcPollScheduledExecutor`（rpcPoll 轮询专用池） | `nop-job/nop-job-coordinator/src/main/resources/_vfs/nop/job/beans/app-engine.beans.xml:86-97` | 同上 | 2 / 2（`nop.job.remote.poll-threads`） | DelayedWorkQueue **无界**；每在途 rpcPoll 任务占 1 个周期任务句柄 | n/a | `nop-job-rpcPoll-N` | bean `destroy-method="destroy"` → `shutdownNow()` | 条目终结依赖 future 完成；错误路径可永久滞留（C4-001、C4-004、C4-008） |
| P3 | 平台 `GlobalExecutors.globalTimer()`（fallback + 6 个 scanner 的定时器） | `AbstractBatchScanner.getExecutor()`（`nop-job-core/.../AbstractBatchScanner.java:134-136`）；`RpcPollTaskManager.scheduledExecutor()`（`RpcPollTaskManager.java:170-173`） | `DefaultScheduledExecutor` → STPE | core 4 / max 5（`nop.commons.concurrent.global-timer.maxPoolSize` 默认 5），daemon | DelayedWorkQueue 无界 | n/a | `nop-global-timer-N` | 平台全局实例，容器关闭**不销毁** | scanner 经 `executeOn` 只借其定时触发；fallback 路径句柄挂永不销毁池（C4-008） |
| P4 | 平台 `GlobalExecutors.globalWorker()`（全部 scanBatch 执行面 + 本地 job 同步回调执行面） | `AbstractBatchScanner.java:134-136`；`LocalJobScheduler` 经 Bind 间接 | `DefaultThreadPoolExecutor` → `StandardThreadPoolExecutor` | core 4 / max 30（`global-worker.maxPoolSize` 默认 30），daemon | `TaskQueue(10000)` **有界**（ThreadPoolConfig 默认 queueCapacity） | JDK 默认 AbortPolicy（30 线程满 + 10000 队列满才拒绝） | `nop-global-worker-N` | 平台全局实例，不销毁 | 跨业务共享无隔离（C4-005）；拒绝→scanner 链死（C4-003） |
| T1 | JobCoordinator/JobWorker 扫描生命周期 | `JobCoordinator.java:52-112`、`JobWorker.java:14-27` | `LifeCycleSupport`（非线程） | — | — | — | — | doStart/doStop 逐组件对称 + per-component try/catch | 正常（F8 对称）；`cancel(false)` 不等在途批次（C4-006） |
| T2 | `LocalJobConfigLoader.@PreDestroy destroy()` | `nop-job-local/.../LocalJobConfigLoader.java:222-227` | 非线程 | — | — | — | — | `scheduler.deactivate()` 逐 job cancelScheduledFire | 正常；`cancelRunning` 为有注释的 no-op（声明边界） |

定时任务盘点（period 与执行时长关系 / F9）：6 个 scanner（Planner/Dispatcher/Completion/Timeout/Worker/CounterReconciler）全部经 `AbstractBatchScanner.startScanning()` → `BindScheduledExecutor.scheduleWithFixedDelay`（**手工重排链**：worker 池上一轮 `command.run()` 完成后才重排下一轮 timer tick——无重叠、无堆积，period 语义正确，见 `BindScheduledExecutor.java:88-116`）；`RpcPollTaskManager.register` 为每 entry 独立 JDK `scheduleWithFixedDelay`（`RpcPollTaskManager.java:119-120`，fixed-delay 单 entry 不重叠）。两处定时面的 F9 暴露见 C4-003 / C4-004。

## 发现条目

### [nop-job-C4-001] rpcPoll 轮询条目在远程端永久不可达且未配置 deadline 时永不终结（错误路径资源泄漏 + 定时任务累积）

1. **发现编号**：nop-job-C4-001
2. **严重度**：P1（错误/取消路径泄漏；长时运行必然劣化）
3. **类别**：C4-2/3/5（池任务生命周期 + 关闭/回收路径 + 定时任务治理）
4. **缺陷定位**：`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcPollTaskManager.java:175-207`（pollOnce 错误分支）、`:259-265`（computeDeadline）、`:119-128`（注册与句柄）；旁证 `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RemoteJobInvoker.java:100-113`（唯一终结源是 future）
5. **证据片段**：
    ```java
    // RpcPollTaskManager.java:195-203
    rpcPollTaskClient.getJobStatus(entry.getSchedule(), entry.getFire(), entry.getTask(), entry.getCancelToken())
            .whenComplete((status, err) -> {
                if (err != null) {
                    // 瞬态失败（网络等）：本轮忽略，下轮重查；连续失败由 TimeoutChecker 墙钟兜底
                    LOG.warn("nop.job.remote.poll-failed:taskId={}", entry.getTaskId(), err);
                    return;
                }
                applyStatus(entry, status);
            });
    ```
    ```java
    // RpcPollTaskManager.java:259-265 —— schedule.timeoutSeconds 未配置（execution-timeout-ms 默认 -1）时 deadline=0
    private static long computeDeadline(NopJobSchedule schedule) {
        Integer timeoutSeconds = schedule != null ? schedule.getTimeoutSeconds() : null;
        if (timeoutSeconds != null && timeoutSeconds > 0) {
            return CoreMetrics.currentTimeMillis() + timeoutSeconds * 1000L;
        }
        return 0L;
    }
    ```
6. **缺陷描述**：entry 的终结条件仅有四个：future 终态 resolve、deadline 到期、cancelToken 取消、future.whenComplete。其中 deadline 仅在 `schedule.timeoutSeconds>0` 时存在（全局 `execution-timeout-ms` 默认 -1 不参与——`computeDeadline` 只看 schedule 行）。`pollOnce` 对 RPC 失败的处理是**无失败预算**的 warn-and-retry：注释声称"连续失败由 TimeoutChecker 墙钟兜底"，但 TimeoutChecker 作用于 **DB 状态机**（CLAIMED/RUNNING→SUSPICIOUS→TIMEOUT），而该注释明确"管理器驱动轮询只看本地持有的 in-memory 信号……不查 DB"（`RpcPollTaskManager.java:56-57`）——DB 侧把 task 判 TIMEOUT 后，**没有任何机制把该终态回传给 in-memory future**。`DefaultJobCancelHandler.cancelRunningTask` 也只调 `invoker.cancelAsync`（返回 boolean，不 complete future，见 `DefaultJobCancelHandler.java:35-49`）。
7. **触发时序**：(a) schedule A 以 `executorKind=rpcPoll` 派发，未配置 `timeoutSeconds`（平台默认不超时语义）；(b) worker 进程崩溃或与 coordinator 网络分区持续存在；(c) 每 5s 的 getJobStatus 全部失败 → `pollOnce` warn 后 return，future 永不完成、deadline=0、无人取消；(d) DB 侧 TimeoutChecker 已把 task 置 TIMEOUT、fire 聚合完结，调度系统恢复正常流转；(e) 该 entry + JDK 周期句柄 + 每 5s 一次的失败 RPC 尝试 + WARN 日志**永久留存**，直至进程重启。每次 worker 崩溃/长分区泄漏一个 entry，累积速率与故障频度成正比。
8. **影响面**：`entries` 注册表与 `nopRpcPollScheduledExecutor` 的 DelayedWorkQueue 缓慢增长（每 entry 约 1 个 PollEntry 对象 + 1 个周期任务句柄）；死 endpoint 每 5s 触发一次真实网络尝试与 WARN 日志（日志洪水放大）；轮询池节拍被无效 tick 摊薄。长时运行（月级）+ 反复滚动发布/网络抖动场景必然劣化。
9. **缺陷族对照**：F8（池任务生命周期与持有者生命周期不对称——future 的终结路径未覆盖"DB 已终态而 RPC 永久失败"分支）；F9 邻接（停止/空转不被感知——本例是"永不停止"形态）。
10. **误报校准考量**：非"已声明边界"——declared 语义是"轮询直至终态/取消/墙钟超时"，而错误路径使三个终结条件全部失效且 DB 兜底不可达；非低频粗锁或有界声明池。已排除正常路径（worker 存活→终态 resolve；worker 重启→NOT_FOUND→REMOTE_TASK_LOST resolve；配置 timeoutSeconds→deadline 兜底）。
11. **修复建议**（候选，修复不在本轮范围）：(a) `pollOnce` 增加连续失败预算（超过阈值后 resolve ERROR(REMOTE_INVOKE_FAILED) 或主动回查 DB task 状态）；(b) 或在 deadline=0 时给默认墙钟上限（如 `nop.job.remote.poll-max-duration-ms`）；(c) 最小方案：连续失败超阈值时降频轮询（指数退避）并暴露 `activeCount` 指标告警。
12. **置信度**：高。终结路径枚举完整（future/deadline/cancelToken/whenComplete 四条），无第五条回传通道。
13. **残留边界**：`timeoutSeconds` 已配置的 schedule 不受此影响（deadline 兜底）；endpoint 返回 NOT_FOUND 的重启场景正常终结；`JobLogReporter` 的 best-effort 上报属声明边界，不在本条范围。

**复核状态**：已保留（P1 维持。全链逐点核实：pollOnce 错误分支 L195-203 无失败预算 warn-and-return、computeDeadline L259-265 仅读 schedule.timeoutSeconds（全局 execution-timeout-ms 默认 -1 不参与，JobTimeoutCheckerImpl.java:53）、DB 终态无回传通道（DefaultJobCancelHandler.java:35-49 仅调 invoker.cancelAsync 不 complete future；RpcPollTaskManager L56-57 明示"不查 DB"）、cancelToken 内建装配恒 null（C4-010 强化）——四个终结条件中三个在默认装配下失效，唯一活性来源 future 在远程不可达时永不终结，无第五条回传通道的枚举经复核成立。历史对照：check-findings P1"rpcPoll 轮询句柄 cancel（每次执行泄漏空转调度项）"已修复（commit 72b770b094，现 register L123 whenComplete + L125-127 双检在位）系 future 终结方向；本条为"future 永不终结"方向，方向不同，未见历史登记。三态自洽：与 C5-001（P1 无界注册表视角）、C7-001（P1 泄漏+重试风暴视角）为同一机制的三维度视角，后续 7.1 合并裁定时应归并单立案，判级 P1 一致）

### [nop-job-C4-002] LocalJobScheduler 定时触发与 job 执行共用同一 4 线程调度池，阻塞 job 无隔离拖垮全部作业触发

1. **发现编号**：nop-job-C4-002
2. **严重度**：P2（边界配置缺陷/任务隔离缺失；有界声明池内可预期劣化）
3. **类别**：C4-4（任务隔离：调度线程与执行线程未分离）
4. **缺陷定位**：`nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:296-309`（scheduleNext 的执行回调直接注册到调度池）、`:313-345`（executeJob 内联调 invoker）；`nop-job/nop-job-local/src/main/java/io/nop/job/local/executor/BeanMethodJobInvoker.java:113-127`（同步方法在调用线程内联执行）；池声明 `nop-job/nop-job-local/src/main/resources/_vfs/nop/job/beans/app-local-scheduler.beans.xml:7-17`；平台语义 `nop-kernel/nop-commons/.../ExecutorHelper.java`（`schedule` 直接在 STPE 线程上 `task.call()`）
5. **证据片段**：
    ```java
    // LocalJobScheduler.java:301-309 —— 回调体（含 executeJob → invoker.invokeAsync）跑在 nopJobScheduledExecutor 的定时线程上
    CompletableFuture<?> future = executor.schedule(() -> {
        synchronized (job) {
            job.scheduledFire = null;
            if (!active || job.state.internal != InternalState.WAITING)
                return null;
            executeJob(job);
        }
        return null;
    }, delay, TimeUnit.MILLISECONDS);
    ```
    ```java
    // BeanMethodJobInvoker.java:113-119 —— 同步 bean 方法（返回非 CompletionStage）在本线程内联执行完
    CompletionStage<Object> invokeReflective(IFunctionModel fn, Object bean, Map<String, Object> methodParams) {
        try {
            return toCompletionStage(invokeMethod(fn, bean, methodParams));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    ```
6. **缺陷描述**：`nopJobScheduledExecutor` 既是**全部作业的定时器**又是**全部作业的执行器**（4 线程，`nop.job.local-scheduler.pool-size` 默认 4，beans 中 core=max 显式对齐）。YAML 配置式（owner doc 推荐路径）经 `BeanMethodJobInvoker` 调用同步 bean 方法时，方法体完整跑在调度线程上。不存在任何"触发在调度池、执行在 worker 池"的解绑（对比：分布式模式 scanner 侧至少用了 `executeOn(globalWorker())` 把扫描体移出定时线程）。
7. **触发时序**：配置 5 个 cron job，其 bean 方法均为阻塞 IO（如各执行 60s 的同步 REST 调用）。4 个 job 先后到点各占一条调度线程；第 5 个 job 的 cron 到点时其 `schedule` 回调在 DelayedWorkQueue 中排队 60s+；期间所有 job（包括彼此）的下次触发时间被持续推迟，且**没有任何日志/指标暴露"调度延迟"**（fireCount 照常累加，仅实际触发时刻漂移）。超过 4 个并发阻塞 job 时全部触发互相拖拽，劣化程度随阻塞 job 数线性增长。
8. **影响面**：本地模式下任意一个慢/阻塞 job 侵蚀其余全部 job 的触发及时性；无观测手段区分"调度健康"与"调度饥饿"。放大场景：编程式用法按 owner doc §1.2 示例直接传 `GlobalExecutors.globalTimer()`（全平台共享的 5 线程定时器）时，阻塞 job 会拖慢**平台级**定时任务，而不止 nop-job 自身。
9. **缺陷族对照**：F3 邻接（缺陷面=全部经 `nopJobScheduledExecutor`/`globalTimer()` 执行的本地 job，无"仅某类 job"的豁免面；失效面枚举=本地模式全部作业）；非 F8（池与持有者生命周期对称，destroy 声明正确）。
10. **误报校准考量**：owner doc 明确本地模式定位"单机嵌入式、轻量"，且 pool-size 可配置调大——但"定时器与执行器同池"是结构性隔离缺失而非参数问题：调大 pool-size 只能线性缓解，阻塞 job 数超过池大小即复现。判 P2 不判 P1 的原因：不丢任务、不泄漏，仅触发及时性劣化，且场景限定于声明过的轻量用途。
11. **修复建议**（候选）：`scheduleNext` 的回调只做"取任务+转投执行池"（如 `GlobalExecutors.globalWorker()` 或新增专用执行池），或文档显式声明"job 方法必须非阻塞，长任务自行转异步"并加启动期提示。至少应在文档 §1.4 补充该约束。
12. **置信度**：高。调用链（schedule 回调 → executeJob → invokeAsync → 内联同步方法）全部为同步直调，无隐藏的线程切换。
13. **残留边界**：invoker 返回真实异步 CompletionStage（如 rpc/rpcPoll 模式）时调度线程即时释放，不受影响；`LocalJobScheduler` 的并发正确性（synchronized(job)/putIfAbsent 双检，L88-131）经核对无缺陷，不属于本条。

**复核状态**：已保留（P2 维持。调用链逐点核实：executor.schedule 回调 L301-309 内联 executeJob → BeanMethodJobInvoker.invokeAsync 全程无线程切换（invokeReflective L113-119 同步直调，非 CompletionStage 返回值经 toCompletionStage completedFuture 内联完成）、池 4/4 显式对齐（app-local-scheduler.beans.xml:13-14）；"定时器与执行器同池是结构性隔离缺失而非参数问题"的定性成立，P2（不丢任务不泄漏、仅及时性劣化）校准合理。历史 nop-job 审计 2026-05/06 各轮 summary 无同族登记）

### [nop-job-C4-003] scanner 调度链遇 Throwable 或提交拒绝即永久停摆：running 标志仍为 true，停摆无生命周期级感知（F9 变体）

1. **发现编号**：nop-job-C4-003
2. **严重度**：P2（缺观测 + 理论风险，触发条件为 Error/拒绝，非 Exception 主路径）
3. **类别**：C4-5（定时任务：调度停止是否被感知）+ C4-2（拒绝策略与被拒任务去向）
4. **缺陷定位**：`nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java:58-80`（startScanning/doScan 无终结观测）、`:88-103`（scanOnce 仅 catch Exception）；`nop-kernel/nop-commons/.../BindScheduledExecutor.java:88-116`（Throwable → completeExceptionally → 链终止）；`AbstractBatchScanner.java:67-73`（scanFuture 从不被检查）
5. **证据片段**：
    ```java
    // AbstractBatchScanner.java:58-73 —— running 置位后无人回收；scanFuture 仅在 stopScanning 使用
    public synchronized void startScanning() {
        if (running) {
            return;
        }
        running = true;
        scanFuture = getExecutor().scheduleWithFixedDelay(
                this::doScan, 0, scanIntervalMs, TimeUnit.MILLISECONDS);
    }
    ```
    ```java
    // AbstractBatchScanner.java:93-102 —— 仅 catch Exception；Error 逃逸到 BindScheduledExecutor 的 catch (Throwable)
    for (int i = 0; i < maxScanLoops; i++) {
        try {
            if (!scanBatch()) {
                return;
            }
        } catch (Exception e) {
            onScanFailed(e);
            return;
        }
    }
    ```
    ```java
    // BindScheduledExecutor.java:99-115 —— command.run() 抛 Throwable 或 executor.execute 被拒绝 → future 异常完结，重排链永久终止
    try {
        executor.execute(() -> {
            if (future.isDone()) return;
            try {
                command.run();
            } catch (Throwable e) {
                LOG.error("nop.err.thread-pool.execute-fail",e);
                future.completeExceptionally(e);
                return;
            }
            schedule(command, delay, delay, unit, future, timerHandle);
        });
    } catch (Throwable e) {
        future.completeExceptionally(e);
    }
    ```
6. **缺陷描述**：6 个 scanner 的调度链是 BindScheduledExecutor 的手工重排链：worker 池上一轮完成后重排下一轮 timer tick。`scanOnce` 对每轮 `scanBatch` 的 `catch (Exception)` 保证了业务异常不终止调度（记 `onScanFailed` 后本轮返回，链继续）——这一层 F9 已处理且感知良好（LOG.error）。但两条旁路会使链**永久死亡**且仅留一条框架级 error 日志：(a) `Exception` 之外 的 `Throwable`（`onCycleStart` override、cursor reset、或 scanBatch 内抛出的 Error 如 `NoClassDefFoundError`/`StackOverflowError`/OOM 包装）→ `command.run()` 抛出 → future completeExceptionally → 不再重排；(b) globalWorker 拒绝（AbortPolicy：30 线程满 + 10000 队列满）→ `executor.execute` 抛 RejectedExecutionException → 同上。链死后：`running` 仍为 true、`scanFuture` 非 null（stopScanning 对已完结 future 是 no-op）、metrics 无变化——外部观测上该 scanner "在运行"，实际永久停止扫描，planner/dispatcher/timeout 任一停摆即整个分布式调度静默冻结。
7. **触发时序**：(a) 例：某次升级后 coordinator 进程内 JobTimeoutChecker 的 scanBatch 依赖类初始化失败抛 `NoClassDefFoundError` → 该轮 scanBatch 抛 Error → 链终止 → 超时检查器永久停摆，LOG.error 一条后无任何告警，`JobCoordinator.isActive()` 仍为 true；(b) 例：平台某业务向 globalWorker 倾泻阻塞任务占满 30 线程 + 10000 队列 → 全部 6 个 scanner 的下一轮 tick 提交被拒 → 全部调度面同时永久停摆。
8. **影响面**：停摆组件丧失全部功能且状态说谎（running=true）；无指标、无自动重启、无告警挂钩。与 C4-005（共享池）复合时为系统性单点。
9. **缺陷族对照**：F9（定时任务停止调度的可感知性——本处"停止"形态：JDK 原生 scheduleWithFixedDelay 的吞异常停摆已被 BindScheduledExecutor 的 try/catch-Throwable + LOG.error 部分治理，但"感知"止步于一条日志，未上升到 lifecycle/metrics 层）；F3 邻接（失效面=6 个 scanner 全体，无豁免）。
10. **误报校准考量**：主路径 Exception 已被正确隔离（不立案）；Error/拒绝属低频路径——按"写不出具体触发时序降 P2"纪律本可再降，但 (a) 类有明确可达序列（类加载失败/OOM 是长生命周期进程现实事件），故定 P2 并标注：**理论风险成分存在（b 类拒绝需池完全饱和），(a) 类时序具体**。
11. **修复建议**（候选）：(a) `scanOnce` 的 catch 放宽为 catch Throwable（或 doScan 包一层）；(b) `startScanning` 给 future 挂 `whenComplete` ——异常完结时 `running=false` + metrics 计数 + LOG.error（带 scanner 名），使停摆可被生命周期/监控反映；(c) 或提供 `restartScanning()` 供告警回调重拉。
12. **置信度**：高（机制层确定）；触发概率为中低。
13. **残留边界**：BindScheduledExecutor 的 LOG.error 已保证"有痕"（区别于 JDK 原生静默停摆），本条主张的是"停摆未被生命周期层感知"，非"完全无痕"。

**复核状态**：已保留（P2 维持且复核强化。scanOnce 仅 catch Exception（L98）、scanFuture 赋值后仅 stopScanning 使用（L63/L69-71）、BindScheduledExecutor command.run 抛 Throwable→LOG.error+completeExceptionally 不再重排均核实；复核另证：globalWorker 拒绝路径走 schedule 方法外层 catch(Throwable)→completeExceptionally，**该 catch 无任何日志**——初审"仅留一条框架级 error 日志"对 (b) 类拒绝路径尚属从宽，实际完全无痕，缺陷比初审描述更重，P2 判级不变。running=true 状态说谎与 stopScanning 对已完结 future no-op 的组合核实无误）

### [nop-job-C4-004] RpcPollTaskManager.pollOnce 抛出 Error 时 JDK 周期任务被 JVM 静默抑制，entry 僵尸化且零日志（F9，理论风险）

1. **发现编号**：nop-job-C4-004
2. **严重度**：P2（理论风险，未具体化到高概率触发；按纪律标注）
3. **类别**：C4-5（定时任务：scheduleWithFixedDelay 异常后停止调度是否被感知）
4. **缺陷定位**：`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcPollTaskManager.java:119-120`（JDK 原生 `scheduleWithFixedDelay` 直挂 `DefaultScheduledExecutor`）、`:175-207`（pollOnce 仅 `catch (Exception e)`）
5. **证据片段**：
    ```java
    // RpcPollTaskManager.java:119-120 —— 挂在裸 STPE 上（非 BindScheduledExecutor，无外层 Throwable 捕获）
    Future<?> handle = scheduledExecutor().scheduleWithFixedDelay(
            () -> pollOnce(entry), pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
    ```
    ```java
    // RpcPollTaskManager.java:204-206 —— 仅捕获 Exception
    } catch (Exception e) {
        LOG.warn("nop.job.remote.poll-failed:taskId={}", entry.getTaskId(), e);
    }
    ```
6. **缺陷描述**：与 C4-003 不同，此处走 JDK `ScheduledThreadPoolExecutor` 原生周期语义：任务抛出未捕获 Throwable 时，JDK **静默抑制该周期任务的全部后续执行**（`ScheduledFutureTask.run` 的既定行为，无任何日志）。`pollOnce` 捕获了 Exception，业务异常不会触发；仅 Error（OOM/StackOverflowError/极端类加载失败）可逃逸。逃逸后：该 entry 的轮询永久停止、future 永不完成、`pollHandle` 变为死句柄仍挂在 DelayedWorkQueue、`entries` 永不移除——与 C4-001 同样的僵尸 entry 形态，但**连 WARN 日志都没有**（BindScheduledExecutor 路径至少有 LOG.error）。
7. **触发时序**：进程内存压力下某次 `pollOnce` 执行中抛 OOM Error → JDK 抑制该 entry 后续轮询且无输出 → 该远程任务既不被轮询也不被终结，仅能等 cancelToken 取消或进程重启。无法给出更高概率的具体化序列，故按纪律降格并标注：**理论风险，未具体化**。
8. **影响面**：单 entry 级僵尸（泄漏量同 C4-001 单例），叠加 C4-001 后注册表可见的 activeCount 虚高不可解释。
9. **缺陷族对照**：F9（原生 scheduleWithFixedRate/Delay 吞异常停摆且不可感知的教科书形态——nop 在 AbstractBatchScanner 链路上已用 BindScheduledExecutor 治理，RpcPollTaskManager 这一处未复用同一设施）。
10. **误报校准考量**：pollOnce 的 Exception 捕获已覆盖全部业务异常；Error 触发序列无法具体化到现实频率 → P2 低档，且不建议单独修复（随 C4-001 的失败预算一并消解）。
11. **修复建议**（候选）：pollOnce 最外层改 `catch (Throwable)`（与 BindScheduledExecutor 同口径），吞 Error 但记 LOG.error——以"留痕"换"周期存续"。
12. **置信度**：机制高、触发概率低。
13. **残留边界**：entry 注册竞态（future 在 setPollHandle 前完成）已由 `register` 尾部 `if (future.isDone()) cancelEntry(entry)` 双检兜底（`RpcPollTaskManager.java:124-127`），核对无缺陷，不在本条。

**复核状态**：已保留（P2 维持。L119-120 裸 JDK scheduleWithFixedDelay 直挂 DefaultScheduledExecutor（非 BindScheduledExecutor 设施）、pollOnce 仅 catch Exception（L204-206）均核实；JDK STPE 周期任务抛未捕获 Throwable 后静默抑制后续执行为既定语义。细节勘误（不影响判级）：异常完结的周期任务不再重入队，done 句柄会被 STPE 出队，"pollHandle 仍挂在 DelayedWorkQueue"表述不准确——持久滞留物是 entries 注册表内的 PollEntry 及其死句柄引用。与 C4-012 同族同修法合并立案口径自洽，P2 理论档与 C4-003 (a) 类校准一致。历史审计无同族登记）

### [nop-job-C4-005] worker 任务派发与全部 scanner 扫描体共享平台 globalWorker，max-concurrency 默认 0 不限制在途调用数，阻塞 invoker 可平台级拖垮调度面

1. **发现编号**：nop-job-C4-005
2. **严重度**：P2（任务隔离缺陷 + 缺观测；默认配置下不设限）
3. **类别**：C4-4（任务隔离：不同业务共享同一池）+ C4-1（worker 容量）
4. **缺陷定位**：`nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java:134-136`（全部 scanner 的执行面）；`nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:119-122`（maxConcurrency 默认 0）、`:218-225`（批量 fire-and-forget 派发）、`:295-306`（invokeAsync 挂 whenComplete 后即返回）；池参数 `nop-kernel/nop-commons/.../GlobalExecutors.java:85-96` + `CommonConfigs.java:26-27`（max 30）+ `ThreadPoolConfig.java:25`（queue 10000）
5. **证据片段**：
    ```java
    // AbstractBatchScanner.java:134-136 —— 6 个 scanner 的 scanBatch 全部在平台 globalWorker 上执行
    protected IScheduledExecutor getExecutor() {
        return GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
    }
    ```
    ```java
    // JobWorkerScannerImpl.java:119-122 —— 0 = 不限（owner doc §五：最大并发执行数 0=不限）
    @InjectValue("@cfg:nop.job.worker.max-concurrency|0")
    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }
    ```
6. **缺陷描述**：三层共享叠加：(a) 6 个 scanner（planner/dispatcher/completion/timeout/worker/reconciler）的扫描体彼此共享 globalWorker；(b) worker 认领的 job 任务 invoker 也在 globalWorker 线程上被触发（scanBatch 在该池上执行，`executeTask` 内联调 `invokeAsync`）；(c) 与全平台其它模块的后台任务共享同池（30 线程 / 10000 队列）。内置 invoker 均为非阻塞异步（RpcJobInvoker/RemoteJobInvoker/DefaultRpcPollTaskClient 走 `IRpcServiceInvoker.invokeAsync`），但 **IJobInvoker 是公开 SPI**（owner doc §三），业务自实现的阻塞 invoker 会在 globalWorker 线程上长时间占位；`max-concurrency=0` 默认下，每 5s 每批最多 100 个任务被无节流地 fire 出去，在途调用数仅受 DB WAITING 任务量约束，JVM 内无信号量。
7. **触发时序**：业务注册一个阻塞式自定义 invoker（如同步 HTTP 长调用），调度 200 个并发任务 → globalWorker 30 线程被阻塞任务逐渐占满、队列向 10000 堆积 → 6 个 scanner 的下一轮 tick 在队列中排队 → planner/timeout 扫描周期从 5s 劣化到分钟级直至拒绝（再触发 C4-003 链死）→ **全平台后台任务一并饥饿**，且无指标区分"job 负载"与"其它业务负载"。
8. **影响面**：跨业务故障耦合（job 的任务执行面 = 全平台后台执行面）；无池级隔离、无在途计数指标、无背压信号（queue 深度有 metrics 但未与 job 关联）。
9. **缺陷族对照**：F3 邻接（失效面=全部 IJobInvoker 实现 + 全部 scanner；内置异步 invoker 不受影响，失效面枚举见清单表）；与 C4-003 构成复合链（饥饿→拒绝→链死）。
10. **误报校准考量**：平台"全局池共享"本身是平台设计（use of platform facility），但 nop-job 把**业务任务执行**放到与**框架扫描**同一无隔离池、且默认无并发上限，是模块级的选择而非平台强制——rpcPoll 已示范了专用池先例（nopRpcPollScheduledExecutor），worker 同步执行链未享受同等待遇。判 P2：默认 invoker 均异步，需业务自实现阻塞 invoker 才可达。
11. **修复建议**（候选）：(a) 为 worker 执行引入可选专用池（如 `nop.job.worker.executor` bean，模式对齐 nopRpcPollScheduledExecutor）；(b) 或文档硬性声明"invokeAsync 必须非阻塞"并在 DefaultJobInvokerResolver 处加检测/告警；(c) `max-concurrency` 文档补注其语义是"DB 在 flight 行数上限"而非"JVM 内并发上限"。
12. **置信度**：高（结构事实清楚）；可达性依赖业务侧 invoker 行为。
13. **残留边界**：`MetadataWorkerCapacityProvider`（DCL+volatile，`MetadataWorkerCapacityProvider.java:72-84`）与 `DefaultWorkerLoadProvider` 的 ThreadLocal per-scan 缓存（beginScan/endScan 在 `JobDispatcherScannerImpl.java:142-193` finally 对称 + `remove()`，`DefaultWorkerLoadProvider.java:54-68`）经核对无缺陷；`LeastLoadedStrategy` 无状态，线程安全。容量/分配治理本身不立案。

**复核状态**：已保留（P2 维持。getExecutor()=globalTimer().executeOn(globalWorker())（L134-136）、max-concurrency 默认 0（L119-122）、批量 fire-and-forget 派发（L218-225）、globalWorker 30 线程/10000 队列/AbortPolicy（CommonConfigs.java:26-27 + ThreadPoolConfig.java:25 + ExecutorHelper.newThreadPoolExecutor TaskQueue）全部核实；"业务任务执行面与框架扫描面共享且 JVM 内无信号量是模块级选择而非平台强制（rpcPoll 专用池为先例反证）"的定性成立，可达性依赖业务自实现阻塞 invoker 的 P2 校准合理。历史 nop-job 审计无同族登记（AR-83/84/86 批处理隔离族系 record 级隔离，与本条池级共享不同面））

### [nop-job-C4-006] 关闭路径无等待语义：池 destroy 为 shutdownNow 无 awaitTermination，scanner stop 为 cancel(false) 不等在途批次，容器停止可能硬切在途任务

1. **发现编号**：nop-job-C4-006
2. **严重度**：P3（关闭宽限期缺失；一致性由 ORM 事务兜底，属可声明边界）
3. **类别**：C4-3（关闭路径：shutdown 后在途任务资源回收）+ F8 对照
4. **缺陷定位**：`nop-job/nop-job-local/src/main/resources/_vfs/nop/job/beans/app-local-scheduler.beans.xml:7-8` 与 `nop-job/nop-job-coordinator/src/main/resources/_vfs/nop/job/beans/app-engine.beans.xml:88-89`（destroy-method）；平台语义 `nop-kernel/nop-commons/.../DefaultScheduledExecutor.java:88-93`（shutdownNow，无 await）；`nop-job/nop-job-core/.../AbstractBatchScanner.java:67-73`（cancel(false)）；`JobCoordinator.java:74-112`（stop 不 join）
5. **证据片段**：
    ```java
    // DefaultScheduledExecutor.java:88-93（平台设施语义，用于使用层判定）
    public void destroy() {
        if(executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
    ```
    ```java
    // AbstractBatchScanner.java:67-73 —— cancel(false)：在途 scanOnce 不被打断也不被等待
    public synchronized void stopScanning() {
        running = false;
        if (scanFuture != null) {
            scanFuture.cancel(false);
            scanFuture = null;
        }
    }
    ```
6. **缺陷描述**：两个显式声明的池 bean 均正确挂接 `destroy-method="destroy"`（F8：池随容器生命周期销毁，无 bean 泄漏——此点合格），但关闭是**立即中断**语义：在途 scanOnce（可能正持有 ORM 会话写 DB）、在途 job 执行、在途 pollOnce 均被 shutdownNow 打断，且调用方（IoC 容器 stop 序列）不等待其结束。`scanFuture.cancel(false)` 不打断在途扫描，但也不等待——`JobCoordinator.doStop` 返回后在途批次仍可能在运行数秒。
7. **触发时序**：应用收到停止信号 → IoC 逆序销毁 → JobCoordinator.stop（scanner 全部 cancel(false)，某 planner 批次正在写 fire 行）→ nopRpcPollScheduledExecutor.destroy → shutdownNow 打断在途 getJobStatus → 进程退出，在途批次若未提交则事务回滚（`@SingleSession` 语义），若恰在提交点则依赖 DB 事务原子性。
8. **影响面**：停止期在途任务被硬切；因状态权威在 DB 且扫描写入均在事务内，重启后由乐观锁/状态机自愈（owner doc 引用的 `concurrency-and-transactions.md` 模式），无一致性损失；代价是停止期出现可预期的 WARN/异常噪音与丢失的最后一轮扫描。
9. **缺陷族对照**：F8 对照结果——**生命周期对称性本身合格**（JobCoordinator/JobWorker doStart/doStop 逐组件对称且 per-component try/catch 隔离，`JobCoordinator.java:74-112`；池 bean destroy 挂接齐全；LocalJobConfigLoader @PreDestroy → deactivate 先于 executor 销毁，依赖序正确）。本条仅记录"对称但无宽限期"。
10. **误报校准考量**：shutdownNow 无 await 属平台设施实现（DefaultScheduledExecutor），按审计纪律只审使用方式；使用层（destroy-method 声明、销毁顺序）无缺陷 → 降 P3。
11. **修复建议**（候选）：scanner 侧可在 stopScanning 后对在途批次做有界等待（如暴露 `awaitIdleScan(timeout)` 供 doStop 调用）；或文档声明"停止期在途批次由事务回滚自愈"为既定语义。
12. **置信度**：高。
13. **残留边界**：`LocalJobScheduler.cancelRunning` 的 no-op（`LocalJobScheduler.java:390-393`）带显式注释声明"running CompletionStage 不可取消，job 将完成并看到 deactivated 状态"——已声明边界，不立案。

**复核状态**：已保留（P3 维持。两池 destroy-method 挂接（app-local-scheduler.beans.xml:8、app-engine.beans.xml:89）、DefaultScheduledExecutor.destroy=shutdownNow 无 awaitTermination（L88-93）、stopScanning cancel(false)（L67-73）、JobCoordinator.doStop L74-112 逐组件对称+per-component try/catch、LocalJobConfigLoader @PreDestroy→deactivate 销毁序正确均核实；"F8 对称性合格、仅缺宽限期"的区分定性准确，一致性由 ORM 事务兜底的 P3 校准合理。全仓 awaitTermination 零命中经复核确认）

### [nop-job-C4-007] 调度池的 maxPoolSize 配置位无效：ExecutorHelper 强制置 0，poll-threads 的 max 语义名不副实

1. **发现编号**：nop-job-C4-007
2. **严重度**：P3（配置诚实性；当前 core=max 无实际影响）
3. **类别**：C4-2（每池核对：核心/最大参数依据）
4. **缺陷定位**：`nop-job/nop-job-coordinator/src/main/resources/_vfs/nop/job/beans/app-engine.beans.xml:93-94`（core/max 同绑 `nop.job.remote.poll-threads`）；平台语义 `nop-kernel/nop-commons/.../ExecutorHelper.java:56-70`
5. **证据片段**：
    ```xml
    <!-- app-engine.beans.xml:93-94 -->
    <property name="corePoolSize" value="@cfg:nop.job.remote.poll-threads|2"/>
    <property name="maxPoolSize" value="@cfg:nop.job.remote.poll-threads|2"/>
    ```
    ```java
    // ExecutorHelper.newScheduledExecutor —— STPE 的 maxPoolSize 无效，直接清零
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(config.getCorePoolSize(), factory);
    // ScheduledThreadPoolExecutor的maxPoolSize没有被使用
    config.setMaxPoolSize(0);
    ```
6. **缺陷描述**：两个 nop-job 调度池均按 core=max 模式声明，规避了 STPE maxPoolSize 无效的平台事实——当前配置是自洽的。但 beans 里保留 `maxPoolSize` 属性位暗示"可独立调大 max"，运维若单独调大 max（如 core=2/max=10）将**静默无效**（实际恒为 core）。无任何校验或文档提示。
7. **触发时序**：运维将 `nop.job.remote.poll-threads` 理解为 max 语义并期望弹性扩容 → 无效果 → 轮询池恒为 core 值 → 高在途量下轮询节拍被摊薄（配合 C4-001 的僵尸 entry 加剧）。
8. **影响面**：配置误导；无运行时缺陷。
9. **缺陷族对照**：非 F3/F8/F9 主族；属 C4-2 配置依据核对项。
10. **误报校准考量**：core=max 显式对齐是正确做法，缺陷仅为"无效属性位未删除/未注释"。
11. **修复建议**（候选）：删除 maxPoolSize 属性位或在 beans 注释中标注"STPE max 无效，core 即池大小"。
12. **置信度**：高。
13. **残留边界**：`ThreadPoolConfig.queueCapacity=10000` 默认对两个 STPE 无意义（DelayedWorkQueue 固有无界）——调度池提交方（LocalJobScheduler 的 schedule、RpcPollTaskManager 的周期任务）提交量分别受 jobs 表/在途任务数约束，正常路径无 OOM 候选；C4-001 的泄漏是唯一无界增长源，已单独立案。

**复核状态**：已保留（P3 维持。app-engine.beans.xml:93-94 core/max 同绑 `nop.job.remote.poll-threads`、ExecutorHelper.newScheduledExecutor（L56）内 L63 `config.setMaxPoolSize(0)` 强制清零（STPE max 无效的平台事实）均核实；当前 core=max 声明自洽、缺陷仅为无效属性位无提示的配置诚实性定性准确，P3 恰当）

### [nop-job-C4-008] RpcPollTaskManager 自身无 stop 生命周期，注册表与句柄清理完全外置于注入池；fallback 到 globalTimer 时句柄挂在永不销毁的平台池上

1. **发现编号**：nop-job-C4-008
2. **严重度**：P3（当前装配下 F8 合格；fallback 装配下清理路径缺失）
3. **类别**：C4-3（池随 bean 泄漏 / 关闭路径）+ F8
4. **缺陷定位**：`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcPollTaskManager.java:62-128`（无 stop/destroy/@PreDestroy）、`:170-173`（fallback）
5. **证据片段**：
    ```java
    // RpcPollTaskManager.java:170-173 —— executor 未注入时整周期任务挂到全局定时器
    private IScheduledExecutor scheduledExecutor() {
        IScheduledExecutor executor = this.scheduledExecutor;
        return executor != null ? executor : GlobalExecutors.globalTimer();
    }
    ```
    ```xml
    <!-- app-engine.beans.xml:102-104 —— 生产装配：专用池注入，池随容器销毁 -->
    <bean id="nopRpcPollTaskManager" class="io.nop.job.coordinator.engine.RpcPollTaskManager">
        <property name="scheduledExecutor" ref="nopRpcPollScheduledExecutor"/>
    </bean>
    ```
6. **缺陷描述**：manager 无独立生命周期：entries 注册表与每 entry 的周期句柄的清理完全依赖 (a) future 终结（运行期，受 C4-001 影响）+ (b) 注入池的容器销毁（停止期）。生产装配（专用池 bean destroy-method）下 F8 对称成立；但 fallback 路径（代码注释明示与 AbstractPollingLeaderElector 同模式的容许装配）下，句柄挂在 `GlobalExecutors.globalTimer()`——平台全局实例、无任何 destroy 调用者——manager 销毁时在途句柄继续在全局定时器上空转（每 5s pollOnce，直至 future 终结或进程退出）。
7. **触发时序**：测试/自定义装配仅注册 `nopRpcPollTaskManager` 不配 `nopRpcPollScheduledExecutor`（fallback 生效）→ 容器关闭销毁 manager bean（无 stop 逻辑，注册表原样）→ 全局定时器上的周期任务继续触发 pollOnce → RPC 失败 warn 循环，直至 JVM 退出。
8. **影响面**：仅非默认装配；默认装配无缺陷。
9. **缺陷族对照**：F8（持有者生命周期与池任务生命周期的对称性在 fallback 装配下断裂）。
10. **误报校准考量**：fallback 本身是声明过的设计（类 javadoc L46-48），且 globalTimer 为 daemon 线程不阻断 JVM 退出——故 P3。
11. **修复建议**（候选）：manager 增加 `@PreDestroy stop()`：遍历 entries 逐个 `cancelEntry`（顺带消解 fallback 装配下的空转）。
12. **置信度**：高。
13. **残留边界**：全局池永不销毁是平台语义（GlobalExecutors 无关闭入口），非 nop-job 可控。

**复核状态**：已保留（P3 维持。RpcPollTaskManager 全文无 @PreDestroy/stop/destroy（复核确认）、scheduledExecutor() fallback 到 globalTimer（L170-173）、生产装配注入专用池（app-engine.beans.xml:102-104）、GlobalExecutors 无关闭入口（register() 替换时 destroy 旧实例但无人对 globalTimer 调用）均核实；仅非默认装配受影响、daemon 线程不阻断 JVM 退出的 P3 校准准确）

## 覆盖度与残留边界

**盘点覆盖声明**：以 `_tmp/cra-baseline-nop-job.txt` C4 节 2 个使用点为起点，人工对 12 个子模块全量 `src/main/java` 执行 5 组关键词 grep（线程/池/调度/关闭/异步原语），逐文件核对全部命中点；核对过的关键链路：AbstractBatchScanner（基类调度链）→ 6 个 scanner 子类 → JobCoordinator/JobWorker 生命周期；LocalJobScheduler 全文（含 addJob/removeJob/suspend/resume/fireNow/deactivate 竞态防护）+ BeanMethodJobInvoker + LocalJobConfigLoader；RpcPollTaskManager 全文 + RemoteJobInvoker + DefaultRpcPollTaskClient + DefaultJobCancelHandler；JobWorkerScannerImpl 全文 + JobWorker + MetadataWorkerCapacityProvider + DefaultWorkerLoadProvider + LeastLoadedStrategy + AdaptiveJobTaskBuilder；4 个 beans.xml（local/coordinator app+engine/worker delta/service）+ 平台设施语义（DefaultScheduledExecutor/DefaultThreadPoolExecutor/BindScheduledExecutor/GlobalExecutors/ThreadPoolConfig/ExecutorHelper/LifeCycleSupport/FutureHelper.futureCall）。基线 C4 节无遗漏项（RpcPollTaskManager 与 AbstractBatchScanner 两点均已展开为更深发现）。

**明确核对且不立案的项**：
- `new Thread` / commonPool / `supplyAsync` 等：src/main 零命中，无线程逃逸面。
- LocalJobScheduler 的 synchronized(job)/putIfAbsent 双检写法（L88-131）：竞态防护完整，属 C2 维度但已顺带核对无误。
- RpcPollTaskManager.register 的 future 早终结竞态（L124-127 双检兜底）：无缺陷。
- JobDispatcherScannerImpl beginScan/endScan 的 ThreadLocal 对称性（try/finally + remove）：无缺陷。
- JobCoordinator 与 JobWorker 可能双重 start 同一 workerScanner（双部署装配）：`running` guard 幂等，无双重调度。
- 线程命名（4 池全部经 NamedThreadFactory 具名）与池指标注册（enableMetrics 默认 true）：可观测性合格（正面结论）。
- `JobFireStoreImpl`/store 层乐观锁、`JobScheduleCounterReconciler` non-@SingleSession 例外：属 C1/C5/C7 维度，未在本维度展开。
- JobLogReporter best-effort 异步上报的并发上报无上限：C6 形态但 best-effort 已声明、量级受业务调用频度约束，判 P2 形态——按 overlay 规则（仅 P0/P1 登记）**不立案**。
- DefaultJobExecutionContextBuilder 中 `jobParams.computeIfAbsent("headers", ...)`（基线 C3 节）：单线程上下文构建路径，无共享可变 Map 证据，留 C3 维度复核。

**残留边界（本轮未覆盖，建议下轮或他维度接手）**：
- `IRpcServiceInvoker` 底层传输池（nop-rpc / http client）的容量与 job 轮询量的匹配关系在模块外。
- ORM 会话（@SingleSession）在 scanner 线程上的绑定/清理机制属 nop-dao 会话管理维度。
- 平台 `BindScheduledExecutor`/`DefaultScheduledExecutor` 自身的实现缺陷（如 destroy 幂等、refreshConfig 竞态）不在本单元立案范围。

**维度集外线索**：本轮未发现 C3/C6/C8/C9 形态的 P0/P1 级线索（候选 JobLogReporter 无上限异步上报、C4-003 的拒绝触发链死均判 P2，按 plan 2464 裁定不登记）。

## 维度复核结论（独立复核子代理）

- 复核人：C4 维度独立复核子代理（与初审/深挖轮不同会话，逐条重读 live code 验证）
- 复核日期：2026-10-15
- 复核范围：第 1 轮 C4-001~008 全部 8 条 + 第 2 轮 C4-009~012 全部 4 条；复核输入两份文件完整读取，逐条重读 live 源码核对文件/行号/证据片段/线程池配置/关闭路径/生命周期推演
- 复核方法：历史现状标注仅依据 ai-dev/audits 下 nop-job 历史审计目录（2026-05/2026-06 各轮 summary/结论节，含 check-findings P1 修复 commit 72b770b094 的 git log 核实）、同 campaign WI 1.1–1.3 审计目录、cra-index.md、git log；未读 ai-dev/plans、ai-dev/bugs、ai-dev/lessons

### 逐条判定表

| 发现编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---------|-------|------|-----------|---------|
| C4-001 | P1 | 保留 | 全链逐点核实（无失败预算 L195-203 / computeDeadline 不读全局 execution-timeout-ms L259-265 / DB 终态无回传通道 DefaultJobCancelHandler.java:35-49 / 不查 DB 注释 L56-57 / cancelToken 恒 null），四终结条件在默认装配下仅剩 future 一个活性来源，P1（错误路径泄漏+长时运行必然劣化）符合 campaign 口径 | 新发现（check-findings P1 已修的 72b770b094 系"每次执行泄漏空转句柄"的终结方向，现 L123/L125-127 在位；本条为 future 永不终结方向，不同向） |
| C4-002 | P2 | 保留 | 调用链 schedule 回调→executeJob→invokeAsync→invokeReflective 全程同步直调核实（L301-309、BeanMethodJobInvoker L113-119），定时器与执行器同池为结构性隔离缺失，P2 校准合理 | 新发现 |
| C4-003 | P2 | 保留（复核强化） | 链死机制逐点核实；复核另证 globalWorker 拒绝路径走外层 catch(Throwable)→completeExceptionally **无任何日志**——初审"仅留一条 error 日志"对 (b) 类从宽，缺陷实际更重，P2 不变 | 新发现 |
| C4-004 | P2 | 保留 | 裸 JDK scheduleWithFixedDelay（L119-120）+ 仅 catch Exception（L204-206）核实，JDK 静默抑制语义成立；细节勘误：异常完结周期任务不再重入队，"死句柄仍挂 DelayedWorkQueue"不准确（滞留物为 entries 内 PollEntry+死句柄引用），核心僵尸化零日志成立 | 新发现 |
| C4-005 | P2 | 保留 | 三层共享（scanner 间/业务任务与框架扫描/与全平台）与 max-concurrency=0 无 JVM 信号量核实（L134-136、L119-122、CommonConfigs:26-27），"模块级选择而非平台强制（rpcPoll 专用池反证）"定性成立 | 新发现 |
| C4-006 | P3 | 保留 | destroy-method 挂接、shutdownNow 无 await（DefaultScheduledExecutor L88-93）、cancel(false)（L67-73）、doStop 对称（L74-112）全部核实，"F8 对称合格+仅缺宽限期"区分定性准确 | 新发现 |
| C4-007 | P3 | 保留 | core/max 同绑 poll-threads（app-engine.beans.xml:93-94）+ ExecutorHelper L63 强制 setMaxPoolSize(0) 核实，配置诚实性 P3 恰当 | 新发现 |
| C4-008 | P3 | 保留 | manager 全文无 stop/@PreDestroy、fallback L170-173、生产装配注入专用池（app-engine.beans.xml:102-104）、GlobalExecutors 无关闭入口均核实，仅非默认装配受影响的 P3 校准准确 | 新发现 |
| C4-009 | P2 | 保留 | 容量扣减链全环核实（promise 无超时 L296-306 / RESERVED 含 RUNNING NopJobCoreConstants:40-44 / sumReservedCost JobTaskStoreImpl:166-169 / 兜底三选一默认全缺 JobTimeoutCheckerImpl:53+L489-502）；反驳面已排除：store 层仅 resetStaleWaitingTasks（WAITING），无 stale-RUNNING 回收路径 | 新发现（AR-83/84/88 已修系 WAITING 态方向，与本条 RUNNING 态不同向） |
| C4-010 | P3 | 保留 | 供给链穷举复核确认 default null 为唯一来源、三内建实现均未供 token、消费点全部透传，"取消即时感知"整体不可达定性成立；定位勘误：DefaultJobExecutionContextBuilder 实际在 nop-job-worker（条目缺模块前缀，不影响结论） | 新发现 |
| C4-011 | P3 | 保留 | 两 beans 无 threadDaemon、ThreadPoolConfig.java:29 默认 false、同库 globalTimer/globalWorker daemon=true 对照、shutdownNow 单次中断核实，SIGTERM 主路径豁免的 P3 校准准确 | 新发现 |
| C4-012 | P2 | 保留 | 两站点 Error 旁路核实（L302+L379-381、L335-344+L284）；影响面勘误："fireNow/awaitIdle 永假"过宽——二者检查 job.running，而 running 在 Error 逃逸前已于 L337 置 null，实际不会永假/永等；核心停摆判定不变；与 C4-004 同族合并立案口径自洽（L284 的 Exception 防护系 72b770b094 已修方向，Error 级未被覆盖） | 新发现 |

### 与同审计他维度发现的归并方向声明

C4-001（本条 P1）、C5-001（P1，无界注册表视角）、C7-001（P1，泄漏+重试风暴+WARN 放大视角）经复核确认为**同一机制**（rpcPoll 条目在"远程失联+无 per-schedule 超时"下永不终结）的三个维度视角，三者判级一致（均 P1），三态判定自洽——后续 7.1 合并裁定时应归并为单立案（避免三重计数），修复须同时覆盖失败预算/deadline 兜底/DB 终态联动三个面，且注意 C4-010 的前置约束（cancelToken 通道在内建装配下恒空，"补 cancelToken"式修复无效）。C4-009 为同根因（无终结驱动）在 worker 容量账本上的第二站点，后果面不同（DB 容量侵蚀 vs 注册表泄漏），维持单独立案。C4-012 与 C4-004 同族合并立案维持。

### 不确定项（显式列出）

1. **C4-004"DelayedWorkQueue 死句柄"细节**：异常完结的 JDK 周期任务不再重入队，done 句柄会被出队；初审"仍挂在 DelayedWorkQueue"表述不准确，持久滞留物是 entries 注册表内 PollEntry 及其死句柄引用——勘误已在条目复核状态行记录，不影响判级与修复方向。
2. **C4-012(b)"fireNow/awaitIdle 永假"子断言**：经核实为过宽表述（job.running 在 Error 逃逸前已置 null，L337 先于 L338），实际后果是 job.state.internal 停留 RUNNING + 永不再排程 + done 悬空（持有者若 await done 则挂起）；勘误已记录，核心停摆判定不变。
3. **C4-003(b) 拒绝路径日志缺失**：复核发现比初审描述更重（外层 catch(Throwable) 无日志），已作为强化项记录；不构成不确定，仅方向为强化。

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|-----|------------|---------|-----------|------|
| C4-001 | P1 | nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcPollTaskManager.java:119-128,175-207,259-265 | rpcPoll 轮询条目在远程永久不可达且未配置 timeoutSeconds 时永不终结（无失败预算+DB 终态不回传+deadline 恒 0），entry+周期句柄+失败 RPC churn 永久累积（与 C5-001/C7-001 同机制，归并单立案） | 新发现（72b770b094 已修系终结方向，不同向） |
| C4-002 | P2 | nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:301-345（池声明 app-local-scheduler.beans.xml:7-17） | 本地模式定时触发与 job 执行共用同一 4 线程调度池，阻塞 bean 方法内联占满调度线程拖垮全部作业触发，无隔离无观测 | 新发现 |
| C4-003 | P2 | nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java:58-103（+BindScheduledExecutor.java:88-116） | scanner 重排链遇 Throwable 或提交拒绝即永久停摆：running 仍 true 状态说谎，Throwable 路径仅一条日志、拒绝路径完全无日志，无生命周期级感知 | 新发现（复核强化：拒绝路径无日志） |
| C4-004 | P2 | nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcPollTaskManager.java:119-120,204-206 | pollOnce 抛 Error 时 JDK 静默抑制该 entry 后续轮询且零日志，entry 僵尸化（与 C4-012 同族同修法合并） | 新发现 |
| C4-005 | P2 | nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java:134-136（+JobWorkerScannerImpl.java:119-122） | worker 业务任务执行与全部 scanner 扫描体共享平台 globalWorker（30 线程/10000 队列），max-concurrency=0 默认无 JVM 内并发上限，阻塞 invoker 可平台级拖垮调度面 | 新发现 |
| C4-006 | P3 | nop-job/nop-job-local/src/main/resources/_vfs/nop/job/beans/app-local-scheduler.beans.xml:7-8（+app-engine.beans.xml:88-89、DefaultScheduledExecutor.java:88-93） | 关闭路径无等待语义：池 destroy 为 shutdownNow 无 awaitTermination，scanner stop 为 cancel(false) 不等在途批次（F8 对称性本身合格） | 新发现 |
| C4-007 | P3 | nop-job/nop-job-coordinator/src/main/resources/_vfs/nop/job/beans/app-engine.beans.xml:93-94（+ExecutorHelper.java:56-70） | 调度池 maxPoolSize 配置位无效（ExecutorHelper 强制置 0），poll-threads 的 max 语义名不副实，运维单独调大静默无效 | 新发现 |
| C4-008 | P3 | nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcPollTaskManager.java:62-128,170-173 | manager 无 stop 生命周期，fallback 到 globalTimer 时在途句柄挂永不销毁平台池，manager 销毁后空转至 JVM 退出（仅非默认装配） | 新发现 |
| C4-009 | P2 | nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:296-306（容量链 NopJobCoreConstants.java:40-44、JobTaskStoreImpl.java:166-169） | worker 侧 invokeAsync promise 无超时等待：永不终结的 invoker future 使 DB task 永久 RUNNING，cost 永久计入 sumReservedCost 侵蚀容量至 worker 静默停止认领（C4-001 同族第二站点） | 新发现 |
| C4-010 | P3 | nop-job/nop-job-api/src/main/java/io/nop/job/api/execution/IJobExecutionContext.java:22-24 | cancelToken 在全部内建装配下恒 null（default 方法唯一来源，三实现均未供 token），"取消即时感知"特性整体不可达，C4-001 终结条件收窄为两条 | 新发现 |
| C4-011 | P3 | nop-job/nop-job-local/src/main/resources/_vfs/nop/job/beans/app-local-scheduler.beans.xml:10-16（+app-engine.beans.xml:90-96、ThreadPoolConfig.java:29） | 两声明池非守护线程（threadDaemon 缺省 false）×同步 job 执行模型×shutdownNow 单次中断：嵌入式显式停止后不可中断任务使 JVM 无法退出，destroy 全程无日志 | 新发现 |
| C4-012 | P2 | nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:302,379-381（+LocalJobScheduler.java:281-290,335-344） | whenComplete 回调 Error 吞没族两站点：衍生 future 无人消费零日志，task/job 永久滞留 RUNNING（F9，与 C4-004 同族同修法合并；"fireNow/awaitIdle 永假"子断言经复核勘误为过宽） | 新发现 |

**复核后总量**：保留 12（P1×1、P2×6、P3×5）、降级 0、驳回 0——与初审/深挖累计统计（P1×1、P2×6、P3×5）一致；全部 12 条现状均为新发现（审计后 git log 无 nop-job 并发修复提交）。需子项复核的高风险项：无（C4-001 为唯一 P1，已完整复核并通过，合并归并方向见上节声明）。
