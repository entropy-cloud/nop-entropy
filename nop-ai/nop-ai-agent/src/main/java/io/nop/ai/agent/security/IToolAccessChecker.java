package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;

public interface IToolAccessChecker {

    ToolAccessResult checkAccess(String toolName, AgentExecutionContext ctx);
}
