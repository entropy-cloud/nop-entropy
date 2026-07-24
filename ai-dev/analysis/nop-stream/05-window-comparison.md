# 窗口机制 & 时间模型源码级对比分析

> **Status**: resolved
> **Created**: 2026-07-24
> **Plan**: `docs/plans/nop-stream-flink-comparison/2026-07-24-1000-3-window-time-comparison.md`
> **Prerequisite Plans**: 316 (Flink source audit) + 317 (nop-stream live audit) — both active, deliverables absent; supplemented by direct source reading per plan guardrails and precedent from items 3 & 4

---

## 1. WindowOperator Execution Path

### 1.1 Flink WindowOperator

**File:** `flink-runtime/src/main/java/org/apache/flink/streaming/runtime/operators/windowing/WindowOperator.java`

**Hierarchy:**
```
AbstractStreamOperator<OUT>
  └── AbstractUdfStreamOperator<OUT, InternalWindowFunction<ACC, OUT, K, W>>
        └── WindowOperator<K, IN, ACC, OUT, W extends Window>
              implements OneInputStreamOperator<IN, OUT>, Triggerable<K, W>
```

**`processElement()` flow (lines 293-447):**

1. **Window assignment** (line 294-296): `windowAssigner.assignWindows(element.getValue(), element.getTimestamp(), windowAssignerContext)` — returns `Collection<W>`. Each element may belong to multiple windows (e.g., sliding windows).

2. **Branch: merging vs. regular** (lines 303-403):
   - **Merging path** (lines 303-403): `MergingWindowSet.addWindow()` triggers merge callbacks → updates `mergingSetsState` → calls `trigger.onMerge()` → merges state via `windowMergingState.mergeNamespaces()` → persists window mapping.
   - **Non-merging path** (lines 404-433): Tumbling/sliding: assign → lateness check → windowState → trigger → fire/purge → cleanup timer.

3. **Trigger invocation** (line 384): `triggerContext.onElement(element)` → `trigger.onElement(element.getValue(), element.getTimestamp(), window, triggerContext)`.

4. **TriggerResult handling**: `isFire()` → `emitWindowContents()`; `isPurge()` → `windowState.clear()`.

5. **State management**: `windowState` (`InternalAppendingState<K, W, IN, ACC, ACC>`) stores per-(key, window) accumulators. `windowMergingState` (`InternalListState<K, VoidNamespace, Tuple2<W, W>>`) stores window-to-state-window mappings for merging windows.

6. **Cleanup** (line 399): `registerCleanupTimer(window)` at `cleanupTime = window.maxTimestamp() + allowedLateness`.

7. **Late data** (lines 440-446): If `isElementLate()` and `lateDataOutputTag != null`, side-output; else increment `numLateRecordsDropped`.

**`emitWindowContents()` (lines 575-580):** Sets `timestampedCollector.setAbsoluteTimestamp(window.maxTimestamp())`, calls `userFunction.process(triggerContext.key, window, processContext, contents, timestampedCollector)`.

**Timer-based triggers:** `onEventTime()` (lines 450-494) and `onProcessingTime()` (lines 497-541) — both resolve merging window state window, call trigger, fire/purge, handle cleanup timer.

### 1.2 nop-stream WindowOperator

**File:** `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java` (1765 lines)

**Hierarchy:** Identical structure — `WindowOperator<K, IN, ACC, OUT, W extends Window>` extends `AbstractUdfStreamOperator` implements `OneInputStreamOperator`, `Triggerable<K, W>`.

**`processElement()` flow:**

1. **Window assignment** — `windowAssigner.assignWindows(element.getValue(), element.getTimestamp(), windowAssignerContext)` → `Collection<W>`.

2. **Key extraction** — `keySelector.getKey(element.getValue())`, `setCurrentKey(key)`.

3. **Branch: merging vs. regular** — `processElementForMergingWindow()` vs `processElementForRegularWindow()`.

4. **State management**: `windowState` (`InternalAppendingState<K, W, IN, ACC, ACC>`), `newListWindowState` (`InternalListState<K, W, IN>`), `mergingSetsState` (`InternalListState<K, VoidNamespace, Tuple2<W, W>>`).

5. **Merging flow details** (processElementForMergingWindow): Gets `MergingWindowSet`, calls `addWindow(window, mergeFunction)`. Merge function callback: checks watermark validity, calls `triggerContext.onMerge(mergedWindows)`, clears merged state, calls `mergeWindowContents(key, stateWindowResult, mergedStateWindows)`. After merge resolution: `addWindowElement()`, fire/purge check, `mergingWindows.persist()`.

6. **TriggerResult handling**: Same `FIRE`/`FIRE_AND_PURGE`/`PURGE`/`CONTINUE` enum.

7. **Cleanup**: `registerCleanupTimer(window)` at `cleanupTime(window)`.

8. **Late data**: If `isElementLate()` and `lateDataOutputTag != null`, `sideOutput(element)`.

### 1.3 Comparison: Match / Gap

