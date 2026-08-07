# Stage 11 — Window, Watermark & Timer Evidence

> Status: produced by Stage 11 audit (plan `nop-stream-independent-audit/2026-08-08-1835-3-window-watermark-timer-audit.md`)
> Domain: manifest a/g (window/watermark/timer/pane source surface in nop-stream-core + WindowOperator/MergingWindowSet in nop-stream-runtime, and the in-process test lane)
> Lane policy: only `in-process` lane (single-JVM source-to-sink / operator-chain) or stronger is credited for window result semantics, watermark propagation, and timer-fire claims; `unit` is component-only. Any capability needing cross-JVM control-plane / HA is `blocked` or `residual-risk` per Stage 5.
> Validator: `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence` (parses `@@EVIDENCE` rows from `*.evidence.md` direct children of this dir)
> All source/test anchors in this file were verified against the live repo on 2026-08-08.

## Window Assigner × Trigger Result Correctness Matrix (frozen by this audit)

This matrix adjudicates every supported window-assigner × trigger combination. Each row cites the live source
anchor that implements it. The matrix changes neither the 11 evidence-row fields nor the 7-value disposition
vocabulary (frozen by Stage 4 `evidence-schema.md`).

| # | Combination | Verdict | Lane | Live anchor (implementing) | Evidence row |
| --- | --- | --- | --- | --- | --- |
| WM1 | Tumbling event-time window (TumblingEventTimeWindows + EventTimeTrigger) | **SUPPORTED** | in-process | `TumblingEventTimeWindows:48`; `EventTimeTrigger:29` (fires at `window.maxTimestamp()`); `WindowOperator.processElementForRegularWindow:691` | EVID-S11-001 |
| WM2 | Sliding event-time window (SlidingEventTimeWindows + EventTimeTrigger) | **SUPPORTED** | in-process | `SlidingEventTimeWindows:26` (assigns multiple overlapping windows per element); `EventTimeTrigger:76`; shared regular-window path `:691` | EVID-S11-002 |
| WM3 | Session event-time window with merge (EventTimeSessionWindows + EventTimeTrigger) | **SUPPORTED** | in-process | `EventTimeSessionWindows:20` (extends `MergingWindowAssigner`); `TimeWindow.mergeWindows:133-179`; `MergingWindowSet.addWindow:162`; `WindowOperator.mergeWindowContents:1336` | EVID-S11-006 |
| WM4 | Processing-time window (TumblingProcessingTimeWindows / SlidingProcessingTimeWindows + ProcessingTimeTrigger) | **SUPPORTED** (non-deterministic clock caveat) | in-process | `TumblingProcessingTimeWindows:27`; `ProcessingTimeTrigger:27` (fires on `onProcessingTime`); `HeapInternalTimerService.currentProcessingTime():52` returns `System.currentTimeMillis()` directly (no clock-injection seam — non-deterministic) | EVID-S11-003 |
| WM5 | Global window (GlobalWindows + custom trigger) | **SUPPORTED** (with explicit trigger) | in-process | `GlobalWindows:19`; default `NeverTrigger:52` always CONTINUE (global windows never fire without an explicit trigger); exercised with `CountTrigger:31` | EVID-S11-005 |
| WM6 | Count trigger (CountTrigger, window-agnostic) | **SUPPORTED** | in-process | `CountTrigger:31` (count ≥ maxCount via `SimpleAccumulator<Long>` `:46-48`, `canMerge=false` `:71`) | EVID-S11-004 |

Adjudication rules applied (consistent with Stage 4 schema + Stage 5 supplement):
- A supported combination gets a source-to-output evidence row with `disposition: e2e-proved` when an in-process
  test traces the chain end-to-end, or an honest weaker disposition when only a segment is exercised.
