# C1 共享可变状态与可见性 — nop-stream-core-runtime（第 1 轮初审）

- 审计对象：`nop-stream/nop-stream-core/src/main/java`（439 files / 57,135 lines）、`nop-stream/nop-stream-runtime/src/main/java`（132 files / 29,922 lines）。`_` 前缀生成文件不在发现对象内。
- 审计维度：C1 共享可变状态与可见性（五类载体盘点 + happens-before 验证 + 发布安全 + 检查-行动复合操作 + long/double 原子性 + F3 同族清扫）。
- 盲审纪律：未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；只审计 live code。

## 检查范围（实际读过的关键文件/包）

**core（io.nop.stream.core）**
- `datastream/`：`SideOutputRegistry`（全文）、`SideOutputDataStream`（全文）、`WindowedStreamImpl`（240-340）、`KeyedStreamImpl.FieldAggregationReducer`（275-391）
- `source/coordinator/`：`SourceCoordinatorRegistry`（全文）、`LocalSourceCoordinator`（全文，含 ReaderChannel）
- `operators/`：`SourceReaderOperator`（60-400）、`StreamSinkOperator`（全文）、`HeapInternalTimerService`（60-135）
- `common/functions/sink/TwoPhaseCommitSinkFunction`（全文）；`common/buffer/PerKeyOrderedBuffer`（30-110）；`common/state/simple/SimpleKeyedStateStore`（全文）；`common/typeinfo/BasicTypeInfo`（全文）
- `execution/`：`TaskMailbox`（全文）、`ProcessingTimeServiceDriver`（全文）、`TaskProcessingTimeService`（全文）、`CheckpointBarrierTracker`（全文）、`ResultPartition`（全文）、`buffer/BufferPool`（全文）、`InputGate`（字段 + 460-600 + 1290-1341）、`materialization/InMemoryMaterializationPoint`（全文）、`task/StreamTaskInvokable`（字段清单 + 关键段）、`task/TaskExecutor`（60-180）、`transport/StreamElementCodec`（1-110）
- `metrics/`：`StreamMetricsRegistries`（全文）、`MicrometerStreamTaskMetrics`（全文）
- `environment/StreamExecutionEnvironment`（195-235，静态工厂路径）；connector 佐证（race 受害者，不在发现对象内）：`FileSplitEnumerator`（全文）、`BatchLoaderSplitEnumerator`、`MessageTopicSplitEnumerator`、`DebeziumTableSplitEnumerator` 字段/赋值逻辑

**runtime（io.nop.stream.runtime）**
- `taskmanager/`：`TaskManager`（全文 1230 行）、`RunningTask`（全文）
- `coordinator/JobCoordinator`（字段全景 100-540、liveness/COMPLETED 段 1090-1264、start/HA 段 570-670）
- `checkpoint/`：`CheckpointCoordinator`（160-300、610-760、1060-1180、1480-1500）、`RetentionCleaner`（全文）、`PendingCheckpoint`（基线核对，synchronized 面）、`storage/JdbcCheckpointStorage`（nextSid 段）、`metrics/CheckpointMetrics`（字段面）
- `transport/`：`RemoteResultPartition`（全文）、`RemoteInputChannel`（字段面）、`ChannelQueueGauges`（全文）、`DbBacklogGauges`（全文）
- `cluster/`：`InMemoryClusterRegistry`（全文）、`JdbcClusterRegistry`（280-340 + removeTaskAssignment）、`JdbcLeaderElector`（字段面）
- `health/JobHealthStateMachine`（全文）；`ops/OpsJobManager`（字段 + 方法面）、`ops/StreamOpsHttpServer`（executor 面）；`launch/PollingJdbcMessageService`（130-400）；`rpc/StreamControlRpcProxyFactory`（40-110）；`execution/SupervisionLoop`（字段/同步面）、`execution/GraphModelCheckpointExecutor`（字段面）；`metrics/EngineMetrics`、`TaskNodeMetrics`、`RuntimeMetrics`（全文）；`checkpoint/incremental/SharedStateRegistryImpl`（全文）

**grep 全量扫描**（共享前缀附录 C 口径 + 扩展）：static 非 final 字段、static final 集合/线程不安全工具、ThreadLocal/NamedThreadLocal（**两模块 main 源码零 ThreadLocal**，与基线一致）、构造器内 start/register（无 unsafe publication 命中）、`++` 复合递增（命中均为任务线程单线程字段或 AtomicLong）、synchronized/computeIfAbsent/CHM 全量清单对照基线。

**规模口径对照**：owner doc `docs-for-ai/03-modules/nop-stream.md` 声明的指标/gauge 生命周期语义（D2c 重注册/关闭语义、随 TaskManager.stop 释放）作为判定边界。

---

## 共享可变状态清单表（载体 × 写者 × 读者 × 同步机制 × 判定）

### 载体 1：static 可变字段 / 全局注册表

| 字段 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|
| `SideOutputRegistry.CONSUMERS`（CHM） | 计划构建线程（`SideOutputDataStream.sink`） | 任务线程（`ChainingOutput:124`、`StreamTaskInvokable:1231`） | ConcurrentHashMap | **发现 C1-002**（无 job 作用域、无生产 remove） |
| `SourceCoordinatorRegistry.COORDINATORS` / `PENDING_ENUMERATOR_RESTORES`（CHM） | 首个 open 的子任务线程 / restore 路径 | 各子任务线程、TaskManager 收尾清扫、StreamExecutionEnvironment 收尾 | CHM + registerIfAbsent / consume-once remove | 正常（v1「按 vertexId 单执行」限制已在 javadoc 声明） |
| `WindowedStreamImpl.defaultFactory`（static volatile） | `setDefaultFactory` / 首次 getFactory（DCL，volatile+synchronized 双检） | 计划构建线程 | volatile + synchronized DCL（正确形态） | 正常（启动/配置路径粗锁，校准豁免） |
| `StreamExecutionEnvironment.serviceLoaderFactory` / `defaultCheckpointExecutorFactory`（static） | `setCheckpointExecutorFactory` / 首次 discover（static synchronized） | execute 路径 | static synchronized 缓存 | 正常 |
| `BasicTypeInfo.INSTANCES`（**plain HashMap**） | `register()`（static init）/ `of()`（任意线程） | `of()` 全部调用点 | **无** | **发现 C1-003** |
| `StreamElementCodec.DECODED_VALUE_TYPES`（CHM） | 解码线程（首见类名） | 全部解码线程 | CHM + computeIfAbsent（键空间=负载类名，可枚举有界） | 正常 |
| `StreamMetricsRegistries.COMPOSITE`（static final Composite） | addMember/removeMember（ops 启停） | 全部 meter 写入/抓取线程 | micrometer Composite 自身线程安全 | 正常 |
| `EngineMetrics.BY_JOB` / `TaskNodeMetrics.BY_NODE`（CHM）+ gauge retention refs | forJob/forNode computeIfAbsent；releaseJob/releaseNode（stop 路径） | 生命周期路径、抓取线程 | CHM + 显式 release（owner doc 声明语义） | 正常（EngineMetrics 的 GAUGE_STATE_REFS.remove 为死代码 → P3 C1-011） |
| `MicrometerStreamTaskMetrics.TASK_HOLDERS`（CHM，holder-rebind） | 构造器 computeIfAbsent+set；releaseHolder CAS 置 null | 抓取线程（gauge 闭包） | CHM + AtomicReference holder（D2c 形态） | 正常（键空间随 job 基数缓慢增长，条目极小；releaseHolder 后内容置 null） |
| `ChannelQueueGauges.HOLDERS` / `DbBacklogGauges.HOLDERS`（CHM，holder-rebind） | bind（computeIfAbsent+set） | 抓取线程 | CHM + AtomicReference；channel 侧有 release，**db 侧无 release** | **发现 C1-009**（D2c 兄弟不对称） |
| `TaskManager.invokableWaitTimeoutMs`（static volatile） | 测试 hook（同线程） | RunningTask 任务线程 | volatile | 正常（生产恒 30s） |
| `JdbcCheckpointStorage.sidSequence`（static） | nextSid() | nextSid() | static synchronized | 正常 |

### 载体 2：singleton / 长生命周期组件实例字段

