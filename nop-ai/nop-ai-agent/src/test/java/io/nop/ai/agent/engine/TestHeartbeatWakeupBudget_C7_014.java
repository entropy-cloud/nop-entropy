package io.nop.ai.agent.engine;

import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.support.ChatResponseFixtures;
import io.nop.ai.agent.tool.IToolProgressTracker;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 3.1-C7-014（plan 2501 Phase 4）heartbeat wakeup 预算与 WAIT 碎片生命周期契约：
 *
 * <ul>
 *   <li><b>wakeup 数量预算</b>：每等待期（per tool-wait phase）wakeup LLM 调用数被
 *       {@code maxHeartbeatTurns} 封顶——超限降级纯机械等待（deadline/cancel/
 *       pause/forceStop/steering 全部保持活性），降级转场 INFO 日志显式可观测
 *       （rule 24 不静默）；{@code <= 0} 完全禁用 wakeup 轮。</li>
 *   <li><b>WAIT 碎片生命周期</b>：WAIT 裁决的 heartbeat 证据改由请求副本携带
 *       （WI3 invariant 1 同机制），WAIT 回复为瞬态裁决——ctx 终史零碎片；仅终局
 *       裁决（ABANDON / parallel work）提交证据与回复（正保护面，防证据丢失）。</li>
 *   <li><b>接线验证（rule 23）</b>：真实 runHeartbeatToolPhase 等待循环上断言预算
 *       真实约束 LLM 调用计数（修复前 wakeup 每 slice 到期即调用、无上限——预算
 *       封顶断言先红，见变异探针 {@code _tmp/phase4-c7-014-red.log}）。</li>
 * </ul>
 */
public class TestHeartbeatWakeupBudget_C7_014 {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void cleanup() {
        CoreInitialization.destroy();
    }

    // ---- scripted infrastructure (same shape as TestHeartbeatWaitPhase) ----

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

    private static AgentExecutionContext buildContext(String sessionId, Set<String> tools) {
        AgentModel model = new AgentModel();
        if (tools != null) {
            model.setTools(tools);
        }
        AgentExecutionContext ctx = AgentExecutionContext.create(model, sessionId);
        ctx.setMaxIterations(10);
        return ctx;
    }

    private static ReActAgentExecutor executor(IChatService chat, IToolManager tools,
                                               long heartBeatIntervalMs, long toolTimeoutMs,
                                               int maxHeartbeatTurns) {
        return ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(tools)
                .hookRegistry(new DefaultHookRegistry())
                .heartBeatIntervalMs(heartBeatIntervalMs)
                .toolTimeoutMs(toolTimeoutMs)
                .maxHeartbeatTurns(maxHeartbeatTurns)
                .build();
    }

