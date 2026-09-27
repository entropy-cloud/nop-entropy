/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.ChannelState;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.OperatorStateMapping;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.TaskEpochSnapshot;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamSourceOperator;

/**
 * Tracks ACK state for {@code >= 1} in-flight checkpoints
 * simultaneously. Each in-flight epoch owns an independent {@link EpochAckState}
 * (ACK counter + snapshot), keyed by {@code checkpointId}. ACK routing is driven
 * by {@link OperatorSnapshotResult#getCheckpointId()} (design §2.8.1); when the
 * result carries no id (legacy/test callers), the tracker falls back to the
 * most-recently-triggered in-flight epoch (single-in-flight back-compat).
 */
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
     * successful snapshot. Nullable for back-compat with test constructors
     * that do not inject an abort sink.
     */
    private final CheckpointFailureListener abortCallback;

    /**
     * Per-epoch in-flight ACK tracking. LinkedHashMap preserves trigger
     * order so the "most-recent in-flight" fallback and channel-state attach
     * (single-in-flight unaligned) are well-defined.
     */
    private final Map<Long, EpochAckState> inFlight = new LinkedHashMap<>();

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
     * snapshot). This closes the silent-corruption path where snapshot failures
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

    /**
     * Registers a new in-flight epoch. Does not reject when another
     * checkpoint is in-flight — each epoch gets its own {@link EpochAckState}.
     * Concurrent in-flight count is bounded by the Coordinator-side
     * {@code maxConcurrentCheckpoints} gating (this tracker does not enforce a cap).
     *
     * @return {@code false} only if {@code checkpointId} is already in-flight
     *         (duplicate trigger) or the source operator rejects the barrier
     */
    public synchronized boolean triggerCheckpoint(long checkpointId, long timestamp, CheckpointType type) throws Exception {
        if (inFlight.containsKey(checkpointId)) {
            LOG.debug("Duplicate trigger for checkpoint {} (already in-flight); ignoring", checkpointId);
            return false;
        }

        int count = 0;
        for (StreamOperator<?> op : operators) {
            if (op instanceof AbstractStreamOperator) {
                count++;
            }
        }
        TaskStateSnapshot snapshot = new TaskStateSnapshot(taskLocation, checkpointId);
        inFlight.put(checkpointId, new EpochAckState(checkpointId, count, snapshot));

        CheckpointBarrier barrier = new CheckpointBarrier(checkpointId, timestamp, type);

        if (!operators.isEmpty()) {
            StreamOperator<?> head = operators.get(0);
            if (head instanceof StreamSourceOperator) {
                boolean accepted = ((StreamSourceOperator<?>) head).offerBarrier(barrier);
                if (!accepted) {
                    inFlight.remove(checkpointId);
                    LOG.warn("Checkpoint {} rejected: source operator rejected barrier", checkpointId);
                    return false;
                }
            } else if (head instanceof io.nop.stream.core.operators.SourceReaderOperator) {
                // FLIP-27 style source path — deliver the barrier via the
                // SourceReaderOperator's mailbox-driven handoff.
                boolean accepted = ((io.nop.stream.core.operators.SourceReaderOperator<?>) head)
                        .offerBarrier(barrier);
                if (!accepted) {
                    inFlight.remove(checkpointId);
                    LOG.warn("Checkpoint {} rejected: source reader operator rejected barrier", checkpointId);
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
            // Route the ACK to its epoch (by carried id, most-recent fallback).
            EpochAckState state = routeAckToEpoch(operatorIndex, snapshot);

            // Validate: stale/duplicate epoch, over-acknowledged epoch, and
            // per-operator duplicate ACKs are all ignored (logged, no state change).
            if (state == null) {
                LOG.debug("Ignoring stale/duplicate ACK from operator {} (cpId={}; no matching in-flight epoch)",
                        operatorIndex, (snapshot != null) ? snapshot.getCheckpointId() : -1L);
                return;
            }

            if (state.operatorsToAck.get() <= 0) {
                LOG.debug("Ignoring duplicate ACK from operator {} for checkpoint {} (already fully acknowledged)",
                        operatorIndex, state.checkpointId);
                return;
            }

            // De-duplicate ACKs PER OPERATOR. The counter
            // counts DISTINCT operators; a duplicate snapshot callback / at-least-once
            // redelivery for an already-acknowledged operator must be ignored —
            // otherwise N-1 real ACKs + 1 duplicate completed the epoch with a missing
            // operator snapshot (silent exactly-once break).
            if (!state.acknowledgedOperators.add(operatorIndex)) {
                LOG.debug("Ignoring duplicate ACK from operator {} for checkpoint {} (operator already acknowledged)",
                        operatorIndex, state.checkpointId);
                return;
            }

            // Fail-fast on snapshot error, routed to the correct epoch.
            if (snapshot != null && snapshot.hasError()) {
                abortError = snapshot.getError();
                abortCheckpointId = state.checkpointId;
                LOG.error("Operator {} reported snapshot failure for checkpoint {} (abortError={})",
                        operatorIndex, abortCheckpointId,
                        abortError == null ? "n/a" : abortError.getMessage(), abortError);
                // Remove ONLY this epoch (mirrors notifyCheckpointAborted semantics).
                inFlight.remove(state.checkpointId);
            } else {
                // Successful ACK — a real snapshot, or null (= empty success ACK).
                if (snapshot != null) {
                    migrateOperatorStates(state, operatorIndex, snapshot);
                }
                callbackToFire = completeEpochIfLastAck(state);
                if (callbackToFire != null) {
                    snapshotToDeliver = state.snapshot;
                }
            }
        }

        fireAckCallbacks(operatorIndex, abortCheckpointId, abortError, callbackToFire, snapshotToDeliver);
    }

    /**
     * Routes an operator ACK to its in-flight epoch.
     *
     * <p>Route by the checkpoint id carried on the result (design
     * §2.8.1). Valid ids are &gt;= 0 (the coordinator's counter starts at 0);
     * -1 means unset (legacy caller that did not tag the result) — those route to
     * the most-recently-triggered in-flight epoch. The fallback is only correct
     * for the single-in-flight legacy contract; production snapshotState always
     * tags the result (design §2.8.1 D2).
     *
     * @return the owning epoch, or {@code null} when no epoch matches
     */
    private EpochAckState routeAckToEpoch(int operatorIndex, OperatorSnapshotResult snapshot) {
        long cpId = (snapshot != null) ? snapshot.getCheckpointId() : -1L;
        if (cpId >= 0) {
            return inFlight.get(cpId);
        }
        EpochAckState state = mostRecentInFlight();
        if (state != null && inFlight.size() > 1) {
            LOG.warn("ACK from operator {} carries no checkpointId while {} epochs are in-flight; "
                    + "routing to most-recent epoch {} (ambiguous — production path should tag the result)",
                    operatorIndex, inFlight.size(), state.checkpointId);
        }
        return state;
    }

    /**
     * Migrates an operator snapshot's states into the owning epoch's
     * {@link TaskStateSnapshot}. Operator states are namespaced under the
     * operator's state key; keyed states under the operator's keyed-state storage
     * key when one is mapped, under their raw keys otherwise.
     */
    private void migrateOperatorStates(EpochAckState state, int operatorIndex,
                                       OperatorSnapshotResult snapshot) {
        TaskStateSnapshot snap = state.snapshot;
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

    /**
     * Decrements the epoch's distinct-operator ACK counter and, when the last
     * operator has acknowledged, removes the epoch from the in-flight map.
     * Merged tail of the previously duplicated completion blocks (real snapshot
     * and empty-success ACK paths completed the epoch identically).
     *
     * @return the completion callback to fire, or {@code null} while the epoch
     *         stays in-flight (or when no completion callback is configured)
     */
    private Consumer<TaskStateSnapshot> completeEpochIfLastAck(EpochAckState state) {
        if (state.operatorsToAck.decrementAndGet() != 0) {
            return null;
        }
        inFlight.remove(state.checkpointId);
        return completionCallback;
    }

    /**
     * Fires the post-ACK callbacks OUTSIDE the tracker lock: the abort channel
     * for a failed snapshot first (a failing abort callback is logged and
     * must not prevent the completion callback), then the epoch completion
     * callback.
     */
    private void fireAckCallbacks(int operatorIndex, long abortCheckpointId, Exception abortError,
                                  Consumer<TaskStateSnapshot> callbackToFire,
                                  TaskStateSnapshot snapshotToDeliver) {
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

    /**
     * Returns the highest in-flight checkpoint id, or {@code -1} when no
     * checkpoint is in-flight. Preserves the legacy single-in-flight contract used
     * by existing tests (id of the active epoch, -1 after completion/abort/error).
     */
    public long getCurrentCheckpointId() {
        long max = -1L;
        for (Long id : inFlight.keySet()) {
            if (id > max) {
                max = id;
            }
        }
        return max;
    }

    /**
     * Whether any checkpoint is currently in-flight (used by the
     * epoch-aware abort handler to decide whether to cancel the task thread).
     */
    public synchronized boolean hasInFlightCheckpoints() {
        return !inFlight.isEmpty();
    }

    /**
     * Snapshot of the currently in-flight checkpoint ids (for tests and
     * observability). Order is trigger order.
     */
    public synchronized List<Long> getInFlightCheckpointIds() {
        return new java.util.ArrayList<>(inFlight.keySet());
    }

    /**
     * Aborts a specific epoch ONLY. Removes that epoch's ACK tracking
     * without disturbing other in-flight epochs (epoch-precise abort, design
     * §2.8.1). Other pending epochs continue to receive ACKs normally.
     */
    public synchronized void notifyCheckpointAborted(long checkpointId) {
        EpochAckState removed = inFlight.remove(checkpointId);
        if (removed != null) {
            LOG.debug("Tracker aborting checkpoint {} for task {} ({} epoch(s) still in-flight)",
                    checkpointId, taskLocation, inFlight.size());
        }
    }

    /**
     * Unaligned checkpoint: attaches channel state to the current
     * in-flight snapshot. Channel state flows on the <em>barrier ACK path</em>
     * (not {@code triggerCheckpoint()}, which runs at initiation before channel
     * state exists): the task thread receives the unaligned barrier from
     * {@code InputGate.read()}, pulls the captured {@link ChannelState} off the
     * gate, and hands it here so it is persisted within the {@link TaskEpochSnapshot}.
     *
     * <p>Attaches to the most-recently-triggered in-flight epoch.
     * Unaligned stays single-in-flight (design §2.8.1), so when channel state
     * arrives there is exactly one in-flight epoch.
     *
     * <p>No-op when there is no active checkpoint or when the channel state is
     * null/empty. Promotes the snapshot to a {@link TaskEpochSnapshot} if needed
     * so the channel-state field is available.
     */
    public synchronized void setChannelState(ChannelState channelState) {
        EpochAckState state = mostRecentInFlight();
        if (state == null || channelState == null || channelState.isEmpty()) {
            return;
        }
        TaskStateSnapshot snap = state.snapshot;
        TaskEpochSnapshot epoch = TaskEpochSnapshot.fromTaskStateSnapshot(snap);
        epoch.setChannelState(channelState);
        if (snap != epoch) {
            state.snapshot = epoch;
        }
    }

    private EpochAckState mostRecentInFlight() {
        EpochAckState last = null;
        for (EpochAckState s : inFlight.values()) {
            last = s;
        }
        return last;
    }

    /**
     * Per-epoch ACK tracking entry. Each in-flight checkpoint owns an
     * independent counter and snapshot so ACKs for different epochs never pollute
     * each other.
     *
     * <p>{@code acknowledgedOperators} records WHICH
     * operators have already ACKed this epoch — the completion counter counts
     * distinct operators only, so a duplicate ACK never double-decrements it.
     */
    private static final class EpochAckState {
        final long checkpointId;
        final AtomicInteger operatorsToAck;
        final java.util.Set<Integer> acknowledgedOperators = new java.util.HashSet<>();
        TaskStateSnapshot snapshot; // mutable: channel-state promotion may replace it

        EpochAckState(long checkpointId, int operatorsToAck, TaskStateSnapshot snapshot) {
            this.checkpointId = checkpointId;
            this.operatorsToAck = new AtomicInteger(operatorsToAck);
            this.snapshot = snapshot;
        }
    }
}