| 字段 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|
| `TaskManager.runningTasks`（CHM） | receiveAssignment putIfAbsent / deployTask compute（CC-02 epoch 感知）/ cancelTask remove / updateFencingToken removeIf | 心跳、trigger、cancel、getRunningTaskCount | CHM 原子操作 + semaphoreReleased CAS | **发现 C1-004**（receiveAssignment/installInvokable 无 epoch 比较；updateFencingToken 与在途 deploy 的间隙） |
| `TaskManager.completedTasks`（CHM） | RunningTask finally put + 逐个淘汰（>1000） | getCompletedTaskResults（unmodifiable 视图） | CHM；有界（MAX_COMPLETED_TASKS=1000，惰性收敛） | 正常 |
| `TaskManager.capacitySemaphore` / commitExecutor（有界队列+CallerRuns） | 部署/取消/提交通知路径 | — | Semaphore / BQP | 正常（R5-CC-10 已治理） |
| `TaskManager.running` / `coordinatorRpcService`（volatile） | start/stop / setter | RPC 线程、任务线程 | volatile | 正常 |
| `RunningTask.invokable/canceled/finished/error/future`（volatile） | 任务线程 / 控制线程 | 心跳、cancel、trigger、finally | volatile + CountDownLatch | 正常（null-check 防御完备） |
| `LocalSourceCoordinator.enumerator` + enumeratorLock | startEnumerator（锁内）/ close（锁内）/ **registerReader 的 addReader（锁外）** / **ReaderChannel.requestSplits（无锁）** | coordinator 线程（snapshotState 锁内）+ 各 reader 任务线程（无锁） | **部分同步（不一致纪律）** | **发现 C1-001**（P1） |
| `JobCoordinator.taskAssignmentMap/allTaskLocations/attemptCounters/subtaskLiveness/deployIssuedAtMs/completedSubtaskKeys`（CHM/Set） | 分配/恢复（recoveryLock 内）+ livenessLock 内合并 | failure detector、RPC 线程 | recoveryLock（ReentrantLock）+ livenessLock（REG-02 契约）+ recoveryPending CAS 去重 | 正常（CC/REG 系列已治理，注释化契约完备） |
| `JobCoordinator.barrierFanoutExecutors`（CHM，per-node 单线程池） | fanoutExecutorFor computeIfAbsent | stop 时迭代 shutdown | CHM；条目随节点数有界 | 正常 |
| `CheckpointCoordinator.pendingCheckpoints`（CHM）/ `latestCompletedCheckpoint`（volatile）/ `tasksToAcknowledge`（volatile）/ `lastCompletedTimestamp`（volatile）/ listeners+participants（COW） | 全部公共入口 synchronized(this) | ACK/触发/超时线程 | coordinator monitor + volatile | 正常（persist/commitFanout/retention executor 惰性创建：首创建均在 monitor 内，retention 线程复用经 submit HB 链发布，注释化论证成立） |
| `RetentionCleaner.retentionInProgress/retentionRecheckNeeded`（AtomicBoolean） | 完成回调（monitor 内）/ retention 线程 finally | 同 | CAS + getAndSet（串行化 + trailing re-run） | 正常 |
| `CheckpointBarrierTracker.inFlight`（LinkedHashMap） | trigger/acknowledge/abort/setChannelState | 同 + 观测读 | 全公共方法 synchronized；回调在锁外触发 | 正常 |
| `ResultPartition.queue`（LBQ）/ `finished/pendingReplay/materializationPoint/currentMaterializationEpoch`（volatile） | 生产者线程 write/close；恢复线程 attach/drain | 消费者线程 read | LBQ + volatile +permit 收支对称（drain 释放） | 正常（attach 单活动段契约的守卫为 check-then-act，见 C1-008 备注） |
| `RemoteResultPartition.sendLock` + backlog cache（synchronized 三方法） | 生产者任务线程 / close | ops 抓取线程（readBacklogCached） | sendLock 锁内 finished 复检 + 方法级 synchronized | 正常 |
| `RemoteInputChannel.queue`（LBQ）/ finished/decodeError/lastReceivedTime（volatile） | message-service 派发线程 | 任务线程 | LBQ + volatile | 正常 |
| `InMemoryClusterRegistry.nodes/leaseStartTimes/leaseExpireTimes/taskAssignmentHistory` | register/renew/evict（nodes 锁内）/ assignTask（history 锁内） | getActiveNodes/getNodeLease（**无锁读**） | 部分 synchronized | **发现 C1-006**（F4 只增不删）+ **C1-010**（拆分裂读，P3） |
| `JobHealthStateMachine.current`（volatile）+ listeners（COW） | transition（synchronized，监听器在锁内触发） | REST/观测线程 | synchronized + volatile | 正常 |
| `OpsJobManager.jobs/terminalAt`（CHM）+ governanceSweeper | submit/stop/remove/sweep（全 synchronized） | sweeper 线程 | manager monitor | 正常（低频管理路径粗锁，校准豁免） |
| `PollingJdbcMessageService.subscriptions`（CHM→COW list）、Subscription.task/cancelled/suspended/lastSeenId | subscribe/cancel | poller 线程 | CHM+COW+volatile+AtomicLong | 正常（initialized 双初始化竞态由幂等 DDL 兜底，非缺陷） |
| `SimpleKeyedStateStore.stateWrites/stateReads`（plain long `++`） | 任务线程（keyed state 属主线程） | 同线程 | 单线程归属 | 正常（校准：单线程字段非共享） |
| `MicrometerStreamTaskMetrics.watermarkGaugesRegistered`（plain boolean） | bindWatermarkGauges（任务 wiring 单线程） | 同线程 | 单线程归属 | 正常 |

### 载体 3/4/5：ThreadLocal / 构造逃逸 / long 非原子

- ThreadLocal：两模块 main 源码 **0 处**（`new ThreadLocal|NamedThreadLocal` grep 为空），无池化线程未清理风险面。
- 构造器启动线程/注册监听器（unsafe publication）：grep 无命中；`ProcessingTimeServiceDriver.start()` 显式启动且 thread volatile，`JobHealthStateMachine`/bus 监听器注册走 COW。
- long/double 非原子读写：唯一跨线程裸读长整型数组点 = `InputGate.currentWatermarks`（long[]）被指标抓取线程读取 → **发现 C1-007**；`RunningTask.error`（volatile Throwable 引用）、各 long 状态均 volatile 或锁内。

---

## 发现条目

### [nop-stream-core-runtime-C1-001] SplitEnumerator 跨线程调用无统一锁纪律：reader 任务线程无锁直调 enumerator，锁内锁外并存（F10）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/LocalSourceCoordinator.java:176-191, 254-267, 296-314`
- **证据片段**:
  ```java
  // registerReader — addReader 在 enumeratorLock 之外执行（各 reader 任务线程并发进入）：
  public ReaderChannel<T> registerReader(int subtaskIndex) {
      ReaderChannel<T> channel = new ReaderChannel<>(subtaskIndex, this);
      ReaderChannel<T> prev = readerChannels.putIfAbsent(subtaskIndex, channel);
      if (prev != null) { return prev; }
      startEnumerator(null);            // 锁内
      try {
          enumerator.addReader(subtaskIndex);   // ← 锁外
      } catch (Exception e) { ... }
      return channel;
  }
  // ReaderChannel.requestSplits — reader 任务线程直接调用，完全不加 enumeratorLock：
  public void requestSplits(int subtaskIndex, Optional<Throwable> reason) {
      ...
      owner.enumerator.handleSplitRequest(subtaskIndex, reason);   // ← 无锁
      owner.enumeratorQueried = true;
  }
  // 对照：snapshotState / close 持有 enumeratorLock —— 三种纪律并存
  public SourceEnumeratorSerializedState snapshotState(long checkpointId) {
      synchronized (enumeratorLock) { ... enumerator.snapshotState(checkpointId) ... }
  }
  ```
