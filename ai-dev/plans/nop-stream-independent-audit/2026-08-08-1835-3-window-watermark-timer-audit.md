# 11 Window, Watermark & Timer Audit (nop-stream Independent Audit)

> Plan Status: active
> Last Reviewed: 2026-08-08
> Source: `ai-dev/backlog/nop-stream-independent-audit-roadmap.md` (Stage 11); frozen Stage-4 outputs (`source-manifest.md`, `evidence-schema.md`, `finding-corpus.md`, `ai-dev/tools/check-nop-stream-audit-manifest.mjs`); frozen Stage-5 outputs (`environment-qualification.md`); frozen Stage-6 outputs (`stage-6-java-api-graph-local.evidence.md`); frozen Stage-9 outputs (`stage-9-checkpoint-barrier-recovery.evidence.md`); live repo baseline of `nop-stream-core` window/watermark/timer surfaces + `nop-stream-runtime` WindowOperator/MergingWindowSet surfaces.
> Mission: nop-stream-independent-audit
> Work Item: 11. Window, watermark and timer audit
> Related: Execution order `{3}` of this DRAFT_PLANS round. Roadmap deps: Stage 4 (evidence schema), Stage 6 (Java/local audit), Stage 9 (checkpoint audit) — all `done`. Not on critical path. Unblocks Stage 19 (Hist P0/P1 checkpoint/state/window), Stage 21 (Hist P2 core/state/window).

## Purpose

独立验证 nop-stream 的 **window 结果语义、watermark 传播、pane 语义与 timer 语义（含 recovery 交互）** 是否实现其设计目标。本审计验证：window assigner/trigger/evictor 组合的结果正确性、event-time/processing-time/session merge 行为、watermark 生成与传播、timer checkpoint/restore 恢复交互、late-data 处理、以及 dormant multi-input facility（无 supported consumer）的显式分类。

本审计**发现**的任何 confirmed live defect 不在本计划内修复，而按 roadmap 规则指派给 active/successor remediation plan。

## Current Baseline

经 2026-08-08 live repo 核对（引用均与 frozen Stage-4 `source-manifest.md` 域 a/g + 实际源码一致；line anchors 经 explore agent 逐行复核）：

