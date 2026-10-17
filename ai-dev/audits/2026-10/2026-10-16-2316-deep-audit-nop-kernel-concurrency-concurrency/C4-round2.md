# C4 线程池治理——第 2 轮深挖报告（盲审）

## 审计单元

- **维度**: C4——线程池与执行器治理（第 2 轮深挖：C4-001 失效面扩展、池参数纪律矩阵、关闭语义全扫、裸线程创建点、虚拟线程治理面）
- **A 面（审计对象本体）**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/{concurrent,cache,pool}`（executor 子包 20 files + thread 子包 6 files + cache/pool 复核）
- **B 面**: `nop-core`/`nop-xlang` 全局模型缓存与注册表单例（第 1 轮实测 46 files 零线程载体——本轮按 live code 抽样复核确认，见「B 面复核」节）
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **判级口径**: 平台设施被全平台并发调用——全局池（globalWorker/globalTimer）为全平台共享资源，占用/饿死面按极低门槛判级；并发类发现必须写出具体触发时序或过载数字。

## 本轮输入

1. 第 1 轮报告 `C4-thread-pools.md`（C4-001~010：P1×1 + P2×5 + P3×4；CL-007 TaskQueue 弹性扩容纠偏）。
2. 基线 `_tmp/cra-baseline-nop-kernel-concurrency.txt`（A C4 节圈定范围用）。
3. live code（本轮全部结论基于当前分支逐文件核对）。

## 盲审纪律声明

本轮为盲审轮次：**未读取** `ai-dev/audits/`（唯一例外：本维度第 1 轮文件 `C4-thread-pools.md`）、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件，未读取同目录其他维度文件。过程中的一次全仓文本 grep 曾使命中行意外回显了同目录其他维度文件的片段，已按纪律弃用该内容、不作为本轮任何结论的依据；本报告涉及的事实均由本轮独立读取 live code 重新验证。全部结论以当前 live code 为准。

## 本轮目标（逐条执行记录）

- **①** C4-001 失效面扩展：globalTimer 消费方分类终表 + 4 线程被 N 个直连长任务占满的概率模型 + 死配置副作用改写 F3 全表。
- **②** A 面全部线程池创建点参数纪律矩阵（修复 plan 直接输入）。
- **③** 关闭语义全扫：A 面 ExecutorService 的创建/关闭配对表 + shutdown 后提交的调用方感知面。
- **④** `new Thread` 裸创建站点逐一判定。
- **⑤** 虚拟线程治理面：`Jdk21CommonsCoreInitializer` 开关语义、virtual vs platform 参数迁移、queue 容量语义。

**反虚构预注遵守**：前轮 10 条不重复立案；`BasicThreadPoolExecutor` 不存在（实为 `StandardThreadPoolExecutor`）；`SyncThreadPoolExecutor` 无 synchronized、destroy no-op 已知；TaskQueue 弹性扩容正确（CL-007 纠偏，本轮复核 `TaskQueue.java:68-84` 维持原判，无翻案）。

---

## ① globalTimer 消费方分类终表（C4-001 失效面扩展）

全仓 `globalTimer()` 生产代码命中 28 个文件（含 `GlobalExecutors.java` 自身），逐站点核对调用形态后分类如下。**相对第 1 轮的三处修正/补充以 ⚠ 标注。**

### A 类：直连占线程（长任务能力，暴露面）

| 站点 | 任务 | 周期性 | 阻塞源 |
|---|---|---|---|
| `LocalResourceLockManager.java:104-105` | `checkTimeout` DB 锁过期扫描 | scheduleWithFixedDelay | DB |
| `SysDaoNamingService.java:77` | `cleanup` DB 删除 | scheduleWithFixedDelay | DB |
| `JdbcConfigSource.java:54` | `refreshConfig` DB 查询 | scheduleWithFixedDelay | DB |
| `AbstractFileConfigSource.java:45` | `refreshConfig` 文件读取 | scheduleWithFixedDelay | 文件系统/NFS |
| ⚠ `nop-ai-agent/.../ScheduledRecoveryManager.java:361` | `scanOnceSafe`→`scanOnce` DB 孤儿会话扫描（多查询） | scheduleWithFixedDelay | DB（javadoc 明示生产注入 `GlobalExecutors.globalTimer()`，`:199`；当前 Java 层仅测试构造，生产接线经 IoC 配置字符串，grep 不可见） |
| ⚠ `JsonRpcWebSocketHandler.java:82` | `sendKeepAlive` WebSocket ping | scheduleWithFixedDelay **每连接一条** | 网络写（慢消费者/全缓冲时可阻塞）——**连接数线性扇出**：N 连接 = N 条周期任务入同一 DelayedWorkQueue |
| ⚠ `CliRunCommand.java:90` | `runTasks`→`runFile` 加载并执行 `.xrun` XPL 任务文件 | scheduleWithFixedDelay | **任意自动化任务（模型加载+XPL 求值），时长无上界** |
| ⚠ `CliWatchCommand.java:90` / `FileWatcherFactory.java:33` → `FileWatcher.java:70` | `debounce("process",...)`→`processChangeEvent` 文件处理 | 事件驱动 | 文件处理回调直连 timer 线程 |
| ⚠ `CliWatchZipCommand.java:100` / `ZipFileWatcher.java:38` | `RateLimitExecutorImpl(globalTimer)` 的 `throttle/debounce` 任务体（zip 打包 IO） | 事件驱动 | `RateLimitExecutorImpl.java:36-44,56-59` 将任务体直接 schedule 到被包装 executor——**无 executeOn 转发**，zip IO 直连 timer 线程 |

**⚠ 第 1 轮纠偏**：第 1 轮 F3 将 `CliRunCommand/CliWatchCommand/CliWatchZipCommand/FileWatcherFactory/ZipFileWatcher` 归入「以上 executeOn/轻量，已自救」——live code 核对不成立：这些站点**均无 `executeOn`**，是直连调度。CLI 场景单进程独享影响有限，但分类事实必须纠正。

### B 类：直连短任务（一次性延迟清理，低危）

| 站点 | 任务 | 形态 |
|---|---|---|
| `DefaultBizEntityExporter.java:72` | 临时文件删除（keepTempFileMinutes 后） | 一次性 schedule |
| `ExcelReportHelper.java:124` | 同上 | 一次性 |
| `ReportDemoBizModel.java:155` | 同上（demo） | 一次性 |
| ⚠ `nop-spring-report-demo/ReportController.java:133` | 同上（demo，第 1 轮漏记） | 一次性 |
| `DownloadHelper.java:39` | 同上 | 一次性 |
| `ShellRunner.java:226` | 命令超时 cleanup（进程销毁） | 一次性 |

文件删除为毫秒级，占线可忽略；但一次性 schedule 不可取消、任务对象常驻 DelayedWorkQueue 至触发（分钟级驻留，内存影响可忽略）。

### C 类：触发转发（executeOn 自救，合格防线）

| 站点 | 形态 |
|---|---|
| `AbstractBatchScanner.java:135`、`RetryScannerImpl.java:166`、`RetryEngineImpl.java:623` | `globalTimer().executeOn(globalWorker())` |
| `AbstractPollingLeaderElector.java:19`、`AutoRegistration.java:205` | 同上 |
| `SysDaoMessageService.java:213-217` | timer 字段直连 + `timer.executeOn(executor).scheduleWithFixedDelay`（半自救：绑定经 executeOn，字段形态与其他直连站点同） |

### D 类：轻量直连（内存检查/异步触发，可接受）

| 站点 | 形态 |
|---|---|
| `RpcPollTaskManager.java:172` | fallback 直连；`pollOnce`（`:175-206`）纯内存 deadline/cancel 检查 + 异步 RPC，实测轻量（维持第 1 轮判定） |
| `ClusterRpcServiceInvoker.java:68` / `ClusterRpcProxyFactoryBean.java:43` | timer 传递给 `ClusterRpcClient` 做超时触发，轻量 |
| `TaskFlowManagerImpl.java:79` | fallback 直连；下游 `TimeoutTaskStepWrapper`/`DelayTaskStep`/`TaskStepHelper:269` 仅做延时/超时触发，任务体在别处执行 |

### 概率模型：4 线程被 N 个直连长任务占满

**容量**：C=4（STPE core 默认；`maxPoolSize` 配置死值，C4-001）。**服务器部署（非 CLI）下的直连长任务面**：A 类前 5 行 = 4~5 条周期性 DB/IO 任务，加每连接 keepalive 扇出 N 条。

- **触发条件**：STPE 语义下（1）任务体在 timer 线程同步执行；（2）`scheduleWithFixedDelay` 的任务运行超时不重入——同一任务不叠副本，但**全部其他到期任务排队**。全体停摆条件 = 同时阻塞数 ≥ 4。
- **相关性放大**：A 类前 3+1 个站点（锁扫描/命名清理/jdbc 刷新/recovery 扫描）共享同一数据库。设 DB 故障窗口内单任务阻塞概率 p，独立假设 P(≥4)=O(p⁴)，但同库相关性使 P(3 个 DB 任务同时阻塞)≈P(DB stall)，全停概率 ≈ P(DB stall ∧ 任一第 4 任务受阻)。第 4 任务来源充足：文件刷新 hang（NFS）、recovery 扫描慢、任一 WS 慢消费者 send 阻塞。即**全停门槛远低于独立事件假设**。
- **结构性超订**：A 类 5 条直连周期任务 > 4 线程。无需"故障"事件：当多条扫描的执行时长接近/超过各自间隔（大锁表 + 大 naming 清理 + 慢配置刷新同时发生），即可进入持续排队态（数字为量级示意，非实测）。
- **停摆后果的量化**：T=120s DB stall 期间，N 个 keepalive 任务各积压 1 条到期执行（fixedDelay 不叠副本），停摆解除后 N 条串行补发（轻量、快）；真实损失是 120s keepalive 空窗（中间件可判定连接空闲剔除，keepalive 目的失效）+ 全平台定时任务集体顺延 C4-001 时序。

### 死配置副作用改写 F3（修复面：除 C4-001/C4-002/C4-008 已立案项外的新增丢弃矩阵）

`ThreadPoolConfig`/`BaseThreadPoolConfig` 全部 10 个字段 × 3 条机件（init 构造 / maxPoolSize<0 分支 / 热更 updateThreadPool）的「读/丢」核对：

| 字段 | `newScheduledExecutor` | `newThreadPoolExecutor` 正常分支 | `newThreadPoolExecutor` maxPoolSize<0 分支 | `updateThreadPool` 热更 |
|---|---|---|---|---|
| name | 读 | 读 | 读 | n/a |
| corePoolSize | 读 | 读 | **丢**（硬编码 0） | 读（:118-120） |
| maxPoolSize | **改写为 0（C4-001）** | 读 | 语义开关（<0） | 读（:107，C4-006 方向反置） |
| keepAliveSeconds | **丢** | 读 | **丢**（硬编码 60s） | 读（:122-125） |
| allowCoreThreadTimeOut | **丢（全仓零消费者，新立案 C4-011）** | **丢（同）** | **丢** | **丢** |
| queueCapacity | **丢** | 读（TaskQueue） | **丢**（SynchronousQueue 硬编码） | **丢** |
| threadPriority | **丢（全仓零消费者，新立案 C4-011）** | **丢（同）** | **丢** | **丢** |
| threadDaemon | 读 | 读 | 读 | **丢** |
| enableMetrics | 读（:201-208） | 读 | 读 | n/a |
| waitForTasksToCompleteOnShutdown / awaitTerminationSeconds | **丢（C4-002 已立案）** | **丢** | **丢** | **丢** |

丢弃不限于 C4-001 的单点：**两条工厂路径 + 热更路径合计 5 个字段存在「@ConfigBean 可配但实现零读取」**（新立案 C4-011）。

---

## ② 池参数纪律矩阵（A 面全部创建点，修复 plan 直接输入）

实测 live code 参数。判定标准：core≤max？queue 有界？reject 路径调用方可感知？daemon/命名/keepAlive 显式？

| # | 创建点 | 实现 | core | max | queue | reject | keepAlive | daemon | 命名 | 判定 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `GlobalExecutors.globalTimer:38-49` | STPE（经 DefaultScheduledExecutor） | 4（默认） | CFG(5)→**改写 0** | DelayedWorkQueue **无界** | STPE 默认 Abort | n/a | true | nop-global-timer-N | **C4-001** |
| 2 | `GlobalExecutors.globalWorker:85-96` | StandardThreadPoolExecutor+TaskQueue | 4 | CFG(30) | TaskQueue(10000) 弹性 | RejectHandler→force(0)→REE(queue-full) | 60s | true | nop-global-worker-N | 弹性正确（CL-007）；背压滞后属 C8 |
| 3 | `GlobalExecutors.cachedThreadPool:71-83` | plain TPE | 0 | 2³¹-1（-1 语义） | SynchronousQueue | AbortPolicy（仅线程创建失败时触发） | 60s | true | nop-cached-thread-pool-N | **C4-004**（无上界、无配额） |
| 4 | `GlobalExecutors.syncExecutor:98-100` | SyncThreadPoolExecutor（调用线程直通） | n/a | n/a | n/a | n/a | n/a | n/a | 无 | **C4-007**（stats/getConfig null、destroy no-op）；**注**：`NOP_SYNC_EXECUTOR` 常量存在但**从未注册进 g_executors**，`getExecutor("nop-sync-executor")` 走 :51-62 无此 fallback → 返回 null（按名解析盲区） |
| 5 | `DefaultThreadPoolExecutor.newExecutor(name,poolSize,queueSize[,daemon]):29-43` | StandardThreadPoolExecutor+TaskQueue | =poolSize | =poolSize | queueSize **无校验**（MAX_VALUE 实证） | RejectHandler→force(0) | 60s | **默认 false（3 参重载）** | 传入 | **C4-003** |
| 6 | `DefaultThreadPoolExecutor.newExecutor(ThreadPoolConfig):45-50` | 同上 | config | config | config | 同上 | config | config | config | **core>max 无守卫**：外部 ThreadPoolConfig bean（GrpcServer:82 / DefaultTaskExecutionQueue:145 注入路径）若配 core>max，`newThreadPoolExecutor:87` 直传构造器 → 裸 `IllegalArgumentException("maximumPoolSize <= corePoolSize")` 于 init 爆炸（无参数名上下文）——C4-003 的 F3 扩展（ThreadPoolConfig 重载路径同样零校验）；`refreshConfig:58-60` 有 core>max 收缩守卫但 **init 路径没有** |
| 7 | `ExecutorHelper.newSingleThreadExecutor:49-54` | plain TPE | 1 | 1 | LBQ(queueSize)（0→SQ） | **JDK 默认 Abort** | 0 | 参数 | 传入 | 调用方 NioFileWatchService:58（queue=1，单长任务 `checkChange` 自循环派发，Abort 实际不可达）合格 |
| 8 | `DefaultScheduledExecutor.newSingleThreadTimer:37-47` | STPE | 1 | 1（改写 0） | 无界 | Abort | n/a | **false（未设 daemon）** | 传入 | **C4-010**（非 daemon） |
| 9 | `VirtualThreadTaskExecutor`（java21） | newVirtualThreadPerTaskExecutor | n/a | **无上界（每任务一虚线程）** | 无队列 | shutdown 后 REE | n/a | n/a（虚线程恒 daemon） | nop-virtual-thread-N | **C4-012**（config 全为虚构默认值） |
| 10 | `GrpcServer.java:82` | newExecutor(ThreadPoolConfig) | config | config | config(10000) | RejectHandler | 60 | config | grpc 配置名 | ownExecutor 守卫 + doStop destroy（:149-162）**合格** |
| 11 | `JdkHttpClient.java:111` | newExecutor 3 参 | config.threadPoolSize | 同 | config.threadQueueSize | RejectHandler | 60 | **false** | config.threadName | stop():155-157 destroy **合格**（无等待，C4-002 族） |
| 12 | `SimpleRpcServer.java:132` | ("simple-rpc-server-"+name, threadPoolSize, **10**) | 配置 | 同 | **硬编码 10** | RejectHandler | 60 | false | 传入 | ownExecutor+stop destroy（:169-178）**合格**；queue=10 硬编码对高并发 RPC 偏小，属配置面 |
| 13 | `SimpleRpcClientFactory.java:126` | ("simple-rpc-client-executor-"+clientName, **5,10**) | 5 | 5 | 10 | RejectHandler | 60 | false | 传入 | destroy（:136-146，含 timer）**合格**；每 clientName 一池一线程累计面见 CL-024 |
| 14 | `SingleThreadConfigExecutor.java:19` | ("nop-config-executor", 1, 100, **true**) | 1 | 1 | 100 | RejectHandler | 60 | true | 传入 | stop destroy（:23-25）**合格** |
| 15 | `AbstractBatchProcessService.java:54` | (name, poolSize, **1**, **true**) | poolSize | poolSize | 1 | RejectHandler | 60 | true | 传入 | destroy（:72-78）**合格** |
| 16 | `DefaultTaskExecutionQueue.java:145` | newExecutor(ThreadPoolConfig) | config | config | config | RejectHandler | 60 | config | config | ownExecutor 守卫 doStop（:152-158）**合格** |
| 17 | `StreamControlRpcProxyFactory.java:62` | newSingleThreadTimer("stream-rpc-client-"+topic) | 1 | 1→0 | 无界 | Abort | n/a | **false** | 传入 | doStop timer.destroy（:110-116）**合格**；非 daemon 见 C4-010 |
| 18 | `ExportDbTool.java:150` / `ImportDbTool.java:232` | (name, threadCount, **Integer.MAX_VALUE**) | threadCount | 同 | 2³¹-1 | RejectHandler（队满不可达） | 60 | false | export-db/import-db | **C4-003** 无界实证；finally destroy（ExportDbTool:187 / ImportDbTool:261）**合格** |
| 19 | `JavaScriptService.java:40` | ("javascript-engine", **-1, -1**) | 0（硬编码） | MAX（硬编码） | SQ | Abort | 60（硬编码） | false | javascript-engine | **C4-003** 负值静默 cached 语义；doStop destroy（:134）**合格** |

**矩阵级结论**（供修复 plan 直接引用）：
1. **reject 全链路可感知**：StandardThreadPoolExecutor 族全部经 `RejectHandler`（裸 REE）→ `execute:156-163` 转 `force(0)` → 最终 `REE("nop.err.thread-pool-executor.queue-full")`，调用方同步感知——**无静默丢弃**（此为合格面）；唯 queue=TaskQueue 时 `force` 内 `parent.isShutdown()` 抛出的 REE key 为 `"nop.err.thread-pool-executor.io.nop.task-queue-not-running"`（`TaskQueue.java:57,63`）——**key 含 "io.nop.task" 改名残迹且非注册错误码**，与 queue-full 双口径并存（错误处理维度线索）。
2. **core>max 唯一可达路径 = ThreadPoolConfig 外部 bean（#6）**，init 裸 IAE；3 参工厂 core=max 不可能 core>max。
3. **无界/准无界 queue**：#1（STPE 固有）、#5/#18（queueSize 直通）、#8/#17（STPE 固有）。
4. **daemon=false 默认**：3 参工厂（#5）波及 #11/#12/#13/#18/#19——均有 destroy 配对（合格），但「差一行 destroy」即 JVM 挂住（C4-010 同族回归面）；#8/#17 已立案。
5. **`prestartAllCoreThreads()`**（`StandardThreadPoolExecutor.java:55,62,69,76`）：所有 Standard 族池**构造即预启动全部 core 线程**——#2（4 线程）与全部配置池建池即持有 core 线程，配合 `allowCoreThreadTimeOut` 死配置（C4-011）→ core 线程永久存活，为 CL-024 的每租户累计提供机制。

---

## ③ 关闭语义全扫（C4-002 的配对表扩展）

### 创建/关闭配对表（谁创建谁关闭）

| 池 | 创建者 | 关闭者 | 状态 |
|---|---|---|---|
| globalTimer / globalWorker / cachedThreadPool | GlobalExecutors 惰性单例 | **无**（全局生命周期，进程退出） | 设计使然；destroy 可逆性缺失已立案 C4-005 |
| nop-sync-executor | SyncThreadPoolExecutor.INSTANCE 静态 | destroy no-op | C4-007 已立案 |
| nop-virtual-thread | Jdk21CommonsCoreInitializer.initialize() | **CoreInitialization.destroy() 不关闭**：`ICoreInitializer.destroy()` 默认空实现（`ICoreInitializer.java:23-24`），Jdk21 初始化器未覆写 → 平台 destroy 后该池存活；仅在再次 initialize 时被 `register` 顶替销毁 | **新立案 C4-012**（创建/关闭归属不对称） |
| GrpcServer 自建 | GrpcServer.newBuilder（ownExecutor） | doStop:159-162 destroy | 合格 |
| JdkHttpClient 自建 | JdkHttpClient.init:111 | stop:155-157 destroy | 合格（无等待） |
| SimpleRpcServer 自建 | start:132（ownExecutor） | stop:174-178 destroy | 合格 |
| SimpleRpcClientFactory timer+executor | start:122,126（ownExecutor） | destroy:136-146 | 合格 |
| SingleThreadConfigExecutor | 构造:19 | stop:23-25 destroy | 合格 |
| AbstractBatchProcessService | start:54 | destroy:72-78 | 合格 |
| DefaultTaskExecutionQueue | doStart:145（ownExecutor 守卫） | doStop:152-158 | 合格（现存正确范式） |
| NioFileWatchService | doStart:58 | doStop（cancel+shutdown） | 合格（第 1 轮已核对，维持） |
| ExportDbTool / ImportDbTool | run 内 :150/:232 | finally destroy（ExportDbTool:187 / ImportDbTool:261） | 合格 |
| JavaScriptService | init:40 | doStop:134 destroy | 合格 |
| StreamControlRpcProxyFactory | start:62 timer | doStop:110-116 | 合格 |
| ThreadHelper.shutdownAndAwaitTermination | — | **全仓生产代码 0 调用者**（本轮 repo-wide grep 复核，仅 ThreadHelper 自身与测试） | 平台自有优雅关闭工具为死代码，强化 C4-002 证据 |

**结论**：A 面设施及全部平台调用方的「有创建无关闭」站点表为空——除三个全局单例（设计使然）与 nop-virtual-thread（新立案 C4-012）外，全部自建池均有 destroy 配对。

### shutdown 后提交的调用方感知面

| 池 | destroy 后 execute/submit 行为 | 调用方感知 |
|---|---|---|
| DefaultThreadPoolExecutor | **无 checkEnabled 守卫**：execute → TPE 拒绝 → force → `TaskQueue.force:56-57` 抛 `REE("...io.nop.task-queue-not-running")` **直接外抛**；`StandardThreadPoolExecutor.execute:156-163` 的 catch 只处理 super.execute 抛出的 REE，force 自身抛出的 REE 绕过 `:161` 的 `submittedCount.decrementAndGet()` → **每笔 shutdown 后提交使 submittedCount 永久 +1**（与 C4-002(5) 记载一致，本轮确认绕行路径为 force 内部抛出而非 force 返回 false） | 裸 JDK REE（非 NopException），消息 key 非注册错误码 |
| DefaultScheduledExecutor | checkEnabled → `NopException(ERR_SERVICE_NOT_ACTIVE)`（`DefaultScheduledExecutor.java:100-105`） | NopException，正确面 |
| VirtualThreadTaskExecutor | **无守卫**：destroy 后 execute → `ExecutorService.execute` 裸 REE | 裸 JDK REE |
| SyncThreadPoolExecutor | 永不拒绝（直通） | n/a |

---

## ④ `new Thread` 裸创建站点逐一判定

- **A 面（nop-commons concurrent/cache/pool + nop-core/nop-utils 平台设施）**：`grep "new Thread(" ` **0 命中**——全部线程创建经 `NamedThreadFactory`→`NopThread`（命名 prefix-poolN-threadN / daemon 显式 / UEH 落 LOG.error，第 1 轮 C4-010 已核合格，维持）。`ThreadHelper` 仅含 `shutdownAndAwaitTermination`/`sleep`/中断恢复，无线程创建；`ContinuationExecutor` 仅 ThreadLocal<Continuation>，无线程创建。**A 面裸创建零发现。**
- **基线 A C4 节复核**：该节为 grep 文本清单（executor 引用行），非裸创建站点；基线中无 A 面裸 `new Thread` 站点，与本轮 grep 一致。
- **全仓裸创建站点**（本轮 repo-wide grep，30 处，全部在业务/外围模块）：nop-stream 13 处（core/runtime/connector/fraud-example，含 `NopStreamThreadFactory.java:48` 裸工厂）、nop-ai 10 处（agent/engine/toolkit/gateway/core）、nop-persistence/nop-message/nop-tools/nop-integration/nop-benchmark 7 处。逐一判定属各模块并发审计范围（nop-ai-agent 已有专项 WI；nop-stream 是否已派工待核，登记 CL-023），不越界展开。抽样核对：`InMemoryActorRuntime.java:302`（"agent-actor-"+actorId 命名，每 actor 单线程池）、`DefaultAgentEngine.java:472`（cached 池线程工厂命名）等均已具名。

---

## ⑤ 虚拟线程治理面（Jdk21CommonsCoreInitializer 开关语义）

事实链（全部本轮独立核对 live code）：

1. **开关默认 true**：`CFG_ENABLE_VIRTUAL_THREAD_POOL = varRef(s_loc, "nop.commons.enable-virtual-thread-pool", Boolean.class, true)`（`CommonConfigs.java:67`）。
2. **注册入口**：`Jdk21CommonsCoreInitializer.initialize()`（经 `META-INF/services/io.nop.core.initialize.ICoreInitializer` ServiceLoader 自动加载）→ `CFG` 为 true 时 `VirtualThreadTaskExecutor.registerGlobalWorker()` → `GlobalExecutors.register(...)`。
3. **方法名与行为不符**：`registerGlobalWorker` 注册的名字是 `NOP_VIRTUAL_THREAD = "nop-virtual-thread"`（`VirtualThreadTaskExecutor.java:22`），**不是** `nop-global-worker`——该开关**不会**把 globalWorker/globalTimer 切换为虚拟线程实现；全平台 `globalWorker()`/`globalTimer()` 消费方在开关开/关下行为完全相同。开关名 `enable-virtual-thread-pool` 给运维「平台已启用虚拟线程承载」的暗示与事实不符。
4. **虚拟 vs platform 的参数语义断裂**：`VirtualThreadTaskExecutor.getConfig()` 返回**裸 `new ThreadPoolConfig()` 只设了 name**（:19-23,35-37）——外界读到 core=4/max=20/queue=10000/keepAlive=60 的**虚构默认值**，对每任务一虚线程（无队列、无上限、无 keepAlive、无拒绝语义）的实体全部无意义；`refreshConfig()` 空实现（:55-57）、`stats()` null（:40-42，C4-007 已立案）。若任何治理/巡检代码遍历 `GlobalExecutors.allExecutors()`（public API，`GlobalExecutors.java:34-36`）读取 config/Stats，得到的是假参数。
5. **可达性**：Java 层 grep 无消费者，但**配置字符串通道存在**：`ModelBasedBatchTaskBuilderFactory.getExecutor(name):207-213` 经 `GlobalExecutors.getExecutor(executorName)` 按名解析——批量模型配置写 `executor: nop-virtual-thread` 即可达该池。届时 maxPoolSize/queue 等治理旋钮**静默无效**（虚线程池无上界并发），与 C4-004 的「无上界共享池」同构但更隐蔽（无任何拒绝信号，submit 永不失败）。
6. **生命周期不对称（关闭语义，③ 的配对缺口）**：创建在 `initialize()`，`ICoreInitializer.destroy()` 默认空、初始化器未覆写 → `CoreInitialization.destroy()`（`CoreInitialization.java:161-191`）不关闭该池；`initializationLevel=-1` 后再次 initialize 会再次 `registerGlobalWorker()` → `GlobalExecutors.register:102-107` 顶替并 `old.destroy()`——按名缓存了旧引用的组件（批量模型解析面）手中的池变为已 shutdown（提交裸 REE）。且平台 destroy 后、下次 initialize 前，该池仍接受提交（在飞任务越过平台销毁点继续执行）。

**立案为 C4-012（P2，治理面）**，理由与诚实标注：当前 Java 层零生产消费方、触发依赖配置字符串或 re-init 时序，正确性后果今日未发生；但「默认 true 的误导性开关 + 全局注册表内的假参数实体 + 平台 destroy 不回收」三者为可机械验证的设施事实，按设施治理口径立案（与 C4-005「守卫缺失、当前无调用者」同判级逻辑）。

---

## 发现条目（新增，自 C4-011 起）

### [nop-kernel-concurrency-C4-011] ThreadPoolConfig 存在 5 字段「@ConfigBean 可配但实现零读取」死旋钮族：threadPriority/allowCoreThreadTimeOut 全仓零消费者，工厂分支与热更路径静默丢弃

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ThreadPoolConfig.java:23,27`（字段）；`concurrent/thread/NamedThreadFactory.java:34-36`（两参构造硬编码 NORM_PRIORITY）；`concurrent/executor/ExecutorHelper.java:61,78`（两参工厂调用）、`:81-83`（maxPoolSize<0 分支丢弃 core/keepAlive/queue）、`:106-126`（updateThreadPool 丢 queueCapacity/daemon/allowCoreThreadTimeOut）
- **证据片段**:
  ```java
  // ThreadPoolConfig.java:23,27 —— @ConfigBean 暴露、setter/getter 齐备
  private boolean allowCoreThreadTimeOut = false;
  private int threadPriority = Thread.NORM_PRIORITY;
  // 全仓 grep（main 代码，排除 ThreadPoolConfig.java 自身）：getThreadPriority()/isAllowCoreThreadTimeOut() 零命中
  // ExecutorHelper.java:61/78 —— 两参 NamedThreadFactory，priority 恒 NORM
  ThreadFactory factory = new NamedThreadFactory(name, config.isThreadDaemon());
  ```
