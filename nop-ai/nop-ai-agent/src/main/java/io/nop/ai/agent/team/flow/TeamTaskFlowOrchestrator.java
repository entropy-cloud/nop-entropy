package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.NoOpMemberSpawner;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamTask;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepExecution;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.impl.TaskFlowManagerImpl;
import io.nop.task.impl.TaskImpl;
import io.nop.task.step.AbstractTaskStep;
import io.nop.task.step.GraphTaskStep;
import io.nop.task.step.GraphTaskStep.GraphStepNode;
import io.nop.task.step.TaskStepExecution;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Dependency-ordered synchronous orchestrator that runs a team's task DAG
 * through the <b>real nop-task runtime</b> (design 裁定 3).
 *
 * <p>For a given team, the orchestrator:
 * <ol>
 *   <li>Loads the team's tasks from {@link ITeamTaskStore} (the single source
 *       of truth for team-task state — design 裁定 1).</li>
 *   <li>Builds a nop-task {@link GraphTaskStepModel} via
 *       {@link TeamTaskGraphBuilder} — this performs <b>real</b> cycle
 *       detection through nop-task's {@code GraphStepAnalyzer} (design
 *       裁定 2). Cyclic {@code blockedBy} is rejected before any execution.</li>
 *   <li>Constructs a runtime {@link GraphTaskStep} — nop-task's DAG scheduler
 *       — where each team task is one graph node whose {@code waitSteps}
 *       encode its {@code blockedBy} predecessors. Each node's step is a
 *       {@link MemberAgentTaskStep} that delegates to a bound member agent
 *       (design 裁定 4: consume already-bound members, never spawn).</li>
 *   <li>Wraps the graph as the main step of a nop-task {@link ITask}, creates
 *       a runtime via {@link ITaskFlowManager#newTaskRuntime}, and executes it
 *       synchronously ({@link io.nop.task.TaskStepReturn#syncGetOutputs()}).
 *       The nop-task scheduler guarantees a node runs only after all its
 *       {@code waitSteps} predecessors have completed successfully.</li>
 *   <li>Each successful node transitions its task CLAIMED → COMPLETED via
 *       {@link ITeamTaskStore#completeTask}; a failed node short-circuits the
 *       graph and cancels its successors (dependency-ordered failure
 *       propagation).</li>
 * </ol>
 *
 * <h2>Synthetic sink</h2>
 * A nop-task graph completes (resolves its future) when an <em>exit</em> node
 * finishes. A team-task set with multiple independent chains has multiple
 * natural sinks; without care the graph would terminate on the first sink and
 * cancel the rest. To guarantee <b>every</b> team task runs regardless of the
 * DAG shape, the orchestrator marks all real task nodes non-exit and appends
 * one synthetic {@link FlowSinkStep} that waits on every task and is the sole
 * exit. This cannot introduce a cycle (the sink has no successors).
 *
 * <h2>No Silent No-Op (Minimum Rules #24)</h2>
 * Empty task set, unknown team, cyclic {@code blockedBy}, and a node failure
 * all surface honestly: the first three throw {@link NopAiAgentException}
 * (structural fast-failures before any node runs); a node failure during the
 * graph run returns a {@link TeamTaskFlowResult} with
 * {@link TeamTaskFlowResult#isSuccess()} {@code false} and the failed /
 * skipped task ids populated (honest failure, never silent success).
 *
 * <h2>Auto-spawn of unbound-member nodes (plan 238)</h2>
 * When a team task node has no already-bound member, the orchestrator no
 * longer fast-fails at build time. Instead it selects a
 * {@link SpawnMemberAgentTaskStep} for that node, and at <b>node run time</b>
 * (when the nop-task DAG scheduler triggers the node, after its
 * {@code blockedBy} predecessors have completed) the step consults the
 * injected {@link IMemberSpawner} to spawn a member agent. The NoOp shipped
 * default spawner honestly declines ({@code NO_SPAWN}), which makes the node
 * throw at run time → {@code TeamTaskFlowResult{success=false}} (an honest
 * failure, intentionally replacing the pre-238 build-time throw — see plan
 * 238 decision 4). A functional spawner spawns and executes the member agent
 * (asynchronously offloaded to a dedicated executor since plan 243 — see
 * {@link SpawnMemberAgentTaskStep}), preserving DAG dependency order. The
 * spawner is wire-at-consumer injected into the orchestrator (the consumer),
 * null-safe → NoOp shipped default (plan 238 decision 5).
 *
 * <h2>Multi-member per-task fan-out (plan 244)</h2>
 * As of plan 244 ({@code L4-multi-member-per-task-routing}), the orchestrator
 * consults a pluggable {@link ITaskMemberRouter} at <b>graph build time</b>
 * to decide which member targets each node fans out to. The shipped
 * {@link NoOpTaskMemberRouter} default produces a singleton plan (bound
 * priority + spawn fallback), so a single-member team behaves line-for-line
 * identically to plans 233/238/241/243 (zero regression). A multi-member
 * router produces an N-target plan, and the orchestrator builds a fan-out
 * node step ({@link BoundMemberFanOutStep} / {@link SpawnMemberFanOutStep} /
 * {@link MixedMemberFanOutStep}) that runs N member agents concurrently and
 * reduces their results under the plan's {@link IReductionStrategy} (shipped
 * default {@link AllMustSucceedReduction}). An empty plan is an honest
 * failure (the orchestrator throws, the task stays CREATED — No Silent
 * No-Op #24). The router is wire-at-consumer injected, null-safe → NoOp
 * shipped default (plan 244 design 裁定 2).
 *
 * <p>This orchestrator is read-only with respect to {@link IAgentEngine},
 * {@link ITeamTaskStore}, and {@link ITeamManager}: it consumes their existing
 * contracts and does not modify them.
 *
 * <p>See plan 233 (L4-nop-task-dag-integration) Phase 2.
 */
public class TeamTaskFlowOrchestrator {

    private final IAgentEngine agentEngine;
    private final ITeamTaskStore taskStore;
    private final ITeamManager teamManager;
    private final ITaskFlowManager taskFlowManager;
    private final TeamTaskGraphBuilder graphBuilder;

    /**
     * Pluggable member spawner (plan 238 / L4-orchestrator-auto-spawn-integration).
     * Consulted only at <b>node run time</b> by a {@link SpawnMemberAgentTaskStep}
     * for a node whose team has no already-bound member (bound-member priority —
     * a node with a bound member never reaches the spawner). Shipped default is
     * {@link NoOpMemberSpawner} (returns explicit
     * {@link io.nop.ai.agent.team.scheduler.SpawnMemberResult.Status#NO_SPAWN},
     * so an unbound-member node honestly fails at run time —
     * {@code TeamTaskFlowResult{success=false}} — preserving the "unbound member
     * = honest failure" business semantics, with the documented intentional
     * change of failure shape from build-time throw to run-time failed result).
     * Integrators opt into auto-spawn via the spawner-aware constructor or
     * {@link #setMemberSpawner}.
     *
     * <p>As of plan 244 ({@code L4-multi-member-per-task-routing}) the spawner
     * is also consulted by the spawn fan-out steps
     * ({@link SpawnMemberFanOutStep} / {@link MixedMemberFanOutStep}) — once
     * per spawn target, with the target carried on the
     * {@link io.nop.ai.agent.team.scheduler.SpawnMemberRequest} so the spawner
     * spawns exactly that target (design 裁定 6). The spawner's interface
     * signature is unchanged.
     *
     * <p>Mutable via {@link #setMemberSpawner} (mirrors
     * {@code TeamTaskSchedulerDaemon.setMemberSpawner} and the other Layer 4
     * extension-point setter patterns). Reads go through the field directly.
     */
    private IMemberSpawner memberSpawner = NoOpMemberSpawner.noOp();

    /**
     * Pluggable per-task member router (plan 244 /
     * L4-multi-member-per-task-routing, design 裁定 2). Consulted at <b>graph
     * build time</b> for each team task to decide which member targets the
     * node fans out to (bound +/or spawn) and which reduction strategy
     * combines their futures. Shipped default is
     * {@link NoOpTaskMemberRouter} (singleton single-member plan — bound
     * priority + spawn fallback, line-for-line identical to plans 233/238/
     * 241/243 single-member behaviour). Integrators opt into multi-member
     * fan-out via the router-aware constructor or
     * {@link #setTaskMemberRouter}.
     *
     * <p>Mutable via {@link #setTaskMemberRouter} (mirrors
     * {@link #setMemberSpawner} and the other Layer 4 extension-point
     * setter patterns). Reads go through the field directly.
     */
    private ITaskMemberRouter taskMemberRouter = NoOpTaskMemberRouter.noOp();

    /**
     * Dedicated executor for {@link SpawnMemberAgentTaskStep}'s supplyAsync
     * (plan 243 design 裁定 3). MUST be independent of the {@code commonPool}
     * ({@code ForkJoinPool.commonPool()}) that {@code DefaultAgentEngine} uses
     * for its own one-arg {@code supplyAsync}: the spawn worker synchronously
     * joins the engine future (via {@code DefaultMemberSpawner.spawnMember} →
     * {@code engine.execute(req).join()}), so sharing the commonPool would
     * stall when concurrent spawn nodes ≥ commonPool parallelism (every
     * commonPool thread parked on {@code .join()}, none left to advance the
     * engine futures).
     *
     * <p>Wire-at-consumer: integrators may inject their own executor via
     * {@link #setSpawnStepExecutor}. When not injected, the orchestrator
     * lazily creates a dedicated bounded daemon-thread pool
     * ({@link #ownedSpawnExecutor}) sized to the spawn concurrency cap; that
     * owned pool is released by {@link #close()}.
     */
    private Executor spawnStepExecutor;

    /**
     * Tracks the spawn-step executor pool created by this orchestrator (when
     * none was injected) so {@link #close()} can shut it down. {@code null}
     * when an executor was injected ({@link #close()} leaves injected
     * executors alone — their owner manages their lifecycle) or before any
     * spawn node has required it.
     */
    private ExecutorService ownedSpawnExecutor;

    public TeamTaskFlowOrchestrator(IAgentEngine agentEngine, ITeamTaskStore taskStore,
                                    ITeamManager teamManager) {
        this(agentEngine, taskStore, teamManager, null);
    }

    public TeamTaskFlowOrchestrator(IAgentEngine agentEngine, ITeamTaskStore taskStore,
                                    ITeamManager teamManager, ITaskFlowManager taskFlowManager) {
        this(agentEngine, taskStore, teamManager, taskFlowManager, null);
    }

    /**
     * Fully-parameterized constructor with an explicit member spawner (plan 238).
     *
     * <p>When {@code memberSpawner} is {@code null}, the orchestrator uses the
     * shipped {@link NoOpMemberSpawner} default (an unbound-member node honestly
     * fails at run time — zero behaviour change for the bound-member path).
     * When a functional spawner (e.g. {@code DefaultMemberSpawner}) is supplied,
     * an unbound-member node spawns and executes a member agent at node run
     * time (bound-member priority: a node with a bound member never reaches
     * the spawner).
     *
     * @param memberSpawner optional member spawner consulted at node run time
     *                      for unbound-member nodes; {@code null} falls back to
     *                      the shipped {@link NoOpMemberSpawner} default
     */
    public TeamTaskFlowOrchestrator(IAgentEngine agentEngine, ITeamTaskStore taskStore,
                                    ITeamManager teamManager, ITaskFlowManager taskFlowManager,
                                    IMemberSpawner memberSpawner) {
        this(agentEngine, taskStore, teamManager, taskFlowManager, memberSpawner, null);
    }

    /**
     * Fully-parameterized constructor with an explicit member spawner (plan 238)
     * and an explicit per-task member router (plan 244).
     *
     * <p>When {@code memberSpawner} is {@code null}, the orchestrator uses the
     * shipped {@link NoOpMemberSpawner} default (an unbound-member node honestly
     * fails at run time — zero behaviour change for the bound-member path).
     * When a functional spawner (e.g. {@code DefaultMemberSpawner}) is supplied,
     * an unbound-member node spawns and executes a member agent at node run
     * time (bound-member priority: a node with a bound member never reaches
     * the spawner).
     *
     * <p>When {@code taskMemberRouter} is {@code null}, the orchestrator uses
     * the shipped {@link NoOpTaskMemberRouter} default (single-member plan —
     * bound priority + spawn fallback, line-for-line identical to plans
     * 233/238/241/243). When a multi-member router is supplied, the
     * orchestrator fans each task node out to the router's N targets under
     * the plan's reduction strategy (plan 244 design 裁定 2).
     *
     * @param memberSpawner    optional member spawner consulted at node run time
     *                         for unbound-member nodes; {@code null} falls back to
     *                         the shipped {@link NoOpMemberSpawner} default
     * @param taskMemberRouter optional per-task member router consulted at graph
     *                         build time to decide the node's fan-out; {@code null}
     *                         falls back to the shipped {@link NoOpTaskMemberRouter}
     *                         single-member default
     */
    public TeamTaskFlowOrchestrator(IAgentEngine agentEngine, ITeamTaskStore taskStore,
                                    ITeamManager teamManager, ITaskFlowManager taskFlowManager,
                                    IMemberSpawner memberSpawner,
                                    ITaskMemberRouter taskMemberRouter) {
        this.agentEngine = agentEngine;
        this.taskStore = taskStore;
        this.teamManager = teamManager;
        this.taskFlowManager = taskFlowManager != null ? taskFlowManager : new TaskFlowManagerImpl();
        this.graphBuilder = new TeamTaskGraphBuilder();
        this.memberSpawner = memberSpawner != null ? memberSpawner : NoOpMemberSpawner.noOp();
        this.taskMemberRouter = taskMemberRouter != null ? taskMemberRouter : NoOpTaskMemberRouter.noOp();
    }

    /**
     * @return the member spawner wired into this orchestrator (plan 238). Never
     *         {@code null}: an orchestrator constructed without an explicit
     *         spawner returns the shipped {@link NoOpMemberSpawner} singleton.
     */
    public IMemberSpawner getMemberSpawner() {
        return memberSpawner;
    }

    /**
     * Wire (or re-wire) the member spawner (plan 238 /
     * L4-orchestrator-auto-spawn-integration). The spawner is consulted at node
     * run time for nodes whose team has no already-bound member (bound-member
     * priority). Passing {@code null} resets to the shipped
     * {@link NoOpMemberSpawner} default (an unbound-member node honestly fails
     * at run time — zero behaviour change for the bound-member path). Passing a
     * functional spawner opts the orchestrator into auto-spawning
     * unbound-member nodes.
     *
     * <p>This mirrors {@code TeamTaskSchedulerDaemon.setMemberSpawner} and the
     * {@code IResourceGuard}→{@code InMemoryTeamManager} wire-at-consumer
     * convention: the spawner's only consumer is the orchestrator's node
     * execution path, so the orchestrator owns its injection (not the engine).
     *
     * @param memberSpawner the spawner to wire; {@code null} falls back to
     *                      {@link NoOpMemberSpawner#noOp()}
     */
    public void setMemberSpawner(IMemberSpawner memberSpawner) {
        this.memberSpawner = memberSpawner != null ? memberSpawner : NoOpMemberSpawner.noOp();
    }

    /**
     * @return the per-task member router wired into this orchestrator (plan 244).
     *         Never {@code null}: an orchestrator constructed without an explicit
     *         router returns the shipped {@link NoOpTaskMemberRouter} singleton.
     */
    public ITaskMemberRouter getTaskMemberRouter() {
        return taskMemberRouter;
    }

    /**
     * Wire (or re-wire) the per-task member router (plan 244 /
     * L4-multi-member-per-task-routing, design 裁定 2). The router is consulted
     * at graph build time for each team task to decide which member targets the
     * node fans out to (bound +/or spawn) and which reduction strategy combines
     * their futures. Passing {@code null} resets to the shipped
     * {@link NoOpTaskMemberRouter} single-member default (line-for-line
     * identical to plans 233/238/241/243 — zero regression). Passing a
     * multi-member router opts the orchestrator into N-target fan-out per node.
     *
     * <p>This mirrors {@link #setMemberSpawner} and the
     * {@code IResourceGuard}→{@code InMemoryTeamManager} wire-at-consumer
     * convention: the router's only consumer is the orchestrator's graph-build
     * loop, so the orchestrator owns its injection (not the engine).
     *
     * @param taskMemberRouter the router to wire; {@code null} falls back to
     *                         {@link NoOpTaskMemberRouter#noOp()}
     */
    public void setTaskMemberRouter(ITaskMemberRouter taskMemberRouter) {
        this.taskMemberRouter = taskMemberRouter != null ? taskMemberRouter : NoOpTaskMemberRouter.noOp();
    }

    /**
     * @return the dedicated executor wired for {@link SpawnMemberAgentTaskStep}
     *         supplyAsync, or {@code null} when none has been injected (the
     *         orchestrator will then lazily create an owned bounded pool on
     *         first use — plan 243 design 裁定 3).
     */
    public Executor getSpawnStepExecutor() {
        return spawnStepExecutor;
    }

    /**
     * Wire (or re-wire) the dedicated executor used by
     * {@link SpawnMemberAgentTaskStep}'s supplyAsync (plan 243 design 裁定 3).
     * The supplied executor MUST be independent of the {@code commonPool}
     * ({@code ForkJoinPool.commonPool()}); passing the commonPool is a
     * deployment error that risks a nested-blocking stall (spawn workers park
     * on {@code engine.execute().join()} while the engine itself runs on the
     * commonPool). When {@code null}, the orchestrator lazily creates and
     * owns a dedicated bounded daemon-thread pool (released by {@link #close()}).
     *
     * <p>This follows the wire-at-consumer convention: the orchestrator is the
     * only consumer of the spawn-step executor, so the orchestrator owns its
     * injection. Injecting an explicit executor lets integrators govern spawn
     * concurrency (pool size = spawn concurrency cap) and thread-pool
     * resources centrally.
     *
     * @param executor the dedicated executor (independent of the commonPool);
     *                 {@code null} resets to the lazily-created owned pool
     */
    public void setSpawnStepExecutor(Executor executor) {
        this.spawnStepExecutor = executor;
    }

    /**
     * Release the spawn-step executor pool created (and owned) by this
     * orchestrator, if any (plan 243 design 裁定 3). No-op when an executor was
     * injected via {@link #setSpawnStepExecutor} (the injector owns its
     * lifecycle) or when no spawn node has ever required a pool. Long-lived
     * orchestrators should call this on disposal; short-lived / one-shot
     * orchestrators can rely on the daemon thread factory (pool threads do
     * not prevent JVM exit).
     */
    public void close() {
        if (ownedSpawnExecutor != null) {
            ownedSpawnExecutor.shutdownNow();
            try {
                ownedSpawnExecutor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ownedSpawnExecutor = null;
        }
    }

    /**
     * Resolve the executor for {@link SpawnMemberAgentTaskStep}'s supplyAsync:
     * use the injected one if present, otherwise lazily create a dedicated
     * bounded daemon-thread pool independent of the commonPool (plan 243
     * design 裁定 3). The pool size is the spawn concurrency cap; it defaults
     * to {@code availableProcessors()} (min 2) so concurrent spawn branches are
     * not artificially starved while staying bounded.
     */
    private Executor resolveSpawnExecutor() {
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
     * Daemon thread factory for the owned spawn-step pool. Threads are daemon
     * so a short-lived orchestrator that forgets to call {@link #close()} does
     * not hang the JVM; they are named {@code ai-agent-spawn-worker-N} for
     * diagnostics (and so tests can assert the spawn worker ran off the
     * calling thread / off the commonPool).
     */
    private static final class SpawnWorkerThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ai-agent-spawn-worker-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }

    /**
     * Execute the team's task DAG synchronously through the nop-task runtime.
     *
     * <p>This is a thin sync wrapper over {@link #executeAsync(String)} (plan 241
     * design 裁定 2). It triggers the same async DAG execution and blocks on
     * the returned future. Structural problems (empty task set, unknown team,
     * cyclic {@code blockedBy}) throw {@link NopAiAgentException} before any
     * node runs (synchronously, identically to {@link #executeAsync}). A node
     * whose team has no bound member does NOT throw here: it is resolved at
     * build time to a spawn-on-demand node (plan 238), and its outcome is
     * reported via the returned {@link TeamTaskFlowResult}.
     *
     * <p>A failed node returns a {@link TeamTaskFlowResult} with
     * {@link TeamTaskFlowResult#isSuccess()} {@code false} and the failed /
     * skipped task ids populated — failure is reported honestly, never silent.
     *
     * <p>Zero-regression note (plan 241): pre-241 this method blocked on
     * {@code task.execute(taskRt).syncGetOutputs()} and caught exceptions in a
     * try/catch. Post-241 it delegates to {@link #executeAsync(String)}
     * followed by {@code .join()}; the async path's {@code exceptionally}
     * handler converts any node-failure exception into an honest
     * {@code TeamTaskFlowResult{success=false}} before {@code .join()} runs,
     * so {@code .join()} never re-throws a node failure (it throws only if
     * the future completes exceptionally for a non-node reason, which the
     * {@code exceptionally} handler also converts to a failed result). The
     * observable behaviour is therefore identical: structural fast-failures
     * throw synchronously; node failures yield
     * {@code TeamTaskFlowResult{success=false}}; success yields
     * {@code TeamTaskFlowResult{success=true}}.
     *
     * @param teamId the owning team's identity
     * @return the run outcome (success or failure), never {@code null}
     * @throws NopAiAgentException for structural fast-failures (empty task
     *                            set, unknown team, cyclic {@code blockedBy})
     */
    public TeamTaskFlowResult execute(String teamId) {
        // join() never throws CompletionException for node failures: the
        // async path's exceptionally handler converts every node-level
        // exception into an honest failed TeamTaskFlowResult before this
        // future completes. Structural fast-failures throw synchronously
        // out of executeAsync (before the future is created), so they
        // propagate directly through this method as NopAiAgentException.
        return executeAsync(teamId).join();
    }

    /**
     * Execute the team's task DAG <b>asynchronously</b> through the nop-task
     * runtime (plan 241 / async team-task orchestration, design 裁定 1).
     *
     * <p>Consumes nop-task's <b>existing</b> async execution model — the graph
     * step's {@link TaskStepReturn#getReturnPromise()} /
     * {@link TaskStepReturn#asyncOutputs()} composition. Each bound-member
     * node's {@link MemberAgentTaskStep} returns an async
     * {@link TaskStepReturn} (wrapping {@link IAgentEngine#execute}'s
     * existing {@link CompletableFuture}), and each spawn-on-demand node's
     * {@link SpawnMemberAgentTaskStep} (no bound member) also returns an
     * async {@link TaskStepReturn} (offloading the synchronous
     * {@code IMemberSpawner.spawnMember} to a dedicated executor via
     * {@code supplyAsync} — plan 243), so independent diamond branches
     * (no {@code blockedBy} edge between them) <b>truly run in parallel</b>
     * under nop-task's {@link GraphTaskStep} CompletableFuture scheduler —
     * the calling thread is NOT blocked on the entire DAG. This holds for
     * graphs mixing bound-member and spawn-on-demand nodes alike.
     *
     * <p>Structural problems (null teamId, empty task set, unknown team,
     * cyclic {@code blockedBy}) throw {@link NopAiAgentException}
     * <b>synchronously</b> before the future is created (fast-fail, identical
     * to {@link #execute(String)}). Node-level failures (claim CAS loss,
     * member-agent exception, non-completed status, complete CAS loss,
     * spawner NO_SPAWN / SPAWN_FAILED / throws) are propagated through the
     * future as exceptions, caught by the {@code exceptionally} handler, and
     * converted into an honest {@code TeamTaskFlowResult{success=false}}
     * (No Silent No-Op #24).
     *
     * @param teamId the owning team's identity
     * @return a future that completes with the run outcome (success or
     *         failure), never completes exceptionally for node-level
     *         failures (those are converted to a failed result); structural
     *         fast-failures throw synchronously before the future is created
     * @throws NopAiAgentException for structural fast-failures (null teamId,
     *                            empty task set, unknown team, cyclic
     *                            {@code blockedBy}) — thrown synchronously
     */
    public CompletableFuture<TeamTaskFlowResult> executeAsync(String teamId) {
        // Shared build path with the legacy sync entry — throws synchronously
        // on structural fast-failures (null teamId / no tasks / unknown team
        // / cyclic blockedBy), identical to the pre-241 execute().
        BuiltGraph built = buildGraphForExecution(teamId);

        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(built.task, false, null);

        // Consume nop-task's existing async model: asyncOutputs() composes
        // the graph step's CompletableFuture chain. The calling thread is
        // released as soon as the graph scheduler has wired up the ready-
        // node triggers; independent branches run concurrently under
        // GraphTaskStep's CompletableFuture scheduling.
        //
        // When a node fails synchronously fast (e.g. an enter node's
        // delegate throws before the scheduler yields, or an engine future
        // is already-completed exceptionally), nop-task's
        // GraphTaskStep.execute may short-circuit via TaskStepReturn.ASYNC's
        // isFutureDone branch and call syncGet on the failed future — which
        // throws synchronously out of built.task.execute(taskRt). Wrap that
        // synchronous throw in a try/catch and convert it to an honest
        // failed TeamTaskFlowResult, mirroring the pre-241 sync execute()'s
        // try/catch around syncGetOutputs (No Silent No-Op #24: failure is
        // reported, never re-thrown as a raw exception to the async caller
        // — the contract is "future completes with success=false result").
        TaskStepReturn stepReturn;
        try {
            stepReturn = built.task.execute(taskRt);
        } catch (Exception nodeFailure) {
            // A node delegate (or the graph scheduler's sync short-circuit)
            // threw before the future was wired up. The recorder has
            // already captured the failed/skipped task ids (the node's
            // markFailed ran before the throw). Report honestly.
            return CompletableFuture.completedFuture(
                    built.recorder.buildResult(false, built.tasks));
        }

        CompletableFuture<TeamTaskFlowResult> result = stepReturn.asyncOutputs()
                .thenApply(outputs -> built.recorder.buildResult(true, built.tasks))
                .toCompletableFuture()
                .exceptionally(ex -> {
                    // A node delegate threw (claim/complete CAS loss, member-
                    // agent failure, etc.). nop-task's GraphTaskStep short-
                    // circuited the graph and cancelled successor nodes.
                    // Report honestly which task failed and which were
                    // skipped — never silently succeed (Minimum Rules #24).
                    return built.recorder.buildResult(false, built.tasks);
                });

        return result;
    }

    /**
     * Shared graph-build path used by both {@link #execute(String)} (via
     * {@link #executeAsync(String)}) and the test-visible internals.
     *
     * <p>Performs the structural fast-fail checks and the cycle-detection
     * build (synchronously — these never depend on node execution). The
     * returned {@link BuiltGraph} holds the runtime nop-task {@link ITask},
     * the {@link ExecutionRecorder} that nodes will populate, and the task
     * list snapshot used to build the result's skipped/completed buckets.
     */
    private BuiltGraph buildGraphForExecution(String teamId) {
        if (teamId == null) {
            throw new NopAiAgentException("nop.ai.team.flow.null-team-id: teamId must not be null");
        }

        List<TeamTask> tasks = taskStore.getTasksByTeam(teamId);
        if (tasks == null || tasks.isEmpty()) {
            throw new NopAiAgentException(
                    "nop.ai.team.flow.no-tasks: team has no tasks to orchestrate: teamId=" + teamId);
        }

        Team team = teamManager.getTeam(teamId)
                .orElseThrow(() -> new NopAiAgentException(
                        "nop.ai.team.flow.team-not-found: unknown team teamId=" + teamId));

        // Real nop-task cycle detection (裁定 2): buildGraph runs the graph
        // through GraphStepAnalyzer and throws on a cyclic blockedBy set.
        graphBuilder.buildGraph(tasks);

        Set<String> allTaskIds = tasks.stream()
                .map(TeamTask::getTaskId)
                .collect(Collectors.toCollection(HashSet::new));

        ExecutionRecorder recorder = new ExecutionRecorder();

        // Orchestrator session identity: recorded as claimedBy / completedBy
        // on the task state transitions this orchestrator drives (symmetric
        // to the daemon's daemonSessionId). It is also passed as the
        // SpawnMemberRequest.daemonSessionId audit metadata for nodes that
        // spawn at run time (plan 238 decision 5).
        String orchestratorSessionId = "orchestrator-" + teamId;

        // Plan 243 design 裁定 2 (explicit-propagation tenant capture): capture
        // the caller's tenant ONCE here, on the caller's thread (where it is
        // reliably set), so each SpawnMemberAgentTaskStep can re-apply it
        // inside its supplyAsync worker regardless of the DAG topology (both
        // enter and non-enter spawn nodes). buildGraphForExecution runs
        // synchronously inside executeAsync, so this reads the caller's
        // ThreadLocalTenantResolver context. Null = no tenant context (all
        // data visible, backward compatible).
        String capturedTenant = ThreadLocalTenantResolver.current();

        // Build the runtime nop-task graph: one node per team task.
        List<GraphStepNode> nodes = new ArrayList<>(tasks.size() + 1);
        for (TeamTask task : tasks) {
            Set<String> waitSteps = task.getBlockedBy().stream()
                    .filter(allTaskIds::contains)
                    .collect(Collectors.toCollection(HashSet::new));
            boolean enter = waitSteps.isEmpty();

            // Plan 244 (L4-multi-member-per-task-routing, design 裁定 2):
            // the per-task member router decides which member targets this
            // node fans out to (bound +/or spawn). The router runs at build
            // time, non-executing (it never calls the engine nor the spawner).
            // Single-member plans short-circuit to the existing single-target
            // node steps (zero-regression for plans 233/238/241/243);
            // multi-member plans build a fan-out node step (N futures composed
            // under the plan's reduction strategy, single CLAIMED → COMPLETED
            // transition when reduction succeeds).
            MemberDispatchPlan plan = taskMemberRouter.route(team, task);
            AbstractTaskStep delegate = buildNodeStepForPlan(plan, team, orchestratorSessionId,
                    capturedTenant, recorder);
            delegate.setStepType("team-task:" + task.getTaskId());

            ITaskStepExecution execution = wrapExecution(delegate, task.getTaskId());
            // Real task nodes are never the graph exit (see "Synthetic sink"
            // above).
            nodes.add(new GraphStepNode(new HashSet<>(waitSteps), null, execution, enter, false));
        }

        // Synthetic sole exit: waits on every task, runs last, terminates the
        // graph.
        FlowSinkStep sink = new FlowSinkStep();
        sink.setStepType("team-flow-sink");
        nodes.add(new GraphStepNode(new HashSet<>(allTaskIds), null, wrapExecution(sink, "__sink__"),
                false, true));

        GraphTaskStep graph = new GraphTaskStep();
        graph.setStepType("team-task-dag");
        graph.setNodes(nodes);

        ITask task = new TaskImpl("team-flow:" + teamId, 0, graph, false,
                null, null, Collections.emptyList(), Collections.emptyList());

        return new BuiltGraph(task, recorder, tasks);
    }

    private ITaskStepExecution wrapExecution(AbstractTaskStep delegate, String stepName) {
        return new TaskStepExecution(null, stepName,
                Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
                null, null, delegate, null, null, false, null, false);
    }

    /**
     * Internal carrier for the build-phase outputs shared between the
     * structural-build path and the (sync or async) execution path.
     */
    private static final class BuiltGraph {
        final ITask task;
        final ExecutionRecorder recorder;
        final List<TeamTask> tasks;

        BuiltGraph(ITask task, ExecutionRecorder recorder, List<TeamTask> tasks) {
            this.task = task;
            this.recorder = recorder;
            this.tasks = tasks;
        }
    }

    /**
     * Build the nop-task node step for one team task's dispatch plan (plan 244
     * / L4-multi-member-per-task-routing, design 裁定 2). Selects the node
     * step based on the plan's target shape:
     * <ul>
     *   <li><b>Empty plan</b> → honest failure throw ({@link NopAiAgentException}).
     *       The node build aborts; the task stays in CREATED; nop-task's graph
     *       build never reaches execution. This is the routing equivalent of
     *       the pre-244 unbound-no-specs honest failure, raised at build time
     *       (No Silent No-Op #24 — never silently skip the node).</li>
     *   <li><b>Single BOUND target</b> → existing {@link MemberAgentTaskStep}
     *       (line-for-line zero-regression for plans 233/241).</li>
     *   <li><b>Single SPAWN target</b> → existing {@link SpawnMemberAgentTaskStep}
     *       (line-for-line zero-regression for plans 238/243).</li>
     *   <li><b>Multiple BOUND-only targets</b> → {@link BoundMemberFanOutStep}
     *       (N engine futures composed under the plan's reduction).</li>
     *   <li><b>Multiple SPAWN-only targets</b> → {@link SpawnMemberFanOutStep}
     *       (N supplyAsync(spawnMember) futures composed under the plan's
     *       reduction, on the dedicated spawn executor + tenant propagation).</li>
     *   <li><b>Mixed BOUND + SPAWN targets</b> → {@link MixedMemberFanOutStep}
     *       (unified reduction over both target kinds).</li>
     * </ul>
     *
     * @param plan                   the dispatch plan for this task (non-null;
     *                               may be empty — handled as honest failure)
     * @param team                   the live team snapshot
     * @param orchestratorSessionId  the orchestrator session id for claim/complete
     * @param capturedTenant         the caller's tenant (re-applied inside each
     *                               spawn supplyAsync worker; plan 243 裁定 2)
     * @param recorder               the shared execution recorder for this DAG run
     * @return the nop-task node step
     * @throws NopAiAgentException when the plan is empty (honest failure)
     */
    private AbstractTaskStep buildNodeStepForPlan(MemberDispatchPlan plan, Team team,
                                                 String orchestratorSessionId,
                                                 String capturedTenant,
                                                 ExecutionRecorder recorder) {
        TeamTask task = plan.getTask();
        List<DispatchTarget> targets = plan.getTargets();
        IReductionStrategy reduction = plan.getReductionStrategy();

        if (targets.isEmpty()) {
            // Honest failure: the router returned no dispatchable target.
            // The task stays in CREATED; never silently skip the node.
            throw new NopAiAgentException(
                    "nop.ai.team.flow.no-dispatchable-member: dispatch plan produced zero targets for taskId="
                            + task.getTaskId() + ", teamId=" + task.getTeamId()
                            + " (router=" + taskMemberRouter.getClass().getName()
                            + " — no bound member and no declarative spawn target)");
        }

        if (targets.size() == 1) {
            // Single-member plan: short-circuit to the original single-target
            // steps (line-for-line zero-regression for plans 233/238/241/243).
            DispatchTarget t = targets.get(0);
            if (t.isBound()) {
                return new MemberAgentTaskStep(
                        task, t.getSessionId(), t.getAgentName(),
                        agentEngine, taskStore, recorder);
            }
            // Single SPAWN target → existing SpawnMemberAgentTaskStep.
            Executor spawnExecutor = resolveSpawnExecutor();
            return new SpawnMemberAgentTaskStep(
                    task, team, orchestratorSessionId, memberSpawner, taskStore, recorder,
                    spawnExecutor, capturedTenant);
        }

        // Multi-member fan-out. Partition by kind to select the step variant.
        boolean hasBound = false;
        boolean hasSpawn = false;
        for (DispatchTarget t : targets) {
            if (t.isBound()) {
                hasBound = true;
            } else if (t.isSpawn()) {
                hasSpawn = true;
            }
        }
        if (hasBound && hasSpawn) {
            Executor spawnExecutor = resolveSpawnExecutor();
            return new MixedMemberFanOutStep(task, team, targets, orchestratorSessionId,
                    agentEngine, memberSpawner, taskStore, recorder, spawnExecutor,
                    capturedTenant, reduction);
        }
        if (hasBound) {
            return new BoundMemberFanOutStep(task, targets, orchestratorSessionId,
                    agentEngine, taskStore, recorder, capturedTenant, reduction);
        }
        // hasSpawn only.
        Executor spawnExecutor = resolveSpawnExecutor();
        return new SpawnMemberFanOutStep(task, team, targets, orchestratorSessionId,
                memberSpawner, taskStore, recorder, spawnExecutor, capturedTenant, reduction);
    }

    /**
     * Synthetic sole-exit node of the team-task graph. It performs no work;
     * its only role is to be the single nop-task graph exit that waits on
     * every real task, so the graph future resolves exactly once — after all
     * real tasks have completed — regardless of how many natural sinks the
     * DAG has. This is legitimate graph topology (a real terminal marker),
     * not a silent no-op in the Minimum-Rules-#24 sense.
     */
    private static final class FlowSinkStep extends AbstractTaskStep {
        @Nonnull
        @Override
        public TaskStepReturn execute(ITaskStepRuntime stepRt) {
            return TaskStepReturn.RETURN_RESULT("team-flow-complete");
        }
    }
}
