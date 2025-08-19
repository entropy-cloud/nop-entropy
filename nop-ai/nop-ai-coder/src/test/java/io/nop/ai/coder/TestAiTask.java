package io.nop.ai.coder;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Set;

@Disabled
public class TestAiTask extends JunitBaseTestCase {
    @Inject
    ITaskFlowManager taskFlowManager;

    String aiProvider = "deepseek"; //""ollama";

    String aiModel = "deepseek-reasoner"; //""qwen3:14b"; deepseek-reasoner deepseek-chat

    String sessionId = "test";

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
    public void testCreateProject() {
        File targetDir = getTargetFile("demo-project");

        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-create-project.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());
        taskRt.setInput("basePackageName", "app.demo");
        taskRt.setInput("appName", "app-demo");
        taskRt.setInput("mavenGroupId", "io.nop.demo");
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void testRefactorRequirements() {
        File targetDir = getTargetFile("demo-project");

        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-biz-analyzer.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("inputRequirementsPath", attachmentFile("input-requirements2.md").getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());
        taskRt.setInput("basePackageName", "app.demo");
        taskRt.setInput("needExpand", false);
        taskRt.setInput("aiProvider", aiProvider);
        taskRt.setInput("aiModel", aiModel);
        taskRt.setInput("sessionId", sessionId);
        taskRt.setInput("workMode", "programming");
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void testOrmDesign() {
        File targetDir = getTargetFile("demo-project");

        File docsDir = new File(targetDir, "docs");

        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-create-orm-and-menu.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("requirementsPath", new File(docsDir, "requirements/index.md").getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());
        taskRt.setInput("basePackageName", "app.demo");
        taskRt.setInput("appName", "app-demo");
        taskRt.setInput("aiProvider", aiProvider);
        taskRt.setInput("aiModel", aiModel);
        taskRt.setInput("sessionId", sessionId);
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void testApiDesign() {
        File targetDir = getTargetFile("demo-project");

        File docsDir = new File(targetDir, "docs");

        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-api-design.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("requirementsPath", new File(docsDir, "requirements/index.md").getAbsolutePath());
        taskRt.setInput("modelDir", new File(targetDir, "model").getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());
        taskRt.setInput("basePackageName", "app.demo");
        taskRt.setInput("appName", "app-demo");
        taskRt.setInput("aiProvider", aiProvider);
        taskRt.setInput("aiModel", aiModel);
        taskRt.setInput("sessionId", sessionId);
        task.execute(taskRt).syncGetOutputs();
    }


    @Test
    public void testServiceDesign() {
        File targetDir = getTargetFile("demo-project");

        File docsDir = new File(targetDir, "docs");

        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-service-design.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("requirementsPath", new File(docsDir, "requirements/index.md").getAbsolutePath());
        taskRt.setInput("modelDir", new File(targetDir, "model").getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());
        taskRt.setInput("basePackageName", "app.demo");
        taskRt.setInput("appName", "app-demo");
        taskRt.setInput("aiProvider", aiProvider);
        taskRt.setInput("aiModel", aiModel);
        taskRt.setInput("sessionId", sessionId);
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void testAiCoderOllama() {
        File targetDir = getTargetFile("demo-project-ollama");

        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-coder.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        //  taskRt.addTag("menu");
        taskRt.setInput("inputRequirementsPath", attachmentFile("input-requirements2.md").getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());
        taskRt.setInput("basePackageName", "app.demo");
        taskRt.setInput("appName", "app-demo");
        taskRt.setInput("mavenGroupId", "io.nop.demo");
        taskRt.setInput("needExpand", false);

        taskRt.setInput("aiProvider", "ollama");
        taskRt.setInput("aiModel", "qwen3:14b");
        taskRt.setInput("sessionId", "test-ollama");
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void testAiCoder() {
        File targetDir = getTargetFile("demo-project");

        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-coder.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("inputRequirementsPath", attachmentFile("input-requirements2.md").getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());
        taskRt.setInput("basePackageName", "app.demo");
        taskRt.setInput("appName", "app-demo");
        taskRt.setInput("mavenGroupId", "io.nop.demo");
        taskRt.setInput("needExpand", false);

        taskRt.setInput("aiProvider", aiProvider);
        taskRt.setInput("aiModel", aiModel);
        taskRt.setInput("sessionId", sessionId);
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void testAiCoderR1() {
        File targetDir = getTargetFile("demo-project-r1");

        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-coder.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("inputRequirementsPath", attachmentFile("input-requirements2.md").getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());
        taskRt.setInput("basePackageName", "app.demo");
        taskRt.setInput("appName", "app-demo");
        taskRt.setInput("mavenGroupId", "io.nop.demo");
        taskRt.setInput("needExpand", false);

        taskRt.setInput("aiProvider", aiProvider);
        taskRt.setInput("aiModel", "deepseek-reasoner");
        taskRt.setInput("sessionId", "test-r1");
        taskRt.setTagSet(Set.of("api", "service"));
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void testAiCoderDoubao() {
        File targetDir = getTargetFile("demo-project-doubao");

        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-coder.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("inputRequirementsPath", attachmentFile("input-requirements2.md").getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());
        taskRt.setInput("basePackageName", "app.demo");
        taskRt.setInput("appName", "app-demo");
        taskRt.setInput("mavenGroupId", "io.nop.demo");
        taskRt.setInput("needExpand", false);

        taskRt.setInput("aiProvider", "volcengine");
        taskRt.setInput("aiModel", "doubao-1-5-pro-32k-250115");
        taskRt.setInput("sessionId", "test-doubao");
        task.execute(taskRt).syncGetOutputs();
    }


    @Test
    public void testAiCoderDoubaoLite() {
        File targetDir = getTargetFile("demo-project-doubao-lite");

        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-coder.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("inputRequirementsPath", attachmentFile("input-requirements2.md").getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());
        taskRt.setInput("basePackageName", "app.demo");
        taskRt.setInput("appName", "app-demo");
        taskRt.setInput("mavenGroupId", "io.nop.demo");
        taskRt.setInput("needExpand", false);

        taskRt.setInput("aiProvider", "volcengine");
        taskRt.setInput("aiModel", "doubao-1-5-lite-32k-250115");
        taskRt.setInput("sessionId", "test-doubao-lite");
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void testAiCoderBailian() {
        File targetDir = getTargetFile("demo-project-bailian");

        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-coder.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("inputRequirementsPath", attachmentFile("input-requirements2.md").getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());
        taskRt.setInput("basePackageName", "app.demo");
        taskRt.setInput("appName", "app-demo");
        taskRt.setInput("mavenGroupId", "io.nop.demo");
        taskRt.setInput("needExpand", false);

        taskRt.setInput("aiProvider", "bailian");
        taskRt.setInput("aiModel", "qwen-plus");
        taskRt.setInput("sessionId", "test-bailian");
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void testAiCoderMock() {
        File targetDir = getTargetFile("demo-project-mock");

        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-coder.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("inputRequirementsPath", attachmentFile("input-requirements2.md").getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());
        taskRt.setInput("basePackageName", "app.demo");
        taskRt.setInput("appName", "app-demo");
        taskRt.setInput("mavenGroupId", "io.nop.demo");
        taskRt.setInput("needExpand", false);

        taskRt.setInput("aiProvider", "deepseek");
        taskRt.setInput("aiModel", "mock");
        taskRt.setInput("sessionId", "test-mock");
        taskRt.setTagSet(Set.of("service"));
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void testExtractReportTpl() {
        File targetDir = getTargetFile("demo-project-mock");

        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-extract-report-tpl.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("reportDataPath", attachmentFile("input-report-data.md").getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());
        taskRt.setInput("reportName", "test");

        taskRt.setInput("aiProvider", "deepseek");
        taskRt.setInput("aiModel", "mock");
        taskRt.setInput("sessionId", "test-mock");
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void testDataFalsification() {
        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-data-falsification.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("inputDir", attachmentFile("../coder").getAbsolutePath());
        taskRt.setInput("outputDir", getTargetFile("data-falsified").getAbsolutePath());

        taskRt.setInput("aiProvider", "ollama");
        taskRt.setInput("aiModel", "qwen3");
        taskRt.setInput("sessionId", "test-mock");
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void testConvertDocx() {
        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-convert-docx.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("docxPath", "c:/test/test2.docx");
        taskRt.setInput("outputDir", getTargetFile("convert").getAbsolutePath());

        taskRt.setInput("aiProvider", "azure");
        taskRt.setInput("aiModel", "gpt-4o");
        taskRt.setInput("sessionId", "test");
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void testAnalyzeImage() {
        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-analyze-images.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("inputDir", "c:/test/test2/media");
        taskRt.setInput("outputDir", getTargetFile("convert2").getAbsolutePath());

        taskRt.setInput("aiProvider", "azure");
        taskRt.setInput("aiModel", "gpt-4o");
        taskRt.setInput("sessionId", "test");
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void testCodeSummary() {
        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-code-summary.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        File sourceDir = new File(getModuleDir(), "../../");
        File targetDir = new File("c:/test/data");

        taskRt.setInput("inputDir", sourceDir.getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());

        taskRt.setInput("aiProvider", "lm-studio");
        taskRt.setInput("aiModel", "qwen/qwen3-8b");

        //taskRt.setInput("aiProvider", "ollama");
        //taskRt.setInput("aiModel", "qwen3:4b");

        taskRt.setInput("sessionId", "test");
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void testFileSummary() {
        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-file-summary.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        File sourceDir = new File(getModuleDir(), "../../");
        File targetDir = new File("c:/test/summary");

        taskRt.setInput("inputDir", sourceDir.getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());

        taskRt.setInput("aiProvider", "lm-studio");
        taskRt.setInput("aiModel", "qwen/qwen3-8b");

        //taskRt.setInput("aiProvider", "ollama");
        //taskRt.setInput("aiModel", "qwen3:4b");

        taskRt.setInput("sessionId", "test");
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void testFileSimpleSummary() {
        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-file-simple-summary.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        //File sourceDir = new File(getModuleDir(), "../../");
        File sourceDir = new File("c:/test/summary");
        File targetDir = new File("c:/test/simple-summary");

        taskRt.setInput("inputDir", sourceDir.getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());

        taskRt.setInput("aiProvider", "lm-studio");
        taskRt.setInput("aiModel", "qwen/qwen3-8b");

        //taskRt.setInput("aiProvider", "ollama");
        //taskRt.setInput("aiModel", "qwen3:4b");

        taskRt.setInput("sessionId", "test");
        task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void testDirSummary() {
        ITask task = taskFlowManager.loadTaskFromPath("/nop/ai/tasks/ai-file-dir-summary.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        //File sourceDir = new File(getModuleDir(), "../../");
        File sourceDir = new File("c:/test/summary");
        File targetDir = new File("c:/test/dir-summary");

        taskRt.setInput("inputDir", sourceDir.getAbsolutePath());
        taskRt.setInput("outputDir", targetDir.getAbsolutePath());

        taskRt.setInput("aiProvider", "lm-studio");
        taskRt.setInput("aiModel", "qwen/qwen3-8b");

        //taskRt.setInput("aiProvider", "ollama");
        //taskRt.setInput("aiModel", "qwen3:4b");

        taskRt.setInput("sessionId", "test");
        task.execute(taskRt).syncGetOutputs();
    }
}