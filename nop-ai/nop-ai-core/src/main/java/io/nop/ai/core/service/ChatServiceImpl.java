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
import io.nop.ai.core.model.LlmModel;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.ratelimit.DefaultRateLimiter;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;
import io.nop.commons.util.StringHelper;
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
import static io.nop.ai.core.AiCoreErrors.ARG_HTTP_STATUS;
import static io.nop.ai.core.AiCoreErrors.ARG_LLM_NAME;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_SERVICE_HTTP_ERROR;

/**
 * 基于 llm.xml 配置的多模型 ChatService 实现。
 * <p>
 * 通过组合多个帮助类实现功能：
 * <ul>
 *   <li>{@link ChatRequestBuilder} - 构建HTTP请求</li>
 *   <li>{@link ChatResponseParser} - 解析HTTP响应</li>
 *   <li>{@link LlmConfigHelper} - 配置管理</li>
 *   <li>{@link MessageConverter} - 消息格式转换</li>
 *   <li>{@link ToolCallHelper} - 工具调用处理</li>
 * </ul>
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

        // 速率限制检查
        checkRateLimit(provider, config);

        long beginTime = CoreMetrics.currentTimeMillis();
        request.setRequestTime(beginTime);
        if (request.getRequestId() == null)
            request.setRequestId(StringHelper.generateUUID());

        boolean logMessage = CFG_AI_SERVICE_LOG_MESSAGE.get();
        if (logMessage) {
            chatLogger.logRequest(request);
        }

        // 构建请求
        String model = LlmConfigHelper.resolveModel(config, request.getOptions());
        HttpRequest httpRequest = new ChatRequestBuilder(config, provider, model, request, false).build();

        return httpClient.fetchAsync(httpRequest, cancelToken)
                .thenApply(response -> {
                    if (response.getHttpStatus() != 200) {
                        throw new NopException(ERR_AI_SERVICE_HTTP_ERROR)
                                .param(ARG_LLM_NAME, provider)
                                .param(ARG_HTTP_STATUS, response.getHttpStatus());
                    }

                    ChatResponse chatResponse = new ChatResponseParser(config, request)
                            .parse(response.getBodyAsString());
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

        // 速率限制检查
        checkRateLimit(provider, config);

        long beginTime = CoreMetrics.currentTimeMillis();
        request.setRequestTime(beginTime);
        if (request.getRequestId() == null)
            request.setRequestId(StringHelper.generateUUID());

        boolean logMessage = CFG_AI_SERVICE_LOG_MESSAGE.get();
        if (logMessage) {
            chatLogger.logRequest(request);
        }

        // 构建流式请求
        String model = LlmConfigHelper.resolveModel(config, request.getOptions());
        HttpRequest httpRequest = new ChatRequestBuilder(config, provider, model, request, true).build();
        httpRequest.setHeader("accept", "text/event-stream");

        SubmissionPublisher<ChatStreamChunk> publisher = new SubmissionPublisher<>();
        StreamChunkParser chunkParser = new StreamChunkParser(config);

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
                ChatStreamChunk chunk = chunkParser.parse(item.getData());
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
     * 检查并应用速率限制
     */
    private void checkRateLimit(String provider, LlmModel config) {
        if (config.getRateLimit() == null) {
            return;
        }

        IRateLimiter rateLimiter = rateLimiters.computeIfAbsent(provider, k -> {
            LOG.debug("nop.ai.create-rate-limiter: provider={}, rate={}", provider, config.getRateLimit());
            return createRateLimiter(config.getRateLimit());
        });

        rateLimiter.acquire();
    }

    /**
     * 创建速率限制器（可由子类覆盖）
     */
    protected IRateLimiter createRateLimiter(double rate) {
        return new DefaultRateLimiter(rate);
    }

    /**
     * 将流式响应汇聚为 ChatResponse
     */
    protected CompletionStage<ChatResponse> aggregateStreamToResponse(ChatRequest request, ICancelToken cancelToken) {

        // 汇聚器
        StreamAggregator aggregator = new StreamAggregator();
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

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
                future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                ChatResponse response = aggregator.toResponse();
                response.setRequestId(request.getRequestId());
                response.setResponseTime(CoreMetrics.currentTimeMillis());

                boolean logMessage = CFG_AI_SERVICE_LOG_MESSAGE.get();
                if (logMessage) {
                    chatLogger.logResponse(request, response);
                }
                future.complete(response);
            }
        });

        return future;
    }

    /**
     * 流式响应汇聚器
     * 将多个 ChatStreamChunk 聚合成一个 ChatResponse
     */
    private static class StreamAggregator {
        private final StringBuilder contentBuilder = new StringBuilder();
        private final StringBuilder thinkingBuilder = new StringBuilder();
        // 按 index 累积 tool calls，支持多工具调用
        private final Map<Integer, ToolCallAccumulator> toolCallAccumulators = new LinkedHashMap<>();
        private String id;
        private String model;
        private String finishReason;

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
            // 处理工具调用增量
            if (chunk.getToolCall() != null) {
                addToolCallChunk(chunk.getToolCall());
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

            io.nop.ai.api.chat.messages.ChatAssistantMessage message =
                    new io.nop.ai.api.chat.messages.ChatAssistantMessage();
            message.setContent(contentBuilder.toString());

            String thinking = thinkingBuilder.toString();
            if (!thinking.isEmpty()) {
                message.setThink(thinking);
            }

            // 组装工具调用列表
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

        /**
         * 工具调用累积器 - 累积单个工具调用的增量数据
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
                
                // 解析累积的 arguments JSON
                String argsStr = argumentsBuilder.toString();
                if (!argsStr.isEmpty()) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> args = (Map<String, Object>) io.nop.api.core.json.JSON.parse(argsStr);
                        toolCall.setArguments(args);
                    } catch (Exception e) {
                        // JSON 解析失败，设置为空对象
                        toolCall.setArguments(new LinkedHashMap<>());
                    }
                } else {
                    toolCall.setArguments(new LinkedHashMap<>());
                }
                return toolCall;
            }
        }
    }
}
