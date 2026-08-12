package io.nop.job.dao.helper;

import io.nop.job.core._NopJobCoreConstants;

import java.util.List;

/**
 * State machine for {@code NopJobTask.taskStatus}: predicates describing which statuses
 * are pending, finished, recoverable, or concurrently finalized.
 *
 * <p>Task statuses are ordered integers (see {@link _NopJobCoreConstants}):
 * <ul>
 *   <li>Pending (non-terminal): WAITING(0), CLAIMED(10), SUSPICIOUS(15), RUNNING(20)
 *   <li>Terminal: SUCCESS(30), FAILED(40), TIMEOUT(50), CANCELED(60)
 * </ul>
 *
 * <p>SUSPICIOUS is intentionally treated as "finished" by {@link #isFinished} but
 * independently by the fire-aggregation logic (it is neither pending nor terminal until
 * the timeout checker resolves it — see {@link JobFireStateMachine#resolveFinalStatus}).
 * The subtle differences between these predicates are the source of most duplicated
 * inline checks across the coordinator / worker / store layers; keep them all here.
 */
public final class JobTaskStateMachine {

    private JobTaskStateMachine() {
    }

    /**
     * Task statuses counted as "in-flight" for concurrency metrics: CLAIMED(10) and RUNNING(20).
     * Strictly tasks that have been dispatched to a worker and are expected to be executing now.
     * Excludes WAITING (not yet dispatched) and SUSPICIOUS (worker lost — counted separately).
     * <p>
     * Intentionally narrower than {@link #RUNNING_LIKE_STATUSES} (which adds SUSPICIOUS for
     * timeout scanning) and than {@code RESERVED_TASK_STATUSES} in {@code NopJobCoreConstants}
     * (which adds WAITING + SUSPICIOUS for resource reservation). Keep the three sets distinct.
     */
    public static final List<Integer> IN_FLIGHT_STATUSES = List.of(
            _NopJobCoreConstants.TASK_STATUS_CLAIMED,
            _NopJobCoreConstants.TASK_STATUS_RUNNING);

    /**
     * Task statuses considered "running-like" for timeout / worker-health scanning:
     * CLAIMED(10), SUSPICIOUS(15), RUNNING(20). Used by {@code fetchRunningTasks} to enumerate
     * every task that is not yet finalized and might need timeout enforcement.
     * <p>
     * Differs from {@link #IN_FLIGHT_STATUSES} by including SUSPICIOUS, and from
     * {@code RESERVED_TASK_STATUSES} by excluding WAITING.
     */
    public static final List<Integer> RUNNING_LIKE_STATUSES = List.of(
            _NopJobCoreConstants.TASK_STATUS_CLAIMED,
            _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS,
            _NopJobCoreConstants.TASK_STATUS_RUNNING);

    public static boolean isWaiting(Integer taskStatus) {
        return taskStatus != null && taskStatus == _NopJobCoreConstants.TASK_STATUS_WAITING;
    }

    public static boolean isClaimed(Integer taskStatus) {
        return taskStatus != null && taskStatus == _NopJobCoreConstants.TASK_STATUS_CLAIMED;
    }

    public static boolean isRunning(Integer taskStatus) {
        return taskStatus != null && taskStatus == _NopJobCoreConstants.TASK_STATUS_RUNNING;
    }

    public static boolean isSuspicious(Integer taskStatus) {
        return taskStatus != null && taskStatus == _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS;
    }

    public static boolean isSuccess(Integer taskStatus) {
        return taskStatus != null && taskStatus == _NopJobCoreConstants.TASK_STATUS_SUCCESS;
    }

    public static boolean isFailed(Integer taskStatus) {
        return taskStatus != null && taskStatus == _NopJobCoreConstants.TASK_STATUS_FAILED;
    }

    public static boolean isTimeout(Integer taskStatus) {
        return taskStatus != null && taskStatus == _NopJobCoreConstants.TASK_STATUS_TIMEOUT;
    }

