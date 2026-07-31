package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamTaskStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 244 success-path scenarios: bound/spawn fan-out real concurrency,
 * all-must-succeed reduction, mixed bound+spawn fan-out, and idempotent
 * handling of already-COMPLETED tasks. Split from
 * {@code TestMultiMemberFanOut} (MA4.2-06); fixtures in
 * {@link AbstractMultiMemberFanOutTest}.
 */
public class TestMultiMemberFanOutSuccess extends AbstractMultiMemberFanOutTest {

    // ========================================================================
    // 1. Bound fan-out real concurrency: diamond A→{B,C}→D where B fans out
    //    to {B1,B2} and C fans out to {C1,C2}. All 4 bound members.
    // ========================================================================

    @Test
    void boundFanOutDiamondRealConcurrencyAndDAfterBoth() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConcurrencyRecordingEngine engine = new ConcurrencyRecordingEngine();
        String[] members = {"b1", "b2", "c1", "c2"};
        String[] sessions = {"s-b1", "s-b2", "s-c1", "s-c2"};
        Team team = createTeamAndBindMembers(mgr, "bound-fanout-team", members, sessions);
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        // Router: B → {b1,b2}, C → {c1,c2}, A/D → single bound (b1 / c1).
        ITaskMemberRouter router = new FixedPlanRouter(t -> {
            switch (t.getSubject()) {
                case "B": return boundFanOutPlan(team, t, Arrays.asList("b1", "b2"));
                case "C": return boundFanOutPlan(team, t, Arrays.asList("c1", "c2"));
                case "A": return boundFanOutPlan(team, t, Collections.singletonList("b1"));
                case "D": return boundFanOutPlan(team, t, Collections.singletonList("c1"));
                default: throw new AssertionError("unexpected task: " + t.getSubject());
            }
        });
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, null, router);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);

            assertTrue(result.isSuccess(), "diamond should complete: " + result);
            assertEquals(4, result.getCompletedTaskIds().size());
            for (String id : Arrays.asList(a, b, c, d)) {
                assertEquals(TeamTaskStatus.COMPLETED, store.getTask(id).orElseThrow().getStatus(),
                        "task " + id + " COMPLETED");
            }

            // Anti-Hollow #22 — REAL CONCURRENCY evidence inside B's fan-out:
            // b1 and b2 must overlap.
            long b1Enter = engine.enterNano.get(b + "#b1");
            long b1Exit = engine.exitNano.get(b + "#b1");
            long b2Enter = engine.enterNano.get(b + "#b2");
            long b2Exit = engine.exitNano.get(b + "#b2");
            boolean bOverlap = (b1Enter < b2Exit) && (b2Enter < b1Exit);
            assertTrue(bOverlap,
                    "B's fan-out members b1,b2 MUST overlap (real concurrency): "
                            + "b1[" + b1Enter + "," + b1Exit + "], b2[" + b2Enter + "," + b2Exit + "]");

            // Same for C's fan-out.
            long c1Enter = engine.enterNano.get(c + "#c1");
            long c1Exit = engine.exitNano.get(c + "#c1");
            long c2Enter = engine.enterNano.get(c + "#c2");
            long c2Exit = engine.exitNano.get(c + "#c2");
            boolean cOverlap = (c1Enter < c2Exit) && (c2Enter < c1Exit);
            assertTrue(cOverlap, "C's fan-out members c1,c2 MUST overlap (real concurrency)");

            // Peak concurrent across the whole DAG >= 2 (independent branches
            // concurrent + each fan-out's members concurrent).
            assertTrue(engine.peakConcurrent.get() >= 2,
                    "diamond peakConcurrent >= 2 (independent branches concurrent): peak="
                            + engine.peakConcurrent.get());

            // Anti-Hollow — DEPENDENCY ORDER: D starts strictly after both
            // B and C complete.
            assertTrue(result.getStartOrder().get(d) > result.getCompletionOrder().get(b),
                    "D starts after B completes");
            assertTrue(result.getStartOrder().get(d) > result.getCompletionOrder().get(c),
                    "D starts after C completes");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 2. Spawn fan-out real concurrency: diamond A→{B,C}→D, B → {b1,b2}
    //    spawn targets, C → {c1,c2} spawn targets.
    // ========================================================================

    @Test
    void spawnFanOutDiamondRealConcurrencyAndDAfterBoth() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConcurrencyRecordingSpawner spawner = new ConcurrencyRecordingSpawner();
        Team team = createTeamWithMembers(mgr, "spawn-fanout-team", "b1", "b2", "c1", "c2");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        ITaskMemberRouter router = new FixedPlanRouter(t -> {
            switch (t.getSubject()) {
                case "B": return spawnFanOutPlan(team, t, Arrays.asList("b1", "b2"));
                case "C": return spawnFanOutPlan(team, t, Arrays.asList("c1", "c2"));
                case "A": return spawnFanOutPlan(team, t, Collections.singletonList("b1"));
                case "D": return spawnFanOutPlan(team, t, Collections.singletonList("c1"));
                default: throw new AssertionError("unexpected task: " + t.getSubject());
            }
        });
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner, router);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);

            assertTrue(result.isSuccess(), "spawn diamond should complete: " + result);
            for (String id : Arrays.asList(a, b, c, d)) {
                assertEquals(TeamTaskStatus.COMPLETED, store.getTask(id).orElseThrow().getStatus(),
                        "task " + id + " COMPLETED via spawn fan-out");
            }

            // Anti-Hollow #22 — REAL CONCURRENCY inside B's spawn fan-out.
            long b1Enter = spawner.enterNano.get(b + "#b1");
            long b1Exit = spawner.exitNano.get(b + "#b1");
            long b2Enter = spawner.enterNano.get(b + "#b2");
            long b2Exit = spawner.exitNano.get(b + "#b2");
            boolean bOverlap = (b1Enter < b2Exit) && (b2Enter < b1Exit);
            assertTrue(bOverlap,
                    "B's spawn fan-out b1,b2 MUST overlap (real concurrency): "
                            + "b1[" + b1Enter + "," + b1Exit + "], b2[" + b2Enter + "," + b2Exit + "]");

            // Spawn workers ran on ai-agent-spawn-worker-N threads.
            assertTrue(spawner.peakConcurrent.get() >= 2,
                    "spawn fan-out peakConcurrent >= 2: peak=" + spawner.peakConcurrent.get());
            assertTrue(spawner.threadNames.stream().anyMatch(n -> n.startsWith("ai-agent-spawn-worker-")),
                    "spawn workers ran on dedicated ai-agent-spawn-worker-N threads: " + spawner.threadNames);

            // Dependency order.
            assertTrue(result.getStartOrder().get(d) > result.getCompletionOrder().get(b));
            assertTrue(result.getStartOrder().get(d) > result.getCompletionOrder().get(c));
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 3. all-must-succeed reduction: success path → task COMPLETED once.
    // ========================================================================

    @Test
    void allMustSucceedReductionCompletesTaskOnce() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConcurrencyRecordingEngine engine = new ConcurrencyRecordingEngine();
        String[] members = {"m1", "m2", "m3"};
        String[] sessions = {"s1", "s2", "s3"};
        Team team = createTeamAndBindMembers(mgr, "reduce-team", members, sessions);
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());

        ITaskMemberRouter router = new FixedPlanRouter(
                t -> boundFanOutPlan(team, t, Arrays.asList("m1", "m2", "m3")));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, null, router);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);

            assertTrue(result.isSuccess(), "fan-out should complete: " + result);
            // Anti-Hollow #23 — task transitioned CLAIMED → COMPLETED exactly
            // once (the orchestrator's completeTask is single per node, not
            // per member).
            assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus(),
                    "task COMPLETED once after 3-member fan-out");
            assertEquals(3, engine.peakConcurrent.get(),
                    "all 3 fan-out members concurrent: peak=" + engine.peakConcurrent.get());
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 4. Mixed bound + spawn fan-out: one dispatch plan with both bound and
    //    spawn targets reduces unified under all-must-succeed.
    // ========================================================================

    @Test
    void mixedBoundAndSpawnFanOutReducesUnified() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConcurrencyRecordingEngine engine = new ConcurrencyRecordingEngine();
        ConcurrencyRecordingSpawner spawner = new ConcurrencyRecordingSpawner();
        // Bind b1, b2; leave s1, s2 as spawn-only.
        Team team = createTeamWithMembers(mgr, "mixed-fanout-team", "b1", "b2", "s1", "s2");
        mgr.bindMemberSession(team.getTeamId(), "b1", "s-b1", "actor-b1");
        mgr.bindMemberSession(team.getTeamId(), "b2", "s-b2", "actor-b2");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        ITaskMemberRouter router = new FixedPlanRouter(
                t -> mixedFanOutPlan(team, t, Arrays.asList("b1", "b2"), Arrays.asList("s1", "s2")));
        // engine bound members, spawner spawns s1/s2.
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, spawner, router);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);

            assertTrue(result.isSuccess(),
                    "mixed bound+spawn fan-out completes: " + result);
            assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
            // All 4 targets were exercised: 2 bound (engine) + 2 spawn (spawner).
            assertTrue(engine.enterNano.containsKey(a + "#b1"), "bound b1 executed");
            assertTrue(engine.enterNano.containsKey(a + "#b2"), "bound b2 executed");
            assertTrue(spawner.enterNano.containsKey(a + "#s1"), "spawn s1 executed");
            assertTrue(spawner.enterNano.containsKey(a + "#s2"), "spawn s2 executed");
            // Real concurrency across the 4 mixed members.
            assertTrue(engine.peakConcurrent.get() >= 2
                            || spawner.peakConcurrent.get() >= 2,
                    "mixed fan-out members concurrent (bound peak=" + engine.peakConcurrent.get()
                            + ", spawn peak=" + spawner.peakConcurrent.get() + ")");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 5. already-COMPLETED task under fan-out → honest idempotent success.
    // ========================================================================

    @Test
    void fanOutAlreadyCompletedIdempotentSuccess() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConfigurableFanOutEngine engine = new ConfigurableFanOutEngine();
        String[] members = {"m1", "m2"};
        String[] sessions = {"s1", "s2"};
        Team team = createTeamAndBindMembers(mgr, "idem-team", members, sessions);
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        // Pre-complete the task.
        Long epoch = store.claimTask(a, "pre-completer").orElseThrow().getClaimEpoch();
        store.completeTask(a, "pre-completer", epoch);
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());

        ITaskMemberRouter router = new FixedPlanRouter(
                t -> boundFanOutPlan(team, t, Arrays.asList("m1", "m2")));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, null, router);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);

            assertTrue(result.isSuccess(),
                    "already-COMPLETED task honest idempotent success: " + result);
            assertTrue(result.getCompletedTaskIds().contains(a),
                    "task in completed set (explicit, not silent skip)");
            // Engine NOT invoked (claim returns empty, status COMPLETED → idempotent shortcut).
            assertEquals(0, engine.executeCount.get(),
                    "engine NOT invoked on already-COMPLETED task");
        } finally {
            orchestrator.close();
        }
    }
}
