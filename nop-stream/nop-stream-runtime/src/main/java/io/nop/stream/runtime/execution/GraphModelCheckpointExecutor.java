/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.environment.StreamExecutionResult;
import io.nop.stream.core.execution.CheckpointBarrierTracker;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.execution.Task;
import io.nop.stream.core.execution.TaskExecutor;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.AbstractUdfStreamOperator;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.CheckpointPlanBuilder;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.runtime.checkpoint.metrics.CheckpointMetricsSnapshot;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Internal
public class GraphModelCheckpointExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(GraphModelCheckpointExecutor.class);

    public static StreamExecutionResult executeWithCheckpoint(
            JobGraph jobGraph,
            String jobName,
            CheckpointConfig checkpointConfig) throws Exception {

        long startTime = System.currentTimeMillis();

        GraphExecutionPlan execPlan = buildExecutionPlan(jobGraph);
        String jobId = resolveJobId(checkpointConfig);
        String pipelineId = resolvePipelineId(checkpointConfig);

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        ICheckpointStorage storage = createStorage(checkpointConfig);
        CheckpointPlan checkpointPlan = CheckpointPlanBuilder.build(execPlan, jobId, pipelineId);

        CheckpointCoordinator coordinator = createCoordinator(jobId, pipelineId, idCounter, storage, checkpointConfig);
        List<StreamTaskInvokable> allInvokables = registerTasksAndTrackers(execPlan, checkpointPlan, coordinator);

        ScheduledExecutorService barrierScheduler = startBarrierScheduler(allInvokables, coordinator, checkpointConfig, jobId);

        restoreFromCheckpoint(execPlan, coordinator, checkpointPlan);

        Map<String, Task> tasks = buildTasks(execPlan);
        TaskExecutor executor = new TaskExecutor();

        try {
            submitAndRun(execPlan, tasks, executor);
            triggerFinalCheckpoint(allInvokables, coordinator);
            checkTaskFailures(tasks);

            logCheckpointMetrics(coordinator);

            long executionTime = System.currentTimeMillis() - startTime;
            return new StreamExecutionResult(jobName, executionTime);
        } finally {
            shutdown(barrierScheduler, coordinator);
        }
    }

    public static String triggerSavepoint(
            JobGraph jobGraph,
            CheckpointConfig checkpointConfig,
            String targetPath) throws Exception {

        GraphExecutionPlan execPlan = buildExecutionPlan(jobGraph);
        String jobId = resolveJobId(checkpointConfig);
        String pipelineId = resolvePipelineId(checkpointConfig);

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        ICheckpointStorage storage = createStorage(checkpointConfig);
        if (targetPath != null && !targetPath.isEmpty()) {
            storage = new LocalFileCheckpointStorage(targetPath);
        }
        CheckpointPlan checkpointPlan = CheckpointPlanBuilder.build(execPlan, jobId, pipelineId);

        CheckpointCoordinator coordinator = createCoordinator(jobId, pipelineId, idCounter, storage, checkpointConfig);
        List<StreamTaskInvokable> allInvokables = registerTasksAndTrackers(execPlan, checkpointPlan, coordinator);

        ScheduledExecutorService barrierScheduler = startBarrierScheduler(allInvokables, coordinator, checkpointConfig, jobId);

        Map<String, Task> tasks = buildTasks(execPlan);
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

        GraphExecutionPlan execPlan = buildExecutionPlan(jobGraph);
        String jobId = resolveJobId(checkpointConfig);
        String pipelineId = resolvePipelineId(checkpointConfig);

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        ICheckpointStorage storage = createStorage(checkpointConfig);
        CheckpointPlan checkpointPlan = CheckpointPlanBuilder.build(execPlan, jobId, pipelineId);

        CheckpointCoordinator coordinator = createCoordinator(jobId, pipelineId, idCounter, storage, checkpointConfig);
        List<StreamTaskInvokable> allInvokables = registerTasksAndTrackers(execPlan, checkpointPlan, coordinator);

        ScheduledExecutorService barrierScheduler = startBarrierScheduler(allInvokables, coordinator, checkpointConfig, jobId);

        if (savepointPath != null && !savepointPath.isEmpty()) {
            restoreFromSavepointPath(execPlan, storage, checkpointPlan, savepointPath);
        }

        Map<String, Task> tasks = buildTasks(execPlan);
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

    private static GraphExecutionPlan buildExecutionPlan(JobGraph jobGraph) {
        return GraphExecutionPlan.build(jobGraph);
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
            StreamTaskInvokable invokable = execPlan.getInvokables().get(vertexId);
            allInvokables.add(invokable);

            TaskLocation taskLocation = findTaskLocationForVertex(checkpointPlan, vertexId);
            coordinator.registerTask(taskLocation);

            JobVertex execVertex = execPlan.getExecutionVertices().get(vertexId);
            List<OperatorStateMapping> mappings = checkpointPlan.getStateMappings(taskLocation);

            for (OperatorChain chain : execVertex.getOperatorChains()) {
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
                    }
                }
            }
        }

        return allInvokables;
    }

    private static TaskLocation findTaskLocationForVertex(CheckpointPlan plan, String vertexId) {
        for (TaskLocation loc : plan.getAllTasks()) {
            if (loc.getVertexId().equals(vertexId)) {
                return loc;
            }
        }
        throw new IllegalStateException("No TaskLocation found for vertex: " + vertexId);
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
                LOG.warn("Failed to inject checkpoint barrier", e);
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
            LOG.warn("Failed to trigger final checkpoint", e);
        }
    }

    private static Map<String, Task> buildTasks(GraphExecutionPlan execPlan) {
        Map<String, Task> tasks = new LinkedHashMap<>();
        for (String vertexId : execPlan.getSortedVertexIds()) {
            JobVertex vertex = execPlan.getExecutionVertices().get(vertexId);
            tasks.put(vertexId, new Task(vertex, 0));
        }
        return tasks;
    }

    private static void submitAndRun(GraphExecutionPlan execPlan, Map<String, Task> tasks, TaskExecutor executor) throws InterruptedException {
        for (String vertexId : execPlan.getSortedVertexIds()) {
            executor.submitTask(tasks.get(vertexId));
        }
        executor.awaitCompletion();
    }

    private static void checkTaskFailures(Map<String, Task> tasks) {
        for (Task task : tasks.values()) {
            if (task.getState() == Task.State.FAILED) {
                throw new RuntimeException("Task failed", task.getError());
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
            throw new IllegalStateException(
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
            CheckpointPlan checkpointPlan) throws Exception {

        CompletedCheckpoint latestCheckpoint = coordinator.restoreFromCheckpoint();
        if (latestCheckpoint == null) {
            LOG.info("No recoverable checkpoint found, starting fresh");
            return;
        }

        LOG.info("Recovering from checkpoint {} (jobId={})",
                latestCheckpoint.getCheckpointId(), latestCheckpoint.getJobId());

        for (String vertexId : execPlan.getSortedVertexIds()) {
            StreamTaskInvokable invokable = execPlan.getInvokables().get(vertexId);
            if (invokable == null) continue;

            TaskLocation taskLocation = findTaskLocationForVertex(checkpointPlan, vertexId);
            TaskStateSnapshot taskState = latestCheckpoint.getTaskState(taskLocation);

            if (taskState == null && !latestCheckpoint.getTaskStates().isEmpty()) {
                LOG.warn("No exact task state found for vertex {}, falling back to first available", vertexId);
                taskState = latestCheckpoint.getTaskStates().values().iterator().next();
            }

            if (taskState == null) {
                LOG.warn("No task state found in checkpoint {} for vertex {}",
                        latestCheckpoint.getCheckpointId(), vertexId);
                continue;
            }

            List<OperatorStateMapping> mappings = checkpointPlan.getStateMappings(taskLocation);
            restoreOperatorsFromState(invokable.getOperatorChain(), taskState, mappings);
        }
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
            StreamTaskInvokable invokable = execPlan.getInvokables().get(vertexId);
            if (invokable == null) continue;

            TaskLocation taskLocation = findTaskLocationForVertex(checkpointPlan, vertexId);
            TaskStateSnapshot taskState = savepointCheckpoint.getTaskState(taskLocation);

            if (taskState == null && !savepointCheckpoint.getTaskStates().isEmpty()) {
                LOG.warn("No exact task state found for vertex {} in savepoint, falling back to first available", vertexId);
                taskState = savepointCheckpoint.getTaskStates().values().iterator().next();
            }

            if (taskState == null) {
                LOG.warn("No task state found in savepoint {} for vertex {}",
                        savepointCheckpoint.getCheckpointId(), vertexId);
                continue;
            }

            List<OperatorStateMapping> mappings = checkpointPlan.getStateMappings(taskLocation);
            restoreOperatorsFromState(invokable.getOperatorChain(), taskState, mappings);
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
                    byte[] opState = taskState.getOperatorState(mapping.getOperatorStateKey());
                    if (opState != null) {
                        builder.putOperatorState(mapping.getOperatorStateKey(), opState);
                        found = true;
                    }

                    if (mapping.hasKeyedState()) {
                        String keyedPrefix = mapping.getKeyedStateStorageKey();
                        for (Map.Entry<String, byte[]> entry : taskState.getKeyedStates().entrySet()) {
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
            byte[] opState = taskState.getOperatorState(opStateKey);
            if (opState != null) {
                builder.putOperatorState(opStateKey, opState);
                LOG.warn("Operator index {} not found in state mappings, using default key '{}'", operatorIndex, opStateKey);
            }

            for (Map.Entry<String, byte[]> entry : taskState.getKeyedStates().entrySet()) {
                builder.putKeyedState(entry.getKey(), entry.getValue());
            }
        }

        return builder.build();
    }
}
