package io.nop.job.dao.helper;

import io.nop.job.core._NopJobCoreConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestJobStatusHelper {

    @Test
    void isActiveFire_returnsTrueForWaitingDispatchingRunning() {
        assertTrue(JobStatusHelper.isActiveFire(_NopJobCoreConstants.FIRE_STATUS_WAITING));
        assertTrue(JobStatusHelper.isActiveFire(_NopJobCoreConstants.FIRE_STATUS_DISPATCHING));
        assertTrue(JobStatusHelper.isActiveFire(_NopJobCoreConstants.FIRE_STATUS_RUNNING));
    }

    @Test
    void isActiveFire_returnsFalseForTerminalStatuses() {
        assertFalse(JobStatusHelper.isActiveFire(_NopJobCoreConstants.FIRE_STATUS_SUCCESS));
        assertFalse(JobStatusHelper.isActiveFire(_NopJobCoreConstants.FIRE_STATUS_FAILED));
        assertFalse(JobStatusHelper.isActiveFire(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT));
        assertFalse(JobStatusHelper.isActiveFire(_NopJobCoreConstants.FIRE_STATUS_CANCELED));
    }

    @Test
    void isActiveFire_returnsFalseForNull() {
        assertFalse(JobStatusHelper.isActiveFire(null));
    }

    @Test
    void isTerminalFire_returnsTrueForSuccessFailedTimeoutCanceled() {
        assertTrue(JobStatusHelper.isTerminalFire(_NopJobCoreConstants.FIRE_STATUS_SUCCESS));
        assertTrue(JobStatusHelper.isTerminalFire(_NopJobCoreConstants.FIRE_STATUS_FAILED));
        assertTrue(JobStatusHelper.isTerminalFire(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT));
        assertTrue(JobStatusHelper.isTerminalFire(_NopJobCoreConstants.FIRE_STATUS_CANCELED));
    }

    @Test
    void isTerminalFire_returnsFalseForActiveStatuses() {
        assertFalse(JobStatusHelper.isTerminalFire(_NopJobCoreConstants.FIRE_STATUS_WAITING));
        assertFalse(JobStatusHelper.isTerminalFire(_NopJobCoreConstants.FIRE_STATUS_DISPATCHING));
        assertFalse(JobStatusHelper.isTerminalFire(_NopJobCoreConstants.FIRE_STATUS_RUNNING));
    }

    @Test
    void isTerminalFire_returnsFalseForNull() {
        assertFalse(JobStatusHelper.isTerminalFire(null));
    }

    @Test
    void isFinishedTask_returnsFalseForWaitingClaimedRunning() {
        assertFalse(JobStatusHelper.isFinishedTask(_NopJobCoreConstants.TASK_STATUS_WAITING));
        assertFalse(JobStatusHelper.isFinishedTask(_NopJobCoreConstants.TASK_STATUS_CLAIMED));
        assertFalse(JobStatusHelper.isFinishedTask(_NopJobCoreConstants.TASK_STATUS_RUNNING));
    }

    @Test
    void isFinishedTask_returnsTrueForTerminalStatuses() {
        assertTrue(JobStatusHelper.isFinishedTask(_NopJobCoreConstants.TASK_STATUS_SUCCESS));
        assertTrue(JobStatusHelper.isFinishedTask(_NopJobCoreConstants.TASK_STATUS_FAILED));
        assertTrue(JobStatusHelper.isFinishedTask(_NopJobCoreConstants.TASK_STATUS_TIMEOUT));
        assertTrue(JobStatusHelper.isFinishedTask(_NopJobCoreConstants.TASK_STATUS_CANCELED));
    }

    @Test
    void isFinishedTask_returnsTrueForSuspicious() {
        assertTrue(JobStatusHelper.isFinishedTask(_NopJobCoreConstants.TASK_STATUS_SUSPICIOUS));
    }

    @Test
    void isFinishedTask_returnsFalseForNull() {
        assertFalse(JobStatusHelper.isFinishedTask(null));
    }
}
