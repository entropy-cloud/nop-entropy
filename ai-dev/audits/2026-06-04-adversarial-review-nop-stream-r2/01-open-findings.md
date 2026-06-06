# Adversarial Review: nop-stream (Round N+1)

**Date**: 2026-06-04
**Reviewer**: AI Adversarial Reviewer
**Module**: nop-stream (all submodules)
**Approach**: Open-ended discovery, focusing on areas not covered by ~16 prior audit rounds (transport codec, window triggers/evictors/assigners, CEP model layer, runtime checkpoint internals, fraud-example, state sharding, timer service internals).

## Dedup Note

Prior audits covered ~125 findings. This review focuses on previously uncovered files/subsystems. Cross-checked against findings from 2026-05-28 through 2026-06-04 audits.

---

### [AR-1] ProcessingTimeoutTrigger.onEventTime() always returns FIRE — copy-paste bug from onProcessingTime

- **文件**: `nop-stream-core/.../windowing/triggers/ProcessingTimeoutTrigger.java:103-110`
- **证据片段**:
  ```java
  @Override
  public TriggerResult onEventTime(long timestamp, W window, TriggerContext ctx) {
      TriggerResult triggerResult = this.nestedTrigger.onEventTime(timestamp, window, ctx);
      if (shouldClearOnTimeout) {
          this.clear(window, ctx);
      }
      return triggerResult.isPurge() ? TriggerResult.FIRE_AND_PURGE : TriggerResult.FIRE;
  }
  ```
- **严重程度**: P1
- **现状**: `onEventTime()` unconditionally promotes the nested trigger's result to FIRE, even when the nested trigger returns `CONTINUE`. This is a copy-paste of `onProcessingTime()` (lines 94-101) where FIRE is intentional because processing-time callbacks ARE the timeout. For event-time, the callback is from the nested trigger's normal timer — promoting CONTINUE→FIRE causes premature window firing on every event-time timer, including timers the nested trigger wants to ignore. Additionally, `shouldClearOnTimeout` causes timeout state to be cleared on event-time callbacks, losing the timeout timer entirely.
- **风险**: Every event-time timer (e.g., from `EventTimeTrigger` or `ContinuousEventTimeTrigger`) fires the window immediately, producing massive duplicate/wrong output. Timeout state is lost, defeating the timeout mechanism.
- **建议**: `onEventTime()` should return `triggerResult` directly (delegate to nested trigger), not promote to FIRE. Only `onProcessingTime()` should handle timeout-triggered FIRE.
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫 / 组合爆炸测试者（组合触发器的行为不一致）

---

### [AR-2] SlidingEventTimeWindows.assignWindows() `start + size` long overflow → corrupt TimeWindow

- **文件**: `nop-stream-core/.../windowing/assigners/SlidingEventTimeWindows.java:57`
- **证据片段**:
  ```java
  for (long start = lastStart;
       start > timestamp - size;
       start -= slide) {
      windows.add(new TimeWindow(start, start + size));
  }
  ```
- **严重程度**: P1
- **现状**: When `start` is near `Long.MAX_VALUE - size`, `start + size` overflows to negative, creating a `TimeWindow` with `end < start`. `TimeWindow.maxTimestamp()` returns `end - 1`, which underflows to `Long.MAX_VALUE`, making the window never fire (black hole accumulating elements forever). Same bug exists in `SlidingProcessingTimeWindows.java:57`, `TumblingEventTimeWindows.java:75`, `TumblingProcessingTimeWindows.java:42`. `EventTimeSessionWindows` correctly protects against this.
- **风险**: Windows with timestamps near `Long.MAX_VALUE` become infinite black holes, leaking memory and never producing output.
- **建议**: Add overflow guard: `long end = start + size; if (end < start) end = Long.MAX_VALUE;` or validate timestamp range in constructors.
- **信心水平**: 很可能（requires extreme timestamp values, but the cascading TimeWindow.maxTimestamp underflow is definite once overflow occurs）
- **发现来源视角**: 10x 规模运维者 / 异常路径侦探

---

### [AR-3] RemoteGraphExecutionPlanBuilder only uses first OperatorChain — multi-chain vertices broken

