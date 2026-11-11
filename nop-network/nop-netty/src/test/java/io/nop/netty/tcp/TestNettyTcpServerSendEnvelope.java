package io.nop.netty.tcp;

import io.nop.api.core.exceptions.NopConnectException;
import io.nop.api.core.exceptions.NopException;
import io.nop.netty.config.NettyTcpClientConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static io.nop.commons.util.NetHelper.findAvailableTcpPort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-15 同步抛统一包络（4.3-MA-14 / plan 2560 裁定 H，netty 面）：
 * 返回 CompletableFuture 的方法不得同步抛——
 * ① sendToFirstChannel/sendToAnyChannel 无可用 channel → failed future（ERR_NETTY_NO_AVAILABLE_CHANNEL）；
 * ② 管线上无 IRpcMessageHandler（纯推送型管线）→ failed future（ERR_NETTY_NO_RPC_HANDLER，
 * 原 NPE 面修复）。
 */
@Timeout(60)
public class TestNettyTcpServerSendEnvelope {

    static int port;
    static NettyTcpServer server;

    @BeforeAll
    static void init() {
        port = findAvailableTcpPort();
        server = TcpTestHelper.createMockServer(port);
    }

    @AfterAll
    static void destroy() {
        if (server != null)
            server.stop();
    }

    /** ① 无可用 channel：sendToFirstChannel 返回 failed future 而非同步抛 */
    @Test
    public void testSendToFirstChannelNoChannelReturnsFailedFuture() {
        // 空服务器（无任何接入连接）
        CompletableFuture<Object> ret = server.sendToFirstChannel("msg", 100);
        ExecutionException e = assertThrows(ExecutionException.class, () -> ret.get(2, TimeUnit.SECONDS));
        assertTrue(e.getCause() instanceof NopException);
        assertEquals("nop.err.netty.no-available-channel", ((NopException) e.getCause()).getErrorCode());
    }

    /** ①b 无可用 channel：sendToAnyChannel 同样返回 failed future */
    @Test
    public void testSendToAnyChannelNoChannelReturnsFailedFuture() {
        CompletableFuture<Object> ret = server.sendToAnyChannel("msg", 100);
        ExecutionException e = assertThrows(ExecutionException.class, () -> ret.get(2, TimeUnit.SECONDS));
        assertTrue(e.getCause() instanceof NopException);
        assertEquals("nop.err.netty.no-available-channel", ((NopException) e.getCause()).getErrorCode());
    }

    /** ② 纯推送型管线（无 IRpcMessageHandler）：sendToFirstChannel 返回 failed future 而非 NPE */
    @Test
    public void testSendToFirstChannelNoRpcHandlerReturnsFailedFuture() throws Exception {
        // 接入一条原始连接（不装配 IRpcMessageHandler）
        NettyTcpClient raw = new NettyTcpClient();
        NettyTcpClientConfig config = new NettyTcpClientConfig();
        config.setAutoReconnect(false);
        config.setConnectTimeout(1000);
        config.setRemoteHost("localhost");
        config.setRemotePort(port);
        raw.setConfig(config);
        raw.setChannelInitializer(pipeline -> {
        });
        raw.start();
        try {
            assertTrue(raw.awaitConnected(5000));
            // 等 server 端 channel 进 channelGroup
            for (int i = 0; i < 100 && server.getChannelGroup().isEmpty(); i++) {
                Thread.sleep(20);
            }
            assertTrue(!server.getChannelGroup().isEmpty(), "server must have accepted channel");

            CompletableFuture<Object> ret = server.sendToFirstChannel("push-msg", 100);
            ExecutionException e = assertThrows(ExecutionException.class, () -> ret.get(2, TimeUnit.SECONDS));
            assertTrue(e.getCause() instanceof NopException, "must be failed future with NopException, not NPE");
            assertEquals("nop.err.netty.no-rpc-handler", ((NopException) e.getCause()).getErrorCode());
        } finally {
            raw.stop();
        }
    }
}
