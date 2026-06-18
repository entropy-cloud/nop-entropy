package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.team.scheduler.SpawnMemberResult;

import java.util.Objects;

/**
 * Outcome of executing one {@link DispatchTarget} inside a fan-out node
 * (plan 244 / L4-multi-member-per-task-routing).
 *
 * <p>Wraps either a successful {@link AgentExecutionResult} (for a BOUND
 * target's engine future, or a SPAWN target's DISPATCHED spawner result) or
 * an honest failure (exception / NO_SPAWN / SPAWN_FAILED / non-completed).
 * The {@link IReductionStrategy} consumes a list of these to decide whether
 * the node should complete or fail.
 *
 * <h2>State model</h2>
 * <ul>
 *   <li>{@link State#COMPLETED} — the member agent reached
 *       {@link io.nop.ai.agent.model.AgentExecStatus#completed}. The wrapped
 *       {@link AgentExecutionResult} is non-null. The node may complete.</li>
 *   <li>{@link State#ENGINE_FAILED} — the engine future completed
 *       exceptionally (BOUND target) or the spawn returned
 *       {@link SpawnMemberResult.Status#SPAWN_FAILED}. The cause is non-null.
 *       The node must fail.</li>
 *   <li>{@link State#NOT_COMPLETED} — the member agent returned a
 *       non-completed terminal status (failed / cancelled / paused / ...).
 *       The wrapped {@link AgentExecutionResult} is non-null. The node must
 *       fail.</li>
 *   <li>{@link State#NO_SPAWN} — the spawner honestly declined
 *       ({@link SpawnMemberResult.Status#NO_SPAWN}). The reason is non-null.
 *       The node must fail.</li>
 *   <li>{@link State#SPAWNER_NULL} — the spawner returned {@code null}
 *       (defensive — a contract violation). The node must fail.</li>
 *   <li>{@link State#SPAWNER_THREW} — the spawner threw (a contract
 *       violation). The cause is non-null. The node must fail.</li>
 * </ul>
 *
 * <p>Immutable. Use the factories.
 *
 * <p>See plan 244 (L4-multi-member-per-task-routing).
 */
public final class MemberExecOutcome {

    /**
     * Terminal state of a single member execution inside a fan-out node.
     */
    public enum State {
        /** Member agent reached {@code AgentExecStatus.completed}. */
        COMPLETED,
        /** Engine future completed exceptionally (BOUND) or spawner returned SPAWN_FAILED. */
        ENGINE_FAILED,
        /** Member agent returned a non-completed terminal status. */
        NOT_COMPLETED,
        /** Spawner honestly declined (NO_SPAWN). */
        NO_SPAWN,
        /** Spawner returned null (defensive contract violation). */
        SPAWNER_NULL,
        /** Spawner threw (defensive contract violation). */
        SPAWNER_THREW
    }

    private final DispatchTarget target;
    private final State state;
    private final AgentExecutionResult executionResult;
    private final Throwable cause;
    private final String reason;

    private MemberExecOutcome(DispatchTarget target, State state,
                              AgentExecutionResult executionResult,
                              Throwable cause, String reason) {
        this.target = Objects.requireNonNull(target, "target");
        this.state = Objects.requireNonNull(state, "state");
        this.executionResult = executionResult;
        this.cause = cause;
        this.reason = reason;
    }

    /**
     * Build a {@link State#COMPLETED} outcome.
     *
     * @param target          the dispatch target (non-null)
     * @param executionResult the member agent's completed result (non-null)
     * @return an immutable COMPLETED outcome
     */
    public static MemberExecOutcome completed(DispatchTarget target, AgentExecutionResult executionResult) {
        Objects.requireNonNull(executionResult, "executionResult");
        return new MemberExecOutcome(target, State.COMPLETED, executionResult, null, null);
    }

    /**
     * Build a {@link State#ENGINE_FAILED} outcome (BOUND engine exception
     * or SPAWN_FAILED).
     *
     * @param target the dispatch target (non-null)
     * @param cause  the failure cause (non-null)
     * @return an immutable ENGINE_FAILED outcome
     */
    public static MemberExecOutcome engineFailed(DispatchTarget target, Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        return new MemberExecOutcome(target, State.ENGINE_FAILED, null, cause, null);
    }

    /**
     * Build a {@link State#NOT_COMPLETED} outcome (member agent returned a
     * non-completed terminal status).
     *
     * @param target          the dispatch target (non-null)
     * @param executionResult the member agent's non-completed result (non-null)
     * @return an immutable NOT_COMPLETED outcome
     */
    public static MemberExecOutcome notCompleted(DispatchTarget target, AgentExecutionResult executionResult) {
        Objects.requireNonNull(executionResult, "executionResult");
        return new MemberExecOutcome(target, State.NOT_COMPLETED, executionResult, null, null);
    }

