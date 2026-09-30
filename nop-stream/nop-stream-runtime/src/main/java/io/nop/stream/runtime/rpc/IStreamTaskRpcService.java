/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.rpc;

import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_OPERATION;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_UNSUPPORTED;
import io.nop.api.core.annotations.core.Internal;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.runtime.cluster.TaskAssignment;

/**
 * Task-side control-plane RPC surface: the calls a JobCoordinator issues to a
 * TaskManager (assignment/deploy, checkpoint trigger, cancel, fencing-token
 * push, 2PC commit notification).
 *
 * <p><strong>Timeout / retry contract</strong> (plan 369 Phase 4; the doc-first
 * half of R5-CC-17, per {@code ai-dev/analysis/nop-stream/09c-flink2.3-compare-network-execution-orchestration.md}
 * §5.3-2): every method here is a <em>one-way, fire-and-forget</em> control
 * message carried by {@link io.nop.api.core.message.IMessageService}. There is
 * NO application-level response, NO built-in RPC timeout and NO automatic
 * retry at this interface — boundedness of the underlying send is the
 * transport backend's responsibility (a backend whose send can block
 * indefinitely violates this contract). Loss or silent drop of any single
 * message must be tolerable; the safety net for each call is:
 * <ul>
 *   <li>{@link #receiveAssignment} / {@link #deployTask}: rejection or loss is
 *       recovered by the coordinator's deployment grace period (R5-CC-07,
 *       {@code JobCoordinator.detectFailures}) re-triggering global recovery;
 *       TM-side rejections additionally report a FAILED {@code TaskStatusReport}
 *       because the one-way RPC cannot carry the exception back.</li>
 *   <li>{@link #triggerCheckpoint}: loss is healed by the checkpoint timeout —
 *       the epoch is aborted (pending checkpoint abort) and the next periodic
 *       trigger proceeds.</li>
 *   <li>{@link #cancelTask}: loss is healed by fencing-epoch rotation — the
 *       next recovery/leadership switch invalidates the missed generation.</li>
 *   <li>{@link #updateFencingToken}: loss is healed by the next epoch push;
 *       the stale node's control calls are rejected on fencing mismatch until
 *       then.</li>
 *   <li>{@link #notifyCheckpointComplete}: loss is healed by the NEXT epoch's
 *       subsuming commit ({@code finishCommit(M)} commits every
 *       {@code eid <= M}) and by the sink restore path's re-commit of
 *       durable-but-uncommitted transactions.</li>
 * </ul>
 *
 * <p>Implementations must not block unboundedly on the calling thread and must
 * surface failures observably (log or metric), never silently swallow them.
 * The mirror coordinator-side surface is {@link IStreamCoordinatorRpcService}.
 */
@Internal
public interface IStreamTaskRpcService {

    /**
     * One-way assignment delivery (in-process mode). Loss is healed by the
     * deployment grace period (R5-CC-07) re-triggering recovery; a rejecting
     * TaskManager reports a FAILED {@code TaskStatusReport} so the rejection is
     * observable coordinator-side (the RPC itself cannot return it).
     */
    void receiveAssignment(TaskAssignment assignment);

    /**
     * One-way checkpoint barrier injection. Loss is healed by the checkpoint
     * timeout aborting the epoch; the next periodic trigger proceeds on the
     * stable task set.
     *
     * @param fencingEpoch monotonic fencing epoch (Stage 39: long, replaces composite String)
     */
    void triggerCheckpoint(CheckpointBarrier barrier, long fencingEpoch);

    /**
     * Cancels the task slot identified by jobId/vertexId/subtaskIndex. The
     * cancel is a control-plane mutation and is fenced like every other
     * mutating entry: a stale coordinator (old leader / old recovery
     * generation) must not be able to cancel an active generation's task.
     *
     * <p>One-way; loss is healed by fencing-epoch rotation at the next
     * recovery/leadership switch.
     *
     * @param fencingEpoch the monotonic fencing epoch of the coordinator issuing the cancel;
     *                     a mismatch against the TaskManager's active epoch is rejected
     *                     fail-fast with {@code ERR_STREAM_FENCING_TOKEN_MISMATCH}
     */
    void cancelTask(String jobId, String vertexId, int subtaskIndex, long fencingEpoch);

