package io.nop.job.dao.helper;

import io.nop.job.core._NopJobCoreConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestJobTaskStateMachine {

    @Test
    void isPending_returnsTrueForWaitingClaimedRunning() {
        assertTrue(JobTaskStateMachine.isPending(_NopJobCoreConstants.TASK_STATUS_WAITING));
        assertTrue(JobTaskStateMachine.isPending(_NopJobCoreConstants.TASK_STATUS_CLAIMED));
        assertTrue(JobTaskStateMachine.isPending(_NopJobCoreConstants.TASK_STATUS_RUNNING));
    }

    @Test
    void isPending_returnsFalseForSuspiciousAndTerminal() {
        assertFalse(JobTaskStateMachine.isPending(_NopJobCoreConstants.TASK_STATUS_SUSPICIOUS));
        assertFalse(JobTaskStateMachine.isPending(_NopJobCoreConstants.TASK_STATUS_SUCCESS));
        assertFalse(JobTaskStateMachine.isPending(_NopJobCoreConstants.TASK_STATUS_FAILED));
        assertFalse(JobTaskStateMachine.isPending(_NopJobCoreConstants.TASK_STATUS_TIMEOUT));
        assertFalse(JobTaskStateMachine.isPending(_NopJobCoreConstants.TASK_STATUS_CANCELED));
    }

    @Test
    void isPending_returnsFalseForNull() {
        assertFalse(JobTaskStateMachine.isPending(null));
    }

    @Test
    void isFinished_returnsFalseForWaitingClaimedRunning() {
        assertFalse(JobTaskStateMachine.isFinished(_NopJobCoreConstants.TASK_STATUS_WAITING));
        assertFalse(JobTaskStateMachine.isFinished(_NopJobCoreConstants.TASK_STATUS_CLAIMED));
        assertFalse(JobTaskStateMachine.isFinished(_NopJobCoreConstants.TASK_STATUS_RUNNING));
    }

    @Test
    void isFinished_returnsTrueForTerminalStatuses() {
        assertTrue(JobTaskStateMachine.isFinished(_NopJobCoreConstants.TASK_STATUS_SUCCESS));
        assertTrue(JobTaskStateMachine.isFinished(_NopJobCoreConstants.TASK_STATUS_FAILED));
        assertTrue(JobTaskStateMachine.isFinished(_NopJobCoreConstants.TASK_STATUS_TIMEOUT));
        assertTrue(JobTaskStateMachine.isFinished(_NopJobCoreConstants.TASK_STATUS_CANCELED));
    }

    @Test
    void isFinished_returnsTrueForSuspicious() {
        assertTrue(JobTaskStateMachine.isFinished(_NopJobCoreConstants.TASK_STATUS_SUSPICIOUS));
    }

    @Test
    void isFinished_returnsFalseForNull() {
        assertFalse(JobTaskStateMachine.isFinished(null));
    }

    @Test
    void isFinished_isStrictComplementOfIsPendingForNonNull() {
        int[] allStatuses = {
                _NopJobCoreConstants.TASK_STATUS_WAITING,
                _NopJobCoreConstants.TASK_STATUS_CLAIMED,
                _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS,
                _NopJobCoreConstants.TASK_STATUS_RUNNING,
                _NopJobCoreConstants.TASK_STATUS_SUCCESS,
                _NopJobCoreConstants.TASK_STATUS_FAILED,
                _NopJobCoreConstants.TASK_STATUS_TIMEOUT,
                _NopJobCoreConstants.TASK_STATUS_CANCELED
        };
        for (int status : allStatuses) {
            assertTrue(JobTaskStateMachine.isFinished(status) != JobTaskStateMachine.isPending(status),
                    "isFinished and isPending must be complementary for status " + status);
        }
    }

    @Test
    void isRecoverable_returnsTrueForCanceledFailedTimeoutSuspicious() {
        assertTrue(JobTaskStateMachine.isRecoverable(_NopJobCoreConstants.TASK_STATUS_CANCELED));
        assertTrue(JobTaskStateMachine.isRecoverable(_NopJobCoreConstants.TASK_STATUS_FAILED));
        assertTrue(JobTaskStateMachine.isRecoverable(_NopJobCoreConstants.TASK_STATUS_TIMEOUT));
        assertTrue(JobTaskStateMachine.isRecoverable(_NopJobCoreConstants.TASK_STATUS_SUSPICIOUS));
    }

    @Test
    void isRecoverable_returnsFalseForWaitingClaimedRunningSuccess() {
        assertFalse(JobTaskStateMachine.isRecoverable(_NopJobCoreConstants.TASK_STATUS_WAITING));
        assertFalse(JobTaskStateMachine.isRecoverable(_NopJobCoreConstants.TASK_STATUS_CLAIMED));
        assertFalse(JobTaskStateMachine.isRecoverable(_NopJobCoreConstants.TASK_STATUS_RUNNING));
        assertFalse(JobTaskStateMachine.isRecoverable(_NopJobCoreConstants.TASK_STATUS_SUCCESS));
    }

    @Test
    void isRecoverable_returnsFalseForNull() {
        assertFalse(JobTaskStateMachine.isRecoverable(null));
    }

    @Test
    void isConcurrentlyFinalized_returnsTrueForTimeoutCanceledSuspicious() {
        assertTrue(JobTaskStateMachine.isConcurrentlyFinalized(_NopJobCoreConstants.TASK_STATUS_TIMEOUT));
        assertTrue(JobTaskStateMachine.isConcurrentlyFinalized(_NopJobCoreConstants.TASK_STATUS_CANCELED));
        assertTrue(JobTaskStateMachine.isConcurrentlyFinalized(_NopJobCoreConstants.TASK_STATUS_SUSPICIOUS));
    }

    @Test
    void isConcurrentlyFinalized_returnsFalseForWaitingClaimedRunningSuccessFailed() {
        assertFalse(JobTaskStateMachine.isConcurrentlyFinalized(_NopJobCoreConstants.TASK_STATUS_WAITING));
        assertFalse(JobTaskStateMachine.isConcurrentlyFinalized(_NopJobCoreConstants.TASK_STATUS_CLAIMED));
        assertFalse(JobTaskStateMachine.isConcurrentlyFinalized(_NopJobCoreConstants.TASK_STATUS_RUNNING));
        assertFalse(JobTaskStateMachine.isConcurrentlyFinalized(_NopJobCoreConstants.TASK_STATUS_SUCCESS));
        assertFalse(JobTaskStateMachine.isConcurrentlyFinalized(_NopJobCoreConstants.TASK_STATUS_FAILED));
    }

    @Test
    void isConcurrentlyFinalized_returnsFalseForNull() {
        assertFalse(JobTaskStateMachine.isConcurrentlyFinalized(null));
    }
}
