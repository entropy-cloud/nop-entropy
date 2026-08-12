/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.HeapInternalTimerService;
import io.nop.stream.core.operators.InternalTimer;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.operators.Triggerable;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Production wiring verification for the processing-time services (plan
 * `2026-08-13-0132-1` Phase 1, Rules #22/#23/#24):
 *
 * <ul>
 *   <li>a NON-checkpoint invokable (local {@code env.execute()} equivalent: no barrier
 *       tracker, no setBarrierTracker call) receives a real ProcessingTimeService +
 *       TimerServiceManager injected into every operator BEFORE open() — the wiring does not
 *       depend on the conditional setBarrierTracker/setupSnapshotCallbacks path;</li>
 *   <li>an operator's {@code registerTimerService} registration is observable on the manager
 *       at runtime;</li>
 *   <li>a processing-time timer registered by an operator fires end-to-end through the
 *       driver → mailbox → task thread chain.</li>
 * </ul>
 */
public class TestStreamTaskInvokableProcessingTimeWiring {

    /**
     * Operator that mirrors the timer wiring contract of ProcessOperator/WindowOperator:
     * creates a HeapInternalTimerService in open(), registers it with the task's
     * TimerServiceManager, and registers a processing-time timer that flags a callback.
     */
    static class TimerRegisteringOperator extends AbstractStreamOperator<String>
            implements OneInputStreamOperator<String, String>, Triggerable<String, String> {

        private static final long serialVersionUID = 1L;

        final AtomicReference<ProcessingTimeService> ptsAtOpen = new AtomicReference<>();
        final AtomicReference<io.nop.stream.core.operators.TimerServiceManager> tsmAtOpen =
                new AtomicReference<>();
        final AtomicBoolean timerFired = new AtomicBoolean();
        final AtomicReference<Long> firedTimestamp = new AtomicReference<>();
        final AtomicReference<Thread> callbackThread = new AtomicReference<>();
        final AtomicInteger elementCount = new AtomicInteger();
        final long timerDelayMs;

        transient HeapInternalTimerService<String, String> internalTimerService;

        TimerRegisteringOperator(long timerDelayMs) {
            this.timerDelayMs = timerDelayMs;
        }

        @Override
        public void open() throws Exception {
            super.open();
            ptsAtOpen.set(getProcessingTimeService());
            tsmAtOpen.set(getTimeServiceManager());
            internalTimerService = new HeapInternalTimerService<>(this);
            if (getTimeServiceManager() != null) {
                getTimeServiceManager().registerTimerService(internalTimerService);
            }
            internalTimerService.registerProcessingTimeTimer("ns",
                    System.currentTimeMillis() + timerDelayMs);
        }

        @Override
        public void processElement(StreamRecord<String> element) throws Exception {
            elementCount.incrementAndGet();
            output.collect(element);
        }

        @Override
        public void onProcessingTime(InternalTimer<String, String> timer) throws Exception {
            timerFired.set(true);
            firedTimestamp.set(timer.getTimestamp());
            callbackThread.set(Thread.currentThread());
        }

        @Override
        public void onEventTime(InternalTimer<String, String> timer) throws Exception {
        }
    }

    private static StreamSourceOperator<String> slowSource(int elements, long emitIntervalMs) {
        return new StreamSourceOperator<>(new SourceFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) throws Exception {
                for (int i = 0; i < elements; i++) {
                    ctx.collect("event-" + i);
                    Thread.sleep(emitIntervalMs);
                }
            }

            @Override
            public void cancel() {
            }
        });
    }

    @Test
    void testNonCheckpointInvokableInjectsProcessingTimeServices() throws Exception {
        TimerRegisteringOperator timerOp = new TimerRegisteringOperator(120);
        TestOutput<String> output = new TestOutput<>();
        timerOp.setOutput((io.nop.stream.core.operators.Output) output);

        OperatorChain chain = new OperatorChain(
                java.util.List.of(slowSource(10, 40), timerOp));

        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        invokable.invoke();

        // Wiring evidence (non-checkpoint path: no setBarrierTracker was ever called).
        assertNotNull(invokable.getProcessingTimeService());
        assertNotNull(invokable.getTimeServiceManager());
        assertNotNull(timerOp.ptsAtOpen.get(), "PTS injected BEFORE operator open()");
        assertNotNull(timerOp.tsmAtOpen.get(), "TimerServiceManager injected BEFORE operator open()");
        assertSame(invokable.getProcessingTimeService(), timerOp.ptsAtOpen.get(),
                "Operator and invokable share the same PTS instance");
        assertSame(invokable.getTimeServiceManager(), timerOp.tsmAtOpen.get(),
                "Operator and invokable share the same TimerServiceManager instance");

        // Rule #23 wiring proof: registerTimerService registration observable at runtime.
        assertEquals(1, invokable.getTimeServiceManager().numTimerServices(),
                "Timer-using operator registered its HeapInternalTimerService during open()");

        // End-to-end PT timer firing: driver → mailbox → task thread.
        assertTrue(timerOp.timerFired.get(), "PT timer fired through the production driver");
        assertNotNull(timerOp.firedTimestamp.get());
        assertEquals(10, timerOp.elementCount.get(), "All elements flowed through the chain");
        assertEquals(10, output.getElements().size());
    }

    @Test
    void testInjectionIsUnconditionalAcrossAllRoles() throws Exception {
        // SINK role (inputGate + no writer) must also receive the services: the wiring is
        // per-invokable, not per-role.
        TimerRegisteringOperator timerOp = new TimerRegisteringOperator(60_000);
        timerOp.setOutput((io.nop.stream.core.operators.Output) new TestOutput<>());
        OperatorChain chain = new OperatorChain(java.util.List.of(timerOp));

        ResultPartition partition = new ResultPartition();
        partition.write(new StreamRecord<>("x"));
        partition.close();
        InputGate gate = new InputGate(new InputChannel(partition));

        StreamTaskInvokable invokable = new StreamTaskInvokable(chain, (RecordWriter<Object>) null, gate);
        assertNotNull(invokable.getProcessingTimeService(), "PTS created at construction");
        assertNotNull(invokable.getTimeServiceManager(), "TimerServiceManager created at construction");

        invokable.invoke();
        assertNotNull(timerOp.ptsAtOpen.get(), "PTS injected before open() even for the SINK role");
        assertEquals(1, timerOp.elementCount.get(), "SINK role processed the record");
    }

    @Test
    void testTimerCallbackRunsOnTaskThread() throws Exception {
        AtomicReference<Thread> mainThread = new AtomicReference<>(Thread.currentThread());
        TimerRegisteringOperator timerOp = new TimerRegisteringOperator(100);
        timerOp.setOutput((io.nop.stream.core.operators.Output) new TestOutput<>());

        OperatorChain chain = new OperatorChain(
                java.util.List.of(slowSource(8, 50), timerOp));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        invokable.invoke();

        assertTrue(timerOp.timerFired.get(), "PT timer fired");
        assertEquals(mainThread.get(), timerOp.callbackThread.get(),
                "Timer callback executed on the task (invoke) thread via the mailbox — "
                        + "never on the driver thread");
    }
}
