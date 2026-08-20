# stream-runtime 实现代码检查报告

- 检查日期: 2026-08-20
- 模块路径: nop-stream/{nop-stream-runtime,nop-stream-rocksdb}
- 文件数: 实际 **82**（nop-stream-runtime 65 + nop-stream-rocksdb 17，均为 src/main/java；任务描述中的 232/30 与实际不符，以实际为准）
- 覆盖范围声明:
  - 深读全文: checkpoint/CheckpointCoordinator、PendingCheckpoint；coordinator/JobCoordinator；taskmanager/TaskManager；execution/GraphModelCheckpointExecutor、SupervisionLoop、RpcDistributedExecutor；checkpoint/storage/LocalFileCheckpointStorage、CheckpointSerDe；transport/RemoteInputChannel、RemoteResultPartition、DataPlaneMessageServiceAdapter；rpc/StreamControlRpcServer；cluster/InMemoryClusterRegistry；operators/windowing/MergingWindowSet；rocksdb/RocksDBKeyedStateBackend、RocksDBIncrementalSnapshotStrategy、RocksDBIncrementalRestore、RocksDBStateBackend、RocksDBMapState（关键段）；source/CollectionReplayableSource；_vfs 下两个 beans.xml。
  - 关键段深读（辅以全文 grep 定位）: JdbcCheckpointStorage、EmbeddedDistributedExecutor、JdbcLeaderElector、WindowOperator（processElement/onEventTime/onProcessingTime/清理段）、RocksDBSnapshotSerDe（反序列化与类名校验段）、stream-core 的 ClassNameValidator（反序列化安全依据）。
  - 其余文件（消息 DTO、metrics、reshard、wire codec 等）经模式扫描（空 catch、`new RuntimeException`、`printStackTrace`、synchronized/volatile、RocksDB iterator/close、网络 close）后未命中可疑点，未逐行深读。
  - 测试代码（src/test）、target/、`_` 前缀生成文件不在范围内。
  - 环境证据: 全仓库 grep（排除 target）用于确认调用方缺失类发现（如 `startCheckpointScheduler` 仅测试调用）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 6 |
| P2 | 5 |
| P3 | 4 |

## 发现列表

