# nop-job 并发审计 — C4 线程池与执行器治理（第 2 轮深挖）

- 审计单元：`nop-job` 聚合器（12 个 jar 子模块，src/main/java 手写代码 164 files / 17,664 lines）
- 仓库根：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation）
- 审计维度：C4 线程池与执行器治理（第 2 轮：C4-001 失效面扩展 / C4-003 同族矩阵化 / C2 移交线索评估 / 关闭路径 F8 专项 / 线程命名矩阵）
- 盲审纪律：未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件（唯一例外为本维度第 1 轮文件 `C4-thread-pools.md`，为指定必要输入）；未读取其他维度输出
- 输入：第 1 轮发现 C4-001~008 + 池清单 P1~P4/T1~T2；`_tmp/cra-baseline-nop-job.txt` C4 节；全部 12 子模块 src/main/java 人工复核

## 1. 本轮发现

### [nop-job-C4-009] worker 侧 invokeAsync promise 无超时等待：永不终结的 invoker future 永久侵蚀 worker 资源容量直至静默停止认领（C4-001 同族第二站点）

1. **发现编号**：nop-job-C4-009
2. **严重度**：P2（任务隔离/容量治理缺陷；可达性依赖 SPI 实现，后果为静默饥饿）
3. **类别**：C4-2/4（池任务生命周期 + 任务隔离）+ F3 邻接
4. **缺陷定位**：`nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:296-306`（promise 等待，无任何超时包装）、`:151-159`（容量耗尽即整批放弃认领）；容量来源链 `nop-job/nop-job-core/src/main/java/io/nop/job/core/NopJobCoreConstants.java:40-44`（RESERVED_TASK_STATUSES 含 RUNNING）、`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobTaskStoreImpl.java:166-169`（sumReservedCost 按该状态集聚合）；DB 侧兜底条件 `JobTimeoutCheckerImpl.java:489-502`（无 timeoutSeconds 且 execution-timeout-ms<=0 时直接 return，不回收）
5. **证据片段**：
    ```java
    // JobWorkerScannerImpl.java:296-303 —— invokeAsync 返回的 promise 无 deadline、无在途注册表、无超时
    IJobExecutionContext ctx = executionContextBuilder.buildContext(schedule, fire, runningTask);
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
    // NopJobCoreConstants.java:30-31,40-44 —— RUNNING 计入资源承诺（reserved cost）
    // 计入 reserved 的任务状态集：WAITING(0)、CLAIMED(10)、SUSPICIOUS(15)、RUNNING(20)。
    List<Integer> RESERVED_TASK_STATUSES = List.of(
            TASK_STATUS_WAITING, TASK_STATUS_CLAIMED, TASK_STATUS_SUSPICIOUS, TASK_STATUS_RUNNING);
    ```
    ```java
    // JobWorkerScannerImpl.java:154-159 —— 容量被吃光后 worker 整体停止认领，仅一条 warn
    if (myRemaining.isZeroOrNegative()) {
        LOG.warn("nop.job.worker.resource-exhausted:cpu={},mem={}", ...);
        workerMetrics.onRejected(0);
        return false;
    }
    ```
