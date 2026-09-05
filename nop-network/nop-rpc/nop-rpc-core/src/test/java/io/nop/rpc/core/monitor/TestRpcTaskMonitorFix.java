package io.nop.rpc.core.monitor;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.rpc.IRpcService;
import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import io.nop.rpc.core.utils.RpcHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

@Timeout(20)
public class TestRpcTaskMonitorFix {

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
    public void testToTaskStatusResponseNullSafe() {
        // callAsync 异常完成时 ret == null，toTaskStatusResponse 不应 NPE
        assertNull(assertDoesNotThrow(() -> RpcHelper.toTaskStatusResponse(null)));
    }

    @Test
    public void testCheckTaskStatusSurvivesSyncException() throws Exception {
        RpcTaskMonitor monitor = new RpcTaskMonitor();
        monitor.setTimer(timer);
        monitor.setStatusStorage(new IRpcTaskStatusStore() {
            @Override
            public void saveTask(RpcTask task) {
            }

            @Override
            public void fetchActiveTasks(java.util.function.Consumer<RpcTask> handler) {
            }

            @Override
            public io.nop.api.core.util.ProcessResult saveTaskStatus(RpcTask task,
                                                                      ApiResponse<io.nop.api.core.beans.task.TaskStatusBean> response,
                                                                      Throwable err) {
                return io.nop.api.core.util.ProcessResult.CONTINUE;
            }
        });

        AtomicInteger calls = new AtomicInteger();
        IRpcService throwingService = new IRpcService() {
            @Override
            public CompletableFuture<ApiResponse<?>> callAsync(String serviceMethod, io.nop.api.core.beans.ApiRequest<?> request,
                                                                io.nop.api.core.util.ICancelToken cancelToken) {
                calls.incrementAndGet();
                // 模拟底层 channel 关闭时 prepareSend 的同步抛出
                throw new IllegalStateException("channel closed");
            }
        };

        RpcTask task = new RpcTask();
        task.setTaskId("t1");
        task.setStatusMethod("getStatus");
        task.setRequest(new io.nop.api.core.beans.ApiRequest<>());
        task.setRpcService(throwingService);
        task.setCancelMethod("cancel");

        Method addTask0 = RpcTaskMonitor.class.getDeclaredMethod("addTask0", RpcTask.class);
        addTask0.setAccessible(true);
        addTask0.invoke(monitor, task);

        Method check = RpcTaskMonitor.class.getDeclaredMethod("checkTaskStatus");
        check.setAccessible(true);
        // 单个任务的同步异常不应逃逸并杀死整个监控循环
        assertDoesNotThrow(() -> check.invoke(monitor));
    }
}
