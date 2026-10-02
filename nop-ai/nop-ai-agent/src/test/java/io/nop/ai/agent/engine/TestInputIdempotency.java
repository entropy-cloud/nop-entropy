package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import io.nop.ai.agent.NopAiAgentErrors;
import static io.nop.ai.agent.NopAiAgentErrors.ERR_AGENT_INTERNAL_DETAIL;
import static io.nop.ai.agent.NopAiAgentErrors.ARG_DETAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2288 (WI7): external input redelivery dedup — session-layer
 * persistence-first idempotency (read-check in assemble, record+save in the
 * execution-slot window) with the duplicate contract (sendMessage →
 * "duplicate" ack; execute → failed future with ERR_AGENT_DUPLICATE_INPUT).
 */
public class TestInputIdempotency {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void cleanup() {
        CoreInitialization.destroy();
    }

    private DefaultAgentEngine engine(InMemorySessionStore store) {
        return new DefaultAgentEngine(new StaticChatService(), new NoOpToolMgr(), store);
    }

    static class StaticChatService implements io.nop.ai.api.chat.IChatService {
        @Override
        public java.util.concurrent.CompletionStage<io.nop.ai.api.chat.ChatResponse> callAsync(
                io.nop.ai.api.chat.ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
            io.nop.ai.api.chat.ChatResponse r = new io.nop.ai.api.chat.ChatResponse();
            io.nop.ai.api.chat.messages.ChatAssistantMessage msg = new io.nop.ai.api.chat.messages.ChatAssistantMessage();
            msg.setContent("ok");
            r.addMessage(msg);
            return java.util.concurrent.CompletableFuture.completedFuture(r);
        }

        @Override
        public java.util.concurrent.Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(
                io.nop.ai.api.chat.ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
            return subscriber -> subscriber.onComplete();
        }
    }

    static class NoOpToolMgr implements io.nop.ai.toolkit.api.IToolManager {
        @Override
        public java.util.concurrent.CompletableFuture<io.nop.ai.toolkit.model.AiToolCallResult> callTool(
                String toolName, io.nop.ai.toolkit.model.AiToolCall call,
                io.nop.ai.toolkit.api.IToolExecuteContext context) {
            throw new UnsupportedOperationException("no tools in this test");
        }

        @Override
        public java.util.concurrent.CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                io.nop.ai.toolkit.model.AiToolCalls calls, io.nop.ai.toolkit.api.IToolExecuteContext context) {
            throw new UnsupportedOperationException("no tools in this test");
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

    private AgentMessageRequest request(String sessionId, String key) {
        return new AgentMessageRequest("test-react-agent", "hello " + key,
                sessionId, Collections.emptyMap(), ChannelKind.API, null, key);
    }

    @Test
    void duplicateKeySecondSendMessageGetsDuplicateAckAndSingleUserMessage() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = engine(store);

        // complete execution A first (messages reach the session snapshot at
        // the terminal sync) so the assertion below reads a stable snapshot
        engine.execute(request("s-idem-1", "key-1")).join();
        AgentMessageAck second = engine.sendMessage(request("s-idem-1", "key-1"));
        assertEquals("duplicate", second.getStatus(), "redelivered key → duplicate ack");

        // the session must contain exactly ONE user message
        AgentSession session = store.get("s-idem-1");
        long userMessages = session.getMessages().stream()
                .filter(m -> m instanceof ChatUserMessage).count();
        assertEquals(1, userMessages, "redelivery must not append a second user message");
    }

    @Test
    void nullKeyRequestsAreNeverDeduped() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = engine(store);

