/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

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
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.assigners.TumblingProcessingTimeWindows;
import io.nop.stream.core.windowing.triggers.ProcessingTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AR-02 (P1) idle-period regression E2E: a processing-time window task whose
 * upstream goes completely silent must still fire windows at wall-clock
 * boundaries, and the processing-time cleanup timer must fire, WITHOUT any new
 * data arriving.
 *
 * <p>Setup: a two-vertex graph — source task → window task (cross-task via
 * ResultPartition/InputGate, so the window task runs the production
 * {@code processInputGate} loop). The source emits one element, then stays
 * silent across several window boundaries (the idle gap), then emits a second
 * element and, after a trailing sleep, finishes.
 *
 * <p>Anti-masking (the existing tests only drain after the source finishes):
 * the test POLLS the sink mid-idle — while the source is still asleep and the
 * job is still running — and asserts the first window already fired. Pre-fix
 * (red): the fire mail sat in the mailbox until the second element arrived
 * (the mailbox is only drained at the {@code processInputGate} loop top, which
 * an idle {@code InputGate.read()} never reached), so the poll finds an empty
 * result list for the whole idle gap.
 *
 * <p>Plan guide coverage: #22 (end-to-end: env entry → cross-task gate → PT
 * trigger → sink output), #23 (wiring: the idle-return makes the loop top
 * {@code processAvailableMails()} actually run during idle — asserted by the
 * mid-idle fire), #24 (no silent skip: a failure to drain produces zero output
 * and the mid-idle assertion fails).
 */
class TestIdlePeriodProcessingTimeWindowE2E {

