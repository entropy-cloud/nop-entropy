package io.nop.ai.agent.hook;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentHookInvoker;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentModel;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W5-3 fail-loud regression: BailResult at a non-POST lifecycle point must
 * throw even on the direct-call (non-chain) points ON_ERROR /
 * REASONING_CHUNK / POST_COMPACT, which previously fell into the after_*
 * warn-and-continue degradation branch and silently dropped the contract
 * violation. Also locks the after-star / before-star business-exception
 * degradation semantics so the fail-loud fix does not change hook error
 * handling.
 */
public class TestAgentHookInvokerBailFailLoud {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private AgentModel agentModel;
    private AgentExecutionContext ctx;

    @BeforeEach
    void setUp() {
        agentModel = new AgentModel();
        agentModel.setName("test-agent");
        ctx = AgentExecutionContext.create(agentModel, "test-session");
    }

    private AgentHookInvoker invoker(DefaultHookRegistry registry) {
        return new AgentHookInvoker(registry, null);
    }

    private DefaultHookRegistry registryWithBail(AgentLifecyclePoint point) {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(point, hookCtx -> new HookResult.BailResult("invalid-point"));
        return registry;
    }

    @Test
    void bailAtPostCompactDirectCallFailsLoud() {
        AgentHookInvoker invoker = invoker(registryWithBail(AgentLifecyclePoint.POST_COMPACT));
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> invoker.invokeHooks(AgentLifecyclePoint.POST_COMPACT, ctx, "test-agent", null, null),
                "BailResult at POST_COMPACT (direct-call point) must fail loud, not be swallowed by the after_* warn branch");
        assertTrue(ex.getMessage().contains("POST"),
                "Error message should mention POST points: " + ex.getMessage());
    }

    @Test
    void bailAtReasoningChunkDirectCallFailsLoud() {
        AgentHookInvoker invoker = invoker(registryWithBail(AgentLifecyclePoint.REASONING_CHUNK));
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> invoker.invokeHooks(AgentLifecyclePoint.REASONING_CHUNK, ctx, "test-agent", null, null),
                "BailResult at REASONING_CHUNK (direct-call point) must fail loud, not be swallowed by the after_* warn branch");
        assertTrue(ex.getMessage().contains("POST"),
                "Error message should mention POST points: " + ex.getMessage());
    }

    @Test
    void bailAtOnErrorDirectCallFailsLoud() {
        AgentHookInvoker invoker = invoker(registryWithBail(AgentLifecyclePoint.ON_ERROR));
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> invoker.invokeHooks(AgentLifecyclePoint.ON_ERROR, ctx, "test-agent", null, null),
                "BailResult at ON_ERROR (direct-call point) must fail loud, not be swallowed by the ON_ERROR warn branch");
        assertTrue(ex.getMessage().contains("POST"),
                "Error message should mention POST points: " + ex.getMessage());
    }

    @Test
    void bailAtPrePointFailsLoud() {
        AgentHookInvoker invoker = invoker(registryWithBail(AgentLifecyclePoint.PRE_ACTING));
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> invoker.invokeHooks(AgentLifecyclePoint.PRE_ACTING, ctx, "test-agent", null, null),
                "BailResult at PRE_ACTING must fail loud");
        assertTrue(ex.getMessage().contains("POST"),
                "Error message should mention POST points: " + ex.getMessage());
    }

    @Test
    void passResultAtDirectCallPointPasses() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.POST_COMPACT, hookCtx -> HookResult.PassResult.instance());
        AgentHookInvoker invoker = invoker(registry);
        HookResult result = invoker.invokeHooks(AgentLifecyclePoint.POST_COMPACT, ctx, "test-agent", null, null);
        assertTrue(result.isPass(), "legal PassResult at a direct-call point must pass through unchanged");
    }

    @Test
    void afterHookBusinessExceptionKeepsWarnAndContinue() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.POST_COMPACT,
                hookCtx -> {
                    throw new IllegalStateException("hook business failure");
                });
        registry.register(AgentLifecyclePoint.POST_COMPACT, hookCtx -> HookResult.PassResult.instance());
        AgentHookInvoker invoker = invoker(registry);
        // A hook business exception at an after_* point keeps the existing
        // warn-and-continue semantics: no throw, later hooks still run.
        HookResult result = assertDoesNotThrow(
                () -> invoker.invokeHooks(AgentLifecyclePoint.POST_COMPACT, ctx, "test-agent", null, null));
        assertTrue(result.isPass(), "after_* business exception must not change the aggregated result");
    }

    @Test
    void beforeHookBusinessExceptionStillFailsLoud() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.PRE_CALL,
                hookCtx -> {
                    throw new IllegalStateException("hook business failure");
                });
        AgentHookInvoker invoker = invoker(registry);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> invoker.invokeHooks(AgentLifecyclePoint.PRE_CALL, ctx, "test-agent", null, null),
                "before_* hook business exception must keep rethrowing (existing fail-loud semantics)");
        assertTrue(ex.getMessage().contains("business"),
                "original exception must propagate unchanged: " + ex.getMessage());
    }
}