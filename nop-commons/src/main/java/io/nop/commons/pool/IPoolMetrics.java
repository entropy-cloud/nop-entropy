/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.pool;

import io.nop.commons.metrics.IMetrics;

public interface IPoolMetrics<O extends IPooledObject> extends IMetrics {
    void onAcquire(IPool<O> pool, PoolAcquireOptions options);

    void onRelease(IPool<O> pool, O object, boolean shouldDestroy, long usageDuration);

    void onAcquireSuccess(IPool<O> pool, O object, long duration);

    void onAcquireFailure(IPool<O> pool, Throwable e, long duration);

    void onDiscard(IPool<O> pool, O object);

    void onBorrowIdleFromPool(IPool<O> pool, O object);

    void onReturnIdleToPool(IPool<O> pool, O object);

    void onExceedMaxActive(IPool<O> pool, int activeCount, int maxActive);

    void onExceedMaxIdle(IPool<O> pool, int idleCount, int maxIdle);

    void onExceedMaxWait(IPool<O> pool, int waitingCount, int maxWaitCount);

    void onCreate(IPool<O> pool);

    void onCreateSuccess(IPool<O> pool, O object, long creationDuration);

    void onCreateFailure(IPool<O> pool, Throwable e, long creationDuration);

    void onDestroy(IPool<O> pool, O object);
}