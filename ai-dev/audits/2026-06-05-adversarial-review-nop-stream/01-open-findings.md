# Adversarial Review: nop-stream (Round N+1)

**Date**: 2026-06-05
**Reviewer**: AI Adversarial Reviewer
**Module**: nop-stream (all submodules)
**Approach**: Open-ended discovery. 4 parallel exploration agents covering (1) CEP internals + NFA + SharedBuffer + model layer, (2) core execution engine + operators + watermark + windowing, (3) runtime checkpoint storage + source enumerator + WindowOperator + cluster, (4) connector + flow + fraud-example + api. Findings cross-verified against source code.

## Dedup Note

Prior audits (~10 adversarial rounds + deep audits, ~125+ findings). Read and deduped against:
- `2026-06-04-adversarial-review-nop-stream-r2/` (AR-1~AR-28)
- `2026-05-30-adversarial-review-nop-stream-r10/` (AR-1~AR-18)
- `2026-05-30-adversarial-review-nop-stream/summary.md` (Round 8+9+10)
- `2026-05-28-deep-audit-nop-stream-full/` (21 dimensions)
- `2026-05-25-deep-audit-nop-stream-full/` (21 dimensions)

This report focuses on files/subsystems not covered by prior rounds.

---

### [AR-1] PatternStreamBuilder.build() hardcodes inputSerializer=null — CEP serialization crash on persistent state backend

- **文件**: `nop-stream-cep/.../PatternStreamBuilder.java:139-150`
- **证据片段**:
  ```java
  final TypeSerializer<IN> inputSerializer = null;
  //  inputStream.getType().createSerializer(inputStream.getExecutionConfig());
  final CepOperator<IN, K, OUT> operator =
          new CepOperator<>(
                  inputSerializer,
                  isProcessingTime,
                  nfaFactory,
                  comparator,
                  pattern.getAfterMatchSkipStrategy(),
                  processFunction,
                  lateDataOutputTag);
  ```
- **严重程度**: P1
- **现状**: `inputSerializer` is hardcoded to `null` and passed to `CepOperator` → `SharedBuffer`. The original line (`inputStream.getType().createSerializer(...)`) is commented out. In the current in-memory state backend, objects are stored by reference so this works by accident. But any path that attempts to serialize events (persistent state backend, RocksDB, network transfer of state, checkpoint serialization) will encounter NPE.
- **风险**: CEP patterns crash on any state serialization path. With current in-memory backend this is latent, but migrating to a persistent backend or enabling any serialization-dependent feature (state redistribution, rescaling) triggers the crash.
- **建议**: Restore the commented-out line, or obtain the serializer through the runtime context during `CepOperator.open()`.
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫

---

### [AR-2] TaskExecutor.awaitCompletion() returns true after TimeoutException — caller misinformed about completion

- **文件**: `nop-stream-core/.../execution/TaskExecutor.java:331-353`
- **证据片段**:
  ```java
  public boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
      // ...
      for (Map.Entry<String, Future<?>> entry : taskFutures.entrySet()) {
          long remainingNanos = timeoutNanos - (System.nanoTime() - startTime);
          if (remainingNanos <= 0) {
              return false;
          }
          try {
              entry.getValue().get(remainingNanos, TimeUnit.NANOSECONDS);
          } catch (Exception e) {     // <-- catches TimeoutException
              LOG.debug("Task {} completed with exception", entry.getKey(), e);
          }
      }
      LOG.info("All tasks completed within timeout");
      return true;                    // <-- reached after timeout!
  }
  ```
- **严重程度**: P1
- **现状**: `Future.get(timeout)` throws `TimeoutException` (a subclass of `Exception`). The catch block catches it at DEBUG level without re-throwing. The loop then exits normally, and the method returns `true` — incorrectly claiming all tasks completed within the timeout. The Javadoc at line 328 says "return true if all tasks completed, false if timeout elapsed", but the implementation violates this contract.
- **风险**: Callers that rely on the boolean return to decide whether to proceed (e.g., job submission, graceful shutdown) will believe tasks succeeded when they actually timed out. This can lead to silent data loss or incomplete processing.
- **建议**: Catch `TimeoutException` separately, set a `timedOut` flag, and return `false` at the end. Or re-throw `TimeoutException` as `ExecutionException`.
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-3] TimestampsAndWatermarksOperator: Timer thread accesses shared state without synchronization

