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
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
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

/**
 * Executes a graph model pipeline with checkpoint support.
 * Bridges the core execution engine with the runtime checkpoint coordinator.
 *
 * <p>Since {@code nop-stream-core} cannot depend on {@code nop-stream-runtime},
 * this class lives in the runtime module and wires the {@link CheckpointCoordinator}
 * (runtime) to the {@link CheckpointBarrierTracker} (core).
 */
@Internal
public class GraphModelCheckpointExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(GraphModelCheckpointExecutor.class);

    public static StreamExecutionResult executeWithCheckpoint(
            JobGraph jobGraph,
            String jobName,
            CheckpointConfig checkpointConfig) throws Exception {

        long startTime = System.currentTimeMillis();
        long jobId = 1L;
        int pipelineId = 1;

        GraphExecutionPlan plan = GraphExecutionPlan.build(jobGraph);

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        ICheckpointStorage storage = createStorage(checkpointConfig);

        CheckpointCoordinator coordinator = new CheckpointCoordinator(
            jobId, pipelineId, idCounter, storage, checkpointConfig);

        List<StreamTaskInvokable> allInvokables = new ArrayList<>();

        for (String vertexId : plan.getSortedVertexIds()) {
            StreamTaskInvokable invokable = plan.getInvokables().get(vertexId);
            allInvokables.add(invokable);

            long taskId = (long) allInvokables.size() - 1;
            coordinator.registerTask(taskId);

            JobVertex execVertex = plan.getExecutionVertices().get(vertexId);
            OperatorChain chain = execVertex.getOperatorChains().get(0);
            List<StreamOperator<?>> operators = chain.getOperators();

            final long tid = taskId;
            CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(
                tid, operators,
                snapshot -> coordinator.acknowledgeTask(tid, snapshot.getCheckpointId(), snapshot)
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

        ScheduledExecutorService barrierScheduler = null;

        if (!allInvokables.isEmpty()) {
            barrierScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "barrier-injector-" + jobId);
                t.setDaemon(true);
                return t;
            });

            barrierScheduler.scheduleAtFixedRate(() -> {
                try {
                    PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
                    if (pending != null) {
                        for (StreamTaskInvokable inv : allInvokables) {
                            if (inv.getBarrierTracker() != null) {
                                inv.getBarrierTracker().triggerCheckpoint(
                                    pending.getCheckpointId(),
                                    pending.getTriggerTimestamp(),
                                    CheckpointType.CHECKPOINT
                                );
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to inject checkpoint barrier", e);
                }
            }, checkpointConfig.getCheckpointInterval(),
               checkpointConfig.getCheckpointInterval(),
               TimeUnit.MILLISECONDS);
        }

        Map<String, Task> tasks = new LinkedHashMap<>();
        for (String vertexId : plan.getSortedVertexIds()) {
            JobVertex vertex = plan.getExecutionVertices().get(vertexId);
            tasks.put(vertexId, new Task(vertex, 0));
        }

        for (String vertexId : plan.getSortedVertexIds()) {
            StreamTaskInvokable invokable = plan.getInvokables().get(vertexId);
            if (invokable != null) {
                OperatorChain chain = invokable.getOperatorChain();
                restoreOperatorsFromCheckpoint(coordinator, chain);
            }
        }

        TaskExecutor executor = new TaskExecutor();

        try {
            for (String vertexId : plan.getSortedVertexIds()) {
                executor.submitTask(tasks.get(vertexId));
            }

            executor.awaitCompletion();

            if (!allInvokables.isEmpty()) {
                try {
                    PendingCheckpoint finalPending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.COMPLETED_POINT_TYPE);
                    if (finalPending != null) {
                        for (StreamTaskInvokable inv : allInvokables) {
                            if (inv.getBarrierTracker() != null) {
                                inv.getBarrierTracker().triggerCheckpoint(
                                    finalPending.getCheckpointId(),
                                    finalPending.getTriggerTimestamp(),
                                    CheckpointType.COMPLETED_POINT_TYPE
                                );
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to trigger final checkpoint", e);
                }
            }

            for (Task task : tasks.values()) {
                if (task.getState() == Task.State.FAILED) {
                    throw new RuntimeException("Task failed", task.getError());
                }
            }

            long executionTime = System.currentTimeMillis() - startTime;
            return new StreamExecutionResult(jobName, executionTime);
        } finally {
            if (barrierScheduler != null) {
                barrierScheduler.shutdownNow();
            }
            coordinator.shutdown();
        }
    }

    private static ICheckpointStorage createStorage(CheckpointConfig config) {
        String basePath = config.getStorageProperty("path");
        if (basePath == null || basePath.isEmpty()) {
            basePath = System.getProperty("java.io.tmpdir") + "/nop-stream-checkpoints";
        }
        return new LocalFileCheckpointStorage(basePath);
    }

    public static String triggerSavepoint(
            JobGraph jobGraph,
            CheckpointConfig checkpointConfig,
            String targetPath) throws Exception {

        long jobId = 1L;
        int pipelineId = 1;

        GraphExecutionPlan plan = GraphExecutionPlan.build(jobGraph);

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        ICheckpointStorage storage = createStorage(checkpointConfig);
        if (targetPath != null && !targetPath.isEmpty()) {
            storage = new LocalFileCheckpointStorage(targetPath);
        }

        CheckpointCoordinator coordinator = new CheckpointCoordinator(
                jobId, pipelineId, idCounter, storage, checkpointConfig);

        List<StreamTaskInvokable> allInvokables = new ArrayList<>();

        for (String vertexId : plan.getSortedVertexIds()) {
            StreamTaskInvokable invokable = plan.getInvokables().get(vertexId);
            allInvokables.add(invokable);

            long taskId = (long) allInvokables.size() - 1;
            coordinator.registerTask(taskId);

            JobVertex execVertex = plan.getExecutionVertices().get(vertexId);
            OperatorChain chain = execVertex.getOperatorChains().get(0);
            List<StreamOperator<?>> operators = chain.getOperators();

            final long tid = taskId;
            CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(
                    tid, operators,
                    snapshot -> coordinator.acknowledgeTask(tid, snapshot.getCheckpointId(), snapshot)
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

        ScheduledExecutorService barrierScheduler = null;

        if (!allInvokables.isEmpty()) {
            barrierScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "barrier-injector-savepoint-" + jobId);
                t.setDaemon(true);
                return t;
            });

            barrierScheduler.scheduleAtFixedRate(() -> {
                try {
                    PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
                    if (pending != null) {
                        for (StreamTaskInvokable inv : allInvokables) {
                            if (inv.getBarrierTracker() != null) {
                                inv.getBarrierTracker().triggerCheckpoint(
                                        pending.getCheckpointId(),
                                        pending.getTriggerTimestamp(),
                                        CheckpointType.CHECKPOINT
                                );
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to inject checkpoint barrier", e);
                }
            }, checkpointConfig.getCheckpointInterval(),
                    checkpointConfig.getCheckpointInterval(),
                    TimeUnit.MILLISECONDS);
        }

        Map<String, Task> tasks = new LinkedHashMap<>();
        for (String vertexId : plan.getSortedVertexIds()) {
            JobVertex vertex = plan.getExecutionVertices().get(vertexId);
            tasks.put(vertexId, new Task(vertex, 0));
        }

        TaskExecutor executor = new TaskExecutor();

        try {
            for (String vertexId : plan.getSortedVertexIds()) {
                executor.submitTask(tasks.get(vertexId));
            }

            executor.awaitCompletion();

            PendingCheckpoint savepointPending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.SAVEPOINT);
            String savepointPath = null;
            if (savepointPending != null) {
                for (StreamTaskInvokable inv : allInvokables) {
                    if (inv.getBarrierTracker() != null) {
                        inv.getBarrierTracker().triggerCheckpoint(
                                savepointPending.getCheckpointId(),
                                savepointPending.getTriggerTimestamp(),
                                CheckpointType.SAVEPOINT
                        );
                    }
                }

                CompletedCheckpoint completed = (CompletedCheckpoint) savepointPending.getCompletableFuture()
                        .get(checkpointConfig.getCheckpointTimeout(), TimeUnit.MILLISECONDS);
                if (completed != null) {
                    savepointPath = storage.storeCheckPoint(completed);
                }
            }

            for (Task task : tasks.values()) {
                if (task.getState() == Task.State.FAILED) {
                    throw new RuntimeException("Task failed", task.getError());
                }
            }

            return savepointPath;
        } finally {
            if (barrierScheduler != null) {
                barrierScheduler.shutdownNow();
            }
            coordinator.shutdown();
        }
    }

    public static StreamExecutionResult executeWithSavepoint(
            JobGraph jobGraph,
            String jobName,
            CheckpointConfig checkpointConfig,
            String savepointPath) throws Exception {

        long startTime = System.currentTimeMillis();
        long jobId = 1L;
        int pipelineId = 1;

        GraphExecutionPlan plan = GraphExecutionPlan.build(jobGraph);

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        ICheckpointStorage storage = createStorage(checkpointConfig);

        CheckpointCoordinator coordinator = new CheckpointCoordinator(
                jobId, pipelineId, idCounter, storage, checkpointConfig);

        List<StreamTaskInvokable> allInvokables = new ArrayList<>();

        for (String vertexId : plan.getSortedVertexIds()) {
            StreamTaskInvokable invokable = plan.getInvokables().get(vertexId);
            allInvokables.add(invokable);

            long taskId = (long) allInvokables.size() - 1;
            coordinator.registerTask(taskId);

            JobVertex execVertex = plan.getExecutionVertices().get(vertexId);
            OperatorChain chain = execVertex.getOperatorChains().get(0);
            List<StreamOperator<?>> operators = chain.getOperators();

            final long tid = taskId;
            CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(
                    tid, operators,
                    snapshot -> coordinator.acknowledgeTask(tid, snapshot.getCheckpointId(), snapshot)
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

        ScheduledExecutorService barrierScheduler = null;

        if (!allInvokables.isEmpty()) {
            barrierScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "barrier-injector-" + jobId);
                t.setDaemon(true);
                return t;
            });

            barrierScheduler.scheduleAtFixedRate(() -> {
                try {
                    PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
                    if (pending != null) {
                        for (StreamTaskInvokable inv : allInvokables) {
                            if (inv.getBarrierTracker() != null) {
                                inv.getBarrierTracker().triggerCheckpoint(
                                        pending.getCheckpointId(),
                                        pending.getTriggerTimestamp(),
                                        CheckpointType.CHECKPOINT
                                );
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to inject checkpoint barrier", e);
                }
            }, checkpointConfig.getCheckpointInterval(),
                    checkpointConfig.getCheckpointInterval(),
                    TimeUnit.MILLISECONDS);
        }

        if (savepointPath != null && !savepointPath.isEmpty()) {
            for (String vertexId : plan.getSortedVertexIds()) {
                StreamTaskInvokable invokable = plan.getInvokables().get(vertexId);
                if (invokable != null) {
                    restoreOperatorsFromSavepointPath(
                            storage, invokable.getOperatorChain(), savepointPath);
                }
            }
        }

        Map<String, Task> tasks = new LinkedHashMap<>();
        for (String vertexId : plan.getSortedVertexIds()) {
            JobVertex vertex = plan.getExecutionVertices().get(vertexId);
            tasks.put(vertexId, new Task(vertex, 0));
        }

        TaskExecutor executor = new TaskExecutor();

        try {
            for (String vertexId : plan.getSortedVertexIds()) {
                executor.submitTask(tasks.get(vertexId));
            }

            executor.awaitCompletion();

            if (!allInvokables.isEmpty()) {
                try {
                    PendingCheckpoint finalPending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.COMPLETED_POINT_TYPE);
                    if (finalPending != null) {
                        for (StreamTaskInvokable inv : allInvokables) {
                            if (inv.getBarrierTracker() != null) {
                                inv.getBarrierTracker().triggerCheckpoint(
                                        finalPending.getCheckpointId(),
                                        finalPending.getTriggerTimestamp(),
                                        CheckpointType.COMPLETED_POINT_TYPE
                                );
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to trigger final checkpoint", e);
                }
            }

            for (Task task : tasks.values()) {
                if (task.getState() == Task.State.FAILED) {
                    throw new RuntimeException("Task failed", task.getError());
                }
            }

            long executionTime = System.currentTimeMillis() - startTime;
            return new StreamExecutionResult(jobName, executionTime);
        } finally {
            if (barrierScheduler != null) {
                barrierScheduler.shutdownNow();
            }
            coordinator.shutdown();
        }
    }

    private static void restoreOperatorsFromSavepointPath(
            ICheckpointStorage defaultStorage,
            OperatorChain chain,
            String savepointPath) throws Exception {
        if (chain == null) {
            return;
        }

        ICheckpointStorage savepointStorage = new LocalFileCheckpointStorage(savepointPath);
        CompletedCheckpoint savepointCheckpoint = savepointStorage.getLatestCheckpoint(1L, 1);
        if (savepointCheckpoint == null) {
            java.nio.file.Path path = java.nio.file.Paths.get(savepointPath);
            if (java.nio.file.Files.exists(path) && savepointPath.endsWith(".checkpoint")) {
                java.nio.file.Path parentDir = path.getParent();
                String parentPath = parentDir != null ? parentDir.toString() : savepointPath;
                ICheckpointStorage parentStorage = new LocalFileCheckpointStorage(parentPath);
                savepointCheckpoint = parentStorage.getLatestCheckpoint(1L, 1);
            }
        }

        if (savepointCheckpoint == null) {
            LOG.info("No recoverable savepoint found at path {}, starting fresh", savepointPath);
            return;
        }

        LOG.info("Recovering from savepoint {} (jobId={})",
                savepointCheckpoint.getCheckpointId(), savepointCheckpoint.getJobId());

        List<StreamOperator<?>> operators = chain.getOperators();
        long taskIndex = 0L;
        TaskStateSnapshot taskState = savepointCheckpoint.getTaskState(taskIndex);

        if (taskState == null && !savepointCheckpoint.getTaskStates().isEmpty()) {
            Map.Entry<Long, TaskStateSnapshot> firstEntry =
                    savepointCheckpoint.getTaskStates().entrySet().iterator().next();
            taskState = firstEntry.getValue();
        }

        if (taskState == null) {
            LOG.warn("No task state found in savepoint {}", savepointCheckpoint.getCheckpointId());
            return;
        }

        for (int i = 0; i < operators.size(); i++) {
            StreamOperator<?> op = operators.get(i);
            if (op instanceof AbstractStreamOperator) {
                OperatorSnapshotResult opResult = buildSnapshotFromTaskState(taskState, i);
                try {
                    ((AbstractStreamOperator<?>) op).restoreState(opResult);
                    LOG.debug("Restored state for operator index {} from savepoint", i);
                } catch (Exception e) {
                    LOG.error("Failed to restore state for operator index {} from savepoint", i, e);
                    throw e;
                }
            }
        }
    }

    private static void restoreOperatorsFromCheckpoint(
            CheckpointCoordinator coordinator, OperatorChain chain) throws Exception {
        if (chain == null) {
            return;
        }

        CompletedCheckpoint latestCheckpoint = coordinator.restoreFromCheckpoint();
        if (latestCheckpoint == null) {
            LOG.info("No recoverable checkpoint found, starting fresh");
            return;
        }

        LOG.info("Recovering from checkpoint {} (jobId={})",
                latestCheckpoint.getCheckpointId(), latestCheckpoint.getJobId());

        List<StreamOperator<?>> operators = chain.getOperators();
        long taskIndex = 0L;
        TaskStateSnapshot taskState = latestCheckpoint.getTaskState(taskIndex);

        if (taskState == null && !latestCheckpoint.getTaskStates().isEmpty()) {
            Map.Entry<Long, TaskStateSnapshot> firstEntry =
                    latestCheckpoint.getTaskStates().entrySet().iterator().next();
            taskState = firstEntry.getValue();
        }

        if (taskState == null) {
            LOG.warn("No task state found in checkpoint {}", latestCheckpoint.getCheckpointId());
            return;
        }

        for (int i = 0; i < operators.size(); i++) {
            StreamOperator<?> op = operators.get(i);
            if (op instanceof AbstractStreamOperator) {
                OperatorSnapshotResult opResult = buildSnapshotFromTaskState(taskState, i);
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

    private static OperatorSnapshotResult buildSnapshotFromTaskState(
            TaskStateSnapshot taskState, int operatorIndex) {
        OperatorSnapshotResult.Builder builder = OperatorSnapshotResult.builder();

        String opStateKey = "operator-" + operatorIndex;
        byte[] opState = taskState.getOperatorState(opStateKey);
        if (opState != null) {
            builder.putOperatorState(opStateKey, opState);
        }

        for (Map.Entry<String, byte[]> entry : taskState.getKeyedStates().entrySet()) {
            builder.putKeyedState(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, byte[]> entry : taskState.getOperatorStates().entrySet()) {
            builder.putOperatorState(entry.getKey(), entry.getValue());
        }

        return builder.build();
    }
}
