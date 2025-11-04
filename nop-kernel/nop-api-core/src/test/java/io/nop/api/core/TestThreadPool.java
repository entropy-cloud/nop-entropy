/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TestThreadPool {
    @Test
    public void testRemove() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        executor.setRemoveOnCancelPolicy(true);
        for (int i = 0; i < 10; i++) {
            Future future = executor.schedule(() -> null, 100, TimeUnit.SECONDS);
            future.cancel(true);
        }

        executor.schedule(() -> null, 1, TimeUnit.MILLISECONDS);
        System.out.println(executor.getQueue().size());
    }
}
