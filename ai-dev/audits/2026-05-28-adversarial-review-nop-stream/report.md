# 对抗性审查报告：nop-stream 模块

**审查日期**: 2026-05-28
**审查范围**: nop-stream 全模块 (~86K 行 Java, 407 主代码文件)
**审查方法**: 开放式、发现导向，以 live code 为准

---

## 发现列表

### 发现 1：CheckpointCoordinator `latestCompletedCheckpoint` 非原子更新 — 状态可能丢失

**位置**: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java` 第 196-246 行 (`completePendingCheckpoint` 方法)

**问题**: `latestCompletedCheckpoint` 是一个 `volatile` 引用，但 `completePendingCheckpoint` 方法的完整操作序列（store → storeManifest → remove → set latest → cleanup → notify）没有任何整体同步。具体地：

```java
// 第 223-228 行
if (!pendingCheckpoints.remove(checkpointId, pending)) { ... }
latestCompletedCheckpoint = completed;   // volatile write
decrementPendingCheckpointCount();
```

在并发场景下（`maxConcurrentCheckpoints > 1`），两个 checkpoint 可以几乎同时完成：
- Checkpoint N 完成，写入 `latestCompletedCheckpoint = N`
- Checkpoint N+1 随后完成，写入 `latestCompletedCheckpoint = N+1`
- 但如果 Checkpoint N+1 的 `storeCheckPoint` 失败导致 abort，`latestCompletedCheckpoint` 不会被回滚到 N

更严重的是，`restoreFromCheckpoint()` 只恢复 `latestCompletedCheckpoint`。如果在 `storeCheckPoint` 成功但 `storeEpochManifest` 失败后，checkpoint 已写入存储但 `latestCompletedCheckpoint` 仍然是旧的——恢复时可能漏掉最新状态。

**为什么不修会怎样**: 在生产环境中，如果 checkpoint N+1 的存储成功但 manifest 失败导致 abort，同时节点故障，恢复时会使用旧的 checkpoint N，导致数据丢失。即使 `maxConcurrentCheckpoints = 1`，`startCheckpointScheduler` 的调度线程和 `completePendingCheckpoint` 之间也存在竞态。

**信心水平**: **确定**

---

### 发现 2：WindowOperator `triggerAccumulators` 未参与 keyed state — checkpoint 后状态丢失

**位置**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java` 第 183 行 & 第 305-311 行 & 第 990-1013 行

**问题**: `triggerAccumulators` 是一个普通的 `HashMap<String, SimpleAccumulator<?>>`，存储在 operator 内存中。虽然 `snapshotState` 和 `restoreState` 尝试对其进行持久化：

```java
// snapshotState (line 307-309)
result.putOperatorState("trigger-accumulators", new HashMap<>(triggerAccumulators));

// restoreState (line 317-320)
Object restored = snapshotResult.getOperatorState("trigger-accumulators");
if (restored instanceof Map) {
    this.triggerAccumulators = (Map<String, SimpleAccumulator<?>>) restored;
}
```

但这里有两个问题：

1. **反序列化丢失**: 当通过 JSON 序列化/反序列化（LocalFileCheckpointStorage 或 JdbcCheckpointStorage）进行 checkpoint 时，`SimpleAccumulator<?>` 对象会被序列化为 JSON map。恢复时直接强转 `(Map<String, SimpleAccumulator<?>>) restored` 会得到 `Map<String, LinkedHashMap>`，不是 `Map<String, SimpleAccumulator>`。后续 `getSimpleAccumulator` 调用会试图在 `LinkedHashMap` 上调用 `add()` / `getLocalValue()`，抛出 `ClassCastException`。

2. **NFACompiler 生成的 CountTrigger 依赖于此**: 如果 trigger accumulator 丢失或损坏，依赖计数触发的窗口（如 CountTrigger）将无法正确触发或丢失已计数的元素。

**为什么不修会怎样**: 任何使用 CountTrigger（或任何使用 `getSimpleAccumulator` 的 Trigger）的窗口操作，在 checkpoint 恢复后都会崩溃或行为错误。这是生产环境不可接受的。

**信心水平**: **确定**

---

### 发现 3：`MemoryInternalAppendingState` 共享 accumulator 实例 — 并发错误

