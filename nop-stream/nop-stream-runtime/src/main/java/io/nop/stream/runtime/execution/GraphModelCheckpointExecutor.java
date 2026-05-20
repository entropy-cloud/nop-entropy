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
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.execution.Task;
import io.nop.stream.core.execution.TaskExecutor;
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.AbstractUdfStreamOperator;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        JobVertex vertex = jobGraph.getVertices().values().iterator().next();

        Invokable<?> invokable = vertex.getInvokable();
        StreamTaskInvokable streamInvokable = null;
        if (invokable instanceof StreamTaskInvokable) {
            streamInvokable = (StreamTaskInvokable) invokable;
        }

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        ICheckpointStorage storage = createStorage(checkpointConfig);

        CheckpointCoordinator coordinator = new CheckpointCoordinator(
            jobId, pipelineId, idCounter, storage, checkpointConfig);

        coordinator.registerTask(0L);

        ScheduledExecutorService barrierScheduler = null;

        if (streamInvokable != null) {
            OperatorChain chain = vertex.getOperatorChains().get(0);
            List<StreamOperator<?>> operators = chain.getOperators();

            CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(
                0L, operators,
                snapshot -> coordinator.acknowledgeTask(0L, snapshot.getCheckpointId(), snapshot)
            );

            streamInvokable.setBarrierTracker(tracker);

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

            StreamOperator<?> head = operators.get(0);

            barrierScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "barrier-injector-" + jobId);
                t.setDaemon(true);
                return t;
            });

            final StreamTaskInvokable inv = streamInvokable;
            barrierScheduler.scheduleAtFixedRate(() -> {
                try {
                    PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
                    if (pending != null) {
                        CheckpointBarrier barrier = new CheckpointBarrier(
                            pending.getCheckpointId(),
                            pending.getTriggerTimestamp(),
                            CheckpointType.CHECKPOINT
                        );
                        inv.getBarrierTracker().triggerCheckpoint(
                            pending.getCheckpointId(),
                            pending.getTriggerTimestamp(),
                            CheckpointType.CHECKPOINT
                        );
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to inject checkpoint barrier", e);
                }
            }, checkpointConfig.getCheckpointInterval(),
               checkpointConfig.getCheckpointInterval(),
               TimeUnit.MILLISECONDS);
        }

        Task task = new Task(vertex, 0);
        TaskExecutor executor = new TaskExecutor();

        // Restore operator state from a previously completed checkpoint
        if (streamInvokable != null) {
            OperatorChain chain = streamInvokable.getOperatorChain();
            restoreOperatorsFromCheckpoint(coordinator, chain);
        }

        try {
            executor.submitTask(task);
            executor.awaitCompletion();

            if (streamInvokable != null && streamInvokable.getBarrierTracker() != null) {
                try {
                    PendingCheckpoint finalPending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.COMPLETED_POINT_TYPE);
                    if (finalPending != null) {
                        streamInvokable.getBarrierTracker().triggerCheckpoint(
                            finalPending.getCheckpointId(),
                            finalPending.getTriggerTimestamp(),
                            CheckpointType.COMPLETED_POINT_TYPE
                        );
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to trigger final checkpoint", e);
                }
            }

            if (task.getState() == Task.State.FAILED) {
                throw new RuntimeException("Task failed", task.getError());
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

        // Also restore the generic keyed state
        for (Map.Entry<String, byte[]> entry : taskState.getKeyedStates().entrySet()) {
            builder.putKeyedState(entry.getKey(), entry.getValue());
        }

        // Also restore generic operator state entries
        for (Map.Entry<String, byte[]> entry : taskState.getOperatorStates().entrySet()) {
            builder.putOperatorState(entry.getKey(), entry.getValue());
        }

        return builder.build();
    }
}
