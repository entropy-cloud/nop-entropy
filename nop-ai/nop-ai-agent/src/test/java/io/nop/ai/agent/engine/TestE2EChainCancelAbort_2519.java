package io.nop.ai.agent.engine;

import io.nop.ai.agent.NopAiAgentException;
import io.nop.ai.agent.conflict.FailFastStrategy;
import io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry;
import io.nop.ai.agent.engine.AgentToolDispatcher.ToolPhaseSession;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.runtime.lock.ISessionTakeoverLock;
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

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2519 Phase 2 rule 22 chain two, segment 2 (cancel / abort chain,
 * 裁定⑦⑩) — composite end-to-end scenario (package-private handle surface
 * keeps this segment in the engine package; segment 1 = the dialect/DB
 * governance chain in {@code io.nop.ai.agent.message}.
 * TestE2EChainDeliveryGovernance_2519):
 *
 * <ol>
 *   <li>裁定⑦: the mechanical timeout / interrupt path of a tool wait phase
 *       reaches the process-level kill channel — the wired tracker really
 *       observes cancelExecution for every pending call (zero-接入现状
 *       reversal; the underlying process does NOT survive as a ghost).</li>
 *   <li>裁定⑩: a root-cause SQLException renewal failure escalates to the
 *       REAL handleLeaseLost abort callback (rule 23⑩) — pre-fix the failure
 *       was WARNed forever and abort never fired.</li>
 * </ol>
 */
public class TestE2EChainCancelAbort_2519 {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ------------------------------------------------------------------
    // 裁定⑦: kill channel really consumed by the cancel path
    // ------------------------------------------------------------------

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
        AgentHookInvoker hookInvoker = new AgentHookInvoker(new DefaultHookRegistry(), null);
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

    @Test
    void chain2_killChannelReachedByCancelPath() {
        RecordingTracker tracker = new RecordingTracker();
        AgentToolDispatcher dispatcher = dispatcher(new NeverToolManager(), tracker);

        AgentExecutionContext c = new AgentExecutionContext(new AgentModel());
        c.setStatus(AgentExecStatus.running);
        ChatToolCall call = new ChatToolCall();
        call.setId("call-e2e");
        call.setName("bash");
        call.setArguments(Map.of());
        c.addMessage(new ChatToolCallMessage("call-e2e", "bash", Map.of()));

        ToolPhaseSession session = dispatcher.startToolPhase(c, "agent", "e2e-chain2",
                List.of(call), null, 0L, new int[]{0});

        assertTrue(session.cancelCall("call-e2e"), "the pending call is abandonable");
        assertEquals(List.of("call-e2e"), List.copyOf(tracker.killedCallIds),
                "裁定⑦/rule 23: the cancel path really consumes the kill channel "
                        + "(tracker.cancelExecution observed with the addressed callId)");
    }

    // ------------------------------------------------------------------
    // 裁定⑩: renewal SQLException escalates to the real handleLeaseLost abort
    // ------------------------------------------------------------------

    @Test
    void chain2_renewalSqlFailureEscalatesToRealLeaseLostAbort() throws Exception {
        DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        config.setLockRenewIntervalMs(20);
        ConcurrentHashMap<String, AgentSessionLifecycle.CancelHandle> running =
                new ConcurrentHashMap<>();
        AtomicInteger leaseLostCount = new AtomicInteger();
        SessionLockRenewal renewal = new SessionLockRenewal(config, running) {
            @Override
            public void handleLeaseLost(AgentSessionLifecycle.CancelHandle handle, String sessionId, String ownerId) {
                leaseLostCount.incrementAndGet();
            }
        };
        ISessionTakeoverLock lock = new ISessionTakeoverLock() {
            @Override
            public boolean tryAcquire(String sessionId, String ownerId, long leaseMs) {
                return true;
            }

            @Override
            public boolean tryRenew(String sessionId, String ownerId, long leaseMs) {
                // the takeover lock wraps every SQLException in a runtime
                // exception — root-cause classification must see through it
                throw new NopAiAgentException("renew failed", new SQLException("connection refused"));
            }

            @Override
            public boolean release(String sessionId, String ownerId) {
                return true;
            }

            @Override
            public boolean isHeld(String sessionId) {
                return false;
            }
        };
        config.setSessionTakeoverLock(lock);
        AgentSessionLifecycle.CancelHandle handle =
                new AgentSessionLifecycle.CancelHandle(null, null);
        running.put("e2e-chain2", handle);
        handle.renewHandle = renewal.startLockRenewal(handle, "e2e-chain2", "owner-1");
        try {
            long deadline = System.currentTimeMillis() + 10_000;
            while (leaseLostCount.get() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(leaseLostCount.get() >= 1,
                    "裁定⑩/rule 23: a root-cause SQLException renewal failure escalates to the real "
                            + "handleLeaseLost abort (pre-fix defect: WARN forever, abort never fired)");
        } finally {
            SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
        }
    }
}
