package io.nop.ai.agent.team.scheduler;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of a single {@link ITeamTaskSchedulerDaemon#scanOnce}
 * invocation, for observability and testing. Returned by every scan
 * (including {@link NoOpTeamTaskSchedulerDaemon}, which returns an all-zero
 * instance — an explicit "no scheduling scanning" semantic, not a silent
 * no-op; Minimum Rules #24).
 *
 * <p><b>Field semantics</b>:
 * <ul>
 *   <li>{@code teamsScanned} — number of teams inspected this scan (the
 *       configured target team set, or all active teams when none
 *       configured).</li>
 *   <li>{@code readyCreatedTasks} — number of tasks identified as ready and
 *       in {@link io.nop.ai.agent.team.TeamTaskStatus#CREATED} status this
 *       scan (eligible for claim / dispatch). CLAIMED tasks returned by
 *       {@link io.nop.ai.agent.team.flow.TeamTaskTopology#getReadyTasks()}
 *       (other members' in-progress tasks) are deliberately excluded.</li>
 *   <li>{@code claimedTasks} — number of tasks this daemon successfully CAS-
 *       claimed (CREATED → CLAIMED) this scan. CAS losses (another claimer
 *       raced ahead) are not counted here — they appear in
 *       {@code claimLostTasks}.</li>
 *   <li>{@code claimLostTasks} — number of CREATED ready tasks where the
 *       CAS claim returned empty (lost the race to another claimer). This is
 *       legitimate concurrency, not an error; the daemon silently skips
 *       these without abandoning them (it never owned them).</li>
 *   <li>{@code dispatchedTasks} — number of claimed tasks for which the
 *       daemon invoked {@link io.nop.ai.agent.engine.IAgentEngine#execute}
 *       on a bound member agent this scan.</li>
 *   <li>{@code completedTasks} — number of dispatched tasks that
 *       successfully transitioned CLAIMED → COMPLETED this scan.</li>
 *   <li>{@code abandonedTasks} — number of claimed tasks that failed
 *       dispatch (member agent threw / returned non-completed terminal
 *       status / completeTask CAS lost) and were transitioned to
 *       ABANDONED by this daemon. Only tasks this daemon CAS-claimed are
 *       ever abandoned — never another member's CLAIMED task.</li>
 *   <li>{@code completedTaskIds} / {@code abandonedTaskIds} — the task IDs
 *       backing {@code completedTasks} / {@code abandonedTasks} (for test
 *       assertions and audit). Bounded by the scan result set.</li>
 *   <li>{@code skippedCoordinatedTeams} — number of teams skipped this scan
 *       because another daemon instance held the active scan lease (plan
 *       242 / {@code L4-cross-process-daemon-coordination}). Zero when the
 *       daemon uses the shipped {@code NoOpDaemonCoordinator} (no
 *       cross-process coordination) or when no other instance is contending.
 *       Non-zero is an explicit coordination signal, not a silent skip
 *       (Minimum Rules #24).</li>
 *   <li>{@code scannedAt} — epoch millisecond timestamp captured at scan
 *       start.</li>
 *   <li>{@code scanDurationMs} — wall-clock duration of the scan, in
 *       milliseconds.</li>
 * </ul>
 *
 * <p>See plan 236 (L4-blockedBy-resolution-engine) and plan 242
 * ({@code L4-cross-process-daemon-coordination}).
 */
public final class SchedulerScanResult {

    private final int teamsScanned;
    private final int readyCreatedTasks;
    private final int claimedTasks;
    private final int claimLostTasks;
    private final int dispatchedTasks;
    private final int completedTasks;
    private final int abandonedTasks;
    private final int skippedCoordinatedTeams;
    private final List<String> completedTaskIds;
    private final List<String> abandonedTaskIds;
    private final long scannedAt;
    private final long scanDurationMs;

    public SchedulerScanResult(int teamsScanned, int readyCreatedTasks,
                                int claimedTasks, int claimLostTasks,
                                int dispatchedTasks, int completedTasks, int abandonedTasks,
                                List<String> completedTaskIds, List<String> abandonedTaskIds,
                                long scannedAt, long scanDurationMs) {
        this(teamsScanned, readyCreatedTasks, claimedTasks, claimLostTasks,
                dispatchedTasks, completedTasks, abandonedTasks, 0,
                completedTaskIds, abandonedTaskIds, scannedAt, scanDurationMs);
    }

    /**
     * Fully-parameterized constructor with the {@code skippedCoordinatedTeams}
     * counter (plan 242 / {@code L4-cross-process-daemon-coordination}).
     *
     * @param skippedCoordinatedTeams number of teams skipped this scan
     *                                because another daemon instance held the
     *                                active scan lease (zero with the shipped
     *                                NoOp coordinator)
     */
    public SchedulerScanResult(int teamsScanned, int readyCreatedTasks,
                                int claimedTasks, int claimLostTasks,
                                int dispatchedTasks, int completedTasks, int abandonedTasks,
                                int skippedCoordinatedTeams,
                                List<String> completedTaskIds, List<String> abandonedTaskIds,
                                long scannedAt, long scanDurationMs) {
        this.teamsScanned = teamsScanned;
        this.readyCreatedTasks = readyCreatedTasks;
        this.claimedTasks = claimedTasks;
        this.claimLostTasks = claimLostTasks;
        this.dispatchedTasks = dispatchedTasks;
        this.completedTasks = completedTasks;
        this.abandonedTasks = abandonedTasks;
        this.skippedCoordinatedTeams = skippedCoordinatedTeams;
        this.completedTaskIds = Collections.unmodifiableList(
                Objects.requireNonNull(completedTaskIds, "completedTaskIds must not be null"));
        this.abandonedTaskIds = Collections.unmodifiableList(
                Objects.requireNonNull(abandonedTaskIds, "abandonedTaskIds must not be null"));
        this.scannedAt = scannedAt;
        this.scanDurationMs = scanDurationMs;
    }

    /**
     * All-zero result for the {@link NoOpTeamTaskSchedulerDaemon} default —
     * an explicit "no scheduling scanning" semantic (zero counts, empty id
     * lists, zero duration, zero timestamp), not a silent no-op.
     */
    public static SchedulerScanResult empty() {
        return new SchedulerScanResult(0, 0, 0, 0, 0, 0, 0, 0,
                Collections.emptyList(), Collections.emptyList(), 0L, 0L);
    }

    public int getTeamsScanned() {
        return teamsScanned;
    }

    public int getReadyCreatedTasks() {
        return readyCreatedTasks;
    }

    public int getClaimedTasks() {
        return claimedTasks;
    }

    public int getClaimLostTasks() {
        return claimLostTasks;
    }

    public int getDispatchedTasks() {
        return dispatchedTasks;
    }

    public int getCompletedTasks() {
        return completedTasks;
    }

    public int getAbandonedTasks() {
        return abandonedTasks;
    }

    /**
     * @return number of teams skipped this scan because another daemon
     *         instance held the active scan lease (plan 242). Zero with the
     *         shipped NoOp coordinator.
     */
    public int getSkippedCoordinatedTeams() {
        return skippedCoordinatedTeams;
    }

    public List<String> getCompletedTaskIds() {
        return completedTaskIds;
    }

    public List<String> getAbandonedTaskIds() {
        return abandonedTaskIds;
    }

    public long getScannedAt() {
        return scannedAt;
    }

    public long getScanDurationMs() {
        return scanDurationMs;
    }

    @Override
    public String toString() {
        return "SchedulerScanResult{teamsScanned=" + teamsScanned
                + ", readyCreatedTasks=" + readyCreatedTasks
                + ", claimedTasks=" + claimedTasks
                + ", claimLostTasks=" + claimLostTasks
                + ", dispatchedTasks=" + dispatchedTasks
                + ", completedTasks=" + completedTasks
                + ", abandonedTasks=" + abandonedTasks
                + ", skippedCoordinatedTeams=" + skippedCoordinatedTeams
                + ", completedTaskIds=" + completedTaskIds
                + ", abandonedTaskIds=" + abandonedTaskIds
                + ", scannedAt=" + scannedAt
                + ", scanDurationMs=" + scanDurationMs + '}';
    }
}
