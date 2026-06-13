package io.nop.ai.agent.guardrail;

import io.nop.ai.agent.engine.AgentExecutionContext;

public final class NoOpContentGuardrail implements IContentGuardrail {

    private static final NoOpContentGuardrail INSTANCE = new NoOpContentGuardrail();

    private NoOpContentGuardrail() {
    }

    public static IContentGuardrail noOp() {
        return INSTANCE;
    }

    @Override
    public GuardrailResult check(GuardrailDirection direction, String content, AgentExecutionContext ctx) {
        return GuardrailResult.PassResult.instance();
    }
}
