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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 end-to-end tests for sub-agent permission inheritance enforcement
 * (design §4.4). These tests exercise the FULL path through the engine:
 *
 * <p><b>Test 1 (constraint enforcement)</b>: parent ReAct loop → call-agent →
 * engine.execute with constraint metadata → sub-agent ReAct → sub-agent's
 * forbidden tool denied by parent constraint. Proves the constraint flows
 * end-to-end from parent's effective tool set → call-agent metadata → engine
 * constraint application → sub-agent permission wrapper → tool denial.
 *
 * <p><b>Test 2 (backward compatibility)</b>: single-agent execution (no
 * call-agent, no constraint) behaves identically to before this plan — all
 * tools work, no spurious denials.
 */
public class TestSubAgentPermissionEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Tool manager that wires {@code call-agent} to the functional executor and
     * resolves all other tool names to a trivial success result. This lets the
     * full ReAct loop run through the real engine.
     */
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

    private ChatToolCall toolCall(String id, String name) {
        ChatToolCall tc = new ChatToolCall();
        tc.setId(id);
        tc.setName(name);
        tc.setArguments(new HashMap<>());
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

    /**
     * Identify the current agent by its system prompt (the first message).
     * Each test agent has a distinct prompt ("You are parent agent P.", etc.).
     */
    private String identifyAgent(ChatRequest request) {
        List<ChatMessage> messages = request.getMessages();
        if (messages != null && !messages.isEmpty() && messages.get(0) instanceof ChatSystemMessage) {
            return ((ChatSystemMessage) messages.get(0)).getContent();
        }
        return "";
    }

    private boolean hasToolResponseMessage(ChatRequest request) {
        if (request.getMessages() == null) return false;
        return request.getMessages().stream().anyMatch(m -> m instanceof ChatToolResponseMessage);
    }

    /**
     * Count how many assistant messages (LLM turns) have already happened in
     * this request. Used to decide whether the sub-agent should keep trying
     * tools or give a final text answer.
     */
    private long countAssistantMessages(ChatRequest request) {
        return request.getMessages().stream()
                .filter(m -> m instanceof ChatAssistantMessage)
                .count();
    }

    /**
     * END-TO-END TEST 1: Full constraint enforcement path.
     *
     * <p>Parent agent P (declared tools: {read-file, call-agent}) → call-agent →
     * sub-agent S (declared tools: {read-file, write-file, bash}). S attempts
     * write-file and bash (denied by parent constraint) and read-file (allowed).
     *
     * <p>Proves: parent ReAct → call-agent → engine.execute with constraint →
     * sub-agent ReAct → tool denied by inherited constraint. The denial message
     * identifies "parent permission constraint".
     */
    @Test
    void endToEndConstraintEnforcement() throws Exception {
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

                if (agent.contains("parent agent P")) {
                    // Parent: first turn → call-agent; second turn → final answer
                    if (assistantTurns == 0) {
                        msg.setContent("");
                        msg.setToolCalls(List.of(callAgentToolCall("p-call-1",
                                "test-sub-agent-wide", "do risky stuff")));
                    } else {
                        msg.setContent("Parent done.");
                    }
                } else if (agent.contains("sub agent S")) {
                    // Sub-agent: first turn → try forbidden tools; second turn → final
                    if (assistantTurns == 0) {
                        msg.setContent("");
                        msg.setToolCalls(List.of(
                                toolCall("s-write", "write-file"),
                                toolCall("s-bash", "bash"),
                                toolCall("s-read", "read-file")));
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

        AgentMessageRequest request = new AgentMessageRequest("test-parent-agent", "delegate risky work");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Execution should complete. Last error: " + result.getError());

        // The sub-agent runs in its own execution context; its tool denials are
        // observable via the shared event publisher (TOOL_CALL_DENIED events)
        // and the denial reason identifies "parent permission constraint".

        // Verify write-file was denied by parent permission constraint (via event)
        boolean writeDenied = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.TOOL_CALL_DENIED
                        && "write-file".equals(e.getPayload().get("toolName"))
                        && e.getPayload().get("reason") != null
                        && ((String) e.getPayload().get("reason")).contains("parent permission constraint"));
        assertTrue(writeDenied,
                "Sub-agent's write-file must be denied by parent permission constraint. Events: " + events);

        // Verify bash was denied by parent permission constraint (via event)
        boolean bashDenied = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.TOOL_CALL_DENIED
                        && "bash".equals(e.getPayload().get("toolName"))
                        && e.getPayload().get("reason") != null
                        && ((String) e.getPayload().get("reason")).contains("parent permission constraint"));
        assertTrue(bashDenied,
                "Sub-agent's bash must be denied by parent permission constraint. Events: " + events);

        // Verify read-file was NOT denied (no TOOL_CALL_DENIED event for read-file)
        boolean readDenied = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.TOOL_CALL_DENIED
                        && "read-file".equals(e.getPayload().get("toolName")));
        assertFalse(readDenied,
                "Sub-agent's read-file should be allowed (in parent's set). Events: " + events);

        // Verify read-file was actually executed (TOOL_CALL_COMPLETED event)
        boolean readCompleted = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.TOOL_CALL_COMPLETED
                        && "read-file".equals(e.getPayload().get("toolName")));
        assertTrue(readCompleted,
                "Sub-agent's read-file should have been executed (TOOL_CALL_COMPLETED). Events: " + events);

        // Verify the TOOL_CALL_DENIED events were published with the parent-constraint reason
        long denialCount = events.stream()
                .filter(e -> e.getEventType() == AgentEventType.TOOL_CALL_DENIED)
                .filter(e -> e.getPayload().get("reason") != null
                        && ((String) e.getPayload().get("reason")).contains("parent permission constraint"))
                .count();
        assertTrue(denialCount >= 2,
                "At least 2 TOOL_CALL_DENIED events (write-file + bash) with parent-constraint reason expected. Events: " + events);
    }

    /**
     * END-TO-END TEST 2: Backward compatibility. A single-agent execution (no
     * call-agent, no constraint in metadata) has identical behavior to before
     * this plan — all existing tools work, no spurious denials.
     */
    @Test
    void backwardCompatibilitySingleAgentNoSpuriousDenials() throws Exception {
        IChatService chatService = new IChatService() {
            final AtomicInteger callCount = new AtomicInteger(0);

            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(build());
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return build();
            }

            private ChatResponse build() {
                int n = callCount.getAndIncrement();
                ChatAssistantMessage msg = new ChatAssistantMessage();
                if (n == 0) {
                    msg.setContent("");
                    msg.setToolCalls(List.of(
                            toolCall("r1", "read-file"),
                            toolCall("r2", "write-file")));
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

        // Default engine: AllowAllToolAccessChecker (no deny rules), no constraint
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, noOpToolManager);

        AgentMessageRequest request = new AgentMessageRequest("test-parent-agent", "do work");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());

        // No spurious denials — both tools should succeed
        List<ChatToolResponseMessage> toolResponses = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .collect(Collectors.toList());

        boolean anyDenied = toolResponses.stream()
                .anyMatch(m -> m.getContent() != null && m.getContent().contains("denied"));
        assertFalse(anyDenied,
                "No tool should be denied in backward-compatible single-agent execution. Responses: "
                        + toolResponses);
    }

    /**
     * END-TO-END TEST 3 (nested delegation): Proves the constraint propagates
     * the <b>clamped (effective)</b> set — not the declared set — through
     * nested call-agent chains.
     *
     * <p>Chain: A (tools: {read-file, call-agent}) → call-agent → B (tools:
     * {read-file, call-agent, write-file} — B deliberately declares MORE than
     * A's constraint allows) → call-agent → C (tools: {read-file, write-file}).
     *
     * <p>Because A's effective set is {read-file, call-agent}, B's effective
     * set is clamped to {read-file, call-agent} (write-file dropped despite
     * being declared). When B delegates to C, C's constraint = B's
     * <b>effective</b> set {read-file, call-agent}, NOT B's declared set — so
     * C's write-file is denied even though B declared it.
     *
     * <p>Without the clamping fix, C would inherit write-file from B's declared
     * set (permission escalation).
     */
    @Test
    void nestedDelegationPropagatesClampedSet() throws Exception {
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

                if (agent.contains("parent agent P")) {
                    // A: first turn → call-agent(B); second turn → final
                    if (assistantTurns == 0) {
                        msg.setContent("");
                        msg.setToolCalls(List.of(callAgentToolCall("a-call-1",
                                "test-middle-agent", "delegate to C")));
                    } else {
                        msg.setContent("A done.");
                    }
                } else if (agent.contains("middle agent B")) {
                    // B: first turn → call-agent(C); second turn → final
                    if (assistantTurns == 0) {
                        msg.setContent("");
                        msg.setToolCalls(List.of(callAgentToolCall("b-call-1",
                                "test-leaf-agent", "do work")));
                    } else {
                        msg.setContent("B done.");
                    }
                } else if (agent.contains("leaf agent C")) {
                    // C: first turn → try write-file (should be denied); second turn → final
                    if (assistantTurns == 0) {
                        msg.setContent("");
                        msg.setToolCalls(List.of(toolCall("c-write", "write-file")));
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

        AgentMessageRequest request = new AgentMessageRequest("test-parent-agent", "start nested chain");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Execution should complete. Last error: " + result.getError());

        // C's write-file must be denied by parent permission constraint.
        // C's parent constraint = B's EFFECTIVE set {read-file, call-agent}
        // (clamped from B's declared set which included write-file).
        // Without the clamping fix, C would inherit write-file from B's
        // declared set and the call would succeed (permission escalation).
        boolean writeDeniedAtLeaf = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.TOOL_CALL_DENIED
                        && "write-file".equals(e.getPayload().get("toolName"))
                        && e.getPayload().get("reason") != null
                        && ((String) e.getPayload().get("reason")).contains("parent permission constraint"));
        assertTrue(writeDeniedAtLeaf,
                "Leaf agent C's write-file must be denied: it inherits B's CLAMPED set "
                        + "(write-file removed), not B's declared set (which included write-file). "
                        + "Events: " + events);
    }
}
