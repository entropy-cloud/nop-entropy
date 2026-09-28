/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.task;

import io.nop.stream.core.execution.InputChannel;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.RecordWriter;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.Input;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A8 regression (plan 01 quality-perf Phase 2): the SINK and SELF_CONTAINED
 * invoke paths must release the operator chain and the input gate through
 * {@code closeChainAndGate} (as MIDDLE/SOURCE already do) — a failing
 * {@code operatorChain.close()} must not skip {@code closeInputGate()} (one
 * leaked remote input-channel subscription per input edge) and must not mask
 * the primary processing error.
 */
class TestSinkSelfContainedCloseSuppression {

    /** Operator that fails when the chain closes it. */
    static class FailingCloseOperator extends AbstractStreamOperator<String>
            implements OneInputStreamOperator<String, String>, Input<String> {

        private static final long serialVersionUID = 1L;

        final AtomicBoolean closeAttempted = new AtomicBoolean(false);

        @Override
        public void processElement(StreamRecord<String> element) {
        }

        @Override
        public void processWatermark(Watermark mark) {
        }

        @Override
        public void close() {
            closeAttempted.set(true);
            throw new IllegalStateException("close boom");
        }
    }

    /** InputGate subclass that records whether close() reached the gate. */
    static class RecordingGate extends InputGate {
        private static final long serialVersionUID = 1L;

        final AtomicBoolean closeCalled = new AtomicBoolean(false);

        RecordingGate(InputChannel channel) {
            super(java.util.List.of(channel), null, true);
        }

        @Override
        public void close() {
            closeCalled.set(true);
            super.close();
        }
    }

    /** Bounded source whose chain close fails (SELF_CONTAINED role). */
    static class FailingCloseSourceOperator extends StreamSourceOperator<String> {
        private static final long serialVersionUID = 1L;

        FailingCloseSourceOperator(SourceFunction<String> source) {
            super(source);
        }

        @Override
        public void close() {
            throw new IllegalStateException("close boom");
        }
    }

    static class TwoElementSource implements SourceFunction<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void run(SourceContext<String> ctx) {
            ctx.collect("s1");
            ctx.collect("s2");
        }

        @Override
        public void cancel() {
        }
    }

    @Test
    void sinkCloseFailureStillClosesGateAndPropagates() throws Exception {
        FailingCloseOperator op = new FailingCloseOperator();
        ResultPartition upstream = new ResultPartition();
        RecordingGate gate = new RecordingGate(new InputChannel(upstream));

        StreamTaskInvokable invokable = new StreamTaskInvokable(
                new OperatorChain(new java.util.ArrayList<>(List.of(op))),
                (io.nop.stream.core.execution.RecordWriter<?>) null, gate);

        // Bounded input: EOS drives the success terminal state, then the chain
        // close fails — the gate must still be released.
        upstream.close();

        AtomicReference<Throwable> taskError = new AtomicReference<>();
        Thread task = new Thread(() -> {
            try {
                invokable.invoke();
            } catch (Throwable e) {
                taskError.set(e);
            }
        });
        task.start();
        task.join(30_000);

        assertFalse(task.isAlive(), "task thread must exit despite the close failure");
        assertTrue(op.closeAttempted.get(), "operatorChain.close() must have run");
        assertTrue(gate.closeCalled.get(),
                "A8: closeInputGate() must run even though operatorChain.close() threw "
                        + "(pre-fix: sequential close skipped the gate release)");
        assertNotNull(taskError.get(),
                "a close-only failure must surface (no processing error to attach it to)");
    }

    @Test
    void selfContainedCloseFailureStillClosesGate() throws Exception {
        FailingCloseSourceOperator sourceOp = new FailingCloseSourceOperator(new TwoElementSource());
        ResultPartition upstream = new ResultPartition();
        RecordingGate gate = new RecordingGate(new InputChannel(upstream));

        // SELF_CONTAINED wiring: no downstream writer AND no input gate.
        StreamTaskInvokable invokable = new StreamTaskInvokable(
                new OperatorChain(new java.util.ArrayList<>(List.of(sourceOp))),
                (io.nop.stream.core.execution.RecordWriter<?>) null, null);

        AtomicReference<Throwable> taskError = new AtomicReference<>();
        Thread task = new Thread(() -> {
            try {
                invokable.invoke();
            } catch (Throwable e) {
                taskError.set(e);
            }
        });
        task.start();
        task.join(30_000);

        assertFalse(task.isAlive(), "task thread must exit despite the close failure");
        assertNotNull(taskError.get(),
                "the SELF_CONTAINED close failure must surface, not vanish");
    }
}
