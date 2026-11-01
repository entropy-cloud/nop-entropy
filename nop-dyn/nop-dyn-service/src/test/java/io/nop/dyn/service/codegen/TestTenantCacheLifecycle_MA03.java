package io.nop.dyn.service.codegen;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-03（C6-002 主 + C1-101，plan 2525 裁定⑤）：租户缓存三转变点治理。
 *
 * <p>①驱逐对称：tenantCache 条目实现 IDestroyable + destroyOnRemove——Caffeine 容量驱逐时
 * 经 LocalCache removalListener 执行 cache.clear()（含逐模块 on-unload），驱逐不再绕过
 * 生命周期清理、不再摘除使用中实例而不留痕迹。
 *
 * <p>③destroy 孤儿窗（当前即红）：getTenantCodeCache DCL 发布后必须复验条目仍在缓存——
 * destroy 并发 removeTenantCache 摘除条目时，新初始化实例成为孤儿（on-load 副作用已发生
 * 却无入口可达清理）；修复 = 就地 clear 自愈后按 miss 重取。交错窗 = initTenantCache 阻塞
 * 期间并发摘除，闩锁确定性构造。
 *
 * <p>②冷启动维持同步 DCL 单飞（后台化否决，既有 TestDynCodeGenTenantCacheInit 钉零回退）。
 */
public class TestTenantCacheLifecycle_MA03 {

    static class CountingClearCache extends InMemoryCodeCache {
        final AtomicInteger clearCount = new AtomicInteger();
        volatile CountDownLatch entered;
        volatile CountDownLatch release;

        CountingClearCache(String tenantId) {
            super(tenantId, "/nop/templates/dyn-gen", new TestDynCodeGenTenantCacheInit.NoopHook());
        }

        @Override
        public synchronized void clear() {
            clearCount.incrementAndGet();
            super.clear();
        }
    }

    static class LatchedDynCodeGen extends DynCodeGen {
        final AtomicReference<CountDownLatch> initEntered = new AtomicReference<>();
        final AtomicReference<CountDownLatch> initRelease = new AtomicReference<>();
        final AtomicInteger initCount = new AtomicInteger();
        final AtomicReference<CountingClearCache> firstCache = new AtomicReference<>();

        @Override
        InMemoryCodeCache initTenantCache(String tenantId) {
            initCount.incrementAndGet();
            CountDownLatch entered = initEntered.get();
            if (entered != null)
                entered.countDown();
            CountDownLatch release = initRelease.get();
            if (release != null) {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            CountingClearCache cache = new CountingClearCache(tenantId);
            firstCache.compareAndSet(null, cache);
            return cache;
        }
    }

    /**
     * ①驱逐对称钉：destroyOnRemove 下显式 removeTenantCache 摘除即触发条目 destroy
     * （cache.clear 含 on-unload）——清理配对在摘除点闭环。
     */
    @Test
    public void testRemoveTenantTriggersEntryDestroy() {
        LatchedDynCodeGen codeGen = new LatchedDynCodeGen();
        InMemoryCodeCache cache = codeGen.getTenantCodeCache("t-evict");
        assertSame(cache, codeGen.firstCache.get());

        codeGen.clearForTenant("t-evict");

        assertTrue(((CountingClearCache) codeGen.firstCache.get()).clearCount.get() >= 1,
                "C6-002: entry removal must destroy the evicted cache (clear + on-unload symmetry)"
                        + " — capacity/TTL evictions reuse the same destroyOnRemove hook");
    }

    /**
     * ③destroy 孤儿窗钉（当前即红）：DCL 初始化期间并发摘除条目，发布后必须复验并自愈。
     */
    @Test
    public void testPublishRevalidateHealsOrphanOnConcurrentRemoval() throws Exception {
        LatchedDynCodeGen codeGen = new LatchedDynCodeGen();
        codeGen.initEntered.set(new CountDownLatch(1));
        codeGen.initRelease.set(new CountDownLatch(1));

        Thread reader = new Thread(() -> codeGen.getTenantCodeCache("t-orphan"), "orphan-reader");
        reader.start();
        // reader 已进入 initTenantCache 并阻塞
        assertTrue(codeGen.initEntered.get().await(5, TimeUnit.SECONDS));

        // 并发摘除（destroy 语义）：条目出缓存时其 cache 尚未发布
        codeGen.clearForTenant("t-orphan");

        codeGen.initRelease.get().countDown();
        reader.join(10000);

        // 修复态：发布后复验发现条目已被摘除 → 就地clear自愈（clearCount=1）→ 按miss重取
        // 缺陷态（无复验）：初始化实例被调用方持有却不在缓存中——孤儿（clearCount=0）
        assertTrue(codeGen.firstCache.get().clearCount.get() >= 1,
                "C1-101: the initialized-but-removed instance must be self-healed (cleared) —"
                        + " otherwise an orphan with fired on-load side effects is unreachable for cleanup");
        InMemoryCodeCache served = codeGen.getTenantCodeCache("t-orphan");
        assertNotSame(codeGen.firstCache.get(), served,
                "after healing the caller must be served from the live cache entry, not the detached instance");
    }
}
