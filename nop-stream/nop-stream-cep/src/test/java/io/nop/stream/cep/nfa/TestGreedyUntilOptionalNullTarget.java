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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5-CEP-02 regression (plan 368 Phase 5): the greedy + until + OPTIONAL
 * combination used to compile a PROCEED edge whose target was
 * {@code originalStateMap.get(proceedState.getName())} WITHOUT a miss guard.
 * When the map has no entry for the proceed state (e.g. {@code times(0,1)}
 * normalizes to {@code Times(1,1)} so the greedy copy is never written; or the
 * copy was stored under a renamed {@code copyWithoutTransitiveNots} key), the
 * compiled edge had a NULL target and the runtime decision-graph walk NPE'd
 * the first time the until condition hit — crashing the task on a legal
 * pattern shape. The fix falls back to the (unmutated) proceed state itself.
 */
public class TestGreedyUntilOptionalNullTarget {

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

    /**
     * Minimal trigger (audit variant a): {@code times(0,1)} normalizes to
     * {@code Times(1,1)}, so {@code createTimesState} never writes the greedy
     * copy into {@code originalStateMap} — the until-proceed edge compiled with
     * a null target. The fed event satisfies the UNTIL condition (and fails the
     * where-condition), so the TAKE edge is blocked and the until-hit PROCEED
     * edge is evaluated — the exact point the null target used to NPE.
     */
    @Test
    void timesGreedyUntil_untilHit_mustNotNPE() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .times(0, 1)
                .greedy()
                .until(SimpleCondition.of(e -> e.getName().equals("b")));

        final NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        List<Map<String, List<Event>>> matches = assertDoesNotThrow(
                () -> feedEvents(nfa, buffer, state, List.of(new Event(1, "b"))),
                "until-hit PROCEED on times(0,1).greedy().until must not NPE");

        // The until-blocked event cannot be consumed into a match: no match may
        // reference the pattern's only state.
        assertTrue(matches.stream().noneMatch(m -> m.containsKey("a")),
                "the until-blocked event must not be consumed into a match: " + matches);
        nfa.close();
    }

    /**
     * Audit variant (b): middle-chain {@code oneOrMore().greedy().optional().until}
     * whose ancestors contain a notFollow — the greedy copy is stored under the
     * renamed {@code copyWithoutTransitiveNots} key while the read uses the
     * original proceed-state name (guaranteed miss, null target).
     */
    @Test
    void oneOrMoreGreedyOptionalUntil_afterNotFollow_untilHit_mustNotNPE() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(SimpleCondition.of(e -> e.getName().equals("a")))
                .followedBy("b")
                .where(SimpleCondition.of(e -> e.getName().equals("b")))
                .notFollowedBy("n")
                .where(SimpleCondition.of(e -> e.getName().equals("n")))
                .followedBy("m")
                .where(SimpleCondition.of(e -> e.getName().equals("m")))
                .oneOrMore()
                .greedy()
                .optional()
                .until(SimpleCondition.of(e -> e.getName().equals("u")));

        final NFA<Event> nfa = compileNFA(pattern);
        SharedBuffer<Event> buffer = createBuffer();
        NFAState state = nfa.createInitialNFAState();

        // a, b TAKEn; then an event that hits the until condition — the TAKE of
        // the loop is blocked by NOT(until) and the until-hit PROCEED edge of the
        // optional loop head is evaluated (null target before the fix). The
        // prefix {a,b} partial match still completes through the proceed.
        List<Map<String, List<Event>>> matches = assertDoesNotThrow(
                () -> feedEvents(nfa, buffer, state,
                        List.of(new Event(1, "a"), new Event(2, "b"), new Event(3, "u"))),
                "until-hit PROCEED on oneOrMore().greedy().optional().until after notFollow"
                        + " must not NPE");
        assertTrue(matches.stream().noneMatch(m -> m.containsKey("m") || m.containsKey("u")),
                "the until-blocked event must not be consumed into a match: " + matches);
        assertTrue(matches.stream().anyMatch(m -> m.containsKey("a") && m.containsKey("b")),
                "the {a,b} partial match must still complete through the until-proceed: "
                        + matches);
        nfa.close();
    }
}
