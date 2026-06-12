package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;

public class AllowAllPathAccessChecker implements IPathAccessChecker {

    @Override
    public PathAccessResult checkAccess(String path, AgentExecutionContext ctx) {
        return PathAccessResult.allow();
    }
}
