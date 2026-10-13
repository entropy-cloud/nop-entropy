# C2 锁与同步原语 — nop-stream-core-runtime（第 1 轮初审）

- 审计维度：C2（锁与同步原语）——锁使用正确性（互斥成立、无死锁）、锁代价（无 unjustified 争用热点）、等待点超时/可中断性（对照 F5）、synchronized 集合迭代在锁内（仓库门禁先例）。
- 审计对象（live code，`_` 前缀生成文件不在发现对象内）：
  - `nop-stream/nop-stream-core/src/main/java`（439 files / 57,135 lines）
  - `nop-stream/nop-stream-runtime/src/main/java`（132 files / 29,922 lines）
- 盲审纪律：未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；只审计当前 live code。
- 方法：以 `_tmp/cra-baseline-nop-stream-core-runtime.txt` 的 C2 locks / compute 节选圈定重点文件，另做补充 grep（`Collections.synchronized*` 全量清扫、私有锁对象清扫、`wait/awaitTermination/LockSupport/Semaphore/BlockingQueue` 等待点清扫），逐文件全文核读其全部锁载体与调用方线程归属。

## 检查范围说明

**逐文件全文核读**（锁载体密集或被基线命中）：

| 模块 | 文件 | 锁载体 |
|---|---|---|
| core | `common/functions/sink/TwoPhaseCommitSinkFunction.java` | `Collections.synchronizedMap(TreeMap)`（pendingCommits）+ `synchronized(pending)` 块 |
| core | `operators/StreamSinkOperator.java` | synchronizedMap 传递（restoreState 双重包装） |
| core | `operators/SourceReaderOperator.java` | synchronizedList（preOpenSplits）+ synchronized(restoredSplits) + volatile 标志 |
| core | `source/coordinator/LocalSourceCoordinator.java` | `enumeratorLock` 私有锁 + synchronizedList（pendingSplits）+ volatile 回调 |
| core | `source/coordinator/SourceCoordinatorRegistry.java` | ConcurrentMap（registerIfAbsent 原子） |
| core | `execution/CheckpointBarrierTracker.java` | this 监视器（全方法 synchronized） |
| core | `execution/TaskMailbox.java` | 私有 lock + `wait/notifyAll` + ConcurrentLinkedQueue |
| core | `execution/TaskProcessingTimeService.java` / `ProcessingTimeServiceDriver.java` | this 监视器 + volatile due 标志 |
| core | `execution/materialization/InMemoryMaterializationPoint.java` | this 监视器（全方法 synchronized） |
| core | `connector/lookup/CachingTableLookup.java` | DCL（volatile cache）+ synchronized(this)（init/close） |
| core | `datastream/WindowedStreamImpl.java` | class 字面量锁 DCL（volatile defaultFactory） |
| core | `environment/StreamExecutionEnvironment.java` | static synchronized（ServiceLoader 发现，volatile 字段） |
| core | `checkpoint/incremental/SharedStateRegistryImpl.java` | CHM compute/computeIfPresent 每键原子 |
| core | `execution/transport/StreamElementCodec.java` / `metrics/MicrometerStreamTaskMetrics.java` | CHM computeIfAbsent 缓存/gauge holder |
| core | `common/buffer/PerKeyOrderedBuffer.java`、`operators/windowing/WindowOperator.java`、`operators/HeapInternalTimerService.java`、`common/state/backend/memory/*` | computeIfAbsent（核为 task 线程单线程契约，无并发访问点） |
| runtime | `coordinator/JobCoordinator.java` | `recoveryLock`（ReentrantLock）+ `livenessLock` + synchronized(this)（periodic 启停）+ CHM computeIfAbsent（barrierFanoutExecutors） |
| runtime | `checkpoint/CheckpointCoordinator.java` | this 监视器（触发/ACK/完成/中止全路径）+ 异步 persist 逃逸监视器设计 |
| runtime | `checkpoint/PendingCheckpoint.java` | this 监视器 + AtomicReference 状态机 |
| runtime | `checkpoint/RetentionCleaner.java` | CAS 守卫 + 执行器 happens-before 链（有书面推理，核验成立） |
| runtime | `checkpoint/storage/LocalFileCheckpointStorage.java` | ReentrantReadWriteLock（全部读写路径） |
| runtime | `checkpoint/storage/JdbcCheckpointStorage.java` | DCL（表初始化）+ static synchronized nextSid |
| runtime | `cluster/InMemoryClusterRegistry.java` / `JdbcClusterRegistry.java` | `synchronized(nodes)` / `synchronized(taskAssignmentHistory)` / DCL（volatile） |
| runtime | `transport/RemoteResultPartition.java` / `RemoteInputChannel.java` | `sendLock` 私有锁 + this 监视器（backlog TTL 缓存）+ 有界 LinkedBlockingQueue |
| runtime | `transport/ChannelQueueGauges.java` / `DbBacklogGauges.java` | CHM computeIfAbsent（gauge holder 注册） |
| runtime | `taskmanager/TaskManager.java` | CHM compute（槽位置换单原子操作）+ Semaphore（容量） |
| runtime | `health/JobHealthStateMachine.java` | synchronized transition（**锁内派发 listener**） |
| runtime | `event/StreamJobEventBus.java` | CopyOnWriteArrayList + 同步内联派发 |
| runtime | `ops/OpsJobManager.java`、`ops/StreamOpsHttpServer.java`、`ops/StreamMetricsReporter.java`、`launch/JobCoordinatorMain.java`、`launch/TaskManagerMain.java`、`launch/PollingJdbcMessageService.java` | synchronized 生命周期方法（低频管理路径） |

**F5 等待点清扫结论**：`TaskMailbox.take()` 的 `lock.wait()` 无超时但有 `closed` volatile + `notifyAll` + 中断响应（close 唤醒、`InterruptedException` 传播）→ 挂死可检测（threaddump 可见且 close 可解除）；`RemoteResultPartition.gateOnBacklog` 100ms 分片睡眠 + 10s 总预算（锁外，声明语义）；`InputGate.parkNanos` 有界；`PollingJdbcMessageService.close` `awaitTermination(2s)`；`TaskManager.stop` 各执行器 `awaitTermination(5s)`。未发现无中断响应且无退出条件的无限期等待点。

**误报校准应用**：无状态单例并发调用、原语本身使用、启动/管理路径粗锁（OpsJobManager.submit、`StreamExecutionEnvironment` 静态发现、JDBC 建表 DCL）、有界配置齐备的队列/缓存、task 线程单线程契约下的非并发容器（WindowOperator/HeapInternalTimerService/PerKeyOrderedBuffer 的 computeIfAbsent）均未立项。

## 锁序图（锁 A 内取锁 B 的全部边）

节点：`RC`=`JobCoordinator.recoveryLock`；`CC`=`CheckpointCoordinator(this)`；`PC`=`PendingCheckpoint(this)`；`H`=`JobHealthStateMachine(this)`；`BUS`=StreamJobEventBus 同步派发（非锁，调用方锁内）；`TRK`=`CheckpointBarrierTracker(this)`；`2PC`=2PC sink `pendingCommits` 监视器；`REG-N`=`InMemoryClusterRegistry.nodes` 监视器；`REG-H`=其 `taskAssignmentHistory` 监视器；`ENUM`=`LocalSourceCoordinator.enumeratorLock`；`OPS`=OpsJobManager(this)；`SEND`=RemoteResultPartition.sendLock；`LFL`=LocalFileCheckpointStorage RW 锁。

```text
RC  ──→ H            （globalRecovery 预算耗尽 → failJob → health.onFailJob，JobCoordinator.java:1772/1782）
RC  ──→ REG-H        （prepareAssignmentsLocked → assignTask synchronized(taskAssignmentHistory)）
RC  ──→ BUS ──→ …    （globalRecovery/failJob 锁内 fire 事件；BUS 为内联派发，listener 锁未知）
CC  ──→ PC           （acknowledgeTask → pending.acknowledgeTask；abortPendingCheckpoint → removed.abort；
                       onCompletePersistFailure → pending.forceFail）
CC  ──→ 2PC          （triggerCheckpoint→injectBarrier 直通路径 → snapshotState → 2PC saveState synchronized(pendingCommits)；
                       经 TRK 重入）
CC  ──→ BUS ──→ H    （onCompletePersistSuccess/Failure/abort 锁内 jobEventBus.fire(CHECKPOINT_COMPLETED)
                       → JobCoordinator 构造期注册的 listener → health.onDurableCheckpoint，JobCoordinator.java:535-541）
CC  ──→ LFL          （sync-fallback：completePersistSynchronously → storage.storeCheckPoint 持 CC 监视器做存储 I/O——已声明语义）
CC  ──→ ENUM         （buildEpochManifest → snapshotSourceEnumerators → coord.snapshotState 持 enumeratorLock；
                       async 路径在 persist 线程、sync 路径在 ACK 线程）
TRK ──→ 2PC          （triggerCheckpoint 持 TRK 监视器 → offerBarrier 直通 injectBarrier → sink saveState）
H   ──→ BUS ──→ …    （transition 锁内派发 health listener；在库 listener 仅 log/事件转发，JOB_DEGRADED 再 fire 进 BUS）
OPS ──→ RC           （OpsJobManager.submit synchronized → coordinator.start/assignTasks → recoveryLock）
SEND                 （叶子锁：sendLock 内仅 messageService.send；backlog this 监视器不嵌套 sendLock）
```

