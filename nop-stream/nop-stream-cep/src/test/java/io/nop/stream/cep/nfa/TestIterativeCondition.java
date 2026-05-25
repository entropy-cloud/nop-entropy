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
import io.nop.stream.cep.pattern.conditions.IterativeCondition;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestIterativeCondition {

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
    void testIterativeWithPrevPatternDependency() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .followedBy("middle")
                .where(SimpleCondition.of(e -> e.getName().equals("b")))
                .followedBy("end")
                .where(new IterativeCondition<>() {
                    @Override
                    public boolean filter(Event value, Context<Event> ctx) throws Exception {
                        return value.getName().equals("c")
                                && ctx.getEventsForPattern("start").iterator().hasNext();
                    }
                });

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(
                        new Event(1, "a"),
                        new Event(2, "b"),
                        new Event(3, "c")));

        assertEquals(1, matches.size());
        nfa.close();
    }

    @Test
    void testIterativeWithPrevPatternDependencyFail() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .followedBy("middle")
                .where(SimpleCondition.of(e -> e.getName().equals("b")))
                .followedBy("end")
                .where(new IterativeCondition<>() {
                    @Override
                    public boolean filter(Event value, Context<Event> ctx) throws Exception {
                        return value.getName().equals("c")
                                && !ctx.getEventsForPattern("middle").iterator().hasNext();
                    }
                });

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(
                        new Event(1, "a"),
                        new Event(2, "b"),
                        new Event(3, "c")));

        assertTrue(matches.isEmpty(),
                "Iterative condition should fail because 'middle' events exist");
        nfa.close();
    }

    @Test
    void testIterativeWithABACPattern() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .followedBy("middle")
                .where(SimpleCondition.of(e -> e.getName().equals("b")))
                .followedBy("end")
                .where(new IterativeCondition<>() {
                    @Override
                    public boolean filter(Event value, Context<Event> ctx) throws Exception {
                        return value.getName().equals("a")
                                && ctx.getEventsForPattern("start").iterator().hasNext();
                    }
                });

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(
                        new Event(1, "a"),
                        new Event(2, "b"),
                        new Event(3, "a")));

        assertEquals(1, matches.size(), "ABAC pattern should match");
        nfa.close();
    }

    @Test
    void testIterativeWithBranchingPattern() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .followedByAny("middle")
                .where(SimpleCondition.of(e -> e.getName().equals("b")))
                .followedBy("end")
                .where(new IterativeCondition<>() {
                    @Override
                    public boolean filter(Event value, Context<Event> ctx) throws Exception {
                        return value.getName().equals("c")
                                && ctx.getEventsForPattern("middle").iterator().hasNext();
                    }
                });

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(
                        new Event(1, "a"),
                        new Event(2, "x"),
                        new Event(3, "b"),
                        new Event(4, "c")));

        assertEquals(1, matches.size());
        nfa.close();
    }

    @Test
    void testSimpleCondition() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getId() > 10))
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("end")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(
                        new Event(5, "start"),
                        new Event(15, "start"),
                        new Event(99, "end")));

        assertEquals(1, matches.size(), "Only event with id>10 should match start");
        nfa.close();
    }

    @Test
    void testSubtypeCondition() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .subtype(io.nop.stream.cep.SubEvent.class)
                .where(SimpleCondition.of(e -> ((io.nop.stream.cep.SubEvent) e).getVolume() > 5.0))
                .followedBy("end")
                .where(SimpleCondition.of(e -> e.getName().equals("end")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(
                        new io.nop.stream.cep.SubEvent(1, "foo", 3.0),
                        new io.nop.stream.cep.SubEvent(2, "bar", 10.0),
                        new Event(99, "end")));

        assertEquals(1, matches.size());
        assertEquals(2, matches.get(0).get("start").get(0).getId());
        nfa.close();
    }
}
