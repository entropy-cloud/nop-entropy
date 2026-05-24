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
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.execution.CheckpointBarrierTracker;
import io.nop.stream.core.operators.*;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestE2EWindowOperatorWithCheckpoint {

    private static final TaskLocation LOC_0 = new TaskLocation("1", "0", "v0", 0);

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
        coordinator = new CheckpointCoordinator("1", "0", idCounter, storage, config);
        coordinator.registerTask(LOC_0);
    }

    @AfterEach
    void teardown() throws Exception {
        coordinator.shutdown();
        storage.deleteAllCheckpoints("1");
    }

    @Test
    void testWindowStateSnapshotAndRestore() throws Exception {
        MemoryStateBackend stateBackend = new MemoryStateBackend();
        IKeyedStateBackend<String> keyedBackend = stateBackend.createKeyedStateBackend(String.class);

        ValueStateDescriptor<Long> sumDescriptor = new ValueStateDescriptor<>("window-sum", Long.class, 0L);
        ValueStateDescriptor<Long> countDescriptor = new ValueStateDescriptor<>("window-count", Long.class, 0L);

        keyedBackend.setCurrentKey("window-1");
        ValueState<Long> sumState = keyedBackend.getState(sumDescriptor);
        sumState.update(45L);
        ValueState<Long> countState = keyedBackend.getState(countDescriptor);
        countState.update(9L);

        AbstractStreamOperator<Void> windowOp = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };
        windowOp.setStateBackend(stateBackend);
        windowOp.setKeyedStateBackend(keyedBackend);

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        TestOutput<Void> output = new TestOutput<>();
        windowOp.setOutput(output);
        windowOp.processBarrier(barrier);

        OperatorSnapshotResult snapshot = windowOp.getLastSnapshotResult();
        assertNotNull(snapshot);
        assertFalse(snapshot.isEmpty());

        IKeyedStateBackend<String> restoredBackend = stateBackend.createKeyedStateBackend(String.class);
        AbstractStreamOperator<Void> restoredOp = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };
        restoredOp.setStateBackend(stateBackend);
        restoredOp.setKeyedStateBackend(restoredBackend);
        restoredOp.restoreState(snapshot);

        ValueState<Long> restoredSum = restoredBackend.getState(sumDescriptor);
        ValueState<Long> restoredCount = restoredBackend.getState(countDescriptor);

        restoredBackend.setCurrentKey("window-1");
        assertEquals(45L, restoredSum.value());
        assertEquals(9L, restoredCount.value());

        restoredSum.update(55L);
        assertEquals(55L, restoredSum.value());

        keyedBackend.close();
        restoredBackend.close();
    }

    @Test
    void testWindowMapStateSnapshotAndRestore() throws Exception {
        MemoryStateBackend stateBackend = new MemoryStateBackend();
        IKeyedStateBackend<String> keyedBackend = stateBackend.createKeyedStateBackend(String.class);

        MapStateDescriptor<String, Integer> elementsDescriptor = new MapStateDescriptor<>("window-elements", String.class, Integer.class);

        keyedBackend.setCurrentKey("window-A");
        MapState<String, Integer> elementsState = keyedBackend.getMapState(elementsDescriptor);
        elementsState.put("elem1", 10);
        elementsState.put("elem2", 20);
        elementsState.put("elem3", 30);

        AbstractStreamOperator<Void> windowOp = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };
        windowOp.setStateBackend(stateBackend);
        windowOp.setKeyedStateBackend(keyedBackend);

        CheckpointBarrier barrier = new CheckpointBarrier(2L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        TestOutput<Void> output = new TestOutput<>();
        windowOp.setOutput(output);
        windowOp.processBarrier(barrier);

        OperatorSnapshotResult snapshot = windowOp.getLastSnapshotResult();
        assertNotNull(snapshot);
        assertFalse(snapshot.isEmpty());

        IKeyedStateBackend<String> restoredBackend = stateBackend.createKeyedStateBackend(String.class);
        AbstractStreamOperator<Void> restoredOp = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };
        restoredOp.setStateBackend(stateBackend);
        restoredOp.setKeyedStateBackend(restoredBackend);
        restoredOp.restoreState(snapshot);

        MapState<String, Integer> restoredElements = restoredBackend.getMapState(elementsDescriptor);
        restoredBackend.setCurrentKey("window-A");
        assertEquals(Integer.valueOf(10), restoredElements.get("elem1"));
        assertEquals(Integer.valueOf(20), restoredElements.get("elem2"));
        assertEquals(Integer.valueOf(30), restoredElements.get("elem3"));

        restoredElements.put("elem4", 40);
        assertEquals(Integer.valueOf(40), restoredElements.get("elem4"));
        assertEquals(4, countIterable(restoredElements.values()));

        keyedBackend.close();
        restoredBackend.close();
    }

    @Test
    void testWindowStateAcrossMultipleKeys() throws Exception {
        MemoryStateBackend stateBackend = new MemoryStateBackend();
        IKeyedStateBackend<String> keyedBackend = stateBackend.createKeyedStateBackend(String.class);

        ValueStateDescriptor<Long> sumDescriptor = new ValueStateDescriptor<>("window-sum", Long.class, 0L);

        keyedBackend.setCurrentKey("window-X");
        keyedBackend.getState(sumDescriptor).update(100L);

        keyedBackend.setCurrentKey("window-Y");
        keyedBackend.getState(sumDescriptor).update(200L);

        keyedBackend.setCurrentKey("window-Z");
        keyedBackend.getState(sumDescriptor).update(300L);

        AbstractStreamOperator<Void> windowOp = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;
        };
        windowOp.setStateBackend(stateBackend);
        windowOp.setKeyedStateBackend(keyedBackend);

        CheckpointBarrier barrier = new CheckpointBarrier(3L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        TestOutput<Void> output = new TestOutput<>();
        windowOp.setOutput(output);
        windowOp.processBarrier(barrier);

        OperatorSnapshotResult snapshot = windowOp.getLastSnapshotResult();
        assertNotNull(snapshot);

        IKeyedStateBackend<String> restoredBackend = stateBackend.createKeyedStateBackend(String.class);
        restoredBackend.restoreState(snapshot.getKeyedState("keyed-state", StateSnapshot.class));

        ValueState<Long> restoredSum = restoredBackend.getState(sumDescriptor);
        restoredBackend.setCurrentKey("window-X");
        assertEquals(100L, restoredSum.value());

        restoredBackend.setCurrentKey("window-Y");
        assertEquals(200L, restoredSum.value());

        restoredBackend.setCurrentKey("window-Z");
        assertEquals(300L, restoredSum.value());

        keyedBackend.close();
        restoredBackend.close();
    }

    @Test
    void testWindowOperatorPipelineWithCheckpoint() throws Exception {
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch checkpointComplete = new CountDownLatch(1);
        AtomicReference<TaskStateSnapshot> capturedSnapshot = new AtomicReference<>();

        SourceFunction<String> source = new SourceFunction<String>() {
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
        };

        MemoryStateBackend stateBackend = new MemoryStateBackend();
        IKeyedStateBackend<String> keyedBackend = stateBackend.createKeyedStateBackend(String.class);

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);

        StreamMap<String, String> windowSimOp = new StreamMap<>(new MapFunction<String, String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public String map(String value) throws Exception {
                ValueStateDescriptor<Long> countDesc =
                        new ValueStateDescriptor<>("window-count", Long.class, 0L);
                keyedBackend.setCurrentKey(value);
                ValueState<Long> countState = keyedBackend.getState(countDesc);
                long current = countState.value();
                countState.update(current + 1);
                return value + ":" + (current + 1);
            }
        });
        windowSimOp.setStateBackend(stateBackend);
        windowSimOp.setKeyedStateBackend(keyedBackend);

        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
                results.add(value);
            }
        });

        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, windowSimOp, sinkOp);
        sourceOp.setOutput(new ChainingOutput<>(windowSimOp));
        windowSimOp.setOutput(new ChainingOutput<>(sinkOp));

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC_0, operators, snapshot -> {
            capturedSnapshot.set(snapshot);
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

        sourceOp.open();
        windowSimOp.open();
        sinkOp.open();

        // Process data first so keyed state is populated before snapshot
        sourceOp.run();

        // Then trigger checkpoint to capture the state.
        // Since source has already finished, triggerCheckpoint offers to the source's queue,
        // but no collect() calls remain to pull it. We drain and inject manually.
        tracker.triggerCheckpoint(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        CheckpointBarrier pendingBarrier = sourceOp.drainPendingBarrier();
        if (pendingBarrier != null) {
            sourceOp.injectBarrier(pendingBarrier);
        }

        assertTrue(checkpointComplete.await(5, TimeUnit.SECONDS));
        assertEquals(Arrays.asList("a:1", "b:1", "c:1"), results);
        assertNotNull(capturedSnapshot.get());

        OperatorSnapshotResult windowSnapshot = windowSimOp.getLastSnapshotResult();
        assertNotNull(windowSnapshot);

        ValueStateDescriptor<Long> countDesc = new ValueStateDescriptor<>("window-count", Long.class, 0L);
        IKeyedStateBackend<String> restoredBackend = stateBackend.createKeyedStateBackend(String.class);
        restoredBackend.restoreState(windowSnapshot.getKeyedState("keyed-state", StateSnapshot.class));

        restoredBackend.setCurrentKey("a");
        assertEquals(1L, restoredBackend.getState(countDesc).value());

        restoredBackend.setCurrentKey("b");
        assertEquals(1L, restoredBackend.getState(countDesc).value());

        sourceOp.close();
        windowSimOp.close();
        sinkOp.close();
        keyedBackend.close();
        restoredBackend.close();
    }

    private <T> int countIterable(Iterable<T> iterable) {
        int count = 0;
        for (T ignored : iterable) {
            count++;
        }
        return count;
    }
}