    public static boolean isCanceled(Integer taskStatus) {
        return taskStatus != null && taskStatus == _NopJobCoreConstants.TASK_STATUS_CANCELED;
    }

    /**
     * Whether a task is currently executing on a worker: CLAIMED or RUNNING. Excludes WAITING
     * (not yet dispatched), SUSPICIOUS (worker lost), and terminal statuses. This is the
     * predicate counterpart of {@link #IN_FLIGHT_STATUSES}; used by the timeout checker's
     * worker-liveness probe (a task can only be marked SUSPICIOUS if its worker is gone and
     * the task is currently in flight).
     */
    public static boolean isInFlight(Integer taskStatus) {
        return taskStatus != null
                && (taskStatus == _NopJobCoreConstants.TASK_STATUS_CLAIMED
                        || taskStatus == _NopJobCoreConstants.TASK_STATUS_RUNNING);
    }

    /**
     * Whether a task is in a pre-execution or execution status: WAITING, CLAIMED, or RUNNING.
     * This is the strict complement of {@link #isFinished} for non-null values (SUSPICIOUS
     * falls on neither side — it is resolved separately by the timeout checker).
     */
    public static boolean isPending(Integer taskStatus) {
        return taskStatus != null
                && (taskStatus == _NopJobCoreConstants.TASK_STATUS_WAITING
                        || taskStatus == _NopJobCoreConstants.TASK_STATUS_CLAIMED
                        || taskStatus == _NopJobCoreConstants.TASK_STATUS_RUNNING);
    }

    /**
     * Whether a task is considered "finished" for cancel-flow purposes.
     * A task is finished if its status is NOT WAITING, NOT CLAIMED, and NOT RUNNING.
     * Note: SUSPICIOUS(15) is treated as finished here — this is intentional and
     * differs from the resource-reservation set {@code RESERVED_TASK_STATUSES} and from
     * {@link #isPending}.
     */
    public static boolean isFinished(Integer taskStatus) {
        if (taskStatus == null)
            return false;
        return taskStatus != _NopJobCoreConstants.TASK_STATUS_WAITING
                && taskStatus != _NopJobCoreConstants.TASK_STATUS_CLAIMED
                && taskStatus != _NopJobCoreConstants.TASK_STATUS_RUNNING;
    }

    /**
     * Whether a task is in a state that the recovery flow can reset back to WAITING.
     * Covers CANCELED, FAILED, TIMEOUT, and SUSPICIOUS. This is the recovery counterpart
     * to {@link #isFinished}: cancel-flow treats SUSPICIOUS as finished (skips it),
     * while recovery treats SUSPICIOUS as resettable (a SUSPICIOUS task whose fire is
     * FAILED/TIMEOUT should get a fresh execution opportunity).
     */
    public static boolean isRecoverable(Integer taskStatus) {
        if (taskStatus == null)
            return false;
        return taskStatus == _NopJobCoreConstants.TASK_STATUS_CANCELED
                || taskStatus == _NopJobCoreConstants.TASK_STATUS_FAILED
                || taskStatus == _NopJobCoreConstants.TASK_STATUS_TIMEOUT
                || taskStatus == _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS;
    }

    /**
     * Whether a task has been concurrently finalized or marked by an external flow
     * (timeout checker or cancel flow) such that the worker must NOT overwrite its result.
     * Covers TIMEOUT, CANCELED, and SUSPICIOUS. A RUNNING task that was independently
     * flipped to one of these (e.g. worker lost → SUSPICIOUS, dispatch timeout → CANCELED)
     * should not be overwritten by a late-arriving execution result.
     */
    public static boolean isConcurrentlyFinalized(Integer taskStatus) {
        if (taskStatus == null)
            return false;
        return taskStatus == _NopJobCoreConstants.TASK_STATUS_TIMEOUT
                || taskStatus == _NopJobCoreConstants.TASK_STATUS_CANCELED
                || taskStatus == _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS;
    }
}