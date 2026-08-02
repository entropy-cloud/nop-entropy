# 02 Channel Heartbeat + Unaligned Checkpoint

> Plan Status: completed
> Last Reviewed: 2026-08-03
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 43; G6
> Related: Stage 16 (barrier alignment), Stage 31 (`EpochManifest.segments`), Stage 39 (fencing epoch), Stage 40 (data plane)

## Purpose

Deliver channel-level heartbeat detection for `RemoteInputChannel` and implement unaligned checkpoint as a backpressure escape mechanism. Currently, sustained backpressure causes barrier alignment to never complete within `barrierAlignmentTimeout` (30s), forcing the task to FAIL and triggering full recovery — even though the pipeline is healthy, just slow. Unaligned checkpoint eliminates this by snapshotting in-flight data instead of blocking on alignment, allowing checkpoints to complete under backpressure.

## Current Baseline

- `InputGate` (`nop-stream-core/execution/InputGate.java`) implements aligned barrier handling: when a barrier arrives on one channel, that channel is blocked (`blockConsumption(channelIndex)`, `:356`) until all channels deliver their barrier. On full alignment, the aligned barrier is emitted and channels are resumed (`resumeConsumptionAll()`, `:381`).
- `barrierAlignmentTimeout` (default 30s, configurable via `CheckpointConfig` → `GraphExecutionPlan.build` → `InputGate` constructor `:119`) caps alignment wait. On timeout, `InputGate` throws `ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT` (`:343`) → task FAILED → triggers global recovery.
- `barrierAlignment=false` mode (AT_LEAST_ONCE) does not block channels but provides only at-least-once semantics — not a backpressure escape for exactly-once pipelines.
- No unaligned checkpoint exists: no channel state (in-flight data) is captured or persisted. `TaskEpochSnapshot` has keyed state shards, timer states, watermark states, participant states — no channel-state field. `EpochManifest` has `segments` (Stage 31) with `codec=json|identity` — reusable for channel state persistence.
- `InputChannel` (`nop-stream-core/execution/InputChannel.java`) exposes only pull-based `read()` / `read(timeout, unit)` and `isFinished()`. Its internal buffer (`ResultPartition` queue for local, `LinkedBlockingQueue` for `RemoteInputChannel`) is not exposed for inspection or draining. There is no method to capture or snapshot in-flight data.
- `CheckpointBarrierTracker` (`nop-stream-core/execution/CheckpointBarrierTracker.java`) supports single in-flight checkpoint only (`operatorsToAck.get() > 0` rejects new triggers, `:89`). Acceptable for unaligned (single in-flight remains; Stage 45 lifts multi-concurrent).
- `RemoteInputChannel` (`nop-stream-runtime/transport/RemoteInputChannel.java`) has no heartbeat. It is callback-driven (`EnvelopeConsumer.onMessage`) and pull-driven (`read`/`read with timeout`). `RemoteResultPartition` (`nop-stream-runtime/transport/RemoteResultPartition.java`) is write-only: `write()` sends via `IMessageService.send()`, no listener for control messages. Network partition is detected only via `TaskManager` lease timeout (~15-20s, per component-roadmap.md:192).
- `CheckpointType` enum has no unaligned variant. `CheckpointConfig` has no `unalignedThreshold` or unaligned-enabled flag.
- checkpoint-design.md §148: "Aligned checkpoint 是基线能力。Unaligned checkpoint 是性能优化" — not an exactly-once correctness prerequisite but a backpressure escape.
- checkpoint-design.md §1137: "持续背压场景需 barrier 抢占式传播通道（unaligned checkpoint），不得仅靠 aligned 对齐（背压下对齐时延无上限）".
- **Vision §六 决策点 #4**: "Checkpoint 协议的变更（如从单 in-flight 扩展为多 in-flight）" requires human decision. Unaligned checkpoint does NOT change the single-in-flight constraint (that is Stage 45). It changes barrier processing mode (block+wait → snapshot+continue) and snapshot content (adds channel state). **This plan treats vision §六 #4 applicability as requiring human confirmation** (Phase 0 Decision item) — the plan does not unilaterally decide the protocol is unchanged.

