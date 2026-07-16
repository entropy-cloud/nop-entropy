package io.nop.ai.agent.middleware;

import io.nop.ai.agent.hook.HookContext;
import io.nop.ai.agent.hook.HookResult;

/**
 * Onion-style interceptor for agent lifecycle points. Multiple middlewares
 * at the same {@link io.nop.ai.agent.hook.AgentLifecyclePoint} form a
 * decentralized chain: each middleware decides whether and when to invoke
 * the next layer via {@code next.proceed(ctx)}, enabling before/after
 * wrapping, short-circuit (Veto), and re-enter (Reenter) semantics.
 *
 * <p>Unlike {@link io.nop.ai.agent.hook.IAgentLifecycleHook} (an observer
 * that cannot wrap or skip the core logic), a middleware can fully control
 * the execution flow around the core — the innermost layer of the chain runs
 * all registered hooks at that point, then the actual lifecycle logic.
 *
 * <p>Both systems coexist: hooks run inside the chain's core, middlewares
 * wrap around it.
 *
 * <p>Return-value semantics (same as {@code HookResult}):
 * <ul>
 *   <li>{@link HookResult.PassResult} after calling {@code next.proceed()}
 *       — normal continuation</li>
 *   <li>{@link HookResult.VetoResult} — chain breaks, lifecycle point
 *       rejected (core and remaining layers do not execute)</li>
 *   <li>{@link HookResult.ReenterResult} — chain restarts from the first
 *       middleware (with a re-entry counter to prevent infinite loops)</li>
 * </ul>
 */
public interface IAgentMiddleware {
    /**
     * Execute this middleware layer. To continue to the next layer (and
     * eventually the core logic), call {@code next.proceed(ctx)} and return
     * its result. To short-circuit, return a Veto/Reenter without calling
     * {@code next.proceed()}.
     *
     * @param ctx  the lifecycle hook context for this point
     * @param next the remaining chain (call {@code next.proceed(ctx)} to
     *             delegate)
     * @return the aggregated hook result (Pass/Veto/Reenter)
     */
    HookResult execute(HookContext ctx, MiddlewareChain next);
}
