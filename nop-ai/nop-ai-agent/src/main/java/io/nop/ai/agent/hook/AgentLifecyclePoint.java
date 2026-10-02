package io.nop.ai.agent.hook;

/**
 * Plan 2292 (WI11): lifecycle hook points with per-point result contracts
 * (see {@code AgentHookInvoker.validateVetoPoint/validateBailPoint} and
 * {@code 03-extension-matrix.md} veto 合同节).
 *
 * <ul>
 *   <li><b>Veto consumable</b>: PRE_CALL, PRE_REASONING (fail-loud
 *       elsewhere).</li>
 *   <li><b>Bail consumable</b>: POST_REASONING, POST_CALL (fail-loud
 *       elsewhere).</li>
 *   <li><b>Reenter consumable</b>: BEFORE/AFTER_TOOL_RESULT_PROCESSED
 *       (fail-loud elsewhere).</li>
 *   <li><b>Observer (result dropped)</b>: ON_ERROR, POST_COMPACT — a Veto
 *       there is warn-logged and dropped, never thrown.</li>
 * </ul>
 */
public enum AgentLifecyclePoint {
    PRE_CALL,
    PRE_REASONING,
    POST_REASONING,
    PRE_ACTING,
    POST_ACTING,
    ON_ERROR,
    POST_CALL,
    PRE_COMPACT,
    POST_COMPACT,
    BEFORE_TOOL_RESULT_PROCESSED,
    AFTER_TOOL_RESULT_PROCESSED
}
