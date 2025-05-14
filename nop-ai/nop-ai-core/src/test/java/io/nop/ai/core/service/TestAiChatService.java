package io.nop.ai.core.service;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.chat.IAiChatService;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.autotest.junit.JunitBaseTestCase;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

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
}
