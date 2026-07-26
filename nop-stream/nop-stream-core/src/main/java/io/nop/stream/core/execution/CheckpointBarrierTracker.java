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
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.OperatorStateMapping;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
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
    /**
     * Error channel invoked when an operator snapshot reports
     * {@link OperatorSnapshotResult#hasError()}. Receives the checkpointId and the
     * cause so the coordinator can abort the matching {@link
     * io.nop.stream.runtime.checkpoint.PendingCheckpoint}. When set, the tracker
     * forwards the error to this callback instead of treating the failed ACK as a
     * successful snapshot (P1-11). Nullable for back-compat with test constructors
     * that do not inject an abort sink.
     */
    private final CheckpointFailureListener abortCallback;

    private volatile long currentCheckpointId = -1;
    private final AtomicInteger operatorsToAck = new AtomicInteger(0);
    private volatile TaskStateSnapshot currentSnapshot;

    public CheckpointBarrierTracker(TaskLocation taskLocation,
                                    List<StreamOperator<?>> operators,
                                    List<OperatorStateMapping> stateMappings,
                                    Consumer<TaskStateSnapshot> completionCallback) {
        this(taskLocation, operators, stateMappings, completionCallback, null);
    }

    public CheckpointBarrierTracker(TaskLocation taskLocation,
                                    List<StreamOperator<?>> operators,
                                    Consumer<TaskStateSnapshot> completionCallback) {
        this(taskLocation, operators, Collections.emptyList(), completionCallback, null);
    }

    /**
     * Full constructor with an explicit abort/error channel.
     *
     * <p>When {@code abortCallback} is non-null, an operator ACK whose
     * {@link OperatorSnapshotResult} carries an error routes the error through the
     * abort callback (and does NOT deliver {@code snapshotToDeliver} as a successful
     * snapshot). This closes the P1-11 silent-corruption path where snapshot failures
     * were treated as successful ACKs.
     */
    public CheckpointBarrierTracker(TaskLocation taskLocation,
                                    List<StreamOperator<?>> operators,
                                    List<OperatorStateMapping> stateMappings,
                                    Consumer<TaskStateSnapshot> completionCallback,
                                    CheckpointFailureListener abortCallback) {
        this.taskLocation = taskLocation;
        this.operators = operators;
        this.stateMappings = stateMappings;
        this.completionCallback = completionCallback;
        this.abortCallback = abortCallback;
    }

    public synchronized boolean triggerCheckpoint(long checkpointId, long timestamp, CheckpointType type) throws Exception {
        if (operatorsToAck.get() > 0) {
            return false;
        }

        long prevCheckpointId = this.currentCheckpointId;
        TaskStateSnapshot prevSnapshot = this.currentSnapshot;

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
                boolean accepted = ((StreamSourceOperator<?>) head).offerBarrier(barrier);
                if (!accepted) {
                    LOG.warn("Checkpoint {} rejected: source operator already has a pending barrier", checkpointId);
                    this.currentCheckpointId = prevCheckpointId;
                    this.currentSnapshot = prevSnapshot;
                    this.operatorsToAck.set(0);
                    return false;
                }
            }
        }

        return true;
    }

    public void acknowledgeOperator(int operatorIndex, OperatorSnapshotResult snapshot) {
        Consumer<TaskStateSnapshot> callbackToFire = null;
        TaskStateSnapshot snapshotToDeliver = null;
        long abortCheckpointId = -1L;
        Exception abortError = null;

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

            // P1-11: fail-fast on snapshot error. The tracker MUST NOT deliver a
            // failed snapshot as a successful ACK — that silently corrupted
            // checkpoint state. When abortCallback is wired, route the error to
            // the coordinator's abort entry; otherwise (legacy callers/tests) we
            // still refuse to deliver the snapshot and log the failure loudly so
            // it can never be silently swallowed (No-Silent-No-Op).
            if (snapshot != null && snapshot.hasError()) {
                abortError = snapshot.getError();
                abortCheckpointId = currentCheckpointId;
                LOG.error("Operator {} reported snapshot failure for checkpoint {} (abortError={})",
                        operatorIndex, abortCheckpointId,
                        abortError == null ? "n/a" : abortError.getMessage(), abortError);
                // Reset tracker state so the next trigger is accepted, mirroring
                // notifyCheckpointAborted semantics.
                this.currentCheckpointId = -1;
                this.currentSnapshot = null;
                this.operatorsToAck.set(0);
            } else if (snapshot != null) {
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

                if (operatorsToAck.decrementAndGet() == 0) {
                    snapshotToDeliver = snap;
                    callbackToFire = completionCallback;
                }
            } else {
                // snapshot == null with no error: treat as empty success ACK.
                if (operatorsToAck.decrementAndGet() == 0) {
                    snapshotToDeliver = snap;
                    callbackToFire = completionCallback;
                }
            }
        }

        if (abortError != null && abortCallback != null) {
            try {
                abortCallback.reportFailure(abortCheckpointId, abortError);
            } catch (Exception e) {
                LOG.error("abortCallback itself failed for checkpoint {} at operator {}",
                        abortCheckpointId, operatorIndex, e);
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

    /**
     * Called when a pending checkpoint is aborted. Resets the tracker state so that
     * the next checkpoint trigger is accepted, and releases any pending ACK wait.
     */
    public synchronized void notifyCheckpointAborted(long checkpointId) {
        if (this.currentCheckpointId == checkpointId) {
            LOG.debug("Tracker aborting checkpoint {} for task {}", checkpointId, taskLocation);
            this.currentCheckpointId = -1;
            this.currentSnapshot = null;
            this.operatorsToAck.set(0);
        }
    }
}
