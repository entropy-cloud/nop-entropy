package io.nop.stream.cep.nfa;

import io.nop.commons.tuple.Tuple2;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.MockRuntimeContext;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.nfa.aftermatch.AfterMatchSkipStrategy;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBuffer;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBufferAccessor;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGreedy {

    private NFA<Event> compileNFA(Pattern<Event, ?> pattern) {
        NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();
        nfa.open(new MockRuntimeContext(), null);
        return nfa;
    }

    private SharedBuffer<Event> createBuffer() {
        return new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
    }

    private List<Map<String, List<Event>>> feedEvents(
            NFA<Event> nfa, SharedBuffer<Event> buffer, NFAState state, List<Event> events) {
        List<Map<String, List<Event>>> allMatches = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            long timestamp = i + 1;
            try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
                Tuple2<Collection<Map<String, List<Event>>>, Collection<Tuple2<Map<String, List<Event>>, Long>>>
                        pending = nfa.advanceTime(accessor, state, timestamp, AfterMatchSkipStrategy.noSkip());
                Collection<Map<String, List<Event>>> matches =
                        nfa.process(accessor, state, events.get(i), timestamp, AfterMatchSkipStrategy.noSkip(), null);
                matches.addAll(pending.f0);
                allMatches.addAll(matches);
            }
            if (state.isStateChanged()) {
                state.resetStateChanged();
                state.resetNewStartPartialMatch();
            }
        }
        return allMatches;
    }

    @Test
    void testGreedyZeroOrMore() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .oneOrMore()
                .greedy()
                .optional()
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("b")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(
                        new Event(1, "a"),
                        new Event(2, "a"),
                        new Event(3, "a"),
                        new Event(4, "b")));

        assertTrue(matches.size() >= 1,
                "Greedy zeroOrMore should produce matches");
        Map<String, List<Event>> match = matches.get(0);
        assertEquals(3, match.get("start").size(),
                "Greedy zeroOrMore should consume all 3 'a' events before 'b'");
        nfa.close();
    }

    @Test
    void testGreedyOneOrMore() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .oneOrMore()
                .greedy()
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("b")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(
                        new Event(1, "a"),
                        new Event(2, "a"),
                        new Event(3, "b")));

        assertTrue(matches.size() >= 1);
        Map<String, List<Event>> match = matches.get(0);
        assertEquals(2, match.get("start").size(),
                "Greedy oneOrMore should consume both 'a' events before 'b'");
        nfa.close();
    }

    @Test
    void testGreedyZeroOrMoreInBetween() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .followedBy("middle")
                .where(SimpleCondition.of(e -> e.getName().equals("x")))
                .oneOrMore()
                .greedy()
                .optional()
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("b")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(
                        new Event(1, "a"),
                        new Event(2, "x"),
                        new Event(3, "x"),
                        new Event(4, "b")));

        assertTrue(matches.size() >= 1, "Greedy with middle events should produce matches");
        Map<String, List<Event>> match = matches.get(0);
        assertEquals(2, match.get("middle").size(),
                "Greedy middle should consume both 'x' events before 'b'");
        nfa.close();
    }

    @Test
    void testNonGreedyOneOrMore() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .oneOrMore()
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("b")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(
                        new Event(1, "a"),
                        new Event(2, "a"),
                        new Event(3, "b")));

        assertTrue(matches.size() >= 2,
                "Non-greedy oneOrMore should produce multiple matches (a->b, a,a->b)");
        boolean foundOneA = false;
        boolean foundTwoA = false;
        for (Map<String, List<Event>> m : matches) {
            int size = m.get("start").size();
            if (size == 1) foundOneA = true;
            if (size == 2) foundTwoA = true;
        }
        assertTrue(foundOneA, "Non-greedy should produce a match with 1 'a' event");
        assertTrue(foundTwoA, "Non-greedy should produce a match with 2 'a' events");
        nfa.close();
    }

    @Test
    void testGreedyTimesRange() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .times(2, 4)
                .greedy()
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("b")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(
                        new Event(1, "a"),
                        new Event(2, "a"),
                        new Event(3, "a"),
                        new Event(4, "a"),
                        new Event(5, "b")));

        assertTrue(matches.size() >= 1);
        Map<String, List<Event>> match = matches.get(0);
        assertEquals(4, match.get("start").size(),
                "Greedy times(2,4) should consume all 4 'a' events before 'b'");
        nfa.close();
    }

    @Test
    void testGreedyWithDummyEventsBeforeQuantifier() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .followedBy("middle")
                .where(SimpleCondition.of(e -> e.getName().equals("x")))
                .oneOrMore()
                .greedy()
                .optional()
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("b")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(
                        new Event(1, "a"),
                        new Event(2, "dummy1"),
                        new Event(3, "x"),
                        new Event(4, "b")));

        assertTrue(matches.size() >= 1,
                "Greedy with dummy before quantifier should match");
        Map<String, List<Event>> match = matches.get(0);
        assertEquals(1, match.get("middle").size(),
                "Greedy middle should consume the single 'x' event");
        nfa.close();
    }
}
