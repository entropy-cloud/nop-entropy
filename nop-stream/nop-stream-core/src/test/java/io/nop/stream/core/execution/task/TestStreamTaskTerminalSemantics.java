/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.task;

import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.execution.InputChannel;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.RecordWriter;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.Input;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-7 / AR-8 regression proofs (plan 1326-2 Phase 3).
 *
 * <p>AR-7: a cancelled/interrupted MIDDLE or SINK task must NOT run the success
 * terminal state — no {@code operatorChain.finish()} (connector flush / 2PC commit
 * window), no MAX_WATERMARK, and (MIDDLE) no EOS to downstream — so a truncated
 * stream is never committed as a bounded-complete one. Pre-fix, cancel/interrupt
 * fell into the same {@code break} as EOS and invokeMiddle/invokeSink finalized
 * (finish + MAX_WATERMARK + unconditional closeOutputWriters).
 *
 * <p>AR-8: on the SUCCESS path, finish() must run BEFORE the MAX_WATERMARK
 * (P1-5 contract, aligned with SOURCE/SELF_CONTAINED) so buffered operators'
 * tail batches reach downstream windows before the final watermark fires. The
 * four-path consistency test pins the SAME ordering on all four roles so the
 * semantics cannot fork pairwise again.
 */
class TestStreamTaskTerminalSemantics {

    /**
     * Recording chain element: appends global ordering events, forwards
     * watermarks, and can buffer elements to flush on finish() (the
     * BatchConsumerSinkFunction shape AR-8 targets).
     */
    static class RecordingOperator extends AbstractStreamOperator<String>
            implements OneInputStreamOperator<String, String>, Input<String> {

        private static final long serialVersionUID = 1L;

        final String id;
        final List<String> events;
        final AtomicBoolean finishCalled = new AtomicBoolean(false);
        final boolean bufferUntilFinish;
        final List<String> buffered = Collections.synchronizedList(new ArrayList<>());
        final AtomicBoolean sawMaxWatermark = new AtomicBoolean(false);
        volatile CountDownLatch elementLatch = new CountDownLatch(1);

        RecordingOperator(String id, List<String> events, boolean bufferUntilFinish) {
            this.id = id;
            this.events = events;
            this.bufferUntilFinish = bufferUntilFinish;
        }

        @Override
        public void processElement(StreamRecord<String> element) throws Exception {
            events.add("element@" + id + ":" + element.getValue());
            if (bufferUntilFinish) {
                buffered.add(element.getValue());
            } else if (getOutput() != null) {
                getOutput().collect(element);
            }
            elementLatch.countDown();
        }

        @Override
        public void processWatermark(Watermark mark) throws Exception {
            if (mark.getTimestamp() == Long.MAX_VALUE) {
                sawMaxWatermark.set(true);
                events.add("max-watermark@" + id);
            }
            if (getOutput() != null) {
                getOutput().emitWatermark(mark);
            }
        }

        @Override
        public void finish() throws Exception {
            finishCalled.set(true);
            events.add("finish@" + id);
            if (bufferUntilFinish && getOutput() != null) {
                for (String v : buffered) {
                    getOutput().collect(new StreamRecord<>(v));
                }
            }
            super.finish();
        }
    }

    /** Bounded source: emits two records then returns (data exhausted). */
    static class TwoElementSource implements SourceFunction<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void run(SourceContext<String> ctx) throws Exception {
            ctx.collect("s1");
            ctx.collect("s2");
        }

