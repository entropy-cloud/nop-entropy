package io.nop.ai.agent.security;

public class AllowAllPermissionProvider implements IPermissionProvider {

    @Override
    public Permission resolve(String toolName, String agentName, String sessionId) {
        return Permission.allow();
    }
}
