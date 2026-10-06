# 维度 04：并发与资源管理

## 审计范围与方法

- **范围**：`nop-stream-runtime`（JobCoordinator 2866 行、CheckpointCoordinator 1822 行、SupervisionLoop 1326 行、GraphModelCheckpointExecutor 1625 行、TaskManager/RunningTask/CheckpointAckSender、AssignmentPlanner、TaskCheckpointWiring、PendingCheckpoint/RetentionCleaner、RemoteInputChannel/RemoteResultPartition、JdbcLeaderElector、StreamOpsHttpServer、EngineMetrics）、`nop-stream-core` execution（InputGate 1301 行、StreamTaskInvokable 1200 行、ResultPartition/RecordWriter/BufferPool/TaskMailbox/MailboxExecutor/TaskExecutor/SubtaskTask/ProcessingTimeServiceDriver、SourceCoordinatorRegistry）、`nop-stream-rocksdb`（RocksDBKeyedStateBackend、RocksDBIncrementalSnapshotStrategy）。
- **方法**：通读上述文件全文（HEAD live code），按 6 个子维度（线程生命周期对称性 / 锁与活性 / 状态机原子性 / 资源泄漏 / 异常路径 / 同类模式深挖）逐项核对；竞态类发现均写出具体交错时序；只审计当前代码，未假设任何历史缺陷残留。
- **明确核对过且未构成发现的点**（避免后续重复审计）：InputGate 的 multi-epoch 对齐状态（ConcurrentHashMap + add-first abort 序 + 单消费者 ArrayDeque，正确）；TaskManager 槽位原子 put 位移 + `semaphoreReleased` CAS + RunningTask 条件 remove（REG 修复在位）；SubtaskTask 状态机全 CAS；JobCoordinator `recoveryPending` CAS 去重 + 外层 finally 清旗标；RunningTask 成功完成保留注册表项的尾提交窗口；ResultPartition 四象限背压（permit 泄漏路径均有归还）；BufferPool close 唤醒；TaskMailbox 双优先级队列；ProcessingTimeServiceDriver 单次启动 + interrupt 停止；RunningTask cancel 的 mailbox+interrupt 双通道；CheckpointAckSender 有界重试；JdbcLeaderElector CC-15 epoch 计数器行（CAS 分配 + advanceCounterPast）；RocksDB 后端单线程（mailbox 模型）假设 + close 顺序（gauge 先于 native handle 分离）；StreamOpsHttpServer 全入口鉴权；所有文件流均在 try-with-resources 中。
- **总体结论**：live HEAD 上未发现 P0/P1。R5 修复批次覆盖的并发活性面（空工作集哨兵、deploy 宽限期、COMPLETED 心跳墓碑、attempt 拒收、commit 队列有界化）在 live code 中均已核实到位。本轮保留 8 项：P2×4（均为结构性并发缺陷，窗口窄或有界但真实）、P3×4。

---

## 第 1 轮（初审）

### [CC-01] CheckpointCoordinator 监视器锁内执行阻塞 RPC fan-out（commit forwarder / 分布式 abort handler），并被 globalRecovery 的 recoveryLock 放大
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:1466-1478, 721-723, 989, 1062-1115, 1210-1216`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:1346-1388, 1772-1773, 2536-2581`
- **证据片段**:
  ```java
  // CheckpointCoordinator.executePersistAsync —— 持 monitor 调成功回调
  synchronized (this) {
      onCompletePersistSuccess(completed, pending, true);   // :721-723
  }
  // onCompletePersistSuccess → 持 monitor 逐个同步调 participant.finishCommit
  for (int i = participants.size() - 1; i >= 0; i--) {
      participant.finishCommit(checkpointId, success);      // :1466-1478
  // JobCoordinator 注册的 distributed commit forwarder —— finishCommit 内逐节点阻塞 RPC
  rpc.notifyCheckpointComplete(epochId, epoch);             // JobCoordinator:1367
  // globalRecovery 在 recoveryLock 锁内 abort 全部 pending → 触发同一把 monitor 下的 abort handler
  checkpointCoordinator.abortAllPendingCheckpoints(         // JobCoordinator:1772-1773
          "global recovery #" + newCount + " ...");
  ```
- **严重程度**: P2
- **现状**: `completePendingCheckpoint`/`onCompletePersistSuccess`/`abortPendingCheckpoint` 均在 `synchronized(this)`（coordinator monitor）内同步调用 participant 回调；分布式部署形态下这两个回调分别是逐节点 `notifyCheckpointComplete` 与逐节点 `cancelTask` 的**阻塞 RPC fan-out**（`AssignmentPlanner` 与 `JobCoordinator` 类注释明确承诺"RPC fan-out 在锁外执行，避免跨 N×TaskManager 阻塞 IO"，但 commit-forward 与 abort 路径违背了这一不变式；`abortAllPendingCheckpoints` 又被 `globalRecovery` 在持有 `recoveryLock` 的临界区内调用（JobCoordinator:1714 锁入 / 1772 abort / 1787 解锁），形成 recoveryLock + monitor 双重持有的阻塞 IO）。
- **风险**: 交错时序——线程 A（persist executor）在 `onCompletePersistSuccess` 持 monitor 并阻塞在节点 K 的 `notifyCheckpointComplete`（K 慢/不可达，RPC 超时 T 秒）；线程 B（TM 的 ACK）到达 `collectAck`→`acknowledgeTask`（synchronized）被阻塞整个 T；TM 侧 ACK 只重试 3×200ms 后放弃，epoch N+1 因此注定 timeout-abort；若同时有线程 C 在 `globalRecovery` 的 `abortAllPendingCheckpoints` 里做 cancelTask fan-out，则 `assignTasks`/`activateAsLeader` 也被 recoveryLock 阻塞。后果：任一节点变慢期间所有 checkpoint 完成路径串行化、周期性 checkpoint 连锁 abort、恢复驱动卡顿——均为有界停顿（RPC 超时封顶）而非死锁（已核实 TM 侧 `cancelTask`/`notifyCheckpointComplete` handler 不会同步回调需要这两把锁的路径；cancel 的任务线程因 `canceled=true` 不回发 FAILED report）。
- **建议**: 与 assignment fan-out 同构化——abort/commit 回调只把 (checkpointId, success, 目标节点快照) 入队，由独立 executor（或 persist executor 的兄弟池）在锁外做 fan-out；`globalRecovery` 的 `abortAllPendingCheckpoints` 移到 `recoveryLock` 释放之后（其入参 reason 与新 epoch 均可在锁外构造；abort 本身有 RUNNING→ABORTED CAS 幂等保护，锁外执行不破坏语义）。
- **信心水平**: 确定（调用链与锁序均为 live code 直读；"有界停顿非死锁"已通过核对 TM handler 无同步回调路径排除死锁变体）
- **误报排除**: 常见误报是"monitor 内有 I/O 但被 async 化遮蔽"——此处不成立：`executePersistAsync` 只把**存储 I/O** 移出了 monitor，而 participant 回调（`notifyParticipantsFinishCommit`）被显式留在 `onCompletePersistSuccess` 内，而该回调在分布式形态下就是 RPC fan-out 本体；也不是"fan-out 有 per-node try/catch 所以无害"——containment 只防异常传播，不缩短单节点阻塞时长。
- **复核状态**: 未复核