- **Window 抽象（`nop-stream-core/.../windowing/windows/`）**：`Window`（abstract base）、`TimeWindow`（`getStart()/getEnd()/maxTimestamp()` `:82` = `end-1`；`intersects` `:114`、`cover` `:121`、**`mergeWindows()`** `:133-179` session-merge algorithm：sort by start, fold overlapping via cover；`getWindowStartWithOffset` `:189`）、`GlobalWindow`（singleton via `get()`）。
- **WindowAssigner 层次（`.../windowing/assigners/`）**：base abstract `WindowAssigner<T,W>` `:41`（`assignWindows()` `:51`、`getDefaultTrigger()` `:60`、`isEventTime()` `:66`）；merge-capable base `MergingWindowAssigner<T,W>` `:31`（`mergeWindows()` `:40` + `MergeCallback` `:46`）。**5 concrete assigners**：`TumblingEventTimeWindows` `:48`（default `EventTimeTrigger`）、`TumblingProcessingTimeWindows`（default `ProcessingTimeTrigger`）、`SlidingEventTimeWindows` `:26`（default `EventTimeTrigger` `:76`）、`SlidingProcessingTimeWindows`（default `ProcessingTimeTrigger`）、`EventTimeSessionWindows` `:20`（**merge-capable**，extends `MergingWindowAssigner`，default `EventTimeTrigger` `:50`，`mergeWindows` delegates to `TimeWindow.mergeWindows` `:59`）、`GlobalWindows` `:19`（default private `NeverTrigger` `:52` always CONTINUE）。**设计漂移**：`window-design.md:75` 列为 `SessionEventTimeWindows` 但 live class 是 `EventTimeSessionWindows`；**无 processing-time session window assigner**。
- **WindowOperator（`nop-stream-runtime/.../operators/windowing/WindowOperator.java`，2026 行）**：single unified operator（`<K,IN,ACC,OUT,W extends Window>` `:122`，extends `AbstractUdfStreamOperator` implements `OneInputStreamOperator` + `Triggerable<K,W>` `:123-124`）。Key fields：`windowAssigner` `:136`、`trigger` `:140`、`allowedLateness` `:172`、`lateDataOutputTag` `:179`、`evictor` `:187`、`accumulationMode` `:189`、`paneTracking` `:198`（Map<String,PaneTrackingInfo>）、`mergingSetsState` `:207`（InternalListState）、`internalTimerService` `:225`（**typed as concrete `HeapInternalTimerService<K,W>`** per time-model-design §10.1）。Main flows：`processElement` `:578`（branches on `MergingWindowAssigner` `:592`）→ `processElementForMergingWindow` `:605`（session）/ `processElementForRegularWindow` `:691`（tumbling/sliding/global）；`processWatermark` `:494` → `internalTimerService.advanceWatermark` `:495` → `super.processWatermark`；`onEventTime` `:725`（Triggerable callback，restore key context `:735`，fire/purge `:756-771`，cleanup if `isCleanupTime` `:773-782`）；`onProcessingTime` `:791`（symmetric）。
- **Session-window merge 逻辑**：两协作类——(a) `MergingWindowSet<W>`（`operators/windowing/MergingWindowSet.java:57`，`Map<W,W> mapping` `:66`，`addWindow()` `:162` calls `windowAssigner.mergeWindows()` then mapping surgery，`getStateWindow()` `:125`，`retireWindow()` `:134`，`persist()` `:105` dirty-check）；(b) `WindowOperator.mergeWindowContents` `:1336`（state-merge callback，3 storage paths：AggregatingState+mergeFunction `:1341-1392` uses `setAccumulator` `:1385` per design constraint；ListState `:1394-1441` concatenates+clears sources；Generic MapState fallback `:1443-1480` throws `ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT` `:1468` for non-accumulator）。**Merge guards** `:623-639`：`ERR_STREAM_WINDOW_MERGE_INVALID_WATERMARK` / `ERR_STREAM_WINDOW_MERGE_INVALID_PROCESSING_TIME`。
- **Trigger 系统（`nop-stream-core/.../windowing/triggers/`）**：base `Trigger<T,W>` `:51`（abstract `onElement/onProcessingTime/onEventTime/clear` + `canMerge()` `:92` default false + `onMerge()` `:103` default throws）；`TriggerResult` enum `:29`（CONTINUE/FIRE_AND_PURGE/FIRE/PURGE）。**8 concrete triggers**：`EventTimeTrigger` `:29`（fires at `window.maxTimestamp()`，`canMerge=true` `:64`，production）、`ProcessingTimeTrigger` `:27`（`canMerge=true` `:56`，production）、`CountTrigger<W>` `:31`（count ≥ maxCount，`canMerge=false` `:71`，production）、`PurgingTrigger<T,W>` `:32`（wraps nested，converts FIRE→FIRE_AND_PURGE，production）、`ContinuousEventTimeTrigger<W>` `:40`（**`@Internal "API 预留，当前未被使用"`** `:37-38`）、`ContinuousProcessingTimeTrigger<W>` `:39`（**reserved** `:36-37`）、`DeltaTrigger<T,W>` `:40`（**reserved** `:37-38`）、`ProcessingTimeoutTrigger<T,W>` `:45`（**reserved** `:42`）。
- **Evictor 系统（`nop-stream-core/.../windowing/evictors/`）**：interface `Evictor<T,W>` `:40`（`evictBefore()` `:50` + `evictAfter()` `:64`）。**3 concrete evictors**：`CountEvictor<W>` `:31`（keeps last N，production）、`TimeEvictor<W>` `:39`（**reserved** `:36`）、`DeltaEvictor<T,W>` `:40`（**reserved** `:37`）。**Evictor is transient-per-fire（G46 decision `window-design.md:284`）**：eviction operates on local `wrapped` ArrayList in `emitWindowContents()` `:859-892`，never writes back to state。
- **AccumulationMode** enum `:10`：`DISCARDING`（clear after each fire `:898-900`）、`ACCUMULATING`（keep state across firings）、`ACCUMULATING_AND_RETRACTING`（**spec-only**：`WindowOperator.open()` `:389-395` throws `ERR_STREAM_UNSUPPORTED` "retract logic has no downstream consumer"）。
- **Watermark（`nop-stream-core/.../streamrecord/watermark/` + `.../common/eventtime/`）**：`Watermark` `:39`（`MAX_WATERMARK` `:42` = `Long.MAX_VALUE`，`UNINITIALIZED` `:44` = `Long.MIN_VALUE`）；`WatermarkStatus` `:80`（`IDLE`/`ACTIVE` `:85-86`）。`WatermarkStrategy<T>` `:52`（convenience factories：`forMonotonousTimestamps` `:216`、`forBoundedOutOfOrderness` `:231`、`noWatermarks` `:246`；decorators：`withIdleness` `:145`、`withWatermarkAlignment` `:166/188`）。Generators：`BoundedOutOfOrdernessWatermarks<T>` `:36`（`maxTimestamp = max(maxTimestamp, eventTs)` `:67`；`emitWatermark(maxTimestamp - outOfOrdernessMillis - 1)` `:72`）、`AscendingTimestampsWatermarks`（subclass outOfOrderness=0）、`NoWatermarksGenerator`、`WatermarksWithIdleness<T>`、`WatermarksWithWatermarkAlignment<T>`（**Coordinator not implemented** `time-model-design.md:236` §9.2）。`WatermarkOutputMultiplexer` **not wired** `time-model-design.md:158/238`。
- **Multi-input watermark combine（dormant）**：`IndexedCombinedWatermarkStatus` `:30`（`@Internal`，`forInputsCount(int)` `:41` **N-capable**，delegates to `CombinedWatermarkStatus` — `updateCombinedWatermark()` takes `Math.min` over active inputs）。`AbstractStreamOperator` `:40` has `private transient IndexedCombinedWatermarkStatus combinedWatermark`；`processWatermark(Watermark, int index)` `:414-421` multi-input path uses **literal `2`** `:416`；`processWatermark1/2` `:423-428` public entry points。**Dormancy**（per `time-model-design.md:168` G47 + §5.4）：nop-stream has **zero `TwoInputStreamOperator` implementations** and **zero callers of `processWatermark1/2`**（no `connect`/`union`/`join`/`coGroup` operators exist）。Single-test-covered（`TestIndexedCombinedWatermarkStatus`），Anti-Hollow-exempted。
- **TimestampsAndWatermarksOperator（`nop-stream-core/.../operators/TimestampsAndWatermarksOperator.java:22`）**——note: lives in **core** not runtime（finding M7-2-P1-16）。`open()` `:66` creates timestampAssigner + watermarkGenerator，schedules periodic timer `:77/84`（`DEFAULT_WATERMARK_INTERVAL_MS = 200` `:30`）。`processElement` `:97`：extracts timestamp `:100`，sets on StreamRecord `:101`，`onEvent` `:103`，forwards `:105`，conditionally emits periodic watermark `:117-127`。`processWatermark` `:131`：only forwards if `mark.getTimestamp() > lastWatermarkTimestamp` `:133`（passes through upstream watermarks monotonically）。`finish()` `:140`：cancels timer，final `onPeriodicEmit`，emits `MAX_WATERMARK` `:146`。
- **Watermark 传播**：`AbstractStreamOperator.processWatermark()` `:377`——calls `timeServiceManager.advanceWatermark(mark)` `:378` then `output.emitWatermark(mark)` `:381`。`WindowOperator.processWatermark()` `:494`——calls `internalTimerService.advanceWatermark(mark.getTimestamp())` `:495` **before** `super.processWatermark`，so timers fire inline during watermark processing。
- **Timer 服务（`nop-stream-core/.../operators/`）**：`InternalTimerService<N>` `:31`、`InternalTimer<K,N>` `:37`（`@Internal`，extends `PriorityComparable`+`Keyed`）、`Triggerable<K,N>` `:30`（`@Internal`）、`ProcessingTimeService` `:28`、`TimerServiceManager` `:22`（manages multiple `HeapInternalTimerService`，`advanceWatermark()` `:32` + `fireProcessingTimeTimers()` `:42` fan-out）。Legacy `io.nop.stream.core.time.TimerService` `:31`（**`@Deprecated @Internal`** `:29-30`，retained for back-compat）。
- **HeapInternalTimerService（`nop-stream-core/.../operators/HeapInternalTimerService.java:33`）**——the unified implementation：storage `TreeMap<Long, Set<TimerEntry<K,N>>>` for eventTime `:35` + processingTime `:36`；`currentWatermark = Long.MIN_VALUE` `:39`。Registration `:60-96`（pulls key from `currentKeySupplier.get()`）。Firing：`advanceWatermark(long)` `:144`（no-op if `<= currentWatermark` `:145`；drains `firstEntry() <= newWatermark`，fires via `triggerable.onEventTime(...)` `:162`）；`fireProcessingTimeTimers(long)` `:121`。`currentProcessingTime()` `:52`——**`System.currentTimeMillis()`** directly（no clock-injection seam；non-deterministic for testing）。**Checkpoint/Restore（G2）**：`snapshotTimers() → TimerSnapshot<K,N>` `:192`（collects all entries + `currentWatermark`，already-fired naturally excluded）；`restoreTimers(TimerSnapshot<K,N>)` `:217`（**directly inserts stored `TimerEntry` bypassing `currentKeySupplier`** `:221-226` — critical because supplier returns stale key at restore time；restores `currentWatermark` if newer `:231-233`）。WindowOperator construction in `open()` `:455`：`new HeapInternalTimerService<>(this, () -> getKeyedStateBackend() != null ? (K) getKeyedStateBackend().getCurrentKey() : null)`。**Namespace = window (W)，key = state backend current key**。
- **Pane 语义**：`PaneInfo` `:15`（`@DataBean`，immutable：`index`/`isFirst`/`isLast`(always false per `window-design.md` §13.3)/`timing`；enum `PaneTiming { EARLY, ON_TIME, LATE }` `:40`）。`WindowOperator.paneTracking:198`（Map<String,PaneTrackingInfo>）；`computePaneInfo(K,W)` `:903`（ON_TIME when `watermark >= window.maxTimestamp() && !onTimeEmitted` `:915-917`；LATE when `onTimeEmitted` `:918-919`；EARLY otherwise `:920-921`；**isLast hardcoded false** `:928`）。`AccumulationMode` post-processing in `emitWindowContents()` `:898-900`（DISCARDING → clear immediately）。
- **Cleanup logic**：`registerCleanupTimer(W)` `:1059`（cleanup time = `window.maxTimestamp + allowedLateness` event-time / `window.maxTimestamp()` processing-time；skips `Long.MAX_VALUE` `:1061`）；`isCleanupTime(W,time)` `:1110` checked in `onEventTime` `:773` / `onProcessingTime` `:834`。
- **Late data**：`isWindowLate(W)` `:1037`（`isEventTime && cleanupTime(window) <= currentWatermark`）；`isElementLate(StreamRecord)` `:1048`；`processElement` `:598-602`（if late → `sideOutput(element)` if `lateDataOutputTag != null`，else silently dropped）。
- **Recovery interaction（WindowOperator snapshot/restore）**：`snapshotState()` `:513` writes **3 operator-state keys**：`"trigger-accumulators"` `:522`（deep-clone each `SimpleAccumulator`）、`"internal-timers"` `:531`（`internalTimerService.snapshotTimers()` G2）、`"pane-tracking"` `:538`（`snapshotPaneTracking()` `:948`，**only TimeWindow-scoped** filter `isTimeWindowPaneKey` `:941/951`）。`restoreState()` `:545`——captures all 3 but **does not apply immediately**：trigger-accumulators assigned directly `:548-559`，internal-timers stored in `restoredTimerSnapshot` `:564-568`，pane-tracking stored in `restoredPaneTrackingSnapshot` `:570-573`。**Deferred application in `open()`** `:386`：pane-tracking `:408-416`，internal-timers `:461-464`（AFTER `internalTimerService` constructed `:455`）。**Rationale**：`restoreState()` runs **before** `open()`（canonical lifecycle constraint anchored at `TestCheckpointRecovery.java:480-485`）。
- **Dormant multi-input facilities**：(1) **2-input watermark valve**（`AbstractStreamOperator.processWatermark1/2` `:423-428`，literal `2` `:416`，zero `TwoInputStreamOperator` impls，zero callers，G47 dormant）；(2) `WatermarksWithWatermarkAlignment` Coordinator not implemented；(3) `WatermarkOutputMultiplexer` not wired；(4) `ContinuousEventTimeTrigger`/`ContinuousProcessingTimeTrigger`/`DeltaTrigger`/`ProcessingTimeoutTrigger` all `@Internal "API 预留，当前未被使用"`；(5) `TimeEvictor`/`DeltaEvictor` `@Internal "API 预留"`；(6) `AccumulationMode.ACCUMULATING_AND_RETRACTING` spec-only fail-fast；(7) `WindowingStrategy` fields `closingBehavior`/`onTimeBehavior`/`outputTime` design-only not in live DataBean `:15`。
- **Corpus 交叉**：finding-corpus.md 中 window-domain finding ~7 个，**无 P0**。关键 P2：M7-2-P2-6（`IWindowOperatorFactory` performative type safety）、M7-2-P2-9/M8-2-P2-23（`TestCountTrigger` tests only `canMerge()==false`，no `onElement` boundary，recurrent，deferred to `2026-08-04-2300-3`）、M7-2-P2-16（`TestWindowOperatorBasic` tests geometry not WindowOperator coverage）。P1 adjacent：M7-2-P1-12（watermark multi-input combine unit-only no e2e，Anti-Hollow-exempted）、M7-2-P1-16（`TimestampsAndWatermarksOperator` doc drift core vs runtime）、M7-2-P1-4（`StreamOperator.initializeState` never called，timer restore contract shape）。
- **测试语料**（manifest 域 g，all T1 in-process，**none gated**）：WindowOperator-level——`TestWindowOperatorBehavior`、`TestWindowOperatorCorrectness`（534 lines，Phase-3 correctness）、`TestWindowOperatorUnificationE2E`（364 lines，E2E via WindowedStream API）、`TestWindowOperatorEvictorTimestamps`、`TestPaneInfoAndAccumulationMode`（497 lines）、`TestTriggerAccumulatorsCheckpoint`（185 lines）、`TestMergingWindowSet`（280 lines）、`TestSessionWindowAdvancedMerge`（142 lines，late element causes two sessions to merge）、`TestSessionWindowWithPeriodicWatermark`（205 lines）、`TestEvictorIntegration`（251 lines）。Assigner unit——`TestTumblingEventTimeWindows`/`TestSlidingEventTimeWindows`/`TestEventTimeSessionWindows`/`TestWindowOverflow`。Trigger unit——8 trigger test classes each with `MockTriggerContext`。Timer——`TestHeapInternalTimerService`/`TestHeapInternalTimerServiceTypedKey`/`TestHeapInternalTimerServiceReentrancy`/`TestHeapInternalTimerServiceSnapshotRestore`/`TestTimerServiceManager`。Watermark——`TestTimestampsAndWatermarksOperator`/`TestPeriodicWatermarkAdvancement`/`TestWatermarkIdleDetection`/`TestWatermarkPropagation`/`TestBoundedOutOfOrdernessWatermarks`/`TestIndexedCombinedWatermarkStatus`。Checkpoint/timer/window-recovery——`TestTimerCheckpointRestoreE2E`（337 lines，G2 E2E：open → register timers → snapshotState → new WindowOperator → restoreState → open → processWatermark → asserts restored timers fire）、`TestE2EWindowOperatorWithCheckpoint`（325 lines）。
- **Stage 9 evidence 交叉**：Stage 9 Non-Goals 明确声明 window/watermark/timer 结果语义 = Stage 11。No Stage-9 evidence row has `finding_id` pointing to a window-domain finding。**Closest Stage-9→Stage-11 handoff**：timer checkpoint mechanism（Stage 9 proves barrier/snapshot/recovery lifecycle，Stage 11 must prove `WindowOperator.snapshotState`'s `"internal-timers"` + `"pane-tracking"` keys round-trip correctly）。
- **真实 gap**：(1) 没有 window assigner × trigger × AccumulationMode 的成套结果正确性 evidence row 矩阵；(2) session-window merge（含 late-element-causes-merge）缺独立 evidence row 冻结；(3) timer checkpoint/restore 的 deferred-application 模式（restoreState before open → apply in open）缺端到端 evidence row（虽有 `TestTimerCheckpointRestoreE2E` 但缺 evidence row 冻结）；(4) watermark 生成→传播→timer-fire 链路缺 evidence row；(5) late-data sideOutput vs silently-dropped 缺 evidence row；(6) dormant multi-input facility（2-input watermark valve + reserved triggers/evictors + ACCUMULATING_AND_RETRACTING + WindowingStrategy design-only fields）缺显式 `non-goal`/`residual-risk` 分类 evidence row；(7) 设计漂移（SessionEventTimeWindows vs EventTimeSessionWindows、TimestampsAndWatermarksOperator core vs runtime）缺 disposition evidence row。

