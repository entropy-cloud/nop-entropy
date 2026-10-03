package io.nop.ai.agent.engine;

import io.nop.ai.agent.conflict.FailFastStrategy;
import io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry;
import io.nop.ai.agent.engine.AgentToolDispatcher.ToolPhaseSession;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.reliability.CheckpointType;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
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
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2287 (WI6): tool execution phase checkpoints — DISPATCHED/EXECUTING
 * phase-tagged TOOL_EXECUTION checkpoints (watermark phase segment as sole
 * authority), the committed-set narrowing (only terminal phase-less
 * checkpoints count as committed — invariant ③ state (a) orphan alarm must
 * survive phase tagging), and the conservative recovery phase resolution.
 */
public class TestToolExecutionPhaseCheckpoints {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void cleanup() {
        CoreInitialization.destroy();
    }

    static AgentToolDispatcher dispatcher(IToolManagerStub tm, ToolExecutionCheckpoint checkpoints) {
        AgentHookInvoker hookInvoker = new AgentHookInvoker(new DefaultHookRegistry(), null);
        return new AgentToolDispatcher(tm, null, null, null, null, null, null,
                0, null, checkpoints, hookInvoker,
                new AgentToolPlanResolver(tm),
                new AgentSecurityConsultation(
                        new DefaultPostDenialGuard(), new Slf4jAuditLogger(),
                        new DefaultToolAccessChecker(), new AllowAllPermissionProvider(),
                        new DefaultPathAccessChecker(), new DefaultSecurityLevelResolver(),
                        new DefaultPermissionMatrix(), new DefaultApprovalGate(),
                        new DefaultDenialLedger(), FailFastStrategy.failFast(),
                        new InMemoryWriteIntentRegistry(), new AgentToolPlanResolver(tm),
                        hookInvoker));
    }

    interface IToolManagerStub extends io.nop.ai.toolkit.api.IToolManager {
    }

    static AiToolCallResult success() {
        AiToolCallResult r = new AiToolCallResult();
        r.setStatus("success");
        return r;
    }

    @Test
    void dispatchedPhaseCheckpointWrittenAndReadable() {
        ToolExecutionCheckpoint checkpoints = new ToolExecutionCheckpoint();
        AgentToolDispatcher dispatcher = dispatcher(new CompletedToolManager(), checkpoints);
        AgentExecutionContext ctx = new AgentExecutionContext(new AgentModel());
        ctx.setStatus(AgentExecStatus.running);
        ctx.addMessage(new ChatToolCallMessage("1", "bash", Map.of()));

        int[] seq = {0};
        ToolPhaseSession session = dispatcher.startToolPhase(ctx, "agent", "s1",
                List.of(callOf("1", "bash")), null, 777L, seq);

        // DISPATCHED written at phase start; seq advanced
        List<Checkpoint> saved = checkpoints.getCheckpoints("s1");
        assertEquals(1, saved.size());
        assertEquals(CheckpointType.TOOL_EXECUTION, saved.get(0).getType());
        assertEquals("1", saved.get(0).getCallId());
        assertEquals(ToolExecutionPhase.DISPATCHED,
                ToolExecutionPhase.fromWatermark(saved.get(0).getWatermark()));
        assertTrue(saved.get(0).getWatermark().contains("777"),
                "watermark keeps the execStartTime segment: " + saved.get(0).getWatermark());
        assertEquals(1, seq[0]);

        session.awaitCompletionSlice(1000);
        session.commitResults();
        // terminal checkpoint appended (phase-less)
        List<Checkpoint> all = checkpoints.getCheckpoints("s1");
        assertEquals(2, all.size());
        assertNull(ToolExecutionPhase.fromWatermark(all.get(1).getWatermark()),
                "terminal checkpoint has no phase segment");
    }

    @Test
    void committedSetNarrowingKeepsOrphanAlarmAlive() {
        // B1 regression: an open call with ONLY a DISPATCHED checkpoint must
        // still be classified as never-committed (orphan alarm state (a)) —
        // phase-tagged checkpoints must not count as committed
        Checkpoint dispatched = Checkpoint.of("s1",
                AgentToolDispatcher.phaseWatermark("s1", "1", 777L, 0, ToolExecutionPhase.DISPATCHED),
                0, 0L, CheckpointType.TOOL_EXECUTION, "bash", "1", null,
                "[phase:DISPATCHED]", 3, 0L);
        List<Checkpoint> checkpoints = List.of(dispatched);

        java.util.Set<String> open = java.util.Set.of("1");
        assertTrue(OpenToolCallResolver.filterNeverCommitted(open, checkpoints).contains("1"),
                "phase-tagged checkpoint must NOT count as committed (B1 narrowing)");
        assertTrue(OpenToolCallResolver.resolveExecutionPhase("1", checkpoints)
                == ToolExecutionPhase.DISPATCHED);
    }

    @Test
    void terminalCheckpointStillCountsAsCommitted() {
        Checkpoint terminal = Checkpoint.of("s1", "s1:tool:1:777:0", 0, 0L,
                CheckpointType.TOOL_EXECUTION, "bash", "1", "{}", "ok", 3, 0L);
        List<Checkpoint> checkpoints = List.of(terminal);
        java.util.Set<String> open = java.util.Set.of("1");
        assertTrue(OpenToolCallResolver.filterNeverCommitted(open, checkpoints).isEmpty(),
                "terminal checkpoint counts as committed (zero regression)");
        assertNull(OpenToolCallResolver.resolveExecutionPhase("1", checkpoints),
                "terminal state resolves to null (no in-flight phase)");
    }

