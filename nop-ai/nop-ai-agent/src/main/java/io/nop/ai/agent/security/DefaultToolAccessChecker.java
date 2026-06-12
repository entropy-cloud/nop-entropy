package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;

import java.util.Set;

public class DefaultToolAccessChecker implements IToolAccessChecker {

    private static final Set<String> DENIED_TOOLS = Set.of(
            "bash",
            "write-file",
            "delete-file",
            "move-file",
            "patch-file",
            "apply-delta",
            "http-request",
            "graphql-query"
    );

    @Override
    public ToolAccessResult checkAccess(String toolName, AgentExecutionContext ctx) {
        if (toolName != null && DENIED_TOOLS.contains(toolName.toLowerCase())) {
            return ToolAccessResult.denyByRule("hardcoded_deny_list", toolName);
        }
        return ToolAccessResult.allow();
    }
}
