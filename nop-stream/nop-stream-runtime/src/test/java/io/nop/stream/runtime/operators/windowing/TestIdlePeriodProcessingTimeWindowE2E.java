/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.test.time.ManualClock;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.windowing.WindowOperator;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.testsupport.TestAwait;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.assigners.TumblingProcessingTimeWindows;
import io.nop.stream.core.windowing.triggers.ProcessingTimeTrigger;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-02 (P1) idle-period regression E2E: a processing-time window task whose
 * upstream goes completely silent must still fire windows at processing-time
 * boundaries, and the processing-time cleanup timer must fire, WITHOUT any new
 * data arriving.
 *
 * <p>Setup: a two-vertex graph — source task → window task (cross-task via
 * ResultPartition/InputGate, so the window task runs the production
 * {@code processInputGate} loop). The source emits one element, then goes
 * completely silent (blocked on a test-released latch — the idle gap), then
 * emits a second element and, only after the test confirmed the second window
 * fired, finishes.
 *
 * <p>时间确定化（plan 2510）：processing-time 语义全链路经 {@code CoreMetrics}
 * （WindowOperator 的窗口分配 / ProcessingTimeTrigger 注册、TaskProcessingTimeService、
 * ProcessingTimeServiceDriver 的轮询、InputGate 的 idle-return 阈值），测试注册
 * {@link ManualClock} 显式推进虚拟时间触发 fire；「idle 期间已 fire」的中期断言以
 * 「第二个元素仍被 latch 关住」为结构前提（不可能到达），「尚未 fire」负断言先取
 * 驱动线程 + 任务线程已在新时刻重评时钟的读取计数证据。不再使用任何真实时间窗。
 *
 * <p>Anti-masking (the existing tests only drain after the source finishes):
 * the test asserts the first window already fired while the source is still
 * blocked (the job is still running). Pre-fix (red): the fire mail sat in the
 * mailbox until the second element arrived (the mailbox is only drained at the
 * {@code processInputGate} loop top, which an idle {@code InputGate.read()}
 * never reached), so the mid-idle assertion finds an empty result list.
 *
 * <p>Plan guide coverage: #22 (end-to-end: env entry → cross-task gate → PT
 * trigger → sink output), #23 (wiring: the idle-return makes the loop top
 * {@code processAvailableMails()} actually run during idle — asserted by the
 * mid-idle fire), #24 (no silent skip: a failure to drain produces zero output
 * and the mid-idle assertion fails).
 */
class TestIdlePeriodProcessingTimeWindowE2E {

    private static final long WINDOW_SIZE = 300L;

    private ManualClock clock;

    @BeforeEach
    void registerManualClock() {
        clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
    }

    @AfterEach
    void restoreDefaultClock() {
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
    }

    @TempDir
    Path tempDir;

    static final class IntEvent {
        final String key;
        final int value;

        IntEvent(String key, int value) {
            this.key = key;
            this.value = value;
        }
    }

    /**
     * Emits one element, then goes silent — blocked on the test-held idle latch
     * (the idle gap has NO duration on any real clock; it lasts exactly until
     * the test opens the latch) — then emits a second element and blocks on the
     * trailing latch until the test confirms the second window's fire mail
     * drained (the pre-EOS drain the old trailing sleep provided), then returns.
     */
    private static SourceFunction<IntEvent> idleGapSource(CountDownLatch idleGate,
                                                          CountDownLatch trailingGate) {
        return new SourceFunction<IntEvent>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<IntEvent> ctx) throws InterruptedException {
                ctx.collect(new IntEvent("key1", 1));
                idleGate.await();
                ctx.collect(new IntEvent("key1", 1));
                trailingGate.await();
            }

