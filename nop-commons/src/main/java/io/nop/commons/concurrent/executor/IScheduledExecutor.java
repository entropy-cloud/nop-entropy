/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.concurrent.executor;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public interface IScheduledExecutor extends IThreadPoolExecutor {

    /**
     * 返回一个包装后的executor，它再调度任务时投递到Executor上执行
     *
     * @param executor 任务执行器
     * @return 一个包装后的Executor
     */
    default IScheduledExecutor executeOn(Executor executor) {
        return new BindScheduledExecutor(this, executor);
    }

    <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);

    default Future<?> scheduleWithRandomDelay(Runnable command, long initialDelay,
                                              long minDelay, long maxDelay, TimeUnit unit) {
        return ExecutorHelper.scheduleWithRandomDelay(this, command, initialDelay, minDelay, maxDelay, unit);
    }
}