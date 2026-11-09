/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2548 R4-14（C4-005 / C6-003）GlobalExecutors 生命周期载体钉。
 */
public class TestGlobalExecutorsLifecycle {

    /** pin4005a：destroyAndUnregister 后注册表不再持死实例，getExecutor 返回 null（不返回已销毁对象）。 */
    @Test
    public void pin4005a_destroyAndUnregisterRemovesDeadInstance() {
        DefaultThreadPoolExecutor executor = new DefaultThreadPoolExecutor();
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setName("lifecycle-probe-a");
        executor.setConfig(config);
        executor.init();
        GlobalExecutors.register(executor);
        try {
            assertSame(executor, GlobalExecutors.getExecutor(executor.getName()));
            IThreadPoolExecutor removed = GlobalExecutors.destroyAndUnregister(executor.getName());
            assertNotNull(removed);
            assertNull(GlobalExecutors.getExecutor(executor.getName()),
                    "缺陷态：注册表持死实例 → use-after-destroy");
        } finally {
            GlobalExecutors.unregister(executor.getName());
        }
    }

    /** pin4005b：契约钉——借用方绕过入口直调 destroy 仍会留下死实例（residual 诚实登记，本批不改）。 */
    @Test
    public void pin4005b_borrowerDirectDestroyLeavesDeadInstance() {
        DefaultThreadPoolExecutor executor = new DefaultThreadPoolExecutor();
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setName("lifecycle-probe-b");
        executor.setConfig(config);
        executor.init();
        GlobalExecutors.register(executor);
        try {
            executor.destroy();
            // residual：注册表仍持有死实例——这是声明的残余面，不是期望行为
            assertSame(executor, GlobalExecutors.getExecutor(executor.getName()),
                    "residual 契约钉：借用方直调 destroy 后注册表仍持死实例（watch-only）");
        } finally {
            GlobalExecutors.unregister(executor.getName());
        }
    }

    /**
     * pin6003：destroyAll 后注册表不再含任何登记过的池且池被销毁
     * （allExecutors 返回 live map，断言依赖该语义；测试与其他类共 JVM，故按快照键断言而非 isEmpty）。
     */
    @Test
    public void pin6003_destroyAllClearsRegistryAndDestroysPools() {
        DefaultThreadPoolExecutor executor = new DefaultThreadPoolExecutor();
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setName("lifecycle-probe-c");
        executor.setConfig(config);
        executor.init();
        GlobalExecutors.register(executor);

        DefaultScheduledExecutor timer = DefaultScheduledExecutor.newSingleThreadTimer("lifecycle-probe-timer");
        GlobalExecutors.register(timer);

        java.util.Set<String> registered = new java.util.HashSet<>(GlobalExecutors.allExecutors().keySet());
        assertFalse(registered.isEmpty());
        GlobalExecutors.destroyAll();
        try {
            for (String name : registered) {
                assertFalse(GlobalExecutors.allExecutors().containsKey(name),
                        "destroyAll 后注册表不应再含 " + name);
            }
            assertTrue(timer.isDestroyed(), "destroyAll 应销毁登记的池");
        } finally {
            GlobalExecutors.unregister("lifecycle-probe-c");
            GlobalExecutors.unregister("lifecycle-probe-timer");
        }
    }
}
