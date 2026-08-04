package io.nop.job.dao.helper;

import io.nop.job.core._NopJobCoreConstants;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestJobFireStateMachine {

    @Test
    void isActive_returnsTrueForWaitingDispatchingRunning() {
        assertTrue(JobFireStateMachine.isActive(_NopJobCoreConstants.FIRE_STATUS_WAITING));
        assertTrue(JobFireStateMachine.isActive(_NopJobCoreConstants.FIRE_STATUS_DISPATCHING));
        assertTrue(JobFireStateMachine.isActive(_NopJobCoreConstants.FIRE_STATUS_RUNNING));
    }

    @Test
    void isActive_returnsFalseForTerminalStatuses() {
        assertFalse(JobFireStateMachine.isActive(_NopJobCoreConstants.FIRE_STATUS_SUCCESS));
        assertFalse(JobFireStateMachine.isActive(_NopJobCoreConstants.FIRE_STATUS_FAILED));
        assertFalse(JobFireStateMachine.isActive(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT));
        assertFalse(JobFireStateMachine.isActive(_NopJobCoreConstants.FIRE_STATUS_CANCELED));
    }

    @Test
    void isActive_returnsFalseForNull() {
        assertFalse(JobFireStateMachine.isActive(null));
    }

    @Test
    void isTerminal_returnsTrueForSuccessFailedTimeoutCanceled() {
        assertTrue(JobFireStateMachine.isTerminal(_NopJobCoreConstants.FIRE_STATUS_SUCCESS));
        assertTrue(JobFireStateMachine.isTerminal(_NopJobCoreConstants.FIRE_STATUS_FAILED));
        assertTrue(JobFireStateMachine.isTerminal(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT));
        assertTrue(JobFireStateMachine.isTerminal(_NopJobCoreConstants.FIRE_STATUS_CANCELED));
    }

    @Test
    void isTerminal_returnsFalseForActiveStatuses() {
        assertFalse(JobFireStateMachine.isTerminal(_NopJobCoreConstants.FIRE_STATUS_WAITING));
        assertFalse(JobFireStateMachine.isTerminal(_NopJobCoreConstants.FIRE_STATUS_DISPATCHING));
        assertFalse(JobFireStateMachine.isTerminal(_NopJobCoreConstants.FIRE_STATUS_RUNNING));
    }

    @Test
    void isTerminal_returnsFalseForNull() {
        assertFalse(JobFireStateMachine.isTerminal(null));
    }

    @Test
    void isRecoverable_returnsTrueForFailedTimeout() {
        assertTrue(JobFireStateMachine.isRecoverable(_NopJobCoreConstants.FIRE_STATUS_FAILED));
        assertTrue(JobFireStateMachine.isRecoverable(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT));
    }

    @Test
    void isRecoverable_returnsFalseForOthers() {
        assertFalse(JobFireStateMachine.isRecoverable(_NopJobCoreConstants.FIRE_STATUS_WAITING));
        assertFalse(JobFireStateMachine.isRecoverable(_NopJobCoreConstants.FIRE_STATUS_DISPATCHING));
        assertFalse(JobFireStateMachine.isRecoverable(_NopJobCoreConstants.FIRE_STATUS_RUNNING));
        assertFalse(JobFireStateMachine.isRecoverable(_NopJobCoreConstants.FIRE_STATUS_SUCCESS));
        assertFalse(JobFireStateMachine.isRecoverable(_NopJobCoreConstants.FIRE_STATUS_CANCELED));
        assertFalse(JobFireStateMachine.isRecoverable(null));
    }

    @Test
    void canRerun_returnsTrueForTerminalAndFalseForActive() {
        assertTrue(JobFireStateMachine.canRerun(_NopJobCoreConstants.FIRE_STATUS_SUCCESS));
        assertTrue(JobFireStateMachine.canRerun(_NopJobCoreConstants.FIRE_STATUS_FAILED));
        assertTrue(JobFireStateMachine.canRerun(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT));
        assertTrue(JobFireStateMachine.canRerun(_NopJobCoreConstants.FIRE_STATUS_CANCELED));
        assertFalse(JobFireStateMachine.canRerun(_NopJobCoreConstants.FIRE_STATUS_WAITING));
        assertFalse(JobFireStateMachine.canRerun(_NopJobCoreConstants.FIRE_STATUS_DISPATCHING));
        assertFalse(JobFireStateMachine.canRerun(_NopJobCoreConstants.FIRE_STATUS_RUNNING));
        assertFalse(JobFireStateMachine.canRerun(null));
    }

    @Test
    void canCancel_returnsTrueForWaitingAndDispatching() {
        assertTrue(JobFireStateMachine.canCancel(_NopJobCoreConstants.FIRE_STATUS_WAITING, true));
        assertTrue(JobFireStateMachine.canCancel(_NopJobCoreConstants.FIRE_STATUS_WAITING, false));
        assertTrue(JobFireStateMachine.canCancel(_NopJobCoreConstants.FIRE_STATUS_DISPATCHING, true));
        assertTrue(JobFireStateMachine.canCancel(_NopJobCoreConstants.FIRE_STATUS_DISPATCHING, false));
    }

    @Test
    void canCancel_runningFireRequiresUnfinishedTask() {
        assertTrue(JobFireStateMachine.canCancel(_NopJobCoreConstants.FIRE_STATUS_RUNNING, true));
        assertFalse(JobFireStateMachine.canCancel(_NopJobCoreConstants.FIRE_STATUS_RUNNING, false));
    }

    @Test
    void canCancel_returnsFalseForTerminalAndNull() {
        assertFalse(JobFireStateMachine.canCancel(_NopJobCoreConstants.FIRE_STATUS_SUCCESS, true));
        assertFalse(JobFireStateMachine.canCancel(_NopJobCoreConstants.FIRE_STATUS_FAILED, true));
        assertFalse(JobFireStateMachine.canCancel(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT, true));
        assertFalse(JobFireStateMachine.canCancel(_NopJobCoreConstants.FIRE_STATUS_CANCELED, true));
        assertFalse(JobFireStateMachine.canCancel(null, true));
    }

    @Test
    void activeStatuses_coverAllNonTerminalStatuses() {
        assertEquals(3, JobFireStateMachine.ACTIVE_STATUSES.size());
        assertTrue(JobFireStateMachine.ACTIVE_STATUSES.contains(_NopJobCoreConstants.FIRE_STATUS_WAITING));
        assertTrue(JobFireStateMachine.ACTIVE_STATUSES.contains(_NopJobCoreConstants.FIRE_STATUS_DISPATCHING));
        assertTrue(JobFireStateMachine.ACTIVE_STATUSES.contains(_NopJobCoreConstants.FIRE_STATUS_RUNNING));
    }

    @Test
    void recoverableStatuses_coverFailedAndTimeout() {
        assertEquals(2, JobFireStateMachine.RECOVERABLE_STATUSES.size());
        assertTrue(JobFireStateMachine.RECOVERABLE_STATUSES.contains(_NopJobCoreConstants.FIRE_STATUS_FAILED));
        assertTrue(JobFireStateMachine.RECOVERABLE_STATUSES.contains(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT));
    }

    @Test
    void resolveFinalStatus_returnsNullWhileAnyTaskPending() {
        assertNull(JobFireStateMachine.resolveFinalStatus(List.of(_NopJobCoreConstants.TASK_STATUS_RUNNING)));
        assertNull(JobFireStateMachine.resolveFinalStatus(List.of(_NopJobCoreConstants.TASK_STATUS_WAITING,
                _NopJobCoreConstants.TASK_STATUS_SUCCESS)));
        assertNull(JobFireStateMachine.resolveFinalStatus(List.of(_NopJobCoreConstants.TASK_STATUS_CLAIMED)));
        assertNull(JobFireStateMachine.resolveFinalStatus(List.of(_NopJobCoreConstants.TASK_STATUS_SUSPICIOUS,
                _NopJobCoreConstants.TASK_STATUS_RUNNING)));

        java.util.ArrayList<Integer> withNull = new java.util.ArrayList<>();
        withNull.add(null);
        assertNull(JobFireStateMachine.resolveFinalStatus(withNull));
    }

    @Test
    void resolveFinalStatus_returnsSuccessWhenAllTasksSucceed() {
        assertEquals(_NopJobCoreConstants.FIRE_STATUS_SUCCESS,
                JobFireStateMachine.resolveFinalStatus(List.of(_NopJobCoreConstants.TASK_STATUS_SUCCESS)));
        assertEquals(_NopJobCoreConstants.FIRE_STATUS_SUCCESS,
                JobFireStateMachine.resolveFinalStatus(List.of(_NopJobCoreConstants.TASK_STATUS_SUCCESS,
                        _NopJobCoreConstants.TASK_STATUS_SUCCESS)));
    }

    @Test
    void resolveFinalStatus_priorityChainTimeoutFailedCanceledSuccess() {
        Integer timeout = _NopJobCoreConstants.TASK_STATUS_TIMEOUT;
        Integer failed = _NopJobCoreConstants.TASK_STATUS_FAILED;
        Integer canceled = _NopJobCoreConstants.TASK_STATUS_CANCELED;
        Integer success = _NopJobCoreConstants.TASK_STATUS_SUCCESS;

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT,
                JobFireStateMachine.resolveFinalStatus(List.of(timeout, failed, canceled, success)));
        assertEquals(_NopJobCoreConstants.FIRE_STATUS_FAILED,
                JobFireStateMachine.resolveFinalStatus(List.of(failed, canceled, success)));
        assertEquals(_NopJobCoreConstants.FIRE_STATUS_CANCELED,
                JobFireStateMachine.resolveFinalStatus(List.of(canceled, success)));
    }

    @Test
    void resolveFinalStatus_suspiciousAloneIsTimeout() {
        assertEquals(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT,
                JobFireStateMachine.resolveFinalStatus(List.of(_NopJobCoreConstants.TASK_STATUS_SUSPICIOUS)));
        assertEquals(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT,
                JobFireStateMachine.resolveFinalStatus(List.of(_NopJobCoreConstants.TASK_STATUS_SUSPICIOUS,
                        _NopJobCoreConstants.TASK_STATUS_SUCCESS)));
    }
}
