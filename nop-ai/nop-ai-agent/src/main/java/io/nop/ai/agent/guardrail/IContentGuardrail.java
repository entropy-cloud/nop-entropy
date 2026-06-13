package io.nop.ai.agent.guardrail;

import io.nop.ai.agent.engine.AgentExecutionContext;

public interface IContentGuardrail {

    GuardrailResult check(GuardrailDirection direction, String content, AgentExecutionContext ctx);
}
