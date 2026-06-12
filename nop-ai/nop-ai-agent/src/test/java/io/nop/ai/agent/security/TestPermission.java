package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPermission {

    @Test
    void testAllowFactory() {
        Permission p = Permission.allow();
        assertTrue(p.isAllowed());
        assertNull(p.getReason());
        assertNull(p.getMatchedRuleId());
    }

    @Test
    void testDenyFactoryWithReason() {
        Permission p = Permission.deny("not authorized");
        assertEquals(false, p.isAllowed());
        assertEquals("not authorized", p.getReason());
        assertNull(p.getMatchedRuleId());
    }

    @Test
    void testDenyFactoryWithReasonAndRuleId() {
        Permission p = Permission.deny("blocked", "rule-1");
        assertEquals(false, p.isAllowed());
        assertEquals("blocked", p.getReason());
        assertEquals("rule-1", p.getMatchedRuleId());
    }

    @Test
    void testEquals() {
        Permission a1 = Permission.allow();
        Permission a2 = Permission.allow();
        assertEquals(a1, a2);

        Permission d1 = Permission.deny("no", "r1");
        Permission d2 = Permission.deny("no", "r1");
        assertEquals(d1, d2);

        assertNotEquals(a1, d1);
        assertNotEquals(Permission.deny("a"), Permission.deny("b"));
    }

    @Test
    void testHashCode() {
        Permission a1 = Permission.allow();
        Permission a2 = Permission.allow();
        assertEquals(a1.hashCode(), a2.hashCode());
    }

    @Test
    void testToString() {
        Permission p = Permission.deny("forbidden", "rule-x");
        String s = p.toString();
        assertNotNull(s);
        assertTrue(s.contains("forbidden"));
        assertTrue(s.contains("rule-x"));
    }
}
