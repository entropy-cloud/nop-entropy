package io.nop.stream.cep.operator;

import io.nop.stream.cep.CepTestUtils;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.metrics.StreamMetricsRegistries;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plan 366 Phase 3: the {@code numLateRecordsDropped} counter must be wired to
 * the real drop path — an event whose timestamp is at or before the current
 * watermark, with no late-data side output configured, is dropped and counted.
 */
public class TestCepOperatorLateRecordsDroppedMetric {

    private static final String METRIC_NAME = "numLateRecordsDropped";

    private static final ProcessingTimeService MOCK_PTS = new ProcessingTimeService() {
        private long time = 1000;

        @Override
        public long getCurrentProcessingTime() {
            return time++;
        }

        @Override
        public ScheduledFuture<?> registerTimer(long timestamp, ProcessingTimeCallback target) {
            return null;
        }
    };

    private double lateDroppedCount(CepOperator<?, ?, ?> operator) {
        io.micrometer.core.instrument.Counter counter = StreamMetricsRegistries.registry()
                .find(METRIC_NAME)
                .tags("operator", operator.getLateDropMetricOperatorTag(),
                        "subtask", operator.getLateDropMetricSubtaskTag())
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    void lateEventWithoutSideOutputIsDroppedAndCounted() throws Exception {
        PatternProcessFunction<Event, String> function = new PatternProcessFunction<>() {
            @Override
            public void processMatch(Map<String, List<Event>> match, Context ctx, Collector<String> out) {
                out.collect("match");
            }
        };

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")));

        NFACompiler.NFAFactory<Event> nfaFactory = NFACompiler.compileFactory(pattern, false);

        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                new EventTypeSerializer(), false, nfaFactory, null, null, function, null);
        operator.setOutput(new TestOutput<>());
        CepTestUtils.injectProcessingTimeService(operator, MOCK_PTS);
        operator.open();

        double before = lateDroppedCount(operator);

        // On-time event (ts=10), then push the watermark past it.
        operator.processElement(new StreamRecord<>(new Event(42, "a"), 10));
        operator.processWatermark(new Watermark(20));

        // Late event: ts=5 <= current watermark 20, no late-data side output
        // configured → dropped, counter must increment by exactly 1.
        operator.processElement(new StreamRecord<>(new Event(43, "b"), 5));

        assertEquals(1.0, lateDroppedCount(operator) - before,
                "a late event with no side output must drop and increment numLateRecordsDropped");

        operator.close();
    }

    /**
     * R5-CEP-03: two parallel instances (distinct subtask locations) must register
     * DISTINCT meters — drops observed by one instance must never appear in the
     * other instance's counter (the old JVM-level shared counter merged them).
     */
    @Test
    void twoParallelInstancesDoNotCrossCount() throws Exception {
        PatternProcessFunction<Event, String> function = new PatternProcessFunction<>() {
            @Override
            public void processMatch(Map<String, List<Event>> match, Context ctx, Collector<String> out) {
                out.collect("match");
            }
        };

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")));

        NFACompiler.NFAFactory<Event> nfaFactory = NFACompiler.compileFactory(pattern, false);

        // Simulate the runtime deployment copy path: each subtask gets its own
        // operator instance carrying its TaskLocation identity.
        CepOperator<Event, Integer, String> template = new CepOperator<>(
                new EventTypeSerializer(), false, nfaFactory, null, null, function, null);
        io.nop.stream.core.checkpoint.TaskLocation loc0 =
                new io.nop.stream.core.checkpoint.TaskLocation("cep-job", "cep-pipe", "cep-vertex", 0);
        io.nop.stream.core.checkpoint.TaskLocation loc1 =
                new io.nop.stream.core.checkpoint.TaskLocation("cep-job", "cep-pipe", "cep-vertex", 1);
        CepOperator<Event, Integer, String> subtask0 = template.copyForSubtask(loc0);
        CepOperator<Event, Integer, String> subtask1 = template.copyForSubtask(loc1);

        subtask0.setOutput(new TestOutput<>());
        subtask1.setOutput(new TestOutput<>());
        CepTestUtils.injectProcessingTimeService(subtask0, MOCK_PTS);
        CepTestUtils.injectProcessingTimeService(subtask1, MOCK_PTS);
        subtask0.open();
        subtask1.open();

        io.micrometer.core.instrument.Counter counter0 = StreamMetricsRegistries.registry()
                .find(METRIC_NAME)
                .tags("operator", subtask0.getLateDropMetricOperatorTag(),
                        "subtask", subtask0.getLateDropMetricSubtaskTag())
                .counter();
        io.micrometer.core.instrument.Counter counter1 = StreamMetricsRegistries.registry()
                .find(METRIC_NAME)
                .tags("operator", subtask1.getLateDropMetricOperatorTag(),
                        "subtask", subtask1.getLateDropMetricSubtaskTag())
                .counter();

        assertEquals("cep-job.cep-pipe.cep-vertex", subtask0.getLateDropMetricOperatorTag());
        assertEquals("0", subtask0.getLateDropMetricSubtaskTag());
        assertEquals("1", subtask1.getLateDropMetricSubtaskTag());
        org.junit.jupiter.api.Assertions.assertNotEquals(counter0, counter1,
                "two parallel instances must not share one meter");

        // Each subtask drops exactly one distinct late record.
        subtask0.processElement(new StreamRecord<>(new Event(42, "a"), 10));
        subtask0.processWatermark(new Watermark(20));
        subtask0.processElement(new StreamRecord<>(new Event(43, "late-0"), 5));

        subtask1.processElement(new StreamRecord<>(new Event(44, "b"), 10));
        subtask1.processWatermark(new Watermark(20));
        subtask1.processWatermark(new Watermark(30));
        subtask1.processElement(new StreamRecord<>(new Event(45, "late-1"), 5));
        subtask1.processElement(new StreamRecord<>(new Event(46, "late-2"), 6));

        assertEquals(1.0, counter0.count(), "subtask 0's counter counts only its own drop");
        assertEquals(2.0, counter1.count(), "subtask 1's counter counts only its own drops");

        subtask0.close();
        subtask1.close();
    }

    private static class EventTypeSerializer implements TypeSerializer<Event> {
        @Override
        public boolean isImmutableType() {
            return false;
        }

        @Override
        public TypeSerializer<Event> duplicate() {
            return this;
        }

        @Override
        public Event createInstance() {
            return new Event();
        }

        @Override
        public Event copy(Event from) {
            return new Event(from.getId(), from.getName());
        }

        @Override
        public Event copy(Event from, Event reuse) {
            return new Event(from.getId(), from.getName());
        }

        @Override
        public int getLength() {
            return -1;
        }
    }
}
