# nop-stream 现有实现审计

> Status: open
> Date: 2026-07-31
> Scope: nop-stream-core, nop-stream-runtime, nop-stream-cep, nop-stream-connector (+batch, +debezium)
> Parent: `docs/backlog/nop-stream-flink-comparison-roadmap.md` Item 2
> Deliverable For: `ai-dev/plans/317-nopstream-live-audit.md`

## 0. Cross-Reference: This Audit (6 Areas) → Flink Source Audit (7 Areas)

| This Audit Section | Flink Source Audit Section (01-flink-source-audit.md) | Matching Notes |
|-------------------|------------------------------------------------------|----------------|
| §1 — nop-stream-core (DataStream API, Transformation, graph) | §1 — Streaming API | Direct mapping: DataStream interface hierarchy, Transformation DAG, StreamGraph/JobGraph generation |
| §1 — nop-stream-core (runtime execution) | §2 — Runtime Execution | Task, TaskExecutor, StreamTaskInvokable, RecordWriter, InputGate, InputChannel in core module |
| §5 — Checkpoint & Watermark | §3 — Checkpoint | CheckpointCoordinator, PendingCheckpoint, BarrierAligner, EpochManifest — checkpoint subsystem |
| §5 — Watermark subsystem | §5 — Window/Time | TimestampsAndWatermarksOperator, WatermarkStrategy, WatermarkGenerator, timer service |
| §3 — nop-stream-cep | §6 — CEP | CepOperator, NFA, SharedBuffer, state backend integration |
| §2 — nop-stream-runtime (distributed execution) | §7 — Distributed Execution | TaskManager, JobCoordinator, EmbeddedDistributedExecutor, cluster/transport layer |
| §4 — nop-stream-connector | — | Connector adapters have no direct Flink equivalent; Flink's connector SDK is more extensive |
| §5 — State Management | §4 — State System | IStateBackend, MemoryKeyedStateBackend, state types, StateShard, checkpoint serialization |

---

## 1. nop-stream-core

### 1.1 DataStream API Coverage

**Status: FULLY IMPLEMENTED** — DataStream interface hierarchy is complete and operational.

#### Interface Hierarchy (all fully defined)

| Interface | File | Lines | Status |
|-----------|------|-------|--------|
| `DataStream<T>` | `datastream/DataStream.java` | 142 | **Fully defined** — 13 methods |
| `SingleOutputStreamOperator<T>` | `datastream/SingleOutputStreamOperator.java` | 34 | **Fully defined** — adds `forceNonParallel()` |
| `DataStreamSource<T>` | `datastream/DataStreamSource.java` | 30 | **Interface only** — no concrete impl in core |
| `KeyedStream<T, KEY>` | `datastream/KeyedStream.java` | 111 | **Fully defined** — 12 methods |
| `WindowedStream<T, K, W>` | `datastream/WindowedStream.java` | 88 | **Fully defined** — 7 methods |

#### Concrete Implementations (all fully implemented)

| Class | File | Lines | Status |
|-------|------|-------|--------|
| `DataStreamImpl<T>` | `datastream/DataStreamImpl.java` | 372 | **Fully implemented** |
| `SingleOutputStreamOperatorImpl<T>` | `datastream/SingleOutputStreamOperatorImpl.java` | 59 | **Fully implemented** |
| `KeyedStreamImpl<T, KEY>` | `datastream/KeyedStreamImpl.java` | 343 | **Fully implemented** |
| `WindowedStreamImpl<T, K, W>` | `datastream/WindowedStreamImpl.java` | 261 | **Conditional** — requires runtime for window ops |

#### Findings

| # | Severity | Finding | File:Line |
|---|----------|---------|-----------|
| CORE-F1 | **LOW** | `DataStreamImpl.map()` type inference falls back to `null` when type not inferrable | `datastream/DataStreamImpl.java:337` |
| CORE-F2 | **LOW** | `DataStreamImpl.flatMap()` and `process()` always use `UnknownTypeInformation` | `datastream/DataStreamImpl.java:183,204` |
| CORE-F3 | **LOW** | `KeyedStreamImpl.unsafe cast` in dual constructors risks NPE | `datastream/KeyedStreamImpl.java:62-63` |
| CORE-F4 | **LOW** | `WindowedStreamImpl.extractEnvironment()` returns null | `datastream/WindowedStreamImpl.java:89` |

### 1.2 Transformation Implementations

**Status: FULLY IMPLEMENTED** — All 7 transformation types exist and are fully functional as data-holder classes.

