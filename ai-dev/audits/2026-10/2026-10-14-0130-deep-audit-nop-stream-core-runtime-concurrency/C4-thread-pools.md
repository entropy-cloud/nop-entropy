# C4 线程池与执行器治理 — nop-stream-core-runtime（第 1 轮初审）

> 审计维度：C4（线程池与执行器治理）。指令来源：`ai-dev/skills/concurrency-resource-audit-prompt.md` 共享提示词前缀（{MODULE}=nop-stream-core-runtime）+ C4 正文。
> 盲审纪律：本轮未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；仅以当前 live code 为准。
> 审计日期：2026-10-14。

## 检查范围

- `nop-stream/nop-stream-core/src/main/java`（439 files / 57,135 lines）
- `nop-stream/nop-stream-runtime/src/main/java`（132 files / 29,922 lines）
- `_` 前缀生成文件不作为发现对象。
- 覆盖方式：以基线 `_tmp/cra-baseline-nop-stream-core-runtime.txt` C4 threads/schedule 节选圈定重点，另做全量 `Executors.new*` / `new ThreadPoolExecutor` / `new Thread(` / `DefaultScheduledExecutor` / `HttpServer.create` / `commonPool` / `parallelStream` / `supplyAsync|runAsync`（未指定 executor）扫描（扫描结果见下文"覆盖对照"），对每个池逐一核对：参数依据、队列有界性、拒绝策略与被拒任务去向、线程命名、关闭路径（shutdown + awaitTermination + shutdownNow）、任务隔离、定时任务堆积策略与异常致死语义（JDK fixed-rate 语义）、F8 生命周期对称性。
- 定时任务全量核对：heartbeat（TM）、failureDetector（JC）、periodicCheckpoint（JC）、barrier-injector（TaskCheckpointWiring）、governanceSweeper（OpsJobManager）、metrics reporter（StreamMetricsReporter）、poller（PollingJdbcMessageService）、lookup refresh scan（CachingTableLookup）、webhook drainLoop、checkpoint timeoutScheduler、RPC proxy timer。
- Owner doc 对照：`docs-for-ai/03-modules/nop-stream.md`（含"自建调度线程裁定 ST-6"——治理清扫/指标上报/webhook 投递线程为引擎自建属已声明边界，不作为缺陷）。

### 覆盖对照（负面清点）

- **commonPool / ForkJoinPool**：两模块 main 源码零命中（无 `parallelStream`、无 `.parallel()`、无 `ForkJoinPool` 引用）——无 commonPool 间接使用面。
- **未指定 executor 的异步工厂**：`supplyAsync/runAsync/thenApplyAsync/orTimeout` 零命中；`CompletableFuture` 仅作为 PendingCheckpoint 完成载体（非线程池提交面）。
- **裸线程创建**：`NopStreamThreadFactory`（工具）、`ProcessingTimeServiceDriver`（见 C4-008 命名问题）、`CachingTableLookup` 两处 lambda（daemon+命名）、JC barrier fanout lambda（daemon+命名）、`PollingJdbcMessageService` lambda（daemon+命名，见 C4-008）、两个 main 入口的 shutdown hook（JDK 钩子线程，合规）。
- **线程命名**：全模块统一 `NopStreamThreadFactory`（daemon + 递增序号）或等价 lambda；例外见 C4-008。
- **scheduleAtFixedRate 异常致死（F9/C4 步骤 5）**：全部 5 处 fixed-rate 循环（heartbeat、detectFailures、barrier-injector、metrics report、governanceSweep）均有任务体级 try/catch 全包容，无"单次未捕获异常永久杀死调度"的暴露面。

## 线程池清单表

| # | 池/线程 | 参数 | 队列 | 拒绝策略 | 关闭路径 | 判定 |
|---|---|---|---|---|---|---|
| 1 | core `TaskExecutor.executorService`（LOCAL 执行） | fixedThreadPool(默认=availableProcessors)，daemon，命名 `stream-task-executor-N` | **无界** LinkedBlockingQueue | 默认 Abort（无界队列下不可达） | `shutdown()` 仅 graceful，不中断、不 await（`TaskExecutor.java:372-377`）；`shutdownNow` 存在但 LOCAL finally 未用 | **发现 C4-001 / C4-006** |
| 2 | core `CachingTableLookup.refreshPool` | fixedThreadPool(refreshMaxConcurrency)，daemon，命名带实例 hash | **无界** LinkedBlockingQueue | 无（无界不拒） | `close()` shutdownNow（幂等）；生产 close 调用点在本仓库审计范围内不存在（仅测试引用，契约落在审计范围外的宿主函数） | **发现 C4-002** |
| 3 | core `CachingTableLookup.scanScheduler` | singleThreadScheduled，daemon，命名 | fixedDelay 非重叠扫描 | n/a | `close()` shutdownNow | 通过 |
| 4 | core `ProcessingTimeServiceDriver` 线程 | 单发裸线程，daemon | n/a（tick+volatile due-flag，不扫表） | n/a | `StreamTaskInvokable.invoke()` finally `shutdown()`（start/stop 对称，幂等） | 命名见 **C4-008** |
| 5 | runtime `TaskManager.taskExecutor` | ThreadPoolExecutor(capacity,capacity,60s)，命名 `tm-task-<nodeId>` | **有界** LBQ(1000) | Abort → typed `reportAssignmentFailure`（响亮） | `stop()`：shutdownNow → awaitTermination(5s) → 逐任务 cancel | 通过（capacitySemaphore 钉住 accepted≤capacity=线程数，见 C4-001 失效面 (d)） |
| 6 | runtime `TaskManager.terminalReportExecutor` | 1,1，命名 `tm-terminal-report-<nodeId>` | 有界 LBQ(64) | 自定义：含 shutdown 后一律 inline run（终态报告不丢） | `stop()` 最后关 + awaitTermination(5s) + shutdownNow（排队报告先排干，防与数据源关闭竞争） | 通过 |
| 7 | runtime `TaskManager.heartbeatExecutor` | singleThreadScheduled，命名 `tm-heartbeat-<nodeId>` | fixedRate；全迭代 catch（防 JDK 静默死亡） | n/a | `stop()` 第一个 shutdownNow | 通过 |
| 8 | runtime `TaskManager.commitExecutor` | 1,1，命名 `tm-commit-<nodeId>` | **有界** LBQ(默认 1000，plan 369 R5-CC-10) | CallerRuns + `commitCallerRuns` 计数（饱和可观测） | `stop()` shutdownNow | 通过（owner doc 已声明指标面） |
| 9 | runtime `JobCoordinator.failureDetector` | singleThreadScheduled，命名 `jc-failure-detector-<jobId>` | fixedRate；方法级全 catch | n/a | `stop()` / `failJob()` shutdownNow | 通过 |
| 10 | runtime `JobCoordinator.periodicCheckpointScheduler` | singleThreadScheduled，命名 `jc-periodic-checkpoint-<jobId>` | **fixedDelay**（慢触发不堆积）+ try/catch + `incrementTriggerFailures` | n/a | `stopPeriodicCheckpoints()`：shutdownNow + await(5s)；`stop()` 先停它再停 checkpointCoordinator（顺序正确） | 通过 |
| 11 | runtime `JobCoordinator.barrierFanoutExecutors`（per-node） | singleThread，daemon，命名 `jc-barrier-fanout-<jobId>-<node>` | **无界** LinkedBlockingQueue | 无 | `stop()`：shutdown → 逐池 await(5s) → shutdownNow | **发现 C4-005** |
| 12 | runtime `CheckpointCoordinator.timeoutScheduler` | singleThreadScheduled，命名 `checkpoint-timeout-<jobId>` | 每 checkpoint 一个一次性超时任务（完成不取消，fire 时 isDone 短路；短生命周期有界） | n/a | `shutdown()`：shutdownNow + await(5s) | 通过 |
| 13 | runtime `CheckpointCoordinator.persistExecutor` | fixedThreadPool(asyncSnapshotThreadPoolSize≥1)，命名 `checkpoint-persist-<jobId>`，惰性创建 | 无界 LBQ（积压受 maxConcurrentCheckpoints 触发门控——pending 在 persist 完成才释放） | RejectedExecutionException 内联兜底（失败副作用同线程补跑） | `shutdown()`：shutdown → await(5s) → shutdownNow | 通过 |
| 14 | runtime `CheckpointCoordinator.commitFanoutExecutor` | 1,1，命名 `checkpoint-commit-fwd-<jobId>` | **有界** LBQ(1000)（镜像 tm-commit 形态） | CallerRuns | `shutdown()` 同上（排水让 shutdown 竞速时 epoch 提交仍送达） | 通过 |
| 15 | runtime `CheckpointCoordinator.retentionExecutor` | singleThread，命名 `checkpoint-retention-<jobId>`，惰性创建 | submit 型；RetentionCleaner CAS 串行 + recheck 补跑（防重复 deleteCheckpoint 竞态） | REE → WARN + 复位串行标志 | `shutdown()` 同上 + 终局同步 cleanup 收敛 | 通过 |
| 16 | runtime `PollingJdbcMessageService.poller` | scheduledThreadPool(2)，daemon，命名 `polling-jdbc-msg`（无实例标识） | scheduled 队列；每订阅 fixedDelay + poll 全 catch | n/a | `close()`：shutdownNow + await(2s)；Subscription.cancel 对称 | **发现 C4-004 / C4-008** |
| 17 | runtime `TaskCheckpointWiring` barrier-injector | singleThreadScheduled，命名 `barrier-injector-<jobId>` | fixedRate + 任务级 try/catch + incrementTriggerFailures | n/a | `GraphModelCheckpointExecutor` finally → `TaskCheckpointWiring.shutdown`：shutdownNow + coordinator.shutdown | 通过 |
| 18 | runtime `StreamMetricsReporter.scheduler` | singleThreadScheduled，命名 `stream-metrics-reporter` | fixedRate + report 全 catch | n/a | `stop()`：shutdownNow + await(10s)；start/stop 幂等对称 | 通过 |
| 19 | runtime `OpsJobManager.governanceSweeper` | singleThreadScheduled，命名 `stream-ops-governance` | fixedRate + governanceSweep 全 catch（R5 CC-07 注记） | n/a | `close()`：shutdownNow | 通过 |
| 20 | runtime `StreamOpsHttpServer` executor | fixedThreadPool(4)，daemon，命名 `stream-ops-http-N` | **无界** LinkedBlockingQueue | 无 | **`stop()` 仅 `httpServer.stop(0)`，executor 未 shutdown** | **发现 C4-003** |
| 21 | runtime `WebhookAlertChannel.deliveryExecutor` | singleThread，daemon，命名 `stream-alert-webhook` | 入口 `pending`=ArrayBlockingQueue(256)，满则丢弃+WARN（best-effort 已声明） | offer 失败即弃（有 WARN，可观测） | `close()`：shutdownNow；drainLoop 逐事件容错、仅中断退出 | 结构通过；`delivered` 累积见 **C4-007** |
| 22 | runtime `StreamControlRpcProxyFactory.timer` | 平台 `DefaultScheduledExecutor.newSingleThreadTimer("stream-rpc-client-<topic>")`（每 proxy 一个） | 平台设施（不重审） | n/a | `doStop()`：client.stop → channel close → timer.destroy；main 入口与 RpcDistributedExecutor teardown 均配对 | 通过（正确复用平台并发设施） |
| 23 | runtime `RunningTask` 任务线程 | 宿主于 #5（tm-task-<nodeId>），RunningTask.run 阻塞 invokableLatch 等 install（30s 窗口） | — | — | RunningTask.cancel 协作取消；`stop()` 逐任务 cancel | 通过 |