- **严重程度**: P1
- **并发场景**: LOCAL/FLIP-27 路径下同一 source vertex 的 N 个子任务各在自己的任务线程（`stream-task-executor-*` 池线程）执行 `open()→registerReader→addReader` 与运行期 `requestSplits→handleSplitRequest`；coordinator/checkpoint 线程持 `enumeratorLock` 执行 `snapshotState`。类 javadoc 声明的线程模型是"enumerator 方法只被 coordinator 线程调用，reader 交互只经 ReaderChannel"——实际代码让 reader 线程直接进入了 enumerator，声明的单线程契约既无运行时守卫（F10），也无锁兜底。
- **触发时序**: 并行 source 启动即为常态触发（无需罕见调度）：T1=子任务 0 的任务线程在 `registerReader` 返回后执行 `enumerator.addReader(0)`（LocalSourceCoordinator.java:186，锁外）；T2=子任务 1 的任务线程同时执行 `registerReader`，`startEnumerator` 返回（T1 已把 `started=true`）后同样进入锁外 `enumerator.addReader(1)`。两线程对同一 enumerator 实例的内部可变状态并发读写。具体受害者（connector 侧佐证，非发现对象）：`BatchLoaderSplitEnumerator.assignIfNeeded`（`private boolean assigned` 的 check-then-act，两线程同见 false → 单个 batch split 被 assign 给两个子任务 → 整批数据双份消费）；`DebeziumTableSplitEnumerator`（plain `LinkedHashMap shareBySubtask`，`deliverShare` 并发 `remove` → 条目丢失/结构破坏 → 表份额静默丢失）；`FileSplitEnumerator` size-aware 策略（两线程各算 plan、各自 mark，plan 分叉时同一文件被两个子任务领取 → 文件级重复读取）。另一交错：checkpoint 线程持锁 `snapshotState` 序列化 `assignedFiles` 的同时，reader 线程无锁 `handleSplitRequest` 改写同一 CHM 集合 → 快照非原子（撕裂容忍度取决于集合原子性，复合不变量被破坏）。
- **现状**: 锁只保护了 enumerator 生命周期两端（start/snapshot/close），中间最高频的两个入口（addReader、handleSplitRequest）完全裸奔；ReaderChannel 自身（pendingSplits/onSplitsDelivered）倒是同步完备，但请求方向没有收敛回 coordinator 线程（无 mailbox/队列漏斗）。
- **风险**: 并行度 ≥2 的任一 FLIP-27 source 在启动窗口即可触发：批量源整批双份（exactly-once 语义违约）、debezium 表份额丢失（数据缺失）、文件源 size-aware 双领取（重复数据）。均为主路径数据正确性后果，且触发条件是部署拓扑的常态而非罕见竞态。
- **建议**: 在 `LocalSourceCoordinator` 内把 `addReader`/`handleSplitRequest` 收敛进 `enumeratorLock`（最小修复：registerReader 的 addReader 移入锁内或在方法内包 `synchronized(enumeratorLock)`；ReaderChannel.requestSplits 同样包锁）。更彻底的修复是按声明的线程模型把 reader 请求经每 coordinator 单线程执行器（可复用平台 `SyncThreadPoolExecutor` 或 TaskMailbox 同款单消费者漏斗）串行化，使"enumerator 单线程"契约获得运行时守卫。
- **失效面枚举**（F3 同族清扫）: ① `LocalSourceCoordinator.registerReader:186`（addReader 锁外）— 本发现主体；② `LocalSourceCoordinator.ReaderChannel.requestSplits:260`（handleSplitRequest 无锁）— 本发现主体；③ `LocalSourceCoordinator.snapshotState:297`（锁内，但与 ①② 构成不一致纪律）— 修复后随之闭合；④ `LocalSourceCoordinator.close:334`（锁内）— 已正确；⑤ 全部 `SplitEnumerator` 实现逐一登记：`BatchLoaderSplitEnumerator`（boolean check-then-act，**实际易损**）、`DebeziumTableSplitEnumerator`（plain LinkedHashMap + `remove`，**实际易损**）、`FileSplitEnumerator`（CHM 字段但 size-aware plan 为复合操作，**条件易损**；round-robin 目标确定性使其对双领取免疫）、`MessageTopicSplitEnumerator`（无状态 per-call，**免疫**）；⑥ `RunningTask.maybeReleaseSourceCoordinatorRegistryEntry:363-381`（unregister 清扫）— 与本发现无交集，已按 declared v1 limitation 豁免。
- **信心水平**: 确定（锁纪律不一致是代码事实；双领取/丢份额后果已在具体 enumerator 实现上逐步可推演）
- **误报排除**: 不属于"原语使用不是问题"的校准项——这里是复合操作在无公共锁下跨线程暴露，且类自身 javadoc 声明了与之矛盾的单线程模型；也不属于"低频路径粗锁"——addReader/handleSplitRequest 是每次 source 部署与每次 split 请求的常态路径。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C1-002] SideOutputRegistry 进程级全局注册表：无 job 作用域、无生产卸载路径，跨 job 同名 tag 互相劫持 + 终态 job 注册永久滞留

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/SideOutputRegistry.java:32,40-75`（写点 `SideOutputDataStream.java:90`；读点 `ChainingOutput.java:124`、`StreamTaskInvokable.java:1231`）
- **证据片段**:
  ```java
  private static final Map<String, Consumer<StreamRecord<?>>>
          CONSUMERS = new ConcurrentHashMap<>();
  /** 注册（同 tagId 直接替换前一个 consumer）*/
  public static void register(String tagId, Consumer<StreamRecord<?>> consumer) {
      ...
      CONSUMERS.put(tagId, consumer);
  }
  /** remove() 在两个模块的 main 源码中零生产调用方（clear() 仅测试用）*/
  public static void remove(String tagId) { ... CONSUMERS.remove(tagId); }
  ```
- **严重程度**: P1
- **并发场景**: 同一进程内的多 job 并存执行（runtime 侧 `OpsJobManager` REST 多作业模式；同一 JVM 的多个嵌入式 `execute`）。写者=各 job 的计划构建线程；读者=运行中任务的数据面线程（每条 tagged 记录查一次注册表）。
- **触发时序**: T1=作业 A 构建管线，`getSideOutput(tagA).sink(sinkA)` 以 `tagA.getId()`（用户声明的字符串，如 `"alerts"`）注册 consumerA；A 开始运行。T2=作业 B 在同进程提交，恰好声明了同名 tag id，构建时 `register` 以 consumerB **替换** consumerA（CHM put 覆盖，无任何 job 维度校验或告警）。此后 A 的生产任务每条 side-output 记录经 `StreamTaskInvokable:1231`/`ChainingOutput:124` 查表命中 consumerB → **A 的数据被持续路由进 B 的 sink**（跨 job 数据错投，正确性违约），且无日志无错误。泄漏交错：作业 A 终止后其注册项永不移除（`remove` 无生产调用方），closure 强引用 sinkA 及其闭包图，随 job 基数单调累积。
- **现状**: 注册表以 CHM 保证原语线程安全，但 key 空间只有用户 tag id，既无 job/epoch 作用域，也无生命周期卸载（`unregister` API 存在而未接线）。
- **风险**: 多作业共存是该 runtime 的声明能力（REST `POST /jobs` 多作业模式）；两个作业复用常见 side-output 命名（"alerts"/"dlq" 等）即静默错投数据——语义违约且难排查。滞留注册使长运行协调进程按 job 基数无界累积闭包（量级小但单调）。
- **建议**: 注册键加入 job 维度（`jobId + tagId` 或 per-job registry 实例挂到 `StreamComponents`/execution 上下文），运行时查找点同样携带 job 身份；作业 stop/finish 路径调用 `remove`（与 `SourceCoordinatorRegistry.unregister` 的收尾清扫同型）。
- **失效面枚举**（F3 同族清扫）: ① `SideOutputRegistry.register`（写，无作用域）— 本发现主体；② `SideOutputRegistry.get`（`ChainingOutput.java:124` 链内路由读点）— 受同一缺陷影响；③ `SideOutputRegistry.get`（`StreamTaskInvokable.java:1231` 跨任务路由读点）— 受同一缺陷影响；④ `SideOutputRegistry.remove` — 存在但零生产调用方（清理面缺失本体）；⑤ `SideOutputRegistry.clear` — 测试专用，不豁免生产缺失；⑥ 同族全局注册表对照：`SourceCoordinatorRegistry`（键=vertexId，v1 限制已声明 + 有 unregister 清扫接线 → 豁免）、`StreamMetricsRegistries`（进程级组合注册表，指标语义本就进程级 → 豁免）。
- **信心水平**: 确定（错投路径逐行成立；触发依赖"两作业同名 tag"这一用户输入条件）
- **误报排除**: javadoc 只声明了"JVM-local"作用域与"同 id 替换"语义，未声明"跨 job 同名 tag 由用户自负"——按共享前缀规则 6，守卫缺失且文档未声明才是缺陷；与"有界声明存在的缓存"校准项无关（此处问题不是容量而是键作用域与生命周期）。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C1-003] BasicTypeInfo.INSTANCES 为 plain static HashMap，of() 无同步的检查-然后-行动（F10 + 容器并发写）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/typeinfo/BasicTypeInfo.java:18,44-51`
- **证据片段**:
  ```java
  private static final Map<Class<?>, BasicTypeInfo<?>> INSTANCES = new HashMap<>();
  ...
  public static <T> BasicTypeInfo<T> of(Class<T> typeClass) {
      @SuppressWarnings("unchecked")
      BasicTypeInfo<T> info = (BasicTypeInfo<T>) INSTANCES.get(typeClass);
      if (info != null) return info;
      info = new BasicTypeInfo<>(typeClass);
      INSTANCES.put(typeClass, info);   // ← 无同步 put，且 get→put 非原子
      return info;
  }
  ```
