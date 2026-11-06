/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA Phase 8.41 R4-01 (plan 2534) 1.1 unit, Group B — red③ driver (C10-012,
 * W-06): commit-fanout saturation must NOT pull the sink commit I/O back under
 * the coordinator monitor (C2-003); the saturated task is re-dispatched through
 * the bounded overflow queue and eventually executes (never lost).
 *
 * <p>Driver (adjudicated C.2 form): the S1 seam shrinks the commit-fanout wait
 * queue to 2; a participant blocks its first commit to pin the fanout thread;
 * three further completed epochs fill the queue (2) and overflow (1). The
 * overflow must be observable (counter), the acking thread — which holds the
 * coordinator monitor — must return promptly (monitor probe, paradigm of
 * {@code TestCommitFanoutOffMonitor2326}), and after the block is lifted every
 * re-dispatched commit executes.
 *
 * <p>Pre-fix red form: the S1 seam (constructor-injected fanout queue capacity)
 * and the overflow counter do not exist, so this driver is deterministically red
 * as a compile-time construction (same form as the Group-A red-GA-w04 driver);
 * behaviorally, the pre-fix {@code CallerRunsPolicy} would run the blocked
 * participant commit INLINE on the acking thread while it holds the monitor, so
 * the monitor probe below would stall for the whole hold window (the inlining
 * is the C2-003 defect body the probe assertion detects).
 */
class TestStreamRuntimeGroupBW06CommitFanoutOverflow {

    private static final String JOB_ID = "gb-w06-job";
    private static final String PIPELINE_ID = "pipeline-0";
    private static final TaskLocation LOC_1 = new TaskLocation(JOB_ID, PIPELINE_ID, "src", 0);
    private static final TaskLocation LOC_2 = new TaskLocation(JOB_ID, PIPELINE_ID, "sink", 0);
    private static final int FANOUT_QUEUE_CAPACITY = 2;
    private static final int COMPLETED_EPOCHS = 5; // 1 executing + 2 queued + 2 overflowed
    private static final long HOLD_MS = 3000L;

    @TempDir
    Path tempDir;

    @Test
    void saturationOverflowsWithoutBlockingMonitorAndRedispatches() throws Exception {
        CheckpointCoordinator coord = new CheckpointCoordinator(JOB_ID, PIPELINE_ID,
                new CheckpointIDCounter(), new LocalFileCheckpointStorage(tempDir.toString()),
                config(), FANOUT_QUEUE_CAPACITY);
        coord.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));

        HoldParticipant participant = new HoldParticipant();
        coord.addParticipant(participant);

        try {
            // Epoch 1: its fan-out task pins the fanout thread inside the blocked
            // finishCommit.
            completeEpoch(coord, 1);
            long deadline = System.currentTimeMillis() + 10_000;
            while (participant.commits.get() < 1 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(1, participant.commits.get(),
                    "the first fan-out must be executing (blocked on the hold window)");

            // Epochs 2..5: two fill the (capacity-2) queue, epochs 4 and 5
            // overflow. Each completion runs on its own completion thread so a
            // pre-fix inline CallerRuns execution cannot wedge the driver loop
            // itself — the monitor probe below is what detects the inlining.
            for (int epoch = 2; epoch <= COMPLETED_EPOCHS; epoch++) {
                completeEpochOnSeparateThread(coord, epoch);
            }
            // Wait until the overflow rejection actually happened (epoch 5's
            // completion returned). Pre-fix the epoch-4 completion thread parks
            // INSIDE the inline commit (monitor held) until the hold expires —
            // bounded by HOLD_MS.
            deadline = System.currentTimeMillis() + HOLD_MS + 10_000;
            while (coord.getCommitFanoutOverflows() < 1 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(coord.getCommitFanoutOverflows() >= 1,
                    "the queue-overflow fan-out must be counted (overflow re-dispatch), got "
                            + coord.getCommitFanoutOverflows());

            // Monitor probe (TestCommitFanoutOffMonitor2326 paradigm): one
            // lightweight synchronized call, timed. Pre-fix (CallerRuns inline)
            // the monitor is held by the completion thread's blocked commit and
            // this probe stalls for the whole hold window — deterministic red.
            long probeStart = System.currentTimeMillis();
            coord.registerTask(LOC_1);
            coord.unregisterTask(LOC_1);
            long probeElapsed = System.currentTimeMillis() - probeStart;
            assertTrue(probeElapsed < HOLD_MS / 2,
                    () -> "monitor blocked " + probeElapsed + "ms during the saturation window — "
                            + "commit fan-out ran inline under the coordinator monitor (C2-003)");

            // Lift the hold: the queued tasks drain and the OVERFLOWED tasks are
            // re-dispatched at a task boundary — every epoch's commit executes.
            participant.hold.set(false);
            participant.release.countDown();
            deadline = System.currentTimeMillis() + 20_000;
            while (participant.commits.get() < COMPLETED_EPOCHS && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(COMPLETED_EPOCHS, participant.commits.get(),
                    "every saturated fan-out must eventually execute (no lost commit — overflow re-dispatch)");
        } finally {
            participant.hold.set(false);
            participant.release.countDown();
            coord.shutdown();
        }
    }

    // ==================== helpers ====================

    private static CheckpointConfig config() {
        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(JOB_ID);
        config.setPipelineId(PIPELINE_ID);
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(3600_000L);
        config.setMinPause(0L);
        config.setMaxConcurrentCheckpoints(64);
        config.setStorageType("local");
        return config;
    }

    /** Completes one epoch inline on the caller thread (sync persist fallback). */
    private void completeEpoch(CheckpointCoordinator coord, long epochHintIgnored) throws InterruptedException {
        PendingCheckpoint pending = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending, "trigger must succeed for the epoch sequence");
        coord.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
        coord.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));
    }

    /** Completes one epoch on its own thread so inline execution cannot wedge the driver loop. */
    private void completeEpochOnSeparateThread(CheckpointCoordinator coord, long epoch) throws InterruptedException {
        Thread completer = new Thread(() -> {
            try {
                completeEpoch(coord, epoch);
            } catch (Exception e) {
                // Surface via the assertion path: a failed completion shows up as
                // a missing overflow/commit count.
            }
        }, "gb-w06-completer-" + epoch);
        completer.setDaemon(true);
        completer.start();
        completer.join(30_000);
    }

    /**
     * Participant that blocks every finishCommit while {@code hold} is set
     * (bounded by HOLD_MS so a pre-fix inline execution self-releases and the
     * driver stays bounded), recording every commit.
     */
    static final class HoldParticipant extends io.nop.stream.core.operators.StreamMap<Object, Object>
            implements CheckpointParticipant {
        private static final long serialVersionUID = 1L;
        final AtomicBoolean hold = new AtomicBoolean(true);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger commits = new AtomicInteger();

        HoldParticipant() {
            super(x -> x);
        }

        @Override
        public TaskStateSnapshot saveState(long epochId) {
            return TaskStateSnapshot.empty(LOC_1);
        }

        @Override
        public void prepareCommit(long epochId) {
        }

        @Override
        public void finishCommit(long epochId, boolean success) {
            commits.incrementAndGet();
            long holdDeadline = System.currentTimeMillis() + HOLD_MS;
            while (hold.get() && System.currentTimeMillis() < holdDeadline) {
                try {
                    release.await(50, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        @Override
        public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {
        }
    }
}
