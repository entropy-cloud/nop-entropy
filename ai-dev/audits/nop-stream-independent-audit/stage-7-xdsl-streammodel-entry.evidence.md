# Stage 7 — XDSL StreamModel Entry Audit Evidence

> Status: produced by Stage 7 audit (plan `2026-08-08-0010-2-xdsl-streammodel-entry-audit.md`)
> Domain: manifest b/c/f/g (XDSL node surface + stream.xml overlays + example + test lane)
> Scope: non-Delta XDSL entry path — `DslModelParser → flow.model.StreamModel → StreamModelDslBuilder → StreamExecutionEnvironment → DataStream API → StreamGraphGenerator`. Delta overlay behavior itself is Stage 8.
> Lane policy: only `in-process` lane (single-JVM `.stream.xml` parse → `StreamModelDslBuilder.of(model).build()` → `env.execute()` → sink output) or stronger is credited for system-capability claims; build-only / component-level evidence is `component-only`; code-trace-only is `component-only` with `manual-trace` proof; no evidence is `unverified`.
> Validator: `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` (parses `@@EVIDENCE` rows from `*.evidence.md` direct children of this dir)

## Support / Reject XDSL Node Matrix (frozen by this audit)

This matrix freezes the disposition of every XDSL node declared in `stream.xdef`
(`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef`) with respect to
the **non-Delta XDSL entry path** compiled by `StreamModelDslBuilder`. It changes neither the
11 evidence-row fields nor the 7-value disposition vocabulary.

### Supported transforms (compiled to a real DataStream-API call — `stream.xdef:108-195`)

| Transform | xdef anchor | Builder dispatch | AdvancedTransforms |
| --- | --- | --- | --- |
| source | `:112` | `StreamModelDslBuilder.buildSource():313` | — |
| map | `:132` | `buildMap():329` | — |
| filter | `:142` | `buildFilter():344` | — |
| flatMap | `:137` | `buildFlatMap():359` | — |
| keyBy | `:147` | `buildKeyBy():374` | — |
| sink | `:170` | `buildSink():384` | — |
| window | `:150` | `buildTransform():266` → adv | `buildWindow():109` |
| aggregate | `:156` | → adv | `buildAggregate():161` |
| reduce | `:159` | → adv | `buildReduce():182` |
| process | `:164` | → adv | `buildProcess():215` |
| cep | `:167` | → adv | `buildCep():282` |
| custom | `:188` | → adv | `buildCustom():257` |
| timestampsAndWatermarks | `:124` | → adv | `buildTimestampsAndWatermarks():336` |

### Fail-fast transforms (throw `UnsupportedOperationException` — runtime API gap)

| Transform | Anchor | Reason |
| --- | --- | --- |
| union | `AdvancedTransforms.buildUnion():244-250` | `DataStream.union()` multi-input not in nop-stream-core runtime |
| sideOutput | `AdvancedTransforms.buildSideOutput():322-329` | `SingleOutputStreamOperator.getSideOutput(OutputTag)` not in runtime |
| unknown transform type | `AdvancedTransforms.build():100-101` | defensive guard; unreachable via valid XDSL parse (xdef enumerates all subtypes) |

### Fail-fast top-level registries / callbacks (`StreamModelDslBuilder.failFastOnUnsupportedRegistries():156-189`)

| Registry | Anchor | Registry | Anchor |
| --- | --- | --- | --- |
| streams | `:157-160` | requirements | `:169-172` |
| sideInputs | `:161-164` | checkpointParticipants | `:173-176` |
| environments | `:165-168` | onStart/onEnd/onError | `:177-180` |
| schemas | `:181-184` | coders | `:185-188` |

---

## Equivalence Criteria Reference (reused from frozen Stage 6)

Stage 6 (`stage-6-java-api-graph-local.evidence.md`) froze three observable invariants that this
audit reuses for the XDSL entry path. They are **criteria text only**; this audit classifies the
XDSL entry path against them and cites them by reference (no re-freezing):