### [P1] TaskManager：waitForInvokable 超时后任务被报告为 COMPLETED（假成功）

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:713-717,731,761-786`
- **维度**: D1（任务生命周期状态机）
- **证据**:
```java
StreamTaskInvokable inv = waitForInvokable();      // 30s 超时返回 null
if (inv == null || canceled) {
    LOG.info("Task {}/{}/{} canceled while waiting for invokable", ...);
    return;                                        // 无异常、无 canceled 标记
}
...
} finally {
    boolean success = error == null && !canceled;  // 超时路径 => success == true
    completedTasks.put(key, new TaskResult(..., success, canceled, error));
    if (!canceled) {
        reportTerminalStatus(success);             // 向 coordinator 报告 COMPLETED
    }
}
```
- **现状**: `waitForInvokable()` 30 秒超时返回 null 后，`error` 为 null 且 `canceled` 为 false，`success=true`，任务以 **COMPLETED** 终态上报 coordinator。日志文案 "canceled while waiting" 与实际上报 COMPLETED 直接矛盾，说明该分支本意不是成功完成。
- **风险**: 两阶段部署模型（`receiveAssignment` 建 slot → coordinator 单独 `installInvokable`，代码注释明确支持该模型）下，若 invokable 安装延迟 >30s 或安装方构建失败未送达，该 subtask 从未运行却被标记成功：
  1. `JobCoordinator.reportTaskStatus` 收到 COMPLETED 后 `subtaskLiveness.remove(key)`，该任务被永久排除在 stall 检测之外（`detectFailures` 对无记录任务"benefit of the doubt"）；
  2. `EmbeddedDistributedExecutor` 类执行器若依赖 `completedTasks`/终态上报判断结果，会得到假成功；
  3. 该任务 checkpoint ACK 永远缺失，checkpoint 反复超时 abort，但没有任何恢复触发点。
- **建议**: 超时分支应设置 `error`（如 `StreamException(ERR_STREAM_INVALID_STATE, "invokable not installed within 30s")`）或置 `canceled=false` + 显式 FAILED 上报；至少与日志语义保持一致。
- **误报排除**: 已核对 `RunningTask.run()` 全文与 `reportTerminalStatus` 实现；`waitForInvokable` 超时是唯一不设 error 的提前 return 路径。

### [P1] RocksDB 增量快照的本地 `cp-{id}` 目录无任何清理代码（磁盘无限增长）

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:743-763`；`nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/incremental/RocksDBIncrementalSnapshotStrategy.java:60-112`
- **维度**: D2（检查点文件清理）
- **证据**:
```java
// RocksDBKeyedStateBackend.snapshotIncremental()
Path baseDir = Paths.get(checkpointBaseDir != null ? checkpointBaseDir : (dbPath + "-checkpoints"));
long cpId = incrementalSnapshotIdCounter.incrementAndGet();
IncrementalSnapshotResult result = strategy.doSnapshot(db, baseDir, cpId);
// strategy 内部创建 checkpointDir/cp-{id}/native 与 cp-{id}/non-sst，仅 deleteIfExists(nativeDir)
```
- **现状**: 每次增量 snapshot 在 `{dbPath}-checkpoints/cp-{id}/` 下产生一个 RocksDB native checkpoint（SST 硬链接/拷贝 + WAL）加一份 `non-sst/` 全量拷贝（MANIFEST/OPTIONS/CURRENT 等）。`RocksDBIncrementalSnapshotStrategy` 的类注释声称 "The strategy also performs no file deletion; lifecycle of the checkpoint directories is owned by the caller / coordinator"，但全仓库（runtime + rocksdb，排除 target/测试）grep `delete|clean|remove|walk` 没有任何代码清理该目录树；coordinator 只清理 `ISegmentStore` 共享段和 checkpoint storage。
- **风险**: 开启 `incrementalCheckpointEnabled` 的长期运行作业，每个 checkpoint interval 累积一个目录，磁盘单调增长直至耗尽（non-sst 拷贝是真实数据拷贝而非硬链接）。另外 `incrementalSnapshotIdCounter` 是 backend 本地计数器（进程重启后从 1 重新计数），目录名复用仅靠 `deleteIfExists(nativeDir)` 兜底，non-sst 目录靠 `REPLACE_EXISTING` 覆盖。
- **建议**: 在 `snapshotIncremental` 或 coordinator 的 subsumption GC 中按保留窗口清理 `checkpointBaseDir` 下超出 `maxRetainedCheckpoints` 的 `cp-*` 目录（需注意与 coordinator 异步持久化读取 SST 的时序，延迟一个窗口再删）。
- **误报排除**: 已确认 `LocalFileSegmentStore` 的段清理、`cleanupOldCheckpoints`、`gcSegmentsForCheckpoint` 均只作用于 segmentStore/storage，不触及任务本地 `dbPath + "-checkpoints"`。

### [P1] 检查点保留策略只删 checkpoint 数据，从不删 EpochManifest（`.epoch` 文件 / `stream_epoch_manifest` 行无限累积）

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:1032-1049`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/LocalFileCheckpointStorage.java:237-252`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:212-230`
- **维度**: D2（检查点文件清理）
- **证据**:
```java
// CheckpointCoordinator.cleanupOldCheckpoints() —— 唯一的滚动清理路径
CompletedCheckpoint old = allCheckpoints.get(i);
checkpointStorage.deleteCheckpoint(jobId, old.getPipelineId(), old.getCheckpointId());

