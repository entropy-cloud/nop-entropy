package io.nop.ai.agent.team;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2519 (C7-022 / 裁定⑤) focused regression: the spawner's bounded
 * {@code get()} TimeoutException branch must CANCEL the spawned member
 * session ({@code cancelSession(spawnedSessionId, reason, forced=true)}) —
 * the pre-fix defect state returned SPAWN_FAILED while the spawned session
 * kept executing as a zombie holding an engine slot and LLM quota (the
 * RECLAIM double-execution window).
 *
 * <p>Pins (新增断言即新钉 — defect state red, fix green):
 * <ul>
 *   <li>{@link #spawnTimeoutCancelsSpawnedSessionForced} — after the bounded
 *       wait times out, cancelSession reached the engine for the spawned
 *       session id, forced=true, and the session reached a terminal state
 *       (defect state: no cancel call → the zombie session stays running —
 *       RED).</li>
 *   <li>{@link #cancelFailureIsRecordedNotSilent} — a failed cancel is
 *       explicitly recorded (member-cancel-failed counter) and the spawn
 *       outcome stays an honest SPAWN_FAILED (rule 24: never swallowed).</li>
 *   <li>{@link #settledSpawnDoesNotCancel} — a spawn that settles within the
 *       deadline must NOT be cancelled (no over-cancel of healthy members).</li>
 * </ul>
 */
public class TestMemberSpawnerTimeoutCancel {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Engine stubs
    // ========================================================================

    /**
     * Engine whose spawned execution NEVER settles (the hung member) and
     * whose cancelSession records the cancel and drives the session to a
     * terminal state — the session-state bookkeeping is the observable face
     * of the "zombie session" defect.
     */
    static class HangingEngineWithCancel implements IAgentEngine {
        final Map<String, String> sessionState = new ConcurrentHashMap<>();
        final List<String> cancelledSessionIds = Collections.synchronizedList(new ArrayList<>());
        final List<String> cancelReasons = Collections.synchronizedList(new ArrayList<>());
        final List<Boolean> forcedFlags = Collections.synchronizedList(new ArrayList<>());

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            sessionState.put(request.getSessionId(), "running");
            return new CompletableFuture<>();
        }

        @Override
        public CompletableFuture<Void> cancelSession(String sessionId, String reason, boolean forced) {
            cancelledSessionIds.add(sessionId);
            cancelReasons.add(reason);
            forcedFlags.add(forced);
            sessionState.put(sessionId, "cancelled");
            return CompletableFuture.completedFuture(null);
        }
    }

    /** Hanging engine whose cancelSession FAILS (cancel path error surface). */
    static final class HangingEngineWithFailingCancel extends HangingEngineWithCancel {
        @Override
        public CompletableFuture<Void> cancelSession(String sessionId, String reason, boolean forced) {
            cancelledSessionIds.add(sessionId);
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(
                    new io.nop.ai.agent.NopAiAgentException("cancel channel down (test)"));
            return f;
        }
    }

    /** Engine whose spawned execution completes quickly (healthy member). */
    static final class FastEngine implements IAgentEngine {
        final List<String> cancelledSessionIds = Collections.synchronizedList(new ArrayList<>());

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            return CompletableFuture.completedFuture(new AgentExecutionResult(
                    AgentExecStatus.completed, "ok", Collections.emptyList(), 1, 10L, 1L, null));
        }

        @Override
        public CompletableFuture<Void> cancelSession(String sessionId, String reason, boolean forced) {
            cancelledSessionIds.add(sessionId);
            return CompletableFuture.completedFuture(null);
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static Team teamWithMember() {
        TeamSpec spec = new TeamSpec("spawn-cancel-team", "d", "lead-agent",
                List.of(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("worker", "worker-agent", MemberRole.MEMBER)),
                0);
        return new InMemoryTeamManager().createTeam(spec);
    }

    private static SpawnMemberRequest spawnRequest(Team team, long timeoutMs) {
        TeamTask task = new InMemoryTeamTaskStore().createTask(team.getTeamId(), "A", "desc-A",
                Collections.emptyList(), "daemon-session");
        return new SpawnMemberRequest(team, task, "daemon-session-id", null, timeoutMs);
    }

    // ========================================================================
    // Pins
    // ========================================================================

    /**
     * Timeout → cancelSession(spawnedSessionId, reason, forced=true): the
     * spawned session must reach a terminal state, not zombie on. Defect
     * state (cancel call removed from the TimeoutException branch): no
     * cancel reaches the engine and the session stays running → RED.
     */
    @Test
    void spawnTimeoutCancelsSpawnedSessionForced() {
        HangingEngineWithCancel engine = new HangingEngineWithCancel();
        DefaultMemberSpawner spawner = new DefaultMemberSpawner(engine);
        Team team = teamWithMember();

        SpawnMemberResult result = spawner.spawnMember(spawnRequest(team, 150L));

        assertEquals(SpawnMemberResult.Status.SPAWN_FAILED, result.getStatus(),
                "the timed-out spawn fails honestly");
        assertEquals(1, engine.cancelledSessionIds.size(),
                "the spawned session must be cancelled on the timeout path "
                        + "(no cancel = the C7-022 zombie-session defect state — RED)");
        String cancelledId = engine.cancelledSessionIds.get(0);
        assertNotNull(cancelledId);
        assertTrue(cancelledId.startsWith("spawned-"),
                "the cancelled id is the spawned session id, got: " + cancelledId);
        assertTrue(engine.forcedFlags.get(0),
                "the timeout cancel is forced (hang cannot be waited out)");
        assertTrue(engine.cancelReasons.get(0).contains("spawn timeout"),
                "the cancel carries the timeout reason");
        assertEquals("cancelled", engine.sessionState.get(cancelledId),
                "the spawned session must reach a terminal state after the timeout "
                        + "(staying running = zombie — RED)");
    }

    /**
     * A failed cancel is explicitly recorded (member-cancel-failed counter
     * increments) and the spawn outcome remains an honest SPAWN_FAILED —
     * never silently swallowed (rule 24).
     */
    @Test
    void cancelFailureIsRecordedNotSilent() {
        HangingEngineWithFailingCancel engine = new HangingEngineWithFailingCancel();
        DefaultMemberSpawner spawner = new DefaultMemberSpawner(engine);
        Team team = teamWithMember();

        long before = DefaultMemberSpawner.memberCancelFailedCount();
        SpawnMemberResult result = spawner.spawnMember(spawnRequest(team, 150L));
        long after = DefaultMemberSpawner.memberCancelFailedCount();

        assertEquals(SpawnMemberResult.Status.SPAWN_FAILED, result.getStatus(),
                "the spawn outcome stays honest even when the cancel fails");
        assertTrue(after > before,
                "a failed cancel must be explicitly recorded via the member-cancel-failed "
                        + "counter (silently swallowed = rule 24 violation — RED)");
        assertEquals(1, engine.cancelledSessionIds.size(),
                "the cancel attempt reached the engine exactly once");
    }

    /**
     * No over-cancel: a spawn that settles within the deadline is never
     * cancelled — the timeout cancel fires on the TimeoutException branch
     * only.
     */
    @Test
    void settledSpawnDoesNotCancel() {
        FastEngine engine = new FastEngine();
        DefaultMemberSpawner spawner = new DefaultMemberSpawner(engine);
        Team team = teamWithMember();

        SpawnMemberResult result = spawner.spawnMember(spawnRequest(team, 5_000L));

        assertEquals(SpawnMemberResult.Status.DISPATCHED, result.getStatus());
        assertTrue(engine.cancelledSessionIds.isEmpty(),
                "a healthy spawn within the deadline must NOT be cancelled (误伤 guard)");
    }
}