| Aspect | Flink | nop-stream | Classification |
|--------|-------|------------|----------------|
| Class hierarchy | WindowOperator → AbstractUdfStreamOperator → AbstractStreamOperator | Identical | Match |
| processElement flow | assign → merge/regular → state → trigger → fire/purge → cleanup | Assign → merge/regular → state → trigger → fire/purge → cleanup | Match |
| State types | InternalAppendingState for aggregate/reduce; InternalListState for apply/process | Same pair | Match |
| Window assigner integration | windowAssigner.assignWindows() → Collection<W> | Identical | Match |
| Merging window branch | Inline in processElement (lines 303-403) | Separate method processElementForMergingWindow | Match (refactored) |
| Evictor support | EvictingWindowOperator extends WindowOperator | Evictor called in emitWindowContents() but `evictAfter()` not called | Gap (Minor) |
| AccumulationMode | Wired into runtime behavior | Enum exists but not wired | Gap (Minor) |
| Reflective factory loading | Direct class construction | `WindowedStreamImpl.getFactory()` uses reflection to load `WindowOperatorFactoryImpl` from nop-stream-runtime | Improvement (design choice) |

---

## 2. MergingWindowSet / Session Window

### 2.1 Flink MergingWindowSet

**File:** `flink-runtime/src/main/java/org/apache/flink/streaming/runtime/operators/windowing/MergingWindowSet.java` (254 lines)

**Data structure:**
- `Map<W, W> mapping` — in-flight window → state window (the window whose namespace stores accumulated state)
- `Map<W, W> initialMapping` — snapshot for `persist()` dirty-checking
- `ListState<Tuple2<W, W>> state` — persisted state

**`addWindow(W newWindow, MergeFunction<W> mergeFunction)` (lines 153-224):**
1. Collects all existing windows + new window.
2. Calls `windowAssigner.mergeWindows(windows, mergeCallback)` to determine overlapping groups.
3. For each merge result: picks first merged window's state window as result's state window (line 190), removes merged windows, inserts merge result mapping, calls `mergeFunction.merge()`.
4. Merge function handles: `trigger.onMerge(result)`, `trigger.clear(merged)`, `deleteCleanupTimer(merged)`, `windowMergingState.mergeNamespaces(stateWindowResult, mergedStateWindows)`.
5. No-merge case: maps new window to itself.

**`persist()` (lines 99-106):** Only writes when `mapping != initialMapping` — write-avoidance optimization.

**`StateWindow` concept:** Multiple in-flight windows can map to the same state window after merge. This avoids costly state data movement during merge.

### 2.2 nop-stream MergingWindowSet

**File:** `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/MergingWindowSet.java` (same structure)

**Same structure:** `Map<W, W> mapping`, `Map<W, W> initialMapping`, `ListState<Tuple2<W, W>> state`.

**Same algorithm:** `addWindow()` → `windowAssigner.mergeWindows()` → merge callback → merge function → state merge.

**Merge function in WindowOperator** (lines 1094-1134): Checks watermark/processing-time validity, calls `triggerContext.onMerge(mergedWindows)`, clears merged windows' state via `windowState.setCurrentNamespace(stateWindow).clear()`, calls `mergeWindowContents()` which clears + re-adds accumulator.

### 2.3 Session Window Merge Bug (Plan 303 follow-up)

**Disabled tests (4 total):**

| Test | File | Annotation |
|------|------|------------|
| `testLateElementCausesTwoEstablishedSessionsToMerge` | `TestSessionWindowAdvancedMerge.java` | `@Disabled("WindowOperator session window merge not yet compatible with EventTimeSessionWindows")` |
| `testThreeWayMerge` | `TestSessionWindowAdvancedMerge.java` | Same |
| `testMergedSessionTriggersOnWatermarkAdvance` | `TestSessionWindowAdvancedMerge.java` | Same |
| `testMultiKeyIndependentSessions` | `TestSessionWindowWithPeriodicWatermark.java` | `@Disabled("WindowOperator session window multi-key merge not yet compatible with EventTimeSessionWindows")` |

**Root cause:** `mergeWindowContents()` (WindowOperator.java:1128-1134) throws `ERR_STREAM_STATE_ERROR` with detail "Failed to set merged accumulator" when `AggregatingState` merge fails. The method clears the state window and re-adds the merged accumulator, but this can fail when the accumulator schema doesn't support clear+add (e.g., `AggregatingState` requires `merge(N, N) → N` semantics, not `clear()` → `add(merged)`).

**Enabled tests in TestSessionWindowWithPeriodicWatermark:** `testSingleElementSession()`, `testAdjacentEventsMerge()`, `testSessionWindowStateMigrationOnMerge()` PASS — so basic session window functionality works.

**Classification:** **Bug/P0** (correctness blocking) — functional regression. Session window merge for advanced scenarios (3-way merge, multi-key independent sessions, periodic watermark triggers) is disabled. This represents a real correctness issue that blocks production use of session windows with merging. See gap table entry G1.

### 2.4 Comparison: Match / Gap

| Aspect | Flink | nop-stream | Classification |
|--------|-------|------------|----------------|
| MergingWindowSet structure | Map<W,W> mapping + initialMapping + ListState | Identical | Match |
| addWindow() algorithm | Overlap detection → merge callback → mapping update | Identical | Match |
| persist() dirty-check | mapping != initialMapping | Identical | Match |
| MergeFunction callbacks | trigger.onMerge, trigger.clear, deleteCleanupTimer, state.mergeNamespaces | Same: triggerContext.onMerge, state.clear, mergeWindowContents | Match |
| Session window merge bug | Full support (all tests pass in Flink 1.20) | 4 disabled tests — "Failed to set merged accumulator" | **Bug/P1** |
| mergeWindowContents() | Uses state.mergeNamespaces() for clean merge | Uses clear() + add() — fails for AggregatingState | **Bug/P1 root cause** |

