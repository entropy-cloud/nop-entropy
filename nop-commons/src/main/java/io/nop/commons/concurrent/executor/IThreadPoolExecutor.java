/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.config.IConfigRefreshable;
import io.nop.commons.lang.IDestroyable;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface IThreadPoolExecutor extends Executor, IDestroyable, IConfigRefreshable {

    String getName();

    ThreadPoolConfig getConfig();

    ThreadPoolStats stats();

    <V> CompletableFuture<V> submit(Callable<V> callable);

    <V> CompletableFuture<V> submit(Runnable task, V result);
}