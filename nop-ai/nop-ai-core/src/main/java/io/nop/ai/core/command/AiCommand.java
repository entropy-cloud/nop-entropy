package io.nop.ai.core.command;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.chat.IAiChatLogger;
import io.nop.ai.core.api.chat.IAiChatService;
import io.nop.ai.core.api.messages.AiAssistantMessage;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.messages.AiMessage;
import io.nop.ai.core.api.messages.AiMessageAttachment;
import io.nop.ai.core.api.messages.AiUserMessage;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.ai.core.api.messages.ToolCall;
import io.nop.ai.core.api.messages.AiToolResponseMessage;
import io.nop.ai.core.api.tool.IAiChatFunctionTool;
import io.nop.ai.core.api.tool.IAiChatToolSet;
import io.nop.ai.core.api.tool.ToolSpecification;
import io.nop.ai.core.commons.processor.IAiChatResponseProcessor;
import io.nop.ai.core.persist.IAiChatResponseCache;
import io.nop.ai.core.prompt.DefaultSystemPromptLoader;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.ai.core.prompt.IPromptTemplateManager;
import io.nop.ai.core.prompt.SimplePromptTemplate;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.retry.RetryHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;

import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_ENABLE_WORK_MODE_SYSTEM_PROMPT;
import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_LOG_MESSAGE;
import static io.nop.ai.core.AiCoreErrors.ARG_TOOL_NAME;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_RESULT_IS_EMPTY;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_UNKNOWN_TOOL_CALL;

public class AiCommand {
    static final Logger LOG = LoggerFactory.getLogger(AiCommand.class);

    private final IAiChatService chatService;
    private final IPromptTemplateManager promptTemplateManager;

    private IPromptTemplate systemPromptTemplate;
    private IPromptTemplate promptTemplate;
    private List<AiMessage> prevMessages;
    private IAiChatResponseProcessor chatResponseProcessor;
    private int retryTimesPerRequest = 3;
    private AiChatOptions chatOptions;
    private IAiChatResponseCache chatCache;
    private boolean returnExceptionAsResponse = true;
    private IAiChatLogger chatLogger;

    private int maxSteps;
    private List<AiMessageAttachment> attachments;

    private IAiChatToolSet toolSet;

    public AiCommand(IAiChatService chatService, IPromptTemplateManager promptTemplateManager) {
        this.chatService = chatService;
        this.promptTemplateManager = promptTemplateManager;
    }

