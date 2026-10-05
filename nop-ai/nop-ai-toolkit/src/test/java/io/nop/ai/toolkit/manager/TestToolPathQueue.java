package io.nop.ai.toolkit.manager;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.api.IToolExecutorProvider;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2298 (WI17): per-path serialization of concurrency-unsafe tools at the
 * {@code callTool} chokepoint — the dispatcher's parallel fan-out calls
 * callTool per call, so the queue here is the production protection (the
 * callTools/executeParallel face has no production callers). Tests use two
 * concurrent callTool invocations (the dispatcher shape) with latch-based
 * deterministic ordering.
 */
public class TestToolPathQueue {

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newCachedThreadPool();
    }

    private AiToolCall callWithPath(String path, String note) {
        AiToolCall call = new AiToolCall();
        call.setToolName("write-file");
        call.setInput("{\"path\":\"" + path + "\",\"note\":\"" + note + "\"}");
        return call;
    }

    /** Counting decorator around a base provider: tracks concurrent executions. */
    private IToolExecutorProvider counting(IToolExecutorProvider base, AtomicInteger running, AtomicInteger maxObserved) {
        return new IToolExecutorProvider() {
            @Override
            public IToolExecutor getExecutor(String toolName) {
                IToolExecutor inner = base.getExecutor(toolName);
                return new IToolExecutor() {
                    @Override
                    public String getToolName() {
                        return inner.getToolName();
                    }

                    @Override
                    public java.util.concurrent.CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
                        int now = running.incrementAndGet();
                        maxObserved.accumulateAndGet(now, Math::max);
                        return inner.executeAsync(call, context).thenApply(r -> {
                            running.decrementAndGet();
                            return r;
                        });
                    }
                };
            }

            @Override
            public Collection<String> getToolNames() {
                return base.getToolNames();
            }
        };
    }

    /** Provider whose executions block on the test-held latch (deterministic ordering). */
    private IToolExecutorProvider blockingProvider(CountDownLatch releaseLatch, AtomicInteger startCount) {
        return new IToolExecutorProvider() {
            @Override
            public IToolExecutor getExecutor(String toolName) {
                return new IToolExecutor() {
                    @Override
                    public String getToolName() {
                        return toolName;
                    }

                    @Override
                    public CompletableFuture<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
                        startCount.incrementAndGet();
                        return CompletableFuture.supplyAsync(() -> {
                            try {
                                releaseLatch.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return AiToolCallResult.successResult(0, "ok");
                        }, executorService);
                    }
                };
            }

            @Override
            public Collection<String> getToolNames() {
                return Collections.singleton("write-file");
            }
        };
    }

    private void awaitMaxObserved(AtomicInteger maxObserved, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (maxObserved.get() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
    }

    @Test
    void samePathCallsSerializeAtCallToolChokepoint() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();
        ToolManagerImpl m = new ToolManagerImpl(
                counting(blockingProvider(release, starts), running, maxObserved),
                Collections.emptyList());
        // loadTool (resource-backed) finds no model → conservative "unsafe" → queued.

        CompletableFuture<AiToolCallResult> first =
                m.callTool("write-file", callWithPath("/a/b.txt", "first"), null);
        long deadline = System.currentTimeMillis() + 5000;
        while (starts.get() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, starts.get(), "first call must start");

        CompletableFuture<AiToolCallResult> second =
                m.callTool("write-file", callWithPath("/a/b.txt", "second"), null);

        // Second is queued: no executor start while the first is blocked.
        Thread.sleep(150); // grace for a (wrong) eager start to become visible
        assertEquals(1, starts.get(), "second call must stay queued while the first runs");

        release.countDown();
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        assertEquals(2, starts.get(), "second call must run after the first completes");
        assertEquals(1, maxObserved.get(), "only one same-path call in flight at a time");
        assertEquals(0, running.get());
    }

    @Test
    void differentPathsRunInParallel() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();
        ToolManagerImpl m = new ToolManagerImpl(
                counting(blockingProvider(release, starts), running, maxObserved),
                Collections.emptyList());

        CompletableFuture<AiToolCallResult> f1 = m.callTool("write-file", callWithPath("/a/1.txt", "x"), null);
        CompletableFuture<AiToolCallResult> f2 = m.callTool("write-file", callWithPath("/a/2.txt", "y"), null);

        awaitMaxObserved(maxObserved, 2);
        assertEquals(2, maxObserved.get(), "different paths must run in parallel");

        release.countDown();
        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
    }

    @Test
    void safeDeclarationBypassesQueue() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();
        // Manager seam: loadTool declares concurrency-safe → no path queue.
        ToolManagerImpl m = new ToolManagerImpl(
                counting(blockingProvider(release, starts), running, maxObserved),
                Collections.emptyList()) {
            @Override
            public AiToolModel loadTool(String toolName) {
                AiToolModel model = new AiToolModel();
                model.setName(toolName);
                model.setConcurrencySafe(true);
                return model;
            }
        };

        CompletableFuture<AiToolCallResult> s1 = m.callTool("write-file", callWithPath("/a/same.txt", "x"), null);
        CompletableFuture<AiToolCallResult> s2 = m.callTool("write-file", callWithPath("/a/same.txt", "y"), null);
        awaitMaxObserved(maxObserved, 2);
        assertEquals(2, maxObserved.get(), "concurrency-safe declaration bypasses the queue");

        release.countDown();
        s1.get(5, TimeUnit.SECONDS);
        s2.get(5, TimeUnit.SECONDS);
    }

    @Test
    void malformedOrPathlessInputRunsUnqueued() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();
        ToolManagerImpl m = new ToolManagerImpl(
                counting(blockingProvider(release, starts), running, maxObserved),
                Collections.emptyList());

        AiToolCall malformed = new AiToolCall();
        malformed.setToolName("write-file");
        malformed.setInput("not json {{{");
        AiToolCall noPath = new AiToolCall();
        noPath.setToolName("write-file");
        noPath.setInput("{\"command\":\"ls\"}");

        CompletableFuture<AiToolCallResult> f1 = m.callTool("write-file", malformed, null);
        CompletableFuture<AiToolCallResult> f2 = m.callTool("write-file", noPath, null);
        awaitMaxObserved(maxObserved, 2);
        assertEquals(2, maxObserved.get(), "no extractable path → unqueued (documented degradation)");

        release.countDown();
        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
    }
}
