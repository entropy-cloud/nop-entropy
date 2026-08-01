package io.nop.ai.agent.middleware;

/**
 * Execution-level (per-attempt) trigger points for {@link IAgentMiddleware}
 * declared with {@link MiddlewareScope#EXECUTION} (W3-1).
 *
 * <p>These are <b>distinct</b> from the session-level
 * {@link io.nop.ai.agent.hook.AgentLifecyclePoint} enum: the existing 12
 * session-level points and their semantics are left entirely untouched, and
 * execution-level concepts are modelled here so the two scopes never
 * conflate. Each {@code ExecutionPoint} fires around a single LLM or tool
 * attempt inside the retry/dispatch loop:
 *
 * <ul>
 *   <li>{@link #PRE_LLM_ATTEMPT} / {@link #POST_LLM_ATTEMPT} — fire
 *       immediately before/after each {@code callChatWithTimeout} inside
 *       {@code LlmCallCoordinator.doLlmCallWithRetry}. On retry the
 *       middleware re-runs (carrying the previous attempt's outcome via
 *       {@link AttemptContext}).</li>
 *   <li>{@link #PRE_TOOL_ATTEMPT} / {@link #POST_TOOL_ATTEMPT} — fire
 *       immediately before/after each individual tool call inside
 *       {@code AgentToolDispatcher.executeAllowedCalls} (per tool call, not
 *       per batch).</li>
 * </ul>
 *
 * <p>Veto semantics (see design §5.1):
 * <ul>
 *   <li>PRE_LLM_ATTEMPT Veto → the attempt is treated as failed and enters
 *       the retry-policy decision path (RETRY/STOP/FALLBACK), with a veto
 *       cap to prevent infinite loops.</li>
 *   <li>POST_LLM_ATTEMPT Veto → the attempt's response is rejected and
 *       enters the same retry decision path.</li>
 *   <li>Tool-side Veto → the single tool call produces an error result; it
 *       does not affect other tool calls in the same batch (tools have no
 *       retry mechanism).</li>
 * </ul>
 */
public enum ExecutionPoint {
    /**
     * Fires immediately before each LLM call attempt inside the retry loop.
     * Veto here skips the call and routes to the retry decision.
     */
    PRE_LLM_ATTEMPT,

    /**
     * Fires immediately after each LLM call attempt, before success/error
     * classification. Veto here rejects the response and routes to the
     * retry decision.
     */
    POST_LLM_ATTEMPT,

    /**
     * Fires immediately before each individual tool call (synchronously,
     * before the async future is submitted). Veto here produces an error
     * tool result for that call only.
     */
    PRE_TOOL_ATTEMPT,

    /**
     * Fires immediately after each individual tool call completes, before
     * the result is committed to the context. Veto here replaces the result
     * with an error result for that call only.
     */
    POST_TOOL_ATTEMPT
}
