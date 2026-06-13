package io.nop.ai.agent.security;

public interface IAuditLogger {

    void log(AuditEvent event);
}
