package io.nop.ai.core.commons;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.chat.IAiChatService;
import io.nop.ai.core.api.messages.AiResultMessage;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.ai.core.commons.processor.IAiResultMessageProcessor;
import io.nop.ai.core.model.PromptVarModel;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.retry.RetryHelper;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;

public class AiCommand {
    private final IAiChatService chatService;
    private IPromptTemplate promptTemplate;
    private IAiResultMessageProcessor resultMessageProcessor;
    private int retryTimesPerRequest = 2;
    private AiChatOptions chatOptions = new AiChatOptions();

    public AiCommand(IAiChatService chatService) {
        this.chatService = chatService;
    }

    public IAiChatService getChatService() {
        return chatService;
    }

    public IPromptTemplate getPromptTemplate() {
        return promptTemplate;
    }

    public void setPromptTemplate(IPromptTemplate promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    public IAiResultMessageProcessor getResultMessageProcessor() {
        return resultMessageProcessor;
    }

    public void setResultMessageProcessor(IAiResultMessageProcessor resultMessageProcessor) {
        this.resultMessageProcessor = resultMessageProcessor;
    }

    public int getRetryTimesPerRequest() {
        return retryTimesPerRequest;
    }

    public void setRetryTimesPerRequest(int retryTimesPerRequest) {
        this.retryTimesPerRequest = retryTimesPerRequest;
    }

    public AiChatOptions getChatOptions() {
        return chatOptions;
    }

    public void setChatOptions(AiChatOptions chatOptions) {
        this.chatOptions = chatOptions;
    }

    public CompletionStage<AiResultMessage> callAiAsync(Map<String, Object> vars, ICancelToken cancelToken) {
        if (cancelToken != null && cancelToken.isCancelled())
            return FutureHelper.reject(new CancellationException("cancel-call-ai"));

        Prompt prompt = newPrompt(vars);

        return RetryHelper.retryNTimes(() -> callAiOnceAsync(prompt, cancelToken),
                AiResultMessage::isValid, retryTimesPerRequest);
    }

    public CompletionStage<AiResultMessage> callAiOnceAsync(Prompt prompt, ICancelToken cancelToken) {
        CompletionStage<AiResultMessage> future = chatService.sendChatAsync(prompt, chatOptions, cancelToken).thenApply(ret -> {
            ret.setPrompt(prompt);
            promptTemplate.processResultMessage(ret);
            return ret;
        });

        if (resultMessageProcessor != null)
            future = future.thenCompose(ret -> resultMessageProcessor.processAsync(ret));

        return future.thenApply(this::postProcess);
    }

    protected AiResultMessage postProcess(AiResultMessage ret) {
        return ret;
    }

    protected Prompt newPrompt(Map<String, Object> vars) {
        checkPromptVars(vars);

        String promptText = promptTemplate.generatePrompt(vars);
        Guard.notEmpty(promptText, "promptText");
        return Prompt.humanText(promptText);
    }

    protected void checkPromptVars(Map<String, Object> vars) {
        if (promptTemplate.getVars() != null) {
            for (PromptVarModel varModel : promptTemplate.getVars()) {
                if (!vars.containsKey(varModel.getName())) {
                    if (varModel.isOptional())
                        vars.put(varModel.getName(), null);
                }
            }
        }
    }
}
