/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc;

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
import test.io.entropy.beans.TestPbiRingA;
import test.io.entropy.beans.TestPbiRingFlags;
import test.io.entropy.beans.TestPbiSlowSetupBean;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.core.unittest.BaseTestCase.clearTestConfig;
import static io.nop.core.unittest.BaseTestCase.setTestConfig;
import static io.nop.ioc.IocConfigs.CFG_IOC_BEAN_INIT_WAIT_TIMEOUT;
import static io.nop.ioc.IocErrors.ERR_IOC_BEAN_INIT_CIRCULAR_WAIT;
import static io.nop.ioc.IocErrors.ERR_IOC_BEAN_INIT_WAIT_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.2-C2-002（←C1-002 跨维度并案）回归：PBI 等待有界化 + 等待链环嗅探。
 *
 * <p>互等环腿：双 owner（A/B 的 init 各在对方线程推进）经 includeCreating=false 交叉获取对偶 bean，
 * 修复前裸 wait() 无超时永久互等（红 = @Timeout 挂死）；修复后等待前经 LoadWaitGraph 登记
 * 「等待线程 → owner」边，后完成登记者命中环抛 {@link ERR_IOC_BEAN_INIT_CIRCULAR_WAIT}
 * （携带 describeChain 快照），另一方经 initError 广播得到传导失败。</p>
 *
 * <p>有界兜底腿：目标 bean 在属性赋值阶段（PROPERTY_SET 推进前）阻塞且无 owner 可回溯 →
 * 等待方按 nop.ioc.bean-init.wait-timeout 预算超时，抛 {@link ERR_IOC_BEAN_INIT_WAIT_TIMEOUT}
 * （携带 beanId/createThread/initThread/等待链快照）。</p>
 *
 * <p>selfWait 同线程 fail-fast 与 initError 广播协议零改动由
 * {@code TestBeanConcurrentInitDeadlock}（机制级钉）零回退覆盖。</p>
 */
