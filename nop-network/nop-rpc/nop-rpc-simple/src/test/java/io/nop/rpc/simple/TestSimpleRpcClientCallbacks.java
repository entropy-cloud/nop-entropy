package io.nop.rpc.simple;

import io.nop.socket.SocketClient;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
