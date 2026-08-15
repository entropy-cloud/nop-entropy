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
import io.nop.api.core.json.JSON;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.StringHelper;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.interceptor.IGatewayInvocation;
import io.nop.gateway.core.streaming.GatewayStreamingConstants;
import io.nop.gateway.core.streaming.IStreamingLifecycleListener;
import io.nop.gateway.core.streaming.IStreamingRetryCallback;
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

            // 2a. 缓冲/重执行扩展点（W7 机制 A，plan 2026-08-15-1116-3）：重执行回调 + 生命周期
            //     监听器经 IGatewayContext attribute 注入（Phase 1 GW-A6/B-12，拦截器 onRequest
            //     写入；缺省 null = 不重订阅/不计数，零回归）。缓冲层 engage 条件 = 回调/监听器
            //     任一存在或 streaming 配置开启缓冲（缓冲关闭时生命周期回调仍触发——计数与
            //     缓冲解耦）。
            IStreamingRetryCallback retryCallback = (IStreamingRetryCallback) context.getAttribute(
                    GatewayStreamingConstants.ATTR_RETRY_CALLBACK);
            IStreamingLifecycleListener lifecycle = (IStreamingLifecycleListener) context.getAttribute(
                    GatewayStreamingConstants.ATTR_LIFECYCLE_LISTENER);
            boolean bufferEngaged = retryCallback != null || lifecycle != null
                    || Boolean.TRUE.equals(streaming.getBufferEnabled());

            Flow.Publisher<Object> mappedPublisher;
            if (bufferEngaged) {
                // 3. 缓冲 + 重订阅层（fetch + 映射链重跑留在本层内部；回调只产出新 HttpRequest）
                mappedPublisher = new BufferedStreamingPublisher(
                        httpClient, route, request, context, streaming, retryCallback, lifecycle,
                        eventPublisher -> createMappedPublisher(eventPublisher, streaming, invocation, context),
                        httpRequest);
            } else {
                // 4. 获取Flow.Publisher
                Flow.Publisher<IServerEventResponse> eventPublisher =
                        httpClient.fetchServerEventFlow(httpRequest, context);

                // 5. 创建流式响应包装器
                mappedPublisher = createMappedPublisher(eventPublisher, streaming, invocation, context);
            }

            // 6. 创建StreamingResponse并存储到context中
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
                subscription.request(1);
            }

            @Override
            public void onNext(IServerEventResponse item) {
                try {
                    Object element = item.getData();
                    if (element instanceof String) {
                        element = JSON.parse(element.toString());
                    }

                    // 1. 应用拦截器的onStreamElement
                    element = invocation.proceedOnStreamElement(element, context);

                    if (element != null) {
                        subscriber.onNext(element);
                    }

                    subscription.request(1);

                } catch (Exception e) {
                    subscriber.onError(e);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                try {
                    Object element = invocation.proceedOnError(throwable, context);
                    if (element != null) {
                        // 降级响应为正常终止信号：onNext + onComplete，而非 onError
                        subscriber.onNext(element);
                        subscriber.onComplete();
                    } else {
                        subscriber.onError(throwable);
                    }
                } catch (Exception e) {
                    subscriber.onError(e);
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

        // base 替换语义（W7 Phase 1 GW-A7/B-14）：request properties 中的 base-url 覆盖
        // （拦截器写入目标候选 accountBaseUrl）替换求值 URL 的 scheme://authority 之后的部分
        // （保留 path+query）；缺省 = 既有表达式求值，零回归。与 dialect.buildUrl(base, chatUrl,
        // apiKey) 组合一致（重试回调按同一语义构造 HttpRequest）。
        url = applyBaseOverride(url, request);

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

    /**
     * base 替换（GW-A7/B-14）：{@code baseOverride} 非空时，以 baseOverride 替换 {@code url} 的
     * base 部分（scheme://authority 之后的一切，含 path+query），保持 path+query 不变。
     */
    static String applyBaseOverride(String url, ApiRequest<?> request) {
        String baseOverride = request != null
                ? request.getStringProperty(GatewayStreamingConstants.PROP_BASE_URL) : null;
        if (StringHelper.isEmpty(baseOverride)) {
            return url;
        }
        int schemeIdx = url.indexOf("://");
        if (schemeIdx < 0) {
            // 非 http(s) URL（表达式产物异常）：显式回退既有 URL（不静默吞覆盖意图——日志可查）。
            return url;
        }
        String base = baseOverride.endsWith("/")
                ? baseOverride.substring(0, baseOverride.length() - 1) : baseOverride;
        int pathIdx = url.indexOf('/', schemeIdx + 3);
        String rest = pathIdx >= 0 ? url.substring(pathIdx) : "";
        return base + rest;
    }
}
