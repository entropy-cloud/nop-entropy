package io.nop.rpc.core.composite;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(20)
public class TestPollingRpcClientFix {

    static class MutableCancelToken implements ICancelToken {
        volatile boolean cancelled;
        final java.util.List<Consumer<String>> tasks = new java.util.concurrent.CopyOnWriteArrayList<>();

        void cancelNow() {
            cancelled = true;
            for (Consumer<String> task : tasks) {
                task.accept(getCancelReason());
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public String getCancelReason() {
            return "test";
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

    static ApiResponse<Object> runningResponse() {
        TaskStatusBean status = new TaskStatusBean();
        status.setTaskId("t1");
        status.setTaskState("RUNNING");
        return ApiResponse.success(status);
    }

    DefaultScheduledExecutor timer;

    @BeforeEach
    void setUp() {
        timer = DefaultScheduledExecutor.newSingleThreadTimer("test-rpc-timer");
    }

    @AfterEach
    void tearDown() {
        timer.destroy();
    }

    @Test
    public void testSyncThrowInPollCompletesCallerFuture() {
        // 首次调用返回 RUNNING 启动轮询；后续 statusMethod 调用同步抛出
        IRpcService rpcService = (serviceMethod, request, cancelToken) -> {
            if (serviceMethod.equals("start")) {
                return CompletableFuture.completedFuture(runningResponse());
            }
            throw new IllegalStateException("channel closed during poll");
        };

        PollingRpcClient client = new PollingRpcClient(rpcService, "getStatus", timer, 20, 2);
        CompletableFuture<ApiResponse<?>> future = client.callAsync("start", new ApiRequest<>(), null)
                .toCompletableFuture();

        Object done = assertDoesNotThrow(() -> future.handle((r, e) -> e).get(5, TimeUnit.SECONDS),
                "caller future must complete even if the poll call throws synchronously");
        assertTrue(done instanceof Throwable, "should complete exceptionally, got: " + done);
    }

    @Test
    public void testPollingStopsAfterCancel() throws Exception {
        MutableCancelToken cancelToken = new MutableCancelToken();
        AtomicInteger statusCalls = new AtomicInteger();

        IRpcService rpcService = (serviceMethod, request, token) -> {
            if (serviceMethod.equals("start")) {
                return CompletableFuture.completedFuture(runningResponse());
            }
            statusCalls.incrementAndGet();
            return CompletableFuture.completedFuture(runningResponse());
        };

        PollingRpcClient client = new PollingRpcClient(rpcService, "getStatus", timer, 20, 100000);
        CompletableFuture<ApiResponse<?>> future = client.callAsync("start", new ApiRequest<>(), cancelToken)
                .toCompletableFuture();

        // 等轮询开始后取消
        for (int i = 0; i < 100 && statusCalls.get() == 0; i++) {
            Thread.sleep(20);
        }
        cancelToken.cancelNow();
        future.cancel(false);

        Thread.sleep(100);
        int callsAfterCancel = statusCalls.get();
        Thread.sleep(400);
        assertTrue(statusCalls.get() <= callsAfterCancel + 1,
                "status calls must stop after cancel: before=" + callsAfterCancel + " after=" + statusCalls.get());
    }
}
