package io.nop.ai.agent.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoOpAuditLogger implements IAuditLogger {

    private static final Logger LOG = LoggerFactory.getLogger(NoOpAuditLogger.class);

    @Override
    public void log(AuditEvent event) {
        LOG.trace("audit logging disabled");
    }
}