6. **缺陷描述**：`JobWorkerScannerImpl.executeTask` 对 `invoker.invokeAsync(ctx)` 返回的 promise 采取"挂 whenComplete 即离场"策略——JVM 侧不存在第二终结通道（对比：rpcPoll 模式下 RemoteJobInvoker 的 future 由 RpcPollTaskManager 以 deadline+周期轮询双保险驱动终结，本站点两者皆无）。DB 侧的回收依赖三选一：schedule.timeoutSeconds>0、全局 `nop.job.coordinator.execution-timeout-ms>0`（**默认 -1**，`JobTimeoutCheckerImpl.java:489-502` 明确不回退到 dispatchTimeout）、worker-liveness 链（仅 worker 进程消失时命中）。默认配置 + 存活 worker 下，一个永不完成的 future 意味着 DB task 行永久滞留 RUNNING。而 RUNNING 属 RESERVED_TASK_STATUSES，`scanBatch` 每轮以 `sumReservedCost` 重算 `myRemaining`——滞留行的 cost 永久扣减容量，累积至 `isZeroOrNegative` 后 worker **整体停止认领一切任务**（含其他 schedule 的健康任务），仅余每轮一条 resource-exhausted warn，无指标、无告警挂钩。`max-concurrency>0` 装配下同构劣化（`countInFlightTasks` 的 [CLAIMED,RUNNING] 集合同样含滞留行）。
7. **触发时序**：(a) 业务经公开 SPI（owner doc §三，IJobInvoker 为扩展点）注册自定义 invoker，其内部队列丢弃任务/回调引用丢失导致返回的 CompletionStage 永不完成；(b) 该 schedule 未配置 timeoutSeconds，全局 execution-timeout-ms 保持默认 -1；(c) worker 进程持续存活 → DB task 永久 RUNNING → cost 计入 reserved；(d) 故障重复发生 N 次后 `myRemaining` 耗尽 → worker 停止认领全部任务，planner/dispatcher 正常流转但 worker 侧静默饥饿；(e) 无任何机制区分"容量耗尽"与"僵尸 promise"，restart 前不可自愈。
8. **影响面**：失效面 = 全部经该 worker 认领的任务（容量共享，无 per-schedule 隔离）；一个坏 invoker 拖垮同 worker 全部作业。与 C4-005（共享 globalWorker）复合：JVM 内线程未被占用（promise 非阻塞），劣化体现在 DB 容量账本而非线程池指标——现有池级 metrics 全部不暴露此劣化。
9. **缺陷族对照**：F3 邻接（失效面枚举：所有经 JobWorkerScannerImpl 认领的 executorKind=rpc/db 任务 + 全部自定义 invoker；内置 RpcJobInvoker/RemoteJobInvoker 在平台传输层有默认超时时间可达时不受影响）；F8 邻接（promise 生命周期与 task 行生命周期不对称：行已 RUNNING 而 JVM 侧无终结驱动者）。
10. **误报校准考量**：非"已声明边界"——owner doc 对超时的声明是"可通过 timeoutSeconds/execution-timeout-ms 配置"，而本缺陷是**默认配置下连兜底都不存在的结构性缺口**，且"reserved 含 RUNNING"是注释明示的刻意设计（NopJobCoreConstants.java:37-38），设计自身放大了后果。判 P2 不判 P1：需业务侧自定义 invoker 出现永不完成行为（或平台传输层被配置为无超时）才可达，与 C4-005 的可达性口径一致。
11. **修复建议**（候选）：(a) `executeTask` 对 promise 挂 `orTimeout`（以 schedule.timeoutSeconds 或新配置 `nop.job.worker.invoke-timeout-ms` 为界，超时走 handleExecutionResult(TIMEOUT)）；(b) 或在 scanBatch 增加对超长 RUNNING 行的本机对账（startTime 早于阈值的本 worker RUNNING 行主动终态化）；(c) 最小方案：resource-exhausted warn 携带 top 滞留 taskId 列表，使僵尸行可被人工定位。
12. **置信度**：高（机制链全部为 live code 直读，无推测环节）；可达性依赖 SPI 使用方式。
13. **残留边界**：promise 正常完成路径（含异常完成）经 `handleExecutionResult` 的 catch(Exception) + CAS 重试写回，核对无缺陷；`max-concurrency=0` 默认语义（DB 在 flight 行数不限）为第 1 轮 C4-005 已立案面，本条不重复计算其 JVM 内并发面；平台 IRpcServiceInvoker 传输层超时语义在模块外。

**复核状态**：已保留（P2 维持。容量扣减链全环核实：promise 无超时包装、无在途注册表（L296-306）；RESERVED_TASK_STATUSES 含 RUNNING（NopJobCoreConstants.java:40-44，注释明示刻意设计）；sumReservedCost 按该集聚合（JobTaskStoreImpl.java:166-169）；DB 兜底三选一默认全缺——execution-timeout-ms 默认 -1（JobTimeoutCheckerImpl.java:53），无 timeoutSeconds 时 tryMarkTimeout L489-502 直接 return（注释明示不回退 dispatchTimeoutMs），liveness 链仅 worker 消失命中，且 store 层仅有 resetStaleWaitingTasks（WAITING 态）无任何 stale-RUNNING 回收路径（复核反驳面已排除）；容量耗尽→整批放弃认领仅一条 warn（L151-159）。"C4-001 同族第二站点"定性成立：同根因（无超时/无终结驱动）不同后果面（DB 容量账本 vs 池注册表），单独立案正确。历史 AR-83/84/88（WAITING 双计/NPE/无 WAITING 回收）均已修复且与本条 RUNNING 态方向不同，无同族登记）

### [nop-job-C4-010] rpcPoll cancelToken 链在全部内建装配下为死代码：文档化的"取消即时感知/在途 RPC 中止"能力不可达，C4-001 的终结条件实际收窄为两条

