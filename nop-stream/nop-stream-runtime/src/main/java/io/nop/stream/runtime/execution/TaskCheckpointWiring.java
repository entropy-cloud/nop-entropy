/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointPlan;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.OperatorStateMapping;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.CheckpointBarrierTracker;
import io.nop.stream.core.execution.CheckpointFailureListener;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.execution.task.Subtask;
import io.nop.stream.core.execution.task.SubtaskTask;
import io.nop.stream.core.execution.task.TaskExecutor;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.AbstractUdfStreamOperator;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.util.NopStreamThreadFactory;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_TASK_INDEX;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_VERTEX_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_EXECUTOR_JOB_GRAPH_INVALID;

/**
 * Checkpoint-pipeline wiring collaborator of {@link GraphModelCheckpointExecutor}:
 * registers tasks and barrier trackers, drives the periodic barrier scheduler,
 * applies epoch-precise local aborts, and performs the joint executor/coordinator
 * shutdown. All members are static functions over their parameters; state lives
 * in the passed-in coordinator / invokables. The host class keeps package-private
 * static delegates so the external call sites ({@code SupervisionLoop}, focused
 * tests) keep their signatures.
 */
class TaskCheckpointWiring {

    // Logs under the GraphModelCheckpointExecutor logger name — wiring
    // diagnostics keep their original logger.
    private static final Logger LOG = LoggerFactory.getLogger(GraphModelCheckpointExecutor.class);

/**
 * Registers every task's checkpoint pipeline wiring (tracker + coordinator
 * task registration + listener/participant registration + state-backend
 * provisioning) and returns the thread-safe list of all invokables used by
 * the barrier scheduler.
 *
 * <p>The returned list is a {@link CopyOnWriteArrayList} — the barrier
 * scheduler thread iterates it while the supervision thread replaces an
 * invokable during a region restart (remove old + add new). A plain
 * ArrayList would throw CME inside the scheduler's for-each, and the
 * {@link #startBarrierScheduler} catch would swallow it, silently dropping
 * that tick's barrier.
 */
static List<StreamTaskInvokable> registerTasksAndTrackers(
        GraphExecutionPlan execPlan,
        CheckpointPlan checkpointPlan,
        CheckpointCoordinator coordinator,
        CheckpointConfig checkpointConfig) {

    List<StreamTaskInvokable> allInvokables = new CopyOnWriteArrayList<>();

    // Inject per-task data-plane
    // metrics on the LOCAL execution path (the REMOTE path injects in
    // TaskManager install/deploy).
    String jobId = coordinator.getJobId();

    for (String vertexId : execPlan.getSortedVertexIds()) {
        JobVertex execVertex = execPlan.getExecutionVertices().get(vertexId);

        for (Subtask subtask : execPlan.getSubtasks(vertexId)) {
            StreamTaskInvokable invokable = subtask.getInvokable();
            invokable.setTaskMetrics(new io.nop.stream.core.metrics.MicrometerStreamTaskMetrics(
                    io.nop.stream.core.metrics.StreamMetricsRegistries.registry(),
                    jobId, vertexId, subtask.getTaskIndex()));
            allInvokables.add(invokable);

            TaskLocation taskLocation = findTaskLocationInPlan(checkpointPlan, vertexId, subtask.getTaskIndex());
            wireTaskCheckpointPipeline(coordinator, checkpointPlan, checkpointConfig,
                    invokable, taskLocation);
        }
    }

    return allInvokables;
}

/**
 * Wires one task's checkpoint pipeline: coordinator task registration,
 * {@link CheckpointBarrierTracker} creation + attachment (which triggers
 * {@code setupSnapshotCallbacks} so every operator's {@code snapshotCallback}
 * is non-null), CheckpointListener/CheckpointParticipant registration for the
 * chain's operators (and their UDFs), and state-backend provisioning.
 *
 * <p>The region-restart
 * path ({@code SupervisionLoop.rebuildTask}) reuses the exact same
 * wiring for rebuilt invokables — a rebuilt task must be indistinguishable
 * from an initially-registered task w.r.t. the checkpoint pipeline.
 *
 * @param coordinator      the checkpoint coordinator (must be non-null)
 * @param checkpointPlan   the checkpoint plan (state mappings for the tracker)
 * @param checkpointConfig the job's checkpoint config (state-backend source;
 *                         may be null → MemoryStateBackend default)
 * @param invokable        the task's invokable to wire
 * @param taskLocation     the checkpoint-plan task location for this task
 */
static void wireTaskCheckpointPipeline(
        CheckpointCoordinator coordinator,
        CheckpointPlan checkpointPlan,
        CheckpointConfig checkpointConfig,
        StreamTaskInvokable invokable,
        TaskLocation taskLocation) {
    coordinator.registerTask(taskLocation);

    List<OperatorStateMapping> mappings = checkpointPlan.getStateMappings(taskLocation);

    // IMPORTANT: use the invokable's ACTUAL operator chain, not the original
    // execVertex chains. For multi-vertex topologies the execution plan
    // deep-copies each chain (GraphExecutionPlan line ~215), so the original
    // chains reference different operator instances than the ones the invokable
    // runs. Creating the tracker / snapshot callbacks / state-backend wiring
    // from the original chains would disconnect checkpoint priming, barrier
    // injection, and ACKs from the live operators.
    OperatorChain chain = invokable.getOperatorChain();
    List<StreamOperator<?>> operators = chain.getOperators();

    CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(
            taskLocation, operators, mappings,
            snapshot -> coordinator.acknowledgeTask(taskLocation, snapshot.getCheckpointId(), snapshot),
            (CheckpointFailureListener) (checkpointId, error) ->
                    coordinator.reportTaskCheckpointFailure(taskLocation, checkpointId, error)
    );

    invokable.setBarrierTracker(tracker);

    for (StreamOperator<?> op : operators) {
        if (op instanceof CheckpointListener) {
            coordinator.addListener((CheckpointListener) op);
        }
        if (op instanceof AbstractUdfStreamOperator) {
            Object udf = ((AbstractUdfStreamOperator<?, ?>) op).getUserFunction();
            if (udf instanceof CheckpointListener && udf != op) {
                coordinator.addListener((CheckpointListener) udf);
            }
            if (udf instanceof CheckpointParticipant && udf != op) {
                coordinator.addParticipant((CheckpointParticipant) udf);
            }
        }
        if (op instanceof CheckpointParticipant && !(op instanceof AbstractUdfStreamOperator)) {
            coordinator.addParticipant((CheckpointParticipant) op);
        }

        // Provision state backend for operators that need managed keyed state
        if (op instanceof AbstractStreamOperator) {
            AbstractStreamOperator<?> abstractOp = (AbstractStreamOperator<?>) op;
            if (abstractOp.getStateBackend() == null) {
                IStateBackend configuredBackend = checkpointConfig != null
                        ? checkpointConfig.getStateBackend() : null;
                IStateBackend stateBackend = configuredBackend != null
                        ? configuredBackend
                        : new MemoryStateBackend();
                abstractOp.setStateBackend(stateBackend);
            }
        }
    }
}

/**
 * Removes a task's operator chain from the coordinator's
 * CheckpointListener / CheckpointParticipant registries. Called by the
 * region-restart path for the OLD (superseded) task's operators
 * before the rebuilt task's operators are registered, so repeated region
 * restarts do not accumulate stale listeners/participants on dead operator
 * instances (mirror of the registration logic in
 * {@link #wireTaskCheckpointPipeline}).
 *
 * <p>Shared UDFs are safe: when the old and new chain reference the same
 * user-function object, unwire removes it and wire re-adds it — net
 * single registration; when they differ, each instance is registered once.
 */
static void unwireTaskCheckpointPipeline(CheckpointCoordinator coordinator, StreamTaskInvokable invokable) {
    if (invokable == null || invokable.getOperatorChain() == null) {
        return;
    }
    for (StreamOperator<?> op : invokable.getOperatorChain().getOperators()) {
        if (op instanceof CheckpointListener) {
            coordinator.removeListener((CheckpointListener) op);
        }
        if (op instanceof AbstractUdfStreamOperator) {
            Object udf = ((AbstractUdfStreamOperator<?, ?>) op).getUserFunction();
            if (udf instanceof CheckpointListener && udf != op) {
                coordinator.removeListener((CheckpointListener) udf);
            }
            if (udf instanceof CheckpointParticipant && udf != op) {
                coordinator.removeParticipant((CheckpointParticipant) udf);
            }
        }
        if (op instanceof CheckpointParticipant && !(op instanceof AbstractUdfStreamOperator)) {
            coordinator.removeParticipant((CheckpointParticipant) op);
        }
    }
}

/**
 * Finds the checkpoint plan's {@link TaskLocation} instance for the given
 * vertex/task-index. Package-private so the region-restart path
 * ({@code SupervisionLoop.rebuildTask}) resolves the SAME location family
 * as the initial registration — the checkpoint pipeline (coordinator
 * tasksToAcknowledge, tracker ACKs, state restore) is keyed by these
 * instances, and the execution plan's locations (jobGraph name based) are
 * value-distinct whenever the configured jobId/pipelineId differ.
 */
static TaskLocation findTaskLocationInPlan(CheckpointPlan plan, String vertexId, int taskIndex) {
    for (TaskLocation loc : plan.getAllTasks()) {
        if (loc.getVertexId().equals(vertexId) && loc.getTaskIndex() == taskIndex) {
            return loc;
        }
    }
    throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_JOB_GRAPH_INVALID)
            .param(ARG_VERTEX_ID, vertexId)
            .param(ARG_TASK_INDEX, taskIndex);
}

