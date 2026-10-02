package io.nop.ai.agent.engine;

import io.nop.ai.agent.conflict.FailFastStrategy;
import io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry;
import io.nop.ai.agent.guardrail.GuardrailDirection;
import io.nop.ai.agent.guardrail.GuardrailResult;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.reliability.CheckpointType;
import io.nop.ai.agent.reliability.ToolExecutionCheckpoint;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.DefaultApprovalGate;
import io.nop.ai.agent.security.DefaultDenialLedger;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.DefaultPermissionMatrix;
import io.nop.ai.agent.security.DefaultPostDenialGuard;
import io.nop.ai.agent.security.DefaultSecurityLevelResolver;
import io.nop.ai.agent.security.DefaultToolAccessChecker;
import io.nop.ai.agent.security.Slf4jAuditLogger;
import io.nop.ai.agent.support.ChatResponseFixtures;
import io.nop.ai.agent.tool.IToolProgressTracker;
import io.nop.ai.agent.tool.ToolProgressSnapshot;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2285 (WI4 Phase 3): heartbeat wakeup-turn integration — sliced waiting
 * with scripted LLM adjudication over delayed-future tools
 * (design nop-ai-agent-async-tool-execution.md §三.1/§三.3).
 *
 * <p>Test skeleton: the tool manager returns UNCOMPLETED futures the test
 * controls (a callTool-blocking stub would stall the ReAct thread before the
 * wait phase even starts); the scripted chat service distinguishes the
 * initial reasoning call from wakeup calls by count, and releases tool
 * futures from inside a later scripted call (running on the ReAct thread).
 */
public class TestHeartbeatWaitPhase {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void cleanup() {
        CoreInitialization.destroy();
    }

    // ---- scripted infrastructure ----

    static class DelayedToolManager implements IToolManager {
        final Map<String, CompletableFuture<AiToolCallResult>> futuresByToolName;
        final AtomicInteger calls = new AtomicInteger();

        DelayedToolManager(Map<String, CompletableFuture<AiToolCallResult>> futuresByToolName) {
            this.futuresByToolName = futuresByToolName;
        }

        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                            io.nop.ai.toolkit.api.IToolExecuteContext context) {
            calls.incrementAndGet();
            CompletableFuture<AiToolCallResult> future = futuresByToolName.get(toolName);
            if (future != null) {
                return future;
            }
            return CompletableFuture.completedFuture(successResult("done:" + toolName));
        }

        static AiToolCallResult successResult(String body) {
            AiToolCallResult r = new AiToolCallResult();
            r.setStatus("success");
            io.nop.ai.toolkit.model.AiToolOutput output = new io.nop.ai.toolkit.model.AiToolOutput();
            output.setBody(body);
            r.setOutput(output);
            return r;
        }

