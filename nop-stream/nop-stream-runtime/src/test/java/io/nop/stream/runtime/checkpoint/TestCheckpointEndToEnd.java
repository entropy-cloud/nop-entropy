/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.execution.CheckpointBarrierTracker;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.*;
import io.nop.stream.core.operators.*;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class TestCheckpointEndToEnd {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
    }

    @Test
    void testCheckpointLifecycle() throws Exception {
        List<Integer> collected = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger checkpointCompleteCount = new AtomicInteger(0);
        AtomicLong lastCompleteCheckpointId = new AtomicLong(-1);

        // Source that emits slowly
        SourceFunction<Integer> slowSource = new SourceFunction<Integer>() {
            private static final long serialVersionUID = 1L;
            private volatile boolean running = true;

            @Override
            public void run(SourceContext<Integer> ctx) throws Exception {
                for (int i = 1; i <= 5; i++) {
                    ctx.collect(i);
                    Thread.sleep(50);
                }
            }

            @Override
            public void cancel() {
                running = false;
            }
        };

        // Map function
        MapFunction<Integer, Integer> doubler = value -> value * 2;

        // Sink that records checkpoint completion
        SinkFunction<Integer> collectingSink = new SinkFunction<Integer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(Integer value) {
                collected.add(value);
            }
        };

        // Build operators
        StreamSourceOperator<Integer> sourceOp = new StreamSourceOperator<>(slowSource);
        StreamMap<Integer, Integer> mapOp = new StreamMap<>(doubler);

        // Wrap sink to track checkpoint notifications
        StreamSinkOperator<Integer> sinkOp = new StreamSinkOperator<>(new CheckpointListenerSink<>(collectingSink, checkpointCompleteCount, lastCompleteCheckpointId));

        // Build chain
        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, mapOp, sinkOp);
        OperatorChain chain = new OperatorChain(operators);

        // Wire directly (like StreamTaskInvokable does)
        ChainingOutput<Integer> sourceToMap = new ChainingOutput<>(mapOp);
        ChainingOutput<Integer> mapToSink = new ChainingOutput<>(sinkOp);
        sourceOp.setOutput(sourceToMap);
        mapOp.setOutput(mapToSink);

        // Setup checkpoint coordinator
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(100);
        config.setCheckpointTimeout(5000);

        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointCoordinator coordinator = new CheckpointCoordinator(1L, 1, idCounter, storage, config);
        coordinator.registerTask(0L);

        // Register listeners
        coordinator.addListener((CheckpointListener) sinkOp.getUserFunction());

        // Create barrier tracker
        AtomicInteger ackCount = new AtomicInteger(0);
        AtomicReference<TaskStateSnapshot> lastSnapshot = new AtomicReference<>();

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(0L, operators, snapshot -> {
            lastSnapshot.set(snapshot);
            coordinator.acknowledgeTask(0L, snapshot.getCheckpointId(), snapshot);
            ackCount.incrementAndGet();
        });

        // Setup snapshot callbacks on operators
        for (int i = 0; i < operators.size(); i++) {
            if (operators.get(i) instanceof AbstractStreamOperator) {
                final int opIndex = i;
                ((AbstractStreamOperator<?>) operators.get(i)).setSnapshotCallback(
                    snapshot -> tracker.acknowledgeOperator(opIndex, snapshot)
                );
            }
        }

        // Open operators
        sourceOp.open();
        mapOp.open();
        sinkOp.open();

        // Trigger a checkpoint manually
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);

        // Inject barrier into source
        tracker.triggerCheckpoint(pending.getCheckpointId(), pending.getTriggerTimestamp(), CheckpointType.CHECKPOINT);

        // Run source (which emits data and the barrier has already been injected)
        sourceOp.run();

        // Wait for checkpoint to complete
        CompletedCheckpoint completed = pending.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(completed);
        assertEquals(pending.getCheckpointId(), completed.getCheckpointId());

        // Verify data flowed correctly
        assertEquals(Arrays.asList(2, 4, 6, 8, 10), collected);

        // Verify snapshot was captured
        assertNotNull(lastSnapshot.get());

        coordinator.shutdown();
    }

    @Test
    void testTwoPhaseCommitLifecycle() throws Exception {
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

        // Build operators
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

        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(tpcSink);

        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, sinkOp);
        OperatorChain chain = new OperatorChain(operators);

        ChainingOutput<String> sourceToSink = new ChainingOutput<>(sinkOp);
        sourceOp.setOutput(sourceToSink);

        // Setup checkpoint
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(100);
        config.setCheckpointTimeout(5000);

        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointCoordinator coordinator = new CheckpointCoordinator(1L, 1, idCounter, storage, config);
        coordinator.registerTask(0L);

        coordinator.addListener(sinkOp);

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(0L, operators, snapshot -> {
            coordinator.acknowledgeTask(0L, snapshot.getCheckpointId(), snapshot);
        });

        for (int i = 0; i < operators.size(); i++) {
            if (operators.get(i) instanceof AbstractStreamOperator) {
                final int opIndex = i;
                ((AbstractStreamOperator<?>) operators.get(i)).setSnapshotCallback(
                    snapshot -> tracker.acknowledgeOperator(opIndex, snapshot)
                );
            }
        }

        sourceOp.open();
        sinkOp.open();

        // Trigger checkpoint
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        long cpId = pending.getCheckpointId();

        // Inject barrier - this should trigger preCommit on the sink
        tracker.triggerCheckpoint(cpId, pending.getTriggerTimestamp(), CheckpointType.CHECKPOINT);

        // Now run source to emit data (barrier already injected)
        sourceOp.run();

        // Wait for checkpoint completion
        CompletedCheckpoint completed = pending.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(completed);

        // Verify preCommit was called during barrier processing
        assertTrue(actions.contains("preCommit:" + cpId), "preCommit should have been called. Actions: " + actions);

        // Verify commit was called after checkpoint completion
        assertTrue(commitCount.get() > 0, "commit should have been called. Actions: " + actions);
        assertTrue(actions.contains("commit:" + cpId), "commit should have been called. Actions: " + actions);

        coordinator.shutdown();
    }

    @Test
    void testBarrierTrackingWithMultipleOperators() throws Exception {
        // Build source -> map -> map -> sink chain
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger snapshotCount = new AtomicInteger(0);

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

        StreamMap<String, String> map1 = new StreamMap<>(s -> s + "1");
        StreamMap<String, String> map2 = new StreamMap<>(s -> s + "2");

        StreamSinkOperator<String> sink = new StreamSinkOperator<>(new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
                results.add(value);
            }
        });

        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, map1, map2, sink);

        // Wire chain
        sourceOp.setOutput(new ChainingOutput<>(map1));
        map1.setOutput(new ChainingOutput<>(map2));
        map2.setOutput(new ChainingOutput<>(sink));

        // Setup tracker
        CountDownLatch checkpointComplete = new CountDownLatch(1);
        AtomicReference<TaskStateSnapshot> capturedSnapshot = new AtomicReference<>();

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(0L, operators, snapshot -> {
            capturedSnapshot.set(snapshot);
            checkpointComplete.countDown();
        });

        for (int i = 0; i < operators.size(); i++) {
            if (operators.get(i) instanceof AbstractStreamOperator) {
                final int opIndex = i;
                ((AbstractStreamOperator<?>) operators.get(i)).setSnapshotCallback(
                    snapshot -> {
                        snapshotCount.incrementAndGet();
                        tracker.acknowledgeOperator(opIndex, snapshot);
                    }
                );
            }
        }

        // Open operators
        for (StreamOperator<?> op : operators) {
            if (op instanceof AbstractStreamOperator) {
                ((AbstractStreamOperator<?>) op).open();
            }
        }

        // Trigger checkpoint
        tracker.triggerCheckpoint(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);

        // Run source (barrier already injected)
        sourceOp.run();

        // Wait for checkpoint completion
        assertTrue(checkpointComplete.await(5, TimeUnit.SECONDS), "Checkpoint should complete within timeout");

        // All 4 operators should have snapshotted
        assertEquals(4, snapshotCount.get());

        // Verify data
        assertEquals(Collections.singletonList("x12"), results);

        // Verify snapshot was captured
        assertNotNull(capturedSnapshot.get());
        assertEquals(1L, capturedSnapshot.get().getCheckpointId());
    }

    /**
     * Sink wrapper that implements CheckpointListener for testing.
     */
    private static class CheckpointListenerSink<T> implements SinkFunction<T>, CheckpointListener {
        private static final long serialVersionUID = 1L;

        private final SinkFunction<T> delegate;
        private final AtomicInteger completeCount;
        private final AtomicLong lastCompleteId;

        CheckpointListenerSink(SinkFunction<T> delegate, AtomicInteger completeCount, AtomicLong lastCompleteId) {
            this.delegate = delegate;
            this.completeCount = completeCount;
            this.lastCompleteId = lastCompleteId;
        }

        @Override
        public void consume(T value) throws Exception {
            delegate.consume(value);
        }

        @Override
        public void notifyCheckpointComplete(long checkpointId) {
            completeCount.incrementAndGet();
            lastCompleteId.set(checkpointId);
        }

        @Override
        public void notifyCheckpointAborted(long checkpointId) {
        }
    }
}
