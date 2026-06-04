package io.nop.job.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.dao.entity.NopJobTask;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static io.nop.job.api.JobApiErrors.ERR_JOB_TASK_DELETE_NOT_ALLOWED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopJobTaskBizModel extends JunitBaseTestCase {
    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testDelete_throwsNotAllowed() {
        NopJobTask task = new NopJobTask();
        task.setJobTaskId("task-delete-test");
        task.setJobFireId("fire-nonexistent");
        task.setTaskNo(1);
        task.setTaskStatus(0);
        task.setPartitionIndex((short) 1);
        task.setVersion(0L);
        task.setCreatedBy("test");
        task.setCreateTime(new Timestamp(System.currentTimeMillis()));
        task.setUpdatedBy("test");
        task.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task);

        NopJobTaskBizModel bizModel = BeanContainer.getBeanByType(NopJobTaskBizModel.class);

        NopException ex = assertThrows(NopException.class,
                () -> bizModel.delete("task-delete-test", null));
        assertEquals(ERR_JOB_TASK_DELETE_NOT_ALLOWED.getErrorCode(), ex.getErrorCode());
    }
}
