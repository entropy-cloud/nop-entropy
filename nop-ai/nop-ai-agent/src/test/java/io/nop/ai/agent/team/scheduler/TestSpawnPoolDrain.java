package io.nop.ai.agent.team.scheduler;

import io.nop.ai.agent.NopAiAgentException;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
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
import io.nop.ai.agent.team.flow.MemberDispatchPlan;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2519 (C4-015 / 裁定④) focused regression: the spawn pool drain is a
 * bounded three-stage sequence (shutdown → awaitTermination budget →
 * shutdownNow) whose discard point EXPLICITLY settles every not-yet-settled
 * in-flight dispatch future (typed cause "spawn pool drained before
 * execution") — a queued spawn task discarded at stop is the DESIGNED
 * semantics, but a SILENT discard (the pre-fix shutdownNow with no drain:
 * dispatch future never settles, inFlightDispatches entry leaks, task stuck
 * CLAIMED with no settle record) is the defect.
 *
 * <p>Pins (新增断言即新钉 — defect state red, fix green):
 * <ul>
 *   <li>{@link #queuedDispatchFutureSettlesExplicitlyAtDrain} — a queued
 *       (never-started) spawn dispatch future SETTLES at stop and its
 *       inFlightDispatches entry is removed (defect state: drain settle point
 *       removed → the future never settles, the queue leaks — RED).</li>
 *   <li>{@link #runningDispatchFutureSettlesWithinDrainBudget} — a running
 *       dispatch settles naturally within the awaitTermination budget; the
 *       inFlightDispatches invariant ("every tracked future settles") holds
 *       for the running state too (两态不变式).</li>
 * </ul>
 */
public class TestSpawnPoolDrain {

    /** Long per-member deadline so the dispatcher orTimeout never races the drain. */
    private static final long LONG_MEMBER_TIMEOUT_MS = 60_000L;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Fixtures (self-contained — the flow-package helpers are not visible)
    // ========================================================================

    /**
     * Spawner that blocks inside its method body until released — occupies
     * the spawn worker so extra fan-out work stays QUEUED behind it.
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
                        "nop.ai.team.flow.spawn-drain-test: blocking spawner interrupted", e);
            }
            return SpawnMemberResult.spawnFailed("released after drain test");
        }
    }

    /** Minimal engine — never consulted for a spawn-only plan. */
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

    static Team teamWithMember(InMemoryTeamManager mgr, String teamName, String memberName) {
        TeamSpec spec = new TeamSpec(teamName, "test", "lead-agent",
                List.of(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec(memberName, memberName + "-agent", MemberRole.MEMBER)),
                0);
        return mgr.createTeam(spec);
    }

    static ITaskMemberRouter spawnOnlyRouter(Team team, String memberName) {
        TeamMemberSpec spec = null;
        for (TeamMemberSpec s : team.getSpec().getMemberSpecs()) {
            if (s.getMemberName().equals(memberName)) {
                spec = s;
                break;
            }
        }
        final TeamMemberSpec target = spec;
        return (t, task) -> new MemberDispatchPlan(team, task,
                List.of(DispatchTarget.spawn(target)), AllMustSucceedReduction.instance());
    }

    static ExecutorService singleWorkerPool(String name) {
        return Executors.newFixedThreadPool(1, new ThreadFactory() {
            final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, name + "-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }

    /**
     * Queued-state drain settle pin: the spawn worker is saturated, so the
     * fan-out supplyAsync stays queued; stop (shutdownOwnSpawnExecutor) must
     * EXPLICITLY settle the tracked dispatch future and remove its
     * inFlightDispatches entry. Defect state (drain settle point removed —
     * the pre-fix bare shutdownNow): the future never settles, the entry
     * leaks forever → RED (poll bound expires with count still 1).
     */
    @Test
    void queuedDispatchFutureSettlesExplicitlyAtDrain() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = teamWithMember(mgr, "drain-queued-team", "m1");
        String taskId = store.createTask(team.getTeamId(), "drain-queued-task",
                "desc", Collections.emptyList(), "lead-session").getTaskId();
        java.util.Optional<TeamTask> claimed = store.claimTask(taskId, "daemon-session");
        assertTrue(claimed.isPresent(), "task must be claimable");

        // Saturate the injected executor so the spawn dispatch stays queued.
        CountDownLatch busyHold = new CountDownLatch(1);
        ExecutorService busy = singleWorkerPool("drain-test-busy-worker");
        busy.submit(() -> {
            try {
                busyHold.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Thread.sleep(200); // let the busy worker start

        BlockingSpawner spawner = new BlockingSpawner();
        TaskDispatchCoordinator coordinator = new TaskDispatchCoordinator(
                new UnusedEngine(), store, "daemon-session", LONG_MEMBER_TIMEOUT_MS);
        coordinator.setMemberSpawner(spawner);
        coordinator.setTaskMemberRouter(spawnOnlyRouter(team, "m1"));
        coordinator.setSpawnStepExecutor(busy);

        TaskDispatchCoordinator.DispatchTally tally = coordinator.dispatchClaimedTask(
                team, store.getTask(taskId).orElseThrow(), claimed.get(), null);
        assertEquals(1, tally.dispatched, "the fan-out must have fired (async in-flight)");
        assertEquals(1, coordinator.inFlightDispatchCount(),
                "the queued dispatch must be tracked in-flight before the drain");

        // Stop with queued work: the drain must settle the tracked future.
        coordinator.shutdownOwnSpawnExecutor();

        long deadline = System.currentTimeMillis() + 3_000;
        while (coordinator.inFlightDispatchCount() > 0
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(0, coordinator.inFlightDispatchCount(),
                "the queued dispatch future must SETTLE explicitly at the drain point "
                        + "(silent discard = the C4-015 defect state — RED if still tracked)");

        // Designed stop semantics: the discarded spawn leaves the task CLAIMED
        // (recovery via reclaim), never silently COMPLETED or stuck forever.
        assertEquals(TeamTaskStatus.CLAIMED, store.getTask(taskId).orElseThrow().getStatus(),
                "a drain-discarded spawn must leave the task CLAIMED for reclaim");

        busyHold.countDown();
        busy.shutdownNow();
        assertTrue(busy.awaitTermination(2, TimeUnit.SECONDS));
    }

    /**
     * Running-state drain pin (两态不变式 running face): a RUNNING dispatch
     * settles naturally within the awaitTermination budget — the drain must
     * not lose it either.
     */
    @Test
    void runningDispatchFutureSettlesWithinDrainBudget() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = teamWithMember(mgr, "drain-running-team", "m1");
        String taskId = store.createTask(team.getTeamId(), "drain-running-task",
                "desc", Collections.emptyList(), "lead-session").getTaskId();
        java.util.Optional<TeamTask> claimed = store.claimTask(taskId, "daemon-session");
        assertTrue(claimed.isPresent(), "task must be claimable");

        BlockingSpawner spawner = new BlockingSpawner();
        TaskDispatchCoordinator coordinator = new TaskDispatchCoordinator(
                new UnusedEngine(), store, "daemon-session", LONG_MEMBER_TIMEOUT_MS);
        coordinator.setMemberSpawner(spawner);
        coordinator.setTaskMemberRouter(spawnOnlyRouter(team, "m1"));
        // No injected executor: the coordinator lazily builds its OWN pool
        // (the daemon stop path drains that pool).

        TaskDispatchCoordinator.DispatchTally tally = coordinator.dispatchClaimedTask(
                team, store.getTask(taskId).orElseThrow(), claimed.get(), null);
        assertEquals(1, tally.dispatched, "the fan-out must have fired (async in-flight)");
        assertTrue(spawner.entered.await(5, TimeUnit.SECONDS),
                "the blocking spawner must be running on the owned pool worker");
        assertEquals(1, coordinator.inFlightDispatchCount(),
                "the running dispatch must be tracked in-flight before the drain");

        // Let the running dispatch finish naturally, then stop: the running
        // state settles within the awaitTermination budget.
        spawner.release.countDown();
        long deadline = System.currentTimeMillis() + 5_000;
        while (coordinator.inFlightDispatchCount() > 0
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(0, coordinator.inFlightDispatchCount(),
                "the running dispatch must settle within the drain budget (两态不变式 running face)");

        coordinator.shutdownOwnSpawnExecutor();
        assertEquals(0, coordinator.inFlightDispatchCount(),
                "no tracked future may remain unsettled after the stop");
    }
}
