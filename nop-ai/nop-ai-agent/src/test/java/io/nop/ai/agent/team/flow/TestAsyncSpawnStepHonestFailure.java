package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.ai.agent.team.scheduler.SpawnMemberRequest;
import io.nop.ai.agent.team.scheduler.SpawnMemberResult;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 243 (L4-spawn-step-async) focused tests for honest-failure semantics
 * in the async spawn step path.
 *
 * <p>Verifies that every failure mode of {@link SpawnMemberAgentTaskStep}
 * (now async) preserves the pre-243 honest-failure contract line-for-line
 * (No Silent No-Op #24), with the task left in CLAIMED (not abandoned):
 * <ul>
 *   <li>{@link #noSpawnHonestFailLeavesClaimed} — NO_SPAWN (NoOp shipped
 *       default) → honest failed result, task left CLAIMED.</li>
 *   <li>{@link #spawnFailedHonestFailLeavesClaimed} — SPAWN_FAILED → honest
 *       failed result, task left CLAIMED.</li>
 *   <li>{@link #dispatchedNonCompletedHonestFailLeavesClaimed} — DISPATCHED
 *       but non-completed → honest failed result, task left CLAIMED.</li>
 *   <li>{@link #spawnerThrowsHonestFailLeavesClaimed} — spawner throws
 *       (contract violation) → honest failed result, task left CLAIMED.</li>
 *   <li>{@link #spawnerReturnsNullHonestFailLeavesClaimed} — spawner returns
 *       null (defensive) → honest failed result, task left CLAIMED.</li>
 *   <li>{@link #completeCasLossHonestFail} — completeTask CAS loss → honest
 *       failed result.</li>
 *   <li>{@link #alreadyCompletedTaskIsIdempotentSuccess} — already-COMPLETED
 *       task → honest SUCCESS (idempotent re-run, explicit not silent skip).</li>
 * </ul>
 *
 * <p>Wiring verification (#23): each test confirms the async spawn path
 * actually executed (task state machine advanced CREATED → CLAIMED) and that
 * the failure was reported, never silently swallowed.
 */
public class TestAsyncSpawnStepHonestFailure {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Configurable stub spawner that returns a fixed {@link SpawnMemberResult}
     * for every call (drives the NO_SPAWN / SPAWN_FAILED / DISPATCHED-non-
     * completed failure paths without a real engine).
     */
    static final class StubSpawner implements IMemberSpawner {
        final SpawnMemberResult fixedResult;
        final RuntimeException throwIfSet;
        final boolean returnNull;

        StubSpawner(SpawnMemberResult fixedResult) {
            this(fixedResult, null, false);
        }

        StubSpawner(SpawnMemberResult fixedResult, RuntimeException throwIfSet) {
            this(fixedResult, throwIfSet, false);
        }

        StubSpawner(boolean returnNull) {
            this(null, null, true);
        }

        private StubSpawner(SpawnMemberResult fixedResult, RuntimeException throwIfSet, boolean returnNull) {
            this.fixedResult = fixedResult;
            this.throwIfSet = throwIfSet;
            this.returnNull = returnNull;
        }

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            if (throwIfSet != null) {
                throw throwIfSet;
            }
            if (returnNull) {
                return null;
            }
            return fixedResult;
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static Team createTeamWithoutBoundMember(InMemoryTeamManager mgr) {
        TeamSpec spec = new TeamSpec("spawn-honest-team", "test", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("worker", "worker-agent-model", MemberRole.MEMBER)),
                0);
        return mgr.createTeam(spec);
    }

    private static String createTask(InMemoryTeamTaskStore store, String teamId, String subject) {
        return store.createTask(teamId, subject, "desc-" + subject, Collections.emptyList(),
                "lead-session").getTaskId();
    }

    private static TeamTaskFlowResult runAsync(InMemoryTeamManager mgr, InMemoryTeamTaskStore store,
                                                IMemberSpawner spawner) throws Exception {
        Team team = createTeamWithoutBoundMember(mgr);
        String teamId = team.getTeamId();
        String taskId = createTask(store, teamId, "A");
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(10, TimeUnit.SECONDS);
            return result;
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 1. NO_SPAWN → honest fail, task left CLAIMED.
    // ========================================================================

    @Test
    void noSpawnHonestFailLeavesClaimed() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithoutBoundMember(mgr);
        String teamId = team.getTeamId();
        String taskId = createTask(store, teamId, "A");

        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(
                null, store, mgr, null, io.nop.ai.agent.team.NoOpMemberSpawner.noOp());
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(10, TimeUnit.SECONDS);

            assertFalse(result.isSuccess(),
                    "NoOp spawner must honestly fail, not silently succeed: " + result);
            assertEquals(taskId, result.getFailedTaskId());
            assertEquals(TeamTaskStatus.CLAIMED, store.getTask(taskId).orElseThrow().getStatus(),
                    "failed unbound task left CLAIMED (not abandoned)");
            assertNotEquals(TeamTaskStatus.ABANDONED, store.getTask(taskId).orElseThrow().getStatus(),
                    "orchestrator does NOT abandon (that is the daemon's model)");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 2. SPAWN_FAILED → honest fail, task left CLAIMED.
    // ========================================================================

    @Test
    void spawnFailedHonestFailLeavesClaimed() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithoutBoundMember(mgr);
        String teamId = team.getTeamId();
        String taskId = createTask(store, teamId, "A");

        StubSpawner spawner = new StubSpawner(SpawnMemberResult.spawnFailed("spawned agent threw: boom"));
        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(10, TimeUnit.SECONDS);

            assertFalse(result.isSuccess(), "SPAWN_FAILED must honestly fail: " + result);
            assertEquals(taskId, result.getFailedTaskId());
            assertEquals(TeamTaskStatus.CLAIMED, store.getTask(taskId).orElseThrow().getStatus(),
                    "failed task left CLAIMED (not abandoned)");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 3. DISPATCHED but non-completed → honest fail, task left CLAIMED.
    // ========================================================================

    @Test
    void dispatchedNonCompletedHonestFailLeavesClaimed() throws Exception {
        for (AgentExecStatus nonCompleted : new AgentExecStatus[]{
                AgentExecStatus.failed, AgentExecStatus.cancelled, AgentExecStatus.paused}) {
            InMemoryTeamManager mgr = new InMemoryTeamManager();
            InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
            Team team = createTeamWithoutBoundMember(mgr);
            String teamId = team.getTeamId();
            String taskId = createTask(store, teamId, "A");

            AgentExecutionResult failedResult = new AgentExecutionResult(
                    nonCompleted, null, Collections.emptyList(), 0, 0L, 0L, "status:" + nonCompleted);
            StubSpawner spawner = new StubSpawner(
                    SpawnMemberResult.dispatched(failedResult, "worker-agent-model", "spawned-x"));
            TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner);
            try {
                TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(10, TimeUnit.SECONDS);

                assertFalse(result.isSuccess(),
                        "DISPATCHED non-completed " + nonCompleted + " must honestly fail: " + result);
                assertEquals(taskId, result.getFailedTaskId());
                assertEquals(TeamTaskStatus.CLAIMED, store.getTask(taskId).orElseThrow().getStatus(),
                        "task left CLAIMED for status=" + nonCompleted);
            } finally {
                orchestrator.close();
            }
        }
    }

    // ========================================================================
    // 4. Spawner THROWS (contract violation) → honest fail, task left CLAIMED.
    // ========================================================================

    @Test
    void spawnerThrowsHonestFailLeavesClaimed() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithoutBoundMember(mgr);
        String teamId = team.getTeamId();
        String taskId = createTask(store, teamId, "A");

        StubSpawner spawner = new StubSpawner(null, new RuntimeException("spawner-contract-violation"));
        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(10, TimeUnit.SECONDS);

            assertFalse(result.isSuccess(),
                    "spawner throwing (contract violation) must honestly fail, not be swallowed: " + result);
            assertEquals(taskId, result.getFailedTaskId());
            assertEquals(TeamTaskStatus.CLAIMED, store.getTask(taskId).orElseThrow().getStatus(),
                    "failed task left CLAIMED (not abandoned)");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 5. Spawner returns NULL (defensive) → honest fail, task left CLAIMED.
    // ========================================================================

    @Test
    void spawnerReturnsNullHonestFailLeavesClaimed() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithoutBoundMember(mgr);
        String teamId = team.getTeamId();
        String taskId = createTask(store, teamId, "A");

        StubSpawner spawner = new StubSpawner(true);
        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(10, TimeUnit.SECONDS);

            assertFalse(result.isSuccess(),
                    "spawner returning null must honestly fail (defensive, not NPE): " + result);
            assertEquals(taskId, result.getFailedTaskId());
            assertEquals(TeamTaskStatus.CLAIMED, store.getTask(taskId).orElseThrow().getStatus(),
                    "failed task left CLAIMED (not abandoned)");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 6. completeTask CAS loss → honest fail (task state already advanced).
    // ========================================================================

    @Test
    void completeCasLossHonestFail() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithoutBoundMember(mgr);
        String teamId = team.getTeamId();
        String taskId = createTask(store, teamId, "A");

        // Wrap the store so the step's claim succeeds (CREATED→CLAIMED), but
        // a "ghost" concurrent actor completes the task BEFORE the step's
        // completeTask runs. The step's CAS then loses.
        GhostCompletingStore ghostStore = new GhostCompletingStore(store, taskId,
                "orchestrator-" + teamId);
        // A spawner that returns a completed DISPATCHED result (so the step
        // reaches the completeTask stage).
        StubSpawner spawner = new StubSpawner(SpawnMemberResult.dispatched(
                new AgentExecutionResult(AgentExecStatus.completed, "ok", Collections.emptyList(),
                        1, 10L, 1L, null),
                "worker-agent-model", "spawned-x"));
        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(null, ghostStore, mgr, null, spawner);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(10, TimeUnit.SECONDS);

            assertFalse(result.isSuccess(),
                    "completeTask CAS loss must honestly fail: " + result);
            assertEquals(taskId, result.getFailedTaskId());
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 7. Already-COMPLETED task → honest SUCCESS (idempotent re-run).
    // ========================================================================

    @Test
    void alreadyCompletedTaskIsIdempotentSuccess() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithoutBoundMember(mgr);
        String teamId = team.getTeamId();
        String taskId = createTask(store, teamId, "A");

        // Pre-complete the task so it is COMPLETED before the orchestrator runs.
        store.claimTask(taskId, "pre-completer");
        store.completeTask(taskId, "pre-completer");
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(taskId).orElseThrow().getStatus());

        // A spawner that would fail if invoked — proves the idempotent path
        // short-circuits BEFORE spawnMember is called.
        StubSpawner spawner = new StubSpawner(null, new RuntimeException("must-not-be-invoked"));
        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(10, TimeUnit.SECONDS);

            assertTrue(result.isSuccess(),
                    "already-COMPLETED task must honestly succeed (idempotent re-run): " + result);
            // No Silent No-Op #24: idempotent success is EXPLICIT, not silent skip.
            assertTrue(result.getCompletedTaskIds().contains(taskId),
                    "task appears in completedTaskIds (explicit success, not silent skip)");
        } finally {
            orchestrator.close();
        }
    }

    /**
     * Store wrapper that delegates to a real store but, when the step calls
     * {@code completeTask} for the watched task, first has a "ghost" (using
     * the SAME orchestrator session) sneak in and complete the task. The
     * step's subsequent completeTask CAS then loses.
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
        public Optional<TeamTask> completeTask(String t, String b) {
            if (watchedTaskId.equals(t) && ghostCompleted.compareAndSet(0, 1)) {
                delegate.completeTask(t, ghostSessionId);
            }
            return delegate.completeTask(t, b);
        }

        @Override
        public Optional<TeamTask> abandonTask(String t, String b) {
            return delegate.abandonTask(t, b);
        }

        @Override
        public Optional<TeamTask> reclaimTask(String t, String b) {
            return delegate.reclaimTask(t, b);
        }
    }
}
