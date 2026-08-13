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
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.impl.TaskFlowManagerImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2026-08-12-2050-2 / AR-3 behavior tests: the orchestrator's overall
 * deadline must be DECOUPLED from the per-member timeout (overall =
 * {@code memberExecTimeoutMs × maxDepth} — decision recorded in the daily
 * log), and an overall deadline breach must cancel the underlying nop-task
 * graph via {@link ITaskRuntime#cancel} instead of only failing the result
 * future ("wait ends, graph keeps running" was the AR-3 defect).
 *
 * <ul>
 *   <li>{@link #legalMultiLayerFlowNotKilledByOverallDeadline} — a legal
 *       2-layer sequential DAG whose total duration exceeds one per-member
 *       deadline (but stays under overall) must complete successfully;
 *       the pre-AR-3 overall == per-member deadline would have killed it.</li>
 *   <li>{@link #overallTimeoutCancelsUnderlyingGraph} — a graph node whose
 *       reduce chain never settles (no per-member timeout covers it) must
 *       fail the flow within the overall deadline AND the underlying
 *       nop-task runtime must be cancelled (observable via the recording
 *       task-flow manager's runtime) — no zombie execution.</li>
 * </ul>
 */
public class TestTeamTaskFlowOrchestratorOverallTimeout extends AbstractMultiMemberFanOutTest {

    /**
     * Engine whose members complete after {@code workMs} of real work — a
     * legitimate member execution that stays under the per-member deadline.
     */
    static final class SlowEngine implements IAgentEngine {
        final long workMs;
        final AtomicInteger executeCount = new AtomicInteger();

        SlowEngine(long workMs) {
            this.workMs = workMs;
        }

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            executeCount.incrementAndGet();
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(workMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new AgentExecutionResult(AgentExecStatus.completed, "ok",
                        Collections.emptyList(), 1, 1L, 1L, null);
            });
        }
    }

    /** Engine that completes members instantly (never the hang source). */
    static final class FastEngine implements IAgentEngine {
        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            return CompletableFuture.completedFuture(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok",
                            Collections.emptyList(), 1, 1L, 1L, null));
        }
    }

    /**
     * A reduction strategy whose {@code reduce} never settles — the realistic
     * hang no per-member orTimeout covers (the members already completed; the
     * reduce/complete chain is stuck). This is exactly the node-level hang the
     * overall deadline backstop exists for.
     */
    static final class HangingReduction implements IReductionStrategy {
        @Override
        public CompletableFuture<Boolean> reduce(List<MemberExecOutcome> outcomes, ReductionContext context) {
            return new CompletableFuture<>();
        }

        @Override
        public String name() {
            return "hanging";
        }
    }

    /**
     * Task-flow manager that records every runtime it creates, so tests can
     * observe the orchestrator's {@code ITaskRuntime.cancel} call on the
     * overall-timeout path (the runtime is the graph's cancel token).
     */
    static final class RecordingTaskFlowManager extends TaskFlowManagerImpl {
        final List<ITaskRuntime> createdRuntimes = Collections.synchronizedList(new ArrayList<>());

        @Override
        public ITaskRuntime newTaskRuntime(ITask task, boolean saveState,
                                           IServiceContext svcCtx, IEvalScope scope) {
            ITaskRuntime rt = super.newTaskRuntime(task, saveState, svcCtx, scope);
            createdRuntimes.add(rt);
            return rt;
        }
    }

    @Test
    void legalMultiLayerFlowNotKilledByOverallDeadline() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamAndBindMembers(mgr, "overall-team-legal",
                new String[]{"m1", "m2"}, new String[]{"session-m1", "session-m2"});

        // Legal 2-layer sequential DAG: B blockedBy A. Each member does
        // 200ms of legitimate work; per-member deadline = 300ms, so each
        // layer stays legal. Total = ~400ms — MORE than one per-member
        // deadline, which the pre-AR-3 overall == per-member deadline would
        // have killed. Overall (300 × 2 = 600ms) must let it through.
        String taskA = createTask(store, team.getTeamId(), "legal-a", Collections.emptyList());
        String taskB = createTask(store, team.getTeamId(), "legal-b", Collections.singletonList(taskA));

        SlowEngine engine = new SlowEngine(200L);
        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(
                engine, store, mgr, null, null, null, 300L);

        long start = System.currentTimeMillis();
        TeamTaskFlowResult result = orchestrator.execute(team.getTeamId());
        long elapsedMs = System.currentTimeMillis() - start;

        assertTrue(result.isSuccess(),
                "a legal multi-layer flow must not be killed by the overall deadline");
        assertTrue(elapsedMs < 5000, "the legal flow must finish in bounded time, took " + elapsedMs + "ms");
        // The flow genuinely crossed one per-member deadline (it is not a
        // degenerate instant flow) — the discriminator for the decoupling.
        assertTrue(elapsedMs >= 250,
                "the 2-layer flow must take longer than one per-member deadline to prove decoupling, took "
                        + elapsedMs + "ms");
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(taskA).orElseThrow().getStatus(),
                "layer A must complete");
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(taskB).orElseThrow().getStatus(),
                "layer B must complete");
        assertTrue(engine.executeCount.get() >= 2, "both member executions must run");
    }

    @Test
    void overallTimeoutCancelsUnderlyingGraph() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamAndBindMembers(mgr, "overall-team-hang",
                new String[]{"m1", "m2"}, new String[]{"session-m1", "session-m2"});
        String taskId = createTask(store, team.getTeamId(), "hang-task", Collections.emptyList());

        // A 2-bound-target fan-out whose reduction never settles: the members
        // complete instantly (no per-member timeout fires), the node hangs in
        // the reduce chain — only the overall deadline can bound it.
        RecordingTaskFlowManager flowManager = new RecordingTaskFlowManager();
        MemberDispatchPlan plan = new MemberDispatchPlan(team, store.getTask(taskId).orElseThrow(),
                List.of(
                        DispatchTarget.bound("m1", "session-m1", agentModelOf(team, "m1")),
                        DispatchTarget.bound("m2", "session-m2", agentModelOf(team, "m2"))),
                new HangingReduction());
        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(
                new FastEngine(), store, mgr, flowManager, null, null, 300L);
        orchestrator.setTaskMemberRouter((t, task) -> plan);

        long start = System.currentTimeMillis();
        TeamTaskFlowResult result = orchestrator.executeAsync(team.getTeamId()).get(10, java.util.concurrent.TimeUnit.SECONDS);
        long elapsedMs = System.currentTimeMillis() - start;

        // The overall deadline (300ms × maxDepth=1) bounds the hang.
        assertFalse(result.isSuccess(), "a never-settling node must fail the flow at the overall deadline");
        assertTrue(elapsedMs < 5000, "the flow must fail bounded by the overall deadline, took " + elapsedMs + "ms");

        // Cancel semantics (AR-3): the underlying graph must be cancelled on
        // the timeout path — observable on the real runtime the orchestrator
        // drove through the recording task-flow manager.
        assertEquals(1, flowManager.createdRuntimes.size(),
                "the orchestrator must create exactly one task runtime");
        ITaskRuntime taskRt = flowManager.createdRuntimes.get(0);
        assertTrue(taskRt.isCancelled(),
                "the underlying task graph must be cancelled on the overall timeout (no zombie execution)");
        assertEquals("timeout", taskRt.getCancelReason(),
                "the cancellation must carry the timeout reason");

        // Honest failure state: the hung task stays CLAIMED (never COMPLETED).
        assertEquals(TeamTaskStatus.CLAIMED, store.getTask(taskId).orElseThrow().getStatus(),
                "a hung node must leave the task CLAIMED (not COMPLETED)");
    }
}
