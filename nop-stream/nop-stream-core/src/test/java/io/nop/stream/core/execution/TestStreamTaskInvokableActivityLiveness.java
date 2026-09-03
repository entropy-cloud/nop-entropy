package io.nop.stream.core.execution;

import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.StreamMap;
import io.nop.stream.core.streamrecord.StreamElement;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G52 / AR-01: task-thread aliveness ({@code lastActivityTime}) vs data progress
 * ({@code lastProgressTime}) semantics on {@link StreamTaskInvokable}.
 *
 * <p>A healthy but data-idle MIDDLE/SINK task keeps its <b>activity</b> timestamp
 * fresh at every idle loop iteration (the AR-02 idle-return path cycles back to
 * the loop top), while its data-progress timestamp stays frozen. The TaskManager
 * heartbeat reports the activity timestamp for MIDDLE/SINK, so the coordinator
 * never flags an idle task as stalled — and a genuinely hung task (loop no
 * longer ticking) still ages out.
 */
public class TestStreamTaskInvokableActivityLiveness {

    private static OperatorChain buildEmptyOperatorChain() {
        return new OperatorChain(Collections.singletonList(new StreamMap<>(x -> x)));
    }

    @Test
    void idleSinkLoopKeepsActivityFreshWhileDataProgressFrozen() throws Exception {
        StreamTaskInvokable inv = new StreamTaskInvokable(
                buildEmptyOperatorChain(), (RecordWriter<Object>) null, new IdleInputGate());
        long progressAtStart = inv.getLastProgressTime();
        long activityAtStart = inv.getLastActivityTime();

        AtomicReference<Throwable> taskError = new AtomicReference<>();
        CountDownLatch exited = new CountDownLatch(1);
        Thread taskThread = new Thread(() -> {
            try {
                inv.invoke();
            } catch (Throwable t) {
                taskError.set(t);
            } finally {
                exited.countDown();
            }
        });
        taskThread.start();

        try {
            // Idle long enough for several idle-return cycles.
            Thread.sleep(600);

            assertNull(taskError.get(), "idle task must not fail: " + taskError.get());
            long progressAfter = inv.getLastProgressTime();
            long activityAfter = inv.getLastActivityTime();

            assertEquals(progressAtStart, progressAfter,
                    "data progress must stay frozen while no data flows");
            assertTrue(activityAfter >= activityAtStart + 300,
                    "task-thread activity must keep advancing during idle (loop top tick), "
                            + "activityAtStart=" + activityAtStart + " activityAfter=" + activityAfter);
            assertTrue(activityAfter >= System.currentTimeMillis() - 200,
                    "activity must be fresh (within the last idle cycle), got " + activityAfter);
        } finally {
            inv.getMailboxExecutor().signalCancel();
            assertTrue(exited.await(3, TimeUnit.SECONDS), "task must exit after cooperative cancel");
            taskThread.join(1_000);
        }
    }

    /**
     * An input gate that is momentarily idle forever: no data, never finished.
     * Mirrors the AR-02 idle-return path. The single dummy channel is never
     * touched because read()/isAllFinished() are overridden.
     */
    static class IdleInputGate extends InputGate {
        IdleInputGate() {
            super(Collections.singletonList(new InputChannel(new ResultPartition())));
        }

        @Override
        public Optional<StreamElement> read() {
            return Optional.empty();
        }

        @Override
        public boolean isAllFinished() {
            return false;
        }
    }
}
