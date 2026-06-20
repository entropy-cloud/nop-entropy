package io.nop.ai.agent.team;

import java.util.List;
import java.util.Optional;

/**
 * Contract for a team's shared task store. {@code team-task-create} tool and
 * successor tools ({@code team-task-update}) read and mutate tasks through
 * this contract.
 *
 * <p>When the shipped {@link NoOpTeamTaskStore} default is wired,
 * {@link #createTask} throws {@link UnsupportedOperationException} (fast
 * failure, not silent success — Minimum Rules #24) and all read operations
 * return empty results, so the engine sees zero behaviour regression. When a
 * functional implementation (e.g. {@link InMemoryTeamTaskStore}) is wired,
 * integrators / team tools can create and query shared tasks.
 *
 * <h2>Foundational slice (plan 225)</h2>
 * This contract only defines creation and read operations. State transitions
 * (claim / complete / abandon) are a successor ({@code team-task-update} tool,
 * vision §8.2). The {@code blockedBy} dependency list is stored verbatim but
 * not resolved — dependency resolution is a successor.
 *
 * <h2>State machine (plan 227 / team-task-update, plan 240 / reclaim,
 * plan 279 / claim-epoch CAS)</h2>
 * The four transition methods {@link #claimTask} / {@link #completeTask} /
 * {@link #abandonTask} / {@link #reclaimTask} drive the {@link TeamTaskStatus}
 * state machine. Legal transitions:
 * <ul>
 *   <li>{@code claimTask}: {@code CREATED → CLAIMED} (records claimedBy and
 *       assigns a fresh monotonically-increasing {@code claimEpoch} — plan 279
 *       / AR-01). The assigned epoch is returned on the resulting
 *       {@link TeamTask#getClaimEpoch()}.</li>
 *   <li>{@code completeTask}: {@code CLAIMED → COMPLETED}. The CAS verifies
 *       the caller-supplied {@code claimEpoch} matches the row's current
 *       {@code CLAIM_EPOCH}, so a stale in-flight dispatcher holding an epoch
 *       from a pre-reclaim claim cannot complete a task that was reclaimed
 *       and re-claimed by another owner — closing the shared-daemon-id
 *       double-execution window.</li>
 *   <li>{@code abandonTask}: {@code CREATED → ABANDONED} or
 *       {@code CLAIMED → ABANDONED}. The CLAIMED branch verifies the epoch
 *       (owner binding); the CREATED branch is epoch-agnostic (matches any
 *       CREATED task, including one reclaimed back to CREATED).</li>
 *   <li>{@code reclaimTask} (plan 240, augmented by plan 279):
 *       {@code CLAIMED → CREATED} — recovery transition that un-sticks a
 *       CLAIMED task whose claimer has disappeared (crash / orphaned session
 *       / timeout). Clears {@code claimedBy} to {@code null} but
 *       <b>preserves</b> {@code claimEpoch} (keeps it monotonic) so the task
 *       is re-claimable by another member via {@link #claimTask} under a
 *       strictly-larger epoch.</li>
 * </ul>
 * An illegal transition (wrong source status), a missing task, or a CAS race
 * loss returns {@code Optional.empty()} — non-exception control flow. The
 * caller (e.g. {@code team-task-update} or the recovery daemon) turns empty
 * into an honest error result for the LLM, not an exception. Concurrent
 * claimers race via an atomic compare-and-swap (in-memory {@code compute} /
 * DB conditional {@code UPDATE} affected-row-count) — at most one wins.
 *
 * <h2>Thread safety</h2>
 * Implementations must be safe for concurrent calls from multiple threads.
 * {@link InMemoryTeamTaskStore} achieves this via {@code ConcurrentHashMap}
 * dual indices + atomic {@code compute} CAS on transitions.
 *
 * <p>See plan 225 (L4-8-team-tools), plan 227 (team-task-update), and
 * vision §8.2.
 */
public interface ITeamTaskStore {

    /**
     * Create a new task in the team's shared store. The store generates a
     * fresh UUID {@code taskId}, sets the initial status to
     * {@link TeamTaskStatus#CREATED}, and records the creation timestamp.
     *
     * @param teamId      the owning team's UUID identity (non-null)
     * @param subject     short task title (non-null)
     * @param description optional longer description (may be null)
     * @param blockedBy   list of dependency task IDs (non-null, may be empty;
     *                    only stored, not resolved in this slice)
     * @param createdBy   the sessionId of the task creator (non-null)
     * @return the created {@link TeamTask} with a fresh taskId and CREATED status
     */
    TeamTask createTask(String teamId, String subject, String description,
                        List<String> blockedBy, String createdBy);

    /**
     * Look up a single task by its runtime identity.
     *
     * @param taskId the UUID task identity
     * @return the task, or empty if no task with this id exists
     */
    Optional<TeamTask> getTask(String taskId);

    /**
     * Return a snapshot list of all tasks belonging to the given team.
     *
     * @param teamId the owning team's UUID identity
     * @return an unmodifiable snapshot list (never null; empty when no tasks)
     */
    List<TeamTask> getTasksByTeam(String teamId);

    /**
     * Return a snapshot list of all tasks created by the given creator.
     *
     * @param createdBy the sessionId of the task creator
     * @return an unmodifiable snapshot list (never null; empty when no tasks)
     */
    List<TeamTask> getTasksByCreator(String createdBy);