- **严重程度**: P2
- **并发场景**: 所有经 `ThreadPoolConfig`（@ConfigBean）配置线程池的站点：IoC beans 注入（GrpcServer、DefaultTaskExecutionQueue、TcpServer 族配置路径）与代码内构造（GlobalExecutors 三池、newExecutor 调用方）。用户/运维对 core 线程超时回收与线程优先级的配置预期与实现零关联。
- **触发时序**: (1) 配 `allowCoreThreadTimeOut=true` 期望「空闲 core 线程 60s 后回收」：`ExecutorHelper.newThreadPoolExecutor` 与 `newScheduledExecutor` 均不调用 `executor.allowCoreThreadTimeOut(true)`，且 `StandardThreadPoolExecutor` 构造器 `prestartAllCoreThreads()`（:55,62,69,76）**建池即预启动全部 core 线程**——配置完全无效，core 线程永久常驻。(2) 配 `threadPriority=MIN_PRIORITY` 期望降低池线程优先级：两参 `NamedThreadFactory(name,daemon)` 委托三参构造硬编码 `Thread.NORM_PRIORITY`，`config.threadPriority` 无任何读取点（本轮 repo-wide grep 排除定义文件后零命中，含 `getThreadPriority`/`setThreadPriority`/`AllowCoreThreadTimeOut` 全变体）。(3) maxPoolSize<0（cached 语义）分支：corePoolSize/keepAliveSeconds/queueCapacity/allowCoreThreadTimeOut 四字段全部被硬编码（0/60s/SynchronousQueue/否）覆盖——用户配的 core=8/queue=100 在该分支下静默变 0-core SQ cached。(4) 热更 `updateThreadPool` 对 queueCapacity/daemon/allowCoreThreadTimeOut 静默忽略，运维热更队列容量「成功」但无效。与 C4-001（maxPoolSize 副作用改写）、C4-002（waitForTasks/awaitTermination 死值）、C4-008（CFG_CONTINUATION_MAX_QUEUE_SIZE 零使用）同族——**平台线程池配置契约的系统性死旋钮面共 8 项，本轮补齐其余 5 项中的 2 个独立死字段**（keepAliveSeconds/queueCapacity 在特定分支的丢弃不单独立案，并入本条 F3 矩阵）。
- **现状**: 配置类字段完备、实现读取零散；无任何 javadoc 声明哪些字段在哪些路径生效。
- **风险**: 运维基于 @ConfigBean 契约做容量/回收/优先级调优全部落空且无告警；`prestartAllCoreThreads` 与 allowCoreThreadTimeOut 死值的组合使「多池+core 常驻」成为线程数底线（叠加 CL-024 每租户累计）。
- **建议**: (1) 立即修：`newThreadPoolExecutor` 落实 `allowCoreThreadTimeOut`（`executor.allowCoreThreadTimeOut(config.isAllowCoreThreadTimeOut())`）、`NamedThreadFactory` 增加 priority 传递并让两工厂传入 `config.getThreadPriority()`；(2) maxPoolSize<0 分支改为读取 config 的 core/keepAlive（保留 cached 语义仅由 maxPoolSize<0 表达）；(3) 对决定不实现的字段（如 threadPriority 若放弃）删除字段或 @Deprecated + warn 日志，消除契约假象；(4) owner 文档给出生效矩阵（即本文 ① F3 表）。
- **失效面枚举**（F3，字段×路径矩阵全文见本文 ① 节表格；站点面）：`GlobalExecutors` 三池构造（globalTimer/globalWorker/cachedThreadPool 的 config 构造点）、`DefaultThreadPoolExecutor.newExecutor` 两重载、`ExecutorHelper.newScheduledExecutor/newThreadPoolExecutor/updateThreadPool`、全部 ThreadPoolConfig 注入站（GrpcServer:82、DefaultTaskExecutionQueue:145、JdkHttpClient:111 配置族）。
- **信心水平**: 确定（零消费为可机械复核的 grep 事实；prestart 行为为构造器代码事实）
- **误报排除**: 非「字段存在但暂未需要」——@ConfigBean 注解本身构成对配置使用者的契约声明，配而不读属 C4-001 同族死配置缺陷；亦不与 C4-002/C4-008 重复（本条立 threadPriority/allowCoreThreadTimeOut 两个新死字段，其余已立案项仅交叉引用）。
- **复核状态**: 未复核
- **复核状态**: 已复核（2026-10-17 独立复核）——**保留 P2**。getThreadPriority/isAllowCoreThreadTimeOut 全仓仅 getter 定义 0 消费者（复核人独立 grep 证实）；两参 NamedThreadFactory 硬编码 NORM_PRIORITY（:36-39）证实；4 个 StandardThreadPoolExecutor 构造器无条件 prestartAllCoreThreads（:55,62,69,76）证实；maxPoolSize<0 分支硬编码覆盖 core/keepAlive/queue（ExecutorHelper:82）证实；updateThreadPool 丢 queueCapacity/daemon/allowCoreThreadTimeOut 证实。现状：已知未修复。

