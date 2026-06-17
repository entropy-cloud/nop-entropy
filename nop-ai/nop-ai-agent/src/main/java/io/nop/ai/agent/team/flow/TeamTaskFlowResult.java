package io.nop.ai.agent.team.flow;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable outcome of a single
 * {@link TeamTaskFlowOrchestrator#execute(String)} run.
 *
 * <p>Regardless of success or failure, the orchestrator always returns a
 * result (it never silently succeeds when a node failed — Minimum Rules
 * #24). Callers inspect {@link #isSuccess()} and, on failure,
 * {@link #getFailedTaskId()} / {@link #getSkippedTaskIds()} for an honest
 * breakdown of what ran, what completed, and what was never started.
 *
 * <h2>Anti-Hollow evidence</h2>
 * {@link #getStartOrder()} / {@link #getCompletionOrder()} expose the
 * monotonic execution counters recorded by each node's delegate step at the
 * moment it started / completed its member-agent invocation. A test can
 * assert dependency ordering (e.g. {@code completionOrder(A) < startOrder(B)})
 * to prove the nop-task graph scheduler really enforced the blockedBy
 * edges — not merely that all tasks ended up COMPLETED.
 *
 * <p>See plan 233 (L4-nop-task-dag-integration) Phase 2.
 */
public final class TeamTaskFlowResult {

    private final boolean success;
    private final List<String> completedTaskIds;
    private final String failedTaskId;
    private final List<String> skippedTaskIds;
    private final Map<String, Integer> startOrder;
    private final Map<String, Integer> completionOrder;

    public TeamTaskFlowResult(boolean success, List<String> completedTaskIds, String failedTaskId,
                              List<String> skippedTaskIds, Map<String, Integer> startOrder,
                              Map<String, Integer> completionOrder) {
        this.success = success;
        this.completedTaskIds = Collections.unmodifiableList(Objects.requireNonNull(completedTaskIds));
        this.failedTaskId = failedTaskId;
        this.skippedTaskIds = Collections.unmodifiableList(Objects.requireNonNull(skippedTaskIds));
        this.startOrder = Collections.unmodifiableMap(Objects.requireNonNull(startOrder));
        this.completionOrder = Collections.unmodifiableMap(Objects.requireNonNull(completionOrder));
    }

    /**
     * @return {@code true} iff every team-task node in the DAG ran its
     *         member-agent delegate to a COMPLETED status.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * @return the taskIds that reached {@link io.nop.ai.agent.team.TeamTaskStatus#COMPLETED},
     *         ordered by their actual completion sequence (earliest first).
     */
    public List<String> getCompletedTaskIds() {
        return completedTaskIds;
    }

    /**
     * @return the taskId whose member-agent delegate failed (causing the
     *         graph to short-circuit), or {@code null} if the run succeeded.
     */
    public String getFailedTaskId() {
        return failedTaskId;
    }

    /**
     * @return the taskIds that were never started because the graph
     *         short-circuited on a failed predecessor (dependency-ordered
     *         failure propagation). Empty on success.
     */
    public List<String> getSkippedTaskIds() {
        return skippedTaskIds;
    }

    /**
     * @return an unmodifiable map of taskId → monotonic start counter
     *         (recorded when the delegate began its member-agent call).
     */
    public Map<String, Integer> getStartOrder() {
        return startOrder;
    }

    /**
     * @return an unmodifiable map of taskId → monotonic completion counter
     *         (recorded when the delegate marked the task COMPLETED).
     */
    public Map<String, Integer> getCompletionOrder() {
        return completionOrder;
    }

    @Override
    public String toString() {
        return "TeamTaskFlowResult{success=" + success
                + ", completed=" + completedTaskIds
                + ", failedTaskId=" + failedTaskId
                + ", skipped=" + skippedTaskIds
                + ", startOrder=" + startOrder
                + ", completionOrder=" + completionOrder + '}';
    }
}
