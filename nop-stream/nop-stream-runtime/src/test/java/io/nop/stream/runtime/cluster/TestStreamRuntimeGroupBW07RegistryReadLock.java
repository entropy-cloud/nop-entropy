/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.cluster;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA Phase 8.41 R4-01 (plan 2534) 1.1 unit, Group B — E2 ubiquitous pin for
 * W-07 (C2-004): InMemoryClusterRegistry's read-side operations
 * ({@code getTaskAssignment} / {@code getAttemptHistory}) run under the same
 * {@code synchronized (taskAssignmentHistory)} monitor as the write side.
 *
 * <p>Classification (design ruling F, E2 branch for theoretical races): the
 * pre-fix defect (unsynchronized ArrayList get/size/copy vs concurrent add) is
 * a JMM-level race — a hammer exposes it probabilistically (AIOOBE, stale-size
 * reads, torn snapshots), so this pin is the post-fix GREEN ANCHOR: under a
 * single monotonic writer, every read-side snapshot must be an exact,
 * prefix-consistent view of the append-only history (element k has attempt
 * number k+1) and {@code getTaskAssignment} must be non-decreasing within a
 * reader. A torn or stale-sized snapshot violates the prefix invariant.
 *
 * <p>The deterministic regression net is the lock-structure check recorded in
 * the plan execution notes (grep-level):
 *
 * <pre>
 * grep -n "synchronized (taskAssignmentHistory)" InMemoryClusterRegistry.java
 *   → assignTask (write, pre-existing) + getTaskAssignment + getAttemptHistory (read, W-07 fix)
 * </pre>
 *
 * <p>Reverting the read-side monitor does not turn this pin deterministically
 * red — that is the adjudicated "probability red candidate + structural
 * argument" branch (E2), registered honestly rather than claimed as a
 * behavioral red.
 */
class TestStreamRuntimeGroupBW07RegistryReadLock {

    private static final String JOB_ID = "gb-w07-job";
    private static final String VERTEX_ID = "v-1";
    private static final String NODE_ID = "gb-w07-node";
    private static final int READER_THREADS = 4;
    private static final int WRITES = 3000;
    private static final int READS_PER_READER = 3000;

    @Test
    void concurrentReadWriteHammerKeepsHistoryConsistent() throws Exception {
        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch writerDone = new CountDownLatch(1);
        AtomicInteger violations = new AtomicInteger();
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger readExceptions = new AtomicInteger();

        Thread writer = new Thread(() -> {
            try {
                startGate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            for (int i = 1; i <= WRITES; i++) {
                registry.assignTask(JOB_ID, VERTEX_ID, 0, NODE_ID, "att-" + i, 1L, i);
            }
            writerDone.countDown();
        }, "gb-w07-writer");
        writer.start();

        ExecutorService pool = Executors.newFixedThreadPool(READER_THREADS);
        try {
            for (int r = 0; r < READER_THREADS; r++) {
                pool.submit(() -> {
                    try {
                        startGate.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    int lastSeen = 0;
                    for (int i = 0; i < READS_PER_READER; i++) {
                        try {
                            List<TaskAssignment> history =
                                    registry.getAttemptHistory(JOB_ID, VERTEX_ID, 0);
                            // Prefix invariant: the append-only history observed
                            // under the write lock is exactly 1..history.size().
                            for (int k = 0; k < history.size(); k++) {
                                if (history.get(k).getAttemptNumber() != k + 1) {
                                    violations.incrementAndGet();
                                    break;
                                }
                            }
                            TaskAssignment last =
                                    registry.getTaskAssignment(JOB_ID, VERTEX_ID, 0);
                            if (last != null) {
                                int attempt = last.getAttemptNumber();
                                if (attempt < lastSeen || attempt > WRITES) {
                                    violations.incrementAndGet();
                                }
                                lastSeen = Math.max(lastSeen, attempt);
                            }
                            reads.incrementAndGet();
                        } catch (RuntimeException e) {
                            // AIOOBE / corruption signatures of the unsynchronized read.
                            readExceptions.incrementAndGet();
                        }
                    }
                });
            }
            startGate.countDown();
            assertTrue(writerDone.await(30, TimeUnit.SECONDS), "writer must finish within the bound");
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "readers must finish within the bound");
        } finally {
            pool.shutdownNow();
        }
        writer.join(5000);

        assertEquals(0, readExceptions.get(), "no read-side exception under concurrent write hammer");
        assertEquals(0, violations.get(), "no torn/inconsistent snapshot under concurrent write hammer");
        assertTrue(reads.get() >= READER_THREADS * READS_PER_READER, "readers actually ran");

        // Post-hammer: the full history is visible, ordered, and the tail is the
        // last assignment.
        List<TaskAssignment> history = registry.getAttemptHistory(JOB_ID, VERTEX_ID, 0);
        assertEquals(WRITES, history.size(), "every append landed");
        TaskAssignment last = registry.getTaskAssignment(JOB_ID, VERTEX_ID, 0);
        assertNotNull(last);
        assertEquals(WRITES, last.getAttemptNumber(),
                "the tail of the history is the last assignment (max attempt number)");
    }
}
