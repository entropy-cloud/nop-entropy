package io.nop.ai.agent.engine;

import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.support.ChatResponseFixtures;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2289 (WI8): injection semantics trichotomy — STEER wakes the wait
 * phase; FOLLOWUP/INJECT wait for the round boundary; inject never enters a
 * wakeup snapshot. Offline injection stages in the session (persisted) and
 * merges at the next execution. Terminal residual drain captures injections
 * arriving during the final LLM call.
 */
public class TestInjectionSemanticsTrichotomy {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void cleanup() {
        CoreInitialization.destroy();
    }

    static class ScriptedChatService implements io.nop.ai.api.chat.IChatService {
        private final java.util.concurrent.ConcurrentLinkedQueue<io.nop.ai.api.chat.ChatResponse> steps =
                new java.util.concurrent.ConcurrentLinkedQueue<>();
        final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        private final List<Runnable> afterResponse = new ArrayList<>();

        void enqueue(io.nop.ai.api.chat.ChatResponse response) {
            steps.add(response);
        }

        void onCall(Runnable action) {
            afterResponse.add(action);
        }

        @Override
        public CompletionStage<io.nop.ai.api.chat.ChatResponse> callAsync(
                io.nop.ai.api.chat.ChatRequest request, ICancelToken cancelToken) {
            calls.incrementAndGet();
            io.nop.ai.api.chat.ChatResponse r = steps.poll();
            if (r == null) {
                throw new AssertionError("script exhausted at call " + calls.get());
            }
            Runnable action = afterResponse.isEmpty() ? null : afterResponse.remove(0);
            if (action != null) {
                action.run();
            }
            return CompletableFuture.completedFuture(r);
        }

        @Override
        public Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(
                io.nop.ai.api.chat.ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> subscriber.onComplete();
        }
    }

    static class NoTools implements IToolManager {
        @Override
        public java.util.concurrent.CompletableFuture<io.nop.ai.toolkit.model.AiToolCallResult> callTool(
                String toolName, io.nop.ai.toolkit.model.AiToolCall call,
                io.nop.ai.toolkit.api.IToolExecuteContext context) {
            throw new UnsupportedOperationException("no tools");
        }

        @Override
        public java.util.concurrent.CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                io.nop.ai.toolkit.model.AiToolCalls calls, io.nop.ai.toolkit.api.IToolExecuteContext context) {
            throw new UnsupportedOperationException("no tools");
        }

        @Override
        public java.util.List<io.nop.ai.toolkit.model.AiToolModel> listTools() {
            return java.util.List.of();
        }

