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
import io.nop.ioc.loader.BeanContainerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import test.io.entropy.beans.MyCreationLog;
import test.io.entropy.beans.TestStartHangInitBean;

import java.util.concurrent.TimeUnit;

import static io.nop.core.unittest.BaseTestCase.clearTestConfig;
import static io.nop.core.unittest.BaseTestCase.setTestConfig;
import static io.nop.ioc.IocConfigs.CFG_IOC_APP_BEANS_CONTAINER_START_JOIN_TIMEOUT;
import static io.nop.ioc.IocErrors.ERR_IOC_START_JOIN_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.2-C4-003（←C4-008 三形态并案）回归：concurrent-start 框架级 join + 失败终态 + 有界超时。
 *
 * <p>修复前红形态：handleStartError 仅 log+stop，调用方无感知（awaitStartFinished 静默返回/
 * 永久 pending）；F2 Error 孤儿下图 future 永不完成；F5 成功回调异常静默。</p>
 *
 * <p>修复后绿形态：失败注入后 awaitStartFinished 抛出（原始失败重抛/超时类型化失败），
 * 容器终态 stopped、started 不翻转、已建 bean 销毁、失败终态可查询（再次调用快速失败）。</p>
 *
 * <p>N5 覆盖边界注记：本类覆盖 concurrent-start 的失败形态；成功路径仅
 * {@code TestBeanDepends#testAsyncStartOrder} 一例（断言 ioc:after 顺序 + bean 创建数），
 * 不重复覆盖启动顺序语义。</p>
 */
public class TestIocConcurrentStartFailurePropagation extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        clearTestConfig(CFG_IOC_APP_BEANS_CONTAINER_START_JOIN_TIMEOUT.getName());
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        MyCreationLog.reset();
        TestStartHangInitBean.reset();
    }

    private IBeanContainerImplementor startConcurrent(String resource) {
        IBeanContainerImplementor container = new BeanContainerBuilder(null)
                .concurrentStart(true)
                .addResource(attachmentResource(resource))
                .build("test");
        container.start();
        return container;
    }

    /**
     * 原始失败传导腿：启动 bean init 抛 Exception → join 重抛原始异常 + 失败终态（stop + started 不翻转）。
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testInitFailurePropagatesAndContainerStopped() {
        IBeanContainerImplementor container = startConcurrent("test_concurrent_start_fail_init.beans.xml");

        RuntimeException e = assertThrows(RuntimeException.class, container::awaitStartFinished);
        assertEquals("simulated-init-failure", rootCause(e).getMessage(),
                "join 必须重抛原始启动失败而非静默返回");

        assertFalse(container.isStarted(), "失败终态下 started 不得翻转");
        assertFalse(container.isRunning(), "失败终态必须已 stop");
        assertTrue(MyCreationLog.order.contains("destroy:okBean"), "已建 bean 必须被失败终态收敛销毁");

        // 失败终态可查询：再次 awaitStartFinished 快速失败（异常终态保留）
        assertThrows(RuntimeException.class, container::awaitStartFinished);
    }

    /**
     * C4-008 F2 腿：Error 逃逸 runTask catch(Exception) → 图 future 永不完成（孤儿图）→
     * join 超时档兜底收敛为失败终态并抛类型化超时错误。
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testErrorOrphanConvergesToFailureTerminal() {
        setTestConfig(CFG_IOC_APP_BEANS_CONTAINER_START_JOIN_TIMEOUT, 1500);
        try {
            IBeanContainerImplementor container = startConcurrent("test_concurrent_start_fail_error.beans.xml");

            NopException e = assertThrows(NopException.class, container::awaitStartFinished);
            assertEquals(ERR_IOC_START_JOIN_TIMEOUT.getErrorCode(), e.getErrorCode(),
                    "Error 孤儿必须由 join 超时档类型化收敛");

            assertFalse(container.isStarted(), "started 不得翻转");
            assertFalse(container.isRunning(), "join 侧失败终态收敛必须 stop 容器");
            assertTrue(MyCreationLog.order.contains("destroy:okBean"), "join 侧收敛必须销毁已建 bean");

            // 失败终态可查询：startFuture 已以超时错误完成，再次调用快速失败
            NopException e2 = assertThrows(NopException.class, container::awaitStartFinished);
            assertEquals(ERR_IOC_START_JOIN_TIMEOUT.getErrorCode(), e2.getErrorCode());
        } finally {
            clearTestConfig(CFG_IOC_APP_BEANS_CONTAINER_START_JOIN_TIMEOUT.getName());
        }
    }

    /**
     * C4-008 F5 腿：启动成功分支回调（runDelayMethod）抛出异常 → 不再静默，
     * 统一走 handleStartError 落失败终态并向 join 传播。
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testSuccessCallbackFailureNonSilent() {
        IBeanContainerImplementor container = startConcurrent("test_concurrent_start_fail_delay.beans.xml");

        RuntimeException e = assertThrows(RuntimeException.class, container::awaitStartFinished);
        assertEquals("simulated-delay-failure", rootCause(e).getMessage(),
                "成功回调异常必须非静默并向 join 传播");

        assertFalse(container.isStarted(), "回调异常时 started 不得翻转");
        assertFalse(container.isRunning(), "回调异常必须落失败终态（stop）");
    }

    /**
     * C4-008 F4 腿：启动 bean init 永久挂起 → join 有界超时兜底（类型化错误而非永久 pending）。
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testHungInitClosedByJoinTimeout() {
        setTestConfig(CFG_IOC_APP_BEANS_CONTAINER_START_JOIN_TIMEOUT, 1500);
        try {
            IBeanContainerImplementor container = startConcurrent("test_concurrent_start_hang.beans.xml");

            NopException e = assertThrows(NopException.class, container::awaitStartFinished);
            assertEquals(ERR_IOC_START_JOIN_TIMEOUT.getErrorCode(), e.getErrorCode(),
                    "挂死 init 必须由 join 超时档类型化收敛");

            assertFalse(container.isStarted(), "started 不得翻转");
            assertFalse(container.isRunning(), "join 侧失败终态收敛必须 stop 容器");
            assertTrue(MyCreationLog.order.contains("destroy:okBean"), "已建 bean 必须被销毁");
        } finally {
            // 释放挂死 init，回收 worker 线程（daemon 夹具有界窗纪律）
            TestStartHangInitBean.releaseInit();
            clearTestConfig(CFG_IOC_APP_BEANS_CONTAINER_START_JOIN_TIMEOUT.getName());
        }
    }

    static Throwable rootCause(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur)
            cur = cur.getCause();
        return cur;
    }
}