- **严重程度**: P2
- **并发场景**: 计划构建/类型解析可由多个线程并发执行：`OpsJobManager` REST 多作业提交（buildArtifacts 在 synchronized submit 内串行，但工厂内部启动的构建逻辑与嵌入式 `StreamExecutionEnvironment.execute` 的用户线程可并行）、多 environment 并发 execute、以及序列化路径的类型解析。
- **触发时序**: T1=线程 A 调 `of(CustomA.class)`，`get` 返回 null，正在构造；T2=线程 B 调 `of(CustomB.class)`，同样 get null 后 `put`。两线程并发 put 触发 HashMap 并发扩容/写：JDK8+ 下后果为条目丢失或树化过程中的数据损坏（极端情况下遍历抛 CCE）；即使无扩容交错，两线程对同一 class 也会各建一个实例并只存活一个映射——`BasicTypeInfo` 未重写 equals/hashCode，两个"同类型"实例 `!=`，下游任何以实例同一性为准的比较（如 `argType == BasicTypeInfo.INT` 风格的判断）在拿到重复实例的调用方处行为分叉。
- **现状**: 静态 init 的九个内建类型经 `register` 单线程填充是安全的；缺陷仅在运行期 `of()` 对任意用户类的懒注册路径。
- **风险**: 并发构建窗口内的 HashMap 状态损坏是进程级全局污染（所有后续类型解析共享该 map）；重复实例造成的行为分叉是静默语义偏差。触发频率低（需并发懒注册同一窗口），一旦发生难以定位。
- **建议**: 换 `ConcurrentHashMap` + `computeIfAbsent`（与同模块 `StreamElementCodec.DECODED_VALUE_TYPES` 同型）；或对内建九型保留、`of()` 未命中时同步双检。
- **失效面枚举**（F3 同族清扫）: 全模块"static final 集合容器"清单逐一核对：`BasicTypeInfo.INSTANCES`（**plain HashMap，本发现**）；`TaskStateTransition.LEGAL_TRANSITIONS/TERMINAL`（static init 后只读 → 免疫）；`StreamEmitMode.NAME_MAP`（static init 只读 → 免疫）；`JobHealthStateMachine.ALLOWED`（`Map.of` 只读 → 免疫）；`ClassNameValidator.ALLOWED_PREFIXES` / `StreamDeserializationFilter.BASELINE_PREFIXES`（unmodifiable 只读 → 免疫）；`SideOutputRegistry.CONSUMERS`（CHM → 另见 C1-002）；`StreamElementCodec.DECODED_VALUE_TYPES`（CHM+computeIfAbsent → 免疫）。范围外兄弟（供跨模块合并参考）：`nop-stream-flow/StreamSchemaRegistry`、`nop-stream-sql/StreamAggregatorFunctionResolver` 仅读内建常量，不写 map → 免疫。
- **信心水平**: 确定（缺陷形态确定；实际触发需并发懒注册窗口）
- **误报排除**: 非"原语使用不是问题"——HashMap 的并发复合读写是公认未定义行为，非风格问题；非低频启动路径——`of()` 服务于运行期任意用户类型的解析，不限于启动。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C1-004] TaskManager 两阶段分配路径缺 epoch 守卫：receiveAssignment 重复槽位检查不比较 fencing epoch，installInvokable 无 epoch 校验

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:565-570, 617-631`
- **证据片段**:
  ```java
  // receiveAssignment：重复槽位守卫只看 key，不比较新旧 epoch（对照 deployTask 的 CC-02 compute）
  String taskKey = taskKey(assignment);
  if (runningTasks.containsKey(taskKey)) {
      LOG.warn("Task {} already running, ignoring duplicate assignment", taskKey);
      capacitySemaphore.release();
      return;                              // ← 新 epoch 的 assignment 被静默忽略
  }
  ...
  // installInvokable：对任意已存在槽位直接装入新 invokable，无 epoch 比对
  public void installInvokable(String jobId, String vertexId, int subtaskIndex,
                               StreamTaskInvokable invokable) {
      String taskKey = taskKey(jobId, vertexId, subtaskIndex);
      RunningTask runningTask = runningTasks.get(taskKey);
      if (runningTask == null) { LOG.warn(...); return; }
      ...
      runningTask.setInvokable(invokable);  // ← 可能装进旧 epoch 的 RunningTask
  }
  ```
- **严重程度**: P2
- **并发场景**: 恢复期协调者（新 epoch）与旧 epoch 任务在同一 TM 上交叠：`receiveAssignment`/`installInvokable` 由 RPC/嵌入执行器线程调用，`updateFencingToken` 由协调者 RPC 线程调用，`RunningTask.run` 的 finally 由旧任务线程执行。
- **触发时序**: 时序 1：T1=旧 epoch-5 任务占住 taskKey（RunningTask 在 map 中）；T2=协调者已 `updateFencingToken(6)` 后重派，`receiveAssignment(epoch=6)` 到达 → 入口 epoch 检查通过（activeEpoch=6）→ `containsKey(taskKey)` 命中 **epoch-5 的旧槽位** → WARN+release+return，新代任务永不部署，槽位由僵尸旧任务继续持有（后续 `installInvokable(6)` 还会把新 invokable 装进旧 RunningTask，制造跨代混装）。时序 2（updateFencingToken 窗口）：T2=僵尸协调者的 `deployTask(epoch=5)` 已通过 :679 的 epoch 检查，正在执行耗时的 `buildSubtaskPlan`；T1=新协调者 `updateFencingToken(6)` 的 `removeIf(oldEpoch)` 先跑完（此刻 map 无该 key）；T2 随后 `runningTasks.compute` 把 **epoch-5 任务插回 map** → 过代任务在节点上运行（自愈路径：新 epoch 对同槽位的后续 deployTask 会按 CC-02 规则置换它，窗口内其输出可短暂进入数据面）。
- **现状**: `deployTask` 路径已经 R6 CC-02 用 per-key compute + `selectDisplacedTask` 做了 epoch 感知置换；但两阶段分配路径（`receiveAssignment`+`installInvokable`，嵌入/Rpc 执行器使用）没有同等的 epoch 比较，`updateFencingToken` 与在途 deploy 之间也无重验。
- **风险**: 恢复场景下槽位被旧代任务占有时新代部署被静默丢弃 → 该子任务停摆直至下一轮恢复（烧重启预算）；过代任务短窗运行 → 旧 epoch 输出短暂流入数据面。均为恢复路径缺陷，常态运行不触发。
- **建议**: `receiveAssignment` 的重复槽位分支改为读出既有 RunningTask 比较 `getFencingEpoch()`（旧代 → 走 CC-02 同款置换；新代 → 拒绝并回报）；`installInvokable` 校验 `runningTask.getFencingEpoch() == assignment epoch` 再装入；`deployTask` 在 `compute` 的 lambda 内对 `currentFencingEpoch.get()` 做最终重验（把 epoch 检查推进到与槽位写入同一临界区）。
- **失效面枚举**（F3 同族清扫）: 同族"控制面入口的 epoch 守卫"逐一核对：`TaskManager.receiveAssignment:529`（入口有 epoch 检查、**槽位守卫无 epoch 比较** — 本发现）；`TaskManager.installInvokable:617`（**无 epoch 检查** — 本发现）；`TaskManager.deployTask:660`（入口+descriptor 双检 + CC-02 compute — 已治理）；`TaskManager.triggerCheckpoint:919`（epoch fail-fast — 已治理）；`TaskManager.cancelTask:939`（epoch fail-fast — 已治理）；`TaskManager.notifyCheckpointComplete:997`（epoch fail-fast — 已治理）；`TaskManager.updateFencingToken:1065`（单调拒绝 + removeIf — 与时序 2 相关的窗口本体）。
- **信心水平**: 很可能（时序 1 依赖"receiveAssignment 先于/独立于 updateFencingToken 清槽到达"的恢复序；时序 2 的窗口真实存在但需僵尸协调者恰好在窗）
- **误报排除**: 非"启动路径粗锁"校准项——这是恢复期控制面入口的守卫缺失；`deployTask` 同族已有治理先例（CC-02），证明该风险面在本仓库被认定为真实缺陷族而非设计意图。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C1-005] TwoPhaseCommitSinkFunction.finishCommit 的 containsKey→commit→remove 为 TOCTOU：并发通知下可双重 commit，安全性依赖子类幂等而无常量保证（F10）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:176-205`
- **证据片段**:
  ```java
  for (Map.Entry<Long, Object> entry : toCommit.entrySet()) {
      Long eid = entry.getKey();
      synchronized (pending) {
          if (!pending.containsKey(eid))
              continue;                 // ← 去重检查
      }                                  // ← 锁在此结束
      commit(eid);                       // ← 提交在锁外执行（阻塞 I/O，故意出锁）
      synchronized (pending) {
          pending.remove(eid);           // ← 检查与移除之间无原子性
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 同一 sink 实例的 `finishCommit(epochId, true)` 可由多条路径并发触达：TM 侧单线程 commitExecutor 的通知、协调侧 commit-fanout 执行器的失败重试（`CheckpointCoordinator.failedCommitParticipants` 重放）、以及 LOCAL 路径的参与者通知——代码注释自身承认"coordinator-side forwarder retries failed commits"与通知可能交叠。
- **触发时序**: T1=通知 A 进入循环，`containsKey(eid)`=true，出锁执行 `commit(eid)`（阻塞 JDBC/文件 I/O）；T2=重试/重复通知 B 同 epoch 进入，`containsKey(eid)` 仍=true（T1 尚未 remove）→ B 也执行 `commit(eid)`；两线程随后先后 `remove`（第二次 no-op）。若 `commit(eid)` 非幂等（按接口契约子类自由实现，基类不强制），同一外部事务被提交两次/提交动作重放。空 map 分支 `else { commit(epochId); }` 更无任何去重，重复通知直接重复调用。
- **现状**: 检查/移除在锁内、阻塞提交在锁外是合理的锁粒度选择，但因此去重语义退化为"尽力而为"；类对调用方未声明"finishCommit 必须串行"契约，也无 per-sink 运行时守卫（如 in-flight epoch 集合）。
- **风险**: 已知生产 sink（jdbc-2pc 台账、file 2PC rename）具备幂等性兜底，故降 P2；但基类是 13+ 子类的扩展契约，任何新 sink 的非幂等 commit 在重试与通知交叠时即双提交（exactly-once 破坏）。
- **建议**: 在基类加 per-instance 提交互斥（如 `Set<Long> committingEpochs` 的 CAS 或单飞守卫），把"commit 幂等"从隐式假设升级为基类保证；或在 javadoc 显式声明串行契约并加 fail-fast 断言（对照 F10 口径）。
- **失效面枚举**（F3 同族清扫）: 同类"提交路径守卫"逐一核对：`finishCommit` 成功分支 :190-200（**TOCTOU 本体**）；`finishCommit` 空 map 分支 :201-203（**无去重直接 commit**，同族兄弟）；`restoreFromEpoch` :250-274（restore 路径 commit 循环，单线程恢复上下文调用 → 时序上安全，但在 `setPendingCommits` 换 map 后旧引用持有者仍可并发操作旧 map——窗口在任务未启动前，豁免）；`RunningTask.notifyCheckpointComplete:310-339`（对每个 operator 调 finishCommit，注释明确依赖"ledger/manifest idempotency guards"——**幂等假设的下游锚点**，与本体同根）；TM `commitExecutor` 单线程（同实例串行化的主防线，已存在但只覆盖 TM 路径，不覆盖协调侧重试）。
- **信心水平**: 很可能（交叠窗口与并发源真实；后果被现有 sink 幂等性掩蔽，基类层面无常量）
- **误报排除**: 非"synchronized 使用本身"——问题在跨锁复合操作的非原子性；非错误处理维度——不涉及异常吞咽，纯并发守卫缺失。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C1-006] ClusterRegistry 任务尝试历史只增不删：removeTaskAssignment 在生产代码零调用方，内存 map 与 JDBC 表均随 job 尝试单调累积（F4）

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/InMemoryClusterRegistry.java:44,143-169`、`JdbcClusterRegistry.java:275-291`
- **证据片段**:
  ```java
  // InMemoryClusterRegistry — append-only（G56 注释自己声明"append, do not overwrite"）
  private final Map<String, List<TaskAssignment>> taskAssignmentHistory = new ConcurrentHashMap<>();
  @Override
  public void assignTask(String jobId, String vertexId, int subtaskIndex, ...) {
      ...
      synchronized (taskAssignmentHistory) {
          List<TaskAssignment> existing = taskAssignmentHistory.get(key);
          if (existing == null) { existing = new ArrayList<>(); taskAssignmentHistory.put(key, existing); }
          ...
          existing.add(assignment);      // 每次（重）分配追加一条，永不裁剪
      }
  }
  // JdbcClusterRegistry.removeTaskAssignment — DELETE 语句存在，
  // 但 grep 全部 main 源码：该 API 的生产调用方 = 0
  ```
