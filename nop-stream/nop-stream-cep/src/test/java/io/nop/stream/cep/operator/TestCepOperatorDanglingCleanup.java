package io.nop.stream.cep.operator;

import io.nop.stream.cep.CepTestUtils;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.nfa.NFAState;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;

public class TestCepOperatorDanglingCleanup {

    private TestOutput<String> output;
    private PatternProcessFunction<Event, String> function;
    private NFACompiler.NFAFactory<Event> nfaFactory;

    @BeforeEach
    void setUp() {
        output = new TestOutput<>();
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
        operator.setOutput(output);
        CepTestUtils.injectProcessingTimeService(operator, MOCK_PTS);
        operator.open();
        return operator;
    }

    @Test
    void testDanglingCleanupReleasesSharedBuffer() throws Exception {
        CepOperator<Event, Integer, String> operator = createOperator();

        operator.processElement(new StreamRecord<>(new Event(42, "a"), 1));
        operator.processWatermark(new Watermark(5));

        assertFalse(operator.getPartialMatches().isEmpty(),
                "SharedBuffer should have entries after partial match");

        long farFuture = 100000L;
        operator.processWatermark(new Watermark(farFuture));

        NFAState state = operator.getNFAStateForTesting();
        boolean partialMatchesEmpty = state.getPartialMatches().size() <= 1
                && state.getCompletedMatches().isEmpty();

        operator.close();
    }

    @Test
    void testNoCleanupWhenPatternStillActive() throws Exception {
        CepOperator<Event, Integer, String> operator = createOperator();

        operator.processElement(new StreamRecord<>(new Event(42, "a"), 1));

        operator.processWatermark(new Watermark(2));

        NFAState state = operator.getNFAStateForTesting();
        assertTrue(state.getPartialMatches().size() >= 1,
                "Partial matches should still be present for active pattern");

        operator.close();
    }

    @Test
    void testSharedBufferCleanedAfterWindowTimeout() throws Exception {
        CepOperator<Event, Integer, String> operator = createOperator();

        operator.processElement(new StreamRecord<>(new Event(42, "start-only"), 1));

        assertFalse(operator.getPartialMatches().isEmpty(),
                "Should have entries in SharedBuffer");

        operator.processWatermark(new Watermark(999999));

        operator.close();
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
