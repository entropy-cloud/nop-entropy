package io.nop.ai.agent.skill;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.talent.ITalent;
import io.nop.ai.agent.talent.NoOpTalent;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test proving the Skill engine is wired into the ReAct loop:
 * skill declarations flow from the agent model → {@link SkillResolver} →
 * {@link SkillAssemblyResult} merge → LLM request (instructions + tools).
 *
 * <p>Verifies:
 * <ol>
 *   <li>Default ({@link NoOpSkillProvider} / no skills): ReAct loop runs
 *       unchanged — backward compatible.</li>
 *   <li>{@code availableSkills} referencing fixtures: skill goals reach the
 *       LLM system context, skill tool dependencies appear in tool defs, and
 *       are invocable through the normal tool-execution path.</li>
 *   <li>{@code requiredSkills} referencing a missing skill: engine fails fast
 *       with {@link NopAiAgentException} before any LLM call.</li>
 *   <li>Both talents and skills registered: both contribute additively
 *       (instructions + tools merged, no conflict).</li>
 * </ol>
 */
public class TestSkillEngineInReActLoop {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private abstract static class StubToolManager implements IToolManager {
        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
            return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "result"));
        }

        @Override
        public CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                io.nop.ai.toolkit.model.AiToolCalls calls, IToolExecuteContext context) {
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
    }

    private static IChatService plainChatService() {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Done.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    private static boolean optionsContainTool(ChatOptions options, String toolName) {
        if (options == null || options.getTools() == null) {
            return false;
        }
        for (ChatToolDefinition def : options.getTools()) {
            if (toolName.equals(def.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean messagesContainSystemInstruction(List<ChatMessage> messages, String fragment) {
        if (messages == null) {
            return false;
        }
        for (ChatMessage msg : messages) {
            if (msg instanceof ChatSystemMessage && msg.getContent() != null && msg.getContent().contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static ISkillProvider inlineProvider(SkillModel... skills) {
        List<SkillModel> list = Arrays.asList(skills);
        return () -> list;
    }

    private static SkillModel skill(String name, String goal, String... tools) {
        SkillModel s = new SkillModel();
        s.setName(name);
        s.setGoal(goal);
        s.setDependencies(Arrays.asList(tools));
        return s;
    }

    private static Set<String> set(String... items) {
        return new LinkedHashSet<>(Arrays.asList(items));
    }

    // ---- Test 1: Default NoOpSkillProvider changes nothing (backward compat) ----

    @Test
    void defaultNoSkillProviderRunsUnchanged() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        AtomicReference<List<ChatMessage>> firstRequestMessages = new AtomicReference<>();
        AtomicReference<ChatOptions> firstRequestOptions = new AtomicReference<>();
        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                if (chatCallCount.getAndIncrement() == 0) {
                    firstRequestMessages.set(new ArrayList<>(request.getMessages()));
                    firstRequestOptions.set(request.getOptions());
                }
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Hello.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {
                })
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        // No skill provider registered → no skill instructions, no skill tools.
        assertFalse(messagesContainSystemInstruction(firstRequestMessages.get(), "SKILL"),
                "No skill instructions should be present with NoOpSkillProvider default");
        // No tools declared → options has no tools (or empty).
        ChatOptions opts = firstRequestOptions.get();
        assertTrue(opts == null || opts.getTools() == null || opts.getTools().isEmpty(),
                "No tools should be present with empty agent tools and NoOpSkillProvider");
    }

    // ---- Test 2: availableSkills inject instruction + tools ----

    @Test
    void availableSkillsInjectInstructionAndTools() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        model.setAvailableSkills(set("log-analysis"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ISkillProvider provider = inlineProvider(
                skill("log-analysis", "Analyze log files for errors and patterns", "read_file", "grep"));

        AtomicReference<List<ChatMessage>> firstRequestMessages = new AtomicReference<>();
        AtomicReference<ChatOptions> firstRequestOptions = new AtomicReference<>();
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                firstRequestMessages.set(new ArrayList<>(request.getMessages()));
                firstRequestOptions.set(request.getOptions());
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Hello.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {
                })
                .skillProvider(provider)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertTrue(messagesContainSystemInstruction(firstRequestMessages.get(), "Analyze log files"),
                "Skill goal must reach the LLM system context");
        assertTrue(optionsContainTool(firstRequestOptions.get(), "read_file"),
                "Skill tool dependency 'read_file' must appear in tool definitions");
        assertTrue(optionsContainTool(firstRequestOptions.get(), "grep"),
                "Skill tool dependency 'grep' must appear in tool definitions");
    }

    // ---- Test 3: missing requiredSkills fails fast before LLM call ----

    @Test
    void missingRequiredSkillFailsFastBeforeLlmCall() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        model.setRequiredSkills(set("nonexistent-skill"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ISkillProvider provider = inlineProvider(skill("real-skill", "goal"));

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                chatCallCount.incrementAndGet();
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Should not reach here.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {
                })
                .skillProvider(provider)
                .build();

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> executor.execute(ctx).toCompletableFuture().join());
        assertTrue(ex.getMessage().contains("nonexistent-skill"),
                "Error must name the missing required skill. Got: " + ex.getMessage());
        assertEquals(0, chatCallCount.get(),
                "No LLM call must happen when a requiredSkill is missing — fail-fast before first call");
    }

    // ---- Test 4: talent + skill both contribute additively ----

    @Test
    void talentAndSkillContributeAdditively() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        model.setAvailableSkills(set("log-analysis"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ISkillProvider provider = inlineProvider(
                skill("log-analysis", "SKILL-INSTRUCTION-FRAGMENT", "skill_tool"));

        ITalent talent = new ITalent() {
            @Override
            public boolean isSupported(AgentExecutionContext ctx) {
                return true;
            }

            @Override
            public void onAttach(AgentExecutionContext ctx) {
            }

            @Override
            public String getInstruction(AgentExecutionContext ctx) {
                return "TALENT-INSTRUCTION-FRAGMENT";
            }

            @Override
            public List<String> getTools(AgentExecutionContext ctx) {
                return List.of("talent_tool");
            }
        };

        AtomicReference<List<ChatMessage>> firstRequestMessages = new AtomicReference<>();
        AtomicReference<ChatOptions> firstRequestOptions = new AtomicReference<>();
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                firstRequestMessages.set(new ArrayList<>(request.getMessages()));
                firstRequestOptions.set(request.getOptions());
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Hello.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {
                })
                .talents(List.of(talent))
                .skillProvider(provider)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        // Both talent and skill instructions must reach the system context.
        assertTrue(messagesContainSystemInstruction(firstRequestMessages.get(), "SKILL-INSTRUCTION-FRAGMENT"),
                "Skill instruction fragment must reach the LLM system context");
        assertTrue(messagesContainSystemInstruction(firstRequestMessages.get(), "TALENT-INSTRUCTION-FRAGMENT"),
                "Talent instruction fragment must reach the LLM system context");
        // Both talent and skill tools must appear in tool definitions.
        assertTrue(optionsContainTool(firstRequestOptions.get(), "skill_tool"),
                "Skill-provided tool must appear in tool definitions");
        assertTrue(optionsContainTool(firstRequestOptions.get(), "talent_tool"),
                "Talent-provided tool must appear in tool definitions");
    }

    // ---- Test 5: skill-provided tool is invocable through normal path ----

    @Test
    void skillProvidedToolIsInvocableThroughNormalPath() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        model.setAvailableSkills(set("log-analysis"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ISkillProvider provider = inlineProvider(
                skill("log-analysis", "Analyze logs", "skill_tool"));

        ChatToolCall skillToolCall = new ChatToolCall();
        skillToolCall.setId("call_1");
        skillToolCall.setName("skill_tool");
        skillToolCall.setArguments(Map.of("input", "hello"));

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCallCount.getAndIncrement();
                ChatResponse resp;
                if (n == 0) {
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("Using skill tool.");
                    msg.setToolCalls(List.of(skillToolCall));
                    resp = ChatResponse.success(msg);
                } else {
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("Done.");
                    resp = ChatResponse.success(msg);
                }
                return CompletableFuture.completedFuture(resp);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        AtomicInteger toolCallCount = new AtomicInteger(0);
        AtomicReference<String> toolCalledName = new AtomicReference<>();
        IToolManager toolManager = new StubToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                toolCallCount.incrementAndGet();
                toolCalledName.set(toolName);
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "skill-tool-output"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(toolManager)
                .skillProvider(provider)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, toolCallCount.get(),
                "Skill-provided tool must be invoked through the normal tool-execution path");
        assertEquals("skill_tool", toolCalledName.get());
        assertEquals(2, chatCallCount.get(),
                "Two LLM calls: first triggers the tool call, second receives the final answer");
    }

    // ---- Test 6: FileSystemSkillProvider end-to-end with VFS fixtures ----

    @Test
    void fileSystemProviderEndToEndWithFixtures() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        model.setAvailableSkills(set("code-review"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        FileSystemSkillProvider provider = new FileSystemSkillProvider("/skills");

        AtomicReference<List<ChatMessage>> firstRequestMessages = new AtomicReference<>();
        AtomicReference<ChatOptions> firstRequestOptions = new AtomicReference<>();
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                firstRequestMessages.set(new ArrayList<>(request.getMessages()));
                firstRequestOptions.set(request.getOptions());
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Hello.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {
                })
                .skillProvider(provider)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        // The code-review fixture goal must reach the system context.
        assertTrue(messagesContainSystemInstruction(firstRequestMessages.get(), "Review code changes"),
                "FileSystemSkillProvider-loaded skill goal must reach the LLM system context");
        // The code-review fixture declares read_file and git_diff dependencies.
        assertTrue(optionsContainTool(firstRequestOptions.get(), "read_file"),
                "code-review fixture dependency 'read_file' must appear in tool definitions");
        assertTrue(optionsContainTool(firstRequestOptions.get(), "git_diff"),
                "code-review fixture dependency 'git_diff' must appear in tool definitions");
    }

    // ---- Test 7: NoOpTalent + NoOpSkillProvider explicit registration = unchanged ----

    @Test
    void explicitNoOpDefaultsRunUnchanged() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("agent_tool"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        AtomicReference<ChatOptions> firstRequestOptions = new AtomicReference<>();
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                firstRequestOptions.set(request.getOptions());
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Hello.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {
                })
                .talents(List.of(NoOpTalent.noOp()))
                .skillProvider(NoOpSkillProvider.noOp())
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        // Only the agent-declared "agent_tool" should be present.
        assertTrue(optionsContainTool(firstRequestOptions.get(), "agent_tool"));
        assertFalse(optionsContainTool(firstRequestOptions.get(), "should-not-exist"));
    }
}
