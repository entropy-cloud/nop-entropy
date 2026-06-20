package io.nop.ai.agent.team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Functional {@link ITeamTaskStore} backed by two {@link ConcurrentHashMap}
 * indices: {@code taskId → TeamTask} (primary) and {@code teamId → taskIds}
 * (per-team secondary index for {@link #getTasksByTeam}).
 *
 * <h2>Index consistency</h2>
 * {@link #createTask} writes both indices atomically: the primary index is
 * written first via {@code putIfAbsent} (fresh UUID guarantees no collision),
 * then the team index entry is appended via {@code compute} on the team's
 * task-id list. A concurrent reader never sees a primary-index entry without
 * a corresponding team-index entry (and vice-versa is harmless since
 * {@code getTasksByTeam} re-resolves each id against the primary index).
 *
 * <h2>State transitions (plan 227 / team-task-update, plan 240 / reclaim)</h2>
 * {@link #claimTask} / {@link #completeTask} / {@link #abandonTask} /
 * {@link #reclaimTask} perform an atomic compare-and-swap on the primary
 * index via {@code tasks.compute}: the remapping function validates the
 * current status is a legal source state and — only on success — replaces
 * the value with a new immutable {@link TeamTask} carrying the target
 * status. On a failed CAS (illegal source status or missing task) the
 * value is left unchanged and the method returns {@code Optional.empty()}.
 * {@code ConcurrentHashMap.compute} serialises the remapping per key, so at
 * most one concurrent claimer wins a CREATED→CLAIMED race (and at most one
 * concurrent reclaimer wins a CLAIMED→CREATED race).
 *
 * <h2>Snapshot semantics</h2>
 * {@link #getTasksByTeam} and {@link #getTasksByCreator} return unmodifiable
 * snapshot lists — mutations to the returned lists do not affect the store,
 * and vice-versa.
 *
 * <h2>Thread safety</h2>
 * Both indices are {@link ConcurrentHashMap}. Per-team list appends are
 * serialised by {@code compute} on the team's list slot; cross-team
 * operations are independent. Transitions are serialised per taskId by
 * {@code compute} on the primary index.
 *
 * <p>See plan 225 (L4-8-team-tools), plan 227 (team-task-update), and
 * vision §8.2.
 */
public final class InMemoryTeamTaskStore implements ITeamTaskStore {

    private final ConcurrentHashMap<String, TeamTask> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> teamIndex = new ConcurrentHashMap<>();

    @Override
    public TeamTask createTask(String teamId, String subject, String description,
                               List<String> blockedBy, String createdBy) {
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(blockedBy, "blockedBy");
        Objects.requireNonNull(createdBy, "createdBy");
        if (subject.isEmpty()) {
            throw new IllegalArgumentException(
                    "InMemoryTeamTaskStore.createTask: subject must not be empty");
        }

        String taskId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        TeamTask task = new TeamTask(taskId, teamId, subject, description,
                blockedBy, TeamTaskStatus.CREATED, createdBy, null, null, now);

        TeamTask prev = tasks.putIfAbsent(taskId, task);
        // taskId is a fresh UUID, so prev is always null; defensive check.
        if (prev != null) {
            throw new IllegalStateException(
                    "InMemoryTeamTaskStore.createTask: taskId collision detected: " + taskId);
        }

        teamIndex.compute(teamId, (id, list) -> {
            List<String> mutable = list != null ? new ArrayList<>(list) : new ArrayList<>();
            mutable.add(taskId);
            return Collections.unmodifiableList(mutable);
        });

        return task;
    }

    @Override
    public Optional<TeamTask> getTask(String taskId) {
        if (taskId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tasks.get(taskId));
    }

    @Override
    public List<TeamTask> getTasksByTeam(String teamId) {
        if (teamId == null) {
            return List.of();
        }
        List<String> taskIds = teamIndex.get(teamId);
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        List<TeamTask> snapshot = new ArrayList<>(taskIds.size());
        for (String id : taskIds) {
            TeamTask task = tasks.get(id);
            if (task != null) {
                snapshot.add(task);
            }
        }
        return Collections.unmodifiableList(snapshot);
    }

    @Override
    public List<TeamTask> getTasksByCreator(String createdBy) {
        if (createdBy == null) {
            return List.of();
        }
        List<TeamTask> snapshot = new ArrayList<>();
        for (TeamTask task : tasks.values()) {
            if (createdBy.equals(task.getCreatedBy())) {
                snapshot.add(task);
            }
        }
        return Collections.unmodifiableList(snapshot);
    }

    @Override
    public Optional<TeamTask> claimTask(String taskId, String claimedBy) {
        if (taskId == null) {
            return Optional.empty();
        }
        Objects.requireNonNull(claimedBy, "claimedBy");
        // Claim assigns a fresh epoch atomically inside the per-key compute
        // CAS (plan 279 / AR-01). The new epoch is returned on the result.
        AtomicReference<TeamTask> resultHolder = new AtomicReference<>();
        tasks.compute(taskId, (id, existing) -> {
            if (existing == null || existing.getStatus() != TeamTaskStatus.CREATED) {
                return existing;
            }
            long prevEpoch = existing.getClaimEpoch() != null ? existing.getClaimEpoch() : 0L;
            long newEpoch = prevEpoch + 1;
            TeamTask updated = new TeamTask(
                    existing.getTaskId(), existing.getTeamId(),
                    existing.getSubject(), existing.getDescription(),
                    existing.getBlockedBy(), TeamTaskStatus.CLAIMED,
                    existing.getCreatedBy(), claimedBy, newEpoch,
                    existing.getCreatedAt());
            resultHolder.set(updated);
            return updated;
        });
        return Optional.ofNullable(resultHolder.get());
    }

    @Override
    public Optional<TeamTask> completeTask(String taskId, String completedBy, Long claimEpoch) {
        if (taskId == null) {
            return Optional.empty();
        }
        Objects.requireNonNull(completedBy, "completedBy");
        // CAS verifies the caller's epoch matches the row's current epoch
        // (plan 279 / AR-01). A stale in-flight owner is rejected.
        return transition(taskId, TeamTaskStatus.CLAIMED, TeamTaskStatus.COMPLETED,
                completedBy, false, claimEpoch);
    }

    @Override
    public Optional<TeamTask> abandonTask(String taskId, String abandonedBy, Long claimEpoch) {
        if (taskId == null) {
            return Optional.empty();
        }
        Objects.requireNonNull(abandonedBy, "abandonedBy");
        // abandon is legal from CLAIMED (epoch-bound owner) or CREATED
        // (epoch-agnostic: any unclaimed / reclaimed CREATED task — a CREATED
        // task has no owner to bind, so the CREATED branch ignores epoch).
        // Try CLAIMED→ABANDONED first with the epoch CAS; on failure try
        // CREATED→ABANDONED (which matches regardless of epoch value).
        Optional<TeamTask> fromClaimed = transition(taskId,
                TeamTaskStatus.CLAIMED, TeamTaskStatus.ABANDONED, abandonedBy, false, claimEpoch);
        if (fromClaimed.isPresent()) {
            return fromClaimed;
        }
        return transition(taskId, TeamTaskStatus.CREATED,
                TeamTaskStatus.ABANDONED, abandonedBy, false, claimEpoch);
    }

    @Override
    public Optional<TeamTask> reclaimTask(String taskId, String reclaimedBy) {
        if (taskId == null) {
            return Optional.empty();
        }
        Objects.requireNonNull(reclaimedBy, "reclaimedBy");
        // reclaim is CLAIMED→CREATED: clear claimedBy (plan 240) but PRESERVE
        // claimEpoch. claimTask assigns the next epoch via prevEpoch+1, so
        // keeping the prior epoch across a reclaim makes the next claim's
        // epoch strictly larger than the abandoned claim's epoch. Nulling it
        // here would reset the next epoch back to 1 — collapsing it with the
        // stale in-flight owner's epoch and REOPENING the shared-daemon-id
        // double-execution window that plan 279 exists to close. The abandon
        // CREATED branch is epoch-agnostic, so a preserved epoch does not
        // block a lead's CREATED abandon after reclaim. Inlined CAS (the
        // generic transition helper's overwriteClaimedBy path does not fit).
        AtomicReference<TeamTask> resultHolder = new AtomicReference<>();
        tasks.compute(taskId, (id, existing) -> {
            if (existing == null) {
                return null;
            }
            if (existing.getStatus() != TeamTaskStatus.CLAIMED) {
                return existing;
            }
            TeamTask updated = new TeamTask(
                    existing.getTaskId(), existing.getTeamId(),
                    existing.getSubject(), existing.getDescription(),
                    existing.getBlockedBy(), TeamTaskStatus.CREATED,
                    existing.getCreatedBy(), null, // claimedBy cleared
                    existing.getClaimEpoch(), // claimEpoch PRESERVED (monotonic)
                    existing.getCreatedAt());
            resultHolder.set(updated);
            return updated;
        });
        return Optional.ofNullable(resultHolder.get());
    }

    /**
     * Atomically transition the task identified by {@code taskId} from
     * {@code expectedStatus} to {@code targetStatus} via a single
     * {@code tasks.compute} (per-key CAS). On a legal transition the value
     * is replaced with a new immutable {@link TeamTask}; on an illegal
     * transition (wrong source status / epoch mismatch) or a missing task
     * the value is left unchanged and {@code Optional.empty()} is returned.
     *
     * @param taskId              the task identity
     * @param expectedStatus      the required current status (CAS guard)
     * @param targetStatus        the target status
     * @param actorSessionId      the sessionId driving the transition
     * @param overwriteClaimedBy  when {@code true}, the new task's
     *                            {@code claimedBy} is set to
     *                            {@code actorSessionId} (claim transition);
     *                            when {@code false}, the existing
     *                            {@code claimedBy} is preserved
     *                            (complete/abandon, design 裁定 6)
     * @param expectedClaimEpoch  the claim epoch the caller captured at claim
     *                            time (plan 279 / AR-01). The CAS fails
     *                            (returns empty) unless it equals the row's
     *                            current epoch; {@code null} matches only a
     *                            null row epoch (CREATED source).
     * @return the updated task, or empty on illegal transition / missing task
     */
    private Optional<TeamTask> transition(String taskId,
                                          TeamTaskStatus expectedStatus,
                                          TeamTaskStatus targetStatus,
                                          String actorSessionId,
                                          boolean overwriteClaimedBy,
                                          Long expectedClaimEpoch) {
        AtomicReference<TeamTask> resultHolder = new AtomicReference<>();
        tasks.compute(taskId, (id, existing) -> {
            if (existing == null) {
                // Missing task: leave absent (compute returning null keeps no mapping).
                return null;
            }
            if (existing.getStatus() != expectedStatus) {
                // Illegal source status: leave the value unchanged.
                return existing;
            }
            // Epoch CAS (plan 279): the caller's captured epoch must match the
            // row's current epoch — but ONLY for CLAIMED-source transitions
            // (complete / CLAIMED-branch abandon), where an owner must be
            // bound. A CREATED-source abandon has no current owner, so it is
            // epoch-agnostic (matches any CREATED task, including a reclaimed
            // one whose epoch was preserved to stay monotonic). Enforcing the
            // epoch CAS on a CREATED abandon would drop reclaimed tasks.
            if (expectedStatus == TeamTaskStatus.CLAIMED
                    && !java.util.Objects.equals(existing.getClaimEpoch(), expectedClaimEpoch)) {
                return existing;
            }
            String newClaimedBy = overwriteClaimedBy
                    ? actorSessionId
                    : existing.getClaimedBy();
            TeamTask updated = new TeamTask(
                    existing.getTaskId(), existing.getTeamId(),
                    existing.getSubject(), existing.getDescription(),
                    existing.getBlockedBy(), targetStatus,
                    existing.getCreatedBy(), newClaimedBy,
                    existing.getClaimEpoch(),
                    existing.getCreatedAt());
            resultHolder.set(updated);
            return updated;
        });
        return Optional.ofNullable(resultHolder.get());
    }
}
