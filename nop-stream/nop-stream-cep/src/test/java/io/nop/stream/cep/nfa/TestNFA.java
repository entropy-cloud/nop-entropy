package io.nop.stream.cep.nfa;

import io.nop.commons.tuple.Tuple2;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.MockRuntimeContext;
import io.nop.stream.cep.SubEvent;
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

public class TestNFA {

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

    private NFA<Event> compileNFA(Pattern<Event, ?> pattern) {
        NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();
        nfa.open(new MockRuntimeContext(), null);
        return nfa;
    }

    @Test
    public void testSimplePatternMatch() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(SimpleCondition.of(event -> event.getId() == 1))
                .next("b")
                .where(SimpleCondition.of(event -> event.getId() == 2));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
        NFAState state = nfa.createInitialNFAState();

        List<Event> events = new ArrayList<>();
        events.add(new Event(1, "e1"));
        events.add(new Event(2, "e2"));
        events.add(new Event(3, "e3"));

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state, events);

        assertEquals(1, matches.size());
        Map<String, List<Event>> match = matches.get(0);
        assertEquals(2, match.size());
        assertTrue(match.containsKey("a"));
        assertTrue(match.containsKey("b"));
        assertEquals(1, match.get("a").get(0).getId());
        assertEquals("e1", match.get("a").get(0).getName());
        assertEquals(2, match.get("b").get(0).getId());
        assertEquals("e2", match.get("b").get(0).getName());

        nfa.close();
    }

    @Test
    public void testPatternNoMatch() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(SimpleCondition.of(event -> event.getId() == 1))
                .next("b")
                .where(SimpleCondition.of(event -> event.getId() == 2));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
        NFAState state = nfa.createInitialNFAState();

        List<Event> events = new ArrayList<>();
        events.add(new Event(3, "e3"));
        events.add(new Event(4, "e4"));
        events.add(new Event(5, "e5"));

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state, events);
        assertTrue(matches.isEmpty());

        nfa.close();
    }

    @Test
    public void testFollowedByMatch() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(SimpleCondition.of(event -> event.getId() == 1))
                .followedBy("b")
                .where(SimpleCondition.of(event -> event.getId() == 3));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
        NFAState state = nfa.createInitialNFAState();

        List<Event> events = new ArrayList<>();
        events.add(new Event(1, "e1"));
        events.add(new Event(2, "e2"));
        events.add(new Event(3, "e3"));

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state, events);

        assertEquals(1, matches.size());
        Map<String, List<Event>> match = matches.get(0);
        assertEquals(2, match.size());
        assertEquals(1, match.get("a").get(0).getId());
        assertEquals(3, match.get("b").get(0).getId());

        nfa.close();
    }

    @Test
    public void testFollowedByAnyMultipleMatches() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(SimpleCondition.of(event -> event.getId() == 1))
                .followedByAny("b")
                .where(SimpleCondition.of(event -> "b".equals(event.getName())));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
        NFAState state = nfa.createInitialNFAState();

        List<Event> events = new ArrayList<>();
        events.add(new Event(1, "a"));
        events.add(new Event(2, "b"));

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state, events);

        assertTrue(matches.size() >= 1);
        Map<String, List<Event>> firstMatch = matches.get(0);
        assertEquals(1, firstMatch.get("a").get(0).getId());
        assertEquals("b", firstMatch.get("b").get(0).getName());

        nfa.close();
    }

    @Test
    public void testPatternPartialMatchNotCompleted() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(SimpleCondition.of(event -> event.getId() == 1))
                .next("b")
                .where(SimpleCondition.of(event -> event.getId() == 2));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
        NFAState state = nfa.createInitialNFAState();

        List<Event> events = new ArrayList<>();
        events.add(new Event(1, "e1"));

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state, events);
        assertTrue(matches.isEmpty());

        nfa.close();
    }
}