- **文件**: `nop-stream-core/.../operators/TimestampsAndWatermarksOperator.java:60-68,130-151`
- **证据片段**:
  ```java
  // Line 60-68: Timer runs on a SEPARATE thread
  watermarkTimer = new Timer("watermark-timer", true);
  watermarkTimer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
          watermarkGenerator.onPeriodicEmit(new OperatorWatermarkOutput());
      }
  }, watermarkInterval, watermarkInterval);

  // Line 130-138: OperatorWatermarkOutput used from Timer thread
  private class OperatorWatermarkOutput implements WatermarkOutput {
      @Override
      public void emitWatermark(Watermark watermark) {
          if (idle) return;
          long ts = watermark.getTimestamp();
          if (ts > lastWatermarkTimestamp) {
              lastWatermarkTimestamp = ts;
              output.emitWatermark(watermark);  // NOT thread-safe
          }
      }
  }
  ```
- **严重程度**: P1
- **现状**: A `java.util.Timer` creates a background thread that calls `watermarkGenerator.onPeriodicEmit()` → `OperatorWatermarkOutput.emitWatermark()`. This accesses `lastWatermarkTimestamp` (volatile) and calls `output.emitWatermark()` from the Timer thread. Meanwhile, `processElement()` (line 72) also calls `onEvent()` and `onPeriodicEmit()` from the operator thread. While `lastWatermarkTimestamp` is volatile, `output.emitWatermark()` is not thread-safe — it may internally write to a shared output buffer concurrently. The `BoundedOutOfOrdernessWatermarks` generator reads its internal `maxTimestamp` field (plain long, not volatile) from the Timer thread while `onEvent()` writes it from the operator thread.
- **风险**: Corrupted watermark values, missed watermarks, or concurrent modification of shared output structures leading to data corruption.
- **建议**: Remove the Timer approach. Use the operator's processing-time timer service (which fires callbacks on the operator thread) instead of `java.util.Timer`. Alternatively, synchronize all watermark emission.
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-4] StreamSourceOperator never calls sourceFunction.cancel() — source resource leak on shutdown

- **文件**: `nop-stream-core/.../operators/StreamSourceOperator.java:195-207`
- **证据片段**:
  ```java
  @Override
  public void open() throws Exception {
  }

  @Override
  public void finish() throws Exception {
      isRunning = false;
  }

  @Override
  public void close() throws Exception {
      isRunning = false;
  }
  ```
- **严重程度**: P1
- **现状**: Neither `finish()` nor `close()` calls `sourceFunction.cancel()`. The runtime's `SubtaskTask.cancel()` only calls `Thread.interrupt()`. Source functions that block on non-interruptible operations (message subscriptions, CDC connectors, file I/O) will not be properly cleaned up. Specifically, `MessageSourceFunction`'s `IMessageSubscription` remains active after operator shutdown, and `BatchLoaderSourceFunction`'s `IBatchLoader` may not be closed if blocked.
- **风险**: Subscription/connection/thread leaks for all source functions that rely on `cancel()` for cleanup. In long-running pipelines with restarts, leaked resources accumulate until OOM.
- **建议**: `close()` should call `sourceFunction.cancel()` if the source function implements `SourceFunction`. Also ensure `SinkFunction.finish()` is called before `close()` in `StreamSinkOperator`.
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

### [AR-5] AbstractStreamOperator.processBarrier() propagates barrier downstream on snapshot failure — partial checkpoint corruption

