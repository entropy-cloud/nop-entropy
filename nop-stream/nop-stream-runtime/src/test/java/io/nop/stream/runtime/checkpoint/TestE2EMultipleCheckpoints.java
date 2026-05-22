/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.execution.CheckpointBarrierTracker;
import io.nop.stream.core.operators.*;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestE2EMultipleCheckpoints {

    private static final TaskLocation LOC_0 = new TaskLocation("1", "0", "v0", 0);
    private static final TaskLocation LOC_0_P1 = new TaskLocation("1", "1", "v0", 0);

    @TempDir
    Path tempDir;

    private LocalFileCheckpointStorage storage;

    @AfterEach
    void teardown() throws Exception {
        storage.deleteAllCheckpoints("1");
    }

    @Test
    void testConsecutiveCheckpointIds() throws Exception {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointInterval(1000);
        CheckpointCoordinator coordinator = new CheckpointCoordinator("1", "0", idCounter, storage, config);
        coordinator.registerTask(LOC_0);

        try {
            List<Long> checkpointIds = new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
                assertNotNull(pending, "Checkpoint " + i + " should be triggered");
                long cpId = pending.getCheckpointId();
                checkpointIds.add(cpId);

                TaskStateSnapshot taskState = TaskStateSnapshot.builder(LOC_0)
                        .checkpointId(cpId)
                        .putOperatorState("iteration", String.valueOf(i))
                        .build();

                coordinator.acknowledgeTask(LOC_0, cpId, taskState);

                CompletedCheckpoint completed = pending.getCompletableFuture().get(5, TimeUnit.SECONDS);
                assertNotNull(completed);
                assertEquals(cpId, completed.getCheckpointId());
            }

            for (int i = 1; i < checkpointIds.size(); i++) {
                assertTrue(checkpointIds.get(i) > checkpointIds.get(i - 1),
                        "Checkpoint IDs should be strictly increasing: " + checkpointIds);
            }

            CompletedCheckpoint latest = coordinator.getLatestCheckpoint();
            assertNotNull(latest);
            assertEquals(checkpointIds.get(checkpointIds.size() - 1), latest.getCheckpointId());
            assertEquals("4", latest.getTaskState(LOC_0).getOperatorState("iteration"));
        } finally {
            coordinator.shutdown();
        }
    }

    @Test
    void testNotifyCheckpointCompleteInOrder() throws Exception {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointInterval(1000);
        CheckpointCoordinator coordinator = new CheckpointCoordinator("1", "0", idCounter, storage, config);
        coordinator.registerTask(LOC_0);

        try {
            List<Long> completedOrder = Collections.synchronizedList(new ArrayList<>());

            CheckpointListener listener = new CheckpointListener() {
                @Override
                public void notifyCheckpointComplete(long checkpointId) {
                    completedOrder.add(checkpointId);
                }

                @Override
                public void notifyCheckpointAborted(long checkpointId) {
                }
            };
            coordinator.addListener(listener);

            for (int i = 0; i < 3; i++) {
                PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
                assertNotNull(pending);
                long cpId = pending.getCheckpointId();

                TaskStateSnapshot taskState = TaskStateSnapshot.builder(LOC_0)
                        .checkpointId(cpId)
                        .putOperatorState("data", "checkpoint-" + i)
                        .build();

                coordinator.acknowledgeTask(LOC_0, cpId, taskState);
                pending.getCompletableFuture().get(5, TimeUnit.SECONDS);
            }

            assertEquals(3, completedOrder.size());
            for (int i = 1; i < completedOrder.size(); i++) {
                assertTrue(completedOrder.get(i) > completedOrder.get(i - 1),
                        "Completed checkpoints should arrive in order: " + completedOrder);
            }
        } finally {
            coordinator.shutdown();
        }
    }

    @Test
    void testOldCheckpointsCleanedUp() throws Exception {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointInterval(1000);
        config.setMaxRetainedCheckpoints(2);
        CheckpointCoordinator coordinator = new CheckpointCoordinator("1", "0", idCounter, storage, config);
        coordinator.registerTask(LOC_0);

        try {
            for (int i = 0; i < 5; i++) {
                CheckpointIDCounter iterCounter = new CheckpointIDCounter();
                CheckpointCoordinator iterCoordinator = new CheckpointCoordinator("1", "0", iterCounter, storage, config);
                iterCoordinator.registerTask(LOC_0);

                PendingCheckpoint pending = iterCoordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
                assertNotNull(pending);
                long cpId = pending.getCheckpointId();

                TaskStateSnapshot taskState = TaskStateSnapshot.builder(LOC_0)
                        .checkpointId(cpId)
                        .putOperatorState("data", "value-" + i)
                        .build();

                iterCoordinator.acknowledgeTask(LOC_0, cpId, taskState);
                pending.getCompletableFuture().get(5, TimeUnit.SECONDS);

                iterCoordinator.shutdown();
            }

            List<CompletedCheckpoint> remaining = storage.getAllCheckpoints("1");
            assertTrue(remaining.size() <= 2,
                    "At most 2 checkpoints should be retained after cleanup, but found: " + remaining.size());

            if (!remaining.isEmpty()) {
                CompletedCheckpoint last = remaining.get(remaining.size() - 1);
                Object data = last.getTaskState(LOC_0).getOperatorState("data");
                assertNotNull(data);
                assertEquals("value-4", data);
            }
        } finally {
            coordinator.shutdown();
        }
    }

    @Test
    void testMultipleCheckpointsWithBarrierTracker() throws Exception {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointInterval(100);
        config.setCheckpointTimeout(5000);
        CheckpointCoordinator coordinator = new CheckpointCoordinator("1", "1", idCounter, storage, config);
        coordinator.registerTask(LOC_0_P1);

        try {
            List<String> results = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger snapshotCount = new AtomicInteger(0);
            List<Long> completedCpIds = Collections.synchronizedList(new ArrayList<>());

            StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(new SourceFunction<String>() {
                private static final long serialVersionUID = 1L;

                @Override
                public void run(SourceContext<String> ctx) {
                    ctx.collect("a");
                    ctx.collect("b");
                }

                @Override
                public void cancel() {
                }
            });

            StreamMap<String, String> mapOp = new StreamMap<>(s -> s.toUpperCase());

            StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(new SinkFunction<String>() {
                private static final long serialVersionUID = 1L;

                @Override
                public void consume(String value) {
                    results.add(value);
                }
            });

            List<StreamOperator<?>> operators = Arrays.asList(sourceOp, mapOp, sinkOp);

            sourceOp.setOutput(new ChainingOutput<>(mapOp));
            mapOp.setOutput(new ChainingOutput<>(sinkOp));

            coordinator.addListener(new CheckpointListener() {
                @Override
                public void notifyCheckpointComplete(long checkpointId) {
                    completedCpIds.add(checkpointId);
                }

                @Override
                public void notifyCheckpointAborted(long checkpointId) {
                }
            });

            sourceOp.open();
            mapOp.open();
            sinkOp.open();

            for (int cp = 0; cp < 3; cp++) {
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<TaskStateSnapshot> snapshotRef = new AtomicReference<>();

                CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC_0_P1, operators, snapshot -> {
                    snapshotRef.set(snapshot);
                    coordinator.acknowledgeTask(LOC_0_P1, snapshot.getCheckpointId(), snapshot);
                    latch.countDown();
                });

                for (int i = 0; i < operators.size(); i++) {
                    if (operators.get(i) instanceof AbstractStreamOperator) {
                        final int opIndex = i;
                        ((AbstractStreamOperator<?>) operators.get(i)).setSnapshotCallback(
                                s -> {
                                    snapshotCount.incrementAndGet();
                                    tracker.acknowledgeOperator(opIndex, s);
                                }
                        );
                    }
                }

                PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
                assertNotNull(pending, "Checkpoint " + cp + " should be triggerable");
                long cpId = pending.getCheckpointId();

                tracker.triggerCheckpoint(cpId, pending.getTriggerTimestamp(), CheckpointType.CHECKPOINT);
                sourceOp.run();

                assertTrue(latch.await(5, TimeUnit.SECONDS), "Checkpoint " + cp + " should complete");
                assertNotNull(snapshotRef.get());
                assertEquals(cpId, snapshotRef.get().getCheckpointId());
            }

            assertTrue(snapshotCount.get() >= 9,
                    "Each of 3 operators should snapshot per checkpoint. Expected >= 9, got: " + snapshotCount.get());

            assertEquals(3, completedCpIds.size());
            for (int i = 1; i < completedCpIds.size(); i++) {
                assertTrue(completedCpIds.get(i) > completedCpIds.get(i - 1),
                        "Checkpoint IDs should be increasing: " + completedCpIds);
            }

            assertEquals(Arrays.asList("A", "B", "A", "B", "A", "B"), results);
        } finally {
            coordinator.shutdown();
        }
    }

    @Test
    void testCheckpointIdCounterPersistence() throws Exception {
        storage = new LocalFileCheckpointStorage(tempDir.toString());

        List<Long> firstRunIds = new ArrayList<>();
        CheckpointIDCounter idCounter1 = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointInterval(1000);

        CheckpointCoordinator coordinator1 = new CheckpointCoordinator("1", "0", idCounter1, storage, config);
        coordinator1.registerTask(LOC_0);

        try {
            for (int i = 0; i < 3; i++) {
                PendingCheckpoint pending = coordinator1.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
                assertNotNull(pending);
                firstRunIds.add(pending.getCheckpointId());

                TaskStateSnapshot taskState = TaskStateSnapshot.builder(LOC_0)
                        .putOperatorState("data", String.valueOf(i))
                        .build();
                coordinator1.acknowledgeTask(LOC_0, pending.getCheckpointId(), taskState);
                pending.getCompletableFuture().get(5, TimeUnit.SECONDS);
            }
        } finally {
            coordinator1.shutdown();
        }

        CheckpointIDCounter idCounter2 = new CheckpointIDCounter(firstRunIds.get(firstRunIds.size() - 1) + 1);
        CheckpointCoordinator coordinator2 = new CheckpointCoordinator("1", "0", idCounter2, storage, config);
        coordinator2.registerTask(LOC_0);

        try {
            PendingCheckpoint pending = coordinator2.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            assertNotNull(pending);
            long newId = pending.getCheckpointId();

            assertTrue(newId > firstRunIds.get(firstRunIds.size() - 1),
                    "New checkpoint ID should be greater than previous ones. Got: " + newId +
                            ", previous max: " + firstRunIds.get(firstRunIds.size() - 1));

            CompletedCheckpoint restored = coordinator2.restoreFromCheckpoint();
            assertNotNull(restored);
            assertEquals(firstRunIds.get(firstRunIds.size() - 1), restored.getCheckpointId());
        } finally {
            coordinator2.shutdown();
        }
    }
}