            @Override
            public void cancel() {
            }
        };
    }

    /** Emits the single accumulated element's value per fired window. */
    private static class IntEventValueWindowFunction
            implements io.nop.stream.core.operators.windowing.functions.InternalWindowFunction<
            Object, Integer, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String key, TimeWindow window,
                            InternalWindowContext context,
                            Object input, Collector<Integer> out) {
            out.collect(((IntEvent) input).value);
        }

        @Override
        public void clear(TimeWindow window, InternalWindowContext context) {
        }
    }

    private JobGraph buildIdleWindowGraph(List<Integer> results, CountDownLatch idleGate,
                                          CountDownLatch trailingGate) {
        StreamSourceOperator<IntEvent> sourceOp =
                new StreamSourceOperator<>(idleGapSource(idleGate, trailingGate));

        WindowOperator<String, IntEvent, Object, Integer, TimeWindow> windowOp =
                new WindowOperator<String, IntEvent, Object, Integer, TimeWindow>(
                        TumblingProcessingTimeWindows.of(WINDOW_SIZE),
                        new LocalSerializers.SimpleTimeWindowSerializer(),
                        (KeySelector<IntEvent, String>) e -> e.key,
                        new LocalSerializers.SimpleStringSerializer(),
                        String.class,
                        new IntEventValueWindowFunction(),
                        ProcessingTimeTrigger.create(),
                        0L,
                        null);

        StreamSinkOperator<Integer> sinkOp = new StreamSinkOperator<>(new SinkFunction<Integer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(Integer value) {
                results.add(value);
            }
        });

        List<KeySelector<?, ?>> keySelectors = new ArrayList<>();
        keySelectors.add((KeySelector<IntEvent, String>) e -> e.key);
        keySelectors.add(null);
        OperatorChain windowChain = new OperatorChain(
                Arrays.asList((io.nop.stream.core.operators.StreamOperator<?>) windowOp, sinkOp),
                keySelectors);
        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));

        JobVertex sourceVertex = new JobVertex("source-idle", "Source", 1,
                Collections.singletonList(sourceChain),
                new io.nop.stream.core.execution.task.StreamTaskInvokable(sourceChain));
        JobVertex windowVertex = new JobVertex("window-idle", "Sink", 1,
                Collections.singletonList(windowChain),
                new io.nop.stream.core.execution.task.StreamTaskInvokable(windowChain));

        JobGraph jobGraph = new JobGraph("idle-pt-window-e2e");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(windowVertex);
        jobGraph.addEdge(new JobEdge("source-idle", "window-idle", ResultPartitionType.PIPELINED));
        return jobGraph;
    }

    @Test
    void testProcessingTimeWindowFiresDuringIdlePeriod() throws Exception {
        List<Integer> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch idleGate = new CountDownLatch(1);
        CountDownLatch trailingGate = new CountDownLatch(1);

        JobGraph jobGraph = buildIdleWindowGraph(results, idleGate, trailingGate);

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId("idle-pt-window");
        config.setPipelineId("1");
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(60_000); // effectively never on the virtual line
        config.setCheckpointTimeout(10_000);
        config.setStorageProperty("path", tempDir.toString());

        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread jobThread = new Thread(() -> {
            try {
                GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, "idle-pt-window", config);
            } catch (Throwable t) {
                error.set(t);
            }
        }, "idle-pt-window-job");
        jobThread.start();

        // 结构证据：窗口任务已处理 element 1 并进入 idle 轮询、driver 线程已多次重评
        // 时钟（两者都读 CoreMetrics）——此后「未 fire」负断言成立。
        TestAwait.until("window task and driver re-evaluated the virtual clock after element 1",
                () -> clock.totalReads() >= 3);
        assertTrue(results.isEmpty(),
                "no window may fire before the virtual clock passes the window boundary");

        // 推进循环：InputGate 的 idle-return 阈值基线在每次 read() 调用重新盖章，
        // 虚拟时钟冻结后每次推进只给窗口任务一个 loop-top 排空机会（processAvailable
        // Mails）；driver 在推进后的 tick（100ms 真实节奏，生产内部轮询）投递 fire
        // mail，由下一次推进送进排空点——循环迭代以「时钟已被重评」为节奏（读取计数
        // 增长），上限仅挂死保护。替代旧的墙钟 idle-gap + 轮询 deadline。
        for (int i = 0; i < 100 && results.isEmpty(); i++) {
            long readsBefore = clock.totalReads();
            clock.advance(WINDOW_SIZE);
            TestAwait.until("clock re-evaluated after the advance (drain opportunity given)",
                    () -> clock.totalReads() > readsBefore, 5_000L);
        }
        assertTrue(!results.isEmpty(),
                "PT window 1 must fire DURING the idle period (second element still latch-gated). "
                        + "The idle drain did not reach the mailbox (pre-fix the fire mail is stuck "
                        + "until the next record arrives)");
        assertTrue(jobThread.isAlive(),
                "The mid-idle fire must happen while the job is still running (the source is "
                        + "latch-blocked, so the second element structurally cannot have arrived) — "
                        + "not only at the source-finished drain (the existing masking mechanism)");

        // 释放 idle 门：element 2 到达（此刻虚拟时间已越过窗口 1 边界 → 落入下一个窗口），
        // 注册窗口 2 的 fire timer；推进循环触发第二次 fire（fire mail 在 EOS 前排空
        // ——trailing latch 仍关住 source）。
        idleGate.countDown();
        for (int i = 0; i < 100 && results.size() < 2; i++) {
            long readsBefore = clock.totalReads();
            clock.advance(WINDOW_SIZE);
            TestAwait.until("clock re-evaluated after the advance (second window drain opportunity)",
                    () -> clock.totalReads() > readsBefore, 5_000L);
        }
        assertTrue(results.size() >= 2,
                "PT window 2 must fire and drain before EOS (trailing latch still held)");

        // 释放 trailing 门 → source 结束 → EOS → 作业收尾。
        trailingGate.countDown();
        jobThread.join(30_000);
        assertNull(error.get(), () -> "Job must not fail: " + error.get());
        assertFalse(jobThread.isAlive(), "Job must terminate cleanly");

        // Both windows fired: the first mid-idle, the second before EOS.
        assertEquals(Arrays.asList(1, 1), results,
                "Exactly two processing-time windows must fire (one per element): " + results);
    }

    /**
     * Cleanup-timer companion to {@link #testProcessingTimeWindowFiresDuringIdlePeriod}:
     * with the data plane completely idle (no EOS yet), the window's processing-time
     * CLEANUP timer must also fire at its processing-time point — proven directly on the
     * {@code internalTimerService}: mid-idle, after the window fired, the timer count
     * must be back to zero (both the fire and the cleanup timer were drained by the
     * idle mailbox drain). Pre-fix the cleanup timer stayed registered while the task
     * was idle (the mailbox was never drained).
     *
     * <p>Direct invokable-level assembly (no graph deep-copy), so the test holds the
     * live {@link WindowOperator} instance and can assert the timer-service state.
     */
    @Test
    void testProcessingTimeCleanupTimerFiresDuringIdlePeriod() throws Exception {
        io.nop.stream.core.test.TestOutput<Integer> output = new io.nop.stream.core.test.TestOutput<>();
        WindowOperator<String, IntEvent, Object, Integer, TimeWindow> windowOp =
                new WindowOperator<String, IntEvent, Object, Integer, TimeWindow>(
                        TumblingProcessingTimeWindows.of(WINDOW_SIZE),
                        new LocalSerializers.SimpleTimeWindowSerializer(),
                        (KeySelector<IntEvent, String>) e -> e.key,
                        new LocalSerializers.SimpleStringSerializer(),
                        String.class,
                        new IntEventValueWindowFunction(),
                        ProcessingTimeTrigger.create(),
                        0L,
                        null);
        windowOp.setOutput((io.nop.stream.core.operators.Output) output);

        List<KeySelector<?, ?>> keySelectors = new ArrayList<>();
        keySelectors.add((KeySelector<IntEvent, String>) e -> e.key);
        OperatorChain chain = new OperatorChain(
                Collections.singletonList((io.nop.stream.core.operators.StreamOperator<?>) windowOp),
                keySelectors);

        // SINK-role invokable: an input gate fed by a partition that the test keeps
        // open — the task idles (no EOS) after the first element.
        io.nop.stream.core.execution.ResultPartition partition =
                new io.nop.stream.core.execution.ResultPartition();
        io.nop.stream.core.execution.InputGate gate = new io.nop.stream.core.execution.InputGate(
                new io.nop.stream.core.execution.InputChannel(partition));
        io.nop.stream.core.execution.task.StreamTaskInvokable invokable =
                new io.nop.stream.core.execution.task.StreamTaskInvokable(
                        chain, (io.nop.stream.core.execution.RecordWriter<Object>) null, gate);

        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread taskThread = new Thread(() -> {
            try {
                invokable.invoke();
            } catch (Throwable t) {
                error.set(t);
            }
        }, "idle-cleanup-task");
        taskThread.start();

        partition.write(new StreamRecord<>(new IntEvent("key1", 1)));

        // 结构证据：任务线程已处理 element 1（窗口 fire timer 注册）且在 idle 轮询中
        // 多次重评虚拟时钟——此后「timer 未 fire」断言成立。
        TestAwait.until("element 1 processed (fire timer registered) and task idling on the virtual clock",
                () -> windowOp.numPendingProcessingTimeTimers() == 1 && clock.readsBy(taskThread) >= 2);
        assertTrue(output.isEmpty(),
                "no window may fire before the virtual clock passes the boundary: " + error.get());
        assertEquals(1, windowOp.numPendingProcessingTimeTimers(),
                "fire timer still pending before the advance");

        // 第一次推进越过 fire/cleanup 截止点：driver 下一 tick（生产内部 100ms 轮询）
        // 投递 fire mail——确定性投递证据 = mailbox 尺寸增长（任务驻留于 idle read，
        // 不会取走 mail；idle-return 阈值基线在每次 read() 调用重新盖章，冻结虚拟线
        // 上任务在两次推进间不产生 loop-top 排空）。
        clock.advance(WINDOW_SIZE);
        TestAwait.until("driver delivered the fire mail (mailbox grows while the task idles)",
                () -> invokable.getMailboxExecutor().getMailbox().size() >= 1, 10_000L);

        // 第二次推进：解锁 idle-return → loop-top 排空 fire mail → 窗口 fire +
        // cleanup timer fire（真实链路，非 EOS 排空）。上限仅挂死保护。
        clock.advance(WINDOW_SIZE);
        TestAwait.until("PT window fired AND cleanup timer fired mid-idle (data plane still open)",
                () -> !output.isEmpty() && windowOp.numPendingProcessingTimeTimers() == 0, 10_000L);
        assertFalse(output.isEmpty(),
                "PT window must fire mid-idle (idle drain wiring): " + error.get());
        assertEquals(0, windowOp.numPendingProcessingTimeTimers(),
                "PT cleanup timer must fire mid-idle — the idle drain must reach the "
                        + "mailbox, not just the EOS drain. Remaining timers: "
                        + windowOp.numPendingProcessingTimeTimers());
        assertTrue(taskThread.isAlive(),
                "Task must still be running (no EOS yet) — the mid-idle assertions "
                        + "must not rely on the termination drain");

        // Terminate: EOS closes the task.
        partition.close();
        taskThread.join(10_000);
        assertNull(error.get(), () -> "Task must not fail: " + error.get());
        assertFalse(taskThread.isAlive(), "Task must terminate after EOS");
    }

    /** WI7 plan 2417: local copies — the production-test serializers are package-private in core. */
    private static class LocalSerializers {
        static class SimpleTimeWindowSerializer implements io.nop.stream.core.common.typeutils.TypeSerializer<TimeWindow> {
            private static final long serialVersionUID = 1L;
            @Override public boolean isImmutableType() { return true; }
            @Override public TypeSerializer<TimeWindow> duplicate() { return this; }
            @Override public TimeWindow createInstance() { return new TimeWindow(0, 0); }
            @Override public TimeWindow copy(TimeWindow from) { return new TimeWindow(from.getStart(), from.getEnd()); }
            @Override public TimeWindow copy(TimeWindow from, TimeWindow reuse) { return new TimeWindow(from.getStart(), from.getEnd()); }
            @Override public int getLength() { return -1; }
        }

        static class SimpleStringSerializer implements io.nop.stream.core.common.typeutils.TypeSerializer<String> {
            private static final long serialVersionUID = 1L;
            @Override public boolean isImmutableType() { return true; }
            @Override public TypeSerializer<String> duplicate() { return this; }
            @Override public String createInstance() { return ""; }
            @Override public String copy(String from) { return from; }
            @Override public String copy(String from, String reuse) { return from; }
            @Override public int getLength() { return -1; }
        }
    }

}
