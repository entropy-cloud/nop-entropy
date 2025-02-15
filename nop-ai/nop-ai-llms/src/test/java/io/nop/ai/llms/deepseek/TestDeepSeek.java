package io.nop.ai.llms.deepseek;

import io.nop.ai.core.api.chat.ChatOptions;
import io.nop.ai.core.api.chat.IChatSession;
import io.nop.ai.core.api.chat.IChatSessionFactory;
import io.nop.ai.core.api.messages.AiResultMessage;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.ai.llms.config.LlmConfig;
import io.nop.ai.llms.impl.DefaultChatSessionFactory;
import io.nop.api.core.json.JSON;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.client.jdk.JdkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
public class TestDeepSeek extends JunitBaseTestCase {

    JdkHttpClient httpClient;
    IChatSessionFactory factory;

    @BeforeEach
    public void setUp() {
        HttpClientConfig config = new HttpClientConfig();
        JdkHttpClient httpClient = new JdkHttpClient(config);
        this.httpClient = httpClient;
        httpClient.start();

        LlmConfig llmConfig = new LlmConfig();
        llmConfig.setBaseUrl("https://api.deepseek.com/");
        llmConfig.setApiKey(System.getProperty("nop.ai.llm.deepseek.api-key"));
        llmConfig.setModel("deepseek-chat");

        llmConfig.setBaseUrl("http://localhost:11434/");
        llmConfig.setModel("deepseek-r1:8b");
        llmConfig.setApiKey(null);
        llmConfig.setChatUrl("/api/chat");

        DefaultChatSessionFactory factory = new DefaultChatSessionFactory();
        factory.setHttpClient(httpClient);
        factory.setLlmConfig(llmConfig);

        this.factory = factory;
    }

    @AfterEach
    public void tearDown() {
        httpClient.stop();
    }

    @Test
    public void testCompletion() {

        ChatOptions options = new ChatOptions();

        IChatSession session = factory.newSession(options);
        Prompt prompt = session.newPrompt(false);
        prompt.addHumanMessage("你好，你是谁？");
        AiResultMessage result = session.sendChat(prompt, null);
        System.out.println(JSON.serialize(result, true));
    }
}
