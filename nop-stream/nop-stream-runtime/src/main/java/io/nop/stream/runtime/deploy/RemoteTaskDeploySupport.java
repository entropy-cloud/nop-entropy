/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.deploy;

import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.checkpoint.CheckpointPlan;
import io.nop.stream.core.checkpoint.OperatorStateMapping;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.execution.CheckpointBarrierTracker;
import io.nop.stream.core.execution.CheckpointFailureListener;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.model.StreamModelFingerprint;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.runtime.checkpoint.CheckpointPlanBuilder;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;
import io.nop.stream.runtime.rpc.TaskDeploymentDescriptor;
import io.nop.stream.runtime.taskmanager.TaskManager;

/**
 * Item 14 (composite-scenario distributed): TM-side checkpoint wiring for the
 * remote-deploy path. Before this, {@code TaskManager.deployTask} built the
 * invokable via {@code SubtaskPlanBuilder} but never attached a
 * {@link CheckpointBarrierTracker} — the {@code triggerCheckpoint} RPC's barrier
 * was silently dropped ("No barrier tracker for ..."), checkpoint ACKs never
 * flowed back, and operator state was never restored from the shared checkpoint
 * storage on recovery redeploy. This class closes those three gaps so that a
 * remotely-deployed subtask is checkpoint-reachable exactly like a LOCAL one:
 *
 * <ol>
 *   <li><b>State backend provisioning</b> — operators with managed keyed state
 *       get a {@link MemoryStateBackend} (the LOCAL default when the checkpoint
 *       config carries none), sized with the descriptor's key-group bound so
 *       routing/ownership parity holds. R6 plan 2324 correction: provisioning
 *       sets the operator's state-backend FACTORY, not a created keyed backend
 *       (keyed backends only materialize in open()); the restore-side rescale
 *       detection therefore does NOT depend on any plan-time keyed marker — it
 *       is driven by the checkpoint payload itself
 *       ({@code GraphModelCheckpointExecutor.snapshotHasKeyedState}).</li>
 *   <li><b>Barrier tracker + ACK wiring</b> — a tracker is attached to the
 *       invokable whose completion callback sends the
 *       {@code io.nop.stream.runtime.taskmanager.CheckpointAckMessage} back to
 *       the coordinator over the control RPC (the TM analog of the LOCAL
 *       {@code wireTaskCheckpointPipeline} callback that calls
 *       {@code coordinator.acknowledgeTask} in-process).</li>
 *   <li><b>Restore-on-deploy</b> — when the deployment descriptor carries a
 *       {@code checkpointRestorePath}, the subtask's operator state is restored
 *       from the latest durable epoch in the shared
 *       {@code LocalFileCheckpointStorage} directory (manifest-first), honoring
 *       restore-time parallelism rescale via KeyGroupRange routing. Restore runs
 *       BEFORE {@code setInvokable} so the task thread never processes a record
 *       against pre-restore state.</li>
 * </ol>
 *
 * <p>Commit notification completes the loop coordinator→TM: see
 * {@code TaskManager.notifyCheckpointComplete} (drives the local 2PC sink
 * participants) and {@code JobCoordinator.registerDistributedCommitForwarder}
 * (fans the notification out on every durable checkpoint).
 */
