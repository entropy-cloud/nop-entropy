/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.nosql.core.cache;

import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.CacheStats;
import io.nop.commons.cache.ICache;
import io.nop.nosql.core.INosqlKeyValueOperations;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class NosqlCache implements ICache<String, Object> {
    private final INosqlKeyValueOperations ops;
    private final CacheConfig cacheConfig;

    private final String name;

    public NosqlCache(String name, INosqlKeyValueOperations ops, CacheConfig config) {
        this.name = name;
        this.ops = ops;
        this.cacheConfig = config;
    }

    @Override
    public boolean removeIfMatch(String key, Object object) {
        return ops.removeIfMatch(key, object);
    }

    @Override
    public @Nullable CompletionStage<Object> getAsync(@Nonnull String key) {
        return ops.getExAsync(key, cacheConfig.getExpireAfterAccess().toMillis());
    }

    @Override
    public @Nonnull CompletionStage<Object> computeIfAbsentAsync(@Nonnull String key,
                                                                 @Nonnull Function<? super String, ?> mappingFunction) {
        return ops.computeIfAbsentAsync(key, mappingFunction);
    }

    @Override
    public @Nonnull CompletionStage<Map<String, Object>> getAllAsync(Collection<? extends String> keys) {
        return ops.getAllAsync(keys);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long estimatedSize() {
        return ops.getSize();
    }

    @Override
    public Object getIfPresent(String key) {
        return ops.get(key);
    }

    @Override
    public Object computeIfAbsent(String key, Function<? super String, ?> mappingFunction) {
        return ops.computeIfAbsent(key, mappingFunction);
    }

    @Override
    public Map<String, Object> getAllPresent(Collection<? extends String> keys) {
        return ops.getAll(keys);
    }

    @Override
    public void put(String key, Object value) {
        ops.put(key, value);
    }

    @Override
    public void putAll(Map<? extends String, ?> map) {
        ops.putAll(map);
    }

    @Override
    public boolean putIfAbsent(String key, Object value) {
        return ops.putIfAbsent(key, value);
    }

    @Override
    public void remove(String key) {
        ops.remove(key);
    }

    @Override
    public boolean remove(String key, Object object) {
        return ops.removeIfMatch(key, object);
    }

    @Override
    public void removeAll(Collection<? extends String> keys) {
        ops.removeAll(keys);
    }

    @Override
    public void clear() {
        ops.clear();
    }

    @Override
    public void refresh(String key) {
        ops.remove(key);
    }

    @Override
    public void forEachEntry(BiConsumer<? super String, ? super Object> consumer) {
        ops.forEachEntry(consumer);
    }

    @Override
    public void clearForTenant(String tenantId) {

    }

    @Override
    public CompletionStage<Boolean> containsKeyAsync(String key) {
        return ops.containsKeyAsync(key);
    }

    @Override
    public CompletionStage<Void> putAsync(String key, Object value) {
        return ops.putAsync(key, value);
    }

    @Override
    public CompletionStage<Void> putAllAsync(Map<? extends String, ?> map) {
        return ops.putAllAsync(map);
    }

    @Override
    public CompletionStage<Boolean> putIfAbsentAsync(String key, Object value) {
        return ops.putIfAbsentAsync(key, value);
    }

    @Override
    public CompletionStage<Object> getAndSetAsync(String key, Object value) {
        return ops.getAndSetAsync(key, value);
    }

    @Override
    public CompletionStage<Void> removeAsync(String key) {
        return ops.removeAsync(key);
    }

    @Override
    public CompletionStage<Boolean> removeIfMatchAsync(String key, Object object) {
        return ops.removeIfMatchAsync(key, object);
    }

    @Override
    public CompletionStage<Void> removeAllAsync(Collection<? extends String> keys) {
        return ops.removeAllAsync(keys);
    }

    @Override
    public CompletionStage<Void> clearAsync() {
        return ops.clearAsync();
    }

    @Override
    public CompletionStage<Void> forEachEntryAsync(BiConsumer<? super String, ? super Object> consumer) {
        return ops.forEachEntryAsync(consumer);
    }

    @Override
    public CacheStats stats() {
        return null;
    }
}
