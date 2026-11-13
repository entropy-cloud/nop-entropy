package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
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
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.initialize.CoreInitialization;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.impl.TaskFlowManagerImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2519 (C7-022 / 裁定⑤) focused regression — the overall-deadline
 * convergence cancel, TWO INDEPENDENT LAYERS (design §5.1 red⑤):
 *
 * <ul>
 *   <li><b>Layer 1 — 图已断</b> (existing AR-3 face, kept as the layer
 *       discriminator): the overall deadline cancels the underlying nop-task
 *       graph ({@code taskRt.cancel}, cancel reason "timeout").</li>
 *   <li><b>Layer 2 — 会话已终态</b> (the plan 2519 increment): ON TOP of the
 *       graph cancel, every spawned member session registered in the flow's
 *       recorder receives {@code cancelSession(forced=true)} — the graph
 *       cancel alone does NOT terminate member sessions already submitted to
 *       the engine (defect state: the convergence sweep removed → the member
 *       session keeps running as a zombie while layer 1 still passes — RED
 *       on layer 2 only, which is exactly the increment discrimination).</li>
 * </ul>
 */
public class TestTeamFlowDeadlineConvergenceCancel {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Spawn spawner: resolves instantly, returns one spawned session per
     * member (the fan-out needs ≥2 targets to take the SpawnMemberFanOutStep
     * path whose reduce chain the overall deadline bounds).
     */
    static final class InstantSpawner implements IMemberSpawner {
        final AtomicInteger calls = new AtomicInteger();

        static String spawnedSessionId(String memberName) {
            return "spawned-" + memberName;
        }

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            calls.incrementAndGet();
            String memberName = request.getTarget() != null
                    ? request.getTarget().getMemberName() : "unknown";
            return SpawnMemberResult.dispatched(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok",
                            Collections.emptyList(), 1, 1L, 1L, null),
                    memberName + "-agent-model",
                    spawnedSessionId(memberName));
        }
    }

    /**
     * Engine that records cancelSession calls and drives the session state —
     * the observable face of the session-level convergence cancel (layer 2).
     */
    static final class ConvergenceRecordingEngine implements IAgentEngine {
        final Map<String, String> sessionState = new ConcurrentHashMap<>();
        final List<String> cancelledSessionIds = Collections.synchronizedList(new ArrayList<>());
        final List<Boolean> forcedFlags = Collections.synchronizedList(new ArrayList<>());

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            sessionState.put(request.getSessionId(), "running");
            return CompletableFuture.completedFuture(new AgentExecutionResult(
                    AgentExecStatus.completed, "ok", Collections.emptyList(), 1, 1L, 1L, null));
        }

        @Override
        public CompletableFuture<Void> cancelSession(String sessionId, String reason, boolean forced) {
            cancelledSessionIds.add(sessionId);
            forcedFlags.add(forced);
            sessionState.put(sessionId, "cancelled");
            return CompletableFuture.completedFuture(null);
        }
    }

    /** Reduction whose reduce never settles — the node-level overall-deadline hang. */
    static final class HangingReduction implements IReductionStrategy {
        @Override
        public CompletableFuture<Boolean> reduce(List<MemberExecOutcome> outcomes,
                                                 ReductionContext context) {
            return new CompletableFuture<>();
        }

        @Override
        public String name() {
            return "hanging";
        }
    }

    /** Records the task runtime the orchestrator drives (layer-1 cancel face). */
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

    /**
     * Overall deadline breach: layer 1 (graph cancelled) AND layer 2 (the
     * spawned member session cancelled + terminal). Defect state (convergence
     * sweep removed): layer 1 passes, layer 2 RED — the zombie member session.
     */
    @Test
    void overallDeadlineCancelsGraphAndSpawnedMemberSessions() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamSpec spec = new TeamSpec("convergence-team", "test", "lead-agent",
                List.of(
                        new TeamMemberSpec("m1", "m1-agent", MemberRole.MEMBER),
                        new TeamMemberSpec("m2", "m2-agent", MemberRole.MEMBER)), 0);
        Team team = mgr.createTeam(spec);
        String taskId = store.createTask(team.getTeamId(), "convergence-task", "desc",
                Collections.emptyList(), "lead-session").getTaskId();

        InstantSpawner spawner = new InstantSpawner();
        ConvergenceRecordingEngine engine = new ConvergenceRecordingEngine();
        RecordingTaskFlowManager flowManager = new RecordingTaskFlowManager();

        List<TeamMemberSpec> spawnSpecs = new ArrayList<>();
        for (TeamMemberSpec s : team.getSpec().getMemberSpecs()) {
            if (!s.getMemberName().equals("lead")) {
                spawnSpecs.add(s);
            }
        }
        MemberDispatchPlan plan = new MemberDispatchPlan(team,
                store.getTask(taskId).orElseThrow(),
                List.of(DispatchTarget.spawn(spawnSpecs.get(0)),
                        DispatchTarget.spawn(spawnSpecs.get(1))),
                new HangingReduction());

        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(
                engine, store, mgr, flowManager, spawner, null, 300L);
        orchestrator.setTaskMemberRouter((t, task) -> plan);
        ExecutorService spawnExecutor = Executors.newFixedThreadPool(2, new ThreadFactory() {
            final AtomicInteger c = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "convergence-test-spawn-worker-" + c.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
        orchestrator.setSpawnStepExecutor(spawnExecutor);

        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(team.getTeamId())
                    .get(10, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis();
            assertTrue(elapsed > 0);

            assertFalse(result.isSuccess(),
                    "a never-settling node must fail the flow at the overall deadline");

            // ---- Layer 1 (图已断 — AR-3 face): the graph runtime is cancelled.
            assertEquals(1, flowManager.createdRuntimes.size(),
                    "exactly one task runtime drives the flow");
            ITaskRuntime taskRt = flowManager.createdRuntimes.get(0);
            assertTrue(taskRt.isCancelled(),
                    "layer 1: the underlying task graph must be cancelled at the overall deadline");
            assertEquals("timeout", taskRt.getCancelReason(),
                    "layer 1: the graph cancel carries the timeout reason");

            // ---- Layer 2 (会话已终态 — plan 2519 increment): EVERY spawned
            // member session received cancelSession(forced=true) and reached
            // a terminal state. Defect state (convergence sweep removed):
            // this layer is RED while layer 1 still passes.
            assertEquals(2, spawner.calls.get(), "the fan-out spawned both members");
            for (String memberName : List.of("m1", "m2")) {
                String spawnedId = InstantSpawner.spawnedSessionId(memberName);
                assertTrue(engine.cancelledSessionIds.contains(spawnedId),
                        "layer 2: the spawned member session " + spawnedId
                                + " must be cancelled at the overall deadline convergence "
                                + "(graph cancel alone leaves the session running — the "
                                + "C7-022 zombie defect — RED)");
                int idx = engine.cancelledSessionIds.indexOf(spawnedId);
                assertTrue(engine.forcedFlags.get(idx),
                        "layer 2: the convergence cancel is forced");
                assertEquals("cancelled", engine.sessionState.get(spawnedId),
                        "layer 2: the spawned member session must reach a terminal state "
                                + "(still running = zombie — RED)");
            }

            // Honest failure state: the hung task stays CLAIMED.
            assertEquals(TeamTaskStatus.CLAIMED, store.getTask(taskId).orElseThrow().getStatus(),
                    "a hung node must leave the task CLAIMED (not COMPLETED)");
        } finally {
            spawnExecutor.shutdownNow();
        }
    }
}
