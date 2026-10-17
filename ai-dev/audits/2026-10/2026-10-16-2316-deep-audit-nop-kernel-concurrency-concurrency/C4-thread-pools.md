# C4 线程池治理——第 1 轮初审报告（盲审）

## 审计单元

- **维度**: C4——线程池与执行器治理（池参数纪律 core/max/queue/reject/keepAlive 组合、全局池配置边界与生命周期、池关闭语义、线程工厂纪律、池隔离与共享占用、调度器治理）
- **A 面（审计对象本体）**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/{concurrent,cache,pool}`，重点 `concurrent/executor` 子包（20 files）
- **B 面**: `nop-core`/`nop-xlang` 全局模型缓存与注册表单例（基线 `_tmp/cra-baseline-nop-kernel-concurrency.txt` B-face enumeration 节，46 files / 5,945 行）
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **判级口径**: 平台设施被全平台并发调用——全局池（globalWorker/globalTimer）是全平台共享资源，占用/饿死面的「常见条件」门槛按极低口径判级；并发类发现必须写出具体触发时序或过载数字，写不出的降 P2 并标注。

## 盲审纪律声明

本轮为盲审轮次：**未读取** `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；仅读取方法论文件 `ai-dev/skills/concurrency-resource-audit-prompt.md`、`ai-dev/skills/deep-audit-prompts.md`（附录 A 格式）与基线 `_tmp/cra-baseline-nop-kernel-concurrency.txt`（圈定范围用）。全部结论以当前 live code 为准。

## 检查范围说明与池载体清单

完整通读 A 面 executor 子包全部 20 文件 + thread 子包 6 文件 + `cache/`（LocalCache/CaffeineCacheLoader 等 16 文件扫描）+ `pool/`（6 文件，纯接口/配置，**无线程载体**，如实记录）。B 面按基线枚举清单逐文件 grep（模式：`ExecutorService|new Thread|ThreadPoolExecutor|ScheduledExecutor|CompletableFuture.|runAsync|supplyAsync|scheduleAtFixedRate|scheduleWithFixedDelay|ForkJoin`），**46 个 B 面文件 0 命中——B 面无线程池面，零发现**（诚实记录：模型缓存/注册表单例不含任何线程创建或调度器载体；其缓存并发正确性属 C1/C2 维度，不在本轮）。

### 池载体清单表（实测 live code 参数，非文档声明值）

| 池 / 载体 | 实现 | core | max | queue | keepAlive | 拒绝策略 | daemon | 关闭路径 | 判定 |
|---|---|---|---|---|---|---|---|---|---|
| `nop-global-worker` | DefaultThreadPoolExecutor → StandardThreadPoolExecutor + TaskQueue(10000) | **4**（ThreadPoolConfig 默认） | **30**（CFG_CONCURRENT_GLOBAL_WORKER_MAX_POOL_SIZE 默认 30） | TaskQueue 弹性（容量 10000） | 60s | RejectHandler(abort) + 队列满时 force(0 超时) → REE | true | `destroy()` = 仅 `shutdown()`，无 awaitTermination | C4-001/002/CL-007 |
| `nop-global-timer` | DefaultScheduledExecutor → ScheduledThreadPoolExecutor | **4**（默认） | **配置死值**：读 CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE（默认 5）后被 `newScheduledExecutor` 改写为 0，STPE 本身忽略 max | STPE 内部 DelayedWorkQueue 无界（按活动 schedule 数） | n/a | Abort（STPE 默认） | true | `destroy()` = `shutdownNow()` + executor=null | **C4-001** |
| `nop-cached-thread-pool` | plain ThreadPoolExecutor | 0 | **Integer.MAX_VALUE** | SynchronousQueue | 60s | AbortPolicy | true | 无关闭（全局生命周期） | **C4-004** |
| `nop-virtual-thread` | VirtualThreadTaskExecutor（java21 模块 register） | n/a | 每任务虚线程 | n/a | n/a | n/a | n/a | `destroy()`=shutdown | C4-007 |
| `nop-sync-executor` | SyncThreadPoolExecutor → ContinuationExecutor（调用线程直通） | n/a | n/a | n/a | n/a | n/a | n/a | **destroy no-op** | C4-007 |
| ad-hoc：`DefaultThreadPoolExecutor.newExecutor(name,poolSize,queueSize)` | StandardThreadPoolExecutor + TaskQueue | 传入 | =core | 传入（**无校验**） | 60s | RejectHandler | 传入 | 仅 shutdown | **C4-003** |
| ad-hoc：`ExecutorHelper.newSingleThreadExecutor(name,queueSize,daemon)` | plain TPE 1/1 | 1 | 1 | queueSize（0→SynchronousQueue） | 0 | **JDK 默认 Abort** | 传入 | 调用方负责（NioFileWatchService doStop 有 cancel+shutdown，已核对） | OK |
| ad-hoc：`DefaultScheduledExecutor.newSingleThreadTimer(name)` | STPE 1 线程 | 1 | 忽略 | 无界 | n/a | Abort | **false（非 daemon 默认）** | 调用方负责（SimpleRpcClientFactory/StreamControlRpcProxyFactory 有 destroy，已核对） | C4-010 |
| ForkJoinPool.commonPool（间接） | LocalCache `buildAsync` 未配 executor → Caffeine 默认落 commonPool | — | — | — | — | — | — | JVM 生命周期 | **C4-009** |

**澄清（反虚构）**：`BasicThreadPoolExecutor` 不存在，实现类为 `StandardThreadPoolExecutor`（Tomcat StandardThreadExecutor 移植）。任务下发口径中「globalWorker core 默认 0」与 live code 不符——`ThreadPoolConfig.corePoolSize` 默认 **4**（`ThreadPoolConfig.java:17`），globalWorker 未覆盖 → 实测 core=4/max=30/queue=10000。「队列满后才扩容」的 JDK 朴素语义在此**不成立**：TaskQueue 是 Tomcat 弹性队列，实测行为见 CL-007。

**已立案不重复项**（本轮遵守不重复立案）：RoundRobinSupplier、scheduleWithRandomDelay、debounce、DefaultScheduledExecutor TOCTOU（checkEnabled/destroy 竞态）、ReactiveObjectMap、CycleSynchronizer 回调、ResourceMultiLock、LocalFileLock、ResourceCacheEntry 死锁、computeIfAbsent 锁内 transform、DedupBlockingQueue 墓碑、BindScheduledExecutor fixedDelay 手工重排/fixedRate 委托 JDK/cancel 面、throttleExecute、LimitedExecutor、SequentialTaskExecutor offer、SyncThreadPoolExecutor INSTANCE destroy no-op 本体。本轮只审其**未立案邻接面**（关闭语义、参数纪律、生命周期、共享占用），并在失效面枚举中交叉引用。

---

## 发现条目