- **文件**: `nop-stream-runtime/.../transport/RemoteGraphExecutionPlanBuilder.java:151-153`
- **证据片段**:
  ```java
  for (int taskIndex = 0; taskIndex < parallelism; taskIndex++) {
      OperatorChain chain = taskIndex == 0
              ? original.getOperatorChains().get(0)
              : original.getOperatorChains().get(0).deepCopy();
  ```
- **严重程度**: P1
- **现状**: All subtasks always use `original.getOperatorChains().get(0)` — only the first chain. If a `JobVertex` has multiple operator chains (e.g., from chain-splitting optimization), the remaining chains are silently ignored. This makes distributed execution functionally incorrect for any vertex with multiple chains.
- **风险**: Silent data loss and incorrect execution for multi-chain vertices in distributed mode.
- **建议**: Distribute chains across subtasks or replicate all chains per subtask, depending on the intended execution model.
- **信心水平**: 确定
- **发现来源视角**: IoC 侦探（构建逻辑在结构上是不完整的）

---

### [AR-4] HeapInternalTimerService.advanceWatermark() reentrant timer registration during callback

- **文件**: `nop-stream-core/.../operators/HeapInternalTimerService.java:108-128`
- **证据片段**:
  ```java
  for (Map.Entry<Long, Set<TimerEntry<N>>> entry : toFire) {
      Set<TimerEntry<N>> originalSet = eventTimeTimers.get(entry.getKey());
      if (originalSet == null) continue;
      List<TimerEntry<N>> timersToFire = new ArrayList<>(originalSet);
      originalSet.removeAll(timersToFire);
      for (TimerEntry<N> timer : timersToFire) {
          triggerable.onEventTime(new HeapInternalTimer<>(timer.key, timer.timestamp, timer.namespace));
      }
      if (originalSet.isEmpty()) {
          eventTimeTimers.remove(entry.getKey());
      }
  }
  ```
- **严重程度**: P1
- **现状**: When `triggerable.onEventTime()` fires, the callback can register new timers or delete timers on the same `eventTimeTimers` TreeMap. Lines 114 re-fetches `originalSet` from the map — if a callback mutated the map (added/removed entries at keys in `toFire`), the re-fetched set may be stale, leading to missed timers or ConcurrentModificationException.
- **风险**: Timer callbacks that register new timers (common in CEP and continuous triggers) can corrupt timer state, leading to missed or duplicate timer firings.
- **建议**: Copy all timers to fire upfront, clear them from the map before firing callbacks, so reentrant mutations go into fresh map entries without affecting the current firing batch.
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探 / 事务边界追踪者

---

### [AR-5] StateShard.computeShardId() Math.abs(hashCode()) returns negative for Integer.MIN_VALUE

- **文件**: `nop-stream-core/.../state/shard/StateShard.java:67-72`
- **证据片段**:
  ```java
  public int computeShardId(Object key) {
      if (stateShardCount == 1) {
          return 0;
      }
      return Math.abs(stableHash(key)) % stateShardCount;
  }
  ```
- **严重程度**: P1
- **现状**: Same `Math.abs(MIN_VALUE)` bug as previously reported in `MemoryKeyedStateBackend.routeKey()`, but in a *different class*. When `key.hashCode()` returns `Integer.MIN_VALUE`, `Math.abs` returns `Integer.MIN_VALUE` (still negative), so the modulo result is negative, causing `ArrayIndexOutOfBoundsException` or incorrect state shard routing.
- **风险**: Keys whose `hashCode()` returns `Integer.MIN_VALUE` (1 in 2^32 probability) crash the state backend or silently route to wrong shard.
- **建议**: Use `(stableHash(key) & 0x7FFFFFFF) % stateShardCount` or `Math.floorMod()`.
- **信心水平**: 确定
- **发现来源视角**: 模型攻击者（与之前报告的错误模式相同，但位于不同的类中）

---

### [AR-6] PendingCheckpoint CompletableFuture never completed on happy path — latent deadlock

- **文件**: `nop-stream-runtime/.../checkpoint/PendingCheckpoint.java:113-129`
- **证据片段**:
  ```java
  public synchronized void acknowledgeTask(TaskLocation taskLocation, TaskStateSnapshot state) {
      if (isDisposed || status.get() != Status.RUNNING) {
          return;
      }
      notYetAcknowledgedTasks.remove(taskLocation);
      if (state != null) {
          taskStates.put(taskLocation, state);
      }
      if (isFullyAcknowledged() && !completableFuture.isDone()) {
          // The future is not auto-completed here. The coordinator completes it
          // after successful storage.
      }
  }
  ```
