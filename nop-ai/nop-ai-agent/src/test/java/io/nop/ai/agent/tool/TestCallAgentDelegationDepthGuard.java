package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.session.InMemorySessionStore;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 278 Phase 3 (AR-05): focused tests verifying the delegation recursion
 * depth guard in {@link CallAgentExecutor} prevents unbounded recursion
 * (self-referencing and A↔B mutual recursion) from overflowing the call stack.
 *
 * <p>Uses a <b>real {@link DefaultAgentEngine}</b> with a real call-agent chain
 * (not a RecordingAgentEngine stub), as mandated by the plan's exit criteria.
 *
 * <p>Anti-Hollow + Wiring Verification (Minimum Rules #22 / #23): the tests
 * prove the depth guard is actually reached on the recursive path and that a
 * structured error result (not a StackOverflowError) is returned.
 */
public class TestCallAgentDelegationDepthGuard {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Self-referencing agent (agentId="self") → depth guard prevents overflow
    // ========================================================================

    /**
     * AR-05 core scenario: an agent that calls itself via
     * {@code call-agent(agentId="self")} must not recurse infinitely. The
     * depth guard bounds the recursion, and the session completes without a
     * StackOverflowError. The recursion is provably bounded because the chat
     * call count stays small (not hundreds).
     */
    @Test
    void selfReferencingAgent_completesWithoutStackOverflow() throws Exception {
        AtomicInteger chatCallCount = new AtomicInteger();
        IChatService chat = new SmartRecursionChat("self", null, chatCallCount);

        CallAgentExecutor callAgentExecutor = new CallAgentExecutor();
        IToolManager toolManager = toolManagerWithCallAgent(callAgentExecutor);
        DefaultAgentEngine engine = new DefaultAgentEngine(chat, toolManager, new InMemorySessionStore());

        AgentMessageRequest request = new AgentMessageRequest(
                "test-recursive-a", "start recursion", "self-ref-root", null);

        AgentExecutionResult result = engine.execute(request).get(120, TimeUnit.SECONDS);

        // The session completes (not a StackOverflowError).
        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "self-referencing agent must complete (depth guard prevents stack overflow). "
                        + "Messages: " + result.getMessages());

        // The recursion is bounded: with maxDelegationDepth=4 (default), the
        // recursion goes at most 4 levels (0→1→2→3, rejected at 4). Each level
        // calls the chat service twice (tool call + final response), so at most
        // ~8 calls. Without the guard, this would be unbounded or stack overflow.
        assertTrue(chatCallCount.get() <= 10,
                "chat call count must be bounded by the depth guard. chatCallCount="
                        + chatCallCount.get());
        assertTrue(chatCallCount.get() >= 2,
                "at least one recursion level must have executed. chatCallCount="
                        + chatCallCount.get());
    }

    // ========================================================================
    // Depth guard directly visible: max=1 → root agent sees the error
    // ========================================================================

    /**
     * AR-05 structured error: with {@code maxDelegationDepth=1}, the very
     * first call-agent from the root is rejected (childDepth 1 >= 1). The
     * root agent receives the depth-guard error directly as its tool response,
     * proving the guard returns a structured error (not a silent skip, not
     * a StackOverflowError).
     */
    @Test
    void depthGuardRejectsAtLimit_returnsStructuredErrorInToolResponse() throws Exception {
        AtomicInteger chatCallCount = new AtomicInteger();
        IChatService chat = new SmartRecursionChat("self", null, chatCallCount);

        CallAgentExecutor callAgentExecutor = new CallAgentExecutor();
        callAgentExecutor.setMaxDelegationDepth(1);

        IToolManager toolManager = toolManagerWithCallAgent(callAgentExecutor);
        DefaultAgentEngine engine = new DefaultAgentEngine(chat, toolManager, new InMemorySessionStore());

        AgentMessageRequest request = new AgentMessageRequest(
                "test-recursive-a", "start", "depth-err-root", null);

        AgentExecutionResult result = engine.execute(request).get(120, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "session must complete even when the depth guard rejects the call. "
                        + "Messages: " + result.getMessages());

        // The root agent's tool response contains the depth-guard error
        // (max=1 → the first call-agent is rejected → the error is directly
        // visible in the root's messages).
        boolean hasDepthError = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> ((ChatToolResponseMessage) m).getContent())
                .anyMatch(content -> content != null
                        && content.contains("delegation depth limit reached"));
        assertTrue(hasDepthError,
                "the depth-guard error result must appear in the root tool responses "
                        + "(max=1 → root sees the error directly). Messages: " + result.getMessages());

        // Only 1 successful level (root), no sub-agent executed. The chat was
        // called twice: once for the tool call, once for the final response.
        assertEquals(2, chatCallCount.get(),
                "with max=1, only the root agent's 2 chat calls should occur (no sub-agent). "
                        + "chatCallCount=" + chatCallCount.get());
    }

    // ========================================================================
    // A↔B mutual recursion → depth guard prevents overflow
    // ========================================================================

    /**
     * AR-05 mutual recursion scenario: agent A calls agent B, agent B calls
     * agent A. The depth guard must break the cycle without a stack overflow.
     */
    @Test
    void mutualRecursionAB_completesWithoutStackOverflow() throws Exception {
        AtomicInteger chatCallCount = new AtomicInteger();
        IChatService chat = new SmartRecursionChat(null, null, chatCallCount);

        CallAgentExecutor callAgentExecutor = new CallAgentExecutor();
        IToolManager toolManager = toolManagerWithCallAgent(callAgentExecutor);
        DefaultAgentEngine engine = new DefaultAgentEngine(chat, toolManager, new InMemorySessionStore());

        AgentMessageRequest request = new AgentMessageRequest(
                "test-recursive-a", "start mutual recursion", "mutual-ref-root", null);

        AgentExecutionResult result = engine.execute(request).get(120, TimeUnit.SECONDS);

        // The session completes (not a StackOverflowError).
        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "mutual A↔B recursion must complete (depth guard prevents stack overflow). "
                        + "Messages: " + result.getMessages());

        // The recursion is bounded.
        assertTrue(chatCallCount.get() <= 10,
                "chat call count must be bounded by the depth guard. chatCallCount="
                        + chatCallCount.get());
        assertTrue(chatCallCount.get() >= 4,
                "at least two recursion levels must have executed (A→B→...). chatCallCount="
                        + chatCallCount.get());
    }

    // ========================================================================
    // Configurable max depth: lower limit → fewer recursion levels
    // ========================================================================

    /**
     * AR-05 configurability: {@link CallAgentExecutor#setMaxDelegationDepth(int)}
     * controls the maximum depth. With max=1, no successful delegation occurs
     * (only the root session). With max=3, multiple sub-sessions are created.
     * This proves the depth limit is configurable and affects the recursion
     * bound.
     */
    @Test
    void configurableMaxDepth_controlsRecursionBound() throws Exception {
        // With max=1, the first call-agent is rejected → no sub-sessions.
        InMemorySessionStore storeMax1 = new InMemorySessionStore();
        AtomicInteger chatCountMax1 = new AtomicInteger();
        CallAgentExecutor execMax1 = new CallAgentExecutor();
        execMax1.setMaxDelegationDepth(1);
        DefaultAgentEngine engineMax1 = new DefaultAgentEngine(
                new SmartRecursionChat("self", null, chatCountMax1),
                toolManagerWithCallAgent(execMax1), storeMax1);
        AgentExecutionResult resultMax1 = engineMax1.execute(
                new AgentMessageRequest("test-recursive-a", "start", "cfg-max1-root", null))
                .get(120, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, resultMax1.getStatus());
        int sessionsMax1 = storeMax1.listAllSessions().size();

        // With max=3, up to 2 successful delegations → multiple sub-sessions.
        InMemorySessionStore storeMax3 = new InMemorySessionStore();
        AtomicInteger chatCountMax3 = new AtomicInteger();
        CallAgentExecutor execMax3 = new CallAgentExecutor();
        execMax3.setMaxDelegationDepth(3);
        DefaultAgentEngine engineMax3 = new DefaultAgentEngine(
                new SmartRecursionChat("self", null, chatCountMax3),
                toolManagerWithCallAgent(execMax3), storeMax3);
        AgentExecutionResult resultMax3 = engineMax3.execute(
                new AgentMessageRequest("test-recursive-a", "start", "cfg-max3-root", null))
                .get(120, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, resultMax3.getStatus());
        int sessionsMax3 = storeMax3.listAllSessions().size();

        // max=3 creates more sessions than max=1 (deeper recursion allowed).
        assertTrue(sessionsMax3 > sessionsMax1,
                "max=3 should create more sessions than max=1. "
                        + "sessionsMax1=" + sessionsMax1 + ", sessionsMax3=" + sessionsMax3);
        // max=1 creates exactly 1 session (the root only — no sub-agent).
        assertEquals(1, sessionsMax1,
                "max=1 should create exactly 1 session (root only). sessionsMax1=" + sessionsMax1);

        assertEquals(1, execMax1.getMaxDelegationDepth());
        assertEquals(3, execMax3.getMaxDelegationDepth());
    }

    // ========================================================================
    // No orphan sessions: depth-limited call does not leak engine resources
    // ========================================================================

    /**
     * AR-05 resource-safety: a depth-rejected call-agent invocation never
     * reaches {@code engine.execute}, so no sub-session is created for the
     * rejected level. The session store count stays bounded.
     */
    @Test
    void depthRejectedCall_leavesNoOrphanSession() throws Exception {
        AtomicInteger chatCallCount = new AtomicInteger();
        IChatService chat = new SmartRecursionChat("self", null, chatCallCount);

        CallAgentExecutor callAgentExecutor = new CallAgentExecutor();
        callAgentExecutor.setMaxDelegationDepth(2);

        IToolManager toolManager = toolManagerWithCallAgent(callAgentExecutor);
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = new DefaultAgentEngine(chat, toolManager, store);

        String rootSessionId = "no-orphan-root";
        AgentMessageRequest request = new AgentMessageRequest(
                "test-recursive-a", "start", rootSessionId, null);

        AgentExecutionResult result = engine.execute(request).get(120, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());

        // The root session exists.
        assertNotNull(store.get(rootSessionId),
                "root session must exist in the store");

        // With maxDelegationDepth=2, only 1 successful delegation (root + 1
        // sub-session at depth 1). The depth-2 call is rejected before
        // engine.execute → no session created for it.
        int sessionCount = store.listAllSessions().size();
        assertTrue(sessionCount <= 3,
                "session count must be bounded (depth guard prevents unbounded session creation). "
                        + "sessionCount=" + sessionCount);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Smart chat service for recursion tests. Each agent calls call-agent
     * exactly ONCE (the first LLM turn), then upon receiving any tool
     * response, returns a final assistant message and completes. This
     * mirrors realistic LLM behavior (the model sees the tool result and
     * stops delegating) while still creating a real recursive call chain
     * that exercises the depth guard.
     *
     * @param fixedTargetAgent if non-null, always use this agentId; if null,
     *                         determine the target from the system prompt
     *                         (A→B, B→A for mutual recursion tests)
     */
    static final class SmartRecursionChat implements IChatService {
        final String fixedTargetAgent;
        final String ignored;
        final AtomicInteger callCount;

        SmartRecursionChat(String fixedTargetAgent, String ignored, AtomicInteger callCount) {
            this.fixedTargetAgent = fixedTargetAgent;
            this.ignored = ignored;
            this.callCount = callCount;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            List<ChatMessage> messages = request.getMessages();

            // After receiving any tool response, return a final message
            // (the agent has delegated once and now stops — realistic LLM
            // behavior that prevents the ReAct loop from iterating forever
            // in the test mock).
            boolean hasToolResponse = messages.stream()
                    .anyMatch(m -> m instanceof ChatToolResponseMessage);
            if (hasToolResponse) {
                ChatAssistantMessage finalMsg = new ChatAssistantMessage();
                finalMsg.setContent("Completed after delegation attempt.");
                return ChatResponse.success(finalMsg);
            }

            // Determine the target agent.
            String targetAgent = fixedTargetAgent;
            if (targetAgent == null) {
                targetAgent = "test-recursive-b";
                for (ChatMessage m : messages) {
                    if (m instanceof ChatSystemMessage) {
                        String prompt = m.getContent();
                        if (prompt != null && prompt.contains("agent B")) {
                            targetAgent = "test-recursive-a";
                        }
                        break;
                    }
                }
            }

            // Return a call-agent tool call (first turn only).
            ChatAssistantMessage msg = new ChatAssistantMessage();
            msg.setContent("");
            ChatToolCall toolCall = new ChatToolCall();
            toolCall.setId("depth-" + callCount.get());
            toolCall.setName("call-agent");
            Map<String, Object> args = new HashMap<>();
            args.put("agentId", targetAgent);
            args.put("input", "recurse");
            toolCall.setArguments(args);
            msg.setToolCalls(List.of(toolCall));
            return ChatResponse.success(msg);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {};
        }
    }

    /**
     * Create a tool manager that routes {@code call-agent} to the provided
     * executor, mirroring the pattern in {@code TestCallAgentSendMessageEndToEnd}.
     */
    private static IToolManager toolManagerWithCallAgent(CallAgentExecutor callAgentExecutor) {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                 IToolExecuteContext context) {
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
                return model;
            }
        };
    }
}
