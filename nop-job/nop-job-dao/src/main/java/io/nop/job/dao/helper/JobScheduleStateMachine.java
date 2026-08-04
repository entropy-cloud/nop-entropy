package io.nop.job.dao.helper;

import io.nop.job.core._NopJobCoreConstants;

/**
 * State machine for {@code NopJobSchedule.scheduleStatus}: predicates and per-operation
 * source-status legality. Each {@code canXxx} method is the single source of truth for the
 * allowed-source-status rules that used to be hand-written as vararg arrays in
 * {@code NopJobScheduleBizModel} ({@code validateScheduleStatus}).
 *
 * <p>Schedule statuses (see {@link _NopJobCoreConstants}):
 * DISABLED(0), ENABLED(10), PAUSED(20), COMPLETED(30), ARCHIVED(40).
 * ARCHIVED is a terminal sink: no operation is legal from it.
 */
public final class JobScheduleStateMachine {

    private JobScheduleStateMachine() {
    }

    public static boolean isDisabled(Integer scheduleStatus) {
        return scheduleStatus != null
                && scheduleStatus == _NopJobCoreConstants.SCHEDULE_STATUS_DISABLED;
    }

    public static boolean isEnabled(Integer scheduleStatus) {
        return scheduleStatus != null
                && scheduleStatus == _NopJobCoreConstants.SCHEDULE_STATUS_ENABLED;
    }

    public static boolean isPaused(Integer scheduleStatus) {
        return scheduleStatus != null
                && scheduleStatus == _NopJobCoreConstants.SCHEDULE_STATUS_PAUSED;
    }

    public static boolean isCompleted(Integer scheduleStatus) {
        return scheduleStatus != null
                && scheduleStatus == _NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED;
    }

    public static boolean isArchived(Integer scheduleStatus) {
        return scheduleStatus != null
                && scheduleStatus == _NopJobCoreConstants.SCHEDULE_STATUS_ARCHIVED;
    }

    /**
     * enableSchedule is legal only from DISABLED. Re-enabling an already-ENABLED schedule
     * is an invalid transition (reported as a status error by the BizModel).
     */
    public static boolean canEnable(Integer scheduleStatus) {
        return isDisabled(scheduleStatus);
    }

    /**
     * disableSchedule is legal from ENABLED or PAUSED. A DISABLED schedule is already in
     * the target state and is short-circuited by the BizModel before this check.
     */
    public static boolean canDisable(Integer scheduleStatus) {
        return isEnabled(scheduleStatus) || isPaused(scheduleStatus);
    }

    /**
     * pauseSchedule is legal only from ENABLED. A PAUSED schedule is already in the target
     * state and is short-circuited by the BizModel before this check.
     */
    public static boolean canPause(Integer scheduleStatus) {
        return isEnabled(scheduleStatus);
    }

    /**
     * resumeSchedule is legal only from PAUSED.
     */
    public static boolean canResume(Integer scheduleStatus) {
        return isPaused(scheduleStatus);
    }

    /**
     * archiveSchedule is legal from any non-terminal status: ENABLED, DISABLED, PAUSED, or
     * COMPLETED. An ARCHIVED schedule is short-circuited by the BizModel before this check.
     */
    public static boolean canArchive(Integer scheduleStatus) {
        return isEnabled(scheduleStatus) || isDisabled(scheduleStatus)
                || isPaused(scheduleStatus) || isCompleted(scheduleStatus);
    }

    /**
     * Manual trigger (triggerNow / rerunFire) is legal unless the schedule is COMPLETED or
     * ARCHIVED. {@code null} status (not yet initialized) is legal, matching
     * {@code validateManualTriggerSchedule}.
     */
    public static boolean canTriggerNow(Integer scheduleStatus) {
        return !isCompleted(scheduleStatus) && !isArchived(scheduleStatus);
    }
}