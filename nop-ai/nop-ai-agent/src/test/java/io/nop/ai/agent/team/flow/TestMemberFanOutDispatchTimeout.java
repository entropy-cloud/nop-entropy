package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I3 R-2-3 regression: the team-flow fan-out per-member timeout.
 *
 * <p>A hanging member agent ({@code agentEngine.execute} never settles)
 * must NOT make the team task wait indefinitely: the per-member
 * {@code orTimeout(memberExecTimeoutMs)} in
 * {@link MemberFanOutDispatcher#executeBoundMember} fires, the child session
 * is cancelled (AUDIT-14-01 semantics, mirroring {@code CallAgentExecutor}),
 * and the member is reported as an honest failed outcome — the task stays
 * CLAIMED for the scheduler layer to reclaim.
 *
 * <p>Behavior-level verification (not just declaration presence): asserts
 * the runtime cancel call and the bounded completion window.
 */
public class TestMemberFanOutDispatchTimeout extends AbstractMultiMemberFanOutTest {

    /**
     * Engine whose {@code execute} never settles (hanging member) and which
     * records every {@code cancelSession} call.
     */
    static final class HangingEngine implements IAgentEngine {
        final List<String> cancelledSessions = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger executeCount = new AtomicInteger();
        final CountDownLatch hanging = new CountDownLatch(1);

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            executeCount.incrementAndGet();
            return new CompletableFuture<>();
        }

        @Override
        public CompletableFuture<Void> cancelSession(String sessionId, String reason, boolean forced) {
            cancelledSessions.add(sessionId);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Test
    void hangingMemberTimesOutCancelsSessionAndReportsHonestFailure() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        HangingEngine engine = new HangingEngine();

        Team team = createTeamAndBindMembers(mgr, "timeout-team",
                new String[]{"m1"}, new String[]{"session-m1"});
        String taskId = createTask(store, team.getTeamId(), "timeout-task", Collections.emptyList());
        TeamTask task = store.getTask(taskId).orElseThrow();

        // The fan-out steps claim first (CREATED → CLAIMED) then dispatch.
        java.util.Optional<TeamTask> claimed = store.claimTask(taskId, "timeout-test-session");
        assertTrue(claimed.isPresent(), "task must be claimable");
        task = claimed.get();

        MemberDispatchPlan plan = boundFanOutPlan(team, task, Collections.singletonList("m1"));

        // Per-member deadline: 150ms — well below any reasonable test window.
        long deadlineMs = 150L;
        long start = System.currentTimeMillis();
        MemberDispatchOutcome outcome = MemberFanOutDispatcher.dispatch(
                task, team, plan.getTargets(), plan.getReductionStrategy(),
                engine, io.nop.ai.agent.team.NoOpMemberSpawner.noOp(),
                store, "timeout-test-session", null, null, deadlineMs)
                .get(10, TimeUnit.SECONDS);
        long elapsedMs = System.currentTimeMillis() - start;

        // The dispatch must settle bounded by the per-member deadline (not
        // wait forever for the hanging engine future).
        assertTrue(elapsedMs < 5000,
                "dispatch must return bounded by the member timeout, took " + elapsedMs + "ms");
        assertTrue(engine.executeCount.get() >= 1,
                "the member engine execute must have been invoked");

        // Honest failure: the member did not complete → reduction fails →
        // outcome failed, task stays CLAIMED (scheduler reclaim surface).
        assertFalse(outcome.isCompleted(),
                "a timed-out member must produce a failed dispatch outcome");
        assertNotNull(outcome.getCause(),
                "the failed outcome must carry the root cause");
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

        // AUDIT-14-01 alignment: the child session must have been cancelled
        // so the underlying execution does not continue as a zombie.
        assertEquals(1, engine.cancelledSessions.size(),
                "the hung member session must be cancelled after the per-member timeout");
        assertEquals("session-m1", engine.cancelledSessions.get(0));

        TeamTask after = store.getTask(taskId).orElseThrow();
        assertEquals(TeamTaskStatus.CLAIMED, after.getStatus(),
                "a timed-out member must leave the task CLAIMED (not COMPLETED) for scheduler reclaim");
    }

    @Test
    void dispatchRejectsNonPositiveMemberTimeout() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamAndBindMembers(mgr, "timeout-team-2",
                new String[]{"m1"}, new String[]{"session-m1-2"});
        String taskId = createTask(store, team.getTeamId(), "timeout-task-2", Collections.emptyList());
        TeamTask task = store.getTask(taskId).orElseThrow();
        MemberDispatchPlan plan = boundFanOutPlan(team, task, Collections.singletonList("m1"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
                        MemberFanOutDispatcher.dispatch(
                                task, team, plan.getTargets(), plan.getReductionStrategy(),
                                new HangingEngine(), io.nop.ai.agent.team.NoOpMemberSpawner.noOp(),
                                store, "timeout-test-session-2", null, null, 0L),
                "a non-positive memberExecTimeoutMs must fail fast");
    }
}
