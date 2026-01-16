package io.nop.task.ext.dao;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.sys.dao.entity.NopSysDict;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.TaskConstants;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestTransactionDecorator extends JunitBaseTestCase {
    @Inject
    IDaoProvider daoProvider;

    @Inject
    ITaskFlowManager taskFlowManager;

    protected void runTask(String taskName) {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        Map<String, Object> ret = task.execute(taskRt).syncGetOutputs();
        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT));
    }

    @Test
    public void testRollback() {
        try {
            runTask("test/transaction-01");
            fail();
        } catch (NopException e) {
            e.printStackTrace();
            assertEquals("test-error", e.getErrorCode());
        }
        assertEquals(0, daoProvider.daoFor(NopSysDict.class).findAll().size());
    }
}
