package io.nop.ai.core.command;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatLogger;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.ResponseFormat;
import io.nop.ai.api.chat.messages.ChatAttachment;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.persist.IAiChatResponseCache;
import io.nop.ai.core.prompt.ChatExchange;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;

import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_ENABLE_WORK_MODE_SYSTEM_PROMPT;
import static io.nop.ai.core.NopAiCoreErrors.ARG_TOOL_NAME;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_RESULT_IS_EMPTY;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_UNKNOWN_TOOL_CALL;

/**
 * Executes a prompt template against the {@code IChatService} stack: renders the
 * prompt, applies chat options, calls the LLM (with an optional tool invocation
 * loop), parses declared outputs into a {@link ChatExchange} and persists
 * responses through the response cache. This is the runtime behind the
 * {@code ai:Execute} xlib tag (task XML {@code ai:TaskStep} steps).
 */
public class AiCommand {
    static final Logger LOG = LoggerFactory.getLogger(AiCommand.class);

    private final IChatService chatService;
    private final IPromptTemplateManager promptTemplateManager;

    private IPromptTemplate systemPromptTemplate;
    private IPromptTemplate promptTemplate;
    private List<ChatMessage> prevMessages;
    private int retryTimesPerRequest = 3;
    private ChatOptions chatOptions;
    private IAiChatResponseCache chatCache;
    private boolean returnExceptionAsResponse = true;
    private IChatLogger chatLogger;

    private int maxSteps;
    private List<ChatAttachment> attachments;
    private String workMode;
    private Set<String> enabledTools;
    private IAiToolInvoker toolInvoker;

    public AiCommand(IChatService chatService, IPromptTemplateManager promptTemplateManager) {
        this.chatService = chatService;
        this.promptTemplateManager = promptTemplateManager;
    }

