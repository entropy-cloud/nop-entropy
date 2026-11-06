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
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_OPERATOR_ERROR;
import io.nop.api.core.time.CoreMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.operators.TimerServiceManager;

/**
 * Drives processing-time timer firing for one task, following the mailbox design intent
 * ({@code ai-dev/design/nop-stream/mailbox-design.md} §7): the scheduler thread NEVER executes
 * timer callbacks. It only checks (via cheap volatile reads) whether a timer is due and, when
 * one is, delivers a control mail to the owning task's {@link TaskMailbox}. The task thread
 * executes the mail at its next safe point (source {@code collect()}, {@code processInputGate}
 * loop top) and fires the timers there — so callbacks run serialized with {@code processElement}
 * and never race on shared operator state.
 *
 * <p><b>Trigger granularity (recorded decision):</b> periodic tick (default
 * {@value #DEFAULT_TICK_MS} ms) combined with a due-check. The driver does not scan the timer
 * map — it reads two volatile due-flags ({@link TaskProcessingTimeService#isTimerDue(long)} and
 * {@link TimerServiceManager#hasProcessingTimeTimersDue(long)}); no mail is delivered when
 * nothing is due, so idle tasks do not accumulate no-op mails.
 *
 * <p><b>Lifecycle:</b> started by {@link io.nop.stream.core.execution.task.StreamTaskInvokable#invoke()} and stopped in its
 * {@code finally} block. The thread is a daemon so a stray driver can never block JVM exit.
 * {@link #shutdown()} sets the stop flag and interrupts the sleep; a subsequent
 * {@link #start()} on the same driver is a no-op (drivers are single-shot).
 */
@Internal
public class ProcessingTimeServiceDriver implements Runnable {

    public static final long DEFAULT_TICK_MS = 100;

    private static final Logger LOG = LoggerFactory.getLogger(ProcessingTimeServiceDriver.class);

    private final TaskMailbox mailbox;
    private final TaskProcessingTimeService processingTimeService;
    private final TimerServiceManager timeServiceManager;
    private final long tickMs;

    private volatile boolean stopped = false;
    private volatile Thread thread;

    /**
     * W-02 (CRA 8.42 R4-02, ruling B.1): delivery debounce. While a timer-fire
     * mail is in flight (put but not yet run by the task thread), subsequent
     * ticks do NOT deliver another one — a task thread blocked at a data-plane
     * backpressure safe point (a slow sink is a NORMAL running state) otherwise
     * accumulates one redundant mail per tick (~864,000/day at the default
     * 100ms tick) with an empty TreeMap at every execution. Site-local object,
     * no cross-thread shared state beyond the CAS/volatile itself (8.41 B.1
     * shape precedent). Package-visible as the red② observation seam.
     *
     * <p>Closed-drop residual (declared semantics): a {@code mailbox.close()}
     * silently drops the mail so the flag would stay set and the driver stops
     * delivering — benign, because the driver is single-shot
     * ({@code StreamTaskInvokable.invoke()} shuts it down in its finally) and
     * close/shutdown share the same terminal window; the flag dies with the
     * driver. Timer-fire fail-fast semantics are unchanged: the wrapped runnable
     * still propagates exceptions from {@link #fireDueTimersOnTaskThread()}.
     */
    final java.util.concurrent.atomic.AtomicBoolean timerFireInFlight =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public ProcessingTimeServiceDriver(TaskMailbox mailbox,
                                       TaskProcessingTimeService processingTimeService,
                                       TimerServiceManager timeServiceManager) {
        this(mailbox, processingTimeService, timeServiceManager, DEFAULT_TICK_MS);
    }

