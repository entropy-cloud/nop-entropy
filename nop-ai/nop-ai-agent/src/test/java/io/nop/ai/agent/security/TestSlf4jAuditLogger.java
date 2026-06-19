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

    // ========================================================================
    // Plan 270 finding 13-6: log-injection defense + actorId/timestamp fields
    // ========================================================================

    /**
     * No-Silent-No-Op (#24): newlines injected into an LLM-influenced field
     * (path / reason) must be stripped, so one audit event always produces
     * exactly one log line — a hostile/hallucinated value cannot forge extra
     * lines. Asserts on {@link Slf4jAuditLogger#buildMessage} directly (no
     * SLF4J output capture needed).
     */
    @Test
    void buildMessageStripsNewlinesFromPathAndReason() {
        // path and reason carry CR/LF in multiple positions.
        AuditEvent event = new AuditEvent("sess", "agent", "actor",
                "tool", AuditDecision.DENY,
                "denied\r\nsecond-forged-line\n",
                "rule",
                "/tmp/x\r\nforged-path",
                42L);

        String message = Slf4jAuditLogger.buildMessage(event);

        assertFalse(message.contains("\n"),
                "audit line must contain no '\\n' after sanitising; got: " + message);
        assertFalse(message.contains("\r"),
                "audit line must contain no '\\r' after sanitising; got: " + message);
        // The core defense: one event always produces exactly ONE line. A log
        // appender writing a line per message can never be tricked into
        // emitting the forged content as a separate (forged) audit line.
        assertEquals(1, message.lines().count(),
                "one audit event must collapse to exactly one line; got: " + message);
        // Content is preserved (no silent data loss) — only the separators are
        // stripped, so "second-forged-line" can no longer start a new line.
        assertTrue(message.contains("reason=deniedsecond-forged-line"),
                "content is preserved with separators stripped; got: " + message);
    }

    /**
     * The audit line must carry the design §4.5 audit fields: the numeric
     * timestamp and the actorId (plan 270 finding 13-6).
     */
    @Test
    void buildMessageIncludesTimestampAndActorId() {
        AuditEvent event = new AuditEvent("sess", "agent", "actor-99",
                "tool", AuditDecision.ALLOW, "ok", "rule1", "/path", 1700000000000L);

        String message = Slf4jAuditLogger.buildMessage(event);

        assertTrue(message.contains("1700000000000"),
                "audit line must embed the numeric timestamp; got: " + message);
        assertTrue(message.contains("actor=actor-99"),
                "audit line must embed actorId; got: " + message);
        // The canonical AUDIT|<ts>|<decision> prefix must be present.
        assertTrue(message.startsWith("AUDIT|1700000000000|ALLOW|"),
                "audit line must start with AUDIT|<timestamp>|<decision>|; got: " + message);
    }
}

