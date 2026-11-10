package io.nop.ai.core.command;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatLogger;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.ResponseFormat;
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.api.chat.messages.ChatAttachment;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.persist.IAiChatResponseCache;
import io.nop.ai.core.reliability.IRetryPolicy;
import io.nop.ai.core.reliability.LlmErrorClassifier;
import io.nop.ai.core.reliability.RetryContext;
import io.nop.ai.core.reliability.RetryOutcome;
import io.nop.ai.core.reliability.StandardRetryPolicy;
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
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.eval.IEvalScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_ENABLE_WORK_MODE_SYSTEM_PROMPT;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_RESULT_IS_EMPTY;

/**
 * Executes a prompt template against the {@code IChatService} stack: renders the
 * prompt, applies chat options, calls the LLM, parses declared outputs into a
 * {@link ChatExchange} and persists responses through the response cache. This
 * is the runtime behind the {@code ai:Execute} xlib tag (task XML
 * {@code ai:TaskStep} steps).
 *
 * <p>Tool invocation is not part of this command path: the legacy tool loop and
 * its transitional invocation seam were retired by plan 2419 (roadmap WI2) —
 * the agent engine's toolkit runtime is the single tool execution path.
 *
 * <p><b>Async envelope + exception-aware retry (3.1-C3-005/C3-006/C7-006,
 * plan 2556 裁定 A/B/H)</b>: every prologue failure of {@link #callChat}
 * (config load / credential check / local rate-limit {@code ERR_AI_RATE_LIMITED}
 * thrown synchronously by {@code ChatServiceImpl.checkRateLimit} before
 * {@code callAsync} returns) is normalized into a failed future via
 * {@link FutureHelper#reject} — the caller sees a single exception channel.
 * The retry loop is exception-aware (the old {@code RetryHelper.retryNTimes}
 * result-chain silently dropped exceptionally-completed attempts): each
 * attempt's failure is classified with {@link LlmErrorClassifier#classify}
 * and retryable classifications (TRANSIENT / RATE_LIMITED) are retried after
 * a {@link StandardRetryPolicy} full-jitter delay scheduled as a lightweight
 * non-blocking hop on the global timer. The retry unit is the whole
 * {@code callChat} invocation, so each attempt re-enters
 * {@code chatService.callAsync} and the rate limiter is naturally rechecked.
 * Response post-processing ({@code processChatResponse} / {@code postProcess} /
 * invalid marking) runs <b>once after the loop</b>; {@link #adjustTemperature}
 * stays <b>per-attempt</b> (pinned contract).
 *
 * <p><b>Circuit-breaker generation capture (3.1-C7-005, plan 2556 裁定 G)</b>:
 * when the caller wraps this command with a breaker, the generation token to
 * hand back to {@code recordSuccess}/{@code recordFailure} is the
 * {@code currentGeneration} observed on the <b>FINAL routed model key</b> at
 * its {@code allowCall} — the token returned by the allowCall that actually
 * admitted this call.
 */
public class AiCommand {
    static final Logger LOG = LoggerFactory.getLogger(AiCommand.class);

    private final IChatService chatService;
    private final IPromptTemplateManager promptTemplateManager;

    private IPromptTemplate systemPromptTemplate;
    private IPromptTemplate promptTemplate;
    private List<ChatMessage> prevMessages;
    private int retryTimesPerRequest = 3;
    private IRetryPolicy retryPolicy;
    private ChatOptions chatOptions;
    private IAiChatResponseCache chatCache;
    private boolean returnExceptionAsResponse = true;
    private IChatLogger chatLogger;

    private List<ChatAttachment> attachments;
    private String workMode;

    public AiCommand(IChatService chatService, IPromptTemplateManager promptTemplateManager) {
        this.chatService = chatService;
        this.promptTemplateManager = promptTemplateManager;
    }

    public static AiCommand create() {
        return new AiCommand(
                BeanContainer.getBeanByType(IChatService.class),
                BeanContainer.getBeanByType(IPromptTemplateManager.class));
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

    /**
     * Retry decision + backoff policy for the exception-aware retry loop
     * (3.1-C7-006, plan 2556 裁定 H). Defaults to a {@link StandardRetryPolicy}
     * whose maxAttempts tracks {@code retryTimesPerRequest + 1}. Visible for
     * testing (carrier {@code TestAiCommandRetryBackoff} injects a
     * deterministic-delay policy); the setter resets the lazy default.
     */
    public void setRetryPolicy(IRetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    public List<ChatMessage> getPrevMessages() {
        return prevMessages;
    }

    public void setPrevMessages(List<ChatMessage> prevMessages) {
        this.prevMessages = prevMessages;
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
                // 3.1-C3-006 (plan 2556 裁定 B): cache-load failure degrades to a
                // cache miss — log at WARN with the throwable (was INFO, dropped).
                LOG.warn("nop.ai.load-cache-fail:promptName={}", promptName, e);
            }
        }

        CompletionStage<ChatExchange> promise = executeWithRetry(request, options, scope, cancelToken);

        if (chatCache != null) {
            return promise.thenApply(res -> {
                if (res.isValid()) {
                    try {
                        chatCache.saveCachedResponse(res, promptName);
                    } catch (Exception e) {
                        // 3.1-C3-006 (plan 2556 裁定 B): a cache-write failure must
                        // never turn an already-successful response exceptional.
                        LOG.warn("nop.ai.save-cache-fail:promptName={}", promptName, e);
                    }
                }
                return res;
            });
        }

        return promise;
    }

