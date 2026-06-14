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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 end-to-end tests for per-agent glob path-rule inheritance across
 * delegation levels (design §4.3/§4.4).
 *
 * <p><b>Test 1 (rule inheritance)</b>: parent A declares path-rules (DENY
 * /workspace/secret/**, ALLOW /workspace/**) → call-agent → sub-agent B
 * (DENY /workspace/temp/**). B attempts paths denied by parent's DENY rule
 * (cross-level deny-wins), B's own DENY rule, and an allowed path.
 *
 * <p><b>Test 2 (nested delegation accumulation)</b>: A (DENY /shared/secret/**)
 * → B (no own rules) → C. C attempts /shared/secret/key → denied by A's rule
 * propagated through B without B re-declaring it.
 */
public class TestPathRulesInheritanceEndToEnd {

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
     * END-TO-END TEST 1: Per-agent path-rule inheritance across delegation.
     *
     * <p>Parent A (DENY /workspace/secret/**, ALLOW /workspace/**) → call-agent
     * → sub-agent B (DENY /workspace/temp/**). B attempts:
     * <ul>
     *   <li>/workspace/secret/key → denied by parent A's DENY rule (cross-level deny-wins)</li>
     *   <li>/workspace/temp/file → denied by B's own DENY rule (within-agent first-match-wins)</li>
     *   <li>/workspace/src/Main.java → allowed (passes parent rules, B's rules, global deny-list)</li>
     * </ul>
     */
    @Test
    void ruleInheritanceEnforcedEndToEnd() throws Exception {
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
                long turns = countAssistantMessages(request);
                ChatAssistantMessage msg = new ChatAssistantMessage();

                if (agent.contains("parent rules agent A")) {
                    if (turns == 0) {
                        msg.setContent("");
                        msg.setToolCalls(List.of(callAgentToolCall("a-call-1",
                                "test-sub-agent-path-rules", "read some files")));
                    } else {
                        msg.setContent("A done.");
                    }
                } else if (agent.contains("sub rules agent B")) {
                    if (turns == 0) {
                        msg.setContent("");
                        msg.setToolCalls(List.of(
                                toolCallWithPath("b-parent-deny", "read-file",
                                        "/workspace/secret/key"),
                                toolCallWithPath("b-own-deny", "read-file",
                                        "/workspace/temp/file"),
                                toolCallWithPath("b-allowed", "read-file",
                                        "/workspace/src/Main.java")));
                    } else {
                        msg.setContent("B done.");
                    }
                } else {
                    msg.setContent("fallback");
                }
                return ChatResponse.success(msg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        IToolManager toolManager = createToolManagerWithCallAgent();
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        engine.getEventPublisher().addSubscriber(events::add);

        AgentMessageRequest request = new AgentMessageRequest("test-parent-agent-path-rules", "delegate file reads");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Execution should complete. Last error: " + result.getError());

        // B's /workspace/secret/key must be denied by parent A's DENY rule (cross-level deny-wins)
        boolean parentRuleDeny = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.PATH_ACCESS_DENIED
                        && e.getPayload().get("path") != null
                        && ((String) e.getPayload().get("path")).contains("/workspace/secret/key")
                        && e.getPayload().get("reason") != null
                        && ((String) e.getPayload().get("reason")).contains("parent path-rule"));
        assertTrue(parentRuleDeny,
                "B's /workspace/secret/key must be denied by parent A's DENY path-rule (cross-level). Events: " + events);

        // B's /workspace/temp/file must be denied by B's own DENY rule (within-agent)
        boolean ownRuleDeny = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.PATH_ACCESS_DENIED
                        && e.getPayload().get("path") != null
                        && ((String) e.getPayload().get("path")).contains("/workspace/temp/file")
                        && e.getPayload().get("reason") != null
                        && ((String) e.getPayload().get("reason")).contains("agent path-rule"));
        assertTrue(ownRuleDeny,
                "B's /workspace/temp/file must be denied by B's own DENY path-rule. Events: " + events);

        // B's /workspace/src/Main.java must NOT be denied
        boolean allowedDenied = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.PATH_ACCESS_DENIED
                        && e.getPayload().get("path") != null
                        && ((String) e.getPayload().get("path")).contains("/workspace/src/Main.java"));
        assertFalse(allowedDenied,
                "B's /workspace/src/Main.java should NOT be denied (passes parent rules, B's rules, global deny-list). Events: " + events);
    }

    /**
     * END-TO-END TEST 2: Nested delegation rule-chain accumulation.
     *
     * <p>Chain: A (DENY /shared/secret/**) → call-agent → B (no own path-rules)
     * → call-agent → C (no own path-rules). C attempts /shared/secret/key →
     * denied by A's DENY rule propagated through B without B re-declaring it.
     */
    @Test
    void nestedDelegationAccumulatesRuleChain() throws Exception {
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
                long turns = countAssistantMessages(request);
                ChatAssistantMessage msg = new ChatAssistantMessage();

                if (agent.contains("nested parent agent A")) {
                    if (turns == 0) {
                        msg.setContent("");
                        msg.setToolCalls(List.of(callAgentToolCall("a-call-1",
                                "test-nested-middle-rules", "delegate to B")));
                    } else {
                        msg.setContent("A done.");
                    }
                } else if (agent.contains("nested middle agent B")) {
                    if (turns == 0) {
                        msg.setContent("");
                        msg.setToolCalls(List.of(callAgentToolCall("b-call-1",
                                "test-nested-leaf-rules", "delegate to C")));
                    } else {
                        msg.setContent("B done.");
                    }
                } else if (agent.contains("nested leaf agent C")) {
                    if (turns == 0) {
                        msg.setContent("");
                        msg.setToolCalls(List.of(toolCallWithPath("c-deny", "read-file",
                                "/shared/secret/key")));
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
                return subscriber -> {};
            }
        };

        IToolManager toolManager = createToolManagerWithCallAgent();
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        engine.getEventPublisher().addSubscriber(events::add);

        AgentMessageRequest request = new AgentMessageRequest("test-nested-parent-rules", "start nested chain");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Execution should complete. Last error: " + result.getError());

        // C's /shared/secret/key must be denied by A's DENY rule propagated through B
        boolean aRuleDeniedAtC = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.PATH_ACCESS_DENIED
                        && e.getPayload().get("path") != null
                        && ((String) e.getPayload().get("path")).contains("/shared/secret/key")
                        && e.getPayload().get("reason") != null
                        && ((String) e.getPayload().get("reason")).contains("parent path-rule"));
        assertTrue(aRuleDeniedAtC,
                "C's /shared/secret/key must be denied by A's DENY rule propagated through B "
                        + "(rule-chain accumulates without B re-declaring). Events: " + events);
    }
}
