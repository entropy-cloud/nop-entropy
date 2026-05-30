package io.nop.stream.cep.operator;

import io.nop.stream.cep.CepTestUtils;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.nfa.NFAState;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for basic event matching in CepOperator:
 * simple pattern match, condition failure, keyed state isolation, and multiple matches.
 */
public class TestCepOperatorBasic {

    private List<String> results;
    private PatternProcessFunction<Event, String> function;
    private NFACompiler.NFAFactory<Event> nfaFactory;

    @BeforeEach
    void setUp() {
        results = new ArrayList<>();
        function = new PatternProcessFunction<>() {
            @Override
            public void processMatch(Map<String, List<Event>> match, Context ctx, Collector<String> out) {
                Event start = match.get("start").get(0);
                Event end = match.get("end").get(0);
                out.collect(start.getName() + "->" + end.getName());
            }
        };

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")));

        nfaFactory = NFACompiler.compileFactory(pattern, false);
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

    private CepOperator<Event, Integer, String> createOperator() throws Exception {
        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                new EventTypeSerializer(),
                false,
                nfaFactory,
                null,
                null,
                function,
                null
        );

        operator.setOutput(new TestOutput(results));
        setProcessingTimeService(operator, MOCK_PTS);
        operator.open();
        return operator;
    }

    private static void setProcessingTimeService(CepOperator<?, ?, ?> op, ProcessingTimeService svc) {
        CepTestUtils.injectProcessingTimeService(op, svc);
    }

    @Test
    void testSimplePatternMatch() throws Exception {
        CepOperator<Event, Integer, String> operator = createOperator();

        // Send start event with id=42 (satisfies >=42) and name="a"
        operator.processElement(new StreamRecord<>(new Event(42, "a"), 1));
        // Send end event with name="end"
        operator.processElement(new StreamRecord<>(new Event(99, "end"), 2));
        operator.processWatermark(new Watermark(10));

        assertFalse(results.isEmpty(), "Should produce at least one match");
        assertEquals("a->end", results.get(0),
                "Match should be 'a->end'");
        assertTrue(results.size() == 1,
                "Should produce exactly one match, got: " + results);

        operator.close();
    }

    @Test
    void testNoMatchWhenConditionFails() throws Exception {
        CepOperator<Event, Integer, String> operator = createOperator();

        // Send start event with id=10 (does NOT satisfy >=42)
        operator.processElement(new StreamRecord<>(new Event(10, "a"), 1));
        // Send end event with name="end"
        operator.processElement(new StreamRecord<>(new Event(99, "end"), 2));
        operator.processWatermark(new Watermark(10));

        assertTrue(results.isEmpty(),
                "Should produce no match when start condition fails, got: " + results);

        operator.close();
    }

    @Test
    void testKeyedStateIsolation() throws Exception {
        // Create two operators simulating different keys
        List<String> results1 = new ArrayList<>();
        List<String> results2 = new ArrayList<>();

        CepOperator<Event, Integer, String> op1 = new CepOperator<>(
                new EventTypeSerializer(), false, nfaFactory, null, null, function, null
        );
        op1.setOutput(new TestOutput(results1));
        setProcessingTimeService(op1, MOCK_PTS);
        op1.open();

        CepOperator<Event, Integer, String> op2 = new CepOperator<>(
                new EventTypeSerializer(), false, nfaFactory, null, null, function, null
        );
        op2.setOutput(new TestOutput(results2));
        setProcessingTimeService(op2, MOCK_PTS);
        op2.open();

        // Send matching events only to op1
        op1.processElement(new StreamRecord<>(new Event(42, "a"), 1));
        op1.processElement(new StreamRecord<>(new Event(99, "end"), 2));
        op1.processWatermark(new Watermark(10));

        // Verify op1 got the match
        assertFalse(results1.isEmpty(), "op1 should produce a match");
        assertEquals("a->end", results1.get(0));

        // Verify op2 got nothing (state isolation)
        assertTrue(results2.isEmpty(),
                "op2 should produce no match (keyed state isolation)");

        // op2's NFA state should have only initial states, no partial matches from op1
        NFAState state2 = op2.getNFAStateForTesting();
        assertNotNull(state2, "op2 should have NFA state");
        // The initial state has exactly 1 partial match (the start state)
        assertEquals(1, state2.getPartialMatches().size(),
                "op2 should only have initial state, no partial matches from op1");

        op1.close();
        op2.close();
    }

    @Test
    void testMultiplePatternMatches() throws Exception {
        CepOperator<Event, Integer, String> operator = createOperator();

        // First match sequence: start(id=42, name="a") -> end(name="end")
        operator.processElement(new StreamRecord<>(new Event(42, "a"), 1));
        operator.processElement(new StreamRecord<>(new Event(99, "end"), 2));
        operator.processWatermark(new Watermark(5));

        // Second match sequence: start(id=50, name="b") -> end(name="end")
        operator.processElement(new StreamRecord<>(new Event(50, "b"), 6));
        operator.processElement(new StreamRecord<>(new Event(100, "end"), 7));
        operator.processWatermark(new Watermark(20));

        assertTrue(results.size() >= 2,
                "Should produce at least two matches, got: " + results);
        assertTrue(results.contains("a->end"),
                "Should contain first match 'a->end', got: " + results);
        assertTrue(results.contains("b->end"),
                "Should contain second match 'b->end', got: " + results);

        operator.close();
    }

    private static class TestOutput implements Output<StreamRecord<String>> {
        private final List<String> results;

        TestOutput(List<String> results) {
            this.results = results;
        }

        @Override
        public void collect(StreamRecord<String> record) {
            results.add(record.getValue());
        }

        @Override
        public void close() {
        }

        @Override
        public void emitWatermark(Watermark mark) {
        }

        @Override
        public void emitBarrier(CheckpointBarrier barrier) {
        }

        @Override
        public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
        }

        @Override
        public void emitLatencyMarker(LatencyMarker latencyMarker) {
        }

        @Override
        public void emitWatermarkStatus(WatermarkStatus watermarkStatus) {
        }
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
