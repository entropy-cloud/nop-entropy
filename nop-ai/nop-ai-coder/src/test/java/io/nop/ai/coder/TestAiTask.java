package io.nop.ai.coder;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
public class TestAiTask extends JunitBaseTestCase {
    @Inject
    ITaskFlowManager taskFlowManager;

    @Test
    public void testBizAnalyzer() {
        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-biz-analyzer.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("inputRequirements", "一个简单的请假系统");
        taskRt.setInput("outputDir", getTargetDir().getAbsolutePath());
        taskRt.setInput("basePackageName", "app.demo");
        task.execute(taskRt).syncGetOutputs();
    }
}
