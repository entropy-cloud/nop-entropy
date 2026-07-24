# 13 StreamModel Rectify

> Plan Status: active
> Plan Type: implementation
> Mission: nop-stream-flink-comparison
> Work Item: roadmap item 13
> Last Reviewed: 2026-07-25
> Source: `docs/backlog/nop-stream-flink-comparison-roadmap.md` Item 13; `ai-dev/analysis/nop-stream/08-gap-analysis.md` gaps G40, G41, G59 (serialization alignment); live code audit 2026-07-25
> Related: `2026-07-25-1600-1-operator-state-basic.md` (Phase 3 serialization bridge depends on Plan 12a's `MemoryOperatorStateBackend` and `MemoryKeyedStateBackend`; other phases independent)

## Purpose

Make `StreamModel` the canonical model of a nop-stream pipeline by: (a) populating `StreamComponents` with all pipeline metadata during graph construction, (b) wiring `StreamModelFingerprint` compile-time validation — `isCompatibleWith()` must be called at pipeline build time to reject incompatible requirement combinations, and (c) bridging serialization by introducing `IStreamSerializer` (extending `TypeSerializer`) with actual `serialize()`/`deserialize()` methods — currently `TypeSerializer` has only copy/clone methods and is unused in production (`JsonTool` used directly).

## Current Baseline

- `StreamModel` (`io.nop.stream.core.model.StreamModel`): **exists** as container wrapping `StreamComponents` and `Map<String, Transformation<?>>`
- `StreamComponents` (plural — `io.nop.stream.core.model.StreamComponents`): **exists** as `@DataBean` with typed Maps: `transforms`, `streams`, `windowingStrategies`, `coders`, `schemas`, `environments`, `sideInputs`, `requirements` (List), `checkpointParticipants` (Set), and `windowOperatorFactory`.
- `StreamComponents` is **populated via constructor** during `StreamModel` creation — NOT via a `buildStreamModel()` method on `StreamGraphGenerator`
- `StreamGraphGenerator.buildStreamGraph()` currently does NOT register pipeline metadata into `StreamModel`/`StreamComponents` — the model is constructed separately and not populated with runtime metadata
- `StreamModelFingerprint`: **exists** with `builder()`, `build()`, `isCompatibleWith()`. Used as parameter in `PartitionedPlanGenerator.generate()` but **no validation call rejects incompatible requirements** — fingerprint is a passive data carrier in the build path (restore-time validation exists in `GraphModelCheckpointExecutor`)
- `TypeSerializer<T>` (`io.nop.stream.core.common.typeutils.TypeSerializer`): **exists** with `isImmutableType()`, `duplicate()`, `createInstance()`, `copy(T)`, `copy(T, T)`, `getLength()`. **No `serialize()` or `deserialize()` methods** — copy-only interface.
- `MemoryStateSerDe` (package-private, `io.nop.stream.core.common.state.backend.memory.MemoryStateSerDe`): **the actual serialization engine** for `MemoryKeyedStateBackend`. Uses `JsonTool` directly. `MemoryKeyedStateBackend` delegates to it (line ~253). Any serializer bridge must thread through `MemoryStateSerDe`.
- `MemoryStateBackend` user-facing store `Map<String, Object>` — serialized by `MemoryStateSerDe.snapshotState()` and deserialized by its `restoreState()` counterpart
- G40: `TypeSerializer` interface unused (Hollow/P2)
- G41: no serializer registry on `StateDescriptor` (P2)
- G59: no schema versioning (P3)

## Goals

- Populate `StreamComponents` during `StreamGraphGenerator.buildStreamGraph()`: register transforms, streams, windowing strategies, requirements, checkpoint participants
- Wire `StreamModelFingerprint.isCompatibleWith()` call in `PartitionedPlanGenerator` (or equivalent) to reject incompatible requirement combinations at pipeline build time
- Introduce `IStreamSerializer<T>` extending `TypeSerializer<T>` with `serialize()`/`deserialize()` — the bridge between existing `TypeSerializer` contract and actual serialization
- Implement `JsonToolSerializer<T>` as default `IStreamSerializer` wrapping `JsonTool`
- Thread serializer awareness through `MemoryStateSerDe` and `StateDescriptor` so state backends use `IStreamSerializer` instead of raw `JsonTool`
- Update owner docs and `source-anchors.md`

## Non-Goals

- Full `TypeSerializerSnapshot` compatibility/schema evolution (G12 — separate plan)
- StateShard → Key-Group migration (G37-G39 — separate plan)
- XDSL declarative pipeline definition (Phase 5)

## Scope

### In Scope

- **StreamComponents population in StreamGraphGenerator**: during `buildStreamGraph()`, register each transform, stream edge, windowing strategy, requirement, and checkpoint participant in `StreamComponents`. Attach populated `StreamModel` to `StreamGraph`/`JobGraph`.
- **StreamModelFingerprint validation**: wire `isCompatibleWith()` call in pipeline build path. Fingerprint computation logic (via `builder`) already exists — only the validation call is missing.
- **IStreamSerializer<T> interface**: extends `TypeSerializer<T>` with `byte[] serialize(T value)` and `T deserialize(byte[] data, Class<T> type)`. This is Option B from the Gap Analysis design decisions (subinterface, not modifying `TypeSerializer`).
- **JsonToolSerializer<T>**: implements `IStreamSerializer<T>` using `JsonTool.serialize()`/`JsonTool.parse()`. Default serializer when no custom one is set.
- **StateDescriptor serializer reference**: both `StateDescriptor` (keyed) and `OperatorStateDescriptor` carry a `TypeSerializer` reference. Default `JsonToolSerializer`. Serializer-aware backend code prefers `IStreamSerializer` when available.
- **MemoryStateSerDe serializer bridge**: thread serializer through `MemoryStateSerDe` so it uses `IStreamSerializer` from descriptor instead of raw `JsonTool`. Fall back to `JsonTool.serialize()`/`JsonTool.parse()` for entries without descriptors.
- **MemoryOperatorStateBackend serializer bridge** (from Plan 12a): same approach as `MemoryStateSerDe` — use `IStreamSerializer` from descriptor if available.
- **Focused tests**:
  - `IStreamSerializer`: `JsonToolSerializer` round-trip
  - Serializer-aware `MemoryOperatorStateBackend`: custom serializer → correct serialization
  - Serializer-aware `MemoryStateSerDe`: keyed state with custom serializer
  - `StreamModelFingerprint`: incompatible requirements rejected
  - StreamComponents population: expected entries present after `buildStreamGraph()`
- **Owner-doc updates**: `ai-dev/design/nop-stream/` model docs; `source-anchors.md`

### Out Of Scope

- `TypeSerializerSnapshot` compatibility system (G12)
- `BroadcastState` type (12b)

## Execution Plan

### Phase 1 — StreamComponents population in graph construction

Status: planned
Targets:
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/graph/StreamGraphGenerator.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/graph/StreamGraph.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/model/StreamModel.java`

Item Types: `Fix | Proof`

- [ ] `Proof` Audit `StreamGraphGenerator.buildStreamGraph()`: map each component creation site to `StreamComponents` registration. Identify: which transforms create which types; stream edge creation; `WindowOperator`-bound transforms for windowing strategies; `CheckpointParticipant` transforms for checkpoint participants.
- [ ] `Fix` In `StreamGraphGenerator`, after/before transform iteration: populate `StreamModel.streamComponents` with registered:
  - transforms: each `Transformation.getUid()` → `Transformation` instance
  - streams: each edge → edge metadata
  - windowing strategies: when `WindowOperator`-bound transform is added
  - requirements: from `StreamExecutionEnvironment` + per-operator requirements
  - checkpoint participants: from transforms implementing `CheckpointParticipant`
- [ ] `Fix` Ensure `StreamGraph` carries the populated `StreamModel` (add field if missing)
- [ ] `Fix` Ensure `StreamModel` propagates to `JobGraph` (add field if missing)
- [ ] Add focused test: after `buildStreamGraph()`, `StreamGraph.getStreamModel().getComponents()` has expected entries with non-empty maps for each registered type

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `StreamGraphGenerator.buildStreamGraph()` populates `StreamComponents` with transforms, streams, windowing strategies, requirements, and checkpoint participants
- [ ] `StreamGraph.getStreamModel()` returns populated model
- [ ] `StreamModel` propagates through `StreamGraph` → `JobGraph`
- [ ] Focused test verifies each component category has expected entries
- [ ] **No Silent No-Op**: registration is explicit per component — no "skip if null" fallback
- [ ] `./mvnw compile -pl nop-stream/nop-stream-core -am` passes
- [ ] No owner-doc update required (Phase 4 handles docs)
- [ ] `ai-dev/logs/` corresponding date entry updated

### Phase 2 — StreamModelFingerprint compile-time validation

Status: planned
Targets:
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/model/StreamModelFingerprint.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/graph/PartitionedPlanGenerator.java` (or `JobGraphGenerator`)

Item Types: `Fix | Proof`

- [ ] `Proof` Audit current `StreamModelFingerprint` usage: trace `StreamModel.computeFingerprint()` → `PartitionedPlanGenerator.generate()` accepts `Fingerprint`. Confirm where the validation gap is: the fingerprint exists as a data carrier but `isCompatibleWith()` is never called.
- [ ] `Proof` Audit `StreamModelFingerprint.builder()`: confirm fingerprint computation (sorted canonical requirement signatures, hash) is deterministic and correct.
- [ ] `Fix` In the pipeline build path (likely `PartitionedPlanGenerator` or `JobGraphGenerator`): after fingerprint is computed, call `fingerprint.isCompatibleWith(requiredFingerprint)`. On incompatibility, throw `NopException` with clear message: "StreamModel requirements incompatible: <detail>".
- [ ] Add focused test: incompatible requirement combination → validation rejects with descriptive error
- [ ] Add focused test: compatible requirement combination → validation passes
- [ ] Add focused test: `StreamModelFingerprint` is deterministic (same model → same fingerprint)

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] Fingerprint validation is wired in pipeline build path
- [ ] Incompatible requirements produce descriptive `NopException`
- [ ] Compatible requirements allow pipeline build to proceed
- [ ] Fingerprint is deterministic
- [ ] **接线验证**: build pipeline → `computeFingerprint()` → `isCompatibleWith()` → throw on mismatch
- [ ] **No Silent No-Op**: validation actually throws — not a no-op check
- [ ] `./mvnw test -pl nop-stream/nop-stream-core -am` passes
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` corresponding date entry updated

### Phase 3 — IStreamSerializer interface + JsonToolSerializer + MemoryStateSerDe bridge

Status: planned
Targets:
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/typeutils/TypeSerializer.java` (reference, not modified)
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/typeutils/` (IStreamSerializer)
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/StateDescriptor.java` (add serializer ref)
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java` (serializer-aware serialization)
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryOperatorStateBackend.java` (from Plan 12a)

