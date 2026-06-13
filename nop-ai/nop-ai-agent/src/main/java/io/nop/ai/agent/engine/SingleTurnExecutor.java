package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class SingleTurnExecutor implements IAgentExecutor {

    private final IChatService chatService;
    private final IAgentEventPublisher eventPublisher;

    public SingleTurnExecutor(IChatService chatService, IAgentEventPublisher eventPublisher) {
        this.chatService = chatService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public CompletionStage<AgentExecutionResult> execute(AgentExecutionContext ctx) {
        AgentModel agentModel = ctx.getAgentModel();
        String agentName = agentModel != null ? agentModel.getName() : null;
        String sessionId = ctx.getSessionId();

        ctx.setStatus(AgentExecStatus.running);

        publishEvent(AgentEventType.EXECUTION_STARTED, sessionId, agentName,
                Map.of("agentName", agentName != null ? agentName : ""));

        try {
            ChatRequest request = new ChatRequest(new ArrayList<>(ctx.getMessages()));
            ChatResponse response = chatService.call(request, null);

            if (!response.isSuccess()) {
                ctx.setStatus(AgentExecStatus.failed);
                ctx.setLastError(response.getError());

                publishErrorEvent(AgentEventType.EXECUTION_FAILED, sessionId, agentName,
                        response.getError());

                return CompletableFuture.completedFuture(AgentExecutionResult.fromContext(ctx));
            }

            ChatAssistantMessage assistantMsg = response.getMessage();
            ctx.addMessage(assistantMsg);

            if (response.getUsage() != null) {
                int promptTokens = response.getPromptTokens() != null ? response.getPromptTokens() : 0;
                int completionTokens = response.getCompletionTokens() != null ? response.getCompletionTokens() : 0;
                ctx.setTokensUsed(ctx.getTokensUsed() + promptTokens + completionTokens);
            }

            ctx.setStatus(AgentExecStatus.completed);

            Map<String, Object> completedPayload = new HashMap<>();
            completedPayload.put("totalIterations", ctx.getCurrentIteration());
            completedPayload.put("totalTokensUsed", ctx.getTokensUsed());
            completedPayload.put("durationMs", System.currentTimeMillis() - ctx.getStartTimeMs());
            publishEvent(AgentEventType.EXECUTION_COMPLETED, sessionId, agentName, completedPayload);

        } catch (Exception e) {
            ctx.setStatus(AgentExecStatus.failed);
            ctx.setLastError(e.toString());

            publishErrorEvent(AgentEventType.EXECUTION_FAILED, sessionId, agentName, e.toString());
        }

        return CompletableFuture.completedFuture(AgentExecutionResult.fromContext(ctx));
    }

    private void publishEvent(AgentEventType type, String sessionId, String agentName,
                              Map<String, Object> payload) {
        if (eventPublisher != null) {
            eventPublisher.publish(AgentEvent.create(type, sessionId, agentName, payload));
        }
    }

    private void publishErrorEvent(AgentEventType type, String sessionId, String agentName,
                                   String error) {
        if (eventPublisher != null) {
            eventPublisher.publish(AgentEvent.createError(type, sessionId, agentName, error));
        }
    }
}