### [nop-kernel-concurrency-C4-001] globalTimer 全平台共享 4 线程定时器：容量配置死值 + 构造副作用改写 config + 无隔离的阻塞放大面

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/GlobalExecutors.java:38-49`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ExecutorHelper.java:56-71`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/CommonConfigs.java:21-23`
- **证据片段**:
  ```java
  // GlobalExecutors.java:38-49 —— corePoolSize 未设置（默认 4），maxPoolSize 设置为 CFG（默认 5）
  public static IScheduledExecutor globalTimer() {
      return (IScheduledExecutor) g_executors.computeIfAbsent(NOP_GLOBAL_TIMER, key -> {
          DefaultScheduledExecutor timer = new DefaultScheduledExecutor();
          ThreadPoolConfig config = new ThreadPoolConfig();
          config.setName(NOP_GLOBAL_TIMER);
          config.setThreadDaemon(true);
          config.setMaxPoolSize(CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE.get());
          timer.setConfig(config); timer.init(); return timer;
      });
  }
  // ExecutorHelper.java:62-65 —— 读入的 max 配置被副作用改写丢弃，STPE 只有 core 生效
  // ScheduledThreadPoolExecutor的maxPoolSize没有被使用
  config.setMaxPoolSize(0);
  ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(config.getCorePoolSize(), factory);
  ```
- **严重程度**: P1
- **并发场景**: 全平台所有 `GlobalExecutors.globalTimer()` 消费方共享同一个 4 线程（core=4，daemon）`ScheduledThreadPoolExecutor`；直接把耗时工作调度到 timer 线程的消费方与仅做触发转发的消费方（`executeOn(globalWorker)`）混用同一 4 线程预算。
- **触发时序**: 三个事实叠加。(1) `CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE`（`nop.commons.concurrent.global-timer.maxPoolSize`，默认 5，描述「全局定时器线程池的大小」）在 `globalTimer()` 中读出后，`ExecutorHelper.newScheduledExecutor` 第 63 行将其**改写为 0**——运维把配置调到 20 也不会多一个线程，定时器恒定 4 线程；且该副作用改写的是 `DefaultScheduledExecutor` 持有的 config 对象，此后 `globalTimer().getConfig().getMaxPoolSize()` 返回 0，`refreshConfig()` 的 `maxPoolSize>0` 分支（`DefaultScheduledExecutor.java:63`）永久失活。(2) JDK `ScheduledThreadPoolExecutor` 语义：单任务执行超过周期会**顺延全体后续任务**，4 线程全忙时全平台定时任务排队。(3) 具体阻塞链——DB stall（锁等待 120s）时：`LocalResourceLockManager.init()` 直连 globalTimer 执行 `checkTimeout`（DB 扫描，`LocalResourceLockManager.java:103-105`）、`SysDaoNamingService.cleanup`（DB 删除，`SysDaoNamingService.java:77`）、`JdbcConfigSource.refreshConfig`（DB 查询，`JdbcConfigSource.java:54`）、`AbstractFileConfigSource.refreshConfig`（挂起文件系统 IO，`AbstractFileConfigSource.java:45`）四个任务同时卡住 → **4/4 timer 线程全部阻塞 120s** → 同池的 `JsonRpcWebSocketHandler.sendKeepAlive`（`nop-graphql-core/JsonRpcWebSocketHandler.java:82`）停发 keepalive → 网关断开健康连接；`ClusterRpcServiceInvoker`/`ClusterRpcProxyFactoryBean` 的超时/探测 timer（`ClusterRpcServiceInvoker.java:68`）延迟触发 → 挂死调用不被及时判死；`DefaultBizEntityExporter`、`ExcelReportHelper`、`DownloadHelper`、`CliRunCommand`、`TaskFlowManagerImpl`、`RpcPollTaskManager` 的调度全部顺延。
- **现状**: 全平台共享定时器无隔离、无独占保障，且其唯一的容量配置项是死值；部分消费方已用 `executeOn(globalWorker)` 自救（见失效面枚举），但平台层未强制，形成「同类站点两种占用模式」的不一致防线。
- **风险**: 任一直连站点的工作变慢（DB stall、NFS hang、大清理）即放大为全平台定时任务集体延迟，且运维无法通过 `maxPoolSize` 配置扩容自救。对照 F3：同族站点已分裂为「自救派」与「直连派」，直连派即暴露面。
- **建议**: (1) `newScheduledExecutor` 移除 `config.setMaxPoolSize(0)` 副作用，改用局部变量或把 CFG 值落到 `corePoolSize`；(2) 修死配置：让 `global-timer.maxPoolSize` 实际生效（STPE 以 core 为容量）；(3) 制定全局定时器使用规约：schedule 只允许轻量触发，重活强制 `executeOn(globalWorker)`，把直连站点（LocalResourceLockManager、SysDaoNamingService、JdbcConfigSource、AbstractFileConfigSource 等）迁移到 `executeOn` 模式；(4) 长期可按用途拆分 timer 池（config-refresh / keepalive / cleanup）。
- **失效面枚举**（F3，globalTimer 消费方逐一状态）:
  - 直连占用（耗时工作在 timer 线程，暴露面）：`LocalResourceLockManager.java:104`（DB checkTimeout）、`SysDaoNamingService.java:77`（DB cleanup）、`JdbcConfigSource.java:54`（DB refresh）、`AbstractFileConfigSource.java:45`（文件 refresh）、`JsonRpcWebSocketHandler.java:82`（sendKeepAlive，轻但同池受牵连）、`DefaultBizEntityExporter.java:72`、`ExcelReportHelper.java:124`、`ReportDemoBizModel.java:155`、`DownloadHelper.java:39`、`SysDaoMessageService.java:213`（timer 字段直连，但 schedule 经 executeOn，半自救）、`RpcPollTaskManager.java:172`（deadline 检查为纯内存，轻）、`ClusterRpcServiceInvoker.java:68`/`ClusterRpcProxyFactoryBean.java:43`（超时触发，轻）、`TaskFlowManagerImpl.java:79`、`CliRunCommand.java:90`、`CliWatchCommand/CliWatchZipCommand/FileWatcherFactory`（CLI watch）、`AbstractPollingLeaderElector.java:19`、`AutoRegistration.java:205`、`RetryScannerImpl.java:166`、`RetryEngineImpl.java:623`、`AbstractBatchScanner.java:135`、`ShellRunner.java:226`、`ZipFileWatcher.java:38`（以上 executeOn/轻量，已自救）。
  - 同族构造点：`DefaultScheduledExecutor.newSingleThreadTimer` 两个站点（SimpleRpcClientFactory/StreamControlRpcProxyFactory）为自建私有 timer，不受本条影响，但其非 daemon 默认见 C4-010。
- **信心水平**: 确定（参数死值与副作用改写为代码事实；阻塞链各站点均已核对 live code）
- **误报排除**: 非「无状态单例并发调用」误报：STPE 4 线程被外部任务同步阻塞是 JDK 明文语义；`maxPoolSize=0` 副作用改写是可执行验证的代码行为，非推测。也不是「已声明边界」——无任何 owner 文档声明 globalTimer 容量恒 4 且配置无效。
- **复核状态**: 未复核
- **复核状态**: 已复核（2026-10-17 独立复核）——**保留 P1（证据修正）**。①死配置+副作用改写证实（ExecutorHelper.java:62-63 改写 globalTimer 持有的同一 config 对象，getConfig().getMaxPoolSize() 恒 0，DefaultScheduledExecutor.refreshConfig:63 分支永久失活）；②28 文件消费方终表经复核人独立 grep 完整吻合（27 消费方+GlobalExecutors 自身，含第 1 轮漏记的 ReportController.java:133）；③CLI 族 5 站点纠偏证实（直连、无 executeOn，RateLimitExecutorImpl:36-59 任务体内联调度）；④**事实修正：checkTimeout（LocalResourceLockManager.java:117-128）为内存锁表遍历（短临界区 synchronized），非「DB 扫描」，该类无子类——DB 相关站点实为 3 个（naming cleanup 的 deleteByQuery、jdbc refresh、recovery scan），本条触发时序与第 2 轮 A 类表该行「阻塞源 DB」应修正**；⑤DB stall 直接阻塞 3/4 线程成立，全停需第 4 任务受阻（文件刷新 hang/WS 慢消费/WI 1.5 业务体占用），round2 概率模型合取表述本身正确；⑥T=120s 为示意参数。现状：已知未修复（git log 无修复提交）。与 WI 1.5 C4-001（使用方占用面，open/待 7.1）为**同设施两面**：本条立设施死配置面，WI 1.5 立使用方占用面，独立成立互补。详见文末「维度复核结论」。

### [nop-kernel-concurrency-C4-002] 池关闭语义不完整：shutdown 配置项全平台死值、destroy 不等待在飞任务、两类池 destroy 语义互斥

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/BaseThreadPoolConfig.java:17-35`；`DefaultThreadPoolExecutor.java:91-93`；`DefaultScheduledExecutor.java:88-93`；对照 `thread/ThreadHelper.java`（shutdownAndAwaitTermination）
- **证据片段**:
  ```java
  // BaseThreadPoolConfig.java:17-19 —— 配置面存在
  private boolean waitForTasksToCompleteOnShutdown = false;
  private int awaitTerminationSeconds = 0;
  // DefaultThreadPoolExecutor.java:91-93 —— destroy 只 shutdown，两个字段从未被读取
  public void destroy() {
      executor.shutdown();
  }
  // DefaultScheduledExecutor.java:88-93 —— 另一类池却是 shutdownNow + 置 null
  public void destroy() {
      if(executor != null) { executor.shutdownNow(); executor = null; }
  }
  ```
- **严重程度**: P2
- **并发场景**: 应用停机（IoC 容器销毁、CLI 退出、池 register 顶替）时，持有在飞任务（导出写盘、RPC 在途、批量写库）的池被关闭。全平台 `DefaultThreadPoolExecutor` 消费方（GrpcServer、JdkHttpClient、SimpleRpcServer/Client、nop-config-executor、javascript-engine、export-db/import-db 等）共用此 destroy 路径。
- **触发时序**: (1) 全仓 grep 证实 `isWaitForTasksToCompleteOnShutdown`/`getAwaitTerminationSeconds` **仅存在于 BaseThreadPoolConfig 自身**（getter 定义处），无任何调用点——通过 `@ConfigBean` 配置 `awaitTerminationSeconds=30` 的用户预期与实现零关联，纯死配置。(2) 停机时序：容器 destroy 调 `pool.destroy()` → `shutdown()` 仅拒新任务即返回 → JVM 退出时 daemon worker 线程被直接杀死 → 队列中剩余任务与在飞任务（如 export-db 半批写入）无声丢弃，无任何「等待/超时/报告未完成」环节。(3) 反向不一致：DefaultScheduledExecutor.destroy 用 `shutdownNow()` **立即中断**在飞定时任务且置 `executor=null`——同平台两种 destroy 语义互斥，前者丢弃不等待，后者中断不等待。(4) 次生缺陷：destroy 前未 init（`executor==null`）时 `DefaultThreadPoolExecutor.destroy()` 抛裸 NPE（`DefaultScheduledExecutor` 有 null 守卫，不对称）；DefaultScheduledExecutor destroy 后 `stats()` 未走 checkEnabled → NPE。(5) 关闭拒绝路径还有 `StandardThreadPoolExecutor.java:156-174` 的 `submittedCount` 泄漏：shutdown 后 execute 被拒 → `queue.force` 抛 REE(not-running) → 不经过任何 decrement 分支直接外抛 → 计数永久虚高（shutdown 后无实际影响，属机件不洁）。
- **现状**: 平台自有优雅关闭工具 `ThreadHelper.shutdownAndAwaitTermination`（guava 式 shutdown→await 半程→shutdownNow→await 半程）已存在，但平台自己的两个 destroy 实现都未使用；配置声明与实现脱节。
- **风险**: 停机丢弃在飞任务不可检测、不可恢复；运维调 `awaitTerminationSeconds` 得到虚假安全感。对照 F8（生命周期不对称）与 F9（静默丢弃不可见）。
- **建议**: `DefaultThreadPoolExecutor.destroy()` 落实 `waitForTasksToCompleteOnShutdown`/`awaitTerminationSeconds`（复用 `ThreadHelper.shutdownAndAwaitTermination`）；统一两类池 destroy 语义并补 null 守卫与 destroy 后 `stats()` 的 fail-fast（NopException 而非 NPE）；在 owner 文档声明两者差异或消除差异。
- **失效面枚举**（F3，destroy/close 实现清单）：`DefaultThreadPoolExecutor.destroy`（shutdown，本条主对象）；`DefaultScheduledExecutor.destroy`（shutdownNow+null，语义冲突面）；`SyncThreadPoolExecutor.destroy`（no-op，见 C4-007）；`VirtualThreadTaskExecutor.destroy`（shutdown，同样无等待）；`BindScheduledExecutor.destroy`（委托 timer.destroy，见 C4-005）；`NioFileWatchService.doStop`（future.cancel(true)+shutdown，**已核对合格**）；`SimpleRpcClientFactory.java:144`/`StreamControlRpcProxyFactory.java:115`（自建 timer.destroy，已核对）；`DefaultTaskExecutionQueue.doStop`（ownExecutor 守卫，**已核对合格**）；`ThreadHelper.shutdownAndAwaitTermination`（正确实现，未被设施复用即本条要点）。
- **信心水平**: 确定（死配置为零 grep 命中，可机械复核）
- **误报排除**: 不是「低频路径粗粒度锁」类误报：这是配置契约违约（配置存在但无实现）+ 停机数据丢弃面，两个都可机械验证；也不是已声明边界——`@ConfigBean` 字段的存在本身构成契约声明。
- **复核状态**: 未复核
- **复核状态**: 已复核（2026-10-17 独立复核）——**保留 P2**。全部子项证实：两字段全仓 0 读取点（grep 仅 getter 定义）；destroy 不对称（shutdown vs shutdownNow+null）；destroy 前未 init 裸 NPE（DefaultThreadPoolExecutor.java:92 无守卫）；destroy 后 stats() NPE；submittedCount 泄漏路径精确成立（TaskQueue.force:56-57 shutdown 后抛 REE 绕过 StandardThreadPoolExecutor.java:161 的 decrement）；`shutdownAndAwaitTermination` 复核人 grep（含测试）0 调用者。现状：已知未修复。

