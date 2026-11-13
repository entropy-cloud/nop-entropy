package io.nop.orm.model.lazy;

import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * 2.3-C2-018（plan 2553 裁定 E）钉：锁外加载 + per-key 去重 + 回环 fail-fast。
 * 变异红形态：inFlightLoads 去重擦除后 loadCount > 1；重入守卫擦除后 pin_c 挂起/StackOverflow。
 */
public class TestLazyLoadConcurrentLoad {

    private static IEntityModel dynModel(String name, String tableName) {
        IEntityModel model = mock(IEntityModel.class);
        lenient().when(model.getName()).thenReturn(name);
        lenient().when(model.getTableName()).thenReturn(tableName);
        lenient().when(model.getShortName()).thenReturn(name);
        lenient().when(model.isRegisterShortName()).thenReturn(false);
        return model;
    }

    /**
     * pin_a：8 线程并发同 key getEntityModel，provider 仅被调用一次（per-key CF 去重）。
     * 变异红形态：去掉 inFlightLoads 去重后 loadCount > 1。
     */
    @Test
    public void pin_a_concurrentSameKeySingleLoad() throws Exception {
        AtomicInteger loadCount = new AtomicInteger();
        CountDownLatch startGate = new CountDownLatch(1);
        LazyLoadOrmModel model = new LazyLoadOrmModel(null, (name, orm) -> {
            loadCount.incrementAndGet();
            try {
                // 加载体内的延迟放大并发窗口：所有线程先在 startGate 排队，再同时涌入
                startGate.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return dynModel(name, "TBL_" + name);
        });

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            List<Future<IEntityModel>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    startGate.await();
                    return model.getEntityModel("DynA");
                }));
            }
            // 等所有线程就位后放行，确保真正的并发竞争
            assertTrue(ready.await(5, TimeUnit.SECONDS), "worker threads not ready");
            startGate.countDown();

            for (Future<IEntityModel> future : futures) {
                IEntityModel loaded = future.get(10, TimeUnit.SECONDS);
                assertNotNull(loaded, "concurrent getEntityModel must return non-null model");
                assertEquals("DynA", loaded.getName());
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, loadCount.get(),
                "provider must be invoked exactly once for concurrent same-key loads");
    }

    /**
     * pin_b：4 个不同 key 并发加载，各恰好加载一次，互不串扰。
     */
    @Test
    public void pin_b_differentKeysConcurrentEachLoadedOnce() throws Exception {
        java.util.Map<String, AtomicInteger> counts = new java.util.concurrent.ConcurrentHashMap<>();
        LazyLoadOrmModel model = new LazyLoadOrmModel(null, (name, orm) -> {
            counts.computeIfAbsent(name, k -> new AtomicInteger()).incrementAndGet();
            return dynModel(name, "TBL_" + name);
        });

        List<String> keys = List.of("DynB0", "DynB1", "DynB2", "DynB3");
        ExecutorService pool = Executors.newFixedThreadPool(keys.size());
        try {
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch ready = new CountDownLatch(keys.size());
            List<Future<IEntityModel>> futures = new java.util.ArrayList<>();
            for (String key : keys) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    startGate.await();
                    return model.getEntityModel(key);
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS), "worker threads not ready");
            startGate.countDown();

            for (int i = 0; i < keys.size(); i++) {
                IEntityModel loaded = futures.get(i).get(10, TimeUnit.SECONDS);
                assertNotNull(loaded, "entity " + keys.get(i) + " must load non-null");
                assertEquals(keys.get(i), loaded.getName());
            }
            for (String key : keys) {
                assertEquals(1, counts.get(key).get(),
                        "entity " + key + " must be loaded exactly once");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * pin_c：加载体内同步回环 getEntityModel（self-loop）时 fail-fast 抛 IllegalStateException；
     * 失败后状态不被污染，换正常 loader 仍可成功加载。
     * 变异红形态：重入守卫被擦除后在飞 CF.join 自死锁/StackOverflow。
     */
    @Test
    public void pin_c_circularLoadFailsFast() {
        ILazyLoadOrmModel[] holder = new ILazyLoadOrmModel[1];
        LazyLoadOrmModel model = new LazyLoadOrmModel(null, (name, orm) -> {
            // 加载体同步回环调用同一 key：IN_FLIGHT_KEYS 守卫应 fail-fast
            holder[0].getEntityModel(name);
            return dynModel(name, "TBL_Loop");
        });
        holder[0] = model;

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> model.getEntityModel("Loop"));
        assertTrue(ex.getMessage().contains("circular dynamic entity load"),
                "unexpected message: " + ex.getMessage());

        // 失败后 inFlightLoads 已清理（loader 线程 finally 先于异常传播完成），
        // 换正常 loader 可继续成功加载——状态不被毒化
        LazyLoadOrmModel recovered = new LazyLoadOrmModel(null,
                (name, orm) -> dynModel(name, "TBL_Loop"));
        IEntityModel loaded = recovered.getEntityModel("Loop");
        assertNotNull(loaded);
        assertEquals("Loop", loaded.getName());
    }
}