- **严重程度**: P1
- **并发场景**: 写者=协调者分配/恢复路径（`AssignmentPlanner:168` 每次 assign/globalRecovery 追加）；读者=attempt 关联查询。无并发缺陷本体——问题是生命周期（F4 账本族），并发维度只是载体盘点的一部分。
- **触发时序**: 非竞态，单调累积链：每次 `assignTasks()`/`globalRecovery()` 对每个 (job,vertex,subtask) 追加一条 `TaskAssignment`（含 attempt 递增）→ 作业终态后无任何路径调用 `removeTaskAssignment` → 进程内 map 条目跨作业存活至进程退出；JDBC 形态的 `nop-stream` 分配表行**跨进程重启**持续累积。长运行多作业协调进程（REST 多作业模式）下，"作业数 × 拓扑槽位 × 尝试数"行数/条目无上界。
- **现状**: 清理 API（`removeTaskAssignment`）在接口与两个实现中均存在，但生产代码零接线；owner doc 的治理配置（`StreamGovernanceConfig`）只覆盖协调侧观测历史与终态作业记录，不含注册表分配历史。
- **风险**: JDBC 平面为持久化单调增长（跨重启），属 AR-06 同族（JDBC 账本只增不删）；内存平面随进程寿命线性增长。无正确性后果（读侧恒取 last），但持久表无界膨胀拖慢查询并最终成为运维事故。
- **建议**: 在作业终态路径（`JobCoordinator.terminate` 完成 / `OpsJobManager` 终态清扫 sweeper）接线 `removeTaskAssignment`（逐槽位或按 jobId 前缀批删）；JDBC 平面为治理配置增加保留期裁剪（与 `job-record.retention-minutes` 同扫）。
- **失效面枚举**（F4 同族"append-only 账本"逐一核对）: ① `InMemoryClusterRegistry.taskAssignmentHistory` — 本发现主体（无清理）；② `JdbcClusterRegistry` 分配表 — 本发现主体（DELETE 存在未接线）；③ `InMemoryClusterRegistry.coordinators/nodes/lease*` — register/renew 覆盖写 + evictExpiredNodes 有清理 → 免疫；④ `TaskManager.completedTasks` — 1000 上界 + 惰性淘汰 → 免疫；⑤ `CheckpointCoordinator.checkpointSegments` — retention GC（gcSegmentsForCheckpoint remove）→ 免疫；⑥ `MicrometerStreamTaskMetrics.TASK_HOLDERS`/`ChannelQueueGauges.HOLDERS`/`DbBacklogGauges.HOLDERS` — javadoc 声明"按进程内出现过的身份基数有界、故意不移除"（D2c 裁定）→ 声明边界豁免（Db 侧冻结值问题另见 C1-009）；⑦ `OpsJobManager.terminalAt` — governance sweeper 按保留期裁剪 → 免疫；⑧ JDBC 2PC 台账（connector 模块，范围外）— 历史已立案，不重复计数。
- **信心水平**: 确定（调用方 grep 全量核实为零）
- **误报排除**: 非"有界声明存在的缓存"校准项——owner doc 未对该账本声明任何上界或保留策略；与 ⑥ 的 D2c 声明边界不同，此处无声明。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C1-007] InputGate 水位数组被指标抓取线程无同步读取：long[] 撕裂 + boolean[] 陈旧（观测面数据竞争，理论风险）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:118,201,532-583`；读点 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/metrics/MicrometerStreamTaskMetrics.java:197-218`
- **证据片段**:
  ```java
  private final long[] currentWatermarks;      // 任务线程写（handleWatermarkNonRecursive）
  private final boolean[] channelIdle;         // 任务线程写（idle 状态翻转）
  ...
  // Micrometer 抓取线程经 holder → gateRef 读取（无任何同步）：
  public long getCurrentWatermark() {
      long min = Long.MAX_VALUE;
      for (int i = 0; i < currentWatermarks.length; i++) {
          if (channelIdle[i]) { continue; }    // ← 可见性无保证
          anyActive = true;
          if (currentWatermarks[i] < min) {    // ← long 元素非原子读（JLS 17.7 允许撕裂）
              min = currentWatermarks[i];
          }
      }
      ...
  }
  ```
- **严重程度**: P2
- **并发场景**: 写者=任务线程（watermark/idle 处理，数据面热路径）；读者=Prometheus/ops HTTP 抓取线程（`StreamOpsHttpServer` 的 4 线程池 → gauge 闭包 → `MicrometerStreamTaskMetrics.mergedWatermarkOrNaN/channelLagOrNaN` → `InputGate.getCurrentWatermark/getMaxActiveChannelWatermark`）。
- **触发时序**: T1=任务线程写 `currentWatermarks[0]`（64 位写，JVM 实现上可分裂为两个 32 位半写）；T2=抓取线程恰在半写之间读取该元素 → 撕裂值（如时间戳高位旧值+低位新值）参与 min/max 聚合；或 T2 在 T1 写 `channelIdle[i]` 前读旧值 → min 集合与新 watermark 状态错配。后果限于 gauge 单次抓取值异常（NaN/负 lag/跳变），下次抓取自愈。**理论风险，未具体化到可复现调度**（HotSpot 对对齐 long 实际按原子处理， formally 可能、实践未证）。
- **现状**: 数组元素无 volatile/锁； WI16 gauge 是声明交付面（owner doc 指标表），抓取路径与任务线程的交叉未被声明或防护。
- **风险**: 观测数据瞬时失真可能触发误告警（如 lag 跳变）；不影响流处理正确性。声明规模包络内无资源后果。
- **建议**: 最小修复：`getChannelWatermark` 系读取入口改为对 `long[]` 快照（`synchronized` 拷贝或改用 `AtomicLongArray`）；抓取频率低，锁开销可忽略。
- **失效面枚举**（F3 同族清扫）: 跨线程读内部数组的入口逐一核对：`InputGate.getCurrentWatermark:532`（抓取+任务两用 — 本发现）；`InputGate.getMaxActiveChannelWatermark:573`（抓取专用 — 本发现同族）；`InputGate.getChannelWatermark:561`（单元素 long 读 — 同族）；`InputGate.isAllChannelsIdle`（boolean[] 读 — 同族，陈旧无害）；`MicrometerStreamTaskMetrics` 时间占比 gauge（读 micrometer Counter/Timer 自身，线程安全 → 免疫）；`ChannelQueueGauges.queueSizeOf`（读 LBQ size，线程安全 → 免疫）；`DbBacklogGauges.backlogOf`（读 synchronized 缓存 → 免疫）；`TaskManager.getRunningTaskCount`（CHM 迭代 + volatile 读 → 免疫）。任务线程内部的 `minWatermarkExcluding/minWatermarkAllIgnoringIdleStatus` 单线程读写 → 免疫。
- **信心水平**: 很可能（JMM 层面成立；实际撕裂依赖 JVM 实现）
- **误报排除**: 非"无状态并发调用"校准项——确有共享可变数组跨线程；降 P2 而非 P1 因后果限于观测面且自愈。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C1-008] SourceReaderOperator.reader 非 volatile，跨线程 bridge 回调读取陈旧 null 可使迟到 split 永久滞留 preOpenSplits（理论风险，未具体化）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/SourceReaderOperator.java:100,107-108,220-234,262-263`
- **证据片段**:
  ```java
  private SourceReader<OUT, ? extends SourceSplit> reader;   // ← 非 volatile
  private final java.util.List<SourceSplit> preOpenSplits =
          java.util.Collections.synchronizedList(new java.util.ArrayList<>());
  ...
  // 该回调运行在"执行 enumerator 分配的线程"上（可能是另一子任务的任务线程）：
  public void accept(List<SourceSplit> splits) {
      SourceReader r = getReaderRaw();      // ← 裸读 reader 字段
      if (r != null) { r.addSplits(splits); }
      else { preOpenSplits.addAll(splits); } // ← 陈旧 null 时入缓冲
  }
  ```
- **严重程度**: P2
- **并发场景**: `open()`（子任务 N 的任务线程）写 `reader` 字段并做一次性 `flushPreOpenSplits()`；enumerator 分配侧（可能是子任务 M 的任务线程经 `handleSplitRequest→assignSplits(标 N)`）执行回调读同一字段——两线程间无 happens-before 边（回调发布经 volatile `onSplitsDelivered`，但 `reader` 写不经过任何共同同步动作）。
- **触发时序**: T1=子任务 0 的 `open()` 完成（reader 已赋值、`flushPreOpenSplits` 已跑过、不再重跑）；T2=子任务 1 的任务线程执行 `assignSplits(0, splits)` → `drainPending` → 回调读 `getReaderRaw()`，JMM 下允许读到陈旧 null → splits 进入 `preOpenSplits`；此后无人再 flush（`flushPreOpenSplits` 只在 open() 调用一次）→ **这批 split 永久滞留**，有界源表现为部分文件/数据永不被处理。**理论风险，未具体化**（需 JVM 实际服务陈旧值；工程时间窗极宽但概率极低）。
- **现状**: preOpen 缓冲 + 一次性 flush 的设计正确覆盖了"wire 先于 open"的主时序（经 `synchronized(preOpenSplits)` 建 HB）；遗留的是 open 之后跨线程回调对 `reader` 的裸读分支。
- **风险**: 有界 source 静默缺数据（不报错、不挂死、checkpoint 无感知）——恰是 F9/F11 类"静默不完整"。概率极低。
- **建议**: `reader` 字段加 `volatile`（一行修复，语义即闭合）；或回调分支去掉 else 缓冲、改为 fail-fast（open 后不允许再出现 null）。
- **失效面枚举**（F3 同族清扫）: 同类"跨线程懒发布字段"逐一核对：`SourceReaderOperator.reader`（**本发现**）；`SourceReaderOperator.assignmentProxy/subtaskIndex/totalParallelism`（wire 单线程写、open 同线程读 → 免疫）；`RunningTask.invokable`（volatile → 免疫）；`RemoteInputChannel.finished/decodeError/lastReceivedTime`（volatile → 免疫）；`TaskProcessingTimeService.nextTimerTimestamp`（volatile → 免疫）；`LocalSourceCoordinator.enumeratorQueried`（volatile → 免疫）；`ResultPartition.finished/pendingReplay`（volatile → 免疫）。
- **信心水平**: 有趣的猜测（JMM 层面成立，工程概率极低；无同步动作连接读写是事实）
- **误报排除**: 非"单线程归属字段"——回调确可运行在非属主线程（跨子任务分配路径）；降 P2 因写不出可复现调度且主时序已被 flush 设计覆盖。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C1-009] DbBacklogGauges 缺少兄弟 ChannelQueueGauges 的 close 释放（D2c 不对称）：关闭后的 gated 分区 gauge 冻结非零值并强引用分区（F8/F3）

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/DbBacklogGauges.java:37-61`；对照 `ChannelQueueGauges.java:123-129` + `RemoteInputChannel.java:512`
- **证据片段**:
  ```java
  // ChannelQueueGauges（兄弟实现）有 close 释放：
  static void release(RemoteInputChannel channel) {
      AtomicReference<RemoteInputChannel> holder = channel.queueGaugeHolder;
      if (holder != null) { holder.compareAndSet(channel, null); }   // gauge 归 0
  }
  // RemoteInputChannel.close() 调用 release(this)；
  // RemoteResultPartition 全文无任何 DbBacklogGauges.release —— close() 只发 EOS：
  @Override
  public void close() {
      if (isFinished()) { return; }
      markFinished();
      ... synchronized (sendLock) { messageService.send(topic, eos); } ...
  }
  ```