### [nop-kernel-concurrency-C4-003] newExecutor 工厂参数零校验：Integer.MAX_VALUE 队列直通（有实证调用者）、负值静默变 cached 语义、非法值抛裸 JDK 异常

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/DefaultThreadPoolExecutor.java:29-43`；`ExecutorHelper.java:73-93`；实证调用 `nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/ExportDbTool.java:150`、`ImportDbTool.java:232`、`nop-js/.../JavaScriptService.java:40`
- **证据片段**:
  ```java
  // DefaultThreadPoolExecutor.java:33-43 —— poolSize/queueSize 原样透传，无任何 Guard 校验
  public static DefaultThreadPoolExecutor newExecutor(String threadName, int poolSize, int queueSize,
                                                      boolean daemon) {
      ThreadPoolConfig config = new ThreadPoolConfig();
      config.setCorePoolSize(poolSize);
      config.setMaxPoolSize(poolSize);
      ...
      config.setQueueCapacity(queueSize);
      return newExecutor(config);
  }
  // 实证 1：ExportDbTool.java:150 / ImportDbTool.java:232 —— 2^31-1 容量队列直通
  executor = DefaultThreadPoolExecutor.newExecutor("export-db", config.getThreadCount(), Integer.MAX_VALUE);
  // 实证 2：JavaScriptService.java:40 —— 负值静默
  executor = DefaultThreadPoolExecutor.newExecutor("javascript-engine", -1, -1);
  ```
- **严重程度**: P2
- **并发场景**: 平台代码与业务代码用该工厂自建池的全部站点（11 处，见失效面）；`ExecutorHelper.newThreadPoolExecutor:85` 将 queueSize 原样传入 `new TaskQueue(capacity)` → `LinkedBlockingQueue(2^31-1)`。
- **触发时序**: (1) **无界队列实证面**：`export-db`/`import-db` 以 `Integer.MAX_VALUE` 队列运行——threadCount（如 8）个线程占满后，新任务全部入队且**永不触发拒绝**：10 万行分片导出 × 每任务持有查询参数/游标闭包，一次误配置的大批量提交即可积压 10 万级任务对象（每个数 KB → 数百 MB 堆），且调用方完全无背压信号（对比同文件 cachedThreadPool 路径至少还有线程数上限语义）。(2) **负值静默**：`newExecutor("javascript-engine", -1, -1)` 中 maxPoolSize=-1 使 `ExecutorHelper.newThreadPoolExecutor:81` 走 `ThreadPoolExecutor(0, Integer.MAX_VALUE, 60s, SynchronousQueue)` 分支——调用方写的 poolSize=-1/queueSize=-1 被静默解释为「0 core 无界 cached 池」，与字面意图（-1 队列可能被理解为无界队列）完全不同的机件；(3) queueSize<=0（除 -1 外，如 0/-5）→ `LinkedBlockingQueue` 构造抛裸 `IllegalArgumentException`（非 NopException、非英文规范消息），poolSize=0 且 max=0 时 TPE 构造抛 `IllegalArgumentException`——均在 `init()` 深处爆炸，无参数名上下文。
- **现状**: 工厂对三个核心参数无一校验、无一文档化（负值语义、MAX_VALUE 语义均为隐式实现细节）。
- **风险**: 任何新调用点复制 `Integer.MAX_VALUE` 惯例即获得「无界积压 + 背压失效 + OOM 候选」三件套；负值语义静默漂移使配置审查失效。
- **建议**: 工厂入口加 `Guard.checkArgument`（poolSize>0；queueSize>=0 且上界封顶，如 100000，超过必须显式 `queueSize<0` 表示无界并打 warn 日志）；负值语义集中到一处并写 owner 文档；将 `ExportDbTool`/`ImportDbTool` 的 `Integer.MAX_VALUE` 改为有界值或显式无界常量（`UNBOUNDED_QUEUE`）+ warn。
- **失效面枚举**（F3，newExecutor 工厂全部调用点逐一状态）：`ExportDbTool.java:150`（MAX_VALUE，缺陷面）、`ImportDbTool.java:232`（MAX_VALUE，缺陷面）、`JavaScriptService.java:40`（-1,-1 静默 cached，缺陷面）、`AbstractBatchProcessService.java:54`（queueSize=1，合格）、`SingleThreadConfigExecutor.java:19`（1,100，合格）、`GrpcServer.java:82`（ThreadPoolConfig 重载，取决于配置，合格）、`JdkHttpClient.java:111`（配置传入，合格）、`SimpleRpcServer.java:132`（合格）、`SimpleRpcClientFactory.java:126`（5,10，合格）、`DefaultTaskExecutionQueue.java:145`（配置重载，ownExecutor 守卫合格）、`NioFileWatchService.java:58`（走 newSingleThreadExecutor，合格）。
- **信心水平**: 确定
- **误报排除**: 非误报校准中「有界声明存在的队列」——恰因无校验，容量上界由调用方任意值决定且已有 MAX_VALUE 实证；「有界但边界配置缺陷」正是本领域 P2 定义项。
- **复核状态**: 未复核
- **复核状态**: 已复核（2026-10-17 独立复核）——**保留 P2**。MAX_VALUE 实证（ExportDbTool.java:150/ImportDbTool.java:232）、负值静默 cached（JavaScriptService.java:40 → ExecutorHelper.java:81-83）、F3 11 调用点经复核人 grep 完整、init 路径无 core>max 守卫证实（refreshConfig:58-60 有守卫，round2 F3 扩展成立）。现状：已知未修复；站点面与 WI 1.7 nop-batch CL-004（exp 线程池无界队列）重叠，修复须合并立项防重复。

### [nop-kernel-concurrency-C4-004] nop-cached-thread-pool：全平台共享的无上界线程池，线程爆炸无熔断、无配额

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/GlobalExecutors.java:71-83`；`ExecutorHelper.java:81-83`；消费方 `nop-batch/nop-batch-core/.../BatchTask.java:87`、`BatchTaskBuilder.java:337`、`nop-batch-exp/ExportDbTool.java:284`、`ImportDbTool.java:296`、`nop-socket/SocketServer.java:140`、`nop-shell/ShellRunner.java:153`
- **证据片段**:
  ```java
  // GlobalExecutors.java:77 —— 不限制线程池大小
  config.setMaxPoolSize(-1);
  // ExecutorHelper.java:82 —— maxPoolSize<0 → 硬编码无界 cached 语义
  executor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS,
          new SynchronousQueue<>(), factory);
  ```
