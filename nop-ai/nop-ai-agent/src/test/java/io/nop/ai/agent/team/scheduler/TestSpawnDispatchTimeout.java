package io.nop.ai.agent.team.scheduler;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.ai.agent.team.flow.AllMustSucceedReduction;
import io.nop.ai.agent.team.flow.DispatchTarget;
import io.nop.ai.agent.team.flow.ITaskMemberRouter;
import io.nop.ai.agent.team.flow.MemberDispatchOutcome;
import io.nop.ai.agent.team.flow.MemberDispatchPlan;
import io.nop.ai.agent.team.flow.MemberFanOutDispatcher;
import io.nop.ai.agent.team.scheduler.SpawnMemberRequest;
import io.nop.ai.agent.team.scheduler.SpawnMemberResult;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2026-08-12-2050-2 / AR-2 fixture A (dispatcher path): a SPAWN target
 * whose {@link IMemberSpawner#spawnMember} <b>blocks inside its method body</b>
 * (the synchronous spawn contract blocks on the spawn worker) must NOT make
 * {@link MemberFanOutDispatcher#dispatch} wait indefinitely — the
 * dispatcher-layer per-target {@code orTimeout} settles the per-member future
 * within {@code memberExecTimeoutMs}, the {@code allOf} reduce propagates the
 * timeout, and {@code dispatch} returns an honest failed outcome.
 *
 * <p>Two assertions per the plan's fixture-A contract:
 * <ul>
 *   <li>{@link #dispatchSettlesBoundedlyWithBlockingSpawner} — direct
 *       dispatcher-level bounded return (failed outcome within the deadline,
 *       timeout cause surfaced, task left CLAIMED).</li>
 *   <li>{@link #daemonInFlightQueueDrainsAfterSpawnTimeout} — through
 *       {@link TaskDispatchCoordinator}: the {@code inFlightDispatches} entry
 *       is removed once the bounded future settles ({@code whenComplete}
 *       fires), i.e. no permanent queue leak (AR-2 daemon queue leak).</li>
 * </ul>
 *
 * <p>Deliberately NOT asserted here: worker release. The blocking mock's
 * worker never returns — {@code orTimeout} does not cancel the source
 * {@code supplyAsync} task (cancel only propagates downwards, AR-4
 * analysis). Worker release is asserted only by fixture B
 * ({@code TestSpawnStepTimeoutHonestFailure}, real
 * {@code DefaultMemberSpawner} + hanging engine → bounded {@code get()}).
 */
public class TestSpawnDispatchTimeout {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Spawner whose {@code spawnMember} blocks inside the method body until
     * released (the synchronous spawn contract — the mock must block in the
     * method, not return a never-settling future: spawnMember returns a
     * value, not a future).
     */
    static final class BlockingSpawner implements IMemberSpawner {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            calls.incrementAndGet();
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NopAiAgentException(
                        "nop.ai.team.flow.spawn-timeout-test: blocking spawner interrupted", e);
            }
            return SpawnMemberResult.spawnFailed("unreachable — worker was released");
        }
    }

    /** Minimal engine — never consulted for a spawn-only plan, but the coordinator needs one. */
    static final class UnusedEngine implements IAgentEngine {
        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }
    }

    /** Engine whose {@code execute} never settles (hanging spawned execution). */
    static final class HangingEngine implements IAgentEngine {
        final AtomicInteger executeCount = new AtomicInteger();

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            executeCount.incrementAndGet();
            return new CompletableFuture<>();
        }
    }

    @Test
    void dispatchSettlesBoundedlyWithBlockingSpawner() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithMembers(mgr, "blocking-spawn-team", "m1");
        String taskId = createTask(store, team.getTeamId(), "blocking-spawn-task");
        TeamTask task = store.getTask(taskId).orElseThrow();
        java.util.Optional<TeamTask> claimed = store.claimTask(taskId, "spawn-timeout-test");
        assertTrue(claimed.isPresent(), "task must be claimable");
        task = claimed.get();

        BlockingSpawner spawner = new BlockingSpawner();
        MemberDispatchPlan plan = spawnFanOutPlan(team, task, "m1");
        ExecutorService spawnExecutor = daemonPool(1);
        try {
            long deadlineMs = 300L;
            long start = System.currentTimeMillis();
            MemberDispatchOutcome outcome = MemberFanOutDispatcher.dispatch(
                    task, team, plan.getTargets(), plan.getReductionStrategy(),
                    /*agentEngine=*/null, spawner, store, "spawn-timeout-test",
                    spawnExecutor, null, deadlineMs)
                    .get(10, TimeUnit.SECONDS);
            long elapsedMs = System.currentTimeMillis() - start;

            // The dispatch future must settle bounded by the per-member
            // deadline (dispatcher-layer orTimeout) even though the spawner
            // blocks inside its method body and never returns.
            assertTrue(elapsedMs < 5000,
                    "dispatch must return bounded by the member timeout, took " + elapsedMs + "ms");
            assertTrue(spawner.entered.await(2, TimeUnit.SECONDS),
                    "the blocking mock spawner must have been invoked on the spawn worker");
            assertEquals(1, spawner.calls.get(), "the spawner must have been called exactly once");

            // Honest failure: timeout → failed outcome, task stays CLAIMED.
            assertFalse(outcome.isCompleted(), "a blocked spawner must produce a failed dispatch outcome");
            assertNotNull(outcome.getCause(), "the failed outcome must carry the root cause");
            Throwable causeChain = outcome.getCause();
            boolean timeoutInChain = false;
            while (causeChain != null) {
                if (causeChain instanceof java.util.concurrent.TimeoutException
                        || String.valueOf(causeChain).contains("TimeoutException")
                        || String.valueOf(causeChain).contains("timeout")) {
                    timeoutInChain = true;
                    break;
                }
                causeChain = causeChain.getCause();
            }
            assertTrue(timeoutInChain,
                    "the outcome cause chain must surface the timeout, got: " + outcome.getCause());

            TeamTask after = store.getTask(taskId).orElseThrow();
            assertEquals(TeamTaskStatus.CLAIMED, after.getStatus(),
                    "a timed-out spawn must leave the task CLAIMED (not COMPLETED)");
        } finally {
            spawnExecutor.shutdownNow();
        }
    }

    @Test
    void daemonInFlightQueueDrainsAfterSpawnTimeout() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithMembers(mgr, "blocking-spawn-team-2", "m1");
        String taskId = createTask(store, team.getTeamId(), "blocking-spawn-task-2");
        TeamTask task = store.getTask(taskId).orElseThrow();
        java.util.Optional<TeamTask> claimed = store.claimTask(taskId, "daemon-session");
        assertTrue(claimed.isPresent(), "task must be claimable");

        BlockingSpawner spawner = new BlockingSpawner();
        long deadlineMs = 300L;
        TaskDispatchCoordinator coordinator = new TaskDispatchCoordinator(
                new UnusedEngine(), store, "daemon-session", deadlineMs);
        coordinator.setMemberSpawner(spawner);
        coordinator.setTaskMemberRouter(spawnOnlyRouter(team, "m1"));

        long start = System.currentTimeMillis();
        TaskDispatchCoordinator.DispatchTally tally = coordinator.dispatchClaimedTask(
                team, task, claimed.get(), null);
        assertEquals(0, tally.completed, "a blocked spawn must not complete synchronously");
        assertEquals(0, tally.failed, "a genuinely async dispatch is not a sync failure");
        assertEquals(1, tally.dispatched, "the fan-out must have fired (async in-flight)");

        // The bounded dispatch future settles within the per-member deadline,
        // then the whenComplete removal fires — poll for the queue drain
        // (AR-2 queue-leak proof: no permanent inFlightDispatches entry).
        long drainDeadline = System.currentTimeMillis() + 10_000;
        while (coordinator.inFlightDispatchCount() > 0 && System.currentTimeMillis() < drainDeadline) {
            Thread.sleep(10);
        }
        long elapsedMs = System.currentTimeMillis() - start;
        assertTrue(elapsedMs < 5000,
                "queue drain must happen bounded by the member timeout, took " + elapsedMs + "ms");
        assertEquals(0, coordinator.inFlightDispatchCount(),
                "inFlightDispatches must drain back to zero after the timed-out dispatch settles");
        assertTrue(coordinator.awaitInFlightDispatches(1000),
                "no in-flight dispatch may remain unsettled after the drain");

        TeamTask after = store.getTask(taskId).orElseThrow();
        assertEquals(TeamTaskStatus.CLAIMED, after.getStatus(),
                "a timed-out spawn must leave the task CLAIMED (recovery via plan 240 reclaim)");
        coordinator.shutdownOwnSpawnExecutor();
    }

    /**
     * Plan exit-criterion end-to-end chain:
     * {@code TaskDispatchCoordinator.dispatchClaimedTask} (SPAWN target) →
     * {@code MemberFanOutDispatcher.dispatch} → real {@code DefaultMemberSpawner}
     * → hanging engine → spawner-layer bounded {@code get()} timeout →
     * honest failed outcome + in-flight queue drained — the production
     * daemon path with the real spawner, end to end.
     */
    @Test
    void daemonPathWithRealSpawnerAndHangingEngineDrainsQueue() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithMembers(mgr, "e2e-spawn-team", "m1");
        String taskId = createTask(store, team.getTeamId(), "e2e-spawn-task");
        TeamTask task = store.getTask(taskId).orElseThrow();
        java.util.Optional<TeamTask> claimed = store.claimTask(taskId, "daemon-session");
        assertTrue(claimed.isPresent(), "task must be claimable");

        HangingEngine engine = new HangingEngine();
        long deadlineMs = 300L;
        TaskDispatchCoordinator coordinator = new TaskDispatchCoordinator(
                engine, store, "daemon-session", deadlineMs);
        coordinator.setMemberSpawner(new io.nop.ai.agent.team.DefaultMemberSpawner(engine));
        coordinator.setTaskMemberRouter(spawnOnlyRouter(team, "m1"));

        long start = System.currentTimeMillis();
        TaskDispatchCoordinator.DispatchTally tally = coordinator.dispatchClaimedTask(
                team, task, claimed.get(), null);
        assertEquals(1, tally.dispatched, "the fan-out must have fired (async in-flight)");

        long drainDeadline = System.currentTimeMillis() + 10_000;
        while (coordinator.inFlightDispatchCount() > 0 && System.currentTimeMillis() < drainDeadline) {
            Thread.sleep(10);
        }
        long elapsedMs = System.currentTimeMillis() - start;
        assertTrue(elapsedMs < 5000,
                "the daemon path must settle bounded by the member timeout, took " + elapsedMs + "ms");
        assertEquals(0, coordinator.inFlightDispatchCount(),
                "inFlightDispatches must drain to zero on the real-spawner path too");
        assertTrue(engine.executeCount.get() >= 1,
                "the real spawner must have reached the engine execution point");

        TeamTask after = store.getTask(taskId).orElseThrow();
        assertEquals(TeamTaskStatus.CLAIMED, after.getStatus(),
                "a timed-out spawn must leave the task CLAIMED");
        coordinator.shutdownOwnSpawnExecutor();
    }

    // ========================================================================
    // Helpers (self-contained — this test lives in the scheduler package, the
    // flow-package AbstractMultiMemberFanOutTest helpers are not visible).
    // ========================================================================

    static Team createTeamWithMembers(InMemoryTeamManager mgr, String teamName, String... memberNames) {
        List<TeamMemberSpec> specs = new ArrayList<>();
        specs.add(new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD));
        for (String name : memberNames) {
            specs.add(new TeamMemberSpec(name, name + "-agent", MemberRole.MEMBER));
        }
        TeamSpec spec = new TeamSpec(teamName, "test", "lead-agent", specs, 0);
        return mgr.createTeam(spec);
    }

    static String createTask(InMemoryTeamTaskStore store, String teamId, String subject) {
        return store.createTask(teamId, subject, "desc-" + subject,
                Collections.emptyList(), "lead-session").getTaskId();
    }

    static MemberDispatchPlan spawnFanOutPlan(Team team, TeamTask task, String memberName) {
        TeamMemberSpec spec = null;
        for (TeamMemberSpec s : team.getSpec().getMemberSpecs()) {
            if (s.getMemberName().equals(memberName)) {
                spec = s;
                break;
            }
        }
        assertNotNull(spec, "memberSpec not found: " + memberName);
        return new MemberDispatchPlan(team, task, List.of(DispatchTarget.spawn(spec)),
                AllMustSucceedReduction.instance());
    }

    static ITaskMemberRouter spawnOnlyRouter(Team team, String memberName) {
        return (t, task) -> spawnFanOutPlan(team, task, memberName);
    }

    static ExecutorService daemonPool(int size) {
        return Executors.newFixedThreadPool(size, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ai-agent-spawn-timeout-test-worker-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }
}
