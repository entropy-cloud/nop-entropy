package io.nop.ai.agent.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link IAuditLogger} that writes a single structured audit line per
 * event via SLF4J (design §4.5).
 *
 * <p><b>Log-injection defense</b> (plan 270 finding 13-6): every caller-supplied
 * field is sanitised of {@code \r}/{@code \n} before it is interpolated into
 * the line. The {@code path} and {@code reason} fields are LLM-influenced (they
 * originate from tool-call arguments), so without stripping newlines a hostile
 * or hallucinated value could forge extra log lines and corrupt the audit
 * trail. All fields go through the same {@link #sanitize(String)} guard so the
 * invariant "one event = one log line" holds regardless of which field carries
 * the newline.
 *
 * <p><b>Audit fields</b>: the line carries {@code timestamp} and
 * {@code actorId} (design §4.5) in addition to the existing decision / session
 * / agent / tool / rule / reason / path fields.
 */
public class Slf4jAuditLogger implements IAuditLogger {

    private static final Logger LOG = LoggerFactory.getLogger(Slf4jAuditLogger.class);

    @Override
    public void log(AuditEvent event) {
        if (event == null) {
            return;
        }
        LOG.info(buildMessage(event));
    }

    /**
     * Build the single-line audit message for an event. Package-private so
     * focused tests can assert on the exact sanitised content (newline-free,
     * contains actorId/timestamp) without capturing SLF4J output.
     */
    static String buildMessage(AuditEvent event) {
        return String.format(
                "AUDIT|%d|%s|actor=%s|session=%s|agent=%s|tool=%s|rule=%s|reason=%s|path=%s",
                event.getTimestamp(),
                event.getDecision(),
                sanitize(event.getActorId()),
                sanitize(event.getSessionId()),
                sanitize(event.getAgentName()),
                sanitize(event.getToolName()),
                sanitize(event.getMatchedRule()),
                sanitize(event.getReason()),
                sanitize(event.getPath()));
    }

    /**
     * Strip carriage-return / newline characters so a single audit event can
     * never span (or forge) multiple log lines. {@code null} → empty string.
     */
    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", "").replace("\n", "");
    }
}
