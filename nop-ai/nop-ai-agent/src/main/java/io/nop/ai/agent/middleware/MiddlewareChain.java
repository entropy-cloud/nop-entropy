package io.nop.ai.agent.middleware;

import io.nop.ai.agent.hook.HookContext;
import io.nop.ai.agent.hook.HookResult;

import java.util.List;
import java.util.function.Function;

/**
 * Immutable onion-chain that threads {@link IAgentMiddleware} layers around
 * a core callable. Built once at assembly time by
 * {@link io.nop.ai.agent.hook.DefaultHookRegistry#buildChain}; the executor
 * invokes {@link #proceed(HookContext)} at each enabled lifecycle point.
 *
 * <p>The core callable (innermost layer) runs all registered
 * {@link io.nop.ai.agent.hook.IAgentLifecycleHook}s at this point — so hooks
 * execute inside the middleware wrapping. This is the "dual-track coexistence"
 * design: middlewares control the flow, hooks observe within the core.
 *
 * <p>When the middleware list is empty, {@link #proceed} delegates directly to
 * the core — zero overhead, equivalent to the pre-middleware hook-only path.
 *
 * <p>Reenter semantics: a middleware returning {@link HookResult.ReenterResult}
 * breaks the chain; the caller is responsible for restarting from index 0 with
 * a re-entry counter guard (see {@code ReActAgentExecutor}'s re-entry handling).
 */
public final class MiddlewareChain {

    private final List<IAgentMiddleware> middlewares;
    private final int index;
    private final Function<HookContext, HookResult> core;

    /**
     * @param middlewares the full ordered middleware list (outer-to-inner)
     * @param index       the current position in {@code middlewares}
     * @param core        the innermost callable that runs the hook observers;
     *                    receives the {@link HookContext} and returns the
     *                    aggregated {@link HookResult}
     */
    public MiddlewareChain(List<IAgentMiddleware> middlewares, int index,
                           Function<HookContext, HookResult> core) {
        this.middlewares = middlewares;
        this.index = index;
        this.core = core;
    }

    /**
     * Advance the chain. If there are more middlewares, invoke the one at
     * {@code index} with a sub-chain starting at {@code index + 1}. Otherwise
     * invoke the core callable directly.
     *
     * @param ctx the lifecycle context (shared across all layers)
     * @return the aggregated hook result
     */
    public HookResult proceed(HookContext ctx) {
        if (index >= middlewares.size()) {
            return core.apply(ctx);
        }
        IAgentMiddleware mw = middlewares.get(index);
        return mw.execute(ctx, new MiddlewareChain(middlewares, index + 1, core));
    }
}