1. **发现编号**：nop-job-C4-010
2. **严重度**：P3（文档/契约与实现不符；无独立运行时缺陷，但收窄 C4-001 泄漏面的终结条件数）
3. **类别**：C4-2（池任务生命周期终结路径核对）+ 文档诚实性
4. **缺陷定位**：`nop-job/nop-job-api/src/main/java/io/nop/job/api/execution/IJobExecutionContext.java:22-24`（default getCancelToken 返回 null）；三个内建上下文实现均未 override：`DefaultJobExecutionContextBuilder.java:70-119`（WorkerJobExecutionContext）、`DefaultJobCancelHandler.java:82-167`（CancelJobExecutionContext）、`LocalJobScheduler.java:559-595`（SimpleExecutionContext）；消费点 `RpcPollTaskManager.java:112-116`（appendOnCancel 仅 cancelToken!=null 时挂接）、`:189-193`（isCancelled 兜底检查）；透传点 `DefaultRpcPollTaskClient.java:43-45` javadoc 与 `RpcJobInvoker.java:64,93`
5. **证据片段**：
    ```java
    // IJobExecutionContext.java:22-24 —— 唯一来源是 default 方法，恒 null
    default ICancelToken getCancelToken(){
        return null;
    }
    ```
    ```java
    // RpcPollTaskManager.java:112-116 —— 非 null 才挂即时回调；内建装配下永不进入
    if (cancelToken != null) {
        Consumer<String> onCancel = reason -> onCancelToken(entry);
        entry.setOnCancelListener(onCancel);
        cancelToken.appendOnCancel(onCancel);
    }
    ```
    ```java
    // DefaultJobExecutionContextBuilder.java:70 —— worker 上下文实现类，无 getCancelToken override
    private static final class WorkerJobExecutionContext extends JobInstanceState implements IJobExecutionContext {
    ```
6. **缺陷描述**：第 1 轮 C4-001 将 cancelToken 列为 entry 的四个终结条件之一，并标注"第 1 轮称死代码"待复核。本轮全量复核确认：**全 nop-job src/main 没有任何 IJobExecutionContext 实现提供非 null cancelToken**——`getCancelToken` 仅有 default null 一个来源，三个实现类（worker 执行、coordinator 取消、local 执行）无一 override，全仓 grep 无 `setCancelToken`/构造注入。因此 RpcPollTaskManager 的整条"取消即时感知"机制（appendOnCancel 即时回调、轮询内 isCancelled 兜底、removeOnCancel 防泄漏）在**所有内建装配下不可达**；`DefaultRpcPollTaskClient`/`RpcJobInvoker` javadoc 声明的"token 中途取消时框架中止在途 RPC"同样永不生效（RPC 一律以 null token 发出，取消只能等响应自然返回后由状态机丢弃结果）。取消语义实际完全落在 DB 侧链：cancelFire/TimeoutChecker → `DefaultJobCancelHandler.cancelRunningTask` → invoker.cancelAsync → cancelJob RPC（且不 complete in-memory future——第 1 轮已并入 C4-001）。
7. **触发时序**：非运行时触发缺陷，而是能力性缺失：任何依赖文档语义（"令牌被取消的瞬间 cancelRemote + resolve，不等下一轮询周期"，`RpcPollTaskManager.java:49-52`）的集成方都会发现取消延迟实际为"下一轮询 never（token 恒 null）+ DB 链异步传播"；对 C4-001 的直接影响是 entry 终结条件从"future/deadline/cancelToken/whenComplete 四条"实际收窄为"future/deadline 两条 + whenComplete 清理"。
8. **影响面**：(a) C4-001 的泄漏面判定被**强化**（无 timeoutSeconds 时终结通道仅剩 future 一个活性来源）；(b) 已取消任务的在途 startJob/getJobStatus RPC 无法被中止，白耗传输层资源至响应返回；(c) 修复 C4-001 时若计划"补 cancelToken 通道"，须先打通 context 层的 token 供给，否则修复无效。
9. **缺陷族对照**：非 F3/F8/F9 主族；属 C4-2 终结路径枚举的修正项 + 文档契约失真。
10. **误报校准考量**：代码自身防御完整（null 检查齐全，不会 NPE），不是崩溃风险；但 javadoc 以"**取消即时感知**"为特性列表第一条（RpcPollTaskManager.java:49），声明与实现背离达到"特性整体不可达"级别，非措辞瑕疵。判 P3：无独立运行时损害，价值在于修正 C4-001 的失效面口径与防止无效修复。
11. **修复建议**（候选）：(a) `DefaultJobExecutionContextBuilder.buildContext` 为 task 创建 CancelToken 并存入 attributes/context（取消路径经 cancelFire 触发 token.cancel）；(b) 或修正 RpcPollTaskManager/DefaultRpcPollTaskClient/RpcJobInvoker 三处 javadoc，显式声明"当前内建装配 cancelToken 恒 null，即时取消能力需自定义 context builder 才生效"。
12. **置信度**：高。全仓 grep（ICancelToken/appendOnCancel/removeOnCancel/getCancelToken/setCancelToken）穷尽，default 方法是唯一来源。
13. **残留边界**：`onCancelToken`/`cancelEntry`/`removeOnCancel` 机制本身实现正确（异常隔离、双检兜底 L124-127 复核无误）——缺陷仅在供给侧恒空；自定义 IJobExecutionContextBuilder 装配下机制可生效（公开 SPI，非死代码意义上的不可删除）。