    public static AiCommand create() {
        return new AiCommand(
                BeanContainer.getBeanByType(IChatService.class),
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

    public IAiToolInvoker getToolInvoker() {
        return toolInvoker;
    }

    public void setToolInvoker(IAiToolInvoker toolInvoker) {
        this.toolInvoker = toolInvoker;
    }

    public AiCommand toolInvoker(IAiToolInvoker toolInvoker) {
        this.toolInvoker = toolInvoker;
        return this;
    }

    public IChatService getChatService() {
        return chatService;
    }

    public IPromptTemplate getPromptTemplate() {
        return promptTemplate;
    }

    public void setPromptTemplate(IPromptTemplate promptTemplate) {
        this.promptTemplate = promptTemplate;
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

    public List<ChatMessage> getPrevMessages() {
        return prevMessages;
    }

    public void setPrevMessages(List<ChatMessage> prevMessages) {
        this.prevMessages = prevMessages;
    }

    public Set<String> getEnabledTools() {
        return enabledTools;
    }

    public void setEnabledTools(Set<String> tools) {
        this.enabledTools = tools;
    }

    public AiCommand enableTools(Set<String> tools) {
        if (this.enabledTools == null)
            this.enabledTools = new java.util.LinkedHashSet<>();
        this.enabledTools.addAll(tools);
        return this;
    }

    public AiCommand enableTool(String toolName) {
        if (this.enabledTools == null)
            this.enabledTools = new java.util.LinkedHashSet<>();
        this.enabledTools.add(toolName);
        return this;
    }

    public ChatOptions getChatOptions() {
        return chatOptions;
    }

    public ChatOptions makeChatOptions() {
        if (chatOptions == null)
            chatOptions = new ChatOptions();
        return chatOptions;
    }

    public void setChatOptions(ChatOptions chatOptions) {
        this.chatOptions = chatOptions;
    }

    /**
     * Accepts a {@link ChatOptions} or a script-supplied {@link Map} of option
     * keys. Only keys supported by {@link ChatOptions} are mapped; unknown
     * legacy keys (seed/contextLength/workMode/streamListener/...) are ignored
     * with a debug log — they have no counterpart on the new stack.
     */
    public AiCommand chatOptions(Object options) {
        if (options == null)
            return this;
        if (options instanceof ChatOptions) {
            setChatOptions(Guard.notNull((ChatOptions) options, "chatOptions"));
            return this;
        }
        Map<?, ?> map = (Map<?, ?>) options;
        ChatOptions opts = makeChatOptions();
        assign(map, "provider", opts::setProvider);
        assign(map, "model", opts::setModel);
        assign(map, "sessionId", opts::setSessionId);
        assign(map, "temperature", opts::setTemperature);
        assign(map, "topP", opts::setTopP);
        assign(map, "topK", opts::setTopK);
        assign(map, "maxTokens", opts::setMaxTokens);
        assign(map, "contextLength", opts::setMaxContextTokens);
        assign(map, "frequencyPenalty", opts::setFrequencyPenalty);
        assign(map, "presencePenalty", opts::setPresencePenalty);
        assign(map, "stop", opts::setStop);
        assign(map, "requestTimeout", opts::setRequestTimeout);
        assign(map, "stream", opts::setStream);
        assign(map, "enableThinking", opts::setEnableThinking);
        assign(map, "toolChoice", opts::setToolChoice);
        Object responseFormat = map.get("responseFormat");
        if (responseFormat != null) {
            if (ResponseFormat.TYPE_JSON_OBJECT.equals(responseFormat)) {
                opts.setResponseFormatConfig(ResponseFormat.jsonObject());
            } else {
                LOG.debug("nop.ai.unsupported-chat-option:key=responseFormat,value={}", responseFormat);
            }
        }
        for (Object key : new Object[]{"seed", "workMode", "streamListener", "disableCache", "enabledTools"}) {
            if (map.get(key) != null)
                LOG.debug("nop.ai.unsupported-chat-option:key={}", key);
        }
        return this;
    }

    private <T> void assign(Map<?, ?> map, String key, java.util.function.Consumer<T> setter) {
        Object value = map.get(key);
        if (value != null)
            setter.accept((T) value);
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
        this.workMode = workMode;
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

    public AiCommand attachments(List<ChatAttachment> attachments) {
        this.attachments = attachments;
        return this;
    }

    public List<ChatAttachment> getAttachments() {
        return attachments;
    }

    public AiCommand chatLogger(IChatLogger chatLogger) {
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

    public ChatExchange execute(Map<String, Object> vars, ICancelToken cancelToken) {
        return execute(vars, cancelToken, null);
    }

    public CompletionStage<ChatExchange> executeAsync(Map<String, Object> vars, ICancelToken cancelToken) {
        return executeAsync(vars, cancelToken, null);
    }

    public ChatExchange execute(Map<String, Object> vars, ICancelToken cancelToken, IEvalContext ctx) {
        return FutureHelper.syncGet(executeAsync(vars, cancelToken, ctx));
    }

    public IChatLogger getChatLogger() {
        if (chatLogger == null)
            chatLogger = BeanContainer.getBeanByType(IChatLogger.class);
        return chatLogger;
    }

    public CompletionStage<ChatExchange> executeAsync(Map<String, Object> vars, ICancelToken cancelToken, IEvalContext ctx) {
        IEvalScope scope = prepareInputs(vars, ctx);
        ChatRequest request = newRequest(scope);
        ChatOptions options = this.chatOptions == null ? new ChatOptions() : this.chatOptions.copy();
        promptTemplate.applyChatOptions(options);
        request.setOptions(options);
        String promptName = promptTemplate.getName();

        if (chatCache != null) {
            try {
                ChatExchange cached = chatCache.loadCachedResponse(request, options, promptName);
                if (cached != null) {
                    promptTemplate.processChatResponse(cached, scope);
                    return FutureHelper.<ChatExchange>success(cached).thenApply(this::postProcess);
                }
            } catch (Exception e) {
                LOG.info("nop.ai.load-cache-fail:promptName={}", promptName);
            }
        }

        CompletionStage<ChatExchange> promise = RetryHelper.retryNTimes((index) -> {
                    adjustTemperature(options, index);
                    return executeOnceAsync(request.copy(), options, scope, cancelToken);
                },
                ChatExchange::isValid, retryTimesPerRequest);

        if (chatCache != null) {
            return promise.thenApply(res -> {
                if (res.isValid()) {
                    chatCache.saveCachedResponse(res, promptName);
                }
                return res;
            });
        }

        return promise;
    }

    protected IEvalScope prepareInputs(Map<String, Object> vars, IEvalContext ctx) {
        return promptTemplate.prepareInputs(vars, ctx);
    }

    protected void adjustTemperature(ChatOptions options, int index) {
        if (index == 1) {
            options.setTemperature(0f);
        } else if (index > 0) {
            options.setTemperature((float) (0.6f + ((1.2 - 0.6) * index) / retryTimesPerRequest));
        }
    }

    public CompletionStage<ChatExchange> executeOnceAsync(ChatRequest request, ChatOptions chatOptions,
                                                          IEvalScope scope, ICancelToken cancelToken) {
        CompletionStage<ChatExchange> future = executeWithTools(0, request, scope, cancelToken);
        if (FutureHelper.isError(future))
            return future;

        future = future.thenApply(ret -> {
            promptTemplate.processChatResponse(ret, scope);
            return ret;
        });

        future = FutureHelper.thenCompleteAsync(future.thenApply(this::postProcess), (r, err) -> {
            if (err != null) {
                if (returnExceptionAsResponse) {
                    ChatExchange response = new ChatExchange(request, null);
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

    protected CompletionStage<ChatExchange> executeWithTools(int stepIndex, ChatRequest request,
                                                             IEvalScope scope, ICancelToken cancelToken) {
        if (cancelToken != null && cancelToken.isCancelled()) {
            LOG.info("nop.ai.cancel-call-ai");
            return FutureHelper.reject(new CancellationException("cancel-call-ai"));
        }

        CompletionStage<ChatResponse> future = chatService.callAsync(request, cancelToken);
        return future.thenCompose(response -> {
            int maxSteps = getMaxSteps();
            if (maxSteps == 0)
                maxSteps = 3;
            if (stepIndex >= maxSteps)
                return FutureHelper.success(new ChatExchange(request, response));

            List<ChatToolCall> toolCalls = response.outputToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                return FutureHelper.success(new ChatExchange(request, response));
            }

            return executeTools(stepIndex + 1, toolCalls, request, scope, cancelToken);
        });
    }

    protected CompletionStage<ChatExchange> executeTools(int stepIndex, List<ChatToolCall> toolCalls,
                                                         ChatRequest request, IEvalScope scope, ICancelToken cancelToken) {
        List<CompletionStage<Object>> toolResults = new ArrayList<>(toolCalls.size());
        for (ChatToolCall toolCall : toolCalls) {
            if (toolInvoker == null || toolInvoker.findTool(toolCall.getName()) == null)
                throw new NopException(ERR_AI_UNKNOWN_TOOL_CALL).param(ARG_TOOL_NAME, toolCall.getName());

            toolResults.add(toolInvoker.callTool(toolCall.getName(), toolCall.getArguments()));
        }

        return FutureHelper.waitAll(toolResults).thenCompose(ret -> {
            for (int i = 0; i < toolResults.size(); i++) {
                ChatToolCall toolCall = toolCalls.get(i);
                Object result = FutureHelper.syncGet(toolResults.get(i));
                request.addToolResponse(toolCall.getId(), toolCall.getName(), toString(result));
            }
            return executeWithTools(stepIndex, request, scope, cancelToken);
        });
    }

    private String toString(Object result) {
        if (result == null)
            return "";
        if (result instanceof String)
            return result.toString();
        return io.nop.core.lang.json.JsonTool.stringify(result);
    }

    protected ChatExchange postProcess(ChatExchange ret) {
        return ret;
    }

    protected ChatRequest newRequest(IEvalScope scope) {
        String promptText = promptTemplate.generatePrompt(scope);
        Guard.notEmpty(promptText, "promptText");
        ChatRequest request = new ChatRequest();
        if (prevMessages != null) {
            request.setMessages(new ArrayList<>(prevMessages));
        } else {
            addSystemPrompt(request, scope);
        }

        ChatUserMessage message = new ChatUserMessage(promptText);
        if (attachments != null && !attachments.isEmpty())
            message.setAttachments(new ArrayList<>(attachments));
        request.addMessage(message);

        addTools(request);
        return request;
    }

    protected void addTools(ChatRequest request) {
        if (getToolInvoker() == null)
            return;

        if (this.getEnabledTools() != null && !this.getEnabledTools().isEmpty()) {
            List<ChatToolDefinition> tools = new ArrayList<>();
            for (String toolName : this.getEnabledTools()) {
                ChatToolDefinition tool = getToolInvoker().findTool(toolName);
                if (tool == null) {
                    LOG.error("nop.ai.unknown-tool:toolName={}", toolName);
                    continue;
                }
                tools.add(tool);
            }
            if (!tools.isEmpty()) {
                request.setTools(tools);
            }
        }
    }

    protected void addSystemPrompt(ChatRequest request, IEvalScope scope) {
        String systemPrompt = null;
        if (systemPromptTemplate != null) {
            systemPrompt = systemPromptTemplate.generatePrompt(scope);
        } else if (CFG_AI_SERVICE_ENABLE_WORK_MODE_SYSTEM_PROMPT.get() && !StringHelper.isEmpty(workMode)) {
            systemPrompt = DefaultSystemPromptLoader.instance().loadSystemPrompt(workMode, null, null, null);
        }
        if (!StringHelper.isEmpty(systemPrompt))
            request.setSystemPrompt(systemPrompt);
    }
}
