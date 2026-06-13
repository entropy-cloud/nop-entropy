package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;

public interface IContentTrustEvaluator {

    boolean isTrustedSource(ContentOrigin origin, AgentExecutionContext ctx);
}