| Transformation | File | Lines | Status |
|---------------|------|-------|--------|
| `Transformation<T>` (abstract) | `transformation/Transformation.java` | 125 | **Fully implemented** |
| `PhysicalTransformation<OUT>` (abstract) | `transformation/PhysicalTransformation.java` | 36 | **Fully implemented** |
| `OneInputTransformation<IN, OUT>` | `transformation/OneInputTransformation.java` | 109 | **Fully implemented** |
| `SourceTransformation<OUT>` | `transformation/SourceTransformation.java` | 71 | **Fully implemented** |
| `SinkTransformation<T>` | `transformation/SinkTransformation.java` | 81 | **Fully implemented** |
| `PartitionTransformation<T>` | `transformation/PartitionTransformation.java` | 113 | **Fully implemented** |
| `TimestampsAndWatermarksTransformation<T>` | `transformation/TimestampsAndWatermarksTransformation.java` | 101 | **Fully implemented** |

#### Missing

| # | Severity | Finding | Context |
|---|----------|---------|---------|
| CORE-F5 | **MEDIUM** | **`TwoInputTransformation` does not exist.** Two-input operations (connect/union/join) are impossible | No class in codebase |
| CORE-F6 | **INFO** | `TwoInputStreamOperator` only mentioned in Javadoc | `operators/StreamOperator.java:29` |

### 1.3 StreamGraph / JobGraph / PartitionedPlan Wiring

**Status: FULLY IMPLEMENTED** — The full compilation pipeline from Transformation DAG → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan → GraphExecutionPlan is complete.

| Component | Module | Lines | Status |
|-----------|--------|-------|--------|
| `StreamGraph` | core | 265 | **Fully implemented** |
| `StreamGraphGenerator` | core | 583 | **Fully implemented** |
| `StreamNode` | core | 269 | **Fully implemented** |
| `StreamEdge` | core | 206 | **Fully implemented** |
| `JobGraph` | core | 231 | **Fully implemented** |
| `JobGraphGenerator` | core | 566 | **Fully implemented** |
| `JobVertex` | core | 210 | **Fully implemented** |
| `JobEdge` | core | 157 | **Fully implemented** |
| `OperatorChain` | core | 251 | **Fully implemented** |
| `PartitionedPlan` | core | 103 | **Fully implemented** |
| `PartitionedPlanGenerator` | core | 109 | **Fully implemented** |
| `DeploymentPlan` | core | 84 | **Fully implemented** |
| `DeploymentAssignment` | core | 84 | **Fully implemented** |
| `GraphExecutionPlan` | core | 619 | **Fully implemented** (topological sort, partition matrix, subtask creation) |

#### Findings

| # | Severity | Finding | File:Line |
|---|----------|---------|-----------|
| CORE-F7 | **LOW** | `StreamGraphGenerator.detectWindowingStrategies()` empty body (documented P1-1) | `graph/StreamGraphGenerator.java:171-180` |
| CORE-F8 | **LOW** | `PartitionOperatorFactory.createStreamOperator()` returns null (logical node) | `graph/StreamGraphGenerator.java:569-570` |
| CORE-F9 | **LOW** | `transform()` silently skips null transformations | `graph/StreamGraphGenerator.java:203-204` |
| CORE-F10 | **LOW** | `createOperatorFromFactory()` returns null when factory is null | `jobgraph/JobGraphGenerator.java:419-420` |

### 1.4 Runtime Execution (Core)

| Component | File | Lines | Status |
|-----------|------|-------|--------|
| `Task` | `execution/Task.java` | 490 | **Fully implemented** (state machine: CREATED→SCHEDULED→DEPLOYING→RUNNING→COMPLETED) |
| `SubtaskTask` | `execution/SubtaskTask.java` | 234 | **Fully implemented** |
| `TaskExecutor` | `execution/TaskExecutor.java` | 452 | **Fully implemented** (thread pool, submit, awaitCompletion, shutdown) |
| `StreamTaskInvokable` | `execution/StreamTaskInvokable.java` | 640+ | **Fully implemented** (SOURCE/MIDDLE/SINK/SELF_CONTAINED roles) |
| `RecordWriter` | `execution/RecordWriter.java` | 306 | **Fully implemented** (emit, emitBarrier, emitWatermark) |
| `InputGate` | `execution/InputGate.java` | 446 | **Fully implemented** (barrier alignment, watermark merge, multi-channel I/O) |
| `InputChannel` | `execution/InputChannel.java` | 66 | **Fully implemented** (thin wrapper around ResultPartition) |
| `CheckpointBarrierTracker` | `execution/CheckpointBarrierTracker.java` | 252 | **Fully implemented** (per-task operator-level barrier tracking) |
| `StreamSourceOperator` | `operators/StreamSourceOperator.java` | 280+ | **Fully implemented** (source lifecycle, barrier injection, mailbox) |
| `AbstractStreamOperator` | `operators/AbstractStreamOperator.java` | 350+ | **Fully implemented** (keyed state, operator state, checkpoint lifecycle) |

#### Findings

