package io.nop.stream.cep.operator;

import io.nop.stream.cep.CepTestUtils;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.EventComparator;
import io.nop.stream.cep.functions.TimedOutPartialMatchHandler;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.streamrecord.StreamRecord;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5-CEP-01 regression (plan 368 Phase 5): processing-time + comparator mode
 * buffers events into per-timestamp buckets ({@code bufferEvent}) and drains
 * them from the per-key timer ledger ({@code drainDueBuckets}). The
 * processing-time branch of {@code registerTimer} used to register ONLY the
 * wall-clock timer without writing a ledger entry, and the per-key ledger
 * reconciliation runs exactly once per key — so after a key's first timer
 * round, every newly buffered bucket was permanently invisible to the drain:
 * matches were silently lost and {@code elementQueueState} grew without bound.
 *
 * <p>These tests pin the corrected behavior: every timer round must drain the
 * buckets buffered since the previous round (the ledger now mirrors every
 * processing-time registration, and consumed entries are cleaned up).
 */
public class TestCepOperatorProcessingTimeBucketDrain {

    /**
     * Processing-time service mock with an adjustable clock so the test can
     * buffer the second round of events at a strictly later processing time,
     * and fire registered timers deterministically.
     */
    private static final class AdjustablePTS implements ProcessingTimeService {
        private long time = 1000;
        private final List<Long> timestamps = new ArrayList<>();
        private final List<ProcessingTimeCallback> callbacks = new ArrayList<>();

