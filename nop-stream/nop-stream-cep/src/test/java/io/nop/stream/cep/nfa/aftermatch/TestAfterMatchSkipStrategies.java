package io.nop.stream.cep.nfa.aftermatch;

import io.nop.stream.cep.nfa.aftermatch.AfterMatchSkipStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestAfterMatchSkipStrategies {

    @Test
    void testSkipPastLastStrategyIsSkipStrategy() {
        assertTrue(AfterMatchSkipStrategy.skipPastLastEvent().isSkipStrategy());
    }

    @Test
    void testSkipPastLastNoPatternName() {
        assertFalse(AfterMatchSkipStrategy.skipPastLastEvent().getPatternName().isPresent());
    }

    @Test
    void testSkipToFirstStrategyHasPatternName() {
        SkipToFirstStrategy strategy = AfterMatchSkipStrategy.skipToFirst("pattern");
        assertTrue(strategy.getPatternName().isPresent());
        assertEquals("pattern", strategy.getPatternName().get());
    }

    @Test
    void testSkipToFirstIsSkipStrategy() {
        assertTrue(AfterMatchSkipStrategy.skipToFirst("p").isSkipStrategy());
    }

    @Test
    void testSkipToFirstThrowExceptionOnMiss() {
        SkipToFirstStrategy strategy = AfterMatchSkipStrategy.skipToFirst("missing");
        SkipToElementStrategy throwing = strategy.throwExceptionOnMiss();
        assertNotNull(throwing);
    }

    @Test
    void testSkipToLastStrategyHasPatternName() {
        SkipToLastStrategy strategy = AfterMatchSkipStrategy.skipToLast("pattern");
        assertTrue(strategy.getPatternName().isPresent());
        assertEquals("pattern", strategy.getPatternName().get());
    }

    @Test
    void testSkipToLastIsSkipStrategy() {
        assertTrue(AfterMatchSkipStrategy.skipToLast("p").isSkipStrategy());
    }

    @Test
    void testSkipToLastThrowExceptionOnMiss() {
        SkipToLastStrategy strategy = AfterMatchSkipStrategy.skipToLast("missing");
        SkipToElementStrategy throwing = strategy.throwExceptionOnMiss();
        assertNotNull(throwing);
    }

    @Test
    void testNoSkipIsNotSkipStrategy() {
        assertFalse(AfterMatchSkipStrategy.noSkip().isSkipStrategy());
    }

    @Test
    void testSkipToNextIsSkipStrategy() {
        assertTrue(AfterMatchSkipStrategy.skipToNext().isSkipStrategy());
    }

    @Test
    void testFactoryMethodSkipPastLastReturnsSingleton() {
        AfterMatchSkipStrategy s1 = AfterMatchSkipStrategy.skipPastLastEvent();
        AfterMatchSkipStrategy s2 = AfterMatchSkipStrategy.skipPastLastEvent();
        assertNotNull(s1);
        assertEquals(s1, s2);
    }
}
