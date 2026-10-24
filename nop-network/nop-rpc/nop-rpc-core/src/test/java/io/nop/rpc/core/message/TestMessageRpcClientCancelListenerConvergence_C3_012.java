package io.nop.rpc.core.message;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CRA 4.3-C3-012（plan 2499 Phase 3 子分片 B，C10-010 随单）：MessageRpcClient 的取消
 * 监听器经平台单点 {@code FutureHelper.bindCancelToken} 收敛——注册与 remove 按 promise
 * 终态配对，长生命周期 cancelToken 的监听器不再随请求次数无界累积（round2 扩展站点）。
 */
@Timeout(20)
public class TestMessageRpcClientCancelListenerConvergence_C3_012 {

    static class CountingCancelToken implements ICancelToken {
        final List<Consumer<String>> tasks = new CopyOnWriteArrayList<>();

        int listenerCount() {
            return tasks.size();
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public String getCancelReason() {
            return "test-cancel";
        }

        @Override
        public void appendOnCancel(Consumer<String> task) {
            tasks.add(task);
        }

        @Override
        public void removeOnCancel(Consumer<String> task) {
            tasks.remove(task);
        }
    }

    DefaultScheduledExecutor timer;
    TestMessageRpcClientFix.StubMessageService messageService;
    MessageRpcClient client;
    RpcChannelState<ApiRequest<?>, ApiResponse<?>> channelState;

    @BeforeEach
    void setUp() {
        timer = DefaultScheduledExecutor.newSingleThreadTimer("test-rpc-timer-c312");
        messageService = new TestMessageRpcClientFix.StubMessageService();
        channelState = new RpcChannelState<>("test-channel-c312", timer);
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

    /**
     * C10-010 判别用例：长生命周期 token 上连续 8 次「请求→应答」往返，每次 promise
     * 终态后监听器归零。修复前监听器逐次累积（第 1 次断言即红）；修复后终态反注册（绿）。
     */
    @Test
    public void testMessageRpcClientDoesNotAccumulateListenersOnLongLivedToken() throws Exception {
        CountingCancelToken token = new CountingCancelToken();

        for (int i = 0; i < 8; i++) {
            ApiRequest<Object> request = new ApiRequest<>();
            CompletableFuture<ApiResponse<?>> future = client
                    .callAsync("myMethod", request, token).toCompletableFuture();

            ApiResponse<String> response = ApiResponse.success("ok-" + i);
            ApiHeaders.setRelId(response, ApiHeaders.getId(messageService.lastRequest));
            messageService.deliver(response);

            future.get(5, TimeUnit.SECONDS);
            assertEquals(0, token.listenerCount(),
                    "listener must be removed at promise completion (round " + i + "), "
                            + "count=" + token.listenerCount());
        }
    }
}
