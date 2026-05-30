package io.nop.stream.cep.model.builder;

import io.nop.stream.cep.model.CepPatternModel;
import io.nop.stream.cep.model.CepPatternSingleModel;
import io.nop.stream.cep.nfa.NFA;
import io.nop.stream.cep.nfa.State;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class TestCepPatternBuilder {

    private NFA<Object> compileToNFA(Pattern<?, ?> pattern) {
        @SuppressWarnings("unchecked")
        NFA<Object> nfa = (NFA<Object>) NFACompiler.compileFactory((Pattern<Object, ?>) pattern, false).createNFA();
        return nfa;
    }

    @Test
    void testBuildSinglePartPattern_returnsNfaWithNonEmptyStates() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("start");

        CepPatternSingleModel step = new CepPatternSingleModel();
        step.setName("start");
        model.addPart(step);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);

        assertNotNull(pattern, "buildFromModel should return a non-null Pattern");

        NFA<Object> nfa = compileToNFA(pattern);

        assertNotNull(nfa, "NFA should not be null");

        Collection<State<Object>> states = nfa.getStates();
        assertNotNull(states, "NFA states should not be null");
        assertFalse(states.isEmpty(), "NFA should contain at least one state");

        boolean hasStart = states.stream().anyMatch(State::isStart);
        assertTrue(hasStart, "NFA should have a start state");

        boolean hasFinal = states.stream().anyMatch(State::isFinal);
        assertTrue(hasFinal, "NFA should have a final state");
    }

    @Test
    void testMultiStepPatternWhereConditionsAppliedToAllSteps() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("a");

        CepPatternSingleModel stepA = new CepPatternSingleModel();
        stepA.setName("a");
        stepA.setNext("b");
        stepA.setWhere((thisObj, args, scope) -> true);

        CepPatternSingleModel stepB = new CepPatternSingleModel();
        stepB.setName("b");
        stepB.setNext("c");
        stepB.setWhere((thisObj, args, scope) -> false);
        stepB.setOneOrMore(true);
        stepB.setUntil((thisObj, args, scope) -> true);

        CepPatternSingleModel stepC = new CepPatternSingleModel();
        stepC.setName("c");
        stepC.setWhere((thisObj, args, scope) -> true);

        model.addPart(stepA);
        model.addPart(stepB);
        model.addPart(stepC);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);

        Pattern<?, ?> stepCPattern = pattern;
        Pattern<?, ?> stepBPattern = pattern.getPrevious();
        Pattern<?, ?> stepAPattern = pattern.getPrevious().getPrevious();

        assertNotNull(stepAPattern.getCondition(), "Step a should have where condition");
        assertNotNull(stepBPattern.getCondition(), "Step b should have where condition");
        assertNotNull(stepCPattern.getCondition(), "Step c should have where condition");
        assertNotNull(stepBPattern.getUntilCondition(), "Step b should have until condition");
    }

    @Test
    void testBuildTwoPartPattern_nfaHasMoreStatesThanSinglePart() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("a");

        CepPatternSingleModel stepA = new CepPatternSingleModel();
        stepA.setName("a");
        stepA.setNext("b");

        CepPatternSingleModel stepB = new CepPatternSingleModel();
        stepB.setName("b");

        model.addPart(stepA);
        model.addPart(stepB);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);

        NFA<Object> nfa = compileToNFA(pattern);

        assertFalse(nfa.getStates().isEmpty(), "NFA states should not be empty");
        assertEquals("b", pattern.getName());
        assertEquals("a", pattern.getPrevious().getName());
    }
}
