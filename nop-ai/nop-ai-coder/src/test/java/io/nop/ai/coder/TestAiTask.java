package io.nop.ai.coder;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.util.FileHelper;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;

@Disabled
public class TestAiTask extends JunitBaseTestCase {
    @Inject
    ITaskFlowManager taskFlowManager;

//    @Test
//    public void testExpandRequirements() {
//        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-biz-analyzer.task.xml");
//        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
//        taskRt.setInput("inputRequirementsPath", attachmentFile("input-requirements.md").getAbsolutePath());
//        taskRt.setInput("outputDir", getTargetDir().getAbsolutePath());
//        taskRt.setInput("basePackageName", "app.demo");
//        taskRt.setInput("needExpand", true);
//        task.execute(taskRt).syncGetOutputs();
//    }

    @Test
    public void testRefactorRequirements() {
        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-biz-analyzer.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("inputRequirementsPath", attachmentFile("input-requirements2.md").getAbsolutePath());
        taskRt.setInput("outputDir", getTargetDir().getAbsolutePath());
        taskRt.setInput("basePackageName", "app.demo");
        taskRt.setInput("needExpand", false);
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void testAiCoder() {
        File targetDir = getTargetFile("demo");

        File docsDir = getTargetFile("docs");

        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-create-orm-and-menu.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("requirementsPath", new File(docsDir, "requirements/refactored-requirements.md").getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());
        taskRt.setInput("basePackageName", "app.demo");
        taskRt.setInput("appName", "app-demo");
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void testApiDesign() {
        File targetDir = getTargetFile("demo");

        File docsDir = getTargetFile("docs");
        File projectDir = getTargetFile("demo");

        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-api-design.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("requirementsPath", new File(docsDir, "requirements/refactored-requirements.md").getAbsolutePath());
        taskRt.setInput("modelDir", new File(projectDir, "model").getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());
        taskRt.setInput("basePackageName", "app.demo");
        taskRt.setInput("appName", "app-demo");
        task.execute(taskRt).syncGetOutputs();
    }
}