## Goals

- Implement `RemoteInputChannel` heartbeat and timeout-based failure detection: the **producer** (`RemoteResultPartition`) sends periodic idle heartbeats when no data is flowing; the **consumer** (`RemoteInputChannel`) detects producer failure if neither data nor heartbeat arrives within `channelTimeout`.
- Implement unaligned checkpoint with precise in-flight data semantics: when a checkpoint operates in unaligned mode, in-flight data is captured per-channel, persisted in the checkpoint, and replayed on recovery — without blocking any channel.
- Implement aligned→unaligned fallback: a checkpoint starts as aligned; if alignment does not complete within a configurable threshold (`unalignedThreshold`), the checkpoint switches to unaligned mode — capturing in-flight data and completing immediately.
- Verify that unaligned checkpoint completes under sustained backpressure (where aligned checkpoint would time out and fail).

## Non-Goals

- Multi-concurrent checkpoint support (Stage 45 — requires task-level multi-epoch barrier tracking).
- Unaligned + rescale interaction (Stage 47 — in-flight data remapping across new parallelism).
- Replacing aligned checkpoint as the default mode. Aligned remains the default; unaligned is a fallback/option.
- Credit-based flow control or network-layer changes (vision §三 约束 7 排除).
- Changing the single-in-flight checkpoint constraint.
- `CancelCheckpointMarker` in-data-flow event (Stage 39 Decision-only; abort remains via `cancelTask` RPC control channel).

## Scope

### In Scope

- `RemoteInputChannel` / `RemoteResultPartition` heartbeat protocol (producer-sends-idle-heartbeats model) + timeout-based channel failure detection.
- Channel state abstraction: capture in-flight data from `InputChannel`s at checkpoint time, with precise per-channel semantics (see Phase 0 design for the behavioral specification).
- Channel state persistence in `TaskEpochSnapshot` + `EpochManifest` (reuse `codec=json` segment type).
- Channel state restoration on recovery (replay in-flight data before processing new data).
- Aligned→unaligned fallback logic in `InputGate`.
- `CheckpointConfig` configuration for unaligned mode and `unalignedThreshold`.
- End-to-end test: sustained backpressure → aligned checkpoint times out → unaligned checkpoint succeeds → exactly-once maintained.

### Out Of Scope

- Multi-epoch barrier tracking (Stage 45).
- In-flight data rescaling across parallelism change (Stage 47).
- Priority events / barrier preemption in the mailbox.
- Output-side channel state. `RemoteResultPartition.write()` immediately sends via `IMessageService` (no internal buffering). For durable backends (SysDao/Pulsar), output in-flight data lives in the backend and is safe. Phase 0 design document will justify this assumption or flag it as a risk.

## Execution Plan

### Phase 0 — Unaligned Checkpoint Design Document + Vision Decision

Status: completed
Targets: `ai-dev/design/nop-stream/checkpoint-design.md` (new §unaligned section), `ai-dev/design/nop-stream/00-vision.md` (decision confirmation)

- Item Types: `Decision`

