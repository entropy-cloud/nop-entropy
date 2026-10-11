package io.nop.ai.deepwiki;

import io.nop.ai.api.chat.IChatService;
import io.nop.ai.core.command.AiCommand;
import io.nop.ai.core.command.IAiToolInvoker;
import io.nop.ai.core.file.FileOperatorHelper;
import io.nop.ai.core.file.LocalFileOperator;
import io.nop.ai.tools.file.FileToolBizModel;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Disabled
@NopTestConfig(localDb = true)
@NopTestProperty(name = "nop.ai.tools.graphql-tool-names", value = "FileTool__readFiles,FileTool__saveFiles,FileTool__mergeFile")
public class TestDeepWikiPrompts extends JunitBaseTestCase {
    @Inject
    IChatService chatService;

    @Inject
    @Named("nopGraphQLToolSet")
    IAiToolInvoker toolInvoker;

    @Inject
    FileToolBizModel fileToolBizModel;

    @Test
    public void testGenerateReadMe() {
        AiCommand command = AiCommand.create();
        command.provider("azure");
        command.model("gpt-5");
        command.promptName("deepwiki/generate-readme");

        command.toolInvoker(toolInvoker);
        Set<String> enabledTools = new LinkedHashSet<>();
        enabledTools.add("FileTool__readFiles");
        enabledTools.add("FileTool__saveFiles");
        enabledTools.add("FileTool__mergeFile");
        command.setEnabledTools(enabledTools);

        File projectDir = new File(getModuleDir(), "../../nop-core");
        fileToolBizModel.setBaseDir(FileHelper.getAbsoluteFile(projectDir.getParentFile()));

        LocalFileOperator operator = new LocalFileOperator(projectDir);
        List<String> paths = operator.findFilesByFilter("/", FileOperatorHelper::filterNopProjectFiles, 0);

        Map<String, Object> vars = new HashMap<>();
        String fileTree = StringHelper.join(paths, "\n");

        vars.put("fileTree", fileTree);
        vars.put("gitRepository", "/test/nop-core");

        var exchange = command.execute(vars, null);

        System.out.println(exchange.getResultText());
    }
}
