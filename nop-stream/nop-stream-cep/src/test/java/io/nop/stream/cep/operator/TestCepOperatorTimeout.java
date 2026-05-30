package io.nop.stream.cep.operator;

import io.nop.stream.cep.CepTestUtils;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.functions.TimedOutPartialMatchHandler;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for CepOperator timeout detection:
 * timeout triggered when pattern window expires, and no timeout when pattern completes.
 */
public class TestCepOperatorTimeout {

    private TestOutput<String> matchOutput;
    private List<String> timeoutResults;
    private NFACompiler.NFAFactory<Event> nfaFactory;

    @BeforeEach
    void setUp() {
        matchOutput = new TestOutput<>();
        timeoutResults = new ArrayList<>();

        // Pattern: start(id>=42) -> end(name="end") within 10ms
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")))
                .within(Duration.ofMillis(10));

        // Compile with timeoutHandling=true since we use TimedOutPartialMatchHandler
        nfaFactory = NFACompiler.compileFactory(pattern, true);
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
        CombinedFunction combined = new CombinedFunction(timeoutResults);

        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                new EventTypeSerializer(),
                false,
                nfaFactory,
                null,
                null,
                combined,
                null
        );

        operator.setOutput(matchOutput);
        setProcessingTimeService(operator, MOCK_PTS);
        operator.open();
        return operator;
    }

    private static void setProcessingTimeService(CepOperator<?, ?, ?> op, ProcessingTimeService svc) {
        CepTestUtils.injectProcessingTimeService(op, svc);
    }

    @Test
    void testTimeoutWithProcessingTime() throws Exception {
        CepOperator<Event, Integer, String> operator = createOperator();

        // Send start event at timestamp 1 (id=42 satisfies >=42)
        operator.processElement(new StreamRecord<>(new Event(42, "a"), 1));

        // No end event yet — advance watermark beyond the window (1 + 10 = 11)
        // This should trigger a timeout for the partial match
        operator.processWatermark(new Watermark(20));

        assertFalse(timeoutResults.isEmpty(),
                "Should produce a timeout result when pattern window expires, got: "
                        + timeoutResults);
        assertEquals("timeout:a", timeoutResults.get(0),
                "Timeout result should be 'timeout:a'");

        assertTrue(matchOutput.isEmpty(),
                "Should not produce any complete match, got: " + matchOutput.getElements());

        operator.close();
    }

    @Test
    void testNoTimeoutWhenPatternCompletes() throws Exception {
        CepOperator<Event, Integer, String> operator = createOperator();

        // Send start event at timestamp 1
        operator.processElement(new StreamRecord<>(new Event(42, "start"), 1));
        // Send end event at timestamp 3 (within the 10ms window)
        // Use id=1 so the end event does NOT satisfy start condition (id>=42),
        // preventing a new partial match that would later time out.
        operator.processElement(new StreamRecord<>(new Event(1, "end"), 3));
        operator.processWatermark(new Watermark(5));

        // Pattern should complete — no timeout
        assertFalse(matchOutput.isEmpty(),
                "Should produce a match when pattern completes, got: " + matchOutput.getElements());
        assertEquals("match:start", matchOutput.get(0),
                "Match result should be 'match:start'");

        // Advance watermark beyond the window to verify no timeout fires
        operator.processWatermark(new Watermark(20));

        assertTrue(timeoutResults.isEmpty(),
                "Should not produce timeout when pattern already completed, got: "
                        + timeoutResults);

        operator.close();
    }

    /**
     * Function that implements both PatternProcessFunction and TimedOutPartialMatchHandler.
     */
    private static class CombinedFunction
            extends PatternProcessFunction<Event, String>
            implements TimedOutPartialMatchHandler<Event> {

        private final List<String> timeoutResults;

        CombinedFunction(List<String> timeoutResults) {
            this.timeoutResults = timeoutResults;
        }

        @Override
        public void processMatch(Map<String, List<Event>> match, Context ctx, Collector<String> out) {
            Event start = match.get("start").get(0);
            out.collect("match:" + start.getName());
        }

        @Override
        public void processTimedOutMatch(Map<String, List<Event>> match, Context ctx) {
            Event start = match.get("start").get(0);
            timeoutResults.add("timeout:" + start.getName());
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
