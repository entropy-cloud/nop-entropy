/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ws;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.auth.IUserContextExtractor;
import io.nop.api.core.exceptions.NopLoginException;
import io.nop.api.core.json.JSON;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.model.selection.FieldSelectionBeanParser;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.graphql.core.jsonrpc.JsonRpcRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * WebSocket subprotocol handler that implements JSON-RPC 2.0 compatible format for GraphQL subscriptions.
 * Uses method as operationName (BizObjName__bizAction format). Auth token is passed via HTTP headers.
 */
public class JsonRpcWebSocketHandler implements IWebSocketHandler {
    protected final Logger LOG = LoggerFactory.getLogger(JsonRpcWebSocketHandler.class);

    public static final String JSONRPC_VERSION = "2.0";

    private static final String METHOD_PING = "ping";
    private static final String METHOD_PONG = "pong";
    private static final String METHOD_UNSUBSCRIBE = "unsubscribe";
    private static final String METHOD_TOKEN_REFRESH = "tokenRefresh";

    protected final BiFunction<JsonRpcRequest, Map<String, Object>, Flow.Publisher<ApiResponse<?>>> executionService;
    protected final IWebSocketSession session;
    protected final Map<String, Flow.Subscriber<ApiResponse<?>>> activeOperations = new ConcurrentHashMap<>();
    protected final Future<?> keepAliveSender;

    private int maxActiveOperations = 1000;
    protected volatile Map<String, Object> authHeaders;
    protected volatile String boundUserId;
    protected volatile String boundSessionId;

    /**
     * 用于从headers中提取用户上下文
     */
    protected IUserContextExtractor userContextExtractor;

    public JsonRpcWebSocketHandler(
            BiFunction<JsonRpcRequest, Map<String, Object>, Flow.Publisher<ApiResponse<?>>> executionService,
            IWebSocketSession session) {
        this(executionService, session, null);
    }

    public JsonRpcWebSocketHandler(
            BiFunction<JsonRpcRequest, Map<String, Object>, Flow.Publisher<ApiResponse<?>>> executionService,
            IWebSocketSession session,
            Map<String, Object> initialHeaders) {
        this.executionService = executionService;
        this.session = session;
        this.authHeaders = initialHeaders != null ? new ConcurrentHashMap<>(initialHeaders) : new ConcurrentHashMap<>();
        this.keepAliveSender = GlobalExecutors.globalTimer().scheduleWithFixedDelay(this::sendKeepAlive, 10, 10,
                TimeUnit.SECONDS);
        
        bindUserIdentityIfNeeded();
    }

    public void setMaxActiveOperations(int maxActiveOperations) {
        this.maxActiveOperations = maxActiveOperations;
    }

    /**
     * 设置用户上下文提取器
     */
    public void setUserContextExtractor(IUserContextExtractor userContextExtractor) {
        this.userContextExtractor = userContextExtractor;
    }

