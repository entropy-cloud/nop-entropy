package io.nop.netty.ssl;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.nop.netty.config.SslConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.3-C1-014（plan 2561 裁定 A）钉：NettySslEngineFactory 惰性缓存——并发首装必须只构建一次
 * （synchronized 惰性块）且各线程取回同一实例；SslConfig 缓存字段必须 volatile。
 * 变异红形态：去掉 synchronized（恢复无锁 check-then-set）→ 并发线程各自通过 null 检查、
 * buildSslContext 计数 &gt; 1 → 红。
 */
public class TestNettySslEngineFactory {

    /** 计数桩：真实构建 SslContext（forClient 零 keystore 依赖），拉长构建窗口放大竞态。 */
    static class CountingFactory extends NettySslEngineFactory {
        final AtomicInteger buildCalls = new AtomicInteger();

        @Override
        protected SslContext buildSslContext(SslConfig sslConfig, boolean clientMode) {
            try {
                // 竞态窗口：无锁形态下所有并发线程都会穿过 null 检查并各自走到这里
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            buildCalls.incrementAndGet();
            try {
                // 桩上下文：client/server 两路径均以零 keystore 依赖的 client 上下文占位（钉的是缓存次数/同一性）
                return SslContextBuilder.forClient().build();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Test
    public void concurrentFirstInitBuildsExactlyOnce() throws Exception {
        CountingFactory factory = new CountingFactory();
        SslConfig config = new SslConfig();
        int threads = 8;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            java.util.List<Future<SslContext>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return factory.getSslContext(config, true);
                }));
            }
            SslContext first = futures.get(0).get(20, TimeUnit.SECONDS);
            assertNotNull(first);
            for (Future<SslContext> future : futures) {
                assertSame(first, future.get(20, TimeUnit.SECONDS),
                        "C1-014: 并发首装各线程必须取回同一 SslContext 实例");
            }
            assertEquals(1, factory.buildCalls.get(),
                    "C1-014: 并发首装 buildSslContext 必须恰好执行一次（无锁 check-then-set 竞态 → 双建泄漏）");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void serverAndClientModesCacheIndependently() {
        CountingFactory factory = new CountingFactory();
        SslConfig config = new SslConfig();
        SslContext clientCtx = factory.getSslContext(config, true);
        SslContext clientCtxAgain = factory.getSslContext(config, true);
        SslContext serverCtx = factory.getSslContext(config, false);
        assertSame(clientCtx, clientCtxAgain, "client 路径命中缓存不再构建");
        assertEquals(2, factory.buildCalls.get(), "client/server 两路径各自惰性构建一次");
        assertNotNull(serverCtx);
    }

    @Test
    public void sslConfigCacheFieldsAreVolatile() throws Exception {
        assertVolatile(SslConfig.class.getDeclaredField("clientSslContext"));
        assertVolatile(SslConfig.class.getDeclaredField("serverSslContext"));
    }

    private static void assertVolatile(Field field) {
        int modifiers = field.getModifiers();
        assertTrue(Modifier.isVolatile(modifiers),
                "C1-014: SslConfig." + field.getName() + " 必须为 volatile（并发早退读可见性结构钉）");
    }
}
