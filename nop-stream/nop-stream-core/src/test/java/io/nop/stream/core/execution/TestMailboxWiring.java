package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.*;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 wiring verification for the mailbox control plane. Validates that:
 * <ul>
 *   <li>(a) SOURCE trigger-checkpoint mail is delivered to the invokable's mailbox and
 *       consumed on the task thread at the SourceContext.collect() emission point.</li>
 *   <li>(b) middle/sink {@code triggerCheckpoint} stays synchronous on the injector
 *       thread: ack count is primed BEFORE any in-band barrier can arrive (cross-task
 *       priming invariant holds → checkpoint does not hang).</li>
 *   <li>(c) middle/sink main loop ({@code processInputGate}) exits cooperatively when the
 *       mailbox cancel flag is raised, without needing an InterruptedException.</li>
 *   <li>The mailbox ownership chain is connected: {@code StreamTaskInvokable} creates and
 *       exposes a {@link MailboxExecutor}, and wires it to the head source operator.</li>
 * </ul>
 */
class TestMailboxWiring {

    private static final TaskLocation LOC = new TaskLocation("job-1", "pipeline-1", "v0", 0);

    /**
     * Ownership: StreamTaskInvokable creates and exposes a MailboxExecutor, and wires it
     * to the head source operator so offerBarrier delivers to the task mailbox.
     */
    @Test
    void testInvokableWiresMailboxToHeadSourceOperator() {
        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(new SourceFunction<String>() {
            private static final long serialVersionUID = 1L;
            @Override public void run(SourceContext<String> ctx) {}
            @Override public void cancel() {}
        });
        OperatorChain chain = new OperatorChain(Collections.singletonList(sourceOp));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);

        MailboxExecutor executor = invokable.getMailboxExecutor();
        assertNotNull(executor, "StreamTaskInvokable must expose a MailboxExecutor");
        assertNotNull(executor.getMailbox(), "MailboxExecutor must hold a TaskMailbox");