- **文件**: `nop-stream-core/.../operators/AbstractStreamOperator.java:264-289`
- **证据片段**:
  ```java
  public void processBarrier(CheckpointBarrier barrier) throws Exception {
      OperatorSnapshotResult snapshotResult = null;
      Exception snapshotError = null;
      if (barrier.snapshot()) {
          try {
              StateSnapshotResult context = new StateSnapshotContext(barrier.getId(), barrier.getTimestamp());
              snapshotResult = snapshotState(context);
              this.lastSnapshotResult = snapshotResult;
          } catch (Exception e) {
              snapshotError = e;
              LOG.error("Snapshot failed for checkpoint {}", barrier.getId(), e);
          }
      }
      if (output != null) {
          output.emitBarrier(barrier);   // <-- Barrier emitted EVEN IF snapshot failed
      }
      // ...
  }
  ```
- **严重程度**: P1
- **现状**: When `snapshotState()` throws, the error is caught and logged but the barrier is still emitted downstream at line 278. Downstream operators receive the barrier and assume upstream state was successfully snapshotted. The snapshot callback at line 280-287 delivers a failure result, but by that time the barrier has already propagated. In a streaming pipeline, this creates a checkpoint where some operators succeeded and others failed — a partially-complete checkpoint that is inconsistent.
- **风险**: Downstream operators may commit state based on an upstream snapshot that failed, leading to data inconsistency on recovery. The checkpoint coordinator may not detect this if it only checks per-operator results.
- **建议**: On snapshot failure, either (a) do not emit the barrier (break the pipeline), or (b) emit an abort barrier that signals downstream operators to skip their snapshots for this checkpoint ID.
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

### [AR-6] CheckpointSerDe inconsistent serialization — raw TaskStateSnapshot vs explicit Map

- **文件**: `nop-stream-runtime/.../checkpoint/storage/CheckpointSerDe.java:39-44 vs 128-135`
- **证据片段**:
  ```java
  // serializeCheckpoint (line 42-43):
  taskStatesMap.put(key, entry.getValue());  // raw TaskStateSnapshot object

  // serializeEpochManifest (line 128-135):
  Map<String, Object> snapshotMap = new LinkedHashMap<>();
  if (entry.getValue().getOperatorStates() != null) {
      snapshotMap.put("operatorStates", entry.getValue().getOperatorStates());
  }
  if (entry.getValue().getKeyedStates() != null) {
      snapshotMap.put("keyedStates", entry.getValue().getKeyedStates());
  }
  taskSnapshotsMap.put(key, snapshotMap);
  ```
- **严重程度**: P1
- **现状**: `serializeCheckpoint()` places the raw `TaskStateSnapshot` object into the serializable map, while `serializeEpochManifest()` explicitly decomposes it into a Map with "operatorStates" and "keyedStates" keys. The deserialization path `deserializeCheckpoint()` expects a Map with these specific keys. If `JsonTool` serializes the raw `TaskStateSnapshot` using its natural field names (which may differ, e.g., `taskLocation`, `operatorStateMap`, etc.), deserialization will fail or silently produce incorrect state.
- **风险**: Silent checkpoint data corruption or loss on restore. The epoch manifest path is safe but the regular checkpoint path is not.
- **建议**: Use the same explicit Map construction in `serializeCheckpoint()` as in `serializeEpochManifest()`.
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-7] SourceEnumerator.splitMetadata not persisted in checkpoint state — split cursor lost on recovery

- **文件**: `nop-stream-runtime/.../source/SourceEnumerator.java:61,93,210-222,230-260`
- **证据片段**:
  ```java
  // Line 61: field exists
  private final Map<String, SourceSplit> splitMetadata;

  // Line 93: populated during discovery
  splitMetadata.put(splitId, split);

  // Lines 210-222: snapshotState() does NOT include splitMetadata
  public SourceEnumeratorState snapshotState() {
      return new SourceEnumeratorState(
              new ArrayList<>(discoveredSplits),
              new ArrayList<>(unassignedSplits),
              assignedMap,
              new LinkedHashSet<>(finishedSplits),
              new LinkedHashSet<>(pendingAcknowledgements),
              discoveryCursor
      );  // splitMetadata missing
  }

  // Lines 230-260: restoreState() never restores splitMetadata
  ```
