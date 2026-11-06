/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.support;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Per-site three-state periodic-failure log suppressor (CRA 8.41 R4-01 W-13,
 * plan 2534 ruling E.1).
 *
 * <p>Periodic failure logs on tight retry cadences (a 50ms poll loop ≈ 1.4M
 * lines/hour) drown the log surface during an outage. Each site owns ONE
 * suppressor instance (no cross-thread shared state — the site's own threading
 * model from adjudication ⑥ is untouched) and consults it on every failure:
 * <ul>
 *   <li><b>first failure in the window</b> → {@link Action#LOG_FULL} — the
 *       caller logs its existing entry verbatim (stack trace included);</li>
 *   <li><b>failures inside the window</b> → {@link Action#SUPPRESS} — silent,
 *       but counted ({@link #getSuppressedCount()} cumulative,
 *       {@link #getWindowFailureCount()} in-window);</li>
 *   <li><b>first failure after the window expires</b> → {@link Action#LOG_AGGREGATE}
 *       — the caller logs ONE summary line (site id + suppressed count + last
 *       exception class/message, never the repeating stack) and a fresh window
 *       opens.</li>
 * </ul>
 *
 * <p>The site's own success signal feeds {@link #success()}: the transition out
 * of a failure state emits {@link Action#LOG_CLEARED} exactly once so the
 * recovery stays visible (fail-loud discipline — the suppressor only limits
 * repetition, it never swallows the failure: the first entry and every window
 * aggregate carry the real cause, and nothing is dropped silently).
 *
 * <p>Time is injected ({@link LongSupplier}) so pins are deterministic without
 * sleeping; production constructs with the two-arg constructor (wall clock).
 */
public final class PeriodicFailureSuppressor {

    /** What the caller should do for this event (ruling E.1 three states). */
    public enum Action {
        /** First failure in the window: log the existing full entry (stack included). */
        LOG_FULL,
        /** Window expired since the last entry: log one aggregate summary line. */
        LOG_AGGREGATE,
        /** Inside the window: silent, counted. */
        SUPPRESS,
        /** Recovery transition: log one cleared line. */
        LOG_CLEARED,
        /** Nothing to log (steady success / repeat success). */
        NONE
    }

    private final String siteId;
    private final long windowMs;
    private final LongSupplier clock;
    private final AtomicLong lastLoggedAt = new AtomicLong(Long.MIN_VALUE);
    private final AtomicLong windowFailureCount = new AtomicLong();
    private final AtomicLong suppressedCount = new AtomicLong();
    private final AtomicLong lastWindowSuppressedCount = new AtomicLong();
    private volatile String lastSummary = "";

    /** Production constructor (wall-clock time). */
    public PeriodicFailureSuppressor(String siteId, long windowMs) {
        this(siteId, windowMs, System::currentTimeMillis);
    }

    /** Test constructor (injected clock — deterministic pins, ruling E.1/S4). */
    public PeriodicFailureSuppressor(String siteId, long windowMs, LongSupplier clock) {
        this.siteId = siteId;
        this.windowMs = windowMs;
        this.clock = clock;
    }

    /**
     * Records one failure and returns the caller's log action. On
     * {@link Action#LOG_AGGREGATE} the caller should include
     * {@link #getLastWindowSuppressedCount()} and {@link #getLastSummary()} in
     * the summary line.
     */
    public Action failure(Throwable error) {
        return failure(summaryOf(error));
    }

    /**
     * Records one failure with a pre-rendered summary (for sites that only have
     * a message string). See {@link #failure(Throwable)}.
     */
    public Action failure(String summary) {
        if (summary != null && !summary.isEmpty()) {
            this.lastSummary = summary;
        }
        long now = clock.getAsLong();
        long last = lastLoggedAt.get();
        if (last == Long.MIN_VALUE || now - last >= windowMs) {
            boolean aggregate = last != Long.MIN_VALUE;
            // the closing window's suppressed count: everything observed since the
            // last entry except the closing failure itself (it is logged, not suppressed)
            lastWindowSuppressedCount.set(aggregate ? windowFailureCount.get() - 1 : 0);
            lastLoggedAt.set(now);
            windowFailureCount.set(1);
            return aggregate ? Action.LOG_AGGREGATE : Action.LOG_FULL;
        }
        windowFailureCount.incrementAndGet();
        suppressedCount.incrementAndGet();
        return Action.SUPPRESS;
    }

    /**
     * The site's success signal. Emits {@link Action#LOG_CLEARED} exactly once
     * on the transition out of a failure state; the cleared state also releases
     * the window anchor so the NEXT failure (after recovery) logs full again.
     */
    public Action success() {
        long failures = windowFailureCount.getAndSet(0);
        if (failures > 0) {
            lastLoggedAt.set(Long.MIN_VALUE);
            return Action.LOG_CLEARED;
        }
        return Action.NONE;
    }

    public boolean isCleared() {
        return windowFailureCount.get() == 0;
    }

    /** Failures observed in the current (open) window. */
    public long getWindowFailureCount() {
        return windowFailureCount.get();
    }

    /** Cumulative suppressed failures since construction (observability counter). */
    public long getSuppressedCount() {
        return suppressedCount.get();
    }

    /** Suppressed failures of the window closed by the most recent aggregate entry. */
    public long getLastWindowSuppressedCount() {
        return lastWindowSuppressedCount.get();
    }

    /** Last failure summary (exception class + message, no stack). */
    public String getLastSummary() {
        return lastSummary;
    }

    public String getSiteId() {
        return siteId;
    }

    public long getWindowMs() {
        return windowMs;
    }

    private static String summaryOf(Throwable error) {
        if (error == null) {
            return "";
        }
        String message = error.getMessage();
        return error.getClass().getName() + (message == null ? "" : (": " + message));
    }
}
