package io.nop.stream.core.operators;

import io.nop.stream.core.common.functions.ProcessFunction;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.VoidNamespace;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestProcessOperator {

    @Test
    void testProcessElementIsCalled() throws Exception {
        List<String> results = new ArrayList<>();
        ProcessFunction<String, String> function = new ProcessFunction<>() {
            @Override
            public void processElement(String value, Context ctx, Collector<String> out) {
                results.add("processed:" + value);
                out.collect(value.toUpperCase());
            }
        };

        ProcessOperator<String, String> operator = new ProcessOperator<>(function);
        TestOutput<String> output = new TestOutput<>();
        operator.setOutput(output);
        operator.open();

        operator.processElement(new StreamRecord<>("hello", 1000));
        operator.processElement(new StreamRecord<>("world", 2000));

        assertEquals(2, results.size());
        assertEquals("processed:hello", results.get(0));
        assertEquals("processed:world", results.get(1));
        assertEquals(2, output.getElements().size());
        assertEquals("HELLO", output.getElements().get(0));
        assertEquals("WORLD", output.getElements().get(1));

        operator.close();
    }

    @Test
    void testContextTimestamp() throws Exception {
        ProcessFunction<String, String> function = new ProcessFunction<>() {
            @Override
            public void processElement(String value, Context ctx, Collector<String> out) {
                out.collect("ts=" + ctx.timestamp());
            }
        };

        ProcessOperator<String, String> operator = new ProcessOperator<>(function);
        TestOutput<String> output = new TestOutput<>();
        operator.setOutput(output);
        operator.open();

        operator.processElement(new StreamRecord<>("a", 500));
        assertEquals("ts=500", output.getElements().get(0));

        operator.processElement(new StreamRecord<>("b"));
        assertEquals("ts=null", output.getElements().get(1));

        operator.close();
    }

    @Test
    void testOnTimerViaWatermark() throws Exception {
        List<Long> timerFired = new ArrayList<>();
        ProcessFunction<String, String> function = new ProcessFunction<>() {
            @Override
            public void processElement(String value, Context ctx, Collector<String> out) {
                ctx.timerService().registerEventTimeTimer(100);
                out.collect("registered:" + value);
            }

            @Override
            public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) {
                timerFired.add(timestamp);
                out.collect("fired:" + timestamp);
            }
        };

        ProcessOperator<String, String> operator = new ProcessOperator<>(function);
        TestOutput<String> output = new TestOutput<>();
        operator.setOutput(output);
        TimerServiceManager tsm = new TimerServiceManager();
        operator.setTimeServiceManager(tsm);
        operator.open();

        operator.processElement(new StreamRecord<>("x", 10));
        assertEquals(1, output.getElements().size());
        assertEquals(0, timerFired.size());

        operator.processWatermark(new Watermark(200));

        assertEquals(1, timerFired.size());
        assertEquals(100L, timerFired.get(0));
        assertEquals(2, output.getElements().size());
        assertEquals("fired:100", output.getElements().get(1));

        operator.close();
    }

    @Test
    void testKeyedStateAccess() throws Exception {
        MemoryStateBackend stateBackend = new MemoryStateBackend();
        var keyedStateBackend = stateBackend.createKeyedStateBackend(String.class);

        ProcessFunction<String, String> function = new ProcessFunction<>() {
            private ValueState<Integer> countState;

            @Override
            public void processElement(String value, Context ctx, Collector<String> out) throws Exception {
                if (countState == null) {
                    countState = getRuntimeContext().getKeyedStateStore()
                            .getState(new ValueStateDescriptor<>("count", Integer.class, 0));
                }
                int count = countState.value();
                countState.update(count + 1);
                out.collect(value + ":" + (count + 1));
            }
        };

        ProcessOperator<String, String> operator = new ProcessOperator<>(function);
        TestOutput<String> output = new TestOutput<>();
        operator.setOutput(output);
        operator.setKeyedStateBackend(keyedStateBackend);
        operator.open();

        keyedStateBackend.setCurrentKey("k1");
        operator.processElement(new StreamRecord<>("a"));
        assertEquals("a:1", output.getElements().get(0));

        keyedStateBackend.setCurrentKey("k2");
        operator.processElement(new StreamRecord<>("b"));
        assertEquals("b:1", output.getElements().get(1));

        keyedStateBackend.setCurrentKey("k1");
        operator.processElement(new StreamRecord<>("c"));
        assertEquals("c:2", output.getElements().get(2));

        operator.close();
    }

    @Test
    void testTimerServiceCurrentWatermark() throws Exception {
        ProcessFunction<String, String> function = new ProcessFunction<>() {
            @Override
            public void processElement(String value, Context ctx, Collector<String> out) {
                long wm = ctx.timerService().currentWatermark();
                out.collect("wm=" + wm);
            }
        };

        ProcessOperator<String, String> operator = new ProcessOperator<>(function);
        TestOutput<String> output = new TestOutput<>();
        operator.setOutput(output);
        TimerServiceManager tsm = new TimerServiceManager();
        operator.setTimeServiceManager(tsm);
        operator.open();

        operator.processWatermark(new Watermark(42));
        operator.processElement(new StreamRecord<>("x"));
        assertEquals("wm=42", output.getElements().get(0));

        operator.close();
    }

    @Test
    void testMultipleOutputElements() throws Exception {
        ProcessFunction<String, String> function = new ProcessFunction<>() {
            @Override
            public void processElement(String value, Context ctx, Collector<String> out) {
                out.collect(value + "_1");
                out.collect(value + "_2");
                out.collect(value + "_3");
            }
        };

        ProcessOperator<String, String> operator = new ProcessOperator<>(function);
        TestOutput<String> output = new TestOutput<>();
        operator.setOutput(output);
        operator.open();

        operator.processElement(new StreamRecord<>("a"));
        assertEquals(3, output.getElements().size());
        assertEquals("a_1", output.getElements().get(0));
        assertEquals("a_2", output.getElements().get(1));
        assertEquals("a_3", output.getElements().get(2));

        operator.close();
    }
}