- A non-deterministic lane (processing-time wall-clock) is honestly annotated, never silently upgraded to a
  deterministic-claim `e2e-proved` (Rule #24).
- A dormant/reserved facility gets `disposition: non-goal` or `residual-risk`, never silently treated as `e2e-proved`.

---

## Evidence Rows

### Phase 1 — Window Assigner × Trigger Result Correctness Matrix

@@EVIDENCE
inventory_id: EVID-S11-001
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/assigners/TumblingEventTimeWindows.java:48
declared_guarantee: Tumbling event-time window — TumblingEventTimeWindows assigns exactly one [start, end) TimeWindow per element (windowStart = timestamp - (timestamp - offset + windowSize) % windowSize); default trigger EventTimeTrigger fires at window.maxTimestamp() (= end-1) on event-time; WindowOperator.processElementForRegularWindow accumulates into per-window state and registers the cleanup timer
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/triggers/EventTimeTrigger.java:29; nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:691-718
runtime_wiring: wired
positive_proof: TestEventTimeWindowE2E#testEventTimeWindowPipeline
rejection_proof: TestWindowOperatorBehavior#testNoOutputBeforeWatermarkCrossesBoundary
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S11-002
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/assigners/SlidingEventTimeWindows.java:26
declared_guarantee: Sliding event-time window — SlidingEventTimeWindows assigns multiple overlapping [start, end) TimeWindows per element (one per slide <= windowSize), so a single element contributes to up to size/slide windows; default trigger EventTimeTrigger fires each window at its maxTimestamp(); the same WindowOperator.processElementForRegularWindow path handles the multi-window result iteratively
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/assigners/SlidingEventTimeWindows.java:75-76; nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:691-718
runtime_wiring: wired
positive_proof: TestSlidingEventTimeWindows#testWindowBoundaryAssignment
rejection_proof: TestSlidingEventTimeWindows#testSingleWindowWhenSlideEqualsSize
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S11-003
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/assigners/TumblingProcessingTimeWindows.java:27
declared_guarantee: Processing-time window — TumblingProcessingTimeWindows/SlidingProcessingTimeWindows assign windows by processing time; default trigger ProcessingTimeTrigger fires on onProcessingTime at the window boundary; non-deterministic because HeapInternalTimerService.currentProcessingTime() returns System.currentTimeMillis() directly (no clock-injection seam), so processing-time tests rely on real wall-clock advances
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/triggers/ProcessingTimeTrigger.java:27; nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/HeapInternalTimerService.java:50-53
runtime_wiring: wired
positive_proof: TestProcessingTimeWindowIntegration#testProcessingTimeWindowPipeline
rejection_proof: TestProcessingTimeTrigger#testOnEventTimeReturnsContinue
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S11-004
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/triggers/CountTrigger.java:31
declared_guarantee: Count trigger — CountTrigger fires when the per-window element count >= maxCount (SimpleAccumulator<Long> incremented by 1 per onElement at :46-48); canMerge() returns false (:71) so it cannot be used with merging (session) windows; exercised end-to-end against GlobalWindow via TestWindowEndToEnd. NOTE live-revalidation of corpus M7-2-P2-9/M8-2-P2-23: TestCountTrigger is a 15-line stub that tests ONLY canMerge()==false (no onElement boundary / firing test); the firing semantics are instead proven by TestWindowEndToEnd#testCountWindowFires + TestWindowOperatorCorrectness#testCountTriggerFiresCorrectly
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/triggers/CountTrigger.java:31-72
runtime_wiring: wired
positive_proof: TestWindowEndToEnd#testCountWindowFires
rejection_proof: TestWindowEndToEnd#testTriggerCanMerge
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P2-9
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S11-005
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/assigners/GlobalWindows.java:19
declared_guarantee: Global window — GlobalWindows assigns every element to the singleton GlobalWindow; its default NeverTrigger (:52) always returns CONTINUE so a global window NEVER fires on its own (a user-supplied trigger such as CountTrigger is mandatory); exercised end-to-end with CountTrigger via TestWindowEndToEnd (field trigger = CountTrigger.of(MAX_COUNT) over GlobalWindow)
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/assigners/GlobalWindows.java:52; nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/windows/GlobalWindow.java:35
runtime_wiring: wired
positive_proof: TestWindowEndToEnd#testCountWindowFires
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

### Phase 2 — Session Merge, AccumulationMode, Pane & Evictor Evidence

@@EVIDENCE
inventory_id: EVID-S11-006
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/assigners/EventTimeSessionWindows.java:20
declared_guarantee: Session-window merge — EventTimeSessionWindows extends MergingWindowAssigner and delegates to TimeWindow.mergeWindows (sort by start, fold overlapping via cover); MergingWindowSet.addWindow runs the assigner merge callback and rewrites the window->stateWindow mapping; WindowOperator.mergeWindowContents merges per-window state across 3 storage paths (AggregatingState+mergeFunction / ListState concatenation / MapState fallback throwing ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT); merge guards throw ERR_STREAM_WINDOW_MERGE_INVALID_WATERMARK / ERR_STREAM_WINDOW_MERGE_INVALID_PROCESSING_TIME on invalid merge context; a late element landing between two established sessions causes them to merge into one
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/windows/TimeWindow.java:133-179; nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/MergingWindowSet.java:162; nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:1336-1480,623-639
runtime_wiring: wired
positive_proof: TestSessionWindowAdvancedMerge#testLateElementCausesTwoEstablishedSessionsToMerge
rejection_proof: TestWindowOperatorCorrectness#testMergeTypeIncompatibilityThrowsException
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S11-007
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/AccumulationMode.java:10
declared_guarantee: AccumulationMode result semantics — DISCARDING clears window state immediately after each fire (emitWindowContents clears at :899) so each pane starts empty; ACCUMULATING retains state across firings so later panes include prior contents; both are production-supported end-to-end through WindowOperator.emitWindowContents
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:852-901
runtime_wiring: wired
positive_proof: TestPaneInfoAndAccumulationMode#testAccumulationModeDiscardingClearsState
rejection_proof: TestPaneInfoAndAccumulationMode#testAccumulationModeAccumulatingKeepsState
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S11-008
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:389-395
declared_guarantee: ACCUMULATING_AND_RETRACTING fail-fast — WindowOperator.open() throws ERR_STREAM_UNSUPPORTED ("retract logic has no downstream consumer") when accumulationMode == ACCUMULATING_AND_RETRACTING, because retract mode has no supported downstream consumer in the current baseline; the operator refuses to start rather than silently running in a broken mode
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:389-395
runtime_wiring: wired
positive_proof: none
rejection_proof: TestPaneInfoAndAccumulationMode#testRetractingModeFailsFastOnOpen
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: fail-fast
@@END

@@EVIDENCE
inventory_id: EVID-S11-009
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/PaneInfo.java:15
declared_guarantee: Pane tracking — computePaneInfo classifies each firing as EARLY (watermark < window.maxTimestamp()), ON_TIME (watermark >= maxTimestamp() and not yet onTimeEmitted), or LATE (watermark >= maxTimestamp() and onTimeEmitted already true); WindowOperator hardcodes isLast=false at :928 (every pane reports isLast=false per window-design §13.3); snapshotPaneTracking persists ONLY TimeWindow-scoped panes (isTimeWindowPaneKey filter at :941/:948-958), so non-TimeWindow pane tracking is NOT checkpointed — a residual coverage gap for non-TimeWindow assigners
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:903-928,941-958
runtime_wiring: wired
positive_proof: TestPaneInfoAndAccumulationMode#testPaneTrackingSurvivesCheckpointRestore
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S11-010
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:859-892
declared_guarantee: Evictor transient-per-fire (G46 decision window-design §13.3) — eviction operates on a LOCAL wrapped ArrayList of TimestampedValue built per fire inside emitWindowContents and NEVER writes back to window state; CountEvictor keeps only the last N elements; the production ListState path is explicitly verified to be transient (state unchanged after eviction)
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/evictors/CountEvictor.java:31; nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:859-901
runtime_wiring: wired
positive_proof: TestEvictorIntegration#testEvictionIsTransientPerFireOnProductionListStatePath
rejection_proof: TestEvictorIntegration#testNoEvictorKeepsAllElements
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S11-011
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:598-602,1015-1016,1037-1051
declared_guarantee: Late-data handling — when an element is skipped because its window is late (isElementLat: element timestamp + allowedLateness <= currentWatermark, or isWindowLate via cleanupTime), processElement routes it to sideOutput when lateDataOutputTag != null (output.collect(lateDataOutputTag, element)); when lateDataOutputTag is null the element is SILENTLY DROPPED (no exception, no record). Both paths are exercised by distinct tests
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:598-602,1015-1016
runtime_wiring: wired
positive_proof: TestWindowOperatorIntegration#testLateDataOutputTag
rejection_proof: TestWindowOperatorIntegration#testLateDataDropped
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

### Phase 3 — Timer Checkpoint/Restore & Watermark Propagation Evidence

@@EVIDENCE
inventory_id: EVID-S11-012
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:513-540,545-573,386-464
declared_guarantee: Timer checkpoint/restore (G2 deferred-application pattern) — snapshotState writes 3 operator-state keys: "trigger-accumulators" (deep-clone each SimpleAccumulator), "internal-timers" (internalTimerService.snapshotTimers), "pane-tracking" (snapshotPaneTracking, TimeWindow-only); restoreState CAPTURES all 3 but DEFERS internal-timers and pane-tracking because restoreState runs BEFORE open() (canonical lifecycle constraint); open() then applies pane-tracking (:408-416) and internal-timers (:461-464) AFTER constructing HeapInternalTimerService (:455). HeapInternalTimerService.restoreTimers directly inserts stored TimerEntry bypassing currentKeySupplier (supplier returns stale key at restore time) and restores currentWatermark if newer
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/HeapInternalTimerService.java:192-202,217-234; nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:455-464
runtime_wiring: wired
positive_proof: TestTimerCheckpointRestoreE2E#testTimerSurvivesCheckpointAndFiresAfterRestore
rejection_proof: TestHeapInternalTimerServiceSnapshotRestore#testRestoredWatermarkPreventsReFiringPastTimers
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S11-013
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/TimestampsAndWatermarksOperator.java:22
declared_guarantee: Watermark generation — TimestampsAndWatermarksOperator (lives in CORE not runtime — finding M7-2-P1-16) creates the timestampAssigner + watermarkGenerator in open(), schedules a periodic timer (DEFAULT_WATERMARK_INTERVAL_MS=200), extracts per-element timestamp + onEvent + forwards, and periodically emits a watermark; BoundedOutOfOrdernessWatermarks advances maxTimestamp = max(maxTimestamp, eventTs) and emits maxTimestamp - outOfOrdernessMillis - 1; note: lives in nop-stream-core/.../operators, not runtime
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/eventtime/BoundedOutOfOrdernessWatermarks.java:36,67,72; nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/TimestampsAndWatermarksOperator.java:66-79,97-128
runtime_wiring: wired
positive_proof: TestPeriodicWatermarkAdvancement#testTimerEmitsWatermarkForElementsWithinInterval
rejection_proof: TestBoundedOutOfOrdernessWatermarks#testNoElementNoWatermarkChange
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P1-16
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S11-014
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/AbstractStreamOperator.java:377-382
declared_guarantee: Watermark propagation — AbstractStreamOperator.processWatermark calls timeServiceManager.advanceWatermark then output.emitWatermark (forward downstream); WindowOperator.processWatermark calls internalTimerService.advanceWatermark BEFORE super.processWatermark so event-time timers fire INLINE during watermark processing; TimestampsAndWatermarksOperator.processWatermark forwards an upstream watermark ONLY if mark.getTimestamp() > lastWatermarkTimestamp (monotonic passthrough, never decreases)
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/HeapInternalTimerService.java:144-165; nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:494-496; nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/TimestampsAndWatermarksOperator.java:131-137
runtime_wiring: wired
positive_proof: TestWatermarkPropagation#testWatermarkForwardedToDownstream
rejection_proof: TestWatermarkPropagation#testNoDuplicateWatermarks
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S11-015
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/eventtime/WatermarksWithIdleness.java:34
declared_guarantee: Watermark idleness — WatermarksWithIdleness toggles a source between ACTIVE and IDLE; the OperatorWatermarkOutput inner class guards emitWatermark with `if (idle) return` so an idle source suppresses watermark emission, and WatermarkStatus IDLE/ACTIVE propagates so a stalled input does not hold back the combined watermark indefinitely
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/TimestampsAndWatermarksOperator.java:149-170; nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/streamrecord/watermark/WatermarkStatus.java:85-86
runtime_wiring: wired
positive_proof: TestWatermarkIdleDetection#testIdleSuppressesWatermarkEmission
rejection_proof: TestWatermarkIdleDetection#testActiveSourceWatermarkAdvances
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

### Phase 4 — Dormant Facility Classification, Design Drift & Historical Finding Revalidation

@@EVIDENCE
inventory_id: EVID-S11-016
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/AbstractStreamOperator.java:414-429
declared_guarantee: 2-input watermark valve (dormant, M7-2-P1-12 revalidation) — AbstractStreamOperator.processWatermark(Watermark,int index) multi-input path hardcodes IndexedCombinedWatermarkStatus.forInputsCount(2) and exposes processWatermark1/processWatermark2 entry points; but nop-stream has ZERO TwoInputStreamOperator implementations and ZERO callers of processWatermark1/2 (no connect/union/join/coGroup operators exist), so this valve EXISTS BY DESIGN but NEVER FIRES AT RUNTIME. Live revalidation of M7-2-P1-12: the multi-input watermark combine remains unit-only (TestIndexedCombinedWatermarkStatus) with no e2e — Anti-Hollow-exempted per time-model-design G47 because there is no supported consumer to wire it to. Real input-count sourcing deferred to a two-input-operator successor
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/eventtime/IndexedCombinedWatermarkStatus.java:30,41
runtime_wiring: unwired
positive_proof: TestIndexedCombinedWatermarkStatus#testTwoInputMinCombine
rejection_proof: none
environment_class: unit
required_lane: unit
finding_id: M7-2-P1-12
disposition: non-goal
@@END

@@EVIDENCE
inventory_id: EVID-S11-017
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/triggers/ContinuousEventTimeTrigger.java:37-40
declared_guarantee: Reserved triggers/evictors (dormant) — ContinuousEventTimeTrigger, ContinuousProcessingTimeTrigger, DeltaTrigger, ProcessingTimeoutTrigger (all @Internal "API 预留，当前未被使用") and TimeEvictor, DeltaEvictor (all @Internal "API 预留") are reserved API surfaces that are NOT supported in the current production baseline; they carry unit tests but are never instantiated by any production assigner/builder path
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/triggers/ContinuousProcessingTimeTrigger.java:36-39; nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/triggers/DeltaTrigger.java:37-40; nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/triggers/ProcessingTimeoutTrigger.java:42-45; nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/evictors/TimeEvictor.java:36-39; nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/evictors/DeltaEvictor.java:37-40
runtime_wiring: unwired
positive_proof: TestContinuousEventTimeTrigger#testOnElementRegistersEventTimeTimer
rejection_proof: none
environment_class: unit
required_lane: unit
finding_id: none
disposition: non-goal
@@END

@@EVIDENCE
inventory_id: EVID-S11-018
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/eventtime/WatermarksWithWatermarkAlignment.java:29
declared_guarantee: WatermarksWithWatermarkAlignment + WatermarkOutputMultiplexer (dormant) — WatermarksWithWatermarkAlignment is @Internal and its Coordinator is NOT implemented (time-model-design §9.2); WatermarkOutputMultiplexer is NOT wired into the runtime (time-model-design §5.4). Both exist as API surface but cannot deliver watermark alignment at runtime in the current baseline
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/eventtime/WatermarksWithWatermarkAlignment.java:29
runtime_wiring: unwired
positive_proof: none
rejection_proof: none
environment_class: none
required_lane: unit
finding_id: none
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S11-019
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/WindowingStrategy.java:15
declared_guarantee: WindowingStrategy design-only fields — the live @DataBean WindowingStrategy carries ONLY strategyId/windowFnId/triggerId/allowedLateness/accumulationMode; the design doc (window-design §) promises closingBehavior/onTimeBehavior/outputTime which are ABSENT from the live DataBean. These fields are design-only and have no runtime effect; activation is a future feature
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/WindowingStrategy.java:15-23
runtime_wiring: partial
positive_proof: none
rejection_proof: none
environment_class: none
required_lane: unit
finding_id: none
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S11-020
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/TimestampsAndWatermarksOperator.java:22
declared_guarantee: Design drift disposition (M7-2-P1-16 + SessionEventTimeWindows naming) — (1) TimestampsAndWatermarksOperator is documented under runtime/watermark in README + time-model-design but actually lives in nop-stream-core/operators (M7-2-P1-16, confirmed live); (2) window-design lists the session assigner as SessionEventTimeWindows but the live class is EventTimeSessionWindows (no class named SessionEventTimeWindows exists). Both are documentation-vs-code drifts, not behavioral defects; convergence owned by Stage 23 (documentation contract)
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/assigners/EventTimeSessionWindows.java:20
runtime_wiring: wired
positive_proof: TestTimestampsAndWatermarksOperator#testTimestampExtraction
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P1-16
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S11-021
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/WindowedStreamImpl.java:194-240
declared_guarantee: M7-2-P2-6 revalidation — IWindowOperatorFactory requires Class<ACC>/IN/K type parameters for performative type safety, but WindowedStreamImpl ALWAYS passes Object.class (via (Class<?>)(Class<?>)Object.class unchecked cast) for the element, accumulator, and key classes at all 4 factory entry points (lines 194-195, 209-210, 224-225, 239-240). The generic class parameters are therefore performative — they never carry real type information. This is a live contract-drift residual (type-safety theater), not a runtime data-correctness defect; final disposition owned by a contract-cleanup successor
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/WindowedStreamImpl.java:194-240
runtime_wiring: wired
positive_proof: TestWindowOperatorUnificationE2E#testFactoryAutoDiscovered
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P2-6
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S11-022
source_anchor: nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/windowing/triggers/TestCountTrigger.java:1-15
declared_guarantee: M7-2-P2-9 / M8-2-P2-23 revalidation — TestCountTrigger is a 15-line stub whose ONLY test method (testCountTriggerCannotMerge) asserts canMerge()==false; it has NO onElement boundary test, NO firing test, NO clear test. The CountTrigger firing/state semantics are instead proven by TestWindowEndToEnd#testCountWindowFires + TestWindowOperatorCorrectness#testCountTriggerFiresCorrectly/#testCountTriggerStateIsPerWindow. The boundary-coverage gap is owned by active plan 2026-08-04-2300-3 (contract-drift/config/test-integrity)
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/triggers/CountTrigger.java:31-72
runtime_wiring: wired
positive_proof: TestWindowEndToEnd#testCountWindowFires
rejection_proof: TestCountTrigger#testCountTriggerCannotMerge
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P2-9
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S11-023
source_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/windowing/TestWindowOperatorBasic.java:23-72
declared_guarantee: M7-2-P2-16 revalidation — TestWindowOperatorBasic tests TimeWindow GEOMETRY primitives (intersects/cover/maxTimestamp/getWindowStartWithOffset) and NOT the WindowOperator processing pipeline; the file name implies WindowOperator coverage it does not provide. WindowOperator pipeline coverage is instead provided by TestWindowOperatorBehavior / TestWindowOperatorCorrectness / TestWindowOperatorUnificationE2E. This is a test-naming/coverage residual, not a production defect
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/windows/TimeWindow.java:82-189
runtime_wiring: wired
positive_proof: TestWindowOperatorCorrectness#testAggregateFunctionFirstElementNotLost
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P2-16
disposition: residual-risk
@@END

---

## Cross-Reference Notes (final disposition owned by Stages 19-22; coverage gaps flagged here)

- **M7-2-P2-6** (IWindowOperatorFactory performative type safety): **residual-risk.** `WindowedStreamImpl:194-240` still casts `Object.class` for ACC/IN/K at all 4 factory entry points. The generics are performative; this is a type-safety-theater contract drift, not a runtime data-correctness defect. Contract cleanup owned by a future contract-successor. EVID-S11-021.
- **M7-2-P2-9 / M8-2-P2-23** (TestCountTrigger boundary gap): **residual-risk.** `TestCountTrigger` is a 15-line stub testing only `canMerge()==false`; no `onElement`/firing boundary test. CountTrigger firing semantics are proven elsewhere (`TestWindowEndToEnd#testCountWindowFires`, `TestWindowOperatorCorrectness#testCountTriggerFiresCorrectly`). The boundary-test gap is owned by active plan `2026-08-04-2300-3`. EVID-S11-022.
- **M7-2-P2-16** (TestWindowOperatorBasic geometry only): **residual-risk.** `TestWindowOperatorBasic` tests TimeWindow geometry primitives, not the WindowOperator pipeline; WindowOperator pipeline coverage exists in `TestWindowOperatorBehavior`/`TestWindowOperatorCorrectness`/`TestWindowOperatorUnificationE2E`. Test-naming residual, not a production defect. EVID-S11-023.
- **M7-2-P1-12** (multi-input watermark combine unit-only): **non-goal.** The 2-input watermark valve (`processWatermark1/2` + `IndexedCombinedWatermarkStatus.forInputsCount(2)`) exists but has ZERO `TwoInputStreamOperator` consumers and ZERO runtime callers; only unit-tested (`TestIndexedCombinedWatermarkStatus`). Anti-Hollow-exempted per `time-model-design` G47 because there is no supported consumer to wire it to. Real input-count sourcing deferred to a two-input-operator successor. EVID-S11-016.
- **M7-2-P1-16** (TimestampsAndWatermarksOperator doc drift): **residual-risk.** Confirmed live: `TimestampsAndWatermarksOperator` lives in `nop-stream-core/operators`, not runtime as documented. Documentation convergence owned by Stage 23. EVID-S11-013 / EVID-S11-020.

## Non-Goals honored (not silently dropped)

- Checkpoint barrier alignment/recovery semantics = Stage 9 (timer restore cross-ref done here via EVID-S11-012).
- State backend encoding (memory/RocksDB schema, key-layout) = Stage 10.
- CEP NFA/SharedBuffer recovery = Stage 12 (CEP has its own `io.nop.stream.cep.time.TimerService`).
- Distributed control-plane/data-plane transport = Stages 13/14.
- Connector source/sink guarantees = Stages 15/16.
- Reserved triggers/evictors (`ContinuousEventTimeTrigger`/`ContinuousProcessingTimeTrigger`/`DeltaTrigger`/`ProcessingTimeoutTrigger`/`TimeEvictor`/`DeltaEvictor`) = `non-goal` (API 预留, no production consumer). EVID-S11-017.
- `WatermarksWithWatermarkAlignment` Coordinator + `WatermarkOutputMultiplexer` = `residual-risk` (not implemented/not wired). EVID-S11-018.
- `WindowingStrategy` closingBehavior/onTimeBehavior/outputTime = `residual-risk` (design-only, absent from live DataBean). EVID-S11-019.
- Processing-time timer deterministic clock injection = future improvement (`HeapInternalTimerService.currentProcessingTime()` returns `System.currentTimeMillis()` directly — non-deterministic; EVID-S11-003 annotated honestly).
- `ACCUMULATING_AND_RETRACTING` = `fail-fast` (no downstream consumer; EVID-S11-008).