        void setTime(long time) {
            this.time = time;
        }

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
            // One-shot index loop: each registered timer fires AT MOST once (fired
            // entries are null-marked so a later fireDue call cannot re-fire them),
            // and timers registered while firing (e.g. window timers registered
            // during a bucket drain) are reached by the continuing loop.
            int i = 0;
            while (i < callbacks.size()) {
                Long ts = timestamps.get(i);
                if (ts != null && ts <= horizon) {
                    timestamps.set(i, null);
                    callbacks.get(i).onProcessingTime(ts);
                }
                i++;
            }
        }
    }

    private static class CombinedFunction
            extends io.nop.stream.cep.functions.PatternProcessFunction<Event, String>
            implements TimedOutPartialMatchHandler<Event> {

        private final List<String> timeoutResults;

        CombinedFunction(List<String> timeoutResults) {
            this.timeoutResults = timeoutResults;
        }

        @Override
        public void processMatch(Map<String, List<Event>> match, Context ctx, Collector<String> out) {
            out.collect(match.get("start").get(0).getName() + "->" + match.get("end").get(0).getName());
        }

        @Override
        public void processTimedOutMatch(Map<String, List<Event>> match, Context ctx) {
            timeoutResults.add("timeout:" + match.get("start").get(0).getName());
        }
    }

    private static final EventComparator<Event> NAME_COMPARATOR = (e1, e2) -> Objects.compare(
            e1.getName(), e2.getName(), String::compareTo);

    private static CepOperator<Event, Integer, String> createProcessingTimeOperator(
            Pattern<Event, ?> pattern, io.nop.stream.cep.functions.PatternProcessFunction<Event, String> function,
            AdjustablePTS pts, TestOutput<String> output) throws Exception {
        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                new EventTypeSerializer(),
                true,
                NFACompiler.compileFactory(pattern, function instanceof TimedOutPartialMatchHandler),
                NAME_COMPARATOR,
                null,
                function,
                null
        );
        operator.setStateBackend(new MemoryStateBackend());
        operator.setOutput(output);
        CepTestUtils.injectProcessingTimeService(operator, pts);
        operator.open();
        return operator;
    }

    private static Pattern<Event, ?> plainPattern() {
        // start(id>=42) -> end(name="end"), no window
        return Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")));
    }

    private static Pattern<Event, ?> windowPattern() {
        // start(id>=42) -> end(name="end") within 10ms
        return Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")))
                .within(Duration.ofMillis(10));
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

    /**
     * Core R5-CEP-01 scenario: TWO timer rounds for the same key. The second
     * round buffers new events at a later processing time and must still be
     * drained and matched. Pre-fix, the second round was silently lost (the
     * drain read a stale, never-updated ledger).
     */
    @Test
    void processingTimeSecondTimerRound_drainsNewlyBufferedEvents() throws Exception {
        AdjustablePTS pts = new AdjustablePTS();
        TestOutput<String> matchOutput = new TestOutput<>();

        CepOperator<Event, Integer, String> op = createProcessingTimeOperator(
                plainPattern(),
                new CombinedFunction(new ArrayList<>()),
                pts,
                matchOutput);
        try {
            // Round 1: buffer a complete sequence at processing time T=1000.
            op.setCurrentKey(1);
            op.processElement(new StreamRecord<>(new Event(42, "a1")));
            op.processElement(new StreamRecord<>(new Event(1, "end")));
            pts.fireDue(2000);

            assertTrue(matchOutput.getElements().contains("a1->end"),
                    "round-1 match must fire, got: " + matchOutput.getElements());
            assertEquals(0, op.getPQSize(1), "round-1 bucket must be drained");

            // Round 2: a LATER processing time, new events for the same key.
            // Pre-fix these were never drained (the drain iterated the stale
            // ledger entry of round 1 and found no bucket behind it).
            pts.setTime(5000);
            op.processElement(new StreamRecord<>(new Event(42, "a2")));
            op.processElement(new StreamRecord<>(new Event(1, "end")));
            pts.fireDue(6000);

            assertTrue(matchOutput.getElements().contains("a2->end"),
                    "round-2 events buffered after the first timer round must still be "
                            + "drained and matched (R5-CEP-01), got: " + matchOutput.getElements());
            assertEquals(0, op.getPQSize(1),
                    "round-2 bucket must be drained; a non-empty queue here means the "
                            + "bucket is permanently stranded (silent match loss + unbounded state)");

            // Round 3 pins the invariant on an open-ended stream: the fix must
            // not be a one-shot backfill but a per-registration ledger write.
            pts.setTime(9000);
            op.processElement(new StreamRecord<>(new Event(42, "a3")));
            op.processElement(new StreamRecord<>(new Event(1, "end")));
            pts.fireDue(10000);

            assertTrue(matchOutput.getElements().contains("a3->end"),
                    "round-3 events must still be drained, got: " + matchOutput.getElements());
            assertEquals(0, op.getPQSize(1), "round-3 bucket must be drained");
            assertTrue(op.getLedgerForTesting().isEmpty(),
                    "consumed ledger entries must be cleaned up (bounded bookkeeping), got: "
                            + op.getLedgerForTesting());
        } finally {
            op.close();
        }
    }

    /**
     * Processing-time + window pattern: the ledger also carries the window
     * timeout registrations (which have no queue bucket). Consumed window
     * entries must be cleaned up so later rounds keep draining and the
     * timeouts keep firing.
     */
    @Test
    void processingTimeWindowTimerRound_timeoutsFireAndLaterBucketsStillDrain() throws Exception {
        AdjustablePTS pts = new AdjustablePTS();
        TestOutput<String> matchOutput = new TestOutput<>();
        List<String> timeoutResults = new ArrayList<>();

        CepOperator<Event, Integer, String> op = createProcessingTimeOperator(
                windowPattern(),
                new CombinedFunction(timeoutResults),
                pts,
                matchOutput);
        try {
            // Round 1: a partial match (start only) whose 10ms window expires.
            op.setCurrentKey(1);
            op.processElement(new StreamRecord<>(new Event(42, "w1")));
            // Fire well past the window: bucket drain + window timeout + ledger cleanup.
            pts.setTime(2000);
            pts.fireDue(3000);

            assertTrue(timeoutResults.contains("timeout:w1"),
                    "window timeout must fire in processing-time mode, got: " + timeoutResults);

            // Round 2: a complete match buffered after the timeout round. The start
            // event is named "a2" so the name-comparator sorts it before "end"
            // (within one bucket the comparator orders the buffered events).
            pts.setTime(5000);
            op.processElement(new StreamRecord<>(new Event(42, "a2")));
            op.processElement(new StreamRecord<>(new Event(1, "end")));
            pts.fireDue(6000);

            assertTrue(matchOutput.getElements().contains("a2->end"),
                    "round-2 complete match must fire after a window-timeout round, got: "
                            + matchOutput.getElements());
            assertEquals(0, op.getPQSize(1), "round-2 bucket must be drained");
            assertTrue(op.getLedgerForTesting().isEmpty(),
                    "consumed ledger entries (bucket + window timers) must be cleaned up, got: "
                            + op.getLedgerForTesting());
        } finally {
            op.close();
        }
    }
}
