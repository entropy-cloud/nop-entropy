package io.nop.ai.agent.conflict;

/**
 * Outcome of a write-conflict resolution performed by an
 * {@link IConflictStrategy}. Mirrors the two terminal states of a conflict
 * consultation: the current write intent is either permitted to proceed
 * ({@link #ALLOW}) or rejected ({@link #DENY}) because it conflicts with
 * another session's existing write intent on the same file.
 *
 * <p>Design {@code nop-ai-agent-multi-agent.md} §4.4.
 */
public enum ConflictDecision {
    /**
     * The current write intent does not conflict (or the strategy chose to
     * permit the conflict). The dispatch path registers the intent and
     * proceeds with the tool call.
     */
    ALLOW,

    /**
     * The current write intent conflicts with another session's existing
     * write intent and the strategy chose to reject it. The dispatch path
     * takes the standard deny route (error response + audit event +
     * denial-ledger recording).
     */
    DENY
}
