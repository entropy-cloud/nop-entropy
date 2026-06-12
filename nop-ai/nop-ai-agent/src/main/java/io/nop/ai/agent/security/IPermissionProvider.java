package io.nop.ai.agent.security;

public interface IPermissionProvider {

    Permission resolve(String toolName, String agentName, String sessionId);
}
