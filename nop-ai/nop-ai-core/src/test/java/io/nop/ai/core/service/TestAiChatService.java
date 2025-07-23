package io.nop.ai.core.service;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.chat.IAiChatService;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.messages.AiUserMessage;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.ai.core.command.AiCommand;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.resource.IResource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static io.nop.ai.core.api.tool.DefaultAiChatToolSet.staticMethodsToolSet;

@Disabled
public class TestAiChatService extends JunitBaseTestCase {
    @Inject
    IAiChatService chatService;

    @Test
    public void testDeepSeek() {
        AiChatOptions options = new AiChatOptions();
        options.setProvider("deepseek");
        options.setModel("deepseek-chat");

        Prompt prompt = Prompt.userText("hello");
        AiChatExchange exchange = chatService.sendChat(prompt, options, null);
        System.out.println(exchange.toText());
    }

    @Test
    public void testGpt4o() {
        AiChatOptions options = new AiChatOptions();
        options.setProvider("azure");
        options.setModel("gpt-4o");

        Prompt prompt = Prompt.userText("请分析所附截图，并按照以下步骤操作：\n" +
                "\n" +
                "提取并转录图片中所有可见的文本内容。\n" +
                "总结图片中表达的主要业务需求或业务规则，要求信息清晰、简明地组织。\n" +
                "尽量使用markdown表格格式来表达图片中的表格或结构化布局。\n【返回格式】\n <IMAGE_SUMMARIZATION>response</IMAGE_SUMMARIZATION>");
        AiUserMessage userMessage = (AiUserMessage) prompt.getLastMessage();
        IResource resource = getResource("/test/test.png");
        userMessage.addImage(resource);

        AiChatExchange exchange = chatService.sendChat(prompt, options, null);
        System.out.println(exchange.toText());
    }

    @Test
    public void testChatTool() {
        AiCommand command = AiCommand.create();
        command.makeChatOptions().setProvider("azure");
        command.makeChatOptions().setModel("gpt-4o");
        command.setEnabledTools(Set.of("loadNopFile"));
        command.setToolSet(staticMethodsToolSet(MyToolSet.class));
        command.prompt("读取a.orm.xlsx，作为orm.xml格式读取，它是一个Nop文件");
        Map<String, Object> vars = new HashMap<>();
        String result = (String) command.execute(vars, null).getOutput("RESULT");
        System.out.println("RESULT=" + result);
    }

    public static class MyToolSet {
        @Description("Nop文件是一系列特殊格式的DSL文件，N可以用xlsx,xml,json,yaml等格式保存，并且可以在多种格式之间自由转换。所有的Nop文件都通过这个函数加载, 可以指定toFileType来转换为指定类型。")
        public static String loadNopFile(@Name("fileName") String fileName,
                                         @Name("toFileType") String toFileType) {
            return "<root>ABC</root>";
        }
    }
}