- **(a) Topology equivalence** — Java entry vs compiled StreamGraph/JobGraph: one-to-one
  correspondence between user `Transformation` nodes and `StreamNode`s, and between input→transform
  edges and `StreamEdge`s. Stage 6 proved this for the **Java** entry (`TestEndToEndPipeline#testCompletePipelineTransformation`).
- **(b) Stable identity** — `Transformation.getId()` → `StreamNode.getId()` → `JobVertex` id
  (`vertex-<id>`) → `GraphExecutionPlan` execution-vertex key. Stage 6 proved this chain is
  continuous on the **Java** path (`TestParallelismLockedPropagation`).
- **(c) Recovery inputs** — LOCAL-mode recovery input criteria (frozen for Stage 9; NOT exercised here).

**Transitivity caveat (honest adjudication):** `TestDagTopologyConsistency` proves XDSL `<transforms>`/`<edges>`
produce the **same `Transformation` DAG topology** as the Java API at the **build-only** layer (it
does **not** call `execute()` and compares only the `Transformation` list, not `StreamGraph`/`JobGraph`
nodes/edges). Combined with Stage 6's Java→graph proof, XDSL ≡ graph holds **transitively**, but the
repo has **no direct XDSL → `execute()` → StreamGraph/JobGraph topology assertion test**, so topology
rows are `component-only`, not `e2e-proved`.

---

## Evidence Rows

### Phase 1 — Supported XDSL transform nodes (source → transform → sink)

@@EVIDENCE
inventory_id: EVID-S7-001
source_anchor: nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:112-121
declared_guarantee: <source> registers a SourceFunction (bean or inline xpl) and emits elements into the XDSL-compiled pipeline end-to-end through LOCAL execution
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:313-326
runtime_wiring: wired
positive_proof: TestStreamModelDslBuilderE2E#streamXmlEndToEndProducesExpectedSinkOutput
rejection_proof: TestStreamModelDslBuilderFailFast#unionTransformThrowsUnsupportedOperationException
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S7-002
source_anchor: nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:132-134
declared_guarantee: <map> applies a one-to-one MapFunction (bean or inline xpl) per element end-to-end through the XDSL-compiled pipeline
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:329-341
runtime_wiring: wired
positive_proof: TestStreamModelDslBuilderE2E#streamXmlEndToEndProducesExpectedSinkOutput
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P2-5
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S7-003
source_anchor: nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:142-144
declared_guarantee: <filter> retains only elements for which the inline-xpl predicate returns true, end-to-end through the XDSL-compiled pipeline
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:344-356
runtime_wiring: wired
positive_proof: TestStreamModelDeltaExtends#xExtendsDeltaProducesDifferentSinkOutput
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S7-004
source_anchor: nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:137-139
declared_guarantee: <flatMap> emits zero-or-more outputs per input via inline-xpl, compiled by buildFlatMap through the XDSL entry path
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:359-371
runtime_wiring: partial
positive_proof: manual-trace:nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:278-280
rejection_proof: none
environment_class: unit
required_lane: in-process
finding_id: M7-2-P2-5
disposition: component-only
@@END

@@EVIDENCE
inventory_id: EVID-S7-005
source_anchor: nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:147
declared_guarantee: <keyBy> partitions the stream by the keyExpr KeySelector so downstream keyed aggregations are consistent, end-to-end through the XDSL-compiled pipeline
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:374-381
runtime_wiring: wired
positive_proof: TestAdvancedPipelineE2E#reducePipelineProducesCorrectAggregatedOutput
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S7-006
source_anchor: nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:150-153
declared_guarantee: <window> resolves a windowingStrategies strategy and applies a WindowAssigner on a KeyedStream upstream to produce a WindowedStream via the XDSL entry path
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:109-127
runtime_wiring: partial
positive_proof: TestAdvancedTransforms#windowTransformProducesWindowedStreamInRegistry
rejection_proof: TestAdvancedTransforms#windowRejectsNonKeyedUpstream
environment_class: unit
required_lane: in-process
finding_id: none
disposition: component-only
@@END

