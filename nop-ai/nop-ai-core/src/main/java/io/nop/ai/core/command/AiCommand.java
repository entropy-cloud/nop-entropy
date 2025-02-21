package io.nop.ai.core.command;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.chat.IAiChatService;
import io.nop.ai.core.api.messages.AiChatResponse;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.ai.core.commons.processor.IAiChatResponseProcessor;
import io.nop.ai.core.model.PromptVarModel;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.retry.RetryHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;

public class AiCommand {
    static final Logger LOG = LoggerFactory.getLogger(AiCommand.class);

    private final IAiChatService chatService;
    private IPromptTemplate promptTemplate;
    private IAiChatResponseProcessor chatResponseProcessor;
    private int retryTimesPerRequest = 4;
    private AiChatOptions chatOptions = new AiChatOptions();

    private boolean returnExceptionAsResponse;

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

    public IAiChatResponseProcessor getChatResponseProcessor() {
        return chatResponseProcessor;
    }

    public void setChatResponseProcessor(IAiChatResponseProcessor chatResponseProcessor) {
        this.chatResponseProcessor = chatResponseProcessor;
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

    public boolean isReturnExceptionAsResponse() {
        return returnExceptionAsResponse;
    }

    public void setReturnExceptionAsResponse(boolean returnExceptionAsResponse) {
        this.returnExceptionAsResponse = returnExceptionAsResponse;
    }

    public CompletionStage<AiChatResponse> callAiAsync(Map<String, Object> vars, ICancelToken cancelToken) {
        Prompt prompt = newPrompt(vars);

        return RetryHelper.retryNTimes((index) -> {
                    adjustTemperature(prompt, index);
                    return callAiOnceAsync(prompt, cancelToken);
                },
                AiChatResponse::isValid, retryTimesPerRequest);
    }

    protected void adjustTemperature(Prompt prompt, int index) {
        if (index == 1) {
            prompt.setTemperature(0f);
        } else if (index > 0) {
            prompt.setTemperature((float) (0.6f + ((1.2 - 0.6) * index) / retryTimesPerRequest));
        }
    }

    public CompletionStage<AiChatResponse> callAiOnceAsync(Prompt prompt, ICancelToken cancelToken) {
        if (cancelToken != null && cancelToken.isCancelled()) {
            LOG.info("nop.ai.cancel-call-ai");
            return FutureHelper.reject(new CancellationException("cancel-call-ai"));
        }

        CompletionStage<AiChatResponse> future = chatService.sendChatAsync(prompt, chatOptions, cancelToken).thenApply(ret -> {
            ret.setPrompt(prompt);
            promptTemplate.processChatResponse(ret);
            return ret;
        });

        if (chatResponseProcessor != null)
            future = future.thenCompose(ret -> chatResponseProcessor.processAsync(ret));

        future = FutureHelper.thenCompleteAsync(future.thenApply(this::postProcess), (r, err) -> {
            if (err != null) {
                if (returnExceptionAsResponse) {
                    AiChatResponse response = new AiChatResponse();
                    response.setPrompt(prompt);
                    response.setInvalid(true);
                    response.setContent("<AI-ERROR>:" + ErrorMessageManager.instance()
                            .buildErrorMessage(null, err, false, false, true).getDescription());
                    return response;
                } else {
                    throw NopException.adapt(err);
                }
            }
            return r;
        });
        return future;
    }

    protected AiChatResponse postProcess(AiChatResponse ret) {
        return ret;
    }

    protected Prompt newPrompt(Map<String, Object> vars) {
        checkPromptVars(vars);

        String promptText = promptTemplate.generatePrompt(vars);
        Guard.notEmpty(promptText, "promptText");
        return Prompt.userText(promptText);
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
