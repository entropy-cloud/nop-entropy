package io.nop.stream.cep;

import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.datastream.DataStream;
import io.nop.stream.core.datastream.KeyedStream;
import io.nop.stream.core.datastream.SingleOutputStreamOperator;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.util.Collector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestPatternStreamBuilder {

    private StreamExecutionEnvironment env;
    private Pattern<Event, ?> pattern;
    private PatternProcessFunction<Event, String> function;

    @BeforeEach
    void setUp() {
        env = new StreamExecutionEnvironment();

        pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("b")));

        function = new PatternProcessFunction<>() {
            @Override
            public void processMatch(Map<String, List<Event>> match, Context ctx, Collector<String> out) {
                Event start = match.get("start").get(0);
                Event end = match.get("end").get(0);
                out.collect(start.getName() + "->" + end.getName());
            }
        };
    }

    @Test
    void testPatternStreamCreationWithKeyedStream() {
        KeyedStream<Event, Integer> keyedStream = env.fromElements(
                new Event(1, "a"),
                new Event(2, "b")
        ).keyBy(Event::getId);

        PatternStream<Event> patternStream = CEP.pattern(keyedStream, pattern);

        assertNotNull(patternStream, "CEP.pattern() should return a non-null PatternStream");
    }

    @Test
    void testPatternStreamProcessWithKeyedStream() {
        KeyedStream<Event, Integer> keyedStream = env.fromElements(
                new Event(1, "a"),
                new Event(2, "b")
        ).keyBy(Event::getId);

        PatternStream<Event> patternStream = CEP.pattern(keyedStream, pattern);

        TypeInformation<String> outType = new TypeInformation<>() {
            @Override
            public Class<String> getTypeClass() {
                return String.class;
            }
        };

        SingleOutputStreamOperator<String> result = patternStream.process(function, outType);

        assertNotNull(result, "PatternStream.process() should return a non-null DataStream");
        assertEquals(String.class, result.getType().getTypeClass(),
                "Result stream type should be String");
    }

    @Test
    void testPatternStreamWithKeyedStreamChaining() {
        KeyedStream<Event, Integer> keyedStream = env.fromElements(
                new Event(1, "a"),
                new Event(2, "b")
        ).keyBy(Event::getId);

        PatternStream<Event> patternStream = CEP.pattern(keyedStream, pattern);

        assertNotNull(patternStream,
                "CEP.pattern() with KeyedStream should return a non-null PatternStream");

        TypeInformation<String> outType = new TypeInformation<>() {
            @Override
            public Class<String> getTypeClass() {
                return String.class;
            }
        };

        SingleOutputStreamOperator<String> result = patternStream.process(function, outType);

        assertNotNull(result,
                "PatternStream.process() on keyed stream should return non-null result");
        assertEquals(String.class, result.getType().getTypeClass(),
                "Result stream type should be String");
    }
}
