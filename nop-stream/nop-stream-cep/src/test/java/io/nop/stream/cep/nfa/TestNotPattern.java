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

public class TestNotPattern {

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
    void testNotNextBlocksMatch() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .notNext("not")
                .where(SimpleCondition.of(e -> e.getName().equals("b")))
                .next("end")
                .where(SimpleCondition.of(e -> e.getName().equals("c")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(new Event(1, "a"), new Event(2, "b"), new Event(3, "c")));

        assertTrue(matches.isEmpty());
        nfa.close();
    }

    @Test
    void testNotFollowedBy() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .notFollowedBy("not")
                .where(SimpleCondition.of(e -> e.getName().equals("b")))
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("c")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(new Event(1, "a"), new Event(2, "d"), new Event(3, "c")));

        assertEquals(1, matches.size());
        nfa.close();
    }

    @Test
    void testNotFollowedByBlocksMatch() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .notFollowedBy("not")
                .where(SimpleCondition.of(e -> e.getName().equals("b")))
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("c")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(new Event(1, "a"), new Event(2, "b"), new Event(3, "c")));

        assertTrue(matches.isEmpty());
        nfa.close();
    }

    @Test
    void testNotFollowedByWithIntermediateEvents() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .notFollowedBy("not")
                .where(SimpleCondition.of(e -> e.getName().equals("c")))
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("d")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(new Event(1, "a"), new Event(2, "b"), new Event(3, "c")));

        assertTrue(matches.isEmpty());
        nfa.close();
    }

    @Test
    void testNotNextNoMatchAtTheEnd() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .notNext("not")
                .where(SimpleCondition.of(e -> e.getName().equals("b")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(new Event(1, "a")));

        assertTrue(matches.isEmpty());
        nfa.close();
    }

    @Test
    void testNotFollowedByBeforeOptional() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .notFollowedBy("not")
                .where(SimpleCondition.of(e -> e.getName().equals("c")))
                .followedBy("middle")
                .where(SimpleCondition.of(e -> e.getName().equals("b")))
                .optional()
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("d")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(new Event(1, "a"), new Event(2, "b"), new Event(3, "d")));

        assertEquals(2, matches.size());
        nfa.close();
    }

    @Test
    void testNotFollowedByWithTimes() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .notFollowedBy("not")
                .where(SimpleCondition.of(e -> e.getName().equals("d")))
                .followedBy("middle")
                .where(SimpleCondition.of(e -> e.getName().equals("b")))
                .times(2)
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("c")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(
                        new Event(1, "a"),
                        new Event(2, "b"),
                        new Event(3, "b"),
                        new Event(4, "c")));

        assertEquals(1, matches.size());
        assertEquals(2, matches.get(0).get("middle").size());
        nfa.close();
    }
}
