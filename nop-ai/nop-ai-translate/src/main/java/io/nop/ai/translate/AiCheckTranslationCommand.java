package io.nop.ai.translate;

import io.nop.ai.core.api.chat.IAiChatService;
import io.nop.ai.core.api.messages.AiChatResponse;
import io.nop.ai.core.command.AiCommand;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.ai.core.prompt.IPromptTemplateManager;
import io.nop.api.core.util.ICancelToken;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.ai.translate.AiTranslateConstants.VAR_CONTENT;
import static io.nop.ai.translate.AiTranslateConstants.VAR_FROM_LANG;
import static io.nop.ai.translate.AiTranslateConstants.VAR_TO_LANG;
import static io.nop.ai.translate.AiTranslateConstants.VAR_TRANSLATED_TEXT;

public class AiCheckTranslationCommand extends AiCommand {
    private String fromLang;
    private String toLang;

    public AiCheckTranslationCommand(IAiChatService chatService, IPromptTemplateManager promptTemplateManager, String promptName) {
        super(chatService);
        setPromptTemplate(promptTemplateManager.getPromptTemplate(promptName));
    }

    public AiCheckTranslationCommand(IAiChatService chatService, IPromptTemplate promptTemplate) {
        super(chatService);
        setPromptTemplate(promptTemplate);
    }

    public String getFromLang() {
        return fromLang;
    }

    public void setFromLang(String fromLang) {
        this.fromLang = fromLang;
    }

    public String getToLang() {
        return toLang;
    }

    public void setToLang(String toLang) {
        this.toLang = toLang;
    }

    public AiCheckTranslationCommand fromLang(String fromLang) {
        this.fromLang = fromLang;
        return this;
    }

    public AiCheckTranslationCommand toLang(String toLang) {
        this.toLang = toLang;
        return this;
    }

    public CompletionStage<AiChatResponse> fixTranslationAsync(String sourceText, String translatedText, ICancelToken cancelToken) {
        return executeAsync(sourceText, translatedText, cancelToken);
    }

    public CompletionStage<AiChatResponse> executeAsync(String sourceText, String translatedText, ICancelToken cancelToken) {
        Map<String, Object> vars = Map.of(VAR_CONTENT, sourceText,
                VAR_TO_LANG, toLang, VAR_FROM_LANG, fromLang,
                VAR_TRANSLATED_TEXT, translatedText == null ? "" : translatedText);
        return callAiAsync(vars, cancelToken);
    }
}