| # | Severity | Finding | File:Line |
|---|----------|---------|-----------|
| CORE-F11 | **INFO** | `BarrierAligner` does NOT exist as a separate class in this codebase. Barrier alignment logic is embedded in `InputGate`. This matches the design doc's updated description (Stage 23 code cleanup removed the deprecated `BarrierAligner`/`AlignedBarrier` classes). | N/A |
| CORE-F12 | **INFO** | `RecordWriter.validateFlowControlPolicy()` throws UnsupportedOperationException for non-BLOCKING_QUEUE policies — intentional fail-fast guard | `execution/RecordWriter.java:139` |
| CORE-F13 | **LOW** | `StreamTaskInvokable.RecordWriterOutput` has empty `emitWatermarkStatus()`, `emitLatencyMarker()`, `collect(OutputTag,...)` — documented as intentional (side outputs/watermark status/latency markers not forwarded across task boundaries) | `execution/StreamTaskInvokable.java:564-576` |
| CORE-F14 | **LOW** | `StreamExecutionResult.getAccumulatorResult()` returns null on type mismatch | `environment/StreamExecutionResult.java:101` |

### 1.5 Design Doc Drift (core-design.md, graph-model-design.md)

| Doc Claim | Live Code State | Drift? |
|-----------|-----------------|--------|
| `StreamComponents` as canonical registry with 8 registry maps | `StreamComponents` exists with 2 registry maps (`transforms`, `streams`); `coders`/`schemas`/`environments`/`sideInputs` removed as dead registries (noted in doc at line 43) | **No drift** — doc documents the removal |
| SPI loading of `IWindowOperatorFactory` into `StreamComponents` | Yes, loaded via `Class.forName("io.nop.stream.runtime.operators.windowing.WindowOperatorFactoryImpl")` | **Match** |
| `CheckpointedFunction` / `OperatorStateStore` interfaces | Exist in core module, but Operator State NOT implemented (documented gap in `core-design.md` §7.3) | **Confirmed gap** |
| Two-layer graph (StreamGraph→JobGraph), no ExecutionGraph | Live code matches: exactly 2 layers + PartitionedPlan as deployment layer | **Match** |
| Invokable originally placeholder (graph-model-design §5) | `StreamTaskInvokable` fully implements invoke() with 4 roles | **Addressed** (doc updated) |
| Null-safe barrier alignment with 30s timeout | `InputGate` implements barrier alignment with 30s `barrierAlignmentTimeout` | **Match** |
| Side outputs only through flatMap | Confirmed: `OutputTag` collect is no-op across task boundaries | **Match** |

---

## 2. nop-stream-runtime

### 2.1 Task Lifecycle — Distributed Execution

**Status: FULLY IMPLEMENTED**

| Component | File | Lines | Status |
|-----------|------|-------|--------|
| `TaskManager` | `taskmanager/TaskManager.java` | 697 | **Fully implemented** (distributed task host, heartbeat, fencing, checkpoint triggering) |
| `EmbeddedDistributedExecutor` | `execution/EmbeddedDistributedExecutor.java` | 307 | **Fully implemented** (multi-node embedded execution) |
| `GraphModelCheckpointExecutor` | `execution/GraphModelCheckpointExecutor.java` | 1122 | **Fully implemented** (end-to-end execution with checkpoint wiring) |
| `JobCoordinator` | `coordinator/JobCoordinator.java` | 982 | **Fully implemented** (RPC service, task assignment, checkpoint triggering, failure detection, global recovery) |
| `CheckpointExecutorFactoryImpl` | `execution/CheckpointExecutorFactoryImpl.java` | — | **Fully implemented** |
| `DeploymentPlanProviderImpl` | `execution/DeploymentPlanProviderImpl.java` | — | **Fully implemented** |

### 2.2 RecordWriter / InputChannel / BarrierAligner Runtime Connectivity

**Status: FULLY IMPLEMENTED** — All data exchange components are fully wired.

| Component | Module | Lines | Status |
|-----------|--------|-------|--------|
| `RemoteInputChannel` | runtime | 258 | **Fully implemented** (MQ subscription via IMessageService, fencing, decode, blocking queue) |
| `RemoteResultPartition` | runtime | 147 | **Fully implemented** (writes via IMessageService) |
| `RemoteGraphExecutionPlanBuilder` | runtime | 332 | **Fully implemented** (builds GraphExecutionPlan with remote transport) |

**Critical Finding**: The `BarrierAligner` class does NOT exist as a separate entity. Barrier alignment is handled by `InputGate` (core module, 446 lines). This aligns with the design doc checkpoint-design.md update (Stage 23 code cleanup removed deprecated `BarrierAligner`/`AlignedBarrier` classes). The design doc §2.2 explicitly documents: "原 BarrierAligner/AlignedBarrier 类已于 Stage 23 代码清理删除".

### 2.3 Checkpoint Subsystem (Runtime)

**Status: FULLY IMPLEMENTED** — Production-grade checkpoint coordinator with async persist, subsuming commit, fencing, and failure handling.