**任务隔离总评**：控制面/数据面/提交面/持久化面/指标面在 runtime 侧分池清晰（TM 四池 + JC 三类 + CheckpointCoordinator 四 executor 各司其职，并有 owner-doc 声明的隔离裁定记录）；隔离缺口集中在 JDBC transport 的共享 poller（C4-004）。core 侧 LOCAL 执行把长驻流任务放进 CPU 核数大小的固定池是唯一的结构性隔离/容量缺陷（C4-001）。

---

## 发现条目

### [nop-stream-core-runtime-C4-001] LOCAL 执行固定池=CPU 核数 + 长驻任务阻塞有界输出队列 → 池耗尽互等死锁（无守卫、无超时、无恢复）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/TaskExecutor.java:116,372-377`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java:543,570-574`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:346-374`
- **证据片段**:
  ```java
  // TaskExecutor.java:116 —— 池大小默认 = availableProcessors，队列无界
  this.executorService = Executors.newFixedThreadPool(poolSize,
          NopStreamThreadFactory.named("stream-task-executor-"));
  // ResultPartition.java:357-359 —— 生产者阻塞语义（无 materialization 点时）
  bufferPool.acquire();
  try {
      queue.put(element);          // 每边队列默认容量 1024，满则 put 永久阻塞
  // StreamExecutionEnvironment.java:574 —— 主线程无超时等待
  executor.awaitCompletion();
  ```
- **严重程度**: P1
- **并发场景**: LOCAL 部署模式主调线程 + `stream-task-executor-N` 池线程。三个入口共享同一形状：`StreamExecutionEnvironment.runLocal`（`StreamExecutionEnvironment.java:543`）、`GraphModelCheckpointExecutor` checkpointed LOCAL（`:275`）与 savepoint（`:391`）——均 `new TaskExecutor()` 默认构造。
- **触发时序**: 前提：拓扑子任务总数 > 池线程数（默认 = CPU 核数）且任一边数据量达到队列容量（默认 1024/边；无限源必然达到）。T1 主线程按拓扑序（`plan.getSortedVertexIds()`，源先汇后）把全部 SubtaskTask 提交进池；超出线程数的汇任务进入无界队列排队。T2 各池线程上的源/中间任务持续 emit，目标 ResultPartition 队列填满后阻塞在 `queue.put`（`ResultPartition.java:359/373`）或 `bufferPool.acquire`。T3 全部池线程 park，排队的汇任务（消费者）永不获得线程 → 队列永不消化 → 生产者永久阻塞，**池内互等死锁成立**；主线程在 `awaitCompletion()`（无超时重载）永久阻塞，无错误、无 watchdog、无恢复（LOCAL 无 SupervisionLoop 区域重启可打破，任务全部 non-terminal 也不会触发任何重启）。
- **现状**: 池容量与任务总数之间无任何守卫（无"任务数 > poolSize 即 typed fail-fast"），无界队列使超编任务**静默排队**而非响亮失败；`shutdown()` 是 graceful 语义，对已阻塞任务无解放作用。
- **风险**: 并行度 × 顶点数超过核数的任何 LOCAL 作业（含 checkpointed LOCAL/savepoint 路径）在数据量过边容量后**必然**永久挂死；嵌入宿主（`env.execute()`）调用线程被无限占用。按校准"负载上升必然触发的死锁"判 P1（P0 的"可达死锁"条目未采纳原因：需拓扑规模超过核数这一配置前提，非主路径无条件可达；已在复核建议中注明可上浮）。
- **建议**: ① `TaskExecutor` 增加容量守卫：累计任务数 > poolSize 时 typed fail-fast（对齐 `TaskManager.capacitySemaphore` 先例——runtime 侧同族问题已被该信号量钉死）；② 或放弃 fixedPool=cores，改每子任务一线程/虚拟线程（流任务长驻、非 CPU 密集，核数池本就不匹配执行模型，Flink 同形问题以 slot=每任务一线程解决）；③ `awaitCompletion` 提供超时 + 池排队深度可观测。
- **失效面枚举**: (a) `TaskExecutor` 池本体（本发现）；(b) `StreamExecutionEnvironment.runLocal:543` 默认构造（同形状，无守卫）；(c) `GraphModelCheckpointExecutor:275` 与 `:391` 两处默认构造（同形状；SupervisionLoop 存在但死锁态下所有任务 non-terminal 且无法调度消费者，区域重启同样无法打破——重启的重build任务同样排队）；(d) `TaskManager.taskExecutor:242`——同族但已有 `capacitySemaphore(=capacity=线程数)` + 有界队列 + Abort→typed 失败，超编响亮拒绝，**不受影响（已防护）**；(e) `EmbeddedDistributedExecutor:210` / `RpcDistributedExecutor`（TM capacity=16）走 (d) 同一守卫，**不受影响**。
- **信心水平**: 确定
- **误报排除**: 非"见共享字段报线程不安全"——有具体 T1/T2/T3 交错与阻塞点行号；非"有界声明存在"豁免——队列无界且无守卫、owner doc 未声明 LOCAL 模式拓扑规模上限。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C4-002] CachingTableLookup 后台刷新池无界队列且无 in-flight 去重：delegate 挂死时每扫描轮为全部老化 key 重复入队 → 无界增长（OOM 候选）+ 降级 DB 重复探测

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/lookup/CachingTableLookup.java:193-208,211-227,229-253`
- **证据片段**:
  ```java
  // :193 —— fixedThreadPool 默认无界 LinkedBlockingQueue，永不拒绝
  refreshPool = Executors.newFixedThreadPool(refreshMaxConcurrency, r -> { ... });
  // :222-224 —— 每轮扫描为每个"老化"key 无条件入队，无 in-flight 去重
  if (now - e.getValue().loadNanos >= refreshAge) {
      // the worker pool's size is the concurrency bound (review F-2)
      refreshPool.execute(() -> refreshOne(e.getKey()));
  }
  // :239 —— worker 挂死点：delegate.lookup 不返回 → loadNanos 永不更新 → 全部 key 永久老化
  Object value = delegate.lookup(key);
  ```
- **严重程度**: P1
- **并发场景**: 同一实例的 `nop-lookup-refresh-scan-<hash>` 单线程扫描调度器、`nop-lookup-refresh-<hash>-N` 工作池、任务线程同步 `lookup()` 三方并发。
- **触发时序**: 前提：delegate 查询挂死（DB 网络黑洞且无 socket timeout——`refreshOne` 的 catch(Exception) 捕不到"不抛异常的挂死"，F5 画像）。T1 全部工作线程（refreshMaxConcurrency 个）阻塞在 `refreshOne:239`，无任何刷新完成；T2 `scanAndDispatch` 每 refreshIntervalMs 执行一轮，因所有 entry 的 `loadNanos` 永不更新而全部判为老化，每轮为**每个** key `execute` 一个新任务；T3 队列以 keyCount × (1/refreshIntervalMs) 任务/秒单调增长，每个任务闭包钉住 key 引用，永不拒绝。量化：maxEntries=10⁴、interval=1s → 10⁴ 任务/秒（每任务 lambda+引用约百字节级），OOM 为数十小时级。次生（非挂死、仅慢）：delegate 延迟 L > interval I 时，同 key 排队的重复任务不互相短路，每个都真正执行 `delegate.lookup`，对已降级 DB 施加额外探测压力（工作吞吐上限 concurrency/L 封顶，放大有界但方向与自愈相反）。
- **现状**: 队列无界 + 无 in-flight 去重；类 javadoc 对刷新失败已定义"保旧值+下轮重试"的自愈，但自愈以"失败会抛异常"为前提，对挂死失效。
- **风险**: 长时运行 + 上游无超时（JDBC 查找默认无 socket timeout 是常态配置）下必然无界增长；OOM 候选；降级 DB 被加重。
- **建议**: ① in-flight 去重：`Set<Object> refreshing = ConcurrentHashMap.newKeySet()`，入队前 add 成功才 execute，`refreshOne` finally remove；② 或队列改有界 + 满时跳过本轮（扫描周期天然重试，语义无损）；③ javadoc/connector 契约显式声明 delegate 必须自带查询超时。
- **失效面枚举**: (a) `refreshPool`（本发现）；(b) 同类 `scanScheduler`——fixedDelay 非重叠，单任务体且轻量（只读 map+入队），无堆积面，**无碍**；(c) 同类 per-record 同步路径 `lookup()` 不经池，**无碍**。同族 grep（引擎内 `newFixedThreadPool`）：`TaskExecutor:116`（C4-001 覆盖）、`CheckpointCoordinator.persistExecutor:1099`（积压受 maxConcurrentCheckpoints 门控 + REE 内联兜底，**无此缺陷**）、`StreamOpsHttpServer:127`（C4-003 覆盖）。生产 close 调用点：全仓库 main 源码无 `CachingTableLookup` 生产构造点（仅测试），close 契约落在审计范围外的宿主函数（javadoc WI17 声明）——池泄漏风险暂无本地触发面，登记为边界注记。
- **信心水平**: 很可能（挂死前提取决于 delegate 配置；类自身未强制超时）
- **误报排除**: 非"有界声明存在"豁免——无任何界声明；非测试专用代码豁免——生产库类（WI11 交付物）；catch(Exception) 不构成挂死防护（F5 定向核对项）。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C4-003] StreamOpsHttpServer.stop() 不关闭请求 executor（F8 清理不对称）+ 请求队列无界

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/StreamOpsHttpServer.java:127,179-186`
- **证据片段**:
  ```java
  // :127 —— executor 局部变量，未保存字段
  httpServer.setExecutor(Executors.newFixedThreadPool(4,
          NopStreamThreadFactory.named("stream-ops-http")));
  // :179-183 —— stop() 不 shutdown executor（JDK HttpServer.stop 不负责调用方 set 的 executor）
  public synchronized void stop() {
      if (httpServer != null) {
          httpServer.stop(0);
          httpServer = null;
      }
  ```
