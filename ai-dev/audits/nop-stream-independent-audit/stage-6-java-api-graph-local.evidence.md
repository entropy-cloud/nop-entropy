# Stage 6 — Java API, Graph & LOCAL Execution Evidence

> Status: produced by Stage 6 audit (plan `2026-08-07-2346-2-java-api-graph-local-audit.md`)
> Domain: manifest a/b/g (Java public types + XDSL node surface + test lane)
> Lane policy: only `in-process` lane (single-JVM source-to-sink) or stronger is credited for system-capability claims; `unit` is component-only.
> Validator: `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence` (parses `@@EVIDENCE` rows from `*.evidence.md` direct children of this dir)

## Equivalence Criteria (frozen by this audit — topology / stable identity / recovery inputs)

These criteria are the observable invariants any later stage (7 XDSL, 8 Delta, 9 checkpoint) may
reuse. They are criteria text only; they change neither the 11 evidence-row fields nor the
7-value disposition vocabulary.

### (a) Topology equivalence — Java entry vs compiled StreamGraph/JobGraph

A Java-built topology and its compiled graph are **topology-equivalent** iff there is a one-to-one
correspondence between (i) user `Transformation` nodes and `StreamNode`s, and (ii) user
input→transformation edges and `StreamEdge`s, preserving direction and partitioner. Observable
proof: after `StreamGraphGenerator.generate(sinks)` the `StreamGraph.getStreamNodes()` count equals
the distinct Transformation count and each `getStreamEdges(srcId)` target matches the declared
downstream transformation id (demonstrated by `TestEndToEndPipeline#testCompletePipelineTransformation`,
which asserts node count + edge presence for Source→Map→Filter→Sink). `JobGraphGenerator` then
collapses chainable StreamNodes into `JobVertex`es; the vertex set is non-empty and each vertex
carries a non-null invokable + parallelism > 0 (same test, Step 3).

### (b) Stable identity — Transformation → StreamNode → JobVertex → GraphExecutionPlan

An operator/vertex id is **stable** iff the integer `Transformation.getId()` propagates without
distortion to `StreamNode.getId()` → `JobVertex` id (formatted `vertex-<id>`) → `GraphExecutionPlan`
execution-vertex key, so that `StreamExecutionEnvironment.execute()` can recover the originating
transformation from a plan vertex id (`parseVertexId`, `StreamExecutionEnvironment.java:447-461`) and
match `SourceApiTransformation` vertices for per-subtask identity wiring. Observable proof: the
parallelism-lock flag rides this same identity chain
(`TestParallelismLockedPropagation#transformationLockPropagatesThroughStreamNodeAndJobVertex` asserts
`Transformation.isParallelismLocked()` → `StreamNode.isParallelismLocked()` → `JobVertex.isParallelismLocked()`
→ `GraphExecutionPlan.build()` forces parallelism=1), confirming the id/attribute chain is continuous.

### (c) Recovery inputs — LOCAL-mode recovery input criteria (frozen for Stage 9; NOT exercised here)

LOCAL-mode recovery (should it be triggered) requires, at minimum: (i) a non-empty set of
`SinkTransformation` roots (`findSinkTransformations`, `StreamExecutionEnvironment.java:266,482`), (ii)
a compiled `JobGraph` whose vertices each carry an `OperatorChain` with realizable operators, and (iii)
a `GraphExecutionPlan` whose sorted vertex ids + subtasks are non-empty (`execute()` loop
`:329-348`). The `initializeState(TaskStateSnapshot)` operator hook is a **recovery-time** input: in
basic LOCAL execution only `OperatorChain.open()` → `StreamOperator.open()` is invoked
(`OperatorChain.java:100-106`); the `TaskStateSnapshot`-bearing `initializeState` is reached only on a
recovery/restore path (checkpoint/barrier territory, Stage 9). This audit **freezes the criteria only**;
it does not run a recovery (Non-Goal: checkpoint/recovery semantics = Stage 9).

---

