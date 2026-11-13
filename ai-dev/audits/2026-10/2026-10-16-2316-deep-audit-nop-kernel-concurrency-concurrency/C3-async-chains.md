# C3 异步链路正确性 —— 第 1 轮初审（盲审）

## 审计单元

- **审计对象**：nop-entropy 平台并发设施自身实现
  - A 面：`nop-kernel/nop-commons/src/main/java/io/nop/commons/{concurrent,cache,pool}`（85 files / 7,550 行）
  - B 面：`nop-kernel/nop-core`、`nop-kernel/nop-xlang` 全局模型缓存与注册表单例（按基线 `_tmp/cra-baseline-nop-kernel-concurrency.txt` B-face enumeration 节，46 files / 5,945 行；按「模型/元数据缓存」语义取子集）
- **仓库**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`，HEAD `8c13908bfd`）
- **维度**：C3 异步链路正确性——异步任务链的异常传播/超时/取消语义正确性；future 转换链的断裂（返回 future ≠ 完成通知挂接 future）；异步回调的上下文传播；异步结果与调用方契约。
- **判级口径**：平台设施被全平台并发调用；P0=默认用法+常见条件即触发且不可逆。

## 盲审纪律声明

本轮为盲审轮次。未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何历史文件（本目录中同批次的 `C1-shared-mutable-state.md`、`C2-lock-sync.md`、`dispatch-log.md` 均未读取）。方法论仅阅读了允许的 `ai-dev/skills/concurrency-resource-audit-prompt.md` 与 `ai-dev/skills/deep-audit-prompts.md`（全文）及基线文件 `_tmp/cra-baseline-nop-kernel-concurrency.txt`（全文）。全部结论基于当前 live code。`nop-api-core` 的 `FutureHelper`/`ContextProvider`/`BaseContext`/`CallableWithContext` 按 R2 勘误口径仅作对照，不立案。

## 检查范围说明

**完整通读的 A 面文件**（executor 包 20 文件全部）：`ExecutorHelper`、`BindScheduledExecutor`、`DefaultScheduledExecutor`、`DefaultThreadPoolExecutor`、`StandardThreadPoolExecutor`、`TaskQueue`、`ContinuationExecutor`、`GlobalExecutors`、`SequentialTaskExecutor`、`LimitedExecutor`、`RateLimitExecutorImpl`、`SyncThreadPoolExecutor`、`IScheduledExecutor`、`IThreadPoolExecutor`；concurrent 包：`CycleSynchronizer`、`ContextualizedRegistry`、`RoundRobinSupplier`（C1 已立案同族，核对修复注记后不再立案）、`IBlockingSource/IBlockingQueue/IBlockingEndpoint/IBlockingSinkNotifier`、`QueueOverflowPolicy`、`impl/OverflowBlockingQueue`、`impl/DedupBlockingQueue`（C2 墓碑同族，不重复立案）、`batch/AbstractBatchProcessService`、`batch/BlockingSourceConsumeTask`、`batch/BatchQueueConfig`、`lock/impl/LocalResourceLockManager`、`lang/impl/Cancellable`、`thread/{NamedThreadFactory,NopThread,ThreadHelper}`；cache 包：`LocalCache`、`MapCache`、`ICache`、`IAsyncCache`、`ISyncCache`、`ICacheLoader`、`CaffeineCacheLoader`、`CacheConfig`、`GlobalCacheRegistry`、`EhcacheCache`（computeIfAbsent 继承 ICache 锁化 default，属 C2 已立案家族）。pool 包无异步载体（仅借还契约），未立案。

**B 面核对结论**：`ResourceLoadingCache`、`ResourceCacheEntry`、`ResourceComponentManager`、`TenantAwareResourceLoadingCache` 通读。**B 面全局模型缓存不存在异步加载路径**：`ResourceLoadingCache` 构造的 `LocalCache` 未开启 `CacheConfig.async`，加载全部同步发生在请求线程；`ResourceComponentManager.loadComponentModel`→`cache.require` 为同步链，`ComponentCacheEntry.transformed` 的 `computeIfAbsent(transform)` 锁内 transform 已由 C2 轮立案（不重复）；`ResourceCacheEntry` 锁内加载死锁已由 C2 轮立案（不重复）。`ResourceTimestampCache` 整文件已注释废弃，不审。

**对照阅读（不立案）**：`nop-api-core` 的 `FutureHelper`（bindCancel/futureCall/completeAfterTask/executeWithThrottling）、`ContextProvider`、`BaseContextProvider`、`BaseContext.executeWithContext`、`CallableWithContext`。

**使用面取证**（失效面枚举依据，均为 src/main grep + 抽点通读）：`scheduleWithFixedDelay/scheduleAtFixedRate` 平台调用点 26 处；`executeOn(` 调用点 8 处；`throttleExecute` 调用点 3 处（nop-web）；`LimitedExecutor`/`SequentialTaskExecutor` 生产代码 0 调用；`CacheConfig.async` 生产代码 0 开启；`AbstractBatchProcessService` 子类 1 个（nop-auth `AuditServiceImpl`）；nop-sys `SysDaoMessageService` 生产注释自证周期停摆行为的负载面。

**判级说明**：`LimitedExecutor`、`SequentialTaskExecutor`、`CycleSynchronizer` 当前无生产调用方，其缺陷按「设施可用 API 的真实缺陷 + 未来使用方失效面」降 P2/P3，不按 P1 立案。B 面「移交其他维度」线索见文末。

---

## 第 1 轮（初审）发现

### [nop-kernel-concurrency-C3-001] 平台周期任务设施无统一异常绝缘层：一次 Throwable 即静默永久停摆且不可感知

- **文件**： `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/DefaultScheduledExecutor.java:126-135`；`IScheduledExecutor.java:29-32`；对照 `BindScheduledExecutor.java:71-116`
- **证据片段**:
  ```java
  // DefaultScheduledExecutor：直通 JDK ScheduledThreadPoolExecutor 语义
  @Override
  public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
      checkEnabled();
      return executor.scheduleAtFixedRate(command, initialDelay, period, unit);
  }

  @Override
  public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
      checkEnabled();
      return executor.scheduleWithFixedDelay(command, initialDelay, delay, unit);
  }
  ```
- **严重程度**: P1
- **并发场景**: 全平台唯一全局定时器 `GlobalExecutors.globalTimer()`（单例 `DefaultScheduledExecutor`，daemon 线程）承载约 26 处周期任务；任务体在 timer/worker 线程执行，异常直接冲击 JDK 调度状态机。
- **触发时序**: T1：`globalTimer().scheduleWithFixedDelay(this::cleanup, ...)`（如 `SysDaoNamingService.java:77`、`JdbcConfigSource.java:54`）周期执行；T2：某一轮任务体抛出未捕获 RuntimeException/Error（如 DB 连接抖动、`NoSuchMethodError`）；T3：JDK `ScheduledThreadPoolExecutor` 语义——该次异常被吞入 ScheduledFuture，**后续所有执行被永久抑制**；T4：返回的 Future 无人消费（`cleanupFuture` 仅在 doStop 时 cancel），无任何完成通知挂接 → 清理/刷新/心跳链静默死亡，系统长期运行在"看似正常、实际停摆"状态。
- **现状**: `IScheduledExecutor` 两个周期方法无任何 javadoc 警告 JDK 语义；`DefaultScheduledExecutor` 直通；同一平台内 `BindScheduledExecutor.scheduleWithFixedDelay`（手工重排版）却专门做了 `catch (Throwable)` 绝缘并支持 future 完成——证明平台已知裸 JDK 语义不满足需求，但绝缘只存在于一个包装器的一个方法，且默认路径（不走 `executeOn`）完全不设防。
- **风险**: 长时运行必然触发（周期任务×26 处×天级运行）。已核实依赖此设施的负载面：leader 选举（`AbstractPollingLeaderElector`，schedule 自续链）、服务注册刷新（`AutoRegistration.java:206`）、重试扫描（`RetryScannerImpl.java:96`、`AbstractBatchScanner.java:63`）、消息消费（`SysDaoMessageService`×3）、配置热刷新（`AbstractFileConfigSource.java:45`）、锁租约清理（`LocalResourceLockManager.java:105`）、WebSocket keepAlive（`JsonRpcWebSocketHandler.java:82`）。业务侧已被迫逐点自查：`SysDaoMessageService.java:295` 生产注释自证"周期任务抛 Throwable 即永久停摆"并手工 catch Exception（注意仍漏 Error）。
- **建议**: 在 `IScheduledExecutor`/`DefaultScheduledExecutor` 层提供带绝缘的周期调度（包装 command：`catch(Throwable)`→log + `future.completeExceptionally` 可选回调，保证下轮继续），或在 javadoc 强制声明"任务体必须自捕获 Throwable"并以 ArchUnit/checkstyle 规约抽查调用点；为周期 future 提供停摆告警挂接点（`whenComplete` 包装）。
- **失效面枚举**（F3，同族方法/入口逐一状态）：`DefaultScheduledExecutor.scheduleAtFixedRate/scheduleWithFixedDelay`（裸，本案主证）；`BindScheduledExecutor.scheduleAtFixedRate`（task 体经 `executor.execute` 落到 worker，command 异常不杀调度但完全不可观测，executor 拒绝时杀调度——见 C3-003）；`BindScheduledExecutor.scheduleWithFixedDelay`（绝缘不完整，见 C3-002）；`ExecutorHelper.scheduleWithRandomDelay`（有 catch(Throwable) 绝缘，C1 轮已立案其守卫缺陷）；直接使用 `ScheduledThreadPoolExecutor` 的独立调度器（nop-stream `CachingTableLookup`、`JobCoordinator`、`TaskManager` 等——越出本审计单元 A/B 面，仅登记）。逐点绝缘状态抽核：已包 catch 的调用点 `AutoRegistration.refreshRegistration`（catch Exception）、`AbstractFileConfigSource.refreshConfig`（catch Exception）、`SysDaoMessageService` 三入口（catch Exception）、`SessionLockRenewal.renewOnceSafe`（catch）；未包的调用点 `CliRunCommand.java:90`、`JsonRpcWebSocketHandler.sendKeepAlive`、`SysDaoNamingService.cleanup`、`RpcMessageHandler.checkTimeout`、`RpcTaskMonitor.checkTaskStatus`、`SocketClient.heartbeat`、`JobCoordinator`×2、`TaskManager` heartbeat、`TaskCheckpointWiring`、`StreamMetricsReporter`、`OpsJobManager`、`PollingJdbcMessageService`、`DBMessageService`×2、`TeamTaskSchedulerDaemon`、`ScheduledRecoveryManager`、`RetryScannerImpl`、`AbstractBatchScanner`、`LocalResourceLockManager.checkTimeout`（该实现本身近乎无抛点，风险最低）、`JdbcConfigSource`——全部 catch Exception 而非 Throwable，Error 一律漏防。
- **信心水平**: 确定
- **误报排除**: 不是"JDK 原语使用"类误报：JDK 语义本身不是缺陷，缺陷在平台设施（1）未声明该语义，（2）同一接口的两个实现/包装器行为不一致（绝缘 vs 不绝缘），（3）返回 Future 的异常信息无任何消费通道。测试代码不构成守卫（周期停摆无回归测试）。
- **复核状态**: 未复核
- **复核状态**: 已复核（C3 维度独立复核终裁）——**保留 P1**。证据亲核成立：`DefaultScheduledExecutor.java:126-135` 直通 JDK STPE、`IScheduledExecutor` 无任何 javadoc 警告；globalTimer 为 DefaultScheduledExecutor 单例（GlobalExecutors.java:41-54，daemon，core=4）。调用点清单抽验 13 处（超出要求的 6 处）全部属实，含 nop-sys 双生产注释自证（SysDaoNamingService.cleanup、SysDaoMessageService.processNonBroadcastEvent，后者注释原文“BindScheduledExecutor的周期任务抛Throwable即永久停摆”直接印证 R2 杀链语义修正）、CliRunCommand:90 全裸、JsonRpcWebSocketHandler 仅 catch IOException、SessionLockRenewal 仅 catch RuntimeException；全量重枚举实测生产周期站点 **29 处**（初判“26 处”为低估），R2 §1 表 30 行漏登 1 处（RpcPollTaskManager.java:119，platform 载体、pollOnce 有 catch(Exception) 绝缘）——F3 补全不改判级。与 WI 1.6 C8-001（nop-retry/tcc 扫描循环被拒/Error 停摆）为**同根因的设施面/使用方面**关系（本条=设施本体，C8-001=使用方扫描循环；cra-index 已注明“修复须平台级统一”），与 nop-job C4-003 同族——跨单元关联单列，见文末复核结论。P1 终裁维持：时序每步可信、停摆不可逆且不可感知、长时运行必然触发；不升 P0（多数业务关键站点已自捕获 Exception，残余触发条件为 Error/未捕获 RuntimeException/裸站点异常，非“默认用法即触发”）。现状：**已知未修复**（HEAD 8c13908bfd live code 亲核，分支无后续修复提交）。

### [nop-kernel-concurrency-C3-002] BindScheduledExecutor.scheduleWithFixedDelay 重排链在 worker 内调用 timer.schedule 未防护——失败后 future 永不完成（CL-005 升格立案）

- **文件**： `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/BindScheduledExecutor.java:88-116`（关键 107 行）
- **证据片段**:
  ```java
  executor.execute(() -> {
      if (future.isDone()) return;
      try {
          command.run();
      } catch (Throwable e) {
          LOG.error("nop.err.thread-pool.execute-fail",e);
          future.completeExceptionally(e);
          return;
      }
      // 重排发生在下游 executor 的 worker 线程内，此处不再有任何 try/catch
      schedule(command, delay, delay, unit, future, timerHandle);
  });
  ```
- **严重程度**: P2
- **并发场景**: 递归重排的任务体运行在绑定 executor（生产上为 `globalWorker`）的 worker 线程；下一轮 `timer.schedule` 调用与 timer 的 destroy/shutdown 并发。
- **触发时序**: T1：第 N 轮任务体在 `globalWorker` worker 上执行完毕；T2（并发）：容器关闭序列并发销毁 globalTimer（`DefaultScheduledExecutor.destroy()` → `shutdownNow`，或 `enabled=false` 后 executor=null）；T1 走到 107 行 `schedule(...)` → 90 行 `timer.schedule` 抛 `RejectedExecutionException`/`NopException(ERR_SERVICE_NOT_ACTIVE)`；该异常不在 99-105 的 try 内、也不在 94-111 的 try 内（那只覆盖 `executor.execute` 调用本身的同步拒绝）→ 异常逃逸到 worker 线程 → 返回的 `future` 永不完成（非正常、非异常完成），重排链静默死亡。
- **现状**: 这是 C2 轮登记的 CL-005 候选（"下游拒绝静默停摆"）。**本轮裁定：立案（升格为发现），但收缩触发面**——`executor.execute` 的同步拒绝路径实际已被 94-111 的 catch(Throwable) 正确兜住并完成 future，真正裸奔的是 worker 内重排时对 timer 的调用，可达触发条件为 timer destroy/shutdown 竞态窗口，故定 P2 而非 P1。
- **风险**: 关闭窗口内消费链死亡本属过渡态，但 future 无终态意味着 `doStop` 的 `cancel(false)`（如 `SysDaoMessageService.java:286-292`）无法区分"已停"与"卡死"；任何未来把该 future 用于关闭等待（`syncGet`）的调用方将永久挂起。设施契约层面："返回 future" ≠ "完成通知挂接 future"——本方法违反自身建立的重排链完成语义。
- **建议**: 将 107 行的重排调用纳入 try/catch(Throwable)，失败时 `future.completeExceptionally(e)`；或在 90 行 schedule 前检查 `future.isDone()` 快速返回。
- **失效面枚举**（F3）：`BindScheduledExecutor.scheduleWithFixedDelay`（本案）；同文件 `schedule(Callable,...)`（55-62 行，单次任务，timer 拒绝时异常同步抛给调用方，fail-fast 合理，不立案）；`DefaultScheduledExecutor.scheduleWithFixedDelay`（裸 JDK 语义，归并 C3-001 主条目）；`ExecutorHelper.scheduleWithRandomDelay`（重排在 task 体内且有 catch(Throwable) 兜底完成 future，绝缘完整——同族正确样本）；`IScheduledExecutor.scheduleWithRandomDelay` default（委托前者）。
- **信心水平**: 确定
- **误报排除**: 不是对"JDK scheduleAtFixedRate 异常抑制语义"的重复报告——该重排链是平台自建的 future 语义设施，缺陷是自建链上完成通知的断裂路径，与 JDK 语义无关；也不是 C2 的锁问题（无锁介入）。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。L107 重排调用逃逸路径亲核成立：worker 内重排最终落到 `timer.schedule`（L90），timer destroy/shutdownNow 时经 `checkEnabled` 抛 NopException(ERR_SERVICE_NOT_ACTIVE) 或 JDK REE，均不在 L99-105 与 L109-111 两个 try 覆盖范围内 → future 永不完成。谱系修正：C2 轮 CL-005（scheduleAtFixedRate 转发拒绝，L65-69）的直系升格是 C3-003；本条是同候选族内**新触发面**（重排拒绝），与 C2 CL-005 复核记录“scheduleWithFixedDelay 重写版正确处理了该面”（仅指 execute 拒绝面）不矛盾——CL-005 升格处置一致性确认。触发条件为关闭竞态窄窗口，P2 恰当。现状：**已知未修复**（前案：C2 轮 CL-005 登记）。

### [nop-kernel-concurrency-C3-003] BindScheduledExecutor.scheduleAtFixedRate：绑定 executor 拒绝即周期链静默死亡，返回 Future 无人消费

- **文件**： `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/BindScheduledExecutor.java:64-69`
- **证据片段**:
  ```java
  @Override
  public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
      return timer.scheduleAtFixedRate(() -> {
          executor.execute(command);
      }, initialDelay, period, unit);
  }
  ```
- **严重程度**: P2
- **并发场景**: timer 线程按 fixed-rate 触发包装 lambda；command 在绑定 executor（`executeOn` 指定，生产上均为 `globalWorker` 无界池）线程执行。
- **触发时序**: T1：绑定 executor 处于 shutdown 状态，或为有界池且队列满（`StandardThreadPoolExecutor.execute` 强制入队失败抛 `RejectedExecutionException("queue-full")`）；T2：timer 线程触发包装 lambda，`executor.execute(command)` 同步抛 REE；T3：REE 逃出 lambda → JDK fixed-rate 语义抑制全部后续执行 → 周期链永久死亡；返回的 ScheduledFuture 异常完成但无任何监听者 → 静默。
- **现状**: 与同文件 `scheduleWithFixedDelay`（71-86 行）形成族内不一致：重排版对命令异常做了绝缘+future 语义，fixed-rate 版完全直通。当前生产 `executeOn` 调用点（8 处）全部绑定无界 `globalWorker`，拒绝仅出现在关闭竞态窗口，故降 P2；一旦未来有调用点绑定有界池（`ThreadPoolConfig` 支持队列容量），负载上升必然触发。
- **风险**: 周期任务（心跳/选举探测/注册刷新）在 executor 拒绝后永久停摆且不可感知；另外 command 自身的异常落到了绑定池 worker 的 UncaughtExceptionHandler（仅日志），调用方完全无通道感知任务反复失败。
- **建议**: 与 `scheduleWithFixedDelay` 重排版对齐：包装 lambda 内 catch(Throwable) 并记录/回调；至少在 javadoc 声明"executor 拒绝将终止调度且不可观测"。
- **失效面枚举**（F3）：`BindScheduledExecutor.scheduleAtFixedRate`（本案）；`DefaultScheduledExecutor.scheduleAtFixedRate`（裸 JDK，command 异常直接杀调度——比本案更严，归并 C3-001）；`IScheduledExecutor.scheduleWithRandomDelay` default（每次重排有异常完成，不属停摆族）；`LocalResourceLockManager.init` 的 `scheduleWithFixedDelay`（任务体近无抛点，C3-001 已列）。
- **信心水平**: 确定
- **误报排除**: 不是重复 C3-001：本案专指 BindScheduledExecutor 内"同族两个周期方法绝缘不对称"这一 F3 失效面，且失效机制（executor 拒绝→JDK 抑制）与 C3-001（命令异常→JDK 抑制）不同。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2，附事实修正**。机制亲核成立：`scheduleAtFixedRate`（L65-69）包装 lambda 内 `executor.execute(command)` 同步 REE 逃逸 → JDK fixed-rate 抑制后续执行。**事实修正**：初判“生产 executeOn 调用点全部绑定无界 globalWorker”不准确——globalWorker 实为有界池（`StandardThreadPoolExecutor`，core=4/max=30/queue=TaskQueue(10000)，CommonConfigs global-worker.maxPoolSize 默认 30），`execute` 强制入队失败抛 REE(“nop.err.thread-pool-executor.queue-full”) 已亲核——拒绝面除关闭竞态外还含“30 线程满 + 10000 队列满”的极端过载，触发面略宽于初判，不改变“仅异常条件触发”的 P2 判级。CL-005 直系升格确认（与 C2 轮 L65-69 登记一致）。现状：**已知未修复**（前案：C2 轮 CL-005 登记）。

### [nop-kernel-concurrency-C3-004] BindScheduledExecutor.schedule/submit 返回 future 的取消不传播到底层任务

- **文件**： `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/BindScheduledExecutor.java:55-62, 128-135, 142-155`
- **证据片段**:
  ```java
  public <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
      CompletableFuture<V> future = new CompletableFuture<>();
      timer.schedule(() -> {
          executeOn(callable, future);   // 底层 timer Future 被丢弃，无 bindCancel
          return null;
      }, delay, unit);
      return future;
  }
  ...
  private <V> void executeOn(Callable<V> callable, CompletableFuture<V> future) {
      try {
          executor.execute(() -> {
              try {
                  future.complete(callable.call());   // 不检查 future.isDone()，取消后仍执行
  ```
- **严重程度**: P2
- **并发场景**: 调用方线程 cancel 返回的 CompletableFuture；timer 线程延迟触发；绑定 executor 线程执行任务体。
- **触发时序**: T1：`future = executor.schedule(heavyTask, 60, SECONDS)`；T2：t=10s 调用方 `future.cancel(true)`（future 进入 CancellationException 终态）；T3：t=60s timer 照常触发（底层 timer Future 已被丢弃、无 cancel 挂接）→ `executeOn` 不检查 `future.isDone()` → `executor.execute` 照常排队 → `callable.call()` 完整执行，副作用全部发生。同文件 `submit`（128-135）同构。
- **现状**: `Future.cancel` 的语义契约是"取消未开始的任务应阻止其执行"；本包装器返回的 future 与实际工作完全解耦。族内对照：`ExecutorHelper.submit/schedule` 通过 `FutureHelper.bindCancel(future, f)` 正确挂接取消（148/172/197 行）；同文件 `scheduleWithFixedDelay` 用 `future.isDone()` 守卫（91/96 行）——证明取消传播是本设施追求的语义，schedule/submit 两个入口漏配。
- **风险**: 取消后的任务照常执行：对选举探测、延迟补偿、定时快照类一次性任务，取消是调用方的安全机制，失效后产生不可撤销副作用或资源占用；取消也不能从 timer 队列移除待触发任务（延迟期间任务驻留）。
- **建议**: 保留底层 `Future` 并 `FutureHelper.bindCancel(future, f)`（对齐 `ExecutorHelper.schedule`）；`executeOn` 执行前增加 `if (future.isDone()) return;` 守卫（对齐同文件 91 行）。
- **失效面枚举**（F3）：`BindScheduledExecutor.schedule`（本案）、`BindScheduledExecutor.submit(Callable)`（本案同构）、`BindScheduledExecutor.submit(Runnable,result)`（委托前者）；`BindScheduledExecutor.execute(Runnable)`（163-165 行，Executor 契约无取消语义，不属违约）；`ExecutorHelper.submit×2/schedule`（已 bindCancel，同族正确样本）；`SyncThreadPoolExecutor.submit`（同步执行，future 返回前已终态，取消无意义，天然豁免）；`DefaultScheduledExecutor/DefaultThreadPoolExecutor.submit`（委托 ExecutorHelper，正确）。
- **信心水平**: 确定
- **误报排除**: CompletableFuture.cancel 本身不承诺停止底层工作，但本类是执行器设施，其同族方法（ExecutorHelper、同文件 fixedDelay）都实现了取消传播，构成事实契约；这是族内漏配（F3 修实例不修类别形态），不是原语误用误报。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。L55-62/129-135/142-155 亲核：底层 timer Future 丢弃、无 bindCancel、executeOn 无 isDone 守卫，取消传播缺失成立；同族对照（ExecutorHelper bindCancel、同文件 fixedDelay isDone 守卫）属实。F3 补充：生产调用方除 executeOn 8 处外，`RetryEngineImpl.java:407` 经 `getExecutor()`（L622-627，globalTimer.executeOn(globalWorker)）调用 Bind.schedule 调度重试延迟任务——取消不传播缺陷已有具体生产暴露点（补全不改判级）。现状：**已知未修复**。

### [nop-kernel-concurrency-C3-005] ExecutorHelper.throttleExecute 捕获 Exception 漏 Throwable——返回 future 永不完成，调用方永久挂起

- **文件**： `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ExecutorHelper.java:240-260`（关键 249 行）
- **证据片段**:
  ```java
  semaphore.acquireUninterruptibly();
  try {
      executor.execute(() -> {
          try {
              task.run();
              future.complete(null);
          } catch (Exception e) {          // ← Error 不在此列
              future.completeExceptionally(e);
          } finally {
              semaphore.release();
          }
      });
  } catch (Exception e) {
      semaphore.release();
      future.completeExceptionally(e);
  }
  ```
- **严重程度**: P2
- **并发场景**: 调用方线程提交并在返回的 future 上等待（`FutureHelper.syncGet(waitAll(futures))`）；任务体在 `globalWorker` 线程执行。
- **触发时序**: T1：`task.run()` 抛出 Error（`StackOverflowError`、页面模板编译触发的 `NoClassDefFoundError`、OOM 均为现实形态——任务体是 XPL 页面渲染，见下使用面）；T2：`catch (Exception)` 不匹配 → `future` 未被完成 → finally 仅释放信号量；T3：Error 沿 TPE worker 逃逸（`execute(Runnable)` 裸跑，worker 线程死亡重建）；T4：调用方 `FutureHelper.syncGet(FutureHelper.waitAll(futures))`（`PageProvider.java:107`、`PageProvider.java:141`、`WebPageExporter.java` 后续 join）永久阻塞——waitAll→allOf 无超时。
- **现状**: 同文件 `submit/schedule` 包装器（137/161/186 行）均为 `catch (Throwable e)` 并 `future.completeExceptionally`——族内唯一漏防点是 throttleExecute。使用面：`nop-web PageProvider.validateAllPages/renderPagesTo` 与 `WebPageExporter`（页面校验/导出工具路径），任务体为模板渲染，恰恰是 Error 高发区。
- **风险**: 一次 Error 将批量导出/校验变成永久挂死的进程操作；由于 future 永不终态，重试也无法进行。
- **建议**: 内外两层 catch 均改为 `catch (Throwable e)`（对齐同文件 submit/schedule）。
- **失效面枚举**（F3）：`ExecutorHelper.throttleExecute`（本案，漏防）；`ExecutorHelper.submit(Callable/Runnable)/schedule`（catch Throwable，正确）；`SyncThreadPoolExecutor.submit`（catch Throwable，正确）；`SequentialTaskExecutor.doTask`（catch Throwable，正确）；`BlockingSourceConsumeTask.run`（catch Exception，漏防→归并 C3-011）；`RateLimitExecutorImpl.throttle` → `FutureHelper.completeAfterTask`（catch Exception，根因在越界文件 nop-api-core FutureHelper.java:329，仅对照不立案，建议移交；其 in-scope 调用方因 finally 清理 promiseMap 可自愈，无永久后果）；nop-sys 调用点 catch Exception（越出本单元，已在 C3-001 失效面登记）。
- **信心水平**: 确定
- **误报排除**: 不是"异常都该吞"的风格问题：本设施契约是"返回的 future 反映任务终态"，族内 4/5 站点按 Throwable 实现；触发时序具体（模板渲染 Error），非泛泛理论。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。L249/L255 双层 catch(Exception) 亲核；使用面亲核：throttleExecute 生产调用 3 处（PageProvider.java:99/133、WebPageExporter.java:120），任务体为模板渲染（Error 高发），`FutureHelper.syncGet(FutureHelper.waitAll(futures))`（PageProvider L107/L141）无超时等待——永久挂死链完整。族内 4/5 站点 catch(Throwable) 对照属实。现状：**已知未修复**。

### [nop-kernel-concurrency-C3-006] ExecutorHelper.throttleExecute 用 acquireUninterruptibly 阻塞调用线程——无超时、无中断响应（F5）

- **文件**： `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ExecutorHelper.java:243`
- **证据片段**:
  ```java
  public static CompletableFuture<Void> throttleExecute(Executor executor, Semaphore semaphore, Runnable task) {
      CompletableFuture<Void> future = new CompletableFuture<>();

      semaphore.acquireUninterruptibly();   // 无超时、忽略中断
      try {
  ```
- **严重程度**: P2
- **并发场景**: 并发导出/校验调用方线程（如 `PageProvider.renderPagesTo` 的 `options.getThreadCount()` 信号量，或 `validateAllPages` 的 `CFG_WEB_PAGE_VALIDATION_THREAD_COUNT`）在许可耗尽时阻塞在提交点。
- **触发时序**: T1：semaphore 配置为 n，n 个任务在慢渲染中持许可；T2：第 n+1 个调用方线程进入 `throttleExecute`，`acquireUninterruptibly()` 无限等待；T3：外部对该调用方线程 interrupt（如上层超时管理器、`Future.cancel(true)` 对调用方自身的取消）——中断被吞，标志位置位但线程继续等；T4：仅当某个持有者完成任务后才能前进；若某持有者因 C3-005 永不完成，则所有等待线程永久不可中断挂死。
- **现状**: 族内对照：`LimitedExecutor.execute` 用可中断的 `semaphore.acquire()`（中断→NopException），`FutureHelper.executeWithThrottling` 同为可中断 acquire——throttleExecute 是同族唯一 uninterruptible 站点。与 C3-005 叠加时后果从"等待"升级为"不可杀的挂死"。
- **风险**: 调用方线程失去取消能力；上层超时/取消机制全部失效；挂死线程不可观测（无超时点、无等待指标）。
- **建议**: 改为 `semaphore.acquire()` + InterruptedException 转 `future.completeExceptionally`（对齐 LimitedExecutor），或提供带超时的 `tryAcquire` 重载。
- **失效面枚举**（F3）：`ExecutorHelper.throttleExecute`（uninterruptible，本案）；`LimitedExecutor.execute`（interruptible，正确——但见 C3-007 其释放路径缺陷）；`FutureHelper.executeWithThrottling`（interruptible，越界文件仅对照）；`OverflowBlockingQueue.send` BLOCK_WAIT 分支（`queue.put` 可中断，正确）。
- **信心水平**: 确定
- **误报排除**: 不是"阻塞提交"设计本身的问题（节流必然阻塞），问题是无超时+无中断响应两个 F5 要素同时缺失，且与同族实现不一致。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。L243 `acquireUninterruptibly` 亲核；族内对照（LimitedExecutor 可中断 acquire、FutureHelper.executeWithThrottling 可中断）亲核属实；与 C3-005 叠加升级为不可杀挂死的组合面成立。现状：**已知未修复**。

### [nop-kernel-concurrency-C3-007] LimitedExecutor.execute 中断路径幻影 release——信号量许可被永久虚增，并发上限击穿

- **文件**： `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/LimitedExecutor.java:22-42`
- **证据片段**:
  ```java
  public void execute(Runnable command) {
      boolean submitted = false;
      try {
          semaphore.acquire();
          executor.execute(() -> { try { command.run(); } finally { semaphore.release(); } });
          submitted = true;
      } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw NopException.adapt(e);
      } finally {
          if (!submitted) {
              semaphore.release();      // acquire() 本身被中断时也执行 → 未获得先释放
          }
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 调用方线程在 `semaphore.acquire()` 阻塞等待许可时被中断（取消/超时管理器触发）。
- **触发时序**: T1：n=1 的 LimitedExecutor，唯一许可被长任务持有；T2：调用方 B 线程 `execute()` 阻塞于 25 行 `acquire()`；T3：B 被中断 → `acquire()` 抛 InterruptedException **且未获得许可** → `submitted==false` → finally 39 行 `semaphore.release()` → 许可数变为 2；T4：此后最多 2 个任务并发执行——上限被永久击穿；反复中断（如调用方对超时请求的批量取消）→ 许可数单调上涨 → 限流完全失效。
- **现状**: 释放逻辑意图是回滚 `executor.execute` 抛 REE 时的占用，但未区分"acquire 成功后失败"与"acquire 本身失败"两种路径。对照组：`FutureHelper.executeWithThrottling`（nop-api-core，越界仅对照）在 InterruptedException 分支同样无条件 release，存在同构缺陷，建议随本条一并移交核查。
- **风险**: 限流器失效属静默劣化：无报错、无日志，隔离语义（限制对下游的并发压力）消失；对以 LimitedExecutor 保护弱资源（连接、外部 API）的使用方即过载保护失效。当前生产代码 0 调用方，故 P2。
- **建议**：将 release 移入"acquire 成功之后"的作用域：`semaphore.acquire(); try { executor.execute(...); } catch (REE e) { semaphore.release(); throw e; }`，或使用 tryAcquire/acquired 标志。
- **失效面枚举**（F3）：`LimitedExecutor.execute`（本案）；`ExecutorHelper.throttleExecute`（acquireUninterruptibly，中断路径不可达，天然豁免）；`FutureHelper.executeWithThrottling`（同构缺陷，越界文件，移交）；`HighWatermarkSemaphore`/`DefaultSemaphore`（原语本身无 acquire/release 配对逻辑，豁免）。
- **信心水平**: 确定
- **误报排除**: 不是"finally 释放"的常规模式误报——JDK `Semaphore.release()` 无上限、允许超发，未获得先释放必然虚增；触发时序具体且可复现（单测：n=1 + interrupt 即可红）。维度归属说明：本质也是 C8 许可泄漏形态，因触发路径是取消/中断语义（本维度重点）而立在本维度。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。幻影 release 亲核成立：`acquire()` 抛 InterruptedException 时未获许可，finally L38-40 仍 release → 许可虚增、上限击穿。移交项核实：`FutureHelper.executeWithThrottling`（api-core L548-552）InterruptedException 分支同样未获许可先 release，同构缺陷属实，移交有效。零生产调用方亲核证实。现状：**已知未修复**。

### [nop-kernel-concurrency-C3-008] SequentialTaskExecutor.execute 忽略队列拒绝——任务静默丢弃，overflow 策略对 execute 路径不生效

- **文件**： `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/SequentialTaskExecutor.java:79-83`；`impl/OverflowBlockingQueue.java:74-77, 115-134`
- **证据片段**:
  ```java
  @Override
  public void execute(Runnable command) {
      queue.offer(command);          // 返回值被忽略
      onMessageQueued();
  }
  // OverflowBlockingQueue.offer：裸透传，无策略
  @Override
  public boolean offer(T o) { return queue.offer(o); }
  ```
- **严重程度**: P2
- **并发场景**: 多生产者线程向 actor 队列提交任务；队列（默认 `ArrayBlockingQueue(capacity)`）满。
- **触发时序**: T1：capacity=500 的 SequentialTaskExecutor 队列打满（消费者 suspend 或任务积压）；T2：生产者调用 `execute(cmd)` → `ArrayBlockingQueue.offer` 返回 false → **返回值被忽略**；T3：cmd 静默消失——`java.util.concurrent.Executor` 契约要求"要么执行要么抛 RejectedExecutionException"，调用方拿到的却是正常返回；即便构造时选择 `DROP_ELDEST` 策略，该策略也只在 `send()`（115-134 行）实现，`execute` 路径完全不生效（既不丢最老任务、也不入队）。
- **现状**: 类为平台 actor 语义设施（`IBlockingSinkNotifier` 实现者），当前生产代码 0 调用方，P2。接口 `IBlockingQueue.offer` javadoc 明确"返回 false 表示 item 未入队，由外部调用者负责释放"——本调用方正是违约的外部调用者。
- **风险**: 未来使用方（状态机/消息 actor 类场景）在过载时静默丢任务：被丢弃的多为"flush/commit/ack"类关键收尾任务，丢失即状态不一致（F2 半个成功形态），且无任何日志与指标。
- **建议**: `execute` 中检查 offer 返回值，false 时按 `QueueOverflowPolicy` 处理或抛 `RejectedExecutionException`；至少 `LOG.warn` 计数。
- **失效面枚举**（F3）：`SequentialTaskExecutor.execute`（本案）；同文件 `clearQueue`（清队列不通知任务方，语义可接受）；`OverflowBlockingQueue.send`（策略实现正确，DROP 分支有 onRemoveItem 回收）；`OverflowBlockingQueue.offer/offer(timeout)`（裸透传属接口设计，风险集中在忽略返回值的调用点——全库 grep 仅本案一处）。
- **信心水平**: 确定
- **误报排除**: 不是"Executor 可以丢弃任务"的契约争议——JDK Executor 契约明确要求 reject-by-exception；亦非 C8 纯背压问题：缺陷点是提交链契约断裂（静默丢弃），策略未接线是直接根因。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。L81 offer 返回值忽略亲核；`IBlockingQueue.offer` javadoc“返回 false 表示未入队、调用者负责释放”亲核；策略仅 `send()` 实现亲核（DROP_ELDEST/DROP_NEWEST/BLOCK_WAIT/ERR_QUEUE_FULL 全在 send）；零生产调用方证实。现状：**已知未修复**。

### [nop-kernel-concurrency-C3-009] IAsyncCache.getAsync/getAllAsync 跨配置语义分叉：async 路径 miss 不加载、getAllAsync 以 k->null 违反接口契约

- **文件**： `nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/LocalCache.java:280-299`；`IAsyncCache.java:39-93`
- **证据片段**:
  ```java
  public @Nullable CompletionStage<V> getAsync(@NonNull K key) {
      if (asyncCache != null)
          return asyncCache.getIfPresent(key);              // miss → null，不加载
      return FutureHelper.futureCall(() -> get(key));       // 非异步 → 同步阻塞加载后包装
  }
  ...
  public @NonNull CompletionStage<Map<K, V>> getAllAsync(Collection<? extends K> keys) {
      if (asyncCache != null)
          return asyncCache.getAll(keys, k -> null);        // 永不加载，缺失键直接缺席
      return FutureHelper.futureCall(() -> getAll(keys));   // 非异步 → 全量加载
  ```
- **严重程度**: P2
- **并发场景**: 同一 `IAsyncCache` 接口的调用方代码在两种 `CacheConfig`（async=true/false）下运行；配置热刷新（`CacheConfig.isCacheTypeChanged`）可在运行期翻转 async 标志并重建缓存。
- **触发时序**: T1：调用方按非异步缓存编写 `cache.getAsync(key)`——miss 时同步加载并返回完成 future（非空）；T2：运维将 `nop.cache.{name}.async` 置 true，`refreshConfig` 重建为 AsyncCache；T3：同一调用点 miss 时返回 **null**（接口 javadoc 明确 null=无映射）→ 调用方 `thenApply(userContext -> ...)` 在 null 上 NPE 或走错分支。`getAllAsync` 同理：async 路径 `k -> null` 使缺失键静默缺席，而 `IAsyncCache.getAllAsync` javadoc（72-93 行）明确承诺 "creating or retrieving those values if necessary ... combined with newly loaded entries"。
- **现状**: 当前生产代码未发现 `CacheConfig.async=true` 的使用点（全库 grep），缺陷处于潜伏态；但 `CacheConfig` 是 `@ConfigBean`，任何配置驱动的开启都会激活分叉。`AbstractUserContextCache.java:107`（用户会话缓存 `getAsync`）是潜在暴露面。
- **风险**: 调用方契约随部署配置漂移；async 分支还使"加载"永远不发生——调用方若依赖 getAsync 触发加载，则缓存永远 miss，功能静默失效。
- **建议**: async 路径 getAsync 改为 `asyncCache.get(key, k -> null)` 之外的显式语义：或按接口契约实现加载（`asyncCache.getAll` 用真实 loader），或在 javadoc 按 config 分支显式声明语义差异并让 `getAllAsync` async 路径改为加载语义。
- **失效面枚举**（F3）：`LocalCache.getAsync/getAllAsync`（本案）；`LocalCache.computeIfAbsentAsync`（两路径语义一致：均"计算并放入"，且 async 路径有 Caffeine 并发去重，豁免）；`MapCache` 各 async 方法（统一 futureCall 包装同步操作，无分叉，豁免）；`ISyncCache` default async 方法（同 MapCache，豁免）；`EhcacheCache`（继承 ISyncCache default，豁免）。
- **信心水平**: 确定（分叉事实）；很可能（运行期触发链依赖配置翻转）
- **误报排除**: 不是"Caffeine getIfPresent 本来就不加载"的原语误报——缺陷在 LocalCache 对同一接口方法的两条实现路径语义不一致，且 getAllAsync async 路径直接违反本仓库接口 javadoc 的书面契约。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。分叉亲核成立：L280-284 getAsync（async 路径 miss 返 null 不加载 / 非异步路径阻塞加载）、L295-299 getAllAsync（async 路径 `k->null` 永不加载）；`IAsyncCache.getAllAsync` javadoc“creating or retrieving those values if necessary”契约亲核——async 路径直接违约。零生产开启亲核：`CacheConfig.async()` 工厂（L121-125）全库无调用，仅配置驱动激活路径（@ConfigBean）。现状：**已知未修复**（潜伏态）。

### [nop-kernel-concurrency-C3-010] LocalCache 异步加载/刷新 executor 固化为 Caffeine 默认 ForkJoinPool.commonPool 且不可配置

- **文件**： `nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/LocalCache.java:80-136`；`CacheConfig.java:20-52`；`ICacheLoader.java:97-109, 181-193`
- **证据片段**:
  ```java
  private void buildCache(CacheConfig config, ICacheLoader<K, V> loader) {
      Caffeine builder = Caffeine.newBuilder();
      // ... maximumSize/expire/weak/weight/refreshAfterWrite 逐项接线
      builder.recordStats();
      // 全程未调用 builder.executor(...)  → Caffeine 默认 ForkJoinPool.commonPool()
      if (config.isAsync()) {
          if (loader != null) {
              AsyncLoadingCache<K, V> cache = builder.buildAsync(createCacheLoader(loader));
  ```
- **严重程度**: P2
- **并发场景**: async=true 的缓存：`ICacheLoader.asyncLoad/asyncReload` 把加载任务投递到 Caffeine 注入的 executor（即 commonPool）；`refreshAfterWrite` 的异步刷新同样落在 commonPool。
- **触发时序**: T1：某 async 缓存的 loader 为阻塞型加载（本平台典型：模型/配置解析含磁盘 I/O 与 XML 解析）；T2：批量 miss（重启预热、批量导出）→ 大量加载任务涌入 commonPool；T3：commonPool 工作线程被阻塞 I/O 占满 → 平台内所有共用 commonPool 的设施（并行流、`CompletableFuture.supplyAsync` 默认路径、Caffeine 其他实例）排队劣化；T4：无任何配置可将其迁出。
- **现状**: `CacheConfig` 无 executor 属性（字段清单 23-38 行核实），`LocalCache.buildCache` 未调用 `builder.executor(...)`——设施层面既未固化合理默认，也未留配置口。当前 async=true 生产使用为 0（潜伏态），故 P2。
- **风险**: 一旦 async 缓存被启用（该开关正是本接口"异步加载链"的核心能力），阻塞加载与全局 commonPool 相互放大，形成跨设施连带劣化；另 commonPool 的守护线程特性使加载失败仅体现在 future 上，与 C3-009 叠加后更难归因。
- **建议**: `CacheConfig` 增加 executor 配置项；`LocalCache` 默认接线 `GlobalExecutors.globalWorker()`（或 `cachedThreadPool()`），将 commonPool 留给 CPU 型短任务。
- **失效面枚举**（F3）：`LocalCache.buildCache` 两个 buildAsync 分支（本案）；`LocalCache` 同步分支（无异步载体，豁免）；`ICacheLoader.asyncLoad/asyncLoadAll/asyncReload` default（正确使用了传入 executor——executor 参数本身即 Caffeine 默认 commonPool，责任在装配层）；`CaffeineCacheLoader`（透传，豁免）；`ResourceLoadingCache.createCache`（非 async，豁免）。
- **信心水平**: 确定（装配事实）；很可能（危害链需 async 启用）
- **误报排除**: 不是"supplyAsync 默认池"类业务侧误报——本案主体是平台缓存设施的装配缺口（无法配置），C3 维度步骤 2 明确要求核对"与模块声明的执行模型是否一致"，`ICacheLoader`/`CacheConfig` 均未声明 commonPool 执行模型。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。`buildCache`（L80-136）全程未调用 `builder.executor(...)` 亲核；`CacheConfig` 字段清单无 executor 项亲核。R2 C3-019 对本条 F3 中“同步分支豁免”误判的修正（refreshAfterWrite>0 时同步 LoadingCache 同样落 commonPool）经复核确认成立。现状：**已知未修复**（潜伏态）。

### [nop-kernel-concurrency-C3-011] BlockingSourceConsumeTask 消费循环可因中断残留或 Error 永久死亡，且死亡不可感知

- **文件**： `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/batch/BlockingSourceConsumeTask.java:48-76`；`batch/AbstractBatchProcessService.java:48-61`
- **证据片段**:
  ```java
  public void run() {
      do {
          ...
          try {
              source.drainTo(items, batchSize, config.getMinWaitMillis(), config.getMaxWaitMillis());
          } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw NopException.adapt(e);        // ← 循环永久终止
          }
          ...
          try {
              filterMessages(items);
              if (!items.isEmpty()) consumer.acceptList(items);
          } catch (Exception e) { ... }           // ← Error 逃逸，同样终止循环
      } while (!isCancelled());
  }
  // AbstractBatchProcessService.start():
  for (int i = 0; i < poolSize; i++) {
      executor.submit(task, null);                    // 返回的 future 全部丢弃
  }
  ```
- **严重程度**: P2
- **并发场景**: 消费循环运行在专用 `DefaultThreadPoolExecutor`（poolSize 线程，每线程跑同一 Runnable 的一个实例）上；生产者线程调用 `getQueue().send()`（BLOCK_WAIT 时 `queue.put` 阻塞）。
- **触发时序**: T1（中断残留链）：同池先行任务在 worker 线程留下置位的中断标志（用户任务捕获 InterruptedException 后未恢复标志或未清除即返回——平台不强制卫生）；T2：消费循环被调度到该 worker → `drainTo` 内 `poll` 立即抛 InterruptedException → `throw NopException.adapt(e)` → 循环退出；T3：submit 返回的 FutureTask 已异常完成，但引用被丢弃 → 无人感知；T4：队列积压至 capacity（默认 500）→ BLOCK_WAIT 下 `send()` 永久阻塞 → 生产者（如 `AuditServiceImpl.saveAudit`，审计写入路径）挂死。Error 路径（T1'：`doProcess` 抛 Error）同样逃逸 catch(Exception) 终止循环。
- **现状**: `consumer.acceptList` 的 catch(Exception) 表明设计意图是"业务失败不杀循环"，但 drainTo 中断路径与 Error 路径都绕过了该保护；消费侧死亡不可观测（无健康钩子、future 被丢弃）。
- **风险**: 唯一已知子类为 `AuditServiceImpl`（审计日志落库）：消费死亡 → 登录/操作审计提交全部阻塞 → 平台核心写路径连锁挂死（F5 挂死形态 + F2 静默失败）。
- **建议**: 循环体最外层包 catch(Throwable)+日志并 continue（仅 cancel 退出）；drainTo 中断时检查 `isCancelled()` 决定退出或继续；`AbstractBatchProcessService` 保存 submit future 并在死亡时告警/重启任务。
- **失效面枚举**（F3）：`BlockingSourceConsumeTask.run`（本案）；`AbstractBatchProcessService.start` 的 submit 循环（丢弃 future，本案共因）；`ExecutorHelper.submit`（提交层异常完成正常，非共因）；`SequentialTaskExecutor.doTask`（同族 actor 循环用 catch(Throwable)+继续，正确样本）；nop-stream 消费循环（越出本单元，仅登记）。
- **信心水平**: 确定（死亡路径）；很可能（生产触发依赖中断残留/Error）
- **误报排除**: 不是"InterruptedException 应当传播"的通用争议——对常驻消费循环，中断是输入而非终态指令（`isCancelled` 才是），且设计已对业务异常选择存活；测试代码不构成守卫（无消费死亡回归测试）。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。死亡路径亲核成立：drainTo 中断 → `throw NopException.adapt(e)` 退出循环；`filterMessages/acceptList` 的 catch(Exception) 漏 Error；`AbstractBatchProcessService.start` L58-60 submit future 全部丢弃。唯一子类 `AuditServiceImpl` 亲核证实，审计写路径连锁挂死风险面成立。现状：**已知未修复**。

### [nop-kernel-concurrency-C3-012] AbstractBatchProcessService.destroy 直接丢弃队列未处理消息——关停路径"半个成功"

- **文件**： `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/batch/AbstractBatchProcessService.java:71-78`
- **证据片段**:
  ```java
  @PreDestroy
  public void destroy() {
      if (task != null)
          task.cancel();          // 仅置位，不等在飞批处理完成

      if (executor != null)
          executor.destroy();     // shutdown() 后队列中未处理消息全部废弃
  }
  ```
- **严重程度**: P2
- **并发场景**: 容器关闭线程执行 @PreDestroy；消费线程可能正在 `doProcess` 一批消息的中途；队列中最多 capacity（默认 500）条已接收消息待处理。
- **触发时序**: T1：业务持续 `send()`，队列存有 N 条待处理审计请求；T2：容器关闭 → `task.cancel()` 置位（消费循环在当前 drainTo 返回后才退出）；T3：`executor.destroy()` → `shutdown()`（非 shutdownNow，排队任务会跑完——但循环任务因 cancel 标志在下一轮退出）→ 队列中剩余消息永不被处理；T4：进程退出，N 条已确认接收（send 正常返回）的消息静默丢失，无任何残留标记、无 flush 钩子。
- **现状**: 类提供 `isAllProcessed()`（队列空+在飞 0）探测点，但 destroy 不使用它——关停语义是"放弃"而非"排空"，与"接收即承诺处理"的批量服务契约不符（F2：部分成功后失败，无恢复语义）。
- **风险**: 对 `AuditServiceImpl` 即关停窗口丢失至多 500 条审计记录（合规敏感）；每 次重启都可能在无感中发生。
- **建议**: destroy 中先停止接收、再 `FutureHelper.waitUntil(this::isAllProcessed, drainTimeout)` 有限等待，超时记录丢弃清单（告警日志），最后再销毁 executor。
- **失效面枚举**（F3）：`AbstractBatchProcessService.destroy`（本案）；`BlockingSourceConsumeTask.cancel`（Cancellable 置位正确，非共因）；`DefaultThreadPoolExecutor.destroy`→`shutdown`（优雅关停正确，非共因）；`LocalResourceLockManager.destroy`（cancel 定时清理，无待处理数据，豁免）；`SysDaoMessageService.doStop`（cancel 周期任务，消息以 DB 为准，豁免）。
- **信心水平**: 确定
- **误报排除**: 不是"destroy 必须无限等待"的过度要求：本案指出的是"无排空尝试+无丢弃记录"两个都缺失；有界等待+丢弃可观测即满足 F2 最低语义。审计丢失为默认关停路径必经，非罕见条件。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。`destroy`（L71-78）cancel 仅置位 + `executor.destroy()` 亲核为 `shutdown()`（DefaultThreadPoolExecutor L91-93，非 shutdownNow），取消后消费循环退出、队列残余消息（默认容量 500）无排空无记录亲核成立；`isAllProcessed()` 探测点存在而未被使用亲核属实。现状：**已知未修复**。

### [nop-kernel-concurrency-C3-013] A 面异步设施上下文卫生不一致：submit 包装器 detach vs execute 直通；池线程 getOrCreateContext 永久驻留（理论风险，未具体化）

- **文件**： `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ExecutorHelper.java:141-144,165-168,190-193`；`DefaultThreadPoolExecutor.java:96-98`；对照 `nop-api-core/.../BaseContextProvider.java:36-42`（不立案）；`nop-core/.../TenantAwareResourceLoadingCache.java:52-66`
- **证据片段**:
  ```java
  // ExecutorHelper.submit 包装器：finally 中 detach（卫生），但不捕获/重绑调用方 IContext（传播）
  } finally {
      // 确保ThreadLocal上的IContext被清除
      ContextProvider.instance().detachContext();
  }
  // DefaultThreadPoolExecutor.execute：直通裸 TPE.execute —— 无包装、无 detach 卫生
  public void execute(Runnable command) { executor.execute(command); }
  ```
- **严重程度**: P2（理论风险，未具体化到确定调用方）
- **并发场景**: 平台上下文为 ThreadLocal（`BaseContextProvider.t_context`）；池化 worker 线程跨任务复用。
- **触发时序**（机制链，非确定业务时序）：T1：任务 A 在 worker W 上执行，其内部调用链触及 `ContextProvider.getOrCreateContext()`（如 `FutureHelper.syncGet` → 174 行 `getOrCreateContext().syncGet(future)`）→ `BaseContextProvider.getOrCreateContext()` 在 context==null 时 **new 并 attach** → W 从此永久挂着一个孤儿 context（仅 ExecutorHelper.submit/schedule 包装器 finally detach 可清除，raw execute 提交的任务无此卫生）；T2：后续任务 B 在 W 上执行 → `currentContext()!=null`（读到 A 的孤儿 context）→ `TenantAwareResourceLoadingCache.getCache`（52-66 行按 `currentTenantId()` 选路）等上下文敏感判定走错分支（租户非空→错租户缓存；或非空判定→"在请求中"误判）。未能指认生产代码中确定的 raw-execute + getOrCreateContext 组合调用点，故按规则降 P2 并标注。
- **现状**: 平台传播模型是显式传播（`CallableWithContext`/`thenOnContext`/`runWithProxyContext`），ExecutorHelper 不自动传播属**已声明边界**（非缺陷）；缺陷点是**卫生不一致**：submit 路径 detach、execute 路径不 detach，以及 `getOrCreateContext` 的 create-and-attach 语义在池线程上的驻留效应。
- **风险**: 上下文串号类问题（错租户缓存、错误用户归属）一旦发生极难排查；当前被 submit 包装器的普遍使用部分掩盖。
- **建议**: `DefaultThreadPoolExecutor/DefaultScheduledExecutor.execute` 增加与 submit 一致的 finally detach 卫生（包装 command）；或 `getOrCreateContext` 提供"只读不创建"的内部变体供 `syncGet` 等等待点使用。
- **失效面枚举**（F3）：`ExecutorHelper.submit×2/schedule`（有 detach，正确样本）；`DefaultThreadPoolExecutor.execute`、`DefaultScheduledExecutor.execute`（139-141 行 checkEnabled 后直通，均无卫生，本案）；`BindScheduledExecutor.execute`（直通，本案）；`SyncThreadPoolExecutor.execute`（ContinuationExecutor 同线程执行，无跨线程边界，豁免）；`GlobalExecutors.cachedThreadPool()/globalWorker()`（DefaultThreadPoolExecutor 实例，继承本案缺陷）。
- **信心水平**: 很可能（机制确定，业务触发点未具体化）
- **误报排除**: 不报"submit 不自动传播 IContext"（平台显式传播为既定设计，误报校准范围）；本案仅立"卫生不对称+孤儿 context 驻留"这一可验证机制，且给出具体连锁终点（TenantAware 缓存选路）。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2（理论风险标注维持）**。机制链亲核成立：`BaseContextProvider.getOrCreateContext` null 即 new+attach；`TenantAwareResourceLoadingCache.getCache`（L52-66）按 `currentTenantId()` 选路；raw execute 无卫生站点（DefaultThreadPoolExecutor.execute L96-98、BindScheduledExecutor.execute L163-165）。未能钉定生产组合调用点的降档标注维持（诚实标注恰当）；R2 §5 矩阵与 CL-022 具名清单（DefaultTaskExecutionQueue L206 raw execute / BlockingSourceConsumeTask / SequentialTaskExecutor.doTask / globalTimer 裸 JDK 站点）逐点核实成立。与 C2 轮 CL-019（ExecutionContextImpl 回调丢失，已移交 C1）**无登记冲突**：两者机制不同（本条=ThreadLocal 孤儿 context 池线程驻留，CL-019=完成状态机窗口/回调丢失），CL-019 机制经亲核仍在 live code（ExecutionContextImpl L83-93），维持 C1 归属。现状：**已知未修复**。

### [nop-kernel-concurrency-C3-014] IBlockingSource.drainTo(c,max,minWait,maxWait) 恰好取满 maxElements 时返回值少 1

- **文件**： `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/IBlockingSource.java:78-113`
- **证据片段**:
  ```java
  for (i = 0; i < maxElements; i++) {
      T item = poll(i == 0 ? maxWait : leftTimeout, TimeUnit.MILLISECONDS);
      if (item == null) break;
      c.add(item);
      if (i == maxElements - 1) break;    // 取到第 maxElements 条后直接 break，i 停在 maxElements-1
      i += drainTo(c, maxElements - i);
      ...
  }
  return i;                                // 满批时返回 maxElements-1
  ```
- **严重程度**: P3
- **并发场景**: 消费者线程批量拉取；生产者持续投递使队列始终满足满批条件。
- **触发时序**: T1：`drainTo(list, 10, ...)` 且队列中 ≥10 条；T2：循环逐条 poll 至 i==9 时 c 已含 10 条 → `break` → `return 9`；调用方按返回值判定"未满批"进而做等待/让步决策时行为失真。maxElements=1 且有数据时返回 0（更严重失真）。
- **现状**: 当前唯一调用方 `BlockingSourceConsumeTask.run` 使用 `items.size()` 而非返回值，未受影响；接口 default 方法的返回契约（"返回时要么超时要么取满 maxElements"）与实现背离，属潜伏契约缺陷。
- **风险**: 任何未来按返回值计数的调用方（限流计数、确认计数）将系统性少计 1。
- **建议**: `return c.size() - 初始size` 或满批时返回 maxElements；补一条满批单测。
- **失效面枚举**（F3）：`IBlockingSource.drainTo(4参)`（本案）；`IBlockingSource.drainTo(2参)`（返回值正确，豁免）；`IBlockingSource.takeMulti`（返回 n+1，正确，豁免）；`OverflowBlockingQueue.drainTo`（透传 JDK，正确，豁免）。
- **信心水平**: 确定
- **误报排除**: 非风格问题：返回值与已收集条数可实测不一致（`c.size()` 对照即可红）；因现网无受害调用方判 P3。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P3，附证据修正与补漏**。原证据的具体触发场景**证伪**：队列初始 ≥maxElements 时走 L83-85 早返回（2 参 drainTo 满批返回 maxElements），返回值正确——初判场景“T1 队列中 ≥10 条”不成立。**替代证据成立（逐行 trace）**：初始 drain n0<max 后循环内取满时——第 maxElements 个条目经 poll 进入（L99）后 L100-101 break → `return i`（=maxElements-1）而 `c.size()`=n0+maxElements，返回值少计；`maxElements=1` 且条目在等待期到达时 return 0 实收 1（更严重失真）。**补漏（F3）**：循环内 L102 `drainTo(c, maxElements - i)` 未扣除初始 n0，批量续取成功时 `c.size()` 可达 n0+maxElements+1，**超出 maxElements 上界**（唯一调用方 BlockingSourceConsumeTask 以 `items.size()` 全量处理，实收可超 batchSize，现网无害）——返回值失真与集合越界两 facets 同为 P3 潜伏契约缺陷，判级不变。现状：**已知未修复**。

---

## 维度集外线索（C8 形态）

本轮无登记。说明：候选形态已逐一判定归属——`LimitedExecutor` 幻影 release（CL 候选）因触发路径为取消/中断语义，按本维度 C3-007 立案并在条目内注明 C8 双重归属；`throttleExecute` 的 uninterruptible 阻塞属 F5 取消/超时语义（C3-006），非背压形态；`OverflowBlockingQueue.send` 的 DROP 策略实现与声明一致，不构成过载行为缺陷。CL-007 起编号保留未被占用。

---

## 移交其他维度的线索（非 C3，不立案展开）

1. **[移交 C6]** `ResourceCacheEntry.clear()`（`ResourceCacheEntry.java:111-124`）与 `ResourceLoadingCache.remove`（163-169 行）：在持有者线程仍在使用旧值时同步 destroy 旧对象（use-after-destroy 形态）；`LocalCache.buildCache` 的 `destroyOnRemove` removal listener 同理。属资源生命周期维度。
2. **[移交 C1/C10]** `DefaultThreadPoolExecutor.submit` 无 `checkEnabled`/null 守卫（未 init 时 NPE），与 `DefaultScheduledExecutor`（`ERR_SERVICE_NOT_ACTIVE`）守卫不对称。
3. **[移交 C8]** `OverflowBlockingQueue.send` BLOCK_WAIT 分支的 `queue.put` 无超时（生产者无界等待）——策略本身已声明，是否需要超时变体由背压维度评估。

---

## 本轮统计与自评

| 严重程度 | 数量 | 条目 |
|---|---|---|
| P0 | 0 | — |
| P1 | 1 | C3-001 |
| P2 | 12 | C3-002 ~ C3-013 |
| P3 | 1 | C3-014 |
| 合计 | 14 | CL 线索 0 条 |

**盲区自评**：
- 未逐一通读 26 处周期任务调用点的任务体（绝缘状态以抽核+grep 为准，未抽核位点已在 C3-001 失效面中如实标注）。
- nop-stream 自建的独立调度器（`CachingTableLookup` 等）不在本审计单元 A/B 面内，其与平台设施的同族缺陷仅登记未展开。
- `ContinuationExecutor`、`Cancellable`、`StandardThreadPoolExecutor`（Tomcat 式队列/线程更新逻辑）、`LocalResourceLockManager` 租约竞态（latch 锁保护已到位）经核对未发现本维度缺陷，未立案；`CycleSynchronizer.resolvePromise` 回调链问题属 C1 轮已立案同族（回调在锁内执行），且该类当前无生产调用方，本轮不重复立案。
- 上下文串号链（C3-013）未能在生产代码中钉定确定触发调用点，已按规则降级标注；复核阶段建议定向排查 raw `execute(` 提交与 `getOrCreateContext`/`syncGet` 的组合调用面。

---

## 维度复核结论

> 复核人：C3 维度独立复核子代理（与初审/深挖代理相互独立，怀疑姿态逐条亲核）。复核基线 HEAD `8c13908bfd`；历史对照面已解除盲审（cra-index、C2 轮 CL-005/CL-019、WI 1.4/1.6/1.7 台账）。本节及各条目回填行均为**追加**，未改动两份初审报告原文。

### 一、总体裁定

| 裁定 | 数量 | 条目 |
|---|---|---|
| 保留（原判级不变） | 17 | C3-001/002/004~013、015~019 |
| 保留（附事实/证据修正，判级不变） | 3 | C3-003（globalWorker 有界性）、C3-014（触发场景改写+越界补漏）、C3-020（竞态 B 机制 NPE） |
| 降级 | 0 | — |
| 驳回 | 0 | — |

**终裁分布：P1×1（C3-001）、P2×16（C3-002~013、015~018）、P3×3（C3-014、019、020），合计 20 条，与两轮初审合计一致，无判级变动。**

现状标注（git 核实）：20 条全部**已知未修复**——分支 `concurrency-audit-remediation` 自审计提交 `8c13908bfd` 后无任何触碰涉案文件的修复提交；C3-002/003 前案为 C2 轮 CL-005（登记在案）；C3-009/010/017/018/019/020 与 C3-013 处于“零生产调用方/配置潜伏”态，维持条件化降档。

### 二、特别复核指令处置

1. **C3-001 终裁：P1 维持。** (a) 设施实现亲核：`DefaultScheduledExecutor.java:126-135` 直通 JDK STPE，异常吞入 ScheduledFuture、后续执行被永久抑制，返回 Future 无消费通道；(b) 调用点清单抽验 13 处（超出要求的 6 处）全部属实，含 nop-sys 双生产注释自证；全量重枚举实测 **29 处生产周期站点**（R1“26 处”低估、R2 表 30 行漏 1，见 F3 补全节）；(c) 设施-使用方关系单列：**WI 1.6 C8-001**（nop-retry/tcc“扫描循环被拒/Error 即（近）静默永久停摆……修复须平台级统一”，cra-index L93）是同根因的**使用方面**，本条是**设施面**；WI 1.4 nop-job C4-003 同族——三者修复必须以本条设施级续链/kill+alarm 契约为先导，仅逐使用方修复不能闭环；(d) P1 要件逐项核实成立（全平台并发调用、时序每步可信、不可逆、不可感知）；不升 P0：多数业务关键站点已自捕获 Exception（残余面为 Error 与裸站点），非“默认用法+单一常见条件即触发”。
2. **C3-016 终裁：P2 维持，高信心缺陷确认。** `bindCancel` 守卫 `!future.isDone()` 在 complete-inside-task 写法下的常规时序恒真性逐步核实成立（whenComplete 同步执行于完成线程、FutureTask 状态机、cancel(true) 注入 runner 中断均为 JDK 确定行为）。“必然”的精度边界：零耗时任务在 `bindCancel` 注册（L148）前整体完成的纳秒级竞态可豁免，属对初判有利的收紧而非削弱。危害面（f 假终态不可见 + worker 中断残留对非 TPE 载体/后续任务的泄漏面）与初判一致。
3. **C3-018 终裁：P2 维持。** 注释-接线方向相反为静态可证事实；该行在全部时序下要么 no-op 要么方向错误；计数失真确定。行号漂移修正（启动前守卫 L209-214）。
4. **C3-013/CL-019/CL-022：** C3-013 理论风险标注维持（机制链亲核成立、业务触发点未钉定）；CL-022 具名清单逐点核实成立；**CL-019（ExecutionContextImpl 回调丢失，C2 轮确认移交 C1）与本维度无登记冲突**——C3-013 的机制（ThreadLocal 孤儿 context 池线程驻留）与 CL-019 的机制（完成状态机窗口/回调丢失）不同类不同文件，CL-019 机制经亲核仍在 live code（ExecutionContextImpl L83-93），维持 C1 归属，C3 侧无需补登记。

### 三、CL 处置一致性

- **CL-005 → C3-002/003 升格：处置一致，确认。** C2 轮 CL-005（scheduleAtFixedRate 转发拒绝停摆，L65-69）经 C2 复核确认“独立成立”；其直系升格为 **C3-003**（同站点同机制）；**C3-002** 是同候选族内的**新触发面**（scheduleWithFixedDelay 重排链 worker 内 timer.schedule 拒绝），与 C2 CL-005 复核记录中“重写版正确处理了该面”（仅指 execute 拒绝面）不矛盾，属合规的 F3 扩展。两条均在 live code 亲核成立。
- **CL-021（载体碎片化）确认**：raw 载体抽验成立（SessionLockRenewal L46 `Executors.newSingleThreadScheduledExecutor` 亲核；nop-stream 8 处、nop-ai-agent 注入面、FeishuClient、RpcMessageHandler Netty 载体与枚举一致）。
- **CL-022（detach 卫生具名清单）确认**：四个具名面逐一亲核成立（DefaultTaskExecutionQueue.queueTask L206 raw execute、BlockingSourceConsumeTask.run、SequentialTaskExecutor.doTask、globalTimer 裸 JDK 周期任务体）。

### 四、F3 完备性补全（不改判级）

1. **周期站点总表补登 1 处**：`nop-job/nop-job-coordinator/.../RpcPollTaskManager.java:119`——platform `IScheduledExecutor.scheduleWithFixedDelay`（每 entry 独立句柄），任务体 `pollOnce` 有 catch(Exception)+防御性 isDone 检查（绝缘状态与表中“调用方 catch Exception”族一致，残余 Error 风险同族）。实测生产周期站点 **29 处**（R1“26 处”为低估；R2 §1 表 30 行覆盖 28/29 生产站点 + mock + default 行）。
2. **C3-004 失效面补登**：`RetryEngineImpl.java:407`（经 L622-627 `getExecutor()`=globalTimer.executeOn(globalWorker) 调用 Bind.schedule）——取消不传播缺陷的具体生产暴露点。
3. **C3-014 补漏 facet**：循环内批量续取可致集合超出 maxElements（c.size 至 n0+maxElements+1，L102 未扣初始 n0）——与返回值失真同根（循环计数未计入初始 drain），唯一调用方现网无害。
4. **行号漂移修正**：C3-018 启动前守卫 L209-214（原 192-197）；C3-020 debounce L50-67（原 44-60）；AbstractBatchScanner cursor.reset/onCycleStart 实际 L84-87（原 86-91）。

### 五、争议点与遗留

1. **C3-003 事实修正**：“executeOn 调用点全部绑定无界 globalWorker”不准确——globalWorker 为有界池（core=4/max=30/queue=10000，`StandardThreadPoolExecutor`+`TaskQueue`）。队列满拒绝（30 忙+10000 积压）使触发面略宽于初判的“仅关闭竞态”，机制与 P2 判级不变。
2. **C3-014 原触发场景证伪后以替代证据保留**（复核规则 1：证据不成立但有亲核替代证据 → 保留并修正证据，而非驳回）：满批早返回路径（L83-85）正确；真实失真发生在循环内取满与 maxElements=1 边界。
3. **C3-020 竞态 B 机制修正**：CHM.remove(key,null) 抛 NPE（非返回 false），条目滞留后果不变。
4. **C3-017 与 C1-005 重叠**：按盲审纪律维持“立案+重叠标注”，留报告合并轮对账。
5. **api-core 侧移交项维持**：bindCancel 守卫根因（C3-016 半边）、completeAfterTask Exception 宽度（C3-005 移交项）、executeWithThrottling 幻影 release（C3-007 移交项，L548-552 亲核属实）——三项均在 api-core 越界口径内，移交有效。

---

## 最终保留项

（终裁判级与修复归属；全部**已知未修复**，基线 HEAD `8c13908bfd`。）

| # | 发现 | 终裁 | 一句话裁定 |
|---|---|---|---|
| C3-001 | 周期任务设施无统一异常绝缘层（实测 29 处站点） | **P1 保留** | 设施面终裁成立；修复须与 WI 1.6 C8-001 / nop-job C4-003 使用方停摆族平台级统一（续链或 kill+alarm 契约），以 R2 §1 表 + F3 补登为直接输入 |
| C3-002 | Bind 重排链 worker 内 timer.schedule 未防护→future 永不完成 | P2 保留 | CL-005 族扩展，关闭竞态窗口；补 try/catch(Throwable)+completeExceptionally |
| C3-003 | Bind.scheduleAtFixedRate 拒绝即杀链 | P2 保留（事实修正） | CL-005 直系升格；修正：globalWorker 有界（30/10000），拒绝面含极端过载 |
| C3-004 | Bind.schedule/submit 取消不传播 | P2 保留 | 生产暴露点补登 RetryEngineImpl:407；补 bindCancel+isDone 守卫 |
| C3-005 | throttleExecute catch Exception 漏 Error→调用方永久挂起 | P2 保留 | nop-web 3 调用点 + 无超时 waitAll 链亲核；双层改 catch(Throwable) |
| C3-006 | throttleExecute acquireUninterruptibly 不可中断 | P2 保留 | 改可中断 acquire 或带超时重载 |
| C3-007 | LimitedExecutor 中断路径幻影 release→限流击穿 | P2 保留 | release 移入 acquire 成功后作用域；api-core executeWithThrottling 同构缺陷移交有效 |
| C3-008 | SequentialTaskExecutor.execute 忽略 offer 返回值 | P2 保留 | 检查返回值按策略处理或抛 REE；零调用方潜伏 |
| C3-009 | IAsyncCache 跨配置语义分叉 + getAllAsync 违约 | P2 保留 | async 路径按接口契约实现加载或按分支显式声明 |
| C3-010 | 异步加载/刷新 executor 固化 commonPool 且不可配置 | P2 保留 | CacheConfig 增 executor 项（与 C3-019 合并修复） |
| C3-011 | BlockingSourceConsumeTask 中断残留/Error 杀循环不可感知 | P2 保留 | 外层 catch(Throwable)+continue；保存 submit future 告警 |
| C3-012 | AbstractBatchProcessService.destroy 丢弃在途消息 | P2 保留 | 有界排空等待 + 丢弃记录 |
| C3-013 | 上下文卫生不对称 + 孤儿 context 驻留 | P2 保留（理论标注维持） | 按 CL-022 清单补 detach 卫生；与 CL-019（C1）无冲突 |
| C3-014 | drainTo 满批返回值少计（+集合越界 facet） | P3 保留（证据修正） | 真实触发=循环内取满 / maxElements=1 边界；补满批单测 |
| C3-015 | 全路径杀链语义澄清 + 站点补登 | P2 保留 | 修复输入表有效；补登 RpcPollTaskManager:119（有 Exception 绝缘） |
| C3-016 | submit/schedule 成功路径必然误 cancel 底层 Future + 注入中断 | P2 保留 | 双层修复：bindCancel 按完成原因判别 + 包装 Callable 清除残留中断标志 |
| C3-017 | scheduleWithRandomDelay 执行中取消窗口 | P2 保留 | 补 isDone 守卫 + LOG；与 C1-005 对账留合并轮 |
| C3-018 | DefaultTaskExecutionQueue bindCancel 参数反接 | P2 保留 | 改 bindCancel(state.getPromise(), future) + 计数分流；行号修正 L209-214 |
| C3-019 | sync LoadingCache 刷新链落 commonPool + 异常仅 JUL | P3 保留 | 与 C3-010 合并修复；reload 包装接平台 LOG |
| C3-020 | debounce 非原子 + holder 竞态 | P3 保留（机制修正：NPE） | 改 compute 原子段对齐 throttle |

**终裁统计：P0×0、P1×1、P2×16、P3×3，合计 20 条；保留 20、降级 0、驳回 0；CL-021/022 确认、CL-005 升格一致（直系=C3-003，族扩展=C3-002）、CL-019 无冲突（维持 C1 归属）。**