- **严重程度**: P2
- **并发场景**: `stream-ops-http-N` 请求处理线程 vs 进程内 start/stop 生命周期调用方（`JobCoordinatorMain.shutdown`、`TaskManagerMain.shutdown`、测试矩阵）。
- **触发时序**: T1 `start()` 建池；请求到达时惰性创建至多 4 个核心线程（fixed 池核心线程常驻 park 在队列 take）。T2 `stop()` 仅 `httpServer.stop(0)`——server 停止接受连接，但已建的至多 4 个线程永久存活（daemon，不阻 JVM 退出，也不随 server 停止终止）。T3 同进程再次 `start()`（程序化运维重启/多轮测试）→ 再建 4 线程；每轮 start/stop 泄漏至多 4 线程，单调累积。次要：队列无界——4 个 handler 全阻塞时（如并发 `POST /stop?mode=DRAIN` 各自等待终态 checkpoint，受 `terminationCheckpointTimeoutMs` 界定）后续请求（含 Prometheus scrape）无限排队。
- **现状**: executor 未保存、stop 未 shutdown（对照同模块其余全部池的 shutdown+await+shutdownNow 三段式纪律，本类是唯一缺修点）；队列无界且无拒绝策略。
- **风险**: 长驻进程内多轮重启线程单调增长；慢 handler 下 scrape 延迟放大。主入口（两个 Main）为单生命周期进程、进程退出即清，实际影响有限 → P2。
- **建议**: executor 保存为字段，`stop()` 中 `shutdown()` + 短 `awaitTermination` + `shutdownNow()`；队列容量有界（如 64）+ 满时 503 结构化响应（与该类"无静默"的既有风格一致）。
- **失效面枚举**: 全模块 server/pool stop 路径逐一核对——`TaskManager.stop`、`JobCoordinator.stop`（含 barrier fanout 三段排水）、`CheckpointCoordinator.shutdown`（四 executor 全配对）、`StreamMetricsReporter.stop`、`OpsJobManager.close`、`PollingJdbcMessageService.close`、`WebhookAlertChannel.close`、`StreamControlRpcProxyFactory.doStop`（timer.destroy）：**全部配对，无同类缺修**；唯一缺修点即本类 executor。
- **信心水平**: 确定
- **误报排除**: 非"低频路径优雅性"类豁免——这是明确的资源释放缺失且与全模块治理标准不一致；daemon 不阻 JVM 退出不等于无泄漏（进程内累积）。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C4-004] JDBC transport 下控制面 RPC 与数据面记录投递共享 2 线程 poller：慢数据面独占线程 → 控制消息（barrier ACK/deploy/cancel）延迟

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java:96-99,293,310-344`
- **证据片段**:
  ```java
  // :96-99 —— 全部订阅共享 2 线程
  this.poller = Executors.newScheduledThreadPool(2, r -> {
      Thread t = new Thread(r, "polling-jdbc-msg");
      t.setDaemon(true);
      return t;
  });
  // :293 —— 每订阅一个 fixedDelay 任务，poll 内联执行 consumer.onMessage
  this.task = poller.scheduleWithFixedDelay(this::poll, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
  // :316-318 —— 单轮 poll 无 LIMIT：全量积压一次载入
  List<Row> rows = jdbcTemplate.executeQuery(SQL.begin()
          .sql("SELECT id, payload FROM " + queueTable
                  + " WHERE topic = ? AND id > ? ORDER BY id ASC", topic, cursor) ...
  ```
- **严重程度**: P2
- **并发场景**: 多 JVM JDBC transport 模式（owner doc「运维手册」主推形态）中，2 个 `polling-jdbc-msg` 线程同时承载：数据面 topic 记录投递（`nop-stream.data.*`，consumer.onMessage 内联写入输入通道）与控制面 RPC 处理（`StreamControlRpcServer` 经 `MessageRpcServer` 订阅回调——deployTask/triggerCheckpoint/cancelTask/ACK/notifyCheckpointComplete 全部跑在同一池）。
- **触发时序**: T1 某 data topic 消费者变慢（下游背压）或单轮 poll 拉中大批积压（SELECT 无 LIMIT，全量载入 rows 后逐条内联投递），一个 poller 线程被长时间占用；T2 第二线程被另一慢 topic 占用；T3 控制面订阅的 fixedDelay 任务到点后排队等待空闲线程 → barrier 注入 ACK、deployTask、cancelTask、checkpoint 完成通知全部延迟；延迟超过 checkpoint timeout 时触发 abort→恢复，恢复流量又经同一池投递，形成放大。消息不丢（JDBC 持久队列 + cursor），失效面是**时效性**而非正确性。
- **现状**: 控制面/数据面无池隔离。对照引擎自身先例：tm-commit 因"慢 commit 不得占用 message-service dispatch 线程"而独立成池、jc-barrier-fanout 因"一个慢节点不得拖累其他节点"而 per-node 隔离——本处是同类裁定缺失的最后一处共享面。
- **风险**: 负载上升时控制面时效退化 → checkpoint 超时/恢复风暴放大；无界批（无 LIMIT）放大单次占用时长（该批载入的内存面交叉登记给 C5）。
- **建议**: ① 按 topic 前缀分池：`nop-stream.rpc.*` 控制面订阅用独立小池（1-2 线程），对齐 tm-commit 隔离先例；② poll 查询加 LIMIT 分批投递（缩短单次占用，兼修 C5 面）；③ 线程名带 nodeId（见 C4-008）。
- **失效面枚举**: (a) `poller` 池本体（本发现）；(b) TM `heartbeatExecutor`——独立线程 + 全 catch，**不受影响**；(c) TM 侧 ACK 发送（ackSender 经 coordinatorRpcService）在 poller 线程内联执行——属同一失效面（随 (a) 修复一并解决）；(d) Pulsar/SysDao 等其他 IMessageService 后端的派发线程属平台/其他模块，不在本维度重审（平台基础设施豁免）。
- **信心水平**: 很可能
- **误报排除**: 非"平台基础设施自身实现不重审"豁免——缺陷在本模块对共享池的使用与隔离决策，非 nop-message 实现；非"有界声明存在"——owner doc 未声明 2 线程共享为已接受边界（ST-6 裁定仅覆盖"引擎自建周期线程"的存在性，不覆盖共享隔离）。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C4-005] JobCoordinator per-node barrier fanout 单线程 executor 无界队列：节点 RPC 挂死时队列单调增长、该节点控制面静默失效

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:2932-2941,977-995,2944-2956`（stop 排水 `:722-736`）
- **证据片段**:
  ```java
  // :2933-2938 —— newSingleThreadExecutor 默认无界队列，永不拒绝
  return barrierFanoutExecutors.computeIfAbsent(nodeId,
          id -> java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
              Thread t = new Thread(r, "jc-barrier-fanout-" + jobId + "-" + id);
              t.setDaemon(true);
              return t;
          }));
  // :2947-2949 —— 任务体只有 catch(Exception)：不抛异常的挂死 send 无界占用唯一线程
  fanoutExecutorFor(nodeId).execute(() -> {
      long epoch = fencingEpoch.get();
      try {
          entry.getValue().triggerCheckpoint(barrier, epoch);
  ```
- **严重程度**: P2
- **并发场景**: JC 的 checkpoint 触发线程（periodicCheckpointScheduler/外部触发线程）提交、每节点一个 `jc-barrier-fanout-<jobId>-<node>` 串行发送线程。
- **触发时序**: T1 某节点 RPC send 挂死（网络黑洞；传输层 send 无超时时——JDBC transport 的 send 是本地 INSERT 不挂，网络型 transport 可挂）。T2 该节点唯一 fanout 线程阻塞在一个 `triggerCheckpoint` send 上。T3 每次 checkpoint 触发继续为该节点入队 1 个 barrier 任务（无界队列永不拒绝），队列以 1 任务/checkpointInterval 增长；该节点后续全部 barrier 在队头之后延迟——节点内串行是设计意图（CC-06），但队头永久挂死使整节点 barrier 静默失效，仅能靠 checkpoint 超时 abort（可观测，有 CHECKPOINT_ABORTED 事件）兜底发现。
- **现状**: 与同引擎 tm-commit（有界 1000 + CallerRuns + 计数）、tm-terminal-report（有界 64 + inline）的队列治理标准不一致——本池是 runtime 侧唯一保留"无界队列 + 无拒绝策略"的任务队列。`stop()` 的 shutdown → await(5s) → shutdownNow 排水有界（挂死 send 由 shutdownNow 中断截断）。
- **风险**: 长时运行 + 传输挂死下队列慢增长（内存影响小：每任务为 barrier+node 引用）；主要是该节点控制面失效的**发现时延**依赖 checkpoint 超时，而非队列治理本身。
- **建议**: 队列容量有界（如 100）+ 满时 WARN 丢弃该 barrier（该 epoch 本将超时 abort，语义无损）+ 丢弃计数指标（对齐 tm-commitCallerRuns 先例）；或给 fanout send 包一层有界等待。
- **失效面枚举**: (a) `barrierFanoutExecutors`（本发现）；(b) `cancelAllAssignedTasks:773-805`——同步在调用方线程执行、不经 fanout 池、有 per-task try/catch，**不受队头阻塞影响（已隔离）**；(c) `failureDetector`——独立单线程 + 方法级全 catch，**不受影响**；(d) per-node executor map 按 nodeId 只增——节点数有界（拓扑固定），无增长面。
- **信心水平**: 很可能（挂死前提取决于传输实现是否自带超时）
- **误报排除**: 非"平台基础设施不重审"（JDK executor 默认形态的使用决策在本模块）；非"有界声明存在"——无界是 `newSingleThreadExecutor` 的默认副作用而非声明过的选择。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C4-006] LOCAL 执行取消路径不对称：中断后 finally 仅 graceful shutdown，不 cancel 子任务 → 任务线程持资源无限存活

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java:574,585-605`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/TaskExecutor.java:372-377`
- **证据片段**:
  ```java
  // StreamExecutionEnvironment.java:585-598 —— finally 只 graceful shutdown + 关 buffer pool
  } finally {
      shutdownLocalExecution(executor, plan);
  }
  private void shutdownLocalExecution(TaskExecutor executor, GraphExecutionPlan plan) {
      executor.shutdown();          // TaskExecutor.java:375 —— executorService.shutdown()，不中断、不 cancel
      plan.closeBufferPool();
  ```
- **严重程度**: P2
- **并发场景**: 嵌入宿主调用 `execute()` 的线程（阻塞于 `awaitCompletion():574` 的 `Future.get`）与 `stream-task-executor-N` 上的长驻 SubtaskTask 线程。
- **触发时序**: T1 宿主线程阻塞在 `awaitCompletion()`（无超时）。T2 调用方中断该线程（嵌入宿主取消执行的唯一手段）→ `InterruptedException` 传播出 `awaitCompletion`。T3 finally `shutdownLocalExecution`：`executor.shutdown()`（graceful，运行中任务不受影响）；`closeBufferPool()` 唤醒阻塞在 `bufferPool.acquire` 的**生产者**（typed 失败退出），但阻塞/轮询在 `InputGate` 读取循环（50ms 有界 poll）的**消费者**任务收不到任何取消信号——`SubtaskTask.cancel()` 存在却未被调用 → 这些任务线程在 `execute()` 返回后继续 50ms 轮询无限循环，持有 source 连接、sink 连接、已 open 的算子直至 JVM 退出。
- **现状**: LOCAL 路径"有 shutdown 无 cancel"——与同仓库 SupervisionLoop 的 3 步协作取消配方（`signalCancel` mailbox 标志 + `task.cancel()` 中断 + 有界终态等待，`SupervisionLoop.java:711-718`）形成直接对照；`ProcessingTimeServiceDriver` 有 finally 对称停止，任务本体没有。
- **风险**: 嵌入宿主以中断取消执行时，任务线程与其底层连接泄漏（daemon 自旋），F8 清理不对称。
- **建议**: `shutdownLocalExecution` 中先对全部 `SubtaskTask` 调 `cancel()`（复用 `SupervisionLoop.cancelTaskWithMailbox` 配方：mailbox signalCancel + cancel 中断），再 `executor.shutdownNow()`；与 (C4-001) 建议的 awaitCompletion 超时配套。
- **失效面枚举**: (a) `runLocal` finally（本发现）；(b) `GraphModelCheckpointExecutor` 两入口 finally → `TaskCheckpointWiring.shutdown:377-384`——用 `executor.shutdownNow()`（中断任务线程），且 CheckpointCoordinator.shutdown 兜底，任务响应中断/mailbox 后可退出，风险较低（同族观察项：同样未先走 mailbox 协作取消，列此备查）；(c) `SubtaskTask.cancel` 本体与 `RunningTask.cancel`（TM 侧 `stop()` 逐任务 cancel）——**已配对，无缺陷**。
- **信心水平**: 确定（交错明确；残余时长取决于任务循环对中断/取消的响应点）
- **误报排除**: 非"低频路径"豁免——中断是嵌入形态唯一取消手段，泄漏为资源性；非纯理论——消费者任务的 50ms 轮询循环无终态出口（生产者失败不 seal 分区）。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C4-007] WebhookAlertChannel.delivered 只增不删：生产路径告警事件无限累积

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/alert/WebhookAlertChannel.java:67,180,134-136`
- **证据片段**:
  ```java
  // :67 —— 测试可观测字段，生产构造器即创建
  private final List<AlertEvent> delivered = new CopyOnWriteArrayList<>();
  // :179-180 —— 每次成功投递永久累积，无任何清理路径
  if (response.statusCode() >= 200 && response.statusCode() < 300) {
      delivered.add(event);
      return;
  }
  ```
- **严重程度**: P2
- **并发场景**: 投递线程（`stream-alert-webhook` drainLoop）写，任意 `getDelivered()` 读方遍历（CopyOnWriteArrayList 写时全量复制）。
- **触发时序**: 单调增长（非竞态）：生产实例经 `JobCoordinatorMain alertWebhookUrl=<url>` 启用后，每个成功投递的告警事件（JOB_FAILED/RECOVERY_STARTED/JOB_DEGRADED 等）永久驻留 `delivered`；flapping 作业的恢复风暴下按事件频率持续累积，每条含完整 message/payload 字符串。close() 亦不清理。
- **现状**: 测试可观测字段未做生产/测试区分，无界（F4"只增不删"画像的内存版）。
- **风险**: 长时运行慢泄漏（速率 = 告警事件频率，健康作业接近 0，故障频发环境持续增长）；CopyOnWriteArrayList 每次投递全量复制数组，事件多时投递线程写放大。
- **建议**: 有界环形缓冲（容量 ~100，满则覆盖最旧）或生产默认关闭收集（构造参数/配置键开关，测试显式开启）；类型可改 `ArrayDeque` + 同步。
- **失效面枚举**: (a) `delivered` 字段（本发现）；(b) 同类 `pending` ArrayBlockingQueue(256)——有界 + 满丢弃 WARN（best-effort 已在 owner doc 声明），**无碍**；(c) `deliveryExecutor` close 配对、drainLoop 逐事件容错——**无碍**；(d) 同族 grep 引擎内其他 CopyOnWriteArrayList（JobCoordinator listeners/CheckpointCoordinator listeners/participants）均为注册表语义且有 remove/清理路径，**无碍**。
- **信心水平**: 确定
- **误报排除**: 不适用"测试专用代码豁免"——该字段在生产构造路径初始化并在生产投递路径写入；非 P3 纯防御冗余——是无界累积。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C4-008] 两处线程命名违反模块自身命名契约：多实例不可区分（可观测性）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ProcessingTimeServiceDriver.java:93`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java:97`
- **证据片段**:
  ```java
  // ProcessingTimeServiceDriver.java:93 —— 每个任务一个 driver，名字全部相同
  Thread t = new Thread(this, "processing-time-driver");
  // PollingJdbcMessageService.java:97 —— 多 JVM 各自的 poller 无节点标识
  Thread t = new Thread(r, "polling-jdbc-msg");
  ```
- **严重程度**: P3
- **并发场景**: 单 JVM 多任务（每个 StreamTaskInvokable 一个 driver 线程）与多 JVM 集群（每进程一个 poller）；运维 `/jobs/{jobId}/threaddump` 与日志检索按线程名定位。
- **触发时序**: 不涉及竞态——静态可观测性缺陷：NopStreamThreadFactory 的 javadoc 明文契约（"prefix identifies the owning component **and instance**…log analysis and tests locate these threads by prefix"）与全模块其余创建点（tm-task-\<nodeId\>、jc-failure-detector-\<jobId\>、checkpoint-persist-\<jobId\> 等）均带实例标识，唯此两处裸名。多任务 JVM 的 threaddump 中 N 个同名 `processing-time-driver` 无法对应到 job/vertex/subtask。
- **现状**: 命名缺实例标识。
- **风险**: 故障排查（挂死任务定位、threaddump 分析）效率下降；与 `/threaddump` 运维契约（owner doc P-REQ-5）的可用性不匹配。
- **建议**: driver 命名 `"processing-time-driver-" + jobId + "-" + vertexId + "-" + subtaskIndex`（构造器已可拿到 mailbox 所属任务上下文，需在 StreamTaskInvokable.startProcessingTimeDriver 处传入）；poller 命名带 nodeId/topicNamespace。
- **失效面枚举**: 全模块裸线程/lambda 命名逐点核对——NopStreamThreadFactory（工具本体，合规）、CachingTableLookup 两处（带 identityHashCode 实例标识，合规）、jc-barrier-fanout（带 jobId+nodeId，合规）、两个 main 的 shutdown hook（JDK 钩子，合规）——**仅此两处违规**。
- **信心水平**: 确定
- **误报排除**: 非"防御性代码冗余/纯结构"凑数——与类库自身声明的观测契约直接相悖，且运维面（threaddump 端点）以此为诊断入口。
- **复核状态**: 未复核

---

## 附：正面确认（校准项对照，非发现）

- **无 commonPool/并行流暴露面**、无未指定 executor 的异步工厂（见"覆盖对照"）——异步提交全部走显式命名池。
- **定时任务异常致死（JDK fixed-rate 语义）零暴露**：5 处 fixed-rate 循环全部任务体级全 catch（heartbeat `TaskManager.java:441-499`、detectFailures 尾部 catch、barrier-injector、metrics report、governanceSweep），符合 F9 定向检查。
- **runtime 侧关闭路径纪律完整**：TaskManager（5 池有序关闭 + terminal-report 排水防数据源关闭竞争）、JobCoordinator（fanout 三段排水 → elector → detector → periodic → checkpointCoordinator）、CheckpointCoordinator（四 executor 全 shutdown+await(5s)+shutdownNow + 终局 retention 收敛）、PollingJdbcMessageService/WebhookAlertChannel/StreamMetricsReporter/OpsJobManager 均配对。
- **RejectedExecutionException 兜底面完整**：persist/retention 提交的 shutdown 竞速全部内联兜底或显式 WARN（无静默丢任务）。
- **平台设施复用正确**：RPC timer 走 `DefaultScheduledExecutor`（io.nop.commons.concurrent.executor）并 doStop destroy——未手写重复轮子。
- **TM 容量守卫**：`capacitySemaphore`（=池线程数）+ 有界队列 + Abort→typed 拒绝，是同族中治理最完整的样本（C4-001 的修复参照）。

# C4 线程池与执行器治理 — nop-stream-core-runtime（第 2 轮深挖追加）

> 审计维度：C4（线程池与执行器治理）。指令来源：`ai-dev/skills/concurrency-resource-audit-prompt.md` 共享提示词前缀（{MODULE}=nop-stream-core-runtime）+ C4 正文。
> 盲审纪律：本轮仅读取本目录第 1 轮文件 `C4-thread-pools.md` 与 live code；未读取 `ai-dev/audits/` 下其他审计目录、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`。
> 审计日期：2026-10-14。序号接续第 1 轮（C4-001 ~ C4-008）。

