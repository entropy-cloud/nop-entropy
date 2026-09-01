# 2026-09-02 Composite Scenario Uncovered Engine Defects

> Status: fixed (item 13 execution)
> Fixing plan: `ai-dev/plans/nop-stream-productization/2026-09-01-2217-2-composite-scenario-local-implementation.md`
> Scope: `nop-stream-core` / `nop-stream-cep` / `nop-stream-connector`

## Problem

Executing the S1/S2 composite scenarios (roadmap item 13) — the first pipeline ever to combine
XDSL declaration + CEP + keyed state + windows + 2PC sinks + checkpoint persistence + restore —
surfaced a cluster of six engine defects. Individually each component had green unit tests;
composed, the pipeline failed at six distinct stages:

1. **JobGraph generation failed** with `unmapped edges` (`ERR_STREAM_INVALID_STATE`) for the
   lineage `CepOperator → keyBy(virtual) → WindowAggregate`.
2. **`KeyedProcessFunction` UDFs threw** `UnsupportedOperationException: Keyed state is only
   available on a keyed stream`.
3. **Every checkpoint persist failed** with `only-data-bean-is-serializable: NFAState` — CEP
   operator state had never been JSON-persistable (documented in
   `TestCepCheckpointRestoreE2E` as backlog P2-INV-6 "not possible in production today").
4. **Java serialization of NFAState silently fell back to raw values** (custom serializer
   threw, `MemoryStateSerDe` logged a WARN and degraded).
5. **Restore of a 2PC sink threw** `ClassCastException: String cannot be cast to Long` in
   `TwoPhaseCommitSinkFunction.restoreFromEpoch`.
6. **Bounded jobs failed non-deterministically** with `Cannot write to a finished
   ResultPartition` during the best-effort final checkpoint (race with natural termination).

## Diagnostic Method

- Symptom 1: added a temporary debug print in `JobGraphGenerator.createJobEdges` naming the
  unmapped endpoints (`KeyBy/factory=PartitionOperatorFactory → WindowAggregate`), then traced
  `identifyChains`/`buildChain`/`findUpstreamVertex` by hand for the fan-out DAG.
- Symptom 3→4: the persist stack showed `writeBean(NFAState)` while the operator's descriptor
  demonstrably carried the custom serializer (verified with a `[DEBUG-CEP]` print). The
  `MemoryStateSerDe` WARN log ("falling back to raw value") then revealed the serializer's
  `serialize()` itself threw — `NotSerializableException` on the PriorityQueue's lambda
  comparator.
- Symptom 5: stack pointed at `entry.getKey()` in `restoreFromEpoch`; the JSON round-trip turns
  `TreeMap<Long,Object>` keys into Strings (JSON object keys are strings).
- Symptom 6: correlated the failure timestamp with `handleJobTermination` →
  `triggerFinalCheckpoint` → `triggerBarrierOnAllInvokables` on already-finished tasks.

## Root Cause

All six share one meta-root-cause: **no pipeline had ever exercised the composed path** —
component-level verification passed while the wiring between layers was broken or unproven
(the classic hollow-composition lesson recorded in the plan guide §Lessons #8).

1. `identifyChains` iterated remaining nodes in HashMap order; a virtual (partition) node whose
   downstream real operator got chained first became an all-virtual chain, and the
   single-level `findUpstreamVertex` returned null when its upstream real operator was not yet
   mapped → S-10 unmapped-edge fail-fast. Additionally, when the upstream mapping DID succeed,
   the cross-vertex JobEdge took the virtual node's outgoing edge partitioner (null) instead of
   the hash partitioner carried by the edge INTO the virtual node — silently degrading keyBy to
   forward at parallelism > 1 (invisible at P=1 where all existing keyed tests ran).
2. `WindowOperator`/`CepOperator` provision their keyed backend in `open()`
   (`stateBackend.createKeyedStateBackend(...)`), but `ProcessOperator` — the operator behind
   the DSL-documented `keyed.process(KeyedProcessFunction)` path — never did.
3. The JSON snapshot path requires `@DataBean` (or a custom `IStreamSerializer`) for every
   state value; the CEP state graph (queues, DeweyNumber, node/edge structures) is neither.
4. `NFAState`'s `PriorityQueue` comparator was a method-reference chain (`Comparator.comparingLong...`)
   which is not `Serializable` — `ObjectOutputStream` fails, and `serializeWithSerializer`'s
   compatibility fallback degraded the snapshot to raw values that the persist layer then
   rejected (see symptom 3).
5. `setPendingCommits` re-wrapped the restored map without key normalization.
6. The best-effort final checkpoint injects barriers into finished tasks; the injection
   forwarded a barrier through a chain whose output partition was already finished.

## Fix

1. `JobGraphGenerator`: remaining-node iteration is now Kahn-topological (deterministic, and a
   virtual node is seen before its downstream so it heads its own chain); `findUpstreamVertex`
   is recursive (visited-set guarded); `resolveJobEdgePartitioner` propagates the partitioner of
   the edge INTO a virtual node to the vertex-boundary JobEdge.
2. `ProcessOperator.open()` provisions `keyedStateBackend` from the configured state backend
   (mirrors the `WindowOperator`/`CepOperator` pattern) and applies deferred restore.
3. CEP state serde: new `JavaStreamSerializer` (`core/typeutils`) registered on the NFA
   ValueState and the SharedBuffer events/entries MapState descriptors; `NFAState`'s comparator
   became a serializable named class; `EventId`/`NodeId` (map keys, JSON path) became
   `@DataBean` POJOs; the CEP state graph classes became `Serializable`.
4. `MemoryStateSerDe.serializeWithSerializer` wraps byte[] payloads in a self-describing
   `{"__java_bytes__": base64}` marker (a raw byte[] silently degrades to an opaque String
   through the JSON persist layer); `deserializeValue` decodes the marker (and bare byte[]) via
   the Java-stream serializer. `MemoryKeyedStateBackend.getState/getMapState` adopt the
   operator-supplied custom serializer onto restored states (restored descriptors are rebuilt
   without it — otherwise the first post-restore snapshot would degrade again).
5. `TwoPhaseCommitSinkFunction.setPendingCommits` normalizes keys to `Long`.
6. `ResultPartition.write` drops checkpoint barriers on finished partitions with a debug log
   (redundant by construction: the executor injects the barrier into every task; data records
   still fail fast).

## Protection

Regression coverage lives with the scenario tests that exposed the defects (all green, plus
full pre-existing suite green — 1516 core / 359 cep / runtime+connectors+flow / 57 fraud-example):

- `TestS1CdcPipelineE2E` / `TestS1CdcRecoveryE2E` / `TestS2FileAggregationE2E` /
  `TestS2RecoveryAndRescaleE2E` / `TestS2OfflineReshardE2E` (defects 1—6 end-to-end),
- `TestS1PatternDeclarations` (XDSL pattern chain semantics incl. the `next=` linkage that the
  old dead `fraud-detection.stream.xml` got wrong),
- `TestDirectoryFileSourceFunction` / `TestReplayableCdcSourceFunction` (cursor/offset
  checkpoint+resume, rescale empty-restore guard, fail-fast paths).

## Lessons

- Component-green ≠ composition-green: the first end-to-end consumer of a documented
  composition (here: DSL `keyed.process` + CEP + 2PC + JSON checkpoint persist) must be treated
  as a hypothesis test of the wiring, not just of the new code.
- The `serializeWithSerializer` raw-fallback WARN is a defect signal, not noise: any "falling
  back to raw value" line in a checkpointing pipeline means the snapshot format has silently
  degraded.