---

## 3. Pane Semantics

### 3.1 Flink Pane Management

Flink does NOT have an explicit `PaneState` class. Pane data is implicit in the per-(key, window) namespace of `windowState`.

**`TriggerResult` enum (4 values):**
- `CONTINUE` (no fire, no purge) — accumulate
- `FIRE` (fire, no purge) — evaluate window function, retain state
- `FIRE_AND_PURGE` (fire and purge) — evaluate + clear state
- `PURGE` (no fire, purge) — clear state, no evaluation

**allowedLateness contract:**
- **Element admission**: Element is late if `element.timestamp + allowedLateness <= currentWatermark` (WindowOperator.java:620-624)
- **Window retention**: Cleanup timer fires at `window.maxTimestamp() + allowedLateness` (WindowOperator.java:670-677)
- **Cleanup lifecycle**: `registerCleanupTimer()` → `isCleanupTime()` → `clearAllState()` (clears windowState + trigger state + process context + retires from MergingWindowSet)

**Pane stages (emergent, no explicit tracking):**
- **On-time**: Trigger fires when watermark passes window's `maxTimestamp` (before `maxTimestamp + allowedLateness`)
- **Late/Allowed**: Elements between `maxTimestamp` and `maxTimestamp + allowedLateness` — still admitted, can trigger late firings
- **Dropped**: Elements arriving after `maxTimestamp + allowedLateness` — dropped or side-output

**`PurgingTrigger`**: Wraps any trigger, converting `FIRE` to `FIRE_AND_PURGE`.

### 3.2 nop-stream Pane Management

**PaneInfo/PaneState data classes exist but are NOT wired:**

- `PaneInfo` (`nop-stream-core/src/main/java/io/nop/stream/core/windowing/PaneInfo.java`): Fields `index`, `isFirst`/`isLast`, `timing` (EARLY/ON_TIME/LATE). `@DataBean` annotated.
- `PaneState` (`nop-stream-core/src/main/java/io/nop/stream/core/windowing/PaneState.java`): Fields `paneInfo`, `window`, `timestamp`, `state`. `@DataBean` annotated.
- **Not used by WindowOperator runtime** — exist as data model / future-API classes only.

**TriggerResult:** Same enum — `CONTINUE`, `FIRE`, `FIRE_AND_PURGE`, `PURGE`.

**Allowed lateness:**
- `isElementLate()` (WindowOperator.java:796): `element.getTimestamp() + allowedLateness <= internalTimerService.currentWatermark()`
- `isWindowLate()` (WindowOperator.java:785): `cleanupTime(window) <= internalTimerService.currentWatermark()`
- `cleanupTime()` (WindowOperator.java:846): `window.maxTimestamp() + allowedLateness` (event time); `window.maxTimestamp()` (processing time)

**AccumulationMode:** Enum exists (`DISCARDING`, `ACCUMULATING`, `ACCUMULATING_AND_RETRACTING` in `WindowingStrategy.java:10`) but **not wired** into `WindowOperator` runtime logic.

**PurgingTrigger:** Same wrapper pattern.

### 3.3 Comparison: Match / Gap

| Aspect | Flink | nop-stream | Classification |
|--------|-------|------------|----------------|
| TriggerResult enum | CONTINUE/FIRE/FIRE_AND_PURGE/PURGE | Identical | Match |
| allowedLateness detection | isElementLate() + isWindowLate() | Identical logic | Match |
| cleanupTime formula | maxTimestamp + allowedLateness | Identical | Match |
| Late data side-output | OutputTag-based | Same pattern | Match |
| PaneInfo/PaneState | No explicit pane state | Data model classes exist but NOT wired | **Hollow** — interfaces exist but unused |
| AccumulationMode | Wired into WindowOperator runtime | Enum exists, NOT wired | **Gap (Minor)** |
| early/on-time/late tracking | Emergent from watermark + trigger | No explicit tracking despite PaneTiming enum | **Gap** |
| PurgingTrigger | Wraps trigger, FIRE→FIRE_AND_PURGE | Identical | Match |

---

## 4. InternalTimerService

### 4.1 Flink InternalTimerService

**Interface:** `flink-runtime/src/main/java/org/apache/flink/streaming/api/operators/InternalTimerService.java`

Methods: `registerProcessingTimeTimer(N)`, `deleteProcessingTimeTimer(N)`, `registerEventTimeTimer(N)`, `deleteEventTimeTimer(N)`, `forEachEventTimeTimer()`, `forEachProcessingTimeTimer()`, `currentProcessingTime()`, `currentWatermark()`.

**Implementation:** `InternalTimerServiceImpl` (`flink-runtime/src/main/java/org/apache/flink/streaming/api/operators/InternalTimerServiceImpl.java`, 471 lines)

**Data structures:**
- `KeyGroupedInternalPriorityQueue<TimerHeapInternalTimer<K, N>> processingTimeTimersQueue`
- `KeyGroupedInternalPriorityQueue<TimerHeapInternalTimer<K, N>> eventTimeTimersQueue`
- Both backed by `HeapPriorityQueueSet`, key-grouped for efficient checkpoint/restore.