### [CC-02] TaskManager.deployTask/receiveAssignment 的 fencing 校验与槽位安装之间存在 check-then-act 窗口，迟到的旧代 deploy 可顶替并取消新代任务
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:594-599, 642-660, 935-956`（`receiveAssignment` 同构：469-476, 511-515）
- **证据片段**:
  ```java
  long activeEpoch = currentFencingEpoch.get();          // :594 校验点
  if (activeEpoch != fencingEpoch) { throw ... }         // :595-599 之后可能被调度走
  ...
  RunningTask existing = runningTasks.put(taskKey, runningTask);  // :649 安装点（无再校验）
  if (existing != null) {
      existing.cancel();                                  // :653 顶替：无条件取消被位移者
      if (existing.semaphoreReleased.compareAndSet(false, true)) {
          capacitySemaphore.release();
      }
  }
  ```
- **严重程度**: P2
- **现状**: epoch 校验（594）与槽位安装（649）之间无原子性；`put` 位移语义会**无条件 cancel 被位移者并归还其许可**，不区分被位移者是旧代僵尸还是当前代健康任务。
- **风险**: 交错时序——线程 T1（上一代 fan-out 迟到的 `deployTask(E1)`，消息后端队列滞留/传输层重试）通过 594 校验（此刻 `currentFencingEpoch==E1`）后停顿；线程 T2 执行 `updateFencingToken(E2)`：`getAndSet` + `removeIf(oldEpoch==E1)`（此时 T1 尚未 put，removeIf 扑空）；线程 T3（本轮恢复的 `deployTask(E2)`）完成安装，E2 健康任务运行中；T1 恢复执行 649 的 `put` → `existing` = E2 健康任务 → **健康任务被 cancel、E1 僵尸成为常驻槽主**（僵尸占一个 slot 许可，心跳被 coordinator 的 attempt 拒收逻辑排除，触发侧 barrier 也因其 `getFencingEpoch()==E1` 被跳过）。系统只能靠"无 liveness 记录超 deploy 宽限期 → 部署失败 → 再一轮恢复（E3 deploy 顶替僵尸）"自愈，代价是白烧一次恢复预算、期间该 subtask 数据面停滞。同类窗口在 `receiveAssignment`（containsKey 检查 492 + putIfAbsent 511 之间）同样存在，但其 putIfAbsent 不顶替、危害较小。
- **建议**: 安装点再校验：`put` 前后核对 `currentFencingEpoch`（或 `runningTask.getFencingEpoch() == currentFencingEpoch.get()` 不成立则走 rejectDeploy 清理路径）；或把位移分支改为"被位移者 epoch == 当前 epoch 时拒绝安装新任务并记 typed 失败"。
- **信心水平**: 很可能（窗口真实存在且 `put` 位移会把危害从"僵尸旁路"放大为"健康任务被杀"；触发依赖旧 deploy RPC 跨越整个轮换窗口在途，概率低但消息后端排队即可满足）
- **误报排除**: 不是"check-then-act 无害因为最终一致"——R5 已用原子 `put` 位移修掉了 get→remove→put 窗口，但位移方向未做 epoch 再判定，恰好在 fencing 边界引入了本条新窗口；也不是"fencing 已经挡住 stale deploy"——fencing 只挡"校验时已轮换"的迟到的消息，挡不住"校验后、安装前"轮换的在途调用。
- **复核状态**: 未复核

### [CC-03] activateAsLeader 在 recoveryLock 外先置 active=true / recoveryGen=0，与同实例重授权窗口内的并发 globalRecovery 交错可使 fencing epoch 回退
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:2119-2126, 2134-2165`（对照 `globalRecovery` 锁内轮换 1713-1788、`detectFailures` 触发 1522-1539、`deactivateToStandby` 不清工作集 2263-2275）
- **证据片段**:
  ```java
  this.currentLeadership = epoch;        // :2122 ┐ 锁外
  this.recoveryGen.set(0);               // :2123 │ 无同步保障
  // Mark active BEFORE rebuilding ...
  this.active = true;                    // :2126 ┘ 已对 detectFailures 可见
  ...
  recoveryLock.lock();                   // :2135 之后才进入互斥区
  try {
      this.active = true;                // :2141（R5 注释称"rotation 失败回滚"）
      rotateFencingEpochCoreLocked(token, FencingRotationCause.LEADERSHIP_GRANT); // :2145
  ```