## Goals

- 产出一份 **window assigner × trigger 结果正确性矩阵**（tumbling/sliding/session/global × event-time/processing-time/count/purging trigger），每组合一条 evidence row，`positive_proof` 为真实 in-process 实跑测试名（element → assign → accumulate → trigger fire → output assertion），`environment_class: in-process`。
- 产出 **session-window merge** evidence row：`EventTimeSessionWindows` + late-element-causes-merge 行为，`positive_proof` 引用 `TestSessionWindowAdvancedMerge` + `TestSessionWindowWithPeriodicWatermark`。
- 产出 **timer checkpoint/restore** evidence row（G2 deferred-application pattern）：`WindowOperator.snapshotState` 的 3 keys（trigger-accumulators/internal-timers/pane-tracking）round-trip + restoreState-before-open→apply-in-open 完整链路，`positive_proof` 引用 `TestTimerCheckpointRestoreE2E` + `TestTriggerAccumulatorsCheckpoint`。
- 产出 **watermark 生成→传播→timer-fire** evidence row：`TimestampsAndWatermarksOperator` periodic emit → `AbstractStreamOperator.processWatermark` → `WindowOperator.processWatermark` → `HeapInternalTimerService.advanceWatermark` → timer fire，`positive_proof` 引用 `TestPeriodicWatermarkAdvancement` + `TestWatermarkPropagation` + `TestWindowOperatorWatermarkReception`。
- 产出 **late-data 处理** evidence row：sideOutput（when `lateDataOutputTag != null`）vs silently-dropped（when null），`positive_proof`/`rejection_proof` 引用 in-process 测试。
- 产出 **AccumulationMode** evidence row：DISCARDING（clear after fire）vs ACCUMULATING（keep state）行为正确性 + ACCUMULATING_AND_RETRACTING fail-fast（`ERR_STREAM_UNSUPPORTED`），`positive_proof` 引用 `TestPaneInfoAndAccumulationMode`，`rejection_proof` 引用 ACCUMULATING_AND_RETRACTING fail-fast 测试或标注 `unverified`。
- 产出 **evictor transient-per-fire（G46）** evidence row：`CountEvictor` eviction operates on local ArrayList never writes back to state，`positive_proof` 引用 `TestEvictorIntegration`。
- 对 **dormant multi-input facilities** 产出显式分类 evidence row：(a) 2-input watermark valve（`non-goal` 或 `residual-risk`：zero `TwoInputStreamOperator`，G47 dormant，unit-only covered）；(b) reserved triggers（`ContinuousEventTimeTrigger`/`ContinuousProcessingTimeTrigger`/`DeltaTrigger`/`ProcessingTimeoutTrigger` → `non-goal`：`@Internal "API 预留"`）；(c) reserved evictors（`TimeEvictor`/`DeltaEvictor` → `non-goal`）；(d) `WatermarksWithWatermarkAlignment` Coordinator not implemented（`residual-risk`）；(e) `WindowingStrategy` design-only fields（`residual-risk` or `non-goal`）。
- 产出 **设计漂移 disposition** evidence row：`SessionEventTimeWindows` vs `EventTimeSessionWindows`（`residual-risk` doc drift）、`TimestampsAndWatermarksOperator` core vs runtime（M7-2-P1-16 `residual-risk` doc drift）。
- 对**关键历史 P2 finding** 做 live 复验标注：M7-2-P2-6（performative type safety）、M7-2-P2-9/M8-2-P2-23（CountTrigger test boundary）、M7-2-P2-16（WindowOperatorBasic geometry only）、M7-2-P1-12（multi-input combine unit-only）、M7-2-P1-16（TimestampsAndWatermarksOperator doc drift）——据 live 行为标 `finding_id` + `disposition`。
- 所有 evidence row 经 `check-nop-stream-audit-manifest.mjs evidence --strict` 校验通过且非空过；corpus finding_id 交叉标注合法。