**Timer registration:**
- **Processing time** (lines 233-246): Inserts into priority queue. If new timer is earlier than current head, cancels old ScheduledFuture and registers new one with `processingTimeService.registerTimer(time, this::onProcessingTime)`.
- **Event time** (lines 249-252): Simply inserts into priority queue. No external scheduling — triggered by watermark advancement.

**Timer firing:**
- **`onProcessingTime()`** (lines 291-312): Pops all timers ≤ current time, sets key context, delegates to `triggerTarget.onProcessingTime(timer)`, re-registers next timer if timers remain.
- **`advanceWatermark()`** (lines 314-322): Pops event time timers ≤ watermark, sets key context, delegates to `triggerTarget.onEventTime(timer)`.

**Checkpoint/restore:**
- `snapshotTimersForKeyGroup(keyGroupIdx)` (lines 356-362): Returns `InternalTimersSnapshot` with key/namespace serializer, event time, and processing time timer subsets per key group.
- `restoreTimersForKeyGroup()` (lines 380-410): Adds to both queues.

**TimerHeapInternalTimer:** `flink-runtime/.../operators/TimerHeapInternalTimer.java` — implements `HeapPriorityQueueElement` with `timerHeapIndex` for O(log n) heap removal.

**Management layer:** `InternalTimeServiceManagerImpl` holds `Map<String, InternalTimerServiceImpl<K, ?>>`, creates/retrieves named timer services, `advanceWatermark()` iterates all services, `snapshotToRawKeyedState()` writes per-key-group timer snapshots.

### 4.2 nop-stream InternalTimerService

**Interface:** `nop-stream-core/src/main/java/io/nop/stream/core/operators/InternalTimerService.java`

Same methods as Flink's interface — `registerProcessingTimeTimer`, `deleteProcessingTimeTimer`, `registerEventTimeTimer`, `deleteEventTimeTimer`, `forEachEventTimeTimer`, `forEachProcessingTimeTimer`, `currentProcessingTime`, `currentWatermark`.

**Two parallel implementations:**

#### A) `HeapInternalTimerService` (`nop-stream-core/.../operators/HeapInternalTimerService.java`)

- Uses `TreeMap<Long, Set<TimerEntry<N>>>` for both event-time and processing-time timers.
- `advanceWatermark(long newWatermark)`: Polls firstEntry while `entry.key <= newWatermark`, fires via `triggerable.onEventTime(...)`.
- `fireProcessingTimeTimers(long timestamp)`: Same pattern.
- **No checkpoint/restore support.**
- Managed by `TimerServiceManager` — used by non-window operators.

#### B) `WindowOperatorTimerService` (`nop-stream-runtime/.../operators/WindowOperatorTimerService.java`)

- Uses `PriorityQueue<InternalTimer<K, N>>` for both event-time and processing-time timers, ordered by `Comparator.comparingLong(InternalTimer::getTimestamp)`.
- `advanceWatermark(long watermark)`: Sets `currentWatermark`, polls timers with `timestamp <= watermark`.
- `advanceProcessingTime(long timestamp)`: Same pattern for processing time.
- **Deduplication:** `register*` methods check `!eventTimeTimers.contains(timer)` before adding.
- **No checkpoint/restore support.**
- **Plan 303 fix:** `advanceProcessingTime()` method exists explicitly — confirms processing-time timer body was previously a no-op and is now fixed.

### 4.3 Comparison: Match / Gap

| Aspect | Flink | nop-stream | Classification |
|--------|-------|------------|----------------|
| InternalTimerService interface | 8 methods | Identical | Match |
| Timer data structure | KeyGroupedInternalPriorityQueue | TreeMap (HeapInternalService) / PriorityQueue (WindowOperatorTimerService) | Match (different impl) |
| Processing time timer scheduling | processingTimeService.registerTimer() | Same pattern (WindowOperatorTimerService), TreeMap (HeapInternal) | Match |
| Event time timer trigger | advanceWatermark() pops ≤ watermark | Same pattern | Match |
| Key context setting | explicit setCurrentKey before firing | Same pattern | Match |
| Two parallel implementations | Single InternalTimerServiceImpl | HeapInternalTimerService vs WindowOperatorTimerService | **Gap (Architecture)** — unnecessary duplication, two code paths |
| Timer checkpoint/restore | Full: snapshotTimersForKeyGroup + restoreTimersForKeyGroup | **None** — both implementations are purely in-memory | **Bug (data loss risk)** |
| Timer namespace management | InternalTimeServiceManagerImpl manages map of named services | TimerServiceManager manages List<HeapInternalTimerService> | Gap |
| Processing time timer | Fully implemented | Fixed in Plan 303 (previously no-op) | **Was Hollow, now fixed** |
| Deduplication | PriorityQueue handles naturally | WindowOperatorTimerService: contains() check before add | Improvement |
| TimerHeapInternalTimer | Implements HeapPriorityQueueElement for O(log n) | InternalTimer extends PriorityComparable | Match (different heap) |

---

## 5. Watermark Generation & Propagation

### 5.1 Flink Watermark

