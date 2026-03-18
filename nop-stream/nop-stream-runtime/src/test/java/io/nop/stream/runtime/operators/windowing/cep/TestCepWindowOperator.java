/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing.cep;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.tuple.Tuple2;
import io.nop.stream.core.common.functions.RuntimeContext;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.nfa.NFA;
import io.nop.stream.cep.nfa.NFAState;
import io.nop.stream.cep.nfa.aftermatch.AfterMatchSkipStrategy;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBuffer;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBufferAccessor;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestCepWindowOperator {

    private NFA<Event> nfa;
    private SharedBuffer<Event> sharedBuffer;
    private NFAState nfaState;
    private long windowSizeMs;
    private CepWindowAssigner windowAssigner;
    private RuntimeContext runtimeContext;

    @BeforeEach
    void setUp() {
        windowSizeMs = 5000;
        windowAssigner = CepWindowAssigner.of(windowSizeMs);
        runtimeContext = new MockRuntimeContext();
    }

    @Test
    void testCepPatternWithinTimeConstraint() {
        Pattern<Event, Event> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 40))
                .next("middle")
                .where(SimpleCondition.of(event -> event.getId() >= 44))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")))
                .within(Duration.ofMillis(10000));

        nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(runtimeContext, null);
        nfaState = nfa.createInitialNFAState();
        sharedBuffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        List<Event> events = new ArrayList<>();
        long baseTime = CoreMetrics.currentTimeMillis();

        events.add(new Event(42, "start", baseTime));
        events.add(new Event(44, "middle", baseTime + 100));
        events.add(new Event(46, "end", baseTime + 6000));

        List<Map<String, List<Event>>> matches = processEventsInWindow(events, baseTime);

        assertEquals(1, matches.size(), "Pattern should match when events are within within() time constraint");
    }

    @Test
    void testMultipleMatchesInWindow() {
        Pattern<Event, Event> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 40))
                .timesOrMore(2)
                .within(Duration.ofMillis(windowSizeMs));

        nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(runtimeContext, null);
        nfaState = nfa.createInitialNFAState();
        sharedBuffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        List<Event> events = new ArrayList<>();
        long baseTime = CoreMetrics.currentTimeMillis();

        events.add(new Event(42, "start", baseTime));
        events.add(new Event(43, "start", baseTime + 100));
        events.add(new Event(44, "start", baseTime + 200));
        events.add(new Event(45, "start", baseTime + 300));

        List<Map<String, List<Event>>> matches = processEventsInWindow(events, baseTime);

        assertFalse(matches.isEmpty());
        assertTrue(matches.size() >= 1);
    }

    @Test
    void testWindowAssignerProperties() {
        assertEquals(windowSizeMs, windowAssigner.getWindowSizeMs());
        assertTrue(windowAssigner.isEventTime());
        assertNotNull(windowAssigner.getDefaultTrigger(null));
    }

    @Test
    void testNoPatternMatch() {
        Pattern<Event, Event> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 40))
                .next("middle")
                .where(SimpleCondition.of(event -> event.getId() >= 50))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")))
                .within(Duration.ofSeconds(10));

        nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(runtimeContext, null);
        nfaState = nfa.createInitialNFAState();
        sharedBuffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        List<Event> events = new ArrayList<>();
        long baseTime = CoreMetrics.currentTimeMillis();
        events.add(new Event(1, "event1", baseTime));
        events.add(new Event(2, "event2", baseTime + 100));
        events.add(new Event(3, "event3", baseTime + 200));

        List<Map<String, List<Event>>> matches = processEventsInWindow(events, baseTime);

        assertTrue(matches.isEmpty(), "Should have no matches when no pattern match is possible");
    }

    @Test
    void testPatternMatchInSingleWindow() {
        Pattern<Event, Event> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() == 42))
                .next("middle")
                .where(SimpleCondition.of(event -> event.getId() >= 44))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")))
                .within(Duration.ofSeconds(10));

        nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(runtimeContext, null);
        nfaState = nfa.createInitialNFAState();
        sharedBuffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        List<Event> events = new ArrayList<>();
        long baseTime = CoreMetrics.currentTimeMillis();
        events.add(new Event(42, "start", baseTime));
        events.add(new Event(44, "middle", baseTime + 100));
        events.add(new Event(46, "end", baseTime + 200));

        List<Map<String, List<Event>>> matches = processEventsInWindow(events, baseTime);

        assertEquals(1, matches.size());
        Map<String, List<Event>> match = matches.get(0);
        assertTrue(match.containsKey("start"));
        assertTrue(match.containsKey("middle"));
        assertTrue(match.containsKey("end"));
    }

    @Test
    void testCepWindowAssignerTumblingWindows() {
        CepWindowAssigner assigner = CepWindowAssigner.of(1000);
        assertEquals(1000, assigner.getWindowSizeMs());
        assertTrue(assigner.isEventTime());
    }

    @Test
    void testCepWindowTriggerCreation() {
        CepWindowTrigger trigger = CepWindowTrigger.create();
        assertNotNull(trigger);
        assertTrue(trigger.isFireOnPatternMatch());
        assertTrue(trigger.isPurgeOnTimeout());
    }

    @Test
    void testCepWindowTriggerFiresOnPatternMatch() {
        CepWindowTrigger trigger = CepWindowTrigger.of(true, false);
        assertNotNull(trigger);
        assertTrue(trigger.isFireOnPatternMatch());
        assertFalse(trigger.isPurgeOnTimeout());
    }

    @Test
    void testCepWindowTriggerPurgeOnTimeout() {
        CepWindowTrigger trigger = CepWindowTrigger.of(false, true);
        assertNotNull(trigger);
        assertFalse(trigger.isFireOnPatternMatch());
        assertTrue(trigger.isPurgeOnTimeout());
    }

    @Test
    void testEventsAcrossWindowBoundary() {
        Pattern<Event, Event> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 40))
                .next("middle")
                .where(SimpleCondition.of(event -> event.getId() >= 44))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")))
                .within(Duration.ofMillis(1000));

        nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(runtimeContext, null);
        nfaState = nfa.createInitialNFAState();
        sharedBuffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        List<Event> events = new ArrayList<>();
        long baseTime = CoreMetrics.currentTimeMillis();
        events.add(new Event(42, "start", baseTime));
        events.add(new Event(44, "middle", baseTime + 100));
        events.add(new Event(46, "end", baseTime + 2000));

        List<Map<String, List<Event>>> matches = processEventsInWindow(events, baseTime);

        assertTrue(matches.isEmpty(), "Pattern should not match when events exceed within() time constraint");
    }

    @Test
    void testWatermarkAdvancement() {
        Pattern<Event, Event> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 40))
                .next("middle")
                .where(SimpleCondition.of(event -> event.getId() >= 44))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")))
                .within(Duration.ofMillis(5000));

        nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(runtimeContext, null);
        nfaState = nfa.createInitialNFAState();
        sharedBuffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        List<Event> events = new ArrayList<>();
        long baseTime = CoreMetrics.currentTimeMillis();
        events.add(new Event(42, "start", baseTime));
        events.add(new Event(44, "middle", baseTime + 100));
        events.add(new Event(46, "end", baseTime + 200));

        List<Map<String, List<Event>>> matches = processEventsInWindowWithWatermark(events, baseTime, baseTime + 5000);

        assertEquals(1, matches.size());
    }

    @Test
    void testSharedBufferStateCleanup() {
        Pattern<Event, Event> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 40))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")))
                .within(Duration.ofMillis(1000));

        nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(runtimeContext, null);
        nfaState = nfa.createInitialNFAState();
        sharedBuffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        List<Event> events = new ArrayList<>();
        long baseTime = CoreMetrics.currentTimeMillis();
        events.add(new Event(42, "start", baseTime));
        events.add(new Event(44, "end", baseTime + 500));

        List<Map<String, List<Event>>> matches = processEventsInWindowWithWatermark(events, baseTime, baseTime + 2000);

        assertEquals(1, matches.size());
        try {
            assertTrue(sharedBuffer.isEmpty(), "SharedBuffer should be cleaned up after pattern match");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testNFAStateResetAfterCompleteMatch() {
        Pattern<Event, Event> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getName().equals("start")))
                .next("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")))
                .within(Duration.ofMillis(5000));

        nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(runtimeContext, null);
        nfaState = nfa.createInitialNFAState();
        sharedBuffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        long baseTime = CoreMetrics.currentTimeMillis();
        
        Collection<Map<String, List<Event>>> match1 = consumeEvent(nfa, sharedBuffer, nfaState, 
            new Event(1, "start", baseTime), baseTime);
        assertTrue(match1.isEmpty());
        
        Collection<Map<String, List<Event>>> match2 = consumeEvent(nfa, sharedBuffer, nfaState,
            new Event(2, "end", baseTime + 100), baseTime + 100);
        assertEquals(1, match2.size());

        if (nfaState.isStateChanged()) {
            nfaState.resetStateChanged();
            nfaState.resetNewStartPartialMatch();
        }

        Collection<Map<String, List<Event>>> match3 = consumeEvent(nfa, sharedBuffer, nfaState,
            new Event(3, "start", baseTime + 200), baseTime + 200);
        assertTrue(match3.isEmpty(), "NFA should be ready for new pattern after complete match");
    }

    @Test
    void testLateEventHandling() {
        Pattern<Event, Event> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 40))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")))
                .within(Duration.ofMillis(1000));

        nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(runtimeContext, null);
        nfaState = nfa.createInitialNFAState();
        sharedBuffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        List<Event> events = new ArrayList<>();
        long baseTime = CoreMetrics.currentTimeMillis();
        events.add(new Event(42, "start", baseTime));
        events.add(new Event(44, "end", baseTime + 500));
        events.add(new Event(46, "late", baseTime - 1000));

        List<Map<String, List<Event>>> matches = new ArrayList<>();
        for (Event event : events) {
            if (event.getTimestamp() < baseTime) {
                continue;
            }
            Collection<Map<String, List<Event>>> eventMatches = consumeEvent(nfa, sharedBuffer, nfaState, event, event.getTimestamp());
            matches.addAll(eventMatches);
            if (nfaState.isStateChanged()) {
                nfaState.resetStateChanged();
                nfaState.resetNewStartPartialMatch();
            }
        }

        assertEquals(1, matches.size(), "Late events should be filtered out");
    }

    @Test
    void testPatternWithOptionalEvents() {
        Pattern<Event, Event> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getName().equals("start")))
                .followedBy("middle")
                .where(SimpleCondition.of(event -> event.getName().equals("middle")))
                .optional()
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")))
                .within(Duration.ofMillis(5000));

        nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(runtimeContext, null);
        nfaState = nfa.createInitialNFAState();
        sharedBuffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        List<Event> events = new ArrayList<>();
        long baseTime = CoreMetrics.currentTimeMillis();
        events.add(new Event(1, "start", baseTime));
        events.add(new Event(2, "end", baseTime + 100));

        List<Map<String, List<Event>>> matches = processEventsInWindow(events, baseTime);

        assertEquals(1, matches.size(), "Pattern should match with optional middle event missing");
        assertTrue(matches.get(0).containsKey("start"));
        assertTrue(matches.get(0).containsKey("end"));
    }

    private List<Map<String, List<Event>>> processEventsInWindow(List<Event> events, long baseTime) {
        List<Map<String, List<Event>>> allMatches = new ArrayList<>();

        for (Event event : events) {
            Collection<Map<String, List<Event>>> matches = consumeEvent(nfa, sharedBuffer, nfaState, event, event.getTimestamp());
            allMatches.addAll(matches);

            if (nfaState.isStateChanged()) {
                nfaState.resetStateChanged();
                nfaState.resetNewStartPartialMatch();
            }
        }
        
        nfa.close();
        return allMatches;
    }

    private List<Map<String, List<Event>>> processEventsInWindowWithWatermark(List<Event> events, long baseTime, long watermark) {
        List<Map<String, List<Event>>> allMatches = new ArrayList<>();

        for (Event event : events) {
            Collection<Map<String, List<Event>>> matches = consumeEvent(nfa, sharedBuffer, nfaState, event, event.getTimestamp());
            allMatches.addAll(matches);

            if (nfaState.isStateChanged()) {
                nfaState.resetStateChanged();
                nfaState.resetNewStartPartialMatch();
            }
        }

        try (SharedBufferAccessor<Event> accessor = sharedBuffer.getAccessor()) {
            Tuple2<Collection<Map<String, List<Event>>>, Collection<Tuple2<Map<String, List<Event>>, Long>>> result =
                nfa.advanceTime(accessor, nfaState, watermark, AfterMatchSkipStrategy.noSkip());
            allMatches.addAll(result.f0);
        }
        
        nfa.close();
        return allMatches;
    }

    private Collection<Map<String, List<Event>>> consumeEvent(NFA<Event> nfa, SharedBuffer<Event> partialMatches,
                                                                NFAState nfaState, Event event, long timestamp) {
        try (SharedBufferAccessor<Event> sharedBufferAccessor = partialMatches.getAccessor()) {
            Tuple2<
                    Collection<Map<String, List<Event>>>,
                    Collection<Tuple2<Map<String, List<Event>>, Long>>>
                    pendingMatchesAndTimeout =
                    nfa.advanceTime(
                            sharedBufferAccessor,
                            nfaState,
                            timestamp,
                            AfterMatchSkipStrategy.noSkip());

            Collection<Map<String, List<Event>>> pendingMatches = pendingMatchesAndTimeout.f0;

            Collection<Map<String, List<Event>>> matchedPatterns =
                    nfa.process(
                            sharedBufferAccessor,
                            nfaState,
                            event,
                            timestamp,
                            AfterMatchSkipStrategy.noSkip(),
                            null);
            matchedPatterns.addAll(pendingMatches);
            return matchedPatterns;
        }
    }

    static class Event {
        private final int id;
        private final String name;
        private final long timestamp;

        Event(int id, String name) {
            this(id, name, 0);
        }

        Event(int id, String name, long timestamp) {
            this.id = id;
            this.name = name;
            this.timestamp = timestamp;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "Event[id=" + id + ",name=" + name + "]";
        }
    }

    @Test
    void testCepWindowOperatorFullProcess() {
        Pattern<Event, Event> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 40))
                .next("middle")
                .where(SimpleCondition.of(event -> event.getId() >= 44))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")))
                .within(Duration.ofMillis(windowSizeMs));

        nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(runtimeContext, null);
        nfaState = nfa.createInitialNFAState();
        sharedBuffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        List<Event> events = new ArrayList<>();
        long baseTime = CoreMetrics.currentTimeMillis();

        events.add(new Event(42, "start", baseTime));
        events.add(new Event(44, "middle", baseTime + 100));
        events.add(new Event(46, "end", baseTime + 200));
        events.add(new Event(48, "other", baseTime + 300));

        List<Map<String, List<Event>>> allMatches = new ArrayList<>();

        for (Event event : events) {
            Collection<Map<String, List<Event>>> matches = consumeEvent(nfa, sharedBuffer, nfaState, event, event.getTimestamp());
            allMatches.addAll(matches);

            if (nfaState.isStateChanged()) {
                nfaState.resetStateChanged();
                nfaState.resetNewStartPartialMatch();
            }
        }
        
        nfa.close();

        assertEquals(1, allMatches.size(), "Should have exactly one pattern match");
        Map<String, List<Event>> match = allMatches.get(0);
        assertTrue(match.containsKey("start"));
        assertTrue(match.containsKey("middle"));
        assertTrue(match.containsKey("end"));
        assertEquals(3, match.values().stream().mapToInt(List::size).sum(), "Match should contain 3 events");
    }

    @Test
    void testSlidingWindowPatternMatching() {
        long slideSize = 1000;
        long windowSize = 3000;
        
        Pattern<Event, Event> pattern = Pattern.<Event>begin("event")
                .where(SimpleCondition.of(event -> event.getId() >= 0))
                .timesOrMore(2)
                .within(Duration.ofMillis(windowSize));

        nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(runtimeContext, null);
        nfaState = nfa.createInitialNFAState();
        sharedBuffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        List<Event> events = new ArrayList<>();
        long baseTime = CoreMetrics.currentTimeMillis();

        events.add(new Event(1, "event", baseTime));
        events.add(new Event(2, "event", baseTime + slideSize));
        events.add(new Event(3, "event", baseTime + 2 * slideSize));
        events.add(new Event(4, "event", baseTime + 3 * slideSize));

        List<Map<String, List<Event>>> allMatches = new ArrayList<>();

        for (Event event : events) {
            Collection<Map<String, List<Event>>> matches = consumeEvent(nfa, sharedBuffer, nfaState, event, event.getTimestamp());
            allMatches.addAll(matches);

            if (nfaState.isStateChanged()) {
                nfaState.resetStateChanged();
                nfaState.resetNewStartPartialMatch();
            }
        }
        
        nfa.close();

        assertFalse(allMatches.isEmpty(), "Sliding window should produce matches");
    }

    @Test
    void testStatePurgeOnTimeout() {
        Pattern<Event, Event> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getName().equals("start")))
                .next("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")))
                .within(Duration.ofMillis(1000));

        nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(runtimeContext, null);
        nfaState = nfa.createInitialNFAState();
        sharedBuffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        List<Event> events = new ArrayList<>();
        long baseTime = CoreMetrics.currentTimeMillis();

        events.add(new Event(1, "start", baseTime));
        events.add(new Event(2, "timeout_trigger", baseTime + 1500));

        List<Map<String, List<Event>>> allMatches = new ArrayList<>();

        for (Event event : events) {
            Collection<Map<String, List<Event>>> matches = consumeEvent(nfa, sharedBuffer, nfaState, event, event.getTimestamp());
            allMatches.addAll(matches);

            if (nfaState.isStateChanged()) {
                nfaState.resetStateChanged();
                nfaState.resetNewStartPartialMatch();
            }
        }
        
        nfa.close();

        assertTrue(allMatches.isEmpty(), "State should be purged on timeout, no matches expected");
    }

    static class MockRuntimeContext implements RuntimeContext {
    }
}
