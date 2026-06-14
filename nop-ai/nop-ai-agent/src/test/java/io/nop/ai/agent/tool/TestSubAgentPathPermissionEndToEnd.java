package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentEvent;
import io.nop.ai.agent.engine.AgentEventType;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 end-to-end tests for sub-agent path-permission inheritance
 * enforcement (design §4.4: 文件权限 = 父权限 ∩ 子配置). These tests exercise
 * the FULL path through the engine:
 *
 * <p><b>Test 1 (constraint enforcement)</b>: parent ReAct loop (with declared
 * workDir) → call-agent → engine.execute with path-constrained checker →
 * sub-agent ReAct → sub-agent's out-of-scope path denied by parent path
 * permission constraint. Proves the path constraint flows end-to-end from
 * parent's workDir → call-agent metadata → engine path-checker wrapping →
 * sub-agent checkPathAccess → path denial.
 *
 * <p><b>Test 2 (backward compatibility)</b>: single-agent execution (no
 * call-agent, no constraint) behaves identically to before — path checking
 * is subject only to the global deny-list.
 *
 * <p><b>Test 3 (nested delegation clamping)</b>: parent A → call-agent →
 * sub-agent B → call-agent → sub-sub-agent C. The path constraint propagates
 * the clamped root set: C inherits A's scope (clamped), not B's own declared
 * scope widened.
 *
 * <p><b>Test 4 (null workDir = no confinement)</b>: a parent agent whose model
 * declares NO workDir → call-agent → sub-agent. The sub-agent's path checking
 * is subject ONLY to the global deny-list (ABSENT path roots → no confinement).
 */
public class TestSubAgentPathPermissionEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private IToolManager createToolManagerWithCallAgent() {
        CallAgentExecutor callAgentExecutor = new CallAgentExecutor();
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                if ("call-agent".equals(toolName)) {
                    return callAgentExecutor.executeAsync(call, context).toCompletableFuture();
                }
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(call.getId(), "ok"));
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
        };
    }

    private ChatToolCall toolCallWithPath(String id, String name, String path) {
        ChatToolCall tc = new ChatToolCall();
        tc.setId(id);
        tc.setName(name);
        Map<String, Object> args = new HashMap<>();
        args.put("path", path);
        tc.setArguments(args);
        return tc;
    }

    private ChatToolCall callAgentToolCall(String id, String agentId, String input) {
        ChatToolCall tc = new ChatToolCall();
        tc.setId(id);
        tc.setName("call-agent");
        Map<String, Object> args = new HashMap<>();
        args.put("agentId", agentId);
        args.put("input", input);
        tc.setArguments(args);
        return tc;
    }

    private String identifyAgent(ChatRequest request) {
        List<ChatMessage> messages = request.getMessages();
        if (messages != null && !messages.isEmpty() && messages.get(0) instanceof ChatSystemMessage) {
            return ((ChatSystemMessage) messages.get(0)).getContent();
        }
        return "";
    }

    private long countAssistantMessages(ChatRequest request) {
        return request.getMessages().stream()
                .filter(m -> m instanceof ChatAssistantMessage)
                .count();
    }

    /**
     * END-TO-END TEST 1: Full path-permission constraint enforcement.
     *
     * <p>Parent agent P (declared workDir=/workspace/project-a, tools={read-file,
     * call-agent}) → call-agent → sub-agent S (tools={read-file}). S attempts
     * read-file with path=/workspace/project-b/secret (denied by parent path
     * constraint) and read-file with path=/workspace/project-a/src/Main.java
     * (allowed — passes constraint then global deny-list).
     *
     * <p>Proves: parent ReAct → call-agent → engine.execute with path-constrained
     * checker → sub-agent ReAct → checkPathAccess → out-of-scope path denied by
     * inherited constraint. The denial message identifies "parent path permission
     * constraint".
     */
    @Test
    void endToEndPathConstraintEnforcement() throws Exception {
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(build(request));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return build(request);
            }

            private ChatResponse build(ChatRequest request) {
                String agent = identifyAgent(request);
                long assistantTurns = countAssistantMessages(request);
                ChatAssistantMessage msg = new ChatAssistantMessage();

                if (agent.contains("parent path agent P")) {
                    if (assistantTurns == 0) {
                        msg.setContent("");
                        msg.setToolCalls(List.of(callAgentToolCall("p-call-1",
                                "test-sub-agent-path", "read some files")));
                    } else {
                        msg.setContent("Parent done.");
                    }
                } else if (agent.contains("sub path agent S")) {
                    if (assistantTurns == 0) {
                        msg.setContent("");
                        msg.setToolCalls(List.of(
                                toolCallWithPath("s-outside", "read-file",
                                        "/workspace/project-b/secret"),
                                toolCallWithPath("s-inside", "read-file",
                                        "/workspace/project-a/src/Main.java")));
                    } else {
                        msg.setContent("Sub done.");
                    }
                } else {
                    msg.setContent("fallback");
                }
                return ChatResponse.success(msg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        IToolManager toolManager = createToolManagerWithCallAgent();
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        engine.getEventPublisher().addSubscriber(events::add);

        AgentMessageRequest request = new AgentMessageRequest("test-parent-agent-path", "delegate file reads");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Execution should complete. Last error: " + result.getError());

        // The sub-agent's out-of-scope path must be denied by parent path constraint
        boolean outsideDenied = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.PATH_ACCESS_DENIED
                        && e.getPayload().get("path") != null
                        && ((String) e.getPayload().get("path")).contains("/workspace/project-b/secret")
                        && e.getPayload().get("reason") != null
                        && ((String) e.getPayload().get("reason")).contains("parent path permission constraint"));
        assertTrue(outsideDenied,
                "Sub-agent's out-of-scope path must be denied by parent path permission constraint. Events: " + events);

        // The sub-agent's in-scope path must NOT be denied by parent path constraint
        boolean insideDenied = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.PATH_ACCESS_DENIED
                        && e.getPayload().get("path") != null
                        && ((String) e.getPayload().get("path")).contains("/workspace/project-a/src/Main.java"));
        assertFalse(insideDenied,
                "Sub-agent's in-scope path should NOT be denied by parent path constraint. Events: " + events);

        // The in-scope read-file should have been executed (TOOL_CALL_COMPLETED)
        boolean readCompleted = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.TOOL_CALL_COMPLETED
                        && "read-file".equals(e.getPayload().get("toolName"))
                        && "success".equals(e.getPayload().get("status")));
        assertTrue(readCompleted,
                "Sub-agent's in-scope read-file should have completed successfully. Events: " + events);
    }

    /**
     * END-TO-END TEST 2: Backward compatibility. Single-agent execution (no
     * call-agent, no constraint) has identical path-checking behavior to before
     * — the global deny-list applies, no spurious parent-constraint denials.
     */
    @Test
    void backwardCompatibilitySingleAgentNoPathConfinement() throws Exception {
        IChatService chatService = new IChatService() {
            int callCount = 0;

            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(build());
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return build();
            }

            private ChatResponse build() {
                int n = callCount++;
                ChatAssistantMessage msg = new ChatAssistantMessage();
                if (n == 0) {
                    msg.setContent("");
                    msg.setToolCalls(List.of(
                            toolCallWithPath("r1", "read-file", "/workspace/project-a/src/Main.java")));
                } else {
                    msg.setContent("done");
                }
                return ChatResponse.success(msg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        IToolManager noOpToolManager = new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(call.getId(), "ok"));
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
                return model;
            }
        };

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, noOpToolManager);

        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        engine.getEventPublisher().addSubscriber(events::add);

        AgentMessageRequest request = new AgentMessageRequest("test-parent-agent-path", "do work");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());

        // No PATH_ACCESS_DENIED events — the path is allowed by the global deny-list
        boolean anyPathDenied = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.PATH_ACCESS_DENIED);
        assertFalse(anyPathDenied,
                "No PATH_ACCESS_DENIED expected in single-agent backward-compatible execution. Events: " + events);

        // No "parent path permission constraint" denials
        boolean anyParentConstraint = events.stream()
                .anyMatch(e -> e.getPayload() != null
                        && e.getPayload().get("reason") != null
                        && ((String) e.getPayload().get("reason")).contains("parent path permission constraint"));
        assertFalse(anyParentConstraint,
                "No parent path permission constraint denials expected in single-agent execution. Events: " + events);
    }

    /**
     * END-TO-END TEST 3 (nested delegation clamping): Proves the path constraint
     * propagates the clamped root set through nested call-agent chains.
     *
     * <p>Chain: A (workDir=/workspace/project-a) → call-agent → B (workDir=
     * /workspace/project-a/sub — within A's scope; B's effective roots clamped
     * to {/workspace/project-a/sub} which is within A's root) → call-agent →
     * C (no workDir — inherits B's effective roots).
     *
     * <p>C attempts a path /workspace/project-b/x → denied (outside A's clamped
     * scope). This proves the path constraint propagates through nested
     * delegation.
     */
    @Test
    void nestedDelegationPropagatesClampedPathScope() throws Exception {
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(build(request));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return build(request);
            }

            private ChatResponse build(ChatRequest request) {
                String agent = identifyAgent(request);
                long assistantTurns = countAssistantMessages(request);
                ChatAssistantMessage msg = new ChatAssistantMessage();

                if (agent.contains("parent path agent P")) {
                    // A: first turn → call-agent(B); second turn → final
                    if (assistantTurns == 0) {
                        msg.setContent("");
                        msg.setToolCalls(List.of(callAgentToolCall("a-call-1",
                                "test-middle-agent-path", "delegate to C")));
                    } else {
                        msg.setContent("A done.");
                    }
                } else if (agent.contains("middle path agent B")) {
                    // B: first turn → call-agent(C); second turn → final
                    if (assistantTurns == 0) {
                        msg.setContent("");
                        msg.setToolCalls(List.of(callAgentToolCall("b-call-1",
                                "test-leaf-agent-path", "do work")));
                    } else {
                        msg.setContent("B done.");
                    }
                } else if (agent.contains("leaf path agent C")) {
                    // C: first turn → try path outside A's scope (should be denied); second turn → final
                    if (assistantTurns == 0) {
                        msg.setContent("");
                        msg.setToolCalls(List.of(toolCallWithPath("c-outside", "read-file",
                                "/workspace/project-b/x")));
                    } else {
                        msg.setContent("C done.");
                    }
                } else {
                    msg.setContent("fallback");
                }
                return ChatResponse.success(msg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        IToolManager toolManager = createToolManagerWithCallAgent();
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        engine.getEventPublisher().addSubscriber(events::add);

        AgentMessageRequest request = new AgentMessageRequest("test-parent-agent-path", "start nested chain");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Execution should complete. Last error: " + result.getError());

        // C's /workspace/project-b/x must be denied by parent path constraint
        // (outside A's clamped scope {/workspace/project-a})
        boolean pathDeniedAtLeaf = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.PATH_ACCESS_DENIED
                        && e.getPayload().get("path") != null
                        && ((String) e.getPayload().get("path")).contains("/workspace/project-b/x")
                        && e.getPayload().get("reason") != null
                        && ((String) e.getPayload().get("reason")).contains("parent path permission constraint"));
        assertTrue(pathDeniedAtLeaf,
                "Leaf agent C's /workspace/project-b/x must be denied by parent path permission constraint "
                        + "(it is outside A's clamped scope). Events: " + events);
    }

    /**
     * END-TO-END TEST 4 (null workDir = no confinement): A parent agent whose
     * model declares NO workDir → call-agent → sub-agent. The sub-agent's path
     * checking is subject ONLY to the global deny-list (no parent path
     * confinement). A path that the global deny-list allows is allowed even
     * though no parent scope was declared.
     */
    @Test
    void nullWorkDirMeansNoPathConfinement() throws Exception {
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(build(request));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return build(request);
            }

            private ChatResponse build(ChatRequest request) {
                String agent = identifyAgent(request);
                long assistantTurns = countAssistantMessages(request);
                ChatAssistantMessage msg = new ChatAssistantMessage();

                if (agent.contains("parent no-workdir agent N")) {
                    if (assistantTurns == 0) {
                        msg.setContent("");
                        msg.setToolCalls(List.of(callAgentToolCall("n-call-1",
                                "test-sub-agent-path", "read files")));
                    } else {
                        msg.setContent("N done.");
                    }
                } else if (agent.contains("sub path agent S")) {
                    // Sub-agent attempts a path that would be outside any parent scope
                    // — but since parent has NO workDir, there is no confinement.
                    // The path is allowed (passes the absent constraint, then the global deny-list).
                    if (assistantTurns == 0) {
                        msg.setContent("");
                        msg.setToolCalls(List.of(
                                toolCallWithPath("s-anywhere", "read-file",
                                        "/workspace/project-z/arbitrary")));
                    } else {
                        msg.setContent("Sub done.");
                    }
                } else {
                    msg.setContent("fallback");
                }
                return ChatResponse.success(msg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        IToolManager toolManager = createToolManagerWithCallAgent();
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        engine.getEventPublisher().addSubscriber(events::add);

        // Parent has NO workDir declared → ABSENT path roots → no path confinement
        AgentMessageRequest request = new AgentMessageRequest(
                "test-parent-agent-no-workdir", "delegate file reads");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Execution should complete. Last error: " + result.getError());

        // The sub-agent's path should NOT be denied by parent path constraint
        // (parent has no workDir → ABSENT path roots → pass-through)
        boolean pathDeniedByParent = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.PATH_ACCESS_DENIED
                        && e.getPayload().get("reason") != null
                        && ((String) e.getPayload().get("reason")).contains("parent path permission constraint"));
        assertFalse(pathDeniedByParent,
                "Sub-agent's path should NOT be denied by parent path constraint when parent has no workDir. "
                        + "Events: " + events);

        // The sub-agent's read-file should have been executed successfully
        boolean readCompleted = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.TOOL_CALL_COMPLETED
                        && "read-file".equals(e.getPayload().get("toolName"))
                        && "success".equals(e.getPayload().get("status")));
        assertTrue(readCompleted,
                "Sub-agent's read-file should have completed successfully (no confinement). Events: " + events);
    }
}
