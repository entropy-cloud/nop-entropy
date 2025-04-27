package io.nop.ai.translate;

import io.nop.ai.core.api.chat.IAiChatService;
import io.nop.ai.core.api.messages.AiChatResponse;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.ai.core.command.AiCommand;
import io.nop.ai.core.commons.aggregator.IAiTextAggregator;
import io.nop.ai.core.commons.debug.DebugMessageHelper;
import io.nop.ai.core.commons.processor.IAiChatResponseChecker;
import io.nop.ai.core.commons.processor.IAiChatResponseProcessor;
import io.nop.ai.core.commons.splitter.IAiTextSplitter;
import io.nop.ai.core.commons.splitter.MarkdownTextSplitter;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.ai.core.prompt.IPromptTemplateManager;
import io.nop.api.core.annotations.core.PropertySetter;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.ResolvedPromise;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.retry.RetryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.nop.ai.translate.AiTranslateConstants.VAR_CONTENT;
import static io.nop.ai.translate.AiTranslateConstants.VAR_FROM_LANG;
import static io.nop.ai.translate.AiTranslateConstants.VAR_TO_LANG;
import static io.nop.api.core.util.FutureHelper.getResults;

public class AiTranslateCommand extends AiCommand {
    static final Logger LOG = LoggerFactory.getLogger(AiTranslateCommand.class);

    private String fromLang;
    private String toLang;

    private IAiTextSplitter textSplitter = new MarkdownTextSplitter();
    private int maxChunkSize = 4096;
    private Predicate<File> fileFilter;
    private int concurrencyLimit = 10;
    private IAiTextAggregator textAggregator;
    private IAiChatResponseChecker needFixChecker;

    private AiCheckTranslationCommand checkTranslationTool;

    /**
     * 将prompt和对应的结果保存到debug文件中
     */
    private boolean debug = false;
    private File debugDir;
    private boolean recoverMode = false;

    public AiTranslateCommand(IAiChatService chatService, IPromptTemplate promptTemplate) {
        super(chatService);
        this.setPromptTemplate(promptTemplate);
    }

