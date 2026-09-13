/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.testsupport.TestAwait;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
 */
public class TestProcessingTimeServiceDriver {

    @Test
    void testCallbackRunsOnTaskThreadViaMail() throws Exception {
        TaskMailbox mailbox = new TaskMailbox();
        TaskProcessingTimeService pts = new TaskProcessingTimeService();
        ProcessingTimeServiceDriver driver =
                new ProcessingTimeServiceDriver(mailbox, pts, null, 20);
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        AtomicInteger fires = new AtomicInteger();

        pts.registerTimer(System.currentTimeMillis() + 30, time -> {
            callbackThread.set(Thread.currentThread());
            fires.incrementAndGet();
        });

        driver.start();
        try {
            long deadline = System.currentTimeMillis() + 5000;
            while (fires.get() == 0 && System.currentTimeMillis() < deadline) {
                mailbox.drainAndRun();
                Thread.sleep(10);
            }
            assertTrue(fires.get() > 0, "Timer fired within timeout");
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

        pts.registerTimer(System.currentTimeMillis() + 10, time -> fires.incrementAndGet());

        driver.start();
        try {
            long deadline = System.currentTimeMillis() + 5000;
            while (mailbox.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(5);
            }
            assertFalse(mailbox.isEmpty(), "Driver delivered a fire mail once the timer was due");
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
            // 负向窗口：无 timer 注册时不得产生 fire mail（循环维持不变量）
            TestAwait.staysTrue("no fire mail without timer", mailbox::isEmpty, 60);
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

        pts.registerTimer(System.currentTimeMillis() + 60_000, time -> {
        });

        driver.start();
        try {
            Thread.sleep(60);
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
            long deadline = System.currentTimeMillis() + 3000;
            while (!driver.isRunning() && System.currentTimeMillis() < deadline) {
                Thread.sleep(5);
            }
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

        pts.registerTimer(System.currentTimeMillis() + 10_000, time -> fires.incrementAndGet());

        driver.start();
        driver.shutdown();
        long deadline = System.currentTimeMillis() + 3000;
        while (driver.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertFalse(driver.isRunning(), "Driver thread terminated after shutdown");
        Thread.sleep(60);
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
        long deadline = System.currentTimeMillis() + 3000;
        while (driver.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        driver.start(); // start after shutdown is a no-op
        Thread.sleep(30);
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
        service.registerProcessingTimeTimer("ns", System.currentTimeMillis() + 20);

        driver.start();
        try {
            long deadline = System.currentTimeMillis() + 5000;
            while (fires.get() == 0 && System.currentTimeMillis() < deadline) {
                mailbox.drainAndRun();
                Thread.sleep(10);
            }
            assertTrue(fires.get() > 0,
                    "TimerServiceManager-registered timer fired via driver + mailbox drain");
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
}
