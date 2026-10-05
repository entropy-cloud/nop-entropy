package io.nop.ai.agent.engine;

import io.nop.ai.agent.engine.AgentEventType;
import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.hook.HookContext;
import io.nop.ai.agent.hook.HookResult;
import io.nop.ai.agent.hook.IAgentLifecycleHook;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.reliability.IRetryPolicy;
import io.nop.ai.core.reliability.RetryContext;
import io.nop.ai.core.reliability.RetryOutcome;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2285 (WI4 Phase 1): the fail-soft wakeup LLM call — terminal failure
 * (retry exhaustion on a non-retryable classification) must NOT mutate the
 * execution state (no ctx failed, no EXECUTION_FAILED event); the success
 * path behaves exactly like the regular call.
 */
public class TestWakeupCallFailSoft {

    private static AgentExecutionContext ctx() {
        AgentExecutionContext c = new AgentExecutionContext(new AgentModel());
        c.setStatus(AgentExecStatus.running);
        return c;
    }

    private static LlmCallCoordinator coordinator(IChatService service, IRetryPolicy policy,
                                                  List<AgentEventType> events) {
        AgentHookInvoker invoker = events != null
                ? new EventRecordingInvoker(new DefaultHookRegistry(), events)
                : new AgentHookInvoker(new DefaultHookRegistry(), null);
        return new LlmCallCoordinator(service, policy,
                io.nop.ai.core.reliability.AlwaysClosed.alwaysClosed(),
                PassThroughModelRouter.passThrough(), 0, null, invoker);
    }

    private static ChatRequest request() {
        ChatRequest req = new ChatRequest(new ArrayList<>(List.of(new ChatUserMessage("hb"))));
        req.setOptions(ChatOptions.builder().provider("p").model("m").build());
        return req;
    }

    private static ChatResponse successResponse() {
        ChatResponse r = new ChatResponse();
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("ok");
        r.addMessage(msg);
        return r;
    }


    private static ChatResponse errorResponse(ErrorClassification classification) {
        return ChatResponse.error(classification, 500, "boom", "provider down", null);
    }

    @Test
    void terminalFailureKeepsExecutionStateUntouched() {
        IChatService failing = fixed(errorResponse(ErrorClassification.NON_TRANSIENT));
        AgentExecutionContext ctx = ctx();
        List<AgentEventType> events = new ArrayList<>();
        // NON_TRANSIENT → policy stops immediately → terminal failure path
        LlmCallCoordinator coordinator = coordinator(failing,
                (RetryContext retryCtx) -> RetryOutcome.stop(), events);

        LlmCallCoordinator.LlmCallResult result = coordinator.doWakeupCallWithRetry(
                request(), ctx, "s1", "agent", request().getOptions());

        assertFalse(result.isSuccess(), "wakeup call reports failure");
        assertEquals(AgentExecStatus.running, ctx.getStatus(),
                "fail-soft: ctx status must stay running");
        assertTrue(events.isEmpty(), "fail-soft: no EXECUTION_FAILED event");
    }

    @Test
    void regularCallTerminalFailureStillFailsLoud() {
        IChatService failing = fixed(errorResponse(ErrorClassification.NON_TRANSIENT));
        AgentExecutionContext ctx = ctx();
        List<AgentEventType> events = new ArrayList<>();
        LlmCallCoordinator coordinator = coordinator(failing,
                (RetryContext retryCtx) -> RetryOutcome.stop(), events);

        LlmCallCoordinator.LlmCallResult result = coordinator.doLlmCallWithRetry(
                request(), ctx, "s1", "agent", request().getOptions());

        assertFalse(result.isSuccess());
        assertEquals(AgentExecStatus.failed, ctx.getStatus(),
                "regular call keeps the fail-loud semantics");
        assertTrue(events.contains(AgentEventType.EXECUTION_FAILED));
    }

    @Test
    void successRecordsLikeRegularCall() {
        IChatService ok = fixed(successResponse());
        AgentExecutionContext ctx = ctx();
        LlmCallCoordinator coordinator = coordinator(ok,
                (RetryContext retryCtx) -> RetryOutcome.stop(), new ArrayList<>());

        LlmCallCoordinator.LlmCallResult result = coordinator.doWakeupCallWithRetry(
                request(), ctx, "s1", "agent", request().getOptions());

        assertTrue(result.isSuccess());
        assertEquals(AgentExecStatus.running, ctx.getStatus());
    }

    /**
     * Records every published error event type so the fail-soft assertions
     * can verify that EXECUTION_FAILED is never emitted on the wakeup path.
     */
    private static class EventRecordingInvoker extends AgentHookInvoker {
        private final List<AgentEventType> events;

        EventRecordingInvoker(DefaultHookRegistry registry, List<AgentEventType> events) {
            super(registry, null);
            this.events = events;
        }

        @Override
        public void publishErrorEvent(AgentEventType eventType, String sessionId, String agentName,
                                      String error) {
            events.add(eventType);
            super.publishErrorEvent(eventType, sessionId, agentName, error);
        }
    }

    private static IChatService fixed(ChatResponse response) {
        return new IChatService() {
            @Override
            public java.util.concurrent.CompletionStage<ChatResponse> callAsync(ChatRequest request,
                                                                                 io.nop.api.core.util.ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(response);
            }

            @Override
            public java.util.concurrent.Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(
                    ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
                return subscriber -> subscriber.onComplete();
            }
        };
    }
}
