# Adversarial Review: nop-stream (Round N+2)

**Date**: 2026-06-05
**Reviewer**: AI Adversarial Reviewer
**Module**: nop-stream (all submodules)
**Approach**: Open-ended discovery. 4 parallel exploration agents covering (1) core operators + execution + state, (2) CEP NFA + shared buffer + conditions, (3) runtime checkpoint + cluster + transport + window operator, (4) connector + flow + api + checkpoint. All findings cross-verified against source code.

## Dedup Note

Prior audits (~17 adversarial rounds + deep audits, ~150+ findings). Read and deduped against:
- `2026-06-05-adversarial-review-nop-stream/` (AR-1~AR-18)
- `2026-06-04-adversarial-review-nop-stream-r2/` (AR-1~AR-28)
- `2026-05-30-adversarial-review-nop-stream-r13/` (AR-1~AR-17)
- `2026-05-28-deep-audit-nop-stream-full/` (21 dimensions)
- Round 1-12 summaries

This report focuses on files/subsystems and issue types NOT covered by prior rounds.

---

### [AR-1] StreamSinkOperator.processBarrier() — no try/catch on snapshotState; failure kills checkpoint flow

- **文件**: `nop-stream-core/.../operators/StreamSinkOperator.java:55-84`
- **证据片段**:
  ```java
  @Override
  public void processBarrier(CheckpointBarrier barrier) throws Exception {
      OperatorSnapshotResult snapshotResult = null;
      if (barrier.snapshot()) {
          StateSnapshotContext context = new StateSnapshotContext(barrier.getId(), barrier.getTimestamp());
          snapshotResult = snapshotState(context);  // No try/catch!

          if (userFunction instanceof CheckpointParticipant) {
              TaskStateSnapshot participantState = ((CheckpointParticipant) userFunction).saveState(barrier.getId());
              // ...
              ((CheckpointParticipant) userFunction).prepareCommit(barrier.getId());
          } else if (userFunction instanceof TwoPhaseCommitSinkFunction) {
              ((TwoPhaseCommitSinkFunction<?>) userFunction).preCommit(barrier.getId());
          }
          this.lastSnapshotResult = snapshotResult;
      }
      if (snapshotCallback != null && snapshotResult != null) {
          snapshotCallback.accept(snapshotResult);
      }
  }
  ```
- **严重程度**: P1
- **现状**: Unlike `AbstractStreamOperator.processBarrier()` (which wraps `snapshotState()` in try/catch and delivers an error result to the snapshot callback), `StreamSinkOperator.processBarrier()` has NO error handling. If `snapshotState()`, `saveState()`, `prepareCommit()`, or `preCommit()` throws, the exception propagates upward. The `snapshotCallback` is never invoked, so `CheckpointBarrierTracker.acknowledgeOperator()` is never called. `operatorsToAck` never reaches zero, and the checkpoint hangs indefinitely.
- **风险**: Any snapshot failure in a sink operator deadlocks the entire checkpoint mechanism for that task. The `CheckpointBarrierTracker` will never acknowledge, so the `PendingCheckpoint` eventually times out, but all subsequent checkpoints are also blocked until then.
- **建议**: Wrap the snapshot body in try/catch, following the same pattern as `AbstractStreamOperator.processBarrier()`. Deliver an error result to the snapshot callback on failure.
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-2] DebeziumCdcSourceFunction.run() — finally block never cleans up source/subscription; resource leak when cancel() precedes run()

- **文件**: `nop-stream-connector/.../connector/DebeziumCdcSourceFunction.java:57-84`
- **证据片段**:
  ```java
  @Override
  public void run(SourceContext<ChangeEvent> ctx) throws Exception {
      if (runEntered) { return; }
      runEntered = true;        // volatile check-then-act (not atomic)
      this.draining = false;
      // running is NOT reset to true here!
      initCompletionLatch();

      try {
          if (!draining) {
              source = new DebeziumMessageSource(config);   // resource allocated
              subscription = source.subscribe(ctx::collect); // engine starts
          }
          while (running && !draining) {  // if cancel() already called, this is false
              if (completionLatch.await(1, TimeUnit.SECONDS)) { break; }
          }
      } finally {
          runEntered = false;  // ONLY resets guard; never cleans up source/subscription!
      }
  }
  ```