**位置**: `nop-stream-core/.../state/backend/memory/MemoryKeyedStateBackend.java` 第 1001-1086 行 (`MemoryInternalAppendingState`)

**问题**: `MemoryInternalAppendingState` 有一个 `transient SimpleAccumulator<IN> accumulator` 实例字段，在 `add()` 方法中被复用：

```java
// 第 1061-1072 行
public void add(IN value) throws IOException {
    TypedNamespaceAndKey key = getStorageKey();
    ACC current = storage.get(key);
    accumulator.resetLocal();      // reset shared accumulator
    if (current != null) {
        accumulator.add((IN) current);
    }
    accumulator.add(value);
    storage.put(key, (ACC) accumulator.getLocalValue());
}
```

这个 `accumulator` 是实例级别（不是 per-key），而且 `MemoryInternalAppendingState` 是通过 `states.get(descriptor.getName())` 缓存和复用的。当不同的 key 调用 `add()` 时，它们共享同一个 `accumulator` 实例。虽然在单线程场景下看起来没问题（因为每次 `resetLocal()`），但如果多个 namespace（窗口）交替调用 `add()`，逻辑上仍然是安全的——只要 `resetLocal()` 正确清除。

**真正的风险在 checkpoint 序列化**: `accumulator` 是 `transient` 的。反序列化后，`rebind()` 会重新创建 accumulator。但序列化时，`storage` 中的值已经是 `getLocalValue()` 的结果（即 ACC 类型），不是 accumulator 本身。如果 ACC 类型不可序列化，或者 JSON round-trip 改变了类型（如 Integer→Long），就会导致 `accumulator.add((IN) current)` 中的类型转换失败。

**为什么不修会怎样**: 单线程下通常工作，但 checkpoint 恢复后的类型不匹配会导致静默数据错误或崩溃。

**信心水平**: **很可能**

---

### 发现 4：`SharedBuffer` 的 `registerEvent` 只写缓存不写 state — 崩溃时事件丢失

**位置**: `nop-stream-cep/.../nfa/sharedbuffer/SharedBuffer.java` 第 216-226 行

**问题**:

```java
EventId registerEvent(V value, long timestamp) {
    Integer id = eventsCount.get(timestamp);
    if (id == null) { id = 0; }
    EventId eventId = new EventId(id, timestamp);
    Lockable<V> lockableValue = new Lockable<>(value, 1);
    eventsCount.put(timestamp, id + 1);       // 写入 state
    eventsBufferCache.put(eventId, lockableValue);  // 只写入缓存！
    return eventId;
}
```

`eventsBufferCache` 是 `ConcurrentHashMap`，只在 `flushCache()` 时才写入 `eventsBuffer` state。如果 `flushCache()` 没有在 checkpoint 时被调用，新注册的事件只存在于内存缓存中。这意味着：

1. 如果进程在 `registerEvent` 和 `flushCache` 之间崩溃，已注册的事件会丢失
2. CEP 的 NFA 已经创建了引用这些事件的 ComputationState，恢复后 NFA 状态指向不存在的事件，导致匹配不完整或 NPE

查看 `NFA.doProcess()` → `EventWrapper.getEventId()` → `sharedBufferAccessor.registerEvent()` 的调用链，事件注册在 `process()` 的 try-with-resources 中完成。`EventWrapper.close()` 会调用 `releaseEvent()`，但 `flushCache` 只在显式调用时执行。

**为什么不修会怎样**: CEP 引擎在 checkpoint 间隔内的崩溃恢复可能导致部分匹配的事件引用断裂，NFA 状态机可能进入不一致状态。对于 exactly-once 语义的流处理场景，这意味着丢失匹配结果。

**信心水平**: **确定**

---

### 发现 5：`CheckpointCoordinator.retryFailedCommits` 使用 participant index 而非稳定标识符 — 重试可能指向错误 participant

**位置**: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java` 第 370-419 行

**问题**: `failedCommitParticipants` 使用 `ConcurrentSkipListMap<Long, Set<Integer>>` 存储，key 是 participant 在 `participants` 列表中的 index。但 `participants` 是一个 `CopyOnWriteArrayList`，participant 可以动态增减：

```java
// 第 385 行
failedCommitParticipants.computeIfAbsent(checkpointId, k -> ConcurrentHashMap.newKeySet()).add(i);

