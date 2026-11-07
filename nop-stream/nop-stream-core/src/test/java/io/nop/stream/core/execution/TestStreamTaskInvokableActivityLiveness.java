package io.nop.stream.core.execution;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.test.time.ManualClock;
import io.nop.stream.core.testsupport.TestAwait;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
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
        // 时间确定化（plan 2510）：activity/progress 时间戳经 CoreMetrics，测试
        // 注册 ManualClock——「activity 持续推进」由虚拟时钟推进 + 任务线程推进后
        // 重读时钟的结构证据（读取计数增长）确定性验证；负向断言（空闲多轮不失败）
        // 以「任务线程已多轮重评」为结构判据，替代 600ms 墙钟观察窗与真实耗时阈值。
        ManualClock clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
        try {
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
                // 结构证据：任务线程在空闲循环中多轮重评（loop top tick 读时钟），
                // 且期间任务不得失败。
                TestAwait.until("idle task re-ticked its loop several times without failing",
                        () -> clock.readsBy(taskThread) >= 3 && taskError.get() == null);

                assertNull(taskError.get(), "idle task must not fail: " + taskError.get());
                long progressAfter = inv.getLastProgressTime();
                assertEquals(progressAtStart, progressAfter,
                        "data progress must stay frozen while no data flows");

                // 推进虚拟时钟越过阈值：任务线程下一次 loop-top tick 读到新时刻 →
                // activity 在虚拟线上确定性前进（替代真实 300ms 耗时阈值断言）。
                long readsBeforeAdvance = clock.readsBy(taskThread);
                clock.advance(300L);
                TestAwait.until("task loop re-read the clock after the virtual advance",
                        () -> clock.readsBy(taskThread) > readsBeforeAdvance);
                long activityAfter = inv.getLastActivityTime();
                assertTrue(activityAfter >= activityAtStart + 300,
                        "task-thread activity must keep advancing during idle (loop top tick), "
                                + "activityAtStart=" + activityAtStart + " activityAfter=" + activityAfter);
            } finally {
                inv.getMailboxExecutor().signalCancel();
                assertTrue(exited.await(3, TimeUnit.SECONDS), "task must exit after cooperative cancel");
                taskThread.join(1_000);
            }
        } finally {
            CoreMetrics.registerClock(CoreMetrics.defaultClock());
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
