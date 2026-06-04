/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamSourceOperator;

@Internal
public class CheckpointBarrierTracker {

    private static final Logger LOG = LoggerFactory.getLogger(CheckpointBarrierTracker.class);

    private final TaskLocation taskLocation;
    private final List<StreamOperator<?>> operators;
    private final List<OperatorStateMapping> stateMappings;
    private final Consumer<TaskStateSnapshot> completionCallback;

    private volatile long currentCheckpointId = -1;
    private final AtomicInteger operatorsToAck = new AtomicInteger(0);
    private volatile TaskStateSnapshot currentSnapshot;

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

        CheckpointBarrier barrier = new CheckpointBarrier(checkpointId, timestamp, type);

        if (!operators.isEmpty()) {
            StreamOperator<?> head = operators.get(0);
            if (head instanceof StreamSourceOperator) {
                // Source-pull: offer barrier to the operator's queue.
                // The source reading thread will poll and inject it during collect().
                boolean accepted = ((StreamSourceOperator<?>) head).offerBarrier(barrier);
                if (!accepted) {
                    LOG.warn("Checkpoint {} rejected: source operator already has a pending barrier", checkpointId);
                    return false;
                }
            }
        }

        return true;
    }

    public void acknowledgeOperator(int operatorIndex, OperatorSnapshotResult snapshot) {
        Consumer<TaskStateSnapshot> callbackToFire = null;
        TaskStateSnapshot snapshotToDeliver = null;

        synchronized (this) {
            TaskStateSnapshot snap = this.currentSnapshot;

            if (currentCheckpointId < 0 || snap == null) {
                LOG.debug("Ignoring duplicate/stale ACK from operator {} (no active checkpoint)", operatorIndex);
                return;
            }

            if (operatorsToAck.get() <= 0) {
                LOG.debug("Ignoring duplicate ACK from operator {} (already fully acknowledged)", operatorIndex);
                return;
            }

            if (snapshot != null) {
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

            if (operatorsToAck.decrementAndGet() == 0) {
                snapshotToDeliver = snap;
                callbackToFire = completionCallback;
            }
        }

        if (callbackToFire != null && snapshotToDeliver != null) {
            callbackToFire.accept(snapshotToDeliver);
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