    /**
     * Stage 39: pushes the rotated monotonic fencing epoch to the task side.
     * One-way; a lost push leaves the target on its stale token until the next
     * rotation (its control calls are fencing-rejected in the meantime).
     *
     * @param fencingEpoch the new monotonic fencing epoch
     */
    void updateFencingToken(long fencingEpoch);

    /**
     * Stage 42 Phase 0: deploys task logic to this TaskManager as a serializable
     * {@link TaskDeploymentDescriptor}. The TaskManager reconstructs its own
     * {@link io.nop.stream.core.execution.task.StreamTaskInvokable} locally from the
     * descriptor's {@link io.nop.stream.core.jobgraph.JobGraph} + edge config,
     * installs it, and starts running the task.
     *
     * <p>This is the cross-JVM replacement for the in-process direct-Java
     * {@code TaskManager.installInvokable(StreamTaskInvokable)} call. In
     * remote-deploy mode, {@link io.nop.stream.runtime.coordinator.JobCoordinator#assignTasks()}
     * calls this instead of {@link #receiveAssignment} + a separate direct
     * install. The descriptor is self-contained (carries
     * {@link io.nop.stream.runtime.cluster.TaskAssignment} metadata), so
     * {@code receiveAssignment} is NOT called separately in remote-deploy mode.
     *
     * <p><strong>Default implementation</strong> throws
     * {@link UnsupportedOperationException} so that the ~12 in-process test
     * doubles of this interface compile unchanged (they never receive
     * {@code deployTask} calls in the in-process / legacy path). The real
     * implementation lives in {@link io.nop.stream.runtime.taskmanager.TaskManager#deployTask}.
     *
     * <p><strong>No silent skip</strong> applies to the IMPLEMENTATION (see
     * {@link io.nop.stream.runtime.taskmanager.TaskManager#deployTask}): the RPC
     * is one-way, so a rejection thrown here never reaches the coordinator —
     * the implementation must report a FAILED {@code TaskStatusReport} instead.
     * A transport-LOST deploy (this call never arrives at all) has no reporter;
     * it is healed by the coordinator's deployment grace period (R5-CC-07)
     * re-triggering recovery.
     *
     * @param descriptor   the serializable deployment descriptor
     * @param fencingEpoch the monotonic fencing epoch the deployment is valid under
     */
    default void deployTask(TaskDeploymentDescriptor descriptor, long fencingEpoch) {
        throw new StreamException(ERR_STREAM_UNSUPPORTED).param(ARG_OPERATION, "deployTask is not supported by this IStreamTaskRpcService implementation. "
                        + "Only TaskManager in remote-deploy mode handles deployTask; "
                        + "in-process test doubles inherit the default UnsupportedOperationException.");
    }

    /**
     * Item 14 (composite-scenario distributed): notifies this TaskManager that
     * checkpoint {@code checkpointId} became durable on the coordinator, so the
     * locally-running 2PC sink participants can commit (their
     * {@code CheckpointParticipant.finishCommit(checkpointId, true)}).
     *
     * <p>Sent by the coordinator's distributed commit forwarder (registered via
     * {@code JobCoordinator.registerDistributedCommitForwarder}) on every
     * completed checkpoint. The fencing epoch must match the TaskManager's
     * current epoch — a stale-epoch notification is rejected (fail-fast, same
     * contract as {@code triggerCheckpoint}).
     *
     * <p><strong>Default implementation</strong> throws
     * {@link UnsupportedOperationException} (mirroring {@link #deployTask}) so
     * in-process test doubles compile unchanged. The real implementation lives
     * in {@link io.nop.stream.runtime.taskmanager.TaskManager#notifyCheckpointComplete}.
     *
     * @param checkpointId the durable checkpoint id whose sink transactions may commit
     * @param fencingEpoch the monotonic fencing epoch of the coordinator issuing the commit
     */
    default void notifyCheckpointComplete(long checkpointId, long fencingEpoch) {
        throw new StreamException(ERR_STREAM_UNSUPPORTED).param(ARG_OPERATION, "notifyCheckpointComplete is not supported by this IStreamTaskRpcService implementation. "
                        + "Only TaskManager in remote-deploy mode handles checkpoint-completion "
                        + "notifications; in-process test doubles inherit the default "
                        + "UnsupportedOperationException.");
    }
}