- **严重程度**: P2
- **并发场景**: 全平台按名共享（`getExecutor("nop-cached-thread-pool")`、`ModelBasedBatchTaskBuilderFactory.java:210` 按配置名解析可命中）；批量任务（batch core/exp）与 SocketServer 每连接、ShellRunner 并发进程共享此池。
- **触发时序**: SynchronousQueue + core=0 + max=2^31-1：每个提交在无空闲线程时**必然新建线程**。过载数字：批量导出并发 1000 分片 → 1000 平台线程（每线程默认栈 1MB → 仅栈即 ~1GB，加 Thread对象/TL）；SocketServer 连接洪峰 10k → 10k 线程 → OOM（unable to create native thread）或操作系统线程上限崩溃；且此池与其他全局池无任何共享配额，线程爆炸不产生可配置的拒绝信号（AbortPolicy 仅在线程创建失败时触发，届时进程已濒死）。
- **现状**: cached 语义本身是 JDK 惯用法，但作为**按名注册的全局共享池**无上界、无告警阈值、无与 maxPoolSize 配置联动的熔断；与 globalWorker（有 30 上限）治理标准不一致。
- **风险**: 上游突发直接转化为线程数突发，故障形态为进程级不可逆崩溃（线程创建失败），且 SocketServer 场景下外部可远程触发（连接洪峰）。
- **建议**: 为 cachedThreadPool 增加可配置上界（如 `nop.commons.concurrent.cached-pool.maxThreads`，默认建议 200）并在达到时走 QueueOverflowPolicy/Abort 明确报错；SocketServer 改用有界池 + 队列策略；文档声明该池的容量语义。
- **失效面枚举**（F3，cachedThreadPool 消费方）：`ExportDbTool.java:284`、`ImportDbTool.java:296`（builder.executor，同批任务并发放大）、`BatchTaskBuilder.java:337`、`BatchTask.java:87`（batch 默认执行器）、`SocketServer.java:140`（每连接外部可达，最高危）、`ShellRunner.java:153`（CLI 场景，低危）、`ModelBasedBatchTaskBuilderFactory.java:210`（按名解析间接命中）。同族「无界线程」站点：`ExecutorHelper.newThreadPoolExecutor` maxPoolSize<0 分支的所有配置可达调用者（见 C4-003 JavaScriptService）。
- **信心水平**: 确定（参数组合为代码事实；线程爆炸数字为 JDK 语义推算）
- **误报排除**: 非误报校准「有界声明存在的池」——此池 max=Integer.MAX_VALUE 等价无界声明缺失；亦非测试代码简化模型，全部为生产路径。
- **复核状态**: 未复核
- **复核状态**: 已复核（2026-10-17 独立复核）——**保留 P2（证据修正）**。无上界本体证实（GlobalExecutors.java:77 maxPoolSize(-1) → ExecutorHelper.java:82 `TPE(0, 2³¹-1, 60s, SynchronousQueue)`）；6 消费方全部证实（含 ModelBasedBatchTaskBuilderFactory.java:207-213 按名解析通道）。**事实修正：SocketServer 每连接派发证实（run() 循环 `executor.execute(processCommand)`），但 `ServerConfig.maxConnections` 默认 10（ServerConfig.java:22）——「连接洪峰 10k→10k 线程」在默认配置下不可达，外部远程可触发性弱化为配置依赖；主可达面=batch 高并发分片**。现状：已知未修复。

### [nop-kernel-concurrency-C4-005] 全局池单例借出即可被 destroy 且不可恢复：destroy 后 g_executors 永久持有死实例，computeIfAbsent 不重建

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/GlobalExecutors.java:32-36, 51-62, 102-111`；`DefaultScheduledExecutor.java:96-105`；`BindScheduledExecutor.java:157-160`
- **证据片段**:
  ```java
  // GlobalExecutors.java:102-111 —— register 顶替时 destroy 旧池不 drain；unregister 移除不 destroy
  public static void register(IThreadPoolExecutor executor) {
      IThreadPoolExecutor old = g_executors.put(executor.getName(), executor);
      if (old != null) { old.destroy(); }
  }
  public static IThreadPoolExecutor unregister(String name) {
      return g_executors.remove(name);          // 不调用 destroy，且全仓 0 调用者
  }
  // DefaultScheduledExecutor.java:100-105 —— destroy 后 executor=null，checkEnabled 永久抛错
  private void checkEnabled() {
      if (executor == null) { throw new NopException(ERR_SERVICE_NOT_ACTIVE)...; }
  }
  ```
- **严重程度**: P2
- **并发场景**: 全局池以 `IThreadPoolExecutor`（继承 `IDestroyable`）接口借出给任意组件；IoC `destroy-method` 约定或组件 stop 钩子一旦作用在借出引用上，即销毁全平台共享池。
- **触发时序**: (1) **不可恢复链**：某组件对 `GlobalExecutors.globalTimer()` 返回值调用 `destroy()`（一次误配的 beans.xml `destroy-method="destroy"` 即可）→ `DefaultScheduledExecutor.executor=null` → **map 条目仍在** → 后续所有 `GlobalExecutors.globalTimer()` 命中 `computeIfAbsent` 已有条目返回死实例 → 全平台 schedule/submit/execute 永久抛 `ERR_SERVICE_NOT_ACTIVE`，直至进程重启。globalWorker 同理：destroy 后 `execute()` 每次抛 RejectedExecutionException（StandardThreadPoolExecutor shutdown 后 force 路径抛 not-running），且同样永久。(2) **借出无守卫**：分发点（globalTimer/globalWorker 返回处）无「此实例为共享、destroy 禁止」的运行时守卫（F10）——当前全仓核对仅 4 处 `timer/executor.destroy()` 调用且全部为自有池（见失效面），`DefaultTaskExecutionQueue` 的 `ownExecutor` 标志是现存唯一正确范式，但该范式未上升为设施契约。(3) **生命周期不对称**：`unregister` 移除不 destroy（若有人使用即泄漏线程池）且当前零调用者=死 API；`register` 顶替时 `old.destroy()` 只 shutdown 不等待在飞任务（与 C4-002 叠加），且旧池的 micrometer 指标绑定（`ExecutorHelper.registerMetrics`）不解除，指标重复注册。
- **现状**: 全局单例的生命周期所有权未定义：借出接口带 destroy、分发无守卫、销毁不可逆、unregister 语义自相矛盾。
- **风险**: 一次误接线即造成全平台调度/执行永久瘫痪且无法热恢复；当前「恰好没人调 destroy」是侥幸而非防线。
- **建议**: 分发点返回**不可销毁包装**（destroy 抛 `NopException(ERR_CONCURRENT_...)` 或 no-op+error 日志），所有权显式化（borrow/own 两类 API）；`unregister` 补 destroy 或删除死 API；`register` 顶替时对旧池走 `ThreadHelper.shutdownAndAwaitTermination` 并解绑 metrics；仿照 `DefaultTaskExecutionQueue.ownExecutor` 把「谁创建谁销毁」写入 owner 文档。
- **失效面枚举**（F3，全局单例 destroy 触点）：`timer/executor.destroy()` 全仓生产调用仅 4 处——`StreamControlRpcProxyFactory.java:115`（自建 timer，合格）、`DefaultTaskExecutionQueue.java:153`（ownExecutor 守卫，合格）、`SimpleRpcClientFactory.java:144`（自建 timer，合格）、`BindScheduledExecutor.java:159`（**委托 timer.destroy()**：包装 globalTimer 的 BindScheduledExecutor（`AbstractPollingLeaderElector`、`RetryEngineImpl.executor`、`RetryScannerImpl.getExecutor()`、`AbstractBatchScanner`、`AutoRegistration`、`SysDaoMessageService` 均经 `executeOn` 产生）一旦被销毁即触发本条不可恢复链——当前无调用者，属守卫缺失面）；`SyncThreadPoolExecutor.destroy` no-op（C4-007）。
- **信心水平**: 确定（不可恢复链每一步均可机械验证；触发前提「有人调 destroy」已如实标注为当前无调用者）
- **误报排除**: 非理论竞态降级项——本条不是时序竞态而是**守卫缺失+不可逆性**（F10 口径：声明共享的设施无 fail-fast 守卫）；平台设施判级口径下「常见条件门槛极低」适用于一次配置错误即触发。
- **复核状态**: 未复核
- **复核状态**: 已复核（2026-10-17 独立复核）——**保留 P2**。不可恢复链逐步证实（destroy→executor=null→checkEnabled 恒抛 ERR_SERVICE_NOT_ACTIVE，g_executors 条目仍在，computeIfAbsent 不重建）；unregister 0 调用者 grep 证实；register 顶替 old.destroy() 不等待证实；BindScheduledExecutor.java:157-160 委托 timer.destroy() 且当前无调用者证实；metrics 重复绑定无解除证实（registerMetrics 无 unbind）；`DefaultTaskExecutionQueue` ownExecutor 守卫范式核实存在（:138-155）。现状：已知未修复。

### [nop-kernel-concurrency-C4-006] ExecutorHelper.updateThreadPool 缩容方向条件反置：新 max 低于当前 core 时直接抛 IllegalArgumentException

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ExecutorHelper.java:106-126`
- **证据片段**:
  ```java
  if (config.getMaxPoolSize() > 0 && config.getMaxPoolSize() != executor.getMaximumPoolSize()) {
      if (config.getMaxPoolSize() > executor.getCorePoolSize()) {
          // 如果是缩小线程池，需要先缩小corePoolSize才能缩小maxPoolSize
          executor.setCorePoolSize(config.getMaxPoolSize());   // 实际是新 max > 当前 core 的「放大」分支
      }
      executor.setMaximumPoolSize(config.getMaxPoolSize());
  ```
