package io.nop.ai.coder;

import io.nop.ai.coder.orm.AiOrmConfig;
import io.nop.ai.coder.orm.AiOrmModel;
import io.nop.ai.core.prompt.IPromptTemplateManager;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.impl.FileResource;
import io.nop.orm.model.OrmModel;
import io.nop.report.core.util.ExcelReportHelper;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Set;

public class AiGenCodeTask extends JunitBaseTestCase {
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

    @Test
    public void saveOrmModel() {
        File file = new File(getModuleDir(), "../model/ai-gen.orm.xml");
        File xlsxFile = new File(getModuleDir(), "../model/nop-ai.orm.xlsx");

        XNode node = XNodeParser.instance().parseFromResource(new FileResource(file));
        AiOrmConfig config = new AiOrmConfig();
        config.setAppName("nop-ai");
        config.setMavenGroupId("io.github.entropy-cloud");
        config.setMavenArtifactId("nop-ai");
        config.setBasePackageName("nop.ai");
        config.setEntityPackageName("nop.ai.dao.entity");
        AiOrmModel ormModel = AiOrmModel.buildFromAiResult(node, config);
        ormModel.fixDictProp("ai/");
        OrmModel ormModelBean = ormModel.getOrmModelBean();
        ExcelReportHelper.saveXlsxObject("/nop/orm/imp/orm.imp.xml", new FileResource(xlsxFile), ormModelBean);

        String code = ormModel.getDictsJava("nop-ai");
        System.out.println(code);
    }
}
