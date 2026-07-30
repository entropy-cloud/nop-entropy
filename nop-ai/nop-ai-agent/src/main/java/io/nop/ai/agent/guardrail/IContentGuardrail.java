package io.nop.ai.agent.guardrail;

import io.nop.ai.agent.engine.AgentExecutionContext;

/**
 * @apiNote This interface has only a NoOp implementation available
 *          ({@link NoOpContentGuardrail}) — no production-grade implementation
 *          exists in this version. Content safety is not enforced with the
 *          shipped default. Production deployments should provide a custom
 *          implementation via {@code DefaultAgentEngine.setContentGuardrail()}.
 */
public interface IContentGuardrail {

    GuardrailResult check(GuardrailDirection direction, String content, AgentExecutionContext ctx);
}
