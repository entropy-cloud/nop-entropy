package io.nop.ai.translate;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;

@Disabled
public class TestAiTranslate extends JunitBaseTestCase {
    @Inject
    ITaskFlowManager taskFlowManager;

    @Test
    public void testTranslate() {
        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-translate-dir.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        File sourceDir = new File(getModuleDir(), "../../docs/theory");
        File targetDir = new File("c:/test/result");

        taskRt.setInput("inputDir", sourceDir.getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());
        taskRt.setInput("chunkSize", 4096);
        taskRt.setInput("fromLang", "中文");
        taskRt.setInput("toLang", "英文");

        taskRt.setInput("aiProvider", "lm-studio");
        taskRt.setInput("aiModel", "qwen/qwen3-8b");

        //taskRt.setInput("aiProvider", "ollama");
        //taskRt.setInput("aiModel", "qwen3:4b");

        //taskRt.setInput("aiProvider", "azure");
        //taskRt.setInput("aiModel", "gpt-4");

        taskRt.setInput("sessionId", "test");
        task.execute(taskRt).syncGetOutputs();
    }
}