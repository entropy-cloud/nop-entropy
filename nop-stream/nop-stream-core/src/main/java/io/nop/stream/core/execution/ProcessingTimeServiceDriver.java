/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

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
            throw new IllegalArgumentException("TaskMailbox must not be null");
        }
        if (processingTimeService == null) {
            throw new IllegalArgumentException("TaskProcessingTimeService must not be null");
        }
        if (tickMs <= 0) {
            throw new IllegalArgumentException("tickMs must be positive, got: " + tickMs);
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
            long now = System.currentTimeMillis();
            boolean due = processingTimeService.isTimerDue(now)
                    || (timeServiceManager != null && timeServiceManager.hasProcessingTimeTimersDue(now));
            if (due) {
                mailbox.put(Mail.control(this::fireDueTimersOnTaskThread, "processing-time-timer-fire"));
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
     * Executed on the task thread via the mail action. Fires both the direct
     * {@link TaskProcessingTimeService} callbacks and the {@link TimerServiceManager}
     * (HeapInternalTimerService) processing-time timers.
     */
    private void fireDueTimersOnTaskThread() {
        long now = System.currentTimeMillis();
        processingTimeService.fireDueTimers(now);
        if (timeServiceManager != null) {
            try {
                timeServiceManager.fireProcessingTimeTimers(now);
            } catch (Exception e) {
                // TimerServiceManager already catches per-service failures and logs them
                // (its established robustness contract); this is the unexpected tail.
                LOG.error("Unexpected failure firing processing-time timers via TimerServiceManager", e);
            }
        }
    }
}
