/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.cache;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.1-C2-001 死锁/时序类回归（plan 2504 Phase 2，§七死锁测试纪律）。
 *
 * <p>修复前红形态（基线 ccbccee458 实证）：
 * <ul>
 * <li>单线程装载重入环 = 可重入监视器无限重载直至 {@link StackOverflowError} 逃逸 catch(Exception)；</li>
 * <li>双线程反序装载环 = 双线程持各自 entry 监视器交叉等待，有界观察窗内 BLOCKED 死锁。</li>
 * </ul>
 * 修复后绿形态：装载状态机（LOADING 占位 + 监视器外装载）+ 等待图环检测 + 有界等待兜底 ——
 * 环命中即时类型化（{@link NopException}），无 BLOCKED 残留、有界完成。禁类型存在断言：
 * 全部断言为运行时可观测行为（完成/异常/BLOCKED 检出）。
 */
public class TestResourceLoadCycle_C2_001 extends BaseTestCase {

    /** 装载等待上界（有界兜底档，测试用小值；基线无此键=无界/无等待协议） */
    static final SourceLocation LOC = SourceLocation.fromClass(TestResourceLoadCycle_C2_001.class);

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        // 有界兜底：跨线程等待环检测存在竞态漏检窗，等待必须带上界（≤0=不设上界，此处取有界档）
        AppConfig.getConfigProvider().updateConfigValue(
                AppConfig.varRef(LOC, "nop.core.component.resource-cache.load-wait-timeout", Integer.class, 5000), 5000);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    // ==================== 单线程重入环（SOE 面闭合） ====================

    /**
     * 直连 entry：装载器同线程重入本 entry。修复前=无限重载至 SOE（Error 逃逸，红）；
     * 修复后=类型化环错误（绿）。
     */
    @Test
    public void testSameThreadReentrantLoadTypedCycle_C2_001() {
        ResourceCacheEntry<String> entry = new ResourceCacheEntry<>("/test/c2-001/self.txt");
        // 装载器每次执行都重入本 entry（真实循环依赖形态：loadDslModel 环）
        class ReentrantLoader implements IResourceObjectLoader<String> {
            @Override
            public String loadObjectFromPath(String path) {
                return entry.getObject(false, this);
            }
        }
        ReentrantLoader loader = new ReentrantLoader();

        // 修复后：同线程重入在首次重入点即被装载状态机检出 → 类型化环错误（无 SOE、无深度递归）
        NopException ex = assertThrows(NopException.class, () -> entry.getObject(false, loader));
        assertEquals("nop.err.core.component.resource-cache.load-reentrant-loop", ex.getErrorCode());
    }

    /**
     * 真实链接线（rule 23）：经 ResourceComponentManager 注册测试模型类型，单线程
     * requireComponentModel(a) → loader → requireComponentModel(b) → loader → requireComponentModel(a)
     * （审计实证路径 GlobalFunctions.loadDslModel → requireComponentModel 的同线程环形态）。
     * 修复前=SOE（红）；修复后=类型化环错误（绿）。
     */
    @Test
    public void testRealChainSameThreadModelLoadRing_C2_001() {
        registerRingModelType();
        try {
            NopException ex = assertThrows(NopException.class,
                    () -> ResourceComponentManager.instance().requireComponentModel("/test/c2-001/ring-a.c2ring"));
            assertEquals("nop.err.core.component.resource-cache.load-reentrant-loop", ex.getErrorCode());
        } finally {
            clearRingCache();
        }
    }

    // ==================== 双线程反序环（BLOCKED 死锁面闭合） ====================