**复核状态**：已保留（P3 维持。供给链穷举复核确认：IJobExecutionContext.java:22-24 default null 为全 nop-job src/main 唯一来源（grep getCancelToken/setCancelToken 穷尽），三个内建实现（DefaultJobExecutionContextBuilder.java:70 WorkerJobExecutionContext、DefaultJobCancelHandler.java:82 CancelJobExecutionContext、LocalJobScheduler.java:559 SimpleExecutionContext）均未 override、无构造注入，消费点（RemoteJobInvoker L100/111/132、RpcJobInvoker L64/93、RpcPollTaskManager L112-116/189/195/247）全部透传——内建装配下"取消即时感知"特性整体不可达、C4-001 终结条件收窄的定性成立，P3（无独立运行时缺陷、防止无效修复）恰当。定位勘误（不影响结论）：DefaultJobExecutionContextBuilder 实际位于 nop-job-worker 模块，条目未标模块前缀）

### [nop-job-C4-011] 两个声明池以非守护线程运行（threadDaemon 未设置，平台默认 false）：嵌入式显式停止路径上 shutdownNow 仅中断一次，不可中断的同步 job 方法使 JVM 无法退出

1. **发现编号**：nop-job-C4-011
2. **严重度**：P3（条件路径；SIGTERM→shutdown-hook 主路径不受影响，限嵌入式场景）
3. **类别**：C4-3（关闭路径：shutdown 后线程归宿）+ F8 对照
4. **缺陷定位**：`nop-job/nop-job-local/src/main/resources/_vfs/nop/job/beans/app-local-scheduler.beans.xml:10-16` 与 `nop-job/nop-job-coordinator/src/main/resources/_vfs/nop/job/beans/app-engine.beans.xml:90-96`（ThreadPoolConfig 均未设置 threadDaemon）；平台默认 `nop-kernel/nop-commons/.../ThreadPoolConfig.java:29`（`private boolean threadDaemon = false;`）；关闭语义 `DefaultScheduledExecutor.java:88-93`（仅 shutdownNow）；执行模型 `BeanMethodJobInvoker.java:113-119`（同步方法内联于池线程）；对照组 `GlobalExecutors.java:44,89`（globalTimer/globalWorker 均显式 `setThreadDaemon(true)`）
5. **证据片段**：
    ```xml
    <!-- app-local-scheduler.beans.xml:10-16 —— 无 threadDaemon 属性 → 平台默认 false → 非守护线程 -->
    <bean class="io.nop.commons.concurrent.executor.ThreadPoolConfig">
        <property name="name" value="nop-job-local-scheduler"/>
        <property name="corePoolSize" value="@cfg:nop.job.local-scheduler.pool-size|4"/>
        <property name="maxPoolSize" value="@cfg:nop.job.local-scheduler.pool-size|4"/>
    </bean>
    ```
    ```java
    // ThreadPoolConfig.java:29 —— 默认非守护
    private boolean threadDaemon = false;
    // DefaultScheduledExecutor.java:88-93 —— destroy = shutdownNow（一次中断），无等待无日志
    public void destroy() {
        if(executor != null) { executor.shutdownNow(); executor = null; }
    }
    ```
