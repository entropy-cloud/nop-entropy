package io.nop.stream.cep.operator;

import io.nop.stream.cep.CepTestUtils;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.nfa.NFAState;
import io.nop.stream.cep.nfa.aftermatch.AfterMatchSkipStrategy;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.util.OutputTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for CEP pattern matching with different skip strategies.
 * Covers noSkip, skipPastLastEvent, skipToNext, skipToFirst, and skipToLast.
 */
public class TestCepSkipStrategyE2E {

    private TestOutput<String> output;
    private PatternProcessFunction<Event, String> function;

    @BeforeEach
    void setUp() {
        output = new TestOutput<>();
        function = new PatternProcessFunction<>() {
            @Override
            public void processMatch(Map<String, List<Event>> match, Context ctx, Collector<String> out) {
                StringJoiner joiner = new StringJoiner("->");
                for (Map.Entry<String, List<Event>> entry : match.entrySet()) {
                    joiner.add(entry.getValue().get(0).getName());
                }
                out.collect(joiner.toString());
            }
        };
    }

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

    private CepOperator<Event, Integer, String> createOperator(
            Pattern<Event, ?> pattern) throws Exception {
        NFACompiler.NFAFactory<Event> nfaFactory = NFACompiler.compileFactory(pattern, false);
        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                new EventTypeSerializer(),
                false,
                nfaFactory,
                null,
                pattern.getAfterMatchSkipStrategy(),
                function,
                null
        );

