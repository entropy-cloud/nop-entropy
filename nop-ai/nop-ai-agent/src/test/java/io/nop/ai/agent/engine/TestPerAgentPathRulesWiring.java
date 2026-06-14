package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.model.PathRuleModel;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.AllowAllToolAccessChecker;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.IPathAccessChecker;
import io.nop.ai.agent.security.IPermissionProvider;
import io.nop.ai.agent.security.IToolAccessChecker;
import io.nop.ai.agent.security.RuleBasedPathAccessChecker;
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
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 integration tests verifying per-agent glob path-rule enforcement in
 * the ReAct loop. These tests prove the wiring from agent model → engine
 * per-agent checker resolution → executor → {@code checkPathAccess} →
 * {@link RuleBasedPathAccessChecker} → deny/allow.
 */
public class TestPerAgentPathRulesWiring {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private PathRuleModel rule(String pattern, String access) {
        PathRuleModel r = new PathRuleModel();
        r.setPattern(pattern);
        r.setAccess(access);
        return r;
    }

    private IChatService mockChatService(ChatToolCall toolCall) {
        AtomicInteger callCount = new AtomicInteger(0);
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = callCount.getAndIncrement();
                if (n == 0) {
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("");
                    msg.setToolCalls(List.of(toolCall));
                    return CompletableFuture.completedFuture(ChatResponse.success(msg));
                }
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Done.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };
    }

    private IToolManager mockToolManager(AtomicBoolean toolCalled, String output) {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String name, AiToolCall call, IToolExecuteContext context) {
                toolCalled.set(true);
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, output));
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
                AiToolModel m = new AiToolModel();
                m.setName(toolName);
                return m;
            }
        };
    }

    private IChatService dummyChatService() {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("ok");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };
    }

    private IToolManager dummyToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String n, AiToolCall c, IToolExecuteContext ctx) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, ""));
            }

            @Override
            public CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                    io.nop.ai.toolkit.model.AiToolCalls c, IToolExecuteContext ctx) {
                return null;
            }

            @Override
            public List<AiToolModel> listTools() {
                return Collections.emptyList();
            }

            @Override
            public AiToolModel loadTool(String n) {
                return null;
            }
        };
    }

    // ---- resolvePerAgentPathChecker ----

    @Test
    void resolvePerAgentPathCheckerReturnsRuleBasedWhenRulesDeclared() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                dummyChatService(), dummyToolManager(),
                new io.nop.ai.agent.session.InMemorySessionStore());

        AgentModel model = new AgentModel();
        model.setPathRules(List.of(rule("/**/secrets/**", "deny")));

        IPathAccessChecker perAgent = engine.resolvePerAgentPathChecker(model);
        assertTrue(perAgent instanceof RuleBasedPathAccessChecker,
                "Agent with path-rules must get a RuleBasedPathAccessChecker");
        assertEquals(1, ((RuleBasedPathAccessChecker) perAgent).getRules().size());
    }

    @Test
    void resolvePerAgentPathCheckerReturnsGlobalWhenNoRulesDeclared() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                dummyChatService(), dummyToolManager(),
                new io.nop.ai.agent.session.InMemorySessionStore());

        AgentModel model = new AgentModel();
        IPathAccessChecker perAgent = engine.resolvePerAgentPathChecker(model);
        assertSame(engine.getPathAccessCheckerForTest(), perAgent,
                "Agent without path-rules must get the global checker unchanged (backward compatible)");
    }

    // ---- End-to-end: per-agent rules enforced in ReAct loop ----

    @Test
    void perAgentDenyRuleBlocksPathInReActLoop() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("read_file"));
        model.setPathRules(List.of(rule("/**/secrets/**", "deny")));

        IPermissionProvider allowAll = new AllowAllPermissionProvider();
        IToolAccessChecker toolChecker = new AllowAllToolAccessChecker();

        // Per-agent base: rule-based checker wrapping the global deny-list
        IPathAccessChecker perAgentChecker = new RuleBasedPathAccessChecker(
                model.getPathRules(), new DefaultPathAccessChecker());

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("read_file");
        toolCall.setArguments(Map.of("path", "/workspace/secrets/key.txt"));

        AtomicBoolean toolCalled = new AtomicBoolean(false);
        IToolManager toolManager = mockToolManager(toolCalled, "should not be called");
        IChatService chatService = mockChatService(toolCall);

        List<AgentEvent> events = new ArrayList<>();
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        publisher.addSubscriber(events::add);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(toolManager)
                .eventPublisher(publisher)
                .permissionProvider(allowAll).toolAccessChecker(toolChecker)
                .pathAccessChecker(perAgentChecker).build();

        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertFalse(toolCalled.get(),
                "Tool should NOT be called — DENY rule /**/secrets/** matches /workspace/secrets/key.txt");

        // PATH_ACCESS_DENIED event must be published with the rule-based matchedRule token
        AgentEvent deniedEvent = events.stream()
                .filter(e -> e.getEventType() == AgentEventType.PATH_ACCESS_DENIED)
                .findFirst().orElse(null);
        assertNotNull(deniedEvent, "PATH_ACCESS_DENIED event should be published");
        String reason = (String) deniedEvent.getPayload().get("reason");
        assertNotNull(reason);
        assertTrue(reason.contains("agent path-rule"),
                "Denial reason must identify agent path-rule. Got: " + reason);
    }

    @Test
    void perAgentAllowRuleLetsPathThroughInReActLoop() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("read_file"));
        // ALLOW /workspace/** → delegates to global deny-list which allows it
        model.setPathRules(List.of(rule("/workspace/**", "allow")));

        IPermissionProvider allowAll = new AllowAllPermissionProvider();
        IToolAccessChecker toolChecker = new AllowAllToolAccessChecker();
        IPathAccessChecker perAgentChecker = new RuleBasedPathAccessChecker(
                model.getPathRules(), new DefaultPathAccessChecker());

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("read_file");
        toolCall.setArguments(Map.of("path", "/workspace/src/Main.java"));

        AtomicBoolean toolCalled = new AtomicBoolean(false);
        IToolManager toolManager = mockToolManager(toolCalled, "file content");
        IChatService chatService = mockChatService(toolCall);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(toolManager)
                .permissionProvider(allowAll).toolAccessChecker(toolChecker)
                .pathAccessChecker(perAgentChecker).build();

        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(toolCalled.get(),
                "Tool should be called — ALLOW rule delegates to global deny-list which allows /workspace/src/Main.java");
    }

    @Test
    void perAgentNoRuleMatchLetsPathThroughInReActLoop() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("read_file"));
        model.setPathRules(List.of(rule("/workspace/secrets/**", "deny")));

        IPermissionProvider allowAll = new AllowAllPermissionProvider();
        IToolAccessChecker toolChecker = new AllowAllToolAccessChecker();
        IPathAccessChecker perAgentChecker = new RuleBasedPathAccessChecker(
                model.getPathRules(), new DefaultPathAccessChecker());

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("read_file");
        // Path matches no rule → delegate to global deny-list → allowed
        toolCall.setArguments(Map.of("path", "/tmp/workdir/output.txt"));

        AtomicBoolean toolCalled = new AtomicBoolean(false);
        IToolManager toolManager = mockToolManager(toolCalled, "file content");
        IChatService chatService = mockChatService(toolCall);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(toolManager)
                .permissionProvider(allowAll).toolAccessChecker(toolChecker)
                .pathAccessChecker(perAgentChecker).build();

        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(toolCalled.get(),
                "Tool should be called — no rule matched, delegate to global deny-list which allows /tmp/...");
    }

    // ---- Backward compatibility: no path-rules → unchanged ----

    @Test
    void agentWithoutPathRulesBehaviorUnchanged() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("read_file"));
        // No path-rules declared

        IPermissionProvider allowAll = new AllowAllPermissionProvider();
        IToolAccessChecker toolChecker = new AllowAllToolAccessChecker();
        // Engine resolves to global checker when no path-rules
        IPathAccessChecker globalChecker = new DefaultPathAccessChecker();

        // Sensitive path → denied by global deny-list (same as before)
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("read_file");
        toolCall.setArguments(Map.of("path", "/etc/passwd"));

        AtomicBoolean toolCalled = new AtomicBoolean(false);
        IToolManager toolManager = mockToolManager(toolCalled, "");
        IChatService chatService = mockChatService(toolCall);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(toolManager)
                .permissionProvider(allowAll).toolAccessChecker(toolChecker)
                .pathAccessChecker(globalChecker).build();

        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertFalse(toolCalled.get(),
                "Global deny-list must still block /etc/passwd (no path-rules = backward compatible)");

        ChatToolResponseMessage toolResp = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .findFirst().orElse(null);
        assertNotNull(toolResp);
        assertTrue(toolResp.getContent().contains("Path access denied"),
                "Must be denied by global deny-list. Got: " + toolResp.getContent());
    }

    // ---- Per-agent checker composes with parent-constraint wrapper ----

    @Test
    void perAgentCheckerComposesWithParentConstraint() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                dummyChatService(), dummyToolManager(),
                new io.nop.ai.agent.session.InMemorySessionStore());

        // Per-agent base: rule-based checker
        AgentModel model = new AgentModel();
        model.setPathRules(List.of(rule("/workspace/**", "deny")));
        IPathAccessChecker perAgentBase = engine.resolvePerAgentPathChecker(model);
        assertTrue(perAgentBase instanceof RuleBasedPathAccessChecker);

        // Parent constraint with path roots
        io.nop.ai.agent.security.ParentPermissionConstraint constraint =
                new io.nop.ai.agent.security.ParentPermissionConstraint(
                        Set.of("read-file"), Set.of("/workspace"),
                        "parent-agent", "parent-sess");
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put(io.nop.ai.agent.security.ParentPermissionConstraint.METADATA_KEY, constraint);

        AgentMessageRequest request = new AgentMessageRequest("sub-agent", "input", null, metadata);
        IPathAccessChecker effective = engine.resolveEffectivePathAccessChecker(request, perAgentBase);

        // Composition: parent constraint wraps per-agent base
        assertTrue(effective instanceof io.nop.ai.agent.security.ParentConstrainedPathAccessChecker,
                "Parent constraint must wrap the per-agent base checker");
        io.nop.ai.agent.security.ParentConstrainedPathAccessChecker wrapper =
                (io.nop.ai.agent.security.ParentConstrainedPathAccessChecker) effective;
        assertSame(perAgentBase, wrapper.getDelegate(),
                "ParentConstrainedPathAccessChecker must delegate to the per-agent (rule-based) base, not the global");
    }
}
