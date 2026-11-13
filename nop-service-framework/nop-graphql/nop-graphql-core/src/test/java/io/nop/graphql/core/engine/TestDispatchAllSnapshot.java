package io.nop.graphql.core.engine;

import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.graphql.core.ParsedGraphQLRequest;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C2-001（plan 2550 裁定 B）钉：dispatchAll 锁外 dispatch。
 * pin1：慢 loader（dispatch 内 DB 级 I/O 阻断）执行期间，同监视器上的并发分支
 * （getRequest）不得被阻塞。变异红形态：dispatch 循环回到 synchronized 域内，
 * getRequest 阻塞至 dispatch 释放 → 计时断言红。
 */
public class TestDispatchAllSnapshot extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void pin1_slowDispatchDoesNotBlockConcurrentMonitorUsers() throws Exception {
        GraphQLExecutionContext ctx = new GraphQLExecutionContext(new ServiceContextImpl());
        ParsedGraphQLRequest request = new ParsedGraphQLRequest();
        ctx.setRequest(request);

        CountDownLatch inDispatch = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicLong dispatchCalls = new AtomicLong();

        DataLoader<String, String> slowLoader = DataLoaderFactory.newDataLoader(keys -> {
            dispatchCalls.incrementAndGet();
            inDispatch.countDown();
            try {
                // 模拟 DB I/O：dispatch 停留至 release
                if (!release.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("dispatch latch timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            List<String> result = new ArrayList<>();
            for (String k : keys) {
                result.add(k);
            }
            return CompletableFuture.completedFuture(result);
        });
        ctx.registerDataLoader("slow", slowLoader);
        ctx.getDataLoader("slow").load("k1");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<CompletionStage<Void>> dispatchFuture = CompletableFuture.supplyAsync(ctx::dispatchAll, pool);
            assertTrue(inDispatch.await(10, TimeUnit.SECONDS), "dispatch must start");

            // dispatch 阻塞期间，并发分支 getRequest（同 this 监视器）必须在有界时间内返回
            long t0 = System.nanoTime();
            CompletableFuture<Object> readFuture = CompletableFuture.supplyAsync(ctx::getRequest, pool);
            Object read = readFuture.get(3, TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

            assertTrue(elapsedMs < 2000,
                    "getRequest must not block behind in-flight dispatch (lock-external dispatch): " + elapsedMs + "ms");
            assertTrue(read == request, "getRequest must return the same request instance");

            release.countDown();
            dispatchFuture.get(15, TimeUnit.SECONDS).toCompletableFuture().get(15, TimeUnit.SECONDS);
            assertTrue(dispatchCalls.get() == 1, "loader.dispatch must be invoked exactly once");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