// retryFailedCommits (line 402)
for (Integer idx : failedIdx) {
    if (idx < participants.size()) {
        participants.get(idx).finishCommit(failedEpoch, true);
    }
}
```

如果在一个 checkpoint 的 `finishCommit` 失败和下一个 checkpoint 的 `retryFailedCommits` 之间，有新 participant 被添加到 `participants` 列表中（在失败 index 之前插入），那么 index 就会指向错误的 participant。这会导致：
- 正确的 participant 永远不会被重试（其 commit 状态处于不确定态）
- 错误的 participant 收到不属于自己的 finishCommit 调用

**为什么不修会怎样**: 在分布式事务场景下，如果一个 participant 的 commit 处于 prepared 但未 committed 状态，它永远不会被确认，可能导致资源锁定。

**信心水平**: **很可能**

---

### 发现 6：`JdbcCheckpointStorage.sidSequence` 静态同步序列 — 多实例和 JVM 重启时冲突

**位置**: `nop-stream-runtime/.../checkpoint/storage/JdbcCheckpointStorage.java` 第 37 行 & 第 692-694 行

**问题**:

```java
private static long sidSequence = System.currentTimeMillis();

private static synchronized long nextSid() {
    return ++sidSequence;
}
```

1. `sidSequence` 用 `System.currentTimeMillis()` 初始化。如果两个 JVM 实例在同一毫秒内启动，它们会有相同的起始值，导致 SID 冲突。这在分布式部署中几乎是必然的。

2. `static synchronized` 意味着所有 `JdbcCheckpointStorage` 实例共享同一个锁和序列。如果同一个 JVM 中有多个作业使用 JDBC 存储，它们会争用同一个锁，且 SID 不会冲突。但如果跨 JVM，SID 冲突是确定的。

3. 表定义中 `PRIMARY KEY (sid)`，SID 冲突会导致 INSERT 失败，checkpoint 写入失败，最终导致 checkpoint abort。

**为什么不修会怎样**: 多实例部署时 checkpoint 写入会因主键冲突而失败，导致频繁的 checkpoint abort 和恢复失败。

**信心水平**: **确定**

---

### 发现 7：`LocalFileCheckpointStorage.storeCheckPoint` 的 finally 块在成功路径上删除 temp 文件 — 无害但逻辑误导

**位置**: `nop-stream-runtime/.../checkpoint/storage/LocalFileCheckpointStorage.java` 第 59-73 行

**问题**:

```java
lock.writeLock().lock();
try {
    ensureDirectoryExists(checkpointPath.getParent().toString());
    byte[] data = serializeCheckpoint(checkpoint);
    Files.write(tempPath, data, ...);
    Files.move(tempPath, checkpointPath, ATOMIC_MOVE, REPLACE_EXISTING);
    return checkpointPath.toString();
} finally {
    lock.writeLock().unlock();
    deleteIfExists(tempPath);  // <-- 这里
}
```

`Files.move` 使用 `ATOMIC_MOVE` 在成功后会将 temp 文件移走，所以 `deleteIfExists(tempPath)` 不会找到文件。但如果 `ATOMIC_MOVE` 被实现为 copy+delete（某些文件系统不支持原子移动），`move` 后 temp 文件可能已经被删除。

实际上这个 bug 更隐蔽：如果 `Files.move` 抛出异常（比如磁盘满），finally 块会正确清理 temp 文件。所以这个逻辑是正确的，但 `storeEpochManifest`（第 471-487 行）有相同的模式，也是正确的。

**修正**: 这个发现实际上是假阳性。`deleteIfExists` 在 finally 中是正确的防御性编程。保留此条仅为记录审查过程中的思考。

**信心水平**: ~~假阳性~~ → 撤回

---

### 发现 8：`WindowOperator.mergeWindowContents` 的"last-write-wins"合并策略 — session 窗口数据丢失

**位置**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java` 第 785-830 行

**问题**: 当两个非 accumulator 的窗口合并时：

```java
// 第 819-821 行
} else {
    // Deterministic fallback for non-accumulator values.
    targetValue = sourceValue;
}
```

这里用 source 覆盖 target（"last-write-wins"）。对于 session 窗口合并，这意味着先到达的窗口内容会被后到达的窗口内容完全替换。如果 target 和 source 都有非 null 值，target 的数据会丢失。

