package io.nop.ai.agent.engine;

import io.nop.ai.agent.memory.IAiMemoryStore;
import io.nop.ai.agent.memory.IMemoryStoreProvider;
import io.nop.ai.agent.memory.InMemoryAiMemoryStore;
import io.nop.ai.agent.memory.InMemoryMemoryStoreProvider;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Phase 1 wiring tests:
 * <ul>
 *     <li>{@link AgentToolExecuteContext#getMemoryStore()} returns the store
 *         passed in the 14-arg constructor and {@code null} when constructed
 *         via the legacy 13-arg overload (backward compat)</li>
 *     <li>{@link DefaultAgentEngine} defaults to an
 *         {@link InMemoryMemoryStoreProvider} and accepts an override via
 *         {@code setMemoryStoreProvider}</li>
 *     <li>{@link IMemoryStoreProvider#getOrCreate(String)} returns the same
 *         instance for the same sessionId (per-session isolation contract)</li>
 * </ul>
 *
 * <p>The dispatch-loop end-to-end wiring (verifying the executor actually calls
 * {@code provider.getOrCreate(sessionId)} and feeds the result into the
 * context) is exercised by the Phase 2 end-to-end test that runs the full
 * ReAct loop with working-memory tools.
 */
public class TestMemoryStoreProviderWiring {

    @Test
    void contextLegacyConstructorLeavesMemoryStoreNull() {
        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                new java.io.File("."),
                Collections.emptyMap(),
                0L,
                null,
                null,
                null,
                null,
                null,
                "sess-1",
                "agent-1",
                null,
                null,
                null);

        assertNull(ctx.getMemoryStore(),
                "13-arg legacy constructor must leave memoryStore null (backward compat)");
    }

    @Test
    void contextFullConstructorCarriesMemoryStore() {
        IAiMemoryStore store = new InMemoryAiMemoryStore();
        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                new java.io.File("."),
                Collections.emptyMap(),
                0L,
                null,
                null,
                null,
                null,
                null,
                "sess-1",
                "agent-1",
                null,
                null,
                null,
                store);

        assertSame(store, ctx.getMemoryStore());
    }

    @Test
    void providerGetOrCreatePerSessionIsolation() {
        InMemoryMemoryStoreProvider provider = new InMemoryMemoryStoreProvider();
        IAiMemoryStore a1 = provider.getOrCreate("session-A");
        IAiMemoryStore a2 = provider.getOrCreate("session-A");
        IAiMemoryStore b = provider.getOrCreate("session-B");

        assertSame(a1, a2, "Same sessionId must return the same store instance");
        assertNotNull(b);
    }

    @Test
    void engineDefaultsToInMemoryMemoryStoreProvider() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        IMemoryStoreProvider provider = engine.getMemoryStoreProvider();
        assertNotNull(provider, "Engine must default to a non-null memory store provider");
        // The default provider is functional: returns a real store
        IAiMemoryStore store = provider.getOrCreate("test-session");
        assertNotNull(store, "Default provider must return a non-null store");
    }

    @Test
    void engineSetMemoryStoreProviderOverridesDefault() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        InMemoryMemoryStoreProvider custom = new InMemoryMemoryStoreProvider();
        engine.setMemoryStoreProvider(custom);

        assertSame(custom, engine.getMemoryStoreProvider());
    }

    private static IChatService noOpChatService() {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return null;
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return null;
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return null;
            }
        };
    }

    private static IToolManager noOpToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return null;
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
                return null;
            }
        };
    }
}