    public static AiCommand create() {
        return new AiCommand(
                BeanContainer.getBeanByType(IAiChatService.class),
                BeanContainer.getBeanByType(IPromptTemplateManager.class));
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public AiCommand maxSteps(int maxSteps) {
        setMaxSteps(maxSteps);
        return this;
    }

    public IAiChatToolSet getToolSet() {
        return toolSet;
    }

    public void setToolSet(IAiChatToolSet toolSet) {
        this.toolSet = toolSet;
    }

    public AiCommand toolSet(IAiChatToolSet toolProvider) {
        this.toolSet = toolProvider;
        return this;
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

    public List<AiMessage> getPrevMessages() {
        return prevMessages;
    }

    public void setPrevMessages(List<AiMessage> prevMessages) {
        this.prevMessages = prevMessages;
    }

    public Set<String> getEnabledTools() {
        return chatOptions == null ? null : chatOptions.getEnabledTools();
    }

    public void setEnabledTools(Set<String> tools) {
        this.makeChatOptions().setEnabledTools(tools);
    }

    public AiCommand enableTools(Set<String> tools) {
        this.makeChatOptions().addEnabledTools(tools);
        return this;
    }

    public AiCommand enableTool(String toolName) {
        this.makeChatOptions().addEnabledTool(toolName);
        return this;
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

    public AiCommand systemPromptTemplate(IPromptTemplate promptTemplate) {
        this.systemPromptTemplate = promptTemplate;
        return this;
    }

    public AiCommand systemPromptName(String systemPromptName) {
        return systemPromptTemplate(promptTemplateManager.getPromptTemplate(systemPromptName));
    }

    public AiCommand systemPromptPath(String systemPromptPath) {
        return systemPromptTemplate(promptTemplateManager.loadPromptTemplateFromPath(systemPromptPath));
    }

    public AiCommand workMode(String workMode) {
        makeChatOptions().setWorkMode(workMode);
        return this;
    }

    public AiCommand promptTemplate(IPromptTemplate promptTemplate) {
        this.promptTemplate = promptTemplate;
        return this;
    }

    public AiCommand prompt(String promptText) {
        return promptTemplate(SimplePromptTemplate.simplePrompt("simple-" + StringHelper.md5Hash(promptText), promptText));
    }

    public AiCommand promptName(String promptName) {
        return promptTemplate(promptTemplateManager.getPromptTemplate(promptName));
    }

    public AiCommand promptPath(String promptPath) {
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

    public AiCommand attachments(List<AiMessageAttachment> attachments) {
        this.attachments = attachments;
        return this;
    }

    public List<AiMessageAttachment> getAttachments() {
        return attachments;
    }

    public AiCommand chatLogger(IAiChatLogger chatLogger) {
        this.chatLogger = chatLogger;
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

    public IAiChatLogger getChatLogger() {
        if (chatLogger == null)
            chatLogger = BeanContainer.getBeanByType(IAiChatLogger.class);
        return chatLogger;
    }

    protected void logCachedResponse(AiChatExchange exchange) {
        boolean logMessage = CFG_AI_SERVICE_LOG_MESSAGE.get();
        if (logMessage) {
            getChatLogger().logChatExchange(exchange);
        }
    }

    public CompletionStage<AiChatExchange> executeAsync(Map<String, Object> vars, ICancelToken cancelToken, IEvalContext ctx) {
        IEvalScope scope = prepareInputs(vars, ctx);
        Prompt prompt = newPrompt(scope);
        AiChatOptions options = this.chatOptions.cloneInstance();
        promptTemplate.applyChatOptions(options);

        if (chatCache != null && !Boolean.TRUE.equals(options.getDisableCache())) {
            try {
                AiChatExchange exchange = chatCache.loadCachedResponse(prompt, options);
                if (exchange != null) {
                    logCachedResponse(exchange);
                    promptTemplate.processChatResponse(exchange, scope);
                    CompletionStage<AiChatExchange> future = FutureHelper.success(exchange);
                    if (chatResponseProcessor != null)
                        future = future.thenCompose(ret -> chatResponseProcessor.processAsync(ret));

                    return future.thenApply(this::postProcess);
                }
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
        CompletionStage<AiChatExchange> future = executeWithTools(0, prompt, chatOptions, scope, cancelToken);
        if (FutureHelper.isError(future))
            return future;

        future = future.thenApply(ret -> {
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

    protected CompletionStage<AiChatExchange> executeWithTools(int stepIndex, Prompt prompt, AiChatOptions options,
                                                               IEvalScope scope, ICancelToken cancelToken) {
        if (cancelToken != null && cancelToken.isCancelled()) {
            LOG.info("nop.ai.cancel-call-ai");
            return FutureHelper.reject(new CancellationException("cancel-call-ai"));
        }

        CompletionStage<AiChatExchange> future = chatService.sendChatAsync(prompt, chatOptions, cancelToken);
        return future.thenCompose(exchange -> {
            int maxSteps = getMaxSteps();
            if (maxSteps == 0)
                maxSteps = 3;
            if (stepIndex >= maxSteps)
                return FutureHelper.success(exchange);

            AiAssistantMessage result = exchange.getResponse();
            if (result.getToolCalls() == null || result.getToolCalls().isEmpty()) {
                return FutureHelper.success(exchange);
            }

            prompt.addMessage(exchange.getResponse());
            prompt.setRequestHash(null);

            return executeTools(stepIndex + 1, result.getToolCalls(), prompt, options, scope, cancelToken);
        });
    }

    protected CompletionStage<AiChatExchange> executeTools(int stepIndex, List<ToolCall> toolCalls,
                                                           Prompt prompt, AiChatOptions options,
                                                           IEvalScope scope, ICancelToken cancelToken) {
        List<CompletionStage<Object>> toolResults = new ArrayList<>(toolCalls.size());
        for (ToolCall toolCall : toolCalls) {
            IAiChatFunctionTool toolSpec = toolSet.getFunctionTool(toolCall.getName());
            if (toolSpec == null)
                throw new NopException(ERR_AI_UNKNOWN_TOOL_CALL).param(ARG_TOOL_NAME, toolCall.getName());

            toolResults.add(toolSpec.callToolAsync(toolCall.getArguments()));
        }

        return FutureHelper.waitAll(toolResults).thenCompose(ret -> {
            for (int i = 0; i < toolResults.size(); i++) {
                ToolCall toolCall = toolCalls.get(i);
                Object result = FutureHelper.syncGet(toolResults.get(i));
                AiToolResponseMessage message = new AiToolResponseMessage();
                message.setName(toolCall.getName());
                message.setToolCallId(toolCall.getId());
                message.setContent(toString(result));
                prompt.getMessages().add(message);
            }
            return executeWithTools(stepIndex, prompt, options, scope, cancelToken);
        });
    }

    private String toString(Object result) {
        if (result == null)
            return "";
        if (result instanceof String)
            return result.toString();
        return JsonTool.stringify(result);
    }

    protected AiChatExchange postProcess(AiChatExchange ret) {
        return ret;
    }

    protected Prompt newPrompt(IEvalScope scope) {
        String promptText = promptTemplate.generatePrompt(scope);
        Guard.notEmpty(promptText, "promptText");
        Prompt prompt = new Prompt();
        if (prevMessages != null) {
            prompt.addMessages(prevMessages);
        } else {
            addSystemPrompt(prompt, scope);
        }

        AiUserMessage message = prompt.addUserMessage(promptText);
        message.setAttachments(attachments);
        prompt.setName(promptTemplate.getName());

        addTools(prompt);
        return prompt;
    }

    protected void addTools(Prompt prompt) {
        if (getToolSet() == null)
            return;

        if (this.getEnabledTools() != null && !this.getEnabledTools().isEmpty()) {
            List<ToolSpecification> tools = new ArrayList<>();
            for (String toolName : this.getEnabledTools()) {
                IAiChatFunctionTool tool = getToolSet().getFunctionTool(toolName);
                if (tool == null) {
                    LOG.error("nop.ai.unknown-tool:toolName={}", toolName);
                    continue;
                }
                tools.add(tool.toSpec());
            }
            if (!tools.isEmpty()) {
                prompt.setTools(tools);
            }
        }
    }

    protected void addSystemPrompt(Prompt prompt, IEvalScope scope) {
        if (systemPromptTemplate != null) {
            String systemPrompt = systemPromptTemplate.generatePrompt(scope);
            if (!StringHelper.isEmpty(systemPrompt))
                prompt.addSystemMessage(systemPrompt);
        } else {
            if (!CFG_AI_SERVICE_ENABLE_WORK_MODE_SYSTEM_PROMPT.get())
                return;

            if (StringHelper.isEmpty(chatOptions.getWorkMode()))
                return;

            String systemPrompt = DefaultSystemPromptLoader.instance().loadSystemPrompt(chatOptions);
            if (!StringHelper.isEmpty(systemPrompt))
                prompt.addSystemMessage(systemPrompt);
        }
    }
}