- **严重程度**: P3
- **并发场景**: 无数据竞争本体；生命周期对称性缺陷。写者=计划构建线程 bind；读者=抓取线程；释放路径缺失使关闭后的 holder 仍指向死分区。
- **触发时序**: 时序即生命周期：gated 生产分区（`backlogLimit>0`）随任务关闭后，`HOLDERS` 中该 topic 的 AtomicReference 仍持有分区对象，gauge 持续读 `readBacklogCached()` 的**最后缓存值**（非 0）——作业停止后 `nop.stream.io.db.backlog` 冻结显示历史积压，直到进程重启。
- **现状**: Channel/Db 两个 gauge 族采用同一 holder-rebind 形态，但只有 channel 侧接了 close→release（D2c 声明语义只覆盖 channel queue gauge）。
- **风险**: 观测误导（停机后积压告警恒真）+ 死分区对象滞留（有界：每 gated topic 一项）。
- **建议**: 仿照 `RemoteInputChannel:512`，在 `RemoteResultPartition.close()`（或持有方任务收尾）调用新增的 `DbBacklogGauges.release(partition)`（holder CAS→null，gauge 读 0）。
- **失效面枚举**（F3 同族清扫）: gauge 生命周期配对逐一核对：`ChannelQueueGauges.bind/release`（配对完整，`RemoteInputChannel.java:512` 接线 → 免疫）；`DbBacklogGauges.bind`（**无 release，无接线** — 本发现）；`MicrometerStreamTaskMetrics.releaseHolder`（任务收尾调用，`StreamTaskInvokable:820` 段注释证实 → 免疫）；`TaskNodeMetrics.releaseNode`（`TaskManager.stop:411` 接线 → 免疫）；`EngineMetrics.releaseJob`（`JobCoordinator.stop` 接线 → 免疫，死代码瑕疵另见 C1-011）。
- **信心水平**: 确定
- **误报排除**: 非"有界声明存在的缓存"——D2c 只为 channel gauge 声明了关闭归零语义，db 侧无声明即无豁免；定 P3 因后果纯观测面。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C1-010] InMemoryClusterRegistry 的 lease 双 CHM 拆分裂读：getNodeLease/getActiveNodes 不持 nodes 锁，租约视图可短暂自相矛盾

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/InMemoryClusterRegistry.java:100-109,127-140`（写侧 68-97,111-124）
- **证据片段**:
  ```java
  // 写侧两个 map 在 synchronized(nodes) 内成对更新：
  public boolean renewLease(String nodeId, long leaseTimeoutMs) {
      synchronized (nodes) {
          ...
          leaseStartTimes.put(nodeId, now);
          leaseExpireTimes.put(nodeId, now + leaseTimeoutMs);   // 成对写
          ...
      }
  }
  // 读侧不持锁，两个 CHM 各读一次 → 可见不同 renewal 代际：
  public LeaseInfo getNodeLease(String nodeId) {
      Long startAt = leaseStartTimes.get(nodeId);       // 第 N 代
      ...
      long expireAt = leaseExpireTimes.get(nodeId);     // 第 N+1 代
      boolean active = expireAt > now;
      return new LeaseInfo(nodeId, startAt, expireAt, active);
  }
  ```
- **严重程度**: P3
- **并发场景**: 写者=TM 心跳线程（renew/register）、evict 调用线程；读者=协调者 failure detector / 观测查询。
- **触发时序**: T1=心跳线程持锁更新 start(N)→expire(N+1)；T2=读者在其间读 start(N)（旧值）+ expire(N+1)（新值）→ LeaseInfo 的 start/expire 窗口跨代错配（甚至 start>expire 的畸形窗口）。后果：一次性的过期/存活误判，下一心跳周期自愈。**理论风险，未具体化到可复现后果**（消费方对 LeaseInfo 的瞬时错配无已知放大路径）。
- **现状**: 状态被拆成三个 CHM，只有 `nodes` 更新在锁内；读侧设计上就放弃了快照一致性（注释承认防御式容忍）。
- **风险**: 极低的误判窗口；`evictExpiredNodes` 自身在锁内、只用 expire 单值 → 清理决策不受影响。无正确性后果。
- **建议**: 若要闭合：单 CHM 值对象（`NodeLease{startAt,expireAt}`）整体 put/get；或读侧包 `synchronized(nodes)`。当前形态可接受为已知瑕疵。
- **失效面枚举**（F3 同族清扫）: 拆分裂读点逐一核对：`getNodeLease:100`（本发现）；`getActiveNodes:127`（nodes CHM 迭代 + leaseExpireTimes 补读 — 同族，注释已声明防御容忍）；`evictExpiredNodes:111`（锁内单值判定 → 免疫）；`renewLease/registerNode`（锁内成对写 → 免疫）；JDBC 形态（单行原子读写 → 免疫）。
- **信心水平**: 确定（竞争存在）/ 后果判定为轻微
- **误报排除**: 非"原语使用"问题（复合状态无快照读）；不报 P2 因消费面无已知放大且自愈。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C1-011] EngineMetrics 的 GAUGE_STATE_REFS 对 checkpoint-size gauge 的保留/移除为死代码：构造器从不 add，remove 恒空操作

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/metrics/EngineMetrics.java:52-53,66-76,109-119`
- **证据片段**:
  ```java
  private static final java.util.Set<Supplier<?>> GAUGE_STATE_REFS =
          java.util.concurrent.ConcurrentHashMap.newKeySet();
  ...
  private EngineMetrics(MeterRegistry registry, String jobId) {
      ...
      registry.gauge(METRIC_CHECKPOINT_SIZE, tags, latestSizeBytes, AtomicLong::get);
      // ← 注意：从未 GAUGE_STATE_REFS.add(latestSizeBytes)
  }
  private void removeFromRegistry() {
      GAUGE_STATE_REFS.remove(latestSizeBytes);   // ← 恒为 no-op 的防御性移除
      registry.remove(completed.getId());
      ...
  }
  ```
- **严重程度**: P3
- **并发场景**: 无实际并发缺陷：gauge 状态对象 `latestSizeBytes` 是 EngineMetrics 的 final 字段，而实例被 `BY_JOB`（CHM）强引用持有到 `releaseJob`，弱引用 gauge 因此实际不会消失——正确的保留恰是"偶然"经由另一条路径成立的。
- **触发时序**: 非竞态：代码路径层面 `GAUGE_STATE_REFS.remove(latestSizeBytes)` 永远移除一个从未加入的元素。
- **现状**: 对照 `registerNodesActiveGauge`（确实 add supplier）与 `TaskNodeMetrics`（keyed retention refs，成对 put/remove），本类 checkpoint-size gauge 的保留机制与移除代码不对称。
- **风险**: 无直接风险；若未来有人"优化"掉 BY_JOB 强引用或改为 weak gauge，checkpoint-size gauge 会静默消失（弱引用陷阱，正是该 retention set 存在的原因）——死代码掩盖了真实依赖。
- **建议**: 构造器补 `GAUGE_STATE_REFS.add(latestSizeBytes)` 使保留显式化；或删除该 set 对本类的误导性引用并注释真实保留链（BY_JOB 强引用）。
- **失效面枚举**（F3 同族清扫）: gauge 保留机制逐一核对：`EngineMetrics.GAUGE_STATE_REFS`（**半接线** — 本发现）；`EngineMetrics.registerNodesActiveGauge`（add/remove 由 `StreamOpsHttpServer` stop 经 removeMember 处理 registry 侧；retention add 正确 → 免疫）；`TaskNodeMetrics.RUNNING_GAUGE_REFS`/`COMMIT_QUEUE_GAUGE_REFS`（put/releaseNode 成对 → 免疫）；`MicrometerStreamTaskMetrics.TASK_HOLDERS`（构造 add + releaseHolder CAS 置 null → 免疫）。
- **信心水平**: 确定
- **误报排除**: 非行为缺陷（当前运行时正确），按 P3"防御性代码冗余"登记。
- **复核状态**: 未复核

---

## 汇总

| 严重程度 | 数量 | 条目 |
|---|---|---|
| P0 | 0 | — |
| P1 | 3 | C1-001（enumerator 跨线程无锁纪律）、C1-002（SideOutputRegistry 跨 job 劫持+滞留）、C1-006（分配历史只增不删，F4） |
| P2 | 5 | C1-003（BasicTypeInfo 静态 HashMap）、C1-004（TM 两阶段分配 epoch 守卫缺口）、C1-005（2PC finishCommit TOCTOU）、C1-007（InputGate 抓取撕裂，理论）、C1-008（reader 裸读，理论） |
| P3 | 3 | C1-009（DbBacklogGauges 关闭不释放）、C1-010（lease 拆分裂读）、C1-011（EngineMetrics 死保留代码） |

