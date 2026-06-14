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
}
