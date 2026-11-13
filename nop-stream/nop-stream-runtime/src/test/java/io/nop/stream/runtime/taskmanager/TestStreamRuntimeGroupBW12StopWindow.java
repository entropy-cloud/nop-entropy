/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.taskmanager;

import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.execution.RecordWriter;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.StreamMap;
import io.nop.stream.runtime.cluster.TaskAssignment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA Phase 8.41 R4-01 (plan 2534) 1.1 unit, Group B — red⑦ driver (C10-016,
 * W-12): a 2PC commit notification dispatched INSIDE the stop() shutdown window
 * must be delivered, never silently dropped.
 *
 * <p>Window construction (adjudicated Major-2 form): the task's invokable parks
 * on a latch, so thread S's {@code tm.stop()} deterministically parks in the
 * taskExecutor {@code awaitTermination(5s)} — stretching the
 * {@code commitExecutor.shutdownNow()} → {@code runningTasks.clear()} window to
 * seconds. The main thread then fires {@code notifyCheckpointComplete} inside
 * that window: the notification is dispatched to a task whose commit executor is
 * already shut down.
 *
 * <p>Pre-fix the {@code saturatedCommitQueuePolicy}'s
 * {@code if (!executor.isShutdown()) r.run()} branch silently dropped the
 * notification (deterministic red: zero TestSink commit records). Post-fix the
 * split-window handler runs the commit INLINE on the caller (CC-17 semantics)
 * and the commit record arrives. The non-discriminative
 * {@code commitCallerRun} counter is deliberately NOT asserted (it increments
 * pre-fix too — review R1 Major-2).
 *
 * <p>The parked task also keeps the driver bounded: releasing the latch lets the
 * task thread exit, {@code awaitTermination} returns immediately and stop()
 * completes — no test ever waits the full 5s budget, and every latch is
 * released in a finally block.
 */
class TestStreamRuntimeGroupBW12StopWindow {

    private static final String NODE_ID = "gb-w12-node";
    private static final long EPOCH = 50L;

    private TaskManager taskManager;
    private CountDownLatch parkLatch;

    @AfterEach
    void tearDown() {
        releaseParkLatch();
        if (taskManager != null) {
            taskManager.stop();
        }
    }

    @Test
    void notifyInsideStopShutdownWindowDeliversCommit() throws Exception {
        RecordingParticipant participant = new RecordingParticipant();
        buildTaskManagerWithParkedTask(participant);

        Thread stopper = new Thread(taskManager::stop, "gb-w12-stopper");
        stopper.start();

        // Deterministic window entry: running=false is the first statement of
        // stop(); the three shutdownNow calls land within microseconds of it,
        // while runningTasks.clear() is blocked behind awaitTermination(5s)
        // (the parked task thread). A 200ms margin is orders of magnitude on
        // both sides.
        long deadline = System.currentTimeMillis() + 10_000;
        while (taskManager.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertFalse(taskManager.isRunning(), "stop() must have begun");
        Thread.sleep(200);
        assertEquals(1, taskManager.getRunningTaskCount(),
                "window precondition: the task entry is still registered (clear waits for the parked thread)");

        // In-window notification: dispatched to the already-shutdown commit executor.
        taskManager.notifyCheckpointComplete(1L, EPOCH);

        assertEquals(1, participant.commits.get(),
                "a commit notification dispatched inside the stop() shutdown window must be "
                        + "delivered inline, never silently dropped (C4-012)");

        releaseParkLatch();
        stopper.join(15_000);
        assertFalse(stopper.isAlive(), "stop() must complete after the latch release");
    }

    /** Second carrier (adjudicated): repeated stop/notify racing under the same window construction. */
    @RepeatedTest(3)
    void stopNotifyRaceStress() throws Exception {
        RecordingParticipant participant = new RecordingParticipant();
        buildTaskManagerWithParkedTask(participant);

        AtomicReference<Throwable> stopperFailure = new AtomicReference<>();
        Thread stopper = new Thread(() -> {
            try {
                taskManager.stop();
            } catch (Throwable t) {
                stopperFailure.set(t);
            }
        }, "gb-w12-stress-stopper");
        stopper.start();

        long deadline = System.currentTimeMillis() + 10_000;
        while (taskManager.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        Thread.sleep(100);
        taskManager.notifyCheckpointComplete(1L, EPOCH);

        assertEquals(1, participant.commits.get(),
                "in-window notification delivers under repeated stop racing (C4-012)");
        assertTrue(stopperFailure.get() == null,
                "stop() must not throw: " + stopperFailure.get());

        releaseParkLatch();
        stopper.join(15_000);
        assertFalse(stopper.isAlive(), "stop() must complete after the latch release");
    }

    // ==================== fixture ====================

    /**
     * Builds a started TaskManager holding one task whose invokable parks on
     * {@link #parkLatch} (uninterruptibly — future.cancel(true)'s interrupt is
     * swallowed so stop()'s awaitTermination window is deterministic).
     */
    private void buildTaskManagerWithParkedTask(RecordingParticipant participant) throws Exception {
        parkLatch = new CountDownLatch(1);
        taskManager = new TaskManager(NODE_ID, "localhost:9412", 2,
                new TestTaskManagerLivenessAndReporting.NoopMessageService(),
                new TestTaskManagerLivenessAndReporting.NoopClusterRegistry(), "control-topic");
        taskManager.start();
        taskManager.updateFencingToken(EPOCH);
        taskManager.receiveAssignment(new TaskAssignment(
                "job-gb-w12", "v-sink", 0, NODE_ID, "att-1", EPOCH,
                System.currentTimeMillis(), 1));
        taskManager.installInvokable("job-gb-w12", "v-sink", 0, new ParkedInvokable(participant, parkLatch), EPOCH);

        long deadline = System.currentTimeMillis() + 5000;
        while (taskManager.getRunningTaskCount() != 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, taskManager.getRunningTaskCount(), "task must be running before the stop race");
    }

    private void releaseParkLatch() {
        CountDownLatch latch = this.parkLatch;
        if (latch != null) {
            latch.countDown();
        }
    }

    /** SINK-role invokable whose invoke() parks on the latch, swallowing interrupts. */
    static final class ParkedInvokable extends StreamTaskInvokable {
        private final CountDownLatch release;

        ParkedInvokable(RecordingParticipant participant, CountDownLatch release) {
            super(new OperatorChain(Collections.<io.nop.stream.core.operators.StreamOperator<?>>singletonList(
                    participant)),
                    (RecordWriter<Object>) null,
                    new TestTaskManagerLivenessAndReporting.IdleInputGate());
            this.release = release;
        }

        @Override
        public void invoke() throws Exception {
            while (release.getCount() > 0) {
                try {
                    release.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    // Swallow: stay parked so stop()'s awaitTermination window
                    // stays open (deterministic in-window construction).
                }
            }
        }
    }

    /** TestSink: records every 2PC finishCommit routed through the operator chain. */
    static final class RecordingParticipant extends StreamMap<Object, Object>
            implements CheckpointParticipant {
        private static final long serialVersionUID = 1L;
        final AtomicInteger commits = new AtomicInteger();

        RecordingParticipant() {
            super(x -> x);
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
            commits.incrementAndGet();
        }

        @Override
        public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {
        }
    }
}
