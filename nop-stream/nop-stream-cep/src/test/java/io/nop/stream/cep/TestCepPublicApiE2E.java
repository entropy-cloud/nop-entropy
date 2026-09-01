package io.nop.stream.cep;

import io.nop.stream.cep.CepTestUtils;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.operator.CepOperator;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.datastream.DataStream;
import io.nop.stream.core.datastream.KeyedStream;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for the CEP public API entry points.
 * Verifies that CEP.pattern() -> PatternStream.process() path works
 * without throwing exceptions (previously broken by null inputSerializer).
 */
public class TestCepPublicApiE2E {

    private TestOutput<String> output;

    @BeforeEach
    void setUp() {
        output = new TestOutput<>();
    }

    /**
     * Verifies that CEP.pattern() does not throw when creating a PatternStream
     * from a non-keyed DataStream.
     */
    @Test
    void testCepPatternCreationFromDataStream() {
        StreamExecutionEnvironment env = new StreamExecutionEnvironment();
        DataStream<Event> stream = env.fromElements(
                new Event(1, "a"),
                new Event(2, "b")
        );

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("b")));

        PatternStream<Event> patternStream = CEP.pattern(stream, pattern);
        assertNotNull(patternStream);
    }

    /**
     * Verifies that CEP.pattern() works with a KeyedStream.
     */
    @Test
    void testCepPatternCreationFromKeyedStream() {
        StreamExecutionEnvironment env = new StreamExecutionEnvironment();
        KeyedStream<Event, Integer> stream = env.fromElements(
                new Event(1, "a"),
                new Event(2, "b")
        ).keyBy(Event::getId);

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("b")));

        PatternStream<Event> patternStream = CEP.pattern(stream, pattern);
        assertNotNull(patternStream);
    }

    /**
     * Verifies that CepOperator can be created with null inputSerializer
     * (the path taken by PatternStreamBuilder.build()).
     */
    @Test
    void testCepOperatorWithNullSerializerDoesNotThrow() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("b")));

        NFACompiler.NFAFactory<Event> nfaFactory = NFACompiler.compileFactory(pattern, false);

        PatternProcessFunction<Event, String> function = new PatternProcessFunction<>() {
            @Override
            public void processMatch(Map<String, List<Event>> match, Context ctx, Collector<String> out) {
                Event start = match.get("start").get(0);
                Event end = match.get("end").get(0);
                out.collect(start.getName() + "->" + end.getName());
            }
        };

        assertDoesNotThrow(() -> {
            CepOperator<Event, Integer, String> operator = new CepOperator<>(
                    null,
                    false,
                    nfaFactory,
                    null,
                    null,
                    function,
                    null
            );
        });
    }

    /**
     * Verifies that CepOperator created with null serializer can process elements
     * and produce correct pattern match results end-to-end.
     */
    @Test
    void testCepOperatorWithNullSerializerProcessesElements() throws Exception {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("b")));

        NFACompiler.NFAFactory<Event> nfaFactory = NFACompiler.compileFactory(pattern, false);

        PatternProcessFunction<Event, String> function = new PatternProcessFunction<>() {
            @Override
            public void processMatch(Map<String, List<Event>> match, Context ctx, Collector<String> out) {
                Event start = match.get("start").get(0);
                Event end = match.get("end").get(0);
                out.collect(start.getName() + "->" + end.getName());
            }
        };

        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                null,
                false,
                nfaFactory,
                null,
                null,
                function,
                null
        );

        operator.setOutput(output);
        setProcessingTimeService(operator);
        operator.open();

        operator.processElement(new StreamRecord<>(new Event(1, "a"), 1));
        operator.processElement(new StreamRecord<>(new Event(2, "b"), 2));
        operator.processWatermark(new Watermark(10));

        assertFalse(output.isEmpty(), "Should have matched the a->b pattern");
        assertEquals("a->b", output.get(0));
    }

    private static void setProcessingTimeService(CepOperator<?, ?, ?> op) throws Exception {
        ProcessingTimeService pts = new ProcessingTimeService() {
            private long time = 1000;
            @Override
            public long getCurrentProcessingTime() { return time++; }
            @Override
            public ScheduledFuture<?> registerTimer(long timestamp, ProcessingTimeCallback target) {
                return null;
            }
        };
        CepTestUtils.injectProcessingTimeService(op, pts);
    }

    /**
     * E2E (audit fix, releaseNode repeat-visit semantics): a {@code followedByAny} branching
     * pattern produces diamond topologies in the SharedBuffer — a shared predecessor is locked
     * once per branch and must be released once per branch. After all matches complete and a
     * final far-future watermark times out every remaining partial match, the SharedBuffer
     * must be fully drained (zero nodes, zero buffered events) — a stranded refcount on any
     * shared node would leave entries/events behind permanently.
     */
    @Test
    void testBranchingFollowedByAnyReleasesAllSharedBufferEntries() throws Exception {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .followedByAny("middle")
                .where(SimpleCondition.of(e -> e.getName().equals("b")))
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("c")))
                .within(java.time.Duration.ofSeconds(100));

        NFACompiler.NFAFactory<Event> nfaFactory = NFACompiler.compileFactory(pattern, false);

        PatternProcessFunction<Event, String> function = new PatternProcessFunction<>() {
            @Override
            public void processMatch(Map<String, List<Event>> match, Context ctx, Collector<String> out) {
                Event start = match.get("start").get(0);
                Event end = match.get("end").get(0);
                out.collect(start.getName() + "->" + end.getName());
            }
        };

        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                null, false, nfaFactory, null, null, function, null);
        operator.setOutput(output);
        setProcessingTimeService(operator);
        operator.open();

        // "a" starts a match; two "b" events branch via followedByAny (diamond topology);
        // "c" completes every outstanding branch.
        operator.processElement(new StreamRecord<>(new Event(1, "a"), 1));
        operator.processElement(new StreamRecord<>(new Event(2, "b"), 2));
        operator.processElement(new StreamRecord<>(new Event(3, "b"), 3));
        operator.processElement(new StreamRecord<>(new Event(4, "c"), 4));

        // Event-time mode buffers elements until the watermark advances past their timestamps:
        // drive the drain before asserting on matches (mirrors the null-serializer E2E above).
        operator.processWatermark(new Watermark(10));
        assertFalse(output.isEmpty(), "branching followedByAny must produce matches");
        // Both branches must complete: (a, b@2, c) and (a, b@3, c).
        assertEquals(2, output.size(), "both followedByAny branches must produce a match");

        // Far-future watermark: must exceed within(100s) by a wide margin so every remaining
        // partial match times out (a watermark of exactly 100000 leaves partials started at
        // ts=1 alive: 1 + 100000 > 100000).
        operator.processWatermark(new Watermark(10_000_000));

        assertEquals(0, operator.getPartialMatches().getSharedBufferNodeSize(),
                "SharedBuffer nodes must be fully released after matches complete and timeouts drain");
        assertEquals(0, operator.getPartialMatches().getEventsBufferSize(),
                "SharedBuffer events must be fully released — a stranded refcount would leak them");
        operator.close();
    }
}