    /**
     * Exception-aware retry loop (3.1-C7-006, plan 2556 裁定 H). The retry
     * unit is the whole {@link #callChat} invocation: every attempt re-enters
     * {@code chatService.callAsync} and therefore rechecks the local rate
     * limiter. Exceptionally-completed attempts are explicitly classified
     * ({@link LlmErrorClassifier#classify}) instead of being silently dropped
     * by a {@code thenCompose} result-chain. Retries wait on a
     * {@link StandardRetryPolicy} full-jitter delay scheduled as a
     * lightweight non-blocking hop on the global timer (§3.1.1 轻量直连豁免 —
     * the retry body itself lands on the completion thread, not on the timer).
     *
     * <p>Total attempts = {@code retryTimesPerRequest + 1} (the legacy
     * {@code RetryHelper.retryNTimes} count semantics: the config value is the
     * number of retry hops after the initial attempt).
     *
     * <p>Pinned contract: {@link #adjustTemperature} runs <b>per attempt</b>;
     * {@code processChatResponse} / {@link #postProcess} / invalid marking run
     * <b>once after the loop</b> (in {@link #finalizeResult}).
     */
    private CompletionStage<ChatExchange> executeWithRetry(ChatRequest request, ChatOptions options,
                                                           IEvalScope scope, ICancelToken cancelToken) {
        CompletableFuture<ChatExchange> promise = new CompletableFuture<>();
        executeAttempt(promise, request, options, scope, cancelToken, 0);
        return promise.thenApply(res -> finalizeResult(res, scope));
    }

    private void executeAttempt(CompletableFuture<ChatExchange> promise, ChatRequest request, ChatOptions options,
                                IEvalScope scope, ICancelToken cancelToken, int attempt) {
        // Per-attempt temperature ramp (pinned contract, see executeWithRetry).
        adjustTemperature(options, attempt);

        callChat(request.copy(), cancelToken).whenComplete((res, err) -> {
            if (err != null) {
                handleAttemptFailure(promise, request, options, scope, cancelToken, attempt, err);
                return;
            }
            // Normal return, no exception: an invalid (or empty) exchange maps
            // to TRANSIENT for delay purposes (裁定 H M2). Mirror the legacy
            // empty-marking so the retry predicate sees the same invalidity.
            if (res.isEmpty() && !res.isInvalid()) {
                res.setInvalid(true);
                res.setInvalidReason(new ErrorBean(ERR_AI_RESULT_IS_EMPTY.getErrorCode()));
            }
            if (!res.isValid() && attempt < retryTimesPerRequest) {
                // Invalid ChatExchange (normal return, no exception) → treat as
                // TRANSIENT for delay purposes (3.1-C7-006, plan 2556 裁定 H M2).
                RetryOutcome outcome = retryPolicy().shouldRetry(
                        new RetryContext(attempt, null, ErrorClassification.TRANSIENT, false));
                scheduleRetry(promise, request, options, scope, cancelToken, attempt,
                        outcome.getDelayMs());
                return;
            }
            promise.complete(res);
        });
    }

    private void handleAttemptFailure(CompletableFuture<ChatExchange> promise, ChatRequest request,
                                      ChatOptions options, IEvalScope scope, ICancelToken cancelToken,
                                      int attempt, Throwable err) {
        // Cancellation must propagate immediately — never retried.
        if (err instanceof CancellationException) {
            promise.completeExceptionally(err);
            return;
        }
        ErrorClassification classification = LlmErrorClassifier.classify(err);
        RetryOutcome outcome = retryPolicy().shouldRetry(
                new RetryContext(attempt, err, classification, false));
        if (outcome.isRetry() && attempt < retryTimesPerRequest) {
            scheduleRetry(promise, request, options, scope, cancelToken, attempt,
                    outcome.getDelayMs());
            return;
        }
        // Retry budget exhausted (or non-retryable classification): finish with
        // the legacy end-of-loop failure semantics (see finalizeFailure).
        finishFailure(promise, request, err);
    }