    public ProcessingTimeServiceDriver(TaskMailbox mailbox,
                                       TaskProcessingTimeService processingTimeService,
                                       TimerServiceManager timeServiceManager,
                                       long tickMs) {
        if (mailbox == null) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "TaskMailbox must not be null");
        }
        if (processingTimeService == null) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "TaskProcessingTimeService must not be null");
        }
        if (tickMs <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "tickMs must be positive, got: " + tickMs);
        }
        this.mailbox = mailbox;
        this.processingTimeService = processingTimeService;
        this.timeServiceManager = timeServiceManager;
        this.tickMs = tickMs;
    }

    /**
     * Starts the daemon scheduler thread. Idempotent: a driver that was started (or already
     * shut down) refuses to start a second thread.
     */
    public synchronized void start() {
        if (thread != null) {
            return;
        }
        if (stopped) {
            LOG.debug("Processing-time driver already shut down; refusing to start again");
            return;
        }
        Thread t = new Thread(this, "processing-time-driver");
        t.setDaemon(true);
        this.thread = t;
        t.start();
    }

    /**
     * Stops the driver. Idempotent; interrupts the scheduler thread so a pending sleep ends
     * immediately. No further fire mails are delivered after shutdown returns.
     */
    public void shutdown() {
        stopped = true;
        Thread t = this.thread;
        if (t != null) {
            t.interrupt();
        }
    }

    public boolean isRunning() {
        Thread t = thread;
        return t != null && t.isAlive();
    }

    @Override
    public void run() {
        while (!stopped) {
            long now = CoreMetrics.currentTimeMillis();
            boolean due = processingTimeService.isTimerDue(now)
                    || (timeServiceManager != null && timeServiceManager.hasProcessingTimeTimersDue(now));
            // W-02 (ruling B.1): debounce — at most ONE in-flight timer-fire mail.
            // The fire is an idempotent catch-up read (fireDueTimers drains every
            // due timer), so skipping ticks while one mail is in flight never
            // loses a trigger: due stays true until the mail runs, and the next
            // tick after the in-flight flag clears re-delivers. Trigger latency
            // is bounded by one tick (the pre-existing tick-sampling granularity).
            if (due && timerFireInFlight.compareAndSet(false, true)) {
                mailbox.put(Mail.control(this::fireDueTimersOnTaskThreadAndReset,
                        "processing-time-timer-fire"));
            }
            try {
                Thread.sleep(tickMs);
            } catch (InterruptedException e) {
                if (!stopped) {
                    LOG.debug("Processing-time driver interrupted while sleeping", e);
                }
            }
        }
    }

    /**
     * Task-thread body of the timer-fire mail: run the catch-up fire, then
     * release the in-flight flag — in a finally, so an exception path (fire
     * fail-fast killing the task) also clears the flag.
     */
    private void fireDueTimersOnTaskThreadAndReset() {
        try {
            fireDueTimersOnTaskThread();
        } finally {
            timerFireInFlight.set(false);
        }
    }

    /**
     * Executed on the task thread via the mail action. Fires both the direct
     * {@link TaskProcessingTimeService} callbacks and the {@link TimerServiceManager}
     * (HeapInternalTimerService) processing-time timers. Timer failures propagate
     * (fail-fast): since {@code Mail.control} takes a {@link Runnable}, a checked failure
     * is rethrown as-is when already unchecked, otherwise wrapped in an unchecked
     * {@link StreamException} — the mail action's exception escapes through
     * {@code TaskMailbox.drainAndRun()} into the task loop, exactly like a failure of the
     * direct {@code processingTimeService.fireDueTimers} path. Timer callbacks must not
     * be silently swallowed while timers are marked fired.
     */
    private void fireDueTimersOnTaskThread() {
        long now = CoreMetrics.currentTimeMillis();
        processingTimeService.fireDueTimers(now);
        if (timeServiceManager != null) {
            try {
                timeServiceManager.fireProcessingTimeTimers(now);
            } catch (Exception e) {
                if (e instanceof RuntimeException)
                    throw (RuntimeException) e;
                throw new StreamException(ERR_STREAM_OPERATOR_ERROR, e)
                        .param(ARG_DETAIL, "processing-time timer fire failed via TimerServiceManager");
            }
        }
    }
}
