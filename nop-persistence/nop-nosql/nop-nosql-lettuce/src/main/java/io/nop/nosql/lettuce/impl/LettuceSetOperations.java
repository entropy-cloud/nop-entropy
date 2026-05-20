/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import io.nop.commons.functional.Functionals;
import io.nop.nosql.core.INosqlSetOperations;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class LettuceSetOperations implements INosqlSetOperations {
    private final LettuceRedisConnectionProvider client;
    private final String key;

    public LettuceSetOperations(LettuceRedisConnectionProvider client, String key) {
        this.client = client;
        this.key = key;
    }

    protected RedisAdvancedClusterAsyncCommands<String, Object> async() {
        return client.getConnection().async();
    }

    @Override
    public CompletableFuture<Boolean> addAsync(Object value) {
        return async().sadd(key, value).thenApply(n -> n > 0).toCompletableFuture();
    }

    @Override
    public boolean add(Object value) {
        return addAsync(value).join();
    }

    @Override
    public CompletableFuture<Boolean> removeAsync(Object value) {
        return async().srem(key, value).thenApply(n -> n > 0).toCompletableFuture();
    }

    @Override
    public boolean remove(Object value) {
        return removeAsync(value).join();
    }

    @Override
    public CompletableFuture<Void> removeAllAsync(Collection<?> values) {
        return async().srem(key, values.toArray()).thenApply(Functionals.toVoid()).toCompletableFuture();
    }

    @Override
    public void removeAll(Collection<?> values) {
        removeAllAsync(values).join();
    }

    @Override
    public CompletableFuture<Boolean> containsAsync(Object value) {
        return async().sismember(key, value).toCompletableFuture();
    }

    @Override
    public boolean contains(Object value) {
        return containsAsync(value).join();
    }

    @Override
    public CompletableFuture<Boolean> containsAllAsync(Collection<?> values) {
        return async().smismember(key, values.toArray()).thenApply(list -> {
            if (list == null)
                return false;
            for (Boolean b : list) {
                if (!Boolean.TRUE.equals(b))
                    return false;
            }
            return true;
        }).toCompletableFuture();
    }

    @Override
    public boolean containsAll(Collection<?> values) {
        return containsAllAsync(values).join();
    }

    @Override
    public CompletableFuture<Long> sizeAsync() {
        return async().scard(key).toCompletableFuture();
    }

    @Override
    public long size() {
        return sizeAsync().join();
    }

    @Override
    public CompletableFuture<Set<Object>> membersAsync() {
        return async().smembers(key).<Set<Object>>thenApply(set -> {
            if (set == null)
                return new HashSet<>();
            return new HashSet<>(set);
        }).toCompletableFuture();
    }

    @Override
    public Set<Object> members() {
        return membersAsync().join();
    }

    @Override
    public CompletableFuture<Object> randomMemberAsync() {
        return async().srandmember(key).toCompletableFuture();
    }

    @Override
    public Object randomMember() {
        return randomMemberAsync().join();
    }

    @Override
    public CompletableFuture<Object> popAsync() {
        return async().spop(key).toCompletableFuture();
    }

    @Override
    public Object pop() {
        return popAsync().join();
    }
}
