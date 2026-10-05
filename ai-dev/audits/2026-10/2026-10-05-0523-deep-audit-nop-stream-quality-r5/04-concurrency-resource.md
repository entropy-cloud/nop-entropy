# 深度审计 R5 · 报告 04：nop-stream 并发正确性 + 资源管理

- **审计日期**: 2026-10-05
- **HEAD SHA**: `bd089444f5c2b1fcd2c9a964ce909bac774a83d9`
- **范围**:
  - nop-stream-runtime：`coordinator`（JobCoordinator、AssignmentPlanner）、`cluster`（JdbcLeaderElector/JdbcClusterRegistry 概览）、`execution`（SupervisionLoop、GraphModelCheckpointExecutor、TaskCheckpointWiring、RpcDistributedExecutor、EmbeddedDistributedExecutor）、`taskmanager`（TaskManager、RunningTask、CheckpointAckSender）、`transport`（RemoteInputChannel、RemoteResultPartition）、`checkpoint`（CheckpointCoordinator、PendingCheckpoint、RetentionCleaner）、`health`、`maintain`、`launch`、`deploy`、`alert`、`ops`
  - nop-stream-core：`execution`（InputGate、ResultPartition、InputChannel、CheckpointBarrierTracker、RecordWriter、TaskMailbox/MailboxExecutor、ProcessingTimeServiceDriver/TaskProcessingTimeService、task/*、buffer/BufferPool、materialization/InMemoryMaterializationPoint）、`common/buffer/PerKeyOrderedBuffer`（结构核查）
- **方法**: 只读源码审计。逐文件全文精读上述并发关键类；对每条竞态/活性疑点做双线程时序推演，并对照库内注释宣称的修复（CC-01/CC-03/CC-04/R5-CC-07/CC-05/REG-01/REG-02/CC-02 重放协议等）验证修复完备性；资源面核查线程池/调度器/订阅/channel 的创建-关闭配对与周期任务异常处理。未运行任何构建/测试；未修改任何产品代码。

---

## 发现总览

| 编号 | 严重程度 | 标题 |
|---|---|---|
| CC-01 | P1 | `failJob` 不 cancel 已分配远程任务，FAILED 作业记录永久占用（孤儿任务 + 2PC 事务悬挂 + 运维 wedged） |
| CC-02 | P1 | `activateAsLeader` 在 rebuild/assignment 失败时留下“半激活 leader”，fresh JVM 无任何自愈路径 |
| CC-03 | P2 | InputGate abort 与迟到 barrier 的 check-then-act 竞态：泄漏的 alignment 30s 后把例行 abort 变成任务失败 |
| CC-04 | P2 | 同 JVM 的 leadership 重授予不 abort 遗留 pending checkpoint，failover 后 checkpoint 循环最长停滞 10 分钟 |
| CC-05 | P2 | 本地/嵌入式执行线程池固定为 CPU 核数 + 长期阻塞任务：总并行度 > 核数时静默死锁 |
| CC-06 | P2 | WebhookAlertChannel 投递循环遇首个未受检异常即永久死亡，告警通道静默失效 |
| CC-07 | P3 | OpsJobManager governanceSweep 周期任务无异常防护，单次异常静默杀死治理清扫 |
| CC-08 | P3 | CheckpointCoordinator.shutdown() 脱离协调器监视器并发清场，与在途完成路径存在交错窗口 |
| CC-09 | P3 | TaskManager.receiveAssignment 与 cancelTask 同纪元竞态可复活刚被取消的任务槽 |
| CC-10 | P3 | JobCoordinator.stop() javadoc 宣称反注册协调器，实际无 unregister，注册表遗留陈旧 coordinator 行 |

---

### [CC-01] failJob 不向 TaskManager fan-out cancel，FAILED 作业记录永久占用
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:775-798`（failJob）；佐证 `:2271-2284`（terminate 的 active 门禁）、`:1716-1733`（预算耗尽路径）；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/OpsJobManager.java:297-305`（remove 拒绝）
- **证据片段**:
  ```java
  public void failJob(Throwable cause) {
      if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.CANCELED) { ... return; }
      this.jobFailureCause = cause;
      this.jobStatus = JobStatus.FAILED;
      this.active = false;
      ...
      try {
          failureDetector.shutdownNow();
      } catch (Exception e) { ... }
  }
  ```
- **严重程度**: P1
- **现状**: `failJob` 是三条路径的终态（预算耗尽 `globalRecovery`、terminal savepoint 失败 `terminateWithTerminalSavepoint`、直接调用）。它翻转 `active=false`、杀掉 failure detector、把 `jobStatus` 置 FAILED，但 **既不调用 `cancelAllAssignedTasks`，也不调用 `stop()`**，且 `running` 保持 `true`。对比之下 `stop()`（CC-03 修复）会 fan-out cancel。
- **风险**（时序推演）:
  1. R5-CC-07 触发连续 deploy 失败 → stall 预算耗尽 → `failJob`。
  2. 所有远程 TaskManager 上的任务继续运行：占住 `capacitySemaphore` 许可、持续消费 source（真实外部副作用）、2PC sink 的 prepared transaction 永远不 commit/abort。
  3. TM 心跳继续调用 `reportNodeTaskLiveness`/`reportTaskStatus` → 被 `active=false` 拒绝（仅 WARN 日志刷屏），TM 侧**没有任何“协调器已死”的看门狗**，其自身租约靠心跳独立续期，节点在 ClusterRegistry 中永远 active。
  4. 运维补救路径全部失效：`OpsJobManager.stop(jobId, CANCEL)` → `terminate()` 在 `:2281` 被 `active=false` 拒绝（no-op + WARN）；`remove(jobId)` 在 `OpsJobManager.java:299-301` 因 `coordinator.isRunning()==true` 抛 `ERR_STREAM_INVALID_STATE`；重复 `submit` 因 `jobs` 仍含该 jobId 抛 `JOB_ALREADY_HOSTED`。**该 jobId 在进程重启前永久不可复用，孤儿任务永生。**
  5. 次级同族窗口：`stop()` 与在途 `globalRecovery` 的 fan-out 也不互斥——`stop()` 不取 `recoveryLock`，fan-out 前后均不复查 `running`，锁释放后 materialize 的 dispatch 仍会在 `stop()` 之后发出（`JobCoordinator.java:1793-1794`、`AssignmentPlanner.java:232-256`），同样产生孤儿。根因相同：**assignment fan-out 与 stop/failJob 无串行化、无事后 cancel 补偿**。
- **建议**: `failJob` 内联（或收尾调用）`cancelAllAssignedTasks("job failed")` + 终止 periodic checkpoint scheduler + 置 `running=false`（复用 `stop()` 的收缩序列）；`OpsJobManager` 对 `jobStatus==FAILED` 的记录允许 remove/重新 submit；`executeAssignmentFanOut` 的每个 dispatch 执行前复查 `running`/`active`。
- **信心水平**: 确定（所有步骤均为当前 live code 直读，无推测分支）
- **误报排除**: 这不是“failJob 后由调用方负责 stop”的契约问题——库内两条 failJob 调用路径（`globalRecovery` 预算分支 `:1720-1733`、`terminateWithTerminalSavepoint` 失败分支 `:2424-2431`）自身都不跟 `stop()`；而 `terminateCancel` 的对称路径（`:2306-2319`）显式走 `stop()`→cancel fan-out，证明“终态必须 cancel 任务”是本类自己的既定契约，failJob 漏掉了它。
- **复核状态**: 未复核

---

### [CC-02] activateAsLeader 失败路径留下“半激活 leader”，fresh JVM 无自愈
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:2113-2147`（activateAsLeader）、`:2021-2031`（rebuild fail-loud 抛出）、`:2102-2108`（重复激活守卫）、`:1494-1511`（CC-01 空工作集哨兵）、`:2216-2231`（seed 失败显式去激活）；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/AssignmentPlanner.java:168-173`（rpc 缺失抛出）
- **证据片段**:
  ```java
  this.currentLeadership = epoch;
  this.recoveryGen.set(0);
  // Mark active BEFORE rebuilding so the internal assignment materialization
  // (prepareAssignmentsLocked) observes the active state.
  this.active = true;

  long token = deriveHaFencingEpoch(epoch.getEpoch(), 0);
  recoveryLock.lock();
  try {
      rotateFencingEpochCoreLocked(token, FencingRotationCause.LEADERSHIP_GRANT);
      seedAttemptCountersFromRegistryLocked();
      dispatches = assignmentPlanner.prepareAssignmentsLocked(...);
  ```
- **严重程度**: P1
- **现状**: `active=true` 先于 rebuild 置位。`seedAttemptCountersFromRegistryLocked` 失败时显式 `active=false` 再 rethrow（`:2220-2222`），但同一锁段内**更早的两个抛出点不做去激活**：(a) `rotateFencingEpochCoreLocked` 的 LEADERSHIP_GRANT rebuild 失败在 `:2026-2031` fail-loud 抛 `StreamException`（此时工作集已在 `:1954-1977` 被清空）；(b) `prepareAssignmentsLocked` 在目标节点无 RPC service 时于 `AssignmentPlanner.java:169-173` 抛出。异常沿 `CoordinatorElectionListener.becomeLeader` 传给平台 elector。
- **风险**（时序推演）:
  1. fresh JVM 赢得选举 → `becomeLeader(E)` → `active=true`、`currentLeadership=E`。
  2. `restoreFromCheckpoint`（或 assignment 规划）抛出 → 异常传播给 elector。按本文件自己的注释（`:2174-2179`）：“after the platform elector swallows the exception with no retry and no degradation”——无重试、无降级回调。
  3. 节点停留状态：`active=true`、epoch 已轮转、**taskAssignmentMap 为空、everAssigned=false**。
  4. `detectFailures` 两个检测环都迭代空 map → 永不触发；CC-01 空工作集哨兵（`:1505`）被 `everAssigned` 门禁挡住（fresh JVM 从未 materialize 过）→ 永不自愈：作业级永久悬挂（lease 持有、无 assignment、无 checkpoint）。
  5. 加重项：若 elector 以后重放**同一 epoch** 的 `becomeLeader`，`:2104-2107` 的重复激活守卫直接 return——连人工/平台重试也被吞。
- **建议**: 把 `this.active = true` 移到锁内全部步骤成功之后（或在 catch 中复用 seed 的去激活纪律：`active=false; currentLeadership=null` 再 rethrow）；重复激活守卫在 `active && taskAssignmentMap.isEmpty()` 时不得短路，应允许重试完整 rebuild。
- **信心水平**: 确定（去激活纪律在相邻代码中自证了 elector 吞异常的行为与哨兵的门禁条件）
- **误报排除**: 不是“CC-01 哨兵已覆盖”的同类误报——哨兵恰好以 `everAssigned` 为门禁（防 fresh 误恢复），而本缺陷的触发前提恰是 fresh JVM 首次 grant，门禁条件与故障条件互斥；这正说明哨兵的修复没有覆盖 leadership-grant 失败这一半。
- **复核状态**: 未复核

---

### [CC-03] InputGate abort 与迟到 barrier 的 check-then-act 竞态 → 例行 abort 转化为 30s 后任务失败
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:978-1053`（handleBarrierNonRecursive）、`:1162-1172`（abortBarrierAlignment）、`:828-861`（checkAlignmentElapsed 超时抛出）
- **证据片段**:
  ```java
  private Optional<StreamElement> handleBarrierNonRecursive(int channelIndex, CheckpointBarrier barrier) {
      long id = barrier.getId();
      if (abortedBarriers.contains(id)) { ... return Optional.empty(); }      // (A) 检查点 1
      ...
      BarrierAlignment align = inFlightAlignments.get(id);
      if (align == null) {
          align = new BarrierAlignment(id, barrier, CoreMetrics.currentTimeMillis());
          inFlightAlignments.put(id, align);                                   // (B) 创建点
  ```
  ```java
  public void abortBarrierAlignment(long checkpointId) {
      abortedBarriers.add(checkpointId);            // add-first（修的是另一侧窗口）
      BarrierAlignment removed = inFlightAlignments.remove(checkpointId);
  ```
- **严重程度**: P2
- **现状**: `abortedBarriers` 检查（A）与 alignment 创建（B）之间无公共锁；abort 线程的 add-first + remove 序列只关闭了“remove 先于 add”的窗口（`:1151-1160` 注释自述），未关闭“A 检查早于 add、B 晚于 remove”的窗口。
- **风险**（完整时序）:
  - 线程 T（任务线程，处理数据面 barrier X）：步骤 1 = (A) `abortedBarriers.contains(X)` → false；
  - 线程 A（checkpoint-timeout 调度线程，经 `TaskCheckpointWiring.registerLocalAbortHandler` → `abortBarrierAlignment(X)`）：步骤 2 = `abortedBarriers.add(X)`；步骤 3 = `inFlightAlignments.remove(X)` → null（T 尚未 put）；
  - 线程 T：步骤 4 = (B) put 新 alignment X，`blockedChannels.add(i)`，X 未全收；
  - 后续：其余通道的 X barrier 在 (A) 处被 `abortedBarriers.contains(X)==true` 全部丢弃；`markFinishedChannel` 仅在通道 FINISH 时补齐（无限流作业永不）→ alignment X 永久在途且是最小 id。
  - 后果分支 1（unaligned 关闭）：`checkAlignmentElapsed` 在 30s 后抛 `ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT` → 任务 FAILED → 烧一次全局恢复预算；
  - 后果分支 2（unaligned 开启，默认开）：1s 后 `switchToUnalignedAndEmit` 捕获在途数据并放行已 abort 纪元的 barrier（tracker 侧无 X 纪元，ACK 被"no matching in-flight epoch"丢弃）——自愈但产生一次伪 unaligned checkpoint 与无谓 channel-state 持久化。
- **建议**: 在 (B) put 之后对 `abortedBarriers` 复查一次（double-check），命中则回滚 put + 解除本通道 block；或将 (A)(B) 与 `abortBarrierAlignment` 收敛到同一把轻量锁。
- **信心水平**: 很可能（窗口仅数条指令宽，但 abort 的触发前提恰是“barrier 已在数据面在途”，即 T 正处于 handleBarrier 路径附近的概率不可忽略）
- **误报排除**: 为什么这次不是常见的“JMM 想象竞态”误报：两个结构（`ConcurrentHashMap` inFlightAlignments、`ConcurrentHashMap.newKeySet` abortedBarriers）本就设计为跨线程并发访问（`:130-140`、`:163-166` 注释明示 abort 线程会并发操作），操作间**没有**任何 happens-before 边；且 `:1151-1160` 的 add-first 注释证明作者已意识到并修掉了对称窗口——残留的是它未覆盖的另一半，属真实残余而非新构想的假想敌。
- **复核状态**: 未复核

---

### [CC-04] 同 JVM leadership 重授予不 abort 遗留 pending，failover 后 checkpoint 循环停滞至 10 分钟
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:2097-2147`（activateAsLeader 全文无 abortAllPendingCheckpoints）、`:1763`（globalRecovery 有）、`:2242-2254`（deactivateToStandby 注释）、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/CheckpointConfig.java:28,32`（默认 timeout=600s、maxConcurrent=1）
- **证据片段**:
  ```java
  // deactivateToStandby:
  // In-flight checkpoints will not commit: collectAck is gated on active,
  // so further ACKs are rejected and the pending checkpoint times out /
  // is aborted by the new leader. ...
  ```
  ```java
  // globalRecovery（对照）:
  checkpointCoordinator.abortAllPendingCheckpoints(
          "global recovery #" + newCount + " (fencing epoch " + newEpoch + ")");
  ```
- **严重程度**: P2
- **现状**: `abortAllPendingCheckpoints` 全库仅一处调用（`globalRecovery`，grep 证实）。`activateAsLeader`（leadership grant）不 abort。`deactivateToStandby` 的注释声称 pending 会“被新 leader abort”，这只在**跨 JVM**（新 leader = 新 CheckpointCoordinator 实例，无遗留 pending）成立；**同 JVM** 去/再激活（leader 抖动、ping-pong 回本机）时 pending 属于同一实例，一直存活到自身 timeout。
- **风险**（时序）: leader A 在途 checkpoint id=N（barrier 已 fan-out）→ 失去 leadership（`collectAck` 的 active 门禁拒绝所有 ACK）→ 数秒后重获 leadership、epoch 轮转 → 旧 pending N 的 ACK 全部被 epoch 校验拒绝，只能等满 `checkpointTimeout`（默认 600_000ms；OpsJobManager 路径 10s）才被 timeout-abort。期间 `maxConcurrent=1`（默认）压制**所有**新 checkpoint 触发 → failover 后最长 10 分钟无 checkpoint（持久化恢复点停更、2PC commit 停摆、`minPause` 无关）。
- **建议**: `activateAsLeader` 在 `rotateFencingEpochCoreLocked` 之前（或之后）调用 `checkpointCoordinator.abortAllPendingCheckpoints("leadership grant ...")`，与 `globalRecovery` 对齐。
- **信心水平**: 确定（代码路径直读；仅影响面大小依赖部署形态）
- **误报排除**: 不是“abort handler 会兜底”的误报——`registerDistributedAbortHandler` 对 `reason.startsWith("Timeout")` 明确跳过 cancelTask，而本场景 pending 恰恰只能走 Timeout abort；LOCAL abort handler 同样不缩短 pending 的等待时长。
- **复核状态**: 未复核

---

### [CC-05] 本地/嵌入式执行线程池固定 CPU 核数，总并行度超核数时静默死锁
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/TaskExecutor.java:77,116-117`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:258,373`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java:503`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:346-376`
- **证据片段**:
  ```java
  private static final int DEFAULT_POOL_SIZE = Runtime.getRuntime().availableProcessors();
  ...
  this.executorService = Executors.newFixedThreadPool(poolSize,
          NopStreamThreadFactory.named("stream-task-executor-"));
  ```
  ```java
  // ResultPartition.enqueueWithBackpressure（非物化、有/无 pool 均存在阻塞分支）:
  } else {
      // Legacy blocking path (no materialization → zero regression).
      queue.put(element);
  }
  ```
- **严重程度**: P2
- **现状**: 三个生产调用点全部 `new TaskExecutor()`（池 = CPU 核数），SupervisionLoop 一次性提交全部 SubtaskTask；每个流任务是**长期阻塞**线程（`InputGate.read` 轮询等待 / `queue.put` 背压阻塞），无任何“总并行度 ≤ 池容量”的校验、告警或文档。
- **风险**（死锁循环推演）: 设总子任务数 P > 池容量 N。提交后 N 个任务占满线程、P−N 个在队列排队。若在跑的 N 个恰为排队任务的（传递）上游，它们写满下游 1024 容量的 `LinkedBlockingQueue` 后进入 `queue.put` 永久阻塞；排队任务拿不到线程 → 永不消费 → 全部线程滞留阻塞 → **无超时、无错误、无日志的永久悬挂**（`waitForCompletion` 只在外层 RPC/embedded 路径有 60s 兜底，`GraphModelCheckpointExecutor` 本地路径的 SupervisionLoop 无任何超时）。典型触发：8 核机器跑 source(4)+window(4)+sink(4)=12 并行度的有限流作业，数据量 > 1024 条即卡死。
- **建议**: 提交前校验总子任务数 ≤ 池容量，超出即 fail-fast（ERR_STREAM_INVALID_ARG 附带诊断）；或池容量取 `max(cores, totalSubtasks)`；或为本地路径引入虚拟线程执行器（Java 21 已可用）。
- **信心水平**: 确定（机制直读成立；实际触发频率取决于用户拓扑规模）
- **误报排除**: 不是“队列 1024 够大不会满”的误报——`queue.put` 的阻塞分支在数据量超过容量后必然到达；也不是“下游先启动”的误报——`SupervisionLoop.run` 按 `tasks.values()`（ConcurrentHashMap，无拓扑序）提交，执行顺序与拓扑序无任何约束。
- **复核状态**: 未复核

---

### [CC-06] WebhookAlertChannel 投递循环遇未受检异常即永久死亡
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/alert/WebhookAlertChannel.java:138-150`（drainLoop）、`:152-153`（deliverWithRetries 的 JsonTool.stringify）
- **证据片段**:
  ```java
  private void drainLoop() {
      try {
          while (!Thread.currentThread().isInterrupted()) {
              AlertEvent event = pending.poll(1, TimeUnit.SECONDS);
              if (event == null) { continue; }
              deliverWithRetries(event);
          }
      } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
      }
  }
  ```
- **严重程度**: P2
- **现状**: while 循环只捕获 `InterruptedException`。`deliverWithRetries` 中 `JsonTool.stringify(...)`（对任意 AlertEvent message/payload 序列化）与 `HttpRequest.newBuilder(url)`（malformed URL 已在构造校验，但 stringify 无防护）抛出的任何 RuntimeException 会穿透 try 块终结 run()——单线程 executor 仍存活但永久空闲。
- **风险**: 一条畸形告警 payload → 投递线程死亡 → 后续所有告警在容量 100 的 `pending` 队列堆积，满了以后按“best-effort”静默丢弃（仅 WARN）→ **JOB_FAILED / RECOVERY_STARTED 等关键告警静默不再外发**，而 logging channel 之外无任何健康信号。与审计要点 7（消费/调度循环异常即静默死亡）完全同族。
- **建议**: `drainLoop` 内 per-event try/catch（对齐本库 `TaskManager.heartbeat()`、`JobCoordinator.detectFailures()` 的“containment + 下一轮重试”范式），并对 stringify 失败降级为 `String.valueOf(event)`。
- **信心水平**: 确定（异常面真实存在：JsonTool 对含循环引用/异常 toString 的对象会抛）
- **误报排除**: 不是“send() 已 offer 容错”的误报——send 的 offer 失败仅覆盖**入队**侧；死亡的是**出队投递**侧，队列满的 WARN 恰恰是该缺陷的可见症状而非保护。
- **复核状态**: 未复核

---

### [CC-07] OpsJobManager governanceSweep 周期任务无异常防护
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/OpsJobManager.java:330-334`（注册点）、`:348-395`（governanceSweep 主体）
- **证据片段**:
  ```java
  governanceSweeper = Executors.newSingleThreadScheduledExecutor(
          NopStreamThreadFactory.named("stream-ops-governance"));
  governanceSweeper.scheduleAtFixedRate(this::governanceSweep,
          governanceConfig.getCleanupIntervalMs(), governanceConfig.getCleanupIntervalMs(),
          TimeUnit.MILLISECONDS);
  ```
- **严重程度**: P3
- **现状**: `governanceSweep` 直接作为 `scheduleAtFixedRate` 的任务且体内无 try/catch。按 `ScheduledExecutorService` 语义，任何一次未捕获异常将**永久抑制后续执行**。
- **风险**: 任意一次 RuntimeException（如某个被注册的 coordinator 处于异常状态时历史/修剪路径抛错）→ 治理清扫静默停摆：checkpoint 历史超限增长、终态作业记录不再按保留期回收（`jobs` map 泄漏，进程生命周期内缓慢累积）。触发概率低（被迭代对象多为防御性实现），但失败模式为完全静默。
- **建议**: 包一层 try/catch（对齐同库 `TaskManager.heartbeat` `:373-429` 与 `JobCoordinator.detectFailures` `:1423-1534` 的显式防护范式——两者注释都点名了 scheduleAtFixedRate 的这条语义，说明本库已确立该纪律，此处是漏网点）。
- **信心水平**: 确定（模式缺陷本身确定；单次异常的具体触发源未实测）
- **误报排除**: 不是与 CC-06 重复计数——CC-06 是事件驱动消费线程，本条是 scheduleAtFixedRate 周期任务（JMM 语义不同：后者异常直接终止整个排程）。
- **复核状态**: 未复核

---

### [CC-08] CheckpointCoordinator.shutdown() 脱离监视器并发清场
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:1513-1585`（shutdown，非 synchronized）、`:604`（completePendingCheckpoint，synchronized）、`:1570-1576`（pending 遍历清场）
- **证据片段**:
  ```java
  public void shutdown() {
      isShutdown = true;
      ...
      for (PendingCheckpoint pending : pendingCheckpoints.values()) {
          long checkpointId = pending.getCheckpointId();
          notifyParticipantsFinishCommit(checkpointId, false);
          notifyCheckpointAborted(checkpointId);
          pending.dispose();
      }
      pendingCheckpoints.clear();
  ```
- **严重程度**: P3
- **现状**: 该类其余全部变更路径（trigger/ack/complete/abort）都在 `synchronized` 监视器内，唯独终态 `shutdown()` 不取监视器即读写 `pendingCheckpoints`/`numPendingCheckpoints`/participants 等。
- **风险**（时序）: 线程 A（persist executor）在 `executePersistAsync` 尾部等锁准备 `onCompletePersistSuccess` ∥ 线程 B（REST 关停）执行 `shutdown()`：B 遍历并 dispose pending（future 被 cancel，等待方收到 CancellationException 而非真实结果）、`participants.clear()`；A 随后进入 `onCompletePersistSuccess` → `pendingCheckpoints.remove(id, pending)` 失败 → skip——DURABLE 已落盘但完成副作用（participants commit 通知、事件、latestCompletedCheckpoint）被跳过 → **已提交纪元的 2PC finishCommit 通知丢失**（依赖下一纪元 subsuming commit 补偿；对作业最后一个纪元即尾部 commit 缺失）。同类交错也可使 `notifyParticipantsFinishCommit(id,false)` 记录一个已 COMMITTED 纪元的 false 终结标记。
- **建议**: `shutdown()` 加 `synchronized`（与同类全部变更路径对齐），并在 dispose 前先按状态分流：COMPLETED 的 pending 走完成通知而非 abort 通知。
- **信心水平**: 很可能（交错窗口真实存在；补偿机制使最坏后果从数据损坏降级为尾部 commit 延迟/缺失）
- **误报排除**: 不是“shutdown 是终态、无人并发”的误报——`JobCoordinator.stop()` 自身即在无公共锁的情况下先 cancel 任务再调 `checkpointCoordinator.shutdown()`（`:714-720`），而 ACK/commit forwarder 线程恰在 stop 前后仍在活跃提交（最后一次 checkpoint 的 persist 常发生在关停窗口内，C2/C3 机制还专门为此设计了 inline fallback，说明该并发是该路径的设计预期）。
- **复核状态**: 未复核

---

### [CC-09] TaskManager.receiveAssignment 与 cancelTask 同纪元竞态复活已取消任务槽
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:480-524`（receiveAssignment）、`:809-839`（cancelTask）
- **证据片段**:
  ```java
  String taskKey = taskKey(assignment);
  if (runningTasks.containsKey(taskKey)) {            // (1) 检查
      ... return;
  }
  RunningTask runningTask = new RunningTask(...);
  RunningTask existing = runningTasks.putIfAbsent(taskKey, runningTask);  // (2) 插入
  ```
  ```java
  public void cancelTask(...) {
      ...
      RunningTask task = runningTasks.remove(taskKey);   // (3) 取消
  ```
- **严重程度**: P3
- **现状**: (1)(2) 之间无锁；(3) 与 (2) 无公共锁。epoch fencing 只能挡**跨纪元**的迟到 assignment；同一纪元内（coordinator stop 与并发 assignTasks、或同代两次 cancel/assign 交错）无保护。
- **风险**（时序）: 线程 A（assignment RPC）步骤 (1) containsKey → false ∥ 线程 B（cancel RPC，coordinator stop fan-out）步骤 (3) remove → null（打出 "No running task to cancel" WARN，不释放许可）→ A 步骤 (2) putIfAbsent 成功 → 任务被部署并永久运行：协调器已 stop，此后不会再有任何 cancel/epoch 轮转（孤儿任务族，参见 CC-01 的根因段）。
- **建议**: `receiveAssignment` 的 (2) 改为“插入后二次校验”：putIfAbsent 成功后复查 `running` 且 assignment epoch 仍等于 `currentFencingEpoch`，不满足即按 cancel 处置（cancel + 释放许可 + 移除）；或为同 key 的 assign/cancel 引入 per-key 串行化。
- **信心水平**: 很可能（窗口真实；触发需 stop 与 assign 并发，受 OpsJobManager synchronized submit/stop 保护的情形不覆盖 RPC/launch 直连路径）
- **误报排除**: `deployTask` 路径无此问题（`:649-657` 的 put-with-displace 原子替换 + 旧槽 cancel 是正确范式），说明作者已在该路径处理过同族问题；`receiveAssignment` 是同族残留而非新臆想。
- **复核状态**: 未复核

---

### [CC-10] stop() javadoc 宣称反注册协调器，实际无 unregister
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:682-727`（stop 全文）；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/ClusterRegistry.java:25`（接口无 unregisterCoordinator）；`JdbcClusterRegistry.java:59`（registerCoordinator 为无租约 upsert）
- **证据片段**:
  ```java
  /**
   * Unregisters from the ClusterRegistry and shuts down internal services.
   */
  public void stop() {
      if (!running) {
          return;
      }
      running = false;
      active = false;
      ...
  ```
- **严重程度**: P3
- **现状**: javadoc 首句 "Unregisters from the ClusterRegistry" 与实现不符：方法体（与 `ClusterRegistry` 接口本身）都没有任何 coordinator 反注册。`registerCoordinator` 写入的是无租约的 upsert 行（不同于 node 租约的“租期到自动失效”机制）。
- **风险**: 每次 stop/failover 后注册表遗留指向死协调器的 coordinator 行（JDBC 表中带旧 fencing epoch）——运维查询、HA 诊断或未来按该表路由的消费者会读到陈旧主控信息；文档契约漂移（读者据 javadoc 假设资源被回收）。属真实维护成本/可观测性污染，非行为损坏。
- **建议**: 补 `unregisterCoordinator(jobId, coordinatorId)` 并在 `stop()` 调用；或修正 javadoc 说明“coordinator 行无租约、随节点生命周期留存”。
- **信心水平**: 确定
- **误报排除**: 不是“TaskManager.stop() 同样不反注册”的对称误报——TM 的 javadoc（`:304-309`）**明确记录**了“NOT explicitly unregistered（无 unregister API），靠租约失效消失”，是已裁决行为；coordinator 侧既无租约机制又宣称 unregistered，二者契约状态不同。
- **复核状态**: 未复核

---

## 验证为健全的重点机制（误报排除记录）

审计过程中对以下历史上标注过缺陷族的位置做了针对性复核，当前 live code 中**未发现**残留问题，列出判定依据以防后续轮次重复开题：

1. **物化重放 T1/T3/T3'/T4 协议（历史确认的数据错误族，CC-02/AR-04 修复）**: `ResultPartition.write`（`ResultPartition.java:289-297`）中 store 双写严格先于 queue enqueue（含 pool 路径：`acquire()` 只把间隔拉宽、方向安全）；`InMemoryMaterializationPoint` 的 write/replay 全 `synchronized`，T3 快照是原子切面；`assembleReplaySegment`（`SupervisionLoop.java:1143-1157`）用 `IdentityHashMap` 判重，而 store 保存的就是 producer 入队的同一实例——恒等检查成立。control 事件保留（AR-04）与 stale barrier 丢弃（`appendDrainedElements`）逻辑闭合。
2. **fencing 轮换推送半途失败（历史“作业级永久悬挂”族）**: `rotateFencingEpochCoreLocked`（`JobCoordinator.java:1992-2001`）per-node contain + CC-01 空工作集哨兵（`:1494-1511`）+ R5-CC-07 deploy 宽限期（`:1463-1490`、三个 fan-out 驱动点都调用 `recordDeploysIssued`）三重闭合。**残余见 CC-02**（fresh JVM 首激活失败时哨兵因 `everAssigned` 门禁失效——这是该修复族的真空白，不是重复计数）。
3. **abort 路径 future 永不完成（历史等待方空等族）**: `PendingCheckpoint.abort/fail/forceFail`（`PendingCheckpoint.java:180-240`）三条路径都以 completeExceptionally 收尾且幂等；`CheckpointCoordinator.onCompletePersistFailure` 以 `forceFail` 收尾（`:1045`）；`shutdown()` 的 `dispose()` 以 `cancel(false)` 收尾。未发现新的“future 悬挂”路径。
4. **REG-02 livenessLock**: COMPLETED 移除与在途 heartbeat merge 的互斥（`JobCoordinator.java:1072-1078` / `:1186-1202`）在锁内闭合；FAILED 分支的 `subtaskLiveness.put` 在锁外（`:1102`）但两侧值均为新鲜时间戳，merge(Math::max) 语义下无回退风险。
5. **CheckpointBarrierTracker 锁纪律**: 单监视器覆盖 inFlight 全部读写，ACK 回调在锁外触发（`fireAckCallbacks`，`:288-302`），与 mailbox/tracker 之间未发现锁序倒置。
6. **BufferPool**: `acquire()` 中断安全、close 后 acquire 的 release-back（`BufferPool.java:60-73`）、EOS sentinel 不占许可、drain 按元素释放——记账全路径平衡。
7. **周期任务异常防护正面清单**: `TaskManager.heartbeat`、`JobCoordinator.detectFailures`、`TaskCheckpointWiring.startBarrierScheduler`、`JobCoordinator.startPeriodicCheckpoints`、`JdbcLeaderElector.checkElection` 均有显式防护/自重排；缺口见 CC-07（governanceSweep）与 CC-06（事件消费线程）。

---

## 检查范围

**全文精读**（并发关键路径）:
- nop-stream-runtime: `coordinator/JobCoordinator.java`、`coordinator/AssignmentPlanner.java`、`taskmanager/TaskManager.java`、`taskmanager/RunningTask.java`、`taskmanager/CheckpointAckSender.java`、`checkpoint/CheckpointCoordinator.java`、`checkpoint/PendingCheckpoint.java`、`checkpoint/RetentionCleaner.java`、`execution/SupervisionLoop.java`、`execution/GraphModelCheckpointExecutor.java`（主体）、`execution/TaskCheckpointWiring.java`、`execution/EmbeddedDistributedExecutor.java`、`execution/RpcDistributedExecutor.java`、`execution/RescaleStateAssembler.java`（前 250 行，静态纯函数）、`transport/RemoteInputChannel.java`、`transport/RemoteResultPartition.java`、`cluster/JdbcLeaderElector.java`（选举主体）、`health/JobHealthStateMachine.java`、`ops/OpsJobManager.java`、`ops/StreamOpsHttpServer.java`（结构核查）、`deploy/RemoteTaskDeploySupport.java`、`alert/WebhookAlertChannel.java`、`alert/AlertService.java`（接口面）、`event/StreamJobEventBus.java`
- nop-stream-core: `execution/InputGate.java`、`execution/InputChannel.java`、`execution/ResultPartition.java`、`execution/CheckpointBarrierTracker.java`、`execution/RecordWriter.java`、`execution/TaskMailbox.java`、`execution/MailboxExecutor.java`、`execution/ProcessingTimeServiceDriver.java`、`execution/TaskProcessingTimeService.java`、`execution/task/SubtaskTask.java`、`execution/task/TaskExecutor.java`、`execution/task/StreamTaskInvokable.java`、`execution/buffer/BufferPool.java`、`execution/materialization/InMemoryMaterializationPoint.java`
- **定向核查**（grep/片段级）: `JdbcClusterRegistry`、`cluster/ClusterRegistry` 接口、`InMemoryClusterRegistry`、`TimerServiceManager`、`checkpoint/CheckpointConfig`（默认值）、`maintain/StreamMaintenanceMain`、`launch/ClusterPipelineFactory`（引用面）、`ops/StreamOpsHttpServer` 生命周期、`metrics/EngineMetrics`/`TaskNodeMetrics` 的 release 配对（引用面）、`WebhookAlertPayload`。
- **未纳入**: `_` 前缀生成产物、`target/`、测试代码（`JobCoordinatorMain`/`MiniStreamCluster` 位于 test 源集，仅作为生命周期契约旁证引用）、windowing/operator 业务算子内部（非本轮范围）、`nop-stream-core/source/coordinator`（FLIP-27 协调器，属 source-api 审计线）。

---

## 深挖第 2 轮追加

- **轮次日期**: 2026-10-05（与初审同 HEAD：`bd089444f5c2b1fcd2c9a964ce909bac774a83d9`）
- **范围**（初审盲区定向）: runtime/rpc 请求-响应匹配全链（`StreamControlRpcProxyFactory`/`StreamControlRpcServer` + 平台 `MessageRpcClient`/`RpcChannelState`）、core/execution/task 任务状态机并发转换（`Task`/`SubtaskTask`/`TaskStateTransition`/`TaskExecutor`）、buffer 包（`BufferPool` 引用记账复核 + `PerKeyOrderedBuffer` 恢复语义 + `EquiJoinOperator` restore 链路）、cluster 节点成员变更（`JdbcClusterRegistry`/`StreamNodeAutoRegistration`/`NodeDiscoveryConsistencyChecker`/`updateFencingToken` 驱逐语义）、launch/maintain 进程清理面、`JobHealthStateMachine` 检测器线程安全、JobCoordinator 状态机盲区（`start()` HA 对账、`terminate` 四模式、`reportTaskStatus`/`reportNodeTaskLiveness` 门禁、`globalRecovery` 再指派语义）、InputGate abort 后对齐清理与 pending-emission drain。
- **方法**: 只读源码审计，逐文件全文精读；每条候选做双线程/恢复时序推演，推演被证伪的候选记入本轮"已验证健全"清单防重开。未运行构建/测试；未修改产品代码。

### [CC-11] PerKeyOrderedBuffer 恢复不合并 insertionSeq：(ts,seq) 碰撞静默覆盖已恢复条目，failover 后 join 缓冲数据丢失
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/buffer/PerKeyOrderedBuffer.java:37`（`insertionSeq` 字段）、`:56-61`（`putAll` 不合并计数器）、`:71-76`（`add` 的 `tree.put` 等键覆盖语义）；佐证 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/join/EquiJoinOperator.java:129`（`open()` 每次 new 实例，计数器归零）、`:150-167`（`restoreState` → `putAll`）、`:170-183`（`processElement` → `add` + `persistCurrent`）、`:349-362`（`durableKey` 以 `(bufferKey, ts, seq)` 命名 keyed-state 条目）
- **证据片段**:
  ```java
  // putAll（恢复路径）只合并 buffers，不推进 this.insertionSeq：
  public void putAll(PerKeyOrderedBuffer<K, V> other) {
      for (Map.Entry<K, TreeMap<long[], V>> e : other.buffers.entrySet()) {
          TreeMap<long[], V> tree = buffers.computeIfAbsent(e.getKey(), k -> new TreeMap<>(TsSeqComparator.INSTANCE));
          tree.putAll(e.getValue());
      }
  }
  // add：等 (ts, seq) 键直接替换既有条目
  long[] tsKey = new long[]{timestamp, insertionSeq++};
  tree.put(tsKey, value);
  ```
- **严重程度**: P1（影响类别为静默数据丢失，属 P0 类；触发概率由 (key, ts, seq) 碰撞门控——恢复后计数器从 0 重扫旧 seq 空间，早恢复/粗时间戳/热键场景下确定性触发，故定 P1）
- **现状**: checkpoint 快照把整个 `PerKeyOrderedBuffer`（含旧实例已累加的 seq 值）序列化为 operator state；恢复路径却是 `open()` 先 `new PerKeyOrderedBuffer<>()`（`insertionSeq=0`）、`restoreState` 再 `putAll` 合并条目——**恢复条目保留旧 seq，而计数器不随之推进**。此后任何与恢复条目同 `(key, ts)` 的新插入，其新分配 seq 落在旧 seq 空间内且相等时，`tree.put` 直接替换恢复条目，`persistCurrent` 又以同一 `durableKey` 覆盖 keyed-state 持久副本——工作缓冲与持久状态**同时**静默丢失被恢复的记录。
- **风险**（完整时序推演）:
  1. T0：EquiJoinOperator 首条缓冲记录 A（equiKey=K、左流、ts=100）入缓冲，分配 tsKey=(100, **0**)，durable mirror 写 `K|L|100|0 → A`。
  2. T1：checkpoint 落盘（operator state 携带 buffer，A 的键为 (100,0)，源实例 insertionSeq=1）。
  3. T2：任务失败 → 恢复重部署 → `open()` 新建 buffer（insertionSeq=0）→ `restoreState` putAll 进 (100,0)→A；**insertionSeq 仍为 0**。
  4. T3：同键同 ts 的新记录 B 到达（事件时间秒级粒度下同 (key, ts) 重放是常态输入）→ `emitMatches` 先对另一侧匹配（A、B 同侧则无匹配发出）→ `buffer.add("K|L", 100, ...)` 分配 (100, **0**) → `tree.put` 把 A 替换为 B → `persistCurrent` 以同一 durableKey `K|L|100|0` put，keyed state 中 A 的持久副本同步被覆盖。
  5. 结果：A 从工作缓冲与持久状态同时消失——A 本应参与的后续匹配/OUTER 补全（`completeUnmatched`）永不发生；无异常、无日志、无指标。且计数器一旦越过恢复集最大 seq 就永不再碰撞——**窗口不可自愈、不可事后检测**，只能以输出缺失的形式呈现。
  6. 加重项：rescale/多实例 operator-state union 恢复时，多个源实例的 seq 空间重叠叠加，碰撞面进一步扩大。
- **建议**: `putAll` 尾部推进 `this.insertionSeq = Math.max(this.insertionSeq, maxSeqOf(other) + 1)`（对每个 tree 取 `lastKey()[1]` 的最大值）；或 `add` 改为 per-key 分配 `tree.lastKey()[1] + 1`。同时为恢复路径补一条断言：恢复后 `insertionSeq` 必须严格大于恢复集内最大 seq。
- **信心水平**: 确定（机制为 live code 直读的纯数据结构缺陷，无并发假设；触发仅依赖"同 (key, ts) 记录在恢复后再次到达"这一常态输入模式）
- **误报排除**: 不是"序列化已保留 insertionSeq"的误报——快照对象确实携带旧计数器，但恢复路径不采用该对象，而是 putAll 进 `open()` 新建的归零实例；也不是与初审负面清单第 6 条（BufferPool 记账）或物化重放 T1/T3 协议重复——那两族是并发/通道协议问题，本条是恢复合并语义缺陷，且 `PerKeyOrderedBuffer` 初审仅做了"结构核查"，未覆盖其恢复语义。
- **复核状态**: 未复核

---

### [CC-12] SubtaskTask/Task 终态收尾用裸 set 而非 CAS：并发 cancel 下 CANCELING 可被 COMPLETED/FAILED 覆盖，偏离 G58 统一状态机
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/SubtaskTask.java:121-131`（成功路径双 get + 裸 set）、`:132-141`（catch 路径裸 set）、`:148-174`（cancel 的 CAS 循环）；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/Task.java:219-231`（catch 路径 validate-then-set，注释自认 "fallback to set for safety"）；对照 `Task.java:204-217`（成功路径正确使用 compareAndTransition）；`TaskStateTransition.java:48`（`CANCELING → CANCELED` 是 CANCELING 唯一出边）
- **证据片段**:
  ```java
  // SubtaskTask.run() 成功路径：get 与 set 之间无原子性
  if (state.get() == State.RUNNING) {
      state.set(State.COMPLETED);
  } else if (state.get() == State.CANCELING) {
      state.set(State.CANCELED);
  }
  ```
- **严重程度**: P3
- **现状**: `cancel()` 严格按 CAS 循环进入 CANCELING，并把 CANCELING→CANCELED 的收尾职责显式交给 run 循环（RUNNING 态取消时不代收）。但 run 循环一侧的终态收尾在 `SubtaskTask` 的两个出口与 `Task` 的 catch 出口都是"先 get 后裸 set"，get 与 set 之间 cancel 的 CAS 可插入，覆盖语义不受 `TaskStateTransition` 校验（未走 `compareAndTransition`）。
- **风险**（时序推演）:
  1. 线程 T（任务线程）：`invoke()` 正常返回 → 行 125 `state.get()==RUNNING` 成立；
  2. 线程 C（取消线程，如 LOCAL abort handler 的 cancelAll）：CAS RUNNING→CANCELING 并 interrupt（T 已离开阻塞点，interrupt 无效果）；
  3. 线程 T：行 126 `state.set(COMPLETED)` 覆盖 CANCELING → 终态 COMPLETED 而非 CANCELED——`cancel()` 已返回 true（宣称完成转换），但约定的 CANCELING→CANCELED 收尾被吞，违反 "CANCELING 唯一出边是 CANCELED" 的机器不变式；
  4. 变体：`invoke()` 因 cancel 的 interrupt 抛出 → catch 行 134 读到 RUNNING → C 线程 CAS RUNNING→CANCELING → T 行 139 `set(FAILED)` 覆盖 → 取消中的任务被判 FAILED。LOCAL 嵌入路径下 SupervisionLoop 以 SubtaskTask 状态为失败信号源，可在计划内 stop 尚未收尾时观察到 FAILED 并触发一次伪 failover（白烧一次恢复预算、与 stop() 的 cancel fan-out 交错）。
- **建议**: 两处收尾改用 CAS 并对齐 `Task.java:204-217` 的纪律：`compareAndSet(RUNNING, COMPLETED)` 失败后读当前态，为 CANCELING 则 `compareAndSet(CANCELING, CANCELED)`；catch 路径同理以 `compareAndSet(current, FAILED)` 表达，删除 "fallback to set"。
- **信心水平**: 确定（窗口为 get→set 两条指令，机制与 CC-03 同族 check-then-act；后果限于终态标签错记与窄窗口伪恢复，未达 P2 的行为损坏门槛）
- **误报排除**: 不是"terminal 态互相覆盖无所谓"的误报——FAILED 与 CANCELED 的语义分叉驱动协调器不同的恢复决策（FAILED 触发 recovery，CANCELED 不回传），且该缺陷恰位于两个文件自证的统一模型（G58 注释 + `TaskStateTransition` 合法转换表）宣称已闭合的路径上；与 CC-03 不重复计数——那是 InputGate 跨结构对齐状态，本条是单一 AtomicReference 的收尾序列。
- **复核状态**: 未复核

---

## 深挖第 2 轮：验证为健全的重点机制（误报排除追加）

1. **runtime/rpc 请求-响应匹配**: 配对/超时/重复响应委托平台 `MessageRpcClient` + `RpcChannelState`（`waitFutures.remove(id)` 吸收重复响应并记 unmatched 日志；超时由 `ApiHeaders` timeout 驱动，`timeout<=0` 显式不排程；`onChannelClose` 清空全部等待方）。nop-stream 侧 `IStreamTaskRpcService`/`IStreamCoordinatorRpcService` 类头文档化了逐条目的 one-way 契约与丢失自愈机制（R5-CC-17 doc-first），`CorrelatingRpcService` 补 relId 闭环。唯一带返回值的 `getJobStatus` 在 live code 无跨 JVM 调用方，无悬挂面。
2. **【候选证伪记录，防第 3 轮重开】retained-entry 阻塞再部署**: 候选缺陷为"RunningTask 成功后保留注册表项（`RunningTask.java:149-170`）× `TaskManager.receiveAssignment:492` containsKey 拒绝 → globalRecovery 无法为已完成子任务重部署"。时序推演**证伪**：`TaskManager.updateFencingToken`（`:935-956`）在 epoch 轮换时 `removeIf` 清除**所有**旧 epoch 条目并 cancel，故 `globalRecovery → rotateFencingEpochCoreLocked` 的 epoch push 到达 TM 后 retained 条目即被清除，再部署不被阻塞；retain 注释宣称的 "the entry is replaced by any redeployment" 在轮换语义下闭合。push 失败路径由 fencing mismatch 显式拒绝 + reportAssignmentFailure 覆盖，非静默。
3. **僵尸心跳掩盖新 attempt 停滞**: `reportNodeTaskLiveness` 已有 CC-05 的 attempt-number 拒绝 + `completedSubtaskKeys` tombstone（`JobCoordinator.java:1164-1203`），旧代任务心跳无法为已迁移槽位续命。
4. **abort 后对齐状态清理**: `abortBarrierAlignment` 解锁 blockedChannels（`InputGate.java:1162-1172`）；`emitPendingBarriers` 对 aborted 的 pending emission 解锁通道并跳过（`:780-792`）；`emitCompletedAlignment`/`switchToUnalignedAndEmit` 均有 `cleanupAbortedBarriersUpTo` 有界清理。除 CC-03 已记录的残余窗口外闭合。
5. **JobHealthStateMachine 检测器自身线程安全**: `transition` synchronized、非法转换 fail-fast 抛出、监听者异常遏制；globalRecovery 的 health 回调走 `notifyRecovery*Safely` 包裹，与并发 failJob 交错的转换异常被遏制，无失控传播。
6. **launch/maintain/deploy 进程清理盲区不存在**: 生产主源集 grep `ProcessBuilder`/`exec`/`addShutdownHook` 为空；子 JVM 孵化仅存在于 test 源集（`JobCoordinatorMain`），无僵尸进程/退出钩子缺陷面。`StreamMaintenanceMain`/`StreamStateResetTool` 为离线 fail-fast CLI，无守护并发面。
7. **Task 状态机部署链**: `CREATED→SCHEDULED→DEPLOYING→RUNNING` 为 CAS 串行，`run()` 入口检查与 cancel 的交错由 `transitionFromCreatedTo` 的"非法即 false"闭合；`cancel` 的 CAS 循环本身正确。残余仅 CC-12 的终态收尾两点。
8. **TaskExecutor 提交门禁**: `isShutdown` 前置检查 + 固定池问题已由初审 CC-05 覆盖；`submittedTasks`/`taskFutures` 随 TaskExecutor 实例生命周期存活（每 job 一实例），无跨 job 泄漏面。

---

## 维度复核结论

- **复核日期**: 2026-10-05（独立维度复核轮，HEAD 不变：`bd089444f5c2b1fcd2c9a964ce909bac774a83d9`）
- **方法**: 只读重验。对 CC-01..12 逐条重走 live code 调用链与时序推演；P1 三条（CC-01/CC-02/CC-11）做了全链深验证（含平台 elector `AbstractLeaderElector`/`JdbcLeaderElector`、TaskManager 心跳与租约、CheckpointSerDe 序列化、operator open/restore 时序）；负面清单做了抽查。未运行构建/测试；未修改产品代码与本文已有发现内容。

### 逐条裁决

**CC-01（failJob 孤儿任务）— 保留，P1。**
全部环节直读证实：(a) `failJob`（JobCoordinator.java:775-798）仅置 `jobStatus=FAILED`、`active=false`、关停 failureDetector，`running` 保持 true，无任何 cancel fan-out / `stop()` 调用；grep 证实全部三个调用点（:1720、:1730 预算耗尽、:2427 终端 savepoint 失败）均无后续 `stop()`，而对称的 `terminateCancel`（:2306-2319）显式 `stop()`→`cancelAllAssignedTasks`（:700），契约不对称自证。(b) 运维堵死三点全证实：`terminate` 的 active 门禁（:2281-2284 no-op+WARN）、`OpsJobManager.remove` 的 `isRunning()` 拒绝（OpsJobManager.java:299-301）、`submit` 的 `JOB_ALREADY_HOSTED`（:129-131）；追加证实治理清扫同样跳过该记录（OpsJobManager.java:383 的 `!c.isRunning()` 条件对 failJob 后的 coordinator 永假）。(c) TM 侧无看门狗证实：`heartbeat()` 独立续租（TaskManager.java:375-379），liveness 上报被协调器拒后仅 WARN（:419-423、:425-429），无任何"协调器已死"自停路径。(d) 2PC 悬挂证实：distributed abort handler 被 active 门禁挡（JobCoordinator.java:2517-2521），commit forwarder 仅在 checkpoint 完成时触发，failJob 后无纪元可完成。(e) 次级窗口证实：`stop()`（:685-727）不取 `recoveryLock`，`executeAssignmentFanOut`（AssignmentPlanner.java:232-256）无 `running`/`active` 复查，epoch 未轮转时 TM 无法拒收迟到 dispatch。无反证。

**CC-02（半激活 leader）— 保留，P1。**
三点全部成立且补齐了平台级证据：(a) `active=true` 先于锁段置位（:2113-2117），`rotateFencingEpochCoreLocked` 的 LEADERSHIP_GRANT rebuild 失败在 :2026-2031 fail-loud 抛出且此时工作集已清空（:1954），`prepareAssignmentsLocked` 的 rpc 缺失抛出在 AssignmentPlanner.java:168-173——两处抛出均无去激活；`seedAttemptCountersFromRegistryLocked` 失败则显式 `active=false; currentLeadership=null`（:2216-2231），相邻纪律反证前者是遗漏。(b) elector 吞异常从 in-file 注释升级为平台代码证实：`AbstractLeaderElector.onBecomeLeader`（nop-cluster-core AbstractLeaderElector.java:183-191）per-listener catch+log，异常根本到不了 elector 的选举循环；`JdbcLeaderElector.checkLeader`（:184-217）持锁期间仅刷新租约、不重放 becomeLeader。(c) 同 epoch 守卫（:2104-2108）确实吞掉重放；哨兵 `everAssigned` 门禁（:1505-1510）与 fresh JVM 首次 grant 的故障前提互斥。一处轻微收窄（不改裁决）：运维侧 `restartElection`（JdbcLeaderElector.java:153-176）或租约过期 ping-pong 会产生严格更大的新 epoch 并触发完整重激活——存在**需人工干预**的逃生通道，但稳态 leadership 下无自愈，"作业级永久悬挂"定性不变。

**CC-03（abort×迟到 barrier 竞态）— 保留，P2。**
结构性证据全部吻合：(A) `abortedBarriers.contains`（InputGate.java:981）与 (B) `inFlightAlignments.put`（:1003-1006）之间无公共锁；两结构注释（:130-140、:163-166）明示跨线程并发设计；`abortBarrierAlignment` add-first（:1163-1164）及其注释（:1151-1160）自证只修了对称窗口。线程前提证实：abort handler 由 checkpoint 中止路径在非任务线程触发（TaskCheckpointWiring.java:323-372，:337-344 注释明言 cross-thread）。后果分支证实：泄漏 alignment 为 min-id、其余通道 X barrier 在 (A) 被弃（:981-989）、`markFinishedChannel` 仅 FINISH 补齐（:1070-1096）；`checkAlignmentElapsed` 30s 抛出（:856-858，默认 30000ms CheckpointConfig.java:29）或 1s unaligned 切换（:852-853，阈值 1000ms :30、默认开 :38）。窗口窄但触发前提恰是 abort 的常态前置（barrier 已在途），"很可能"定级恰当。

**CC-04（重授予不 abort 遗留 pending）— 保留，P2。**
grep 证实 `abortAllPendingCheckpoints` 主源集仅一处调用（JobCoordinator.java:1763，globalRecovery）；`activateAsLeader` 全文（:2097-2147）无 abort。同 JVM 语义证实：pending 属同一 CheckpointCoordinator 实例，重授予后旧 ACK 被双重门禁拒绝（active 门禁 :970-974 + epoch 比较 ：986-989，TM 旧代任务被 `updateFencingToken` 的 removeIf 清除后 ACK 永不再来），`tryTriggerCheckpointWithReason` 的 maxConcurrent 门禁（CheckpointCoordinator.java:450-454）压制冷新触发直至 pending 走满自身 timeout（scheduleTimeout :1415-1425；默认 600s CheckpointConfig.java:28，OpsJobManager 路径 10s :145）。`deactivateToStandby` 注释（:2250-2253）"aborted by the new leader"只在跨 JVM 成立，与初审判断一致。影响面依赖部署形态，P2 恰当。

**CC-05（本地池固定核数死锁）— 保留，P2。**
全部锚点证实：`DEFAULT_POOL_SIZE = availableProcessors()`（TaskExecutor.java:77）、`newFixedThreadPool`（:116-117）；三个生产调用点全为无参构造（GraphModelCheckpointExecutor.java:258、:373、StreamExecutionEnvironment.java:503）；SupervisionLoop 对 `tasks.values()`（ConcurrentHashMap，无拓扑序）一次性提交（SupervisionLoop.java:274-275）；`ResultPartition` 阻塞 `queue.put` 在 pool+无物化与无 pool+无物化两分支均存在（ResultPartition.java:346-376），容量 1024（:51、:210）；本地 SupervisionLoop 路径无外层超时。死锁推演（P>N、上游占满线程写满下游队列、排队任务饿死）机制成立。

**CC-06（Webhook 投递线程死亡）— 保留，P2（附证据微瑕）。**
`drainLoop`（WebhookAlertChannel.java:138-150）只捕获 InterruptedException；`JsonTool.stringify`（:153）在内层 try 之外，任何 RuntimeException 穿透终结 run()，单线程 executor 存活但永久空闲，后续告警在队列堆积后按 best-effort 丢弃（:122-126）。证据微瑕（不影响机制）：队列容量实为 `QUEUE_CAPACITY = 256`（:49），发现文本写"容量 100"。定级理由成立：死亡的是故障时刻最需要的带外告警通道，且违反本库自立的 containment 纪律（TaskManager.heartbeat :368-373 注释明确该范式）。

**CC-07（governanceSweep 无防护）— 保留，P3。**
`scheduleAtFixedRate(this::governanceSweep)`（OpsJobManager.java:330-334）且 `governanceSweep`（:348-395）体内无 try/catch，ScheduledExecutorService 语义下单次未捕获异常永久抑制后续执行——标准 JMM/SDK 语义，无争议。对照纪律自证：同库 `TaskManager.heartbeat`（:368-373）与 `JobCoordinator.detectFailures`（:1423）均有显式防护。触发概率低但失败模式完全静默，P3 恰当。

**CC-08（shutdown 脱离监视器）— 保留，P3。**
`shutdown()`（CheckpointCoordinator.java:1513-1585）确为该类唯一非 synchronized 的变更路径（trigger :449、ack :573、complete :604、abort :1062、abortAll :1210、markTaskCompleted :1357 均 synchronized）。并发前提有 in-code 自证：:657-658 注释明言 "terminal shutdown racing with a final ACK" 是设计预期交错。后果推演成立：persist 线程在 ：721-723 等锁期间，shutdown 遍历到 status=COMPLETED 但未移除的 pending（:1570-1575）→ 对已 DURABLE 纪元记 false 终结标记并 dispose → 持锁后 `pendingCheckpoints.remove(id, pending)` 失败（:942-944）→ 完成副作用全链跳过，终态 shutdown 下无下一纪元 subsuming 补偿 → 尾部 commit 通知丢失。非终态场景有补偿路径，P3 恰当。

**CC-09（同纪元 assign/cancel 竞态）— 保留，P3。**
`receiveAssignment` 的 containsKey（TaskManager.java:492）与 putIfAbsent（:511）之间无锁、`cancelTask` 的 remove（:828）与之无公共锁；epoch 校验（:469-476）只挡跨纪元。同纪元并发 assign×cancel 的现实入口存在：CC-01 次级窗口（stop 与 recovery fan-out 无串行化）与 launch 直连路径。deployTask 的 put-with-displace + 旧槽 cancel（:649-657）证明同族问题已有正确范式，此处是残留。P3 恰当。

**CC-10（stop javadoc 契约漂移）— 保留，P3。**
javadoc "Unregisters from the ClusterRegistry"（JobCoordinator.java:683）与方法体（:685-727）及 `ClusterRegistry` 接口（grep 证实无任何 unregister 方法）均不符；`JdbcClusterRegistry.registerCoordinator`（:59-85）为 delete+insert upsert、无租约列。TM 侧 javadoc 对称性反驳成立（TaskManager.java:304-309 明确记录"NOT explicitly unregistered"是已裁决行为）。维护成本/可观测性污染，非行为损坏，P3 恰当。

**CC-11（PerKeyOrderedBuffer 恢复 seq 碰撞）— 降级 P1 → P2；修正与 ST-12 的关系判定。**
组件级事实全部证实：`putAll` 不推进 `insertionSeq`（PerKeyOrderedBuffer.java:56-61）、`add` 等键 `tree.put` 静默替换（:71-76）、`durableKey` 以 `(bufferKey, ts, seq)` 命名（EquiJoinOperator.java:360-362）、WI13 测试契约 open→restore 下碰撞可达（TestEquiJoinWithCheckpoint.java:50,91）。**但生产可达链断裂**——本轮新证据链证实所有生产恢复路径的 restore 都发生在任务线程 `open()` 之前：
- 时序锚点：`SubtaskTask.run` 注释自证 "The invokable's single open happens inside invoke()"（SubtaskTask.java:103-112）；`StreamTaskInvokable` 四个 invoke 变体才调 `operatorChain.open()`（:653/:743/:801/:844）；本地骨架 restore（GraphModelCheckpointExecutor.java:247→:1363）先于 `buildTasks`（:257）；SupervisionLoop 重建在 SubtaskTask 构造前 restore（SupervisionLoop.java:934）；remote-deploy 在 `setInvokable` 前 restore（RemoteTaskDeploySupport.java:140）。**决定性自证**：`WindowOperator.restoreState` 注释明言 "restoreState runs before open()"（WindowOperator.java:683-685），并以 deferred-to-open 模式正确处理——EquiJoin/OverWindow 未遵循该纪律。
- 因此恢复时 `buffer` 为 null：(a) 同 JVM 内存恢复路径（SupervisionLoop region restart，快照对象是活实例、instanceof 通过）→ `buffer.putAll` NPE（EquiJoinOperator.java:164）→ `restoreOperatorsFromState` 重抛 → `ERR_STREAM_REGION_RESTART_UNSUPPORTED`——**响亮失败**，非静默覆盖；(b) 跨 JVM/跨进程恢复路径——checkpoint 持久化为 JSON（CheckpointSerDe.java:119 JsonTool.serialize）→ 反序列化条目是 Map、instanceof 不通过 → putAll 被跳过 → **工作视图根本未合并**，不存在可碰撞的恢复条目（此即 ST-12 族"零恢复"管道）。
- 裁决：所述"(ts,seq) 碰撞静默覆盖"场景在本 HEAD 不可达，P1 的"确定性触发"前提不成立；但 `putAll` 不合并计数器是真实、确定、修复建议正确的组件级缺陷——一旦恢复管道修复（merge 真正执行），它立即成为无异常、无日志、不可检测的静默数据丢失点。故**降级保留为 P2**。
- **与 ST-12 关系修正**：二者不是对等的"同管道相邻缺陷"，而是**遮蔽关系**——ST-12 族的零恢复/恢复时序缺陷当前挡在 CC-11 触发链之前；CC-11 是修复恢复管道后必然暴露的**后续缺陷**（修复任一恢复路径时必须同步修 `putAll` seq 合并，否则把静默失败换成静默数据丢失）。
- **复核新增观察（建议另立新发现，不在本文件展开）**：`EquiJoinOperator.restoreState`（:164）与 `OverWindowOperator`（:145、:303）在 restore-before-open 时序下对 null transient buffer 直接 putAll——同 JVM 恢复路径 NPE、JSON 路径工作视图静默零恢复；`WindowOperator`（:683-685 deferred 模式）是库内已确立的正确范式。

**CC-12（终态收尾裸 set）— 保留，P3。**
全部锚点证实：SubtaskTask 成功路径双 get + 裸 set（SubtaskTask.java:125-131）、catch 路径裸 set（:134-141）、cancel 的 CAS 循环把 CANCELING→CANCELED 收尾交给 run 循环（:148-174）；Task catch 路径 validate-then-裸-set 且注释自认 "fallback to set for safety"（Task.java:222-231）、成功路径正确用 `compareAndTransition`（:204-217）——不对称自证；`CANCELING → CANCELED` 是 CANCELING 唯一出边（TaskStateTransition 合法转换表）。get→set 窗口两条指令、后果限于终态标签错记与窄窗口伪恢复，P3 恰当。

### 负面清单抽查

抽查通过，未发现需要重开的"已验证健全"项：
- 物化重放协议（负面清单 1）、fencing 轮换三重闭合（负面清单 2，含 CC-01 哨兵 `everAssigned` 门禁 ：1505-1510 与 R5-CC-07 三个 fan-out 驱动点 ：861/:1793/:2145）、REG-02 livenessLock（负面清单 4，FAILED 分支锁外 put 的 Math::max 论证成立）：复核证实。
- 深挖轮证伪记录 2（retained-entry 不阻塞再部署）：`TaskManager.updateFencingToken` 的 `removeIf` 清除旧 epoch 条目并 cancel（TaskManager.java:945-954），证伪逻辑成立。
- 僵尸心跳（深挖轮 3，:1178-1185 attempt 拒绝 + :1190-1192 tombstone）、abort 后对齐清理（深挖轮 4）、launch/maintain 无进程孵化面（深挖轮 6，grep `ProcessBuilder`/`exec`/`addShutdownHook` 主源集为空）：复核证实。
- CheckpointBarrierTracker 锁纪律、BufferPool 记账、JobHealthStateMachine、runtime/rpc 匹配（负面清单 5/6 与深挖轮 1/5）：本轮未逐行重验，作为负面断言接受，未发现任何相矛盾的证据。

### 复核总结

12 条发现：**11 条保留原级，1 条降级**（CC-11 P1→P2）；无驳回。初审的行号引用、时序推演与严重程度定级整体准确度高；唯一实质性修正是 CC-11 的生产可达性（restore-before-open 时序 + JSON 反序列化双重点断了静默碰撞链），并由此新增一条建议立项的相邻观察（EquiJoin/OverWindow 的 null-buffer putAll 违反 WindowOperator 已确立的 deferred-restore 纪律）。

## 最终保留项

| 编号 | 裁决 | 严重程度 | 一句话裁决理由 |
|---|---|---|---|
| CC-01 | 保留 | P1 | failJob 无 cancel fan-out 三调用点、运维 remove/submit/terminate/清扫四路全堵、TM 无看门狗、2PC 悬挂，全部直读证实 |
| CC-02 | 保留 | P1 | active 先置位 + elector 吞异常（平台代码证实）+ 同 epoch 守卫挡重试三点全成立；仅存需人工干预的 restartElection 逃生通道 |
| CC-03 | 保留 | P2 | (A)/(B) 无公共锁、add-first 只修对称窗口、泄漏 alignment 30s 后转任务失败，结构与线程前提全部证实 |
| CC-04 | 保留 | P2 | abortAllPendingCheckpoints 全库仅 globalRecovery 一处，同 JVM 重授予遗留 pending 压制新 checkpoint 至满 timeout |
| CC-05 | 保留 | P2 | 三生产点固定核数池 + 长期阻塞任务 + 无拓扑序提交，P>N 死锁推演机制成立且无校验/告警 |
| CC-06 | 保留 | P2 | drainLoop 无 per-event containment，stringify 在内层 try 之外，告警通道静默死亡（容量实为 256，证据微瑕不改机制） |
| CC-07 | 保留 | P3 | scheduleAtFixedRate 裸任务单次异常即永久停摆，同库防护纪律自证此处是漏网点 |
| CC-08 | 保留 | P3 | shutdown 非 synchronized 与全部变更路径不对齐，并发预期有 in-code 自证，最坏后果为尾部 commit 缺失 |
| CC-09 | 保留 | P3 | containsKey/putIfAbsent 与 cancel remove 无公共锁，deployTask 已有正确范式的同族残留 |
| CC-10 | 保留 | P3 | javadoc 宣称 unregister 而接口/实现均无，无租约 upsert 行遗留，契约漂移属实 |
| CC-11 | **降级** | **P2** | 组件缺陷与证据全部真实，但生产 restore-before-open 时序 + JSON instanceof 双重点断了静默碰撞可达链（内存路径 NPE 响亮失败、JSON 路径零合并）；系被 ST-12 族遮蔽的后续缺陷，修复恢复管道时必须同步修 |
| CC-12 | 保留 | P3 | 终态收尾 get→裸 set 与 cancel CAS 的竞态真实，后果限于标签错记与窄窗口伪恢复 |
