/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamSourceOperator;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Tracks checkpoint barriers through a single-chain pipeline.
 *
 * <p>When a checkpoint is triggered, this tracker injects a barrier into the source operator.
 * As the barrier propagates through each operator, operators snapshot their state and report
 * back via {@link #acknowledgeOperator(int, OperatorSnapshotResult)}. When all operators have
 * acknowledged, the tracker invokes the completion callback with the aggregated
 * {@link TaskStateSnapshot}.
 *
 * <p>This class bridges the checkpoint coordinator (in runtime module) with the execution
 * engine (in core module) without creating a direct dependency between them.
 *
 * @see io.nop.stream.core.operators.AbstractStreamOperator#processBarrier
 */
@Internal
public class CheckpointBarrierTracker {

    private final long taskId;
    private final List<StreamOperator<?>> operators;
    private final Consumer<TaskStateSnapshot> completionCallback;

    private long currentCheckpointId = -1;
    private int operatorsToAck = 0;
    private TaskStateSnapshot currentSnapshot;

    public CheckpointBarrierTracker(long taskId,
                                    List<StreamOperator<?>> operators,
                                    Consumer<TaskStateSnapshot> completionCallback) {
        this.taskId = taskId;
        this.operators = operators;
        this.completionCallback = completionCallback;
    }

    /**
     * Triggers a checkpoint by injecting a barrier into the source operator.
     *
     * @param checkpointId the checkpoint ID
     * @param timestamp    the checkpoint timestamp
     * @param type         the checkpoint type
     */
    public void triggerCheckpoint(long checkpointId, long timestamp, CheckpointType type) throws Exception {
        this.currentCheckpointId = checkpointId;
        this.currentSnapshot = new TaskStateSnapshot(taskId, checkpointId);
        this.operatorsToAck = 0;

        // Count operators that need to ACK
        for (StreamOperator<?> op : operators) {
            if (op instanceof AbstractStreamOperator) {
                operatorsToAck++;
            }
        }

        // Inject barrier into source
        StreamOperator<?> head = operators.get(0);
        if (head instanceof StreamSourceOperator) {
            CheckpointBarrier barrier = new CheckpointBarrier(checkpointId, timestamp, type);
            ((StreamSourceOperator<?>) head).injectBarrier(barrier);
        }
    }

    /**
     * Called by an operator when it has completed its snapshot.
     *
     * @param operatorIndex the index of the operator in the chain
     * @param snapshot       the snapshot result from the operator
     */
    public void acknowledgeOperator(int operatorIndex, OperatorSnapshotResult snapshot) {
        if (snapshot != null) {
            String stateKey = "operator-" + operatorIndex;
            if (snapshot.getOperatorStates() != null && !snapshot.getOperatorStates().isEmpty()) {
                for (Map.Entry<String, byte[]> entry : snapshot.getOperatorStates().entrySet()) {
                    currentSnapshot.getOperatorStates().put(stateKey + "-" + entry.getKey(), entry.getValue());
                }
            }
            if (snapshot.getKeyedStates() != null) {
                for (Map.Entry<String, byte[]> entry : snapshot.getKeyedStates().entrySet()) {
                    currentSnapshot.getKeyedStates().put(entry.getKey(), entry.getValue());
                }
            }
        }

        operatorsToAck--;
        if (operatorsToAck <= 0) {
            // All operators acknowledged
            if (completionCallback != null) {
                completionCallback.accept(currentSnapshot);
            }
        }
    }

    public long getTaskId() {
        return taskId;
    }

    public long getCurrentCheckpointId() {
        return currentCheckpointId;
    }
}
