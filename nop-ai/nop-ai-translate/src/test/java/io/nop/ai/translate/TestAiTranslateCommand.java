package io.nop.ai.translate;

import io.nop.ai.core.api.chat.IAiChatService;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.commons.debug.DebugMessageHelper;
import io.nop.ai.core.prompt.IPromptTemplateManager;
import io.nop.ai.core.prompt.PromptTemplateManager;
import io.nop.ai.core.service.DefaultAiChatService;
import io.nop.ai.translate.fix.FixMarkdownTranslation;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.client.jdk.JdkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

@Disabled
public class TestAiTranslateCommand extends JunitBaseTestCase {

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
        String model = "deepseek-r1:8b";
        //  model = "deepseek-r1:14b";
        model = "llama3.1:8b";
        //model = "phi4";
        //model = "llama3.2:latest";
        String promptName = "translate";

        AiTranslateCommand translator = new AiTranslateCommand(chatService, templateManager, promptName);
        translator.fromLang("中文").toLang("英文").concurrencyLimit(1).maxChunkSize(2048);
        translator.makeChatOptions().setProvider("ollama");
        translator.makeChatOptions().setModel(model);
        translator.makeChatOptions().setTemperature(0.6f);
        translator.makeChatOptions().setRequestTimeout(600 * 1000L);
        translator.makeChatOptions().setContextLength(8192);
        //translator.getChatOptions().setMaxTokens(4096);
        translator.setDebug(true);
        translator.recoverMode(true);

        File docsDir = getDocsDir();
        File docsEnDir = new File(docsDir.getParent(), "docs-en");

