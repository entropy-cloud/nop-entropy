package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class TestNoOpAuditLogger {

    private final NoOpAuditLogger logger = new NoOpAuditLogger();

    @Test
    void testLogDoesNotThrow() {
        AuditEvent event = new AuditEvent("sess-1", "agent-x", null,
                "calculator", AuditDecision.ALLOW, null, null, null, System.currentTimeMillis());
        assertDoesNotThrow(() -> logger.log(event));
    }

    @Test
    void testLogDenyDoesNotThrow() {
        AuditEvent event = new AuditEvent("sess-2", "agent-y", null,
                "bash", AuditDecision.DENY, "denied", "rule1", null, System.currentTimeMillis());
        assertDoesNotThrow(() -> logger.log(event));
    }

    @Test
    void testLogNullEventDoesNotThrow() {
        assertDoesNotThrow(() -> logger.log(null));
    }

    @Test
    void testMultipleLogsDoNotThrow() {
        for (int i = 0; i < 100; i++) {
            AuditEvent event = new AuditEvent("s-" + i, "a", null,
                    "tool-" + i, AuditDecision.ALLOW, null, null, null, System.currentTimeMillis());
            assertDoesNotThrow(() -> logger.log(event));
        }
    }
}