// LocalFileCheckpointStorage.deleteCheckpoint() —— 只删一个文件
Path checkpointPath = getCheckpointPath(jobId, pipelineId, checkpointId);   // {id}.checkpoint
lock.writeLock().lock();
try { deleteIfExists(checkpointPath); } finally { lock.writeLock().unlock(); }
// JdbcCheckpointStorage.deleteCheckpoint() 同样只 DELETE checkpoint 表，不动 stream_epoch_manifest
```
- **现状**: 每次 checkpoint 完成都写两个等量数据：`{id}.checkpoint` 与 `{id}.epoch` manifest。`serializeEpochManifest`（CheckpointSerDe.java:142-203）把 **全量 taskSnapshots**（与 checkpoint 主体同样大）嵌入 manifest。滚动清理只调用 `deleteCheckpoint`，两个存储实现的该方法都只删 checkpoint 主体。
- **风险**: 任何启用 checkpoint 的长期运行作业（包括非增量模式），manifest 存储单调增长（每个文件≈一份完整状态快照）。同时 `loadRetainedEpochManifests` 恢复时会加载已被删掉 checkpoint 主体的 manifest，`restoreSharedStateRegistry` 据此注册 segment 引用，使共享段 GC 判定失真（引用已不存在/已淘汰的 epoch）。
- **建议**: `deleteCheckpoint` 同步删除对应 `.epoch` 文件 / `stream_epoch_manifest` 行（或提供 `deleteEpochManifest` 并在 cleanup 与 checkpoint 删除同事务调用）。
- **误报排除**: 已核对 `deleteAllCheckpoints`（LocalFile 版会 walk 删除整个 job 目录含 `.epoch`，JDBC 版仍不删 manifest 表——附带问题），确认滚动路径确实不删 manifest。

### [P1] `startCheckpointScheduler()` 生产代码零调用：分布式模式没有周期性 checkpoint

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:266-332`
- **维度**: D8（exactly-once 契约）+ D1
- **证据**:
```java
public synchronized void startCheckpointScheduler() {   // 全仓库 main 代码无调用方
    ...
    scheduler.scheduleAtFixedRate(() -> { tryTriggerCheckpointWithReason(CheckpointType.CHECKPOINT); ... },
            interval, interval, TimeUnit.MILLISECONDS);
}
```
- **现状**: 全仓库 grep（排除 target/测试）确认 `startCheckpointScheduler` 只在 3 个测试文件中被调用，生产路径零调用。分布式执行链（`JobCoordinator` / `RpcDistributedExecutor` / `EmbeddedDistributedExecutor`）构造了 `CheckpointCoordinator` 但从不启动调度器，也不做周期 `triggerCheckpoint()`（仅 `terminate(DRAIN/SUSPEND/EXPORT)` 时触发终端 savepoint）。周期 checkpoint 目前只存在于嵌入式路径（`GraphModelCheckpointExecutor.startBarrierScheduler` 自建 scheduler 直接 `tryTriggerPendingCheckpoint`）。
- **风险**: 分布式部署（Stage 39/42 RPC 拓扑、多 JVM remote-deploy）下作业长期运行而不产生任何中间 durable checkpoint；节点故障/globalRecovery 后只能从初始状态或很久以前的终端 savepoint 恢复，输入被重复消费——与平台 checkpoint/exactly-once 设计声明不符。minPause/maxConcurrent/超时 abort 等在调度器内实现的精细治理逻辑全部是死代码。
- **建议**: 在 `JobCoordinator.start()`（或 activateAsLeader）中按 CheckpointConfig 调用 `checkpointCoordinator.startCheckpointScheduler()`，并在 barrier 下发路径上改走 `triggerCheckpoint()` 的 source 定向发送；若确属分期未完成，应在文档/beans.xml 声明分布式模式暂无周期 checkpoint。
- **误报排除**: 已 grep 确认 `JobCoordinator` 内无调用；`GraphModelCheckpointExecutor` 有独立 barrier scheduler（嵌入式路径不受影响）；测试调用不计入。

### [P1] RemoteInputChannel：分发线程可永久阻塞在 `queue.put()`，`close()` 无法解除且 EOS 投递会静默失败

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java:328-337,349-423`
- **维度**: D2（通道泄漏）+ D3（通道读写竞争）
- **证据**:
```java
// EnvelopeConsumer.onMessage —— 运行在 IMessageService 的消息分发线程上
try {
    StreamElement element = StreamElementCodec.decode(envelope);
    if (!finished) {
        queue.put(element);            // 容量 1024，满时无限期阻塞分发线程
    }
} catch (InterruptedException e) { ... }

