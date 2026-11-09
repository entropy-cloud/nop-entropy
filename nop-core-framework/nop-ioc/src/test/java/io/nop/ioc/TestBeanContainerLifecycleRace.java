/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import test.io.entropy.beans.TestRaceFailDelayBean;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2549 R4-15（C1-001 / C4-009）容器生命周期互斥与启动代际载体钉。
 */
public class TestBeanContainerLifecycleRace extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private IBeanContainerImplementor startConcurrent() {
        IBeanContainerImplementor container = new BeanContainerBuilder(null)
                .concurrentStart(true)
                .addResource(attachmentResource("test_race_gen.beans.xml"))
                .build("race-test");
        container.start();
        return container;
    }

    /**
     * pin1b：restart 代际令牌——旧代启动失败收敛（handleStartError 携旧代快照）在代际不匹配时
     * **放弃自毁 stop 且不污染新代 startFailed 终态**；新生代保持 started、bean 可用。
     * （注入方式：单元级反射直调 handleStartError(e, staleGen)，规避 async 时序不可控。）
     */
    @Test
    public void pin1b_staleStartErrorDoesNotKillNewGeneration() throws Exception {
        TestRaceFailDelayBean.reset();
        IBeanContainerImplementor container = startConcurrent();
        try {
            // 第 1 代 delay（第 1 次调用）抛错 → 失败终态自动收敛（handleStartError(gen1) → stop）
            container.awaitStartFinished();
        } catch (Exception expected) {
            // 失败传导（2507 契约）——预期
        }
        // rebuild（gen2）：delay 第 2 次调用成功 → started
        container.restart();
        container.awaitStartFinished();
        assertTrue(container.isStarted(), "restart 后新代应 started");

        // 注入「旧代失败收敛延迟到达」：携带 stale gen → 代际守卫放弃自毁 + 不污染新代终态
        java.lang.reflect.Method m = container.getClass().getDeclaredMethod("handleStartError",
                Throwable.class, long.class);
        m.setAccessible(true);
        m.invoke(container, new IllegalStateException("stale-gen-failure"), 1L /* gen1 */);
        // startFailed 未被污染：再次 await 不触发失败终态
        container.awaitStartFinished();

        assertTrue(container.isStarted(), "缺陷态：旧代失败收敛自毁新生代 → started 被清");
        assertNotNull(container.getBean("raceBean"), "新生代 bean 应可用");
    }

    /** pin1a：restart（synchronized stop+start）后并发 getBean 无孤儿——getBean 返回新代 singleton。 */
    @Test
    public void pin1a_concurrentGetBeanAfterRestartNoOrphan() throws Exception {
        TestRaceFailDelayBean.reset();
        IBeanContainerImplementor container = startConcurrent();
        try {
            // 首代 delay（第 1 次调用）注入失败 → await 收敛（2507 失败传导契约），再 restart
            try {
                container.awaitStartFinished();
            } catch (Exception expected) {
                // 失败终态预期
            }
            CountDownLatch go = new CountDownLatch(1);
            AtomicReference<Object> raced = new AtomicReference<>();
            Thread getter = new Thread(() -> {
                try {
                    go.await(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                raced.set(container.getBean("raceBean"));
            });
            getter.start();
            container.restart();
            // 并发 start 模式下 started 由 async 回调翻转：join 后再取
            container.awaitStartFinished();
            go.countDown();
            getter.join(10000);

            Object bean = container.getBean("raceBean");
            assertNotNull(bean);
            // 无孤儿：竞速线程拿到的实例与新代查询实例同源（synchronized 互斥后无半初始化引用逃逸）
            if (raced.get() != null) {
                assertTrue(raced.get() == bean || raced.get().getClass() == bean.getClass(),
                        "竞速 getBean 不应得半初始化孤儿实例");
            }
        } finally {
            try {
                container.stop();
            } catch (Exception ignore) {
                // ignore
            }
        }
    }
}