## Evidence Rows

### Phase 1 — Supported DataStream constructs (source → transform → sink)

@@EVIDENCE
inventory_id: EVID-S6-001
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java:175-218
declared_guarantee: fromElements/fromCollection/addSource register a SourceTransformation and emit elements through the source operator to downstream operators in LOCAL execution
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java:543-561
runtime_wiring: wired
positive_proof: TestDataStreamPipeline#testSourceMapFilterSink
rejection_proof: TestDataStreamPipeline#testCannotExecuteTwice
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S6-002
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java:135-143
declared_guarantee: map(MapFunction) applies a one-to-one transformation per element end-to-end through LOCAL execution
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamMap.java
runtime_wiring: wired
positive_proof: TestDataStreamPipeline#testSourceMapFilterSink
rejection_proof: TestE2ESimplePipeline#testEmptySource
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P2-5
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S6-003
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java:164-170
declared_guarantee: filter(FilterFunction) retains only elements for which the predicate returns true, end-to-end through LOCAL execution
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamFilter.java
runtime_wiring: wired
positive_proof: TestDataStreamPipeline#testSourceFilterSink
rejection_proof: TestE2ESimplePipeline#testEmptySource
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S6-004
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java:180-186
declared_guarantee: flatMap(FlatMapFunction) emits zero-or-more outputs per input element, end-to-end through LOCAL execution
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamFlatMap.java
runtime_wiring: wired
positive_proof: TestDataStreamPipeline#testSourceFlatMapSink
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P2-5
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S6-005
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java:200-207
declared_guarantee: process(ProcessFunction) applies a stateful ProcessFunction via a ProcessOperator end-to-end through LOCAL execution
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/ProcessOperator.java
runtime_wiring: partial
positive_proof: TestProcessOperator#testProcessElementIsCalled
rejection_proof: none
environment_class: unit
required_lane: in-process
finding_id: M7-2-P2-5
disposition: component-only
@@END

@@EVIDENCE
inventory_id: EVID-S6-006
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java:231-251
declared_guarantee: keyBy(KeySelector) partitions the stream by key and routes elements to keyed aggregations end-to-end through LOCAL execution
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java:345-371
runtime_wiring: wired
positive_proof: TestKeyedStreamAggregation#testSumAggregation
rejection_proof: TestKeyedStreamAggregation#testNonKeyedStreamReduceRejected
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S6-007
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java:209-221
declared_guarantee: assignTimestampsAndWatermarks(WatermarkStrategy) creates a TimestampsAndWatermarksTransformation that compiles and runs end-to-end through LOCAL execution
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/graph/StreamGraphGenerator.java:438-464
runtime_wiring: wired
positive_proof: TestAssignTimestampsAndWatermarks#testPipelineWithTimestampsAndWatermarks
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S6-008
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java:103-125
declared_guarantee: transform(operatorName,typeInfo,operator) injects a user OneInputStreamOperator as a OneInputTransformation and runs it end-to-end through LOCAL execution
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/SimpleStreamOperatorFactory.java
runtime_wiring: wired
positive_proof: TestEventTimeWindowE2E#testEventTimeWindowPipeline
rejection_proof: TestKeyedStreamAggregation#testNonKeyedStreamReduceRejected
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S6-009
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java:280-293
declared_guarantee: sink(SinkFunction)/collect/print register a terminal SinkTransformation that receives elements end-to-end through LOCAL execution
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamSinkOperator.java
runtime_wiring: wired
positive_proof: TestDataStreamPipeline#testSourceMapFilterSink
rejection_proof: TestDataStreamPipeline#testCannotExecuteTwice
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

### Phase 2 — Graph/plan compilation chain & operator lifecycle