    /**
     * 直连 entry 双线程反序装载环。夹具纪律：daemon 线程 + ready/go latch 对齐（双方各自安装占位/
     * 持有监视器后同时交叉）+ 有界观察窗（超时判红并放行 JVM 退出，禁无限 join）。
     * 修复前=双线程 BLOCKED 死锁（红）；修复后=一方完成装载、另一方类型化环错误，有界完成（绿）。
     */
    @Test
    public void testCrossThreadReverseOrderRingBoundedOrTyped_C2_001() throws Exception {
        ResourceCacheEntry<String> e1 = new ResourceCacheEntry<>("/test/c2-001/x.txt");
        ResourceCacheEntry<String> e2 = new ResourceCacheEntry<>("/test/c2-001/y.txt");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        // loader1 在装载 e1 期间取 e2；loader2 在装载 e2 期间取 e1 —— 反序环
        IResourceObjectLoader<String> loader1 = path -> {
            ready.countDown();
            awaitUninterruptibly(go);
            return e2.getObject(false, p -> "leaf-y");
        };
        IResourceObjectLoader<String> loader2 = path -> {
            ready.countDown();
            awaitUninterruptibly(go);
            return e1.getObject(false, p -> "leaf-x");
        };

        AtomicReference<Object> v1 = new AtomicReference<>();
        AtomicReference<Throwable> r1 = new AtomicReference<>();
        AtomicReference<Object> v2 = new AtomicReference<>();
        AtomicReference<Throwable> r2 = new AtomicReference<>();

        Thread t1 = daemonThread(() -> {
            try {
                v1.set(e1.getObject(false, loader1));
            } catch (Throwable t) {
                r1.set(t);
            }
        });
        Thread t2 = daemonThread(() -> {
            try {
                v2.set(e2.getObject(false, loader2));
            } catch (Throwable t) {
                r2.set(t);
            }
        });
        t1.start();
        t2.start();
        assertTrue(ready.await(3, TimeUnit.SECONDS), "both loaders must reach the gate");
        go.countDown();

        // 有界观察窗（15s）：超时即判红并放行 JVM 退出（daemon 线程）
        joinBounded(t1, 15_000);
        joinBounded(t2, 15_000);

        assertFalse(t1.isAlive(), "t1 must finish in bounded window (deadlock reproduced if still alive: BLOCKED)");
        assertFalse(t2.isAlive(), "t2 must finish in bounded window (deadlock reproduced if still alive: BLOCKED)");

        // 绿形态：一方完成装载，另一方获类型化环错误/有界兜底超时；绝不双双悬挂或裸崩溃
        assertTrue(hasTypedRingOutcome(v1, r1) && hasTypedRingOutcome(v2, r2),
                "both sides must resolve to value or typed ring error: r1=" + r1.get() + stackTop(r1.get(), 20)
                        + ", r2=" + r2.get() + stackTop(r2.get(), 20));
        assertTrue(isRingError(r1.get()) || isRingError(r2.get()),
                "true cycle fixture must produce at least one typed ring error: r1=" + r1.get() + ", r2=" + r2.get());
    }

    /**
     * 真实链接线（rule 23）：经 ResourceComponentManager 的双线程反序模型装载环。
     * 修复前=BLOCKED 死锁（红）；修复后=类型化/有界完成（绿）。
     */
    @Test
    public void testRealChainCrossThreadModelLoadRing_C2_001() throws Exception {
        registerRingModelType();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        AtomicReference<Object> va = new AtomicReference<>();
        AtomicReference<Throwable> ra = new AtomicReference<>();
        AtomicReference<Object> vb = new AtomicReference<>();
        AtomicReference<Throwable> rb = new AtomicReference<>();

        Thread ta = daemonThread(() -> {
            try {
                ready.countDown();
                awaitUninterruptibly(go);
                va.set(ResourceComponentManager.instance().requireComponentModel("/test/c2-001/ring-a.c2ring"));
            } catch (Throwable t) {
                ra.set(t);
            }
        });
        Thread tb = daemonThread(() -> {
            try {
                ready.countDown();
                awaitUninterruptibly(go);
                vb.set(ResourceComponentManager.instance().requireComponentModel("/test/c2-001/ring-b.c2ring"));
            } catch (Throwable t) {
                rb.set(t);
            }
        });
        try {
            ta.start();
            tb.start();
            assertTrue(ready.await(3, TimeUnit.SECONDS), "both model loaders must reach the gate");
            go.countDown();

            joinBounded(ta, 15_000);
            joinBounded(tb, 15_000);
            assertFalse(ta.isAlive(), "ta must finish in bounded window (BLOCKED deadlock if alive)");
            assertFalse(tb.isAlive(), "tb must finish in bounded window (BLOCKED deadlock if alive)");

            // 绿形态（裁定②预期表）：环检测即时类型化——双线程各自获类型化失败，或一方完成另一方类型化；
            // 真环夹具下至少一方必须获环错误（reentrant-loop / circular-wait / wait-timeout），禁 SOE/裸崩溃
            assertTrue(hasTypedRingOutcome(va, ra) && hasTypedRingOutcome(vb, rb),
                    "both sides must resolve to value or typed ring error: ra=" + ra.get() + ", rb=" + rb.get());
            boolean ringSeen = isRingError(ra.get()) || isRingError(rb.get());
            assertTrue(ringSeen, "true cycle fixture must produce at least one typed ring error: ra=" + ra.get()
                    + ", rb=" + rb.get());
        } finally {
            clearRingCache();
        }
    }

