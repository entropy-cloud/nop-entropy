package io.nop.ai.shell.executor;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Plan 2301 (WI25) test stub for {@link IBackgroundJobSink}: forks the body
 * on a local pool, records submissions and completions, and supports
 * cancellation — the addressable-lifecycle contract the agent-side registry
 * adapter provides in production wiring.
 */
public class RecordingBackgroundJobSink implements IBackgroundJobSink {

    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "test-shell-bg-fork");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, Future<ExecutionResult>> jobs = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private volatile CompletableFuture<String> firstSubmission =
            new CompletableFuture<>();

    @Override
    public String submit(String commandText, Callable<ExecutionResult> body) {
        String taskId = "bg-" + ids.incrementAndGet();
        // ExecutorService.submit returns a FutureTask whose cancel(true)
        // interrupts the running body (CompletableFuture.cancel does not)
        Future<ExecutionResult> future = pool.submit(body);
        jobs.put(taskId, future);
        firstSubmission.complete(taskId);
        return taskId;
    }

    /** Await the first {@code &} submission (deterministic single-job tests). */
    public String awaitSubmission() throws Exception {
        return firstSubmission.get(5, TimeUnit.SECONDS);
    }

    public ExecutionResult awaitCompletion(String taskId, long timeout, TimeUnit unit) throws Exception {
        return jobs.get(taskId).get(timeout, unit);
    }

    public long snapshotCount(String taskId) {
        return jobs.containsKey(taskId) ? 1 : 0;
    }

    /** Cancel the running job (the future-fallback channel of the registry). */
    public boolean cancel(String taskId) {
        Future<ExecutionResult> future = jobs.get(taskId);
        return future != null && future.cancel(true);
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}
