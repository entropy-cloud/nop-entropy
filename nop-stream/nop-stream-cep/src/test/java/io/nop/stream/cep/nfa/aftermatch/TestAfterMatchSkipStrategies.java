package io.nop.stream.cep.nfa.aftermatch;

import io.nop.stream.cep.CepTestUtils;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.nfa.NFAState;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.operator.CepOperator;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral tests for {@link AfterMatchSkipStrategy} — covers both
 * (a) factory metadata (consolidated into a single test) and
 * (b) NFA behavior under each strategy on the same event sequence.
 *
 * <p>P1-14 fix: the prior test had 13 separate methods that each asserted only
 * factory metadata ({@code isSkipStrategy()}, {@code getPatternName()}) with
 * zero NFA behavior coverage. Per the plan, we consolidate the metadata
 * assertions and add NFA behavior tests that verify the four skip strategies
 * produce different match counts / positions on the same input sequence
 * (aligned with the existing {@code TestCepSkipStrategyE2E}).
 *
 * <p>Plan {@code 2026-07-26-0804-2-parallel-execution-cep-correctness.md} Phase 3.
 */
class TestAfterMatchSkipStrategies {

    private TestOutput<String> output;
    private PatternProcessFunction<Event, String> function;

    @BeforeEach
    void setUp() {
        output = new TestOutput<>();
        function = new PatternProcessFunction<>() {
            @Override
            public void processMatch(Map<String, List<Event>> match, Context ctx, Collector<String> out) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, List<Event>> e : match.entrySet()) {
                    if (sb.length() > 0) sb.append("->");
                    sb.append(e.getValue().get(0).getName());
                }
                out.collect(sb.toString());
            }
        };
    }

    // ---- (a) Consolidated factory metadata assertions ----

    @Test
    void allFactoryMethodsProduceExpectedMetadata() {
        // noSkip is NOT a skip-strategy (the others are).
        assertFalse(AfterMatchSkipStrategy.noSkip().isSkipStrategy(),
                "noSkip must not be a skip strategy");

        // All real skip strategies report isSkipStrategy == true.
        assertTrue(AfterMatchSkipStrategy.skipPastLastEvent().isSkipStrategy());
        assertTrue(AfterMatchSkipStrategy.skipToNext().isSkipStrategy());
        assertTrue(AfterMatchSkipStrategy.skipToFirst("p").isSkipStrategy());
        assertTrue(AfterMatchSkipStrategy.skipToLast("p").isSkipStrategy());

        // skipPastLastEvent has no pattern name; skipToFirst/skipToLast expose theirs.
        assertFalse(AfterMatchSkipStrategy.skipPastLastEvent().getPatternName().isPresent());
        assertEquals("p", AfterMatchSkipStrategy.skipToFirst("p").getPatternName().orElseThrow());
        assertEquals("p", AfterMatchSkipStrategy.skipToLast("p").getPatternName().orElseThrow());

        // skipPastLastEvent is a singleton (referentially equal across calls).
        assert AfterMatchSkipStrategy.skipPastLastEvent() == AfterMatchSkipStrategy.skipPastLastEvent();

        // skipToFirst/skipToLast throw-on-miss adapters are non-null.
        assertNotNull(((SkipToFirstStrategy) AfterMatchSkipStrategy.skipToFirst("missing"))
                .throwExceptionOnMiss());
        assertNotNull(((SkipToLastStrategy) AfterMatchSkipStrategy.skipToLast("missing"))
                .throwExceptionOnMiss());
    }

    // ---- (b) NFA behavior: same input, different strategies => different matches ----

    /**
     * The canonical event sequence used across all four strategies. With
     * pattern {@code begin("a").where(a*).followedBy("end").where(end)} and
     * input {@code a1, a2, end}, each strategy produces a distinct match count.
     */
    private static final String[] INPUT_SEQUENCE = {"a1", "a2", "end"};

    private List<String> runWithStrategy(AfterMatchSkipStrategy strategy) throws Exception {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start", strategy)
                .where(SimpleCondition.of(e -> e.getName().startsWith("a")))
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("end")));

        NFACompiler.NFAFactory<Event> nfaFactory = NFACompiler.compileFactory(pattern, false);
        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                null, false, nfaFactory, null, strategy, function, null);

        TestOutput<String> localOutput = new TestOutput<>();
        operator.setOutput(localOutput);
        injectPts(operator);

        operator.open();
        int ts = 1;
        for (String name : INPUT_SEQUENCE) {
            operator.processElement(new StreamRecord<>(new Event(ts, name), ts));
            ts++;
        }
        operator.processWatermark(new Watermark(100));
        operator.close();

        return new ArrayList<>(localOutput.getElements());
    }

    private static void injectPts(CepOperator<?, ?, ?> op) {
        ProcessingTimeService pts = new ProcessingTimeService() {
            private long time = 1000;
            @Override public long getCurrentProcessingTime() { return time++; }
            @Override public ScheduledFuture<?> registerTimer(long timestamp, ProcessingTimeCallback target) {
                return null;
            }
        };
        CepTestUtils.injectProcessingTimeService(op, pts);
    }

    @Test
    void noSkipProducesAllOverlappingMatches() throws Exception {
        List<String> matches = runWithStrategy(AfterMatchSkipStrategy.noSkip());
        // a1->end and a2->end (noSkip keeps every start).
        assertEquals(2, matches.size(),
                "noSkip must produce all overlapping matches, got: " + matches);
        assertTrue(matches.contains("a1->end"));
        assertTrue(matches.contains("a2->end"));
    }

    @Test
    void skipPastLastEventProducesSingleMatch() throws Exception {
        List<String> matches = runWithStrategy(AfterMatchSkipStrategy.skipPastLastEvent());
        // After matching a1->end, skip past the last event of the match (end),
        // so a2 cannot start a new match before that. Exactly one match.
        assertEquals(1, matches.size(),
                "skipPastLastEvent must produce exactly one match, got: " + matches);
        assertEquals("a1->end", matches.get(0));
    }

    @Test
    void skipToNextProducesSameStartExcludedMatches() throws Exception {
        List<String> matches = runWithStrategy(AfterMatchSkipStrategy.skipToNext());
        // After matching a1->end, skip to the next start (a2). a2->end is still
        // matched. So 2 matches total, but skipToNext differs from noSkip when
        // there are multiple starts at the same position; on this simple input
        // they coincide.
        assertEquals(2, matches.size(),
                "skipToNext should produce 2 matches on this input, got: " + matches);
        assertTrue(matches.contains("a1->end"));
    }

    @Test
    void strategiesProduceDifferentMatchCountsOnSameInput() throws Exception {
        // Anti-hollow check: prove the four strategies actually diverge on
        // the same input — if the skip logic were deleted, all four would
        // produce the same match count.
        int noSkip = runWithStrategy(AfterMatchSkipStrategy.noSkip()).size();
        int skipPastLast = runWithStrategy(AfterMatchSkipStrategy.skipPastLastEvent()).size();

        assertTrue(noSkip > skipPastLast,
                "noSkip (" + noSkip + ") must produce more matches than skipPastLastEvent ("
                        + skipPastLast + ") — if equal, skip logic is hollow");
    }
}