public final class RemoteTaskDeploySupport {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteTaskDeploySupport.class);

    /** The pipeline id used by the coordinator's TaskLocation family. */
    static final String DEFAULT_PIPELINE_ID = "pipeline-0";

    private RemoteTaskDeploySupport() {
    }

    /**
     * Wires one deployed subtask's checkpoint pipeline. Must be called BEFORE
     * {@code RunningTask.setInvokable} so the tracker is attached and state
     * restored before the task thread starts invoking.
     *
     * @param taskManager the owning TaskManager (provides the ACK send path)
     * @param descriptor  the deployment descriptor (jobId / vertexId / subtaskIndex /
     *                    fencing epoch / checkpointRestorePath)
     * @param deployedGraph the CARRIED or spec-rebuilt JobGraph backing this deployment
     *                    (its StreamModel provides the restore fingerprint)
     * @param localPlan   the full locally-built execution plan (all subtasks, mirrors
     *                    the global topology — needed for the checkpoint plan and the
     *                    rescale-aware restore)
     * @param invokable   the invokable that will run on this TM
     */
    public static void wireDeployedSubtask(TaskManager taskManager,
                                           TaskDeploymentDescriptor descriptor,
                                           io.nop.stream.core.jobgraph.JobGraph deployedGraph,
                                           GraphExecutionPlan localPlan,
                                           StreamTaskInvokable invokable) {
        String jobId = descriptor.getJobId();
        String pipelineId = resolvePipelineId(descriptor);
        String vertexId = descriptor.getVertexId();
        int subtaskIndex = descriptor.getSubtaskIndex();

        // 1. Provision state backends (before the checkpoint plan build so the
        //    mappings detect keyed state — the rescale-routing marker).
        provisionStateBackends(invokable, descriptor.getMaxParallelism());

        // 2. Build the checkpoint plan from the full local plan.
        CheckpointPlan checkpointPlan = CheckpointPlanBuilder.build(localPlan, jobId, pipelineId);

        // 3. Attach the barrier tracker with the RPC ACK callback. The
        //    TaskLocation MUST come from the (jobId, pipelineId) family the
        //    coordinator registered via setTasksToAcknowledge — NOT from the
        //    locally-built plan's locations (those derive from the JobGraph
        //    name, which differs from the coordinator's jobId).
        TaskLocation taskLocation = new TaskLocation(jobId, pipelineId, vertexId, subtaskIndex);
        OperatorChain chain = invokable.getOperatorChain();
        java.util.List<StreamOperator<?>> operators = chain.getOperators();
        java.util.List<OperatorStateMapping> mappings = checkpointPlan.getStateMappings(taskLocation);

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(
                taskLocation,
                operators,
                mappings,
                snapshot -> taskManager.sendCheckpointAck(snapshot.getCheckpointId(), snapshot),
                remoteAbortListener(jobId, vertexId, subtaskIndex));
        invokable.setBarrierTracker(tracker);

        // 4. Restore from the shared checkpoint storage (no-op on a fresh job).
        if (descriptor.getCheckpointRestorePath() != null
                && !descriptor.getCheckpointRestorePath().isBlank()) {
            try {
                // Item 14: the fingerprint comes from the RESOLVED graph (carried
                // or spec-rebuilt on this TM) — the descriptor's own jobGraph is
                // null in spec mode.
                StreamModelFingerprint fingerprint = deployedGraph.getStreamModel() != null
                        ? deployedGraph.getStreamModel().computeFingerprint()
                        : null;
                // R6 plan 2324: thread the descriptor's key-group bound so the
                // restore-side maxParallelism resolution falls back to the job's
                // real value instead of the engine default (KR-2 phantom mismatch).
                long restoredEpoch = GraphModelCheckpointExecutor.restoreDeployedSubtaskFromStorage(
                        localPlan, checkpointPlan, descriptor.getCheckpointRestorePath(),
                        jobId, pipelineId, vertexId, subtaskIndex, fingerprint,
                        descriptor.getMaxParallelism());
                if (restoredEpoch >= 0) {
                    LOG.info("Deployed subtask {}/{} of job {} restored from durable epoch {}",
                            vertexId, subtaskIndex, jobId, restoredEpoch);
                }
            } catch (Exception e) {
                // Fail loud (no silent no-op): a restore failure means the subtask
                // would run against wrong state. Propagating lets deployTask report
                // FAILED and trigger recovery instead of silently corrupting output.
                throw new StreamException(ERR_STREAM_INVALID_STATE, e).param(ARG_DETAIL, "Failed to restore deployed subtask " + vertexId + "/" + subtaskIndex
                                + " of job " + jobId + " from "
                                + descriptor.getCheckpointRestorePath());
            }
        }
    }

    /**
     * Provisioning counterpart of
     * {@code GraphModelCheckpointExecutor.wireTaskCheckpointPipeline} — but NOT
     * an exact mirror. Known divergence: the LOCAL wiring first honors
     * {@code checkpointConfig.getStateBackend()} and only falls back to
     * {@link MemoryStateBackend} when unset, while this remote path ALWAYS
     * provisions a {@link MemoryStateBackend} — the TaskManager-side deploy
     * path has no access to the job's CheckpointConfig. Consequence: a job
     * explicitly configured with a non-memory backend (e.g. RocksDB) that runs
     * through remote deploy gets a memory backend on its operators. Unifying
     * the backend TYPE is a semantic change — still under owner adjudication.
     * R5 KR-1/AR-12: the maxParallelism SCALAR no longer diverges — the
     * coordinator ships the graph-frozen keyBy bound in the descriptor and the
     * backend is provisioned with it, so routing/ownership parity holds in the
     * DISTRIBUTED form. Spec-mode note: when the TM rebuilds the graph from a
     * {@code RemotePipelineSpec}, keyBy resolves M from the TM-side default
     * (no DSL backend declaration exists yet, so both sides are 128 — if DSL
     * backend declaration lands, coordinator and TM M must share one source).
     */
    private static void provisionStateBackends(StreamTaskInvokable invokable, int maxParallelism) {
        for (StreamOperator<?> op : invokable.getOperatorChain().getOperators()) {
            if (op instanceof AbstractStreamOperator) {
                AbstractStreamOperator<?> abstractOp = (AbstractStreamOperator<?>) op;
                if (abstractOp.getStateBackend() == null) {
                    IStateBackend backend = maxParallelism >= 1
                            ? new MemoryStateBackend(maxParallelism)
                            : new MemoryStateBackend();
                    abstractOp.setStateBackend(backend);
                }
            }
        }
    }

    /**
     * Snapshot-failure channel for the remote path. There is no coordinator RPC
     * for per-task snapshot failure; the failure is surfaced here (observable)
     * and the coordinator's checkpoint timeout + distributed abort handler
     * cancel the stalled task (checkpoint-design §13.2 independent control
     * channel), after which failure detection drives recovery.
     */
    private static CheckpointFailureListener remoteAbortListener(String jobId, String vertexId, int subtaskIndex) {
        return (checkpointId, error) -> LOG.error(
                "Operator snapshot failed for checkpoint {} on job {} subtask {}/{} — "
                        + "waiting for coordinator-side timeout abort + recovery",
                checkpointId, jobId, vertexId, subtaskIndex, error);
    }

    private static String resolvePipelineId(TaskDeploymentDescriptor descriptor) {
        if (descriptor.getDeploymentPlan() != null
                && descriptor.getDeploymentPlan().getPipelineId() != null
                && !descriptor.getDeploymentPlan().getPipelineId().isEmpty()) {
            return descriptor.getDeploymentPlan().getPipelineId();
        }
        return DEFAULT_PIPELINE_ID;
    }
}
