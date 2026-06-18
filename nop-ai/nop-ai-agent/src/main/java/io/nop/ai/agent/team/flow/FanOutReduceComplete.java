package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.task.TaskStepReturn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Shared helper for the three fan-out step variants
 * ({@link BoundMemberFanOutStep}, {@link SpawnMemberFanOutStep},
 * {@link MixedMemberFanOutStep}) that reduces the N per-member futures and
 * performs the single {@code completeTask} transition (plan 244 /
 * L4-multi-member-per-task-routing).
 *
 * <p>Centralizing this logic avoids duplicating the reduce+complete+tenant
 * chain across three step classes. The helper:
 * <ol>
 *   <li>waits for all per-member futures (each already maps to a
 *       {@link MemberExecOutcome});</li>
 *   <li>runs the {@link IReductionStrategy} over the collected outcomes;</li>
 *   <li>on reduction success, re-applies the caller's captured tenant
 *       (plan 243 design 裁定 2) around the single {@code completeTask} CAS
 *       so DB stores filter by the caller's tenant — the reduction +
 *       complete chain runs on whatever thread completed the last member
 *       future, which may have cleared its tenant context (e.g. a spawn
 *       worker's {@code finally});</li>
 *   <li>on reduction failure or CAS loss, throws an honest
 *       {@link NopAiAgentException} (the caller's {@code whenComplete}
 *       records {@code markFailed} — see the fan-out steps).</li>
 * </ol>
 *
 * <p>Package-private — an internal implementation detail of the fan-out steps.
 *
 * <p>See plan 244 (L4-multi-member-per-task-routing), design 裁定 4 / 5.
 */
final class FanOutReduceComplete {

    private FanOutReduceComplete() {
    }

    /**
     * Build a future that, when all per-member futures have settled, runs the
     * reduction strategy and — on success — performs the single
     * {@code completeTask} CAS under the caller's captured tenant.
     *
     * @param perMember             the N per-member outcome futures (non-null,
     *                              non-empty; each settles to a
     *                              {@link MemberExecOutcome})
     * @param reductionStrategy     the reduction strategy (non-null)
     * @param taskId                the team task id
     * @param orchestratorSessionId the orchestrator session id for the CAS
     * @param taskStore             the task store (for the CAS)
     * @param recorder              the shared execution recorder
     * @param capturedTenant        the caller's tenant, re-applied around the
     *                              {@code completeTask} call (may be null)
     * @return a future that completes with a {@link TaskStepReturn} on success
     *         or completes exceptionally on reduction failure / CAS loss
     */
    static CompletableFuture<TaskStepReturn> reduceAndComplete(
            List<CompletableFuture<MemberExecOutcome>> perMember,
            IReductionStrategy reductionStrategy,
            String taskId, String orchestratorSessionId,
            ITeamTaskStore taskStore, ExecutionRecorder recorder,
            String capturedTenant) {

        return CompletableFuture
                .allOf(perMember.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<MemberExecOutcome> outcomes = new ArrayList<>(perMember.size());
                    for (CompletableFuture<MemberExecOutcome> f : perMember) {
                        // allOf guarantees every future is settled by now.
                        outcomes.add(f.join());
                    }
                    return outcomes;
                })
                .thenCompose(outcomes -> {
                    ReductionContext ctx = new ReductionContext(taskId, recorder);
                    return reductionStrategy.reduce(outcomes, ctx)
                            .thenApply(reductionSucceeded -> {
                                if (!reductionSucceeded) {
                                    // Honest failure: the reduction explicitly
                                    // declined. Defensively treat as a failure
                                    // (the AllMustSucceedReduction never does
                                    // this — it throws — but a custom strategy
                                    // might).
                                    throw new NopAiAgentException(
                                            "nop.ai.team.flow.fanout-reduction-declined: reduction returned false for taskId="
                                                    + taskId + ", strategy=" + reductionStrategy.name());
                                }
                                // Complete the task ONCE under the caller's
                                // tenant. This chain runs on whatever thread
                                // completed the last member future, which may
                                // have cleared its tenant context (spawn
                                // worker's finally) — so re-apply explicitly.
                                ThreadLocalTenantResolver.set(capturedTenant);
                                try {
                                    Optional<io.nop.ai.agent.team.TeamTask> completed =
                                            taskStore.completeTask(taskId, orchestratorSessionId);
                                    if (completed.isEmpty()) {
                                        throw new NopAiAgentException(
                                                "nop.ai.team.flow.complete-failed: cannot complete team task taskId=" + taskId
                                                        + " (not in CLAIMED status — possible concurrent transition)");
                                    }
                                    recorder.markComplete(taskId);
                                    return TaskStepReturn.RETURN_RESULT("completed:" + taskId);
                                } finally {
                                    ThreadLocalTenantResolver.clear();
                                }
                            });
                });
    }
}
