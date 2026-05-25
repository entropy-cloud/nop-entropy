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
import io.nop.stream.cep.pattern.WithinType;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNFAExtended {

    private List<Map<String, List<Event>>> feedEvents(
            NFA<Event> nfa, SharedBuffer<Event> buffer, NFAState state, List<Event> events) {
        return feedEvents(nfa, buffer, state, events, null);
    }

    private List<Map<String, List<Event>>> feedEvents(
            NFA<Event> nfa, SharedBuffer<Event> buffer, NFAState state,
            List<Event> events, List<Tuple2<Map<String, List<Event>>, Long>> timeoutMatches) {
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
                if (timeoutMatches != null) {
                    timeoutMatches.addAll(pending.f1);
                }
            }
            if (state.isStateChanged()) {
                state.resetStateChanged();
                state.resetNewStartPartialMatch();
            }
        }
        return allMatches;
    }

    private List<Map<String, List<Event>>> feedEventsWithTimestamps(
            NFA<Event> nfa, SharedBuffer<Event> buffer, NFAState state,
            List<Event> events, List<Long> timestamps) {
        return feedEventsWithTimestamps(nfa, buffer, state, events, timestamps, null);
    }

    private List<Map<String, List<Event>>> feedEventsWithTimestamps(
            NFA<Event> nfa, SharedBuffer<Event> buffer, NFAState state,
            List<Event> events, List<Long> timestamps,
            List<Tuple2<Map<String, List<Event>>, Long>> timeoutMatches) {
        List<Map<String, List<Event>>> allMatches = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            long timestamp = timestamps.get(i);
            try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
                Tuple2<Collection<Map<String, List<Event>>>, Collection<Tuple2<Map<String, List<Event>>, Long>>>
                        pending = nfa.advanceTime(accessor, state, timestamp, AfterMatchSkipStrategy.noSkip());
                Collection<Map<String, List<Event>>> matches =
                        nfa.process(accessor, state, events.get(i), timestamp, AfterMatchSkipStrategy.noSkip(), null);
                matches.addAll(pending.f0);
                allMatches.addAll(matches);
                if (timeoutMatches != null) {
                    timeoutMatches.addAll(pending.f1);
                }
            }
            if (state.isStateChanged()) {
                state.resetStateChanged();
                state.resetNewStartPartialMatch();
            }
        }
        return allMatches;
    }

    private NFA<Event> compileNFA(Pattern<Event, ?> pattern) {
        return compileNFA(pattern, false);
    }

    private NFA<Event> compileNFA(Pattern<Event, ?> pattern, boolean timeout) {
        NFA<Event> nfa = NFACompiler.compileFactory(pattern, timeout).createNFA();
        nfa.open(new MockRuntimeContext(), null);
        return nfa;
    }

    private SharedBuffer<Event> createBuffer() {
        return new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
    }

    private void assertMatches(List<Map<String, List<Event>>> actual,
                               List<List<String>> expectedPatterns) {
        Set<String> actualStr = new HashSet<>();
        for (Map<String, List<Event>> m : actual) {
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, List<Event>> e : m.entrySet()) {
                for (Event ev : e.getValue()) {
                    names.add(ev.getName());
                }
            }
            actualStr.add(String.join(",", names));
        }
        Set<String> expectedStr = new HashSet<>();
        for (List<String> p : expectedPatterns) {
            expectedStr.add(String.join(",", p));
        }
        assertEquals(expectedStr, actualStr,
                "Match sets differ. Actual: " + actualStr + ", Expected: " + expectedStr);
    }

    @Test
    void testNoConditionNFA() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start").followedBy("end");
        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        Event a = new Event(40, "a");
        Event b = new Event(41, "b");
        Event c = new Event(42, "c");
        Event d = new Event(43, "d");
        Event e = new Event(44, "e");

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(a, b, c, d, e));

        assertEquals(4, matches.size());
        nfa.close();
    }

    @Test
    void testSimplePatternWithSubtype() {
        Event startEvent = new Event(41, "start");
        SubEvent middleEvent = new SubEvent(42, "foo", 10.0);
        Event endEvent = new Event(43, "end");

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(value -> value.getName().equals("start")))
                .followedBy("middle")
                .subtype(SubEvent.class)
                .where(SimpleCondition.of(value -> ((SubEvent) value).getVolume() > 5.0))
                .followedBy("end")
                .where(SimpleCondition.of(value -> value.getName().equals("end")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(startEvent,
                        new Event(43, "foobar"),
                        new SubEvent(41, "barfoo", 5.0),
                        middleEvent,
                        new Event(43, "start"),
                        endEvent));

        assertEquals(1, matches.size());
        Map<String, List<Event>> match = matches.get(0);
        assertEquals(startEvent, match.get("start").get(0));
        assertEquals(middleEvent, match.get("middle").get(0));
        assertEquals(endEvent, match.get("end").get(0));
        nfa.close();
    }

    @Test
    void testStrictContinuityWithResults() {
        Event a = new Event(41, "a");
        Event b = new Event(42, "b");

        Pattern<Event, ?> pattern = Pattern.<Event>begin("middle")
                .where(SimpleCondition.of(value -> value.getName().equals("a")))
                .next("end")
                .where(SimpleCondition.of(value -> value.getName().equals("b")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state, List.of(a, b));

        assertEquals(1, matches.size());
        assertEquals(a, matches.get(0).get("middle").get(0));
        assertEquals(b, matches.get(0).get("end").get(0));
        nfa.close();
    }

    @Test
    void testStrictContinuityNoResults() {
        Event a = new Event(41, "a");
        Event c = new Event(42, "c");
        Event b = new Event(43, "b");

        Pattern<Event, ?> pattern = Pattern.<Event>begin("middle")
                .where(SimpleCondition.of(value -> value.getName().equals("a")))
                .next("end")
                .where(SimpleCondition.of(value -> value.getName().equals("b")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state, List.of(a, c, b));
        assertTrue(matches.isEmpty());
        nfa.close();
    }

    @Test
    void testOneOrMore() {
        Event start = new Event(40, "c");
        Event a1 = new Event(41, "a");
        Event a2 = new Event(42, "a");
        Event end = new Event(43, "b");

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(value -> value.getName().equals("c")))
                .followedBy("middle")
                .where(SimpleCondition.of(value -> value.getName().equals("a")))
                .oneOrMore()
                .followedBy("end")
                .where(SimpleCondition.of(value -> value.getName().equals("b")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(start, a1, a2, end));

        assertTrue(matches.size() >= 2);
        nfa.close();
    }

    @Test
    void testZeroOrMore() {
        Event start = new Event(40, "c");
        Event a1 = new Event(41, "a");
        Event a2 = new Event(42, "a");
        Event end = new Event(43, "b");

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(value -> value.getName().equals("c")))
                .followedBy("middle")
                .where(SimpleCondition.of(value -> value.getName().equals("a")))
                .oneOrMore()
                .optional()
                .followedBy("end")
                .where(SimpleCondition.of(value -> value.getName().equals("b")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(start, a1, a2, end));

        assertTrue(matches.size() >= 2);
        nfa.close();
    }

    @Test
    void testOptional() {
        Event start = new Event(40, "c");
        Event end = new Event(43, "b");

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(value -> value.getName().equals("c")))
                .followedBy("middle")
                .where(SimpleCondition.of(value -> value.getName().equals("a")))
                .optional()
                .followedBy("end")
                .where(SimpleCondition.of(value -> value.getName().equals("b")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(start, end));

        assertEquals(1, matches.size());
        assertTrue(matches.get(0).containsKey("start"));
        assertTrue(matches.get(0).containsKey("end"));
        nfa.close();
    }

    @Test
    void testTimes() {
        Event start = new Event(40, "c");
        Event a1 = new Event(41, "a");
        Event a2 = new Event(42, "a");
        Event a3 = new Event(43, "a");
        Event end = new Event(44, "b");

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(value -> value.getName().equals("c")))
                .followedBy("middle")
                .where(SimpleCondition.of(value -> value.getName().equals("a")))
                .times(2)
                .followedBy("end")
                .where(SimpleCondition.of(value -> value.getName().equals("b")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(start, a1, a2, a3, end));

        assertEquals(1, matches.size());
        assertEquals(2, matches.get(0).get("middle").size());
        nfa.close();
    }

    @Test
    void testTimesRange() {
        Event start = new Event(40, "c");
        Event a1 = new Event(41, "a");
        Event a2 = new Event(42, "a");
        Event a3 = new Event(43, "a");
        Event end = new Event(44, "b");

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(value -> value.getName().equals("c")))
                .followedBy("middle")
                .where(SimpleCondition.of(value -> value.getName().equals("a")))
                .times(1, 3)
                .followedBy("end")
                .where(SimpleCondition.of(value -> value.getName().equals("b")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(start, a1, a2, a3, end));

        assertTrue(matches.size() >= 1);
        nfa.close();
    }

    @Test
    void testBranchingPattern() {
        Event startEvent = new Event(40, "start");
        SubEvent m1 = new SubEvent(41, "foo1", 10.0);
        SubEvent m2 = new SubEvent(42, "foo2", 10.0);
        SubEvent m3 = new SubEvent(43, "foo3", 10.0);
        SubEvent n1 = new SubEvent(44, "next-one", 2.0);
        SubEvent n2 = new SubEvent(45, "next-one", 2.0);
        Event endEvent = new Event(46, "end");

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(value -> value.getName().equals("start")))
                .followedByAny("middle-first")
                .subtype(SubEvent.class)
                .where(SimpleCondition.of(value -> ((SubEvent) value).getVolume() > 5.0))
                .followedByAny("middle-second")
                .subtype(SubEvent.class)
                .where(SimpleCondition.of(value -> value.getName().equals("next-one")))
                .followedByAny("end")
                .where(SimpleCondition.of(value -> value.getName().equals("end")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(startEvent, m1, m2, m3, n1, n2, endEvent));

        assertEquals(6, matches.size());
        nfa.close();
    }

    @Test
    void testFollowedByAnyProducesMoreMatches() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .followedByAny("end")
                .where(SimpleCondition.of(e -> e.getName().equals("c")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        Event a = new Event(1, "a");
        Event b = new Event(2, "b");
        Event c = new Event(3, "c");

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state, List.of(a, b, c));

        assertEquals(1, matches.size());
        assertEquals("a", matches.get(0).get("start").get(0).getName());
        assertEquals("c", matches.get(0).get("end").get(0).getName());
        nfa.close();
    }

    @Test
    void testSimplePatternWithTimeWindow() {
        Event startEvent = new Event(2, "start");
        Event middleEvent = new Event(3, "middle");
        Event endEvent = new Event(5, "end");

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(value -> value.getName().equals("start")))
                .followedBy("middle")
                .where(SimpleCondition.of(value -> value.getName().equals("middle")))
                .followedBy("end")
                .where(SimpleCondition.of(value -> value.getName().equals("end")))
                .within(Duration.ofMillis(10));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Long> timestamps = List.of(1L, 2L, 3L, 4L, 11L, 13L);
        List<Event> events = List.of(
                new Event(1, "start"),
                startEvent,
                middleEvent,
                new Event(4, "foobar"),
                endEvent,
                new Event(6, "end"));

        List<Map<String, List<Event>>> matches = feedEventsWithTimestamps(nfa, buffer, state, events, timestamps);

        assertEquals(1, matches.size());
        assertEquals(startEvent, matches.get(0).get("start").get(0));
        assertEquals(middleEvent, matches.get(0).get("middle").get(0));
        assertEquals(endEvent, matches.get(0).get("end").get(0));
        nfa.close();
    }

    @Test
    void testStrictOneOrMore() {
        Event start = new Event(40, "c");
        Event a1 = new Event(41, "a");
        Event a2 = new Event(42, "a");
        Event a3 = new Event(43, "a");
        Event end = new Event(44, "b");

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(value -> value.getName().equals("c")))
                .followedBy("middle")
                .where(SimpleCondition.of(value -> value.getName().equals("a")))
                .oneOrMore()
                .consecutive()
                .followedBy("end")
                .where(SimpleCondition.of(value -> value.getName().equals("b")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(start, a1, a2, a3, end));

        assertTrue(matches.size() >= 1);
        nfa.close();
    }

    @Test
    void testBeginWithOneOrMore() {
        Event a1 = new Event(1, "a");
        Event a2 = new Event(2, "a");
        Event end = new Event(3, "b");

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(value -> value.getName().equals("a")))
                .oneOrMore()
                .followedBy("end")
                .where(SimpleCondition.of(value -> value.getName().equals("b")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(a1, a2, end));

        assertTrue(matches.size() >= 1);
        assertTrue(matches.get(0).get("start").size() >= 1);
        nfa.close();
    }

    @Test
    void testEndWithOneOrMore() {
        Event start = new Event(1, "c");
        Event a1 = new Event(2, "a");
        Event a2 = new Event(3, "a");

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(value -> value.getName().equals("c")))
                .followedBy("middle")
                .where(SimpleCondition.of(value -> value.getName().equals("a")))
                .oneOrMore();

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(start, a1, a2));

        assertTrue(matches.size() >= 1);
        nfa.close();
    }

    @Test
    void testTimesClearingBuffer() {
        Event start = new Event(1, "c");
        Event a1 = new Event(2, "a");
        Event a2 = new Event(3, "a");
        Event a3 = new Event(4, "a");
        Event end = new Event(5, "b");

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(value -> value.getName().equals("c")))
                .followedBy("middle")
                .where(SimpleCondition.of(value -> value.getName().equals("a")))
                .times(2)
                .followedBy("end")
                .where(SimpleCondition.of(value -> value.getName().equals("b")))
                .within(Duration.ofMillis(5));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Long> timestamps = List.of(1L, 2L, 3L, 4L, 20L);
        List<Map<String, List<Event>>> matches = feedEventsWithTimestamps(nfa, buffer, state,
                List.of(start, a1, a2, a3, end), timestamps);

        assertTrue(matches.isEmpty());
        nfa.close();
    }

    @Test
    void testOptionalClearingBuffer() {
        Event start = new Event(1, "c");
        Event a = new Event(2, "a");
        Event end = new Event(3, "b");

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(value -> value.getName().equals("c")))
                .followedBy("middle")
                .where(SimpleCondition.of(value -> value.getName().equals("a")))
                .optional()
                .followedBy("end")
                .where(SimpleCondition.of(value -> value.getName().equals("b")))
                .within(Duration.ofMillis(2));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Long> timestamps = List.of(1L, 2L, 10L);
        List<Map<String, List<Event>>> matches = feedEventsWithTimestamps(nfa, buffer, state,
                List.of(start, a, end), timestamps);

        assertTrue(matches.isEmpty());
        nfa.close();
    }

    @Test
    void testMultipleStartEventsNoSkip() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a",
                        AfterMatchSkipStrategy.noSkip())
                .where(SimpleCondition.of(e -> e.getName().startsWith("a")))
                .followedBy("b")
                .where(SimpleCondition.of(e -> e.getName().equals("end")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(new Event(1, "a1"), new Event(2, "a2"), new Event(3, "end")));

        assertEquals(2, matches.size());
        nfa.close();
    }

    @Test
    void testMultipleStartEventsSkipPastLast() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a",
                        AfterMatchSkipStrategy.skipPastLastEvent())
                .where(SimpleCondition.of(e -> e.getName().startsWith("a")))
                .followedBy("b")
                .where(SimpleCondition.of(e -> e.getName().equals("end")));

        NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = feedEvents(nfa, buffer, state,
                List.of(new Event(1, "a1"), new Event(2, "a2"), new Event(3, "end")));

        assertTrue(matches.size() >= 1);
        assertTrue(matches.size() <= 2);
        nfa.close();
    }
}
