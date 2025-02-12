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
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.IoHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.ai.translate.AiTranslateConstants.VAR_EXTRA_PROMPT;
import static io.nop.ai.translate.AiTranslateConstants.VAR_FROM_LANG;
import static io.nop.ai.translate.AiTranslateConstants.VAR_MODEL;
import static io.nop.ai.translate.AiTranslateConstants.VAR_PROLOG;
import static io.nop.ai.translate.AiTranslateConstants.VAR_TEXT;
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

    public CompletionStage<String> translateAsync(String text, ICancelToken cancelToken) {
        if (textSplitter != null && text.length() > maxChunkSize) {
            List<ITextSplitter.SplitChunk> chunks = textSplitter.split(text, prologSize, maxChunkSize);

            List<CompletionStage<?>> promises = new ArrayList<>(chunks.size());
            for (ITextSplitter.SplitChunk chunk : chunks) {
                promises.add(doTranslateAsync(chunk.getProlog(), chunk.getContent(), cancelToken));
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
            return doTranslateAsync(null, text, cancelToken);
        }
    }

    protected CompletionStage<String> doTranslateAsync(String prolog, String text, ICancelToken cancelToken) {
        ChatOptions options = new ChatOptions();
        IChatSession session = factory.newSession(options);
        try {
            String promptText = promptTemplate.generatePrompt(
                    Map.of(VAR_MODEL, factory.getModel(), VAR_PROLOG, prolog, VAR_TEXT, text,
                            VAR_FROM_LANG, fromLang, VAR_TO_LANG, toLang, VAR_EXTRA_PROMPT, extraPrompt));
            Prompt prompt = session.newPrompt(false);
            prompt.addHumanMessage(promptText);
            return session.sendChatAsync(prompt, cancelToken).thenApply(this::getTranslatedText);
        } finally {
            IoHelper.safeCloseObject(session);
        }
    }

    protected String getTranslatedText(AiResultMessage message) {
        return message.getContent();
    }
}