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
import io.nop.nosql.core.INosqlListOperations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class LettuceListOperations extends AbstractLettuceOperations implements INosqlListOperations {
    private final String key;

    public LettuceListOperations(LettuceRedisConnectionProvider client, String key) {
        super(client);
        this.key = key;
    }

    @Override
    public CompletableFuture<Long> getSizeAsync() {
        return async().llen(key).toCompletableFuture();
    }

    @Override
    public long getSize() {
        return FutureHelper.syncGet(getSizeAsync());
    }

    @Override
    public CompletableFuture<Void> clearAsync() {
        return async().del(key).thenApply(Functionals.toVoid()).toCompletableFuture();
    }

    @Override
    public void clear() {
        FutureHelper.syncGet(clearAsync());
    }

    @Override
    public CompletableFuture<Void> addAsync(Object value) {
        return async().rpush(key, value).thenApply(Functionals.toVoid()).toCompletableFuture();
    }

    @Override
    public void add(Object value) {
        FutureHelper.syncGet(addAsync(value));
    }

    @Override
    public CompletableFuture<Void> addAllAsync(Collection<?> values) {
        return async().rpush(key, values.toArray()).thenApply(Functionals.toVoid()).toCompletableFuture();
    }

    @Override
    public void addAll(Collection<?> values) {
        FutureHelper.syncGet(addAllAsync(values));
    }

    @Override
    public CompletableFuture<List<Object>> getRangeAsync(long start, int maxCount) {
        return async().lrange(key, start, start + maxCount - 1).toCompletableFuture();
    }

    @Override
    public List<Object> getRange(long start, int maxCount) {
        return FutureHelper.syncGet(getRangeAsync(start, maxCount));
    }

    @Override
    public CompletableFuture<Boolean> trimAsync(long start, long end) {
        return async().ltrim(key, start, end).thenApply(s -> "OK".equals(s)).toCompletableFuture();
    }

    @Override
    public boolean trim(long start, long end) {
        return FutureHelper.syncGet(trimAsync(start, end));
    }

    @Override
    public CompletableFuture<Object> leftPopAsync() {
        return async().lpop(key).toCompletableFuture();
    }

    @Override
    public Object leftPop() {
        return FutureHelper.syncGet(leftPopAsync());
    }

    @Override
    public CompletableFuture<Object> rightPopAsync() {
        return async().rpop(key).toCompletableFuture();
    }

    @Override
    public Object rightPop() {
        return FutureHelper.syncGet(rightPopAsync());
    }

    @Override
    public CompletableFuture<List<Object>> leftPopMultiAsync(int maxCount) {
        return async().lpop(key, maxCount).thenApply(list -> {
            if (list == null)
                return new ArrayList<>(0);
            return list;
        }).toCompletableFuture();
    }

    @Override
    public List<Object> leftPopMulti(int maxCount) {
        return FutureHelper.syncGet(leftPopMultiAsync(maxCount));
    }

    @Override
    public CompletableFuture<Void> forEachItemAsync(Consumer<Object> consumer) {
        return async().lrange(key, 0, -1).thenAccept(list -> {
            if (list != null) {
                for (Object item : list) {
                    consumer.accept(item);
                }
            }
        }).toCompletableFuture();
    }

    @Override
    public void forEachItem(Consumer<Object> consumer) {
        FutureHelper.syncGet(forEachItemAsync(consumer));
    }
}