- **严重程度**: P1
- **现状**: When all tasks acknowledge, `isFullyAcknowledged()` becomes true but the `CompletableFuture` is NOT completed. Only external callers of `forceComplete()` resolve it. If used standalone (without a coordinator polling), the future hangs forever. The comment acknowledges this but the API contract is a trap.
- **风险**: Any code that calls `pendingCheckpoint.getCompletableFuture().get()` will deadlock unless a coordinator thread explicitly calls `forceComplete()`.
- **建议**: Auto-complete the future in `acknowledgeTask()` when `isFullyAcknowledged()` becomes true, or make the API unambiguous by removing the public future accessor.
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

### [AR-7] CheckpointPlanBuilder auto-detect uses vertexId instead of per-subtask participant ID

- **文件**: `nop-stream-runtime/.../checkpoint/CheckpointPlanBuilder.java:198-212`
- **证据片段**:
  ```java
  for (String vertexId : executionPlan.getSortedVertexIds()) {
      JobVertex vertex = executionPlan.getExecutionVertices().get(vertexId);
      for (OperatorChain chain : vertex.getOperatorChains()) {
          for (StreamOperator<?> op : chain.getOperators()) {
              if (op instanceof AbstractUdfStreamOperator) {
                  Object udf = ((AbstractUdfStreamOperator<?, ?>) op).getUserFunction();
                  if (udf instanceof TwoPhaseCommitSinkFunction) {
                      participantIds.add(vertexId);
                      break;
                  }
              }
          }
      }
  }
  ```
- **严重程度**: P1
- **现状**: When parallelism > 1, multiple subtasks share the same `vertexId` but each runs its own `TwoPhaseCommitSinkFunction` instance. Auto-detection adds `vertexId` once to `participantIds`, making 2PC commit tracking ambiguous — one subtask committing doesn't mean all have committed. Additionally, the inner `break` only exits the operators loop, not the chains loop, so `vertexId` can be added multiple times if a vertex has multiple chains containing 2PC sinks.
- **风险**: 2PC commit tracking is incorrect for parallel sinks — partial commits may be reported as fully committed, leading to data inconsistency.
- **建议**: Use per-subtask participant IDs (e.g., `vertexId + "-" + taskIndex`).
- **信心水平**: 很可能
- **发现来源视角**: 事务边界追踪者

---

### [AR-8] GeographicAnomalyPattern city2 filter early-exit loop — logic bug

- **文件**: `nop-stream-fraud-example/.../pattern/GeographicAnomalyPattern.java:89-98`
- **证据片段**:
  ```java
  for (TransactionEvent city1Event : ctx.getEventsForPattern("city1")) {
      if (!value.getUserId().equals(city1Event.getUserId())) {
          return false;
      }
      return !value.getCity().equals(city1Event.getCity());
  }
  ```
- **严重程度**: P1
- **现状**: The loop always exits after the first iteration: either via `return false` (userId mismatch) or via the city comparison return. If multiple `city1` matches exist (e.g., from non-keyed CEP evaluation), the filter only checks the first one. This produces both false negatives (misses valid geographic anomalies from later matches) and false positives (accepts cross-user matches). Other patterns in the same module (AccountTakeoverPattern, RapidTransactionPattern) correctly iterate to find matching users.
- **风险**: Geographic anomaly detection produces wrong alerts — both missed fraud and false alarms.
- **建议**: Restructure loop to find the matching userId first, then check city, or use a proper filtering pattern like the other patterns in the module.
- **信心水平**: 确定
- **发现来源视角**: 新人开发者

---

### [AR-9] InMemoryClusterRegistry has no eviction — unbounded memory growth

- **文件**: `nop-stream-runtime/.../cluster/InMemoryClusterRegistry.java:18-107` (entire class)
- **严重程度**: P1
- **证据片段**: No `unregisterNode()` method, no background eviction thread. `nodes`, `leaseTimestamps`, `taskAssignments` maps grow without bound.
- **现状**: Nodes are added via `registerNode()` but never removed. `getActiveNodes()` filters by lease freshness but doesn't clean up stale entries. In a long-running cluster with nodes joining and leaving, this is a memory leak. `taskAssignments` for failed/expired nodes are also never cleaned up.
- **风险**: Memory grows without bound in production clusters with node churn, eventually causing OOM.
- **建议**: Add periodic eviction of stale nodes (based on lease expiry) and clean up associated task assignments.
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

