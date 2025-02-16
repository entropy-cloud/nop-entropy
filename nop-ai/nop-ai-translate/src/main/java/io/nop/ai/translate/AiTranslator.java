package io.nop.ai.translate;

import io.nop.ai.core.api.aggregator.IAiTextAggregator;
import io.nop.ai.core.api.chat.ChatOptions;
import io.nop.ai.core.api.chat.IChatSession;
import io.nop.ai.core.api.chat.IChatSessionFactory;
import io.nop.ai.core.api.messages.AiResultMessage;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.ai.core.api.processor.IAiResultMessageProcessor;
import io.nop.ai.core.api.processor.IAiTextRewriter;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.ai.core.prompt.IPromptTemplateManager;
import io.nop.ai.translate.support.MarkdownSplitter;
import io.nop.api.core.annotations.core.PropertySetter;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.retry.RetryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Semaphore;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.nop.ai.translate.AiTranslateConstants.VAR_CONTENT;
import static io.nop.ai.translate.AiTranslateConstants.VAR_EXTRA_PROMPT;
import static io.nop.ai.translate.AiTranslateConstants.VAR_FROM_LANG;
import static io.nop.ai.translate.AiTranslateConstants.VAR_MODEL;
import static io.nop.ai.translate.AiTranslateConstants.VAR_PROLOG;
import static io.nop.ai.translate.AiTranslateConstants.VAR_TO_LANG;

public class AiTranslator {
    static final Logger LOG = LoggerFactory.getLogger(AiTranslator.class);

    private final IChatSessionFactory factory;
    private String fromLang;
    private String toLang;

    private String extraPrompt;
    private ITextSplitter textSplitter = new MarkdownSplitter();
    private final IPromptTemplate promptTemplate;
    private int prologSize = 256;
    private int maxChunkSize = 4096;
    private Predicate<File> fileFilter;
    private int concurrencyLimit = 10;

    /**
     * 单次请求失败之后会自动重试，这里控制总的尝试次数。
     */
    private int tryTimesPerRequest = 3;
    private IAiResultMessageProcessor resultMessageProcessor;
    private IAiTextAggregator textAggregator;
    private ChatOptions chatOptions = new ChatOptions();

    private IAiTextRewriter textRewriter = new TranslateTextRewriter();

    public AiTranslator(IChatSessionFactory factory, IPromptTemplate promptTemplate) {
        this.factory = factory;
        this.promptTemplate = promptTemplate;
    }

    public AiTranslator(IChatSessionFactory factory, IPromptTemplateManager promptTemplateManager, String promptName) {
        this(factory, promptTemplateManager.getPromptTemplate(factory.getModel(), promptName));
    }

    public int getTryTimes(){
        return tryTimesPerRequest;
    }

    public ChatOptions getChatOptions(){
        return chatOptions;
    }

    public String getFromLang() {
        return fromLang;
    }

    public String getToLang() {
        return toLang;
    }

    public String getExtraPrompt() {
        return extraPrompt;
    }

    public ITextSplitter getTextSplitter() {
        return textSplitter;
    }

    public IPromptTemplate getPromptTemplate() {
        return promptTemplate;
    }

    public int getPrologSize() {
        return prologSize;
    }

    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    public Predicate<File> getFileFilter() {
        return fileFilter;
    }

    public int getConcurrencyLimit() {
        return concurrencyLimit;
    }

    public IAiResultMessageProcessor getResultMessageProcessor() {
        return resultMessageProcessor;
    }

