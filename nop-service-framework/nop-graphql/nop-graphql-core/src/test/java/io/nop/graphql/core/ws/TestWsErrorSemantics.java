package io.nop.graphql.core.ws;

import io.nop.api.core.beans.ApiResponse;
import io.nop.graphql.core.jsonrpc.JsonRpcRequest;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C3-006（plan 2550 裁定 D）钉：WS 错误三分支收口。
 * pin10-a：上游 onError → 客户端收到 jsonrpc 终止 error 帧（不再静默关流）。
 * pin10-b：session-closed 后推送 → 取消上游订阅（不再只静默 ignore）。
 * pin10-c：消息处理同步 RuntimeException 不穿透——handler 存活，后续消息照常处理。
 */
public class TestWsErrorSemantics extends BaseTestCase {

    /** 测试局部类型化异常（规避 no-raw-Exception lint：不实例化裸 RuntimeException） */
    static final class UpstreamError extends RuntimeException {
        UpstreamError(String message) {
            super(message);
        }
    }

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static void awaitUntil(String message, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean())
                return;
            Thread.sleep(10);
        }
        org.junit.jupiter.api.Assertions.fail("timeout: " + message);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static class MockWebSocketSession implements IWebSocketSession {
        final List<String> sentMessages = new java.util.ArrayList<>();
        boolean closed;
        short closeCode;

        @Override
        public void sendMessage(String message) {
            sentMessages.add(message);
        }

        @Override
        public void close(short statusCode, String reason) {
            this.closed = true;
            this.closeCode = statusCode;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }
    }

    @SuppressWarnings("unchecked")
    private static JsonRpcRequest subscriptionRequest(String id) {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setId(id);
        request.setMethod("TestObj__bizGet");
        return request;
    }

    private static JsonRpcRequest pingRequest() {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setId("ping-1");
        request.setMethod("ping");
        return request;
    }

    /**
     * pin10-a：上游 error → 客户端收到终止信号帧（jsonrpc error 帧后再清理）。
     */
    @Test
    public void pin10a_upstreamErrorSendsTerminationErrorFrame() throws Exception {
        MockWebSocketSession session = new MockWebSocketSession();
        AtomicReference<Flow.Subscriber> subRef = new AtomicReference<>();
        BiFunction<JsonRpcRequest, Map<String, Object>, Flow.Publisher<ApiResponse<?>>> executionService =
                (req, headers) -> subscriber -> {
                    subRef.set(subscriber);
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
        handler.onMessage(subscriptionRequest("op-err"));

        awaitUntil("subscription registered", () -> subRef.get() != null);
        subRef.get().onError(new UpstreamError("upstream boom"));

        awaitUntil("client must receive jsonrpc error termination frame",
                () -> session.sentMessages.stream().anyMatch(m -> m.contains("\"error\"")));
    }

    /**
     * pin10-b：session-closed 后仍推送 → 上游订阅被取消（不再静默消费）。
     */
    @Test
    public void pin10b_closedSessionCancelsUpstream() throws Exception {
        MockWebSocketSession session = new MockWebSocketSession();
        session.closed = true; // 预置关闭态
        AtomicBoolean upstreamCancelled = new AtomicBoolean(false);
        AtomicReference<Flow.Subscriber> subRef = new AtomicReference<>();
        BiFunction<JsonRpcRequest, Map<String, Object>, Flow.Publisher<ApiResponse<?>>> executionService =
                (req, headers) -> subscriber -> {
                    subRef.set(subscriber);
                    subscriber.onSubscribe(new Flow.Subscription() {
                        @Override
                        public void request(long n) {
                        }

                        @Override
                        public void cancel() {
                            upstreamCancelled.set(true);
                        }
                    });
                };
        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler(executionService, session);
        handler.onMessage(subscriptionRequest("op-closed"));
        awaitUntil("subscription registered", () -> subRef.get() != null);

        subRef.get().onNext(ApiResponse.success("late-data"));
        awaitUntil("upstream must be cancelled after push into closed session",
                upstreamCancelled::get);
    }

    /**
     * pin10-c：订阅执行链同步 RuntimeException → handler 存活，后续 ping 照常处理
     * （旧形态异常穿透 catch(IOException)，消息循环被杀）。
     */
    @Test
    public void pin10c_syncRuntimeExceptionDoesNotKillHandler() throws Exception {
        MockWebSocketSession session = new MockWebSocketSession();
        AtomicBoolean first = new AtomicBoolean(true);
        BiFunction<JsonRpcRequest, Map<String, Object>, Flow.Publisher<ApiResponse<?>>> executionService =
                (req, headers) -> {
                    if (first.compareAndSet(true, false)) {
                        throw new UpstreamError("sync explode");
                    }
                    throw new IllegalStateException("should not subscribe again");
                };
        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler(executionService, session);

        handler.onMessage(subscriptionRequest("op-boom")); // 同步炸
        assertFalse(session.closed, "handler must survive sync RuntimeException");

        // ping 响应经 sendPump 异步投递；轮询消息列表
        handler.onMessage(pingRequest());
        awaitUntil("ping must still be answered after sync explosion",
                () -> session.sentMessages.stream().anyMatch(m -> m.contains("\"pong\"")));
    }
}