Item Types: `Fix | Decision`

- [ ] `Decision` Interface design: `IStreamSerializer<T>` extends `TypeSerializer<T>` with `byte[] serialize(T value, OutputStream out)` and `T deserialize(InputStream in, Class<T> type)` — or simpler `byte[] serialize(T)` / `T deserialize(byte[])`. Recommended: `byte[] serialize(T)` / `T deserialize(byte[], Class<T>)` for simplicity (avoid stream abstraction).
- [ ] `Fix` Create `IStreamSerializer<T>` extending `TypeSerializer<T>`: declare `byte[] serialize(T value)` and `T deserialize(byte[] data, Class<T> type)`.
- [ ] `Fix` Create `JsonToolSerializer<T>` implementing `IStreamSerializer<T>`: `serialize()` → `JsonTool.serialize(value).getBytes(StandardCharsets.UTF_8)`. `deserialize()` → `JsonTool.parse(new String(data, StandardCharsets.UTF_8), type)`.
- [ ] `Fix` Add `TypeSerializer<T> getSerializer()` / `void setSerializer(TypeSerializer<T>)` to `StateDescriptor`. Default: `JsonToolSerializer` instance.
- [ ] `Fix` Thread serializer through `MemoryStateSerDe`: modify `snapshotState()`/`restoreState()` to use `IStreamSerializer.serialize()`/`deserialize()` from each `StateDescriptor` when serializing keyed state. For states without descriptors (raw `Map.Entry<String, Object>`), fall back to `JsonTool` directly. This is the key change that bridges G40.
- [ ] `Fix` Thread serializer through `MemoryOperatorStateBackend` (from Plan 12a): use `IStreamSerializer` from `OperatorStateDescriptor` or `ListStateDescriptor`. Fall back to `JsonToolSerializer`.
- [ ] Add focused test: `JsonToolSerializer` round-trip (serialize → deserialize → `equals`)
- [ ] Add focused test: `MemoryStateSerDe` with custom `IStreamSerializer` → state correctly serialized using custom serializer
- [ ] Add focused test: `MemoryOperatorStateBackend` with custom `IStreamSerializer` → state correctly serialized
- [ ] Add regression test: no serializer set → `JsonToolSerializer` used → backward compatible behavior

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `IStreamSerializer<T>` exists with `serialize()`/`deserialize()` extending `TypeSerializer`
- [ ] `JsonToolSerializer` implements `IStreamSerializer` wrapping `JsonTool`
- [ ] `StateDescriptor` carries `TypeSerializer` reference (default `JsonToolSerializer`)
- [ ] `MemoryStateSerDe` uses `IStreamSerializer` from descriptor when available (not raw `JsonTool` call)
- [ ] `MemoryOperatorStateBackend` uses `IStreamSerializer` from descriptor
- [ ] Backward compatibility: no custom serializer → `JsonToolSerializer` → same behavior as before
- [ ] **Anti-Hollow Check**: `IStreamSerializer.serialize()`/`deserialize()` is actually called in the serialization path (verified by test assertion — not just typed)
- [ ] `./mvnw test -pl nop-stream/nop-stream-core -am` passes
- [ ] No owner-doc update required (Phase 4 handles docs)
- [ ] `ai-dev/logs/` corresponding date entry updated

