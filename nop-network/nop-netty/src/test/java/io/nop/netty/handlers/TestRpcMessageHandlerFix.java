package io.nop.netty.handlers;

import io.netty.channel.embedded.EmbeddedChannel;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
public class TestRpcMessageHandlerFix {

    @Test
    public void testSendAfterChannelInactiveCompletesExceptionally() throws Exception {
        RpcMessageHandler handler = new RpcMessageHandler(100, new IRpcMessageAdapter() {
            @Override
            public Object getRequestId(Object request) {
                return request;
            }

            @Override
            public Object getResponseId(Object response) {
                return response;
            }
        });
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // 关闭连接（channelInactive 将 channel 置空）
        channel.close().await(1, TimeUnit.SECONDS);

        CompletableFuture<Object> future = new CompletableFuture<>();
        assertDoesNotThrow(() -> handler.send("msg-1", 10000, future));

        // 连接已断开时 send 必须以错误完成 future，而不是无限挂起
        Object result = assertDoesNotThrow(() -> future.handle((r, e) -> e).get(3, TimeUnit.SECONDS),
                "future must complete after channel inactive");
        assertTrue(result instanceof NopException,
                "should fail with channel-not-active error, got: " + result);
    }
}
