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

    @Override
    public ThreadPoolStats stats() {
        return null;
    }

    @Override
    public <V> CompletableFuture<V> submit(Callable<V> callable) {
        return ExecutorHelper.submit(executor, callable);
    }

    @Override
    public <V> CompletableFuture<V> submit(Runnable task, V result) {
        return ExecutorHelper.submit(executor, task, result);
    }

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