    @Override
    public void onMessage(String text) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("<<< " + text);
        }
        JsonRpcRequest request = parseMessage(text);
        if (request != null) {
            onMessage(request);
        }
    }

    @Override
    public void onThrowable(Throwable t) {
        LOG.warn("Error in websocket", t);
        if (keepAliveSender != null) {
            keepAliveSender.cancel(false);
        }
    }

    @Override
    public void onClose() {
        LOG.debug("nop.websocket.session-closed:{}", session);
        activeOperations.forEach((id, operation) -> cancelOperation(id));
        if (!session.isClosed()) {
            session.close((short) 1000, "");
        }
        if (keepAliveSender != null) {
            keepAliveSender.cancel(false);
        }
    }

    protected void onMessage(JsonRpcRequest request) {
        String method = request.getMethod();
        String id = request.getId();

        if (method == null) {
            try {
                sendError(id, JsonRpcWebSocketErrorCodes.INVALID_REQUEST, "Missing 'method' field");
            } catch (IOException e) {
                LOG.warn("nop.websocket.send-error-fail", e);
            }
            return;
        }

        try {
            switch (method) {
                case METHOD_PING:
                    handlePing(id);
                    break;
                case METHOD_PONG:
                    break;
                case METHOD_UNSUBSCRIBE:
                    handleUnsubscribe(request);
                    break;
                case METHOD_TOKEN_REFRESH:
                    handleTokenRefresh(request);
                    break;
                default:
                    handleSubscription(request);
            }
        } catch (IOException e) {
            LOG.warn("nop.websocket.on-message-error", e);
        }
    }

    private void handlePing(String id) throws IOException {
        if (id != null) {
            session.sendMessage(createSuccessResponse(id, Map.of("pong", true)));
        } else {
            session.sendMessage(createNotification("pong"));
        }
    }

    private void handleSubscription(JsonRpcRequest request) throws IOException {
        String id = request.getId();
        String method = request.getMethod();

        if (id == null) {
            sendError(null, JsonRpcWebSocketErrorCodes.INVALID_REQUEST, "Subscription request must have 'id' field");
            return;
        }

        if (!validSubscription(id, method)) {
            return;
        }

        request.mergeHeaders(authHeaders);
        Flow.Publisher<ApiResponse<?>> stream = executionService.apply(request, authHeaders);
        sendStreamingMessage(id, stream);
    }

    @SuppressWarnings("unchecked")
    private void handleUnsubscribe(JsonRpcRequest request) throws IOException {
        String id = request.getId();
        String subscriptionId = null;
        if (request.getParams() instanceof Map) {
            subscriptionId = (String) ((Map<String, Object>) request.getParams()).get("id");
        }

        if (subscriptionId == null) {
            subscriptionId = id;
        }

        if (subscriptionId == null) {
            sendError(id, JsonRpcWebSocketErrorCodes.INVALID_PARAMS, "Missing subscription 'id'");
            return;
        }

        boolean cancelled = cancelOperation(subscriptionId);
        if (id != null) {
            session.sendMessage(createSuccessResponse(id, Map.of("cancelled", cancelled)));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleTokenRefresh(JsonRpcRequest request) throws IOException {
        String id = request.getId();
        if (request.getParams() instanceof Map) {
            Map<String, Object> payload = (Map<String, Object>) request.getParams();
            Map<String, Object> newHeaders = extractAuthHeaders(payload);

            Map<String, Object> mergedHeaders = new HashMap<>(this.authHeaders);
            if (!newHeaders.isEmpty()) {
                mergedHeaders.putAll(newHeaders);
            }

            // 使用 IUserContextExtractor 接口验证刷新后的 token
            if (userContextExtractor != null && boundUserId != null) {
                try {
                    IUserContext userContext = userContextExtractor.extractFromHeaders(mergedHeaders);
                    if (userContext == null) {
                        LOG.info("nop.websocket.token-refresh-no-auth:boundUser={}", boundUserId);
                        session.close((short) 4401, "Authentication required");
                        return;
                    }
                    String newUserId = userContext.getUserId();
                    if (newUserId != null && !boundUserId.equals(newUserId)) {
                        LOG.warn("nop.websocket.token-refresh-user-mismatch:boundUser={},newUser={}",
                                boundUserId, newUserId);
                        session.close((short) 4403, "Token user mismatch, please re-login");
                        return;
                    }
                } catch (NopLoginException e) {
                    LOG.warn("nop.websocket.token-refresh-auth-failed:boundUser={},error={}",
                            boundUserId, e.getMessage());
                    session.close((short) 4401, "Token refresh failed: " + e.getMessage());
                    return;
                }
            }

            if (!newHeaders.isEmpty()) {
                this.authHeaders = mergedHeaders;
            }
        }

        if (id != null) {
            session.sendMessage(createSuccessResponse(id, Map.of("refreshed", true)));
        }
    }

    private Map<String, Object> extractAuthHeaders(Map<String, Object> payload) {
        Map<String, Object> newHeaders = new HashMap<>();

        Object headersObj = payload.get("headers");
        if (headersObj instanceof Map) {
            Map<?, ?> headersMap = (Map<?, ?>) headersObj;
            for (Map.Entry<?, ?> entry : headersMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    newHeaders.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }

        Object authToken = payload.get("authToken");
        if (authToken != null) {
            newHeaders.put(io.nop.api.core.ApiConstants.HEADER_AUTHORIZATION, "Bearer " + authToken);
        }

        Object accessToken = payload.get("accessToken");
        if (accessToken != null) {
            newHeaders.put(io.nop.api.core.ApiConstants.HEADER_ACCESS_TOKEN, String.valueOf(accessToken));
        }

        return newHeaders;
    }

    /**
     * 绑定用户身份。使用 {@link #userContextExtractor} 从 headers 中提取用户上下文。
     * <p>
     * 如果 userContextExtractor 返回 null，表示没有认证信息，打印日志并关闭连接。
     * 如果抛出 NopLoginException，表示认证失败，打印日志并关闭连接。
     */
    private void bindUserIdentityIfNeeded() {
        if (boundUserId != null)
            return;

        if (userContextExtractor == null)
            return;

        try {
            IUserContext userContext = userContextExtractor.extractFromHeaders(authHeaders);
            if (userContext == null) {
                LOG.info("nop.websocket.no-auth-info:session={}", session);
                session.close((short) 4401, "Authentication required");
                return;
            }
            bindUserFromContext(userContext);
        } catch (NopLoginException e) {
            LOG.warn("nop.websocket.auth-failed:session={},error={}", session, e.getMessage());
            session.close((short) 4401, "Authentication failed: " + e.getMessage());
        }
    }

    /**
     * 从 IUserContext 绑定用户信息
     */
    private void bindUserFromContext(IUserContext userContext) {
        if (userContext != null) {
            this.boundUserId = userContext.getUserId();
            this.boundSessionId = userContext.getSessionId();
            LOG.debug("nop.websocket.user-bound:userId={},sessionId={}", boundUserId, boundSessionId);
        }
    }

    private boolean validSubscription(String operationId, String method) throws IOException {
        if (activeOperations.size() >= maxActiveOperations) {
            LOG.warn("nop.websocket.too-many-active-operations:opId={}", operationId);
            sendError(operationId, JsonRpcWebSocketErrorCodes.TOO_MANY_SUBSCRIPTIONS, "Too many active subscriptions");
            return false;
        }

        if (activeOperations.containsKey(operationId)) {
            sendError(operationId, JsonRpcWebSocketErrorCodes.SUBSCRIPTION_EXISTS, "Subscription already exists: " + operationId);
            session.close((short) 4409, "Subscriber for " + operationId + " already exists");
            return false;
        }
        return true;
    }

    private boolean cancelOperation(String operationId) {
        Flow.Subscriber<?> subscriber = activeOperations.remove(operationId);
        if (subscriber != null) {
            if (subscriber instanceof SubscriptionSubscriber) {
                ((SubscriptionSubscriber) subscriber).cancel();
            }
            return true;
        }
        return false;
    }

    private void sendStreamingMessage(String operationId, Flow.Publisher<ApiResponse<?>> stream) {
        SubscriptionSubscriber subscriber = new SubscriptionSubscriber(session, operationId);
        activeOperations.put(operationId, subscriber);
        stream.subscribe(subscriber);
    }

    private void sendKeepAlive() {
        try {
            session.sendMessage(createNotification("ping"));
        } catch (IOException e) {
            LOG.warn("nop.websocket.send-keep-alive-fail", e);
        }
    }

    private JsonRpcRequest parseMessage(String text) {
        if (StringHelper.isEmpty(text)) {
            return null;
        }
        try {
            return JsonTool.parseBeanFromText(text, JsonRpcRequest.class);
        } catch (Exception ex) {
            LOG.info("nop.err.graphql.parse-json-fail", ex);
            ErrorBean error = ErrorMessageManager.instance().buildErrorMessage(null,ex);
            session.close((short) 4400, JsonTool.stringify(error));
            return null;
        }
    }

    private void sendError(String id, int code, String message) throws IOException {
        session.sendMessage(createErrorResponse(id, code, message, null, null));
    }

    private void sendErrorFromGraphQL(String operationId, ApiResponse<?> response) throws IOException {
        session.sendMessage(createErrorResponse(operationId, JsonRpcWebSocketErrorCodes.INTERNAL_ERROR,
                response.getMsg(), response.getCode(), response.getData()));
    }

    private String createSuccessResponse(String id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", JSONRPC_VERSION);
        response.put("id", id);
        response.put("result", result);
        return JSON.stringify(response);
    }

    private String createErrorResponse(String id, int code, String message, String errorCode, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", JSONRPC_VERSION);
        if (id != null) {
            response.put("id", id);
        }

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (errorCode != null) {
            error.put("errorCode", errorCode);
        }
        if (data != null) {
            error.put("data", data);
        }
        response.put("error", error);
        return JSON.stringify(response);
    }

    private String createDataMessage(String operationId, ApiResponse<?> response) {
        Object payload = response.getData();
        Map<String, Object> responseMap = new LinkedHashMap<>();
        responseMap.put("jsonrpc", JSONRPC_VERSION);
        responseMap.put("id", operationId);
        responseMap.put("result", payload);
        return JSON.stringify(responseMap);
    }

    private String createCompleteMessage(String operationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", JSONRPC_VERSION);
        response.put("id", operationId);
        response.put("result", Map.of("complete", true));
        return JSON.stringify(response);
    }

    private String createNotification(String method) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", JSONRPC_VERSION);
        response.put("method", method);
        return JSON.stringify(response);
    }

    private class SubscriptionSubscriber implements Flow.Subscriber<ApiResponse<?>> {

        private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        private final IWebSocketSession session;
        private final String operationId;

        public SubscriptionSubscriber(IWebSocketSession session, String operationId) {
            this.session = session;
            this.operationId = operationId;
        }

        @Override
        public void onSubscribe(Flow.Subscription s) {
            subscription.set(s);
            subscription.get().request(1);
        }

        @Override
        public void onNext(ApiResponse<?> executionResult) {
            if (!session.isClosed()) {
                try {
                    if (!executionResult.isOk()) {
                        sendErrorFromGraphQL(operationId, executionResult);
                    } else {
                        session.sendMessage(createDataMessage(operationId, executionResult));
                    }
                } catch (Exception e) {
                    LOG.warn("nop.websocket.send-next-fail", e);
                }
                subscription.get().request(1);
            } else {
                LOG.debug("nop.websocket.ignore-response");
            }
        }

        @Override
        public void onError(Throwable t) {
            LOG.error("nop.websocket.error", t);
        }

        @Override
        public void onComplete() {
            if (LOG.isTraceEnabled()) {
                LOG.trace("nop.websocket.subscription-completed:opId={}", operationId);
            }

            try {
                session.sendMessage(createCompleteMessage(operationId));
            } catch (Exception e) {
                LOG.warn("nop.websocket.send-fail", e);
            }
            activeOperations.remove(operationId);
        }

        public void cancel() {
            Flow.Subscription sub = subscription.get();
            if (sub != null) {
                sub.cancel();
            }
        }
    }
}