    /**
     * Build a {@link State#NO_SPAWN} outcome (spawner declined).
     *
     * @param target the dispatch target (non-null)
     * @param reason the spawner's reason (non-null)
     * @return an immutable NO_SPAWN outcome
     */
    public static MemberExecOutcome noSpawn(DispatchTarget target, String reason) {
        Objects.requireNonNull(reason, "reason");
        return new MemberExecOutcome(target, State.NO_SPAWN, null, null, reason);
    }

    /**
     * Build a {@link State#SPAWNER_NULL} outcome (defensive null return).
     *
     * @param target the dispatch target (non-null)
     * @return an immutable SPAWNER_NULL outcome
     */
    public static MemberExecOutcome spawnerNull(DispatchTarget target) {
        return new MemberExecOutcome(target, State.SPAWNER_NULL, null, null, "spawner returned null");
    }

    /**
     * Build a {@link State#SPAWNER_THREW} outcome.
     *
     * @param target the dispatch target (non-null)
     * @param cause  the spawner's thrown exception (non-null)
     * @return an immutable SPAWNER_THREW outcome
     */
    public static MemberExecOutcome spawnerThrew(DispatchTarget target, Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        return new MemberExecOutcome(target, State.SPAWNER_THREW, null, cause, null);
    }

    /**
     * @return the dispatch target this outcome corresponds to.
     */
    public DispatchTarget getTarget() {
        return target;
    }

    /**
     * @return the terminal state.
     */
    public State getState() {
        return state;
    }

    /**
     * @return the member agent's execution result when {@code state ==
     *         COMPLETED} or {@code state == NOT_COMPLETED}; {@code null}
     *         otherwise.
     */
    public AgentExecutionResult getExecutionResult() {
        return executionResult;
    }

    /**
     * @return the failure cause when {@code state == ENGINE_FAILED} or
     *         {@code state == SPAWNER_THREW}; {@code null} otherwise.
     */
    public Throwable getCause() {
        return cause;
    }

    /**
     * @return the honest reason when {@code state == NO_SPAWN} or
     *         {@code state == SPAWNER_NULL}; {@code null} otherwise.
     */
    public String getReason() {
        return reason;
    }

    /**
     * @return {@code true} if this outcome is a success ({@link State#COMPLETED}).
     */
    public boolean isCompleted() {
        return state == State.COMPLETED;
    }

    /**
     * Build an honest {@link NopAiAgentException} describing this failure
     * outcome. Used by the orchestrator / reduction to fast-fail the node.
     *
     * @param taskId the team task id (for the error message)
     * @return a non-null exception describing the failure
     */
    NopAiAgentException toException(String taskId) {
        switch (state) {
            case ENGINE_FAILED:
                return new NopAiAgentException(
                        "nop.ai.team.flow.fanout-member-engine-failed: member target '"
                                + target.getMemberName() + "' engine future failed for taskId=" + taskId, cause);
            case NOT_COMPLETED:
                return new NopAiAgentException(
                        "nop.ai.team.flow.fanout-member-not-completed: member target '"
                                + target.getMemberName() + "' did not complete for taskId=" + taskId
                                + ", status=" + executionResult.getStatus()
                                + (executionResult.getError() != null ? ", error=" + executionResult.getError() : ""));
            case NO_SPAWN:
                return new NopAiAgentException(
                        "nop.ai.team.flow.fanout-no-spawn: spawner declined to spawn member target '"
                                + target.getMemberName() + "' for taskId=" + taskId + ", reason=" + reason);
            case SPAWNER_NULL:
                return new NopAiAgentException(
                        "nop.ai.team.flow.fanout-spawn-null: spawner returned null for member target '"
                                + target.getMemberName() + "' for taskId=" + taskId);
            case SPAWNER_THREW:
                return new NopAiAgentException(
                        "nop.ai.team.flow.fanout-spawn-threw: spawner threw for member target '"
                                + target.getMemberName() + "' for taskId=" + taskId, cause);
            case COMPLETED:
            default:
                throw new IllegalStateException("toException called on a COMPLETED outcome");
        }
    }

    @Override
    public String toString() {
        return "MemberExecOutcome{target=" + target.getMemberName() + ", state=" + state
                + (executionResult != null ? ", execStatus=" + executionResult.getStatus() : "")
                + (reason != null ? ", reason='" + reason + "'" : "")
                + (cause != null ? ", cause=" + cause.getMessage() : "")
                + '}';
    }
}
