/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.cep.operator;

import io.nop.stream.cep.CepTestUtils;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.functions.TimedOutPartialMatchHandler;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression proofs for the ledger-driven timer drain (plan 2279 Q1):
 *
 * <ul>
 *   <li>Per-key reconciliation: queue buckets restored from a state whose timer
 *       ledger lost entries (legacy/partial restore shape) must still drain — the
 *       first drain backfills the ledger from the observed buckets instead of
 *       silently skipping them.</li>
 *   <li>Window-timer skip: due ledger timestamps without a queued bucket (window
 *       timers registered by processEvent) are skipped by the drain without
 *       errors, while their timeout semantics still fire through advanceTime —
 *       matching the old bucket-driven behavior where such timestamps were never
 *       visited.</li>
 * </ul>
 */
class TestCepOperatorLedgerDrain {

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

    private static class CombinedFunction
            extends PatternProcessFunction<Event, String>
            implements TimedOutPartialMatchHandler<Event> {

        final List<String> results = new ArrayList<>();

        @Override
        public void processMatch(Map<String, List<Event>> match, Context ctx, Collector<String> out) {
            results.add("match:" + match.get("start").get(0).getName());
        }

        @Override
        public void processTimedOutMatch(Map<String, List<Event>> match, Context ctx) {
            results.add("timeout:" + match.get("start").get(0).getName());
        }
    }

    private static Pattern<Event, ?> windowedPattern() {
        // start(id>=42) -> end(name="end") within 10ms: the within clause makes
        // processEvent register a window timer whose timestamp has NO queue bucket.
        return Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")))
                .within(Duration.ofMillis(10));
    }

    private CepOperator<Event, Integer, String> newEventTimeOperator(
            Pattern<Event, ?> pattern, PatternProcessFunction<Event, String> function,
            TestOutput<String> output) throws Exception {
        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                new TestCepOperatorMultiKeyWatermarkSerializer(),
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

    /** Removes one queued-bucket timestamp from the current key's timer ledger,
     *  simulating a restore whose ledger is partial (key present, bucket-timer
     *  entry missing). */
    private static void dropBucketTimerFromLedger(CepOperator<?, ?, ?> operator,
                                                  long bucketTimestamp) throws Exception {
        Field ledgerField = CepOperator.class
                .getDeclaredField("registeredEventTimeTimersByKey");
        ledgerField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Object, java.util.TreeSet<Long>> ledger =
                (Map<Object, java.util.TreeSet<Long>>) ledgerField.get(operator);
        java.util.TreeSet<Long> timers = ledger.get(operator.getCurrentKey());
        timers.remove(bucketTimestamp);
    }

    @Test
    void reconciledLedgerStillDrainsBucketsWithoutLedgerEntries() throws Exception {
        CombinedFunction function = new CombinedFunction();
        TestOutput<String> output = new TestOutput<>();
        CepOperator<Event, Integer, String> op =
                newEventTimeOperator(windowedPattern(), function, output);
        try {
            op.setCurrentKey(1);
            op.processElement(new StreamRecord<>(new Event(42, "a1"), 1));
            op.processElement(new StreamRecord<>(new Event(42, "a2"), 5));

            // Simulate the partial-ledger restore shape: the key keeps one timer
            // entry (so it is still scheduled for the drain) but the ts=1 bucket's
            // ledger entry is gone.
            dropBucketTimerFromLedger(op, 1L);
            assertEquals(2, op.getPQSize(1), "two events must be buffered before the watermark");

            op.processWatermark(new Watermark(20));

            assertEquals(0, op.getPQSize(1),
                    "the first drain must reconcile the ledger from the observed buckets "
                            + "and drain the bucket even though its ledger entry was missing "
                            + "(a non-reconciling ledger-driven drain would silently skip it)");
            assertTrue(function.results.contains("timeout:a1") && function.results.contains("timeout:a2"),
                    "both buffered events must reach the NFA (their 10ms window expires at the "
                            + "watermark with no end event, so a timeout is the correct outcome — "
                            + "what matters is that neither bucket was skipped), got: "
                            + function.results);
        } finally {
            op.close();
        }
    }

    @Test
    void windowTimerTimestampsWithoutBucketAreSkippedWithoutError() throws Exception {
        CombinedFunction function = new CombinedFunction();
        TestOutput<String> output = new TestOutput<>();
        CepOperator<Event, Integer, String> op =
                newEventTimeOperator(windowedPattern(), function, output);
        try {
            // A partial match whose 10ms window expires at ts 11: the window timer
            // (ts 11) sits in the ledger without a queue bucket.
            op.setCurrentKey(1);
            op.processElement(new StreamRecord<>(new Event(42, "a1"), 1));

            op.processWatermark(new Watermark(20));

            assertTrue(function.results.contains("timeout:a1"),
                    "the window timeout must still fire through advanceTime, got: "
                            + function.results);
            assertEquals(0, op.getPQSize(1), "no bucket may linger after the drain");
        } finally {
            op.close();
        }
    }

    @Test
    void plainPatternStillMatchesAfterLedgerDrivenDrain() throws Exception {
        Pattern<Event, ?> plain = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")));
        CombinedFunction function = new CombinedFunction();
        TestOutput<String> output = new TestOutput<>();
        CepOperator<Event, Integer, String> op =
                newEventTimeOperator(plain, function, output);
        try {
            op.setCurrentKey(7);
            op.processElement(new StreamRecord<>(new Event(42, "s"), 1));
            op.processElement(new StreamRecord<>(new Event(7, "end"), 2));

            op.processWatermark(new Watermark(5));

            assertTrue(function.results.contains("match:s"),
                    "complete matches must still fire on the watermark, got: "
                            + function.results);
            assertEquals(0, op.getPQSize(7));
        } finally {
            op.close();
        }
    }

    /** Minimal event serializer (same shape as the multi-key watermark test). */
    private static class TestCepOperatorMultiKeyWatermarkSerializer
            implements io.nop.stream.core.common.typeutils.TypeSerializer<Event> {
        @Override
        public boolean isImmutableType() {
            return false;
        }

        @Override
        public io.nop.stream.core.common.typeutils.TypeSerializer<Event> duplicate() {
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
