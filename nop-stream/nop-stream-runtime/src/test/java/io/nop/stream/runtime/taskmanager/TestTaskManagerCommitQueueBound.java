/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.taskmanager;

import io.micrometer.core.instrument.MeterRegistry;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.execution.RecordWriter;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.metrics.StreamMetricsRegistries;
import io.nop.stream.core.operators.StreamMap;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.metrics.TaskNodeMetrics;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 369 Phase 4 focused tests (R5-CC-10): the TaskManager's dedicated 2PC
 * commit executor has a BOUNDED wait queue and a defined saturation behavior.
 *
 * <ul>
 *   <li>{@link #saturatedQueueFallsBackToCallerRunsAndNeverDrops}: with the
 *       commit thread blocked, notifications beyond the queue capacity run on
 *       the CALLER's thread (CallerRuns backpressure) — no notification is
 *       dropped, and every fallback is counted on
 *       {@code nop.stream.task.commitCallerRuns.total}.</li>
 *   <li>{@link #commitQueueBacklogStaysBoundedAndGaugeTracksDepth}: the queue
 *       depth never exceeds the configured capacity, and the
 *       {@code nop.stream.task.commitQueueDepth} gauge tracks the backlog.</li>
 *   <li>{@link #nonPositiveCapacityFailsFast}: a non-positive capacity is
 *       rejected with a typed {@link StreamException}.</li>
 * </ul>
 */
class TestTaskManagerCommitQueueBound {

    private static final int CAPACITY = 2;
    private static final int NOTIFICATIONS = 6;

    @Test
    void saturatedQueueFallsBackToCallerRunsAndNeverDrops() throws Exception {
        String node = "node-cc10-callerruns";
        TaskManager tm = new TaskManager(node, "embedded:cc10-a", 4,
                new TestTaskManagerLivenessAndReporting.NoopMessageService(),
                new TestTaskManagerLivenessAndReporting.NoopClusterRegistry(), "ct",
                5000L, 15000L, CAPACITY);
        tm.start();
        CountDownLatch blocker = new CountDownLatch(1);
        try {
            assertEquals(CAPACITY, tm.commitQueueCapacity());

            long token = 31L;
            tm.updateFencingToken(token);
            tm.receiveAssignment(new TaskAssignment(
                    "job-cc10-a", "v-sink", 0, node, "att-1", token, System.currentTimeMillis(), 1));
            CountingParticipant participant = new CountingParticipant(blocker);
            tm.installInvokable("job-cc10-a", "v-sink", 0, new CommitSinkInvokable(participant));
            awaitTaskRunning(tm);

            // Deterministic saturation: ONLY the first commit blocks. Park the
            // commit thread in commit #1 FIRST (poll until it entered
            // finishCommit), so notifications #2/#3 fill the queue (capacity 2)
            // and #4/#5/#6 are rejected → CallerRuns. Their inline commits
            // return immediately (not the first), so the notify loop cannot
            // race the queue drain.
            tm.notifyCheckpointComplete(1L, token);
            long parkedDeadline = System.currentTimeMillis() + 2000;
            while (participant.commits.get() < 1 && System.currentTimeMillis() < parkedDeadline) {
                Thread.sleep(10);
            }
            assertEquals(1, participant.commits.get(),
                    "commit #1 must be in flight on the blocked commit thread");

            for (long id = 2; id <= NOTIFICATIONS; id++) {
                tm.notifyCheckpointComplete(id, token);
            }

            // No exception may escape and no notification may be dropped: the
            // saturation policy is CallerRuns, not abort.
            double callerRuns = TaskNodeMetrics.forNode(node).getCommitCallerRunCount();
            assertTrue(callerRuns >= NOTIFICATIONS - (CAPACITY + 1),
                    "every queue-overflow commit must fall back to the caller thread and be counted, "
                            + "expected >= " + (NOTIFICATIONS - (CAPACITY + 1)) + " fallbacks, got "
                            + callerRuns);

            // Release the blocked commit and let the queue drain.
            blocker.countDown();
            awaitAllCommits(participant, NOTIFICATIONS);

            // Exactly the (1 running + capacity queued) commits were ever handed
            // to the dedicated thread; the overflow ran on the caller.
            assertEquals(CAPACITY + 1, countOnDedicatedThread(participant),
                    "only the running commit plus the queued commits may execute on the dedicated "
                            + "commit thread; the rest must have run on the caller thread");
        } finally {
            blocker.countDown();
            tm.stop();
        }
    }

    @Test
    void commitQueueBacklogStaysBoundedAndGaugeTracksDepth() throws Exception {
        String node = "node-cc10-depth";
        TaskManager tm = new TaskManager(node, "embedded:cc10-b", 4,
                new TestTaskManagerLivenessAndReporting.NoopMessageService(),
                new TestTaskManagerLivenessAndReporting.NoopClusterRegistry(), "ct",
                5000L, 15000L, CAPACITY);
        tm.start();
        CountDownLatch blocker = new CountDownLatch(1);
        try {
            long token = 32L;
            tm.updateFencingToken(token);
            tm.receiveAssignment(new TaskAssignment(
                    "job-cc10-b", "v-sink", 0, node, "att-1", token, System.currentTimeMillis(), 1));
            CountingParticipant participant = new CountingParticipant(blocker);
            tm.installInvokable("job-cc10-b", "v-sink", 0, new CommitSinkInvokable(participant));
            awaitTaskRunning(tm);
            startDelayedReleaser(blocker, 1500);

            // With the commit thread blocked, 2 notifications fit the queue; a
            // third runs on the caller thread (CallerRuns). Depth must never
            // exceed the capacity.
            for (long id = 1; id <= 3; id++) {
                tm.notifyCheckpointComplete(id, token);
            }
            assertTrue(tm.commitQueueDepth() <= CAPACITY,
                    "commit queue depth must stay within the configured bound, got "
                            + tm.commitQueueDepth() + " > " + CAPACITY);

            MeterRegistry registry = StreamMetricsRegistries.registry();
            io.micrometer.core.instrument.Gauge gauge = registry.find(
                    TaskNodeMetrics.METRIC_COMMIT_QUEUE_DEPTH).tag("nodeId", node).gauge();
            assertTrue(gauge != null, "commitQueueDepth gauge must be registered on start()");
            assertTrue(gauge.value() <= CAPACITY,
                    "gauge must track the bounded backlog, got " + gauge.value());
        } finally {
            blocker.countDown();
            tm.stop();
        }
    }

    @Test
    void nonPositiveCapacityFailsFast() {
        assertThrows(StreamException.class, () -> new TaskManager("node-cc10-bad", "embedded:cc10-c", 1,
                new TestTaskManagerLivenessAndReporting.NoopMessageService(),
                new TestTaskManagerLivenessAndReporting.NoopClusterRegistry(), "ct",
                5000L, 15000L, 0));
    }

    @Test
    void defaultCapacityConstructorKeepsBoundedQueue() {
        String node = "node-cc10-default";
        TaskManager tm = new TaskManager(node, "embedded:cc10-d", 2,
                new TestTaskManagerLivenessAndReporting.NoopMessageService(),
                new TestTaskManagerLivenessAndReporting.NoopClusterRegistry(), "ct");
        try {
            assertEquals(TaskManager.DEFAULT_COMMIT_QUEUE_CAPACITY, tm.commitQueueCapacity(),
                    "the legacy constructor must keep the bounded default capacity");
        } finally {
            tm.stop();
        }
    }

    // ==================== helpers & stubs ====================

    private static void awaitTaskRunning(TaskManager tm) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (tm.getRunningTaskCount() != 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(1, tm.getRunningTaskCount(), "task must be running before notifications fire");
    }

    private static void startDelayedReleaser(CountDownLatch blocker, long delayMs) {
        Thread releaser = new Thread(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            blocker.countDown();
        }, "cc10-blocker-releaser");
        releaser.setDaemon(true);
        releaser.start();
    }

    private static void awaitAllCommits(CountingParticipant participant, int expected)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 8000;
        while (participant.commits.get() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(expected, participant.commits.get(), "every commit must eventually run");
    }

    private static long countOnDedicatedThread(CountingParticipant participant) {
        return participant.commitThreads.stream()
                .filter(t -> t.startsWith("tm-commit-")).count();
    }

    /** SINK-role invokable whose finishCommit blocks until the latch opens. */
    static class CommitSinkInvokable extends StreamTaskInvokable {
        CommitSinkInvokable(CountingParticipant participant) {
            super(new OperatorChain(Collections.<io.nop.stream.core.operators.StreamOperator<?>>singletonList(
                    participant)),
                    (RecordWriter<Object>) null,
                    new TestTaskManagerLivenessAndReporting.IdleInputGate());
        }
    }

    /** 2PC participant that blocks only its FIRST commit and records the executing thread. */
    static class CountingParticipant extends StreamMap<Object, Object> implements CheckpointParticipant {
        private static final long serialVersionUID = 1L;
        final CountDownLatch blocker;
        final AtomicInteger commits = new AtomicInteger();
        final List<String> commitThreads = new CopyOnWriteArrayList<>();

        CountingParticipant(CountDownLatch blocker) {
            super(x -> x);
            this.blocker = blocker;
        }

        @Override
        public TaskStateSnapshot saveState(long epochId) {
            return null;
        }

        @Override
        public void prepareCommit(long epochId) {
        }

        @Override
        public void finishCommit(long epochId, boolean success) {
            commitThreads.add(Thread.currentThread().getName());
            int nth = commits.incrementAndGet();
            if (nth == 1) {
                try {
                    blocker.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {
        }
    }
}
