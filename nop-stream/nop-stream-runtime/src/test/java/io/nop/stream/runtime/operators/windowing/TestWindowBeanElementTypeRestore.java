package io.nop.stream.runtime.operators.windowing;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.WindowFunction;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.operators.HeapInternalTimerService;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * F-05 regression proof (plan 1326-2 Phase 1): the window apply path's ListState
 * element type must be the real IN class (flowing from the builder descriptor), so
 * bean elements survive the JSON checkpoint persist path as beans — not
 * LinkedHashMaps that CCE in the user function on the first post-restore fire.
 *
 * <p>Mirrors the storage-layer round trip {@code CheckpointSerDe} performs
 * (serialize keyed-state snapshot data as JSON, parse back into a plain map).
 */
class TestWindowBeanElementTypeRestore {

    @DataBean
    public static class TradeEvent {
        private long id;
        private long amount;

        public TradeEvent() {
        }

        public TradeEvent(long id, long amount) {
            this.id = id;
            this.amount = amount;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public long getAmount() {
            return amount;
        }

        public void setAmount(long amount) {
            this.amount = amount;
        }
    }

    /** Captures the element classes the window function actually receives. */
    static class ClassCapturingWindowFunction implements WindowFunction<TradeEvent, String, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        final List<Class<?>> elementClasses = new ArrayList<>();
        final List<TradeEvent> typedElements = new ArrayList<>();

        @Override
        public void apply(String key, TimeWindow window, Iterable<TradeEvent> input, Collector<String> out) {
            for (TradeEvent e : input) {
                elementClasses.add(e.getClass());
                if (e.getClass() == TradeEvent.class) {
                    typedElements.add(e);
                }
            }
            out.collect("count=" + elementClasses.size());
        }
    }

    private static WindowOperator<String, TradeEvent, Iterable<TradeEvent>, String, TimeWindow> buildOperator(
            ClassCapturingWindowFunction fn) {
        return new WindowOperatorBuilder<TradeEvent, String, TimeWindow>()
                .windowAssigner(TumblingEventTimeWindows.of(200L))
                .trigger(EventTimeTrigger.create())
                .keySelector((KeySelector<TradeEvent, String>) v -> "key1")
                .keyClass(String.class)
                .keySerializer(new TestWindowOperatorBuilder.SimpleStringSerializer())
                .windowSerializer(new TestWindowOperatorBuilder.SimpleTimeWindowSerializer())
                .apply(fn, TradeEvent.class);
    }

    /**
     * Bean elements buffered in the window ListState must survive a JSON
     * checkpoint round trip with {@code getClass() == TradeEvent.class}.
     */
    @Test
    void testBeanElementsKeepTypeAfterJsonCheckpointRestore() throws Exception {
        ClassCapturingWindowFunction fn1 = new ClassCapturingWindowFunction();
        WindowOperator<String, TradeEvent, Iterable<TradeEvent>, String, TimeWindow> op1 = buildOperator(fn1);
        TestOutput<String> out1 = new TestOutput<>();
        op1.setOutput((Output) out1);
        op1.open();
        try {
            op1.processElement(new StreamRecord<>(new TradeEvent(1, 100), 10));
            op1.processElement(new StreamRecord<>(new TradeEvent(2, 200), 50));

            // snapshot before the window fires, so the restore path is what feeds the fire
            OperatorSnapshotResult snap = op1.snapshotState(new StateSnapshotContext(1, 1L));

            // JSON round trip of the keyed state (what CheckpointSerDe's persist path does)
            Object keyedObj = snap.getKeyedStates().get("keyed-state");
            assertNotNull(keyedObj, "operator snapshot must carry keyed state");
            StateSnapshot stateSnapshot = (StateSnapshot) keyedObj;
            String json = JsonTool.serialize(stateSnapshot.getStateData(), false);
            Map<String, Object> parsed = JsonTool.parseMap(json);
            StateSnapshot restoredSnapshot = new StateSnapshot(parsed);

            OperatorSnapshotResult restoreResult = new OperatorSnapshotResult();
            restoreResult.putKeyedState("keyed-state", restoredSnapshot);

            ClassCapturingWindowFunction fn2 = new ClassCapturingWindowFunction();
            WindowOperator<String, TradeEvent, Iterable<TradeEvent>, String, TimeWindow> op2 =
                    buildOperator(fn2);
            TestOutput<String> out2 = new TestOutput<>();
            // restore BEFORE open (the production lifecycle: restoreState runs first,
            // open() applies the pending restore into the fresh backend)
            op2.restoreState(restoreResult);
            op2.setOutput((Output) out2);
            op2.open();
            try {
                // Timers are not carried by this keyed-state-only restore, so feed one
                // fresh element into the same [0,200) window to (re)register the fire
                // timer; the fire must then iterate the RESTORED beans — pre-fix they
                // came back as LinkedHashMaps from the JSON round trip.
                op2.processElement(new StreamRecord<>(new TradeEvent(3, 300), 20));
                advanceWatermark(op2, 199L);

                assertEquals(1, out2.size(), "restored window must fire once");
                assertEquals("count=3", out2.getElements().get(0),
                        "2 restored beans + 1 fresh element must be aggregated");
                assertEquals(3, fn2.elementClasses.size());
                for (Class<?> clazz : fn2.elementClasses) {
                    assertEquals(TradeEvent.class, clazz,
                            "restored window element must be a TradeEvent, not a JSON-native map");
                }
                assertEquals(100L, fn2.typedElements.get(0).getAmount());
                assertEquals(200L, fn2.typedElements.get(1).getAmount());
                assertEquals(300L, fn2.typedElements.get(2).getAmount());
            } finally {
                op2.close();
            }
        } finally {
            op1.close();
        }
    }

    private static void advanceWatermark(WindowOperator<?, ?, ?, ?, ?> op, long ts) throws Exception {
        java.lang.reflect.Field f = WindowOperator.class.getDeclaredField("internalTimerService");
        f.setAccessible(true);
        Object svc = f.get(op);
        if (svc instanceof HeapInternalTimerService) {
            ((HeapInternalTimerService<?, ?>) svc).advanceWatermark(ts);
        }
    }
}