@@EVIDENCE
inventory_id: EVID-S7-007
source_anchor: nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:156
declared_guarantee: <aggregate> applies an AggregateFunction bean on a WindowedStream upstream via the XDSL entry path
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:161-179
runtime_wiring: partial
positive_proof: TestAdvancedTransforms#aggregateDispatchesToWindowedAggregatePath
rejection_proof: TestAdvancedTransforms#aggregateWithoutBeanFailsFast
environment_class: unit
required_lane: in-process
finding_id: none
disposition: component-only
@@END

@@EVIDENCE
inventory_id: EVID-S7-008
source_anchor: nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:159-161
declared_guarantee: <reduce> applies a ReduceFunction (bean or inline xpl) on a KeyedStream upstream and emits running reductions end-to-end through the XDSL-compiled pipeline
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:182-208
runtime_wiring: wired
positive_proof: TestAdvancedPipelineE2E#reducePipelineProducesCorrectAggregatedOutput
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S7-009
source_anchor: nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:164
declared_guarantee: <process> applies a ProcessFunction/KeyedProcessFunction bean on a DataStream/KeyedStream upstream via the XDSL entry path
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:215-238
runtime_wiring: partial
positive_proof: TestAdvancedTransforms#processOnDataStreamProducesSingleOutputStreamOperator
rejection_proof: none
environment_class: unit
required_lane: in-process
finding_id: M7-2-P2-5
disposition: component-only
@@END

@@EVIDENCE
inventory_id: EVID-S7-010
source_anchor: nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:167
declared_guarantee: <cep> resolves a patterns pattern, builds it via CepPatternBuilder, and applies a PatternProcessFunction bean on a KeyedStream upstream via the XDSL entry path
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:282-314
runtime_wiring: partial
positive_proof: TestAdvancedTransforms#cepTransformBuildsPatternAndDispatches
rejection_proof: TestAdvancedTransforms#cepRejectsNonKeyedUpstream
environment_class: unit
required_lane: in-process
finding_id: M7-2-P2-1
disposition: component-only
@@END

@@EVIDENCE
inventory_id: EVID-S7-011
source_anchor: nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:188-193
declared_guarantee: <custom> injects a bean-resolved OneInputStreamOperator as a named custom operator on a DataStream upstream via the XDSL entry path
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:257-275
runtime_wiring: partial
positive_proof: TestAdvancedTransforms#customTransformProducesSingleOutputStreamOperator
rejection_proof: TestAdvancedTransforms#customWithoutCustomTypeRejectedByXdef
environment_class: unit
required_lane: in-process
finding_id: M7-2-P2-5
disposition: component-only
@@END

@@EVIDENCE
inventory_id: EVID-S7-012
source_anchor: nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:124-129
declared_guarantee: <timestampsAndWatermarks> assigns event-time timestamps via inline-xpl/WatermarkStrategy on a DataStream upstream via the XDSL entry path
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:336-354
runtime_wiring: partial
positive_proof: TestAdvancedTransforms#timestampsAndWatermarksProducesSingleOutputStreamOperator
rejection_proof: none
environment_class: unit
required_lane: in-process
finding_id: none
disposition: component-only
@@END

@@EVIDENCE
inventory_id: EVID-S7-013
source_anchor: nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:170-178
declared_guarantee: <sink> registers a terminal SinkFunction (bean or inline xpl) that receives elements end-to-end through the XDSL-compiled pipeline
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:384-396
runtime_wiring: wired
positive_proof: TestStreamModelDslBuilderE2E#streamXmlEndToEndProducesExpectedSinkOutput
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

### Phase 2 — XDSL → Java/graph trace & topology/stable-identity equivalence

