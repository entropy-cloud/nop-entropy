package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.team.DefaultMemberSpawner;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2026-08-12-2050-2 / AR-2 fixture B (step path): the real
 * {@link DefaultMemberSpawner} + a hanging {@link IAgentEngine}
 * ({@code execute} returns a future that never settles) must NOT make
 * {@link SpawnMemberAgentTaskStep} (and the whole orchestrator run) wait
 * unbounded. The timeout value carried on the {@link SpawnMemberRequest}
 * (wired through the step constructor from the orchestrator's
 * {@code memberExecTimeoutMs}) reaches the spawner's bounded
 * {@code get()} → {@code TimeoutException} → honest
 * {@code SpawnMemberResult#spawnFailed} → node failure → failed
 * {@link TeamTaskFlowResult}.
 *
 * <p>Worker-release assertion (the plan assigns worker release exclusively to
 * this fixture): the step runs on a <b>single-thread</b> spawn executor; if
 * the worker were still parked on the unbounded join, a subsequent task
 * submitted to that pool would never run. The orchestrator returning in
 * bounded time + the follow-up task completing proves the spawn worker was
 * released by the bounded {@code get()}.
 *
 * <p>Wiring verification: the timeout that fired is the configured
 * {@code memberExecTimeoutMs} (≈300ms) — if the value never reached the
 * execution point, the wait would be the 120s default and the 5s window
 * would fail.
 */
public class TestSpawnStepTimeoutHonestFailure {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Engine whose {@code execute} never settles (hanging spawned
     * execution). The spawner's bounded {@code get()} must fire first.
     */
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
    void hangingEngineSpawnStepFailsBoundedlyAndReleasesWorker() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        HangingEngine engine = new HangingEngine();

        // No bound members — the NoOp router's spawn fallback selects the
        // SpawnMemberAgentTaskStep for the node (real spawn path).
        Team team = createTeamWithMembers(mgr, "step-timeout-team", "m1");
        String taskId = createTask(store, team.getTeamId(), "step-timeout-task");
        assertTrue(store.getTask(taskId).isPresent(), "task must exist");

        IMemberSpawner spawner = new DefaultMemberSpawner(engine);
        long memberExecTimeoutMs = 300L;
        ExecutorService spawnExecutor = Executors.newFixedThreadPool(1, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ai-agent-spawn-step-timeout-worker-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });

        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(
                engine, store, mgr, /*taskFlowManager=*/null, spawner,
                /*taskMemberRouter=*/null, memberExecTimeoutMs);
        orchestrator.setSpawnStepExecutor(spawnExecutor);
        try {
            long start = System.currentTimeMillis();
            TeamTaskFlowResult result = orchestrator.execute(team.getTeamId());
            long elapsedMs = System.currentTimeMillis() - start;

            // Bounded settlement: the spawner's bounded get() must release
            // the spawn worker within memberExecTimeoutMs and the step must
            // fail honestly — never wait the 120s default (wiring evidence).
            assertTrue(elapsedMs < 5000,
                    "the spawn step must settle bounded by the member timeout, took " + elapsedMs + "ms");
            assertFalse(result.isSuccess(),
                    "a hung spawned execution must produce a failed team-flow result");

            // Honest failure state: the task stays CLAIMED (not COMPLETED).
            TeamTask after = store.getTask(taskId).orElseThrow();
            assertEquals(TeamTaskStatus.CLAIMED, after.getStatus(),
                    "a timed-out spawn must leave the task CLAIMED (not COMPLETED)");

            // The engine was really invoked (the spawn reached the
            // execution point; the bounded get() timed out waiting for it).
            assertTrue(engine.executeCount.get() >= 1,
                    "the member engine execute must have been invoked");

            // Worker-release proof: the single-thread pool must accept and
            // complete a follow-up task — the spawn worker is no longer
            // parked on the engine future.
            CompletableFuture<Boolean> probe = CompletableFuture.supplyAsync(() -> true, spawnExecutor);
            assertTrue(probe.get(2, TimeUnit.SECONDS),
                    "the spawn worker must be released after the bounded get() timeout");
        } finally {
            spawnExecutor.shutdownNow();
        }
    }

    // ========================================================================
    // Helpers
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
}
