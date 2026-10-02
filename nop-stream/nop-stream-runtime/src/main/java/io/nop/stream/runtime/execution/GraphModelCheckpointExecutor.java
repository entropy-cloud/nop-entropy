/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.api.core.time.CoreMetrics;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointPlan;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.ChannelState;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.JobTerminationMode;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.OperatorStateMapping;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.TaskEpochSnapshot;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.checkpoint.StorageJobIds;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.environment.StreamExecutionResult;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.model.StreamRequirement;
import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_CHECKPOINT_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_CHECKPOINT_VERTEX_IDS;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_CURRENT_VERTEX_IDS;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EPOCH_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_MISSING_VERTEX_IDS;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_NEW_PARALLELISM;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_OLD_PARALLELISM;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_REASON;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_TASK_INDEX;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_TASK_LOCATION;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_VERTEX_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_2PC_SINK_PARALLELISM_CHANGE_UNSUPPORTED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_ABORTED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_EXECUTOR_EXECUTE_FAILED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_EXECUTOR_FAILED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_EXECUTOR_JOB_GRAPH_INVALID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_EXECUTOR_SAVEPOINT_FAILED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_SAVEPOINT_VERTEX_DIFFERENTIAL;
import io.nop.stream.core.execution.CheckpointBarrierTracker;
import io.nop.stream.core.execution.CheckpointFailureListener;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.execution.task.Subtask;
import io.nop.stream.core.execution.task.SubtaskTask;
import io.nop.stream.core.execution.task.TaskExecutor;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.model.StreamModel;
import io.nop.stream.core.model.StreamModelFingerprint;
import io.nop.stream.core.util.NopStreamThreadFactory;
import io.nop.stream.core.common.state.shard.KeyGroup;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
import io.nop.stream.core.common.state.shard.KeyGroupRange;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.AbstractUdfStreamOperator;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.CheckpointPlanBuilder;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.runtime.checkpoint.metrics.CheckpointMetricsSnapshot;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

