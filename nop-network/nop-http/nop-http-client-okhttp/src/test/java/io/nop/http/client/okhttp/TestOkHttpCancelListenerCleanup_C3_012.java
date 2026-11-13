package io.nop.http.client.okhttp;

import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CRA 4.3-C3-012（plan 2499 Phase 3 子分片 B，C10-010 随单）：OkHttpClientImpl 的取消
 * 监听器经平台单点 {@code FutureHelper.bindCancelToken} 收敛——注册与 remove 按 promise
 * 终态配对（审计站点 fetchAsync/doDownloadAsync/upload 共 3 处，本用例覆盖 fetchAsync 面，
 * 其余 2 处与 fetchAsync 共用同一收敛入口形态）。
 */
public class TestOkHttpCancelListenerCleanup_C3_012 {

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

    OkHttpClientImpl client;

    @BeforeEach
    public void setUp() {
        client = new OkHttpClientImpl(new OkHttpClient.Builder().build(), new HttpClientConfig());
    }

    @Test
    public void testFetchAsyncDoesNotAccumulateListenersOnLongLivedToken() throws Exception {
        CountingCancelToken token = new CountingCancelToken();

        for (int i = 0; i < 5; i++) {
            HttpRequest request = new HttpRequest();
            request.setUrl("http://127.0.0.1:1/no-such-endpoint");

            try {
                client.fetchAsync(request, token).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // 连接拒绝：promise 异常终态同样触发反注册
            }
            assertEquals(0, token.listenerCount(),
                    "listener must be removed at promise completion (round " + i + "), "
                            + "count=" + token.listenerCount());
        }
    }
}