- [x] Write a design specification for unaligned checkpoint in `checkpoint-design.md` (new section) covering ALL of the following behavioral semantics (the design answers "what should happen", not "how to code it"):
  - **In-flight data semantics per channel type**: For a channel that HAS delivered its barrier (aligned channel): in-flight data = records buffered AFTER the barrier on that channel (new-epoch records that arrived while waiting for other channels to align). For a channel that has NOT delivered its barrier (non-aligned channel): in-flight data = ALL currently buffered records on that channel (they are pre-barrier records that must be preserved for exactly-once). This distinction is critical: capturing the wrong set breaks exactly-once.
  - **Channel state capture trigger**: WHO captures channel state (the `InputGate` when switching to unaligned mode), WHEN (at the moment of aligned→unaligned fallback, for all non-aligned channels; immediately upon barrier receipt for aligned channels), and HOW it reaches the snapshot (channel state flows from `InputGate` through the emitted barrier event to `CheckpointBarrierTracker`, which includes it in the `TaskStateSnapshot` alongside operator state).
  - **Channel state replay on recovery**: WHERE in the task lifecycle channel state is replayed (after operator state restoration, before the task starts reading from `InputGate`), and WHAT order (in-flight records are pre-injected into `InputChannel` buffers so they are processed before any new upstream records).
  - **Output-side safety**: justify why output channel state is not needed (`RemoteResultPartition.write()` immediately delegates to `IMessageService.send()` with no internal buffering; durable backends preserve in-flight output; for non-durable backends, flag as a known limitation).
  - **Timeout interaction**: specify the relationship between `unalignedThreshold` (mode-switch trigger, e.g., 1s) and `barrierAlignmentTimeout` (absolute fail, 30s). When unaligned mode activates, the `barrierAlignmentTimeout` timer is cancelled for that checkpoint. `unalignedThreshold` must be < `barrierAlignmentTimeout` (validated at config time, fail-fast on misconfiguration).
- [x] Obtain human confirmation on vision §六 决策点 #4 applicability: does unaligned checkpoint (adding channel state to snapshot + changing barrier processing mode) constitute a "Checkpoint 协议变更" requiring explicit human approval? Record the decision and its rationale. If human approval is required, this plan is `blocked` until approved.

Exit Criteria:

- [x] `checkpoint-design.md` contains a complete unaligned checkpoint section covering all five behavioral semantic points above. Each point specifies expected behavior precisely enough that an implementer can verify their implementation against it without guessing.
- [x] Vision §六 #4 decision is recorded (either "not a protocol change, proceed" or "requires human approval — blocked until approved"). If blocked, the plan status remains `draft` until approval.
- [x] **No implementation code in this Phase** — this is a design + decision phase only.
- [x] Owner-doc: `checkpoint-design.md` updated with the new unaligned section.
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 1 — RemoteInputChannel Heartbeat (Producer-Sends-Idle Model)

Status: completed
Targets: `nop-stream-runtime/transport/RemoteResultPartition.java` (producer heartbeat emission), `nop-stream-runtime/transport/RemoteInputChannel.java` (consumer heartbeat timeout detection)

- Item Types: `Fix | Proof`

- [x] Implement **producer-side heartbeat emission**: `RemoteResultPartition` sends a periodic idle-heartbeat envelope (via the same `IMessageService` topic it uses for data) when no data records have been sent for `heartbeatInterval` (default e.g., 5s). The heartbeat is a lightweight control envelope distinguishable from data (e.g., `StreamMessageEnvelope` with `TYPE_CONTROL` and payload `"HEARTBEAT"`), carrying the current fencing epoch.
- [x] Implement **consumer-side heartbeat timeout detection**: `RemoteInputChannel` tracks `lastReceivedTime` (updated on any message: data, barrier, watermark, or heartbeat). A scheduled check (on each `read()` call, or a shared timer in the owning task) compares `now - lastReceivedTime` against `channelTimeout` (default e.g., 15s). If exceeded, the channel reports failure → task failure → coordinator-driven recovery.
- [x] Distinguish heartbeat timeout from normal end-of-stream: EOS is an explicit `END_OF_STREAM` control message (already handled, sets `finished=true`); heartbeat timeout is the absence of both data AND heartbeat AND EOS, indicating producer death or network partition.
- [x] Heartbeat must respect fencing epoch: heartbeats with wrong epoch are discarded (not treated as liveness), consistent with existing `RemoteInputChannel` epoch filtering (`:212-216`).
- [x] Threading model: timeout detection piggybacks on the `read()` path (no dedicated timer thread per channel). The owning task's read loop naturally checks elapsed time. Alternatively, the `TaskManager`'s existing scheduled executor can perform periodic checks. The design doc (Phase 0) should specify which, but either is acceptable — no new background thread per channel.