public class TestPbiWaitBoundedAndCycleSniff extends BaseTestCase {

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
        TestPbiRingFlags.reset();
        TestPbiSlowSetupBean.reset();
        TestPbiRingA.container = null;
        setTestConfig(CFG_IOC_BEAN_INIT_WAIT_TIMEOUT, 5000);
    }

    @AfterEach
    public void tearDown() {
        // 释放可能仍阻塞的夹具线程（daemon + 有界观察窗纪律）
        TestPbiSlowSetupBean.releaseSetter();
        clearTestConfig(CFG_IOC_BEAN_INIT_WAIT_TIMEOUT.getName());
    }

    /**
     * PBI 双 owner 互等环：t2 持 B 创建锁等待 A 的 init 开始；t1 创建并 init A 后经
     * includeCreating=false 获取 B（t2 持 B 的 BD 锁）；B 创建完成后 t2 成为 B 的 init owner，
     * 反向获取 A——两等待点互等，后完成登记者命中环。
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testPbiCrossThreadInitCycleTypedFailure() throws Exception {
        IBeanContainerImplementor container = loadContainer();
        try {
            TestPbiRingA.container = container;

            AtomicReference<Throwable> errA = new AtomicReference<>();
            AtomicReference<Throwable> errB = new AtomicReference<>();

            // t2 先创建 pbiRingB：构造器等待 A 的 init 开始（保证 A 的 init owner 先置位）
            Thread t2 = newDaemon("init-pbiRingB", () -> container.getBean("pbiRingB"), errB);
            t2.start();
            assertTrue(awaitFlag(TestPbiRingFlags::isBCtorEntered),
                    "t2 必须先进入 pbiRingB 构造器（持 B 的 BD 锁）");

            // t1 创建并 init pbiRingA：initMe 标记 aInitStarted（放行 B 构造）后获取 pbiRingB
            Thread t1 = newDaemon("init-pbiRingA", () -> container.getBean("pbiRingA"), errA);
            t1.start();

            t1.join(25000);
            t2.join(25000);
            assertFalse(t1.isAlive(), "t1 必须有界收敛（修复前永久互等挂死）");
            assertFalse(t2.isAlive(), "t2 必须有界收敛（修复前永久互等挂死）");

            NopException ring = TestBeanDefCrossThreadCycleDetection.typedErrorOf(errA, errB,
                    ERR_IOC_BEAN_INIT_CIRCULAR_WAIT);
            assertNotNull(ring, "必须检出 PBI 互等环: A=" + errA.get() + ", B=" + errB.get());
            String chain = String.valueOf(ring.getMessage());
            assertTrue(chain.contains("init-pbiRingA") && chain.contains("init-pbiRingB"),
                    "环错误必须携带 describeChain 等待链快照（含双方 bean）: " + chain);

            // A/B init 互引无成功路径：双方都必须得到失败传导（一方环错误原样广播）
            Throwable errorA = errA.get();
            assertNotNull(errorA, "t1 必须得到失败传导");
            assertTrue(errorA instanceof NopException
                            && ERR_IOC_BEAN_INIT_CIRCULAR_WAIT.getErrorCode()
                                    .equals(((NopException) errorA).getErrorCode()),
                    "环错误必须经 initError 广播传导给另一等待方: " + errorA);
            assertNotNull(errB.get(), "t2 必须得到失败传导");
        } finally {
            container.stop();
        }
    }

    /**
     * 有界兜底腿：目标 bean 属性赋值阶段阻塞（PROPERTY_SET 不推进、无 owner 可回溯）→
     * 等待方按预算超时，抛类型化超时错误（携带 beanId/createThread/initThread/等待链快照）；
     * 门闩放行后创建方正常完成、bean 可用。
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testPbiWaitTimeoutTypedFailure() throws Exception {
        IBeanContainerImplementor container = loadContainer();
        AtomicReference<Throwable> err = new AtomicReference<>();
        AtomicReference<Object> res = new AtomicReference<>();
        try {
            setTestConfig(CFG_IOC_BEAN_INIT_WAIT_TIMEOUT, 1000);
            AtomicReference<Throwable> holderErr = err;
            AtomicReference<Object> holderRes = res;
            Thread t1 = newDaemon("create-pbiSlow",
                    () -> holderRes.set(container.getBean("pbiSlow")), holderErr);
            t1.start();
            assertTrue(awaitFlag(TestPbiSlowSetupBean::isSetterEntered),
                    "t1 必须阻塞于属性赋值（PROPERTY_SET 未推进）");

            long begin = System.currentTimeMillis();
            NopException e = assertThrows(NopException.class, () -> container.getBean("pbiSlow", false, null));
            long elapsed = System.currentTimeMillis() - begin;
            assertEquals(ERR_IOC_BEAN_INIT_WAIT_TIMEOUT.getErrorCode(), e.getErrorCode(),
                    "无 owner 长等待必须有界兜底类型化收敛");
            assertTrue(elapsed < 10_000, "等待必须有界收敛，实际耗时 " + elapsed + "ms");
            String msg = String.valueOf(e.getMessage());
            assertTrue(msg.contains("pbiSlow") && msg.contains("createThread=") && msg.contains("initThread="),
                    "超时错误必须携带 beanId/createThread/initThread 诊断信息: " + msg);
        } finally {
            TestPbiSlowSetupBean.releaseSetter();
        }

        // 门闩放行后创建方正常完成，bean 完整初始化（等待超时不产生半成品副作用）
        long deadline = System.currentTimeMillis() + 20_000;
        while (err.get() == null && res.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertNull(err.get(), "门闩放行后创建方必须正常完成: " + err.get());
        assertNotNull(res.get(), "门闩放行后 bean 必须创建成功");
    }

    private IBeanContainerImplementor loadContainer() {
        IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_pbi_wait_ring.beans.xml"));
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
}
