/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.http;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.cache.ResourceCacheEntry;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.streaming.StreamingResponse;
import io.nop.gateway.impl.GatewayHandler;
import io.nop.gateway.model.GatewayModel;
import io.nop.http.api.HttpStatus;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.server.IHttpServerContext;
import io.nop.http.api.server.IHttpServerFilter;
import io.nop.record_mapping.IRecordMappingManager;
import io.nop.rpc.core.utils.RpcHelper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Supplier;

import static io.nop.gateway.GatewayConfigs.CFG_GATEWAY_MODEL_PATH;

/**
 * Gateway HTTP过滤器
 *
 * <p>作为HTTP请求的入口点，负责：</p>
 * <ul>
 *   <li>加载GatewayModel配置</li>
 *   <li>构建ApiRequest请求对象</li>
 *   <li>调用GatewayHandler处理请求</li>
 *   <li>处理普通响应和流式响应</li>
 * </ul>
 */
public class GatewayHttpFilter implements IHttpServerFilter {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayHttpFilter.class);

    private IRpcServiceInvoker rpcServiceInvoker;
    private IHttpClient httpClient;
    private IRecordMappingManager recordMappingManager;

    private ResourceCacheEntry<GatewayHandler> handlerCache;

    @Inject
    public void setRpcServiceInvoker(IRpcServiceInvoker rpcServiceInvoker) {
        this.rpcServiceInvoker = rpcServiceInvoker;
    }

    @Inject
    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Inject
    public void setRecordMappingManager(IRecordMappingManager recordMappingManager) {
        this.recordMappingManager = recordMappingManager;
    }

    @PostConstruct
    public void init() {
        String modelPath = CFG_GATEWAY_MODEL_PATH.get();
        if (!StringHelper.isEmpty(modelPath)) {
            handlerCache = new ResourceCacheEntry<>(modelPath);
        }
    }

    @PreDestroy
    public void destroy() {
        if (handlerCache != null) {
            handlerCache.destroy();
        }
    }

    private final IResourceObjectLoader<GatewayHandler> handlerLoader = path -> {
        GatewayModel model = (GatewayModel) ResourceComponentManager.instance().loadComponentModel(path);
        if (model == null) {
            return null;
        }
        return new GatewayHandler(model, rpcServiceInvoker, httpClient, recordMappingManager);
    };

    @Override
    public int order() {
        return NORMAL_PRIORITY - 50;
    }

    @Override
    public CompletionStage<Void> filterAsync(IHttpServerContext context, Supplier<CompletionStage<Void>> next) {
        if (handlerCache == null) {
            return next.get();
        }

        GatewayHandler handler;
        try {
            handler = handlerCache.getObject(true, handlerLoader);
            if (handler == null) {
                return next.get();
            }

            ApiRequest<?> request = buildRequest(context);
            IGatewayContext gatewayCtx = buildGatewayContext(request, context);

            context.resumeRequest();

            CompletionStage<ApiResponse<?>> future = handler.handle(request, gatewayCtx);
            if (future == null) {
                return next.get();
            }

            return future.thenCompose(ret -> write(context, ret, gatewayCtx));
        } catch (Exception e) {
            LOG.error("nop.gateway.process-error:path={}", context.getRequestPath(), e);
            String locale = ContextProvider.currentLocale();
            ApiResponse<?> res = ErrorMessageManager.instance().buildResponseForException(locale, e);
            writeErrorResponse(context, res);
            return FutureHelper.success(null);
        }
    }

    protected CompletionStage<Void> write(IHttpServerContext context, ApiResponse<?> response, IGatewayContext gatewayCtx) {
        StreamingResponse streamingResponse = (StreamingResponse) gatewayCtx.getAttribute(StreamingResponse.class.getName());

        if (streamingResponse != null) {
            return writeStreamingResponse(context, streamingResponse);
        }
        if (response.isOk()) {
            writeNormalResponse(context, response);
        } else {
            writeErrorResponse(context, response);
        }
        return FutureHelper.success(null);
    }

    protected CompletionStage<Void> writeStreamingResponse(IHttpServerContext context, StreamingResponse streamingResponse) {
        String contentType = streamingResponse.getContentType();
        if (contentType == null) {
            contentType = "text/event-stream";
        }

        final String ct = contentType;
        Flow.Publisher<String> stringPublisher = subscriber -> {
            Flow.Publisher<Object> source = streamingResponse.getPublisher();
            source.subscribe(new Flow.Subscriber<Object>() {
                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.subscription = subscription;
                    subscriber.onSubscribe(subscription);
                    subscription.request(1);
                }

                @Override
                public void onNext(Object item) {
                    if (item != null) {
                        String data = serializeStreamElement(item, ct);
                        if (data != null) {
                            subscriber.onNext(data);
                            return;
                        }
                    }
                    subscription.request(1);
                }

                @Override
                public void onError(Throwable throwable) {
                    subscriber.onError(throwable);
                }

                @Override
                public void onComplete() {
                    subscriber.onComplete();
                }
            });
        };

        return context.sendStreamingResponse(HttpStatus.SC_OK, contentType, stringPublisher);
    }

    private String serializeStreamElement(Object item, String contentType) {
        if (item == null) {
            return null;
        }

        if (contentType.contains("event-stream")) {
            String json = JsonTool.serialize(item, false);
            return "data: " + json + "\n\n";
        } else if (contentType.contains("ndjson")) {
            String json = JsonTool.serialize(item, false);
            return json + "\n";
        } else {
            return JsonTool.serialize(item, false);
        }
    }

    /**
     * 写入普通响应
     */
    protected void writeNormalResponse(IHttpServerContext context, ApiResponse<?> response) {
        int status = response.getHttpStatus();
        if (status == 0) {
            status = HttpStatus.SC_OK;
        }

        boolean wrapper = response.isWrapper();
        String body;
        if (wrapper) {
            body = JsonTool.serialize(response.getData(), false);
        } else {
            body = JsonTool.serialize(response.cloneInstance(false), false);
        }

        if (response.getHeaders() != null && !response.getHeaders().isEmpty()) {
            writeHeaders(context, response.getHeaders());
        }
        context.sendResponse(status, body);
    }

    /**
     * 写入错误响应
     */
    protected void writeErrorResponse(IHttpServerContext context, ApiResponse<?> response) {
        int status = response.getHttpStatus();
        if (status == 0) {
            status = HttpStatus.SC_OK;
        }
        boolean wrapper = response.isWrapper();
        String body;
        if (wrapper) {
            body = JsonTool.serialize(response.getMsg(), false);
        } else {
            body = JsonTool.serialize(response.cloneInstance(false), false);
        }
        if (response.getHeaders() != null && !response.getHeaders().isEmpty()) {
            writeHeaders(context, response.getHeaders());
        }
        context.sendResponse(status, body);
    }

    /**
     * 写入响应头
     */
    protected void writeHeaders(IHttpServerContext context, Map<String, Object> headers) {
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            context.setResponseHeader(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 构建API请求
     */
    protected ApiRequest<?> buildRequest(IHttpServerContext context) {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setHeaders(context.getRequestHeaders());
        request.setData(context.getRequestBody().getJson());
        RpcHelper.setHttpUrl(request, context.getRequestUrl());
        RpcHelper.setHttpMethod(request, context.getMethod());
        return request;
    }

    protected IGatewayContext buildGatewayContext(ApiRequest<?> request, IHttpServerContext context) {
        GatewayContextImpl svcCtx = new GatewayContextImpl();
        svcCtx.setRequest(request);
        svcCtx.setRequestPath(context.getRequestPath());
        svcCtx.setRequestHeaders(request.getHeaders());
        svcCtx.setQueryParams(context.getQueryParams());
        return svcCtx;
    }
}