@@EVIDENCE
inventory_id: EVID-S7-014
source_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:106-118
declared_guarantee: build() compiles a parsed flow.model.StreamModel into a StreamExecutionEnvironment whose DataStream-API call chain yields a Transformation DAG equivalent to the Java entry, then StreamGraphGenerator.generate() compiles it to StreamGraph
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/graph/StreamGraphGenerator.java:110-126
runtime_wiring: wired
positive_proof: TestDagTopologyConsistency#xdslAndJavaApiProduceSameDagTopology
rejection_proof: none
environment_class: unit
required_lane: in-process
finding_id: M8-2-P2-17
disposition: component-only
@@END

@@EVIDENCE
inventory_id: EVID-S7-015
source_anchor: nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:103-108
declared_guarantee: XDSL <transforms> id (xdef:key-attr="id") is the stable identifier that propagates from parse through build to the compiled graph so a plan vertex can recover its originating transform
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:260-263
runtime_wiring: partial
positive_proof: manual-trace:nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:260-263
rejection_proof: none
environment_class: unit
required_lane: in-process
finding_id: none
disposition: component-only
@@END

@@EVIDENCE
inventory_id: EVID-S7-016
source_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:124-150
declared_guarantee: applyCheckpointConfig() applies the XDSL <checkpoint> enabled/interval/processingGuarantee/timeout/maxConcurrentCheckpoints/minPause/maxRetainedCheckpoints/jobTerminationMode fields to the StreamExecutionEnvironment/CheckpointConfig
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:124-150
runtime_wiring: partial
positive_proof: manual-trace:nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:124-150
rejection_proof: none
environment_class: unit
required_lane: in-process
finding_id: none
disposition: component-only
@@END

### Phase 3 — Unsupported XDSL node fail-fast coverage matrix

@@EVIDENCE
inventory_id: EVID-S7-017
source_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:244-250
declared_guarantee: <union> fails fast with UnsupportedOperationException because DataStream.union() multi-input is not in the nop-stream-core runtime
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:244-250
runtime_wiring: unwired
positive_proof: none
rejection_proof: TestStreamModelDslBuilderFailFast#unionTransformThrowsUnsupportedOperationException
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: fail-fast
@@END

@@EVIDENCE
inventory_id: EVID-S7-018
source_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:322-329
declared_guarantee: <sideOutput> fails fast with UnsupportedOperationException because SingleOutputStreamOperator.getSideOutput(OutputTag) is not in the nop-stream-core runtime
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:322-329
runtime_wiring: unwired
positive_proof: none
rejection_proof: TestStreamModelDslBuilderFailFast#sideOutputTransformThrowsUnsupportedOperationException
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: fail-fast
@@END

@@EVIDENCE
inventory_id: EVID-S7-019
source_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:157-160
declared_guarantee: <streams> registry fails fast with UnsupportedOperationException (no execution consumer) rather than being silently ignored
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:157-160
runtime_wiring: unwired
positive_proof: none
rejection_proof: TestStreamModelDslBuilderFailFast#streamsRegistryFailsFast
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: fail-fast
@@END

@@EVIDENCE
inventory_id: EVID-S7-020
source_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:161-164
declared_guarantee: <sideInputs> registry fails fast with UnsupportedOperationException (no execution consumer)
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:161-164
runtime_wiring: unwired
positive_proof: none
rejection_proof: TestStreamModelDslBuilderFailFast#sideInputsRegistryFailsFast
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: fail-fast
@@END

@@EVIDENCE
inventory_id: EVID-S7-021
source_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:165-168
declared_guarantee: <environments> registry fails fast with UnsupportedOperationException (no execution consumer)
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:165-168
runtime_wiring: unwired
positive_proof: none
rejection_proof: TestStreamModelDslBuilderFailFast#environmentsRegistryFailsFast
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: fail-fast
@@END