- **严重程度**: P2
- **并发场景**: 运行期配置热更新（`IConfigRefreshable.refreshConfig`）作用于 `DefaultThreadPoolExecutor`/`DefaultScheduledExecutor`（globalWorker/globalTimer 及全部配置驱动池）。
- **触发时序**: JDK `ThreadPoolExecutor.setMaximumPoolSize` 要求 `maximumPoolSize >= corePoolSize` 否则抛 `IllegalArgumentException`。(1) **缩容爆炸分支**：globalWorker 当前 core=4，把 max 从 30 热更为 2 → 条件 `2 > 4` 为假 → 跳过预调整 → `setMaximumPoolSize(2)` 时 core 仍为 4 → **2 < 4 抛裸 IllegalArgumentException**，refreshConfig 失败。任何「新 max < 当前 core」的缩容请求必然抛异常——注释声称处理缩小场景，条件恰好写反（触发于放大场景）。(2) **多余抬升分支**：新 max=40 > core=4 时，先 `setCorePoolSize(40)`——若此时队列有积压，JDK 立即预启动至 40 线程，随后 `setMaximumPoolSize(40)`、`setCorePoolSize(4)` 又回收——一次纯放大的热更造成 36 个线程的创建-销毁抖动（globalTimer 因 max 被改写为 0 走不进首分支，仅 core 分支生效，见 C4-001 联动）。(3) 次要：updateThreadPool 不处理 `queueCapacity`/`allowCoreThreadTimeOut`/daemon，热更面不全（静默忽略）。
- **现状**: 动态调整的边界条件与 JDK 约束方向相反，缩容功能实际不可用。
- **风险**: 配置热更缩容必炸（裸 IAE，非 NopException，违反错误处理两档策略）；扩容热更产生无谓线程抖动；队列容量热更静默失效误导运维。
- **建议**: 修正为 `if (config.getMaxPoolSize() < executor.getCorePoolSize()) { executor.setCorePoolSize(config.getMaxPoolSize()); }`（真缩容先降 core 再降 max），放大分支删除预抬步骤；补充缩容/扩容单测（TPE 参数方向矩阵）；`DefaultThreadPoolExecutor.refreshConfig` 捕获并转 NopException。
- **失效面枚举**（F3，refreshConfig→updateThreadPool 全部链路）：`DefaultThreadPoolExecutor.refreshConfig:57-65`、`DefaultScheduledExecutor.refreshConfig:62-70`（两者均走同一 helper，缺陷面相同）；`ExecutorHelper.updateThreadPool` 本体（单点修复即全族修复）；无可绕行兄弟实现（全仓仅此一处动态调整机件）。
- **信心水平**: 确定（JDK setMaximumPoolSize 的 core<=max 校验为明文契约；方向反置可读码+单测复核）
- **误报排除**: 非误报校准项；触发条件（热更缩容）虽非常态路径，但异常是**确定性**的（非竞态、无需交错时序），按「有明确触发链」P2 口径立案。
- **复核状态**: 未复核
- **复核状态**: 已复核（2026-10-17 独立复核）——**保留 P2**。方向反置证实：新 max < 当前 core 时跳过预调整 → `setMaximumPoolSize` 触发 JDK `maximumPoolSize < corePoolSize` IAE，缩容确定性失败。**可达性补充**：refreshConfig 经 IoC `DefaultBeanClassIntrospection.getRefreshConfigMethod`（:96-99）对 IConfigRefreshable bean 派发，池 bean 实际存在（nop-job app-local-scheduler/app-engine beans.xml 引用两个 Default 类）——bean 管理池为实证可达面；globalWorker/globalTimer 的 config 为代码构造静态对象、非热更派发对象，「globalWorker 缩容必炸」为机制同源理论面。放大分支的线程抖动正确地以队列积压为条件（JDK setCorePoolSize 不校验 core≤max，预抬合法）。判级不变。现状：已知未修复。

### [nop-kernel-concurrency-C4-007] SyncThreadPoolExecutor / VirtualThreadTaskExecutor 池契约缺失：stats()/getConfig() 返回 null、destroy no-op、零 javadoc，监控与生命周期调用方无区分手段

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/SyncThreadPoolExecutor.java:15-69`；`nop-utils/nop-commons-java21/.../VirtualThreadTaskExecutor.java`；消费 `GlobalExecutors.java:98-100`、`nop-batch-biz/AbstractBizEntityService.java:52-60`
- **证据片段**:
  ```java
  // SyncThreadPoolExecutor.java —— 全类无一行 javadoc
  public ThreadPoolConfig getConfig() { return null; }   // 33-35
  public ThreadPoolStats stats() { return null; }        // 37-40
  public void destroy() { }                              // 61-63 no-op
  public void execute(Runnable command) {
      ContinuationExecutor.INSTANCE.execute(command);    // 66-68 调用线程直通
  }
  ```
- **严重程度**: P3
- **并发场景**: `GlobalExecutors.syncExecutor()` 与 `register` 后的 virtual-thread 执行器与真实池同以 `IThreadPoolExecutor` 接口暴露；`AbstractBizEntityService.getExecutor` 在同一扩展点下按 `max-concurrency` 配置在 globalWorker（异步）与 syncExecutor（**调用线程直通**）间切换（默认 `max-concurrency|1` → 同步直通）。
- **触发时序**: (1) 监控/巡检代码遍历执行器调用 `stats()`（或 `getConfig()`）→ NPE（`DefaultThreadPoolExecutor.stats` 对真池返回对象，对 sync 池返回 null，接口契约未声明可空）。(2) 生命周期调用方对 sync 池调 `destroy()` → 静默 no-op → 调用方确信「已销毁」（IDestroyable 契约违约，无 isDestroyed 可查询——`IScheduledExecutor` 有 `isDestroyed`，`IThreadPoolExecutor` 没有，接口族不一致）。(3) 语义切换无运行时标志：业务把 `nop.biz.report.export.max-concurrency` 从 1 调到 2 的瞬间，导出从「请求线程内联执行」切换为「globalWorker 池执行」——IContext 传播、超时归属、线程名全部改变，无任何日志或文档声明该耦合。
- **现状**: 两个特殊执行器的可观测性与生命周期契约缺失属实现事实；`AbstractBizEntityService` 的同步默认是刻意设计（并发 1 时省线程切换），但切换面未文档化。
- **风险**: 监控接入即 NPE；生命周期管理得出虚假结论；配置语义漂移无提示。均为可诊断性问题，非正确性缺陷。
- **建议**: `stats()` 返回空 `ThreadPoolStats`（或接口标注 `@Nullable` 并在 owner doc 声明）；`execute()` javadoc 显式声明「在调用线程同步执行，destroy 为 no-op」；`IThreadPoolExecutor` 补 `isDestroyed()` 默认方法；`AbstractBizEntityService` 在 sync 分支打一行 debug 日志标明执行模式。
- **失效面枚举**（F3，返回 null/no-op 的池实现）：`SyncThreadPoolExecutor.getConfig/stats/destroy/setName`（4 处空实现，缺陷面）；`VirtualThreadTaskExecutor.stats`（返回 null，缺陷面；destroy=shutdown 正常）；`DefaultScheduledExecutor.stats`（destroy 后 NPE，见 C4-002）；`ContinuationExecutor.remove()`（存在但 SyncThreadPoolExecutor 从不调用——池化线程上 ThreadLocal<Continuation> 常驻，单个对象量级，可忽略）。
- **信心水平**: 确定
- **误报排除**: 非误报校准项；按判级口径「写不出具体时序/无正确性后果」如实降 P3，不虚报 P2。
- **复核状态**: 未复核
- **复核状态**: 已复核（2026-10-17 独立复核）——**保留 P3**。SyncThreadPoolExecutor getConfig/stats null（:33-40）、destroy no-op（:61-63）、execute 直通证实；VirtualThreadTaskExecutor.stats null 证实；`IThreadPoolExecutor` 无 isDestroyed 而 `IScheduledExecutor` 有的接口族不一致证实；AbstractBizEntityService:52-60 按 max-concurrency 切换证实；`NOP_SYNC_EXECUTOR` 从未注册进 g_executors、getExecutor 无 fallback 恒 null（解析盲区）证实。现状：已知未修复。

### [nop-kernel-concurrency-C4-008] StandardThreadPoolExecutor 携带的 Tomcat 线程更新机件为死代码：contextStopping 零调用链、非 volatile 跨线程字段、死配置 knob

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/StandardThreadPoolExecutor.java:38-49, 93-126, 177-203`；`TaskQueue.java:37, 109-122`；`CommonConfigs.java:39-40`（CFG_CONTINUATION_MAX_QUEUE_SIZE）
- **证据片段**:
  ```java
  // TaskQueue.java:35-37 —— 注释声称单线程读写，实际 contextStopping 可从任意线程调用
  // No need to be volatile. This is written and read in a single thread
  private Integer forcedRemainingCapacity = null;
  // StandardThreadPoolExecutor.java:177 —— 全仓 grep：contextStopping/setThreadRenewalDelay 无任何调用者
  public void contextStopping() { this.lastContextStoppedTime.set(System.currentTimeMillis()); ... }
  ```
