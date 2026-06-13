package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAuditEvent {

    @Test
    void testConstructionWithAllFields() {
        long ts = System.currentTimeMillis();
        AuditEvent event = new AuditEvent("sess-1", "agent-x", "actor-1",
                "calculator", AuditDecision.ALLOW, null, "allow_all", null, ts);

        assertEquals("sess-1", event.getSessionId());
        assertEquals("agent-x", event.getAgentName());
        assertEquals("actor-1", event.getActorId());
        assertEquals("calculator", event.getToolName());
        assertEquals(AuditDecision.ALLOW, event.getDecision());
        assertNull(event.getReason());
        assertEquals("allow_all", event.getMatchedRule());
        assertNull(event.getPath());
        assertEquals(ts, event.getTimestamp());
    }

    @Test
    void testDenyEvent() {
        long ts = System.currentTimeMillis();
        AuditEvent event = new AuditEvent("sess-2", "agent-y", null,
                "bash", AuditDecision.DENY, "hardcoded deny", "hardcoded_deny_list", null, ts);

        assertEquals("sess-2", event.getSessionId());
        assertNull(event.getActorId());
        assertEquals(AuditDecision.DENY, event.getDecision());
        assertEquals("hardcoded deny", event.getReason());
    }

    @Test
    void testWithPathVariable() {
        long ts = System.currentTimeMillis();
        AuditEvent event = new AuditEvent("sess-3", "agent-z", null,
                "read-file", AuditDecision.DENY, "path denied", "deny_sensitive", "/etc/passwd", ts);

        assertEquals("/etc/passwd", event.getPath());
    }

    @Test
    void testEquality() {
        long ts = 1000L;
        AuditEvent a = new AuditEvent("s", "a", null, "t", AuditDecision.ALLOW, null, null, null, ts);
        AuditEvent b = new AuditEvent("s", "a", null, "t", AuditDecision.ALLOW, null, null, null, ts);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void testInequality() {
        long ts = 1000L;
        AuditEvent a = new AuditEvent("s", "a", null, "t", AuditDecision.ALLOW, null, null, null, ts);
        AuditEvent b = new AuditEvent("s", "a", null, "t", AuditDecision.DENY, null, null, null, ts);

        assertFalse(a.equals(b));
    }

    @Test
    void testToString() {
        long ts = 1000L;
        AuditEvent event = new AuditEvent("s1", "ag1", null, "tool1",
                AuditDecision.DENY, "not allowed", "rule1", "/tmp/file", ts);

        String str = event.toString();
        assertTrue(str.contains("s1"));
        assertTrue(str.contains("ag1"));
        assertTrue(str.contains("tool1"));
        assertTrue(str.contains("DENY"));
        assertTrue(str.contains("not allowed"));
        assertTrue(str.contains("rule1"));
        assertTrue(str.contains("/tmp/file"));
        assertTrue(str.contains("1000"));
    }

    @Test
    void testImmutability() {
        AuditEvent event = new AuditEvent("s", "a", "act", "t",
                AuditDecision.ALLOW, "r", "rule", "/p", 1L);

        assertEquals("s", event.getSessionId());
        assertEquals("a", event.getAgentName());
        assertEquals("act", event.getActorId());
        assertEquals("t", event.getToolName());
        assertEquals(AuditDecision.ALLOW, event.getDecision());
        assertEquals("r", event.getReason());
        assertEquals("rule", event.getMatchedRule());
        assertEquals("/p", event.getPath());
        assertEquals(1L, event.getTimestamp());
    }

    @Test
    void testNullableActorId() {
        AuditEvent event = new AuditEvent("s", "a", null, "t",
                AuditDecision.ALLOW, null, null, null, 0L);
        assertNull(event.getActorId());
    }
}
