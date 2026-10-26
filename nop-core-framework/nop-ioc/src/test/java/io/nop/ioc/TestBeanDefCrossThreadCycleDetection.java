/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.AppBeanContainerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import test.io.entropy.beans.TestBeanDefFlags;
import test.io.entropy.beans.TestCtorInjectA;
import test.io.entropy.beans.TestFbHolderB;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.core.unittest.BaseTestCase.clearTestConfig;
import static io.nop.core.unittest.BaseTestCase.setTestConfig;
import static io.nop.ioc.IocConfigs.CFG_IOC_BEAN_INIT_WAIT_TIMEOUT;
import static io.nop.ioc.IocErrors.ERR_IOC_BEAN_CREATION_CIRCULAR_WAIT;
import static io.nop.ioc.IocErrors.ERR_IOC_BEAN_CREATION_WAIT_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.2-C2-001（←C2-012 不可见边族并案）回归：getBean0 跨线程 BD 等待登记 + 环检出 + 有界兜底。
 *
 * <p>夹具采用不可见边（构造器级按类型获取 / factoryBean 边——均不入构建期拓扑图），
 * 双线程各持一个 BD 创建锁后在构造器内互请求对偶 bean，复现审计缺陷形态：
 * 修复前 synchronized 无超时互等永久 BLOCKED（红 = @Timeout 挂死）；修复后一方抛
 * {@link ERR_IOC_BEAN_CREATION_CIRCULAR_WAIT}（携带 describeChain 等待链快照），
 * 另一方得到传导失败。</p>
 *
 * <p>机制说明：跨线程等待登记汇聚于 getBean0 BD 临界区入口单点，对全部不可见边族
 * （type-injection / factoryBean / supplier / 表达式等）统一生效——两腿夹具分别代表
 * E1（构造器按类型获取）与 E2（factoryBean）边族，共用同一登记/检出机制。</p>
 */
