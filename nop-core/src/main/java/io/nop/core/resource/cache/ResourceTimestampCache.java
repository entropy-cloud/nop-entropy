/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.cache;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.api.core.resource.IResourceReference;

import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_TIMESTAMP_CACHE_SIZE;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_TIMESTAMP_CACHE_TIMEOUT;
import static io.nop.core.CoreConstants.RESOURCE_TIMESTAMP_CACHE_NAME;

/**
 * 为避免频繁调用IResource.lastModified消耗时间，这里提供了对于lastModified的调用缓存
 */
@GlobalInstance
public class ResourceTimestampCache {
    private static final ResourceTimestampCache _instance = new ResourceTimestampCache();

    public static ResourceTimestampCache instance() {
        return _instance;
    }

    private final ICache<String, Long> cache;

    public ResourceTimestampCache() {
        this.cache = createCache();
    }

    public void clear() {
        cache.clear();
    }

    public long getLastModified(IResourceReference resource) {
        return cache.computeIfAbsent(resource.getPath(), path -> resource.lastModified());
    }

    ICache<String, Long> createCache() {
        CacheConfig config = new CacheConfig();
        config.setMaximumSize(CFG_COMPONENT_RESOURCE_TIMESTAMP_CACHE_SIZE.get());
        config.setExpireAfterWrite(CFG_COMPONENT_RESOURCE_TIMESTAMP_CACHE_TIMEOUT.get());
        return LocalCache.newCache(RESOURCE_TIMESTAMP_CACHE_NAME, config, null);
    }
}