    public AiTranslateCommand(IAiChatService chatService, IPromptTemplateManager promptTemplateManager, String promptName) {
        this(chatService, promptTemplateManager.getPromptTemplate(promptName));
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public String getFromLang() {
        return fromLang;
    }

    public String getToLang() {
        return toLang;
    }

    public IAiTextSplitter getTextSplitter() {
        return textSplitter;
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

    @PropertySetter
    public AiTranslateCommand debugDir(File debugDir) {
        this.debugDir = debugDir;
        return this;
    }

    @PropertySetter
    public AiTranslateCommand recoverMode(boolean recoverMode) {
        this.recoverMode = recoverMode;
        return this;
    }

    @PropertySetter
    public AiTranslateCommand checkTranslationTool(AiCheckTranslationCommand checkTranslationTool) {
        this.checkTranslationTool = checkTranslationTool;
        if (fromLang != null)
            checkTranslationTool.setFromLang(fromLang);
        if (toLang != null)
            checkTranslationTool.setToLang(toLang);
        return this;
    }

    @PropertySetter
    public AiTranslateCommand fromLang(String fromLang) {
        this.fromLang = fromLang;
        return this;
    }

    @PropertySetter
    public AiTranslateCommand toLang(String toLang) {
        this.toLang = toLang;
        return this;
    }

    @PropertySetter
    public AiTranslateCommand textSplitter(IAiTextSplitter textSplitter) {
        this.textSplitter = textSplitter;
        return this;
    }

    @PropertySetter
    public AiTranslateCommand maxChunkSize(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
        return this;
    }

    @PropertySetter
    public AiTranslateCommand fileFilter(Predicate<File> fileFilter) {
        this.fileFilter = fileFilter;
        return this;
    }

    public AiTranslateCommand chatResponseProcessor(IAiChatResponseProcessor chatResponseProcessor) {
        this.setChatResponseProcessor(chatResponseProcessor);
        return this;
    }

    @PropertySetter
    public AiTranslateCommand concurrencyLimit(int concurrencyLimit) {
        this.concurrencyLimit = concurrencyLimit;
        return this;
    }

    @PropertySetter
    public AiTranslateCommand textAggregator(IAiTextAggregator textAggregator) {
        this.textAggregator = textAggregator;
        return this;
    }

    public AiTranslateCommand retryTimesPerRequest(int tryTimesPerRequest) {
        this.setRetryTimesPerRequest(tryTimesPerRequest);
        return this;
    }

    @PropertySetter
    public AiTranslateCommand needFixChecker(IAiChatResponseChecker resultChecker) {
        this.needFixChecker = resultChecker;
        return this;
    }

    public void translateDir(File srcDir, File targetFir, ICancelToken cancelToken) {
        FutureHelper.syncGet(translateDirAsync(srcDir, targetFir, cancelToken));
    }

    public static void syncFromDebugFile(File outDir, File debugDir) {
        FileHelper.walk2(debugDir, outDir, (debugFile, outFile) -> {
            if (!debugFile.getName().endsWith(".md"))
                return FileVisitResult.CONTINUE;

            List<AiChatResponse> messages = DebugMessageHelper.parseDebugFile(debugFile);
            String aggText = DebugMessageHelper.getText(messages);

            String text = FileHelper.readText(outFile, null);
            if (!aggText.equals(text)) {
                FileHelper.writeText(outFile, aggText, null);
            }
            return FileVisitResult.CONTINUE;
        });
    }

    public CompletionStage<Void> translateDirAsync(File srcDir, File targetDir, ICancelToken cancelToken) {
        List<CompletionStage<?>> futures = new ArrayList<>();

        Semaphore limit = new Semaphore(concurrencyLimit);

        String path = srcDir.getAbsolutePath();
        File debugDir = this.debugDir != null ? this.debugDir
                : new File(targetDir.getParentFile(), targetDir.getName() + "-debug");
        FileHelper.walk2(srcDir, targetDir, (srcFile, targetFile) -> {
            if (srcFile.isFile() && acceptSourceFile(srcFile) && acceptTargetFile(targetFile)) {
                File debugFile = new File(debugDir, srcFile.getAbsolutePath().substring(path.length()));
                CompletionStage<?> future = translateFileAsync(srcFile, targetFile, debugFile, cancelToken, limit);
                futures.add(future);
            }
            return FileVisitResult.CONTINUE;
        });
        return FutureHelper.waitAll(futures);
    }

    public void translateFile(File srcFile, File targetFile, File debugFile,
                              ICancelToken cancelToken, Semaphore limit) {
        FutureHelper.syncGet(translateFileAsync(srcFile, targetFile, debugFile,
                cancelToken, limit));
    }

    public CompletionStage<?> translateFileAsync(File srcFile, File targetFile, File debugFile,
                                                 ICancelToken cancelToken, Semaphore limit) {
        LOG.info("nop.ai.translate-file:path={}", FileHelper.getAbsolutePath(srcFile));

        CompletionStage<AggregateText> promise;
        if (recoverMode && debugFile != null && debugFile.exists()) {
            promise = recoverFromDebugFileAsync(debugFile, cancelToken, limit);
        } else {
            String text = FileHelper.readText(srcFile, null);
            promise = translateLongTextAsync(text, cancelToken, limit);
        }

        return promise.thenApply(ret -> {
            if (debug && debugFile != null)
                FileHelper.writeText(debugFile, ret.getDebugText(), null);

            if (ret.isAllValid())
                FileHelper.writeText(targetFile, ret.getText(), null);
            return null;
        });
    }

    public CompletionStage<AggregateText> recoverFromDebugFileAsync(File debugFile,
                                                                    ICancelToken cancelToken, Semaphore limit) {
        LOG.info("nop.ai.translate-file-with-recover-mode:debugFile={}", FileHelper.getAbsolutePath(debugFile));

        List<AiChatResponse> messages = DebugMessageHelper.parseDebugFile(debugFile);

        Cancellable cancellable = new Cancellable();
        Consumer<String> cleanup = cancellable::cancel;
        if (cancelToken != null) {
            cancelToken.appendOnCancel(cleanup);
        }
        List<CompletionStage<AiChatResponse>> promises = new ArrayList<>(messages.size());
        for (AiChatResponse message : messages) {
            if (message.isValid()) {
                promises.add(FutureHelper.success(message));
                continue;
            }

            CompletionStage<AiChatResponse> promise = FutureHelper.executeWithThrottling(() ->
                    translateTextAsync(message.getPrompt().getLastMessage().getContent(), cancellable), limit);

            promises.add(promise);
        }

        CompletionStage<AggregateText> ret = FutureHelper.thenCompleteAsync(FutureHelper.waitAll(promises), (v, e) -> {
            return aggregateResults(getResults(promises));
        });

        return ret.whenComplete((r, e) -> {
            if (cancelToken != null)
                cancelToken.removeOnCancel(cleanup);
        });
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

    public CompletionStage<AggregateText> translateLongTextAsync(String text, ICancelToken cancelToken, Semaphore limit) {
        if (textSplitter != null && text.length() > maxChunkSize) {
            List<IAiTextSplitter.SplitChunk> chunks = textSplitter.split(text, maxChunkSize);

            List<CompletionStage<AiChatResponse>> promises = new ArrayList<>(chunks.size());
            for (IAiTextSplitter.SplitChunk chunk : chunks) {
                CompletionStage<AiChatResponse> promise = FutureHelper.executeWithThrottling(() ->
                        translateTextAsync(chunk.getContent(), cancelToken), limit);

                promises.add(promise);
            }


            CompletionStage<AggregateText> ret = FutureHelper.thenCompleteAsync(FutureHelper.waitAll(promises), (v, e) -> {
                return aggregateResults(getResults(promises));
            });

            return ret;

        } else {
            return FutureHelper.executeWithThrottling(() ->
                    translateTextAsync(text, cancelToken).thenApply(AggregateText::fromResultMessage), limit);
        }
    }

    AggregateText aggregateResults(List<AiChatResponse> messages) {
        String text;
        if (textAggregator != null) {
            text = textAggregator.aggregate(messages);
        } else {
            text = messages.stream().map(AiChatResponse::getContent).collect(Collectors.joining("\n\n"));
        }
        return new AggregateText(messages, text);
    }

    public CompletionStage<AiChatResponse> translateTextAsync(String text, ICancelToken cancelToken) {
        if (checkTranslationTool != null) {
            return RetryHelper.retryNTimes(index -> {
                        return doTranslateTextAsync(text, cancelToken).thenCompose(ret -> {
                            if (!ret.isValid())
                                return ResolvedPromise.success(ret);
                            if (!needCheck(ret))
                                return ResolvedPromise.success(ret);
                            return checkTranslationTool.fixTranslationAsync(text, ret.getContent(), cancelToken);
                        });
                    },
                    AiChatResponse::isValid, 1);
        }
        return doTranslateTextAsync(text, cancelToken);
    }

    protected boolean needCheck(AiChatResponse message) {
        if (needFixChecker != null)
            return needFixChecker.isAccepted(message);
        return false;
    }

    protected CompletionStage<AiChatResponse> doTranslateTextAsync(String text, ICancelToken cancelToken) {
        Map<String, Object> vars = Map.of(VAR_CONTENT, text,
                VAR_FROM_LANG, fromLang, VAR_TO_LANG, toLang);

        if (StringHelper.isBlank(text)) {
            AiChatResponse response = new AiChatResponse();
            Prompt prompt = newPrompt(prepareInputs(vars));
            response.setPrompt(prompt);
            response.setContent(text);
            return FutureHelper.success(response);
        }

        return executeAsync(vars, cancelToken);
    }

}