- **严重程度**: P3
- **并发场景**: 池内 worker 线程（`TaskQueue.poll/take` → `stopCurrentThreadIfNeeded`）与假想的 `contextStopping` 调用线程。
- **触发时序**: 全仓生产代码 grep 证实 `contextStopping`、`setThreadRenewalDelay`、`lastContextStoppedTime` 写入点均无调用者 → `currentThreadShouldBeStopped()` 恒假（`creationTime < 0` 不可能）→ `TaskQueue.poll:92`、`take:99` 的线程自杀分支与 `forcedRemainingCapacity` 技巧（`contextStopping` 内 `setForcedRemainingCapacity(0)`）整体不可达。若未来有人启用 `contextStopping`：该调用可来自任意管理线程，而 `forcedRemainingCapacity`（Integer，非 volatile）在 worker 线程的 `remainingCapacity()` 中读取——写读之间无 happens-before，worker 可能长期读不到 0（可见性）或读到中途状态；此为启用即触发的理论面（当前不可达，按口径标注）。附带：`CFG_CONTINUATION_MAX_QUEUE_SIZE`（默认 1000）同样零使用——`ContinuationExecutor.tasks` 是无界 ArrayDeque 且无任何容量检查，死配置 knob。另有 `RejectHandler.java:207` 抛无消息 `new RejectedExecutionException()`，与 `queue-full` 字符串常量并存的报错口径不一致。
- **现状**: Tomcat 移植机件未接全仓任何触发点；携带成本为可读性与未来误用面。
- **风险**: 死代码被误认为生效的线程防漏机制；启用路径带可见性缺陷；死配置误导（与 C4-001 maxPoolSize 死值同族）。
- **建议**: 二选一：(a) 删除 contextStopping/threadRenewalDelay/forcedRemainingCapacity/StopPooledThreadException 机件（连带 `NopThread.DEFAULT_EXCEPTION_HANDLER` 中对 INSTANCE 的特判）；(b) 补全调用链（容器 context 重载点）并修 `forcedRemainingCapacity` 为 volatile。`CFG_CONTINUATION_MAX_QUEUE_SIZE` 删除或在 `Continuation.submit` 落实上限。
- **失效面枚举**（F3，死机件清单）：`StandardThreadPoolExecutor.contextStopping`（0 调用者）、`setThreadRenewalDelay`（0 调用者）、`lastContextStoppedTime`（仅 contextStopping 写）、`TaskQueue.forcedRemainingCapacity/setForcedRemainingCapacity`（仅 contextStopping 调用）、`StopPooledThreadException`（仅死分支抛出）、`CFG_CONTINUATION_MAX_QUEUE_SIZE`（0 使用）；活机件：`TaskQueue.offer/poll/take` 的弹性与 parent 协作（见 CL-007，实现正确）。
- **信心水平**: 确定（零调用链可机械复核）
- **误报排除**: 非并发缺陷误报——本条明确标注「当前不可达」，立案理由是死配置+误导性死机件的治理面，非虚构运行时风险。
- **复核状态**: 未复核
- **复核状态**: 已复核（2026-10-17 独立复核）——**保留 P3**。contextStopping/setThreadRenewalDelay 全仓 0 调用者 grep 证实；currentThreadShouldBeStopped 恒假（lastContextStoppedTime 初始 0，creationTime 不可能小于 0）证实；forcedRemainingCapacity 非 volatile 证实（TaskQueue:35-37 注释即承认单线程假设）；CFG_CONTINUATION_MAX_QUEUE_SIZE 0 使用 grep 证实；RejectHandler.java:208 裸 REE 证实。现状：已知未修复。

