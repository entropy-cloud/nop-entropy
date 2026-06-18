package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.DefaultTeamAclChecker;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.ITeamAclChecker;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.NoOpMemberSpawner;
import io.nop.ai.agent.team.NoOpTeamAclChecker;
import io.nop.ai.agent.team.NoOpTeamManager;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.json.JSON;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 241 (L4-async-cross-process-orchestration, async half) Anti-Hollow
 * wiring test for {@link TeamExecuteFlowExecutor}.
 *
 * <p>Verifies that the {@code team-execute-flow} LLM tool now consumes the
 * orchestrator's <b>real</b> {@code executeAsync(teamId)} entry point (plan
 * 241 wiring, design 裁定 6), rather than the pre-241 hollow pattern of
 * wrapping a sync {@code execute(teamId)} result in
 * {@code CompletableFuture.completedFuture(...)}.
 *
 * <p>Anti-Hollow #23 evidence: a recording engine that delays each execute()
 * call by a fixed amount is used. The tool's future must complete AFTER the
 * orchestrator's DAG future completes (not synchronously), and the engine
 * must be actually invoked. A spy counter on {@code IAgentEngine.execute}
 * proves the orchestrator's async path runs through the real engine.
 */
public class TestTeamExecuteFlowAsyncWiring {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Recording agent engine that counts execute() invocations and completes
     * each call after a small async delay (so a true-async wiring is
     * distinguishable from a sync-then-wrap pattern).
     */
    static final class RecordingAgentEngine implements IAgentEngine {
        final AtomicInteger executeCount = new AtomicInteger();
        final List<AgentMessageRequest> capturedRequests = Collections.synchronizedList(new ArrayList<>());

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            executeCount.incrementAndGet();
            capturedRequests.add(request);
            String taskId = (String) request.getMetadata().get("teamTaskId");
            // Real async: complete on a different thread after a small delay.
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new NopAiAgentException("interrupted", e);
                }
                return new AgentExecutionResult(AgentExecStatus.completed, "ok:" + taskId,
                        Collections.emptyList(), 1, 10L, 1L, null);
            });
        }
    }

    private static Team createTeamWithBoundMember(InMemoryTeamManager mgr,
                                                  String memberName, String sessionId) {
        TeamSpec spec = new TeamSpec("wiring-team", "test", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec(memberName, memberName + "-agent", MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), memberName, sessionId, "actor-" + memberName);
        return team;
    }

    private static String createTask(InMemoryTeamTaskStore store, String teamId,
                                     String subject, List<String> blockedBy) {
        return store.createTask(teamId, subject, "desc-" + subject, blockedBy, "lead-session").getTaskId();
    }

    private static AgentToolExecuteContext buildContext(IAgentEngine engine,
                                                       ITeamManager mgr,
                                                       ITeamTaskStore store,
                                                       String sessionId) {
        ITeamAclChecker checker = (mgr instanceof InMemoryTeamManager)
                ? new DefaultTeamAclChecker((InMemoryTeamManager) mgr)
                : NoOpTeamAclChecker.noOp();
        return new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                engine, null, sessionId, "test-agent",
                null, null, null, null,
                mgr, store, checker);
        // Note: the spawner is wire-at-consumer on the EXECUTOR (DD#3), not
        // on the context — set via TeamExecuteFlowExecutor.setMemberSpawner.
    }

    private static AiToolCall buildCall() {
        AiToolCall c = new AiToolCall();
        c.setId(1);
        c.setInput("{}");
        return c;
    }

    // ========================================================================
    // 1. The tool consumes the orchestrator's real executeAsync — the engine
    //    IS invoked, the future completes AFTER the DAG work, and the result
    //    reflects real DAG completion.
    // ========================================================================

    @Test
    void toolConsumesRealExecuteAsync() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();

        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));

        // Build the context with the CALLER session bound as a member of
        // the team so ACL allows (DefaultTeamAclChecker, MEMBER role →
        // WRITE for team-execute-flow).
        AgentToolExecuteContext ctx = buildContext(engine, mgr, store, "worker-session");

        TeamExecuteFlowExecutor executor = new TeamExecuteFlowExecutor();
        executor.setMemberSpawner(NoOpMemberSpawner.noOp());

        CompletableFuture<AiToolCallResult> future = executor
                .executeAsync(buildCall(), ctx)
                .toCompletableFuture();

        // The tool's future is initially incomplete (engine's future is parked).
        assertFalse(future.isDone(),
                "tool future must not be done synchronously — real async wiring "
                        + "(pre-241 hollow would wrap a completedFuture)");

        AiToolCallResult result = future.get(5, TimeUnit.SECONDS);

        // Anti-Hollow #23: the engine was actually invoked twice.
        assertEquals(2, engine.executeCount.get(),
                "engine.execute invoked exactly once per task (real orchestrator path)");

        // Result is mapped to the success body.
        assertEquals("success", result.getStatus(),
                "tool result status=success for a successful DAG: " + result);
        Object body = JSON.parse(result.getOutput().getBody());
        assertTrue(body instanceof Map, "body is a JSON object");
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyMap = (Map<String, Object>) body;
        assertEquals(Boolean.TRUE, bodyMap.get("success"),
                "body success=true for a successful DAG: " + bodyMap);

        // DAG really ran (tasks COMPLETED in the store, not skipped).
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus());
    }

    // ========================================================================
    // 2. Honest failure propagation: node failure → tool returns success
    //    status with success:false body (No Silent No-Op #24).
    // ========================================================================

    @Test
    void toolHonestFailurePropagation() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        // Engine that fails on a specific task.
        IAgentEngine engine = new IAgentEngine() {
            @Override
            public AgentMessageAck sendMessage(AgentMessageRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
                String taskId = (String) request.getMetadata().get("teamTaskId");
                return CompletableFuture.completedFuture(
                        new AgentExecutionResult(AgentExecStatus.failed, null,
                                Collections.emptyList(), 0, 0L, 0L, "failed:" + taskId));
            }
        };

        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        AgentToolExecuteContext ctx = buildContext(engine, mgr, store, "worker-session");

        TeamExecuteFlowExecutor executor = new TeamExecuteFlowExecutor();
        AiToolCallResult result = executor.executeAsync(buildCall(), ctx)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "tool result status=success so ReAct loop continues: " + result);
        Object body = JSON.parse(result.getOutput().getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyMap = (Map<String, Object>) body;
        assertEquals(Boolean.FALSE, bodyMap.get("success"),
                "body success=false for a failed DAG (honest failure, not silent): " + bodyMap);
        assertEquals(a, bodyMap.get("failedTaskId"),
                "failedTaskId reported in the body: " + bodyMap);
    }

    // ========================================================================
    // 3. Honest not-enabled / no-team / structural paths still hold.
    // ========================================================================

    @Test
    void toolNotEnabledStillHonest() throws Exception {
        // NoOp team manager — the tool must honestly report "not enabled"
        // and NOT invoke the orchestrator.
        RecordingAgentEngine engine = new RecordingAgentEngine();
        AgentToolExecuteContext ctx = buildContext(
                engine,
                NoOpTeamManager.noOp(),               // NoOp triggers not-enabled
                new InMemoryTeamTaskStore(),
                "any-session");

        TeamExecuteFlowExecutor executor = new TeamExecuteFlowExecutor();
        AiToolCallResult result = executor.executeAsync(buildCall(), ctx)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "not-enabled honest report has status=success: " + result);
        assertTrue(result.getOutput().getBody().contains("not enabled"),
                "body reports not-enabled honestly: " + result.getOutput().getBody());
        assertEquals(0, engine.executeCount.get(),
                "engine NOT invoked — orchestrator short-circuited (NoOp teamManager)");
    }
}
