/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.cache;

import io.nop.api.core.config.IConfigReference;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;

import java.util.Objects;

import static io.nop.commons.cache.CacheConfig.newConfig;
import static io.nop.core.CoreConfigs.CFG_RESOURCE_CONTENT_CACHE_SIZE;
import static io.nop.core.CoreConstants.RESOURCE_CONTENT_CACHE_NAME;

public class ResourceContentCache implements IResourceContentCache {
    static final ResourceContentCache _INSTANCE = new ResourceContentCache(CFG_RESOURCE_CONTENT_CACHE_SIZE);

    public static ResourceContentCache instance() {
        return _INSTANCE;
    }

    private ICache<String, ContentEntry> cache;

    private static class ContentEntry {
        final long lastModified;
        final String text;

        public ContentEntry(long lastModified, String text) {
            this.lastModified = lastModified;
            this.text = text;
        }
    }

    public ResourceContentCache(IConfigReference<Integer> config) {
        cache = LocalCache.newCache(RESOURCE_CONTENT_CACHE_NAME, newConfig(config.get()).useMetrics(), null);
    }

    @Override
    public String getCachedText(IResource resource, boolean allowLoad) {
        long lastModified = resource.lastModified();
        ContentEntry entry = cache.get(resource.getPath());
        if (entry == null || entry.lastModified != lastModified) {
            if (allowLoad)
                return cache.computeIfAbsent(resource.getPath(), key -> {
                    return new ContentEntry(resource.lastModified(), resource.readText());
                }).text;
            return null;
        }
        return entry.text;
    }

    @Override
    public void clearCachedText(IResource resource, boolean removeFile) {
        cache.remove(resource.getPath());
        if (removeFile) {
            resource.delete();
        }
    }

    @Override
    public boolean updateCachedText(IResource resource, String text, boolean flushToFile, boolean removeEmptyFile) {
        ContentEntry entry = cache.get(resource.getPath());
        if (entry != null) {
            // 如果文件已经被删除，则文件时间会不一致
            if (entry.lastModified == resource.lastModified() && Objects.equals(entry.text, text)) {
                return false;
            }
        }
        if (flushToFile) {
            if (removeEmptyFile && StringHelper.isEmpty(text)) {
                resource.delete();
            } else {
                ResourceHelper.writeText(resource, text == null ? "" : text, null);
            }
        }
        cache.put(resource.getPath(), new ContentEntry(resource.lastModified(), text));
        return true;
    }
}