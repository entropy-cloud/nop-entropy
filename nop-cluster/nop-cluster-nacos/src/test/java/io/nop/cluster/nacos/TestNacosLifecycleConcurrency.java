package io.nop.cluster.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.cache.LocalCache;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.2-C1-005/C2-002/MA-03（plan 2559 裁定 A/E/F）载体：TestNacosLifecycleConcurrency。
 * <p>
 * 覆盖四条载体链：
 * (1) destroy 拒复活（裁定 E：destroyed 双检 + 懒初始化类型化拒绝）；
 * (2) 订阅失败必退订（裁定 A①：catch 拓宽至 Exception）；
 * (3) 并发首装 = 单次远程调用 且 存活订阅数 = 1（裁定 F：三段式 + per-key 装载互斥）；
 * (4) putIfAbsent 败者退订（裁定 F 败者泄漏新变体预防）；
 * (5) 缓存容量上界 + 淘汰联动退订（裁定 A②：destroy-on-remove）。
 * <p>
 * stub 接缝复用 R2-18 的 getNamingService() 覆写形态
 * （TestNacosInstanceRebuildAndSnapshotGuard:93-96 pattern）。
 */
public class TestNacosLifecycleConcurrency {

    /**
     * Counting JDK-proxy NamingService stub: subscribe/unsubscribe/getAllInstances carry
     * behavior and counters; everything else is a no-op.
     */
    static final class CountingStub {
        final AtomicInteger subscribeCount = new AtomicInteger();
        final AtomicInteger unsubscribeCount = new AtomicInteger();
        final AtomicInteger getAllInstancesCount = new AtomicInteger();
        volatile boolean failSubscribe = false;
        volatile boolean failGetAllNonNacos = false;
        volatile long getAllInstancesDelayMillis = 0;

