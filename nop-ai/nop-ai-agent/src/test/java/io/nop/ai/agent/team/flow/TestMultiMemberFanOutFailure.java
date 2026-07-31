package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 244 failure-path scenarios: all-must-succeed reduction honest
 * failure (bound member failure / spawn failure / empty router plan /
 * complete CAS loss) with the task left in the observable state the
 * orchestrator's failure model specifies. Split from
 * {@code TestMultiMemberFanOut} (MA4.2-06); fixtures in
 * {@link AbstractMultiMemberFanOutTest}.
 */
public class TestMultiMemberFanOutFailure extends AbstractMultiMemberFanOutTest {

    // ========================================================================
    // all-must-succeed reduction: failure paths → task stays CLAIMED.
    //    (a) bound member non-completed status
    //    (b) bound member engine exception
    //    (c) spawn NO_SPAWN
    //    (d) spawn SPAWN_FAILED
    //    (e) spawn spawner throws
    //    (f) spawn spawner returns null
    //    (g) spawn dispatched non-completed
    // ========================================================================

    @Test
    void boundFanOutMemberNonCompletedLeavesTaskClaimed() throws Exception {
        runBoundFanOutFailureLeavesTaskClaimed(ConfigurableFanOutEngine.FailureKind.NON_COMPLETED);
    }

    @Test
    void boundFanOutMemberExceptionLeavesTaskClaimed() throws Exception {
        runBoundFanOutFailureLeavesTaskClaimed(ConfigurableFanOutEngine.FailureKind.EXCEPTION);
    }

    private void runBoundFanOutFailureLeavesTaskClaimed(
            ConfigurableFanOutEngine.FailureKind kind) throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConfigurableFanOutEngine engine = new ConfigurableFanOutEngine();
        String[] members = {"m1", "m2"};
        String[] sessions = {"s1", "s2"};
        Team team = createTeamAndBindMembers(mgr, "bound-fail-team", members, sessions);
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        engine.failOn(a, "m2", kind);