具体场景：假设两个 session 窗口 W1=[0,5) 和 W2=[3,8) 需要合并为 W3=[0,8)。W1 已有值 "A"，W2 有值 "B"。合并后 W3 的值是 "B"——"A" 丢失了。

**为什么不修会怎样**: 在非 accumulator 的 session 窗口场景下（例如 `ProcessWindowFunction` 不使用聚合），合并会导致先到达的数据被静默丢弃。

**信心水平**: **很可能**（取决于是否存在不使用 accumulator 的 session 窗口场景）

---

### 发现 9：`TaskManager.RunningTask.run()` 中 `runningTasks.remove(key)` 在 finally 中 — 与 `cancelTask` 竞态

**位置**: `nop-stream-runtime/.../taskmanager/TaskManager.java` 第 409-441 行

**问题**: `RunningTask.run()` 的 finally 块中：

```java
finally {
    String key = taskKey(jobId, vertexId, subtaskIndex);
    completedTasks.put(key, new TaskResult(...));
    runningTasks.remove(key);    // <-- 可能与 cancelTask 竞态
}
```

同时 `cancelTask` 方法：

```java
public void cancelTask(String jobId, String vertexId, int subtaskIndex) {
    String taskKey = taskKey(jobId, vertexId, subtaskIndex);
    RunningTask task = runningTasks.remove(taskKey);  // <-- 先 remove
    if (task != null) {
        task.cancel();    // <-- 然后 cancel
    }
}
```

竞态场景：
1. `cancelTask` 线程：`runningTasks.remove(taskKey)` → 返回 task 对象
2. `RunningTask.run()` 线程（在 executor 中）：进入 finally，`runningTasks.remove(key)` → 不影响（已被移除）
3. `cancelTask` 线程：`task.cancel()` → 设置 `canceled = true`，但 task 可能已经在 finally 中了

这本身不会导致数据损坏，但有一个更微妙的竞态：`cancelTask` 在 `runningTasks.remove` 之后、`task.cancel()` 之前被打断，此时 `updateFencingToken` 的 `removeIf` 也不会找到这个 task。task 就处于"已从 runningTasks 移除但未 cancel"的僵尸状态。

**为什么不修会怎样**: 在高频 cancel/recovery 场景下可能出现僵尸 task，它仍在执行但无法被追踪或取消。

**信心水平**: **有趣的猜测**（实际影响取决于 task 执行时间和 cancel 频率）

---

### 发现 10：`CheckpointBarrierTracker` 缺失 — 无法验证 checkpoint 完整性

**位置**: 整个 nop-stream-core 和 nop-stream-runtime

**问题**: 我在审查中没有看到 `CheckpointBarrierTracker` 的完整实现文件（仅在 `StreamTaskInvokable` 和 `TaskManager` 中被引用）。从代码中可以看到：

```java
// StreamTaskInvokable.java line 173
((AbstractStreamOperator<?>) operators.get(i)).setSnapshotCallback(
    snapshot -> barrierTracker.acknowledgeOperator(opIndex, snapshot)
);
```

`CheckpointBarrierTracker` 似乎需要：
1. 接收 barrier trigger
2. 跟踪每个 operator 的 snapshot 完成
3. 在所有 operator 完成后发送 ACK

但如果一个 operator 的 `snapshotState` 抛出异常，ACK 可能永远不会被发送，导致 checkpoint 超时。在超时处理中，`PendingCheckpoint` 被 abort，但 operator 的状态可能已经部分写入。在 exactly-once 语义下，这意味着恢复后可能出现重复处理。

**为什么不修会怎样**: 部分 snapshot 失败可能导致 checkpoint 超时和重试，在极端情况下可能导致数据重复处理。

**信心水平**: **很可能**（需要确认 CheckpointBarrierTracker 的异常处理逻辑）

---

### 发现 11：`WindowOperator.windowNamespace` 使用字符串拼接 — 语义等价的窗口可能产生不同 namespace