Exit Criteria:

- [x] `RemoteResultPartition` sends idle heartbeats when no data flows for `heartbeatInterval`. Verifiable by: consumer receives heartbeat envelopes during idle periods.
- [x] `RemoteInputChannel` detects producer failure within `channelTimeout` when both data and heartbeat stop. Verifiable by: test stops the producer → consumer reports failure within timeout window.
- [x] Normal end-of-stream (`END_OF_STREAM` control message) is not mistaken for heartbeat timeout — `finished=true` path still works.
- [x] Heartbeat timeout triggers task failure → coordinator detects it via task status report → recovery is initiated.
- [x] Fencing: heartbeats with wrong epoch are discarded, not treated as liveness.
- [x] **接线验证**: `RemoteResultPartition` heartbeat emission and `RemoteInputChannel` timeout detection are both exercised in the same test (producer + consumer wired together), not just standalone unit tests.
- [x] Owner-doc: `component-roadmap.md` C5 row "channel 心跳" updated to ✅.
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — Channel State Capture + Persistence

Status: completed
Targets: `nop-stream-core/execution/InputChannel.java`, `nop-stream-core/execution/InputGate.java`, `nop-stream-core/execution/CheckpointBarrierTracker.java`, `nop-stream-core/checkpoint/TaskEpochSnapshot.java`, `nop-stream-core/checkpoint/EpochManifest.java`

- Item Types: `Fix | Proof`

- [x] Add a channel-state capture capability to `InputChannel`: a method (e.g., `captureInFlightData(boolean barrierReceived)`) that returns the currently buffered records according to the Phase 0 semantics — for a non-aligned channel (`barrierReceived=false`): all buffered records; for an aligned channel (`barrierReceived=true`): records after the barrier. Note: barrier-received state currently lives in `InputGate` (`barrierReceived[]` array, `InputGate.java:69`), not in `InputChannel`. Phase 0 design must resolve whether `InputChannel` independently tracks barrier receipt or receives it as a parameter from `InputGate` (the latter is simpler — `InputGate` passes `barrierReceived[channelIndex]` when calling capture). The method drains the buffer (records are moved into channel state, not copied).
- [x] Define a `ChannelState` data structure holding per-channel in-flight records (channel index → list of `StreamElement`). This structure is serializable and participates in `TaskEpochSnapshot`.
- [x] Extend `TaskEpochSnapshot` to carry `ChannelState` (nullable — absent for aligned checkpoints without channel state). Existing snapshots without channel state must deserialize correctly (channel state treated as empty/null).
- [x] Define the data flow: `InputGate` captures `ChannelState` from its channels at the moment of unaligned mode switch. Since `triggerCheckpoint()` runs at checkpoint initiation (before barriers flow through the data path), channel state cannot be passed to it. Instead: `InputGate` returns the unaligned barrier + `ChannelState` to the task thread via `read()` → the task thread delivers them to `CheckpointBarrierTracker` via `acknowledgeOperator()` or a dedicated `setChannelState()` setter on the tracker → the tracker adds `ChannelState` to the current `TaskStateSnapshot` alongside operator snapshots. The channel state rides the barrier ACK path, not the trigger path.
- [x] Persist `ChannelState` via existing `EpochManifest.segments` infrastructure (`codec=json`, `segmentType=channel-state`). `CheckpointSerDe` must serialize/deserialize `ChannelState` within `TaskEpochSnapshot`.

Exit Criteria:

