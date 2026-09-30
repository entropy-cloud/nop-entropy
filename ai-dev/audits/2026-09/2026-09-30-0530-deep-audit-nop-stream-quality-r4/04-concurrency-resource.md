# nop-stream 并发与资源管理深度审计报告（R4 轮 / R5-CC 系列）

- 审计日期：2026-09-30
- 审计范围：nop-stream 运行时子系统（LOCAL GraphExecutionPlan+SupervisionLoop；DISTRIBUTED JobCoordinator/TaskManager/RPC 控制面与数据面）
- 审计方式：live code 逐文件通读 + 跨线程交错推演。所有行号以当前工作区为准。
- 前置约定：R4-N1/N2/N3/N5（plan 366，2026-09-29）已知已修复项不重复报告；本报告对 R4-N1 的已知 follow-up（回放窗口"先存缝隙"）给出评估结论（R5-CC-02）。
- 严重程度标尺：P0=死锁/永久悬挂/数据损坏；P1=竞态导致错误状态/资源泄漏在高频路径；P2=局部缺陷；P3=低优先级。

## 汇总

| 严重程度 | 数量 | 编号 |
|---|---|---|
| P0 | 0 | — |
| P1 | 3 | R5-CC-01, R5-CC-02, R5-CC-03 |
| P2 | 5 | R5-CC-04, R5-CC-05, R5-CC-06, R5-CC-07, R5-CC-08 |
| P3 | 9 | R5-CC-09 … R5-CC-17 |

共 17 项。无 P0。最严重三项（P1）：恢复临界区半途失败导致作业永久 wedge（R5-CC-01，准 P0）；物化回放"先存缝隙"竞态导致 exactly-once 边双投递（R5-CC-02）；cancel/stop 不回收远程任务形成孤儿任务（R5-CC-03）。

---

### [R5-CC-01] fencing 轮换推送无 per-node 异常隔离且工作集先清空——恢复半途失败后作业永久 wedge（准 P0）

**文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java` L1600-L1615（rotateFencingEpochCoreLocked）、L1437-L1511（globalRecovery 锁内段）、L1194-L1249（detectFailures）

**证据片段**（rotateFencingEpochCoreLocked，先 clear 后 push，push 无 try/catch）：
```java
// L1605-1615
// Clear the in-memory working set only. ...
taskAssignmentMap.clear();
allTaskLocations.clear();