static ScheduledExecutorService startBarrierScheduler(
        List<StreamTaskInvokable> allInvokables,
        CheckpointCoordinator coordinator,
        CheckpointConfig config,
        String jobId) {

    if (allInvokables.isEmpty()) {
        return null;
    }

    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            NopStreamThreadFactory.named("barrier-injector-" + jobId));

    scheduler.scheduleAtFixedRate(() -> {
        try {
            PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            if (pending != null) {
                triggerBarrierOnAllInvokables(allInvokables, pending);
            }
        } catch (Exception e) {
            LOG.error("Failed to inject checkpoint barrier for job {}", jobId, e);
            coordinator.incrementTriggerFailures();
        }
    }, config.getCheckpointInterval(), config.getCheckpointInterval(), TimeUnit.MILLISECONDS);

    return scheduler;
}

static void triggerBarrierOnAllInvokables(
        List<StreamTaskInvokable> allInvokables, PendingCheckpoint pending) throws Exception {
    for (StreamTaskInvokable inv : allInvokables) {
        if (inv.getBarrierTracker() != null) {
            // Per-invokable containment: a throw from one tracker must not abort
            // the loop and leave the remaining invokables without this epoch's
            // barrier (a partial barrier injection dooms the checkpoint to
            // timeout-abort with no per-task diagnostics).
            try {
                boolean accepted = inv.getBarrierTracker().triggerCheckpoint(
                        pending.getCheckpointId(),
                        pending.getTriggerTimestamp(),
                        pending.getCheckpointType()
                );
                if (!accepted) {
                    LOG.warn("Checkpoint {} skipped for task due to overlap", pending.getCheckpointId());
                }
            } catch (Exception e) {
                LOG.error("Failed to inject checkpoint {} barrier into invokable {}",
                        pending.getCheckpointId(), inv, e);
            }
        }
    }
}

