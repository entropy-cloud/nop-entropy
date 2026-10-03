package io.nop.ai.agent.tool;

import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.concurrent.executor.ThreadPoolStats;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Plan 2301 (WI25): executor adapter for the engine's tool dispatch context
 * ({@code AgentToolDispatcher.prepareDispatchContext}). The executor slot on
 * {@code IToolExecuteContext} must be non-null — toolkit tools call
 * {@code context.getExecutor().submit(...)} unconditionally (the pre-WI25
 * null slot made the engine+tool combination NPE; no integration test
 * covered it).
 *
 * <p>Runs on the shared {@link CompletableFuture#runAsync} ForkJoin pool:
 * stateless, nothing owned to release. Deliberately not in the
 * {@code io.nop.ai.agent.engine} package — the gate-2 orchestration-timeout
 * completeness test mechanically derives {@code execute(*}) entry points from
 * engine-package sources, and this adapter's {@code execute(Runnable)} is a
 * scheduling primitive, not an orchestration entry.
 */
public final class AgentDispatchExecutor implements IThreadPoolExecutor {

    public static final AgentDispatchExecutor INSTANCE = new AgentDispatchExecutor();

    private AgentDispatchExecutor() {
    }

    @Override
    public void execute(Runnable command) {
        CompletableFuture.runAsync(command);
    }

    @Override
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public <T> CompletableFuture<T> submit(Runnable task, T result) {
        return CompletableFuture.runAsync(task).thenApply(v -> result);
    }

    @Override
    public String getName() {
        return "agent-dispatch-executor";
    }

    @Override
    public ThreadPoolConfig getConfig() {
        return new ThreadPoolConfig();
    }

    @Override
    public ThreadPoolStats stats() {
        return new ThreadPoolStats();
    }

    @Override
    public void destroy() {
        // shared pool adapter — nothing owned to release
    }

    @Override
    public void refreshConfig() {
        // shared pool adapter — no own config to refresh
    }
}
