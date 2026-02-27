/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ws;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.graphql.core.jsonrpc.JsonRpcRequest;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJsonRpcWebSocketHandler extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private MockWebSocketSession session;
    private BiFunction<JsonRpcRequest, Map<String, Object>, Flow.Publisher<ApiResponse<?>>> executionService;

    @BeforeEach
    public void setUp() {
        session = new MockWebSocketSession();
        executionService = (request, headers) -> {
            SubmissionPublisher<ApiResponse<?>> publisher = new SubmissionPublisher<>();
            ApiResponse<Map<String, Object>> response = ApiResponse.success(new HashMap<>());
            ((Map<String, Object>)response.getData()).put("testField", "testValue");
            publisher.submit(response);
            publisher.close();
            return publisher;
        };
    }

    @Test
    public void testSubscribeDirectly() throws Exception {
        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler(executionService, session);

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSubscription__onEvent\",\"params\":{},\"id\":\"sub-1\"}");

        Thread.sleep(100);

        List<String> messages = session.getSentMessages();
        assertFalse(messages.isEmpty(), "Should have received data messages");

        boolean foundDataMessage = false;
        boolean foundCompleteMessage = false;
        for (String msg : messages) {
            if (msg.contains("\"result\"") && msg.contains("\"data\"")) {
                foundDataMessage = true;
                assertTrue(msg.contains("\"jsonrpc\":\"2.0\""), "Data message should have jsonrpc version");
                assertTrue(msg.contains("\"id\":\"sub-1\""), "Data message should have correct id");
            }
            if (msg.contains("\"complete\":true")) {
                foundCompleteMessage = true;
                assertTrue(msg.contains("\"jsonrpc\":\"2.0\""), "Complete message should have jsonrpc version");
                assertTrue(msg.contains("\"id\":\"sub-1\""), "Complete message should have correct id");
            }
        }
        assertTrue(foundDataMessage || foundCompleteMessage, "Should have data or complete message");
    }

    @Test
    public void testSubscribeWithInitialHeaders() throws Exception {
        AtomicReference<Map<String, Object>> capturedHeaders = new AtomicReference<>();

        executionService = (request, headers) -> {
            capturedHeaders.set(headers);
            SubmissionPublisher<ApiResponse<?>> publisher = new SubmissionPublisher<>();
            ApiResponse<Map<String, Object>> response = ApiResponse.success(new HashMap<>());
            publisher.submit(response);
            publisher.close();
            return publisher;
        };

        Map<String, Object> initialHeaders = new HashMap<>();
        initialHeaders.put("Authorization", "Bearer my-token");
        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler(executionService, session, initialHeaders);

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSubscription__onEvent\",\"params\":{},\"id\":\"sub-1\"}");
        Thread.sleep(100);

        Map<String, Object> headers = capturedHeaders.get();
        assertNotNull(headers, "Headers should be passed to execution service");
        assertTrue(headers.containsKey("Authorization"), "Should have Authorization header");
    }

    @Test
    public void testSubscribeWithMethodAndSelection() throws Exception {
        AtomicReference<JsonRpcRequest> capturedRequest = new AtomicReference<>();

        executionService = (request, headers) -> {
            capturedRequest.set(request);
            SubmissionPublisher<ApiResponse<?>> publisher = new SubmissionPublisher<>();
            ApiResponse<Map<String, Object>> response = ApiResponse.success(new HashMap<>());
            publisher.submit(response);
            publisher.close();
            return publisher;
        };

        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler(executionService, session);

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSubscription__onEvent\",\"params\":{\"userId\":\"1001\"},\"selection\":\"id,name\",\"id\":\"sub-1\"}");

        Thread.sleep(100);

        JsonRpcRequest request = capturedRequest.get();
        assertNotNull(request, "Request should be captured");
        assertEquals("TestSubscription__onEvent", request.getMethod(), "Method should be operationName");
        assertNotNull(request.getParams(), "Params should be set");
        assertEquals("1001", ((Map<?, ?>) request.getParams()).get("userId"));
        assertEquals("id,name", request.getSelection(), "Selection should be preserved");
    }

    @Test
    public void testUnsubscribe() throws Exception {
        AtomicBoolean publisherCancelled = new AtomicBoolean(false);

        executionService = (request, headers) -> {
            SubmissionPublisher<ApiResponse<?>> publisher = new SubmissionPublisher<>();
            return new Flow.Publisher<ApiResponse<?>>() {
                @Override
                public void subscribe(Flow.Subscriber<? super ApiResponse<?>> subscriber) {
                    publisher.subscribe(new Flow.Subscriber<ApiResponse<?>>() {
                        @Override
                        public void onSubscribe(Flow.Subscription subscription) {
                            subscriber.onSubscribe(new Flow.Subscription() {
                                @Override
                                public void request(long n) {
                                    subscription.request(n);
                                }

                                @Override
                                public void cancel() {
                                    publisherCancelled.set(true);
                                    subscription.cancel();
                                }
                            });
                        }

                        @Override
                        public void onNext(ApiResponse<?> item) {
                            subscriber.onNext(item);
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
                }
            };
        };

        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler(executionService, session);

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSubscription__onEvent\",\"params\":{},\"id\":\"sub-1\"}");
        Thread.sleep(50);

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"unsubscribe\",\"params\":{\"id\":\"sub-1\"},\"id\":\"cancel-1\"}");
        Thread.sleep(50);

        assertTrue(publisherCancelled.get(), "Publisher should be cancelled after unsubscribe");

        List<String> messages = session.getSentMessages();
        boolean foundCancelResponse = messages.stream()
                .anyMatch(m -> m.contains("\"id\":\"cancel-1\"") && m.contains("\"cancelled\":true"));
        assertTrue(foundCancelResponse, "Should have cancel response");
    }

    @Test
    public void testPingPong() throws Exception {
        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler(executionService, session);

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"id\":\"ping-1\"}");
        Thread.sleep(50);

        List<String> messages = session.getSentMessages();
        boolean foundPong = messages.stream().anyMatch(m ->
                m.contains("\"jsonrpc\":\"2.0\"") &&
                m.contains("\"id\":\"ping-1\"") &&
                m.contains("\"result\"") &&
                m.contains("\"pong\":true"));
        assertTrue(foundPong, "Should have sent pong response in JSON-RPC format");
    }

    @Test
    public void testPingNotification() throws Exception {
        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler(executionService, session);

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"ping\"}");
        Thread.sleep(50);

        List<String> messages = session.getSentMessages();
        boolean foundPong = messages.stream().anyMatch(m ->
                m.contains("\"jsonrpc\":\"2.0\"") &&
                m.contains("\"method\":\"pong\""));
        assertTrue(foundPong, "Should have sent pong notification");
    }

    @Test
    public void testInvalidRequest_NoMethod() throws Exception {
        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler(executionService, session);

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"id\":\"req-1\"}");

        Thread.sleep(50);

        List<String> messages = session.getSentMessages();
        assertFalse(messages.isEmpty(), "Should have error response");

        String response = messages.get(0);
        assertTrue(response.contains("\"error\""), "Should have error field");
        assertTrue(response.contains("\"code\":" + JsonRpcWebSocketErrorCodes.INVALID_REQUEST), "Should have INVALID_REQUEST code");
    }

    @Test
    public void testSubscribe_NoId() throws Exception {
        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler(executionService, session);

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSubscription__onEvent\",\"params\":{}}");

        Thread.sleep(50);

        List<String> messages = session.getSentMessages();
        boolean foundError = messages.stream().anyMatch(m ->
                m.contains("\"error\"") &&
                m.contains("must have 'id' field"));
        assertTrue(foundError, "Should have error about missing id");
    }

    @Test
    public void testTokenRefresh() throws Exception {
        Map<String, Object> initialHeaders = new HashMap<>();
        initialHeaders.put("Authorization", "Bearer old-token");
        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler(executionService, session, initialHeaders);

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"tokenRefresh\",\"params\":{\"authToken\":\"new-token\"},\"id\":\"refresh-1\"}");
        Thread.sleep(50);

        List<String> messages = session.getSentMessages();
        boolean foundRefreshResponse = messages.stream().anyMatch(m ->
                m.contains("\"id\":\"refresh-1\"") &&
                m.contains("\"refreshed\":true"));
        assertTrue(foundRefreshResponse, "Should have refresh response");
    }

    @Test
    public void testDuplicateSubscriptionId() throws Exception {
        executionService = (request, headers) -> subscriber -> {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                }

                @Override
                public void cancel() {
                }
            });
        };

        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler(executionService, session);

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSubscription__onEvent1\",\"params\":{},\"id\":\"sub-1\"}");
        Thread.sleep(100);

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSubscription__onEvent2\",\"params\":{},\"id\":\"sub-1\"}");
        Thread.sleep(100);

        assertTrue(session.isClosed(), "Should close on duplicate subscription id");
        assertEquals(4409, session.getCloseCode(), "Close code should be 4409 (Subscriber already exists)");
    }

    @Test
    public void testDataMessageFormat() throws Exception {
        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler(executionService, session);

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSubscription__onEvent\",\"params\":{},\"id\":\"sub-1\"}");
        Thread.sleep(100);

        List<String> messages = session.getSentMessages();

        for (String msg : messages) {
            if (msg.contains("\"testField\"")) {
                assertTrue(msg.contains("\"jsonrpc\":\"2.0\""), "Data message should have jsonrpc version");
                assertTrue(msg.contains("\"id\":\"sub-1\""), "Data message should have id");
                assertTrue(msg.contains("\"result\""), "Data message should use 'result' field (JSON-RPC style)");
                assertFalse(msg.contains("\"type\""), "Data message should NOT have 'type' field (GraphQL-WS style)");
            }
        }
    }

    @Test
    public void testCompleteMessageFormat() throws Exception {
        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler(executionService, session);

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSubscription__onEvent\",\"params\":{},\"id\":\"sub-1\"}");
        Thread.sleep(100);

        List<String> messages = session.getSentMessages();

        boolean foundComplete = false;
        for (String msg : messages) {
            if (msg.contains("\"complete\":true")) {
                foundComplete = true;
                assertTrue(msg.contains("\"jsonrpc\":\"2.0\""), "Complete message should have jsonrpc version");
                assertTrue(msg.contains("\"id\":\"sub-1\""), "Complete message should have id");
                assertTrue(msg.contains("\"result\""), "Complete message should use 'result' field");
                assertFalse(msg.contains("\"type\":\"complete\""), "Complete message should NOT use GraphQL-WS style");
            }
        }
        assertTrue(foundComplete, "Should have complete message");
    }

    private static class MockWebSocketSession implements IWebSocketSession {
        private final List<String> sentMessages = new ArrayList<>();
        private boolean closed = false;
        private short closeCode = 0;
        private String closeReason = "";

        @Override
        public void sendMessage(String message) throws IOException {
            sentMessages.add(message);
        }

        @Override
        public void close(short statusCode, String reason) {
            this.closed = true;
            this.closeCode = statusCode;
            this.closeReason = reason;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        public List<String> getSentMessages() {
            return sentMessages;
        }

        public short getCloseCode() {
            return closeCode;
        }

        public String getCloseReason() {
            return closeReason;
        }
    }
}