### Phase 4 — Owner-doc synchronization + source-anchors

Status: planned
Targets:
- `ai-dev/design/nop-stream/` (model/serialization docs — flat directory, no `model/` subdirectory)
- `docs-for-ai/04-reference/source-anchors.md`

Item Types: `Fix | Follow-up`

- [ ] `Proof` Audit `ai-dev/design/nop-stream/` for architecture docs describing model layer. Update to reflect current `StreamModel` + `StreamComponents` + fingerprint validation design.
- [ ] `Fix` Add/update design doc at `ai-dev/design/nop-stream/` (use existing file if available, create if this topic lacks coverage): `StreamModel` population, `StreamModelFingerprint` validation, `IStreamSerializer` bridge.
- [ ] `Fix` Add nop-stream anchor entries to `source-anchors.md`:
  - `StreamModel`, `StreamComponents`, `StreamModelFingerprint`
  - `JobGraphGenerator`, `PartitionedPlanGenerator`
  - `WindowOperator`, `WindowOperatorBuilder`
  - `PendingCheckpoint`, `CheckpointCoordinator`
  - `HeapInternalTimerService`
  - Other major classes missing coverage
- [ ] Run: `node ai-dev/tools/check-doc-links.mjs --strict` — must exit 0

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `ai-dev/design/nop-stream/` accurately describes current `StreamModel` + `StreamComponents` + fingerprint validation + `IStreamSerializer` bridge
- [ ] `source-anchors.md` has nop-stream entries for major classes
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0
- [ ] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

