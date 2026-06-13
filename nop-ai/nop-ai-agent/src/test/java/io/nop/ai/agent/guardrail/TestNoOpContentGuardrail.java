package io.nop.ai.agent.guardrail;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNoOpContentGuardrail {

    @Test
    void checkReturnsPassForInputDirection() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        GuardrailResult result = NoOpContentGuardrail.noOp().check(
                GuardrailDirection.INPUT, "some user content", ctx);

        assertTrue(result.isPass());
        assertSame(GuardrailResult.PassResult.instance(), result);
    }

    @Test
    void checkReturnsPassForOutputDirection() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        GuardrailResult result = NoOpContentGuardrail.noOp().check(
                GuardrailDirection.OUTPUT, "some llm content", ctx);

        assertTrue(result.isPass());
        assertSame(GuardrailResult.PassResult.instance(), result);
    }

    @Test
    void checkReturnsPassForNullContent() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        GuardrailResult result = NoOpContentGuardrail.noOp().check(
                GuardrailDirection.INPUT, null, ctx);

        assertTrue(result.isPass());
    }

    @Test
    void noOpFactoryReturnsSameInstance() {
        IContentGuardrail a = NoOpContentGuardrail.noOp();
        IContentGuardrail b = NoOpContentGuardrail.noOp();
        assertSame(a, b);
    }

    @Test
    void implementsIContentGuardrail() {
        assertTrue(IContentGuardrail.class.isAssignableFrom(NoOpContentGuardrail.class));
    }
}