| Component | File | Lines | Status |
|-----------|------|-------|--------|
| `CheckpointCoordinator` | `checkpoint/CheckpointCoordinator.java` | 919 | **Fully implemented** (trigger gating, async persist, completion, abort, failure tracking, metrics, restore) |
| `PendingCheckpoint` | `checkpoint/PendingCheckpoint.java` | 229 | **Fully implemented** (RUNNING→COMPLETED|ABORTED|FAILED state machine, ACK tracking, CompletableFuture) |
| `CheckpointPlanBuilder` | `checkpoint/CheckpointPlanBuilder.java` | 244 | **Fully implemented** (plan gen, participant detection, parallelism > 1) |
| `LocalFileCheckpointStorage` | `checkpoint/storage/LocalFileCheckpointStorage.java` | 555 | **Fully implemented** (all ICheckpointStorage methods, atomic writes, lock concurrency) |
| `JdbcCheckpointStorage` | `checkpoint/storage/JdbcCheckpointStorage.java` | 681 | **Fully implemented** (DDL auto-creation, duplicate key handling, all methods) |
| `CheckpointSerDe` | `checkpoint/storage/CheckpointSerDe.java` | 348 | **Fully implemented** (JSON-based, format versioning v1/v2, round-trip for all types) |

| Interface | Implementations | Status |
|-----------|----------------|--------|
| `ICheckpointStorage` | LocalFileCheckpointStorage, JdbcCheckpointStorage | **2 impls, both fully implemented** |
| `ICheckpointExecutorFactory` | CheckpointExecutorFactoryImpl | **1 impl, fully wired** |
| `IDeploymentPlanProvider` | DeploymentPlanProviderImpl, DefaultDeploymentPlanProvider | **2 impls, fully wired** |

### 2.4 Design Doc Drift

| Doc Claim | Live Code State | Drift? |
|-----------|-----------------|--------|
| Async persist path (SNAPSHOTTING→DURABLE via dedicated executor) | Fully implemented in `CheckpointCoordinator.completePendingCheckpoint()` + `executePersistAsync()` | **Match** |
| Epoch lifecycle states | All 7 states used | **Match** |
| Barrier alignment timeout (30s) | Implemented in `InputGate` | **Match** |
| `minPause` and `maxConcurrentCheckpoints` | Both fully respected | **Match** |
| Consecutive failure tracking (threshold=3) | Implemented (`CONSECUTIVE_FAILURE_THRESHOLD` at line 360) | **Match** |
| Subsuming commit contract | `finishCommit(false)` does NOT abort prepared transaction | **Match** |

---

## 3. nop-stream-cep

### 3.1 CepOperator State Backend

**Status: FULLY IMPLEMENTED** — The CEP module is the most mature nop-stream submodule. State backend integration uses `IKeyedStateBackend` (G18/G19/G20 closed).

| Component | File | Lines | Status |
|-----------|------|-------|--------|
| `CepOperator<IN, OUT>` | `CepOperator.java` | 450+ | **Fully implemented** (NFA engine, shared buffer, state backend, watermark, checkpoint) |
| `NFA<T>` | `NFA.java` | 984 | **Fully implemented** (process, advanceTime, computeNextStates) |
| `NFACompiler` | `NFACompiler.java` | 1100 | **Fully implemented** (pattern-to-NFA graph compilation) |
| `SharedBuffer<V>` | `SharedBuffer.java` | 388 | **Fully implemented** (LRU cache, event registration, versioned storage) |
| `SharedBufferAccessor<V>` | `SharedBufferAccessor.java` | 386 | **Fully implemented** (pattern extraction, node locking, flush-on-close) |
| `NFAState` | `NFAState.java` | — | **Fully implemented** |
| `MemoryKeyedStateBackend<K>` | core backend | 63 | **Fully implemented** (JVM heap-backed, sharding, schema checksum) |
| `MemoryStateBackend` | core backend | 40 | **Fully implemented** (creates MemoryKeyedStateBackend + MemoryOperatorStateBackend) |

#### State Backend Wiring in CepOperator

The state backend wiring (`CepOperator.java:230-245`) follows a clear priority:
1. Use existing `keyedStateBackend`
2. If null but `stateBackend` set, create backend via factory
3. Fallback to `MemoryKeyedStateBackend` with WARN log

**Key Finding**: The fallback path at line 241-244 creates `MemoryKeyedStateBackend` directly (not through `createKeyedStateBackend()`), so `applyPendingRestoreState()` is NOT called — checkpoint recovery is silently skipped when no `stateBackend` is configured.

| # | Severity | Finding | File:Line |
|---|----------|---------|-----------|
| CEP-F1 | **HIGH** | Fallback `MemoryKeyedStateBackend` loses pending restore state (checkpoint recovery silently skipped) | `CepOperator.java:241-244` |
| CEP-F2 | **HIGH** | `RedisStateBackend` documented in `IStateBackend` javadoc but does NOT exist | `IStateBackend.java` javadoc |
| CEP-F3 | **MEDIUM** | `CepRuntimeContext` hardcodes subtask=0, parallelism=1 | `CepOperator.java:307-308` |
| CEP-F4 | **MEDIUM** | Processing time timer deletion is no-op (`deleteProcessingTimeTimer()` empty) | `CepOperator.java:279-280` |
| CEP-F5 | **MEDIUM** | `AbstractStreamOperator.finish()` is empty — no flush on normal completion | `AbstractStreamOperator.java:118-120` |
| CEP-F6 | **LOW** | `SharedBuffer.releaseCacheStatisticsTimer()` has empty body | `SharedBuffer.java:230-231` |

