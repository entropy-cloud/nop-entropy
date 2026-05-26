package io.nop.stream.cep;

import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.operator.CepOperator;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.datastream.DataStream;
import io.nop.stream.core.datastream.KeyedStream;
import io.nop.stream.core.datastream.SingleOutputStreamOperator;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
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

    private List<String> results;

    @BeforeEach
    void setUp() {
        results = new ArrayList<>();
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

        operator.setOutput(new TestOutput(results));
        setProcessingTimeService(operator);
        operator.open();

        operator.processElement(new StreamRecord<>(new Event(1, "a"), 1));
        operator.processElement(new StreamRecord<>(new Event(2, "b"), 2));
        operator.processWatermark(new Watermark(10));

        assertFalse(results.isEmpty(), "Should have matched the a->b pattern");
        assertEquals("a->b", results.get(0));
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
        Field f = io.nop.stream.core.operators.AbstractStreamOperator.class
                .getDeclaredField("processingTimeService");
        f.setAccessible(true);
        f.set(op, pts);
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
        public void close() {}

        @Override
        public void emitWatermark(Watermark mark) {}

        @Override
        public void emitBarrier(CheckpointBarrier barrier) {}

        @Override
        public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {}

        @Override
        public void emitLatencyMarker(LatencyMarker latencyMarker) {}

        @Override
        public void emitWatermarkStatus(WatermarkStatus watermarkStatus) {}
    }
}
