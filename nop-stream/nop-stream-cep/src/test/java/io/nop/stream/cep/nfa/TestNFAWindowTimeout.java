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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNFAWindowTimeout {

    private List<Map<String, List<Event>>> feedEventsWithAdvance(
            NFA<Event> nfa, SharedBuffer<Event> buffer, NFAState state, List<Long> timestamps,
            List<Event> events, List<Tuple2<Map<String, List<Event>>, Long>> timeoutResults) {
        List<Map<String, List<Event>>> allMatches = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            long timestamp = timestamps.get(i);
            try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
                Tuple2<Collection<Map<String, List<Event>>>, Collection<Tuple2<Map<String, List<Event>>, Long>>>
                        pending = nfa.advanceTime(accessor, state, timestamp, AfterMatchSkipStrategy.noSkip());
                if (pending.f1 != null) {
                    timeoutResults.addAll(pending.f1);
                }
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

    private NFA<Event> compileNFAWithTimeout(Pattern<Event, ?> pattern) {
        NFA<Event> nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(new MockRuntimeContext(), null);
        return nfa;
    }

    @Test
    public void testWindowTimeoutExpiresPartialMatch() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(SimpleCondition.of(event -> event.getId() == 1))
                .next("b")
                .where(SimpleCondition.of(event -> event.getId() == 2))
                .within(Duration.ofMillis(5));

        NFA<Event> nfa = compileNFAWithTimeout(pattern);
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
        NFAState state = nfa.createInitialNFAState();

        List<Event> events = new ArrayList<>();
        events.add(new Event(1, "start"));
        events.add(new Event(99, "gap"));
        events.add(new Event(99, "gap2"));
        events.add(new Event(2, "too-late"));

        List<Long> timestamps = new ArrayList<>();
        timestamps.add(1L);
        timestamps.add(10L);
        timestamps.add(20L);
        timestamps.add(30L);

        List<Tuple2<Map<String, List<Event>>, Long>> timeoutResults = new ArrayList<>();
        List<Map<String, List<Event>>> matches = feedEventsWithAdvance(nfa, buffer, state, timestamps, events, timeoutResults);

        assertTrue(matches.isEmpty());
        assertTrue(timeoutResults.size() > 0);

        nfa.close();
    }

    @Test
    public void testWindowTimeoutMatchBeforeExpiry() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(SimpleCondition.of(event -> event.getId() == 1))
                .next("b")
                .where(SimpleCondition.of(event -> event.getId() == 2))
                .within(Duration.ofMillis(100));

        NFA<Event> nfa = compileNFAWithTimeout(pattern);
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
        NFAState state = nfa.createInitialNFAState();

        List<Event> events = new ArrayList<>();
        events.add(new Event(1, "start"));
        events.add(new Event(2, "next"));

        List<Long> timestamps = new ArrayList<>();
        timestamps.add(1L);
        timestamps.add(2L);

        List<Tuple2<Map<String, List<Event>>, Long>> timeoutResults = new ArrayList<>();
        List<Map<String, List<Event>>> matches = feedEventsWithAdvance(nfa, buffer, state, timestamps, events, timeoutResults);

        assertEquals(1, matches.size());
        assertEquals(1, matches.get(0).get("a").get(0).getId());
        assertEquals(2, matches.get(0).get("b").get(0).getId());
        assertTrue(timeoutResults.isEmpty());

        nfa.close();
    }

    @Test
    public void testAdvanceTimePrunesTimedOutStates() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(SimpleCondition.of(event -> event.getId() == 1))
                .followedBy("b")
                .where(SimpleCondition.of(event -> event.getId() == 2))
                .within(Duration.ofMillis(5));

        NFA<Event> nfa = compileNFAWithTimeout(pattern);
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
        NFAState state = nfa.createInitialNFAState();

        List<Event> events = new ArrayList<>();
        events.add(new Event(1, "start"));
        events.add(new Event(99, "skip1"));
        events.add(new Event(99, "skip2"));
        events.add(new Event(99, "skip3"));

        List<Long> timestamps = new ArrayList<>();
        timestamps.add(1L);
        timestamps.add(10L);
        timestamps.add(20L);
        timestamps.add(30L);

        List<Tuple2<Map<String, List<Event>>, Long>> timeoutResults = new ArrayList<>();
        feedEventsWithAdvance(nfa, buffer, state, timestamps, events, timeoutResults);

        assertTrue(timeoutResults.size() > 0);
        assertTrue(timeoutResults.get(0).f1 > 0);

        nfa.close();
    }

    @Test
    public void testPartialMatchPrunedOnTimeout() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(SimpleCondition.of(event -> event.getId() == 1))
                .next("b")
                .where(SimpleCondition.of(event -> event.getId() == 2))
                .within(Duration.ofMillis(3));

        NFA<Event> nfa = compileNFAWithTimeout(pattern);
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
        NFAState state = nfa.createInitialNFAState();

        List<Event> events = new ArrayList<>();
        events.add(new Event(1, "a1"));
        events.add(new Event(3, "gap"));
        events.add(new Event(2, "b-too-late"));

        List<Long> timestamps = new ArrayList<>();
        timestamps.add(1L);
        timestamps.add(10L);
        timestamps.add(20L);

        List<Tuple2<Map<String, List<Event>>, Long>> timeoutResults = new ArrayList<>();
        List<Map<String, List<Event>>> matches = feedEventsWithAdvance(nfa, buffer, state, timestamps, events, timeoutResults);

        assertTrue(matches.isEmpty());
        assertTrue(timeoutResults.size() > 0);

        nfa.close();
    }
}
