/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import io.nop.stream.core.common.functions.FilterFunction;
import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.operators.*;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestBarrierPropagation {

    @Test
    public void testBarrierIsStreamElement() {
        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        assertTrue(barrier instanceof io.nop.stream.core.streamrecord.StreamElement);
        assertTrue(barrier.isCheckpointBarrier());
        assertEquals(barrier, barrier.asCheckpointBarrier());
    }

    @Test
    public void testBarrierPropagationSingleChain() throws Exception {
        StreamSourceOperator<String> source = new StreamSourceOperator<>(new SourceFunction<String>() {
            private static final long serialVersionUID = 1L;
            @Override
            public void run(SourceContext<String> ctx) {
                // no-op source
            }
            @Override
            public void cancel() {}
        });

        List<String> dataResults = new ArrayList<>();
        List<CheckpointBarrier> receivedBarriers = new ArrayList<>();

        StreamSinkOperator<String> sink = new StreamSinkOperator<>(new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;
            @Override
            public void consume(String value) {
                dataResults.add(value);
            }
        }) {
            @Override
            public void processBarrier(CheckpointBarrier b) throws Exception {
                receivedBarriers.add(b);
            }
        };

        ChainingOutput<String> chainingOutput = new ChainingOutput<>(sink);
        source.setOutput(chainingOutput);

        source.open();
        sink.open();

        // emit data through the source's output
        source.getOutput().collect(new StreamRecord<>("data1"));
        source.getOutput().collect(new StreamRecord<>("data2"));

        // inject barrier
        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        source.injectBarrier(barrier);

        assertEquals(2, dataResults.size());
        assertEquals("data1", dataResults.get(0));
        assertEquals("data2", dataResults.get(1));

        assertEquals(1, receivedBarriers.size());
        assertEquals(1L, receivedBarriers.get(0).getId());
        assertEquals(barrier, receivedBarriers.get(0));

        source.close();
        sink.close();
    }

    @Test
    public void testBarrierDoesNotAffectDataFlow() throws Exception {
        List<String> dataResults = new ArrayList<>();
        List<CheckpointBarrier> receivedBarriers = new ArrayList<>();

        StreamSourceOperator<String> source = new StreamSourceOperator<>(new SourceFunction<String>() {
            private static final long serialVersionUID = 1L;
            @Override
            public void run(SourceContext<String> ctx) {}
            @Override
            public void cancel() {}
        });

        StreamSinkOperator<String> sink = new StreamSinkOperator<>(new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;
            @Override
            public void consume(String value) {
                dataResults.add(value);
            }
        }) {
            @Override
            public void processBarrier(CheckpointBarrier b) throws Exception {
                receivedBarriers.add(b);
            }
        };

        source.setOutput(new ChainingOutput<>(sink));
        source.open();
        sink.open();

        // interleave data and barriers
        source.getOutput().collect(new StreamRecord<>("a"));
        source.injectBarrier(new CheckpointBarrier(1L, 100L, CheckpointType.CHECKPOINT));
        source.getOutput().collect(new StreamRecord<>("b"));
        source.injectBarrier(new CheckpointBarrier(2L, 200L, CheckpointType.CHECKPOINT));
        source.getOutput().collect(new StreamRecord<>("c"));

        assertEquals(3, dataResults.size());
        assertEquals("a", dataResults.get(0));
        assertEquals("b", dataResults.get(1));
        assertEquals("c", dataResults.get(2));

        assertEquals(2, receivedBarriers.size());
        assertEquals(1L, receivedBarriers.get(0).getId());
        assertEquals(2L, receivedBarriers.get(1).getId());

        source.close();
        sink.close();
    }

    @Test
    public void testBarrierPropagationMultiOperatorChain() throws Exception {
        StreamSourceOperator<String> source = new StreamSourceOperator<>(new SourceFunction<String>() {
            private static final long serialVersionUID = 1L;
            @Override
            public void run(SourceContext<String> ctx) {}
            @Override
            public void cancel() {}
        });

        StreamMap<String, String> mapOp = new StreamMap<>(new MapFunction<String, String>() {
            private static final long serialVersionUID = 1L;
            @Override
            public String map(String value) {
                return value.toUpperCase();
            }
        });

        StreamFilter<String> filterOp = new StreamFilter<>(new FilterFunction<String>() {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean filter(String value) {
                return value.length() <= 3;
            }
        });

        List<String> dataResults = new ArrayList<>();
        List<CheckpointBarrier> receivedBarriers = new ArrayList<>();

        StreamSinkOperator<String> sink = new StreamSinkOperator<>(new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;
            @Override
            public void consume(String value) {
                dataResults.add(value);
            }
        }) {
            @Override
            public void processBarrier(CheckpointBarrier b) throws Exception {
                receivedBarriers.add(b);
            }
        };

        // wire: source -> map -> filter -> sink
        source.setOutput(new ChainingOutput<>(mapOp));
        mapOp.setOutput(new ChainingOutput<>(filterOp));
        filterOp.setOutput(new ChainingOutput<>(sink));

        source.open();
        mapOp.open();
        filterOp.open();
        sink.open();

        source.getOutput().collect(new StreamRecord<>("a"));
        source.getOutput().collect(new StreamRecord<>("bb"));
        source.getOutput().collect(new StreamRecord<>("cccc"));

        CheckpointBarrier barrier = new CheckpointBarrier(42L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        source.injectBarrier(barrier);

        // "a" -> "A" (len 1, passes), "bb" -> "BB" (len 2, passes), "cccc" -> "CCCC" (len 4, filtered)
        assertEquals(2, dataResults.size());
        assertEquals("A", dataResults.get(0));
        assertEquals("BB", dataResults.get(1));

        assertEquals(1, receivedBarriers.size());
        assertEquals(42L, receivedBarriers.get(0).getId());

        source.close();
        mapOp.close();
        filterOp.close();
        sink.close();
    }

    @Test
    public void testBarrierReachesSinkWithNoOutput() throws Exception {
        List<CheckpointBarrier> receivedBarriers = new ArrayList<>();

        StreamSinkOperator<String> sink = new StreamSinkOperator<>(new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;
            @Override
            public void consume(String value) {}
        }) {
            @Override
            public void processBarrier(CheckpointBarrier b) throws Exception {
                receivedBarriers.add(b);
            }
        };

        sink.open();

        ChainingOutput<String> chain = new ChainingOutput<>(sink);
        CheckpointBarrier barrier = new CheckpointBarrier(5L, 500L, CheckpointType.SAVEPOINT);
        chain.emitBarrier(barrier);

        assertEquals(1, receivedBarriers.size());
        assertEquals(5L, receivedBarriers.get(0).getId());
        assertTrue(receivedBarriers.get(0).isSavepoint());

        sink.close();
    }

    @Test
    public void testCollectElementDispatchesBarrier() {
        TestOutput<String> output = new TestOutput<>();

        CheckpointBarrier barrier = new CheckpointBarrier(1L, 100L, CheckpointType.CHECKPOINT);
        output.collectElement(barrier);

        assertEquals(1, output.getBarriers().size());
        assertEquals(1L, output.getBarriers().get(0).getId());
        assertTrue(output.isEmpty()); // no records
    }

    @Test
    public void testCollectElementDispatchesRecord() {
        TestOutput<String> output = new TestOutput<>();

        output.collectElement(new StreamRecord<>("hello"));

        assertEquals(1, output.size());
        assertEquals("hello", output.get(0));
        assertTrue(output.getBarriers().isEmpty());
    }
}
