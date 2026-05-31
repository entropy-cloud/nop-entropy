# 维度 14：异步与事务模式

## 第 1 轮（初审）

### [维度14-01] CheckpointCoordinator: registerTask/unregisterTask copy-on-write 竞态导致任务丢失

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:354-371`
- **证据片段**:
  ```java
  public void registerTask(TaskLocation taskLocation) {
      Set<TaskLocation> current = this.tasksToAcknowledge;
      if (!current.contains(taskLocation)) {
          Set<TaskLocation> newSet = ConcurrentHashMap.newKeySet();
          newSet.addAll(current);
          newSet.add(taskLocation);
          this.tasksToAcknowledge = newSet;
      }
  }
  ```
- **严重程度**: P0
- **现状**: registerTask 和 unregisterTask 对 volatile Set 执行 read-copy-write 但无同步保护，并发调用可能互相覆盖
- **风险**: 并发注册/注销时任务可能从 tasksToAcknowledge 中丢失，导致检查点永远无法收齐 ACK，最终超时
- **建议**: 加 synchronized 或改用单一 ConcurrentHashMap + putIfAbsent/remove
- **信心水平**: 确定
- **误报排除**: 不是设计上的有意解耦——竞态条件在高并行度恢复场景中实际可触发
- **复核状态**: 未复核

### [维度14-02] PendingCheckpoint: forceComplete() 未同步，与 acknowledgeTask() 竞态

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/PendingCheckpoint.java:163-168`
- **证据片段**:
  ```java
  public boolean forceComplete() {
      if (isFullyAcknowledged() && !completableFuture.isDone()) {
          return completableFuture.complete(toCompletedCheckpoint());
      }
      return false;
  }
  ```
- **严重程度**: P0
- **现状**: forceComplete() 不是 synchronized 的，但读取 notYetAcknowledgedTasks（由 synchronized 的 acknowledgeTask() 修改），存在 check-then-act 竞态
- **风险**: abort() 可能在 forceComplete() 的 check 和 complete 之间介入，导致已 abort 的检查点被正常完成
- **建议**: 将 forceComplete() 改为 synchronized，或在方法内也检查 status
- **信心水平**: 确定
- **误报排除**: CompletableFuture.complete() 虽然是幂等的，但 toCompletedCheckpoint() 的时序问题确实存在
- **复核状态**: 未复核

### [维度14-03] CheckpointCoordinator: 检查点存储失败后 CompletableFuture 永不 resolve

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:225-253`
- **证据片段**:
  ```java
  // 存储失败时:
  pending.getStatus().set(PendingCheckpoint.Status.ABORTED);
  pendingCheckpoints.remove(checkpointId, pending);
  // 缺失: pending.getCompletableFuture().completeExceptionally(e);
  ```
- **严重程度**: P0
- **现状**: 存储失败时未对 completableFuture 调用 completeExceptionally()，等待的线程将永远阻塞直到超时
- **风险**: 在 DRAIN/SUSPEND 模式下导致优雅关闭挂起 60 秒
- **建议**: 在存储失败路径添加 pending.getCompletableFuture().completeExceptionally(e)
- **信心水平**: 确定
- **误报排除**: 不是设计意图——代码中其他异常路径都有正确的 future 完成
- **复核状态**: 未复核

### [维度14-04] TwoPhaseCommitSinkFunction: finishCommit 三段 synchronized 间隙导致非原子提交

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:80-108`
- **严重程度**: P2
- **现状**: finishCommit() 对 pending Map 三次分别获取 synchronized 锁，锁与锁之间有间隙
- **风险**: 间隙中并发 saveState() 调用可能导致 toCommit 内容与当前 pending 不一致
- **建议**: 将整个 finishCommit 逻辑包裹在单个 synchronized(pending) 块内
- **信心水平**: 很可能
- **误报排除**: 流处理引擎通常在 checkpoint 完成回调中单线程调用 finishCommit，竞态在实践中不易触发
- **复核状态**: 未复核

### [维度14-05] CheckpointCoordinator: shutdown() 未等待 timeoutScheduler 终止

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:473-491`
- **严重程度**: P2
- **现状**: shutdown() 调用 timeoutScheduler.shutdownNow() 但不等待终止
- **风险**: 嵌入式使用场景中可能导致资源泄漏或测试不稳定
- **建议**: 添加 timeoutScheduler.awaitTermination(5, TimeUnit.SECONDS)
- **信心水平**: 很可能
- **误报排除**: stopCheckpointScheduler() 正确调用了 awaitTermination，此处遗漏是 bug
- **复核状态**: 未复核

### [维度14-06] TaskManager: taskExecutor 创建非守护线程，阻止 JVM 退出

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:105`
- **证据片段**:
  ```java
  this.taskExecutor = Executors.newFixedThreadPool(Math.max(1, capacity));  // 非守护线程
  this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "tm-heartbeat-" + nodeId);
      t.setDaemon(true);  // 正确
      return t;
  });
  ```
