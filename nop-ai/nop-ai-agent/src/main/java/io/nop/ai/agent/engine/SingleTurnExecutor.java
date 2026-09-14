package io.nop.ai.agent.engine;

import io.nop.ai.agent.engine.NopAiAgentException;
import static io.nop.ai.agent.NopAiAgentErrors.ERR_AGENT_INTERNAL_DETAIL;
import static io.nop.ai.agent.NopAiAgentErrors.ARG_DETAIL;
import io.nop.api.core.time.CoreMetrics;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SingleTurnExecutor implements IAgentExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(SingleTurnExecutor.class);

    private final IChatService chatService;
    private final IAgentEventPublisher eventPublisher;
    private final long llmTimeoutMs;
    private final Executor timeoutExecutor;

    /** Backward-compatible constructor: 120s timeout on the common pool. */
    public SingleTurnExecutor(IChatService chatService, IAgentEventPublisher eventPublisher) {
        this(chatService, eventPublisher, 120_000L, ForkJoinPool.commonPool());
    }

    /**
     * Fully-parameterized constructor (I3 adjudication gate-2/SingleTurnExecutor
     * fix): the synchronous {@code chatService.call} is dispatched to
     * {@code timeoutExecutor} and bounded by {@code get(llmTimeoutMs)} so a
     * hanging LLM call cannot block the calling thread indefinitely. Mirrors
     * {@code LlmCallCoordinator.callChatWithTimeout} (same config source:
     * {@code DefaultAgentEngineConfig.llmTimeoutMs} wired by
     * {@code AgentExecutorResolver}).
     */
    public SingleTurnExecutor(IChatService chatService, IAgentEventPublisher eventPublisher,
                              long llmTimeoutMs, Executor timeoutExecutor) {
        this.chatService = chatService;
        this.eventPublisher = eventPublisher;
        this.llmTimeoutMs = llmTimeoutMs;
        this.timeoutExecutor = timeoutExecutor != null ? timeoutExecutor : ForkJoinPool.commonPool();
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
            ChatResponse response = callWithTimeout(request);

            if (!response.isSuccess()) {
                ctx.setStatus(AgentExecStatus.failed);
                ctx.setLastError(response.getError());

                publishErrorEvent(AgentEventType.EXECUTION_FAILED, sessionId, agentName,
                        response.getError());

                return CompletableFuture.completedFuture(AgentExecutionResult.fromContext(ctx));
            }

            ChatAssistantMessage assistantMsg = extractAssistantMessage(response);
            if (assistantMsg == null) {
                // P2 (tool-call-only responses, real provider form): the
                // response carries only ChatToolCallMessage items. Record an
                // empty assistant message so ctx history stays consistent and
                // no NPE is thrown downstream; observable via INFO log
                // (Minimum Rules #24).
                LOG.info("LLM response carried no ChatAssistantMessage (tool-call-only); recording empty assistant message. session={}", sessionId);
                assistantMsg = new ChatAssistantMessage("");
            }
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
            completedPayload.put("durationMs", CoreMetrics.currentTimeMillis() - ctx.getStartTimeMs());
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

    /**
     * Wall-clock-bounded invocation of the blocking {@code chatService.call}
     * (I3 gate-2/SingleTurnExecutor fix). A hanging LLM call times out after
     * {@code llmTimeoutMs} with a {@link TimeoutException} (wrapped in a
     * {@link CompletionException}), which the caller's failure path converts
     * into an honest failed execution result — the calling thread never
     * blocks indefinitely. {@code get(long, TimeUnit)} (interruptible) is
     * used instead of {@code join()} so a forced cancel can break the wait.
     */
    private ChatResponse callWithTimeout(ChatRequest request) {
        CompletableFuture<ChatResponse> future = CompletableFuture.supplyAsync(
                () -> chatService.call(request, null), timeoutExecutor);
        try {
            return future.get(llmTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "single-turn LLM call interrupted (forced cancel or thread interrupt)");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new CompletionException(cause != null ? cause : e);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new CompletionException(e);
        }
    }

    private static ChatAssistantMessage extractAssistantMessage(ChatResponse response) {
        List<ChatMessage> messages = response.getMessages();
        if (messages != null) {
            for (ChatMessage msg : messages) {
                if (msg instanceof ChatAssistantMessage) {
                    return (ChatAssistantMessage) msg;
                }
            }
        }
        return null;
    }
}