@@EVIDENCE
inventory_id: EVID-S7-022
source_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:181-184
declared_guarantee: <schemas> registry fails fast with UnsupportedOperationException (no execution consumer)
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:181-184
runtime_wiring: unwired
positive_proof: none
rejection_proof: TestStreamModelDslBuilderFailFast#schemasRegistryFailsFast
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: fail-fast
@@END

@@EVIDENCE
inventory_id: EVID-S7-023
source_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:185-188
declared_guarantee: <coders> registry fails fast with UnsupportedOperationException (no execution consumer)
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:185-188
runtime_wiring: unwired
positive_proof: none
rejection_proof: TestStreamModelDslBuilderFailFast#codersRegistryFailsFast
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: fail-fast
@@END

@@EVIDENCE
inventory_id: EVID-S7-024
source_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:169-172
declared_guarantee: <requirements> declarations fail fast with UnsupportedOperationException (no builder-side consumer)
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:169-172
runtime_wiring: unwired
positive_proof: none
rejection_proof: none
environment_class: none
required_lane: in-process
finding_id: none
disposition: unverified
@@END

@@EVIDENCE
inventory_id: EVID-S7-025
source_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:173-176
declared_guarantee: <checkpointParticipants> declarations fail fast with UnsupportedOperationException (no builder-side consumer)
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:173-176
runtime_wiring: unwired
positive_proof: none
rejection_proof: none
environment_class: none
required_lane: in-process
finding_id: none
disposition: unverified
@@END

@@EVIDENCE
inventory_id: EVID-S7-026
source_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:177-180
declared_guarantee: <onStart>/<onEnd>/<onError> lifecycle callbacks fail fast with UnsupportedOperationException (no execution consumer)
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:177-180
runtime_wiring: unwired
positive_proof: none
rejection_proof: none
environment_class: none
required_lane: in-process
finding_id: none
disposition: unverified
@@END

@@EVIDENCE
inventory_id: EVID-S7-027
source_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:100-101
declared_guarantee: an unknown transform subtype fails fast with UnsupportedOperationException (defensive guard; unreachable via a valid XDSL parse because stream.xdef enumerates every bean sub-type)
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:100-101
runtime_wiring: unwired
positive_proof: none
rejection_proof: manual-trace:nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:100-101
environment_class: unit
required_lane: in-process
finding_id: none
disposition: fail-fast
@@END

### Phase 4 — Production-wiring gap, demo dangling transforms & checkpoint config unused fields

@@EVIDENCE
inventory_id: EVID-S7-028
source_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:94-100
declared_guarantee: XDSL entry path is invokable only from test code; no production loader/dispatcher/bean in main/ loads a .stream.xml (grep of nop-stream/**/src/main/**/*.java for DslModelParser, parseFromResource, StreamModelDslBuilder.of, .stream.xml returns ZERO main/ hits; demo fraud-detection.stream.xml has no Java driver class)
implementation_anchor: none
runtime_wiring: partial
positive_proof: none
rejection_proof: none
environment_class: none
required_lane: in-process
finding_id: none
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S7-029
source_anchor: nop-stream/nop-stream-fraud-example/src/main/resources/_vfs/nop/stream/demo/fraud-detection.stream.xml
declared_guarantee: demo contains dangling transforms split-map (flatMap) and sum-reduce (reduce) that no <edge> references; if the demo were loaded and built, buildTransforms() topological sort would place them as zero-upstream and requireSingleInput would throw IllegalArgumentException("found 0") — a latent build-time fail-fast
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:292-306
runtime_wiring: unwired
positive_proof: none
rejection_proof: manual-trace:nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:292-306
environment_class: unit
required_lane: in-process
finding_id: none
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S7-030
source_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/model/CheckpointConfigModel.java
declared_guarantee: <checkpoint> fields storageConfig/storageType/barrierAlignmentTimeout/maxConsecutiveCheckpointFailures/jobId/pipelineId are parsed by the XDSL model (generated _CheckpointConfigModel) but NEVER applied by applyCheckpointConfig() to the StreamExecutionEnvironment — parsed but unused
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:124-150
runtime_wiring: partial
positive_proof: manual-trace:nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:124-150
rejection_proof: none
environment_class: unit
required_lane: in-process
finding_id: none
disposition: residual-risk
@@END

