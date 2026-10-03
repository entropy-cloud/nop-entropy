package io.nop.ai.agent.engine;

import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.tool.DefaultBackgroundTaskRegistry;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.toolkit.api.IBackgroundTaskRegistry;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2301 (WI25) Phase 1: the completion-notification bridge is real
 * wiring, not a stub — a terminal background task reaches
 * {@code DefaultAgentEngine.injectInput} as a FOLLOWUP input (staged into the
 * session's pending injections for a non-running session, merged at the next
 * execution), and the bridge failure path moves the task to
 * NOTIFICATION_FAILED (fail-visible).
 */
public class TestBackgroundCompletionBridge {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void cleanup() {
        CoreInitialization.destroy();
    }

    static class NoCallChatService implements io.nop.ai.api.chat.IChatService {
        @Override
        public CompletionStage<io.nop.ai.api.chat.ChatResponse> callAsync(
                io.nop.ai.api.chat.ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException("no llm call expected");
        }

        @Override
        public Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(
                io.nop.ai.api.chat.ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> subscriber.onComplete();
        }
    }

    @Test
    void terminalTaskBridgesIntoSessionPendingInjections() {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = new DefaultAgentEngine(new NoCallChatService(), null, store);
        engine.initBackgroundSupport(null, null);
        IBackgroundTaskRegistry registry = engine.getBackgroundTaskRegistry();
        assertNotNull(registry, "engine must own a background task registry after wiring");

        // create the session the way execute() would, then complete a task
        // against it — the notification must land in the session's staged
        // pending injections (FOLLOWUP, non-running session)
        String sessionId = "s-bg-bridge";
        store.getOrCreate(sessionId, "test-react-agent");

        String taskId = registry.submit("bash", sessionId, "call-bg-1", 3_600_000L);
        registry.complete(taskId, IBackgroundTaskRegistry.STATUS_COMPLETED, "build finished");

        assertEquals(IBackgroundTaskRegistry.STATUS_COMPLETED,
                registry.snapshot(taskId).getStatus());
        assertTrue(registry.snapshot(taskId).isNotificationSent(),
                "successful bridge must set the exactly-once claim");

        io.nop.ai.agent.session.AgentSession session = store.get(sessionId);
        assertNotNull(session);
        String staged = stagedInjections(session);
        assertTrue(staged.contains("[BACKGROUND_TASK taskId=" + taskId),
                "completion notification must be staged into the session: " + staged);
        assertTrue(staged.contains("status=COMPLETED"), "notification carries the terminal status");
        assertTrue(staged.contains("task-output"), "notification points at the single read channel");
    }

    @Test
    void bridgeFailureIsFailVisibleNotSilent() {
        InMemorySessionStore store = new InMemorySessionStore() {
            @Override
            public void save(io.nop.ai.agent.session.AgentSession session) {
                if (session.getSessionId().equals("s-bg-fail")
                        && !session.getPendingInjections().isEmpty()) {
                    throw new IllegalStateException("store down");
                }
                super.save(session);
            }
        };
        DefaultAgentEngine engine = new DefaultAgentEngine(new NoCallChatService(), null, store);
        engine.initBackgroundSupport(null, null);
        IBackgroundTaskRegistry registry = engine.getBackgroundTaskRegistry();
        String sessionId = "s-bg-fail";
        store.getOrCreate(sessionId, "test-react-agent");

        DefaultBackgroundTaskRegistry defaultRegistry = (DefaultBackgroundTaskRegistry) registry;
        String taskId = defaultRegistry.submit("bash", sessionId, "call-bg-2", 1000L);
        defaultRegistry.complete(taskId, IBackgroundTaskRegistry.STATUS_COMPLETED, "out");

        assertEquals(IBackgroundTaskRegistry.STATUS_NOTIFICATION_FAILED,
                defaultRegistry.rawStatus(taskId),
                "bridge failure must be fail-visible (NOTIFICATION_FAILED)");
        assertEquals(false, registry.snapshot(taskId).isNotificationSent());
    }

    private static String stagedInjections(io.nop.ai.agent.session.AgentSession session) {
        StringBuilder sb = new StringBuilder();
        for (io.nop.ai.agent.session.AgentSession.PendingInjection injection : session.getPendingInjections()) {
            sb.append(injection.getSemantics()).append(':').append(injection.getMessageJson()).append('\n');
        }
        return sb.toString();
    }
}
