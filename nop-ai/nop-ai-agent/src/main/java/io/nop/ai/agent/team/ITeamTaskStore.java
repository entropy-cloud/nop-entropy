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
 * <h2>State machine (plan 227 / team-task-update)</h2>
 * The three transition methods {@link #claimTask} / {@link #completeTask} /
 * {@link #abandonTask} drive the {@link TeamTaskStatus} state machine. Legal
 * transitions:
 * <ul>
 *   <li>{@code claimTask}: {@code CREATED → CLAIMED} (records claimedBy).</li>
 *   <li>{@code completeTask}: {@code CLAIMED → COMPLETED}.</li>
 *   <li>{@code abandonTask}: {@code CREATED → ABANDONED} or
 *       {@code CLAIMED → ABANDONED}.</li>
 * </ul>
 * An illegal transition (wrong source status), a missing task, or a CAS race
 * loss returns {@code Optional.empty()} — non-exception control flow. The
 * caller (e.g. {@code team-task-update}) turns empty into an honest error
 * result for the LLM, not an exception. Concurrent claimers race via an
 * atomic compare-and-swap (in-memory {@code compute} / DB conditional
 * {@code UPDATE} affected-row-count) — at most one wins.
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
     * {@link TeamTaskStatus#CLAIMED} and record the claimer's sessionId in
     * {@code claimedBy}. Concurrency control is an atomic compare-and-swap —
     * at most one concurrent claimer wins a race for the same task.
     *
     * @param taskId    the UUID task identity
     * @param claimedBy the sessionId of the claiming member (non-null)
     * @return the updated task with status CLAIMED and claimedBy set, or
     *         empty if the task does not exist or its current status is not
     *         CREATED (already claimed / completed / abandoned, or lost a CAS
     *         race)
     */
    Optional<TeamTask> claimTask(String taskId, String claimedBy);

    /**
     * Complete a claimed task: transition {@link TeamTaskStatus#CLAIMED} →
     * {@link TeamTaskStatus#COMPLETED}. The recorded {@code claimedBy} is
     * preserved (design 裁定 6: complete does not overwrite claimedBy).
     *
     * @param taskId      the UUID task identity
     * @param completedBy the sessionId of the completing member (non-null;
     *                    recorded for validation only — claimedBy is preserved)
     * @return the updated task with status COMPLETED, or empty if the task
     *         does not exist or its current status is not CLAIMED (a CREATED
     *         task must be claimed first; a terminal task cannot transition)
     */
    Optional<TeamTask> completeTask(String taskId, String completedBy);

    /**
     * Abandon a task: transition {@link TeamTaskStatus#CREATED} or
     * {@link TeamTaskStatus#CLAIMED} → {@link TeamTaskStatus#ABANDONED}.
     * A lead may abandon an unclaimed task (CREATED → ABANDONED); a claimer
     * may give up (CLAIMED → ABANDONED). Terminal statuses (COMPLETED /
     * ABANDONED) cannot transition.
     *
     * @param taskId       the UUID task identity
     * @param abandonedBy  the sessionId of the abandoning member (non-null)
     * @return the updated task with status ABANDONED, or empty if the task
     *         does not exist or is already terminal (COMPLETED / ABANDONED)
     */
    Optional<TeamTask> abandonTask(String taskId, String abandonedBy);
}