**环检测：在库代码未发现环**。逐边核对反向边：`H`/`PC`/`2PC`/`REG-*`/`ENUM`/`SEND` 监视器内均不回调持有 `RC`/`CC`/`TRK` 的代码；`RC` 持有期间对 `CC` 的唯一调用点（`abortAllPendingCheckpoints`）被刻意移到 `recoveryLock.unlock()` 之后（JobCoordinator.java:1815-1826，R6 CC-01 注释）——设计正确。

**两条"距环一步"的受警边**（当前无在库实现成环，立为契约风险，随 C2-003 记失效面）：
1. `H`（transition）与 `CC`（onCompletePersist*）都在监视器内同步派发 BUS listener；`CC ──→ BUS ──→ H` 已是实边。任何未来注册的 event/health listener 回头调用 `checkpointCoordinator.*`（取 CC）或 `globalRecovery`（取 RC）即成环 `CC→BUS→H→BUS→listener→CC/RC`。当前在库 listener（Logging/AlertService/Webhook 有界队列/DEGRADED→fire）均无此行为，属"守卫缺失但未触发"。
2. `CC ──→ BUS` 派发点在 CC 监视器内做内联 listener 调用——WebhookAlertChannel 有界队列满时丢弃（不阻塞），Logging 写日志——当前安全。

## 发现条目

### [nop-stream-core-runtime-C2-001] LocalSourceCoordinator 的 enumeratorLock 未覆盖全部 enumerator 入口（addReader / handleSplitRequest 锁外调用）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/LocalSourceCoordinator.java:176-191, 254-267, 296-314`
- **证据片段**:
  ```java
  // registerReader —— startEnumerator 释放 enumeratorLock 之后：
  public ReaderChannel<T> registerReader(int subtaskIndex) {
      ...
      startEnumerator(null);          // 锁内启动 enumerator，返回即已解锁
      try {
          enumerator.addReader(subtaskIndex);   // ← 锁外变更 enumerator 状态
      } catch (Exception e) { ... }
  }
  // ReaderChannel.requestSplits（reader task 线程直接调用）：
  owner.enumerator.handleSplitRequest(subtaskIndex, reason);   // ← 锁外
  // 而 snapshotState：
  public SourceEnumeratorSerializedState snapshotState(long checkpointId) {
      synchronized (enumeratorLock) {  ... enumerator.snapshotState(checkpointId); ... }
  ```
- **严重程度**: P1
- **并发场景**: task 线程（`SourceReaderOperator.wireCoordinatorChannel`→`registerReader`；reader `pollNext`→`requestSplits`）与 checkpoint 持久化线程（`CheckpointCoordinator.executePersistAsync`→`buildEpochManifest`→`snapshotSourceEnumerators`→`coord.snapshotState`，async 模式在 `checkpoint-persist-*` 线程）并发访问同一 `SplitEnumerator` 实例。
- **触发时序**: T1（persist 线程）进入 `snapshotState`，持 `enumeratorLock`，正在执行 `enumerator.snapshotState()` 遍历 enumerator 内部分配集合（状态 S1：正在迭代）→ T2（另一 subtask 的 task 线程）经 `requestSplits` 进入 `handleSplitRequest`（无锁），变更/新增同一集合的条目 → T1 迭代中遭遇结构性变更，抛 CME 或产出撕裂快照。CME 被 `snapshotSourceEnumerators` 的 catch 捕获仅 WARN（CheckpointCoordinator.java:1724-1727），该 epoch 的 `sourceEnumeratorSnapshots` 条目**静默缺失** → 恢复时 enumerator 从零重发现（split 位点丢失语义降级）。撕裂快照则把不一致的分配状态持久化进 manifest。`registerReader→addReader` 同理：区域重启期间新 subtask 的 open() 与上一 epoch 的异步 persist 并发。
- **现状**: `enumeratorLock` 声称保护 enumerator（类注释"enumerator methods are invoked from the coordinator thread"），但三个变更入口中两个（addReader、handleSplitRequest）完全绕开该锁；ReaderChannel 被明确文档化为"task 线程可安全调用"，其内部却直呼未加锁的 enumerator 方法——契约自相矛盾。
- **风险**: 大状态/慢持久化窗口（async persist 拉长）× reader 频繁 requestSplits 时命中概率上升；后果是该 epoch enumerator 状态静默缺失（恢复重发现，可能重复投递 split，破坏 split 级 exactly-once 前提）或持久化撕裂快照。
- **建议**: `registerReader` 的 `addReader` 与 `ReaderChannel.requestSplits` 的 `handleSplitRequest` 一律移入 `enumeratorLock`（handleSplitRequest 内部回调 `assignSplits→channel.deliver` 为非阻塞入队，锁内执行无死锁风险——`deliver` 只取 `pendingSplits` 监视器，锁序 ENUM→pendingSplits 单向无反向边）。
- **失效面枚举**（F3 全同族登记）: enumerator 入口共 5 个——①`startEnumerator`（锁内，OK）；②`snapshotState`（锁内，OK）；③`close`（锁内，OK）；④`registerReader→addReader`（**锁外，本发现**）；⑤`ReaderChannel.requestSplits→handleSplitRequest`（**锁外，本发现**）。同族模式兄弟：`JobCoordinator.recoveryLock` 曾有同族"锁外 fan-out"缺陷（已修，见 JobCoordinator.java:1815-1833 注释）——本发现与其同构但方向相反（漏加锁而非锁内 I/O）。
- **信心水平**: 确定（锁覆盖缺口是静态事实；触发时序具体）
- **误报排除**: 非"低频启动路径粗锁"——requestSplits 是每 reader 轮询即可触发的热路径入口；非"原语使用问题"而是锁覆盖范围与文档契约不一致的复合误用；LOCAL 单 subtask 下确实串行（无害），但 parallelism>1 / 分布式恢复窗口下两个真实线程即可交错。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C2-002] SourceReaderOperator 分裂投递回调的 reader 判空在 flush 锁外，split 可永久滞留 preOpenSplits（静默丢失）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/SourceReaderOperator.java:220-234, 242-263, 297-310`
- **证据片段**:
  ```java
  // 回调（由 enumerator/deliver 线程执行）：
  public void accept(List<SourceSplit> splits) {
      SourceReader r = getReaderRaw();      // ← 非 volatile 字段，无锁读取
      if (r != null) { r.addSplits(splits); }
      else { preOpenSplits.addAll(splits); } // ← 与 flushPreOpenSplits 不同锁，无原子性
  }
  // open()（task 线程）：
  this.reader = source.createReader(ctx);   // 255 行：普通写
  ...
  flushPreOpenSplits();                      // 263 行：一次性清空缓冲，此后再无 flush
  ```
- **严重程度**: P1
- **并发场景**: 枚举器/deliver 线程（初始分配=首个 boot 的 task 线程在 `enumerator.start` 内同步 `assignSplits`；后续分配=另一 subtask 的 task 线程经 `requestSplits→handleSplitRequest→assignSplits→channel.deliver`）与本 operator 的 task 线程（`open()` 内 set reader → flushPreOpenSplits）。
- **触发时序**（parallelism>1，同一 JVM 多 SubtaskTask 线程）: T1（subtask-0 task 线程）执行到 `open():255` 已写入 `this.reader`（T2 尚不可见该写，字段非 volatile）→ T2（subtask-1 task 线程）执行回调，`getReaderRaw()` 读到**陈旧 null** → 走 `preOpenSplits.addAll(splits)` 分支（226-231 行）→ T1 完成 `flushPreOpenSplits()`（263 行，此刻缓冲可能仍空，flush 空跑返回）→ T2 的 `addAll` 落在 flush 之后，split 永久滞留 `preOpenSplits` → subtask-0 的 reader 永远收不到该 split：有界 source 下该 split 的数据静默丢失（isFinished 提前判定）或任务挂住。另一变体：T2 在 T1 判空之后、flush 释放 `preOpenSplits` 监视器之后才 `addAll`（判空与入队两步非原子），无需可见性缺陷即可复现。
- **现状**: 注释声称"Buffering in the channel handles the registerReader-before-create-reader race"，但缓冲与 flush 之间没有共同互斥量：判空在锁外、flush 只在 `open()` 执行一次、`reader` 字段非 volatile。
- **风险**: 静默数据丢失（无报错、无指标），FLIP-27 source 的 split 级 exactly-once 前提被破坏；恢复后亦无法自愈（buffer 不持久化）。
- **建议**: 将判空+分流并入 `preOpenSplits` 同一监视器：回调改为 `synchronized (preOpenSplits) { if (readerKnown()) reader.addSplits(...) else preOpenSplits.addAll(...); }`，`open()` 的 reader 赋值与 flush 也在同一锁内完成；或将 `reader` 声明为 volatile 并把 flush 改为"回调侧驱动"（reader 就绪标志翻转时由回调把缓冲 flush 进 reader）。
- **失效面枚举**（F3 全同族登记）: 同一判空-分流模式共 2 处——①`bridgeChannelToReaderFromWire`（220-234 行，wire 期回调，**同缺陷**）；②`bridgeChannelToReader`（318-333 行，open 期替换回调，**同缺陷**，注释"Should not happen post-open, but defensive"恰是滞留入口）。同族兄弟对照：`flushRestoredSplits`（279-291 行）在 `synchronized(restoredSplits)` 内完成快照-清空，模式正确（但其 reader 侧 restore 也无 happens-before 保障，仅因 restore 只发生在 task 线程而无害）。
- **信心水平**: 确定（交错序列完整可复现；字段可见性与 check-then-act 双缺陷叠加）
- **误报排除**: 非"看到共享字段就报线程不安全"——判空-入队两步与一次性 flush 的交错有明确后果（split 滞留缓冲且无第二次 flush）；非低频路径——split 请求是 reader 轮询驱动。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C2-003] CheckpointCoordinator commit-fanout 执行器 CallerRunsPolicy 饱和时把 sink 提交 I/O 拉回 CC 监视器内执行，并打破串行提交契约

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:994-1013, 1086-1095`
- **证据片段**:
  ```java
  // R6 CC-01 注释（994-1000 行）："participant commit fan-out (sink finishCommit
  // file/JDBC I/O, distributed RPC) and failed-commit retries run on the
  // dedicated commit-fanout executor — NOT under this monitor. Serial execution
  // preserves retry→current-epoch order ..."
  getOrCreateCommitFanoutExecutor().execute(() -> {
      try { retryFailedCommits(); notifyParticipantsFinishCommit(fanoutCheckpointId, true); ...
  // 而执行器：
  return new java.util.concurrent.ThreadPoolExecutor(1, 1, 0L, MILLISECONDS,
          new java.util.concurrent.LinkedBlockingQueue<>(1000),
          NopStreamThreadFactory.named("checkpoint-commit-fwd-" + jobId),
          new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());   // ← 饱和回退
  ```
