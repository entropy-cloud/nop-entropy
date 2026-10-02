package io.nop.core.execution;

import io.nop.api.core.util.progress.IProgressListener;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.concurrent.executor.ThreadPoolStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDefaultTaskExecutionQueue {

    /**
     * 手动排空执行器：execute 只入队，测试中显式 drain 在调用线程内执行任务。
     * 完全确定性，无后台线程；同时避免同步执行与 computeIfAbsent 的重入冲突。
     */
    static class ManualExecutor implements IThreadPoolExecutor {
        final List<Runnable> pending = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            pending.add(command);
        }

        void drainPending() {
            while (!pending.isEmpty()) {
                pending.remove(0).run();
            }
        }

        @Override
        public String getName() {
            return "manual-test-executor";
        }

        @Override
        public ThreadPoolConfig getConfig() {
            return null;
        }

        @Override
        public ThreadPoolStats stats() {
            return null;
        }

        @Override
        public <V> CompletableFuture<V> submit(Callable<V> callable) {
            try {
                return CompletableFuture.completedFuture(callable.call());
            } catch (Exception e) {
                CompletableFuture<V> ret = new CompletableFuture<>();
                ret.completeExceptionally(e);
                return ret;
            }
        }

        @Override
        public <V> CompletableFuture<V> submit(Runnable task, V result) {
            task.run();
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public void refreshConfig() {
        }

        @Override
        public void destroy() {
        }
    }

    @Test
    @Timeout(10)
    public void testAddTaskExecutesAndCompletesAfterDrain() throws Exception {
        ManualExecutor executor = new ManualExecutor();
        DefaultTaskExecutionQueue queue = new DefaultTaskExecutionQueue();
        queue.setExecutor(executor);

        ITaskExecutionState state = queue.addTaskIfAbsent("t1", "task1", "src", "desc",
                cancelToken -> CompletableFuture.completedFuture("ok"));

        assertNotNull(state);
        assertEquals("t1", state.getTaskRef());
        assertEquals("task1", state.getTaskName());
        assertEquals("src", state.getSource());
        assertEquals("desc", state.getDescription());
        assertFalse(state.isStarted(), "task should not start before drain");

        assertEquals(1, queue.getCurrentTaskCount());
        assertEquals(1, queue.getPendingTaskCount());

        executor.drainPending();

        assertTrue(state.isStarted(), "task should start after drain");
        assertEquals("ok", state.getPromise().get(1, TimeUnit.SECONDS));
        assertTrue(state.getPromise().isDone());

        // 完成后从 states 移除
        assertEquals(0, queue.getCurrentTaskCount());
        assertEquals(1, queue.getCompletedTaskCount());
        assertEquals(0, queue.getFailedTaskCount());
        assertEquals(0, queue.getRunningTaskCount());
        assertEquals(1, queue.getTotalTaskCount());
        assertNotNull(state.getStartTime());
        assertNotNull(state.getQueueTime());
    }

    @Test
    @Timeout(10)
    public void testAddTaskIfAbsentReturnsSameStateForSameRef() {
        ManualExecutor executor = new ManualExecutor();
        DefaultTaskExecutionQueue queue = new DefaultTaskExecutionQueue();
        queue.setExecutor(executor);

        ITaskExecutionState first = queue.addTaskIfAbsent("dup", "n", "s", "d",
                cancelToken -> CompletableFuture.completedFuture(1));
        ITaskExecutionState second = queue.addTaskIfAbsent("dup", "n", "s", "d",
                cancelToken -> CompletableFuture.completedFuture(2));

        assertSame(first, second, "same taskRef should not enqueue a second task");
        assertEquals(1, queue.getTotalTaskCount(), "second add should not enqueue");
        assertEquals(1, executor.pending.size(), "only one runnable should be queued");
        executor.drainPending();
    }

    @Test
    @Timeout(10)
    public void testFailedTaskCountedAndRecorded() throws Exception {
        ManualExecutor executor = new ManualExecutor();
        DefaultTaskExecutionQueue queue = new DefaultTaskExecutionQueue();
        queue.setExecutor(executor);

        ITaskExecutionState state = queue.addTaskIfAbsent("f1", "fail-task", "src", "d",
                cancelToken -> {
                    CompletableFuture<Object> f = new CompletableFuture<>();
                    f.completeExceptionally(new RuntimeException("boom"));
                    return f;
                });

        executor.drainPending();

        assertTrue(state.getPromise().isDone());
        assertTrue(state.getPromise().isCompletedExceptionally());
        assertEquals(1, queue.getFailedTaskCount());
        assertEquals(1, queue.getCompletedTaskCount(), "failed tasks count as completed");
        assertEquals(0, queue.getCurrentTaskCount());
    }

    @Test
    @Timeout(10)
    public void testSyncExceptionInTaskMarksFailed() {
        ManualExecutor executor = new ManualExecutor();
        DefaultTaskExecutionQueue queue = new DefaultTaskExecutionQueue();
        queue.setExecutor(executor);

        ITaskExecutionState state = queue.addTaskIfAbsent("f2", "sync-fail", "src", "d",
                cancelToken -> {
                    throw new IllegalStateException("sync boom");
                });

        executor.drainPending();

        assertTrue(state.getPromise().isCompletedExceptionally(),
                "sync exception should complete promise exceptionally");
        assertEquals(1, queue.getFailedTaskCount());
        assertEquals(0, queue.getCurrentTaskCount());
    }

    @Test
    @Timeout(10)
    public void testGetTaskStatesAndFilters() {
        ManualExecutor executor = new ManualExecutor();
        DefaultTaskExecutionQueue queue = new DefaultTaskExecutionQueue();
        queue.setExecutor(executor);
        // 不 drain：任务保持 pending 状态
        queue.addTaskIfAbsent("p1", "nameA", "srcX", "d", cancelToken -> new CompletableFuture<>());
        queue.addTaskIfAbsent("p2", "nameB", "srcX", "d", cancelToken -> new CompletableFuture<>());
        queue.addTaskIfAbsent("p3", "nameC", "srcY", "d", cancelToken -> new CompletableFuture<>());

        assertEquals(3, queue.getCurrentTaskCount());
        assertEquals(0, queue.getRunningTaskCount());
        assertEquals(3, queue.getPendingTaskCount());

        assertEquals(2, queue.getTaskStatesBySource("srcX").size());
        assertEquals(1, queue.getTaskStatesBySource("srcY").size());
        assertEquals("nameB", queue.getTaskStatesByName("nameB").get(0).getTaskName());
        assertNotNull(queue.getTaskState("p1"));
        assertNull(queue.getTaskState("unknown"));
        assertEquals(3, queue.getTaskStates().size());

        // 未完成任务不能 remove
        assertFalse(queue.removeCompletedTask("p1"));
    }

    @Test
    @Timeout(10)
    public void testRemoveCompletedTask() {
        ManualExecutor executor = new ManualExecutor();
        DefaultTaskExecutionQueue queue = new DefaultTaskExecutionQueue();
        queue.setExecutor(executor);
        queue.addTaskIfAbsent("done", "n", "s", "d",
                cancelToken -> CompletableFuture.completedFuture(1));
        executor.drainPending();

        // 完成任务在 whenComplete 中已被自动移除，因此 removeCompletedTask 无可移除项
        assertNull(queue.getTaskState("done"), "completed task should be auto-removed");
        assertFalse(queue.removeCompletedTask("done"), "nothing left to remove");

        queue.addTaskIfAbsent("done2", "n", "s", "d",
                cancelToken -> CompletableFuture.completedFuture(1));
        executor.drainPending();
        queue.removeAllCompletedTasks();
        assertNull(queue.getTaskState("done2"));
    }

    @Test
    @Timeout(10)
    public void testWaitForTaskTimesOutOnPending() throws Exception {
        ManualExecutor executor = new ManualExecutor();
        DefaultTaskExecutionQueue queue = new DefaultTaskExecutionQueue();
        queue.setExecutor(executor);
        queue.addTaskIfAbsent("w1", "n", "s", "d", cancelToken -> new CompletableFuture<>());

        // 未 drain：promise 未完成 -> 超时返回 null
        assertNull(queue.waitForTask("w1", 10, TimeUnit.MILLISECONDS));

        executor.drainPending();
        // 任务 future 永不完成：状态保留、running 计数为 1，waitForTask 超时仍返回 null
        assertNotNull(queue.getTaskState("w1"), "incomplete task state should remain");
        assertEquals(1, queue.getRunningTaskCount());
        assertNull(queue.waitForTask("w1", 10, TimeUnit.MILLISECONDS));

        // 已取消任务：promise 被 cancel，waitForTask 内 get() 抛 CancellationException（未被捕获，向外传播）
        ManualExecutor executor2 = new ManualExecutor();
        DefaultTaskExecutionQueue queue2 = new DefaultTaskExecutionQueue();
        queue2.setExecutor(executor2);
        ITaskExecutionState cancelled = queue2.addTaskIfAbsent("wc", "n", "s", "d",
                cancelToken -> new CompletableFuture<>());
        cancelled.cancel("test-cancel");
        org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.CancellationException.class,
                () -> queue2.waitForTask("wc", 1, TimeUnit.SECONDS));

        // 状态不存在的 taskRef -> null
        assertNull(queue2.waitForTask("missing", 1, TimeUnit.MILLISECONDS));
    }

    @Test
    @Timeout(10)
    public void testReplaceTaskCancelsOldState() throws Exception {
        ManualExecutor executor = new ManualExecutor();
        DefaultTaskExecutionQueue queue = new DefaultTaskExecutionQueue();
        queue.setExecutor(executor);
        ITaskExecutionState old = queue.addTaskIfAbsent("r", "n", "s", "d",
                cancelToken -> CompletableFuture.completedFuture(1));

        ITaskExecutionState replaced = queue.replaceTask("r", "n2", "s", "d",
                cancelToken -> CompletableFuture.completedFuture(2));

        assertSame(replaced, queue.getTaskState("r"));
        assertTrue(old.isCancelled(), "old state should be cancelled on replace");
        assertEquals(1, queue.getCancelledTaskCount());
        executor.drainPending();
        assertEquals(2, replaced.getPromise().get(1, TimeUnit.SECONDS));
    }

    @Test
    @Timeout(10)
    public void testTaskQueueStats() {
        ManualExecutor executor = new ManualExecutor();
        DefaultTaskExecutionQueue queue = new DefaultTaskExecutionQueue();
        queue.setExecutor(executor);
        queue.addTaskIfAbsent("s1", "n", "s", "d",
                cancelToken -> CompletableFuture.completedFuture(1));
        executor.drainPending();

        TaskQueueStats stats = queue.getTaskQueueStats();
        assertEquals(0, stats.getCurrentTaskCount());
        assertEquals(1, stats.getCompletedTaskCount());
        assertEquals(1, stats.getTotalTaskCount());
        assertEquals(0, stats.getFailedTaskCount());
        assertEquals(0, stats.getCancelledTaskCount());
    }

    @Test
    @Timeout(10)
    public void testStateProgressListener() {
        ManualExecutor executor = new ManualExecutor();
        DefaultTaskExecutionQueue queue = new DefaultTaskExecutionQueue();
        queue.setExecutor(executor);
        ITaskExecutionState state = queue.addTaskIfAbsent("prog", "n", "s", "d",
                cancelToken -> CompletableFuture.completedFuture(1));

        IProgressListener listener = (IProgressListener) state;
        listener.onProgress("step", 3, 10);
        assertEquals("step", state.getProgressMessage());
        assertEquals(3, state.getCurrentProgress());
        assertEquals(10, state.getProgressTotal());
        assertNotNull(state.getQueueTime());
        assertEquals("d", state.getDescription());
    }
}
