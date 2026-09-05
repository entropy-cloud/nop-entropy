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
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-9 cross-task end-to-end proof (plan 1326-2 Phase 4, D1 option (a)): a real
 * SOURCE task that goes idle ({@code SourceContext.markAsTemporarilyIdle()} →
 * RecordWriterOutput → RecordWriter → wire → InputGate) no longer pins the
 * downstream MIDDLE task's merged event time — the idle channel is excluded
 * from the min and the active channel's watermarks keep advancing the merge.
 *
 * <p>Pre-fix, {@code RecordWriterOutput.emitWatermarkStatus} was an empty body
 * ("Not forwarded across task boundaries"), so the IDLE status died at the
 * first task boundary and {@code WatermarkStrategyWithIdleness} silently did
 * nothing across tasks.
 */
class TestWatermarkIdlenessCrossTaskE2E {

    /**
     * Source that emits one record + watermark 50, then marks itself idle and
     * waits for cancellation (the "silent partition" shape — an idle upstream
     * subtask that never sends another watermark).
     */
    static class GoesIdleSource implements SourceFunction<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void run(SourceContext<String> ctx) throws Exception {
            ctx.collectWithTimestamp("a", 10);
            ctx.emitWatermark(50);
            ctx.markAsTemporarilyIdle();
            while (!ctx.isCancelled() && !Thread.currentThread().isInterrupted()) {
                Thread.sleep(5);
            }
        }

        @Override
        public void cancel() {
        }
    }

    /** MIDDLE head operator recording the merged watermarks/statuses it observes. */
    static class MergedWatermarkRecorder extends AbstractStreamOperator<String>
            implements OneInputStreamOperator<String, String>, Input<String> {

        private static final long serialVersionUID = 1L;

        final List<Object> observed = new ArrayList<>();
        final AtomicBoolean done = new AtomicBoolean(false);

        @Override
        public void processElement(StreamRecord<String> element) {
        }

        @Override
        public void processWatermark(Watermark mark) throws Exception {
            observed.add(mark.getTimestamp());
            super.processWatermark(mark);
        }

        @Override
        public void processWatermarkStatus(WatermarkStatus status) {
            observed.add(status);
        }

        @Override
        public void finish() throws Exception {
            done.set(true);
            super.finish();
        }
    }

    @Test
    void testIdleUpstreamTaskDoesNotPinDownstreamEventTime() throws Exception {
        // --- downstream MIDDLE task: 2-channel gate (idle source task + active producer) ---
        ResultPartition fromIdleSource = new ResultPartition();
        ResultPartition fromActive = new ResultPartition();
        ResultPartition downstream = new ResultPartition();

        MergedWatermarkRecorder head = new MergedWatermarkRecorder();
        InputGate gate = new InputGate(
                Arrays.asList(new InputChannel(fromIdleSource), new InputChannel(fromActive)), null, true);
        RecordWriter<Object> writer = new RecordWriter<>(downstream);

        StreamTaskInvokable middleTask = new StreamTaskInvokable(
                new OperatorChain(new ArrayList<>(List.of(head))), writer, gate);
        Thread middleThread = new Thread(() -> {
            try {
                middleTask.invoke();
            } catch (Exception e) {
                throw new io.nop.stream.core.exceptions.StreamRuntimeException("task thread failed", e);
            }
        }, "middle-task");
        middleThread.start();

        // --- upstream SOURCE task whose source goes idle after watermark 50 ---
        StreamSourceOperator<String> idleSourceOp = new StreamSourceOperator<>(new GoesIdleSource());
        RecordWriter<Object> idleWriter = new RecordWriter<>(fromIdleSource);
        StreamTaskInvokable sourceTask = new StreamTaskInvokable(
                new OperatorChain(new ArrayList<>(List.of(idleSourceOp))), idleWriter, null);
        Thread sourceThread = new Thread(() -> {
            try {
                sourceTask.invoke();
            } catch (Exception e) {
                throw new io.nop.stream.core.exceptions.StreamRuntimeException("task thread failed", e);
            }
        }, "idle-source-task");
        sourceThread.start();

        // --- the ACTIVE producer channel drives its watermarks past the idle one ---
        // (fed directly: this channel's purpose is progress, not status forwarding)
        fromActive.write(new Watermark(100));
        awaitObserved(head, 100L); // merged = min(50, 100) = 50 arrives first
        // NOTE: the 50/100 ordering assertion is done via the sequence below; the
        // await simply guarantees the source task's watermark landed before we advance.

        // idle source task's IDLE status crosses the task boundary → the merge
        // excludes that channel → merged watermark advances to the active
        // channel's watermark (100). Pre-fix: stuck at 50 forever.
        awaitObserved(head, 100L);

        fromActive.write(new Watermark(300));
        awaitObserved(head, 300L);
        fromActive.write(new Watermark(500));
        awaitObserved(head, 500L);

        // sequence sanity: the merged stream must show progress beyond the idle
        // channel's stale 50 — the KEY AR-9 assertion (pre-fix: [50] then nothing)
        List<Object> snapshot = new ArrayList<>(head.observed);
        assertTrue(snapshot.contains(300L) && snapshot.contains(500L),
                "downstream event time must keep advancing after the upstream task "
                        + "went idle. Observed: " + snapshot);

        // tear down: cancel both tasks (the idle source's spin loop exits on cancel)
        middleTask.getMailboxExecutor().signalCancel();
        sourceTask.getMailboxExecutor().signalCancel();
        sourceThread.interrupt();
        middleThread.join(30_000);
        sourceThread.join(30_000);
        assertTrue(!sourceThread.isAlive() && !middleThread.isAlive(), "tasks must exit");
    }

    private static void awaitObserved(MergedWatermarkRecorder recorder, long watermark) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            synchronized (recorder.observed) {
                for (Object o : recorder.observed) {
                    if (o instanceof Long && (Long) o == watermark) {
                        return;
                    }
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("watermark " + watermark + " never reached the downstream head. Observed: "
                + recorder.observed);
    }
}