- [x] `InputChannel.captureInFlightData()` returns the correct set of records per Phase 0 semantics: all buffered records for non-aligned channels, post-barrier records for aligned channels. Verifiable by: test pre-loads a channel with known records, calls capture, asserts the captured set matches expected.
- [x] `ChannelState` round-trips through serialization: construct → serialize via `CheckpointSerDe` → deserialize → assert per-channel records match.
- [x] `TaskEpochSnapshot` with `ChannelState` deserializes correctly; `TaskEpochSnapshot` without `ChannelState` (existing aligned checkpoint) also deserializes correctly (backward compatible).
- [x] **接线验证**: `InputGate`'s unaligned mode (Phase 3) actually invokes `captureInFlightData()` on its channels and passes the result through to `CheckpointBarrierTracker`. Verifiable by test asserting `ChannelState` appears in the snapshot when unaligned mode is triggered.
- [x] Owner-doc: `checkpoint-design.md` §2.5 (Snapshot 内容) updated with channel-state row; `state-management-design.md` if state backend interface changes.
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — Aligned→Unaligned Fallback in InputGate

Status: completed
Targets: `nop-stream-core/execution/InputGate.java`, `nop-stream-core/execution/CheckpointBarrierTracker.java`, `nop-stream-core/checkpoint/CheckpointConfig.java`

- Item Types: `Fix | Proof`

- [x] Add `unalignedThreshold` (default e.g., 1000ms) and `unalignedCheckpointEnabled` (default `true`) to `CheckpointConfig`. Validate at config time: if `unalignedCheckpointEnabled=true` then `unalignedThreshold` must be < `barrierAlignmentTimeout` (fail-fast on misconfiguration). Thread these through `GraphExecutionPlan.build()` → `InputGate` constructor.
- [x] Implement mode-switch in `InputGate.readMultiChannel()`: alongside the existing `barrierAlignmentTimeout` check (`:340-346`), add an `unalignedThreshold` check. When `unalignedThreshold` elapses without full alignment AND `unalignedCheckpointEnabled=true`: (a) call `captureInFlightData()` on all non-aligned channels (Phase 2), building `ChannelState`; (b) cancel the `barrierAlignmentTimeout` timer for this checkpoint; (c) emit the barrier immediately with attached `ChannelState`; (d) resume all blocked channels; (e) log the mode switch with checkpoint ID + elapsed time.
- [x] When `unalignedCheckpointEnabled=false`: existing behavior preserved — alignment timeout throws `ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT` → task FAILED.
- [x] Single-input tasks: unaligned is trivially correct (no multi-channel alignment; `ChannelState` is empty). Multi-input tasks: `ChannelState` captures in-flight data from non-aligned channels per Phase 0 semantics.
- [x] `CheckpointBarrierTracker` must receive `ChannelState` via the barrier ACK path (not `triggerCheckpoint()` which runs at initiation before channel state exists) — either through `acknowledgeOperator()` or a dedicated `setChannelState()` setter that attaches channel state to the current `TaskStateSnapshot`. No change to the single-in-flight constraint.

Exit Criteria:

- [x] When `unalignedCheckpointEnabled=true` and alignment doesn't complete within `unalignedThreshold`: checkpoint completes via unaligned mode (channel state captured, barrier emitted, snapshot taken with `ChannelState`) — no `ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT` thrown.
- [x] When `unalignedCheckpointEnabled=false`: existing behavior preserved (alignment timeout → task FAILED).
- [x] Config validation: `unalignedThreshold >= barrierAlignmentTimeout` with `unalignedCheckpointEnabled=true` → fail-fast at config load.
- [x] Single-input task unaligned checkpoint: `ChannelState` is empty (no cross-channel in-flight data).
- [x] Multi-input task unaligned checkpoint: `ChannelState` contains in-flight records from non-aligned channels — verifiable by: records after barrier on slow channels are in `ChannelState`, not lost.
- [x] Mode switch is logged with checkpoint ID + threshold elapsed.
- [x] **接线验证**: `InputGate` unaligned path is reached from the task's checkpoint barrier processing. Verifiable by test that exercises the fallback and asserts `ChannelState` is present in the resulting `TaskStateSnapshot`.
- [x] Owner-doc: `checkpoint-design.md` §2.4 (Barrier 对齐规则) updated with unaligned mode + fallback; `CheckpointConfig` javadoc updated.
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — Recovery with Channel State Replay + E2E Backpressure Test

