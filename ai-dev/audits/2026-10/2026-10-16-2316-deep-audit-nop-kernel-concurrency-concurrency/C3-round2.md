# C3 异步链路正确性 —— 第 2 轮深挖（盲审）

## 审计单元

- **审计对象**：nop-entropy 平台并发设施自身实现
  - A 面：`nop-kernel/nop-commons/src/main/java/io/nop/commons/{concurrent,cache,pool}`
  - B 面：`nop-kernel/nop-core`、`nop-kernel/nop-xlang` 全局模型缓存与注册表单例（基线 B-face enumeration）
- **仓库**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`，HEAD `8c13908bfd`）
- **维度**：C3 异步链路正确性（异常传播/超时/取消语义、future 转换链同一性、异步回调上下文传播、异步结果与调用方契约）
- **判级口径**：平台设施被全平台并发调用；P0=默认用法+常见条件即触发且不可逆；无生产调用方的设施 API 按「设施可用 API 的真实缺陷 + 未来使用方失效面」降 P2/P3。

## 本轮输入

- 第 1 轮报告 `C3-async-chains.md`（C3-001~014：P1×1 周期任务设施无统一异常绝缘层 / P2×12 / P3×1）。
- 基线 `_tmp/cra-baseline-nop-kernel-concurrency.txt`（第 1 轮已全文阅读，本轮沿用其 B-face enumeration 口径）。
- live code（当前工作区，全部结论基于现场取证）。

## 盲审纪律声明

本轮为盲审轮次。仅读取了本目录第 1 轮文件 `C3-async-chains.md`（唯一获准输入）；未读取 `ai-dev/audits/` 下其他维度文件（C1-shared-mutable-state.md、C2-lock-sync.md、dispatch-log.md 等）、未读取 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件。`nop-api-core` 的 `FutureHelper`/`ContextProvider` 按既定口径仅作对照，不作为立案主体。C1-005（scheduleWithRandomDelay 站点，C1 维度已立案）内容不可读，凡涉及该站点的新缺陷均显式标注可能与 C1-005 重叠、留对账。

## 本轮目标（逐条执行）

1. **① C3-001 失效面扩展**：反向枚举 A/B 面全部周期任务调用点，产出「周期任务调用点绝缘状态表」（见下）。
2. **② future 转换链断裂全扫**：ExecutorHelper / BindScheduledExecutor / DefaultScheduledExecutor / DefaultThreadPoolExecutor / StandardThreadPoolExecutor 全部公开方法「返回 future vs 异常落点」逐一判定（结果见 §2）。
3. **③ 取消语义全扫**：Cancellable/ICancellable 实现族、future.cancel 与包装层耦合、周期任务 cancel 后的在飞迭代（结果见 §3）。
4. **④ B 面异步面核查**：ResourceLoadingCache/TenantAwareResourceLoadingCache/ResourceComponentManager 刷新触发同步性量化；LocalCache refreshAfterWrite 异步刷新链异常落点（结果见 §4）。
5. **⑤ 上下文传播缺失面**：A 面 executor 传播矩阵表（结果见 §5）。

---

## §1 指令①：周期任务调用点绝缘状态表（C3-001 修复直接输入）

全量 grep（`scheduleAtFixedRate|scheduleWithFixedDelay|scheduleWithRandomDelay`，src/main，33 文件 30+ 站点）后逐点通读任务体。**核心结构结论（第 2 轮新认知，修正第 1 轮 C3-001 中「BindScheduledExecutor 专门做了绝缘」的表述）**：

> **平台不存在任何「续链」周期设施。三条周期路径在任务体抛出 Throwable 时全部终止调度链**：(a) `DefaultScheduledExecutor`（裸 JDK 语义：异常吞入 ScheduledFuture，后续执行被抑制，无日志）；(b) `BindScheduledExecutor.scheduleWithFixedDelay`（catch(Throwable)→`future.completeExceptionally`→下一轮 `isDone()` 守卫退出——**同样是杀链**，区别仅是 LOG.error + future 可观测）；(c) 自续链（`schedule` 一次性完成后手动重排，如 `AbstractPollingLeaderElector`：一次 Throwable→future 异常完成且被丢弃→重排不再发生）。`ExecutorHelper.scheduleWithRandomDelay` 亦为杀链（catch(Throwable)→completeExceptionally，不重排）。因此 **C3-001 的修复不能把 BindScheduledExecutor 现状当作"绝缘=续链"的样板**：若按其现状对齐，得到的仍是杀链+日志；设施级修复需要新增「catch(Throwable)→log→强制下一轮续排」的续链语义或显式的 kill+alarm 契约（全平台当前无一处实现续链）。

调用方 `catch(Exception)` 能把业务异常挡在设施语义之外（三路径都不杀链），残余风险统一是 **Error（以及 catch 宽度不足的 RuntimeException 谱系）**。

### 周期任务调用点绝缘状态表

| # | 调用点 | 载体（设施路径） | 任务体绝缘 | Throwable 杀链后果 |
|---|---|---|---|---|
| 1 | `CliRunCommand.java:90` | globalTimer（裸 JDK） | **全裸**（runTasks 直接抛 NopException，无 catch） | 一次任务模型异常→watch 轮询死亡；`System.in.read` 保活进程，停摆完全不可感知 |
| 2 | `JsonRpcWebSocketHandler.java:82` | globalTimer（裸 JDK） | 仅 `catch(IOException)`（357-363 行） | 消息序列化 RuntimeException/Error→keepalive 死→僵尸 WebSocket 连接永不清扫 |
| 3 | `SysDaoNamingService.java:77` | globalTimer（裸 JDK） | catch Exception（89-101 行，生产注释自证 JDK 杀链语义） | Error→临时实例清理死→注册表残留脏数据 |
| 4 | `AbstractFileConfigSource.java:45` | globalTimer（裸 JDK） | catch Exception（59-70 行）；变更回调 `task.run()` 在 try 内且在 **timer 线程**同步执行 | Error→配置热刷新死；慢回调占用共享 timer 线程（见 F3 注记） |
| 5 | `JdbcConfigSource.java:54` | globalTimer（裸 JDK） | catch Exception 双重（76-93 行），errorCount 抑制日志 | Error→配置热刷新死 |
| 6 | `LocalResourceLockManager.java:105` | globalTimer（裸 JDK） | 无 catch；任务体近无抛点（同 R1 判定） | Error→租约超时清理死→锁泄漏至重启 |
| 7 | `RpcMessageHandler.java:73`（nop-netty） | **Netty EventExecutor.scheduleWithFixedDelay（非平台设施）** | **无 catch**（76-99 行 while 循环裸奔；循环体抛点少） | 未捕获异常在 Netty 调度语义下→超时清扫死→ResponseFuture 泄漏、请求永不超时 |
| 8 | `SocketClient.java:133` | IScheduledExecutor（注入，生产为 globalTimer，裸 JDK） | catch Exception（150-152 行） | Error→心跳死→连接假死不可检测 |
| 9 | `RpcTaskMonitor.java:74` | timer（注入 IScheduledExecutor，裸 JDK） | 逐任务 catch Exception（104-108 行，注释自证 JDK 杀链语义） | Error→任务状态监控死→卡死 RPC 任务永不回收 |
| 10 | `AutoRegistration.java:206` | globalTimer.executeOn(globalWorker)=**BindScheduledExecutor** | catch Exception（209-216 行） | Error→Bind 杀链（有日志）；注册心跳死 |
| 11 | `RetryScannerImpl.java:96` | globalTimer.executeOn(globalWorker)=Bind | doScan 外层 catch Exception + processor 逐批 catch Exception | Error→Bind 杀链；重试扫描死→重试记录永久滞留 |
| 12 | `AbstractBatchScanner.java:63` | globalTimer.executeOn(globalWorker)=Bind | **部分裸**：`cursor.reset()`/`onCycleStart()` 在 try 外（86-91 行），仅 `scanBatch()` 在 catch(Exception) 内 | onCycleStart/cursor.reset 抛 RuntimeException 即杀链；5 个子类扫描器全部继承该暴露面 |
| 13 | `SysDaoMessageService.java:217/221/228` | timer.executeOn(globalWorker)=Bind | 三入口均 catch Exception + 生产注释自证（365-373、300-306、262-289 行） | Error→事件消费死（注释已认知） |
| 14 | `AbstractPollingLeaderElector.doStart/scheduleCheck`（自续链） | globalTimer.executeOn(globalWorker)=Bind.schedule | checkElection 实现（JdbcLeaderElector:115-、SysDaoLeaderElector:46-67）均 catch Exception | Error→选举轮询死且 future 被丢弃→节点静默退出选举博弈 |
| 15-22 | nop-stream 8 处（`CachingTableLookup:205`、`TaskManager:343`、`JobCoordinator:693/1309`、`PollingJdbcMessageService:293`、`OpsJobManager:342`、`TaskCheckpointWiring:275`、`StreamMetricsReporter:80`） | **raw `Executors.newSingleThreadScheduledExecutor`/`newScheduledThreadPool`（绕过平台设施）** | 各自不等（R1 已登记为登记项，未逐点展开——越出 A/B 面） | 各自独立调度器，平台设施修复不覆盖 |
| 23 | `SessionLockRenewal.java:62`（nop-ai-agent） | **raw `Executors.newSingleThreadScheduledExecutor`（专用线程）** | 仅 `catch(RuntimeException)`（69-77 行） | Error→续租死→租约过期→**会话双执行风险**（该链的安全意义最重） |
| 24 | `ScheduledRecoveryManager.java:361`（nop-ai-agent） | scheduledExecutor（注入 ScheduledExecutorService） | 仅 `catch(RuntimeException)`（scanOnceSafe，注释声称"never silently killed"但 RuntimeException 宽度不足） | Error→恢复扫描死→孤儿会话/陈旧锁永不清理 |
| 25 | `TeamTaskSchedulerDaemon.java:237`（nop-ai-agent） | scheduledExecutor（注入） | 仅 `catch(RuntimeException)` | Error→团队任务调度死 |
| 26/27 | `DBMessageService.java:213/217`（nop-ai-agent） | **raw 单线程 poller** | pollAllTopics 逐 topic catch Exception（323-327）；sweep catch Exception（576-580） | Error→消费+sweep 双死→消息积压 |
| 28 | `FeishuClient.java:262`（nop-integration-feishu） | **raw 单线程 scheduler** | sendHeartbeat catch Exception（276-279 行） | Error→心跳死→连接断不可自愈 |
| 29 | `MockScheduledExecutor`（nop-autotest，mock 实现） | DelayQueue 自实现 | 测试设施，不列生产失效面 | — |
| 30 | `IScheduledExecutor.scheduleWithRandomDelay` default → `ExecutorHelper.scheduleWithRandomDelay` | 调用方传入 IScheduledExecutor | 见 C3-017（杀链+无日志+执行中取消失效） | 生产 0 调用 |

**F3 注记（设施侧）**：globalTimer 为 core=4 的共享 STPE（`ThreadPoolConfig` 默认 corePoolSize=4；`ExecutorHelper.newScheduledExecutor` 62-63 行显式注释 maxPoolSize 无效）；`AbstractFileConfigSource`/`JdbcConfigSource` 的变更回调 `task.run()`（ConfigChangeApplier→缓存/池重建类重活）同步运行在共享 timer 线程上，与全平台 26 处周期任务互相形成尾延迟影响——共享定时器无队列隔离，属架构权衡，本轮不单独立案（记入 C3-015 F3，供设施修复时一并决策）。

---

## §2 指令②：future 转换链断裂全扫

5 个类全部公开方法逐一判定（「返回 future 是否忠实反映任务终态/取消传播是否挂接」）：

| 方法 | 判定 |
|---|---|
| `ExecutorHelper.submit(ExecutorService,Callable)` / `submit(ExecutorService,Runnable,T)` | catch(Throwable)+completeExceptionally+bindCancel+finally detach——异常落点正确；**但 bindCancel 在成功路径必然误触发 cancel(true)**→**新立案 C3-016** |
| `ExecutorHelper.schedule(STPE,Callable,delay,unit)` | 同上结构→归并 C3-016 |
| `ExecutorHelper.scheduleWithRandomDelay` | 杀链+无 LOG；**执行中取消窗口**→**新立案 C3-017**（站点可能与 C1-005 重叠，需对账） |
| `ExecutorHelper.throttleExecute` | R1 已立案（C3-005/C3-006），无新面 |
| `BindScheduledExecutor.schedule/submit/submit(Runnable,result)` | 取消不传播——R1 已立案（C3-004）；executeOn 私有 thunk 的异常落点正确（catch Throwable 双层） |
| `BindScheduledExecutor.scheduleAtFixedRate/scheduleWithFixedDelay/execute` | R1 已立案（C3-002/C3-003/C3-013） |
| `DefaultScheduledExecutor.submit×2/schedule` | 委托 ExecutorHelper——继承 C3-016 |
| `DefaultScheduledExecutor.scheduleAtFixedRate/scheduleWithFixedDelay/execute` | 裸 JDK——R1 已立案（C3-001/C3-013） |
| `DefaultThreadPoolExecutor.submit×2` | 委托 ExecutorHelper——继承 C3-016（checkEnabled 缺失已由 R1 移交 C1/C10） |
| `StandardThreadPoolExecutor` | 无 future 方法；execute(force) 异常路径为同步 REE（fail-fast 正确）；afterExecute 自杀换线程为设计行为 |
| `SyncThreadPoolExecutor.submit` | catch Throwable 同线程完成——future 先完成再返回，取消语义天然豁免（R1 判定维持） |
| `IScheduledExecutor.scheduleWithRandomDelay` default | 委托 ExecutorHelper——归并 C3-017 |
| `RateLimitExecutorImpl.throttle/debounce` | debounce 非原子→**新立案 C3-020**；throttle→completeAfterTask（Exception 宽度）根因在 api-core（R1 已判定仅对照+移交） |

---

## §3 指令③：取消语义全扫

- **`Cancellable`（`io.nop.commons.lang.impl.Cancellable`）**：CAS 原子置位（单 winner）、回调在锁外执行、回调异常 catch(Throwable) 逐个隔离、`appendOnCancel` 在已取消态立即执行——**取消语义正确，不立案**。取消感知模型为**标志位轮询+回调**，无中断传播——这是平台显式模型（`ICancelToken`/`bindCancelToken` 均同），不属缺陷。
- **`future.cancel` 与包装层耦合**：`FutureHelper.bindCancel(promise, future)`（api-core，对照）守卫为 `if (!future.isDone()) future.cancel(true)`——在 ExecutorHelper 的「任务体内 complete(promise)」写法下，**成功路径守卫恒真**→**新立案 C3-016**。`DefaultTaskExecutionQueue` 的 bindCancel 参数反接→**新立案 C3-018**。
- **周期任务 cancel 后的在飞迭代**：`BindScheduledExecutor.scheduleWithFixedDelay`：caller cancel→exceptionally 回调 cancel timerHandle（在飞轮次的 timer future cancel(false) 为 no-op）→command 跑完→107 行重排→**91 行 `future.isDone()` 守卫拦截**——在飞迭代自然结束后链正确终止，**该族取消语义正确**（对照组）。`ExecutorHelper.scheduleWithRandomDelay`：无 isDone 守卫→**cancel 命中任一在飞轮次即永久失效**→**新立案 C3-017**。裸 JDK 路径（DefaultScheduledExecutor）：caller 持 JDK Future，cancel(true) 可中断在飞轮次——正确。`AbstractBatchScanner.stopScanning`/`RetryScannerImpl.stopScanning`：cancel(false)+running 标志双保险，在飞 doScan 轮次跑完自然退出——正确。

---

## §4 指令④：B 面异步面核查

- **刷新触发同步性**（通读 `ResourceLoadingCache`/`ResourceCacheEntry`/`TenantAwareResourceLoadingCache` 确认 R1 结论并量化）：B 面**无任何异步刷新链**。刷新=失效后由下一个请求线程同步重载：`ResourceLoadingCache.get(path):190-198`→`ResourceCacheEntry.getObject(checkChanged,loader):176-215`→`synchronized(this){ loadObject(loader) }`。**阻塞量化**：(a) 失效后第一个请求线程独占 entry monitor 承担全量重载（磁盘 IO+XML 解析+XLang 编译+transform，单模型数百 ms 量级）；(b) 同路径并发请求在 monitor 上串行等待全额重载时长；(c) 不同路径请求各自在自己的请求线程上独立重载（并行但每个都阻塞各自调用方）；(d) 无后台预热/warmup 路径；(e) `checkChanged` 开启时每次访问都执行 `isChanged()`（依赖文件 mtime 检查）。**裁定**：同步重载是 B 面的既定执行模型（R1 已声明，C2 轮已立案锁内加载的死锁面），本轮不作为新缺陷立案；量化结论供 C6（资源生命周期）与性能维度参考。
- **`LocalCache.refreshAfterWrite` 异步刷新链异常落点**：`buildCache:104-106` 对 **sync LoadingCache 同样接线 refreshAfterWrite**；Caffeine（3.2.x）对 sync LoadingCache 的自动刷新也提交到 cache executor——`LocalCache.buildCache` 从未调用 `builder.executor(...)`→**ForkJoinPool.commonPool**。异常落点已用 `~/.m2` 中 caffeine-3.2.4 反汇编核实：`LocalLoadingCache.refresh` 的 whenComplete 内含字符串常量 `"Exception thrown during refresh"`——异常仅达 **Caffeine 内部 JUL logger**，平台 SLF4J LOG/指标体系无通道，旧值保留、静默继续服务。`ICacheLoader.reload/asyncReload` javadoc 自述 "all exceptions will be logged and then swallowed"——行为被适配层文档化，但日志通道（JUL vs 平台 LOG）与执行器归属（commonPool）是 A 面装配缺口。→**新立案 C3-019**（修正 R1 C3-010 的 F3 中「同步分支无异步载体，豁免」的判定——该豁免在 refreshAfterWrite>0 时不成立）。

---

## §5 指令⑤：上下文传播矩阵表（C3-013 的修复面扩展）

grep 证实：A 面 concurrent 包中 `ContextProvider` 仅出现在 `ExecutorHelper` 3 处 `finally detachContext()`（142-144/165-168/190-193 行）。**A 面无任何方法捕获/重绑调用方 IContext**（显式传播为既定设计，非缺陷）；差异仅在 detach 卫生。

| 方法 | 传播 IContext | finally detach 卫生 | 后果 |
|---|---|---|---|
| `ExecutorHelper.submit×2 / schedule` | 否（声明边界） | **有** | 卫生正确样本；池线程不留孤儿 context |
| `DefaultThreadPoolExecutor.execute` / `DefaultScheduledExecutor.execute` | 否 | **无** | raw execute：任务体内 `getOrCreateContext()` 创建的 context 永久驻留 worker（C3-013 主面） |
| `DefaultThreadPoolExecutor/DefaultScheduledExecutor.submit/schedule`（经 ExecutorHelper） | 否 | 有 | 卫生正确 |
| `BindScheduledExecutor.execute / executeOn(thunk)` | 否 | **无** | 同 raw execute；AutoRegistration/SysDaoMessageService/RetryScanner/AbstractBatchScanner 的周期任务体都经此路径在 globalWorker 上执行 |
| `BindScheduledExecutor.schedule/submit`（timer 段 lambda） | 否 | **无**（lambda 在 timer 线程仅做 execute 转发，抛点在 executor 段，后者无卫生） | timer 线程若被任务体间接触碰 getOrCreateContext 同样驻留 |
| `SyncThreadPoolExecutor.execute/submit` | 否 | 不需要（同线程 continuation，无跨线程边界） | 豁免 |
| `SequentialTaskExecutor.doTask`（actor 循环） | 否 | **无** | 单次 `executor.execute(doTask)` 内连续跑多个业务任务，任一任务残留 context 会串给同批后续任务（比单任务边界更宽） |
| `BlockingSourceConsumeTask.run`（常驻消费线程） | 否 | **无** | 常驻线程孤儿 context 驻留效应最强（生命周期=进程） |
| `LimitedExecutor.execute` | 否 | **无** | 0 生产调用方，随设施修复 |
| `RateLimitExecutorImpl.throttle/debounce` 任务体 | 否 | 间接有（经 `DefaultScheduledExecutor.schedule`→ExecutorHelper 的 detach） | 卫生正确 |
| globalTimer 周期任务体（裸 JDK 路径 9 处） | 否 | **无** | 周期任务体内调 `syncGet` 等会创建 timer 线程孤儿 context；`TenantAwareResourceLoadingCache.getCache` 读到空租户→走 shareCache（C3-013 已指认的连锁终点，矩阵确认裸 JDK 路径全部无卫生） |

**裁定**：矩阵未产生超出 C3-013 的新立案面（C3-013 已立案「卫生不对称+孤儿 context 驻留」）；本表作为 C3-013 修复的直接输入（须补卫生的站点清单），并新登记 **CL-022**（具名长驻载体清单）。

---

## 第 2 轮新增发现

### [nop-kernel-concurrency-C3-015] 周期任务调用点绝缘状态表与「全路径杀链」语义澄清（C3-001 失效面扩展）：BindScheduledExecutor 的 catch(Throwable) 是杀链可观测化而非续链，两类新裸奔站点补登

- **文件**： `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/BindScheduledExecutor.java:99-107`（主证）；`nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java:86-99`；`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/ws/JsonRpcWebSocketHandler.java:357-363`；`nop-network/nop-netty/src/main/java/io/nop/netty/handlers/RpcMessageHandler.java:76-99`；`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/SessionLockRenewal.java:69-77`
- **证据片段**:
  ```java
  // BindScheduledExecutor.scheduleWithFixedDelay 的"绝缘"实际是杀链：
  try {
      command.run();
  } catch (Throwable e) {
      LOG.error("nop.err.thread-pool.execute-fail",e);
      future.completeExceptionally(e);   // future 进入异常终态
      return;                            // 不重排
  }
  schedule(command, delay, delay, unit, future, timerHandle); // 下一轮 isDone() 守卫退出
  ```
- **严重程度**: P2（族属 C3-001 P1 的失效面扩展；设施级 P1 主体维持 C3-001，本条为站点级枚举与语义修正，修复优先级随 C3-001）
- **并发场景**: 全平台周期任务沿三条载体路径（裸 JDK / BindScheduledExecutor / 自续链）执行；任务体异常与周期触发并发。
- **触发时序**: T1：任一周期任务体抛出 Throwable；T2：三条路径殊途同归——裸 JDK 抑制后续执行（无日志）、BindScheduledExecutor 完成 future 并在下一轮 isDone 守卫退出（有日志）、自续链的 schedule 返回 future 异常完成且被丢弃（重排不再发生）；T3：调用方持有的 future 无人监听（全部 30 处调用点中仅 doStop 时 cancel，无 whenComplete 停摆告警）；T4：周期链静默死亡，形态与 C3-001 一致，唯 Bind 路径多一条日志。
- **现状**: R1 C3-001 将 `BindScheduledExecutor.scheduleWithFixedDelay` 的 catch(Throwable) 表述为"专门做了绝缘"，易被读作"绝缘=续链样板"；本轮逐路径核对确认其同样是杀链语义（future 异常终态→isDone 守卫拦截后续轮次）。全平台**不存在任何一处续链实现**（`ExecutorHelper.scheduleWithRandomDelay` 亦杀链）。因此 C3-001 的修复必须新增续链语义（catch(Throwable)→log→强制下一轮续排）或显式 kill+alarm 契约，对齐 BindScheduledExecutor 现状是不足的。
- **风险**: 修复方向误判（按 Bind 现状对齐→得到仍是杀链）；两类 R1 未细化的裸奔站点补登后确认残余风险面：(a) `AbstractBatchScanner` 的 `cursor.reset()/onCycleStart()` 位于 try 外——RuntimeException 即杀链且 5 个子类扫描器继承暴露面；(b) `JsonRpcWebSocketHandler.sendKeepAlive` 仅 catch(IOException)——消息序列化 RuntimeException 即杀 keepalive；(c) `RpcMessageHandler.checkTimeout` 完全无 catch（Netty 载体）；(d) nop-ai-agent 三处仅 catch(RuntimeException)——Error 即杀，其中 `SessionLockRenewal` 杀链后果是会话双执行（安全语义最重）；(e) `CliRunCommand` 全裸。
- **建议**: 作为 C3-001 修复的直接输入：①设施层实现续链或 kill+alarm 契约时以本表逐点核对调用方预期；②对 (a)(b)(c)(d) 四类站点补 catch 宽度（Exception→Throwable 的决策随设施契约统一定夺）；③为周期 future 统一提供停摆告警挂接点。
- **失效面枚举**（F3）：见 §1 绝缘状态表全表（30 站点逐一状态）；设施侧 F3 注记：globalTimer 共享 4 线程无队列隔离、config 变更回调在 timer 线程同步执行；nop-stream 8 处 raw 载体归并 CL-021。
- **信心水平**: 确定（三路径代码逐行核对；Caffeine/Netty 外部语义仅涉载体描述不涉立案主体）
- **误报排除**: 不是对 C3-001 的重复立案——本条不含 C3-001 的设施主体缺陷，内容为 (1) 语义修正（绝缘≠续链，防止修复走偏），(2) R1 未逐点核实的站点级补登（R1 盲区自评第一条），(3) 修复输入表。生产注释（SysDaoMessageService 等）自证平台已知杀链语义，本条不与其冲突。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。三路径杀链语义亲核确认：Bind `scheduleWithFixedDelay` 的 catch(Throwable)→`completeExceptionally`→`return`（不重排）→下一轮 L91 isDone 守卫退出——杀链可观测化而非续链，R2 对 R1 表述的语义修正成立且必要（SysDaoMessageService 生产注释自证同识）。站点补登抽验属实（AbstractBatchScanner cursor.reset/onCycleStart 在 try 外（实际 L84-87，初判 86-91 为漂移）、JsonRpcWebSocketHandler sendKeepAlive 仅 catch IOException、CliRunCommand 全裸、SessionLockRenewal 仅 catch RuntimeException）；nop-stream“8 处”经重枚举确认准确（另两处 grep 命中为注释文本）。**F3 补全**：§1 表漏登 `RpcPollTaskManager.java:119`（nop-job-coordinator，platform IScheduledExecutor 载体，pollOnce 有 catch(Exception)+防御性 isDone 检查——绝缘状态与表中“调用方 catch Exception”族一致；实测生产周期站点总数 29 处）。判级与修复输入不变。现状：**已知未修复**。

### [nop-kernel-concurrency-C3-016] ExecutorHelper.submit/schedule 正常完成路径必然触发 bindCancel 误取消：底层 FutureTask 以 cancelled 终态收场并向 worker 注入中断

- **文件**： `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ExecutorHelper.java:128-150, 152-174, 176-199`（关键 137-148/159-172/184-197 行）；根因协作方 `nop-api-core/.../util/FutureHelper.java:313-318`（对照，不立案）
- **证据片段**:
  ```java
  // ExecutorHelper.submit：complete 发生在任务体内（底层 future 仍在运行）
  T result = task.call();
  future.complete(result);      // ← whenComplete 回调在此同步执行
  ...
  FutureHelper.bindCancel(future, f);
  // FutureHelper.bindCancel（api-core，对照）：
  promise.whenComplete((value, ex) -> {
      if (!future.isDone())     // ← 成功路径恒真：f 还在执行本任务
          future.cancel(true);  // ← 取消底层 + 中断执行线程
  });
  ```
- **严重程度**: P2
- **并发场景**: 任务在 worker 线程执行至 `future.complete(result)`；该调用同步触发 bindCancel 注册的 whenComplete 回调（同一 worker 线程）；此时底层 FutureTask 尚未 return、state==NEW。
- **触发时序**: T1：worker W 执行包装 Callable，`task.call()` 成功；T2：`future.complete(result)`→postComplete 同步弹出 whenComplete 栈→`f.isDone()`==false（任务还在跑）→`f.cancel(true)`；T3：W 被置中断标志；FutureTask 状态转 CANCELLED——包装层 `return result` 后 `set()` 因 state!=NEW 被跳过；T4：任务实际成功，但底层 future 终态为 cancelled；W 带中断标志回到 TPE runWorker。
- **现状**: 守卫 `!future.isDone()` 的本意是"future 已完成则不再取消"，但在「complete-inside-task」写法下**成功路径守卫恒真**——helper 无法区分 promise 的取消完成与成功完成（正确判据应为 `ex instanceof CancellationException`）。当前生产损害受限：TPE 的 `getTask()` 阻塞在 workQueue.take/poll 上会立即吞掉残留中断（InterruptedException 被 getTask catch 吸收），故 globalWorker 等裸 TPE 载体仅多一次空唤醒；底层 future 的 cancelled 假终态因 `f` 仅用于 bindCancel 而无人读见。
- **风险**: (1) 语义契约破坏：任何持有底层 future 的代码（指标、调试、未来重构把 `f` 暴露出去）看到成功任务被取消；(2) 中断残留对非 TPE 载体可泄漏到下一任务——`SequentialTaskExecutor.doTask` 循环（内部仅非阻塞 `queue.poll()`，无阻塞点吸收中断）中，A 任务经 submit 包装完成后注入的中断会直接命中同批 B 任务的第一个阻塞调用（当前 0 生产调用方，属未来组合面）；(3) promise 后续挂接的同步依赖回调在 W 上执行时 inherits 中断标志。
- **建议**: 双层修复：`FutureHelper.bindCancel` 守卫改为按完成原因判别（`ex instanceof CancellationException` 才 cancel——api-core 侧，建议随 C3-005 移交项一并处理）；ExecutorHelper 侧可先将 `future.complete` 移出包装 Callable 的任务体语义不可行（完成点必须在任务体内），替代方案是在包装 Callable 内 `complete` 后补 `Thread.interrupted()` 清除残留标志（消除对载体的依赖）。
- **失效面枚举**（F3）：`ExecutorHelper.submit(Callable)`（本案）；`ExecutorHelper.submit(Runnable,T)`（本案同构）；`ExecutorHelper.schedule(STPE,Callable,delay,unit)`（本案同构）；`DefaultScheduledExecutor/DefaultThreadPoolExecutor` 的 submit/schedule（委托上述，继承）；`FutureHelper.bindCancel` 其余调用方 `DefaultTaskExecutionQueue:238`（见 C3-018，独立缺陷）；`SyncThreadPoolExecutor.submit`（无 bindCancel，豁免）；`BindScheduledExecutor`（无 bindCancel，其取消缺失已由 C3-004 立案）。
- **信心水平**: 确定（whenComplete 同步执行于完成线程、FutureTask 状态机、TPE getTask 吸收中断三点均为 JDK 确定行为；单测可用「submit 后断言底层 future.isCancelled()==true」直接复现）
- **误报排除**: 不是"cancel(true) 无害"的原语误用争议——缺陷点是平台自己的 wrapper 写法（complete-inside-task）与 helper 守卫的组合必然误触发，非理论路径；也不是 C3-004（那是取消不传播，这是取消过度传播）；api-core 根因按既定口径仅对照，立案主体是 in-scope 的 ExecutorHelper 调用面。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2（高信心缺陷确认）**。根因协作方亲核：`FutureHelper.bindCancel`（api-core L313-318）守卫 `if (!future.isDone()) future.cancel(true)`。恒真性核实：常规时序下（bindCancel 于 L148 注册先于任务完成——任何耗时超过 submit→bindCancel 纳秒级窗口的任务）`future.complete(result)` 在任务体内同步触发 whenComplete，此刻底层 FutureTask 仍在执行本任务（state==NEW）→ 守卫恒真 → `f.cancel(true)` 注入 runner 中断 + FutureTask 终态 CANCELLED，`set()` 因 state!=NEW 被跳过——**成功路径被打断**的机制链每步可信（JDK 确定行为）。**精度注记**：“必然”成立于常规时序；零耗时任务在 L148 前整体完成的极窄竞态可豁免（此时 f 已 done，守卫为假），属对初判有利的收紧，不影响裁定。损害面评估亲核属实（TPE getTask 于下次阻塞 take/poll 吸收中断；f 为局部变量无人读见），P2 恰当。现状：**已知未修复**。

### [nop-kernel-concurrency-C3-017] ExecutorHelper.scheduleWithRandomDelay 执行中取消窗口：cancel 命中在飞轮次即永久失效，续排无 isDone 守卫（站点可能与 C1-005 重叠，需对账）

- **文件**： `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ExecutorHelper.java:210-238`（关键 222/233-236 行）
- **证据片段**:
  ```java
  Callable<Void> command = new Callable<>() {
      public Void call() {
          try {
              task.run();                  // ← 在飞窗口：整个任务体时长
              ref.set(executor.schedule(this, random, timeUnit)); // 无 isDone 检查即续排
          } catch (Throwable e) {
              future.completeExceptionally(e);   // 杀链但无 LOG
          }
          return null;
      }
  };
  Future<?> f = executor.schedule(command, initialDelay, timeUnit);
  ref.set(f);
  future.exceptionally(ex -> { ref.get().cancel(false); return null; });
  ```
- **严重程度**: P2
- **并发场景**: 调用方线程对返回 future 执行 cancel；timer 线程正在执行某一轮 `task.run()`（或恰在 run 结束与 `ref.set(下一轮)` 之间）。
- **触发时序**: T1：第 N 轮 command.call() 在 timer 线程执行 `task.run()`，`ref.get()`==当前轮 future f；T2：调用方 `future.cancel(false/true)`→exceptionally 回调同步执行→`ref.get().cancel(false)`→f 正在运行，cancel(false) 为 no-op（cancel(true) 也仅中断不停止）；T3：task.run() 正常结束→`ref.set(第 N+1 轮 future)`→**续排无任何 isDone 守卫**→第 N+1 轮照常触发；T4：链永久续存，取消被静默忽略。任务体越长，取消命中在飞窗口的概率越高（与 BindScheduledExecutor 91 行的 isDone 守卫形成同族对照）。
- **现状**: 对照组 `BindScheduledExecutor.scheduleWithFixedDelay` 在每轮重排后由 91 行 `if (future.isDone()) return null` 兜住完全相同的竞态——证明该守卫是本平台自建周期链的既定语义，本方法漏配。附带不一致：catch(Throwable) 分支无 LOG.error（同文件 submit/schedule 均有），失败仅存于被丢弃的 future。当前生产 0 调用方（全库 grep 证实），缺陷为潜伏态。
- **风险**: 未来使用方（退避重试轮询、抖动探测类场景）依赖 cancel 停链时产生不可撤销的持续副作用；且因无日志，停不下来的链与失败链都不可观测。
- **建议**: command.call() 开头补 `if (future.isDone()) return null;`（对齐 BindScheduledExecutor 91 行）；catch(Throwable) 分支补 LOG.error；cancel 时同时 cancel `ref` 当前值与新 set 值（或统一改用 future 状态判定重排资格）。
- **失效面枚举**（F3）：`ExecutorHelper.scheduleWithRandomDelay`（本案）；`IScheduledExecutor.scheduleWithRandomDelay` default（委托前者，继承）；`BindScheduledExecutor.scheduleWithFixedDelay`（有 isDone 守卫，同族正确样本，其 C3-002 缺陷不属取消语义）；裸 JDK `scheduleWithFixedDelay`（JDK 原生取消语义正确，豁免）。
- **信心水平**: 确定（竞态窗口=整个任务体时长，时序可单测复现：启动→首轮在飞中 cancel→断言第二轮仍执行即红）
- **误报排除**: 该站点已由 C1-005 立案（C1 维度，内容不可读）——本条按「同族新缺陷（取消语义/完成通知），非同一缺陷」立案，已在标题显式标注重叠嫌疑，交对账轮去重；若 C1-005 已覆盖本缺陷则以先立案者为准，本条降为 F3 登记。不是 CompletableFuture.cancel "不承诺停止底层工作"的原语误报：同平台同族方法已实现取消传播，构成事实契约。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。L222 续排无 isDone 守卫、L233-236 exceptionally 回调对在飞轮次 cancel(false) 为 no-op、L223-225 catch(Throwable) 无 LOG——三点亲核成立；与 BindScheduledExecutor L91 守卫的同族对照属实；生产 0 调用方亲核证实（全库 grep 无调用）。与 C1-005 的重叠对账标注维持，留报告合并轮处置（本复核未读 C1 文件，不预判归属）。现状：**已知未修复**（潜伏态）。

### [nop-kernel-concurrency-C3-018] DefaultTaskExecutionQueue.queueTask 取消绑定参数反接：声明的「state.promise 取消→取消任务 future」未接线；被取消任务完成时计入 completedTasks

- **文件**： `nop-kernel/nop-core/src/main/java/io/nop/core/execution/DefaultTaskExecutionQueue.java:236-240`（B 面）；`State.cancel:110-113`
- **证据片段**:
  ```java
  // 注释声称的方向："确保 state.promise 取消时可取消 future"
  // 实际接线（bindCancel(promise, future) 语义：promise 完成时取消 future）：
  FutureHelper.bindCancel(future, state.getPromise());
  // → task future 完成时 → cancel state.promise（方向反了）
  // State.cancel：cancel 时只 cancel promise，无人 cancel task future
  public void cancel(String reason) {
      super.cancel(reason);
      promise.cancel(false);
  }
  ```
- **严重程度**: P2
- **并发场景**: 调用方对 `ITaskExecutionState.getPromise()` 或 `State.cancel` 发起取消；任务已在 globalWorker（raw execute，无 detach 卫生）上运行 `task.executeAsync(state)`。
- **触发时序**: T1：任务运行中，调用方 `state.cancel("...")`→promise.cancel(false)→Cancellable 回调执行；T2：底层任务 future 无任何挂接——继续运行至自然终态（除非任务体主动轮询 state.isCancelled()）；T3：任务 future 完成时 whenComplete(#1) 尝试 `state.getPromise().complete(res)`——promise 已取消，complete 返回 false（被忽略）；T4：finally 中 `completedTasks.incrementAndGet()`——**被取消的任务最终被计入 completed 而非 cancelled**（指标失真）；bindCancel 的回调因 promise 已 done 而恒为 no-op——即该行代码在登记后的全部时序下要么 no-op 要么方向错误。
- **现状**: `bindCancel(future, state.getPromise())` 若意图为「caller 取消 promise→取消任务 future」，正确写法是 `bindCancel(state.getPromise(), future)`（promise=caller 侧，future=任务侧）。注释文字与正确写法一致，代码与注释相反——高置信参数顺序笔误。任务启动前取消有 192-197 行 `state.isCancelled()` 守卫兜底（正确）；唯一失效面是**启动后取消不传播**。该队列当前生产 0 调用方（全库 grep 仅接口定义与自身），缺陷潜伏。
- **风险**: 未来使用方（任务管理/导入导出类 UI）取消长任务时任务照常跑完（资源浪费+副作用完整发生），且指标显示"成功完成"；取消语义形同虚设会反向诱导使用方绕开该设施自建取消通道。
- **建议**: 改为 `bindCancel(state.getPromise(), future)` 对齐注释语义；whenComplete 的 finally 按 `state.isCancelled()` 分流计数（cancelled++ 而非 completed++）；取消后任务仍自然完成的形态在 javadoc 显式声明（at-least-once 完成+结果丢弃）。
- **失效面枚举**（F3）：`DefaultTaskExecutionQueue.queueTask` 的 bindCancel 行（本案）；`DefaultTaskExecutionQueue.queueTask` 启动前取消守卫（正确，豁免）；`State.cancel`→promise.cancel（自身正确，缺传播对象是本案）；`ITaskExecutionQueue.replaceTask` 的 oldState.cancel（依赖同一失效传播路径，继承本案）；全库其余 bindCancel 调用方（ExecutorHelper 3 处→C3-016）。
- **信心水平**: 确定（参数顺序与注释矛盾为静态可证事实；计数失真为确定行为；"注释表达意图"有同文件 233 行注释与 API 命名双重佐证）
- **误报排除**: 不是"CompletableFuture.cancel 不传播底层"的通用问题——这里挂接代码本身存在且方向接反，属自建链漏接/错接；计数失真非指标口径选择问题（cancelledTasks 计数器存在却永不因取消后完成而递增）。B 面文件：基线 B-face enumeration 含 nop-core 全局设施，DefaultTaskExecutionQueue 为平台任务执行队列设施（`ITaskExecutionQueue` 公共接口），在审计对象内。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。参数反接亲核成立：L237 注释“确保 state.promise 取消时可取消 future”与 L238 `bindCancel(future, state.getPromise())`（语义=第一参完成时取消第二参）方向相反；given L221 先注册的 whenComplete 先完成 promise，L238 回调恒为 no-op；`State.cancel`（L106-109）仅 cancel promise、无人取消任务 future；被取消任务完成时 finally 计入 completedTasks（L230）亲核属实。行号修正：启动前取消守卫实际在 **L209-214**（初判 192-197 为漂移）。零生产调用方亲核证实（全库仅接口定义与实现）。现状：**已知未修复**（潜伏态）。

### [nop-kernel-concurrency-C3-019] LocalCache 同步 LoadingCache 的 refreshAfterWrite/refresh(key) 异步刷新链落在 Caffeine 默认 commonPool 且异常仅达 Caffeine JUL logger（修正 C3-010 的 F3 豁免）

- **文件**： `nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/LocalCache.java:104-106, 266-272`；`CacheConfig.java:34`
- **证据片段**:
  ```java
  if (config.getRefreshAfterWrite() != null) {
      builder.refreshAfterWrite(config.getRefreshAfterWrite());   // sync LoadingCache 同样接线
  }
  // buildCache 全程未调用 builder.executor(...) → Caffeine 默认 ForkJoinPool.commonPool
  public void refresh(K key) {
      if (loadingCache != null) loadingCache.refresh(key);        // 亦为异步刷新
  ```
- **严重程度**: P3
- **并发场景**: sync `LoadingCache`（`builder.build(loader)` 分支，B 面模型缓存的形态）配置了 refreshAfterWrite；访问命中陈旧条目时 Caffeine 将 reload 提交到 cache executor（未配置→commonPool）。
- **触发时序**: T1：某 sync 缓存启用 refreshAfterWrite（或调用方调用 `LocalCache.refresh(key)`）；T2：reload（阻塞型加载，本平台典型含磁盘 IO/XML 解析）进入 commonPool；T3：批量陈旧→commonPool 被阻塞 IO 占满→平台共用 commonPool 的设施连带劣化（与 C3-010 async 路径同构，但触发条件是 refreshAfterWrite 而非 async=true）；T4：reload 抛异常→仅 Caffeine 内部 JUL logger（caffeine-3.2.4 `LocalLoadingCache` 字符串常量 `"Exception thrown during refresh"` 已反汇编核实）→平台 SLF4J/指标无通道，旧值静默保留。
- **现状**: R1 C3-010 的 F3 标注「LocalCache 同步分支（无异步载体，豁免）」——该豁免在 refreshAfterWrite>0 或调用 `refresh(key)` 时不成立（sync LoadingCache 的自动刷新同样是异步载体）。当前生产 `setRefreshAfterWrite` 0 调用、`LocalCache.refresh` 0 调用（全库 grep 证实），缺陷完全潜伏，判 P3。`ICacheLoader.reload` javadoc 已自述"logged and then swallowed"——swallow 是文档化行为，缺陷在日志通道（JUL≠平台 LOG）与执行器归属（不可配置）两点。
- **风险**: 与 C3-010 同构的 commonPool 连带劣化 + 刷新失败完全不可观测（错误日志落在 JUL，运维聚合与告警全部漏接）；陈旧值无限期服役。
- **建议**: 与 C3-010 合并修复：`CacheConfig` 增加 executor 配置项，`LocalCache.buildCache` 统一 `builder.executor(...)`（覆盖 async 与 refreshAfterWrite 两条异步链）；刷新失败告警通过 `CacheLoader.reload` 包装（try/catch→平台 LOG→rethrow）接入。
- **失效面枚举**（F3）：`LocalCache.buildCache` refreshAfterWrite 接线（本案）；`LocalCache.refresh(K)`（本案同链）；`LocalCache` async=true 路径（C3-010 已立案，归并修复）；`CaffeineCacheLoader.reload/asyncReload`（透传，责任在装配层）；B 面 `ResourceLoadingCache.createCache`（未配置 refreshAfterWrite，当前豁免——但若未来配置即暴露，修复合并生效）。
- **信心水平**: 确定（装配事实+异常落点反汇编核实）；很可能（危害链需配置启用）
- **误报排除**: 不是重复 C3-010——触发面不同（refreshAfterWrite/refresh(key) vs async=true），且修正了 C3-010 F3 中"同步分支豁免"的错误判定（这是复核性的 F3 修正，非重复立案）；"Caffeine 默认行为"不是误报豁免：装配层（LocalCache/CacheConfig）未声明也未配置执行模型，责任在平台设施装配。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P3**。`buildCache` L104-106 对 sync LoadingCache 同样接线 refreshAfterWrite 亲核；全程无 `builder.executor(...)` 亲核；异常落点独立实证：本地 ~/.m2 caffeine-3.2.4.jar 的 `LocalLoadingCache.class` 含字符串常量“Exception thrown during refresh”，R2 反汇编核实结论成立；`setRefreshAfterWrite` 零外部调用、`LocalCache.refresh(K)` 零外部调用亲核证实，潜伏态 P3 恰当。对 C3-010 F3“同步分支豁免”的修正确认成立。现状：**已知未修复**（潜伏态）。

### [nop-kernel-concurrency-C3-020] RateLimitExecutorImpl.debounce 非原子（get-cancel-schedule-put）+ delay=0 的 holder 竞态：并发去重失效与映射条目滞留

- **文件**： `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/RateLimitExecutorImpl.java:44-60`
- **证据片段**:
  ```java
  public void debounce(final Object key, long delay, Runnable task) {
      Future<?> old = promiseMap.get(key);      // ← get
      if (old != null) old.cancel(true);        // ← cancel
      final Future<?>[] holder = new Future<?>[1];
      Future<?> future = executorService.schedule(() -> {
          try { task.run(); }
          finally { promiseMap.remove(key, holder[0]); }  // ← holder 可能仍为 null
      }, delay, TimeUnit.MILLISECONDS);
      holder[0] = future;                        // ← schedule 返回后才赋值
      promiseMap.put(key, future);               // ← put（get-cancel-put 全程无原子性）
  }
  ```
- **严重程度**: P3
- **并发场景**: 多个线程（或 watch 服务多 listener 分发线程）对同一 key 并发调用 debounce；`replace()` 以 delay=0 调用 debounce。
- **触发时序**: 竞态 A（去重失效）：T1/T2 两线程同时 get→old==null（或读到同一 done 条目）→双双 schedule→**两个任务都执行**——debounce"只保留最后一次"的契约破坏。竞态 B（条目滞留）：`replace()`（delay=0）时任务在 timer 线程立即执行，早于调用线程 `holder[0] = future`→finally `remove(key, null)`→CHM 对 null value 的条件 remove 返回 false→done 条目滞留至下次 debounce 覆盖（内存滞留有限，且 cancel(true) 对 done future 为 no-op，功能影响轻）。竞态 C（跨任务误删）：A 线程的 finally 用自己的 holder 删掉 B 线程刚 put 的新条目→B 的取消句柄脱管。
- **现状**: 全库 `IRateLimitExecutor` 消费方 3 处（`ZipFileWatcher`、`CliWatchZipCommand`、`nop-core FileWatcher`）——均为开发/调试工具链（watch 服务、zip 重建），单触发线程场景下竞态不可达，判 P3。`throttle` 用 `compute` 原子实现（正确，同族对照）；`debounce` 未对齐。
- **风险**: 工具链外若被业务采纳（防抖语义天然吸引高频写场景），并发去重失效=重复执行副作用（zip 重建场景是重复 IO，业务场景可能是重复提交）；条目滞留随 key 空间增长。
- **建议**: debounce 改为与 throttle 同构的 `promiseMap.compute(key, ...)` 原子段：compute 内 cancel 旧任务、schedule 新任务（schedule 抛异常时不落条目）；执行体内 `remove(key, holder快照)` 改为 `remove(key, ownFuture)` 的局部 final 变量（消除 holder 数组时序窗）。
- **失效面枚举**（F3）：`RateLimitExecutorImpl.debounce`（本案）；`RateLimitExecutorImpl.replace`（委托 debounce(0)，继承竞态 B）；`RateLimitExecutorImpl.throttle`（compute 原子，正确样本；其 completeAfterTask Exception 宽度归并 R1 移交项）；载体维度：基于 `DefaultScheduledExecutor` 时 cancel(true) 经 ExecutorHelper 的 bindCancel 正确传播到 STPE（豁免）；基于 BindScheduledExecutor 时旧任务取消不传播——C3-004 已立案根因，此处仅登记衍生暴露面。
- **信心水平**: 确定（三竞态均为静态可证；单测：两线程并发 debounce 同 key 断言执行次数即红）
- **误报排除**: 不是"防抖允许偶尔重复"的语义弹性争议——类 javadoc/字段名承诺 last-wins；当前 3 个消费方全为工具链故降 P3 而非缓报。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P3，附事实修正**。debounce 非原子 get-cancel-schedule-put 亲核成立（实际行号 **L50-67**，初判 44-60 为漂移），竞态 A（去重失效）/竞态 C（跨任务误删）静态可证。**事实修正**（竞态 B 机制）：`promiseMap` 为 ConcurrentHashMap，`remove(key, null)` 抛 NullPointerException 而非返回 false——delay=0 时任务先于 `holder[0]` 赋值执行时，finally 中 NPE 逃逸任务体（经 ExecutorHelper 包装被 catch(Throwable) 记录、future 异常完成），**条目滞留后果不变**且多一条 ERROR 日志，危害方向与初判一致，P3 不变。3 个消费方（ZipFileWatcher/FileWatcher/CliWatchZipCommand，均为工具链）亲核证实；throttle 用 compute 原子实现（L29-47）对照属实。现状：**已知未修复**。

---

## 维度集外线索（本轮新登记）

- **[CL-021] 周期任务载体碎片化：12+ 处生产调用绕过 IScheduledExecutor 直接使用 raw JDK 调度器**（C8/架构形态）：nop-stream 8 处（`Executors.newSingleThreadScheduledExecutor`/`newScheduledThreadPool`）、nop-ai-agent 3 处（DBMessageService poller、ScheduledRecoveryManager/TeamTaskSchedulerDaemon 注入面）、nop-integration-feishu 1 处、nop-netty RpcMessageHandler（Netty EventExecutor）——C3-001 的设施级修复**不会覆盖**这批站点，修复范围声明必须区分「设施内 30 处表」与「绕行载体」两批；nop-ai-agent 的 `SessionLockRenewal`（会话续租，安全语义最重）恰在绕行批内。
- **[CL-022] 长驻 worker 载体的 detach 卫生缺口具名清单**（C3-013 修复联动面，C6/生命周期交叉形态）：`DefaultTaskExecutionQueue.queueTask`（raw `executor.execute`，任务 lambda 内执行 `task.executeAsync`，无 finally detach——C3-013 预言的「raw execute + getOrCreateContext」组合的首个具名 B 面设施）、`BlockingSourceConsumeTask.run`（常驻消费线程）、`SequentialTaskExecutor.doTask`（单次调度多任务循环）、globalTimer 裸 JDK 路径 9 处周期任务体。修复 C3-013 时按此清单逐点补卫生或改用 submit 包装器。

---

## 移交其他维度的线索（本轮新增）

1. **[移交 C6]** `ResourceLoadingCache` 同步重载的调用方阻塞量化（§4）：失效后首个请求独占 entry monitor 全额承担重载；同路径并发请求串行等待；无后台预热。属资源生命周期/性能维度决策（是否引入异步预热），非 C3 立案面。
2. **[移交 C1/C10]** `FutureHelper.bindCancel` 守卫 `!future.isDone()` 无法区分取消完成与成功完成（C3-016 的 api-core 根因半边，与 R1 已移交的 `completeAfterTask` Exception 宽度同文件）。

---

## 本轮统计与自评

| 严重程度 | 数量 | 条目 |
|---|---|---|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 4 | C3-015、C3-016、C3-017、C3-018 |
| P3 | 2 | C3-019、C3-020 |
| 合计 | 6 | CL 线索 2 条（CL-021、CL-022） |

**累计（R1+R2）**：P1×1（C3-001）、P2×16、P3×3。

**五条指令执行情况**：①绝缘状态表 30 站点全覆盖（§1，C3-015）；②5 类 executor 全公开方法逐一判定（§2，C3-016/C3-017）；③Cancellable 族正确不立案、取消传播与在飞迭代全扫（§3，C3-016/C3-017/C3-018）；④B 面确认无异步加载路径+同步刷新量化+refreshAfterWrite 异常落点闭环（§4，C3-019）；⑤传播矩阵表产出（§5，CL-022）。

**深挖继续规则判定**：本轮新增全部为 P2/P3，无 P0/P1。终止信号核查：(a) 指令②的 5 类 executor 公开方法已全部逐一判定完毕，无剩余未核方法；(b) 指令③的 Cancellable 实现族（Cancellable/CancellableRpcClient/Debezium/ReplayableCdcSourceFunction 使用面）核对完毕，无新增取消语义缺陷；(c) 指令④的 B 面异步面已闭环（无异步加载路径为结构性事实，refreshAfterWrite 链 0 生产用户）；(d) 指令⑤矩阵覆盖 A 面全部执行载体，无新立案面；(e) 剩余暴露面均有归属：nop-stream 自建调度器（越出 A/B 面，CL-021 登记）、api-core 根因（既定越界口径，移交项登记）。**判定：C3 维度 A/B 面深挖收敛，终止。** 第 1 轮 P1（C3-001）修复时应以 §1 绝缘状态表为直接输入，并按 C3-015 的语义澄清选择续链或 kill+alarm 契约。

**盲区自评**：
- nop-stream 8 处周期站点的任务体未逐点展开（越出 A/B 审计单元，CL-021 已登记载体碎片化事实，绝缘判定留给该单元自己的维度）。
- Netty `EventExecutor.scheduleWithFixedDelay` 的异常抑制语义未深入（非平台设施，表中按"非平台设施"标注）。
- C3-017 与 C1-005 的重叠对账需在报告合并轮完成（C1 轮文件不可读）。