6. **缺陷描述**：nop-job 两个显式池 bean 均未设置 threadDaemon，落在线程工厂默认 false——`nop-job-local-scheduler-N` 与 `nop-job-rpcPoll-N` 全部为**非守护线程**；同模块 fallback 面的 globalTimer/globalWorker 则是 daemon（对照鲜明）。本地模式的设计执行模型是同步 bean 方法内联跑在调度池线程上（owner doc 推荐路径），`shutdownNow` 只发出一次 interrupt：不响应中断的阻塞调用（无 SO_TIMEOUT 的 socket read、部分 JDBC 驱动、文件 IO）会持有该非守护线程直至自然返回。`LocalJobConfigLoader.destroy → scheduler.deactivate` 的 `cancelRunning` 为 no-op（已声明边界，第 1 轮 C4-006 残留项），不等待也不标记。两个已声明边界（cancelRunning no-op、无宽限期）叠加本条的非守护线程属性后，后果超出二者任一单独声明：**嵌入式宿主显式停止容器并从 main 返回时，JVM 因残留非守护线程不退出**，且 destroy 全程无日志，排障无从下手。
7. **触发时序**：(a) 应用以嵌入式方式使用本地模式（类 javadoc 定位"单机嵌入式场景"，`LocalJobScheduler.java:39`）；(b) 某 job 的 bean 方法正执行无超时的阻塞 IO；(c) 宿主 main 线程调用容器 stop → beans 逆序销毁（nopJobLocalConfigLoader @PreDestroy → deactivate 先行，随后 nopJobScheduledExecutor.destroy → shutdownNow 中断一次）；(d) 方法不响应中断继续阻塞 → main 返回后 JVM 存活于非守护线程，无任何日志提示；(e) 宿主只能 SIGKILL。注：SIGTERM 路径下 JVM 在 shutdown hooks 完成后无条件 halt，非守护线程被一并杀死，不触发本条。
8. **影响面**：仅嵌入式显式停止 + 不可中断任务同时成立的场景；分布式模式（异步 invoker，停止期线程空闲）不受影响。rpcPoll 池的 pollOnce 为快速异步触发，命中概率更低，但同属非守护面。
9. **缺陷族对照**：F8 对照——生命周期**挂接对称性**合格（第 1 轮已判），本条揭示的是"对称但线程归宿不满足 JVM 退出语义"：daemon 属性是池任务生命周期独立于持有者进程的唯一兜底，两声明池缺省未启用。
10. **误报校准考量**：threadDaemon 默认值属平台设施（ThreadPoolConfig），但"本地模式的指定执行模型 = 同步业务方法占用池线程 + beans 不设 daemon"是 nop-job 使用层的组合选择——平台全局池同一代码库内示范了正确用法（daemon）。判 P3：SIGTERM 主路径不受影响，嵌入式场景可由文档声明规避。
11. **修复建议**（候选）：(a) 两 beans 的 ThreadPoolConfig 增加 `<property name="threadDaemon" value="true"/>`（对齐 globalTimer/globalWorker，改动一行 ×2）；(b) 或 owner doc §1.4 显式声明"job 方法必须响应中断；嵌入式停止后 JVM 退出依赖 job 方法自然返回"。
12. **置信度**：高（daemon 缺省与关闭语义均为直读事实）；触发需要嵌入式用法叠加不可中断任务。
13. **残留边界**：`cancelRunning` no-op 与"无 awaitTermination 宽限期"两项已由第 1 轮 C4-006 以已声明边界/平台设施口径结案，本条不重复立案；JVM shutdown-hook halt 语义为 JDK 事实，非本仓代码。

**复核状态**：已保留（P3 维持。两 beans 的 ThreadPoolConfig 均无 threadDaemon 属性（app-local-scheduler.beans.xml:10-16、app-engine.beans.xml:90-96）、ThreadPoolConfig.java:29 默认 false、同库对照 GlobalExecutors globalTimer/globalWorker 均显式 setThreadDaemon(true)、shutdownNow 仅一次中断（DefaultScheduledExecutor L88-93）、本地模式同步方法内联占用池线程（BeanMethodJobInvoker L113-119）均核实；"两个已声明边界叠加非守护线程属性后后果超出任一单独声明"的组合定性成立，SIGTERM 主路径不受影响的边界声明正确，P3 恰当）

### [nop-job-C4-012] CompletableFuture whenComplete 回调 Error 吞没家族的两个新站点：handleExecutionResult / LocalJobScheduler 回调内 Error 逃逸零日志，任务/作业永久滞留 RUNNING（F9，理论风险，与 C4-004 同族）

1. **发现编号**：nop-job-C4-012
2. **严重度**：P2（理论风险，Error 级触发无法具体化到现实频率；按 campaign 口径标注，与第 1 轮 C4-004 同档）
3. **类别**：C4-5/F9（异步回调终结路径的吞异常停摆感知）
4. **缺陷定位**：站点 a：`nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:302`（whenComplete 挂接，返回的衍生 future 被丢弃）与 `:379-381`（handleExecutionResult 仅 catch Exception）；站点 b：`nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:335-344`（executeJob 的 whenComplete 回调）与 `:281-290`（scheduleNext 的 trigger 计算仅 catch Exception）
5. **证据片段**：
    ```java
    // JobWorkerScannerImpl.java:302 —— 回调结果 future 无人消费，Error 静默消失
    promise.whenComplete((result, err) -> handleExecutionResult(runningTask.getJobTaskId(), result, err));
    // JobWorkerScannerImpl.java:379-381 —— 仅 catch Exception；Error 逃逸到被丢弃的衍生 future
    } catch (Exception e) {
        LOG.error("nop.job.worker.handle-result-failed:taskId={}", jobTaskId, e);
    }
    ```
    ```java
    // LocalJobScheduler.java:335-344 —— 回调中途抛 Error 时 done 永不 complete，job.state 卡 RUNNING
    future.whenComplete((result, err) -> {
        synchronized (job) {
            job.running = null;
            handleResult(job, result, err);   // → scheduleNext：trigger calc 仅 catch Exception（L284）
            if (err == null) done.complete(null);
            else done.completeExceptionally(err);
        }
    });
    ```
