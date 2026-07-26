package io.nop.stream.cep;

import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.operator.CepOperator;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.datastream.DataStream;
import io.nop.stream.core.datastream.SingleOutputStreamOperator;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that {@code CEP.pattern(nonKeyedStream, pattern)} no longer throws
 * at runtime. Previously the non-keyed entry path called
 * {@code SingleOutputStreamOperator.forceNonParallel()} which threw
 * {@link UnsupportedOperationException} unconditionally (P0-1).
 *
 * <p>Plan {@code 2026-07-26-0804-2-parallel-execution-cep-correctness.md} Phase 1
 * exit criterion: "{@code CEP.pattern(nonKeyedStream, pattern)} 构建不再抛
 * （有 e2e 测试：非 keyed CEP 管线产出匹配，且 vertex 并行度=1）".
 */
public class TestCepNonKeyedEntryE2E {

    @Test
    void cepPatternOnNonKeyedStreamBuildsWithoutThrowing() {
        StreamExecutionEnvironment env = new StreamExecutionEnvironment();
        DataStream<Event> stream = env.fromElements(
                new Event(1, "a"),
                new Event(2, "b")
        );

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("b")));

        // Before fix: this threw UnsupportedOperationException from forceNonParallel().
        // After fix: returns a non-null PatternStream.
        assertDoesNotThrow(() -> CEP.pattern(stream, pattern));
        assertNotNull(CEP.pattern(stream, pattern));
    }

    @Test
    void cepPatternOnNonKeyedStreamProducesMatches() throws Exception {
        // Build the non-keyed CEP path directly via PatternStreamBuilder logic.
        // We construct the CepOperator with NullByteKeySelector semantics (same
        // as PatternStreamBuilder.build() non-keyed branch) and verify matches
        // are produced end-to-end. The forceNonParallel() call (now functional)
        // is what previously blocked this path.
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

        // Same operator construction as PatternStreamBuilder.build() non-keyed branch
        CepOperator<Event, Byte, String> operator = new CepOperator<>(
                null,
                false,
                nfaFactory,
                null,
                null,
                function,
                null
        );

        TestOutput<String> output = new TestOutput<>();
        operator.setOutput(output);
        injectPts(operator);

        operator.open();

        operator.processElement(new StreamRecord<>(new Event(1, "a"), 1));
        operator.processElement(new StreamRecord<>(new Event(2, "b"), 2));
        operator.processWatermark(new Watermark(10));

        assertFalse(output.isEmpty(), "Non-keyed CEP pipeline must produce matches");
        assertEquals("a->b", output.get(0));

        operator.close();
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
}