**总体观感**：本模块并发治理成熟度高——JobCoordinator/CheckpointCoordinator/TaskManager 的主状态（recoveryLock、livenessLock、recoveryPending CAS、CC/REG 系列守卫、holder-rebind gauge、permit 收支对称的 BufferPool/ResultPartition）均有注释化契约且形态正确；ThreadLocal 零使用、无 unsafe publication 命中。剩余风险集中在三类：① 声明线程模型与实际调用线不一致的 source coordinator 边界（C1-001，本轮最高优先级）；② 进程级全局注册表的键作用域/生命周期（C1-002、C1-006）；③ 治理已完成路径之外的兄弟入口未同步升级（C1-004 vs CC-02、C1-009 vs D2c、C1-005 vs commitExecutor 串行化）。

# C1 共享可变状态与可见性 — nop-stream-core-runtime（深挖第 2 轮追加）

## 深挖第 2 轮追加

- 审计对象：第 1 轮（`C1-shared-mutable-state.md`）未覆盖的盲区面——`operators/`（53 文件中未读部分）、`windowing/`（38 文件）、`common/`（state backends / accumulators / typeutils / buffer / functions）、core `checkpoint/`（38 文件）、`execution/` 未读文件、`connector/registry/`、runtime 侧 `cluster/JdbcLeaderElector`（全文）、`event/`、`alert/`、`launch/SharedJdbcInfrastructure`、`transport/` wire codec 族、`checkpoint/core|storage` 字段面、`taskmanager/` 剩余、`rpc/` 剩余。
- 盲审纪律：仅读取本维度第 1 轮文件与共享提示词；未读取其他审计目录、plans、bugs、lessons。

## 第 2 轮覆盖面（证据性记录，供合并与后续轮次免重扫）

**定向补扫（C1 五步全量口径）**
1. static 非 final 字段精确重扫（两模块 main 源码）：命中 6 处，全部在第 1 轮清单内（`WindowedStreamImpl.defaultFactory`、`StreamDeserializationFilter.cachedConfig`、`StreamExecutionEnvironment.serviceLoaderFactory/defaultCheckpointExecutorFactory`、`JdbcCheckpointStorage.sidSequence`）——无新增载体。static final 集合/线程不安全工具（SimpleDateFormat/Random/Calendar）：0 命中。
2. ThreadLocal/NamedThreadLocal/InheritableThreadLocal：0 命中（与第 1 轮一致）。
3. 构造器 this 逃逸（`addListener(this)`/`register(this)`/`new Thread(this)` 形态）：0 命中；无 unsafe publication 新命中。
4. 检查-然后-行动全量：`containsKey` 43 命中逐一核对——除第 1 轮已立案的 C1-001/004/005 本体外，其余为构建期一次性注册表（`StreamConnectorRegistry` 构建后只读）、CHM 原子操作（已裁定）、锁封闭复合操作。懒初始化 `if (x == null) x = create` 扫描命中点全部落在第 1 轮已论证的 monitor 内（CheckpointCoordinator 三 executor、StreamTaskInvokable driver）或任务线程。
5. long/double 非原子：`double` 字段仅 accumulators（标注未使用/任务线程）与 `DeltaEvictor.threshold`（构造期只读）；裸 `++` 命中全部为任务线程字段、局部变量或 AtomicLong（`PerKeyOrderedBuffer.insertionSeq` 使用方仅 EquiJoin/OverWindow 两算子，任务线程归属核实）。无 C1-007 同族新命中。

**新读文件逐一裁定（全部无新发现）**
- `TimerServiceManager` + `ProcessingTimeServiceDriver` + `HeapInternalTimerService`（全文）：调度线程仅做 volatile `nextProcessingTimeTimer` 读，触发经 `Mail.control` 漏斗回任务线程，timerServices 为 COW——注释化契约与实现一致，判定正常。
- `TimestampedCollector`（reuse-record 同步写出，不跨线程逃逸）、`StreamUnionOperator`（无状态 Shareable）、`TimestampsAndWatermarksOperator`（copyForSubtask 新实例 + strategy 不可变 + 生成器每子任务 open 重建）、`StreamMap/Filter/FlatMap/ReduceOperator`（函数共享为 javadoc 声明契约）。
- `IWindowOperatorFactory`/`WindowOperatorFactoryImpl`（无状态，每调用新建 builder）；`WindowOperator`/`OverWindowOperator`/`MergingWindowSet`/`GlobalKeyedStateStore`/`PerWindowKeyedStateStore`/`NamespaceAware*`/`EquiJoinOperator` 字段面：全部 transient 每子任务、任务线程封闭；checkpoint snapshot/restore 经 mailbox 同任务线程。
- F10/F3 关键族 `copyForSubtask` 全调用点核对：仅 `OperatorChain.deepCopy(int)`:256 与 `deepCopy(TaskLocation)`:277 两个收口；`AbstractStreamOperator.copyForSubtask()` 默认序列化深拷贝安全网 + `@Shareable` 显式豁免 + 非 Serializable fail-fast——两阶段/本地/远程三条部署路径均被收口覆盖，无共享实例旁路。`Shareable` 显式声明者仅 `StreamUnionOperator`（无状态）。
- `AbstractStreamOperator.close()` keyedStateBackend 关闭、"字段故意不置 null"——声明语义，正常。
- `InputGate` 1000-1140 barrier 对齐段（第 1 轮未读段）：`inFlightAlignments`(CHM)/`abortedBarriers`(CHM keySet)/`blockedChannels`(CHM keySet)/`lastAcceptedBarrierIds`(long[]，任务线程独占写读)/`getInFlightBarrierIds`(CHM 弱一致迭代)/`getChannels`(unmodifiable 视图)——任务线程封闭，无抓取线程交叉（水位数组交叉已由 C1-007 立案）。
- `TypeRegistry`（实例 CHM）、`RecordWriter`（全 final）、`RecordReader`/`InputChannel`/`TimerRegistrationFuture`（final+volatile）、分区路由器（无状态或 AtomicInteger，每 RecordWriter 新建）。
- core `checkpoint/`：`CheckpointIDCounter`（AtomicLong）、`LocalFileSegmentStore`（final）、DTO 包（不可变载体）、`participant/CheckpointParticipant`。
- runtime：`JdbcLeaderElector` 全文（volatile 初始化旗标 = 幂等 DDL best-effort，选举本体 DB 乐观 CAS；无共享内存竞态面）、`StreamJobEventBus`（COW + 声明式吞异常日志）、`WebhookAlertChannel`（ArrayBlockingQueue(256) 满丢弃 WARN——声明行为、单线程投递 executor）、wire codec 族（无状态 static final INSTANCE）、`DataPlaneMessageServiceAdapter`（final 委托）、`SharedJdbcInfrastructure`（final）、`StreamNodeAutoRegistration.instance`（注册生命周期单线程）、`NodeDiscoveryConsistencyChecker`（final）、`CheckpointAckSender`（final owner）、`StreamControlRpcServer`（final）、`CheckpointHistory`（CLQ + size 收敛裁剪，同 `completedTasks` 惰性收敛已裁定形态）、`S3/LocalFile/JdbcCheckpointStorage` 字段面（final 配置 + volatile 初始化旗标 + RRWL）、`RestoreGates`/`SavepointMaterializer`（无实例状态）、`RuntimeMetrics`（静态 registry 持有，第 1 轮已裁定）。
- `StreamDeserializationFilter.create()`：volatile cachedConfig 的检查-重建-回写存在竞态，但缓存值是系统属性纯函数、重建幂等、Published 对象不可变（final 字段经 volatile 写发布）——last-write-wins 结果等价，属良性 memoization，不构成发现。
- `TtlContext`（javadoc 自声明 mailbox 单线程模型）、`KeyGroupReshard`/`MaxParallelismReshardMigration`/`RescaleStateAssembler`（离线/恢复单线程工具）。

## 结论

**未发现新的问题。深挖结束。**

第 2 轮未产出新发现条目（P0/P1/P2/P3 均为 0），无需接续第 1 轮序号（C1-001～C1-011 保持为该维度全部条目）。第 1 轮"总体观感"的三类剩余风险（source coordinator 边界、进程级全局注册表、治理后兄弟入口）在本轮新覆盖面上无同族新增命中：`copyForSubtask`/`@Shareable` 契约、定时器 mailbox 漏斗、checkpoint storage 字段面、事件总线、告警通道、选主器均为 final/volatile/Atomic/COW 或锁封闭的正确形态。

---

# C1 共享可变状态与可见性 — 独立复核结论（2026-10-14）

- 复核人：独立复核子代理（与本轮初审不同任务上下文）。本轮复核解除盲审：已读取 `ai-dev/audits/` 历史审计（2026-08 `check/stream-core.md`、2026-09 r1/r4/r5、2026-10 r5/r6 并发维度）与 `git log`。
- 复核方法：逐条重读 live code（发现所指文件与行号全部逐一比对，含各枚举受害者类）+ 调用方 grep 核实 + 对照 `ai-dev/skills/concurrency-resource-audit-prompt.md`「本领域误报校准」与严重程度判级表。

## 维度复核结论

### [C1-001] → 已保留
- 逐行核实成立：`registerReader:186` 的 `enumerator.addReader` 与 `ReaderChannel.requestSplits:260` 的 `handleSplitRequest` 确在 `enumeratorLock` 之外，与 `snapshotState:297`/`close:334` 的锁内纪律并存；类 javadoc（:46-48）声明的单线程契约与实际调用线矛盾。
- 受害者核实：`BatchLoaderSplitEnumerator:32,50-54`（plain boolean check-then-act）、`DebeziumTableSplitEnumerator:47,89`（plain LinkedHashMap `remove`）均为真实易损实现；调用方核实为各子任务任务线程经 `open()→wireCoordinatorChannel→registerReader` 并发进入。
- P1 恰当：并行 source 启动即为主路径常态窗口，后果为数据正确性（双份/丢失/撕裂快照），符合判级表"有明确触发链的可具体化竞态"。

