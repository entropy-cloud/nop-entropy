package io.nop.job.worker.engine;

import io.nop.api.core.beans.ErrorBean;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.core.JobCoreErrors;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2254: DefaultJobExecutionContextBuilder.buildResultUpdate 错误码映射测试——
 * RemoteJobInvoker（executorKind=rpcPoll）终态错误码写回 CANCELED/TIMEOUT，DB 模式不受影响。
 */
public class TestDefaultJobExecutionContextBuilder {

    private DefaultJobExecutionContextBuilder builder;
    private NopJobTask task;

    @BeforeEach
    void setUp() {
        builder = new DefaultJobExecutionContextBuilder();
        task = new NopJobTask();
        task.setJobTaskId("t1");
        task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_RUNNING);
    }

    @Test
    void testSuccessResult_writesSuccess() {
        JobTaskExecutionUpdate update = builder.buildResultUpdate(task, JobFireResult.CONTINUE, null);

        assertEquals(_NopJobCoreConstants.TASK_STATUS_SUCCESS, update.getTaskStatus());
        assertNull(update.getError());
        assertFalse(update.isCompleted());
    }

    @Test
    void testCompletedResult_writesCompleted() {
        JobTaskExecutionUpdate update = builder.buildResultUpdate(task, JobFireResult.COMPLETED, null);

        assertEquals(_NopJobCoreConstants.TASK_STATUS_SUCCESS, update.getTaskStatus());
        assertTrue(update.isCompleted());
    }

    @Test
    void testNullResult_noErr_writesSuccess() {
        JobTaskExecutionUpdate update = builder.buildResultUpdate(task, null, null);

        assertEquals(_NopJobCoreConstants.TASK_STATUS_SUCCESS, update.getTaskStatus());
    }

    @Test
    void testThrowable_writesFailed() {
        JobTaskExecutionUpdate update = builder.buildResultUpdate(task, null, new RuntimeException("boom"));

        assertEquals(_NopJobCoreConstants.TASK_STATUS_FAILED, update.getTaskStatus());
        assertEquals(JobCoreErrors.ERR_JOB_EXECUTION_FAILED.getErrorCode(), update.getError().getErrorCode());
    }

    @Test
    void testGenericError_writesFailed() {
        JobFireResult result = JobFireResult.ERROR(new ErrorBean("nop.err.custom").description("boom"));
        JobTaskExecutionUpdate update = builder.buildResultUpdate(task, result, null);

        assertEquals(_NopJobCoreConstants.TASK_STATUS_FAILED, update.getTaskStatus());
        assertEquals("nop.err.custom", update.getError().getErrorCode());
    }

    @Test
    void testCanceledError_writesCanceled() {
        JobFireResult result = JobFireResult.ERROR(new ErrorBean(JobCoreErrors.ERR_JOB_CANCELED.getErrorCode()));
        JobTaskExecutionUpdate update = builder.buildResultUpdate(task, result, null);

        assertEquals(_NopJobCoreConstants.TASK_STATUS_CANCELED, update.getTaskStatus());
        assertEquals(JobCoreErrors.ERR_JOB_CANCELED.getErrorCode(), update.getError().getErrorCode());
    }

    @Test
    void testTimeoutError_writesTimeout() {
        JobFireResult result = JobFireResult.ERROR(new ErrorBean(JobCoreErrors.ERR_JOB_TIMEOUT.getErrorCode()));
        JobTaskExecutionUpdate update = builder.buildResultUpdate(task, result, null);

        assertEquals(_NopJobCoreConstants.TASK_STATUS_TIMEOUT, update.getTaskStatus());
        assertEquals(JobCoreErrors.ERR_JOB_TIMEOUT.getErrorCode(), update.getError().getErrorCode());
    }
}