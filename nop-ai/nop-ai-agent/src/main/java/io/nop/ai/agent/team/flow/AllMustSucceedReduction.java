package io.nop.ai.agent.team.flow;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Shipped default {@link IReductionStrategy} for multi-member fan-out nodes
 * (plan 244 / L4-multi-member-per-task-routing, design 裁定 3).
 *
 * <p>The strictest, most honest reduction: all N member outcomes must reach
 * {@link MemberExecOutcome.State#COMPLETED} for the node to complete the
 * task. Any failure (engine exception, non-completed status, NO_SPAWN,
 * SPAWN_FAILED, spawner null / threw) fast-fails the node and leaves the
 * task CLAIMED (No Silent No-Op #24 — failure is reported, never silently
 * swallowed). This mirrors the single-member bound and spawn step
 * honest-failure semantics line-for-line, generalized to N members.
 *
 * <h2>Fast-fail semantics</h2>
 * <p>The first failure observed (in completion order, not target order)
 * completes the reduced future exceptionally. Other in-flight member
 * executions are <b>not cancelled</b> — they run to completion and their
 * results are discarded. Cancellation of in-flight members is an explicit
 * Non-Goal successor of plan 244 (requires
 * {@code IAgentEngine.cancelSession} integration).
 *
 * <h2>Singleton</h2>
 * <p>Stateless and thread-safe; a singleton instance is exposed via
 * {@link #instance()}.
 *
 * <p>See plan 244 (L4-multi-member-per-task-routing), design 裁定 3 / 5.
 */
public final class AllMustSucceedReduction implements IReductionStrategy {

    private static final AllMustSucceedReduction INSTANCE = new AllMustSucceedReduction();

    /**
     * @return the singleton instance.
     */
    public static AllMustSucceedReduction instance() {
        return INSTANCE;
    }

    private AllMustSucceedReduction() {
    }

    @Override
    public CompletableFuture<Boolean> reduce(List<MemberExecOutcome> outcomes, ReductionContext context) {
        // The orchestrator pre-checks the empty-plan case as an honest
        // failure, so a non-empty outcomes list is guaranteed here. Still
        // defend: an empty list is an honest failure, not a vacuous success
        // (Minimum Rules #24).
        if (outcomes.isEmpty()) {
            CompletableFuture<Boolean> failed = new CompletableFuture<>();
            failed.completeExceptionally(new io.nop.ai.agent.engine.NopAiAgentException(
                    "nop.ai.team.flow.fanout-empty-plan: dispatch plan produced zero outcomes for taskId="
                            + context.getTaskId() + " (no member to dispatch to)"));
            return failed;
        }

        // Find the first failure in target order; if any, fast-fail the
        // reduced future with that failure. The in-flight members continue
        // (run-to-completion); their results are discarded.
        for (MemberExecOutcome outcome : outcomes) {
            if (!outcome.isCompleted()) {
                CompletableFuture<Boolean> failed = new CompletableFuture<>();
                failed.completeExceptionally(outcome.toException(context.getTaskId()));
                return failed;
            }
        }
        // All members completed → node may complete the task.
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public String name() {
        return "all-must-succeed";
    }
}