## 深挖第 2 轮追加

### 第 2 轮盲区扫描覆盖说明（负面清点）

针对第 1 轮后的五个指定盲区逐一复核：

- **漏登记的池**：全量重扫 `Executors.new*` / `new ThreadPoolExecutor` / `new Thread(` / 虚拟线程 / ForkJoinPool / commonPool / 并行流——与第 1 轮 23 行清单完全吻合，无漏登记。两处疑似新增点核实为非新池：`GraphModelCheckpointExecutor:738 startBarrierScheduler` 是对 `TaskCheckpointWiring:262` 的包内委托（同一 `barrier-injector-<jobId>` 池，第 1 轮表 #17）；`TaskManager:1138` 的 `AbstractExecutorService` 包装是 CC-17 测试钩子（`tm-terminal-report-test`，测试专用豁免）。
- **任务隔离**：OpsJobManager 多作业宿主下每 job 独立 JobCoordinator/CheckpointCoordinator 池组（per-job 隔离成立）；WebhookAlertChannel 单投递线程跨 job 共享属 owner doc ST-6 已声明边界且队列有界（不重复立项）；除 C4-004（JDBC 共享 poller）外无新共享面。
- **池内任务再提交同池死锁**：全量扫 `Future.get/join/invokeAll` 与 `.submit/.execute` 提交方→池映射——checkpoint persist/retention/commit-fanout 任务均为 fire-and-forget 或跨池提交（RetentionCleaner 递归为非阻塞 re-submit，单线程池队列消化）；LOCAL TaskExecutor 任务体内无向本池的阻塞提交。无新死锁点（C4-001 已覆盖 LOCAL 生产者-消费者互等形态）。
- **F9（定时任务异常致死）其他点**：第 1 轮核对了全部 5 处 fixed-rate 循环；本轮补查 fixedDelay 任务体——发现 1 处缺口（C4-011，scanAndDispatch 无全包容 catch）。其余（PollingJdbcMessageService.poll、JC periodic checkpoint）均有 catch，无新增。
- **池随 bean 泄漏其他点**：`RpcDistributedExecutor.startJob` 有显式 `teardownOnStartupFailure`；`EmbeddedDistributedExecutor` 同族 try/finally 纪律（其注释互证）；两个 Main 为单生命周期进程（daemon 线程不阻 JVM 退出）。发现 1 处多作业长驻进程的失败窗口泄漏（C4-009）。

---