// Push the rotated fencing epoch to all registered TaskManagers ...
for (IStreamTaskRpcService rpc : taskRpcServices.values()) {
    rpc.updateFencingToken(newEpoch);        // 无 per-node try/catch
}
```
globalRecovery 锁内调用它且不捕获（L1487），异常直接穿透到外层 finally（L1549 只清 recoveryPending）后向上抛出。detectFailures 的两个检测循环都遍历 `taskAssignmentMap`：
```java
// L1214 / L1234
for (List<TaskAssignment> assignments : taskAssignmentMap.values()) {   // 已被清空
```

**严重程度**：P1（条件性永久悬挂；触发后无任何自愈路径，接近 P0）

**现状**：故障检测触发 recovery → 锁内顺序为"轮换 epoch → DB 注册 → 清空 taskAssignmentMap/allTaskLocations → 向全部 TM 推送新 epoch（阻塞 RPC，无隔离）→ abortAllPendingCheckpoints → prepareAssignmentsLocked"。若推送循环中任一 `updateFencingToken` 抛出（目标 TM 不可达/传输实现同步抛错），或 `activateAsLeader` 路径上 `restoreFromCheckpoint()` 存储重建失败（fail-loud，L1640）、`clusterRegistry.registerCoordinator` DB 写失败，recovery 在"工作集已清空、新 assignment 尚未物化"的状态下中止。

**交错时序推演**：
- 线程 A（failureDetector / RPC FAILED-report 线程）：进入 globalRecovery，拿到 recoveryLock，将 fencingEpoch 轮换为 E2，`taskAssignmentMap.clear()`。
- 线程 A 继续：`rpc.updateFencingToken(E2)` 对 TM-2 抛出（TM-2 正是本次 recovery 要摘除的故障节点，或网络抖动）。异常穿透，锁释放，recoveryPending 被外层 finally 复位。此刻：epoch=E2、taskAssignmentMap=空、无任何 redeploy 发生、旧任务在可达 TM 上已被先前成功的 updateFencingToken 取消（E1 任务被 removeIf 清除）。
- 线程 A'（下一轮 detectFailures，5s 后）：`running=true`、`active=true` 均通过，但两个检测循环遍历空的 taskAssignmentMap → `nodeFailureDetected=false`、`taskStallDetected=false` → 永不触发 requestRecovery。
- 线程 B（TM 上未取消的 E1 任务）的 FAILED 报告被 `epoch != report.getFencingEpoch()`（L914）拒绝。周期 checkpoint 触发的 barrier 被 TM 以 E1≠E2 拒绝。
- 坏结果：作业停留在"active 但零 assignment"状态，无故障可检、无 recovery 可触发、无 checkpoint 可完成——除外部重启/重新 activateAsLeader 外不可恢复，即作业级永久悬挂。

**风险**：每次 globalRecovery / leadership grant 都走这条路径；在节点故障驱动的 recovery 中，向刚故障的 TM 发同步控制消息失败是现实事件（fire-and-forget 传输实现会弱化此概率，见误报排除）；HA takeover 时存储重建失败同样命中。

**建议**：(1) 对 `updateFencingToken` 推送循环做 per-node try/catch（与 `executeAssignmentFanOut` 的 per-dispatch containment 对齐），失败节点交由下一轮检测/redeploy 收敛；(2) 将"清空工作集"移到 assignment 物化成功之后（或失败时回滚/重物化），保证 detectFailures 的观测集在恢复中断后仍非空；(3) 兜底：detectFailures 增加"active 但 taskAssignmentMap 为空超过 N 个 tick"的哨兵告警/重触发。

**信心水平**：代码路径 100%（清空在前、无隔离、检测循环以空 map 为观测集均逐行确认）；触发概率取决于控制面传输是否同步抛错。

**误报排除**：`seedAttemptCountersFromRegistryLocked` 的失败路径已显式 `active=false` 自降级（L1828-1843），不在此列；`executeAssignmentFanOut` 的 fan-out 失败有 per-dispatch 捕获且 assignment 已物化、liveness 检测仍可工作（但见 R5-CC-08）；in-process 直连模式下 `taskRpcServices` 为本地引用、不会抛传输异常——该模式下仅 DB/存储失败触发本条。

---

### [R5-CC-02] 物化回放"先存缝隙"竞态：drain 与 store 快照之间生产者写入导致物化边双投递（R4-N1 follow-up 评估）

**文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java` L816-L831（buildConsumerInvokableWithReplay）；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputChannel.java` L217-L240（activateMaterializationReplay）；`.../materialization/InMemoryMaterializationPoint.java` L75-L88（replay）；`.../execution/ResultPartition.java` L514-L536（drainBufferedElements）、L239-L278（write 双写）

**证据片段**（先 drain 队列，再对 store 做快照并 attach——两步之间无同步）：
```java
// SupervisionLoop L825-827
List<StreamElement> drained = oldPartition.drainBufferedElements();
InputChannel tempChannel = new InputChannel(consumerPartition);
int injected = tempChannel.activateMaterializationReplay(consistentCutEpoch);
```
```java
// InputChannel L224, L238
List<MaterializedElement> materialized = point.replay(fromEpoch);  // store 快照
...
partition.attachPendingReplay(elements);
```
```java
// ResultPartition L276-277（生产者双写：store + queue）
dualWriteToMaterialization(point, element);
enqueueWithBackpressure(point, element);
```

**严重程度**：P1（exactly-once 边上的记录双投递——正确性破坏；发生在每次消费侧 region 重启）

**现状**：R4-N1 将回放改为 `attachPendingReplay` 惰性回放（O(1)、不阻塞灌队列）——该修复本身确认有效（attach 在首次 read 之前 happens-before，`pendingReplay` 为 volatile，读路径先 replay 后 queue）。但"先存缝隙"仍在：回放集合取自 store 快照（`point.replay(fromEpoch)` 在时刻 T3），而队列残余在更早的时刻 T1 已被 drain。生产者在 (T1, T3) 区间内的每次写入都会同时落入 store（epoch ≥ N，进回放集合）和 queue（存活为活数据）。

**交错时序推演**：
- 线程 S（supervision，重启消费 region）：T1 执行 `oldPartition.drainBufferedElements()`，队列清空；T3 执行 `activateMaterializationReplay(N)`，从 store 快照出 `{R1, R2, R3}` 并 attach。
- 线程 P（另一 region 的存活生产者，未被取消，持续写）：T2（T1 < T2 < T3）写记录 R2：`point.write(R2, N)` → store 追加 R2；`queue.offer(R2)` 成功 → 队列也持有 R2。
- 消费者启动后：`read()` 先交付 pendingReplay 中的 R2，再从 queue 读到同一个 R2。
- 坏结果：物化边（类 javadoc 明确承诺 "no data is double-delivered on materialization edges"，SupervisionLoop L121-L126）上 R2 被交付两次——下游窗口/聚合/2PC 账本按记录级 exactly-once 的假设被破坏。数据流量越高、drain→快照间隙内写入越多，双投递概率越大（窗口虽小，但与生产速率成正比）。

**风险**：消费侧 region 重启是 materialization 特性的核心场景；双投递对有状态算子（count/sum/window）是静默的数据错误，仅在下游有记录级去重时才可吸收。

**建议**：消除 T1–T3 缝隙，可任选其一：(1) 先做 store 快照并 attach，再 drain 队列，同时让回放元素携带 (epoch, seq) 且消费端对 "queue 中 epoch ≥ N 的记录" 去重；(2) 在 attach 前后二次 drain（attach 完成后再 drain 一次，把 (T1,T3) 落入队列的记录清掉——它们已在回放集合中）；(3) 由 partition 提供原子原语 `swapToReplay(N)`，在 store 同一把锁内完成"快照 + 清队列"。方案 (2) 改动最小且与现有结构兼容。

**信心水平**：高。交错仅需生产者在两条相邻语句之间写一条记录；InMemoryMaterializationPoint 的 write/replay 虽各自 synchronized，但 drain 队列不在该锁内，无法闭合缝隙。

**误报排除**：若生产者恰在 T2 时队列满且 attach 前 offer 失败（overflow-bypass 只写 store），该记录仅经回放投递一次——此子情形正确；本条针对 offer 成功的主流子情形。R4-N1 修复的"put 阻塞灌队列"问题不在本条范围内，且确认未回归。

---

### [R5-CC-03] JobCoordinator.stop()/terminateCancel() 不向 TaskManager fan-out cancelTask——远程任务成为孤儿（资源泄漏 + 数据继续流动）

**文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java` L604-L636（stop）、L1918-L1931（terminateCancel）；对照 L2069-L2114（cancelTask 仅由 checkpoint abort handler 发出）

**证据片段**：
```java
// stop() 全文做的事：
running = false;
active = false;
if (electionListenerHandle != null) { ... electionListenerHandle.close(); }
failureDetector.shutdownNow();
stopPeriodicCheckpoints();
checkpointCoordinator.shutdown();
io.nop.stream.runtime.metrics.EngineMetrics.releaseJob(jobId);
// 没有任何 cancelTask / receiveAssignment 撤回逻辑
```
```java
private void terminateCancel() {
    ...
    this.jobStatus = JobStatus.CANCELED;
    jobEventBus.fire(...JOB_CANCELED...);
    stop();     // 直接 stop，无 cancelTask fan-out
}
```
全仓 grep 确认 `rpc.cancelTask(...)` 的唯一发送点是 `registerDistributedAbortHandler`（checkpoint snapshot-failure abort）。

**严重程度**：P1（常规操作路径上的持续资源泄漏：cancel 一次即泄漏该作业全部远程任务）

**现状**：CANCEL 模式终止（以及 OpsJobManager.close 里的 `coordinator.stop()`）只拆除协调端：注册表注销、检测器停、checkpoint 关。TaskManager 侧的 RunningTask 无任何信号：任务线程继续运行，source 持续拉外部数据、持续向下游 topic 产出，槽位 permit 永久占用，InputChannel 订阅保持活跃，心跳继续每 5s 打向已停止的 coordinator（被 `!running` 拒绝并 WARN 刷屏）。

**交错时序推演**：
- 线程 A（运维/REST）：对运行中的分布式作业调用 `terminate(CANCEL)` → `terminateCancel()` → `stop()` 返回，作业标记 CANCELED。
- 线程 B（TM-1 的 source 任务线程）：无感知，继续 `invokeSource()` 循环拉数、emit 到 RemoteResultPartition。下游 MIDDLE/SINK 同样存活，整个数据面继续运转。
- 坏结果：作业"已取消"但 N 个 JVM 上的任务永不退出：capacity 信号量被永久占用（该节点后续作业容量减少）、消息后端 topic 持续写入（外部副作用持续）、心跳对死 coordinator 每 5s WARN。直到该 TM 重启或同一 jobId 的新 coordinator 启动并轮换 epoch（updateFencingToken 取消旧任务）才回收。

**风险**：短生命周期作业反复 submit/cancel 的部署形态下，孤儿任务累积直至 TM 容量耗尽；被取消作业的 sink 持续对外提交，违反用户对 cancel 语义的预期。

**建议**：`stop()`（或至少 `terminateCancel` 的 CANCEL 分支）在关闭协调端之前，按当前 `taskAssignmentMap` 对每个 node fan-out `cancelTask(jobId, vertex, idx, fencingEpoch)`（per-node try/catch，失败靠日志 + 运维）；若担心 stop 语义过宽，可仅在 terminate(CANCEL) 路径做，DRAIN/SUSPEND 已有各自终态协议。

**信心水平**：高。`cancelTask` 发送点全仓唯一（grep 验证），stop 路径无任何任务回收调用逐行确认。

**误报排除**：LOCAL/embedded 模式（EmbeddedDistributedExecutor）由 env.execute 的同步生命周期管理，不在本条范围；bounded-run 自然结束后任务自行退出，也不受影响；本条不涉及 DRAIN/SUSPEND（它们依赖最终 checkpoint + 自然 EOS）。

---

### [R5-CC-04] subtaskLiveness 不随 recovery 清理：上一代 attempt 的陈旧时间戳污染新 assignment 的 stall 判定

**文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java` L1605-L1608（清理缺失处）、L1234-L1248（stall 判定）、L219（map 声明）

**证据片段**：
```java
// rotateFencingEpochCoreLocked 只清两个工作集：
taskAssignmentMap.clear();
allTaskLocations.clear();
// subtaskLiveness 无对应清理；prepareAssignmentsLocked 之后
// 新 assignment 的 key（vertexId/subtaskIndex）与旧条目完全同名。
```
```java
// detectFailures L1236-1242
String livenessKey = assignment.getVertexId() + "/" + assignment.getSubtaskIndex();
Long lastProgress = subtaskLiveness.get(livenessKey);
// 只有 lastProgress == null 才享受 benefit-of-the-doubt
if (lastProgress != null && lastProgress < cutoff) { taskStallDetected = true; }
```

**严重程度**：P2

**现状**：recovery 清空并重建 taskAssignmentMap，但 liveness map 保留上一代 attempt 的时间戳。recovery 后、新任务首次心跳到达前的窗口内，detectFailures 用"新 assignment × 旧时间戳"做 stall 判定。默认参数（lease 15s + taskTimeout 60s + cooldown 30s）下旧条目年龄约 20–30s < 60s，通常无害；但当 `leaseTimeoutMs` 调大到约 50s 以上（`TaskManager` 构造器允许），或 `taskTimeoutMs` 调小时，旧条目在 recovery 后首个 tick 即越过 cutoff。

**交错时序推演**：
- 线程 A（heartbeat 线程，故障节点）：最后一次心跳在 T0。此后该节点各任务的 liveness 条目冻结在 T0。
- 线程 B（failureDetector）：T0+70s（lease 60s + 检测周期）判定 nodeFailure → recovery #1（NODE_FAILURE 预算，注意不写 lastStallRecoveryAt）。新任务部署到其它节点。
- 线程 B'（failureDetector，T0+75s 第一个 tick）：新任务心跳未到（heartbeat 周期 5s + invokable 安装耗时），读到冻结条目 age=75s > 60s → 判 TASK_STALL → requestRecovery(TASK_STALL)：cooldown 检查读 lastStallRecoveryAt=0（NODE_FAILURE 恢复不设置它）→ 通过 → 消耗 1/3 stall 预算做一次完全不必要的 recovery。
- 坏结果：每次满足参数条件的节点故障都会额外烧掉一次 stall 预算（默认上限 3），极端时把作业推向 "stall cap exceeded → failJob"。

**风险**：参数耦合隐蔽（lease 与 taskTimeout 的相对大小决定是否发作）；stall 预算被虚假消耗后，真正的任务挂起反而可能无预算可用。

**建议**：在 `rotateFencingEpochCoreLocked` 中随工作集一并 `subtaskLiveness.clear()`（recovery 后所有 assignment 都是新代，无保留价值）；或 TaskProgress 增加 attemptNumber 过滤——心跳已携带 `attemptNumber` 字段但 merge 时被丢弃（见 R5-CC-05），按 attempt 维度隔离即可同时解决两条。

**信心水平**：高（代码路径确定）；是否实际发作取决于 lease/taskTimeout 配置比例，属"参数条件触发"。

**误报排除**：stall 驱动的 recovery 场景中 cooldown（30s）通常能挡住首个虚假触发（新心跳 5–10s 内到达），故默认参数下不构成恢复风暴；本条不涉及 R4-N3（isFinished 过滤已确认在 L326 生效，与其无关——冻结条目来自死节点，不是完成任务的残留）。

---

### [R5-CC-05] reportNodeTaskLiveness 无 fencing epoch / attempt 过滤：跨代心跳共用同一 liveness key

**文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java` L984-L1008（reportNodeTaskLiveness）；`.../coordinator/TaskProgress.java` L29-L46（无 epoch 字段）；对照 `reportTaskStatus` L912-L919（有完整 fencing 校验）

**证据片段**：
```java
@Override
public void reportNodeTaskLiveness(String nodeId, List<TaskProgress> progress) {
    if (progress == null || progress.isEmpty()) return;
    if (!running) { ...; return; }
    if (!active) { ...; return; }          // 只挡 standby，不挡旧代
    for (TaskProgress p : progress) {
        String livenessKey = p.getVertexId() + "/" + p.getSubtaskIndex();
        subtaskLiveness.merge(livenessKey, p.getLastProgressTime(), Math::max);
        // 无 fencing epoch 校验；attemptNumber 携带但未参与
    }
}
```

**严重程度**：P2

**现状**：`reportTaskStatus` 用单 long 比较 fencer 旧代报告，而同一 heartbeat 通道捎带的 `reportNodeTaskLiveness` 完全无 fencing（TaskProgress 也没有承载 epoch 的字段）。recovery 后，若某 TM 的 `updateFencingToken` 推送失败/迟到（见 R5-CC-01 的无隔离推送），其上仍存活的 E1 任务继续以同一 `vertexId/subtaskIndex` key 刷新 liveness——与新代任务的心跳在 coordinator 侧不可区分地 merge（Math::max 恒取较新者）。

**交错时序推演**：
- 线程 A（TM-1 heartbeat，持有 E1 zombie 任务）：每 5s `reportNodeTaskLiveness(node-1, [v0/0 → now])`——zombie 的 `isFinished()` 为 false，不会被 R4-N3 过滤。
- 线程 B（coordinator）：merge 后 v0/0 的 liveness 永远新鲜。即使 v0/0 在新代已被重新指派且新任务真实挂死，detectFailures 因 zombie 的新鲜时间戳永远判不出 stall——新代挂死任务被旧代 zombie 的心跳"隐身"。
- 坏结果：stall 检测对该 subtask 失效，只能依赖节点 lease 兜底；若 zombie 同时占据 TM-1 槽位（capacity 泄漏），故障被长期掩盖。

**风险**：与 R5-CC-01 叠加时（推送失败正是本条前提），recovery 的核心安全网之一被静默旁路。

**建议**：TaskProgress 增加 `fencingEpoch` 字段，coordinator 端与 `reportTaskStatus` 同样做单 long 比较；短期可先用已携带的 `attemptNumber` 对照 ClusterRegistry 当前 attempt 拒绝旧代心跳。

**信心水平**：高（代码确定）；实际危害依赖"推送失败 + zombie 存活"的组合前提。

**误报排除**：正常 recovery 中 updateFencingToken 成功后旧代任务被 TM 侧 removeIf 取消，不再上报——本条只覆盖推送失败/部分失败的残留窗口；standby 拒绝路径已存在，与本条无关。

---

### [R5-CC-06] CheckpointCoordinator 单monitor内执行 participant.finishCommit / abortHandler（N×阻塞 RPC fan-out）——ACK/触发/超时全部停摆

**文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java` L556-L558 + L773-L834（onCompletePersistSuccess 在 synchronized 内被调用并触发 notifyParticipantsFinishCommit）、L1211-L1223（notifyParticipantsFinishCommit）、L897-L947（abortPendingCheckpoint 在 synchronized 内调用 abortHandler）；`JobCoordinator.java` L1124-L1166（forwarder.finishCommit 的 RPC fan-out）、L2089-L2112（abort handler 的 cancelTask fan-out）

**证据片段**：
```java
// executePersistAsync —— 存储IO在锁外，但副作用回到锁内：
synchronized (this) {
    onCompletePersistSuccess(completed, pending, true);   // 持 monitor
}
// onCompletePersistSuccess L820-824（仍在 monitor 内）：
retryFailedCommits();
notifyParticipantsFinishCommit(checkpointId, true);
```
```java
// JobCoordinator 分布式 forwarder（participant 实现，逐节点阻塞 RPC）：
for (String nodeId : assignmentPlanner.computeAssignedNodeIds()) {
    ...
    rpc.notifyCheckpointComplete(epochId, epoch);   // 同步 send，慢节点拖住全程
}
```

**严重程度**：P2

**现状**：design 注释明确"the side-effect steps re-acquire the monitor"（L529-L530），但副作用集合里包含 participant 通知与 abortHandler——分布式模式下二者都是 N 节点的同步 RPC fan-out。monitor 被占期间，`acknowledgeTask`（所有 TM 的 ACK RPC 线程）、`tryTriggerPendingCheckpoint`（周期触发线程）、`abortPendingCheckpoint`（超时线程）全部阻塞。

**交错时序推演**：
- 线程 A（persist 线程）：checkpoint N 持久化成功，持 monitor 进入 `notifyParticipantsFinishCommit` → 逐节点 `notifyCheckpointComplete`；节点 TM-3 网络半死，每次 send 需等待传输层超时（比如 10s×若干节点）。
- 线程 B…X（各 TM 的 ACK RPC 线程）：checkpoint N+1 的 ACK 到达，全部卡在 `synchronized acknowledgeTask` 上；CheckpointAckSender 的 3 次重试 + 600ms backoff 在 TM 侧耗尽后 ACK 丢失（有 checkpoint 超时兜底，但 N+1 被拖延整个超时周期）。
- 线程 Y（TM 心跳线程）：`reportNodeTaskLiveness` RPC 同样被 coordinator 侧处理阻塞；TaskManager.heartbeat() 的 renewLease 与 report 在同一线程序列执行（TaskManager L294-L337），report 阻塞会推迟下一轮 renewLease → 节点 lease（15s）可能过期 → 触发本可避免的 NODE_FAILURE recovery。
- 坏结果：一个慢节点把"确认提交"放大为全集群控制面停摆 + 潜在误 recovery 级联。

**风险**：故障场景（恰是 commit/abort 最频繁之时）放大；有 checkpoint timeout 与 lease timeout 两层兜底，故不到 P1，但兜底本身会转化为不必要的 abort/recovery。

**建议**：将 participant 通知与 abortHandler 移出 monitor（在释放锁后执行，或投递到独立 executor；`failedCommitParticipants` 的重试结构已天然支持异步化）；abortHandler 已携带 reason，可在锁外执行不受 CAS 语义影响（RUNNING→ABORTED 的 CAS 已在锁内完成）。

**信心水平**：高（调用链逐行确认：executePersistAsync → synchronized → onCompletePersistSuccess → notifyParticipantsFinishCommit → participant.finishCommit → RPC）。

**误报排除**：LOCAL 模式的 participant 是本地 sink UDF（内存/JDBC 本地提交），临界区短，实际影响集中在分布式 commit forwarder / cancelTask fan-out；`retentionCleaner` 已做锁外化（L809-L817），说明作者有意识地控制该临界区，本条指出的是漏掉的两个回调。

---

### [R5-CC-07] deployTask 传输层失败后任务无 liveness 记录——"benefit of the doubt"成为永久检测盲区

**文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java` L1237-L1242（无记录即豁免）；`.../coordinator/AssignmentPlanner.java` L220-L240（fan-out 仅 per-dispatch catch+log）；对照 TaskManager.deployTask L629-L647（TM 侧失败会上报 FAILED，但 RPC 未到达时无上报）

**证据片段**：
```java
// detectFailures：
Long lastProgress = subtaskLiveness.get(livenessKey);
// A task with no liveness record yet (just-assigned, before first
// heartbeat) gets the benefit of the doubt — node-lease detection
// will catch a true failure.
if (lastProgress != null && lastProgress < cutoff) { ... }
```
```java
// executeAssignmentFanOut：
} catch (Exception e) {
    LOG.error("Failed to dispatch assignment for {} (epoch {})", ...);  // 仅日志
}
```

**严重程度**：P2

**现状**：deployTask RPC 因传输错误未到达 TM 时：TM 不会运行任务、不会上报 FAILED（rejectDeploy 依赖消息到达）、也不会有心跳 → 该 subtask 在 coordinator 侧永远"无 liveness 记录"。节点本身健康（lease 持续续约），节点级检测也不触发。

**交错时序推演**：
- 线程 A（coordinator fan-out）：向 TM-2 的 deployTask 因消息后端瞬时故障 send 抛异常 → catch+log，fan-out 继续。
- 线程 B（TM-2 心跳）：节点正常续约 lease；无该任务的心跳。
- 线程 C（failureDetector）：assignment 存在（prepareAssignmentsLocked 已物化+写注册表），liveness 无记录 → 永久豁免；node lease 正常 → 无 recovery。
- 坏结果：该 vertex 缺一个 subtask。下游 gate 该 channel 永远空转（非物化边上游会在 queue 满后阻塞 → 上游 lastActivityTime 停走 → 60s 后 stall 检测间接救场；**物化边**因 overflow-bypass 上游永不阻塞、activity 保持新鲜 → 整条流水线对该 channel 静默半死，无任何恢复触发）。

**风险**：物化拓扑 + 瞬时 send 失败的组合 = 无告警、无恢复的静默降额运行。

**建议**：为 assignment 引入"部署宽限期"：`prepareAssignmentsLocked` 记录 assignment 时间，detectFailures 对"超过 grace（如 2×heartbeat+invokable 等待）仍无 liveness 记录"的 assignment 判定部署失败并重触发 recovery；或在 fan-out catch 中主动 `requestRecovery()`。

**信心水平**：高（三段代码拼图完整；"节点健康 + 部署丢失"组合是 send 异常的直接后果）。

**误报排除**：TM 侧可检测的失败（容量、fencing、build 错误）都会走 rejectDeploy → FAILED report → recovery（TaskManager L494-L647 确认），不在本条范围；本条仅覆盖"消息未到达"这一传输层子情形。

---

### [R5-CC-08] SubtaskTask cancel/finish/fail 三态并发交错：取消瞬间完成/失败的状态覆盖竞态

**文件**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/SubtaskTask.java` L121-L141（run 的终态判定）、L148-L174（cancel 的 CAS 循环）

**证据片段**：
```java
// run()：
if (state.get() == State.RUNNING) {          // L125 读
    state.set(State.COMPLETED);              // L126 无条件写（非 CAS）
} else if (state.get() == State.CANCELING) {
    state.set(State.CANCELED);
}
...
} catch (Throwable t) {
    this.error = t;
    State s = state.get();                   // L134 读
    if (s == State.CANCELING) { state.set(State.CANCELED); }
    else { state.set(State.FAILED); }        // 非 CAS 写
```

**严重程度**：P3

**现状**：终态写入是"读-判-写"三步而非 CAS，与 `cancel()` 的 `CAS(RUNNING→CANCELING) + interrupt` 存在两个交错窗口：(a) catch 线程读 s==RUNNING 之后、set(FAILED) 之前，cancel 的 CAS 成功——任务以 FAILED 收场，但取消方已认为 CANCELING 生效；(b) run 线程 L125 读 RUNNING 为真后，cancel CAS 成功，随后 L126 无条件 set(COMPLETED) 覆盖 CANCELING。

**交错时序推演（窗口 a）**：
- 线程 T（任务线程）：算子抛出真实异常，异常向上传播中。
- 线程 C（supervision/abort）：`cancel()` 读到 RUNNING → CAS 至 CANCELING → interrupt（T 几乎不受影响，异常已在传播）。
- 线程 T：catch 读 s——若此刻仍读到 RUNNING（cancel 的 CAS 尚未执行）→ set(FAILED)；若已读到 CANCELING → CANCELED。
- 坏结果：被主动取消的任务终态为 FAILED。SupervisionLoop 的 `findFirstFailed` 会将其视为真实失败并触发一次 region restart（重启一个本要放弃的 region）；abort 路径下则多烧一次 restart 预算。窗口 (b) 方向相反：终态 COMPLETED 覆盖 CANCELING，取消方误认为取消成功——因两者皆 terminal，下游影响有限。

**风险**：窗口为微秒级，触发罕见；后果是一次多余 region restart（有预算上限，最坏多烧 1/3 预算）。

**建议**：终态写入改为 CAS（`compareAndSet(RUNNING, COMPLETED/FAILED)`，失败即读方让位于 CANCELING 分支），消除读-判-写窗口；语义上"取消优先于失败"与 cancel() 的 CAS-first 设计一致。

**信心水平**：中高（竞态构造成立；触发概率低）。

**误报排除**：RunningTask（TM 侧）的同类终态由 `canceled` volatile 单独判定（`success = error == null && !canceled`），不受本条影响；本条仅限 LOCAL 模式 SubtaskTask。

---

### [R5-CC-09] InputGate.abortBarrierAlignment 的 add-first 仍留 check-then-act 缝：被 abort epoch 的 barrier 可复活对齐

**文件**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java` L1150-L1160（abortBarrierAlignment）、L966-L1004（handleBarrierNonRecursive 的 contains→get→put 序列）

**证据片段**：
```java
// 任务线程（handleBarrierNonRecursive）：
if (abortedBarriers.contains(id)) { return Optional.empty(); }   // (1) 检查
...
BarrierAlignment align = inFlightAlignments.get(id);
if (align == null) {
    align = new BarrierAlignment(...);
    inFlightAlignments.put(id, align);                            // (2) 复活点
}
// abort 线程（abortBarrierAlignment）：
abortedBarriers.add(checkpointId);                                // (3) add-first
inFlightAlignments.remove(checkpointId);
```

**严重程度**：P3

**现状**：javadoc 声称 add-first 封闭了复活窗口（L1139-L1148），但 (1) 与 (2) 是两个独立并发集合上的非原子序列：任务线程的 contains 检查可以先于 abort 的 add 执行，而 put 晚于 remove 执行。

**交错时序推演**：
- 线程 T（任务线程）：执行 (1)，abortedBarriers 不含 id（abort 未开始）→ 通过。
- 线程 A（abort 线程，checkpoint 超时）：执行 (3)(4)：add(id)、remove(id)（此时对齐尚不存在，remove 返回 null）。
- 线程 T：执行 (2)——为已 abort 的 epoch 放入全新对齐；若 STRICT_EXACTLY_ONCE，还会 block 该 channel。
- 坏结果：幽灵对齐永不完成，30s 后 `checkAlignmentElapsed` 抛 `ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT` → 任务 FAILED → 一次不必要的 region restart / recovery。其余 epoch 不受影响。

**风险**：abort 与该 epoch barrier 首达的精确并发窗口，罕见；有对齐超时兜底，不会永久悬挂。

**建议**：将 abortedBarriers 的检查移入 inFlightAlignments 的 put 路径（如 `inFlightAlignments.compute(id, ...)` 内部二次校验 abortedBarriers），或 handleBarrier 在 put 前重读 abortedBarriers。

**信心水平**：高（交错构造成立且注释中声称的封闭性确实不覆盖此序）；发生频率低。

**误报排除**：若 barrier 在 add 之前已被 (1) 检查并放行但尚未 (2)，本条即命中；若 (1) 晚于 (3)，contains 直接过滤——注释描述的正是后一序，未覆盖前一序。

---

### [R5-CC-10] TaskManager.commitExecutor 单线程无界队列：慢提交无限积压且通知顺序放大延迟

**文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java` L174-L181（创建）、L790（execute 提交）

**证据片段**：
```java
// Single thread keeps commit ordering per task ...
this.commitExecutor = Executors.newSingleThreadExecutor(
        NopStreamThreadFactory.named("tm-commit-" + nodeId));   // 无界队列，无拒绝策略
...
commitExecutor.execute(() -> task.notifyCheckpointComplete(checkpointId));  // 每任务每 epoch 一条
```

**严重程度**：P3

**现状**：2PC finishCommit（JDBC/文件阻塞 IO）在单线程 executor 上串行。一个卡死的提交（无超时的 JDBC 连接）使队列无界增长：同节点全部任务的后续 epoch 通知积压，提交延迟随积压线性放大；`stop()` 的 shutdownNow 是唯一泄压口。

**交错时序推演**：
- 线程 A（commit 线程）：task-1 的 finishCommit 阻塞在无超时 JDBC。
- 线程 B（RPC dispatch）：每个 checkpoint 完成通知为节点上每个任务入队一条 → 队列按 (任务数 × epoch 数) 增长。
- 坏结果：下游 sink 的提交被无限推迟，最终由 coordinator 侧 checkpoint/重试机制兜底（subsuming 语义限制了正确性损害），但延迟与内存占用无上界。

**风险**：正确性有 2PC subsuming + retryFailedCommits 兜底，主要是延迟/内存退化，故 P3。

**建议**：给 commit 执行加超时（语句/连接级），或换有界队列 + CallerRuns/丢弃-由-subsuming-兜底策略并在丢弃时打点。

**信心水平**：高（结构性事实）；触发依赖慢提交存在。

**误报排除**：注释已论证"单线程保序"是有意为之，本条不质疑保序，只指出无界积压。

---

### [R5-CC-11] terminateWithTerminalSavepoint 捕获 Exception 吞掉 InterruptedException 且不恢复中断位

**文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java` L1981-L1993

**证据片段**：
```java
pending.getCompletableFuture()
        .get(terminationCheckpointTimeoutMs, TimeUnit.MILLISECONDS);
...
} catch (Exception e) {
    LOG.error("{}: failed to complete {} for job {}", mode, snapshotNoun, jobId, e);
}
```

**严重程度**：P3

**现状**：`future.get(...)` 抛出的 InterruptedException 被泛化 catch 记日志后丢弃，线程中断位未恢复（对比 `stopPeriodicCheckpoints` L1080-L1082 的正确写法：`Thread.currentThread().interrupt()`）。DRAIN/SUSPEND 最长阻塞 60s（terminationCheckpointTimeoutMs），期间调用方（如 REST 线程、OpsJobManager.close）的取消请求被静默吞掉，且后续仍会执行 health.onFinished + stop()。

**交错时序推演**：线程 A 执行 terminate(DRAIN) 阻塞在 get()；线程 B（shutdown 钩子）中断 A；A 的中断被吞，继续以"checkpoint 失败"路径收尾并 stop()。坏结果：关闭流程的取消语义失效，A 比预期晚至多 60s 退出，中断位丢失可能向上游传播更隐蔽的问题。

**建议**：catch 分支对 `InterruptedException` 单独处理：恢复中断位后按中断语义收尾（或直接 `catch (InterruptedException e) { Thread.currentThread().interrupt(); ... }`）。

**信心水平**：高（代码确定）；影响面为终止路径的体验/时序。

**误报排除**：CheckpointAckSender（L80-L85）与 stopPeriodicCheckpoints 的中断处理均正确，本条为孤立缺陷。

---

### [R5-CC-12] SupervisionLoop.rewireCheckpointPipeline 对 barrier 注入列表的 remove+add 非原子：替换瞬间可漏发一个 epoch 的 barrier

**文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java` L925-L928；`.../execution/TaskCheckpointWiring.java` L274-L284（调度 tick）、L289-L312（triggerBarrierOnAllInvokables 遍历 COW list）

**证据片段**：
```java
if (allInvokables != null) {
    allInvokables.remove(oldInvokable);   // 时刻 A：新旧都不在列表
    allInvokables.add(newInvokable);      // 时刻 B：新任务才恢复接收
}
```

**严重程度**：P3

**现状**：CopyOnWriteArrayList 解决了迭代 CME（注释准确），但 remove 与 add 之间调度 tick 若恰好触发，region 内被重建的任务收不到该 epoch 的 barrier → 其 in-flight epoch 不存在 → 任务侧 barrier 处理正常但该 epoch 的 ACK 永不产生 → 该 checkpoint 走超时 abort（maxConcurrent=1 时还会压住下一个 epoch）。

**交错时序推演**：线程 A（barrier-injector tick）：`tryTriggerPendingCheckpoint` 成功、开始遍历 allInvokables，恰在读取快照后、注入执行前；线程 B（supervision）：remove(old) 执行。tick 对该任务的引用仍是 oldInvokable（COW 迭代快照语义）→ barrier 注入到已取消的旧 invokable，新 invokable 错过本 epoch。坏结果：单次 checkpoint 超时 abort（下一次周期触发在新集合上成功）。

**风险**：仅损失一个 checkpoint epoch，自愈；但在 checkpoint 频率低 + 重启频繁的作业中可观察为周期性 checkpoint 缺失。

**建议**：用 `list.replaceAll`/索引 set 原子替换，或 COW list 的 `set(index, new)`（先 indexOf），消除双元素窗口。

**信心水平**：高（COW 快照迭代语义确定）；后果有界。

**误报排除**：`triggerCheckpoint` 侧的 in-flight 注册在 tracker（线程 A 持 tracker monitor），与本窗口无关；已有注释正确排除了 CME 误报，本条是另一维度（丢失注入）。

---

### [R5-CC-13] RemoteInputChannel.close() 满队时 EOS sentinel 入队失败仅置错误标志——阻塞在无界 read() 的读者不会被唤醒

**文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java` L497-L535（close/enqueueTerminalSentinel/flagOverflow）、L274-L291（无界 read 的 queue.take）

**证据片段**：
```java
private void enqueueTerminalSentinel() {
    if (queue.offer(END_OF_STREAM)) {
        return;
    }
    flagOverflow("could not enqueue the EOS sentinel (queue full) - "
            + "reader unblocked via typed failure");   // 实际上 take() 中的读者无人唤醒
}
```

**严重程度**：P3

**现状**：队列满时 close/失败路径只置 `overflowError` 并尝试 offer；若 offer 失败，没有任何元素入队，阻塞在 `read()`（`queue.take()`）的读者既拿不到 sentinel 也无人中断它的等待——javadoc 所述"reader unblocked via typed failure"只对调用有界 `read(50ms)` 轮询的读者成立（InputGate 生产路径轮询空转后 `checkChannelError` 抛出 typed error）。

**交错时序推演**：线程 A（dispatch，onMessage 溢出分支）：队列满 → overflowError 置位 → enqueueTerminalSentinel 失败（队列仍满）。线程 B（某个使用无界 read() 的调用方，如诊断/测试代码）：阻塞在 take()，既无元素可取也未被中断 → 永久阻塞。生产 InputGate 路径（50ms 有界 poll）不受影响。

**建议**：enqueueTerminalSentinel 失败时降级为 `queue.put`（close 语义下 sentinel 必须落到队首之后是可接受的）或对阻塞读者做 interrupt；至少修正注释。

**信心水平**：高（机制确定）；生产主路径不触发。

**误报排除**：`read(timeout)` 路径由 poll 超时 + `checkChannelError` 兜底，本条仅针对无界 `read()` 的潜在调用方。

---

### [R5-CC-14] RemoteResultPartition.close() 双并发 close 的 TOCTOU：重复 EOS 控制消息

**文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteResultPartition.java` L149-L178

**证据片段**：
```java
public void close() {
    if (isFinished()) {       // 读 volatile
        return;
    }
    markFinished();           // 写 volatile —— 两线程可同时通过上面的读
    ...
    synchronized (sendLock) { messageService.send(topic, eos); }  // 各发一条 EOS
}
```

**严重程度**：P3

**现状**：finished 的 check 与 markFinished 非原子，两个线程并发 close 会各发送一条 CONTROL_END_OF_STREAM。下游 EnvelopeConsumer 对每条 EOS 都执行 `finished=true; enqueueTerminalSentinel()`，本地队列可能被放入两个 sentinel。

**交错时序推演**：线程 A、B 同时进入 close：都读到 finished=false → 都 markFinished → 都在 sendLock 内各发一条 EOS。坏结果：下游队列多一个 sentinel、日志多一次 EOS——读路径对 sentinel 统一返回 null（EOS 语义幂等），无正确性影响。

**风险**：无数据损害；纯冗余消息。当前唯一调用方（invoke finally 的 closeOutputWriters）是单线程，实际不可达，记录为防御性缺口。

**建议**：check-and-mark 用 AtomicBoolean CAS（或 sendLock 内双检），与 write 的 in-lock finished 复查对偶。

**信心水平**：高；实际触发概率≈0（单线程调用方）。

**误报排除**：write-vs-close 的次序已由 in-lock 复查（L139-L147）正确保证，本条不涉及。

---

### [R5-CC-15] JdbcLeaderElector.tryBecomeLeader 硬编码 epoch=1：租约行丢失后 fencing 单调性断裂

**文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/JdbcLeaderElector.java` L225-L248

**证据片段**：
```java
private void tryBecomeLeader() {
    ...
    long epoch = 1L;    // 无历史感知的固定初值
    SQL sql = ... "INSERT INTO " + leaseTable + " (..., leader_epoch, ...) VALUES (?,?,...,?)",
            getClusterId(), getHostId(), getLeaderAddr(), epoch, ...
```

**严重程度**：P3

**现状**：仅当租约行不存在（首次部署或行被运维/重建脚本删除）时走到此分支。若历史上 epoch 已推进到 N，回卷到 1 派生的 fencing epoch（`leaderEpoch*EPOCH_SCALE + gen`）可能小于 TM 现存 `currentFencingEpoch` → `TaskManager.updateFencingToken` 按回滚拒绝抛异常 → 与 R5-CC-01 叠加（推送循环无隔离 → activateAsLeader 半途中止且 assignment 未物化）。

**交错时序推演**：DBA 清空 nop_stream_leader 表 → 节点 A tryBecomeLeader(epoch=1) 成功 → activateAsLeader 派生 token=1×SCALE+0；各 TM 的 currentFencingEpoch 停留在旧代的更大值 → updateFencingToken(1×SCALE) 抛 FENCING_TOKEN_MISMATCH → 无隔离 → 激活半途失败 → 作业 wedge（R5-CC-01 同款且更难恢复，因所有 TM 都拒绝）。

**建议**：tryBecomeLeader 前读历史 max(leader_epoch)（表为空时无从读起——可在 insert 冲突/失败后走 changeLeader 递增路径），或 epoch 初值取 `System.currentTimeMillis()/1000` 之类的天然单调源。

**信心水平**：中（代码确定；触发需外部删行，属运维事故场景）。

**误报排除**：正常 takeover（changeLeader）按 row.leaderEpoch+1 乐观递增，单调性正确；restartElection 亦递增，均不受本条影响。

---

### [R5-CC-16] activateAsLeader 在 leaderEpoch=0 的平台 elector 下派生"未初始化哨兵"epoch=0，ACK/报告全拒

**文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java` L1733（token 派生）、L850-L857 与 L913-L914（epoch==0 哨兵拒绝）；对照 L497-L519（非 HA 路径刻意把 recoveryGen 种到 1 避开哨兵）

**证据片段**：
```java
// activateAsLeader：
this.recoveryGen.set(0);
...
long token = deriveHaFencingEpoch(epoch.getEpoch(), 0);   // leaderEpoch==0 ⇒ token==0
// collectAck：
long epoch = fencingEpoch.get();
if (epoch == 0L) {
    LOG.warn("Rejecting checkpoint ACK: coordinator fencing epoch not initialized");
    return false;
}
```

**严重程度**：P3

**现状**：非 HA 路径专门注释了"epoch 必须≠0"并把 recoveryGen 种为 1（L500-L519），但 HA 激活路径 `deriveHaFencingEpoch(leaderEpochValue, 0)` 在平台 elector 的 LeaderEpoch 从 0 开始时会派生出 0——落入自己声明的"未初始化哨兵"，collectAck / reportTaskStatus / abort 全部永久拒绝，直到第一次 globalRecovery 才恢复。

**交错时序推演**：平台 elector（非 JdbcLeaderElector，其 epoch 从 1 起）发放 epoch=0 → activateAsLeader 设 fencingEpoch=0 → TM 侧 updateFencingToken(0)：`0 < current`? current=0 时相等通过（getAndSet 无变化、旧任务不取消）；随后所有 ACK/report 被 coordinator 的哨兵检查拒绝 → checkpoint 永不完成、失败报告被忽略。坏结果：HA 模式下作业表面 active 实际控制面瘫痪。

**建议**：activateAsLeader 与非 HA 路径同样将 recoveryGen 种为 1（`deriveHaFencingEpoch(epoch.getEpoch(), 1)`），或在派生处对 0 结果断言失败。

**信心水平**：代码路径确定；**触发前提**（平台 elector 的 epoch 允许为 0）未在仓库内证实——JdbcLeaderElector 从 1 起，SysDaoLeaderElector 未能审计（依赖方向限制）。低-中信心，列为防御性缺陷。

**误报排除**：JdbcLeaderElector 路径（nop-stream 自带 HA）epoch≥1，不触发；本条不适用于非 HA 模式（已有 1 的种子）。

---

### [R5-CC-17] TaskManager.taskExecutor 为容量固定的无界队列线程池：终端状态 RPC 在任务线程 finally 中同步发送，协调端卡顿时连锁耗尽执行线程

**文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java` L170-L171（newFixedThreadPool 无界队列）、L437-L438（submit）、`RunningTask.java` L179-L182 + L213-L215（finally 中同步 reportTaskStatus）

**证据片段**：
```java
this.taskExecutor = Executors.newFixedThreadPool(Math.max(1, capacity), ...);  // 无界 LinkedBlockingQueue
...
// RunningTask.run() finally：
if (!canceled) {
    reportTerminalStatus(success);   // 任务线程上同步 RPC，无超时包装、无重试预算
}
```

**严重程度**：P3

**现状**：`reportTerminalStatus`/`CheckpointAckSender.send`（后者自带 3 次×(200/400ms) backoff）都在 taskExecutor 的容量个线程上执行。若 coordinator 端处理阻塞（如 R5-CC-06 的 monitor 长临界区）且传输层无发送超时，capacity 个线程可全部滞留在 finally 的 RPC 中；此后所有 submitTask 进入无界队列排队，新部署任务 30s 内等不到线程 → invokable 安装超时 → FAILED → 触发更多部署，形成放大回路。

**交错时序推演**：coordinator 持 monitor 阻塞 30s（R5-CC-06）→ 节点上 N 个任务同时自然结束，N 个线程都卡在 reportTaskStatus 的发送上（若该 send 同步等待响应）→ 期间 recovery 的 redeploy submitTask 全部排队 → 30s 后批量 invokable-wait 超时 → 二次 FAILED 报告 → 再 recovery。坏结果：单点卡顿被放大为节点级任务雪崩（有界于传输层超时的存在性）。

**建议**：将终端报告/ACK 发送移出 taskExecutor 线程（独立小发送 executor + 有界队列），或确认并固化控制面传输的发送超时契约（写入 IStreamTaskRpcService javadoc）。

**信心水平**：中（结构成立；实际危害取决于 message-service 传输是否提供发送超时——仓库内未见显式超时设置）。

**误报排除**：capacity 信号量与线程池是两个独立约束：permit 在 finally 早期释放（L171-L173），容量计数不受影响，本条是线程饥饿而非 permit 泄漏（R4 早期修复的 permit leak 已确认未回归）。

---

## R4 已修复项回归核对（plan 366）

| 项 | 结论 |
|---|---|
| R4-N1 attachPendingReplay 惰性回放 | 修复在位（ResultPartition L83-L118：volatile + CLQ，attach O(1) 不灌队列）；但其已知 follow-up"先存缝隙"确认仍开放 → 本报告 R5-CC-02 |
| R4-N2 region 内部边复用旧分区 + MIDDLE writer 保留 | 修复在位（SupervisionLoop L803-L838 注释"Always reuse the old partition"；L847-L855 fan-out writer 保留）；未发现修复引入的新并发问题 |
| R4-N3 心跳过滤 isFinished 任务 | 修复在位（TaskManager.heartbeat L326 `if (task.isFinished()) continue;`）；liveness 冻结条目问题只剩死节点场景 → R5-CC-04/05 与其无关 |
| R4-N5 ResultPartition.injectFront 删除 | grep 全仓无 injectFront；EOS 哨兵 permit 语义不变（sentinel 不持 permit，drain 亦不释放）|
| 更早修复（recovery race / InputGate 对齐 / permit leak / zombie restart） | recovery race 由 recoveryLock+recoveryPending CAS 覆盖（globalRecovery 外层 finally 清 flag，含异常路径——注意 R5-CC-01 是其遗留盲区）；InputGate 对齐状态全部 concurrent 集合；permit 的 CAS 配对（semaphoreReleased）在 deploy/cancel/finish/replace 四路径对称 |

## 附：审计覆盖文件清单

- JobCoordinator.java（2356 行，全文）、AssignmentPlanner.java（全文）
- TaskManager.java（911 行，全文）、RunningTask.java（全文）、CheckpointAckSender.java（全文）
- SupervisionLoop.java（969 行，全文）
- InputGate.java（1290 行，全文）、InputChannel.java（全文）、ResultPartition.java（全文）、InMemoryMaterializationPoint.java（全文）
- StreamTaskInvokable.java（1193 行，全文）、SubtaskTask.java（全文）、TaskExecutor.java（全文）、TaskMailbox.java（全文）、MailboxExecutor.java（全文）、CheckpointBarrierTracker.java（关键段）
- GraphExecutionPlan.java（807 行，全文）
- RemoteInputChannel.java（全文）、RemoteResultPartition.java（全文）、TaskCheckpointWiring.java（全文）
- CheckpointCoordinator.java（关键段：trigger/ack/complete/persist/abort/timeout/shutdown/participant 通知）
- JdbcLeaderElector.java（全文）、JdbcClusterRegistry.java（全文）、InMemoryClusterRegistry.java（结构核对）
- OpsJobManager.java（close/governance 段）、TaskProgress.java（全文）
