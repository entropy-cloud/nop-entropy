/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.local;

import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.41 R4-01 red⑥ driver (finding 1.1-C4-006 / C10-015; plan 2534 ruling D.3):
 * interrupting a LOCAL execute while a consumer task blocks in user code must give
 * that task a cooperative-cancel terminal exit — mailbox signalCancel + cancel()
 * interrupt (the {@code SupervisionLoop.cancelTaskWithMailbox} recipe, mirrored into
 * {@code TaskExecutor.cancelSubtasks}) — so the blocked task thread leaves its
 * blocking wait within the cancel-await budget.
 *
 * <p>Pre-fix {@code shutdownLocalExecution} only called graceful {@code shutdown()}
 * (no cancel, no interrupt): a task blocked in user code had NO terminal exit path —
 * the pre-fix red form is the sink function never observing an interrupt.
 */
class TestLocalCancelTerminatesTaskThreadsR401 {

    private static final String EXECUTOR_THREAD_PREFIX = "stream-task-executor";

    @Test
    void interruptedLocalExecuteGivesBlockedConsumerATerminalExit() throws Exception {
        CountDownLatch sinkStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // the sink observes its own terminal exit: an interrupt reaching the blocked
        // wait is the cooperative-cancel signal (pre-fix: nothing ever interrupts it)
        AtomicBoolean sinkExited = new AtomicBoolean(false);
        AtomicReference<String> exitMode = new AtomicReference<>("none");

        Set<String> threadsBefore = Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .collect(Collectors.toSet());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.fromCollection(List.of("a"))
                .sink((SinkFunction<String>) v -> {
                    sinkStarted.countDown();
                    try {
                        release.await(30, TimeUnit.SECONDS);
                        exitMode.set("latch-released");
                    } catch (InterruptedException e) {
                        exitMode.set("interrupted");
                        Thread.currentThread().interrupt();
                    } finally {
                        sinkExited.set(true);
                    }
                });

        AtomicReference<Throwable> executeFailure = new AtomicReference<>();
        Thread execThread = new Thread(() -> {
            try {
                env.execute("local-cancel-r401");
            } catch (Throwable t) {
                executeFailure.set(t);
            } finally {
                // test-end safety: unblock the consumer regardless of the path taken
                release.countDown();
            }
        }, "local-cancel-r401-exec");
        execThread.start();

        assertTrue(sinkStarted.await(20, TimeUnit.SECONDS),
                "the consumer task must be running and blocked in user code");
        assertTrue(waitForNewExecutorThread(threadsBefore, 10_000),
                "a stream-task-executor task thread must be alive before the interrupt");

        // the C4-006 trigger: interrupt the LOCAL execute while the consumer blocks
        execThread.interrupt();

        // red⑥ assertion: the blocked consumer must get its terminal exit within the
        // cancel-await budget (default 10s; the interrupt exit happens in ms post-fix).
        // Pre-fix this poll times out with exitMode=="none" — the task thread spun on
        // with no terminal exit path.
        long deadline = System.currentTimeMillis() + 10_000;
        while (!sinkExited.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(sinkExited.get(),
                "the blocked consumer task must observe a terminal exit within the "
                        + "cancel-await budget; exitMode=" + exitMode.get());
        assertEquals("interrupted", exitMode.get(),
                "the terminal exit must be the cooperative-cancel interrupt (ruling D.3), "
                        + "not a released latch");

        execThread.join(20_000);
        // NOTE: the exec-thread interrupt itself may be consumed by the timed
        // awaitCompletion's exception logging (pre-existing behavior, zero-touch
        // here) — the W-11 observable is the blocked consumer's terminal exit
        // above, delivered by the cancel sweep in the teardown's finally.
        assertTrue(!execThread.isAlive(),
                "the LOCAL execute thread must finish (teardown completes) after the cancel sweep");
    }

    private static boolean waitForNewExecutorThread(Set<String> before, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                if (t.getName().startsWith(EXECUTOR_THREAD_PREFIX) && !before.contains(t.getName())) {
                    return true;
                }
            }
            Thread.sleep(20);
        }
        return false;
    }
}
