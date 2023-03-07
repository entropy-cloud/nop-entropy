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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public interface IScheduledExecutor extends IThreadPoolExecutor {

    <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);
}