**WatermarkStrategy** (`flink-core/.../api/common/eventtime/WatermarkStrategy.java`):
- Interface extends `TimestampAssignerSupplier<T>` and `WatermarkGeneratorSupplier<T>`.
- Builder methods: `withTimestampAssigner()`, `withIdleness(Duration)`, `withWatermarkAlignment(group, maxDrift)`.
- Static convenience: `forMonotonousTimestamps()`, `forBoundedOutOfOrderness(Duration)`, `noWatermarks()`.

**WatermarkGenerator** (`flink-core/.../api/common/eventtime/WatermarkGenerator.java`):
- `onEvent(T event, long eventTimestamp, WatermarkOutput output)` — per-element.
- `onPeriodicEmit(WatermarkOutput output)` — periodic.

**TimestampsAndWatermarksOperator** (`flink-streaming-java/.../operators/TimestampsAndWatermarksOperator.java`):
- **Auto-inserted** by transformation pipeline when `.assignTimestampsAndWatermarks()` is called.
- `open()` (lines 86-120): Creates timestamp assigner + watermark generator. Reads `watermarkInterval = getExecutionConfig().getAutoWatermarkInterval()`. If >0, schedules periodic timer.
- `processElement()` (lines 133-142): Extracts timestamp, forwards element, calls `watermarkGenerator.onEvent()`.
- `onProcessingTime()` (lines 145-150): Calls `watermarkGenerator.onPeriodicEmit()`, re-registers timer.
- `WatermarkEmitter` inner class (lines 182-225): Skip non-increasing, mark idle/active.

**Multi-input alignment:**
- `CombinedWatermarkStatus` (`flink-core/.../api/common/eventtime/CombinedWatermarkStatus.java`): Combined = min of all active inputs' watermarks. If all inputs idle, combined = max across all.
- `IndexedCombinedWatermarkStatus`: For binary operators.
- `StatusWatermarkValve` (`flink-runtime/.../watermarkstatus/StatusWatermarkValve.java`, 422 lines): Per-subpartition status tracking. Min-watermark semantics via heap. Idle subpartitions removed from alignment.

**Idle detection:** `WatermarkStrategy.withIdleness(Duration)` wraps generator → emits `WatermarkStatus.IDLE` when no data for timeout period.

### 5.2 nop-stream Watermark

**WatermarkStrategy** (`nop-stream-core/.../common/eventtime/WatermarkStrategy.java`):
- Same interface — extends both `TimestampAssignerSupplier<T>` and `WatermarkGeneratorSupplier<T>`.
- Same static convenience methods: `forMonotonousTimestamps()`, `forBoundedOutOfOrderness(Duration)`, `noWatermarks()`.
- Same builder methods: `withTimestampAssigner()`, `withIdleness(Duration)`, `withWatermarkAlignment()`.

**WatermarkGenerator** (`nop-stream-core/.../common/eventtime/WatermarkGenerator.java`):
- Same interface: `onEvent()` + `onPeriodicEmit()`.

**TimestampsAndWatermarksOperator** (`nop-stream-core/.../operators/TimestampsAndWatermarksOperator.java`):
- Same structure: holds `watermarkStrategy`, creates `timestampAssigner` + `watermarkGenerator` in `open()`.
- `watermarkInterval` defaults to `200L` (NOT hardcoded to 0 — contrary to roadmap assumption).
- `processElement()` (line 84): Extracts timestamp, forwards element, calls `watermarkGenerator.onEvent()`.
- **Periodic emission** (line 68): If `watermarkInterval > 0`, schedules processing-time timer. Callback calls `watermarkGenerator.onPeriodicEmit()` and re-schedules.
- `processWatermark()` (line 118): Forwards if > `lastWatermarkTimestamp`.
- `finish()` (line 127): Cancels timer, final `onPeriodicEmit()`, emits `Watermark.MAX_WATERMARK`.
- `OperatorWatermarkOutput` inner class: `emitWatermark()`, `markIdle()`, `markActive()`.

**Auto-insertion**: `DataStreamImpl.assignTimestampsAndWatermarks()` (line 210-221) creates `TimestampsAndWatermarksTransformation` with `watermarkInterval` from `environment.getWatermarkInterval()`. **Operator IS auto-inserted** in the transformation pipeline when user calls `.assignTimestampsAndWatermarks()`. However, source-level watermarks (from `SourceFunction`) are NOT automatically assigned — the error messages in `TumblingEventTimeWindows` and `SlidingEventTimeWindows` remind users to call `.assignTimestampsAndWatermarks()`.

**Multi-input alignment:**
- `CombinedWatermarkStatus` (`nop-stream-core/.../common/eventtime/CombinedWatermarkStatus.java`): Same logic — combined = min of active inputs, idle = max.
- `IndexedCombinedWatermarkStatus`: For two-input operators, same mechanism.
- **No `StatusWatermarkValve` equivalent** — the multi-input watermark combination is simpler (per-index tracking without subpartition-level heap).

### 5.3 Comparison: Match / Gap

