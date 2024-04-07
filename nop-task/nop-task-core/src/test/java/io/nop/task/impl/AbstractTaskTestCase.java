package io.nop.task.impl;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.TaskConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class AbstractTaskTestCase extends BaseTestCase {

    protected ITaskFlowManager taskFlowManager;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        ITaskFlowManager taskManager = new TaskFlowManagerImpl();
        this.taskFlowManager = taskManager;
    }

    protected Map<String, Object> runTask(String taskName) {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        Map<String, Object> ret = task.execute(taskRt).syncGet();
        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT));
        return ret;
    }
}
