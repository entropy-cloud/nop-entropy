package io.nop.job.dao.helper;

import io.nop.job.core._NopJobCoreConstants;

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