- **严重程度**: P2
- **并发场景**: `checkpoint-commit-fwd-*` 单线程（正常执行体）与 checkpoint 完成/ACK 调用线程（persist 线程或 ACK 线程，持 CC 监视器）在队列打满时的交叠。
- **触发时序**: T1（fanout 线程）被一个慢 sink `finishCommit`（如 JDBC 2PC 提交遇到 DB 抖动，秒级）钉住 → 每个 durable checkpoint 完成仍向队列投递 1 个任务，约 1000 个 checkpoint（interval=1s 时约 17 分钟持续提交成功而提交转发阻塞）后队列满 → T2（完成线程）在 `onCompletePersistSuccess`（持 CC 监视器）内 `execute` 触发 CallerRuns → `notifyParticipantsFinishCommit` 的 sink file/JDBC I/O **在 CC 监视器内**执行，时长=慢 sink 提交时长 → 期间一切 `tryTriggerCheckpoint/acknowledgeTask/abortPendingCheckpoint` 阻塞 → checkpoint ACK 超时连锁 → 恢复风暴；且 T1 与 T2 并发执行两个 `finishCommit`，击穿注释承诺的"Serial execution preserves retry→current-epoch order"。
- **现状**: 有界队列 + CallerRuns 防丢通知是 plan 369 Phase 4 R5-CC-10 的裁定（owner doc 记载的是 **TaskManager 侧** commit 执行器的 CallerRuns 语义并配 `commitCallerRuns.total` 饱和打点）；coordinator 侧复制了该形状，但**没有**饱和计数指标，且与"NOT under this monitor"注释直接矛盾。
- **风险**: 慢 sink 场景下监视器占用时间被放大为 checkpoint 全链路停摆；并发 finishCommit 依赖 sink 自身幂等（JDBC ledger 幂等有保障，file sink 依赖 move 原子性）兜底；无观测面（TM 侧有 CallerRuns counter，此侧没有）。
- **建议**: 三选一：①饱和打点对齐 TM 侧（新增 coordinator 侧 `commitCallerRuns` counter）+ 注释修正；②CallerRuns 任务内先释放监视器不可能（调用方栈上），改为队列满时投递"标记 + 立即返回"，由 fanout 线程自旋补投（牺牲不丢，保住监视器）；③至少在 CallerRuns 分支内断言/告警"holding coordinator monitor"。
- **失效面枚举**（F3 全同族登记）: CallerRuns 形状执行器共 3 处——①CC 侧 `commitFanoutExecutor`（本发现）；②TaskManager 侧 commit 执行器（TaskManager.java:277 附近，队列 64 + CallerRuns，有饱和 counter，且其调用方不持全局监视器 → **已裁定可接受**）；③TaskManager terminalReportExecutor（队列 1000，拒绝策略需另查，调用方为 RPC 线程无全局监视器 → 低危）。CC 侧 `persistExecutor`（newFixedThreadPool，默认 AbortPolicy，拒绝有 inline 兜底 + WARN，无此问题）。
- **信心水平**: 很可能（饱和路径代码事实确定；达到 1000 深度的运行条件真实但需要分钟级慢 sink + 健康提交并存）
- **误报排除**: 非"有界声明即可豁免"——问题不在队列无界而在**拒绝策略把 I/O 拉回持锁线程**，与该文件自身注释声明的不变式冲突；非纯性能项——伴随并发性/顺序性契约破坏。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C2-004] InMemoryClusterRegistry.taskAssignmentHistory 写锁下维护 ArrayList，读路径（getTaskAssignment/getAttemptHistory）不加锁迭代

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/InMemoryClusterRegistry.java:151-166, 171-187`
- **证据片段**:
  ```java
  synchronized (taskAssignmentHistory) {        // 写侧：append 受监视器保护
      List<TaskAssignment> existing = taskAssignmentHistory.get(key);
      if (existing == null) { existing = new ArrayList<>(); taskAssignmentHistory.put(key, existing); }
      ...
      existing.add(assignment);
  }
  // 读侧：无监视器
  public TaskAssignment getTaskAssignment(...) {
      List<TaskAssignment> history = taskAssignmentHistory.get(...);
      return history.get(history.size() - 1);   // ← ArrayList.get 与并发 add 竞争
  }
  public List<TaskAssignment> getAttemptHistory(...) {
      ...
      return Collections.unmodifiableList(new ArrayList<>(history));  // ← 拷贝迭代无锁
  }
  ```
- **严重程度**: P2（理论风险，未具体化——生产接线中同键读写被 recoveryLock 串行化，见误报排除）
- **并发场景**: 写者=各 JobCoordinator 的 `AssignmentPlanner.prepareAssignmentsLocked→assignTask`（持 recoveryLock）；读者=同 coordinator 的 `seedAttemptCountersFromRegistryLocked→getAttemptHistory`（亦持 recoveryLock）及潜在 RPC/诊断读取。同键读写跨线程并存需两个共享同一 InMemory registry 且同 jobId 的执行体。
- **触发时序**: 理论上 T1 持 `taskAssignmentHistory` 监视器执行 `existing.add`（内部数组扩容拷贝中）→ T2 无锁调用 `history.get(history.size()-1)`：若读到旧数组引用与新 size，抛 `ArrayIndexOutOfBoundsException`，或经非 volatile 读看到未发布的元素。**生产中无法点名两个会同键交错的线程**（同 coordinator 内两路径被 recoveryLock 串行；OpsJobManager 多 jobId 共享 registry 时键不同、list 对象不同），故按共享前缀规则降 P2 并标注。
- **现状**: 写侧明确加了监视器（G56 注释"read-modify-write 受保护"），读侧遗漏——不满足仓库门禁先例"每个 synchronized 字段的迭代点必须在锁内"。
- **风险**: 直接以该 registry 构建诊断/RPC 读取面（或未来拆除 recoveryLock 串行假设）时，读路径可抛 AIOOBE 或读到未发布元素；当前为潜伏的门禁违规。
- **建议**: 读侧复制/取尾也放入 `synchronized (taskAssignmentHistory)`；或把值类型改为 `CopyOnWriteArrayList`（append 少读多，契合本访问模式）。
- **失效面枚举**（F3 全同族登记）: 该 registry 全部读点 8 个——`getActiveCoordinator`（CHM，OK）、`getNodeLease`（CHM，OK）、`getActiveNodes`（CHM 弱一致迭代，OK）、`getTaskAssignment`（**缺口**）、`getAttemptHistory`（**缺口**）、`removeTaskAssignment`（CHM remove，OK，读者持旧引用无害）、`evictExpiredNodes`（锁内迭代，OK）、`renewLease`/`registerNode`（锁内，OK）。同族对照：`JdbcClusterRegistry` 同接口为 DB 实现（行级事务），无此内存集合问题。
- **信心水平**: 确定（同步纪律缺口是静态事实）/ 很可能（若串行假设失效则可复现）
- **误报排除**: 非"看到共享集合就报线程不安全"——写侧确有监视器，缺陷仅在读者绕过；降级理由是生产线程图谱内写不出无前提的具体交错，已按规则标注并降 P2。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C2-005] CheckpointBarrierTracker.triggerCheckpoint 持监视器直通 injectBarrier（mailbox 缺失/finished 回退路径），快照序列化在锁内执行

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java:111-151` + `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/SourceReaderOperator.java:523-569`
- **证据片段**:
  ```java
  public synchronized boolean triggerCheckpoint(long checkpointId, ...) throws Exception {
      ...
      if (head instanceof StreamSourceOperator) {
          boolean accepted = ((StreamSourceOperator<?>) head).offerBarrier(barrier);
  // SourceReaderOperator.offerBarrier 的回退分支：
  MailboxExecutor exec = this.mailboxExecutor;
  if (exec == null) {
      try { injectBarrier(barrier); ...        // ← 调用方（TRK 监视器持有者）线程上直接快照
  // injectBarrier → snapshotState → 全量算子状态序列化（JSON/Base64），再 snapshotCallback.accept
  ```
