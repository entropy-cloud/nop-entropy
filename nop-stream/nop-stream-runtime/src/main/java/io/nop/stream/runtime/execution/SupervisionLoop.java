/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.CheckpointPlan;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.OperatorStateMapping;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.InputChannel;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.RecordWriter;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.execution.Subtask;
import io.nop.stream.core.execution.SubtaskTask;
import io.nop.stream.core.execution.TaskExecutor;
import io.nop.stream.core.execution.materialization.IMaterializationPoint;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_MAX_RESTARTS;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_REGION_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_TASK_INDEX;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_VERTEX_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_REGION_RESTART_UNSUPPORTED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_SUPERVISION_TASK_FAILED;

import io.nop.stream.core.execution.flow.EdgeConfig;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.region.Region;
import io.nop.stream.core.jobgraph.region.RegionDecomposition;
import io.nop.stream.core.jobgraph.region.RegionId;

/**
 * Stage 44 successor 3: supervision loop execution model.
 *
 * <p>Replaces {@code GraphModelCheckpointExecutor.submitAndRun}'s
 * {@code awaitCompletion} block-wait with an active supervision loop that:
 * <ol>
 *   <li>Submits all tasks to the {@link TaskExecutor}.</li>
 *   <li>Polls task states at a fixed interval (mid-execution detection).</li>
 *   <li>When a task enters {@link SubtaskTask.State#FAILED FAILED}, identifies
 *       its region and attempts a region-scoped restart.</li>
 *   <li>If the region is restartable (consumer-only region downstream of a
 *       materialization boundary), rebuilds and resubmits the region's tasks
 *       with materialization replay activated.</li>
 *   <li>If the region is not restartable (contains producers needing
 *       drain/reconnect — successor plan 4 scope), or the per-region restart
 *       budget is exhausted, falls back to global recovery by throwing a
 *       {@link StreamException} that the caller surfaces to the existing
 *       recovery path.</li>
 * </ol>
 *
 * <h3>Zero-regression guarantee</h3>
 * For graphs with no materialization-enabled edges (single region), the
 * supervision loop detects the first FAILED task and throws immediately —
 * equivalent to the legacy {@code awaitCompletion} + {@code checkTaskFailures}
 * path. The failure is surfaced explicitly, never silently skipped.
 *
 * <h3>Mailbox interaction contract</h3>
 * When terminating a task for region restart, the supervision loop calls
 * {@code SubtaskTask.cancel()} which:
 * <ol>
 *   <li>CAS-transitions the task to {@link SubtaskTask.State#CANCELING CANCELING}.</li>
 *   <li>Interrupts the live execution thread (if RUNNING).</li>
 * </ol>
 * The task thread observes {@code CANCELING} on its next mailbox drain
 * ({@code MailboxExecutor.processAvailableMails()} returns true for cancelled
 * tasks) and cooperatively exits. Pending timer/checkpoint mails in the
 * terminated task's mailbox are <strong>discarded</strong> — the restarted
 * task builds a fresh {@code MailboxExecutor}. This is safe because:
 * <ul>
 *   <li>Timer mails: the restarted operator re-registers timers from restored
 *       state (checkpoint recovery path) or from scratch (fresh start).</li>
 *   <li>Checkpoint mails: the next checkpoint barrier (from the barrier
 *       scheduler) re-primes the new task's mailbox.</li>
 * </ul>
 * The full drain protocol (capture and re-inject in-flight mails) belongs to
 * drain/reconnect (successor plan 4); here we discard + rebuild.
 *
 * <h3>Exactly-once safety argument</h3>
 * Region-scoped restart triggers consistent-cut replay: the restarted
 * consumer has its operator state restored from the latest completed
 * checkpoint (at epoch {@code N}), then reads from a <em>fresh</em>
 * {@link ResultPartition} that shares the materialization point with the
 * (surviving) producer partition. {@code activateMaterializationReplay(N)}
 * injects precisely the post-checkpoint records (epoch {@code >= N}) at the
 * front. Because the partition is fresh, there is no duplicate data from the
 * old queue. No data is lost (all post-checkpoint records are replayed) and
 * no data is duplicated (fresh partition + checkpoint-aligned epoch cut).
 * When no checkpoint exists (startup edge case), the replay falls back to
 * epoch 0 + full replay with empty operator state — correct because
 * operators start from empty state and full replay rebuilds state from
 * scratch. Exactly-once holds within the replay scope.
 *
 * <p>See {@code ai-dev/design/nop-stream/failover-design.md} §3.5, §五.3, §五.4
 * and §9.4.
 */
