package io.nop.ai.translate;

import io.nop.ai.core.api.chat.IAiChatService;
import io.nop.ai.core.prompt.IPromptTemplateManager;
import io.nop.ai.core.prompt.PromptTemplateManager;
import io.nop.ai.core.service.DefaultAiChatService;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.client.jdk.JdkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

@Disabled
public class TestAiTranslator extends JunitBaseTestCase {

    JdkHttpClient httpClient;
    IAiChatService chatService;

    IPromptTemplateManager templateManager;

    @BeforeEach
    public void setUp() {
        HttpClientConfig config = new HttpClientConfig();
        config.setReadTimeout(Duration.ofMinutes(5));
        JdkHttpClient httpClient = new JdkHttpClient(config);
        this.httpClient = httpClient;
        httpClient.start();

        setTestConfig("nop.ai.llm.ollama.base-url", "http://localhost:11434/");

        DefaultAiChatService chatService = new DefaultAiChatService();
        chatService.setHttpClient(httpClient);

        this.chatService = chatService;

        this.templateManager = new PromptTemplateManager();
    }

    @AfterEach
    public void tearDown() {
        httpClient.stop();
    }

    File getDocsDir() {
        return new File(getModuleDir(), "../../docs");
    }

    @Test
    public void testTranslateDir() {
        String model = "deepseek-r1:14b";

        AiTranslator translator = new AiTranslator(chatService, templateManager, "translate2");
        translator.fromLang("中文").toLang("英文").concurrencyLimit(1).maxChunkSize(3200);
        translator.getChatOptions().setLlm("ollama");
        translator.getChatOptions().setModel(model);
        translator.getChatOptions().setTemperature(0.6f);
        translator.getChatOptions().setRequestTimeout(600 * 1000L);
        translator.getChatOptions().setContextLength(4096);

        File docsDir = getDocsDir();
        File docsEnDir = new File(docsDir.getParent(), "docs-en");

        translator.translateDir(docsDir, docsEnDir, null);
    }

    void translateFile(String model, Consumer<AiTranslator> config) {
        AiTranslator translator = new AiTranslator(chatService, templateManager, "translate2");
        translator.fromLang("中文").toLang("英文").concurrencyLimit(1).maxChunkSize(3200);
        translator.getChatOptions().setLlm("ollama");
        translator.getChatOptions().setModel(model);
        translator.getChatOptions().setTemperature(0.6f);
        translator.getChatOptions().setRequestTimeout(600 * 1000L);
        translator.getChatOptions().setContextLength(8192);
        translator.setSavePrompt(true);

        config.accept(translator);

        File docsDir = getDocsDir();

        File srcFile = new File(docsDir, "compare/nop-vs-apijson.md");
        String normalizedName = model.replace(':', '-') + '-' + translator.getChatOptions().getContextLength();
        File targetFile = getTargetFile("translated/" + normalizedName + ".md");
        targetFile.delete();
        translator.translateFile(srcFile, targetFile, null, new Semaphore(1));
    }

    @Test
    public void testQwen7B() {
        translateFile("qwen2.5-coder:7b", translator -> {
        });
    }

    @Test
    public void testDeepSeek8B() {
        translateFile("deepseek-r1:8b", translator -> {
        });
    }

    @Test
    public void testDeepSeek8B32K() {
        translateFile("deepseek-r1:8b", translator -> translator.getChatOptions().setContextLength(32768));
    }

    @Test
    public void testDeepSeek14B() {
        translateFile("deepseek-r1:14b", translator -> translator.getChatOptions().setContextLength(4096));
    }
}