- **严重程度**: P2
- **并发场景**: barrier 注入线程（分布式模式=jc-barrier-fanout 单线程或 TaskManager.triggerCheckpoint RPC 线程，持 TRK 监视器）与 ACK 处理线程（`StreamTaskInvokable.setupSnapshotCallbacks` 把 `acknowledgeOperator` 接到算子快照回调上；RPC ACK 路径为另一线程）。
- **触发时序**: T1（fanout/RPC 线程）进入 `triggerCheckpoint`，持 TRK 监视器，source 已 `finished`（或 mailbox 未接线）→ `offerBarrier` 走直通分支，`injectBarrier→snapshotState` 在 T1 线程上做全量快照序列化（大状态时秒级）→ T2（ACK 线程）携上一 epoch 的 `OperatorSnapshotResult` 调 `acknowledgeOperator`，在 `synchronized(this)` 上阻塞整个快照时长 → ACK 延迟被注入路径放大；若 T2 的 ACK 正是最后一份，epoch 完成被推迟，逼近 checkpointTimeout。同型：`StreamSinkOperator.processBarrier` 的 2PC `saveState`（synchronized(pendingCommits)）也在该直通栈内（TRK→2PC 边）。
- **触发时序备注**: mailbox 正常接线时 barrier 经 mail 在 task 线程安全点执行（设计意图），监视器内重操作仅在回退路径（finished / mailbox==null / LOCAL 无 tracker 场景）发生——影响面限于回退路径，但该路径正是"source 已 finished 的终局 checkpoint"这一真实场景。
- **现状**: 快照与 ACK 的互斥本身必要（inFlight LinkedHashMap 非并发容器，376 行注释已声明），缺陷在互斥范围覆盖了快照执行而非仅登记簿操作。
- **风险**: 终局/回退 checkpoint 路径上 ACK 吞吐被序列化时间串行化；大状态作业的 checkpoint 时长尾部放大。无正确性后果（无环、重入安全）。
- **建议**: ①`triggerCheckpoint` 内先在锁内完成 inFlight 登记，`offerBarrier` 移到锁外（登记先行，拒绝时回滚 remove——与既有 133/143 行的回滚形状一致）；②或直通路径记录 WARN 以暴露回退频率。
- **失效面枚举**（F3 全同族登记）: TRK 全部公开方法 9 个均为 synchronized——`triggerCheckpoint`（**锁内重操作，本发现**）、`acknowledgeOperator`（锁内仅登记簿操作，回调在锁外 fire——模式正确）、`getCurrentCheckpointId`/`hasInFlightCheckpoints`/`getInFlightCheckpointIds`/`notifyCheckpointAborted`/`setChannelState`（轻量，OK）。同族对照：`CheckpointCoordinator` 已完成"登记在监视器内、I/O 交 persist 执行器"的改造（async persist 路径），TRK 未跟进同款。
- **信心水平**: 确定（锁内执行序列化是静态事实）/ P2 定级依据：可证明的争用热点，无数据损坏
- **误报排除**: 非"synchronized 使用本身"误报——报告的是互斥范围与重操作的复合；非冷路径——triggerCheckpoint 是每 checkpoint 必经入口，回退分支对应真实的终局快照场景。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C2-006] CachingTableLookup 首次初始化与 close 竞争可泄漏 refresh 调度器与工作线程组（closed 检查与资源创建非原子）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/lookup/CachingTableLookup.java:180-209, 274-292`
- **证据片段**:
  ```java
  private void ensureInit() {
      if (cache != null) return;
      synchronized (this) {
          if (cache != null) return;
          cache = new ConcurrentHashMap<>();      // ← cache 先发布（volatile）
          if (closed) return;                      // ← closed 检查点（此后不再复查）
          ...
          refreshPool = Executors.newFixedThreadPool(refreshMaxConcurrency, ...);
          ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(...);
          scheduler.scheduleWithFixedDelay(this::scanAndDispatch, refreshIntervalMs, ...);
          this.scanScheduler = scheduler;          // ← 创建发生在 closed 检查之后
      }
  }
  public synchronized void close() {               // 与 ensureInit 同一监视器，但时序交错仍可泄漏
      closed = true; scan.shutdownNow(); pool.shutdownNow(); map.clear();
  }
  ```
- **严重程度**: P2
- **并发场景**: 任务线程首查（`lookup→ensureInit`）与宿主函数关闭线程（`close`）。
- **触发时序**: T1（task 线程）在 `ensureInit` 通过 `if (closed) return` 检查（closed=false）→ T2（close 线程）执行完整 `close()`：`closed=true`，但此时 `scanScheduler`/`refreshPool` 字段仍为 null，`shutdownNow` 双双跳过 → T1 继续 `synchronized(this)`——**不重入冲突**（同锁会串行？不会：T2 的 close 已释放锁，T1 本就持锁在创建中；交错点是 T1 在检查后尚未赋值字段时 T2 抢入）——T1 创建并赋值 `refreshPool`/`scanScheduler`，无人再关闭 → `refreshMaxConcurrency` 个 worker + 1 个 scan 线程常驻（daemon 不阻 JVM 退出，但长驻进程内每次"首查×close 竞争"漏一组线程；`scanAndDispatch` 每周期空转一次，refreshPool 线程永久 idle-park）。
- **现状**: close 语义（"stops the pools, clears the cache"）与类注释声明一致，但初始化序列中"closed 检查 → 创建 → 赋值"三步非原子，close 的快照读取（`scan = scanScheduler`）取的是字段瞬时值。
- **风险**: 反复部署/热替换 lookup 函数的长驻进程内线程数缓慢增长；泄漏线程执行 `scanAndDispatch` 前的 `closed` 检查为空转（无功能危害），refreshPool 的 `execute` 永不再被调用。属关闭路径资源泄漏（F8 清理不对称变体）。
- **建议**: 创建完成后复查 `if (closed) { shutdown 两个池; return; }`（同锁内闭环）；或 close 后二次补偿：`ensureInit` 末尾再次读 volatile `closed` 并自清。
- **失效面枚举**（F3 全同族登记）: 该类"volatile 标志 + 锁内创建"资源共 2 组——`refreshPool`/`scanScheduler`（**同缺陷，一并修复**）；`cache`（先创建后检查 closed，泄漏仅为空 map，无害）。同族对照：`ProcessingTimeServiceDriver.start/shutdown` 的 stopped 检查在 synchronized(start) 内且启动后线程自检 volatile stopped 自行退出（自愈，无泄漏）；`StreamOpsHttpServer.start/stop` 均 synchronized（无窗口）。
- **信心水平**: 很可能（交错成立；窗口为微秒级，需 close 与首次 lookup 恰好并发）
- **误报排除**: 非"有界声明缓存"豁免项——泄漏对象是线程组而非缓存条目；非测试专用代码——close 是声明给生产 close 钩子/destroy-method 的路径。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C2-007] TwoPhaseCommitSinkFunction.finishCommit 的 contains→commit→remove 三步跨锁，重复提交依赖调用方串行化兜底

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:176-205`
- **证据片段**:
  ```java
  for (Map.Entry<Long, Object> entry : toCommit.entrySet()) {
      Long eid = entry.getKey();
      synchronized (pending) {
          if (!pending.containsKey(eid)) continue;   // 步骤① 锁内判存
      }
      commit(eid);                                    // 步骤② 锁外提交（可能秒级 I/O）
      synchronized (pending) {
          pending.remove(eid);                        // 步骤③ 锁内删除
      }
  }
  ```
