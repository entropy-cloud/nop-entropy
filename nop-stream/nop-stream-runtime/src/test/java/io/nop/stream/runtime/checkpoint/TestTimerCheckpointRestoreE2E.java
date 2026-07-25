/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.HeapInternalTimerService;
import io.nop.stream.core.operators.InternalTimerService;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.operators.windowing.WindowOperator;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for G2 (timer checkpoint/restore): verifies that event-time timers
 * registered before a checkpoint survive the checkpoint→kill→restore cycle and fire
 * correctly after recovery, producing the same window output as if no failure occurred.
 *
 * <p>Test flow:
 * <ol>
 *   <li>{@code open()} the operator</li>
 *   <li>process elements with timestamps that register event-time cleanup timers</li>
 *   <li>{@code snapshotState()} captures timer snapshot</li>
 *   <li>create a <b>new</b> {@code WindowOperator} with the same configuration</li>
 *   <li>{@code restoreState(snapshotResult)} on the new operator (deferred store — timer service is null)</li>
 *   <li>{@code open()} on the new operator (applies the deferred timer snapshot)</li>
 *   <li>{@code processWatermark()} past the timer threshold</li>
 *   <li>verify restored timers fire and produce correct window output</li>
 * </ol>
 *
 * <p>This exercises the full deferred-application pattern required by the
 * restore-before-open lifecycle constraint (see {@code TestCheckpointRecovery.java:478}).
 */
class TestTimerCheckpointRestoreE2E {

    private static final long WINDOW_SIZE = 100L;

    @Test
    void testTimerSurvivesCheckpointAndFiresAfterRestore() throws Exception {
        TestOutput<String> output1 = new TestOutput<>();
        TestableWindowOperator op1 = newOperator();
        op1.setOutput((Output) output1);
        op1.open();

        try {
            // Process elements into window [0, 100). EventTimeTrigger.onElement()
            // registers an event-time cleanup timer at window.maxTimestamp() = 99.
            op1.processElement(new StreamRecord<>(5, 10));
            op1.processElement(new StreamRecord<>(3, 20));
            op1.processElement(new StreamRecord<>(7, 50));

            assertTrue(output1.isEmpty(), "No output before watermark advances");

            // Verify the cleanup timer was registered before snapshot.
            InternalTimerService<TimeWindow> timerSvc1 = op1.getInternalTimerService();
            assertNotNull(timerSvc1);
            assertTrue(timerSvc1 instanceof HeapInternalTimerService);
            assertTrue(((HeapInternalTimerService<String, TimeWindow>) timerSvc1).numEventTimeTimers() > 0,
                    "EventTimeTrigger should have registered at least one event-time timer");

            // Snapshot BEFORE advancing watermark — captures pending timers.
            OperatorSnapshotResult snapshot = op1.snapshotState(
                    new StateSnapshotContext(1L, System.currentTimeMillis()));

            // Verify the snapshot includes the "internal-timers" key.
            Object timerSnapshot = snapshot.getOperatorState("internal-timers");
            assertNotNull(timerSnapshot, "snapshotState() must include timer state under 'internal-timers'");
            assertTrue(timerSnapshot instanceof HeapInternalTimerService.TimerSnapshot);
            assertFalse(((HeapInternalTimerService.TimerSnapshot<?, ?>) timerSnapshot).isEmpty(),
                    "Timer snapshot should contain pending timers");

            // Simulate kill + restore: create a brand-new operator.
            TestOutput<String> output2 = new TestOutput<>();
            TestableWindowOperator op2 = newOperator();
            op2.setOutput((Output) output2);

            // restoreState runs BEFORE open() — internalTimerService is still null.
            // This is the deferred-application pattern.
            op2.restoreState(snapshot);

            // open() creates the timer service and applies the deferred snapshot.
            op2.open();

            try {
                // Verify the restored timer service has the pending timer.
                InternalTimerService<TimeWindow> timerSvc2 = op2.getInternalTimerService();
                assertNotNull(timerSvc2);
                assertTrue(((HeapInternalTimerService<String, TimeWindow>) timerSvc2).numEventTimeTimers() > 0,
                        "Restored timer service should contain the cleanup timer from the snapshot");

                // Advance watermark past the timer threshold — restored timer should fire.
                op2.advanceInternalWatermark(99);

                // The restored timer fires and produces window output matching the
                // pre-checkpoint state.
                assertEquals(1, output2.size(),
                        "Restored timer should fire and emit exactly one window result");
                // ToStringWindowFunction emits the last value (last-write-wins accumulator).
                assertEquals("7", output2.getElements().get(0));
            } finally {
                op2.close();
            }
        } finally {
            op1.close();
        }
    }

