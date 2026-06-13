package io.nop.ai.agent.hook;

import io.nop.ai.agent.model.AgentHookModel;
import io.nop.ai.agent.model.AgentModel;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDefaultHookRegistry {

    @Test
    void emptyRegistryReturnsEmptyHooks() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        List<IAgentLifecycleHook> hooks = registry.getHooks(AgentLifecyclePoint.PRE_REASONING, "agent");
        assertTrue(hooks.isEmpty());
    }

    @Test
    void registerAndGetHooks() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        IAgentLifecycleHook hook = ctx -> HookResult.PassResult.instance();
        registry.register(AgentLifecyclePoint.PRE_REASONING, hook);

        List<IAgentLifecycleHook> hooks = registry.getHooks(AgentLifecyclePoint.PRE_REASONING, "agent");
        assertEquals(1, hooks.size());
        assertEquals(hook, hooks.get(0));
    }

    @Test
    void hooksStoredByLifecyclePoint() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        IAgentLifecycleHook hook1 = ctx -> HookResult.PassResult.instance();
        IAgentLifecycleHook hook2 = ctx -> HookResult.PassResult.instance();

        registry.register(AgentLifecyclePoint.PRE_REASONING, hook1);
        registry.register(AgentLifecyclePoint.POST_REASONING, hook2);

        assertEquals(1, registry.getHooks(AgentLifecyclePoint.PRE_REASONING, "agent").size());
        assertEquals(1, registry.getHooks(AgentLifecyclePoint.POST_REASONING, "agent").size());
        assertEquals(0, registry.getHooks(AgentLifecyclePoint.PRE_ACTING, "agent").size());
    }

    @Test
    void multipleHooksAtSamePoint() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        IAgentLifecycleHook hook1 = ctx -> HookResult.PassResult.instance();
        IAgentLifecycleHook hook2 = ctx -> HookResult.PassResult.instance();

        registry.register(AgentLifecyclePoint.PRE_REASONING, hook1);
        registry.register(AgentLifecyclePoint.PRE_REASONING, hook2);

        List<IAgentLifecycleHook> hooks = registry.getHooks(AgentLifecyclePoint.PRE_REASONING, "agent");
        assertEquals(2, hooks.size());
    }

    @Test
    void fromAgentModelWithNullModel() {
        DefaultHookRegistry registry = DefaultHookRegistry.fromAgentModel(null);
        assertNotNull(registry);
        assertTrue(registry.getHooks(AgentLifecyclePoint.PRE_REASONING, "agent").isEmpty());
    }

    @Test
    void fromAgentModelWithNoHooks() {
        AgentModel model = new AgentModel();
        model.setName("test-agent");
        DefaultHookRegistry registry = DefaultHookRegistry.fromAgentModel(model);
        assertNotNull(registry);
        assertTrue(registry.getHooks(AgentLifecyclePoint.PRE_REASONING, "agent").isEmpty());
    }

    @Test
    void fromAgentModelLoadsHooksWithEventMatching() {
        AgentModel model = new AgentModel();
        model.setName("test-agent");

        AgentHookModel hookModel = new AgentHookModel();
        hookModel.setId("hook1");
        hookModel.setEvent("before_reasoning");
        hookModel.setBody(null);

        model.setHooks(List.of(hookModel));

        DefaultHookRegistry registry = DefaultHookRegistry.fromAgentModel(model);
        List<IAgentLifecycleHook> hooks = registry.getHooks(AgentLifecyclePoint.PRE_REASONING, "test-agent");
        assertEquals(1, hooks.size());
    }

    @Test
    void eventPatternMatchingCaseInsensitive() {
        assertEquals(AgentLifecyclePoint.PRE_CALL, DefaultHookRegistry.resolveLifecyclePoint("before_call"));
        assertEquals(AgentLifecyclePoint.PRE_CALL, DefaultHookRegistry.resolveLifecyclePoint("pre_call"));
        assertEquals(AgentLifecyclePoint.PRE_CALL, DefaultHookRegistry.resolveLifecyclePoint("PRE_CALL"));
        assertEquals(AgentLifecyclePoint.PRE_CALL, DefaultHookRegistry.resolveLifecyclePoint("Before_Call"));
    }

    @Test
    void eventPatternMappingAllPoints() {
        assertEquals(AgentLifecyclePoint.PRE_REASONING, DefaultHookRegistry.resolveLifecyclePoint("before_reasoning"));
        assertEquals(AgentLifecyclePoint.POST_REASONING, DefaultHookRegistry.resolveLifecyclePoint("after_reasoning"));
        assertEquals(AgentLifecyclePoint.PRE_ACTING, DefaultHookRegistry.resolveLifecyclePoint("before_acting"));
        assertEquals(AgentLifecyclePoint.POST_ACTING, DefaultHookRegistry.resolveLifecyclePoint("after_acting"));
        assertEquals(AgentLifecyclePoint.ON_ERROR, DefaultHookRegistry.resolveLifecyclePoint("on_error"));
        assertEquals(AgentLifecyclePoint.POST_CALL, DefaultHookRegistry.resolveLifecyclePoint("after_call"));
        assertEquals(AgentLifecyclePoint.REASONING_CHUNK, DefaultHookRegistry.resolveLifecyclePoint("reasoning_chunk"));
        assertEquals(AgentLifecyclePoint.PRE_COMPACT, DefaultHookRegistry.resolveLifecyclePoint("before_compact"));
        assertEquals(AgentLifecyclePoint.POST_COMPACT, DefaultHookRegistry.resolveLifecyclePoint("after_compact"));
        assertEquals(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED, DefaultHookRegistry.resolveLifecyclePoint("before_tool_result_processed"));
        assertEquals(AgentLifecyclePoint.AFTER_TOOL_RESULT_PROCESSED, DefaultHookRegistry.resolveLifecyclePoint("after_tool_result_processed"));
    }

    @Test
    void eventPatternAltNames() {
        assertEquals(AgentLifecyclePoint.PRE_REASONING, DefaultHookRegistry.resolveLifecyclePoint("pre_reasoning"));
        assertEquals(AgentLifecyclePoint.POST_REASONING, DefaultHookRegistry.resolveLifecyclePoint("post_reasoning"));
        assertEquals(AgentLifecyclePoint.PRE_ACTING, DefaultHookRegistry.resolveLifecyclePoint("pre_acting"));
        assertEquals(AgentLifecyclePoint.POST_ACTING, DefaultHookRegistry.resolveLifecyclePoint("post_acting"));
        assertEquals(AgentLifecyclePoint.POST_CALL, DefaultHookRegistry.resolveLifecyclePoint("post_call"));
        assertEquals(AgentLifecyclePoint.PRE_COMPACT, DefaultHookRegistry.resolveLifecyclePoint("pre_compact"));
        assertEquals(AgentLifecyclePoint.POST_COMPACT, DefaultHookRegistry.resolveLifecyclePoint("post_compact"));
    }

    @Test
    void resolveReturnsNullForNullOrEmpty() {
        assertNull(DefaultHookRegistry.resolveLifecyclePoint(null));
        assertNull(DefaultHookRegistry.resolveLifecyclePoint(""));
    }

    @Test
    void resolveReturnsNullForUnknown() {
        assertNull(DefaultHookRegistry.resolveLifecyclePoint("unknown_event"));
    }

    @Test
    void getHooksReturnsUnmodifiableList() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.PRE_REASONING, ctx -> HookResult.PassResult.instance());
        List<IAgentLifecycleHook> hooks = registry.getHooks(AgentLifecyclePoint.PRE_REASONING, "agent");
        assertThrows(UnsupportedOperationException.class, () -> hooks.add(ctx -> HookResult.PassResult.instance()));
    }
}