        ITaskMemberRouter router = new FixedPlanRouter(
                t -> boundFanOutPlan(team, t, Arrays.asList("m1", "m2")));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, null, router);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);

            assertFalse(result.isSuccess(),
                    "bound fan-out with " + kind + " must honestly fail: " + result);
            assertEquals(a, result.getFailedTaskId());
            // Anti-Hollow #24 — task left CLAIMED (not abandoned, not silent skip).
            assertEquals(TeamTaskStatus.CLAIMED, store.getTask(a).orElseThrow().getStatus(),
                    "task stays CLAIMED on fan-out failure (kind=" + kind + ")");
            assertNotEquals(TeamTaskStatus.ABANDONED, store.getTask(a).orElseThrow().getStatus(),
                    "task NOT abandoned (orchestrator's failure model, not daemon's)");
        } finally {
            orchestrator.close();
        }
    }

    @Test
    void spawnFanOutNoSpawnLeavesTaskClaimed() throws Exception {
        runSpawnFanOutFailureLeavesTaskClaimed(ConcurrencyRecordingSpawner.SpawnFailure.NO_SPAWN);
    }

    @Test
    void spawnFanOutSpawnFailedLeavesTaskClaimed() throws Exception {
        runSpawnFanOutFailureLeavesTaskClaimed(ConcurrencyRecordingSpawner.SpawnFailure.SPAWN_FAILED);
    }

    @Test
    void spawnFanOutSpawnerThrowsLeavesTaskClaimed() throws Exception {
        runSpawnFanOutFailureLeavesTaskClaimed(ConcurrencyRecordingSpawner.SpawnFailure.THROWS);
    }

    @Test
    void spawnFanOutSpawnerNullLeavesTaskClaimed() throws Exception {
        runSpawnFanOutFailureLeavesTaskClaimed(ConcurrencyRecordingSpawner.SpawnFailure.NULL);
    }

    @Test
    void spawnFanOutDispatchedNonCompletedLeavesTaskClaimed() throws Exception {
        runSpawnFanOutFailureLeavesTaskClaimed(ConcurrencyRecordingSpawner.SpawnFailure.NON_COMPLETED);
    }

    private void runSpawnFanOutFailureLeavesTaskClaimed(
            ConcurrencyRecordingSpawner.SpawnFailure failure) throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConcurrencyRecordingSpawner spawner = new ConcurrencyRecordingSpawner();
        Team team = createTeamWithMembers(mgr, "spawn-fail-team", "m1", "m2");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        spawner.failOn(a, "m2", failure);

        ITaskMemberRouter router = new FixedPlanRouter(
                t -> spawnFanOutPlan(team, t, Arrays.asList("m1", "m2")));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner, router);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);

            assertFalse(result.isSuccess(),
                    "spawn fan-out with " + failure + " must honestly fail: " + result);
            assertEquals(a, result.getFailedTaskId());
            assertEquals(TeamTaskStatus.CLAIMED, store.getTask(a).orElseThrow().getStatus(),
                    "task stays CLAIMED on spawn fan-out failure (failure=" + failure + ")");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // Empty router plan → honest failure (NopAiAgentException), task stays
    // CREATED, no node runs. Structural fast-fail before execute.
    // ========================================================================

    @Test
    void emptyRouterPlanHonestFailureBuildAbort() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConcurrencyRecordingEngine engine = new ConcurrencyRecordingEngine();
        Team team = createTeamWithMembers(mgr, "empty-plan-team", "m1");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        ITaskMemberRouter router = new FixedPlanRouter(t -> emptyPlan(team, t));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, null, router);
        try {
            // Structural fast-fail: empty plan throws synchronously out of
            // executeAsync (before the future is created).
            NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                    () -> orchestrator.executeAsync(teamId),
                    "empty plan MUST honest-fail at build time (not silent skip)");
            assertTrue(ex.getMessage().contains("no-dispatchable-member")
                            || ex.getMessage().contains("zero targets"),
                    "exception message identifies the empty-plan cause: " + ex.getMessage());

            // Anti-Hollow #24 — task stays CREATED (never claimed, never silently skipped).
            assertEquals(TeamTaskStatus.CREATED, store.getTask(a).orElseThrow().getStatus(),
                    "task stays CREATED on empty plan (honest failure, no silent skip)");
            // Engine never invoked.
            assertEquals(0, engine.executeCount.get(), "engine never invoked on empty plan");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // complete CAS loss on fan-out → honest failure.
    // ========================================================================

    /**
     * Store wrapper that, on the orchestrator's completeTask for a watched
     * task, first has a "ghost" complete sneak in, so the orchestrator's CAS
     * loses (mirrors TestAsyncMemberStepHonestFailure.GhostCompletingStore
     * but for the fan-out path).
     */
    static final class GhostCompletingFanOutStore implements ITeamTaskStore {
        private final InMemoryTeamTaskStore delegate;
        private final String watchedTaskId;
        private final String ghostSessionId;
        private final AtomicInteger ghostCompleted = new AtomicInteger(0);

        GhostCompletingFanOutStore(InMemoryTeamTaskStore delegate, String taskId, String ghostSessionId) {
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

    @Test
    void fanOutCompleteCasLossHonestFail() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore realStore = new InMemoryTeamTaskStore();
        ConcurrencyRecordingEngine engine = new ConcurrencyRecordingEngine();
        String[] members = {"m1", "m2"};
        String[] sessions = {"s1", "s2"};
        Team team = createTeamAndBindMembers(mgr, "cas-team", members, sessions);
        String teamId = team.getTeamId();
        String a = createTask(realStore, teamId, "A", Collections.emptyList());

        // Ghost completes with the orchestrator session id (so exactly one
        // of the two CAS-conditional completeTask calls wins; orchestrator's
        // loses).
        String orchestratorSessionId = "orchestrator-" + teamId;
        GhostCompletingFanOutStore ghostStore =
                new GhostCompletingFanOutStore(realStore, a, orchestratorSessionId);

        ITaskMemberRouter router = new FixedPlanRouter(
                t -> boundFanOutPlan(team, t, Arrays.asList("m1", "m2")));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, ghostStore, mgr, null, null, router);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);

            // Anti-Hollow #24 — complete CAS loss honest failure.
            assertFalse(result.isSuccess(),
                    "fan-out complete CAS loss must honestly fail: " + result);
            assertEquals(a, result.getFailedTaskId());
        } finally {
            orchestrator.close();
        }
    }
}
