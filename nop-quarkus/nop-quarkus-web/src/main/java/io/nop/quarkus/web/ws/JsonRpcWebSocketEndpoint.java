/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.quarkus.web.ws;

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
import io.nop.http.api.server.IHttpServerContext;
import io.nop.quarkus.web.utils.QuarkusExecutorHelper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.function.BiFunction;

@ApplicationScoped
@ServerEndpoint("/ws/jsonrpc")
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

        JakartaWebSocketSession wsSession = new JakartaWebSocketSession(session);
        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler(executionService, wsSession, initialHeaders);

        // 设置用户上下文提取器
        IUserContextExtractor userContextExtractor = BeanContainer.instance().getBeanByType(IUserContextExtractor.class);
        handler.setUserContextExtractor(userContextExtractor);


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

        Map<String, List<String>> handshakeHeaders = (Map<String, List<String>>) config.getUserProperties()
                .get("org.jboss.resteasy.websocket.handshake.headers");
        
        if (handshakeHeaders != null) {
            for (Map.Entry<String, List<String>> entry : handshakeHeaders.entrySet()) {
                List<String> values = entry.getValue();
                if (values != null && !values.isEmpty()) {
                    headers.put(entry.getKey().toLowerCase(), values.get(0));
                }
            }
        }

        Map<String, List<String>> requestHeaders = (Map<String, List<String>>) config.getUserProperties()
                .get("io.quarkus.websocket.headers");
        if (requestHeaders != null) {
            for (Map.Entry<String, List<String>> entry : requestHeaders.entrySet()) {
                List<String> values = entry.getValue();
                if (values != null && !values.isEmpty()) {
                    headers.putIfAbsent(entry.getKey().toLowerCase(), values.get(0));
                }
            }
        }

        try {
            IHttpServerContext httpCtx = QuarkusExecutorHelper.getHttpServerContext();
            if (httpCtx != null) {
                String clientAddr = null;
                IClientIpFetcher ipFetcher = BeanContainer.instance().tryGetBeanByType(IClientIpFetcher.class);
                if (ipFetcher != null) {
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