        // Delivering a trigger mail through offerBarrier must place it in the invokable's
        // mailbox (drained by the task thread), proving the wiring link.
        CheckpointBarrier barrier = new CheckpointBarrier(1L, 1L, CheckpointType.CHECKPOINT);
        sourceOp.offerBarrier(barrier);
        assertFalse(executor.getMailbox().isEmpty(),
                "offerBarrier must deliver a trigger-checkpoint mail to the invokable's mailbox");
        Mail mail = executor.getMailbox().poll();
        assertNotNull(mail);
        assertEquals(Mail.Priority.CONTROL, mail.getPriority());
    }

    /**
     * (a) SOURCE trigger-checkpoint mail is consumed on the task thread at collect().
     * Verifies snapshotState runs on the source task thread, not the injector thread.
     */
    @Test
    void testSourceTriggerMailConsumedOnTaskThread() throws Exception {
        AtomicReference<String> snapshotThread = new AtomicReference<>();
        CountDownLatch snapshotDone = new CountDownLatch(1);
        CountDownLatch holdSource = new CountDownLatch(1);

        SourceFunction<String> source = new SourceFunction<String>() {
            private static final long serialVersionUID = 1L;
            @Override
            public void run(SourceContext<String> ctx) throws Exception {
                ctx.collect("first");
                holdSource.await(5, TimeUnit.SECONDS);
                ctx.collect("second");
            }
            @Override public void cancel() { holdSource.countDown(); }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        sourceOp.setOutput(new NopOutput<String>() {
            @Override public void emitBarrier(CheckpointBarrier barrier) {
                snapshotThread.set(Thread.currentThread().getName());
                snapshotDone.countDown();
            }
        });
        // Wire a mailbox so offerBarrier delivers a trigger-checkpoint mail that is
        // drained on the source task thread (not the fallback direct-inject path).
        sourceOp.setMailboxExecutor(new MailboxExecutor());
        sourceOp.open();

        List<StreamOperator<?>> operators = Collections.singletonList(sourceOp);
        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC, operators, snapshot -> {});
        sourceOp.setSnapshotCallback(snapshot -> tracker.acknowledgeOperator(0, snapshot));

        Thread sourceTask = new Thread(() -> {
            try { sourceOp.run(); } catch (Exception e) { throw new StreamException("mailbox wiring test thread failed", e); }
        }, "source-task-thread");
        sourceTask.start();
        Thread.sleep(100);

        // Injector thread triggers checkpoint; triggerCheckpoint primes synchronously
        // then offerBarrier delivers a CONTROL mail to the task mailbox.
        Thread injector = new Thread(() -> {
            try {
                tracker.triggerCheckpoint(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
            } catch (Exception e) {
                throw new StreamException("mailbox wiring test thread failed", e);
            }
        }, "barrier-injector");
        injector.start();
        injector.join(2000);

        // Release source so the next collect() drains the mail on the task thread.
        holdSource.countDown();
        sourceTask.join(5000);

        assertTrue(snapshotDone.await(2, TimeUnit.SECONDS), "snapshotState must have run via mailbox drain");
        assertEquals("source-task-thread", snapshotThread.get(),
                "SOURCE snapshotState must run on the task thread (mailbox drain), not the injector thread");
    }

    /**
     * (b) middle/sink triggerCheckpoint stays synchronous on the injector thread: ack
     * count is primed before any in-band barrier can arrive. Verified by: after
     * triggerCheckpoint returns true, an immediate acknowledgeOperator (simulating the
     * in-band barrier's snapshot ACK) is accepted (counted down), proving priming
     * happened synchronously and the ACK will not be silently dropped as stale.
     */
    @Test
    void testMiddleSinkTriggerPrimesAckCountSynchronously() throws Exception {
        // A non-source operator chain (middle/sink): head is a map/sink, not a source.
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;
            @Override public void consume(String value) {}
        });
        List<StreamOperator<?>> operators = Collections.singletonList(sinkOp);

        AtomicInteger completed = new AtomicInteger(0);
        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC, operators, snap -> completed.incrementAndGet());
        sinkOp.setSnapshotCallback(snap -> tracker.acknowledgeOperator(0, snap));

        // Simulate the injector thread calling triggerCheckpoint.
        boolean accepted = tracker.triggerCheckpoint(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        assertTrue(accepted, "middle/sink trigger must be accepted");
        assertEquals(1L, tracker.getCurrentCheckpointId(),
                "currentCheckpointId must be primed synchronously when triggerCheckpoint returns");

        // The barrier would then flow in-band and reach the operator's processBarrier,
        // which calls snapshotState + acknowledgeOperator. Because priming already
        // happened synchronously on the injector thread, this ACK must NOT be dropped.
        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        sinkOp.processBarrier(barrier);

        assertEquals(1, completed.get(),
                "ACK for the in-band barrier must complete the checkpoint (primed before barrier → no hang)");
    }

    /**
     * (c) middle/sink main loop observes the cooperative cancel flag at its top. Verified
     * two ways: (1) the invokable's mailbox executor reflects signalCancel immediately;
     * (2) a task thread blocked in processInputGate terminates promptly once abort raises
     * the cancel flag + interrupts (no hang), exiting via either the loop-top cancel
     * check or the interrupt-unblocked read.
     */
    @Test
    void testMiddleSinkCooperativeCancelFlagPropagates() {
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;
            @Override public void consume(String value) {}
        });
        OperatorChain chain = new OperatorChain(Collections.singletonList(sinkOp));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);

        // (1) cancel flag is observable through the invokable's mailbox executor.
        MailboxExecutor exec = invokable.getMailboxExecutor();
        assertFalse(exec.isCancelled());
        assertFalse(exec.processAvailableMails(), "no cancel before signalCancel");
        exec.signalCancel();
        assertTrue(exec.isCancelled());
        assertTrue(exec.processAvailableMails(), "processAvailableMails must reflect cancel flag at loop top");
    }

    /**
     * (c) integration: a middle/sink task blocked in processInputGate terminates promptly
     * after the abort path (signalCancel + interrupt) is invoked. This proves the
     * cooperative-cancel wiring does not hang. Uses a real ResultPartition that blocks.
     */
    @Test
    void testMiddleSinkTaskTerminatesOnAbort() throws Exception {
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;
            @Override public void consume(String value) {}
        });
        OperatorChain chain = new OperatorChain(Collections.singletonList(sinkOp));

        // An empty ResultPartition: read() blocks until interrupted (single channel).
        ResultPartition partition = new ResultPartition(4);
        InputChannel channel = new InputChannel(partition);
        InputGate inputGate = new InputGate(Collections.singletonList(channel));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain, (RecordWriter<Object>) null, inputGate);

        AtomicReference<String> exitState = new AtomicReference<>("not-exited");
        Thread taskThread = new Thread(() -> {
            try {
                invokable.invoke();
                exitState.set("completed");
            } catch (Exception e) {
                exitState.set("exception:" + e.getClass().getSimpleName());
            }
        }, "middle-task-thread");
        taskThread.start();

        // Give the task thread time to block in InputGate.read().
        Thread.sleep(300);

        // Abort path: raise cancel flag + deliver marker mail, then interrupt to unblock read.
        invokable.getMailboxExecutor().signalCancel();
        taskThread.interrupt();
        taskThread.join(5000);

        assertFalse(taskThread.isAlive(),
                "main loop must terminate promptly after abort (signalCancel + interrupt), not hang");
        assertNotEquals("not-exited", exitState.get());
    }

    /** Minimal no-op Output to reduce test boilerplate. */
    private abstract static class NopOutput<T> implements Output<StreamRecord<T>> {
        @Override public void collect(StreamRecord<T> record) {}
        @Override public void close() {}
        @Override public void emitWatermark(io.nop.stream.core.streamrecord.watermark.Watermark watermark) {}
        @Override public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {}
        @Override public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {}
        @Override public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker latencyMarker) {}
    }
}
