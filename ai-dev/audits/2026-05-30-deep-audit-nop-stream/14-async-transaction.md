# 维度 14：异步与事务模式

## 第 1 轮（初审）

### [维度14-01] Lockable.release() 的 check-then-act 竞态可导致引用计数变为负数

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/Lockable.java:54-60`
- **证据片段**:
  ```java
  // Line 29-31: Javadoc claims thread safety
  // "lock() and release() are thread-safe for concurrent access."
  // Line 54-60
  boolean release() {
      if (refCounter.get() <= 0) {   // Thread A reads 1, Thread B reads 1
          return true;
      }
      return refCounter.decrementAndGet() == 0; // A: 1->0 (returns true), B: 0->-1 (returns false)
  }
  ```
- **严重程度**: P1
- **现状**: Javadoc 声明线程安全，但 check-then-act 模式在并发场景下可导致 refCounter 变为 -1。
- **风险**: 若未来用于多线程场景，lock() 使计数回到 0，再 release() 立即返回 true，导致 SharedBuffer 节点被过早释放，CEP 匹配结果丢失。
- **建议**: 用 `decrementAndGet` 替代 check-then-decrement（先递减再检查结果是否 < 0，若是则递增回去）。
- **信心水平**: 确定
- **误报排除**: 当前 CEP operator 按 key 单线程处理，实际触发概率低。但文档明确承诺线程安全且使用 ConcurrentHashMap 暗示并发设计意图。
- **复核状态**: 未复核

### [维度14-02] CheckpointCoordinator.tryTriggerPendingCheckpoint 的 TOCTOU 竞态

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:165-186`
- **证据片段**:
  ```java
  public PendingCheckpoint tryTriggerPendingCheckpoint(CheckpointType checkpointType) {
      if (numPendingCheckpoints.get() >= config.getMaxConcurrentCheckpoints()) {
          return null;
      }
      // ... both create PendingCheckpoint ...
      pendingCheckpoints.put(checkpointId, pending);
      numPendingCheckpoints.incrementAndGet(); // Both increment: now exceeds max
  }
  ```
- **严重程度**: P1
- **现状**: `numPendingCheckpoints` 的读取和递增之间不是原子的。两个并发调用者都可以通过检查。
- **风险**: 可产生超过配置上限的并发 checkpoint。GraphModelCheckpointExecutor 和 CheckpointCoordinator 的调度线程可同时触发。
- **建议**: 用 `compareAndSet` 循环或 `synchronized` 包裹整个方法。
- **信心水平**: 确定
- **误报排除**: `startCheckpointScheduler` 已是 `synchronized`，但 `tryTriggerPendingCheckpoint` 本身不是，可从外部非同步路径调用。
- **复核状态**: 未复核

### [维度14-03] PendingCheckpoint.acknowledgeTask 与 isFullyAcknowledged 之间存在观察间隙

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:194-209`
- **证据片段**:
  ```java
  public boolean acknowledgeTask(...) {
      PendingCheckpoint pending = pendingCheckpoints.get(checkpointId);
      pending.acknowledgeTask(taskLocation, state); // synchronized on 'pending'
      // ↑ synchronized block exits here
      // Thread A and B both see isFullyAcknowledged() == true
      if (pending.isFullyAcknowledged() && !pending.getCompletableFuture().isDone()) {
          completePendingCheckpoint(pending.toCompletedCheckpoint()); // Both call this
      }
  }
  ```
- **严重程度**: P1
- **现状**: acknowledgeTask 在 synchronized 块内执行，但 isFullyAcknowledged 检查在 synchronized 块外。
- **风险**: 多个 ACK 线程可同时看到"已完全确认"状态，导致冗余的 toCompletedCheckpoint 调用和 GC 压力。
- **建议**: 将 isFullyAcknowledged() 检查也放入 synchronized 块内，或在 acknowledgeTask 内部直接返回完成状态。
- **信心水平**: 很可能
- **误报排除**: completePendingCheckpoint 内有 CAS 保护不会重复存储，但冗余的 HashMap 拷贝是真实开销。
- **复核状态**: 未复核

### [维度14-04] TaskManager.completedTasks 的逐出逻辑存在竞态且非 FIFO

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:454-464`
- **证据片段**:
  ```java
  completedTasks.put(key, new TaskResult(...));
  if (completedTasks.size() > MAX_COMPLETED_TASKS) {
      Iterator<String> it = completedTasks.keySet().iterator();
      if (it.hasNext()) {
          it.next();
          it.remove(); // Removes arbitrary entry, not necessarily oldest
      }
  }
  ```