6. **缺陷描述**：第 1 轮 C4-004 立案了 JDK 周期任务的 Error 抑制形态；本轮发现同族 F9 形态在**CompletableFuture 回调**面还有两个未覆盖站点：(a) worker 站点——`handleExecutionResult` 内部 catch(Exception) 保障了业务异常有痕（LOG.error），但 Error（OOM、极端类加载失败）从 whenComplete action 逃逸后进入无人消费的衍生 future，JDK 不打印任何输出；后果为 DB task 滞留 RUNNING（回收依赖 C4-009 列举的默认缺失的三条 DB 兜底）；(b) local 站点——`handleResult → scheduleNext` 的 trigger 计算只 catch Exception（L284），Error 逃逸使 `done.complete` 永不执行、`job.state.internal` 停留 RUNNING、下次触发永不排程，`fireNow`/`awaitIdle` 永假，**零日志**（对比 ExecutorHelper 路径至少有 `nop.executor.execute-fail` LOG.error——ExecutorHelper.java:186-190）。两站点 Exception 路径均已被正确隔离并有痕，本条仅指 Error 级旁路。
7. **触发时序**：进程内存压力下回调执行中抛 OOM/StackOverflowError → 衍生 future 异常完结无人观察 → (a) task 行滞留 RUNNING 或 (b) local job 永久 RUNNING。无法给出更高概率的具体化序列——按纪律标注：**理论风险，未具体化**。
8. **影响面**：(a) 单 task 级僵尸（叠加 C4-009 的容量侵蚀链）；(b) 单 job 级永久卡死（local 模式仅 removeJob/re-addJob 或重启可解）。均为 Error 级低频。
9. **缺陷族对照**：F9（吞异常停摆不可感知的 CompletableFuture 变体；BindScheduledExecutor 链路已用 catch(Throwable)+LOG.error 治理，ExecutorHelper submit/schedule 同口径，这两处用户级回调未复用同一口径）。
10. **误报校准考量**：与 C4-004 同口径判 P2 理论档；合并立案（两站点同族同修法）避免碎片化，不建议独立于 C4-004 修复。
11. **修复建议**（候选）：两站点回调体最外层包 try/catch(Throwable) 记 LOG.error（换"周期/终结存续"为"留痕"），与 BindScheduledExecutor/ExecutorHelper 同口径；local 站点可顺带在 catch 中置 job.state = FAILED 使状态不说谎。
12. **置信度**：机制高（逃逸路径代码直证）；触发概率低。
13. **残留边界**：`handleExecutionResult` 的 Exception 隔离与 CAS 重试写回（L343-369）核对无缺陷；`done` 悬空不影响 running 清理（running=null 先于 handleResult 执行，L337-338 顺序已核对）。

**复核状态**：已保留（P2 维持。站点 a：L302 whenComplete 衍生 future 丢弃 + handleExecutionResult 仅 catch Exception（L379-381）核实；站点 b：L335-344 回调 + trigger 计算仅 catch Exception（L284，该 Exception 防护系 check-findings 72b770b094 已修的"触发器异常置 FAILED"方向，Error 级旁路未被该修复覆盖）核实——Error 逃逸致 done 永不 complete、job.state.internal 停留 RUNNING、下次触发永不排程、零日志成立。影响面勘误（不影响判级）："fireNow/awaitIdle 永假"表述过宽——二者检查 job.running（L216/L436），而 running 在 Error 逃逸前已于 L337 置 null，实际不会永假/永等；核心停摆判定不变。与 C4-004 同族同修法合并立案口径自洽，P2 理论档一致）

## 2. 排除项/定向核查

### 2.1 C4-003 同族扩展：6 个 scanner 异常传播矩阵（复核结论：无扩大，第 1 轮单点声明升级为全量矩阵）

| scanner | onCycleStart override | onScanFailed override | scanBatch 内 per-record Exception 隔离 | 链死旁路（相对 C4-003 基线） |
|---|---|---|---|---|
| JobPlannerScannerImpl | 无（默认 no-op） | 有（专有 error 码日志） | 有：per-schedule try/catch（L128-133） | 无新增 |
| JobDispatcherScannerImpl | 无 | 有 | 有：per-fire try/catch + NopException no-fitting-worker 分支（L147-183）；beginScan/endScan try/finally 对称 | 无新增 |
| JobCompletionProcessorImpl | 无 | 有 | 有：per-fire try/catch（L105-117）；REQUIRES_NEW 事务隔离 | 无新增 |
| JobTimeoutCheckerImpl | 无（L149-151 显式注释声明不 override） | 有 | 有：processDispatchTimeouts（L195-207）+ scanTaskTimeouts（L252-264）双处 per-record 隔离；resolveAliveWorkerIds 自带 catch | 无新增 |
| JobScheduleCounterReconciler | 无 | 无 override（用基类实现，仍按 scanner 名记 error，可感知） | 有：per-schedule try/catch（L64-69） | 无新增 |
| JobWorkerScannerImpl | 无 | 有 | 有：per-task try/catch（L219-224）+ executeTask 内 load/resolve 隔离 + handleExecutionResult catch(Exception) | Error 旁路 → 移交 C4-012(a) |