/**
 * Package-private so the single-input abort
 * wiring e2e can register the REAL production handler — the test must exercise
 * the production abort chain, not a hand-copied handler body.
 */
static AtomicBoolean registerLocalAbortHandler(
        CheckpointCoordinator coordinator,
        Map<String, SubtaskTask> tasks) {
    AtomicBoolean abortMarked = new AtomicBoolean(false);
    coordinator.setAbortHandler(abortedCheckpointId -> {
        LOG.warn("Checkpoint {} aborted, applying epoch-precise abort to local tasks", abortedCheckpointId);
        boolean anyTaskStillHasInFlight = false;
        for (SubtaskTask task : tasks.values()) {
            // Notify barrier tracker to release ACK wait for THIS epoch only
            // (other in-flight epochs are undisturbed).
            StreamTaskInvokable invokable = task.getSubtask().getInvokable();
            CheckpointBarrierTracker tracker = invokable.getBarrierTracker();
            if (tracker != null) {
                tracker.notifyCheckpointAborted(abortedCheckpointId);
            }
            // Release THIS epoch's InputGate alignment only (not
            // resumeConsumptionAll), so channels blocked by the aborted barrier are freed
            // while other epochs' alignment state is preserved. The InputGate's alignment
            // collections (inFlightAlignments / abortedBarriers / blockedChannels, plus the
            // per-BarrierAlignment channel sets) are concurrent-safe structures, so this
            // cross-thread call does NOT throw ConcurrentModificationException and does not
            // corrupt the task thread's in-progress barrier iteration. A mailbox-delivered
            // abort would not work here: InputGate.read()
            // blocks inside barrier alignment and only drains the mailbox at the caller's
            // (processInputGate) loop top, so a mailbox-delivered abort could not unblock
            // the read and would deadlock the epoch-precise abort until alignment timeout.
            InputGate inputGate = invokable.getInputGate();
            if (inputGate != null) {
                inputGate.abortBarrierAlignment(abortedCheckpointId);
            }
            // Design §2.8.1 D3: only cancel the task thread when no
            // other epoch is in-flight for it. If other epochs remain, the task
            // keeps running so they can still ACK/complete (epoch-precise abort).
            if (tracker != null && tracker.hasInFlightCheckpoints()) {
                anyTaskStillHasInFlight = true;
                LOG.debug("Task {} still has in-flight epoch(s) after abort of {}; not cancelling",
                        task, abortedCheckpointId);
                continue;
            }
            // No other epochs in-flight → cooperative cancel + interrupt.
            invokable.getMailboxExecutor().signalCancel();
            if (inputGate != null) {
                inputGate.resumeConsumptionAll();
            }
            task.cancel();
        }
        // Only mark the job-wide abort flag when no task has remaining
        // in-flight epochs (i.e. this abort actually empties the pipeline). When
        // other epochs survive, the job is still healthy and the final-checkpoint
        // skip must not fire.
        if (!anyTaskStillHasInFlight) {
            abortMarked.set(true);
        }
    });
    return abortMarked;
}

static void shutdown(ScheduledExecutorService barrierScheduler, CheckpointCoordinator coordinator, TaskExecutor executor) {
    if (executor != null) {
        executor.shutdownNow();
    }
    if (barrierScheduler != null) {
        barrierScheduler.shutdownNow();
    }
    coordinator.shutdown();
}
}