        @Override
        public void cancel() {
        }
    }

    // ------------------------------------------------------------------
    // AR-7: cancelled MIDDLE / SINK tasks ≠ success terminal state
    // ------------------------------------------------------------------

    @Test
    void testCancelledMiddleTaskSkipsSuccessTerminalState() throws Exception {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        RecordingOperator op = new RecordingOperator("mid", events, false);

        ResultPartition upstream = new ResultPartition();
        InputGate gate = new InputGate(new InputChannel(upstream), null);
        ResultPartition downstream = new ResultPartition();
        RecordWriter<Object> writer = new RecordWriter<>(downstream);

        StreamTaskInvokable invokable = new StreamTaskInvokable(
                new OperatorChain(new ArrayList<>(List.of(op))), writer, gate);

        // one record; upstream stays OPEN (no EOS) so the loop keeps running
        upstream.write(new StreamRecord<>("a", 0));

        Thread task = runInThread(invokable);
        assertTrue(op.elementLatch.await(10, TimeUnit.SECONDS), "task must process the element");

        // cooperative cancel (exactly what the checkpoint-abort handler raises)
        invokable.getMailboxExecutor().signalCancel();
        task.join(30_000);
        assertFalse(task.isAlive(), "task thread must exit after cooperative cancel");

        assertFalse(op.finishCalled.get(),
                "AR-7: a cancelled MIDDLE task must NOT run operatorChain.finish() "
                        + "(no connector flush / commit window on a truncated stream)");
        assertFalse(op.sawMaxWatermark.get(),
                "AR-7: a cancelled MIDDLE task must NOT emit MAX_WATERMARK");
        assertFalse(downstream.isFinished(),
                "AR-7: a cancelled MIDDLE task must NOT close its output writers "
                        + "(no EOS downstream — the truncated stream must not look bounded-complete; "
                        + "a producer-region restart needs the open partition)");

        upstream.close();
    }

    @Test
    void testCancelledSinkTaskSkipsSuccessTerminalState() throws Exception {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        RecordingOperator op = new RecordingOperator("sink", events, true);

        ResultPartition upstream = new ResultPartition();
        InputGate gate = new InputGate(new InputChannel(upstream), null);

        StreamTaskInvokable invokable = new StreamTaskInvokable(
                new OperatorChain(new ArrayList<>(List.of(op))), (RecordWriter<?>) null, gate);

        upstream.write(new StreamRecord<>("a", 0));

        Thread task = runInThread(invokable);
        assertTrue(op.elementLatch.await(10, TimeUnit.SECONDS));
        invokable.getMailboxExecutor().signalCancel();
        task.join(30_000);
        assertFalse(task.isAlive());

        assertFalse(op.finishCalled.get(),
                "AR-7: a cancelled SINK task must NOT run finish() — the 2PC sink "
                        + "flush/commit window must not run on a cancelled task (abort, not commit)");
        assertFalse(op.sawMaxWatermark.get(),
                "AR-7: a cancelled SINK task must NOT emit MAX_WATERMARK");

        upstream.close();
    }

    // ------------------------------------------------------------------
    // AR-8: finish() before MAX_WATERMARK on the SUCCESS path (MIDDLE chain
    // with a buffering operator — the P1-5 BatchConsumerSinkFunction shape)
    // ------------------------------------------------------------------

    @Test
    void testMiddleChainTailBatchPrecedesFinalWatermark() throws Exception {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        RecordingOperator bufferingHead = new RecordingOperator("head", events, true);
        RecordingOperator tail = new RecordingOperator("tail", events, false);

        ResultPartition upstream = new ResultPartition();
        InputGate gate = new InputGate(new InputChannel(upstream), null);
        ResultPartition downstream = new ResultPartition();
        RecordWriter<Object> writer = new RecordWriter<>(downstream);

        StreamTaskInvokable invokable = new StreamTaskInvokable(
                new OperatorChain(new ArrayList<>(List.of(bufferingHead, tail))), writer, gate);

        upstream.write(new StreamRecord<>("tail-1", 0));
        upstream.write(new StreamRecord<>("tail-2", 0));
        upstream.close(); // bounded input → EOS → success terminal state

        Thread task = runInThread(invokable);
        task.join(30_000);
        assertFalse(task.isAlive(), "bounded input must complete the task");

        assertTrue(bufferingHead.finishCalled.get(), "success path must run finish()");

        int tail1 = indexOf(events, "element@tail:tail-1");
        int tail2 = indexOf(events, "element@tail:tail-2");
        int maxWm = indexOf(events, "max-watermark@tail");
        assertTrue(tail1 >= 0 && tail2 >= 0,
                "buffered tail batch must be flushed by finish() and reach the chain tail");
        assertTrue(maxWm >= 0, "success path must emit MAX_WATERMARK");
        assertTrue(tail1 < maxWm && tail2 < maxWm,
                "AR-8: tail-batch records must arrive BEFORE the final watermark "
                        + "(pre-fix: watermark first → tail batch missed the final window). "
                        + "Events: " + events);
    }

    /**
     * Success-path EOS still closes the writers (the bounded-complete contract is
     * unchanged — only cancel/interrupt/error skip it).
     */
    @Test
    void testMiddleTaskEosStillClosesOutputs() throws Exception {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        RecordingOperator op = new RecordingOperator("mid", events, false);

        ResultPartition upstream = new ResultPartition();
        InputGate gate = new InputGate(new InputChannel(upstream), null);
        ResultPartition downstream = new ResultPartition();
        RecordWriter<Object> writer = new RecordWriter<>(downstream);

        StreamTaskInvokable invokable = new StreamTaskInvokable(
                new OperatorChain(new ArrayList<>(List.of(op))), writer, gate);

        upstream.write(new StreamRecord<>("a", 0));
        upstream.close();

        Thread task = runInThread(invokable);
        task.join(30_000);
        assertFalse(task.isAlive());

        assertTrue(op.finishCalled.get(), "EOS success path runs finish()");
        assertTrue(op.sawMaxWatermark.get(), "EOS success path emits MAX_WATERMARK");
        assertTrue(downstream.isFinished(),
                "EOS success path still closes the output writers (bounded-complete)");
    }

    // ------------------------------------------------------------------
    // AR-8 four-path consistency: SOURCE / SELF_CONTAINED (with a real bounded
    // source operator) and MIDDLE / SINK (bounded input) must all run
    // finish() BEFORE the final MAX_WATERMARK reaches the chain tail.
    // ------------------------------------------------------------------

    @Test
    void testFourRolesFinishBeforeMaxWatermark() throws Exception {
        assertFinishBeforeMaxWatermark("SOURCE", true, false);
        assertFinishBeforeMaxWatermark("MIDDLE", true, true);
        assertFinishBeforeMaxWatermark("SINK", false, true);
        assertFinishBeforeMaxWatermark("SELF_CONTAINED", false, false);
    }

    private void assertFinishBeforeMaxWatermark(String role, boolean withWriter, boolean withGate)
            throws Exception {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        RecordingOperator tail = new RecordingOperator("tail", events, false);

        OperatorChain chain;
        if (withGate) {
            chain = new OperatorChain(new ArrayList<>(List.of(
                    new RecordingOperator("head", events, false), tail)));
        } else {
            chain = new OperatorChain(new ArrayList<>(List.of(
                    new StreamSourceOperator<>(new TwoElementSource()), tail)));
        }

        ResultPartition upstream = null;
        RecordWriter<Object> writer = null;
        InputGate gate = null;
        if (withWriter) {
            writer = new RecordWriter<>(new ResultPartition());
        }
        if (withGate) {
            upstream = new ResultPartition();
            gate = new InputGate(new InputChannel(upstream), null);
            upstream.write(new StreamRecord<>("m1", 0));
            upstream.close(); // bounded input
        }

        StreamTaskInvokable invokable = new StreamTaskInvokable(chain, writer, gate);
        Thread task = runInThread(invokable);
        task.join(30_000);
        assertFalse(task.isAlive(), role + ": task must complete on bounded input");

        assertTrue(tail.finishCalled.get(), role + ": success path runs finish()");
        assertTrue(tail.sawMaxWatermark.get(), role + ": success path emits MAX_WATERMARK");
        int finishIdx = indexOf(events, "finish@tail");
        int maxWmIdx = indexOf(events, "max-watermark@tail");
        assertTrue(finishIdx >= 0 && maxWmIdx >= 0 && finishIdx < maxWmIdx,
                role + ": finish() must run BEFORE the final MAX_WATERMARK reaches the chain "
                        + "tail (four-path P1-5 consistency). Events: " + events);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static Thread runInThread(StreamTaskInvokable invokable) {
        Thread task = new Thread(() -> {
            try {
                invokable.invoke();
            } catch (Exception e) {
                throw new io.nop.stream.core.exceptions.StreamRuntimeException("task thread failed", e);
            }
        });
        task.start();
        return task;
    }

    private static int indexOf(List<String> events, String exact) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).equals(exact)) {
                return i;
            }
        }
        return -1;
    }
}