### [nop-kernel-concurrency-C4-009] LocalCache 异步模式（Caffeine buildAsync）未配置 executor：异步加载隐式落 ForkJoinPool.commonPool，脱离平台池治理

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/LocalCache.java:117-126`；设施入口 `cache/CacheConfig.java:122-124`（`async()`）
- **证据片段**:
  ```java
  if (config.isAsync()) {
      if (loader != null) {
          AsyncLoadingCache<K, V> cache = builder.buildAsync(createCacheLoader(loader));  // 120
          this.asyncCache = cache; ...
      } else {
          this.asyncCache = builder.buildAsync();        // 124 —— 无 builder.executor(...)
  ```
- **严重程度**: P3
- **并发场景**: 启用 `CacheConfig.async()` 的缓存，其 `asyncLoad/asyncReload` 回调执行在 Caffeine 默认 executor = **ForkJoinPool.commonPool()**（`buildAsync` 未调用 `builder.executor(...)` 时 Caffeine 取 commonPool）。
- **触发时序**: 首个 async 缓存 + 阻塞型 loader（DB/远程查询）上线后：每个未命中键占一个 commonPool 线程做阻塞 IO；commonPool 并行度 = CPU-1（如 8C → 7 线程）→ 8 个并发 miss 即打满 → 同池的并行流、其他 `CompletableFuture` 默认异步任务全体排队；且 blocking loader 与 commonPool 的「计算专用」契约相反，可能诱发 commonPool 内提交死锁形态（caller 也参与执行）。全仓当前 grep 无任何生产 `async()` 缓存调用者——激活面为设施首用，故按口径降 P3 并如实标注。
- **现状**: 平台已有 globalWorker 等受治理池，但异步缓存设施默认绕开它们且无文档声明。
- **建议**: `buildCache` 增加 executor 注入点，默认 `GlobalExecutors.globalWorker()` 或要求调用方显式传入；owner 文档（`docs-for-ai/` cache 条目）声明 async 模式的线程归属。
- **失效面枚举**（F3，Caffeine executor 注入点）：`LocalCache.buildAsync` 两处（loader/无 loader，缺陷面相同）；`CaffeineCacheLoader.asyncLoad/asyncReload`（透传 Caffeine 给的 executor，即 commonPool，同根因）；同步路径 `builder.build(...)`（无 executor 面，不受影响）。同族默认池绕行：`ForkJoinPool.commonPool` 间接使用点属各业务模块 C3/C4 范围，不在本轮重复登记。
- **信心水平**: 很可能（Caffeine 默认 executor 行为为其明文文档；「仓库当前无 async 缓存」为 grep 结论）
- **误报排除**: 非误报校准项；按「写不出已发生的具体时序」如实降 P3 并标注激活条件，不虚报。
- **复核状态**: 未复核

### [nop-kernel-concurrency-C4-010] 线程工厂纪律：命名/UEH 齐备（合格面），但私有 timer 非 daemon 默认 + register 顶替时 metrics 重复绑定不解除

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/thread/NamedThreadFactory.java:28, 81-90`；`thread/NopThread.java:48-56`；`DefaultScheduledExecutor.java:37-47`；`ExecutorHelper.java:201-208`；`GlobalExecutors.java:102-107`
- **证据片段**:
  ```java
  // NamedThreadFactory.java:82-88 —— 命名(prefix-poolN-threadN)/daemon/priority/UEH 齐备（合格）
  Thread t = new NopThread(r, namePrefix + '-' + poolNumber + '-' + threadNumber.getAndIncrement(), 0);
  t.setDaemon(isDaemon); ... t.setUncaughtExceptionHandler(uncaughtExceptionHandler);
  // NopThread.java:51-55 —— UEH 落 LOG.error，不静默（合格）；StopPooledThreadException 定向静默（配套死机件）
  // DefaultScheduledExecutor.newSingleThreadTimer —— 未设 threadDaemon → 非 daemon 线程
  ```
- **严重程度**: P3
- **并发场景**: 平台全部池线程统一经 `NamedThreadFactory`+`NopThread`（纪律合格：命名含池序号与线程序号、未捕获异常落 `LOG.error` 不静默、`StopPooledThreadException` 定向放行）。缺陷在两个邻接面。
- **触发时序**: (1) `newSingleThreadTimer`（`DefaultScheduledExecutor.java:37-47`）不设置 daemon → `SimpleRpcClientFactory`（每 clientName 一个 `simple-rpc-client-timer-*`）、`StreamControlRpcProxyFactory`（每 topic 一个 `stream-rpc-client-*`）在多 client/topic 下各持一条**非 daemon** 常驻线程；任何一处漏调 `destroy()`（两者当前均有 destroy，属「差一行代码」的回归面）→ JVM 挂住不退出。与 globalTimer/globalWorker 的 daemon=true 惯例不一致。(2) `GlobalExecutors.register` 顶替旧池时旧池 destroy，但其 `ExecutorServiceMetrics` 绑定（`ExecutorHelper.registerMetrics:201-208`，同名校验仅按 config.name）不解除 → 顶替后 micrometer 中同名 executor 双指标源，观测数据污染（`VirtualThreadTaskExecutor.registerGlobalWorker` 重复调用即触发，当前启动期单次调用，低危）。
- **现状**: 工厂纪律整体合格；两处邻接面为默认值与配套清理不一致。
- **风险**: 线程泄漏挂 JVM 退出（有 destroy 前提下是回归面而非现行缺陷）；指标重复绑定污染观测。
- **建议**: `newSingleThreadTimer` 显式 `config.setThreadDaemon(true)` 或提供 daemon 参数；`register` 顶替路径用 `ExecutorServiceMetrics` 的移除能力或按名去重绑定。
- **失效面枚举**（F3，线程创建点纪律核对）：`NamedThreadFactory`（合格）、`NopForkJoinWorkerThreadFactory`（继承 NopForkJoinWorkerThread，registerWorker 强制 daemon=true 已注释声明，合格）、`newSingleThreadTimer` 两消费点（非 daemon，缺陷面）、`ExecutorHelper.newSingleThreadExecutor`（daemon 显式参数，NioFileWatchService 用非 daemon + doStop 关闭，合格）、`VirtualThreadTaskExecutor`（虚线程无 daemon 语义，合格）、`register`→`registerMetrics`（重复绑定缺陷面）。
- **信心水平**: 确定
- **误报排除**: 未把「daemon 与否」一概当问题——global 池 daemon、CLI watch 非 daemon 均合理；只立「同族默认值不一致 + 配套清理缺失」两点。
- **复核状态**: 未复核
- **复核状态**: 已复核（2026-10-17 独立复核）——**保留 P3**。NamedThreadFactory 命名（prefix-poolN-threadN）/daemon/priority/UEH 合格面证实；newSingleThreadTimer 不设 daemon（ThreadPoolConfig.threadDaemon 默认 false）证实；NopThread UEH 落 LOG.error 证实；register→registerMetrics 同名重复绑定无解除证实。现状：已知未修复。

---

## 维度集外线索

### [nop-kernel-concurrency-CL-007]（维度集外线索，不立案展开）globalWorker 过载信号被 10000 深队列吸收：TaskQueue 弹性扩容使「队列优先于扩容」预设不成立，背压滞后属 C8 形态

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/TaskQueue.java:68-84`；`StandardThreadPoolExecutor.java:135-175`
- **实测结论（纠正预设）**: 任务口径假设「queue 10000 满后才扩到 max 30、突发下实际并发恒 1」——**live code 不成立**。`TaskQueue.offer` 是 Tomcat 弹性启发式：`StandardThreadPoolExecutor.execute` 先 `submittedCount.incrementAndGet()` 再执行 TPE 流程，当 `submittedCount > poolSize && poolSize < max` 时 offer 返回 false → TPE 立即扩线程（直至 30），**无需等队列满**。实现正确性核对：`<=` 判断（`offer:77`）配合 execute 的前置递增与 Tomcat 原版 `<` 语义等价，弹性扩容路径正确；满载（30 线程）后才轮转入 10000 深队列，队满才经 RejectHandler→`force(0)`→抛 `queue-full` REE。
- **C8 形态要点**: 真正的问题是过载信号滞后——globalWorker 为全平台共享池，max 30 后仍可积压 10000 任务（含每任务闭包/参数），一个慢业务即可无声占满，其他业务的任务排队 10000 深才得到第一个拒绝信号；无积压深度告警锚点（ThreadPoolStats 未暴露 submittedCount）。属背压/流控（C8）而非池参数纪律（C4），登记不展开。建议 C8 轮核对：globalWorker 消费方中无积压感知的 submit-and-forget 站点（NopDatavExportTaskBizModel.java:272、ReportDeliveryExecutor.java:175、PageProvider.java:91,126、WebPageExporter.java:117、BeanContainerImpl.java:542、DebeziumEngineWrapper.java:159、ShellCommandExecutor.java:60、AbstractBizEntityService.java:54 等）。
- **不立案理由**: 维度归属 C8；池参数与启发式实现本身正确。
- **复核状态**（2026-10-17 独立复核）: **确认——纠偏成立**。TaskQueue.offer:68-84 弹性启发式与 StandardThreadPoolExecutor.execute:152-153 前置 submittedCount 递增逐行核对：`submittedCount > poolSize && poolSize < max` 即返回 false 扩线程、满载（30）后入 10000 深队列、队满才 RejectHandler→force(0)→REE——第 1 轮「队列满后才扩容」预设的纠偏正确；ThreadPoolStats 确未暴露 submittedCount（getStats 仅 6 字段）。维持不立案、移交 C8 的处置。

---

## B 面结论（如实记录）

按基线 B-face enumeration 的 46 个 `nop-core`/`nop-xlang` 文件（EvalGlobalRegistry、StdDomainRegistry、ScriptCompilerRegistry、XPathOperatorRegistry、EvalBackendRegistry、ReflectionManager、ModuleManager、ResourceLoadingCache 族、ResourceComponentManager、GlobalStatManager、ErrorMessageManager、JsonTool、DictProvider、I18nMessageManager、TenantAware 族、XDslParseHelper 等）逐文件 grep `ExecutorService|new Thread|ThreadPoolExecutor|ScheduledExecutor|CompletableFuture.|runAsync|supplyAsync|scheduleAtFixedRate|scheduleWithFixedDelay|ForkJoin`：**0 命中**。B 面（全局模型缓存与注册表单例）不含任何线程池/调度器/线程创建载体，**C4 维度 B 面零发现**。其「检查-然后-放入」缓存竞态等并发面归 C1/C2 维度，本轮不越界。

## 总计

- **C4 立案发现：10 条**——P1 × 1（C4-001 globalTimer 共享定时器容量死值+阻塞放大）、P2 × 5（C4-002 关闭语义/C4-003 工厂零校验/C4-004 无界全局 cached 池/C4-005 全局单例可被 destroy 不可恢复/C4-006 updateThreadPool 缩容条件反置）、P3 × 4（C4-007 池契约缺失/C4-008 Tomcat 死机件/C4-009 异步缓存默认 commonPool/C4-010 工厂纪律邻接面）。
- **CL 维度集外线索：1 条**（CL-007，C8 形态：过载信号被 10000 深队列吸收 + TaskQueue 实测弹性扩容纠正预设）。
- **B 面：零发现**（无线程池载体，已如实记录）。
- **未立案邻接面核对说明**：BindScheduledExecutor 关闭语义（destroy 委托面）并入 C4-005 失效面枚举；多任务共享单 BindScheduledExecutor 核对无相互干扰（每任务独立 future/timerHandle）；`DefaultTaskExecutionQueue.ownExecutor`、`NioFileWatchService.doStop`、`LocalResourceLockManager.destroy`、两个私有 timer 的 destroy 为核对合格面，已在失效面枚举中逐一标注状态。

---

## 维度复核结论（独立复核子代理，2026-10-17）

复核范围：C4-001~010（本文件）+ C4-011/012 与 CL-023/024（`C4-round2.md`）+ CL-007 纠偏判定，合计 12 条发现 + 3 条 CL 线索。复核方法：逐条亲自打开 file:line 核对 live code（分支 `concurrency-audit-remediation`，HEAD=8c13908bfd），关键「零调用者」论断均由复核人独立 grep 复跑；并按本阶段解除盲审的指令对照历史台账 `ai-dev/audits/cra-index.md`（WI 1.5 C4-001、WI 1.6 CL-002/CL-006 关联面）与 git log 核实现状。各条目的逐项裁定已回填至两文件各条目的第二条 `**复核状态**` 行。

### 裁定汇总表

| ID | 初审判级 | 复核裁定 | 现状 | 证据复核要点 / 修正 |
|---|---|---|---|---|
| C4-001 | P1 | **保留 P1（证据修正）** | 已知未修复 | 死配置+副作用改写、28 文件终表完整性、CLI 族纠偏全部证实；**修正：checkTimeout 实为内存锁表遍历非「DB 扫描」（无子类），DB 相关站点 3 非 4；全停需第 4 任务受阻合取；T=120s 为示意值** |
| C4-002 | P2 | **保留** | 已知未修复 | 全部子项证实（死配置 0 命中、destroy 不对称、双 NPE 面、submittedCount 泄漏路径精确、shutdownAndAwaitTermination 全仓 0 调用者） |
| C4-003 | P2 | **保留** | 已知未修复 | MAX_VALUE/-1 实证、F3 11 调用点完整、init 无 core>max 守卫证实 |
| C4-004 | P2 | **保留（证据修正）** | 已知未修复 | 无上界本体与 6 消费方证实；**修正：SocketServer maxConnections 默认 10，「10k 线程」默认配置不可达，主可达面=batch 分片** |
| C4-005 | P2 | **保留** | 已知未修复 | 不可恢复链逐步证实、unregister 0 调用者、ownExecutor 范式核实存在 |
| C4-006 | P2 | **保留** | 已知未修复 | 方向反置确定性证实；可达性澄清：bean 管理池实证可达（IoC getRefreshConfigMethod 派发、nop-job beans.xml 有实例），globalWorker/globalTimer 静态 config 为理论面 |
| C4-007 | P3 | **保留** | 已知未修复 | null/no-op、接口族不一致、切换面、nop-sync-executor 解析盲区全部证实 |
| C4-008 | P3 | **保留** | 已知未修复 | 0 调用链与死 knob grep 证实、非 volatile 证实 |
| C4-009 | P3 | **保留（条件化标注维持）** | 已知未修复 | buildAsync 无 executor 注入、async() 生产 0 消费者证实 |
| C4-010 | P3 | **保留** | 已知未修复 | 纪律合格面与两邻接面证实 |
| C4-011（round2） | P2 | **保留** | 已知未修复 | 两死字段 0 消费者、prestart 机制、分支丢弃矩阵全部证实 |
| C4-012（round2） | P2 | **保留** | 已知未修复 | 开关默认 true、注册名不符/对平台线程模型零影响、假参数、无 destroy 覆写、零 Java 消费者、按名字符串通道全部证实 |

### C4-001 终裁（特别复核指令）

**终裁：P1 维持**，附证据修正。理由：(a) 核心事实链成立——`newScheduledExecutor:63` 的 `config.setMaxPoolSize(0)` 对 `globalTimer()` 读入的 CFG（默认 5）构成确定性副作用丢弃，运维无任何配置自救通道，且改写的是 executor 持有的同一 config 对象（`getConfig().getMaxPoolSize()` 恒 0、`refreshConfig` 首分支永久失活，均亲自核实）；(b) 28 文件消费方终表经复核人独立 grep 完整无缺漏，CLI 族 5 站点「直连非 executeOn」纠偏成立（CliRunCommand:90 直连调度任意 .xrun 任务为最强暴露面）；(c) DB stall 相关性放大论证在修正后仍成立：SysDaoNamingService.cleanup（deleteByQuery DB 删除）、JdbcConfigSource.refreshConfig（DB 查询）、ScheduledRecoveryManager DB 扫描 3 站点同库相关，DB stall 单一常见条件即阻塞 3/4 线程，全停（4/4）按 round2 概率模型的合取式 `P(DB stall ∧ 任一第 4 任务受阻)` 达成，第 4 任务来源充足（文件刷新 hang、WS 慢消费 send、WI 1.5 使用方业务体占用）；(d) 与 **WI 1.5 C4-001（MA-04，open/待 7.1）为同设施两面**：WI 1.5 立使用方占用面（wf/task retry/delay 业务体+saveState 簿记直连占用 timer），本条立设施本体面（容量死配置+副作用改写+无隔离放大）——两者独立成立、互补不重复，且 WI 1.5 的使用方事实交叉印证了本条 P1 的占用严重度（生产部署下 timer 已承载业务体，非纯轻量触发）；修复两面对接同一个 globalTimer 隔离/迁移方案。若修复排期需收缩表述，可拆为「P1（设施面：死配置+副作用改写，零条件确定性）+ P2（放大面：全停合取）」，但不改变整条立案为 P1 的终裁。

**随本终裁的强制修正项**：第 1 轮触发时序与本报告 round2 终表 A 类中 `LocalResourceLockManager` 行的「checkTimeout DB 锁过期扫描 / 阻塞源 DB」应更正为「checkTimeout 内存锁表遍历（locks.values() 短临界区 synchronized）/ 阻塞源=内存（轻）」——该类无 DB 访问、无子类（复核人已核实）。修复 plan 引用终表时不得沿用错误表述。

### CL 线索处置

| 线索 | 处置 | 核实要点 |
|---|---|---|
| CL-007 | **确认（纠偏成立）** | TaskQueue.offer:68-84 与 execute:152-175 逐行核对，弹性扩容描述准确；ThreadPoolStats 确无 submittedCount。维持不立案、移交 C8 |
| CL-023 | **确认** | 抽验 3 站点属实（stream TaskExecutor newFixedThreadPool、CachingTableLookup:193-199 双裸建、NopStreamThreadFactory:48 裸 new Thread 无 UEH）；复核人 grep nop-stream main 共 26~27 处，与报告 13+13 吻合。模块面归属维持 |
| CL-024 | **确认** | SimpleRpcClientFactory:122/:126、StreamControlRpcProxyFactory:62 证实；prestartAllCoreThreads 无条件预启动 × allowCoreThreadTimeOut 全平台死值（C4-011）机制链证实。移交 C5/C7 维持 |

### 现状标注

git log（HEAD=8c13908bfd）核实：本维度 12 条发现涉及的设施文件（nop-commons concurrent/executor、cache、nop-commons-java21）自审计基线以来**无任何修复提交**（近期提交均为 WI 1.x 文档与 nop-stream 连接器修复）。**全部 12 条 = 已知未修复**；3 条 CL 为登记线索不涉修复。

### 历史对照（本阶段解除盲审）

- **WI 1.5 C4-001**（cra-index P1 表 MA-04，open）：同设施两面关系见 C4-001 终裁 (d)。
- **WI 1.6 CL-002**（confirmed）：doScan 体经 BindScheduledExecutor 实占 globalWorker——对本维度 C4-001 分类表的含义：C 类「executeOn 自救」是把占用从 4 线程 timer 转嫁到 30 线程 worker，全局池族的跨面占用在 WI 1.6 已有使用方条目；本维度建议 (3)「重活强制 executeOn」的修复方案实施时，须与 WI 1.6 CL-002 同批复核 worker 侧容量。
- **WI 1.6 CL-006**（confirmed，留 rpc 单元）：完成回调占用进程级共享 RPC 线程池——与本维度 C4-004 同属「进程级共享执行资源无隔离」形态，修复 plan 应合并考量共享资源隔离策略。
- **WI 1.7 nop-batch CL-004**（独立行动项：exp 线程池无界队列+cachedThreadPool 切换）：与本维度 C4-003/C4-004 指向同一站点（ExportDbTool/ImportDbTool 的 Integer.MAX_VALUE 队列与 cachedThreadPool executor），修复须合并立项防重复。

### 复核统计与争议点

**统计**：12 条发现**全部保留，0 驳回，0 判级变更**（终版 P1×1 + P2×7 + P3×4）；**证据修正 2 条**（C4-001 checkTimeout 内存面、C4-004 maxConnections=10 封顶）；**可达性补充 1 条**（C4-006 热更派发面）；**CL 3 条全部确认**；F3 完备性经复核人独立 grep 证实（28 文件终表、newExecutor 11 调用点均无缺漏；复核过程中确认 ReportController.java:133 位于 `nop-demo/nop-spring-report-demo`，路径含 `/spring/test/`，普通 `/test/` 过滤易误排除——初审未漏记，round2 补记正确）。

**争议点**：
1. C4-001 的 checkTimeout「DB 扫描」表述错误（实为内存遍历）——不改判级，但为强制修正项（见终裁节）。
2. C4-004 的「10k 线程」数字默认配置不可达——P2 立案本体不依赖该数字，修复表述应以 batch 分片为主可达面。
3. C4-001 P1 与「全停需 4/4」的张力——维持 P1 的依据：死配置为零条件确定性缺陷 + 结构性超订（服务器部署下 A 类周期直连任务数 ≥ 4 线程容量，WI 1.5 使用方占用交叉印证）+ 判级口径明文「全局池占用/饿死面常见条件门槛极低」；如需收缩可拆分表述但不降整条判级。
4. C4-006 globalWorker 热更可达性弱化（静态 config 非派发对象）——不影响 updateThreadPool 本体的方向缺陷成立（bean 管理池实证可达）。

---

## 最终保留项（C4 维度终版清单）

终版 = 第 1 轮 10 条 + 第 2 轮 2 条，全部保留、判级不变，现状均为「已知未修复」。修复入口：设施本体条目（C4-001/002/003/004/005/006/011/012）单点修复即全族生效；C4-007/008/009/010 为治理/观测面可随后批处理。

| ID | 判级 | 一句话 | 现状 | 关联/修复注意 |
|---|---|---|---|---|
| C4-001 | P1 | globalTimer 4 线程共享定时器：唯一容量配置为死值且被工厂副作用改写丢弃、直连与转发消费方混用无隔离、DB stall 相关性放大 | 已知未修复 | 同设施使用方面=WI 1.5 C4-001（open）；**修复须同步修正终表 checkTimeout「DB 扫描」表述为内存遍历** |
| C4-002 | P2 | 池关闭语义：waitForTasks/awaitTerminationSeconds 死配置、destroy 不等待在飞任务、两类池 destroy 互斥、shutdown 后提交泄漏 submittedCount | 已知未修复 | `ThreadHelper.shutdownAndAwaitTermination` 为现成修复件（0 调用者） |
| C4-003 | P2 | newExecutor/ThreadPoolConfig 工厂零校验：MAX_VALUE 队列实证、负值静默 cached、init 无 core>max 守卫 | 已知未修复 | 站点面与 WI 1.7 nop-batch CL-004 重叠，须合并立项 |
| C4-004 | P2 | nop-cached-thread-pool 按名共享无上界（0/2³¹-1/SQ）：线程爆炸无熔断无配额 | 已知未修复 | 主可达面=batch 分片（SocketServer 受 maxConnections 默认 10 封顶） |
| C4-005 | P2 | 全局池单例借出即可 destroy 且不可恢复（computeIfAbsent 返回死实例）、unregister 死 API、register 顶替不 drain+metrics 不解绑 | 已知未修复 | ownExecutor（DefaultTaskExecutionQueue:138-155）为现存正确范式 |
| C4-006 | P2 | updateThreadPool 缩容方向条件反置：新 max < 当前 core 必抛裸 IAE，缩容功能实际不可用 | 已知未修复 | 实证可达面=IoC bean 管理池；globalWorker/globalTimer 静态 config 为理论面 |
| C4-007 | P3 | SyncThreadPoolExecutor/VirtualThreadTaskExecutor 契约缺失：stats/getConfig null、destroy no-op、同步/异步切换无标志、nop-sync-executor 按名解析盲区 | 已知未修复 | — |
| C4-008 | P3 | StandardThreadPoolExecutor 携带 Tomcat 死机件：contextStopping 0 调用链、forcedRemainingCapacity 非 volatile、CFG_CONTINUATION_MAX_QUEUE_SIZE 死 knob | 已知未修复 | 与 C4-001/011 同族死配置面 |
| C4-009 | P3 | LocalCache async 模式隐式落 ForkJoinPool.commonPool，脱离平台池治理（当前无生产 async() 消费者，条件化） | 已知未修复 | 激活前修 |
| C4-010 | P3 | 线程工厂纪律合格；邻接面：newSingleThreadTimer 非 daemon 默认、register 顶替 metrics 重复绑定 | 已知未修复 | — |
| C4-011 | P2 | ThreadPoolConfig 死旋钮族：threadPriority/allowCoreThreadTimeOut 全仓零消费者；工厂分支与热更路径丢弃矩阵；prestartAllCoreThreads×死超时=core 线程永久常驻 | 已知未修复 | 与 C4-001/002/008 合并为系统性死配置面；prestart 机制同时支撑 CL-024 |
| C4-012 | P2 | nop-virtual-thread 治理面：默认 true 误导性开关（对平台线程模型零影响）+注册表假参数实体+destroy 不回收+re-init 顶替销毁借出引用 | 已知未修复 | 按名通道=ModelBasedBatchTaskBuilderFactory.getExecutor |

CL 线索终版：CL-007 确认（纠偏成立，移交 C8）；CL-023 确认（nop-stream 模块面，待派工核对）；CL-024 确认（每实例线程累计机制，移交 C5/C7）。