### [C1-002] → 降级（P1 → P2）
- 证据全部成立：`register` 同 tag 直接 put 覆盖（SideOutputRegistry.java:47）、`remove` 生产零调用方（仅测试 `clear`）、读点 `ChainingOutput:124`/`StreamTaskInvokable:1231` 均在数据面、终态作业注册滞留属实。
- 降级理由：触发受"同进程两作业用户自定义 tag id 同名"门控，概率显著低于直接历史先例——r6-CC-04（`SourceCoordinatorRegistry` 裸 vertexId 键，任何两作业**必然**碰撞）经复核裁定 P2；本条触发条件更苛刻而机制同族（静默错投 + 有界泄漏），按同族一致性定 P2。泄漏面为每 job 一条小条目，非 P1"必然无界增长"量级。

### [C1-003] → 已保留
- live code 逐行核实成立：plain `HashMap`（:18）+ `of()` 无同步 get→put（:44-51）；调用方核实 `DataStreamImpl:417,427` 可达任意用户类懒注册。
- P2 恰当：并发懒注册窗口写不出具体时序，按判级表"写不出具体时序的理论竞态"定 P2（历史 P3 系 2026-08 代码检查的自有口径，本审计并发判级表下 P2 为指定档位）。

### [C1-004] → 已保留
- live code 核实成立：`receiveAssignment:565-570` 重复槽位分支只看 key 不比较 epoch；`installInvokable:617-631` 无 epoch 校验；`deployTask` 的 R6 CC-02 per-key compute 置换已在库（:723-765，plan 2326 注释在位），证明两阶段路径为治理后残留缺口；`updateFencingToken`（getAndSet 与 removeIf 非原子）与时序 2 的窗口属实。
- P2 恰当：恢复路径专用（`RunningTask.run` finally :165 会自摘槽位，常态不占位），时序 1 窗口窄但具体可写；与 r5-CC-09（同纪元 assign/cancel 竞态，P3）为同方法不同缺陷，不构成重复立案。

### [C1-005] → 已保留
- 代码形态逐行核实成立：`containsKey` 锁内 → `commit(eid)` 锁外 → `remove` 锁内（:190-200），空 map 分支 `else { commit(epochId); }` 无任何去重（:201-203）。
- 并发入口核实：TM 侧 `RunningTask:321,326`（commitExecutor 单线程）与协调侧 `CheckpointCoordinator:1523,1546`（commit-fanout/retry 重放）确实可交叠；已知 jdbc/file sink 幂等兜底属实，基类对 13+ 子类无幂等保证。P2 恰当（后果被现有 sink 掩蔽，契约缺口真实）。

### [C1-006] → 已保留
- 全量 grep 核实：`removeTaskAssignment` 仅接口（ClusterRegistry.java:127）与两实现定义点（InMemory:190 / Jdbc:275），生产调用方为零；`assignTask` 唯一写点 `AssignmentPlanner:168` 每次分配必追加；InMemory `taskAssignmentHistory:151-166` append-only 属实。
- P1 恰当：JDBC 平面跨重启持久单调增长 + 内存平面随进程寿命线性增长，符合判级表"长时运行必然触发的无界增长"；owner doc 无任何上界/保留声明，不落入"有界声明缓存"豁免。

### [C1-007] → 已保留
- 核实成立：`currentWatermarks`（plain long[], :118）/`channelIdle`（plain boolean[]）被抓取线程经 gauge 闭包无同步读取（MicrometerStreamTaskMetrics:172,176→201,209,213→InputGate:532-590），写者为任务线程。
- P2 恰当：发现自身已标注"理论风险，未具体化"（HotSpot 对齐 long 实际原子），后果限观测面且自愈——判级表下 P2 即该类发现的指定档位，不再下调。

### [C1-008] → 已保留
- 核实成立：`reader` 字段非 volatile（:100），两处回调裸读（:226、:325），`flushPreOpenSplits` 仅 open() 一次（:263）；`onSplitsDelivered` 的 volatile 只能发布回调对象本身，陈旧回调线程对 `reader` 的后续裸读无 HB 边，滞留 `preOpenSplits` 后无人再 flush 的分析成立。
- P2 恰当：JMM 层面成立但写不出可复现调度（发现自称"有趣的猜测"），主时序已被 preOpen+flush 设计覆盖；与已修复的 P1-6.7-008（restore 先于 open）及 r6-CC-04（注册生命周期）均不重复。

### [C1-009] → 已保留
- 核实成立：`DbBacklogGauges` 全文 63 行仅 `bind` 无 release；`RemoteResultPartition` 全文无任何 DbBacklogGauges 引用（close :273 仅发 EOS）；兄弟实现 `RemoteInputChannel:512` 确调 `ChannelQueueGauges.release`。
- owner doc（`docs-for-ai/03-modules/nop-stream.md:88`）D2c 关闭置零语义仅声明 channel queue gauge，db 侧无声明即无豁免；P3 恰当（纯观测面冻结值 + 有界滞留）。

### [C1-010] → 已保留（附更正）
- 形态核实成立：`getNodeLease:100-109` 双 CHM 无锁拆分读 vs `renewLease/registerNode` 锁内成对写（:69-97）。
- 更正：`getNodeLease` 生产代码**零调用方**（仅测试引用），发现所称"failure detector/观测查询"读者实际落在 `getActiveNodes:127-140`（注释已声明防御容忍，且已在失效面枚举中登记）——实际生产暴露面比发现正文更小。P3（地板档）维持：真实形状瑕疵、自愈、无正确性后果。

### [C1-011] → 已保留
- 核实成立：构造器 `:75` 注册 checkpoint-size gauge 从未 `GAUGE_STATE_REFS.add`；`removeFromRegistry:110` 的 remove 恒为 no-op；对照 `registerNodesActiveGauge:145` 确实 add——半接线不对称属实，当前行为正确（BY_JOB 强引用链偶然兜住）。
- P3 恰当（防御性代码冗余，弱引用陷阱属未来风险）。兄弟问题现状：r1-2026-09-26 的"GAUGE_STATE_REFS 无 remove 路径"（P2）已经 `releaseJob/releaseNode` 接线修复；r6-CC-06（nodes-active supplier 只增不移除）在 live code（:145 无配对移除）**仍未修复**。

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状标注 |
|---|---|---|---|---|
| C1-001 | P1 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/LocalSourceCoordinator.java:176-191,254-267,296-314 | SplitEnumerator 跨线程调用锁纪律三态并存：addReader/handleSplitRequest 无锁直调，与声明的单线程契约矛盾，并行 source 启动即可触发双份分配/份额丢失/快照撕裂 | 新发现 |
| C1-002 | P2（降级） | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/SideOutputRegistry.java:32,40-75 | 进程级 side-output 注册表无 job 作用域且 remove 零生产接线：跨 job 同名 tag 静默错投 + 终态注册滞留 | 新发现 |
| C1-003 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/typeinfo/BasicTypeInfo.java:18,44-51 | BasicTypeInfo.INSTANCES 为 plain static HashMap，of() 无同步检查-然后-行动，并发懒注册可致结构损坏与重复实例 | 已知未修复（`ai-dev/audits/check/stream-core.md` [P3] BasicTypeInfo.INSTANCES，2026-08-20；本审计按并发判级表定 P2） |
| C1-004 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:565-570,617-631 | 两阶段分配路径缺 epoch 守卫：receiveAssignment 重复槽位分支与 installInvokable 均不比较 fencing epoch（deployTask 已按 R6 CC-02 治理，此为残留缺口） | 新发现（同族：r5-CC-09 同纪元竞态 P3 已知未修复；r6-CC-02 deployTask 侧已修复） |
| C1-005 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:176-205 | finishCommit 的 containsKey→commit→remove TOCTOU：并发通知/重试下可双重 commit，安全性依赖子类幂等而基类无常量保证 | 新发现 |
| C1-006 | P1 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/InMemoryClusterRegistry.java:44,143-169；JdbcClusterRegistry.java:275-291 | 任务分配尝试历史只增不删：removeTaskAssignment 生产零调用方，内存 map 与 JDBC 表随 job×槽位×尝试单调累积（JDBC 跨重启） | 新发现 |
| C1-007 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:118,532-583 | 水位数组 plain long[]/boolean[] 被指标抓取线程无同步读取：long 撕裂 + 陈旧 idle 状态（观测面理论风险，自愈） | 新发现 |
| C1-008 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/SourceReaderOperator.java:100,220-234,262-263 | reader 字段非 volatile，跨子任务分配回调裸读陈旧 null 可使迟到 split 永久滞留 preOpenSplits（理论风险，未具体化） | 新发现 |
| C1-009 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/DbBacklogGauges.java:37-61 | DbBacklogGauges 缺兄弟 ChannelQueueGauges 的 close 释放：关闭后 gauge 冻结非零积压值并强引用死分区（D2c 不对称） | 新发现 |
| C1-010 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/InMemoryClusterRegistry.java:100-109,127-140 | lease 双 CHM 拆分裂读：读侧不持 nodes 锁可见跨代错配窗口（复核更正：getNodeLease 生产零调用方，实际暴露面仅 getActiveNodes） | 新发现 |
| C1-011 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/metrics/EngineMetrics.java:52-53,66-76,109-119 | GAUGE_STATE_REFS 对 checkpoint-size gauge 半接线：构造器从不 add、remove 恒 no-op，死代码掩盖真实保留链（BY_JOB 强引用） | 新发现（兄弟问题：r6-CC-06 nodes-active supplier 只增不移除，已知未修复） |

**复核汇总**：11 条初审+深挖发现 → 已保留 10、降级 1（C1-002 P1→P2）、驳回 0。严重程度分布（复核后）：P1×2（C1-001、C1-006）、P2×6、P3×3。现状标注：新发现×10、已知未修复×1（C1-003）、已修复×0。无一条触发"误报校准"驳回线：所有发现均有 live code 逐行证据，无"已声明边界+fail-fast 守卫"误判（C1-002 的 javadoc 仅声明 JVM-local 作用域与替换语义，未声明跨 job 安全；C1-009/C1-011 的 D2c 声明明确不覆盖 db 侧与 checkpoint-size gauge）。
