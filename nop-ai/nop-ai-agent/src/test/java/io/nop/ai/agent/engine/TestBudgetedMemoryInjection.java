package io.nop.ai.agent.engine;

import io.nop.ai.agent.memory.AiMemoryItem;
import io.nop.ai.agent.memory.IAiMemoryStore;
import io.nop.ai.agent.memory.InMemoryMemoryStoreProvider;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 192 Phase 1 unit tests: budgeted working-memory auto-injection into the
 * system prompt via {@link DefaultAgentEngine}'s {@code buildBaseExecutionContext}
 * (shared by {@code doExecute} and {@code resumeSession}).
 *
 * <p>These tests drive the engine's {@code execute} path with a capturing chat
 * service (returns a final assistant message — no tool calls) and assert the
 * first message the LLM receives is a {@link ChatSystemMessage} whose text
 * reflects the injected budgeted memory. A shared helper pre-populates the
 * per-session store before execution so injection has something to consume.
 *
 * <p>The {@code test-agent} resource (no tools → react mode → single LLM call
 * that returns a final message) is used so the ReAct loop terminates after one
 * turn, and the chat-service capture records exactly what the LLM saw.
 */
public class TestBudgetedMemoryInjection {

    private static final String BASE_PROMPT = "You are a helpful assistant.";

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static AiMemoryItem memoryItem(String key, String type, String content,
                                           int priority, boolean pinned) {
        AiMemoryItem item = new AiMemoryItem();
        item.setKey(key);
        item.setType(type);
        item.setContent(content);
        item.setPriority(priority);
        item.setPinned(pinned);
        return item;
    }

    /**
     * Capturing chat service: records the messages it received and returns a
     * completed assistant message (no tool calls), so the ReAct loop terminates
     * after one LLM call.
     */
    private static final class CapturingChatService implements IChatService {
        final AtomicReference<List<ChatMessage>> captured = new AtomicReference<>();

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            captured.set(new ArrayList<>(request.getMessages()));
            return CompletableFuture.completedFuture(doneResponse());
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            captured.set(new ArrayList<>(request.getMessages()));
            return doneResponse();
        }