- **严重程度**: P3（当前生产接线单线程执行体使其不可达；作为 C2-003 的联动缺口登记）
- **并发场景**: 正常路径仅 `checkpoint-commit-fwd-*` 单线程执行 `finishCommit`（retryFailedCommits 与当期通知同队列串行）——安全。仅当 C2-003 的 CallerRuns 饱和回退发生时，才有第二个线程（持 CC 监视器的完成线程）与 fanout 线程并发进入本方法。
- **触发时序**: T1 判存 `containsKey(eid)=true`（①，释放锁）→ T2 判存同 eid `=true`（①）→ T1 `commit(eid)`（②）→ T2 `commit(eid)`（②，重复提交）→ 各自 remove（③，幂等）。后果=同 epoch 重复 commit，正确性完全依赖 sink 幂等（JDBC 2PC ledger 幂等键有保障；file sink 依赖目标 move 原子性）。
- **现状**: 步骤①③各自持锁但②跨锁，注释未声明"finishCommit 不得并发"的守卫；`restoreFromEpoch`（250-274 行）同构（commit/abort 锁外 + 锁内 remove），恢复路径单线程，同依赖调用方串行。
- **风险**: 若调用方串行化假设被打破（正是 C2-003 场景），重复提交外溢到 sink 层；届时 file sink 若目标已存在同名提交产物，行为取决于各实现。
- **建议**: 把"判存→提交→删除"合并为单锁段（commit I/O 进锁段会让 pendingCommits 监视器覆盖 I/O——更差）；正解是保住调用方串行契约：修 C2-003（饱和不再回退到持锁线程）并在 finishCommit javadoc 显式声明单线程契约 + 入口加 `AtomicBoolean` 防重入守卫（fail-fast，对照 F10）。
- **失效面枚举**（F3 全同族登记）: 同一"锁外 commit + 锁内 remove"模式 2 处——`finishCommit`（176-205 行，**本发现**）、`restoreFromEpoch`（250-274 行，同构，恢复路径单线程）。兄弟入口 `saveState`（162-164 行）整段持锁拷贝，模式正确。子类覆盖面：FileTwoPhaseCommitSink/JDBC 2PC sink 的 `commit` 均经此基类循环，无自带并发守卫（依赖基类契约）。
- **信心水平**: 确定（三步跨锁是静态事实）/ 不可达性判断=很可能
- **误报排除**: 不按独立 P1 报"线程不安全"——生产线程图内单线程串行使交错当前不可达；按 F10 登记为"契约无运行时守卫"的 P3。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C2-008] ReaderChannel.drainPending 的批出队在监视器外执行，并发 deliver 线程可乱序投递 split 批

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/LocalSourceCoordinator.java:221-245`
- **证据片段**:
  ```java
  void deliver(List<T> splits) {
      pendingSplits.addAll(splits);   // 锁内（synchronizedList 自身锁）
      drainPending();
  }
  private void drainPending() {
      ...
      synchronized (pendingSplits) {
          toPush = new ArrayList<>(pendingSplits); pendingSplits.clear();   // 拷贝+清空：锁内
      }
      if (!toPush.isEmpty()) cb.accept(toPush);   // ← 回调在锁外执行
  }
  ```
- **严重程度**: P3
- **并发场景**: 两个 deliver 线程并存：初始分配由 boot 线程在 `enumerator.start`（ENUM 锁内）调用；后续分配由另一 subtask 的 task 线程经 `requestSplits→handleSplitRequest`（C2-001：无 ENUM 锁）调用——两条路径可并发进入同一 channel 的 `deliver`。
- **触发时序**: T1 拷贝+清空批 A（锁内）→ 释放锁，尚未 `cb.accept(A)` → T2 抢入锁，拷贝+清空批 B，`cb.accept(B)` 先行 → T1 随后 `cb.accept(A)` → reader 收到 B、A 乱序。后果取决于 reader 对 split 到达顺序的敏感度（目录枚举等 split 集合语义通常无序无害；若 split 携带位点前继关系则有损）。
- **现状**: 拷贝-清空在锁内（迭代安全达标），但"出队"与"投递"分离，无批序保证，注释亦未声明序契约。
- **风险**: split 批乱序；不丢批（每批整体投递）。对现有内置 source（file/batch-loader，split 无序语义）无害，构成对未来 order-sensitive source 的隐性契约缺口。
- **建议**: 若需保序：`drainPending` 在锁内仅拷贝、以 channel 级单线程执行器顺序回调；或在 javadoc 显式声明"批间无序"契约。
- **失效面枚举**（F3 全同族登记）: 同类"锁内出队、锁外回调"2 处——`ReaderChannel.drainPending`（本发现）、`SourceReaderOperator.flushPreOpenSplits`（300-309 行，同构；其仅 open() 单点调用故单线程，无害）。`pendingCount()` 锁内，OK。
- **信心水平**: 确定（乱序可交错）/ 影响判断=有趣的猜测（是否存在 order-sensitive source 未定）
- **误报排除**: 非迭代安全问题（拷贝在锁内达标）——报告的是批序契约缺口；降 P3 因当前无受害实现。
- **复核状态**: 未复核

## 已检查、无发现的重点项（防复核遗漏）

- `JobCoordinator.globalRecovery/activateAsLeader/deactivateToStandby`：recoveryLock 持有范围与 RPC fan-out 出锁顺序（R6 CC-01/CC-03）核验正确；`livenessLock` 的墓碑-merge 竞态关闭方案（REG-02）自洽；`barrierFanoutExecutors.computeIfAbsent`（CHM，每键单线程执行器）无重入。
- `CheckpointCoordinator` 异步 persist 设计：manifest 快照在监视器内、存储 I/O 与提交转发出监视器、失败/成功侧效果回锁内——注释与实现一致（CallerRuns 饱和缺口除外，见 C2-003）；`RetentionCleaner` 的 happens-before 推理（getOrCreate*Executor 的发布链）核验成立。
- `PendingCheckpoint` 状态机 CAS + synchronized 组合、`forceFail`/`abort` 的幂等与 future 完成语义——无发现。
- `TaskMailbox` 的 guarded wait（锁内复查队列与 closed）无丢通知窗口；close 后迟到 mail 至多被消费一次（无害）。
- `TaskProcessingTimeService`：register/cancel/fire 全在 this 监视器内且调用方为 task 线程单线程契约，scheduler 线程仅读 volatile due 标志——锁设计正确；`fireDueTimers` 锁内执行用户回调，但注册方同为 task 线程，无跨线程争用（回调期间 driver 线程不取该锁，仅投递 mail）。
- `TaskManager.deployTask` 的 `runningTasks.compute`：置换决策在 per-key lambda 内、cancel/permit 释放全部在 compute 之外——CHM bin 锁内无回调，模式正确。
- `LocalFileCheckpointStorage` RW 锁：全路径读写锁一致，无 read→write 升级嵌套（无自死锁）；读锁内反序列化 I/O 属维护/恢复冷路径（按误报校准不立项）。
- `JdbcClusterRegistry.ensureTables` / `JdbcCheckpointStorage.ensureTable/ensureEpochTable`：volatile + DCL + CREATE TABLE IF NOT EXISTS 多 JVM 容忍——启动路径，无发现。
- `StreamElementCodec.DECODED_VALUE_TYPES` / `MicrometerStreamTaskMetrics.TASK_HOLDERS` / `ChannelQueueGauges` / `DbBacklogGauges` 的 computeIfAbsent：lambda 无同 map 递归、无长 I/O（Class.forName 与 micrometer 注册为有界短操作），key 空间由部署拓扑枚举——无发现。
- `WindowedStreamImpl.getFactory` / `StreamExecutionEnvironment.discoverCheckpointExecutorFactory`：volatile + DCL / static synchronized，正确。
- `InMemoryMaterializationPoint`：全方法 this 监视器，无嵌套；无界 store 属 C5 维度不在本报告立项。
- `RemoteResultPartition`：gate 等待在 sendLock 外（WI6 声明语义）、锁内仅 backend send——锁序为叶子，无发现。

## 统计

- P0：0
- P1：2（C2-001 enumeratorLock 覆盖缺口；C2-002 split 投递判空-入队竞态致 split 滞留丢失）
- P2：4（C2-003 commit-fanout CallerRuns 拉回监视器；C2-004 registry 读路径绕过写锁〔理论风险，未具体化〕；C2-005 TRK 监视器内快照序列化；C2-006 CachingTableLookup init/close 竞争线程泄漏）
- P3：2（C2-007 finishCommit 三步跨锁〔不可达，联动 C2-003〕；C2-008 drainPending 批序无契约）

# C2 锁与同步原语 — nop-stream-core-runtime（深挖第 2 轮追加）

- 审计维度：C2（锁与同步原语）第 2 轮深挖，盲区定向：锁序图未覆盖的锁边、synchronized 集合迭代点补查、computeIfAbsent 持锁重逻辑补查、wait/await/poll 无超时等待点补扫（对照 F5）。
- 盲审纪律：本轮仅读取本维度第 1 轮文件（`C2-locks-sync.md`）与 live code；未读取 `ai-dev/audits/` 其他目录、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`。
- 方法：全量重扫 `synchronized` 命中文件清单（发现第 1 轮表外文件 `checkpoint/metrics/CheckpointMetrics.java`）、`Collections.synchronized*` 全量、显式锁/同步器全量（`ReentrantLock|ReadWriteLock|StampedLock|LockSupport|CountDownLatch|CyclicBarrier|Phaser|Exchanger|Semaphore|Condition`）、阻塞等待点全量（`wait|notify|await|awaitTermination|take\(\)|poll\(|Thread.sleep|join|parkNanos`）、`computeIfAbsent|compute|merge|putIfAbsent` 全量、以及第 1 轮未全文核读的 `ResultPartition`/`BufferPool`/`InputChannel`/`RecordReader`/`RemoteInputChannel` 读写链。

## 深挖第 2 轮追加