### [nop-stream-core-runtime-C4-009] OpsJobManager.submit 失败窗口孤儿 coordinator：活线程池泄漏 + 对全部治理路径不可见 + 同 jobId 重提交产生双活协调器（非 HA fencing epoch 碰撞）

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/OpsJobManager.java:124,149,174,188-194,424-433`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:509-511,608-611,693-696,1582-1584`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:321-322`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/StreamOpsHttpServer.java:410-413`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/AssignmentPlanner.java:133-150`
- **证据片段**:
  ```java
  // OpsJobManager.java:188-194 —— start/assign/periodic 到 jobs.put 之间无 try/finally
  coordinator.start();
  coordinator.assignTasks();
  if (artifacts.getCheckpointIntervalMs() != null && ...) {
      coordinator.startPeriodicCheckpoints(...);
  }
  jobs.put(jobId, coordinator);      // 仅全成功才登记
  // OpsJobManager.java:131-133 —— 重提交去重只查 jobs map，孤儿不可见
  if (jobs.containsKey(jobId)) { throw new StreamException(ERR_STREAM_JOB_ALREADY_HOSTED)... }
  // JobCoordinator.java:693-694 —— failureDetector fixedRate 已在跑
  failureDetector.scheduleAtFixedRate(this::detectFailures, ...);
  // JobCoordinator.java:1582-1584 —— detectFailures 全包容 catch：孤儿永生
  } catch (Exception e) {
      LOG.error("Error during failure detection for job {}", jobId, e);
  // OpsJobManager.java:424-433 —— close() 只遍历 jobs.values()，孤儿不在其中
  ```
- **严重程度**: P1
- **并发场景**: StreamOpsHttpServer 请求线程（POST /jobs → `jobManager.submit`）与孤儿 coordinator 的 `jc-failure-detector-<jobId>` 调度线程长生命周期交叠；多作业长驻进程（OpsJobManager 为 REST 运维宿主，跨天运行）。
- **触发时序**: 前提：`submit()` 中 `coordinator.start()` 成功之后、`jobs.put(:194)` 之前任一步抛出。具体抛出点（均已核实非理论）：① `AssignmentPlanner.prepareAssignmentsLocked:133-139` 对 materialized assignment 缺子任务映射 typed throw（用户 pipeline factory 返回的 DeploymentPlan 少一个映射即触发）；② 同方法 `:150` 附近的 `clusterRegistry.assignTask(...)`（每子任务一次 registry 写，持久化实现下 DB 故障即抛）；③ `JobCoordinator.start()` 内 `rpc.updateFencingToken(epoch)` 对不可达 TM 的 RPC 异常。T1 请求线程执行 `start()`（:188）成功——`registerCoordinator` 已写 registry、`startFailureDetector` 已调度、fencing epoch 已推送；T2 `assignTasks()`（:189）抛出 → 异常沿 `StreamOpsHttpServer:413` catch 转 HTTP 500，无任何补偿 `coordinator.stop()`；T3 孤儿 coordinator 不在 `jobs` map → `stopJob`/`remove`/`governanceSweep`/`close`（:424-433 只遍历 jobs）全部不可达；`jc-failure-detector` 线程以 fixedRate 永久 tick（detectFailures 全包容 catch 保证任何异常都不致死），连同 CheckpointCoordinator 构造器急切创建的 `checkpoint-timeout-<jobId>` 调度器（CheckpointCoordinator.java:321-322）一并泄漏。T4 客户端按常规重试同 jobId 提交 → 去重检查只查 `jobs.containsKey`（:131-133）→ 放行 → 同 JVM 出现**双活协调器**：两者均为非 HA 模式、`start()` 各自把 fencing epoch 种子化为 1（JobCoordinator.java:608-611），TM 侧 fencing 校验对两者同等放行——孤儿与新协调器的 registry 写、健康事件（JOB_FAILED 告警双份）、失败检测/恢复决策互相交叠， fencing 不再能隔离旧控制面。
- **现状**: `submit()` 成功路径之后的失败窗口无 try/finally 补偿停止；对照同模块 `RpcDistributedExecutor.startJob:207-268` 的显式 `teardownOnStartupFailure` 纪律（注释自述"Without teardown on failure the already-started ... would leak (threads, subscriptions, registry entries)"），OpsJobManager 是同族装配流程中唯一缺该纪律的入口。
- **风险**: 每次失败的提交泄漏 1 个永久 tick 的守护线程 + 1 个 CheckpointCoordinator 池组 + 一条 stale registry 记录；运维 API 对其完全不可见（listJobs/stopJob 均查 jobs map）；长驻进程中反复"失败-重试"使泄漏与双活协调器叠加，告警双发、控制面行为不可预测。按校准"错误路径资源泄漏 + 可具体化触发链"判 P1。
- **建议**: ① `submit()` 以 try/catch 包裹 `start()` 起的全部步骤，catch 中 `coordinator.stop()`（幂等）后再 rethrow——直接复用 RpcDistributedExecutor 的 teardown 配方；② 或将 `jobs.put` 提前到构造后、失败时从 map 中 stop+remove；③ 去重面增补：`ClusterRegistry.registerCoordinator` 对同 jobId 重复注册 fail-fast，作为 jobs map 之外的第二道孤儿防线。
- **失效面枚举**: (a) `OpsJobManager.submit`（本发现，OpsJobManager 模式下 4 个创建点中唯一长驻多作业入口）；(b) `JobCoordinatorMain:238`——单生命周期 main 进程，失败即 JVM 退出清线程（shutdown hook :572），**无实质泄漏面**；(c) `RpcDistributedExecutor.startJob:225`——有 `teardownOnStartupFailure` catch 全量补偿，**已防护**；(d) `EmbeddedDistributedExecutor:265`（assembleCoordinator）——`execute()` 整体 try/finally 纪律（RpcDistributedExecutor 注释互证），**已防护**；(e) 同族"构造即建池"组件：`CheckpointCoordinator`（timeoutScheduler 构造器急切创建）随 (a) 一并泄漏、随 (c)(d) 的补偿路径一并停止，无独立缺口。
- **信心水平**: 确定（三个抛出点均有 live 代码位置；泄漏不可达性经 stopJob/remove/governanceSweep/close 四路径逐一核实）
- **误报排除**: 非"平台基础设施不重审"——泄漏在本模块装配流程的失败窗口，非池实现；非"低频路径优雅性"——assignTasks 的 materialized-mapping 抛出点是普通配置/模型错误，REST 提交失败-重试是常规运维动作；非第 1 轮已登记项——第 1 轮表 #19 仅核对 governanceSweeper 池本体纪律，#9/#12 仅核对"stop 存在"，未核对"每个构造路径都能到达 stop"。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C4-010] TaskExecutor 线程工厂静态前缀 + per-factory 计数器：同 JVM 并发多个 LOCAL 执行实例线程名碰撞（可观测性，第 1 轮 C4-008 清点的方法盲区）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/TaskExecutor.java:116-117`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/util/NopStreamThreadFactory.java:17-18,29,48`
- **证据片段**:
  ```java
  // TaskExecutor.java:116-117 —— 每个 TaskExecutor 实例各建一个工厂，前缀固定无实例标识
  this.executorService = Executors.newFixedThreadPool(poolSize,
          NopStreamThreadFactory.named("stream-task-executor-"));
  // NopStreamThreadFactory.java:17-18 —— javadoc 契约：前缀须标识 owning component AND instance
  // named {@code <prefix>-N}, where {@code N} is a per-factory counter starting at 1
  // The prefix identifies the owning component and instance ...
  ```
- **严重程度**: P3
- **并发场景**: 同一 JVM 内并发存在的多个 `TaskExecutor` 实例——`StreamExecutionEnvironment.runLocal:543`（每次 `env.execute()` 一个）、`GraphModelCheckpointExecutor:275/:391`（checkpointed LOCAL 与 savepoint 各一个，`new TaskExecutor()` 默认构造）。嵌入宿主并发跑多个作业、或 runLocal 与 savepoint 并行时，每个实例的工厂计数器独立从 1 起。
- **触发时序**: 不涉及竞态——静态可观测性缺陷：T1 宿主线程 A 执行 `env.execute()`（作业 jobA）建 TaskExecutor#1，线程名 `stream-task-executor-1..N`；T2 宿主线程 B 并发执行另一作业（或对 jobA 触发 savepoint）建 TaskExecutor#2，线程名同样从 `stream-task-executor-1` 起；T3 threaddump / 日志中出现两组完全同名的线程，`NopStreamThreadFactory` javadoc 声明的"by prefix 定位线程"契约失效，无法归属到 job/入口。与 C4-006 的泄漏形态叠加时（取消后残留线程），同名残留线程进一步不可归属。
- **现状**: 前缀缺实例标识；计数器为 per-factory 实例字段（:29 `private final AtomicLong index`），跨实例无全局去重。
- **风险**: 故障排查（threaddump、日志定位、并发测试断言）在多实例场景失效；纯可观测性，无正确性影响。
- **建议**: 工厂前缀拼入实例标识——`TaskExecutor` 构造器接受可选 taskId/jobId 片段，缺省用 `System.identityHashCode(this)`（对齐 `CachingTableLookup:194` 的既有先例 `"nop-lookup-refresh-" + System.identityHashCode(this)`）。
- **失效面枚举**: 全部 17 处 `NopStreamThreadFactory.named(` 调用点逐一核对（本轮重扫）——runtime 侧 13 处均拼入 `nodeId`/`jobId`（tm-task/tm-commit/tm-heartbeat/tm-terminal-report/jc-failure-detector/jc-periodic-checkpoint/barrier-injector/checkpoint-timeout/checkpoint-persist/checkpoint-commit-fwd/checkpoint-retention，合规）；进程级单例 4 处（stream-alert-webhook/stream-ops-http/stream-metrics-reporter/stream-ops-governance，每 JVM 一个实例，无碰撞面，合规）；**唯一违规点为 TaskExecutor:117**。第 1 轮 C4-008 的失效面清点核对了"裸线程/lambda"并将工厂本体判为合规——本轮修正：工厂本体确实合规，违规在其调用点（固定前缀），属 C4-008 清单的方法性遗漏，非重复登记。
- **信心水平**: 确定
- **误报排除**: 非"防御性代码冗余/纯结构"凑数——与工厂自身 javadoc 契约（"prefix identifies the owning component **and instance**"）直接相悖，且多 LOCAL 实例并发是嵌入形态的受支持用法；非 C4-008 重复——C4-008 的两处（processing-time-driver / polling-jdbc-msg）是"固定名无实例标识"，本条是"per-factory 计数器 + 固定前缀导致跨实例碰撞"，代码位置、机制、失效面均不同。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C4-011] CachingTableLookup 扫描调度任务体无全包容 catch：违反模块自建的"整迭代守卫"不变式（F9 潜伏面，当前无可具体化抛出点）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/lookup/CachingTableLookup.java:205-206,211-227`
- **证据片段**:
  ```java
  // :205-206 —— 方法引用直挂 fixedDelay，无 try/catch 包装
  scheduler.scheduleWithFixedDelay(this::scanAndDispatch,
          refreshIntervalMs, refreshIntervalMs, TimeUnit.MILLISECONDS);
  // :211-227 —— scanAndDispatch 任务体：无任何 catch
  private void scanAndDispatch() {
      Map<Object, Entry> map = cache();
      if (map == null || closed) { return; }
      ...
      for (Map.Entry<Object, Entry> e : map.entrySet()) {
          ...
          refreshPool.execute(() -> refreshOne(e.getKey()));
      }
  }
  ```
- **严重程度**: P3
- **并发场景**: `nop-lookup-refresh-scan-<hash>` 单线程调度器；JDK `ScheduledThreadPoolExecutor` 语义下 periodic 任务体任何未捕获异常静默取消全部后续调度，无日志、无事件、无恢复。
- **触发时序**: 理论风险，未具体化（按纪律降级并标注）：当前任务体内的现实抛出面已逐一排除——`cache()` 双检锁不抛、ConcurrentHashMap 弱一致迭代无 CME、`refreshPool.execute` 仅在 close 竞速窗口抛 REE（此时调度器已被 close 一并 shutdown，取消无行为影响）。缺口在防御缺失：未来对 scanAndDispatch 的任何增量（补 metrics、加日志格式化、Entry 字段扩展）引入 RuntimeException 即**永久杀死刷新机制**——所有缓存条目从此永不刷新、脏值无限期驻留，且因调度静默死亡而无任何可观测信号。与 C4-002 的"delegate 挂死→队列堆积"为不同失效面（彼处是任务永不完成，此处是调度器整体死亡）。
- **现状**: 模块已自建并文档化相反不变式——`TaskManager.java:442-443` 明文注释 "The WHOLE iteration is guarded: per ScheduledExecutorService semantics a single uncaught exception from scheduleAtFixedRate permanently kills"，且第 1 轮核实的 5 处 fixed-rate 循环 + JC periodic + JDBC poll 全部执行了该守卫；本处是全部 9 个周期任务中唯一无任务体级 catch 的一处。
- **风险**: 长时运行下一次未来编辑即可引入"刷新静默死亡"故障模式；届时脏读 DB 旧值与 C4-002 的堆积路径叠加，且无告警面。
- **建议**: 任务体级 try/catch 全包容 + WARN（对齐 TaskManager heartbeat 先例），或提取共享的 `periodicGuard(runnable)` 工具统一包装（NopStreamThreadFactory 同包，一处修复九处受益）。
- **失效面枚举**: 全部周期任务体逐一核对（fixed-rate 5 处第 1 轮已验 + 本轮补 fixedDelay/方法引用形态）——heartbeat（TM，全 catch）、detectFailures（JC，:1582 全 catch）、periodicCheckpoint（JC，catch + incrementTriggerFailures）、barrier-injector（TaskCheckpointWiring，catch + incrementTriggerFailures）、metrics report（全 catch）、governanceSweep（全 catch，R5 CC-07）、JDBC poll（全 catch）、**scanAndDispatch（本发现，无 catch）**、`ProcessingTimeServiceDriver` tick 循环（裸线程自管循环非 ScheduledExecutor periodic，异常即线程退出但 finally shutdown 对称且任务生命周期与 invokable 绑定，不属本族）。
- **信心水平**: 确定（守卫缺失是事实）；危害实现依赖未来引入抛出点——**理论风险，未具体化**
- **误报排除**: 非"平台设施自身实现不重审"——ScheduledExecutor 语义已知，缺陷在本模块未按自身不变式设防；非第 1 轮重复——第 1 轮表 #3 以"fixedDelay 非重叠扫描"判定通过，仅核对了堆积面，异常致死面（F9 步骤 5 的另一半）未核对，本轮补齐。
- **复核状态**: 未复核

# C4 线程池与执行器治理 — nop-stream-core-runtime（第 3 轮深挖追加）

> 审计维度：C4（线程池与执行器治理）。指令来源：`ai-dev/skills/concurrency-resource-audit-prompt.md` 共享提示词前缀（{MODULE}=nop-stream-core-runtime）+ C4 正文。
> 盲审纪律：本轮仅读取本目录第 1 轮 `C4-thread-pools.md`、第 2 轮 `C4-round2.md` 与 live code；未读取 `ai-dev/audits/` 下其他审计目录、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`。
> 审计日期：2026-10-14。序号接续第 2 轮（C4-001 ~ C4-011）。

## 深挖第 3 轮追加

### 第 3 轮盲区扫描覆盖说明（负面清点）

- **执行器/调度点全量重扫**：`Executors.new*` / `new ThreadPoolExecutor` / `new Thread(` / `DefaultScheduledExecutor` / `ScheduledThreadPoolExecutor` / ForkJoinPool / commonPool / 并行流 / `Timer` / 虚拟线程——与第 1 轮 23 行清单及第 2 轮两项核销完全吻合，**无漏登记的池**。补充盘点两个第 1/2 轮未显式核销的面：`JobCoordinator` HA elector（`leaderElector`，JobCoordinator.java:163/652）为平台 IoC bean（`io.nop.cluster.elector`，stop 注释 :737-738 明示"elector bean 本身不由 coordinator 停止"），无自有线程池，属平台设施豁免；`OpsJobManager.governanceSweep` 单线程跨全部 job 共享——`sweepOnce` 纯内存（历史修剪 + terminal 记录清扫，无 I/O）且方法 synchronized，慢清扫不成立，无隔离缺陷。
- **C4-009 同族「构造后注册前抛出」窗口逐宿主核查**：`TaskManager.start`（:325-358）为 register-first 序——`registerNode` 抛出时四池均未提交过任务（JDK 池线程惰性创建，0 线程），无泄漏；调用方 RpcDistributedExecutor（teardown 纪律）/EmbeddedDistributedExecutor（try/finally）/TaskManagerMain（单生命周期）均已覆盖。`PollingJdbcMessageService.subscribe`（:170-177）为 register-before-start 序——`sub.start()`（含 SELECT MAX(id) DB 查询，可抛）失败时订阅已在 map 但 poll 任务从未调度，无线程泄漏（仅 map 内死条目，topic 数有界）。`AlertService.fromProperties`（AlertService.java:67-87）单 webhook URL、构造器内 `execute(drainLoop)` 为最后一句（WebhookAlertChannel.java:110-112），构造后无抛出窗口；OpsJobManager 共享 AlertService 默认仅 LoggingAlertChannel（无线程），webhook 通道归 embedder 所有。`StreamOpsHttpServer.start` 失败窗口：executor 线程惰性，start 抛出时 0 线程。`startPeriodicCheckpoints`（JobCoordinator.java:1296-1335）有 `periodicCheckpointsStarted` 幂等守卫（synchronized 内），无二次创建泄漏。`OpsJobManager.submit` 中 `new CheckpointCoordinator`（:146）与 `coordinator.start()` 之间的窗口：`restoreFromCheckpoint` 已 catch（:167-175），`computeFingerprint` 抛出孤儿化 CheckpointCoordinator，但首触发前 timeoutScheduler 0 线程（惰性），无实质泄漏面。**结论：C4-009 之外无新的未设防窗口。**
- **第 2 轮排除项复核（ProcessingTimeServiceDriver 静默死亡）**：tick 循环（ProcessingTimeServiceDriver.java:118-135）的两个 due 判读均为单 volatile 读（TaskProcessingTimeService.java:101-103、HeapInternalTimerService.java:221-223，两类 javadoc 均明示 "never touches the timer map"/"Safe to call from the scheduler thread"）；`mailbox.put` 抛出仅发生在任务自身失败/关闭竞速（此时 supervision 重启或任务终止，driver 生命周期随之终结）——第 2 轮排除成立，维持非发现。
- **拒绝策略/REE 兜底同族全量清扫**（新增发现 C4-012 的失效面来源）：taskExecutor Abort+typed（:247/:595/:781）、terminalReport always-inline（:259 + :1107-1113 belt-and-braces）、commitExecutor 计数+条件 inline（:310-316）、persistExecutor REE catch inline（CheckpointCoordinator.java:643/:666）、commitFanoutExecutor 裸 CallerRunsPolicy（:1092）、retentionExecutor REE catch WARN+复位（RetentionCleaner.java:134/:273）——见 C4-012 失效面枚举。
- **CheckpointCoordinator 三处惰性池创建的竞态分析**（新增发现 C4-013）：persist/commitFanout/retention 三个 getter 的调用方线程上下文与 happens-before 链逐一核实，见 C4-013。

---

### [nop-stream-core-runtime-C4-012] stop() 竞速窗口静默丢弃 2PC commit 通知：同族三档拒绝策略不一致，两处自身声明的"不丢"不变式在 shutdown 态失真 + 饱和度指标被污染

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:292,310-316,367,1012`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/TaskManagerMain.java:209-216`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:1092,1593-1608`
- **证据片段**:
  ```java
  // TaskManager.java:292 —— 自我声明的不变式（shutdown 态下为假）
  //   CallerRuns applies natural backpressure instead and never loses a notification.
  // TaskManager.java:313-316 —— 拒绝策略：WARN 声称 caller-run，isShutdown 时实际丢弃
  nodeMetrics.commitCallerRun();
  LOG.warn("Commit executor queue saturated ... running the commit on the caller thread ...", ...);
  if (!executor.isShutdown()) { r.run(); }
  // TaskManager.java:367 —— stop() 第二步即关 commit 提交通道
  commitExecutor.shutdownNow();
  // TaskManagerMain.java:209→216 —— 派发线程存活到 messageService.close()，窗口敞开
  taskManager.stop();  ...  messageService.close();
  // CheckpointCoordinator.java:1092 —— 同族兄弟用裸 CallerRunsPolicy（最弱档，无计数无日志）
  new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
  ```
- **严重程度**: P2
- **并发场景**: TaskManager 停机序列（`stop()` 调用方：Main shutdown hook / RpcDistributedExecutor teardown / Embedded finally）与仍存活的 `polling-jdbc-msg` 控制消息派发线程交叠；coordinator 侧则为 `CheckpointCoordinator.shutdown()`（stopJob 路径）与其完成回调线程的竞速。
- **触发时序**: T1 `TaskManagerMain.shutdown():209` → `TaskManager.stop():367` 执行 `commitExecutor.shutdownNow()`；messageService 到 `:216` 才关闭，`polling-jdbc-msg` 派发线程在此窗口内仍活着（窗口含 stop() 内两段 awaitTermination 排水，秒级宽）。T2 派发线程处理排队中的 `notifyCheckpointComplete` RPC → fencing 校验通过（stop 不重置 `currentFencingEpoch`）→ `:1012 commitExecutor.execute(...)` → 拒绝策略 `:310-316`：`commitCallerRun` 计数 +1（被记成队列饱和，污染 `nop.stream.task.commitCallerRuns.total` 的语义）、WARN 文本声称 "running the commit on the caller thread"（并未执行）、`executor.isShutdown()` 为真 → **任务静默丢弃**。T3 该 TM 对此 epoch 的 2PC `finishCommit` 永不执行 → coordinator 侧等待该 epoch 提交直至 checkpoint 超时 → abort fan-out（`finishCommit(false)` 保留 prepared 事务，交由后续 epoch subsume——设计内兜底）；若 coordinator 恰在其自身 `shutdown()` 窗口（CheckpointCoordinator.java:1596-1608 的 grace window），这个 abort fan-out 落进 `commitFanoutExecutor` 的裸 `CallerRunsPolicy`（:1092，JDK 语义 isShutdown 后无日志无计数直接丢弃）→ TM 侧 prepared 事务收不到任何通知，滞留到下一个 coordinator 实例恢复/subsume。
- **现状**: 同一引擎内三个 commit/通知型 executor 的 shutdown 竞速行为分三档：terminalReportExecutor **always-inline**（:254-259，注释明言 "CallerRunsPolicy would silently drop once isShutdown()"，且 `submitTerminalReport:1107-1113` 双保险）；commitExecutor **计数+WARN 但丢弃**；CheckpointCoordinator.commitFanoutExecutor **裸 CallerRunsPolicy 静默丢弃**。commitExecutor 的 javadoc（:292 "never loses a notification"，且选择记录明言丢 commit 对 bounded job 尾提交不可接受）与 CheckpointCoordinator.shutdown 的 javadoc（:1593-1594 "a shutdown racing a completion still delivers the epoch's commits"——仅对 pre-shutdown 已入队任务为真，grace window 内新提交的 fan-out 直接丢弃）在 shutdown 态均为假。
- **风险**: ① bounded job 尾提交在停机窗口被丢（恰是其选择记录声明要避免的失效），靠 abort→subsume/重提交兜底，DRAIN 停机可能等满 `terminationCheckpointTimeoutMs` 后报失败；② prepared 事务滞留窗口拉长（滞留期间持有 sink 侧锁/资源）；③ `commitCallerRuns` 指标混入停机丢弃计数，饱和度不可观测；④ 两处 WARN/javadoc 失真误导运维与后续维护。窗口秒级真实存在、可具体化，故 P2（有设计内兜底故不上 P1）。
- **建议**: ① 将 CC-17 always-inline 先例推广：`saturatedCommitQueuePolicy` 去掉 `isShutdown` 条件无条件 `r.run()`（CallerRuns 背压语义在 shutdown 态同样成立，执行方仍是派发线程、代价一次性）；`CheckpointCoordinator.commitFanoutExecutor` 换用同一 always-inline handler；② 或 stop() 中仿 terminal-report 排水：先 `taskExecutor` await 完成后给 commitExecutor `shutdown()` + 短 await 再 `shutdownNow()`；③ 同步修正 ：292/:1593 两处 javadoc 与 ：312 WARN 文本，`commitCallerRun` 计数仅在确实 inline 执行时递增。
- **失效面枚举**: (a) `TaskManager.commitExecutor`（本发现，计数+WARN 但丢弃）；(b) `TaskManager.terminalReportExecutor`——always-inline + belt-and-braces（:259/:1107-1113），**已防护（修复先例）**；(c) `CheckpointCoordinator.commitFanoutExecutor`——裸 CallerRunsPolicy（:1092），shutdown 竞速静默丢弃且无观测，同族最弱档，随本发现一并修复；(d) `CheckpointCoordinator.persistExecutor`——REE catch → inline 失败副作用（:643/:666），**已防护**；(e) `RetentionCleaner` 两处 REE catch（:134 WARN+CAS 复位、:273 inline best-effort discard），**已防护**；(f) `TaskManager.taskExecutor` AbortPolicy → typed `reportAssignmentFailure`（:247/:595/:781），响亮失败，**已防护**；(g) `JobCoordinator.barrierFanoutExecutors` 默认 Abort——shutdown 后提交的 REE 传播到调用方（periodic 任务体有 catch、手动触发转 HTTP 500），被 C4-005 已登记的队列治理面覆盖，无新增。宿主顺序依赖：TaskManagerMain（tm.stop:209 → messageService.close:216，**窗口敞开**）、RpcDistributedExecutor（服务先停、tm.stop:545 最后，**窗口闭合**）、EmbeddedDistributedExecutor（进程内调用，窗口取决于 finally 序，同形状）。
- **信心水平**: 确定（JDK CallerRunsPolicy 的 isShutdown 分支语义明确；时序各步均有行号；stop→close 顺序已核实）
- **误报排除**: 非"平台设施不重审"——缺陷在本模块自写的拒绝策略与停机排序，非 JDK 实现；非第 1 轮重复——第 1 轮表 #6/#8 仅核对"队列有界 + CallerRuns 可观测 + stop 存在"，未核对拒绝策略在 `isShutdown` 分支的行为与 javadoc 不变式的一致性；非"有界声明存在"豁免——丢弃发生在我方 handler 的显式条件分支，不是队列容量的已声明边界。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C4-013] CheckpointCoordinator.persistExecutor 无同步惰性初始化（非 volatile check-then-act）：当前全部路径经论证安全，但安全性论证未覆盖第三个脱监视器调用方（shutdown 终局清理），防御缺口 + 注释契约不完整

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:216,1078-1083,1576-1581`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/RetentionCleaner.java:249-256,261`
- **证据片段**:
  ```java
  // CheckpointCoordinator.java:216 —— 非 volatile 字段
  private ExecutorService persistExecutor;
  // :1078-1083 —— 无同步 check-then-act（对比 :1491 retention getter 有 monitor-safe 论证注释）
  ExecutorService getOrCreatePersistExecutor() {
      if (persistExecutor == null) {
          persistExecutor = createPersistExecutor();
      }
      return persistExecutor;
  }
  // :1576 —— shutdown() 终局清理：脱监视器线程上的第三个 off-monitor 调用方
  retentionCleaner.cleanupOldCheckpoints();   // → gcSegmentsForCheckpoint → :261 getOrCreatePersistExecutor()
  ```
- **严重程度**: P3
- **并发场景**: 持有 coordinator 监视器的 ACK 线程（completePendingCheckpoint:614 synchronized，:640/:663 创建）、retention 执行器线程（RetentionCleaner.gcSegmentsForCheckpoint:261，无监视器）、stopJob 调用方线程执行 `shutdown()` 终局清理（:1576，无监视器）。
- **触发时序**: 理论风险，未具体化（当前 live code 已逐一验证不可达，论证链如下）：① retention 线程路径有 submit 建立的 happens-before（scheduleRetentionCleanup 仅由 onCompletePersistSuccess:988 调用，其时 persist executor 必已在 :640/:663 于同一 ACK 线程先行创建，两段 submit 传递可见性），且 gc 前置 `checkpointSegments.remove` 为空即早退（旧实例遗留行不进入本实例 map）；② :1002/:1059/:1137 三个 commitFanout 调用点均经 `executePersistAsync` 的 `synchronized(this)` 包装（:716/:738/:764/:782）或 `abortPendingCheckpoint:1095` 进入，创建被监视器串行化；③ :1576 的 shutdown 终局清理若要成为首建者，需同时满足"存储中存在可删旧行 + 本实例注册过 segments"，而这蕴含此前已有完成（即已创建）。缺口在于：RetainerCleaner.java:249-256 的安全性 javadoc 只论证了 retention-task 路径，**:1576 这个 off-monitor 调用方不在任何书面论证覆盖内**，其当前安全性仅由"gc 蕴含先前完成"这一隐式不变式支撑（非 volatile 字段读无任何同步屏障）；未来任何新增 off-monitor 调用方或"先调度 retention 后 persist"的路径变更，都会立即把缺口变成真实的双创建竞态（两池皆建，败者因 shutdown() 只关字段引用而永久泄漏 idle daemon 池）。
- **现状**: 惰性初始化正确性完全依赖散布在两个类 javadoc 里的隐式不变式；同文件 :1491 的 retention getter 写明了 monitor-safe 论证，persist getter 无对应说明，注释契约不完整。
- **风险**: 当前无直接危害（daemon 线程、无任务时不建线程）；是防回归型缺口——违反该模块自建的"惰性创建必须写明同步论证"惯例，一次无感知的调用方新增即引入永久线程池泄漏。
- **建议**: ① 最小修复：getter 加 `synchronized`（或在字段创建后置 `volatile` + synchronized 包装创建），并补 :1078 注释说明第三调用方（:1576）的安全性依据；② 或消除惰性：timeoutScheduler 已是构造器急切创建先例，persist/commitFanout 随构造器一并创建、随 `shutdown()` 一并关闭（消除全部论证负担，代价是从未完成 checkpoint 的实例多 1-2 个 idle daemon 线程）；③ 为该 getter 增加单线程约束断言（debug 断言持锁）防未来回归。
- **失效面枚举**: (a) `getOrCreatePersistExecutor`（本发现）；(b) `getOrCreateCommitFanoutExecutor`（:1086-1093）——全部三个调用点均在监视器内（见触发时序②），**已防护**；(c) `getOrCreateRetentionExecutor`（:1490-1494）——注释声明 monitor-safe 且全部调用点（RetentionCleaner.scheduleRetentionCleanup，仅由持监视器的 onCompletePersistSuccess 调用）核实一致，**已防护**；(d) 同族形态对照 `CachingTableLookup.refreshPool`（:193）——在 close() 守卫下创建、非并发惰性路径，**无此缺陷**；模块内无其他非同步惰性建池点（第 3 轮全量重扫确认）。
- **信心水平**: 确定（守卫与注释缺口是事实）；双创建竞态本体**理论风险，未具体化（当前交错已验证不可达）**
- **误报排除**: 非"平台设施自身实现不重审"的错位——对象是本模块自写 getter；非 C4-011 重复——C4-011 是周期任务体缺异常守卫（F9 面），本条是惰性初始化缺同步论证（双创建泄漏面），代码位置与失效机制不同；非 P2 竞态降档错误——按纪律"写不出具体时序降级并标注"，本条更进一步：当前交错经论证不可达，属防回归缺口，比照第 2 轮 C4-011 的 P3 先例判级。
- **复核状态**: 未复核

---

# C4 线程池与执行器治理 — 维度复核（盲审解除轮）

> 复核角色：独立复核子代理（与第 1-3 轮初审/深挖无关）。
> 复核日期：2026-10-14。HEAD：`4d522b00ae`。
> 方法：逐条重走 live code——13 条发现的全部锚点行号（含 `TaskExecutor.java:77/116-117/372-377`、`StreamExecutionEnvironment.java:543/574/597`、`GraphModelCheckpointExecutor.java:275/391`、`CachingTableLookup.java:193/205-227/239`、`StreamOpsHttpServer.java:127/179`、`PollingJdbcMessageService.java:96-99/293`、`JobCoordinator.java:2932-2956/723-736/983/1583`、`OpsJobManager.java:129-130/188-194/421-435`、`WebhookAlertChannel.java:67/180`、`TaskManager.java:292-316/367/1012`、`CheckpointCoordinator.java:321-322/1078-1092/1576-1608`、`TaskManagerMain.java:209-216`）及调用方/关闭路径/拒绝策略逐一直读核对；解除盲审后对照历史审计（`2026-09-26-0546-deep-audit-nop-stream-quality/04`、`2026-09-30-0530-...-r4/04`、`2026-10-05-0523-...-r5/04` 及其维度复核、`2026-10-06-0522-...-r6/04` 及其维度复核）与 `git log` 确定现状归属，修复 SHA 经 git log 按文件核证（e600de4058=plan 2314、24b935140d=plan 369 P4、8355aea426=plan 2326、547be576f8=plan 2435 C1）。未修改本文既有发现正文；未运行构建/测试。

## 维度复核结论

**C4-001 — 保留，P1 维持。现状：已知未修复（r5 CC-05，2026-10-05 r5 轮同发现，其维度复核维持保留）。**
锚点全部直读证实：固定池=核数（`TaskExecutor.java:77`）、无界队列无容量守卫（`:116-117`）、三个入口默认构造（`StreamExecutionEnvironment.java:543`、`GraphModelCheckpointExecutor.java:275/391`）、无超时 `awaitCompletion()`（`StreamExecutionEnvironment.java:574`）、finally 仅 graceful（`:597`）；`ResultPartition` 的 pool+无物化与无 pool+无物化两分支 `queue.put` 阻塞在位，`BufferPool.close` 只唤醒生产者。失效面 (d) 的 runtime 守卫（capacitySemaphore）由 plan 2435（547be576f8）补齐后 LOCAL 侧无守卫的不对称更突出。定级注明：与 r5 复核的 P2 存在分歧，本轮按本审计提示词校准（"可具体化触发链"判 P1，后果为宿主线程静默永久挂死、无任何诊断信号）维持 P1；若采纳 r5 的"拓扑规模配置前提"加权可回落 P2，修复优先级不因分歧降低。自 r5 起无任何修复提交触及 `TaskExecutor.java`/`StreamExecutionEnvironment.java` 相关面（仅 plan 2313 路由整改），判定未修复。

**C4-002 — 保留，P1 维持。现状：新发现。**
机制直读证实：`:193` 无界 fixedThreadPool、`:222-224` 每轮为全部老化 key 无条件入队（无 in-flight 去重）、`:239` 挂死点、`:247` catch(Exception) 不构成挂死防护；类 javadoc"保旧值+下轮重试"自愈以失败会抛异常为前提，挂死下失效。范围注记：main 源码当前无生产构造点（仅测试引用；类为 WI11/plan 2338/52599aba5c 交付的库类），触发以宿主接线为前提——不改变缺陷本体（类契约即预期被宿主使用），in-flight 去重/有界队列修复建议维持。

**C4-003 — 降级 P2→P3。现状：已知未修复（r6 CC-05，2026-10-06 r6 轮同发现，其维度复核维持 P3）。**
机制证实（`:127` setExecutor 引用未保存、`:179-186` stop 仅 `httpServer.stop(0)`），与 r6 CC-05 完全同发现；r6 复核已裁定 P3（至多 4 个 daemon idle 线程、不阻 JVM 退出、两个 Main 单生命周期进程）。本条自述"实际影响有限"与 P2 定级自相矛盾，对齐 r6 复核改判 P3；修复建议（保存引用 + shutdown/await/shutdownNow 三段式 + 有界队列）与 r6 一致。自 r6 起该文件仅重构/功能提交（plan 2416/2340/2333），未修复。

**C4-004 — 保留，P2。现状：新发现。**
机制证实：`:96-99` 全部订阅共享 2 线程、`:293` 每订阅 fixedDelay 内联 poll、`:310-344` 查询无 LIMIT 全量载入逐条内联投递；控制/数据面 topic（`nop-stream.rpc.*` / `nop-stream.data.*`，`StreamControlRpcTopics`）共用该池成立。owner doc（plan 2328 WI1）将 `PollingJdbcMessageService` 随 Main 入口列为 main-scope 生产形态（`ClusterLaunchConfig` 头注释"test infrastructure"已过时，属文档漂移注记，不影响判定）。失效面为时效性而非正确性（JDBC cursor 不丢消息），P2 恰当；与 tm-commit/jc-barrier-fanout 的隔离先例对照成立。

**C4-005 — 保留，P2。现状：新发现（fanout 池本体由 plan 2435/547be576f8 于 2026-10-11 新引入，队列治理缺口此前未登记）。**
机制证实：`fanoutExecutorFor:2932-2941`（newSingleThreadExecutor 默认无界队列）、`:983`/`:2945` 两个提交点任务体仅 catch(Exception)、`stop():723-736` 三段排水有界。触发以传输 send 挂死为前提（JDBC transport 本地 INSERT 不挂、网络型可挂）；队列增速 1 任务/checkpointInterval 缓慢有界，主害为该节点 barrier 静默失效、靠 checkpoint 超时 abort 兜底发现——P2 恰当；与 tm-commit（有界 1000+CallerRuns+计数）、tm-terminal-report（有界 64+inline）的治理标准不一致成立。

**C4-006 — 保留，P2。现状：新发现。**
机制证实：`shutdownLocalExecution:597-605` 仅 graceful shutdown + closeBufferPool（`BufferPool.close:126-131` 只唤醒 acquire 阻塞的生产者）+ coordinator 注销；`SubtaskTask.cancel:148` 在 LOCAL env 路径零调用方（grep 证实，唯一调用配方在 runtime `SupervisionLoop.cancelTaskWithMailbox:711-716`）；消费者任务 50ms 轮询循环无终态出口，`execute()` 返回后任务线程持连接自旋至 JVM 退出。中断是嵌入宿主唯一取消手段，F8 清理不对称成立，P2 恰当。

**C4-007 — 保留，P2。现状：新发现（同类的 r5 CC-06 drainLoop 异常致死已由 e600de4058/plan 2314 修复——本轮核实 drainLoop 已带 "R5 CC-06: per-event exception containment" 实现；`delivered` 累积面此前未登记）。**
机制证实：`:67` 生产构造即创建、`:180` 唯一 add 点、全文件无 remove/clear（grep 证实）、close() 不清理。增速=告警事件频率（健康作业近零、故障频发环境持续增长）+ CopyOnWriteArrayList 每次投递全量复制写放大，P2 恰当。

**C4-008 — 保留，P3。现状：新发现。**
两处锚点直读证实（`ProcessingTimeServiceDriver.java:93` 裸名 "processing-time-driver"、`PollingJdbcMessageService.java:97` 裸名 "polling-jdbc-msg"）；`NopStreamThreadFactory` javadoc 契约（"prefix identifies the owning component and instance"）证实相悖。纯可观测性缺陷，P3 恰当。

**C4-009 — 保留，P1。现状：新发现。**
结构四要素全部直读证实：submit 的 `start()/assignTasks()/startPeriodicCheckpoints() → jobs.put` 序列无 try/finally 补偿（`OpsJobManager.java:188-194`）；去重仅 `jobs.containsKey`（`:129-130`）；`close():421-435` 只遍历 jobs.values()；`detectFailures:1583-1584` 全包容 catch 保证孤儿永生。抛出点证实：`AssignmentPlanner:141-148` materialized 映射缺失 typed throw（用户 plan 少一个映射即触发）、registry 写、RPC 不可达（证据微瑕：`StreamOpsHttpServer:415-419` 捕获 StreamException 转 409/400 而非发现所述 500——仅非 StreamException 才未捕获，不改变"无补偿"机制）。对照 `RpcDistributedExecutor.teardownOnStartupFailure` 先例的同族缺修成立；每次失败泄漏永久 tick 的 `jc-failure-detector` 线程 + 构造器急切创建的 CheckpointCoordinator 池组（`:321-322`）+ stale registry 行，同 jobId 重试产生双活协调器——按校准"错误路径资源泄漏 + 可具体化触发链"判 P1 恰当。

**C4-010 — 保留，P3。现状：新发现（对 C4-008 的方法性扩展，机制不同：per-factory 计数器跨实例碰撞 vs 固定名无标识，非重复登记）。**
锚点证实（`TaskExecutor.java:116-117` 固定前缀、`NopStreamThreadFactory.java:29` per-factory `AtomicLong index`）；与工厂 javadoc 契约相悖成立；多 LOCAL 实例并发（多次 `env.execute()`、runLocal 与 savepoint 并行）是嵌入形态受支持用法。纯可观测性，P3 恰当。

**C4-011 — 保留，P3（理论风险，未具体化——发现已自带标注，符合纪律）。**
锚点证实（`:205-206` 方法引用直挂 fixedDelay、`:211-227` 任务体无 catch）；现实抛出面排除论证（cache() 双检锁不抛、CHM 弱一致迭代无 CME、REE 仅 close 竞速窗口）复核成立。同族 r5 CC-07（governanceSweep 同形态）已由 e600de4058 修复，本处属"修实例不修类别"（F3）的漏修兄弟点；守卫缺失事实成立、危害依赖未来引入抛出点，P3 恰当。

**C4-012 — 保留，P2。现状：新发现（plan 369/24b935140d 的 commitExecutor 治理与 plan 2326/8355aea426 引入的 commitFanoutExecutor 在 shutdown 态的残余，历史审计未登记）。**
机制证实：`saturatedCommitQueuePolicy:310-316` 的 `if (!executor.isShutdown()) r.run()` 在 shutdown 态静默丢弃，`commitCallerRun` 计数与 WARN 文本（"running the commit on the caller thread"）双双失真，`:292-305` 选择记录 "never loses a notification" 在 shutdown 态为假；`TaskManagerMain:209→216` 的 stop→messageService.close 窗口敞开（间隔含两段 awaitTermination 排水）；`TaskManager.java:1012` 提交点证实；`CheckpointCoordinator:1092` 裸 CallerRunsPolicy（JDK 语义 isShutdown 后无日志无计数直接丢弃）与 `:1593-1600` grace window javadoc 的过度承诺证实。有 abort→subsume 设计内兜底故不上 P1；同族三档不一致（terminal always-inline / commit 计数但丢 / fanout 裸丢）对照成立，P2 恰当。

**C4-013 — 保留，P3（理论风险，未具体化——发现已自带"当前交错已验证不可达"标注，符合纪律）。**
锚点证实：`:216` 非 volatile 字段、`:1078-1083` 无同步 check-then-act（对照 `:1484-1490` retention getter 有 monitor-safe 论证）、`RetentionCleaner:261` 与 `CheckpointCoordinator:1576` 两个 off-monitor 调用方。不可达论证链（retention 路径 submit 建 happens-before、commitFanout 三调用点在监视器内、gc 蕴含先前完成）复核成立，属防回归缺口 + 注释契约不完整，比照 C4-011 的 P3 先例恰当。

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状标注 |
|---|---|---|---|---|
| C4-001 | P1 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/TaskExecutor.java:77,116-117,372-377（+environment/StreamExecutionEnvironment.java:543,574,597） | LOCAL 执行固定池=CPU 核数+长驻任务阻塞有界输出队列：拓扑超核数后池内互等死锁，无守卫/超时/恢复，宿主线程永久挂死 | 已知未修复（r5 CC-05） |
| C4-002 | P1 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/lookup/CachingTableLookup.java:193,222-224,239 | 后台刷新池无界队列且无 in-flight 去重：delegate 挂死时每扫描轮全量重入队→无界增长（OOM 候选）+降级 DB 重复探测 | 新发现 |
| C4-003 | P3（降级） | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/StreamOpsHttpServer.java:127,179-186 | stop() 不关闭请求 executor（每轮 start/stop 泄漏至多 4 个 daemon idle 线程）+请求队列无界 | 已知未修复（r6 CC-05） |
| C4-004 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java:96-99,293,310-344 | JDBC transport 控制面 RPC 与数据面投递共享 2 线程 poller+单轮 poll 无 LIMIT：慢数据面独占线程→控制消息延迟/恢复风暴放大 | 新发现 |
| C4-005 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:2932-2941,983,2945（stop 排水 :723-736） | per-node barrier fanout 单线程 executor 无界队列：节点 RPC 挂死时队列单调增长、该节点 barrier 静默失效靠 checkpoint 超时兜底 | 新发现（池本体 plan 2435/547be576f8 新引入） |
| C4-006 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java:574,597-605（+task/TaskExecutor.java:372-377） | LOCAL 取消路径不对称：中断后 finally 仅 graceful shutdown 不 cancel 子任务→消费者任务线程持连接无限自旋 | 新发现 |
| C4-007 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/alert/WebhookAlertChannel.java:67,180 | delivered 只增不删：生产告警事件无限累积 + CopyOnWriteArrayList 投递写放大 | 新发现 |
| C4-008 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ProcessingTimeServiceDriver.java:93；nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java:97 | 两处线程命名违反模块自身"前缀标识组件与实例"契约，多实例/多 JVM 不可区分 | 新发现 |
| C4-009 | P1 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/OpsJobManager.java:129-133,188-194,421-435（+coordinator/AssignmentPlanner.java:141-148、checkpoint/CheckpointCoordinator.java:321-322） | submit 失败窗口孤儿 coordinator：活线程池泄漏+治理路径不可见+同 jobId 重提交双活协调器 | 新发现 |
| C4-010 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/TaskExecutor.java:116-117（+util/NopStreamThreadFactory.java:17-18,29） | 线程工厂静态前缀+per-factory 计数器：同 JVM 多 LOCAL 实例线程名碰撞，threaddump 不可归属 | 新发现 |
| C4-011 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/lookup/CachingTableLookup.java:205-206,211-227 | 扫描调度任务体无全包容 catch：违反模块"整迭代守卫"不变式（F9 潜伏面；理论风险，未具体化） | 新发现（同族 r5 CC-07 已修复 e600de4058，此处为漏修兄弟点） |
| C4-012 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:292,310-316,367,1012（+checkpoint/CheckpointCoordinator.java:1092,1593-1608、launch/TaskManagerMain.java:209-216） | stop() 竞速窗口静默丢弃 2PC commit 通知：拒绝策略 isShutdown 分支丢弃但计数/日志/javadoc 声称 caller-run，同族三档不一致 | 新发现（plan 369/24b935140d 与 plan 2326/8355aea426 修复引入面的残余） |
| C4-013 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:216,1078-1083,1576（+checkpoint/RetentionCleaner.java:261） | persistExecutor 无同步惰性初始化：当前路径经论证安全，但第三个脱监视器调用方不在书面论证内（防回归缺口；理论风险，未具体化） | 新发现 |

**复核统计**：13 条 = 保留 13（其中 12 条维持原级、1 条降级：C4-003 P2→P3）+ 驳回 0。现状归属：新发现 11、已知未修复 2（C4-001→r5 CC-05、C4-003→r6 CC-05）、已修复 0。P1 最终保留 3 条（C4-001、C4-002、C4-009）。两处证据微瑕已注明、不改判定（C4-009 的 HTTP 500→实为 StreamException 409/400 分流；C4-004 相关的 `ClusterLaunchConfig` "test infrastructure" 注释与 owner doc 生产定位漂移）。C4-001 与 r5 复核的 P1/P2 定级分歧已注明依据与回落条件。
