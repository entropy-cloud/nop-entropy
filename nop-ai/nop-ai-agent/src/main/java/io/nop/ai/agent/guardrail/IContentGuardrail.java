package io.nop.ai.agent.guardrail;

import io.nop.ai.agent.engine.AgentExecutionContext;

/**
 * Content guardrail SPI. The shipped engine default is the no-op
 * implementation ({@link NoOpContentGuardrail}) for backwards compatibility.
 * A production-grade implementation ({@link PromptInjectionGuardrail}) is
 * shipped and can be wired via
 * {@code DefaultAgentEngine.setContentGuardrail()}. Constructing an engine
 * with the NoOp default emits a WARN (a production alternative exists).
 *
 * @apiNote The shipped default is {@link NoOpContentGuardrail} (content safety
 *          not enforced). A production-grade implementation
 *          ({@link PromptInjectionGuardrail}) is available and should be wired
 *          for production deployments via
 *          {@code DefaultAgentEngine.setContentGuardrail()}.
 */
public interface IContentGuardrail {

    GuardrailResult check(GuardrailDirection direction, String content, AgentExecutionContext ctx);
}
