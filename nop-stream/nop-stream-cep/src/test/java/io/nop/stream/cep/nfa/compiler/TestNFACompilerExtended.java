package io.nop.stream.cep.nfa.compiler;

import io.nop.stream.cep.Event;
import io.nop.stream.cep.nfa.NFA;
import io.nop.stream.cep.nfa.State;
import io.nop.stream.cep.nfa.StateTransition;
import io.nop.stream.cep.nfa.StateTransitionAction;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNFACompilerExtended {

    private static final SimpleCondition<Event> ALWAYS_TRUE =
            SimpleCondition.of(event -> true);

    @Test
    public void testTimesRangeCompilation() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(ALWAYS_TRUE)
                .times(3, 5);
        NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();

        Collection<State<Event>> states = nfa.getStates();
        assertFalse(states.isEmpty());

        Set<String> stateNames = new HashSet<>();
        for (State<Event> state : states) {
            stateNames.add(state.getName());
        }

        assertTrue(stateNames.contains("a"), "Should contain state 'a'");
        assertTrue(stateNames.stream().anyMatch(n -> n.contains("a")),
                "Should contain states for 'a' pattern with times(3,5)");
    }

    @Test
    public void testTimesRangeStateTransitionsExist() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(ALWAYS_TRUE)
                .times(3, 5);
        NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();

        for (State<Event> state : nfa.getStates()) {
            if (!state.isFinal()) {
                assertFalse(state.getStateTransitions().isEmpty(),
                        "Non-final state '" + state.getName() + "' should have transitions");
            }
        }
    }

    @Test
    public void testOneOrMoreCompilation() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(ALWAYS_TRUE)
                .oneOrMore()
                .next("b");
        NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();

        Collection<State<Event>> states = nfa.getStates();
        assertFalse(states.isEmpty());

        Set<String> stateNames = new HashSet<>();
        for (State<Event> state : states) {
            stateNames.add(state.getName());
        }

        assertTrue(stateNames.contains("a"), "Should contain state 'a'");
        assertTrue(stateNames.contains("b"), "Should contain state 'b'");

        boolean hasLoopingState = false;
        for (State<Event> state : states) {
            if (!state.isFinal() && !state.isStart()) {
                for (StateTransition<Event> transition : state.getStateTransitions()) {
                    if (transition.getSourceState() == transition.getTargetState()) {
                        hasLoopingState = true;
                        break;
                    }
                }
            }
        }
        assertTrue(hasLoopingState, "oneOrMore should produce a self-looping state");
    }

    @Test
    public void testOptionalFollowedByCompilation() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(ALWAYS_TRUE)
                .followedBy("b")
                .where(ALWAYS_TRUE)
                .optional();
        NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();

        Collection<State<Event>> states = nfa.getStates();
        assertFalse(states.isEmpty());

        Set<String> stateNames = new HashSet<>();
        boolean hasStart = false;
        boolean hasFinal = false;
        for (State<Event> state : states) {
            stateNames.add(state.getName());
            if (state.isStart()) hasStart = true;
            if (state.isFinal()) hasFinal = true;
        }

        assertTrue(hasStart, "Should have a start state");
        assertTrue(hasFinal, "Should have a final state");
        assertTrue(stateNames.contains("a"), "Should contain state 'a'");
        assertTrue(stateNames.contains("b"), "Should contain state 'b'");

        int totalIgnoreTransitions = 0;
        for (State<Event> state : states) {
            for (StateTransition<Event> t : state.getStateTransitions()) {
                if (t.getAction() == StateTransitionAction.IGNORE) {
                    totalIgnoreTransitions++;
                }
            }
        }
        assertTrue(totalIgnoreTransitions > 0,
                "optional+followedBy should produce IGNORE transitions");
    }

    @Test
    public void testOptionalFollowedBySkipsMiddle() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(SimpleCondition.of(e -> e.getId() == 1))
                .followedBy("b")
                .where(SimpleCondition.of(e -> e.getId() == 2))
                .optional()
                .next("c")
                .where(SimpleCondition.of(e -> e.getId() == 3));

        NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();
        assertNotNull(nfa);
        assertFalse(nfa.getStates().isEmpty());
    }

    @Test
    public void testGroupPatternCompilation() {
        Pattern<Event, ?> groupPattern = Pattern.<Event>begin("g1")
                .where(ALWAYS_TRUE)
                .next("g2")
                .where(ALWAYS_TRUE);

        Pattern<Event, ?> pattern = Pattern.begin(groupPattern)
                .next("end");

        NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();

        Collection<State<Event>> states = nfa.getStates();
        assertFalse(states.isEmpty());

        Set<String> stateNames = new HashSet<>();
        for (State<Event> state : states) {
            stateNames.add(state.getName());
        }

        assertTrue(stateNames.contains("g1"), "Should contain group state 'g1'");
        assertTrue(stateNames.contains("g2"), "Should contain group state 'g2'");
        assertTrue(stateNames.contains("end"), "Should contain state 'end'");
    }

    @Test
    public void testGroupPatternWithFollowedBy() {
        Pattern<Event, ?> groupPattern = Pattern.<Event>begin("x")
                .where(ALWAYS_TRUE)
                .followedBy("y")
                .where(ALWAYS_TRUE);

        Pattern<Event, ?> pattern = Pattern.begin(groupPattern)
                .followedBy("z");

        NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();
        assertNotNull(nfa);

        Set<String> stateNames = new HashSet<>();
        for (State<Event> state : nfa.getStates()) {
            stateNames.add(state.getName());
        }

        assertTrue(stateNames.contains("x"));
        assertTrue(stateNames.contains("y"));
        assertTrue(stateNames.contains("z"));
    }

    @Test
    public void testTimesExactCompilation() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(ALWAYS_TRUE)
                .times(3);
        NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();

        Collection<State<Event>> states = nfa.getStates();
        assertFalse(states.isEmpty());

        Set<String> stateNames = new HashSet<>();
        for (State<Event> state : states) {
            stateNames.add(state.getName());
        }

        assertTrue(stateNames.contains("a"));
    }

    @Test
    public void testOneOrMoreWithConsecutive() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(ALWAYS_TRUE)
                .oneOrMore()
                .consecutive()
                .next("b");
        NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();

        assertNotNull(nfa);
        assertFalse(nfa.getStates().isEmpty());

        Set<String> stateNames = new HashSet<>();
        for (State<Event> state : nfa.getStates()) {
            stateNames.add(state.getName());
        }

        assertTrue(stateNames.contains("a"));
        assertTrue(stateNames.contains("b"));
    }

    @Test
    public void testTimesRangeWithFollowedBy() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(ALWAYS_TRUE)
                .times(3, 5)
                .followedBy("b");
        NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();

        assertNotNull(nfa);
        assertFalse(nfa.getStates().isEmpty());

        Set<String> stateNames = new HashSet<>();
        for (State<Event> state : nfa.getStates()) {
            stateNames.add(state.getName());
        }

        assertTrue(stateNames.contains("a"));
        assertTrue(stateNames.contains("b"));
    }
}
