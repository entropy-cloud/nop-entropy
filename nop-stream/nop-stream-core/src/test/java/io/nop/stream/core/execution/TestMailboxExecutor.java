package io.nop.stream.core.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused unit tests for {@link MailboxExecutor}: cancel-flag semantics,
 * {@link MailboxExecutor#processAvailableMails()} draining, and the standalone
 * {@link MailboxExecutor#runLoop()} exiting on cancel.
 */
class TestMailboxExecutor {

    @Test
    void testProcessAvailableMailsDrainsControlFirst() {
        MailboxExecutor executor = new MailboxExecutor();
        AtomicInteger order = new AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<String> firstRun = new java.util.concurrent.atomic.AtomicReference<>();

        executor.getMailbox().put(Mail.normal(() -> firstRun.compareAndSet(null, "normal"), "normal"));
        executor.getMailbox().put(Mail.control(() -> firstRun.compareAndSet(null, "control"), "control"));

        boolean cancelled = executor.processAvailableMails();
        assertFalse(cancelled);
        assertEquals("control", firstRun.get(), "control mail must run before normal mail");
        assertTrue(executor.getMailbox().isEmpty());
    }

    @Test
    void testProcessAvailableMailsReturnsCancelledAfterCancel() {
        MailboxExecutor executor = new MailboxExecutor();
        executor.getMailbox().put(Mail.control(() -> {
        }, "x"));
        assertFalse(executor.processAvailableMails());

        executor.cancel();
        assertTrue(executor.isCancelled());
        // Even with no mails, the return value reflects cancel state
        assertTrue(executor.processAvailableMails());
    }

    @Test
    @Timeout(5)
    void testRunLoopExitsOnCancel() throws Exception {
        MailboxExecutor executor = new MailboxExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger(0);

        executor.getMailbox().put(Mail.control(runs::incrementAndGet, "seed"));

        Thread loop = new Thread(() -> {
            entered.countDown();
            executor.runLoop();
        }, "mailbox-exec-loop");
        loop.start();
        assertTrue(entered.await(1, TimeUnit.SECONDS));

        // Seed mail should be processed quickly
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (runs.get() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, runs.get(), "seed mail must be processed by runLoop");

        // Cancel from another thread; loop must exit
        executor.cancel();
        loop.join(3000);
        assertFalse(loop.isAlive(), "runLoop must exit promptly after cancel()");
    }

    @Test
    void testSignalCancelRaisesFlagAndDeliversMarker() {
        MailboxExecutor executor = new MailboxExecutor();
        executor.signalCancel();
        assertTrue(executor.isCancelled());
        // Marker mail is present so a blocked drain wakes up
        Mail m = executor.getMailbox().poll();
        assertNotNull(m);
        assertEquals(Mail.Priority.CONTROL, m.getPriority());
    }

    @Test
    void testGetMailboxReturnsSameInstance() {
        MailboxExecutor executor = new MailboxExecutor();
        assertSame(executor.getMailbox(), executor.getMailbox());
    }
}
