package io.nop.ai.agent.middleware;

/**
 * The scope at which an {@link IAgentMiddleware} operates (W3-1 dual-layer
 * middleware).
 *
 * <ul>
 *   <li>{@link #SESSION} — the existing layer: fires once per request at one of
 *       the 9 chain-enabled {@link io.nop.ai.agent.hook.AgentLifecyclePoint}s.
 *       This is the default and preserves plan-296 behaviour unchanged.</li>
 *   <li>{@link #EXECUTION} — the new layer: fires per LLM/tool attempt at one
 *       of the {@link ExecutionPoint}s, so that safety/circuit checks are
 *       <b>re-evaluated on every retry/resurrection attempt</b> (the core
 *       value of W3-1). Execution-level middleware does not wrap the core
 *       operation; PRE_* and POST_* are separate trigger points invoked
 *       immediately before/after each attempt.</li>
 * </ul>
 *
 * <p>The two layers coexist without interaction: a middleware declares one
 * scope (via {@code <middleware scope="execution" .../>}, default
 * {@code session}) and is routed to the corresponding registry region at
 * assembly time.
 */
public enum MiddlewareScope {
    /**
     * Per-request session-level middleware, registered against an
     * {@link io.nop.ai.agent.hook.AgentLifecyclePoint}. Existing 9-point
     * behaviour (plan 296) — unchanged.
     */
    SESSION,

    /**
     * Per-attempt execution-level middleware, registered against an
     * {@link ExecutionPoint}. Re-evaluated on every retry.
     */
    EXECUTION
}
