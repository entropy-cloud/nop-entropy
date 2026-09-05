/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ScheduledFuture;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.operators.ProcessingTimeService;

/**
 * Production {@link ProcessingTimeService} implementation owned by a single
 * {@link io.nop.stream.core.execution.task.StreamTaskInvokable}.
 *
 * <p><b>Threading contract (mailbox-delivered callbacks):</b> timer callbacks are never executed
 * by the scheduler thread. {@link #registerTimer(long, ProcessingTimeCallback)} stores the
 * registration; the {@link ProcessingTimeServiceDriver} (a daemon scheduler thread) only
 * decides <i>whether</i> a timer is due and, when it is, delivers a fire mail to the task
 * thread's {@link TaskMailbox}. The task thread drains the mail at its safe points
 * (source {@code collect()}, {@code processInputGate} loop top) and executes
 * {@link #fireDueTimers(long)}, which invokes the callbacks <b>on the task thread</b>. This
 * keeps timer callbacks serialized with {@code processElement} — they never race on shared
 * per-operator state (trigger context, current key, output).
 *
 * <p><b>Exception policy (recorded decision):</b> callbacks fired by this service fail fast —
 * an exception thrown by a callback propagates out of {@link #fireDueTimers(long)}, out of the
 * mail action and into the task main loop, failing the task visibly instead of silently
 * dropping the event or the exception. This mirrors {@code CepOperator}'s existing intent
 * (its internal timer wrapper wraps callback errors in a {@code StreamException}). The
 * {@code TimerServiceManager} path keeps its own per-service catch-and-log contract
 * (tested by {@code TestTimerServiceManagerRobustness}).
 *
 * <p>All timer bookkeeping (register / cancel / fire) runs on the owning task thread. The
 * only cross-thread read is {@link #isTimerDue(long)}, which reads a single volatile field
 * written by the task thread, so the scheduler thread never touches the timer map.
 */
@Internal
public class TaskProcessingTimeService implements ProcessingTimeService {

    private final TreeMap<Long, List<TimerRegistration>> timers = new TreeMap<>();

    /**
     * Earliest registered timer timestamp, or {@link Long#MAX_VALUE} when no timer is
     * registered. Written by the task thread (register / cancel / fire), read by the
     * scheduler thread ({@link ProcessingTimeServiceDriver}). Volatile for cross-thread
     * visibility; slight staleness is harmless (an extra no-op fire mail at most).
     */
    private volatile long nextTimerTimestamp = Long.MAX_VALUE;

    @Override
    public long getCurrentProcessingTime() {
        return System.currentTimeMillis();
    }

    @Override
    public synchronized ScheduledFuture<?> registerTimer(long timestamp, ProcessingTimeCallback target) {
        if (target == null) {
            throw new IllegalArgumentException("ProcessingTimeCallback must not be null");
        }
        TimerRegistration registration = new TimerRegistration(this, timestamp, target);
        timers.computeIfAbsent(timestamp, k -> new ArrayList<>()).add(registration);
        if (timestamp < nextTimerTimestamp) {
            nextTimerTimestamp = timestamp;
        }
        return registration.future;
    }

    /**
     * Removes a single pending registration (cancellation path). Idempotent.
     *
     * @return {@code true} if the registration was still pending and got removed
     */
    synchronized boolean cancelRegistration(TimerRegistration registration) {
        List<TimerRegistration> bucket = timers.get(registration.timestamp);
        if (bucket == null || !bucket.remove(registration)) {
            return false;
        }
        if (bucket.isEmpty()) {
            timers.remove(registration.timestamp);
        }
        recomputeNextTimer();
        return true;
    }

    /**
     * @return {@code true} if at least one registered timer is due at or before {@code now}.
     *         Safe to call from the scheduler thread (single volatile read).
     */
    public boolean isTimerDue(long now) {
        return nextTimerTimestamp <= now;
    }

    /**
     * Fires every registered timer with timestamp <= {@code now} on the calling (task) thread.
     * Timers are removed before their callbacks run, so a callback that registers a new timer
     * (re-arm) does not disturb the fire loop and never double-fires.
     *
     * <p>Fail-fast: a callback exception propagates to the caller (the mail action on the task
     * thread) and fails the task visibly. See the class javadoc for the recorded decision.
     */
    public synchronized void fireDueTimers(long now) {
        List<TimerRegistration> toFire = new ArrayList<>();
        while (true) {
            Map.Entry<Long, List<TimerRegistration>> entry = timers.firstEntry();
            if (entry == null || entry.getKey() > now) {
                break;
            }
            timers.pollFirstEntry();
            toFire.addAll(entry.getValue());
        }
        recomputeNextTimer();
        for (TimerRegistration registration : toFire) {
            registration.future.markFired();
            try {
                registration.callback.onProcessingTime(registration.timestamp);
            } catch (Exception e) {
                throw new ProcessingTimeCallbackException(registration.timestamp, e);
            }
        }
    }

    private void recomputeNextTimer() {
        Map.Entry<Long, List<TimerRegistration>> first = timers.firstEntry();
        nextTimerTimestamp = first != null ? first.getKey() : Long.MAX_VALUE;
    }

    /**
     * A single pending timer registration: (timestamp, callback, cancellation handle).
     */
    static final class TimerRegistration {
        final long timestamp;
        final ProcessingTimeCallback callback;
        final TimerRegistrationFuture future;

        TimerRegistration(TaskProcessingTimeService service, long timestamp, ProcessingTimeCallback callback) {
            this.timestamp = timestamp;
            this.callback = callback;
            this.future = new TimerRegistrationFuture(service, this);
        }
    }

    /**
     * Marker exception wrapping a callback failure so the task thread reports a useful
     * cause instead of a bare NPE deep inside the fire loop.
     */
    public static class ProcessingTimeCallbackException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ProcessingTimeCallbackException(long timestamp, Exception cause) {
            super("Processing-time callback failed for timer at " + timestamp, cause);
        }
    }
}
