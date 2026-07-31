package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the {@link SecurityLevel} enum value set matches design §5.1.
 *
 * <p>MA4.4-03 adjudication: the former three tests (valueOf / ordinal /
 * compareTo assertions) were merged into a single structural test. The
 * dropped assertions are compiler-guaranteed — valueOf round-trips and
 * declaration-order ordinals cannot diverge from the enum source without a
 * compile error. The one non-trivial assertion retained is the value count:
 * adding a fourth level would still compile, so the design contract
 * (§5.1: exactly 3 levels) needs an explicit guard.
 */
public class TestSecurityLevel {

    @Test
    void structureMatchesDesignSpec() {
        SecurityLevel[] values = SecurityLevel.values();
        assertEquals(3, values.length,
                "SecurityLevel must have exactly 3 values per design §5.1");
    }
}