### [AR-10] Task.run() unconditional state.set(COMPLETED) — inconsistent state on close failure

- **文件**: `nop-stream-core/.../execution/Task.java:159-172`
- **证据片段**:
  ```java
  state.set(State.COMPLETED);     // line 160 — unconditional
  LOG.info("Task completed successfully: {}", getTaskName());
  } catch (Throwable t) {
      this.error = t;
      state.set(State.FAILED);
  } finally {
      closeOperatorChains();       // can set this.error at line 234
  }
  ```
- **严重程度**: P1
- **现状**: `state.set(State.COMPLETED)` is unconditional (not CAS). If `closeOperatorChains()` in the `finally` block throws, `this.error` is set but the state remains `COMPLETED`. Downstream code checking `getState() == COMPLETED` to decide success will see COMPLETED even though the close phase failed. Additionally, `cancel()` could race with completion.
- **风险**: Tasks with close failures appear successful to monitoring, hiding resource leaks or incomplete cleanup.
- **建议**: Use `state.compareAndSet(State.RUNNING, State.COMPLETED)`. Check `this.error` after close and transition to FAILED if close failed.
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-11] CollectionReplayableSource.run() non-atomic read-check-increment + seek() no bounds check

- **文件**: `nop-stream-runtime/.../source/CollectionReplayableSource.java:29-33`
- **证据片段**:
  ```java
  public void run(SourceContext<T> ctx) throws Exception {
      while (running && currentOffset < data.size()) {
          ctx.collect(data.get((int) currentOffset));
          currentOffset++;
      }
  }
  ```
- **严重程度**: P1
- **现状**: `currentOffset` is `volatile long` but `currentOffset++` is not atomic. If `seek()` is called concurrently (during checkpoint restore), `currentOffset` can be set to a value that makes `(int) currentOffset` exceed `data.size()`, causing `IndexOutOfBoundsException`. `seek()` has no bounds checking.
- **风险**: Concurrent checkpoint restore + source execution can crash the source with IndexOutOfBoundsException.
- **建议**: Use `AtomicLong` with `getAndIncrement()`, add bounds checking in `seek()`, or synchronize `run()` and `seek()`.
- **信心水平**: 很可能
- **发现来源视角**: 并发异常路径侦探

---

### [AR-12] BarrierAligner.checkComplete() only completes one checkpoint per call — progress stall

- **文件**: `nop-stream-runtime/.../checkpoint/barrier/BarrierAligner.java:65-95`
- **证据片段**:
  ```java
  private boolean checkComplete() {
      Long completedCheckpointId = findCompletedCheckpointId();
      if (completedCheckpointId == null) {
          return false;
      }
      for (TreeMap<Long, CheckpointBarrier> barriers : inputBarriers) {
          barriers.remove(completedCheckpointId);
      }
      return true;
  }
  ```
- **严重程度**: P2
- **现状**: Only one checkpoint is completed per `checkComplete()` call. If barriers for checkpoints 1, 2, 3 all arrive simultaneously, only checkpoint 1 is detected as complete. Checkpoints 2+ remain stuck until another barrier arrives. `processBarrier` only calls `checkComplete()` when a new barrier is inserted.
- **风险**: Progress stall under burst barrier scenarios. Completed checkpoints remain pending until the next barrier arrival.
- **建议**: Loop `checkComplete()` until it returns false, or track per-checkpoint arrival counts incrementally.
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

### [AR-13] ChainingOutput.collect() side output silently discarded — data loss

- **文件**: `nop-stream-core/.../operators/ChainingOutput.java:82-84`
- **证据片段**:
  ```java
  @Override
  public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
      LOG.warn("Side output '{}' discarded in simplified chaining mode.", outputTag);
  }
  ```