    @Test
    void testNoDoubleFireOfAlreadyFiredTimers() throws Exception {
        TestOutput<String> output1 = new TestOutput<>();
        TestableWindowOperator op1 = newOperator();
        op1.setOutput((Output) output1);
        op1.open();

        try {
            op1.processElement(new StreamRecord<>(5, 10));
            op1.processElement(new StreamRecord<>(15, 110));

            // Advance watermark to fire the FIRST window [0, 100) but NOT the second [100, 200).
            op1.advanceInternalWatermark(99);
            assertEquals(1, output1.size(), "First window should fire");
            assertEquals("5", output1.getElements().get(0));

            output1.clear();

            // Snapshot AFTER first window fired — its cleanup timer is gone.
            // Only the second window's cleanup timer (at 199) should be in the snapshot.
            OperatorSnapshotResult snapshot = op1.snapshotState(
                    new StateSnapshotContext(1L, System.currentTimeMillis()));

            Object timerSnapshot = snapshot.getOperatorState("internal-timers");
            assertNotNull(timerSnapshot);
            HeapInternalTimerService.TimerSnapshot<?, ?> ts =
                    (HeapInternalTimerService.TimerSnapshot<?, ?>) timerSnapshot;
            assertTrue(ts.getEventTimeTimers().size() >= 1,
                    "Snapshot should contain the second window's pending timer");

            // Restore into a new operator.
            TestOutput<String> output2 = new TestOutput<>();
            TestableWindowOperator op2 = newOperator();
            op2.setOutput((Output) output2);
            op2.restoreState(snapshot);
            op2.open();

            try {
                // Advance watermark to 99 — the first window's timer is NOT in the
                // snapshot (it already fired), so nothing should be emitted.
                op2.advanceInternalWatermark(99);
                assertEquals(0, output2.size(),
                        "Already-fired timer must not be in snapshot and must not double-fire");

                // Advance to 199 — the second window's timer fires.
                op2.advanceInternalWatermark(199);
                assertEquals(1, output2.size(), "Second window timer should fire after restore");
                assertEquals("15", output2.getElements().get(0));
            } finally {
                op2.close();
            }
        } finally {
            op1.close();
        }
    }

    @Test
    void testEmptyTimerSnapshotRestoreIsNoError() throws Exception {
        TestOutput<String> output1 = new TestOutput<>();
        TestableWindowOperator op1 = newOperator();
        op1.setOutput((Output) output1);
        op1.open();

        try {
            // Snapshot immediately after open() — no elements processed, no timers registered.
            OperatorSnapshotResult snapshot = op1.snapshotState(
                    new StateSnapshotContext(1L, System.currentTimeMillis()));

            Object timerSnapshot = snapshot.getOperatorState("internal-timers");
            assertNotNull(timerSnapshot, "snapshotTimers() should return a non-null (possibly empty) snapshot");
            assertTrue(((HeapInternalTimerService.TimerSnapshot<?, ?>) timerSnapshot).isEmpty(),
                    "No timers registered → empty snapshot");

            // Restore empty snapshot — should complete without error.
            TestOutput<String> output2 = new TestOutput<>();
            TestableWindowOperator op2 = newOperator();
            op2.setOutput((Output) output2);
            op2.restoreState(snapshot);
            op2.open();

            try {
                // No timers to fire.
                op2.advanceInternalWatermark(1000);
                assertEquals(0, output2.size());
            } finally {
                op2.close();
            }
        } finally {
            op1.close();
        }
    }

    // ------------------------------------------------------------------------

    private TestableWindowOperator newOperator() {
        return new TestableWindowOperator(
                TumblingEventTimeWindows.of(WINDOW_SIZE),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                new ToStringWindowFunction(),
                EventTimeTrigger.create(),
                0L,
                null
        );
    }

    static class TestableWindowOperator extends WindowOperator<String, Integer, Object, String, TimeWindow> {

        TestableWindowOperator(
                TumblingEventTimeWindows windowAssigner,
                TypeSerializer<TimeWindow> windowSerializer,
                KeySelector<Integer, String> keySelector,
                TypeSerializer<String> keySerializer,
                Class<String> keyClass,
                InternalWindowFunction<Object, String, String, TimeWindow> windowFunction,
                EventTimeTrigger trigger,
                long allowedLateness,
                OutputTag<Integer> lateDataOutputTag) {
            super(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                    windowFunction, trigger, allowedLateness, lateDataOutputTag);
        }

        void advanceInternalWatermark(long timestamp) throws Exception {
            internalTimerService.advanceWatermark(timestamp);
        }

        InternalTimerService<TimeWindow> getInternalTimerService() {
            return internalTimerService;
        }
    }

    static class ToStringWindowFunction implements InternalWindowFunction<Object, String, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String key, TimeWindow window, InternalWindowContext context,
                            Object input, io.nop.stream.core.util.Collector<String> out) {
            out.collect(String.valueOf(input));
        }

        @Override
        public void clear(TimeWindow window, InternalWindowContext context) {
        }
    }

    static class SimpleTimeWindowSerializer implements TypeSerializer<TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() {
            return true;
        }

        @Override
        public TypeSerializer<TimeWindow> duplicate() {
            return this;
        }

        @Override
        public TimeWindow createInstance() {
            return new TimeWindow(0, 0);
        }

        @Override
        public TimeWindow copy(TimeWindow from) {
            return new TimeWindow(from.getStart(), from.getEnd());
        }

        @Override
        public TimeWindow copy(TimeWindow from, TimeWindow reuse) {
            return new TimeWindow(from.getStart(), from.getEnd());
        }

        @Override
        public int getLength() {
            return -1;
        }
    }

    static class SimpleStringSerializer implements TypeSerializer<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() {
            return true;
        }

        @Override
        public TypeSerializer<String> duplicate() {
            return this;
        }

        @Override
        public String createInstance() {
            return "";
        }

        @Override
        public String copy(String from) {
            return from;
        }

        @Override
        public String copy(String from, String reuse) {
            return from;
        }

        @Override
        public int getLength() {
            return -1;
        }
    }
}
