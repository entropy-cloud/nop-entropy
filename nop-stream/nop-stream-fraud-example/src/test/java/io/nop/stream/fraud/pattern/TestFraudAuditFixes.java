package io.nop.stream.fraud.pattern;

import io.nop.stream.cep.nfa.NFA;
import io.nop.stream.cep.nfa.NFAState;
import io.nop.stream.cep.nfa.aftermatch.AfterMatchSkipStrategy;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBuffer;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBufferAccessor;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.fraud.FraudDetectionDemo;
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
 * Focused regression tests for the item 11 audit fixes (roadmap item 11,
 * fraud-example side):
 * <ul>
 *   <li>FX-1: the RapidTransaction / AccountTakeover "same user" conditions now scan
 *       the whole iterable (copy-template alignment with the fixed
 *       GeographicAnomalyPattern idiom — under the engine's branch-local
 *       {@code getEventsForPattern} semantics the two forms are behaviorally
 *       equivalent for these linear patterns; the early-return form diverges on
 *       multi-element iterables such as quantifier patterns). These tests pin the
 *       multi-user stream behavior through the real NFA engine (the demo's path).</li>
 *   <li>FX-2: {@code FraudDetectionDemo.main} happy path runs cleanly end to end
 *       (demo wiring smoke test; main now fails loudly on error).</li>
 * </ul>
 */
class TestFraudAuditFixes {

    private static TransactionEvent tx(String id, String userId, double amount, String city,
                                       long ts, String eventType) {
        return new TransactionEvent(id, userId, BigDecimal.valueOf(amount), city, ts, eventType);
    }

    /**
     * user2's partial match precedes user1's in arrival order; user1's contiguous
     * pair must still match (behavior pinning on a multi-user stream).
     */
    @Test
    void rapidTransactionMatchesUserPairWithOtherUserPartialFirst() throws Exception {
        long base = System.currentTimeMillis();
        List<TransactionEvent> events = List.of(
                tx("t0", "user2", 2000.0, "CityA", base, "PURCHASE"),
                tx("t1", "user1", 2000.0, "CityA", base + 1000, "PURCHASE"),
                tx("t2", "user1", 2000.0, "CityB", base + 2000, "PURCHASE"));

        Collection<Map<String, List<TransactionEvent>>> matches =
                run(RapidTransactionPattern.createPattern(), events);

        assertEquals(1, matches.size(), "user1's rapid pair must match although user2's partial "
                + "match is buffered first");
        Map<String, List<TransactionEvent>> match = matches.iterator().next();
        assertEquals("user1", match.get("second").get(0).getUserId());
    }

    /**
     * Cross-user sequence must NOT be reported as a rapid pair.
     */
    @Test
    void rapidTransactionDoesNotMatchAcrossUsers() throws Exception {
        long base = System.currentTimeMillis();
        List<TransactionEvent> events = List.of(
                tx("t0", "user1", 2000.0, "CityA", base, "PURCHASE"),
                tx("t1", "user2", 2000.0, "CityA", base + 1000, "PURCHASE"));

        Collection<Map<String, List<TransactionEvent>>> matches =
                run(RapidTransactionPattern.createPattern(), events);

        assertFalse(matches.iterator().hasNext(), "user2's large transaction must not pair with user1's");
    }

    /**
     * user2's LOGIN partial match arrives before user1's chain; the contiguous
     * login→change→withdraw sequence for user1 must complete (behavior pinning on a
     * multi-user stream).
     */
    @Test
    void accountTakeoverCompletesUserChainWithOtherUserPartialFirst() throws Exception {
        long base = System.currentTimeMillis();
        List<TransactionEvent> events = List.of(
                tx("t0", "user2", 0.0, "CityA", base, "LOGIN"),
                tx("t1", "user1", 0.0, "CityA", base + 1000, "LOGIN"),
                tx("t2", "user1", 0.0, "CityA", base + 2000, "CHANGE_PASSWORD"),
                tx("t3", "user1", 500.0, "CityA", base + 3000, "WITHDRAWAL"));

        Collection<Map<String, List<TransactionEvent>>> matches =
                run(AccountTakeoverPattern.createPattern(), events);

        assertEquals(1, matches.size(), "user1's login→change→withdraw chain must match although "
                + "user2's LOGIN partial match is buffered first");
        Map<String, List<TransactionEvent>> match = matches.iterator().next();
        assertEquals("user1", match.get("withdraw").get(0).getUserId());
    }

    /**
     * FX-2 smoke test: the demo entry point runs all four patterns end to end on mock
     * data without throwing (the demo previously swallowed every failure and exited 0).
     */
    @Test
    void demoMainHappyPathRunsAllPatterns() {
        FraudDetectionDemo.main(new String[0]);
    }

    private static Collection<Map<String, List<TransactionEvent>>> run(
            Pattern<TransactionEvent, ?> pattern, List<TransactionEvent> events) throws Exception {
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
