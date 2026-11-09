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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
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

    private static final int KEEP_ALIVE_INTERVAL_SECONDS = 10;

    /**
     * A5 keepalive fan-out convergence (plan 2498 Phase 3, finding 2.3-C8-001): live
     * handlers registered for the shared keepalive scan. Sessions whose close callback was
     * missed are evicted lazily by the scan itself (self-cleaning), so the registry cannot
     * leak.
     */
    private static final Set<JsonRpcWebSocketHandler> LIVE_HANDLERS = ConcurrentHashMap.newKeySet();

    /**
     * The single shared keepalive chain, armed once per JVM. Trigger = shared globalTimer
     * (O(1) per connection count); body = hops to the shared worker pool via
     * {@code executeOn} (platform scheduling-slot contract), and each ping is further
     * enqueued into the target session's bounded send pump, so no timer thread ever
     * executes a blocking send and one slow client cannot delay other sessions.
     */
    private static volatile Future<?> sharedKeepAliveChain;

    protected final BiFunction<JsonRpcRequest, Map<String, Object>, Flow.Publisher<ApiResponse<?>>> executionService;
    protected final IWebSocketSession session;
    protected final Map<String, Flow.Subscriber<ApiResponse<?>>> activeOperations = new ConcurrentHashMap<>();

    /**
     * This session's participation handle in the shared keepalive chain (A5 convergence,
     * plan 2498 Phase 3): the old per-connection globalTimer periodic task was replaced by
     * one O(1) shared scan, so this future no longer is a timer task — it represents the
     * session's registration in {@link #LIVE_HANDLERS} and is cancelled when the session
     * leaves the chain.
     */
    protected final Future<?> keepAliveSender;

    /**
     * Per-session bounded, order-preserving send pump draining on the shared worker pool
     * (2.3-C8-001): blocking sends never run on the publishing thread or the timer, and a
     * slow client overflows into a session disconnect instead of pinning platform carriers.
     */
    protected final WebSocketSendPump sendPump;

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
        this.sendPump = new WebSocketSendPump(session, WebSocketSendPump.DEFAULT_CAPACITY);
        this.keepAliveSender = registerKeepAlive(this);
    }

    public void setMaxActiveOperations(int maxActiveOperations) {
        this.maxActiveOperations = maxActiveOperations;
    }

    /**
     * 设置用户上下文提取器。端点（quarkus/spring）在构造handler之后注入extractor，
     * 注入时立即绑定连接身份，否则boundUserId恒为null：tokenRefresh的用户一致性校验（4403）
     * 与令牌有效性校验（4401）全部不可达，客户端可把会话authHeaders替换为任意用户的令牌。
     */
    public void setUserContextExtractor(IUserContextExtractor userContextExtractor) {
        this.userContextExtractor = userContextExtractor;
        bindUserIdentityIfNeeded();
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
        unregisterKeepAlive();
    }

    @Override
    public void onClose() {
        LOG.debug("nop.websocket.session-closed:{}", session);
        activeOperations.forEach((id, operation) -> cancelOperation(id));
        if (!session.isClosed()) {
            session.close((short) 1000, "");
        }
        unregisterKeepAlive();
        sendPump.close();
    }

    /**
     * A5 keepalive fan-out convergence (plan 2498 Phase 3, finding 2.3-C8-001): registers
     * the handler in the shared scan registry and arms the single shared keepalive chain
     * once per JVM. The chain is
     * {@code globalTimer().executeOn(globalWorker()).scheduleWithFixedDelay(...)} — timer
     * triggers only, the scan body and every send run on the shared worker pool. N
     * connections therefore add O(1) timer tasks, not N.
     */
    private static Future<?> registerKeepAlive(JsonRpcWebSocketHandler handler) {
        LIVE_HANDLERS.add(handler);
        if (sharedKeepAliveChain == null) {
            synchronized (JsonRpcWebSocketHandler.class) {
                if (sharedKeepAliveChain == null) {
                    sharedKeepAliveChain = GlobalExecutors.globalTimer()
                            .executeOn(GlobalExecutors.globalWorker())
                            .scheduleWithFixedDelay(JsonRpcWebSocketHandler::sendKeepAliveToAll,
                                    KEEP_ALIVE_INTERVAL_SECONDS, KEEP_ALIVE_INTERVAL_SECONDS, TimeUnit.SECONDS);
                }
            }
        }
        // registration handle: cancelled when the session leaves the chain (see unregisterKeepAlive)
        return new CompletableFuture<Void>();
    }

    private void unregisterKeepAlive() {
        LIVE_HANDLERS.remove(this);
        Future<?> handle = keepAliveSender;
        if (handle != null) {
            handle.cancel(false);
        }
    }

    /**
     * The single shared keepalive round: evict closed sessions lazily and enqueue one ping
     * per live session. Enqueue-only by design — a slow session's ping sits in its own
     * bounded pump, so it can never delay other sessions' keepalives.
     */
    static void sendKeepAliveToAll() {
        for (JsonRpcWebSocketHandler handler : LIVE_HANDLERS) {
            if (handler.session.isClosed()) {
                LIVE_HANDLERS.remove(handler);
                continue;
            }
            handler.sendKeepAlive();
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
            // 2.3-C3-006（plan 2550 裁定 D）分支三：IOException = 连接级故障（关连接语义）
            LOG.warn("nop.websocket.on-message-error", e);
        } catch (Throwable e) {
            // 分支三续：同步 RuntimeException 不穿透——按 keepAlive 绝缘同型（R1-01 契约）
            // 结构化日志留痕，handler 与下一条消息存活，连接不因单条消息处理异常被杀。
            LOG.error("nop.err.websocket.on-message-fail:method={}", method, e);
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
                    LOG.warn("nop.websocket.token-refresh-auth-failed:boundUser={}", boundUserId, e);
                    session.close((short) 4401, "Token refresh failed");
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
            LOG.warn("nop.websocket.auth-failed:session={}", session, e);
            session.close((short) 4401, "Authentication failed");
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
        // 必须先注册再subscribe：同步publisher会在subscribe调用内同步触发onComplete/onError，
        // 若put在后，清理先于注册执行（remove空跑），滞留条目导致id复用被4409拒绝。
        // 2.3-C1-005（plan 2550 裁定 A）：validSubscription 的 containsKey 检查与注册之间存在
        // 竞态窗——裸 put 会让并发同 id 注册覆盖首订阅（首订阅的清理路径失效）。putIfAbsent
        // 保证首订阅唯一所有权；后来者放弃自建 subscriber（冷 publisher 未 subscribe 即未执行，
        // 无资源回收面），与 validSubscription 的 4409 终态机协同。
        Flow.Subscriber<ApiResponse<?>> existing = activeOperations.putIfAbsent(operationId, subscriber);
        if (existing != null) {
            LOG.warn("nop.websocket.subscription-race:opId={}", operationId);
            return;
        }
        stream.subscribe(subscriber);
    }

    /**
     * Best-effort keepalive round (CRA 30-point table row #2, finding 2.1-C3-015). The old narrow
     * {@code catch (IOException)} let any serialization RuntimeException escape the periodic task body
     * on the shared globalTimer and kill the keepalive chain. Per the periodic-task insulation contract
     * (ai-dev/design/concurrency/periodic-task-insulation.md), a failed round must stay observable and
     * must never terminate the chain: catch Throwable at site level, log with a structured key, and let
     * the next scheduled round proceed. The failure is logged, not swallowed silently.
     *
     * <p>2.3-C8-001 (plan 2498 Phase 3): the ping is enqueued into this session's bounded
     * send pump and written by the shared worker pool — never on the timer/scan thread. The
     * site-level Throwable guard (R1-01 insulation) is preserved over the enqueue path.
     */
    protected void sendKeepAlive() {
        try {
            sendPump.submit(() -> {
                try {
                    session.sendMessage(createNotification(METHOD_PING));
                } catch (IOException e) {
                    LOG.warn("nop.websocket.send-keep-alive-fail", e);
                } catch (Throwable e) {
                    LOG.error("nop.err.websocket.keep-alive-fail:session={}", session, e);
                }
            });
        } catch (Throwable e) {
            // enqueue path itself failed (overflow disconnect / closed session are reported via
            // the return value; a Throwable here is a pump defect): keep the insulation contract —
            // observable, never propagated out of the periodic body
            LOG.error("nop.err.websocket.keep-alive-fail:session={}", session, e);
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
                // 2.3-C8-001 (plan 2498 Phase 3): the blocking send moves off the publisher
                // thread into the session's bounded send pump (shared worker pool). Overflow
                // (slow consumer) disconnects this session instead of back-pressuring shared
                // platform carriers.
                sendPump.submit(() -> {
                    try {
                        if (!executionResult.isOk()) {
                            sendErrorFromGraphQL(operationId, executionResult);
                        } else {
                            session.sendMessage(createDataMessage(operationId, executionResult));
                        }
                    } catch (Exception e) {
                        LOG.warn("nop.websocket.send-next-fail", e);
                    }
                });
                subscription.get().request(1);
            } else {
                // 2.3-C3-006（plan 2550 裁定 D）分支二：session-closed 不再只静默——取消上游
                // 订阅（SSE/执行链在会话关闭后停止推送，否则上游执行至 EOF 白耗资源）。
                LOG.debug("nop.websocket.ignore-response:cancel-upstream:opId={}", operationId);
                cancel();
            }
        }

        @Override
        public void onError(Throwable t) {
            LOG.error("nop.websocket.error", t);
            // 2.3-C3-006（plan 2550 裁定 D）分支一：上游错误必须向客户端发终止信号
            // （jsonrpc error 帧）后再关流——不发则客户端无从感知订阅已死（悬挂等待）。
            // 错误帧经同一 sendPump 保持会话内顺序；发送失败按既有 warn 语义，不影响清理。
            sendPump.submit(() -> {
                try {
                    sendError(operationId, JsonRpcWebSocketErrorCodes.INTERNAL_ERROR,
                            t.getMessage() == null ? t.toString() : t.getMessage());
                } catch (Exception e) {
                    LOG.warn("nop.websocket.send-error-fail", e);
                }
            });
            // 订阅已终止，条目必须清理，否则id复用会触发4409断开整个连接，且滞留条目耗尽maxActiveOperations
            activeOperations.remove(operationId, this);
        }

        @Override
        public void onComplete() {
            if (LOG.isTraceEnabled()) {
                LOG.trace("nop.websocket.subscription-completed:opId={}", operationId);
            }

            // complete 消息与数据消息共用同一 pump，保持会话内顺序（2.3-C8-001 卸载）
            sendPump.submit(() -> {
                try {
                    session.sendMessage(createCompleteMessage(operationId));
                } catch (Exception e) {
                    LOG.warn("nop.websocket.send-fail", e);
                }
            });
            // 使用双参remove防止误删并发重订阅的新条目
            activeOperations.remove(operationId, this);
        }

        public void cancel() {
            Flow.Subscription sub = subscription.get();
            if (sub != null) {
                sub.cancel();
            }
        }
    }
}
