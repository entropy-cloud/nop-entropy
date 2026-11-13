package io.nop.graphql.core.ws;

import io.nop.api.core.beans.ApiResponse;
import io.nop.graphql.core.jsonrpc.JsonRpcRequest;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C1-005（plan 2550 裁定 A）钉：同 id 并发注册不覆盖首订阅（putIfAbsent）。
 * pin5：N 线程并发以相同 operationId 注册订阅——执行 publisher（冷流）必须恰被
 * subscribe 一次（旧形态裸 put 会让输家覆盖赢家后双双 subscribe = 2 次）。
 */
public class TestJsonRpcSubscriptionRegister extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static class MockWebSocketSession implements IWebSocketSession {
        private final List<String> sentMessages = new java.util.ArrayList<>();
        private boolean closed;
        private short closeCode;

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

    private static JsonRpcRequest subscriptionRequest(String id) {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setId(id);
        request.setMethod("TestObj__bizGet");
        return request;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Flow.Subscriber<ApiResponse<?>>> activeOperations(JsonRpcWebSocketHandler handler)
            throws Exception {
        Field f = JsonRpcWebSocketHandler.class.getDeclaredField("activeOperations");
        f.setAccessible(true);
        return (Map<String, Flow.Subscriber<ApiResponse<?>>>) f.get(handler);
    }

    @Test
    public void pin5_concurrentSameIdRegisterDoesNotOverwriteFirst() throws Exception {
        MockWebSocketSession session = new MockWebSocketSession();
        AtomicInteger subscribed = new AtomicInteger(0);
        BiFunction<JsonRpcRequest, Map<String, Object>, Flow.Publisher<ApiResponse<?>>> executionService =
                (req, headers) -> subscriber -> {
                    subscribed.incrementAndGet();
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

        int racers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(racers);
        for (int i = 0; i < racers; i++) {
            pool.execute(() -> {
                try {
                    start.await();
                    handler.onMessage(subscriptionRequest("same-id"));
                } catch (InterruptedException e) {
                    // 忽略：竞态允许输家走 4409 拒绝路径
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdownNow();

        // 恰一个订阅存活：putIfAbsent 保证首订阅唯一所有权
        assertEquals(1, activeOperations(handler).size(), "exactly one subscription entry must survive");
        assertEquals(1, subscribed.get(),
                "exactly one publisher must be subscribed (bare put would let the loser overwrite + subscribe = 2)");
    }
}
