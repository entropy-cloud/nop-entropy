package io.nop.ai.agent.middleware;

import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.hook.HookContext;
import io.nop.ai.agent.hook.HookResult;
import io.nop.ai.agent.hook.IAgentLifecycleHook;
import io.nop.ai.agent.hook.IHookRegistry;
import io.nop.ai.agent.hook.NoOpHookRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 296 (WS1): unit tests for the onion-style middleware chain.
 * Verifies:
 * <ul>
 *   <li>3-layer wrapping order (outer.before → middle.before → inner.before → core → inner.after → middle.after → outer.after)</li>
 *   <li>Veto in the middle layer breaks the chain (core and subsequent layers do not execute)</li>
 *   <li>Reenter from a layer breaks the chain (returned to caller for re-dispatch)</li>
 *   <li>Middleware wraps core which contains hooks (mixed timing)</li>
 *   <li>Empty middleware list delegates directly to core (no overhead)</li>
 *   <li>NoOpHookRegistry: getMiddlewares returns empty, registerMiddleware throws</li>
 * </ul>
 */
public class TestMiddlewareChain {

    /**
     * A recording middleware that logs "name.before" before calling next and
     * "name.after" after.
     */
    static class RecordingMiddleware implements IAgentMiddleware {
        final String name;
        final List<String> log;

        RecordingMiddleware(String name, List<String> log) {
            this.name = name;
            this.log = log;
        }

        @Override
        public HookResult execute(HookContext ctx, MiddlewareChain next) {
            log.add(name + ".before");
            HookResult result = next.proceed(ctx);
            log.add(name + ".after");
            return result;
        }
    }

    /** A middleware that vetoes without calling next. */
    static class VetoMiddleware implements IAgentMiddleware {
        @Override
        public HookResult execute(HookContext ctx, MiddlewareChain next) {
            return new HookResult.VetoResult("blocked-by-middleware");
        }
    }

    /** A middleware that returns Reenter without calling next. */
    static class ReenterMiddleware implements IAgentMiddleware {
        @Override
        public HookResult execute(HookContext ctx, MiddlewareChain next) {
            return new HookResult.ReenterResult("reenter-requested");
        }
    }

    private HookContext dummyContext() {
        return new HookContext(AgentLifecyclePoint.PRE_REASONING, null);
    }

    @Test
    void threeLayerWrappingOrder() {
        List<String> log = new ArrayList<>();
        IAgentMiddleware outer = new RecordingMiddleware("outer", log);
        IAgentMiddleware mid = new RecordingMiddleware("mid", log);
        IAgentMiddleware inner = new RecordingMiddleware("inner", log);

        Function<HookContext, HookResult> core = ctx -> {
            log.add("core");
            return HookResult.PassResult.instance();
        };

        MiddlewareChain chain = new MiddlewareChain(Arrays.asList(outer, mid, inner), 0, core);
        HookResult result = chain.proceed(dummyContext());

        assertTrue(result.isPass());
        assertEquals(Arrays.asList(
                "outer.before", "mid.before", "inner.before",
                "core",
                "inner.after", "mid.after", "outer.after"), log);
    }

    @Test
    void vetoInMiddleBreaksChain() {
        List<String> log = new ArrayList<>();
        // outer wraps veto: outer.before → veto (returns Veto, skips next) → outer.after
        IAgentMiddleware outer = new RecordingMiddleware("outer", log);
        IAgentMiddleware veto = new VetoMiddleware();
        IAgentMiddleware inner = new RecordingMiddleware("inner", log);

        Function<HookContext, HookResult> core = ctx -> {
            log.add("core");
            return HookResult.PassResult.instance();
        };

        MiddlewareChain chain = new MiddlewareChain(Arrays.asList(outer, veto, inner), 0, core);
        HookResult result = chain.proceed(dummyContext());

        assertTrue(result.isVeto());
        // outer.before ran, outer wrapped veto so outer.after also ran.
        // But core and inner never ran — veto did not call next.proceed().
        assertEquals(Arrays.asList("outer.before", "outer.after"), log);
        assertFalse(log.contains("core"));
        assertFalse(log.contains("inner.before"));
    }

    @Test
    void reenterBreaksChain() {
        IAgentMiddleware outer = new RecordingMiddleware("outer", new ArrayList<>());
        IAgentMiddleware reenter = new ReenterMiddleware();

        Function<HookContext, HookResult> core = ctx -> HookResult.PassResult.instance();

        MiddlewareChain chain = new MiddlewareChain(Arrays.asList(outer, reenter), 0, core);
        HookResult result = chain.proceed(dummyContext());

        assertTrue(result.isReenter());
    }