### 3.2 NFA State Checkpoint

**Status: FULLY IMPLEMENTED** — NFA state participates in checkpoint through `ValueState<NFAState>` backed by `MemoryValueState`. Serialization chain:

```
CepOperator.snapshotState() → AbstractStreamOperator.snapshotState()
  → keyedStateBackend.snapshotState() → MemoryStateSerDe.snapshotState()
```

`NFAState` stores `Queue<ComputationState>` fields (POJO with DeweyNumber, NodeId, EventId). All participate properly.

### 3.3 Watermark Handling in CEP

**Status: FULLY IMPLEMENTED** — `processWatermark()` advances `currentWatermark`, triggers `onEventTime()` for buffered events, and drives NFA timeouts.

**Finding**: `registeredEventTimeTimers` (TreeSet) is saved/restored in checkpoints but never drives processing — `onEventTime()` is called directly from `processWatermark()`. The timer set is decorative bookkeeping (watermark-driven processing is the actual mechanism).

| # | Severity | Finding | File:Line |
|---|----------|---------|-----------|
| CEP-F7 | **MEDIUM** | Event-time timers are decorative — `registeredEventTimeTimers` saved/restored but never drive processing | `CepOperator.java:254,339` |
| CEP-F8 | **LOW** | CEP design doc (cep-design.md §8) documents known limitation: "事件时间超时未生效（currentWatermark() returns Long.MIN_VALUE）" — appears to be fixed in live code where `processWatermark()` correctly updates `currentWatermark` | `CepOperator.java:368` |

### 3.4 Design Doc Drift (cep-design.md)

| Doc Claim | Live Code State | Drift? |
|-----------|-----------------|--------|
| `IKeyedStateBackend` used instead of `SimpleKeyedStateStore` (G18/G19/G20) | Confirmed: `CepOperator.open()` uses `getKeyedStateBackend()` or `stateBackend.createKeyedStateBackend()` | **Match** |
| `SimpleKeyedStateStore` no longer used by CepOperator | Confirmed: `SimpleKeyedStateStore` exists in core but CepOperator does NOT use it | **Match** |
| CEP as most mature submodule | Confirmed: 58 source files, full NFA + SharedBuffer + state backend | **Match** |
| Known limitation: watermark not propagated to CEP | Partially addressed: `processWatermark()` updates `currentWatermark` but CEP's watermark path through `AbstractStreamOperator.processWatermark()` is described as separate | **Match** |

---

## 4. nop-stream-connector

### 4.1 Module Structure

The connector family is split into 3 modules:

| Module | Role | Files |
|--------|------|-------|
| `nop-stream-connector` | Base connectors (IMessageService bridge) | `MessageSourceFunction.java`, `MessageSinkFunction.java` |
| `nop-stream-connector-batch` | Batch bridge adapters | `BatchLoaderSourceFunction.java`, `BatchConsumerSinkFunction.java`, `StreamConnectors.java` |
| `nop-stream-connector-debezium` | CDC source | `DebeziumCdcSourceFunction.java` |

This split follows AR-2 to keep the base module loadable when optional libraries are absent.

### 4.2 Implementation State

**Status: ALL FULLY IMPLEMENTED** — No stubs, no hollow implementations, no no-op methods in production connector code.

| Class | Interface | Lines | Status |
|-------|-----------|-------|--------|
| `MessageSourceFunction<T>` | `SourceFunction<T>` | 200 | **Fully implemented** (P1-9 error propagation, null validation, partition-aware) |
| `MessageSinkFunction<T>` | `SinkFunction<T>` | 51 | **Fully implemented** |
| `BatchLoaderSourceFunction<S>` | `ReplayableSourceFunction<S>` | 106 | **Fully implemented** |
| `BatchConsumerSinkFunction<R>` | `SinkFunction<R>` | 151 | **Fully implemented** (buffer-flush, null rejection P1-15) |
| `DebeziumCdcSourceFunction` | `DrainableSource<ChangeEvent>` | 152 | **Fully implemented** (CAS guard on run, DebeziumMessageSource) |
| `StreamConnectors` | Factory methods | 57 | **Fully implemented** |

### 4.3 Exactly-Once Support Analysis

**Critical Finding**: Despite `STRICT_EXACTLY_ONCE` being the DEFAULT `processingGuarantee` in `CheckpointConfig.java:43`, **zero production connector implementations can achieve exactly-once semantics**.