@Internal
public class SupervisionLoop {

    private static final Logger LOG = LoggerFactory.getLogger(SupervisionLoop.class);

    /**
     * Default poll interval for the supervision loop (mid-execution failure
     * detection cadence). Short enough to catch failures promptly, long enough
     * to avoid excessive CPU usage in the blocking executor model.
     */
    static final long DEFAULT_POLL_INTERVAL_MS = 100L;

    /**
     * Default per-region restart budget. The global restartCount
     * ({@code JobCoordinator}) is the bottom-line safety net; this per-region
     * counter prevents a single flaky region from being restarted
     * indefinitely within one job execution. Per-region counter persistence
     * is successor plan 5's scope; here we use an in-memory counter.
     */
    static final int DEFAULT_MAX_RESTARTS_PER_REGION = 3;

    /**
     * Runs the supervision loop: submits all tasks, polls for mid-execution
     * failures, and restarts failed regions with materialization replay.
     *
     * <p>This method blocks until all tasks reach a terminal state
     * (COMPLETED/CANCELED) or a non-restartable failure is surfaced (throws).
     *
     * <p>Stage 44 successor 4: the {@code coordinator} and {@code checkpointPlan}
     * enable consistent-cut epoch alignment and operator state restore on
     * region-scoped restart. When both are non-null, restarted tasks have their
     * operator state restored from the latest completed checkpoint and
     * materialization replay starts from the checkpoint-aligned epoch (not epoch
     * 0). When either is null (e.g. checkpoints disabled), restart falls back to
     * epoch 0 + full replay (the successor-3 behavior — correct for finite
     * inputs where operators start from empty state).
     *
     * @param execPlan        the execution plan (provides region decomposition + vertices)
     * @param tasks           the live task map (keyed "{vertexId}-{taskIndex}"); this method
     *                        mutates the map when restarting tasks (replaces entries)
     * @param executor        the task executor (must accept new submissions during the loop)
     * @param jobGraph        the job graph (for edge inspection to classify restartable regions)
     * @param coordinator     the checkpoint coordinator (nullable; provides latest checkpoint
     *                        for state restore + consistent-cut epoch selection)
     * @param checkpointPlan  the checkpoint plan (nullable; provides operator-state mappings
     *                        for restore)
     * @throws InterruptedException if the supervision thread is interrupted
     * @throws StreamException      if a non-restartable failure occurs or the restart
     *                              budget is exhausted (surfaces for global recovery)
     */
    public static void run(GraphExecutionPlan execPlan,
                           Map<String, SubtaskTask> tasks,
                           TaskExecutor executor,
                           JobGraph jobGraph,
                           CheckpointCoordinator coordinator,
                           CheckpointPlan checkpointPlan) throws InterruptedException {
        run(execPlan, tasks, executor, jobGraph, coordinator, checkpointPlan,
                DEFAULT_MAX_RESTARTS_PER_REGION, DEFAULT_POLL_INTERVAL_MS);
    }

    /**
     * Convenience overload without checkpoint context (legacy / non-checkpoint
     * paths). Restarts use epoch 0 + full replay; no operator state restore.
     * Equivalent to passing {@code null} coordinator and checkpointPlan.
     */
    public static void run(GraphExecutionPlan execPlan,
                           Map<String, SubtaskTask> tasks,
                           TaskExecutor executor,
                           JobGraph jobGraph) throws InterruptedException {
        run(execPlan, tasks, executor, jobGraph, null, null,
                DEFAULT_MAX_RESTARTS_PER_REGION, DEFAULT_POLL_INTERVAL_MS);
    }

