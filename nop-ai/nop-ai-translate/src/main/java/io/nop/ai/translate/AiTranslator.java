package io.nop.ai.translate;

import io.nop.ai.core.api.aggregator.IAiTextAggregator;
import io.nop.ai.core.api.chat.IAiChatService;
import io.nop.ai.core.api.messages.AiResultMessage;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.ai.core.api.processor.IAiResultMessageProcessor;
import io.nop.ai.core.api.processor.IAiTextRewriter;
import io.nop.ai.core.commons.AiTool;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.ai.core.prompt.IPromptTemplateManager;
import io.nop.ai.translate.support.MarkdownSplitter;
import io.nop.api.core.annotations.core.PropertySetter;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
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
import static io.nop.ai.translate.AiTranslateConstants.VAR_EXTRA_PROMPT;
import static io.nop.ai.translate.AiTranslateConstants.VAR_FROM_LANG;
import static io.nop.ai.translate.AiTranslateConstants.VAR_PROLOG;
import static io.nop.ai.translate.AiTranslateConstants.VAR_TO_LANG;

public class AiTranslator extends AiTool {
    static final Logger LOG = LoggerFactory.getLogger(AiTranslator.class);

    private String fromLang;
    private String toLang;

    private String extraPrompt;
    private ITextSplitter textSplitter = new MarkdownSplitter();
    private int prologSize = 256;
    private int maxChunkSize = 4096;
    private Predicate<File> fileFilter;
    private int concurrencyLimit = 10;
    private IAiTextRewriter textRewriter;
    private IAiTextAggregator textAggregator;

    /**
     * 将prompt也保存到文件中，用于调试。
     */
    private boolean savePrompt = false;

    public AiTranslator(IAiChatService chatService, IPromptTemplate promptTemplate) {
        super(chatService);
        this.setPromptTemplate(promptTemplate);
    }

    public AiTranslator(IAiChatService chatService, IPromptTemplateManager promptTemplateManager, String promptName) {
        this(chatService, promptTemplateManager.getPromptTemplate(promptName));
    }

    public boolean isSavePrompt() {
        return savePrompt;
    }

    public void setSavePrompt(boolean savePrompt) {
        this.savePrompt = savePrompt;
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

    public AiTranslator resultMessageProcessor(IAiResultMessageProcessor resultMessageProcessor) {
        this.setResultMessageProcessor(resultMessageProcessor);
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
    public AiTranslator textRewriter(IAiTextRewriter textRewriter) {
        this.textRewriter = textRewriter;
        return this;
    }

    @PropertySetter
    public AiTranslator textAggregator(IAiTextAggregator textAggregator) {
        this.textAggregator = textAggregator;
        return this;
    }

    public AiTranslator retryTimesPerRequest(int tryTimesPerRequest) {
        this.setRetryTimesPerRequest(tryTimesPerRequest);
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

    public void translateFile(File srcFile, File targetFile, ICancelToken cancelToken, Semaphore limit) {
        FutureHelper.syncGet(translateFileAsync(srcFile, targetFile, cancelToken, limit));
    }

    public CompletionStage<?> translateFileAsync(File srcFile, File targetFile, ICancelToken cancelToken, Semaphore limit) {
        LOG.info("nop.ai.translate-file:path={}", FileHelper.getAbsolutePath(srcFile));

        String text = FileHelper.readText(srcFile, null);
        return translateAsync(text, cancelToken, limit).thenApply(ret -> {
            FileHelper.writeText(targetFile, ret, null);
            return null;
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

    public CompletionStage<String> translateAsync(String text, ICancelToken cancelToken, Semaphore limit) {
        if (textSplitter != null && text.length() > maxChunkSize) {
            List<ITextSplitter.SplitChunk> chunks = textSplitter.split(text, prologSize, maxChunkSize);

            List<CompletionStage<?>> promises = new ArrayList<>(chunks.size());
            for (ITextSplitter.SplitChunk chunk : chunks) {
                promises.add(FutureHelper.executeWithThrottling(() ->
                        doTranslateAsync(chunk.getProlog(), chunk.getContent(), cancelToken), limit));
            }

            return FutureHelper.waitAll(promises).thenApply(v -> {
                return aggregateResults(getResults(promises));
            });
        } else {
            return FutureHelper.executeWithThrottling(() ->
                    doTranslateAsync(null, text, cancelToken), limit);
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

    protected CompletionStage<String> doTranslateAsync(String prolog, String text, ICancelToken cancelToken) {
        if (textRewriter != null)
            text = textRewriter.rewriteRequestText(text);

        Map<String, Object> vars = Map.of(VAR_PROLOG, prolog == null ? "" : prolog, VAR_CONTENT, text,
                VAR_FROM_LANG, fromLang, VAR_TO_LANG, toLang,
                VAR_EXTRA_PROMPT, extraPrompt == null ? "" : extraPrompt);

        return callAiAsync(vars, cancelToken).thenApply(this::getTranslatedText);
    }

    @Override
    protected AiResultMessage postProcess(AiResultMessage message) {
        if (textRewriter != null) {
            String text = textRewriter.correctResponseText(message.getContent());
            message.setContent(text);
            if (text == null) {
                message.setInvalid(true);
                return message;
            }
        }
        return message;
    }

    protected String getTranslatedText(AiResultMessage message) {
        if (savePrompt) {
            StringBuilder sb = new StringBuilder();
            Prompt prompt = message.getPrompt();
            sb.append("<[prompt]>\n");
            sb.append(prompt.getMessages().get(0).getContent());
            sb.append("</[prompt]>\n");

            if (message.getThink() != null) {
                sb.append("<think>\n");
                sb.append(message.getThink());
                sb.append("\n</think>\n");
            }
            sb.append(message.getContent());
            return sb.toString();
        }
        return message.getContent();
    }
}