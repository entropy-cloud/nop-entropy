/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.task;

import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression proof for the task-teardown close ordering: an output-close
 * failure (EOS send failure surfaced by {@code RemoteResultPartition.close()})
 * used to be rethrown straight out of the {@code invokeSource}/
 * {@code invokeMiddle} finally block, skipping {@code operatorChain.close()}
 * and {@code closeInputGate()} — leaking the input-channel subscriptions the
 * task holds.
 *
 * <p>The discriminating assertion is that the recording channel's
 * {@code close()} runs even though the writer close threw. Pre-fix, it did
 * not. The exception contract is also pinned: the FIRST failure (the EOS send
 * failure, or the task's own input error) is what propagates; later close
 * failures ride along as suppressed exceptions.
 */
class TestTaskTeardownCloseChain {

    /** Writer whose close() fails the way a broken message backend does. */
    static class FailingWriter extends RecordWriter<Object> {
        final StreamException closeError;

        FailingWriter(ResultPartition partition, StreamException closeError) {
            super(partition);
            this.closeError = closeError;
        }

        @Override
        public void close() {
            throw closeError;
        }
    }

    /** Channel that records whether its close() was reached. */
    static class RecordingChannel extends InputChannel {
        final AtomicBoolean closed = new AtomicBoolean(false);

        RecordingChannel(ResultPartition partition) {
            super(partition);
        }

        @Override
        public void close() {
            closed.set(true);
            super.close();
        }
    }

    /**
     * Chain element with injectable processElement/close failures and close
     * recording, so tests can pin both the propagation and the suppression
     * contract of the teardown sequence.
     */
    static class TestOperator extends AbstractStreamOperator<String>
            implements OneInputStreamOperator<String, String>, Input<String> {

        private static final long serialVersionUID = 1L;

        final String id;
        final List<String> events;
        final RuntimeException processElementError;
        final RuntimeException closeError;
        final AtomicBoolean sawMaxWatermark = new AtomicBoolean(false);

        TestOperator(String id, List<String> events,
                     RuntimeException processElementError, RuntimeException closeError) {
            this.id = id;
            this.events = events;
            this.processElementError = processElementError;
            this.closeError = closeError;
        }

        @Override
        public void processElement(StreamRecord<String> element) throws Exception {
            if (processElementError != null) {
                throw processElementError;
            }
            if (getOutput() != null) {
                getOutput().collect(element);
            }
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
        public void close() throws Exception {
            events.add("close@" + id);
            if (closeError != null) {
                throw closeError;
            }
            super.close();
        }
    }

    static class BoundedSource implements SourceFunction<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void run(SourceContext<String> ctx) throws Exception {
            ctx.collect("s1");
        }

        @Override
        public void cancel() {
        }
    }

    @Test
    void testEosSendFailureDoesNotSkipInputGateClose() throws Exception {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        StreamException eosFailure = new StreamException("eos send failed");

        ResultPartition upstream = new ResultPartition();
        RecordingChannel channel = new RecordingChannel(upstream);
        InputGate gate = new InputGate(channel, null);
        ResultPartition downstream = new ResultPartition();
        FailingWriter writer = new FailingWriter(downstream, eosFailure);

        TestOperator op = new TestOperator("mid", events, null, null);
        StreamTaskInvokable invokable = new StreamTaskInvokable(
                new OperatorChain(new ArrayList<>(List.of(op))), writer, gate);

        upstream.write(new StreamRecord<>("a", 0));
        upstream.close(); // bounded input → EOS → success terminal state

        AtomicReference<Throwable> failure = runInThread(invokable);

        assertTrue(channel.closed.get(),
                "the input channel must be closed even though the writer close threw "
                        + "(pre-fix: EOS failure rethrown out of the finally skipped closeInputGate — "
                        + "the subscription leaked)");
        assertTrue(events.contains("close@mid"),
                "the operator chain must be closed even though the writer close threw. Events: " + events);
        assertSame(eosFailure, failure.get(),
                "the EOS send failure is the first failure and must propagate as-is");
        assertTrue(op.sawMaxWatermark.get(),
                "the success terminal state must still have run before teardown");
    }

    @Test
    void testInputErrorKeepsPriorityOverCloseFailure() throws Exception {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        RuntimeException inputFailure = new RuntimeException("boom in processElement");
        RuntimeException closeFailure = new RuntimeException("close failed too");

        ResultPartition upstream = new ResultPartition();
        RecordingChannel channel = new RecordingChannel(upstream);
        InputGate gate = new InputGate(channel, null);
        ResultPartition downstream = new ResultPartition();
        RecordWriter<Object> writer = new RecordWriter<>(downstream);

        TestOperator op = new TestOperator("mid", events, inputFailure, closeFailure);
        StreamTaskInvokable invokable = new StreamTaskInvokable(
                new OperatorChain(new ArrayList<>(List.of(op))), writer, gate);

        upstream.write(new StreamRecord<>("a", 0)); // operator throws; no EOS

        AtomicReference<Throwable> failure = runInThread(invokable);

        assertTrue(channel.closed.get(),
                "the input channel must be closed on the error path too");
        assertSame(inputFailure, failure.get(),
                "the task's own input error must propagate, not the later close failure");
        assertTrue(failure.get().getSuppressed().length >= 1,
                "the close failure must ride along as a suppressed exception");
    }

    @Test
    void testSourceEosSendFailureStillClosesOperatorChain() throws Exception {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        StreamException eosFailure = new StreamException("eos send failed");

        ResultPartition downstream = new ResultPartition();
        FailingWriter writer = new FailingWriter(downstream, eosFailure);

        TestOperator tail = new TestOperator("tail", events, null, null);
        OperatorChain chain = new OperatorChain(new ArrayList<>(List.of(
                new StreamSourceOperator<>(new BoundedSource()), tail)));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain, writer, null);

        AtomicReference<Throwable> failure = runInThread(invokable);

        assertTrue(events.contains("close@tail"),
                "the operator chain must be closed even though the writer close threw. Events: " + events);
        assertSame(eosFailure, failure.get(),
                "the EOS send failure must propagate as-is from the source path");
    }

    private static AtomicReference<Throwable> runInThread(StreamTaskInvokable invokable)
            throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread task = new Thread(() -> {
            try {
                invokable.invoke();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        task.start();
        task.join(30_000);
        assertTrue(!task.isAlive(), "task thread must exit");
        assertTrue(failure.get() != null, "task must fail with the injected close failure");
        return failure;
    }
}
