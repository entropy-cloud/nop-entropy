package io.nop.ai.agent.tool;

import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
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
 * <p>CRA 3.1-C4-011 (plan 2498 Phase 4): the adapter no longer routes tasks
 * to the JVM-wide {@link CompletableFuture} ForkJoin common pool. Toolkit
 * tool bodies are inherently blocking (Bash process {@code waitFor} up to
 * {@code toolTimeoutMs}=300s, blocking HTTP, file I/O) — running them on the
 * shared common pool starves every other common-pool consumer platform-wide
 * (the same reason {@code DefaultAgentEngine} builds its own agent executor).
 * The singleton therefore owns a dedicated named bounded module pool built
 * through the platform pool factory ({@code DefaultThreadPoolExecutor
 * .newExecutor} — R1-02 fail-fast validated contract: poolSize >= 1,
 * queueSize >= 1):
 *
 * <ul>
 *   <li>thread name prefix {@code nop-ai-agent-dispatch} — tool fan-out
 *       threads are distinguishable from shared-pool threads in thread
 *       dumps;</li>
 *   <li>fixed width {@value #DISPATCH_POOL_SIZE} threads: the tool dispatch
 *       throughput ceiling is this module pool's width, never the JVM global
 *       pool width (the former pool-width-capped-by-commonPool starvation
 *       face is closed);</li>
 *   <li>bounded queue ({@value #DISPATCH_QUEUE_SIZE}): saturation fails loud
 *       with a {@code RejectedExecutionException} instead of the previous
 *       unbounded common-pool submission queue (CL-025 disposal: the one-arg
 *       {@code runAsync} form is eliminated from this site);</li>
 *   <li>daemon threads with owned lifecycle: {@link #destroy()} shuts the
 *       pool down (bounded — daemon threads cannot block JVM exit).</li>
 * </ul>
 *
 * <p>Blocking-occupancy boundary: one in-flight tool body occupies one
 * dispatch thread for up to its tool timeout (default cap
 * {@code toolTimeoutMs}=300s). A resolved cap {@code <= 0} disables the
 * per-tool mechanical timeout — {@code AgentToolDispatcher} emits a one-shot
 * WARN in that configuration, because a hung tool body then occupies one
 * dispatch thread permanently.
 *
 * <p>Deliberately not in the {@code io.nop.ai.agent.engine} package — the
 * gate-2 orchestration-timeout completeness test mechanically derives
 * {@code execute(*}) entry points from engine-package sources, and this
 * adapter's {@code execute(Runnable)} is a scheduling primitive, not an
 * orchestration entry.
 */
public final class AgentDispatchExecutor implements IThreadPoolExecutor {

    /**
     * Tool dispatch pool width (blocking tool bodies). Sized against the
     * envelope "concurrent agent sessions x parallel tool fan-out per
     * iteration"; threads are created lazily on demand, so this is an upper
     * bound, not a pre-warmed footprint.
     */
    static final int DISPATCH_POOL_SIZE = 32;

    /**
     * Bounded dispatch queue depth. Saturation rejects loud instead of
     * queueing without bound (the pre-C4-011 common-pool submission queue).
     */
    static final int DISPATCH_QUEUE_SIZE = 1024;

    public static final AgentDispatchExecutor INSTANCE = new AgentDispatchExecutor();

    private final IThreadPoolExecutor delegate;

    private AgentDispatchExecutor() {
        this.delegate = DefaultThreadPoolExecutor.newExecutor(
                "nop-ai-agent-dispatch", DISPATCH_POOL_SIZE, DISPATCH_QUEUE_SIZE, true);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(command);
    }

    @Override
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        return delegate.submit(task);
    }

    @Override
    public <T> CompletableFuture<T> submit(Runnable task, T result) {
        return delegate.submit(task, result);
    }

    @Override
    public String getName() {
        return "agent-dispatch-executor";
    }

    @Override
    public ThreadPoolConfig getConfig() {
        return delegate.getConfig();
    }

    @Override
    public ThreadPoolStats stats() {
        return delegate.stats();
    }

    @Override
    public void destroy() {
        // owned module pool — released with the adapter (daemon threads, so
        // JVM exit is never blocked; shutdown() only stops new submissions)
        delegate.destroy();
    }

    @Override
    public void refreshConfig() {
        // fixed module pool — no dynamic resize contract
        delegate.refreshConfig();
    }
}
