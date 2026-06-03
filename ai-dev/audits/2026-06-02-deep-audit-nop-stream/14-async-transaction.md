# Dimension 14: Async & Transaction Patterns — nop-stream

## 第 1 轮（初审）

### [维度14-01] TaskExecutor thread pool never shut down in StreamExecutionEnvironment.execute()

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java:265-277`
- **Evidence snippet**:
```java
TaskExecutor executor = new TaskExecutor();
// ... submit tasks ...
executor.awaitCompletion();
// NO executor.shutdown() called!
```
- **Severity**: P2
- **Current state**: In local execution path, TaskExecutor is created but never shut down. GraphModelCheckpointExecutor correctly does shutdown.
- **Risk**: Thread pool resources leaked in long-running applications.
- **Recommendation**: Add executor.shutdown() in finally block.
- **Confidence**: Certain
- **False positive exclusion**: GraphModelCheckpointExecutor demonstrates correct pattern.
- **Review status**: Unreviewed

### [维度14-02] CheckpointCoordinator.scheduler is non-volatile, read without synchronization

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:42,98-99,146-147`
- **Severity**: P2
- **Current state**: scheduler field written inside synchronized startCheckpointScheduler() but read in non-synchronized stopCheckpointScheduler(). Volatile isSchedulerStarted guard doesn't provide happens-before for scheduler field.
- **Risk**: Thread may see stale scheduler reference, causing thread leak.
- **Recommendation**: Declare scheduler as volatile, or synchronize stopCheckpointScheduler().
- **Confidence**: Very likely
- **False positive exclusion**: Java Memory Model allows stale non-volatile field reads even when separate volatile guard is true.
- **Review status**: Unreviewed

### [维度14-03] CheckpointCoordinator registerTask/unregisterTask copy-on-write without synchronization

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:354-372`
- **Severity**: P2
- **Current state**: Both methods implement check-then-copy-then-swap pattern. Concurrent calls can lose task entries.
- **Risk**: Registered task could be silently dropped, causing checkpoints to never complete.
- **Recommendation**: Use synchronized on registerTask/unregisterTask, or use ConcurrentHashMap.newKeySet() directly.
- **Confidence**: Very likely
- **False positive exclusion**: Volatile only guarantees visibility, not atomicity of read-copy-write.
- **Review status**: Unreviewed

### [维度14-04] PendingCheckpoint.forceComplete() is not synchronized

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/PendingCheckpoint.java:163-168`
- **Severity**: P2
- **Current state**: forceComplete() reads fields without holding intrinsic lock while acknowledgeTask() is synchronized and mutates same fields.
- **Risk**: Could capture incomplete task state snapshot.
- **Recommendation**: Make forceComplete() synchronized, or document single-caller constraint.
- **Confidence**: Very likely
- **False positive exclusion**: acknowledgeTask() is synchronized but forceComplete() is not.
- **Review status**: Unreviewed
