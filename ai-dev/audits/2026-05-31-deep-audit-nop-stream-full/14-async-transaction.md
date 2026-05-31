# 维度 14：异步与事务模式

## 第 1 轮（初审）

### [维度14-01] CheckpointCoordinator.registerTask/unregisterTask 存在 check-then-act 竞态条件

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:354-372`
- **证据片段**:
  ```java
  public void registerTask(TaskLocation taskLocation) {
      Set<TaskLocation> current = this.tasksToAcknowledge;  // (1) read volatile
      if (!current.contains(taskLocation)) {                  // (2) check
          Set<TaskLocation> newSet = ConcurrentHashMap.newKeySet();
          newSet.addAll(current);                             // (3) copy from stale ref
          newSet.add(taskLocation);                           // (4) add
          this.tasksToAcknowledge = newSet;                   // (5) write - may overwrite
      }
  }
  ```
- **严重程度**: P1
- **现状**: registerTask 和 unregisterTask 使用"读取 volatile → 检查 → 创建新 Set → 写回 volatile"模式，整个 read-check-write 序列不是原子的。两个并发调用可能互相覆盖。
- **风险**: 在分布式模式下并发任务注册可能丢失 TaskLocation，导致 checkpoint 永远无法被所有任务 ACK，进而导致 checkpoint 超时。
- **建议**: 将 registerTask/unregisterTask 改为 synchronized 方法，或使用 AtomicReference + CAS 循环模式，或直接使用 ConcurrentHashMap.newKeySet() 并通过 synchronized 保护 replace 语义的操作。
- **信心水平**: 确定
- **误报排除**: 不是误报——这是经典的 check-then-act 竞态条件。volatile 只保证可见性，不保证复合操作的原子性。
- **复核状态**: 已降级至 P3（独立复核确认：代码缺陷存在但当前所有生产调用路径均为单线程顺序执行，unregisterTask 无生产调用者。是潜在线程安全缺陷而非当前可触发的生产 bug）

### [维度14-02] TwoPhaseCommitSinkFunction.finishCommit 在 synchronized 块外调用 commit()

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:94-103`
- **证据片段**:
  ```java
  for (Map.Entry<Long, Object> entry : toCommit.entrySet()) {
      Long eid = entry.getKey();
      synchronized (pending) {
          if (!pending.containsKey(eid))    // check under lock
              continue;
      }                                      // lock released
      commit(eid);                           // commit outside lock
      synchronized (pending) {
          pending.remove(eid);               // remove under lock
      }
  }
  ```
- **严重程度**: P2
- **现状**: finishCommit 在 synchronized 块外执行 commit，如果在 containsKey 检查和实际 commit 之间另一个线程调用了 restoreFromEpoch（rollback+clear），可能 commit 一个已被回滚的事务。
- **风险**: 可能导致数据不一致——提交了一个本应回滚的事务。
- **建议**: 在 commit 前后保持锁，或使用不可变快照 + 序列化执行。
- **信心水平**: 很可能
- **误报排除**: 不是误报——锁的粒度设计确实存在 TOCTOU 问题。
- **复核状态**: 未复核

### [维度14-03] CheckpointCoordinator 创建的 ScheduledExecutorService 资源管理依赖显式 shutdown

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:71-75,473-492`
- **证据片段**:
  ```java
  this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "checkpoint-timeout-" + jobId);
      t.setDaemon(true);
      return t;
  });
  ```
- **严重程度**: P3
- **现状**: timeoutScheduler 在构造时创建，shutdown() 正确关闭。daemon 线程不阻止 JVM 退出。但如果 coordinator 被 GC 而未调用 shutdown，存在资源管理问题。
- **风险**: 嵌入式模式下通常有明确的 shutdown 流程，影响有限。
- **建议**: 考虑实现 AutoCloseable 或使用 try-with-resources 模式。
- **信心水平**: 确定
- **误报排除**: shutdown() 方法已正确实现，问题是调用时机。
- **复核状态**: 未复核

### [维度14-04] JdbcCheckpointStorage.ensureTable 的 DDL 在事务中执行，部分数据库不兼容

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:431-463`
- **严重程度**: P3
- **现状**: DDL（CREATE TABLE/INDEX/CONSTRAINT）在事务中执行，部分数据库会隐式提交。tablesInitialized 标志可能在异常前被设为 true。
- **建议**: 将 DDL 操作移到事务外执行，或在 DDL 失败时重置标志。
- **信心水平**: 很可能
- **误报排除**: 不是误报——MySQL/PostgreSQL 的 DDL 确实会隐式提交。
- **复核状态**: 未复核

### [维度14-05] cleanupOldCheckpoints 无事务保护

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:390-404`
- **严重程度**: P3
- **现状**: getAllCheckpoints 和 deleteCheckpoint 之间无事务保护。
- **建议**: 影响有限（按 ID 排序后只删旧的），可接受。
- **信心水平**: 确定
- **误报排除**: 低概率问题。
- **复核状态**: 未复核

## 合规亮点

- Fencing token 防护机制设计良好（AtomicReference + CAS）
- 事务使用参数化传递
- ExecutorService 关闭处理规范（shutdown → awaitTermination → shutdownNow）
