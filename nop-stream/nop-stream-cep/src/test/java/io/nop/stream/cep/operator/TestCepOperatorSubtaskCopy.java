package io.nop.stream.cep.operator;

import io.nop.stream.cep.Event;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.util.Collector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestCepOperatorSubtaskCopy {

    @Test
    void cepOperatorCopyForSubtaskReturnsDistinctInstance() {
        PatternProcessFunction<Event, String> function = new PatternProcessFunction<>() {
            @Override
            public void processMatch(Map<String, List<Event>> match, Context ctx, Collector<String> out) {
                Event start = match.get("start").get(0);
                Event end = match.get("end").get(0);
                out.collect(start.getName() + "->" + end.getName());
            }
        };

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")));

        NFACompiler.NFAFactory<Event> nfaFactory = NFACompiler.compileFactory(pattern, false);

        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                null, false, nfaFactory, null, null, function, null);

        CepOperator<Event, Integer, String> copy = operator.copyForSubtask();

        assertNotSame(operator, copy, "CepOperator copy must be a fresh instance");
        assertSame(operator.getUserFunction(), copy.getUserFunction(),
                "CepOperator user function must be shared across subtasks");
    }
}