    /**
     * 预算封顶断言（先红面，rule 23 接线）：cap=1000ms、slice=20ms ⇒ ~48 个 slice
     * 到期窗口；budget=2 ⇒ wakeup LLM 调用恰好封顶为 2（initial + 2 wakeups +
     * final = 4）。修复前（或预算门失效变异）wakeup 无上限 ⇒ ~48 次调用（红）。
     */
    @Test
    void wakeupTurnsAreBudgetCappedPerWaitPhase() {
        CompletableFuture<AiToolCallResult> never = new CompletableFuture<>();
        TestHeartbeatWaitPhase.DelayedToolManager tools =
                new TestHeartbeatWaitPhase.DelayedToolManager(Map.of("slow-tool", never));
        TestHeartbeatWaitPhase.ScriptedChatService chat = new TestHeartbeatWaitPhase.ScriptedChatService();
        chat.enqueue(toolCallResponse("A1", "slow-tool"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("WAIT"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("WAIT"), null);
        // budget exhausted → pure mechanical wait; the 1000ms mechanical cap
        // completes the future exceptionally → COMPLETED → final turn
        chat.enqueue(ChatResponseFixtures.assistantText("final"), null);

        AgentExecutionContext ctx = buildContext("s-c714-budget", Set.of("slow-tool"));
        ReActAgentExecutor executor = executor(chat, tools, 20L, 1000L, 2);
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(4, chat.calls.get(),
                "wakeup LLM calls must be capped at the budget (initial + 2 wakeups + final); "
                        + "pre-fix/mutation: every slice expiry issued a wakeup → unbounded calls");
    }

    /** 预算=0 ⇒ wakeup 轮完全禁用（纯机械等待到 cap 到期），LLM 调用仅 initial+final。 */
    @Test
    void zeroBudgetDisablesWakeupTurnsEntirely() {
        CompletableFuture<AiToolCallResult> never = new CompletableFuture<>();
        TestHeartbeatWaitPhase.DelayedToolManager tools =
                new TestHeartbeatWaitPhase.DelayedToolManager(Map.of("slow-tool", never));
        TestHeartbeatWaitPhase.ScriptedChatService chat = new TestHeartbeatWaitPhase.ScriptedChatService();
        chat.enqueue(toolCallResponse("A1", "slow-tool"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("final"), null);

        AgentExecutionContext ctx = buildContext("s-c714-zero", Set.of("slow-tool"));
        ReActAgentExecutor executor = executor(chat, tools, 20L, 200L, 0);
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(2, chat.calls.get(),
                "budget 0 disables wakeup turns entirely (initial + final only)");
    }

    /**
     * WAIT 碎片不入终史（正保护）：wakeup 请求副本携带 hb 证据（模型协议满足面）、
     * ctx 终史零 hb、瞬态 WAIT 回复不入史。
     */
    @Test
    void waitWakeupEvidenceRidesRequestCopyOnly() {
        CompletableFuture<AiToolCallResult> slow = new CompletableFuture<>();
        TestHeartbeatWaitPhase.DelayedToolManager tools =
                new TestHeartbeatWaitPhase.DelayedToolManager(Map.of("slow-tool", slow));
        TestHeartbeatWaitPhase.ScriptedChatService chat = new TestHeartbeatWaitPhase.ScriptedChatService();
        chat.enqueue(toolCallResponse("A1", "slow-tool"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("WAIT"),
                () -> slow.complete(TestHeartbeatWaitPhase.DelayedToolManager.successResult("built")));
        chat.enqueue(ChatResponseFixtures.assistantText("final"), null);

        AgentExecutionContext ctx = buildContext("s-c714-wait", Set.of("slow-tool"));
        ReActAgentExecutor executor = executor(chat, tools, 20L, 0L, 20);
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(TestHeartbeatWaitPhase.hbRequestCount(chat) >= 1,
                "the wakeup request copies must carry the heartbeat evidence (model protocol "
                        + "satisfied on the transient copy)");
        assertEquals(0, ctx.getMessages().stream()
                        .filter(TestHeartbeatWaitPhase::isHeartbeatMessage).count(),
                "WAIT verdicts commit nothing — the ctx terminal history carries zero heartbeat "
                        + "fragments: " + TestHeartbeatWaitPhase.tokens(ctx.getMessages()));
        assertTrue(ctx.getMessages().stream().noneMatch(m ->
                        m instanceof ChatAssistantMessage && "WAIT".equals(m.getContent())),
                "the transient WAIT reply must not enter the terminal history");
    }

    /**
     * 终局裁决（ABANDON）仍提交证据与回复（正保护面——预算/副本化不丢失终局记录）：
     * hb 恰好 1 条入史 + 合成取消结果 in-place + 进程级取消被调用。
     */
    @Test
    void abandonVerdictStillCommitsHeartbeatEvidence() {
        CompletableFuture<AiToolCallResult> never = new CompletableFuture<>();
        TestHeartbeatWaitPhase.DelayedToolManager tools =
                new TestHeartbeatWaitPhase.DelayedToolManager(Map.of("slow-tool", never));
        TestHeartbeatWaitPhase.ScriptedChatService chat = new TestHeartbeatWaitPhase.ScriptedChatService();
        chat.enqueue(toolCallResponse("A1", "slow-tool"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("ABANDON"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("gave up"), null);

        TestHeartbeatWaitPhase.CancelRecordingTracker tracker = new TestHeartbeatWaitPhase.CancelRecordingTracker();
        AgentExecutionContext ctx = buildContext("s-c714-abandon", Set.of("slow-tool"));
        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(tools)
                .hookRegistry(new DefaultHookRegistry())
                .heartBeatIntervalMs(20L)
                .toolTimeoutMs(0L)
                .toolProgressTracker((IToolProgressTracker) tracker)
                .build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(tracker.cancelled.contains("A1"), "process-level cancel must be invoked");
        assertEquals(1, ctx.getMessages().stream()
                        .filter(TestHeartbeatWaitPhase::isHeartbeatMessage).count(),
                "the terminal ABANDON adjudication commits its heartbeat evidence + reply: "
                        + TestHeartbeatWaitPhase.tokens(ctx.getMessages()));
        List<String> tokens = TestHeartbeatWaitPhase.tokens(ctx.getMessages());
        int callIdx = tokens.indexOf("call:A1");
        assertTrue(callIdx >= 0 && "result:A1".equals(tokens.get(callIdx + 1)),
                "synthetic cancel result in-place: " + tokens);
    }

    /**
     * 终局裁决（parallel work）仍提交证据：两个终局 wakeup（派生 B1 + ABANDON）
     * 各提交一条 hb——派生调用的 tool_call/result 配对完整。
     */
    @Test
    void parallelWorkVerdictStillCommitsHeartbeatEvidence() {
        CompletableFuture<AiToolCallResult> slow = new CompletableFuture<>();
        TestHeartbeatWaitPhase.DelayedToolManager tools =
                new TestHeartbeatWaitPhase.DelayedToolManager(Map.of("slow-tool", slow));
        TestHeartbeatWaitPhase.ScriptedChatService chat = new TestHeartbeatWaitPhase.ScriptedChatService();
        chat.enqueue(toolCallResponse("A1", "slow-tool"), null);
        chat.enqueue(toolCallResponse("B1", "quick-tool"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("ABANDON"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("done"), null);

        AgentExecutionContext ctx = buildContext("s-c714-parallel", Set.of("slow-tool", "quick-tool"));
        ReActAgentExecutor executor = executor(chat, tools, 20L, 0L, 20);
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(2, ctx.getMessages().stream()
                        .filter(TestHeartbeatWaitPhase::isHeartbeatMessage).count(),
                "both terminal wakeups (parallel spawn + ABANDON) commit their evidence: "
                        + TestHeartbeatWaitPhase.tokens(ctx.getMessages()));
        List<String> tokens = TestHeartbeatWaitPhase.tokens(ctx.getMessages());
        int bIdx = tokens.indexOf("call:B1");
        assertTrue(bIdx >= 0 && "result:B1".equals(tokens.get(bIdx + 1)),
                "derived call result in-place: " + tokens);
        int aIdx = tokens.indexOf("call:A1");
        assertTrue(aIdx >= 0 && "result:A1".equals(tokens.get(aIdx + 1)),
                "abandoned call result in-place: " + tokens);
    }

    /**
     * 预算耗尽降级转场显式可观测（rule 24）：INFO 日志携带 "wakeup budget" +
     * "exhausted"，只记一次。
     */
    @Test
    void budgetExhaustionIsExplicitlyObservable() {
        CompletableFuture<AiToolCallResult> never = new CompletableFuture<>();
        TestHeartbeatWaitPhase.DelayedToolManager tools =
                new TestHeartbeatWaitPhase.DelayedToolManager(Map.of("slow-tool", never));
        TestHeartbeatWaitPhase.ScriptedChatService chat = new TestHeartbeatWaitPhase.ScriptedChatService();
        chat.enqueue(toolCallResponse("A1", "slow-tool"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("WAIT"), null);
        chat.enqueue(ChatResponseFixtures.assistantText("final"), null);

        Logger logger = (Logger) LoggerFactory.getLogger(ReActAgentExecutor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            AgentExecutionContext ctx = buildContext("s-c714-observable", Set.of("slow-tool"));
            ReActAgentExecutor executor = executor(chat, tools, 20L, 400L, 1);
            AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

            assertEquals(AgentExecStatus.completed, result.getStatus());
            assertEquals(3, chat.calls.get(), "budget 1: initial + 1 wakeup + final");
            long budgetLogs = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.INFO
                            && e.getFormattedMessage().contains("wakeup budget")
                            && e.getFormattedMessage().contains("exhausted"))
                    .count();
            assertTrue(budgetLogs >= 1,
                    "the budget-exhausted degradation must be explicitly observable in the log");
            assertEquals(1, budgetLogs, "the transition is logged exactly once, not per slice");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