- **严重程度**: P1
- **现状**: Three interrelated issues:
  1. **Resource leak**: The `finally` block only resets `runEntered`. If `cancel()` was called before `run()` entered, `running` stays `false`, the while-loop never executes, but `source` and `subscription` are created and never cleaned up. The Debezium engine thread runs indefinitely.
  2. **`running` never reset**: After `cancel()` sets `running=false`, any subsequent `run()` call creates resources that are immediately abandoned because the while-loop sees `running==false`.
  3. **`runEntered` check-then-act**: Two concurrent `run()` calls can both read `runEntered==false` before either writes `true`, creating duplicate source/subscription instances.
- **风险**: Debezium engine threads leak permanently. In production with pipeline restarts, leaked CDC connections accumulate until connection pool exhaustion or OOM.
- **建议**: (1) Add resource cleanup in `finally` block. (2) Reset `running=true` at start of `run()` or guard resource creation. (3) Use `AtomicBoolean.compareAndSet` for `runEntered`.
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

### [AR-3] WindowOperator.emitWindowContents() evictor path uses watermark timestamp instead of event timestamp

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java:685-693`
- **证据片段**:
  ```java
  if (evictor != null) {
      Iterable<IN> elements = (Iterable<IN>) contents;
      List<TimestampedValue<IN>> wrapped = new ArrayList<>();
      for (IN element : elements) {
          long elementTimestamp = internalTimerService.currentWatermark();  // WRONG DEFAULT
          if (element instanceof StreamRecord) {          // ALWAYS FALSE
              elementTimestamp = ((StreamRecord<IN>) element).getTimestamp();
          }
          wrapped.add(new TimestampedValue<>(element, elementTimestamp));
      }
  ```
- **严重程度**: P1
- **现状**: Window contents are stored as raw values (via `element.getValue()` at `addWindowElement` line 499/532), NOT as `StreamRecord`. The `instanceof StreamRecord` check at line 690 is always false for stored window contents. The fallback timestamp is `internalTimerService.currentWatermark()` — the current watermark, NOT the element's original timestamp. The evictor receives wrong timestamps for every element, causing incorrect eviction decisions.
- **风险**: Time-based evictors (e.g., `TimeEvictor`) evict wrong elements — either too many or too few. Window results are silently incorrect for any pipeline using evictors.
- **建议**: Store the original element timestamp alongside the value when adding to the window (e.g., use `TimestampedValue` in window state), or store `StreamRecord` objects.
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-4] WindowOperator.snapshotState() shallow-copies mutable triggerAccumulators — checkpoint corruption

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java:383-388`
- **证据片段**:
  ```java
  @Override
  public OperatorSnapshotResult snapshotState(StateSnapshotContext context) throws Exception {
      OperatorSnapshotResult result = super.snapshotState(context);
      if (triggerAccumulators != null) {
          result.putOperatorState("trigger-accumulators", new HashMap<>(triggerAccumulators));
      }
      return result;
  }
  ```
- **严重程度**: P1
- **现状**: `new HashMap<>(triggerAccumulators)` is a shallow copy — the `SimpleAccumulator` values are shared between the live map and the snapshot. Processing continues after the snapshot and accumulators are mutated (e.g., via `trigger.onElement` → `getSimpleAccumulator().add()`), silently corrupting the already-captured checkpoint data. After restore, trigger state reflects a later mutation, not the checkpoint-time state.
- **风险**: Checkpoint contains corrupted trigger state. After restore, triggers behave incorrectly — windows may fire early, late, or not at all.
- **建议**: Deep-copy each `SimpleAccumulator` value during snapshot (e.g., via a `clone()` or `copy()` method on `SimpleAccumulator`).
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-5] WindowOperator.close() nulls triggerAccumulators while pending timers may still fire

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java:371-379,1569-1574`
- **证据片段**:
  ```java
  // close() at line 372:
  @Override
  public void close() throws Exception {
      super.close();
      // ...
      triggerAccumulators = null;  // line 379
  }

  // Context.getSimpleAccumulator() at line 1569:
  SimpleAccumulator<T> existing = (SimpleAccumulator<T>) triggerAccumulators.get(stateKey);
  // NPE if triggerAccumulators is null
  ```
- **严重程度**: P2
- **现状**: `close()` nulls `triggerAccumulators` at line 379. If `super.close()` triggers remaining timers (e.g., via `advanceWatermark(MAX_WATERMARK)` in the parent), those timer callbacks invoke `Context.getSimpleAccumulator()` which dereferences `triggerAccumulators` — causing `NullPointerException`.
- **风险**: NPE during operator shutdown if any timers are still pending. Depending on exception handling, this may mask the original shutdown cause.
- **建议**: Move `triggerAccumulators = null` to after timer service shutdown, or add null guard in `getSimpleAccumulator()`.
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-6] JobCoordinator.triggerCheckpoint() sends barrier to ALL TaskManagers — duplicate barrier injection

- **文件**: `nop-stream-runtime/.../coordinator/JobCoordinator.java:274-281`
- **证据片段**:
  ```java
  if (!taskRpcServices.isEmpty()) {
      for (Map.Entry<String, IStreamTaskRpcService> entry : taskRpcServices.entrySet()) {
          try {
              entry.getValue().triggerCheckpoint(barrier, token);
          } catch (Exception e) {
              LOG.error("Failed to send checkpoint signal to node {}", entry.getKey(), e);
          }
      }
  }
  ```
- **严重程度**: P1
- **现状**: `triggerCheckpoint()` sends the barrier to ALL registered TaskManagers via `taskRpcServices`. In a proper checkpoint flow, barriers should only be injected at source operators; downstream operators receive barriers through the data stream (via `processBarrier`). Broadcasting to all TaskManagers means every task (including middle and sink tasks) receives a `triggerCheckpoint()` call, each injecting the barrier into its local `CheckpointBarrierTracker`. This causes duplicate barrier injection for non-source tasks, which can trigger premature or duplicate checkpoints.
- **风险**: Non-source tasks receive spurious checkpoint triggers. For aligned barrier processing, this can cause the same checkpoint ID to be processed multiple times, corrupting checkpoint state tracking.
- **建议**: Only send `triggerCheckpoint()` to TaskManagers that host source subtasks. Use the task assignment map to filter by vertex role.
- **信心水平**: 很可能
- **发现来源视角**: 事务边界追踪者

---

### [AR-7] StreamTaskInvokable — MAX_WATERMARK not sent on source failure, leaving downstream timers unfired

- **文件**: `nop-stream-core/.../execution/StreamTaskInvokable.java:259-276`
- **证据片段**:
  ```java
  private void invokeSource() throws Exception {
      try {
          // ...
          if (head instanceof StreamSourceOperator) {
              StreamSourceOperator<?> sourceOp = (StreamSourceOperator<?>) head;
              if (sourceOp.getOutput() != null) {
                  sourceOp.run();
                  sourceOp.processWatermark(Watermark.MAX_WATERMARK);  // only on success
              }
          }
      } finally {
          if (outputWriter != null) {
              outputWriter.close();
          }
      }
  }
  ```
- **严重程度**: P2
- **现状**: If `sourceOp.run()` throws, `processWatermark(Watermark.MAX_WATERMARK)` is never called. Downstream operators with event-time timers (window operators, CEP operators) never receive the signal to fire their final windows/timers. Buffered data in those windows is silently lost rather than being flushed. The `MAX_WATERMARK` emission should be in the `finally` block before closing the writer.
- **风险**: Data loss in event-time windows when upstream source tasks fail. Windows remain open indefinitely until the downstream operator is also closed.
- **建议**: Move `sourceOp.processWatermark(Watermark.MAX_WATERMARK)` to the `finally` block (before `outputWriter.close()`), or at least emit it in a catch block before re-throwing.
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-8] CheckpointBarrierTracker.triggerCheckpoint() — rejected barrier leaves stale state, permanently disables checkpoints

- **文件**: `nop-stream-core/.../execution/CheckpointBarrierTracker.java:55-87`
- **证据片段**:
  ```java
  public synchronized boolean triggerCheckpoint(...) throws Exception {
      if (operatorsToAck.get() > 0) {
          return false;
      }

      this.currentCheckpointId = checkpointId;     // SET
      this.currentSnapshot = new TaskStateSnapshot(taskLocation, checkpointId);  // SET
      this.operatorsToAck.set(count);              // SET

      // ...
      boolean accepted = ((StreamSourceOperator<?>) head).offerBarrier(barrier);
      if (!accepted) {
          LOG.warn("Checkpoint {} rejected: source operator already has a pending barrier", checkpointId);
          return false;   // returns false, BUT state is already mutated!
      }
      return true;
  }
  ```
- **严重程度**: P2
- **现状**: When `offerBarrier()` returns false (source already has a pending barrier), the method returns `false` but has already set `currentCheckpointId`, `currentSnapshot`, and `operatorsToAck` to non-initial values. A subsequent `triggerCheckpoint()` call sees `operatorsToAck.get() > 0` at line 56 and immediately returns `false`. The tracker becomes permanently stuck — no new checkpoints can ever be triggered.
- **风险**: A single barrier rejection permanently disables the checkpoint mechanism for this task. No data durability guarantees after that point.
- **建议**: Reset all state (currentCheckpointId, currentSnapshot, operatorsToAck) before returning `false` on rejection, or move state mutation after the barrier acceptance check.
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-9] CepOperator — event-time timer state not persisted in checkpoint; timers lost on restore

- **文件**: `nop-stream-cep/.../operator/CepOperator.java:224`
- **证据片段**:
  ```java
  final Set<Long> registeredEventTimeTimers = new TreeSet<>();
  ```
- **严重程度**: P2
- **现状**: The `registeredEventTimeTimers` set is a local `TreeSet` created fresh in `open()`. It is NOT part of the checkpointed state (not saved in `snapshotState()` nor restored in `restoreState()`). The watermark IS persisted, but pending timers are not. After a checkpoint restore:
  1. `currentWatermark` is restored correctly
  2. But `registeredEventTimeTimers` is empty
  3. Timers that were registered before the checkpoint are lost
  4. Those timers never fire, causing pattern matches to remain in partial state indefinitely
- **风险**: In production deployments with checkpoint/restore, window-based timeout handlers may never fire after recovery. Partial CEP matches accumulate without cleanup.
- **建议**: Include `registeredEventTimeTimers` in the checkpointed state, or re-register timers from NFA state during `restoreState()`.
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

### [AR-10] CepOperator.onProcessingTime() — missing dangling partial match cleanup present in onEventTime()

- **文件**: `nop-stream-cep/.../operator/CepOperator.java` (onProcessingTime vs onEventTime)
- **严重程度**: P2
- **证据片段**: `onEventTime()` has a dangling cleanup block that checks if all partial matches are timed out and releases SharedBuffer nodes. `onProcessingTime()` has **no equivalent cleanup**.
- **现状**: In processing-time mode with patterns that have window times, partial matches that have fully timed out but remain as the sole entry in the queue will never be cleaned up. Over time, this causes slow memory accumulation of stale SharedBuffer entries.
- **风险**: Slow memory leak in processing-time mode for long-running patterns with window constraints.
- **建议**: Add the same dangling cleanup logic to `onProcessingTime()` that exists in `onEventTime()`.
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫

---

### [AR-11] CheckpointCoordinator.completePendingCheckpoint() not synchronized — double-store race

- **文件**: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:194-209,212-285`
- **证据片段**:
  ```java
  // acknowledgeTask() is synchronized (line 194)
  public synchronized boolean acknowledgeTask(...) {
      // ...
      if (pending.isFullyAcknowledged()) {
          completePendingCheckpoint(pending.toCompletedCheckpoint());
      }
      return true;
  }

  // completePendingCheckpoint() is NOT synchronized (line 212)
  public void completePendingCheckpoint(CompletedCheckpoint completed) {
      // ... storeCheckPoint, storeEpochManifest, forceComplete ...
  }
  ```
