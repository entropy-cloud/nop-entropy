/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.streamrecord.watermark.Watermark;

/**
 * Manages multiple {@link HeapInternalTimerService} instances and advances their watermarks
 * in response to incoming watermark events.
 */
public class TimerServiceManager {

    private static final Logger LOG = LoggerFactory.getLogger(TimerServiceManager.class);

    /**
     * Copy-on-write so the {@code ProcessingTimeServiceDriver} scheduler thread can iterate
     * (due-check) while the task thread registers services during operator open().
     */
    private final List<HeapInternalTimerService<?, ?>> timerServices = new CopyOnWriteArrayList<>();

    public void registerTimerService(HeapInternalTimerService<?, ?> timerService) {
        timerServices.add(timerService);
    }

    /**
     * @return the number of registered {@link HeapInternalTimerService} instances. Used by
     *         runtime wiring assertions (a timer-using operator must have registered its
     *         service during {@code open()}).
     */
    public int numTimerServices() {
        return timerServices.size();
    }

    /**
     * Advances all registered timer services to the given watermark. Fail-fast: if any
     * service fails, the first exception is rethrown after all services have been
     * attempted (subsequent failures are attached via {@code addSuppressed}) — a failing
     * event-time trigger must surface to the task instead of silently dropping
     * window firings while the watermark keeps advancing. Per-service logging here is
     * diagnostic only.
     */
    public void advanceWatermark(Watermark mark) throws Exception {
        Exception first = null;
        for (HeapInternalTimerService<?, ?> service : timerServices) {
            try {
                advanceWatermarkUnchecked(service, mark.getTimestamp());
            } catch (Exception e) {
                LOG.error("Failed to advance watermark for timer service: {}", service, e);
                if (first == null)
                    first = e;
                else
                    first.addSuppressed(e);
            }
        }
        if (first != null)
            throw first;
    }

    /**
     * Fires all due processing-time timers. Fail-fast under the same contract as
     * {@link #advanceWatermark(Watermark)}: first exception rethrown after all services
     * have been attempted, subsequent failures suppressed — a failing timer callback must
     * not be silently swallowed while timers are marked fired.
     */
    public void fireProcessingTimeTimers(long timestamp) throws Exception {
        Exception first = null;
        for (HeapInternalTimerService<?, ?> service : timerServices) {
            try {
                fireProcessingTimeTimersUnchecked(service, timestamp);
            } catch (Exception e) {
                LOG.error("Failed to fire processing time timers for service: {}", service, e);
                if (first == null)
                    first = e;
                else
                    first.addSuppressed(e);
            }
        }
        if (first != null)
            throw first;
    }

    /**
     * @return {@code true} if any registered {@link HeapInternalTimerService} has a
     *         processing-time timer due at or before {@code now}. Safe to call from the
     *         scheduler thread ({@code ProcessingTimeServiceDriver}): each service's check
     *         is a single volatile read.
     */
    public boolean hasProcessingTimeTimersDue(long now) {
        for (HeapInternalTimerService<?, ?> service : timerServices) {
            if (service.hasProcessingTimeTimersDue(now)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void advanceWatermarkUnchecked(HeapInternalTimerService<?, ?> service, long timestamp) throws Exception {
        ((HeapInternalTimerService) service).advanceWatermark(timestamp);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void fireProcessingTimeTimersUnchecked(HeapInternalTimerService<?, ?> service, long timestamp) throws Exception {
        ((HeapInternalTimerService) service).fireProcessingTimeTimers(timestamp);
    }
}
