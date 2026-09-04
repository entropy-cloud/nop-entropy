/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_CHECKPOINT_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_CHECKPOINT_VERTEX_IDS;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_CURRENT_VERTEX_IDS;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
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
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.shard.KeyGroup;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
import io.nop.stream.core.common.state.shard.KeyGroupRange;
import io.nop.stream.core.common.state.shard.KeyGroupRangeRestoreFilter;
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

    public static StreamExecutionResult executeWithCheckpoint(
            JobGraph jobGraph,
            String jobName,
            CheckpointConfig checkpointConfig) throws Exception {
        // ① JobGraph-entry id resolution: config values used verbatim (no
        // partitioned-plan defaults — asymmetric with the StreamModel entries, pinned).
        // ② no fingerprint source, ④ restore without a StreamModel,
        // ③ 4-arg plan build without unaligned passthrough (pinned asymmetry — see
        // buildExecutionPlan selection in the skeleton).
        return executeWithCheckpointSkeleton(jobGraph, jobName, checkpointConfig,
                resolveJobId(checkpointConfig), resolvePipelineId(checkpointConfig),
                null, false, null);
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
                jobId, pipelineId, deploymentPlan, true, streamModel);
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

        // ① user-config merge: user values win, missing ids fall back to the
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
                jobId, pipelineId, deploymentPlan, true, streamModel);
    }

    /**
     * Phase 3 (Plan 2026-09-03-1951-1, F-B): converged skeleton for the three public
     * {@code executeWithCheckpoint} overloads (~85-90% literal clone: build plan →
     * coordinator → register → scheduler → restore → submit → finally(shutdown +
     * closeBufferPool)). The public signatures are unchanged (callers untouched); the
     * entries only perform their ① id/config resolution and delegate here. The complete
     * difference set is the explicit parameter list (behavior pinned before/after by
     * {@code TestGraphModelCheckpointExecutorEntryPinning}):
     * <ul>
     *   <li>① id resolution — done by the entries (verbatim config values for the
     *       JobGraph entry; partitioned-plan defaults for the StreamModel entries);</li>
     *   <li>② fingerprint + ④ restore model — keyed on {@code streamModel != null}
     *       (StreamModel entries set {@code computeFingerprint()} and restore with the
     *       model; the JobGraph entry does neither — pinned asymmetry, preserved);</li>
     *   <li>③ plan build form — {@code threadUnalignedConfig=false} uses the 4-arg
     *       build which does NOT thread unaligned-checkpoint config (pinned asymmetry
     *       preserved); {@code true} uses the 6-arg build threading
     *       {@code isUnalignedCheckpointEnabled()}/{@code getUnalignedThreshold()};</li>
     *   <li>⑤ validate/resolve order — unified to validate-then-resolve (the former
     *       per-overload order difference was a pure, unobservable reordering of a
     *       throwing validation and a pure getter — the plan's only exempted
     *       "unification").</li>
     * </ul>
     */
    private static StreamExecutionResult executeWithCheckpointSkeleton(
            JobGraph jobGraph,
            String jobName,
            CheckpointConfig checkpointConfig,
            String jobId,
            String pipelineId,
            DeploymentPlan deploymentPlan,
            boolean threadUnalignedConfig,
            StreamModel streamModel) throws Exception {
        long startTime = System.currentTimeMillis();

        checkpointConfig.validateUnalignedConfig();
        boolean barrierAlignment = resolveBarrierAlignment(checkpointConfig);
        GraphExecutionPlan execPlan = threadUnalignedConfig
                ? buildExecutionPlan(jobGraph, deploymentPlan, barrierAlignment, checkpointConfig)
                : buildExecutionPlan(jobGraph, barrierAlignment, checkpointConfig.getBarrierAlignmentTimeout());

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        ICheckpointStorage storage = createStorage(checkpointConfig);
        CheckpointPlan checkpointPlan = CheckpointPlanBuilder.build(execPlan, jobId, pipelineId, null, checkpointConfig);

        CheckpointCoordinator coordinator = createCoordinator(jobId, pipelineId, idCounter, storage, checkpointConfig, jobGraph);

        // ② StreamModel entries: compute and set fingerprint for EpochManifest persistence.
        if (streamModel != null) {
            StreamModelFingerprint fingerprint = streamModel.computeFingerprint();
            coordinator.setCurrentFingerprint(fingerprint);
        }

        List<StreamTaskInvokable> allInvokables = registerTasksAndTrackers(execPlan, checkpointPlan, coordinator, checkpointConfig);

        ScheduledExecutorService barrierScheduler = startBarrierScheduler(allInvokables, coordinator, checkpointConfig, jobId);

        // ④ restore parameter: the JobGraph entry restores without a StreamModel.
        // D1 (AR-1): only an explicitly-configured storage path participates in
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

            long executionTime = System.currentTimeMillis() - startTime;
            return new StreamExecutionResult(jobName, executionTime);
        } finally {
            shutdown(barrierScheduler, coordinator, executor);
            // Release the per-job buffer pool so any producer blocked on global
            // exhaustion is woken. On a recovery attempt a fresh plan (and fresh
            // pool) is built; closing the prior pool avoids leaked permits from
            // the failed attempt starving the new one.
            execPlan.closeBufferPool();
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

    public static String triggerSavepoint(
            JobGraph jobGraph,
            CheckpointConfig checkpointConfig,
             String targetPath) throws Exception {

        checkpointConfig.validateUnalignedConfig();
        boolean barrierAlignment = resolveBarrierAlignment(checkpointConfig);
        GraphExecutionPlan execPlan = buildExecutionPlan(jobGraph, barrierAlignment, checkpointConfig.getBarrierAlignmentTimeout());
        String jobId = resolveJobId(checkpointConfig);
        String pipelineId = resolvePipelineId(checkpointConfig);

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        ICheckpointStorage storage = createStorage(checkpointConfig);
        if (targetPath != null && !targetPath.isEmpty()) {
            storage = new LocalFileCheckpointStorage(targetPath);
        }
        CheckpointPlan checkpointPlan = CheckpointPlanBuilder.build(execPlan, jobId, pipelineId, null, checkpointConfig);

        CheckpointCoordinator coordinator = createCoordinator(jobId, pipelineId, idCounter, storage, checkpointConfig, jobGraph);
        List<StreamTaskInvokable> allInvokables = registerTasksAndTrackers(execPlan, checkpointPlan, coordinator, checkpointConfig);

        ScheduledExecutorService barrierScheduler = startBarrierScheduler(allInvokables, coordinator, checkpointConfig, jobId);

        Map<String, SubtaskTask> tasks = buildTasks(execPlan);
        TaskExecutor executor = new TaskExecutor();
        AtomicBoolean abortMarked = registerLocalAbortHandler(coordinator, tasks);

        try {
            submitAndRun(execPlan, tasks, executor, jobGraph, coordinator, checkpointPlan,
                    allInvokables, checkpointConfig,
                    checkpointConfig.getMaxRestartsPerRegion());
            checkAbortMarker(abortMarked);

            PendingCheckpoint savepointPending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.SAVEPOINT);
            String savepointPath = null;
            if (savepointPending != null) {
                triggerBarrierOnAllInvokables(allInvokables, savepointPending);

                CompletedCheckpoint completed = (CompletedCheckpoint) savepointPending.getCompletableFuture()
                        .get(checkpointConfig.getCheckpointTimeout(), TimeUnit.MILLISECONDS);
                if (completed != null) {
                    // Stage 35: materialize per-subtask KeyGroupRange ownership so the
                    // savepoint records which subtask owned which range (the restore path
                    // can then route keyed state on a parallelism change).
                    materializeKeyGroupOwnership(completed, execPlan);
                    savepointPath = storage.storeCheckPoint(completed);
                }
            }

            checkTaskFailures(tasks);
            return savepointPath;
        } finally {
            shutdown(barrierScheduler, coordinator, executor);
            // Release the per-job buffer pool so any producer blocked on global
            // exhaustion is woken. On a recovery attempt a fresh plan (and fresh
            // pool) is built; closing the prior pool avoids leaked permits from
            // the failed attempt starving the new one.
            execPlan.closeBufferPool();
        }
    }

    public static StreamExecutionResult executeWithSavepoint(
            JobGraph jobGraph,
            String jobName,
            CheckpointConfig checkpointConfig,
            String savepointPath) throws Exception {

        long startTime = System.currentTimeMillis();

        checkpointConfig.validateUnalignedConfig();
        boolean barrierAlignment = resolveBarrierAlignment(checkpointConfig);
        GraphExecutionPlan execPlan = buildExecutionPlan(jobGraph, barrierAlignment, checkpointConfig.getBarrierAlignmentTimeout());
        String jobId = resolveJobId(checkpointConfig);
        String pipelineId = resolvePipelineId(checkpointConfig);

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        ICheckpointStorage storage = createStorage(checkpointConfig);
        CheckpointPlan checkpointPlan = CheckpointPlanBuilder.build(execPlan, jobId, pipelineId, null, checkpointConfig);

        CheckpointCoordinator coordinator = createCoordinator(jobId, pipelineId, idCounter, storage, checkpointConfig, jobGraph);
        List<StreamTaskInvokable> allInvokables = registerTasksAndTrackers(execPlan, checkpointPlan, coordinator, checkpointConfig);

        ScheduledExecutorService barrierScheduler = startBarrierScheduler(allInvokables, coordinator, checkpointConfig, jobId);

        if (savepointPath != null && !savepointPath.isEmpty()) {
            restoreFromSavepointPath(execPlan, storage, checkpointPlan, savepointPath);
        }

        Map<String, SubtaskTask> tasks = buildTasks(execPlan);
        TaskExecutor executor = new TaskExecutor();
        AtomicBoolean abortMarked = registerLocalAbortHandler(coordinator, tasks);

        try {
            submitAndRun(execPlan, tasks, executor, jobGraph, coordinator, checkpointPlan,
                    allInvokables, checkpointConfig,
                    checkpointConfig.getMaxRestartsPerRegion());
            checkAbortMarker(abortMarked);
            triggerFinalCheckpoint(allInvokables, coordinator);
            checkTaskFailures(tasks);

            long executionTime = System.currentTimeMillis() - startTime;
            return new StreamExecutionResult(jobName, executionTime);
        } finally {
            shutdown(barrierScheduler, coordinator, executor);
            // Release the per-job buffer pool so any producer blocked on global
            // exhaustion is woken. On a recovery attempt a fresh plan (and fresh
            // pool) is built; closing the prior pool avoids leaked permits from
            // the failed attempt starving the new one.
            execPlan.closeBufferPool();
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
            CheckpointConfig config) {

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
                // Stage 28: CheckpointType aligned to checkpoint-design.md §7.3
                // (TERMINAL_SAVEPOINT for DRAIN/SUSPEND). The previous SAVEPOINT
                // was inconsistent with JobCoordinator.terminateSuspend() and
                // with the authoritative §7.3 table.
                triggerTerminalSavepoint(allInvokables, coordinator, config, CheckpointType.TERMINAL_SAVEPOINT);
                stopSources(allInvokables);
                break;
            case CANCEL:
            default:
                triggerFinalCheckpoint(allInvokables, coordinator);
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
            PendingCheckpoint terminalPending = coordinator.tryTriggerPendingCheckpoint(checkpointType);
            if (terminalPending != null) {
                triggerBarrierOnAllInvokables(allInvokables, terminalPending);

                // Wait for terminal savepoint completion
                Object result = terminalPending.getCompletableFuture()
                        .get(config.getCheckpointTimeout(), TimeUnit.MILLISECONDS);
                if (result != null) {
                    LOG.info("Terminal savepoint completed: checkpointId={}",
                            terminalPending.getCheckpointId());
                }
            }
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
     * Stage 43 (unaligned checkpoint): build with aligned→unaligned fallback
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
        // D1b: the config-supplied jobId gets the same sanitization as job names so a
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
     * Stage 49 D2: creates the coordinator AND, when {@code jobGraph} is supplied, scans
     * its vertices for {@code SourceReaderOperator} heads and registers each source-api
     * vertex id via {@link CheckpointCoordinator#registerSourceEnumeratorVertex(int)} so
     * that {@code buildEpochManifest} snapshots enumerator state into the
     * {@code sourceEnumeratorSnapshots} manifest section on every checkpoint.
     *
     * <p>Without this registration, the manifest section is always empty — enumerator state
     * (discovered/assigned/finished splits) is lost on restore, and the source would
     * re-read already-finished splits. Anti-Hollow fix.
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
     * Stage 49 D2: walks the JobGraph's operator chains and registers any vertex whose
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

    /**
     * Registers every task's checkpoint pipeline wiring (tracker + coordinator
     * task registration + listener/participant registration + state-backend
     * provisioning) and returns the thread-safe list of all invokables used by
     * the barrier scheduler.
     *
     * <p>P0-02: the returned list is a {@link CopyOnWriteArrayList} — the barrier
     * scheduler thread iterates it while the supervision thread replaces an
     * invokable during a region restart (remove old + add new). A plain
     * ArrayList would throw CME inside the scheduler's for-each, and the
     * {@link #startBarrierScheduler} catch would swallow it, silently dropping
     * that tick's barrier.
     */
    private static List<StreamTaskInvokable> registerTasksAndTrackers(
            GraphExecutionPlan execPlan,
            CheckpointPlan checkpointPlan,
            CheckpointCoordinator coordinator,
            CheckpointConfig checkpointConfig) {

        List<StreamTaskInvokable> allInvokables = new CopyOnWriteArrayList<>();

        // Item 16 (P-REQ-1 operator/io layers): inject per-task data-plane
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
     * <p>Extracted from {@link #registerTasksAndTrackers} so the region-restart
     * path ({@code SupervisionLoop.rebuildTask}, P0-02) reuses the exact same
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
     * region-restart path (P0-02) for the OLD (superseded) task's operators
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

    private static ScheduledExecutorService startBarrierScheduler(
            List<StreamTaskInvokable> allInvokables,
            CheckpointCoordinator coordinator,
            CheckpointConfig config,
            String jobId) {

        if (allInvokables.isEmpty()) {
            return null;
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "barrier-injector-" + jobId);
            t.setDaemon(true);
            return t;
        });

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

    private static void triggerBarrierOnAllInvokables(
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
     * CANCEL-mode terminal checkpoint: <b>best-effort by design</b> (intentional
     * asymmetry with {@link #triggerTerminalSavepoint}, which rethrows). CANCEL
     * means "stop now" — the user has already accepted state loss — so a failed
     * final checkpoint must not wedge the requested cancellation; the failure is
     * surfaced via {@code LOG.error} (observable, not swallowed) and the cancel
     * proceeds. DRAIN/SUSPEND instead promise a durable savepoint and therefore
     * fail fast.
     */
    private static void triggerFinalCheckpoint(
            List<StreamTaskInvokable> allInvokables, CheckpointCoordinator coordinator) {
        if (allInvokables.isEmpty()) {
            return;
        }
        try {
            PendingCheckpoint finalPending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.COMPLETED_POINT_TYPE);
            if (finalPending != null) {
                triggerBarrierOnAllInvokables(allInvokables, finalPending);
            }
        } catch (Exception e) {
            LOG.error("Failed to trigger final checkpoint (best-effort on CANCEL; continuing with cancellation)", e);
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
     * Stage 44 successor 3: submits all tasks and runs the supervision loop
     * (mid-execution failure detection + region-scoped restart). Replaces the
     * legacy {@code awaitCompletion} block-wait.
     *
     * <p>The supervision loop submits all tasks, polls for FAILED tasks at a
     * fixed interval, and on detecting a failure attempts a region-scoped
     * restart (consumer-only regions with materialization replay). For
     * single-region jobs (no materialization), the loop surfaces the first
     * failure immediately — equivalent to the legacy
     * {@code awaitCompletion} + {@code checkTaskFailures} path (zero regression).
     *
     * <p>The retained {@link #checkTaskFailures} call-sites (5 in total) serve
     * as <strong>post-completion terminal verification</strong>: after the
     * supervision loop exits (all tasks terminal), they re-scan for any FAILED
     * task that the loop's in-flight restart path may have surfaced. The two
     * mechanisms coexist — supervision loop owns mid-execution detection;
     * checkTaskFailures owns terminal-state consistency.
     *
     * <p>Stage 44 successor 5: {@code maxRestartsPerRegion} is threaded from
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
     * <p>P0-02: {@code allInvokables} (the barrier-scheduler injection list) and
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

    private static AtomicBoolean registerLocalAbortHandler(
            CheckpointCoordinator coordinator,
            Map<String, SubtaskTask> tasks) {
        AtomicBoolean abortMarked = new AtomicBoolean(false);
        coordinator.setAbortHandler(abortedCheckpointId -> {
            LOG.warn("Checkpoint {} aborted, applying epoch-precise abort to local tasks", abortedCheckpointId);
            boolean anyTaskStillHasInFlight = false;
            for (SubtaskTask task : tasks.values()) {
                // Notify barrier tracker to release ACK wait for THIS epoch only
                // (Stage 45 per-epoch tracking: other in-flight epochs are undisturbed).
                StreamTaskInvokable invokable = task.getSubtask().getInvokable();
                CheckpointBarrierTracker tracker = invokable.getBarrierTracker();
                if (tracker != null) {
                    tracker.notifyCheckpointAborted(abortedCheckpointId);
                }
                // Stage 45 / P1 hardening: release THIS epoch's InputGate alignment only (not
                // resumeConsumptionAll), so channels blocked by the aborted barrier are freed
                // while other epochs' alignment state is preserved. The InputGate's alignment
                // collections (inFlightAlignments / abortedBarriers / blockedChannels, plus the
                // per-BarrierAlignment channel sets) are concurrent-safe structures, so this
                // cross-thread call does NOT throw ConcurrentModificationException and does not
                // corrupt the task thread's in-progress barrier iteration. (Approach (a) —
                // mailbox delivery of the abort — was evaluated and rejected: InputGate.read()
                // blocks inside barrier alignment and only drains the mailbox at the caller's
                // (processInputGate) loop top, so a mailbox-delivered abort could not unblock
                // the read and would deadlock the epoch-precise abort until alignment timeout.)
                InputGate inputGate = invokable.getInputGate();
                if (inputGate != null) {
                    inputGate.abortBarrierAlignment(abortedCheckpointId);
                }
                // Stage 45 (design §2.8.1 D3): only cancel the task thread when no
                // other epoch is in-flight for it. If other epochs remain, the task
                // keeps running so they can still ACK/complete (epoch-precise abort).
                if (tracker != null && tracker.hasInFlightCheckpoints()) {
                    anyTaskStillHasInFlight = true;
                    LOG.debug("Task {} still has in-flight epoch(s) after abort of {}; not cancelling",
                            task, abortedCheckpointId);
                    continue;
                }
                // No other epochs in-flight → cooperative cancel + interrupt (legacy
                // sweep behavior for the single-in-flight case).
                invokable.getMailboxExecutor().signalCancel();
                if (inputGate != null) {
                    inputGate.resumeConsumptionAll();
                }
                task.cancel();
            }
            // Stage 45: only mark the job-wide abort flag when no task has remaining
            // in-flight epochs (i.e. this abort actually empties the pipeline). When
            // other epochs survive, the job is still healthy and the final-checkpoint
            // skip must not fire.
            if (!anyTaskStillHasInFlight) {
                abortMarked.set(true);
            }
        });
        return abortMarked;
    }

    private static void checkAbortMarker(AtomicBoolean abortMarked) {
        if (abortMarked != null && abortMarked.get()) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_ABORTED).param(ARG_REASON,
                    "Checkpoint was aborted (timeout or explicit abort), job entering failure/recovery state. " +
                    "handleJobTermination final checkpoint is skipped.");
        }
    }

    private static void shutdown(ScheduledExecutorService barrierScheduler, CheckpointCoordinator coordinator, TaskExecutor executor) {
        if (executor != null) {
            executor.shutdownNow();
        }
        if (barrierScheduler != null) {
            barrierScheduler.shutdownNow();
        }
        coordinator.shutdown();
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
     * D1 (AR-1): whether the user explicitly configured the checkpoint storage path.
     * An explicit path is an explicit recovery intent — manifest-first auto-restore
     * (with the fingerprint guard) runs. The default machine-level directory gets NO
     * auto-restore: leftovers from failed/killed jobs there can never be identity-proven
     * (CompletedCheckpoint rows carry no fingerprint), so restoring from them would be
     * exactly the AR-1 silent-inheritance vector. Fresh start + WARN instead.
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

            // P0-03: advance the checkpoint id counter past the restored epoch so
            // the next triggered checkpoint produces a strictly greater epoch id
            // (monotonic-only advance, identical semantics to restoreFromCheckpoint).
            // Without this, new checkpoints would land in the shadow window [0, R)
            // below the restored epoch and be unobservable on the next crash.
            coordinator.advanceCheckpointIdCounterAfterRestore(epochManifest.getEpochId());

            validateFingerprintCompatibility(epochManifest, streamModel, coordinator);

            // P0-7: pass the checkpoint's TaskLocation set so the shared restore
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
     * Item 14 (deploy-restore variant): fingerprint compatibility check for the
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
                            + "policy, checkpoint-design.md §\"指纹比对 + 快速失败策略\").");
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
            // §"指纹比对 + 快速失败策略"). Restore via the StreamModel-based
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
            LOG.info("No recoverable savepoint found at path {}, starting fresh", savepointPath);
            return;
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
     * Item 14 (composite-scenario distributed): restores ONE deployed subtask's
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
     * keyed state is routed by KeyGroupRange intersection (Stage 35 machinery).
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
     * Item 14 (composite-scenario distributed, defect fix): fresh-start state
     * initialization for the remote-deploy path. A fresh subtask never flows
     * through {@code restoreOperatorsFromState} (no durable state), so operators
     * implementing {@link io.nop.stream.core.common.functions.ICheckpointedFunction}
     * never received {@code initializeState} — e.g. the CDC source function's
     * offset store stayed {@code transient null}, its {@code snapshotState}
     * persisted an EMPTY {@code cdc-offsets} map at every epoch, and recovery
     * replayed the whole stream from position 0 against restored downstream
     * state (duplicate pattern matches, divergent outputs). Calling
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

    private static void restoreTaskStatesFromSource(
            GraphExecutionPlan execPlan,
            CheckpointPlan checkpointPlan,
            long epochId,
            Set<TaskLocation> checkpointLocations,
            TaskStateLookup stateLookup) throws Exception {
        restoreTaskStatesFromSource(execPlan, checkpointPlan, epochId, checkpointLocations,
                stateLookup, null, -1);
    }

    /**
     * Item 14 (composite-scenario distributed): full restore path with an optional
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

        // P0-7: reverse-direction vertex differential check. The forward
        // direction (current vertex absent from checkpoint) is already rejected
        // below via stateLookup.lookup throwing. The reverse direction — a
        // stateful vertex present in the checkpoint but absent from the current
        // graph — was previously silently dropped (the loop only walked current
        // vertices). Per checkpoint-design.md §8.6 the safe default is to
        // reject such a restore (it indicates a stateful vertex was deleted).
        validateReverseVertexDifferential(execPlan, checkpointPlan, checkpointLocations);

        // Stage 35: group the checkpoint's old subtasks by vertex so a rescale
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

            // CONN-01 successor D1 (checkpoint-design.md §8.5.2): a 2PC sink vertex
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
                // Stage 47: channel state (unaligned checkpoint in-flight data)
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
                // Item 14 subtask filter: skip subtasks the caller is not
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

                // Stage 43 (unaligned checkpoint recovery): AFTER operator state
                // restore and BEFORE the task starts reading, inject the captured
                // in-flight channel records into the invokable's InputGate so they
                // are replayed ahead of any new upstream records. Aligned-checkpoint
                // snapshots have no channel state (null) → no-op.
                restoreChannelStateIfPresent(invokable, taskState);
            }
        }
    }

    /**
     * Stage 43: injects unaligned-checkpoint channel state into a recovered
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
    private static int resolveMaxParallelism(GraphExecutionPlan execPlan, CheckpointPlan checkpointPlan) {
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

    /**
     * Stage 47 (unaligned checkpoint + rescale interaction): fails fast when a
     * rescale restore would have to redistribute channel state (unaligned
     * checkpoint in-flight data) across a new parallelism. Channel state carries
     * per-channel records with no cross-parallelism redistribution metadata
     * (no {@code InflightDataRescalingDescriptor} in the first version), so
     * silently dropping it — which the prior {@code instanceof TaskEpochSnapshot}
     * guard in {@code restoreChannelStateIfPresent} did, because
     * {@code buildRescaledTaskState} produces a plain {@code TaskStateSnapshot} —
     * breaks exactly-once. See {@code checkpoint-design.md} §2.11.8 D1/D2.
     *
     * <p>Package-private so the focused unit test can exercise the check directly
     * (same pattern as {@link #validateReverseVertexDifferential}).
     *
     * @param vertexId        the rescaling vertex
     * @param oldSubtasks      the checkpoint's old subtask locations for this vertex
     * @param newParallelism   the new parallelism
     * @param oldParallelism   the old parallelism
     * @param stateLookup      lookup over the checkpoint's task states
     * @throws StreamException ({@code ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED})
     *         if any old subtask snapshot carries a non-empty {@link ChannelState}
     */
    static void assertNoChannelStateOnRescale(
            String vertexId, List<TaskLocation> oldSubtasks,
            int newParallelism, int oldParallelism, TaskStateLookup stateLookup) throws Exception {
        for (TaskLocation oldLoc : oldSubtasks) {
            TaskStateSnapshot oldState = stateLookup.lookup(oldLoc);
            if (!(oldState instanceof TaskEpochSnapshot)) {
                continue;
            }
            ChannelState cs = ((TaskEpochSnapshot) oldState).getChannelState();
            if (cs != null && !cs.isEmpty()) {
                throw new StreamException(ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED)
                        .param(ARG_VERTEX_ID, vertexId)
                        .param(ARG_OLD_PARALLELISM, oldParallelism)
                        .param(ARG_NEW_PARALLELISM, newParallelism);
            }
        }
    }

    /**
     * Stage 35: build the rescaled TaskStateSnapshot for a new subtask by
     * merging keyed state from <em>all</em> old subtasks of the vertex (the new
     * subtask's KeyGroupRange may intersect several old subtask ranges) and
     * filtering the merged entries to those owned by {@code newRange}. Operator
     * (non-keyed) state is taken 1:1 from the old subtask at the same index
     * when it exists, and left empty for subtasks added by a scale-up (operator
     * state rescale redistribution is out of scope per the plan Non-Goals).
     */
    @SuppressWarnings("unchecked")
    private static TaskStateSnapshot buildRescaledTaskState(
            String vertexId, int taskIndex, KeyGroupRange newRange,
            List<TaskLocation> oldSubtasks, int newParallelism, int oldParallelism,
            int maxParallelism, TaskStateLookup stateLookup, CheckpointPlan checkpointPlan) throws Exception {

        TaskLocation newLoc = new TaskLocation(checkpointPlan.getJobId(), checkpointPlan.getPipelineId(), vertexId, taskIndex);
        TaskStateSnapshot merged = new TaskStateSnapshot(newLoc, -1);

        // Operator (non-keyed) state: 1:1 by index where an old subtask exists.
        if (taskIndex < oldParallelism) {
            TaskLocation oldLoc = oldSubtasks.get(taskIndex);
            TaskStateSnapshot oldState = stateLookup.lookup(oldLoc);
            if (oldState != null && oldState.getOperatorStates() != null) {
                for (Map.Entry<String, Object> e : oldState.getOperatorStates().entrySet()) {
                    merged.putOperatorState(e.getKey(), e.getValue());
                }
            }
        }

        // Keyed state: union of all old subtasks' keyed snapshots, filtered by newRange.
        // Collect each keyed storage key (e.g. "operator-3-keyed") and merge its entries.
        Map<String, List<Map<String, Object>>> mergedKeyedByName = new LinkedHashMap<>();
        for (TaskLocation oldLoc : oldSubtasks) {
            TaskStateSnapshot oldState = stateLookup.lookup(oldLoc);
            if (oldState == null || oldState.getKeyedStates() == null) continue;
            for (Map.Entry<String, Object> ke : oldState.getKeyedStates().entrySet()) {
                Map<String, Object> dataMap = toStateDataMap(ke.getValue());
                if (dataMap == null) continue;
                Object statesObj = dataMap.get("states");
                if (!(statesObj instanceof Map)) continue;
                Map<String, Object> statesMap = (Map<String, Object>) statesObj;
                List<Map<String, Object>> bucket = mergedKeyedByName
                        .computeIfAbsent(ke.getKey(), k -> new ArrayList<>());
                bucket.add(statesMap);
            }
        }

        for (Map.Entry<String, List<Map<String, Object>>> entry : mergedKeyedByName.entrySet()) {
            Map<String, Object> mergedStates = mergeAndFilterKeyedStates(entry.getValue(), newRange, maxParallelism);
            if (mergedStates.isEmpty()) continue;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("states", mergedStates);
            merged.putKeyedState(entry.getKey(), new StateSnapshot(data));
        }

        return merged;
    }

    /**
     * Merge multiple old subtasks' {@code states} sub-maps (per keyed state name)
     * and keep only the entries whose raw key is owned by {@code range}. Entries
     * are appended in old-subtask order; the result preserves each state's info
     * metadata (stateType/valueType/schema...) taken from the first contributor.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> mergeAndFilterKeyedStates(List<Map<String, Object>> sources,
                                                                 KeyGroupRange range, int maxParallelism) {
        Map<String, Object> result = new LinkedHashMap<>();
        // stateName -> merged info map (entries list grows across contributors)
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (Map<String, Object> src : sources) {
            Map<String, Object> filtered = KeyGroupRangeRestoreFilter.filterKeyedStates(src, range, maxParallelism);
            for (Map.Entry<String, Object> e : filtered.entrySet()) {
                Map<String, Object> info = (Map<String, Object>) e.getValue();
                Map<String, Object> acc = byName.get(e.getKey());
                if (acc == null) {
                    byName.put(e.getKey(), new LinkedHashMap<>(info));
                } else {
                    Object entries = info.get("entries");
                    if (entries instanceof List) {
                        Object accEntries = acc.computeIfAbsent("entries", k -> new ArrayList<>());
                        if (accEntries instanceof List) {
                            ((List<Object>) accEntries).addAll((List<?>) entries);
                        }
                    }
                }
            }
        }
        result.putAll(byName);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toStateDataMap(Object keyedValue) {
        if (keyedValue instanceof StateSnapshot) {
            return ((StateSnapshot) keyedValue).getStateData();
        }
        if (keyedValue instanceof Map) {
            return (Map<String, Object>) keyedValue;
        }
        return null;
    }

    /**
     * Stage 35: materialize the key-group ownership of every keyed subtask into
     * a {@link TaskEpochSnapshot} so the production checkpoint path records the
     * KeyGroupRange each subtask owned (the {@code shards} list was never
     * populated in production). The stamped ownership is persisted by
     * {@code CheckpointSerDe} and re-read on restore. Mutates the checkpoint's
     * task-state map in place (it is a mutable {@code HashMap}).
     *
     * <p>Per-vertex {@code parallelism} is derived from the number of subtask
     * locations recorded for that vertex; {@code maxParallelism} is resolved
     * from the execution plan's keyed backends (job-global constant).
     */
    static void materializeKeyGroupOwnership(CompletedCheckpoint checkpoint, GraphExecutionPlan execPlan) {
        if (checkpoint == null || checkpoint.getTaskStates() == null || checkpoint.getTaskStates().isEmpty()) {
            return;
        }
        int maxParallelism = resolveMaxParallelism(execPlan, null);
        // Count subtasks per vertex to derive each vertex's parallelism.
        Map<String, Integer> parallelismByVertex = new HashMap<>();
        for (TaskLocation loc : checkpoint.getTaskStates().keySet()) {
            parallelismByVertex.merge(loc.getVertexId(), 1, Integer::sum);
        }
        Map<TaskLocation, TaskStateSnapshot> taskStates = checkpoint.getTaskStates();
        for (Map.Entry<TaskLocation, TaskStateSnapshot> entry : taskStates.entrySet()) {
            TaskLocation loc = entry.getKey();
            int parallelism = parallelismByVertex.getOrDefault(loc.getVertexId(), 1);
            KeyGroupRange range = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(
                    maxParallelism, parallelism, loc.getTaskIndex());
            TaskEpochSnapshot epoch = TaskEpochSnapshot.fromTaskStateSnapshot(entry.getValue());
            epoch.setKeyGroupOwnership(parallelism, maxParallelism, range);
            entry.setValue(epoch);
        }
    }

    /**
     * P0-7: enforce reverse-direction savepoint/checkpoint vertex differential.
     * Computes the set of stateful vertices (vertexId) referenced by the
     * checkpoint and rejects restore if any of them are absent from the current
     * execution plan — i.e. a stateful vertex was deleted. Aligns with
     * {@code checkpoint-design.md} §8.6 "delete stateful vertex = default
     * reject". Forward direction (current vertex not in checkpoint) is
     * rejected by {@code stateLookup.lookup} in the caller — also hardened
     * here as an explicit forward-differential pre-check so the reject fires
     * independent of invokable installation state.
     *
     * <p>Vertex-level granularity only — operatorId-level differential and the
     * nuanced state-aware §8.6 classification (distinguish stateful vs
     * stateless new vertex, initial-state fallback) are deferred to the
     * roadmap successor (see plan Deferred But Adjudicated §"P0-7 operatorId
     * 粒度差分").
     */
    static void validateReverseVertexDifferential(
            GraphExecutionPlan execPlan,
            CheckpointPlan checkpointPlan,
            Set<TaskLocation> checkpointLocations) {
        if (checkpointLocations == null || checkpointLocations.isEmpty()) {
            return;
        }

        // Current graph's vertex set (only vertices present in the execution
        // plan with installed subtasks count — others are not state-bearing).
        Set<String> currentVertexIds = new TreeSet<>();
        for (String vertexId : execPlan.getSortedVertexIds()) {
            if (!execPlan.getSubtasks(vertexId).isEmpty()) {
                currentVertexIds.add(vertexId);
            }
        }

        // Checkpoint's vertex set (extracted from TaskLocation.vertexId).
        Set<String> checkpointVertexIds = new TreeSet<>();
        for (TaskLocation loc : checkpointLocations) {
            if (loc != null && loc.getVertexId() != null) {
                checkpointVertexIds.add(loc.getVertexId());
            }
        }

        // Forward differential: current vertices absent from checkpoint.
        // Mirrors the existing throw at the production stateLookup lambda —
        // hardened here as a pre-check so it fires independent of invokable
        // installation state. Removing this check would lose the contract
        // that a new stateful vertex is rejected on restore.
        Set<String> forwardMissing = new TreeSet<>(currentVertexIds);
        forwardMissing.removeAll(checkpointVertexIds);
        if (!forwardMissing.isEmpty()) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED)
                    .param(ARG_DETAIL, "Current graph contains stateful vertices absent from checkpoint "
                            + "(likely new): missing=" + forwardMissing
                            + "; current-vertices=" + currentVertexIds
                            + "; checkpoint-vertices=" + checkpointVertexIds);
        }

        // Reverse differential: checkpoint vertices not in current graph.
        Set<String> missing = new TreeSet<>(checkpointVertexIds);
        missing.removeAll(currentVertexIds);
        if (!missing.isEmpty()) {
            throw new StreamException(ERR_STREAM_SAVEPOINT_VERTEX_DIFFERENTIAL)
                    .param(ARG_MISSING_VERTEX_IDS, missing)
                    .param(ARG_CHECKPOINT_VERTEX_IDS, checkpointVertexIds)
                    .param(ARG_CURRENT_VERTEX_IDS, currentVertexIds);
        }
    }

    private static void restoreTaskStatesFromCheckpoint(
            GraphExecutionPlan execPlan,
            CheckpointPlan checkpointPlan,
            CompletedCheckpoint checkpoint) throws Exception {
        // P0-7: pass the checkpoint's TaskLocation set so the shared restore
        // path can perform the reverse-direction vertex differential check.
        Set<TaskLocation> checkpointLocations = checkpoint.getTaskStates().keySet();
        restoreTaskStatesFromSource(execPlan, checkpointPlan, checkpoint.getCheckpointId(),
                checkpointLocations,
                (taskLocation) -> {
                    TaskStateSnapshot state = checkpoint.getTaskState(taskLocation);
                    if (state == null) {
                        throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED)
                                .param(ARG_VERTEX_ID, taskLocation.getVertexId())
                                .param(ARG_TASK_INDEX, taskLocation.getTaskIndex())
                                .param(ARG_TASK_LOCATION, taskLocation)
                                .param(ARG_CHECKPOINT_ID, checkpoint.getCheckpointId())
                                .param(ARG_EPOCH_ID, checkpoint.getCheckpointId())
                                .param(ARG_DETAIL, "Available keys: " + checkpoint.getTaskStates().keySet());
                    }
                    return state;
                });
    }

    /**
     * Restores operator state for a single {@link OperatorChain} from a
     * {@link TaskStateSnapshot} captured at the given epoch.
     *
     * <p>Stage 44 successor 4 (drain/reconnect): exposed package-private so
     * {@link SupervisionLoop#rebuildTask} can reuse the exact same restore path
     * as the initial {@link #restoreFromCheckpoint} on region-scoped restart.
     * Before this exposure, {@code rebuildTask} deep-copied the JobVertex
     * template (empty initial state) and replayed from epoch 0 — correct only
     * for full replay. With consistent-cut epoch alignment (replay from
     * epoch N &gt; 0), operator state must be restored from the checkpoint at
     * epoch N, otherwise stateful operators (window/CEP/aggregate) lose their
     * pre-checkpoint accumulated state and silently produce wrong results.
     *
     * @param chain     the operator chain to restore into (must not be null)
     * @param epochId   the checkpoint id (consistent-cut epoch) of the snapshot
     * @param taskState the per-task state snapshot (must not be null)
     * @param mappings  operator-state mappings for this task (may be empty)
     * @throws Exception if any operator's restore fails (fail-fast)
     */
    static void restoreOperatorsFromState(
            OperatorChain chain,
            long epochId,
            TaskStateSnapshot taskState,
            List<OperatorStateMapping> mappings) throws Exception {

        if (chain == null) return;

        List<StreamOperator<?>> operators = chain.getOperators();
        for (int i = 0; i < operators.size(); i++) {
            StreamOperator<?> op = operators.get(i);
            if (op instanceof AbstractStreamOperator) {
                OperatorSnapshotResult opResult = buildSnapshotFromTaskState(taskState, i, mappings);
                if (opResult != null && !opResult.isEmpty()) {
                    try {
                        ((AbstractStreamOperator<?>) op).restoreState(opResult);
                        LOG.debug("Restored state for operator index {}", i);
                    } catch (Exception e) {
                        LOG.error("Failed to restore state for operator index {}", i, e);
                        throw e;
                    }
                }
            }

            if (op instanceof CheckpointParticipant) {
                try {
                    ((CheckpointParticipant) op).restoreFromEpoch(epochId, taskState);
                    LOG.debug("Restored from epoch {} for CheckpointParticipant operator index {}", epochId, i);
                } catch (Exception e) {
                    LOG.error("Failed to restoreFromEpoch for operator index {}", i, e);
                    throw e;
                }
            } else if (op instanceof AbstractUdfStreamOperator) {
                Object udf = ((AbstractUdfStreamOperator<?, ?>) op).getUserFunction();
                if (udf instanceof CheckpointParticipant && udf != op) {
                    try {
                        ((CheckpointParticipant) udf).restoreFromEpoch(epochId, taskState);
                        LOG.debug("Restored from epoch {} for CheckpointParticipant UDF operator index {}", epochId, i);
                    } catch (Exception e) {
                        LOG.error("Failed to restoreFromEpoch for UDF operator index {}", i, e);
                        throw e;
                    }
                }
            }
        }
    }

    static OperatorSnapshotResult buildSnapshotFromTaskState(
            TaskStateSnapshot taskState,
            int operatorIndex,
            List<OperatorStateMapping> mappings) {

        OperatorSnapshotResult.Builder builder = OperatorSnapshotResult.builder();
        boolean found = false;

        if (mappings != null) {
            for (OperatorStateMapping mapping : mappings) {
                if (mapping.getOperatorIndex() == operatorIndex) {
                    String opStateKey = mapping.getOperatorStateKey();
                    String prefix = opStateKey + "-";
                    for (Map.Entry<String, Object> entry : taskState.getOperatorStates().entrySet()) {
                        if (entry.getKey().equals(opStateKey) || entry.getKey().startsWith(prefix)) {
                            String stateKey = entry.getKey().equals(opStateKey)
                                    ? entry.getKey()
                                    : entry.getKey().substring(prefix.length());
                            builder.putOperatorState(stateKey, entry.getValue());
                            found = true;
                        }
                    }

                    if (mapping.hasKeyedState()) {
                        String keyedPrefix = mapping.getKeyedStateStorageKey();
                        for (Map.Entry<String, Object> entry : taskState.getKeyedStates().entrySet()) {
                            if (entry.getKey().startsWith(keyedPrefix)) {
                                builder.putKeyedState(entry.getKey(), entry.getValue());
                                found = true;
                            }
                        }
                    } else {
                        // P1-01 (restore-chain gap found during execution):
                        // CheckpointPlanBuilder marks keyed state only when the
                        // operator's keyedStateBackend exists AT PLAN-BUILD TIME
                        // (pre-open → always null), so the tracker ACK writes the
                        // operator's keyed snapshot under the RAW key
                        // ("keyed-state", the key AbstractStreamOperator.
                        // snapshotState uses) instead of a per-operator prefix.
                        // Without this fallback, keyed state was silently dropped
                        // on restore (opResult empty → restore skipped) and every
                        // keyed operator resumed from empty state. The fallback
                        // mirrors the tracker's raw-key ACK path; a chain has at
                        // most one keyed-state-bearing operator by construction
                        // (the raw key would otherwise overwrite on the ACK side).
                        Object rawKeyed = taskState.getKeyedState("keyed-state");
                        if (rawKeyed != null) {
                            builder.putKeyedState("keyed-state", rawKeyed);
                            found = true;
                        }
                    }
                    break;
                }
            }
        }

        if (!found) {
            String opStateKey = "operator-" + operatorIndex;
            Object opState = taskState.getOperatorState(opStateKey);
            if (opState != null) {
                builder.putOperatorState(opStateKey, opState);
            }

            LOG.warn("No mapping found for operator index {}, skipping keyed state", operatorIndex);
        }

        return builder.build();
    }
}