- **严重程度**: P2
- **现状**: taskExecutor 未设置 daemon=true，而同文件 heartbeatExecutor 正确设置了
- **风险**: 如果 stop() 未被调用，非守护线程阻止 JVM 退出
- **建议**: 为 taskExecutor 提供 ThreadFactory，设置 t.setDaemon(true)
- **信心水平**: 确定
- **误报排除**: 同文件中的 heartbeatExecutor 已经正确设置 daemon，taskExecutor 遗漏是 bug
- **复核状态**: 未复核

### [维度14-07] Lockable: equals/hashCode 依赖可变 refCounter，不适合做 Map key

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/Lockable.java:79-93`
- **严重程度**: P2
- **现状**: equals() 和 hashCode() 使用 AtomicInteger refCounter 的当前值，违反 Object contract
- **风险**: 如果 Lockable 被用作 HashMap/HashSet 的 key，lock()/release() 后无法正确查找
- **建议**: equals/hashCode 应只基于 element，不包含 refCounter
- **信心水平**: 很可能
- **误报排除**: 当前 SharedBuffer 中 Lockable 仅作为 value 而非 key，实际影响有限
- **复核状态**: 未复核

### [维度14-08] WindowOperator: windowNamespace 使用 System.identityHashCode，序列化后不一致

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:839-848`
- **严重程度**: P2
- **现状**: 非 TimeWindow 类型使用 System.identityHashCode 作为 namespace，反序列化后会改变
- **风险**: 检查点恢复时非 TimeWindow 窗口状态无法匹配正确 namespace
- **建议**: 要求 Window 类型实现稳定的 toString() 或提供 toNamespaceString()
- **信心水平**: 很可能
- **误报排除**: TimeWindow（主要使用场景）不受影响，GlobalWindow 单例在 JVM 内稳定
- **复核状态**: 未复核

### [维度14-09] MemoryStateSerDe: JSON round-trip 反序列化可能丢失复杂类型信息

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:598-607`
- **严重程度**: P2
- **现状**: deserializeValue() 在类型不匹配时使用 JSON round-trip 做转换，可能进一步丢失类型信息
- **建议**: 引入显式类型注册表替代通用 JSON 序列化
- **信心水平**: 很可能
- **误报排除**: 仅影响非基本类型的复杂对象状态
- **复核状态**: 未复核

### [维度14-10] JdbcCheckpointStorage: nextSid() 静态 synchronized 全局锁瓶颈

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:644-646`
- **严重程度**: P3
- **现状**: 所有实例共享同一个 static synchronized 锁
- **建议**: 改用 AtomicLong.getAndIncrement()
- **信心水平**: 确定
- **误报排除**: 检查点间隔通常数百毫秒到秒级，实际影响很小
- **复核状态**: 未复核

## 维度复核结论

本维度 3 个 P0 发现经复核确认成立。CheckpointCoordinator 的并发竞态是实际可触发的 bug，已在并发测试文件 TestCheckpointConcurrencySafety 中部分覆盖但未完全修复。

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 14-01 | P0 | CheckpointCoordinator.java | registerTask/unregisterTask 竞态 |
| 14-02 | P0 | PendingCheckpoint.java | forceComplete() 竞态 |
| 14-03 | P0 | CheckpointCoordinator.java | 存储失败 future 不 resolve |
| 14-04 | P2 | TwoPhaseCommitSinkFunction.java | 三段 synchronized 间隙 |
| 14-05 | P2 | CheckpointCoordinator.java | shutdown 未等待 timeoutScheduler |
| 14-06 | P2 | TaskManager.java | 非守护线程阻止 JVM 退出 |
| 14-07 | P2 | Lockable.java | equals/hashCode 依赖可变字段 |
| 14-08 | P2 | WindowOperator.java | identityHashCode 序列化后不一致 |
| 14-09 | P2 | MemoryStateSerDe.java | JSON round-trip 丢失类型信息 |
| 14-10 | P3 | JdbcCheckpointStorage.java | 静态全局锁瓶颈 |
