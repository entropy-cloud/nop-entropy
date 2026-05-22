/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamSourceOperator;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Internal
public class CheckpointBarrierTracker {

    private final TaskLocation taskLocation;
    private final List<StreamOperator<?>> operators;
    private final List<OperatorStateMapping> stateMappings;
    private final Consumer<TaskStateSnapshot> completionCallback;

    private volatile long currentCheckpointId = -1;
    private final AtomicInteger operatorsToAck = new AtomicInteger(0);
    private volatile TaskStateSnapshot currentSnapshot;
    private volatile CheckpointBarrier pendingBarrier;

    public CheckpointBarrierTracker(TaskLocation taskLocation,
                                    List<StreamOperator<?>> operators,
                                    List<OperatorStateMapping> stateMappings,
                                    Consumer<TaskStateSnapshot> completionCallback) {
        this.taskLocation = taskLocation;
        this.operators = operators;
        this.stateMappings = stateMappings;
        this.completionCallback = completionCallback;
    }

    public CheckpointBarrierTracker(TaskLocation taskLocation,
                                    List<StreamOperator<?>> operators,
                                    Consumer<TaskStateSnapshot> completionCallback) {
        this(taskLocation, operators, Collections.emptyList(), completionCallback);
    }

    public synchronized boolean triggerCheckpoint(long checkpointId, long timestamp, CheckpointType type) throws Exception {
        if (operatorsToAck.get() > 0) {
            return false;
        }

        this.currentCheckpointId = checkpointId;
        this.currentSnapshot = new TaskStateSnapshot(taskLocation, checkpointId);

        int count = 0;
        for (StreamOperator<?> op : operators) {
            if (op instanceof AbstractStreamOperator) {
                count++;
            }
        }
        this.operatorsToAck.set(count);
        this.pendingBarrier = new CheckpointBarrier(checkpointId, timestamp, type);

        if (!operators.isEmpty()) {
            StreamOperator<?> head = operators.get(0);
            if (head instanceof StreamSourceOperator) {
                ((StreamSourceOperator<?>) head).setBarrierTracker(this);
                CheckpointBarrier barrier = this.pendingBarrier;
                if (barrier != null) {
                    this.pendingBarrier = null;
                    ((StreamSourceOperator<?>) head).injectBarrier(barrier);
                }
            }
        }

        return true;
    }

    public synchronized CheckpointBarrier pollPendingBarrier() {
        CheckpointBarrier barrier = this.pendingBarrier;
        if (barrier != null) {
            this.pendingBarrier = null;
        }
        return barrier;
    }

    public boolean hasPendingBarrier() {
        return pendingBarrier != null;
    }

    public void acknowledgeOperator(int operatorIndex, OperatorSnapshotResult snapshot) {
        TaskStateSnapshot snap = this.currentSnapshot;
        if (snapshot != null && snap != null) {
            String opStateKey = getOperatorStateKey(operatorIndex);
            if (snapshot.getOperatorStates() != null && !snapshot.getOperatorStates().isEmpty()) {
                for (Map.Entry<String, Object> entry : snapshot.getOperatorStates().entrySet()) {
                    snap.putOperatorState(opStateKey + "-" + entry.getKey(), entry.getValue());
                }
            }
            String keyedKey = getKeyedStateStorageKey(operatorIndex);
            if (keyedKey != null && snapshot.getKeyedStates() != null) {
                for (Map.Entry<String, Object> entry : snapshot.getKeyedStates().entrySet()) {
                    snap.putKeyedState(keyedKey + "-" + entry.getKey(), entry.getValue());
                }
            } else if (snapshot.getKeyedStates() != null) {
                for (Map.Entry<String, Object> entry : snapshot.getKeyedStates().entrySet()) {
                    snap.putKeyedState(entry.getKey(), entry.getValue());
                }
            }
        }

        if (operatorsToAck.decrementAndGet() <= 0) {
            if (completionCallback != null && snap != null) {
                completionCallback.accept(snap);
            }
        }
    }

    private String getOperatorStateKey(int operatorIndex) {
        if (stateMappings != null) {
            for (OperatorStateMapping mapping : stateMappings) {
                if (mapping.getOperatorIndex() == operatorIndex) {
                    return mapping.getOperatorStateKey();
                }
            }
        }
        return "operator-" + operatorIndex;
    }

    private String getKeyedStateStorageKey(int operatorIndex) {
        if (stateMappings != null) {
            for (OperatorStateMapping mapping : stateMappings) {
                if (mapping.getOperatorIndex() == operatorIndex) {
                    return mapping.hasKeyedState() ? mapping.getKeyedStateStorageKey() : null;
                }
            }
        }
        return null;
    }

    public TaskLocation getTaskLocation() {
        return taskLocation;
    }

    public long getTaskId() {
        return taskLocation != null ? taskLocation.getTaskIndex() : -1;
    }

    public long getCurrentCheckpointId() {
        return currentCheckpointId;
    }
}
