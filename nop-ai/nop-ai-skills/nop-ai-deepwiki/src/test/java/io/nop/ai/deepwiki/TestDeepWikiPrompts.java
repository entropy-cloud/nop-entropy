package io.nop.ai.deepwiki;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.chat.IAiChatService;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.tool.IAiChatToolSet;
import io.nop.ai.core.command.AiCommand;
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
import java.util.List;
import java.util.Map;

@Disabled
@NopTestConfig(localDb = true)
@NopTestProperty(name = "nop.ai.tools.graphql-tool-names", value = "FileTool__readFiles,FileTool__saveFiles,FileTool__mergeFile")
public class TestDeepWikiPrompts extends JunitBaseTestCase {
    @Inject
    IAiChatService chatService;

    @Inject
    @Named("nopGraphQLToolSet")
    IAiChatToolSet toolSet;

    @Inject
    FileToolBizModel fileToolBizModel;

    @Test
    public void testGenerateReadMe() {
        AiCommand command = AiCommand.create();
        AiChatOptions options = command.makeChatOptions();
        options.setProvider("azure");
        options.setModel("gpt-5");
        command.promptName("deepwiki/generate-readme");

        command.setToolSet(toolSet);
        command.setEnabledTools(toolSet.getToolNames());


        File projectDir = new File(getModuleDir(), "../../nop-core");
        fileToolBizModel.setBaseDir(FileHelper.getAbsoluteFile(projectDir.getParentFile()));

        LocalFileOperator operator = new LocalFileOperator(projectDir);
        List<String> paths = operator.findFilesByFilter("/", FileOperatorHelper::filterNopProjectFiles,0);

        Map<String, Object> vars = new HashMap<>();
        String fileTree = StringHelper.join(paths, "\n");

        vars.put("fileTree", fileTree);
        vars.put("gitRepository", "/test/nop-core");

        AiChatExchange exchange = command.execute(vars, null);

        System.out.println(exchange.toText());
    }
}