    /**
     * Full-parameter run method (package-private for focused testing).
     */
    static void run(GraphExecutionPlan execPlan,
                    Map<String, SubtaskTask> tasks,
                    TaskExecutor executor,
                    JobGraph jobGraph,
                    CheckpointCoordinator coordinator,
                    CheckpointPlan checkpointPlan,
                    int maxRestartsPerRegion,
                    long pollIntervalMs) throws InterruptedException {

        // Submit all tasks.
        for (SubtaskTask task : tasks.values()) {
            executor.submitTask(task);
        }
        LOG.info("Supervision loop started: {} tasks submitted, pollInterval={}ms, maxRestartsPerRegion={}",
                tasks.size(), pollIntervalMs, maxRestartsPerRegion);

        RegionDecomposition decomposition = execPlan.getRegionDecomposition();
        boolean singleRegion = decomposition == null || decomposition.getRegionCount() <= 1;

        // Per-region restart counter (in-memory; successor plan 5 adds persistence).
        Map<RegionId, AtomicInteger> regionRestartCounts = new ConcurrentHashMap<>();

        // Main supervision loop.
        while (true) {
            SubtaskTask failedTask = findFirstFailed(tasks);
            if (failedTask != null) {
                RegionId failedRegionId = failedTask.getRegionId();
                String vertexId = failedTask.getSubtask().getVertexId();
                int taskIndex = failedTask.getSubtask().getTaskIndex();
                String regionStr = failedRegionId != null ? failedRegionId.getId() : "null";

                LOG.error("Supervision loop detected FAILED task: vertex={} taskIndex={} region={} error={}",
                        vertexId, taskIndex, regionStr,
                        failedTask.getError() == null ? "unknown" : failedTask.getError().toString(),
                        failedTask.getError());

                if (singleRegion || failedRegionId == null) {
                    // Single-region (no materialization boundary) → no scoped restart possible.
                    // Surface the failure for global recovery (zero-regression with legacy path).
                    throw new StreamException(ERR_STREAM_SUPERVISION_TASK_FAILED, failedTask.getError())
                            .param(ARG_VERTEX_ID, vertexId)
                            .param(ARG_TASK_INDEX, taskIndex)
                            .param(ARG_REGION_ID, regionStr)
                            .param(ARG_DETAIL, "Single-region job: scoped restart not applicable, "
                                    + "surfacing for global recovery");
                }

                // Multi-region: attempt region-scoped restart.
                int count = regionRestartCounts
                        .computeIfAbsent(failedRegionId, k -> new AtomicInteger(0))
                        .incrementAndGet();
                if (count > maxRestartsPerRegion) {
                    LOG.error("Region {} restart budget exhausted: attempts={} max={}",
                            failedRegionId, count, maxRestartsPerRegion);
                    throw new StreamException(ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED, failedTask.getError())
                            .param(ARG_REGION_ID, failedRegionId.getId())
                            .param(ARG_MAX_RESTARTS, maxRestartsPerRegion);
                }

                LOG.info("Supervision loop restarting region {} (attempt {}/{})",
                        failedRegionId, count, maxRestartsPerRegion);
                boolean restarted = restartRegion(execPlan, tasks, executor, jobGraph,
                        decomposition, failedRegionId, coordinator, checkpointPlan);
                if (!restarted) {
                    // Region contains producers needing drain/reconnect (successor plan 4).
                    throw new StreamException(ERR_STREAM_REGION_RESTART_UNSUPPORTED, failedTask.getError())
                            .param(ARG_REGION_ID, failedRegionId.getId());
                }

                // Region was restarted — continue the supervision loop to monitor the new tasks.
                continue;
            }

            // No failed task: check if all tasks have completed successfully.
            // NOTE: we intentionally do NOT treat FAILED as terminal here. If a
            // task transitions from RUNNING to FAILED between findFirstFailed
            // and this check, the loop must continue so the next iteration's
            // findFirstFailed catches it. Only COMPLETED/CANCELED exit the loop.
            if (allTasksCompletedOrCanceled(tasks)) {
                LOG.info("Supervision loop completed: all {} tasks reached COMPLETED/CANCELED", tasks.size());
                break;
            }

            // Still have RUNNING tasks — sleep before next poll.
            Thread.sleep(pollIntervalMs);
        }

        // Post-loop terminal consistency check: at this point every task must
        // be COMPLETED or CANCELED (the loop only exits when allTasksCompletedOrCanceled
        // returns true). A FAILED task here indicates a logic error — surface it
        // explicitly (No-Silent-No-Op).
        for (SubtaskTask task : tasks.values()) {
            if (task.getState() == SubtaskTask.State.FAILED) {
                throw new StreamException(ERR_STREAM_SUPERVISION_TASK_FAILED, task.getError())
                        .param(ARG_VERTEX_ID, task.getSubtask().getVertexId())
                        .param(ARG_TASK_INDEX, task.getSubtask().getTaskIndex())
                        .param(ARG_REGION_ID, task.getRegionId() != null ? task.getRegionId().getId() : "null")
                        .param(ARG_DETAIL, "Post-completion terminal check found FAILED task "
                                + "(supervision loop invariant violation)");
            }
        }
    }