        // complete both executions before asserting (messages reach the
        // session snapshot at the terminal sync; sendMessage is async)
        engine.execute(request("s-idem-2", null)).join();
        engine.execute(request("s-idem-2", null)).join();
        AgentSession session = store.get("s-idem-2");
        long userMessages = session.getMessages().stream()
                .filter(m -> m instanceof ChatUserMessage).count();
        assertEquals(2, userMessages);
    }

    @Test
    void executePathDuplicateCompletesFailedFutureWithDuplicateCode() {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = engine(store);
        engine.execute(request("s-idem-3", "key-3")).join();

        java.util.concurrent.CompletableFuture<AgentExecutionResult> redelivery =
                engine.execute(request("s-idem-3", "key-3"));
        assertTrue(redelivery.isCompletedExceptionally(),
                "execute() duplicate contract: future completes exceptionally");
        try {
            redelivery.join();
            org.junit.jupiter.api.Assertions.fail("expected duplicate exception");
        } catch (java.util.concurrent.CompletionException e) {
            Throwable cause = e.getCause();
            assertTrue(cause instanceof NopAiAgentException);
            assertEquals("nop.err.ai.agent.duplicate-input",
                    ((NopAiAgentException) cause).getErrorCode());
        }
    }

    @Test
    void persistenceFirstKeySurvivesStoreRebuild() {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = engine(store);
        engine.sendMessage(request("s-idem-4", "key-4")).getStatus();

        // simulate a restart: same store, NEW engine (fresh in-memory caches) —
        // the recorded key lives in the session the store still holds
        DefaultAgentEngine engine2 = engine(store);
        AgentMessageAck redelivery = engine2.sendMessage(request("s-idem-4", "key-4"));
        assertEquals("duplicate", redelivery.getStatus(),
                "persistence-first: the key was recorded before dispatch, a "
                        + "re-delivery after an engine rebuild is still deduped");
    }

    @Test
    void busySessionRejectionDoesNotPoisonTheKey() throws Exception {
        // B1 regression (behavioural): while execution A is in flight (gated),
        // a DIFFERENT-key delivery B hits the execution-slot busy rejection —
        // which happens BEFORE the record stage, so key-B must stay unrecorded.
        // After A completes, key-B redelivered must EXECUTE (with the B1
        // violation — recording at the read-check stage — key-B would be
        // poisoned and the redelivery silently swallowed as a ghost duplicate).
        java.util.concurrent.CountDownLatch releaseA = new java.util.concurrent.CountDownLatch(1);
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine gatedEngine = new DefaultAgentEngine(
                new GatedChatService(releaseA), new NoOpToolMgr(), store);

        // A: gated in the LLM call (holds the execution slot)
        java.util.concurrent.CompletableFuture<AgentExecutionResult> fa =
                gatedEngine.execute(request("s-idem-5", "key-5"));
        for (int i = 0; i < 250 && !AgentExecStatus.running.equals(gatedEngine.getSessionStatus("s-idem-5")); i++) {
            Thread.sleep(20);
        }
        assertEquals(AgentExecStatus.running, gatedEngine.getSessionStatus("s-idem-5"),
                "execution A must hold the slot");

        // B: DIFFERENT key while A holds the slot → busy rejection (not
        // duplicate — the key is fresh), thrown synchronously, and the key
        // must be UNRECORDED
        try {
            gatedEngine.execute(request("s-idem-5", "key-B"));
            org.junit.jupiter.api.Assertions.fail("expected busy-session rejection");
        } catch (NopAiAgentException e) {
            // the busy-vs-duplicate distinction is carried by the error code
            // itself (getMessage() assertions trip the lint rule); busy is
            // ERR_AGENT_INTERNAL_DETAIL, duplicate is
            // nop.err.ai.agent.duplicate-input
            assertFalse("nop.err.ai.agent.duplicate-input".equals(e.getErrorCode()),
                    "a fresh key must NOT be rejected as duplicate (got: " + e.getErrorCode() + ")");
            assertTrue("nop.err.ai.agent.internal-detail".equals(e.getErrorCode()),
                    "busy rejection error code (got: " + e.getErrorCode() + ")");
        }
        AgentSession session = store.get("s-idem-5");
        assertFalse(session.hasSeenInput("key-B"),
                "B1: busy rejection must not record the fresh key");

        releaseA.countDown();
        fa.join();

        // after A completes, key-B redelivered must EXECUTE
        DefaultAgentEngine freshEngine = new DefaultAgentEngine(
                new StaticChatService(), new NoOpToolMgr(), store);
        AgentExecutionResult redelivery =
                freshEngine.execute(request("s-idem-5", "key-B")).join();
        assertEquals(AgentExecStatus.completed, redelivery.getStatus(),
                "redelivery of the busy-rejected key must execute (not be swallowed)");
    }

    /** Chat service whose completion is externally gated (execution A in flight). */
    static class GatedChatService implements io.nop.ai.api.chat.IChatService {
        private final java.util.concurrent.CountDownLatch latch;

        GatedChatService(java.util.concurrent.CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public java.util.concurrent.CompletionStage<io.nop.ai.api.chat.ChatResponse> callAsync(
                io.nop.ai.api.chat.ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_INTERNAL_DETAIL, e)
                        .param(ARG_DETAIL, "interrupted while gating execution A");
            }
            io.nop.ai.api.chat.ChatResponse r = new io.nop.ai.api.chat.ChatResponse();
            io.nop.ai.api.chat.messages.ChatAssistantMessage msg = new io.nop.ai.api.chat.messages.ChatAssistantMessage();
            msg.setContent("ok");
            r.addMessage(msg);
            return java.util.concurrent.CompletableFuture.completedFuture(r);
        }

        @Override
        public java.util.concurrent.Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(
                io.nop.ai.api.chat.ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
            return subscriber -> subscriber.onComplete();
        }
    }


}
