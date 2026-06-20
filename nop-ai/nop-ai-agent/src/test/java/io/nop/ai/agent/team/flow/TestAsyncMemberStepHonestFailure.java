package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMember;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 241 (L4-async-cross-process-orchestration, async half) focused tests
 * for honest-failure semantics in the async member step path.
 *
 * <p>Verifies that every failure mode of {@link MemberAgentTaskStep} (now
 * async) preserves the pre-241 honest-failure contract line-for-line (No
 * Silent No-Op #24):
 * <ul>
 *   <li>claim failure (task missing / already claimed) → honest failed
 *       result, task stays in the original / CLAIMED state.</li>
 *   <li>member agent exception → honest failed result, task left CLAIMED
 *       (NOT auto-abandoned — daemon's abandon is a different recovery
 *       model).</li>
 *   <li>member agent non-completed status (failed/cancelled/...) → honest
 *       failed result, task left CLAIMED.</li>
 *   <li>{@code completeTask} CAS loss → honest failed result, task state
 *       reflects the concurrent transition.</li>
 *   <li>already-COMPLETED task (idempotent re-run) → honest SUCCESS (no
 *       engine invocation) — this is NOT a silent skip, it's an explicit
 *       "already done" success.</li>
 * </ul>
 */
public class TestAsyncMemberStepHonestFailure {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static Team createTeamWithBoundMember(InMemoryTeamManager mgr,
                                                  String memberName, String sessionId) {
        TeamSpec spec = new TeamSpec("honest-fail-team", "test", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec(memberName, memberName + "-agent", MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), memberName, sessionId, "actor-" + memberName);
        return team;
    }

    private static String createTask(InMemoryTeamTaskStore store, String teamId,
                                     String subject) {
        return store.createTask(teamId, subject, "desc-" + subject, Collections.emptyList(),
                "lead-session").getTaskId();
    }

    /**
     * Configurable engine that fails a specific task either by exception
     * or by non-completed status, and counts how many times execute was
     * invoked (used to assert already-COMPLETED idempotent path doesn't
     * invoke the engine).
     */
    static final class ConfigurableAgentEngine implements IAgentEngine {
        final AtomicInteger executeCount = new AtomicInteger();
        final String failOnTaskId;
        final boolean failByException;
        final AgentExecStatus failStatus;

        ConfigurableAgentEngine() {
            this(null, false, null);
        }

        ConfigurableAgentEngine(String failOnTaskId, boolean failByException, AgentExecStatus failStatus) {
            this.failOnTaskId = failOnTaskId;
            this.failByException = failByException;
            this.failStatus = failStatus;
        }

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            executeCount.incrementAndGet();
            String taskId = (String) request.getMetadata().get("teamTaskId");

            if (failOnTaskId != null && failOnTaskId.equals(taskId)) {
                if (failByException) {
                    CompletableFuture<AgentExecutionResult> f = new CompletableFuture<>();
                    f.completeExceptionally(new RuntimeException("boom:" + taskId));
                    return f;
                }
                return CompletableFuture.completedFuture(
                        new AgentExecutionResult(failStatus, null,
                                Collections.emptyList(), 0, 0L, 0L, "status:" + failStatus));
            }
            return CompletableFuture.completedFuture(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok:" + taskId,
                            Collections.emptyList(), 1, 10L, 1L, null));
        }
    }

    // ========================================================================
    // 1. Member agent exception → honest failed result, task left CLAIMED.
    // ========================================================================

    @Test
    void memberAgentExceptionHonestFailLeavesClaimed() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String taskId = createTask(store, team.getTeamId(), "A");

        ConfigurableAgentEngine engine = new ConfigurableAgentEngine(taskId, true, null);
        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(engine, store, mgr);

        TeamTaskFlowResult result = orchestrator.executeAsync(team.getTeamId())
                .get(5, TimeUnit.SECONDS);

        assertFalse(result.isSuccess(),
                "engine exception must honestly fail (not silent success): " + result);
        assertEquals(taskId, result.getFailedTaskId());
        // Task was claimed then engine threw — must remain CLAIMED, NOT abandoned.
        TeamTask finalState = store.getTask(taskId).orElseThrow();
        assertEquals(TeamTaskStatus.CLAIMED, finalState.getStatus(),
                "task left CLAIMED (not abandoned) — orchestrator's failure model: " + finalState);
        assertNotEquals(TeamTaskStatus.ABANDONED, finalState.getStatus(),
                "task NOT abandoned (abandon is daemon's recovery model, not orchestrator's)");
    }

    // ========================================================================
    // 2. Member agent non-completed status → honest failed result, CLAIMED.
    // ========================================================================

    @Test
    void memberAgentNonCompletedStatusHonestFailLeavesClaimed() throws Exception {
        for (AgentExecStatus nonCompleted : new AgentExecStatus[]{
                AgentExecStatus.failed, AgentExecStatus.cancelled, AgentExecStatus.paused}) {
            InMemoryTeamManager mgr = new InMemoryTeamManager();
            InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
            Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
            String taskId = createTask(store, team.getTeamId(), "A");

            ConfigurableAgentEngine engine = new ConfigurableAgentEngine(taskId, false, nonCompleted);
            TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(engine, store, mgr);

            TeamTaskFlowResult result = orchestrator.executeAsync(team.getTeamId())
                    .get(5, TimeUnit.SECONDS);

            assertFalse(result.isSuccess(),
                    "non-completed status " + nonCompleted + " must honestly fail: " + result);
            assertEquals(taskId, result.getFailedTaskId());
            assertEquals(TeamTaskStatus.CLAIMED, store.getTask(taskId).orElseThrow().getStatus(),
                    "task left CLAIMED for status=" + nonCompleted);
        }
    }

    // ========================================================================
    // 3. Already-COMPLETED task → honest SUCCESS (idempotent re-run).
    // ========================================================================

    @Test
    void alreadyCompletedTaskIsIdempotentSuccess() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String taskId = createTask(store, team.getTeamId(), "A");

        // Pre-complete the task: claim + complete (so it's COMPLETED before
        // the orchestrator runs).
        Long epoch = store.claimTask(taskId, "pre-completer").orElseThrow().getClaimEpoch();
        store.completeTask(taskId, "pre-completer", epoch);
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(taskId).orElseThrow().getStatus());

        ConfigurableAgentEngine engine = new ConfigurableAgentEngine(); // doesn't fail
        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(engine, store, mgr);

        TeamTaskFlowResult result = orchestrator.executeAsync(team.getTeamId())
                .get(5, TimeUnit.SECONDS);

        assertTrue(result.isSuccess(),
                "already-COMPLETED task must honestly succeed (idempotent re-run): " + result);
        // No Silent No-Op #24: idempotent success is EXPLICIT, not silent skip.
        // The task is reported as completed.
        assertTrue(result.getCompletedTaskIds().contains(taskId),
                "task appears in completedTaskIds (explicit success, not silent skip)");
        assertEquals(0, engine.executeCount.get(),
                "engine NOT invoked on already-COMPLETED task (idempotent shortcut)");
    }

    // ========================================================================
    // 4. claimTask CAS loss (task pre-claimed by another session) → honest fail.
    // ========================================================================

    @Test
    void claimCasLossHonestFail() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String taskId = createTask(store, team.getTeamId(), "A");

        // Pre-claim by ANOTHER session (not the bound member's session).
        // The bound member session is "worker-session". Have someone else
        // claim first, so the orchestrator's claim loses the CAS.
        store.claimTask(taskId, "another-session");
        assertEquals(TeamTaskStatus.CLAIMED, store.getTask(taskId).orElseThrow().getStatus());

        ConfigurableAgentEngine engine = new ConfigurableAgentEngine();
        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(engine, store, mgr);

        TeamTaskFlowResult result = orchestrator.executeAsync(team.getTeamId())
                .get(5, TimeUnit.SECONDS);

        // The orchestrator's bound-member claim fails CAS (another session
        // already has it). The orchestrator must honestly report failure
        // (not silently succeed, not silently skip).
        assertFalse(result.isSuccess(),
                "claim CAS loss must honestly fail (not silent success): " + result);
        assertEquals(taskId, result.getFailedTaskId(),
                "claim-failed task is reported as the failed task");
        // Engine NOT invoked (claim happened before engine call).
        assertEquals(0, engine.executeCount.get(),
                "engine NOT invoked — claim failed before engine call");
        // Task stays CLAIMED by the other session (not auto-completed by us).
        assertEquals(TeamTaskStatus.CLAIMED, store.getTask(taskId).orElseThrow().getStatus());
        assertEquals("another-session", store.getTask(taskId).orElseThrow().getClaimedBy(),
                "task still claimed by the original other session (we did not steal it)");
    }

    // ========================================================================
    // 5. completeTask CAS loss → honest fail (task state already advanced).
    // ========================================================================

    @Test
    void completeCasLossHonestFail() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String taskId = createTask(store, team.getTeamId(), "A");

        // Wrap the store so the orchestrator's claim succeeds (CREATED→CLAIMED),
        // but a "ghost" concurrent actor completes the task BEFORE the
        // orchestrator's completeTask runs. The orchestrator's CAS fails.
        GhostCompletingStore ghostStore = new GhostCompletingStore(store, taskId, "worker-session");
        ConfigurableAgentEngine engine = new ConfigurableAgentEngine();
        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(engine, ghostStore, mgr);

        TeamTaskFlowResult result = orchestrator.executeAsync(team.getTeamId())
                .get(5, TimeUnit.SECONDS);

        // The orchestrator's bound-member claim succeeded, then engine ran
        // and returned completed, then orchestrator's completeTask CAS lost
        // (ghost had already completed it). Honest failure.
        assertFalse(result.isSuccess(),
                "completeTask CAS loss must honestly fail: " + result);
        assertEquals(taskId, result.getFailedTaskId());
    }

    /**
     * Store wrapper that delegates to a real InMemoryTeamTaskStore but,
     * when the orchestrator calls {@code completeTask} for the watched task,
     * first has a "ghost" (another session using the SAME claim) sneak in
     * and complete the task. The orchestrator's subsequent completeTask CAS
     * then loses. This simulates a concurrent actor completing the task
     * between the orchestrator's claim and its complete.
     */
    static final class GhostCompletingStore implements io.nop.ai.agent.team.ITeamTaskStore {
        private final InMemoryTeamTaskStore delegate;
        private final String watchedTaskId;
        private final String ghostSessionId;
        private final AtomicInteger ghostCompleted = new AtomicInteger(0);

        GhostCompletingStore(InMemoryTeamTaskStore delegate, String taskId, String ghostSessionId) {
            this.delegate = delegate;
            this.watchedTaskId = taskId;
            this.ghostSessionId = ghostSessionId;
        }

        @Override
        public TeamTask createTask(String t, String s, String d, List<String> b, String c) {
            return delegate.createTask(t, s, d, b, c);
        }

        @Override
        public Optional<TeamTask> getTask(String taskId) {
            return delegate.getTask(taskId);
        }

        @Override
        public List<TeamTask> getTasksByTeam(String tid) {
            return delegate.getTasksByTeam(tid);
        }

        @Override
        public List<TeamTask> getTasksByCreator(String c) {
            return delegate.getTasksByCreator(c);
        }

        @Override
        public Optional<TeamTask> claimTask(String t, String b) {
            return delegate.claimTask(t, b);
        }

        @Override
        public Optional<TeamTask> completeTask(String t, String b, Long claimEpoch) {
            // Sneak in: have the ghost complete the task first (using the
            // same session, as if a concurrent dispatcher won the race).
            // The CAS conditions are the same so exactly one of the two
            // completeTask calls will succeed; the orchestrator's call (the
            // second one) loses.
            if (watchedTaskId.equals(t) && ghostCompleted.compareAndSet(0, 1)) {
                delegate.completeTask(t, ghostSessionId, claimEpoch);
            }
            return delegate.completeTask(t, b, claimEpoch);
        }

        @Override
        public Optional<TeamTask> abandonTask(String t, String b, Long claimEpoch) {
            return delegate.abandonTask(t, b, claimEpoch);
        }

        @Override
        public Optional<TeamTask> reclaimTask(String t, String b) {
            return delegate.reclaimTask(t, b);
        }
    }
}
