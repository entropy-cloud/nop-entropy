package io.nop.ai.agent.team.scheduler;

import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.NoOpMemberSpawner;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.flow.ITaskMemberRouter;
import io.nop.ai.agent.team.flow.MemberDispatchOutcome;
import io.nop.ai.agent.team.flow.MemberDispatchPlan;
import io.nop.ai.agent.team.flow.MemberFanOutDispatcher;
import io.nop.ai.agent.team.flow.NoOpTaskMemberRouter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-task dispatch orchestration for the team-task scheduler daemon
 * (extracted from {@link TeamTaskSchedulerDaemon}, MA4.2-05). Owns the
 * per-task member router + shared fan-out + reduce + complete chain
 * ({@link MemberFanOutDispatcher}), the spawn-target executor pool and the
 * in-flight dispatch tracking queue.
 */
public class TaskDispatchCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(TeamTaskSchedulerDaemon.class);

    private ITaskMemberRouter taskMemberRouter;
    private final IAgentEngine agentEngine;
    private IMemberSpawner memberSpawner;
    private final ITeamTaskStore taskStore;
    private final String daemonSessionId;
    private final java.util.concurrent.ConcurrentLinkedQueue<java.util.concurrent.CompletableFuture<MemberDispatchOutcome>> inFlightDispatches =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    private Executor spawnStepExecutor;
    private ExecutorService ownedSpawnExecutor;

    public TaskDispatchCoordinator(IAgentEngine agentEngine,
                                   ITeamTaskStore taskStore,
                                   String daemonSessionId) {
        this.agentEngine = agentEngine;
        this.taskStore = taskStore;
        this.daemonSessionId = daemonSessionId;
        this.taskMemberRouter = NoOpTaskMemberRouter.noOp();
        this.memberSpawner = NoOpMemberSpawner.noOp();
    }

    public ITaskMemberRouter getTaskMemberRouter() {
        return taskMemberRouter;
    }

    public void setTaskMemberRouter(ITaskMemberRouter taskMemberRouter) {
        this.taskMemberRouter = taskMemberRouter != null ? taskMemberRouter : NoOpTaskMemberRouter.noOp();
    }

    public IMemberSpawner getMemberSpawner() {
        return memberSpawner;
    }

    public void setMemberSpawner(IMemberSpawner memberSpawner) {
        this.memberSpawner = memberSpawner != null ? memberSpawner : NoOpMemberSpawner.noOp();
    }

    public void setSpawnStepExecutor(Executor executor) {
        this.spawnStepExecutor = executor;
    }

    public Executor getSpawnStepExecutor() {
        return spawnStepExecutor;
    }

    public void shutdownOwnSpawnExecutor() {
        if (ownedSpawnExecutor != null) {
            ownedSpawnExecutor.shutdownNow();
            ownedSpawnExecutor = null;
        }
    }

    // ---- moved verbatim from TeamTaskSchedulerDaemon (MA4.2-05 split) ----
    /**
     * pool independent of the commonPool (plan 243 design 裁定 3, reused).
     */
    Executor resolveSpawnExecutor() {
        if (spawnStepExecutor != null) {
            return spawnStepExecutor;
        }
        if (ownedSpawnExecutor == null) {
            int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
            ThreadFactory factory = new SpawnWorkerThreadFactory();
            ownedSpawnExecutor = Executors.newFixedThreadPool(poolSize, factory);
        }
        return ownedSpawnExecutor;
    }
    /**
     * Daemon thread factory for the owned spawn pool (mirrors the orchestrator
     * naming so tests can assert the spawn worker ran off the calling thread /
     * off the commonPool).
     */
    private static final class SpawnWorkerThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ai-agent-daemon-spawn-worker-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
    public boolean awaitInFlightDispatches(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        for (CompletableFuture<MemberDispatchOutcome> f : inFlightDispatches) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return false;
            }
            try {
                f.get(remaining, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                // the dispatcher never completes exceptionally (it returns
                // a FAILED outcome), so this is a timeout / interruption —
                // continue awaiting the rest up to the deadline.
            }
        }
        return System.currentTimeMillis() <= deadline;
    }
    public DispatchTally dispatchClaimedTask(Team team, TeamTask routingTask, TeamTask claimedTask,
                                              String capturedTenant) {
        String taskId = routingTask.getTaskId();

        // dispatch time, non-executing (it never calls the engine nor the
        // spawner). NoOp shipped default → singleton plan = bound priority +
        // spawn fallback (line-for-line zero regression).
        //
        // the ready-CREATED snapshot with claimedBy=null). The router's
        // bound-priority inspects task.getClaimedBy() to detect a MEMBER
        // binding; the daemon's OWN claim (claimedBy=daemonSessionId on
        // claimedTask) is an internal ownership marker, NOT a member binding,
        // so routing on the claimed task would mis-detect a bound member and
        // dispatch to the engine with a null agentModel. The claim epoch
        // threaded into the completeTask CAS comes from claimedTask (below).
        MemberDispatchPlan plan;
        try {
            plan = taskMemberRouter.route(team, routingTask);
        } catch (RuntimeException e) {
            // A router that throws is a contract violation (the contract says
            // return an empty plan for the no-member case). Honest failure:
            // task stays CLAIMED, no fan-out fired.
            LOG.warn("TeamTaskSchedulerDaemon: taskMemberRouter threw for taskId={}, teamId={} — "
                    + "task left CLAIMED (honest failure)", taskId, team.getTeamId(), e);
            return DispatchTally.failedNoDispatch(taskId);
        }
        if (plan == null) {
            // Defensive: contract says never null. Treat as honest failure.
            LOG.warn("TeamTaskSchedulerDaemon: taskMemberRouter returned null for taskId={}, teamId={} — "
                    + "task left CLAIMED (router contract violation)", taskId, team.getTeamId());
            return DispatchTally.failedNoDispatch(taskId);
        }

        if (plan.isEmpty()) {
            // Honest failure: empty plan (no dispatchable member). The task
            // stays CLAIMED (recovery via plan 240 reclaim). No fan-out fired.
            LOG.warn("TeamTaskSchedulerDaemon: dispatch plan produced zero targets for taskId={}, "
                            + "teamId={} — task left CLAIMED (no dispatchable member; router={})",
                    taskId, team.getTeamId(), taskMemberRouter.getClass().getName());
            return DispatchTally.failedNoDispatch(taskId);
        }

        // Determine whether any spawn target is present (requires the
        // dedicated spawn executor). Bound-only plans do not need it.
        boolean hasSpawn = false;
        for (io.nop.ai.agent.team.flow.DispatchTarget t : plan.getTargets()) {
            if (t.isSpawn()) {
                hasSpawn = true;
                break;
            }
        }
        Executor spawnExecutor = hasSpawn ? resolveSpawnExecutor() : null;

        // Fire the shared fan-out + reduce + complete chain. The dispatcher
        // never throws — it returns a MemberDispatchOutcome (COMPLETED or
        // FAILED). For already-complete underlying futures the returned
        // future IS DONE at construction time and the chain (including
        // completeTask) has run synchronously.
        CompletableFuture<MemberDispatchOutcome> dispatched = MemberFanOutDispatcher.dispatch(
                claimedTask, team, plan.getTargets(), plan.getReductionStrategy(),
                agentEngine, memberSpawner, taskStore, daemonSessionId,
                spawnExecutor, capturedTenant);

        if (dispatched.isDone()) {
            // Synchronous resolution (already-complete futures). Record the
            // outcome immediately. join() is safe — the future is done and
            // the dispatcher never completes exceptionally.
            try {
                MemberDispatchOutcome outcome = dispatched.join();
                if (outcome.isCompleted()) {
                    return DispatchTally.completed(taskId);
                }
                LOG.warn("TeamTaskSchedulerDaemon: fan-out reduction failed for taskId={}, teamId={} — "
                                + "task left CLAIMED (recovery via plan 240 reclaim): {}",
                        taskId, team.getTeamId(), outcome.getCause().toString());
                return DispatchTally.failedAfterDispatch(taskId);
            } catch (RuntimeException e) {
                // Defensive: the dispatcher's exceptionally() guarantees this
                // never happens, but defend against an unexpected propagation.
                LOG.warn("TeamTaskSchedulerDaemon: dispatch future threw unexpectedly for taskId={} — "
                                + "task left CLAIMED",
                        taskId, e);
                return DispatchTally.failedAfterDispatch(taskId);
            }
        }

        // Genuinely async — do NOT block the scan thread. Track in-flight so
        // callers (tests / graceful shutdown) can await. The dispatcher's
        // chain performs the store transition (completeTask on success /
        // leave CLAIMED on failure) regardless of timing. Remove from the
        // in-flight queue once settled to keep the queue bounded across scans.
        inFlightDispatches.add(dispatched);
        dispatched.whenComplete((outcome, ex) -> {
            inFlightDispatches.remove(dispatched);
            if (ex != null) {
                LOG.warn("TeamTaskSchedulerDaemon: in-flight dispatch settled exceptionally for "
                                + "taskId={} — task left CLAIMED",
                        taskId, ex);
            } else if (outcome != null && !outcome.isCompleted()) {
                LOG.warn("TeamTaskSchedulerDaemon: in-flight fan-out reduction failed for taskId={} — "
                                + "task left CLAIMED (recovery via plan 240 reclaim): {}",
                        taskId, outcome.getCause().toString());
            }
        });
        // The task is CLAIMED and dispatched (in-flight). Its final
        // completed/failed outcome will be observed in a later scan's
        // idempotent already-COMPLETED path (on success) or reclaim (on
        // failure). No synchronous completed/failed counter increment here.
        return DispatchTally.inFlight();
    }
    static final class DispatchTally {
        final int completed;
        final int failed;
        final int abandoned;
        final int dispatched;
        final List<String> completedIds;
        final List<String> failedIds;
        final List<String> abandonedIds;

        private DispatchTally(int completed, int failed, int abandoned, int dispatched,
                              List<String> completedIds, List<String> failedIds,
                              List<String> abandonedIds) {
            this.completed = completed;
            this.failed = failed;
            this.abandoned = abandoned;
            this.dispatched = dispatched;
            this.completedIds = completedIds;
            this.failedIds = failedIds;
            this.abandonedIds = abandonedIds;
        }

        /** Fan-out fired + succeeded (CLAIMED → COMPLETED, observed sync). */
        static DispatchTally completed(String taskId) {
            return new DispatchTally(1, 0, 0, 1,
                    Collections.singletonList(taskId),
                    Collections.emptyList(), Collections.emptyList());
        }

        /** Fan-out fired but reduction failed sync (task LEFT IN CLAIMED). */
        static DispatchTally failedAfterDispatch(String taskId) {
            return new DispatchTally(0, 1, 0, 1,
                    Collections.emptyList(),
                    Collections.singletonList(taskId),
                    Collections.emptyList());
        }

        /** No fan-out fired (empty plan / router threw); task LEFT IN CLAIMED. */
        static DispatchTally failedNoDispatch(String taskId) {
            return new DispatchTally(0, 1, 0, 0,
                    Collections.emptyList(),
                    Collections.singletonList(taskId),
                    Collections.emptyList());
        }

        /** Fan-out fired but genuinely async (outcome observed in a later scan). */
        static DispatchTally inFlight() {
            return new DispatchTally(0, 0, 0, 1,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }
    }
}
