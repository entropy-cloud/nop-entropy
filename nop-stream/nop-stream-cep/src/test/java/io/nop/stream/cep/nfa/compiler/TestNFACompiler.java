package io.nop.stream.cep.nfa.compiler;

import io.nop.stream.cep.Event;
import io.nop.stream.cep.nfa.NFA;
import io.nop.stream.cep.nfa.State;
import io.nop.stream.cep.nfa.StateTransition;
import io.nop.stream.cep.pattern.MalformedPatternException;
import io.nop.stream.cep.pattern.Pattern;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNFACompiler {

    @Test
    public void testSimplePatternCompilation() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a").next("b");
        NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();

        Collection<State<Event>> states = nfa.getStates();
        assertFalse(states.isEmpty());

        Set<String> stateNames = new HashSet<>();
        boolean hasStart = false;
        boolean hasFinal = false;

        for (State<Event> state : states) {
            stateNames.add(state.getName());
            if (state.isStart()) {
                hasStart = true;
            }
            if (state.isFinal()) {
                hasFinal = true;
                assertEquals(NFACompiler.ENDING_STATE_NAME, state.getName());
            }
        }

        assertTrue(hasStart, "NFA should have a Start state");
        assertTrue(hasFinal, "NFA should have a Final state");
        assertTrue(stateNames.contains("a"));
        assertTrue(stateNames.contains("b"));
    }

    @Test
    public void testPatternWithFollowedBy() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a").followedBy("b");
        NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();

        Collection<State<Event>> states = nfa.getStates();
        assertFalse(states.isEmpty());
        assertTrue(states.size() >= 3);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNullPatternReturnsEmptyNFA() {
        NFA nfa = NFACompiler.compileFactory(null, false).createNFA();
        assertEquals(0, nfa.getStates().size());
    }

    @Test
    public void testStateTransitionsExist() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a").next("b");
        NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();

        for (State<Event> state : nfa.getStates()) {
            if (!state.isFinal()) {
                assertFalse(state.getStateTransitions().isEmpty(),
                        "Non-final state '" + state.getName() + "' should have transitions");
            }
        }

        boolean foundTakeTransition = false;
        for (State<Event> state : nfa.getStates()) {
            for (StateTransition<Event> transition : state.getStateTransitions()) {
                if (transition.getAction() != null) {
                    foundTakeTransition = true;
                    break;
                }
            }
        }
        assertTrue(foundTakeTransition, "Should have at least one transition with an action");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDuplicatePatternNamesThrow() {
        assertThrows(MalformedPatternException.class, () -> {
            Pattern<Event, ?> pattern = Pattern.<Event>begin("a").next("a");
            NFACompiler.compileFactory(pattern, false).createNFA();
        });
    }
}
