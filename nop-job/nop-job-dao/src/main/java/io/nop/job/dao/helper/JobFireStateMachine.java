package io.nop.job.dao.helper;

import io.nop.job.core._NopJobCoreConstants;

import java.util.List;

/**
 * State machine for {@code NopJobFire.fireStatus}: predicates, transition legality
 * (cancel / rerun), the fire-status query sets, and the task→fire aggregation rule.
 *
 * <p>Fire statuses are ordered integers (see {@link _NopJobCoreConstants}):
 * <ul>
 *   <li>Active (cancelable): WAITING(0), DISPATCHING(10), RUNNING(20) — all &lt; FIRE_STATUS_SUCCESS(30)
 *   <li>Terminal (rerunnable): SUCCESS(30), FAILED(40), TIMEOUT(50), CANCELED(60) — all &gt;= FIRE_STATUS_SUCCESS(30)
 * </ul>
 */
public final class JobFireStateMachine {

    private JobFireStateMachine() {
    }

    /**
     * Fire statuses that are still active (not yet terminal): WAITING, DISPATCHING, RUNNING.
     * Query-filter form of {@link #isActive}; use with {@code FilterBeans.in(...)}.
     */
    public static final List<Integer> ACTIVE_STATUSES = List.of(
            _NopJobCoreConstants.FIRE_STATUS_WAITING,
            _NopJobCoreConstants.FIRE_STATUS_DISPATCHING,
            _NopJobCoreConstants.FIRE_STATUS_RUNNING
    );

    /**
     * Fire statuses that the recovery flow can reuse: FAILED, TIMEOUT.
     * Query-filter form of {@link #isRecoverable}; use with {@code FilterBeans.in(...)}.
     */
    public static final List<Integer> RECOVERABLE_STATUSES = List.of(
            _NopJobCoreConstants.FIRE_STATUS_FAILED,
            _NopJobCoreConstants.FIRE_STATUS_TIMEOUT
    );

    /**
     * Whether a fire is in an active (cancelable) status: WAITING, DISPATCHING, or RUNNING.
     * Uses range check: {@code fireStatus < FIRE_STATUS_SUCCESS}.
     */
    public static boolean isActive(Integer fireStatus) {
        return fireStatus != null && fireStatus < _NopJobCoreConstants.FIRE_STATUS_SUCCESS;
    }

    /**
     * Whether a fire is in a terminal (rerunnable) status: SUCCESS, FAILED, TIMEOUT, or CANCELED.
     * Uses range check: {@code fireStatus >= FIRE_STATUS_SUCCESS}.
     */
    public static boolean isTerminal(Integer fireStatus) {
        return fireStatus != null && fireStatus >= _NopJobCoreConstants.FIRE_STATUS_SUCCESS;
    }

    /**
     * Whether a fire can be reused by the recovery (block-strategy RECOVERY) flow:
     * FAILED or TIMEOUT. This is the query counterpart of {@link #RECOVERABLE_STATUSES}.
     */
    public static boolean isRecoverable(Integer fireStatus) {
        return fireStatus != null
                && (fireStatus == _NopJobCoreConstants.FIRE_STATUS_FAILED
                        || fireStatus == _NopJobCoreConstants.FIRE_STATUS_TIMEOUT);
    }

    /**
     * Whether a terminal fire can be manually rerun: any status in {@link #isTerminal}.
     * Also used by the recovery flow to decide reusability of a fire record.
     */
    public static boolean canRerun(Integer fireStatus) {
        return isTerminal(fireStatus);
    }

    /**
     * Whether an active fire can be canceled. A fire whose fireStatus is RUNNING is
     * cancelable only while at least one of its tasks is unfinished — once all tasks are
     * finished the fire should be left for the completion processor to finalize.
     *
     * @param hasUnfinishedTask whether at least one task of this fire is not finished
     *                          (see {@link JobTaskStateMachine#isFinished})
     */
    public static boolean canCancel(Integer fireStatus, boolean hasUnfinishedTask) {
        if (!isActive(fireStatus)) {
            return false;
        }
        if (fireStatus != _NopJobCoreConstants.FIRE_STATUS_RUNNING) {
            return true;
        }
        return hasUnfinishedTask;
    }

    /**
     * Resolves the aggregate fire status from individual task statuses.
     * <p>
     * Priority chain: TIMEOUT &gt; FAILED &gt; CANCELED &gt; SUCCESS.
     * For broadcast fires, a single CANCELED/FAILED/TIMEOUT shard determines
     * the fire's aggregate status. Operators should inspect individual task
     * statuses for partial success details.
     * SUSPICIOUS tasks are treated as pending only while active tasks remain.
     * Once no WAITING/CLAIMED/RUNNING tasks exist, SUSPICIOUS is treated as
     * TIMEOUT (worker unreachable).
     *
     * @return the aggregated terminal fire status, or {@code null} while any task is
     *         still pending (WAITING/CLAIMED/RUNNING, or SUSPICIOUS alongside a pending task)
     */
    public static Integer resolveFinalStatus(List<Integer> taskStatuses) {
        boolean hasPendingTask = false;
        boolean hasTimeoutTask = false;
        boolean hasFailedTask = false;
        boolean hasCanceledTask = false;
        boolean hasSuspiciousTask = false;

        for (Integer taskStatus : taskStatuses) {
            if (taskStatus == null || JobTaskStateMachine.isPending(taskStatus)) {
                hasPendingTask = true;
                continue;
            }
            if (taskStatus == _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS) {
                hasSuspiciousTask = true;
                continue;
            }
            if (taskStatus == _NopJobCoreConstants.TASK_STATUS_TIMEOUT) {
                hasTimeoutTask = true;
            } else if (taskStatus == _NopJobCoreConstants.TASK_STATUS_FAILED) {
                hasFailedTask = true;
            } else if (taskStatus == _NopJobCoreConstants.TASK_STATUS_CANCELED) {
                hasCanceledTask = true;
            }
        }

        if (hasPendingTask) {
            return null;
        }

        if (hasSuspiciousTask) {
            hasTimeoutTask = true;
        }

        if (hasTimeoutTask) {
            return _NopJobCoreConstants.FIRE_STATUS_TIMEOUT;
        }
        if (hasFailedTask) {
            return _NopJobCoreConstants.FIRE_STATUS_FAILED;
        }
        if (hasCanceledTask) {
            return _NopJobCoreConstants.FIRE_STATUS_CANCELED;
        }
        return _NopJobCoreConstants.FIRE_STATUS_SUCCESS;
    }
}