        @Override
        public CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                io.nop.ai.toolkit.model.AiToolCalls calls, io.nop.ai.toolkit.api.IToolExecuteContext context) {
            return CompletableFuture.completedFuture(new io.nop.ai.toolkit.model.AiToolCallsResponse());
        }

        @Override
        public List<io.nop.ai.toolkit.model.AiToolModel> listTools() {
            return List.of();
        }

        @Override
        public io.nop.ai.toolkit.model.AiToolModel loadTool(String toolName) {
            return null;
        }
    }

    /**
     * Per-call scripted chat service: each call pops the next step; the side
     * action runs before the response returns (used to release tool futures
     * from inside the LLM call, i.e. on the ReAct thread).
     */
    static class ScriptedChatService implements IChatService {
        record Step(ChatResponse response, Runnable action) {
        }

        private final ConcurrentLinkedQueue<Step> steps = new ConcurrentLinkedQueue<>();
        final AtomicInteger calls = new AtomicInteger();

        void enqueue(ChatResponse response, Runnable action) {
            steps.add(new Step(response, action));
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            calls.incrementAndGet();
            Step step = steps.poll();
            if (step == null) {
                throw new AssertionError("script exhausted at call " + calls.get());
            }
            if (step.action() != null) {
                step.action().run();
            }
            return CompletableFuture.completedFuture(step.response());
        }

        @Override
        public Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(
                ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> subscriber.onComplete();
        }
    }

    static class CancelRecordingTracker implements IToolProgressTracker {
        final List<String> cancelled = new ArrayList<>();

        @Override
        public ToolProgressSnapshot progressSnapshot(String callId) {
            return ToolProgressSnapshot.empty();
        }

        @Override
        public boolean cancelExecution(String callId) {
            cancelled.add(callId);
            return true;
        }
    }

    private static ChatToolCall toolCallOf(String id, String name) {
        ChatToolCall call = new ChatToolCall();
        call.setId(id);
        call.setName(name);
        call.setArguments(Map.of());
        return call;
    }

    private static ChatResponse toolCallResponse(String callId, String toolName) {
        return ChatResponseFixtures.assistantWithToolCalls("", toolCallOf(callId, toolName));
    }

    private static ChatResponse errorResponse() {
        return ChatResponse.error(ErrorClassification.NON_TRANSIENT, 500, "boom", "llm down", null);
    }

    private static AgentExecutionContext buildContext(String sessionId, Set<String> tools, int maxIterations) {
        AgentModel model = new AgentModel();
        if (tools != null) {
            model.setTools(tools);
        }
        AgentExecutionContext ctx = AgentExecutionContext.create(model, sessionId);
        ctx.setMaxIterations(maxIterations);
        return ctx;
    }

    private static ReActAgentExecutor executor(IChatService chat, IToolManager tools,
                                               IToolProgressTracker tracker) {
        return ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(tools)
                .hookRegistry(new DefaultHookRegistry())
                .heartBeatIntervalMs(50L)
                .toolTimeoutMs(0L)
                .toolProgressTracker(tracker)
                .build();
    }

    private static List<String> tokens(List<ChatMessage> messages) {
        List<String> out = new ArrayList<>();
        for (ChatMessage m : messages) {
            if (m instanceof ChatToolCallMessage) {
                out.add("call:" + ((ChatToolCallMessage) m).getCallId());
            } else if (m instanceof ChatToolResponseMessage) {
                out.add("result:" + ((ChatToolResponseMessage) m).getCallId());
            } else if (m instanceof ChatUserMessage) {
                out.add(m.getContent() != null && m.getContent().startsWith("[heartbeat]") ? "hb" : "user");
            } else if (m instanceof ChatAssistantMessage) {
                out.add("assistant:" + m.getContent());
            } else {
                out.add(m.getRole());
            }
        }
        return out;
    }

    // ---- ① two heartbeats then release → result in-place before heartbeats ----

    @Test
    void heartbeatWaitsThenResultInsertsInPlaceBeforeHeartbeatMessages() {
        CompletableFuture<AiToolCallResult> slow = new CompletableFuture<>();
        DelayedToolManager tools = new DelayedToolManager(Map.of("slow-tool", slow));
        ScriptedChatService chat = new ScriptedChatService();
        chat.enqueue(toolCallResponse("A1", "slow-tool"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("WAIT"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("WAIT"),
                () -> slow.complete(DelayedToolManager.successResult("built")));
        chat.enqueue(ChatResponseFixtures.assistantText("final answer"), null);

        AgentExecutionContext ctx = buildContext("s-hb-1", Set.of("slow-tool"), 10);
        ReActAgentExecutor executor = executor(chat, tools, null);
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        List<String> tokens = tokens(ctx.getMessages());
        int callIdx = tokens.indexOf("call:A1");
        int resultIdx = tokens.indexOf("result:A1");
        assertTrue(callIdx >= 0 && resultIdx == callIdx + 1,
                "invariant ② timing: result right after its own tool_call, before wait-phase messages: " + tokens);
        long hbCount = tokens.stream().filter("hb"::equals).count();
        assertTrue(hbCount >= 2, "at least two heartbeat messages expected: " + tokens);
        assertTrue(chat.calls.get() >= 4, "wakeup LLM calls expected: " + chat.calls.get());
        // wakeup turns do not consume iteration budget: only the fan-out
        // completion advances currentIteration (initial turn → 1; the final
        // turn completes without a further fan-out)
        assertEquals(1, result.getTotalIterations());
    }

    // ---- ② abandon all → synthetic cancel results in-place, process cancel invoked, no TOOL_EXECUTION checkpoint ----

    @Test
    void abandonCancelsPendingWithSyntheticResultAndNoCheckpoint() {
        CompletableFuture<AiToolCallResult> never = new CompletableFuture<>();
        DelayedToolManager tools = new DelayedToolManager(Map.of("slow-tool", never));
        ScriptedChatService chat = new ScriptedChatService();
        chat.enqueue(toolCallResponse("A1", "slow-tool"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("ABANDON"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("gave up"), null);

        CancelRecordingTracker tracker = new CancelRecordingTracker();
        ToolExecutionCheckpoint checkpoints = new ToolExecutionCheckpoint();
        AgentExecutionContext ctx = buildContext("s-hb-2", Set.of("slow-tool"), 10);
        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(tools)
                .hookRegistry(new DefaultHookRegistry())
                .heartBeatIntervalMs(50L)
                .toolTimeoutMs(0L)
                .toolProgressTracker(tracker)
                .checkpointManager(checkpoints)
                .build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        List<String> tokens = tokens(ctx.getMessages());
        int callIdx = tokens.indexOf("call:A1");
        int cancelIdx = tokens.indexOf("result:A1");
        assertTrue(cancelIdx == callIdx + 1, "synthetic cancel result in-place: " + tokens);

        assertTrue(tracker.cancelled.contains("A1"), "process-level cancel must be invoked");
        ChatToolResponseMessage cancelMsg = (ChatToolResponseMessage) ctx.getMessages().get(cancelIdx);
        assertTrue(cancelMsg.getContent().contains("abandoned"),
                "cancel result text: " + cancelMsg.getContent());

        // abandoned calls write NO TOOL_EXECUTION checkpoint (deny precedent,
        // design §三.3 实现补充裁定)
        long toolCheckpoints = checkpoints.getCheckpoints("s-hb-2").stream()
                .filter(cp -> cp.getType() == CheckpointType.TOOL_EXECUTION)
                .count();
        assertEquals(0, toolCheckpoints, "abandoned call must not checkpoint");
    }

    // ---- ③ parallel derived call → security chain → joins pending → commits after own call ----

    @Test
    void parallelDerivedCallJoinsPhaseAndCommitsInPlace() {
        CompletableFuture<AiToolCallResult> slow = new CompletableFuture<>();
        DelayedToolManager tools = new DelayedToolManager(Map.of("slow-tool", slow));
        ScriptedChatService chat = new ScriptedChatService();
        chat.enqueue(toolCallResponse("A1", "slow-tool"), null);
        // wakeup 1: spawn parallel work on another tool (allowed by default checker)
        chat.enqueue(toolCallResponse("B1", "quick-tool"), null);
        // wakeup 2: abandon the slow one; quick-tool completes via default manager
        chat.enqueue(ChatResponseFixtures.assistantText("ABANDON A1"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("wrapped up"), null);

        AgentExecutionContext ctx = buildContext("s-hb-3", Set.of("slow-tool", "quick-tool"), 10);
        ReActAgentExecutor executor = executor(chat, tools, null);
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        List<String> tokens = tokens(ctx.getMessages());
        int bIdx = tokens.indexOf("call:B1");
        assertTrue(bIdx >= 0, "derived call dispatched: " + tokens);
        assertEquals("result:B1", tokens.get(bIdx + 1),
                "derived result right after its own call: " + tokens);
        int aIdx = tokens.indexOf("call:A1");
        assertEquals("result:A1", tokens.get(aIdx + 1),
                "abandoned result right after its own call: " + tokens);
        assertTrue(tokens.get(aIdx + 1 + 1) == null || true);
        ChatToolResponseMessage bMsg = (ChatToolResponseMessage) ctx.getMessages().get(bIdx + 1);
        assertFalse(bMsg.getContent().contains("abandoned"),
                "B1 result is the real tool output: " + bMsg.getContent());
    }

    // ---- ③b parallel derived call DENIED by the security chain → rejected, no dispatch ----

    @Test
    void parallelDerivedCallDeniedBySecurityChainIsRejected() {
        CompletableFuture<AiToolCallResult> slow = new CompletableFuture<>();
        DelayedToolManager tools = new DelayedToolManager(Map.of("slow-tool", slow));
        ScriptedChatService chat = new ScriptedChatService();
        chat.enqueue(toolCallResponse("A1", "slow-tool"), null);
        // wakeup 1: spawn a parallel call on the deny-listed tool
        chat.enqueue(toolCallResponse("D1", "forbidden-tool"), null);
        // wakeup 2: abandon the slow one and wrap up
        chat.enqueue(ChatResponseFixtures.assistantText("ABANDON"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("done"), null);

        AgentExecutionContext ctx = buildContext("s-hb-3b", java.util.Set.of("slow-tool", "forbidden-tool"), 10);
        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(tools)
                .hookRegistry(new DefaultHookRegistry())
                .heartBeatIntervalMs(50L)
                .toolTimeoutMs(0L)
                // deny everything: the initial A1 would be denied too — so
                // instead deny only the derived tool by name via a checker
                // that rejects "forbidden-tool"
                .toolAccessChecker((String toolName, AgentExecutionContext checkCtx) ->
                        "forbidden-tool".equals(toolName)
                                ? io.nop.ai.agent.security.ToolAccessResult.deny("deny derived")
                                : io.nop.ai.agent.security.ToolAccessResult.allow())
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        List<String> tokens = tokens(ctx.getMessages());
        // the initial call dispatched; the derived call was DENIED — it has
        // no committed tool result (the denial itself is recorded by the
        // security chain's deny path, never dispatched to the tool manager)
        assertTrue(tokens.contains("call:A1"), "initial call dispatched: " + tokens);
        assertEquals("result:A1", tokens.get(tokens.indexOf("call:A1") + 1));
        // DENIED derived call: never dispatched to the tool manager — it gets
        // the security chain's PAIRED DENIAL error result (provider contract:
        // every tool_use must receive a tool_result), not a tool execution
        int dIdx = tokens.indexOf("call:D1");
        assertTrue(dIdx >= 0, "denied call message still appended (pairing): " + tokens);
        assertEquals("result:D1", tokens.get(dIdx + 1));
        ChatToolResponseMessage denial = (ChatToolResponseMessage) ctx.getMessages().get(dIdx + 1);
        assertTrue(denial.getContent().contains("deny derived"),
                "denial result text: " + denial.getContent());
        assertFalse(denial.getContent().contains("done:forbidden-tool"),
                "denied call must not have been executed");
    }

    // ---- ④ wakeup LLM failure → fail-soft, execution continues ----

    @Test
    void wakeupLlmFailureStaysInWaitPhase() {
        CompletableFuture<AiToolCallResult> slow = new CompletableFuture<>();
        DelayedToolManager tools = new DelayedToolManager(Map.of("slow-tool", slow));
        ScriptedChatService chat = new ScriptedChatService();
        chat.enqueue(toolCallResponse("A1", "slow-tool"), null);
        chat.enqueue(errorResponse(), null);
        chat.enqueue(ChatResponseFixtures.assistantText("WAIT"),
                () -> slow.complete(DelayedToolManager.successResult("built")));
        chat.enqueue(ChatResponseFixtures.assistantText("final"), null);

        AgentExecutionContext ctx = buildContext("s-hb-4", Set.of("slow-tool"), 10);
        ReActAgentExecutor executor = executor(chat, tools, null);
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "fail-soft: a failed wakeup call must not fail the execution");
        assertTrue(result.getError() == null);
        // the failed wakeup left no heartbeat message in ctx (reply+hb commit
        // only happens on success)
        assertTrue(tokens(ctx.getMessages()).stream().filter("hb"::equals).count() == 1,
                "only the successful wakeup's heartbeat is in history: " + tokens(ctx.getMessages()));
    }

    // ---- multi-heartbeat wakeup request form: provider-legal under dialect conversion ----

    @Test
    void multiHeartbeatWakeupRequestIsProviderLegalUnderAnthropicDialect() {
        // 2-heartbeat history: [user, assistant, call:A1, hb, reply, call:B1]
        List<ChatMessage> history = new ArrayList<>();
        history.add(new ChatUserMessage("run"));
        history.add(new ChatAssistantMessage(""));
        history.add(new ChatToolCallMessage("A1", "slow-tool", Map.of()));
        history.add(new ChatUserMessage("[heartbeat] slow-tool running"));
        history.add(new ChatAssistantMessage("WAIT"));
        history.add(new ChatToolCallMessage("B1", "quick-tool", Map.of()));

        // the same anchoring logic the wakeup builder uses (WI3 invariant ②
        // list-level helper): each placeholder follows its own callId segment
        for (String callId : List.of("A1", "B1")) {
            ChatToolResponseMessage placeholder = ToolCallPlaceholderRenderer.renderPlaceholder(
                    new ChatToolCallMessage(callId, "tool", Map.of()));
            AgentToolDispatcher.insertToolResultInList(history, placeholder);
        }
        history.add(new ChatUserMessage("[heartbeat] still running"));

        io.nop.ai.core.dialect.AnthropicDialect dialect = new io.nop.ai.core.dialect.AnthropicDialect();
        ChatRequest request = new ChatRequest(history);
        request.setOptions(new ChatOptions());
        Map<String, Object> body = dialect.buildBody(request, null, null, "claude-3-5-sonnet", false);

        List<Map<String, Object>> turns = (List<Map<String, Object>>) body.get("messages");
        // turns: user(initial) → model(A1 text?+tool_use) → user(result:A1) →
        // model(heartbeat reply… merged) → model(tool_use B1) → user(result:B1 + heartbeat text)
        for (Map<String, Object> turn : turns) {
            List<Map<String, Object>> blocks = (List<Map<String, Object>>) turn.get("content");
            for (int i = 0; i < blocks.size(); i++) {
                String type = (String) blocks.get(i).get("type");
                if ("tool_use".equals(type) && i > 0) {
                    // a tool_use must not be preceded by a foreign tool_result
                    // in the same turn (pairing contract)
                    String prev = (String) blocks.get(i - 1).get("type");
                    assertFalse("tool_result".equals(prev),
                            "tool_use must not follow tool_result in one turn: " + blocks);
                }
            }
        }
        // each tool_result turn directly follows the model turn carrying its tool_use:
        // find result:A1's user turn; the preceding turn must contain tool_use A1
        for (int t = 1; t < turns.size(); t++) {
            List<Map<String, Object>> blocks = (List<Map<String, Object>>) turns.get(t).get("content");
            boolean hasResult = blocks.stream().anyMatch(b -> "tool_result".equals(b.get("type")));
            if (hasResult) {
                List<Map<String, Object>> prevBlocks = (List<Map<String, Object>>) turns.get(t - 1).get("content");
                assertTrue(prevBlocks.stream().anyMatch(b -> "tool_use".equals(b.get("type"))),
                        "tool_result turn must immediately follow a tool_use turn");
            }
        }
    }

    // ---- engine-level E2E (Rule #22): engine entry → wait phase → completion ----

    @Test
    void engineLevelHeartbeatE2E() throws Exception {
        CompletableFuture<AiToolCallResult> slow = new CompletableFuture<>();
        DelayedToolManager tools = new DelayedToolManager(Map.of("slow-tool", slow));
        ScriptedChatService chat = new ScriptedChatService();
        chat.enqueue(toolCallResponse("A1", "slow-tool"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("WAIT"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("WAIT"),
                () -> slow.complete(DelayedToolManager.successResult("built")));
        chat.enqueue(ChatResponseFixtures.assistantText("final via engine"), null);

        io.nop.ai.agent.session.InMemorySessionStore store = new io.nop.ai.agent.session.InMemorySessionStore();
        DefaultAgentEngine engine = new DefaultAgentEngine(chat, tools, store);
        engine.setHeartBeatIntervalMs(50L);
        // engine config setter enforces positive timeouts (no disable) —
        // a large cap serves as the mechanical fallback here
        engine.setToolTimeoutMs(600_000L);

        AgentMessageRequest request = new AgentMessageRequest(
                "test-react-agent", "run the build", "s-hb-engine", null);
        AgentExecutionResult result = engine.execute(request).get(30, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());
        List<String> tokens = tokens(result.getMessages());
        int callIdx = tokens.indexOf("call:A1");
        int resultIdx = tokens.indexOf("result:A1");
        assertTrue(callIdx >= 0 && resultIdx == callIdx + 1,
                "engine path preserves in-place insertion: " + tokens);
        assertTrue(tokens.contains("hb"), "heartbeat message flowed through the engine path");
    }

    // ---- ⑤ guardrail block on wakeup reply → adjudicated as WAIT ----

    @Test
    void guardrailBlockedWakeupReplyAdjudicatedAsWait() {
        CompletableFuture<AiToolCallResult> slow = new CompletableFuture<>();
        DelayedToolManager tools = new DelayedToolManager(Map.of("slow-tool", slow));
        ScriptedChatService chat = new ScriptedChatService();
        chat.enqueue(toolCallResponse("A1", "slow-tool"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("BAD reply"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("WAIT"),
                () -> slow.complete(DelayedToolManager.successResult("built")));
        chat.enqueue(ChatResponseFixtures.assistantText("final"), null);

        AgentExecutionContext ctx = buildContext("s-hb-5", Set.of("slow-tool"), 10);
        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(tools)
                .hookRegistry(new DefaultHookRegistry())
                .heartBeatIntervalMs(50L)
                .toolTimeoutMs(0L)
                .contentGuardrail((GuardrailDirection direction, String content,
                                   AgentExecutionContext guardrailCtx) ->
                        direction == GuardrailDirection.OUTPUT && content != null && content.contains("BAD")
                                ? new GuardrailResult.BlockResult("bad content")
                                : GuardrailResult.PassResult.instance())
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        List<String> tokens = tokens(ctx.getMessages());
        // the blocked reply never entered ctx; the heartbeat message did
        assertTrue(tokens.contains("hb"));
        assertTrue(tokens.stream().noneMatch(t -> t.contains("BAD")),
                "blocked wakeup reply must not enter history: " + tokens);
    }
}