- **严重程度**: P2
- **现状**: ConcurrentHashMap.keySet().iterator() 的迭代顺序不保证 FIFO，且 size() 检查与 remove() 之间可被其他线程修改。
- **风险**: 影响限于诊断/调试场景，不影响核心流处理正确性。
- **建议**: 使用 ConcurrentLinkedDeque 或加锁。
- **信心水平**: 确定
- **误报排除**: 不影响流处理正确性，仅影响诊断数据完整性。
- **复核状态**: 未复核

### [维度14-05] TwoPhaseCommitSinkFunction.commit() 在 synchronized 块内执行 I/O

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:76-96`
- **证据片段**:
  ```java
  synchronized (pending) {
      for (Long eid : toCommit.keySet()) {
          commit(eid);       // <-- Slow I/O while holding lock
          pending.remove(eid);
      }
  }
  ```
- **严重程度**: P2
- **现状**: finishCommit 在 synchronized(pending) 块内循环调用 commit()。若 commit() 涉及网络 I/O，持锁时间可能很长。
- **风险**: 阻塞 restoreFromEpoch 和并发的 finishCommit 调用，延迟故障恢复。
- **建议**: 在 synchronized 块内仅收集待提交条目，在锁外执行 commit。
- **信心水平**: 很可能
- **误报排除**: 当前 MemoryStateBackend 下的 commit 是瞬时操作，实际影响取决于后端实现。
- **复核状态**: 未复核

### [维度14-06] SharedBuffer.flushCache() 的缓存刷写非原子

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java:301-310`
- **证据片段**:
  ```java
  void flushCache() {
      if (!entryCache.isEmpty()) {
          entries.putAll(entryCache);  // Step 1
          entryCache.clear();          // Step 2
          // Gap: any put() between step 1 and step 2 loses data
      }
  }
  ```
- **严重程度**: P2
- **现状**: putAll 和 clear 之间不是原子操作。新写入的条目可能被 clear 清除而未被 putAll 包含。
- **风险**: 当前按 key 单线程处理不会触发。但 ConcurrentHashMap 的使用暗示了并发设计意图。
- **建议**: 若未来改为多线程访问，需用锁或原子操作保护。
- **信心水平**: 很可能
- **误报排除**: 当前 CEP operator 按 key 单线程处理，实际不会出现并发 flushCache 和 put。
- **复核状态**: 未复核

### [维度14-07] CheckpointCoordinator.shutdown() 与进行中的 checkpoint 操作存在竞态

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:468-487`
- **证据片段**:
  ```java
  public void shutdown() {
      stopCheckpointScheduler();
      timeoutScheduler.shutdownNow();
      // No synchronization here
      for (PendingCheckpoint pending : pendingCheckpoints.values()) {
          notifyParticipantsFinishCommit(checkpointId, false); // abort
          // Thread B may be calling completePendingCheckpoint() concurrently
      }
      pendingCheckpoints.clear();
  }
  ```
- **严重程度**: P2
- **现状**: shutdown() 不是 synchronized，可以与 completePendingCheckpoint/abortPendingCheckpoint 并发执行。
- **风险**: 参与者的 finishCommit 可能被调用两次（一次 abort，一次 commit）。当前 TwoPhaseCommitSinkFunction 在 abort 路径不做操作，无实际损害。但非幂等参与者会导致数据不一致。
- **建议**: 在 shutdown() 中加锁或对每个 pending checkpoint 先尝试 CAS 到 ABORTED 状态。
- **信心水平**: 很可能
- **误报排除**: 当前参与者是幂等的，实际影响有限。
- **复核状态**: 未复核

### [维度14-08] DebeziumCdcSourceFunction.run() 中异常时存在资源泄漏路径

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/DebeziumCdcSourceFunction.java:56-74`
- **证据片段**:
  ```java
  public void run(SourceContext<ChangeEvent> ctx) throws Exception {
      // ...
      subscription = source.subscribe(ctx::collect);
      // If InterruptedException thrown here:
      // subscription is not cancelled, source not stopped
      while (running && !draining) {
          if (completionLatch.await(1, TimeUnit.SECONDS)) {
              break;
          }
      }
      // No finally block to clean up subscription/source
  }
  ```
