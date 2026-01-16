package io.nop.task.test;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.ioc.support.BeanContainerHelper;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.TaskConstants;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestTaskFlow extends JunitBaseTestCase {
    @Inject
    ITaskFlowManager taskFlowManager;

    /**
     * 测试使用自定义的BeanContainer
     */
    @Test
    public void testCustomBeanLoader() {
        IBeanContainer container = BeanContainerHelper.buildContainer("custom", "/nop/task/beans/custom.beans.xml");
        container.start();
        try {
            ITask task = taskFlowManager.getTask("test/custom-container-01", 0);
            ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);

            // 设置beanContainer
            taskRt.getEvalScope().setBeanProvider(container);

            Map<String, Object> ret = task.execute(taskRt).syncGetOutputs();
            assertEquals("OK", ret.get(TaskConstants.VAR_RESULT));
        } finally {
            container.stop();
        }
    }
}