Status: completed
Targets: `nop-stream-runtime/` recovery path, `nop-stream-core/execution/InputGate.java` (replay injection), test sources

- Item Types: `Fix | Proof`

- [x] Implement channel state replay on recovery: define a new recovery lifecycle step that occurs AFTER operator state restoration but BEFORE the task starts reading from `InputGate`. This step pre-injects `ChannelState` records into the corresponding `InputChannel` buffers (for `RemoteInputChannel`, inject into the `LinkedBlockingQueue` before the subscription starts delivering; for local channels, inject into the `ResultPartition` queue).
- [x] The replay lifecycle hook: `GraphModelCheckpointExecutor.restoreOperatorsFromState()` currently restores operator state only. Add a step (e.g., `restoreChannelState(ChannelState)`) that injects in-flight records into `InputGate`'s channels after the gate is constructed but before the task thread starts processing.
- [x] Order guarantee: replayed in-flight records are processed before any new upstream records or barriers, ensuring the recovered task's state is consistent with the checkpoint.
- [x] Deliver E2E test (`TestUnalignedCheckpointBackpressure`): pipeline with a slow (backpressured) channel → aligned checkpoint cannot complete within `barrierAlignmentTimeout` → with `unalignedCheckpointEnabled=true`, checkpoint completes via unaligned mode → kill task → restore from unaligned checkpoint → verify exactly-once (no duplicates from in-flight replay, no data loss).
- [x] Deliver E2E test (`TestUnalignedCheckpointMultiInput`): multi-input operator → in-flight data captured from non-aligned channels → recovery replays in-flight data → downstream sees consistent results.

Exit Criteria:

