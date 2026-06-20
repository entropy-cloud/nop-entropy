package io.nop.ai.agent.team;

import java.util.List;
import java.util.Objects;

/**
 * Immutable data object representing a single task in a team's shared task
 * store. Created by {@link ITeamTaskStore#createTask} with a fresh UUID
 * {@code taskId} and an initial status of {@link TeamTaskStatus#CREATED}.
 *
 * <p>In the foundational slice (plan 225), {@code TeamTask} instances are
 * only ever created — never mutated. The {@code status} field is always
 * {@link TeamTaskStatus#CREATED}. The {@code CLAIMED} / {@code COMPLETED} /
 * {@code ABANDONED} statuses and the state machine that drives them are
 * reserved for the successor {@code team-task-update} tool (vision §8.2).
 *
 * <p>In the {@code team-task-update} slice (plan 227), {@code claimedBy}
 * records the sessionId of the member that claimed the task; it is
 * {@code null} until the first {@link TeamTaskStatus#CLAIMED} transition
 * and is preserved (not overwritten) by subsequent complete / abandon
 * transitions for audit traceability (design 裁定 6).
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@code taskId} — UUID generated at creation time by the store.</li>
 *   <li>{@code teamId} — the owning team's UUID identity.</li>
 *   <li>{@code subject} — short task title (non-null, non-blank).</li>
 *   <li>{@code description} — optional longer description (may be null).</li>
 *   <li>{@code blockedBy} — list of task IDs that this task depends on
 *       (may be empty; only stored, not resolved in this slice).</li>
 *   <li>{@code status} — the {@link TeamTaskStatus} (CREATED at creation).</li>
 *   <li>{@code createdBy} — the sessionId of the caller that created the task.</li>
 *   <li>{@code claimedBy} — the sessionId of the member that claimed the task
 *       (null until the first CLAIMED transition; preserved thereafter).</li>
 *   <li>{@code claimEpoch} — the claim-generation epoch assigned at the most
 *       recent {@code claimTask} (plan 279 / AR-01). {@code null} while the
 *       task is CREATED (never claimed, or reclaimed back to CREATED); a
 *       positive integer once claimed. Bound in the {@code completeTask} /
 *       {@code abandonTask} CAS so a stale in-flight dispatcher holding a
 *       pre-reclaim epoch cannot complete/abandon a task that was reclaimed
 *       and re-claimed by another owner.</li>
 *   <li>{@code createdAt} — wall-clock timestamp (millis) at creation.</li>
 * </ul>
 *
 * <p>See plan 225 (L4-8-team-tools), plan 227 (team-task-update), and
 * vision §8.2.
 */
public final class TeamTask {

    private final String taskId;
    private final String teamId;
    private final String subject;
    private final String description;
    private final List<String> blockedBy;
    private final TeamTaskStatus status;
    private final String createdBy;
    private final String claimedBy;
    private final Long claimEpoch;
    private final long createdAt;

    /**
     * Construct an immutable team task.
     *
     * @param taskId      UUID-generated task identity (non-null)
     * @param teamId      the owning team's UUID identity (non-null)
     * @param subject     short task title (non-null)
     * @param description optional longer description (may be null)
     * @param blockedBy   list of dependency task IDs (non-null, defensively copied;
     *                    may be empty)
     * @param status      the task status (non-null)
     * @param createdBy   the sessionId of the task creator (non-null)
     * @param claimedBy   the sessionId of the member that claimed the task
     *                    (null until the first CLAIMED transition; preserved
     *                    by subsequent complete/abandon transitions)
     * @param claimEpoch  the claim-generation epoch (plan 279 / AR-01):
     *                    {@code null} while CREATED (never claimed / reclaimed),
     *                    a positive integer once claimed. Bound in the
     *                    {@code completeTask} / {@code abandonTask} CAS.
     * @param createdAt   wall-clock timestamp (millis) at creation
     */
    public TeamTask(String taskId, String teamId, String subject, String description,
                    List<String> blockedBy, TeamTaskStatus status, String createdBy,
                    String claimedBy, Long claimEpoch, long createdAt) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.teamId = Objects.requireNonNull(teamId, "teamId");
        this.subject = Objects.requireNonNull(subject, "subject");
        this.description = description;
        this.blockedBy = List.copyOf(Objects.requireNonNull(blockedBy, "blockedBy"));
        this.status = Objects.requireNonNull(status, "status");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.claimedBy = claimedBy;
        this.claimEpoch = claimEpoch;
        this.createdAt = createdAt;
    }

    /**
     * @return the UUID-generated task identity.
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * @return the owning team's UUID identity.
     */
    public String getTeamId() {
        return teamId;
    }

    /**
     * @return the short task title.
     */
    public String getSubject() {
        return subject;
    }

    /**
     * @return the optional longer description (may be null).
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return an unmodifiable view of the dependency task IDs (may be empty).
     */
    public List<String> getBlockedBy() {
        return blockedBy;
    }

    /**
     * @return the task status (CREATED in the foundational slice).
     */
    public TeamTaskStatus getStatus() {
        return status;
    }

    /**
     * @return the sessionId of the task creator.
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * @return the sessionId of the member that claimed the task, or
     *         {@code null} when the task has not been claimed (CREATED) or
     *         was abandoned directly from CREATED. Preserved (not
     *         overwritten) by complete / abandon transitions for audit
     *         traceability (design 裁定 6).
     */
    public String getClaimedBy() {
        return claimedBy;
    }

    /**
     * @return the claim-generation epoch (plan 279 / AR-01), or {@code null}
     *         while the task is CREATED (never claimed / reclaimed). A
     *         positive integer once claimed; bound in the
     *         {@code completeTask} / {@code abandonTask} CAS so a stale
     *         in-flight dispatcher holding a pre-reclaim epoch cannot
     *         complete/abandon a task reclaimed and re-claimed by another
     *         owner.
     */
    public Long getClaimEpoch() {
        return claimEpoch;
    }

    /**
     * @return wall-clock timestamp (millis) at creation.
     */
    public long getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "TeamTask{taskId='" + taskId + "', teamId='" + teamId
                + "', subject='" + subject + "', status=" + status
                + ", blockedBy=" + blockedBy + ", createdBy='" + createdBy
                + "', claimedBy='" + claimedBy + "', claimEpoch=" + claimEpoch
                + ", createdAt=" + createdAt + '}';
    }
}
