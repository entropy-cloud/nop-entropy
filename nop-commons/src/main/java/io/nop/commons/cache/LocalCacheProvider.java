/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.cache;

import io.nop.api.core.config.IConfigRefreshable;
import io.nop.commons.util.StringHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocalCacheProvider implements ICacheProvider, IConfigRefreshable {
    private final String prefix;
    private final CacheConfig config;

    private final Map<String, LocalCache<Object, Object>> caches = new ConcurrentHashMap<>();

    public LocalCacheProvider(String prefix, CacheConfig config) {
        this.prefix = prefix;
        this.config = config;
    }

    public CacheConfig getConfig() {
        return config;
    }

    @Override
    public void refreshConfig() {
        for (LocalCache<?, ?> cache : caches.values()) {
            cache.refreshConfig();
        }
    }

    @Override
    public <K, V> ICache<K, V> getCache(String name) {
        return (ICache<K, V>) caches.computeIfAbsent(name, this::buildCache);
    }

    LocalCache<Object, Object> buildCache(String name) {
        return new LocalCache<>(buildFullName(name), config);
    }

    String buildFullName(String name) {
        if (StringHelper.isEmpty(prefix))
            return name;
        return prefix + '.' + name;
    }

    @Override
    public void clearAllCache() {
        for (ICache<?, ?> cache : caches.values()) {
            cache.clear();
        }
    }
}