    @Test
    void phaseResolutionRanksExecutingAboveDispatched() {
        Checkpoint dispatched = Checkpoint.of("s1",
                AgentToolDispatcher.phaseWatermark("s1", "1", 777L, 0, ToolExecutionPhase.DISPATCHED),
                0, 0L, CheckpointType.TOOL_EXECUTION, "bash", "1", null,
                "[phase:DISPATCHED]", 3, 0L);
        Checkpoint executing = Checkpoint.of("s1",
                AgentToolDispatcher.phaseWatermark("s1", "1", 777L, 1, ToolExecutionPhase.EXECUTING),
                1, 0L, CheckpointType.TOOL_EXECUTION, "bash", "1", null,
                "[phase:EXECUTING]", 3, 0L);

        assertEquals(ToolExecutionPhase.EXECUTING,
                OpenToolCallResolver.resolveExecutionPhase("1", List.of(dispatched, executing)));
        assertEquals(ToolExecutionPhase.DISPATCHED,
                OpenToolCallResolver.resolveExecutionPhase("1", List.of(dispatched)));
        // terminal beats everything even when phases precede it
        Checkpoint terminal = Checkpoint.of("s1", "s1:tool:1:777:2", 2, 0L,
                CheckpointType.TOOL_EXECUTION, "bash", "1", "{}", "ok", 3, 0L);
        assertNull(OpenToolCallResolver.resolveExecutionPhase("1", List.of(dispatched, executing, terminal)));
    }

    @Test
    void phaseTaggedWatermarkIsDivergenceReclassificationTrigger() {
        // M2 (revised ruling): a phase-tagged latest checkpoint with a
        // missing live key is AMBIGUOUS (wakeup-derived call not yet persisted
        // vs truncated history) — divergence flag is KEPT conservatively and
        // only the alarm message is reclassified as inconclusive
        // (AgentSessionLifecycle.verifyLatestCheckpoint); the phase-tagged
        // watermark is the trigger condition
        Checkpoint dispatched = Checkpoint.of("s1",
                AgentToolDispatcher.phaseWatermark("s1", "derived-1", 777L, 0, ToolExecutionPhase.DISPATCHED),
                0, 0L, CheckpointType.TOOL_EXECUTION, "bash", "derived-1", null,
                "[phase:DISPATCHED]", 3, 0L);
        // the persisted session has no message for derived-1 → liveKey would be null
        assertEquals(ToolExecutionPhase.DISPATCHED,
                ToolExecutionPhase.fromWatermark(dispatched.getWatermark()),
                "phase-tagged watermark is the exemption trigger");
    }

    @Test
    void restoreOrphanAlarmPhaseGradingSplitsExecutingFromDispatched() {
        // WI6 exit criterion: the restore alarm grades orphans by phase —
        // EXECUTING (side effects possible) and DISPATCHED (intent only) are
        // reported as separate rows
        Checkpoint dispatched = Checkpoint.of("s1",
                AgentToolDispatcher.phaseWatermark("s1", "call-d", 777L, 0, ToolExecutionPhase.DISPATCHED),
                0, 0L, CheckpointType.TOOL_EXECUTION, "bash", "call-d", null,
                "[phase:DISPATCHED]", 3, 0L);
        Checkpoint executing = Checkpoint.of("s1",
                AgentToolDispatcher.phaseWatermark("s1", "call-e", 777L, 1, ToolExecutionPhase.EXECUTING),
                1, 0L, CheckpointType.TOOL_EXECUTION, "bash", "call-e", null,
                "[phase:EXECUTING]", 3, 0L);
        List<Checkpoint> checkpoints = List.of(dispatched, executing);
        java.util.Set<String> orphans = java.util.Set.of("call-d", "call-e");

        // both are orphans (never committed — B1 narrowing)
        assertEquals(orphans, OpenToolCallResolver.filterNeverCommitted(orphans, checkpoints));

        // the grade splits them into separate rows
        Map<String, List<String>> graded =
                AgentSessionLifecycle.gradeOrphanPhases(orphans, checkpoints);
        assertEquals(List.of("call-e"), graded.get("EXECUTING"));
        assertEquals(List.of("call-d"), graded.get("DISPATCHED"));
    }

    @Test
    void watermarkUniquenessAcrossExecutionsWithSameSeq() {
        // B2: two executions of the same callId with the same seq — the
        // execStartTime segment must separate the watermarks
        String w1 = AgentToolDispatcher.phaseWatermark("s1", "1", 1000L, 0, ToolExecutionPhase.DISPATCHED);
        String w2 = AgentToolDispatcher.phaseWatermark("s1", "1", 2000L, 0, ToolExecutionPhase.DISPATCHED);
        assertTrue(!w1.equals(w2), "execStartTime segment separates cross-execution watermarks");
        // null session → anon prefix (m6)
        String anon = AgentToolDispatcher.phaseWatermark(null, "1", 1000L, 0, ToolExecutionPhase.DISPATCHED);
        assertTrue(anon.startsWith("anon:tool:"), "anon branch preserved: " + anon);
    }

    private static io.nop.ai.api.chat.messages.ChatToolCall callOf(String id, String name) {
        io.nop.ai.api.chat.messages.ChatToolCall call = new io.nop.ai.api.chat.messages.ChatToolCall();
        call.setId(id);
        call.setName(name);
        call.setArguments(Map.of());
        return call;
    }

    static class CompletedToolManager implements IToolManagerStub {
        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                            io.nop.ai.toolkit.api.IToolExecuteContext context) {
            return CompletableFuture.completedFuture(success());
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
}
