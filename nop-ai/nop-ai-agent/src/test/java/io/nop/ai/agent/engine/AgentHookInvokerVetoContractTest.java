package io.nop.ai.agent.engine;

import io.nop.ai.agent.engine.AgentHookInvoker;
import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.hook.HookResult;
import io.nop.ai.agent.hook.IAgentLifecycleHook;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2292 (WI11): veto contract narrowing — dead veto points fail loud;
 * ON_ERROR/POST_COMPACT (non-chain observer points) WARN-drop a veto without
 * throwing (failing loud inside error/terminal handling would be hazardous).
 */
public class AgentHookInvokerVetoContractTest {

    private AgentHookInvoker invoker(DefaultHookRegistry registry) {
        return new AgentHookInvoker(registry, null);
    }

    @Test
    void vetoAtDeadChainPointFailsLoud() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.PRE_ACTING,
                ctx -> new HookResult.VetoResult("dead veto"));
        AgentHookInvoker invoker = invoker(registry);
        AgentExecutionContext ctx = new AgentExecutionContext(new AgentModel());

        try {
            invoker.executeWithMiddleware(AgentLifecyclePoint.PRE_ACTING, ctx, "agent", null, null);
            org.junit.jupiter.api.Assertions.fail("expected contract violation");
        } catch (NopAiAgentException e) {
            assertTrue("nop.err.ai.agent.internal-detail".equals(e.getErrorCode()),
                    "contract violation error code (got: " + e.getErrorCode() + ")");
        }
    }

    @Test
    void vetoAtObserverPointsIsWarnDroppedNotThrown() {
        for (AgentLifecyclePoint observerPoint : new AgentLifecyclePoint[]{
                AgentLifecyclePoint.ON_ERROR, AgentLifecyclePoint.POST_COMPACT}) {
            DefaultHookRegistry registry = new DefaultHookRegistry();
            AtomicInteger fires = new AtomicInteger();
            registry.register(observerPoint, ctx -> {
                fires.incrementAndGet();
                return new HookResult.VetoResult("observer veto");
            });
            AgentHookInvoker invoker = invoker(registry);
            AgentExecutionContext ctx = new AgentExecutionContext(new AgentModel());

            HookResult result = invoker.executeWithMiddleware(
                    observerPoint, ctx, "agent", null, null);

            assertEquals(1, fires.get(), observerPoint + " hook fired");
            // WARN-drop: the veto is NOT returned (dropped → Pass fallback in
            // the caller's view), NOT thrown
            assertTrue(!result.isVeto(),
                    observerPoint + " veto must be warn-dropped, not propagated");
        }
    }

    @Test
    void vetoAtOnErrorDirectCallIsWarnDropped() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.ON_ERROR,
                ctx -> new HookResult.VetoResult("veto in error path"));
        AgentHookInvoker invoker = invoker(registry);
        AgentExecutionContext ctx = new AgentExecutionContext(new AgentModel());

        // invokeOnError wraps invokeHooks(ON_ERROR) — a veto there must not
        // break the error-handling path (hazard-aware WARN-drop). A freshly
        // built ctx has status pending (status changes happen during real
        // execution); the assertion is that invokeOnError does NOT throw and
        // does NOT mutate status.
        AgentExecStatus before = ctx.getStatus();
        invoker.invokeOnError(ctx, "agent");
        assertEquals(before, ctx.getStatus(),
                "error path continues after observer veto (status unchanged)");
    }

    @Test
    void vetoAtPreReasoningStillConsumed() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.PRE_REASONING,
                ctx -> new HookResult.VetoResult("skip iteration"));
        AgentHookInvoker invoker = invoker(registry);
        AgentExecutionContext ctx = new AgentExecutionContext(new AgentModel());

        HookResult result = invoker.executeWithMiddleware(
                AgentLifecyclePoint.PRE_REASONING, ctx, "agent", null, null);
        assertTrue(result.isVeto(), "PRE_REASONING veto passes through (consumed by executor)");
    }
}
