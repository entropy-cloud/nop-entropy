# Checkpoint & Barrier 源码级对比分析

> **Status**: resolved
> **Created**: 2026-07-24
> **Plan**: `docs/plans/nop-stream-flink-comparison/2026-07-24-1000-1-checkpoint-barrier-comparison.md`
> **Prerequisite Plans**: 316 (Flink source audit), 317 (nop-stream live audit) — both active, deliverables absent; supplemented by direct source reading per plan guardrails

---

## Table of Contents

1. [Barrier Injection & Alignment Paths](#1-barrier-injection--alignment-paths)
2. [CheckpointCoordinator Coordination Flow](#2-checkpointcoordinator-coordination-flow)
3. [Checkpoint Storage](#3-checkpoint-storage)
4. [State Snapshot Path](#4-state-snapshot-path)
5. [Exactly-Once Level Implementation](#5-exactly-once-level-implementation)
6. [Failure Recovery Path](#6-failure-recovery-path)
7. [Abort Control Channel](#7-abort-control-channel)
8. [Gap Table](#8-gap-table)
9. [Roadmap Gap Verification](#9-roadmap-gap-verification)

---

## 1. Barrier Injection & Alignment Paths

### Flink Architecture

Flink uses a layered barrier processing architecture with **three key abstractions**:

1. **`CheckpointBarrier`** — `RuntimeEvent` subclass carrying `(id, timestamp, CheckpointOptions)`. Sent as a normal event on the data channel from source operators downstream.
   - Source: `org.apache.flink.runtime.io.network.api.CheckpointBarrier`

2. **`CheckpointedInputGate`** — wraps a raw `InputGate`, intercepts all `BufferOrEvent` elements. Dispatches `CheckpointBarrier` → `barrierHandler.processBarrier()`, `CancelCheckpointMarker` → `barrierHandler.processCancellationBarrier()`.
   - Source: `org.apache.flink.streaming.runtime.io.checkpointing.CheckpointedInputGate`

3. **`CheckpointBarrierHandler`** (abstract) — strategy pattern base class. Two implementations:
   - **`SingleCheckpointBarrierHandler`** — exactly-once semantics. Uses a `BarrierHandlerState` state machine with `4+` states (`WaitingForFirstBarrier`, etc.). Tracks per-channel barrier arrival via `alignedChannels` (Set). Blocks channels that have delivered their barrier (via `ChannelState.blockChannel()`). Supports unaligned mode and alternating mode (aligned with timeout → unaligned).
   - **`CheckpointBarrierTracker`** — at-least-once semantics. Tracks barrier counts per checkpoint ID, never blocks channels.

4. **`CancelCheckpointMarker`** — separate event type for checkpoint cancellation, providing a dedicated abort channel.
   - Source: `org.apache.flink.runtime.io.network.api.CancelCheckpointMarker`

**Key design patterns:**
- Channel-level blocking: `InputGate.blockConsumption(channelInfo)` / `resumeConsumption(channelInfo)`
- State machine for barrier alignment transitions
- Alignment metrics tracking (alignment duration, bytes processed during alignment)
- `CheckpointOptions.AlignmentType` enum: `ALIGNED`, `UNALIGNED`, `FORCED_ALIGNED`, `AT_LEAST_ONCE`

### nop-stream Architecture

nop-stream has **two parallel barrier processing paths**:

#### Path A: Production Path (inline, no alignment)

1. **`CheckpointBarrier`** — extends `StreamElement`, carries `(id, timestamp, CheckpointType)`.
   - Source: `nop-stream-core/.../checkpoint/CheckpointBarrier.java`

2. **Production barrier handling chain** (active code):
   - `StreamTaskInvokable` receives barrier → calls `headInput.processBarrier(element.asCheckpointBarrier())`
   - `ChainingOutput.processBarrier()` forwards to next `Input` in chain
   - `AbstractStreamOperator.processBarrier()` / `StreamSinkOperator.processBarrier()` handles directly

3. **Per-operator processing**: Each operator processes barriers sequentially per input, with NO cross-input alignment. The operator immediately triggers its snapshot when it receives the barrier.

#### Path B: BarrierAligner (dead code)

1. **`BarrierAligner`** — complete implementation, but **zero production callers**.
   - Source: `nop-stream-runtime/.../barrier/BarrierAligner.java`
   - Methods: `processBarrier()`, `pollAlignedBarrier()`, `abortAll()`, `findCompletedCheckpointId()`
   - Data structures: `List<TreeMap<Long, CheckpointBarrier>>` — one TreeMap per input, keyed by checkpoint ID
   - Algorithm: collects barriers per-input in TreeMaps, then checks if a checkpoint ID has been received on ALL inputs (`findCompletedCheckpointId()` returns min ID present in all maps)
   - Javadoc explicitly states: `"当前 GraphModelCheckpointExecutor 未使用"`

2. **`AlignedBarrier`** — output data class for `BarrierAligner`. Only used by `BarrierAligner` (dead code).

### Comparison

| Aspect | Flink | nop-stream | Gap |
|--------|-------|-----------|-----|
| Barrier class | `CheckpointBarrier` extends `RuntimeEvent` | `CheckpointBarrier` extends `StreamElement` | No gap — parallel designs |
| Cancel/abort marker | `CancelCheckpointMarker` (separate event type) | No equivalent — TODO in abort channel section | **Gap**: no separate cancellation barrier event type |
| Alignment strategy | State machine: `WaitingForFirstBarrier`, `AbstractAlignedBarrierHandlerState`, `Alternating*` states | `BarrierAligner.processBarrier()` — simpler: collects in TreeMap, checks `findCompletedCheckpointId()` | **Hollow**: BarrierAligner is a standalone class with no production integration |
| Channel blocking | `InputGate.blockConsumption(channelInfo)` — per-channel blocking | No channel-level blocking mechanism | **Gap**: no blocking mechanism |
| Multi-input alignment | `SingleCheckpointBarrierHandler` tracks `alignedChannels` set, blocks until all barriers received | Production code processes barriers per-operator sequentially, no cross-input coordination | **Gap**: exactly-once in multi-input (join/cogroup) operators not implemented |
| Unaligned checkpoint | Full support via `AlignmentType.UNALIGNED`, channel state in `OperatorSnapshotFutures` | No support | **Gap**: unaligned checkpoint not implemented |
| Alternating mode | Aligned with timeout → unaligned via `BarrierHandlerState.alignedCheckpointTimeout()` | Not applicable (no alignment) | **Gap**: no alternating mode |
| Alignment metrics | `markAlignmentStart()`/`end()`, `addProcessedBytes()`, alignment duration tracking | Not implemented | **Improvement** |

### Classification by Dimension

| Finding | Classification | Severity | Evidence |
|---------|---------------|----------|----------|
| BarrierAligner has no production caller | **Hollow** (complete class, unused) | **P0** | nop-stream: `BarrierAligner.java` — 0 production callers, 3 test files only. Javadoc self-admission |
| No multi-input barrier alignment at runtime | **Gap** | **P0** | nop-stream: `AbstractStreamOperator.processBarrier()` — sequential per-input handling; no cross-input coordination |
| No unaligned checkpoint support | **Gap** | **P1** | nop-stream: no equivalent of Flink's `AlignmentType.UNALIGNED` or channel state persistence |
| No channel blocking mechanism | **Gap** | **P1** | nop-stream: no `blockConsumption()`/`resumeConsumption()` equivalent in `Input`/`InputGate` |

---

## 2. CheckpointCoordinator Coordination Flow

### Flink CheckpointCoordinator

**Class**: `org.apache.flink.runtime.checkpoint.CheckpointCoordinator`
**Role**: Central orchestrator on JobManager

**Trigger flow:**
1. `startTriggeringCheckpoint()` calculates checkpoint plan → gets ID from `CheckpointIDCounter` → creates `PendingCheckpoint` → initializes `CheckpointStorageLocation` → triggers operator coordinators → snapshots master hooks → calls `triggerCheckpointRequest()`
2. `triggerCheckpointRequest()` sends barrier to all source tasks via `Execution.triggerCheckpoint()`
3. `CheckpointRequestDecider` gates trigger requests (respects minPause, maxConcurrent, backpressure)

**ACK collection:**
1. `receiveAcknowledgeMessage()` → `pendingCheckpoint.acknowledgeTask()` merges `OperatorState`
2. When fully acknowledged, calls `completePendingCheckpoint()`
3. `completePendingCheckpoint()` → `pendingCheckpoint.finalizeCheckpoint()` serializes metadata → `completedCheckpointStore.addCheckpointAndSubsumeOldestOne()` → notify tasks of completion

**Subsume strategy:**
- `CompletedCheckpointStore.addCheckpointAndSubsumeOldestOne()` returns the subsumed checkpoint for cleanup
- `maxNumberOfRetainedCheckpoints` configurable
- Savepoints cannot be subsumed (`canBeSubsumed()` returns false)
- `SharedStateRegistry` manages shared state reference counting

**Pending management:**
- `pendingCheckpoints` map (ConcurrentHashMap for key-based access, but guarded by lock for mutation)
- `CheckpointFailureManager` handles failure counting and job failure decision

### nop-stream CheckpointCoordinator

**Class**: `io.nop.stream.runtime.checkpoint.CheckpointCoordinator`
**Role**: Central orchestrator

**Trigger flow:**
1. `tryTriggerPendingCheckpoint(CheckpointType)` — creates `PendingCheckpoint` via counter, validates no overlapping checkpoint
2. Scheduled via `ScheduledExecutorService` with configurable interval
3. Barrier sent to source tasks via `JobCoordinator.triggerCheckpoint()` → RPC call to source tasks

**ACK collection:**
1. `acknowledgeTask(TaskLocation, checkpointId, TaskStateSnapshot)` → `pendingCheckpoint.acknowledgeTask()`
2. If `pendingCheckpoint.isFullyAcknowledged()`, calls `completePendingCheckpoint(CompletedCheckpoint)` which persists to storage, builds epoch manifest, and notifies listeners
3. `completePendingCheckpoint()` → `checkpointStorage.storeCheckPoint()` → `buildEpochManifest()` → `checkpointStorage.storeEpochManifest()` → cleanup old checkpoints

**Subsume strategy:**
- `cleanupCheckpoints()` removes all checkpoints older than `latestCompletedCheckpoint`
- `maxRetainedCheckpoints` config controls how many to keep
- No equivalent of Flink's `CompletedCheckpointStore.addCheckpointAndSubsumeOldestOne()` — nop-stream does a bulk cleanup after completion

**Pending management:**
- `pendingCheckpoints` (ConcurrentHashMap)
- `consecutiveTriggerFailures` tracked, with `maxConsecutiveCheckpointFailures` threshold
- Failed participant commit retry (`ConcurrentSkipListMap`)

### Comparison

| Aspect | Flink | nop-stream | Gap |
|--------|-------|-----------|-----|
| Trigger flow | Full orchestration with plan calculation, operator coordinator trigger, master hooks | Simpler: creates PendingCheckpoint, sends barrier | **Improvement**: nop-stream lacks operator coordinator and master hook pre/post triggers |
| ACK collection | `receiveAcknowledgeMessage()` → task-level + coordinator-level + master-level ACK tracking | `acknowledgeTask()` → task-level only | **Gap**: no operator coordinator / master hook ACK tracking |
| Checkpoint request gating | `CheckpointRequestDecider` — respects minPause, backpressure, concurrent limit | Simple check: `numPendingCheckpoints < maxConcurrentCheckpoints` | **Improvement**: simpler, no backpressure gating |
| Subsume strategy | `CompletedCheckpointStore.addCheckpointAndSubsumeOldestOne()` — precise oldest-removal | `cleanupCheckpoints()` — bulk cleanup of older checkpoints | **Improvement**: functionally equivalent but less precise |
| Failure management | `CheckpointFailureManager` with failure reason classification, continuous counter, `FailJobCallback` | `consecutiveTriggerFailures` threshold, `incrementTriggerFailures()` | **Improvement**: simpler but less sophisticated failure handling |
| findCompletedCheckpointId | N/A (coordinator-driven, no local alignment query) | `BarrierAligner.findCompletedCheckpointId()` — picks min ID present on all inputs | **Hollow**: unused dead code (BarrierAligner unwired) |
| Hard-coded maxConcurrent=1 | `maxConcurrentCheckpoints` configurable (default 1) | `effectiveMaxConcurrent = Math.min(1, config.getMaxConcurrentCheckpoints())` | **Bug**: config ignored, hard-coded to 1 |
| Shared state registry | `SharedStateRegistry` reference counting | None | **Gap** |

### Classification

| Finding | Classification | Severity | Evidence |
|---------|---------------|----------|----------|
| maxConcurrentCheckpoints config ignored, hard-coded to 1 | **Bug** | **P2** | nop-stream: `CheckpointCoordinator.java:196` — `int effectiveMaxConcurrent = Math.min(1, ...)` |
| No operator coordinator ACK tracking | **Gap** | **P2** | nop-stream: `acknowledgeTask()` only tracks `TaskLocation` → `TaskStateSnapshot` |
| No shared state registry for reference counting | **Gap** | **P2** | nop-stream: no `SharedStateRegistry` equivalent |
| Bulk cleanup instead of precise subsume | **Improvement** | **P3** | nop-stream: `cleanupCheckpoints()` iterates all, vs Flink's precise subsume |

---

## 3. Checkpoint Storage

### Flink

**Interface**: `CompletedCheckpointStore` + `CheckpointStorage` (two-level abstraction)
**Implementations**: `StandaloneCompletedCheckpointStore` (in-memory), `ZooKeeperCompletedCheckpointStore` (HA), `FileSystemCompletedCheckpointStore`

**Key contract**:
- `addCheckpointAndSubsumeOldestOne()` — transactional add with subsume
- `getLatestCheckpoint()` — for recovery
- `requiresExternalizedCheckpoints()` — controls whether metadata must survive JM failover
- `getSharedStateRegistry()` — shared state ref-counting across checkpoints

**Recovery**: `CompletedCheckpointStore` provides the latest checkpoint for restoration.

### nop-stream

**Interface**: `ICheckpointStorage` (single interface for all operations)
**Implementations**:
- `LocalFileCheckpointStorage` — directory-based: `<baseDir>/<jobId>/<pipelineId>/<checkpointId>.checkpoint`
- `JdbcCheckpointStorage` — database-backed: `stream_checkpoint` + `stream_epoch_manifest` tables

**Key contract** (interface methods):
- `storeCheckPoint(CompletedCheckpoint)` → persist to storage
- `getLatestCheckpoint(jobId, pipelineId)` → single latest by timestamp desc
- `getAllCheckpoints(jobId)` → list all
- `deleteCheckpoint()` / `deleteAllCheckpoints()` → cleanup
- `storeEpochManifest()` / `loadLatestEpochManifest()` → epoch-level metadata
- `storeSavepoint()` / `loadSavepoint()` → savepoint support

**Safety features in `LocalFileCheckpointStorage`**:
- Atomic write via temp file + rename
- Read-write lock protection
- Path traversal prevention (`validatePath()`)
- Safe ID validation (`SAFE_ID_PATTERN` = `[a-zA-Z0-9_-]+`)

### Comparison

| Aspect | Flink | nop-stream | Gap |
|--------|-------|-----------|-----|
| HA support | ZooKeeper-based, multiple HA store implementations | Single-machine `LocalFileCheckpointStorage`; `JdbcCheckpointStorage` provides DB persistence | **Gap**: no ZooKeeper/consensus-based HA store |
| Shared state | `SharedStateRegistry` with ref-counting | None | **Gap**: no shared state dedup across checkpoints |
| Checkpoint metadata | `CompletedCheckpoint` with `storageLocation`, `externalPointer` for file path | `CompletedCheckpoint` with `taskStates` map; serialized via `CheckpointSerDe` (JSON) | No gap — both store full state map |
| Epoch manifest | N/A (single concept: checkpoint) | `EpochManifest` + `EpochState` — epoch-based recovery metadata | **Doc**: nop-stream has unique epoch concept not present in Flink |
| Savepoint | Full savepoint support: `SavepointType`, `SavepointFormatType` (canonical/native) | `storeSavepoint()` / `loadSavepoint()` via `ICheckpointStorage` | No gap |
| Durable checkpoint property | `CheckpointProperties` controls discard-on-subsumed/finished/cancelled/failed/suspended | `ProcessingGuarantee.requiresDurableCheckpoint()` | **Improvement**: simpler discard policy |
| CheckpointSerDe format | Multiple: `CheckpointMetadataSerializer` (JSON for metadata), state handles use FS/DFS | `CheckpointSerDe` (JSON via `JsonTool`) | No gap |
| Serialization schema versioning | Schema versioning metadata in serialization format | No schema versioning in top-level envelope | **Improvement**: lacks schema versioning for data model evolution |

### Classification

| Finding | Classification | Severity | Evidence |
|---------|---------------|----------|----------|
| No HA checkpoint store | **Gap** | **P2** | nop-stream: no ZooKeeper/consensus-based `CompletedCheckpointStore` equivalent |
| No shared state registry | **Gap** | **P2** | Flink `SharedStateRegistry` not present in nop-stream |
| LocalFileCheckpointStorage has path traversal protection | — | — | Evidence of security-conscious design |
| JdbcCheckpointStorage has fragile duplicate key detection | **Improvement** | **P3** | `isDuplicateKeyException()` uses string matching on error messages |

---

## 4. State Snapshot Path

### Flink (Async Two-Phase)

1. **Barrier received** → `StreamOperator.snapshotState()` returns `OperatorSnapshotFutures`
2. `OperatorSnapshotFutures` contains **6 async futures**:
   - `keyedStateManagedFuture` — managed keyed state
   - `keyedStateRawFuture` — raw keyed state
   - `operatorStateManagedFuture` — managed operator state
   - `operatorStateRawFuture` — raw operator state
   - `inputChannelStateFuture` — in-flight input buffers (unaligned)
   - `resultSubpartitionStateFuture` — in-flight output buffers (unaligned)
3. `SubtaskCheckpointCoordinator` waits for ALL futures to complete
4. Then reports `TaskStateSnapshot` back to JobManager via `AcknowledgeCheckpoint` message
5. State materialization (spilling to DFS) happens asynchronously — the JM receives ack before state files are fully written

### nop-stream (Synchronous)

1. **Barrier received** → operator processes immediately via `processBarrier()` method
2. Each operator snapshots its state **synchronously** (no future-based pattern)
3. `TaskStateSnapshot` is collected and returned directly in `acknowledgeTask()`
4. Storage write happens synchronously in `completePendingCheckpoint()` via `checkpointStorage.storeCheckPoint()`

### Comparison

| Aspect | Flink | nop-stream | Gap |
|--------|-------|-----------|-----|
| Snapshot execution | Async via `OperatorSnapshotFutures` (6 concurrent futures) | Synchronous per-operator | **Gap**: no async snapshot pipeline |
| State reporting | `AcknowledgeCheckpoint` sent before state files fully materialized | ACK sent after state fully written | **Design simplification**: synchronous is simpler but blocks the pipeline longer |
| Channel state persistence | `inputChannelStateFuture` + `resultSubpartitionStateFuture` (for unaligned) | Not applicable (no unaligned) | **Gap**: no channel state persistence capability |
| Parallelism in snapshot | All 6 futures run in parallel; materialization in I/O executor | Sequential per-operator | **Improvement**: nop-stream could benefit from async for large state |
| State size tracking | `getStateSize()` sums operator state sizes | `estimateSize()` on `CompletedCheckpoint` | No gap |

### Classification

| Finding | Classification | Severity | Evidence |
|---------|---------------|----------|----------|
| No async snapshot pipeline | **Gap** | **P2** | nop-stream: synchronous per-operator; no `OperatorSnapshotFutures` equivalent |
| No channel state persistence | **Gap** | **P2** | nop-stream: no `inputChannelStateFuture`/`resultSubpartitionStateFuture` (also related to unaligned checkpoint gap) |

---

## 5. Exactly-Once Level Implementation

### Flink

**CheckpointOptions.AlignmentType** controls four levels:

| Level | Alignment | Channel State | Use Case |
|-------|-----------|---------------|----------|
| `ALIGNED` | Block channels until all barriers arrive | No | Exactly-once, low throughput sensitivity |
| `UNALIGNED` | No blocking — persist in-flight buffers | Yes (input + output) | Exactly-once, high throughput / long backpressure |
| `FORCED_ALIGNED` | Force alignment even if unaligned configured | No | Downstream doesn't support unaligned |
| `AT_LEAST_ONCE` | No alignment, no blocking | No | Throughput-over-correctness |

### nop-stream

**ProcessingGuarantee** enum defines four levels:

| Level | barrierAlignment | requiresDurableCheckpoint | Implementation Status |
|-------|-----------------|--------------------------|----------------------|
| `STRICT_EXACTLY_ONCE` | true | true | **Hollow**: `barrierAlignment=true` has no effect — BarrierAligner is dead code |
| `AT_LEAST_ONCE` | false | false | Implemented — no alignment, no durability |
| `EFFECTIVELY_ONCE` | false | true | Implemented — no alignment, with durability |
| `BEST_EFFORT` | false | false | Implemented — no alignment, no durability |

### Comparison

| Aspect | Flink | nop-stream | Gap |
|--------|-------|-----------|-----|
| Aligned exactly-once | `SingleCheckpointBarrierHandler` state machine with channel blocking | `ProcessingGuarantee.STRICT_EXACTLY_ONCE` defined but BarrierAligner dead code | **Hollow**: configuration exists but alignment never happens |
| Unaligned exactly-once | Full support with channel state persistence | Not supported | **Gap**: completely absent |
| At-least-once | `CheckpointBarrierTracker` — no blocking, barrier tracking only | Implemented (no alignment) | No gap |
| Effectively-once | N/A (Flink has no `EFFECTIVELY_ONCE` level) | ProcessingGuarantee.EFFECTIVELY_ONCE | **Doc**: nop-stream has an additional semantics level |
| Config knob wiring | `CheckpointOptions.AlignmentType` consumed by `CheckpointedInputGate` | `ProcessingGuarantee.isBarrierAlignment()` not consumed by any production code | **Hollow**: configuration exists but the consumer is unwired |

### Classification

| Finding | Classification | Severity | Evidence |
|---------|---------------|----------|----------|
| STRICT_EXACTLY_ONCE alignment config has no runtime effect | **Hollow** | **P0** | `ProcessingGuarantee.isBarrierAlignment()` returns true for STRICT_EXACTLY_ONCE, but no production code calls BarrierAligner. BarrierAligner has 0 production callers (only tests). |
| Unaligned checkpoint entirely missing | **Gap** | **P1** | No equivalent of Flink's `AlignmentType.UNALIGNED`, no channel state persistence |
| AT_LEAST_ONCE / EFFECTIVELY_ONCE / BEST_EFFORT functional | — | — | These levels work correctly with nop-stream's simpler per-operator barrier handling |
| EFFECTIVELY_ONCE is unique to nop-stream | **Doc** | — | nop-stream adds a semantics level not present in Flink |

---

## 6. Failure Recovery Path

### Flink

**Recovery chain:**
1. **CheckpointFailureManager** detects excessive failures → calls `FailJobCallback.failJob()`
2. **ExecutionFailureHandler** computes failure handling result:
   - `FailoverStrategy` determines which vertices to restart
   - `RestartBackoffTimeStrategy` determines if/when restart can happen
3. **Two restoration paths:**
   - `restoreLatestCheckpointedStateToAll()` — global restore (restores coordinators with `RESTORE_OR_RESET`)
   - `restoreLatestCheckpointedStateToSubtasks()` — regional/local failover (does NOT restore coordinators)
4. **`RestartPipelinedRegionFailoverStrategy`** — restart only pipelined regions affected (default)
5. **`RestartAllFailoverStrategy`** — restart all vertices (legacy)

### nop-stream

**Recovery chain:**
1. `restoreFromCheckpoint()` — reads latest `CompletedCheckpoint` from storage
2. `restoreLatestEpochManifest()` — reads epoch metadata for recovery
3. `globalRecovery()` — appears to be a full restart mechanism
4. No region-level or partial recovery

### Comparison

| Aspect | Flink | nop-stream | Gap |
|--------|-------|-----------|-----|
| Failure detection | `CheckpointFailureManager` with failure reason classification | `consecutiveTriggerFailures` + `incrementTriggerFailures()` | **Improvement**: simpler failure counting |
| Failover scope | Region-level (`RestartPipelinedRegionFailoverStrategy`) or full (`RestartAllFailoverStrategy`) | Global only (`globalRecovery()`) | **Gap**: no partial/region failover |
| State restoration granularity | Per-`ExecutionJobVertex` with subtask-level state assignment | Full restore from latest `CompletedCheckpoint` | **Gap**: no subtask-level granular restoration |
| Operator coordinator restore | Optional: `RESTORE_OR_RESET` mode | Not applicable (no operator coordinator concept) | **Gap** |
| Epoch recovery | No concept of epoch | `EpochManifest` + `restoreLatestEpochManifest()` | **Doc**: nop-stream has unique epoch-level recovery metadata |

### Classification

| Finding | Classification | Severity | Evidence |
|---------|---------------|----------|----------|
| No partial/region failover | **Gap** | **P2** | nop-stream: `globalRecovery()` — no region-level equivalent to Flink's `RestartPipelinedRegionFailoverStrategy` |
| No subtask-level granular restoration | **Gap** | **P2** | nop-stream: full restore only; Flink: `restoreLatestCheckpointedStateToSubtasks()` |
| Epoch-based recovery is unique to nop-stream | **Doc** | — | `EpochManifest` + `loadLatestEpochManifest()` not present in Flink |

---

## 7. Abort Control Channel

### Flink

Flink has a **dedicated abort channel**:

1. **`CancelCheckpointMarker`** — separate `RuntimeEvent` subclass, sent as a data channel event
2. **CheckpointedInputGate** dispatches `CancelCheckpointMarker` → `barrierHandler.processCancellationBarrier()`
3. **SingleCheckpointBarrierHandler.processCancellationBarrier()**:
   - Resets alignment state for the cancelled checkpoint
   - Resumes all blocked channels
   - Calls `notifyAbort()` on the task
4. **Abort message from JM to tasks**: `sendAbortedMessages()` → `execution.notifyCheckpointAborted()` → sends `CancelCheckpointMarker` downstream
5. **Abort failure reasons** (from `CheckpointFailureReason`): `CHECKPOINT_EXPIRED`, `CHECKPOINT_SUBSUMED`, `CHECKPOINT_COORDINATOR_SHUTDOWN`, `JOB_FAILOVER_REGION`, etc.

### nop-stream

**Abort flow:**
1. `CheckpointCoordinator.abortPendingCheckpoint(PendingCheckpoint, String reason)`:
   - Calls `pendingCheckpoint.abort(reason, cause)`
   - Calls `abortHandler` consumer if set
   - `abortHandler` is set by `JobCoordinator` and is wired to send abort messages to tasks
2. `PendingCheckpoint.abort()`:
   - Transitions status to `ABORTED`
   - Fails the `CompletableFuture`
   - Sets `isDisposed = true`
3. **No dedicated cancellation barrier event type** — nop-stream does not have an equivalent of `CancelCheckpointMarker`
4. The `abortHandler` callback mechanism means abort behavior is caller-defined rather than built into the data channel

### Comparison

| Aspect | Flink | nop-stream | Gap |
|--------|-------|-----------|-----|
| Dedicated abort event | `CancelCheckpointMarker` (RuntimeEvent) | No dedicated event type | **Gap**: no `CancelCheckpointMarker` equivalent |
| Abort message routing | Via data channel (event in stream) | Via `abortHandler` callback (application-level) | **Improvement**: nop-stream's approach is more flexible but less deterministic |
| Abort on timeout | `CheckpointCanceller` scheduled timer → `abortPendingCheckpoint()` → `sendAbortedMessages()` | `timeoutScheduler` → `abortPendingCheckpoint()` | No gap — both have timeout-based abort |
| Barrier alignment cancellation | `processCancellationBarrier()` → resume blocked channels | Not applicable (no alignment) | **Gap**: no alignment cancellation needed because alignment not implemented |
| Abort with subsumption | `CHECKPOINT_SUBSUMED` reason, checkpoint cleanup | `cleanupCheckpoints()` removes older | No gap |
| Abort signal to downstream operators | `CancelCheckpointMarker` flows with data stream | Not explicitly implemented | **Gap**: no mechanism to propagate abort to downstream operators via data channel |

### Classification

| Finding | Classification | Severity | Evidence |
|---------|---------------|----------|----------|
| No dedicated cancel checkpoint marker event type | **Gap** | **P1** | Flink: `CancelCheckpointMarker` extends `RuntimeEvent`; nop-stream: no equivalent |
| No abort propagation via data channel | **Gap** | **P2** | nop-stream: abort is callback-based, not embedded in data stream |
| abortHandler pattern is functional for JM-coordinated abort | — | — | `CheckpointCoordinator.setAbortHandler()` → `JobCoordinator` wires it to task RPC |

---

## 8. Gap Table

| # | Finding | Classification | Severity | Flink Reference | nop-stream Reference | Repair Suggestion |
|---|---------|---------------|----------|-----------------|---------------------|-------------------|
| 1 | BarrierAligner has no production caller | **Hollow** | **P0** | `SingleCheckpointBarrierHandler` (multi-input alignment) | `BarrierAligner.java` — 0 production callers, only tests | Wire BarrierAligner into `GraphModelCheckpointExecutor` or remove dead code |
| 2 | No multi-input barrier alignment at runtime | **Gap** | **P0** | `CheckpointedInputGate` → `SingleCheckpointBarrierHandler` with state machine | `AbstractStreamOperator.processBarrier()` — sequential per-input only | Implement multi-input alignment in the operator barrier handling path |
| 3 | STRICT_EXACTLY_ONCE alignment config has no effect | **Hollow** | **P0** | `CheckpointOptions.AlignmentType.ALIGNED` consumed by barrier handler | `ProcessingGuarantee.isBarrierAlignment()` returns true; no consumer | Wire alignment config to enable BarrierAligner or inline alignment logic |
| 4 | No CancelCheckpointMarker event type | **Gap** | **P1** | `CancelCheckpointMarker` extends `RuntimeEvent` | No equivalent | Add CancelCheckpointMarker event type and data channel handling |
| 5 | No unaligned checkpoint support | **Gap** | **P1** | `AlignmentType.UNALIGNED` + channel state in `OperatorSnapshotFutures` | Not present | Implement unaligned checkpoint mode with channel state persistence |
| 6 | No channel blocking mechanism | **Gap** | **P1** | `InputGate.blockConsumption(channelInfo)` | No `blockConsumption()`/`resumeConsumption()` on `Input` | Add channel blocking API to `Input`/`InputGate` |
| 7 | No partial/region failover | **Gap** | **P2** | `RestartPipelinedRegionFailoverStrategy` | `globalRecovery()` — full restart only | Implement region-level failover |
| 8 | No subtask-level granular restoration | **Gap** | **P2** | `restoreLatestCheckpointedStateToSubtasks()` | Full restore only | Add subtask-level state restoration |
| 9 | No async snapshot pipeline | **Gap** | **P2** | `OperatorSnapshotFutures` with 6 concurrent futures | Synchronous per-operator | Add async snapshot futures for state materialization |
| 10 | maxConcurrentCheckpoints config hard-coded to 1 | **Bug** | **P2** | `maxConcurrentCheckpoints` configurable | `CheckpointCoordinator.java:196`: `Math.min(1, ...)` | Remove hard-coded min(1, ...) to respect configured value |
| 11 | No HA checkpoint store | **Gap** | **P2** | `ZooKeeperCompletedCheckpointStore` | `LocalFileCheckpointStorage` (single-machine) | Implement consensus-based HA store |
| 12 | No shared state registry | **Gap** | **P2** | `SharedStateRegistry` with reference counting | None | Add shared state registry for checkpoint dedup |
| 13 | No abort propagation via data channel | **Gap** | **P2** | `CancelCheckpointMarker` flows in data stream | Callback-based only | Add data channel abort event propagation |
| 14 | No operator coordinator ACK tracking | **Gap** | **P2** | JM tracks coordinator + master state ACKs | Task-level ACK only | Add coordinator/master state ACK tracking |
| 15 | CheckpointSerDe lacks schema versioning | **Improvement** | **P3** | Schema versioning in serialization format | No schema versioning in envelope | Add schema version field to serialization format |
| 16 | Bulk cleanup instead of precise subsume | **Improvement** | **P3** | `addCheckpointAndSubsumeOldestOne()` precise subsume | `cleanupCheckpoints()` bulk iteration | Implement precise subsume pattern |
| 17 | JdbcCheckpointStorage fragile duplicate key detection | **Improvement** | **P3** | N/A | `isDuplicateKeyException()` string matching | Use standard SQLState codes |
| 18 | EFFECTIVELY_ONCE semantics unique to nop-stream | **Doc** | — | N/A | `ProcessingGuarantee.EFFECTIVELY_ONCE` | Document as intentional design difference |
| 19 | Epoch-based recovery unique to nop-stream | **Doc** | — | N/A | `EpochManifest` + `loadLatestEpochManifest()` | Document as intentional design difference |

---

## 9. Roadmap Gap Verification

### Gap 1: "BarrierAligner unplugged"

**Status**: ✅ **Confirmed with source-level evidence**

**Evidence**:
- `BarrierAligner` class exists at `nop-stream-runtime/.../barrier/BarrierAligner.java` with complete implementation
- Zero production callers — only 3 test files invoke `processBarrier()`
- Javadoc states: `"当前 GraphModelCheckpointExecutor 未使用"` ("currently not used by GraphModelCheckpointExecutor")
- The production barrier path goes through `StreamTaskInvokable` → `ChainingOutput` → `AbstractStreamOperator` directly, bypassing `BarrierAligner`
- `ProcessingGuarantee.STRICT_EXACTLY_ONCE` and `CheckpointConfig.barrierAlignmentTimeout` exist but have no effect (no consumer)

### Gap 2: "findCompletedCheckpointId complexity"

**Status**: ✅ **Confirmed with source-level analysis**

**Evidence**:
- `BarrierAligner.findCompletedCheckpointId()` (private method in `BarrierAligner.java`) finds the minimum checkpoint ID that appears on ALL input TreeMaps
- Complexity: O(n * m) where n = checkpoint IDs, m = inputs — acceptable for typical cases with few inputs (<10)
- However, this method is part of the **dead code** BarrierAligner; it has no production impact
- The method has a starvation edge case: if one input never receives a particular barrier, it blocks completion of lower IDs — tested in `TestBarrierAlignerStarvation.java`
- **Conclusion**: The complexity concern is valid but moot because the code is unused. If BarrierAligner were wired in, `findCompletedCheckpointId()` would need optimization for inputs with skewed barrier arrival times.

### Gap 3: "Abort channel unwired"

**Status**: ✅ **Confirmed with source-level evidence**

**Evidence**:
- Flink has a dedicated `CancelCheckpointMarker` event type that flows through the data channel to all downstream operators
- nop-stream has no `CancelCheckpointMarker` equivalent — no dedicated event type for cancellation
- nop-stream's `CheckpointCoordinator.abortPendingCheckpoint()` uses a callback pattern (`abortHandler` Consumer) set by `JobCoordinator`
- This means abort signals are routed through application-level RPC rather than the data channel
- There is no mechanism to propagate abort signals to downstream operators via the data stream
- The `BarrierAligner.abortAll()` method exists but is unused (consistent with BarrierAligner being dead code)

---

## References

### Flink Source
- `org.apache.flink.runtime.checkpoint.CheckpointCoordinator` — `flink-runtime/.../checkpoint/CheckpointCoordinator.java`
- `org.apache.flink.runtime.checkpoint.PendingCheckpoint` — `flink-runtime/.../checkpoint/PendingCheckpoint.java`
- `org.apache.flink.runtime.checkpoint.CompletedCheckpointStore` — `flink-runtime/.../checkpoint/CompletedCheckpointStore.java`
- `org.apache.flink.runtime.checkpoint.CheckpointBarrier` — `flink-runtime/.../io/network/api/CheckpointBarrier.java`
- `org.apache.flink.runtime.checkpoint.CancelCheckpointMarker` — `flink-runtime/.../io/network/api/CancelCheckpointMarker.java`
- `org.apache.flink.runtime.checkpoint.CheckpointOptions` — `flink-runtime/.../checkpoint/CheckpointOptions.java`
- `org.apache.flink.runtime.checkpoint.CheckpointFailureManager` — `flink-runtime/.../checkpoint/CheckpointFailureManager.java`
- `org.apache.flink.streaming.runtime.io.checkpointing.CheckpointedInputGate` — `flink-streaming-java/.../io/checkpointing/CheckpointedInputGate.java`
- `org.apache.flink.streaming.runtime.io.checkpointing.CheckpointBarrierHandler` — `flink-streaming-java/.../io/checkpointing/CheckpointBarrierHandler.java`
- `org.apache.flink.streaming.runtime.io.checkpointing.SingleCheckpointBarrierHandler` — `flink-streaming-java/.../io/checkpointing/SingleCheckpointBarrierHandler.java`
- `org.apache.flink.streaming.runtime.io.checkpointing.CheckpointBarrierTracker` — `flink-streaming-java/.../io/checkpointing/CheckpointBarrierTracker.java`
- `org.apache.flink.streaming.api.operators.OperatorSnapshotFutures` — `flink-streaming-java/.../operators/OperatorSnapshotFutures.java`
- `org.apache.flink.runtime.io.network.partition.consumer.CheckpointableInput` — `flink-runtime/.../partition/consumer/CheckpointableInput.java`

### nop-stream Source
- `io.nop.stream.runtime.checkpoint.CheckpointCoordinator` — `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java`
- `io.nop.stream.runtime.checkpoint.PendingCheckpoint` — `nop-stream-runtime/.../checkpoint/PendingCheckpoint.java`
- `io.nop.stream.runtime.checkpoint.barrier.BarrierAligner` — `nop-stream-runtime/.../barrier/BarrierAligner.java`
- `io.nop.stream.runtime.checkpoint.barrier.AlignedBarrier` — `nop-stream-runtime/.../barrier/AlignedBarrier.java`
- `io.nop.stream.core.checkpoint.CheckpointBarrier` — `nop-stream-core/.../checkpoint/CheckpointBarrier.java`
- `io.nop.stream.core.checkpoint.storage.ICheckpointStorage` — `nop-stream-core/.../checkpoint/storage/ICheckpointStorage.java`
- `io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage` — `nop-stream-runtime/.../storage/LocalFileCheckpointStorage.java`
- `io.nop.stream.runtime.checkpoint.storage.JdbcCheckpointStorage` — `nop-stream-runtime/.../storage/JdbcCheckpointStorage.java`
- `io.nop.stream.runtime.checkpoint.storage.CheckpointSerDe` — `nop-stream-runtime/.../storage/CheckpointSerDe.java`
- `io.nop.stream.core.checkpoint.CompletedCheckpoint` — `nop-stream-core/.../checkpoint/CompletedCheckpoint.java`
- `io.nop.stream.core.checkpoint.CheckpointConfig` — `nop-stream-core/.../checkpoint/CheckpointConfig.java`
- `io.nop.stream.core.checkpoint.ProcessingGuarantee` — `nop-stream-core/.../checkpoint/ProcessingGuarantee.java`
- `io.nop.stream.core.checkpoint.EpochManifest` — `nop-stream-core/.../checkpoint/EpochManifest.java`
- `io.nop.stream.runtime.checkpoint.CheckpointPlanBuilder` — `nop-stream-runtime/.../checkpoint/CheckpointPlanBuilder.java`
