package io.nop.ai.translate;

import io.nop.ai.core.api.chat.IChatSessionFactory;
import io.nop.ai.core.prompt.IPromptTemplateManager;
import io.nop.ai.core.prompt.PromptTemplateManager;
import io.nop.ai.llms.config.LlmConfig;
import io.nop.ai.llms.impl.DefaultChatSessionFactory;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.client.jdk.JdkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Duration;

@Disabled
public class TestAiTranslator extends JunitBaseTestCase {

    JdkHttpClient httpClient;
    IChatSessionFactory factory;

    IPromptTemplateManager templateManager;

    @BeforeEach
    public void setUp() {
        HttpClientConfig config = new HttpClientConfig();
        config.setReadTimeout(Duration.ofMinutes(5));
        JdkHttpClient httpClient = new JdkHttpClient(config);
        this.httpClient = httpClient;
        httpClient.start();

        String baseUrl = "https://api.deepseek.com/";
        String model = "deepseek-chat";
        String chatUrl = "/chat/completions";

        baseUrl = "http://localhost:11434/";
        model = "qwen2.5-coder:7b";
        model = "qwen2.5:3b";
        model = "deepseek-r1:8b";
        chatUrl = "/api/chat";

        LlmConfig llmConfig = new LlmConfig();
        llmConfig.setMaxTokens(8000);
        llmConfig.setBaseUrl(baseUrl);
        llmConfig.setApiKey(System.getProperty("nop.ai.llm.deepseek.api-key"));
        llmConfig.setModel(model);
        llmConfig.setChatUrl(chatUrl);


        DefaultChatSessionFactory factory = new DefaultChatSessionFactory();
        factory.setHttpClient(httpClient);
        factory.setLlmConfig(llmConfig);

        this.factory = factory;

        this.templateManager = new PromptTemplateManager();
    }

    @AfterEach
    public void tearDown() {
        httpClient.stop();
    }

    @Test
    public void testTranslateDir() {
        AiTranslator translator = new AiTranslator(factory, templateManager, "translate2");
        translator.fromLang("中文").toLang("英文").concurrencyLimit(1).maxChunkSize(2048);
        translator.getChatOptions().setTemperature(0.6f);
        translator.getChatOptions().setRequestTimeout(600*1000L);

        File docsDir = new File(getModuleDir(), "../../docs");
        File docsEnDir = new File(docsDir.getParent(), "docs-en");

        translator.translateDir(docsDir, docsEnDir, null);
    }
}
