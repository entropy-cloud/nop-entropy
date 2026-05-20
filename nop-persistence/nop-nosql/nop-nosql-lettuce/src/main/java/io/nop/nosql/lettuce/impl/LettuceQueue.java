/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.nop.api.core.util.FutureHelper;
import io.nop.commons.functional.Functionals;
import io.nop.nosql.core.INosqlQueue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LettuceQueue extends AbstractLettuceOperations implements INosqlQueue {
    private final String key;

    public LettuceQueue(LettuceRedisConnectionProvider client, String key) {
        super(client);
        this.key = key;
    }

    @Override
    public CompletableFuture<Void> enqueueAsync(Object item) {
        return async().rpush(key, item).thenApply(Functionals.toVoid()).toCompletableFuture();
    }

    @Override
    public void enqueue(Object item) {
        FutureHelper.syncGet(enqueueAsync(item));
    }

    @Override
    public CompletableFuture<Void> enqueueBatchAsync(Collection<?> items) {
        return async().rpush(key, items.toArray()).thenApply(Functionals.toVoid()).toCompletableFuture();
    }

    @Override
    public void enqueueBatch(Collection<?> items) {
        FutureHelper.syncGet(enqueueBatchAsync(items));
    }

    @Override
    public CompletableFuture<Object> dequeueAsync() {
        return async().lpop(key).toCompletableFuture();
    }

    @Override
    public Object dequeue() {
        return FutureHelper.syncGet(dequeueAsync());
    }

    @Override
    public CompletableFuture<List<Object>> dequeueBatchAsync(int maxCount) {
        return async().lpop(key, maxCount).<List<Object>>thenApply(list -> {
            if (list == null)
                return new ArrayList<>(0);
            return new ArrayList<>(list);
        }).toCompletableFuture();
    }

    @Override
    public List<Object> dequeueBatch(int maxCount) {
        return FutureHelper.syncGet(dequeueBatchAsync(maxCount));
    }

    @Override
    public CompletableFuture<Object> peekAsync() {
        return async().lindex(key, 0).toCompletableFuture();
    }

    @Override
    public Object peek() {
        return FutureHelper.syncGet(peekAsync());
    }

    @Override
    public CompletableFuture<Long> sizeAsync() {
        return async().llen(key).toCompletableFuture();
    }

    @Override
    public long size() {
        return FutureHelper.syncGet(sizeAsync());
    }

    @Override
    public CompletableFuture<Void> clearAsync() {
        return async().del(key).thenApply(Functionals.toVoid()).toCompletableFuture();
    }

    @Override
    public void clear() {
        FutureHelper.syncGet(clearAsync());
    }
}
