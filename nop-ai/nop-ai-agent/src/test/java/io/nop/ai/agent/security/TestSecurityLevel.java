package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the {@link SecurityLevel} enum value set and ordering match
 * design §5.1.
 */
public class TestSecurityLevel {

    @Test
    void hasExactlyThreeLevels() {
        SecurityLevel[] values = SecurityLevel.values();
        assertEquals(3, values.length,
                "SecurityLevel must have exactly 3 values per design §5.1");
    }

    @Test
    void valuesMatchDesignSpec() {
        assertEquals(SecurityLevel.STANDARD, SecurityLevel.valueOf("STANDARD"));
        assertEquals(SecurityLevel.ELEVATED, SecurityLevel.valueOf("ELEVATED"));
        assertEquals(SecurityLevel.RESTRICTED, SecurityLevel.valueOf("RESTRICTED"));
    }

    @Test
    void ordinalOrderIsStandardElevatedRestricted() {
        // STANDARD < ELEVATED < RESTRICTED — ascending risk
        assertEquals(0, SecurityLevel.STANDARD.ordinal());
        assertEquals(1, SecurityLevel.ELEVATED.ordinal());
        assertEquals(2, SecurityLevel.RESTRICTED.ordinal());
        assertEquals(-1, SecurityLevel.STANDARD.compareTo(SecurityLevel.ELEVATED));
        assertEquals(-1, SecurityLevel.ELEVATED.compareTo(SecurityLevel.RESTRICTED));
    }
}