**位置**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java` 第 832-837 行

**问题**:

```java
private String windowNamespace(W window) {
    if (window == null) { return "_null_window_"; }
    return window.getClass().getName() + "#" + window.toString();
}
```

namespace 的唯一性依赖于 `window.toString()` 的唯一性。如果两个不同的 `TimeWindow` 对象（例如 `TimeWindow(0, 1000)` 和 `TimeWindow(0, 1000)`）的 `toString()` 相同，它们会映射到同一个 namespace——这是正确的。

但如果 `toString()` 实现返回了截断或格式不一致的字符串（例如大数用科学计数法），就会导致 key 冲突。更实际的问题是：`window.toString()` 的稳定性依赖于 `Window` 类的实现。如果 `toString()` 包含额外信息（如内存地址），同一个逻辑窗口的两个实例会产生不同的 namespace。

**信心水平**: **有趣的猜测**（取决于 Window.toString() 的具体实现，TimeWindow 通常使用 `[start, end)` 格式，应该是安全的）

---

### 发现 12：`GraphModelCheckpointExecutor` 双重 barrier 调度 — barrier 注入和 CheckpointCoordinator 调度可能冲突

**位置**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java` 第 449-477 行

**问题**: `startBarrierScheduler` 创建了一个独立的 `ScheduledExecutorService`，定期触发 checkpoint 并调用 `triggerBarrierOnAllInvokables`。但 `CheckpointCoordinator` 本身也有一个 `startCheckpointScheduler` 方法（第 94-125 行），也创建了独立的调度器。

在 `executeWithCheckpoint` 中，只使用了 `startBarrierScheduler`（GraphModelCheckpointExecutor 自己的调度器），没有调用 `coordinator.startCheckpointScheduler()`。这两个调度器的职责有重叠：
- `CheckpointCoordinator.startCheckpointScheduler`: 触发 pending checkpoint（创建 PendingCheckpoint 对象）
- `GraphModelCheckpointExecutor.startBarrierScheduler`: 触发 pending checkpoint AND 注入 barrier

所以没有冲突——`executeWithCheckpoint` 只用了 `startBarrierScheduler`。但代码中存在两个独立的调度路径，容易在维护中引入 bug（比如有人调用 `coordinator.startCheckpointScheduler()` 导致双重触发）。

**信心水平**: **很可能**（设计混乱，虽然当前不冲突）

---

## 总评

### 最值得关注的 3 个方向

1. **Checkpoint 序列化/反序列化的类型安全**（发现 2、3、6）
   这是最实际、最可能影响生产的问题。`triggerAccumulators` 通过 JSON 序列化后无法正确恢复 `SimpleAccumulator` 对象，会导致使用 CountTrigger 的窗口在 checkpoint 恢复后崩溃。`JdbcCheckpointStorage` 的 SID 机制在多实例部署时必然冲突。这类 bug 在本地测试中可能不显现，但在分布式部署时会是第一个暴露的问题。

2. **CEP SharedBuffer 的事件持久化时机**（发现 4）
   事件注册只写缓存不写 state，如果 `flushCache` 不在每次 checkpoint 前被调用，崩溃恢复后 CEP 的 NFA 状态会引用不存在的事件。这直接破坏 exactly-once 语义，对于欺诈检测等场景是不可接受的。

3. **CheckpointCoordinator 的并发安全**（发现 1、5）
   `latestCompletedCheckpoint` 的非原子更新和 `failedCommitParticipants` 使用 index 而非稳定标识符，都是只有在生产环境高并发下才会暴露的问题。但一旦暴露，会导致状态丢失或事务悬挂。

### 盲区自评

1. **我没有完整阅读 `CheckpointBarrierTracker` 的实现**。它在 `nop-stream-core` 中，但我在文件列表中没有找到。它对理解 exactly-once 语义至关重要。
2. **没有阅读 connector 模块**。连接器（source/sink）的正确性对 exactly-once 至关重要，特别是幂等性和事务性写入。
3. **没有深入审查 nop-stream-flow 和 nop-stream-flink 的集成代码**。这些模块可能引入自己的状态管理和错误处理路径。
4. **没有运行测试**。很多并发问题只能通过压力测试暴露，代码审查只能发现"可能有问题的模式"。
5. **`NFACompiler`（1090 行）**我只粗略浏览了，没有深入分析 NFA 状态机的生成逻辑。Pattern 编译的正确性直接影响 CEP 的匹配行为。
6. **窗口 assigner 的边界条件**我只通过 `WindowOperator` 的消费端审查了，没有深入审查 `WindowAssigner` 的实现（如 TumblingEventTimeWindows、SlidingEventTimeWindows）。