### [nop-stream-core-runtime-C2-009] CheckpointMetrics.snapshot() 的 synchronized 是装饰性锁——写方全部不持该监视器，多字段快照必然撕裂

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/metrics/CheckpointMetrics.java:111-122`（读侧）+ `39-43, 86-94`（写侧）
- **证据片段**:
  ```java
  public void updateLatestCheckpoint(long size, long duration) {   // 39 行：无 synchronized
      latestCheckpointSize.set(size);
      latestCheckpointDuration.set(duration);
      lastCheckpointTimestamp.set(CoreMetrics.currentTimeMillis());
  }
  public synchronized CheckpointMetricsSnapshot snapshot() {       // 111 行：取 this 监视器
      return new CheckpointMetricsSnapshot(
              numCompletedCheckpoints.get(), ... , failureCause);
  }
  ```
- **严重程度**: P3
- **并发场景**: ops HTTP 线程（`StreamOpsHttpServer.java:462/510-511` 调 `getMetrics().snapshot()`）与 checkpoint 完成/失败路径线程（CC 监视器内、persist 线程、ACK 线程调用 `incrementCompletedCheckpoints`/`updateLatestCheckpoint`/`recordFailure` 等）。
- **触发时序**: T1（ops 线程）进入 `snapshot()`，持 `this` 监视器，逐字段调用 `AtomicLong.get()` → T2（checkpoint 线程）执行 `updateLatestCheckpoint`/`incrementCompletedCheckpoints`（**不取该监视器**，无任何阻塞）→ T1 读到的字段组跨越 T2 的写入点 → 快照内部不一致（如 `numCompletedCheckpoints` 已含 N+1 而 `lastCheckpointDuration`/`lastCheckpointTimestamp` 仍是 N 号 checkpoint 的值）。`synchronized` 未排除任何并发写方。
- **现状**: `snapshot()` 用 `synchronized` 修饰制造"一致快照"的假象；类内全部 10 个写点（increment×3、updateLatestCheckpoint、addToTotalStateSize、recordDuration、recordStateSize、recordAborted、recordFailure、reset）均不持同一监视器，监视器实际未保护任何复合不变式。
- **风险**: 仅可观测性——ops 端点暴露的多字段值轻微不对应，无正确性后果；误信该锁的后续维护者可能在依赖"快照一致"假设上叠加逻辑。
- **建议**: 二选一：①去掉 `synchronized` 并在 javadoc 声明"逐字段原子、跨字段不保证一致"（与逐 AtomicLong 读的真实语义对齐）；②若确需一致快照，改为单一 `AtomicReference<MetricsState>` 整体 `set`/`get`（写方构造不可变状态对象原子替换）。
- **失效面枚举**（F3 全同族登记）: 本类写点全清单即上述 10 个，**全部**与 snapshot 监视器无互斥关系——缺陷覆盖整类而非单点；同族对照：`MicrometerStreamTaskMetrics` 的 gauge 为逐字段独立注册读取（未声明跨字段一致性），无此假象；`EngineMetrics`/`TaskNodeMetrics` 的 gauge 同为逐字段注册——同形类中仅 CheckpointMetrics 叠加了无效 `synchronized`。
- **信心水平**: 确定（锁与写方的互斥缺失是静态事实）
- **误报排除**: 非"synchronized 原语使用本身"误报——报告的是锁与其声称保护的复合读之间无互斥关系的装饰性使用；不升 P2——影响面限于运维观测字段，无状态损坏。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C2-010] JobCoordinator.stop() 关闭 barrierFanoutExecutors 与 fanoutExecutorFor 的 computeIfAbsent 存在创建-关闭竞态（逃逸执行器泄漏 + 已关闭执行器上 execute 抛 REE）

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:703-733`（stop 关闭窗口）、`2932-2940`（fanoutExecutorFor）、`2941-2956`（sendBarrierToAllTaskManagers）
- **证据片段**:
  ```java
  // stop()：718 cancelAllAssignedTasks → 722-733 关闭 fanout 执行器 → 749/753 才停调度器
  for (java.util.concurrent.ExecutorService ex : barrierFanoutExecutors.values()) {
      ex.shutdown();                                   // 724：只 shutdown，不清 map 条目
  }
  // 2932：创建侧不检查 running 标志
  private java.util.concurrent.ExecutorService fanoutExecutorFor(String nodeId) {
      return barrierFanoutExecutors.computeIfAbsent(nodeId,
              id -> java.util.concurrent.Executors.newSingleThreadExecutor(...));
  // 2945：execute 调用在 lambda 内 try 之外
  fanoutExecutorFor(nodeId).execute(() -> { long epoch = ...; try { ...triggerCheckpoint... } ... });
  ```
- **严重程度**: P3
- **并发场景**: 停机线程（`stop()`）与 periodic checkpoint 调度线程（`periodicCheckpointScheduler`，`scheduleWithFixedDelay`:1309，直到 753 行 `stopPeriodicCheckpoints()` 才停止）。
- **触发时序**: 变体①（逃逸关闭）：T1（stop 线程）完成 718 行 `cancelAllAssignedTasks`（best-effort RPC，可达秒级）进入 722-733 对 `values()` 迭代 shutdown；T2（调度线程）同期最后 tick 调 `fanoutExecutorFor` → `computeIfAbsent` 的创建 lambda 正在执行（执行器尚未入 map）→ T1 的迭代未命中该条目 → T2 创建完成并入 map → 该执行器永不被 shutdown（idle daemon 线程泄漏）。变体②（已关闭执行器上 execute）：T1 完成 733 行关闭循环后、753 行停调度器前的窗口内，T2 tick → `triggerCheckpoint` → `sendBarrierToAllTaskManagers:2945` 对已 shutdown 的执行器（map 条目仍在）`execute` → `RejectedExecutionException`（execute 调用在 lambda 内 try 之外，无人捕获）→ 异常进入调度任务 → `scheduleWithFixedDelay` 按 JDK 语义静默取消后续执行（F9 形态，停机上下文无实际受害）。
- **现状**: CC-06 注释声明"drain in-flight barrier/cancel fan-outs, then stop"，但创建侧（computeIfAbsent，不查 `running`）与关闭侧（迭代 values，不移除条目、无 happens-before）之间无协调；`running=false` 在 708 行已置位却无任何入口检查。
- **风险**: 变体①每命中一次泄漏 1 个 idle daemon 线程（长驻进程反复启停 job 时缓慢累积）；变体②产生未处理 REE + 调度任务静默取消——停机路径上无功能后果，但若未来 stop() 增加可恢复/重试语义，将留下永不恢复的僵尸调度。
- **建议**: ①`fanoutExecutorFor` 入口加 `if (!running) throw new StreamException(...)` fail-fast；②`stop()` 关闭循环后 `barrierFanoutExecutors.clear()`，创建侧在 computeIfAbsent 后复查 `running`，对新建执行器补 shutdown（与 C2-006 建议的"创建后复查关闭标志"闭环模式同构，一并修复）。
- **失效面枚举**（F3 全同族登记）: JobCoordinator 内 computeIfAbsent 创建资源共 1 处（`barrierFanoutExecutors`，本发现）；对照：CC 侧 `persistExecutor`/`commitFanoutExecutor`/`timeoutScheduler` 由 `CheckpointCoordinator.shutdown`（1582-1633）统一关闭且创建点为单线程 start 路径（无跨线程创建-关闭竞争）→ 无同族缺口；同型"创建-关闭竞争"已登记兄弟：C2-006（CachingTableLookup init/close，第 1 轮）。
- **信心水平**: 很可能（交错成立；变体②窗口由 cancelAllAssignedTasks RPC + 每 executor 5s awaitTermination 实际拉长到秒级，但停机上下文无害化）
- **误报排除**: 非"低频管理路径粗锁"豁免——报告的不是锁粒度而是资源创建与关闭循环的竞态（F8 清理不对称变体）；非纯理论——窗口有具体的秒级拉长机制，变体②的 REE 传播路径逐层可追溯。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C2-011] ResultPartition.drainBufferedElements 吞掉 EOS 哨兵且不归还，与兄弟实现 RemoteInputChannel.captureInFlightData 的"放回哨兵"防御不对称；无界 read() 消费者在 capture 后永久阻塞

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:534-556`（drain 吞哨兵）、`388-401`（无界 read）；对照 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java:427-444`
- **证据片段**:
  ```java
  // ResultPartition.drainBufferedElements（checkpoint 捕获线程）：
  while ((e = queue.poll()) != null) {
      if (e == END_OF_STREAM) {
          break;                        // ← 哨兵已被 poll 移除，不归还
      } ...
  }
  // 兄弟实现 RemoteInputChannel.captureInFlightData：
  if (e == END_OF_STREAM) {
      // Re-place the sentinel so a subsequent read still observes EOS.
      if (!queue.offer(END_OF_STREAM)) { flagOverflow("...could not re-place the EOS sentinel..."); }
      break;
  }
  ```
- **严重程度**: P3
- **并发场景**: checkpoint 捕获线程（unaligned capture → `InputChannel.captureInFlightData:112-114` → `partition.drainBufferedElements`）与消费任务线程（`partition.read()`/`read(timeout)`；类注释明言"drainBufferedElements runs on the checkpoint thread while the consumer reads"）。
- **触发时序**: T1（生产者线程）`close():451-464` 已将 EOS 哨兵入队（`finished=true`）→ T2（checkpoint 线程）`drainBufferedElements` 将哨兵 `poll` 出并 break（544-548 行，移除后丢弃）→ T3（消费线程）此后调用无界 `read():393` 的 `queue.take()` —— 队列永久空、无新元素到达，`finished` 标志在该 API 中无人再检查 → 永久阻塞。Gate 路径（`InputGate.readSingleChannel:624`/`readMultiChannel:761`）因超时轮询 + `isChannelEndOfStream`（`isFinished` volatile 标志）判定，不受哨兵丢失影响。
- **现状**: 同仓库兄弟实现 `RemoteInputChannel.captureInFlightData` 显式注释"Re-place the sentinel so a subsequent read still observes EOS"并验证 offer 落位（432-439 行），core `ResultPartition` 未做同款防御——同族协议两个实现不对称；无界 `read()` javadoc 亦未声明该交互。
- **风险**: 当前生产主路径无受害调用方（无界读链 `ResultPartition.read()` → `InputChannel.read():47` → `RecordReader.read():43` 在 main 源码中零生产实例化，`new RecordReader` 全仓无命中）——属公开 API 层的潜伏 F5 挂死缺口：任何未来消费者使用无界 `read()` 或 `RecordReader` 即触达永久阻塞，且该路径无 gate 路径的 channelTimeout 心跳再触发机制（比 gate 更弱）。
- **建议**: ①`drainBufferedElements` 对齐兄弟实现：观察哨兵后重新 `offer` 回并验证落位（失败时告警/计数，语义与 `flagOverflow` 对齐）；②或在无界 `read()` javadoc 显式声明"capture 后哨兵可能消失，阻塞读方必须使用有界重载 + `isFinished` 判定"，并将无界重载标记 deprecated。
- **失效面枚举**（F3 全同族登记）: 捕获路径哨兵处理 2 处——`ResultPartition.drainBufferedElements`（**吞哨兵，本发现**）、`RemoteInputChannel.captureInFlightData`（放回 + 验证，正确，可作修复模板）；无界读入口 3 个——`ResultPartition.read():388`、`InputChannel.read():47`、`RecordReader.read():43`（同属潜伏面，均无生产调用方）；gate 有界读 2 处（`readSingleChannel`/`readMultiChannel`）以 `isFinished` 判 EOS，不受影响。同类"volatile 标志兜底哨兵"设计：`close()` 先置 `finished` 后置哨兵（451-455 行）——gate 语义正确，勿在修复中破坏该顺序。
- **信心水平**: 确定（不对称与阻塞序列是静态事实）/ 受害面判断=很可能（当前无生产受害调用方，触发需未来新调用方）
- **误报排除**: 非"队列原语使用本身"误报——报告的是跨线程 EOS 哨兵协议在捕获路径上的丢失及同仓库内两个实现的防御不对称（F3）；非测试专用代码豁免——无界 `read()` 是 main 源码公开 API 而非测试简化实现；降 P3 因当前无可达受害路径。
- **复核状态**: 未复核

