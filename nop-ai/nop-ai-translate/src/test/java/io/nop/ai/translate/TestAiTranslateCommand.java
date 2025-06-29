package io.nop.ai.translate;

import io.nop.ai.core.api.chat.IAiChatService;
import io.nop.ai.core.prompt.IPromptTemplateManager;
import io.nop.api.core.time.CoreMetrics;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

@Disabled
public class TestAiTranslateCommand extends JunitBaseTestCase {

    @Inject
    IAiChatService chatService;

    @Inject
    IPromptTemplateManager templateManager;

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
            if (List.of("png", "jpg", "jpeg", "gif", "bmp").contains(ext)) {
                FileHelper.copyFile(f1, f2);
                return FileVisitResult.CONTINUE;
            }
            return FileVisitResult.CONTINUE;
        });
    }

    void translateFile(String promptName, String provider, String model, Consumer<AiTranslateCommand> config) {
        int contextLength = 4096;
        AiTranslateCommand translator = new AiTranslateCommand(chatService, templateManager, promptName);
        translator.fromLang("中文").toLang("英文").concurrencyLimit(1).maxChunkSize(2048);
        translator.setReturnExceptionAsResponse(true);
        translator.makeChatOptions().setProvider(provider);
        translator.makeChatOptions().setModel(model);
        translator.makeChatOptions().setTemperature(0.6f);
        translator.makeChatOptions().setRequestTimeout(600 * 1000L);
        translator.makeChatOptions().setContextLength(contextLength);
        //translator.getChatOptions().setMaxTokens(4096);
        translator.setDebug(true);
        translator.useResponseCache(true);

        config.accept(translator);

        File docsDir = getDocsDir();

        File srcFile = new File(docsDir, "arch/index.md");//""compare/nop-vs-apijson.md");
        String normalizedName = model.replace(':', '-') + '-' + CoreMetrics.currentTimeMillis() + "-" + promptName
                + "-" + translator.makeChatOptions().getContextLength() + "," + translator.makeChatOptions().getTemperature();
        File targetFile = getTargetFile("translated/" + normalizedName + ".md");
        targetFile.delete();
        translator.translateFile(srcFile, targetFile, null, null, new Semaphore(1));
    }

    @Test
    public void testQwen7B() {
        translateFile("translate/translate-nop", "ollama", "qwen2.5-coder:7b", translator -> {
        });
    }

    @Test
    public void testDeepSeek8B() {
        translateFile("translate/translate-nop", "ollama", "deepseek-r1:8b", translator -> {
        });
    }

    @Test
    public void testDeepSeek8B32K() {
        translateFile("translate/translate-nop", "ollama", "deepseek-r1:8b", translator -> translator.makeChatOptions().setContextLength(32768));
    }

    @Test
    public void testDeepSeek14B() {
        translateFile("translate/translate-nop", "ollama", "deepseek-r1:14b", translator -> translator.makeChatOptions().setContextLength(4096));
    }

    @Test
    public void testDeepSeek() {
        translateFile("translate/translate-nop", "deepseek", "deepseek-reasoner", translator -> {
            translator.makeChatOptions().setContextLength(50000);
            translator.makeChatOptions().setMaxTokens(50000);
            translator.maxChunkSize(64000);
        });
    }

}
