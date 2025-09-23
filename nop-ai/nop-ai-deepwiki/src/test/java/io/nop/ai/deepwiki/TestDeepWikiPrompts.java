package io.nop.ai.deepwiki;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.chat.IAiChatService;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.command.AiCommand;
import io.nop.ai.core.file.FileOperatorHelper;
import io.nop.ai.core.file.LocalFileOperator;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.util.StringHelper;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Disabled
public class TestDeepWikiPrompts extends JunitBaseTestCase {
    @Inject
    IAiChatService chatService;

    @Test
    public void testGenerateReadMe() {
        AiCommand command = AiCommand.create();
        AiChatOptions options = command.makeChatOptions();
        options.setProvider("azure");
        options.setModel("gpt5");
        command.promptName("deepwiki/generate-readme");
        //command.setToolSet(graphqlToolSet);

        File projectDir = new File(getModuleDir(), "../../nop-core");
        LocalFileOperator operator = new LocalFileOperator(projectDir);
        List<String> paths = operator.findFilesByFilter("/", FileOperatorHelper::filterNopProjectFiles);


        Map<String, Object> vars = new HashMap<>();
        String fileTree = StringHelper.join(paths, "\n");

        vars.put("fileTree", fileTree);

        AiChatExchange exchange = command.execute(vars, null);

        System.out.println(exchange.toText());
    }
}