- **严重程度**: P2
- **现状**: All side-output records are silently discarded with only a LOG.warn. Operators that rely on side outputs for primary output (e.g., stream splitting) silently lose all data on those branches. No error, no exception.
- **风险**: Data loss for any operator using side outputs within a chained pipeline.
- **建议**: Either support side-output forwarding in chained mode, or throw an exception at pipeline construction time if side outputs are used with chaining.
- **信心水平**: 确定
- **发现来源视角**: 新人开发者

---

### [AR-14] RemoteInputChannel.read() decode error silently lost when queue has pending elements

- **文件**: `nop-stream-runtime/.../transport/RemoteInputChannel.java:115-125`
- **证据片段**:
  ```java
  public StreamElement read() throws InterruptedException {
      if (decodeError != null) {
          throw new StreamException(ERR_STREAM_STATE_ERROR, decodeError);
      }
      StreamElement element = queue.take();
      // ...
  }
  ```
- **严重程度**: P2
- **现状**: The decode error check runs before `queue.take()`. If a decode error occurs after the check passes, the reader blocks on `queue.take()`. Even when END_OF_STREAM is offered by the error handler, the reader returns `null` (end-of-stream) rather than throwing the decode error. The error is silently lost.
- **风险**: Decode errors in remote channels are silently swallowed, producing data loss without any diagnostic.
- **建议**: Check `decodeError` after `queue.take()` returns, or offer a special error sentinel in the queue.
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-15] RecordReader.read() swallows InterruptedException — cannot distinguish EOF from interruption

- **文件**: `nop-stream-core/.../execution/RecordReader.java:39-47`
- **证据片段**:
  ```java
  public Optional<StreamElement> read() {
      try {
          StreamElement element = channel.read();
          return Optional.ofNullable(element);
      } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return Optional.empty();
      }
  }
  ```
- **严重程度**: P2
- **现状**: Both end-of-stream (channel returns null) and interruption return `Optional.empty()`. The caller cannot distinguish between normal termination and cancellation, leading to silent data loss on interruption. Note: this is a different class from the previously reported `InputGate.readSingleChannel()` swallowing.
- **风险**: Task cancellation is misinterpreted as normal end-of-stream, causing silent data loss.
- **建议**: Propagate `InterruptedException` or return a distinguished sentinel value for interruption vs EOF.
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-16] TimestampedCollector reuses mutable StreamRecord — potential cross-element contamination

- **文件**: `nop-stream-core/.../operators/TimestampedCollector.java:43-56`
- **证据片段**:
  ```java
  private final StreamRecord<T> reuse;
  public void collect(T record) {
      output.collect(reuse.replace(record));
  }
  ```
- **严重程度**: P2
- **现状**: The `reuse` StreamRecord is a single mutable instance. `reuse.replace(record)` mutates it in-place. If a downstream operator stores StreamRecord references (not just values), all stored references point to the same mutated object showing the latest value.
- **风险**: Subtle data corruption if any downstream operator stores StreamRecord references rather than copying values.
- **建议**: Document the contract clearly, or copy the value in operators that store StreamRecords.
- **信心水平**: 很可能（已知 Flink 模式，但 Nop 中需要本地验证）
- **发现来源视角**: 异常路径侦探

---

### [AR-17] NFAState STATE_COMPARATOR uses hashCode() for comparison — violates Comparator contract

- **文件**: `nop-stream-cep/.../nfa/NFAState.java:115-119`
- **证据片段**:
  ```java
  private static final Comparator<ComputationState> STATE_COMPARATOR =
      Comparator.comparingInt((ComputationState c) -> c.getVersion().hashCode())
          .thenComparingLong(ComputationState::getStartTimestamp)
          .thenComparingLong(ComputationState::getPreviousTimestamp);
  ```
- **严重程度**: P2
- **现状**: The comparator uses `DeweyNumber.hashCode()` as a primary comparison key. Two different `DeweyNumber` instances can have the same `hashCode` but be non-equal, violating the comparator contract (`compare(a,b)==0` should imply `a.equals(b)`). This can cause `Arrays.sort` to throw `IllegalArgumentException` on JDK 7+ due to TimSort's contract enforcement, or silently produce wrong sort order.
- **风险**: NFAState.equals() may produce incorrect results in edge cases with hashCode collisions; potential TimSort crash.
- **建议**: Implement a proper `DeweyNumber.compareTo()` method and use it in the comparator.
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-18] Quantifier.Times hashCode/equals contract violation — Duration comparison inconsistency

