package io.nop.ai.toolkit.manager;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.api.IToolExecutorProvider;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the {@code executeParallel} concurrency bound
 * (maxConcurrency): with a positive limit at most that many tool calls are in
 * flight at any moment, every call eventually completes (nothing is dropped),
 * and a null limit keeps the unbounded behavior.
 */
public class ToolManagerImplParallelTest {

    private static DefaultThreadPoolExecutor pool;

    @BeforeAll
    static void initPool() {
        pool = DefaultThreadPoolExecutor.newExecutor("toolkit-parallel-test", 8, 64, true);
    }

    @AfterAll
    static void destroyPool() {
        if (pool != null) {
            pool.destroy();
        }
    }

    /** Counting executor: tracks in-flight executions and max observed concurrency. */
    static class CountingExecutor implements IToolExecutor {
        static final String TOOL_NAME = "counting-tool";

        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxObserved = new AtomicInteger();
        final AtomicInteger completed = new AtomicInteger();

        @Override
        public String getToolName() {
            return TOOL_NAME;
        }

        @Override
        public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
            return context.getExecutor().submit(() -> {
                int now = inFlight.incrementAndGet();
                maxObserved.accumulateAndGet(now, Math::max);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                inFlight.decrementAndGet();
                completed.incrementAndGet();
                AiToolCallResult result = new AiToolCallResult();
                result.setId(call.getId());
                result.setStatus("success");
                return result;
            });
        }
    }

    static class SingleExecutorProvider implements IToolExecutorProvider {
        private final IToolExecutor executor;

        SingleExecutorProvider(IToolExecutor executor) {
            this.executor = executor;
        }

        @Override
        public IToolExecutor getExecutor(String toolName) {
            return executor;
        }

        @Override
        public java.util.Collection<String> getToolNames() {
            return java.util.List.of(executor.getToolName());
        }
    }

    private ToolManagerImpl newManager(CountingExecutor countingExecutor) {
        return new ToolManagerImpl(new SingleExecutorProvider(countingExecutor), null);
    }

    private AiToolCalls buildCalls(int count, Integer maxConcurrency) {
        AiToolCalls calls = new AiToolCalls();
        calls.setParallel(true);
        calls.setMaxConcurrency(maxConcurrency);
        List<AiToolCall> body = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            XNode node = XNode.make("counting-tool");
            node.setAttr("id", i);
            AiToolCall call = AiToolCall.fromNode(node);
            call.setToolName(CountingExecutor.TOOL_NAME);
            body.add(call);
        }
        calls.setBody(body);
        return calls;
    }

    private static class MockContext implements IToolExecuteContext {
        @Override public File getWorkDir() { return new File("."); }
        @Override public Map<String, String> getEnvs() { return Map.of(); }
        @Override public long getExpireAt() { return Long.MAX_VALUE; }
        @Override public ICancelToken getCancelToken() { return null; }
        @Override public IToolFileSystem getFileSystem() { return null; }
        @Override public IThreadPoolExecutor getExecutor() { return pool; }
    }

    @Test
    void testMaxConcurrencyBoundsInFlightCalls() throws Exception {
        CountingExecutor countingExecutor = new CountingExecutor();
        ToolManagerImpl manager = newManager(countingExecutor);
        AiToolCalls calls = buildCalls(5, 2);

        CompletableFuture<AiToolCallsResponse> future = manager.callTools(calls, new MockContext());
        AiToolCallsResponse response = future.get(30, TimeUnit.SECONDS);

        assertNotNull(response.getResults());
        assertEquals(5, response.getResults().size(), "all 5 calls must complete, none dropped");
        for (AiToolCallResult r : response.getResults()) {
            assertEquals("success", r.getStatus());
        }
        assertEquals(5, countingExecutor.completed.get(), "all calls must have executed");
        assertTrue(countingExecutor.maxObserved.get() <= 2,
                "in-flight calls must never exceed maxConcurrency=2, observed max="
                        + countingExecutor.maxObserved.get());
        assertEquals(2, countingExecutor.maxObserved.get(),
                "with 5 slow calls and maxConcurrency=2 the bound must actually be reached");
    }

    @Test
    void testNullMaxConcurrencyStaysUnbounded() throws Exception {
        CountingExecutor countingExecutor = new CountingExecutor();
        ToolManagerImpl manager = newManager(countingExecutor);
        AiToolCalls calls = buildCalls(5, null);

        CompletableFuture<AiToolCallsResponse> future = manager.callTools(calls, new MockContext());
        AiToolCallsResponse response = future.get(30, TimeUnit.SECONDS);

        assertEquals(5, response.getResults().size(), "all 5 calls must complete");
        assertEquals(5, countingExecutor.completed.get());
        assertEquals(5, countingExecutor.maxObserved.get(),
                "null maxConcurrency must stay unbounded (all calls in flight)");
    }

    @Test
    void testMaxConcurrencyOneIsSequential() throws Exception {
        CountingExecutor countingExecutor = new CountingExecutor();
        ToolManagerImpl manager = newManager(countingExecutor);
        AiToolCalls calls = buildCalls(3, 1);

        CompletableFuture<AiToolCallsResponse> future = manager.callTools(calls, new MockContext());
        AiToolCallsResponse response = future.get(30, TimeUnit.SECONDS);

        assertEquals(3, response.getResults().size());
        assertEquals(3, countingExecutor.completed.get());
        assertEquals(1, countingExecutor.maxObserved.get(), "maxConcurrency=1 must serialize execution");
    }

    @Test
    void testMaxConcurrencyGreaterThanCallCountRunsAll() throws Exception {
        CountingExecutor countingExecutor = new CountingExecutor();
        ToolManagerImpl manager = newManager(countingExecutor);
        AiToolCalls calls = buildCalls(3, 10);

        CompletableFuture<AiToolCallsResponse> future = manager.callTools(calls, new MockContext());
        AiToolCallsResponse response = future.get(30, TimeUnit.SECONDS);

        assertEquals(3, response.getResults().size());
        assertEquals(3, countingExecutor.completed.get());
        assertEquals(3, countingExecutor.maxObserved.get(),
                "limit above the call count must not throttle the batch");
    }

    @Test
    void testResultsPreserveCallOrder() throws Exception {
        CountingExecutor countingExecutor = new CountingExecutor();
        ToolManagerImpl manager = newManager(countingExecutor);
        AiToolCalls calls = buildCalls(5, 2);

        AiToolCallsResponse response = manager.callTools(calls, new MockContext()).get(30, TimeUnit.SECONDS);

        List<AiToolCallResult> results = response.getResults();
        for (int i = 0; i < results.size(); i++) {
            assertEquals(Integer.valueOf(i), results.get(i).getId(),
                    "results must be reported in request order");
        }
    }
}