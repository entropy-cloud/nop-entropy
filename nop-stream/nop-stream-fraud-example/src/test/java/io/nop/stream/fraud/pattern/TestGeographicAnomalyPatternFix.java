package io.nop.stream.fraud.pattern;

import io.nop.stream.cep.nfa.NFA;
import io.nop.stream.cep.nfa.NFAState;
import io.nop.stream.cep.nfa.aftermatch.AfterMatchSkipStrategy;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBuffer;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBufferAccessor;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.fraud.model.TransactionEvent;
import io.nop.stream.fraud.state.DemoKeyedStateStore;
import io.nop.commons.tuple.Tuple2;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Behavior pinning for the geographic anomaly pattern on multi-user streams, driven
 * through the REAL pattern class + NFA engine (the previous version of this test
 * duplicated the condition body inline, so a regression in the real class could not
 * be caught).
 *
 * <p>Engine semantics pinned here: {@code getEventsForPattern} materializes the
 * current partial-match branch (not all buffered events), and {@code next()} is
 * strict contiguity — an interleaved event from another user breaks the chain.
 */
class TestGeographicAnomalyPatternFix {

    private static TransactionEvent tx(String id, String userId, double amount, String city, long ts) {
        return new TransactionEvent(id, userId, BigDecimal.valueOf(amount), city, ts, "TRANSFER");
    }

    /**
     * A contiguous same-user CityA→CityB pair matches even though another user's
     * partial match is present in the shared buffer.
     */
    @Test
    void contiguousPairMatchesDespiteOtherUserPartial() throws Exception {
        long base = System.currentTimeMillis();
        List<TransactionEvent> events = List.of(
                tx("t0", "user2", 50.0, "CityA", base),
                tx("t1", "user1", 50.0, "CityA", base + 1000),
                tx("t2", "user1", 100.0, "CityB", base + 2000));

        Collection<Map<String, List<TransactionEvent>>> matches = runPattern(events);

        assertEquals(1, matches.size(), "the contiguous user1 CityA→CityB pair must match");
        Map<String, List<TransactionEvent>> match = matches.iterator().next();
        assertEquals("user1", match.get("city1").get(0).getUserId());
        assertEquals("CityB", match.get("city2").get(0).getCity());
    }

    /**
     * Strict contiguity: a different user's event between the two same-user events
     * breaks the chain — the anomaly must NOT be reported.
     */
    @Test
    void interleavedPairDoesNotMatchUnderStrictContiguity() throws Exception {
        long base = System.currentTimeMillis();
        List<TransactionEvent> events = List.of(
                tx("t0", "user1", 50.0, "CityA", base),
                tx("t1", "user2", 50.0, "CityA", base + 1000),
                tx("t2", "user1", 100.0, "CityB", base + 2000));

        Collection<Map<String, List<TransactionEvent>>> matches = runPattern(events);

        assertFalse(matches.iterator().hasNext(),
                "an interleaved other-user event breaks strict contiguity — no anomaly");
    }

    private static Collection<Map<String, List<TransactionEvent>>> runPattern(
            List<TransactionEvent> events) throws Exception {
        Pattern<TransactionEvent, ?> pattern = GeographicAnomalyPattern.createPattern();
        NFA<TransactionEvent> nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(null, null);

        NFAState nfaState = nfa.createInitialNFAState();
        SharedBuffer<TransactionEvent> buffer = new SharedBuffer<>(
                new DemoKeyedStateStore(), null, new SharedBufferCacheConfig());

        Collection<Map<String, List<TransactionEvent>>> allMatches = new ArrayList<>();
        for (TransactionEvent event : events) {
            try (SharedBufferAccessor<TransactionEvent> accessor = buffer.getAccessor()) {
                Tuple2<
                        Collection<Map<String, List<TransactionEvent>>>,
                        Collection<Tuple2<Map<String, List<TransactionEvent>>, Long>>>
                        pending = nfa.advanceTime(accessor, nfaState, event.getTimestamp(),
                        AfterMatchSkipStrategy.noSkip());
                Collection<Map<String, List<TransactionEvent>>> matched = nfa.process(
                        accessor, nfaState, event, event.getTimestamp(),
                        AfterMatchSkipStrategy.noSkip(), null);
                matched.addAll(pending.f0);
                allMatches.addAll(matched);
            }
            if (nfaState.isStateChanged()) {
                nfaState.resetStateChanged();
                nfaState.resetNewStartPartialMatch();
            }
        }
        nfa.close();
        return allMatches;
    }
}