        NamingService proxy() {
            return (NamingService) Proxy.newProxyInstance(
                    TestNacosLifecycleConcurrency.class.getClassLoader(),
                    new Class[]{NamingService.class},
                    (p, method, args) -> {
                        switch (method.getName()) {
                            case "subscribe":
                                subscribeCount.incrementAndGet();
                                if (failSubscribe)
                                    throw new NacosException(NacosException.SERVER_ERROR, "subscribe fail");
                                return null;
                            case "unsubscribe":
                                unsubscribeCount.incrementAndGet();
                                return null;
                            case "getAllInstances":
                                getAllInstancesCount.incrementAndGet();
                                if (getAllInstancesDelayMillis > 0) {
                                    try {
                                        Thread.sleep(getAllInstancesDelayMillis);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                                if (failGetAllNonNacos)
                                    throw new IllegalStateException("non-nacos snapshot failure");
                                return Collections.emptyList();
                            default:
                                return null;
                        }
                    });
        }
    }

    private static NacosNamingService newNaming(CountingStub stub) {
        return new NacosNamingService() {
            @Override
            public NamingService getNamingService() {
                return stub.proxy();
            }
        };
    }

    private static LocalCache<String, ServiceInfo> cacheOf(NacosNamingService naming) throws Exception {
        java.lang.reflect.Field field = NacosNamingService.class.getDeclaredField("instanceCache");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        LocalCache<String, ServiceInfo> cache = (LocalCache<String, ServiceInfo>) field.get(naming);
        return cache;
    }

    // ------------------------------------------------------------------
    // (1) C1-005 / 裁定 E: destroy 后懒初始化 typed 拒绝
    // ------------------------------------------------------------------

    @Test
    public void destroyRejectsLazyRevival() throws Exception {
        // production getNamingService/getNamingMaintainService (no stub override) —
        // the destroyed double-check lives in those production methods
        NacosNamingService plain = new NacosNamingService();
        plain.destroy();

        NopException e1 = assertThrows(NopException.class, plain::getNamingService);
        assertEquals(NacosErrors.ERR_CLUSTER_NACOS_NAMING_SERVICE_DESTROYED.getErrorCode(), e1.getCode(),
                "C1-005: getNamingService after destroy must be a typed rejection, not a revival");

        NopException e2 = assertThrows(NopException.class, plain::getNamingMaintainService);
        assertEquals(NacosErrors.ERR_CLUSTER_NACOS_NAMING_SERVICE_DESTROYED.getErrorCode(), e2.getCode());

        // post-destroy getInstances must not lazily rebuild the cache/client either
        CountingStub stub = new CountingStub();
        NacosNamingService naming = newNaming(stub);
        assertNotNull(naming.getInstances("svc")); // legitimate use before destroy
        naming.destroy();

        NopException e3 = assertThrows(NopException.class, () -> naming.getInstances("svc"));
        assertEquals(NacosErrors.ERR_CLUSTER_NACOS_NAMING_SERVICE_DESTROYED.getErrorCode(), e3.getCode(),
                "C1-005: getInstances after destroy must not lazily rebuild the cache/client");

        // the destroyed naming service was used exactly once before destroy: no re-subscribe
        assertEquals(1, stub.subscribeCount.get(), "destroy must not trigger any new subscription");
    }

    // ------------------------------------------------------------------
    // (2) MA-03 / 裁定 A①: 订阅失败必退订（catch widened to Exception）
    // ------------------------------------------------------------------

    @Test
    public void subscribeFailureUnsubscribesItsOwnListener() {
        CountingStub stub = new CountingStub();
        NacosNamingService naming = newNaming(stub);
        // MA-03/A①: subscribe succeeds, then the snapshot load fails with a NON-Nacos
        // exception (previously only NacosException was caught → the listener leaked)
        stub.failGetAllNonNacos = true;

        // NopException.adapt wrapping: the propagation form is RuntimeException-typed;
        // the load-bearing assertion is the unsubscribe below
        assertThrows(RuntimeException.class, () -> naming.getInstances("svc"));

        assertEquals(1, stub.subscribeCount.get());
        assertEquals(1, stub.unsubscribeCount.get(),
                "MA-03/A①: a failed subscribe must unsubscribe the listener that was registered");
    }

    // ------------------------------------------------------------------
    // (3) C2-002 / 裁定 F: 并发首装 = 单次远程调用 + 存活订阅数 = 1
    // ------------------------------------------------------------------

    @Test
    public void concurrentFirstInstallLoadsOnceAndKeepsSingleSurvivingSubscription() throws Exception {
        CountingStub stub = new CountingStub();
        stub.getAllInstancesDelayMillis = 100; // widen the load window
        NacosNamingService naming = newNaming(stub);

        int threads = 8;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    barrier.await();
                    naming.getInstances("svc");
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        assertTrue(done.await(15, java.util.concurrent.TimeUnit.SECONDS), "all workers must finish");

        assertEquals(0, failures.get(), "no worker may fail");
        assertEquals(1, stub.getAllInstancesCount.get(),
                "C2-002/F: concurrent first install must perform exactly one remote snapshot load");
        assertEquals(1, stub.subscribeCount.get(),
                "裁定 F: only the single loader thread may subscribe (per-key load mutex)");
        assertEquals(0, stub.unsubscribeCount.get());
        // surviving subscription count == 1
        assertEquals(1, stub.subscribeCount.get() - stub.unsubscribeCount.get(),
                "裁定 F: surviving subscription count after concurrent first install must be exactly 1");
    }

    // ------------------------------------------------------------------
    // (4) C2-002 / 裁定 F: putIfAbsent 败者退订（writeBack 败者路径）
    // ------------------------------------------------------------------

    @Test
    public void putIfAbsentLoserUnsubscribesItsOwnListener() throws Exception {
        CountingStub stub = new CountingStub();
        NacosNamingService naming = newNaming(stub);

        // install the winner through the production path
        List<io.nop.cluster.discovery.ServiceInstance> first = naming.getInstances("svc");
        ServiceInfo winner = cacheOf(naming).getIfPresent("svc");
        assertNotNull(winner);
        int subscribeBefore = stub.subscribeCount.get();

        // a rival (loser) entry: its own listener registers a subscription on the stub
        ServiceInfo loser = new ServiceInfo();
        NacosNamingService.ServiceInfoListener loserListener = naming.new ServiceInfoListener("svc", loser);
        loser.setListener(loserListener);
        stub.proxy().subscribe("svc", "DEFAULT", loserListener);
        int unsubscribeBefore = stub.unsubscribeCount.get();

        ServiceInfo resolved = naming.writeBack(cacheOf(naming), "svc", loser);

        assertNotSame(loser, resolved, "the existing entry must win the putIfAbsent race");
        assertEquals(first, resolved.getServiceInstances());
        assertEquals(subscribeBefore + 1, stub.subscribeCount.get());
        assertEquals(unsubscribeBefore + 1, stub.unsubscribeCount.get(),
                "裁定 F: the putIfAbsent loser must unsubscribe the listener its own load registered");
    }

    // ------------------------------------------------------------------
    // (5) MA-03 / 裁定 A②: 容量上界 + 淘汰联动退订（destroy-on-remove）
    // ------------------------------------------------------------------

    @Test
    public void cacheEvictionUnsubscribesEvictedListener() throws Exception {
        CountingStub stub = new CountingStub();
        NacosNamingService naming = newNaming(stub);
        naming.setInstanceCacheMaxSize(1);

        naming.getInstances("svc-a"); // subscribe #1
        naming.getInstances("svc-b"); // subscribe #2, evicts svc-a (LRU, destroy-on-remove)

        // Caffeine size eviction runs on async maintenance — poll until the eviction
        // callback (unsubscribe) lands
        long deadline = System.currentTimeMillis() + 10_000;
        while (stub.unsubscribeCount.get() < 1 && System.currentTimeMillis() < deadline) {
            cacheOf(naming).getIfPresent("svc-a"); // touches maintenance
            Thread.sleep(20);
        }

        assertEquals(2, stub.subscribeCount.get());
        assertEquals(1, stub.unsubscribeCount.get(),
                "MA-03/A②: the LRU-evicted entry's subscription must be torn down with it");
        assertEquals(1, cacheOf(naming).estimatedSize(), "cache bound: at most 1 entry survives");
    }
}
