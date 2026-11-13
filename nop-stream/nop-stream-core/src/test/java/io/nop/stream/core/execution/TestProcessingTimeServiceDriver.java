/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.test.time.ManualClock;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.testsupport.TestAwait;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.nop.stream.core.operators.HeapInternalTimerService;
import io.nop.stream.core.operators.InternalTimer;
import io.nop.stream.core.operators.Triggerable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused unit tests for {@link ProcessingTimeServiceDriver} (plan `2026-08-13-0132-1`
 * Phase 1, Rule #25): mail-based delivery (callbacks run on the consuming task thread),
 * due-check suppression of no-op mails, daemon semantics, and shutdown race (no firing
 * after shutdown).
 *
 * <p>时间确定化（plan 2510）：driver 的时间读取经 {@code CoreMetrics}，测试注册
 * {@link ManualClock} 显式推进虚拟时间触发 fire；「仍未发生」类断言前先以时钟读取计数
 * 增长确认 driver 线程已重新求值，不再使用墙钟观察窗。
 */
public class TestProcessingTimeServiceDriver {

    private ManualClock clock;

    @BeforeEach
    void registerManualClock() {
        clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
    }

    @AfterEach
    void restoreDefaultClock() {
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
    }

    @Test
    void testCallbackRunsOnTaskThreadViaMail() throws Exception {
        TaskMailbox mailbox = new TaskMailbox();
        TaskProcessingTimeService pts = new TaskProcessingTimeService();
        ProcessingTimeServiceDriver driver =
                new ProcessingTimeServiceDriver(mailbox, pts, null, 10);
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        AtomicInteger fires = new AtomicInteger();

        pts.registerTimer(clock.peekMillis() + 30, time -> {
            callbackThread.set(Thread.currentThread());
            fires.incrementAndGet();
        });

        driver.start();
        try {
            // 推进虚拟时间越过截止点：driver 线程下一个 tick 读到 due 并投递 fire mail，
            // 任务（测试）线程 drain 执行回调。
            clock.advance(100);
            TestAwait.until("timer fire mail drained and callback ran", () -> {
                mailbox.drainAndRun();
                return fires.get() > 0;
            });
            assertEquals(Thread.currentThread(), callbackThread.get(),
                    "Callback executes on the mail-consuming (task) thread, not the driver thread");
        } finally {
            driver.shutdown();
        }
    }

    @Test
    void testMailDrainRunsCallback() throws Exception {
        TaskMailbox mailbox = new TaskMailbox();
        TaskProcessingTimeService pts = new TaskProcessingTimeService();
        ProcessingTimeServiceDriver driver =
                new ProcessingTimeServiceDriver(mailbox, pts, null, 10);
        AtomicInteger fires = new AtomicInteger();

        pts.registerTimer(clock.peekMillis() + 10, time -> fires.incrementAndGet());

        driver.start();
        try {
            clock.advance(100);
            TestAwait.until("driver delivered a fire mail once the timer was due",
                    () -> !mailbox.isEmpty());
            mailbox.drainAndRun();
            assertEquals(1, fires.get(), "Draining the mail on the task thread fires the timer");
        } finally {
            driver.shutdown();
        }
    }

    @Test
    void testNoMailWhenNothingDue() throws Exception {
        TaskMailbox mailbox = new TaskMailbox();
        TaskProcessingTimeService pts = new TaskProcessingTimeService();
        ProcessingTimeServiceDriver driver =
                new ProcessingTimeServiceDriver(mailbox, pts, null, 10);

        driver.start();
        try {
            // 负向断言的结构性证据：driver 线程已多次读过时钟（活着并反复求值），mailbox 仍空。
            TestAwait.until("driver polled the clock at least 3 times",
                    () -> clock.totalReads() >= 3);
            assertTrue(mailbox.isEmpty(), "No fire mail when no timer is registered");
        } finally {
            driver.shutdown();
        }
    }

    @Test
    void testNoMailWhenTimerNotYetDue() throws Exception {
        TaskMailbox mailbox = new TaskMailbox();
        TaskProcessingTimeService pts = new TaskProcessingTimeService();
        ProcessingTimeServiceDriver driver =
                new ProcessingTimeServiceDriver(mailbox, pts, null, 10);

        pts.registerTimer(clock.peekMillis() + 60_000, time -> {
        });

        driver.start();
        try {
            // 时钟未推进（截止点在虚拟时间线上远未到达），driver 已多次重新求值后 mailbox 仍空。
            TestAwait.until("driver re-evaluated the due-check after start",
                    () -> clock.totalReads() >= 3);
            assertTrue(mailbox.isEmpty(), "No fire mail while the earliest timer is far in the future");
        } finally {
            driver.shutdown();
        }
    }

    @Test
    void testDriverThreadIsDaemon() throws Exception {
        TaskMailbox mailbox = new TaskMailbox();
        TaskProcessingTimeService pts = new TaskProcessingTimeService();
        ProcessingTimeServiceDriver driver =
                new ProcessingTimeServiceDriver(mailbox, pts, null, 10);

        driver.start();
        try {
            TestAwait.until("driver thread started", driver::isRunning);
            assertTrue(driver.isRunning(), "Driver thread started");
        } finally {
            driver.shutdown();
        }
    }

    @Test
    void testShutdownStopsFiring() throws Exception {
        TaskMailbox mailbox = new TaskMailbox();
        TaskProcessingTimeService pts = new TaskProcessingTimeService();
        ProcessingTimeServiceDriver driver =
                new ProcessingTimeServiceDriver(mailbox, pts, null, 10);
        AtomicInteger fires = new AtomicInteger();

        pts.registerTimer(clock.peekMillis() + 10_000, time -> fires.incrementAndGet());

        driver.start();
        driver.shutdown();
        // 驱动线程终止后，投递方（driver）不复存在——mailbox 必然保持空，无需墙钟观察窗。
        TestAwait.until("driver thread terminated after shutdown", () -> !driver.isRunning());
        assertFalse(driver.isRunning(), "Driver thread terminated after shutdown");
        assertTrue(mailbox.isEmpty(), "No fire mail delivered after shutdown");
        assertEquals(0, fires.get(), "No callback fires after shutdown");
    }

    @Test
    void testStartIsIdempotent() throws Exception {
        TaskMailbox mailbox = new TaskMailbox();
        TaskProcessingTimeService pts = new TaskProcessingTimeService();
        ProcessingTimeServiceDriver driver =
                new ProcessingTimeServiceDriver(mailbox, pts, null, 10);

        driver.start();
        driver.start(); // second start is a no-op
        driver.shutdown();
        TestAwait.until("driver thread terminated", () -> !driver.isRunning());
        driver.start(); // start after shutdown is a no-op
        assertFalse(driver.isRunning(), "Driver cannot be restarted after shutdown");
    }

    @Test
    void testDriverFiresManagerTimers() throws Exception {
        TaskMailbox mailbox = new TaskMailbox();
        TaskProcessingTimeService pts = new TaskProcessingTimeService();
        io.nop.stream.core.operators.TimerServiceManager tsm =
                new io.nop.stream.core.operators.TimerServiceManager();
        ProcessingTimeServiceDriver driver =
                new ProcessingTimeServiceDriver(mailbox, pts, tsm, 10);
        AtomicInteger fires = new AtomicInteger();

        Triggerable<String, String> triggerable = new Triggerable<String, String>() {
            @Override
            public void onProcessingTime(InternalTimer<String, String> timer) throws Exception {
                fires.incrementAndGet();
            }

            @Override
            public void onEventTime(InternalTimer<String, String> timer) throws Exception {
            }
        };
        HeapInternalTimerService<String, String> service = new HeapInternalTimerService<>(triggerable);
        tsm.registerTimerService(service);
        service.registerProcessingTimeTimer("ns", clock.peekMillis() + 20);

        driver.start();
        try {
            clock.advance(100);
            TestAwait.until("TimerServiceManager-registered timer fired via driver + mailbox drain",
                    () -> {
                        mailbox.drainAndRun();
                        return fires.get() > 0;
                    });
            assertEquals(0, service.numProcessingTimeTimers(), "Fired timer removed from service");
        } finally {
            driver.shutdown();
        }
    }

    @Test
    void testNullMailboxRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(StreamException.class,
                () -> new ProcessingTimeServiceDriver(null, new TaskProcessingTimeService(), null, 10));
    }

    /**
     * P1-6.7-007（plan 2387）：TimerServiceManager 的定时器失败经 driver 的 mail action
     * 传播到 mailbox drain——driver 不二次吞（与 processingTimeService.fireDueTimers 直接
     * 路径同语义，任务 fail-fast 而非静默丢发射）。
     */
    @Test
    void testManagerTimerFailurePropagatesThroughMailboxDrain() throws Exception {
        TaskMailbox mailbox = new TaskMailbox();
        TaskProcessingTimeService pts = new TaskProcessingTimeService();
        io.nop.stream.core.operators.TimerServiceManager tsm =
                new io.nop.stream.core.operators.TimerServiceManager();
        ProcessingTimeServiceDriver driver =
                new ProcessingTimeServiceDriver(mailbox, pts, tsm, 10);

        Triggerable<String, String> triggerable = new Triggerable<String, String>() {
            @Override
            public void onProcessingTime(InternalTimer<String, String> timer) {
                throw new StreamException(io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL)
                        .param(io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL, "manager timer boom");
            }

            @Override
            public void onEventTime(InternalTimer<String, String> timer) {
            }
        };
        HeapInternalTimerService<String, String> service = new HeapInternalTimerService<>(triggerable);
        tsm.registerTimerService(service);
        service.registerProcessingTimeTimer("ns", clock.peekMillis() + 20);

        AtomicReference<StreamException> propagated = new AtomicReference<>();
        driver.start();
        try {
            clock.advance(100);
            TestAwait.until("fire mail delivered after the timer became due",
                    () -> !mailbox.isEmpty());
            try {
                mailbox.drainAndRun();
            } catch (StreamException e) {
                propagated.set(e);
            }
        } finally {
            driver.shutdown();
        }
        assertTrue(propagated.get() != null,
                "manager timer failure must propagate through the driver's mail action (fail-fast, P1-6.7-007)");
    }

    // ==================== CRA 8.42 R4-02 W-02 (C10-021, 红②) ====================
    // Driver-side delivery debounce: while a timer-fire mail is in flight
    // (undrained), subsequent ticks must NOT deliver another one — a task thread
    // blocked at a backpressure safe point otherwise accumulates ~864,000 mails
    // per day from a continuously-due timer at the 100ms default tick. Reverting
    // the in-flight CAS guard (adjudication B.1) turns this pin red.

    @Test
    void continuouslyDueTimerDeliversAtMostOneInFlightMail() throws Exception {
        TaskMailbox mailbox = new TaskMailbox();
        TaskProcessingTimeService pts = new TaskProcessingTimeService();
        ProcessingTimeServiceDriver driver =
                new ProcessingTimeServiceDriver(mailbox, pts, null, 10);
        AtomicInteger fires = new AtomicInteger();

        // A timer that is ALREADY due and never drained stays due every tick —
        // the continuously-due driver state (fire removes the timer, so the
        // undrained registration never goes away).
        // ManualClock 冻结在 1_000_000（plan 2510 确定化口径）：注册一个恒到期的 timer
        pts.registerTimer(clock.peekMillis(), time -> fires.incrementAndGet());

        driver.start();
        try {
            // ≥3 ticks at tickMs=10: a 100ms window elapses ~8-10 ticks without
            // any drain (the unboundedly-blocked-task-thread normal state).
            TestAwait.staysTrue("driver ticks elapse with the mailbox undrained",
                    () -> true, 100);

            // Debounce upper bound: at most ONE in-flight timer-fire mail.
            assertTrue(mailbox.size() <= 1,
                    "at most one in-flight timer-fire mail while the task thread never "
                            + "reaches a safe point, got " + mailbox.size());

            // Fire idempotence (catch-up read): one drain fires the due timer
            // exactly once — the debounce must not swallow the trigger.
            mailbox.drainAndRun();
            assertEquals(1, fires.get(),
                    "one drain fires the due timer exactly once (catch-up semantics)");

            // After the fire the timer is consumed: no further fire mails pile up
            // (negative window over several ticks).
            TestAwait.staysTrue("mailbox stays empty after the due timer fired",
                    mailbox::isEmpty, 80);
            assertEquals(0, mailbox.size(), "no redundant fire mail after the timer fired");
        } finally {
            driver.shutdown();
        }
    }
}
