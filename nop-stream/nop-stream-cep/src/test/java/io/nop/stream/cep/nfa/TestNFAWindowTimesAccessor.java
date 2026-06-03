package io.nop.stream.cep.nfa;

import io.nop.stream.cep.Event;
import io.nop.stream.cep.MockRuntimeContext;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBuffer;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestNFAWindowTimesAccessor {

    @Test
    void testGetWindowTimesReturnsEmptyMapForSimplePattern() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(SimpleCondition.of(event -> event.getId() == 1))
                .next("b")
                .where(SimpleCondition.of(event -> event.getId() == 2));

        NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();
        nfa.open(new MockRuntimeContext(), null);

        assertTrue(nfa.getWindowTimes().isEmpty());
        assertEquals(0, nfa.getWindowTime());
    }

    @Test
    void testGetWindowTimesReturnsGlobalWindowTime() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(SimpleCondition.of(event -> event.getId() == 1))
                .next("b")
                .where(SimpleCondition.of(event -> event.getId() == 2))
                .within(Duration.ofMillis(100));

        NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();
        nfa.open(new MockRuntimeContext(), null);

        assertEquals(100, nfa.getWindowTime());
    }

    @Test
    void testGetWindowTimesReturnsPerStateWindowTime() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(SimpleCondition.of(event -> event.getId() == 1))
                .oneOrMore(Duration.ofMillis(50))
                .next("b")
                .where(SimpleCondition.of(event -> event.getId() == 2));

        NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();
        nfa.open(new MockRuntimeContext(), null);

        Map<String, Long> windowTimes = nfa.getWindowTimes();
        assertFalse(windowTimes.isEmpty());
        boolean hasPerStateWindow = windowTimes.values().stream().anyMatch(t -> t == 50);
        assertTrue(hasPerStateWindow,
                "Per-state window time should be recorded for 'a' oneOrMore pattern. Actual keys: " + windowTimes.keySet());
    }

    @Test
    void testGetWindowTimesWithBothGlobalAndPerState() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("a")
                .where(SimpleCondition.of(event -> event.getId() == 1))
                .oneOrMore(Duration.ofMillis(30))
                .next("b")
                .where(SimpleCondition.of(event -> event.getId() == 2))
                .within(Duration.ofMillis(100));

        NFA<Event> nfa = NFACompiler.compileFactory(pattern, false).createNFA();
        nfa.open(new MockRuntimeContext(), null);

        assertEquals(100, nfa.getWindowTime());
        Map<String, Long> windowTimes = nfa.getWindowTimes();
        boolean hasPerStateWindow = windowTimes.values().stream().anyMatch(t -> t == 30);
        assertTrue(hasPerStateWindow, "Should have per-state window time of 30ms. Actual: " + windowTimes);
    }
}