| Connector | Declared Capability | Exactly-Once Ready? |
|-----------|--------------------|--------------------|
| `MessageSourceFunction` | `AT_LEAST_ONCE` | No — not REPLAYABLE |
| `MessageSinkFunction` | `AT_LEAST_ONCE` | No — not TWO_PHASE_COMMIT |
| `BatchLoaderSourceFunction` | `AT_LEAST_ONCE` (despite replayable behavior) | No — capability is weaker than actual (conservative) |
| `BatchConsumerSinkFunction` | `IDEMPOTENT` | No — not TWO_PHASE_COMMIT |
| `DebeziumCdcSourceFunction` | `REPLAYABLE` | Source only — no sink partner exists |

| # | Severity | Finding | File:Line |
|---|----------|---------|-----------|
| CONN-F1 | **HIGH** | No `TwoPhaseCommitSinkFunction` subclass exists in production code — only in tests | Entire module tree |
| CONN-F2 | **HIGH** | Default `CheckpointConfig.processingGuarantee` is `STRICT_EXACTLY_ONCE` but no shipped connector can satisfy it | `CheckpointConfig.java:43` |
| CONN-F3 | **MEDIUM** | `BatchLoaderSourceFunction` implements `ReplayableSourceFunction` with working `seek()`/`getCurrentOffset()` but declares `AT_LEAST_ONCE` (conservative) | `BatchLoaderSourceFunction.java:93-95` |
| CONN-F4 | **MEDIUM** | `CheckpointedSourceFunction` is `@Internal` "API 预留，当前未被使用" — no class directly implements it | `CheckpointedSourceFunction.java:20-21` |
| CONN-F5 | **INFO** | `DebeziumCdcSourceFunction` declares `REPLAYABLE` — no exactly-once sink adapter exists for CDC | `DebeziumCdcSourceFunction.java:129-131` |

### 4.4 No Silent Swallows

All exception handling in connector modules is properly documented:
- `MessageSourceFunction` uses P1-9 error propagation (captures exception, rethrows on caller thread)
- `BatchConsumerSinkFunction` wraps and rethrows flush errors
- `DebeziumCdcSourceFunction` catches and ignores cleanup errors (intentional, conventional pattern, documented with comment)

### 4.5 Design Doc Drift (connector-design.md)

| Doc Claim | Live Code State | Drift? |
|-----------|-----------------|--------|
| Adapters are "thin" wrappers (~60 lines each) | Actual: MessageSourceFunction (200 lines), BatchConsumerSinkFunction (151 lines) — thicker due to error handling, validation, lifecycle management | **Slight under-estimate in doc** but functionally correct |
| BatchLoaderSourceFunction implements SourceFunction | Actual: implements ReplayableSourceFunction (extends CheckpointedSourceFunction, extends SourceFunction) — **upgraded** from design doc | **Improvement** (not drift) |
| MessageSourceFunction uses Thread.sleep(1000) loop | Actual: uses CountDownLatch (more efficient blocking) | **Improvement** |
| BatchConsumerSinkFunction uses null IBatchChunkContext | Confirmed: `consumer.consume(buffer, null)` — documented limitation | **Match** |
| Kafka IMessageService adapter not implemented | Confirmed: `nop-message-kafka` module is empty | **Match** |

---

## 5. Checkpoint & Watermark Subsystems (Cross-Cutting)

### 5.1 Checkpoint Subsystem

**Status: FULLY IMPLEMENTED** — Production-grade implementation spanning core + runtime.

#### Architecture Summary

```
CheckpointCoordinator (runtime, 919 lines)
├── Trigger gating: maxConcurrentCheckpoints + minPause + consecutiveFailureThreshold
├── Async persist: completePendingCheckpoint() CAS→persist executor→onCompletePersistSuccess
├── Completion: pending→completed, subsuming commit, cleanup, notify participants
├── Abort: CAS ABORTED, finishCommit(false), abortHandler callback
├── Recovery: load latest durable manifest, rebuild plan, restore state
├── Scheduler: periodic trigger via ScheduledExecutorService
└── Metrics: checkpoint metrics snapshot + monitoring

PendingCheckpoint (runtime, 229 lines)
├── State machine: RUNNING→COMPLETED|ABORTED|FAILED
├── ACK tracking: acknowledgeTask() removes from pending set
└── CompletableFuture for savepoint blocking

InputGate / CheckpointBarrierTracker (core, 446+252 lines)
├── InputGate: multi-channel barrier alignment, timeout, overlapping detection, watermark merge
├── CheckpointBarrierTracker: per-task operator-level ACK tracking
└── No BarrierAligner class — alignment logic embedded in InputGate
```

### 5.2 Watermark Subsystem

**Status: FULLY IMPLEMENTED** — Complete watermark infrastructure.