    /**
     * Non-blocking delay hop: {@code delayMs > 0} is scheduled on the global
     * timer (lightweight exemption §3.1.1); {@code delayMs == 0} proceeds
     * inline on the completion thread. The actual retry attempt runs on the
     * timer thread only for the trivial re-entry call — the heavy work still
     * lands on completion threads inside {@code chatService.callAsync}.
     */
    private void scheduleRetry(CompletableFuture<ChatExchange> promise, ChatRequest request, ChatOptions options,
                               IEvalScope scope, ICancelToken cancelToken, int attempt, long delayMs) {
        int nextAttempt = attempt + 1;
        LOG.info("nop.ai.chat-retry:attempt={},delayMs={},maxRetries={}",
                attempt, delayMs, retryTimesPerRequest);
        if (delayMs > 0) {
            IScheduledExecutor timer = GlobalExecutors.globalTimer();
            timer.schedule((Callable<Void>) () -> {
                executeAttempt(promise, request, options, scope, cancelToken, nextAttempt);
                return null;
            }, delayMs, TimeUnit.MILLISECONDS);
        } else {
            executeAttempt(promise, request, options, scope, cancelToken, nextAttempt);
        }
    }

    /**
     * Runs once after the retry loop (3.1-C7-006 裁定 H semantic-equivalence
     * pin): response parsing / post-processing moved from per-attempt to a
     * single terminal application. A parse/postProcess throw after the retry
     * budget is exhausted follows the terminal failure semantics (see
     * {@link #finishFailure}) instead of failing the future raw.
     */
    private ChatExchange finalizeResult(ChatExchange res, IEvalScope scope) {
        if (res.isInvalid() && res.getResponse() == null) {
            // Converted failure receipt from finishFailure: returned as-is
            // (legacy error path bypassed post-processing).
            return res;
        }
        try {
            promptTemplate.processChatResponse(res, scope);
            return postProcess(res);
        } catch (Exception e) {
            if (returnExceptionAsResponse) {
                res.setInvalid(true);
                res.setInvalidReason(ErrorMessageManager.instance()
                        .buildErrorMessage(null, e, false, false, true));
                return res;
            }
            throw NopException.adapt(e);
        }
    }

    private IRetryPolicy retryPolicy() {
        if (retryPolicy == null) {
            // maxAttempts = retryTimesPerRequest + 1 aligns the policy's
            // STOP-at-exhaustion rule with the legacy retry-hop count.
            retryPolicy = new StandardRetryPolicy(retryTimesPerRequest + 1,
                    StandardRetryPolicy.DEFAULT_BASE_DELAY_MS, StandardRetryPolicy.DEFAULT_MAX_DELAY_MS);
        }
        return retryPolicy;
    }

    /**
     * Terminal failure semantics (legacy {@code executeOnceAsync} error
     * branch, now applied once at the end of the loop): with
     * {@code returnExceptionAsResponse} the failure is converted into an
     * invalid {@link ChatExchange}; otherwise the promise fails exceptionally.
     * This is also the single failure channel for prologue exceptions of
     * {@link #callChat} (3.1-C3-005, plan 2556 裁定 A): those are rejected
     * futures, not synchronous throws, so they arrive here.
     */
    private void finishFailure(CompletableFuture<ChatExchange> promise, ChatRequest request, Throwable err) {
        if (returnExceptionAsResponse) {
            ChatExchange response = new ChatExchange(request, null);
            response.setInvalid(true);
            response.setInvalidReason(ErrorMessageManager.instance()
                    .buildErrorMessage(null, err, false, false, true));
            promise.complete(response);
        } else {
            promise.completeExceptionally(NopException.adapt(err));
        }
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

    private CompletionStage<ChatExchange> callChat(ChatRequest request, ICancelToken cancelToken) {
        if (cancelToken != null && cancelToken.isCancelled()) {
            LOG.info("nop.ai.cancel-call-ai");
            return FutureHelper.reject(new CancellationException("cancel-call-ai"));
        }

        try {
            return chatService.callAsync(request, cancelToken)
                    .thenApply(response -> new ChatExchange(request, response));
        } catch (Throwable e) {
            // 3.1-C3-005 (plan 2556 裁定 A): every prologue failure of the chat
            // stack (loadConfig / resolveBaseUrl / credential validation /
            // ChatServiceImpl.checkRateLimit's synchronous ERR_AI_RATE_LIMITED
            // throw before callAsync returns) is normalized into a failed
            // future — the caller observes a single exception channel, never a
            // synchronous throw escaping the async envelope.
            return FutureHelper.reject(e);
        }
    }

    public CompletionStage<ChatExchange> executeOnceAsync(ChatRequest request, ChatOptions chatOptions,
                                                          IEvalScope scope, ICancelToken cancelToken) {
        CompletionStage<ChatExchange> future = callChat(request, cancelToken);
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

        return request;
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
