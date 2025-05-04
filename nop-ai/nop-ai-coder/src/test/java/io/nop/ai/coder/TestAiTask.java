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
        taskRt.setInput("inputRequirementsPath", attachmentFile("input-requirements.md").getAbsolutePath());
        taskRt.setInput("outputDir", getTargetDir().getAbsolutePath());
        taskRt.setInput("basePackageName", "app.demo");
        taskRt.setInput("needExpand", true);
        task.execute(taskRt).syncGetOutputs();
    }
}