    static boolean hasTypedRingOutcome(AtomicReference<Object> value, AtomicReference<Throwable> error) {
        if (value.get() != null)
            return true;
        return isRingError(error.get());
    }

    static boolean isRingError(Throwable t) {
        if (!(t instanceof NopException))
            return false;
        String code = ((NopException) t).getErrorCode();
        return "nop.err.core.component.resource-cache.load-reentrant-loop".equals(code)
                || "nop.err.core.component.resource-cache.load-circular-wait".equals(code)
                || "nop.err.core.component.resource-cache.load-wait-timeout".equals(code);
    }

    /** 非类型化失败时附带栈顶摘要（诊断可观测，防不可复现的面丢失证据） */
    static String stackTop(Throwable t, int frames) {
        if (t == null)
            return "";
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] els = t.getStackTrace();
        for (int i = 0; i < Math.min(frames, els.length); i++) {
            sb.append("\n    at ").append(els[i]);
        }
        return sb.toString();
    }

    // ==================== 夹具 ====================

    static final String RING_MODEL_TYPE = "c2ring";
    static volatile boolean ringRegistered;

    /**
     * 注册环形装载测试模型类型：ring-a 装载期间 require ring-b，ring-b 装载期间 require ring-a。
     * 经真实 ResourceComponentManager.ModelLoader → ResourceLoadingCache → ResourceCacheEntry 链装载。
     */
    synchronized void registerRingModelType() {
        if (ringRegistered)
            return;
        ComponentModelConfig config = new ComponentModelConfig();
        config.setModelType(RING_MODEL_TYPE);
        config.getLoaders().put(RING_MODEL_TYPE, new ComponentModelConfig.LoaderConfig(RING_MODEL_TYPE, null, null,
                null, path -> {
            if (path.endsWith("ring-a.c2ring")) {
                return ResourceComponentManager.instance()
                        .requireComponentModel("/test/c2-001/ring-b.c2ring");
            }
            if (path.endsWith("ring-b.c2ring")) {
                return ResourceComponentManager.instance()
                        .requireComponentModel("/test/c2-001/ring-a.c2ring");
            }
            return "leaf";
        }));
        ResourceComponentManager.instance().registerComponentModelConfig(config);
        ringRegistered = true;
    }

    void clearRingCache() {
        ResourceComponentManager.instance().clearCache(RING_MODEL_TYPE);
    }

    static Thread daemonThread(Runnable run) {
        Thread t = new Thread(run, "c2-001-ring-worker");
        t.setDaemon(true);
        return t;
    }

    static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            assertTrue(latch.await(3, TimeUnit.SECONDS), "gate must open");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for gate", e);
        }
    }

    static void joinBounded(Thread t, long millis) throws InterruptedException {
        t.join(millis);
    }
}