// close()
if (subscription != null && !subscription.isCancelled()) { subscription.cancel(); }
if (!finished) {
    finished = true;
    queue.offer(END_OF_STREAM);        // 满队列时 offer 立即返回 false，被忽略
}
```
- **现状**: 消费端背压通过阻塞消息分发线程实现。当任务线程异常退出（不再 `read()`）而上游持续发送时，本地队列填满，`onMessage` 卡死在 `queue.put()`；此时 `close()` 设置 `finished=true` 无法解除 `put` 阻塞（`subscription.cancel()` 是否中断正在执行的回调取决于 IMessageService 实现，通常不中断），满队列上 `offer(END_OF_STREAM)` 静默失败也无法唤醒潜在 reader。
- **风险**: 跨 JVM 数据面（Stage 40/42 后端：SysDao/Pulsar/Kafka）任务失败后，该 topic 的消费者/分发线程被永久占用——单线程 dispatch 的后端会拖停整个节点的数据面消费，构成线程/连接资源泄漏与级联停摆。
- **建议**: `close()` 中对可能阻塞的 put 提供解除手段（如改用 `offer` + 有界重试 + 关闭标志，或记录并丢弃溢出元素并告警）；EOS 投递失败时至少记录 warn。
- **误报排除**: 已读全文确认无其它唤醒机制；类注释自认 "Cross-JVM producer-side bound is the responsibility of the IMessageService backend"，但消费端 dispatch 线程阻塞仍是本类引入的。

### [P1] TaskManager.receiveAssignment 容量拒绝与 installInvokable 缺槽均静默 warn，任务不可检测地丢失

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:305-310,352-361`
- **维度**: D1（任务调度）+ D4
- **证据**:
```java
// receiveAssignment —— 容量满：仅 warn 后 return，无 FAILED 上报（对比 deployTask 的 reportDeployFailure+throw）
if (!capacitySemaphore.tryAcquire()) {
    LOG.warn("Node {} at capacity ({}/{}), rejecting assignment for {}/{}", ...);
    return;
}
// installInvokable —— slot 不存在（例如因上面被拒）：仅 warn
RunningTask runningTask = runningTasks.get(taskKey);
if (runningTask == null) {
    LOG.warn("No running task slot for {}/{}/{}", jobId, vertexId, subtaskIndex);
    return;
}
```
- **现状**: in-process 两阶段路径上，容量拒绝后 coordinator 侧 `taskAssignmentMap`/registry 已登记该 assignment，但任务永不创建。`deployTask` 路径已按 "#24 no silent skip" 硬化为 throw + FAILED 上报，`receiveAssignment` 的容量分支仍是静默失败。
- **风险**: 容量满（如并行度 > `capacity`，默认 16/节点）时该 subtask 永不运行：无 FAILED 报告、无 liveness 记录（`detectFailures` 对无记录任务不判 stall）、checkpoint ACK 永久缺失只导致反复 abort；`EmbeddedDistributedExecutor/RpcDistributedExecutor.waitForCompletion` 只统计 `runningTaskCount`，被拒任务不在计数内，其余任务完成后作业可“成功”返回——静默丢数据分支。
- **建议**: 容量拒绝分支镜像 `deployTask` 的处理（抛 StreamException + `reportDeployFailure` 同类的 FAILED 上报），使 coordinator 能感知并触发恢复/重新分配。
- **误报排除**: 已确认 `waitForCompletion` 的计数逻辑与 coordinator 侧无任何超时核对 assignment 数与运行数的机制。

