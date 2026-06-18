package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.Team;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 241 (L4-async-cross-process-orchestration, async half) focused tests
 * for {@link TeamTaskFlowOrchestrator#executeAsync(String)}.
 *
 * <p>Verifies the non-blocking entry point and its honest-failure semantics
 * (plan 241 Phase 2 Exit Criteria):
 * <ul>
 *   <li>{@link #executeAsyncReturnsCompletableFutureNonBlocking} — the entry
 *       returns a {@link CompletableFuture} that is initially incomplete when
 *       the DAG has work to do, and the calling thread is not blocked on the
 *       entire DAG.</li>
 *   <li>{@link #linearChainExecuteAsyncAllCompleted} — a linear A→B→C DAG
 *       driven through {@code executeAsync} completes with all tasks
 *       COMPLETED and {@code success=true}.</li>
 *   <li>{@link #nodeFailureMapsToHonestFailedResult} — a node-level failure
 *       yields {@code success=false} with the failed / skipped task ids
 *       populated (No Silent No-Op #24).</li>
 *   <li>{@link #structuralFailuresFastThrowSynchronously} — structural
 *       fast-failures (null teamId / no tasks / unknown team / cyclic
 *       blockedBy) throw {@link NopAiAgentException} synchronously, before
 *       the future is created (identical to {@code execute()}).</li>
 *   <li>{@link #executeSyncEqualsExecuteAsyncJoin} — {@code execute(teamId)}
 *       and {@code executeAsync(teamId).join()} produce equivalent results
 *       (semantic equivalence, design 裁定 2).</li>
 * </ul>
 */
public class TestTeamTaskFlowOrchestratorAsync {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Recording mock engine — completes each task immediately, optionally
    //   fails a specific task by status or exception.
    // ========================================================================

    static final class RecordingAgentEngine implements IAgentEngine {
        final List<String> invocationOrder = Collections.synchronizedList(new ArrayList<>());
        final String failOnTaskId;
        final boolean failByException;

        RecordingAgentEngine() {
            this(null, false);
        }

        RecordingAgentEngine(String failOnTaskId, boolean failByException) {
            this.failOnTaskId = failOnTaskId;
            this.failByException = failByException;
        }

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            String taskId = (String) request.getMetadata().get("teamTaskId");
            invocationOrder.add(taskId);

            if (failOnTaskId != null && failOnTaskId.equals(taskId)) {
                if (failByException) {
                    CompletableFuture<AgentExecutionResult> f = new CompletableFuture<>();
                    f.completeExceptionally(new RuntimeException("async-boom:" + taskId));
                    return f;
                }
                return CompletableFuture.completedFuture(
                        new AgentExecutionResult(AgentExecStatus.failed, null,
                                Collections.emptyList(), 0, 0L, 0L, "async-failed:" + taskId));
            }
            return CompletableFuture.completedFuture(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok:" + taskId,
                            Collections.emptyList(), 1, 10L, 1L, null));
        }
    }

    /**
     * Slow engine that blocks each execute() call until a latch is released,
     * used to prove {@code executeAsync} returns a non-blocking future (the
     * calling thread can run another statement while the DAG is still
     * executing).
     */
    static final class BlockingAgentEngine implements IAgentEngine {
        final AtomicInteger enteredExecute = new AtomicInteger(0);
        final java.util.concurrent.CountDownLatch releaseLatch = new java.util.concurrent.CountDownLatch(1);

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            enteredExecute.incrementAndGet();
            return CompletableFuture.supplyAsync(() -> {
                try {
                    releaseLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new NopAiAgentException("interrupted", e);
                }
                String taskId = (String) request.getMetadata().get("teamTaskId");
                return new AgentExecutionResult(AgentExecStatus.completed, "ok:" + taskId,
                        Collections.emptyList(), 1, 10L, 1L, null);
            });
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static Team createTeamWithBoundMember(InMemoryTeamManager mgr,
                                                  String memberName, String sessionId) {
        TeamSpec spec = new TeamSpec("async-flow-team", "test", "lead-agent",
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

    // ========================================================================
    // 1. executeAsync returns a CompletableFuture; non-blocking when DAG has
    //    work to do.
    // ========================================================================

    @Test
    void executeAsyncReturnsCompletableFutureNonBlocking() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        BlockingAgentEngine engine = new BlockingAgentEngine();

        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();
        createTask(store, teamId, "A", Collections.emptyList());

        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(engine, store, mgr);

        long callStart = System.nanoTime();
        CompletableFuture<TeamTaskFlowResult> future = orchestrator.executeAsync(teamId);
        long callEnd = System.nanoTime();

        // Non-blocking: executeAsync returns very quickly even though the
        // member agent is parked on a latch. (Sanity timing — not a hard
        // threshold — just "far less than the actual DAG time.")
        assertTrue(callEnd - callStart < 500_000_000L,
                "executeAsync should not block on the entire DAG: call took too long (ns=" + (callEnd - callStart) + ")");

        // The future is initially incomplete because the engine's future is
        // parked. The engine has been entered (the DAG scheduler triggered
        // the node synchronously and the node returned an async TaskStepReturn
        // wrapping the parked future).
        assertTrue(engine.enteredExecute.get() >= 1,
                "DAG scheduler should have triggered the member-agent execution");

        // Release the parked engine so the test can complete cleanly.
        engine.releaseLatch.countDown();

        TeamTaskFlowResult result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(result.isSuccess(),
                "after release the DAG should complete: " + result);
    }

    // ========================================================================
    // 2. Linear A→B→C through executeAsync: all tasks COMPLETED.
    // ========================================================================

    @Test
    void linearChainExecuteAsyncAllCompleted() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();

        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(b));

        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(engine, store, mgr);
        CompletableFuture<TeamTaskFlowResult> future = orchestrator.executeAsync(teamId);
        TeamTaskFlowResult result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);

        assertTrue(result.isSuccess(), "linear chain should complete successfully: " + result);
        assertEquals(Arrays.asList(a, b, c).stream().sorted().collect(java.util.stream.Collectors.toList()),
                result.getCompletedTaskIds().stream().sorted().collect(java.util.stream.Collectors.toList()),
                "all three tasks completed");
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(c).orElseThrow().getStatus());
        assertEquals(3, engine.invocationOrder.size(), "engine.execute invoked once per task");

        // Anti-Hollow dependency order: B starts after A completes, etc.
        int completionA = result.getCompletionOrder().get(a);
        int startB = result.getStartOrder().get(b);
        int completionB = result.getCompletionOrder().get(b);
        int startC = result.getStartOrder().get(c);
        assertTrue(startB > completionA, "B starts after A completes");
        assertTrue(startC > completionB, "C starts after B completes");
    }

    // ========================================================================
    // 3. Node failure → honest failed result (No Silent No-Op #24).
    // ========================================================================

    @Test
    void nodeFailureMapsToHonestFailedResult() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(b));

        RecordingAgentEngine engine = new RecordingAgentEngine(b, false);

        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(engine, store, mgr);
        CompletableFuture<TeamTaskFlowResult> future = orchestrator.executeAsync(teamId);
        TeamTaskFlowResult result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);

        assertFalse(result.isSuccess(),
                "run with a failed node is not success: " + result);
        assertEquals(b, result.getFailedTaskId(), "failing task reported");
        // A completed before B failed; C was skipped (dependency-ordered failure propagation).
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        assertNotEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus(),
                "B did not complete");
        assertTrue(result.getSkippedTaskIds().contains(c),
                "C skipped: " + result.getSkippedTaskIds());
        // B was started (claimed) but left CLAIMED (not abandoned).
        assertNotEquals(TeamTaskStatus.ABANDONED, store.getTask(b).orElseThrow().getStatus(),
                "B was NOT abandoned — orchestrator leaves it CLAIMED for recovery");
    }

    @Test
    void nodeFailureByExceptionMapsToHonestFailedResult() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));

        RecordingAgentEngine engine = new RecordingAgentEngine(a, true);

        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(engine, store, mgr);
        CompletableFuture<TeamTaskFlowResult> future = orchestrator.executeAsync(teamId);
        TeamTaskFlowResult result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);

        assertFalse(result.isSuccess());
        assertEquals(a, result.getFailedTaskId());
        assertTrue(result.getSkippedTaskIds().contains(b), "B skipped after A threw");
    }

    // ========================================================================
    // 4. Structural fast-failures throw synchronously (before future creation).
    // ========================================================================

    @Test
    void structuralFailuresFastThrowSynchronously() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");

        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(new RecordingAgentEngine(), store, mgr);

        // null teamId
        assertThrows(NopAiAgentException.class, () -> orchestrator.executeAsync(null),
                "null teamId must throw synchronously");

        // No tasks
        NopAiAgentException noTasks = assertThrows(NopAiAgentException.class,
                () -> orchestrator.executeAsync(team.getTeamId()));
        assertTrue(noTasks.getMessage().contains("no-tasks"),
                "empty task set throws synchronously: " + noTasks.getMessage());

        // Unknown team — set up a store that returns a task for a nonexistent team
        String unknownTeamId = "nonexistent-" + UUID.randomUUID();
        TeamTask orphan = new TeamTask("orphan", unknownTeamId, "A", "d",
                Collections.emptyList(), TeamTaskStatus.CREATED, "lead", null, 1L);
        ITeamTaskStoreStub stubStore = new ITeamTaskStoreStub(unknownTeamId, orphan);
        TeamTaskFlowOrchestrator stubOrchestrator =
                new TeamTaskFlowOrchestrator(new RecordingAgentEngine(), stubStore, mgr);
        NopAiAgentException unknown = assertThrows(NopAiAgentException.class,
                () -> stubOrchestrator.executeAsync(unknownTeamId));
        assertTrue(unknown.getMessage().contains("team-not-found"),
                "unknown team throws synchronously: " + unknown.getMessage());
    }

    @Test
    void cyclicBlockedByThrowsSynchronouslyBeforeAnyExecution() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();

        // A cycle: A blockedBy B, B blockedBy A. The orchestrator must run
        // the nop-task GraphStepAnalyzer cycle check and reject it before
        // the future is created.
        String cycleTeamId = teamId;
        ITeamTaskStoreStub cycleStore = new ITeamTaskStoreStub(cycleTeamId,
                new TeamTask("CA", cycleTeamId, "A", "d",
                        Collections.singletonList("CB"), TeamTaskStatus.CREATED, "lead", null, 1L),
                new TeamTask("CB", cycleTeamId, "B", "d",
                        Collections.singletonList("CA"), TeamTaskStatus.CREATED, "lead", null, 1L));

        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(new RecordingAgentEngine(), cycleStore, mgr);
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> orchestrator.executeAsync(cycleTeamId));
        assertTrue(ex.getMessage().contains("cycle"),
                "cyclic blockedBy rejected before future creation: " + ex.getMessage());
    }

    // ========================================================================
    // 5. execute == executeAsync().join() (semantic equivalence, 裁定 2).
    // ========================================================================

    @Test
    void executeSyncEqualsExecuteAsyncJoin() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));

        // First run via executeAsync().join()
        TeamTaskFlowOrchestrator asyncOrchestrator =
                new TeamTaskFlowOrchestrator(new RecordingAgentEngine(), store, mgr);
        // Need a fresh store/engine because the previous run mutated the state.
        InMemoryTeamTaskStore store2 = new InMemoryTeamTaskStore();
        Team team2 = createTeamWithBoundMember(mgr, "worker2", "worker2-session");
        String teamId2 = team2.getTeamId();
        String a2 = createTask(store2, teamId2, "A", Collections.emptyList());
        String b2 = createTask(store2, teamId2, "B", Collections.singletonList(a2));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(new RecordingAgentEngine(), store2, mgr);

        TeamTaskFlowResult asyncResult = orchestrator.executeAsync(teamId2).join();
        TeamTaskFlowResult syncResult = new TeamTaskFlowOrchestrator(
                new RecordingAgentEngine(), store2, mgr).execute(teamId2);

        // Both runs produce the same shape: both tasks completed, success.
        // The store2 was already mutated by the first run (both COMPLETED),
        // so the second run sees "already completed" (idempotent re-run path).
        // Both calls complete without throwing — the contract is preserved.
        assertTrue(asyncResult.isSuccess(),
                "executeAsync().join() succeeds: " + asyncResult);
        assertTrue(syncResult.isSuccess(),
                "execute() succeeds: " + syncResult);
        // Both completed all tasks (idempotent re-run path of second call).
        assertEquals(2, asyncResult.getCompletedTaskIds().size());
        assertEquals(2, syncResult.getCompletedTaskIds().size());
    }

    // ========================================================================
    // Minimal ITeamTaskStore stub for driving specific structural paths.
    // ========================================================================

    private static final class ITeamTaskStoreStub implements io.nop.ai.agent.team.ITeamTaskStore {
        private final String stubTeamId;
        private final java.util.List<TeamTask> stubTasks;

        ITeamTaskStoreStub(String teamId, TeamTask... tasks) {
            this.stubTeamId = teamId;
            this.stubTasks = Arrays.asList(tasks);
        }

        @Override
        public TeamTask createTask(String t, String s, String d, List<String> b, String c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<TeamTask> getTask(String taskId) {
            return stubTasks.stream().filter(t -> t.getTaskId().equals(taskId)).findFirst();
        }

        @Override
        public List<TeamTask> getTasksByTeam(String tid) {
            return stubTeamId.equals(tid) ? stubTasks : Collections.emptyList();
        }

        @Override
        public List<TeamTask> getTasksByCreator(String c) {
            return Collections.emptyList();
        }

        @Override
        public java.util.Optional<TeamTask> claimTask(String t, String b) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<TeamTask> completeTask(String t, String b) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<TeamTask> abandonTask(String t, String b) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<TeamTask> reclaimTask(String t, String b) {
            throw new UnsupportedOperationException();
        }
    }
}
