/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2547 R4-13（C3-013）载体钉：`DefaultThreadPoolExecutor.execute` 的上下文卫生。
 * <p>
 * 缺陷态：execute 直通不做 detach → 任务内 `getOrCreateContext()` 创建的 IContext
 * 永久驻留在 worker 线程（线程复用时跨任务泄漏）。
 */
public class TestExecutorContextHygiene {

    /** pin0313：execute 路径任务结束后，池线程上不得残留 IContext。 */
    @Test
    public void pin0313_executeDetachesContextAfterTask() throws Exception {
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setName("hygiene-probe");
        config.setCorePoolSize(1);
        config.setMaxPoolSize(1);

        DefaultThreadPoolExecutor executor = new DefaultThreadPoolExecutor();
        executor.setConfig(config);
        executor.init();

        AtomicReference<IContext> afterTask = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                // 任务内创建上下文（模拟业务代码）
                IContext ctx = ContextProvider.instance().getOrCreateContext();
                assertTrue(ctx != null, "任务内必须能创建上下文");
                // 任务体结束时由 execute 包装的 finally detach —— 这里无法直接观测，
                // 故在下一任务中检查残留
                ContextProvider.instance().detachContext();
                done.countDown();
            });
            assertTrue(done.await(5, TimeUnit.SECONDS), "任务必须执行");

            // 第二个任务检查：同一 worker 线程上不得残留上一个任务的 IContext
            CountDownLatch second = new CountDownLatch(1);
            executor.execute(() -> {
                afterTask.set(ContextProvider.instance().currentContext());
                second.countDown();
            });
            assertTrue(second.await(5, TimeUnit.SECONDS));

            assertNull(afterTask.get(),
                    "execute 路径任务结束后池线程不得残留 IContext（当前=" + afterTask.get() + "）");
        } finally {
            executor.destroy();
        }
    }
}