- **严重程度**: P2
- **现状**: `splitMetadata` stores per-split metadata (descriptions, cursors, offsets) but is excluded from both `snapshotState()` and `restoreState()`. After checkpoint recovery, all split metadata is empty. Sources that depend on cursor information to resume reading from the correct position will either start from scratch (data duplication) or from an incorrect position (data loss).
- **风险**: Data loss or duplication after checkpoint recovery for any source that stores position information in `SourceSplit` metadata.
- **建议**: Include `splitMetadata` in `SourceEnumeratorState` and restore it in `restoreState()`.
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-8] MessageSourceFunction: onMessage callback runs on message service thread without synchronization

- **文件**: `nop-stream-connector/.../connector/MessageSourceFunction.java:117-131`
- **严重程度**: P1
- **证据片段**:
  ```java
  public Object onMessage(String t, Object msg, IMessageConsumeContext context) {
      // ...
      try {
          ctx.collect((T) msg);  // ctx is SourceContext from StreamSourceOperator
      } catch (Exception e) {
          failed = true;
          return null;
      }
      return null;
  }
  ```
- **现状**: The `onMessage` callback is invoked by the `IMessageService`'s delivery thread (not the operator thread). It calls `ctx.collect()` which delegates to `StreamSourceOperator.SourceContext.collect()` — a method that calls `injectPendingBarrier()` (polls a `LinkedBlockingQueue`) and `output.collect()` (writes to the output). Neither operation is synchronized. If message delivery and the source's `run()` thread both call `collect()` concurrently, data corruption or barrier loss occurs.
- **风险**: Data corruption in the output stream, lost barriers, or `ConcurrentModificationException` in downstream operators.
- **建议**: Synchronize the `SourceContext.collect()` method, or ensure the message service delivers messages on the operator's thread (e.g., via a queue that the source's `run()` loop drains).
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-9] OperatorChain.open() opens operators in REVERSE order — comment says "forward order"

- **文件**: `nop-stream-core/.../jobgraph/OperatorChain.java:127,136`
- **证据片段**:
  ```java
  // Line 127: Javadoc says "forward order"
  // <p><strong>Implementation Note:</strong> The operators are opened in forward order.

  // Line 136: Code does REVERSE order
  for (int i = operators.size() - 1; i >= 0; i--) {
      try {
          operators.get(i).open();
  ```
- **严重程度**: P2
- **现状**: The Javadoc claims operators are opened in forward order (0→N, head→tail), but the code iterates from `size-1` to `0` (tail→head). If downstream operators depend on upstream operators being initialized first (e.g., to set up output wiring, register timers, or initialize shared state), reverse-order opening may cause NPEs or incorrect initialization. The close method also iterates in reverse (line 155), which is correct for closing (LIFO), but opening in reverse is potentially wrong.
- **风险**: Operator initialization failures or subtle state bugs when operators have cross-initialization dependencies.
- **建议**: Either fix the code to open in forward order (0→N), or fix the comment and document why reverse order is intentional.
- **信心水平**: 很可能
- **发现来源视角**: 新人开发者

---

### [AR-10] RecordWriter broadcast partial delivery on InterruptedException — downstream divergence

- **文件**: `nop-stream-core/.../execution/RecordWriter.java:144-156`
- **证据片段**:
  ```java
  if (isBroadcast) {
      int delivered = 0;
      for (ResultPartition partition : partitions) {
          try {
              partition.write(record);
              delivered++;
          } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new StreamException(ERR_STREAM_INTERRUPTED_WRITE, e);
              // partitions[delivered..end] NEVER received this record
          }
      }
  }
  ```
- **严重程度**: P2
- **现状**: During broadcast, if `partition.write()` throws `InterruptedException` at partition N, only partitions 0..N-1 received the record. The exception exits the loop, and partitions N+1..end never receive it. For broadcast semantics, this creates inconsistent state across downstream tasks.
- **风险**: Downstream tasks receive different subsets of broadcast data, leading to divergent state and incorrect results.
- **建议**: Either (a) complete all writes before throwing, or (b) use a best-effort approach that records which partitions failed and allows recovery.
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-11] WindowAggregationFunction.merge() default throws UnsupportedOperationException — no validation at construction

