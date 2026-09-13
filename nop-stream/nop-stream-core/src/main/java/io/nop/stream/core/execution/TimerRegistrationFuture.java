/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import io.nop.api.core.time.CoreMetrics;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.nop.api.core.annotations.core.Internal;

/**
 * {@link ScheduledFuture} handle returned by {@link TaskProcessingTimeService#registerTimer}.
 *
 * <p>The registered callback is executed by the task thread (via the mailbox fire mail), not by
 * any scheduler; this handle therefore does not represent a scheduled executor task. It exists
 * so callers can cancel a pending registration ({@link #cancel(boolean)}) and observe whether
 * the timer has fired or been cancelled ({@link #isDone()} / {@link #isCancelled()}).
 *
 * <p>Semantics:
 * <ul>
 *   <li>{@link #cancel(boolean)} removes the registration from the owning service if it is
 *       still pending; returns {@code false} if the timer already fired or was already
 *       cancelled (nothing left to cancel). The {@code mayInterruptIfRunning} flag is ignored:
 *       the callback runs cooperatively on the task thread and cannot be interrupted.</li>
 *   <li>{@link #get()} / {@link #get(long, TimeUnit)} throw {@link CancellationException} if
 *       cancelled; they return {@code null} (the {@code Void} result) once the timer has fired,
 *       and throw {@link IllegalStateException} while the timer is still pending — the codebase
 *       only consumes {@link #cancel(boolean)}, so blocking semantics are intentionally not
 *       provided.</li>
 * </ul>
 */
@Internal
public class TimerRegistrationFuture implements ScheduledFuture<Void> {

    private final TaskProcessingTimeService service;
    private final TaskProcessingTimeService.TimerRegistration registration;

    private volatile boolean fired = false;
    private volatile boolean cancelled = false;

    TimerRegistrationFuture(TaskProcessingTimeService service,
                            TaskProcessingTimeService.TimerRegistration registration) {
        this.service = service;
        this.registration = registration;
    }

    void markFired() {
        this.fired = true;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long remaining = registration.timestamp - CoreMetrics.currentTimeMillis();
        return unit.convert(Math.max(0, remaining), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
        return Long.compare(getDelay(TimeUnit.MILLISECONDS), other.getDelay(TimeUnit.MILLISECONDS));
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (fired || cancelled) {
            return false;
        }
        if (service.cancelRegistration(registration)) {
            cancelled = true;
            return true;
        }
        // The registration was no longer pending (already fired concurrently).
        return false;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public boolean isDone() {
        return fired || cancelled;
    }

    @Override
    public Void get() {
        if (cancelled) {
            throw new CancellationException("Processing-time timer was cancelled");
        }
        if (fired) {
            return null;
        }
        throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "Timer at " + registration.timestamp + " is still pending; it completes when the "
                        + "task thread fires it (mailbox-delivered). Only cancel()/isDone() are supported "
                        + "for pending registrations.");
    }

    @Override
    public Void get(long timeout, TimeUnit unit) {
        return get();
    }
}
