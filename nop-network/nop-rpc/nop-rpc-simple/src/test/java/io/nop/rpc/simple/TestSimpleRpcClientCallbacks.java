package io.nop.rpc.simple;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.json.JSON;
import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.core.lang.json.JsonTool;
import io.nop.rpc.core.message.RpcChannelState;
import io.nop.socket.BinaryCommand;
import io.nop.socket.ClientConfig;
import io.nop.socket.ICommandClient;
import io.nop.socket.SocketClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.3-C1-005（plan 2560 裁定 K）钉：SimpleRpcClient 构造器不得静默丢弃工厂先行配置的回调
 * （合并语义：工厂回调先行、内部回调后行、晚设置回调可见）。
 * 变异红形态：构造器覆写恢复（丢弃 prevOpen/prevClose）→ 工厂回调计数 0 → 红。
 */
public class TestSimpleRpcClientCallbacks {

    @Test
    public void testFactoryCallbackNotDroppedByConstructor() {
        SocketClient client = new SocketClient();
        AtomicInteger factoryOpenCount = new AtomicInteger();
        client.setOnChannelOpen(factoryOpenCount::incrementAndGet);
        client.setOnChannelClose(factoryOpenCount::incrementAndGet);

        AtomicBoolean internalOpen = new AtomicBoolean(false);
        SimpleRpcClient rpcClient = new SimpleRpcClient("test", client,
                resp -> {
                }, new io.nop.commons.concurrent.executor.DefaultScheduledExecutor()
                        .newSingleThreadTimer("test-timer"));
        rpcClient.setOnChannelOpen(() -> {
            internalOpen.set(true);
        });

        client.getOnChannelOpen().run();
        assertTrue(factoryOpenCount.get() >= 1, "工厂先行回调不得被构造器覆写静默丢弃（缺陷态计数 0 → 红）");
        assertTrue(internalOpen.get(), "内部 channelState 回调必须执行");

        client.getOnChannelClose().run();
        assertEquals(2, factoryOpenCount.get(), "open+close 各一次工厂回调");
    }

    @Test
    public void testLateSetCallbackVisible() {
        SocketClient client = new SocketClient();
        SimpleRpcClient rpcClient = new SimpleRpcClient("test", client, resp -> {
        }, new io.nop.commons.concurrent.executor.DefaultScheduledExecutor()
                .newSingleThreadTimer("test-timer-2"));
        AtomicBoolean lateFired = new AtomicBoolean(false);
        rpcClient.setOnChannelOpen(() -> lateFired.set(true));
        client.getOnChannelOpen().run();
        assertTrue(lateFired.get(), "晚设置回调对连接事件线程可见（volatile）");
    }

    /** send 恒抛的 ICommandClient 桩（不依赖 SocketClient 实现细节）。 */
    static class FailingSendClient implements ICommandClient {
        final ClientConfig config = new ClientConfig();
        Runnable onOpen;
        Runnable onClose;

        @Override
        public ClientConfig getClientConfig() {
            return config;
        }

        @Override
        public void setOnChannelOpen(Runnable onOpen) {
            this.onOpen = onOpen;
        }

        @Override
        public void setOnChannelClose(Runnable onClose) {
            this.onClose = onClose;
        }

        @Override
        public void connect() {
        }

        @Override
        public void send(BinaryCommand cmd, boolean flush) {
            throw new IllegalStateException("send-failed-for-test");
        }

        @Override
        public void ping() {
        }

        @Override
        public void reconnect() {
        }

        @Override
        public void recv(Executor executor, Consumer<BinaryCommand> handler) {
        }

        @Override
        public void close() {
        }
    }

    /**
     * 4.3-C3-009（plan 2561 裁定 E）钉：send 失败经 channelState.onFailure 完成 waitFutures 中
     * 登记的 raw future（remove + completeExceptionally）。
     * 变异红形态：catch 回退为对派生 future completeExceptionally → 调用方仍见失败，但 raw
     * future 滞留 waitFutures（泄漏）→ 登记表非空断言红。
     */
    @Test
    public void testSendFailureCompletesWaitFutureViaOnFailure() throws Exception {
        JSON.registerProvider(JsonTool.instance()); // buildRequest 需 JSON 序列化（单元测试裸环境）
        FailingSendClient client = new FailingSendClient();
        // 超时预算放大：排除「超时清理兜底」把泄漏面掩盖成绿的可能
        client.config.setResponseTimeout(600_000);
        IScheduledExecutor timer = DefaultScheduledExecutor.newSingleThreadTimer("test-timer-c3-009");
        try {
            SimpleRpcClient rpcClient = new SimpleRpcClient("test", client, resp -> {
            }, timer);

            CompletableFuture<ApiResponse<?>> future = rpcClient.callAsync("echo", new ApiRequest<>(), null)
                    .toCompletableFuture();

            assertTrue(future.isCompletedExceptionally(), "send 失败必须以失败终态交付调用方");
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            Throwable cause = ex.getCause();
            while (cause.getCause() != null && !(cause instanceof IllegalStateException))
                cause = cause.getCause();
            assertEquals("send-failed-for-test", cause.getMessage(), "失败原因必须是 send 抛出的原始异常");

            Field stateField = SimpleRpcClient.class.getDeclaredField("channelState");
            stateField.setAccessible(true);
            Object channelState = stateField.get(rpcClient);
            Field waitField = RpcChannelState.class.getDeclaredField("waitFutures");
            waitField.setAccessible(true);
            Map<?, ?> waitFutures = (Map<?, ?>) waitField.get(channelState);
            assertTrue(waitFutures.isEmpty(),
                    "C3-009: send 失败后 raw future 必须已从 waitFutures 摘除并完成（缺陷态滞留 → 泄漏）: "
                            + waitFutures);
        } finally {
            timer.destroy();
        }
    }
}