- **文件**: `nop-stream-core/.../operators/WindowAggregationFunction.java:20-22`
- **证据片段**:
  ```java
  default ACC merge(ACC acc1, ACC acc2) throws Exception {
      throw new UnsupportedOperationException("merge not implemented");
  }
  ```
- **严重程度**: P2
- **现状**: `merge()` has a default implementation that throws `UnsupportedOperationException`. If a session window assigner is used with an aggregation function that doesn't override `merge()`, the exception is thrown at runtime when two windows actually need to merge. This is not validated at pipeline construction time — the error only manifests when a merge is triggered, which could be a late-discovery production bug.
- **风险**: Late production failures for session windows when two windows first overlap. Debugging is difficult because the exception occurs deep in the window operator.
- **建议**: Validate at pipeline construction that `merge()` is implemented when using `MergingWindowAssigner`. Call `merge(acc, acc)` on test accumulators during construction.
- **信心水平**: 确定
- **发现来源视角**: 新人开发者

---

### [AR-12] SharedBuffer.hasEventInBuffer swallows exceptions — enables duplicate EventIds

- **文件**: `nop-stream-cep/.../nfa/sharedbuffer/SharedBuffer.java:210-217`
- **证据片段**:
  ```java
  private boolean hasEventInBuffer(EventId eventId) {
      try {
          return eventsBuffer.get(eventId) != null;
      } catch (Exception e) {
          LOG.error("Failed to check event in buffer for eventId={}", eventId, e);
          return false;
      }
  }
  ```
- **严重程度**: P2
- **现状**: Called from `registerEvent()` (line 190) as part of the EventId uniqueness loop:
  ```java
  while (eventsBufferCache.containsKey(eventId) || hasEventInBuffer(eventId)) {
      id++;
      eventId = new EventId(id, timestamp);
  }
  ```
  If the state backend throws a transient exception (I/O error, timeout), `hasEventInBuffer` returns `false`, causing `registerEvent` to create a duplicate EventId. This overwrites the existing event in the shared buffer, corrupting CEP state.
- **风险**: CEP shared buffer corruption on transient state backend errors. Duplicate EventIds overwrite existing events, breaking pattern matching.
- **建议**: Propagate the exception instead of returning `false`, or retry the operation.
- **信心水平**: 确定
- **发现来源视角**: 模型攻击者

---

### [AR-13] JdbcCheckpointStorage INSERT-then-UPDATE swallows all exceptions — not just duplicate key

- **文件**: `nop-stream-runtime/.../checkpoint/storage/JdbcCheckpointStorage.java:88-108`
- **证据片段**:
  ```java
  try {
      jdbcTemplate.executeUpdate(sql);
  } catch (Exception e) {    // catches ALL exceptions
      LOG.debug("INSERT failed, attempting UPDATE ...");
      jdbcTemplate.executeUpdate(updateSql);  // may fail with different error
  }
  ```
- **严重程度**: P2
- **现状**: The INSERT-fail-then-UPDATE pattern catches ALL exceptions from INSERT, not just duplicate key violations. If the INSERT fails due to connection timeout, disk full, SQL syntax error, or table locked, the code swallows the original exception and blindly attempts UPDATE. The UPDATE may succeed on a stale row (silently overwriting data) or fail with a different error, making debugging extremely difficult.
- **风险**: Silent data overwrite on stale checkpoint rows, or confusing error messages when the root cause is not a duplicate key.
- **建议**: Only catch the specific duplicate key exception (e.g., `SQLIntegrityConstraintViolationException` or check SQL state). Re-throw all other exceptions.
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-14] JdbcClusterRegistry.registerNode TOCTOU race — duplicate INSERT fails

- **文件**: `nop-stream-runtime/.../cluster/JdbcClusterRegistry.java:95-119`
- **证据片段**:
  ```java
  jdbcTemplate.txn().runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
      boolean exists = jdbcTemplate.exists(existsSql);
      if (exists) {
          jdbcTemplate.executeUpdate(updateSql);
      } else {
          jdbcTemplate.executeUpdate(insertSql);
      }
      return null;
  });
  ```
