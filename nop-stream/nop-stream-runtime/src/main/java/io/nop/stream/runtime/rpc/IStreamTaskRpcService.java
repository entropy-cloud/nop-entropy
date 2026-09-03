/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.rpc;

import io.nop.api.core.annotations.core.Internal;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.runtime.cluster.TaskAssignment;

@Internal
public interface IStreamTaskRpcService {

    void receiveAssignment(TaskAssignment assignment);

    /**
     * @param fencingEpoch monotonic fencing epoch (Stage 39: long, replaces composite String)
     */
    void triggerCheckpoint(CheckpointBarrier barrier, long fencingEpoch);

    void cancelTask(String jobId, String vertexId, int subtaskIndex);

    /**
     * Stage 39: pushes the rotated monotonic fencing epoch to the task side.
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
     * @param descriptor   the serializable deployment descriptor
     * @param fencingEpoch the monotonic fencing epoch the deployment is valid under
     */
    default void deployTask(TaskDeploymentDescriptor descriptor, long fencingEpoch) {
        throw new UnsupportedOperationException(
                "deployTask is not supported by this IStreamTaskRpcService implementation. "
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
        throw new UnsupportedOperationException(
                "notifyCheckpointComplete is not supported by this IStreamTaskRpcService implementation. "
                        + "Only TaskManager in remote-deploy mode handles checkpoint-completion "
                        + "notifications; in-process test doubles inherit the default "
                        + "UnsupportedOperationException.");
    }
}
