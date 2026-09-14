package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