| Aspect | Flink | nop-stream | Classification |
|--------|-------|------------|----------------|
| WatermarkStrategy interface | TimestampAssignerSupplier + WatermarkGeneratorSupplier | Identical | Match |
| Static convenience methods | forMonotonous/BoundedOutOfOrderness/noWatermarks | Identical | Match |
| Builder: withIdleness | Yes | Yes | Match |
| Builder: withWatermarkAlignment | Yes | Yes | Match |
| WatermarkGenerator interface | onEvent + onPeriodicEmit | Identical | Match |
| TimestampsAndWatermarksOperator | Full operator with open/processElement/onProcessingTime | Same structure | Match |
| watermarkInterval default | From ExecutionConfig.getAutoWatermarkInterval() | 200L (DEFAULT_WATERMARK_INTERVAL_MS) | Match (not hardcoded 0) |
| Periodic emission | processingTimeService.registerTimer -> onPeriodicEmit | Same pattern | Match |
| Auto-insertion | Yes, via transformation pipeline | Yes, via DataStreamImpl.assignTimestampsAndWatermarks() | Match |
| SourceFunction watermark auto-assign | AutomaticWatermarkContext in StreamSourceContexts | Not implemented — source timestamps are user-managed | **Gap** |
| Multi-input (2 inputs) | CombinedWatermarkStatus / IndexedCombinedWatermarkStatus | Same classes | Match |
| Multi-input (N subpartitions) | StatusWatermarkValve with heap-based min-tracking | No equivalent — only per-input-index tracking | **Gap (N-input)** |
| Idle detection | markIdle/markActive via WatermarkOutput | Same pattern | Match |
| Per-element watermark emission | If watermarkInterval = 0, emit after every element | Same (processElement line 84 check) | Match |

---

## 6. Watermark Auto-Insertion (Legacy SourceFunction API)

### 6.1 Flink SourceFunction Integration

**File:** `flink-runtime/src/main/java/org/apache/flink/streaming/api/operators/StreamSource.java`

`run()` (lines 65-113):
- Reads `watermarkInterval = getExecutionConfig().getAutoWatermarkInterval()`.
- Creates source context via `StreamSourceContexts.getSourceContext(processingTimeService, ..., watermarkInterval, -1, emitProgressiveWatermarks)`.
- Calls `userFunction.run(ctx)`.

**`AutomaticWatermarkContext`** (StreamSourceContexts.java:201-356):
- Registers first `WatermarkEmittingTask` at `now + watermarkInterval`.
- `processAndCollect()` (lines 242-258): If `lastRecordTime > nextWatermarkTime`, emits `Watermark(watermarkTime)`. Watermark advances in `watermarkInterval` increments.
- `WatermarkEmittingTask.onProcessingTime()` (lines 320-355): Checks idle timeout, emits watermark at current time modulo watermarkInterval, re-registers.

**`ManualWatermarkContext`** (lines 366-414): Used when source explicitly calls `ctx.emitWatermark()`.

### 6.2 nop-stream Watermark Auto-Insertion

**No `StreamSource` equivalent** with automatic watermark context.

Sources must explicitly call `.assignTimestampsAndWatermarks()` on the output stream. There is no `StreamSourceContexts` class that wraps a `SourceFunction` with automatic watermark emission.

The `TimestampsAndWatermarksOperator` is only created when user code invokes `assignTimestampsAndWatermarks()` on a `DataStream`. Sources that do not chain this operator produce no watermarks at all.

### 6.3 Comparison: Match / Gap

| Aspect | Flink | nop-stream | Classification |
|--------|-------|------------|----------------|
| SourceFunction automatic watermark | AutomaticWatermarkContext with periodic emission | **Not implemented** — no StreamSourceContexts equivalent | **Gap** |
| Manual watermark emission | ManualWatermarkContext for explicit emit | Source must call assignTimestampsAndWatermarks() | Match (pattern different) |
| Source-level watermarkInterval config | ExecutionConfig.getAutoWatermarkInterval() | No source-level equivalent | Gap |
| Watermark integration for legacy sources | Full (Automatic + Manual paths) | None — all sources must use WatermarkStrategy | **Gap** |

---

## 7. Gap Table

Classifications: **Bug** (incorrect behavior), **Gap** (feature missing), **Improvement** (enhancement opportunity), **Hollow** (interface exists but body no-op), **No-Op** (silent skip), **Doc** (documentation/contract drift).

### P0 — Correctness Blocking

| # | Finding | Classification | Flink Ref | nop-stream Ref | Description |
|---|---------|---------------|-----------|----------------|-------------|
| G1 | Session window merge fails for AggregatingState | **Bug/P0** | `WindowOperator.mergeWindowContents()` uses `state.mergeNamespaces()` | `WindowOperator.mergeWindowContents()` uses `clear() + add()` — throws "Failed to set merged accumulator" | 4 disabled tests — `TestSessionWindowAdvancedMerge` (entire class) + `testMultiKeyIndependentSessions`. Root cause: AggregatingState merge should use merge() not clear+add() |
| G2 | No timer checkpoint/restore | **Bug/P0** | `InternalTimerServiceImpl.snapshotTimersForKeyGroup()` + restore | `HeapInternalTimerService` and `WindowOperatorTimerService` both purely in-memory | After failure, timer state is lost — timers never fire post-recovery, causing stuck windows |

### P1 — Design Contract Violation

