package io.nop.ai.agent.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4jAuditLogger implements IAuditLogger {

    private static final Logger LOG = LoggerFactory.getLogger(Slf4jAuditLogger.class);

    @Override
    public void log(AuditEvent event) {
        if (event == null) {
            return;
        }

        String message = String.format("AUDIT|%s|session=%s|agent=%s|tool=%s|rule=%s|reason=%s|path=%s",
                event.getDecision(),
                nullSafe(event.getSessionId()),
                nullSafe(event.getAgentName()),
                nullSafe(event.getToolName()),
                nullSafe(event.getMatchedRule()),
                nullSafe(event.getReason()),
                nullSafe(event.getPath()));

        LOG.info(message);
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
