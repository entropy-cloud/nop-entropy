package io.nop.graphql.core.ws;

import io.nop.api.core.beans.ApiResponse;
import io.nop.graphql.core.jsonrpc.JsonRpcRequest;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C8-009（plan 2551 裁定 J）钉：WS 会话活性面（承载 C10-015）。
 * pin-a：pong 记账——METHOD_PONG 更新 lastPongAt（原空分支红）；
 * pin-b：连续发送失败（3 次）→ 主动断连（close 路径触发，不再仅 WARN）；
 * pin-c：onThrowable 清理 activeOperations（原仅注销 keepAlive 的泄漏面）。
 */
public class TestWsSessionLiveness extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }


    static class LivenessSession implements IWebSocketSession {
        final List<String> sent = new java.util.ArrayList<>();
        final AtomicBoolean closeRequested = new AtomicBoolean(false);
        volatile boolean failSends;
        int failures;

        @Override
        public void sendMessage(String message) throws java.io.IOException {
            if (failSends) {
                failures++;
                throw new java.io.IOException("simulated broken pipe");
            }
            sent.add(message);
        }

        @Override
        public void close(short statusCode, String reason) {
            closeRequested.set(true);
        }

        @Override
        public boolean isClosed() {
            return closeRequested.get();
        }
    }

    private static JsonRpcWebSocketHandler newHandler(LivenessSession session) {
        BiFunction<JsonRpcRequest, Map<String, Object>, Flow.Publisher<ApiResponse<?>>> executionService =
                (req, headers) -> subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                    }

                    @Override
                    public void cancel() {
                    }
                });
        return new JsonRpcWebSocketHandler(executionService, session);
    }

    private static long field(JsonRpcWebSocketHandler handler, String name) throws Exception {
        Field f = JsonRpcWebSocketHandler.class.getDeclaredField(name);
        f.setAccessible(true);
        return (long) f.get(handler);
    }

    @Test
    public void pin_a_pongUpdatesBookkeeping() throws Exception {
        LivenessSession session = new LivenessSession();
        JsonRpcWebSocketHandler handler = newHandler(session);
        long before = field(handler, "lastPongAt");
        Thread.sleep(20);

        JsonRpcRequest pong = new JsonRpcRequest();
        pong.setMethod("pong");
        handler.onMessage(pong);

        assertTrue(field(handler, "lastPongAt") > before,
                "METHOD_PONG must update lastPongAt (was an empty branch)");
    }

    @Test
    public void pin_b_consecutiveSendFailuresDisconnect() throws Exception {
        LivenessSession session = new LivenessSession();
        session.failSends = true;
        JsonRpcWebSocketHandler handler = newHandler(session);

        // sendKeepAlive 经 pump 异步——驱动三轮，连续失败 3 次后 close 路径触发
        for (int i = 0; i < 3 && !session.closeRequested.get(); i++) {
            handler.sendKeepAlive();
            for (int j = 0; j < 200 && !session.closeRequested.get(); j++) {
                Thread.sleep(5);
            }
        }
        assertTrue(session.closeRequested.get(),
                "3 consecutive send failures must trigger disconnect via close path");
    }

    @Test
    public void pin_c_onThrowableClearsActiveOperations() throws Exception {
        LivenessSession session = new LivenessSession();
        JsonRpcWebSocketHandler handler = newHandler(session);

        JsonRpcRequest req = new JsonRpcRequest();
        req.setId("op-t");
        req.setMethod("TestObj__bizGet");
        handler.onMessage(req);

        Field opsField = JsonRpcWebSocketHandler.class.getDeclaredField("activeOperations");
        opsField.setAccessible(true);
        Map<String, ?> ops = (Map<String, ?>) opsField.get(handler);
        assertFalse(ops.isEmpty(), "subscription must be registered before throwable");

        handler.onThrowable(new RuntimeException("transport blew up"));

        assertTrue(ops.isEmpty(), "onThrowable must clear activeOperations (was keepAlive-only unregister)");
    }
}
