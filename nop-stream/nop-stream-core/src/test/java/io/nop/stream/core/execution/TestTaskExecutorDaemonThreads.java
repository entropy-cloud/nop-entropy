package io.nop.stream.core.execution;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class TestTaskExecutorDaemonThreads {

    @Test
    void testDaemonThreadFactory() throws Exception {
        AtomicLong counter = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] isDaemon = new boolean[1];
        String[] threadName = new String[1];

        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "stream-task-executor-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        };

        ExecutorService pool = Executors.newFixedThreadPool(1, factory);
        pool.submit(() -> {
            isDaemon[0] = Thread.currentThread().isDaemon();
            threadName[0] = Thread.currentThread().getName();
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(isDaemon[0], "Thread should be a daemon thread");
        assertTrue(threadName[0].startsWith("stream-task-executor-"),
                "Thread name should start with 'stream-task-executor-', got: " + threadName[0]);
        pool.shutdownNow();
    }
}
