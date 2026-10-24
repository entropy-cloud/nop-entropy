package io.nop.rpc.core.composite;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.api.core.rpc.IRpcService;
import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.2-C4-003（plan 2498 Phase 6）：PollingRpcClient 可选整体 deadline——
 * deadline 到期调用方 future 异常完成并停止续排（cancelToken 缺失亦强制终止链）；
 * 既有 maxErrorCount 出口在 deadline 配置下零回退。
 */
@Timeout(30)
public class TestPollingRpcClientDeadline_C4_003 {

    static ApiResponse<Object> runningResponse() {
        TaskStatusBean status = new TaskStatusBean();
        status.setTaskId("t1");
        status.setTaskState("RUNNING");
        return ApiResponse.success(status);
    }

    static ApiResponse<Object> completedResponse() {
        TaskStatusBean status = new TaskStatusBean();
        status.setTaskId("t1");
        status.setTaskState("COMPLETED");
        status.setTaskStatus(TaskStatusBean.STATUS_SUCCESS);
        return ApiResponse.success(status);
    }

    DefaultScheduledExecutor timer;

    @BeforeAll
    static void initJsonProvider() {
        // PollTask tick 的 LOG.debug 分支使用 JSON.serialize，裸 JUnit 环境需注册解析器
        JSON.registerProvider(JsonTool.instance());
    }

    @BeforeEach
    void setUp() {
        timer = DefaultScheduledExecutor.newSingleThreadTimer("test-poll-deadline-timer");
    }

    @AfterEach
    void tearDown() {
        timer.destroy();
    }

    /**
     * Exit ②：deadline 到期链终止断言——cancelToken 为 null（缺失）时同样强制终止：
     * future 异常完成（错误键 nop.err.rpc.poll-deadline-exceeded）且 timer 队列不再续排。
     */
    @Test
    public void deadlineTerminatesChainWhenCancelTokenAbsent_C4_003() throws Exception {
        AtomicInteger statusCalls = new AtomicInteger();
        IRpcService rpcService = (serviceMethod, request, cancelToken) -> {
            if (serviceMethod.equals("start"))
                return CompletableFuture.completedFuture(runningResponse());
            statusCalls.incrementAndGet();
            return CompletableFuture.completedFuture(runningResponse());
        };

        // pollTimeout=200ms 远大于 pollInterval=20ms：deadline 前链已续排多轮
        PollingRpcClient client = new PollingRpcClient(rpcService, "getStatus", timer, 20, 100000, 200);
        CompletableFuture<ApiResponse<?>> future = client.callAsync("start", new ApiRequest<>(), null)
                .toCompletableFuture();

        Throwable err = future.handle((r, e) -> e).get(3, TimeUnit.SECONDS);
        assertTrue(err != null, "deadline must terminate the polling chain exceptionally");
        while (err instanceof CompletionException && err.getCause() != null)
            err = err.getCause();
        assertTrue(err instanceof NopException, "expected NopException, got: " + err);
        assertEquals("nop.err.rpc.poll-deadline-exceeded", ((NopException) err).getErrorCode());

        // 链终止后 timer 队列不再续排：观察窗口内 tick 计数不再增长
        int callsAfterDeadline = statusCalls.get();
        Thread.sleep(300);
        assertTrue(statusCalls.get() <= callsAfterDeadline,
                "no further rescheduling after deadline: before=" + callsAfterDeadline
                        + " after=" + statusCalls.get());
    }

    /**
     * deadline watchdog 兜底：在飞轮次挂死（底层 callAsync 永不完成、无 cancelToken）时，
     * 链同样在 deadline 处有界终止——「无整体截止」面闭合的强形态。
     */
    @Test
    public void deadlineWatchdogTerminatesChainWhenPollCallHangs_C4_003() throws Exception {
        IRpcService hangingRpcService = (serviceMethod, request, cancelToken) -> {
            if (serviceMethod.equals("start"))
                return CompletableFuture.completedFuture(runningResponse());
            return new CompletableFuture<>(); // 永不完成
        };

        PollingRpcClient client = new PollingRpcClient(hangingRpcService, "getStatus", timer, 20, 100000, 200);
        long begin = System.nanoTime();
        CompletableFuture<ApiResponse<?>> future = client.callAsync("start", new ApiRequest<>(), null)
                .toCompletableFuture();

        Throwable err = future.handle((r, e) -> e).get(3, TimeUnit.SECONDS);
        long elapsedMillis = (System.nanoTime() - begin) / 1_000_000;
        assertTrue(err != null, "watchdog must terminate the hung chain at deadline");
        assertTrue(elapsedMillis < 2000, "chain must be bounded by deadline, elapsed=" + elapsedMillis + "ms");
    }

    /**
     * 既有出口零回退钉：deadline 配置下 maxErrorCount 出口语义不变（错误风暴仍按既有阈值终止）。
     */
    @Test
    public void maxErrorCountExitUnaffectedByDeadline_C4_003() throws Exception {
        IRpcService rpcService = (serviceMethod, request, cancelToken) -> {
            if (serviceMethod.equals("start"))
                return CompletableFuture.completedFuture(runningResponse());
            throw new IllegalStateException("status channel broken");
        };

        // deadline=10s 不会先到期：链终止只能由 maxErrorCount 出口承载
        PollingRpcClient client = new PollingRpcClient(rpcService, "getStatus", timer, 20, 2, 10000);
        CompletableFuture<ApiResponse<?>> future = client.callAsync("start", new ApiRequest<>(), null)
                .toCompletableFuture();

        Object done = future.handle((r, e) -> e).get(3, TimeUnit.SECONDS);
        assertTrue(done != null, "maxErrorCount exit must still terminate the chain");
        assertTrue(done instanceof Throwable, "expected exceptional completion via maxErrorCount");
    }

    /**
     * 5 参向后兼容构造器零回退钉：不启用 deadline 时（既有形态）链不受影响，
     * 状态完成即正常结束（chain completes normally on completed status）。
     */
    @Test
    public void legacyConstructorCompletesNormallyWithoutDeadline_C4_003() throws Exception {
        IRpcService rpcService = (serviceMethod, request, cancelToken) ->
                CompletableFuture.completedFuture(completedResponse());

        PollingRpcClient client = new PollingRpcClient(rpcService, "getStatus", timer, 20, 5);
        ApiResponse<?> ret = client.callAsync("start", new ApiRequest<>(), null)
                .toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertTrue(ret.isBizSuccess(), "legacy constructor semantics unchanged");
    }
}
