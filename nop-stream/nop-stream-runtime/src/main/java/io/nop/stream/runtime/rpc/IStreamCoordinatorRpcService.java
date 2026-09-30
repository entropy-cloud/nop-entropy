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

/**
 * Coordinator-side control-plane RPC surface: the calls TaskManagers issue back
 * to the JobCoordinator (checkpoint ACKs, terminal reports, liveness) plus the
 * job-control entries (terminate / abort / status).
 *
 * <p><strong>Timeout / retry contract</strong> (plan 369 Phase 4; the doc-first
 * half of R5-CC-17, per {@code ai-dev/analysis/nop-stream/09c-flink2.3-compare-network-execution-orchestration.md}
 * §5.3-2): all entries are <em>one-way</em> control messages carried by
 * {@link io.nop.api.core.message.IMessageService} — no application-level
 * response, no built-in RPC timeout, no automatic retry at this interface.
 * Boundedness of any individual send is the transport backend's responsibility
 * (an unboundedly blocking send violates this contract). Loss tolerance per
 * entry:
 * <ul>
 *   <li>{@link #receiveCheckpointAck}: the sender retries transient failures
 *       inline with a bounded budget ({@code TaskManager.sendCheckpointAck});
 *       exhaustion is counted and the coordinator-side checkpoint timeout abort
 *       remains the subsuming safety net.</li>
 *   <li>{@link #reportTaskStatus}: loss means the coordinator learns of the
 *       failure later, via liveness aging ({@code taskTimeout}) or node-lease
 *       expiry — both re-trigger the same recovery.</li>
 *   <li>{@link #reportNodeTaskLiveness}: loss merely ages the affected
 *       timestamps; the next heartbeat beat (5s cadence) re-reports.</li>
 *   <li>{@link #terminate} / {@link #abortCheckpoint} / {@link #getJobStatus}:
 *       job-control entries; {@code getJobStatus} is the only read-style call
 *       and returns a value in-band — callers reaching it over RPC own any
 *       retry they need.</li>
 * </ul>
 *
 * <p>Implementations must surface failures observably (log or metric), never
 * silently swallow them. The mirror task-side surface is
 * {@link IStreamTaskRpcService}.
 */
@Internal
public interface IStreamCoordinatorRpcService {

    /**
     * One-way checkpoint ACK delivery (see the bounded sender-side retry on
     * {@code TaskManager.sendCheckpointAck}; checkpoint timeout = safety net).
     */
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
     * Each entry carries one task's aliveness timestamp (G52 / AR-01: task
     * thread loop activity for MIDDLE/SINK, TaskManager wall clock for
     * SOURCE/SELF_CONTAINED — decoupled from data progress); the coordinator
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