- [ ] G40 addressed: `IStreamSerializer` bridge makes `TypeSerializer` actually used in serialization
- [ ] G41 addressed: `StateDescriptor` carries serializer reference
- [ ] G59 groundwork: `IStreamSerializer` provides basis for schema versioning (future work)
- [ ] `StreamComponents` populated during graph construction with all major component types
- [ ] `StreamModelFingerprint.isCompatibleWith()` validation wired and rejects incompatible requirements
- [ ] `MemoryStateSerDe` uses `IStreamSerializer` from descriptors instead of raw `JsonTool`
- [ ] Backward compatibility maintained
- [ ] Owner docs synchronized; `source-anchors.md` has nop-stream entries
- [ ] No in-scope live defect deferred to follow-up
- [ ] Independent sub-agent closure-audit completed and evidence recorded
- [ ] **Anti-Hollow Check**: (a) `StreamComponents` is populated by graph construction (not dead data), (b) fingerprint validation throws on mismatch, (c) `IStreamSerializer` is called in serialization path
- [ ] `./mvnw compile -pl nop-stream/nop-stream-core -am`
- [ ] `./mvnw test -pl nop-stream/nop-stream-core -am`
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <this-plan-file> --strict` exits 0

## Deferred But Adjudicated

### Full TypeSerializerSnapshot compatibility system (G12)

- Classification: `optimization candidate`
- Why Not Blocking Closure: `IStreamSerializer` bridge introduces serializer usage. Full schema evolution is additive on top.
- Successor Required: `yes`

### StateShard → Key-Group migration (G37-G39)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Independent state backend architecture decision.
- Successor Required: `yes`

## Non-Blocking Follow-ups

- (none at draft time)

## Closure

Status Note: <<filled on completion>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<independent reviewer>>
- Evidence: <<PASS/FAIL results for each exit criterion and closure gate>>
