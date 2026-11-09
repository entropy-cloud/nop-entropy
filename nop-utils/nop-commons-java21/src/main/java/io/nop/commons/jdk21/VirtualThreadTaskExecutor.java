package io.nop.commons.jdk21;

import io.nop.commons.concurrent.executor.ExecutorHelper;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.concurrent.executor.ThreadPoolStats;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.nop.commons.concurrent.executor.GlobalExecutors.NOP_VIRTUAL_THREAD;

public class VirtualThreadTaskExecutor implements IThreadPoolExecutor {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final ThreadPoolConfig config = new ThreadPoolConfig();

    private VirtualThreadTaskExecutor() {
        config.setName(NOP_VIRTUAL_THREAD);
    }

    public static void registerGlobalWorker() {
        GlobalExecutors.register(new VirtualThreadTaskExecutor());
    }

    @Override
    public String getName() {
        return config.getName();
    }

    @Override
    public ThreadPoolConfig getConfig() {
        return config;
    }

    /**
     * C4-012(c)（plan 2548）：虚拟线程 per-task 模型无固定池，无池统计可报——
     * 返回零值统计（名称如实标注），不返回 null（调用方判空负担）。
     */
    @Override
    public ThreadPoolStats stats() {
        return ThreadPoolStats.ZERO;
    }

    @Override
    public <V> CompletableFuture<V> submit(Callable<V> callable) {
        return ExecutorHelper.submit(executor, callable);
    }

    @Override
    public <V> CompletableFuture<V> submit(Runnable task, V result) {
        return ExecutorHelper.submit(executor, task, result);
    }

    /**
     * C4-012(d)（plan 2548 Decision）：虚拟线程 per-task 模型无容量旋钮，热更为 no-op（不给假承诺）。
     */
    @Override
    public void refreshConfig() {

    }

    @Override
    public void destroy() {
        executor.shutdown();
    }

    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }
}