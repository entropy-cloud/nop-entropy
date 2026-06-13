package io.nop.ai.agent.hook;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNoOpHookRegistry {

    @Test
    void getHooksReturnsEmptyList() {
        NoOpHookRegistry registry = NoOpHookRegistry.INSTANCE;
        List<IAgentLifecycleHook> hooks = registry.getHooks(AgentLifecyclePoint.PRE_REASONING, "test-agent");
        assertTrue(hooks.isEmpty());
    }

    @Test
    void getHooksReturnsEmptyForAllPoints() {
        NoOpHookRegistry registry = NoOpHookRegistry.INSTANCE;
        for (AgentLifecyclePoint point : AgentLifecyclePoint.values()) {
            assertTrue(registry.getHooks(point, "test-agent").isEmpty(),
                    "Should return empty for " + point);
        }
    }

    @Test
    void registerThrowsUnsupportedOperationException() {
        NoOpHookRegistry registry = NoOpHookRegistry.INSTANCE;
        assertThrows(UnsupportedOperationException.class, () ->
                registry.register(AgentLifecyclePoint.PRE_REASONING, ctx -> HookResult.PassResult.instance()));
    }

    @Test
    void emptyFactoryReturnsNoOp() {
        IHookRegistry registry = IHookRegistry.empty();
        assertTrue(registry instanceof NoOpHookRegistry);
    }
}