@Internal
public class GraphModelCheckpointExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(GraphModelCheckpointExecutor.class);

    /**
     * Which {@code buildExecutionPlan} form the skeleton uses. {@link #THREAD_UNALIGNED_CONFIG}
     * is the 6-arg build threading {@code isUnalignedCheckpointEnabled()}/
     * {@code getUnalignedThreshold()} into the plan; {@link #LEGACY_NO_UNALIGNED} is the
     * 4-arg build without unaligned passthrough (JobGraph entry — pinned asymmetry).
     */
    private enum PlanBuildMode {
        THREAD_UNALIGNED_CONFIG, LEGACY_NO_UNALIGNED
    }

    public static StreamExecutionResult executeWithCheckpoint(
            JobGraph jobGraph,
            String jobName,
            CheckpointConfig checkpointConfig) throws Exception {
        // JobGraph entry: config id values used verbatim (no
        // partitioned-plan defaults — asymmetric with the StreamModel entries,
        // pinned). No fingerprint source; restore without a StreamModel;
        // 4-arg plan build without unaligned passthrough (pinned asymmetry — see
        // buildExecutionPlan selection in the skeleton).
        gateTwoPhaseRequiresConfig(jobGraph, checkpointConfig);
        return executeWithCheckpointSkeleton(jobGraph, jobName, checkpointConfig,
                resolveJobId(checkpointConfig), resolvePipelineId(checkpointConfig),
                null, PlanBuildMode.LEGACY_NO_UNALIGNED, null);
    }

    /**
     * Executes with checkpoint support using PartitionedPlan and DeploymentPlan.
     * This is the new execution path called from StreamExecutionEnvironment
     * when checkpointing is enabled.
     */
    public static StreamExecutionResult executeWithCheckpoint(
            StreamModel streamModel,
            PartitionedPlan partitionedPlan,
            DeploymentPlan deploymentPlan) throws Exception {
        JobGraph jobGraph = buildJobGraphFromStreamModel(streamModel);
        String jobName = partitionedPlan.getJobId() != null ? partitionedPlan.getJobId() : "Streaming Job";
        String jobId = partitionedPlan.getJobId() != null ? partitionedPlan.getJobId() : "job-0";
        String pipelineId = partitionedPlan.getPipelineId() != null ? partitionedPlan.getPipelineId() : "pipeline-0";

        CheckpointConfig checkpointConfig = new CheckpointConfig();
        checkpointConfig.setCheckpointEnabled(true);
        checkpointConfig.setJobId(jobId);
        checkpointConfig.setPipelineId(pipelineId);

        return executeWithCheckpointSkeleton(jobGraph, jobName, checkpointConfig,
                jobId, pipelineId, deploymentPlan, PlanBuildMode.THREAD_UNALIGNED_CONFIG, streamModel);
    }

    public static StreamExecutionResult executeWithCheckpoint(
            StreamModel streamModel,
            PartitionedPlan partitionedPlan,
            DeploymentPlan deploymentPlan,
            CheckpointConfig userConfig) throws Exception {
        JobGraph jobGraph = buildJobGraphFromStreamModel(streamModel);
        String jobName = partitionedPlan.getJobId() != null ? partitionedPlan.getJobId() : "Streaming Job";
        String jobId = partitionedPlan.getJobId() != null ? partitionedPlan.getJobId() : "job-0";
        String pipelineId = partitionedPlan.getPipelineId() != null ? partitionedPlan.getPipelineId() : "pipeline-0";

        // User-config merge: user values win, missing ids fall back to the
        // partitioned-plan defaults above.
        CheckpointConfig checkpointConfig;
        if (userConfig != null) {
            checkpointConfig = userConfig;
            checkpointConfig.setCheckpointEnabled(true);
            if (checkpointConfig.getJobId() == null) {
                checkpointConfig.setJobId(jobId);
            }
            if (checkpointConfig.getPipelineId() == null) {
                checkpointConfig.setPipelineId(pipelineId);
            }
        } else {
            checkpointConfig = new CheckpointConfig();
            checkpointConfig.setCheckpointEnabled(true);
            checkpointConfig.setJobId(jobId);
            checkpointConfig.setPipelineId(pipelineId);
        }

        return executeWithCheckpointSkeleton(jobGraph, jobName, checkpointConfig,
                jobId, pipelineId, deploymentPlan, PlanBuildMode.THREAD_UNALIGNED_CONFIG, streamModel);
    }

    /**
     * Converged skeleton for the three public
     * {@code executeWithCheckpoint} overloads (build plan →
     * coordinator → register → scheduler → restore → submit →
     * finally(shutdown + closeBufferPool)). The
     * entries only perform their id/config resolution and delegate here. The complete
     * difference set is the explicit parameter list:
     * <ul>
     *   <li>id resolution — done by the entries (verbatim config values for the
     *       JobGraph entry; partitioned-plan defaults for the StreamModel entries);</li>
     *   <li>fingerprint + restore model — keyed on {@code streamModel != null}
     *       (StreamModel entries set {@code computeFingerprint()} and restore with the
     *       model; the JobGraph entry does neither — pinned asymmetry, preserved);</li>
     *   <li>plan build form — {@link PlanBuildMode#LEGACY_NO_UNALIGNED} uses the 4-arg
     *       build which does NOT thread unaligned-checkpoint config (pinned asymmetry
     *       preserved); {@link PlanBuildMode#THREAD_UNALIGNED_CONFIG} uses the 6-arg build
     *       threading {@code isUnalignedCheckpointEnabled()}/{@code getUnalignedThreshold()};</li>
     *   <li>validate/resolve order — validate-then-resolve for every entry.</li>
     * </ul>
     */
    private static StreamExecutionResult executeWithCheckpointSkeleton(
            JobGraph jobGraph,
            String jobName,
            CheckpointConfig checkpointConfig,
            String jobId,
            String pipelineId,
            DeploymentPlan deploymentPlan,
            PlanBuildMode planBuildMode,
            StreamModel streamModel) throws Exception {
        long startTime = CoreMetrics.currentTimeMillis();

        gateTwoPhaseRequiresConfig(jobGraph, checkpointConfig);
        checkpointConfig.validateUnalignedConfig();
        boolean barrierAlignment = resolveBarrierAlignment(checkpointConfig);
        boolean threadUnalignedConfig = (planBuildMode == PlanBuildMode.THREAD_UNALIGNED_CONFIG);
        GraphExecutionPlan execPlan = threadUnalignedConfig
                ? buildExecutionPlan(jobGraph, deploymentPlan, barrierAlignment, checkpointConfig)
                : buildExecutionPlan(jobGraph, barrierAlignment, checkpointConfig.getBarrierAlignmentTimeout());

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        ICheckpointStorage storage = createStorage(checkpointConfig);
        CheckpointPlan checkpointPlan = CheckpointPlanBuilder.build(execPlan, jobId, pipelineId, null, checkpointConfig);

        CheckpointCoordinator coordinator = createCoordinator(jobId, pipelineId, idCounter, storage, checkpointConfig, jobGraph);

        // StreamModel entries: compute and set fingerprint for EpochManifest persistence.
        if (streamModel != null) {
            StreamModelFingerprint fingerprint = streamModel.computeFingerprint();
            coordinator.setCurrentFingerprint(fingerprint);
        }

        List<StreamTaskInvokable> allInvokables = registerTasksAndTrackers(execPlan, checkpointPlan, coordinator, checkpointConfig);

        ScheduledExecutorService barrierScheduler = startBarrierScheduler(allInvokables, coordinator, checkpointConfig, jobId);

        // Restore parameter: the JobGraph entry restores without a StreamModel.
        // Only an explicitly-configured storage path participates in
        // auto-restore; the default machine-level directory always starts fresh.
        if (hasExplicitStoragePath(checkpointConfig)) {
            restoreFromCheckpoint(execPlan, coordinator, checkpointPlan, streamModel);
        } else {
            LOG.warn("Job {} uses the DEFAULT checkpoint storage directory (no 'path' storage"
                    + " property configured). Automatic restore is DISABLED for the default"
                    + " directory — leftover artifacts from failed/killed jobs cannot be"
                    + " identity-proven and must not be silently inherited (AR-1). Starting"
                    + " fresh. Configure checkpointConfig storageProperty('path', ...) to"
                    + " enable cross-run recovery.", jobId);
        }

        Map<String, SubtaskTask> tasks = buildTasks(execPlan);
        TaskExecutor executor = new TaskExecutor();
        AtomicBoolean abortMarked = registerLocalAbortHandler(coordinator, tasks);

        try {
            submitAndRun(execPlan, tasks, executor, jobGraph, coordinator, checkpointPlan,
                    allInvokables, checkpointConfig,
                    checkpointConfig.getMaxRestartsPerRegion());
            checkAbortMarker(abortMarked);
            handleJobTermination(allInvokables, coordinator, checkpointConfig);
            checkTaskFailures(tasks);

            logCheckpointMetrics(coordinator);

            long executionTime = CoreMetrics.currentTimeMillis() - startTime;
            return new StreamExecutionResult(jobName, executionTime);
        } finally {
            shutdownAndReleasePool(barrierScheduler, coordinator, executor, execPlan);
        }
    }

    private static JobGraph buildJobGraphFromStreamModel(StreamModel streamModel) {
        io.nop.stream.core.graph.StreamGraphGenerator graphGenerator = new io.nop.stream.core.graph.StreamGraphGenerator();

        java.util.List<io.nop.stream.core.transformation.Transformation<?>> sinkList = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, io.nop.stream.core.transformation.Transformation<?>> entry
                : streamModel.getTransformations().entrySet()) {
            io.nop.stream.core.transformation.Transformation<?> t = entry.getValue();
            if (t instanceof io.nop.stream.core.transformation.SinkTransformation) {
                sinkList.add(t);
            }
        }

        io.nop.stream.core.graph.StreamGraph streamGraph = graphGenerator.generate(sinkList);
        io.nop.stream.core.jobgraph.JobGraphGenerator jobGraphGenerator = new io.nop.stream.core.jobgraph.JobGraphGenerator();
        return jobGraphGenerator.generate(streamGraph);
    }

    /**
     * Shared runtime state of the two savepoint entries
     * ({@link #triggerSavepoint}/{@link #executeWithSavepoint}), built by
     * {@link #prepareSavepointRuntime}.
     */
    private static final class SavepointRuntime {
        final GraphExecutionPlan execPlan;
        final ICheckpointStorage storage;
        final CheckpointPlan checkpointPlan;
        final CheckpointCoordinator coordinator;
        final List<StreamTaskInvokable> allInvokables;
        final ScheduledExecutorService barrierScheduler;
        final Map<String, SubtaskTask> tasks;
        final TaskExecutor executor;
        final AtomicBoolean abortMarked;

        SavepointRuntime(GraphExecutionPlan execPlan, ICheckpointStorage storage,
                         CheckpointPlan checkpointPlan, CheckpointCoordinator coordinator,
                         List<StreamTaskInvokable> allInvokables,
                         ScheduledExecutorService barrierScheduler,
                         Map<String, SubtaskTask> tasks, TaskExecutor executor,
                         AtomicBoolean abortMarked) {
            this.execPlan = execPlan;
            this.storage = storage;
            this.checkpointPlan = checkpointPlan;
            this.coordinator = coordinator;
            this.allInvokables = allInvokables;
            this.barrierScheduler = barrierScheduler;
            this.tasks = tasks;
            this.executor = executor;
            this.abortMarked = abortMarked;
        }
    }

    /**
     * Shared prologue of the two savepoint entries (validate config →
     * legacy-form plan build → id resolution → storage/plan/coordinator → task
     * registration → barrier scheduler → optional savepoint restore → local
     * tasks + abort handler). Deliberately NOT routed through
     * {@link #executeWithCheckpointSkeleton}: the savepoint entries diverge
     * from it at three pinned points (storage override, savepoint-path restore
     * instead of manifest auto-restore, no fingerprint handling), so folding
     * them in would turn those into behavioral hooks.
     *
     * @param overrideStoragePath replaces the configured storage BEFORE the
     *                            checkpoint plan is built ({@code triggerSavepoint}'s
     *                            targetPath); null/blank = keep configured storage
     * @param restoreSavepointPath restores operator state AFTER the barrier
     *                            scheduler is started ({@code executeWithSavepoint}'s
     *                            savepointPath); null/blank = no restore
     */
    private static SavepointRuntime prepareSavepointRuntime(
            JobGraph jobGraph, CheckpointConfig checkpointConfig,
            String overrideStoragePath, String restoreSavepointPath) throws Exception {
        checkpointConfig.validateUnalignedConfig();
        boolean barrierAlignment = resolveBarrierAlignment(checkpointConfig);
        GraphExecutionPlan execPlan = buildExecutionPlan(jobGraph, barrierAlignment, checkpointConfig.getBarrierAlignmentTimeout());
        gateTwoPhaseRequiresConfig(jobGraph, checkpointConfig);
        String jobId = resolveJobId(checkpointConfig);
        String pipelineId = resolvePipelineId(checkpointConfig);

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        ICheckpointStorage storage = createStorage(checkpointConfig);
        if (overrideStoragePath != null && !overrideStoragePath.isEmpty()) {
            storage = new LocalFileCheckpointStorage(overrideStoragePath);
        }
        CheckpointPlan checkpointPlan = CheckpointPlanBuilder.build(execPlan, jobId, pipelineId, null, checkpointConfig);

        CheckpointCoordinator coordinator = createCoordinator(jobId, pipelineId, idCounter, storage, checkpointConfig, jobGraph);
        List<StreamTaskInvokable> allInvokables = registerTasksAndTrackers(execPlan, checkpointPlan, coordinator, checkpointConfig);

        ScheduledExecutorService barrierScheduler = startBarrierScheduler(allInvokables, coordinator, checkpointConfig, jobId);

        if (restoreSavepointPath != null && !restoreSavepointPath.isEmpty()) {
            restoreFromSavepointPath(execPlan, storage, checkpointPlan, restoreSavepointPath);
        }

        Map<String, SubtaskTask> tasks = buildTasks(execPlan);
        TaskExecutor executor = new TaskExecutor();
        AtomicBoolean abortMarked = registerLocalAbortHandler(coordinator, tasks);

        return new SavepointRuntime(execPlan, storage, checkpointPlan, coordinator,
                allInvokables, barrierScheduler, tasks, executor, abortMarked);
    }

    /**
     * Joint finally-body of the checkpoint/savepoint entries: executor +
     * barrier-scheduler + coordinator shutdown, then release the per-job
     * buffer pool so any producer blocked on global exhaustion is woken. On a
     * recovery attempt a fresh plan (and fresh pool) is built; closing the
     * prior pool avoids leaked permits from the failed attempt starving the
     * new one.
     */
    private static void shutdownAndReleasePool(ScheduledExecutorService barrierScheduler,
                                               CheckpointCoordinator coordinator,
                                               TaskExecutor executor,
                                               GraphExecutionPlan execPlan) {
        shutdown(barrierScheduler, coordinator, executor);
        execPlan.closeBufferPool();
    }

    public static String triggerSavepoint(
            JobGraph jobGraph,
            CheckpointConfig checkpointConfig,
             String targetPath) throws Exception {

        SavepointRuntime rt = prepareSavepointRuntime(jobGraph, checkpointConfig, targetPath, null);
        try {
            submitAndRun(rt.execPlan, rt.tasks, rt.executor, jobGraph, rt.coordinator, rt.checkpointPlan,
                    rt.allInvokables, checkpointConfig,
                    checkpointConfig.getMaxRestartsPerRegion());
            checkAbortMarker(rt.abortMarked);

            // C2 (plan 369): bounded-retry trigger. A collision with an in-flight
            // periodic checkpoint (maxConcurrent=1) no longer silently skips the
            // savepoint — the trigger retries within the checkpoint timeout. A
            // NO_TASKS_TO_ACK rejection short-circuits as success (null return:
            // with the C3 participant contraction every task is terminal, so
            // there is nothing to snapshot). A final trigger failure throws
            // (loud) instead of returning a null savepoint path.
            PendingCheckpoint savepointPending = rt.coordinator.triggerCheckpointBounded(
                    CheckpointType.SAVEPOINT, checkpointConfig.getCheckpointTimeout());
            String savepointPath = null;
            if (savepointPending != null) {
                // C2×C3: an all-terminal epoch is completed inline by the
                // coordinator from inherited states — no live invokable left to
                // fan a barrier into; the fan-out itself is contained, but
                // skipping it for an already-done epoch avoids misleading
                // per-task barrier warnings.
                if (!savepointPending.getCompletableFuture().isDone()) {
                    triggerBarrierOnAllInvokables(rt.allInvokables, savepointPending);
                } else {
                    LOG.info("Savepoint {} assembled from terminal states (no live participants); "
                            + "barrier fan-out skipped", savepointPending.getCheckpointId());
                }

                CompletedCheckpoint completed = (CompletedCheckpoint) savepointPending.getCompletableFuture()
                        .get(checkpointConfig.getCheckpointTimeout(), TimeUnit.MILLISECONDS);
                if (completed != null) {
                    // Materialize per-subtask KeyGroupRange ownership so the
                    // savepoint records which subtask owned which range (the restore path
                    // can then route keyed state on a parallelism change).
                    materializeKeyGroupOwnership(completed, rt.execPlan);
                    savepointPath = rt.storage.storeCheckPoint(completed);
                }
            }

            checkTaskFailures(rt.tasks);
            return savepointPath;
        } finally {
            shutdownAndReleasePool(rt.barrierScheduler, rt.coordinator, rt.executor, rt.execPlan);
        }
    }

    public static StreamExecutionResult executeWithSavepoint(
            JobGraph jobGraph,
            String jobName,
            CheckpointConfig checkpointConfig,
            String savepointPath) throws Exception {

        long startTime = CoreMetrics.currentTimeMillis();

        SavepointRuntime rt = prepareSavepointRuntime(jobGraph, checkpointConfig, null, savepointPath);
        try {
            submitAndRun(rt.execPlan, rt.tasks, rt.executor, jobGraph, rt.coordinator, rt.checkpointPlan,
                    rt.allInvokables, checkpointConfig,
                    checkpointConfig.getMaxRestartsPerRegion());
            checkAbortMarker(rt.abortMarked);
            triggerFinalCheckpoint(rt.allInvokables, rt.coordinator, checkpointConfig);
            checkTaskFailures(rt.tasks);

            long executionTime = CoreMetrics.currentTimeMillis() - startTime;
            return new StreamExecutionResult(jobName, executionTime);
        } finally {
            shutdownAndReleasePool(rt.barrierScheduler, rt.coordinator, rt.executor, rt.execPlan);
        }
    }

    /**
     * Handles job termination based on the configured JobTerminationMode.
     * <ul>
     *   <li>CANCEL - default, triggers COMPLETED_POINT_TYPE final checkpoint</li>
     *   <li>DRAIN - triggers TERMINAL_SAVEPOINT, waits for all in-flight data</li>
     *   <li>SUSPEND - triggers SAVEPOINT, then stops sources</li>
     * </ul>
     */
    private static void handleJobTermination(
            List<StreamTaskInvokable> allInvokables,
            CheckpointCoordinator coordinator,
            CheckpointConfig config) throws Exception {

        JobTerminationMode mode = config.getJobTerminationMode();
        if (mode == null) {
            mode = JobTerminationMode.CANCEL;
        }

        switch (mode) {
            case DRAIN:
                LOG.info("Job termination mode: DRAIN - triggering terminal savepoint");
                triggerTerminalSavepoint(allInvokables, coordinator, config, CheckpointType.TERMINAL_SAVEPOINT);
                break;
            case SUSPEND:
                LOG.info("Job termination mode: SUSPEND - triggering terminal savepoint then stopping sources");
                // CheckpointType per checkpoint-design.md §7.3
                // (TERMINAL_SAVEPOINT for DRAIN/SUSPEND), consistent with
                // JobCoordinator.terminateSuspend().
                triggerTerminalSavepoint(allInvokables, coordinator, config, CheckpointType.TERMINAL_SAVEPOINT);
                stopSources(allInvokables);
                break;
            case CANCEL:
            default:
                triggerFinalCheckpoint(allInvokables, coordinator, config);
                break;
        }
    }

    /**
     * Triggers a terminal savepoint (for DRAIN or SUSPEND mode).
     * Waits for the savepoint to complete within the configured timeout.
     */
    private static void triggerTerminalSavepoint(
            List<StreamTaskInvokable> allInvokables,
            CheckpointCoordinator coordinator,
            CheckpointConfig config,
            CheckpointType checkpointType) {

        if (allInvokables.isEmpty()) {
            return;
        }
        try {
            // C2 (plan 369): bounded-retry trigger with typed-reason disposition
            // (see CheckpointCoordinator.triggerCheckpointBounded). A collision
            // with an in-flight periodic checkpoint retries instead of silently
            // skipping the terminal savepoint (DRAIN/SUSPEND must not complete a
            // job without its durable savepoint); NO_TASKS_TO_ACK short-circuits
            // as success; the retry budget exhausting throws (job fail, loud).
            PendingCheckpoint terminalPending = coordinator.triggerCheckpointBounded(
                    checkpointType, config.getCheckpointTimeout());
            if (terminalPending != null) {
                // C2×C3: an already-done epoch (assembled from terminal states)
                // needs no barrier fan-out — see triggerSavepoint.
                if (!terminalPending.getCompletableFuture().isDone()) {
                    triggerBarrierOnAllInvokables(allInvokables, terminalPending);
                } else {
                    LOG.info("Terminal savepoint {} assembled from terminal states "
                            + "(no live participants); barrier fan-out skipped",
                            terminalPending.getCheckpointId());
                }

                // Wait for terminal savepoint completion
                Object result = terminalPending.getCompletableFuture()
                        .get(config.getCheckpointTimeout(), TimeUnit.MILLISECONDS);
                if (result != null) {
                    LOG.info("Terminal savepoint completed: checkpointId={}",
                            terminalPending.getCheckpointId());
                }
            }
        } catch (InterruptedException e) {
            // C2 (plan 369): keep the interrupt cancellable — restore the flag
            // and fail the terminal savepoint loudly (the caller's shutdown /
            // cancel path re-observes the interrupt on its next blocking call).
            Thread.currentThread().interrupt();
            LOG.error("Interrupted while waiting for terminal savepoint of type {}", checkpointType, e);
            throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_SAVEPOINT_FAILED, e);
        } catch (Exception e) {
            LOG.error("Failed to trigger terminal savepoint", e);
            throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_SAVEPOINT_FAILED, e);
        }
    }

    /**
     * Stops source tasks by closing their input/output.
     * Used in SUSPEND mode after savepoint is taken.
     */
    private static void stopSources(List<StreamTaskInvokable> allInvokables) {
        for (StreamTaskInvokable invokable : allInvokables) {
            try {
                // Close the output writer to stop data flow from sources
                if (invokable.getOutputWriter() != null) {
                    invokable.getOutputWriter().close();
                }
            } catch (Exception e) {
                LOG.error("Failed to stop source invokable", e);
            }
        }
    }

    private static GraphExecutionPlan buildExecutionPlan(JobGraph jobGraph) {
        return GraphExecutionPlan.build(jobGraph);
    }

    private static GraphExecutionPlan buildExecutionPlan(JobGraph jobGraph, boolean barrierAlignment) {
        return GraphExecutionPlan.build(jobGraph, null, barrierAlignment);
    }

    private static GraphExecutionPlan buildExecutionPlan(JobGraph jobGraph, boolean barrierAlignment,
                                                          long barrierAlignmentTimeout) {
        return GraphExecutionPlan.build(jobGraph, null, barrierAlignment, barrierAlignmentTimeout);
    }

    /**
     * Unaligned checkpoint: build with aligned→unaligned fallback
     * config threaded from {@link CheckpointConfig}. The caller MUST have invoked
     * {@code CheckpointConfig.validateUnalignedConfig()} first.
     */
    private static GraphExecutionPlan buildExecutionPlan(JobGraph jobGraph, DeploymentPlan deploymentPlan,
                                                          boolean barrierAlignment,
                                                          CheckpointConfig checkpointConfig) {
        return GraphExecutionPlan.build(jobGraph, deploymentPlan, barrierAlignment,
                checkpointConfig.getBarrierAlignmentTimeout(),
                checkpointConfig.isUnalignedCheckpointEnabled(),
                checkpointConfig.getUnalignedThreshold());
    }

    private static GraphExecutionPlan buildExecutionPlan(JobGraph jobGraph, DeploymentPlan deploymentPlan,
                                                          boolean barrierAlignment, long barrierAlignmentTimeout) {
        return GraphExecutionPlan.build(jobGraph, deploymentPlan, barrierAlignment, barrierAlignmentTimeout);
    }

    private static boolean resolveBarrierAlignment(CheckpointConfig config) {
        return config.getProcessingGuarantee().isBarrierAlignment();
    }

    private static String resolveJobId(CheckpointConfig config) {
        // The config-supplied jobId gets the same sanitization as job names so a
        // user-set id with unsafe characters cannot fail LocalFileCheckpointStorage's
        // validateId at store time. Null passes through (callers fall back to defaults).
        return StorageJobIds.sanitizeJobId(config.getJobId());
    }

    private static String resolvePipelineId(CheckpointConfig config) {
        return config.getPipelineId();
    }

    private static CheckpointCoordinator createCoordinator(
            String jobId, String pipelineId,
            CheckpointIDCounter idCounter, ICheckpointStorage storage,
            CheckpointConfig config) {
        return createCoordinator(jobId, pipelineId, idCounter, storage, config, null);
    }

    /**
     * Creates the coordinator AND, when {@code jobGraph} is supplied, scans
     * its vertices for {@code SourceReaderOperator} heads and registers each source-api
     * vertex id via {@link CheckpointCoordinator#registerSourceEnumeratorVertex(int)} so
     * that {@code buildEpochManifest} snapshots enumerator state into the
     * {@code sourceEnumeratorSnapshots} manifest section on every checkpoint.
     *
     * <p>Without this registration, the manifest section is always empty — enumerator state
     * (discovered/assigned/finished splits) is lost on restore, and the source would
     * re-read already-finished splits.
     */
    private static CheckpointCoordinator createCoordinator(
            String jobId, String pipelineId,
            CheckpointIDCounter idCounter, ICheckpointStorage storage,
            CheckpointConfig config, JobGraph jobGraph) {
        CheckpointCoordinator coordinator = new CheckpointCoordinator(jobId, pipelineId, idCounter, storage, config);
        if (jobGraph != null) {
            registerSourceApiVertices(coordinator, jobGraph);
        }
        return coordinator;
    }

    /**
     * Walks the JobGraph's operator chains and registers any vertex whose
     * head operator is a {@code SourceReaderOperator} (FLIP-27 source-api path). The
     * vertex id is parsed from the {@code "vertex-<id>"} format used by
     * {@code JobGraphGenerator}.
     */
    private static void registerSourceApiVertices(CheckpointCoordinator coordinator, JobGraph jobGraph) {
        for (JobVertex vertex : jobGraph.getVertices().values()) {
            int vertexId = parseVertexIdForSourceEnumerator(vertex.getId());
            if (vertexId < 0) {
                continue;
            }
            boolean hasSourceReaderHead = false;
            if (vertex.getOperatorChains() != null) {
                outer:
                for (io.nop.stream.core.jobgraph.OperatorChain chain : vertex.getOperatorChains()) {
                    if (chain.getOperators() == null || chain.getOperators().isEmpty()) {
                        continue;
                    }
                    io.nop.stream.core.operators.StreamOperator<?> head = chain.getOperators().get(0);
                    if (head instanceof io.nop.stream.core.operators.SourceReaderOperator) {
                        hasSourceReaderHead = true;
                        break outer;
                    }
                }
            }
            if (hasSourceReaderHead) {
                coordinator.registerSourceEnumeratorVertex(vertexId);
            }
        }
    }

    /** Parses {@code "vertex-<id>"} back to int; returns -1 on failure. */
    private static int parseVertexIdForSourceEnumerator(String vertexId) {
        if (vertexId == null) return -1;
        String numPart = vertexId.startsWith("vertex-") ? vertexId.substring("vertex-".length()) : vertexId;
        try {
            return Integer.parseInt(numPart);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------
    // Delegates to TaskCheckpointWiring (extracted collaborator).
    // Package-private delegates keep the SupervisionLoop / focused-test
    // call sites and their signatures unchanged.
    // ------------------------------------------------------------------

    private static List<StreamTaskInvokable> registerTasksAndTrackers(
            GraphExecutionPlan execPlan,
            CheckpointPlan checkpointPlan,
            CheckpointCoordinator coordinator,
            CheckpointConfig checkpointConfig) {
        return TaskCheckpointWiring.registerTasksAndTrackers(execPlan, checkpointPlan, coordinator, checkpointConfig);
    }

    /** Package-private delegate — implementation in {@link TaskCheckpointWiring}. */
    static void wireTaskCheckpointPipeline(
            CheckpointCoordinator coordinator,
            CheckpointPlan checkpointPlan,
            CheckpointConfig checkpointConfig,
            StreamTaskInvokable invokable,
            TaskLocation taskLocation) {
        TaskCheckpointWiring.wireTaskCheckpointPipeline(coordinator, checkpointPlan, checkpointConfig, invokable, taskLocation);
    }

    /** Package-private delegate — implementation in {@link TaskCheckpointWiring}. */
    static void unwireTaskCheckpointPipeline(CheckpointCoordinator coordinator, StreamTaskInvokable invokable) {
        TaskCheckpointWiring.unwireTaskCheckpointPipeline(coordinator, invokable);
    }

    /** Package-private delegate — implementation in {@link TaskCheckpointWiring}. */
    static TaskLocation findTaskLocationInPlan(CheckpointPlan plan, String vertexId, int taskIndex) {
        return TaskCheckpointWiring.findTaskLocationInPlan(plan, vertexId, taskIndex);
    }




    private static ScheduledExecutorService startBarrierScheduler(
            List<StreamTaskInvokable> allInvokables,
            CheckpointCoordinator coordinator,
            CheckpointConfig config,
            String jobId) {
        return TaskCheckpointWiring.startBarrierScheduler(allInvokables, coordinator, config, jobId);
    }

    private static void triggerBarrierOnAllInvokables(
            List<StreamTaskInvokable> allInvokables, PendingCheckpoint pending) throws Exception {
        TaskCheckpointWiring.triggerBarrierOnAllInvokables(allInvokables, pending);
    }

    /**
     * CANCEL-mode COMPLETED_POINT_TYPE final checkpoint trigger.
     *
     * <p><b>C2 (plan 369) semantics change</b>: this trigger used to be
     * best-effort (a collision with an in-flight periodic checkpoint returned
     * null and the failure was logged while the job finished). The plan 369
     * adjudication closes that gap: the trigger now uses
     * {@link CheckpointCoordinator#triggerCheckpointBounded} — a collision with
     * in-flight periodic work is retried within the checkpoint timeout,
     * NO_TASKS_TO_ACK short-circuits as success (with the C3 participant
     * contraction, an empty ACK set at CANCEL means every task is terminal —
     * nothing to snapshot), and a final trigger failure throws loud (job fail)
     * instead of being swallowed. Completion of the triggered checkpoint itself
     * remains asynchronous (barrier fanned out; ACKs drain before the caller's
     * shutdown).
     */
    private static void triggerFinalCheckpoint(
            List<StreamTaskInvokable> allInvokables, CheckpointCoordinator coordinator,
            CheckpointConfig config) throws Exception {
        if (allInvokables.isEmpty()) {
            return;
        }
        PendingCheckpoint finalPending = coordinator.triggerCheckpointBounded(
                CheckpointType.COMPLETED_POINT_TYPE, config.getCheckpointTimeout());
        if (finalPending != null) {
            // C2×C3: an already-done epoch (assembled from terminal states)
            // needs no barrier fan-out — see triggerSavepoint.
            if (!finalPending.getCompletableFuture().isDone()) {
                triggerBarrierOnAllInvokables(allInvokables, finalPending);
            } else {
                LOG.info("Final checkpoint {} assembled from terminal states "
                        + "(no live participants); barrier fan-out skipped",
                        finalPending.getCheckpointId());
            }
        }
    }

    private static Map<String, SubtaskTask> buildTasks(GraphExecutionPlan execPlan) {
        // Concurrent map: the local abort handler iterates this map on the
        // checkpoint-timeout scheduler thread while the supervision thread
        // structurally mutates it during region restart (tasks.put) — a
        // LinkedHashMap iteration there can throw CME and leave the abort
        // partially applied. Iteration order is not semantically relied upon
        // (submission, failure-scan and abort are all order-agnostic).
        Map<String, SubtaskTask> tasks = new ConcurrentHashMap<>();
        for (String vertexId : execPlan.getSortedVertexIds()) {
            JobVertex vertex = execPlan.getExecutionVertices().get(vertexId);
            for (Subtask subtask : execPlan.getSubtasks(vertexId)) {
                String taskKey = vertexId + "-" + subtask.getTaskIndex();
                OperatorChain chain = subtask.getInvokable().getOperatorChain();
                List<OperatorChain> chainList = java.util.Collections.singletonList(chain);
                tasks.put(taskKey, new SubtaskTask(subtask, vertex, chainList));
            }
        }
        return tasks;
    }

    /**
     * Submits all tasks and runs the supervision loop
     * (mid-execution failure detection + region-scoped restart).
     *
     * <p>The supervision loop submits all tasks, polls for FAILED tasks at a
     * fixed interval, and on detecting a failure attempts a region-scoped
     * restart (consumer-only regions with materialization replay). For
     * single-region jobs (no materialization), the loop surfaces the first
     * failure immediately.
     *
     * <p>The retained {@link #checkTaskFailures} call-sites (5 in total) serve
     * as <strong>post-completion terminal verification</strong>: after the
     * supervision loop exits (all tasks terminal), they re-scan for any FAILED
     * task that the loop's in-flight restart path may have surfaced. The two
     * mechanisms coexist — supervision loop owns mid-execution detection;
     * checkTaskFailures owns terminal-state consistency.
     *
     * <p>{@code maxRestartsPerRegion} is threaded from
     * {@link CheckpointConfig#getMaxRestartsPerRegion()} at each call-site so
     * the per-region restart budget is production-configurable (default
     * {@code CheckpointConfig.DEFAULT_MAX_RESTARTS_PER_REGION = 3}). Wiring:
     * config → executeWithCheckpoint → submitAndRun → SupervisionLoop.run
     * (package-private full-parameter signature).
     */
    /**
     * Submits all tasks and runs the supervision loop (mid-execution failure
     * detection + region-scoped restart).
     *
     * <p>{@code allInvokables} (the barrier-scheduler injection list) and
     * {@code checkpointConfig} are threaded into the supervision loop so a
     * region-restarted task can be re-wired into the checkpoint pipeline: the
     * old invokable is replaced in the injection list, a fresh tracker +
     * listener/participant registrations are installed, and the configured
     * state backend is re-provisioned.
     */
    private static void submitAndRun(GraphExecutionPlan execPlan, Map<String, SubtaskTask> tasks,
                                     TaskExecutor executor, JobGraph jobGraph,
                                     CheckpointCoordinator coordinator,
                                     CheckpointPlan checkpointPlan,
                                     List<StreamTaskInvokable> allInvokables,
                                     CheckpointConfig checkpointConfig,
                                     int maxRestartsPerRegion) throws InterruptedException {
        SupervisionLoop.run(execPlan, tasks, executor, jobGraph, coordinator, checkpointPlan,
                allInvokables, checkpointConfig,
                maxRestartsPerRegion, SupervisionLoop.DEFAULT_POLL_INTERVAL_MS);
    }

    private static void checkTaskFailures(Map<String, SubtaskTask> tasks) {
        for (SubtaskTask task : tasks.values()) {
            if (task.getState() == SubtaskTask.State.FAILED) {
                throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_EXECUTE_FAILED, task.getError());
            }
        }
    }


    /**
     * Package-private so the single-input abort
     * wiring e2e can register the REAL production handler — the test must exercise
     * the production abort chain, not a hand-copied handler body.
     * Delegate — implementation in {@link TaskCheckpointWiring}.
     */
    static AtomicBoolean registerLocalAbortHandler(
            CheckpointCoordinator coordinator,
            Map<String, SubtaskTask> tasks) {
        return TaskCheckpointWiring.registerLocalAbortHandler(coordinator, tasks);
    }

    private static void checkAbortMarker(AtomicBoolean abortMarked) {
        if (abortMarked != null && abortMarked.get()) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_ABORTED).param(ARG_REASON,
                    "Checkpoint was aborted (timeout or explicit abort), job entering failure/recovery state. " +
                    "handleJobTermination final checkpoint is skipped.");
        }
    }


    private static void shutdown(ScheduledExecutorService barrierScheduler, CheckpointCoordinator coordinator, TaskExecutor executor) {
        TaskCheckpointWiring.shutdown(barrierScheduler, coordinator, executor);
    }

    private static void logCheckpointMetrics(CheckpointCoordinator coordinator) {
        CheckpointMetricsSnapshot snap = coordinator.getMetrics().snapshot();
        if (snap.getNumCompletedCheckpoints() == 0 && snap.getNumFailedCheckpoints() == 0) {
            return;
        }
        LOG.info("Checkpoint metrics: completed={}, failed={}, aborted={}, " +
                        "latestDurationMs={}, latestStateSize={}, totalStateSize={}",
                snap.getNumCompletedCheckpoints(),
                snap.getNumFailedCheckpoints(),
                snap.getNumAbortedCheckpoints(),
                snap.getLatestCheckpointDuration(),
                snap.getLatestCheckpointSize(),
                snap.getTotalStateSize());
    }

    static ICheckpointStorage createStorage(CheckpointConfig config) {
        String storageType = config.getStorageType();
        if ("jdbc".equalsIgnoreCase(storageType)) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_FAILED)
                    .param(ARG_DETAIL,
                            "JdbcCheckpointStorage requires IJdbcTemplate configuration. " +
                            "Use storageType='local' or provide JDBC configuration.");
        }
        if (storageType == null || !"local".equalsIgnoreCase(storageType)) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_FAILED)
                    .param(ARG_DETAIL, "Unknown storage type: " + storageType);
        }
        String basePath = config.getStorageProperty("path");
        if (basePath == null || basePath.isEmpty()) {
            basePath = defaultStorageBaseDir();
        }
        return new LocalFileCheckpointStorage(basePath);
    }

    /**
     * Default (no {@code path} storage property configured) checkpoint base directory.
     * Resolution order: the {@code nop-stream.checkpoint.storage.dir} system property
     * (deployment/ops override, also used by tests to avoid machine-level pollution),
     * then {@code ${java.io.tmpdir}/nop-stream-checkpoints}.
     */
    static String defaultStorageBaseDir() {
        String override = System.getProperty("nop-stream.checkpoint.storage.dir");
        if (override != null && !override.isBlank()) {
            return override;
        }
        return System.getProperty("java.io.tmpdir") + "/nop-stream-checkpoints";
    }

    /**
     * Whether the user explicitly configured the checkpoint storage path.
     * An explicit path is an explicit recovery intent — manifest-first auto-restore
     * (with the fingerprint guard) runs. The default machine-level directory gets NO
     * auto-restore: leftovers from failed/killed jobs there can never be identity-proven
     * (CompletedCheckpoint rows carry no fingerprint), so restoring from them would be
     * a silent-inheritance vector. Fresh start + WARN instead.
     */
    static boolean hasExplicitStoragePath(CheckpointConfig config) {
        String basePath = config.getStorageProperty("path");
        return basePath != null && !basePath.isEmpty();
    }

    private static void restoreFromCheckpoint(
            GraphExecutionPlan execPlan,
            CheckpointCoordinator coordinator,
            CheckpointPlan checkpointPlan,
            StreamModel streamModel) throws Exception {

        EpochManifest epochManifest = coordinator.restoreLatestEpochManifest();
        if (epochManifest != null) {
            LOG.info("Recovering from EpochManifest epoch {} (jobId={})",
                    epochManifest.getEpochId(), epochManifest.getJobId());

            // Advance the checkpoint id counter past the restored epoch so
            // the next triggered checkpoint produces a strictly greater epoch id
            // (monotonic-only advance, identical semantics to restoreFromCheckpoint).
            // Without this, new checkpoints would land in the shadow window [0, R)
            // below the restored epoch and be unobservable on the next crash.
            coordinator.advanceCheckpointIdCounterAfterRestore(epochManifest.getEpochId());

            validateFingerprintCompatibility(epochManifest, streamModel, coordinator);

            // Pass the checkpoint's TaskLocation set so the shared restore
            // path can perform the reverse-direction vertex differential check.
            Set<TaskLocation> checkpointLocations = epochManifest.getTaskSnapshots().keySet();
            restoreTaskStatesFromSource(execPlan, checkpointPlan, epochManifest.getEpochId(),
                    checkpointLocations,
                    (taskLocation) -> {
                        TaskStateSnapshot state = epochManifest.getTaskSnapshots().get(taskLocation);
                        if (state == null) {
                            throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED)
                                    .param(ARG_VERTEX_ID, taskLocation.getVertexId())
                                    .param(ARG_TASK_INDEX, taskLocation.getTaskIndex())
                                    .param(ARG_TASK_LOCATION, taskLocation)
                                    .param(ARG_EPOCH_ID, epochManifest.getEpochId())
                                    .param(ARG_DETAIL, "Available keys: " + epochManifest.getTaskSnapshots().keySet());
                        }
                        return state;
                    });
            return;
        }

        CompletedCheckpoint latestCheckpoint = coordinator.restoreFromCheckpoint();
        if (latestCheckpoint == null) {
            LOG.info("No recoverable checkpoint found, starting fresh");
            return;
        }

        LOG.info("Recovering from checkpoint {} (jobId={})",
                latestCheckpoint.getCheckpointId(), latestCheckpoint.getJobId());

        restoreTaskStatesFromCheckpoint(execPlan, checkpointPlan, latestCheckpoint);
    }

    /**
     * Fingerprint compatibility check for the
     * remote-deploy path, which has the fingerprint directly (from the JobGraph's
     * StreamModel) and no coordinator instance. Delegates to the shared
     * comparison logic.
     */
    static void validateFingerprintCompatibility(EpochManifest epochManifest,
                                                 StreamModelFingerprint currentFingerprint) {
        StreamModelFingerprint storedFingerprint = epochManifest.getStreamModelFingerprint();
        if (storedFingerprint == null) {
            LOG.info("No fingerprint in EpochManifest epoch={}, skipping compatibility check",
                    epochManifest.getEpochId());
            return;
        }

        if (currentFingerprint == null) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED)
                    .param(ARG_DETAIL, "EpochManifest epoch=" + epochManifest.getEpochId()
                            + " requires a fingerprint compatibility check, but the deployment "
                            + "descriptor carries no StreamModel fingerprint. Refusing to restore "
                            + "a possibly topology-incompatible checkpoint (fingerprint fast-fail "
                            + "policy, checkpoint-design.md section \"fingerprint-compare-and-fail-fast\").");
        }

        if (!currentFingerprint.isCompatibleWith(storedFingerprint)) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED)
                    .param(ARG_DETAIL, "StreamModel fingerprint incompatible on restore. stored=" + storedFingerprint + ", current=" + currentFingerprint);
        }

        LOG.info("Fingerprint compatibility check passed for epoch {}",
                epochManifest.getEpochId());
    }

    /**
     * Adapts the coordinator-based entry to the fingerprint-direct entry.
     * Public because focused tests exercise the fail-fast branches directly.
     */
    public static void validateFingerprintCompatibility(
            EpochManifest epochManifest, StreamModel streamModel, CheckpointCoordinator coordinator) {
        StreamModelFingerprint current;
        if (streamModel != null) {
            current = streamModel.computeFingerprint();
        } else if (coordinator != null && coordinator.getCurrentFingerprint() != null) {
            current = coordinator.getCurrentFingerprint();
        } else {
            // Fail-fast, not a warn-skip: the manifest carries a fingerprint (written
            // by a StreamModel-based run) but this execution path (the JobGraph-only
            // entry) has no fingerprint source, so compatibility cannot be proven.
            // Silently skipping here would let a topology-incompatible restore
            // through — violating the fingerprint fast-fail policy (checkpoint-design
            // §"fingerprint compare and fail fast"). Restore via the StreamModel-based
            // executeWithCheckpoint entry to keep the check enforced.
            current = null;
        }
        validateFingerprintCompatibility(epochManifest, current);
    }

    private static void restoreFromSavepointPath(
            GraphExecutionPlan execPlan,
            ICheckpointStorage defaultStorage,
            CheckpointPlan checkpointPlan,
            String savepointPath) throws Exception {

        ICheckpointStorage savepointStorage = new LocalFileCheckpointStorage(savepointPath);
        String jobId = checkpointPlan.getJobId();
        String pipelineId = checkpointPlan.getPipelineId();
        CompletedCheckpoint savepointCheckpoint = savepointStorage.getLatestCheckpoint(jobId, pipelineId);

        if (savepointCheckpoint == null) {
            savepointCheckpoint = savepointStorage.loadSavepoint(savepointPath);
        }

        if (savepointCheckpoint == null) {
            java.nio.file.Path path = java.nio.file.Paths.get(savepointPath);
            if (java.nio.file.Files.exists(path) && savepointPath.endsWith(".checkpoint")) {
                java.nio.file.Path parentDir = path.getParent();
                String parentPath = parentDir != null ? parentDir.toString() : savepointPath;
                ICheckpointStorage parentStorage = new LocalFileCheckpointStorage(parentPath);
                savepointCheckpoint = parentStorage.getLatestCheckpoint(jobId, pipelineId);
            }
        }

        if (savepointCheckpoint == null) {
            // R5-ST-07: an EXPLICIT savepoint request that misses all three lookup
            // tiers must fail loudly, not silently start fresh — a wrong path or a
            // stale jobId would otherwise boot a stateful job from empty state
            // (windows/CEP reset, 2PC pending commits lost). Enumerate every tier
            // searched so the operator can correct the misconfiguration directly.
            String tier1 = savepointPath + " [latest checkpoint for jobId=" + jobId
                    + ", pipelineId=" + pipelineId + "]";
            String tier2 = savepointPath + " [direct savepoint file load]";
            String tier3 = tier1 + " via parent directory of the .checkpoint file";
            throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED)
                    .param(ARG_DETAIL, "No recoverable savepoint found for the explicitly requested"
                            + " savepoint path. Lookups performed (all missed): (1) " + tier1
                            + "; (2) " + tier2 + "; (3) " + tier3
                            + ". Refusing to start fresh from empty state.");
        }

        LOG.info("Recovering from savepoint {} (jobId={})",
                savepointCheckpoint.getCheckpointId(), savepointCheckpoint.getJobId());

        restoreTaskStatesFromCheckpoint(execPlan, checkpointPlan, savepointCheckpoint);
    }

    @FunctionalInterface
    interface TaskStateLookup {
        TaskStateSnapshot lookup(TaskLocation taskLocation) throws Exception;
    }

    /**
     * Restores ONE deployed subtask's
     * operator state from a shared {@code LocalFileCheckpointStorage} directory.
     * This is the remote-deploy recovery entry: a TaskManager whose
     * {@code TaskDeploymentDescriptor} carries a {@code checkpointRestorePath}
     * calls this during {@code deployTask}, BEFORE the invokable starts running,
     * so the subtask resumes from the latest durable epoch (manifest-first, raw
     * checkpoint fallback — same preference order as the LOCAL
     * {@code restoreFromCheckpoint}).
     *
     * <p>Restore-time parallelism rescale is honored: when the manifest's subtask
     * set for a keyed vertex has a different parallelism than the current plan,
     * keyed state is routed by KeyGroupRange intersection.
     *
     * <p>A missing/null restore path or an empty storage is a fresh start (logged,
     * not an error). A present-but-incompatible manifest fails fast (fingerprint
     * policy identical to the LOCAL path).
     *
     * @param execPlan           the locally-built full execution plan (all subtasks; mirrors the global topology)
     * @param checkpointPlan     the checkpoint plan built from {@code execPlan} (after backend provisioning)
     * @param checkpointBaseDir  shared checkpoint storage directory; null/blank = fresh start
     * @param jobId              job id (must match the coordinator's TaskLocation family)
     * @param pipelineId         pipeline id (must match the coordinator's)
     * @param vertexId           the deployed subtask's vertex
     * @param subtaskIndex       the deployed subtask's index
     * @param currentFingerprint the current pipeline's fingerprint (from the JobGraph's StreamModel); may be null
     *                           only when the stored manifest carries none
     * @return the restored epoch id, or -1 when no durable state existed (fresh start)
     */
    public static long restoreDeployedSubtaskFromStorage(
            GraphExecutionPlan execPlan,
            CheckpointPlan checkpointPlan,
            String checkpointBaseDir,
            String jobId,
            String pipelineId,
            String vertexId,
            int subtaskIndex,
            StreamModelFingerprint currentFingerprint) throws Exception {
        if (checkpointBaseDir == null || checkpointBaseDir.isBlank()) {
            LOG.info("deployTask restore: no checkpointRestorePath for {}/{} — fresh start", vertexId, subtaskIndex);
            freshInitializeSubtaskOperators(execPlan, vertexId, subtaskIndex);
            return -1L;
        }
        io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage storage =
                new io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage(checkpointBaseDir);

        EpochManifest manifest = storage.loadLatestEpochManifest(jobId, pipelineId);
        if (manifest != null) {
            LOG.info("deployTask restore: recovering {}/{} from EpochManifest epoch {} (jobId={})",
                    vertexId, subtaskIndex, manifest.getEpochId(), manifest.getJobId());
            validateFingerprintCompatibility(manifest, currentFingerprint);
            Set<TaskLocation> checkpointLocations = manifest.getTaskSnapshots().keySet();
            restoreTaskStatesFromSource(execPlan, checkpointPlan, manifest.getEpochId(),
                    checkpointLocations,
                    (taskLocation) -> {
                        TaskStateSnapshot state = manifest.getTaskSnapshots().get(taskLocation);
                        if (state == null) {
                            throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED)
                                    .param(ARG_VERTEX_ID, taskLocation.getVertexId())
                                    .param(ARG_TASK_INDEX, taskLocation.getTaskIndex())
                                    .param(ARG_TASK_LOCATION, taskLocation)
                                    .param(ARG_EPOCH_ID, manifest.getEpochId())
                                    .param(ARG_DETAIL, "Available keys: " + manifest.getTaskSnapshots().keySet());
                        }
                        return state;
                    },
                    vertexId, subtaskIndex);
            return manifest.getEpochId();
        }

        CompletedCheckpoint latest = storage.getLatestCheckpoint(jobId, pipelineId);
        if (latest == null) {
            LOG.info("deployTask restore: no durable checkpoint found for job {} at {} — fresh start",
                    jobId, checkpointBaseDir);
            freshInitializeSubtaskOperators(execPlan, vertexId, subtaskIndex);
            return -1L;
        }

        LOG.info("deployTask restore: recovering {}/{} from checkpoint {} (jobId={})",
                vertexId, subtaskIndex, latest.getCheckpointId(), latest.getJobId());
        Set<TaskLocation> checkpointLocations = latest.getTaskStates().keySet();
        restoreTaskStatesFromSource(execPlan, checkpointPlan, latest.getCheckpointId(),
                checkpointLocations,
                (taskLocation) -> {
                    TaskStateSnapshot state = latest.getTaskState(taskLocation);
                    if (state == null) {
                        throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED)
                                .param(ARG_VERTEX_ID, taskLocation.getVertexId())
                                .param(ARG_TASK_INDEX, taskLocation.getTaskIndex())
                                .param(ARG_TASK_LOCATION, taskLocation)
                                .param(ARG_CHECKPOINT_ID, latest.getCheckpointId())
                                .param(ARG_EPOCH_ID, latest.getCheckpointId())
                                .param(ARG_DETAIL, "Available keys: " + latest.getTaskStates().keySet());
                    }
                    return state;
                },
                vertexId, subtaskIndex);
        return latest.getCheckpointId();
    }

    /**
     * Fresh-start state
     * initialization for the remote-deploy path. A fresh subtask never flows
     * through {@code restoreOperatorsFromState} (no durable state); without this
     * hook, operators
     * implementing {@link io.nop.stream.core.common.functions.ICheckpointedFunction}
     * would never see {@code initializeState} on the remote path — e.g. the CDC
     * source function's offset store would stay {@code transient null} and its
     * {@code snapshotState} would persist an EMPTY {@code cdc-offsets} map,
     * replaying the whole stream from position 0 against restored downstream
     * state. Calling
     * {@code restoreState(null)} mirrors the LOCAL empty-restore semantics:
     * {@code AbstractStreamOperator.restoreState(null)} propagates
     * {@code initializeState(null)}, which is the documented fresh-start hook
     * ({@code StreamSourceOperator.restoreState} always initializes its
     * {@code CheckpointedSourceFunction} — empty snapshot or not).
     */
    static void freshInitializeSubtaskOperators(
            GraphExecutionPlan execPlan, String vertexId, int subtaskIndex) throws Exception {
        java.util.List<Subtask> subtasks = execPlan.getSubtasks(vertexId);
        if (subtasks == null) {
            return;
        }
        for (Subtask subtask : subtasks) {
            if (subtask.getTaskIndex() != subtaskIndex) {
                continue;
            }
            StreamTaskInvokable invokable = subtask.getInvokable();
            if (invokable == null || invokable.getOperatorChain() == null) {
                continue;
            }
            for (StreamOperator<?> op : invokable.getOperatorChain().getOperators()) {
                if (op instanceof AbstractStreamOperator) {
                    ((AbstractStreamOperator<?>) op).restoreState(null);
                    LOG.debug("Fresh-start initializeState applied to operator {} of {}/{}",
                            op.getClass().getSimpleName(), vertexId, subtaskIndex);
                }
            }
        }
    }

    // Package-private: invoked by RescaleStateAssembler (checkpoint-plane entry).
    static void restoreTaskStatesFromSource(
            GraphExecutionPlan execPlan,
            CheckpointPlan checkpointPlan,
            long epochId,
            Set<TaskLocation> checkpointLocations,
            TaskStateLookup stateLookup) throws Exception {
        restoreTaskStatesFromSource(execPlan, checkpointPlan, epochId, checkpointLocations,
                stateLookup, null, -1);
    }

    /**
     * Full restore path with an optional
     * subtask filter. When {@code targetVertexId} is non-null, ONLY that
     * (vertex, subtaskIndex) is restored — the remote-deploy path uses this so a
     * TaskManager restores exactly the subtask it is about to run (a full-plan
     * restore on every TM would also restore foreign subtasks, driving redundant
     * sink re-commits that only the ledger/manifest idempotency guards would
     * absorb). The reverse-direction vertex differential check still covers the
     * WHOLE plan (the local plan mirrors the global topology).
     */
    static void restoreTaskStatesFromSource(
            GraphExecutionPlan execPlan,
            CheckpointPlan checkpointPlan,
            long epochId,
            Set<TaskLocation> checkpointLocations,
            TaskStateLookup stateLookup,
            String targetVertexId,
            int targetSubtaskIndex) throws Exception {

        // Reverse-direction vertex differential check. The forward
        // direction (current vertex absent from checkpoint) is already rejected
        // below via stateLookup.lookup throwing. The reverse direction — a
        // stateful vertex present in the checkpoint but absent from the current
        // graph — must be rejected as well. Per checkpoint-design.md §8.6 the
        // safe default is to reject such a restore (it indicates a stateful
        // vertex was deleted).
        validateReverseVertexDifferential(execPlan, checkpointPlan, checkpointLocations);

        // Group the checkpoint's old subtasks by vertex so a rescale
        // (parallelism change) can route keyed state by KeyGroupRange
        // intersection instead of a strict 1:1 TaskLocation lookup.
        Map<String, List<TaskLocation>> oldSubtasksByVertex = groupCheckpointSubtasksByVertex(checkpointLocations);
        int maxParallelism = resolveMaxParallelism(execPlan, checkpointPlan);

        for (String vertexId : execPlan.getSortedVertexIds()) {
            List<Subtask> newSubtasks = execPlan.getSubtasks(vertexId);
            int newParallelism = newSubtasks.size();
            List<TaskLocation> oldSubtasks = oldSubtasksByVertex.getOrDefault(vertexId, java.util.Collections.emptyList());
            int oldParallelism = oldSubtasks.size();
            boolean vertexKeyed = isVertexKeyed(checkpointPlan, vertexId, oldSubtasks);
            boolean rescale = vertexKeyed && oldParallelism > 0 && oldParallelism != newParallelism;

            // (checkpoint-design.md §8.5.2): a 2PC sink vertex
            // cannot restore across a parallelism change. Operator state (the 2PC
            // pendingCommits) restores strictly 1:1 by subtask index — a scale-down
            // would silently drop the retired subtasks' durable-uncommitted pending
            // commits (§6.4 invariant violation) and a non-keyed scale-up has no
            // state-lookup path (generic failure, no mismatch semantics). Reject
            // typed BEFORE any per-subtask merge/lookup. The check deliberately does
            // NOT gate on `vertexKeyed`: the live `rescale` boolean is keyed-only,
            // and a 2PC sink vertex is typically non-keyed — both shapes are covered.
            // Same-parallelism recovery (kill/recover) is unaffected: oldP == newP
            // takes the regular 1:1 restore path below.
            if (oldParallelism > 0 && oldParallelism != newParallelism && isVertex2PcSink(newSubtasks)) {
                throw new StreamException(ERR_STREAM_2PC_SINK_PARALLELISM_CHANGE_UNSUPPORTED)
                        .param(ARG_VERTEX_ID, vertexId)
                        .param(ARG_OLD_PARALLELISM, oldParallelism)
                        .param(ARG_NEW_PARALLELISM, newParallelism);
            }

            if (rescale) {
                // Channel state (unaligned checkpoint in-flight data)
                // cannot be redistributed across a parallelism change in the first
                // version. Fail-fast here — at the rescale detection point, before
                // any per-subtask merge — rather than relying on the downstream
                // instanceof TaskEpochSnapshot guard in restoreChannelStateIfPresent,
                // which silently drops channel state when buildRescaledTaskState
                // produces a plain TaskStateSnapshot (No-Silent-No-Op violation).
                assertNoChannelStateOnRescale(vertexId, oldSubtasks, newParallelism, oldParallelism, stateLookup);
                LOG.info("Stage 35 rescale detected for vertex {}: oldParallelism={} -> newParallelism={} "
                                + "(maxParallelism={}); routing keyed state by KeyGroupRange intersection",
                        vertexId, oldParallelism, newParallelism, maxParallelism);
            }

            for (Subtask subtask : newSubtasks) {
                // Subtask filter: skip subtasks the caller is not
                // restoring (remote-deploy restores only its own subtask).
                if (targetVertexId != null
                        && !(targetVertexId.equals(vertexId) && subtask.getTaskIndex() == targetSubtaskIndex)) {
                    continue;
                }

                StreamTaskInvokable invokable = subtask.getInvokable();
                if (invokable == null) continue;

                int taskIndex = subtask.getTaskIndex();
                TaskLocation taskLocation = findTaskLocationInPlan(checkpointPlan, vertexId, taskIndex);

                TaskStateSnapshot taskState;
                if (rescale) {
                    KeyGroupRange newRange = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(
                            maxParallelism, newParallelism, taskIndex);
                    taskState = buildRescaledTaskState(vertexId, taskIndex, newRange, oldSubtasks,
                            newParallelism, oldParallelism, maxParallelism, stateLookup, checkpointPlan);
                } else {
                    taskState = stateLookup.lookup(taskLocation);
                }

                List<OperatorStateMapping> mappings = checkpointPlan.getStateMappings(taskLocation);
                restoreOperatorsFromState(invokable.getOperatorChain(), epochId, taskState, mappings);

                // Unaligned checkpoint recovery: AFTER operator state
                // restore and BEFORE the task starts reading, inject the captured
                // in-flight channel records into the invokable's InputGate so they
                // are replayed ahead of any new upstream records. Aligned-checkpoint
                // snapshots have no channel state (null) → no-op.
                restoreChannelStateIfPresent(invokable, taskState);
            }
        }
    }

    /**
     * Injects unaligned-checkpoint channel state into a recovered
     * task's {@link InputGate}. No-op when the snapshot has no channel state
     * (aligned checkpoints) or the task has no InputGate (source/self-contained).
     */
    private static void restoreChannelStateIfPresent(StreamTaskInvokable invokable,
                                                      TaskStateSnapshot taskState) {
        if (taskState instanceof TaskEpochSnapshot) {
            io.nop.stream.core.checkpoint.ChannelState cs =
                    ((TaskEpochSnapshot) taskState).getChannelState();
            if (cs != null && !cs.isEmpty()) {
                InputGate inputGate = invokable.getInputGate();
                if (inputGate != null) {
                    inputGate.restoreChannelState(cs);
                }
            }
        }
    }

    /**
     * Group the checkpoint's old TaskLocations by vertexId, each list sorted by
     * taskIndex ascending. Used to enumerate the old subtask set per vertex and
     * derive the old parallelism on a rescale.
     */
    private static Map<String, List<TaskLocation>> groupCheckpointSubtasksByVertex(Set<TaskLocation> locations) {
        Map<String, List<TaskLocation>> byVertex = new LinkedHashMap<>();
        if (locations == null) return byVertex;
        for (TaskLocation loc : locations) {
            if (loc == null || loc.getVertexId() == null) continue;
            byVertex.computeIfAbsent(loc.getVertexId(), k -> new ArrayList<>()).add(loc);
        }
        for (List<TaskLocation> list : byVertex.values()) {
            list.sort(java.util.Comparator.comparingInt(TaskLocation::getTaskIndex));
        }
        return byVertex;
    }

    /**
     * Resolve the job-global maxParallelism. It is constant for the job lifetime
     * (only parallelism changes on rescale), so any keyed backend's value is
     * authoritative. Falls back to {@link KeyGroup#DEFAULT_MAX_PARALLELISM} when
     * no keyed backend is reachable from the execution plan.
     */
    // Package-private: invoked by RescaleStateAssembler (max-parallelism resolution stays on the host).
    static int resolveMaxParallelism(GraphExecutionPlan execPlan, CheckpointPlan checkpointPlan) {
        for (String vertexId : execPlan.getSortedVertexIds()) {
            for (Subtask subtask : execPlan.getSubtasks(vertexId)) {
                StreamTaskInvokable invokable = subtask.getInvokable();
                if (invokable == null) continue;
                OperatorChain chain = invokable.getOperatorChain();
                if (chain == null) continue;
                for (StreamOperator<?> op : chain.getOperators()) {
                    if (op instanceof AbstractStreamOperator) {
                        io.nop.stream.core.common.state.backend.IKeyedStateBackend<?> keyed =
                                ((AbstractStreamOperator<?>) op).getKeyedStateBackend();
                        if (keyed != null) {
                            return keyed.getMaxParallelism();
                        }
                    }
                }
            }
        }
        return KeyGroup.DEFAULT_MAX_PARALLELISM;
    }

    /**
     * @return {@code true} if any subtask of this vertex holds a
     * {@code TwoPhaseCommitSinkFunction} UDF in its operator chain (the sink
     * vertex shape the D1 cross-parallelism restore rejection protects,
     * checkpoint-design.md §8.5.2).
     */
    private static boolean isVertex2PcSink(List<Subtask> sampleSubtasks) {
        for (Subtask subtask : sampleSubtasks) {
            StreamTaskInvokable invokable = subtask.getInvokable();
            if (invokable == null || invokable.getOperatorChain() == null) {
                continue;
            }
            for (StreamOperator<?> op : invokable.getOperatorChain().getOperators()) {
                if (op instanceof AbstractUdfStreamOperator) {
                    if (((AbstractUdfStreamOperator<?, ?>) op).getUserFunction()
                            instanceof TwoPhaseCommitSinkFunction) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * @return {@code true} if any operator mapping of this vertex carries keyed
     * state (i.e. the vertex needs KeyGroupRange routing on a rescale).
     */
    private static boolean isVertexKeyed(CheckpointPlan plan, String vertexId, List<TaskLocation> sampleSubtasks) {
        for (TaskLocation loc : sampleSubtasks) {
            for (OperatorStateMapping m : plan.getStateMappings(loc)) {
                if (m.hasKeyedState()) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Delegates to RescaleStateAssembler (extracted collaborator).
    // Package-private delegates keep the SupervisionLoop / focused-test
    // call sites and their signatures unchanged.
    // ------------------------------------------------------------------

    /** Package-private delegate — implementation in {@link RescaleStateAssembler}. */
    static void assertNoChannelStateOnRescale(
            String vertexId, List<TaskLocation> oldSubtasks,
            int newParallelism, int oldParallelism, TaskStateLookup stateLookup) throws Exception {
        RescaleStateAssembler.assertNoChannelStateOnRescale(vertexId, oldSubtasks, newParallelism, oldParallelism, stateLookup);
    }

    private static TaskStateSnapshot buildRescaledTaskState(
            String vertexId, int taskIndex, KeyGroupRange newRange,
            List<TaskLocation> oldSubtasks, int newParallelism, int oldParallelism,
            int maxParallelism, TaskStateLookup stateLookup, CheckpointPlan checkpointPlan) throws Exception {
        return RescaleStateAssembler.buildRescaledTaskState(vertexId, taskIndex, newRange, oldSubtasks,
                newParallelism, oldParallelism, maxParallelism, stateLookup, checkpointPlan);
    }

    /** Package-private delegate — implementation in {@link RescaleStateAssembler}. */
    static void materializeKeyGroupOwnership(CompletedCheckpoint checkpoint, GraphExecutionPlan execPlan) {
        RescaleStateAssembler.materializeKeyGroupOwnership(checkpoint, execPlan);
    }

    /** Package-private delegate — implementation in {@link RescaleStateAssembler}. */
    static void validateReverseVertexDifferential(
            GraphExecutionPlan execPlan,
            CheckpointPlan checkpointPlan,
            Set<TaskLocation> checkpointLocations) {
        RescaleStateAssembler.validateReverseVertexDifferential(execPlan, checkpointPlan, checkpointLocations);
    }

    private static void restoreTaskStatesFromCheckpoint(
            GraphExecutionPlan execPlan,
            CheckpointPlan checkpointPlan,
            CompletedCheckpoint checkpoint) throws Exception {
        RescaleStateAssembler.restoreTaskStatesFromCheckpoint(execPlan, checkpointPlan, checkpoint);
    }

    /** Package-private delegate — implementation in {@link RescaleStateAssembler}. */
    static void restoreOperatorsFromState(
            OperatorChain chain,
            long epochId,
            TaskStateSnapshot taskState,
            List<OperatorStateMapping> mappings) throws Exception {
        RescaleStateAssembler.restoreOperatorsFromState(chain, epochId, taskState, mappings);
    }

    /** Package-private delegate — implementation in {@link RescaleStateAssembler}. */
    static OperatorSnapshotResult buildSnapshotFromTaskState(
            TaskStateSnapshot taskState,
            int operatorIndex,
            List<OperatorStateMapping> mappings) {
        return RescaleStateAssembler.buildSnapshotFromTaskState(taskState, operatorIndex, mappings);
    }


    /**
     * WI21 gate B fallback: vertex-chain scan for a 2PC sink (same discovery rule
     * as {@code CheckpointPlanBuilder}'s operator marking) — used when the job
     * graph carries no attached StreamModel.
     */
    /**
     * WI21 gate B (§八 12): runtime-side requirement re-validation — the
     * requirements ride the job graph's attached model (JobGraphGenerator
     * population); if absent (JobGraph-only entry), fall back to scanning the
     * vertex chain for a TwoPhaseCommitSinkFunction (same discovery rule as
     * CheckpointPlanBuilder's 2PC marking). Every runtime checkpoint-coordinated
     * entry (execute / savepoint) calls this BEFORE any config dereference.
     */
    private static void gateTwoPhaseRequiresConfig(JobGraph jobGraph, CheckpointConfig checkpointConfig) {
        boolean twoPhaseRequirement = false;
        if (jobGraph.getStreamModel() != null) {
            twoPhaseRequirement = jobGraph.getStreamModel().getRequirements()
                    .contains(StreamRequirement.TWO_PHASE_COMMIT_SINK);
        }
        if (!twoPhaseRequirement) {
            twoPhaseRequirement = hasTwoPhaseCommitSinkOnGraph(jobGraph);
        }
        if (twoPhaseRequirement && checkpointConfig == null) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                    "job declares a TwoPhaseCommitSinkFunction sink but reached the runtime "
                            + "checkpoint path without a CheckpointConfig — 2PC prepare/commit cannot "
                            + "run without checkpointing");
        }
    }

    private static boolean hasTwoPhaseCommitSinkOnGraph(JobGraph jobGraph) {
        if (jobGraph.getVertices() == null) {
            return false;
        }
        for (JobVertex vertex : jobGraph.getVertices().values()) {
            if (vertex.getOperatorChains() == null) {
                continue;
            }
            for (OperatorChain chain : vertex.getOperatorChains()) {
                if (chain == null || chain.getOperators() == null) {
                    continue;
                }
                for (StreamOperator<?> op : chain.getOperators()) {
                    if (op instanceof AbstractUdfStreamOperator
                            && ((AbstractUdfStreamOperator<?, ?>) op).getUserFunction()
                                    instanceof TwoPhaseCommitSinkFunction) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
