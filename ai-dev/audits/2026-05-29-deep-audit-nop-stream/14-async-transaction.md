# 维度14：异步与事务模式

## 第 1 轮（初审）

### [维度14-01] TwoPhaseCommitSinkFunction.finishCommit() 对 synchronizedMap 复合操作缺少外部同步

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:76-95`
- **证据片段**:
  ```java
  // pendingCommits 是 Collections.synchronizedMap(new TreeMap<>())
  public void finishCommit(long epochId, boolean success) throws Exception {
      Map<Long, Object> pending = getPendingCommits();
      if (success) {
          if (pending != null && !pending.isEmpty()) {
              TreeMap<Long, Object> toCommit = new TreeMap<>();
              for (Map.Entry<Long, Object> entry : pending.entrySet()) {  // ← 未持有锁的遍历
                  if (entry.getKey() <= epochId) {
                      toCommit.put(entry.getKey(), entry.getValue());
                  }
              }
              for (Long eid : toCommit.keySet()) {
                  commit(eid);
                  pending.remove(eid);
              }
          }
      }
  }
  ```
- **严重程度**: P1
- **现状**: `Collections.synchronizedMap` 的 JavaDoc 要求遍历时手动同步，但此方法在 `pending.entrySet()` 上遍历时没有加锁。`finishCommit` 可被 `whenComplete` 回调线程和超时调度器线程并发触发。
- **风险**: 并发调用 `finishCommit` 时可能导致 `ConcurrentModificationException`，或遗漏提交/重复提交。
- **建议**: 在 `finishCommit` 方法体中用 `synchronized(pending)` 包裹遍历和修改操作。
- **信心水平**: 高
- **误报排除**: `Collections.synchronizedMap` 的使用契约明确要求遍历时加锁，这是 JDK 规范级别的问题。
- **复核状态**: 未复核

---

### [维度14-02] CheckpointCoordinator.tryTriggerPendingCheckpoint() 的 numPendingCheckpoints check-then-act 竞态

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:146-167`
- **证据片段**:
  ```java
  public PendingCheckpoint tryTriggerPendingCheckpoint(CheckpointType checkpointType) {
      if (numPendingCheckpoints.get() >= config.getMaxConcurrentCheckpoints()) {  // ← check
          return null;
      }
      long checkpointId = checkpointIdCounter.getAndIncrement();
      // ... 中间操作 ...
      numPendingCheckpoints.incrementAndGet();  // ← act
  }
  ```
- **严重程度**: P2
- **现状**: `get()` 和 `incrementAndGet()` 是两个独立的原子操作，check-and-increment 复合操作不是原子的。两个线程可同时通过检查，导致实际并发 checkpoint 数量超过限制。
- **风险**: 在高频 checkpoint interval 下短暂出现超额 checkpoint，增加内存压力。
- **建议**: 使用 `compareAndSet` 循环包裹整个 check-and-increment。
- **信心水平**: 高
- **误报排除**: 非"看起来不优雅"。check-then-act 是经典的并发反模式，可量化地导致并发数超过配置限制。
- **复核状态**: 未复核

---

### [维度14-03] PendingCheckpoint.dispose() 未加 synchronized，与 acknowledgeTask() 存在竞态

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/PendingCheckpoint.java:111-125, 167-176`
- **证据片段**:
  ```java
  // acknowledgeTask 是 synchronized
  public synchronized void acknowledgeTask(TaskLocation taskLocation, TaskStateSnapshot state) {
      if (isDisposed || status.get() != Status.RUNNING) return;
      notYetAcknowledgedTasks.remove(taskLocation);
      // ...
      if (isFullyAcknowledged() && !completableFuture.isDone()) {
          completableFuture.complete(toCompletedCheckpoint());
      }
  }

  // dispose 不是 synchronized
  public void dispose() {
      if (!isDisposed) {
          isDisposed = true;
          notYetAcknowledgedTasks.clear();  // ← 可与 acknowledgeTask 并发
          taskStates.clear();
          if (!completableFuture.isDone()) completableFuture.cancel(false);
      }
  }
  ```
- **严重程度**: P2
- **现状**: `acknowledgeTask()` 是 `synchronized`，`dispose()` 不是。`dispose()` 可与 `acknowledgeTask()` 并发执行。`dispose()` 清空 `notYetAcknowledgedTasks` 后，`isFullyAcknowledged()` 可能错误返回 `true`，导致用不完整的 taskStates 构建 CompletedCheckpoint。
- **风险**: 在 checkpoint timeout 和最后一个 task ACK 同时到达的窗口中，可能构建不完整的 CompletedCheckpoint 并持久化，导致恢复时丢失部分 task state。
- **建议**: 为 `dispose()` 方法添加 `synchronized` 修饰符。
- **信心水平**: 高
- **误报排除**: 不是"看起来不优雅"。`synchronized` 方法与非同步方法操作同一 mutable state 是明确的线程安全 bug。
- **复核状态**: 未复核

---

### [维度14-04] notifyParticipantsFinishCommit() 重试无退避间隔

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:393-413`
- **证据片段**:
  ```java
  while (retries > 0) {
      try {
          participants.get(i).finishCommit(checkpointId, success);
          break;
      } catch (Exception e) {
          retries--;
          if (retries > 0) {
              LOG.warn("finishCommit failed, retrying ({} left)", retries, e);
              // ← 无 Thread.sleep / backoff
          }
      }
  }
  ```
- **严重程度**: P3
- **现状**: 3 次无间隔重试对瞬态故障（网络抖动等）几乎不可能成功。
- **风险**: 所有 commit 落入 `failedCommitParticipants`，增加数据一致性的延迟窗口。
- **建议**: 添加指数退避间隔。
- **信心水平**: 高
- **误报排除**: 不是"看起来不优雅"。无退避重试是可量化的效率问题。
- **复核状态**: 未复核

---

### [维度14-05] CheckpointCoordinator.shutdown() 不等待 in-flight 回调完成

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:445-459`
- **证据片段**:
  ```java
  public void shutdown() {
      stopCheckpointScheduler();
      timeoutScheduler.shutdownNow();
      for (PendingCheckpoint pending : pendingCheckpoints.values()) {
          pending.dispose();
      }
      pendingCheckpoints.clear();
      listeners.clear();
      participants.clear();
  }
  ```
- **严重程度**: P3
- **现状**: `shutdown()` 不等待 `whenComplete` 回调完成就直接 dispose + clear。回调可能正在操作 `pendingCheckpoints` map。
- **风险**: 在优雅关闭中，最后一个 checkpoint 的 `completePendingCheckpoint` 回调可能与 `shutdown()` 并发执行，导致二阶段提交的外部事务遗留悬挂事务。
- **建议**: 在 dispose 之前等待所有 `completableFuture` 回调完成。
- **信心水平**: 高
- **误报排除**: 这是优雅关闭的完整性问题，不是审美问题。
- **复核状态**: 未复核

---

## 检查范围说明

- `JdbcCheckpointStorage` 和 `JdbcClusterRegistry` 中的 `txn().runInTransaction()` 使用正确 ✓
- `LocalFileCheckpointStorage` 使用 `ReentrantReadWriteLock` + atomic file move ✓
- `TaskExecutor` 和 `TaskManager` 的线程池使用基本正确 ✓