    @PropertySetter
    public AiTranslator chatOptions(ChatOptions chatOptions) {
        this.chatOptions = chatOptions;
        return this;
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
    public AiTranslator resultMessageProcessor(IAiResultMessageProcessor resultMessageProcessor) {
        this.resultMessageProcessor = resultMessageProcessor;
        return this;
    }

    @PropertySetter
    public AiTranslator concurrencyLimit(int concurrencyLimit) {
        this.concurrencyLimit = concurrencyLimit;
        return this;
    }

    public IAiTextRewriter getTextRewriter() {
        return textRewriter;
    }

    @PropertySetter
    public AiTranslator textProcessor(IAiTextRewriter textProcessor) {
        this.textRewriter = textProcessor;
        return this;
    }

    @PropertySetter
    public AiTranslator textAggregator(IAiTextAggregator textAggregator) {
        this.textAggregator = textAggregator;
        return this;
    }

    @PropertySetter
    public AiTranslator tryTimesPerRequest(int tryTimesPerRequest) {
        this.tryTimesPerRequest = tryTimesPerRequest;
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
                LOG.info("nop.ai.translate-file:path={}", FileHelper.getAbsolutePath(f1));

                String text = FileHelper.readText(f1, null);
                futures.add(translateAsync(text, cancelToken, limit).thenApply(ret -> {
                    FileHelper.writeText(f2, ret, null);
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
                        doTranslateWithModifierAsync(chunk.getProlog(), chunk.getContent(), cancelToken), limit));
            }

            return FutureHelper.waitAll(promises).thenApply(v -> {
                return aggregateResults(getResults(promises));
            });
        } else {
            return FutureHelper.executeWithThrottling(() ->
                    doTranslateWithModifierAsync(null, text, cancelToken), limit);
        }
    }

    List<String> getResults(List<CompletionStage<?>> promises) {
        return promises.stream().map(promise -> (String) FutureHelper.syncGet(promise)).collect(Collectors.toList());
    }

    String aggregateResults(List<String> results) {
        if (textAggregator != null)
            return textAggregator.aggregate(results);

        StringBuilder sb = new StringBuilder();
        for (String result : results) {
            sb.append(result);
            sb.append("\n**********************************\n");
        }
        return sb.toString();
    }

    protected CompletionStage<String> doTranslateWithModifierAsync(String prolog, String text, ICancelToken cancelToken) {
        // 将待翻译的文本放到特殊的字符串之间，然后在结果中要严格保持这两个字符串的匹配结构，通过这一特性来自动实现对返回格式的检查。
        // qwen3b有时会直接把原文返回，并不会做翻译。有的时候又会多一些多余的响应。

        String toTranslated = textRewriter == null ? text : textRewriter.rewriteRequestText(text);
        return RetryHelper.retryNTimes(() -> doTranslateAsync(prolog, toTranslated, null)
                                .thenApply(ret -> {
                                    return textRewriter == null ? ret : textRewriter.correctResponseText(ret);
                                }),
                        Objects::nonNull, tryTimesPerRequest)
                .thenApply(ret -> Guard.notNull(ret, "invalid text"))
                .exceptionally(err -> {
                    LOG.error("nop.ai.translate-error", err);
                    throw NopException.adapt(err);
                });
    }

    protected CompletionStage<String> doTranslateAsync(String prolog, String text, ICancelToken cancelToken) {
        if (cancelToken != null && cancelToken.isCancelled())
            return FutureHelper.reject(new CancellationException("cancel-translate"));

        IChatSession session = factory.newSession(chatOptions);
        try {
            String promptText = promptTemplate.generatePrompt(
                    Map.of(VAR_MODEL, factory.getModel(), VAR_PROLOG, prolog == null ? "" : prolog, VAR_CONTENT, text,
                            VAR_FROM_LANG, fromLang, VAR_TO_LANG, toLang,
                            VAR_EXTRA_PROMPT, extraPrompt == null ? "" : extraPrompt));
            Prompt prompt = session.newPrompt(false);
            prompt.addHumanMessage(promptText);

            CompletionStage<AiResultMessage> future = session.sendChatAsync(prompt, cancelToken);
            if (resultMessageProcessor != null)
                future = future.thenCompose(ret -> resultMessageProcessor.processAsync(prompt, ret));
            return future.thenApply(this::getTranslatedText)
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