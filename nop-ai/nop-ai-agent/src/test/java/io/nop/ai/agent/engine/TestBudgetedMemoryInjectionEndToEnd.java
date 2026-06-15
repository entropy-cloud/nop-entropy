package io.nop.ai.agent.engine;

import io.nop.ai.agent.memory.AiMemoryItem;
import io.nop.ai.agent.memory.IAiMemoryStore;
import io.nop.ai.agent.memory.InMemoryMemoryStoreProvider;
import io.nop.ai.agent.memory.InMemoryAiMemoryStore;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.IDenialLedger;
import io.nop.ai.agent.security.DenialRecord;
import io.nop.ai.agent.security.DenialRecordOutcome;
import io.nop.ai.agent.security.DenialLayerSource;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.tool.ReadMemoryExecutor;
import io.nop.ai.agent.tool.SearchMemoryExecutor;
import io.nop.ai.agent.tool.WriteMemoryExecutor;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatUserMessage;
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
 * Plan 192 Phase 2 end-to-end + wiring verification tests for budgeted
 * working-memory auto-injection into the system prompt.
 *
 * <p><b>End-to-end (Anti-Hollow Rule #22)</b>: an Agent's ReAct loop first
 * calls {@code write-memory} (writes one item) in turn 1. In turn 2, the
 * system prompt the LLM receives <b>automatically contains</b> the written
 * memory content — without the Agent calling {@code read-memory}. This proves
 * the full path write-memory → store → readBudgeted → buildBaseExecutionContext
 * → ChatSystemMessage is wired (NOT hollow).
 *
 * <p><b>Resume path</b>: a paused session with pre-populated memory, when
 * resumed, produces a system prompt containing the budgeted memory — proving
 * injection works on the {@code resumeSession} path (same
 * {@code buildBaseExecutionContext}).
 *
 * <p><b>Backward compatibility</b>: a session that never writes memory has a
 * system prompt equal to the base prompt (no memory section, no extra system
 * message) — existing sessions are unaffected.
 */
public class TestBudgetedMemoryInjectionEndToEnd {

    private static final String BASE_PROMPT = "You are a helpful assistant.";

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static AiMemoryItem memItem(String key, String type, String content,
                                        int priority, boolean pinned) {
        AiMemoryItem item = new AiMemoryItem();
        item.setKey(key);
        item.setType(type);
        item.setContent(content);
        item.setPriority(priority);
        item.setPinned(pinned);
        return item;
    }

    private static IToolManager toolManagerWithMemoryTools() {
        ReadMemoryExecutor readMemory = new ReadMemoryExecutor();
        WriteMemoryExecutor writeMemory = new WriteMemoryExecutor();
        SearchMemoryExecutor searchMemory = new SearchMemoryExecutor();

        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                if ("read-memory".equals(toolName)) {
                    return readMemory.executeAsync(call, context).toCompletableFuture();
                }
                if ("write-memory".equals(toolName)) {
                    return writeMemory.executeAsync(call, context).toCompletableFuture();
                }
                if ("search-memory".equals(toolName)) {
                    return searchMemory.executeAsync(call, context).toCompletableFuture();
                }
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
                m.setDescription("Mock: " + toolName);
                return m;
            }
        };
    }

    private static ChatToolCall toolCall(String id, String name, Map<String, Object> args) {
        ChatToolCall tc = new ChatToolCall();
        tc.setId(id);
        tc.setName(name);
        tc.setArguments(args);
        return tc;
    }

    private static ChatResponse assistantWithToolCall(String id, String toolName, Map<String, Object> args) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("");
        msg.setToolCalls(List.of(toolCall(id, toolName, args)));
        return ChatResponse.success(msg);
    }

    private static ChatResponse finalAssistant(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }

    // ========================================================================
    // 1. Write-then-auto-inject (Anti-Hollow)
    // ========================================================================

    /**
     * Turn 1 (first execute): Agent calls write-memory. Turn 2 (second execute,
     * same sessionId): the system prompt automatically contains the written
     * memory — without the Agent calling read-memory. Proves the full
     * write → store → readBudgeted → buildBaseExecutionContext →
     * ChatSystemMessage chain across two execution turns.
     */
    @Test
    void writeMemoryThenAutoInjectedInNextTurn() throws Exception {
        String memoryContent = "User prefers concise answers and concrete examples.";
        String sessionId = "auto-inject-1";

        // Track the system prompt captured on each execute() call.
        AtomicReference<String> firstExecSystemPrompt = new AtomicReference<>();
        AtomicReference<String> secondExecSystemPrompt = new AtomicReference<>();

        // First execute: LLM emits write-memory tool call, then a final message.
        AtomicInteger firstCallCount = new AtomicInteger(0);
        IChatService firstChatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(buildResponse(request));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return buildResponse(request);
            }

            private ChatResponse buildResponse(ChatRequest request) {
                int n = firstCallCount.getAndIncrement();
                if (n == 0) {
                    List<ChatMessage> msgs = request.getMessages();
                    if (!msgs.isEmpty() && msgs.get(0) instanceof ChatSystemMessage) {
                        firstExecSystemPrompt.set(msgs.get(0).getContent());
                    }
                    Map<String, Object> args = new HashMap<>();
                    args.put("action", "add");
                    args.put("key", "user-pref");
                    args.put("type", "note");
                    args.put("priority", 5);
                    args.put("pinned", true);
                    args.put("content", memoryContent);
                    return assistantWithToolCall("e2e-write-1", "write-memory", args);
                }
                return finalAssistant("Written.");
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        InMemoryMemoryStoreProvider provider = new InMemoryMemoryStoreProvider();
        InMemorySessionStore sessionStore = new InMemorySessionStore();

        DefaultAgentEngine engine = new DefaultAgentEngine(
                firstChatService, toolManagerWithMemoryTools(), sessionStore, new AllowAllPermissionProvider());
        engine.setMemoryStoreProvider(provider);

        // Turn 1: write memory.
        AgentMessageRequest request1 = new AgentMessageRequest(
                "test-agent", "remember this", sessionId, Collections.emptyMap());
        engine.execute(request1).get(60, TimeUnit.SECONDS);

        // Verify: turn 1's system prompt does NOT contain the memory (write
        // hasn't happened at system-prompt build time).
        assertNotNull(firstExecSystemPrompt.get(), "Turn 1 must have captured the system prompt");
        assertFalse(firstExecSystemPrompt.get().contains(memoryContent),
                "Turn 1 system prompt must NOT contain memory (write hasn't happened yet)");

        // Verify the memory was actually written.
        IAiMemoryStore store = provider.getOrCreate(sessionId);
        assertFalse(store instanceof InMemoryAiMemoryStore && ((InMemoryAiMemoryStore) store).size() == 0,
                "Memory must have been written by write-memory tool");

        // Turn 2: second execute on the SAME sessionId with a fresh chat service.
        AtomicInteger secondCallCount = new AtomicInteger(0);
        IChatService secondChatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(buildResponse(request));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return buildResponse(request);
            }

            private ChatResponse buildResponse(ChatRequest request) {
                if (secondCallCount.getAndIncrement() == 0) {
                    List<ChatMessage> msgs = request.getMessages();
                    if (!msgs.isEmpty() && msgs.get(0) instanceof ChatSystemMessage) {
                        secondExecSystemPrompt.set(msgs.get(0).getContent());
                    }
                }
                return finalAssistant("Acknowledged user preference.");
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        // Rebuild engine with the second chat service but SAME sessionStore + provider.
        DefaultAgentEngine engine2 = new DefaultAgentEngine(
                secondChatService, toolManagerWithMemoryTools(), sessionStore, new AllowAllPermissionProvider());
        engine2.setMemoryStoreProvider(provider);

        AgentMessageRequest request2 = new AgentMessageRequest(
                "test-agent", "next turn", sessionId, Collections.emptyMap());
        engine2.execute(request2).get(60, TimeUnit.SECONDS);

        // Verify: turn 2's system prompt DOES contain the memory automatically.
        assertNotNull(secondExecSystemPrompt.get(), "Turn 2 must have captured the system prompt");
        assertTrue(secondExecSystemPrompt.get().contains(memoryContent),
                "Turn 2 system prompt MUST contain the written memory (auto-injection). Got: "
                        + secondExecSystemPrompt.get());
        assertTrue(secondExecSystemPrompt.get().contains("## Working Memory"),
                "Turn 2 system prompt must contain the memory section header");
        assertTrue(secondExecSystemPrompt.get().startsWith(BASE_PROMPT),
                "Base prompt must remain the prefix");
    }

    // ========================================================================
    // 2. Resume path injection
    // ========================================================================

    /**
     * A paused session with pre-populated memory, when resumed, produces a
     * system prompt containing the budgeted memory. Proves injection works on
     * the resumeSession path (shared buildBaseExecutionContext).
     */
    @Test
    void resumeSessionInjectsBudgetedMemory() throws Exception {
        String sessionId = "resume-inject-1";
        InMemorySessionStore store = new InMemorySessionStore();

        // Build a paused session with some history.
        AgentSession session = store.getOrCreate(sessionId, "test-agent");
        session.appendMessages(List.of(new ChatUserMessage("earlier message")));
        session.setStatus(AgentExecStatus.paused);

        // Pre-populate the memory store for this session.
        InMemoryMemoryStoreProvider provider = new InMemoryMemoryStoreProvider();
        IAiMemoryStore memStore = provider.getOrCreate(sessionId);
        memStore.add(memItem("resume-fact", "note", "Important resume context", 5, true));

        // Capturing chat service (terminal response → single iteration).
        AtomicReference<String> resumeSystemPrompt = new AtomicReference<>();
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(call(request, cancelToken));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                List<ChatMessage> msgs = request.getMessages();
                if (!msgs.isEmpty() && msgs.get(0) instanceof ChatSystemMessage) {
                    resumeSystemPrompt.set(msgs.get(0).getContent());
                }
                return finalAssistant("resumed done");
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatService, toolManagerWithMemoryTools(), store, new AllowAllPermissionProvider());
        engine.setMemoryStoreProvider(provider);
        engine.setDenialLedger(new ResetOnlyDenialLedger());

        AgentExecutionResult result = engine.resumeSession(sessionId, "operator", "test")
                .toCompletableFuture().get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Resumed session must complete. Messages: " + result.getMessages());

        assertNotNull(resumeSystemPrompt.get(), "Resume must have captured the system prompt");
        assertTrue(resumeSystemPrompt.get().contains("Important resume context"),
                "Resume system prompt MUST contain the budgeted memory. Got: "
                        + resumeSystemPrompt.get());
        assertTrue(resumeSystemPrompt.get().contains("## Working Memory"),
                "Resume system prompt must contain the memory section header");
    }

    // ========================================================================
    // 3. Backward compatibility — no memory → behavior unchanged
    // ========================================================================

    /**
     * A session that never writes memory has a system prompt equal to the base
     * prompt — no memory section, no extra system message. Existing sessions
     * are unaffected.
     */
    @Test
    void noMemorySessionUnchangedBehavior() throws Exception {
        AtomicReference<String> systemPrompt = new AtomicReference<>();
        AtomicInteger callCount = new AtomicInteger(0);

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(call(request, cancelToken));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                if (callCount.getAndIncrement() == 0) {
                    List<ChatMessage> msgs = request.getMessages();
                    if (!msgs.isEmpty() && msgs.get(0) instanceof ChatSystemMessage) {
                        systemPrompt.set(msgs.get(0).getContent());
                    }
                }
                return finalAssistant("ok");
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        // Default provider (empty store for this session).
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManagerWithMemoryTools());

        AgentMessageRequest request = new AgentMessageRequest(
                "test-agent", "hello", "no-memory-1", Collections.emptyMap());
        engine.execute(request).get(60, TimeUnit.SECONDS);

        assertNotNull(systemPrompt.get());
        assertEquals(BASE_PROMPT, systemPrompt.get(),
                "Session with no memory must have system prompt = base prompt (backward compatible)");
        assertFalse(systemPrompt.get().contains("## Working Memory"),
                "No memory section when store is empty");
    }

    // ========================================================================
    // 4. Multi-item budget truncation end-to-end
    // ========================================================================

    /**
     * A session with multiple memory items (pinned + high/low priority) gets
     * a system prompt containing only the budget-eligible items (pinned + high
     * priority, low priority truncated). The injected content matches
     * readBudgeted(budget).
     */
    @Test
    void multiItemBudgetTruncationEndToEnd() throws Exception {
        String sessionId = "multi-item-1";

        InMemoryMemoryStoreProvider provider = new InMemoryMemoryStoreProvider();
        IAiMemoryStore store = provider.getOrCreate(sessionId);
        store.add(memItem("pin", "note", "pinned-item", 0, true));
        store.add(memItem("high", "note", "high-priority-item", 9, false));
        store.add(memItem("low", "note", "low-priority-item", 1, false));

        AtomicReference<String> systemPrompt = new AtomicReference<>();
        AtomicInteger callCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(call(request, cancelToken));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                if (callCount.getAndIncrement() == 0) {
                    List<ChatMessage> msgs = request.getMessages();
                    if (!msgs.isEmpty() && msgs.get(0) instanceof ChatSystemMessage) {
                        systemPrompt.set(msgs.get(0).getContent());
                    }
                }
                return finalAssistant("ok");
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManagerWithMemoryTools());
        engine.setMemoryStoreProvider(provider);
        // budget = 8 tokens. Each item is ~3 tokens (chars/4). pinned(3) + high(4) = 7 ≤ 8.
        // low would push to 7+4=11 > 8 → excluded.
        engine.setMemoryInjectionBudgetTokens(8);

        AgentMessageRequest request = new AgentMessageRequest(
                "test-agent", "hello", sessionId, Collections.emptyMap());
        engine.execute(request).get(60, TimeUnit.SECONDS);

        assertNotNull(systemPrompt.get());
        String text = systemPrompt.get();
        assertTrue(text.contains("pinned-item"), "Pinned item must be injected");
        assertTrue(text.contains("high-priority-item"), "High-priority item must be injected");
        assertFalse(text.contains("low-priority-item"), "Low-priority item must be truncated out");

        // Cross-check with readBudgeted directly.
        List<AiMemoryItem> direct = store.readBudgeted(8, new HashMap<>());
        for (AiMemoryItem i : direct) {
            assertTrue(text.contains(i.getContent()));
        }
    }

    // ========================================================================
    // Helper: a minimal denial ledger that supports reset (for resume tests)
    // ========================================================================

    private static final class ResetOnlyDenialLedger implements IDenialLedger {
        @Override
        public DenialRecordOutcome recordDenial(DenialRecord record) {
            return DenialRecordOutcome.of(0, false);
        }

        @Override
        public boolean isPaused(String sessionId) {
            return false;
        }

        @Override
        public int getDenialCount(String sessionId) {
            return 0;
        }

        @Override
        public void reset(String sessionId) {
            // no-op: test only needs reset to be callable without error.
        }
    }
}
