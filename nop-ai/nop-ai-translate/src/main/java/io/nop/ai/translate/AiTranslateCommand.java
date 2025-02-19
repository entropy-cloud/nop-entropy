package io.nop.ai.translate;

import io.nop.ai.core.api.chat.IAiChatService;
import io.nop.ai.core.api.messages.AiResultMessage;
import io.nop.ai.core.commons.AiCommand;
import io.nop.ai.core.commons.aggregator.IAiTextAggregator;
import io.nop.ai.core.commons.processor.IAiResultMessageChecker;
import io.nop.ai.core.commons.processor.IAiResultMessageProcessor;
import io.nop.ai.core.commons.splitter.IAiTextSplitter;
import io.nop.ai.core.commons.splitter.MarkdownTextSplitter;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.ai.core.prompt.IPromptTemplateManager;
import io.nop.api.core.annotations.core.PropertySetter;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.ResolvedPromise;
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
    private IAiResultMessageChecker needFixChecker;

    private AiCheckTranslationCommand checkTranslationTool;

    /**
     * 将prompt和对应的结果保存到debug文件中
     */
    private boolean debug = false;

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

    public AiTranslateCommand resultMessageProcessor(IAiResultMessageProcessor resultMessageProcessor) {
        this.setResultMessageProcessor(resultMessageProcessor);
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
    public AiTranslateCommand needFixChecker(IAiResultMessageChecker resultChecker) {
        this.needFixChecker = resultChecker;
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
                futures.add(translateLongTextAsync(text, cancelToken, limit).thenApply(ret -> {
                    if (debug)
                        FileHelper.writeText(getDebugFile(f2), ret.getDebugText(), null);

                    FileHelper.writeText(f2, ret.getText(), null);
                    return null;
                }));
            }
            return FileVisitResult.CONTINUE;
        });
        return FutureHelper.waitAll(futures);
    }

    public void translateFile(File srcFile, File targetFile, ICancelToken cancelToken, Semaphore limit) {
        FutureHelper.syncGet(translateFileAsync(srcFile, targetFile, cancelToken, limit));
    }

    public CompletionStage<?> translateFileAsync(File srcFile, File targetFile, ICancelToken cancelToken, Semaphore limit) {
        LOG.info("nop.ai.translate-file:path={}", FileHelper.getAbsolutePath(srcFile));

        String text = FileHelper.readText(srcFile, null);
        return translateLongTextAsync(text, cancelToken, limit).thenApply(ret -> {
            if (debug)
                FileHelper.writeText(getDebugFile(targetFile), ret.getDebugText(), null);

            FileHelper.writeText(targetFile, ret.getText(), null);
            return null;
        });
    }

    protected File getDebugFile(File targetFile) {
        return new File(targetFile.getParentFile(), targetFile.getName() + ".debug." + StringHelper.fileExt(targetFile.getName()));
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

            List<CompletionStage<AiResultMessage>> promises = new ArrayList<>(chunks.size());
            for (IAiTextSplitter.SplitChunk chunk : chunks) {
                promises.add(FutureHelper.executeWithThrottling(() ->
                        translateTextAsync(chunk.getContent(), cancelToken), limit));
            }

            return FutureHelper.waitAll(promises).thenApply(v -> {
                return aggregateResults(getResults(promises));
            });
        } else {
            return FutureHelper.executeWithThrottling(() ->
                    translateTextAsync(text, cancelToken).thenApply(AggregateText::fromResultMessage), limit);
        }
    }

    AggregateText aggregateResults(List<AiResultMessage> messages) {
        String text;
        if (textAggregator != null) {
            text = textAggregator.aggregate(messages);
        } else {
            text = messages.stream().map(AiResultMessage::getContent).collect(Collectors.joining("\n"));
        }
        return new AggregateText(messages, text);
    }

    public CompletionStage<AiResultMessage> translateTextAsync(String text, ICancelToken cancelToken) {
        if (checkTranslationTool != null) {
            return RetryHelper.retryNTimes(() -> {
                        return doTranslateTextAsync(text, cancelToken).thenCompose(ret -> {
                            if (!ret.isValid())
                                return ResolvedPromise.success(ret);
                            if (!needCheck(ret))
                                return ResolvedPromise.success(ret);
                            return checkTranslationTool.fixTranslationAsync(text, ret.getContent(), cancelToken);
                        });
                    },
                    AiResultMessage::isValid, 1);
        }
        return doTranslateTextAsync(text, cancelToken);
    }

    protected boolean needCheck(AiResultMessage message) {
        if (needFixChecker != null)
            return needFixChecker.isAccepted(message);
        return false;
    }

    protected CompletionStage<AiResultMessage> doTranslateTextAsync(String text, ICancelToken cancelToken) {
        Map<String, Object> vars = Map.of(VAR_CONTENT, text,
                VAR_FROM_LANG, fromLang, VAR_TO_LANG, toLang);

        return callAiAsync(vars, cancelToken);
    }

}