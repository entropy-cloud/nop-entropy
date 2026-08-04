package io.nop.job.dao.helper;

import io.nop.job.core._NopJobCoreConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestJobScheduleStateMachine {

    @Test
    void statusPredicates_recognizeEachStatus() {
        Integer disabled = _NopJobCoreConstants.SCHEDULE_STATUS_DISABLED;
        Integer enabled = _NopJobCoreConstants.SCHEDULE_STATUS_ENABLED;
        Integer paused = _NopJobCoreConstants.SCHEDULE_STATUS_PAUSED;
        Integer completed = _NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED;
        Integer archived = _NopJobCoreConstants.SCHEDULE_STATUS_ARCHIVED;

        assertTrue(JobScheduleStateMachine.isDisabled(disabled));
        assertTrue(JobScheduleStateMachine.isEnabled(enabled));
        assertTrue(JobScheduleStateMachine.isPaused(paused));
        assertTrue(JobScheduleStateMachine.isCompleted(completed));
        assertTrue(JobScheduleStateMachine.isArchived(archived));

        assertFalse(JobScheduleStateMachine.isDisabled(enabled));
        assertFalse(JobScheduleStateMachine.isEnabled(disabled));
        assertFalse(JobScheduleStateMachine.isPaused(enabled));
        assertFalse(JobScheduleStateMachine.isCompleted(enabled));
        assertFalse(JobScheduleStateMachine.isArchived(enabled));

        assertFalse(JobScheduleStateMachine.isEnabled(null));
        assertFalse(JobScheduleStateMachine.isArchived(null));
    }

    @Test
    void canEnable_requiresDisabled() {
        assertTrue(JobScheduleStateMachine.canEnable(_NopJobCoreConstants.SCHEDULE_STATUS_DISABLED));
        assertFalse(JobScheduleStateMachine.canEnable(_NopJobCoreConstants.SCHEDULE_STATUS_ENABLED));
        assertFalse(JobScheduleStateMachine.canEnable(_NopJobCoreConstants.SCHEDULE_STATUS_PAUSED));
        assertFalse(JobScheduleStateMachine.canEnable(_NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED));
        assertFalse(JobScheduleStateMachine.canEnable(_NopJobCoreConstants.SCHEDULE_STATUS_ARCHIVED));
        assertFalse(JobScheduleStateMachine.canEnable(null));
    }

    @Test
    void canDisable_requiresEnabledOrPaused() {
        assertTrue(JobScheduleStateMachine.canDisable(_NopJobCoreConstants.SCHEDULE_STATUS_ENABLED));
        assertTrue(JobScheduleStateMachine.canDisable(_NopJobCoreConstants.SCHEDULE_STATUS_PAUSED));
        assertFalse(JobScheduleStateMachine.canDisable(_NopJobCoreConstants.SCHEDULE_STATUS_DISABLED));
        assertFalse(JobScheduleStateMachine.canDisable(_NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED));
        assertFalse(JobScheduleStateMachine.canDisable(_NopJobCoreConstants.SCHEDULE_STATUS_ARCHIVED));
        assertFalse(JobScheduleStateMachine.canDisable(null));
    }

    @Test
    void canPause_requiresEnabled() {
        assertTrue(JobScheduleStateMachine.canPause(_NopJobCoreConstants.SCHEDULE_STATUS_ENABLED));
        assertFalse(JobScheduleStateMachine.canPause(_NopJobCoreConstants.SCHEDULE_STATUS_DISABLED));
        assertFalse(JobScheduleStateMachine.canPause(_NopJobCoreConstants.SCHEDULE_STATUS_PAUSED));
        assertFalse(JobScheduleStateMachine.canPause(_NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED));
        assertFalse(JobScheduleStateMachine.canPause(_NopJobCoreConstants.SCHEDULE_STATUS_ARCHIVED));
        assertFalse(JobScheduleStateMachine.canPause(null));
    }

    @Test
    void canResume_requiresPaused() {
        assertTrue(JobScheduleStateMachine.canResume(_NopJobCoreConstants.SCHEDULE_STATUS_PAUSED));
        assertFalse(JobScheduleStateMachine.canResume(_NopJobCoreConstants.SCHEDULE_STATUS_DISABLED));
        assertFalse(JobScheduleStateMachine.canResume(_NopJobCoreConstants.SCHEDULE_STATUS_ENABLED));
        assertFalse(JobScheduleStateMachine.canResume(_NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED));
        assertFalse(JobScheduleStateMachine.canResume(_NopJobCoreConstants.SCHEDULE_STATUS_ARCHIVED));
        assertFalse(JobScheduleStateMachine.canResume(null));
    }

    @Test
    void canArchive_requiresAnyNonArchivedStatus() {
        assertTrue(JobScheduleStateMachine.canArchive(_NopJobCoreConstants.SCHEDULE_STATUS_DISABLED));
        assertTrue(JobScheduleStateMachine.canArchive(_NopJobCoreConstants.SCHEDULE_STATUS_ENABLED));
        assertTrue(JobScheduleStateMachine.canArchive(_NopJobCoreConstants.SCHEDULE_STATUS_PAUSED));
        assertTrue(JobScheduleStateMachine.canArchive(_NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED));
        assertFalse(JobScheduleStateMachine.canArchive(_NopJobCoreConstants.SCHEDULE_STATUS_ARCHIVED));
        assertFalse(JobScheduleStateMachine.canArchive(null));
    }

    @Test
    void canTriggerNow_rejectsCompletedAndArchivedOnly() {
        assertTrue(JobScheduleStateMachine.canTriggerNow(_NopJobCoreConstants.SCHEDULE_STATUS_DISABLED));
        assertTrue(JobScheduleStateMachine.canTriggerNow(_NopJobCoreConstants.SCHEDULE_STATUS_ENABLED));
        assertTrue(JobScheduleStateMachine.canTriggerNow(_NopJobCoreConstants.SCHEDULE_STATUS_PAUSED));
        assertFalse(JobScheduleStateMachine.canTriggerNow(_NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED));
        assertFalse(JobScheduleStateMachine.canTriggerNow(_NopJobCoreConstants.SCHEDULE_STATUS_ARCHIVED));
        assertTrue(JobScheduleStateMachine.canTriggerNow(null));
    }
}