public class TestBeanDefCrossThreadCycleDetection extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        TestBeanDefFlags.reset();
        TestCtorInjectA.container = null;
        TestFbHolderB.container = null;
        setTestConfig(CFG_IOC_BEAN_INIT_WAIT_TIMEOUT, 5000);
    }

    @AfterEach
    public void tearDown() {
        // 释放可能仍阻塞的夹具线程（daemon + 有界观察窗纪律）
        TestBeanDefFlags.releaseCtorHang();
        clearTestConfig(CFG_IOC_BEAN_INIT_WAIT_TIMEOUT.getName());
    }

    /**
     * 构造器级按类型获取（type-injection 边族）跨线程环：双方就位后同时放行，
     * 后完成等待登记者命中环 → 类型化环错误（等待链含双方 bean 对应线程名）；
     * 另一方获得传导失败而非永久 BLOCKED。
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testCtorTypeInjectionCrossThreadCycleTypedFailure() throws Exception {
        IBeanContainerImplementor container = loadContainer();
        try {
            TestCtorInjectA.container = container;

            AtomicReference<Throwable> errA = new AtomicReference<>();
            AtomicReference<Throwable> errB = new AtomicReference<>();

            Thread t1 = newDaemon("create-ctorInjectA",
                    () -> container.getBean("ctorInjectA"), errA);
            Thread t2 = newDaemon("create-ctorInjectB",
                    () -> container.getBean("ctorInjectB"), errB);

            t1.start();
            assertTrue(awaitFlag(TestBeanDefFlags::isACtorEntered),
                    "t1 必须先进入 ctorInjectA 构造器（持 A 的 BD 锁）");
            t2.start();
            assertTrue(awaitFlag(TestBeanDefFlags::isBCtorEntered),
                    "t2 必须先进入 ctorInjectB 构造器（持 B 的 BD 锁）");

            // 双方各持一 BD 锁后同时放行：构造器内互请求对偶 bean → BD 等待环
            TestBeanDefFlags.signalGoNext();

            t1.join(20000);
            t2.join(20000);
            assertFalse(t1.isAlive(), "t1 必须有界收敛（修复前永久 BLOCKED 挂死）");
            assertFalse(t2.isAlive(), "t2 必须有界收敛（修复前永久 BLOCKED 挂死）");

            NopException ring = typedErrorOf(errA, errB, ERR_IOC_BEAN_CREATION_CIRCULAR_WAIT);
            assertNotNull(ring, "必须一方抛出 BD 环错误: A=" + errA.get() + ", B=" + errB.get());
            String chain = String.valueOf(ring.getMessage());
            assertTrue(chain.contains("create-ctorInjectA") && chain.contains("create-ctorInjectB"),
                    "环错误必须携带 describeChain 等待链快照（含双方 bean）: " + chain);

            // 双 bean 构造期互引无成功路径：两线程都必须得到类型化失败（不得静默）
            assertNotNull(errA.get(), "t1 必须得到失败传导");
            assertNotNull(errB.get(), "t2 必须得到失败传导");
        } finally {
            container.stop();
        }
    }

    /**
     * factoryBean 边族（E2）同型：fbProdA 经 factory-bean 边依赖 fbHolderB（不可见），
     * fbHolderB 构造器反向获取 fbProdA——跨线程 BD 互等环由同一等待登记单点检出。
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testFactoryBeanCrossThreadCycleTypedFailure() throws Exception {
        IBeanContainerImplementor container = loadContainer();
        try {
            TestFbHolderB.container = container;

            AtomicReference<Throwable> errA = new AtomicReference<>();
            AtomicReference<Throwable> errB = new AtomicReference<>();

            // t2 先持 fbHolderB 的 BD 锁（构造器内等待放行信号）
            Thread t2 = newDaemon("create-fbHolderB",
                    () -> container.getBean("fbHolderB"), errB);
            t2.start();
            assertTrue(awaitFlag(TestBeanDefFlags::isBCtorEntered),
                    "t2 必须先进入 fbHolderB 构造器（持 B 的 BD 锁）");

            // t1 创建 fbProdA：factoryBean 解析处等待 fbHolderB 的 BD 锁
            Thread t1 = newDaemon("create-fbProdA",
                    () -> container.getBean("fbProdA"), errA);
            t1.start();
            assertTrue(awaitThreadBlocking(t1), "t1 必须阻塞于 factoryBean 目标 bean 的 BD 锁");

            TestBeanDefFlags.signalGoNext();

            t1.join(20000);
            t2.join(20000);
            assertFalse(t1.isAlive(), "t1 必须有界收敛");
            assertFalse(t2.isAlive(), "t2 必须有界收敛");

            NopException ring = typedErrorOf(errA, errB, ERR_IOC_BEAN_CREATION_CIRCULAR_WAIT);
            assertNotNull(ring, "factoryBean 环必须被类型化检出: A=" + errA.get() + ", B=" + errB.get());
            assertNotNull(errA.get(), "t1 必须得到失败传导");
            assertNotNull(errB.get(), "t2 必须得到失败传导");
        } finally {
            container.stop();
        }
    }

    /**
     * 有界兜底腿：目标 bean 构造器阻塞（持 BD 锁不放、无环）→ 等待方按
     * nop.ioc.bean-init.wait-timeout 预算超时，抛类型化等待超时错误（携带等待链快照）。
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testCreationWaitBoundedFallback() throws Exception {
        IBeanContainerImplementor container = loadContainer();
        AtomicReference<Throwable> err = new AtomicReference<>();
        AtomicReference<Object> res = new AtomicReference<>();
        try {
            AtomicReference<Throwable> holderErr = err;
            AtomicReference<Object> holderRes = res;
            Thread t1 = newDaemon("create-ctorHangBean",
                    () -> holderRes.set(container.getBean("ctorHangBean")), holderErr);
            t1.start();
            assertTrue(awaitFlag(TestBeanDefFlags::isCtorHangEntered),
                    "t1 必须进入阻塞构造器（持 BD 锁）");

            setTestConfig(CFG_IOC_BEAN_INIT_WAIT_TIMEOUT, 1000);
            long begin = System.currentTimeMillis();
            NopException e = assertThrows(NopException.class, () -> container.getBean("ctorHangBean"));
            long elapsed = System.currentTimeMillis() - begin;
            assertEquals(ERR_IOC_BEAN_CREATION_WAIT_TIMEOUT.getErrorCode(), e.getErrorCode(),
                    "无环长等待必须有界兜底类型化收敛");
            assertTrue(elapsed < 10_000, "等待必须有界收敛，实际耗时 " + elapsed + "ms");
            assertTrue(String.valueOf(e.getMessage()).contains("create-ctorHangBean"),
                    "超时错误必须携带等待链快照: " + e.getMessage());
        } finally {
            TestBeanDefFlags.releaseCtorHang();
        }

        // 门闩放行后持锁方正常完成，bean 正常创建（无 BLOCKED 残留）
        // t1/err/res 为 effectively final 局部引用，等待其收敛
        long deadline = System.currentTimeMillis() + 20_000;
        while (err.get() == null && res.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertNull(err.get(), "持锁方放行后必须正常完成: " + err.get());
        assertNotNull(res.get(), "门闩放行后 bean 必须创建成功");
    }

    private IBeanContainerImplementor loadContainer() {
        IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_bean_def_cross_cycle.beans.xml"));
        container.start();
        return container;
    }

    private static Thread newDaemon(String name, Runnable body, AtomicReference<Throwable> err) {
        Thread t = new Thread(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                err.set(e);
            }
        }, name);
        t.setDaemon(true);
        return t;
    }

    private static boolean awaitFlag(java.util.function.BooleanSupplier cond) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean())
                return true;
            Thread.sleep(5);
        }
        return false;
    }

    private static boolean awaitThreadBlocking(Thread t) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Thread.State s = t.getState();
            if (s == Thread.State.BLOCKED || s == Thread.State.WAITING || s == Thread.State.TIMED_WAITING)
                return true;
            Thread.sleep(5);
        }
        return false;
    }

    /**
     * 环/等待错误在构造器内抛出时经反射 InvocationTargetException 包装
     * （createInstance catch(Exception) → NopWrapException），按因果链任意深度匹配类型化错误码。
     */
    static NopException typedErrorOf(AtomicReference<Throwable> a, AtomicReference<Throwable> b, ErrorCode code) {
        for (Throwable t : new Throwable[]{a.get(), b.get()}) {
            NopException found = typedErrorInChain(t, code);
            if (found != null)
                return found;
        }
        return null;
    }

    private static NopException typedErrorInChain(Throwable t, ErrorCode code) {
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 10) {
            if (cur instanceof NopException && code.getErrorCode().equals(((NopException) cur).getErrorCode()))
                return (NopException) cur;
            cur = cur.getCause();
            depth++;
        }
        return null;
    }
}
