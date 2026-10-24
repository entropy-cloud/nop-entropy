package io.nop.http.client.jdk;

import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 4.3-C3-012（plan 2499 Phase 3 子分片 B，C10-010 随单）：JdkHttpClient 的取消监听器
 * 经平台单点 {@code FutureHelper.bindCancelToken} 收敛——注册与 remove 按 promise 终态
 * 配对，长生命周期 cancelToken 的监听器不再随请求次数无界累积（审计站点 fetchAsync/
 * downloadAsync/uploadByFile/uploadByBase64Form 共 4 处，本用例覆盖 fetchAsync 面，
 * 其余 3 处与 fetchAsync 共用同一收敛入口形态）。
 * <p>
 * 请求目标为拒绝连接的本地端口：promise 以异常终态完成，反注册同样触发（终态配对不区分
 * 正常/异常完成）。
 */
public class TestJdkHttpClientCancelListenerCleanup_C3_012 {

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

    JdkHttpClient client;

    @BeforeEach
    public void setUp() {
        HttpClientConfig config = new HttpClientConfig();
        client = new JdkHttpClient(config);
        client.start();
    }

    @AfterEach
    public void tearDown() {
        client.stop();
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
        assertTrue(token.listenerCount() == 0, "no residue after all rounds");
    }
}