| # | Finding | Classification | Flink Ref | nop-stream Ref | Description |
|---|---------|---------------|-----------|----------------|-------------|
| G3 | PaneInfo/PaneState data model exists but not wired | **Hollow/P1** | No explicit PaneState class (pane is implicit in per-window state) | `PaneInfo.java`, `PaneState.java` exist as `@DataBean` classes but WindowOperator never reads/writes them | Users can reference PaneInfo/PaneState in code but they have no runtime effect |
| G4 | AccumulationMode not wired | **Gap/P1** | `WindowingStrategy.accumulationMode` influences window function behavior | `WindowingStrategy.accumulationMode` enum exists but WindowOperator ignores it | No effect on fire behavior — always accumulates |
| G5 | Two parallel timer service implementations | **Gap/P1** | Single `InternalTimerServiceImpl` | `HeapInternalTimerService` vs `WindowOperatorTimerService` — different data structures (TreeMap vs PriorityQueue), different management | Unnecessary duplication; WindowOperatorTimerService not registered with TimerServiceManager |
| G6 | SourceFunction watermark auto-insertion missing | **Gap/P1** | `StreamSourceContexts.AutomaticWatermarkContext` + `ManualWatermarkContext` | No equivalent — all sources must explicitly call `assignTimestampsAndWatermarks()` | Legacy/automatic source watermarking not supported |

### P2 — Missing Capability

| # | Finding | Classification | Flink Ref | nop-stream Ref | Description |
|---|---------|---------------|-----------|----------------|-------------|
| G7 | Evictor.evictAfter() not called | **Gap/P2** | `EvictingWindowOperator.evictAfter()` called in emit path | `Evictor` interface has `evictAfter()` but WindowOperator never calls it | Post-evaluation eviction not supported |
| G8 | StatusWatermarkValve equivalent missing | **Gap/P2** | `StatusWatermarkValve` with per-subpartition heap-based min-tracking | `CombinedWatermarkStatus` only handles per-input-index (not per-subpartition) | N-input operator watermark alignment less precise than Flink |
| G9 | Early/on-time/late pane tracking missing | **Gap/P2** | Emergent from watermark advancement + allowedLateness | No explicit PaneTiming-based stage tracking despite PaneTiming.EARLY/ON_TIME/LATE enum | PaneTiming is declared but unused |

### P3 — Improvement/Optimization

| # | Finding | Classification | Flink Ref | nop-stream Ref | Description |
|---|---------|---------------|-----------|----------------|-------------|
| G10 | Deduplication in timer registration | **Improvement/P3** | PriorityQueue naturally handles duplicates via heap ordering | `WindowOperatorTimerService` uses `contains()` check before insert — O(n) scan | Pre-existing pattern, could be optimized |
| G11 | Reflective factory loading | **Improvement/P3** | Direct class construction | `WindowedStreamImpl.getFactory()` uses reflection to load `WindowOperatorFactoryImpl` | Runtime decoupling adds overhead but enables modularity |

---

## 8. Roadmap Gap Verification

| Roadmap Known Gap | Status | Evidence |
|-------------------|--------|----------|
| TimestampsAndWatermarksOperator not auto-inserted | ❌ **Partially incorrect** | Operator IS auto-inserted when `.assignTimestampsAndWatermarks()` is called. However, source-level (SourceFunction) automatic watermarking is NOT implemented — no `StreamSourceContexts` equivalent |
| watermarkInterval hardcoded to 0 | ❌ **Incorrect** | Default is `200L` (DEFAULT_WATERMARK_INTERVAL_MS). Configurable via `StreamExecutionEnvironment.getWatermarkInterval()`. Only 0 when explicitly set |
| Multi-input watermark merge may not be wired | ✅ **Confirmed** | `CombinedWatermarkStatus` exists but `StatusWatermarkValve` equivalent for N-subpartition alignment is missing. Only 2-input combined watermark is supported |

### Plan 303 Follow-up Assessment

**4 disabled session window tests:**
- Classification: **Bug/P1** (functional regression — real behavior degradation)
- Root cause: `mergeWindowContents()` uses `clear() + add()` instead of Flink's `mergeNamespaces()` — fails for AggregatingState where merge requires `merge(N, N) → N` semantics, not `clear() + add()`
- Impact: Session windows with reduce/aggregate functions will fail during merge. Basic session windows (without reduce/aggregate) work (confirmed by enabled tests in `TestSessionWindowWithPeriodicWatermark`).
- Priority: P0 (correctness blocking) — functional regression that blocks session window production use

---

## References

