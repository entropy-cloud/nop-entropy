package io.nop.biz.crud;

import io.nop.biz.api.IBizObject;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C2-003（plan 2550 裁定 B）钉：loadByProp 双检——biz invoke（校验链+DB I/O）
 * 不在缓存锁内执行；并发竞争失败方丢弃自建结果。
 * pin2-a：同键竞争方调用不得阻塞在慢 invoke 之后（旧 computeIfAbsent 形态红：竞争方
 * 在缓存锁内等待慢 invoke，latency ≥ 阻塞窗；双检形态：自建 invoke 立即返回）。
 * pin2-b：并发同键双查结果一致（胜出方实例统一），胜出结果入缓存。
 * 变异红形态：invoke 回到 computeIfAbsent 映射 lambda 内 → pin2-a 计时断言红。
 */
public class TestBizObjHelperDoubleCheck extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static class CountingBiz {
        final AtomicInteger invokeCount = new AtomicInteger();
        final AtomicBoolean firstInvokeBlocked = new AtomicBoolean(false);
        boolean blocking = true;
        CountDownLatch inInvoke = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        IBizObject proxy() {
            return (IBizObject) Proxy.newProxyInstance(IBizObject.class.getClassLoader(),
                    new Class<?>[]{IBizObject.class},
                    (p, method, args) -> {
                        switch (method.getName()) {
                            case "getBizObjName":
                                return "TestEntity";
                            case "invoke":
                                return doInvoke();
                            case "toString":
                                return "CountingBiz(TestEntity)";
                            default:
                                return null;
                        }
                    });
        }

        private Object doInvoke() {
            invokeCount.incrementAndGet();
            // 仅首次 invoke 阻塞（模拟 DB I/O）；后续 invoke 立即返回——区分「竞争方自建
            // 结果快速返回」（双检形态）与「竞争方阻塞在缓存锁内等待慢 invoke」（旧形态）。
            if (blocking && firstInvokeBlocked.compareAndSet(false, true)) {
                inInvoke.countDown();
                try {
                    release.await(15, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return "entity-TestEntity-" + invokeCount.get();
        }
    }

    @Test
    public void pin2a_slowInvokeDoesNotBlockConcurrentSameKeyCaller() throws Exception {
        ServiceContextImpl ctx = new ServiceContextImpl();
        CountingBiz biz = new CountingBiz();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // 胜者线程：loadByProp（invoke 阻塞在 DB I/O 模拟窗内）
            CompletableFuture<Object> winner = CompletableFuture.supplyAsync(
                    () -> BizObjHelper.loadByProp(biz.proxy(), "refId", "v1", ctx), pool);
            assertTrue(biz.inInvoke.await(10, TimeUnit.SECONDS), "first invoke must start");

            // 竞争方线程（同键）：双检形态下自建 invoke 立即返回（invoke 不持缓存锁）；
            // 变异红形态（computeIfAbsent 内 invoke）：竞争方阻塞在缓存锁内直至慢 invoke 释放。
            long t0 = System.nanoTime();
            CompletableFuture<Object> loser = CompletableFuture.supplyAsync(
                    () -> BizObjHelper.loadByProp(biz.proxy(), "refId", "v1", ctx), pool);
            Object loserResult = loser.get(3, TimeUnit.SECONDS);
            long loserMs = (System.nanoTime() - t0) / 1_000_000;
            assertTrue(loserMs < 2500,
                    "same-key concurrent caller must not block behind in-flight invoke (lock-external invoke): "
                            + loserMs + "ms");
            assertNotNull(loserResult);

            // 释放慢 invoke，胜者收敛
            biz.release.countDown();
            assertNotNull(winner.get(15, TimeUnit.SECONDS));
        } finally {
            biz.release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    public void pin2b_concurrentSameKeyLoadsConvergeToWinner() throws Exception {
        int racers = 8;
        for (int round = 0; round < 5; round++) {
            ServiceContextImpl ctx = new ServiceContextImpl();
            CountingBiz biz = new CountingBiz();
            biz.blocking = false; // pin2b 只验收敛语义，不阻塞

            ExecutorService pool = Executors.newFixedThreadPool(racers);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(racers);
            List<CompletableFuture<Object>> futures = new java.util.ArrayList<>();

            for (int i = 0; i < racers; i++) {
                CompletableFuture<Object> f = CompletableFuture.supplyAsync(() -> {
                    try {
                        start.await();
                        return BizObjHelper.loadByProp(biz.proxy(), "refId", "v1", ctx);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    } finally {
                        done.countDown();
                    }
                }, pool);
                futures.add(f);
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS));
            pool.shutdownNow();

            Object first = futures.get(0).join();
            for (CompletableFuture<Object> f : futures) {
                // 结果一致性：所有调用方拿到同一胜出实例（竞争失败方丢弃自建结果）
                if (!Objects.equals(first, f.join())) {
                    org.junit.jupiter.api.Assertions.fail(
                            "loadByProp results diverged across racing callers (round " + round + ")");
                }
            }
            int count = biz.invokeCount.get();
            assertTrue(count >= 1 && count <= racers, "invoke count bounded by racers: " + count);
            Object cached = ctx.getCache().getIfPresent(Arrays.asList("v1", "refId", "TestEntity",
                    io.nop.biz.BizConstants.KEY_REF_ENTITY));
            assertEquals(first, cached, "winner result must be cached");
        }
    }
}
