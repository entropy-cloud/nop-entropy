package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;

import java.util.concurrent.CompletableFuture;

public interface IAgentEngine {

    AgentMessageAck sendMessage(AgentMessageRequest request);

    CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request);

    /**
     * Fork the session identified by {@code request.getSessionId()} into an
     * independent child session.
     * <p>
     * The parent session id is taken from {@link AgentMessageRequest#getSessionId()};
     * if it is {@code null} or empty the call fails fast with a
     * {@link NopAiAgentException}. The parent session must exist in the
     * backing {@link io.nop.ai.agent.session.ISessionStore}; otherwise the
     * call fails fast.
     * <p>
     * {@code request.getAgentName()} (if non-null) overrides the child
     * session's agent name; {@code request.getMetadata()} is merged into the
     * child session's metadata.
     * <p>
     * When {@code inheritContext} is {@code true}, the child receives an
     * independent snapshot of the parent's message history, planId reference,
     * and metadata. When {@code false}, the child starts empty.
     * <p>
     * A {@link AgentEventType#SESSION_FORKED} event is published on fork.
     *
     * @param request        the fork request (carries parent sessionId, agentName, metadata)
     * @param inheritContext whether to inherit the parent's message history, planId, and metadata
     * @return a completed future holding the new child session id
     */
    default CompletableFuture<String> forkSession(AgentMessageRequest request, boolean inheritContext) {
        throw new UnsupportedOperationException("forkSession requires Phase 2 ISessionStore");
    }

    default AgentExecStatus getSessionStatus(String sessionId) {
        throw new UnsupportedOperationException("getSessionStatus requires Phase 2");
    }

    default CompletableFuture<Void> cancelSession(String sessionId, String reason, boolean forced) {
        throw new UnsupportedOperationException("cancelSession requires Phase 2");
    }

    /**
     * Resume a denial-ledger-paused session (design §6.2
     * {@code pauseBehavior = sticky}): this is the human-intervention recovery
     * entry point that clears the pause and re-executes the session.
     * <p>
     * Only a session with status {@link AgentExecStatus#paused} can be resumed.
     * The implementation calls {@link io.nop.ai.agent.security.IDenialLedger#reset}
     * to clear the pause (removing the per-session denial count), publishes a
     * {@link AgentEventType#SESSION_RESUMED} event carrying the
     * {@code approver}, {@code reason}, and {@code preResetDenialCount} for
     * audit, then re-executes the session as a transparent continuation of its
     * existing conversation history (no new user message is appended).
     * <p>
     * Calling this on a non-paused session, a session that does not exist, or
     * an engine with the default {@code NoOpDenialLedger} (which never pauses)
     * fails fast with a {@link NopAiAgentException} rather than silently
     * no-op'ing. This enforces the sticky contract: only an explicit resume
     * clears a pause; auto-recovery is forbidden.
     *
     * @param sessionId the paused session to resume; must exist and be paused
     * @param approver  the identity of the human operator performing the
     *                  recovery (recorded for audit; never used for permission
     *                  checks in this contract)
     * @param reason    a free-text reason for the recovery (recorded for audit)
     * @return a future that completes with the result of the re-execution
     */
    default CompletableFuture<AgentExecutionResult> resumeSession(String sessionId, String approver, String reason) {
        throw new UnsupportedOperationException("resumeSession requires a registered denial ledger and a paused session");
    }
}
