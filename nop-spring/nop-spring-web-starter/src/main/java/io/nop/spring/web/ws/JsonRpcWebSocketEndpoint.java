/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.spring.web.ws;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.auth.IUserContextExtractor;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.ApiHeaders;
import io.nop.core.model.selection.FieldSelectionBeanParser;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.graphql.core.jsonrpc.JsonRpcRequest;
import io.nop.graphql.core.ws.JsonRpcWebSocketHandler;
import io.nop.graphql.core.ws.IWebSocketHandler;
import io.nop.http.api.server.IClientIpFetcher;
import io.nop.spring.web.filter.ServletHttpServerContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.function.BiFunction;

@Component
@ServerEndpoint(value = "/ws/jsonrpc")
@ConditionalOnProperty(name = "nop.spring.jsonrpc-websocket.enabled", havingValue = "true", matchIfMissing = true)
public class JsonRpcWebSocketEndpoint extends Endpoint {

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        IGraphQLEngine engine = BeanContainer.instance().getBeanByType(IGraphQLEngine.class);

        Map<String, Object> initialHeaders = extractInitialHeaders(session, config);

        BiFunction<JsonRpcRequest, Map<String, Object>, Flow.Publisher<ApiResponse<?>>> executionService =
                (request, headers) -> {
                    ApiRequest<?> apiRequest = toApiRequest(request);
                    IGraphQLExecutionContext context = engine.newRpcContext(
                        GraphQLOperationType.subscription,
                        request.getMethod(),
                        apiRequest
                    );
                    if (headers != null && !headers.isEmpty()) {
                        Map<String, Object> mergedHeaders = new HashMap<>(context.getRequestHeaders());
                        mergedHeaders.putAll(headers);
                        context.setRequestHeaders(mergedHeaders);
                    }
                    return engine.subscribeRpc(context);
                };

        SpringWebSocketSession wsSession = new SpringWebSocketSession(session);
        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler(executionService, wsSession, initialHeaders);

        // 设置用户上下文提取器
        IUserContextExtractor loginService = BeanContainer.instance().tryGetBeanByType(IUserContextExtractor.class);
        handler.setUserContextExtractor(loginService);

        session.getUserProperties().put("graphql.handler", handler);

        session.addMessageHandler(String.class, handler::onMessage);
    }

    private ApiRequest<?> toApiRequest(JsonRpcRequest request) {
        ApiRequest<Object> apiRequest = new ApiRequest<>();
        apiRequest.setData(request.getParams());
        apiRequest.setSelection(request.getSelection() != null ? 
            new FieldSelectionBeanParser().parseFromText(null, request.getSelection()) : null);
        return apiRequest;
    }

    protected Map<String, Object> extractInitialHeaders(Session session, EndpointConfig config) {
        Map<String, Object> headers = new HashMap<>();

        Map<String, Object> userProperties = config.getUserProperties();
        for (Map.Entry<String, Object> entry : userProperties.entrySet()) {
            String key = entry.getKey();
            if (key != null && entry.getValue() instanceof String) {
                headers.put(key.toLowerCase(), entry.getValue());
            }
        }

        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                Enumeration<String> headerNames = request.getHeaderNames();
                while (headerNames.hasMoreElements()) {
                    String name = headerNames.nextElement();
                    String value = request.getHeader(name);
                    if (value != null) {
                        headers.putIfAbsent(name.toLowerCase(), value);
                    }
                }

                String clientAddr = null;
                IClientIpFetcher ipFetcher = BeanContainer.instance().tryGetBeanByType(IClientIpFetcher.class);
                if (ipFetcher != null) {
                    ServletHttpServerContext httpCtx = new ServletHttpServerContext(request, null);
                    clientAddr = ipFetcher.getClientRealAddr(httpCtx);
                }
                if (clientAddr != null) {
                    ApiHeaders.setHeader(headers, ApiConstants.HEADER_CLIENT_ADDR, clientAddr);
                }
            }
        } catch (Exception e) {
        }

        return headers;
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        IWebSocketHandler handler = (IWebSocketHandler) session.getUserProperties().get("graphql.handler");
        if (handler != null) {
            handler.onClose();
        }
    }

    @Override
    public void onError(Session session, Throwable thr) {
        IWebSocketHandler handler = (IWebSocketHandler) session.getUserProperties().get("graphql.handler");
        if (handler != null) {
            handler.onThrowable(thr);
        }
    }
}
