/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.jobgraph;

import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the 5-segment operator lifecycle ({@code open -> process* -> finish
 * -> close}) is driven end-to-end by {@link OperatorChain}, with a particular
 * focus on the P1-5 fix: connectors that buffer (e.g. a batch sink) must have
 * their {@code finish()} hook invoked so the tail batch is flushed before
 * {@code close()}.
 */
public class TestOperatorChainLifecycle {

    @Test
    void finishInvokesFinishOnEveryOperatorInReverseOrder() throws Exception {
        List<String> calls = new CopyOnWriteArrayList<>();

        // Two recording operators. Each finish() appends its own identifier so
        // we can assert ordering (tail first, matching close()).
        RecordingOperator tail = new RecordingOperator("tail", calls);
        RecordingOperator head = new RecordingOperator("head", calls);

        OperatorChain chain = new OperatorChain(Arrays.asList(head, tail));
        chain.open();
        chain.finish();
        chain.close();

        assertEquals(Arrays.asList("finish:tail", "finish:head"), calls,
                "OperatorChain.finish() must call finish() on each operator in reverse order");
    }

    @Test
    void finishFlushesBufferedSinkBeforeClose() throws Exception {
        // Reproduces the P1-5 contract: a sink that buffers records and only
        // emits on finish() must observe its buffer flushed when the chain is
        // finished. Without the OperatorChain.finish() wiring this sink would
        // silently drop the tail batch.
        List<List<String>> flushedBatches = new CopyOnWriteArrayList<>();
        BufferingSink sink = new BufferingSink(2, flushedBatches);

        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(sink);
        TestOutput<Void> output = new TestOutput<>();
        sinkOp.setOutput(output);

        OperatorChain chain = new OperatorChain(List.of(sinkOp));
        chain.open();

        // Process three records — only the first two trigger an inline flush
        // when the buffer fills; the third sits in the buffer waiting for
        // finish() to flush it.
        sinkOp.processElement(new StreamRecord<>("a"));
        sinkOp.processElement(new StreamRecord<>("b"));
        sinkOp.processElement(new StreamRecord<>("c"));
        assertEquals(1, flushedBatches.size(), "first full buffer should flush inline");

        // P1-5: finish() is what releases the tail batch. If finish() is
        // removed or made a no-op, this assertion fails — anti-hollow.
        chain.finish();
        assertEquals(2, flushedBatches.size(), "OperatorChain.finish() must flush the buffered tail batch");
        assertEquals(Arrays.asList("c"), flushedBatches.get(1));

        chain.close();
    }

    /**
     * Minimal buffered sink used to verify finish() flush semantics. Buffers
     * incoming records and emits a batch once either the buffer fills or
     * {@code finish()} is invoked.
     */
    private static final class BufferingSink implements SinkFunction<String> {
        private static final long serialVersionUID = 1L;

        private final int batchSize;
        private final List<String> buffer = new ArrayList<>();
        private final List<List<String>> flushedBatches;

        BufferingSink(int batchSize, List<List<String>> flushedBatches) {
            this.batchSize = batchSize;
            this.flushedBatches = flushedBatches;
        }

        @Override
        public void consume(String value) {
            buffer.add(value);
            if (buffer.size() >= batchSize) {
                flush();
            }
        }

        @Override
        public void finish() throws Exception {
            flush();
        }

        private void flush() {
            if (buffer.isEmpty()) {
                return;
            }
            flushedBatches.add(new ArrayList<>(buffer));
            buffer.clear();
        }
    }

    /**
     * Bare {@link io.nop.stream.core.operators.StreamOperator} stub that records
     * the order of finish() invocations. Used to verify reverse-order traversal.
     */
    private static final class RecordingOperator extends StreamSinkOperator<Void> {
        private static final long serialVersionUID = 1L;

        private final String id;
        private final List<String> calls;

        RecordingOperator(String id, List<String> calls) {
            super(new SinkFunction<Void>() {
                private static final long serialVersionUID = 1L;

                @Override
                public void consume(Void value) {
                }
            });
            this.id = id;
            this.calls = calls;
        }

        @Override
        public void finish() throws Exception {
            calls.add("finish:" + id);
        }
    }
}
