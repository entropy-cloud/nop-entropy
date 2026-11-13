package io.nop.graphql.core.ws;

import io.nop.api.core.beans.ApiResponse;
import io.nop.graphql.core.jsonrpc.JsonRpcRequest;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 4.4-C1-003（plan 2562 裁定 D）载体：控制帧并入 sendPump（承载 C10-008 控制帧面）。
 *
 * <p>pin-a：消息线程直发站点（ping→pong 确认、invalid-request error 帧）不再在消息线程上
 * 执行阻塞 sendMessage——出站经会话有界 sendPump 在共享 worker 池上投递（变异红线：
 * 回退为直发时 sendMessage 回到调用线程，pin-a 红）。
 * pin-b：控制帧与数据帧共享单 FIFO 泵——控制帧排序在待发数据帧之后（裁定 D 显式声明）。
 * pin-c：泵内直发 helper（sendErrorDirect）保持直发语义——在调用线程上同步执行
 *（裁定 D：泵内调用方嵌套入队 + DROP_NEWEST 静默丢弃面 → 包私有直发即决策）。
 */
@Timeout(60)
public class TestJsonRpcControlFramePump {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }


    /**
     * 记录每次 sendMessage 的执行线程与消息内容——直发/泵送的判别面。
     */
    static class RecordingSession implements IWebSocketSession {
        final List<String> sent = new ArrayList<>();
        final List<Thread> sendThreads = new ArrayList<>();
        boolean closed;
        short closeCode;

        @Override
        public void sendMessage(String message) throws IOException {
            sendThreads.add(Thread.currentThread());
            sent.add(message);
        }

        @Override
        public void close(short statusCode, String reason) {
            closed = true;
            closeCode = statusCode;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }
    }

    private static JsonRpcWebSocketHandler newHandler(RecordingSession session) {
        // 同步投递 publisher：subscribe 后 request(1) 在消息处理线程内同步触发 onNext+onComplete
        BiFunction<JsonRpcRequest, Map<String, Object>, Flow.Publisher<ApiResponse<?>>> executionService =
                (request, headers) -> subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                    boolean delivered;

                    @Override
                    public void request(long n) {
                        if (n > 0 && !delivered) {
                            delivered = true;
                            subscriber.onNext(ApiResponse.success(Map.of("d", "v")));
                            subscriber.onComplete();
                        }
                    }

                    @Override
                    public void cancel() {
                    }
                });
        return new JsonRpcWebSocketHandler(executionService, session);
    }

    private static void awaitUntil(String message, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean())
                return;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(message);
            }
        }
        fail(message);
    }

    /**
     * pin-a：pong 确认帧经 sendPump 投递——不在消息线程上同步执行。
     */
    @Test
    public void pingPongFrameIsDeliveredViaSendPumpOffMessageThread() {
        RecordingSession session = new RecordingSession();
        JsonRpcWebSocketHandler handler = newHandler(session);
        Thread caller = Thread.currentThread();

        try {
            handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"id\":\"p-1\"}");

            awaitUntil("pong frame must be delivered via the send pump",
                    () -> session.sent.stream().anyMatch(m ->
                            m.contains("\"id\":\"p-1\"") && m.contains("\"pong\":true")));

            assertEquals(1, session.sent.size(), "control frame must be the only outbound message");
            assertNotEquals(caller, session.sendThreads.get(0),
                    "pong frame must NOT be sent on the message thread (must be pumped"
                            + " onto the shared worker pool — direct send regression is red here)");
        } finally {
            handler.onClose();
        }
    }

    /**
     * pin-a 续：error 帧（invalid request，onMessage 路径）同样经 sendPump。
     */
    @Test
    public void errorFrameFromMessagePathIsDeliveredViaSendPumpOffMessageThread() {
        RecordingSession session = new RecordingSession();
        JsonRpcWebSocketHandler handler = newHandler(session);
        Thread caller = Thread.currentThread();

        try {
            handler.onMessage("{\"jsonrpc\":\"2.0\",\"id\":\"req-1\"}");

            awaitUntil("error frame must be delivered via the send pump",
                    () -> session.sent.stream().anyMatch(m ->
                            m.contains("\"error\"") && m.contains("\"id\":\"req-1\"")));

            assertNotEquals(caller, session.sendThreads.get(0),
                    "error frame must NOT be sent on the message thread");
        } finally {
            handler.onClose();
        }
    }

    /**
     * pin-b：控制帧与数据帧共享单 FIFO 泵——先订阅（数据帧待发）再 ping，pong 确认帧
     * 排序在数据帧之后（裁定 D 显式声明的顺序语义）。
     */
    @Test
    public void controlFrameIsOrderedAfterPendingDataFramesInSingleFifoPump() {
        RecordingSession session = new RecordingSession();
        JsonRpcWebSocketHandler handler = newHandler(session);

        try {
            // 订阅：数据帧 + complete 帧先入泵
            handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSub__onEvent\",\"params\":{},\"id\":\"sub-1\"}");
            // 随后控制帧入泵（同一 FIFO）
            handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"id\":\"p-1\"}");

            awaitUntil("all frames flushed in pump order",
                    () -> session.sent.size() >= 3);

            int dataIndex = -1, completeIndex = -1, pongIndex = -1;
            for (int i = 0; i < session.sent.size(); i++) {
                String m = session.sent.get(i);
                if (m.contains("\"d\":\"v\""))
                    dataIndex = i;
                if (m.contains("\"complete\":true"))
                    completeIndex = i;
                if (m.contains("\"pong\":true"))
                    pongIndex = i;
            }
            assertTrue(dataIndex >= 0 && completeIndex >= 0 && pongIndex >= 0,
                    "all three frames must be delivered");
            assertTrue(pongIndex > dataIndex && pongIndex > completeIndex,
                    "control frame (pong) must be ordered after the pending data/complete frames"
                            + " in the single FIFO pump (declared semantics, plan 2562 裁定 D)");
        } finally {
            handler.onClose();
        }
    }

    /**
     * pin-c：泵内直发 helper 保持直发语义——sendErrorDirect 在调用线程上同步出站
     *（对应泵内 onNext/onError/sendErrorFromGraphQL 调用方，嵌套入队为禁止面）。
     */
    @Test
    public void pumpInternalDirectHelperSendsSynchronouslyOnCallerThread() throws IOException {
        RecordingSession session = new RecordingSession();
        JsonRpcWebSocketHandler handler = newHandler(session);

        try {
            handler.sendErrorDirect("op-1", JsonRpcWebSocketErrorCodes.INTERNAL_ERROR, "boom");

            assertEquals(1, session.sent.size(),
                    "direct helper must send synchronously (no pump hop)");
            assertEquals(Thread.currentThread(), session.sendThreads.get(0),
                    "direct helper must execute on the caller thread (pump-internal contract)");
            assertTrue(session.sent.get(0).contains("\"error\"")
                            && session.sent.get(0).contains("\"code\":" + JsonRpcWebSocketErrorCodes.INTERNAL_ERROR),
                    "direct helper must emit the jsonrpc error frame");
        } finally {
            handler.onClose();
        }
    }

    /**
     * pin-d：订阅上游 onError 的错误帧仍经同一 sendPump（泵内直发 helper 承载），
     * 订阅条目照常清理（2550 裁定 D 终止信号语义零回退）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void upstreamErrorFrameStillDeliveredViaSamePump() {
        RecordingSession session = new RecordingSession();
        AtomicReference<Flow.Subscriber<ApiResponse<?>>> subRef = new AtomicReference<>();
        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler((request, headers) -> subscriber -> {
            subRef.set((Flow.Subscriber) subscriber);
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                }

                @Override
                public void cancel() {
                }
            });
        }, session);

        try {
            handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"TestSub__onEvent\",\"params\":{},\"id\":\"sub-1\"}");
            Flow.Subscriber<ApiResponse<?>> subscriber = subRef.get();
            assertTrue(subscriber != null, "subscriber must be registered");
            subscriber.onError(new IOException("upstream-boom"));

            awaitUntil("error termination frame must be delivered via the send pump",
                    () -> session.sent.stream().anyMatch(m -> m.contains("\"error\"") && m.contains("\"sub-1\"")));
            assertTrue(handler.activeOperations.isEmpty(),
                    "errored subscription must be cleaned from activeOperations");
        } finally {
            handler.onClose();
        }
    }

    /**
     * 结构钉（变异复核锚）：消息线程直发站点全部经 sendControlFrame → sendPump.submit
     * 出站（泵内 helper 除外）。行为钉（pin-a/pin-b）已覆盖红绿判别，本钉定位转换面。
     */
    @Test
    public void messageThreadSitesAreWiredThroughSendPumpStructure() throws Exception {
        java.nio.file.Path source = java.nio.file.Path.of(
                "src/main/java/io/nop/graphql/core/ws/JsonRpcWebSocketHandler.java");
        String code = java.nio.file.Files.readString(source);
        // handlePing / handleUnsubscribe / handleTokenRefresh / sendError 的转换面：
        // sendControlFrame 出现 ≥4 次（ping×2 不可区分，取下界 3），且这些方法体内不再出现
        // session.sendMessage 直发（泵内 sendErrorDirect/sendErrorFromGraphQL/sendKeepAlive/
        // onNext 保持直发/submit，不受此钉约束）。
        long conversions = code.lines().filter(l -> l.contains("sendControlFrame(")).count();
        assertTrue(conversions >= 3,
                "message-thread control-frame sites must route through sendControlFrame (found "
                        + conversions + ")");
        int pingBody = code.indexOf("private void handlePing(String id)");
        int pingEnd = code.indexOf("private void sendControlFrame");
        assertTrue(pingBody > 0 && pingEnd > pingBody,
                "handlePing body must exist");
        assertFalse(code.substring(pingBody, pingEnd).contains("session.sendMessage"),
                "handlePing must not direct-send on the message thread");
    }
}