- **严重程度**: P2
- **现状**: Under READ COMMITTED isolation, two concurrent `registerNode()` calls for the same `nodeId` can both see that the node does not exist, then both attempt INSERT. The second INSERT fails with a unique constraint violation. Unlike `JdbcCheckpointStorage` which handles this with INSERT-then-UPDATE, `JdbcClusterRegistry` does NOT use that pattern.
- **风险**: Node registration failures under concurrent startup scenarios, causing tasks to not be assigned to the node.
- **建议**: Use the same INSERT-then-UPDATE pattern as `JdbcCheckpointStorage`, or use `MERGE`/`UPSERT` SQL.
- **信心水平**: 很可能
- **发现来源视角**: 10x 规模运维者

---

### [AR-15] GraphModelCheckpointExecutor savepoint restore uses current jobId — fails when jobId differs from savepoint

- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java:659-688`
- **证据片段**:
  ```java
  private static void restoreFromSavepointPath(...) {
      ICheckpointStorage savepointStorage = new LocalFileCheckpointStorage(savepointPath);
      String jobId = checkpointPlan.getJobId();
      String pipelineId = checkpointPlan.getPipelineId();
      CompletedCheckpoint savepointCheckpoint = savepointStorage.getLatestCheckpoint(jobId, pipelineId);
      // ...
  }
  ```
- **严重程度**: P2
- **现状**: The method queries for checkpoints using the CURRENT job's `jobId` and `pipelineId`. But the savepoint was created from a PREVIOUS job with different IDs. `getLatestCheckpoint()` returns null, and the restore is silently skipped. The job starts from scratch despite a valid savepoint existing at the path.
- **风险**: Savepoint restore silently fails. Jobs that should resume from a savepoint start with empty state, causing data loss.
- **建议**: For savepoint paths, query without jobId/pipelineId filter, or list all checkpoints in the savepoint directory.
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-16] TimestampsAndWatermarksOperator: dead code — empty batch-data handling blocks

- **文件**: `nop-stream-core/.../operators/TimestampsAndWatermarksOperator.java:96-101`
- **证据片段**:
  ```java
  if (now == lastEmitTime && watermarkInterval == 0) {
      // Element-count based trigger for batch data with interval=0
      // *** EMPTY ***
  } else if (now == lastEmitTime && watermarkInterval > 0) {
      // Same millisecond batch data - still use element count as fallback
      // *** EMPTY ***
  }
  ```
- **严重程度**: P3
- **现状**: Two empty `if` blocks with comments about "element-count based trigger for batch data" but no logic. For batch workloads where all records arrive in the same millisecond, watermarks may not advance properly.
- **风险**: Batch-mode pipelines with `watermarkInterval > 0` may not emit watermarks when all elements share the same millisecond timestamp.
- **建议**: Either implement the batch-data handling logic or remove the empty blocks and document the limitation.
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫

---

### [AR-17] NFAState.compareDeweyNumber converts int[] → String → split → parseInt — fragile and wasteful

- **文件**: `nop-stream-cep/.../nfa/NFAState.java:128-137`
- **证据片段**:
  ```java
  private static int compareDeweyNumber(DeweyNumber a, DeweyNumber b) {
      int minLen = Math.min(a.length(), b.length());
      String[] da = a.toString().split("\\.");
      String[] db = b.toString().split("\\.");
      for (int i = 0; i < minLen; i++) {
          int cmp = Integer.compare(Integer.parseInt(da[i]), Integer.parseInt(db[i]));
          if (cmp != 0) return cmp;
      }
      return Integer.compare(a.length(), b.length());
  }
  ```
- **严重程度**: P3
- **现状**: `DeweyNumber` internally stores `int[]` but this method converts to `String`, splits with regex, then parses back to `int`. This is called from `sortedCopy()` which is called by `NFAState.equals()` and `NFAState.hashCode()`. The round-trip is O(n*m) where m is digit count, versus O(n) direct comparison. Also fragile: if `DeweyNumber.toString()` format changes, comparison breaks silently.
- **风险**: Performance overhead on every NFAState comparison. Fragile coupling to `toString()` format.
- **建议**: Implement `DeweyNumber.compareTo()` directly on the internal `int[]` array.
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫

---

### [AR-18] EvalFunctionCondition hardcodes `Object` type parameter — type safety hole in model-driven CEP

- **文件**: `nop-stream-cep/.../model/builder/EvalFunctionCondition.java:16`
- **证据片段**:
  ```java
  public class EvalFunctionCondition extends IterativeCondition<Object> implements Serializable {
  ```
- **严重程度**: P3
- **现状**: The type parameter is hardcoded to `Object` instead of the actual event type `T`. When a model-driven pattern is applied to events of a specific type, the unchecked cast from `IterativeCondition<Object>` to `IterativeCondition<F>` at the call site produces a `ClassCastException` at runtime if the eval function calls methods specific to the actual event type but receives a different type.
- **风险**: Model-driven CEP patterns applied to wrong event types fail at runtime with `ClassCastException` deep inside the engine, with no compile-time protection.
- **建议**: Make `EvalFunctionCondition<T>` generic, or add runtime type checking in the `filter` method.
- **信心水平**: 很可能
- **发现来源视角**: XDSL 语义侦探

---

## 总评

经过 ~17 轮审计后，本轮仍发现 18 个新问题。我认为 nop-stream 当前最值得关注的 3 个方向是：

1. **Source 函数生命周期管理缺失** (AR-4 + AR-8): `StreamSourceOperator` 从不调用 `sourceFunction.cancel()`，这是所有 connector 资源泄漏的根因。`MessageSourceFunction` 进一步暴露了线程安全问题——回调在消息服务线程上运行，但 `SourceContext` 没有同步保护。这不是单个 connector 的 bug，而是框架级别的契约缺失。

2. **Watermark 生成器的线程模型错误** (AR-3): `TimestampsAndWatermarksOperator` 使用 `java.util.Timer` 在独立线程上生成 watermark，但 `WatermarkGenerator` 的内部状态（如 `BoundedOutOfOrdernessWatermarks.maxTimestamp`）不是线程安全的。这违反了流处理引擎的基本假设：算子状态只能从算子线程访问。

3. **Checkpoint 序列化一致性** (AR-5 + AR-6): `AbstractStreamOperator.processBarrier()` 在快照失败后仍传播 barrier，而 `CheckpointSerDe` 在不同路径使用不同的序列化格式。这两个问题组合意味着：一个快照失败的算子会传播 barrier，下游算子成功快照，最终 checkpoint 部分成功——恢复时状态不一致。且该不一致的 checkpoint 可能因序列化格式问题而在恢复时静默丢失数据。

## 本次审查的盲区自评

- **序列化正确性端到端验证**: 未实际运行 checkpoint 序列化/反序列化来确认 `CheckpointSerDe` 的不一致性是否在实际使用中触发。
- **Timer 服务重构后的状态**: AR-3 建议使用算子的处理时间定时器替代 `java.util.Timer`，但未验证当前 `HeapInternalTimerService` 是否已被正确集成到算子生命周期中。
- **CEP NFA 恢复路径**: 未深入验证 NFA 状态在 checkpoint 恢复后的正确性（特别是 SharedBuffer 的 Lockable 引用计数恢复）。
- **Connector 集成测试**: 未验证 `MessageSourceFunction` 和 `BatchConsumerSinkFunction` 在实际消息服务中的端到端行为。
- **nop-stream-flink 模块**: 该模块为空，未评估。

## 严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 6    | 序列化(1), 生命周期管理(2), 线程安全(2), Barrier语义(1) |
| P2      | 9    | 检查点(4), 状态管理(2), 广播(1), 窗口(1), 连接器(1) |
| P3      | 3    | 死代码(1), 性能(1), 类型安全(1) |
