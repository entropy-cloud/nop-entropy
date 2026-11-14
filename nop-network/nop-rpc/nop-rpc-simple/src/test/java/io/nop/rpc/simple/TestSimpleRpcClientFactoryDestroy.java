package io.nop.rpc.simple;

import io.nop.socket.BinaryCommand;
import io.nop.rpc.api.IRpcProxy;
import io.nop.socket.ClientConfig;
import io.nop.socket.ICommandClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.3-C6-010（plan 2561 裁定 K）钉：SimpleRpcClientFactory client 登记表——登记点 = newInstance()、
 * 登记物 = SimpleRpcClient（AutoCloseable）；destroy 逆序关闭兜底；关闭路径容忍重复回调
 * （SocketClient.close() 重入会再触发 onChannelClose——计数断言取「至少一次」形态，不做 closed 守卫）。
 * 变异红形态：移除 newInstance 登记或 destroy 关闭循环 → 关闭序列为空 → 红。
 */
public class TestSimpleRpcClientFactoryDestroy {

    /** 记录型 ICommandClient 桩：close 记序，并模拟 SocketClient.close 每次重入都再触发 onChannelClose。 */
    static class RecordingClient implements ICommandClient {
        final int index;
        final List<Integer> closeOrder;
        final ClientConfig config;
        final AtomicInteger closeCalls = new AtomicInteger();
        final AtomicInteger channelCloseCallbacks = new AtomicInteger();
        boolean failOnClose;
        volatile Runnable onOpen;
        volatile Runnable onClose;

        RecordingClient(int index, List<Integer> closeOrder, ClientConfig config) {
            this.index = index;
            this.closeOrder = closeOrder;
            this.config = config;
        }

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
        public Runnable getOnChannelOpen() {
            return onOpen;
        }

        @Override
        public Runnable getOnChannelClose() {
            return onClose;
        }

        @Override
        public void connect() {
        }

        @Override
        public void send(BinaryCommand cmd, boolean flush) {
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
            closeCalls.incrementAndGet();
            closeOrder.add(index);
            if (failOnClose)
                throw new IllegalStateException("close-failed-for-test:" + index);
            // 无 closed 守卫：每次 close 都再触发 onChannelClose（SocketClient 现行重入语义）
            Runnable cb = onClose;
            if (cb != null) {
                channelCloseCallbacks.incrementAndGet();
                cb.run();
            }
        }
    }

    static class RecordingFactory extends SimpleRpcClientFactory<EchoService> {
        final List<Integer> closeOrder = new CopyOnWriteArrayList<>();
        final List<RecordingClient> created = new ArrayList<>();

        RecordingFactory() {
            setRpcInterface(EchoService.class);
            setClientConfig(new ClientConfig());
        }

        @Override
        protected ICommandClient newClient() {
            RecordingClient client = new RecordingClient(created.size() + 1, closeOrder, getClientConfig());
            created.add(client);
            return client;
        }
    }

    private static int registrySize(SimpleRpcClientFactory<?> factory) throws Exception {
        Field f = SimpleRpcClientFactory.class.getDeclaredField("clients");
        f.setAccessible(true);
        return ((List<?>) f.get(factory)).size();
    }

    @Test
    public void newInstanceRegistersClientAndDestroyClosesInReverseOrder() throws Exception {
        RecordingFactory factory = new RecordingFactory();
        factory.newInstance();
        factory.newInstance();
        factory.newInstance();

        assertEquals(3, registrySize(factory), "C6-010: 每次 newInstance 登记一个 SimpleRpcClient");
        assertTrue(factory.closeOrder.isEmpty(), "destroy 之前不得关闭");

        factory.destroy();

        assertEquals(List.of(3, 2, 1), factory.closeOrder,
                "C6-010: destroy 必须逆序关闭登记的 client（后建先关；缺陷态零关闭 → 连接泄漏）");
        assertEquals(0, registrySize(factory), "destroy 后登记表清空");
        assertTrue(factory.isDestroyed());
    }

    @Test
    public void destroyToleratesRepeatedCloseCallbacks() throws Exception {
        RecordingFactory factory = new RecordingFactory();
        factory.newInstance();
        factory.newInstance();
        // 工厂关闭前调用方已自行关闭过一次：destroy 的兜底关闭构成重入
        RecordingClient first = factory.created.get(0);
        first.close();

        assertDoesNotThrow(factory::destroy, "重复 close/onChannelClose 回调不得使 destroy 抛错");

        assertEquals(2, first.closeCalls.get(), "已被调用方关闭的 client 仍被兜底关闭（不做 closed 幂等守卫）");
        assertTrue(first.channelCloseCallbacks.get() >= 1, "onChannelClose 至少触发一次（容忍重复回调）");
        assertTrue(factory.created.get(1).closeCalls.get() >= 1, "其余 client 至少关闭一次");
        // 注：整厂 destroy 的二次幂等不在 C6-010 范围（登记表已清空，重复关闭自然不会发生；
        // 自有 executor/timer 的 destroy 幂等为独立既有面，裁定 K 明示不加 closed 守卫）
    }

    @Test
    public void closeFailureDoesNotBlockRemainingClients() {
        RecordingFactory factory = new RecordingFactory();
        factory.newInstance();
        factory.newInstance();
        factory.newInstance();
        factory.created.get(1).failOnClose = true;

        assertDoesNotThrow(factory::destroy, "单个 client 关闭失败不得中断 destroy");
        assertEquals(List.of(3, 2, 1), factory.closeOrder, "失败项之后的 client 仍按逆序被关闭");
    }
    /**
     * plan 2572 Phase 2 钉：close 摘除登记——长寿 factory 反复 newInstance 时登记表不因
     * 正常关闭累积；漏关 client 仍由 destroy 兜底（C6-010 语义不回退）。
     * 变异红形态：去掉 setOnCloseCallback 注入 → registrySize 保持 2 → 红。
     */
    @Test
    public void pin2572_closeDeregistersFromFactoryRegistry() throws Exception {
        RecordingFactory factory = new RecordingFactory();
        EchoService p1 = factory.newInstance();
        EchoService p2 = factory.newInstance();
        assertEquals(2, registrySize(factory));

        ((IRpcProxy) p1).proxy_destroy();
        assertEquals(1, registrySize(factory), "close must deregister the closed client");

        ((IRpcProxy) p2).proxy_destroy();
        assertEquals(0, registrySize(factory), "both clients closed: registry must be empty");

        // destroy 兜底：已空表 close 不再触发额外 RecordingClient.close（每 client 已记 1 次）
        factory.destroy();
        assertEquals(0, registrySize(factory), "destroy after manual close: registry stays empty");
        // closeOrder 含 2 次 RecordingClient.close（用户侧），destroy 未追加（无重复关闭）
    }
}