- **文件**: `nop-stream-cep/.../pattern/Quantifier.java:230-250`
- **证据片段**:
  ```java
  // equals uses windowTime.toMillis():
  && windowTime.toMillis() == times.windowTime.toMillis()
  // hashCode uses Objects.hash(windowTime):
  return Objects.hash(from, to, windowTime);
  ```
- **严重程度**: P2
- **现状**: `equals` compares `windowTime` using `toMillis()` while `hashCode` uses `Objects.hash(windowTime)` which calls `Duration.hashCode()` (uses seconds + nanos separately). Two Durations equal in millis but differing in nanos (e.g., `Duration.ofMillis(1000)` vs `Duration.ofSeconds(1)`) are `equal` by `Times.equals` but have different `hashCode`, violating the contract.
- **风险**: `Times` objects can be lost in HashMaps/HashSets.
- **建议**: Use `Long.hashCode(windowTime.toMillis())` in `hashCode`.
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-19] CheckpointMetrics.snapshot() torn reads — inconsistent monitoring data

- **文件**: `nop-stream-runtime/.../checkpoint/metrics/CheckpointMetrics.java:94-104`
- **证据片段**:
  ```java
  public CheckpointMetricsSnapshot snapshot() {
      return new CheckpointMetricsSnapshot(
              numCompletedCheckpoints.get(),
              numFailedCheckpoints.get(),
              latestCheckpointSize.get(),
              latestCheckpointDuration.get(),
              totalStateSize.get(),
              lastCheckpointTimestamp.get()
      );
  }
  ```
- **严重程度**: P2
- **现状**: Each `AtomicLong.get()` is individually atomic but the snapshot as a whole is not consistent. A concurrent update can set `latestCheckpointSize` before the snapshot reads it but `latestCheckpointDuration` after, producing a snapshot where size and duration belong to different checkpoints.
- **风险**: Monitoring dashboards show inconsistent checkpoint metrics (e.g., size from checkpoint N, duration from checkpoint N-1).
- **建议**: Use synchronized snapshot or copy all values atomically.
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

### [AR-20] TimerServiceManager.advanceWatermark partial failure desynchronizes timer services

- **文件**: `nop-stream-core/.../operators/TimerServiceManager.java:27-31`
- **证据片段**:
  ```java
  public void advanceWatermark(Watermark mark) throws Exception {
      for (HeapInternalTimerService<?> service : timerServices) {
          service.advanceWatermark(mark.getTimestamp());
      }
  }
  ```
- **严重程度**: P2
- **现状**: If the first timer service's `advanceWatermark()` succeeds but the second throws, the first has fired timers while the second is left at the old watermark. No rollback. This creates permanent watermark desync between timer services.
- **风险**: Missed or duplicate timer firings across timer services after partial failure.
- **建议**: Batch timer firing or add rollback support.
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-21] InMemoryClusterRegistry.getNodeLease() always returns active=true — lease check is useless

- **文件**: `nop-stream-runtime/.../cluster/InMemoryClusterRegistry.java:63-69`
- **证据片段**:
  ```java
  public LeaseInfo getNodeLease(String nodeId) {
      Long timestamp = leaseTimestamps.get(nodeId);
      if (timestamp == null) return null;
      return new LeaseInfo(nodeId, timestamp, timestamp + LEASE_TIMEOUT_MS, true);
  }
  ```
- **严重程度**: P2
- **现状**: The `active` field is hardcoded to `true` regardless of lease expiry. Compare with `getActiveNodes()` which correctly checks `(now - leaseTime) < LEASE_TIMEOUT_MS`. Any code using `getNodeLease().isActive()` will always see `true`.
- **风险**: Lease-dependent logic (e.g., task assignment, failover) may route to expired nodes.
- **建议**: Compute `active` based on current time vs lease timestamp + timeout, consistent with `getActiveNodes()`.
- **信心水平**: 确定
- **发现来源视角**: IoC 侦探

---

### [AR-22] CepPatternBuilder double-optional crash when times(from=0) + isOptional=true

