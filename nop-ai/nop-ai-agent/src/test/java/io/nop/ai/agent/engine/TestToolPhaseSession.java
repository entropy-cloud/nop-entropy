package io.nop.ai.agent.engine;

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
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2285 (WI4 Phase 1): the tool wait-phase session — slice-based waiting
 * (COMPLETED / HEARTBEAT_TICK), pending-call addressing, abandon cancellation
 * (commit skips cancelled entries) and per-own-callId anchoring under
 * multi-segment histories produced by wait-phase wakeups.
 */
public class TestToolPhaseSession {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void cleanup() {
        CoreInitialization.destroy();
    }

    static class DelayedToolManager implements IToolManager {
        final Map<String, CompletableFuture<AiToolCallResult>> futuresByToolName;

        DelayedToolManager(Map<String, CompletableFuture<AiToolCallResult>> futuresByToolName) {
            this.futuresByToolName = futuresByToolName;
        }

        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                            io.nop.ai.toolkit.api.IToolExecuteContext context) {
            CompletableFuture<AiToolCallResult> future = futuresByToolName.get(toolName);
            if (future != null) {
                return future;
            }
            AiToolCallResult r = new AiToolCallResult();
            r.setStatus("success");
            return CompletableFuture.completedFuture(r);
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

    private static AgentToolDispatcher dispatcher(IToolManager tm) {
        AgentHookInvoker hookInvoker = new AgentHookInvoker(new DefaultHookRegistry(), null);
        return new AgentToolDispatcher(tm, null, null, null, null, null, null,
                0, null, NoOpCheckpoint.noOp(), hookInvoker,
                new AgentToolPlanResolver(tm),
                new AgentSecurityConsultation(
                        new DefaultPostDenialGuard(),
                        new Slf4jAuditLogger(),
                        new DefaultToolAccessChecker(),
                        new AllowAllPermissionProvider(),
                        new DefaultPathAccessChecker(),
                        new DefaultSecurityLevelResolver(),
                        new DefaultPermissionMatrix(),
                        new DefaultApprovalGate(),
                        new DefaultDenialLedger(),
                        FailFastStrategy.failFast(),
                        new InMemoryWriteIntentRegistry(),
                        new AgentToolPlanResolver(tm),
                        hookInvoker));
    }

    private static AiToolCallResult success() {
        AiToolCallResult r = new AiToolCallResult();
        r.setStatus("success");
        return r;
    }

    @Test
    void completedFuturesReturnImmediately() {
        CompletableFuture<AiToolCallResult> done = CompletableFuture.completedFuture(success());
        AgentToolDispatcher dispatcher = dispatcher(new DelayedToolManager(Map.of("read-file", done)));
        AgentExecutionContext c = ctx();
        c.addMessage(new ChatToolCallMessage("1", "read-file", Map.of()));

        ToolPhaseSession session = dispatcher.startToolPhase(c, "agent", "s1",
                List.of(callOf("1", "read-file")), null, 0L, new int[]{0});

        assertTrue(session.awaitCompletionSlice(50));
        assertEquals(List.of(), session.pendingCallIds());
        session.commitResults();
        assertTrue(c.getMessages().stream().anyMatch(m -> m instanceof ChatToolResponseMessage));
    }

    @Test
    void sliceExpiryReturnsHeartbeatTick() {
        CompletableFuture<AiToolCallResult> never = new CompletableFuture<>();
        AgentToolDispatcher dispatcher = dispatcher(new DelayedToolManager(Map.of("bash", never)));
        AgentExecutionContext c = ctx();
        c.addMessage(new ChatToolCallMessage("1", "bash", Map.of()));

        ToolPhaseSession session = dispatcher.startToolPhase(c, "agent", "s1",
                List.of(callOf("1", "bash")), null, 0L, new int[]{0});

        boolean completed = session.awaitCompletionSlice(50);
        assertFalse(completed, "slice expiry must surface the heartbeat wakeup point");
        assertEquals(List.of("1"), session.pendingCallIds());
        // heartbeat evidence: elapsedMs is tracked per call
        assertTrue(session.elapsedMs("1") >= 0);
        never.complete(success());
    }

    @Test
    void abandonCancelsAndCommitSkipsCancelled() {
        CompletableFuture<AiToolCallResult> never = new CompletableFuture<>();
        CompletableFuture<AiToolCallResult> done = CompletableFuture.completedFuture(success());
        AgentToolDispatcher dispatcher = dispatcher(new DelayedToolManager(Map.of("bash", never, "read-file", done)));
        AgentExecutionContext c = ctx();
        c.addMessage(new ChatUserMessage("run"));
        c.addMessage(new ChatAssistantMessage(""));
        c.addMessage(new ChatToolCallMessage("1", "bash", Map.of()));
        c.addMessage(new ChatToolCallMessage("2", "read-file", Map.of()));

        ToolPhaseSession session = dispatcher.startToolPhase(c, "agent", "s1",
                List.of(callOf("1", "bash"), callOf("2", "read-file")), null, 0L, new int[]{0});

        assertTrue(session.cancelCall("1"));
        assertFalse(session.cancelCall("1"), "second abandon is a no-op");
        assertFalse(session.cancelCall("999"), "unknown callId is not abandonable");
        assertTrue(session.awaitCompletionSlice(1000), "remaining future completes");
        session.commitResults();

        List<String> tokens = new ArrayList<>();
        for (ChatMessage m : c.getMessages()) {
            if (m instanceof ChatToolResponseMessage) {
                tokens.add("result:" + ((ChatToolResponseMessage) m).getCallId());
            }
        }
        // the abandoned call's synthetic result is the executor's adjudication
        // duty (Phase 3); the session commit emits only the completed call
        assertEquals(List.of("result:2"), tokens);
        never.complete(success());
    }

    @Test
    void deadlineSlicesRespectPolicyCap() {
        AgentHookInvoker hookInvoker = new AgentHookInvoker(new DefaultHookRegistry(), null);
        StubLikeToolManager tm = new StubLikeToolManager();
        AgentToolDispatcher dispatcher = new AgentToolDispatcher(tm, null, null, null, null,
                null, null, 60_000L, null, NoOpCheckpoint.noOp(), hookInvoker,
                new AgentToolPlanResolver(tm),
                new AgentSecurityConsultation(
                        new DefaultPostDenialGuard(), new Slf4jAuditLogger(),
                        new DefaultToolAccessChecker(), new AllowAllPermissionProvider(),
                        new DefaultPathAccessChecker(), new DefaultSecurityLevelResolver(),
                        new DefaultPermissionMatrix(), new DefaultApprovalGate(),
                        new DefaultDenialLedger(), FailFastStrategy.failFast(),
                        new InMemoryWriteIntentRegistry(), new AgentToolPlanResolver(tm),
                        hookInvoker),
                Map.of("bash", 3_600_000L, "*", 1_200_000L, "build", 7_200_000L));

        assertEquals(3_600_000L, dispatcher.resolveToolTimeoutMs("bash"));
        assertEquals(7_200_000L, dispatcher.resolveToolTimeoutMs("build"));
        assertEquals(1_200_000L, dispatcher.resolveToolTimeoutMs("read-file"));

        // precedence pin (design §三.5): exact name → any tool tag → wildcard —
        // a tag override must not be shadowed by the "*" global cap
        AgentHookInvoker tagHookInvoker = new AgentHookInvoker(new DefaultHookRegistry(), null);
        TagCarryingToolManager tagTm = new TagCarryingToolManager(Map.of("tagged-tool", java.util.Set.of("build")));
        AgentToolDispatcher withTags = new AgentToolDispatcher(tagTm, null, null, null, null,
                null, null, 60_000L, null, NoOpCheckpoint.noOp(), tagHookInvoker,
                new AgentToolPlanResolver(tagTm),
                new AgentSecurityConsultation(
                        new DefaultPostDenialGuard(), new Slf4jAuditLogger(),
                        new DefaultToolAccessChecker(), new AllowAllPermissionProvider(),
                        new DefaultPathAccessChecker(), new DefaultSecurityLevelResolver(),
                        new DefaultPermissionMatrix(), new DefaultApprovalGate(),
                        new DefaultDenialLedger(), FailFastStrategy.failFast(),
                        new InMemoryWriteIntentRegistry(), new AgentToolPlanResolver(tagTm),
                        tagHookInvoker),
                Map.of("bash", 3_600_000L, "*", 1_200_000L, "build", 7_200_000L));
        assertEquals(3_600_000L, withTags.resolveToolTimeoutMs("bash"));
        assertEquals(7_200_000L, withTags.resolveToolTimeoutMs("tagged-tool"),
                "tag override must win over the wildcard global cap");
        assertEquals(1_200_000L, withTags.resolveToolTimeoutMs("untagged"),
                "untagged tool falls to the wildcard cap");
        // empty policy falls back to the plain toolTimeoutMs default
        AgentHookInvoker plainHookInvoker = new AgentHookInvoker(new DefaultHookRegistry(), null);
        StubLikeToolManager plainTm = new StubLikeToolManager();
        AgentToolDispatcher plainDispatcher = new AgentToolDispatcher(plainTm, null, null, null, null,
                null, null, 30_000L, null, NoOpCheckpoint.noOp(), hookInvoker,
                new AgentToolPlanResolver(tm),
                new AgentSecurityConsultation(
                        new DefaultPostDenialGuard(), new Slf4jAuditLogger(),
                        new DefaultToolAccessChecker(), new AllowAllPermissionProvider(),
                        new DefaultPathAccessChecker(), new DefaultSecurityLevelResolver(),
                        new DefaultPermissionMatrix(), new DefaultApprovalGate(),
                        new DefaultDenialLedger(), FailFastStrategy.failFast(),
                        new InMemoryWriteIntentRegistry(), new AgentToolPlanResolver(plainTm),
                        plainHookInvoker));
        assertEquals(30_000L, plainDispatcher.resolveToolTimeoutMs("bash"));
    }

    /** Tool model stub carrying tags for cap-precedence resolution tests. */
    static class TagCarryingToolManager extends StubLikeToolManager {
        private final Map<String, java.util.Set<String>> tagsByToolName;

        TagCarryingToolManager(Map<String, java.util.Set<String>> tagsByToolName) {
            this.tagsByToolName = tagsByToolName;
        }

        @Override
        public io.nop.ai.toolkit.model.AiToolModel loadTool(String toolName) {
            java.util.Set<String> tags = tagsByToolName.get(toolName);
            if (tags == null) {
                return null;
            }
            io.nop.ai.toolkit.model.AiToolModel model = new io.nop.ai.toolkit.model.AiToolModel();
            model.setTags(tags);
            return model;
        }
    }

    static class StubLikeToolManager implements IToolManager {
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

    @Test
    void statusUnchangedBySessionLifecycle() {
        CompletableFuture<AiToolCallResult> done = CompletableFuture.completedFuture(success());
        AgentToolDispatcher dispatcher = dispatcher(new DelayedToolManager(Map.of("read-file", done)));
        AgentExecutionContext c = ctx();
        c.addMessage(new ChatToolCallMessage("1", "read-file", Map.of()));

        ToolPhaseSession session = dispatcher.startToolPhase(c, "agent", "s1",
                List.of(callOf("1", "read-file")), null, 0L, new int[]{0});
        session.awaitCompletionSlice(1000);
        session.commitResults();

        assertEquals(AgentExecStatus.running, c.getStatus());
    }
}
