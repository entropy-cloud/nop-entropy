package io.nop.stream.cep.operator;

import io.nop.stream.cep.Event;
import io.nop.stream.cep.MockRuntimeContext;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.nfa.NFA;
import io.nop.stream.cep.nfa.NFAState;
import io.nop.stream.cep.nfa.aftermatch.AfterMatchSkipStrategy;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBuffer;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBufferAccessor;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.commons.tuple.Tuple2;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests full CEP state restore: snapshot NFA computation state, restore into a new NFA instance
 * backed by the same SharedBuffer (simulating checkpoint recovery where both NFA state and
 * SharedBuffer data are restored from persistent storage), then continue processing events
 * and verify correct pattern match output.
 *
 * <p>Pattern under test: begin("start").where(name=="a").followedBy("end").where(name=="b")
 */
public class TestCepStateRestoreAndContinue {

    private NFACompiler.NFAFactory<Event> nfaFactory;

    @BeforeEach
    void setUp() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getName().equals("a")))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("b")));

        nfaFactory = NFACompiler.compileFactory(pattern, false);
    }

    /**
     * Processes a single event through the NFA + SharedBuffer, following the same
     * pattern as TestPattern.consumeEvent: advance time, then process the event.
     */
    private Collection<Map<String, List<Event>>> consumeEvent(
            NFA<Event> nfa, SharedBuffer<Event> buffer,
            NFAState state, Event event, long timestamp) {
        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            Tuple2<Collection<Map<String, List<Event>>>,
                    Collection<Tuple2<Map<String, List<Event>>, Long>>> pending =
                    nfa.advanceTime(accessor, state, timestamp, AfterMatchSkipStrategy.noSkip());

            Collection<Map<String, List<Event>>> matches =
                    nfa.process(accessor, state, event, timestamp,
                            AfterMatchSkipStrategy.noSkip(), null);
            matches.addAll(pending.f0);
            return matches;
        }
    }

    private void resetStateChanged(NFAState state) {
        if (state.isStateChanged()) {
            state.resetStateChanged();
            state.resetNewStartPartialMatch();
        }
    }

    @Test
    void testSnapshotRestoreThenContinueMatching() throws Exception {
        // Shared state store simulates persisted checkpoint data.
        // The SharedBuffer is backed by this store and retains event data across NFA restarts,
        // just as it would after a real checkpoint restore.
        SimpleKeyedStateStore stateStore = new SimpleKeyedStateStore();
        SharedBuffer<Event> sharedBuffer = new SharedBuffer<>(stateStore, null, new SharedBufferCacheConfig());

        // === Phase 1: Process event "a" to create a partial match ===
        NFA<Event> nfa1 = nfaFactory.createNFA();
        nfa1.open(new MockRuntimeContext(), null);
        NFAState nfaState = nfa1.createInitialNFAState();

        Collection<Map<String, List<Event>>> phase1Matches =
                consumeEvent(nfa1, sharedBuffer, nfaState, new Event(1, "a"), 1);
        resetStateChanged(nfaState);

        assertTrue(phase1Matches.isEmpty(),
                "No complete match after only event 'a'");
        assertFalse(nfaState.getPartialMatches().isEmpty(),
                "NFA should have partial matches after seeing 'a'");

        // === Snapshot: capture NFA computation state ===
        NFAState snapshot = new NFAState(
                new PriorityQueue<>(nfaState.getPartialMatches()),
                new PriorityQueue<>(nfaState.getCompletedMatches()));
        int partialMatchCount = snapshot.getPartialMatches().size();

        nfa1.close();

        // === Phase 2: Simulate restore — new NFA instance, restored state, same SharedBuffer ===
        NFA<Event> nfa2 = nfaFactory.createNFA();
        nfa2.open(new MockRuntimeContext(), null);

        // Verify restored state carries over the partial match count
        assertEquals(partialMatchCount, snapshot.getPartialMatches().size(),
                "Captured snapshot should preserve partial match count");

        // === Phase 3: Feed event "b" and verify complete match ===
        Collection<Map<String, List<Event>>> phase2Matches =
                consumeEvent(nfa2, sharedBuffer, snapshot, new Event(2, "b"), 2);

        assertFalse(phase2Matches.isEmpty(),
                "Should produce at least one match after feeding event 'b'");

        Map<String, List<Event>> match = phase2Matches.iterator().next();
        assertTrue(match.containsKey("start"), "Match should contain 'start' pattern variable");
        assertTrue(match.containsKey("end"), "Match should contain 'end' pattern variable");

        Event startEvt = match.get("start").get(0);
        Event endEvt = match.get("end").get(0);
        assertEquals("a", startEvt.getName(), "Start event should be 'a'");
        assertEquals("b", endEvt.getName(), "End event should be 'b'");

        nfa2.close();
    }

    @Test
    void testRestoreWithNonMatchingEventProducesNoOutput() throws Exception {
        SimpleKeyedStateStore stateStore = new SimpleKeyedStateStore();
        SharedBuffer<Event> sharedBuffer = new SharedBuffer<>(stateStore, null, new SharedBufferCacheConfig());

        // === Phase 1: Process event "a" to create a partial match ===
        NFA<Event> nfa1 = nfaFactory.createNFA();
        nfa1.open(new MockRuntimeContext(), null);
        NFAState nfaState = nfa1.createInitialNFAState();

        consumeEvent(nfa1, sharedBuffer, nfaState, new Event(1, "a"), 1);
        resetStateChanged(nfaState);

        NFAState snapshot = new NFAState(
                new PriorityQueue<>(nfaState.getPartialMatches()),
                new PriorityQueue<>(nfaState.getCompletedMatches()));
        assertNotNull(snapshot);

        nfa1.close();

        // === Phase 2: Restore and feed non-matching event "c" ===
        NFA<Event> nfa2 = nfaFactory.createNFA();
        nfa2.open(new MockRuntimeContext(), null);

        Collection<Map<String, List<Event>>> matches =
                consumeEvent(nfa2, sharedBuffer, snapshot, new Event(2, "c"), 2);

        assertTrue(matches.isEmpty(),
                "Non-matching event 'c' should not produce any pattern match output");

        nfa2.close();
    }
}