        operator.setOutput(output);
        setProcessingTimeService(operator, MOCK_PTS);
        operator.open();
        return operator;
    }

    private static void setProcessingTimeService(CepOperator<?, ?, ?> op, ProcessingTimeService svc) {
        CepTestUtils.injectProcessingTimeService(op, svc);
    }

    @Test
    void testNoSkipProducesAllMatches() throws Exception {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start",
                        AfterMatchSkipStrategy.noSkip())
                .where(SimpleCondition.of(e -> e.getName().startsWith("a")))
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("end")));

        CepOperator<Event, Integer, String> op = createOperator(pattern);

        op.processElement(new StreamRecord<>(new Event(1, "a1"), 1));
        op.processElement(new StreamRecord<>(new Event(2, "a2"), 2));
        op.processElement(new StreamRecord<>(new Event(99, "end"), 3));
        op.processWatermark(new Watermark(10));

        assertTrue(output.size() >= 2,
                "noSkip should produce all matches, got: " + output.getElements());
        assertTrue(output.getElements().contains("a1->end"));
        assertTrue(output.getElements().contains("a2->end"));

        op.close();
    }

    @Test
    void testSkipPastLastEventSkipsOverlappingMatches() throws Exception {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start",
                        AfterMatchSkipStrategy.skipPastLastEvent())
                .where(SimpleCondition.of(e -> e.getName().startsWith("a")))
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("end")));

        CepOperator<Event, Integer, String> op = createOperator(pattern);

        op.processElement(new StreamRecord<>(new Event(1, "a1"), 1));
        op.processElement(new StreamRecord<>(new Event(2, "a2"), 2));
        op.processElement(new StreamRecord<>(new Event(99, "end"), 3));
        op.processWatermark(new Watermark(10));

        assertEquals(1, output.size(),
                "skipPastLastEvent should produce exactly 1 match, got: " + output.getElements());
        assertTrue(output.getElements().contains("a1->end"),
                "First match should be a1->end, got: " + output.getElements());

        op.close();
    }

    @Test
    void testSkipToNextSkipsSameStartMatches() throws Exception {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start",
                        AfterMatchSkipStrategy.skipToNext())
                .where(SimpleCondition.of(e -> e.getName().startsWith("a")))
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("end")));

        CepOperator<Event, Integer, String> op = createOperator(pattern);

        op.processElement(new StreamRecord<>(new Event(1, "a1"), 1));
        op.processElement(new StreamRecord<>(new Event(2, "a2"), 2));
        op.processElement(new StreamRecord<>(new Event(99, "end"), 3));
        op.processWatermark(new Watermark(10));

        assertEquals(2, output.size(),
                "skipToNext should produce 2 matches, got: " + output.getElements());
        assertTrue(output.getElements().contains("a1->end"));

        op.close();
    }

    @Test
    void testSkipToFirstSkipsToPattern() throws Exception {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start",
                        AfterMatchSkipStrategy.skipToFirst("start"))
                .where(SimpleCondition.of(e -> e.getName().startsWith("a")))
                .followedBy("middle")
                .where(SimpleCondition.of(e -> e.getName().startsWith("b")))
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("end")));

        CepOperator<Event, Integer, String> op = createOperator(pattern);

        op.processElement(new StreamRecord<>(new Event(1, "a1"), 1));
        op.processElement(new StreamRecord<>(new Event(2, "a2"), 2));
        op.processElement(new StreamRecord<>(new Event(3, "b1"), 3));
        op.processElement(new StreamRecord<>(new Event(99, "end"), 4));
        op.processWatermark(new Watermark(10));

        assertEquals(2, output.size(),
                "skipToFirst should produce 2 matches, got: " + output.getElements());
        assertTrue(output.getElements().contains("a1->b1->end") || output.getElements().stream().anyMatch(r -> r.endsWith("->end")),
                "Should produce a match ending with 'end', got: " + output.getElements());

        op.close();
    }

    @Test
    void testSkipToLastSkipsToLastPatternEvent() throws Exception {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start",
                        AfterMatchSkipStrategy.skipToLast("start"))
                .where(SimpleCondition.of(e -> e.getName().startsWith("a")))
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("end")));

        CepOperator<Event, Integer, String> op = createOperator(pattern);

        op.processElement(new StreamRecord<>(new Event(1, "a1"), 1));
        op.processElement(new StreamRecord<>(new Event(2, "a2"), 2));
        op.processElement(new StreamRecord<>(new Event(99, "end"), 3));
        op.processWatermark(new Watermark(10));

        assertEquals(2, output.size(),
                "skipToLast should produce 2 matches, got: " + output.getElements());

        op.close();
    }

    @Test
    void testNoSkipWithOneOrMoreProducesMultipleMatches() throws Exception {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start",
                        AfterMatchSkipStrategy.noSkip())
                .where(SimpleCondition.of(e -> e.getName().startsWith("a")))
                .oneOrMore()
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("end")));

        CepOperator<Event, Integer, String> op = createOperator(pattern);

        op.processElement(new StreamRecord<>(new Event(1, "a1"), 1));
        op.processElement(new StreamRecord<>(new Event(2, "a2"), 2));
        op.processElement(new StreamRecord<>(new Event(99, "end"), 3));
        op.processWatermark(new Watermark(10));

        assertEquals(3, output.size(),
                "noSkip+oneOrMore should produce 3 matches, got: " + output.getElements());

        op.close();
    }

    @Test
    void testSkipToNextWithOneOrMore() throws Exception {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start",
                        AfterMatchSkipStrategy.skipToNext())
                .where(SimpleCondition.of(e -> e.getName().startsWith("a")))
                .oneOrMore()
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("end")));

        CepOperator<Event, Integer, String> op = createOperator(pattern);

        op.processElement(new StreamRecord<>(new Event(1, "a1"), 1));
        op.processElement(new StreamRecord<>(new Event(2, "a2"), 2));
        op.processElement(new StreamRecord<>(new Event(99, "end"), 3));
        op.processWatermark(new Watermark(10));

        assertEquals(2, output.size(),
                "skipToNext+oneOrMore should produce 2 matches, got: " + output.getElements());

        op.close();
    }

    @Test
    void testSkipPastLastEventWithOneOrMore() throws Exception {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start",
                        AfterMatchSkipStrategy.skipPastLastEvent())
                .where(SimpleCondition.of(e -> e.getName().startsWith("a")))
                .oneOrMore()
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("end")));

        CepOperator<Event, Integer, String> op = createOperator(pattern);

        op.processElement(new StreamRecord<>(new Event(1, "a1"), 1));
        op.processElement(new StreamRecord<>(new Event(2, "a2"), 2));
        op.processElement(new StreamRecord<>(new Event(99, "end"), 3));
        op.processWatermark(new Watermark(10));

        assertEquals(1, output.size(),
                "skipPastLastEvent+oneOrMore should produce exactly 1 match, got: " + output.getElements());

        op.close();
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