    private static final long WINDOW_SIZE = 300L;

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
     * Emits one element, then goes silent for {@code idleGapMs} (several window
     * boundaries), then emits a second element and sleeps {@code trailingSleepMs}
     * before returning (so the second window's fire mail drains before EOS).
     */
    private static SourceFunction<IntEvent> idleGapSource(long idleGapMs, long trailingSleepMs) {
        return new SourceFunction<IntEvent>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<IntEvent> ctx) {
                ctx.collect(new IntEvent("key1", 1));
                try {
                    Thread.sleep(idleGapMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                ctx.collect(new IntEvent("key1", 1));
                try {
                    Thread.sleep(trailingSleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public void cancel() {
            }
        };
    }

    /** Emits the single accumulated element's value per fired window. */
    private static class IntEventValueWindowFunction
            implements io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction<
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

    private JobGraph buildIdleWindowGraph(List<Integer> results, long idleGapMs, long trailingSleepMs) {
        StreamSourceOperator<IntEvent> sourceOp =
                new StreamSourceOperator<>(idleGapSource(idleGapMs, trailingSleepMs));

        WindowOperator<String, IntEvent, Object, Integer, TimeWindow> windowOp =
                new WindowOperator<String, IntEvent, Object, Integer, TimeWindow>(
                        TumblingProcessingTimeWindows.of(WINDOW_SIZE),
                        new TestProcessingTimeWindowProductionE2E.SimpleTimeWindowSerializer(),
                        (KeySelector<IntEvent, String>) e -> e.key,
                        new TestProcessingTimeWindowProductionE2E.SimpleStringSerializer(),
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
                new io.nop.stream.core.execution.StreamTaskInvokable(sourceChain));
        JobVertex windowVertex = new JobVertex("window-idle", "Sink", 1,
                Collections.singletonList(windowChain),
                new io.nop.stream.core.execution.StreamTaskInvokable(windowChain));

        JobGraph jobGraph = new JobGraph("idle-pt-window-e2e");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(windowVertex);
        jobGraph.addEdge(new JobEdge("source-idle", "window-idle", ResultPartitionType.PIPELINED));
        return jobGraph;
    }

    @Test
    void testProcessingTimeWindowFiresDuringIdlePeriod() throws Exception {
        List<Integer> results = Collections.synchronizedList(new ArrayList<>());
        // Idle gap: 2 window sizes + 600ms of complete silence. Long enough that
        // the poll deadline below lands strictly BEFORE the second element
        // (e2 at t0+1200) regardless of job-setup jitter, so the pre-fix red run
        // cannot false-pass.
        long idleGapMs = 2 * WINDOW_SIZE + 600;
        long trailingSleepMs = 900;

        JobGraph jobGraph = buildIdleWindowGraph(results, idleGapMs, trailingSleepMs);

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId("idle-pt-window");
        config.setPipelineId("1");
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(60000); // effectively never for this short job
        config.setCheckpointTimeout(10000);
        config.setStorageProperty("path", tempDir.toString());

        long start = System.currentTimeMillis();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread jobThread = new Thread(() -> {
            try {
                GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, "idle-pt-window", config);
            } catch (Throwable t) {
                error.set(t);
            }
        }, "idle-pt-window-job");
        jobThread.start();

        // Mid-idle assertion (anti-masking): while the source is still in its
        // idle sleep — well before the second element (t0+1200) — the first
        // window must ALREADY have fired: data-plane idle → InputGate idle-return
        // → processInputGate loop top → processAvailableMails() drains the fire
        // mail → window fires at the wall-clock boundary. Pre-fix (red): the fire
        // mail stays in the mailbox until the second element arrives, so the poll
        // deadline finds an empty result list.
        long deadline = start + 2 * WINDOW_SIZE + 400;
        while (System.currentTimeMillis() < deadline
                && results.isEmpty()
                && jobThread.isAlive()) {
            Thread.sleep(50);
        }
        assertFalse(results.isEmpty(),
                "PT window must fire DURING the idle period (before the second element). "
                        + "Elapsed: " + (System.currentTimeMillis() - start) + "ms — the idle "
                        + "drain did not reach the mailbox (pre-fix the fire mail is stuck "
                        + "until the next record arrives)");
        assertTrue(jobThread.isAlive(),
                "The mid-idle fire must happen while the job is still running, "
                        + "not only at the source-finished drain (the existing masking mechanism)");

        jobThread.join(30000);
        assertNull(error.get(), () -> "Job must not fail: " + error.get());
        assertFalse(jobThread.isAlive(), "Job must terminate cleanly");

        // Both windows fired: the first mid-idle, the second after the trailing
        // sleep drained its fire mail before the EOS.
        assertEquals(Arrays.asList(1, 1), results,
                "Exactly two processing-time windows must fire (one per element): " + results);
    }

    /**
     * Cleanup-timer companion to {@link #testProcessingTimeWindowFiresDuringIdlePeriod}:
     * with the data plane completely idle (no EOS yet), the window's processing-time
     * CLEANUP timer must also fire at its wall-clock time — proven directly on the
     * {@code internalTimerService}: mid-idle, after the window fired, the timer count
     * must be back to zero (both the fire and the cleanup timer were drained by the
     * idle mailbox drain). Pre-fix the cleanup timer stayed registered while the task
     * was idle (the mailbox was never drained), so the poll below times out.
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
                        new TestProcessingTimeWindowProductionE2E.SimpleTimeWindowSerializer(),
                        (KeySelector<IntEvent, String>) e -> e.key,
                        new TestProcessingTimeWindowProductionE2E.SimpleStringSerializer(),
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
        io.nop.stream.core.execution.StreamTaskInvokable invokable =
                new io.nop.stream.core.execution.StreamTaskInvokable(
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

        // Mid-idle assertion: the window fired AND the cleanup timer fired while the
        // data plane is still completely idle (partition open, no EOS).
        long deadline = System.currentTimeMillis() + 3000L;
        while (System.currentTimeMillis() < deadline
                && (output.isEmpty()
                || windowOp.internalTimerService.numProcessingTimeTimers() > 0)) {
            Thread.sleep(50);
        }
        assertFalse(output.isEmpty(),
                "PT window must fire mid-idle (idle drain wiring): " + error.get());
        assertEquals(0, windowOp.internalTimerService.numProcessingTimeTimers(),
                "PT cleanup timer must fire mid-idle — the idle drain must reach the "
                        + "mailbox, not just the EOS drain. Remaining timers: "
                        + windowOp.internalTimerService.numProcessingTimeTimers());
        assertTrue(taskThread.isAlive(),
                "Task must still be running (no EOS yet) — the mid-idle assertions "
                        + "must not rely on the termination drain");

        // Terminate: EOS closes the task.
        partition.close();
        taskThread.join(10000);
        assertNull(error.get(), () -> "Task must not fail: " + error.get());
        assertFalse(taskThread.isAlive(), "Task must terminate after EOS");
    }
}
