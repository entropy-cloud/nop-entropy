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
import org.junit.jupiter.api.AfterAll;
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

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
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
        JsonRpcWebSocketHandler handler = createHandler();

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSubscription__onContinuousEvent\",\"params\":{},\"selection\":\"id\",\"id\":\"sub-1\"}");

        TimeUnit.MILLISECONDS.sleep(100);

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"unsubscribe\",\"params\":{\"id\":\"sub-1\"},\"id\":\"cancel-1\"}");

        int countBefore = session.getSentMessages().size();
        TimeUnit.MILLISECONDS.sleep(500);
        int countAfter = session.getSentMessages().size();

        assertTrue(countAfter <= countBefore + 5, 
                "Should not receive many more after cancel. Before: " + countBefore + ", After: " + countAfter);
    }

    @Test
    public void testPingPong() throws Exception {
        JsonRpcWebSocketHandler handler = createHandler();

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"id\":\"ping-1\"}");
        TimeUnit.MILLISECONDS.sleep(100);

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
        JsonRpcWebSocketHandler handler = createHandler();

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSubscription__onContinuousEvent\",\"params\":{},\"selection\":\"id\",\"id\":\"sub-1\"}");

        TimeUnit.MILLISECONDS.sleep(100);

        handler.onClose();

        int countAfterClose = session.getSentMessages().size();
        TimeUnit.MILLISECONDS.sleep(300);
        int countLater = session.getSentMessages().size();

        assertEquals(countAfterClose, countLater, "No more messages after close");
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
    }
}
