/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.core;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface INosqlQueue {
    CompletableFuture<Void> enqueueAsync(Object item);

    void enqueue(Object item);

    CompletableFuture<Void> enqueueBatchAsync(Collection<?> items);

    void enqueueBatch(Collection<?> items);

    CompletableFuture<Object> dequeueAsync();

    /** Returns null if the queue is empty */
    Object dequeue();

    CompletableFuture<List<Object>> dequeueBatchAsync(int maxCount);

    List<Object> dequeueBatch(int maxCount);

    CompletableFuture<Object> peekAsync();

    Object peek();

    CompletableFuture<Long> sizeAsync();

    long size();

    CompletableFuture<Void> clearAsync();

    void clear();
}