## 已检查、无发现的第 2 轮盲区（防复核遗漏）

- `BufferPool`（fair Semaphore）：`acquire()` 阻塞可中断、acquire 后复查 `closed` 并归还许可、`close()` 释放全容量唤醒阻塞生产者、背压阻塞为声明语义——无发现。
- `TaskManager.capacitySemaphore`：全部入口 `tryAcquire()` fail-fast（554/702 行），无阻塞 acquire 点——无发现。
- `CheckpointAckSender`：ACK 重试有界次数 + 线性退避 + 中断响应 + 失败打点（对照 F5）——无发现。
- `SupervisionLoop.waitForTerminal`（budget 有界 + zombie 超时抛错）、`EmbeddedDistributedExecutor.waitForCompletion` / `RpcDistributedExecutor.waitForCompletion`（deadline 有界 + 超时抛错）——无发现。
- `InputGate` 对齐状态族（`BarrierAlignment.receivedChannels/blockedChannels`、`abortedBarriers`、`inFlightAlignments`、`pendingBarrierEmissions`）：全部并发集，`abortBarrierAlignment` 的 add-first 时序有书面推理且成立（1195-1213 行）；`TaskCheckpointWiring:362` 的 abort 线程 `resumeConsumptionAll` 跨线程访问被并发集覆盖——无发现。
- F5 等待点全量补扫：所有 `awaitTermination` 均有超时（500ms-10s）；无界 `CountDownLatch.await()` 仅 `TaskManagerMain:178`/`JobCoordinatorMain:484` 主线程 shutdown-latch 惯用法（可中断、进程退出兜底）——按误报校准不立项。
- computeIfAbsent 全量补扫：`EngineMetrics.BY_JOB`/`TaskNodeMetrics.BY_NODE` 均有 `remove`+`removeFromRegistry` 配对；`SupervisionLoop:337`、`AssignmentPlanner:160`（recoveryLock 内）、`PollingJdbcMessageService:171`（CHM+COW）、恢复路径各 assembler/migration——lambda 均无重 I/O、无同 map 递归——无发现。
- `getPendingCommits()` 暴露面（live synchronizedMap 逃逸）：in-scope 调用点 `finishCommit:177`/`restoreFromEpoch:235` 均在 `synchronized(pending)` 内；connector 三子类（模块外顺带核）：Kafka 迭代在锁内（restoreFromEpoch）、File/JDBC 仅单键 get/put/remove——RL-4 迭代纪律达标，无发现。
- 2PC sink 锁序补查：`pendingCommits` 监视器 → `currentBuffer` → `MANIFEST_JVM_LOCKS` → FileLock 单向（commit I/O 在 pending 监视器外执行，无反向边，无环）——无发现。
- `SharedStateRegistryImpl` register/unregister 的 compute lambda：纯内存 refcount + entry 增删，无 I/O——无发现。
- `TwoPhaseCommitSinkFunction.pendingCommits` 非 final 字段被 `setPendingCommits` 替换（restore 路径）：新旧监视器更替仅发生在 task 恢复阶段（commit fanout 线程对同一 sink 实例无重叠生命周期），语义重叠部分已被第 1 轮 C2-007 的"调用方串行契约"条目覆盖——不重复立项。

## 统计

- P0：0
- P1：0
- P2：0
- P3：3（C2-009 装饰性 synchronized 撕裂快照；C2-010 fanout 执行器创建-关闭竞态；C2-011 EOS 哨兵捕获不对称 + 无界 read 潜伏挂死）

**结论：未发现新的高价值问题。深挖结束。**

## 维度复核结论

> 复核人：独立复核子代理（与初审无关）。方法：逐条直读全部被引文件与行号上下文 + 关键调用方追踪（枚举器投递线程模型逐实现核验、CC 监视器持有链核验、tick→fanout 调用链核验、`new RecordReader` 生产实例化清点）；本轮已解除盲审，历史归属参照 `ai-dev/audits/2026-09/`（09-26 深审、09-30 r4/r4 对抗、r2/r3）、`2026-10/`（r6 深审及 00-fix-of-fix、10-05/10-06 对抗）与同报告兄弟维度（C1/C3/C4）。未修改既有发现正文，未运行构建。

### [C2-001] → 已保留（P1）
- 锁覆盖缺口为静态事实：`registerReader→addReader`（:186）与 `ReaderChannel.requestSplits→handleSplitRequest`（:260）确在 `enumeratorLock` 外，`snapshotState`（:297）持锁，跨线程对（persist 线程 × task 线程）真实存在。
- CME 受害者在库内具体化为 `DebeziumTableSplitEnumerator`（`shareBySubtask` 为普通 `LinkedHashMap`，`addReader→deliverShare→remove` 结构性变更 vs `snapshotState` 遍历；该 Source 无 parallelism=1 门禁）；`snapshotSourceEnumerators` 逐 coord catch 仅 WARN 跳过（CheckpointCoordinator.java:1710-1721）核验属实。微调备注：Debezium 的 `handleSplitRequest` 是 no-op，CME 实际走失效面第④入口（addReader）；第⑤入口（handleSplitRequest）对在库 File 枚举器为 CHM 弱一致撕裂快照（无异常、后果降为恢复期可能重复分配）——不改变 P1 定级（静默持久化状态丢失类）。

### [C2-002] → 降级（P1 → P2「理论风险，未具体化」，与 C1-008 合并）
- 所述跨线程投递在库内不可达：逐一核验全部 4 个在库枚举器均只向"调用方自己的 subtask"投递——File `assignAvailableSplitsTo(subtaskIndex)` 只 `assignSplits(subtaskIndex,…)`、Message `deliverToken`、BatchLoader `assignIfNeeded`、Debezium `deliverShare` 同构；`enumerator.start()` 只发现不投递。故回调（`accept→getReaderRaw`）永远在 reader 所属 subtask 自己的 task 线程上执行，与 `open()` 的 reader 写/一次性 flush 严格同线程序，"子任务 1 线程读子任务 0 的陈旧 null reader"这一 T2 不存在。
- 残余真实面：SPI 未禁止自定义枚举器跨线程调 `assignSplits`（`SplitEnumeratorContext` 无线程契约），届时协议确实竞争——这正是同报告 C1-008 已按 P2「理论风险，未具体化」登记的同一缺陷。按误报校准降 P2 并标注，合并裁定应并入 C1-008，不重复计数。

### [C2-003] → 已保留（P2）
- 监视器持有链核验属实：`onCompletePersistSuccess` 在 `synchronized(this)` 内被调（:732；同步路径经 `completePersistSynchronously`:678、`completePendingCheckpoint`:614），:1002/:1059 的 `execute` 在持 CC 监视器线程上；执行器 LBQ(1000)+CallerRuns（:1086-1095）、coordinator 侧无饱和计数（对照 TM 侧 `commitCallerRuns.total`）均属实。
- 与"NOT under this monitor"注释（:994-1000）的矛盾成立。现状归属：该执行器是 R6 CC-01（plan 2326）修复的产物，本条是其 CallerRuns 回退残余——新发现；同报告 C3-003 为同一缺陷的跨维度重复登记（C3 文内已自标"与 runtime 侧合并"），合并裁定应并入，另 R4 04-concurrency-resource.md:416 曾建议 CallerRuns 形状须配饱和打点。

### [C2-004] → 已保留（P2，原文已自带「理论风险，未具体化」标注）
- 同步纪律缺口属实：`assignTask` 写侧持 `taskAssignmentHistory` 监视器（:151-166），`getTaskAssignment`（:172-178）/`getAttemptHistory`（:181-187）读侧无锁，违反"synchronized 字段迭代点必须在锁内"门禁先例。
- 线程图核验支持其自降级：`getAttemptHistory` 在 main 源唯一调用点为 `seedAttemptCountersFromRegistryLocked`（JobCoordinator.java:2284，javadoc 明示须持 recoveryLock）；`getTaskAssignment` main 源零调用点。生产无具体交错，P2 底线级别恰当。现状：新发现（r4 仅对 InMemoryClusterRegistry 做过"结构核对"，无在先登记）。

