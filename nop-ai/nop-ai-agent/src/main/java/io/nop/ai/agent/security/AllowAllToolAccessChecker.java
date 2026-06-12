package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;

public class AllowAllToolAccessChecker implements IToolAccessChecker {

    @Override
    public ToolAccessResult checkAccess(String toolName, AgentExecutionContext ctx) {
        return ToolAccessResult.allow();
    }
}
