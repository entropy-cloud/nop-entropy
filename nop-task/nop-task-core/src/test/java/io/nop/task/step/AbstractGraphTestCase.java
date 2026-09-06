package io.nop.task.step;

import io.nop.core.initialize.CoreInitialization;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
import io.nop.task.impl.TaskFlowManagerImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * graph 相关测试的公共基类：CoreInitialization 生命周期 + 最小 task 执行 helper。
 */
public abstract class AbstractGraphTestCase {

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
        this.taskFlowManager = new TaskFlowManagerImpl();
    }

    protected Map<String, Object> runTask(String taskName) {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        TaskStepReturn ret = task.execute(taskRt);
        Map<String, Object> outputs = ret.syncGetOutputs();
        assertEquals("OK", outputs.get(TaskConstants.VAR_RESULT),
                "task " + taskName + " must complete through its error-edge handler");
        return outputs;
    }
}