## Non-Goals

- Checkpoint barrier 对齐/recovery 语义——属 Stage 9（已完成，本计划引用其 lifecycle evidence 做 timer restore 交叉）。
- State backend 编码（memory/RocksDB schema、key-layout）——属 Stage 10。
- CEP NFA 匹配语义——属 Stage 12（CEP 有自己的 `io.nop.stream.cep.time.TimerService`，不在本计划范围）。
- 分布式 control-plane/data-plane transport——属 Stages 13/14。
- Connector source/sink 保证——属 Stages 15/16。
- 修复本审计发现的 confirmed live defect（按 roadmap 规则指派 remediation plan）。

## Scope

### In Scope

- `ai-dev/audits/nop-stream-independent-audit/stage-11-window-watermark-timer.evidence.md`（domain evidence rows，manifest 域 a/g 范围内的 window/watermark/timer/pane source anchor + test lane）。**文件名必须是 `*.evidence.md` 且为 audit dir 直系子文件。**
- window assigner × trigger 矩阵 + dormant facility 分类文本（写入证据文件头部，仅矩阵/判据不改 frozen 字段/词表）。

### Out Of Scope

- 修复 confirmed live defect（指派 remediation plan）。
- Checkpoint/state backend/CEP/control-plane/connector 语义（Stages 9/10/12/13/14/15/16）。
- 修改 frozen evidence-row 11 字段定义或 7 分类词表。

