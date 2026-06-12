package io.nop.ai.agent.engine;

import java.util.concurrent.CompletableFuture;

public interface IAgentEngine {

    AgentMessageAck sendMessage(AgentMessageRequest request);

    CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request);
}