- **严重程度**: P2
- **现状**: 激活序的三次写（leadership/`recoveryGen=0`/`active=true`）发生在获取 `recoveryLock` 之前；`detectFailures` 只以 `volatile active` 为门，因此在 2126–2135 窗口内探测器即可见"active=true + 上一代残留工作集"。
- **风险**: 交错时序（同 JVM 实例失去又重获领导权，`everAssigned=true` 且 `taskAssignmentMap`/`subtaskLiveness` 仍持上一代残留）：线程 T1（选举回调）执行到 :2126（active=true）尚未拿到锁；线程 T2（failureDetector 周期 tick）此刻进入 `detectFailures`，读 active=true 通过，且检测到残留 assignment 的节点租约过期/任务 stall → `requestRecovery`（recoveryPending 此刻为 false，CAS 成功）→ `globalRecovery` 先拿到 `recoveryLock`，`recoveryGen` 0→1，epoch = L*SCALE+1，轮换/重派完成并释放锁；T1 随后拿锁执行 :2145，`token = L*SCALE+0` → `fencingEpoch.set` **回退**。TM 侧 `updateFencingToken` 会以 rollback 拒绝（被 per-node catch 吞掉并继续），但 coordinator 自身 epoch 已低于 TM：后续 `triggerCheckpoint(E+0)`/`deployTask(E+0)` 全部 mismatch 拒收并触发 FAILED report → 再一轮恢复才收敛。结果：重授权场景下 fencing 单调性被打开一个真实窗口，以额外恢复风暴为代价自愈。
- **建议**: 把 `currentLeadership/recoveryGen/active` 的写入全部移入 `recoveryLock` 临界区（`active=true` 本就已有锁内副本 :2141；锁外的 2126 可改为锁内设置，注释所述"让 prepareAssignmentsLocked 观察 active"在锁内天然成立）；或在 `rotateFencingEpochCoreLocked` 前增加 `newEpoch <= fencingEpoch.get()` 的单调断言（违反则跳过轮换并走一次真实 recovery）。
- **信心水平**: 很可能（各步骤均为 live code 直读；触发要求探测器 tick 恰落入 9 行窗口且确有可检出的失败，概率低但 standby→leader 的重授权正是该路径的设计场景）
- **误报排除**: 不是"volatile 写顺序无关紧要"的通用担忧——问题不在可见性而在**锁外写与锁内写跨越互斥边界形成可观测中间态**（:2126 的注释自己也承认激活要先于重建可见）；也不是 duplicate-activation guard 能挡——guard（:2113）比对的是 `currentLeadership`，而 T2 的 globalRecovery 不改该字段，guard 判定与 epoch 回退正交。
- **复核状态**: 未复核

### [CC-04] SourceCoordinatorRegistry 以裸 vertexId 为进程级静态键，TaskManager/远程路径无任何注销点：跨任务泄漏 + 同 JVM 多作业串台
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/SourceCoordinatorRegistry.java:33-40`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/SourceReaderOperator.java:170-180`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java:598-605`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:310-340`（stop 无注销）、`RunningTask.java:124-182`（finally 无注销）
- **证据片段**:
  ```java
  // SourceCoordinatorRegistry —— 键只有 vertexId，类注释自认 "jobId+vertexId ... (not in v1)"
  private static final ConcurrentMap<Integer, LocalSourceCoordinator<? extends SourceSplit, ?>> COORDINATORS =
          new ConcurrentHashMap<>();
  // 唯一注销点在嵌入式本地执行路径：
  private void shutdownLocalExecution(TaskExecutor executor, GraphExecutionPlan plan) {
      ...
      io.nop.stream.core.source.coordinator.SourceCoordinatorRegistry.unregister(t.getId());  // StreamExecutionEnvironment:602
  ```
- **严重程度**: P2
- **现状**: FLIP-27 source 协调器注册进 JVM 级静态 map（键为 `vertex-N` 整数）。嵌入式路径在 `shutdownLocalExecution` 注销；而分布式路径（`TaskManager.stop()`、`RunningTask.run` finally、`StreamTaskInvokable` close 链、`SourceReaderOperator.close`）**均无注销调用**（已全库 grep 核实：unregister 在 main 代码中只有 :602 一处）。
- **风险**: (a) **泄漏**：TM JVM 每跑完一个含 source-api 顶点的作业，其 `LocalSourceCoordinator`（持有 enumerator，CDC 类 enumerator 持外部连接）连同静态条目永久滞留，直到进程退出；(b) **串台**：`TaskManager` 按 `jobId/vertex/subtask` 键控、结构上支持同 JVM 多作业/先后作业，两个作业的 "vertex-1" 在静态 map 中碰撞——后注册者 `registerIfAbsent` 拿到前一个作业的 coordinator/enumerator（split 状态跨作业串染，且旧 coordinator 从未 close）；(c) 恢复重部署同作业时共享旧 coordinator 可能是**有意设计**（split 连续性），但该设计与 (a)(b) 共用同一无生命周期机制，无法区分。
- **建议**: 注册键升级为 `(jobId, vertexId)`（类注释已预留该方向）；在 TM 侧任务退出路径（`RunningTask.run` finally 或 `OperatorChain.close`）做引用计数式注销，至少在 `TaskManager.stop()` 补一个该 jobId 的清扫。
- **信心水平**: 确定（泄漏与无注销为代码事实；"多作业同 JVM"是否为受支持形态影响 (b) 的实际命中面，故整体不评 P1）
- **误报排除**: 不是"静态缓存泄漏但进程短命所以无所谓"——TaskManager 是常驻节点进程，且 `TaskManager.stop()` 明确支持节点级重启复用（metrics 都做了 `releaseNode`，唯独此处没有对应清理）；也不是"region restart 需要保留 coordinator 所以不能清"——同作业重部署场景需要的是 (jobId, vertexId) 键下的保留，与跨作业串台/进程级泄漏并不冲突。
- **复核状态**: 未复核

