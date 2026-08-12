package io.nop.stream.cep;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.operator.CepOperator;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.typeinfo.BasicTypeInfo;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.datastream.KeyedStream;
import io.nop.stream.core.datastream.SingleOutputStreamOperator;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.operators.TimestampsAndWatermarksOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.util.Collector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CEP production-path E2E (plan `2026-08-13-0132-1` Phase 1, Rule #22): builds CEP jobs that
 * run through the FULL production execution path (env.execute → GraphExecutionPlan →
 * StreamTaskInvokable.invoke → operatorChain.open) with NO mock ProcessingTimeService
 * injection. Before the fix, {@code CepOperator.open()} NPE'd on
 * {@code registerCacheStatisticsTimer()} because the production path never wired a
 * ProcessingTimeService; the guard tests at the bottom pin the explicit fallback behavior.
 */
public class TestCepProductionExecutionE2E {

    static final class Event {
        final int id;
        final String name;
        final long ts;

        Event(int id, String name, long ts) {
            this.id = id;
            this.name = name;
            this.ts = ts;
        }

        @Override
        public String toString() {
            return "Event{id=" + id + ", name='" + name + "', ts=" + ts + '}';
        }
    }

    private static PatternProcessFunction<Event, String> matchFunction() {
        return new PatternProcessFunction<>() {
            @Override
            public void processMatch(Map<String, List<Event>> match,
                                     io.nop.stream.cep.functions.PatternProcessFunction.Context ctx,
                                     Collector<String> out) {
                Event start = match.get("start").get(0);
                Event end = match.get("end").get(0);
                out.collect(start.name + "->" + end.name);
            }
        };
    }

    private static Pattern<Event, ?> abPattern() {
        return Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.name.equals("a")))
                .next("end")
                .where(SimpleCondition.of(e -> e.name.equals("b")));
    }

    private static StreamExecutionEnvironment buildEnv() {
        return StreamExecutionEnvironment.createTestEnvironment();
    }

    /**
     * Event-time CEP job through the full production path. The TimestampsAndWatermarks
     * operator (watermarkInterval=0) emits a watermark after every element; the source-end
     * MAX_WATERMARK flushes the buffered events and the match is emitted to the sink.
     */
    @Test
    void testCepEventTimeJobRunsThroughProductionPath() throws Exception {
        List<String> results = Collections.synchronizedList(new java.util.ArrayList<>());
        StreamExecutionEnvironment env = buildEnv();

        WatermarkStrategy<Event> strategy = WatermarkStrategy
                .<Event>forBoundedOutOfOrderness(java.time.Duration.ofMillis(10))
                .withTimestampAssigner((event, ts) -> event.ts);

        SingleOutputStreamOperator<Event> timestamped = env.fromCollection(java.util.Arrays.asList(
                        new Event(1, "a", 1),
                        new Event(2, "b", 2),
                        new Event(3, "a", 3),
                        new Event(4, "b", 4)))
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<Event>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy, 0));

        KeyedStream<Event, Integer> keyed = timestamped.keyBy(e -> e.id);
        PatternStream<Event> patternStream = CEP.pattern(keyed, abPattern());
        patternStream.process(matchFunction(), BasicTypeInfo.STRING)
                .sink((SinkFunction<String>) results::add);

        env.execute("cep-event-time-production-e2e");

        assertEquals(2, results.size(), "Two a->b matches across the two keys");
        assertTrue(results.contains("a->b"), "Match emitted: " + results);
    }

    /**
     * Processing-time CEP job through the full production path: processElement advances the
     * NFA with the injected (real) ProcessingTimeService's current processing time.
     */
    @Test
    void testCepProcessingTimeModeRunsThroughProductionPath() throws Exception {
        List<String> results = Collections.synchronizedList(new java.util.ArrayList<>());
        StreamExecutionEnvironment env = buildEnv();

        KeyedStream<Event, Integer> keyed = env.fromCollection(java.util.Arrays.asList(
                        new Event(1, "a", 1),
                        new Event(1, "b", 2)))
                .keyBy(e -> e.id);

        CEP.pattern(keyed, abPattern())
                .inProcessingTime()
                .process(matchFunction(), BasicTypeInfo.STRING)
                .sink((SinkFunction<String>) results::add);

        env.execute("cep-processing-time-production-e2e");

        assertEquals(1, results.size(), "PT-mode job emits the a->b match: " + results);
        assertEquals("a->b", results.get(0));
    }

    /**
     * Guard fallback: CepOperator.open() without any injected ProcessingTimeService must NOT
     * NPE (WARN instead), pinning the P0-01 null-guard behavior for non-task usage.
     */
    @Test
    void testCepOpenWithoutServiceDoesNotNpe() throws Exception {
        CepOperator<Event, Integer, String> operator = buildCepOperator(false);
        operator.setOutput(new io.nop.stream.core.test.TestOutput<>());
        // No setProcessingTimeService / no inject: open() must not NPE (guard + WARN).
        operator.open();
        operator.close();
    }

    /**
     * Guard fallback: processing-time mode processElement with no ProcessingTimeService must
     * fail explicitly (no silent NPE, no silent skip) — the P0-01 explicit-failure contract.
     */
    @Test
    void testCepProcessingTimeProcessElementFailsExplicitlyWithoutService() throws Exception {
        CepOperator<Event, Integer, String> operator = buildCepOperator(true);
        operator.setOutput(new io.nop.stream.core.test.TestOutput<>());
        operator.open();

        StreamException ex = assertThrows(StreamException.class,
                () -> operator.processElement(new StreamRecord<>(new Event(1, "a", 1))),
                "PT-mode processElement without a service fails fast");
        assertTrue(ex.getParamsString() != null && ex.getParamsString().contains("ProcessingTimeService"),
                "Error mentions the missing ProcessingTimeService");
    }

    private static CepOperator<Event, Integer, String> buildCepOperator(boolean processingTime) {
        NFACompiler.NFAFactory<Event> nfaFactory = NFACompiler.compileFactory(abPattern(), false);
        return new CepOperator<>(
                (TypeSerializer<Event>) null,
                processingTime,
                nfaFactory,
                null,
                null,
                matchFunction(),
                null);
    }
}
