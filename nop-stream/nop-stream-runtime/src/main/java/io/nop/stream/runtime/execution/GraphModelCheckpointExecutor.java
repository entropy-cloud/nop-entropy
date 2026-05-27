/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.environment.StreamExecutionResult;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.CheckpointBarrierTracker;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.execution.Subtask;
import io.nop.stream.core.execution.SubtaskTask;
import io.nop.stream.core.execution.TaskExecutor;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.model.StreamModel;
import io.nop.stream.core.model.StreamModelFingerprint;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.AbstractUdfStreamOperator;
import io.nop.stream.core.operators.StreamOperator;
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

        long startTime = System.currentTimeMillis();

        boolean barrierAlignment = resolveBarrierAlignment(checkpointConfig);
        GraphExecutionPlan execPlan = buildExecutionPlan(jobGraph, barrierAlignment);
        String jobId = resolveJobId(checkpointConfig);
        String pipelineId = resolvePipelineId(checkpointConfig);

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        ICheckpointStorage storage = createStorage(checkpointConfig);
        CheckpointPlan checkpointPlan = CheckpointPlanBuilder.build(execPlan, jobId, pipelineId, null, checkpointConfig);

        CheckpointCoordinator coordinator = createCoordinator(jobId, pipelineId, idCounter, storage, checkpointConfig);
        List<StreamTaskInvokable> allInvokables = registerTasksAndTrackers(execPlan, checkpointPlan, coordinator);

        ScheduledExecutorService barrierScheduler = startBarrierScheduler(allInvokables, coordinator, checkpointConfig, jobId);

        restoreFromCheckpoint(execPlan, coordinator, checkpointPlan, null);

        Map<String, SubtaskTask> tasks = buildTasks(execPlan);
        TaskExecutor executor = new TaskExecutor();

        try {
            submitAndRun(execPlan, tasks, executor);
            handleJobTermination(allInvokables, coordinator, checkpointConfig);
            checkTaskFailures(tasks);

            logCheckpointMetrics(coordinator);

            long executionTime = System.currentTimeMillis() - startTime;
            return new StreamExecutionResult(jobName, executionTime);
        } finally {
            shutdown(barrierScheduler, coordinator);
        }
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

        long startTime = System.currentTimeMillis();

        // Build JobGraph from the stream model's transformations
        JobGraph jobGraph = buildJobGraphFromStreamModel(streamModel);
        String jobName = partitionedPlan.getJobId() != null ? partitionedPlan.getJobId() : "Streaming Job";

        CheckpointConfig checkpointConfig = new CheckpointConfig();
        checkpointConfig.setCheckpointEnabled(true);
        String jobId = partitionedPlan.getJobId() != null ? partitionedPlan.getJobId() : "job-0";
        String pipelineId = partitionedPlan.getPipelineId() != null ? partitionedPlan.getPipelineId() : "pipeline-0";
        checkpointConfig.setJobId(jobId);
        checkpointConfig.setPipelineId(pipelineId);

        boolean barrierAlignment = resolveBarrierAlignment(checkpointConfig);
        GraphExecutionPlan execPlan = buildExecutionPlan(jobGraph, deploymentPlan, barrierAlignment);

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        ICheckpointStorage storage = createStorage(checkpointConfig);
        CheckpointPlan checkpointPlan = CheckpointPlanBuilder.build(execPlan, jobId, pipelineId, null, checkpointConfig);

        CheckpointCoordinator coordinator = createCoordinator(jobId, pipelineId, idCounter, storage, checkpointConfig);

        // Compute and set fingerprint for EpochManifest persistence
        StreamModelFingerprint fingerprint = streamModel.computeFingerprint();
        coordinator.setCurrentFingerprint(fingerprint);

        List<StreamTaskInvokable> allInvokables = registerTasksAndTrackers(execPlan, checkpointPlan, coordinator);

        ScheduledExecutorService barrierScheduler = startBarrierScheduler(allInvokables, coordinator, checkpointConfig, jobId);

        restoreFromCheckpoint(execPlan, coordinator, checkpointPlan, streamModel);

        Map<String, SubtaskTask> tasks = buildTasks(execPlan);
        TaskExecutor executor = new TaskExecutor();

        try {
            submitAndRun(execPlan, tasks, executor);
            handleJobTermination(allInvokables, coordinator, checkpointConfig);
            checkTaskFailures(tasks);

            logCheckpointMetrics(coordinator);

            long executionTime = System.currentTimeMillis() - startTime;
            return new StreamExecutionResult(jobName, executionTime);
        } finally {
            shutdown(barrierScheduler, coordinator);
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

        boolean barrierAlignment = resolveBarrierAlignment(checkpointConfig);
        GraphExecutionPlan execPlan = buildExecutionPlan(jobGraph, barrierAlignment);
        String jobId = resolveJobId(checkpointConfig);
        String pipelineId = resolvePipelineId(checkpointConfig);

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        ICheckpointStorage storage = createStorage(checkpointConfig);
        if (targetPath != null && !targetPath.isEmpty()) {
            storage = new LocalFileCheckpointStorage(targetPath);
        }
        CheckpointPlan checkpointPlan = CheckpointPlanBuilder.build(execPlan, jobId, pipelineId, null, checkpointConfig);

        CheckpointCoordinator coordinator = createCoordinator(jobId, pipelineId, idCounter, storage, checkpointConfig);
        List<StreamTaskInvokable> allInvokables = registerTasksAndTrackers(execPlan, checkpointPlan, coordinator);

        ScheduledExecutorService barrierScheduler = startBarrierScheduler(allInvokables, coordinator, checkpointConfig, jobId);

        Map<String, SubtaskTask> tasks = buildTasks(execPlan);
        TaskExecutor executor = new TaskExecutor();

        try {
            submitAndRun(execPlan, tasks, executor);

            PendingCheckpoint savepointPending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.SAVEPOINT);
            String savepointPath = null;
            if (savepointPending != null) {
                triggerBarrierOnAllInvokables(allInvokables, savepointPending);

                CompletedCheckpoint completed = (CompletedCheckpoint) savepointPending.getCompletableFuture()
                        .get(checkpointConfig.getCheckpointTimeout(), TimeUnit.MILLISECONDS);
                if (completed != null) {
                    savepointPath = storage.storeCheckPoint(completed);
                }
            }

            checkTaskFailures(tasks);
            return savepointPath;
        } finally {
            shutdown(barrierScheduler, coordinator);
        }
    }

    public static StreamExecutionResult executeWithSavepoint(
            JobGraph jobGraph,
            String jobName,
            CheckpointConfig checkpointConfig,
            String savepointPath) throws Exception {

        long startTime = System.currentTimeMillis();

        boolean barrierAlignment = resolveBarrierAlignment(checkpointConfig);
        GraphExecutionPlan execPlan = buildExecutionPlan(jobGraph, barrierAlignment);
        String jobId = resolveJobId(checkpointConfig);
        String pipelineId = resolvePipelineId(checkpointConfig);

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        ICheckpointStorage storage = createStorage(checkpointConfig);
        CheckpointPlan checkpointPlan = CheckpointPlanBuilder.build(execPlan, jobId, pipelineId, null, checkpointConfig);

        CheckpointCoordinator coordinator = createCoordinator(jobId, pipelineId, idCounter, storage, checkpointConfig);
        List<StreamTaskInvokable> allInvokables = registerTasksAndTrackers(execPlan, checkpointPlan, coordinator);

        ScheduledExecutorService barrierScheduler = startBarrierScheduler(allInvokables, coordinator, checkpointConfig, jobId);

        if (savepointPath != null && !savepointPath.isEmpty()) {
            restoreFromSavepointPath(execPlan, storage, checkpointPlan, savepointPath);
        }

        Map<String, SubtaskTask> tasks = buildTasks(execPlan);
        TaskExecutor executor = new TaskExecutor();

        try {
            submitAndRun(execPlan, tasks, executor);
            triggerFinalCheckpoint(allInvokables, coordinator);
            checkTaskFailures(tasks);

            long executionTime = System.currentTimeMillis() - startTime;
            return new StreamExecutionResult(jobName, executionTime);
        } finally {
            shutdown(barrierScheduler, coordinator);
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
                LOG.info("Job termination mode: SUSPEND - triggering savepoint then stopping sources");
                triggerTerminalSavepoint(allInvokables, coordinator, config, CheckpointType.SAVEPOINT);
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

    private static GraphExecutionPlan buildExecutionPlan(JobGraph jobGraph, DeploymentPlan deploymentPlan,
                                                         boolean barrierAlignment) {
        return GraphExecutionPlan.build(jobGraph, deploymentPlan, barrierAlignment);
    }

    private static boolean resolveBarrierAlignment(CheckpointConfig config) {
        return config.getProcessingGuarantee().isBarrierAlignment();
    }

    private static String resolveJobId(CheckpointConfig config) {
        return config.getJobId();
    }

    private static String resolvePipelineId(CheckpointConfig config) {
        return config.getPipelineId();
    }

    private static CheckpointCoordinator createCoordinator(
            String jobId, String pipelineId,
            CheckpointIDCounter idCounter, ICheckpointStorage storage,
            CheckpointConfig config) {
        return new CheckpointCoordinator(jobId, pipelineId, idCounter, storage, config);
    }

    private static List<StreamTaskInvokable> registerTasksAndTrackers(
            GraphExecutionPlan execPlan,
            CheckpointPlan checkpointPlan,
            CheckpointCoordinator coordinator) {

        List<StreamTaskInvokable> allInvokables = new ArrayList<>();

        for (String vertexId : execPlan.getSortedVertexIds()) {
            JobVertex execVertex = execPlan.getExecutionVertices().get(vertexId);
            List<OperatorChain> chains = execVertex.getOperatorChains();

            for (Subtask subtask : execPlan.getSubtasks(vertexId)) {
                StreamTaskInvokable invokable = subtask.getInvokable();
                allInvokables.add(invokable);

                TaskLocation taskLocation = findTaskLocationInPlan(checkpointPlan, vertexId, subtask.getTaskIndex());
                coordinator.registerTask(taskLocation);

                List<OperatorStateMapping> mappings = checkpointPlan.getStateMappings(taskLocation);

                for (OperatorChain chain : chains) {
                    List<StreamOperator<?>> operators = chain.getOperators();

                    CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(
                            taskLocation, operators, mappings,
                            snapshot -> coordinator.acknowledgeTask(taskLocation, snapshot.getCheckpointId(), snapshot)
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
                                IStateBackend stateBackend = new MemoryStateBackend();
                                abstractOp.setStateBackend(stateBackend);
                            }
                        }
                    }
                }
            }
        }

        return allInvokables;
    }

    private static TaskLocation findTaskLocationInPlan(CheckpointPlan plan, String vertexId, int taskIndex) {
        for (TaskLocation loc : plan.getAllTasks()) {
            if (loc.getVertexId().equals(vertexId) && loc.getTaskIndex() == taskIndex) {
                return loc;
            }
        }
        throw new StreamException("No TaskLocation found for vertex " + vertexId + " subtask " + taskIndex);
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
                LOG.error("Failed to inject checkpoint barrier", e);
            }
        }, config.getCheckpointInterval(), config.getCheckpointInterval(), TimeUnit.MILLISECONDS);

        return scheduler;
    }

    private static void triggerBarrierOnAllInvokables(
            List<StreamTaskInvokable> allInvokables, PendingCheckpoint pending) throws Exception {
        for (StreamTaskInvokable inv : allInvokables) {
            if (inv.getBarrierTracker() != null) {
                boolean accepted = inv.getBarrierTracker().triggerCheckpoint(
                        pending.getCheckpointId(),
                        pending.getTriggerTimestamp(),
                        pending.getCheckpointType()
                );
                if (!accepted) {
                    LOG.warn("Checkpoint {} skipped for task due to overlap", pending.getCheckpointId());
                }
            }
        }
    }

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
            LOG.error("Failed to trigger final checkpoint", e);
        }
    }

    private static Map<String, SubtaskTask> buildTasks(GraphExecutionPlan execPlan) {
        Map<String, SubtaskTask> tasks = new LinkedHashMap<>();
        for (String vertexId : execPlan.getSortedVertexIds()) {
            JobVertex vertex = execPlan.getExecutionVertices().get(vertexId);
            for (Subtask subtask : execPlan.getSubtasks(vertexId)) {
                String taskKey = vertexId + "-" + subtask.getTaskIndex();
                tasks.put(taskKey, new SubtaskTask(subtask, vertex));
            }
        }
        return tasks;
    }

    private static void submitAndRun(GraphExecutionPlan execPlan, Map<String, SubtaskTask> tasks, TaskExecutor executor) throws InterruptedException {
        for (SubtaskTask task : tasks.values()) {
            executor.submitTask(task);
        }
        executor.awaitCompletion();
    }

    private static void checkTaskFailures(Map<String, SubtaskTask> tasks) {
        for (SubtaskTask task : tasks.values()) {
            if (task.getState() == SubtaskTask.State.FAILED) {
                throw new StreamException("Task failed", task.getError());
            }
        }
    }

    private static void shutdown(ScheduledExecutorService barrierScheduler, CheckpointCoordinator coordinator) {
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
            throw new StreamException(
                    "JdbcCheckpointStorage requires IJdbcTemplate configuration. " +
                    "Use storageType='local' or provide JDBC configuration.");
        }
        String basePath = config.getStorageProperty("path");
        if (basePath == null || basePath.isEmpty()) {
            basePath = System.getProperty("java.io.tmpdir") + "/nop-stream-checkpoints";
        }
        return new LocalFileCheckpointStorage(basePath);
    }

    private static void restoreFromCheckpoint(
            GraphExecutionPlan execPlan,
            CheckpointCoordinator coordinator,
            CheckpointPlan checkpointPlan,
            StreamModel streamModel) throws Exception {

        // Try EpochManifest first (preferred recovery path)
        EpochManifest epochManifest = coordinator.restoreLatestEpochManifest();
        if (epochManifest != null) {
            LOG.info("Recovering from EpochManifest epoch {} (jobId={})",
                    epochManifest.getEpochId(), epochManifest.getJobId());

            // Validate fingerprint compatibility
            validateFingerprintCompatibility(epochManifest, streamModel, coordinator);

            for (String vertexId : execPlan.getSortedVertexIds()) {
                for (Subtask subtask : execPlan.getSubtasks(vertexId)) {
                    StreamTaskInvokable invokable = subtask.getInvokable();
                    if (invokable == null) continue;

                    TaskLocation taskLocation = findTaskLocationInPlan(checkpointPlan, vertexId, subtask.getTaskIndex());
                    TaskStateSnapshot taskState = epochManifest.getTaskSnapshots().get(taskLocation);

                    if (taskState == null) {
                        throw new StreamException(
                                "No exact task state found for vertex " + vertexId +
                                        " subtask " + subtask.getTaskIndex() +
                                        " (taskLocation=" + taskLocation + ") in EpochManifest epoch " +
                                        epochManifest.getEpochId() + ". Available keys: " +
                                        epochManifest.getTaskSnapshots().keySet());
                    }

                    List<OperatorStateMapping> mappings = checkpointPlan.getStateMappings(taskLocation);
                    restoreOperatorsFromState(invokable.getOperatorChain(), taskState, mappings);
                }
            }
            return;
        }

        // Fall back to CompletedCheckpoint
        CompletedCheckpoint latestCheckpoint = coordinator.restoreFromCheckpoint();
        if (latestCheckpoint == null) {
            LOG.info("No recoverable checkpoint found, starting fresh");
            return;
        }

        LOG.info("Recovering from checkpoint {} (jobId={})",
                latestCheckpoint.getCheckpointId(), latestCheckpoint.getJobId());

        for (String vertexId : execPlan.getSortedVertexIds()) {
            for (Subtask subtask : execPlan.getSubtasks(vertexId)) {
                StreamTaskInvokable invokable = subtask.getInvokable();
                if (invokable == null) continue;

                TaskLocation taskLocation = findTaskLocationInPlan(checkpointPlan, vertexId, subtask.getTaskIndex());
                TaskStateSnapshot taskState = latestCheckpoint.getTaskState(taskLocation);

                if (taskState == null) {
                    throw new StreamException(
                            "No exact task state found for vertex " + vertexId +
                                    " subtask " + subtask.getTaskIndex() +
                                    " (taskLocation=" + taskLocation + ") in checkpoint " +
                                    latestCheckpoint.getCheckpointId() + ". Available keys: " +
                                    latestCheckpoint.getTaskStates().keySet());
                }

                List<OperatorStateMapping> mappings = checkpointPlan.getStateMappings(taskLocation);
                restoreOperatorsFromState(invokable.getOperatorChain(), taskState, mappings);
            }
        }
    }

    /**
     * Validates that the current StreamModel fingerprint is compatible with the
     * fingerprint stored in the EpochManifest. Throws StreamException if incompatible.
     */
    private static void validateFingerprintCompatibility(
            EpochManifest epochManifest,
            StreamModel streamModel,
            CheckpointCoordinator coordinator) {

        StreamModelFingerprint storedFingerprint = epochManifest.getStreamModelFingerprint();
        if (storedFingerprint == null) {
            LOG.info("No fingerprint in EpochManifest epoch={}, skipping compatibility check",
                    epochManifest.getEpochId());
            return;
        }

        StreamModelFingerprint currentFingerprint;
        if (streamModel != null) {
            currentFingerprint = streamModel.computeFingerprint();
        } else if (coordinator.getCurrentFingerprint() != null) {
            currentFingerprint = coordinator.getCurrentFingerprint();
        } else {
            LOG.warn("No current fingerprint available, skipping compatibility check");
            return;
        }

        if (!currentFingerprint.isCompatibleWith(storedFingerprint)) {
            throw new StreamException(
                    "StreamModel fingerprint incompatible on restore. " +
                            "stored=" + storedFingerprint + ", current=" + currentFingerprint +
                            ". DAG topology or requirements have changed since the checkpoint was taken.");
        }

        LOG.info("Fingerprint compatibility check passed for epoch {}",
                epochManifest.getEpochId());
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

        for (String vertexId : execPlan.getSortedVertexIds()) {
            for (Subtask subtask : execPlan.getSubtasks(vertexId)) {
                StreamTaskInvokable invokable = subtask.getInvokable();
                if (invokable == null) continue;

                TaskLocation taskLocation = findTaskLocationInPlan(checkpointPlan, vertexId, subtask.getTaskIndex());
                TaskStateSnapshot taskState = savepointCheckpoint.getTaskState(taskLocation);

                if (taskState == null) {
                    throw new StreamException(
                            "No exact task state found for vertex " + vertexId +
                                    " subtask " + subtask.getTaskIndex() +
                                    " (taskLocation=" + taskLocation + ") in savepoint " +
                                    savepointCheckpoint.getCheckpointId() + ". Available keys: " +
                                    savepointCheckpoint.getTaskStates().keySet());
                }

                List<OperatorStateMapping> mappings = checkpointPlan.getStateMappings(taskLocation);
                restoreOperatorsFromState(invokable.getOperatorChain(), taskState, mappings);
            }
        }
    }

    private static void restoreOperatorsFromState(
            OperatorChain chain,
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
                    Object opState = taskState.getOperatorState(mapping.getOperatorStateKey());
                    if (opState != null) {
                        builder.putOperatorState(mapping.getOperatorStateKey(), opState);
                        found = true;
                    }

                    if (mapping.hasKeyedState()) {
                        String keyedPrefix = mapping.getKeyedStateStorageKey();
                        for (Map.Entry<String, Object> entry : taskState.getKeyedStates().entrySet()) {
                            if (entry.getKey().startsWith(keyedPrefix)) {
                                builder.putKeyedState(entry.getKey(), entry.getValue());
                                found = true;
                            }
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
                LOG.warn("Operator index {} not found in state mappings, using default key '{}'", operatorIndex, opStateKey);
            }

            for (Map.Entry<String, Object> entry : taskState.getKeyedStates().entrySet()) {
                builder.putKeyedState(entry.getKey(), entry.getValue());
            }
        }

        return builder.build();
    }
}
