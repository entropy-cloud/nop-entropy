package io.nop.ai.core.command;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.chat.IAiChatService;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.ai.core.commons.processor.IAiChatResponseProcessor;
import io.nop.ai.core.persist.IAiChatResponseCache;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.ai.core.prompt.IPromptTemplateManager;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.retry.RetryHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.eval.IEvalScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;

import static io.nop.ai.core.AiCoreErrors.ERR_AI_RESULT_IS_EMPTY;

public class AiCommand {
    static final Logger LOG = LoggerFactory.getLogger(AiCommand.class);

    private final IAiChatService chatService;
    private IPromptTemplate promptTemplate;
    private IAiChatResponseProcessor chatResponseProcessor;
    private int retryTimesPerRequest = 3;
    private AiChatOptions chatOptions;
    private IAiChatResponseCache chatCache;
    private boolean returnExceptionAsResponse = true;

    public AiCommand(IAiChatService chatService) {
        this.chatService = chatService;
    }

    public static AiCommand create() {
        return new AiCommand(BeanContainer.getBeanByType(IAiChatService.class));
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

    public void setChatResponseCache(IAiChatResponseCache chatCache) {
        this.chatCache = chatCache;
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

    public AiChatOptions makeChatOptions() {
        if (chatOptions == null)
            chatOptions = new AiChatOptions();
        return chatOptions;
    }

    public void setChatOptions(AiChatOptions chatOptions) {
        this.chatOptions = chatOptions;
    }

    public AiCommand chatOptions(AiChatOptions chatOptions) {
        setChatOptions(Guard.notNull(chatOptions, "chatOptions"));
        return this;
    }

    public AiCommand provider(String provider) {
        makeChatOptions().setProvider(provider);
        return this;
    }

    public AiCommand model(String model) {
        makeChatOptions().setModel(model);
        return this;
    }

    public AiCommand temperature(Float temperature) {
        makeChatOptions().setTemperature(temperature);
        return this;
    }

    public AiCommand promptName(String promptName) {
        IPromptTemplateManager promptTemplateManager = BeanContainer.getBeanByType(IPromptTemplateManager.class);
        this.promptTemplate = promptTemplateManager.getPromptTemplate(promptName);
        return this;
    }

    public AiCommand promptPath(String promptPath) {
        IPromptTemplateManager promptTemplateManager = BeanContainer.getBeanByType(IPromptTemplateManager.class);
        this.promptTemplate = promptTemplateManager.loadPromptTemplateFromPath(promptPath);
        return this;
    }

    public AiCommand useResponseCache(boolean useResponseCache) {
        if (useResponseCache) {
            this.chatCache = BeanContainer.getBeanByType(IAiChatResponseCache.class);
        } else {
            this.chatCache = null;
        }
        return this;
    }

    public AiCommand retryTimesPerRequest(int retryTimesPerRequest) {
        this.setRetryTimesPerRequest(retryTimesPerRequest);
        return this;
    }

    public boolean isReturnExceptionAsResponse() {
        return returnExceptionAsResponse;
    }

    public void setReturnExceptionAsResponse(boolean returnExceptionAsResponse) {
        this.returnExceptionAsResponse = returnExceptionAsResponse;
    }

    public AiChatExchange execute(Map<String, Object> vars, ICancelToken cancelToken) {
        return execute(vars, cancelToken, null);
    }

    public CompletionStage<AiChatExchange> executeAsync(Map<String, Object> vars, ICancelToken cancelToken) {
        return executeAsync(vars, cancelToken, null);
    }

    public AiChatExchange execute(Map<String, Object> vars, ICancelToken cancelToken, IEvalContext ctx) {
        return FutureHelper.syncGet(executeAsync(vars, cancelToken, ctx));
    }

    public CompletionStage<AiChatExchange> executeAsync(Map<String, Object> vars, ICancelToken cancelToken, IEvalContext ctx) {
        IEvalScope scope = prepareInputs(vars, ctx);
        Prompt prompt = newPrompt(scope);
        AiChatOptions options = this.chatOptions.cloneInstance();
        promptTemplate.applyChatOptions(options);

        if (chatCache != null) {
            try {
                AiChatExchange exchange = chatCache.loadCachedResponse(prompt, options);
                if (exchange != null)
                    return FutureHelper.success(exchange);
            } catch (Exception e) {
                LOG.info("nop.ai.load-cache-fail:promptName={},requestHash={}", prompt.getName(), prompt.getRequestHash());
            }
        }

        CompletionStage<AiChatExchange> promise = RetryHelper.retryNTimes((index) -> {
                    adjustTemperature(options, index);
                    return executeOnceAsync(prompt, options, scope, cancelToken);
                },
                AiChatExchange::isValid, retryTimesPerRequest);

        if (chatCache != null) {
            return promise.thenApply(res -> {
                if (res.isValid()) {
                    chatCache.saveCachedResponse(res);
                }
                return res;
            });
        }

        return promise;
    }

    protected IEvalScope prepareInputs(Map<String, Object> vars, IEvalContext ctx) {
        return promptTemplate.prepareInputs(vars, ctx);
    }

    protected void adjustTemperature(AiChatOptions options, int index) {
        if (index == 1) {
            options.setTemperature(0f);
        } else if (index > 0) {
            options.setTemperature((float) (0.6f + ((1.2 - 0.6) * index) / retryTimesPerRequest));
        }
    }

    public CompletionStage<AiChatExchange> executeOnceAsync(Prompt prompt, AiChatOptions chatOptions,
                                                            IEvalScope scope, ICancelToken cancelToken) {
        if (cancelToken != null && cancelToken.isCancelled()) {
            LOG.info("nop.ai.cancel-call-ai");
            return FutureHelper.reject(new CancellationException("cancel-call-ai"));
        }

        CompletionStage<AiChatExchange> future = chatService.sendChatAsync(prompt, chatOptions, cancelToken).thenApply(ret -> {
            promptTemplate.processChatResponse(ret, scope);
            return ret;
        });

        if (chatResponseProcessor != null)
            future = future.thenCompose(ret -> chatResponseProcessor.processAsync(ret));

        future = FutureHelper.thenCompleteAsync(future.thenApply(this::postProcess), (r, err) -> {
            if (err != null) {
                if (returnExceptionAsResponse) {
                    AiChatExchange response = new AiChatExchange();
                    response.setPrompt(prompt);
                    response.setInvalid(true);
                    response.setInvalidReason(ErrorMessageManager.instance()
                            .buildErrorMessage(null, err, false, false, true));
                    return response;
                } else {
                    throw NopException.adapt(err);
                }
            } else if (r.isEmpty() && !r.isInvalid()) {
                r.setInvalid(true);
                r.setInvalidReason(new ErrorBean(ERR_AI_RESULT_IS_EMPTY.getErrorCode()));
            }
            return r;
        });
        return future;
    }

    protected AiChatExchange postProcess(AiChatExchange ret) {
        return ret;
    }

    protected Prompt newPrompt(IEvalScope scope) {
        String promptText = promptTemplate.generatePrompt(scope);
        Guard.notEmpty(promptText, "promptText");
        Prompt prompt = Prompt.userText(promptText);
        prompt.setName(promptTemplate.getName());
        return prompt;
    }
}