@@EVIDENCE
inventory_id: EVID-S6-010
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java:279-350
declared_guarantee: execute() compiles the topology via StreamGraphGenerator->JobGraphGenerator->PartitionedPlanGenerator->generateDeploymentPlan->GraphExecutionPlan.build and submits subtasks to TaskExecutor on the LOCAL path
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java
runtime_wiring: wired
positive_proof: TestDataStreamPipeline#testSourceMapFilterSink
rejection_proof: TestEndToEndPipeline#testCompletePipelineTransformation
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S6-011
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java:345-532
declared_guarantee: operator lifecycle open/initializeState-basic/processElement/finish/close is actually triggered on the LOCAL execution path (SELF_CONTAINED/SOURCE/MIDDLE/SINK roles)
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/OperatorChain.java:100-106
runtime_wiring: wired
positive_proof: TestE2ESimplePipeline#testMultiOperatorChain
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P1-5
disposition: e2e-proved
@@END

### Phase 3 — Fan-out, partition & parallelism

@@EVIDENCE
inventory_id: EVID-S6-012
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java:345-371
declared_guarantee: keyBy routes elements by KeySelector hash partition so that keyed state/aggregation is consistent per key on the LOCAL path
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/graph/StreamGraphGenerator.java:409-436
runtime_wiring: wired
positive_proof: TestKeyedStreamAggregation#testMinMaxAggregation
rejection_proof: TestKeyedStreamAggregation#testNonKeyedStreamReduceRejected
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S6-013
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/SingleOutputStreamOperatorImpl.java:52-58
declared_guarantee: forceNonParallel locks the transformation to parallelism=1 and the lock propagates Transformation->StreamNode->JobVertex->GraphExecutionPlan without throwing
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java:519-525
runtime_wiring: wired
positive_proof: TestParallelismLockedPropagation#transformationLockPropagatesThroughStreamNodeAndJobVertex
rejection_proof: TestCepNonKeyedEntryE2E#cepPatternOnNonKeyedStreamBuildsWithoutThrowing
environment_class: unit
required_lane: in-process
finding_id: M7-2-P0-1
disposition: component-only
@@END

### Phase 4 — Unsupported forms (absent from supported baseline)

@@EVIDENCE
inventory_id: EVID-S6-014
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStream.java:20-142
declared_guarantee: two-input connect() is not present in the core DataStream interface; the supported baseline is one-input chains only
implementation_anchor: none
runtime_wiring: unwired
positive_proof: none
rejection_proof: none
environment_class: none
required_lane: in-process
finding_id: M7-2-P2-3
disposition: non-goal
@@END

@@EVIDENCE
inventory_id: EVID-S6-015
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStream.java:20-142
declared_guarantee: union() is not present in the core DataStream interface; multi-input union is out of scope for the current supported baseline
implementation_anchor: none
runtime_wiring: unwired
positive_proof: none
rejection_proof: none
environment_class: none
required_lane: in-process
finding_id: none
disposition: non-goal
@@END

@@EVIDENCE
inventory_id: EVID-S6-016
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStream.java:20-142
declared_guarantee: core side-output (getSideOutput/OutputTag public DataStream entry) is not present; OutputTag exists as an internal type but has no public DataStream side-output construct and cross-task side output is explicitly a no-op
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java:621-623
runtime_wiring: unwired
positive_proof: none
rejection_proof: none
environment_class: none
required_lane: in-process
finding_id: none
disposition: non-goal
@@END

---

## Cross-Reference Notes (corpus findings touched by this audit — final disposition owned by Stages 19-22)

