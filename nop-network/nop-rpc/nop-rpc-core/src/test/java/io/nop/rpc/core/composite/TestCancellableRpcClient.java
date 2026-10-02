package io.nop.rpc.core.composite;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 CancellableRpcClient 的可取消调用语义：
 * 无 cancelToken 时直通；请求已完成后取消不得再触发 cancelMethod；
 * 未完成时取消必须以原始 reqId 调用 cancelMethod。
 */
@Timeout(10)
public class TestCancellableRpcClient {

    static class MutableCancelToken implements ICancelToken {
        volatile boolean cancelled;
        final List<java.util.function.Consumer<String>> tasks = new CopyOnWriteArrayList<>();

        void cancelNow(String reason) {
            cancelled = true;
            for (java.util.function.Consumer<String> task : tasks) {
                task.accept(reason);
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public String getCancelReason() {
            return "test-cancel";
        }

        @Override
        public void appendOnCancel(java.util.function.Consumer<String> task) {
            tasks.add(task);
        }

        @Override
        public void removeOnCancel(java.util.function.Consumer<String> task) {
            tasks.remove(task);
        }
    }

    static class RecordingService implements IRpcService {
        final List<String> methods = new CopyOnWriteArrayList<>();
        volatile boolean hang = false;

        @Override
        public java.util.concurrent.CompletionStage<ApiResponse<?>> callAsync(
                String serviceMethod, ApiRequest<?> request, ICancelToken cancelToken) {
            methods.add(serviceMethod);
            if (hang) {
                // 模拟远端未返回：用不完成的 future，配合 cancel 语义断言
                return new java.util.concurrent.CompletableFuture<>();
            }
            return java.util.concurrent.CompletableFuture.completedFuture(ApiResponse.success("ok"));
        }
    }

    @Test
    public void testWithoutCancelTokenPassThrough() throws Exception {
        RecordingService base = new RecordingService();
        CancellableRpcClient client = new CancellableRpcClient(base, "cancelRpc");

        ApiRequest<Object> request = new ApiRequest<>();
        java.util.concurrent.CompletionStage<ApiResponse<?>> future =
                client.callAsync("doIt", request, null);

        assertEquals("ok", future.toCompletableFuture().get(5, TimeUnit.SECONDS).getData());
        assertEquals(List.of("doIt"), base.methods, "only the business method may be invoked");
    }

    @Test
    public void testCancelAfterCompletionDoesNotInvokeCancelMethod() throws Exception {
        RecordingService base = new RecordingService();
        CancellableRpcClient client = new CancellableRpcClient(base, "cancelRpc");
        MutableCancelToken token = new MutableCancelToken();

        ApiRequest<Object> request = new ApiRequest<>();
        client.callAsync("doIt", request, token).toCompletableFuture().get(5, TimeUnit.SECONDS);

        token.cancelNow("late");
        assertEquals(List.of("doIt"), base.methods,
                "cancel after completion must not invoke cancelMethod");
    }

    @Test
    public void testCancelBeforeCompletionInvokesCancelMethod() throws Exception {
        RecordingService base = new RecordingService();
        base.hang = true;
        CancellableRpcClient client = new CancellableRpcClient(base, "cancelRpc");
        MutableCancelToken token = new MutableCancelToken();

        ApiRequest<Object> request = new ApiRequest<>();
        ApiHeaders.setId(request, "req-1");
        java.util.concurrent.CompletableFuture<ApiResponse<?>> future =
                client.callAsync("doIt", request, token).toCompletableFuture();

        // 自旋等待下游调用登记后触发取消（防挂起规则：短 sleep 自旋）
        for (int i = 0; i < 100 && base.methods.isEmpty(); i++) {
            Thread.sleep(10);
        }
        token.cancelNow("user");

        // 自旋等待 cancelMethod 被调用
        AtomicBoolean cancelled = new AtomicBoolean(false);
        for (int i = 0; i < 200 && !cancelled.get(); i++) {
            cancelled.set(base.methods.contains("cancelRpc"));
            if (!cancelled.get())
                Thread.sleep(10);
        }
        assertTrue(cancelled.get(), "cancel must invoke the configured cancelMethod");
        future.cancel(false);
    }

    @Test
    public void testRequestWithoutIdGetsUuidAssigned() throws Exception {
        RecordingService base = new RecordingService();
        CancellableRpcClient client = new CancellableRpcClient(base, "cancelRpc");

        ApiRequest<Object> request = new ApiRequest<>();
        client.callAsync("doIt", request, null).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertTrue(ApiHeaders.getId(request) != null && !ApiHeaders.getId(request).isEmpty(),
                "a request id must be assigned to support later cancellation");
    }
}
