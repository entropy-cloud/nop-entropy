package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 1 unit tests for {@link PathAccessDecision}: the two-valued allow/deny
 * enum for per-agent glob path-rules (design §4.3).
 */
public class TestPathAccessDecision {

    @Test
    void enumHasAllowAndDenyValues() {
        assertEquals(2, PathAccessDecision.values().length);
        assertEquals(PathAccessDecision.ALLOW, PathAccessDecision.valueOf("ALLOW"));
        assertEquals(PathAccessDecision.DENY, PathAccessDecision.valueOf("DENY"));
    }

    @Test
    void fromStringParsesAllowCaseInsensitive() {
        assertEquals(PathAccessDecision.ALLOW, PathAccessDecision.fromString("allow"));
        assertEquals(PathAccessDecision.ALLOW, PathAccessDecision.fromString("ALLOW"));
        assertEquals(PathAccessDecision.ALLOW, PathAccessDecision.fromString("Allow"));
        assertEquals(PathAccessDecision.ALLOW, PathAccessDecision.fromString(" allow "));
    }

    @Test
    void fromStringParsesDenyCaseInsensitive() {
        assertEquals(PathAccessDecision.DENY, PathAccessDecision.fromString("deny"));
        assertEquals(PathAccessDecision.DENY, PathAccessDecision.fromString("DENY"));
        assertEquals(PathAccessDecision.DENY, PathAccessDecision.fromString("Deny"));
        assertEquals(PathAccessDecision.DENY, PathAccessDecision.fromString(" deny "));
    }

    @Test
    void fromStringDefaultsToDenyForNull() {
        assertEquals(PathAccessDecision.DENY, PathAccessDecision.fromString(null),
                "null input must default to DENY (fail-closed)");
    }

    @Test
    void fromStringDefaultsToDenyForUnrecognized() {
        assertEquals(PathAccessDecision.DENY, PathAccessDecision.fromString("read-write"),
                "Unrecognized input must default to DENY (fail-closed)");
        assertEquals(PathAccessDecision.DENY, PathAccessDecision.fromString(""),
                "Blank input must default to DENY (fail-closed)");
        assertEquals(PathAccessDecision.DENY, PathAccessDecision.fromString("garbage"),
                "Garbage input must default to DENY (fail-closed)");
    }
}