- **严重程度**: P2
- **现状**: `acknowledgeTask()` is `synchronized`, but it calls `completePendingCheckpoint()` which is NOT. Two threads entering `acknowledgeTask()` concurrently could both observe `isFullyAcknowledged()` return true inside their respective synchronized blocks, and both proceed to `completePendingCheckpoint()`. The CAS at line 220 prevents double-completion of the status, but `checkpointStorage.storeCheckPoint()` and `checkpointStorage.storeEpochManifest()` could each be executed twice for the same checkpoint — wasted I/O and potential storage-level side effects.
- **风险**: Duplicate checkpoint storage operations. Depending on the storage backend, this could cause unnecessary load or data anomalies.
- **建议**: Make `completePendingCheckpoint()` `synchronized`, or move the `isFullyAcknowledged()` check + completion call entirely within the synchronized block.
- **信心水平**: 很可能
- **发现来源视角**: 10x 规模运维者

---

### [AR-12] CheckpointCoordinator.shutdown() does not await timeoutScheduler termination — race with abort callbacks

- **文件**: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:463-482`
- **证据片段**:
  ```java
  public void shutdown() {
      stopCheckpointScheduler();

      timeoutScheduler.shutdownNow();  // No awaitTermination()!

      for (PendingCheckpoint pending : pendingCheckpoints.values()) {
          // ...
          pending.dispose();
      }
      pendingCheckpoints.clear();  // Race: timeout callbacks may still be accessing this map
  }
  ```
- **严重程度**: P2
- **现状**: `timeoutScheduler.shutdownNow()` is called but there is no `awaitTermination()`. Timeout callbacks could still be executing when `shutdown()` proceeds to `pendingCheckpoints.clear()`. Those callbacks call `abortPendingCheckpoint()` which accesses `pendingCheckpoints` — but the map is cleared, causing potential `ConcurrentModificationException` or missed aborts.
- **风险**: Race during shutdown. Pending checkpoint abort callbacks may access cleared map, causing exceptions or incomplete cleanup.
- **建议**: Add `timeoutScheduler.awaitTermination(5, TimeUnit.SECONDS)` after `shutdownNow()`.
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-13] DebeziumCdcSourceFunction — DebeziumConfig not Serializable, breaks serialization contract

- **文件**: `nop-stream-connector/.../connector/DebeziumCdcSourceFunction.java:29` and external `DebeziumConfig.java`
- **证据片段**:
  ```java
  public class DebeziumCdcSourceFunction implements DrainableSource<ChangeEvent> {
      private static final long serialVersionUID = 1L;
      private final DebeziumConfig config;  // NOT Serializable
  ```
- **严重程度**: P2
- **现状**: `DebeziumCdcSourceFunction` implements `SourceFunction` → `Serializable`, and declares `serialVersionUID = 1L`. But its `config` field holds `DebeziumConfig` which does NOT implement `Serializable`. Currently the embedded executor uses `OperatorChain.deepCopy()` which shares functions by reference (no serialization). This is a latent defect that will surface when distributed execution or state checkpointing involving source function serialization is attempted.
- **风险**: `NotSerializableException` when attempting distributed execution or serialization-based state management.
- **建议**: Make `DebeziumConfig` implement `Serializable`, or mark `config` as `transient` and reconstruct from a serializable representation.
- **信心水平**: 确定
- **发现来源视角**: 组合爆炸测试者

---

### [AR-14] BroadcastingRecordWriterOutput.close() — partial close on failure cascades into resource leak

- **文件**: `nop-stream-core/.../execution/StreamTaskInvokable.java:400-404`
- **证据片段**:
  ```java
  public void close() {
      for (Output<StreamRecord<Object>> output : outputs) {
          output.close();  // If this throws, remaining outputs never closed
      }
  }
  ```
- **严重程度**: P2
- **现状**: If one output's `close()` throws an exception, the loop terminates early and all remaining outputs are never closed. Each unclosed output may hold a `RecordWriter` that wraps a `ResultPartition` with an active queue. The producer-side sentinel (`END_OF_STREAM`) is never placed, so the downstream consumer's `InputGate` could block forever.
- **风险**: Resource leak on close failure can cascade into downstream task hangs.
- **建议**: Catch and log exceptions from each `output.close()`, continuing to close all remaining outputs before re-throwing the first error.
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-15] BatchConsumerSinkFunction.close() — flush failure silently swallows buffered data

- **文件**: `nop-stream-connector/.../connector/BatchConsumerSinkFunction.java:96-105`
- **严重程度**: P2
- **现状**: If `flush()` fails in `close()`, the exception is caught and only logged. The buffered records are never delivered. The consumer is then closed. No retry mechanism, no dead-letter path, and no re-throw. Data is silently dropped.
- **风险**: Data loss for batch sinks when the final flush fails during operator shutdown.
- **建议**: At minimum re-throw the exception after logging. Consider a retry or dead-letter mechanism.
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-16] RemoteResultPartition.write()/close() race — data element may be sent after END_OF_STREAM

- **文件**: `nop-stream-runtime/.../transport/RemoteResultPartition.java:84-119`
- **严重程度**: P2
- **现状**: `write()` checks `isFinished()` at line 88, and `close()` calls `markFinished()` at line 107 then sends END_OF_STREAM. If `write()` passes the `isFinished()` check but `close()` runs concurrently and sends END_OF_STREAM first, the data element arrives at the consumer after END_OF_STREAM. The consumer discards it, causing silent data loss.
- **风险**: Data loss when close() races with a concurrent write() in distributed mode.
- **建议**: Synchronize `write()` and `close()`, or use an atomic state transition that prevents writes after close begins.
- **信心水平**: 很可能
- **发现来源视角**: 10x 规模运维者

---

### [AR-17] Lockable mutable hashCode/equals based on AtomicInteger refCounter — violates Object contract

- **文件**: `nop-stream-cep/.../nfa/sharedbuffer/Lockable.java:95-109`
- **严重程度**: P3
- **现状**: `Lockable.equals()` and `hashCode()` depend on `refCounter.get()`, which changes with every `lock()`/`release()` call. This violates the `Object.hashCode()` contract: two objects that are equal at time T1 may not be equal at time T2; hash codes change over time. Currently safe because `Lockable` is used as map values (not keys), but `SharedBufferNode.equals()` compares `List<Lockable<SharedBufferEdge>>` using `Objects.equals`, which calls `Lockable.equals`. This is a latent defect that would surface if `SharedBufferNode` is ever placed in a `HashSet` or used as a map key.
- **风险**: Latent contract violation. Could cause subtle bugs if collection usage patterns change.
- **建议**: Remove `refCounter` from `equals()`/`hashCode()`, or document that `Lockable` must never be used as a collection key.
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫

---

### [AR-18] NFA.ConditionContext.getEventsForPattern() — raw-type Collections.EMPTY_LIST

- **文件**: `nop-stream-cep/.../nfa/NFA.java:938`
- **严重程度**: P3
- **现状**: Uses `Collections.EMPTY_LIST.<T>iterator()` which is a raw-type unchecked cast. Should use `Collections.<T>emptyList().iterator()`.
- **风险**: Trivial — produces unchecked warning only.
- **建议**: Replace with `Collections.<T>emptyList().iterator()`.
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫

---

## 总评

本轮审查在 ~17 轮对抗性审查和深度审计之后，仍然发现了 18 个新问题。我认为 nop-stream 当前最值得关注的 3 个方向是：

1. **Sink 算子的 checkpoint 契约不完整** (AR-1): `StreamSinkOperator.processBarrier()` 继承了 `AbstractStreamOperator` 的 barrier 处理签名，但完全重写了实现且丢失了错误处理。这不仅仅是一个 bug —— 它暴露了一个系统性问题：子类覆盖 barrier 处理时没有模板或抽象保障。每个新的算子子类都可能重复这个错误。

2. **Window 算子状态管理的正确性** (AR-3 + AR-4): `emitWindowContents()` 的 evictor 路径丢失了元素时间戳（总是用水位线时间戳），这是一个确定性的逻辑错误，影响所有使用 evictor 的窗口。`snapshotState()` 的浅拷贝问题意味着即使是正常运行的窗口，其 checkpoint 中的 trigger 状态也可能是损坏的。这两个问题组合意味着：使用 evictor 的窗口产生错误结果，且恢复后 trigger 行为也不正确。

3. **Connector 生命周期管理缺陷** (AR-2 + AR-13 + AR-15): `DebeziumCdcSourceFunction` 的 `run()` 方法有三重问题：资源泄漏、状态不复位、非原子重入检查。这与之前审查发现的 `StreamSourceOperator` 不调用 `cancel()` 的根因问题 (Round N+1 AR-4) 形成连锁：框架不调用 cancel → CDC source 的 finally 块也不清理 → 双重泄漏。

## 本次审查的盲区自评

- **WindowOperator 完整逻辑路径**: 未完整审查 `WindowOperator` 的 1668 行代码中的所有分支（如 session window merge、state snapshot 序列化格式）。
- **RPC 服务实现**: `IStreamTaskRpcService` 和 `IStreamCoordinatorRpcService` 的具体实现未审查，可能存在分布式协议层面的更多问题。
- **TaskManager 完整逻辑**: `TaskManager` 类的完整行为未深入审查。
- **端到端 checkpoint 恢复**: 未实际运行 checkpoint 序列化/反序列化来确认数据正确性。
- **nop-stream-flink 模块**: 仍然为空，未评估。

## 严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 6    | Checkpoint契约(1), 资源泄漏(1), 窗口正确性(2), 分布式协议(1), Barrier路由(1) |
| P2      | 9    | 状态持久化(2), 生命周期(2), 并发(2), 序列化(1), 连接器(1), 关闭安全(1) |
| P3      | 2    | API契约(1), 代码质量(1) |