- **文件**: `nop-stream-cep/.../model/builder/CepPatternBuilder.java:155-192`
- **证据片段**:
  ```java
  if (partModel.getTimesFrom() == 0 || partModel.getTimesFrom() > 1) {
      // times(0, to, windowTime) already sets OPTIONAL internally
      pattern = pattern.times(partModel.getTimesFrom(), partModel.getTimesTo(), windowTime);
  }
  // ...
  if (partModel.isOptional()) {
      pattern = pattern.optional();   // crashes if OPTIONAL already set by times(0,...)
  }
  ```
- **严重程度**: P2
- **现状**: `Pattern.times(0, to, windowTime)` internally sets the quantifier to OPTIONAL. If the model also has `isOptional() == true`, `pattern.optional()` is called again, throwing `MalformedPatternException("Optional already applied!")`. The declarative model allows this combination.
- **风险**: Declarative CEP patterns with `timesFrom=0` and `isOptional=true` crash at build time with a confusing error.
- **建议**: Check if quantifier already has OPTIONAL before calling `optional()`.
- **信心水平**: 很可能
- **发现来源视角**: XDSL 语义侦探

---

### [AR-23] Quantifier.checkPattern() discards errorMessage — unhelpful pattern validation errors

- **文件**: `nop-stream-cep/.../pattern/Quantifier.java:85-89`
- **证据片段**:
  ```java
  private static void checkPattern(boolean condition, Object errorMessage) {
      if (!condition) {
          throw new MalformedPatternException(ERR_CEP_MALFORMED_PATTERN);
      }
  }
  ```
- **严重程度**: P3
- **现状**: All callers pass descriptive error messages but `checkPattern` ignores the `errorMessage` parameter. Users only see "Malformed CEP pattern" without context.
- **风险**: Makes debugging pattern errors significantly harder.
- **建议**: Pass `errorMessage` as `.param(ARG_PATTERN_DETAIL, errorMessage)` to the exception.
- **信心水平**: 确定
- **发现来源视角**: 新人开发者

---

### [AR-24] NFAStateNameHandler colon delimiter collision — pattern names containing `:` break name resolution

- **文件**: `nop-stream-cep/.../nfa/compiler/NFAStateNameHandler.java:49,80-88`
- **证据片段**:
  ```java
  // getOriginalNameFromInternal splits on ":"
  // getUniqueInternalName uses ":" as STATE_NAME_DELIM
  ```
- **严重程度**: P3
- **现状**: If a user provides a pattern name containing `:` (e.g., `"my:pattern"`), `getOriginalNameFromInternal` splits on `:` and returns only `"my"` instead of `"my:pattern"`. This causes wrong state lookups in skip-to strategies that reference pattern names.
- **风险**: Pattern names with `:` cause incorrect skip-to-strategy behavior or NPE.
- **建议**: Use a delimiter unlikely to appear in user names (e.g., `\0` or `$$`) or escape the delimiter.
- **信心水平**: 很可能
- **发现来源视角**: 模型攻击者

---

### [AR-25] CountTrigger accepts maxCount <= 0 — degenerate fire loop

- **文件**: `nop-stream-core/.../windowing/triggers/CountTrigger.java:39-41,47-48`
- **证据片段**:
  ```java
  private CountTrigger(long maxCount) {
      this.maxCount = maxCount;    // no validation
  }
  // in onElement:
  if (count.get() >= maxCount) {   // maxCount==0: always true
  ```
- **严重程度**: P3
- **现状**: No validation on `maxCount`. If `maxCount == 0`, every element fires immediately then clears, causing continuous fire-and-reset with no accumulation. If `maxCount < 0`, same behavior.
- **风险**: Degenerate window behavior with no useful output.
- **建议**: Validate `maxCount > 0` in constructor.
- **信心水平**: 确定
- **发现来源视角**: 新人开发者

---

### [AR-26] SubtaskTask.isFinished() returns true for CANCELING — premature termination signal

- **文件**: `nop-stream-core/.../execution/SubtaskTask.java:127-130`
- **证据片段**:
  ```java
  public boolean isFinished() {
      State s = state.get();
      return s == State.COMPLETED || s == State.FAILED || s == State.CANCELED || s == State.CANCELING;
  }
  ```
