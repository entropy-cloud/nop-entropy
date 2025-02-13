package io.nop.ai.translate;

import io.nop.ai.core.api.chat.ChatOptions;
import io.nop.ai.core.api.chat.IChatSession;
import io.nop.ai.core.api.chat.IChatSessionFactory;
import io.nop.ai.core.api.messages.AiResultMessage;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.ai.core.prompt.IPromptTemplateManager;
import io.nop.ai.translate.support.SimpleTextSplitter;
import io.nop.api.core.annotations.core.PropertySetter;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Semaphore;
import java.util.function.Predicate;

import static io.nop.ai.translate.AiTranslateConstants.VAR_CONTENT;
import static io.nop.ai.translate.AiTranslateConstants.VAR_EXTRA_PROMPT;
import static io.nop.ai.translate.AiTranslateConstants.VAR_FROM_LANG;
import static io.nop.ai.translate.AiTranslateConstants.VAR_MODEL;
import static io.nop.ai.translate.AiTranslateConstants.VAR_PROLOG;
import static io.nop.ai.translate.AiTranslateConstants.VAR_TO_LANG;

public class AiTranslator {
    private final IChatSessionFactory factory;
    private String fromLang;
    private String toLang;

    private String extraPrompt;
    private ITextSplitter textSplitter = SimpleTextSplitter.INSTANCE;
    private final IPromptTemplate promptTemplate;
    private int prologSize = 256;
    private int maxChunkSize = 4096;
    private Predicate<File> fileFilter;
    private int concurrencyLimit = 10;

    public AiTranslator(IChatSessionFactory factory, IPromptTemplate promptTemplate) {
        this.factory = factory;
        this.promptTemplate = promptTemplate;
    }

    public AiTranslator(IChatSessionFactory factory, IPromptTemplateManager promptTemplateManager, String promptName) {
        this(factory, promptTemplateManager.getPromptTemplate(factory.getModel(), promptName));
    }

    @PropertySetter
    public AiTranslator fromLang(String fromLang) {
        this.fromLang = fromLang;
        return this;
    }

    @PropertySetter
    public AiTranslator toLang(String toLang) {
        this.toLang = toLang;
        return this;
    }

    @PropertySetter
    public AiTranslator extraPrompt(String extraPrompt) {
        this.extraPrompt = extraPrompt;
        return this;
    }

    @PropertySetter
    public AiTranslator textSplitter(ITextSplitter textSplitter) {
        this.textSplitter = textSplitter;
        return this;
    }

    @PropertySetter
    public AiTranslator prologSize(int prologSize) {
        this.prologSize = prologSize;
        return this;
    }

    @PropertySetter
    public AiTranslator maxChunkSize(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
        return this;
    }

    @PropertySetter
    public AiTranslator fileFilter(Predicate<File> fileFilter) {
        this.fileFilter = fileFilter;
        return this;
    }

    @PropertySetter
    public AiTranslator concurrencyLimit(int concurrencyLimit) {
        this.concurrencyLimit = concurrencyLimit;
        return this;
    }

    public void translateDir(File srcDir, File targetFir, ICancelToken cancelToken) {
        FutureHelper.syncGet(translateDirAsync(srcDir, targetFir, cancelToken));
    }

    public CompletionStage<Void> translateDirAsync(File srcDir, File targetDir, ICancelToken cancelToken) {
        List<CompletionStage<?>> futures = new ArrayList<>();

        Semaphore limit = new Semaphore(concurrencyLimit);

        FileHelper.walk2(srcDir, targetDir, (f1, f2) -> {
            if (f1.isFile() && acceptSourceFile(f1) && acceptTargetFile(f2)) {
                String text = FileHelper.readText(f1, null);
                futures.add(translateAsync(text, cancelToken, limit).thenApply(ret -> {
                    FileHelper.writeText(f2, text, null);
                    return null;
                }));
            }
            return FileVisitResult.CONTINUE;
        });
        return FutureHelper.waitAll(futures);
    }

    protected boolean acceptSourceFile(File file) {
        if (fileFilter != null)
            return fileFilter.test(file);
        String fileExt = StringHelper.fileExt(file.getName());
        return fileExt.equals("md") || fileExt.equals("txt");
    }

    protected boolean acceptTargetFile(File file) {
        return !file.exists();
    }

    public CompletionStage<String> translateAsync(String text, ICancelToken cancelToken, Semaphore limit) {
        if (textSplitter != null && text.length() > maxChunkSize) {
            List<ITextSplitter.SplitChunk> chunks = textSplitter.split(text, prologSize, maxChunkSize);

            List<CompletionStage<?>> promises = new ArrayList<>(chunks.size());
            for (ITextSplitter.SplitChunk chunk : chunks) {
                promises.add(FutureHelper.executeWithThrottling(() ->
                        doTranslateAsync(chunk.getProlog(), chunk.getContent(), cancelToken), limit));
            }

            return FutureHelper.waitAll(promises).thenApply(v -> {
                StringBuilder sb = new StringBuilder();
                for (CompletionStage<?> promise : promises) {
                    String result = (String) FutureHelper.syncGet(promise);
                    sb.append(result);
                }
                return sb.toString();
            });
        } else {
            return FutureHelper.executeWithThrottling(()->
                    doTranslateAsync(null, text, cancelToken), limit);
        }
    }

    protected CompletionStage<String> doTranslateAsync(String prolog, String text, ICancelToken cancelToken) {
        if (cancelToken != null && cancelToken.isCancelled())
            return FutureHelper.reject(new CancellationException("cancel-translate"));

        ChatOptions options = new ChatOptions();
        IChatSession session = factory.newSession(options);
        try {
            String promptText = promptTemplate.generatePrompt(
                    Map.of(VAR_MODEL, factory.getModel(), VAR_PROLOG, prolog == null ? "" : prolog, VAR_CONTENT, text,
                            VAR_FROM_LANG, fromLang, VAR_TO_LANG, toLang,
                            VAR_EXTRA_PROMPT, extraPrompt == null ? "" : extraPrompt));
            Prompt prompt = session.newPrompt(false);
            prompt.addHumanMessage(promptText);
            return session.sendChatAsync(prompt, cancelToken).thenApply(this::getTranslatedText)
                    .whenComplete((ret, err) -> IoHelper.safeCloseObject(session));
        } catch (Exception e) {
            IoHelper.safeCloseObject(session);
            throw NopException.adapt(e);
        }
    }

    protected String getTranslatedText(AiResultMessage message) {
        return message.getContent();
    }
}