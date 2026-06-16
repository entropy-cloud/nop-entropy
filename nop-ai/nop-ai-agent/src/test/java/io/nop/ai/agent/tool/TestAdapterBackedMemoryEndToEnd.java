package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentEvent;
import io.nop.ai.agent.engine.AgentEventType;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.memory.AdapterBackedAiMemoryStore;
import io.nop.ai.agent.memory.AdapterBackedMemoryStoreProvider;
import io.nop.ai.agent.memory.IAiMemoryStore;
import io.nop.ai.agent.memory.InMemoryEmbeddingAdapter;
import io.nop.ai.agent.memory.InMemoryStorageAdapter;
import io.nop.ai.agent.memory.InMemoryVectorAdapter;
import io.nop.ai.agent.memory.NoOpEmbeddingAdapter;
import io.nop.ai.agent.memory.NoOpVectorAdapter;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 215 Phase 2 — wiring + end-to-end verification for the adapter-backed
 * working-memory store.
 *
 * <p><b>Wiring verification (Rule #23)</b>: an adapter-backed
 * {@link AdapterBackedMemoryStoreProvider} is injected into
 * {@link DefaultAgentEngine} via {@code setMemoryStoreProvider}. The ReAct
 * dispatch loop must resolve a per-session {@link AdapterBackedAiMemoryStore}
 * and expose it to the memory tools — asserted by capturing the store observed
 * at tool-call time and confirming it is an {@code AdapterBackedAiMemoryStore}
 * (NOT the shipped {@code InMemoryAiMemoryStore}).
 *
 * <p><b>End-to-end (Rule #22, Anti-Hollow)</b>: the agent's ReAct loop first
 * calls {@code write-memory} (add) then {@code search-memory}. The
 * full path LLM tool_call → IToolManager.callTool → WriteMemoryExecutor /
 * SearchMemoryExecutor → AgentToolExecuteContext.getMemoryStore() →
 * AdapterBackedAiMemoryStore → (Storage + Embedding + Vector adapters) is
 * proven connected: the search result body contains the content written one
 * turn earlier.
 *
 * <p>Two scenarios:
 * <ul>
 *   <li>Functional adapter triplet (in-memory) → semantic search path.</li>
 *   <li>NoOp embedding + NoOp vector → keyword fallback path (search still
 *       works, the adapter-backed store is still the resolved instance).</li>
 * </ul>
 */
public class TestAdapterBackedMemoryEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static final AtomicReference<IAiMemoryStore> firstStoreSeen = new AtomicReference<>();
    private static final AtomicInteger storeObservationCount = new AtomicInteger(0);

    private IToolManager toolManagerWithMemoryTools() {
        ReadMemoryExecutor readMemory = new ReadMemoryExecutor();
        WriteMemoryExecutor writeMemory = new WriteMemoryExecutor();
        SearchMemoryExecutor searchMemory = new SearchMemoryExecutor();
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                if (context instanceof io.nop.ai.agent.engine.AgentToolExecuteContext) {
                    IAiMemoryStore observed = ((io.nop.ai.agent.engine.AgentToolExecuteContext) context).getMemoryStore();
                    if (observed != null) {
                        firstStoreSeen.compareAndSet(null, observed);
                        storeObservationCount.incrementAndGet();
                    }
                }
                switch (toolName) {
                    case "read-memory":
                        return readMemory.executeAsync(call, context).toCompletableFuture();
                    case "write-memory":
                        return writeMemory.executeAsync(call, context).toCompletableFuture();
                    case "search-memory":
                        return searchMemory.executeAsync(call, context).toCompletableFuture();
                    default:
                        return CompletableFuture.completedFuture(AiToolCallResult.successResult(call.getId(), ""));
                }
            }

            @Override
            public CompletableFuture<AiToolCallsResponse> callTools(AiToolCalls calls, IToolExecuteContext context) {
                return null;
            }

            @Override
            public List<AiToolModel> listTools() {
                return Collections.emptyList();
            }

            @Override
            public AiToolModel loadTool(String toolName) {
                AiToolModel model = new AiToolModel();
                model.setName(toolName);
                model.setDescription("Mock tool: " + toolName);
                return model;
            }
        };
    }

    private ChatAssistantMessage turn(int n, String writeKey, String writtenContent, String searchQuery) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        if (n == 0) {
            // turn 1: write-memory (add)
            msg.setContent("");
            ChatToolCall call = new ChatToolCall();
            call.setId("w1");
            call.setName("write-memory");
            Map<String, Object> args = new HashMap<>();
            args.put("action", "add");
            args.put("key", writeKey);
            args.put("type", "note");
            args.put("content", writtenContent);
            call.setArguments(args);
            msg.setToolCalls(List.of(call));
        } else if (n == 1) {
            // turn 2: search-memory
            msg.setContent("");
            ChatToolCall call = new ChatToolCall();
            call.setId("s1");
            call.setName("search-memory");
            Map<String, Object> args = new HashMap<>();
            args.put("query", searchQuery);
            call.setArguments(args);
            msg.setToolCalls(List.of(call));
        } else {
            msg.setContent("Done.");
        }
        return msg;
    }

    /**
     * Runs an engine with the given provider, drives write-memory then
     * search-memory, returns the captured tool-response bodies joined as a
     * string for assertions.
     */
    private AgentExecutionResult runEngine(AdapterBackedMemoryStoreProvider provider,
                                           String writeKey, String writtenContent, String searchQuery) throws Exception {
        firstStoreSeen.set(null);
        storeObservationCount.set(0);

        AtomicInteger callCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(
                        ChatResponse.success(turn(callCount.getAndIncrement(), writeKey, writtenContent, searchQuery)));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return ChatResponse.success(turn(callCount.getAndIncrement(), writeKey, writtenContent, searchQuery));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManagerWithMemoryTools());
        engine.setMemoryStoreProvider(provider);

        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        engine.getEventPublisher().addSubscriber(events::add);

        AgentMessageRequest req = new AgentMessageRequest("test-agent", "write then search");
        CompletableFuture<AgentExecutionResult> future = engine.execute(req);
        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Execution should complete. Messages: " + result.getMessages());
        return result;
    }

    private AdapterBackedMemoryStoreProvider functionalProvider() {
        // Per-session factory: each session gets its own isolated adapter triplet
        // (the in-memory adapters hold per-session mutable state).
        return new AdapterBackedMemoryStoreProvider(
                sid -> new AdapterBackedAiMemoryStore(
                        new InMemoryStorageAdapter(),
                        new InMemoryEmbeddingAdapter(),
                        new InMemoryVectorAdapter()));
    }

    private AdapterBackedMemoryStoreProvider keywordFallbackProvider() {
        return new AdapterBackedMemoryStoreProvider(
                sid -> new AdapterBackedAiMemoryStore(
                        new InMemoryStorageAdapter(),
                        NoOpEmbeddingAdapter.instance(),
                        NoOpVectorAdapter.instance()));
    }

    @Test
    void writeThenSearchRoundTripViaFunctionalAdapters() throws Exception {
        String content = "the quick brown fox preference note";
        AgentExecutionResult result = runEngine(functionalProvider(), "user-pref", content, "quick fox");

        // End-to-end: search-memory result body must contain the written content
        boolean contentReturned = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .anyMatch(m -> m.getContent() != null && m.getContent().contains(content));
        assertTrue(contentReturned,
                "search-memory must return the content written one turn earlier (e2e, functional adapters). "
                        + "Messages: " + result.getMessages());

        // Wiring verification: the dispatch loop resolved an AdapterBackedAiMemoryStore
        assertNotNull(firstStoreSeen.get(), "A non-null store must be resolved at tool-call time");
        assertTrue(firstStoreSeen.get() instanceof AdapterBackedAiMemoryStore,
                "Resolved store must be AdapterBackedAiMemoryStore, but was: "
                        + firstStoreSeen.get().getClass().getName());
        assertTrue(((AdapterBackedAiMemoryStore) firstStoreSeen.get()).isSemanticSearchEnabled(),
                "Functional in-memory embedding adapter must enable semantic search");
        assertTrue(storeObservationCount.get() >= 2,
                "Both write-memory and search-memory must observe a non-null store. count="
                        + storeObservationCount.get());
    }

    @Test
    void writeThenSearchRoundTripViaKeywordFallback() throws Exception {
        // NoOp embedding → search falls back to keyword substring matching.
        String content = "BudgetedKeywordFallbackContent";
        AgentExecutionResult result = runEngine(keywordFallbackProvider(), "k1", content, "KeywordFallback");

        boolean contentReturned = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .anyMatch(m -> m.getContent() != null && m.getContent().contains(content));
        assertTrue(contentReturned,
                "search-memory must still return written content on the keyword-fallback path. Messages: "
                        + result.getMessages());

        assertNotNull(firstStoreSeen.get());
        assertTrue(firstStoreSeen.get() instanceof AdapterBackedAiMemoryStore);
        assertFalse(((AdapterBackedAiMemoryStore) firstStoreSeen.get()).isSemanticSearchEnabled(),
                "NoOp embedding adapter must report semantic search disabled (keyword fallback active)");
    }
}
