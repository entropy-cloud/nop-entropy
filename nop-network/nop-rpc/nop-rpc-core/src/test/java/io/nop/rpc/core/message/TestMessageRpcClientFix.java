package io.nop.rpc.core.message;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.util.ApiHeaders;
import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(20)
public class TestMessageRpcClientFix {

    static class StubMessageService implements IMessageService {
        volatile IMessageConsumer consumer;
        volatile ApiRequest<?> lastRequest;
        volatile CompletableFuture<Void> sendResult = new CompletableFuture<>();
        volatile boolean failSend;

        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            lastRequest = (ApiRequest<?>) message;
            if (failSend) {
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(new RuntimeException("broker down"));
                return failed;
            }
            return sendResult;
        }

        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
            this.consumer = listener;
            return new IMessageSubscription() {
                @Override
                public void cancel() {
                }

                @Override
                public void suspend() {
                }

                @Override
                public void resume() {
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public boolean isSuspended() {
                    return false;
                }
            };
        }

        void deliver(ApiResponse<?> response) {
            IMessageConsumeContext ctx = null;
            consumer.onMessage("reply-topic", response, ctx);
        }
    }

    DefaultScheduledExecutor timer;
    StubMessageService messageService;
    MessageRpcClient client;
    RpcChannelState<ApiRequest<?>, ApiResponse<?>> channelState;

    @BeforeEach
    void setUp() {
        timer = DefaultScheduledExecutor.newSingleThreadTimer("test-rpc-timer");
        messageService = new StubMessageService();
        channelState = new RpcChannelState<>("test-channel", timer);
        client = new MessageRpcClient();
        client.setMessageAdapter(DefaultRpcMessageAdapter.INSTANCE);
        client.setMessageService(messageService);
        client.setTopic("rpc-topic");
        client.setChannelState(channelState);
        client.start();
    }

    @AfterEach
    void tearDown() {
        client.stop();
        timer.destroy();
    }

    @Test
    public void testRequestWithoutTimeoutGetsResponse() throws Exception {
        // 未设置 nop-timeout 头（getTimeout 返回 -1）的请求不应立即超时
        ApiRequest<Object> request = new ApiRequest<>();
        CompletableFuture<ApiResponse<?>> future = client.callAsync("myMethod", request, null)
                .toCompletableFuture();

        String id = ApiHeaders.getId(messageService.lastRequest);
        assertNotNull(id, "client should synthesize a request id when none set");
        ApiResponse<String> response = ApiResponse.success("ok");
        ApiHeaders.setRelId(response, id);
        messageService.deliver(response);

        ApiResponse<?> ret = future.get(5, TimeUnit.SECONDS);
        assertEquals("ok", ret.getData());
    }

    @Test
    public void testSendFailureFailsPromise() throws Exception {
        messageService.failSend = true;
        ApiRequest<Object> request = new ApiRequest<>();
        CompletableFuture<ApiResponse<?>> future = client.callAsync("myMethod", request, null)
                .toCompletableFuture();

        // 发送失败必须传播到调用方，而不是等到超时
        CompletableFuture<Object> any = future.handle((r, e) -> e);
        Object result = any.get(5, TimeUnit.SECONDS);
        assertTrue(result instanceof Throwable, "promise should complete exceptionally");
    }
}
