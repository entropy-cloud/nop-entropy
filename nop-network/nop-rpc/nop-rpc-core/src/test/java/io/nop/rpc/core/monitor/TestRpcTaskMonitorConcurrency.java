package io.nop.rpc.core.monitor;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.util.ProcessResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.3-MA-13（plan 2560 裁定 F）：RpcTaskMonitor 并发面载体。
 * <p>
 * 缺陷面：checkFailCount RMW（原 RpcTaskMonitor.handleTaskStatus 对 plain int 字段
 * read-then-write，多任务回调并发落在同一 RpcTask 上时丢失更新）+ cancellable 字段
 * 跨线程读写（doStop 线程 vs checkTaskStatus 轮次线程）缺 volatile 可见性保证。
 * <p>
 * 判别口径（新造即红）：并发 N 线程对同一 task 执行失败状态记账 N*T 次——修复前
 * plain int 丢更新（计数 &lt; N*T，红）；修复后 AtomicInteger 原子递增（恰好 N*T，绿）。
 * 变异面：回退 incrementCheckFailCount 为 set(get()+1) 即红。
 */
public class TestRpcTaskMonitorConcurrency {

    static RpcTaskMonitor monitorWithStorage() {
        RpcTaskMonitor monitor = new RpcTaskMonitor();
        monitor.setStatusStorage(new IRpcTaskStatusStore() {
            @Override
            public void saveTask(RpcTask task) {
            }

            @Override
            public void fetchActiveTasks(java.util.function.Consumer<RpcTask> taskConsumer) {
            }

            @Override
            public ProcessResult saveTaskStatus(RpcTask task, ApiResponse<TaskStatusBean> res, Throwable err) {
                return ProcessResult.CONTINUE;
            }
        });
        return monitor;
    }

    /**
     * 跨轮 RMW 面判定：并发失败记账无丢失更新（AtomicInteger 原子递增）。
     */
    @Test
    public void testConcurrentFailCountAccountingIsAtomic() throws Exception {
        RpcTaskMonitor monitor = monitorWithStorage();
        RpcTask task = new RpcTask();
        task.setTaskId("t-rmw");

        int threads = 8;
        int rounds = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int r = 0; r < rounds; r++) {
                            // 失败状态记账（res=null 走 incrementCheckFailCount 分支）
                            monitor.handleTaskStatus(task, null, new RuntimeException("check fail"));
                        }
                        done.countDown();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "all accounting threads must finish");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(threads * rounds, task.getCheckFailCount(),
                "concurrent fail-count accounting must not lose updates (4.3-MA-13 裁定 F)");
    }

    /**
     * 成功状态清零语义零回退：失败计数经成功记账回到 0。
     */
    @Test
    public void testSuccessResetsFailCount() {
        RpcTaskMonitor monitor = monitorWithStorage();
        RpcTask task = new RpcTask();
        task.setTaskId("t-reset");

        monitor.handleTaskStatus(task, null, new RuntimeException("fail"));
        monitor.handleTaskStatus(task, null, new RuntimeException("fail"));
        assertEquals(2, task.getCheckFailCount());

        TaskStatusBean bean = new TaskStatusBean();
        monitor.handleTaskStatus(task, ApiResponse.success(bean), null);
        assertEquals(0, task.getCheckFailCount(), "success must reset the fail count");
    }

    /**
     * volatile 可见性面（裁定 F :41/:92）：cancellable 字段必须 volatile——doStop 线程
     * 与轮次线程的跨线程读回归钉（变异面：摘除 volatile 即红）。
     */
    @Test
    public void testCancellableFieldIsVolatile() throws Exception {
        java.lang.reflect.Field f = RpcTaskMonitor.class.getDeclaredField("cancellable");
        assertTrue(Modifier.isVolatile(f.getModifiers()),
                "cancellable must be volatile for cross-thread visibility (doStop vs checkTaskStatus)");
    }
}