---

## Cross-Reference Notes (corpus findings touched by this audit — final disposition owned by Stages 19-22)

- **M7-2-P2-1** (`nop-stream-flow/pom.xml` depends on `nop-stream-cep`, contradicting README/architecture):
  this audit confirms the dependency is **load-bearing for the XDSL entry path** — `AdvancedTransforms`
  imports `io.nop.stream.cep.*` (`CEP`, `PatternStream`, `CepPatternBuilder`) to implement the `<cep>`
  transform (EVID-S7-010). So the XDSL `<cep>` construct cannot compile without the cep module on the
  flow classpath. Final pom/doc reconciliation owned by Stage 21.
- **M7-2-P2-2** (`flow/model/` duplicate source tree): the XDSL Java binding audited here
  (`io.nop.stream.flow.model.*`) is the hand-authored thin subclass layer; the generated bases live
  under `_gen/` (excluded by manifest rule). This row does not adjudicate the duplicate-tree finding;
  it only records that the XDSL model classes are the binding consumed by `StreamModelDslBuilder`.
  Final disposition owned by Stage 21.
- **M7-2-P2-5** (`UnknownTypeInformation.INSTANCE<?>` cast): still live on the XDSL path — `buildMap`,
  `buildFlatMap`, `buildProcess`, `buildCustom`, `buildCep` all flow into DataStream-API calls that
  cast `UnknownTypeInformation.INSTANCE` to `TypeInformation<R>` (cross-referenced on EVID-S7-002/004/009/011).
  It does not block in-process XDSL execution (the e2e tests pass) but remains a contract defect.
  Final disposition owned by Stage 21.
- **M8-2-P2-17** (README "五层执行管线" vs architecture "六阶段" pipeline-count drift): the XDSL schema
  header itself (`stream.xdef:9-10`) declares the chain
  `StreamModel → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan → GraphExecutionPlan`
  (5 arrows / 6 names), which this audit's trace row (EVID-S7-014) follows. The doc-count drift is a
  Stage 23 doc-contract item; flagged here only.

## Coverage Gaps Found (assigned to successor remediation per roadmap rule)

- **`<flatMap>` lacks an in-process XDSL source-to-sink test.** `buildFlatMap` exists and dispatches
  (EVID-S7-004, `component-only`), structurally identical to the e2e-proved `buildMap`, but no non-demo
  `.stream.xml` exercises `<flatMap>` through `env.execute()` → sink. The fraud demo uses `<flatMap>`
  but it is a dangling transform with no driver (EVID-S7-029). This is a coverage gap, not a confirmed
  live defect; assignable to a test-effectiveness remediation plan. Adding one `<flatMap>` e2e fixture
  would close it.
- **`<window>`/`<aggregate>` cannot execute in-process on the flow test classpath** (no
  `IWindowOperatorFactory`, provided by `nop-stream-runtime`). Their dispatch is proven to the runtime
  boundary (EVID-S7-006/007, `component-only`); end-to-end execution requires the runtime module. This
  is an environment-classification limit, not a defect.
- **`<requirements>`, `<checkpointParticipants>`, `<onStart>/<onEnd>/<onError>` have no rejection test.**
  The fail-fast `throw` exists (EVID-S7-024/025/026, `unverified`), but no test exercises these three
  branches. Coverage gap; adding three inline-XML fail-fast tests would close it.
- **No test traces an XDSL transform `id` to a `StreamNode`/`JobVertex` id** (EVID-S7-015). The XDSL
  `id` is the builder `streamRegistry` key for wiring only; `Transformation.getId()` is an unrelated
  static `AtomicInteger`, so the declarative XDSL `id` does **not** ride the Stage-6 stable-identity
  chain. This is an honest architectural observation, not a defect of this audit's scope.