        private ChatResponse doneResponse() {
            ChatAssistantMessage msg = new ChatAssistantMessage();
            msg.setContent("done");
            return ChatResponse.success(msg);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    private static IToolManager noToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(call.getId(), ""));
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
                AiToolModel m = new AiToolModel();
                m.setName(toolName);
                return m;
            }
        };
    }

    /**
     * Run a single-turn execution and return the first message the LLM
     * received (the system message, if any). The engine is built with the
     * given provider and an explicit budget.
     */
    private ChatMessage runAndCaptureFirstMessage(InMemoryMemoryStoreProvider provider, int budget) {
        CapturingChatService chat = new CapturingChatService();
        DefaultAgentEngine engine = new DefaultAgentEngine(chat, noToolManager());
        engine.setMemoryStoreProvider(provider);
        engine.setMemoryInjectionBudgetTokens(budget);

        // Use sessionId "s1" so the pre-populated store is the one injected.
        AgentMessageRequest request = new AgentMessageRequest(
                "test-agent", "hello", "s1", Collections.emptyMap());
        engine.execute(request).join();

        List<ChatMessage> msgs = chat.captured.get();
        assertNotNull(msgs, "Chat service should have captured the request messages");
        assertFalse(msgs.isEmpty(), "Captured messages should not be empty");
        return msgs.get(0);
    }

    // ------------------------------------------------------------------
    // formatMemorySection unit tests (pure)
    // ------------------------------------------------------------------

    @Test
    void formatMemorySectionIncludesHeaderAndContent() {
        List<AiMemoryItem> items = List.of(
                memoryItem("k1", "note", "first memory", 5, false),
                memoryItem("k2", null, "second memory", 1, false)
        );
        String section = DefaultAgentEngine.formatMemorySection(items);
        assertTrue(section.contains("## Working Memory"), "Section must have recognizable header");
        assertTrue(section.contains("first memory"), "Section must contain item content");
        assertTrue(section.contains("second memory"), "Section must contain item content");
        assertTrue(section.contains("[k1]"), "Section must contain item key");
        assertTrue(section.contains("[note]"), "Section must contain item type");
    }

    @Test
    void formatMemorySectionNullContentRendersEmpty() {
        AiMemoryItem item = memoryItem("k1", "note", null, 1, false);
        String section = DefaultAgentEngine.formatMemorySection(List.of(item));
        assertTrue(section.contains("[k1]"));
        assertTrue(section.contains("- [note] [k1]"));
    }

    // ------------------------------------------------------------------
    // Injection correctness (base prompt + memory section in one system msg)
    // ------------------------------------------------------------------

    @Test
    void injectedMemoryAppendedAfterBasePrompt() {
        InMemoryMemoryStoreProvider provider = new InMemoryMemoryStoreProvider();
        IAiMemoryStore store = provider.getOrCreate("s1");
        store.add(memoryItem("pref", "note", "User prefers concise answers", 5, true));

        ChatMessage first = runAndCaptureFirstMessage(provider, 1024);

        assertTrue(first instanceof ChatSystemMessage,
                "First message must be a ChatSystemMessage when base prompt exists");
        String text = first.getContent();
        assertTrue(text.startsWith(BASE_PROMPT),
                "Base prompt must remain the prefix (prefix-cache friendly)");
        assertTrue(text.contains("## Working Memory"),
                "Memory section must be appended after base prompt");
        assertTrue(text.contains("User prefers concise answers"),
                "Injected memory content must be present");
        int baseIdx = text.indexOf(BASE_PROMPT);
        int memIdx = text.indexOf("## Working Memory");
        assertTrue(baseIdx < memIdx, "Base prompt must precede the memory section");
    }

    @Test
    void injectedMemoryContentPresent() {
        InMemoryMemoryStoreProvider provider = new InMemoryMemoryStoreProvider();
        IAiMemoryStore store = provider.getOrCreate("s1");
        store.add(memoryItem("fact", "note", "The sky is blue", 3, false));
        store.add(memoryItem("tip", "note", "Be concise", 5, false));

        ChatMessage first = runAndCaptureFirstMessage(provider, 1024);
        String text = first.getContent();
        assertTrue(text.contains("The sky is blue"));
        assertTrue(text.contains("Be concise"));
    }

    // ------------------------------------------------------------------
    // Budget truncation: injected set == readBudgeted(budget)
    // ------------------------------------------------------------------

    @Test
    void budgetTruncationMatchesReadBudgeted() {
        InMemoryMemoryStoreProvider provider = new InMemoryMemoryStoreProvider();
        IAiMemoryStore store = provider.getOrCreate("s1");
        // Each item: 8 chars = 2 tokens (chars/4). budget=4 → pinned + high, low excluded.
        store.add(memoryItem("low", "note", "low12345", 1, false));
        store.add(memoryItem("high", "note", "high1234", 5, false));
        store.add(memoryItem("pin", "note", "pinned12", 0, true));

        ChatMessage first = runAndCaptureFirstMessage(provider, 4);
        String text = first.getContent();

        assertTrue(text.contains("pinned12"), "Pinned item must be in injected section");
        assertTrue(text.contains("high1234"), "Higher-priority item must be in injected section");
        assertFalse(text.contains("low12345"),
                "Lower-priority item exceeding budget must be truncated out");

        // Cross-check: every item in readBudgeted(budget) is present in the text.
        List<AiMemoryItem> direct = store.readBudgeted(4, new HashMap<>());
        for (AiMemoryItem i : direct) {
            assertTrue(text.contains(i.getContent()),
                    "Injected set must equal readBudgeted(budget)");
        }
    }

    // ------------------------------------------------------------------
    // Pinned priority: pinned always present even near budget
    // ------------------------------------------------------------------

    @Test
    void pinnedItemAlwaysInjectedNearBudget() {
        InMemoryMemoryStoreProvider provider = new InMemoryMemoryStoreProvider();
        IAiMemoryStore store = provider.getOrCreate("s1");
        store.add(memoryItem("pin", "note", "pinned-content-here", 0, true));
        store.add(memoryItem("big", "note", "other-content-here", 9, false));

        // budget=2: readBudgeted includes pinned first (uses 2 tokens), then the
        // non-pinned item (would exceed) is skipped. Pinned survives.
        ChatMessage first = runAndCaptureFirstMessage(provider, 2);
        assertTrue(first.getContent().contains("pinned-content-here"),
                "Pinned item must be present even when budget is tight");
    }

    // ------------------------------------------------------------------
    // Empty memory not injected (backward compatibility)
    // ------------------------------------------------------------------

    @Test
    void emptyStoreDoesNotInject() {
        InMemoryMemoryStoreProvider provider = new InMemoryMemoryStoreProvider();
        provider.getOrCreate("s1"); // exists but empty

        ChatMessage first = runAndCaptureFirstMessage(provider, 1024);
        assertTrue(first instanceof ChatSystemMessage);
        String text = first.getContent();
        assertEquals(BASE_PROMPT, text,
                "Empty store must not add a memory section — system prompt equals base prompt");
        assertFalse(text.contains("## Working Memory"),
                "No memory section header when store is empty");
    }

    // ------------------------------------------------------------------
    // Budget <= 0 disables injection (explicit opt-out)
    // ------------------------------------------------------------------

    @Test
    void zeroBudgetDisablesInjection() {
        InMemoryMemoryStoreProvider provider = new InMemoryMemoryStoreProvider();
        IAiMemoryStore store = provider.getOrCreate("s1");
        store.add(memoryItem("k", "note", "should not appear", 5, true));

        ChatMessage first = runAndCaptureFirstMessage(provider, 0);
        String text = first.getContent();
        assertEquals(BASE_PROMPT, text, "budget=0 must disable injection");
        assertFalse(text.contains("should not appear"));
    }

    @Test
    void negativeBudgetDisablesInjection() {
        InMemoryMemoryStoreProvider provider = new InMemoryMemoryStoreProvider();
        IAiMemoryStore store = provider.getOrCreate("s1");
        store.add(memoryItem("k", "note", "should not appear", 5, true));

        ChatMessage first = runAndCaptureFirstMessage(provider, -1);
        String text = first.getContent();
        assertEquals(BASE_PROMPT, text, "negative budget must disable injection");
        assertFalse(text.contains("should not appear"));
    }

    // ------------------------------------------------------------------
    // Null provider skipped (no exception)
    // ------------------------------------------------------------------

    @Test
    void nullProviderSkipsInjectionWithoutException() {
        CapturingChatService chat = new CapturingChatService();
        DefaultAgentEngine engine = new DefaultAgentEngine(chat, noToolManager());
        engine.setMemoryStoreProvider(null);

        AgentMessageRequest request = new AgentMessageRequest(
                "test-agent", "hello", "s1", Collections.emptyMap());
        engine.execute(request).join();

        List<ChatMessage> msgs = chat.captured.get();
        assertFalse(msgs.isEmpty());
        ChatMessage first = msgs.get(0);
        assertTrue(first instanceof ChatSystemMessage);
        assertEquals(BASE_PROMPT, first.getContent(),
                "Null provider must skip injection — system prompt equals base prompt");
    }

    // ------------------------------------------------------------------
    // Budget configurable: different budgets → different item counts
    // ------------------------------------------------------------------

    @Test
    void budgetConfigurableAffectsItemCount() {
        InMemoryMemoryStoreProvider providerA = new InMemoryMemoryStoreProvider();
        InMemoryMemoryStoreProvider providerB = new InMemoryMemoryStoreProvider();
        for (InMemoryMemoryStoreProvider p : List.of(providerA, providerB)) {
            IAiMemoryStore s = p.getOrCreate("s1");
            s.add(memoryItem("pin", "note", "pinned12", 0, true));
            s.add(memoryItem("h", "note", "high1234", 5, false));
            s.add(memoryItem("l", "note", "low12345", 1, false));
        }

        ChatMessage smallFirst = runAndCaptureFirstMessage(providerA, 2);
        String smallText = smallFirst.getContent();

        ChatMessage largeFirst = runAndCaptureFirstMessage(providerB, 1024);
        String largeText = largeFirst.getContent();

        assertTrue(smallText.contains("pinned12"));
        assertFalse(smallText.contains("high1234"), "Small budget should exclude high item");
        assertFalse(smallText.contains("low12345"), "Small budget should exclude low item");

        assertTrue(largeText.contains("pinned12"));
        assertTrue(largeText.contains("high1234"));
        assertTrue(largeText.contains("low12345"));
    }

    // ------------------------------------------------------------------
    // Shipped defaults: budget > 0, provider non-null
    // ------------------------------------------------------------------

    @Test
    void shippedDefaultsAreSensible() {
        DefaultAgentEngine engine = new DefaultAgentEngine(new CapturingChatService(), noToolManager());
        assertNotNull(engine.getMemoryStoreProvider(), "Shipped provider must be non-null");
        assertTrue(engine.getMemoryInjectionBudgetTokens() > 0,
                "Shipped budget must be > 0 so memory injection is enabled by default");
    }
}