## Execution Plan

### Phase 1 - Window Assigner × Trigger Result Correctness Matrix Evidence

Status: planned
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-11-window-watermark-timer.evidence.md`

- Item Types: `Proof`

- [ ] 产出 tumbling event-time window evidence row：`source_anchor` 指向 `TumblingEventTimeWindows:48` + `EventTimeTrigger:29`（fires at `window.maxTimestamp()`）+ `WindowOperator.processElementForRegularWindow:691`；`positive_proof` 引用 in-process 实跑测试（element → assign → accumulate → trigger fire → output assertion），如 `TestWindowOperatorUnificationE2E` 或 `TestEventTimeWindowE2E`。
- [ ] 产出 sliding event-time window evidence row：`source_anchor` 指向 `SlidingEventTimeWindows:26` + `EventTimeTrigger:76`；`positive_proof` 引用 in-process 实跑测试。
- [ ] 产出 processing-time window evidence row：`source_anchor` 指向 `TumblingProcessingTimeWindows` + `ProcessingTimeTrigger:27`（fires on `onProcessingTime`）；`positive_proof` 引用 `TestProcessingTimeWindowIntegration` 或 `TestWindowOperatorBehavior`。**注明**：`HeapInternalTimerService.currentProcessingTime()` returns `System.currentTimeMillis()` directly（non-deterministic）——`disposition` 须标 `component-only` 或 `e2e-proved` 据 in-process 实跑诚实裁定。
- [ ] 产出 count-trigger window evidence row：`source_anchor` 指向 `CountTrigger:31`（count ≥ maxCount via `SimpleAccumulator<LongCounter>` `:46-48`，`canMerge=false` `:71`）；`positive_proof` 引用 `TestCountTrigger` + `TestWindowEndToEnd`（CountTrigger + GlobalWindow flow）。**注明 M7-2-P2-9/M8-2-P2-23**：`TestCountTrigger` tests only `canMerge()==false`，no `onElement` boundary——据 live 行为标 `finding_id` + `disposition`。
- [ ] 产出 global window evidence row：`source_anchor` 指向 `GlobalWindows:19`（default `NeverTrigger` `:52` always CONTINUE）；`positive_proof` 引用 in-process 实跑测试。
- [ ] 冻结 **window assigner × trigger 矩阵**文本（写入证据文件头部）：tumbling event-time（SUPPORTED）、sliding event-time（SUPPORTED）、session event-time merge（SUPPORTED）、processing-time（SUPPORTED，non-deterministic clock caveat）、global + count（SUPPORTED with NeverTrigger/CountTrigger）。

Exit Criteria:

- [ ] ≥5 条 window assigner × trigger evidence row，格式经 `check-nop-stream-audit-manifest.mjs evidence --strict` 校验 exit 0，且校验器实际解析到行（非空过）
- [ ] **端到端验证（Rule #22）**：tumbling event-time row 的 `positive_proof` 是真实 in-process 实跑测试名（element → assign → accumulate → trigger fire → output assertion），`environment_class >= in-process`，`disposition: e2e-proved`（若该测试存在）；若不存在端到端测试，须标 `unverified`/`component-only` 并注明——不得用 component/unit 测试充数
- [ ] **接线验证（Rule #23）**：window result row 的 `runtime_wiring` 据 LOCAL 实跑裁定（`processElement` → `windowAssigner.assignWindows` → `windowState` accumulate → `trigger.onElement` → timer register → `onEventTime` fire → `emitWindowContents` → output 确实连通），不得仅凭方法存在标 `wired`
- [ ] **无静默跳过**：processing-time row 因 `System.currentTimeMillis()` non-deterministic 不得被静默标 `e2e-proved`——须诚实标注；CountTrigger row 的 `onElement` boundary gap 须标 `finding_id`
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Session Merge, AccumulationMode, Pane & Evictor Evidence

Status: planned
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-11-window-watermark-timer.evidence.md`