| Component | Module | Lines | Status |
|-----------|--------|-------|--------|
| `WatermarkStrategy<T>` | core | 249 | **Fully implemented** (interface + static factories) |
| `WatermarkGenerator<T>` | core | 43 | **Interface fully defined** |
| `AscendingTimestampsWatermarks` | core | — | **Fully implemented** |
| `BoundedOutOfOrdernessWatermarks<T>` | core | 74 | **Fully implemented** |
| `NoWatermarksGenerator<E>` | core | 34 | **Fully implemented** (by-design no-op) |
| `TimestampAssigner<T>` | core | — | **Interface fully defined** |
| `WatermarkOutputMultiplexer` | core | 226 | **Fully implemented** (only referenced by own tests) |
| `CombinedWatermarkStatus` | core | 137 | **Fully implemented** |
| `IndexedCombinedWatermarkStatus` | core | — | **Fully implemented** (N-capable, single-tested) |
| `TimestampsAndWatermarksOperator<T>` | core | 171 | **Fully implemented** (auto-inserted in execute()) |
| `HeapInternalTimerService<K,N>` | core | — | **Fully implemented** (unified G16, timer checkpoint G2) |

#### Watermark Flow

```
Source → TimestampsAndWatermarksOperator → Map → Window → Sink
  ↑processElement: extractTimestamp + onEvent
  ↑onProcessingTime: onPeriodicEmit (watermarkInterval default 200ms)
  ↑processWatermark: passthrough (own watermark generated locally)
```

#### Findings

