package io.nop.task.impl;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.task.ITask;
import io.nop.task.ITaskManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.TaskConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class AbstractTaskTestCase extends BaseTestCase {

    protected ITaskManager taskManager;

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
        ITaskManager taskManager = new TaskManagerImpl();
        this.taskManager = taskManager;
    }

    protected Map<String, Object> runTask(String taskName) {
        ITask task = taskManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskManager.newTaskRuntime(task.getTaskName(), task.getTaskVersion(), false, null);
        Map<String, Object> ret = task.execute(taskRt).syncGet();
        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT));
        return ret;
    }
}