    @Test
    void emptyMiddlewareListDelegatesToCore() {
        List<String> log = new ArrayList<>();
        Function<HookContext, HookResult> core = ctx -> {
            log.add("core");
            return HookResult.PassResult.instance();
        };

        MiddlewareChain chain = new MiddlewareChain(List.of(), 0, core);
        HookResult result = chain.proceed(dummyContext());

        assertTrue(result.isPass());
        assertEquals(List.of("core"), log);
    }

    @Test
    void middlewareWrapsHookInCore() {
        List<String> log = new ArrayList<>();
        List<String> hookLog = new ArrayList<>();

        // The core runs a hook observer
        IAgentLifecycleHook hook = ctx -> {
            hookLog.add("hook");
            return HookResult.PassResult.instance();
        };

        Function<HookContext, HookResult> core = ctx -> {
            log.add("core-before-hook");
            HookResult r = hook.onEvent(ctx);
            log.add("core-after-hook");
            return r;
        };

        IAgentMiddleware mw = new RecordingMiddleware("mw", log);
        MiddlewareChain chain = new MiddlewareChain(List.of(mw), 0, core);
        HookResult result = chain.proceed(dummyContext());

        assertTrue(result.isPass());
        // Middleware wraps around core, hook runs inside core
        assertEquals(Arrays.asList("mw.before", "core-before-hook", "core-after-hook", "mw.after"), log);
        assertEquals(List.of("hook"), hookLog);
    }

    @Test
    void noOpRegistryReturnsEmptyMiddlewares() {
        List<IAgentMiddleware> mws = NoOpHookRegistry.INSTANCE.getMiddlewares(AgentLifecyclePoint.PRE_CALL, "agent");
        assertTrue(mws.isEmpty());
    }

    @Test
    void noOpRegistryRegisterMiddlewareThrows() {
        assertThrows(UnsupportedOperationException.class, () ->
                NoOpHookRegistry.INSTANCE.registerMiddleware(AgentLifecyclePoint.PRE_CALL, (ctx, next) -> next.proceed(ctx)));
    }

    @Test
    void defaultHookRegistryRegisterAndGetMiddlewares() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        IAgentMiddleware mw = new RecordingMiddleware("mw", new ArrayList<>());

        registry.registerMiddleware(AgentLifecyclePoint.PRE_REASONING, mw);

        List<IAgentMiddleware> mws = registry.getMiddlewares(AgentLifecyclePoint.PRE_REASONING, "agent");
        assertEquals(1, mws.size());
        assertEquals(mw, mws.get(0));

        // Other points have no middlewares
        assertTrue(registry.getMiddlewares(AgentLifecyclePoint.POST_CALL, "agent").isEmpty());
    }

    @Test
    void buildChainWithNoMiddlewaresRunsCoreDirectly() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        List<String> log = new ArrayList<>();
        Function<HookContext, HookResult> core = ctx -> {
            log.add("core");
            return HookResult.PassResult.instance();
        };

        MiddlewareChain chain = registry.buildChain(AgentLifecyclePoint.PRE_CALL, core);
        HookResult result = chain.proceed(dummyContext());

        assertTrue(result.isPass());
        assertEquals(List.of("core"), log);
    }

    @Test
    void buildChainWithMiddlewaresWrapsCore() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        List<String> log = new ArrayList<>();
        registry.registerMiddleware(AgentLifecyclePoint.PRE_CALL, new RecordingMiddleware("mw", log));

        Function<HookContext, HookResult> core = ctx -> {
            log.add("core");
            return HookResult.PassResult.instance();
        };

        MiddlewareChain chain = registry.buildChain(AgentLifecyclePoint.PRE_CALL, core);
        HookResult result = chain.proceed(dummyContext());

        assertTrue(result.isPass());
        assertEquals(Arrays.asList("mw.before", "core", "mw.after"), log);
    }

    @Test
    void iHookRegistryDefaultGetMiddlewaresReturnsEmpty() {
        // A minimal registry that only implements the two original methods
        IHookRegistry customRegistry = new IHookRegistry() {
            @Override
            public List<IAgentLifecycleHook> getHooks(AgentLifecyclePoint point, String agentName) {
                return List.of();
            }

            @Override
            public void register(AgentLifecyclePoint point, IAgentLifecycleHook hook) {
            }
        };

        assertTrue(customRegistry.getMiddlewares(AgentLifecyclePoint.PRE_CALL, "agent").isEmpty());
    }

    @Test
    void iHookRegistryDefaultRegisterMiddlewareThrows() {
        IHookRegistry customRegistry = new IHookRegistry() {
            @Override
            public List<IAgentLifecycleHook> getHooks(AgentLifecyclePoint point, String agentName) {
                return List.of();
            }

            @Override
            public void register(AgentLifecyclePoint point, IAgentLifecycleHook hook) {
            }
        };

        assertThrows(UnsupportedOperationException.class, () ->
                customRegistry.registerMiddleware(AgentLifecyclePoint.PRE_CALL, (ctx, next) -> next.proceed(ctx)));
    }
}