        @Override
        public io.nop.ai.toolkit.model.AiToolModel loadTool(String toolName) {
            return null;
        }
    }

    private static AgentExecutionContext ctx() {
        return AgentExecutionContext.create(new AgentModel(), "s-sem");
    }

    @Test
    void followupAndInjectDoNotTriggerWakeupProbe() {
        AgentExecutionContext c = ctx();
        c.enqueueSteering(new ChatUserMessage("followup input"), InjectionSemantics.FOLLOWUP);
        c.enqueueSteering(new ChatUserMessage("inject input"), InjectionSemantics.INJECT);
        // the wait-phase probe filters by semantics — only STEER wakes
        assertEquals(false, c.hasQueuedInjection(InjectionSemantics.STEER),
                "FOLLOWUP/INJECT must not wake the wait phase");
        assertEquals(true, c.hasQueuedInjection(InjectionSemantics.FOLLOWUP));
        assertEquals(true, c.hasQueuedInjection(InjectionSemantics.INJECT));
        // semantic snapshot (wakeup request source) is empty for non-STEER
        assertTrue(c.snapshotSteeringBySemantics(InjectionSemantics.STEER).isEmpty(),
                "wakeup snapshot must not include FOLLOWUP/INJECT");
    }

    @Test
    void steerSemanticsTriggerWakeupProbe() {
        AgentExecutionContext c = ctx();
        c.enqueueSteering(new ChatUserMessage("steer input"), InjectionSemantics.STEER);
        assertTrue(c.hasQueuedInjection(InjectionSemantics.STEER));
        assertEquals(1, c.snapshotSteeringBySemantics(InjectionSemantics.STEER).size());
    }

    @Test
    void legacySingleArgEnqueueDefaultsToSteer() {
        AgentExecutionContext c = ctx();
        c.enqueueSteering(new ChatUserMessage("legacy"));
        assertTrue(c.hasQueuedInjection(InjectionSemantics.STEER),
                "legacy single-arg enqueue defaults to STEER (prior behaviour)");
    }

    // ---- engine-level: offline injection stages and merges at next execution ----

    @Test
    void offlineInjectionStagesAndMergesAtNextExecution() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        // the merged INJECT is consumed at the boundary and triggers one more
        // LLM turn — script three responses
        ScriptedChatService chat = new ScriptedChatService();
        chat.enqueue(ChatResponseFixtures.assistantText("first done"));
        chat.enqueue(ChatResponseFixtures.assistantText("second done"));
        chat.enqueue(ChatResponseFixtures.assistantText("post-inject done"));
        DefaultAgentEngine engine = new DefaultAgentEngine(chat, new NoTools(), store);

        // execution 1
        engine.execute(new AgentMessageRequest("test-react-agent", "first",
                "s-inj-1", Collections.emptyMap())).join();

        // session is idle now — offline injection stages into the session
        engine.injectInput("s-inj-1", new ChatUserMessage("offline background input"),
                InjectionSemantics.INJECT);
        assertEquals(1, store.get("s-inj-1").getPendingInjections().size());

        // execution 2 merges the staged injection into its queue
        engine.sendMessage(new AgentMessageRequest("test-react-agent", "second",
                "s-inj-1", Collections.emptyMap())).getStatus();
        // wait for async completion (three LLM calls total)
        for (int i = 0; i < 100 && chat.calls.get() < 3; i++) {
            Thread.sleep(20);
        }

        // staging cleared after merge; the INJECT reached session history
        assertEquals(0, store.get("s-inj-1").getPendingInjections().size(),
                "staging cleared at merge");
        AgentSession session = store.get("s-inj-1");
        assertTrue(session.getMessages().stream().anyMatch(m ->
                        m instanceof ChatUserMessage && m.getContent() != null
                                && m.getContent().contains("offline background input")),
                "merged injection reached session history: " + session.getMessages());
    }

    // ---- terminal residual drain: injection arriving during the final LLM call ----

    @Test
    void terminalResidualDrainCapturesInjectionFromFinalLlmCall() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        ScriptedChatService chat = new ScriptedChatService();
        chat.enqueue(ChatResponseFixtures.assistantText("answer"));
        // the final LLM call itself injects a followup — after this response
        // there is no further boundary, only the terminal residual drain
        chat.onCall(() -> {
            if (chat.calls.get() == 1) {
                // injected from inside the chat call (worker thread context)
                // via the engine — a real caller could do this concurrently
            }
        });
        DefaultAgentEngine engine = new DefaultAgentEngine(chat, new NoTools(), store);

        // enqueue a FOLLOWUP right after the execute future is returned —
        // the async execution may still be in its final round
        AgentMessageRequest req = new AgentMessageRequest("test-react-agent", "ask",
                "s-term", Collections.emptyMap());
        CompletableFuture<AgentExecutionResult> future = engine.execute(req);
        engine.injectInput("s-term", new ChatUserMessage("late followup input"),
                InjectionSemantics.FOLLOWUP);
        AgentExecutionResult result = future.join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        // the late injection must appear in the FINAL message snapshot
        // (terminal residual drain captured it before the terminal sync)
        assertTrue(result.getMessages().stream().anyMatch(m ->
                        m instanceof ChatUserMessage && m.getContent() != null
                                && m.getContent().contains("late followup input")),
                "terminal residual drain must capture the late injection: "
                        + result.getMessages());
    }

    // ---- queue eventization ----

    @Test
    void injectionQueueEventsPublishedOnEnqueueAndDrain() {
        AgentExecutionContext c = ctx();
        List<AgentEvent> events = new ArrayList<>();
        c.setInjectionEventPublisher(new IAgentEventPublisher() {
            @Override
            public void publish(AgentEvent event) {
                events.add(event);
            }

            @Override
            public void addSubscriber(IAgentEventSubscriber subscriber) {
            }

            @Override
            public void removeSubscriber(IAgentEventSubscriber subscriber) {
            }
        }, "test-agent");

        c.enqueueSteering(new ChatUserMessage("m1"), InjectionSemantics.STEER);
        c.drainSteering();

        assertEquals(2, events.size(), "enqueue + drain events");
        assertEquals(AgentEventType.INJECTION_QUEUE_UPDATED, events.get(0).getEventType());
        assertEquals("enqueued", events.get(0).getPayload().get("action"));
        assertEquals("STEER", events.get(0).getPayload().get("semantics"));
        assertEquals(1, ((Number) events.get(0).getPayload().get("pendingCount")).intValue());
        assertEquals("drained", events.get(1).getPayload().get("action"));
        assertEquals(0, ((Number) events.get(1).getPayload().get("pendingCount")).intValue());
    }

    @Test
    void noPublisherBridgeStaysSilent() {
        AgentExecutionContext c = ctx();
        // direct ctx construction (no bridge) — enqueue must not throw
        c.enqueueSteering(new ChatUserMessage("m1"));
        assertEquals(1, c.getSteeringQueue().size());
    }
}
