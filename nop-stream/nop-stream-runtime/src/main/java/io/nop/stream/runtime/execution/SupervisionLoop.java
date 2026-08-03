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
import io.nop.stream.core.checkpoint.TaskLocation;
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
 * Region-scoped restart triggers materialization replay: the restarted
 * consumer reads from a <em>fresh</em> {@link ResultPartition} that shares the
 * materialization point with the (surviving) producer partition. The fresh
 * partition starts empty; {@code activateMaterializationReplay(0L)} injects
 * all materialized data at the front. Because the partition is fresh, there
 * is no duplicate data from the old queue. The replayed data is the complete
 * dataset (finite input for E2E; the consistent-cut epoch selection is
 * successor plan 4's responsibility — here we replay from epoch 0). No data
 * is lost (all materialized data is replayed) and no data is duplicated
 * (fresh partition, no stale queue content). Exactly-once holds within the
 * replay scope.
 *
 * <p>See {@code ai-dev/design/nop-stream/failover-design.md} §3.5 and §五.3.
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
     * @param execPlan    the execution plan (provides region decomposition + vertices)
     * @param tasks       the live task map (keyed "{vertexId}-{taskIndex}"); this method
     *                    mutates the map when restarting tasks (replaces entries)
     * @param executor    the task executor (must accept new submissions during the loop)
     * @param jobGraph    the job graph (for edge inspection to classify restartable regions)
     * @throws InterruptedException if the supervision thread is interrupted
     * @throws StreamException      if a non-restartable failure occurs or the restart
     *                              budget is exhausted (surfaces for global recovery)
     */
    public static void run(GraphExecutionPlan execPlan,
                           Map<String, SubtaskTask> tasks,
                           TaskExecutor executor,
                           JobGraph jobGraph) throws InterruptedException {
        run(execPlan, tasks, executor, jobGraph,
                DEFAULT_MAX_RESTARTS_PER_REGION, DEFAULT_POLL_INTERVAL_MS);
    }

    /**
     * Full-parameter run method (package-private for focused testing).
     */
    static void run(GraphExecutionPlan execPlan,
                    Map<String, SubtaskTask> tasks,
                    TaskExecutor executor,
                    JobGraph jobGraph,
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
                        decomposition, failedRegionId);
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
     * <p>Restartable regions are <strong>consumer-only</strong> regions (all
     * vertices are pure sinks: no outgoing edges to other regions). When a
     * region has an outgoing edge to another region, its tasks are producers
     * whose output partitions must be drained/reconnected safely — this
     * belongs to successor plan 4 (drain/reconnect). For such regions, this
     * method returns false so the caller falls back to global recovery.
     *
     * <p>For restartable (consumer-only) regions, this method:
     * <ol>
     *   <li>Cancels all non-terminal tasks in the region (cooperative cancel
     *       via {@link SubtaskTask#cancel()}, which interrupts the live thread).</li>
     *   <li>Waits for canceled tasks to reach a terminal state.</li>
     *   <li>For each task: builds a fresh {@link OperatorChain} (deep copy),
     *       a fresh {@link ResultPartition} sharing the materialization point,
     *       a fresh {@link InputGate}/{@link InputChannel}, activates
     *       materialization replay, builds a new {@link StreamTaskInvokable}
     *       + {@link Subtask} + {@link SubtaskTask}, and resubmits.</li>
     * </ol>
     */
    private static boolean restartRegion(GraphExecutionPlan execPlan,
                                         Map<String, SubtaskTask> tasks,
                                         TaskExecutor executor,
                                         JobGraph jobGraph,
                                         RegionDecomposition decomposition,
                                         RegionId regionId) {
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

        // Classify restartability: the region is restartable only if NONE of its
        // vertices has an outgoing edge crossing into a different region (i.e.
        // every vertex is a pure consumer/sink relative to the materialization
        // boundary). An outgoing cross-region edge means the vertex is a producer
        // whose output must be drained/reconnected (successor plan 4 scope).
        if (hasOutgoingCrossRegionEdge(jobGraph, decomposition, verticesInRegion)) {
            LOG.warn("Region {} has outgoing cross-region edges (producer role); "
                    + "restart requires drain/reconnect (successor plan 4). Falling back.",
                    regionId);
            return false;
        }

        LOG.info("Restarting region {} with {} vertices: {}", regionId, verticesInRegion.size(), verticesInRegion);

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
            SubtaskTask newTask = rebuildTask(execPlan, oldTask, regionId);
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
     *   <li>For consumer tasks (with an InputGate): a fresh {@link ResultPartition}
     *       sharing the materialization point of the old partition, with
     *       materialization replay activated (injects all materialized data at
     *       epoch {@code >= 0} into the fresh partition's queue).</li>
     *   <li>A fresh {@link InputGate}/{@link InputChannel} pointing to the fresh
     *       partition.</li>
     *   <li>A fresh {@link StreamTaskInvokable} with the new chain + gate.</li>
     * </ul>
     */
    private static SubtaskTask rebuildTask(GraphExecutionPlan execPlan,
                                           SubtaskTask oldTask,
                                           RegionId regionId) {
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

        // Determine the role: if the old task had an InputGate, it's a consumer.
        StreamTaskInvokable oldInvokable = oldSubtask.getInvokable();
        InputGate oldInputGate = oldInvokable.getInputGate();

        StreamTaskInvokable newInvokable;
        if (oldInputGate != null) {
            // Consumer role (SINK or MIDDLE): build a fresh partition + gate with
            // materialization replay. For each old InputChannel, create a fresh
            // ResultPartition sharing the materialization point, then activate
            // replay so the materialized data is injected at the front.
            List<InputChannel> newChannels = new ArrayList<>();
            for (InputChannel oldChannel : oldInputGate.getChannels()) {
                ResultPartition oldPartition = oldChannel.getPartition();
                IMaterializationPoint matPoint = oldPartition.getMaterializationPoint();

                ResultPartition freshPartition = new ResultPartition();
                if (matPoint != null) {
                    freshPartition.setMaterializationPoint(matPoint);
                    // Activate replay: injects all materialized elements (epoch >= 0)
                    // into the fresh partition's queue. The consistent-cut epoch
                    // selection is successor plan 4's scope; here we replay from
                    // epoch 0 (complete replay for finite-input E2E).
                    InputChannel tempChannel = new InputChannel(freshPartition);
                    int injected = tempChannel.activateMaterializationReplay(0L);
                    LOG.info("Replayed {} materialized elements into fresh partition for vertex={} taskIndex={}",
                            injected, vertexId, taskIndex);
                    // If the producer partition is finished (closed), the replayed
                    // data represents the complete dataset (finite input). Close
                    // the fresh partition to signal EOS so the consumer exits
                    // after processing the replay — it does not block indefinitely
                    // waiting for more data that will never arrive.
                    // (Reconnect-to-live-queue for the infinite-source case is
                    // successor plan 4's scope.)
                    if (oldPartition.isFinished()) {
                        try {
                            freshPartition.close();
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new StreamException(ERR_STREAM_REGION_RESTART_UNSUPPORTED, ie)
                                    .param(ARG_REGION_ID, regionId.getId())
                                    .param(ARG_DETAIL, "Interrupted while sealing fresh partition during region restart");
                        }
                    }
                }
                // If no materialization point (shouldn't happen for a consumer in a
                // restartable region — it would have been classified as non-restartable),
                // the fresh partition is empty. The consumer will read EOS immediately.
                newChannels.add(new InputChannel(freshPartition));
            }

            InputGate newInputGate = new InputGate(newChannels, (EdgeConfig) null, false);
            // Consumer role: chain + null writer + inputGate → SINK invokable.
            newInvokable = new StreamTaskInvokable(newChain, (RecordWriter<?>) null, newInputGate);
        } else {
            // No InputGate → source or self-contained. In a restartable region
            // (consumer-only), this branch should not be reached. If it is, build
            // a self-contained invokable (the caller has already verified the region
            // is consumer-only, so this is a defensive fallback).
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
