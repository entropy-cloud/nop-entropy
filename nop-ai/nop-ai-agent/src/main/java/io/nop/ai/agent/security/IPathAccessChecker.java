package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;

public interface IPathAccessChecker {

    PathAccessResult checkAccess(String path, AgentExecutionContext ctx);
}
