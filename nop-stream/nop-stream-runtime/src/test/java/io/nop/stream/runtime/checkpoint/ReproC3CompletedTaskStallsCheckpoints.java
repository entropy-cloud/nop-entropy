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
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * PRE-FIX repro for plan 369 Phase 1 C3 (defect recorded in
 * ai-dev/analysis/nop-stream/09a-flink2.3-compare-checkpoint-fault-tolerance.md):
 * a task that reached its terminal COMPLETED state stayed in the checkpoint
 * ACK set (the pre-fix production wiring — {@code unregisterTask} had zero
 * callers, and no terminal-scan notified the coordinator), so every
 * subsequent epoch waited for the dead task's ACK until the full
 * {@code checkpointTimeout} and aborted — the bounded-job 600s
 * timeout-abort loop.
 *
 * <p>This test pins the MECHANISM the production job hit pre-fix: after a
 * real completed checkpoint, a task completes WITHOUT any coordinator
 * notification (no {@code markTaskCompleted} call — exactly the pre-fix
 * wiring), and the next epoch can only end in the timeout abort. Post-fix the
 * LOCAL {@code SupervisionLoop} scan and the DISTRIBUTED
 * {@code reportTaskStatus} tombstone call {@code markTaskCompleted}, which
 * contracts the completed task and lets the epoch complete.
 *
 * <p>Repro run recorded in {@code _tmp/r6-p1-c3-repro.log}.
 */
class ReproC3CompletedTaskStallsCheckpoints {

    private static final TaskLocation LOC_1 = new TaskLocation("j", "p", "v1", 0);
    private static final TaskLocation LOC_2 = new TaskLocation("j", "p", "v2", 0);

    @TempDir
    Path tempDir;

    private CheckpointCoordinator coordinator;

    @BeforeEach
    void setUp() {
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointTimeout(400L)
                .minPause(0L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(5)
                .asyncSnapshotEnabled(false)
                .build();
        coordinator = new CheckpointCoordinator("j", "p", idCounter,
                new LocalFileCheckpointStorage(tempDir.toString()), config);
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
    }

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    @Test
    void testCompletedTaskWithoutNotificationStallsNextEpoch() throws Exception {
        // Epoch 1 completes normally — a real completed checkpoint exists (the
        // task's state is durable and would be inheritable).
        PendingCheckpoint p1 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(p1, "Epoch 1 triggers");
        coordinator.acknowledgeTask(LOC_1, p1.getCheckpointId(),
                TaskStateSnapshot.builder(LOC_1).checkpointId(p1.getCheckpointId())
                        .putOperatorState("op", "e1-v1").build());
        coordinator.acknowledgeTask(LOC_2, p1.getCheckpointId(),
                TaskStateSnapshot.builder(LOC_2).checkpointId(p1.getCheckpointId())
                        .putOperatorState("op", "e1-v2").build());
        assertNotNull(p1.getCompletableFuture().get(5, TimeUnit.SECONDS), "Epoch 1 completes");

        // THE PRE-FIX WIRING: task v2 reaches its terminal COMPLETED state but
        // NOTHING tells the coordinator (pre-fix: no terminal scan, no
        // tombstone — markTaskCompleted did not exist). Deliberately NO
        // markTaskCompleted(LOC_2) here.

        // Epoch 2 stalls: both tasks are still participants, v2 can never ACK,
        // so the epoch can only end in the checkpoint-timeout abort — the
        // bounded-job 600s timeout-abort loop in unit-scale form.
        PendingCheckpoint p2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(p2, "Epoch 2 triggers with BOTH tasks still in the ACK set");
        assertEquals(2, p2.getNumberOfNotAcknowledgedTasks(),
                "PRE-FIX BUG confirmed: the completed task is still a checkpoint participant");
        coordinator.acknowledgeTask(LOC_1, p2.getCheckpointId(),
                TaskStateSnapshot.builder(LOC_1).checkpointId(p2.getCheckpointId())
                        .putOperatorState("op", "e2-v1").build());

        long start = System.currentTimeMillis();
        try {
            p2.getCompletableFuture().get(5, TimeUnit.SECONDS);
            fail("Expected the epoch to abort at the checkpoint timeout (pre-fix stall loop)");
        } catch (ExecutionException e) {
            assertTrue(System.currentTimeMillis() - start >= 350L,
                    "The epoch waited for the full checkpoint timeout before aborting");
            assertTrue(String.valueOf(e.getCause()).contains("Timeout")
                            || String.valueOf(e.getCause()).contains("abort"),
                    "The epoch ended in the timeout abort: " + e.getCause());
        }
    }
}
