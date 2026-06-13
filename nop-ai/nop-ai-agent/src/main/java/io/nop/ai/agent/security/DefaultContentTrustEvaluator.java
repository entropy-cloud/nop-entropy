package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;

public class DefaultContentTrustEvaluator implements IContentTrustEvaluator {

    @Override
    public boolean isTrustedSource(ContentOrigin origin, AgentExecutionContext ctx) {
        if (origin == null) {
            return false;
        }
        switch (origin) {
            case CHANNEL_INPUT:
            case AGENT_GENERATED:
                return true;
            case WEB_FETCH:
            case FILE_READ:
                return false;
            default:
                return false;
        }
    }
}
