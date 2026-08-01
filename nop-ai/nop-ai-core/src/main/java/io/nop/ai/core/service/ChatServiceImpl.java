/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatLogger;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.dialect.ILlmDialect;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.model.LlmModelModel;
import io.nop.ai.core.dialect.LlmDialectFactory;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.ratelimit.DefaultRateLimiter;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IServerEventResponse;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_LOG_MESSAGE;
import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_RATE_LIMIT_ACQUIRE_TIMEOUT;
import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_READ_TIMEOUT;
import static io.nop.ai.core.NopAiCoreErrors.ARG_HTTP_STATUS;
import static io.nop.ai.core.NopAiCoreErrors.ARG_LLM_NAME;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_RATE_LIMITED;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_SERVICE_NO_BASE_URL;
import static io.nop.http.api.HttpApiErrors.ARG_BODY;
import static io.nop.http.api.HttpApiErrors.ARG_RESPONSE_HEADERS;

/**
 * 基于 llm.xml 配置的多模型 ChatService 实现。
 * <p>
 * 使用 LlmDialect 处理不同 API 风格的特定逻辑，将请求构建、响应解析、流式处理
 * 等功能委托给对应的方言实现。
 *
 * @author canonical_entropy@163.com
 */
public class ChatServiceImpl implements IChatService {
    private static final Logger LOG = LoggerFactory.getLogger(ChatServiceImpl.class);

    private IHttpClient httpClient;
    private final Map<String, IRateLimiter> rateLimiters = new ConcurrentHashMap<>();
    private IChatLogger chatLogger;

    @Inject
    public void setChatLogger(IChatLogger chatLogger) {
        this.chatLogger = chatLogger;
    }

    @Inject
    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @InjectValue("@cfg:nop.ai.secret-dir|/nop/ai/secret")
    public void setSecretDir(File secretDir) {
        LlmConfigHelper.setSecretDir(secretDir);
    }

    public void clearSecretCache() {
        LlmConfigHelper.clearSecretCache();
    }

    @Override
    public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
        boolean stream = true;
        if (request.getOptions().getStream() != null)
            stream = request.getOptions().getStream();

        // 如果 stream=true，先调用流式接口，再汇聚结果
        if (stream) {
            return aggregateStreamToResponse(request, cancelToken);
        }

        String provider = LlmConfigHelper.getProvider(request.getOptions());
        LlmModel config = LlmConfigHelper.loadConfig(provider);
        ILlmDialect dialect = LlmDialectFactory.getDialect(config.getApiStyle());

        // 速率限制检查
        checkRateLimit(provider, config);

        long beginTime = CoreMetrics.currentTimeMillis();
        request.setRequestTime(beginTime);
        if (request.getRequestId() == null)
            request.setRequestId(StringHelper.generateUUID());

        boolean logMessage = shouldLogMessage(config);
        if (logMessage) {
            chatLogger.logRequest(request);
        }

        // 构建请求
        String model = LlmConfigHelper.resolveModel(config, request.getOptions());
        HttpRequest httpRequest = buildHttpRequest(config, provider, model, request, false, dialect);