### Flink Source
- `org.apache.flink.streaming.runtime.operators.windowing.WindowOperator` — `flink-runtime/.../windowing/WindowOperator.java`
- `org.apache.flink.streaming.runtime.operators.windowing.MergingWindowSet` — `flink-runtime/.../windowing/MergingWindowSet.java`
- `org.apache.flink.streaming.runtime.operators.windowing.EvictingWindowOperator` — `flink-runtime/.../windowing/EvictingWindowOperator.java`
- `org.apache.flink.streaming.api.windowing.triggers.Trigger` — `flink-runtime/.../windowing/triggers/Trigger.java`
- `org.apache.flink.streaming.api.windowing.triggers.TriggerResult` — `flink-runtime/.../windowing/triggers/TriggerResult.java`
- `org.apache.flink.streaming.api.windowing.windows.TimeWindow` — `flink-runtime/.../windowing/windows/TimeWindow.java`
- `org.apache.flink.streaming.api.windowing.assigners.MergingWindowAssigner` — `flink-runtime/.../windowing/assigners/MergingWindowAssigner.java`
- `org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows` — `flink-streaming-java/.../windowing/assigners/EventTimeSessionWindows.java`
- `org.apache.flink.streaming.api.operators.InternalTimerService` — `flink-runtime/.../operators/InternalTimerService.java`
- `org.apache.flink.streaming.api.operators.InternalTimerServiceImpl` — `flink-runtime/.../operators/InternalTimerServiceImpl.java`
- `org.apache.flink.streaming.api.operators.TimerHeapInternalTimer` — `flink-runtime/.../operators/TimerHeapInternalTimer.java`
- `org.apache.flink.streaming.api.operators.InternalTimeServiceManagerImpl` — `flink-runtime/.../operators/InternalTimeServiceManagerImpl.java`
- `org.apache.flink.streaming.runtime.operators.TimestampsAndWatermarksOperator` — `flink-streaming-java/.../operators/TimestampsAndWatermarksOperator.java`
- `org.apache.flink.api.common.eventtime.WatermarkStrategy` — `flink-core/.../eventtime/WatermarkStrategy.java`
- `org.apache.flink.api.common.eventtime.WatermarkGenerator` — `flink-core/.../eventtime/WatermarkGenerator.java`
- `org.apache.flink.api.common.eventtime.CombinedWatermarkStatus` — `flink-core/.../eventtime/CombinedWatermarkStatus.java`
- `org.apache.flink.api.common.eventtime.IndexedCombinedWatermarkStatus` — `flink-core/.../eventtime/IndexedCombinedWatermarkStatus.java`
- `org.apache.flink.streaming.runtime.watermarkstatus.StatusWatermarkValve` — `flink-runtime/.../watermarkstatus/StatusWatermarkValve.java`
- `org.apache.flink.streaming.api.operators.StreamSource` — `flink-streaming-java/.../operators/StreamSource.java`
- `org.apache.flink.streaming.api.operators.StreamSourceContexts` — `flink-streaming-java/.../operators/StreamSourceContexts.java`

### nop-stream Source
- `io.nop.stream.runtime.operators.windowing.WindowOperator` — `nop-stream-runtime/.../operators/windowing/WindowOperator.java`
- `io.nop.stream.runtime.operators.windowing.MergingWindowSet` — `nop-stream-runtime/.../operators/windowing/MergingWindowSet.java`
- `io.nop.stream.runtime.operators.WindowOperatorTimerService` — `nop-stream-runtime/.../operators/WindowOperatorTimerService.java`
- `io.nop.stream.core.operators.HeapInternalTimerService` — `nop-stream-core/.../operators/HeapInternalTimerService.java`
- `io.nop.stream.core.operators.InternalTimerService` — `nop-stream-core/.../operators/InternalTimerService.java`
- `io.nop.stream.core.operators.InternalTimer` — `nop-stream-core/.../operators/InternalTimer.java`
- `io.nop.stream.core.operators.TimerServiceManager` — `nop-stream-core/.../operators/TimerServiceManager.java`
- `io.nop.stream.core.operators.Triggerable` — `nop-stream-core/.../operators/Triggerable.java`
- `io.nop.stream.core.operators.TimestampsAndWatermarksOperator` — `nop-stream-core/.../operators/TimestampsAndWatermarksOperator.java`
- `io.nop.stream.core.common.eventtime.WatermarkStrategy` — `nop-stream-core/.../common/eventtime/WatermarkStrategy.java`
- `io.nop.stream.core.common.eventtime.WatermarkGenerator` — `nop-stream-core/.../common/eventtime/WatermarkGenerator.java`
- `io.nop.stream.core.common.eventtime.CombinedWatermarkStatus` — `nop-stream-core/.../common/eventtime/CombinedWatermarkStatus.java`
- `io.nop.stream.core.common.eventtime.IndexedCombinedWatermarkStatus` — `nop-stream-core/.../common/eventtime/IndexedCombinedWatermarkStatus.java`
- `io.nop.stream.core.common.eventtime.BoundedOutOfOrdernessWatermarks` — `nop-stream-core/.../common/eventtime/BoundedOutOfOrdernessWatermarks.java`
- `io.nop.stream.core.windowing.PaneInfo` — `nop-stream-core/.../windowing/PaneInfo.java`
- `io.nop.stream.core.windowing.PaneState` — `nop-stream-core/.../windowing/PaneState.java`
- `io.nop.stream.core.windowing.triggers.TriggerResult` — `nop-stream-core/.../windowing/triggers/TriggerResult.java`
- `io.nop.stream.core.windowing.assigners.EventTimeSessionWindows` — `nop-stream-core/.../windowing/assigners/EventTimeSessionWindows.java`
- `io.nop.stream.core.windowing.assigners.MergingWindowAssigner` — `nop-stream-core/.../windowing/assigners/MergingWindowAssigner.java`
- `io.nop.stream.core.windowing.windows.TimeWindow` — `nop-stream-core/.../windowing/windows/TimeWindow.java`
- `io.nop.stream.core.windowing.WindowingStrategy` — `nop-stream-core/.../windowing/WindowingStrategy.java`
- `io.nop.stream.core.datastream.DataStreamImpl` — `nop-stream-core/.../datastream/DataStreamImpl.java` (assignTimestampsAndWatermarks at line 210-221)