### [C2-005] → 已保留（P2）
- 核验属实：`triggerCheckpoint` 全程 `synchronized`（:111），`SourceReaderOperator.offerBarrier` 的 finished/mailbox==null 回退分支在调用方（持 TRK 监视器）线程上直接 `injectBarrier→snapshotState` 全量序列化（SourceReaderOperator.java:523-554），`acknowledgeOperator` 亦 synchronized（:159）被阻塞整个快照时长；mailbox 正常路径仅入 mail、监视器内无重操作——影响面确限于回退路径，争用热点定 P2 恰当（无正确性后果）。
- 现状：新发现（历史相邻项 2026-09-26 03-performance.md:48 登记的是同类的 `acknowledgeOperator` synchronized 内深拷贝 P3 冷路径，方法与侧面不同，非重复）。

### [C2-006] → 驳回
- 所述交错违反监视器互斥语义，不可达：`ensureInit` 的 `if (closed)` 检查与两池创建/赋值全部在 `synchronized(this)` 临界区内（:184-208），`close()` 亦为 `synchronized(this)`（:275）——T2 无法在 T1"检查 closed 之后、字段赋值之前"抢入；要么 close 先完整执行（T1 在同锁内的 closed 检查处返回，池根本不创建），要么 close 后行（经同一监视器看到非 null 字段并 shutdown）。且 `cache/scanScheduler/refreshPool/closed` 全部 volatile（:79-82）。
- 原文自身的犹疑（"同锁会串行？不会"）推理有误——同锁临界区必然串行，T2 不能抢入 T1 持有的监视器。属"已声明边界 + 同锁内 fail-fast 守卫"的正确实现，非缺陷。

### [C2-007] → 已保留（P3）
- 三步跨锁（contains→commit→remove，:190-200）与 `restoreFromEpoch` 同构（:250-274）均为静态事实；`saveState` 锁内防御拷贝（:162-164）模式正确。
- 微调备注：原判"不可达"略窄——TM 侧 commitExecutor（队列 64 + CallerRuns + 计数，plan 369 R5-CC-10 已裁定形状）饱和回退时，派发线程可与执行器线程并发进入同一 sink 实例的 `finishCommit`；但后果仍由 sink 幂等（JDBC ledger / file move）兜底，P3 定级维持。现状：新发现（C2-003 的联动缺口；2026-09-26 P1"提交阻塞派发线程"是另一缺陷且已由 commit 执行器修复，非本条的在先登记）。

### [C2-008] → 已保留（P3）
- "锁内拷贝-清空、锁外回调"代码形（:229-245）属实，批间无序契约缺口成立。
- 补充核验：受 C2-002 复核同款的"自投递"纪律约束（4 个在库枚举器均向调用方自身 subtask 投递），单 channel 的 `deliver` 在库内实为单线程，乱序交错与 C1-008/C2-002 同属"跨线程枚举器"假设下的潜伏面——P3「无受害实现」的契约缺口定级维持。现状：新发现。

### [C2-009] → 已保留（P3）
- 核验属实：`snapshot()` 是该类唯一 synchronized 成员（:111），全部 10 个写点（:27-109）均不持该监视器——锁为装饰性，多字段快照必然可撕裂，仅影响 ops 可观测面，P3 恰当。
- 现状：新发现（历史仅 4.3-giant-files 将 CheckpointMetrics 记为卫星类职责拆分，无锁相关在先登记）。

### [C2-010] → 已保留（P3，机理修正后维持）
- 结构缺口属实：`fanoutExecutorFor` 的 `computeIfAbsent`（:2933）与 `stop()` 的 `values()` 迭代 shutdown（:723-734）之间无 clear()、无 running 复查、无 happens-before——创建-关闭竞态类成立，后果限于停机上下文（泄漏 idle daemon 线程 / 一次已捕获异常），P3 恰当。
- 机理修正（不改定级）：①周期 tick 的 fan-out 实际在 `triggerCheckpoint` 内联处 :983（:2941-2956 的 `sendBarrierToAllTaskManagers` 是终局 savepoint 变体，同一 executor map）；②`running/active` 为 volatile 且在 stop() 开头（:706-708）置位，:938/:943 的门在 fan-out 之前——stop 后新 tick 到不了 fan-out，变体②需在途 tick 横跨秒级 `cancelAllAssignedTasks`+`awaitTermination` 窗口滞留在门与 execute 之间；③REE 会被 tick lambda 的 `catch (Exception)`（startPeriodicCheckpoints 内）捕获并 WARN + 计数，原文"scheduleWithFixedDelay 静默取消（F9 形态）"的后果链不成立。
- 现状：新发现（同执行器族的 C4-005 为无界队列缺陷、C4-012/013 为 TM 侧拒绝策略与 persistExecutor 惰性初始化，均不同缺陷非重复；执行器本体系 plan 2435 CC-06 引入）。

### [C2-011] → 已保留（P3）
- 不对称属实：`ResultPartition.drainBufferedElements` 吞哨兵不归还（:544-548），`RemoteInputChannel.captureInFlightData` 放回并校验（:430-439）；无界 `read()` 的 `take()` 无 finished 兜底（:388-401）。
- 受害面核验支持 P3：`new RecordReader` main 源零命中，无界读链 `InputChannel.read()`（:48）仅 RecordReader 消费；gate 路径走有界读 + `isFinished` 判定；`drainBufferedElements` 另两处生产调用（SupervisionLoop:1085/1089）为旧分区置换场景、无捕获后无界读。现状：新发现（相邻历史面：2026-09-30 对抗审记过 drain 丢 Watermark 面、r6 CC-07 记过 close() 非幂等双哨兵——均不同缺陷）。

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状标注 |
|---|---|---|---|---|
| C2-001 | P1 | nop-stream-core/.../source/coordinator/LocalSourceCoordinator.java:176-191,254-267,296-314 | enumeratorLock 未覆盖 addReader/handleSplitRequest 两个变更入口，persist 线程 snapshotState 与 task 线程并发访问同一枚举器（在库 CME 受害者：Debezium 枚举器 LinkedHashMap）→ 快照静默缺失/撕裂 | 新发现 |
| C2-002 | P2（降级，理论风险未具体化） | nop-stream-core/.../operators/SourceReaderOperator.java:220-234,242-263,297-334 | 回调判空-入队跨监视器 + reader 非 volatile + 一次性 flush：库内因"自投递单线程"不可达，仅跨线程枚举器假设下可滞留 split | 已知未修复——同报告 C1-008 同缺陷（跨维度重复登记，按合并裁定并入 C1-008） |
| C2-003 | P2 | nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:994-1013,1086-1095 | commit-fanout 执行器 CallerRuns 饱和时把 sink 提交 I/O 拉回 CC 监视器内并打破串行提交契约，无饱和打点 | 新发现（R6 CC-01/plan 2326 修复的残余缺口；与同报告 C3-003 同缺陷重复，按合并裁定并入） |
| C2-004 | P2（理论风险未具体化） | nop-stream-runtime/.../cluster/InMemoryClusterRegistry.java:151-187 | taskAssignmentHistory 写侧持锁、读路径（getTaskAssignment/getAttemptHistory）不加锁迭代，门禁违规；生产线程图内被 recoveryLock 串行 | 新发现 |
| C2-005 | P2 | nop-stream-core/.../execution/CheckpointBarrierTracker.java:111-151（+SourceReaderOperator.java:523-569） | triggerCheckpoint 持监视器直通 injectBarrier（finished/mailbox 缺失回退路径），快照序列化在锁内串行化 ACK | 新发现 |
| C2-007 | P3 | nop-stream-core/.../common/functions/sink/TwoPhaseCommitSinkFunction.java:176-205,234-277 | finishCommit/restoreFromEpoch 的判存→提交→删除三步跨锁，重复提交依赖调用方串行化 + sink 幂等兜底，无运行时守卫 | 新发现（C2-003 联动缺口） |
| C2-008 | P3 | nop-stream-core/.../source/coordinator/LocalSourceCoordinator.java:221-245 | ReaderChannel.drainPending 出队与投递分离，锁外回调无批序契约（库内单线程投递下不可达，未来 order-sensitive/跨线程 source 的隐性缺口） | 新发现 |
| C2-009 | P3 | nop-stream-runtime/.../checkpoint/metrics/CheckpointMetrics.java:39-122 | snapshot() 的 synchronized 是装饰性锁（全部 10 个写点不持该监视器），多字段快照必然撕裂，仅可观测性影响 | 新发现 |
| C2-010 | P3 | nop-stream-runtime/.../coordinator/JobCoordinator.java:703-733,937-995,2932-2956 | barrierFanoutExecutors 创建（computeIfAbsent）与 stop() 关闭迭代无协调：逃逸执行器泄漏 + 已关闭执行器 REE（tick catch 兜底，停机上下文无害） | 新发现（机理修正见复核条目） |
| C2-011 | P3 | nop-stream-core/.../execution/ResultPartition.java:388-401,534-556（对照 runtime/.../transport/RemoteInputChannel.java:427-444） | drainBufferedElements 吞 EOS 哨兵不归还，与 RemoteInputChannel"放回哨兵"防御不对称；无界 read() 在捕获后永久阻塞（当前无生产受害调用方） | 新发现 |

> 复核裁定汇总：11 条中已保留 9（C2-001/003/004/005/007/008/009/010/011）、降级 1（C2-002 → P2 理论风险，并入 C1-008）、驳回 1（C2-006，交错时序违反监视器互斥不可达）。最终 P0=0、P1=1（C2-001）。现状标注：新发现 9、已知未修复（同报告 C1-008 重复）1、已修复 0（全部发现对照 HEAD 4d522b00ae 的 live code 核验，无一已被修复）。
