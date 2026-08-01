package io.nop.ai.coder;

import io.nop.ai.core.prompt.IPromptTemplateManager;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Set;

/**
 * Manual codegen runner for the AI coder task flow.
 * <p>
 * NOT an automated test: it drives the full ai-coder task flow which requires real LLM API keys
 * (deepseek) and long-running generation. Intentionally not picked up by surefire.
 */
@Disabled
public class AiGenCodeTaskManual extends JunitBaseTestCase {
    @Inject
    IPromptTemplateManager promptTemplateManager;

    @Inject
    ITaskFlowManager taskFlowManager;

    @Test
    public void runCodeGen() {
        File targetDir = getTargetFile("ai-project-r1");

        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-coder.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("inputRequirementsPath", new File(getModuleDir(), "../model/input-requirements.md").getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());
        taskRt.setInput("inputDir", targetDir.getAbsolutePath());
        taskRt.setInput("basePackageName", "nop.ai");
        taskRt.setInput("appName", "nop-ai");
        taskRt.setInput("mavenGroupId", "io.nop.ai");
        taskRt.setInput("needExpand", false);

        taskRt.setInput("aiProvider", "deepseek");
        taskRt.setInput("aiModel", "deepseek-reasoner");
        taskRt.setInput("sessionId", "ai-r1");
        taskRt.setTagSet(Set.of("req", "orm"));
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void runCodeGenMock() {
        File targetDir = getTargetFile("ai-project-r1");

        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-coder.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("inputRequirementsPath", new File(getModuleDir(), "../model/input-requirements.md").getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());
        taskRt.setInput("inputDir", targetDir.getAbsolutePath());
        taskRt.setInput("basePackageName", "nop.ai");
        taskRt.setInput("appName", "nop-ai");
        taskRt.setInput("mavenGroupId", "io.nop.ai");
        taskRt.setInput("needExpand", true);

        taskRt.setInput("aiProvider", "deepseek");
        taskRt.setInput("aiModel", "mock");
        taskRt.setInput("sessionId", "ai-r1-mock");
        taskRt.setTagSet(Set.of("req", "orm"));
        task.execute(taskRt).syncGetOutputs();
    }
}
