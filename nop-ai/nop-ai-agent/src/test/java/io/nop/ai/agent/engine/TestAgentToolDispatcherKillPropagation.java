package io.nop.ai.agent.engine;

import io.nop.ai.agent.NopAiAgentException;
import io.nop.ai.agent.conflict.FailFastStrategy;
import io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry;
import io.nop.ai.agent.engine.AgentToolDispatcher.ToolPhaseSession;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.DefaultApprovalGate;
import io.nop.ai.agent.security.DefaultDenialLedger;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.DefaultPermissionMatrix;
import io.nop.ai.agent.security.DefaultPostDenialGuard;
import io.nop.ai.agent.security.DefaultSecurityLevelResolver;
import io.nop.ai.agent.security.DefaultToolAccessChecker;
import io.nop.ai.agent.security.Slf4jAuditLogger;
import io.nop.ai.agent.tool.IToolProgressTracker;
import io.nop.ai.agent.tool.ToolProgressSnapshot;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2519 (C3-011 / 裁定⑦) focused regression — the kill-channel reach
 * judgment (判据写死): a wired {@link IToolProgressTracker} ⇒ mechanical
 * cancel paths MUST reach {@code tracker.cancelExecution(callId)} (the same
 * process-level kill channel as heartbeat abandon / background stop). The
 * pre-fix defect state: timeout/interrupt/cancelCall paths only did
 * {@code future.cancel(true)} — the underlying process kept running as a
 * ghost (bash / container / HTTP).
 *
 * <p>Mock-tracker layer — non-docker runnable (design §5.2 / 勘误 10).
 *
 * <p>Pins (新增断言即新钉 — defect state red, fix green):
 * <ul>
 *   <li>{@link #cancelCallReachesTrackerKill} — cancelCall on a pending call
 *       reaches the kill channel with the addressed callId (defect state:
 *       kill wiring removed — RED).</li>
 *   <li>{@link #interruptedWaitReachesKillForAllPendingCalls} — an
 *       interrupted tool wait phase kills every pending call (defect state:
 *       RED).</li>
 *   <li>{@link #noTrackerIsRecordedNotSilent} — without a tracker the
 *       unreachable kill is explicitly counted (tool-kill-unreachable), never
 *       a silent mechanical-only cancel (defect state: counter dead — RED).</li>
 * </ul>
 */
public class TestAgentToolDispatcherKillPropagation {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Fixtures
    // ========================================================================

    /** Mock tracker — records every kill; non-docker, in-process. */
    static final class RecordingTracker implements IToolProgressTracker {
        final ConcurrentLinkedQueue<String> killedCallIds = new ConcurrentLinkedQueue<>();

        @Override
        public ToolProgressSnapshot progressSnapshot(String callId) {
            return ToolProgressSnapshot.empty();
        }

        @Override
        public boolean cancelExecution(String callId) {
            killedCallIds.add(callId);
            return true;
        }
    }

    /** Tool manager handing back never-settling futures (the hung tool body). */
    static final class NeverToolManager implements IToolManager {
        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                            io.nop.ai.toolkit.api.IToolExecuteContext context) {
            return new CompletableFuture<>();
        }

        @Override
        public CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                io.nop.ai.toolkit.model.AiToolCalls calls,
                io.nop.ai.toolkit.api.IToolExecuteContext context) {
            return CompletableFuture.completedFuture(
                    new io.nop.ai.toolkit.model.AiToolCallsResponse());
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

    private static AgentToolDispatcher dispatcher(IToolManager tm,
                                                  IToolProgressTracker tracker) {
        AgentHookInvoker hookInvoker = new io.nop.ai.agent.engine.AgentHookInvoker(new DefaultHookRegistry(), null);
        AgentToolDispatcher dispatcher = new AgentToolDispatcher(tm, null, null, null, null,
                null, null, 0, null, NoOpCheckpoint.noOp(), hookInvoker,
                new AgentToolPlanResolver(tm),
                new AgentSecurityConsultation(
                        new DefaultPostDenialGuard(), new Slf4jAuditLogger(),
                        new DefaultToolAccessChecker(), new AllowAllPermissionProvider(),
                        new DefaultPathAccessChecker(), new DefaultSecurityLevelResolver(),
                        new DefaultPermissionMatrix(), new DefaultApprovalGate(),
                        new DefaultDenialLedger(), FailFastStrategy.failFast(),
                        new InMemoryWriteIntentRegistry(), new AgentToolPlanResolver(tm),
                        hookInvoker));
        if (tracker != null) {
            dispatcher.setToolProgressTracker(tracker);
        }
        return dispatcher;
    }

    private static AgentExecutionContext ctx() {
        AgentExecutionContext c = new AgentExecutionContext(new AgentModel());
        c.setStatus(AgentExecStatus.running);
        return c;
    }

    private static ChatToolCall callOf(String id, String name) {
        ChatToolCall call = new ChatToolCall();
        call.setId(id);
        call.setName(name);
        call.setArguments(Map.of());
        return call;
    }

    private static ToolPhaseSession hungSession(AgentToolDispatcher dispatcher, String... callIds) {
        AgentExecutionContext c = ctx();
        java.util.List<ChatToolCall> calls = new java.util.ArrayList<>();
        for (String id : callIds) {
            c.addMessage(new ChatToolCallMessage(id, "bash", Map.of()));
            calls.add(callOf(id, "bash"));
        }
        return dispatcher.startToolPhase(c, "agent", "s1", calls, null, 0L, new int[]{0});
    }

    // ========================================================================
    // Pins
    // ========================================================================

    /**
     * cancelCall MUST reach the process-level kill channel when a tracker is
     * wired (javadoc 契约句 now aligned with the implementation). Defect
     * state (kill wiring removed): only the mechanical future cancel happens
     * → RED (tracker saw nothing).
     */
    @Test
    void cancelCallReachesTrackerKill() {
        RecordingTracker tracker = new RecordingTracker();
        AgentToolDispatcher dispatcher = dispatcher(new NeverToolManager(), tracker);
        ToolPhaseSession session = hungSession(dispatcher, "call-1");

        assertTrue(session.cancelCall("call-1"), "the pending call is abandonable");

        assertEquals(List.of("call-1"), List.copyOf(tracker.killedCallIds),
                "cancelCall must reach tracker.cancelExecution with the addressed callId "
                        + "(mechanical-only cancel = the C3-011 ghost-process defect — RED)");
    }

    /**
     * An interrupted tool wait phase kills EVERY pending call — the
     * interrupt-time mechanical cancel path reaches the kill channel.
     * Defect state → RED (no kill reaches the tracker).
     */
    @Test
    void interruptedWaitReachesKillForAllPendingCalls() {
        RecordingTracker tracker = new RecordingTracker();
        AgentToolDispatcher dispatcher = dispatcher(new NeverToolManager(), tracker);
        ToolPhaseSession session = hungSession(dispatcher, "call-1", "call-2");

        // Pre-set the interrupt flag: allOf().get() throws InterruptedException
        // on entry → the wait phase's cancel-and-kill discipline fires.
        Thread.currentThread().interrupt();
        assertThrows(NopAiAgentException.class, () -> session.awaitCompletionSlice(1_000),
                "the interrupted wait phase keeps its honest-failure discipline");
        Thread.interrupted(); // clear the flag for the rest of the test

        assertTrue(tracker.killedCallIds.contains("call-1")
                        && tracker.killedCallIds.contains("call-2"),
                "an interrupted wait must kill every pending call via the tracker "
                        + "(observed kills: " + tracker.killedCallIds
                        + "; mechanical-only cancel = the defect state — RED)");
    }

    /**
     * Without a wired tracker the kill is unreachable — that outcome must be
     * explicitly COUNTED (tool-kill-unreachable), never a silent
     * mechanical-only cancel. Defect state (no counter / no record) → RED.
     */
    @Test
    void noTrackerIsRecordedNotSilent() {
        AgentToolDispatcher dispatcher = dispatcher(new NeverToolManager(), null);
        ToolPhaseSession session = hungSession(dispatcher, "call-1");

        long before = AgentToolDispatcher.toolKillUnreachableCount();
        assertTrue(session.cancelCall("call-1"),
                "the mechanical cancel still succeeds without a tracker");
        long after = AgentToolDispatcher.toolKillUnreachableCount();

        assertTrue(after > before,
                "an unreachable kill must be explicitly recorded (tool-kill-unreachable "
                        + "counter) — the silent pre-fix behavior is the defect state — RED");
        assertFalse(session.pendingCallIds().contains("call-1"),
                "the mechanically cancelled call is no longer pending");
    }
}
