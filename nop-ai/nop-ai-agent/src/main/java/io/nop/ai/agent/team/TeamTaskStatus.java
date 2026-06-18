package io.nop.ai.agent.team;

/**
 * Lifecycle status of a {@link TeamTask}. The four values and their intended
 * transitions form the team-task state machine.
 *
 * <h2>State-transition diagram</h2>
 * <pre>
 *   CREATED ──→ CLAIMED ──→ COMPLETED
 *      │           │  │
 *      │           │  └──← (reclaim, plan 240)
 *      │           └──→ ABANDONED
 *      └──→ ABANDONED
 * </pre>
 * A task may be abandoned from either {@link #CREATED} (a lead gives up on an
 * unclaimed task) or {@link #CLAIMED} (a claimer gives up). {@link #COMPLETED}
 * and {@link #ABANDONED} are terminal. A {@code CLAIMED} task may also be
 * <strong>reclaimed</strong> back to {@link #CREATED} (plan 240 /
 * {@link ITeamTaskStore#reclaimTask}) — this is the recovery transition that
 * un-sticks a CLAIMED task whose claimer has disappeared (crash / orphaned
 * session / timeout), making it re-claimable by another member. Reclaim
 * clears {@code claimedBy} to {@code null}; the task is then in the same
 * CREATED state as at creation time.
 *
 * <h2>State machine implementation (plan 227 / 240)</h2>
 * The {@code team-task-update} tool drives the forward transitions above via
 * {@link ITeamTaskStore#claimTask}, {@link ITeamTaskStore#completeTask}, and
 * {@link ITeamTaskStore#abandonTask}; {@link ITeamTaskStore#reclaimTask}
 * (plan 240) drives the {@code CLAIMED → CREATED} recovery transition.
 * An illegal transition (e.g. {@code COMPLETED → CLAIMED}) returns
 * {@code Optional.empty()} (CAS-failure semantics, non-exception control
 * flow). Concurrency control: at most one claimer wins a
 * {@code CREATED → CLAIMED} race (in-memory
 * {@code ConcurrentHashMap.compute} CAS / DB conditional {@code UPDATE}
 * affected-row-count CAS).
 *
 * <h2>Foundational slice (plan 225)</h2>
 * That slice only <strong>creates</strong> tasks ({@link #CREATED}). The
 * {@code team-task-create} tool never transitions a task out of CREATED.
 *
 * <p>See plan 225 (L4-8-team-tools), plan 227 (team-task-update), and
 * vision §8.2.
 */
public enum TeamTaskStatus {
    /**
     * Initial state: the task has been created in the team's shared task
     * store but has not yet been claimed by any member. Transitions to
     * {@link #CLAIMED} when a member claims it, or directly to
     * {@link #ABANDONED} when abandoned before being claimed
     * ({@code team-task-update} tool).
     */
    CREATED,

    /**
     * The task has been claimed by a member and is being worked on.
     * Transitions to {@link #COMPLETED} on successful completion or
     * {@link #ABANDONED} if the claimer gives up ({@code team-task-update}
     * tool). May also be reclaimed back to {@link #CREATED} (plan 240,
     * {@code reclaimTask}) when the claimer has disappeared (crash /
     * orphaned session / timeout) and the task must be re-claimable by
     * another member — the recovery transition for un-sticking a stuck
     * CLAIMED task.
     */
    CLAIMED,

    /**
     * Terminal state: the task has been completed successfully.
     */
    COMPLETED,

    /**
     * Terminal state: the task was abandoned (a claimer gave up without
     * completing, or a lead abandoned it before it was claimed). May be
     * re-claimable in a successor slice.
     */
    ABANDONED
}
