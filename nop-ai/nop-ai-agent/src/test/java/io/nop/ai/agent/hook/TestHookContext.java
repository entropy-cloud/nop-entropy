package io.nop.ai.agent.hook;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestHookContext {

    private AgentExecutionContext execCtx;

    @BeforeEach
    void setUp() {
        AgentModel model = new AgentModel();
        model.setName("test-agent");
        execCtx = AgentExecutionContext.create(model, "session-1");
    }

    @Test
    void lifecyclePointIsSet() {
        HookContext ctx = new HookContext(AgentLifecyclePoint.PRE_REASONING, execCtx);
        assertEquals(AgentLifecyclePoint.PRE_REASONING, ctx.getLifecyclePoint());
    }

    @Test
    void executionContextIsSet() {
        HookContext ctx = new HookContext(AgentLifecyclePoint.POST_CALL, execCtx);
        assertEquals(execCtx, ctx.getExecutionContext());
        assertEquals("test-agent", ctx.getExecutionContext().getAgentModel().getName());
    }

    @Test
    void dataMapIsMutable() {
        HookContext ctx = new HookContext(AgentLifecyclePoint.PRE_ACTING, execCtx);
        assertNotNull(ctx.getData());
        assertTrue(ctx.getData().isEmpty());

        ctx.getData().put("key1", "value1");
        ctx.getData().put("key2", 42);
        assertEquals("value1", ctx.getData().get("key1"));
        assertEquals(42, ctx.getData().get("key2"));
    }

    @Test
    void toolNameAndToolCallIdNullable() {
        HookContext ctx = new HookContext(AgentLifecyclePoint.PRE_ACTING, execCtx);
        assertNull(ctx.getToolName());
        assertNull(ctx.getToolCallId());

        ctx.setToolName("calculator");
        ctx.setToolCallId("call_123");
        assertEquals("calculator", ctx.getToolName());
        assertEquals("call_123", ctx.getToolCallId());
    }

    @Test
    void dataMapIndependentAcrossInstances() {
        HookContext ctx1 = new HookContext(AgentLifecyclePoint.PRE_CALL, execCtx);
        HookContext ctx2 = new HookContext(AgentLifecyclePoint.POST_CALL, execCtx);

        ctx1.getData().put("shared", "from-ctx1");
        assertTrue(ctx2.getData().isEmpty());
    }
}