- Item Types: `Proof | Decision`

- [ ] 产出 session-window merge evidence row：`source_anchor` 指向 `EventTimeSessionWindows:20`（extends `MergingWindowAssigner`）+ `TimeWindow.mergeWindows():133-179`（session-merge algorithm）+ `MergingWindowSet.addWindow():162` + `WindowOperator.mergeWindowContents:1336`（3 storage paths）+ merge guards `:623-639`；`positive_proof` 引用 `TestSessionWindowAdvancedMerge`（late element causes two established sessions to merge）+ `TestSessionWindowWithPeriodicWatermark` + `TestMergingWindowSet`。
- [ ] 产出 AccumulationMode evidence row：`source_anchor` 指向 `AccumulationMode:10`（DISCARDING/ACCUMULATING）+ `emitWindowContents():898-900`（DISCARDING → clear immediately after fire）；`positive_proof` 引用 `TestPaneInfoAndAccumulationMode`（DISCARDING vs ACCUMULATING semantics）。
- [ ] 产出 ACCUMULATING_AND_RETRACTING fail-fast evidence row：`source_anchor` 指向 `WindowOperator.open():389-395`（throws `ERR_STREAM_UNSUPPORTED` "retract logic has no downstream consumer"）；`disposition: fail-fast`；`rejection_proof` 引用相关测试或标注 `unverified` 如无 rejection 测试。
- [ ] 产出 pane tracking evidence row：`source_anchor` 指向 `PaneInfo:15` + `computePaneInfo():903`（EARLY/ON_TIME/LATE classification，`isLast` hardcoded false `:928`）+ `snapshotPaneTracking():948`（**only TimeWindow-scoped**）；`positive_proof` 引用 `TestPaneInfoAndAccumulationMode`。
- [ ] 产出 evictor transient-per-fire evidence row（G46）：`source_anchor` 指向 `emitWindowContents():859-892`（eviction on local ArrayList，never writes back to state）+ `CountEvictor:31`（keeps last N）；`positive_proof` 引用 `TestEvictorIntegration`（251 lines）+ `TestWindowOperatorEvictorTimestamps`。
- [ ] 产出 late-data evidence row：`source_anchor` 指向 `isWindowLate():1037` + `isElementLate():1048` + `processElement:598-602`（sideOutput if `lateDataOutputTag != null`，else silently dropped）+ `sideOutput():1015`；`positive_proof` 引用 in-process 实跑测试验证 sideOutput path；注明 silently-dropped path 的 disposition。

Exit Criteria:

- [ ] ≥6 条 session/accumulation/pane/evictor/late-data evidence row，格式校验 exit 0
- [ ] **端到端验证（Rule #22）**：session merge row 的 `positive_proof` 引用 in-process 实跑测试（elements → session assign → merge → output），`environment_class >= in-process`
- [ ] **接线验证（Rule #23）**：session merge row 的 `runtime_wiring` 证明 `processElementForMergingWindow → MergingWindowSet.addWindow → windowAssigner.mergeWindows → mergeWindowContents → emitWindowContents` 确实连通
- [ ] **无静默跳过**：ACCUMULATING_AND_RETRACTING 须有 `rejection_proof` 或标 `unverified`；late-data silently-dropped path 须有 disposition 而非静默忽略；pane-tracking TimeWindow-only filter 须注明非 TimeWindow pane 的 disposition
- [ ] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且校验器实际解析到行（非空过）
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Timer Checkpoint/Restore & Watermark Propagation Evidence

Status: planned
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-11-window-watermark-timer.evidence.md`

- Item Types: `Proof`

- [ ] 产出 timer checkpoint/restore evidence row（G2 deferred-application pattern）：`source_anchor` 指向 `WindowOperator.snapshotState():513`（3 keys：trigger-accumulators `:522`/internal-timers `:531`/pane-tracking `:538`）+ `restoreState():545`（capture but defer）+ `open():386`（apply pane-tracking `:408-416` + internal-timers `:461-464`）+ `HeapInternalTimerService.snapshotTimers():192`/`restoreTimers():217`（bypass currentKeySupplier `:221-226`）；`positive_proof` 引用 `TestTimerCheckpointRestoreE2E`（337 lines，G2 E2E：open → register cleanup timers → snapshotState → new WindowOperator → restoreState → open → processWatermark past threshold → asserts restored timers fire and produce correct output）+ `TestTriggerAccumulatorsCheckpoint`（185 lines）+ `TestHeapInternalTimerServiceSnapshotRestore`。
- [ ] 产出 watermark generation evidence row：`source_anchor` 指向 `TimestampsAndWatermarksOperator:22`（`open()` `:66` creates generator，schedules periodic timer `:77/84`；`processElement` `:97` extracts timestamp + `onEvent` + forward；periodic emit `:117-127`）+ `BoundedOutOfOrdernessWatermarks:36`（`maxTimestamp = max(...)` `:67`；`emitWatermark(maxTimestamp - outOfOrderness - 1)` `:72`）；`positive_proof` 引用 `TestTimestampsAndWatermarksOperator` + `TestPeriodicWatermarkAdvancement` + `TestBoundedOutOfOrdernessWatermarks`。
- [ ] 产出 watermark propagation evidence row：`source_anchor` 指向 `AbstractStreamOperator.processWatermark():377`（`timeServiceManager.advanceWatermark` `:378` → `output.emitWatermark` `:381`）+ `WindowOperator.processWatermark():494`（`internalTimerService.advanceWatermark` `:495` **before** super → timers fire inline during watermark processing）+ `TimestampsAndWatermarksOperator.processWatermark():131`（only forwards if `> lastWatermarkTimestamp` `:133` monotonic passthrough）；`positive_proof` 引用 `TestWatermarkPropagation` + `TestWindowOperatorWatermarkReception`。
- [ ] 产出 watermark idleness evidence row：`source_anchor` 指向 `WatermarksWithIdleness` + `WatermarkStatus.IDLE/ACTIVE:85-86` + `TimestampsAndWatermarksOperator.OperatorWatermarkOutput:149`（guards against `idle` `:153`）；`positive_proof` 引用 `TestWatermarkIdleDetection`。

Exit Criteria:

- [ ] ≥4 条 timer/watermark evidence row，格式校验 exit 0
- [ ] **端到端验证（Rule #22）**：timer checkpoint/restore row 的 `positive_proof` 引用 in-process 实跑测试（register timers → snapshot → new operator → restore → open → processWatermark → timers fire），`environment_class >= in-process`，`disposition: e2e-proved`
- [ ] **接线验证（Rule #23）**：timer checkpoint/restore row 的 `runtime_wiring` 证明 `snapshotState("internal-timers") → TimerSnapshot → restoreState(capture) → open(apply) → HeapInternalTimerService.restoreTimers → advanceWatermark → onEventTime fire` 完整链路连通
- [ ] **无静默跳过**：`HeapInternalTimerService.restoreTimers()` bypassing `currentKeySupplier` 是 critical 设计——须在 evidence row 注明而非忽略；`currentProcessingTime()` returns `System.currentTimeMillis()` non-determinism 须注明
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - Dormant Facility Classification, Design Drift & Historical Finding Revalidation

Status: planned
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-11-window-watermark-timer.evidence.md`

- Item Types: `Decision | Proof`

