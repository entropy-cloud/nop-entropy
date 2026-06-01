package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

public class TestWindowContextState {

    private TestOutput<String> output;
    private WindowOperator<String, Integer, Object, String, TimeWindow> operator;

    @BeforeEach
    void setUp() throws Exception {
        output = new TestOutput<>();
    }

    private void setupOperator(InternalWindowFunction<Object, String, String, TimeWindow> function) throws Exception {
        operator = new WindowOperator<>(
                TumblingEventTimeWindows.of(100L),
                new SimpleTimeWindowSerializer(),
                (KeySelector<Integer, String>) v -> "key1",
                new SimpleStringSerializer(),
                String.class,
                function,
                EventTimeTrigger.create(),
                0L,
                null
        );
        operator.setOutput((Output) output);
        operator.open();
    }

    @Test
    void testWindowStatePerWindowIsolation() throws Exception {
        List<String> windowLabels = Collections.synchronizedList(new ArrayList<>());
        Map<String, String> capturedStates = new ConcurrentHashMap<>();

        InternalWindowFunction<Object, String, String, TimeWindow> function =
                new InternalWindowFunction<Object, String, String, TimeWindow>() {
                    @Override
                    public void process(String key, TimeWindow window, InternalWindowContext context,
                                        Object input, io.nop.stream.core.util.Collector<String> out) throws Exception {
                        windowLabels.add(window.getStart() + "-" + window.getEnd());
                        try {
                            KeyedStateStore ws = context.windowState();
                            ValueState<String> state = ws.getState(
                                    new ValueStateDescriptor<>("ws-test", String.class));
                            String current = state.value();
                            if (current == null) {
                                state.update("w:" + window.getStart() + "-" + window.getEnd());
                            } else {
                                state.update(current + ",w:" + window.getStart() + "-" + window.getEnd());
                            }
                            capturedStates.put(window.getStart() + "-" + window.getEnd(), state.value());
                        } catch (Exception e) {
                            capturedStates.put("error-" + window.getStart(), e.toString());
                        }
                        out.collect("fired:" + window.getStart());
                    }

                    @Override
                    public void clear(TimeWindow window, InternalWindowContext context) {
                    }
                };

        setupOperator(function);

        operator.processElement(new StreamRecord<>(1, 50));
        operator.processElement(new StreamRecord<>(2, 150));
        operator.processElement(new StreamRecord<>(3, 250));

        operator.processWatermark(new Watermark(350));

        assertFalse(windowLabels.isEmpty(), "Windows should have fired, got: " + windowLabels);
        assertEquals(3, windowLabels.size(), "Three windows should have fired");

        String state0 = capturedStates.get("0-100");
        String state1 = capturedStates.get("100-200");
        String state2 = capturedStates.get("200-300");

        assertNotNull(state0, "Window [0,100) should have state, keys: " + capturedStates.keySet());
        assertNotNull(state1, "Window [100,200) should have state");
        assertNotNull(state2, "Window [200,300) should have state");

        assertEquals("w:0-100", state0, "Window [0,100) state should be isolated");
        assertEquals("w:100-200", state1, "Window [100,200) state should be isolated");
        assertEquals("w:200-300", state2, "Window [200,300) state should be isolated");

        operator.close();
    }

    @Test
    void testGlobalStateSharedAcrossWindows() throws Exception {
        List<String> capturedGlobalStates = new ArrayList<>();

        InternalWindowFunction<Object, String, String, TimeWindow> function =
                new InternalWindowFunction<Object, String, String, TimeWindow>() {
                    @Override
                    public void process(String key, TimeWindow window, InternalWindowContext context,
                                        Object input, io.nop.stream.core.util.Collector<String> out) throws IOException {
                        KeyedStateStore gs = context.globalState();
                        ValueState<Long> counter = gs.getState(
                                new ValueStateDescriptor<>("global-counter", Long.class));
                        Long current = counter.value();
                        long newVal = (current == null ? 0 : current) + 1;
                        counter.update(newVal);
                        capturedGlobalStates.add(window.getStart() + "-" + window.getEnd() + ":" + newVal);
                    }

                    @Override
                    public void clear(TimeWindow window, InternalWindowContext context) {
                    }
                };

        setupOperator(function);

        operator.processElement(new StreamRecord<>(1, 50));
        operator.processElement(new StreamRecord<>(2, 150));
        operator.processElement(new StreamRecord<>(3, 250));

        operator.processWatermark(new Watermark(350));

        assertEquals(3, capturedGlobalStates.size(), "Three windows should have fired");

        assertEquals("0-100:1", capturedGlobalStates.get(0), "First window: global counter = 1");
        assertEquals("100-200:2", capturedGlobalStates.get(1), "Second window: global counter = 2");
        assertEquals("200-300:3", capturedGlobalStates.get(2), "Third window: global counter = 3");

        operator.close();
    }

    @Test
    void testWindowStateAndGlobalStateDontInterfere() throws Exception {
        List<String> results = new ArrayList<>();

        InternalWindowFunction<Object, String, String, TimeWindow> function =
                new InternalWindowFunction<Object, String, String, TimeWindow>() {
                    @Override
                    public void process(String key, TimeWindow window, InternalWindowContext context,
                                        Object input, io.nop.stream.core.util.Collector<String> out) throws IOException {
                        KeyedStateStore ws = context.windowState();
                        KeyedStateStore gs = context.globalState();

                        ValueState<String> windowState = ws.getState(
                                new ValueStateDescriptor<>("shared-name", String.class));
                        ValueState<String> globalState = gs.getState(
                                new ValueStateDescriptor<>("shared-name", String.class));

                        String wv = windowState.value();
                        String gv = globalState.value();

                        windowState.update("window-" + window.getStart());
                        globalState.update("global-" + window.getStart());

                        results.add("w=" + wv + ",g=" + gv);
                    }

                    @Override
                    public void clear(TimeWindow window, InternalWindowContext context) {
                    }
                };

        setupOperator(function);

        operator.processElement(new StreamRecord<>(1, 50));
        operator.processElement(new StreamRecord<>(2, 150));

        operator.processWatermark(new Watermark(250));

        assertEquals(2, results.size(), "Two windows should have fired");

        assertEquals("w=null,g=null", results.get(0), "First window: both states null initially");
        assertEquals("w=null,g=global-0", results.get(1),
                "Second window: window state isolated (null), global state carries over");

        operator.close();
    }

    static class SimpleTimeWindowSerializer implements TypeSerializer<TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() { return true; }

        @Override
        public TypeSerializer<TimeWindow> duplicate() { return this; }

        @Override
        public TimeWindow createInstance() { return new TimeWindow(0, 0); }

        @Override
        public TimeWindow copy(TimeWindow from) { return new TimeWindow(from.getStart(), from.getEnd()); }

        @Override
        public TimeWindow copy(TimeWindow from, TimeWindow reuse) { return new TimeWindow(from.getStart(), from.getEnd()); }

        @Override
        public int getLength() { return -1; }
    }

    static class SimpleStringSerializer implements TypeSerializer<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() { return true; }

        @Override
        public TypeSerializer<String> duplicate() { return this; }

        @Override
        public String createInstance() { return ""; }

        @Override
        public String copy(String from) { return from; }

        @Override
        public String copy(String from, String reuse) { return from; }

        @Override
        public int getLength() { return -1; }
    }
}