| # | Severity | Finding | File:Line |
|---|----------|---------|-----------|
| WM-F1 | **LOW** | `TimestampsAndWatermarksOperator` has empty if blocks at lines 118-119 (leftover stubs) | `operators/TimestampsAndWatermarksOperator.java:118-120` |
| WM-F2 | **LOW** | `NoWatermarksGenerator.onEvent()` and `onPeriodicEmit()` empty — by design | `common/eventtime/NoWatermarksGenerator.java:28-33` |
| WM-F3 | **LOW** | Multi-input watermark merge via `IndexedCombinedWatermarkStatus.forInputsCount(2)` is dormant — no TwoInputStreamOperator consumers | G47 ruling, documented in time-model-design.md §5.4 |
| WM-F4 | **LOW** | `WatermarkOutputMultiplexer` not wired into execution path (its internal WatermarkUpdateListener is used by CombinedWatermarkStatus in production code, but the multiplexer's primary multi-split merging path is only self-tested) | `common/eventtime/WatermarkOutputMultiplexer.java` |

### 5.3 Design Doc Drift (checkpoint-design.md, time-model-design.md)

| Doc Claim | Live Code State | Drift? |
|-----------|-----------------|--------|
| Async persist path (3-segment: ACK thread→persist executor→re-acquire) | Implemented in `CheckpointCoordinator.completePendingCheckpoint()` | **Match** |
| `setAbortHandler` for timeout abort | Implemented with `abortMarked` + `SubtaskTask.cancel()` | **Match** |
| BarrierAligner removed in Stage 23 | Confirmed: no BarrierAligner class; InputGate handles alignment | **Match** (doc updated) |
| `TimestampsAndWatermarksOperator` auto-inserted in execute() | Confirmed: transformation generates `TimestampsAndWatermarksTransformation` which creates StreamNode in graph | **Match** |
| `watermarkInterval` default 200ms | `TimestampsAndWatermarksOperator.DEFAULT_WATERMARK_INTERVAL_MS=200` | **Match** |
| Timer checkpoint/restore (G2) | `HeapInternalTimerService.snapshotTimers()` + `restoreTimers()` with deferred application pattern | **Match** |

---

## 6. Hollow / No-Op / Stub Implementation Register

All findings from all modules, organized by severity and type.

### 6.1 HIGH Severity

| # | Module | Finding | File:Line | Type |
|---|--------|---------|-----------|------|
| H1 | CEP | Fallback `MemoryKeyedStateBackend` skips `applyPendingRestoreState()` — checkpoint recovery silently lost when no state backend configured | `CepOperator.java:241-244` | **Silent degraded behavior** |
| H2 | CEP | `RedisStateBackend` documented in `IStateBackend` javadoc but does NOT exist anywhere in codebase | `IStateBackend.java` javadoc | **Missing documented component** |
| H3 | Connector | Zero `TwoPhaseCommitSinkFunction` subclasses in production code — no exactly-once sink shipped | Entire connector tree | **Missing production implementation** |
| H4 | Connector | Default `CheckpointConfig.processingGuarantee` is `STRICT_EXACTLY_ONCE` but no shipped connector can satisfy it | `CheckpointConfig.java:43` | **Configuration gap** |

### 6.2 MEDIUM Severity

| # | Module | Finding | File:Line | Type |
|---|--------|---------|-----------|------|
| M1 | Core | `TwoInputTransformation` does not exist — connect/union/join impossible | N/A | **Missing functionality** |
| M2 | CEP | `CepRuntimeContext` hardcodes subtask=0, parallelism=1 | `CepOperator.java:307-308` | **Hollow** |
| M3 | CEP | Processing time timer deletion is no-op (`deleteProcessingTimeTimer()` empty) | `CepOperator.java:279-280` | **No-op** |
| M4 | CEP | `AbstractStreamOperator.finish()` is empty — no flush on stream end | `AbstractStreamOperator.java:118-120` | **No-op** |
| M5 | CEP | Event-time timers decorative — saved/restored but never drive processing | `CepOperator.java:254,339` | **Redundant** |
| M6 | Connector | `BatchLoaderSourceFunction` declares `AT_LEAST_ONCE` despite replayable behavior | `BatchLoaderSourceFunction.java:93-95` | **Conservative under-declaration** |

### 6.3 LOW Severity

| # | Module | Finding | File:Line | Type |
|---|--------|---------|-----------|------|
| L1 | Core | `DataStreamImpl.map()` type inference returns null | `datastream/DataStreamImpl.java:337` | **Regression potential** |
| L2 | Core | `KeyedStreamImpl` unsafe cast risks NPE | `datastream/KeyedStreamImpl.java:62-63` | **Fragile** |
| L3 | Core | `StreamGraphGenerator.detectWindowingStrategies()` empty (P1-1) | `graph/StreamGraphGenerator.java:171-180` | **Documented gap** |
| L4 | Core | `StreamExecutionResult.getAccumulatorResult()` returns null on type mismatch | `environment/StreamExecutionResult.java:101` | **Degraded** |
| L5 | Core | `StreamTaskInvokable.RecordWriterOutput` no-ops for side outputs, WM status, latency markers | `execution/StreamTaskInvokable.java:564-576` | **Intentional** |
| L6 | CEP | `SharedBuffer.releaseCacheStatisticsTimer()` empty | `SharedBuffer.java:230-231` | **No-op cleanup hook** |
| L7 | Watermark | `TimestampsAndWatermarksOperator` empty if blocks (leftover stubs) | `operators/TimestampsAndWatermarksOperator.java:118-120` | **Dead code** |
| L8 | Watermark | Multi-input watermark merge dormant (no TwoInputStreamOperator) | G47 ruling | **Pending consumer** |

### 6.4 Design Doc Drift Register

| # | Doc | Claim | Reality | Drift |
|---|-----|-------|---------|-------|
| D1 | core-design.md | `StreamComponents` coders/schemas/environments/sideInputs registries | Removed in code (doc documents the removal at line 43) | **No drift — doc updated** |
| D2 | checkpoint-design.md | `BarrierAligner` class, epoch lifecycle states | BarrierAligner removed (Stage 23); alignment in InputGate | **No drift — doc updated** |
| D3 | cep-design.md | `currentWatermark()` returns Long.MIN_VALUE (known limitation) | Live code: `processWatermark()` correctly updates `currentWatermark` at line 368 | **Fix delivered but doc not yet updated** |
| D4 | connector-design.md | Adapters ~60 lines each | Actual: 50-200 lines (thicker with validation/error handling) | **Doc under-estimate, functionally correct** |
| D5 | time-model-design.md | `WatermarkOutputMultiplexer` not wired | Confirmed — only self-referenced in tests | **Match** |
| D6 | state-management-design.md | Operator State not implemented | Confirmed — `OperatorStateStore` interface exists but not wired | **Match** |

---

## Summary

| Module | Coverage | Hollow/No-Op Count | Critical Findings |
|--------|----------|-------------------|-------------------|
| **nop-stream-core** | **FULL** | 14 (all LOW) | `TwoInputTransformation` missing (M1), but out of scope for current phase |
| **nop-stream-runtime** | **FULL** | 0 on critical path | No hollow/no-op implementations on execution path; all intentional no-ops documented |
| **nop-stream-cep** | **FULL** | 8 (2 HIGH) | **H1**: Fallback loses pending restore; **H2**: RedisStateBackend documented but absent |
| **nop-stream-connector** | **FULL** | 0 in production code | **H3**: No exactly-once sinks; **H4**: Default config expects exactly-once but no connector delivers it |
| **Checkpoint subsystem** | **FULL** | 0 | Production-grade implementation across core+runtime |
| **Watermark subsystem** | **FULL** | 4 (all LOW) | Dormant multi-input merge path; leftover stubs |

**Overall Assessment**: The nop-stream implementation is significantly more complete than the prior baseline described in the roadmap. The modules compile successfully, the full compilation pipeline (Transformation→StreamGraph→JobGraph→PartitionedPlan→DeploymentPlan→GraphExecutionPlan) is operational, checkpoint/watermark subsystems are production-grade, and the CEP module is the most mature with full NFA+SharedBuffer+state-backend integration.

The primary architectural gap is the **exactly-once connector chain**: the framework has complete exactly-once infrastructure (TwoPhaseCommitSinkFunction, barrier alignment, checkpoint coordinator, `StreamRequirementValidator`), but no shipped connector satisfies `STRICT_EXACTLY_ONCE`. This is a deliberate conservative declaration — the framework correctly rejects pipelines that can't meet the guarantee.

---

## Appendix: Non-Blocking Follow-ups

(To be filled when comparison analyses (items 3-7) consume this audit document.)
