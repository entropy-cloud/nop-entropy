package io.nop.stream.cep.operator;

import io.nop.stream.cep.CepTestUtils;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.EventComparator;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.functions.TimedOutPartialMatchHandler;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 (stream-cep audit): watermark / timer callbacks used to run in whatever key context
 * happened to be current (the last processed element's key). On a keyed (multi-key) input
 * in event-time mode, buffered events of every other key were never drained when the
 * watermark passed their timestamps, their partial matches never timed out, and the
 * SharedBuffer leaked. These tests pin the corrected per-key semantics: the watermark
 * drain and timer callbacks must first switch to the owning key's context
 * (mirrors {@code KeyExtractingOutput} in production, which sets the key before
 * {@code processElement} but not before {@code processWatermark}).
 */
public class TestCepOperatorMultiKeyWatermark {

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

    /**
     * Function collecting complete matches to the main output and timeouts to a side list.
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
            Event end = match.get("end").get(0);
            out.collect(start.getName() + "->" + end.getName());
        }

        @Override
        public void processTimedOutMatch(Map<String, List<Event>> match, Context ctx) {
            Event start = match.get("start").get(0);
            timeoutResults.add("timeout:" + start.getName());
        }
    }

    private static Pattern<Event, ?> timeoutPattern() {
        // start(id>=42) -> end(name="end") within 10ms
        return Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")))
                .within(Duration.ofMillis(10));
    }

    private static Pattern<Event, ?> plainPattern() {
        // start(id>=42) -> end(name="end"), no window
        return Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")));
    }

    private CepOperator<Event, Integer, String> createEventTimeOperator(
            Pattern<Event, ?> pattern, PatternProcessFunction<Event, String> function,
            TestOutput<String> output) throws Exception {
        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                new EventTypeSerializer(),
                false,
                NFACompiler.compileFactory(pattern, function instanceof TimedOutPartialMatchHandler),
                null,
                null,
                function,
                null
        );
        operator.setStateBackend(new MemoryStateBackend());
        operator.setOutput(output);
        CepTestUtils.injectProcessingTimeService(operator, MOCK_PTS);
        operator.open();
        return operator;
    }

    /**
     * Multi-key event-time drain: a watermark arriving while key 2 is the current key
     * context must still drain key 1's buffered events and fire key 1's window timeout.
     */
    @Test
    void multiKeyEventTimeDrainAndTimeoutPerKey() throws Exception {
        TestOutput<String> matchOutput = new TestOutput<>();
        List<String> timeoutResults = new ArrayList<>();
        CepOperator<Event, Integer, String> op =
                createEventTimeOperator(timeoutPattern(), new CombinedFunction(timeoutResults), matchOutput);
        try {
            // key 1: a partial match whose 10ms window will expire at the watermark
            op.setCurrentKey(1);
            op.processElement(new StreamRecord<>(new Event(42, "a1"), 1));

            // key 2: a complete match
            op.setCurrentKey(2);
            op.processElement(new StreamRecord<>(new Event(42, "a2"), 2));
            op.processElement(new StreamRecord<>(new Event(1, "end"), 3));

            // Watermark arrives with key 2 as the last-set key context (production wiring:
            // KeyExtractingOutput sets the key only for elements, not for watermarks).
            op.processWatermark(new Watermark(20));

            assertTrue(matchOutput.getElements().contains("a2->end"),
                    "current key's match must fire, got: " + matchOutput.getElements());
            assertTrue(timeoutResults.contains("timeout:a1"),
                    "key 1's partial match must time out at the watermark even though key 2 "
                            + "was the current key context, got: " + timeoutResults);
            assertEquals(0, op.getPQSize(1),
                    "key 1's buffered events must be drained by the watermark");
            assertEquals(0, op.getPQSize(2),
                    "key 2's buffered events must be drained by the watermark");
            assertFalse(op.hasNonEmptySharedBuffer(1),
                    "key 1's SharedBuffer entries must be released after the timeout");
        } finally {
            op.close();
        }
    }

    /**
     * Multi-key event-time drain without timeouts: every key's complete match must fire.
     */
    @Test
    void multiKeyEventTimeMatchesFireForAllKeys() throws Exception {
        TestOutput<String> matchOutput = new TestOutput<>();
        List<String> timeoutResults = new ArrayList<>();
        CepOperator<Event, Integer, String> op =
                createEventTimeOperator(plainPattern(), new CombinedFunction(timeoutResults), matchOutput);
        try {
            // key 1 complete sequence
            op.setCurrentKey(1);
            op.processElement(new StreamRecord<>(new Event(42, "a"), 1));
            op.processElement(new StreamRecord<>(new Event(1, "end"), 2));

            // key 2 complete sequence (key 2 is the current key context at the watermark)
            op.setCurrentKey(2);
            op.processElement(new StreamRecord<>(new Event(42, "b"), 3));
            op.processElement(new StreamRecord<>(new Event(1, "end"), 4));

            op.processWatermark(new Watermark(10));

            assertTrue(matchOutput.getElements().contains("a->end"),
                    "key 1's match must fire although key 2 was processed last, got: "
                            + matchOutput.getElements());
            assertTrue(matchOutput.getElements().contains("b->end"),
                    "key 2's match must fire, got: " + matchOutput.getElements());
            assertEquals(0, op.getPQSize(1), "key 1's queue must be drained");
            assertEquals(0, op.getPQSize(2), "key 2's queue must be drained");
            assertTrue(op.getRegisteredEventTimeTimersForTesting().isEmpty(),
                    "consumed timers must not linger in the registry (bounded bookkeeping)");
        } finally {
            op.close();
        }
    }

    /**
     * Processing-time mode (comparator buffering): the processing-time timer callback must
     * restore the key context captured at registration time before draining the queue.
     */
    @Test
    void processingTimeCallbackRestoresKeyContext() throws Exception {
        RecordingPTS pts = new RecordingPTS();
        TestOutput<String> matchOutput = new TestOutput<>();

        CepOperator<Event, Integer, String> op = new CepOperator<>(
                new EventTypeSerializer(),
                true,
                NFACompiler.compileFactory(plainPattern(), false),
                (EventComparator<Event>) (e1, e2) -> Objects.compare(
                        e1.getName(), e2.getName(), String::compareTo),
                null,
                new PatternProcessFunction<>() {
                    @Override
                    public void processMatch(Map<String, List<Event>> match, Context ctx,
                                             Collector<String> out) {
                        Event start = match.get("start").get(0);
                        Event end = match.get("end").get(0);
                        out.collect(start.getName() + "->" + end.getName());
                    }
                },
                null
        );
        op.setStateBackend(new MemoryStateBackend());
        op.setOutput(matchOutput);
        CepTestUtils.injectProcessingTimeService(op, pts);
        op.open();
        try {
            // key 1 sequence buffered at processing time T1
            op.setCurrentKey(1);
            op.processElement(new StreamRecord<>(new Event(42, "a")));
            op.processElement(new StreamRecord<>(new Event(1, "end")));

            // key 2 sequence buffered at T2 (key 2 stays the current key context)
            op.setCurrentKey(2);
            op.processElement(new StreamRecord<>(new Event(42, "b")));
            op.processElement(new StreamRecord<>(new Event(1, "end")));

            // Fire the recorded processing-time callbacks with no key context switch of
            // our own: the callback itself must restore the owning key's context.
            pts.fireDue(2000);

            assertTrue(matchOutput.getElements().contains("a->end"),
                    "key 1's match must fire from its own timer callback, got: "
                            + matchOutput.getElements());
            assertTrue(matchOutput.getElements().contains("b->end"),
                    "key 2's match must fire from its own timer callback, got: "
                            + matchOutput.getElements());
            assertEquals(0, op.getPQSize(1), "key 1's queue must be drained by its callback");
            assertEquals(0, op.getPQSize(2), "key 2's queue must be drained by its callback");
        } finally {
            op.close();
        }
    }

    /**
     * Processing-time service mock that records registrations so the test can fire them
     * deterministically. {@link #fireDue(long)} fires only callbacks with timestamp at or
     * before the horizon so the (far-future, periodically re-arming) cache-statistics
     * timer is not disturbed.
     */
    private static final class RecordingPTS implements ProcessingTimeService {
        private long time = 1000;
        private final List<Long> timestamps = new ArrayList<>();
        private final List<ProcessingTimeCallback> callbacks = new ArrayList<>();

        @Override
        public long getCurrentProcessingTime() {
            return time;
        }

        @Override
        public ScheduledFuture<?> registerTimer(long timestamp, ProcessingTimeCallback target) {
            timestamps.add(timestamp);
            callbacks.add(target);
            return null;
        }

        void fireDue(long horizon) throws Exception {
            for (int i = 0; i < callbacks.size(); i++) {
                if (timestamps.get(i) <= horizon) {
                    callbacks.get(i).onProcessingTime(timestamps.get(i));
                }
            }
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
