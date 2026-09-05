package io.nop.stream.cep.operator;

import io.nop.stream.cep.CepTestUtils;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.RichIterativeCondition;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code CepOperator.open()} must hand a non-null {@link io.nop.stream.core.configuration.Configuration}
 * to user conditions via {@code NFA.open()} (same contract as
 * {@code AbstractUdfStreamOperator.open()} gives the UDF) — previously {@code null} was passed,
 * so any user {@link RichIterativeCondition#open(Configuration)} that checked its
 * {@code parameters} argument for null failed.
 */
public class TestCepOperatorConditionLifecycle {

    private static final ProcessingTimeService MOCK_PTS = new ProcessingTimeService() {
        @Override
        public long getCurrentProcessingTime() {
            return 1000;
        }

        @Override
        public ScheduledFuture<?> registerTimer(long timestamp, ProcessingTimeCallback target) {
            return null;
        }
    };

    @Test
    void testRichConditionReceivesNonNullConfigurationOnOpen() throws Exception {
        AtomicBoolean sawNonNullParameters = new AtomicBoolean(false);

        RichIterativeCondition<Event> condition = new RichIterativeCondition<>() {
            @Override
            public boolean filter(Event value, Context<Event> ctx) throws Exception {
                return true;
            }

            @Override
            public void open(io.nop.stream.core.configuration.Configuration parameters) {
                sawNonNullParameters.set(parameters != null);
            }
        };

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(condition)
                .followedBy("end")
                .where(io.nop.stream.cep.pattern.conditions.SimpleCondition.of(
                        value -> value.getName().equals("end")));

        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                new NoopSerializer(), false,
                NFACompiler.compileFactory(pattern, false),
                null, null,
                new PatternProcessFunction<>() {
                    @Override
                    public void processMatch(Map<String, List<Event>> match, Context ctx,
                                             Collector<String> out) {
                        out.collect("matched");
                    }
                }, null);
        operator.setOutput(new TestOutput<>());
        CepTestUtils.injectProcessingTimeService(operator, MOCK_PTS);
        operator.open();

        assertTrue(sawNonNullParameters.get(),
                "RichIterativeCondition.open() must receive a non-null Configuration");

        operator.processElement(new StreamRecord<>(new Event(1, "a"), 1));
        operator.processWatermark(new Watermark(10));
        operator.close();
    }

    private static class NoopSerializer implements TypeSerializer<Event> {
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
            return from;
        }

        @Override
        public Event copy(Event from, Event reuse) {
            return from;
        }

        @Override
        public int getLength() {
            return -1;
        }
    }
}
