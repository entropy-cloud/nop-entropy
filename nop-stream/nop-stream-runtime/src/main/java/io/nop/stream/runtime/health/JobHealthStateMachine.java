/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.health;

import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_ILLEGAL_HEALTH_TRANSITION;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Item 16 (P-REQ-7): logical health state machine driven by REAL lifecycle
 * events (JobCoordinator start / globalRecovery / failJob / terminate and the
 * durable-checkpoint completion callback) — never by timers or inference.
 *
 * <p>Transition legality table (observability-design §3.6; the authoritative
 * machine form):
 * <ul>
 *   <li>CREATED → RUNNING (start)</li>
 *   <li>RUNNING → RECOVERING | FAILED | CANCELED | FINISHED</li>
 *   <li>RECOVERING → DEGRADED | FAILED (restart cap exhausted)</li>
 *   <li>DEGRADED → RUNNING (next durable checkpoint) | RECOVERING | CANCELED | FINISHED</li>
 *   <li>FAILED / CANCELED / FINISHED — terminal, no outgoing transitions</li>
 * </ul>
 *
 * <p>Illegal transitions (including self-transitions) fail fast with
 * {@link IllegalStateException} — an explicit exception, never a silent
 * ignore (plan guide #24). Every legal transition is logged as
 * {@code job health transition: ...} (the cross-JVM observable evidence used
 * by the multi-JVM e2e) and dispatched to all registered
 * {@link JobHealthListener}s; listener exceptions are logged and swallowed
 * (an observability listener must never break the control path).
 *
 * <p>Thread-safety: transitions are synchronized; listeners are held in a
 * copy-on-write list so registration never races an in-flight transition.
 */
public final class JobHealthStateMachine {

    private static final Logger LOG = LoggerFactory.getLogger(JobHealthStateMachine.class);

    private static final Map<StreamJobHealth, Set<StreamJobHealth>> ALLOWED = Map.of(
            StreamJobHealth.CREATED, Set.of(StreamJobHealth.RUNNING),
            StreamJobHealth.RUNNING, Set.of(StreamJobHealth.RECOVERING, StreamJobHealth.FAILED,
                    StreamJobHealth.CANCELED, StreamJobHealth.FINISHED),
            StreamJobHealth.RECOVERING, Set.of(StreamJobHealth.DEGRADED, StreamJobHealth.FAILED),
            StreamJobHealth.DEGRADED, Set.of(StreamJobHealth.RUNNING, StreamJobHealth.RECOVERING,
                    StreamJobHealth.CANCELED, StreamJobHealth.FINISHED),
            StreamJobHealth.FAILED, Set.of(),
            StreamJobHealth.CANCELED, Set.of(),
            StreamJobHealth.FINISHED, Set.of());

    private final String jobId;
    private final List<JobHealthListener> listeners = new CopyOnWriteArrayList<>();

    private volatile StreamJobHealth current = StreamJobHealth.CREATED;

    public JobHealthStateMachine(String jobId) {
        this.jobId = jobId;
    }

    public StreamJobHealth getCurrent() {
        return current;
    }

    public static boolean isTransitionAllowed(StreamJobHealth from, StreamJobHealth to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public void addListener(JobHealthListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(JobHealthListener listener) {
        listeners.remove(listener);
    }

    public int getListenerCount() {
        return listeners.size();
    }

    /**
     * Applies a transition, enforcing the legality table.
     *
     * @throws IllegalStateException when {@code from -> to} is not in the
     *                               legality table (fail-fast, no silent ignore)
     */
    public synchronized StreamJobHealth transition(StreamJobHealth to, String cause) {
        StreamJobHealth from = current;
        if (from == to || !isTransitionAllowed(from, to)) {
            throw new StreamException(ERR_STREAM_ILLEGAL_HEALTH_TRANSITION).param(ARG_DETAIL, "Illegal health transition for job '" + jobId + "': "
                    + from + " -> " + to + " (cause=" + cause + "). Allowed from " + from + ": "
                    + ALLOWED.get(from));
        }
        current = to;
        LOG.info("job health transition: job={} {} -> {} (cause={})", jobId, from, to, cause);
        for (JobHealthListener listener : listeners) {
            try {
                listener.onHealthTransition(jobId, from, to, cause);
            } catch (Exception e) {
                LOG.warn("health listener {} failed on transition {} -> {} for job {}: {}",
                        listener.getClass().getName(), from, to, jobId, e.toString(), e);
            }
        }
        return to;
    }

    // ==================== lifecycle event entry points ====================

    /** JobCoordinator.start(): CREATED → RUNNING. */
    public void onStart() {
        transition(StreamJobHealth.RUNNING, "start");
    }

    /** globalRecovery() entry: RUNNING|DEGRADED → RECOVERING. */
    public void onRecoveryStarted(int attempt) {
        transition(StreamJobHealth.RECOVERING, "globalRecovery#" + attempt);
    }

    /**
     * globalRecovery() success exit: RECOVERING → DEGRADED. A recovery always
     * follows a failure (restart count &gt; 0), so the post-recovery state
     * carries the failure trace until the next durable checkpoint heals it.
     */
    public void onRecoveryCompleted(int restartCount) {
        transition(StreamJobHealth.DEGRADED, "recovery-completed(restarts=" + restartCount + ")");
    }

    /**
     * Durable-checkpoint completion callback (real CheckpointCoordinator
     * completion path): heals DEGRADED → RUNNING. In every other state a
     * durable checkpoint is not a health event — a job already RUNNING needs
     * no healing, and CREATED/RECOVERING/terminal states have no
     * checkpoint-driven transition in the legality table — so the current
     * state is returned unchanged (designed no-op, logged at debug).
     */
    public synchronized StreamJobHealth onDurableCheckpoint(long checkpointId) {
        if (current == StreamJobHealth.DEGRADED) {
            return transition(StreamJobHealth.RUNNING, "durable-checkpoint-" + checkpointId);
        }
        LOG.debug("durable checkpoint {} does not change health of job {} (state={})",
                checkpointId, jobId, current);
        return current;
    }

    /** failJob(): RUNNING|RECOVERING|DEGRADED → FAILED. */
    public void onFailJob(String cause) {
        transition(StreamJobHealth.FAILED, cause);
    }

    /** terminate(CANCEL): RUNNING|DEGRADED → CANCELED. */
    public void onCanceled() {
        transition(StreamJobHealth.CANCELED, "terminate(CANCEL)");
    }

    /** terminate(DRAIN|SUSPEND) completed: RUNNING|DEGRADED → FINISHED. */
    public void onFinished(String mode) {
        transition(StreamJobHealth.FINISHED, "terminate(" + mode + ")");
    }
}
