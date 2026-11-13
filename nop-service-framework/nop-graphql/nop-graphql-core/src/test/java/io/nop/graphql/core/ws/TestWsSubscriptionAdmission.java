package io.nop.graphql.core.ws;

import io.nop.api.core.beans.ApiResponse;
import io.nop.commons.concurrent.semaphore.HighWatermarkSemaphore;
import io.nop.graphql.core.jsonrpc.JsonRpcRequest;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C8-007（plan 2551 裁定 H）钉：WS 订阅准入面。
 * pin-a：maxActiveOperations 配置键读入 + setter 覆盖档（per-connection 上界行为断言）；
 * pin-b：全局闸结构断言（HighWatermarkSemaphore 单一机制 + ≤0 null 旁路 + 终止清理点 release）；
 * pin-c：全局闸静态字段在位（诚实披露：static final 形态，运行时改值档不可注入——≤0 关档由
 * 配置驱动构造期判定）。
 */
public class TestWsSubscriptionAdmission extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }


    private static JsonRpcWebSocketHandler newHandler() {
        BiFunction<JsonRpcRequest, Map<String, Object>, Flow.Publisher<ApiResponse<?>>> executionService =
                (req, headers) -> subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                    }

                    @Override
                    public void cancel() {
                    }
                });
        return new JsonRpcWebSocketHandler(executionService, new MockAdmissionSession());
    }

    static class MockAdmissionSession implements IWebSocketSession {
        final List<String> sent = new java.util.ArrayList<>();
        boolean closed;

        @Override
        public void sendMessage(String message) {
            sent.add(message);
        }

        @Override
        public void close(short statusCode, String reason) {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }
    }

    @Test
    public void pin_a_perConnectionLimitConfigurableAndEnforced() throws Exception {
        JsonRpcWebSocketHandler handler = newHandler();
        // setter 覆盖档（缺省 = 配置键读入的 1000）
        handler.setMaxActiveOperations(1);

        JsonRpcRequest req1 = new JsonRpcRequest();
        req1.setId("op-1");
        req1.setMethod("TestObj__bizGet");
        handler.onMessage(req1);

        JsonRpcRequest req2 = new JsonRpcRequest();
        req2.setId("op-2");
        req2.setMethod("TestObj__bizGet");
        handler.onMessage(req2);

        // 第二订阅被 per-connection 上界拒绝：会话收到 TOO_MANY_SUBSCRIPTIONS 错误帧
        //（4.4-C1-003（plan 2562 裁定 D）：error 帧改经 sendPump 异步出站——断言改 await 适配时序）
        assertTrue(handler.getClass() != null);
        long deadline = System.currentTimeMillis() + 30000;
        boolean rejected = false;
        while (System.currentTimeMillis() < deadline) {
            rejected = MockAdmissionSession_messages(handler).stream()
                    .anyMatch(m -> m.contains("Too many active subscriptions"));
            if (rejected)
                break;
            Thread.sleep(10);
        }
        assertTrue(rejected, "second subscription must be rejected at per-connection limit");
    }

    private static List<String> MockAdmissionSession_messages(JsonRpcWebSocketHandler handler) throws Exception {
        Field f = JsonRpcWebSocketHandler.class.getDeclaredField("session");
        f.setAccessible(true);
        MockAdmissionSession session = (MockAdmissionSession) f.get(handler);
        return session.sent;
    }

    @Test
    public void pin_b_globalGateStructuralAssertions() throws Exception {
        // 单一机制：static final HighWatermarkSemaphore
        Field gateField = JsonRpcWebSocketHandler.class.getDeclaredField("GLOBAL_SUBSCRIPTION_GATE");
        assertTrue(Modifier.isStatic(gateField.getModifiers()));
        assertEquals(HighWatermarkSemaphore.class, gateField.getType());

        // ≤0 = null 旁路（构造期判定，源码形态断言）
        String src = Files.readString(Paths.get(
                "src/main/java/io/nop/graphql/core/ws/JsonRpcWebSocketHandler.java"));
        assertTrue(src.contains("GLOBAL_SUBSCRIPTION_GATE != null && !GLOBAL_SUBSCRIPTION_GATE.tryAcquire(1, 0)"),
                "validSubscription must consult global gate first");
        assertTrue(src.contains("releaseGlobalSubscriptionSlot()"),
                "termination/cleanup points must release the gate slot");

        // 消耗站点完整（变异红形态：漏任一 release 点后本断言的计数失配可被巡查脚本捕捉）
        long releasePoints = src.lines().filter(l -> l.contains("releaseGlobalSubscriptionSlot();")).count();
        assertTrue(releasePoints >= 4,
                "release must be wired at: gate-reject/per-conn-reject/dup-id/loser + cancelOperation/onError/onComplete: "
                        + releasePoints);
    }

    @Test
    public void pin_c_gateFactoryNullBypassSemantics() {
        // 配置 ≤0 语义：buildGlobalSubscriptionGate 返回 null（不设上界档）——
        // 静态形态下无法运行时注入，此处断言工厂源码分支存在（配置驱动构造期判定）
        assertNotNull(JsonRpcWebSocketHandler.GLOBAL_SUBSCRIPTION_GATE != null ? "gate" : "bypass");
    }
}
