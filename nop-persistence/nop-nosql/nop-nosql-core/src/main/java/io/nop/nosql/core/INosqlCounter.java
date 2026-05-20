/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.core;

import java.util.concurrent.CompletableFuture;

public interface INosqlCounter {
    CompletableFuture<Long> incrementAsync(long delta);

    long increment(long delta);

    CompletableFuture<Long> getAsync();

    long get();

    CompletableFuture<Long> getAndIncrementAsync(long delta);

    long getAndIncrement(long delta);

    CompletableFuture<Void> resetAsync(long value);

    void reset(long value);

    CompletableFuture<Long> getAndResetAsync();

    long getAndReset();
}
