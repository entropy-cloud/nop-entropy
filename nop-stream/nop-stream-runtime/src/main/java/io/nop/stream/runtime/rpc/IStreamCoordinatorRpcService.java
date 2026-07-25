/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.rpc;

import java.util.List;

import io.nop.api.core.annotations.core.Internal;

import io.nop.stream.core.checkpoint.JobTerminationMode;
import io.nop.stream.runtime.coordinator.JobStatusResponse;
import io.nop.stream.runtime.coordinator.TaskProgress;
import io.nop.stream.runtime.coordinator.TaskStatusReport;
import io.nop.stream.runtime.taskmanager.CheckpointAckMessage;

@Internal
public interface IStreamCoordinatorRpcService {

    void receiveCheckpointAck(CheckpointAckMessage ack);

    /**
     * G52: per-task terminal-state report from a RunningTask. The coordinator
     * uses this to detect a task FAILURE even when its host node is still alive
     * (the gap that node-level lease detection cannot close). Also serves as a
     * strong terminal signal for COMPLETED tasks.
     *
     * <p>Implementations must surface failures (not silently swallow). At
     * minimum, log the report and update per-subtask liveness; the
     * {@code JobCoordinator} additionally triggers recovery on FAILED reports.
     */
    void reportTaskStatus(TaskStatusReport report);

    /**
     * G52: per-node batched liveness piggybacked on {@code TaskManager.heartbeat()}.
     * Each entry carries one task's {@code lastProgressTime}; the coordinator
     * detects stalls by comparing against {@code taskTimeout}.
     *
     * @param nodeId the reporting node
     * @param progress per-task liveness for tasks currently running on {@code nodeId}
     */
    void reportNodeTaskLiveness(String nodeId, List<TaskProgress> progress);

    /**
     * G23: terminates the job according to the specified
     * {@link JobTerminationMode}. Delegates to the coordinator's existing
     * four-mode termination implementation (CANCEL / DRAIN / SUSPEND /
     * EXPORT_SAVEPOINT). Local callers invoke this directly; cross-JVM callers
     * (Stage 39) will reach the same implementation via a generated RPC proxy,
     * so no new semantics are required when the transport layer is added.
     *
     * @param mode the termination mode (never null)
     */
    void terminate(JobTerminationMode mode);

    /**
     * G23: aborts the pending checkpoint identified by {@code epochId}. Triggers
     * the existing abort path inside {@code CheckpointCoordinator} which, in
     * turn, fires the LOCAL abort handler registered by
     * {@code GraphModelCheckpointExecutor.registerLocalAbortHandler} to cancel
     * the coordinator-JVM tasks. Recovery strategy is unchanged by abort.
     *
     * <p>If {@code epochId} does not match any currently-pending checkpoint, the
     * implementation logs a warning and returns (no silent swallow — the
     * unmatched case is explicitly observable).
     *
     * @param epochId the checkpoint id to abort
     */
    void abortCheckpoint(long epochId);

    /**
     * G23: returns a serializable snapshot of the current job status, including
     * the captured failure cause when the job has reached
     * {@link io.nop.stream.runtime.coordinator.JobStatus#FAILED}. Enables a
     * remote caller (Stage 39) to query job health without a second round-trip.
     *
     * @return a {@link JobStatusResponse} carrying the current status (never null)
     */
    JobStatusResponse getJobStatus();
}
