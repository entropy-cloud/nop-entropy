package io.nop.rpc.core.composite;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 4.3-C3-012（plan 2499 Phase 3 子分片 B，C10-010 随单）：取消监听器经平台单点
 * {@code FutureHelper.bindCancelToken} 收敛——注册与 remove 按 future 终态配对。
 * <p>
 * 缺陷：CancellableRpcClient 对长生命周期 cancelToken 只增不删监听器，监听器随请求次数
 * 无界累积（每条目钉住 future→response 对象图），token 取消时 O(n) 遍历。
 * <p>
 * 判别口径（C10-010）：单一长生命周期 token 上连续 N 次请求，每次请求终态后监听器数量
 * 必须归零。修复前监听器单调累积（先红）；修复后配对反注册（绿）。
 */
public class TestRpcClientCancelListenerConvergence_C3_012 {

    static class CountingCancelToken implements ICancelToken {
        final List<Consumer<String>> tasks = new CopyOnWriteArrayList<>();
        volatile boolean cancelled;
        volatile String reason;

        void cancelNow(String cancelReason) {
            this.cancelled = true;
            this.reason = cancelReason;
            for (Consumer<String> task : tasks) {
                task.accept(cancelReason);
            }
        }

        int listenerCount() {
            return tasks.size();
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public String getCancelReason() {
            return reason;
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

    static class OkService implements IRpcService {
        @Override
        public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request,
                                                         ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(ApiResponse.success("ok"));
        }
    }

    /**
     * C10-010 判别用例：长生命周期 token 上连续 8 次请求，每次终态后监听器归零。
     * 修复前监听器逐次累积（第 1 次断言即红）；修复后 bindCancelToken 终态反注册（绿）。
     */
    @Test
    public void testCancellableRpcClientDoesNotAccumulateListenersOnLongLivedToken() throws Exception {
        CountingCancelToken token = new CountingCancelToken();
        CancellableRpcClient client = new CancellableRpcClient(new OkService(), "cancelMethod");

        for (int i = 0; i < 8; i++) {
            client.callAsync("svc__method", new ApiRequest<>(), token)
                    .toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(0, token.listenerCount(),
                    "listener must be removed at future completion (round " + i + "), "
                            + "count=" + token.listenerCount());
        }
    }

    /**
     * 真取消路径回归：挂起请求上 token 取消 → 监听器触发（cancelMethod 语义保持）；
     * 后续 future 完成时监听器仍被反注册，不留残留。
     */
    @Test
    public void testCancelStillPropagatesAndListenerRemovedAtCompletion() throws Exception {
        CountingCancelToken token = new CountingCancelToken();
        CompletableFuture<ApiResponse<?>> pending = new CompletableFuture<>();
        IRpcService hangService = (method, request, cancelToken) -> pending;
        CancellableRpcClient client = new CancellableRpcClient(hangService, "cancelMethod");

        CompletableFuture<ApiResponse<?>> call = client
                .callAsync("svc__method", new ApiRequest<>(), token).toCompletableFuture();
        assertEquals(1, token.listenerCount(), "pending call registers exactly one listener");

        token.cancelNow("caller-cancelled");
        // 挂起请求上取消传播保持：cancelMethod 触发链不受收敛改造影响（由既有
        // TestCancellableRpcClient 覆盖），此处钉监听器仍然在位（future 未完成）
        assertEquals(1, token.listenerCount(), "listener stays while the future is pending");

        pending.complete(ApiResponse.success("late"));
        call.get(2, TimeUnit.SECONDS);
        assertEquals(0, token.listenerCount(), "listener removed once the future completes");
        assertTrue(token.isCancelled());
    }
}
