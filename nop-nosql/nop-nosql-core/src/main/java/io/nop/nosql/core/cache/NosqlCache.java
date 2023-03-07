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

    public NosqlCache(INosqlKeyValueOperations ops, CacheConfig config) {
        this.ops = ops;
        this.cacheConfig = config;
    }

    @Override
    public boolean removeIfMatch(String key, Object object) {
        return false;
    }

    @Override
    public @Nullable CompletionStage<Object> getAsync(@Nonnull String key) {
        return ops.getExAsync(key, cacheConfig.getExpireAfterAccess().toMillis());
    }

    @Override
    public @Nonnull CompletionStage<Object> computeIfAbsentAsync(@Nonnull String key,
                                                                 @Nonnull Function<? super String, ?> mappingFunction) {
        return null;
    }

    @Override
    public @Nonnull CompletionStage<Map<String, Object>> getAllAsync(Collection<? extends String> keys) {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public long estimatedSize() {
        return 0;
    }

    @Override
    public Object getIfPresent(String key) {
        return null;
    }

    @Override
    public Object computeIfAbsent(String key, Function<? super String, ?> mappingFunction) {
        return null;
    }

    @Override
    public Map<String, Object> getAllPresent(Collection<? extends String> keys) {
        return null;
    }

    @Override
    public void put(String key, Object value) {

    }

    @Override
    public void putAll(Map<? extends String, ?> map) {
        ICache.super.putAll(map);
    }

    @Override
    public boolean putIfAbsent(String key, Object value) {
        return ICache.super.putIfAbsent(key, value);
    }

    @Override
    public void remove(String key) {

    }

    @Override
    public boolean remove(String key, Object object) {
        return ICache.super.remove(key, object);
    }

    @Override
    public void removeAll(Collection<? extends String> keys) {

    }

    @Override
    public void clear() {

    }

    @Override
    public void refresh(String key) {

    }

    @Override
    public void forEachEntry(BiConsumer<? super String, ? super Object> consumer) {

    }

    @Override
    public void clearForTenant(String tenantId) {
        ICache.super.clearForTenant(tenantId);
    }

    @Override
    public CompletionStage<Boolean> containsKeyAsync(String key) {
        return null;
    }

    @Override
    public CompletionStage<Void> putAsync(String key, Object value) {
        return null;
    }

    @Override
    public CompletionStage<Void> putAllAsync(Map<? extends String, ?> map) {
        return null;
    }

    @Override
    public CompletionStage<Boolean> putIfAbsentAsync(String key, Object value) {
        return null;
    }

    @Override
    public CompletionStage<Object> getAndSetAsync(String key, Object value) {
        return null;
    }

    @Override
    public CompletionStage<Void> removeAsync(String key) {
        return null;
    }

    @Override
    public CompletionStage<Boolean> removeIfMatchAsync(String key, Object object) {
        return null;
    }

    @Override
    public CompletionStage<Void> removeAllAsync(Collection<? extends String> keys) {
        return null;
    }

    @Override
    public CompletionStage<Void> clearAsync() {
        return null;
    }

    @Override
    public CompletionStage<Void> forEachEntryAsync(BiConsumer<? super String, ? super Object> consumer) {
        return null;
    }

    @Override
    public CacheStats stats() {
        return null;
    }
}
