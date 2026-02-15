/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.mock;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.StringHelper;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 基于内存的响应提供者实现。
 * 适用于单元测试和集成测试场景，可以通过编程方式设置预期响应。
 */
public class InMemoryResponseProvider implements IResponseProvider {

    private final Map<String, BlockingQueue<ChatResponse>> responseMap = new ConcurrentHashMap<>();
    private final Map<String, ChatRequest> requestMap = new ConcurrentHashMap<>();
    private Function<ChatRequest, ChatResponse> responseHandler;
    private long defaultTimeoutMs = 30000; // 默认30秒超时

    /**
     * 设置默认超时时间。
     *
     * @param timeoutMs 超时时间（毫秒）
     */
    public void setDefaultTimeoutMs(long timeoutMs) {
        this.defaultTimeoutMs = timeoutMs;
    }

    /**
     * 设置响应处理器，用于根据请求动态生成响应。
     *
     * @param handler 响应处理器
     */
    public void setResponseHandler(Function<ChatRequest, ChatResponse> handler) {
        this.responseHandler = handler;
    }

    /**
     * 为指定请求ID预置响应。
     *
     * @param requestId 请求ID
     * @param response  响应内容
     */
    public void mockResponse(String requestId, ChatResponse response) {
        responseMap.computeIfAbsent(requestId, k -> new LinkedBlockingQueue<>())
                .offer(response);
    }

    /**
     * 为指定请求ID预置文本响应。
     *
     * @param requestId    请求ID
     * @param content      响应文本内容
     * @param finishReason 结束原因
     */
    public void mockTextResponse(String requestId, String content, String finishReason) {
        ChatResponse response = new ChatResponse();
        ChatAssistantMessage message = new ChatAssistantMessage();
        message.setContent(content);
        response.setMessage(message);
        response.setFinishReason(finishReason);
        response.setResponseTime(CoreMetrics.currentTimeMillis());
        mockResponse(requestId, response);
    }

    /**
     * 为指定请求ID预置文本响应（默认finishReason为stop）。
     *
     * @param requestId 请求ID
     * @param content   响应文本内容
     */
    public void mockTextResponse(String requestId, String content) {
        mockTextResponse(requestId, content, "stop");
    }

    /**
     * 为下一个请求预置响应（使用任意请求ID）。
     *
     * @param response 响应内容
     */
    public void mockNextResponse(ChatResponse response) {
        mockResponse("*", response);
    }

    /**
     * 为下一个请求预置文本响应。
     *
     * @param content 响应文本内容
     */
    public void mockNextTextResponse(String content) {
        mockTextResponse("*", content);
    }

    /**
     * 清除指定请求的所有预置响应。
     *
     * @param requestId 请求ID
     */
    public void clearResponses(String requestId) {
        BlockingQueue<ChatResponse> queue = responseMap.get(requestId);
        if (queue != null) {
            queue.clear();
        }
    }

    /**
     * 清除所有预置响应。
     */
    public void clearAllResponses() {
        responseMap.clear();
        requestMap.clear();
    }

    /**
     * 获取已记录的请求。
     *
     * @param requestId 请求ID
     * @return 请求对象
     */
    public ChatRequest getRecordedRequest(String requestId) {
        return requestMap.get(requestId);
    }

    @Override
    public CompletionStage<ChatResponse> awaitResponse(ChatRequest request, ICancelToken cancelToken) {
        return CompletableFuture.supplyAsync(() -> {
            String requestId = getRequestId(request);
            requestMap.put(requestId, request);

            if (cancelToken != null && cancelToken.isCancelled()) {
                throw new CancellationException("Request cancelled");
            }

            // 优先使用预置的特定响应
            ChatResponse response = pollResponse(requestId, cancelToken);

            // 如果没有特定响应，尝试使用通配符响应
            if (response == null) {
                response = pollResponse("*", cancelToken);
            }

            // 如果有响应处理器，使用它生成响应
            if (response == null && responseHandler != null) {
                response = responseHandler.apply(request);
            }

            if (response == null) {
                throw new IllegalStateException("No mock response available for request: " + requestId);
            }

            // 设置响应元数据
            if (response.getRequestId() == null) {
                response.setRequestId(requestId);
            }
            if (response.getResponseTime() == 0) {
                response.setResponseTime(CoreMetrics.currentTimeMillis());
            }

            return response;
        });
    }

    protected ChatResponse pollResponse(String requestId, ICancelToken cancelToken) {
        BlockingQueue<ChatResponse> queue = responseMap.get(requestId);
        if (queue == null || queue.isEmpty()) {
            return null;
        }

        try {
            while (true) {
                if (cancelToken != null && cancelToken.isCancelled()) {
                    throw new CancellationException("Request cancelled");
                }

                ChatResponse response = queue.poll(100, TimeUnit.MILLISECONDS);
                if (response != null) {
                    return response;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Response polling interrupted", e);
        }
    }

    protected String getRequestId(ChatRequest request) {
        if (StringHelper.isEmpty(request.getRequestId())) {
            return StringHelper.generateUUID();
        }
        return request.getRequestId();
    }
}
