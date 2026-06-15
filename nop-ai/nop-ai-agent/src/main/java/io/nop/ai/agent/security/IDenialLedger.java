package io.nop.ai.agent.security;

/**
 * Layer 3 denial-ledger contract (design §6.2): per-session denial counting
 * with automatic pause of autonomous execution once the denial threshold is
 * reached. Sits in the defense-in-depth chain (design §8) immediately after
 * {@link IApprovalGate} and before {@link IPostDenialGuard} (L3-7 — landed) /
 * {@code ISandboxBackend} (Layer 4).
 *
 * <p><b>Dispatch-path integration</b>: the {@code ReActAgentExecutor}
 * dispatch loop records every deny decision (Layer 1 / 2 / 3 — five deny
 * checkpoints) into the ledger via {@link #recordDenial}. The returned
 * {@link DenialRecordOutcome#isThresholdExceeded()} flag tells the dispatch
 * path whether to abort the dispatch loop and mark the session as paused
 * (design §6.2). On the next ReAct-loop iteration start,
 * {@link #isPaused} is consulted: a paused session aborts the ReAct loop
 * before any further LLM call.
 *
 * <p><b>Default</b>: {@link DefaultDenialLedger} — in-memory threshold-based
 * counting (threshold = 3, design §6.2). {@link NoOpDenialLedger} is retained
 * as a public opt-in; {@link DBDenialLedger} persists per-session counts to
 * the database.
 *
 * <p><b>Thread safety</b>: implementations must be thread-safe. Multiple
 * sessions may access the same ledger instance concurrently, and per-session
 * counts must remain independent — a denial in session A must not affect the
 * denial count of session B.
 *
 * <p><b>Persistence</b>: the contract does not mandate persistence. The
 * {@link NoOpDenialLedger} default does not persist; a {@code DBDenialLedger}
 * implementation persists per-session counts to the database so they survive
 * session recovery / ledger-instance reconstruction. The audit-readiness
 * finding L3-G5 (interface did not define a persistence contract) is narrowed
 * here: persistence is a property of the functional implementation, not the
 * interface contract.
 */
public interface IDenialLedger {

    /**
     * Record a single per-session denial and return the resulting outcome.
     *
     * <p>The dispatch path calls this at every deny checkpoint (Layer 1 / 2 / 3)
     * and inspects the returned
     * {@link DenialRecordOutcome#isThresholdExceeded()} flag to decide
     * whether to abort the dispatch loop.
     *
     * @param record the structured denial record; never null
     * @return the outcome (cumulative per-session count + whether the
     *         threshold has been reached); never null
     */
    DenialRecordOutcome recordDenial(DenialRecord record);

    /**
     * Query whether the session has been paused because its cumulative
     * denial count has reached the threshold.
     *
     * <p>Consulted at the start of every ReAct-loop iteration: a paused
     * session aborts the ReAct loop before any further LLM call.
     *
     * @param sessionId the session identifier; may be null (anonymous —
     *                  always returns {@code false} for the NoOp default)
     * @return {@code true} if the session has reached the denial threshold
     */
    boolean isPaused(String sessionId);

    /**
     * Query the current cumulative denial count for a session.
     *
     * @param sessionId the session identifier; may be null
     * @return the cumulative denial count for the session, or {@code 0} for
     *         the {@link NoOpDenialLedger} default
     */
    int getDenialCount(String sessionId);

    /**
     * Reset the denial count for a session, clearing the paused state.
     *
     * <p>This is the human-intervention recovery entry point (design §6.2
     * {@code pauseBehavior = sticky} — full sticky recovery protocol is a
     * deferred successor; the current contract exposes {@code reset} so a
     * future recovery workflow can call it).
     *
     * @param sessionId the session identifier; may be null
     */
    void reset(String sessionId);
}