    /**
     * Claim a task: transition {@link TeamTaskStatus#CREATED} →
     * {@link TeamTaskStatus#CLAIMED}, record the claimer's sessionId in
     * {@code claimedBy}, and assign a fresh monotonically-increasing
     * {@code claimEpoch} (plan 279 / AR-01). Concurrency control is an atomic
     * compare-and-swap — at most one concurrent claimer wins a race for the
     * same task. The assigned epoch is available on the returned
     * {@link TeamTask#getClaimEpoch()} and MUST be threaded by the caller
     * into the subsequent {@link #completeTask} / {@link #abandonTask} CAS
     * so a stale in-flight dispatcher cannot complete/abandon a task that
     * was reclaimed and re-claimed under a new epoch.
     *
     * @param taskId    the UUID task identity
     * @param claimedBy the sessionId of the claiming member (non-null)
     * @return the updated task with status CLAIMED, claimedBy set, and
     *         claimEpoch populated; or empty if the task does not exist or
     *         its current status is not CREATED (already claimed / completed
     *         / abandoned, or lost a CAS race)
     */
    Optional<TeamTask> claimTask(String taskId, String claimedBy);

    /**
     * Complete a claimed task: transition {@link TeamTaskStatus#CLAIMED} →
     * {@link TeamTaskStatus#COMPLETED}. The CAS verifies the caller-supplied
     * {@code claimEpoch} matches the row's current {@code CLAIM_EPOCH}
     * (plan 279 / AR-01), so a stale in-flight dispatcher holding an epoch
     * from a pre-reclaim claim cannot complete a task that was reclaimed and
     * re-claimed by another owner. The recorded {@code claimedBy} is
     * preserved (design 裁定 6: complete does not overwrite claimedBy).
     *
     * @param taskId      the UUID task identity
     * @param completedBy the sessionId of the completing member (non-null);
     *                    recorded for audit only — claimedBy is preserved
     * @param claimEpoch  the claim epoch captured by the caller at
     *                    {@link #claimTask} time (non-null for a genuinely
     *                    CLAIMED task; passing {@code null} or a stale epoch
     *                    makes the CAS fail honestly and return empty)
     * @return the updated task with status COMPLETED, or empty if the task
     *         does not exist, its current status is not CLAIMED, or the
     *         epoch does not match (a CREATED task must be claimed first; a
     *         terminal task cannot transition; a reclaimed+re-claimed task
     *         has a newer epoch)
     */
    Optional<TeamTask> completeTask(String taskId, String completedBy, Long claimEpoch);

    /**
     * Abandon a task: transition {@link TeamTaskStatus#CREATED} or
     * {@link TeamTaskStatus#CLAIMED} → {@link TeamTaskStatus#ABANDONED}.
     * A lead may abandon an unclaimed task (CREATED → ABANDONED); a claimer
     * may give up (CLAIMED → ABANDONED). Terminal statuses (COMPLETED /
     * ABANDONED) cannot transition.
     *
     * <p>For the CLAIMED branch the CAS verifies the caller-supplied
     * {@code claimEpoch} (plan 279 / AR-01, owner binding); the CREATED
     * branch is epoch-agnostic (matches any CREATED task, including one
     * reclaimed back to CREATED — a CREATED task has no owner to bind).
     *
     * @param taskId       the UUID task identity
     * @param abandonedBy  the sessionId of the abandoning member (non-null)
     * @param claimEpoch   the claim epoch captured at {@link #claimTask}
     *                     time for a CLAIMED-task abandon (owner binding);
     *                     {@code null} for a CREATED-task abandon (an
     *                     unclaimed task, or one reclaimed back to CREATED).
     *                     A stale epoch for a CLAIMED task makes the CAS fail
     *                     honestly and return empty.
     * @return the updated task with status ABANDONED, or empty if the task
     *         does not exist, is already terminal (COMPLETED / ABANDONED),
     *         or the CLAIMED-branch epoch does not match
     */
    Optional<TeamTask> abandonTask(String taskId, String abandonedBy, Long claimEpoch);

    /**
     * Reclaim a stuck task: transition {@link TeamTaskStatus#CLAIMED} →
     * {@link TeamTaskStatus#CREATED} and clear {@code claimedBy} to
     * {@code null} (plan 240). This is the recovery transition that
     * un-sticks a CLAIMED task whose claimer has disappeared (process crash
     * / session orphaned / timeout) — after reclaim the task is in the same
     * CREATED state as at creation and may be re-claimed by another member
     * via {@link #claimTask}. The recovery daemon
     * ({@code DefaultTeamTaskRecoveryHandler}) performs this via a
     * semantically-equivalent raw JDBC UPDATE; this store method exists for
     * state-machine completeness, in-memory team support, and future LLM
     * tool consumption.
     *
     * <p>An illegal source status (CREATED / COMPLETED / ABANDONED) or a
     * missing task returns {@code Optional.empty()} (CAS-failure semantics,
     * non-exception control flow). Terminal statuses (COMPLETED /
     * ABANDONED) are not recoverable — reclaim only resets a stuck
     * non-terminal CLAIMED task, it never resurrects a terminal task. Plan
     * 279 PRESERVES {@code claimEpoch} across reclaim (rather than nulling
     * it) so the next claim assigns a strictly-larger epoch — closing the
     * shared-daemon-id double-execution window.
     *
     * @param taskId       the UUID task identity
     * @param reclaimedBy  the sessionId / actor identity driving the
     *                     reclaim (non-null; recorded for audit only —
     *                     {@code claimedBy} is cleared, not overwritten
     *                     with this value)
     * @return the updated task with status CREATED and claimedBy=null, or
     *         empty if the task does not exist or its current status is not
     *         CLAIMED (already CREATED / completed / abandoned, or lost a
     *         CAS race)
     */
    Optional<TeamTask> reclaimTask(String taskId, String reclaimedBy);
}