`scanOnce` 的 `c.reset()` 循环与 `onCycleStart()` 位于 try/catch 之外（AbstractBatchScanner.java:88-92），理论上构成"Exception 即链死"的加宽面——但 6 个子类**无一 override onCycleStart**（默认空实现），cursor reset 为纯字段赋值不可抛，加宽面实际为零。C4-003 的链死面维持 (a) Throwable/Error 与 (b) globalWorker 拒绝两条，矩阵化后**无新立案**。

### 2.2 C2 第 2 轮移交线索评估：「本地单线程 executor 队头阻塞」在本维度口径下的裁定

- **队列形态/拒绝策略**：LocalJobScheduler 调度池（P1）为 STPE，DelayedWorkQueue 固有无界、submit 永不拒绝——**零背压信号**是事实，但每个 job 同一时刻至多 1 个在途 scheduledFire（`scheduleNext` 覆盖写 `job.scheduledFire`），队列深度以 job 数为界，无独立的无界增长面（唯一无界源 C4-001 已立案）。与 globalWorker（有界 10000 + AbortPolicy）的不对称不构成新缺陷。
- **队头阻塞**：pool-size=1（可配置）或编程式传入单线程 executor 时，一个阻塞 job 冻结全部触发——这是 C4-002 机制在极端参数下的形态，非独立缺陷，不另立案；后果链（触发漂移无观测）已并入 C4-002。
- ** exotic 死锁面**：job 方法若阻塞等待一个"由同一池上其他任务完成"的 future，池饱和时构成线程饥饿死锁——需要刻意构造的 job 间 future 依赖，理论风险，记为 C4-002 残留边界，不立案。
- **移交结论**：C2 线索在 C4 口径下**不产生新立案**；其有效增量为间接促成了 C4-011（非守护线程 × 同步执行模型的关闭面发现）。

### 2.3 关闭路径 F8 专项：全部 stop/destroy 钩子序列核对

| 钩子 | 位置 | 序列 | 判定 |
|---|---|---|---|
| nopJobScheduledExecutor bean destroy | app-local-scheduler.beans.xml:8 | shutdownNow（无 awaitTermination/无日志） | F8 挂接合格；无宽限期 = C4-006；非守护 = C4-011 |
| nopRpcPollScheduledExecutor bean destroy | app-engine.beans.xml:89 | 同上 | 同上（pollOnce 快速返回，命中低） |
| LocalJobConfigLoader @PreDestroy | LocalJobConfigLoader.java:223-227 | deactivate → 逐 job cancelScheduledFire（cancel(false)）+ cancelRunning no-op（声明边界） | 合格；销毁序正确（loader 后创建先销毁，先于 executor.destroy，无 ERR_SERVICE_NOT_ACTIVE 窗口） |
| JobCoordinator.doStop | JobCoordinator.java（doStop 全文核对） | 6 组件逐一 stopScanning，per-component try/catch + warn | F8 对称合格；无等待 = C4-006 |
| JobWorker.doStop | JobWorker.java doStop | workerScanner.stopScanning | 合格 |
| RpcPollTaskManager | app-engine.beans.xml:102-104（无 destroy-method）、类内无 @PreDestroy | 无钩子 | C4-008 已立案（本轮复核无变化） |

全仓 `awaitTermination` 零命中（含平台设施）——无任何钩子实现 shutdown→await→shutdownNow 三段式；该缺口的使用层后果已由 C4-006/C4-011 覆盖，平台实现本身不在立案范围。`shutdownNow` 仅 2 处（两 bean destroy），`@PreDestroy` 仅 1 处，无遗漏钩子。

### 2.4 线程命名可观测性矩阵（复核结论：合格，无发现）

| 线程来源 | 命名 | 工厂 | metrics |
|---|---|---|---|
| P1 nopJobScheduledExecutor | `nop-job-local-scheduler-{pool}-{n}`（beans name 属性） | NamedThreadFactory（ExecutorHelper.newScheduledExecutor L66） | enableMetrics 默认 true → ExecutorServiceMetrics 注册 |
| P2 nopRpcPollScheduledExecutor | `nop-job-rpcPoll-{pool}-{n}` | 同上 | 同上 |
| P3 globalTimer（fallback+scanner timer） | `nop-global-timer-*`（GlobalExecutors NOP_GLOBAL_TIMER） | NamedThreadFactory daemon=true | 平台全局 |
| P4 globalWorker | `nop-global-worker-*` | NamedThreadFactory daemon=true | 平台全局 |
| 兜底名 | 未命名 config → `nop-thread-executor`（ExecutorHelper L63-64） | — | — |

