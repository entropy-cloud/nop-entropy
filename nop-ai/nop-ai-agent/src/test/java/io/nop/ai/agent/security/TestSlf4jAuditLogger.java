package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSlf4jAuditLogger {

    private final Slf4jAuditLogger logger = new Slf4jAuditLogger();

    @Test
    void testLogAllowEvent() {
        AuditEvent event = new AuditEvent("sess-1", "agent-x", null,
                "calculator", AuditDecision.ALLOW, null, "allow_all", null, System.currentTimeMillis());
        assertDoesNotThrow(() -> logger.log(event));
    }

    @Test
    void testLogDenyEvent() {
        AuditEvent event = new AuditEvent("sess-2", "agent-y", null,
                "bash", AuditDecision.DENY, "hardcoded deny", "hardcoded_deny_list", null, System.currentTimeMillis());
        assertDoesNotThrow(() -> logger.log(event));
    }

    @Test
    void testLogWithPath() {
        AuditEvent event = new AuditEvent("sess-3", "agent-z", null,
                "read-file", AuditDecision.DENY, "path denied", "deny_sensitive", "/etc/passwd", System.currentTimeMillis());
        assertDoesNotThrow(() -> logger.log(event));
    }

    @Test
    void testLogNullEvent() {
        assertDoesNotThrow(() -> logger.log(null));
    }

    @Test
    void testLogAllFieldsPresent() {
        AuditEvent event = new AuditEvent("s", "a", "actor-1",
                "tool", AuditDecision.ALLOW, "ok", "rule1", "/path", 1000L);
        assertDoesNotThrow(() -> logger.log(event));
    }
}