        return httpClient.fetchAsync(httpRequest, cancelToken)
                .thenApply(response -> {
                    if (response.getHttpStatus() != 200) {
                        // 非 200 不再吞 body/头，也不抛异常——经 dialect 规范化为携带
                        // errorClassification 的错误 ChatResponse（设计 §3.4 契约变更：
                        // 响应级错误走 ChatResponse，传输级错误才抛异常）。
                        ChatResponse errResponse = dialect.parseErrorResponse(
                                response.getBodyAsString(),
                                response.getHttpStatus(),
                                response.getHeaders(),
                                config);
                        errResponse.setRequestId(request.getRequestId());
                        errResponse.setResponseTime(CoreMetrics.currentTimeMillis());

                        if (logMessage) {
                            chatLogger.logResponse(request, errResponse);
                        }
                        return errResponse;
                    }

                    ChatResponse chatResponse = dialect.parseResponse(response.getBodyAsString(), config);
                    chatResponse.setRequestId(request.getRequestId());
                    chatResponse.setResponseTime(CoreMetrics.currentTimeMillis());

                    if (logMessage) {
                        chatLogger.logResponse(request, chatResponse);
                    }
                    return chatResponse;
                });
    }

    @Override
    public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
        String provider = LlmConfigHelper.getProvider(request.getOptions());
        LlmModel config = LlmConfigHelper.loadConfig(provider);
        ILlmDialect dialect = LlmDialectFactory.getDialect(config.getApiStyle());

        // 速率限制检查
        checkRateLimit(provider, config);

        long beginTime = CoreMetrics.currentTimeMillis();
        request.setRequestTime(beginTime);
        if (request.getRequestId() == null)
            request.setRequestId(StringHelper.generateUUID());

        boolean logMessage = shouldLogMessage(config);
        if (logMessage) {
            chatLogger.logRequest(request);
        }

        // 构建流式请求
        String model = LlmConfigHelper.resolveModel(config, request.getOptions());
        HttpRequest httpRequest = buildHttpRequest(config, provider, model, request, true, dialect);
        httpRequest.setHeader("accept", "text/event-stream");

        SubmissionPublisher<ChatStreamChunk> publisher = new SubmissionPublisher<>();

        Flow.Publisher<IServerEventResponse> eventPublisher = httpClient.fetchServerEventFlow(httpRequest, cancelToken);
        eventPublisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
                if (cancelToken != null) {
                    cancelToken.appendOnCancelTask(subscription::cancel);
                }
            }

            @Override
            public void onNext(IServerEventResponse item) {
                ChatStreamChunk chunk = dialect.parseStreamChunk(item.getData());
                if (chunk != null) {
                    publisher.submit(chunk);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                publisher.closeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                publisher.close();
            }
        });

        return publisher;
    }

    /**
     * 构建 HTTP 请求
     */
    private HttpRequest buildHttpRequest(LlmModel config, String provider, String model,
                                           ChatRequest request, boolean stream, ILlmDialect dialect) {
        String baseUrl = resolveBaseUrl(config, provider);
        String apiKey = LlmConfigHelper.resolveApiKey(provider);
        LlmModelModel modelConfig = LlmConfigHelper.getModelConfig(config, model);

        HttpRequest httpRequest = new HttpRequest();
        httpRequest.setMethod("POST");
        httpRequest.setUrl(dialect.buildUrl(baseUrl, config.getChatUrl(), apiKey));
        dialect.setHeaders(httpRequest, apiKey, config.getApiKeyHeader());

        httpRequest.setTimeout(CFG_AI_SERVICE_READ_TIMEOUT.get());

        Map<String, Object> body = dialect.buildBody(request, config, modelConfig, model, stream);
        httpRequest.setBody(JsonTool.serialize(body, false));

        return httpRequest;
    }

    /**
     * 解析 Base URL
     */
    private String resolveBaseUrl(LlmModel config, String provider) {
        String baseUrlKey = StringHelper.replace(
            io.nop.ai.core.AiCoreConstants.CONFIG_VAR_LLM_BASE_URL,
            io.nop.ai.core.AiCoreConstants.PLACE_HOLDER_LLM_NAME,
            provider
        );
        String baseUrl = (String) io.nop.api.core.config.AppConfig.var(baseUrlKey);

        if (StringHelper.isEmpty(baseUrl)) {
            baseUrl = config.getBaseUrl();
        }

        if (StringHelper.isEmpty(baseUrl)) {
            throw new NopException(ERR_AI_SERVICE_NO_BASE_URL).param(ARG_LLM_NAME, provider);
        }

        return baseUrl;
    }

    /**
     * 检查并应用速率限制（MA6.3-AR-6）：限时 {@code tryAcquire} 替代旧的无限阻塞
     * {@code acquire()}——配额耗尽时在 {@code nop.ai.service.rate-limit-acquire-timeout}
     * 内等待许可，超时抛 {@code ERR_AI_RATE_LIMITED}（携带 httpStatus=429，经
     * {@code LlmErrorClassifier} 判为 RATE_LIMITED 可重试，与上层 ReAct 重试循环联动）。
     * 失败面不静默：错误可被上层观察/重试。
     *
     * <p>per-tenant 配额：当前实现无 tenant 身份来源（无 ITenantResolver、无请求头解析），
     * 按 MA6.3-AR-6 裁定为<b>文档化扩展点</b>——子类可覆盖
     * {@link #createRateLimiter(double)}（按需 per-tenant key 建 limiter）或本方法
     * 引入自己的租户维度；跨 JVM 分布式限流同样为文档化扩展点（替换
     * {@code IRateLimiter} 实现即可，接口已抽象 tryAcquire 语义）。
     */
    void checkRateLimit(String provider, LlmModel config) {
        if (config.getRateLimit() == null) {
            return;
        }

        IRateLimiter rateLimiter = rateLimiters.computeIfAbsent(provider, k -> {
            LOG.debug("nop.ai.create-rate-limiter: provider={}, rate={}", provider, config.getRateLimit());
            return createRateLimiter(config.getRateLimit());
        });

        long timeoutMs = CFG_AI_SERVICE_RATE_LIMIT_ACQUIRE_TIMEOUT.get();
        if (!rateLimiter.tryAcquire(1, timeoutMs)) {
            throw new NopException(ERR_AI_RATE_LIMITED)
                    .param(ARG_LLM_NAME, provider)
                    .param(ARG_HTTP_STATUS, 429);
        }
    }

    private boolean shouldLogMessage(LlmModel config) {
        return CFG_AI_SERVICE_LOG_MESSAGE.get() && config.isLogMessage();
    }

    /**
     * 创建速率限制器（可由子类覆盖）
     */
    protected IRateLimiter createRateLimiter(double rate) {
        return new DefaultRateLimiter(rate);
    }

    /**
     * 将流式响应汇聚为 ChatResponse
     *
     * <p>错误路径（设计 §3.4）：流式 {@code onError} 收到的异常携带 Phase 1 挂上的
     * {@code ARG_BODY} + {@code ARG_HTTP_STATUS} + {@code ARG_RESPONSE_HEADERS}（含
     * Retry-After）。本方法从异常取出这些信息，经 {@code dialect.parseErrorResponse(...)}
     * 规范化为携带 {@code errorClassification} 的错误 ChatResponse，并 {@code complete}
     * （不 exceptionally）——与 {@code callAsync} 非流式错误路径一致：响应级错误走 ChatResponse。
     * 已流出内容后的错误仍走异常（流式保护不变，由上层 {@code hasStreamedContent} 守卫）。</p>
     */
    protected CompletionStage<ChatResponse> aggregateStreamToResponse(ChatRequest request, ICancelToken cancelToken) {
        StreamAggregator aggregator = new StreamAggregator();
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        String provider = LlmConfigHelper.getProvider(request.getOptions());
        LlmModel config = LlmConfigHelper.loadConfig(provider);
        ILlmDialect dialect = LlmDialectFactory.getDialect(config.getApiStyle());
        boolean logMessage = shouldLogMessage(config);

        callStream(request, cancelToken).subscribe(new Flow.Subscriber<ChatStreamChunk>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Integer.MAX_VALUE);
            }

            @Override
            public void onNext(ChatStreamChunk item) {
                aggregator.addChunk(item);
            }

            @Override
            public void onError(Throwable throwable) {
                ChatResponse errResponse = parseStreamError(throwable, dialect, config);
                if (errResponse != null) {
                    errResponse.setRequestId(request.getRequestId());
                    errResponse.setResponseTime(CoreMetrics.currentTimeMillis());
                    if (logMessage) {
                        chatLogger.logResponse(request, errResponse);
                    }
                    future.complete(errResponse);
                } else {
                    future.completeExceptionally(throwable);
                }
            }

            @Override
            public void onComplete() {
                ChatResponse response = aggregator.toResponse();
                response.setRequestId(request.getRequestId());
                response.setResponseTime(CoreMetrics.currentTimeMillis());

                if (logMessage) {
                    chatLogger.logResponse(request, response);
                }
                future.complete(response);
            }
        });

        return future;
    }

    /**
     * 从流式 {@code onError} 的异常中解析错误 ChatResponse。仅当异常是携带 HTTP 状态码的
     * {@link NopException}（即 {@code ServerEventPublisher} 非 2xx 抛出的响应级错误）时规范化；
     * 否则返回 null（传输级错误——无 HTTP 响应，无法构造 ChatResponse，仍 exceptionally）。
     */
    @SuppressWarnings("unchecked")
    private ChatResponse parseStreamError(Throwable throwable, ILlmDialect dialect, LlmModel config) {
        Throwable t = throwable;
        while (t != null) {
            if (t instanceof NopException) {
                NopException ex = (NopException) t;
                Object statusObj = ex.getParam(ARG_HTTP_STATUS);
                Integer httpStatus = statusObj instanceof Number ? ((Number) statusObj).intValue() : null;
                if (httpStatus == null) {
                    break;
                }
                Object bodyObj = ex.getParam(ARG_BODY);
                String body = bodyObj != null ? bodyObj.toString() : "";
                Object headersObj = ex.getParam(ARG_RESPONSE_HEADERS);
                Map<String, String> headers = headersObj instanceof Map
                        ? (Map<String, String>) headersObj : null;
                return dialect.parseErrorResponse(body, httpStatus, headers, config);
            }
            t = t.getCause();
        }
        return null;
    }

    /**
     * 流式响应汇聚器
     * 将多个 ChatStreamChunk 聚合成一个 ChatResponse
     */
    static class StreamAggregator {
        private final StringBuilder contentBuilder = new StringBuilder();
        private final StringBuilder thinkingBuilder = new StringBuilder();
        private final Map<Integer, ToolCallAccumulator> toolCallAccumulators = new LinkedHashMap<>();
        private String id;
        private String model;
        private String finishReason;
        private io.nop.ai.api.chat.messages.ChatUsage usage;

        void addChunk(ChatStreamChunk chunk) {
            if (chunk.getId() != null) {
                this.id = chunk.getId();
            }
            if (chunk.getModel() != null) {
                this.model = chunk.getModel();
            }
            if (chunk.getContent() != null) {
                contentBuilder.append(chunk.getContent());
            }
            if (chunk.getThinking() != null) {
                thinkingBuilder.append(chunk.getThinking());
            }
            if (chunk.getFinishReason() != null) {
                this.finishReason = chunk.getFinishReason();
            }
            if (chunk.getToolCall() != null) {
                addToolCallChunk(chunk.getToolCall());
            }
            if (chunk.getUsage() != null) {
                this.usage = chunk.getUsage();
            }
        }

        private void addToolCallChunk(io.nop.ai.api.chat.stream.ChatToolCallChunk toolCallChunk) {
            Integer index = toolCallChunk.getIndex() != null ? toolCallChunk.getIndex() : 0;
            ToolCallAccumulator acc = toolCallAccumulators.computeIfAbsent(index, k -> new ToolCallAccumulator());
            
            if (toolCallChunk.getId() != null) {
                acc.id = toolCallChunk.getId();
            }
            if (toolCallChunk.getName() != null) {
                acc.name = toolCallChunk.getName();
            }
            if (toolCallChunk.getArguments() != null) {
                acc.argumentsBuilder.append(toolCallChunk.getArguments());
            }
        }

        ChatResponse toResponse() {
            ChatResponse response = new ChatResponse();
            response.setId(id);
            response.setModel(model);
            response.setFinishReason(finishReason);
            response.setUsage(usage);

            io.nop.ai.api.chat.messages.ChatAssistantMessage message =
                    new io.nop.ai.api.chat.messages.ChatAssistantMessage();
            message.setContent(contentBuilder.toString());

            String thinking = thinkingBuilder.toString();
            if (!thinking.isEmpty()) {
                message.setThink(thinking);
            }

            if (!toolCallAccumulators.isEmpty()) {
                List<io.nop.ai.api.chat.messages.ChatToolCall> toolCalls = new ArrayList<>();
                for (ToolCallAccumulator acc : toolCallAccumulators.values()) {
                    io.nop.ai.api.chat.messages.ChatToolCall toolCall = acc.toToolCall();
                    if (toolCall != null) {
                        toolCalls.add(toolCall);
                    }
                }
                if (!toolCalls.isEmpty()) {
                    message.setToolCalls(toolCalls);
                }
            }

            response.setMessage(message);
            return response;
        }
    }

    /**
     * 工具调用累积器
     */
    private static class ToolCallAccumulator {
        String id;
        String name;
        final StringBuilder argumentsBuilder = new StringBuilder();

        io.nop.ai.api.chat.messages.ChatToolCall toToolCall() {
            if (id == null || name == null) {
                return null;
            }
            io.nop.ai.api.chat.messages.ChatToolCall toolCall = 
                new io.nop.ai.api.chat.messages.ChatToolCall();
            toolCall.setId(id);
            toolCall.setName(name);
            
            String argsStr = argumentsBuilder.toString();
            if (!argsStr.isEmpty()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> args = (Map<String, Object>) JSON.parse(argsStr);
                    toolCall.setArguments(args);
                } catch (Exception e) {
                    toolCall.setArguments(new LinkedHashMap<>());
                }
            } else {
                toolCall.setArguments(new LinkedHashMap<>());
            }
            return toolCall;
        }
    }
}
