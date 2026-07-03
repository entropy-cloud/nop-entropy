package io.nop.job.dao.helper;

import io.nop.job.core._NopJobCoreConstants;

/**
 * Centralized fire/task status judgment utilities.
 *
 * <p>Fire statuses are ordered integers (see {@link _NopJobCoreConstants}):
 * <ul>
 *   <li>Active (cancelable): WAITING(0), DISPATCHING(10), RUNNING(20) — all &lt; FIRE_STATUS_SUCCESS(30)
 *   <li>Terminal (rerunnable): SUCCESS(30), FAILED(40), TIMEOUT(50), CANCELED(60) — all &gt;= FIRE_STATUS_SUCCESS(30)
 * </ul>
 *
 * <p>Task statuses follow the same ordering but include SUSPICIOUS(15) between CLAIMED and RUNNING.
 * The {@link #isFinishedTask} method preserves the exact semantics of the original inline checks
 * (NOT WAITING/CLAIMED/RUNNING), which intentionally treats SUSPICIOUS as finished for cancel-flow
 * purposes — this is different from the resource-reservation set
 * {@code RESERVED_TASK_STATUSES} which includes SUSPICIOUS.
 */
public final class JobStatusHelper {

    private JobStatusHelper() {
    }

    // ---- Fire status checks ----

    /**
     * Whether a fire is in an active (cancelable) status: WAITING, DISPATCHING, or RUNNING.
     * Uses range check: {@code fireStatus < FIRE_STATUS_SUCCESS}.
     */
    public static boolean isActiveFire(Integer fireStatus) {
        return fireStatus != null && fireStatus < _NopJobCoreConstants.FIRE_STATUS_SUCCESS;
    }

    /**
     * Whether a fire is in a terminal (rerunnable) status: SUCCESS, FAILED, TIMEOUT, or CANCELED.
     * Uses range check: {@code fireStatus >= FIRE_STATUS_SUCCESS}.
     */
    public static boolean isTerminalFire(Integer fireStatus) {
        return fireStatus != null && fireStatus >= _NopJobCoreConstants.FIRE_STATUS_SUCCESS;
    }

    // ---- Task status checks ----

    /**
     * Whether a task is considered "finished" for cancel-flow purposes.
     * A task is finished if its status is NOT WAITING, NOT CLAIMED, and NOT RUNNING.
     * Note: SUSPICIOUS(15) is treated as finished here — this is intentional and
     * differs from {@code RESERVED_TASK_STATUSES}.
     */
    public static boolean isFinishedTask(Integer taskStatus) {
        if (taskStatus == null)
            return false;
        return taskStatus != _NopJobCoreConstants.TASK_STATUS_WAITING
                && taskStatus != _NopJobCoreConstants.TASK_STATUS_CLAIMED
                && taskStatus != _NopJobCoreConstants.TASK_STATUS_RUNNING;
    }
}