### [nop-kernel-concurrency-C4-012] nop-virtual-thread 治理面：默认 true 的误导性开关 + 全局注册表内的假参数实体 + 平台 destroy 不回收、re-init 顶替销毁借出引用

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/CommonConfigs.java:67`；`nop-utils/nop-commons-java21/src/main/java/io/nop/commons/jdk21/Jdk21CommonsCoreInitializer.java:10-13`；同目录 `VirtualThreadTaskExecutor.java:16-67`；`GlobalExecutors.java:102-107`；`nop-kernel/nop-core/src/main/java/io/nop/core/initialize/CoreInitialization.java:99-114,161-191`；`ICoreInitializer.java:23-24`；消费通道 `nop-batch/nop-batch-dsl/.../ModelBasedBatchTaskBuilderFactory.java:207-213`
- **证据片段**:
  ```java
  // CommonConfigs.java:67 —— 默认 true
  IConfigReference<Boolean> CFG_ENABLE_VIRTUAL_THREAD_POOL =
      varRef(s_loc, "nop.commons.enable-virtual-thread-pool", Boolean.class, true);
  // Jdk21CommonsCoreInitializer.java:11-12 —— 开 → registerGlobalWorker()
  if (CFG_ENABLE_VIRTUAL_THREAD_POOL.get())
      VirtualThreadTaskExecutor.registerGlobalWorker();
  // VirtualThreadTaskExecutor.java:22 —— 注册名是 nop-virtual-thread，不是 global worker
  config.setName(NOP_VIRTUAL_THREAD);        // getConfig() 返回仅设 name 的裸 config（core=4/max=20/queue=10000 虚构默认）
  // ICoreInitializer.java:23-24 —— default void destroy() {} 且初始化器未覆写
  ```
- **严重程度**: P2
- **并发场景**: JDK21+ 部署且 `nop-commons-java21` 在 classpath（模块自带 ServiceLoader 注册）——每次平台启动即向 `GlobalExecutors` 全局注册表注入一个按名可达的执行器实体；批量模型配置字符串（`ModelBasedBatchTaskBuilderFactory.getExecutor:210` 的 `GlobalExecutors.getExecutor(executorName)` 按名解析）是该实体唯一业务可达通道。
- **触发时序**: 四个可机械验证的事实叠加。(1) **开关名与行为不符**：`enable-virtual-thread-pool` 开/关对 globalWorker/globalTimer 的实现（平台线程）**零影响**（注册名是 `nop-virtual-thread`，`GlobalExecutors.register` 按 `executor.getName()` 落表）——运维以为开关控制平台线程模型，实际只注册了一个无人使用的孤立执行器。(2) **假参数实体**：`getConfig()` 返回 core=4/max=20/queue=10000 的虚构默认值、`refreshConfig()` 空、`stats()` null——经 `allExecutors()` 遍历的监控/治理代码读到全假参数。(3) **无上界且无信号**：若批量模型配置 `executor: nop-virtual-thread`，每任务一虚线程无任何上限/队列/拒绝语义——过载时任务数无界增长且 submit 永不失败（比 C4-004 cached 池更无信号，虚线程廉价但任务对象/内存无界）。(4) **生命周期缺口**：`ICoreInitializer.destroy()` 默认空、初始化器未覆写 → `CoreInitialization.destroy()` 不回收该池（在飞任务越过平台销毁点继续执行）；再次 `initialize()`（level 重置后）时 `registerGlobalWorker()` 重入 → `GlobalExecutors.register:103-106` 顶替并 `old.destroy()`（shutdown）——持有旧引用者（按名解析缓存面）的后续提交抛裸 REE。
- **现状**: 虚拟线程设施处于「注册了但没人用、配置了但全失效、销毁了但没人管」的治理真空；C6 轮已提示二次 registerGlobalWorker 时序，本轮从治理口径（开关语义/参数迁移/生命周期归属）补全立案。
- **风险**: 误导性开关使「平台已虚拟线程化」的判断失真；配置字符串接入即获得无治理无信号的执行器；re-init 时序产生借出引用失效面。今日无正确性事故（Java 层零消费方），全部为激活前可修的治理缺陷。
- **建议**: (1) 开关改名/改名注册目标：若意图是「用虚拟线程替代 globalWorker」，应实现 globalWorker 的实现切换并在文档声明 queue/keepAlive/max 旋钮随之失效；若意图只是注册独立池，命名应为 `registerVirtualThreadExecutor` 且开关默认改 false。(2) `getConfig()` 返回显式声明的空语义 config（或文档标注虚拟执行器字段无意义），`refreshConfig` 打 warn。(3) `Jdk21CommonsCoreInitializer` 覆写 `destroy()` 关闭已注册实例，或 `registerGlobalWorker` 幂等化（已注册即跳过），消除 re-init 顶替销毁。(4) 批量模型按名解析面在 owner 文档列出可用执行器名及各自治理旋钮有效性。
- **失效面枚举**（F3）：`Jdk21CommonsCoreInitializer.initialize`（注册点，无 destroy 覆写）、`VirtualThreadTaskExecutor.getConfig/stats/refreshConfig`（假参数面 3 处）、`GlobalExecutors.register:102-107`（顶替销毁路径）、`CoreInitialization.initialize/destroy`（re-init 时序载体）、`ModelBasedBatchTaskBuilderFactory.getExecutor:207-213`（配置字符串可达通道）、`GlobalExecutors.getExecutor:51-62`（`nop-sync-executor` 无 fallback 分支——同名解析盲区：`NOP_SYNC_EXECUTOR` 常量存在但 syncExecutor 从不入表，按名解析恒 null，属同族治理缺口，顺带登记）。
- **信心水平**: 确定（全部为代码事实；「零 Java 消费方」为 grep 结论并如实标注配置字符串通道）
- **误报排除**: 不虚构运行时事故——明确标注当前无 Java 消费方、触发需配置接入或 re-init 时序；判 P2 依据是设施治理契约（默认 true 的全局注册 + 假参数 + 生命周期缺口）而非已发生的并发故障，与 C4-005 判级逻辑一致。不与 C4-007 重复：C4-007 立 stats()/destroy no-op 的接口契约，本条立开关语义/注册表治理/生命周期归属。
- **复核状态**: 未复核
- **复核状态**: 已复核（2026-10-17 独立复核）——**保留 P2**。全部论断证实：开关默认 true（CommonConfigs:67）；注册名 `nop-virtual-thread` ≠ `nop-global-worker`，「开关对平台线程模型零影响」论断成立（VirtualThreadTaskExecutor:20-28 + GlobalExecutors.register 按 getName() 落表）；getConfig() 返回仅设 name 的裸 config（虚构 core=4/max=20/queue=10000/keepAlive=60）证实；refreshConfig 空/stats null 证实；ICoreInitializer.destroy 默认空 + Jdk21 初始化器未覆写 → CoreInitialization.destroy 不回收、re-init 顶替 old.destroy() 证实；Java 层 0 消费者（复核人 grep 证实）+ ModelBasedBatchTaskBuilderFactory.getExecutor:207-213 按名字符串通道证实（ServiceLoader 注册文件核实存在）。现状：已知未修复。

---

## 维度集外线索（自 CL-023 起）

### [nop-kernel-concurrency-CL-023]（维度集外线索，不立案展开）nop-stream 模块绕开平台池设施：13+ 处裸 `Executors.*`/裸 `new Thread` 自建池

- **实测结论**: 全仓 grep（main 代码）显示 `nop-stream-core`/`nop-stream-runtime`/`nop-stream-connector` 族含 13 处 `Executors.newFixedThreadPool/newSingleThreadScheduledExecutor/newScheduledThreadPool`/裸 `new ThreadPoolExecutor`（`TaskExecutor.java:116`、`CachingTableLookup.java:193-199`、`TaskManager.java:260,275`、`CheckpointCoordinator.java:321,1099,1494`、`JobCoordinator.java:510,1307,2934`、`WebhookAlertChannel.java:110`、`PollingJdbcMessageService.java:96` 等）+ 13 处裸 `new Thread`（含 `NopStreamThreadFactory.java:48` 裸工厂——未设 UEH）。属模块级线程池治理（C4 口径的模块面），不在本轮 A 面范围。
- **不立案理由**: 维度范围界定为平台设施自身（A 面 nop-commons）；nop-ai-agent 等模块已有专项审计，nop-stream 是否已派工待派工表核对，故登记线索不展开。
- **复核状态**（2026-10-17 独立复核）: **确认**。抽验 3 站点属实（stream TaskExecutor `Executors.newFixedThreadPool`、CachingTableLookup:193-199 双裸建、NopStreamThreadFactory:48 裸 `new Thread` 无 UEH）；复核人 grep nop-stream main 共 26~27 处 `new Thread(|Executors.new`，与报告 13+13 计数吻合。模块面归属维持。

### [nop-kernel-concurrency-CL-024]（维度集外线索，不立案展开）每租户/每 topic 线程池线性累计：SimpleRpcClientFactory、StreamControlRpcProxyFactory + prestartAllCoreThreads 机制

- **实测结论**: `SimpleRpcClientFactory` 每 clientName 建 1 条非 daemon timer（C4-010）+ 1 个 core=5 的 executor（:122-126）；`StreamControlRpcProxyFactory` 每 topic 建 1 条 timer（:62）。配合 `StandardThreadPoolExecutor` 构造器无条件 `prestartAllCoreThreads()`（:55,62,69,76）与 `allowCoreThreadTimeOut` 死配置（C4-011），每个租户/topic/客户端实例化即持有「1+N 条永不超时退出的常驻线程」。多租户 SaaS 形态下线程数随租户数线性增长，无共享配额、无总量告警锚点。
- **不立案理由**: 当前调用形态为少量客户端/topic（每实例一线程量级，非并发正确性缺陷）；资源 stance 与总量观测归 C5/C7 维度，登记供其核对。
- **复核状态**（2026-10-17 独立复核）: **确认**。SimpleRpcClientFactory:122（timer）/:126（5,10 executor）、StreamControlRpcProxyFactory:62（per-topic timer）证实；`prestartAllCoreThreads` 无条件预启动（StandardThreadPoolExecutor:55,62,69,76）× `allowCoreThreadTimeOut` 全平台死值（C4-011）机制链证实，「每实例 core 线程永久常驻」成立。移交 C5/C7 维持。

---

## B 面复核（如实记录）

第 1 轮对 46 个 `nop-core`/`nop-xlang` B 面文件 grep 全部线程载体模式 0 命中。本轮按指令要求复核：对 B 面代表文件（`ResourceLoadingCache` 族、`StdDomainRegistry`、`JsonTool`、`ScriptCompilerRegistry` 等）抽样重跑 `ExecutorService|new Thread|ThreadPoolExecutor|ScheduledExecutor|ForkJoin` 模式——**维持零命中**。B 面（全局模型缓存与注册表单例）无线程池载体，**C4 维度 B 面维持零发现**；其缓存并发正确性归 C1/C2，不越界。

## 与第 1 轮的纠偏与补全记录（反虚构）

1. **纠偏 1**：第 1 轮 F3 将 CLI 族站点（CliRunCommand/CliWatchCommand/CliWatchZipCommand/FileWatcherFactory/ZipFileWatcher）标为「executeOn/轻量，已自救」——live code 核对为**直连调度**（无 executeOn），已在 ① 终表纠正（C4-001 失效面内部修正，不改变 C4-001 立案本体）。
2. **补全 2**：第 1 轮漏记 `ScheduledRecoveryManager`（nop-ai-agent，javadoc 明示生产注入 globalTimer，DB 扫描直连）与 `ReportController.java:133`（demo），已入终表 A/B 类。
3. **维持 3**：TaskQueue 弹性扩容正确性（CL-007）、`pollOnce` 纯内存轻量判定、三个全局池参数值、全部 destroy 合格面——逐项复核维持原判，无翻案。

## 总计

- **C4 第 2 轮新增立案：2 条**——P2 × 2（C4-011 死配置旋钮族 threadPriority/allowCoreThreadTimeOut+工厂分支丢弃矩阵；C4-012 nop-virtual-thread 治理面：误导开关+假参数+生命周期缺口）。**无新增 P0/P1**。
- **第 1 轮 10 条维持**，其中 C4-001 失效面按 ① 终表扩展（新增直连站点 5 处、纠偏 CLI 族 5 处分类、概率模型与连接扇出量化）；C4-002 证据强化（`shutdownAndAwaitTermination` 全仓生产 0 调用者复核、shutdown 后提交的 submittedCount 泄漏路径精确到 `TaskQueue.force:56-57` 绕过 `StandardThreadPoolExecutor.java:161` decrement）；C4-003 F3 扩展（ThreadPoolConfig 重载路径 core>max 无守卫，init 裸 IAE）。
- **CL 维度集外线索：2 条**（CL-023 nop-stream 裸池宽度；CL-024 每租户池线性累计+prestart 机制）。
- **B 面：维持零发现**（抽样复核通过）。
- **终止信号判定**：本轮新增全部为 P2，无 P0/P1；五项深挖指令（①~⑤）均已产出闭环输出（终表/矩阵/配对表/裸创建判定/治理面立案）；剩余未探面仅为业务模块自建池（属各模块专项审计范围，已以 CL-023 登记交接）。**建议 C4 维度第 2 轮收敛、终止，不再进行第 3 轮**；若修复 plan 实施后需验证，以「C4-001/C4-011/C4-012 修复回归 + ② 参数矩阵逐行核对」作为单次复审范围。