### [P2] 检查点持久化失败不完成 PendingCheckpoint 的 future，等待方只能白等超时

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:821-831`
- **维度**: D4（异常吞噬/丢失败信号）
- **证据**:
```java
private void onCompletePersistFailure(CompletedCheckpoint completed, PendingCheckpoint pending, ...) {
    pending.getStatus().set(PendingCheckpoint.Status.FAILED);   // 直接覆盖状态
    pendingCheckpoints.remove(checkpointId, pending);
    decrementPendingCheckpointCount();
    notifyParticipantsFinishCommit(checkpointId, false);
    notifyCheckpointAborted(checkpointId);
    // 未调用 pending.fail(...) / completableFuture.completeExceptionally(...)
}
```
- **现状**: 存储落盘失败时直接 `set(FAILED)`，不完成 `completableFuture`。对比：abort 路径 `abortPendingCheckpoint → pending.abort()` 会 `completeExceptionally`；`PendingCheckpoint.fail()` 方法存在但从未被调用。
- **风险**: 所有等待 `pending.getCompletableFuture().get(timeout)` 的调用方（`JobCoordinator.terminateDrain/Suspend/ExportSavepoint`、`GraphModelCheckpointExecutor.triggerSavepoint/triggerTerminalSavepoint`）在持久化失败时感知不到失败，只能等满 checkpointTimeout 才超时返回；任何未来不带超时的消费者会永久挂起。失败信号被降级为超时，混淆诊断。
- **建议**: 失败回调中改用 `pending.fail(failMessage, cause)`（其内部 CAS 失败也无害，可改为直接 completeExceptionally），与 abort 路径行为对齐。
- **误报排除**: 已核对全部 future 消费点均有显式 timeout（当前不挂死），故定 P2 而非 P1。

### [P2] RocksDBKeyedStateBackend.openDB 泄漏 native `Options` 对象

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:205-211`
- **维度**: D2（RocksDB 句柄管理）
- **证据**:
```java
List<byte[]> existingCFs;
try {
    Options options = new Options(dbOptions, cfOptions);       // native 对象
    existingCFs = RocksDB.listColumnFamilies(options, dbPath);
} catch (RocksDBException e) {
    existingCFs = Collections.emptyList();
}
// options 从未 close()
```
- **现状**: `Options` 是 rocksdbjni 的 native 资源（`RocksObject` 持有 C++ 堆内存），创建后未关闭。同仓库 `RocksDBIncrementalRestore.restoreRangeInto`（L150-151）对同样的用法使用了 `try (Options listOpts = ...)` 正确关闭，证明此处是遗漏而非约定。
- **风险**: 每个 `RocksDBKeyedStateBackend` 实例（每个 keyed operator subtask 一个）泄漏一次 native 内存；backend 随任务重建（region restart、恢复）反复创建时累积。
- **建议**: 改为 try-with-resources。
- **误报排除**: 已确认 `close()`（L868-891）未涉及该对象；openDB 仅构造时调用一次。

### [P2] cleanupOldCheckpoints 跨 pipeline 计数并删除，同 jobId 多 pipeline 互相删对方检查点

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:1032-1049`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/LocalFileCheckpointStorage.java:149-192`；`JdbcCheckpointStorage.java:149-179`
- **维度**: D2/D8（检查点清理 + 契约）
- **证据**:
```java
// LocalFile: 遍历 {baseDir}/{jobId}/ 下【所有 pipeline 目录】
pipelineDirs.filter(Files::isDirectory).forEach(pipelineDir -> { ... result.add(cp); });
// JDBC: WHERE job_id = ? —— 同样不带 pipeline 过滤
// cleanupOldCheckpoints:
List<CompletedCheckpoint> allCheckpoints = checkpointStorage.getAllCheckpoints(jobId);  // 跨 pipeline
for (int i = maxRetained; i < allCheckpoints.size(); i++) {
    checkpointStorage.deleteCheckpoint(jobId, old.getPipelineId(), old.getCheckpointId());
```
- **现状**: `CheckpointCoordinator` 是 per (jobId, pipelineId) 的，但保留策略对 jobId 下全部 pipeline 的 checkpoint 全局按 id 排序删除。
- **风险**: 同一 jobId 运行多个 pipeline 时（CheckpointCoordinator 构造函数明确支持 pipelineId 维度），各 coordinator 在每次完成 checkpoint 时互删对方保留窗口内的 checkpoint：实际保留数低于 `maxRetainedCheckpoints`，极端情况下旧 durable checkpoint 被清光，恢复点前移/丢失。
- **建议**: `getAllCheckpoints` 增加 pipelineId 过滤重载，或 cleanup 侧按 `old.getPipelineId().equals(this.pipelineId)` 过滤。
- **误报排除**: 已核对当前默认执行入口均用 "pipeline-0"，单 pipeline 部署不受影响；但 TaskLocation/CheckpointPlan 均携带 pipelineId，多 pipeline 是接口支持的形态。

