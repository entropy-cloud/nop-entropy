package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Pluggable extension point that <b>reduces</b> the N member-execution
 * futures of a multi-member fan-out node into a single node future
 * (plan 244 / L4-multi-member-per-task-routing, design 裁定 3).
 *
 * <p>The shipped default is {@link AllMustSucceedReduction} (the strictest,
 * most honest reduction): all N members must reach
 * {@code AgentExecStatus.completed}; any failure (exception / non-completed
 * status / complete-CAS loss) fast-fails the node and leaves the task
 * CLAIMED. Other reduction strategies — quorum, majority, first-wins,
 * partitioning, pipeline — are explicit Non-Goals of plan 244 and remain
 * opt-in successors; the extension point is reserved here so an integrator
 * can swap in a different reduction without modifying the orchestrator.
 *
 * <h2>Contract</h2>
 * <p>The strategy receives:
 * <ul>
 *   <li>the per-member execution results (one {@link MemberExecOutcome} per
 *       {@link DispatchTarget}), each of which already wraps the
 *       {@link AgentExecutionResult} for a bound target or the
 *       {@link io.nop.ai.agent.team.scheduler.SpawnMemberResult} for a
 *       spawn target;</li>
 *   <li>a {@link ReductionContext} that exposes the shared
 *       {@link ExecutionRecorder} (so the strategy can record
 *       markFailed/markComplete exactly once) and the task id.</li>
 * </ul>
 * The strategy returns a {@code CompletableFuture<Boolean>} that completes
 * with {@code true} when the node should complete (all reduction criteria
 * satisfied → the orchestrator performs the single CLAIMED → COMPLETED
 * transition) or completes exceptionally when the node should fail (any
 * reduction criterion violated → the task stays CLAIMED, No Silent No-Op
 * #24). The orchestrator — NOT the strategy — performs the actual
 * {@code completeTask} CAS, so the strategy's job is purely a yes/no
 * decision over already-collected member outcomes.
 *
 * <h2>Why a strategy and not inline allOf?</h2>
 * <p>{@link AllMustSucceedReduction} is implementable as a literal
 * {@code CompletableFuture.allOf(...)} composition, but extracting the
 * reduction as an extension point lets a successor plan ship a quorum /
 * majority / first-wins variant without touching the orchestrator's node
 * step code. The v1 implementation is the strict default; future
 * integrators opt into alternative reductions by injecting a different
 * {@link IReductionStrategy} into the dispatch plan.
 *
 * <p>See plan 244 (L4-multi-member-per-task-routing), design 裁定 3 / 5.
 */
public interface IReductionStrategy {

    /**
     * Reduce the N member-execution outcomes to a single node decision.
     *
     * <p>Implementations MUST:
     * <ul>
     *   <li>complete the returned future with {@code true} only when the
     *       reduction criterion is satisfied (e.g. for all-must-succeed:
     *       every outcome reached {@link MemberExecOutcome.State#COMPLETED});</li>
     *   <li>complete the returned future exceptionally otherwise — never
     *       silently complete with {@code false} (No Silent No-Op #24);</li>
     *   <li>not perform the {@code completeTask} CAS themselves — the
     *       orchestrator owns that single transition;</li>
     *   <li>be safe for concurrent invocation (multiple fan-out nodes may
     *       reduce concurrently in the DAG).</li>
     * </ul>
     *
     * @param outcomes the per-member execution outcomes (non-null, non-empty
     *                 for a real fan-out; the orchestrator pre-checks the
     *                 empty case as an honest failure)
     * @param context  the reduction context (recorder + task id)
     * @return a future that completes with {@code true} when the node should
     *         complete the task, or completes exceptionally when the node
     *         should fail
     */
    CompletableFuture<Boolean> reduce(List<MemberExecOutcome> outcomes, ReductionContext context);

    /**
     * @return a short human-readable name for diagnostics (e.g.
     *         {@code "all-must-succeed"}).
     */
    String name();
}
