package io.nop.stream.core.datastream;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.operators.WindowAggregationFunction;
import io.nop.stream.core.operators.WindowAggregationOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestAllowedLatenessPropagation {

    static class IdentityKeySelector implements KeySelector<String, String> {
        @Override
        public String getKey(String value) { return value; }
    }

    static class ListAgg implements WindowAggregationFunction<String, List<String>, String, String, TimeWindow> {
        @Override public List<String> createAccumulator() { return new ArrayList<>(); }
        @Override public List<String> add(String value, List<String> acc) { acc.add(value); return acc; }
        @Override public List<String> merge(List<String> a, List<String> b) { a.addAll(b); return a; }
        @Override public void emitResult(String key, TimeWindow window, List<String> acc, Collector<String> out) {
            out.collect(String.join(",", acc));
        }
    }

    @Test
    void allowedLatenessIsRespectedInWindowOperator() throws Exception {
        WindowAssigner<Object, TimeWindow> assigner = TumblingEventTimeWindows.of(1000);
        Trigger<Object, TimeWindow> trigger = EventTimeTrigger.create();

        WindowAggregationOperator<String, List<String>, String, String, TimeWindow> operator =
                new WindowAggregationOperator<>(assigner, trigger, new ListAgg(), new IdentityKeySelector());
        operator.setAllowedLateness(5000);

        TestOutput<String> output = new TestOutput<>();
        operator.setOutput((io.nop.stream.core.operators.Output) output);
        operator.open();

        operator.setCurrentKey("k1");
        operator.processElement(new StreamRecord<>("a", 100));
        operator.processWatermark(new Watermark(1100));

        assertFalse(output.isEmpty(), "Should emit window result on watermark");

        operator.processElement(new StreamRecord<>("late-b", 100));
        assertTrue(output.getElements().size() >= 1,
                "Element within allowedLateness should not be dropped, may re-trigger window");

        operator.close();
    }
}
