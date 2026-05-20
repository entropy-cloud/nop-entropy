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
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
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

class TestE2ETwoPhaseCommitSink {

    @TempDir
    Path tempDir;

    private LocalFileCheckpointStorage storage;
    private CheckpointCoordinator coordinator;

    @BeforeEach
    void setup() {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointInterval(1000);
        coordinator = new CheckpointCoordinator(1L, 0, idCounter, storage, config);
        coordinator.registerTask(0L);
    }

    @AfterEach
    void teardown() throws Exception {
        coordinator.shutdown();
        storage.deleteAllCheckpoints(1);
    }

    @Test
    void testTwoPhaseCommitSequence() throws Exception {
        List<String> actions = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger commitCount = new AtomicInteger(0);

        TwoPhaseCommitSinkFunction<String> tpcSink = new TwoPhaseCommitSinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void beginTransaction() {
                actions.add("begin");
            }

            @Override
            public void invoke(String value) {
                actions.add("invoke:" + value);
            }

            @Override
            public void preCommit(long checkpointId) {
                actions.add("preCommit:" + checkpointId);
            }

            @Override
            public void commit(long checkpointId) {
                actions.add("commit:" + checkpointId);
                commitCount.incrementAndGet();
            }

            @Override
            public void rollback() {
                actions.add("rollback");
            }
        };

        tpcSink.beginTransaction();

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(new SourceFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("a");
                ctx.collect("b");
                ctx.collect("c");
            }

            @Override
            public void cancel() {
            }
        });

        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(tpcSink);

        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, sinkOp);

        ChainingOutput<String> sourceToSink = new ChainingOutput<>(sinkOp);
        sourceOp.setOutput(sourceToSink);

        CountDownLatch checkpointComplete = new CountDownLatch(1);
        AtomicReference<TaskStateSnapshot> capturedSnapshot = new AtomicReference<>();

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(0L, operators, snapshot -> {
            capturedSnapshot.set(snapshot);
            coordinator.acknowledgeTask(0L, snapshot.getCheckpointId(), snapshot);
            checkpointComplete.countDown();
        });

        for (int i = 0; i < operators.size(); i++) {
            if (operators.get(i) instanceof AbstractStreamOperator) {
                final int opIndex = i;
                ((AbstractStreamOperator<?>) operators.get(i)).setSnapshotCallback(
                        snapshot -> tracker.acknowledgeOperator(opIndex, snapshot)
                );
            }
        }

        coordinator.addListener(sinkOp);

        sourceOp.open();
        sinkOp.open();

        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        long cpId = pending.getCheckpointId();

        tracker.triggerCheckpoint(cpId, pending.getTriggerTimestamp(), CheckpointType.CHECKPOINT);

        sourceOp.run();

        assertTrue(checkpointComplete.await(5, TimeUnit.SECONDS));

        assertTrue(actions.contains("begin"), "begin should have been called. Actions: " + actions);
        assertTrue(actions.contains("invoke:a"), "invoke:a should have been called. Actions: " + actions);
        assertTrue(actions.contains("invoke:b"), "invoke:b should have been called. Actions: " + actions);
        assertTrue(actions.contains("invoke:c"), "invoke:c should have been called. Actions: " + actions);
        assertTrue(actions.contains("preCommit:" + cpId), "preCommit should have been called. Actions: " + actions);
        assertTrue(commitCount.get() > 0, "commit should have been called. Actions: " + actions);

        int beginIndex = actions.indexOf("begin");
        int preCommitIndex = actions.indexOf("preCommit:" + cpId);
        int commitIndex = actions.indexOf("commit:" + cpId);
        assertTrue(beginIndex < preCommitIndex, "begin should precede preCommit");
        assertTrue(preCommitIndex < commitIndex, "preCommit should precede commit");

        coordinator.shutdown();
    }

    @Test
    void testRollbackOnAbort() throws Exception {
        List<String> actions = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger rollbackCount = new AtomicInteger(0);

        TwoPhaseCommitSinkFunction<String> tpcSink = new TwoPhaseCommitSinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void beginTransaction() {
                actions.add("begin");
            }

            @Override
            public void invoke(String value) {
                actions.add("invoke:" + value);
            }

            @Override
            public void preCommit(long checkpointId) {
                actions.add("preCommit:" + checkpointId);
            }

            @Override
            public void commit(long checkpointId) {
                actions.add("commit:" + checkpointId);
            }

            @Override
            public void rollback() {
                actions.add("rollback");
                rollbackCount.incrementAndGet();
            }
        };

        tpcSink.beginTransaction();

        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(tpcSink);
        sinkOp.open();

        sinkOp.restoreState(OperatorSnapshotResult.empty());

        assertTrue(rollbackCount.get() >= 1, "rollback should have been called during restore. Actions: " + actions);
        assertTrue(actions.contains("rollback"), "Actions should contain rollback: " + actions);

        long beginCount = actions.stream().filter(a -> a.equals("begin")).count();
        assertTrue(beginCount >= 2, "begin should have been called at least twice (initial + after rollback). Actions: " + actions);
    }

    @Test
    void testNotifyCheckpointCompleteReceived() throws Exception {
        List<Long> completedCheckpoints = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger completeCount = new AtomicInteger(0);

        SinkFunction<String> trackingSink = new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
            }
        };

        CheckpointListener listener = new CheckpointListener() {
            @Override
            public void notifyCheckpointComplete(long checkpointId) {
                completedCheckpoints.add(checkpointId);
                completeCount.incrementAndGet();
            }

            @Override
            public void notifyCheckpointAborted(long checkpointId) {
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(new SourceFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("x");
            }

            @Override
            public void cancel() {
            }
        });

        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(trackingSink);

        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, sinkOp);
        sourceOp.setOutput(new ChainingOutput<>(sinkOp));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TaskStateSnapshot> capturedSnapshot = new AtomicReference<>();

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(0L, operators, snapshot -> {
            capturedSnapshot.set(snapshot);
            coordinator.acknowledgeTask(0L, snapshot.getCheckpointId(), snapshot);
            latch.countDown();
        });

        for (int i = 0; i < operators.size(); i++) {
            if (operators.get(i) instanceof AbstractStreamOperator) {
                final int opIndex = i;
                ((AbstractStreamOperator<?>) operators.get(i)).setSnapshotCallback(
                        snapshot -> tracker.acknowledgeOperator(opIndex, snapshot)
                );
            }
        }

        coordinator.addListener(listener);

        sourceOp.open();
        sinkOp.open();

        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        long cpId = pending.getCheckpointId();

        tracker.triggerCheckpoint(cpId, pending.getTriggerTimestamp(), CheckpointType.CHECKPOINT);
        sourceOp.run();

        CompletedCheckpoint completed = pending.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(completed);

        assertEquals(1, completeCount.get());
        assertEquals(cpId, completedCheckpoints.get(0));

        coordinator.shutdown();
    }
}