        translator.translateDir(docsDir, docsEnDir, null);
    }

    @Test
    public void testTranslateResult() {
        File file = getTargetFile("test-classes/data/translate-result0.md");
        String text = FileHelper.readText(file, null);
        AiChatExchange response = new AiChatExchange();
        response.setContent(text);
        response.getBlock("<TRANSLATE_RESULT>\n", "\n</TRANSLATE_RESULT>", true, false);
    }

    @Test
    public void checkTranslation() {
        File docsDir = getDocsDir();
        File docsEnDir = new File(docsDir.getParentFile(), "docs-en");
        File docsEnDebugDir = new File(docsDir.getParentFile(), "docs-en-debug");

        String model = "llama3.1:8b";
        // model = "deepseek-r1:8b";
        //model = "llama3.2:latest";

        AiCheckTranslationCommand check = new AiCheckTranslationCommand(chatService, templateManager, "translate/score");
        check.fromLang("中文").toLang("英文");
        check.makeChatOptions().setProvider("ollama");
        check.makeChatOptions().setModel(model);
        check.makeChatOptions().setTemperature(0.6f);
        check.makeChatOptions().setRequestTimeout(600 * 1000L);
        check.makeChatOptions().setContextLength(8192);

        FileHelper.walk2(docsEnDir, docsEnDebugDir, (f1, f2) -> {
            if (f2.getName().endsWith(".md") && f2.exists()) {
                List<AiChatExchange> messages = DebugMessageHelper.parseDebugFile(f2);
                boolean changed = false;
                for (AiChatExchange message : messages) {
                    message.checkNotEmpty();

                    if (message.isInvalid())
                        continue;

                    Object scoreValue = message.getOutput("score");
                    if (scoreValue != null)
                        continue;

                    String source = getSourceText(message);
                    if (source == null)
                        continue;

                    changed = true;
                    try {

                        AiChatExchange scoreMessage = FutureHelper.syncGet(check.executeAsync(source, message.getContent(), null));
                        Number score = (Number) scoreMessage.getOutput("score");
                        if (score != null)
                            message.setOutput("score", score);
                        if (score != null && score.intValue() < 7) {
                            message.setInvalid(true);
                            message.setInvalidReason(new ErrorBean("score.too-low").param("score", score));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (changed)
                    DebugMessageHelper.writeDebugFile(f2, messages);
            }
            return FileVisitResult.CONTINUE;
        });
    }

    String getSourceText(AiChatExchange message) {
        String text = message.getBlockFromPrompt("待翻译的内容如下：\n", "\n[EndOfData]");
        if (text == null) {
            text = message.getBlockFromPrompt("<TRANSLATE_SOURCE>\n", "\n</TRANSLATE_SOURCE>", 1);
            if (text == null)
                text = message.getBlockFromPrompt("<TRANSLATE_SOURCE>\n", "\n</TRANSLATE_SOURCE>", 0);
        }
        return text;
    }

    @Test
    public void syncFromDebugFile() {
        File docsDir = getDocsDir();
        File docsEnDir = new File(docsDir.getParentFile(), "docs-en");
        File docsEnDebugDir = new File(docsDir.getParentFile(), "docs-en-debug-fix");

        AiTranslateCommand.syncFromDebugFile(docsEnDir, docsEnDebugDir);
    }

    @Test
    public void moveDebugFile() {
        File docsDir = getDocsDir();
        File docsEnDir = new File(docsDir.getParentFile(), "docs-en");
        File docsEnDebugDir = new File(docsDir.getParentFile(), "docs-en-debug");

        FileHelper.walk2(docsEnDir, docsEnDebugDir, (f1, f2) -> {
            if (f1.getName().endsWith(".debug.md")) {
                File targetFile = new File(f2.getParentFile(), StringHelper.removeEnd(f1.getName(), ".debug.md"));
                if (!targetFile.exists()) {
                    System.out.println("move file:" + targetFile);
                    FileHelper.copyFile(f1, targetFile);
                }
                f1.delete();
            }
            return FileVisitResult.CONTINUE;
        });
    }

    @Test
    public void removeExtraExt() {
        File docsDir = getDocsDir();
        File docsEnDir = new File(docsDir.getParentFile(), "docs-en");
        File docsEnDebugDir = new File(docsDir.getParentFile(), "docs-en-debug");

        FileHelper.walk(docsEnDebugDir, f1 -> {
            if (f1.getName().endsWith(".md.md")) {
                File newFile = new File(f1.getParentFile(), StringHelper.removeEnd(f1.getName(), ".md"));
                f1.renameTo(newFile);
            }
            return FileVisitResult.CONTINUE;
        });
    }

    @Test
    public void copyImages() {
        File docsDir = getDocsDir();
        File docsEnDir = new File(docsDir.getParentFile(), "docs-en");

        FileHelper.walk2(docsDir, docsEnDir, (f1, f2) -> {
            String ext = StringHelper.fileExt(f1.getName()).toLowerCase();
            if (List.of("png", "jpg", "jpeg", "gif","bmp").contains(ext)) {
                FileHelper.copyFile(f1, f2);
                return FileVisitResult.CONTINUE;
            }
            return FileVisitResult.CONTINUE;
        });
    }

    void translateFile(String model, Consumer<AiTranslateCommand> config) {
        String promptName = "translate3";
        int contextLength = 4096;
        AiTranslateCommand translator = new AiTranslateCommand(chatService, templateManager, promptName);
        translator.fromLang("中文").toLang("英文").concurrencyLimit(1).maxChunkSize(2048);
        translator.setReturnExceptionAsResponse(true);
        translator.makeChatOptions().setProvider("ollama");
        translator.makeChatOptions().setModel(model);
        translator.makeChatOptions().setTemperature(0.6f);
        translator.makeChatOptions().setRequestTimeout(600 * 1000L);
        translator.makeChatOptions().setContextLength(contextLength);
        //translator.getChatOptions().setMaxTokens(4096);
        translator.setDebug(true);

        config.accept(translator);

        File docsDir = getDocsDir();

        File srcFile = new File(docsDir, "compare/nop-vs-apijson.md");
        String normalizedName = model.replace(':', '-') + '-' + CoreMetrics.currentTimeMillis() + "-" + promptName
                + "-" + translator.makeChatOptions().getContextLength() + "," + translator.makeChatOptions().getTemperature();
        File targetFile = getTargetFile("translated/" + normalizedName + ".md");
        targetFile.delete();
        translator.translateFile(srcFile, targetFile, null, null, new Semaphore(1));
    }

    @Test
    public void testDebugFile() {
        File docsDir = getDocsDir();
        File docsEnDebugDir = new File(docsDir.getParentFile(), "docs-en-debug");

        File file = new File(docsEnDebugDir, "theory/why-xlang-is-innovative.md");
        List<AiChatExchange> responses = DebugMessageHelper.parseDebugFile(file);
        for (AiChatExchange response : responses) {
            System.out.println(response.getContent());
        }
    }

    @Test
    public void testFixMarkdown() {
        File docsDir = getDocsDir();
        File docsEnDebugDir = new File(docsDir.getParentFile(), "docs-en-debug");
        File fixDir = new File(docsDir.getParentFile(), "docs-en-debug-fix");
        new FixMarkdownTranslation().fixDir(docsEnDebugDir, fixDir);
    }

    @Test
    public void fixDebugFile() {
        File docsDir = getDocsDir();
        File docsEnDebugDir = new File(docsDir.getParentFile(), "docs-en-debug");
        File docsEnFixDir = new File(docsDir.getParentFile(), "docs-en-debug-fix");

        fixDebugFileForDir(docsEnFixDir);
        fixDebugFileForDir(docsEnDebugDir);
    }

    void fixDebugFileForDir(File dir) {
        FileHelper.walk(dir, f1 -> {
            if (f1.getName().endsWith(".md")) {
                List<AiChatExchange> messages = DebugMessageHelper.parseDebugFile(f1);
                boolean changed = false;
                for (AiChatExchange message : messages) {
                    String content = message.getContent();
                    if (content != null && content.contains("<TRANSLATE_RESULT>")) {
                        message.parseContentBlock("<TRANSLATE_RESULT>\n", "\n</TRANSLATE_RESULT>", false, false);
                        changed = true;
                    }
                }

                if (changed) {
                    DebugMessageHelper.writeDebugFile(f1, messages);
                }
            }
            return FileVisitResult.CONTINUE;
        });
    }

    @Test
    public void removeErrorFiles() {
        File docsDir = getDocsDir();
        File docsEnDir = new File(docsDir.getParentFile(), "docs-en-x");
        File docsEnDebugDir = new File(docsDir.getParentFile(), "docs-en-debug-x");

        FileHelper.walk2(docsEnDir, docsEnDebugDir, (f1, f2) -> {
            if (!f1.isFile())
                return FileVisitResult.CONTINUE;

            String text1 = FileHelper.readText(f1, null);
            boolean error = false;
            if (StringHelper.countChinese(text1) > 50) {
                error = true;
            }

            if (f2.exists()) {
                String text = FileHelper.readText(f2, null);
                if (text.startsWith(DebugMessageHelper.PROMPT_BEGIN + DebugMessageHelper.PROMPT_BEGIN)) {
                    error = true;
                }
                if (text.indexOf("AiChatResponse{") > 0) {
                    error = true;
                }
            }

            if (error) {
                System.out.println("remove-error:" + FileHelper.getAbsolutePath(f1));
                f1.delete();
                f2.delete();
            }

            return FileVisitResult.CONTINUE;
        });
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
        translateFile("deepseek-r1:8b", translator -> translator.makeChatOptions().setContextLength(32768));
    }

    @Test
    public void testDeepSeek14B() {
        translateFile("deepseek-r1:14b", translator -> translator.makeChatOptions().setContextLength(4096));
    }

    @Test
    public void testFixTranslate() {
        translateFile("deepseek-r1:14b", translator -> {
            translator.makeChatOptions().setContextLength(4096);
            AiCheckTranslationCommand checkTool = newCheckTool();
            checkTool.makeChatOptions().setContextLength(4096);
            translator.checkTranslationTool(checkTool);
            translator.needFixChecker(msg -> {
                return true; //StringHelper.containsChinese(msg.getContent());
            });
        });
    }

    AiCheckTranslationCommand newCheckTool() {
        AiCheckTranslationCommand tool = new AiCheckTranslationCommand(chatService, templateManager, "check-translation");
        return tool;
    }
}
