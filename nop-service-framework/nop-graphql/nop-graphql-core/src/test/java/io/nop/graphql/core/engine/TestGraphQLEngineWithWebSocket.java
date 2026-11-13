/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.core.model.selection.FieldSelectionBeanParser;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.jsonrpc.JsonRpcRequest;
import io.nop.graphql.core.ws.JsonRpcWebSocketHandler;
import io.nop.graphql.core.ws.IWebSocketSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

public class TestGraphQLEngineWithWebSocket extends BaseTestCase {

    private GraphQLEngine engine;
    private MockWebSocketSession session;
    private AtomicReference<JsonRpcWebSocketHandler> handlerRef;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }


    @BeforeEach
    public void setUp() {
        engine = new GraphQLEngine();
        engine.setSchemaLoader(new SubscriptionMockSchemaLoader());
        engine.init();
        session = new MockWebSocketSession();
        handlerRef = new AtomicReference<>();
    }

    @AfterEach
    public void tearDown() {
        JsonRpcWebSocketHandler handler = handlerRef.get();
        if (handler != null) {
            handler.onClose();
        }
        // 时间确定化：复位 mock 流闸门，避免静态闩泄漏影响同 fork 后续测试
        releaseContinuousGate();
        SubscriptionMockSchemaLoader.TestSubscriptionBizModel.continuousGate = null;
        SubscriptionMockSchemaLoader.TestSubscriptionBizModel.continuousProducerDone = null;
        SubscriptionMockSchemaLoader.TestSubscriptionBizModel.testEventDone = null;
    }

    private void armContinuousGate() {
        SubscriptionMockSchemaLoader.TestSubscriptionBizModel.continuousGate = new CountDownLatch(1);
        SubscriptionMockSchemaLoader.TestSubscriptionBizModel.continuousProducerDone = new CountDownLatch(1);
        SubscriptionMockSchemaLoader.TestSubscriptionBizModel.testEventDone = new CountDownLatch(1);
    }

    private void releaseContinuousGate() {
        CountDownLatch gate = SubscriptionMockSchemaLoader.TestSubscriptionBizModel.continuousGate;
        if (gate != null)
            gate.countDown();
    }

    private int countMessages(String fragment) {
        int count = 0;
        for (String m : session.getSentMessages()) {
            if (m.contains(fragment))
                count++;
        }
        return count;
    }

    private JsonRpcWebSocketHandler createHandler() {
        BiFunction<JsonRpcRequest, Map<String, Object>, Flow.Publisher<ApiResponse<?>>> executionService =
                (request, headers) -> {
                    ApiRequest<?> apiRequest = toApiRequest(request);
                    
                    IGraphQLExecutionContext ctx = engine.newRpcContext(
                        GraphQLOperationType.subscription,
                        request.getMethod(),
                        apiRequest
                    );
                    if (headers != null) {
                        Map<String, Object> current = ctx.getRequestHeaders();
                        Map<String, Object> merged = current != null ? new HashMap<>(current) : new HashMap<>();
                        merged.putAll(headers);
                        ctx.setRequestHeaders(merged);
                    }
                    return engine.subscribeRpc(ctx);
                };

        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler(executionService, session);
        handlerRef.set(handler);
        return handler;
    }

    private ApiRequest<?> toApiRequest(JsonRpcRequest request) {
        ApiRequest<Object> apiRequest = new ApiRequest<>();
        apiRequest.setData(request.getParams());
        apiRequest.setSelection(request.getSelection() != null ? 
            new FieldSelectionBeanParser().parseFromText(null, request.getSelection()) : null);
        return apiRequest;
    }

    @Test
    public void testSubscriptionWithRealEngine() throws Exception {
        JsonRpcWebSocketHandler handler = createHandler();

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSubscription__onTestEvent\",\"params\":{\"filter\":\"test\"},\"selection\":\"id,message\",\"id\":\"sub-1\"}");

        boolean received = session.waitForMessages(1, 10000);
        List<String> messages = session.getSentMessages();
        assertTrue(received, "Should receive at least 1 message. Got: " + messages.size() + ", messages: " + messages);
        
        // RPC style: result contains data directly (not wrapped in "data" field)
        boolean hasNext = messages.stream().anyMatch(m -> m.contains("\"result\"") && (m.contains("\"id\"") || m.contains("\"message\"")));
        boolean hasComplete = messages.stream().anyMatch(m -> m.contains("\"complete\":true"));
        boolean hasError = messages.stream().anyMatch(m -> m.contains("\"error\""));
        
        assertTrue(hasNext || hasComplete || hasError, 
                "Should have result, complete or error message. hasNext=" + hasNext + ", hasComplete=" + hasComplete + ", hasError=" + hasError);
    }

    @Test
    public void testSubscriptionWithVariables() throws Exception {
        JsonRpcWebSocketHandler handler = createHandler();

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSubscription__onTestEvent\",\"params\":{\"filter\":\"my-filter\"},\"selection\":\"id,message\",\"id\":\"sub-1\"}");

        boolean received = session.waitForMessages(1, 10000);
        List<String> messages = session.getSentMessages();
        assertTrue(received, "Should receive at least 1 message. Got: " + messages.size());
    }

    @Test
    public void testContinuousSubscription() throws Exception {
        JsonRpcWebSocketHandler handler = createHandler();

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSubscription__onContinuousEvent\",\"params\":{},\"selection\":\"id\",\"id\":\"sub-1\"}");

        boolean received = session.waitForMessages(1, 10000);
        List<String> messages = session.getSentMessages();
        assertTrue(received, "Should receive at least 1 message. Got: " + messages.size());
    }

    @Test
    public void testUnsubscribe() throws Exception {
        // 时间确定化（plan 2513-WI7）：continuous 流为闸门控制的有限流——首条事件证明订阅建立，
        // unsubscribe 的 ack 证明取消已完成（onMessage 同步处理），放行闸后生产者提交的剩余
        // 事件发生在订阅取消之后，被 Flow 协议确定性丢弃；不再使用墙钟观察窗。
        armContinuousGate();
        JsonRpcWebSocketHandler handler = createHandler();

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSubscription__onContinuousEvent\",\"params\":{},\"selection\":\"id\",\"id\":\"sub-1\"}");

        assertTrue(session.waitForMessageContaining("continuous_0", 10000),
                "first continuous event must be delivered");

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"unsubscribe\",\"params\":{\"id\":\"sub-1\"},\"id\":\"cancel-1\"}");

        assertTrue(session.waitForMessageContaining("\"cancelled\":true", 10000),
                "unsubscribe must be answered synchronously with a cancel ack, messages="
                        + session.getSentMessages());

        // 取消已完成后再放行：后续提交全部被丢弃
        releaseContinuousGate();
        assertTrue(SubscriptionMockSchemaLoader.TestSubscriptionBizModel.continuousProducerDone
                .await(10, TimeUnit.SECONDS), "gated producer must finish after release");

        int events = countMessages("continuous_");
        assertEquals(1, events, "no events must be forwarded after unsubscribe, messages=" + session.getSentMessages());
    }

    @Test
    public void testPingPong() throws Exception {
        JsonRpcWebSocketHandler handler = createHandler();

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"id\":\"ping-1\"}");
        assertTrue(session.waitForMessageContaining("\"pong\":true", 10000), "pong must be delivered");

        List<String> messages = session.getSentMessages();
        boolean hasPong = messages.stream().anyMatch(m -> m.contains("\"pong\":true"));
        assertTrue(hasPong, "Should respond with pong");
    }

    @Test
    public void testMultipleSubscriptions() throws Exception {
        JsonRpcWebSocketHandler handler = createHandler();

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSubscription__onTestEvent\",\"params\":{\"filter\":\"a\"},\"selection\":\"id\",\"id\":\"sub-a\"}");

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSubscription__onContinuousEvent\",\"params\":{},\"selection\":\"id\",\"id\":\"sub-b\"}");

        boolean received = session.waitForMessages(2, 10000);
        List<String> messages = session.getSentMessages();
        assertTrue(received, "Should receive messages from both. Got: " + messages.size());

        boolean hasSubA = messages.stream().anyMatch(m -> m.contains("\"id\":\"sub-a\""));
        boolean hasSubB = messages.stream().anyMatch(m -> m.contains("\"id\":\"sub-b\""));
        
        assertTrue(hasSubA || hasSubB, "Should have messages for at least one subscription");
    }

    @Test
    public void testOnClose() throws Exception {
        // 时间确定化（plan 2513-WI7）：onClose 同步取消全部订阅并关闭会话；放行闸后生产者
        // 提交的剩余事件发生在取消之后，被 Flow 协议确定性丢弃，不再使用墙钟观察窗。
        armContinuousGate();
        JsonRpcWebSocketHandler handler = createHandler();

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSubscription__onContinuousEvent\",\"params\":{},\"selection\":\"id\",\"id\":\"sub-1\"}");

        assertTrue(session.waitForMessageContaining("continuous_0", 10000),
                "first continuous event must be delivered");

        handler.onClose();
        assertTrue(session.isClosed(), "handler onClose must close the session synchronously");

        releaseContinuousGate();
        assertTrue(SubscriptionMockSchemaLoader.TestSubscriptionBizModel.continuousProducerDone
                .await(10, TimeUnit.SECONDS), "gated producer must finish after release");

        int events = countMessages("continuous_");
        assertEquals(1, events, "No more messages after close, messages=" + session.getSentMessages());
    }

    private static class MockWebSocketSession implements IWebSocketSession {
        private final List<String> sentMessages = new ArrayList<>();
        private boolean closed = false;
        private short closeCode = 0;
        private final Object lock = new Object();

        @Override
        public void sendMessage(String message) throws IOException {
            if (closed) {
                throw new IOException("Session is closed");
            }
            synchronized (lock) {
                sentMessages.add(message);
                lock.notifyAll();
            }
        }

        @Override
        public void close(short statusCode, String reason) {
            this.closed = true;
            this.closeCode = statusCode;
            synchronized (lock) {
                lock.notifyAll();
            }
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        public List<String> getSentMessages() {
            synchronized (lock) {
                return new ArrayList<>(sentMessages);
            }
        }

        public short getCloseCode() {
            return closeCode;
        }

        public boolean waitForMessages(int minCount, long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            synchronized (lock) {
                while (sentMessages.size() < minCount && !closed && System.currentTimeMillis() < deadline) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) break;
                    lock.wait(remaining);
                }
                return sentMessages.size() >= minCount;
            }
        }

        /**
         * 时间确定化（plan 2513-WI7）：keepAlive 会周期性发送 ping 消息，按条数等待会被
         * ping 噪声提前满足；等待「包含指定片段的消息已到达」这一确定事实。
         */
        public boolean waitForMessageContaining(String fragment, long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            synchronized (lock) {
                while (!containsFragment(fragment) && System.currentTimeMillis() < deadline) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) break;
                    lock.wait(remaining);
                }
                return containsFragment(fragment);
            }
        }

        private boolean containsFragment(String fragment) {
            for (String m : sentMessages) {
                if (m.contains(fragment))
                    return true;
            }
            return false;
        }
    }
}
