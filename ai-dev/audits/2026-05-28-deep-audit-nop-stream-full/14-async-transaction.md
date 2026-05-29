# 维度 14：异步与事务模式

## 第 1 轮（初审）

### [维度14-01] Pending checkpoint count leak on storage failure

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:196-280`
- **证据片段**:
  ```java
  // completePendingCheckpoint:
  // Line 204: CAS RUNNING -> COMPLETED (succeeds)
  // Line 210: storeCheckPoint() throws
  // Line 213: CAS COMPLETED -> ABORTED (succeeds)
  // Line 214: calls abortPendingCheckpoint(pending, ...)
  //   Line 258: CAS RUNNING -> ABORTED (FAILS -- status already ABORTED)
  //   Line 263: pendingCheckpoints.remove(checkpointId) skipped
  //   Line 270: decrementPendingCheckpointCount() skipped
  ```
- **严重程度**: P2
- **现状**: 当 completePendingCheckpoint 在 CAS 成功后存储失败时，通过 abortPendingCheckpoint 进行清理，但 abortPendingCheckpoint 的 CAS（RUNNING→ABORTED）因状态已是 ABORTED 而失败，导致 PendingCheckpoint 不被移除、计数器不递减。
- **风险**: 反复存储失败会导致 numPendingCheckpoints 持续累积至 maxConcurrentCheckpoints，最终永久阻塞所有 checkpoint。
- **建议**: 在 abortPendingCheckpoint 中也处理 COMPLETED→ABORTED 转换，或在 completePendingCheckpoint 存储失败时直接执行清理。
- **误报排除**: 不是理论上的竞态条件。通过代码路径分析确认了 CAS 状态不匹配导致清理跳过的逻辑错误。
- **复核状态**: 未复核

### [维度14-02] Lockable.refCounter is not thread-safe

- **文件**: `nop-stream-cep/.../sharedbuffer/Lockable.java:38-55`
- **严重程度**: P3
- **现状**: refCounter 的 lock()/release() 使用普通 int 运算，无同步保护。当前因 SharedBufferAccessor 的单线程契约而安全，但无强制机制。
- **建议**: 改为 AtomicInteger 或添加线程安全契约文档。
- **误报排除**: 当前设计是安全的（单线程访问），但缺少防御性保护。
- **复核状态**: 未复核

### 已验证正确的方面

- 事务边界短且良好（无远程调用）
- ExecutorService 生命周期正确管理
- 无死锁风险（简单锁森林）
- LocalFileCheckpointStorage 正确使用 ReadWriteLock + 原子文件移动
- PendingCheckpoint 状态机有良好的并发测试覆盖