- **严重程度**: P2
- **现状**: `subscription` 分配后，如果后续 `await` 抛出 InterruptedException，subscription 不会被取消，source 也不会被停止。
- **风险**: Debezium engine 的 Kafka consumer 线程会继续运行，占用网络连接和内存。容器化部署中可能导致连接泄漏累积。
- **建议**: 将 while 循环包裹在 try-finally 中，在 finally 中调用 cancel()。
- **信心水平**: 确定
- **误报排除**: 已检查 cancel() 方法的实现，它能正确清理 subscription 和 source。
- **复核状态**: 未复核

### [维度14-09] JdbcCheckpointStorage.nextSid() 使用 static synchronized 导致类级别锁竞争

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:38, 636-638`
- **证据片段**:
  ```java
  private static long sidSequence = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
  private static synchronized long nextSid() {
      return ++sidSequence;
  }
  ```
- **严重程度**: P3
- **现状**: static synchronized 使用类级别 monitor，所有实例竞争同一把锁。
- **风险**: 锁持有时间极短（仅 ++操作），实际竞争概率低。但在多实例场景中可能成为微型瓶颈。
- **建议**: 替换为 AtomicLong。
- **信心水平**: 确定
- **误报排除**: 实际影响极小，标记为 P3。
- **复核状态**: 未复核

## 深挖第 2 轮追加

### [维度14-10] GraphModelCheckpointExecutor.shutdown() 不等待线程池终止

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:543-551`
- **证据片段**:
  ```java
  private static void shutdown(ScheduledExecutorService barrierScheduler, 
          CheckpointCoordinator coordinator, TaskExecutor executor) {
      if (executor != null) {
          executor.shutdownNow();
      }
      if (barrierScheduler != null) {
          barrierScheduler.shutdownNow();
      }
      coordinator.shutdown();
  }
  ```
- **严重程度**: P2
- **现状**: shutdown() 调用 shutdownNow() 后不调用 awaitTermination()。对比同模块 CheckpointCoordinator.stopCheckpointScheduler() 和 TaskManager.stop() 都正确执行了 awaitTermination()。
- **风险**: try-finally 中 shutdown 可能在线程池任务仍在处理 checkpoint 数据时强制中断，导致状态写入半截。
- **建议**: 为 barrierScheduler 和 executor 添加 awaitTermination() 等待。
- **信心水平**: 确定
- **误报排除**: 与同模块其他 shutdown 方法对比，缺少 awaitTermination 是遗漏。
- **复核状态**: 未复核

### [维度14-11] JdbcCheckpointStorage INSERT-then-UPDATE 回退吞没非主键冲突异常

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:80-100`
- **证据片段**:
  ```java
  try {
      jdbcTemplate.executeUpdate(sql);  // INSERT
  } catch (Exception e) {
      jdbcTemplate.executeUpdate(updateSql);  // UPDATE 可能影响 0 行
  }
  ```
- **严重程度**: P2
- **现状**: catch 块过于宽泛，将所有 INSERT 失败都回退为 UPDATE，UPDATE 可能静默影响 0 行。
- **风险**: 调用方认为存储成功，实际 checkpoint 数据未持久化。
- **建议**: 只在特定异常时回退为 UPDATE；UPDATE 后检查影响行数；或使用 MERGE/UPSERT。
- **信心水平**: 很可能
- **误报排除**: 宽泛 catch 是真实的数据丢失风险。
- **复核状态**: 未复核

### [维度14-12] JobCoordinator 终止方法超时后不中止 pending checkpoint

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:484-551`
- **证据片段**:
  ```java
  finalCheckpoint.getCompletableFuture().get(60, TimeUnit.SECONDS);
  // catch 块仅 log.error
  stop();
  ```