    // ==================== Failure Detection ====================

    /**
     * Returns the first task found in FAILED state, or null if none.
     */
    private static SubtaskTask findFirstFailed(Map<String, SubtaskTask> tasks) {
        for (SubtaskTask task : tasks.values()) {
            if (task.getState() == SubtaskTask.State.FAILED) {
                return task;
            }
        }
        return null;
    }

    /**
     * Returns true only if every task is COMPLETED or CANCELED. A FAILED or
     * still-running task keeps the loop alive so the failure is handled
     * explicitly (never silently exits with a FAILED task).
     */
    private static boolean allTasksCompletedOrCanceled(Map<String, SubtaskTask> tasks) {
        for (SubtaskTask task : tasks.values()) {
            SubtaskTask.State s = task.getState();
            if (s != SubtaskTask.State.COMPLETED && s != SubtaskTask.State.CANCELED) {
                return false;
            }
        }
        return true;
    }

    // ==================== Region Restart ====================

    /**
     * Attempts to restart all tasks in the given region. Returns true if the
     * restart succeeded, false if the region is not restartable (contains
     * producer vertices needing drain/reconnect — successor plan 4 scope).
     *
     * <p>Stage 44 successor 4 Phase 2: drainable producer-regions are also
     * restartable. A region with outgoing cross-region edges has producer
     * vertices whose output feeds downstream consumer regions via materialized
     * edges. Such producers can be safely restarted because:
     * <ul>
     *   <li>The materialization point holds all pre-failure data (overflow-bypass
     *       ensures the producer does not block before death).</li>
     *   <li>The rebuilt producer reuses the old output writer (same
     *       {@link ResultPartition}s + materialization points), so the surviving
     *       consumer continues reading seamlessly.</li>
     *   <li>Operator state is restored from the latest checkpoint (Phase 1),
     *       so the producer resumes from the consistent-cut rather than
     *       re-emitting from scratch.</li>
     * </ul>
     * The {@code ERR_STREAM_REGION_RESTART_UNSUPPORTED} hard rejection is
     * therefore lifted for drainable producer-regions.
     *
     * <p>For all restartable regions, this method:
     * <ol>
     *   <li>Cancels all non-terminal tasks in the region (cooperative cancel
     *       via {@link SubtaskTask#cancel()}, which interrupts the live thread).</li>
     *   <li>Waits for canceled tasks to reach a terminal state.</li>
     *   <li>For each task: builds a fresh {@link OperatorChain} (deep copy),
     *       restores operator state from the latest checkpoint (Phase 1),
     *       wires the consumer to a fresh {@link ResultPartition} sharing the
     *       materialization point (with checkpoint-aligned replay) OR wires the
     *       producer to the reused output writer, builds a new
     *       {@link StreamTaskInvokable} + {@link Subtask} + {@link SubtaskTask},
     *       and resubmits.</li>
     * </ol>
     */
    private static boolean restartRegion(GraphExecutionPlan execPlan,
                                         Map<String, SubtaskTask> tasks,
                                         TaskExecutor executor,
                                         JobGraph jobGraph,
                                         RegionDecomposition decomposition,
                                         RegionId regionId,
                                         CheckpointCoordinator coordinator,
                                         CheckpointPlan checkpointPlan) {
        // Find the region and its vertices.
        Region targetRegion = null;
        for (Region r : decomposition.getRegions()) {
            if (r.getId().equals(regionId)) {
                targetRegion = r;
                break;
            }
        }
        if (targetRegion == null) {
            throw new StreamException(ERR_STREAM_REGION_RESTART_UNSUPPORTED)
                    .param(ARG_REGION_ID, regionId.getId());
        }

        Set<String> verticesInRegion = targetRegion.getVertexIds();

        // Stage 44 successor 4 Phase 2: drainable producer-regions are now
        // restartable (the hard rejection is lifted). The classification is
        // still logged for observability — a producer-region restart exercises
        // the drain/reconnect path (overflow-bypass + reused output writer).
        boolean hasProducerRole = hasOutgoingCrossRegionEdge(jobGraph, decomposition, verticesInRegion);
        if (hasProducerRole) {
            LOG.info("Region {} has outgoing cross-region edges (producer role); "
                    + "proceeding with drainable producer-region restart (Stage 44 successor 4).",
                    regionId);
        }

        LOG.info("Restarting region {} with {} vertices: {} (producerRole={})",
                regionId, verticesInRegion.size(), verticesInRegion, hasProducerRole);

        // Phase 1: cancel all non-terminal tasks in the region.
        List<String> taskKeysToRestart = new ArrayList<>();
        for (String vertexId : verticesInRegion) {
            int parallelism = execPlan.getSubtasks(vertexId).size();
            for (int i = 0; i < parallelism; i++) {
                String taskKey = vertexId + "-" + i;
                SubtaskTask oldTask = tasks.get(taskKey);
                if (oldTask != null && !oldTask.isFinished()) {
                    cancelTaskWithMailbox(oldTask);
                }
                taskKeysToRestart.add(taskKey);
            }
        }

        // Phase 2: wait for canceled tasks to reach terminal state (cooperative).
        for (String taskKey : taskKeysToRestart) {
            SubtaskTask oldTask = tasks.get(taskKey);
            if (oldTask != null) {
                waitForTerminal(oldTask, taskKey);
            }
        }

        // Phase 3: rebuild and resubmit each task in the region.
        for (String taskKey : taskKeysToRestart) {
            SubtaskTask oldTask = tasks.get(taskKey);
            if (oldTask == null) {
                continue;
            }
            SubtaskTask newTask = rebuildTask(execPlan, oldTask, regionId, coordinator, checkpointPlan);
            tasks.put(taskKey, newTask);
            executor.submitTask(newTask);
            LOG.info("Restarted task {} in region {}", taskKey, regionId);
        }

        return true;
    }