src/main 零 `new Thread`、零匿名 ThreadFactory；RpcPollTaskManager fallback 经 globalTimer 继承具名。全部线程创建点收敛于 NamedThreadFactory，无发现。

### 2.5 其他定向核查（均无缺陷，不立案）

- **兄弟轮询/异步等待站点穷举**：RpcPollTaskManager（per-entry 周期）、JobWorkerScannerImpl promise（→C4-009）、NopRetryJobRetryBridge.onFireFailed（fire-and-forget whenComplete，回调仅日志、外层 catch Exception，err 分支有痕，`nop-job-retry-adapter/.../NopRetryJobRetryBridge.java:49-60`）、JobLogReporter（零线程/executor/schedule 命中，纯委托，C6 排除维持）。除此之外 12 子模块 grep（new Thread/ExecutorService/Executors./ScheduledExecutor/whenComplete/synchronized）无第五个等待站点。
- **RpcPollTaskManager.register 竞态**（L110-127）：appendOnCancel 先于调度、whenComplete 后置双检、pollOnce 内 isDone 防御——三重兜底复核完好；cancelEntry 的 removeOnCancel 异常隔离（L143-148）完好。
- **LocalJobScheduler 并发面复核**：synchronized(job)/putIfAbsent 双检、SUSPENDED 热更新保持（check2 P3-13）、trigger-calc Exception 防护（L284-290）、deactivate 后 handleResult 的 `!jobs.containsKey → COMPLETED` 守卫（L371-374，堵住 destroy 竞态下 ERR_SERVICE_NOT_ACTIVE 逃逸）——均完好；Error 旁路移交 C4-012(b)。
- **`_NopJobCoreConstants`/`NopJobCoreConstants` 状态集**：RESERVED=[WAITING,CLAIMED,SUSPICIOUS,RUNNING]、IN_FLIGHT=[CLAIMED,RUNNING]、RUNNING_LIKE=+SUSPICIOUS——三集分离的注释与实现一致（支撑 C4-009 容量链判定）。
- **nop-job-meta / nop-job-codegen / nop-job-app / nop-job-web**：src/main 零并发原语命中；NopJobApplication 仅 Quarkus @Observes 启停转发。

## 3. 终止声明

**C4 维度深挖终止。** 判定依据：本轮对既定 5 条深挖线的枚举已闭合——(1) C4-001 失效面扩展完成：兄弟站点穷举至第 5 个（无第六个），cancelToken 死代码判定经全仓 grep 复核确认并强化（C4-010），worker 侧 promise 站点立案为 C4-009；(2) C4-003 由单点声明升级为 6-scanner 全量矩阵，链死面无扩大；(3) C2 移交线索裁定为 C4-002/006 的既有面，无独立缺陷；(4) 关闭路径全部 6 类钩子核对完毕，仅产出 C4-011 一项增量；(5) 命名矩阵合格。剩余 Error 级旁路已全部归并 C4-012/C4-004 同族；再挖的边际收益为零。移交他维度线索：无新增（C4-009 的 DB 滞留行回收缺口与 C1/DB 状态机维度相邻，已在第 1 轮 C4-001 残留边界提及，不重复移交）。

## 4. 覆盖声明

- **本轮实际读取**：第 1 轮报告全文 + 基线 C4/C2 节；RpcPollTaskManager、RemoteJobInvoker、DefaultRpcPollTaskClient、DefaultJobCancelHandler、DefaultJobExecutionContextBuilder、IJobExecutionContext、JobWorkerScannerImpl、AbstractBatchScanner、6 个 scanner 全文（Planner/Dispatcher/Completion/Timeout/Reconciler/Worker）、LocalJobScheduler、LocalJobConfigLoader、BeanMethodJobInvoker、JobCoordinator、JobWorker、NopRetryJobRetryBridge、RpcJobInvoker；平台语义参照 DefaultScheduledExecutor、ExecutorHelper、ThreadPoolConfig、NamedThreadFactory、GlobalExecutors（只审使用层）；全部 10 个 beans.xml + worker delta。
- **新增发现**：C4-009（P2）、C4-010（P3）、C4-011（P3）、C4-012（P2 理论）。累计 C4 维度 12 项：P1×1、P2×6（含 2 项理论标注）、P3×5。
- **确认干净项**：6-scanner 异常矩阵、register 三重竞态兜底、removeOnCancel 清理、handleExecutionResult 的 Exception/CAS 路径、LocalJobScheduler 竞态防护全集、销毁顺序、线程命名与 metrics 注册、JobLogReporter/NopRetryJobRetryBridge 的异步面。
- **纪律声明**：本轮未读取 ai-dev/audits（除指定第 1 轮文件）、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 及其他维度输出；未修改任何前轮文件；全部结论基于 concurrency-audit-remediation 分支 live code，文件行号以当前工作区为准。
