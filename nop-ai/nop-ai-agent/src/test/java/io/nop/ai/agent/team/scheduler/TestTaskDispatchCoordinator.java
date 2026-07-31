package io.nop.ai.agent.team.scheduler;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.team.flow.ITaskMemberRouter;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.flow.MemberDispatchPlan;
import io.nop.ai.agent.team.flow.NoOpTaskMemberRouter;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamTask;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused tests for the MA4.2-05 extracted {@link TaskDispatchCoordinator}.
 */
public class TestTaskDispatchCoordinator {

    static class StubTaskStore implements ITeamTaskStore {
        @Override
        public TeamTask createTask(String teamId, String subject, String description,
                                   List<String> blockedBy, String createdBy) {
            return null;
        }

        @Override
        public List<TeamTask> getTasksByTeam(String teamId) {
            return List.of();
        }

        @Override
        public List<TeamTask> getTasksByCreator(String createdBy) {
            return List.of();
        }

        @Override
        public java.util.Optional<TeamTask> getTask(String taskId) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<TeamTask> claimTask(String taskId, String claimedBy) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<TeamTask> completeTask(String taskId, String completedBy, Long claimEpoch) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<TeamTask> abandonTask(String taskId, String abandonedBy, Long claimEpoch) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<TeamTask> reclaimTask(String taskId, String reclaimedBy) {
            return java.util.Optional.empty();
        }


    }

    static class StubEngine implements IAgentEngine {
        @Override
        public io.nop.ai.agent.engine.AgentMessageAck sendMessage(AgentMessageRequest request) {
            return null;
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            io.nop.ai.agent.engine.AgentExecutionContext ctx =
                    new io.nop.ai.agent.engine.AgentExecutionContext(
                            new io.nop.ai.agent.model.AgentModel());
            ctx.setStatus(io.nop.ai.agent.model.AgentExecStatus.completed);
            return CompletableFuture.completedFuture(AgentExecutionResult.fromContext(ctx));
        }

        @Override
        public void close() {
        }
    }

    @Test
    void coordinatorDefaultsToNoOpRouter() {
        TaskDispatchCoordinator coordinator = new TaskDispatchCoordinator(
                new StubEngine(), new StubTaskStore(), "daemon");
        assertTrue(coordinator.getTaskMemberRouter() instanceof NoOpTaskMemberRouter);
        assertNotNull(coordinator.getMemberSpawner());
        coordinator.setTaskMemberRouter(null);
        coordinator.setMemberSpawner(null);
        assertTrue(coordinator.getTaskMemberRouter() instanceof NoOpTaskMemberRouter);
    }

    @Test
    void dispatchClaimedTaskFailsNoDispatchOnRouterThrow() {
        TaskDispatchCoordinator coordinator = new TaskDispatchCoordinator(
                new StubEngine(), new StubTaskStore(), "daemon");
        coordinator.setTaskMemberRouter((team, task) -> {
            throw new IllegalStateException("boom");
        });
        TaskDispatchCoordinator.DispatchTally tally = coordinator.dispatchClaimedTask(
                team(), task("t1"), task("t1"), null);
        assertEquals(1, tally.failed);
        assertEquals(0, tally.dispatched);
        assertEquals(0, tally.completed);
    }

    @Test
    void dispatchClaimedTaskFailsNoDispatchOnEmptyPlan() {
        TaskDispatchCoordinator coordinator = new TaskDispatchCoordinator(
                new StubEngine(), new StubTaskStore(), "daemon");
        coordinator.setTaskMemberRouter((team, task) -> new MemberDispatchPlan(team, task,
                java.util.Collections.emptyList(), io.nop.ai.agent.team.flow.AllMustSucceedReduction.instance()));
        TaskDispatchCoordinator.DispatchTally tally = coordinator.dispatchClaimedTask(
                team(), task("t1"), task("t1"), null);
        assertEquals(1, tally.failed);
        assertEquals(0, tally.dispatched);
    }

    @Test
    void dispatchClaimedTaskCompletedSynchronouslyForSingleBoundTarget() {
        TaskDispatchCoordinator coordinator = new TaskDispatchCoordinator(
                new StubEngine(), new StubTaskStore(), "daemon");
        coordinator.setTaskMemberRouter((team, task) -> new MemberDispatchPlan(team, task,
                java.util.Collections.emptyList(), io.nop.ai.agent.team.flow.AllMustSucceedReduction.instance()));
        TaskDispatchCoordinator.DispatchTally tally = coordinator.dispatchClaimedTask(
                team(), task("t1"), task("t1"), null);
        // empty plan -> honest failure, task left CLAIMED (no fan-out fired)
        assertEquals(0, tally.dispatched);
        assertEquals(0, tally.completed);
    }

    @Test
    void awaitInFlightDispatchesReturnsTrueWhenNothingTracked() {
        TaskDispatchCoordinator coordinator = new TaskDispatchCoordinator(
                new StubEngine(), new StubTaskStore(), "daemon");
        assertTrue(coordinator.awaitInFlightDispatches(10));
    }

    @Test
    void setScanLeaseValidationOnDaemon() {
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                new StubEngine(), new StubTaskStore(), io.nop.ai.agent.team.NoOpTeamManager.noOp(),
                new NoOpScheduler());
        assertThrows(NopAiAgentException.class, () -> daemon.setScanLeaseMs(0));
        daemon.setScanLeaseMs(5000);
        assertEquals(5000, daemon.getScanLeaseMs());
    }

    private Team team() {
        return new Team("team-1",
                new io.nop.ai.agent.team.TeamSpec("team-1", null, "lead",
                        java.util.Collections.emptyList(), 1),
                new java.util.concurrent.ConcurrentHashMap<>(), io.nop.ai.agent.team.TeamStatus.ACTIVE,
                System.currentTimeMillis());
    }

    private TeamTask task(String id) {
        return new TeamTask(id, "team-1", "subject", "description",
                java.util.Collections.emptyList(), io.nop.ai.agent.team.TeamTaskStatus.CREATED,
                "creator", null, null, System.currentTimeMillis());
    }

    static final class NoOpScheduler implements IScheduledExecutor {
        @Override
        public java.util.concurrent.Future<?> scheduleWithFixedDelay(
                Runnable command, long initialDelay, long delay, TimeUnit unit) {
            return new java.util.concurrent.CompletableFuture<>();
        }

        @Override
        public <V> CompletableFuture<V> schedule(
                java.util.concurrent.Callable<V> callable, long delay, TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public java.util.concurrent.Future<?> scheduleAtFixedRate(
                Runnable command, long initialDelay, long period, TimeUnit unit) {
            return new java.util.concurrent.CompletableFuture<>();
        }

        @Override
        public void execute(Runnable command) {
        }

        @Override
        public void destroy() {
        }

        @Override
        public boolean isDestroyed() {
            return false;
        }

        @Override
        public String getName() {
            return "no-op-scheduler";
        }

        @Override
        public io.nop.commons.concurrent.executor.ThreadPoolConfig getConfig() {
            return null;
        }

        @Override
        public io.nop.commons.concurrent.executor.ThreadPoolStats stats() {
            return null;
        }

        @Override
        public <V> CompletableFuture<V> submit(java.util.concurrent.Callable<V> callable) {
            return new CompletableFuture<>();
        }

        @Override
        public <V> CompletableFuture<V> submit(Runnable task, V result) {
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public void refreshConfig() {
        }
    }
}
