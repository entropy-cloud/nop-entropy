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
import io.nop.nosql.core.INosqlHashOperations;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class LettuceHashOperations extends AbstractLettuceOperations implements INosqlHashOperations {
    private final String key;

    public LettuceHashOperations(LettuceRedisConnectionProvider client, String key) {
        super(client);
        this.key = key;
    }

    @Override
    public CompletionStage<Object> getAsync(String field) {
        return async().hget(key, field);
    }

    @Override
    public Object get(String field) {
        return FutureHelper.syncGet(getAsync(field));
    }

    @Override
    public CompletionStage<Object> computeIfAbsentAsync(String field, Function<? super String, ?> mappingFunction) {
        return async().hget(key, field).thenCompose(value -> {
            if (value == null) {
                value = mappingFunction.apply(field);
                Object ret = value;
                return async().hset(key, field, value).thenApply(v -> ret);
            }
            return CompletableFuture.completedFuture(value);
        });
    }

    @Override
    public Object computeIfAbsent(String field, Function<? super String, ?> mappingFunction) {
        return FutureHelper.syncGet(computeIfAbsentAsync(field, mappingFunction));
    }

    @Override
    public CompletionStage<Map<String, Object>> getAllAsync(Collection<? extends String> fields) {
        return async().hmget(key, fields.toArray(new String[0])).thenApply(LettuceHelper::toMap);
    }

    @Override
    public Map<String, Object> getAll(Collection<? extends String> fields) {
        return FutureHelper.syncGet(getAllAsync(fields));
    }

    @Override
    public CompletionStage<Boolean> containsKeyAsync(String field) {
        return async().hexists(key, field);
    }

    @Override
    public boolean containsKey(String field) {
        return FutureHelper.syncGet(containsKeyAsync(field));
    }

    @Override
    public CompletionStage<Void> putAsync(String field, Object value) {
        return async().hset(key, field, value).thenApply(Functionals.toVoid());
    }

    @Override
    public void put(String field, Object value) {
        FutureHelper.syncGet(putAsync(field, value));
    }

    @SuppressWarnings("unchecked") // Lettuce hset requires Map<String, Object>, wildcard capture is safe at runtime
    @Override
    public CompletionStage<Void> putAllAsync(Map<? extends String, ?> map) {
        if (map == null || map.isEmpty())
            return CompletableFuture.completedFuture(null);
        return async().hset(key, (Map) map).thenApply(Functionals.toVoid());
    }

    @Override
    public void putAll(Map<? extends String, ?> map) {
        FutureHelper.syncGet(putAllAsync(map));
    }

    @Override
    public CompletionStage<Boolean> putIfAbsentAsync(String field, Object value) {
        return async().hsetnx(key, field, value);
    }

    @Override
    public boolean putIfAbsent(String field, Object value) {
        return FutureHelper.syncGet(putIfAbsentAsync(field, value));
    }

    @Override
    public CompletionStage<Object> getAndSetAsync(String field, Object value) {
        return async().hget(key, field).thenCompose(oldValue ->
                async().hset(key, field, value).thenApply(v -> oldValue));
    }

    @Override
    public Object getAndSet(String field, Object value) {
        return FutureHelper.syncGet(getAndSetAsync(field, value));
    }

    @Override
    public CompletionStage<Void> removeAsync(String field) {
        return async().hdel(key, field).thenApply(Functionals.toVoid());
    }

    @Override
    public void remove(String field) {
        FutureHelper.syncGet(removeAsync(field));
    }

    @Override
    public CompletionStage<Boolean> removeIfMatchAsync(String field, Object object) {
        return async().hget(key, field).thenCompose(value -> {
            if (value != null && value.equals(object)) {
                return async().hdel(key, field).thenApply(n -> n > 0);
            }
            return CompletableFuture.completedFuture(false);
        });
    }

    @Override
    public boolean removeIfMatch(String field, Object object) {
        return FutureHelper.syncGet(removeIfMatchAsync(field, object));
    }

    @Override
    public CompletionStage<Void> removeAllAsync(Collection<? extends String> fields) {
        return async().hdel(key, fields.toArray(new String[0])).thenApply(Functionals.toVoid());
    }

    @Override
    public void removeAll(Collection<? extends String> fields) {
        FutureHelper.syncGet(removeAllAsync(fields));
    }

    @Override
    public CompletionStage<Void> clearAsync() {
        return async().del(key).thenApply(Functionals.toVoid());
    }

    @Override
    public void clear() {
        FutureHelper.syncGet(clearAsync());
    }

    @Override
    public CompletionStage<Void> forEachEntryAsync(BiConsumer<? super String, ? super Object> consumer) {
        return async().hgetall(key).thenAccept(map -> {
            if (map != null) {
                map.forEach(consumer);
            }
        });
    }

    @Override
    public void forEachEntry(BiConsumer<? super String, ? super Object> consumer) {
        FutureHelper.syncGet(forEachEntryAsync(consumer));
    }

    @Override
    public CompletionStage<Long> getSizeAsync() {
        return async().hlen(key);
    }

    @Override
    public long getSize() {
        return FutureHelper.syncGet(getSizeAsync());
    }

    @Override
    public CompletionStage<Void> putExAsync(String field, Object value, long timeout) {
        return async().hset(key, field, value).thenCompose(v ->
                async().pexpire(key, timeout).thenApply(Functionals.toVoid()));
    }

    @Override
    public CompletionStage<Object> getExAsync(String field, long timeout) {
        return async().hget(key, field).thenCompose(value -> {
            if (timeout >= 0 && value != null) {
                return async().pexpire(key, timeout).thenApply(v -> value);
            }
            return CompletableFuture.completedFuture(value);
        });
    }

    @Override
    public CompletionStage<Boolean> putIfAbsentExAsync(String field, Object value, long timeout) {
        return async().hsetnx(key, field, value).thenCompose(set -> {
            if (set && timeout >= 0) {
                return async().pexpire(key, timeout).thenApply(v -> true);
            }
            return CompletableFuture.completedFuture(set);
        });
    }

    @Override
    public CompletionStage<String> putIfAbsentOrMatchExAsync(String field, String value, long timeout) {
        return async().hget(key, field).thenCompose(oldValue -> {
            if (oldValue == null || (oldValue instanceof String && oldValue.equals(value))) {
                return async().hset(key, field, value).thenCompose(v -> {
                    if (timeout > 0) {
                        return async().pexpire(key, timeout).thenApply(exp -> oldValue != null ? String.valueOf(oldValue) : null);
                    }
                    return CompletableFuture.completedFuture(oldValue != null ? String.valueOf(oldValue) : null);
                });
            }
            return CompletableFuture.completedFuture(oldValue != null ? String.valueOf(oldValue) : null);
        });
    }

    @Override
    public CompletionStage<Object> getAndSetExAsync(String field, Object value, long timeout) {
        return async().hget(key, field).thenCompose(oldValue ->
                async().hset(key, field, value).thenCompose(v -> {
                    if (timeout >= 0) {
                        return async().pexpire(key, timeout).thenApply(expired -> oldValue);
                    }
                    return CompletableFuture.completedFuture(oldValue);
                }));
    }

    @Override
    public CompletionStage<Long> getTimeoutAsync(String field) {
        return async().pttl(key);
    }

    @Override
    public CompletionStage<Boolean> setTimeoutAsync(String field, long timeout) {
        return async().pexpire(key, timeout);
    }

    @Override
    public CompletionStage<Map<String, Object>> getAllAsync() {
        return async().hgetall(key);
    }
}