- [ ] 产出 2-input watermark valve dormant evidence row：`source_anchor` 指向 `AbstractStreamOperator.processWatermark1/2:423-428`（literal `2` `:416`）+ `IndexedCombinedWatermarkStatus.forInputsCount:41` + grep evidence（zero `TwoInputStreamOperator` implementations，zero callers of `processWatermark1/2`）；`disposition: non-goal` 或 `residual-risk`（G47 dormant adjudication per `time-model-design.md:168`）；注明"exists by design but never fires at runtime; real input-count sourcing deferred to two-input-operator successor"。
- [ ] 产出 reserved triggers/evictors dormant evidence row：`source_anchor` 指向 `ContinuousEventTimeTrigger:37-38`/`ContinuousProcessingTimeTrigger:36-37`/`DeltaTrigger:37-38`/`ProcessingTimeoutTrigger:42` + `TimeEvictor:36`/`DeltaEvictor:37`（all `@Internal "API 预留，当前未被使用"`）；`disposition: non-goal`（reserved API，not supported in current baseline）。
- [ ] 产出 `WatermarksWithWatermarkAlignment` + `WatermarkOutputMultiplexer` dormant evidence row：`source_anchor` 指向 `WatermarksWithWatermarkAlignment`（Coordinator not implemented `time-model-design.md:236`）+ `WatermarkOutputMultiplexer`（not wired `time-model-design.md:158/238`）；`disposition: residual-risk` or `non-goal`。
- [ ] 产出 `WindowingStrategy` design-only fields evidence row：`source_anchor` 指向 `WindowingStrategy.java:15`（live fields：strategyId/windowFnId/triggerId/allowedLateness/accumulationMode）vs `window-design.md:139-140`（promises closingBehavior/onTimeBehavior/outputTime absent from live DataBean）；`disposition: residual-risk`（design-only，not in live code）。
- [ ] 产出 design drift disposition evidence row：`SessionEventTimeWindows` vs `EventTimeSessionWindows`（`window-design.md:75` vs live `EventTimeSessionWindows:20`，`residual-risk` doc drift → Stage 23 doc reconciliation）+ `TimestampsAndWatermarksOperator` core vs runtime（M7-2-P1-16，`residual-risk` doc drift）。
- [ ] 对关键历史 P1/P2 finding 做 live 复验标注 evidence row（至少覆盖：M7-2-P2-6 performative type safety、M7-2-P2-9/M8-2-P2-23 CountTrigger test boundary、M7-2-P2-16 WindowOperatorBasic geometry、M7-2-P1-12 multi-input combine unit-only、M7-2-P1-16 TimestampsAndWatermarksOperator doc drift）——据 live 行为标 `finding_id` + `disposition`。
- [ ] 全 evidence 文件回归校验 + corpus 交叉标注核对。

Exit Criteria:

- [ ] ≥4 条 dormant/design-drift evidence row + ≥5 条 historical finding revalidation evidence row，格式校验 exit 0
- [ ] **无静默跳过（Rule #24）**：dormant facility 不得被静默当作 `e2e-proved`——须显式标 `non-goal`/`residual-risk` + 注明 dormant reason；design drift 须标 `residual-risk` + Stage 23 successor
- [ ] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且校验器实际解析到行（非空过）；finding_id 交叉标注合法（ID 在 frozen corpus 内或 `none`）
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **审计计划（无生产代码变更）**：本计划产出为 evidence rows + 矩阵文本，不改 nop-stream 生产代码。`./mvnw test`/`compile` 不强制；改为以 evidence 校验器退出码 + in-process 实跑证据引用为 closure 依据。但若审计中发现 confirmed live defect，按 roadmap 规则指派 remediation plan（不在本计划内修复）。

- [ ] window assigner × trigger 结果正确性矩阵各有 evidence row（in-process lane 实跑或如实标注缺覆盖）
- [ ] session-window merge（含 late-element-causes-merge）+ AccumulationMode + pane + evictor transient-per-fire + late-data 已验证（runtime_wiring 经实跑/manual-trace 裁定）
- [ ] timer checkpoint/restore G2 deferred-application pattern + watermark 生成→传播→timer-fire 已验证（端到端 in-process 实跑）
- [ ] dormant multi-input facilities（2-input valve + reserved triggers/evictors + alignment/multiplexer + WindowingStrategy design-only）有显式 `non-goal`/`residual-risk` 分类
- [ ] design drift（SessionEventTimeWindows vs EventTimeSessionWindows、TimestampsAndWatermarksOperator core vs runtime）有 disposition
- [ ] 关键历史 P1/P2 finding（至少 5 个）的 live 复验结果已标注为 evidence row
- [ ] 所有 evidence row 经 `check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且**非空过**
- [ ] 不存在被静默降级到 deferred 的 in-scope 审计项（每个 facility/finding 有明确 disposition）
- [ ] 审计发现的任何 confirmed live defect 已指派 active/successor remediation plan
- [ ] `No owner-doc update required`（不改 `docs-for-ai/`）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证（a）window result row 的 `positive_proof` 确为 in-process 实跑测试名（非组件 unit 充数），（b）timer checkpoint/restore row 的 `positive_proof` 验证 restore→open→fire 完整链路（非仅 snapshot round-trip），（c）`runtime_wiring=wired` 确经接线验证，（d）dormant facility 无静默放行（标 `non-goal`/`residual-risk`），（e）processing-time non-determinism + CountTrigger boundary gap 如实标注

## Deferred But Adjudicated

（执行中如出现延期项，须写明 Classification / Why Not Blocking Closure / Successor Required。预期场景：processing-time timer 因 `System.currentTimeMillis()` non-deterministic 无法确定性复验——此类 row 应标 `disposition: component-only` + 注明 non-deterministic caveat，而非 deferred。`TestCountTrigger` onElement boundary gap（M7-2-P2-9/M8-2-P2-23）如已由 active plan `2026-08-04-2300-3` 拥有，则 evidence row 只标 live 复验结果 + cross-ref。dormant reserved API（triggers/evictors）标 `non-goal` 是合法终态。）

## Non-Blocking Follow-ups

- `TestCountTrigger` onElement boundary test gap（M7-2-P2-9/M8-2-P2-23）→ active plan `2026-08-04-2300-3-contract-drift-config-test-integrity.md`。
- 2-input watermark valve real input-count sourcing → successor two-input-operator feature plan。
- `WatermarksWithWatermarkAlignment` Coordinator implementation → future feature plan。
- `WindowingStrategy` closingBehavior/onTimeBehavior/outputTime fields → future feature plan when semantics activated。
- design drift corrections（SessionEventTimeWindows naming、TimestampsAndWatermarksOperator module location）→ Stage 23（Documentation contract and readiness decision）。
- processing-time timer deterministic clock injection → future improvement（currently `System.currentTimeMillis()` non-deterministic）。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
- <<或者明确写 no remaining plan-owned work>>