### [CC-05] StreamOpsHttpServer.stop() 不关闭 setExecutor 注入的线程池，每次 start/stop 周期泄漏 4 个空闲线程
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/StreamOpsHttpServer.java:109-111, 162-172`
- **证据片段**:
  ```java
  httpServer = HttpServer.create(new InetSocketAddress(config.getBindAddress(), config.getPort()), 0);
  httpServer.setExecutor(Executors.newFixedThreadPool(4,          // :110-111 引用未保存
          NopStreamThreadFactory.named("stream-ops-http")));
  ...
  public synchronized void stop() {                                // :162-172
      if (httpServer != null) {
          httpServer.stop(0);            // 停止分发，但 executor 线程池不随其关闭
          httpServer = null;
      }
  ```
- **严重程度**: P3
- **现状**: `HttpServer.stop(0)` 只停止分发，不会关闭调用方通过 `setExecutor` 注入的 ExecutorService；该池的引用也未保存，`stop()` 无法补关。
- **风险**: 每次 `start()`→`stop()` 循环遗留 4 个永久 idle 的 daemon 线程；`OpsJobManager`/运维工具反复重建 ops server 的场景下线性累积（daemon 不阻 JVM 退出，但占内存与线程表，且 `stream-ops-http-N` 线程名会混淆 threaddump 诊断——该 server 本身就提供 /threaddump）。
- **建议**: 保存 executor 引用，`stop()` 中 `executor.shutdown()` + 短 `awaitTermination`（对齐本模块其它 executor 的关闭纪律）。
- **信心水平**: 确定（JDK HttpServer 契约明确：注入的 executor 由调用方负责生命周期）
- **误报排除**: 不是"daemon 线程所以泄漏无害"的误报套路——本条按 P3 定级正是因为它有界且不阻退出；列出它是因为同模块所有其它线程池（persist/retention/heartbeat/commit）都实现了完整 shutdown+await 纪律，此处是唯一缺口。
- **复核状态**: 未复核

---

## 深挖第 2 轮追加

### [CC-06] EngineMetrics.registerNodesActiveGauge 的强引用 supplier 永不移除：JobCoordinator.start 泄漏一个持有 clusterRegistry 的状态引用
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/metrics/EngineMetrics.java:144-152, 100-116`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:1914-1920, 724`
- **证据片段**:
  ```java
  public static void registerNodesActiveGauge(MeterRegistry registry, Supplier<Number> supplier) {
      // retain the state object strongly (weak-gauge semantics — see field javadoc)
      GAUGE_STATE_REFS.add(supplier);                    // :148 只增
      registry.gauge(METRIC_NODES_ACTIVE, supplier, s -> s.get().doubleValue());
  }
  private void removeFromRegistry() {                    // releaseJob 路径
      GAUGE_STATE_REFS.remove(latestSizeBytes);          // :110 只清 size gauge 的引用
      ...
      // 无 METRIC_NODES_ACTIVE 的移除（也无 tag 可区分归属 job）
  ```
- **严重程度**: P3
- **现状**: `JobCoordinator.start()`（:1914-1920）注册无 job tag 的 nodes-active gauge，supplier 闭包持有 `clusterRegistry`；`stop()`（:724）调用 `EngineMetrics.releaseJob(jobId)` 清理 job-tagged meters，但该无 tag gauge 与其 `GAUGE_STATE_REFS` 强引用不在清理面内；重复注册时 micrometer 同 id no-op，旧 supplier（旧 registry）保持强可达。
- **风险**: 单 JVM 多短命作业（Coordinator 进程跑多个 job 生命周期）时，`GAUGE_STATE_REFS` 每次启动净增一个条目，链式钉住已 stop 的 `ClusterRegistry`（JDBC registry 还可能持有连接工厂引用）不被 GC；gauge 读数还会继续反映旧 registry 的节点集，指标语义漂移。
- **建议**: `registerNodesActiveGauge` 返回句柄（或在 `EngineMetrics` 内按 clusterRegistry 弱键记录），`JobCoordinator.stop()` 对称移除；或 gauge 改挂 WeakReference 语义并在 `removeFromRegistry` 中按 id 一并清理。
- **信心水平**: 确定（`GAUGE_STATE_REFS` 全文件仅 :110/:148 两处触点，nodes-active supplier 无任何移除路径——已 grep 核实）
- **误报排除**: 不是"micrometer 弱引用自动回收"——代码注释自己写明 `weak-gauge semantics` 所以刻意强持有；也不是与 CC-05 重复的线程泄漏——本条是堆引用泄漏 + 指标语义问题，二者根因与修复点不同。
- **复核状态**: 未复核

### [CC-07] ResultPartition.close() 非幂等：重复 close 注入第二个 EOS 哨兵（SUSPEND stopSources 与任务自身成功关闭路径叠加时必然发生）
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:451-464`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:570-581`（stopSources）；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/StreamTaskInvokable.java:590-597`（closeOutputWriters）
- **证据片段**:
  ```java
  public void close() throws InterruptedException {
      finished = true;                       // 无 finished 已置检查（对照 RemoteResultPartition.close 有幂等守卫）
      try {
          queue.put(END_OF_STREAM);          // 每次 close 都再放一个哨兵
  // SUSPEND 路径先由外部关一次：
  invokable.getOutputWriter().close();       // GraphModelCheckpointExecutor.stopSources :575
  // 任务随后自然 EOS，成功路径再关一次：
  closeOutputWriters(); → outputWriter.close()   // StreamTaskInvokable:719/777
  ```
- **严重程度**: P3
- **现状**: 基类 `close()` 不检查 `finished`，二次调用再入队一个 `END_OF_STREAM`；`RemoteResultPartition` 有幂等守卫而本地队列分区没有。SUSPEND 模式 `stopSources` 关闭输出后 source 任务若继续按成功路径退出（或并发 double-close），同一分区被关两次。
- **风险**: 消费侧读第一个哨兵即 EOS，第二个哨兵滞留队列（哨兵不持 buffer-pool 许可，无许可泄漏；但永久占用一个队列槽位并使 `size()`/`isBackpressured()` 指标失真）；另一叠加效应：stopSources 关闭后仍在运行的 source 下一次 `write()` 命中 `finished` 抛 `ERR_STREAM_INVALID_STATE`，任务以 FAILED 收场——SUSPEND 语义下任务是被"关停"还是"失败"变得依赖时序。
- **建议**: 基类 `close()` 增加 `if (finished) return;`（与 `RemoteResultPartition` 对齐）；`stopSources` 与任务成功关闭路径之间以 `RecordWriter`/partition 的幂等性为唯一防线，不再依赖调用方只调一次。
- **信心水平**: 确定（两处调用点与 close 无守卫均为直读；第二个哨兵的实际危害限于指标/槽位，故 P3）
- **误报排除**: 不是"sentinel 重复读取导致二次 EOS 误判"——消费侧读完第一个哨兵即返回 null 且 `isFinished()` 已 true，不会挂起；也不是与 CC-01 同类的许可问题——哨兵写入不经 `enqueueWithBackpressure`，无 permit 记账。
- **复核状态**: 未复核

### [CC-08] advanceCheckpointIdCounterAfterRestore 为非同步 get-then-set，与并发 trigger 的 getAndIncrement 理论上可产生低于已恢复 epoch 的影子 checkpoint id（当前装配下不可达的潜伏面）
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:1148-1155, 1117-1130, 481`
- **证据片段**:
  ```java
  public void advanceCheckpointIdCounterAfterRestore(long restoredId) {
      long currentCounter = checkpointIdCounter.get();       // 读
      if (restoredId >= currentCounter) {
          checkpointIdCounter.set(restoredId + 1);           // 写：非 CAS，方法亦非 synchronized
      }
  ...
  // 触发侧（synchronized 方法内）：
  long checkpointId = checkpointIdCounter.getAndIncrement(); // :481
  ```
- **严重程度**: P3
- **现状**: 恢复推进计数器是公开的非同步 check-then-act；周期 trigger 在 coordinator monitor 内 `getAndIncrement`。两者并发交错可消耗/回绕出 `id <= restoredId` 的影子 epoch——恰是该 javadoc 声明要封死的窗口。
- **风险**: 当前装配下**不可达**（已核推）：LEADERSHIP_GRANT 是唯一调用 `restoreFromCheckpoint`→advance 的生产路径，且仅当 `latestCompletedCheckpoint==null`（全新 JVM，此时 ACK 集为空 → `tryTriggerCheckpointWithReason` 以 NO_TASKS_TO_ACK 拒绝一切并发 trigger）；同 JVM 重授权因 in-memory 视图非空而跳过 restore。若未来任何路径（如运维工具、新恢复入口）在 ACK 集非空时调用 advance，影子 id 会使 `loadLatestEpochManifest` 的 max-id 选择回滚到旧 epoch。
- **建议**: 将 `restoreFromCheckpoint`/`advanceCheckpointIdCounterAfterRestore` 纳入 coordinator monitor（或把 advance 改为对 `CheckpointIDCounter` 的 CAS 循环），并在 javadoc 标注并发契约。
- **信心水平**: 有趣的猜测（缺陷形态确定，现网装配不可达；列为防御性加固）
- **误报排除**: 不是"synchronized 方法已覆盖"——`tryTriggerCheckpointWithReason` 的 monitor 与本方法的非同步访问互不互斥；也不是"当前不可达就不用报"——该方法是 public API，其 javadoc 明确以"防止影子窗口"为存在理由，契约与实现强度不匹配才是本条要点。
- **复核状态**: 未复核

---

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|---|---|---|---|
| CC-01 | P2 | nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:1466-1478,721-723,1062-1115 + JobCoordinator.java:1346-1388,1772-1773 | commit forwarder / 分布式 abort 在 coordinator monitor（并被 globalRecovery 的 recoveryLock 放大）内做逐节点阻塞 RPC，节点变慢期间 ACK/触发/恢复全面串行停顿 |
| CC-02 | P2 | nop-stream-runtime/.../taskmanager/TaskManager.java:594-599,642-660,935-956 | deployTask fencing 校验与槽位 put 之间的窗口：迟到旧代 deploy 经 put 位移可取消新代健康任务并让僵尸常驻 |
| CC-03 | P2 | nop-stream-runtime/.../coordinator/JobCoordinator.java:2119-2126,2134-2165 | activateAsLeader 在 recoveryLock 外置 active/recoveryGen=0，重授权窗口内并发 globalRecovery 可使 fencing epoch 回退 |
| CC-04 | P2 | nop-stream-core/.../source/coordinator/SourceCoordinatorRegistry.java:33-40（+TaskManager/RunningTask 无注销） | 进程级静态 source 协调器注册表以裸 vertexId 为键且远程路径无注销：跨作业泄漏与同 JVM 串台 |
| CC-05 | P3 | nop-stream-runtime/.../ops/StreamOpsHttpServer.java:109-111,162-172 | ops HTTP server 的 4 线程 executor 在 stop() 中不关闭，每次重启周期泄漏 4 个 idle 线程 |
| CC-06 | P3 | nop-stream-runtime/.../metrics/EngineMetrics.java:144-152,100-116 | nodes-active gauge 的强引用 supplier 无移除路径，JobCoordinator.start 每次泄漏一个钉住 clusterRegistry 的引用 |
| CC-07 | P3 | nop-stream-core/.../execution/ResultPartition.java:451-464 | 基类 close() 非幂等，SUSPEND stopSources + 任务自身关闭叠加时重复注入 EOS 哨兵并使指标失真 |
| CC-08 | P3 | nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:1148-1155 | checkpoint id 计数器恢复推进为非同步 check-then-act，与并发 trigger 理论上可产生影子 id（当前装配不可达的公开 API 契约缺口） |

**质量自检**：8 项发现均含文件+行号+3-10 行证据片段；CC-02/CC-03 给出完整交错时序，CC-01 给出阻塞链与"为何非死锁"的排除论证；每项含严重程度/现状/风险/建议/信心水平/误报排除/复核状态（≥9 行）；P0/P1 为零的结论基于 live code 逐文件通读而非历史报告；除本报告外未创建/修改任何文件，未运行构建或测试（仅只读 grep/find/sed）。

---

## 维度复核结论

> 复核人：独立审计复核员（与初审无关）。方法：逐条直读全部被引文件相关行号核对证据；竞态类（CC-02/CC-03）独立推演交错时序并反向搜寻初审者可能遗漏的保护（其它锁、volatile、CAS、fencing 校验点、RPC 分发线程模型）；历史归属仅参照 R5 同维度报告与 R5 汇总（git 确认 HEAD 已含 R5 修复批次 e600de4058 = plan 2314）。未修改本报告既有正文，未运行构建。

| 发现 | 判定 | 现状归属 | 复核要点 |
|---|---|---|---|
| CC-01 | 保留 | 新发现 | 锁内回调链逐环直读证实：`executePersistAsync:721-723` synchronized → `onCompletePersistSuccess:989` → `notifyParticipantsFinishCommit:1466-1478` → JobCoordinator 分布式 commit forwarder :1346-1388 逐节点阻塞 `notifyCheckpointComplete`；abort 侧 `abortPendingCheckpoint:1062`（synchronized）在 ：1105-1112 monitor 内调 abortHandler → 分布式 abort handler :2534-2588 逐节点 `cancelTask`；`abortAllPendingCheckpoints:1210` 在 globalRecovery 的 recoveryLock 临界区内（锁入 ~:1716 / abort :1772 / 解锁 ~:1787）被调用，双重持有成立。"fan-out 在锁外"的不变式承诺在 live code 中存在（JobCoordinator :860/:2133、AssignmentPlanner :43），commit/abort 路径确实违背。**非死锁定性核实成立**：TM 侧 `notifyCheckpointComplete` 派发到有界 commit executor 后即返回（TaskManager:882）、`cancelTask` 为 remove+mailbox signal+interrupt 无同步回调；ACK 损失链量化证实（CheckpointAckSender :30 `ACK_SEND_ATTEMPTS=3`、:33 退避 200ms×attempt；coordinator 侧 `collectAck:974` → `acknowledgeTask:573` synchronized 被阻塞）。一处补充：commit executor 满载 CallerRuns 回退（TaskManager:855-864 注释自述）会把 sink 提交 I/O 跑在持有 monitor 的 forwarder 线程上，**延长**而非缩短锁持有——与发现方向一致。P2 恰当（有界停顿、连锁 abort、非死锁）。 |
| CC-02 | 保留 | 主体（deployTask 位移方向窗口）= 新发现；次级窗口（receiveAssignment）= 已知未修复（R5 CC-09） | 证据全部核实：校验点 ：594-599 与 `put` 位移点 :648-657 之间无任何 epoch 再校验（中间仅 nodeId/描述符 epoch 校验与 `tryAcquire`），位移分支无条件 `existing.cancel()` + 归还许可。交错时序独立推演成立（见下文详情）。**遗漏保护反向排查**：(a) TM 控制面 API 无 per-node 串行化契约——`deployTask`/`updateFencingToken` 均非 synchronized，仅依赖原子量 + CHM；(b) 消息分发线程模型核查：`LocalMessageService.send`（nop-message-core :194-206）在**发送者线程同步**执行 listener，故嵌入式/本地后端形态（含 MiniStreamCluster 测试与 Embedded 形态）下 deployTask 与 updateFencingToken 可由不同线程并发进入同一 TaskManager，交错结构上成立；broker 后端若订阅串行消费则窗口进一步收窄，但该契约不存在于代码中。初审"消息后端排队即可满足"表述略宽——必要条件是 T1 在 ：594 与 ：649 之间发生线程停顿且轮换推送 + E2 重部署落入该停顿；窗口窄于字面表述但真实。次级 receiveAssignment 窗口（containsKey :492 + putIfAbsent :511）与 R5 CC-09 所述逐字相同，R5 后未修。P2 维持（窗口窄、后果真实：健康任务被杀 + 僵尸常驻 + 白烧一次恢复）。 |
| CC-03 | 保留 | R5 已修复的新残余（R5 CC-02 / plan 2314 修复不完整的残余） | 现场代码证实：锁外三次写（`currentLeadership=epoch` / `recoveryGen.set(0)` / `active=true`，:2121-2126）在 2314 修复后**原样保留**——该修复只新增锁内 `active=true`（:2141）与失败回滚（catch 内 `active=false`），diff 中无删除行；修复注释声称 "a rotation failure rolls back **before anything observes active=true**"，在锁外写存在时该声明不成立。交错时序独立推演成立且**触发面比初审更宽**（见下文详情）：`rotateFencingEpochCoreLocked:1956` 直接 `fencingEpoch.set`，无单调性守卫；TM `updateFencingToken:942-947` 拒绝回滚，异常被轮换处的 per-node catch（:2003-2010）吞掉并继续——coordinator 停在回退 epoch；duplicate-activation guard 比对 `currentLeadership`，与 globalRecovery 正交（T1 已先写该字段）。P2 维持。 |
| CC-04 | 保留 | 新发现 | 全部核实：静态 map 键为裸 `Integer vertexId`（:35-36），类 javadoc 自认 v1 限制；main 代码 `unregister` 唯一调用点为 `StreamExecutionEnvironment:602`（LOCAL 路径，grep 全库核实）；注册点 `SourceReaderOperator.wireCoordinatorChannel:172-181` 在**任何**任务 JVM（含 TM）的 operator open 时执行；`TaskManager.stop()`（:309-340）无注销（metrics 做了 `releaseNode`，形成对照）；`RunningTask.run` finally（:124-182）无注销；`LocalSourceCoordinator` 持有 `SplitEnumerator`，其 `close()` 仅经 unregister 可达。跨作业串台条件：TaskManager 结构上支持节点级重启复用与先后作业（R5 报告亦确认该形态），后作业同 vertexId `registerIfAbsent` 直接复用前作业的 coordinator/enumerator。`CheckpointCoordinator:1636` 亦读该注册表，证实分布式路径真实使用。P2 恰当（泄漏确定、串台受部署形态门控）。 |
| CC-05 | 保留 | 新发现 | 核实成立：`setExecutor(Executors.newFixedThreadPool(4,...))`（:109-111）引用未保存，`stop()`（:162-172）只 `httpServer.stop(0)` + 关 prometheus registry，无法补关 executor；JDK HttpServer 契约明确注入的 executor 由调用方负责生命周期。补充核实：`NopStreamThreadFactory:49` 置 daemon=true——不阻 JVM 退出，与 P3 定级一致。start() 每次 new 池，重复 start/stop 线性泄漏成立。P3 恰当。 |
| CC-06 | 保留 | 新发现 | 核实成立：`registerNodesActiveGauge:146-150` 向 `GAUGE_STATE_REFS` 强引用列表 add 无 job tag 的 nodes-active supplier；`removeFromRegistry:109-119` 只移除 `latestSizeBytes` 与 job-tagged meter id；`JobCoordinator` 注册点 ：1909-1920 supplier 闭包捕获 `clusterRegistry`，`stop()` 的 `releaseJob`（:723）清理面不含该 gauge。重复注册时 micrometer 同 id no-op、旧 supplier（旧 registry）保持强可达且 gauge 持续轮询旧 registry——泄漏与指标语义漂移均成立。P3 恰当。 |
| CC-07 | 保留 | 新发现 | 核实成立：基类 `close():451-464` 无 `finished` 已置检查（对照 `RemoteResultPartition.close():150-153` 有幂等守卫）；SUSPEND 流程 `GraphModelCheckpointExecutor:503` → `stopSources:570-581` → `RecordWriter.close:250` → partition.close（第一次），任务自身成功路径 `StreamTaskInvokable:719/:777` → `closeOutputWriters:590-597` → 第二次 close → 第二个哨兵。二次效应核实：`write()` 对 finished 分区 barrier 丢弃、data 抛 `ERR_STREAM_INVALID_STATE`（:270-282），SUSPEND 下"关停 vs 失败"依赖时序成立；哨兵经 `queue.put` 直入、不经 `enqueueWithBackpressure`，无 permit 记账问题——初审的"危害限于指标/槽位"边界准确，P3 恰当。 |
| CC-08 | 保留 | 新发现 | 缺陷形态核实：`advanceCheckpointIdCounterAfterRestore:1148-1155` 为非同步 get-then-set，与 synchronized trigger 的 `getAndIncrement:481` 无互斥。**不可达结论复核后成立，但推理需修正两点**：(a) 生产调用点实为**三处**而非初审所述一处（JobCoordinator:2021 LEADERSHIP_GRANT；GraphModelCheckpointExecutor:967 LOCAL 恢复；OpsJobManager:164-166 submit 时恢复）——后两处严格先于其 coordinator 实例的任何 trigger 驱动/任务注册（sequenced-before），同样无并发能力，不可达结论反而更强；(b) 初审理由"以 NO_TASKS_TO_ACK 拒绝一切并发 trigger"需补充：拒绝发生在 `getAndIncrement` **之后**（:481 先消耗 id，:510-519 才检查 ACK 集），fresh JVM 上周期与显式动作（completedTasks 亦空）全部被拒、被消耗的 id 不会物化 checkpoint，故回退 set 造成的计数器扰动（含 advance 的 set 覆盖并发增量）无持久危害——影子 epoch 无法落盘。公开 API 的 javadoc 以"防影子窗口"为存在理由而实现强度不匹配，保留为 P3 防御性加固恰当。 |

### 竞态类重点复核详情

**CC-02（deployTask fencing 校验与槽位安装窗口）**——独立交错推演（三条线程，全部持锁点核实）：

1. T1（迟到的旧代 `deployTask(E1)`，本地消息后端下在**协调器 fan-out 线程**同步执行于 TM 对象上）通过 ：594 校验（此刻 `currentFencingEpoch==E1`），随后在 ：599–:648 之间停顿（线程去调度/GC）。
2. T2（恢复轮换推送）执行 `updateFencingToken(E2)`（TaskManager:935-956）：`getAndSet` 置 E2 → `removeIf(epoch==E1)` 清 E1 任务——T1 尚未 put，扑空。
3. T3（本轮恢复 `deployTask(E2)`）通过校验（E2==E2），`put` 安装健康 E2 任务并提交执行。
4. T1 恢复，执行 ：648 `put` → `existing` = E2 健康任务 → 无条件 `existing.cancel()` + 归还其许可；E1 僵尸成为槽主并被 submit。
5. 后果核实：僵尸的心跳/liveness 被协调器 attempt 拒收（无 liveness 记录）、trigger 侧 barrier 因 `getFencingEpoch()==E1` 被跳过（TaskManager:801-805）；E2 任务被 cancel 后因 `canceled=true` 不回发 FAILED report（RunningTask:179-181 只报非 canceled），因此**无即时失败信号**；自愈路径 = detectFailures 的 deploy 宽限期到期 → 部署失败 → E3 恢复顶替僵尸，代价一次恢复预算 + 宽限期内该 subtask 数据面停滞。
6. 遗漏保护排查结论：无其它锁/CAS/fencing 再校验挡住该窗口；`put` 位移的原子性只解决"同 key 丢失被位移者"，不解决"位移者自身已跨代"。被位移者 epoch 与当前 epoch 的比对缺失是缺陷本体。

**CC-03（activateAsLeader 锁外激活写与并发 globalRecovery 的 epoch 回退）**——独立交错推演（同 JVM 失去又重获领导权，`deactivateToStandby:2263-2272` 已核实不清 `taskAssignmentMap`/`subtaskLiveness`）：

1. T1（选举回调）执行 ：2121-2126：`currentLeadership=L`、`recoveryGen.set(0)`、`active=true`（volatile，对探测线程立即可见），并预算 `token = L*SCALE+0`（:2129，锁外），随后在 `recoveryLock.lock()`（:2135）前停顿。
2. T2（failureDetector 单线程，:676 注册）进入 `detectFailures`：仅以 `running`+`active` 为门（:1422-1431），不取 recoveryLock；**残留工作集使触发无需租约过期**——上一代 `subtaskLiveness` 条目的旧时间戳在第一个 tick 即满足 `lastProgress < cutoff` → `taskStallDetected=true`（:1471-1480）；`recoveryPending` 此刻为 false（activateAsLeader 不置位），CAS 成功 → 探测线程上同步进入 `globalRecovery`。
3. T2 先拿到 `recoveryLock`：`recoveryGen.incrementAndGet()→1`、`newEpoch=L*SCALE+1`、`rotateFencingEpochCoreLocked`（推送 E=L*SCALE+1 至全部 TM、清工作集）、abort pending、物化 assignments、解锁、锁外 fan-out——新代任务以 L*SCALE+1 运行。
4. T1 拿到锁：锁内 `active=true`（no-op）→ `rotateFencingEpochCoreLocked(L*SCALE+0, LEADERSHIP_GRANT)` → `fencingEpoch.set` **回退**（:1957 无单调守卫）；推送 L*SCALE+0 被 TM `updateFencingToken` 以 rollback 拒绝（:942-947），异常被 ：2003-2010 的 per-node catch 吞掉并继续。
5. 后果核实：coordinator 停在 L*SCALE+0，后续 `deployTask`/`triggerCheckpoint` 全部 mismatch 拒收（TM 侧 ：594/:794 校验）→ FAILED report → 再一轮 `requestRecovery` 使 recoveryGen 前跳才收敛；duplicate-activation guard（:2113-2119）比对 `currentLeadership`，而 T1 已写该字段、T2 的 recovery 不改它——与回退正交，与初审判断一致。
6. **比初审更强的两个事实**：(a) 触发面更宽——不依赖节点租约过期，残留 liveness 旧时间戳即可在首个 tick 触发；(b) 交错不要求 T1 停顿在 9 行窗口内——若 T2 的完整 globalRecovery 落在 T1 的锁外写与加锁之间（T1 任意停顿），T1 随后的轮换同样回退。修复方向（三次写全部移入锁内）经核实可行：`prepareAssignmentsLocked` 观测 `active` 的需求已由锁内 ：2141 副本满足，:2126 锁外写的注释语义已被 2314 取代。

**CC-01（非死锁定性）**——已按复核要求专项核对回调链与锁持有范围：两条 RPC fan-out（commit forwarder / 分布式 abort handler）都在 coordinator monitor 内执行、abort 路径再叠加 recoveryLock 双重持有；TM 侧两个 handler（`notifyCheckpointComplete` 派发到有界 executor 后返回、`cancelTask` remove+mailbox+interrupt）均无同步回调进入 coordinator 的 monitor/recoveryLock，死锁变体排除成立；阻塞时长由 RPC 超时封顶，"有界停顿 + 连锁 abort（ACK 3 次重试 ~0.6-1s 内放弃 → 在途 epoch timeout-abort）+ 恢复卡顿"的定性准确。

## 最终保留项（复核后）

| 编号 | 复核后严重程度 | 文件路径 | 一句话摘要 | 现状归属 |
|---|---|---|---|---|
| CC-01 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:721-723,1062-1115,1466-1478 + coordinator/JobCoordinator.java:1346-1388,1772-1773,2534-2588 | commit forwarder / 分布式 abort 的逐节点阻塞 RPC fan-out 在 coordinator monitor（abort 路径再叠加 recoveryLock）内执行，节点变慢期间 ACK/触发/恢复全面串行停顿（有界、非死锁） | 新发现 |
| CC-02 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:594-599,648-657（次级 ：492,:511） | deployTask fencing 校验与槽位 put 位移之间的窗口：迟到旧代 deploy 可取消新代健康任务并让僵尸常驻（位移方向无 epoch 再校验）；receiveAssignment 同型窗口为 R5 CC-09 残留 | 主体新发现；次级窗口已知未修复（R5 CC-09） |
| CC-03 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:2121-2126,2135,2141-2146 | activateAsLeader 在 recoveryLock 外写 currentLeadership/recoveryGen=0/active=true，重授权窗口内并发 globalRecovery 先轮换可使 fencing epoch 回退（rotate 无单调守卫、TM 拒绝被 per-node catch 吞掉） | R5 已修复的新残余（R5 CC-02 / plan 2314 修复未移除锁外激活写） |
| CC-04 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/SourceCoordinatorRegistry.java:35-36（+SourceReaderOperator:172-181、TaskManager.stop:309-340 与 RunningTask finally:124-182 无注销） | 进程级静态 source 协调器注册表以裸 vertexId 为键且分布式路径无注销：TM JVM 跨作业泄漏 + 同 JVM 先后作业串台 | 新发现 |
| CC-05 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/StreamOpsHttpServer.java:109-111,162-172 | ops HTTP server 的 4 线程 executor 在 stop() 中不关闭且引用未保存，每次 start/stop 周期泄漏 4 个 daemon idle 线程 | 新发现 |
| CC-06 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/metrics/EngineMetrics.java:146-150,109-119 + coordinator/JobCoordinator.java:1909-1920,723 | nodes-active gauge 的强引用 supplier（闭包持 clusterRegistry）无任何移除路径，每次 JobCoordinator.start 净增一个钉住旧 registry 的引用且 gauge 读数漂移 | 新发现 |
| CC-07 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:451-464（对照 transport/RemoteResultPartition.java:150-153） | 基类 close() 非幂等：SUSPEND stopSources 与任务自身成功关闭叠加时重复注入 EOS 哨兵（占队列槽 + 指标失真），且关停后写入使任务以 FAILED 收场依赖时序 | 新发现 |
| CC-08 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:1148-1155（对照 ：449,:481） | checkpoint id 计数器恢复推进为非同步 get-then-set，与并发 trigger 理论上可扰动计数——当前三处生产调用点均无并发能力（不可达），public API 契约强度与 javadoc 不匹配，列防御性加固 | 新发现 |

**复核统计**：8/8 保留（0 降级、0 驳回）；严重程度全部维持初审定级（P2×4、P3×4）。现状归属：新发现 6 条（CC-01、CC-02 主体、CC-04..CC-08）＋ R5 已修复的新残余 1 条（CC-03，源于 plan 2314 对 R5 CC-02 的修复未移除锁外激活写）＋ CC-02 次级 receiveAssignment 窗口为已知未修复（R5 CC-09）。初审证据与行号全部核实为真，无虚引；两处推理精度修正（CC-02 触发条件表述、CC-08 调用点数量与"getAndIncrement 先于 ACK 门禁"细节）已在上文注明，均不改变判定。
