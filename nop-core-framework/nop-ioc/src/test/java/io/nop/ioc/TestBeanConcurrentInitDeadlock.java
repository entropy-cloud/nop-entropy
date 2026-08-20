/**
 * 并发初始化死锁回归测试：
 * 复现 2026-08-20 现场死锁（main 持 BeanCreationContext 等 ProducedBeanInstance；
 * 调度线程持 ProducedBeanInstance 等 BeanCreationContext）。
 * 旧代码此测试必死锁（@Timeout 快速失败）；新代码（owner+wait/notify 状态机 + 锁外回调）正常通过。
 */
package io.nop.ioc;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.impl.BeanCreationContext;
import io.nop.ioc.impl.BeanDefinition;
import io.nop.ioc.loader.AppBeanContainerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import test.io.entropy.beans.TestConcurrentD;
import test.io.entropy.beans.TestConcurrentP;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.core.unittest.BaseTestCase.setTestConfig;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TestBeanConcurrentInitDeadlock extends BaseTestCase {

    @BeforeAll
    public static void init() {
        setTestConfig(IocConfigs.CFG_IOC_APP_BEANS_CONTAINER_START_MODE, "ALL_LAZY");
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        TestConcurrentP.reset();
        TestConcurrentD.reset();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testConcurrentInitNoDeadlock() throws Exception {
        IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("repro",
                attachmentResource("test_concurrent_init_deadlock.beans.xml"));
        container.start();
        try {
            BeanDefinition pDef = (BeanDefinition) container.getBeanDefinition("beanP");
            BeanCreationContext ctxA = new BeanCreationContext(pDef);

            // Step 1: T1 以 includeCreating=true 创建 P，捕获 ctxA 进 init 闭包
            Object rawP = container.getBean("beanP", true, ctxA);
            assertNotNull(rawP);
            assertSame(rawP, container.getBean("beanP", true, ctxA), "early visibility: raw P must be returned without init");

            // Step 2: T2 成为 P 的 init owner，并在 D 的构造函数内阻塞
            AtomicReference<Throwable> t2Error = new AtomicReference<>();
            Thread t2 = new Thread(() -> {
                try {
                    Object p = container.getBean("beanP", false, null);
                    assertNotNull(p, "t2 must get fully initialized P");
                } catch (Throwable e) {
                    t2Error.set(e);
                }
            }, "t2-p-init-owner");
            t2.start();

            // 等待 T2 进入 D 的构造函数（D 构造内先置标志再阻塞，test 线程轮询确认）
            long deadline = System.currentTimeMillis() + 10000;
            while (!TestConcurrentD.isEntered() && System.currentTimeMillis() < deadline) {
                Thread.sleep(5);
            }
            assertTrue(TestConcurrentD.isEntered(), "T2 must enter D constructor");

            // Step 3: T1 以 ctxA 获取 P，进入 flushInit(ctxA) 并阻塞于 runUntil(P)
            AtomicReference<Throwable> t1Error = new AtomicReference<>();
            AtomicReference<Object> t1Result = new AtomicReference<>();
            Thread t1 = new Thread(() -> {
                try {
                    Object p = container.getBean("beanP", false, ctxA);
                    t1Result.set(p);
                } catch (Throwable e) {
                    t1Error.set(e);
                }
            }, "t1-flush-ctxA");
            t1.start();

            // 等待 T1 阻塞于 P 的 monitor（旧代码 BLOCKED/WAITING；新代码 WAITING），确认已进入 flushInit 并持锁/等待
            deadline = System.currentTimeMillis() + 10000;
            while (System.currentTimeMillis() < deadline) {
                Thread.State state = t1.getState();
                if (state == Thread.State.BLOCKED || state == Thread.State.WAITING)
                    break;
                Thread.sleep(5);
            }
            assertTrue(t1.getState() == Thread.State.BLOCKED || t1.getState() == Thread.State.WAITING,
                    "T1 must be blocked on P monitor, but was " + t1.getState());

            // Step 4: 放开门闩，T2 继续 newObject(D) → flushInit(ctxA)。旧代码死锁，新代码通过
            TestConcurrentD.release();

            t1.join(15000);
            t2.join(15000);

            assertFalse(t1.isAlive(), "T1 must complete");
            assertFalse(t2.isAlive(), "T2 must complete");
            assertNull(t1Error.get(), "T1 error: " + t1Error.get());
            assertNull(t2Error.get(), "T2 error: " + t2Error.get());
            assertNotNull(t1Result.get(), "T1 must get fully initialized P");
            assertTrue(TestConcurrentP.isInitRan(), "P init must run exactly once");
            assertTrue(TestConcurrentD.isInited(), "D must be initialized");
            assertTrue(TestConcurrentP.isDInitedBeforeInit(), "resolvedDepends contract: D must be fully inited before P.init");
        } finally {
            container.stop();
        }
    }

    private void assertFalse(boolean b, String msg) {
        if (b)
            fail(msg);
    }
}