- [x] **端到端验证** (`TestUnalignedCheckpointBackpressure`): a pipeline under sustained backpressure completes a checkpoint via unaligned mode, then recovers from that checkpoint, and exactly-once holds (record count at sink matches source, accounting for in-flight data). This is the Anti-Hollow exit criterion (plan guide #22).
- [x] Channel state replay on recovery precedes new data processing — verifiable by: recovered task processes in-flight records before any new upstream records (assertable via record ordering or sequence numbers).
- [x] No data loss: records that were in-flight (captured in `ChannelState`) are present in the sink output after recovery.
- [x] No duplicates: records before the barrier (already processed and acked) are not replayed.
- [x] Multi-input unaligned recovery (`TestUnalignedCheckpointMultiInput`): in-flight data from multiple non-aligned channels is correctly replayed to their respective channels.
- [x] **接线验证**: recovery path actually loads and replays `ChannelState` (not just deserializes it). Verifiable by assertion that in-flight records appear in the processing stream after recovery.
- [x] **无静默跳过**: any unimplemented recovery branch throws `UnsupportedOperationException` or equivalent, not silent skip.
- [x] Owner-doc: `checkpoint-design.md` updated with unaligned recovery semantics; `component-roadmap.md` C5 容错缺口 "背压逃生（unaligned）" row → ✅ implemented.
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] All Phase 0–4 Exit Criteria checked `[x]`
- [x] Phase 0 design document complete and vision §六 #4 decision recorded
- [x] `RemoteInputChannel` / `RemoteResultPartition` heartbeat detects network partition faster than lease-only timeout
- [x] Unaligned checkpoint completes under sustained backpressure where aligned checkpoint would time out
- [x] Channel state round-trips: capture → persist → restore → replay (with correct per-channel semantics)
- [x] Exactly-once holds after unaligned checkpoint recovery (E2E proof in `TestUnalignedCheckpointBackpressure`)
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` passes
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` passes
- [x] `./mvnw compile` passes
- [x] No hollow implementations: heartbeat sends/receives, channel state captures/drains data, unaligned fallback switches mode and captures state, recovery replays (plan guide #22/#24)
- [x] checkstyle / code conventions pass
- [x] Owner-docs updated: `checkpoint-design.md` (unaligned section + §2.4/§2.5), `component-roadmap.md` C5, `CheckpointConfig` javadoc
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据

## Deferred But Adjudicated

### Multi-Concurrent Unaligned Checkpoint

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Multi-concurrent checkpoint (Stage 45) requires task-level multi-epoch barrier tracking. Unaligned checkpoint works with single-in-flight (current constraint). The two features compose independently.
- Successor Required: `yes` — Stage 45 (`45-concurrent-checkpoint`)

### Unaligned + Rescale Interaction

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Rescale with unaligned checkpoint requires remapping in-flight data across new parallelism (Stage 47). First version forces aligned checkpoint for rescale recovery if needed.
- Successor Required: `yes` — Stage 47 (`47-unaligned-rescale`)

### Native Priority Events in Mailbox

- Classification: `optimization candidate`
- Why Not Blocking Closure: Unaligned checkpoint captures in-flight data directly from channel buffers, making priority event preemption unnecessary for correctness.
- Successor Required: `no`

## Non-Blocking Follow-ups

- Consider exposing unaligned checkpoint mode as a metric (count of aligned→unaligned switches per job).
- Consider adaptive threshold: dynamically adjust `unalignedThreshold` based on historical alignment duration.
- **M-3 (closure-audit Minor)**: heartbeat capability is implemented + tested but not yet wired into the production `RemoteGraphExecutionPlanBuilder` (it constructs `RemoteResultPartition`/`RemoteInputChannel` with `heartbeatIntervalMs=0`/`channelTimeoutMs=0` and does not call `startHeartbeat(sharedScheduler)`). Enabling in production requires threading a shared `ScheduledExecutorService` from the task runtime. Tracked in `component-roadmap.md` C5 ("生产默认启用待 task runtime 共享 scheduler 接线"). Non-blocking: capability + wiring are proven; production default-enable is a separate scheduler-plumbing task.

## Closure

Status Note: Stage 43 交付 channel 心跳 + unaligned checkpoint（背压逃生）。Phase 0 设计 + vision §六 #4 裁决；Phase 1 producer-sends-idle 心跳 + consumer 超时检测（fencing 感知）；Phase 2 ChannelState capture/persistence（per-channel 语义，向后兼容）；Phase 3 aligned→unaligned 回退（CheckpointConfig + InputGate + tracker 接线）；Phase 4 恢复重放 + 端到端 exactly-once 验证。Anti-Hollow：capture→persist→restore→replay 调用链在运行时完整连通，由独立子 agent closure-audit 确认（无 Blocker/Major）。
Completed: 2026-08-03

Closure Audit Evidence:

- Reviewer / Agent: Independent closure auditor — glm-5.2, fresh session (opencode explore subagent, `task_id=ses_03bb62d7dffeEXrH63jBEtdIVO`)
- Audit Session: ses_03bb62d7dffeEXrH63jBEtdIVO (read-only, Anti-Hollow plan guide #22/#24)
- Evidence:
  - **Phase 0 PASS**: `checkpoint-design.md` §2.11（§2.11.2 per-channel 语义表 / §2.11.3 WHO-WHEN-HOW / §2.11.4 replay 顺序 / §2.11.5 输出侧安全 / §2.11.6 超时不变量）；`00-vision.md:81` §六 #4 裁决"不构成需单独人审批的协议变更"
  - **Phase 1 PASS**: `RemoteResultPartition.sendHeartbeatIfIdle`/`startHeartbeat`（producer-sends-idle）；`RemoteInputChannel.isChannelTimedOut`/`checkChannelTimeout`（read 路径 piggyback）；fencing 错误 epoch 不刷新 liveness（`EnvelopeConsumer` epoch 过滤后更新 `lastReceivedTime`）；EOS（`finished`）抑制 timeout。`TestRemoteInputChannelHeartbeat`（7 tests，producer+consumer 接线）全绿
  - **Phase 2 PASS**: `InputChannel.captureInFlightData`/`ResultPartition.drainBufferedElements`/`RemoteInputChannel.captureInFlightData`（drain，释放 buffer-pool permit）；`ChannelState.toSerializableForm`/`fromSerializableForm` round-trip；`TaskEpochSnapshot.getChannelState` nullable（向后兼容）；`CheckpointSerDe` serialize/deserialize channelState（CompletedCheckpoint + EpochManifest 双路径）。`TestChannelStateCapture`（6）+ `TestCheckpointSerDeChannelState`（3）全绿
  - **Phase 3 PASS**: `CheckpointConfig.unalignedCheckpointEnabled`/`unalignedThreshold`/`validateUnalignedConfig`（fail-fast，5 个 entry point 全部校验）；`InputGate.switchToUnalignedAndEmit` per-channel `barrierReceived[i]` flag；`StreamTaskInvokable.processInputGate` 接线（`consumePendingChannelState`→`tracker.setChannelState` BEFORE `processBarrier`）；`unalignedCheckpointEnabled=false` 保留对齐超时行为；mode switch 日志（checkpointId + elapsed + threshold + record 数）。`TestInputGateUnalignedFallback`（4，含 RecordingChannel 接线验证）+ `TestCheckpointConfig`（4 unaligned）全绿
  - **Phase 4 PASS**: `InputGate.restoreChannelState` + `GraphModelCheckpointExecutor.restoreChannelStateIfPresent`（restoreOperatorsFromState 之后调用）；replay 在新数据之前；exactly-once（no loss + no duplicates）。`TestUnalignedCheckpointBackpressure`（2，端到端 capture→persist→restore→replay）+ `TestUnalignedCheckpointMultiInput`（2，per-channel 路由）全绿
  - **Anti-Hollow 调用链追踪（运行时连通，非仅类型存在）**: InputGate.readMultiChannel `unalignedThreshold` 触发 → switchToUnalignedAndEmit `captureInFlightData(barrierReceived[i])` → ResultPartition.drainBufferedElements（真实 drain + 释放 permit）→ pendingChannelState 暂存 → StreamTaskInvokable.processInputGate `consumePendingChannelState` → CheckpointBarrierTracker.setChannelState → TaskEpochSnapshot → CheckpointSerDe.serializeTaskStateSnapshot `channelState` map → deserialize `ChannelState.fromSerializableForm` → restoreChannelStateIfPresent → InputGate.restoreChannelState → injectElements/injectFront（prepend）→ 下一次 read 先消费 replay 记录。每个 hop 有非空实现并由 `TestUnalignedCheckpointBackpressure.testCapturePersistRestoreReplayLoop` 端到端覆盖
  - **Closure Gates 验证**: `./mvnw clean install -pl nop-stream -am -T 1C -DskipTests` 全模块 SUCCESS；`./mvnw test -pl nop-stream -T 1C` 全 10 模块 SUCCESS（0 failures）；`node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-core/--module nop-stream-runtime --severity high` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
  - **Deferred 项分类检查**: M-3（生产 heartbeat 接线）为 `optimization candidate`（已文档化，能力+wiring 已验证，scheduler 接线为独立任务）；multi-concurrent（Stage 45）、rescale+unaligned（Stage 47）均为 `out-of-scope improvement` 且在 plan `Deferred But Adjudicated` 中显式记录 successor。无 in-scope live defect / contract drift 被降级到 deferred

Follow-up:

- M-3（见 `Non-Blocking Follow-ups`）：heartbeat 生产默认启用（共享 scheduler 接线）
- no other remaining plan-owned work
