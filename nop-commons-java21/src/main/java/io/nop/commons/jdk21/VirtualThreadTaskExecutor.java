package io.nop.commons.jdk21;

import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.concurrent.executor.ThreadPoolStats;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public class VirtualThreadTaskExecutor implements IThreadPoolExecutor {
    private ThreadPoolConfig config = new ThreadPoolConfig();

    public void setName(String name) {
        config.setName(name);
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
        return null;
    }

    @Override
    public <V> CompletableFuture<V> submit(Runnable task, V result) {
        return null;
    }

    @Override
    public void refreshConfig() {

    }

    @Override
    public void destroy() {

    }

    @Override
    public void execute(Runnable command) {

    }
}