- **严重程度**: P2
- **现状**: `CANCELING` is a transitional state (thread still active). Including it in `isFinished()` causes premature cleanup of shared resources while the task is still processing its final elements. Compare with `Task.isFinished()` which correctly only checks terminal states.
- **风险**: Premature resource cleanup while task is still running.
- **建议**: Remove `CANCELING` from `isFinished()`.
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-27] InMemoryClusterRegistry.renewLease() TOCTOU — orphaned lease entries

- **文件**: `nop-stream-runtime/.../cluster/InMemoryClusterRegistry.java:54-59`
- **证据片段**:
  ```java
  public boolean renewLease(String nodeId, long leaseTimeoutMs) {
      if (!nodes.containsKey(nodeId)) {
          return false;
      }
      leaseTimestamps.put(nodeId, System.currentTimeMillis());
      return true;
  }
  ```
- **严重程度**: P2
- **现状**: `containsKey` + `put` is not atomic. Between the check and the put, another thread can `unregister` the node, leaving a stale lease timestamp with no corresponding node entry. Over time, orphaned lease entries accumulate.
- **风险**: Memory leak from orphaned lease entries.
- **建议**: Use `computeIfPresent` or synchronize.
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-28] WindowOperatorTimerService uses PriorityQueue.contains() O(n) per timer registration

- **文件**: `nop-stream-runtime/.../operators/WindowOperatorTimerService.java:75-81`
- **证据片段**:
  ```java
  public void registerEventTimeTimer(N namespace, long time) {
      InternalTimer<K, N> timer = new SimpleInternalTimer<>(time, key, namespace);
      if (!eventTimeTimers.contains(timer)) {   // O(n)
          eventTimeTimers.add(timer);
      }
  }
  ```
- **严重程度**: P3
- **现状**: `PriorityQueue.contains()` is O(n). For high-throughput window operators registering many timers, this degrades to O(n) per registration, creating quadratic behavior.
- **风险**: Performance degradation for high-cardinality window operations.
- **建议**: Add a `HashSet` alongside the priority queue for O(1) dedup.
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

## 总评

这次审查在经历了 ~16 轮审计之后，仍然发现了 28 个新问题。我认为 nop-stream 当前最值得关注的 3 个方向是：

1. **分布式执行路径的成熟度严重不足**: `RemoteGraphExecutionPlanBuilder` 只使用第一个 OperatorChain (AR-3)、`executionVertices` 只存储 subtask 0 (AR-18)、`CheckpointPlanBuilder` 使用 vertexId 而非 per-subtask ID (AR-7)。这表明分布式执行模式可能从未被端到端测试过。

2. **Timer 和 Window 子系统存在组合错误**: `ProcessingTimeoutTrigger.onEventTime()` 的 copy-paste 错误 (AR-1) 是一个确定性 bug；`HeapInternalTimerService` 的重入问题 (AR-4) 在 CEP + windowing 组合场景下会触发。`SlidingEventTimeWindows` 的溢出 (AR-2) 虽然需要极端值，但其级联的 `TimeWindow.maxTimestamp()` 下溢后果严重。

3. **CEP 声明式模型层（CepPatternBuilder）的质量门控不足**: 双重 optional crash (AR-22)、错误信息被丢弃 (AR-23)、名称分隔符冲突 (AR-24) 都指向同一个根因——model→Pattern 的转换路径缺乏充分的验证和边界情况测试。

## 本次审查的盲区自评

- **序列化正确性**: 未验证 checkpoint 序列化/反序列化的端到端正确性（特别是复杂 state 类型的 round-trip）。
- **并发压力测试**: 未验证 BarrierAligner、CheckpointCoordinator 在高频 checkpoint 触发下的并发行为。
- **Flink 兼容层**: `nop-stream-flink` 模块为空，未评估。
- **流控和背压**: `FlowControlPolicy` 和 `MemoryBudget` 的实际效果未验证。
- **端到端集成**: 未验证从 StreamModel → GraphModelCheckpointExecutor → 分布式执行 → checkpoint restore 的完整链路。

## 严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 9    | 触发器语义(1), 窗口溢出(1), 分布式执行(2), Timer重入(1), 状态路由(1), Checkpoint(2), 并发源(1) |
| P2      | 13   | 数据丢失(3), 一致性(4), 性能(2), 泄漏(2), API陷阱(2) |
| P3      | 6    | 验证缺失(3), 封装问题(1), 命名冲突(1), 性能(1) |
