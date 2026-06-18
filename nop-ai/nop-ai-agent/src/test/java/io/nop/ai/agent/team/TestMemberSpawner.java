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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 237 Phase 1 focused tests for the spawn extension point:
 * {@link IMemberSpawner} / {@link NoOpMemberSpawner} / {@link DefaultMemberSpawner}.
 *
 * <p>Coverage map (maps 1:1 to Phase 1 Exit Criteria):
 * <ul>
 *   <li>{@link #noOpReturnsNoSpawnExplicit} — NoOp shipped 默认返回显式
 *       {@link SpawnMemberResult.Status#NO_SPAWN}（非静默 null/空，零回归）</li>
 *   <li>{@link #functionalSpawnerResolvesAgentModelFromMemberSpec} — functional
 *       spawner 真实从 {@link TeamMemberSpec#getAgentModel()} 解析 spawn 目标
 *       （断言 {@code agentModel} 正确传入 {@link AgentMessageRequest}）</li>
 *   <li>{@link #functionalSpawnerInvokesAgentEngineExecute} — 接线验证 #23：
 *       functional spawner 确实调用 {@link IAgentEngine#execute}（非仅返回 placeholder）</li>
 *   <li>{@link #functionalSpawnerUsesTaskSubjectAndDescriptionAsPrompt} — spawn
 *       使用的 prompt 来自 task 的 subject + description（非硬编码）</li>
 *   <li>{@link #functionalSpawnerUsesFreshSessionPerSpawn} — 每次 spawn 创建新
 *       session id（per-task spawn，非复用）</li>
 *   <li>{@link #functionalSpawnerCarriesAuditMetadata} — spawned 执行携带
 *       teamTaskId/teamId/daemonSessionId 元数据（审计可追溯）</li>
 *   <li>{@link #noMemberSpecReturnsHonestNoSpawn} — 无 memberSpec = 诚实 NO_SPAWN
 *       （非静默跳过）</li>
 *   <li>{@link #blankAgentModelReturnsHonestNoSpawn} — agentModel null/blank =
 *       诚实 NO_SPAWN（防御性，TeamMemberSpec 构造期已 non-null 兜底）</li>
 *   <li>{@link #spawnExecutionSuccessMapsToDispatchedWithResult} — spawn execution
 *       成功 → DISPATCHED 携带 AgentExecutionResult</li>
 *   <li>{@link #spawnExecutionNonCompletedStillDispatchedDaemonInterpretsStatus}
 *       — spawn execution 返回非 completed 终态 → 仍 DISPATCHED（spawner 不解释
 *       status，daemon 决定 complete/abandon，决策4）</li>
 *   <li>{@link #spawnExecutionExceptionMapsToSpawnFailed} — spawn execution 抛异常
 *       → 诚实 SPAWN_FAILED（非静默跳过）</li>
 *   <li>{@link #preferMemberRoleSpecOverLeadSpec} — 优先 MEMBER role 的 memberSpec
 *       作 spawn 目标（决策2，与 daemon bound-member 策略对称）</li>
 *   <li>{@link #fallbackAnySpecWhenNoMemberRole} — 无 MEMBER role 时回退任意
 *       memberSpec（决策2）</li>
 * </ul>
 */
public class TestMemberSpawner {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Recording agent engine — captures the spawned AgentMessageRequest and
    // returns a configurable AgentExecutionResult (or throws).
    // ========================================================================

    static final class RecordingAgentEngine implements IAgentEngine {
        final List<AgentMessageRequest> capturedRequests = Collections.synchronizedList(new ArrayList<>());
        final AtomicReference<AgentExecutionResult> nextResult = new AtomicReference<>();
        final AtomicReference<RuntimeException> nextException = new AtomicReference<>();

        RecordingAgentEngine returning(AgentExecutionResult result) {
            this.nextResult.set(result);
            this.nextException.set(null);
            return this;
        }

        RecordingAgentEngine throwing(RuntimeException ex) {
            this.nextException.set(ex);
            this.nextResult.set(null);
            return this;
        }

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            capturedRequests.add(request);
            RuntimeException ex = nextException.get();
            if (ex != null) {
                CompletableFuture<AgentExecutionResult> f = new CompletableFuture<>();
                f.completeExceptionally(ex);
                return f;
            }
            AgentExecutionResult result = nextResult.get();
            if (result == null) {
                // Default to a successful completed result.
                result = new AgentExecutionResult(AgentExecStatus.completed, "ok",
                        Collections.emptyList(), 1, 10L, 1L, null);
            }
            return CompletableFuture.completedFuture(result);
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static Team createTeamWithSpec(String leadAgent, String memberName,
                                           String memberAgentModel, MemberRole memberRole) {
        TeamSpec spec = new TeamSpec("spawn-team", "d", leadAgent,
                Arrays.asList(
                        new TeamMemberSpec("lead", leadAgent, MemberRole.LEAD),
                        new TeamMemberSpec(memberName, memberAgentModel, memberRole)),
                0);
        return new InMemoryTeamManager().createTeam(spec);
    }

    private static Team createTeamWithoutMemberSpecs(String leadAgent) {
        TeamSpec spec = new TeamSpec("empty-team", "d", leadAgent,
                Collections.singletonList(
                        new TeamMemberSpec("lead", leadAgent, MemberRole.LEAD)),
                0);
        return new InMemoryTeamManager().createTeam(spec);
    }

    private static TeamTask createTask(String teamId, String subject) {
        return new InMemoryTeamTaskStore().createTask(teamId, subject, "desc-" + subject,
                Collections.emptyList(), "daemon-session");
    }

    private static SpawnMemberRequest spawnRequest(Team team, TeamTask task) {
        return new SpawnMemberRequest(team, task, "daemon-session-id");
    }

    // ========================================================================
    // 1. NoOp default — explicit NO_SPAWN (zero regression, No Silent No-Op)
    // ========================================================================

    @Test
    void noOpReturnsNoSpawnExplicit() {
        NoOpMemberSpawner noOp = NoOpMemberSpawner.noOp();
        Team team = createTeamWithSpec("lead-agent", "worker", "worker-agent", MemberRole.MEMBER);
        TeamTask task = createTask(team.getTeamId(), "A");

        SpawnMemberResult result = noOp.spawnMember(spawnRequest(team, task));

        assertNotNull(result, "NoOp never returns null (Minimum Rules #24)");
        assertEquals(SpawnMemberResult.Status.NO_SPAWN, result.getStatus(),
                "NoOp explicit NO_SPAWN — auto-spawn not enabled");
        assertNotNull(result.getReason(), "NO_SPAWN carries a reason (not a silent null)");
        assertNull(result.getExecutionResult(),
                "NO_SPAWN does not carry an execution result");
        assertNull(result.getSpawnedAgentName());
        assertNull(result.getSpawnedSessionId());
    }

    // ========================================================================
    // 2. functional spawner resolves agentModel from memberSpec
    // ========================================================================

    @Test
    void functionalSpawnerResolvesAgentModelFromMemberSpec() {
        RecordingAgentEngine engine = new RecordingAgentEngine().returning(
                new AgentExecutionResult(AgentExecStatus.completed, "ok", Collections.emptyList(),
                        1, 10L, 1L, null));
        DefaultMemberSpawner spawner = new DefaultMemberSpawner(engine);

        Team team = createTeamWithSpec("lead-agent", "worker", "worker-agent-model",
                MemberRole.MEMBER);
        TeamTask task = createTask(team.getTeamId(), "A");

        SpawnMemberResult result = spawner.spawnMember(spawnRequest(team, task));

        assertEquals(SpawnMemberResult.Status.DISPATCHED, result.getStatus());
        assertEquals("worker-agent-model", result.getSpawnedAgentName(),
                "spawned agent name = memberSpec.agentModel (decision 2)");

        assertEquals(1, engine.capturedRequests.size(),
                "execute was invoked exactly once");
        AgentMessageRequest captured = engine.capturedRequests.get(0);
        assertEquals("worker-agent-model", captured.getAgentName(),
                "AgentMessageRequest.agentName = resolved agentModel (not hardcoded)");
    }

    // ========================================================================
    // 3. Wiring verification #23 — functional spawner actually invokes
    //    IAgentEngine.execute (not just returns a placeholder)
    // ========================================================================

    @Test
    void functionalSpawnerInvokesAgentEngineExecute() {
        RecordingAgentEngine engine = new RecordingAgentEngine().returning(
                new AgentExecutionResult(AgentExecStatus.completed, "ok", Collections.emptyList(),
                        1, 10L, 1L, null));
        DefaultMemberSpawner spawner = new DefaultMemberSpawner(engine);

        Team team = createTeamWithSpec("lead-agent", "worker", "worker-agent", MemberRole.MEMBER);
        TeamTask task = createTask(team.getTeamId(), "A");

        spawner.spawnMember(spawnRequest(team, task));

        assertEquals(1, engine.capturedRequests.size(),
                "execute WAS invoked (not a placeholder return)");
    }

    // ========================================================================
    // 4. Task subject + description become the spawned agent's prompt
    //    (not hardcoded)
    // ========================================================================

    @Test
    void functionalSpawnerUsesTaskSubjectAndDescriptionAsPrompt() {
        RecordingAgentEngine engine = new RecordingAgentEngine().returning(
                new AgentExecutionResult(AgentExecStatus.completed, "ok", Collections.emptyList(),
                        1, 10L, 1L, null));
        DefaultMemberSpawner spawner = new DefaultMemberSpawner(engine);

        Team team = createTeamWithSpec("lead-agent", "worker", "worker-agent", MemberRole.MEMBER);
        // Create a task with a distinctive subject + description.
        TeamTask task = new InMemoryTeamTaskStore().createTask(
                team.getTeamId(), "SubjectXYZ", "BodyABC", Collections.emptyList(),
                "daemon-session");

        spawner.spawnMember(spawnRequest(team, task));

        AgentMessageRequest captured = engine.capturedRequests.get(0);
        assertTrue(captured.getUserMessage().contains("SubjectXYZ"),
                "spawned prompt includes task subject");
        assertTrue(captured.getUserMessage().contains("BodyABC"),
                "spawned prompt includes task description");
        assertTrue(captured.getUserMessage().startsWith("Execute team task: "),
                "spawned prompt has the same shape as bound-member dispatch");
    }

    // ========================================================================
    // 5. Fresh session id per spawn (per-task spawn, no reuse)
    // ========================================================================

    @Test
    void functionalSpawnerUsesFreshSessionPerSpawn() {
        RecordingAgentEngine engine = new RecordingAgentEngine().returning(
                new AgentExecutionResult(AgentExecStatus.completed, "ok", Collections.emptyList(),
                        1, 10L, 1L, null));
        DefaultMemberSpawner spawner = new DefaultMemberSpawner(engine);

        Team team = createTeamWithSpec("lead-agent", "worker", "worker-agent", MemberRole.MEMBER);
        TeamTask task = createTask(team.getTeamId(), "A");

        SpawnMemberResult r1 = spawner.spawnMember(spawnRequest(team, task));
        SpawnMemberResult r2 = spawner.spawnMember(spawnRequest(team, task));

        assertNotNull(r1.getSpawnedSessionId());
        assertNotNull(r2.getSpawnedSessionId());
        assertFalse(r1.getSpawnedSessionId().equals(r2.getSpawnedSessionId()),
                "each spawn gets a fresh session id (per-task spawn)");
        assertTrue(r1.getSpawnedSessionId().startsWith("spawned-"),
                "spawned session id has the spawned- prefix");
    }

    // ========================================================================
    // 6. Spawned execution carries teamTaskId / teamId / daemonSessionId
    //    metadata (audit traceability)
    // ========================================================================

    @Test
    void functionalSpawnerCarriesAuditMetadata() {
        RecordingAgentEngine engine = new RecordingAgentEngine().returning(
                new AgentExecutionResult(AgentExecStatus.completed, "ok", Collections.emptyList(),
                        1, 10L, 1L, null));
        DefaultMemberSpawner spawner = new DefaultMemberSpawner(engine);

        Team team = createTeamWithSpec("lead-agent", "worker", "worker-agent", MemberRole.MEMBER);
        TeamTask task = createTask(team.getTeamId(), "A");

        spawner.spawnMember(spawnRequest(team, task));

        AgentMessageRequest captured = engine.capturedRequests.get(0);
        Map<String, Object> md = captured.getMetadata();
        assertEquals(task.getTaskId(), md.get("teamTaskId"),
                "spawned execution carries teamTaskId (audit)");
        assertEquals(team.getTeamId(), md.get("teamId"),
                "spawned execution carries teamId (audit)");
        assertEquals("daemon-session-id", md.get("daemonSessionId"),
                "spawned execution carries daemonSessionId (audit)");
        assertEquals("worker", md.get("spawnedFromMemberSpec"),
                "spawned execution records which memberSpec it was spawned from");
    }

    // ========================================================================
    // 7. No declarative memberSpec → honest NO_SPAWN (No Silent No-Op #24)
    // ========================================================================

    @Test
    void noMemberSpecReturnsHonestNoSpawn() {
        RecordingAgentEngine engine = new RecordingAgentEngine().returning(
                new AgentExecutionResult(AgentExecStatus.completed, "ok", Collections.emptyList(),
                        1, 10L, 1L, null));
        DefaultMemberSpawner spawner = new DefaultMemberSpawner(engine);

        // Team with only the lead spec — no MEMBER-role spec, only LEAD.
        // resolveSpawnTarget falls back to the LEAD spec, so this is NOT a
        // no-memberSpec scenario. To test no-memberSpec we need an empty
        // memberSpecs list, which TeamSpec forbids via leadAgentName being
        // required to be in the spec... actually TeamSpec allows empty
        // memberSpecs (leadAgentName is just a name, not required to be in
        // the list). Construct an empty-spec team.
        TeamSpec emptySpec = new TeamSpec("truly-empty", "d", "lead-agent",
                Collections.emptyList(), 0);
        Team team = new InMemoryTeamManager().createTeam(emptySpec);
        TeamTask task = createTask(team.getTeamId(), "A");

        SpawnMemberResult result = spawner.spawnMember(spawnRequest(team, task));

        assertEquals(SpawnMemberResult.Status.NO_SPAWN, result.getStatus(),
                "empty memberSpecs = honest NO_SPAWN (cannot materialise an undeclared member)");
        assertNotNull(result.getReason(), "NO_SPAWN carries a reason");
        assertEquals(0, engine.capturedRequests.size(),
                "execute NOT invoked when there is no memberSpec to spawn from");
    }

    // ========================================================================
    // 8. Blank agentModel → honest NO_SPAWN (defensive)
    // ========================================================================

    @Test
    void blankAgentModelReturnsHonestNoSpawn() {
        // TeamMemberSpec constructor only null-checks agentModel, so a blank
        // string is permitted. The spawner defensively treats blank as
        // unspawnable — exercised here by constructing a spec with a blank
        // agentModel memberSpec.
        TeamSpec blankSpec = new TeamSpec("blank-model-team", "d", "lead-agent",
                Collections.singletonList(
                        new TeamMemberSpec("blank", "  ", MemberRole.MEMBER)),
                0);
        Team team = new InMemoryTeamManager().createTeam(blankSpec);
        TeamTask task = createTask(team.getTeamId(), "A");

        RecordingAgentEngine engine = new RecordingAgentEngine();
        DefaultMemberSpawner spawner = new DefaultMemberSpawner(engine);

        SpawnMemberResult result = spawner.spawnMember(spawnRequest(team, task));

        assertEquals(SpawnMemberResult.Status.NO_SPAWN, result.getStatus(),
                "blank agentModel = honest NO_SPAWN (defensive — cannot spawn unnamed agent)");
        assertNotNull(result.getReason());
        assertEquals(0, engine.capturedRequests.size(),
                "execute NOT invoked when agentModel is blank");
    }

    // ========================================================================
    // 9. Spawn execution success → DISPATCHED with the wrapped result
    // ========================================================================

    @Test
    void spawnExecutionSuccessMapsToDispatchedWithResult() {
        AgentExecutionResult execResult = new AgentExecutionResult(
                AgentExecStatus.completed, "ok", Collections.emptyList(),
                1, 10L, 1L, null);
        RecordingAgentEngine engine = new RecordingAgentEngine().returning(execResult);
        DefaultMemberSpawner spawner = new DefaultMemberSpawner(engine);

        Team team = createTeamWithSpec("lead-agent", "worker", "worker-agent", MemberRole.MEMBER);
        TeamTask task = createTask(team.getTeamId(), "A");

        SpawnMemberResult result = spawner.spawnMember(spawnRequest(team, task));

        assertEquals(SpawnMemberResult.Status.DISPATCHED, result.getStatus());
        assertSame(execResult, result.getExecutionResult(),
                "DISPATCHED wraps the engine's AgentExecutionResult (not a placeholder)");
    }

    // ========================================================================
    // 10. Spawn execution non-completed → still DISPATCHED (spawner does NOT
    //     interpret status — daemon decides complete/abandon, decision 4)
    // ========================================================================

    @Test
    void spawnExecutionNonCompletedStillDispatchedDaemonInterpretsStatus() {
        AgentExecutionResult execResult = new AgentExecutionResult(
                AgentExecStatus.failed, null, Collections.emptyList(),
                1, 10L, 1L, "boom");
        RecordingAgentEngine engine = new RecordingAgentEngine().returning(execResult);
        DefaultMemberSpawner spawner = new DefaultMemberSpawner(engine);

        Team team = createTeamWithSpec("lead-agent", "worker", "worker-agent", MemberRole.MEMBER);
        TeamTask task = createTask(team.getTeamId(), "A");

        SpawnMemberResult result = spawner.spawnMember(spawnRequest(team, task));

        assertEquals(SpawnMemberResult.Status.DISPATCHED, result.getStatus(),
                "non-completed execution is still DISPATCHED — spawner does not interpret status");
        assertSame(execResult, result.getExecutionResult(),
                "the wrapped result (with failed status) is passed through for the daemon to interpret");
    }

    // ========================================================================
    // 11. Spawn execution exception → honest SPAWN_FAILED (No Silent No-Op)
    // ========================================================================

    @Test
    void spawnExecutionExceptionMapsToSpawnFailed() {
        RecordingAgentEngine engine = new RecordingAgentEngine().throwing(
                new RuntimeException("spawn-agent-boom"));
        DefaultMemberSpawner spawner = new DefaultMemberSpawner(engine);

        Team team = createTeamWithSpec("lead-agent", "worker", "worker-agent", MemberRole.MEMBER);
        TeamTask task = createTask(team.getTeamId(), "A");

        SpawnMemberResult result = spawner.spawnMember(spawnRequest(team, task));

        assertEquals(SpawnMemberResult.Status.SPAWN_FAILED, result.getStatus(),
                "spawn execution exception = honest SPAWN_FAILED (not silent skip)");
        assertNotNull(result.getReason());
        assertTrue(result.getReason().contains("spawn-agent-boom"),
                "SPAWN_FAILED reason surfaces the cause message");
        assertNull(result.getExecutionResult());
        assertEquals(1, engine.capturedRequests.size(),
                "execute WAS invoked (then threw) — not a silent pre-check failure");
    }

    // ========================================================================
    // 12. Prefer MEMBER-role spec over LEAD (decision 2 — symmetric to daemon)
    // ========================================================================

    @Test
    void preferMemberRoleSpecOverLeadSpec() {
        RecordingAgentEngine engine = new RecordingAgentEngine().returning(
                new AgentExecutionResult(AgentExecStatus.completed, "ok", Collections.emptyList(),
                        1, 10L, 1L, null));
        DefaultMemberSpawner spawner = new DefaultMemberSpawner(engine);

        Team team = createTeamWithSpec("lead-agent", "worker", "worker-agent-model",
                MemberRole.MEMBER);
        TeamTask task = createTask(team.getTeamId(), "A");

        spawner.spawnMember(spawnRequest(team, task));

        AgentMessageRequest captured = engine.capturedRequests.get(0);
        assertEquals("worker-agent-model", captured.getAgentName(),
                "MEMBER-role spec preferred over LEAD-role spec");
    }

    // ========================================================================
    // 13. Fallback any spec when no MEMBER role (decision 2)
    // ========================================================================

    @Test
    void fallbackAnySpecWhenNoMemberRole() {
        RecordingAgentEngine engine = new RecordingAgentEngine().returning(
                new AgentExecutionResult(AgentExecStatus.completed, "ok", Collections.emptyList(),
                        1, 10L, 1L, null));
        DefaultMemberSpawner spawner = new DefaultMemberSpawner(engine);

        // Team with only a LEAD spec — fallback should resolve the LEAD.
        TeamSpec leadOnlySpec = new TeamSpec("lead-only", "d", "lead-agent",
                Collections.singletonList(
                        new TeamMemberSpec("lead", "lead-agent-model", MemberRole.LEAD)),
                0);
        Team team = new InMemoryTeamManager().createTeam(leadOnlySpec);
        TeamTask task = createTask(team.getTeamId(), "A");

        SpawnMemberResult result = spawner.spawnMember(spawnRequest(team, task));

        assertEquals(SpawnMemberResult.Status.DISPATCHED, result.getStatus(),
                "fallback resolves the LEAD spec when no MEMBER role exists");
        assertEquals("lead-agent-model", result.getSpawnedAgentName(),
                "fallback target is the LEAD spec's agentModel");
    }
}
