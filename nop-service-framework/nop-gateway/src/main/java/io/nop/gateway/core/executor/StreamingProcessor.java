/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.executor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.interceptor.IGatewayInvocation;
import io.nop.gateway.core.streaming.StreamingResponse;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.gateway.model.GatewayStreamingModel;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IServerEventResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_INVOKE_WITH_NULL_URL;
import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_STREAMING_NOT_ENABLED;

/**
 * 处理流式响应
 *
 * <p>真正的流式处理实现，返回Flow.Publisher而不是收集后返回。</p>
 * <p>支持流式生命周期回调：onStreamStart/onStreamElement/onStreamError/onStreamComplete</p>
 */
public class StreamingProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(StreamingProcessor.class);

    private final IHttpClient httpClient;
    private final MappingProcessor mappingProcessor;

    public StreamingProcessor(IHttpClient httpClient, MappingProcessor mappingProcessor) {
        this.httpClient = httpClient;
        this.mappingProcessor = mappingProcessor;
    }

    /**
     * 执行流式路由处理
     *
     * @param route   路由配置
     * @param request API请求
     * @param context 网关上下文
     * @return 异步响应（包含流式数据标记）
     */
    public CompletionStage<ApiResponse<?>> executeStreaming(GatewayRouteModel route, ApiRequest<?> request,
                                                            IGatewayContext context, IGatewayInvocation invocation) {
        GatewayStreamingModel streaming = route.getStreaming();
        if (streaming == null) {
            throw new NopException(ERR_GATEWAY_STREAMING_NOT_ENABLED);
        }

        // 设置流式模式
        context.setStreamingMode(true);

        try {
            // 1. 调用onStreamStart回调
            invocation.proceedOnStreamStart(request, context);

            // 2. 构建流式HTTP请求
            HttpRequest httpRequest = buildStreamingHttpRequest(route, request, context);

            // 3. 获取Flow.Publisher
            Flow.Publisher<IServerEventResponse> eventPublisher = httpClient.fetchServerEventFlow(httpRequest, context);

            // 4. 创建流式响应包装器
            Flow.Publisher<Object> mappedPublisher = createMappedPublisher(
                    eventPublisher, streaming, invocation, context);

            // 5. 创建StreamingResponse并存储到context中
            String contentType = streaming.getContentType() != null
                    ? streaming.getContentType()
                    : "text/event-stream";

            StreamingResponse streamingResponse = new StreamingResponse(
                    mappedPublisher,
                    contentType,
                    streaming
            );

            // 存储在context中供HttpFilter使用
            context.setAttribute(StreamingResponse.class.getName(), streamingResponse);

            // 返回一个空的成功响应，实际数据通过流式传输
            return FutureHelper.success(ApiResponse.success(null));

        } catch (Exception e) {
            return FutureHelper.toCompletionStage(invocation.proceedOnError(e, context));
        }
    }

    /**
     * 创建映射后的Publisher
     */
    private Flow.Publisher<Object> createMappedPublisher(
            Flow.Publisher<IServerEventResponse> sourcePublisher,
            GatewayStreamingModel streaming, IGatewayInvocation invocation, IGatewayContext context) {

        return subscriber -> sourcePublisher.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(IServerEventResponse item) {
                try {
                    Object element = item.getData();

                    // 1. 应用拦截器的onStreamElement
                    element = invocation.proceedOnStreamElement(element, context);

                    if (element != null) {
                        subscriber.onNext(element);
                    }

                } catch (Exception e) {
                    onError(e);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                try {
                    Object element = invocation.proceedOnError(throwable, context);
                    if (element != null) {
                        subscriber.onNext(element);
                    }
                } catch (Exception e) {
                    subscriber.onError(throwable);
                }
            }

            @Override
            public void onComplete() {
                try {
                    invocation.proceedOnStreamComplete(context);
                } finally {
                    subscriber.onComplete();
                }
            }
        });
    }

    /**
     * 构建流式HTTP请求
     */
    private HttpRequest buildStreamingHttpRequest(GatewayRouteModel route, ApiRequest<?> request, IGatewayContext context) {
        if (route.getInvoke() == null || route.getInvoke().getUrl() == null) {
            throw new NopException(ERR_GATEWAY_STREAMING_NOT_ENABLED)
                    .source(route);
        }

        // 评估URL表达式
        Object urlObj = route.getInvoke().getUrl().invoke(context);
        if (urlObj == null) {
            throw new NopException(ERR_GATEWAY_INVOKE_WITH_NULL_URL)
                    .source(route.getInvoke());
        }

        String url = urlObj.toString();
        HttpRequest httpRequest = new HttpRequest();
        httpRequest.setUrl(url);
        httpRequest.setMethod(context.getHttpMethod());

        // 复制请求头
        if (request.getHeaders() != null) {
            for (Map.Entry<String, Object> entry : request.getHeaders().entrySet()) {
                httpRequest.header(entry.getKey(), entry.getValue());
            }
        }

        // 设置请求体
        httpRequest.setBody(request.getData());

        return httpRequest;
    }
}