- **M7-2-P0-1** (`forceNonParallel()` always-throws): **RESOLVED in live code.** `SingleOutputStreamOperatorImpl.java:53-57` now calls `transformation.lockParallelismToOne()` instead of throwing; the lock propagates through the full compilation chain (`TestParallelismLockedPropagation`, `GraphExecutionPlan.java:519-525`). The CEP non-keyed entry path no longer crashes (`TestCepNonKeyedEntryE2E`). EVID-S6-013 is classified `component-only` (`environment_class: unit`): the lock MECHANISM is proven at graph-compilation level (Transformation→StreamNode→JobVertex→GraphExecutionPlan, with DeploymentPlan-override rejection), and the no-throw is proven by the CEP build test — but there is no `env.execute()` source-to-sink test that exercises a `forceNonParallel()` vertex end-to-end, so the row is not `e2e-proved`. Honest classification; the mechanism gap is a test-coverage item, not a live defect.
- **M7-2-P1-5** (`finish()` never called): **RESOLVED on the LOCAL path.** `StreamTaskInvokable` calls `operatorChain.finish()` for SOURCE/MIDDLE/SINK/SELF_CONTAINED roles before MAX_WATERMARK + close (`StreamTaskInvokable.java:402,448,476,508`), directly addressing the prior "buffered-data flush contract silently inactive" defect. EVID-S6-011.
- **M7-2-P1-4** (`StreamOperator.initializeState(TaskStateSnapshot)` never called): recovery-scoped. Basic LOCAL execution invokes `OperatorChain.open()` → `StreamOperator.open()` only (`OperatorChain.java:100-106`); the `TaskStateSnapshot`-bearing `initializeState` belongs to the recovery/restore path (Stage 9). Recovery-input criteria frozen above; not exercised here.
- **M7-2-P2-3** (public operator Javadoc references non-existent `TwoInputStreamOperator`/`MultipleInputStreamOperator`): **STALE.** A live search of `nop-stream-core/src/main/java/` finds **zero** occurrences of `TwoInputStreamOperator`/`MultipleInputStreamOperator` — the Javadoc references appear to have been removed. The two-input forms remain absent by design (EVID-S6-014). Final disposition owned by Stage 21.
- **M7-2-P2-5** (`UnknownTypeInformation.INSTANCE<?>` cast to `TypeInformation<R>`): **still live** at `DataStreamImpl.java:140` (map), `:183` (flatMap), `:204` (process) — confirmed by this audit. It does not block LOCAL source-to-sink execution (the in-process tests pass), but the unchecked cast remains a contract/test defect. Final disposition owned by Stage 21; flagged here only.
- **M8-2-P2-18** (`AbstractUdfStreamOperator.initializeState` passes null `operatorStateStore` when no IStateBackend): **still live** at `AbstractUdfStreamOperator.java:111-134`, but only reachable on the `ICheckpointedFunction` recovery path, not basic LOCAL execution. Out of scope for Stage 6 (checkpoint/state = Stage 9/10). Flagged here only.

## Coverage Gaps Found (assigned to successor remediation per roadmap rule)

- **process(ProcessFunction) lacks an in-process source-to-sink test.** `process()` delegates to the (e2e-proven) `transform()` → `OneInputTransformation` path and the `ProcessOperator` has component tests (`TestProcessOperator`), but no test runs `env...process(...)sink(); env.execute()` and asserts sink output. Per the e2e-proved rule this row is classified `component-only` (EVID-S6-005), not silently upgraded. This is a coverage gap, not a confirmed live defect; it should be assigned to an active/successor test-effectiveness remediation plan (roadmap item 17 / contract-test plan). Adding one `env.execute()`-driven process() test would close it.
- **forceNonParallel lacks an `env.execute()` source-to-sink test.** The lock mechanism is proven at graph-compilation level (EVID-S6-013, `component-only`), but no test runs a `forceNonParallel()`-marked vertex through full `execute()` source-to-sink. This is a coverage gap (not a live defect — the historical P0 always-throws is resolved); assignable to a test-effectiveness remediation plan.

## Note on a Stale In-Repo Comment

- `TestEventTimeWindowE2E.java:43-46` states `assignTimestampsAndWatermarks()` "creates a TimestampsAndWatermarksTransformation that the fast-path executor does not yet handle". This is **outdated**: `StreamGraphGenerator.java:227-228,438-464` handles `TimestampsAndWatermarksTransformation`, and `TestAssignTimestampsAndWatermarks#testPipelineWithTimestampsAndWatermarks` runs it through `env.execute()` successfully (EVID-S6-007). Recorded as a doc/comment drift; not a code defect.
