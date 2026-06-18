package io.nop.ai.agent.team.scheduler;

import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.AgentExecutionResult;

import java.util.Objects;

/**
 * Immutable result of {@link io.nop.ai.agent.team.IMemberSpawner#spawnMember}.
 *
 * <p>Encodes the spawner's honest outcome (Minimum Rules #24): never {@code null},
 * never a silent swallow of a spawn attempt. The daemon folds the three
 * possible statuses into its existing dispatch-outcome enum:
 * <ul>
 *   <li>{@link Status#DISPATCHED} → daemon dispatches identically to a
 *       bound-member success/failure (complete on AgentExecStatus.completed,
 *       abandon on anything else / CAS loss).</li>
 *   <li>{@link Status#NO_SPAWN} → daemon {@code UNBOUND_MEMBER} (honest
 *       abandon, identical to the pre-spawn unbound-member path).</li>
 *   <li>{@link Status#SPAWN_FAILED} → daemon {@code DISPATCH_FAILED}
 *       (honest abandon, identical to a bound-member dispatch failure).</li>
 * </ul>
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@code status} — one of {@link Status#DISPATCHED} /
 *       {@link Status#NO_SPAWN} / {@link Status#SPAWN_FAILED} (non-null).</li>
 *   <li>{@code executionResult} — when {@code status == DISPATCHED}, the
 *       {@link AgentExecutionResult} returned by the spawned
 *       {@link io.nop.ai.agent.engine.IAgentEngine#execute}; {@code null}
 *       otherwise. The daemon inspects its {@link AgentExecutionResult#getStatus()}
 *       to decide complete vs. abandon.</li>
 *   <li>{@code spawnedAgentName} — when {@code status == DISPATCHED}, the
 *       agent name the spawner resolved from
 *       {@link io.nop.ai.agent.team.TeamMemberSpec#getAgentModel()} and passed
 *       to {@link AgentMessageRequest}. {@code null} otherwise. Used by the
 *       daemon for logging / audit.</li>
 *   <li>{@code spawnedSessionId} — when {@code status == DISPATCHED}, the
 *       fresh session id the spawner created for the spawned execution;
 *       {@code null} otherwise.</li>
 *   <li>{@code reason} — when {@code status == NO_SPAWN} or
 *       {@code status == SPAWN_FAILED}, a human-readable reason (non-null);
 *       {@code null} when {@code status == DISPATCHED}.</li>
 * </ul>
 *
 * <p>See plan 237 ({@code L4-auto-spawn-member-agent}), design 裁定 4.
 */
public final class SpawnMemberResult {

    /**
     * Outcome of a spawn attempt.
     */
    public enum Status {
        /**
         * The spawner spawned a member agent and executed the task; the
         * wrapped {@link AgentExecutionResult} reports the execution outcome
         * (the daemon decides complete vs. abandon from its
         * {@link AgentExecutionResult#getStatus()}).
         */
        DISPATCHED,

        /**
         * The spawner honestly declined to spawn (e.g. no declarative
         * memberSpec to spawn from, or the NoOp default). The daemon treats
         * this as {@code UNBOUND_MEMBER} (honest abandon, identical to the
         * pre-spawn unbound-member path).
         */
        NO_SPAWN,

        /**
         * The spawner attempted to spawn but the execution threw or returned
         * a non-completed terminal status. The daemon treats this as
         * {@code DISPATCH_FAILED} (honest abandon, identical to a bound-member
         * dispatch failure).
         */
        SPAWN_FAILED
    }

    private final Status status;
    private final AgentExecutionResult executionResult;
    private final String spawnedAgentName;
    private final String spawnedSessionId;
    private final String reason;

    private SpawnMemberResult(Status status, AgentExecutionResult executionResult,
                              String spawnedAgentName, String spawnedSessionId,
                              String reason) {
        this.status = Objects.requireNonNull(status, "status");
        this.executionResult = executionResult;
        this.spawnedAgentName = spawnedAgentName;
        this.spawnedSessionId = spawnedSessionId;
        this.reason = reason;
    }

    /**
     * Build a {@link Status#DISPATCHED} result.
     *
     * @param executionResult the spawned execution's result (non-null)
     * @param spawnedAgentName the agent name the spawner resolved and dispatched
     *                         to (non-null, non-blank)
     * @param spawnedSessionId the fresh session id the spawner created (non-null,
     *                         non-blank)
     * @return an immutable DISPATCHED result
     */
    public static SpawnMemberResult dispatched(AgentExecutionResult executionResult,
                                               String spawnedAgentName,
                                               String spawnedSessionId) {
        Objects.requireNonNull(executionResult, "executionResult");
        Objects.requireNonNull(spawnedAgentName, "spawnedAgentName");
        Objects.requireNonNull(spawnedSessionId, "spawnedSessionId");
        return new SpawnMemberResult(Status.DISPATCHED, executionResult,
                spawnedAgentName, spawnedSessionId, null);
    }

    /**
     * Build a {@link Status#NO_SPAWN} result. The daemon treats this as
     * {@code UNBOUND_MEMBER} (honest abandon).
     *
     * @param reason human-readable reason the spawner declined (non-null)
     * @return an immutable NO_SPAWN result
     */
    public static SpawnMemberResult noSpawn(String reason) {
        Objects.requireNonNull(reason, "reason");
        return new SpawnMemberResult(Status.NO_SPAWN, null, null, null, reason);
    }

    /**
     * Build a {@link Status#SPAWN_FAILED} result. The daemon treats this as
     * {@code DISPATCH_FAILED} (honest abandon).
     *
     * @param reason human-readable reason the spawn execution failed (non-null)
     * @return an immutable SPAWN_FAILED result
     */
    public static SpawnMemberResult spawnFailed(String reason) {
        Objects.requireNonNull(reason, "reason");
        return new SpawnMemberResult(Status.SPAWN_FAILED, null, null, null, reason);
    }

    /**
     * @return the spawn outcome status (never {@code null}).
     */
    public Status getStatus() {
        return status;
    }

    /**
     * @return the spawned execution's result when {@code status == DISPATCHED};
     *         {@code null} otherwise.
     */
    public AgentExecutionResult getExecutionResult() {
        return executionResult;
    }

    /**
     * @return the agent name the spawner resolved and dispatched to when
     *         {@code status == DISPATCHED}; {@code null} otherwise.
     */
    public String getSpawnedAgentName() {
        return spawnedAgentName;
    }

    /**
     * @return the fresh session id the spawner created when
     *         {@code status == DISPATCHED}; {@code null} otherwise.
     */
    public String getSpawnedSessionId() {
        return spawnedSessionId;
    }

    /**
     * @return the human-readable reason when {@code status == NO_SPAWN} or
     *         {@code status == SPAWN_FAILED}; {@code null} when
     *         {@code status == DISPATCHED}.
     */
    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "SpawnMemberResult{status=" + status
                + (spawnedAgentName != null ? ", spawnedAgentName='" + spawnedAgentName + "'" : "")
                + (spawnedSessionId != null ? ", spawnedSessionId='" + spawnedSessionId + "'" : "")
                + (reason != null ? ", reason='" + reason + "'" : "")
                + (executionResult != null
                ? ", executionStatus=" + executionResult.getStatus() : "")
                + '}';
    }
}