- **严重程度**: P2
- **现状**: terminateDrain/Suspend/ExportSavepoint 超时后仅记录日志，pending checkpoint 仍在 RUNNING 状态。
- **风险**: shutdown 与超时回调竞态，可能导致外部系统 prepared transaction 残留。
- **建议**: 超时 catch 块中显式调用 abortPendingCheckpoint 再 stop()。
- **信心水平**: 很可能
- **误报排除**: 分布式模式下的终止路径，超时是生产环境实际场景。
- **复核状态**: 未复核

### [维度14-13] CollectionReplayableSource.run() 对 currentOffset 的复合读写非原子

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/source/CollectionReplayableSource.java:29-33`
- **证据片段**:
  ```java
  while (running && currentOffset < data.size()) {
      ctx.collect(data.get((int) currentOffset));
      currentOffset++;
  }
  ```
- **严重程度**: P3
- **现状**: volatile 保证可见性但不保证复合操作原子性。seek() 可能在读取和递增之间被调用。
- **风险**: seek() 设置的新值被覆盖，导致跳过或重复消费数据。
- **建议**: 使用 AtomicLong + getAndIncrement()。
- **信心水平**: 确定
- **误报排除**: seek() 在恢复时被调用，与 run() 可并发。
- **复核状态**: 未复核

### [维度14-14] MessageSourceFunction.subscription 非 volatile

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:48, 107, 126-128`
- **证据片段**:
  ```java
  private IMessageSubscription subscription;  // 无 volatile
  // run() 线程赋值
  subscription = messageService.subscribe(effectiveTopic, ...);
  // cancel() 线程读取
  if (subscription != null) { subscription.cancel(); }
  ```
- **严重程度**: P3
- **现状**: 无 volatile 修饰符，跨线程可见性不保证。同模块 DebeziumCdcSourceFunction 已正确标记 volatile。
- **风险**: cancel() 可能看不到 subscription 赋值，导致消息订阅未被取消。
- **建议**: 将 subscription 声明为 volatile。
- **信心水平**: 确定
- **误报排除**: 同模块其他类已正确处理，说明是遗漏。
- **复核状态**: 未复核

## 维度复核结论

| 原编号 | 原等级 | 复核结果 | 原因 |
|--------|--------|---------|------|
| 14-01 | P1 | **降级为 P2** | 竞态真实存在但 CEP 算子按 key 单线程处理，实际触发概率极低。Javadoc 声称线程安全是不准确的。 |
| 14-02 | P1 | **降级为 P2** | 竞态真实存在但所有调用者实际单线程。后果仅多一个并发 checkpoint，不影响数据正确性。 |
| 14-03 | P1 | **降级为 P3** | 观察间隙存在但 completePendingCheckpoint 有完善 CAS 保护，完全消解了实际危害。 |

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 14-01 | P2 | Lockable.java:54-60 | release() check-then-act 竞态可致引用计数变负 |
| 14-02 | P2 | CheckpointCoordinator.java:165-186 | tryTriggerPendingCheckpoint TOCTOU 竞态 |
| 14-03 | P3 | CheckpointCoordinator.java:194-209 | acknowledgeTask 观察间隙（CAS 已保护） |
| 14-04 | P2 | TaskManager.java:454-464 | completedTasks 逐出非 FIFO |
| 14-05 | P2 | TwoPhaseCommitSinkFunction.java:76-96 | synchronized 内 I/O |
| 14-06 | P2 | SharedBuffer.java:301-310 | flushCache 非原子 |
| 14-07 | P2 | CheckpointCoordinator.java:468-487 | shutdown 与 checkpoint 操作竞态 |
| 14-08 | P2 | DebeziumCdcSourceFunction.java:56-74 | 异常时资源泄漏 |
| 14-09 | P3 | JdbcCheckpointStorage.java:636-638 | static synchronized 锁竞争 |
| 14-10 | P2 | GraphModelCheckpointExecutor.java:543-551 | shutdown 不等待线程池终止 |
| 14-11 | P2 | JdbcCheckpointStorage.java:80-100 | INSERT-then-UPDATE 吞没非 PK 异常 |
| 14-12 | P2 | JobCoordinator.java:484-551 | 终止超时不中止 pending checkpoint |
| 14-13 | P3 | CollectionReplayableSource.java:29-33 | currentOffset 复合读写非原子 |
| 14-14 | P3 | MessageSourceFunction.java:48 | subscription 非 volatile |
