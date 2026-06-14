package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@link LevelHints} value object immutability, value equality,
 * and the {@link LevelHints#defaults()} factory.
 */
public class TestLevelHints {

    @Test
    void fieldsAreStoredAndAccessible() {
        LevelHints hints = new LevelHints(true, false, true, false, true);
        assertTrue(hints.isTrustedSource());
        assertFalse(hints.isWritesOutsideWorkspace());
        assertTrue(hints.isCrossesTrustBoundary());
        assertFalse(hints.isNeedsNetwork());
        assertTrue(hints.isHighImpact());
    }

    @Test
    void defaultsReturnsAllFalse() {
        LevelHints hints = LevelHints.defaults();
        assertFalse(hints.isTrustedSource());
        assertFalse(hints.isWritesOutsideWorkspace());
        assertFalse(hints.isCrossesTrustBoundary());
        assertFalse(hints.isNeedsNetwork());
        assertFalse(hints.isHighImpact());
    }

    @Test
    void equalsAndHashCodeByValue() {
        LevelHints a = new LevelHints(true, false, true, false, true);
        LevelHints b = new LevelHints(true, false, true, false, true);
        LevelHints c = new LevelHints(false, false, true, false, true);
        LevelHints d = new LevelHints(true, true, true, false, true);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, d);
        assertNotEquals(a, null);
        assertNotEquals(a, "not-level-hints");
    }

    @Test
    void defaultsEqualsNewAllFalse() {
        LevelHints defaults = LevelHints.defaults();
        LevelHints manual = new LevelHints(false, false, false, false, false);
        assertEquals(defaults, manual);
        assertEquals(defaults.hashCode(), manual.hashCode());
    }

    @Test
    void toStringContainsAllFields() {
        LevelHints hints = new LevelHints(true, true, false, false, true);
        String s = hints.toString();
        assertTrue(s.contains("trustedSource=true"));
        assertTrue(s.contains("writesOutsideWorkspace=true"));
        assertTrue(s.contains("crossesTrustBoundary=false"));
        assertTrue(s.contains("needsNetwork=false"));
        assertTrue(s.contains("highImpact=true"));
    }
}