### [P2] cleanupOldCheckpoints 在持有 coordinator monitor 时全量反序列化所有保留检查点

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:794,1032-1049`；`LocalFileCheckpointStorage.java:149-192`
- **维度**: D6（性能）+ D3（锁粒度）
- **证据**:
```java
// onCompletePersistSuccess（caller 持 monitor；async 路径在 persist 线程重新加锁后调用）
cleanupOldCheckpoints();
...
private void cleanupOldCheckpoints() {
    List<CompletedCheckpoint> allCheckpoints = checkpointStorage.getAllCheckpoints(jobId);
    // LocalFile 实现: 读盘 + JSON 反序列化【每一个】保留 checkpoint 的全量状态
```
- **现状**: 每次 checkpoint 完成都把 jobId 下所有保留 checkpoint 从磁盘读入并 JSON 反序列化为 `CompletedCheckpoint`（含全部 taskStates），仅为比较数量与删除。
- **风险**: 大状态下每次 checkpoint 完成引入 O(retained × stateSize) 的 I/O 与 CPU，且全程持有 coordinator monitor，阻塞并发 ACK 处理、触发与 abort——checkpoint 越大越慢的正反馈。
- **建议**: 存储层提供轻量 listing（只返回 id/pipelineId，不反序列化 state_data），cleanup 只基于元数据决策。
- **误报排除**: 已核对 `getAllCheckpoints` 两个实现均无轻量变体；`getLatestCheckpoints` 同样全量反序列化。

### [P2] JdbcLeaderElector 将所有 INSERT 异常归类为"竞选失败"，DB 故障被 debug 级日志掩盖

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/JdbcLeaderElector.java:229-247`
- **维度**: D4（故障检测遗漏）
- **证据**:
```java
try {
    jdbcTemplate.executeUpdate(sql);
    LeaderEpoch leaderEpoch = new LeaderEpoch(getHostId(), epoch, new Timestamp(expireAt));
    onBecomeLeader(leaderEpoch);
    onElectionCompleted(leaderEpoch);
} catch (Exception e) {
    // Duplicate key or constraint violation — another node raced and won.
    // Safe to ignore; the next poll re-evaluates.
    LOG.debug("nop.stream.leader-elector.become-leader-fail: clusterId={}", getClusterId(), e);
}
```
- **现状**: `catch (Exception)` 不区分 duplicate key 与连接失败/表损坏/权限错误，全部按"别的节点赢了"静默处理且只打 debug 日志。同模块 `JdbcCheckpointStorage` 有 `isDuplicateKeyException` 精确判断可复用。
- **风险**: 数据库故障期间 HA 选主静默失效（可接受，poll 会重试），但真实故障被 debug 级日志掩盖，运维不可见；若故障持续，集群长期无 leader 而无任何 warn/error 痕迹。
- **建议**: 仅 duplicate-key 走静默分支（复用 `isDuplicateKeyException`），其余按 warn/error 上报。
- **误报排除**: 已确认 `changeLeader` 的乐观并发路径有 affected==0 判断，问题仅在 becomeLeader 的 INSERT 分支。

### [P3] 检查点反序列化的反射实例化白名单偏宽

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointSerDe.java:523-539`；`nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBSnapshotSerDe.java:518-519` 等；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/util/ClassNameValidator.java:16-63`
- **维度**: D5（反序列化）
- **证据**:
```java
// CheckpointSerDe.accumulatorFromForm
String typeName = (String) form.get("@type");
io.nop.stream.core.util.ClassNameValidator.validateAccumulatorClass(typeName);  // 仅要求前缀 "io.nop.stream."
acc = (SimpleAccumulator<?>) Class.forName(typeName).getDeclaredConstructor().newInstance();
// ClassNameValidator.validateClassName 白名单还含 "javax.crypto.", "javax.net.", "java.io." 等前缀（Class.forName 会执行静态初始化）
```
- **现状**: 检查点数据为 JSON（非 Java 原生序列化，整体安全形态良好），但 accumulator 恢复按 `@type` 类名反射实例化，白名单是前缀匹配而非精确类清单；RocksDB serde 的值类型恢复对白名单前缀内类做 `Class.forName`（触发静态块）。
- **风险**: 前提是攻击者已能写入 checkpoint 文件/DB 行（此时已具备更高权限），故为深度防御问题而非可远程利用漏洞；`io.nop.stream.` 下任意类的无参构造可被触发。
- **建议**: accumulator 恢复改为精确类清单（平台内建 accumulator 集合），或至少排除非 accumulator 目标类型。
- **误报排除**: 已确认非 Java 原生序列化、`validateClassName` 在每个 `Class.forName` 前均有调用。

### [P3] `CheckpointCoordinator.isShutdown` 为只写不读的死字段

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:194,1164`
- **维度**: D7（维护性）
- **证据**:
```java
private volatile boolean isShutdown = false;   // L194 声明
...
public void shutdown() { isShutdown = true; ... }   // L1164 唯一写入；全文件无读取
```
- **现状**: 字段声明意图是 shutdown 守卫，但没有任何读取点（`scheduleTimeout` 检查的是 `timeoutScheduler.isShutdown()`）。死代码，且给读者以"已有 shutdown 守卫"的误导。
- **风险**: 维护性/误导；无行为影响。
- **建议**: 删除该字段，或在 `tryTriggerCheckpointWithReason`/`acknowledgeTask` 入口真正检查它。
- **误报排除**: 已 grep 全文件确认仅 1 声明 + 1 写入。

### [P3] nop-stream-rocksdb 22 处 `new StreamException("...")` 无 ErrorCode

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/*.java`（22 处，如 `RocksDBMapState.java:226`）；`nop-stream-runtime` 3 处
- **维度**: D7（平台错误处理规范）
- **证据**:
```java
} catch (Exception e) {
    throw new StreamException("Failed to check MapState", e);   // 无 ErrorCode、无 .param(...)
}
```
- **现状**: 模块内大量使用字符串构造器，未走 `ERR_STREAM_STATE_ERROR` 等 ErrorCode + `.param()` 模式（同文件其它处已有该模式，风格不一致）。
- **风险**: 错误码体系覆盖不全，监控/告警无法按 ErrorCode 聚类；与仓库主流约定漂移。
- **建议**: 统一替换为 `new StreamException(ERR_STREAM_STATE_ERROR, e).param(ARG_DETAIL, "...")`。
- **误报排除**: 已统计计数；属于规范/一致性问题而非行为缺陷，故定 P3。

### [P3] RocksDB 写路径无 WriteBatch，MapState 集合视图每次全量物化

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:273-301,649-670`；`RocksDBMapState.java:230-275`
- **维度**: D6（性能）
- **证据**:
```java
// copyColumnFamilyRange / deleteByPrefix: 迭代中逐条 db.put / db.delete
db.put(dstCf, key, it.value());
...
for (byte[] k : toDelete) { db.delete(cf, k); }

// MapState.keys()/values()/entries(): 每次调用都 collectMap() 全量扫描物化
public Iterable<UK> keys() { return collectMap().keySet(); }
```
- **现状**: 批量写未用 `WriteBatch`（每条独立 JNI 调用与落盘放大）；`keys()/values()/entries()/iterator()` 每次全量扫描并物化整个 map。
- **风险**: 大 map / 大恢复量场景性能明显劣化；正确性无影响。
- **建议**: 批量路径用 WriteBatch + `db.write`；集合视图提供惰性迭代（封装 RocksIterator 并在关闭时释放）。
- **误报排除**: 已确认所有迭代器 try-with-resources 关闭正确（本项是性能而非泄漏）。

## 附注（已核对无问题的高风险面）

- **RocksDB 迭代器管理**: 全部 8 个 state 类 + SnapshotSerDe 的 `RocksIterator` 均用 try-with-resources，未发现泄漏。
- **RocksDBIncrementalRestore**: Options/handles/src DB 关闭顺序与临时目录清理正确（finally 中 deleteRecursively）。
- **barrier 对齐/窗口算子**: `WindowOperator`/`MergingWindowSet` 的 purge/cleanup/merge 路径状态清理对称（多处审计修复注释 P1-INV-1/RL-6 在位），未发现新缺口。
- **fencing 一致性**: 数据面（envelope epochId）与控制面（TaskManager.currentFencingEpoch、collectAck 校验）均为单 long 严格相等比较，与 Stage 39 设计一致。
- **Nop IoC 约定**: 两个模块的类均为编程式构造/beans.xml 显式装配（`_vfs/nop/stream/beans/*.beans.xml`），未发现 private 字段注入或注解扫描假设。
- **bare RuntimeException**: 仅 2 处且均为受检异常转发包装（`JdbcCheckpointStorage.java:749`、`StreamControlRpcServer.java:124`），语境合理。
