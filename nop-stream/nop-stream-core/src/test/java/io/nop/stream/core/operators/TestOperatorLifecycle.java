/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.FunctionInitializationContext;
import io.nop.stream.core.checkpoint.FunctionSnapshotContext;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.AbstractRichFunction;
import io.nop.stream.core.common.functions.ICheckpointedFunction;
import io.nop.stream.core.common.functions.RichFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.configuration.Configuration;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class TestOperatorLifecycle {

    @Test
    void testLifecycleOrder() throws Exception {
        List<String> lifecycleCalls = new ArrayList<>();

        TestOutput<String> testOutput = new TestOutput<>();
        AbstractStreamOperator<String> operator = new AbstractStreamOperator<>() {
            @Override
            public void open() throws Exception {
                lifecycleCalls.add("open");
            }

            @Override
            public void processWatermark(Watermark mark) throws Exception {
                lifecycleCalls.add("processWatermark:" + mark.getTimestamp());
                super.processWatermark(mark);
            }

            @Override
            public void close() throws Exception {
                lifecycleCalls.add("close");
            }
        };

        operator.setOutput((Output) testOutput);
        operator.open();
        operator.processWatermark(new Watermark(100));
        operator.close();

        assertEquals(3, lifecycleCalls.size());
        assertEquals("open", lifecycleCalls.get(0));
        assertEquals("processWatermark:100", lifecycleCalls.get(1));
        assertEquals("close", lifecycleCalls.get(2));
    }

    @Test
    void testCloseIsAlwaysCalled() throws Exception {
        List<String> lifecycleCalls = new ArrayList<>();

        TestOutput<String> testOutput = new TestOutput<>();
        AbstractStreamOperator<String> operator = new AbstractStreamOperator<>() {
            @Override
            public void open() throws Exception {
                lifecycleCalls.add("open");
            }

            @Override
            public void close() throws Exception {
                lifecycleCalls.add("close");
            }
        };

        operator.setOutput((Output) testOutput);
        operator.open();
        operator.close();

        assertTrue(lifecycleCalls.contains("open"));
        assertTrue(lifecycleCalls.contains("close"));
    }

    @Test
    void testUdfOpenCallsRichFunctionOpen() throws Exception {
        AtomicBoolean openCalled = new AtomicBoolean(false);
        RichFunction richFunction = new AbstractRichFunction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void open(Configuration parameters) {
                openCalled.set(true);
            }
        };

        AbstractUdfStreamOperator<Void, RichFunction> operator = new AbstractUdfStreamOperator<>(richFunction) {
            private static final long serialVersionUID = 1L;
        };
        TestOutput<Void> output = new TestOutput<>();
        operator.setOutput(output);
        operator.open();
        assertTrue(openCalled.get());
    }

    @Test
    void testUdfFinishCallsSinkFunctionFinish() throws Exception {
        AtomicBoolean finishCalled = new AtomicBoolean(false);
        SinkFunction<String> sink = new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
            }

            @Override
            public void finish() throws Exception {
                finishCalled.set(true);
            }
        };

        StreamSinkOperator<String> operator = new StreamSinkOperator<>(sink);
        TestOutput<Void> output = new TestOutput<>();
        operator.setOutput(output);
        operator.open();
        operator.finish();
        assertTrue(finishCalled.get());
    }

    @Test
    void testUdfSnapshotStateCallsCheckpointedFunction() throws Exception {
        AtomicBoolean snapshotCalled = new AtomicBoolean(false);
        AtomicLong capturedCheckpointId = new AtomicLong(-1);
        AtomicLong capturedTimestamp = new AtomicLong(-1);

        ICheckpointedFunction fn = new ICheckpointedFunction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void snapshotState(FunctionSnapshotContext context) {
                snapshotCalled.set(true);
                capturedCheckpointId.set(context.getCheckpointId());
                capturedTimestamp.set(context.getCheckpointTimestamp());
            }

            @Override
            public void initializeState(FunctionInitializationContext context) {
            }
        };

        AbstractUdfStreamOperator<Void, ICheckpointedFunction> operator = new AbstractUdfStreamOperator<>(fn) {
            private static final long serialVersionUID = 1L;
        };
        TestOutput<Void> output = new TestOutput<>();
        operator.setOutput(output);

        StateSnapshotContext ctx = new StateSnapshotContext(42L, 1234567890L);
        operator.snapshotState(ctx);

        assertTrue(snapshotCalled.get());
        assertEquals(42L, capturedCheckpointId.get());
        assertEquals(1234567890L, capturedTimestamp.get());
    }

    @Test
    void testUdfInitializeStateCallsCheckpointedFunction() throws Exception {
        AtomicBoolean initCalled = new AtomicBoolean(false);
        AtomicBoolean isRestored = new AtomicBoolean(false);

        ICheckpointedFunction fn = new ICheckpointedFunction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void snapshotState(FunctionSnapshotContext context) {
            }

            @Override
            public void initializeState(FunctionInitializationContext context) {
                initCalled.set(true);
                isRestored.set(context.isRestored());
            }
        };

        AbstractUdfStreamOperator<Void, ICheckpointedFunction> operator = new AbstractUdfStreamOperator<>(fn) {
            private static final long serialVersionUID = 1L;
        };

        TaskStateSnapshot snapshot = new TaskStateSnapshot(null, 42L);
        snapshot.putOperatorState("test", "value");
        operator.initializeState(snapshot);

        assertTrue(initCalled.get());
        assertTrue(isRestored.get());
    }

    @Test
    void testUdfInitializeStateWithEmptySnapshot() throws Exception {
        AtomicBoolean initCalled = new AtomicBoolean(false);
        AtomicBoolean isRestored = new AtomicBoolean(false);

        ICheckpointedFunction fn = new ICheckpointedFunction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void snapshotState(FunctionSnapshotContext context) {
            }

            @Override
            public void initializeState(FunctionInitializationContext context) {
                initCalled.set(true);
                isRestored.set(context.isRestored());
            }
        };

        AbstractUdfStreamOperator<Void, ICheckpointedFunction> operator = new AbstractUdfStreamOperator<>(fn) {
            private static final long serialVersionUID = 1L;
        };

        operator.initializeState(new TaskStateSnapshot(null));
        assertTrue(initCalled.get());
        assertFalse(isRestored.get());
    }

    @Test
    void testNonCheckpointedUserFunctionDoesNotCallSnapshotState() throws Exception {
        SinkFunction<String> sink = new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
            }
        };

        StreamSinkOperator<String> operator = new StreamSinkOperator<>(sink);
        TestOutput<Void> output = new TestOutput<>();
        operator.setOutput(output);

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        operator.processBarrier(barrier);
        assertNotNull(operator.getLastSnapshotResult());
    }

    @Test
    void testProcessWatermarkExceptionPropagation() throws Exception {
        AbstractStreamOperator<String> operator = new AbstractStreamOperator<>() {
            @Override
            public void processWatermark(Watermark mark) throws Exception {
                throw new RuntimeException("test exception in processWatermark");
            }
        };

        TestOutput<String> output = new TestOutput<>();
        operator.setOutput((Output) output);
        operator.open();

        assertThrows(RuntimeException.class, () ->
                operator.processWatermark(new Watermark(100)));
    }

    @Test
    void testKeyIsolationInReduceOperator() throws Exception {
        List<String> lifecycleCalls = new ArrayList<>();
        StreamReduceOperator<String> op = new StreamReduceOperator<>((a, b) -> a + "," + b);
        TestOutput<String> output = new TestOutput<>();
        op.setOutput(output);
        op.open();

        op.setCurrentKey("key1");
        op.processElement(new StreamRecord<>("a", 1));
        op.setCurrentKey("key2");
        op.processElement(new StreamRecord<>("b", 1));
        op.setCurrentKey("key1");
        op.processElement(new StreamRecord<>("c", 1));

        assertEquals(3, output.getElements().size());
        assertEquals("a", output.getElements().get(0));
        assertEquals("b", output.getElements().get(1));
        assertEquals("a,c", output.getElements().get(2));
        op.close();
    }
}
