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

    /**
     * 时间确定化（plan 2514-WI8）：轮询定时器注入 {@link ManualScheduledExecutor}（虚拟时间），
     * 轮询触发与「取消后停止轮询」的复核均在显式推进的虚拟时间线上确定性发生，
     * 替代原 sleep 自旋 + 墙钟观察窗。
     */
    @org.junit.jupiter.api.Test
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

        try {
            io.nop.commons.test.time.ManualClock manualClock = new io.nop.commons.test.time.ManualClock(2_000_000L);
            io.nop.api.core.time.CoreMetrics.registerClock(manualClock);
            io.nop.commons.test.time.ManualScheduler scheduler = new io.nop.commons.test.time.ManualScheduler(manualClock);
            ManualScheduledExecutor exec = new ManualScheduledExecutor(scheduler);
            PollingRpcClient client = new PollingRpcClient(rpcService, "getStatus", exec, 20, 100000);
            CompletableFuture<ApiResponse<?>> future = client.callAsync("start", new ApiRequest<>(), cancelToken)
                    .toCompletableFuture();

            // 轮询开始（虚拟时间推进触发确定性轮询）
            scheduler.advance(100);
            int callsBeforeCancel = statusCalls.get();
            assertTrue(callsBeforeCancel >= 1, "poll must have started before cancel");

            cancelToken.cancelNow();
            future.cancel(false);

            // 推进远超轮询间隔的虚拟时间窗：取消后不得再有任何 status 调用
            scheduler.advance(400);
            assertTrue(statusCalls.get() == callsBeforeCancel,
                    "status calls must stop after cancel: before=" + callsBeforeCancel
                            + " after=" + statusCalls.get());
        } finally {
            io.nop.api.core.time.CoreMetrics.registerClock(io.nop.api.core.time.CoreMetrics.defaultClock());
        }
    }
}