    /**
     * Cancels a task using the established 3-step cooperative-cancel recipe:
     * signalCancel (mailbox flag + wake mail) + thread interrupt (via cancel()).
     * This mirrors {@code GraphModelCheckpointExecutor.registerLocalAbortHandler}.
     */
    private static void cancelTaskWithMailbox(SubtaskTask task) {
        StreamTaskInvokable invokable = task.getSubtask().getInvokable();
        if (invokable != null) {
            invokable.getMailboxExecutor().signalCancel();
        }
        task.cancel();
    }

    /**
     * Waits for a task to reach a terminal state. Uses bounded polling to avoid
     * indefinite blocking. The cancel signal has already been delivered; this
     * just waits for the task thread to observe it and exit.
     */
    private static void waitForTerminal(SubtaskTask task, String taskKey) {
        long deadline = System.currentTimeMillis() + 10_000L; // 10s budget
        while (!task.isFinished() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!task.isFinished()) {
            LOG.warn("Task {} did not reach terminal state within 10s after cancel; state={}",
                    taskKey, task.getState());
        }
    }

    /**
     * Rebuilds a single task for region restart. The new task has:
     * <ul>
     *   <li>A fresh {@link OperatorChain} (deep-copied from the JobVertex's chain
     *       template, so operator state is reset).</li>
     *   <li>Stage 44 successor 4: operator state restored from the latest completed
     *       checkpoint (when {@code coordinator} + {@code checkpointPlan} are
     *       provided), so stateful operators (window/CEP/aggregate) retain their
     *       pre-checkpoint accumulated state. Falls back to empty initial state
     *       when no checkpoint exists (startup edge case — operator from empty
     *       state + full replay = correct).</li>
     *   <li>For consumer tasks (with an InputGate): a fresh {@link ResultPartition}
     *       sharing the materialization point of the old partition, with
     *       materialization replay activated at the checkpoint-aligned epoch
     *       (post-checkpoint records only). When the producer partition is
     *       finished, the fresh partition is sealed (EOS); otherwise it stays
     *       open for reconnect-to-live-queue (Phase 3).</li>
     *   <li>For producer tasks (with an output writer, no InputGate): reuses the
     *       old output writer (points to the same {@link ResultPartition}s with
     *       their attached materialization points), so the surviving consumer
     *       continues reading seamlessly.</li>
     *   <li>A fresh {@link InputGate}/{@link InputChannel} pointing to the fresh
     *       partition (consumer) or a fresh {@link StreamTaskInvokable} wired to
     *       the reused writer (producer).</li>
     * </ul>
     */
    private static SubtaskTask rebuildTask(GraphExecutionPlan execPlan,
                                           SubtaskTask oldTask,
                                           RegionId regionId,
                                           CheckpointCoordinator coordinator,
                                           CheckpointPlan checkpointPlan) {
        Subtask oldSubtask = oldTask.getSubtask();
        String vertexId = oldSubtask.getVertexId();
        int taskIndex = oldSubtask.getTaskIndex();
        TaskLocation taskLocation = oldSubtask.getTaskLocation();

        JobVertex jobVertex = execPlan.getExecutionVertices().get(vertexId);
        if (jobVertex == null) {
            throw new StreamException(ERR_STREAM_REGION_RESTART_UNSUPPORTED)
                    .param(ARG_REGION_ID, regionId.getId())
                    .param(ARG_DETAIL, "JobVertex not found for vertexId=" + vertexId
                            + " during region restart");
        }

        // Deep-copy the operator chain (fresh operator state).
        OperatorChain newChain = jobVertex.getOperatorChains().get(0).deepCopy();

        // Stage 44 successor 4: consistent-cut epoch selection + operator state
        // restore. When a checkpoint coordinator + plan are available, restore
        // operator state from the latest completed checkpoint and use its id as
        // the consistent-cut epoch for materialization replay. Without a
        // checkpoint (startup edge case), fall back to epoch 0 + full replay +
        // empty operator state (successor-3 behavior — correct for finite input
        // or first-run scenarios where operators start from empty state).
        CompletedCheckpoint latestCheckpoint = coordinator != null ? coordinator.getLatestCheckpoint() : null;
        long consistentCutEpoch = 0L;
        if (latestCheckpoint != null) {
            consistentCutEpoch = latestCheckpoint.getCheckpointId();
            // Restore operator state from the checkpoint. fail-fast if the
            // checkpoint exists but this task's state is missing — that
            // indicates a topology/identity mismatch, not a fresh start
            // (No-Silent-No-Op #24).
            if (checkpointPlan != null) {
                TaskStateSnapshot taskState = latestCheckpoint.getTaskState(taskLocation);
                if (taskState == null) {
                    throw new StreamException(ERR_STREAM_REGION_RESTART_UNSUPPORTED)
                            .param(ARG_REGION_ID, regionId.getId())
                            .param(ARG_VERTEX_ID, vertexId)
                            .param(ARG_TASK_INDEX, taskIndex)
                            .param(ARG_DETAIL, "Checkpoint " + consistentCutEpoch
                                    + " exists but has no state for task " + taskLocation
                                    + " (topology/identity mismatch); refusing to silently"
                                    + " restart from empty state");
                }
                List<OperatorStateMapping> mappings = checkpointPlan.getStateMappings(taskLocation);
                try {
                    GraphModelCheckpointExecutor.restoreOperatorsFromState(
                            newChain, consistentCutEpoch, taskState, mappings);
                    LOG.info("Restored operator state for vertex={} taskIndex={} from checkpoint epoch {}",
                            vertexId, taskIndex, consistentCutEpoch);
                } catch (Exception e) {
                    throw new StreamException(ERR_STREAM_REGION_RESTART_UNSUPPORTED, e)
                            .param(ARG_REGION_ID, regionId.getId())
                            .param(ARG_VERTEX_ID, vertexId)
                            .param(ARG_TASK_INDEX, taskIndex)
                            .param(ARG_DETAIL, "Operator state restore failed from checkpoint epoch "
                                    + consistentCutEpoch + ": " + e.getMessage());
                }
            }
        } else {
            LOG.info("No completed checkpoint available for region restart; using epoch 0 + full"
                    + " replay with empty operator state (startup edge case) for vertex={} taskIndex={}",
                    vertexId, taskIndex);
        }

        // Determine the role: if the old task had an InputGate, it's a consumer.
        StreamTaskInvokable oldInvokable = oldSubtask.getInvokable();
        InputGate oldInputGate = oldInvokable.getInputGate();
        RecordWriter<Object> oldOutputWriter = oldInvokable.getOutputWriter();

        StreamTaskInvokable newInvokable;
        if (oldInputGate != null) {
            // Consumer role (SINK or MIDDLE): build a fresh InputGate with
            // materialization replay activated at the checkpoint-aligned epoch.
            //
            // Stage 44 successor 4 Phase 3 (reconnect-to-live-queue): when the
            // producer partition is NOT finished (infinite source / producer
            // still running), the consumer must reconnect to the LIVE partition
            // after replay. This is implemented by REUSING the old partition:
            //   1. Drain stale queue data (already captured in the materialization
            //      store → no loss; removes duplicates that would otherwise
            //      overlap with the replay injection).
            //   2. injectFront the post-checkpoint replay data.
            //   3. The consumer reads replay data first, then live data from the
            //      surviving producer (which continues writing to the same queue).
            // InputChannel.partition is final, so reconnect creates a NEW
            // InputChannel wrapping the reused (old) partition and feeds it into
            // the fresh InputGate.
            //
            // When the producer partition IS finished (finite source), a fresh
            // partition is used (no live producer to reconnect to) and sealed
            // after replay so the consumer sees EOS.
            List<InputChannel> newChannels = new ArrayList<>();
            for (InputChannel oldChannel : oldInputGate.getChannels()) {
                ResultPartition oldPartition = oldChannel.getPartition();
                IMaterializationPoint matPoint = oldPartition.getMaterializationPoint();

                ResultPartition consumerPartition;
                if (matPoint != null && !oldPartition.isFinished()) {
                    // Phase 3 reconnect-to-live-queue: reuse the live partition.
                    // Drain stale data (it's in the materialization store), then
                    // injectFront the post-checkpoint replay data.
                    java.util.List<io.nop.stream.core.streamrecord.StreamElement> drained =
                            oldPartition.drainBufferedElements();
                    consumerPartition = oldPartition;
                    consumerPartition.setMaterializationPoint(matPoint);
                    InputChannel tempChannel = new InputChannel(consumerPartition);
                    int injected = tempChannel.activateMaterializationReplay(consistentCutEpoch);
                    LOG.info("Reconnect-to-live-queue: drained {} stale element(s), replayed {} post-checkpoint"
                            + " element(s) (epoch >= {}) into live partition for vertex={} taskIndex={}"
                            + " (producer still running — consumer will continue reading live data after replay)",
                            drained.size(), injected, consistentCutEpoch, vertexId, taskIndex);
                } else {
                    // Finite source (producer finished) OR no materialization:
                    // fresh partition + replay + seal (EOS).
                    consumerPartition = new ResultPartition();
                    if (matPoint != null) {
                        consumerPartition.setMaterializationPoint(matPoint);
                        InputChannel tempChannel = new InputChannel(consumerPartition);
                        int injected = tempChannel.activateMaterializationReplay(consistentCutEpoch);
                        LOG.info("Replayed {} materialized elements (epoch >= {}) into fresh partition"
                                + " for vertex={} taskIndex={} (producer finished — fresh partition sealed)",
                                injected, consistentCutEpoch, vertexId, taskIndex);
                        if (oldPartition.isFinished()) {
                            try {
                                consumerPartition.close();
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new StreamException(ERR_STREAM_REGION_RESTART_UNSUPPORTED, ie)
                                        .param(ARG_REGION_ID, regionId.getId())
                                        .param(ARG_DETAIL, "Interrupted while sealing fresh partition during region restart");
                            }
                        }
                    }
                }
                newChannels.add(new InputChannel(consumerPartition));
            }

            InputGate newInputGate = new InputGate(newChannels, (EdgeConfig) null, false);
            // Consumer role: chain + null writer + inputGate → SINK invokable.
            newInvokable = new StreamTaskInvokable(newChain, (RecordWriter<?>) null, newInputGate);
        } else if (oldOutputWriter != null) {
            // Stage 44 successor 4 Phase 2: producer role (SOURCE or MIDDLE with
            // no InputGate). Reuse the old output writer so the new producer
            // writes to the SAME ResultPartition(s) the surviving consumer is
            // reading from (with the same attached materialization points). The
            // consumer continues reading seamlessly; no explicit cross-region
            // reconnect is needed when the consumer is healthy. Operator state
            // was restored above (if a checkpoint exists) or starts fresh.
            newInvokable = new StreamTaskInvokable(newChain, oldOutputWriter, null);
            LOG.info("Rebuilt producer task vertex={} taskIndex={} reusing existing output writer"
                    + " (state restored from epoch {})", vertexId, taskIndex, consistentCutEpoch);
        } else {
            // No InputGate and no output writer → self-contained.
            newInvokable = new StreamTaskInvokable(newChain);
        }

        // Build the new subtask + SubtaskTask.
        Subtask newSubtask = new Subtask(vertexId, taskIndex, taskLocation, newInvokable, regionId);
        List<OperatorChain> chainList = Collections.singletonList(newChain);
        return new SubtaskTask(newSubtask, jobVertex, chainList);
    }

    // ==================== Region Classification ====================

    /**
     * Returns true if any vertex in {@code regionVertices} has an outgoing edge
     * to a vertex in a <em>different</em> region. Such an edge makes the vertex
     * a producer whose output must be drained/reconnected safely during restart
     * (successor plan 4 scope). A consumer-only region has no outgoing
     * cross-region edges and can be safely restarted with materialization replay.
     */
    private static boolean hasOutgoingCrossRegionEdge(JobGraph jobGraph,
                                                      RegionDecomposition decomposition,
                                                      Set<String> regionVertices) {
        List<JobEdge> edges = jobGraph.getEdges();
        if (edges == null) {
            return false;
        }
        for (JobEdge edge : edges) {
            String source = edge.getSourceVertex();
            String target = edge.getTargetVertex();
            if (!regionVertices.contains(source)) {
                continue;
            }
            // The source is in our region. Is the target in a different region?
            RegionId sourceRegion = decomposition.getRegionId(source);
            RegionId targetRegion = decomposition.getRegionId(target);
            if (targetRegion != null && !targetRegion.equals(sourceRegion)) {
                return true;
            }
        }
        return false;
    }
}
