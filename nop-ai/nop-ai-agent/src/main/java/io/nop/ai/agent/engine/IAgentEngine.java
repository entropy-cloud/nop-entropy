package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;

import java.util.concurrent.CompletableFuture;

public interface IAgentEngine {

    AgentMessageAck sendMessage(AgentMessageRequest request);

    CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request);

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
